package com.example.domain

import com.example.data.catalog.GameCatalog
import com.example.domain.model.MissionScope
import com.example.domain.model.MissionTemplate
import com.example.domain.model.Operator
import com.example.domain.model.OperatorRank
import com.example.domain.model.OperatorStatus
import com.example.domain.model.RankTier
import com.example.domain.model.TacticalSquad
import com.example.domain.model.isHqStaffer
import com.example.domain.model.MAX_SQUAD_SIZE
import kotlin.random.Random

/**
 * Session 42 (item 73): service contracts. Enlisted soldiers sign for 4 years, officers and warrant officers
 * for 6. At the end of a contract the soldier decides to re-up or leave, mostly on morale; the player can
 * sway someone who wants to leave twice in a career. On signing, a soldier may ask for something this
 * contract (a school, a squad, an HQ billet, a type of op, or a signing bonus). SSG+ with 10 years go
 * indefinite; from 20 years nobody signs again and retirement requests come once a year instead.
 */
object ContractRules {
    const val ENLISTED_TERM_YEARS = 4.0
    const val OFFICER_TERM_YEARS = 6.0
    const val INDEFINITE_YEARS = 10.0
    const val MAX_SWAYS = 2
    const val ASK_CHANCE = 0.60
    const val ASK_MET_MORALE = 10
    const val ASK_MISSED_MORALE = -10
    const val MISSION_ASK_COUNT = 2

    enum class AskType(val label: String) { SCHOOL("a school"), SQUAD("a squad"), BILLET("an HQ billet"), MISSION("a type of op"), MONEY("a signing bonus") }

    data class Ask(val type: AskType, val target: String? = null) {
        fun encode(): String = type.name + (target?.let { ":$it" } ?: "")

        companion object {
            fun decode(s: String?): Ask? {
                if (s.isNullOrBlank()) return null
                val parts = s.split(":", limit = 2)
                val t = AskType.entries.find { it.name == parts[0] } ?: return null
                return Ask(t, parts.getOrNull(1))
            }
        }
    }

    fun termYears(op: Operator): Double = if (op.rank.tier.isOfficer) OFFICER_TERM_YEARS else ENLISTED_TERM_YEARS

    /** SSG and above with 10+ years: no more contract decisions (enlisted only). */
    fun isIndefinite(op: Operator, today: Double = GameCalendar.today): Boolean =
        op.rank.tier.isEnlisted && op.rank.seniorityIndex >= OperatorRank.SSG.seniorityIndex &&
            GameCalendar.yearsServed(op, today) >= INDEFINITE_YEARS

    /** Past 20 years nobody signs again; retirement requests take over. */
    fun isRetirementPhase(op: Operator, today: Double = GameCalendar.today): Boolean =
        GameCalendar.yearsServed(op, today) >= GameCalendar.RETIREMENT_ELIGIBLE_YEARS

    fun hasContract(op: Operator, today: Double = GameCalendar.today): Boolean =
        !isIndefinite(op, today) && !isRetirementPhase(op, today)

    /** First contract on arrival: a full term, less what prior service already used of the current one. */
    fun firstContractEnd(op: Operator, today: Double): Double {
        val term = termYears(op) * GameCalendar.DAYS_PER_YEAR
        val used = op.priorServiceDays % term
        return today + (term - used).coerceAtLeast(GameCalendar.DAYS_PER_YEAR)
    }

    fun contractDue(op: Operator, today: Double = GameCalendar.today): Boolean =
        op.unlocked && !op.status.isOutOfActiveService && op.status != OperatorStatus.CAPTURED &&
            op.contractEndDay >= 0 && today >= op.contractEndDay && hasContract(op, today)

    /** Signing bonus by rank (placeholder; release scales it with the economy, item 64). */
    fun signingBonus(op: Operator): Int = when {
        op.rank.tier == RankTier.FLAG_OFFICER -> 500
        op.rank.tier.isOfficer -> 400
        op.rank.tier == RankTier.SNCO -> 500
        op.rank.tier == RankTier.NCO -> 250
        else -> 100
    }

    /** Cost to talk a leaving soldier into staying: the bonus, more the second time. */
    fun swayCost(op: Operator): Int = (signingBonus(op) * if (op.swaysUsed == 0) 1.5 else 2.5).toInt()

    fun swayChancePct(op: Operator): Int = if (op.swaysUsed == 0) 60 else 35

    fun canSway(op: Operator): Boolean = op.swaysUsed < MAX_SWAYS

    /** Chance (percent) this soldier wants to re-up. Morale leads; a met or missed ask swings it hard. */
    fun reUpChancePct(op: Operator, awardsHeld: Int): Int {
        var pct = 55.0
        pct += (op.morale - 60) * 0.6
        pct += awardsHeld.coerceAtMost(10)
        pct -= (op.timesWounded * 3).coerceAtMost(12)
        if (op.isHqStaffer) pct += 5
        val ask = Ask.decode(op.contractAsk)
        if (ask != null) pct += if (op.contractAskMet) 20 else -25
        return pct.toInt().coerceIn(10, 95)
    }

    // ------------------------------------------------------------
    // Asks
    // ------------------------------------------------------------

    /** What this soldier might ask for when signing, or null (they ask nothing). */
    fun rollAsk(op: Operator, squads: List<TacticalSquad>, operators: List<Operator>, random: Random = Random): Ask? {
        if (random.nextDouble() >= ASK_CHANCE) return null
        val options = buildList {
            SoldierEvents.trainingTarget(op)?.let { add(Ask(AskType.SCHOOL, it)) }
            val current = squads.firstOrNull { op.id in it.memberIds }
            squads.firstOrNull { sq ->
                sq.id != current?.id && sq.memberIds.size < MAX_SQUAD_SIZE &&
                    (!sq.isSpecialForces || TacticalCalculator.isSfQualified(op))
            }?.takeIf { !op.isHqStaffer && !op.isAviator }?.let { add(Ask(AskType.SQUAD, it.id)) }
            if (!op.isHqStaffer && op.rank.isCareerEligibleForStaffOrTransfer && op.rank.tier != RankTier.FLAG_OFFICER) add(Ask(AskType.BILLET))
            if (!op.isAviator && !op.isHqStaffer) add(Ask(AskType.MISSION, SoldierEvents.opRequestFor(op).name))
            add(Ask(AskType.MONEY))
        }
        return options[random.nextInt(options.size)]
    }

    /** Whether the current ask is satisfied now (money is settled at signing). */
    fun askSatisfied(op: Operator, squads: List<TacticalSquad>): Boolean {
        val ask = Ask.decode(op.contractAsk) ?: return false
        return when (ask.type) {
            AskType.SCHOOL -> ask.target != null && ask.target in op.completedMissionIds
            AskType.SQUAD -> squads.any { it.id == ask.target && op.id in it.memberIds }
            AskType.BILLET -> op.isHqStaffer
            AskType.MISSION -> op.contractAskProgress >= MISSION_ASK_COUNT
            AskType.MONEY -> op.contractAskMet
        }
    }

    /** An op that counts toward a "send me on X ops" ask. */
    fun countsForMissionAsk(op: Operator, mission: MissionTemplate): Boolean {
        val ask = Ask.decode(op.contractAsk) ?: return false
        if (ask.type != AskType.MISSION || op.contractAskMet) return false
        val t = OpRequestType.entries.find { it.name == ask.target } ?: return false
        return t.matches(mission)
    }

    fun askLabel(op: Operator, squads: List<TacticalSquad>): String? {
        val ask = Ask.decode(op.contractAsk) ?: return null
        return when (ask.type) {
            AskType.SCHOOL -> GameCatalog.missions.find { it.id == ask.target }?.displayTitle?.removePrefix("School: ") ?: "a school"
            AskType.SQUAD -> squads.find { it.id == ask.target }?.name?.let { "a spot on $it" } ?: "another squad"
            AskType.BILLET -> "an HQ billet"
            AskType.MISSION -> "$MISSION_ASK_COUNT ${OpRequestType.entries.find { it.name == ask.target }?.label ?: "field"} ops" +
                if (op.contractAskProgress in 1 until MISSION_ASK_COUNT) " (${op.contractAskProgress}/$MISSION_ASK_COUNT)" else ""
            AskType.MONEY -> "a ${signingBonus(op)} CR signing bonus"
        }
    }

    /** A new contract from today, with this ask (or none). */
    fun signed(op: Operator, today: Double, ask: Ask?, askMet: Boolean = false): Operator = op.copy(
        contractStartDay = today,
        contractEndDay = today + termYears(op) * GameCalendar.DAYS_PER_YEAR,
        contractAsk = ask?.encode(),
        contractAskMet = askMet,
        contractAskProgress = 0
    )

    // ------------------------------------------------------------
    // Retirement requests (20 to 30 years)
    // ------------------------------------------------------------

    /** Chance (percent) a soldier past 20 years asks to retire this year: rises each year. */
    fun retireAskChancePct(op: Operator, today: Double = GameCalendar.today): Int {
        val over = (GameCalendar.yearsServed(op, today) - GameCalendar.RETIREMENT_ELIGIBLE_YEARS).coerceAtLeast(0.0)
        return (30 + over * 10).toInt().coerceAtMost(95)
    }

    fun retireAskDue(op: Operator, today: Double = GameCalendar.today): Boolean =
        op.unlocked && !op.status.isOutOfActiveService && op.status != OperatorStatus.CAPTURED &&
            !op.retirementRequested && isRetirementPhase(op, today) &&
            op.rank.isCareerEligibleForStaffOrTransfer && (op.nextRetireAskDay < 0 || today >= op.nextRetireAskDay)

    @Suppress("unused")
    private fun isField(m: MissionTemplate) = m.scope == MissionScope.FIELD_COMBAT
}
