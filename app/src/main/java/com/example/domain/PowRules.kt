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
    /** SERE Level 3 graduates are harder still to capture. */
    const val SERE3_CAPTURE_FACTOR = 0.4
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
        val sere = when {
            TacticalCalculator.sereLevel(operator) >= 3 -> SERE3_CAPTURE_FACTOR
            TacticalCalculator.isSereQualified(operator) -> SERE_CAPTURE_FACTOR
            else -> 1.0
        }
        val cover = if (blownCover) BLOWN_COVER_FACTOR else 1.0
        val sniper = if (TacticalCalculator.isSniper(operator)) TacticalCalculator.SNIPER_CAPTURE_FACTOR else 1.0
        return base * statFactor * sere * cover * sniper
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
            powTalksCooldownUntilMillis = now + GameCalendar.daysToMillis(GameCalendar.TALKS_COOLDOWN_DAYS)
        )
        val who = "${target.rank.code} ${target.callsign}"
        if (random.nextDouble() < BAD_FAITH_CHANCE) {
            return diedInCaptivity(hardened) to "Talks failed in bad faith: $who was killed in captivity."
        }
        val moved = random.nextDouble() < PRISONER_MOVED_CHANCE
        val note = "Talks for $who collapsed. No new talks for ${GameCalendar.formatGameSpan(GameCalendar.TALKS_COOLDOWN_DAYS)}; " +
            "the captors are harder to deal with (-${hardened.powTalksFailures * HARDENED_PENALTY_PCT}%)." +
            if (moved) " They moved the prisoner: the rescue window is closed." else ""
        return hardened.copy(powRescueClosed = hardened.powRescueClosed || moved) to note
    }
    /** Returning POWs spend twice the usual Medbay time recovering. */
    const val RETURN_RECOVERY_FACTOR = 2.0

    fun captivityMillis(operator: Operator): Long {
        val base = GameCalendar.daysToMillis(GameCalendar.CAPTIVITY_DAYS)
        return if (TacticalCalculator.isSereQualified(operator)) (base * SERE_CAPTIVITY_FACTOR).toLong() else base
    }

    fun releaseSurvivalPct(operator: Operator): Int =
        if (TacticalCalculator.isSereQualified(operator)) SERE_RELEASE_SURVIVAL_PCT else RELEASE_SURVIVAL_PCT

    fun capture(operator: Operator, mission: MissionTemplate, now: Long): Operator = operator.copy(
        status = OperatorStatus.CAPTURED,
        capturedAtMillis = now,
        captivityResolveAtMillis = now + captivityMillis(operator),
        captivityTerm = 1,
        escapeOffered = false,
        evadingUntilMillis = 0L,
        captorsHardenedPct = 0,
        captureTheater = mission.theater,
        timesCaptured = operator.timesCaptured + 1,
        powTalksFailures = 0,
        powTalksCooldownUntilMillis = 0L,
        powRescueClosed = false,
        recoveryUntilMillis = 0L,
        hqRole = null
    )

    /**
     * Fix for saves made before the capture record was kept: a held soldier with no capture time gets one now
     * (a fresh rescue window and wait), and a soldier home with a POW Medal waiting gets their capture counted.
     * Returns null when nothing needs fixing.
     */
    fun repairedRecord(op: Operator, hasPendingPowMedal: Boolean, now: Long): Operator? = when {
        op.status == OperatorStatus.CAPTURED && op.capturedAtMillis == 0L -> op.copy(
            capturedAtMillis = now,
            captivityResolveAtMillis = now + captivityMillis(op),
            timesCaptured = op.timesCaptured.coerceAtLeast(1)
        )
        op.status != OperatorStatus.CAPTURED && hasPendingPowMedal && op.timesCaptured == 0 -> op.copy(timesCaptured = 1)
        else -> null
    }

    fun rescueWindowOpen(operator: Operator, now: Long): Boolean =
        operator.status == OperatorStatus.CAPTURED && !operator.powRescueClosed &&
            now < operator.capturedAtMillis + GameCalendar.daysToMillis(GameCalendar.RESCUE_WINDOW_DAYS)

    fun rescueWindowRemainingMillis(operator: Operator, now: Long): Long =
        if (operator.powRescueClosed) 0L else (operator.capturedAtMillis + GameCalendar.daysToMillis(GameCalendar.RESCUE_WINDOW_DAYS) - now).coerceAtLeast(0L)

    /** Home again: into Medbay for a longer recovery, captivity record kept for the POW Medal. */
    fun released(operator: Operator, now: Long, recoveryMultiplier: Double): Operator = operator.copy(
        status = OperatorStatus.RECOVERING,
        recoveryUntilMillis = now + GameTiming.millis((TacticalCalculator.BASE_RECOVERY_MILLIS * recoveryMultiplier * RETURN_RECOVERY_FACTOR).toLong()),
        capturedAtMillis = 0L,
        captivityResolveAtMillis = 0L,
        captureTheater = null,
        captivityTerm = 0,
        escapeOffered = false,
        evadingUntilMillis = 0L,
        captorsHardenedPct = 0
    )

    fun diedInCaptivity(operator: Operator): Operator = operator.copy(
        status = OperatorStatus.FALLEN,
        fallenNote = "Died in captivity" + (operator.captureTheater?.let { ": ${it.label}" } ?: ""),
        capturedAtMillis = 0L,
        captivityResolveAtMillis = 0L,
        evadingUntilMillis = 0L
    )

    // ============================================================
    // Session 43 (item 73): captivity terms, escape, Personnel Recovery
    // ============================================================

    const val MAX_TERMS = 3
    /** End of a term: released / held another term / dies (percent). SERE graduates fare better. */
    const val RELEASE_PCT = 55
    const val HELD_PCT = 30
    const val SERE_RELEASE_PCT = 65
    const val SERE_HELD_PCT = 28
    /** The last term: released or dies only. */
    const val FINAL_RELEASE_PCT = 80
    const val SERE_FINAL_RELEASE_PCT = 90
    const val HARDENED_STEP_PCT = 10
    /** Squadmates lose this much morale per term each time a POW is held over. */
    const val HELD_OVER_MORALE_PER_TERM = -4
    const val ESCAPE_PCT_NONE = 5
    const val ESCAPE_PCT_SERE2 = 12
    const val ESCAPE_PCT_SERE3 = 20
    const val FAILED_ESCAPE_DEATH_PCT = 10
    const val SERE3_FAILED_ESCAPE_DEATH_PCT = 5
    const val EVADE_WINDOW_DAYS = 14.0
    const val PERSONNEL_RECOVERY_PASS_BONUS = 20

    /** Set while a rescue is staged or resolved, so the shared odds formula knows it's a Personnel Recovery. */
    @Volatile var activeRescueTargetEvading: Boolean = false

    enum class TermResult { RELEASED, HELD, DIED }

    fun isEvading(op: Operator, now: Long): Boolean = op.status == OperatorStatus.CAPTURED && op.evadingUntilMillis > now

    /** Odds (percent) at the end of a term: released, held over, died. Hardened captors move release odds to held. */
    fun termOdds(op: Operator): Triple<Int, Int, Int> {
        val sere = TacticalCalculator.isSereQualified(op)
        if (op.captivityTerm >= MAX_TERMS) {
            val rel = ((if (sere) SERE_FINAL_RELEASE_PCT else FINAL_RELEASE_PCT) - op.captorsHardenedPct).coerceIn(5, 95)
            return Triple(rel, 0, 100 - rel)
        }
        val baseRel = if (sere) SERE_RELEASE_PCT else RELEASE_PCT
        val baseHeld = if (sere) SERE_HELD_PCT else HELD_PCT
        val rel = (baseRel - op.captorsHardenedPct).coerceAtLeast(5)
        val held = baseHeld + (baseRel - rel)
        return Triple(rel, held, 100 - rel - held)
    }

    fun rollTerm(op: Operator, roll: Int): TermResult {
        val (rel, held, _) = termOdds(op)
        return when {
            roll < rel -> TermResult.RELEASED
            roll < rel + held -> TermResult.HELD
            else -> TermResult.DIED
        }
    }

    /** Held over: a new term. The rescue window and talks reopen, and another escape chance will come. */
    fun newTerm(op: Operator, now: Long): Operator = op.copy(
        captivityTerm = (op.captivityTerm + 1).coerceAtMost(MAX_TERMS),
        capturedAtMillis = now,
        captivityResolveAtMillis = now + captivityMillis(op),
        powRescueClosed = false,
        powTalksCooldownUntilMillis = 0L,
        escapeOffered = false,
        evadingUntilMillis = 0L
    )

    fun escapeChancePct(op: Operator): Int = when (TacticalCalculator.sereLevel(op)) {
        3 -> ESCAPE_PCT_SERE3
        2 -> ESCAPE_PCT_SERE2
        else -> ESCAPE_PCT_NONE
    }

    fun failedEscapeDeathPct(op: Operator): Int =
        if (TacticalCalculator.sereLevel(op) >= 3) SERE3_FAILED_ESCAPE_DEATH_PCT else FAILED_ESCAPE_DEATH_PCT

    /** Partway through the term, once: the escape chance comes up. */
    fun escapeDue(op: Operator, now: Long): Boolean =
        op.status == OperatorStatus.CAPTURED && !op.escapeOffered && op.evadingUntilMillis == 0L &&
            op.captivityResolveAtMillis > 0 && now >= op.capturedAtMillis + (op.captivityResolveAtMillis - op.capturedAtMillis) / 2

    /** A successful escape: out, but not home. The term clock stops while they evade. */
    fun escaped(op: Operator, now: Long): Operator = op.copy(
        evadingUntilMillis = now + GameCalendar.daysToMillis(EVADE_WINDOW_DAYS),
        captivityResolveAtMillis = 0L,
        escapeOffered = true
    )

    /** Caught after evading (window lapsed or Personnel Recovery failed): a fresh term with harder captors. */
    fun recaptured(op: Operator, now: Long): Operator =
        newTerm(op.copy(captorsHardenedPct = op.captorsHardenedPct + HARDENED_STEP_PCT, captivityTerm = op.captivityTerm), now)

    fun evasionLapsed(op: Operator, now: Long): Boolean =
        op.status == OperatorStatus.CAPTURED && op.evadingUntilMillis in 1..now

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
        mission.id == RESCUE_MISSION_ID && !isEvading(target, now) && !rescueWindowOpen(target, now) -> "The rescue window for ${target.callsign} has closed"
mission.id == NEGOTIATION_MISSION_ID && isEvading(target, now) -> "${target.callsign} is on the run; send a Personnel Recovery"
        mission.id == NEGOTIATION_MISSION_ID && talksCoolingDown(target, now) -> "Talks for ${target.callsign} collapsed; try again later"
        mission.id == NEGOTIATION_MISSION_ID && team.isNotEmpty() && team.none { it.isSeniorNegotiator() } ->
            "Talks need an officer, warrant officer or senior NCO at the table"
        mission.id == NEGOTIATION_MISSION_ID && prestige < talksPrestigeCost(team) -> "Needs ${talksPrestigeCost(team).toInt()} PRS to open talks"
        else -> null
    }
}
