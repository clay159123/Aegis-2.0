package com.example.domain

import com.example.domain.model.*
import kotlin.math.roundToInt

/**
 * Session 38 (item 70): soldier morale. A 0-100 score that missions, awards, pins, schools, leave, losses and
 * base life move, drifting back toward a baseline the base sets (DFAC, Main Gate, Parade Ground activity).
 */
object MoraleRules {
    const val START = 70
    const val MAX = 100

    // Bands
    const val HIGH_AT = 80
    const val STEADY_AT = 50
    const val LOW_AT = 25

    enum class Band(val label: String, val teamOddsPct: Int) {
        HIGH("High", 2), STEADY("Steady", 0), LOW("Low", -2), BROKEN("Broken", -6)
    }

    fun band(morale: Int): Band = when {
        morale >= HIGH_AT -> Band.HIGH
        morale >= STEADY_AT -> Band.STEADY
        morale >= LOW_AT -> Band.LOW
        else -> Band.BROKEN
    }

    fun band(op: Operator): Band = band(op.morale)

    // Changes
    const val MISSION_SUCCESS = 3
    const val MISSION_FAILURE = -4
    const val WOUNDED = -6
    const val SQUADMATE_KILLED = -10
    const val SQUADMATE_CAPTURED = -5
    const val RALLIED = 5
    const val AWARD_PRESENTED = 5
    const val RANK_PINNED = 6
    const val SCHOOL_PASSED = 3
    const val LEAVE = 10
    const val REQUEST_DECLINED = -8
    const val STREAK_PENALTY = -2
    /** Field deployments in a row before each one more costs morale. */
    const val STREAK_FREE = 3
    /** Time back on base that counts as rest between deployments (testing speed). */
    const val REST_GAP_MILLIS = 10L * 60_000L
    /** A seated Command Sergeant Major softens every morale loss by this share. */
    const val CSM_LOSS_FACTOR = 0.75
    /** Rally bonus on a rescue mission for a captured squadmate. */
    const val RALLY_RESCUE_PASS_PCT = 5

    // Baseline and drift
    const val BASELINE_BASE = 60
    const val BASELINE_PER_DFAC_LEVEL = 2
    const val BASELINE_PER_GATE_LEVEL = 1
    const val BASELINE_PARADE_RECENT = 3
    const val PARADE_RECENT_MILLIS = 60L * 60_000L
    /** Drift toward the baseline: one point per this many millis (testing speed). */
    const val DRIFT_STEP_MILLIS = 60_000L

    /** Set each tick from state: true when a Command Sergeant Major is seated. */
    @Volatile var csmSeated: Boolean = false

    fun baseline(facilities: List<FacilityState>, lastParadeActivityAtMillis: Long, now: Long): Int =
        (BASELINE_BASE +
            BASELINE_PER_DFAC_LEVEL * TacticalCalculator.facilityLevel(facilities, FacilityType.DFAC).coerceAtLeast(0) +
            BASELINE_PER_GATE_LEVEL * TacticalCalculator.facilityLevel(facilities, FacilityType.MAIN_GATE).coerceAtLeast(0) +
            (if (lastParadeActivityAtMillis > 0 && now - lastParadeActivityAtMillis < PARADE_RECENT_MILLIS) BASELINE_PARADE_RECENT else 0)
            ).coerceIn(0, MAX)

    /** Apply [delta]; losses are softened while a CSM is seated. */
    fun adjust(op: Operator, delta: Int): Operator {
        val d = if (delta < 0 && csmSeated) (delta * CSM_LOSS_FACTOR).roundToInt() else delta
        return op.copy(morale = (op.morale + d).coerceIn(0, MAX))
    }

    /** One drift step toward [baseline] (call once per [DRIFT_STEP_MILLIS] elapsed). */
    fun drift(op: Operator, baseline: Int, steps: Int = 1): Operator = when {
        op.morale < baseline -> op.copy(morale = (op.morale + steps).coerceAtMost(baseline))
        op.morale > baseline -> op.copy(morale = (op.morale - steps).coerceAtLeast(baseline))
        else -> op
    }

    /** Team odds from the team's average morale. */
    fun teamOddsPct(team: List<Operator>): Int {
        if (team.isEmpty()) return 0
        return band(team.map { it.morale }.average().roundToInt()).teamOddsPct
    }

    /** Cohesion gains run faster with a high-morale team. */
    fun cohesionFactor(team: List<Operator>): Double =
        if (team.isNotEmpty() && band(team.map { it.morale }.average().roundToInt()) == Band.HIGH) 1.1 else 1.0

    /** Transfer and retirement petition chance multiplier for one soldier. */
    fun petitionFactor(op: Operator): Double = when (band(op)) {
        Band.HIGH -> 0.6
        Band.STEADY -> 1.0
        Band.LOW -> 1.6
        Band.BROKEN -> 2.5
    }

    /** Broken morale keeps a soldier from a promotion board. */
    fun boardBlocker(op: Operator): String? =
        if (band(op) == Band.BROKEN) "Morale ${op.morale}: too low to sit a board (raise it above $LOW_AT)" else null

    /** Deployment streak: the new streak and whether this deployment costs morale. */
    fun nextStreak(op: Operator, departingAt: Long): Int =
        if (op.lastReturnAtMillis > 0 && departingAt - op.lastReturnAtMillis >= REST_GAP_MILLIS) 1 else op.deployStreak + 1

    fun streakPenalty(streak: Int): Int = if (streak > STREAK_FREE) STREAK_PENALTY else 0

    // ---------- Parade Ground activities ----------
    const val UNIT_RUN_MORALE = 3
    const val UNIT_RUN_SUPPLIES = 10.0
    const val UNIT_RUN_COOLDOWN_MILLIS = 10L * 60_000L
    const val COOKOUT_MORALE = 5
    const val COOKOUT_SUPPLIES = 40.0
    const val COOKOUT_CREDITS_PER_10 = 15.0
    const val COOKOUT_COOLDOWN_MILLIS = 30L * 60_000L
    const val FORMATION_MORALE_PER_AWARD = 2
    const val FORMATION_COHESION_XP = 10

    fun cookoutCost(soldiers: Int): Pair<Double, Double> =
        COOKOUT_SUPPLIES to (COOKOUT_CREDITS_PER_10 * kotlin.math.ceil(soldiers / 10.0).coerceAtLeast(1.0))

    // ---------- Squad leave ----------
    const val SQUAD_LEAVE_MORALE = 25
    const val SQUAD_LEAVE_MILLIS = 10L * 60_000L
    const val SQUAD_LEAVE_COOLDOWN_MILLIS = 60L * 60_000L
    const val SQUAD_LEAVE_CREDITS_PER_SOLDIER = 20.0

    // ---------- Uniform inspections ----------
    const val INSPECTION_COOLDOWN_MILLIS = 30L * 60_000L

    fun inspectionMorale(gradeLetter: String): Int = when {
        gradeLetter.startsWith("S") || gradeLetter.startsWith("A+") -> 4
        gradeLetter.startsWith("A") -> 3
        gradeLetter.startsWith("B") -> 2
        else -> 0
    }
}
