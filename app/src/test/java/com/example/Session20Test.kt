package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.*
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session 20: prisoners of war (63) and SERE in captivity (115b). */
class Session20Test {
    private val now = 50_000_000L
    private fun soldier(id: String = "a", sere: Boolean = false) =
        GameCatalog.createPostBasicRecruit(id, OperatorSpecialty.ASSAULT, emptySet(), 0).copy(
            id = id, unlocked = true, baseStealth = 15, baseResilience = 15,
            completedMissionIds = setOf("msn_basic_qual") + if (sere) setOf(TacticalCalculator.SERE_SCHOOL_ID) else emptySet()
        )
    private fun field(risk: MissionRisk) = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT && it.risk == risk }
    private val rescue = GameCatalog.missions.first { it.id == PowRules.RESCUE_MISSION_ID }
    private val talks = GameCatalog.missions.first { it.id == PowRules.NEGOTIATION_MISSION_ID }

    @Test
    fun captureOddsFollowRiskOutcomeStatsAndSere() {
        val a = soldier()
        assertEquals(10.0, PowRules.captureChancePct(field(MissionRisk.EXTREME), a, missionSucceeded = false), 0.001)
        assertEquals(2.0, PowRules.captureChancePct(field(MissionRisk.EXTREME), a, missionSucceeded = true), 0.001)
        assertEquals(0.0, PowRules.captureChancePct(field(MissionRisk.LOW), a, missionSucceeded = true), 0.001)
        assertEquals(0.0, PowRules.captureChancePct(talks, a, missionSucceeded = false), 0.001)
        val school = GameCatalog.missions.first { it.scope == MissionScope.TRAINING }
        assertEquals(0.0, PowRules.captureChancePct(school, a, missionSucceeded = false), 0.001)
        assertEquals(6.0 * PowRules.SERE_CAPTURE_FACTOR, PowRules.captureChancePct(field(MissionRisk.HIGH), soldier(sere = true), false), 0.001)
        val tough = a.copy(baseStealth = 40, baseResilience = 40)
        assertEquals(6.0 * (1 - PowRules.MAX_STAT_REDUCTION), PowRules.captureChancePct(field(MissionRisk.HIGH), tough, false), 0.001)
        assertEquals(30.0, PowRules.captureChancePct(field(MissionRisk.EXTREME), a, false, blownCover = true), 0.001)
    }

    @Test
    fun capturedSoldiersKeepTheirSlotAndCannotDeploy() {
        val m = field(MissionRisk.HIGH)
        val pow = PowRules.capture(soldier(), m, now)
        assertEquals(OperatorStatus.CAPTURED, pow.status)
        assertEquals(1, pow.timesCaptured)
        assertEquals(m.theater, pow.captureTheater)
        assertEquals(now + PowRules.captivityMillis(soldier()), pow.captivityResolveAtMillis)
        assertFalse(pow.status.isOutOfActiveService)
        assertTrue(SquadCohesionRules.isOperatorEligibleForFieldSquad(pow))
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(pow, m, emptySet()).eligible)
        // SERE: shorter captivity, better odds
        val serePow = PowRules.capture(soldier(sere = true), m, now)
        assertTrue(serePow.captivityResolveAtMillis < pow.captivityResolveAtMillis)
        assertTrue(PowRules.releaseSurvivalPct(serePow) > PowRules.releaseSurvivalPct(pow))
    }

    @Test
    fun rescueWindowAndOperationBlockers() {
        val pow = PowRules.capture(soldier(), field(MissionRisk.HIGH), now)
        assertTrue(PowRules.rescueWindowOpen(pow, now + 1000L))
        assertFalse(PowRules.rescueWindowOpen(pow, now + GameTiming.millis(PowRules.RESCUE_WINDOW_MILLIS) + 1))
        assertNull(PowRules.operationBlocker(rescue, pow, emptyList(), 0.0, now + 1000L))
        assertNotNull(PowRules.operationBlocker(rescue, pow, emptyList(), 0.0, now + GameTiming.millis(PowRules.RESCUE_WINDOW_MILLIS) + 1))
        assertNotNull(PowRules.operationBlocker(rescue, soldier(), emptyList(), 0.0, now))
        assertNotNull(PowRules.operationBlocker(talks, pow, emptyList(), PowRules.NEGOTIATION_PRESTIGE_COST - 1, now))
        assertNull(PowRules.operationBlocker(talks, pow, emptyList(), PowRules.NEGOTIATION_PRESTIGE_COST, now + 99_999_999L))
        val running = ActiveDeployment("d", rescue.id, listOf("x"), now, 1000L, 100, 100, targetOperatorId = pow.id)
        assertNotNull(PowRules.operationBlocker(talks, pow, listOf(running), 999.0, now))
    }

    @Test
    fun returningPowsRecoverAndEarnThePowMedal() {
        val medal = GameCatalog.medals.first { it.id == PowRules.POW_MEDAL_ID }
        assertEquals(AwardCategory.RIBBON, medal.category)
        val pow = PowRules.capture(soldier(), field(MissionRisk.HIGH), now)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(medal, pow, emptySet(), emptyList()).eligible)
        val home = PowRules.released(pow, now, 1.0)
        assertEquals(OperatorStatus.RECOVERING, home.status)
        assertEquals(now + GameTiming.millis((TacticalCalculator.BASE_RECOVERY_MILLIS * PowRules.RETURN_RECOVERY_FACTOR).toLong()), home.recoveryUntilMillis)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(medal, home, emptySet(), emptyList()).eligible)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(medal, soldier(), emptySet(), emptyList()).eligible)
        assertEquals(OperatorStatus.FALLEN, PowRules.diedInCaptivity(pow).status)
    }

    @Test
    fun powOperationsStayOffTheMissionBoard() {
        for (m in listOf(rescue, talks)) {
            assertNull(m.campaignId)
            assertFalse(m.isEvergreenStandalone)
            assertTrue(GameCatalog.rotatingStandalonePool.none { it.id == m.id })
            assertTrue(PowRules.isPowOperation(m))
        }
        assertEquals(MissionScope.FIELD_COMBAT, rescue.scope)
        assertEquals(MissionScope.STAFF_DIPLOMATIC, talks.scope)
    }
}

/** Session 20 follow-up: who sits at the table, and what a failed round costs. */
class Session20TalksTest {
    private val now = 50_000_000L
    private fun op(id: String, rank: OperatorRank, status: OperatorStatus = OperatorStatus.READY, role: HqStaffRole? = null) =
        GameCatalog.createPostBasicRecruit(id, OperatorSpecialty.COMMAND, emptySet(), 0).copy(
            id = id, unlocked = true, xp = rank.xpRequired, authorizedTier = rank.tier, status = status, hqRole = role,
            flagGrade = if (rank.tier == RankTier.FLAG_OFFICER) rank.flagGrade else 0
        )
    private val talks = GameCatalog.missions.first { it.id == PowRules.NEGOTIATION_MISSION_ID }
    private val pow = PowRules.capture(op("pow", OperatorRank.SGT), GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT }, now)

    @Test
    fun seniorityBilletsAndSupportSetTheBonus() {
        assertEquals(15, PowRules.leadBonusPct(op("bg", OperatorRank.BG)))
        assertEquals(20, PowRules.leadBonusPct(op("gen", OperatorRank.GEN)))
        assertEquals(8, PowRules.leadBonusPct(op("col", OperatorRank.COL)))
        assertEquals(8, PowRules.leadBonusPct(op("sgm", OperatorRank.SGM)))
        assertEquals(0, PowRules.leadBonusPct(op("cpt", OperatorRank.CPT)))
        // Only the best lead counts; extras add support, billets add their own
        val gen = op("gen", OperatorRank.GEN, OperatorStatus.HQ_STAFF, HqStaffRole.CO_COMMANDING_OFFICER)
        val s2 = op("s2", OperatorRank.MAJ, OperatorStatus.HQ_STAFF, HqStaffRole.S2_INTEL_OVERWATCH)
        val dcg = op("dcg", OperatorRank.BG, OperatorStatus.HQ_STAFF, HqStaffRole.DEPUTY_COMMANDER)
        assertEquals(20, PowRules.talksTeamBonusPct(listOf(gen)))
        assertEquals(20 + 5 + 3, PowRules.talksTeamBonusPct(listOf(gen, s2)))
        assertEquals(20 + 10 + 6, PowRules.talksTeamBonusPct(listOf(gen, s2, dcg)))
        assertEquals(0 + 0, PowRules.talksTeamBonusPct(listOf(op("p1", OperatorRank.SPC), op("p2", OperatorRank.PFC))))
        // Generals close the deal for less
        assertEquals(PowRules.GENERAL_NEGOTIATION_PRESTIGE_COST, PowRules.talksPrestigeCost(listOf(gen)), 0.001)
        assertEquals(PowRules.NEGOTIATION_PRESTIGE_COST, PowRules.talksPrestigeCost(listOf(s2)), 0.001)
        // Juniors alone can't open talks
        assertNotNull(PowRules.operationBlocker(talks, pow, emptyList(), 999.0, now, listOf(op("p", OperatorRank.SPC))))
        assertNull(PowRules.operationBlocker(talks, pow, emptyList(), 999.0, now, listOf(op("p", OperatorRank.SPC), op("sfc", OperatorRank.SFC))))
        assertNotNull(PowRules.operationBlocker(talks, pow, emptyList(), 50.0, now, listOf(s2)))
        assertNull(PowRules.operationBlocker(talks, pow, emptyList(), 50.0, now, listOf(gen)))
    }

    @Test
    fun failedTalksCollapseHardenAndMayMoveOrKill() {
        var moved = 0; var killed = 0
        repeat(2000) { i ->
            val (after, _) = PowRules.failedTalks(pow, now, kotlin.random.Random(i))
            if (after.status == OperatorStatus.FALLEN) killed++
            else {
                assertEquals(1, after.powTalksFailures)
                assertTrue(PowRules.talksCoolingDown(after, now + 1000L))
                assertNotNull(PowRules.operationBlocker(talks, after, emptyList(), 999.0, now + 1000L))
                if (after.powRescueClosed) { moved++; assertFalse(PowRules.rescueWindowOpen(after, now + 1000L)) }
            }
        }
        assertTrue("killed $killed", killed in 60..140)
        assertTrue("moved $moved", moved in 850..1050)
        assertEquals(20 - 10, PowRules.talksModifierPct(listOf(op("gen", OperatorRank.GEN)), failures = 2))
        // A fresh capture clears the old record
        val again = PowRules.capture(pow.copy(powTalksFailures = 3, powRescueClosed = true), GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT }, now)
        assertEquals(0, again.powTalksFailures)
        assertFalse(again.powRescueClosed)
    }
}
