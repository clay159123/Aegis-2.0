package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.AwardRules
import com.example.domain.TacticalCalculator
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class Session40Test {
    private val base = GameCatalog.createPostBasicRecruit("op_s40", OperatorSpecialty.ASSAULT, emptySet(), 0)
        .copy(completedMissionIds = setOf("msn_basic_qual"))
    private fun medal(id: String) = GameCatalog.medals.first { it.id == id }
    private fun mission(id: String) = GameCatalog.missions.first { it.id == id }
    private fun gaps(id: String, op: Operator, cites: List<AwardedMedal> = emptyList()) =
        TacticalCalculator.evaluateAwardEligibility(medal(id), op, cites.filter { it.operatorId == op.id }.map { it.medalId }.toSet(), cites).missingRequirements

    // ---------- 191: coat cut ----------
    @Test
    fun snco_and_nco_pass_in_enlisted_coats() {
        listOf(RankTier.SNCO, RankTier.NCO).forEach { tier ->
            assertTrue(UniformRegaliaRules.coatFitsRank(DressUniformStyle.NCO_PARADE_BLUES, tier))
            assertTrue(UniformRegaliaRules.coatFitsRank(DressUniformStyle.ENLISTED_MESS_BLUES, tier))
            assertTrue(UniformRegaliaRules.coatFitsRank(DressUniformStyle.ARMY_SERVICE_GREEN, tier))
            assertFalse(UniformRegaliaRules.coatFitsRank(DressUniformStyle.OFFICER_SERVICE_BLUES, tier))
        }
        assertFalse(UniformRegaliaRules.coatFitsRank(DressUniformStyle.NCO_PARADE_BLUES, RankTier.SOLDIER))
        assertFalse(UniformRegaliaRules.coatFitsRank(DressUniformStyle.ARMY_DRESS_BLUES, RankTier.OFFICER))
        assertTrue(UniformRegaliaRules.coatFitsRank(DressUniformStyle.OFFICER_MESS_BLUES, RankTier.WARRANT))
        assertFalse(UniformRegaliaRules.coatFitsRank(DressUniformStyle.FLAG_STAFF_FORMAL, RankTier.OFFICER))
        assertTrue(UniformRegaliaRules.coatFitsRank(DressUniformStyle.FLAG_STAFF_FORMAL, RankTier.FLAG_OFFICER))
        // Every rank's default coat in every dress kit passes
        OperatorRank.entries.forEach { r ->
            listOf(WardrobeKit.ALPHAS, WardrobeKit.BRAVOS, WardrobeKit.MESS).forEach { k ->
                assertTrue("$r $k", UniformRegaliaRules.coatFitsRank(TacticalCalculator.defaultDressStyleForKit(k, r.tier), r.tier))
            }
        }
    }

    // ---------- 192: warrant boards ----------
    @Test
    fun warrant_board_steps_count_as_boards() {
        var checked = 0
        listOf(OperatorRank.WO1, OperatorRank.CW2, OperatorRank.CW3, OperatorRank.CW4).forEach { r ->
            val next = OperatorRank.nextRank(r) ?: return@forEach
            assertTrue(OperatorRank.isBoardGate(r, next))
            checked++
        }
        assertTrue(checked > 0)
    }

    // ---------- 193: SF combat patch ----------
    @Test
    fun sf_patch_wins_over_later_regular_campaign() {
        val op = base.copy(combatPatchHistory = listOf(
            CombatPatchRecord("camp_a", UnitPatchStyle.SF_ODA2, 1_000L),
            CombatPatchRecord("camp_b", UnitPatchStyle.DIVISION_1, 2_000L)
        ))
        assertEquals(UnitPatchStyle.SF_ODA2, UniformRegaliaRules.resolveCombatPatch(op))
        // Without SF, newest wins; Unassigned never beats a real patch
        val regular = base.copy(combatPatchHistory = listOf(
            CombatPatchRecord("camp_a", UnitPatchStyle.DIVISION_2, 1_000L),
            CombatPatchRecord("camp_b", UnitPatchStyle.UNASSIGNED, 3_000L),
            CombatPatchRecord("camp_c", UnitPatchStyle.DIVISION_1, 2_000L)
        ))
        assertEquals(UnitPatchStyle.DIVISION_1, UniformRegaliaRules.resolveCombatPatch(regular))
    }

    @Test
    fun standalone_ops_count_toward_their_theater_campaign() {
        val standalone = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT && it.campaignId == null && GameCatalog.campaigns.any { c -> c.theater == it.theater } }
        val camp = GameCatalog.campaigns.first { it.theater == standalone.theater }
        assertEquals(camp.id, TacticalCalculator.deploymentCampaignKey(standalone))
        assertEquals(null, TacticalCalculator.deploymentCampaignKey(mission("msn_ranger_school")))
    }

    // ---------- 195: CIB over EIB ----------
    @Test
    fun cib_replaces_eib_on_the_chest() {
        val badges = listOf(medal(UniformRegaliaRules.EIB_ID), medal(UniformRegaliaRules.CIB_ID))
        val worn = UniformRegaliaRules.resolveChestBadgeSlots(base, badges, UniformTailorOverrides()).mapNotNull { it?.id }
        assertTrue(UniformRegaliaRules.CIB_ID in worn)
        assertFalse(UniformRegaliaRules.EIB_ID in worn)
        val eibOnly = UniformRegaliaRules.resolveChestBadgeSlots(base, listOf(medal(UniformRegaliaRules.EIB_ID)), UniformTailorOverrides()).mapNotNull { it?.id }
        assertTrue(UniformRegaliaRules.EIB_ID in eibOnly)
    }

    // ---------- 196: award inflation ----------
    @Test
    fun expeditionary_is_once_per_campaign() {
        val camp = GameCatalog.campaigns.first()
        val stage1 = camp.stageMissionIds[0]
        val stage2 = camp.stageMissionIds[1]
        val vet = base.copy(missionsCompleted = 3, combatMissionsCompleted = 3, highestRiskSurvived = MissionRisk.LOW,
            completedMissionIds = base.completedMissionIds + stage1 + stage2)
        assertEquals(1, TacticalCalculator.deploymentCampaignsServed(vet))
        assertTrue(gaps("medal_expeditionary", vet).isEmpty())
        val one = listOf(AwardedMedal("c1", vet.id, "medal_expeditionary", "", awardedAtMillis = 1L))
        assertTrue(gaps("medal_expeditionary", vet, one).any { it.startsWith("Serve in a new campaign") })
        // A second campaign opens the next one
        val other = GameCatalog.campaigns[1].stageMissionIds.last()
        val two = vet.copy(completedMissionIds = vet.completedMissionIds + other)
        assertTrue(gaps("medal_expeditionary", two, one).isEmpty())
    }

    @Test
    fun senior_awards_have_rank_floors() {
        val career = base.copy(missionsCompleted = 20, staffOpsSupported = 10)
        assertTrue(gaps("medal_meritorious", career.copy(xp = 0)).any { it.contains("SFC") } || career.rank in AwardRules.MSM_RANKS)
        assertTrue(OperatorRank.SSG !in AwardRules.MSM_RANKS && OperatorRank.SFC in AwardRules.MSM_RANKS)
        assertTrue(OperatorRank.LT1 !in AwardRules.LOM_RANKS && OperatorRank.MSG !in AwardRules.LOM_RANKS && OperatorRank.SGM in AwardRules.LOM_RANKS)
        assertTrue(OperatorRank.CW4 !in AwardRules.DSM_RANKS && OperatorRank.COL in AwardRules.DSM_RANKS && OperatorRank.CW5 in AwardRules.DSM_RANKS)
        // Legion of Merit: one per career outside the top seats
        val gap = AwardRules.eligibilityGaps(AwardRules.LEGION_OF_MERIT_ID, career, false, heldCount = 1)
        assertTrue(gap.any { it.startsWith("One per career") })
        val co = career.copy(status = OperatorStatus.HQ_STAFF, hqRole = HqStaffRole.CO_COMMANDING_OFFICER)
        assertFalse(AwardRules.eligibilityGaps(AwardRules.LEGION_OF_MERIT_ID, co, false, heldCount = 1).any { it.startsWith("One per career") })
        // DSM needs the CO or DCG seat
        assertTrue(AwardRules.eligibilityGaps(AwardRules.DSM_ID, career, false, 0).any { it.contains("billet") })
        assertFalse(AwardRules.eligibilityGaps(AwardRules.DSM_ID, co, false, 0).any { it.contains("billet") })
    }

    @Test
    fun schools_dont_advance_good_conduct() {
        val op = base.copy(goodConductStreak = 4)
        val r = AwardRules.serviceAwardsFor(op, mission("msn_ranger_school"), OperatorOutcomeType.TRAINING_PASSED, true, emptyMap(), emptySet(), Random(1))
        assertEquals(4, r.operator.goodConductStreak)
        assertEquals(9, AwardRules.GOOD_CONDUCT_STREAK)
        assertEquals(0.25, AwardRules.ARCOM_CHANCE, 0.0)
        assertEquals(0.30, AwardRules.AAM_CHANCE, 0.0)
    }
}
