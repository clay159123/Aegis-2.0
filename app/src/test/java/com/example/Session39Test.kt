package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.AwardRules
import com.example.domain.CompoundRules
import com.example.domain.SquadCohesionRules
import com.example.domain.TacticalCalculator
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Session39Test {
    private val base = GameCatalog.createPostBasicRecruit("op_s39", OperatorSpecialty.ASSAULT, emptySet(), 0)
        .copy(completedMissionIds = setOf("msn_basic_qual"))
    private fun mission(id: String) = GameCatalog.missions.first { it.id == id }

    @Test
    fun hqStafferOnLeaveKeepsTheSeat() {
        val s1 = base.copy(id = "s1", status = OperatorStatus.ON_LEAVE, hqRole = HqStaffRole.S1_PERSONNEL_HONORS, recoveryUntilMillis = 5_000L)
        assertTrue(s1.holdsHqSeat)
        assertTrue(s1.isHqStaffer)
        // Not a field soldier, so never pruned into or out of a squad as one
        assertFalse(SquadCohesionRules.isOperatorEligibleForFieldSquad(s1))
        assertFalse(CompoundRules.isFieldSoldier(s1))
        // Leaving HQ frees the seat
        assertFalse(s1.copy(status = OperatorStatus.READY, hqRole = null).holdsHqSeat)
        assertFalse(s1.copy(status = OperatorStatus.RETIRED).holdsHqSeat)
    }

    @Test
    fun csmOnLeaveStillWearsCsm() {
        val csm = base.copy(status = OperatorStatus.ON_LEAVE, hqRole = HqStaffRole.CSM_COMMAND_SERGEANT_MAJOR)
            .let { it.copy(xp = 1_000_000, pinnedRank = OperatorRank.SGM) }
        if (csm.earnedRank == OperatorRank.SGM) assertEquals(OperatorRank.CSM, csm.rank)
    }

    @Test
    fun squadMemberOnLeaveStaysOnTheRoster() {
        val ops = (1..4).map { base.copy(id = "m$it") }
        val leave = ops[2].copy(status = OperatorStatus.ON_LEAVE, recoveryUntilMillis = 9_000L)
        val squad = TacticalSquad(id = "sq", name = "Alpha", memberIds = ops.map { it.id }, leaderId = "m1")
        val pruned = SquadCohesionRules.pruneUnavailableMembers(listOf(squad), ops.map { if (it.id == leave.id) leave else it })
        assertTrue(leave.id in pruned.single().memberIds)
        assertEquals("ON LEAVE • ${TacticalCalculator.formatBuildTime(4)}", TacticalCalculator.awayLabel(leave, 5_000L))
        assertNull(TacticalCalculator.awayLabel(ops[0], 5_000L))
    }

    @Test
    fun hqStaffAreClosedOutOfFieldSchools() {
        val hq = base.copy(status = OperatorStatus.HQ_STAFF, hqRole = HqStaffRole.S1_PERSONNEL_HONORS)
        TacticalCalculator.HQ_LOCKED_SCHOOL_IDS.forEach { id ->
            val m = GameCatalog.missions.firstOrNull { it.id == id } ?: return@forEach
            val r = TacticalCalculator.canOperatorDeployOnMission(hq, m, emptySet())
            assertTrue(id, r.missingRequirements.any { it.startsWith("Field school") })
        }
        // A field soldier never sees that reason
        val field = TacticalCalculator.canOperatorDeployOnMission(base, mission("msn_ranger_school"), emptySet())
        assertFalse(field.missingRequirements.any { it.startsWith("Field school") })
        // Leadership stays open to HQ
        val nco = TacticalCalculator.canOperatorDeployOnMission(hq, mission("msn_nco_school"), emptySet())
        assertFalse(nco.missingRequirements.any { it.startsWith("Field school") })
    }

    @Test
    fun heroismOnlyAtDangerousSchools() {
        assertEquals(0.0, AwardRules.soldiersMedalOdds(mission("msn_night_qual"), OperatorOutcomeType.TRAINING_PASSED), 0.0)
        assertEquals(0.0, AwardRules.soldiersMedalOdds(mission("msn_pathfinder_school"), OperatorOutcomeType.TRAINING_PASSED), 0.0)
        assertEquals(AwardRules.DANGEROUS_SCHOOL_HEROISM_ODDS, AwardRules.soldiersMedalOdds(mission("msn_ranger_school"), OperatorOutcomeType.TRAINING_PASSED), 0.0)
        assertTrue(AwardRules.DANGEROUS_SCHOOL_HEROISM_ODDS < AwardRules.SOLDIERS_MEDAL_ODDS)
    }

    @Test
    fun environmentBadgeIgnoresTheSchools() {
        val envIds = TacticalCalculator.ENVIRONMENT_BADGE_IDS
        val schoolIds = TacticalCalculator.ENVIRONMENT_SCHOOLS.map { it.schoolId }.toSet()
        val picks = (0 until 30).map { i ->
            val op = base.copy(id = "env_$i", completedMissionIds = base.completedMissionIds + schoolIds)
            UniformRegaliaRules.environmentBadgeFor(op, envIds)?.id
        }.toSet()
        // Before the fix every soldier tied and got the first badge
        assertTrue(picks.size > 1)
    }

    @Test
    fun terrainsHavePreferredInsertions() {
        val urban = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT && it.theater.terrain == Terrain.URBAN && InsertionMethod.FAST_ROPE in it.allowedInsertions }
        assertTrue(TacticalCalculator.isPreferredInsertion(urban, InsertionMethod.FAST_ROPE))
        assertFalse(TacticalCalculator.isPreferredInsertion(urban, InsertionMethod.GROUND))
        val desert = GameCatalog.missions.firstOrNull { it.scope == MissionScope.FIELD_COMBAT && it.theater.terrain == Terrain.DESERT }
        if (desert != null) {
            assertTrue(TacticalCalculator.isPreferredInsertion(desert, InsertionMethod.GROUND))
            assertEquals(
                TacticalCalculator.PREFERRED_INSERTION_PASS_BONUS,
                TacticalCalculator.insertionPassDelta(desert, InsertionMethod.GROUND, false) -
                    com.example.domain.ClandestineRules.signatureBonus(desert, InsertionMethod.GROUND)
            )
        }
        // Every field mission with more than one way in suits at least one of them, garrison aside
        GameCatalog.missions.filter { it.scope == MissionScope.FIELD_COMBAT && it.theater.terrain != Terrain.GARRISON }.forEach {
            if (it.allowedInsertions.size > 1) {
                assertTrue("${it.id} ${it.theater.terrain} ${it.allowedInsertions}", TacticalCalculator.preferredInsertions(it).isNotEmpty())
            }
        }
    }

    @Test
    fun recruitsWearRibbonsOnAlphas() {
        RecruitSource.entries.forEachIndexed { i, src ->
            val r = GameCatalog.createRecruit("r$i", OperatorSpecialty.ASSAULT, src, emptySet(), i)
            assertNull(src.name, r.wardrobe.alphas.dressDisplayMode)
            assertEquals(src.name, DressDisplayMode.RIBBONS, TacticalCalculator.resolveKitAppearance(r, WardrobeKit.ALPHAS).dressDisplayMode)
        }
        // HQ staff keep full medals on Alphas while on leave
        val away = base.copy(status = OperatorStatus.ON_LEAVE, hqRole = HqStaffRole.S1_PERSONNEL_HONORS)
        assertEquals(DressDisplayMode.FULL_MEDALS, TacticalCalculator.resolveKitAppearance(away, WardrobeKit.ALPHAS).dressDisplayMode)
    }

    @Test
    fun promotableListSkipsTheFallenAndSortsBySeniority() {
        val ops = (0 until 6).map { i -> base.copy(id = "p$i", xp = 2_000 * i) }
        val fallen = ops.map { it.copy(status = OperatorStatus.FALLEN) }
        assertTrue(TacticalCalculator.promotableSoldiers(fallen, emptyList(), emptyList(), emptyList()).isEmpty())
        val list = TacticalCalculator.promotableSoldiers(ops, emptyList(), emptyList(), emptyList())
        assertEquals(list.sortedByDescending { it.rank.seniorityIndex }.map { it.id }, list.map { it.id })
        assertEquals(ops.filter { it.pinEligibleRank != null }.map { it.id }.toSet(), list.filter { it.pinEligibleRank != null }.map { it.id }.toSet())
    }
}
