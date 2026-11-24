package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.ClandestineRules
import com.example.domain.MosEffects
import com.example.domain.TacticalCalculator
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Session36Test {
    private val base = GameCatalog.createPostBasicRecruit("op_s36", OperatorSpecialty.ASSAULT, emptySet(), 0)
        .copy(completedMissionIds = setOf("msn_basic_qual"))
    private fun mos(m: OperatorSpecialty, id: String = "op_$m") = base.copy(id = id, specialty = m)

    @Test
    fun eachMosAddsItsSignatureOncePerTeam() {
        val assault = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT && MosEffects.isAssaultMission(it) }
        assertEquals(2, MosEffects.passBonusPct(assault, listOf(mos(OperatorSpecialty.ASSAULT), mos(OperatorSpecialty.ASSAULT, "op_b"))))
        assertEquals(0, MosEffects.passBonusPct(assault, listOf(mos(OperatorSpecialty.MEDIC))))
        assertEquals(0.8, MosEffects.captureFactor(listOf(mos(OperatorSpecialty.RECON))), 1e-9)
        assertEquals(0.9, MosEffects.durationFactor(assault, listOf(mos(OperatorSpecialty.SIGNALS))), 1e-9)
        val school = GameCatalog.missions.first { it.scope == MissionScope.TRAINING }
        assertEquals(1.0, MosEffects.durationFactor(school, listOf(mos(OperatorSpecialty.SIGNALS))), 1e-9)
        assertEquals(15, MosEffects.casualtySavePct(listOf(mos(OperatorSpecialty.BREACHER), mos(OperatorSpecialty.MEDIC))))
        assertEquals(0.85, MosEffects.recoveryFactor(listOf(mos(OperatorSpecialty.MEDIC))), 1e-9)
        assertEquals(1.15, MosEffects.cohesionFactor(listOf(mos(OperatorSpecialty.COMMAND))), 1e-9)
        // Recon cuts the blown-cover chance on clandestine work
        ClandestineRules.languageRegions.firstOrNull()?.let {
            val clandestine = GameCatalog.missions.firstOrNull { m -> m.isClandestine } ?: return@let
            val plain = ClandestineRules.blownCoverChancePct(clandestine, listOf(mos(OperatorSpecialty.ASSAULT)), emptySet())
            val withRecon = ClandestineRules.blownCoverChancePct(clandestine, listOf(mos(OperatorSpecialty.RECON)), emptySet())
            assertTrue(withRecon <= plain)
        }
    }

    @Test
    fun squadBuilderListsMissingMos() {
        val squad = listOf(mos(OperatorSpecialty.ASSAULT), mos(OperatorSpecialty.MEDIC))
        assertEquals(
            listOf(OperatorSpecialty.RECON, OperatorSpecialty.SIGNALS, OperatorSpecialty.BREACHER, OperatorSpecialty.COMMAND),
            MosEffects.missingMos(squad)
        )
        assertTrue(MosEffects.missingMos(MosEffects.SQUAD_MOS.map { mos(it) }).isEmpty())
    }

    @Test
    fun parachutistLadderRunsOnCombatJumps() {
        val jumper = base.copy(completedMissionIds = base.completedMissionIds + "msn_night_qual")
        val wings = setOf("insig_parachutist")
        assertNull(TacticalCalculator.parachutistMilestone(jumper.copy(combatJumps = 2), wings))
        assertEquals(TacticalCalculator.SENIOR_PARA_ID, TacticalCalculator.parachutistMilestone(jumper.copy(combatJumps = 3), wings))
        val senior = wings + TacticalCalculator.SENIOR_PARA_ID
        // Master needs 8 combat jumps and SGT+
        assertNull(TacticalCalculator.parachutistMilestone(jumper.copy(combatJumps = 8), senior))
        val sgt = jumper.copy(combatJumps = 8, xp = OperatorRank.SGT.xpRequired, authorizedTier = RankTier.NCO)
        assertEquals(TacticalCalculator.MASTER_PARA_ID, TacticalCalculator.parachutistMilestone(sgt, senior))
        // A star for every 3 combat jumps
        assertEquals(0, TacticalCalculator.combatJumpStars(jumper.copy(combatJumps = 2)))
        assertEquals(2, TacticalCalculator.combatJumpStars(jumper.copy(combatJumps = 7)))
        assertEquals(5, TacticalCalculator.combatJumpStars(jumper.copy(combatJumps = 40)))
        // Only the highest wings are worn
        val ownedBadges = GameCatalog.medals.filter { it.id in setOf("insig_parachutist", TacticalCalculator.SENIOR_PARA_ID, TacticalCalculator.MASTER_PARA_ID) }
        val worn = UniformRegaliaRules.resolveChestBadgeSlots(sgt, ownedBadges, UniformTailorOverrides()).filterNotNull().map { it.id }
        assertTrue(TacticalCalculator.MASTER_PARA_ID in worn)
        assertFalse("insig_parachutist" in worn)
        assertFalse(TacticalCalculator.SENIOR_PARA_ID in worn)
    }

    @Test
    fun cgscEarnsTheStaffCrest() {
        val crest = GameCatalog.medals.first { it.id == "insig_staff_crest" }
        val cpt = base.copy(xp = OperatorRank.CPT.xpRequired, authorizedTier = RankTier.OFFICER, specialty = OperatorSpecialty.COMMAND)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(crest, cpt.copy(status = OperatorStatus.HQ_STAFF, staffOpsSupported = 5), emptySet(), emptyList()).eligible)
        val grad = cpt.copy(completedMissionIds = cpt.completedMissionIds + TacticalCalculator.CGSC_SCHOOL_ID)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(crest, grad, emptySet(), emptyList()).eligible)
        val cgsc = GameCatalog.missions.first { it.id == TacticalCalculator.CGSC_SCHOOL_ID }
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(cpt, cgsc, emptySet()).eligible)
        val lt = cpt.copy(xp = OperatorRank.LT2.xpRequired)
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(lt, cgsc, emptySet()).eligible)
    }
}
