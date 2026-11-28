package com.example.domain

import com.example.domain.model.FacilityState
import com.example.domain.model.FacilityType
import com.example.domain.model.MAX_SQUAD_COHESION_XP
import com.example.domain.model.Operator
import com.example.domain.model.OperatorStatus
import com.example.domain.model.isHqStaffer
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Session 44 (item 71): base-wide events at a home-country Fort. Calendar events come on a set month in their year ([CYCLE])
 * (and change of command when a new CO is seated); random ones about once a game quarter. Each event draws
 * whoever is home and available, never needs a full squad, scales its rewards with turnout, and takes the
 * attendees off deployment for its length (AT EVENT). Formal events put attendees in Mess Dress; the honor guard
 * wears Alphas. A pop-up left alone goes ahead with the cheaper default.
 */
object BaseEvents {
    /** Scheduled month index (0 = Jan) for calendar events. */
    val SCHEDULED: Map<SoldierEventKind, Int> = mapOf(
        SoldierEventKind.BASE_FAMILY_DAY to 3,      // April
        SoldierEventKind.BASE_ARMY_BALL to 5,       // June (Army Birthday)
        SoldierEventKind.BASE_IG_INSPECTION to 9,   // October
        SoldierEventKind.BASE_BLOCK_LEAVE to 11     // December
    )

    /**
     * Item 198: calendar events no longer come every year. The Ball and block leave alternate years (the Ball
     * in even years, block leave in odd ones); the IG and Family Day come every 4 years, staggered so they never
     * share a year. Year 1: block leave and the IG; year 2: the Ball; year 3: block leave and Family Day; year 4: the Ball.
     */
    val CYCLE: Map<SoldierEventKind, Pair<Int, Int>> = mapOf(
        SoldierEventKind.BASE_ARMY_BALL to (2 to 0),
        SoldierEventKind.BASE_BLOCK_LEAVE to (2 to 1),
        SoldierEventKind.BASE_IG_INSPECTION to (4 to 1),
        SoldierEventKind.BASE_FAMILY_DAY to (4 to 3)
    )

    /** Whether a calendar event comes in the game year that [days] falls in. */
    fun scheduledThisYear(kind: SoldierEventKind, days: Double): Boolean {
        val (every, offset) = CYCLE[kind] ?: return true
        return GameCalendar.year(days) % every == offset
    }
    const val RANDOM_EVERY_DAYS = 91.0
    const val HONOR_GUARD_MIN = 4
    const val GATE_RESPONSE_MIN = 2

    /** The cheaper choice taken when a pop-up is left alone. */
    fun defaultChoice(kind: SoldierEventKind): String = when (kind) {
        SoldierEventKind.BASE_ARMY_BALL, SoldierEventKind.BASE_FAMILY_DAY -> "skip"
        SoldierEventKind.BASE_IG_INSPECTION -> "wing"
        SoldierEventKind.BASE_GENERAL_VISIT -> "brief"
        SoldierEventKind.BASE_CHANGE_OF_COMMAND -> "simple"
        SoldierEventKind.BASE_BLOCK_LEAVE -> "skeleton"
        SoldierEventKind.BASE_GATE_INCIDENT -> "lockdown"
        SoldierEventKind.BASE_STORM -> "wait"
        SoldierEventKind.BASE_FISCAL_YEAR_END -> "bank"
        else -> "decline"
    }

    fun gateLevel(facilities: List<FacilityState>): Int = TacticalCalculator.facilityLevel(facilities, FacilityType.MAIN_GATE)

    // ------------------------------------------------------------
    // Who can attend
    // ------------------------------------------------------------

    fun available(op: Operator, ctx: SoldierEventContext): Boolean =
        op.unlocked && (op.status == OperatorStatus.READY || op.status == OperatorStatus.HQ_STAFF) &&
            op.id !in ctx.deployedIds && op.atEventUntilMillis <= ctx.now && !ContractRules.contractDue(op)

    fun hqHome(ctx: SoldierEventContext) = ctx.operators.filter { available(it, ctx) && it.isHqStaffer }
    fun soldiersHome(ctx: SoldierEventContext) = ctx.operators.filter { available(it, ctx) && !it.isHqStaffer && !it.isAviator }
    fun everyoneHome(ctx: SoldierEventContext) = ctx.operators.filter { available(it, ctx) }

    /** Active roster for turnout (not fallen, retired, transferred). */
    private fun roster(ctx: SoldierEventContext) = ctx.operators.count { it.unlocked && !it.status.isOutOfActiveService }.coerceAtLeast(1)

    fun turnout(attendees: Int, ctx: SoldierEventContext): Double = (attendees.toDouble() / roster(ctx)).coerceIn(0.0, 1.0)

    /** Anyone at all for this event? Calendar events wait for people to come home. */
    fun anyoneFor(kind: SoldierEventKind, ctx: SoldierEventContext): Boolean = when (kind) {
        SoldierEventKind.BASE_GATE_INCIDENT, SoldierEventKind.BASE_STORM, SoldierEventKind.BASE_FISCAL_YEAR_END -> true
        else -> everyoneHome(ctx).isNotEmpty()
    }

    // ------------------------------------------------------------
    // Scheduling
    // ------------------------------------------------------------

    fun key(kind: SoldierEventKind, today: Double) = "${kind.name}:${GameCalendar.year(today)}"

    /** A calendar event due this month that hasn't run yet this year. */
    fun dueScheduled(today: Double, done: Set<String>): SoldierEventKind? {
        val month = GameCalendar.monthIndex(today)
        return SCHEDULED.entries.firstOrNull { (k, m) -> month >= m && scheduledThisYear(k, today) && key(k, today) !in done }?.key
    }

    /** Past the month's grace with nobody home: it passes with the default. */
    fun scheduledLapsed(kind: SoldierEventKind, today: Double): Boolean {
        val m = SCHEDULED[kind] ?: return false
        return GameCalendar.monthIndex(today) >= m + 2
    }

    /** Next calendar event within a month, for the Ops header ("Army Birthday Ball in Jun"). */
    fun upcoming(today: Double, done: Set<String>): Pair<SoldierEventKind, String>? {
        val inMonth = GameCalendar.monthIndex(today + GameCalendar.DAYS_PER_MONTH)
        val ahead = today + GameCalendar.DAYS_PER_MONTH
        return SCHEDULED.entries.firstOrNull { (k, m) -> m == inMonth && GameCalendar.monthIndex(today) != m && scheduledThisYear(k, ahead) && key(k, ahead) !in done }
            ?.let { (k, _) -> k to GameCalendar.label(today + GameCalendar.DAYS_PER_MONTH).split(" · ")[1] }
    }

    /** A random event for this quarter, or null. Bad events get rarer with each Main Gate level. */
    fun pickRandom(ctx: SoldierEventContext, today: Double, random: Random = Random): Pair<SoldierEventKind, String?>? {
        val gate = gateLevel(ctx.facilities)
        val season = GameCalendar.season(today)
        val bad = (1.0 - 0.08 * gate).coerceAtLeast(0.3)
        val options = buildList {
            if (hqHome(ctx).isNotEmpty() || soldiersHome(ctx).size >= HONOR_GUARD_MIN) add(Triple(SoldierEventKind.BASE_GENERAL_VISIT, null as String?, 1.0))
            add(Triple(SoldierEventKind.BASE_GATE_INCIDENT, null, bad))
            if (season == "Winter" || season == "Summer") add(Triple(SoldierEventKind.BASE_STORM, null, bad))
            if (GameCalendar.monthIndex(today) == 8) {
                val windfall = random.nextDouble() < (0.5 + 0.05 * gate).coerceAtMost(0.9)
                add(Triple(SoldierEventKind.BASE_FISCAL_YEAR_END, if (windfall) "WINDFALL" else "SHORTAGE", 1.5))
            }
        }
        if (options.isEmpty()) return null
        var roll = random.nextDouble() * options.sumOf { it.third }
        for ((k, t, w) in options) {
            roll -= w
            if (roll <= 0) return k to t
        }
        return options.last().let { it.first to it.second }
    }

    // ------------------------------------------------------------
    // Cards
    // ------------------------------------------------------------

    private fun c(id: String, label: String, result: String) = SoldierEventChoice(id, label, result)

    fun card(e: PendingSoldierEvent, ctx: SoldierEventContext): SoldierEventCard {
        val all = everyoneHome(ctx)
        val hq = hqHome(ctx)
        val soldiers = soldiersHome(ctx)
        val gate = gateLevel(ctx.facilities)
        val title = e.kind.title
        return when (e.kind) {
            SoldierEventKind.BASE_ARMY_BALL -> SoldierEventCard(title,
                "The Army Birthday Ball. ${all.size} soldiers and staff are home to attend.",
                listOf(c("host", "Host it (${ballCost(all.size)} CR)", "${all.size} attend in Mess Dress for 2 days: +8 morale each, Prestige by turnout."),
                    c("skip", "Skip it", "-2 morale across the base.")))
            SoldierEventKind.BASE_FAMILY_DAY -> SoldierEventCard(title,
                "Spring Family Day on the parade ground. ${all.size} are home.",
                listOf(c("host", "Host it (${20 + all.size * 2} SUP)", "${all.size} take a day: +6 morale each${if (gate > 0) " (+$gate for the Main Gate)" else ""}."),
                    c("skip", "Skip it", "Nothing changes.")))
            SoldierEventKind.BASE_IG_INSPECTION -> SoldierEventCard(title,
                "The Inspector General arrives next month. HQ staff home: ${hq.size}; soldiers home: ${soldiers.size}.",
                listOf(c("prep", "Prep for a week", "HQ and up to 12 soldiers home for 1 week: 80% a clean report (+15 PRS), else minor findings (+5 PRS)."),
                    c("wing", "Wing it", "No one pulled: 50% +20 PRS, 50% -15 PRS and -3 morale across the base.")))
            SoldierEventKind.BASE_GENERAL_VISIT -> {
                val guardOk = soldiers.size >= HONOR_GUARD_MIN
                SoldierEventCard(title,
                    "A general is visiting the Fort.",
                    listOf(
                        c("guard", if (guardOk) "Honor guard (${HONOR_GUARD_MIN}+ soldiers)" else "Honor guard (needs $HONOR_GUARD_MIN soldiers home)",
                            if (guardOk) "Up to 8 soldiers in Alphas for 2 days: +${visitPrestige(12, gate)} PRS." else "Not enough soldiers at home."),
                        c("brief", "Brief only", if (hq.isNotEmpty()) "HQ staff brief them: +${visitPrestige(4, gate)} PRS." else "No HQ staff home: nothing comes of it.")))
            }
            SoldierEventKind.BASE_CHANGE_OF_COMMAND -> {
                val newCo = ctx.op(e.targetId)?.let { "${it.rank.code} ${it.callsign}" } ?: "The new commander"
                SoldierEventCard(title,
                    "$newCo takes command of the Fort.",
                    listOf(c("full", "Full ceremony", "HQ and one squad's soldiers home in Mess Dress for 2 days: +5 morale each, +8 PRS."),
                        c("simple", "Simple handover", "Nothing changes.")))
            }
            SoldierEventKind.BASE_BLOCK_LEAVE -> SoldierEventCard(title,
                "Holiday block leave. ${all.size} soldiers and staff are home.",
                listOf(c("all", "Everyone (2 weeks)", "All ${all.size} on leave 2 weeks: +12 morale each. No deployments for them meanwhile."),
                    c("skeleton", "Skeleton crew", "Half go on leave 2 weeks: +6 morale each; the rest stay ready.")))
            SoldierEventKind.BASE_GATE_INCIDENT -> {
                val ok = soldiers.size >= GATE_RESPONSE_MIN
                SoldierEventCard(title,
                    "A security incident at the Main Gate.",
                    listOf(c("respond", if (ok) "Send a team (${GATE_RESPONSE_MIN}+ soldiers)" else "Send a team (needs $GATE_RESPONSE_MIN soldiers home)",
                        if (ok) "Up to 4 soldiers on it for 1 week: +3 PRS." else "Not enough soldiers at home."),
                        c("lockdown", "Lock down the Fort", "Base production -${lockdownPct(gate)}% for 2 weeks.")))
            }
            SoldierEventKind.BASE_STORM -> SoldierEventCard(title,
                "A ${if (GameCalendar.season(GameCalendar.today) == "Winter") "winter storm" else "summer storm"} hits the Fort.",
                listOf(c("repair", "Repair now (${stormCost(gate)} SUP)", "No lasting damage."),
                    c("wait", "Ride it out", "A building upgrade under way slows by a fifth (or 40 SUP lost if none).")))
            SoldierEventKind.BASE_FISCAL_YEAR_END -> if (e.targetId == "WINDFALL") SoldierEventCard(title,
                "Fiscal year-end: leftover funds came down to the Fort.",
                listOf(c("gear", "Spend on supplies", "+150 SUP."), c("bank", "Bank it", "+150 CR.")))
            else SoldierEventCard(title,
                "Fiscal year-end shortfall: the budget came up short.",
                listOf(c("cover", "Cover it (120 CR)", "Nothing else changes."), c("bank", "Tighten belts", "-4 morale across the base.")))
            else -> SoldierEventCard(title, "", listOf(c("decline", "Close", "")))
        }
    }

    fun ballCost(attendees: Int) = 40 + attendees * 5
    fun visitPrestige(base: Int, gate: Int) = (base * (1.0 + 0.1 * gate)).roundToInt()
    fun lockdownPct(gate: Int) = (10 - gate).coerceAtLeast(3)
    fun stormCost(gate: Int) = (80 - gate * 8).coerceAtLeast(20)

    // ------------------------------------------------------------
    // Outcomes
    // ------------------------------------------------------------

    private fun atEvent(op: Operator, ctx: SoldierEventContext, days: Double, kit: String?) =
        op.copy(atEventUntilMillis = ctx.now + GameCalendar.daysToMillis(days), eventKit = kit)

    fun apply(e: PendingSoldierEvent, choiceId: String, ctx: SoldierEventContext, random: Random = Random): SoldierEventOutcome {
        val out = applyChoice(e, choiceId, ctx, random)
        // Item 197: everyone newly pulled to the event carries its name, for the countdown on Ops
        val before = ctx.operators.associateBy { it.id }
        return out.copy(operatorUpdates = out.operatorUpdates.map { op ->
            if (op.atEventUntilMillis > ctx.now && op.atEventUntilMillis != before[op.id]?.atEventUntilMillis) op.copy(eventLabel = e.kind.title) else op
        })
    }

    private fun applyChoice(e: PendingSoldierEvent, choiceId: String, ctx: SoldierEventContext, random: Random): SoldierEventOutcome {
        val all = everyoneHome(ctx)
        val hq = hqHome(ctx)
        val soldiers = soldiersHome(ctx)
        val gate = gateLevel(ctx.facilities)
        val everyone = ctx.operators.filter { it.unlocked && !it.status.isOutOfActiveService }
        fun lacks(cr: Int = 0, sup: Int = 0) = ctx.resources.credits < cr || ctx.resources.supplies < sup
        return when (e.kind) {
            SoldierEventKind.BASE_ARMY_BALL -> when (choiceId) {
                "host" -> {
                    val cost = ballCost(all.size)
                    if (lacks(cr = cost)) return SoldierEventOutcome(banner = "Not enough credits to host the Ball.")
                    val prs = (20 * turnout(all.size, ctx)).roundToInt()
                    SoldierEventOutcome(operatorUpdates = all.map { atEvent(MoraleRules.adjust(it, 8), ctx, 2.0, "MESS") },
                        creditsDelta = -cost.toDouble(), prestigeDelta = prs.toDouble(),
                        banner = "The Army Birthday Ball: ${all.size} in Mess Dress (+8 morale, +$prs PRS).")
                }
                else -> SoldierEventOutcome(operatorUpdates = everyone.map { MoraleRules.adjust(it, -2) }, banner = "The Ball was skipped this year (-2 morale).")
            }
            SoldierEventKind.BASE_FAMILY_DAY -> when (choiceId) {
                "host" -> {
                    val cost = 20 + all.size * 2
                    if (lacks(sup = cost)) return SoldierEventOutcome(banner = "Not enough supplies for Family Day.")
                    SoldierEventOutcome(operatorUpdates = all.map { atEvent(MoraleRules.adjust(it, 6 + gate), ctx, 1.0, null) },
                        suppliesDelta = -cost.toDouble(), banner = "Family Day: ${all.size} took the day (+${6 + gate} morale).")
                }
                else -> SoldierEventOutcome(banner = "No Family Day this spring.")
            }
            SoldierEventKind.BASE_IG_INSPECTION -> when (choiceId) {
                "prep" -> {
                    val pulled = hq + soldiers.take(12)
                    val clean = random.nextInt(100) < 80
                    SoldierEventOutcome(operatorUpdates = pulled.map { atEvent(it, ctx, 7.0, null) }, prestigeDelta = if (clean) 15.0 else 5.0,
                        banner = if (clean) "IG inspection: clean report (+15 PRS)." else "IG inspection: minor findings (+5 PRS).")
                }
                else -> if (random.nextBoolean()) SoldierEventOutcome(prestigeDelta = 20.0, banner = "Winged the IG and it went fine (+20 PRS).")
                else SoldierEventOutcome(operatorUpdates = everyone.map { MoraleRules.adjust(it, -3) }, prestigeDelta = -15.0,
                    banner = "The IG found problems (-15 PRS, -3 morale).")
            }
            SoldierEventKind.BASE_GENERAL_VISIT -> when (choiceId) {
                "guard" -> {
                    if (soldiers.size < HONOR_GUARD_MIN) return SoldierEventOutcome(banner = "Not enough soldiers home for an honor guard.")
                    val guard = soldiers.take(8)
                    val prs = visitPrestige(12, gate)
                    SoldierEventOutcome(operatorUpdates = guard.map { atEvent(it, ctx, 2.0, "ALPHAS") }, prestigeDelta = prs.toDouble(),
                        banner = "Honor guard of ${guard.size} in Alphas for the general (+$prs PRS).")
                }
                else -> if (hq.isEmpty()) SoldierEventOutcome(banner = "No HQ staff home; the general's visit came and went.")
                else visitPrestige(4, gate).let { SoldierEventOutcome(prestigeDelta = it.toDouble(), banner = "HQ briefed the general (+$it PRS).") }
            }
            SoldierEventKind.BASE_CHANGE_OF_COMMAND -> when (choiceId) {
                "full" -> {
                    val squad = ctx.squads.maxByOrNull { sq -> soldiers.count { it.id in sq.memberIds } }
                    val party = hq + soldiers.filter { squad != null && it.id in squad.memberIds }
                    SoldierEventOutcome(operatorUpdates = party.map { atEvent(MoraleRules.adjust(it, 5), ctx, 2.0, "MESS") }, prestigeDelta = 8.0,
                        banner = "Change of command ceremony: ${party.size} in Mess Dress (+5 morale, +8 PRS).")
                }
                else -> SoldierEventOutcome(banner = "A simple handover; command has changed.")
            }
            SoldierEventKind.BASE_BLOCK_LEAVE -> {
                val going = if (choiceId == "all") all else all.filterIndexed { i, _ -> i % 2 == 0 }
                val bump = if (choiceId == "all") 12 else 6
                SoldierEventOutcome(operatorUpdates = going.map {
                    MoraleRules.adjust(it, bump).copy(status = OperatorStatus.ON_LEAVE,
                        recoveryUntilMillis = ctx.now + GameCalendar.daysToMillis(GameCalendar.SQUAD_LEAVE_DAYS), deployStreak = 0)
                }, banner = "Holiday block leave: ${going.size} home for the holidays (+$bump morale).")
            }
            SoldierEventKind.BASE_GATE_INCIDENT -> when (choiceId) {
                "respond" -> {
                    if (soldiers.size < GATE_RESPONSE_MIN) return SoldierEventOutcome(banner = "Not enough soldiers home to respond.")
                    val team = soldiers.take(4)
                    SoldierEventOutcome(operatorUpdates = team.map { atEvent(it, ctx, 7.0, null) }, prestigeDelta = 3.0,
                        banner = "${team.size} soldiers handled the gate incident (+3 PRS).")
                }
                else -> SoldierEventOutcome(productionCutPct = lockdownPct(gate),
                    productionCutUntilMillis = ctx.now + GameCalendar.daysToMillis(14.0),
                    banner = "The Fort is locked down: production -${lockdownPct(gate)}% for 2 weeks.")
            }
            SoldierEventKind.BASE_STORM -> when (choiceId) {
                "repair" -> {
                    val cost = stormCost(gate)
                    if (lacks(sup = cost)) return SoldierEventOutcome(banner = "Not enough supplies for repairs.")
                    SoldierEventOutcome(suppliesDelta = -cost.toDouble(), banner = "Storm damage repaired.")
                }
                else -> {
                    val building = ctx.facilities.firstOrNull { it.isUpgrading }
                    if (building != null) {
                        val total = building.upgradeEndsAtMillis - building.upgradeStartedAtMillis
                        SoldierEventOutcome(facilityUpdates = listOf(building.copy(upgradeEndsAtMillis = building.upgradeEndsAtMillis + total / 5)),
                            banner = "The storm slowed work on the ${building.type.title}.")
                    } else SoldierEventOutcome(suppliesDelta = -40.0, banner = "The storm cost 40 SUP in damage.")
                }
            }
            SoldierEventKind.BASE_FISCAL_YEAR_END -> if (e.targetId == "WINDFALL") {
                if (choiceId == "gear") SoldierEventOutcome(suppliesDelta = 150.0, banner = "Year-end funds: +150 SUP.")
                else SoldierEventOutcome(creditsDelta = 150.0, banner = "Year-end funds banked: +150 CR.")
            } else {
                if (choiceId == "cover") {
                    if (lacks(cr = 120)) return SoldierEventOutcome(banner = "Not enough credits to cover it.")
                    SoldierEventOutcome(creditsDelta = -120.0, banner = "Shortfall covered (-120 CR).")
                } else SoldierEventOutcome(operatorUpdates = everyone.map { MoraleRules.adjust(it, -4) }, banner = "Belts tightened (-4 morale).")
            }
            else -> SoldierEventOutcome(banner = "")
        }
    }

    // ------------------------------------------------------------
    // Item 169: team leader events
    // ------------------------------------------------------------

    fun teamLeaderCandidates(op: Operator, ctx: SoldierEventContext, ev: (SoldierEventKind, String?) -> PendingSoldierEvent): List<PendingSoldierEvent> {
        val squad = ctx.squadOf(op.id) ?: return emptyList()
        if (squad.teamLeaderId != op.id) return emptyList()
        return buildList {
            add(ev(SoldierEventKind.TL_TRAINING_LANE, squad.id))
            if (squad.leaderId != null && squad.leaderId != op.id) add(ev(SoldierEventKind.TL_FRICTION, squad.leaderId))
            add(ev(SoldierEventKind.TL_BOARD_REC, null))
            ctx.operators.firstOrNull { it.id in squad.memberIds && it.id != op.id && it.rank.tier == com.example.domain.model.RankTier.SOLDIER && SoldierEvents.trainingTarget(it) != null }
                ?.let { add(ev(SoldierEventKind.TL_SCHOOL_BUDDY, it.id)) }
        }
    }

    const val TL_LANE_COHESION = 15
    const val TL_BOARD_XP = 60

    fun teamLeaderCard(e: PendingSoldierEvent, who: String, ctx: SoldierEventContext): SoldierEventCard {
        val other = ctx.op(e.targetId)?.let { "${it.rank.code} ${it.callsign}" } ?: "the squad leader"
        return when (e.kind) {
            SoldierEventKind.TL_TRAINING_LANE -> SoldierEventCard(e.kind.title, "$who wants to run a training lane for their team.",
                listOf(c("approve", "Approve (15 SUP)", "+$TL_LANE_COHESION squad cohesion."), c("decline", "Not now", "Nothing changes.")))
            SoldierEventKind.TL_FRICTION -> SoldierEventCard(e.kind.title, "$who and $other don't see eye to eye on how to run the squad.",
                listOf(c("tl", "Back the team leader", "$who +5 morale, $other -5."), c("sl", "Back the squad leader", "$other +5 morale, $who -5."),
                    c("mediate", "Sit them down (10 CR)", "+10 squad cohesion.")))
            SoldierEventKind.TL_BOARD_REC -> SoldierEventCard(e.kind.title, "$who's squad leader recommends them for the next promotion board.",
                listOf(c("approve", "Endorse it", "+$TL_BOARD_XP XP."), c("decline", "Not yet", "Nothing changes.")))
            SoldierEventKind.TL_SCHOOL_BUDDY -> {
                val school = ctx.op(e.targetId)?.let { SoldierEvents.trainingTarget(it) }?.let { id -> com.example.data.catalog.GameCatalog.missions.find { it.id == id }?.displayTitle }
                SoldierEventCard(e.kind.title, "$who wants to take $other along to ${school ?: "a school"}.",
                    listOf(c("approve", "Approve", "$other: +3 morale and ${SoldierEvents.TRAINING_DISCOUNT_PCT}% off that enrollment."), c("decline", "Not now", "Nothing changes.")))
            }
            else -> SoldierEventCard(e.kind.title, "", listOf(c("decline", "Close", "")))
        }
    }

    fun teamLeaderApply(e: PendingSoldierEvent, op: Operator, who: String, choiceId: String, ctx: SoldierEventContext): SoldierEventOutcome {
        val other = ctx.op(e.targetId)
        val squad = ctx.squadOf(op.id)
        if (choiceId == "decline") return SoldierEventOutcome(banner = "")
        return when (e.kind) {
            SoldierEventKind.TL_TRAINING_LANE -> {
                if (ctx.resources.supplies < 15 || squad == null) return SoldierEventOutcome(banner = "Not enough supplies.")
                SoldierEventOutcome(squadUpdates = listOf(squad.copy(cohesionXp = (squad.cohesionXp + TL_LANE_COHESION).coerceAtMost(MAX_SQUAD_COHESION_XP))),
                    suppliesDelta = -15.0, banner = "$who ran a training lane (+$TL_LANE_COHESION cohesion).")
            }
            SoldierEventKind.TL_FRICTION -> when (choiceId) {
                "tl" -> SoldierEventOutcome(operatorUpdates = listOfNotNull(MoraleRules.adjust(op, 5), other?.let { MoraleRules.adjust(it, -5) }), banner = "You backed $who.")
                "sl" -> SoldierEventOutcome(operatorUpdates = listOfNotNull(MoraleRules.adjust(op, -5), other?.let { MoraleRules.adjust(it, 5) }), banner = "You backed the squad leader.")
                else -> {
                    if (ctx.resources.credits < 10 || squad == null) return SoldierEventOutcome(banner = "Not enough credits.")
                    SoldierEventOutcome(squadUpdates = listOf(squad.copy(cohesionXp = (squad.cohesionXp + 10).coerceAtMost(MAX_SQUAD_COHESION_XP))),
                        creditsDelta = -10.0, banner = "Talked it out (+10 cohesion).")
                }
            }
            SoldierEventKind.TL_BOARD_REC -> SoldierEventOutcome(operatorUpdates = listOf(op.copy(xp = op.xp + TL_BOARD_XP)), banner = "$who endorsed for the board (+$TL_BOARD_XP XP).")
            SoldierEventKind.TL_SCHOOL_BUDDY -> if (other == null) SoldierEventOutcome(banner = "")
            else SoldierEventOutcome(operatorUpdates = listOf(MoraleRules.adjust(other, 3).copy(eventBuffs = other.eventBuffs.copy(
                schoolDiscountUntilMillis = ctx.now + GameTiming.millis(SoldierEvents.TRAINING_DISCOUNT_MILLIS)))),
                banner = "${other.callsign} will go to school with $who's backing.")
            else -> SoldierEventOutcome(banner = "")
        }
    }
}
