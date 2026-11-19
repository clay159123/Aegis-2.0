package com.example.domain

import com.example.domain.model.*

/**
 * Session 22: a bigger Barracks (item 116), up to 3 SF squads (item 119) and the SF Compound (item 118).
 * SF squad members live in the Compound and stop counting against the Barracks, up to its capacity.
 */
object CompoundRules {
    const val BARRACKS_BASE_SLOTS = 8
    const val BARRACKS_SLOTS_PER_LEVEL = 3
    const val SOLDIERS_PER_SF_SQUAD = MAX_SQUAD_SIZE
    /** Compound perks: -1% blown-cover risk and 4% faster Medbay recovery for SF soldiers per level; +5% SF schools at level 10. */
    const val BLOWN_COVER_CUT_PER_LEVEL = 1
    const val SF_RECOVERY_CUT_PER_LEVEL = 0.04
    const val SF_SCHOOL_BONUS_AT_MAX = 5

    /** Compound level, set from game state for the pure rules. */
    @Volatile var compoundLevel: Int = 0

    fun barracksSlots(level: Int): Int = BARRACKS_BASE_SLOTS + (level - 1).coerceAtLeast(0) * BARRACKS_SLOTS_PER_LEVEL

    /** SF soldiers the Compound houses: 6 at level 1, 12 at level 4, 18 at level 7. */
    fun compoundCapacity(level: Int): Int = when {
        level >= 7 -> 3 * SOLDIERS_PER_SF_SQUAD
        level >= 4 -> 2 * SOLDIERS_PER_SF_SQUAD
        level >= 1 -> SOLDIERS_PER_SF_SQUAD
        else -> 0
    }

    /** SF squads allowed: one without a Compound, then one per 6 Compound beds (up to 3). */
    fun sfSquadLimit(facilities: List<FacilityState>): Int =
        maxOf(1, compoundCapacity(TacticalCalculator.facilityLevel(facilities, FacilityType.SF_COMPOUND)) / SOLDIERS_PER_SF_SQUAD)

    fun isFieldSoldier(op: Operator): Boolean =
        op.unlocked && op.status != OperatorStatus.HQ_STAFF && !op.status.isOutOfActiveService

    fun sfSquadMemberIds(squads: List<TacticalSquad>): Set<String> =
        squads.filter { it.isSpecialForces }.flatMap { it.memberIds }.toSet()

    /** SF squad members living in the Compound right now. */
    fun compoundUsed(operators: List<Operator>, squads: List<TacticalSquad>, facilities: List<FacilityState>): Int {
        val ids = sfSquadMemberIds(squads)
        val sf = operators.count { it.id in ids && isFieldSoldier(it) }
        return minOf(sf, compoundCapacity(TacticalCalculator.facilityLevel(facilities, FacilityType.SF_COMPOUND)))
    }

    /** Barracks slots in use: every field soldier except SF squad members housed in the Compound. */
    fun fieldRosterUsed(operators: List<Operator>, squads: List<TacticalSquad>, facilities: List<FacilityState>): Int =
        operators.count { isFieldSoldier(it) } - compoundUsed(operators, squads, facilities)

    fun blownCoverCutPct(): Int = compoundLevel * BLOWN_COVER_CUT_PER_LEVEL

    /** Medbay time multiplier for an SF squad member. */
    fun sfRecoveryMultiplier(level: Int = compoundLevel): Double = (1.0 - level * SF_RECOVERY_CUT_PER_LEVEL).coerceAtLeast(0.5)

    fun sfSchoolBonusPct(level: Int = compoundLevel): Int = if (level >= MAX_FACILITY_LEVEL) SF_SCHOOL_BONUS_AT_MAX else 0

    fun perkSummary(level: Int): String =
        if (level <= 0) "Houses SF squads • form an SF squad to build"
        else "Houses ${compoundCapacity(level)} SF soldiers (${compoundCapacity(level) / SOLDIERS_PER_SF_SQUAD} SF squad${if (compoundCapacity(level) > SOLDIERS_PER_SF_SQUAD) "s" else ""}) • " +
            "-${level * BLOWN_COVER_CUT_PER_LEVEL}% blown cover • SF recovery -${(level * SF_RECOVERY_CUT_PER_LEVEL * 100).toInt()}%" +
            if (level >= MAX_FACILITY_LEVEL) " • SF schools +$SF_SCHOOL_BONUS_AT_MAX%" else ""
}
