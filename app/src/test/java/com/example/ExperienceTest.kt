package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session 10b: experience gains (item 40) and combat jumps (item 39). */
class ExperienceTest {

    private fun recruit() = GameCatalog.createPostBasicRecruit("op_e", OperatorSpecialty.ASSAULT, emptySet(), 0)

    @Test
    fun everyNonTrainingMissionTrainsOneOrTwoStats() {
        GameCatalog.missions.forEach { m ->
            if (m.scope == MissionScope.TRAINING) {
                assertTrue(m.id, m.trainsStats.isEmpty())
            } else {
                assertTrue(m.id, m.trainsStats.size in 1..2)
            }
        }
        GameCatalog.missions.filter { it.scope == MissionScope.STAFF_DIPLOMATIC }.forEach {
            assertEquals(it.id, setOf(StatType.TECH), it.trainsStats)
        }
        assertTrue(StatType.STEALTH in GameCatalog.missions.first { it.id == "msn_ridge_patrol" }.trainsStats)
        assertTrue(StatType.TECH in GameCatalog.missions.first { it.id == "msn_relay_sync" }.trainsStats)
        assertTrue(StatType.RESILIENCE in GameCatalog.missions.first { it.id == "msn_sa_medevac_lift" }.trainsStats)
    }

    @Test
    fun pointsByRiskHalfOnFailureNothingWhenFallen() {
        fun m(risk: MissionRisk) = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT }.copy(risk = risk)
        assertEquals(1, TacticalCalculator.missionExperiencePoints(m(MissionRisk.LOW), OperatorOutcomeType.SAFE, true))
        assertEquals(2, TacticalCalculator.missionExperiencePoints(m(MissionRisk.MODERATE), OperatorOutcomeType.WOUNDED, true))
        assertEquals(3, TacticalCalculator.missionExperiencePoints(m(MissionRisk.HIGH), OperatorOutcomeType.SAFE, true))
        assertEquals(4, TacticalCalculator.missionExperiencePoints(m(MissionRisk.EXTREME), OperatorOutcomeType.SAFE, true))
        assertEquals(2, TacticalCalculator.missionExperiencePoints(m(MissionRisk.HIGH), OperatorOutcomeType.SAFE, false))
        assertEquals(1, TacticalCalculator.missionExperiencePoints(m(MissionRisk.LOW), OperatorOutcomeType.SAFE, false))
        assertEquals(0, TacticalCalculator.missionExperiencePoints(m(MissionRisk.EXTREME), OperatorOutcomeType.FALLEN, true))
        val school = GameCatalog.missions.first { it.id == "msn_ranger_school" }
        assertEquals(0, TacticalCalculator.missionExperiencePoints(school, OperatorOutcomeType.TRAINING_PASSED, true))
    }

    @Test
    fun gainsFollowTheThresholdsAndCapAtSix() {
        assertEquals(listOf(0, 1, 1, 2, 3, 4, 5, 6, 6), listOf(3, 4, 9, 10, 18, 28, 40, 54, 999).map { TacticalCalculator.experienceGain(it) })
        assertEquals(4, TacticalCalculator.pointsToNextExperienceGain(0))
        assertEquals(6, TacticalCalculator.pointsToNextExperienceGain(4))
        assertNull(TacticalCalculator.pointsToNextExperienceGain(54))

        // A Moderate-risk rifleman: +1 Firepower after 2 missions, +3 after 9, +6 after 27
        var op = recruit()
        val notes = mutableListOf<String>()
        repeat(27) {
            val (next, note) = TacticalCalculator.applyExperience(op, setOf(StatType.FIREPOWER), 2)
            op = next
            notes += note
            when (it + 1) {
                2 -> assertEquals(1, TacticalCalculator.experienceGain(op.expPointsFirepower))
                9 -> assertEquals(3, TacticalCalculator.experienceGain(op.expPointsFirepower))
            }
        }
        assertEquals(6, TacticalCalculator.experienceGain(op.expPointsFirepower))
        assertTrue(notes[1].contains("Firepower +1 (experience)"))
        assertEquals("", notes[0])

        // Gains are their own fields: base stats untouched, but soldierStats (and the rifle qual score) include them
        val fresh = recruit()
        assertEquals(fresh.baseFirepower, op.baseFirepower)
        val before = TacticalCalculator.soldierStats(fresh, emptyList()).firepower
        val after = TacticalCalculator.soldierStats(op, emptyList()).firepower
        assertEquals(before + 6, after)
        assertEquals(6, TacticalCalculator.rifleQualScore(op, emptyList(), emptyList(), 0) - TacticalCalculator.rifleQualScore(fresh, emptyList(), emptyList(), 0))
    }

    @Test
    fun combatJumpStarsTopOutAtFive() {
        val op = recruit()
        assertEquals(0, TacticalCalculator.combatJumpStars(op))
        // Item 166: one star per 3 combat jumps
        assertEquals(1, TacticalCalculator.combatJumpStars(op.copy(combatJumps = 3)))
        assertEquals(5, TacticalCalculator.combatJumpStars(op.copy(combatJumps = 15)))
    }
}
