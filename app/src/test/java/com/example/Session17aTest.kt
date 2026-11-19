package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session 17a: Flight Hours 1-10, SF school retries, general boards, Airfield levels. */
class Session17aTest {
    private val base = GameCatalog.createPostBasicRecruit("op_s17", OperatorSpecialty.ASSAULT, emptySet(), 0)

    @After fun reset() { TacticalCalculator.airfieldLevel = 0 }

    @Test
    fun flightHoursScaleWithTheBooking() {
        val pilot = base.copy(specialty = OperatorSpecialty.AVIATION, flightSkill = 10)
        assertEquals(5.0, TacticalCalculator.flightHoursDurationMultiplier(10), 0.001)
        assertEquals(0.5, TacticalCalculator.flightHoursDurationMultiplier(1), 0.001)
        val passed = TacticalCalculator.applyFlightHours(pilot, 6, passed = true, airfield = 0)
        assertEquals(6, passed.flightHoursLogged)
        assertEquals(10 + 6 * TacticalCalculator.FLIGHT_SKILL_PER_HOUR, passed.flightSkill)
        val failed = TacticalCalculator.applyFlightHours(pilot, 6, passed = false, airfield = 0)
        assertEquals(3, failed.flightHoursLogged)
        assertTrue(failed.flightSkill in 11 until passed.flightSkill)
        // The Airfield adds skill per session every two levels
        assertEquals(passed.flightSkill + 2, TacticalCalculator.applyFlightHours(pilot, 6, passed = true, airfield = 4).flightSkill)
        assertEquals(TacticalCalculator.MAX_FLIGHT_SKILL, TacticalCalculator.applyFlightHours(pilot.copy(flightSkill = 99), 10, true).flightSkill)
    }

    @Test
    fun sfSchoolsHaveNoHardLimitButRetriesCostMore() {
        val sf = GameCatalog.missions.first { it.id == "msn_sf_school" }
        val limit = sf.schoolRequirements!!.maxAttempts!!
        val tired = base.copy(schoolAttempts = mapOf(sf.id to limit))
        val elig = TacticalCalculator.canOperatorDeployOnMission(tired, sf, emptySet())
        assertFalse(elig.missingRequirements.any { it.startsWith("Washed out") })
        assertEquals(0, TacticalCalculator.schoolRetrySurcharge(sf, listOf(base), 1.0))
        val one = TacticalCalculator.schoolRetrySurcharge(sf, listOf(tired), 1.0)
        val two = TacticalCalculator.schoolRetrySurcharge(sf, listOf(tired.copy(schoolAttempts = mapOf(sf.id to limit + 1))), 1.0)
        assertTrue(one > 0 && two > one)
        // Item 101: every school now works this way
        val ranger = GameCatalog.missions.first { it.id == "msn_ranger_school" }
        val rangerOut = base.copy(schoolAttempts = mapOf(ranger.id to ranger.schoolRequirements!!.maxAttempts!!))
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(rangerOut, ranger, emptySet()).missingRequirements.any { it.startsWith("Washed out") })
        assertTrue(TacticalCalculator.schoolRetrySurcharge(ranger, listOf(rangerOut), 1.0) > 0)
    }

    @Test
    fun everyGeneralsStarIsABoard() {
        listOf(OperatorRank.COL to OperatorRank.BG, OperatorRank.BG to OperatorRank.MG, OperatorRank.MG to OperatorRank.LTG, OperatorRank.LTG to OperatorRank.GEN)
            .forEach { (a, b) -> assertTrue("$a→$b", OperatorRank.isBoardGate(a, b)) }
        val bg = base.copy(xp = OperatorRank.COL.xpRequired, authorizedTier = RankTier.OFFICER).promotedTo(OperatorRank.BG)
        assertEquals(OperatorRank.BG, bg.rank)
        // Plenty of XP no longer pins a second star by itself
        assertEquals(OperatorRank.BG, bg.copy(xp = OperatorRank.GEN.xpRequired + 1000).rank)
        assertEquals(OperatorRank.MG, bg.promotedTo(OperatorRank.MG).rank)
        assertTrue(bg.isAtCategoryPromotionGate)
    }

    @Test
    fun airfieldLevelsMatter() {
        assertEquals(1, TacticalCalculator.maxHelicopterMissions(3))
        assertEquals(2, TacticalCalculator.maxHelicopterMissions(4))
        assertEquals(3, TacticalCalculator.maxHelicopterMissions(8))
        val pilot = base.copy(specialty = OperatorSpecialty.AVIATION, flightSkill = 20)
        TacticalCalculator.airfieldLevel = 0
        val slow = TacticalCalculator.insertionDurationMultiplier(InsertionMethod.HELICOPTER, pilot)
        val lowSurv = TacticalCalculator.insertionSurvivalDelta(InsertionMethod.HELICOPTER, pilot, 0, false)
        val scatter = TacticalCalculator.scatterChancePct(InsertionMethod.AIRBORNE)
        TacticalCalculator.airfieldLevel = 6
        assertTrue(TacticalCalculator.insertionDurationMultiplier(InsertionMethod.HELICOPTER, pilot) < slow)
        assertEquals(lowSurv + 3, TacticalCalculator.insertionSurvivalDelta(InsertionMethod.HELICOPTER, pilot, 0, false))
        assertTrue(TacticalCalculator.scatterChancePct(InsertionMethod.AIRBORNE) < scatter)
        val night = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT && it.isNightOp }
        assertEquals(TacticalCalculator.NIGHT_FLYING_PASS_BONUS, TacticalCalculator.insertionPassDelta(night, InsertionMethod.HELICOPTER, false))
        val school = GameCatalog.missions.first { it.id == TacticalCalculator.FLIGHT_SCHOOL_MISSION_ID }
        val lowField = listOf(FacilityState(FacilityType.AIRFIELD, 1))
        val highField = listOf(FacilityState(FacilityType.AIRFIELD, 6))
        val cand = base.copy(xp = OperatorRank.WO1.xpRequired, authorizedTier = RankTier.WARRANT, warrantGrade = 1)
        assertTrue(TacticalCalculator.schoolPassOddsPct(school, cand, emptyList(), null, highField) >
            TacticalCalculator.schoolPassOddsPct(school, cand, emptyList(), null, lowField))
        assertTrue(TacticalCalculator.airfieldPerkSummary(5).contains("Night flying"))
    }
}
