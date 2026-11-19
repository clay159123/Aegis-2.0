package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.SquadCohesionRules
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session 11: platoon missions (item 35) and Special Forces missions with MFF/HALO (item 36). */
class SpecialForcesPlatoonTest {

    private val facilities = FacilityType.entries.map { FacilityState(it, 3) }
    private fun mission(id: String) = GameCatalog.missions.first { it.id == id }

    private fun soldier(id: String, mos: OperatorSpecialty = OperatorSpecialty.ASSAULT, vararg schools: String) =
        GameCatalog.createPostBasicRecruit(id, mos, emptySet(), 0).copy(
            id = id,
            xp = OperatorRank.SGT.xpRequired,
            authorizedTier = RankTier.NCO,
            completedMissionIds = setOf("msn_basic_qual", "msn_night_qual") + schools,
            highestRiskSurvived = MissionRisk.HIGH,
            missionsCompleted = 4
        )

    private fun sf(id: String, mos: OperatorSpecialty = OperatorSpecialty.ASSAULT, vararg extra: String) =
        soldier(id, mos, TacticalCalculator.SF_SCHOOL_ID, *extra)

    private fun lieutenant(id: String) = soldier(id).copy(xp = OperatorRank.LT2.xpRequired, authorizedTier = RankTier.OFFICER)

    @Test
    fun catalogHasSfMissionsOfEveryTypeAndAUwCampaign() {
        val sfMissions = GameCatalog.missions.filter { it.requiresSfTeam }
        assertEquals(
            setOf("Direct Action", "Special Reconnaissance", "Foreign Internal Defense", "Unconventional Warfare"),
            sfMissions.mapNotNull { it.sfMissionType }.toSet()
        )
        sfMissions.forEach {
            assertEquals(it.id, MissionScope.FIELD_COMBAT, it.scope)
            assertEquals(it.id, TacticalCalculator.SF_MIN_TEAM, it.minOperators)
            assertEquals(it.id, TacticalCalculator.SF_MAX_TEAM, it.maxOperators)
            assertTrue(it.id, InsertionMethod.HALO in it.allowedInsertions)
            assertEquals(it.id, it.coastal, InsertionMethod.MARITIME in it.allowedInsertions)
        }
        // Direct Action is High/Extreme; Special Recon long and intel-heavy; FID prestige-heavy
        sfMissions.filter { it.sfMissionType == "Direct Action" }.forEach {
            assertTrue(it.risk == MissionRisk.HIGH || it.risk == MissionRisk.EXTREME)
        }
        sfMissions.filter { it.sfMissionType == "Special Reconnaissance" }.forEach { assertTrue(it.rewardIntel > it.rewardPrestige) }
        sfMissions.filter { it.sfMissionType == "Foreign Internal Defense" }.forEach { assertTrue(it.risk.baseDangerPenalty <= MissionRisk.MODERATE.baseDangerPenalty) }
        // UW is a campaign, endgame after the four theater campaigns, and doesn't block "all campaigns cleared"
        val uw = GameCatalog.campaigns.first { it.id == TacticalCalculator.UW_CAMPAIGN_ID }
        assertTrue(uw.isSpecialForces)
        assertTrue(uw.stageMissionIds.all { id -> GameCatalog.missions.first { it.id == id }.requiresSfTeam })
        val theaters = GameCatalog.campaigns.filter { !it.isSpecialForces }.map { it.id }.toSet()
        assertTrue(TacticalCalculator.isAllCampaignsCleared(theaters))
        assertFalse(TacticalCalculator.isCampaignUnlocked(uw, emptySet()))
    }

    @Test
    fun sfMissionsUnlockWithAnSfSquad() {
        val normal = TacticalSquad("sq1", "Alpha", listOf("a", "b"), "a")
        assertFalse(TacticalCalculator.sfMissionsUnlocked(listOf(normal)))
        assertFalse(TacticalCalculator.sfMissionsUnlocked(listOf(normal.copy(isSpecialForces = true, memberIds = emptyList()))))
        assertTrue(TacticalCalculator.sfMissionsUnlocked(listOf(normal, normal.copy(id = "sq2", isSpecialForces = true))))
    }

    @Test
    fun sfTeamsAreFourToSixQualifiedAndRewardDifferentMos() {
        val da = mission("msn_sf_da_hostage")
        val team = listOf(sf("a"), sf("m", OperatorSpecialty.MEDIC), sf("e", OperatorSpecialty.BREACHER), sf("x", OperatorSpecialty.SIGNALS))
        val sfSquad = listOf(TacticalSquad("sq_sf", "ODA", listOf("a", "m"), "a", isSpecialForces = true))
        assertNull(TacticalCalculator.teamBlocker(da, team, sfSquad))
        assertNotNull(TacticalCalculator.teamBlocker(da, team, emptyList()))
        assertNotNull(TacticalCalculator.teamBlocker(da, team.take(3), sfSquad))
        assertNotNull(TacticalCalculator.teamBlocker(da, team.take(3) + soldier("c"), sfSquad))
        // Four different MOS beat four of the same
        val sameMos = listOf(sf("a"), sf("b"), sf("c"), sf("d"))
        assertNull(TacticalCalculator.teamBlocker(da, sameMos + sf("f") + sf("g"), sfSquad.map { it.copy(memberIds = listOf("a", "b")) }))
        assertEquals(12, TacticalCalculator.mosCoverage(da, team).second)
        assertEquals(3, TacticalCalculator.mosCoverage(da, sameMos).second)
        // Heroism rolls apply (field combat)
        assertTrue(TacticalCalculator.heroismOdds(da, OperatorOutcomeType.SAFE, true, false) > 0.0)
    }

    @Test
    fun haloNeedsAnAllMffTeamOnAnSfMission() {
        val sr = mission("msn_sf_sr_watch")
        val mff = TacticalCalculator.MFF_SCHOOL_ID
        val allMff = (1..4).map { sf("h$it", OperatorSpecialty.RECON, mff) }
        assertNull(TacticalCalculator.insertionBlocker(sr, InsertionMethod.HALO, allMff, null, facilities))
        assertNotNull(TacticalCalculator.insertionBlocker(sr, InsertionMethod.HALO, allMff.take(3) + sf("n"), null, facilities))
        // HALO gives the stealth bonus on a stealth-weighted SR mission
        assertEquals(TacticalCalculator.HALO_STEALTH_PASS_BONUS, TacticalCalculator.insertionPassDelta(sr, InsertionMethod.HALO, false))
        // MFF school: SF only, ~55%, 2 attempts; badge outranks the diver badge and Master Parachutist
        val school = mission(mff)
        assertEquals(55, school.baseSuccessRatePct)
        assertEquals(2, school.schoolRequirements?.maxAttempts)
        assertTrue(TacticalCalculator.SF_SCHOOL_ID in school.schoolRequirements!!.prerequisiteAllIds)
        val badge = GameCatalog.medals.first { it.id == TacticalCalculator.MFF_BADGE_ID }
        assertEquals(InsigniaShape.MFF_WINGS, badge.insigniaShape)
        val rank = { id: String -> com.example.domain.UniformRegaliaRules.mosBadgePriority(OperatorSpecialty.ASSAULT, id, ChestBadgeSlot.ABOVE_RACK) }
        assertTrue(rank(badge.id) < rank(TacticalCalculator.COMBAT_DIVER_BADGE_ID))
        assertTrue(rank(TacticalCalculator.COMBAT_DIVER_BADGE_ID) < rank("insig_master_para"))
    }

    @Test
    fun platoonMissionsNeedTwoSquadsAndAnOfficer() {
        val plt = mission("msn_plt_valley_sweep")
        assertEquals(2, plt.requiredSquads)
        assertEquals(8, plt.minOperators)
        assertEquals(12, plt.maxOperators)
        val alphaIds = (1..4).map { "a$it" }
        val bravoIds = (1..4).map { "b$it" }
        val alpha = TacticalSquad(id = "sq_a", name = "Alpha", memberIds = alphaIds, leaderId = "a1", cohesionXp = 1500)
        val bravo = TacticalSquad(id = "sq_b", name = "Bravo", memberIds = bravoIds, leaderId = "b1", cohesionXp = 1500)
        val lt = lieutenant("lt")
        val team = (alphaIds + bravoIds).map { soldier(it) } + lt
        val squads = listOf(alpha, bravo)

        assertNull(TacticalCalculator.teamBlocker(plt, team, squads))
        assertEquals("lt", TacticalCalculator.platoonLeaderFor(plt, team)?.id)
        // No officer: blocked
        assertNotNull(TacticalCalculator.teamBlocker(plt, team - lt, squads))
        // Only one squad: blocked
        assertNotNull(TacticalCalculator.teamBlocker(plt, alphaIds.map { soldier(it) } + (1..4).map { soldier("x$it") } + lt, squads))
        // A chosen leader who isn't a commissioned officer is refused
        assertNotNull(TacticalCalculator.teamBlocker(plt, team, squads, platoonLeaderId = "a1"))

        // Platoon leader adds pass chance; one-squad missions never get it
        val withLt = TacticalCalculator.missionSuccessOddsPct(plt, team, emptyList(), emptyList(), facilities, squads = squads)
        val withoutLt = TacticalCalculator.missionSuccessOddsPct(plt, team - lt + soldier("filler"), emptyList(), emptyList(), facilities, squads = squads)
        assertTrue(withLt > withoutLt)
        assertNull(TacticalCalculator.platoonLeaderFor(mission("msn_ridge_patrol"), listOf(lt)))

        // Bringing the second squad isn't "mixing" on a platoon mission
        val bonus = SquadCohesionRules.evaluateActiveBonus(team, squads, multiSquadMission = true)
        assertFalse(bonus?.mixedSquads ?: false)

        // Pays about 1.6x a comparable one-squad mission (same risk), XP per soldier unchanged
        val comparable = GameCatalog.missions.filter {
            it.scope == MissionScope.FIELD_COMBAT && it.requiredSquads == 1 && !it.requiresSfTeam && it.risk == plt.risk && it.campaignId == null
        }
        val avgCredits = comparable.map { it.rewardCredits }.average()
        assertTrue(plt.rewardCredits / avgCredits in 1.3..2.0)
    }
}
