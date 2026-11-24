package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.ClandestineRules
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session 25: Deputy rank cap (150) and no language school for HQ staff (152). */
class Session25Test {
    private val base = GameCatalog.createPostBasicRecruit("op_s25", OperatorSpecialty.ASSAULT, emptySet(), 0)
    private val hq = listOf(FacilityState(FacilityType.COMMAND_HQ, 10), FacilityState(FacilityType.AIRFIELD, 1))

    @After fun reset() { HqChain.commanderRank = null }

    @Test
    fun deputyStaysOneRankBelowTheCo() {
        val dcg = base.copy(id = "op_dcg", xp = OperatorRank.GEN.xpRequired, authorizedTier = RankTier.FLAG_OFFICER, flagGrade = 4,
            status = OperatorStatus.HQ_STAFF, hqRole = HqStaffRole.DEPUTY_COMMANDER)
        HqChain.commanderRank = OperatorRank.MG
        assertEquals(OperatorRank.BG, dcg.rank)
        assertTrue(dcg.isAtHqRankCap)
        assertNull(TacticalCalculator.evaluatePromotionBoard(dcg, listOf(dcg), emptyList(), emptyList(), hq).nextRank)
        HqChain.commanderRank = OperatorRank.GEN
        assertEquals(OperatorRank.LTG, dcg.rank)
        HqChain.commanderRank = null
        assertEquals(OperatorRank.LTG, dcg.rank)
        // Leaving the billet restores the earned rank
        assertEquals(OperatorRank.GEN, dcg.copy(status = OperatorStatus.READY, hqRole = null).rank)
    }

    @Test
    fun newStarOnlyFillsDeputyWhenCoOutranksBg() {
        val col = base.copy(id = "op_col", xp = OperatorRank.COL.xpRequired, authorizedTier = RankTier.OFFICER)
        val co = base.copy(id = "op_co", xp = OperatorRank.BG.xpRequired, authorizedTier = RankTier.FLAG_OFFICER, flagGrade = 1,
            status = OperatorStatus.HQ_STAFF, hqRole = HqStaffRole.CO_COMMANDING_OFFICER)
        HqChain.commanderRank = OperatorRank.BG
        assertNull(TacticalCalculator.openGeneralBillet(col, listOf(col, co), hq))
        HqChain.commanderRank = OperatorRank.MG
        assertEquals(HqStaffRole.DEPUTY_COMMANDER, TacticalCalculator.openGeneralBillet(col, listOf(col, co), hq))
    }

    @Test
    fun hqStaffSkipLanguageSchool() {
        val school = GameCatalog.missions.first { it.id == ClandestineRules.LANGUAGE_SCHOOL_ID }
        val staff = base.copy(status = OperatorStatus.HQ_STAFF, hqRole = HqStaffRole.S2_INTEL_OVERWATCH)
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(staff, school, emptySet(), facilities = hq)
            .missingRequirements.contains("HQ staff don't need language training"))
    }
}
