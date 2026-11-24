package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.MoraleRules
import com.example.domain.SoldierEvents
import com.example.domain.TacticalCalculator
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Session38Test {
    private val base = GameCatalog.createPostBasicRecruit("op_s38", OperatorSpecialty.ASSAULT, emptySet(), 0)
        .copy(completedMissionIds = setOf("msn_basic_qual"))

    @Test
    fun bandsAndTeamOdds() {
        assertEquals(MoraleRules.Band.HIGH, MoraleRules.band(85))
        assertEquals(MoraleRules.Band.STEADY, MoraleRules.band(70))
        assertEquals(MoraleRules.Band.LOW, MoraleRules.band(30))
        assertEquals(MoraleRules.Band.BROKEN, MoraleRules.band(10))
        assertEquals(2, MoraleRules.teamOddsPct(listOf(base.copy(morale = 90), base.copy(morale = 80))))
        assertEquals(-6, MoraleRules.teamOddsPct(listOf(base.copy(morale = 10))))
        assertEquals(0, MoraleRules.teamOddsPct(emptyList()))
    }

    @Test
    fun adjustClampsAndTheCsmSoftensLosses() {
        assertEquals(100, MoraleRules.adjust(base.copy(morale = 98), 5).morale)
        assertEquals(0, MoraleRules.adjust(base.copy(morale = 3), -10).morale)
        MoraleRules.csmSeated = true
        try {
            assertEquals(63, MoraleRules.adjust(base.copy(morale = 70), -10).morale) // -10 x 0.75 = -7.5, rounds to -7
            assertEquals(75, MoraleRules.adjust(base.copy(morale = 70), 5).morale)
        } finally { MoraleRules.csmSeated = false }
    }

    @Test
    fun baselineFromDfacGateAndParade() {
        val facs = FacilityType.entries.map { FacilityState(it, when (it) { FacilityType.DFAC -> 5; FacilityType.MAIN_GATE -> 4; else -> it.startLevel }) }
        val now = 10_000_000L
        assertEquals(60 + 10 + 4, MoraleRules.baseline(facs, 0L, now))
        assertEquals(60 + 10 + 4 + 3, MoraleRules.baseline(facs, now - 1000L, now))
        // Drift toward the baseline, never past it
        assertEquals(72, MoraleRules.drift(base.copy(morale = 70), 74, 2).morale)
        assertEquals(74, MoraleRules.drift(base.copy(morale = 90), 74, 50).morale)
    }

    @Test
    fun brokenMoraleBlocksBoardsButNotPins() {
        assertNotNull(MoraleRules.boardBlocker(base.copy(morale = 10)))
        assertNull(MoraleRules.boardBlocker(base.copy(morale = 40)))
        assertTrue(MoraleRules.petitionFactor(base.copy(morale = 10)) > MoraleRules.petitionFactor(base.copy(morale = 90)))
    }

    @Test
    fun deploymentStreaksAndRest() {
        val back = base.copy(deployStreak = 3, lastReturnAtMillis = 1_000_000L)
        assertEquals(4, MoraleRules.nextStreak(back, 1_000_000L + 60_000L))
        assertEquals(-2, MoraleRules.streakPenalty(4))
        assertEquals(0, MoraleRules.streakPenalty(3))
        assertEquals(1, MoraleRules.nextStreak(back, 1_000_000L + MoraleRules.REST_GAP_MILLIS))
    }

    @Test
    fun inspectionGradesAndFindings() {
        assertEquals(4, MoraleRules.inspectionMorale("S (Distinguished)"))
        assertEquals(4, MoraleRules.inspectionMorale("A+"))
        assertEquals(3, MoraleRules.inspectionMorale("A"))
        assertEquals(2, MoraleRules.inspectionMorale("B"))
        assertEquals(0, MoraleRules.inspectionMorale("C"))
        // Gloves are only checked with Mess; every deduction is written out
        val soldier = base.copy(hasWhiteGloves = false)
        val alphas = UniformRegaliaRules.evaluateUniformInspection(listOf(soldier), emptyList(), emptyList(), emptyList(), inspectedKit = WardrobeKit.ALPHAS)
        assertFalse(alphas.findings.any { "gloves" in it })
        val messNoGloves = soldier.copy(wardrobe = soldier.wardrobe.withKit(WardrobeKit.MESS, soldier.wardrobe.mess.copy(hasWhiteGloves = false)))
        val mess = UniformRegaliaRules.evaluateUniformInspection(listOf(messNoGloves), emptyList(), emptyList(), emptyList(), inspectedKit = WardrobeKit.MESS)
        assertTrue(mess.findings.any { "white gloves" in it && "(-5)" in it })
        // A squad member who isn't the leader isn't docked for the leader crest
        val leader = base.copy(id = "op_ldr")
        val member = base.copy(id = "op_mbr")
        val squad = TacticalSquad("sq", "Alpha", listOf(leader.id, member.id), leader.id)
        val report = UniformRegaliaRules.evaluateUniformInspection(listOf(leader, member), emptyList(), emptyList(), listOf(squad), subjectSquad = squad, inspectedKit = WardrobeKit.ALPHAS)
        assertFalse(report.findings.any { it.startsWith("${member.callsign}: squad leader") })
    }

    @Test
    fun alphasDefaultsGlovesOffAndSashForLeaders() {
        val leader = base.copy(id = "op_ldr2", hasWhiteGloves = true)
        val squad = TacticalSquad("sq2", "ODA 1", listOf(leader.id), leader.id, isSpecialForces = true)
        val look = TacticalCalculator.resolveKitAppearance(leader, WardrobeKit.ALPHAS, listOf(squad))
        assertFalse(look.hasWhiteGloves)
        assertTrue(look.hasDressSash)
        assertTrue(TacticalCalculator.resolveKitAppearance(leader, WardrobeKit.MESS, listOf(squad)).hasWhiteGloves)
        // Not a leader: no sash on Auto
        assertFalse(TacticalCalculator.resolveKitAppearance(base, WardrobeKit.ALPHAS, listOf(squad)).hasDressSash)
    }

    @Test
    fun mainGateCutsRecruitCost() {
        val was = TacticalCalculator.mainGateLevel
        try {
            TacticalCalculator.mainGateLevel = 5
            assertEquals(85.0, SoldierEvents.discountedRecruitCost(100.0), 1e-9)
        } finally { TacticalCalculator.mainGateLevel = was }
        assertEquals(1, FacilityType.MAIN_GATE.startLevel)
    }
}
