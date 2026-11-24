package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.TacticalCalculator
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session 28: marksmanship (148) and SF team patches (120). */
class Session28Test {
    private fun recruit(spec: OperatorSpecialty) = GameCatalog.createPostBasicRecruit("op_$spec", spec, emptySet(), 0)
    private val maxRange = FacilityType.entries.map { FacilityState(it, if (it == FacilityType.TRAINING_RANGE) 10 else it.startLevel) }

    @Test
    fun firstTryExpertIsNoLongerAutomatic() {
        assertEquals(33, EXPERT_SCORE)
        assertEquals(5, TacticalCalculator.rangeQualBonus(maxRange))
        fun expertOdds(op: Operator) = (0..QUAL_ROLL_MAX_FOR_TEST).count {
            TacticalCalculator.rifleQualScore(op, emptyList(), maxRange, it) >= EXPERT_SCORE
        } * 100 / (QUAL_ROLL_MAX_FOR_TEST + 1)
        val pv2Infantry = expertOdds(recruit(OperatorSpecialty.ASSAULT))
        assertTrue("$pv2Infantry", pv2Infantry in 15..45)
        assertTrue(expertOdds(recruit(OperatorSpecialty.RECON)) > 0)
        assertEquals(0, expertOdds(recruit(OperatorSpecialty.SIGNALS)))
        // A combat-MOS sergeant usually makes it
        val sgt = recruit(OperatorSpecialty.ASSAULT).copy(xp = OperatorRank.SGT.xpRequired, authorizedTier = RankTier.NCO)
        assertTrue(expertOdds(sgt) >= 60)
        assertEquals(2, TacticalCalculator.rifleQualMosModifier(recruit(OperatorSpecialty.ASSAULT)))
        assertEquals(-3, TacticalCalculator.rifleQualMosModifier(recruit(OperatorSpecialty.AVIATION)))
    }

    @Test
    fun eachSfTeamWearsItsOwnArrowhead() {
        val a = recruit(OperatorSpecialty.ASSAULT).copy(id = "a")
        val b = recruit(OperatorSpecialty.RECON).copy(id = "b")
        val c = recruit(OperatorSpecialty.MEDIC).copy(id = "c")
        val oda1 = TacticalSquad("s1", "ODA 1", listOf("a"), "a", isSpecialForces = true, standardPatch = UnitPatchStyle.SPECIAL_FORCES)
        val oda2 = TacticalSquad("s2", "ODA 2", listOf("b"), "b", isSpecialForces = true, standardPatch = UnitPatchStyle.SF_ODA2)
        val renamed = TacticalSquad("s3", "Wolfpack", listOf("c"), "c", isSpecialForces = true, standardPatch = UnitPatchStyle.DIVISION_2)
        val squads = listOf(oda1, oda2, renamed)
        assertEquals(UnitPatchStyle.SPECIAL_FORCES, UniformRegaliaRules.resolveUnitPatch(a, squads))
        assertEquals(UnitPatchStyle.SF_ODA2, UniformRegaliaRules.resolveUnitPatch(b, squads))
        // A legacy SF squad with no team patch takes the first free one
        assertEquals(UnitPatchStyle.SF_ODA3, UniformRegaliaRules.resolveUnitPatch(c, squads))
        assertTrue(UnitPatchStyle.SF_TEAM_PATCHES.none { it.isDivision })
        // Renaming keeps the stored team patch
        assertEquals(UnitPatchStyle.SF_ODA2, UnitPatchStyle.sfTeamPatchFor(oda2.copy(name = "Ghost"), squads))
    }

    private val QUAL_ROLL_MAX_FOR_TEST = RIFLE_QUAL_ROLL_MAX
}
