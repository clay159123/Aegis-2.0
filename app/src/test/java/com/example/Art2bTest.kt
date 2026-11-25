package com.example

import com.example.data.catalog.GameCatalog
import com.example.ui.components.BaseLandmarks
import com.example.ui.viewmodel.parseCampaignDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Art 2b: Heritage Walk statue spots and campaign completion days. */
class Art2bTest {
    @Test fun everyCampaignHasItsOwnStatueSpot() {
        val spots = GameCatalog.campaigns.map { BaseLandmarks.spotIndex(it.order, it.isSpecialForces) }
        assertEquals(GameCatalog.campaigns.size, spots.toSet().size)
        assertTrue(spots.all { it in BaseLandmarks.STATUE_SPOTS.indices })
    }

    @Test fun campaignDaysParse() {
        assertEquals(mapOf("camp_a" to 12.5, "camp_b" to 400.0), parseCampaignDays("camp_a:12.5,camp_b:400.0"))
        assertTrue(parseCampaignDays("").isEmpty())
        assertEquals(mapOf("camp_a" to 3.0), parseCampaignDays("camp_a:3.0,broken,:7"))
    }
}
