package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.SquadCohesionRules
import com.example.domain.TacticalCalculator
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session 9: OCS/WOCS either-or, warrant officers, aviation, recruit sources. */
class WarrantAviationTest {

    private val withAirfield = FacilityType.entries.map { FacilityState(it, if (it == FacilityType.AIRFIELD) 1 else 3) }
    private val noAirfield = FacilityType.entries.map { FacilityState(it, if (it == FacilityType.AIRFIELD) 0 else 3) }
    private fun mission(id: String) = GameCatalog.missions.first { it.id == id }

    /** A strong SGT with the basics done: meets WOCS stat floors. */
    private fun sergeant(specialty: OperatorSpecialty = OperatorSpecialty.SIGNALS): Operator =
        GameCatalog.createPostBasicRecruit("op_sgt_${specialty.name}", specialty, emptySet(), 0).copy(
            xp = OperatorRank.SGT.xpRequired,
            authorizedTier = RankTier.NCO,
            baseTech = 30,
            baseResilience = 30,
            baseFirepower = 30,
            baseStealth = 30,
            completedMissionIds = setOf("msn_basic_qual"),
            highestRiskSurvived = MissionRisk.HIGH,
            missionsCompleted = 4
        )

    private fun warrant(grade: Int, specialty: OperatorSpecialty = OperatorSpecialty.SIGNALS): Operator =
        sergeant(specialty).copy(
            xp = OperatorRank.forWarrantGrade(grade).xpRequired,
            authorizedTier = RankTier.WARRANT,
            warrantGrade = grade,
            completedMissionIds = setOf("msn_basic_qual", TacticalCalculator.WOCS_MISSION_ID) +
                if (specialty == OperatorSpecialty.AVIATION) setOf(TacticalCalculator.FLIGHT_SCHOOL_MISSION_ID) else emptySet()
        )

    @Test
    fun warrantRanksFormTheirOwnTrack() {
        // XP alone never makes anyone a warrant officer, and generals still top out at GEN
        assertFalse(OperatorRank.forXp(99_999).isWarrant)
        assertEquals(OperatorRank.GEN, OperatorRank.forXp(99_999))
        assertNull(OperatorRank.nextRank(OperatorRank.GEN))
        assertEquals(OperatorRank.LT2, OperatorRank.nextRank(OperatorRank.SGM))
        assertEquals(
            listOf(OperatorRank.CW2, OperatorRank.CW3, OperatorRank.CW4, OperatorRank.CW5),
            listOf(OperatorRank.WO1, OperatorRank.CW2, OperatorRank.CW3, OperatorRank.CW4).map { OperatorRank.nextRank(it) }
        )
        assertNull(OperatorRank.nextRank(OperatorRank.CW5))
        // Seniority: WO1 above SFC, CW5 below LTC; officers' ceilings unchanged
        assertTrue(OperatorRank.WO1.seniorityIndex > OperatorRank.SFC.seniorityIndex)
        assertTrue(OperatorRank.CW5.seniorityIndex < OperatorRank.LTC.seniorityIndex)
        assertEquals(OperatorRank.BG.xpRequired, OperatorRank.ceilingXpForTier(RankTier.OFFICER))
        assertNull(OperatorRank.ceilingXpForTier(RankTier.FLAG_OFFICER))
        // Warrant officers wear officer uniforms but aren't commissioned
        assertTrue(RankTier.WARRANT.isOfficer)
        assertFalse(OperatorRank.CW3.isCommissioned)
        // A warrant officer's rank comes from the stored grade, not XP
        val cw3 = warrant(3)
        assertEquals(OperatorRank.CW3, cw3.rank)
        assertEquals(OperatorRank.CW3, cw3.copy(xp = 50_000).rank)
        val promoted = cw3.promotedTo(OperatorRank.CW4)
        assertEquals(OperatorRank.CW4, promoted.rank)
        assertEquals(RankTier.WARRANT, promoted.authorizedTier)
        // Every warrant grade is its own board
        assertTrue(cw3.isAtCategoryPromotionGate)
    }

    @Test
    fun ocsAndWocsAreEitherOr() {
        val sgt = sergeant()
        val ocs = mission(TacticalCalculator.OCS_MISSION_ID)
        val wocs = mission(TacticalCalculator.WOCS_MISSION_ID)
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(sgt, wocs, emptySet()).eligible)

        // Started OCS (even a failed attempt): WOCS closed with a clear reason
        val ocsCandidate = sgt.copy(schoolAttempts = mapOf(TacticalCalculator.OCS_MISSION_ID to 1))
        val blocked = TacticalCalculator.canOperatorDeployOnMission(ocsCandidate, wocs, emptySet())
        assertFalse(blocked.eligible)
        assertTrue(blocked.missingRequirements.any { it.contains("OCS") })

        // Started WOCS: OCS closed; a warrant officer can't go to OCS either
        val wocsCandidate = sgt.copy(schoolAttempts = mapOf(TacticalCalculator.WOCS_MISSION_ID to 1))
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(wocsCandidate, ocs, emptySet()).eligible)
        val wo = warrant(1)
        val woOcs = TacticalCalculator.canOperatorDeployOnMission(wo, ocs, emptySet())
        assertFalse(woOcs.eligible)
        assertTrue(woOcs.missingRequirements.any { it.contains("WOCS") })
    }

    @Test
    fun flightSchoolNeedsAWarrantOfficerAndAnAirfield() {
        val flight = mission(TacticalCalculator.FLIGHT_SCHOOL_MISSION_ID)
        val hours = mission(TacticalCalculator.FLIGHT_HOURS_MISSION_ID)
        val wo = warrant(1)
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(wo, flight, emptySet(), facilities = withAirfield).eligible)
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(wo, flight, emptySet(), facilities = noAirfield).eligible)
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(sergeant(), flight, emptySet(), facilities = withAirfield).eligible)

        // Flight Hours: aviators only, repeatable, never a combat deployment
        val pilot = warrant(1, OperatorSpecialty.AVIATION).copy(flightSkill = 10)
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(wo, hours, emptySet(), facilities = withAirfield).eligible)
        val logged = pilot.copy(completedMissionIds = pilot.completedMissionIds + TacticalCalculator.FLIGHT_HOURS_MISSION_ID)
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(logged, hours, emptySet(), facilities = withAirfield).eligible)
        assertEquals(MissionScope.TRAINING, hours.scope)
        assertEquals(0.0, TacticalCalculator.heroismOdds(hours, OperatorOutcomeType.TRAINING_PASSED, true, true), 0.0)

        // Aviators don't join ground teams or squads
        val combat = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT }
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(pilot, combat, emptySet(), facilities = withAirfield).eligible)
        assertFalse(SquadCohesionRules.isOperatorEligibleForFieldSquad(pilot))
        assertTrue(SquadCohesionRules.isOperatorEligibleForFieldSquad(wo))
    }

    @Test
    fun warrantBoardChecksOpsAndFlightSkill() {
        val pilot = warrant(1, OperatorSpecialty.AVIATION).copy(
            xp = OperatorRank.CW2.xpRequired,
            missionsCompleted = 1,
            flightSkill = 10
        )
        val board = TacticalCalculator.evaluatePromotionBoard(pilot, listOf(pilot), emptyList(), emptyList(), withAirfield)
        assertEquals(OperatorRank.CW2, board.nextRank)
        assertTrue(board.isCrossTierGate)
        assertFalse(board.allMeritsMet)
        assertTrue(board.meritChecks.any { it.id == "flight_warrant" && !it.satisfied })

        val ready = pilot.copy(missionsCompleted = 3, flightSkill = TacticalCalculator.warrantFlightSkillRequired(OperatorRank.CW2))
        assertTrue(TacticalCalculator.evaluatePromotionBoard(ready, listOf(ready), emptyList(), emptyList(), withAirfield).allMeritsMet)

        // A seated CCWO makes warrant boards cheaper
        val ccwo = warrant(4).copy(id = "op_ccwo", status = OperatorStatus.HQ_STAFF, hqRole = HqStaffRole.CCWO_COMMAND_CHIEF_WARRANT)
        val full = TacticalCalculator.evaluatePromotionBoard(ready, listOf(ready), emptyList(), emptyList(), withAirfield).creditCost
        val discounted = TacticalCalculator.evaluatePromotionBoard(ready, listOf(ready, ccwo), emptyList(), emptyList(), withAirfield).creditCost
        assertTrue(discounted < full)
    }

    @Test
    fun aviatorWingsUpgradeAndAirMedalRepeats() {
        val wings = GameCatalog.medals.first { it.id == TacticalCalculator.AVIATOR_WINGS_ID }
        val senior = GameCatalog.medals.first { it.id == TacticalCalculator.SENIOR_AVIATOR_WINGS_ID }
        val master = GameCatalog.medals.first { it.id == TacticalCalculator.MASTER_AVIATOR_WINGS_ID }
        val airMedal = GameCatalog.medals.first { it.id == TacticalCalculator.AIR_MEDAL_ID }
        val pilot = warrant(2, OperatorSpecialty.AVIATION).copy(flightSkill = 50)

        assertTrue(TacticalCalculator.evaluateAwardEligibility(wings, pilot, emptySet()).eligible)
        // Senior needs CW3 as well as flight skill
        assertFalse(TacticalCalculator.evaluateAwardEligibility(senior, pilot, setOf(wings.id)).eligible)
        val cw3 = pilot.copy(warrantGrade = 3)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(senior, cw3, setOf(wings.id)).eligible)
        // Once Senior replaces the basic wings, the basic wings aren't offered again
        assertFalse(TacticalCalculator.evaluateAwardEligibility(wings, cw3, setOf(senior.id)).eligible)
        // Master: CW4 and a higher flight skill
        val cw4 = cw3.copy(warrantGrade = 4)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(master, cw4, setOf(senior.id)).eligible)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(master, cw4.copy(flightSkill = 70), setOf(senior.id)).eligible)
        // Aviators prefer their wings on the chest
        assertEquals(0, UniformRegaliaRules.mosBadgePriority(OperatorSpecialty.AVIATION, master.id, ChestBadgeSlot.ABOVE_RACK))

        // Air Medal: every 3 combat sorties as pilot; repeat awards need 3 more each
        assertFalse(TacticalCalculator.evaluateAwardEligibility(airMedal, pilot.copy(combatSortiesFlown = 2), emptySet()).eligible)
        val threeSorties = pilot.copy(combatSortiesFlown = 3)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(airMedal, threeSorties, emptySet()).eligible)
        val held = listOf(AwardedMedal("c1", pilot.id, airMedal.id, "x", awardedAtMillis = 1L))
        assertFalse(TacticalCalculator.evaluateAwardEligibility(airMedal, threeSorties, setOf(airMedal.id), held).eligible)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(airMedal, pilot.copy(combatSortiesFlown = 6), setOf(airMedal.id), held).eligible)
        assertTrue(TacticalCalculator.isRepeatableAward(airMedal))
        assertEquals(TacticalCalculator.MAX_REPEAT_AWARDS, TacticalCalculator.perSoldierCapForAward(airMedal.id))
    }

    @Test
    fun warrantBilletsNeedTheRightWarrantOfficer() {
        val pilotCw2 = warrant(2, OperatorSpecialty.AVIATION).copy(missionsCompleted = 6)
        val techCw4 = warrant(4).copy(missionsCompleted = 8)
        val captain = sergeant().copy(xp = OperatorRank.CPT.xpRequired, authorizedTier = RankTier.OFFICER, missionsCompleted = 8)
        assertTrue(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.AIR_OPS_OFFICER, pilotCw2).eligible)
        assertFalse(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.AIR_OPS_OFFICER, techCw4).eligible)
        assertFalse(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.AIR_OPS_OFFICER, captain).eligible)
        assertTrue(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.CCWO_COMMAND_CHIEF_WARRANT, techCw4).eligible)
        assertFalse(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.CCWO_COMMAND_CHIEF_WARRANT, warrant(3).copy(missionsCompleted = 8)).eligible)
        assertFalse(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.CCWO_COMMAND_CHIEF_WARRANT, pilotCw2.copy(warrantGrade = 4)).eligible)
        // Existing billets still compare by seniority: a WO1 can sit S-1 (SGT+), not CO (CPT+)
        assertTrue(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.S1_PERSONNEL_HONORS, warrant(1)).eligible)
        assertFalse(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.CO_COMMANDING_OFFICER, warrant(1).copy(missionsCompleted = 8)).eligible)

        // Slots: the two new billets open at HQ 6 and 7; the cap still matches the role count
        fun slots(hq: Int) = TacticalCalculator.maxHqStaffSlots(listOf(FacilityState(FacilityType.COMMAND_HQ, hq)))
        assertEquals(8, slots(6))
        assertEquals(HqStaffRole.entries.size, slots(7))
        assertEquals(HqStaffRole.entries.size, slots(10))
        assertEquals(6, HqStaffRole.AIR_OPS_OFFICER.requiredHqLevel)
        assertEquals(7, HqStaffRole.CCWO_COMMAND_CHIEF_WARRANT.requiredHqLevel)
    }

    @Test
    fun technicalWarrantOfficersLiftTheirTeam() {
        val signalsMission = GameCatalog.missions.first {
            it.scope == MissionScope.FIELD_COMBAT && it.primaryFocus.contains("Signals")
        }
        val sgt = sergeant()
        val wo = warrant(2)
        assertEquals(0, TacticalCalculator.technicalWarrantOddsBonus(signalsMission, listOf(sgt)))
        assertEquals(1 + TacticalCalculator.TECH_WARRANT_MOS_PASS_BONUS, TacticalCalculator.technicalWarrantOddsBonus(signalsMission, listOf(sgt, wo)))
        val medicWo = warrant(2, OperatorSpecialty.MEDIC)
        assertEquals(1, TacticalCalculator.technicalWarrantOddsBonus(signalsMission, listOf(medicWo)))
        // Aviators are not technical warrant officers
        assertFalse(warrant(2, OperatorSpecialty.AVIATION).isTechnicalWarrant)
    }

    @Test
    fun recruitSourcesArriveAtTheirRankWithACleanRecord() {
        RecruitSource.entries.forEach { source ->
            val r = GameCatalog.createRecruit("op_${source.name}", OperatorSpecialty.BREACHER, source, emptySet(), 1)
            assertEquals(source.name, source.arrivalRank, r.rank)
            assertEquals(source.name, 0, r.missionsCompleted)
            assertNull(source.name, r.highestRiskSurvived)
            assertTrue(source.name, r.combatPatchHistory.isEmpty())
            source.schoolId?.let { assertTrue(source.name, it in r.completedMissionIds) }
            val awards = GameCatalog.recruitAwardPackage(source, r.specialty)
            // No valor, wound, combat badge or campaign awards from any source
            val banned = setOf("medal_purple_shield", "medal_bronze_valor", "insig_eib_rifle", "insig_cib", "insig_combat_medic", "insig_ranger_tab", "insig_sf_tab", "insig_parachutist")
            assertTrue(source.name, awards.none { it in banned })
            awards.forEach { id -> assertTrue("$id exists", GameCatalog.medals.any { it.id == id }) }
            // One-time awards appear at most once
            awards.filter { TacticalCalculator.perSoldierCapForAward(it) == 1 }.groupBy { it }.values.forEach {
                assertEquals(source.name, 1, it.size)
            }
        }
        // Reception Station is the old behavior: PV2, Basic Training only, no schools
        val pv2 = GameCatalog.createRecruit("op_pv2", OperatorSpecialty.ASSAULT, RecruitSource.RECEPTION, emptySet(), 0)
        assertEquals(listOf("medal_basic_training", "medal_national_defense"), GameCatalog.recruitAwardPackage(RecruitSource.RECEPTION, OperatorSpecialty.ASSAULT))
        assertTrue(pv2.completedMissionIds.isEmpty())
        // SFC comes with the NCO Academy behind them and the MOS school, but no Ranger/SF/Airborne
        val sfc = GameCatalog.createRecruit("op_sfc", OperatorSpecialty.MEDIC, RecruitSource.SENIOR_LEADER_COURSE, emptySet(), 0)
        assertTrue("msn_nco_school" in sfc.completedMissionIds)
        assertTrue("msn_medical_school" in sfc.completedMissionIds)
        assertFalse("msn_ranger_school" in sfc.completedMissionIds)
        assertFalse("msn_sf_school" in sfc.completedMissionIds)
        assertTrue(sfc.baseTech > pv2.baseTech - 5)
        // WOCS Aviator: 153A WO1 with Flight School and a starting flight skill
        val aviator = GameCatalog.createRecruit("op_avn", OperatorSpecialty.ASSAULT, RecruitSource.WOCS_AVIATOR, emptySet(), 0)
        assertEquals(OperatorSpecialty.AVIATION, aviator.specialty)
        assertEquals(OperatorRank.WO1, aviator.rank)
        assertTrue(TacticalCalculator.FLIGHT_SCHOOL_MISSION_ID in aviator.completedMissionIds)
        assertEquals(TacticalCalculator.FLIGHT_SCHOOL_STARTING_SKILL, aviator.flightSkill)
        assertTrue(TacticalCalculator.AVIATOR_WINGS_ID in GameCatalog.recruitAwardPackage(RecruitSource.WOCS_AVIATOR, aviator.specialty))
        // WOCS Technical keeps the chosen MOS
        val tech = GameCatalog.createRecruit("op_tech", OperatorSpecialty.SIGNALS, RecruitSource.WOCS_TECHNICAL, emptySet(), 0)
        assertEquals(OperatorSpecialty.SIGNALS, tech.specialty)
        assertTrue(tech.isTechnicalWarrant)
        // Officer recruit: 2LT on the commissioned path
        val lt = GameCatalog.createRecruit("op_lt", OperatorSpecialty.COMMAND, RecruitSource.OCS, emptySet(), 0)
        assertEquals(OperatorRank.LT2, lt.rank)
        assertEquals(listOf("medal_basic_training"), GameCatalog.recruitAwardPackage(RecruitSource.OCS, OperatorSpecialty.COMMAND).take(1))
    }

    @Test
    fun everyMosHasCordAndBadgePreferences() {
        // Adding Aviation must not leave any per-MOS rule without an entry
        OperatorSpecialty.entries.forEach { mos ->
            val op = GameCatalog.createPostBasicRecruit("op_${mos.name}", mos, emptySet(), 0)
            assertTrue(mos.name, UniformRegaliaRules.leftCordAutoOrder(op).isNotEmpty())
        }
        assertEquals("153A AVN", OperatorSpecialty.AVIATION.roleTag)
    }
}
