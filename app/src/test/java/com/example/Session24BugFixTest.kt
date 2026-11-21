package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.PowRules
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session 24: bug fixes (items 121, 124, 127, 128). */
class Session24BugFixTest {
    private val base = GameCatalog.createPostBasicRecruit("op_s24", OperatorSpecialty.ASSAULT, emptySet(), 0)

    @Test
    fun savingAStandardClearsConflictingPicks() {
        // Item 127
        val op = base.copy(
            wardrobe = base.wardrobe
                .withKit(WardrobeKit.ALPHAS, base.wardrobe.alphas.copy(dressStyle = DressUniformStyle.NCO_PARADE_BLUES, hasWhiteGloves = false))
                .withKit(WardrobeKit.FIELD, base.wardrobe.field.copy(camoPattern = CamoPattern.entries.first()))
        )
        val squad = TacticalSquad("sq", "Alpha", listOf(op.id), op.id,
            standardCamo = CamoPattern.entries.last(), standardDressStyle = DressUniformStyle.ARMY_SERVICE_GREEN)
        val followed = TacticalCalculator.followSquadStandard(op, squad)
        assertNull(followed.wardrobe.alphas.dressStyle)
        assertNull(followed.wardrobe.field.camoPattern)
        // Gloves weren't in the standard, so the soldier's pick stays
        assertEquals(false, followed.wardrobe.alphas.hasWhiteGloves)
        assertEquals(CamoPattern.entries.last(), TacticalCalculator.resolveKitAppearance(followed, WardrobeKit.FIELD, listOf(squad)).camoPattern)
        assertTrue(TacticalCalculator.isTierDefaultCoat(DressUniformStyle.NCO_PARADE_BLUES))
    }

    @Test
    fun schoolRecruitsArriveHoldingEverythingTheyEarned() {
        // Item 128: nothing one-time is left waiting to be awarded by hand
        RecruitSource.entries.forEach { source ->
            OperatorSpecialty.entries.filter { it != OperatorSpecialty.AVIATION || source == RecruitSource.WOCS_AVIATOR }.forEach { spec ->
                val recruit = GameCatalog.createRecruit("op_r", spec, source, emptySet(), 3)
                val held = TacticalCalculator.recruitArrivalAwards(recruit, source).toSet()
                val leftover = GameCatalog.medals.filter { m ->
                    m.id !in held && !TacticalCalculator.isRepeatableAward(m) &&
                        TacticalCalculator.evaluateAwardEligibility(m, recruit, held).eligible
                }
                assertTrue("$source $spec: ${leftover.map { it.id }}", leftover.isEmpty())
            }
        }
    }

    @Test
    fun foundingSoldiersHoldTheirRanksSchools() {
        // Item 121
        val byId = GameCatalog.initialOperators.associateBy { it.id }
        assertTrue(TacticalCalculator.OCS_MISSION_ID in byId.getValue("op_kestrel").completedMissionIds)
        assertTrue("msn_nco_school" in byId.getValue("op_vesper").completedMissionIds)
        assertTrue("msn_snco_school" in byId.getValue("op_bastion").completedMissionIds)
        assertTrue(byId.getValue("op_cipher").completedMissionIds.isEmpty())
        GameCatalog.initialOperators.forEach { assertTrue(it.id, GameCatalog.starterRecordSchools(it).isEmpty()) }
    }

    @Test
    fun powMedalOncePerCaptivity() {
        // Item 124
        val medal = GameCatalog.medals.first { it.id == PowRules.POW_MEDAL_ID }
        val home = base.copy(timesCaptured = 1)
        val cit = AwardedMedal("c1", home.id, PowRules.POW_MEDAL_ID, "", awardedAtMillis = 0L)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(medal, home, emptySet()).eligible)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(medal, home, setOf(medal.id), listOf(cit)).eligible)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(medal, home.copy(timesCaptured = 2), setOf(medal.id), listOf(cit)).eligible)
    }
}
