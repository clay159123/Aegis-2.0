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
