package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.TacticalCalculator
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session 17b: Air Assault and Fast Rope (59), Combat Diver TDY (60), aviation patch (100), cancel and abort (103). */
class Session17bTest {
    private val base = GameCatalog.createPostBasicRecruit("op_17b", OperatorSpecialty.ASSAULT, emptySet(), 0)
    private fun mission(id: String) = GameCatalog.missions.first { it.id == id }
    private fun airfield(level: Int) = listOf(FacilityState(FacilityType.AIRFIELD, level))
    private val pilot = base.copy(
        id = "op_pilot", specialty = OperatorSpecialty.AVIATION,
        xp = OperatorRank.WO1.xpRequired, authorizedTier = RankTier.WARRANT, warrantGrade = 1,
        completedMissionIds = setOf("msn_basic_qual", TacticalCalculator.WOCS_MISSION_ID, TacticalCalculator.FLIGHT_SCHOOL_MISSION_ID)
    )

    @Test
    fun fastRopeRidesWithEveryHelicopterMission() {
        val heloMissions = GameCatalog.missions.filter { InsertionMethod.HELICOPTER in it.allowedInsertions }
        assertTrue(heloMissions.isNotEmpty())
        heloMissions.forEach { assertTrue(it.id, InsertionMethod.FAST_ROPE in it.allowedInsertions) }
        assertTrue(InsertionMethod.FAST_ROPE.isHelicopter)
        assertFalse(InsertionMethod.AIRBORNE.isHelicopter)
    }

    @Test
    fun fastRopeNeedsAirfieldPilotAndAirAssaultTeam() {
        val m = GameCatalog.missions.first { InsertionMethod.FAST_ROPE in it.allowedInsertions }
        val qualified = base.copy(completedMissionIds = base.completedMissionIds + TacticalCalculator.AIR_ASSAULT_SCHOOL_ID)
        assertNotNull(TacticalCalculator.insertionBlocker(m, InsertionMethod.FAST_ROPE, listOf(qualified), pilot, airfield(2)))
        assertNotNull(TacticalCalculator.insertionBlocker(m, InsertionMethod.FAST_ROPE, listOf(qualified), null, airfield(3)))
        assertNotNull(TacticalCalculator.insertionBlocker(m, InsertionMethod.FAST_ROPE, listOf(base), pilot, airfield(3)))
        assertNull(TacticalCalculator.insertionBlocker(m, InsertionMethod.FAST_ROPE, listOf(qualified), pilot, airfield(3)))
    }

    @Test
    fun airAssaultSchoolAwardsTheBadge() {
        val school = mission(TacticalCalculator.AIR_ASSAULT_SCHOOL_ID)
        assertEquals(MissionScope.TRAINING, school.scope)
        val grad = base.copy(completedMissionIds = base.completedMissionIds + school.id)
        val (medal, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            school, grad, OperatorOutcomeType.TRAINING_PASSED, emptyList(), emptySet()
        )
        assertEquals("insig_air_assault", medal)
        // Airborne no longer hands out the Air Assault Badge
        val jumper = base.copy(completedMissionIds = base.completedMissionIds + "msn_night_qual")
        val (airborneMedal, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission("msn_night_qual"), jumper, OperatorOutcomeType.TRAINING_PASSED, emptyList(), emptySet()
        )
        assertTrue(airborneMedal != "insig_air_assault")
    }

    @Test
    fun combatDiverSchoolIsLongTdy() {
        val diver = mission("msn_combat_diver_school")
        assertTrue(diver.isTdy)
        // Session 41: eight weeks of game time
        assertEquals(com.example.domain.GameCalendar.realSecondsFor(56), diver.durationSeconds)
        assertFalse(mission("msn_sf_school").isTdy)
        val tdy = ActiveDeployment("d1", diver.id, listOf("a", "b"), 0L, 1000L, 100, 100)
        val field = ActiveDeployment("d2", "msn_sf_school", listOf("c"), 0L, 1000L, 100, 100)
        assertEquals(setOf("a", "b"), TacticalCalculator.tdyOperatorIdsFor(listOf(tdy, field)))
    }

    @Test
    fun aviatorsWearTheAviationBrigadePatch() {
        assertEquals(UnitPatchStyle.AVIATION_BRIGADE, UniformRegaliaRules.resolveUnitPatch(pilot, emptyList()))
        assertEquals(UnitPatchStyle.UNASSIGNED, UniformRegaliaRules.resolveUnitPatch(base, emptyList()))
        val hqPilot = pilot.copy(status = OperatorStatus.HQ_STAFF)
        assertEquals(UnitPatchStyle.SUPREME_HQ, UniformRegaliaRules.resolveUnitPatch(hqPilot, emptyList()))
        // A Tailor pick still wins
        val picked = pilot.copy(tailorOverrides = pilot.tailorOverrides.copy(unitPatch = UnitPatchStyle.DIVISION_2))
        assertEquals(UnitPatchStyle.DIVISION_2, UniformRegaliaRules.resolveUnitPatch(picked, emptyList()))
    }

    @Test
    fun abortOddsFollowRiskProgressAndInsertion() {
        fun odds(r: MissionRisk, p: Float = 0.5f, i: InsertionMethod = InsertionMethod.GROUND) =
            TacticalCalculator.abortCasualtyOddsPct(r, p, i)
        assertEquals(0.0 to 0.0, odds(MissionRisk.TRAINING))
        assertEquals(2.0 to 0.0, odds(MissionRisk.LOW))
        assertEquals(4.0 to 0.5, odds(MissionRisk.MODERATE))
        assertEquals(7.0 to 1.0, odds(MissionRisk.HIGH))
        assertEquals(10.0 to 2.0, odds(MissionRisk.EXTREME))
        assertTrue(odds(MissionRisk.HIGH, 0.9f).first > odds(MissionRisk.HIGH, 0.5f).first)
        assertTrue(odds(MissionRisk.HIGH, 0.1f).first < odds(MissionRisk.HIGH, 0.5f).first)
        assertTrue(odds(MissionRisk.HIGH, i = InsertionMethod.HELICOPTER).first < odds(MissionRisk.HIGH).first)
        assertTrue(odds(MissionRisk.HIGH, i = InsertionMethod.FAST_ROPE).second < odds(MissionRisk.HIGH).second)
    }
}
