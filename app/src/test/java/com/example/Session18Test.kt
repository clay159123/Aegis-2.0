package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.PromotionBoardEvaluator
import com.example.domain.SquadCohesionRules
import com.example.domain.TacticalCalculator
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session 18: SF squad (58, 68, 87), SOF billet (102), Command Sergeant Major billet (110). */
class Session18Test {
    private fun soldier(id: String, rank: OperatorRank = OperatorRank.SGT, sf: Boolean = true) =
        GameCatalog.createPostBasicRecruit(id, OperatorSpecialty.ASSAULT, emptySet(), 0).copy(
            id = id, xp = rank.xpRequired, authorizedTier = rank.tier, missionsCompleted = 6,
            completedMissionIds = setOf("msn_basic_qual") + if (sf) setOf(TacticalCalculator.SF_SCHOOL_ID) else emptySet()
        )
    private val da = GameCatalog.missions.first { it.id == "msn_sf_da_hostage" }

    @Test
    fun sfSquadWearsTheArrowheadAndASterileFieldUniform() {
        val a = soldier("a")
        val sfSquad = TacticalSquad("sq_sf", "ODA", listOf("a"), "a", isSpecialForces = true)
        assertEquals(UnitPatchStyle.SPECIAL_FORCES, UniformRegaliaRules.resolveUnitPatch(a, listOf(sfSquad)))
        assertTrue(UniformRegaliaRules.isSfSquadMember(a, listOf(sfSquad)))
        val normal = sfSquad.copy(isSpecialForces = false, standardPatch = UnitPatchStyle.DIVISION_3)
        assertEquals(UnitPatchStyle.DIVISION_3, UniformRegaliaRules.resolveUnitPatch(a, listOf(normal)))
        assertFalse(UniformRegaliaRules.isSfSquadMember(a, listOf(normal)))
    }

    @Test
    fun sfMissionsTakeOnlySfQualifiedSoldiers() {
        val plain = soldier("p", sf = false)
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(plain, da, emptySet()).eligible)
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(soldier("q"), da, emptySet()).eligible)
    }

    @Test
    fun fullSfSquadGetsFullBonusAndFillInsGetTheCut() {
        val six = (1..6).map { soldier("s$it").copy(baseFirepower = 20) }
        val squad = TacticalSquad("sq_sf", "ODA", six.map { it.id }, "s1", cohesionXp = 800, isSpecialForces = true)
        val full = SquadCohesionRules.evaluateActiveBonus(six, listOf(squad))!!
        assertTrue(full.fullStrength)
        assertFalse(full.mixedSquads)
        // Four of the squad plus an unassigned SF soldier: filled in, so the mixed cut applies
        val fourOfSix = squad.copy(memberIds = six.take(4).map { it.id })
        val filled = SquadCohesionRules.evaluateActiveBonus(six.take(4) + soldier("x"), listOf(fourOfSix))!!
        assertFalse(filled.fullStrength)
        assertTrue(filled.mixedSquads)
    }

    @Test
    fun sofBilletNeedsSfAndHelpsSfWork() {
        val role = HqStaffRole.SOF_SPECIAL_OPERATIONS
        assertTrue(TacticalCalculator.evaluateHqRoleEligibility(role, soldier("a", OperatorRank.SFC)).eligible)
        assertFalse(TacticalCalculator.evaluateHqRoleEligibility(role, soldier("b", OperatorRank.SSG)).eligible)
        assertFalse(TacticalCalculator.evaluateHqRoleEligibility(role, soldier("c", OperatorRank.SFC, sf = false)).eligible)
        assertEquals(OperatorRank.MSG, role.rankCapFor(OperatorRank.SGM))
        assertEquals(OperatorRank.MAJ, role.rankCapFor(OperatorRank.COL))
        val sofOfficer = soldier("o", OperatorRank.SFC).copy(status = OperatorStatus.HQ_STAFF, hqRole = role)
        val summary = TacticalCalculator.summarizeHqStaff(listOf(sofOfficer))
        assertEquals(role.sfMissionPassBonusPct, summary.sfMissionPassBonusPct)
        assertTrue(TacticalCalculator.isSfOperation(da))
        // SF school odds rise for a weak candidate with the SOF officer seated
        val school = GameCatalog.missions.first { it.id == TacticalCalculator.SF_SCHOOL_ID }
        val weak = soldier("w", OperatorRank.SPC, sf = false)
        assertTrue(
            TacticalCalculator.schoolPassOddsPct(school, weak, emptyList(), summary) >
                TacticalCalculator.schoolPassOddsPct(school, weak, emptyList(), TacticalCalculator.HqStaffSummary())
        )
    }

    @Test
    fun commandSergeantMajorIsEnlistedUncappedAndWearsCsm() {
        val role = HqStaffRole.CSM_COMMAND_SERGEANT_MAJOR
        assertTrue(role.isEnlistedOnly)
        assertNull(role.rankCapFor(OperatorRank.SGM))
        val sgm = soldier("c", OperatorRank.SGM)
        assertTrue(TacticalCalculator.evaluateHqRoleEligibility(role, sgm).eligible)
        assertFalse(TacticalCalculator.evaluateHqRoleEligibility(role, soldier("m", OperatorRank.MSG)).eligible)
        val seated = sgm.copy(status = OperatorStatus.HQ_STAFF, hqRole = role)
        assertEquals(OperatorRank.CSM, seated.rank)
        assertEquals(OperatorRank.SGM, seated.earnedRank)
        assertEquals(OperatorRank.SGM, sgm.rank)
        // CSM never comes from XP and sits level with SGM
        assertEquals(OperatorRank.SGM, OperatorRank.forXp(OperatorRank.SGM.xpRequired + 300, RankTier.SNCO))
        assertEquals(OperatorRank.SGM.seniorityIndex, OperatorRank.CSM.seniorityIndex)
        // Enlisted boards cost less with the CSM seated
        val sgt = soldier("s", OperatorRank.SGT)
        val without = PromotionBoardEvaluator.evaluate(sgt, listOf(sgt), emptyList(), emptyList(), emptyList())
        val with = PromotionBoardEvaluator.evaluate(sgt, listOf(sgt, seated), emptyList(), emptyList(), emptyList())
        assertTrue(with.creditCost < without.creditCost)
        assertTrue(TacticalCalculator.summarizeHqStaff(listOf(seated)).cohesionGainBonusPct > 0)
    }
}
