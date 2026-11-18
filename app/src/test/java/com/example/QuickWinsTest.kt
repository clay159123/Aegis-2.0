package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.GameTiming
import com.example.domain.TacticalCalculator
import com.example.domain.TimeMode
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session 13: names, starters, SF School at SPC, transfers, time scale. */
class QuickWinsTest {

    @Test
    fun bigNamePoolsWithoutRepeats() {
        assertTrue(GameCatalog.recruitNamePoolSize >= 60 * 60)
        OperatorSpecialty.entries.forEach { assertTrue(it.name, GameCatalog.callsignPoolSize(it) >= 20) }
        // 40 recruits in a row: no repeated names or callsigns
        val names = mutableSetOf<String>()
        val calls = mutableSetOf<String>()
        repeat(40) { i ->
            val r = GameCatalog.createRecruit("r$i", OperatorSpecialty.ASSAULT, RecruitSource.RECEPTION, calls, i, names)
            assertTrue(r.name, names.add(r.name))
            assertTrue(r.callsign, calls.add(r.callsign))
        }
        // Once a callsign pool runs out, numbered callsigns keep them unique
        val full = (0 until 30).fold(mutableSetOf<String>()) { acc, i -> acc.add(GameCatalog.pickCallsign(OperatorSpecialty.MEDIC, acc, i)); acc }
        assertEquals(30, full.size)
    }

    @Test
    fun fourStartersIncludingAn11bAndEarnedAwards() {
        val starters = GameCatalog.initialOperators.filter { it.unlocked }
        assertEquals(4, starters.size)
        val rifleman = starters.first { it.specialty == OperatorSpecialty.ASSAULT }
        assertEquals("11B INF", rifleman.specialty.roleTag)
        // Every starter gets National Defense Service and whatever their record earns
        starters.forEach { op ->
            val earned = TacticalCalculator.earnedStartingAwards(op)
            assertTrue(op.id, "medal_national_defense" in earned)
            earned.forEach { id -> assertFalse("$id should be one-time", TacticalCalculator.isRepeatableAward(GameCatalog.medals.first { it.id == id })) }
        }
        val kestrel = starters.first { it.id == "op_kestrel" }
        assertTrue("insig_ranger_tab" in TacticalCalculator.earnedStartingAwards(kestrel))
        assertTrue("cord_infantry_blue" in TacticalCalculator.earnedStartingAwards(rifleman))
        // Nothing already held is granted twice
        assertFalse("medal_national_defense" in TacticalCalculator.earnedStartingAwards(kestrel, setOf("medal_national_defense")))
        // NDSM is one per soldier
        assertEquals(1, TacticalCalculator.perSoldierCapForAward("medal_national_defense"))
        // Every recruit source brings it
        RecruitSource.entries.forEach { assertTrue(it.name, "medal_national_defense" in GameCatalog.recruitAwardPackage(it, OperatorSpecialty.BREACHER)) }
    }

    @Test
    fun sfSchoolOpensAtSpecialist() {
        val sf = GameCatalog.missions.first { it.id == "msn_sf_school" }
        fun soldier(rank: OperatorRank) = GameCatalog.createPostBasicRecruit("op_s", OperatorSpecialty.ASSAULT, emptySet(), 0).copy(
            xp = rank.xpRequired,
            authorizedTier = rank.tier,
            baseFirepower = 40, baseStealth = 40, baseResilience = 40,
            completedMissionIds = setOf("msn_basic_qual", "msn_night_qual"),
            highestRiskSurvived = MissionRisk.HIGH
        )
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(soldier(OperatorRank.SPC), sf, emptySet()).eligible)
        val pfc = TacticalCalculator.canOperatorDeployOnMission(soldier(OperatorRank.PFC), sf, emptySet())
        assertFalse(pfc.eligible)
        assertTrue(pfc.missingRequirements.any { it.contains("SPC") })
    }

    @Test
    fun transferredSoldiersAreOutOfServiceButKeepTheirNames() {
        assertTrue(OperatorStatus.TRANSFERRED.isOutOfActiveService)
        val op = GameCatalog.createPostBasicRecruit("op_t", OperatorSpecialty.ASSAULT, emptySet(), 0).copy(status = OperatorStatus.TRANSFERRED)
        val combat = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT }
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(op, combat, emptySet()).eligible)
    }

    @Test
    fun timeScaleDefaultsToTestingAndScalesDurations() {
        assertEquals(TimeMode.TESTING, GameTiming.MODE)
        assertEquals(1.0, GameTiming.MODE.durationMultiplier, 0.0)
        assertEquals(30, GameTiming.seconds(30))
        assertEquals(10.0, GameTiming.income(10.0), 0.0)
    }
}
