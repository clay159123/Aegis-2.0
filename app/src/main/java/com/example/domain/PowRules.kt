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
        recoveryUntilMillis = 0L,
        hqRole = null
    )

    fun rescueWindowOpen(operator: Operator, now: Long): Boolean =
        operator.status == OperatorStatus.CAPTURED && now < operator.capturedAtMillis + GameTiming.millis(RESCUE_WINDOW_MILLIS)

    fun rescueWindowRemainingMillis(operator: Operator, now: Long): Long =
        (operator.capturedAtMillis + GameTiming.millis(RESCUE_WINDOW_MILLIS) - now).coerceAtLeast(0L)

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
    fun operationBlocker(mission: MissionTemplate, target: Operator?, deployments: List<ActiveDeployment>, prestige: Double, now: Long): String? = when {
        target == null || target.status != OperatorStatus.CAPTURED -> "Pick a soldier on the POW list"
        deployments.any { it.targetOperatorId == target.id } -> "An operation for ${target.callsign} is already under way"
        mission.id == RESCUE_MISSION_ID && !rescueWindowOpen(target, now) -> "The rescue window for ${target.callsign} has closed"
        mission.id == NEGOTIATION_MISSION_ID && prestige < NEGOTIATION_PRESTIGE_COST -> "Needs ${NEGOTIATION_PRESTIGE_COST.toInt()} PRS to open talks"
        else -> null
    }
}
