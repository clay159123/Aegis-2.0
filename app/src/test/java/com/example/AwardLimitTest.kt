package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.AwardRules
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Repeatable awards stop at 21: four silver devices on one ribbon. */
class AwardLimitTest {
    private val op = GameCatalog.createPostBasicRecruit("op_lim", OperatorSpecialty.ASSAULT, emptySet(), 0).copy(missionsCompleted = 30)
    private fun cits(id: String, n: Int) = (1..n).map { AwardedMedal("c$it", op.id, id, "", awardedAtMillis = it.toLong()) }

    @Test
    fun repeatableAwardsCapAtTwentyOne() {
        assertEquals(21, TacticalCalculator.MAX_REPEAT_AWARDS)
        assertEquals(21, TacticalCalculator.perSoldierCapForAward("medal_army_commendation"))
        assertEquals(1, TacticalCalculator.perSoldierCapForAward("insig_cib"))
        val arcom = GameCatalog.medals.first { it.id == "medal_army_commendation" }
        assertTrue(TacticalCalculator.evaluateAwardEligibility(arcom, op, setOf(arcom.id), cits(arcom.id, 20)).eligible)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(arcom, op, setOf(arcom.id), cits(arcom.id, 21)).eligible)
        assertTrue(TacticalCalculator.isAtAwardLimit(op.id, arcom.id, cits(arcom.id, 21)))
        // The 21st award shows four silver oak leaf clusters
        val dev = com.example.domain.UniformRegaliaRules.resolveAwardDevices(op, arcom.id, cits(arcom.id, 21))
        assertEquals(4, dev.silverOakLeaves)
        assertEquals(0, dev.bronzeOakLeaves)
        // Routine service awards stop at the limit too
        val low = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT && it.risk == MissionRisk.LOW }
        val r = AwardRules.serviceAwardsFor(op.copy(goodConductStreak = 5), low, OperatorOutcomeType.SAFE, true,
            mapOf(AwardRules.GOOD_CONDUCT_ID to 21), emptySet(), Random(1))
        assertFalse(AwardRules.GOOD_CONDUCT_ID in r.medalIds)
    }
}
