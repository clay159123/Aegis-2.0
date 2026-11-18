package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.MissionNameGenerator
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Session 14b: ten theater campaigns, a bigger single-mission pool, one-squad Extremes on every board. */
class Session14bTest {
    private val theaterCampaigns = GameCatalog.campaigns.filter { !it.isSpecialForces }

    @After fun reset() { TacticalCalculator.clearedCampaignIds = emptySet() }

    @Test
    fun tenCampaignsInOrderWithOwnTheaters() {
        assertEquals(10, theaterCampaigns.size)
        assertEquals((1..10).toList(), theaterCampaigns.map { it.order })
        assertEquals(10, theaterCampaigns.map { it.theater }.toSet().size)
        assertEquals(11, GameCatalog.campaigns.single { it.isSpecialForces }.order)
        theaterCampaigns.forEach { c ->
            assertTrue("${c.id} has 3 to 5 stages", c.stageMissionIds.size in 3..5)
            c.stageMissionIds.forEachIndexed { i, id ->
                val stage = GameCatalog.campaignMissionsById.getValue(id)
                assertEquals(c.id, stage.campaignId)
                assertEquals(i + 1, stage.campaignStageOrder)
                assertEquals(c.theater, stage.theater)
            }
        }
    }

    @Test
    fun campaignsGetHarder() {
        val peak = theaterCampaigns.map { c ->
            c.stageMissionIds.maxOf { GameCatalog.campaignMissionsById.getValue(it).recommendedPower }
        }
        // Campaign IV onward: each campaign's hardest stage is at least as hard as the last one's
        assertEquals(peak.drop(3).sorted(), peak.drop(3))
        val bonus = theaterCampaigns.map { it.completionBonusCredits }
        assertEquals(bonus.sorted(), bonus)
    }

    @Test
    fun campaignsUnlockInOrderAndAllTenClearTheBoard() {
        val ids = theaterCampaigns.map { it.id }
        assertTrue(TacticalCalculator.isCampaignUnlocked(theaterCampaigns[4], ids.take(4).toSet()))
        assertFalse(TacticalCalculator.isCampaignUnlocked(theaterCampaigns[5], ids.take(4).toSet()))
        assertFalse(TacticalCalculator.isAllCampaignsCleared(ids.take(9).toSet()))
        assertTrue(TacticalCalculator.isAllCampaignsCleared(ids.toSet()))
    }

    @Test
    fun newTheaterPerksApply() {
        val all = theaterCampaigns.map { it.id }.toSet()
        assertEquals(1.10 * 1.05, TacticalCalculator.campaignSupplyYieldMultiplier(all), 0.0001)
        assertEquals(1.10 * 1.05, TacticalCalculator.campaignCreditYieldMultiplier(all), 0.0001)
        assertEquals(1.0, TacticalCalculator.durationMultiplier(emptyList()), 0.0001)
        TacticalCalculator.clearedCampaignIds = setOf("camp_verdance", "camp_karst", "camp_meridian", "camp_talon")
        assertEquals(0.92, TacticalCalculator.durationMultiplier(emptyList()), 0.0001)
        assertEquals(0.80, TacticalCalculator.aidRecoveryMultiplier(emptyList()), 0.0001)
        assertEquals(2, TacticalCalculator.campaignFieldPassBonus())
        assertEquals(2, TacticalCalculator.campaignSurvivalBonus())
    }

    @Test
    fun everyBoardHasOneSquadHighAndExtreme() {
        val rng = Random(7)
        var prev = emptyList<String>()
        repeat(40) {
            val ids = TacticalCalculator.pickNextRotatingMissions(prev, rng)
            val ms = ids.map { id -> GameCatalog.rotatingStandalonePool.first { it.id == id } }
            assertEquals(TacticalCalculator.ROTATING_BOARD_SIZE, ids.toSet().size)
            assertTrue(ms.any { it.risk == MissionRisk.EXTREME && it.requiredSquads == 1 && it.scope == MissionScope.FIELD_COMBAT })
            assertTrue(ms.any { it.risk == MissionRisk.HIGH && it.requiredSquads == 1 && it.scope == MissionScope.FIELD_COMBAT })
            assertTrue(ms.any { it.scope == MissionScope.STAFF_DIPLOMATIC })
            prev = ids
        }
        val extremes = GameCatalog.rotatingStandalonePool.count { it.risk == MissionRisk.EXTREME }
        assertTrue("several one-squad Extremes in the pool", extremes >= 5)
    }

    @Test
    fun newTheatersGetNamesAndMissionsAreUnique() {
        val ids = GameCatalog.missions.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val pool = GameCatalog.rotatingStandalonePool.filter { it.theater == TheaterRegion.IRONHOLD || it.theater == TheaterRegion.KARST_PEAKS }
        val names = MissionNameGenerator.namesFor(pool, 99L)
        assertEquals(pool.size, names.values.toSet().size)
        assertTrue(GameCatalog.missions.any { it.theater == TheaterRegion.TALON_ARCHIPELAGO && it.coastal })
    }
}
