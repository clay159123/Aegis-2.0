package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.PowRules
import com.example.domain.TacticalCalculator
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import com.example.ui.components.BASE_PLOTS
import com.example.ui.components.PENDANT_ART_IDS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Session32Test {
    private val base = GameCatalog.createPostBasicRecruit("op_s32", OperatorSpecialty.ASSAULT, emptySet(), 0)
    private fun mission(id: String) = GameCatalog.missions.first { it.id == id }
    private fun fac(type: FacilityType, lv: Int) = FacilityType.entries.map { FacilityState(it, if (it == type) lv else it.startLevel) }

    @Test
    fun everySchoolSitsInExactlyOneCategory() {
        val schools = GameCatalog.missions.filter { it.scope == MissionScope.TRAINING }.map { it.id }
        val placed = GameCatalog.schoolCategories.flatMap { it.missionIds }
        assertEquals(placed.size, placed.toSet().size)
        assertEquals(schools.toSet(), placed.toSet())
    }

    @Test
    fun awardPrecedenceIsUniqueAndInArmyOrder() {
        val orders = GameCatalog.medals.map { it.precedenceOrder }
        assertEquals(orders.size, orders.toSet().size)
        fun p(id: String) = GameCatalog.medals.first { it.id == id }.precedenceOrder
        assertTrue(p("medal_meritorious") < p("medal_air_medal"))
        assertTrue(p("medal_air_medal") < p("medal_army_commendation"))
        assertTrue(p("medal_nco_dev") < p("medal_basic_training"))
        assertTrue(p("medal_basic_training") < p("medal_overseas_service"))
        assertTrue(p("medal_un") < p("medal_nato"))
        // The ribbon rack comes first, then cords, then badges and tabs
        val rack = GameCatalog.medals.filter { it.category == AwardCategory.MEDAL || it.category == AwardCategory.RIBBON }
        val cords = GameCatalog.medals.filter { it.category == AwardCategory.SHOULDER_CORD }
        val insignia = GameCatalog.medals.filter { it.category == AwardCategory.SCHOOL_INSIGNIA }
        assertTrue(rack.maxOf { it.precedenceOrder } < cords.minOf { it.precedenceOrder })
        assertTrue(cords.maxOf { it.precedenceOrder } < insignia.minOf { it.precedenceOrder })
        assertTrue(p("insig_sf_tab") < p("insig_cib"))
    }

    @Test
    fun noTwoAwardsShareARibbonOrPendant() {
        val stripes = GameCatalog.medals.map { it.ribbonStripeHexes }
        assertEquals(stripes.size, stripes.toSet().size)
        listOf(
            "medal_expeditionary" to "medal_nco_dev", "medal_nato" to "medal_un", "insig_cib" to "insig_eib_rifle",
            "insig_combat_medic" to "insig_cmb_medic", "insig_senior_aviator_wings" to "cord_aviation"
        ).forEach { (a, b) ->
            assertNotEquals("$a vs $b", GameCatalog.medals.first { it.id == a }.ribbonStripeHexes, GameCatalog.medals.first { it.id == b }.ribbonStripeHexes)
        }
        GameCatalog.medals.filter { it.category == AwardCategory.MEDAL || it.category == AwardCategory.RIBBON }
            .forEach { assertTrue("${it.id} has its own pendant", it.id in PENDANT_ART_IDS) }
    }

    @Test
    fun rangeAndAirborneRangerCenterSplitTheSchools() {
        assertEquals(setOf(FacilityType.AIRBORNE_RANGER_CENTER), BASE_PLOTS.mapNotNull { it.type }.filter { it == FacilityType.AIRBORNE_RANGER_CENTER }.toSet())
        assertEquals(0, FacilityType.AIRBORNE_RANGER_CENTER.startLevel)
        val range5 = fac(FacilityType.TRAINING_RANGE, 5)
        val center5 = fac(FacilityType.AIRBORNE_RANGER_CENTER, 5)
        for (id in listOf("msn_basic_qual", "msn_rifle_qual", "msn_eib_testing", "msn_sniper_school")) {
            assertEquals(id, 10, TacticalCalculator.rangeSchoolBonusPct(mission(id), range5))
            assertEquals(id, 0, TacticalCalculator.airborneRangerSchoolBonusPct(mission(id), center5))
        }
        for (id in listOf("msn_night_qual", "msn_air_assault_school", "msn_pathfinder_school", "msn_ranger_school", "msn_mff_school")) {
            assertEquals(id, 0, TacticalCalculator.rangeSchoolBonusPct(mission(id), range5))
            assertEquals(id, 10, TacticalCalculator.airborneRangerSchoolBonusPct(mission(id), center5))
        }
        // No general bonus and nothing for SF from the Range
        for (id in listOf("msn_sf_school", "msn_signals_school", "msn_language_school", "msn_sere_school")) {
            assertEquals(id, 0, TacticalCalculator.rangeSchoolBonusPct(mission(id), range5))
            assertEquals(id, 0, TacticalCalculator.airborneRangerSchoolBonusPct(mission(id), center5))
        }
    }

    @Test
    fun sere3GraduatesAreHarderToCapture() {
        val m = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT && it.risk == MissionRisk.HIGH }
        val plain = base.copy(completedMissionIds = setOf("msn_basic_qual"))
        val sere2 = plain.copy(completedMissionIds = plain.completedMissionIds + "msn_sere_school")
        val sere3 = sere2.copy(completedMissionIds = sere2.completedMissionIds + TacticalCalculator.SERE3_SCHOOL_ID)
        val p0 = PowRules.captureChancePct(m, plain, missionSucceeded = false)
        assertEquals(p0 * 0.6, PowRules.captureChancePct(m, sere2, missionSucceeded = false), 0.0001)
        assertEquals(p0 * 0.4, PowRules.captureChancePct(m, sere3, missionSucceeded = false), 0.0001)
    }

    @Test
    fun tailorCanOverrideTheEnvironmentBadge() {
        val all = TacticalCalculator.ENVIRONMENT_BADGE_IDS
        val soldier = base.copy(completedMissionIds = setOf("msn_basic_qual"))
        val auto = UniformRegaliaRules.environmentBadgeFor(soldier, all)!!
        val other = all.first { it != auto.id }
        val chosen = soldier.copy(tailorOverrides = soldier.tailorOverrides.copy(environmentSlot = other))
        assertEquals(other, UniformRegaliaRules.environmentBadgeFor(chosen, all)!!.id)
        val none = soldier.copy(tailorOverrides = soldier.tailorOverrides.copy(environmentSlot = UniformTailorOverrides.SLOT_NONE))
        assertNull(UniformRegaliaRules.environmentBadgeFor(none, all))
        // A pick the soldier doesn't own falls back to Auto
        val fallback = UniformRegaliaRules.environmentBadgeFor(chosen, all - other)
        assertTrue(fallback != null && fallback.id != other)
    }
}
