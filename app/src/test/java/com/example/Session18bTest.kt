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

/** Session 18b: pilot uniforms (112, 113), aviator school bars (114), SERE (115), SF Aviation (111). */
class Session18bTest {
    private fun mission(id: String) = GameCatalog.missions.first { it.id == id }
    private val pilot = GameCatalog.createPostBasicRecruit("op_av", OperatorSpecialty.AVIATION, emptySet(), 0).copy(
        id = "op_av", xp = OperatorRank.CW2.xpRequired, authorizedTier = RankTier.WARRANT, warrantGrade = 2,
        completedMissionIds = setOf("msn_basic_qual", TacticalCalculator.WOCS_MISSION_ID, TacticalCalculator.FLIGHT_SCHOOL_MISSION_ID)
    )
    private val sfPilot = pilot.copy(id = "op_sfav",
        completedMissionIds = pilot.completedMissionIds + TacticalCalculator.SERE_SCHOOL_ID + TacticalCalculator.SF_AVIATION_SCHOOL_ID)

    @Test
    fun aviatorsStayOnTheirOwnSchoolTrack() {
        for (id in listOf("msn_ranger_school", "msn_eib_testing", "msn_nco_school", "msn_snco_school", "msn_ocs_school")) {
            assertFalse(id, OperatorSpecialty.AVIATION in mission(id).schoolRequirements!!.allowedSpecialties)
        }
        for (id in listOf("msn_air_assault_school", "msn_night_qual", "msn_sere_school", "msn_sf_aviation_school", "msn_rifle_qual")) {
            assertTrue(id, OperatorSpecialty.AVIATION in mission(id).schoolRequirements!!.allowedSpecialties)
        }
        // Ground soldiers can't take SF Aviation
        assertEquals(setOf(OperatorSpecialty.AVIATION), mission("msn_sf_aviation_school").schoolRequirements!!.allowedSpecialties)
    }

    @Test
    fun sereIsForAviatorsSfAndAirborneAndAwardsTheRibbon() {
        val sere = mission(TacticalCalculator.SERE_SCHOOL_ID)
        // Session 30: SERE 2 is for pilots and Airborne graduates (SF School now requires it)
        assertEquals(setOf("msn_flight_school", "msn_night_qual"), sere.schoolRequirements!!.prerequisiteAnyIds)
        val grad = pilot.copy(completedMissionIds = pilot.completedMissionIds + sere.id)
        val (medal, _) = TacticalCalculator.recommendedMedalForMissionDeed(sere, grad, OperatorOutcomeType.TRAINING_PASSED, emptyList(), emptySet())
        assertEquals("medal_sere", medal)
        assertEquals(AwardCategory.RIBBON, GameCatalog.medals.first { it.id == "medal_sere" }.category)
        // SF Aviation needs Flight School and SERE
        assertEquals(setOf("msn_flight_school", "msn_sere_school"), mission(TacticalCalculator.SF_AVIATION_SCHOOL_ID).schoolRequirements!!.prerequisiteAllIds)
    }

    @Test
    fun onlySfAviatorsFlySfMissions() {
        assertFalse(TacticalCalculator.isSfAviator(pilot))
        assertTrue(TacticalCalculator.isSfAviator(sfPilot))
        val sfMission = GameCatalog.missions.first { it.requiresSfTeam && InsertionMethod.HELICOPTER in it.allowedInsertions }
        val team = (1..4).map { i ->
            GameCatalog.createPostBasicRecruit("s$i", OperatorSpecialty.ASSAULT, emptySet(), 0)
                .copy(id = "s$i", completedMissionIds = setOf("msn_basic_qual", TacticalCalculator.SF_SCHOOL_ID, TacticalCalculator.AIR_ASSAULT_SCHOOL_ID))
        }
        val airfield = listOf(FacilityState(FacilityType.AIRFIELD, 5))
        assertNotNull(TacticalCalculator.insertionBlocker(sfMission, InsertionMethod.HELICOPTER, team, pilot, airfield))
        assertNull(TacticalCalculator.insertionBlocker(sfMission, InsertionMethod.HELICOPTER, team, sfPilot, airfield))
        assertNotNull(TacticalCalculator.insertionBlocker(sfMission, InsertionMethod.FAST_ROPE, team, pilot, airfield))
        assertNull(TacticalCalculator.insertionBlocker(sfMission, InsertionMethod.FAST_ROPE, team, sfPilot, airfield))
        // Regular missions still take any warrant aviator
        val normal = GameCatalog.missions.first { !TacticalCalculator.isSfOperation(it) && InsertionMethod.HELICOPTER in it.allowedInsertions }
        assertNull(TacticalCalculator.insertionBlocker(normal, InsertionMethod.HELICOPTER, team, pilot, airfield))
    }

    @Test
    fun sfAviatorsWearTheSoaPatchAndPilotsHaveNoFieldKit() {
        assertEquals(UnitPatchStyle.SOA, UniformRegaliaRules.resolveUnitPatch(sfPilot, emptyList()))
        assertEquals(UnitPatchStyle.AVIATION_BRIGADE, UniformRegaliaRules.resolveUnitPatch(pilot, emptyList()))
        assertFalse(WardrobeKit.FIELD.isAvailableFor(pilot))
        assertEquals(WardrobeKit.BRAVOS, TacticalCalculator.resolveEffectiveWardrobeKit(pilot, true, DutyUniformContext.COMBAT_DEPLOYMENT))
        assertEquals(WardrobeKit.BRAVOS, TacticalCalculator.resolveEffectiveWardrobeKit(pilot, true, DutyUniformContext.FIELD_SCHOOL))
        assertFalse(CamoPattern.FLIGHT_BLACK in CamoPattern.SELECTABLE)
    }
}
