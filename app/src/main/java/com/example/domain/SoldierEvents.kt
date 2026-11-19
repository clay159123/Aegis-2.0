package com.example.domain

import com.example.data.catalog.GameCatalog
import com.example.domain.model.*
import kotlin.random.Random

/**
 * Item 69: random soldier events. At most one check every [EVENT_INTERVAL_MILLIS] (scaled by the time switch),
 * each check fires with [EVENT_FIRE_CHANCE], only one event waits at a time, and an unanswered event expires
 * after [EVENT_EXPIRY_MILLIS]. Only soldiers who are around (READY field soldiers and seated HQ staff, not
 * deployed) can raise one, and each event only goes to soldiers it fits.
 */
enum class SoldierEventKind(val title: String, val personal: Boolean = false) {
    TRAINING_REQUEST("Training Request", personal = true),
    SQUAD_TRANSFER("Squad Transfer Request", personal = true),
    OP_REQUEST("Op Request", personal = true),
    TRANSFER_PETITION("Transfer Request", personal = true),
    RETIREMENT_PETITION("Retirement Request", personal = true),
    BARRACKS_INSPECTION("Failed Barracks Inspection"),
    RANGE_TIME("Extra Range Time", personal = true),
    SOLDIER_OF_MONTH("Soldier of the Month Board"),
    MENTOR_PAIRING("Mentor Pairing"),
    WANTS_TEAM("Wants a Team", personal = true),
    SQUAD_RIVALRY("Squad Rivalry"),
    EQUIPMENT_IDEA("Equipment Improvement Idea"),
    RECRUITING_DUTY("Recruiting Duty", personal = true),
    STANDARDS_WALK("Standards Walk-Through"),
    DINING_IN("Dining-In Invitation"),
    LT_FIRST_PLAN("The Lieutenant's First Plan", personal = true),
    PT_CHALLENGE("Commander's PT Challenge"),
    STAFF_CONFERENCE("Staff Conference"),
    AIRCRAFT_WRITE_UP("Maintenance Write-Up"),
    STICK_TIME("Extra Stick Time", personal = true),
    SYSTEMS_UPGRADE("Systems Upgrade Proposal"),
    PRESS_INTERVIEW("Press Interview"),
    JOINT_SUMMIT("Joint Planning Summit"),
    DELEGATION_TOUR("Delegation Tour"),
    SQUAD_TRAINING_DAY("Squad Training Day"),
    MEDBAY_PUSH("Medbay Supply Push"),
    SF_REHEARSAL("Mission Rehearsal in Isolation"),
    INTEL_LEAD("Intel Lead"),
    FAMILY_NEWS("Family News", personal = true)
}

/** Kinds of op a soldier can ask for. */
enum class OpRequestType(val label: String) {
    RECON("recon"), ASSAULT("assault"), URBAN("urban"), NIGHT("night"), DIPLOMATIC("diplomatic");

    fun matches(m: MissionTemplate): Boolean = when (this) {
        RECON -> m.scope == MissionScope.FIELD_COMBAT && listOf("Recon", "Stealth", "Intel", "Infiltration").any { it in m.primaryFocus }
        ASSAULT -> m.scope == MissionScope.FIELD_COMBAT && listOf("Assault", "Breach", "Strike", "Raid").any { it in m.primaryFocus }
        URBAN -> m.scope == MissionScope.FIELD_COMBAT && m.theater.terrain == Terrain.URBAN
        NIGHT -> m.scope == MissionScope.FIELD_COMBAT && m.isNightOp
        DIPLOMATIC -> m.scope == MissionScope.STAFF_DIPLOMATIC
    }
}

/** One waiting event. [targetId] is the other soldier, the squad, the school or the op type, by kind. */
data class PendingSoldierEvent(
    val kind: SoldierEventKind,
    val operatorId: String,
    val targetId: String? = null,
    val createdAtMillis: Long,
    val expiresAtMillis: Long
) {
    fun toCsv(): String = listOf(kind.name, operatorId, targetId ?: "", createdAtMillis, expiresAtMillis).joinToString("|")

    companion object {
        fun fromCsv(csv: String): PendingSoldierEvent? {
            val p = csv.split("|")
            if (p.size < 5) return null
            val kind = SoldierEventKind.entries.find { it.name == p[0] } ?: return null
            return PendingSoldierEvent(kind, p[1], p[2].ifBlank { null }, p[3].toLongOrNull() ?: 0L, p[4].toLongOrNull() ?: 0L)
        }
    }
}

data class SoldierEventChoice(val id: String, val label: String, val result: String)

data class SoldierEventCard(val title: String, val body: String, val choices: List<SoldierEventChoice>)

/** Everything an event needs to look at. */
data class SoldierEventContext(
    val operators: List<Operator>,
    val squads: List<TacticalSquad>,
    val deployments: List<ActiveDeployment>,
    val facilities: List<FacilityState>,
    val resources: ResourceBundle,
    val now: Long
) {
    val deployedIds: Set<String> = deployments.flatMap { it.operatorIds }.toSet()
    fun op(id: String?): Operator? = operators.find { it.id == id }
    fun squadOf(opId: String): TacticalSquad? = squads.find { opId in it.memberIds }
}

/** Command-wide effects an event can leave behind. */
data class CommandEventEffects(
    val heloGroundedUntilMillis: Long = 0L,
    val recruitDiscountPct: Int = 0,
    val sfRehearsalBonusPct: Int = 0
)

/** What answering an event changes. The view model saves it. */
data class SoldierEventOutcome(
    val operatorUpdates: List<Operator> = emptyList(),
    val squadUpdates: List<TacticalSquad> = emptyList(),
    val creditsDelta: Double = 0.0,
    val suppliesDelta: Double = 0.0,
    val intelDelta: Double = 0.0,
    val prestigeDelta: Double = 0.0,
    val effects: CommandEventEffects? = null,
    val deeds: List<PendingDeed> = emptyList(),
    /** Petitions hand off to the existing approve or decline flow. */
    val petitionAction: PetitionAction? = null,
    val banner: String
)

enum class PetitionAction { APPROVE_TRANSFER, DECLINE_TRANSFER, APPROVE_RETIREMENT, DENY_RETIREMENT }

object SoldierEvents {
    const val EVENT_INTERVAL_MILLIS = 5 * 60_000L
    const val EVENT_FIRE_CHANCE = 0.40
    const val EVENT_EXPIRY_MILLIS = 15 * 60_000L
    /** Leave and other away time from an event. Release can vary this by event. */
    const val AWAY_MILLIS = 5 * 60_000L
    const val TRAINING_DISCOUNT_PCT = 25
    const val TRAINING_DISCOUNT_MILLIS = 30 * 60_000L
    const val OP_REQUEST_WINDOW_MILLIS = 20 * 60_000L
    const val OP_REQUEST_XP_BONUS_PCT = 50
    const val LT_PLAN_XP_BONUS_PCT = 50
    const val LT_PLAN_PASS_PENALTY = 2
    const val RANGE_TIME_BONUS_PCT = 10
    const val RECRUIT_DISCOUNT_PCT = 20
    const val SF_REHEARSAL_BONUS_PCT = 3
    /** Free choices that carry a small risk of a minor injury. */
    const val TRAINING_INJURY_CHANCE = 0.04
    const val HARD_LANDING_CHANCE_STICK = 0.03
    const val HARD_LANDING_CHANCE_FLY_ANYWAY = 0.10
    const val PRESS_GAFFE_CHANCE = 0.10
    const val SOLDIER_OF_MONTH_WIN_CHANCE = 0.60

    /** Set from game state so pure rules (insertions, odds, recruit costs) can see command effects. */
    @Volatile var effects: CommandEventEffects = CommandEventEffects()

    fun eventIntervalMillis(): Long = GameTiming.millis(EVENT_INTERVAL_MILLIS)
    fun expiryMillis(): Long = GameTiming.millis(EVENT_EXPIRY_MILLIS)
    fun awayMillis(): Long = GameTiming.millis(AWAY_MILLIS)

    // ---------- Who can raise events ----------

    /** Soldiers who are around: READY field soldiers and seated HQ staff, not deployed, at school, on TDY or on leave. */
    fun idleOperators(ctx: SoldierEventContext): List<Operator> = ctx.operators.filter {
        it.unlocked && it.id !in ctx.deployedIds &&
            (it.status == OperatorStatus.READY || it.status == OperatorStatus.HQ_STAFF)
    }

    private val Operator.isJunior get() = rank.tier == RankTier.SOLDIER
    private val Operator.isNco get() = rank.tier == RankTier.NCO
    private val Operator.isSnco get() = rank.tier == RankTier.SNCO
    private val Operator.isCompanyGrade get() = rank == OperatorRank.LT2 || rank == OperatorRank.LT1 || rank == OperatorRank.CPT
    private val Operator.isFieldGrade get() = rank == OperatorRank.MAJ || rank == OperatorRank.LTC || rank == OperatorRank.COL
    private val Operator.isGeneral get() = rank.tier == RankTier.FLAG_OFFICER
    private val Operator.isOfficerBelowGeneral get() = rank.tier == RankTier.OFFICER

    /** Every (event, soldier, target) that fits right now. Petitions come first when any are waiting. */
    fun candidates(ctx: SoldierEventContext): List<PendingSoldierEvent> {
        val idle = idleOperators(ctx)
        if (idle.isEmpty()) return emptyList()
        fun ev(kind: SoldierEventKind, op: Operator, target: String? = null) =
            PendingSoldierEvent(kind, op.id, target, ctx.now, ctx.now + expiryMillis())
        val petitions = idle.mapNotNull { op ->
            when {
                op.transferRequested -> ev(SoldierEventKind.TRANSFER_PETITION, op)
                op.retirementRequested -> ev(SoldierEventKind.RETIREMENT_PETITION, op)
                else -> null
            }
        }
        if (petitions.isNotEmpty()) return petitions
        val out = mutableListOf<PendingSoldierEvent>()
        val inMedbay = ctx.operators.any { it.unlocked && it.status == OperatorStatus.RECOVERING }
        for (op in idle) {
            val squad = ctx.squadOf(op.id)
            val field = op.status == OperatorStatus.READY
            val hq = op.status == OperatorStatus.HQ_STAFF
            // Spec four
            if (!op.isGeneral) trainingTarget(op)?.let { out += ev(SoldierEventKind.TRAINING_REQUEST, op, it) }
            if (field && squad != null) squadTransferTarget(op, squad, ctx)?.let { out += ev(SoldierEventKind.SQUAD_TRANSFER, op, it.id) }
            if (field && !op.isAviator && (op.rank.tier.isEnlisted || squad != null)) {
                out += ev(SoldierEventKind.OP_REQUEST, op, opRequestFor(op).name)
            }
            // Junior enlisted
            if (op.isJunior && field && !op.isAviator) out += ev(SoldierEventKind.BARRACKS_INSPECTION, op)
            if ((op.isJunior || op.isNco) && !op.isAviator && op.marksmanshipQual != MarksmanshipBadgeTier.EXPERT) {
                out += ev(SoldierEventKind.RANGE_TIME, op)
            }
            if (op.rank == OperatorRank.SPC || op.rank == OperatorRank.SGT) out += ev(SoldierEventKind.SOLDIER_OF_MONTH, op)
            // NCOs
            if (op.isNco && squad != null) {
                ctx.operators.firstOrNull { it.id in squad.memberIds && it.id != op.id && it.isJunior }
                    ?.let { out += ev(SoldierEventKind.MENTOR_PAIRING, op, it.id) }
                if (squad.teamLeaderId == null && squad.leaderId != op.id && SquadCohesionRules.teamLeaderBlocker(squad, op) == null) {
                    out += ev(SoldierEventKind.WANTS_TEAM, op, squad.id)
                }
            }
            if ((op.isJunior || op.isNco) && squad != null && squad.memberIds.size >= 3) {
                ctx.operators.firstOrNull { it.id in squad.memberIds && it.id != op.id && it.rank.tier.isEnlisted && it.id !in ctx.deployedIds }
                    ?.let { out += ev(SoldierEventKind.SQUAD_RIVALRY, op, it.id) }
            }
            if ((op.isNco || op.isSnco) && (op.specialty == OperatorSpecialty.BREACHER || op.specialty == OperatorSpecialty.COMMAND)) {
                out += ev(SoldierEventKind.EQUIPMENT_IDEA, op)
            }
            // Senior NCOs
            if (op.isSnco && field) out += ev(SoldierEventKind.RECRUITING_DUTY, op)
            if (op.rank == OperatorRank.SGM || op.rank == OperatorRank.CSM) {
                if (ctx.squads.isNotEmpty()) out += ev(SoldierEventKind.STANDARDS_WALK, op)
            }
            if (op.isSnco || op.isOfficerBelowGeneral) out += ev(SoldierEventKind.DINING_IN, op)
            // Officers
            if (op.rank == OperatorRank.LT2 || op.rank == OperatorRank.LT1) out += ev(SoldierEventKind.LT_FIRST_PLAN, op)
            if ((op.rank == OperatorRank.CPT || op.isFieldGrade) && ctx.squads.isNotEmpty()) out += ev(SoldierEventKind.PT_CHALLENGE, op)
            if (op.isFieldGrade && hq) out += ev(SoldierEventKind.STAFF_CONFERENCE, op)
            // Warrant officers and aviators
            if (op.isAviator && op.isWarrantOfficer) {
                out += ev(SoldierEventKind.AIRCRAFT_WRITE_UP, op)
                out += ev(SoldierEventKind.STICK_TIME, op)
            }
            if (op.isTechnicalWarrant) out += ev(SoldierEventKind.SYSTEMS_UPGRADE, op)
            // Generals
            if (op.isGeneral) {
                out += ev(SoldierEventKind.PRESS_INTERVIEW, op)
                out += ev(SoldierEventKind.JOINT_SUMMIT, op)
                out += ev(SoldierEventKind.DELEGATION_TOUR, op)
            }
            // Positions
            if (squad != null && squad.leaderId == op.id) out += ev(SoldierEventKind.SQUAD_TRAINING_DAY, op, squad.id)
            if (op.specialty == OperatorSpecialty.MEDIC && inMedbay) out += ev(SoldierEventKind.MEDBAY_PUSH, op)
            if (squad?.isSpecialForces == true) out += ev(SoldierEventKind.SF_REHEARSAL, op, squad.id)
            if (hq && op.hqRole == HqStaffRole.S2_INTEL_OVERWATCH) out += ev(SoldierEventKind.INTEL_LEAD, op)
            out += ev(SoldierEventKind.FAMILY_NEWS, op)
        }
        return out
    }

    /** Roll one event, or null. The caller handles the timer and the one-at-a-time rule. */
    fun roll(ctx: SoldierEventContext, random: Random = Random): PendingSoldierEvent? {
        if (random.nextDouble() >= EVENT_FIRE_CHANCE) return null
        return pick(ctx, random)
    }

    /** Pick a fitting event (petitions first), or null when nobody is around. */
    fun pick(ctx: SoldierEventContext, random: Random = Random): PendingSoldierEvent? {
        val all = candidates(ctx)
        if (all.isEmpty()) return null
        // Pick a soldier first so a soldier with many fitting events isn't favoured, then one of their events
        val byOp = all.groupBy { it.operatorId }
        val opId = byOp.keys.toList()[random.nextInt(byOp.size)]
        val mine = byOp.getValue(opId)
        return mine[random.nextInt(mine.size)]
    }

    /** True when the waiting event still makes sense (soldier around, target still valid). */
    fun stillValid(e: PendingSoldierEvent, ctx: SoldierEventContext): Boolean =
        ctx.now < e.expiresAtMillis && idleOperators(ctx).any { it.id == e.operatorId }

    // ---------- Targets ----------

    /** A school this soldier qualifies for and hasn't finished. */
    fun trainingTarget(op: Operator): String? = GameCatalog.missions
        .filter { it.schoolRequirements != null && it.id !in op.completedMissionIds && it.id != TacticalCalculator.RIFLE_QUAL_MISSION_ID }
        .firstOrNull { TacticalCalculator.canOperatorDeployOnMission(op, it, emptySet()).eligible }?.id

    /** Another squad with room that fits this soldier better (it lacks their MOS, or it's the SF squad and they're SF). */
    fun squadTransferTarget(op: Operator, current: TacticalSquad, ctx: SoldierEventContext): TacticalSquad? {
        if (current.leaderId == op.id || current.teamLeaderId == op.id) return null
        return ctx.squads.firstOrNull { sq ->
            sq.id != current.id && sq.memberIds.size < MAX_SQUAD_SIZE &&
                (!sq.isSpecialForces || TacticalCalculator.isSfQualified(op)) &&
                (sq.isSpecialForces || ctx.operators.none { it.id in sq.memberIds && it.specialty == op.specialty })
        }
    }

    fun opRequestFor(op: Operator): OpRequestType = when (op.specialty) {
        OperatorSpecialty.RECON -> OpRequestType.RECON
        OperatorSpecialty.BREACHER -> OpRequestType.URBAN
        OperatorSpecialty.SIGNALS -> OpRequestType.NIGHT
        OperatorSpecialty.COMMAND -> OpRequestType.DIPLOMATIC
        else -> if (!op.rank.tier.isEnlisted) OpRequestType.DIPLOMATIC else OpRequestType.ASSAULT
    }

    // ---------- Cards ----------

    fun card(e: PendingSoldierEvent, ctx: SoldierEventContext): SoldierEventCard {
        val op = ctx.op(e.operatorId)
        val who = op?.let { "${it.rank.code} ${it.callsign}" } ?: "A soldier"
        val target = ctx.op(e.targetId)
        val targetName = target?.let { "${it.rank.code} ${it.callsign}" } ?: "a teammate"
        val squad = ctx.squads.find { it.id == e.targetId }
        val school = GameCatalog.missions.find { it.id == e.targetId }
        fun c(id: String, label: String, result: String) = SoldierEventChoice(id, label, result)
        val decline = c("decline", "Decline", "Nothing changes.")
        return when (e.kind) {
            SoldierEventKind.TRAINING_REQUEST -> SoldierEventCard(e.kind.title,
                "$who asks to attend ${school?.displayTitle ?: "a school"}.",
                listOf(c("approve", "Approve", "$TRAINING_DISCOUNT_PCT% off their enrollment for ${mins(TRAINING_DISCOUNT_MILLIS)} minutes."), decline))
            SoldierEventKind.SQUAD_TRANSFER -> SoldierEventCard(e.kind.title,
                "$who asks to move to ${squad?.name ?: "another squad"}, where their skills are needed more.",
                listOf(c("approve", "Approve", "They move; both squads take the usual turnover cohesion loss."), decline))
            SoldierEventKind.OP_REQUEST -> {
                val t = OpRequestType.entries.find { it.name == e.targetId } ?: OpRequestType.ASSAULT
                SoldierEventCard(e.kind.title, "$who asks: \"Send me on a ${t.label} op.\"",
                    listOf(c("approve", "I'll see what I can do", "If they deploy on a ${t.label} op within ${mins(OP_REQUEST_WINDOW_MILLIS)} minutes, they earn +$OP_REQUEST_XP_BONUS_PCT% XP."),
                        c("decline", "Not now", "Nothing changes.")))
            }
            SoldierEventKind.TRANSFER_PETITION -> SoldierEventCard(e.kind.title,
                "$who requests a transfer to another command.",
                listOf(c("approve", "Approve Transfer", "They leave the command; +10 PRS."), c("decline", "Decline", "They stay.")))
            SoldierEventKind.RETIREMENT_PETITION -> SoldierEventCard(e.kind.title,
                "$who requests retirement after a full career.",
                listOf(c("approve", "Approve Retirement", "They retire with honors and add to the veteran legacy."), c("decline", "Deny", "They stay on duty.")))
            SoldierEventKind.BARRACKS_INSPECTION -> SoldierEventCard(e.kind.title,
                "$who failed the barracks inspection: unmade bunk and a dirty weapon.",
                listOf(c("approve", "Corrective training", "+1 RS."), c("decline", "Let it slide", "Nothing changes.")))
            SoldierEventKind.RANGE_TIME -> SoldierEventCard(e.kind.title,
                "$who wants extra time on the range.",
                listOf(c("approve", "Approve (15 CR)", "+$RANGE_TIME_BONUS_PCT% on their next Rifle Qual."), decline))
            SoldierEventKind.SOLDIER_OF_MONTH -> SoldierEventCard(e.kind.title,
                "$who could go before the Soldier of the Month board.",
                listOf(c("approve", "Send them (10 CR)", "${(SOLDIER_OF_MONTH_WIN_CHANCE * 100).toInt()}% to win: +60 XP, +5 PRS and an Army Achievement Medal recommendation."), decline))
            SoldierEventKind.MENTOR_PAIRING -> SoldierEventCard(e.kind.title,
                "$who offers to mentor $targetName.",
                listOf(c("approve", "Pair them", "Both +40 XP; their squad +30 cohesion."), decline))
            SoldierEventKind.WANTS_TEAM -> SoldierEventCard(e.kind.title,
                "$who asks to lead a fire team in ${squad?.name ?: "their squad"}.",
                listOf(c("approve", "Make them team leader", "They become the squad's team leader."), decline))
            SoldierEventKind.SQUAD_RIVALRY -> SoldierEventCard(e.kind.title,
                "$who and $targetName are at each other's throats.",
                listOf(c("mediate", "Mediate (10 CR)", "Squad +20 cohesion."),
                    c("pt", "Hash it out at PT", "Both +1 FP; squad -10 cohesion."),
                    c("decline", "Ignore it", "Squad -30 cohesion.")))
            SoldierEventKind.EQUIPMENT_IDEA -> SoldierEventCard(e.kind.title,
                "$who has an idea to stretch the supply budget.",
                listOf(c("approve", "Fund it (40 CR)", "+60 SUP."), c("decline", "Pass", "Nothing changes.")))
            SoldierEventKind.RECRUITING_DUTY -> SoldierEventCard(e.kind.title,
                "$who volunteers for a stint on recruiting duty.",
                listOf(c("approve", "Approve", "Away ${mins(AWAY_MILLIS)} minutes; your next recruit costs $RECRUIT_DISCOUNT_PCT% less."), decline))
            SoldierEventKind.STANDARDS_WALK -> SoldierEventCard(e.kind.title,
                "$who wants to walk the line and check standards across the squads.",
                listOf(c("approve", "Approve", "Every squad +15 cohesion."), decline))
            SoldierEventKind.DINING_IN -> SoldierEventCard(e.kind.title,
                "$who is invited to a formal dining-in.",
                listOf(c("approve", "Attend (30 CR)", "+6 PRS and +40 XP for them."), decline))
            SoldierEventKind.LT_FIRST_PLAN -> SoldierEventCard(e.kind.title,
                "$who wants to plan and brief their own mission.",
                listOf(c("approve", "Let them run it", "Their next mission: +$LT_PLAN_XP_BONUS_PCT% XP for them, -$LT_PLAN_PASS_PENALTY% pass."), c("decline", "Not yet", "Nothing changes.")))
            SoldierEventKind.PT_CHALLENGE -> SoldierEventCard(e.kind.title,
                "$who challenges the squads to a PT competition.",
                listOf(c("approve", "Approve", "Every squad +25 cohesion; ${pct(TRAINING_INJURY_CHANCE)}% chance one soldier pulls something (short Medbay stay)."), decline))
            SoldierEventKind.STAFF_CONFERENCE -> SoldierEventCard(e.kind.title,
                "$who proposes hosting a regional staff conference.",
                listOf(c("approve", "Host it (40 CR)", "+30 INT."), decline))
            SoldierEventKind.AIRCRAFT_WRITE_UP -> SoldierEventCard(e.kind.title,
                "$who wrote up a fault on the aircraft.",
                listOf(c("ground", "Ground it", "No helicopter insertions for ${mins(AWAY_MILLIS)} minutes."),
                    c("fly", "Fly it anyway", "${pct(HARD_LANDING_CHANCE_FLY_ANYWAY)}% chance of a hard landing: the pilot goes to Medbay.")))
            SoldierEventKind.STICK_TIME -> SoldierEventCard(e.kind.title,
                "$who asks for extra stick time.",
                listOf(c("approve", "Approve", "+2 flight hours logged; ${pct(HARD_LANDING_CHANCE_STICK)}% chance of a hard landing (Medbay)."), decline))
            SoldierEventKind.SYSTEMS_UPGRADE -> SoldierEventCard(e.kind.title,
                "$who proposes an upgrade to the command's systems.",
                listOf(c("approve", "Fund it (35 CR)", "+25 INT."), c("decline", "Pass", "Nothing changes.")))
            SoldierEventKind.PRESS_INTERVIEW -> SoldierEventCard(e.kind.title,
                "A reporter wants an interview with $who.",
                listOf(c("approve", "Grant it", "+8 PRS; ${pct(PRESS_GAFFE_CHANCE)}% chance of a gaffe: -4 PRS instead."), decline))
            SoldierEventKind.JOINT_SUMMIT -> SoldierEventCard(e.kind.title,
                "$who is invited to a joint planning summit.",
                listOf(c("approve", "Send them", "Away ${mins(AWAY_MILLIS)} minutes; +30 INT and +5 PRS."), decline))
            SoldierEventKind.DELEGATION_TOUR -> SoldierEventCard(e.kind.title,
                "A delegation wants $who to show them the base.",
                listOf(c("approve", "Host it (50 CR)", "+12 PRS."), decline))
            SoldierEventKind.SQUAD_TRAINING_DAY -> SoldierEventCard(e.kind.title,
                "$who asks for a squad training day for ${squad?.name ?: "their squad"}.",
                listOf(c("approve", "Approve", "Squad +40 cohesion; ${pct(TRAINING_INJURY_CHANCE)}% chance one member pulls something (short Medbay stay)."), decline))
            SoldierEventKind.MEDBAY_PUSH -> SoldierEventCard(e.kind.title,
                "$who wants to push extra supplies to the Medbay.",
                listOf(c("approve", "Approve (20 SUP)", "Everyone in Medbay: 25% off their remaining recovery."), decline))
            SoldierEventKind.SF_REHEARSAL -> SoldierEventCard(e.kind.title,
                "$who asks for a full mission rehearsal in isolation.",
                listOf(c("approve", "Approve (15 SUP)", "+$SF_REHEARSAL_BONUS_PCT% on the next SF mission."), decline))
            SoldierEventKind.INTEL_LEAD -> SoldierEventCard(e.kind.title,
                "$who has a lead worth chasing.",
                listOf(c("approve", "Chase it (30 CR)", "+40 INT."), c("decline", "Pass", "Nothing changes.")))
            SoldierEventKind.FAMILY_NEWS -> SoldierEventCard(e.kind.title,
                "$who just got word: a baby on the way.",
                listOf(c("approve", "Grant leave", "Away ${mins(AWAY_MILLIS)} minutes; +30 XP."), c("decline", "Not now", "Nothing changes.")))
        }
    }

    private fun mins(base: Long): Long = GameTiming.millis(base) / 60_000L
    private fun pct(p: Double): Int = (p * 100).toInt()

    /** Credits or supplies a choice costs up front (so the UI can disable it). */
    fun choiceCost(kind: SoldierEventKind, choiceId: String): Pair<Double, Double> = when {
        choiceId == "decline" -> 0.0 to 0.0
        kind == SoldierEventKind.RANGE_TIME -> 15.0 to 0.0
        kind == SoldierEventKind.SOLDIER_OF_MONTH -> 10.0 to 0.0
        kind == SoldierEventKind.SQUAD_RIVALRY && choiceId == "mediate" -> 10.0 to 0.0
        kind == SoldierEventKind.EQUIPMENT_IDEA -> 40.0 to 0.0
        kind == SoldierEventKind.DINING_IN -> 30.0 to 0.0
        kind == SoldierEventKind.STAFF_CONFERENCE -> 40.0 to 0.0
        kind == SoldierEventKind.SYSTEMS_UPGRADE -> 35.0 to 0.0
        kind == SoldierEventKind.DELEGATION_TOUR -> 50.0 to 0.0
        kind == SoldierEventKind.INTEL_LEAD -> 30.0 to 0.0
        kind == SoldierEventKind.MEDBAY_PUSH -> 0.0 to 20.0
        kind == SoldierEventKind.SF_REHEARSAL -> 0.0 to 15.0
        else -> 0.0 to 0.0
    }

    // ---------- Applying a choice ----------

    fun apply(e: PendingSoldierEvent, choiceId: String, ctx: SoldierEventContext, random: Random = Random): SoldierEventOutcome {
        val op = ctx.op(e.operatorId) ?: return SoldierEventOutcome(banner = "That soldier is no longer available.")
        val who = "${op.rank.code} ${op.callsign}"
        val (cr, sup) = choiceCost(e.kind, choiceId)
        if (ctx.resources.credits < cr || ctx.resources.supplies < sup) {
            return SoldierEventOutcome(banner = "Not enough resources for that choice.")
        }
        val declined = choiceId == "decline"
        fun squadPlus(sq: TacticalSquad, delta: Int) = sq.copy(cohesionXp = (sq.cohesionXp + delta).coerceIn(0, MAX_SQUAD_COHESION_XP))
        fun injured(o: Operator) = o.copy(
            status = OperatorStatus.RECOVERING,
            recoveryUntilMillis = ctx.now + GameTiming.millis(TacticalCalculator.BASE_RECOVERY_MILLIS / 2)
        )
        fun away(o: Operator) = o.copy(status = OperatorStatus.ON_LEAVE, recoveryUntilMillis = ctx.now + awayMillis())
        fun base(banner: String) = SoldierEventOutcome(creditsDelta = -cr, suppliesDelta = -sup, banner = banner)

        return when (e.kind) {
            SoldierEventKind.TRANSFER_PETITION -> SoldierEventOutcome(
                petitionAction = if (declined) PetitionAction.DECLINE_TRANSFER else PetitionAction.APPROVE_TRANSFER, banner = "")
            SoldierEventKind.RETIREMENT_PETITION -> SoldierEventOutcome(
                petitionAction = if (declined) PetitionAction.DENY_RETIREMENT else PetitionAction.APPROVE_RETIREMENT, banner = "")
            else -> if (declined) SoldierEventOutcome(banner = "$who: request declined.") else when (e.kind) {
                SoldierEventKind.TRAINING_REQUEST -> base("$who: $TRAINING_DISCOUNT_PCT% off their next enrollment.").copy(
                    operatorUpdates = listOf(op.copy(eventBuffs = op.eventBuffs.copy(
                        schoolDiscountUntilMillis = ctx.now + GameTiming.millis(TRAINING_DISCOUNT_MILLIS)))))
                SoldierEventKind.SQUAD_TRANSFER -> {
                    val from = ctx.squadOf(op.id)
                    val to = ctx.squads.find { it.id == e.targetId }
                    if (from == null || to == null || to.memberIds.size >= MAX_SQUAD_SIZE) {
                        SoldierEventOutcome(banner = "That squad no longer has room.")
                    } else {
                        val fromMembers = from.memberIds - op.id
                        base("$who moved to ${to.name}.").copy(squadUpdates = listOf(
                            SquadCohesionRules.applyRosterChange(from, fromMembers, from.leaderId?.takeIf { it in fromMembers }),
                            SquadCohesionRules.applyRosterChange(to, to.memberIds + op.id, to.leaderId)
                        ))
                    }
                }
                SoldierEventKind.OP_REQUEST -> base("$who is ready for that op.").copy(
                    operatorUpdates = listOf(op.copy(eventBuffs = op.eventBuffs.copy(
                        opRequestType = OpRequestType.entries.find { it.name == e.targetId },
                        opRequestUntilMillis = ctx.now + GameTiming.millis(OP_REQUEST_WINDOW_MILLIS)))))
                SoldierEventKind.BARRACKS_INSPECTION -> base("$who: corrective training done. +1 RS.").copy(
                    operatorUpdates = listOf(op.copy(baseResilience = op.baseResilience + 1)))
                SoldierEventKind.RANGE_TIME -> base("$who: +$RANGE_TIME_BONUS_PCT% on their next Rifle Qual.").copy(
                    operatorUpdates = listOf(op.copy(eventBuffs = op.eventBuffs.copy(rifleQualBonusPct = RANGE_TIME_BONUS_PCT))))
                SoldierEventKind.SOLDIER_OF_MONTH -> if (random.nextDouble() < SOLDIER_OF_MONTH_WIN_CHANCE) {
                    base("$who won Soldier of the Month!").copy(
                        operatorUpdates = listOf(op.copy(xp = op.xp + 60)),
                        prestigeDelta = 5.0,
                        deeds = listOf(PendingDeed(
                            id = "deed_evt_${ctx.now}", operatorId = op.id, missionTitle = "Soldier of the Month",
                            deedSummary = "Won the Soldier of the Month board.", recommendedMedalId = "medal_army_achievement",
                            createdAtMillis = ctx.now)))
                } else base("$who came up short at the board. Next time.")
                SoldierEventKind.MENTOR_PAIRING -> {
                    val mentee = ctx.op(e.targetId)
                    val sq = ctx.squadOf(op.id)
                    base("$who is mentoring ${mentee?.callsign ?: "a junior soldier"}.").copy(
                        operatorUpdates = listOfNotNull(op.copy(xp = op.xp + 40), mentee?.copy(xp = mentee.xp + 40)),
                        squadUpdates = listOfNotNull(sq?.let { squadPlus(it, 30) }))
                }
                SoldierEventKind.WANTS_TEAM -> {
                    val sq = ctx.squads.find { it.id == e.targetId }
                    if (sq == null || SquadCohesionRules.teamLeaderBlocker(sq, op) != null) SoldierEventOutcome(banner = "That team leader slot is no longer open.")
                    else base("$who now leads a fire team in ${sq.name}.").copy(squadUpdates = listOf(sq.copy(teamLeaderId = op.id)))
                }
                SoldierEventKind.SQUAD_RIVALRY -> {
                    val sq = ctx.squadOf(op.id)
                    val other = ctx.op(e.targetId)
                    if (choiceId == "pt") base("$who and ${other?.callsign} settled it at PT.").copy(
                        operatorUpdates = listOfNotNull(op.copy(baseFirepower = op.baseFirepower + 1), other?.copy(baseFirepower = other.baseFirepower + 1)),
                        squadUpdates = listOfNotNull(sq?.let { squadPlus(it, -10) }))
                    else base("Mediated: the squad is pulling together.").copy(squadUpdates = listOfNotNull(sq?.let { squadPlus(it, 20) }))
                }
                SoldierEventKind.EQUIPMENT_IDEA -> base("$who's idea paid off: +60 SUP.").copy(suppliesDelta = 60.0)
                SoldierEventKind.RECRUITING_DUTY -> base("$who is on recruiting duty. Next recruit -$RECRUIT_DISCOUNT_PCT%.").copy(
                    operatorUpdates = listOf(away(op)), effects = effects.copy(recruitDiscountPct = RECRUIT_DISCOUNT_PCT))
                SoldierEventKind.STANDARDS_WALK -> base("$who walked the line. Every squad +15 cohesion.").copy(
                    squadUpdates = ctx.squads.map { squadPlus(it, 15) })
                SoldierEventKind.DINING_IN -> base("$who attended the dining-in.").copy(
                    operatorUpdates = listOf(op.copy(xp = op.xp + 40)), prestigeDelta = 6.0)
                SoldierEventKind.LT_FIRST_PLAN -> base("$who will plan their next mission.").copy(
                    operatorUpdates = listOf(op.copy(eventBuffs = op.eventBuffs.copy(leadsNextMission = true))))
                SoldierEventKind.PT_CHALLENGE -> trainingDay(ctx.squads, 25, ctx, random, "$who's PT challenge is done.")
                    .let { it.copy(creditsDelta = -cr, suppliesDelta = -sup) }
                SoldierEventKind.STAFF_CONFERENCE -> base("$who hosted the staff conference: +30 INT.").copy(intelDelta = 30.0)
                SoldierEventKind.AIRCRAFT_WRITE_UP -> if (choiceId == "ground") {
                    base("Aircraft grounded for maintenance.").copy(
                        effects = effects.copy(heloGroundedUntilMillis = ctx.now + awayMillis()))
                } else if (random.nextDouble() < HARD_LANDING_CHANCE_FLY_ANYWAY) {
                    base("Hard landing! $who is in Medbay.").copy(operatorUpdates = listOf(injured(op)))
                } else base("$who flew it anyway. The fault held.")
                SoldierEventKind.STICK_TIME -> {
                    val flown = TacticalCalculator.applyFlightHours(op, 2, passed = true)
                    if (random.nextDouble() < HARD_LANDING_CHANCE_STICK) {
                        base("Hard landing on the practice flight. $who is in Medbay.").copy(operatorUpdates = listOf(injured(flown)))
                    } else base("$who logged extra stick time.").copy(operatorUpdates = listOf(flown))
                }
                SoldierEventKind.SYSTEMS_UPGRADE -> base("$who's upgrade is live: +25 INT.").copy(intelDelta = 25.0)
                SoldierEventKind.PRESS_INTERVIEW -> if (random.nextDouble() < PRESS_GAFFE_CHANCE) {
                    base("$who misspoke on camera. -4 PRS.").copy(prestigeDelta = -4.0)
                } else base("$who's interview went well. +8 PRS.").copy(prestigeDelta = 8.0)
                SoldierEventKind.JOINT_SUMMIT -> base("$who is at the joint summit.").copy(
                    operatorUpdates = listOf(away(op)), intelDelta = 30.0, prestigeDelta = 5.0)
                SoldierEventKind.DELEGATION_TOUR -> base("$who hosted the delegation. +12 PRS.").copy(prestigeDelta = 12.0)
                SoldierEventKind.SQUAD_TRAINING_DAY -> trainingDay(listOfNotNull(ctx.squads.find { it.id == e.targetId }), 40, ctx, random, "$who ran a squad training day.")
                SoldierEventKind.MEDBAY_PUSH -> base("$who pushed supplies to the Medbay.").copy(
                    operatorUpdates = ctx.operators.filter { it.status == OperatorStatus.RECOVERING }.map {
                        it.copy(recoveryUntilMillis = ctx.now + ((it.recoveryUntilMillis - ctx.now).coerceAtLeast(0L) * 3 / 4))
                    })
                SoldierEventKind.SF_REHEARSAL -> base("Rehearsal complete: +$SF_REHEARSAL_BONUS_PCT% on the next SF mission.").copy(
                    effects = effects.copy(sfRehearsalBonusPct = SF_REHEARSAL_BONUS_PCT))
                SoldierEventKind.INTEL_LEAD -> base("$who ran down the lead: +40 INT.").copy(intelDelta = 40.0)
                SoldierEventKind.FAMILY_NEWS -> base("$who is on leave. Congratulations!").copy(
                    operatorUpdates = listOf(away(op).copy(xp = op.xp + 30)))
                SoldierEventKind.TRANSFER_PETITION, SoldierEventKind.RETIREMENT_PETITION -> SoldierEventOutcome(banner = "")
            }
        }
    }

    /** Cohesion for [squads], with a small chance one idle member pulls something. */
    private fun trainingDay(squads: List<TacticalSquad>, cohesion: Int, ctx: SoldierEventContext, random: Random, banner: String): SoldierEventOutcome {
        val updated = squads.map { it.copy(cohesionXp = (it.cohesionXp + cohesion).coerceIn(0, MAX_SQUAD_COHESION_XP)) }
        val pool = ctx.operators.filter { o -> squads.any { o.id in it.memberIds } && o.status == OperatorStatus.READY && o.id !in ctx.deployedIds }
        return if (pool.isNotEmpty() && random.nextDouble() < TRAINING_INJURY_CHANCE) {
            val hurt = pool[random.nextInt(pool.size)]
            SoldierEventOutcome(
                squadUpdates = updated,
                operatorUpdates = listOf(hurt.copy(status = OperatorStatus.RECOVERING,
                    recoveryUntilMillis = ctx.now + GameTiming.millis(TacticalCalculator.BASE_RECOVERY_MILLIS / 2))),
                banner = "$banner ${hurt.callsign} pulled something and is in Medbay."
            )
        } else SoldierEventOutcome(squadUpdates = updated, banner = banner)
    }

    // ---------- Buff helpers used elsewhere ----------

    /** Share of the class that holds an active training discount for [mission]. */
    fun schoolDiscountFactor(mission: MissionTemplate, ops: List<Operator>, now: Long): Double {
        if (mission.schoolRequirements == null || ops.isEmpty()) return 1.0
        val discounted = ops.count { it.eventBuffs.schoolDiscountUntilMillis > now }
        return 1.0 - TRAINING_DISCOUNT_PCT / 100.0 * discounted / ops.size
    }

    fun opRequestMet(op: Operator, mission: MissionTemplate, launchedAtMillis: Long): Boolean {
        val t = op.eventBuffs.opRequestType ?: return false
        return launchedAtMillis <= op.eventBuffs.opRequestUntilMillis && t.matches(mission)
    }

    fun discountedRecruitCost(base: Double): Double = base * (100 - effects.recruitDiscountPct) / 100.0
}
