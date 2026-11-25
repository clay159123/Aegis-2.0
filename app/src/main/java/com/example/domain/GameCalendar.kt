package com.example.domain

import com.example.domain.model.ActiveDeployment
import com.example.domain.model.MissionRisk
import com.example.domain.model.MissionScope
import com.example.domain.model.MissionTemplate
import com.example.domain.model.Operator
import com.example.domain.model.OperatorRank
import com.example.domain.model.OperatorStatus
import com.example.domain.model.RankTier
import com.example.domain.model.TacticalSquad
import kotlin.math.abs
import kotlin.math.floor

/**
 * Session 41 (item 73): the unit calendar. It runs at a fixed rate whenever any soldier activity is in
 * progress (an op, school, leave, the medbay, captivity or dwell at home), online or offline, and freezes
 * when nothing is running. Everyone on the roster ages with it. Every activity has a game length in days;
 * its real duration is that length at the current [TimeMode] rate (testing: 1 real second per game day).
 */
object GameCalendar {
    const val DAYS_PER_YEAR = 365.0
    const val DAYS_PER_MONTH = DAYS_PER_YEAR / 12.0
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** Today's game date in days since the command stood up. Set every tick (like the other volatile globals). */
    @Volatile var today: Double = 0.0

    fun msPerGameDay(): Long = GameTiming.MODE.secondsPerGameDay * 1000L
    fun daysToMillis(days: Double): Long = (days * msPerGameDay()).toLong()
    fun millisToDays(ms: Long): Double = ms.toDouble() / msPerGameDay()

    // ------------------------------------------------------------
    // Timing table (approved Nov 25 for initial testing)
    // ------------------------------------------------------------

    private val SCHOOL_DAYS: Map<String, Int> = mapOf(
        "msn_rifle_qual" to 7, "msn_flight_hours" to 7,
        "msn_eib_testing" to 14, "msn_air_assault_school" to 14, "msn_sere_school" to 14,
        "msn_mountain_school" to 14, "msn_jungle_school" to 14, "msn_arctic_school" to 14,
        "msn_night_qual" to 21, "msn_pathfinder_school" to 21, "msn_sere3_school" to 21,
        "msn_sapper_school" to 28, "msn_mff_school" to 28, "msn_selection_prep" to 28, "msn_nco_school" to 28,
        "msn_sniper_school" to 42, "msn_wocs" to 42, "msn_snco_school" to 42,
        "msn_combat_diver_school" to 56, "msn_logistics_school" to 56,
        "msn_ranger_school" to 63,
        "msn_basic_qual" to 70,
        "msn_ocs_school" to 84, "msn_sf_aviation_school" to 84,
        "msn_engineer_school" to 98, "msn_medical_school" to 112, "msn_intel_school" to 112,
        "msn_signals_school" to 140,
        "msn_language_school" to 182,
        "msn_cgsc_school" to 304,
        "msn_sf_school" to 365, "msn_flight_school" to 365
    )

    private val SF_TYPE_DAYS: Map<String, Int> = mapOf(
        "Direct Action" to 30,
        "Special Reconnaissance" to 91,
        "Foreign Internal Defense" to 182,
        "Unconventional Warfare" to 122,
        "Clandestine" to 182
    )

    /** The game length of a mission or school, in days. */
    fun gameDaysFor(m: MissionTemplate): Int {
        SCHOOL_DAYS[m.id]?.let { return it }
        if (m.id == "msn_pow_rescue" || m.id == "msn_pow_negotiation") return 14
        m.sfMissionType?.let { t -> SF_TYPE_DAYS[t]?.let { return it } }
        if (m.isClandestine) return 182
        if (m.scope == MissionScope.TRAINING) return 21
        if (m.scope == MissionScope.STAFF_DIPLOMATIC) {
            return when (m.risk) {
                MissionRisk.LOW, MissionRisk.TRAINING -> 7
                MissionRisk.MODERATE -> 14
                else -> 21
            }
        }
        val base = when (m.risk) {
            MissionRisk.LOW, MissionRisk.TRAINING -> 14
            MissionRisk.MODERATE -> 30
            MissionRisk.HIGH -> 61
            MissionRisk.EXTREME -> 91
        }
        return base + if (m.requiredSquads > 1) 30 else 0
    }

    /** Real seconds for a game length at the current rate. */
    fun realSecondsFor(days: Int): Int = (days * GameTiming.MODE.secondsPerGameDay).coerceAtLeast(1)

    const val SQUAD_LEAVE_DAYS = 14.0
    const val EVENT_LEAVE_DAYS = 7.0
    const val BASE_RECOVERY_DAYS = 21.0
    const val CAPTIVITY_DAYS = 182.0
    const val RESCUE_WINDOW_DAYS = 91.0
    const val TALKS_COOLDOWN_DAYS = 30.0

    // ------------------------------------------------------------
    // Dwell time (1:1 to start)
    // ------------------------------------------------------------

    const val DWELL_RATIO = 1.0
    const val POW_RESCUE_ID = "msn_pow_rescue"

    /** Field ops need the soldier home from dwell; POW Rescue never waits. */
    fun dwellBlocks(m: MissionTemplate): Boolean = m.scope == MissionScope.FIELD_COMBAT && m.id != POW_RESCUE_ID

    fun dwellLeftMillis(op: Operator, now: Long): Long = (op.dwellUntilMillis - now).coerceAtLeast(0L)

    fun formatGameSpan(days: Double): String {
        val d = days.coerceAtLeast(0.0)
        return when {
            d >= DAYS_PER_YEAR -> "%.1f yr".format(d / DAYS_PER_YEAR)
            d >= 60 -> "${(d / DAYS_PER_MONTH).toInt()} mo"
            d >= 7 -> "${(d / 7).toInt()} wk"
            else -> "${d.toInt().coerceAtLeast(1)} d"
        }
    }

    /** Compact form for chips: 4d, 5w, 3mo, 1.2y. */
    fun formatGameSpanShort(days: Double): String {
        val d = days.coerceAtLeast(0.0)
        return when {
            d >= DAYS_PER_YEAR -> "%.1fy".format(d / DAYS_PER_YEAR)
            d >= 60 -> "${(d / DAYS_PER_MONTH).toInt()}mo"
            d >= 7 -> "${(d / 7).toInt()}w"
            else -> "${d.toInt().coerceAtLeast(1)}d"
        }
    }

    // ------------------------------------------------------------
    // Clock: union of activity intervals
    // ------------------------------------------------------------

    /** Real-time windows (start, end) when something is in progress. Open-start windows use Long.MIN_VALUE. */
    fun activeIntervals(operators: List<Operator>, deployments: List<ActiveDeployment>, squads: List<TacticalSquad>): List<Pair<Long, Long>> =
        buildList {
            deployments.forEach { add(it.startedAtMillis to it.startedAtMillis + it.durationMillis) }
            operators.forEach { op ->
                if (!op.unlocked || op.status.isOutOfActiveService) return@forEach
                when (op.status) {
                    OperatorStatus.ON_LEAVE, OperatorStatus.RECOVERING -> if (op.recoveryUntilMillis > 0) add(Long.MIN_VALUE to op.recoveryUntilMillis)
                    OperatorStatus.CAPTURED -> when {
                        op.captivityResolveAtMillis > 0 -> add(Long.MIN_VALUE to op.captivityResolveAtMillis)
                        op.evadingUntilMillis > 0 -> add(Long.MIN_VALUE to op.evadingUntilMillis)
                    }
                    else -> {}
                }
                if (op.dwellUntilMillis > 0) add(Long.MIN_VALUE to op.dwellUntilMillis)
            }
            squads.forEach { if (it.leaveUntilMillis > 0) add(Long.MIN_VALUE to it.leaveUntilMillis) }
        }

    /** Milliseconds within [from, to] covered by at least one interval. */
    fun activeMillisBetween(intervals: List<Pair<Long, Long>>, from: Long, to: Long): Long {
        if (to <= from) return 0L
        val clipped = intervals.mapNotNull { (s, e) ->
            val a = maxOf(s, from); val b = minOf(e, to)
            if (b > a) a to b else null
        }.sortedBy { it.first }
        var total = 0L
        var curS = Long.MIN_VALUE; var curE = Long.MIN_VALUE
        for ((s, e) in clipped) {
            if (curE == Long.MIN_VALUE || s > curE) {
                if (curE != Long.MIN_VALUE) total += curE - curS
                curS = s; curE = e
            } else if (e > curE) curE = e
        }
        if (curE != Long.MIN_VALUE) total += curE - curS
        return total
    }

    /** The calendar after [from]..[to] of real time with these activities. */
    fun advance(days: Double, intervals: List<Pair<Long, Long>>, from: Long, to: Long): Double =
        days + millisToDays(activeMillisBetween(intervals, from, to))

    // ------------------------------------------------------------
    // Display
    // ------------------------------------------------------------

    fun year(days: Double): Int = floor(days / DAYS_PER_YEAR).toInt() + 1
    fun monthIndex(days: Double): Int = (floor((days % DAYS_PER_YEAR) / DAYS_PER_MONTH).toInt()).coerceIn(0, 11)

    /** Northern hemisphere seasons. */
    fun season(days: Double): String = when (monthIndex(days)) {
        11, 0, 1 -> "Winter"
        2, 3, 4 -> "Spring"
        5, 6, 7 -> "Summer"
        else -> "Fall"
    }

    fun label(days: Double): String = "Year ${year(days)} · ${MONTHS[monthIndex(days)]} · ${season(days)}"

    // ------------------------------------------------------------
    // Ages and service
    // ------------------------------------------------------------

    fun yearsServed(op: Operator, today: Double = this.today): Double =
        if (op.joinedDay < 0) op.priorServiceDays / DAYS_PER_YEAR
        else (op.priorServiceDays + (today - op.joinedDay).coerceAtLeast(0.0)) / DAYS_PER_YEAR

    fun age(op: Operator, today: Double = this.today): Int =
        (op.ageAtJoin + if (op.joinedDay < 0) 0.0 else (today - op.joinedDay).coerceAtLeast(0.0) / DAYS_PER_YEAR).toInt()

    fun yearsInGrade(op: Operator, today: Double = this.today): Double =
        if (op.rankSinceDay < 0) 0.0 else (today - op.rankSinceDay).coerceAtLeast(0.0) / DAYS_PER_YEAR

    /** Typical years of service for a soldier arriving at this rank. */
    fun typicalServiceYears(rank: OperatorRank): Double = when (rank) {
        OperatorRank.PVT -> 0.0; OperatorRank.PV2 -> 0.3; OperatorRank.PFC -> 1.0; OperatorRank.SPC -> 2.0
        OperatorRank.SGT -> 4.0; OperatorRank.SSG -> 7.0; OperatorRank.SFC -> 11.0; OperatorRank.MSG -> 15.0
        OperatorRank.SGM -> 19.0; OperatorRank.CSM -> 21.0
        OperatorRank.LT2 -> 0.0; OperatorRank.LT1 -> 2.0; OperatorRank.CPT -> 5.0; OperatorRank.MAJ -> 11.0
        OperatorRank.LTC -> 16.0; OperatorRank.COL -> 21.0
        OperatorRank.BG -> 25.0; OperatorRank.MG -> 27.0; OperatorRank.LTG -> 29.0; OperatorRank.GEN -> 31.0
        OperatorRank.WO1 -> 6.0; OperatorRank.CW2 -> 9.0; OperatorRank.CW3 -> 13.0; OperatorRank.CW4 -> 17.0; OperatorRank.CW5 -> 22.0
        else -> 0.0
    }

    /** Age on arrival: enlisted from 18–22, officers 22–24 (warrants come up through the enlisted ranks). */
    fun arrivalAge(op: Operator): Double {
        val jitter = abs(op.id.hashCode() % 1000) / 1000.0
        val service = typicalServiceYears(op.rank)
        return when {
            op.rank.tier == RankTier.OFFICER || op.rank.tier == RankTier.FLAG_OFFICER -> 22.0 + jitter * 2.0 + service
            else -> 18.0 + jitter * 4.0 + service
        }
    }

    // ------------------------------------------------------------
    // Time-based rules
    // ------------------------------------------------------------

    /** Good Conduct: one per 3 clean years of service. */
    const val GOOD_CONDUCT_YEARS = 3.0
    /** Service stripes (enlisted, Alphas left sleeve): one per 3 years. */
    const val SERVICE_STRIPE_YEARS = 3.0
    const val RETIREMENT_ELIGIBLE_YEARS = 20.0
    const val MANDATORY_RETIREMENT_YEARS = 30.0
    const val GENERAL_RETIREMENT_AGE = 62

    fun serviceStripes(op: Operator, today: Double = this.today): Int =
        if (op.rank.tier.isEnlisted) (yearsServed(op, today) / SERVICE_STRIPE_YEARS).toInt().coerceIn(0, 10) else 0

    /** Must retire now: 30 years of service, or a general at the age limit. */
    fun mustRetire(op: Operator, today: Double = this.today): Boolean =
        yearsServed(op, today) >= MANDATORY_RETIREMENT_YEARS ||
            (op.rank.tier == RankTier.FLAG_OFFICER && age(op, today) >= GENERAL_RETIREMENT_AGE)

    /** Minimum years at the current rank before the next (pins and boards). Soldier ranks below SGT promote on XP alone. */
    fun timeInGradeYears(from: OperatorRank): Double = when (from) {
        OperatorRank.SPC -> 1.0
        OperatorRank.SGT -> 1.0
        OperatorRank.SSG -> 2.0
        OperatorRank.SFC -> 3.0
        OperatorRank.MSG -> 3.0
        OperatorRank.LT2 -> 1.5
        OperatorRank.LT1 -> 2.0
        OperatorRank.CPT -> 4.0
        OperatorRank.MAJ -> 3.0
        OperatorRank.LTC -> 3.0
        OperatorRank.COL -> 3.0
        OperatorRank.BG, OperatorRank.MG, OperatorRank.LTG -> 2.0
        OperatorRank.WO1 -> 2.0
        OperatorRank.CW2, OperatorRank.CW3, OperatorRank.CW4 -> 4.0
        else -> 0.0
    }

    fun timeInGradeMet(op: Operator, today: Double = this.today): Boolean =
        op.rankSinceDay < 0 || yearsInGrade(op, today) >= timeInGradeYears(op.rank)
}
