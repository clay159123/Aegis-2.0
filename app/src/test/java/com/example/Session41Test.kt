package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.AwardRules
import com.example.domain.GameCalendar
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class Session41Test {
    private val base = GameCatalog.createPostBasicRecruit("op_s41", OperatorSpecialty.ASSAULT, emptySet(), 0)
        .copy(unlocked = true, completedMissionIds = setOf("msn_basic_qual"))
    private fun mission(id: String) = GameCatalog.missions.first { it.id == id }
    private val Y = GameCalendar.DAYS_PER_YEAR

    @After fun reset() { GameCalendar.today = 0.0 }

    @Test
    fun calendar_only_counts_time_when_something_runs() {
        val intervals = listOf(0L to 10_000L, 5_000L to 12_000L, 20_000L to 25_000L)
        assertEquals(17_000L, GameCalendar.activeMillisBetween(intervals, 0L, 30_000L))
        assertEquals(0L, GameCalendar.activeMillisBetween(intervals, 12_000L, 20_000L))
        // Open-start windows (leave, dwell, medbay) count from the start of the range
        assertEquals(4_000L, GameCalendar.activeMillisBetween(listOf(Long.MIN_VALUE to 4_000L), 0L, 10_000L))
        // Testing rate: 1 second per game day
        assertEquals(17.0, GameCalendar.advance(0.0, intervals, 0L, 30_000L), 0.001)
    }

    @Test
    fun labels_and_northern_seasons() {
        assertEquals("Year 1 · Jan · Winter", GameCalendar.label(0.0))
        assertEquals("Spring", GameCalendar.season(GameCalendar.DAYS_PER_MONTH * 3 + 1))
        assertEquals("Summer", GameCalendar.season(GameCalendar.DAYS_PER_MONTH * 6 + 1))
        assertEquals("Winter", GameCalendar.season(GameCalendar.DAYS_PER_MONTH * 11 + 1))
        assertEquals(2, GameCalendar.year(Y + 1))
    }

    @Test
    fun timing_table() {
        assertEquals(21, GameCalendar.gameDaysFor(mission("msn_night_qual")))
        assertEquals(63, GameCalendar.gameDaysFor(mission("msn_ranger_school")))
        assertEquals(365, GameCalendar.gameDaysFor(mission("msn_sf_school")))
        assertEquals(14, GameCalendar.gameDaysFor(mission("msn_ridge_patrol")))
        assertEquals(91, GameCalendar.gameDaysFor(mission("msn_deep_expedition")))
        assertEquals(30 + 30, GameCalendar.gameDaysFor(mission("msn_plt_valley_sweep")))
        assertEquals(182, GameCalendar.gameDaysFor(mission("msn_clan_sahr")))
        assertEquals(30, GameCalendar.gameDaysFor(mission("msn_sf_da_raid")))
        assertEquals(122, GameCalendar.gameDaysFor(mission("camp_uw_1")))
        // Catalog timers follow the table at the testing rate
        assertEquals(63, mission("msn_ranger_school").durationSeconds)
        assertTrue(GameCatalog.missions.all { it.durationSeconds == GameCalendar.gameDaysFor(it) })
    }

    @Test
    fun dwell_blocks_field_ops_but_not_rescue_or_schools() {
        val home = base.copy(dwellUntilMillis = System.currentTimeMillis() + 60_000L)
        val field = TacticalCalculator.canOperatorDeployOnMission(home, mission("msn_ridge_patrol"), emptySet())
        assertFalse(field.eligible)
        assertTrue(field.missingRequirements.single().startsWith("Dwell"))
        val rescue = TacticalCalculator.canOperatorDeployOnMission(home, mission(GameCalendar.POW_RESCUE_ID), emptySet())
        assertFalse(rescue.missingRequirements.any { it.startsWith("Dwell") })
        val school = TacticalCalculator.canOperatorDeployOnMission(home, mission("msn_night_qual"), emptySet())
        assertFalse(school.missingRequirements.any { it.startsWith("Dwell") })
    }

    @Test
    fun ages_service_and_stripes() {
        val op = base.copy(joinedDay = 100.0, ageAtJoin = 19.5, priorServiceDays = 2 * Y)
        GameCalendar.today = 100.0 + 4 * Y
        assertEquals(23, GameCalendar.age(op))
        assertEquals(6.0, GameCalendar.yearsServed(op), 0.001)
        assertEquals(2, GameCalendar.serviceStripes(op))
        assertFalse(GameCalendar.mustRetire(op))
        assertTrue(GameCalendar.mustRetire(op.copy(priorServiceDays = 26 * Y)))
    }

    @Test
    fun retirement_by_years() {
        val sgt = base.copy(xp = OperatorRank.SGT.xpRequired, authorizedTier = RankTier.NCO, pinnedRank = OperatorRank.SGT, joinedDay = 0.0, priorServiceDays = 19 * Y)
        GameCalendar.today = 0.5 * Y
        assertFalse(TacticalCalculator.evaluateRetirementEligibility(sgt).eligible)
        GameCalendar.today = 1.1 * Y
        assertTrue(TacticalCalculator.evaluateRetirementEligibility(sgt).eligible)
    }

    @Test
    fun time_in_grade_holds_pins() {
        val sgt = base.copy(xp = OperatorRank.SSG.xpRequired, authorizedTier = RankTier.NCO, pinnedRank = OperatorRank.SGT, joinedDay = 0.0, rankSinceDay = 0.0, rankSinceName = "SGT")
        GameCalendar.today = 0.5 * Y
        assertNull(sgt.pinEligibleRank)
        GameCalendar.today = 1.1 * Y
        assertEquals(OperatorRank.SSG, sgt.pinEligibleRank)
    }

    @Test
    fun good_conduct_every_three_clean_years() {
        val m = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT && it.risk == MissionRisk.LOW }
        val vet = base.copy(missionsCompleted = 5, goodConductSinceDay = 0.0)
        GameCalendar.today = 2.0 * Y
        assertFalse(AwardRules.GOOD_CONDUCT_ID in AwardRules.serviceAwardsFor(vet, m, OperatorOutcomeType.SAFE, true, emptyMap(), emptySet(), Random(1)).medalIds)
        GameCalendar.today = 3.1 * Y
        val r = AwardRules.serviceAwardsFor(vet, m, OperatorOutcomeType.SAFE, true, emptyMap(), emptySet(), Random(1))
        assertTrue(AwardRules.GOOD_CONDUCT_ID in r.medalIds)
        assertEquals(3.1 * Y, r.operator.goodConductSinceDay, 0.001)
        // A wound restarts the clean stretch
        val hurt = AwardRules.serviceAwardsFor(vet, m, OperatorOutcomeType.WOUNDED, true, emptyMap(), emptySet(), Random(1))
        assertFalse(AwardRules.GOOD_CONDUCT_ID in hurt.medalIds)
        assertEquals(3.1 * Y, hurt.operator.goodConductSinceDay, 0.001)
    }

    @Test
    fun captivity_runs_six_months() {
        assertEquals(GameCalendar.daysToMillis(182.0), com.example.domain.PowRules.captivityMillis(base))
    }
}
