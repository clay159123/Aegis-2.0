package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.GearAutoDistributor
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Patch 2: school locks, rank locks on SF regalia, aviators carry no field kit, flight hours field. */
class PatchTwoTest {
    private fun sergeant(specialty: OperatorSpecialty = OperatorSpecialty.SIGNALS): Operator =
        GameCatalog.createPostBasicRecruit("op_p2_${specialty.name}", specialty, emptySet(), 0).copy(
            xp = OperatorRank.SGT.xpRequired,
            authorizedTier = RankTier.NCO,
            baseTech = 30, baseResilience = 30, baseFirepower = 30, baseStealth = 30,
            completedMissionIds = setOf("msn_basic_qual"),
            highestRiskSurvived = MissionRisk.HIGH,
            missionsCompleted = 4
        )

    private fun warrant(specialty: OperatorSpecialty) = sergeant(specialty).copy(
        xp = OperatorRank.forWarrantGrade(1).xpRequired,
        authorizedTier = RankTier.WARRANT,
        warrantGrade = 1,
        completedMissionIds = setOf("msn_basic_qual", TacticalCalculator.WOCS_MISSION_ID)
    )

    private fun mission(id: String) = GameCatalog.missions.first { it.id == id }

    @Test
    fun warrantOfficersSkipEnlistedLeadershipSchools() {
        val wo = warrant(OperatorSpecialty.SIGNALS)
        val nco = TacticalCalculator.canOperatorDeployOnMission(wo, mission("msn_nco_school"), emptySet())
        assertFalse(nco.eligible)
        assertTrue(nco.missingRequirements.any { it.startsWith("Enlisted leadership school") })
        val slc = TacticalCalculator.canOperatorDeployOnMission(wo, mission("msn_snco_school"), emptySet())
        assertTrue(slc.missingRequirements.any { it.startsWith("Enlisted leadership school") })
        // A sergeant isn't blocked by that rule
        val sgt = TacticalCalculator.canOperatorDeployOnMission(sergeant(), mission("msn_nco_school"), emptySet())
        assertFalse(sgt.missingRequirements.any { it.startsWith("Enlisted leadership school") })
    }

    @Test
    fun sfRegaliaHasNoNcoLock() {
        listOf("cord_sf_emerald", "insig_sf_tab", "insig_mff_wings", "insig_combat_diver").forEach { id ->
            val item = GameCatalog.medals.firstOrNull { it.id == id }
            if (item != null) assertEquals("$id should have no tier lock", RankTier.SOLDIER, item.minTierRequired)
        }
        assertEquals(RankTier.SOLDIER, HeadgearStyle.EMERALD_BERET.requiredTier)
    }

    @Test
    fun diverAndFreefallOpenAtSpc() {
        listOf("msn_combat_diver_school", "msn_mff_school").forEach { id ->
            val m = mission(id).schoolRequirements!!
            assertEquals(RankTier.SOLDIER, m.minRankTier)
            assertEquals(OperatorRank.SPC, m.minRank)
        }
    }

    @Test
    fun aviatorsCarryNoFieldKit() {
        assertTrue(GearAutoDistributor.carriesFieldKit(sergeant(OperatorSpecialty.ASSAULT)))
        assertFalse(GearAutoDistributor.carriesFieldKit(warrant(OperatorSpecialty.AVIATION)))
    }

    @Test
    fun flightHoursStartAtZero() {
        assertEquals(0, sergeant().flightHoursLogged)
        assertTrue(TacticalCalculator.FLIGHT_HOURS_PER_SESSION > TacticalCalculator.FLIGHT_HOURS_PER_SORTIE)
    }
}
