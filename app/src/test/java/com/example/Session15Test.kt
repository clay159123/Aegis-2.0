package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.AwardRules
import com.example.domain.SquadCohesionRules
import com.example.domain.TacticalCalculator
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Session 15: award all, repeat service awards, companion cords, award groups, new awards, chance valor. */
class Session15Test {
    private val op = GameCatalog.createPostBasicRecruit("op_s15", OperatorSpecialty.ASSAULT, emptySet(), 0)
    private fun medal(id: String) = GameCatalog.medals.first { it.id == id }
    private fun mission(id: String) = GameCatalog.missions.first { it.id == id }

    /** A Random whose doubles are always [v]. */
    private class FixedRandom(private val v: Double) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = v
    }

    @Test
    fun newAwardsSitInRealPrecedence() {
        val ids = listOf(
            "medal_sovereign_star", "medal_distinguished_cross", "medal_distinguished_service", "medal_silver_vanguard",
            "medal_legion_merit", "medal_flying_cross", "medal_soldiers", "medal_bronze_valor", "medal_purple_shield",
            "medal_air_medal", "medal_army_commendation", "medal_army_achievement", "medal_good_conduct",
            "medal_national_defense", "medal_humanitarian", "medal_overseas_service", "medal_nato", "medal_un"
        )
        val order = ids.map { medal(it).precedenceOrder }
        assertEquals(order.sorted(), order)
        assertEquals(order.size, order.toSet().size)
        // Cords and school insignia always sort after the ribbon rack
        GameCatalog.medals.filter { it.category == AwardCategory.SHOULDER_CORD || it.category == AwardCategory.SCHOOL_INSIGNIA }
            .forEach { assertTrue(it.id, it.precedenceOrder > 100) }
        assertEquals(GameCatalog.medals.size, GameCatalog.medals.map { it.id }.toSet().size)
    }

    @Test
    fun oneTimeAndRepeatableAwards() {
        assertFalse(TacticalCalculator.isRepeatableAward(medal(AwardRules.AFSM_ID)))
        assertFalse(TacticalCalculator.isRepeatableAward(medal(AwardRules.OVERSEAS_SERVICE_ID)))
        listOf(AwardRules.DSM_ID, AwardRules.LEGION_OF_MERIT_ID, AwardRules.DFC_ID, AwardRules.SOLDIERS_MEDAL_ID,
            AwardRules.AAM_ID, AwardRules.JSCM_ID, AwardRules.JSAM_ID, AwardRules.HUMANITARIAN_ID, AwardRules.NATO_ID, AwardRules.UN_ID)
            .forEach { assertTrue(it, TacticalCalculator.isRepeatableAward(medal(it))) }
    }

    @Test
    fun serviceStarAwardsUseStarsNotOakLeaves() {
        val cits = (1..3).map { AwardedMedal("c$it", op.id, AwardRules.UN_ID, "", awardedAtMillis = it.toLong()) }
        val dev = UniformRegaliaRules.resolveAwardDevices(op, AwardRules.UN_ID, cits)
        assertEquals(2, dev.bronzeServiceStars)
        assertEquals(0, dev.bronzeOakLeaves)
        assertEquals("3rd award: 2 Bronze Service Star", TacticalCalculator.repeatAwardLabel(op, medal(AwardRules.UN_ID), cits.take(2)))
    }

    @Test
    fun valorIsChanceBasedOnHighAndExtremeOnly() {
        val extreme = mission("msn_deep_expedition")
        // Extreme base 5%: Service Cross under 15%, Silver Vanguard under 25%, Bronze Valor under 50%
        assertEquals(AwardRules.DSC_ID, AwardRules.valorAwardFor(extreme, OperatorOutcomeType.SAFE, true, 0.10))
        assertEquals(AwardRules.SILVER_VANGUARD_ID, AwardRules.valorAwardFor(extreme, OperatorOutcomeType.SAFE, true, 0.20))
        assertEquals(AwardRules.BRONZE_VALOR_ID, AwardRules.valorAwardFor(extreme, OperatorOutcomeType.SAFE, true, 0.40))
        assertNull(AwardRules.valorAwardFor(extreme, OperatorOutcomeType.SAFE, true, 0.55))
        // A wound raises the odds
        assertEquals(AwardRules.BRONZE_VALOR_ID, AwardRules.valorAwardFor(extreme, OperatorOutcomeType.WOUNDED, true, 0.55))
        val moderate = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT && it.risk == MissionRisk.MODERATE }
        assertNull(AwardRules.valorAwardFor(moderate, OperatorOutcomeType.SAFE, true, 0.0))
        // Valor awards need the citation; the valor cord follows a valor award
        assertFalse(TacticalCalculator.evaluateAwardEligibility(medal(AwardRules.BRONZE_VALOR_ID), op, emptySet()).eligible)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(medal(AwardRules.BRONZE_VALOR_ID), op, emptySet(), heroismCited = true).eligible)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(medal(AwardRules.VALOR_CORD_ID), op, emptySet()).eligible)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(medal(AwardRules.VALOR_CORD_ID), op, setOf(AwardRules.BRONZE_VALOR_ID)).eligible)
    }

    @Test
    fun cordsComeWithTheirAward() {
        assertEquals("cord_sf_emerald", AwardRules.companionCordFor("insig_sf_tab"))
        assertEquals("cord_airborne_crimson", AwardRules.companionCordFor("insig_parachutist"))
        assertEquals("cord_infantry_blue", AwardRules.companionCordFor("insig_cib"))
        assertEquals(AwardRules.VALOR_CORD_ID, AwardRules.companionCordFor(AwardRules.SILVER_VANGUARD_ID))
        assertNull(AwardRules.companionCordFor("medal_good_conduct"))
    }

    @Test
    fun goodConductEverySixCleanActions() {
        val m = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT && it.risk == MissionRisk.LOW }
        val veteran = op.copy(missionsCompleted = 5, goodConductStreak = 5)
        val r = AwardRules.serviceAwardsFor(veteran, m, OperatorOutcomeType.SAFE, true, emptyMap(), emptySet(), FixedRandom(0.99))
        assertTrue(AwardRules.GOOD_CONDUCT_ID in r.medalIds)
        assertEquals(0, r.operator.goodConductStreak)
        // A wound resets the streak
        val hurt = AwardRules.serviceAwardsFor(veteran, m, OperatorOutcomeType.WOUNDED, true, emptyMap(), emptySet(), FixedRandom(0.99))
        assertFalse(AwardRules.GOOD_CONDUCT_ID in hurt.medalIds)
        assertEquals(0, hurt.operator.goodConductStreak)
    }

    @Test
    fun arcomAndAamRespectCooldowns() {
        val moderate = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT && it.risk == MissionRisk.MODERATE }
        val vet = op.copy(missionsCompleted = 5, serviceActions = 10)
        val first = AwardRules.serviceAwardsFor(vet, moderate, OperatorOutcomeType.SAFE, true, emptyMap(), emptySet(), FixedRandom(0.0))
        assertTrue(AwardRules.ARCOM_ID in first.medalIds)
        assertEquals(11, first.operator.lastArcomAtAction)
        // Right after: cooldown blocks a second ARCOM
        val second = AwardRules.serviceAwardsFor(first.operator, moderate, OperatorOutcomeType.SAFE, true,
            mapOf(AwardRules.ARCOM_ID to 1), emptySet(), FixedRandom(0.0))
        assertFalse(AwardRules.ARCOM_ID in second.medalIds)
        // A Low mission is a smaller win: Army Achievement Medal
        val low = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT && it.risk == MissionRisk.LOW }
        val aam = AwardRules.serviceAwardsFor(vet, low, OperatorOutcomeType.SAFE, true, emptyMap(), emptySet(), FixedRandom(0.0))
        assertTrue(AwardRules.AAM_ID in aam.medalIds)
    }

    @Test
    fun overseasAndCampaignStyleAwards() {
        val overseas = mission("msn_x_dune_convoy")
        val done = op.copy(missionsCompleted = 2, completedMissionIds = op.completedMissionIds + overseas.id)
        val r = AwardRules.serviceAwardsFor(done, overseas, OperatorOutcomeType.SAFE, true, emptyMap(), emptySet(), FixedRandom(0.99))
        assertTrue(AwardRules.OVERSEAS_SERVICE_ID in r.medalIds)
        // One Humanitarian star per different relief mission
        val relief = mission("msn_x_well_bombing")
        val helped = op.copy(missionsCompleted = 2, completedMissionIds = op.completedMissionIds + relief.id)
        val h1 = AwardRules.serviceAwardsFor(helped, relief, OperatorOutcomeType.SAFE, true, emptyMap(), emptySet(), FixedRandom(0.99))
        assertTrue(AwardRules.HUMANITARIAN_ID in h1.medalIds)
        val h2 = AwardRules.serviceAwardsFor(helped, relief, OperatorOutcomeType.SAFE, true,
            mapOf(AwardRules.HUMANITARIAN_ID to 1), emptySet(), FixedRandom(0.99))
        assertFalse(AwardRules.HUMANITARIAN_ID in h2.medalIds)
    }

    @Test
    fun unitAwardTypes() {
        val ops = (1..4).map { GameCatalog.createPostBasicRecruit("u$it", OperatorSpecialty.ASSAULT, emptySet(), it).copy(id = "u$it") }
        val squad = TacticalSquad(id = "sq", name = "Alpha", memberIds = ops.map { it.id }, leaderId = "u1", missionsTogether = 2)
        val extreme = mission("msn_deep_expedition")
        assertEquals(UnitAwardType.PRESIDENTIAL_UNIT_CITATION, SquadCohesionRules.unitAwardTypeFor(squad, ops, extreme))
        val wounded = ops.mapIndexed { i, o -> if (i == 0) o.copy(status = OperatorStatus.RECOVERING) else o }
        assertEquals(UnitAwardType.VALOROUS_UNIT_AWARD, SquadCohesionRules.unitAwardTypeFor(squad, wounded, extreme))
        val high = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT && it.risk == MissionRisk.HIGH && it.campaignId == null }
        assertEquals(UnitAwardType.MERITORIOUS_UNIT_COMMENDATION, SquadCohesionRules.unitAwardTypeFor(squad, ops, high))
        val low = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT && it.risk == MissionRisk.LOW }
        assertNull(SquadCohesionRules.unitAwardTypeFor(squad, ops, low))
        assertEquals(UnitAwardType.SUPERIOR_UNIT_AWARD, SquadCohesionRules.unitAwardTypeFor(squad.copy(missionsTogether = 5), ops, low))
        // Soldiers wear each type once, counted for clusters, including awards kept from old squads
        val cited = squad.copy(unitCitations = listOf(
            UnitCitation("m", "M", 0L, squad.memberIds, UnitAwardType.MERITORIOUS_UNIT_COMMENDATION),
            UnitCitation("m2", "M2", 1L, squad.memberIds, UnitAwardType.MERITORIOUS_UNIT_COMMENDATION)
        ))
        val worn = UniformRegaliaRules.unitAwardsFor(ops[0].copy(retainedUnitAwards = listOf(UnitAwardType.PRESIDENTIAL_UNIT_CITATION)), listOf(cited))
        assertEquals(listOf(UnitAwardType.PRESIDENTIAL_UNIT_CITATION to 1, UnitAwardType.MERITORIOUS_UNIT_COMMENDATION to 2), worn)
    }

    @Test
    fun awardGroupsCoverEveryAward() {
        assertEquals(AwardRules.AwardGroup.VALOR, AwardRules.groupFor(medal("medal_silver_vanguard")))
        assertEquals(AwardRules.AwardGroup.SERVICE_MEDALS, AwardRules.groupFor(medal("medal_army_commendation")))
        assertEquals(AwardRules.AwardGroup.RIBBONS, AwardRules.groupFor(medal("medal_overseas_service")))
        assertEquals(AwardRules.AwardGroup.TABS, AwardRules.groupFor(medal("insig_ranger_tab")))
        assertEquals(AwardRules.AwardGroup.BADGES, AwardRules.groupFor(medal("insig_cib")))
        assertEquals(AwardRules.AwardGroup.CORDS, AwardRules.groupFor(medal("cord_sf_emerald")))
    }
}
