package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session 23: HQ staff fixes (items 125, 126, 129, 135, 144). */
class Session23HqStaffTest {
    private val base = GameCatalog.createPostBasicRecruit("op_s23", OperatorSpecialty.ASSAULT, emptySet(), 0)
    private fun mission(id: String) = GameCatalog.missions.first { it.id == id }
    private fun hq(level: Int) = listOf(FacilityState(FacilityType.COMMAND_HQ, level), FacilityState(FacilityType.AIRFIELD, 1))
    private fun seated(role: HqStaffRole, rank: OperatorRank, id: String = "op_${role.name}") = base.copy(
        id = id, xp = rank.xpRequired, authorizedTier = rank.tier, warrantGrade = rank.warrantGrade,
        flagGrade = rank.flagGrade, status = OperatorStatus.HQ_STAFF, hqRole = role, missionsCompleted = 30
    )

    @Test
    fun hqSeatsEveryUnlockedBillet() {
        // Item 144: at top HQ level all 11 billets can be filled (the old ladder count stopped at 9)
        val top = hq(10)
        assertEquals(HqStaffRole.entries.size, TacticalCalculator.hqSeatCount(top))
        assertTrue(HqStaffRole.CCWO_COMMAND_CHIEF_WARRANT in TacticalCalculator.unlockedHqRoles(top))
        assertTrue(HqStaffRole.SOF_SPECIAL_OPERATIONS in TacticalCalculator.unlockedHqRoles(top))
        assertEquals(TacticalCalculator.unlockedHqRoles(hq(1)).size, TacticalCalculator.hqSeatCount(hq(1)))
    }

    @Test
    fun seatedStaffBelowCapCanPromote() {
        // Item 126: every capped billet offers the next rank until its cap
        listOf(
            HqStaffRole.S1_PERSONNEL_HONORS to OperatorRank.SGT,
            HqStaffRole.S2_INTEL_OVERWATCH to OperatorRank.LT1,
            HqStaffRole.S3_OPS_DIRECTOR to OperatorRank.MSG,
            HqStaffRole.S4_LOGISTICS_MEDEVAC to OperatorRank.SFC,
            HqStaffRole.XO_CHIEF_OF_STAFF to OperatorRank.CPT,
            HqStaffRole.DEPUTY_COMMANDER to OperatorRank.LTC
        ).forEach { (role, rank) ->
            val op = seated(role, rank)
            val board = TacticalCalculator.evaluatePromotionBoard(op, listOf(op), emptyList(), emptyList(), hq(10))
            assertNotNull(role.shortCode, board.nextRank)
            assertEquals(role.shortCode, OperatorRank.nextRank(rank), op.promotedTo(board.nextRank!!).rank)
        }
        assertTrue(TacticalCalculator.HQ_STAFF_XP_SHARE >= 0.5)
    }

    @Test
    fun cappedAndEnlistedBilletsExplainTheBlock() {
        val s3 = seated(HqStaffRole.S3_OPS_DIRECTOR, OperatorRank.SGM)
        val s3Board = TacticalCalculator.evaluatePromotionBoard(s3, listOf(s3), emptyList(), emptyList(), hq(10))
        assertNull(s3Board.nextRank)
        assertTrue(s3Board.blockedReason!!.contains("S-3"))
        val csm = seated(HqStaffRole.CSM_COMMAND_SERGEANT_MAJOR, OperatorRank.SGM)
        val csmBoard = TacticalCalculator.evaluatePromotionBoard(csm, listOf(csm), emptyList(), emptyList(), hq(10))
        assertNull(csmBoard.nextRank)
        assertTrue(csmBoard.blockedReason!!.contains("enlisted billet"))
    }

    @Test
    fun enlistedBilletsCantApplyForCommission() {
        // Item 125
        val s3 = seated(HqStaffRole.S3_OPS_DIRECTOR, OperatorRank.MSG)
        listOf(TacticalCalculator.OCS_MISSION_ID, TacticalCalculator.WOCS_MISSION_ID).forEach { id ->
            val result = TacticalCalculator.canOperatorDeployOnMission(s3, mission(id), emptySet(), facilities = hq(10))
            assertTrue(id, result.missingRequirements.any { it.startsWith("Seated in an enlisted billet") })
        }
        val s1 = seated(HqStaffRole.S1_PERSONNEL_HONORS, OperatorRank.SSG)
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(s1, mission(TacticalCalculator.OCS_MISSION_ID), emptySet(), facilities = hq(10))
            .missingRequirements.none { it.startsWith("Seated in an enlisted billet") })
    }

    @Test
    fun ocsGraduatesAwaitingCommissionAreFound() {
        val sgm = seated(HqStaffRole.XO_CHIEF_OF_STAFF, OperatorRank.SGM).copy(
            completedMissionIds = base.completedMissionIds + TacticalCalculator.OCS_MISSION_ID
        )
        assertTrue(TacticalCalculator.awaitingOcsCommission(sgm))
        assertEquals(OperatorRank.LT2, sgm.promotedTo(OperatorRank.LT2).rank)
        // Not while seated in an enlisted billet, and not once commissioned
        assertTrue(!TacticalCalculator.awaitingOcsCommission(sgm.copy(hqRole = HqStaffRole.CSM_COMMAND_SERGEANT_MAJOR)))
        assertTrue(!TacticalCalculator.awaitingOcsCommission(sgm.promotedTo(OperatorRank.LT2)))
    }

    @Test
    fun hqPilotsDontLogFlightHours() {
        // Item 129
        val pilot = seated(HqStaffRole.AIR_OPS_OFFICER, OperatorRank.CW2).copy(specialty = OperatorSpecialty.AVIATION, flightSkill = 30)
        val result = TacticalCalculator.canOperatorDeployOnMission(pilot, mission(TacticalCalculator.FLIGHT_HOURS_MISSION_ID), emptySet(), facilities = hq(10))
        assertTrue(result.missingRequirements.contains("HQ staff don't log flight hours"))
    }

    @Test
    fun generalsServeOnHqOnly() {
        // Item 135: the general board needs the COL seated as CO or Deputy; a field general can't take field missions
        val col = base.copy(id = "op_col", xp = OperatorRank.COL.xpRequired, authorizedTier = RankTier.OFFICER, missionsCompleted = 30)
        val board = TacticalCalculator.evaluatePromotionBoard(col, listOf(col), emptyList(), emptyList(), hq(10))
        assertTrue(board.meritChecks.any { it.id == "billet_flag" && !it.satisfied })
        assertNull(TacticalCalculator.seatedGeneralBillet(col))
        val coCol = col.copy(status = OperatorStatus.HQ_STAFF, hqRole = HqStaffRole.CO_COMMANDING_OFFICER)
        assertEquals(HqStaffRole.CO_COMMANDING_OFFICER, TacticalCalculator.seatedGeneralBillet(coCol))
        // The XO tops out at COL, so no board from there
        val xo = seated(HqStaffRole.XO_CHIEF_OF_STAFF, OperatorRank.COL, "op_xo")
        assertNull(TacticalCalculator.evaluatePromotionBoard(xo, listOf(xo), emptyList(), emptyList(), hq(10)).nextRank)
        assertNull(TacticalCalculator.seatedGeneralBillet(xo))
        val fieldGeneral = base.copy(id = "op_gen", xp = OperatorRank.BG.xpRequired, authorizedTier = RankTier.FLAG_OFFICER, flagGrade = 1)
        val combat = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT }
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(fieldGeneral, combat, emptySet(), facilities = hq(10))
            .missingRequirements.contains("Generals serve on HQ staff only"))
    }
}
