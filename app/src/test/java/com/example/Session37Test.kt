package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.model.FacilityType
import com.example.ui.components.BASE_PLOTS
import com.example.ui.components.HERITAGE_LABEL
import com.example.ui.components.PARADE_LABEL
import com.example.ui.components.monumentCampaigns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Session37Test {
    @Test
    fun baseMapHasTheParadeGroundAndHeritageWalk() {
        val parade = BASE_PLOTS.single { it.label == PARADE_LABEL }
        val heritage = BASE_PLOTS.single { it.label == HERITAGE_LABEL }
        val compound = BASE_PLOTS.single { it.type == FacilityType.SF_COMPOUND }
        // The new row sits between the Armory row and the SF Compound / Gate row
        assertTrue(parade.y > BASE_PLOTS.single { it.type == FacilityType.ARMORY_WORKSHOP }.y)
        assertTrue(compound.y > parade.y + parade.h)
        assertEquals(parade.y, heritage.y)
    }

    @Test
    fun monumentsFollowCampaignOrderWithSfLast() {
        val all = GameCatalog.campaigns.map { it.id }.toSet()
        val order = monumentCampaigns(all)
        assertEquals(GameCatalog.campaigns.size, order.size)
        assertTrue(order.size <= 12)
        assertTrue(order.last().isSpecialForces || order.none { it.isSpecialForces })
        assertTrue(monumentCampaigns(emptySet()).isEmpty())
    }
}
