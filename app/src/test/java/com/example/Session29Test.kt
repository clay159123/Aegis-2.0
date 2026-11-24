package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.PowRules
import com.example.domain.TacticalCalculator
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session 29: SF for Signals (139), Selection Prep (141), environment schools (142), snipers (143). */
class Session29Test {
    private fun m(id: String) = GameCatalog.missions.first { it.id == id }
    private val base = GameCatalog.createPostBasicRecruit("op_s29", OperatorSpecialty.ASSAULT, emptySet(), 0)
        .copy(completedMissionIds = setOf("msn_basic_qual", "msn_night_qual"), baseStealth = 20, baseResilience = 20, baseTech = 20, xp = OperatorRank.SPC.xpRequired)

    @Test
    fun signalsCanTryForSf() {
        assertTrue(OperatorSpecialty.SIGNALS in m("msn_sf_school").schoolRequirements!!.allowedSpecialties)
    }

    @Test
    fun selectionPrepBanksTenPercentForOneTry() {
        val prep = m(TacticalCalculator.SELECTION_PREP_ID)
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(base, prep, emptySet()).eligible)
        val banked = base.copy(eventBuffs = base.eventBuffs.copy(selectionPrepPct = 10))
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(banked, prep, emptySet()).missingRequirements.any { it.startsWith("Prep already banked") })
        val sf = m("msn_sf_school")
        assertEquals(10, TacticalCalculator.schoolPassOddsPct(sf, banked, emptyList()) - TacticalCalculator.schoolPassOddsPct(sf, base, emptyList()))
        assertEquals(0, TacticalCalculator.schoolPassOddsPct(m("msn_ranger_school"), banked, emptyList()) - TacticalCalculator.schoolPassOddsPct(m("msn_ranger_school"), base, emptyList()))
        // The buff survives a save round trip
        assertEquals(10, SoldierEventBuffs.fromCsv(banked.eventBuffs.toCsv()).selectionPrepPct)
        assertEquals(0, SoldierEventBuffs.fromCsv("0||0|0|false").selectionPrepPct)
    }

    @Test
    fun environmentSchoolsHelpInTheirTerrainAndPickOneBadge() {
        val mountain = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT && it.theater.terrain == Terrain.MOUNTAIN }
        val trained = base.copy(id = "t", completedMissionIds = base.completedMissionIds + "msn_mountain_school")
        val plain = base.copy(id = "p")
        assertEquals(5, TacticalCalculator.environmentPassBonus(mountain, listOf(trained)))
        assertEquals(3, TacticalCalculator.environmentPassBonus(mountain, listOf(trained, plain)))
        assertEquals(0, TacticalCalculator.environmentPassBonus(mountain, listOf(plain)))
        // One badge: the combat patch theater wins when it matches
        val all = setOf("insig_mountain", "insig_jungle", "insig_arctic")
        val jungleCampaign = GameCatalog.campaigns.first { it.theater.terrain == Terrain.JUNGLE }
        val vet = trained.copy(combatPatchHistory = listOf(CombatPatchRecord(jungleCampaign.id, UnitPatchStyle.DIVISION_1, 5L)))
        assertEquals("insig_jungle", UniformRegaliaRules.environmentBadgeFor(vet, all)?.id)
        assertEquals("insig_arctic", UniformRegaliaRules.environmentBadgeFor(vet, setOf("insig_arctic"))?.id)
        assertNull(UniformRegaliaRules.environmentBadgeFor(vet, emptySet()))
        // A fixed pick when nothing else decides
        val pick = UniformRegaliaRules.environmentBadgeFor(plain, all)?.id
        assertEquals(pick, UniformRegaliaRules.environmentBadgeFor(plain, all)?.id)
        // Never in the chest pin slots
        val envMedals = GameCatalog.medals.filter { it.id in all }
        assertTrue(UniformRegaliaRules.resolveChestBadgeSlots(plain, envMedals, UniformTailorOverrides()).all { it == null })
    }

    @Test
    fun snipersNeedExpertAndCarryRisk() {
        val school = m(TacticalCalculator.SNIPER_SCHOOL_ID)
        val sharp = base.copy(marksmanshipQual = MarksmanshipBadgeTier.SHARPSHOOTER)
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(sharp, school, emptySet()).missingRequirements.contains("Requires an Expert rifle qualification"))
        val expert = base.copy(marksmanshipQual = MarksmanshipBadgeTier.EXPERT)
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(expert, school, emptySet()).missingRequirements.none { it.contains("Expert") })
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(expert.copy(specialty = OperatorSpecialty.MEDIC), school, emptySet()).eligible.not())
        val sniper = expert.copy(completedMissionIds = expert.completedMissionIds + TacticalCalculator.SNIPER_SCHOOL_ID)
        assertEquals("Sniper", sniper.mosLabel)
        assertTrue(TacticalCalculator.isInfantryOrSfQualified(sniper))
        val field = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT }
        assertEquals(PowRules.captureChancePct(field, expert, false) * 1.5, PowRules.captureChancePct(field, sniper, false), 1e-9)
        val recon = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT && it.primaryFocus.contains("Recon") }
        assertEquals(4, TacticalCalculator.sniperPassBonus(recon, listOf(sniper, expert)))
        assertEquals(0, TacticalCalculator.sniperPassBonus(recon, listOf(expert)))
        // The Sniper Badge takes the pocket slot below the ribbons
        val badges = GameCatalog.medals.filter { it.id == TacticalCalculator.SNIPER_BADGE_ID || it.id == "insig_parachutist" }
        assertEquals(TacticalCalculator.SNIPER_BADGE_ID, UniformRegaliaRules.resolveChestBadgeSlots(sniper, badges, UniformTailorOverrides())[1]?.id)
    }
}
