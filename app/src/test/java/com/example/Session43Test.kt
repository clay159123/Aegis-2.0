package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.GameCalendar
import com.example.domain.PendingSoldierEvent
import com.example.domain.PowRules
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

class Session43Test {
    private val base = GameCatalog.createPostBasicRecruit("op_s43", OperatorSpecialty.ASSAULT, emptySet(), 0)
        .copy(unlocked = true, completedMissionIds = setOf("msn_basic_qual"))
    private val field = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT && it.risk == MissionRisk.HIGH }
    private val rescue get() = GameCatalog.missions.first { it.id == PowRules.RESCUE_MISSION_ID }
    private val talks get() = GameCatalog.missions.first { it.id == PowRules.NEGOTIATION_MISSION_ID }
    private val sere2 = base.copy(completedMissionIds = base.completedMissionIds + TacticalCalculator.SERE_SCHOOL_ID)
    private val sere3 = sere2.copy(completedMissionIds = sere2.completedMissionIds + TacticalCalculator.SERE3_SCHOOL_ID)
    private object Zero : Random() { override fun nextBits(bitCount: Int) = 0 }
    private object Max : Random() {
        override fun nextBits(bitCount: Int) = 0
        override fun nextInt(until: Int) = until - 1
    }

    @After fun reset() { PowRules.activeRescueTargetEvading = false }

    @Test
    fun term_odds() {
        val pow = PowRules.capture(base, field, 0L)
        assertEquals(1, pow.captivityTerm)
        assertEquals(Triple(55, 30, 15), PowRules.termOdds(pow))
        assertEquals(Triple(65, 28, 7), PowRules.termOdds(PowRules.capture(sere2, field, 0L)))
        // Hardened captors move release odds into "held over"
        assertEquals(Triple(45, 40, 15), PowRules.termOdds(pow.copy(captorsHardenedPct = 10)))
        // Last term: released or lost only
        assertEquals(Triple(80, 0, 20), PowRules.termOdds(pow.copy(captivityTerm = 3)))
        assertEquals(PowRules.TermResult.RELEASED, PowRules.rollTerm(pow, 54))
        assertEquals(PowRules.TermResult.HELD, PowRules.rollTerm(pow, 55))
        assertEquals(PowRules.TermResult.DIED, PowRules.rollTerm(pow, 85))
        assertEquals(PowRules.TermResult.DIED, PowRules.rollTerm(pow.copy(captivityTerm = 3), 85))
    }

    @Test
    fun held_over_reopens_rescue_and_talks() {
        val pow = PowRules.capture(base, field, 0L).copy(powRescueClosed = true, powTalksCooldownUntilMillis = 99L, escapeOffered = true)
        val now = PowRules.captivityMillis(pow) + 5
        val next = PowRules.newTerm(pow, now)
        assertEquals(2, next.captivityTerm)
        assertFalse(next.powRescueClosed)
        assertFalse(next.escapeOffered)
        assertTrue(PowRules.rescueWindowOpen(next, now + 1))
        assertEquals(3, PowRules.newTerm(next.copy(captivityTerm = 3), now).captivityTerm)
        assertEquals(GameCalendar.daysToMillis(182.0), PowRules.captivityMillis(base))
    }

    @Test
    fun escape_odds_and_timing() {
        assertEquals(5, PowRules.escapeChancePct(base))
        assertEquals(12, PowRules.escapeChancePct(sere2))
        assertEquals(20, PowRules.escapeChancePct(sere3))
        assertEquals(5, PowRules.failedEscapeDeathPct(sere3))
        val pow = PowRules.capture(base, field, 0L)
        val half = (pow.captivityResolveAtMillis - pow.capturedAtMillis) / 2
        assertFalse(PowRules.escapeDue(pow, half - 1))
        assertTrue(PowRules.escapeDue(pow, half))
        assertFalse(PowRules.escapeDue(pow.copy(escapeOffered = true), half))
    }

    @Test
    fun escape_evade_and_recapture() {
        val pow = PowRules.capture(base, field, 0L)
        val out = PowRules.escaped(pow, 1_000L)
        assertTrue(PowRules.isEvading(out, 1_001L))
        assertEquals(0L, out.captivityResolveAtMillis)
        // Personnel Recovery is open even with the rescue window closed; talks are not
        val closed = out.copy(powRescueClosed = true)
        assertNull(PowRules.operationBlocker(rescue, closed, emptyList(), 0.0, 1_001L))
        assertNotNull(PowRules.operationBlocker(talks, closed, emptyList(), 999.0, 1_001L))
        // Nobody came: caught again for a fresh term with harder captors
        val lapseAt = out.evadingUntilMillis + 1
        assertTrue(PowRules.evasionLapsed(out, lapseAt))
        val caught = PowRules.recaptured(out, lapseAt)
        assertEquals(2, caught.captivityTerm)
        assertEquals(10, caught.captorsHardenedPct)
        assertEquals(0L, caught.evadingUntilMillis)
        assertTrue(caught.captivityResolveAtMillis > lapseAt)
        // The calendar keeps running while they evade
        assertTrue(GameCalendar.activeIntervals(listOf(out), emptyList(), emptyList()).any { it.second == out.evadingUntilMillis })
    }

    @Test
    fun personnel_recovery_is_easier() {
        val team = GameCatalog.initialOperators.take(3).map { it.copy(unlocked = true) }
        val plain = TacticalCalculator.missionSuccessOddsPct(rescue, team, emptyList(), emptyList(), emptyList())
        PowRules.activeRescueTargetEvading = true
        val pickup = TacticalCalculator.missionSuccessOddsPct(rescue, team, emptyList(), emptyList(), emptyList())
        assertTrue(pickup > plain)
    }

    @Test
    fun escape_event_outcomes() {
        val pow = PowRules.capture(sere3, field, 0L)
        val ctx = SoldierEventContext(listOf(pow), emptyList(), emptyList(), emptyList(), ResourceBundle(), 10L)
        val ev = PendingSoldierEvent(SoldierEventKind.ESCAPE_CHANCE, pow.id, null, 0L, 100L)
        assertTrue(SoldierEvents.stillValid(ev, ctx))
        assertEquals(listOf("attempt", "decline"), SoldierEvents.card(ev, ctx).choices.map { it.id })
        val success = SoldierEvents.apply(ev, "attempt", ctx, Zero).operatorUpdates.single()
        assertTrue(success.evadingUntilMillis > 10L)
        val caught = SoldierEvents.apply(ev, "attempt", ctx, Max).operatorUpdates.single()
        assertEquals(OperatorStatus.CAPTURED, caught.status)
        assertEquals(10, caught.captorsHardenedPct)
        assertTrue(SoldierEvents.apply(ev, "decline", ctx, Zero).operatorUpdates.isEmpty())
        // Released soldiers come home with a clean captivity record
        val home = PowRules.released(caught, 20L, 1.0)
        assertEquals(0, home.captivityTerm)
        assertEquals(0, home.captorsHardenedPct)
    }

    @Test
    fun expired_contract_keeps_soldier_home() {
        val y = GameCalendar.DAYS_PER_YEAR
        val op = base.copy(joinedDay = 0.0, contractStartDay = 0.0, contractEndDay = 4 * y)
        GameCalendar.today = 9 * y
        try {
            val r = TacticalCalculator.canOperatorDeployOnMission(op, field, emptySet())
            assertFalse(r.eligible)
            assertTrue(r.missingRequirements.single().startsWith("Contract up"))
            GameCalendar.today = 3 * y
            assertFalse(TacticalCalculator.canOperatorDeployOnMission(op, field, emptySet()).missingRequirements.any { it.startsWith("Contract up") })
        } finally { GameCalendar.today = 0.0 }
    }
}
