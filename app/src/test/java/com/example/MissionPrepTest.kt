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

/** Session 10: MOS fit, insertion methods, Combat Diver School, mission prep preview. */
class MissionPrepTest {

    private val facilities = FacilityType.entries.map { FacilityState(it, if (it == FacilityType.AIRFIELD) 2 else 3) }
    private val noAirfield = FacilityType.entries.map { FacilityState(it, if (it == FacilityType.AIRFIELD) 0 else 3) }
    private fun mission(id: String) = GameCatalog.missions.first { it.id == id }

    private fun soldier(id: String, mos: OperatorSpecialty, vararg schools: String) =
        GameCatalog.createPostBasicRecruit(id, mos, emptySet(), 0).copy(
            id = id,
            xp = OperatorRank.SGT.xpRequired,
            authorizedTier = RankTier.NCO,
            completedMissionIds = setOf("msn_basic_qual") + schools,
            highestRiskSurvived = MissionRisk.HIGH,
            missionsCompleted = 4
        )

    private fun pilot(skill: Int) = GameCatalog.createRecruit("op_pilot", OperatorSpecialty.AVIATION, RecruitSource.WOCS_AVIATOR, emptySet(), 0)
        .copy(id = "op_pilot", flightSkill = skill)

    @Test
    fun onlyMissionsWhereMosMattersAreTagged() {
        val field = GameCatalog.missions.filter { it.scope == MissionScope.FIELD_COMBAT }
        assertTrue(field.any { it.recommendedSpecialties.isEmpty() })
        assertTrue(field.any { it.recommendedSpecialties.isNotEmpty() })
        field.forEach { m ->
            assertTrue(m.id, m.recommendedSpecialties.size <= 3)
            // Item 98: Free Strait stages built around HALO or Maritime only allow that insertion
            assertTrue(m.id, InsertionMethod.GROUND in m.allowedInsertions || m.signatureInsertion in m.allowedInsertions)
        }
        // Schools and diplomatic work: no MOS tags, ground only
        GameCatalog.missions.filter { it.scope != MissionScope.FIELD_COMBAT }.forEach { m ->
            assertTrue(m.id, m.recommendedSpecialties.isEmpty())
            assertEquals(m.id, listOf(InsertionMethod.GROUND), m.allowedInsertions)
        }
        // HALO and Maritime are for SF missions only
        GameCatalog.missions.filter { InsertionMethod.HALO in it.allowedInsertions || InsertionMethod.MARITIME in it.allowedInsertions }
            .forEach { assertTrue(it.id, it.requiresSfTeam) }
    }

    @Test
    fun mosCoverageAddsThreePerMosCappedAndUntaggedGivesNothing() {
        val untagged = mission("msn_ridge_patrol")
        val team = listOf(soldier("a", OperatorSpecialty.ASSAULT), soldier("m", OperatorSpecialty.MEDIC))
        assertEquals(0, TacticalCalculator.mosCoverage(untagged, team).second)

        val deep = mission("msn_deep_expedition") // Infantry, Medic, Signals
        assertEquals(6, TacticalCalculator.mosCoverage(deep, team).second)
        // Two medics count once
        assertEquals(6, TacticalCalculator.mosCoverage(deep, team + soldier("m2", OperatorSpecialty.MEDIC)).second)
        val full = team + soldier("s", OperatorSpecialty.SIGNALS)
        assertEquals(9, TacticalCalculator.mosCoverage(deep, full).second)
        // Cap
        val big = deep.copy(recommendedSpecialties = OperatorSpecialty.entries.take(5))
        val everyone = OperatorSpecialty.entries.take(5).mapIndexed { i, mos -> soldier("x$i", mos) }
        assertEquals(TacticalCalculator.MOS_COVERAGE_BONUS_CAP_PCT, TacticalCalculator.mosCoverage(big, everyone).second)
        // It reaches the pass odds
        val without = TacticalCalculator.missionSuccessOddsPct(deep, listOf(soldier("a", OperatorSpecialty.ASSAULT)), emptyList(), emptyList(), facilities)
        val with = TacticalCalculator.missionSuccessOddsPct(deep, listOf(soldier("m", OperatorSpecialty.MEDIC)), emptyList(), emptyList(), facilities)
        assertTrue(with >= without)
    }

    @Test
    fun insertionRequirements() {
        val deep = mission("msn_deep_expedition") // allows Ground, Helicopter, Airborne
        val convoy = mission("msn_convoy_escort") // ground only
        val legs = listOf(soldier("a", OperatorSpecialty.ASSAULT))
        val jumpers = listOf(soldier("j", OperatorSpecialty.ASSAULT, TacticalCalculator.AIRBORNE_SCHOOL_ID))

        assertNull(TacticalCalculator.insertionBlocker(deep, InsertionMethod.GROUND, legs, null, facilities))
        assertNotNull(TacticalCalculator.insertionBlocker(convoy, InsertionMethod.HELICOPTER, legs, pilot(20), facilities))
        // Helicopter: needs an Airfield and a warrant aviator
        assertNotNull(TacticalCalculator.insertionBlocker(deep, InsertionMethod.HELICOPTER, legs, null, facilities))
        assertNotNull(TacticalCalculator.insertionBlocker(deep, InsertionMethod.HELICOPTER, legs, pilot(20), noAirfield))
        assertNotNull(TacticalCalculator.insertionBlocker(deep, InsertionMethod.HELICOPTER, legs, legs.first(), facilities))
        assertNull(TacticalCalculator.insertionBlocker(deep, InsertionMethod.HELICOPTER, legs, pilot(20), facilities))
        // Airborne: every soldier jump-qualified
        assertNotNull(TacticalCalculator.insertionBlocker(deep, InsertionMethod.AIRBORNE, legs + jumpers, null, facilities))
        assertNull(TacticalCalculator.insertionBlocker(deep, InsertionMethod.AIRBORNE, jumpers, null, facilities))
        // Maritime: coastal and Combat Divers (checked even before SF missions allow it)
        val sfMission = deep.copy(allowedInsertions = InsertionMethod.entries, requiresSfTeam = true)
        assertNotNull(TacticalCalculator.insertionBlocker(deep.copy(allowedInsertions = InsertionMethod.entries), InsertionMethod.HALO, legs, null, facilities))
        val divers = listOf(soldier("d", OperatorSpecialty.RECON, "msn_sf_school", TacticalCalculator.COMBAT_DIVER_SCHOOL_ID))
        assertTrue(sfMission.coastal)
        assertNull(TacticalCalculator.insertionBlocker(sfMission, InsertionMethod.MARITIME, divers, null, facilities))
        assertNotNull(TacticalCalculator.insertionBlocker(sfMission, InsertionMethod.MARITIME, legs, null, facilities))
        assertNotNull(TacticalCalculator.insertionBlocker(sfMission.copy(coastal = false), InsertionMethod.MARITIME, divers, null, facilities))
        assertNotNull(TacticalCalculator.insertionBlocker(sfMission, InsertionMethod.HALO, divers, null, facilities))
    }

    @Test
    fun insertionEffects() {
        val stealthy = mission("msn_sa_timber_cache")
        val loud = mission("msn_convoy_escort")
        assertEquals(1.0, TacticalCalculator.insertionDurationMultiplier(InsertionMethod.GROUND, null), 0.0)
        assertEquals(0.75, TacticalCalculator.insertionDurationMultiplier(InsertionMethod.HELICOPTER, pilot(0)), 1e-9)
        assertTrue(TacticalCalculator.insertionDurationMultiplier(InsertionMethod.HELICOPTER, pilot(80)) < 0.75)
        assertEquals(0.6, TacticalCalculator.insertionDurationMultiplier(InsertionMethod.AIRBORNE, null), 0.0)
        assertEquals(0.85, TacticalCalculator.insertionDurationMultiplier(InsertionMethod.MARITIME, null), 0.0)
        // HALO and Maritime only help stealth-weighted missions; scatter always hurts
        assertEquals(15, TacticalCalculator.insertionPassDelta(stealthy, InsertionMethod.HALO, false))
        assertEquals(0, TacticalCalculator.insertionPassDelta(loud, InsertionMethod.HALO, false))
        assertEquals(10, TacticalCalculator.insertionPassDelta(stealthy, InsertionMethod.MARITIME, false))
        assertEquals(-10, TacticalCalculator.insertionPassDelta(loud, InsertionMethod.AIRBORNE, true))
        assertEquals(15, TacticalCalculator.scatterChancePct(InsertionMethod.AIRBORNE))
        assertEquals(5, TacticalCalculator.scatterChancePct(InsertionMethod.HALO))
        // Helicopters cut casualty risk, more with a skilled pilot and an Air Ops officer
        val basic = TacticalCalculator.insertionSurvivalDelta(InsertionMethod.HELICOPTER, pilot(0), 0, false)
        assertTrue(basic > 0)
        assertTrue(TacticalCalculator.insertionSurvivalDelta(InsertionMethod.HELICOPTER, pilot(60), 0, false) > basic)
        assertTrue(TacticalCalculator.insertionSurvivalDelta(InsertionMethod.HELICOPTER, pilot(0), 20, false) >= basic)
        assertTrue(TacticalCalculator.insertionSurvivalDelta(InsertionMethod.AIRBORNE, null, 0, true) < 0)
        // Helicopter supply: Airfield levels and Air Ops make it cheaper
        val lv1 = TacticalCalculator.insertionSupplyCost(InsertionMethod.HELICOPTER, listOf(FacilityState(FacilityType.AIRFIELD, 1)), 0)
        val lv5 = TacticalCalculator.insertionSupplyCost(InsertionMethod.HELICOPTER, listOf(FacilityState(FacilityType.AIRFIELD, 5)), 0)
        val lv5Air = TacticalCalculator.insertionSupplyCost(InsertionMethod.HELICOPTER, listOf(FacilityState(FacilityType.AIRFIELD, 5)), 20)
        assertTrue(lv5 < lv1)
        assertTrue(lv5Air < lv5)
        assertEquals(0, TacticalCalculator.insertionSupplyCost(InsertionMethod.AIRBORNE, facilities, 0))
    }

    @Test
    fun mixingSquadsCutsCohesionForThisMissionOnly() {
        val ids = (1..4).map { "op_m$it" }
        val ops = ids.map { soldier(it, OperatorSpecialty.ASSAULT) }
        val outsider = soldier("op_out", OperatorSpecialty.MEDIC)
        val alpha = TacticalSquad(id = "sq_a", name = "Alpha", memberIds = ids, leaderId = ids[0], cohesionXp = 2000)
        val bravo = TacticalSquad(id = "sq_b", name = "Bravo", memberIds = listOf("op_out", "op_x"), leaderId = "op_out")
        val pure = SquadCohesionRules.evaluateActiveBonus(ops, listOf(alpha, bravo))!!
        val mixed = SquadCohesionRules.evaluateActiveBonus(ops.take(3) + outsider, listOf(alpha, bravo))!!
        assertFalse(pure.mixedSquads)
        assertTrue(mixed.mixedSquads)
        assertTrue(mixed.successOddsBonusPct < pure.successOddsBonusPct || pure.successOddsBonusPct == 0)
        // A soldier with no squad doesn't count as mixing
        val loner = soldier("op_loner", OperatorSpecialty.MEDIC)
        assertFalse(SquadCohesionRules.evaluateActiveBonus(ops.take(3) + loner, listOf(alpha, bravo))!!.mixedSquads)
    }

    @Test
    fun prepPreviewMatchesTheLaunchMath() {
        val deep = mission("msn_deep_expedition")
        val team = listOf(soldier("a", OperatorSpecialty.ASSAULT), soldier("m", OperatorSpecialty.MEDIC))
        val p = pilot(40)
        val ground = TacticalCalculator.previewMissionPlan(deep, team, InsertionMethod.GROUND, null, team, emptyList(), emptyList(), facilities, 0, null, emptyList(), false)
        val helo = TacticalCalculator.previewMissionPlan(deep, team, InsertionMethod.HELICOPTER, p, team + p, emptyList(), emptyList(), facilities, 0, null, emptyList(), false)
        assertNull(helo.insertionBlocker)
        assertTrue(helo.durationSeconds < ground.durationSeconds)
        assertTrue(helo.survivalPct > ground.survivalPct)
        assertTrue(helo.supplyCost > ground.supplyCost)
        assertEquals(6, ground.mosBonusPct)
        assertTrue(ground.breakdown.any { it.first == "MOS coverage" })
        // Untagged missions don't show an MOS row
        val patrol = TacticalCalculator.previewMissionPlan(mission("msn_ridge_patrol"), team, InsertionMethod.GROUND, null, team, emptyList(), emptyList(), facilities, 0, null, emptyList(), false)
        assertTrue(patrol.breakdown.none { it.first == "MOS coverage" })
        assertEquals(TacticalCalculator.AIRBORNE_SCATTER_CHANCE_PCT,
            TacticalCalculator.previewMissionPlan(deep, team, InsertionMethod.AIRBORNE, null, team, emptyList(), emptyList(), facilities, 0, null, emptyList(), false).scatterChancePct)
    }

    @Test
    fun combatDiverSchoolAndBadge() {
        val school = mission(TacticalCalculator.COMBAT_DIVER_SCHOOL_ID)
        val strong = soldier("d", OperatorSpecialty.RECON, "msn_sf_school").copy(baseStealth = 40, baseResilience = 40)
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(strong, school, emptySet()).eligible)
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(strong.copy(completedMissionIds = setOf("msn_basic_qual")), school, emptySet()).eligible)
        assertEquals(2, school.schoolRequirements?.maxAttempts)
        assertEquals(45, school.baseSuccessRatePct)
        val badge = GameCatalog.medals.first { it.id == TacticalCalculator.COMBAT_DIVER_BADGE_ID }
        assertEquals(InsigniaShape.DIVER_BADGE, badge.insigniaShape)
        val diver = strong.copy(completedMissionIds = strong.completedMissionIds + school.id)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(badge, diver, emptySet()).eligible)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(badge, strong, emptySet()).eligible)
        // Ranked just above Master Parachutist on the chest
        val diverRank = com.example.domain.UniformRegaliaRules.mosBadgePriority(OperatorSpecialty.ASSAULT, badge.id, ChestBadgeSlot.ABOVE_RACK)
        val masterRank = com.example.domain.UniformRegaliaRules.mosBadgePriority(OperatorSpecialty.ASSAULT, "insig_master_para", ChestBadgeSlot.ABOVE_RACK)
        assertEquals(masterRank - 1, diverRank)
    }
}
