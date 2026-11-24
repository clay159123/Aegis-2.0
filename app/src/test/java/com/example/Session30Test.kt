package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session 30: SERE levels, SF tiers and the 75% team rule. */
class Session30Test {
    private fun m(id: String) = GameCatalog.missions.first { it.id == id }
    private val base = GameCatalog.createPostBasicRecruit("op_s30", OperatorSpecialty.ASSAULT, emptySet(), 0)
        .copy(xp = OperatorRank.SGT.xpRequired, authorizedTier = RankTier.NCO, baseFirepower = 30, baseStealth = 30, baseResilience = 30, baseTech = 30)
    private val sf1 = base.copy(completedMissionIds = setOf("msn_basic_qual", "msn_night_qual", "msn_sere_school", "msn_sf_school"))
    private val sf2 = sf1.copy(completedMissionIds = sf1.completedMissionIds + TacticalCalculator.SERE3_SCHOOL_ID, sfMissionsSucceeded = 3)
    private val sf3 = sf2.copy(
        completedMissionIds = sf2.completedMissionIds + TacticalCalculator.MFF_SCHOOL_ID + TacticalCalculator.COMBAT_DIVER_SCHOOL_ID,
        languageRegions = setOf(TheaterRegion.SAHR_EXPANSE), sfMissionsSucceeded = 8, sfHighMissionsSucceeded = 5
    )

    @Test
    fun sereLevelsAndSfEntry() {
        assertTrue("msn_sere_school" in m("msn_sf_school").schoolRequirements!!.prerequisiteAllIds)
        assertEquals(setOf("msn_flight_school", "msn_night_qual"), m("msn_sere_school").schoolRequirements!!.prerequisiteAnyIds)
        assertEquals(setOf("msn_sf_school", "msn_sere_school"), m(TacticalCalculator.SERE3_SCHOOL_ID).schoolRequirements!!.prerequisiteAllIds)
        assertEquals(1, TacticalCalculator.sereLevel(base.copy(completedMissionIds = setOf("msn_basic_qual"))))
        assertEquals(2, TacticalCalculator.sereLevel(sf1))
        assertEquals(3, TacticalCalculator.sereLevel(sf2))
        val noSere = base.copy(completedMissionIds = setOf("msn_basic_qual", "msn_night_qual"))
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(noSere, m("msn_sf_school"), emptySet()).eligible.not())
    }

    @Test
    fun tiersFollowSchoolsMissionsAndRank() {
        assertEquals(0, TacticalCalculator.sfTier(base))
        assertEquals(1, TacticalCalculator.sfTier(sf1))
        assertEquals(1, TacticalCalculator.sfTier(sf2.copy(sfMissionsSucceeded = 2)))
        assertEquals(2, TacticalCalculator.sfTier(sf2))
        assertEquals(3, TacticalCalculator.sfTier(sf3))
        assertEquals(2, TacticalCalculator.sfTier(sf3.copy(languageRegions = emptySet())))
        assertEquals(3, TacticalCalculator.sfTier(sf3.copy(languageRegions = emptySet(), nativeRegion = TheaterRegion.MERIDIAN_CITY)))
        assertEquals(2, TacticalCalculator.sfTier(sf3.copy(sfHighMissionsSucceeded = 4)))
        assertEquals(2, TacticalCalculator.sfTier(sf3.copy(xp = OperatorRank.SPC.xpRequired, authorizedTier = RankTier.SOLDIER)))
        assertEquals("18B SF WPN", sf1.mosTag)
        assertEquals("18E SF COMMO", sf1.copy(specialty = OperatorSpecialty.SIGNALS).mosTag)
    }

    @Test
    fun missionsNeedTheirTierFromThreeQuartersOfTheTeam() {
        val extreme = GameCatalog.missions.first { TacticalCalculator.isSfOperation(it) && it.risk == MissionRisk.EXTREME && !it.isClandestine }
        val high = GameCatalog.missions.first { TacticalCalculator.isSfOperation(it) && it.risk == MissionRisk.HIGH && !it.isClandestine }
        val low = GameCatalog.missions.first { TacticalCalculator.isSfOperation(it) && it.risk == MissionRisk.LOW }
        assertEquals(3, TacticalCalculator.requiredSfTier(extreme))
        assertEquals(2, TacticalCalculator.requiredSfTier(high))
        assertEquals(1, TacticalCalculator.requiredSfTier(low))
        fun team(vararg ops: Operator) = ops.mapIndexed { i, o -> o.copy(id = "t$i") }
        val squad = listOf(TacticalSquad("oda", "ODA 1", (0..5).map { "t$it" }, "t0", isSpecialForces = true))
        // 6 soldiers: 5 needed (75% rounded up)
        assertEquals(5, TacticalCalculator.sfTierQuota(6))
        assertNull(TacticalCalculator.teamBlocker(extreme, team(sf3, sf3, sf3, sf3, sf3, sf1), squad))
        assertTrue(TacticalCalculator.teamBlocker(extreme, team(sf3, sf3, sf3, sf3, sf2, sf1), squad)!!.startsWith("Needs SF3"))
        assertNull(TacticalCalculator.teamBlocker(high, team(sf2, sf2, sf2, sf1), squad))
        assertNull(TacticalCalculator.teamBlocker(low, team(sf1, sf1, sf1, sf1), squad))
        assertEquals(2, TacticalCalculator.groupSfTier(team(sf3, sf3, sf2, sf2)))
        assertEquals(3, TacticalCalculator.squadSfTier(squad[0], team(sf3, sf3, sf3, sf3, sf3, sf1)))
    }
}
