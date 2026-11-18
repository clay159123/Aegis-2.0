package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.TacticalCalculator
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session 16: camo by terrain, squad standards by tier, HQ dress defaults, crest placement, flight suit. */
class Session16Test {
    private val base = GameCatalog.createPostBasicRecruit("op_s16", OperatorSpecialty.ASSAULT, emptySet(), 0)
    private fun team(camo: CamoPattern?) = (1..4).map { i ->
        base.copy(id = "t$i", wardrobe = base.wardrobe.withKit(WardrobeKit.FIELD, WardrobeKitSettings(camoPattern = camo)))
    }
    private val desert = GameCatalog.missions.first { it.id == "msn_x_dune_convoy" }

    @Test
    fun camoMatchesTheTerrain() {
        assertEquals(Terrain.DESERT, desert.theater.terrain)
        val match = TacticalCalculator.teamCamoEffect(desert, team(CamoPattern.ARID_SAND), emptyList(), false)
        assertEquals(TacticalCalculator.CAMO_MATCH_PASS_BONUS, match.passDelta)
        assertEquals(TacticalCalculator.CAMO_MATCH_SURVIVAL_BONUS, match.survivalDelta)
        val wrong = TacticalCalculator.teamCamoEffect(desert, team(CamoPattern.ALPINE_FROST), emptyList(), false)
        assertEquals(-TacticalCalculator.CAMO_WRONG_PASS_PENALTY, wrong.passDelta)
        // Smart Duty-Dress picks the matching pattern for soldiers on Auto
        val auto = team(null).map { it.copy(camoPattern = CamoPattern.ALPINE_FROST) }
        assertEquals(TacticalCalculator.CAMO_MATCH_PASS_BONUS, TacticalCalculator.teamCamoEffect(desert, auto, emptyList(), true).passDelta)
        assertTrue(TacticalCalculator.teamCamoEffect(desert, auto, emptyList(), false).passDelta < 0)
        // Every non-garrison terrain has a matching pattern, and the flight suit is never a field choice
        Terrain.entries.filter { it != Terrain.GARRISON }.forEach { assertTrue(it.name, TacticalCalculator.bestCamoFor(it) != null) }
        assertFalse(CamoPattern.FLIGHT_SAGE in CamoPattern.SELECTABLE)
        // Odds include the camo effect
        val ops = team(CamoPattern.ARID_SAND)
        val good = TacticalCalculator.missionSuccessOddsPct(desert, ops, emptyList(), emptyList(), emptyList())
        val bad = TacticalCalculator.missionSuccessOddsPct(desert, team(CamoPattern.ALPINE_FROST), emptyList(), emptyList(), emptyList())
        assertTrue(good > bad)
    }

    @Test
    fun squadStandardUsesEachTiersVersion() {
        val std = TacticalCalculator.KitStandard(dressStyle = DressUniformStyle.OFFICER_SERVICE_BLUES, headgear = HeadgearStyle.OFFICER_PEAKED_CAP)
        val soldier = TacticalCalculator.tierVersionOf(std, RankTier.SOLDIER)
        assertEquals(DressUniformStyle.ARMY_DRESS_BLUES, soldier.dressStyle)
        assertEquals(HeadgearStyle.ENLISTED_SERVICE_CAP, soldier.headgear)
        val nco = TacticalCalculator.tierVersionOf(std.copy(headgear = HeadgearStyle.ENLISTED_SERVICE_CAP), RankTier.SNCO)
        assertEquals(DressUniformStyle.NCO_PARADE_BLUES, nco.dressStyle)
        assertEquals(HeadgearStyle.NCO_SERVICE_CAP, nco.headgear)
        assertEquals(HeadgearStyle.OFFICER_PEAKED_CAP, TacticalCalculator.tierVersionOf(HeadgearStyle.ENLISTED_SERVICE_CAP, RankTier.WARRANT))
        assertEquals(HeadgearStyle.TAN_BERET, TacticalCalculator.tierVersionOf(HeadgearStyle.TAN_BERET, RankTier.OFFICER))
        assertEquals(DressUniformStyle.FLAG_MESS_FORMAL, TacticalCalculator.tierVersionOf(DressUniformStyle.ENLISTED_MESS_BLUES, RankTier.FLAG_OFFICER))
        assertEquals(DressUniformStyle.ARMY_SERVICE_GREEN, TacticalCalculator.tierVersionOf(DressUniformStyle.ARMY_SERVICE_GREEN, RankTier.OFFICER))
    }

    @Test
    fun hqStaffDressDefaults() {
        val staff = base.copy(xp = OperatorRank.SGT.xpRequired, authorizedTier = RankTier.NCO, status = OperatorStatus.HQ_STAFF, hasDressSash = false)
        assertTrue(TacticalCalculator.resolveKitAppearance(staff, WardrobeKit.ALPHAS).hasDressSash)
        assertEquals(WardrobeKit.MESS, TacticalCalculator.resolveEffectiveWardrobeKit(staff.copy(activeWardrobeKit = WardrobeKit.ALPHAS), false, DutyUniformContext.DIPLOMATIC_MISSION))
        assertEquals(WardrobeKit.ALPHAS, TacticalCalculator.resolveEffectiveWardrobeKit(staff.copy(activeWardrobeKit = WardrobeKit.ALPHAS), false, DutyUniformContext.HQ_ASSIGNMENT))
    }

    @Test
    fun staffCrestAlwaysBelowTheNameTag() {
        val owned = GameCatalog.medals.filter { it.id in setOf("insig_staff_crest", "insig_logistics_crest", "insig_intel_crest") }
        for (mos in OperatorSpecialty.entries) {
            val slots = UniformRegaliaRules.resolveChestBadgeSlots(base.copy(specialty = mos), owned, UniformTailorOverrides())
            assertEquals(mos.name, "insig_staff_crest", slots[2]?.id)
        }
    }

    @Test
    fun aviatorsWearAFlightSuitAndNoFieldUniform() {
        val pilot = base.copy(specialty = OperatorSpecialty.AVIATION)
        assertFalse(WardrobeKit.FIELD.isAvailableFor(pilot))
        assertEquals("Flight Suit", WardrobeKit.BRAVOS.shortLabelFor(pilot))
        assertEquals("Bravos", WardrobeKit.BRAVOS.shortLabelFor(base))
        assertEquals(WardrobeKit.BRAVOS, TacticalCalculator.resolveEffectiveWardrobeKit(pilot, true, DutyUniformContext.FIELD_SCHOOL))
        assertEquals(WardrobeKit.BRAVOS, TacticalCalculator.resolveEffectiveWardrobeKit(pilot, true, DutyUniformContext.COMBAT_DEPLOYMENT))
        assertEquals(WardrobeKit.BRAVOS, TacticalCalculator.resolveKitAppearance(pilot, WardrobeKit.FIELD).activeWardrobeKit)
    }

    @Test
    fun bravosFollowTheAlphasFamily() {
        val officer = base.copy(xp = OperatorRank.CPT.xpRequired, authorizedTier = RankTier.OFFICER)
        val agsu = officer.copy(wardrobe = officer.wardrobe.withKit(WardrobeKit.ALPHAS, WardrobeKitSettings(dressStyle = DressUniformStyle.ARMY_SERVICE_GREEN)))
        assertEquals(DressUniformStyle.ARMY_SERVICE_GREEN, TacticalCalculator.resolveKitAppearance(agsu, WardrobeKit.BRAVOS).dressStyle)
        val blues = officer.copy(dressStyle = DressUniformStyle.OFFICER_SERVICE_BLUES)
        assertEquals(DressUniformStyle.OFFICER_SERVICE_BLUES, TacticalCalculator.resolveKitAppearance(blues, WardrobeKit.BRAVOS).dressStyle)
    }
}
