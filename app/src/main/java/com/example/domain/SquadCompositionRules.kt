package com.example.domain

import com.example.domain.model.*

/**
 * Session 33: rank-mix bonuses for a team. Never required; each is worth [BONUS_PCT] on field missions.
 *
 * - Full Detachment (an all-SF team): one officer and one SNCO; every other seat an NCO (SGT/SSG) or a warrant.
 * - Full Squad (any other team): one officer, one SNCO and one NCO; every other seat junior enlisted (PVT-SPC)
 *   or a warrant.
 *
 * Warrants are optional in both. Platoon missions earn the bonus only when every squad in the team qualifies.
 */
object SquadCompositionRules {
    const val BONUS_PCT = 3

    enum class Kind(val label: String) {
        FULL_DETACHMENT("Full Detachment"),
        FULL_SQUAD("Full Squad")
    }

    enum class Seat { OFFICER, WARRANT, SNCO, NCO, JUNIOR, FLAG }

    fun seatOf(op: Operator): Seat = when {
        op.rank.isWarrant -> Seat.WARRANT
        op.rank.tier == RankTier.FLAG_OFFICER -> Seat.FLAG
        op.rank.tier == RankTier.OFFICER -> Seat.OFFICER
        op.rank.tier == RankTier.SNCO -> Seat.SNCO
        op.rank.tier == RankTier.NCO -> Seat.NCO
        else -> Seat.JUNIOR
    }

    /** An SF team is one where every member wears the Tab. */
    fun isSfTeam(members: List<Operator>): Boolean = members.isNotEmpty() && members.all { TacticalCalculator.isSfQualified(it) }

    fun kindFor(members: List<Operator>): Kind = if (isSfTeam(members)) Kind.FULL_DETACHMENT else Kind.FULL_SQUAD

    /** What the team still needs for its composition bonus; empty when it qualifies. */
    fun missing(members: List<Operator>): List<String> {
        if (members.isEmpty()) return listOf("No soldiers")
        val counts = members.groupingBy { seatOf(it) }.eachCount()
        fun n(s: Seat) = counts[s] ?: 0
        val out = mutableListOf<String>()
        if (n(Seat.FLAG) > 0) out += "No generals in the field"
        if (n(Seat.OFFICER) == 0) out += "Needs an officer"
        if (n(Seat.OFFICER) > 1) out += "Only one officer"
        if (n(Seat.SNCO) == 0) out += "Needs a senior NCO"
        if (n(Seat.SNCO) > 1) out += "Only one senior NCO"
        when (kindFor(members)) {
            Kind.FULL_DETACHMENT -> if (n(Seat.JUNIOR) > 0) out += "Other seats must be NCOs or warrants"
            Kind.FULL_SQUAD -> {
                if (n(Seat.NCO) == 0) out += "Needs an NCO"
                if (n(Seat.NCO) > 1) out += "Only one NCO; other seats junior enlisted or warrants"
            }
        }
        return out
    }

    fun qualifies(members: List<Operator>): Kind? = if (missing(members).isEmpty()) kindFor(members) else null

    /**
     * The bonus a team earns on [mission], and which kind. Field missions only. On platoon missions the team is
     * split by squad (soldiers outside the squads form one more group) and every group must qualify.
     */
    fun teamBonus(mission: MissionTemplate, team: List<Operator>, squads: List<TacticalSquad>): Pair<Kind, Int>? {
        if (mission.scope != MissionScope.FIELD_COMBAT || team.isEmpty()) return null
        val groups = if (mission.requiredSquads > 1) {
            team.groupBy { op -> squads.firstOrNull { op.id in it.memberIds }?.id }.values.toList()
        } else listOf(team)
        val kinds = groups.map { qualifies(it) ?: return null }
        val kind = if (kinds.all { it == Kind.FULL_DETACHMENT }) Kind.FULL_DETACHMENT else Kind.FULL_SQUAD
        return kind to BONUS_PCT
    }
}
