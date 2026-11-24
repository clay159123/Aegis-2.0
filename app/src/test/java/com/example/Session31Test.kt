package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session 31: smart Ready only and the SF progress checklist. */
class Session31Test {
    private val base = GameCatalog.createPostBasicRecruit("op_s31", OperatorSpecialty.ASSAULT, emptySet(), 0)
        .copy(xp = OperatorRank.SGT.xpRequired, authorizedTier = RankTier.NCO)
    private val sf1 = base.copy(completedMissionIds = setOf("msn_basic_qual", "msn_night_qual", "msn_sere_school", "msn_sf_school"))
    private val sf2 = sf1.copy(completedMissionIds = sf1.completedMissionIds + TacticalCalculator.SERE3_SCHOOL_ID, sfMissionsSucceeded = 3)
    private fun team(op: Operator, n: Int) = (0 until n).map { op.copy(id = "${op.hashCode()}_$it") }

    @Test
    fun readyOnlyChecksTeamRules() {
        val high = GameCatalog.missions.first { TacticalCalculator.isSfOperation(it) && it.risk == MissionRisk.HIGH && !it.isClandestine && it.campaignId == null }
        val low = GameCatalog.missions.first { TacticalCalculator.isSfOperation(it) && it.risk == MissionRisk.LOW }
        val sf1s = team(sf1, 6)
        val oda = listOf(TacticalSquad("oda", "ODA 1", sf1s.map { it.id }, sf1s[0].id, isSpecialForces = true))
        assertTrue(TacticalCalculator.canFieldTeam(low, sf1s, oda))
        assertFalse(TacticalCalculator.canFieldTeam(high, sf1s, oda))
        // Three SF2 and one SF1 make a valid 4-man High team (3 of 4)
        val mixed = team(sf2, 3) + sf1s.take(1)
        val oda2 = listOf(TacticalSquad("oda", "ODA 1", mixed.map { it.id }, mixed[0].id, isSpecialForces = true))
        assertTrue(TacticalCalculator.canFieldTeam(high, mixed, oda2))
        // No ODA at all: nothing SF is ready
        assertFalse(TacticalCalculator.canFieldTeam(low, sf1s, emptyList()))
        // Platoon missions need two squads
        val platoon = GameCatalog.missions.first { it.requiredSquads >= 2 }
        val grunts = (0 until 8).map { base.copy(id = "g$it") }
        val one = listOf(TacticalSquad("a", "Alpha Squad", grunts.map { it.id }, "g0"))
        assertFalse(TacticalCalculator.canFieldTeam(platoon, grunts, one))
    }

    @Test
    fun checklistShowsWhatTheNextTierNeeds() {
        val (next1, steps1) = TacticalCalculator.sfNextTierChecklist(sf1)
        assertEquals(2, next1)
        assertEquals(listOf(false, false), steps1.map { it.done })
        val (next2, steps2) = TacticalCalculator.sfNextTierChecklist(sf2)
        assertEquals(3, next2)
        assertEquals(5, steps2.size)
        assertTrue(steps2.last().done) // SGT
        assertNull(TacticalCalculator.sfNextTierChecklist(base).first)
    }
}
