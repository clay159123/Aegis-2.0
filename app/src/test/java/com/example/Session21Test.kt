package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.*
import com.example.domain.model.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Session 21: language school and clandestine missions (62), Op Free Strait in 7 stages (98). */
class Session21Test {
    private fun sf(id: String, langs: Set<TheaterRegion> = emptySet(), st: Int = 20, tc: Int = 20) =
        GameCatalog.createPostBasicRecruit(id, OperatorSpecialty.ASSAULT, emptySet(), 0).copy(
            id = id, unlocked = true, baseStealth = st, baseTech = tc, languageRegions = langs,
            completedMissionIds = setOf("msn_basic_qual", TacticalCalculator.SF_SCHOOL_ID, TacticalCalculator.MFF_SCHOOL_ID, TacticalCalculator.COMBAT_DIVER_SCHOOL_ID)
        )
    private fun mission(id: String) = GameCatalog.missions.first { it.id == id }
    private val clan = mission("msn_clan_meridian")

    @After fun reset() {
        ClandestineRules.clearedStageIds = emptySet()
        TacticalCalculator.clearedCampaignIds = emptySet()
    }

    @Test
    fun languageSchoolIsSfOnlyAndRepeatsUntilEveryRegionIsLearned() {
        val school = mission(ClandestineRules.LANGUAGE_SCHOOL_ID)
        assertEquals(setOf(TacticalCalculator.SF_SCHOOL_ID), school.schoolRequirements!!.prerequisiteAllIds)
        val regions = ClandestineRules.languageRegions
        // Item 133: clandestine theaters plus Kestrel Strait
        assertTrue(regions.size >= 4)
        assertEquals((GameCatalog.missions.filter { it.isClandestine }.map { it.theater } + TheaterRegion.KESTREL_STRAIT).toSet(), regions.toSet())
        val plain = sf("p").copy(completedMissionIds = setOf("msn_basic_qual"), baseTech = 20)
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(plain, school, emptySet()).eligible)
        val one = sf("a", setOf(TheaterRegion.MERIDIAN_CITY)).copy(completedMissionIds = sf("a").completedMissionIds + school.id)
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(one, school, emptySet()).eligible)
        val all = one.copy(languageRegions = regions.toSet())
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(all, school, emptySet()).eligible)
        assertEquals(regions.size - 1, ClandestineRules.regionsToLearn(one).size)
    }

    @Test
    fun clandestineTeamsAreSfSquadOnlyWithLanguageSpeakers() {
        val team = (1..4).map { sf("s$it", if (it <= 2) setOf(TheaterRegion.MERIDIAN_CITY) else emptySet()) }
        val squad = TacticalSquad("sq", "ODA", team.map { it.id }, "s1", isSpecialForces = true)
        assertNull(TacticalCalculator.teamBlocker(clan, team, listOf(squad)))
        // One speaker isn't enough
        val oneSpeaker = team.mapIndexed { i, o -> if (i == 1) o.copy(languageRegions = emptySet()) else o }
        assertNotNull(TacticalCalculator.teamBlocker(clan, oneSpeaker, listOf(squad)))
        // Everyone must be in the SF squad
        val outsider = team.take(3) + sf("x", setOf(TheaterRegion.MERIDIAN_CITY))
        assertNotNull(TacticalCalculator.teamBlocker(clan, outsider, listOf(squad.copy(memberIds = squad.memberIds.take(3) + "zz"))))
        assertEquals(listOf(InsertionMethod.GROUND), clan.allowedInsertions)
        assertTrue(clan.durationSeconds >= 2 * ClandestineRules.VERY_HARD_SECONDS)
        assertEquals(3, GameCatalog.missions.count { it.isClandestine && it.campaignId == null })
    }

    @Test
    fun blownCoverOddsAndClock() {
        val weak = (1..4).map { sf("w$it", if (it <= 2) setOf(TheaterRegion.MERIDIAN_CITY) else emptySet()) }
        val strong = (1..4).map { sf("t$it", setOf(TheaterRegion.MERIDIAN_CITY), st = 34, tc = 34) }
        val weakPct = ClandestineRules.blownCoverChancePct(clan, weak)
        assertEquals(ClandestineRules.BASE_BLOWN_COVER_PCT - 2 * ClandestineRules.LANGUAGE_CUT_PCT, weakPct)
        assertTrue(ClandestineRules.blownCoverChancePct(clan, strong) < weakPct)
        assertTrue(ClandestineRules.blownCoverChancePct(clan, strong) >= ClandestineRules.MIN_BLOWN_COVER_PCT)
        assertEquals(weakPct - ClandestineRules.FREE_STRAIT_CUT_PCT, ClandestineRules.blownCoverChancePct(clan, weak, setOf(ClandestineRules.FREE_STRAIT_ID)))
        assertEquals(0, ClandestineRules.blownCoverChancePct(mission("msn_sf_da_hostage"), weak))
        // Roll: 0 means cover holds; otherwise between 25% and 75% of the way in
        val start = 1_000L; val dur = 720_000L
        assertEquals(0L, ClandestineRules.rollCoverBlownAt(0, start, dur, Random(1)))
        val at = ClandestineRules.rollCoverBlownAt(100, start, dur, Random(1))
        assertTrue(at in (start + dur / 4)..(start + dur * 3 / 4))
        // Blowing cover drops the remaining time to a very hard mission's length
        val d = ActiveDeployment("d", clan.id, weak.map { it.id }, start, dur, 100, 100, coverBlownAtMillis = at)
        assertTrue(ClandestineRules.coverBlowsNow(d, at))
        assertFalse(ClandestineRules.coverBlowsNow(d, at - 1))
        val blown = ClandestineRules.blowCover(d, at)
        assertTrue(blown.coverBlown)
        assertEquals(GameTiming.seconds(ClandestineRules.VERY_HARD_SECONDS.toLong()) * 1000L, blown.startedAtMillis + blown.durationMillis - at)
        assertFalse(ClandestineRules.coverBlowsNow(blown, at + 10))
        // Blown cover triples the capture chance
        assertEquals(3.0 * PowRules.captureChancePct(clan, weak[0], false), PowRules.captureChancePct(clan, weak[0], false, blownCover = true), 0.001)
    }

    @Test
    fun freeStraitIsSevenStagesForTheFullSfSquad() {
        val uw = GameCatalog.campaigns.first { it.id == ClandestineRules.FREE_STRAIT_ID }
        assertEquals(7, uw.stageMissionIds.size)
        assertEquals(3000.0, uw.completionBonusCredits, 0.001)
        val stages = uw.stageMissionIds.map { mission(it) }
        stages.forEach {
            assertEquals(it.id, 6, it.minOperators)
            assertEquals(it.id, 6, it.maxOperators)
            assertNotNull(it.id, it.signatureInsertion)
            assertTrue(it.id, it.signatureInsertion in it.allowedInsertions)
        }
        assertEquals(listOf(InsertionMethod.HALO), stages[0].allowedInsertions)
        assertEquals(listOf(InsertionMethod.MARITIME), stages[1].allowedInsertions)
        assertTrue(stages[2].isClandestine)
        assertEquals((1..7).toList(), stages.map { it.campaignStageOrder })
        // Signature insertion bonus
        assertEquals(ClandestineRules.SIGNATURE_INSERTION_BONUS_PCT, ClandestineRules.signatureBonus(stages[0], InsertionMethod.HALO))
        assertEquals(0, ClandestineRules.signatureBonus(stages[3], InsertionMethod.GROUND))
        // The guerrilla force grows with each cleared stage
        ClandestineRules.clearedStageIds = setOf("camp_uw_1", "camp_uw_2", "camp_uw_3")
        assertEquals(3, ClandestineRules.guerrillaStrength())
        assertEquals(6, ClandestineRules.guerrillaPassBonus(stages[3]))
        assertEquals(3, ClandestineRules.guerrillaSurvivalBonus(stages[3]))
        assertEquals(0, ClandestineRules.guerrillaPassBonus(mission("msn_sf_da_hostage")))
        // Completion perk: +5% on all SF missions
        assertEquals(5, ClandestineRules.freeStraitSfBonus(mission("msn_sf_da_hostage"), setOf(ClandestineRules.FREE_STRAIT_ID)))
        assertEquals(0, ClandestineRules.freeStraitSfBonus(mission("msn_ridge_patrol"), setOf(ClandestineRules.FREE_STRAIT_ID)))
    }

    @Test
    fun maritimeNeedsFourDivers() {
        val stage2 = mission("camp_uw_2")
        val divers = (1..4).map { sf("d$it") }
        val swimmers = divers + (1..2).map { sf("n$it").copy(completedMissionIds = setOf("msn_basic_qual", TacticalCalculator.SF_SCHOOL_ID)) }
        assertNull(TacticalCalculator.insertionBlocker(stage2, InsertionMethod.MARITIME, swimmers, null, emptyList()))
        val three = divers.take(3) + (1..3).map { sf("m$it").copy(completedMissionIds = setOf("msn_basic_qual", TacticalCalculator.SF_SCHOOL_ID)) }
        assertNotNull(TacticalCalculator.insertionBlocker(stage2, InsertionMethod.MARITIME, three, null, emptyList()))
    }
}

class Session21LanguageRetryTest {
    @Test
    fun learningMoreRegionsIsNotARetry() {
        val school = GameCatalog.missions.first { it.id == ClandestineRules.LANGUAGE_SCHOOL_ID }
        val op = GameCatalog.createPostBasicRecruit("a", OperatorSpecialty.ASSAULT, emptySet(), 0).copy(
            languageRegions = setOf(TheaterRegion.MERIDIAN_CITY, TheaterRegion.SAHR_EXPANSE, TheaterRegion.KESTREL_STRAIT),
            schoolAttempts = mapOf(school.id to 3)
        )
        assertEquals(0, TacticalCalculator.schoolRetrySurcharge(school, listOf(op), 1.0))
        val failedTwice = op.copy(schoolAttempts = mapOf(school.id to 5))
        assertTrue(TacticalCalculator.schoolRetrySurcharge(school, listOf(failedTwice), 1.0) > 0)
    }
}
