package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.ContractRules
import com.example.domain.GameCalendar
import com.example.domain.PetitionAction
import com.example.domain.PendingSoldierEvent
import com.example.domain.SoldierEventContext
import com.example.domain.SoldierEventKind
import com.example.domain.SoldierEvents
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class Session42Test {
    private val Y = GameCalendar.DAYS_PER_YEAR
    private val base = GameCatalog.createPostBasicRecruit("op_s42", OperatorSpecialty.ASSAULT, emptySet(), 0)
        .copy(unlocked = true, completedMissionIds = setOf("msn_basic_qual"), joinedDay = 0.0, contractStartDay = 0.0, contractEndDay = 4 * Y)
    private fun ranked(r: OperatorRank, years: Double) = base.copy(
        xp = r.xpRequired, authorizedTier = r.tier, pinnedRank = r.takeIf { it.tier != RankTier.SOLDIER && !it.isWarrant },
        priorServiceDays = years * Y
    )
    private fun ctx(ops: List<Operator>, credits: Double = 5_000.0) =
        SoldierEventContext(ops, emptyList(), emptyList(), FacilityType.entries.map { FacilityState(it, 2) }, ResourceBundle(credits = credits), 0L)
    private fun ev(op: Operator) = PendingSoldierEvent(SoldierEventKind.CONTRACT_END, op.id, null, 0L, 1L)

    @After fun reset() { GameCalendar.today = 0.0 }

    @Test
    fun terms_and_status() {
        assertEquals(4.0, ContractRules.termYears(base), 0.0)
        assertEquals(6.0, ContractRules.termYears(ranked(OperatorRank.CPT, 5.0)), 0.0)
        GameCalendar.today = 0.0
        assertTrue(ContractRules.isIndefinite(ranked(OperatorRank.SSG, 10.0)))
        assertFalse(ContractRules.isIndefinite(ranked(OperatorRank.SPC, 12.0)))
        assertFalse(ContractRules.isIndefinite(ranked(OperatorRank.CPT, 12.0)))
        assertTrue(ContractRules.isRetirementPhase(ranked(OperatorRank.CPT, 20.0)))
        // A 7-year SSG has a year left on the current 4-year term
        assertEquals(1.0 * Y, ContractRules.firstContractEnd(ranked(OperatorRank.SSG, 7.0), 0.0), 0.01)
    }

    @Test
    fun contract_comes_due_only_with_a_live_contract() {
        GameCalendar.today = 4.1 * Y
        assertTrue(ContractRules.contractDue(base))
        GameCalendar.today = 3.9 * Y
        assertFalse(ContractRules.contractDue(base))
        GameCalendar.today = 4.1 * Y
        assertFalse(ContractRules.contractDue(ranked(OperatorRank.SSG, 10.0).copy(contractEndDay = 1.0)))
        // Contract decisions come before every other event
        val due = base.copy(transferRequested = true)
        assertEquals(SoldierEventKind.CONTRACT_END, SoldierEvents.candidates(ctx(listOf(due))).first().kind)
    }

    @Test
    fun reup_odds_follow_morale_and_asks() {
        val happy = ContractRules.reUpChancePct(base.copy(morale = 95), 0)
        val sad = ContractRules.reUpChancePct(base.copy(morale = 20), 0)
        assertTrue(happy > sad)
        val met = ContractRules.reUpChancePct(base.copy(contractAsk = "BILLET", contractAskMet = true), 0)
        val missed = ContractRules.reUpChancePct(base.copy(contractAsk = "BILLET", contractAskMet = false), 0)
        assertTrue(met - missed >= 40)
    }

    @Test
    fun asks_are_met() {
        assertTrue(ContractRules.askSatisfied(base.copy(contractAsk = "SCHOOL:msn_ranger_school", completedMissionIds = setOf("msn_ranger_school")), emptyList()))
        assertFalse(ContractRules.askSatisfied(base.copy(contractAsk = "SCHOOL:msn_ranger_school"), emptyList()))
        assertTrue(ContractRules.askSatisfied(base.copy(contractAsk = "BILLET", status = OperatorStatus.HQ_STAFF, hqRole = HqStaffRole.S1_PERSONNEL_HONORS), emptyList()))
        assertTrue(ContractRules.askSatisfied(base.copy(contractAsk = "MISSION:URBAN", contractAskProgress = 2), emptyList()))
        val sq = TacticalSquad("sq1", "Bravo", listOf(base.id), base.id)
        assertTrue(ContractRules.askSatisfied(base.copy(contractAsk = "SQUAD:sq1"), listOf(sq)))
        val urban = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT && it.theater.terrain == Terrain.URBAN }
        assertTrue(ContractRules.countsForMissionAsk(base.copy(contractAsk = "MISSION:URBAN"), urban))
        assertEquals(ContractRules.Ask(ContractRules.AskType.SCHOOL, "x"), ContractRules.Ask.decode("SCHOOL:x"))
    }

    @Test
    fun decision_is_fixed_for_a_contract_end() {
        val c = ctx(listOf(base))
        assertEquals(SoldierEvents.contractDecision(base, c), SoldierEvents.contractDecision(base, c))
    }

    @Test
    fun signing_releasing_and_swaying() {
        GameCalendar.today = 4.1 * Y
        // Find soldiers who want to stay and who want to leave
        val variants = (0 until 60).map { base.copy(id = "op_v$it", morale = 60) }
        val c = ctx(variants)
        val stayer = variants.first { SoldierEvents.contractDecision(it, c).wantsToStay && SoldierEvents.contractDecision(it, c).ask?.type != ContractRules.AskType.MONEY }
        val leaver = variants.first { !SoldierEvents.contractDecision(it, c).wantsToStay }

        val signed = SoldierEvents.apply(ev(stayer), "sign", c).operatorUpdates.single()
        assertEquals(4.1 * Y + 4 * Y, signed.contractEndDay, 0.01)
        assertEquals(PetitionAction.CONTRACT_LEAVE, SoldierEvents.apply(ev(stayer), "release", c).petitionAction)

        // Sway: paid only when they accept; counts against the two-per-career limit either way
        val accept = SoldierEvents.apply(ev(leaver), "sway", c, object : Random() { override fun nextBits(bitCount: Int) = 0 })
        assertEquals(1, accept.operatorUpdates.single().swaysUsed)
        assertTrue(accept.creditsDelta < 0)
        assertNull(accept.petitionAction)
        val maxed = leaver.copy(swaysUsed = ContractRules.MAX_SWAYS)
        assertFalse(SoldierEvents.card(ev(maxed), ctx(listOf(maxed))).choices.any { it.id == "sway" })
    }

    @Test
    fun money_ask_paid_at_signing() {
        GameCalendar.today = 4.1 * Y
        val variants = (0 until 200).map { base.copy(id = "op_m$it", morale = 90) }
        val c = ctx(variants)
        val wantsMoney = variants.firstOrNull { SoldierEvents.contractDecision(it, c).ask?.type == ContractRules.AskType.MONEY } ?: return
        val paid = SoldierEvents.apply(ev(wantsMoney), "pay", c)
        assertEquals(-ContractRules.signingBonus(wantsMoney).toDouble(), paid.creditsDelta, 0.0)
        assertTrue(paid.operatorUpdates.single().contractAskMet)
        val unpaid = SoldierEvents.apply(ev(wantsMoney), "sign", c).operatorUpdates.single()
        assertFalse(unpaid.contractAskMet)
        assertTrue(unpaid.morale < wantsMoney.morale)
    }

    @Test
    fun retirement_asks_rise_each_year() {
        val vet = ranked(OperatorRank.MSG, 20.0)
        GameCalendar.today = 0.0
        assertTrue(ContractRules.retireAskDue(vet))
        assertEquals(30, ContractRules.retireAskChancePct(vet))
        GameCalendar.today = 5 * Y
        assertEquals(80, ContractRules.retireAskChancePct(vet))
        assertFalse(ContractRules.retireAskDue(vet.copy(nextRetireAskDay = 6 * Y)))
    }

    @Test
    fun rush_price() {
        assertEquals(5, TacticalCalculator.rushPrestigeCostFor(10_000L))
        assertEquals(30, TacticalCalculator.rushPrestigeCostFor(30 * 60_000L))
    }

    @Test
    fun ask_roll_can_be_nothing() {
        val asks = (0 until 50).map { ContractRules.rollAsk(base, emptyList(), listOf(base), Random(it)) }
        assertTrue(asks.any { it == null })
        assertTrue(asks.any { it != null })
        assertNotNull(asks.firstOrNull { it?.type == ContractRules.AskType.MONEY })
    }
}
