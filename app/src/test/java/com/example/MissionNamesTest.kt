package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.MissionNameGenerator
import com.example.domain.MissionNames
import com.example.domain.TacticalCalculator
import com.example.domain.displayTitle
import com.example.domain.model.OperatorSpecialty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session 14a: generated mission names, SF campaign gating, name-pool fallback. */
class MissionNamesTest {

    @Test
    fun boardNamesAreUniqueStableAndChangeOnRotation() {
        val pool = GameCatalog.rotatingStandalonePool
        val a = MissionNameGenerator.namesFor(pool, 1_000L)
        assertEquals(pool.size, a.values.toSet().size)
        // Same rotation, same names
        assertEquals(a, MissionNameGenerator.namesFor(pool, 1_000L))
        // New rotation, new names
        assertNotEquals(a, MissionNameGenerator.namesFor(pool, 2_000L))
        // Each keeps its catalog prefix ("Op", "Contract", "Diplomatic")
        pool.forEach { m -> assertTrue(a[m.id]!!.startsWith(m.title.substringBefore(':'))) }

        MissionNames.current = a
        assertEquals(a[pool.first().id], pool.first().displayTitle)
        // Missions not on the board keep their catalog title
        val campaignStage = GameCatalog.missions.first { it.campaignId != null }
        assertEquals(campaignStage.title, campaignStage.displayTitle)
        MissionNames.current = emptyMap()
    }

    @Test
    fun sfCampaignOpensWithTheSfSquadNotCampaignFour() {
        val uw = GameCatalog.campaigns.first { it.isSpecialForces }
        val stage1 = GameCatalog.missions.first { it.id == uw.stageMissionIds.first() }
        val allTheaters = GameCatalog.campaigns.filter { !it.isSpecialForces }.map { it.id }.toSet()
        try {
            TacticalCalculator.sfSquadEstablished = false
            assertFalse(TacticalCalculator.isCampaignUnlocked(uw, allTheaters))
            assertFalse(TacticalCalculator.isCampaignStageUnlocked(stage1, emptySet(), allTheaters))
            TacticalCalculator.sfSquadEstablished = true
            assertTrue(TacticalCalculator.isCampaignUnlocked(uw, emptySet()))
            assertTrue(TacticalCalculator.isCampaignStageUnlocked(stage1, emptySet(), emptySet()))
        } finally {
            TacticalCalculator.sfSquadEstablished = false
        }
    }

    @Test
    fun nameFallbackStaysUniqueAfterEveryNameIsUsed() {
        val used = mutableSetOf<String>()
        repeat(GameCatalog.recruitNamePoolSize) { i -> used += GameCatalog.pickRecruitName(used, i) }
        assertEquals(GameCatalog.recruitNamePoolSize, used.size)
        val next = GameCatalog.pickRecruitName(used, 7)
        assertFalse(next in used)
        assertTrue(GameCatalog.pickCallsign(OperatorSpecialty.ASSAULT, setOf(), 0).isNotBlank())
    }
}
