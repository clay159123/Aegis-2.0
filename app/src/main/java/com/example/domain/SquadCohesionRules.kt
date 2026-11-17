package com.example.domain

import com.example.domain.model.*
import kotlin.math.roundToInt

data class SquadCohesionGainResult(
    val updatedSquad: TacticalSquad,
    val gainedXp: Int,
    val previousScore: Int,
    val newScore: Int,
    val summaryNote: String
)

object SquadCohesionRules {

    /** Survival and pass-chance boost for a squad deployed at Full Strength. */
    const val FULL_STRENGTH_BONUS_PCT = 2

    fun cohesionHeadcountXp(participants: Int): Int =
        30 + (minOf(participants, FULL_STRENGTH_SQUAD_SIZE) - 2) * 15 + (participants - FULL_STRENGTH_SQUAD_SIZE).coerceAtLeast(0) * 5

    private val natoSquadCallsigns = listOf(
        "Alpha Squad",
        "Bravo Squad",
        "Charlie Squad",
        "Delta Squad",
        "Echo Squad",
        "Foxtrot Squad"
    )

    fun nextDefaultSquadName(existingSquads: List<TacticalSquad>): String {
        val existingLower = existingSquads.map { it.name.trim().lowercase() }.toSet()
        return natoSquadCallsigns.firstOrNull { it.lowercase() !in existingLower }
            ?: "Squad ${existingSquads.size + 1}"
    }

    fun pickDefaultLeaderId(
        memberIds: List<String>,
        operators: List<Operator>
    ): String? {
        if (memberIds.isEmpty()) return null
        val opsById = operators.associateBy { it.id }
        return memberIds
            .distinct()
            .sortedWith(
                compareByDescending<String> { opsById[it]?.rank?.ordinal ?: -1 }
                    .thenByDescending { opsById[it]?.xp ?: -1 }
            )
            .firstOrNull()
    }

    /** Cohesion XP a Team Leader adds when present on a mission (a smaller version of the squad leader's bonus). */
    const val TEAM_LEADER_COHESION_XP = 8

    /** Why [candidate] can't be this squad's Team Leader, or null when they can. */
    fun teamLeaderBlocker(squad: TacticalSquad, candidate: Operator?): String? = when {
        squad.memberIds.size < TEAM_LEADER_MIN_SQUAD_SIZE -> "Squad needs $TEAM_LEADER_MIN_SQUAD_SIZE+ members"
        candidate == null || candidate.id !in squad.memberIds -> "Not a squad member"
        candidate.id == squad.leaderId -> "Already the Squad Leader"
        candidate.rank.ordinal < OperatorRank.SGT.ordinal -> "Requires SGT or above"
        else -> null
    }

    /** The squad's Team Leader if still valid; clears when the squad drops below 5 or the soldier leaves. */
    fun validTeamLeaderId(squad: TacticalSquad, operators: List<Operator>): String? {
        val id = squad.teamLeaderId ?: return null
        return id.takeIf { teamLeaderBlocker(squad, operators.find { it.id == id }) == null }
    }

    fun isOperatorEligibleForFieldSquad(operator: Operator): Boolean =
        operator.unlocked &&
            !operator.status.isOutOfActiveService &&
            operator.status != OperatorStatus.HQ_STAFF

    fun orderedMembersForDeployment(
        squad: TacticalSquad,
        eligibleOperatorIds: Set<String>,
        maxCount: Int
    ): List<String> {
        val eligible = squad.memberIds.filter { it in eligibleOperatorIds }.distinct()
        val leaderFirst = if (squad.leaderId != null && squad.leaderId in eligible) {
            listOf(squad.leaderId) + eligible.filter { it != squad.leaderId }
        } else {
            eligible
        }
        return leaderFirst.take(maxCount.coerceAtLeast(0))
    }

    fun evaluateActiveBonus(
        selectedOperators: List<Operator>,
        squads: List<TacticalSquad>
    ): ActiveSquadCohesionBonus? {
        if (selectedOperators.size < 2 || squads.isEmpty()) return null
        val selectedById = selectedOperators.associateBy { it.id }

        val bestMatch = squads
            .map { squad ->
                val deployedMembers = squad.memberIds.filter { it in selectedById }
                squad to deployedMembers
            }
            .filter { (_, deployedMembers) -> deployedMembers.size >= 2 }
            .maxWithOrNull(
                compareBy<Pair<TacticalSquad, List<String>>> { it.second.size }
                    .thenBy { it.first.cohesionXp }
            ) ?: return null

        val (squad, deployedMemberIds) = bestMatch
        val deployedCount = deployedMemberIds.size.coerceAtMost(MAX_SQUAD_SIZE)
        val leaderOp = squad.leaderId?.let { selectedById[it] }
        val leaderPresent = leaderOp != null
        val score = squad.cohesionScore
        val tier = squad.cohesionTier

        val teamFactor = when (deployedCount) {
            2 -> 0.55
            3 -> 0.80
            else -> 1.00
        }
        val leaderFactor = if (leaderPresent) 1.0 else 0.60
        val leaderPresenceEff = if (leaderPresent) {
            val rankBoost = if (leaderOp!!.rank.tier.order >= RankTier.NCO.order) 1 else 0
            val mosBoost = if (leaderOp.specialty == OperatorSpecialty.COMMAND) 1 else 0
            2 + rankBoost + mosBoost
        } else {
            0
        }
        val leaderPresenceOdds = if (leaderPresent && leaderOp!!.rank.tier.order >= RankTier.NCO.order) 1 else 0

        val scale = (score / 100.0) * teamFactor * leaderFactor
        val effBonus = (scale * 16.0).roundToInt() + leaderPresenceEff
        val survBonus = (scale * 10.0).roundToInt() + if (leaderPresent && score >= 20) 1 else 0
        val oddsBonus = (scale * 8.0).roundToInt() + leaderPresenceOdds

        // Full Strength: a squad of 4+ that deploys at least 4 of its members
        val fullStrength = squad.memberIds.size >= FULL_STRENGTH_SQUAD_SIZE && deployedCount >= FULL_STRENGTH_SQUAD_SIZE
        val fullStrengthBonus = if (fullStrength) FULL_STRENGTH_BONUS_PCT else 0

        if (effBonus <= 0 && survBonus <= 0 && oddsBonus <= 0 && !fullStrength) return null

        return ActiveSquadCohesionBonus(
            squad = squad,
            deployedMemberCount = deployedCount,
            leaderPresent = leaderPresent,
            cohesionScore = score,
            tier = tier,
            efficiencyBonusPct = effBonus,
            survivalBonusPct = survBonus + fullStrengthBonus,
            successOddsBonusPct = oddsBonus + fullStrengthBonus,
            fullStrength = fullStrength
        )
    }

    fun calculateCohesionGain(
        squad: TacticalSquad,
        deployedOperators: List<Operator>,
        mission: MissionTemplate,
        missionSucceeded: Boolean
    ): SquadCohesionGainResult? {
        val squadMemberSet = squad.memberIds.toSet()
        val participating = deployedOperators.filter {
            it.id in squadMemberSet && it.status != OperatorStatus.FALLEN
        }
        if (participating.size < 2) return null

        val leaderOp = squad.leaderId?.let { id -> participating.firstOrNull { it.id == id } }
        val leaderPresent = leaderOp != null
        // Headcount helps up to 4; members 5 and 6 add only a little
        val baseXp = cohesionHeadcountXp(participating.size)
        val hazardBonus = when (mission.risk) {
            MissionRisk.TRAINING, MissionRisk.LOW -> 0
            MissionRisk.MODERATE -> 10
            MissionRisk.HIGH -> 20
            MissionRisk.EXTREME -> 30
        }
        val leaderBonus = if (leaderPresent) {
            15 + if (leaderOp!!.rank.tier.order >= RankTier.NCO.order || leaderOp.specialty == OperatorSpecialty.COMMAND) 10 else 0
        } else {
            0
        }
        val teamLeaderPresent = squad.teamLeaderId != null && participating.any { it.id == squad.teamLeaderId }
        val teamLeaderBonus = if (teamLeaderPresent) TEAM_LEADER_COHESION_XP else 0
        val rawGain = baseXp + hazardBonus + leaderBonus + teamLeaderBonus
        val finalGain = if (missionSucceeded) rawGain else (rawGain / 2).coerceAtLeast(10)

        val previousScore = squad.cohesionScore
        val nextXp = (squad.cohesionXp + finalGain).coerceIn(0, MAX_SQUAD_COHESION_XP)
        val updatedSquad = squad.copy(
            cohesionXp = nextXp,
            missionsTogether = squad.missionsTogether + 1
        )
        val newScore = updatedSquad.cohesionScore
        val scoreDelta = newScore - previousScore
        val leaderNote = if (leaderPresent) " • LDR ${leaderOp!!.callsign}" else " • No Leader"
        val deltaText = if (scoreDelta > 0) "+$scoreDelta%" else "+${finalGain} XP"
        val note = "${squad.name} Cohesion $deltaText → $newScore% (${updatedSquad.cohesionTier.label}$leaderNote)"

        return SquadCohesionGainResult(
            updatedSquad = updatedSquad,
            gainedXp = finalGain,
            previousScore = previousScore,
            newScore = newScore,
            summaryNote = note
        )
    }

    fun pruneUnavailableMembers(
        squads: List<TacticalSquad>,
        operators: List<Operator>
    ): List<TacticalSquad> {
        val opsById = operators.associateBy { it.id }
        return squads.map { squad ->
            val validMembers = squad.memberIds
                .filter { id -> opsById[id]?.let { isOperatorEligibleForFieldSquad(it) } == true }
                .distinct()
                .take(MAX_SQUAD_SIZE)
            val validLeader = squad.leaderId?.takeIf { it in validMembers }
                ?: pickDefaultLeaderId(validMembers, operators)
            val validTeamLeader = validTeamLeaderId(
                squad.copy(memberIds = validMembers, leaderId = validLeader),
                operators
            )
            if (validMembers == squad.memberIds && validLeader == squad.leaderId && validTeamLeader == squad.teamLeaderId) {
                squad
            } else {
                squad.copy(memberIds = validMembers, leaderId = validLeader, teamLeaderId = validTeamLeader)
            }
        }
    }
}
