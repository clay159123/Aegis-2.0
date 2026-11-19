package com.example.domain

import com.example.domain.model.*

/**
 * Item 63: prisoners of war. Field missions carry a capture chance; captured soldiers keep their roster slot
 * while held. The player brings them home by rescue (a timed window), diplomacy (a large prestige cost) or
 * waiting (released or lost in captivity). Returning POWs get the POW Medal.
 * Item 115b: SERE graduates are harder to capture, held for less time and more likely to come home.
 */
object PowRules {
    const val RESCUE_MISSION_ID = "msn_pow_rescue"
    const val NEGOTIATION_MISSION_ID = "msn_pow_negotiation"
    const val POW_MEDAL_ID = "medal_pow"

    /** Capture chance (percent) per surviving soldier at the midpoint of risk, on a failed / successful mission. */
    fun baseCaptureChancePct(risk: MissionRisk, missionSucceeded: Boolean): Double = when (risk) {
        MissionRisk.TRAINING -> 0.0
        MissionRisk.LOW -> if (missionSucceeded) 0.0 else 1.0
        MissionRisk.MODERATE -> if (missionSucceeded) 0.5 else 3.0
        MissionRisk.HIGH -> if (missionSucceeded) 1.0 else 6.0
        MissionRisk.EXTREME -> if (missionSucceeded) 2.0 else 10.0
    }

    /** Stealth and resilience above 20 each shave capture odds, up to 40% off. */
    const val STAT_REDUCTION_PER_POINT = 0.01
    const val MAX_STAT_REDUCTION = 0.40
    const val SERE_CAPTURE_FACTOR = 0.6
    /** Item 62 (Session 21) will pass true when a clandestine cover is blown. */
    const val BLOWN_COVER_FACTOR = 3.0

    fun captureChancePct(
        mission: MissionTemplate,
        operator: Operator,
        missionSucceeded: Boolean,
        blownCover: Boolean = false
    ): Double {
        if (mission.scope != MissionScope.FIELD_COMBAT) return 0.0
        val base = baseCaptureChancePct(mission.risk, missionSucceeded)
        val statPoints = (operator.baseStealth - 20).coerceAtLeast(0) + (operator.baseResilience - 20).coerceAtLeast(0)
        val statFactor = 1.0 - (statPoints * STAT_REDUCTION_PER_POINT).coerceAtMost(MAX_STAT_REDUCTION)
        val sere = if (TacticalCalculator.isSereQualified(operator)) SERE_CAPTURE_FACTOR else 1.0
        val cover = if (blownCover) BLOWN_COVER_FACTOR else 1.0
        return base * statFactor * sere * cover
    }

    /** How long a held soldier waits before the captors release them (or they die in captivity). */
    const val CAPTIVITY_MILLIS = 60 * 60_000L
    const val SERE_CAPTIVITY_FACTOR = 0.75
    /** The rescue window after capture. */
    const val RESCUE_WINDOW_MILLIS = 30 * 60_000L
    /** Chance (percent) the soldier is released alive when the wait runs out. */
    const val RELEASE_SURVIVAL_PCT = 70
    const val SERE_RELEASE_SURVIVAL_PCT = 85
    const val NEGOTIATION_PRESTIGE_COST = 60.0
    /** A general leading the talks closes the deal for less. */
    const val GENERAL_NEGOTIATION_PRESTIGE_COST = 45.0
    /** Lead negotiator bonus: a general (BG +15 up to GEN +20), a field-grade officer, CSM or SGM (+8). */
    const val GENERAL_LEAD_BONUS_PCT = 15
    const val SENIOR_LEAD_BONUS_PCT = 8
    /** The S-2 knows the captors; the Deputy Commander can make a deal. */
    const val BILLET_TALKS_BONUS_PCT = 5
    /** Each extra senior or HQ negotiator beyond the lead, up to two. */
    const val SUPPORT_TALKS_BONUS_PCT = 3
    const val MAX_SUPPORT_NEGOTIATORS = 2
    /** A failed round: talks collapse for a while, the captors harden, and they may move or kill the prisoner. */
    const val TALKS_COOLDOWN_MILLIS = 15 * 60_000L
    const val HARDENED_PENALTY_PCT = 5
    const val PRISONER_MOVED_CHANCE = 0.50
    const val BAD_FAITH_CHANCE = 0.05

    /** Set while talks are staged or resolved so the shared odds formula can see the captors' mood. */
    @Volatile var activeTalksFailures: Int = 0

    private fun Operator.isSeniorNegotiator(): Boolean =
        rank.tier == RankTier.OFFICER || rank.tier == RankTier.FLAG_OFFICER || rank.tier == RankTier.WARRANT || rank.tier == RankTier.SNCO

    private fun Operator.isSupportNegotiator(): Boolean = isSeniorNegotiator() || status == OperatorStatus.HQ_STAFF

    fun leadBonusPct(op: Operator): Int = when {
        op.rank.tier == RankTier.FLAG_OFFICER -> GENERAL_LEAD_BONUS_PCT + ((op.rank.flagGrade - 1) * 5 + 1) / 3
        op.rank in setOf(OperatorRank.MAJ, OperatorRank.LTC, OperatorRank.COL, OperatorRank.SGM, OperatorRank.CSM) -> SENIOR_LEAD_BONUS_PCT
        else -> 0
    }

    /** Talks bonus from who's at the table: the best lead, two billets, and up to two supporting seniors. */
    fun talksTeamBonusPct(team: List<Operator>): Int {
        if (team.isEmpty()) return 0
        val lead = team.maxBy { leadBonusPct(it) * 100 + it.rank.seniorityIndex }
        val billets = team.count { it.status == OperatorStatus.HQ_STAFF && (it.hqRole == HqStaffRole.S2_INTEL_OVERWATCH || it.hqRole == HqStaffRole.DEPUTY_COMMANDER) }
        val support = team.filter { it.id != lead.id && it.isSupportNegotiator() }.size.coerceAtMost(MAX_SUPPORT_NEGOTIATORS)
        return leadBonusPct(lead) + billets * BILLET_TALKS_BONUS_PCT + support * SUPPORT_TALKS_BONUS_PCT
    }

    fun talksPrestigeCost(team: List<Operator>): Double =
        if (team.any { it.rank.tier == RankTier.FLAG_OFFICER }) GENERAL_NEGOTIATION_PRESTIGE_COST else NEGOTIATION_PRESTIGE_COST

    /** Talks odds modifier for this team against a POW's captors. */
    fun talksModifierPct(team: List<Operator>, failures: Int): Int = talksTeamBonusPct(team) - failures * HARDENED_PENALTY_PCT

    fun talksCoolingDown(target: Operator, now: Long): Boolean = now < target.powTalksCooldownUntilMillis

    /** A failed round of talks. Returns the updated POW and what happened. */
    fun failedTalks(target: Operator, now: Long, random: kotlin.random.Random = kotlin.random.Random): Pair<Operator, String> {
        val hardened = target.copy(
            powTalksFailures = target.powTalksFailures + 1,
            powTalksCooldownUntilMillis = now + GameTiming.millis(TALKS_COOLDOWN_MILLIS)
        )
        val who = "${target.rank.code} ${target.callsign}"
        if (random.nextDouble() < BAD_FAITH_CHANCE) {
            return diedInCaptivity(hardened) to "Talks failed in bad faith: $who was killed in captivity."
        }
        val moved = random.nextDouble() < PRISONER_MOVED_CHANCE
        val note = "Talks for $who collapsed. No new talks for ${GameTiming.millis(TALKS_COOLDOWN_MILLIS) / 60_000L} minutes; " +
            "the captors are harder to deal with (-${hardened.powTalksFailures * HARDENED_PENALTY_PCT}%)." +
            if (moved) " They moved the prisoner: the rescue window is closed." else ""
        return hardened.copy(powRescueClosed = hardened.powRescueClosed || moved) to note
    }
    /** Returning POWs spend twice the usual Medbay time recovering. */
    const val RETURN_RECOVERY_FACTOR = 2.0

    fun captivityMillis(operator: Operator): Long {
        val base = GameTiming.millis(CAPTIVITY_MILLIS)
        return if (TacticalCalculator.isSereQualified(operator)) (base * SERE_CAPTIVITY_FACTOR).toLong() else base
    }

    fun releaseSurvivalPct(operator: Operator): Int =
        if (TacticalCalculator.isSereQualified(operator)) SERE_RELEASE_SURVIVAL_PCT else RELEASE_SURVIVAL_PCT

    fun capture(operator: Operator, mission: MissionTemplate, now: Long): Operator = operator.copy(
        status = OperatorStatus.CAPTURED,
        capturedAtMillis = now,
        captivityResolveAtMillis = now + captivityMillis(operator),
        captureTheater = mission.theater,
        timesCaptured = operator.timesCaptured + 1,
        powTalksFailures = 0,
        powTalksCooldownUntilMillis = 0L,
        powRescueClosed = false,
        recoveryUntilMillis = 0L,
        hqRole = null
    )

    fun rescueWindowOpen(operator: Operator, now: Long): Boolean =
        operator.status == OperatorStatus.CAPTURED && !operator.powRescueClosed &&
            now < operator.capturedAtMillis + GameTiming.millis(RESCUE_WINDOW_MILLIS)

    fun rescueWindowRemainingMillis(operator: Operator, now: Long): Long =
        if (operator.powRescueClosed) 0L else (operator.capturedAtMillis + GameTiming.millis(RESCUE_WINDOW_MILLIS) - now).coerceAtLeast(0L)

    /** Home again: into Medbay for a longer recovery, captivity record kept for the POW Medal. */
    fun released(operator: Operator, now: Long, recoveryMultiplier: Double): Operator = operator.copy(
        status = OperatorStatus.RECOVERING,
        recoveryUntilMillis = now + GameTiming.millis((TacticalCalculator.BASE_RECOVERY_MILLIS * recoveryMultiplier * RETURN_RECOVERY_FACTOR).toLong()),
        capturedAtMillis = 0L,
        captivityResolveAtMillis = 0L,
        captureTheater = null
    )

    fun diedInCaptivity(operator: Operator): Operator = operator.copy(
        status = OperatorStatus.FALLEN,
        capturedAtMillis = 0L,
        captivityResolveAtMillis = 0L
    )

    fun isPowOperation(mission: MissionTemplate): Boolean = mission.id == RESCUE_MISSION_ID || mission.id == NEGOTIATION_MISSION_ID

    /** Why this POW operation can't launch for [target], or null. */
    fun operationBlocker(
        mission: MissionTemplate,
        target: Operator?,
        deployments: List<ActiveDeployment>,
        prestige: Double,
        now: Long,
        team: List<Operator> = emptyList()
    ): String? = when {
        target == null || target.status != OperatorStatus.CAPTURED -> "Pick a soldier on the POW list"
        deployments.any { it.targetOperatorId == target.id } -> "An operation for ${target.callsign} is already under way"
        mission.id == RESCUE_MISSION_ID && !rescueWindowOpen(target, now) -> "The rescue window for ${target.callsign} has closed"
        mission.id == NEGOTIATION_MISSION_ID && talksCoolingDown(target, now) -> "Talks for ${target.callsign} collapsed; try again later"
        mission.id == NEGOTIATION_MISSION_ID && team.isNotEmpty() && team.none { it.isSeniorNegotiator() } ->
            "Talks need an officer, warrant officer or senior NCO at the table"
        mission.id == NEGOTIATION_MISSION_ID && prestige < talksPrestigeCost(team) -> "Needs ${talksPrestigeCost(team).toInt()} PRS to open talks"
        else -> null
    }
}
