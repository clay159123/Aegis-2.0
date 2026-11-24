package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.*
import com.example.domain.model.*
import com.example.ui.components.BASE_PLOTS
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session 22: bigger Barracks (116), up to 3 SF squads (119), the SF Compound (118) and the new base row. */
class Session22Test {
    private fun fac(vararg pairs: Pair<FacilityType, Int>) = pairs.map { FacilityState(it.first, it.second) }
    private fun sf(id: String) = GameCatalog.createPostBasicRecruit(id, OperatorSpecialty.ASSAULT, emptySet(), 0).copy(
        id = id, unlocked = true, completedMissionIds = setOf("msn_basic_qual", TacticalCalculator.SF_SCHOOL_ID, TacticalCalculator.SERE3_SCHOOL_ID),
        // Session 30: SF2, so the High-risk test mission's tier gate passes
        sfMissionsSucceeded = 3
    )
    private fun grunt(id: String) = GameCatalog.createPostBasicRecruit(id, OperatorSpecialty.ASSAULT, emptySet(), 0).copy(id = id, unlocked = true)

    @After fun reset() {
        TacticalCalculator.sfSquadEstablished = false
        CompoundRules.compoundLevel = 0
    }

    @Test
    fun barracksAndMissionSlotsGrow() {
        assertEquals(8, TacticalCalculator.maxFieldRosterSlots(fac(FacilityType.BARRACKS to 1)))
        assertEquals(35, TacticalCalculator.maxFieldRosterSlots(fac(FacilityType.BARRACKS to 10)))
        assertEquals(4, TacticalCalculator.maxDeploymentSlots(fac(FacilityType.COMMAND_HQ to 8)))
        assertEquals(5, TacticalCalculator.maxDeploymentSlots(fac(FacilityType.COMMAND_HQ to 9)))
    }

    @Test
    fun compoundHousesSfSquadsAndSetsTheLimit() {
        assertEquals(listOf(0, 6, 6, 6, 12, 12, 12, 18, 18, 18, 18), (0..10).map { CompoundRules.compoundCapacity(it) })
        assertEquals(1, CompoundRules.sfSquadLimit(emptyList()))
        assertEquals(1, CompoundRules.sfSquadLimit(fac(FacilityType.SF_COMPOUND to 3)))
        assertEquals(2, CompoundRules.sfSquadLimit(fac(FacilityType.SF_COMPOUND to 4)))
        assertEquals(3, CompoundRules.sfSquadLimit(fac(FacilityType.SF_COMPOUND to 10)))
        // SF squad members in the Compound don't take Barracks slots
        val sfOps = (1..8).map { sf("s$it") }
        val others = (1..5).map { grunt("g$it") }
        val squads = listOf(TacticalSquad("sf1", "ODA 1", sfOps.take(6).map { it.id }, "s1", isSpecialForces = true))
        val all = sfOps + others
        assertEquals(13, CompoundRules.fieldRosterUsed(all, squads, emptyList()))
        assertEquals(7, CompoundRules.fieldRosterUsed(all, squads, fac(FacilityType.SF_COMPOUND to 1)))
        assertEquals(6, CompoundRules.compoundUsed(all, squads, fac(FacilityType.SF_COMPOUND to 10)))
    }

    @Test
    fun compoundUnlocksWithTheFirstSfSquad() {
        val f = fac(FacilityType.COMMAND_HQ to 1)
        TacticalCalculator.sfSquadEstablished = false
        assertFalse(TacticalCalculator.isFacilityUnlocked(FacilityType.SF_COMPOUND, f))
        assertEquals("Form an SF squad first", TacticalCalculator.facilityUpgradeBlocker(FacilityType.SF_COMPOUND, f))
        TacticalCalculator.sfSquadEstablished = true
        assertTrue(TacticalCalculator.isFacilityUnlocked(FacilityType.SF_COMPOUND, f))
        assertNull(TacticalCalculator.facilityUpgradeBlocker(FacilityType.SF_COMPOUND, f))
        assertEquals(0, FacilityType.SF_COMPOUND.startLevel)
    }

    @Test
    fun anySfSquadCanAnchorAnSfTeam() {
        val a = (1..6).map { sf("a$it") }
        val b = (1..6).map { sf("b$it") }
        val squads = listOf(
            TacticalSquad("sf1", "ODA 1", a.map { it.id }, "a1", isSpecialForces = true),
            TacticalSquad("sf2", "ODA 2", b.map { it.id }, "b1", isSpecialForces = true)
        )
        val da = GameCatalog.missions.first { it.id == "msn_sf_da_hostage" }
        assertNull(TacticalCalculator.teamBlocker(da, b.take(4), squads))
        assertNull(TacticalCalculator.teamBlocker(da, b.take(2) + a.take(2), squads))
        assertNotNull(TacticalCalculator.teamBlocker(da, b.take(1) + a.take(1) + listOf(sf("x"), sf("y")), squads))
        // Clandestine: everyone from an SF squad, any of them
        val clan = GameCatalog.missions.first { it.id == "msn_clan_meridian" }
        val speakers = b.take(2).map { it.copy(languageRegions = setOf(TheaterRegion.MERIDIAN_CITY)) } + a.take(2)
        assertNull(TacticalCalculator.teamBlocker(clan, speakers, squads))
        assertNotNull(TacticalCalculator.teamBlocker(clan, speakers.take(3) + sf("x"), squads))
    }

    @Test
    fun compoundPerks() {
        CompoundRules.compoundLevel = 10
        assertEquals(10, CompoundRules.blownCoverCutPct())
        assertEquals(0.6, CompoundRules.sfRecoveryMultiplier(), 0.001)
        assertEquals(5, CompoundRules.sfSchoolBonusPct())
        CompoundRules.compoundLevel = 9
        assertEquals(0, CompoundRules.sfSchoolBonusPct())
        val clan = GameCatalog.missions.first { it.id == "msn_clan_meridian" }
        val team = (1..4).map { sf("t$it") }
        CompoundRules.compoundLevel = 0
        val without = ClandestineRules.blownCoverChancePct(clan, team, emptySet())
        CompoundRules.compoundLevel = 5
        assertEquals(without - 5, ClandestineRules.blownCoverChancePct(clan, team, emptySet()))
    }

    @Test
    fun baseMapHasTheCompoundRowWithoutOverlaps() {
        val compound = BASE_PLOTS.single { it.type == FacilityType.SF_COMPOUND }
        assertEquals(119f, compound.w)
        val gate = BASE_PLOTS.single { it.type == FacilityType.MAIN_GATE }
        assertTrue(gate.y >= compound.y)
        assertEquals(FacilityType.entries.toSet(), BASE_PLOTS.mapNotNull { it.type }.toSet())
        for (i in BASE_PLOTS.indices) for (j in i + 1 until BASE_PLOTS.size) {
            val p = BASE_PLOTS[i]; val q = BASE_PLOTS[j]
            val overlap = p.x < q.x + q.w && q.x < p.x + p.w && p.y < q.y + q.h && q.y < p.y + p.h
            assertFalse("${p.label} overlaps ${q.label}", overlap)
            assertTrue(p.y + p.h <= 436f)
        }
    }
}
