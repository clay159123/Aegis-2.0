package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.TacticalCalculator
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Patch after Session 11: repeat-award label, kept unit citations, Auto for coat/camo/gloves. */
class PatchOneTest {
    private val op = GameCatalog.createPostBasicRecruit("op_p", OperatorSpecialty.ASSAULT, emptySet(), 0)
    private val shield = GameCatalog.medals.first { it.id == "medal_purple_shield" }
    private fun cit(n: Int) = (1..n).map { AwardedMedal("c$it", op.id, shield.id, "wounded", awardedAtMillis = it.toLong()) }

    @Test
    fun repeatAwardCardSaysWhatTheSoldierGets() {
        assertNull(TacticalCalculator.repeatAwardLabel(op, shield, emptyList()))
        assertEquals("2nd award: 1 Bronze Oak Leaf Cluster", TacticalCalculator.repeatAwardLabel(op, shield, cit(1)))
        assertEquals("3rd award: 2 Bronze Oak Leaf Cluster", TacticalCalculator.repeatAwardLabel(op, shield, cit(2)))
        assertEquals("6th award: 1 Silver Oak Leaf Cluster", TacticalCalculator.repeatAwardLabel(op, shield, cit(5)))
        assertTrue(TacticalCalculator.repeatAwardLabel(op, shield, cit(10))!!.startsWith("11th award"))
    }

    @Test
    fun disbandedSquadsLeaveTheirCitationWithTheSoldiers() {
        assertFalse(UniformRegaliaRules.hasUnitCitation(op, emptyList()))
        assertTrue(UniformRegaliaRules.hasUnitCitation(op.copy(retainedUnitCitations = 1), emptyList()))
    }

    @Test
    fun autoFlagsDefaultOff() {
        val req = UniformTailorRequest.fromOperator(op, WardrobeKit.ALPHAS)
        assertFalse(req.dressAuto)
        assertFalse(req.camoAuto)
        assertFalse(req.glovesAuto)
    }
}
