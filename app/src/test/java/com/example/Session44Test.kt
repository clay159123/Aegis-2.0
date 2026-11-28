package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.BaseEvents
import com.example.domain.GameCalendar
import com.example.domain.PendingSoldierEvent
import com.example.domain.SoldierEventContext
import com.example.domain.SoldierEventKind
import com.example.domain.SoldierEvents
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class Session44Test {
    private val M = GameCalendar.DAYS_PER_MONTH
    private fun soldier(i: Int) = GameCatalog.createPostBasicRecruit("op_b$i", OperatorSpecialty.ASSAULT, emptySet(), i)
        .copy(unlocked = true, completedMissionIds = setOf("msn_basic_qual"), morale = 60)
    private val hq = soldier(99).copy(id = "op_hq", status = OperatorStatus.HQ_STAFF, hqRole = HqStaffRole.S1_PERSONNEL_HONORS)
    private fun ctx(ops: List<Operator>, gate: Int = 1, facs: List<FacilityState>? = null, squads: List<TacticalSquad> = emptyList()) =
        SoldierEventContext(ops, squads, emptyList(), facs ?: FacilityType.entries.map { FacilityState(it, if (it == FacilityType.MAIN_GATE) gate else 2) },
            ResourceBundle(credits = 5_000.0, supplies = 5_000.0), 1_000L)
    private fun ev(kind: SoldierEventKind, target: String? = null) = PendingSoldierEvent(kind, "op_hq", target, 0L, 10L)

    @After fun reset() { GameCalendar.today = 0.0 }

    @Test
    fun calendar_events_by_month() {
        // Item 198: year 3 has Family Day (every 4 years) and block leave (odd years), no Ball and no IG
        val y3 = 2 * GameCalendar.DAYS_PER_YEAR
        assertNull(BaseEvents.dueScheduled(y3, emptySet()))
        assertEquals(SoldierEventKind.BASE_FAMILY_DAY, BaseEvents.dueScheduled(y3 + 3 * M + 1, emptySet()))
        val familyDone = setOf(BaseEvents.key(SoldierEventKind.BASE_FAMILY_DAY, y3 + 3 * M + 1))
        assertNull(BaseEvents.dueScheduled(y3 + 6 * M + 1, familyDone))
        assertEquals(SoldierEventKind.BASE_BLOCK_LEAVE, BaseEvents.dueScheduled(y3 + 11 * M + 1, familyDone))
        // Year 2 has the Ball, announced a month ahead
        val y2 = GameCalendar.DAYS_PER_YEAR
        assertNull(BaseEvents.dueScheduled(y2 + 4 * M + 1, emptySet()))
        assertEquals(SoldierEventKind.BASE_ARMY_BALL, BaseEvents.dueScheduled(y2 + 5 * M + 1, emptySet()))
        assertEquals(SoldierEventKind.BASE_ARMY_BALL to "Jun", BaseEvents.upcoming(y2 + 4 * M + 5, emptySet()))
        assertNull(BaseEvents.upcoming(y3 + 4 * M + 5, familyDone))
        assertFalse(BaseEvents.scheduledLapsed(SoldierEventKind.BASE_ARMY_BALL, 6 * M + 1))
        assertTrue(BaseEvents.scheduledLapsed(SoldierEventKind.BASE_ARMY_BALL, 7 * M + 1))
        // The IG and Family Day never share a year; each comes once in 4 years
        for (y in 1..8) {
            val d = (y - 1) * GameCalendar.DAYS_PER_YEAR
            assertFalse(BaseEvents.scheduledThisYear(SoldierEventKind.BASE_IG_INSPECTION, d) && BaseEvents.scheduledThisYear(SoldierEventKind.BASE_FAMILY_DAY, d))
            assertTrue(BaseEvents.scheduledThisYear(SoldierEventKind.BASE_ARMY_BALL, d) != BaseEvents.scheduledThisYear(SoldierEventKind.BASE_BLOCK_LEAVE, d))
        }
        assertEquals(2, (1..8).count { BaseEvents.scheduledThisYear(SoldierEventKind.BASE_IG_INSPECTION, (it - 1) * GameCalendar.DAYS_PER_YEAR) })
        // Family Day comes once a year
        assertEquals(1, BaseEvents.SCHEDULED.keys.count { it == SoldierEventKind.BASE_FAMILY_DAY })
    }

    @Test
    fun random_events_fit_the_season() {
        val c = ctx(listOf(hq) + (0 until 5).map { soldier(it) })
        val spring = (0 until 200).mapNotNull { BaseEvents.pickRandom(c, 3 * M + 1, Random(it))?.first }.toSet()
        assertFalse(SoldierEventKind.BASE_STORM in spring)
        assertFalse(SoldierEventKind.BASE_FISCAL_YEAR_END in spring)
        val sept = (0 until 200).mapNotNull { BaseEvents.pickRandom(c, 8 * M + 1, Random(it))?.first }.toSet()
        assertTrue(SoldierEventKind.BASE_FISCAL_YEAR_END in sept)
        val winter = (0 until 200).mapNotNull { BaseEvents.pickRandom(c, 0.5, Random(it))?.first }.toSet()
        assertTrue(SoldierEventKind.BASE_STORM in winter)
    }

    @Test
    fun ball_puts_attendees_in_mess_and_scales_with_turnout() {
        val ops = listOf(hq) + (0 until 3).map { soldier(it) }
        val c = ctx(ops)
        val out = SoldierEvents.apply(ev(SoldierEventKind.BASE_ARMY_BALL), "host", c)
        assertEquals(4, out.operatorUpdates.size)
        assertTrue(out.operatorUpdates.all { it.eventKit == "MESS" && it.atEventUntilMillis > 1_000L && it.morale == 68 })
        assertEquals(-BaseEvents.ballCost(4).toDouble(), out.creditsDelta, 0.0)
        assertEquals(20.0, out.prestigeDelta, 0.0)
        val skip = SoldierEvents.apply(ev(SoldierEventKind.BASE_ARMY_BALL), "skip", c)
        assertTrue(skip.operatorUpdates.all { it.morale == 58 })
        // At the event: no deployments, and the duty dress is Mess
        val attendee = out.operatorUpdates.first { !it.isHqStaffer }.copy(atEventUntilMillis = System.currentTimeMillis() + 60_000L)
        val field = GameCatalog.missions.first { it.id == "msn_ridge_patrol" }
        assertEquals(listOf("At a base event"), TacticalCalculator.canOperatorDeployOnMission(attendee, field, emptySet()).missingRequirements)
        assertEquals(DutyUniformContext.DIPLOMATIC_MISSION, TacticalCalculator.resolveDutyContextForOperator(attendee))
    }

    @Test
    fun small_turnouts_and_hq_only() {
        val three = (0 until 3).map { soldier(it) }
        val noGuard = SoldierEvents.apply(ev(SoldierEventKind.BASE_GENERAL_VISIT), "guard", ctx(three))
        assertTrue(noGuard.operatorUpdates.isEmpty())
        assertTrue(noGuard.banner.contains("Not enough"))
        val brief = SoldierEvents.apply(ev(SoldierEventKind.BASE_GENERAL_VISIT), "brief", ctx(listOf(hq)))
        assertTrue(brief.prestigeDelta > 0)
        val guard = SoldierEvents.apply(ev(SoldierEventKind.BASE_GENERAL_VISIT), "guard", ctx((0 until 5).map { soldier(it) }))
        assertTrue(guard.operatorUpdates.all { it.eventKit == "ALPHAS" })
        // Block leave: everyone or half
        val ten = (0 until 10).map { soldier(it) }
        assertEquals(10, SoldierEvents.apply(ev(SoldierEventKind.BASE_BLOCK_LEAVE), "all", ctx(ten)).operatorUpdates.count { it.status == OperatorStatus.ON_LEAVE })
        assertEquals(5, SoldierEvents.apply(ev(SoldierEventKind.BASE_BLOCK_LEAVE), "skeleton", ctx(ten)).operatorUpdates.size)
        // Base events stay valid even with nobody idle
        assertTrue(SoldierEvents.stillValid(ev(SoldierEventKind.BASE_GATE_INCIDENT), ctx(emptyList()).copy(now = 5L)))
    }

    @Test
    fun gate_and_storm() {
        val lock1 = SoldierEvents.apply(ev(SoldierEventKind.BASE_GATE_INCIDENT), "lockdown", ctx(emptyList(), gate = 1))
        val lock5 = SoldierEvents.apply(ev(SoldierEventKind.BASE_GATE_INCIDENT), "lockdown", ctx(emptyList(), gate = 5))
        assertEquals(9, lock1.productionCutPct)
        assertEquals(5, lock5.productionCutPct)
        val facs = FacilityType.entries.map { if (it == FacilityType.COMMAND_HQ) FacilityState(it, 2, upgradeEndsAtMillis = 2_000L, upgradeStartedAtMillis = 1_000L) else FacilityState(it, 2) }
        val slowed = SoldierEvents.apply(ev(SoldierEventKind.BASE_STORM), "wait", ctx(emptyList(), facs = facs)).facilityUpdates.single()
        assertEquals(2_200L, slowed.upgradeEndsAtMillis)
        assertEquals("lockdown", BaseEvents.defaultChoice(SoldierEventKind.BASE_GATE_INCIDENT))
        assertEquals("skip", BaseEvents.defaultChoice(SoldierEventKind.BASE_ARMY_BALL))
    }

    @Test
    fun team_leader_events() {
        val sl = soldier(1).copy(id = "sl")
        val tl = soldier(2).copy(id = "tl")
        val pv = soldier(3).copy(id = "pv")
        val sq = TacticalSquad("sq", "Alpha", listOf("sl", "tl", "pv"), "sl", teamLeaderId = "tl")
        val c = ctx(listOf(sl, tl, pv), squads = listOf(sq))
        val kinds = SoldierEvents.candidates(c).filter { it.operatorId == "tl" }.map { it.kind }.toSet()
        assertTrue(SoldierEventKind.TL_TRAINING_LANE in kinds)
        assertTrue(SoldierEventKind.TL_FRICTION in kinds)
        val lane = SoldierEvents.apply(PendingSoldierEvent(SoldierEventKind.TL_TRAINING_LANE, "tl", "sq", 0L, 10L), "approve", c)
        assertEquals(BaseEvents.TL_LANE_COHESION, lane.squadUpdates.single().cohesionXp)
        // Only the team leader raises them
        assertFalse(SoldierEvents.candidates(c).any { it.operatorId == "sl" && it.kind == SoldierEventKind.TL_TRAINING_LANE })
    }
}
