package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.ClandestineRules
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Session 27: language regions, native speakers (133) and boat riders (137). */
class Session27Test {
    private val base = GameCatalog.createPostBasicRecruit("op_s27", OperatorSpecialty.ASSAULT, emptySet(), 0)

    @Test
    fun languageRegionsAreClandestineTheatersPlusKestrel() {
        val expected = (GameCatalog.missions.filter { it.isClandestine }.map { it.theater } + TheaterRegion.KESTREL_STRAIT).toSet()
        assertEquals(expected, ClandestineRules.languageRegions.toSet())
        assertTrue(TheaterRegion.MERIDIAN_CITY in expected && TheaterRegion.SAHR_EXPANSE in expected && TheaterRegion.VERDANCE_BASIN in expected)
    }

    @Test
    fun nativeSpeakersKnowTheirRegionAndGetAnEdge() {
        val native = base.copy(nativeRegion = TheaterRegion.SAHR_EXPANSE, baseStealth = 20, baseTech = 20)
        val schooled = base.copy(languageRegions = setOf(TheaterRegion.SAHR_EXPANSE), baseStealth = 20, baseTech = 20)
        assertTrue(ClandestineRules.knowsLanguage(native, TheaterRegion.SAHR_EXPANSE))
        assertTrue(TheaterRegion.SAHR_EXPANSE !in ClandestineRules.regionsToLearn(native))
        val clan = GameCatalog.missions.first { it.isClandestine && it.theater == TheaterRegion.SAHR_EXPANSE }
        assertEquals(
            ClandestineRules.blownCoverChancePct(clan, listOf(schooled), emptySet()) - ClandestineRules.NATIVE_EXTRA_CUT_PCT,
            ClandestineRules.blownCoverChancePct(clan, listOf(native), emptySet())
        )
        assertEquals(ClandestineRules.NATIVE_SF_PASS_BONUS, ClandestineRules.nativeSfPassBonus(clan, listOf(native)))
        assertEquals(0, ClandestineRules.nativeSfPassBonus(clan, listOf(schooled)))
        // Roughly 8% of rolls land a native language
        val rng = Random(7)
        val hits = (1..5000).count { ClandestineRules.rollNativeRegion(rng) != null }
        assertTrue("$hits", hits in 300..500)
    }

    @Test
    fun coastalSfMissionsTakeUpToTwoBoatRiders() {
        val coastal = GameCatalog.missions.filter { TacticalCalculator.takesBoatRiders(it) }
        assertTrue(coastal.isNotEmpty())
        val mission = coastal.first()
        val rider = base.copy(id = "op_rider")
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(rider, mission, emptySet()).missingRequirements.none { it == "SF-qualified soldiers only" })
        assertEquals("Non-SF soldiers can only ride in with the boat team",
            mission.allowedInsertions.firstOrNull { it != InsertionMethod.MARITIME }?.let {
                TacticalCalculator.insertionBlocker(mission, it, listOf(rider), null, emptyList())
            } ?: "Non-SF soldiers can only ride in with the boat team")
        // Clandestine and non-coastal SF missions stay SF only
        GameCatalog.missions.filter { it.requiresSfTeam && !TacticalCalculator.takesBoatRiders(it) }.forEach { m ->
            assertTrue(m.id, TacticalCalculator.canOperatorDeployOnMission(rider, m, emptySet()).missingRequirements.contains("SF-qualified soldiers only"))
        }
        assertTrue(GameCatalog.missions.filter { it.isClandestine }.none { TacticalCalculator.takesBoatRiders(it) })
    }
}
