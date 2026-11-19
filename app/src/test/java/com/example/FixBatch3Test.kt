package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fix batch: school retries (101), school difficulty ladder (106), HQ rank caps (107), platoon finales (108). */
class FixBatch3Test {
    private val base = GameCatalog.createPostBasicRecruit("op_fb3", OperatorSpecialty.ASSAULT, emptySet(), 0)
    private fun mission(id: String) = GameCatalog.missions.first { it.id == id }

    @Test
    fun sfIsHarderThanEibAtEveryRangeLevel() {
        val strong = base.copy(
            xp = OperatorRank.SFC.xpRequired, authorizedTier = RankTier.SNCO,
            baseFirepower = 40, baseStealth = 40, baseTech = 40, baseResilience = 40
        )
        for (lv in 0..10) {
            val fac = listOf(FacilityState(FacilityType.TRAINING_RANGE, lv))
            fun odds(id: String) = TacticalCalculator.schoolPassOddsPct(mission(id), strong, emptyList(), null, fac)
            assertTrue("Ranger > SF at Range $lv", odds("msn_ranger_school") > odds("msn_sf_school"))
            assertTrue("EIB > SF at Range $lv", odds("msn_eib_testing") > odds("msn_sf_school"))
            assertTrue("Diver >= SF at Range $lv", odds("msn_combat_diver_school") >= odds("msn_sf_school"))
        }
    }

    @Test
    fun everySchoolAllowsRetriesAtACost() {
        GameCatalog.missions.filter { it.scope == MissionScope.TRAINING && it.schoolRequirements?.maxAttempts != null }.forEach { m ->
            val limit = m.schoolRequirements!!.maxAttempts!!
            val tired = base.copy(schoolAttempts = mapOf(m.id to limit))
            assertFalse(m.id, TacticalCalculator.canOperatorDeployOnMission(tired, m, emptySet()).missingRequirements.any { it.startsWith("Washed out") })
            assertTrue(m.id, TacticalCalculator.schoolRetrySurcharge(m, listOf(tired), 1.0) > 0)
        }
    }

    @Test
    fun hqBilletsCapRanks() {
        fun seated(role: HqStaffRole, rank: OperatorRank) = base.copy(
            xp = OperatorRank.GEN.xpRequired, authorizedTier = rank.tier,
            warrantGrade = rank.warrantGrade, status = OperatorStatus.HQ_STAFF, hqRole = role
        )
        val s1 = seated(HqStaffRole.S1_PERSONNEL_HONORS, OperatorRank.COL)
        assertEquals(OperatorRank.MAJ, s1.rank)
        assertTrue(s1.isAtHqRankCap)
        assertFalse(s1.isReadyForNextCategoryPromotion)
        assertEquals(OperatorRank.COL, seated(HqStaffRole.XO_CHIEF_OF_STAFF, OperatorRank.COL).rank)
        assertEquals(OperatorRank.CW4, seated(HqStaffRole.S2_INTEL_OVERWATCH, OperatorRank.CW5).rank)
        assertEquals(OperatorRank.SGM, HqStaffRole.S3_OPS_DIRECTOR.rankCapFor(OperatorRank.SGM))
        assertNull(HqStaffRole.CO_COMMANDING_OFFICER.rankCapFor(OperatorRank.GEN))
        // Back in the field, the earned rank shows again
        assertEquals(s1.earnedRank, s1.copy(status = OperatorStatus.READY, hqRole = null).rank)
        // The S-3 is enlisted only
        val officer = base.copy(xp = OperatorRank.CPT.xpRequired, authorizedTier = RankTier.OFFICER, missionsCompleted = 20,
            completedMissionIds = base.completedMissionIds + "msn_ranger_school")
        assertTrue(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.S3_OPS_DIRECTOR, officer).missingRequirements.any { it.startsWith("Enlisted only") })
    }

    @Test
    fun lateCampaignFinalesArePlatoonOps() {
        listOf("camp_meridian_4", "camp_karst_4", "camp_talon_5", "camp_ironhold_5").forEach { id ->
            val m = GameCatalog.campaignMissionsById.getValue(id)
            assertEquals(id, 2, m.requiredSquads)
            assertEquals(12, m.maxOperators)
        }
        assertEquals(1, GameCatalog.campaignMissionsById.getValue("camp_meridian_1").requiredSquads)
    }
}
