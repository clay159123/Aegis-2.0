package com.example

import com.example.data.catalog.GameCatalog
import com.example.domain.BoardConsensus
import com.example.domain.GearAutoDistributor
import com.example.domain.SquadCohesionRules
import com.example.domain.TacticalCalculator
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A player-picked cord on each shoulder, stored the way the Tailor Studio saves it */
private fun Operator.withManualCords(left: String?, right: String?): Operator = copy(
    wardrobe = wardrobe.withKit(
        WardrobeKit.ALPHAS,
        wardrobe.alphas.copy(equippedLeftCordId = left, equippedRightCordId = right, cordCustomized = true)
    )
)

class TacticalCalculatorTest {

    @Test
    fun oneTimeAwardsAreCappedAtOnePerSoldierAndRepeatableAwardsAreNot() {
        assertEquals(1, TacticalCalculator.perSoldierCapForAward("medal_basic_training"))
        assertEquals(1, TacticalCalculator.perSoldierCapForAward("medal_sovereign_star"))
        assertEquals(1, TacticalCalculator.perSoldierCapForAward("insig_ranger_tab"))
        assertEquals(1, TacticalCalculator.perSoldierCapForAward("cord_infantry_blue"))
        GameCatalog.medals
            .filter { it.category == AwardCategory.SCHOOL_INSIGNIA || it.category == AwardCategory.SHOULDER_CORD }
            .forEach { assertEquals(it.id, 1, TacticalCalculator.perSoldierCapForAward(it.id)) }
        assertEquals(TacticalCalculator.MAX_REPEAT_AWARDS, TacticalCalculator.perSoldierCapForAward("medal_army_commendation"))
        assertEquals(TacticalCalculator.MAX_REPEAT_AWARDS, TacticalCalculator.perSoldierCapForAward("medal_bronze_valor"))
        assertEquals(TacticalCalculator.MAX_REPEAT_AWARDS, TacticalCalculator.perSoldierCapForAward("medal_purple_shield"))
        assertEquals(TacticalCalculator.MAX_REPEAT_AWARDS, TacticalCalculator.perSoldierCapForAward("medal_good_conduct"))
    }

    @Test
    fun schoolStatsAndOddsIgnoreEquippedGear() {
        val recruit = GameCatalog.initialOperators.first { it.id == "op_cipher" }
        val rifle = GameCatalog.initialGear.first { it.firepowerPerTier > 0 }
            .copy(tier = 5, unlocked = true)
        val facilities = FacilityType.entries.map { FacilityState(it, 5) }
        val armed = recruit.copy(equippedGearIds = mapOf(rifle.slot to rifle.id))
        val withGear = TacticalCalculator.operatorStats(armed, listOf(rifle), emptyList(), facilities)
        val own = TacticalCalculator.soldierStats(recruit, emptyList())
        assertTrue(withGear.firepower > own.firepower)
        assertEquals(TacticalCalculator.operatorStats(recruit, emptyList(), emptyList(), emptyList()), own)
    }

    @Test
    fun fieldSchoolsDressInFieldAndClassroomSchoolsInBravos() {
        val fieldSchools = setOf(
            "msn_basic_qual", "msn_medical_school", "msn_engineer_school", "msn_night_qual",
            "msn_ranger_school", "msn_sf_school", "msn_ocs_school",
            "msn_sapper_school", "msn_pathfinder_school", "msn_eib_testing", "msn_rifle_qual",
            "msn_flight_school", "msn_flight_hours", "msn_combat_diver_school", "msn_mff_school",
            "msn_air_assault_school"
        )
        GameCatalog.missions.filter { it.scope == MissionScope.TRAINING }.forEach { school ->
            val expected = if (school.id in fieldSchools) WardrobeKit.FIELD else WardrobeKit.BRAVOS
            assertEquals(school.id, expected, TacticalCalculator.schoolUniformKit(school))
            assertTrue(TacticalCalculator.missionUniformRequirement(school).isMandatory)
        }
        val kestrel = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
        val hqKestrel = kestrel.copy(status = OperatorStatus.HQ_STAFF, hqRole = HqStaffRole.CO_COMMANDING_OFFICER)
        // Schools override the wardrobe setting, and field schools put HQ staff in Field kit
        assertEquals(
            WardrobeKit.FIELD,
            TacticalCalculator.resolveEffectiveWardrobeKit(hqKestrel, smartWardrobeEnabled = false, DutyUniformContext.FIELD_SCHOOL)
        )
        assertEquals(
            WardrobeKit.BRAVOS,
            TacticalCalculator.resolveEffectiveWardrobeKit(
                kestrel.copy(activeWardrobeKit = WardrobeKit.MESS),
                smartWardrobeEnabled = false,
                DutyUniformContext.TRAINING_MISSION
            )
        )
        val ranger = GameCatalog.missions.first { it.id == "msn_ranger_school" }
        assertEquals(DutyUniformContext.FIELD_SCHOOL, TacticalCalculator.schoolDutyContext(ranger))
    }

    @Test
    fun manualTabPicksStillDrawInPrecedenceOrder() {
        val base = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
        val tabs = GameCatalog.medals.filter {
            it.id in setOf("insig_airborne_tab", "insig_ranger_tab", "insig_sapper_tab")
        }
        val picked = base.copy(
            tailorOverrides = base.tailorOverrides.copy(
                tabSlot1 = "insig_airborne_tab",
                tabSlot2 = "insig_sapper_tab",
                tabSlot3 = "insig_ranger_tab"
            )
        )
        val worn = TacticalCalculator.resolveWornRegalia(picked, tabs)
        assertEquals(
            listOf("insig_ranger_tab", "insig_sapper_tab", "insig_airborne_tab"),
            worn.shoulderTabs.map { it.id }
        )
    }

    @Test
    fun leftShoulderPatchFollowsSquadHqOrUnassigned() {
        val soldier = GameCatalog.initialOperators.first { it.id == "op_cipher" }
        val squadA = TacticalSquad(id = "sq_a", name = "Alpha", memberIds = listOf(soldier.id), leaderId = soldier.id, standardPatch = UnitPatchStyle.DIVISION_3)
        val squadB = TacticalSquad(id = "sq_b", name = "Bravo", memberIds = emptyList(), leaderId = null, standardPatch = UnitPatchStyle.DIVISION_5)
        assertEquals(UnitPatchStyle.DIVISION_3, UniformRegaliaRules.resolveUnitPatch(soldier, listOf(squadA, squadB)))
        // Moving squads changes the patch automatically
        val moved = listOf(squadA.copy(memberIds = emptyList()), squadB.copy(memberIds = listOf(soldier.id)))
        assertEquals(UnitPatchStyle.DIVISION_5, UniformRegaliaRules.resolveUnitPatch(soldier, moved))
        assertEquals(UnitPatchStyle.UNASSIGNED, UniformRegaliaRules.resolveUnitPatch(soldier, emptyList()))
        val hq = soldier.copy(status = OperatorStatus.HQ_STAFF)
        assertEquals(UnitPatchStyle.SUPREME_HQ, UniformRegaliaRules.resolveUnitPatch(hq, listOf(squadA)))
        // A Tailor Studio pick still overrides Auto
        val picked = soldier.copy(tailorOverrides = UniformTailorOverrides(unitPatch = UnitPatchStyle.DIVISION_6))
        assertEquals(UnitPatchStyle.DIVISION_6, UniformRegaliaRules.resolveUnitPatch(picked, listOf(squadA)))
        // New squads get the first division patch nobody is using
        assertEquals(UnitPatchStyle.DIVISION_1, UnitPatchStyle.firstUnusedDivision(listOf(squadA, squadB)))
        assertEquals(
            UnitPatchStyle.DIVISION_2,
            UnitPatchStyle.firstUnusedDivision(listOf(squadA.copy(standardPatch = UnitPatchStyle.DIVISION_1), squadB))
        )
    }

    @Test
    fun combatAndExpertBadgesFollowMosAndTakeSlotOne() {
        val base = GameCatalog.initialOperators.first { it.id == "op_cipher" }
        val infantry = base.copy(specialty = OperatorSpecialty.ASSAULT, combatMissionsCompleted = 1)
        val medic = base.copy(specialty = OperatorSpecialty.MEDIC, combatMissionsCompleted = 1)
        val signals = base.copy(specialty = OperatorSpecialty.SIGNALS, combatMissionsCompleted = 1)
        val sfSignals = signals.copy(completedMissionIds = setOf("msn_sf_school"))
        fun eligible(id: String, op: Operator) =
            TacticalCalculator.evaluateAwardEligibility(GameCatalog.medals.first { it.id == id }, op, emptySet()).eligible

        assertEquals("insig_cib", TacticalCalculator.combatBadgeIdFor(infantry))
        assertEquals("insig_combat_medic", TacticalCalculator.combatBadgeIdFor(medic))
        assertEquals("insig_cab", TacticalCalculator.combatBadgeIdFor(signals))
        assertEquals("insig_cib", TacticalCalculator.combatBadgeIdFor(sfSignals))
        assertTrue(eligible("insig_cib", infantry))
        assertFalse(eligible("insig_cib", infantry.copy(combatMissionsCompleted = 0)))
        assertFalse(eligible("insig_cab", infantry))
        assertTrue(eligible("insig_cab", signals))
        assertTrue(eligible("insig_combat_medic", medic))
        // The EIB needs EIB Testing, not just the Marksmanship school
        assertFalse(eligible("insig_eib_rifle", infantry.copy(completedMissionIds = setOf("msn_basic_qual"))))
        assertTrue(eligible("insig_eib_rifle", infantry.copy(completedMissionIds = setOf("msn_eib_testing"))))
        assertFalse(eligible("insig_eib_rifle", signals.copy(completedMissionIds = setOf("msn_eib_testing"))))

        // Slot 1 wears CIB over EIB for infantry, and the jump wings move down to slot 2
        val badges = GameCatalog.medals.filter { it.id in setOf("insig_eib_rifle", "insig_cib", "insig_master_para") }
        val worn = TacticalCalculator.resolveWornRegalia(infantry, badges)
        assertEquals("insig_cib", worn.chestPinAboveRack?.id)
        assertEquals("insig_master_para", worn.chestPinLeftPocket?.id)
        // Slot 3 catches the leftover EIB instead of sitting empty
        assertEquals("insig_eib_rifle", worn.chestPinRightPocket?.id)
    }

    @Test
    fun sapperRangerAndPathfinderComeFromTheirOwnSchools() {
        val cipher = GameCatalog.initialOperators.first { it.id == "op_cipher" }
        fun eligible(id: String, done: Set<String>) = TacticalCalculator.evaluateAwardEligibility(
            GameCatalog.medals.first { it.id == id }, cipher.copy(completedMissionIds = done, missionsCompleted = 1), emptySet()
        ).eligible
        assertFalse(eligible("insig_sapper_tab", setOf("msn_engineer_school")))
        assertFalse(eligible("insig_sapper_tab", setOf("msn_ranger_school")))
        assertTrue(eligible("insig_sapper_tab", setOf("msn_sapper_school")))
        val sapper = GameCatalog.missions.first { it.id == "msn_sapper_school" }
        assertEquals(setOf(OperatorSpecialty.ASSAULT, OperatorSpecialty.BREACHER), sapper.schoolRequirements?.allowedSpecialties)
        val sf = GameCatalog.missions.first { it.id == "msn_sf_school" }
        assertFalse("msn_ranger_school" in sf.schoolRequirements!!.prerequisiteAllIds)
        assertEquals(
            setOf(OperatorSpecialty.ASSAULT, OperatorSpecialty.RECON, OperatorSpecialty.BREACHER),
            sf.schoolRequirements!!.naturalFitSpecialties
        )
        // Ranger graduation awards only the Ranger Tab, even for Engineers
        val ranger = GameCatalog.missions.first { it.id == "msn_ranger_school" }
        val (rangerAward, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = ranger,
            operator = cipher.copy(specialty = OperatorSpecialty.BREACHER),
            outcome = OperatorOutcomeType.TRAINING_PASSED,
            equippedGear = emptyList(),
            existingCitationMedalIds = emptySet()
        )
        assertEquals("insig_ranger_tab", rangerAward)
    }

    @Test
    fun schoolDeploymentsDoNotUseHqSlots() {
        val school = GameCatalog.missions.first { it.scope == MissionScope.TRAINING }
        val combat = GameCatalog.missions.first { it.scope == MissionScope.FIELD_COMBAT }
        fun dep(id: String, templateId: String) = ActiveDeployment(
            id = id,
            templateId = templateId,
            operatorIds = emptyList(),
            startedAtMillis = 0L,
            durationMillis = 1_000L,
            efficiencyPct = 100,
            avgSurvivalPct = 100
        )
        val deployments = listOf(dep("d1", school.id), dep("d2", school.id), dep("d3", combat.id))
        assertEquals(1, TacticalCalculator.occupiedDeploymentSlots(deployments))
    }

    @Test
    fun facilityProductionAndSlotsScaleWithLevels() {
        val baseFacilities = FacilityType.entries.map { FacilityState(it, 1) }
        val baseRates = TacticalCalculator.productionRates(baseFacilities)
        assertEquals(3.9, baseRates.credits, 0.01)
        assertEquals(2.0, baseRates.supplies, 0.01)
        assertEquals(0.85, baseRates.intel, 0.01)
        assertEquals(2, TacticalCalculator.maxDeploymentSlots(baseFacilities))

        val upgradedFacilities = listOf(
            FacilityState(FacilityType.COMMAND_HQ, 3),
            FacilityState(FacilityType.LOGISTICS_DEPOT, 2),
            FacilityState(FacilityType.RECON_ARRAY, 4),
            FacilityState(FacilityType.ARMORY_WORKSHOP, 2)
        )
        assertEquals(3, TacticalCalculator.maxDeploymentSlots(upgradedFacilities))
        assertTrue(TacticalCalculator.durationMultiplier(upgradedFacilities) < 1.0)
        assertTrue(TacticalCalculator.gearBonusMultiplier(upgradedFacilities) > 1.0)
    }

    @Test
    fun operatorStatsIncludeRankGearAndMedals() {
        val facilities = FacilityType.entries.map { FacilityState(it, 1) }
        val kestrel = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
        val gear = GameCatalog.initialGear
        val citation = AwardedMedal(
            id = "c1",
            operatorId = kestrel.id,
            medalId = "medal_bronze_valor",
            citationNote = "Test citation",
            awardedAtMillis = 0L
        )

        val withoutMedal = TacticalCalculator.operatorStats(kestrel, gear, emptyList(), facilities)
        val withMedal = TacticalCalculator.operatorStats(kestrel, gear, listOf(citation), facilities)

        assertEquals(withoutMedal.firepower + 4, withMedal.firepower)
        assertEquals(withoutMedal.resilience + 2, withMedal.resilience)
        assertEquals(8, TacticalCalculator.operatorYieldBonusPct(kestrel.id, listOf(citation)))
    }

    @Test
    fun armyRankStructureCoversAllFourTiers() {
        assertEquals(OperatorRank.PVT, OperatorRank.forXp(0))
        assertEquals(RankTier.SOLDIER, OperatorRank.PVT.tier)

        assertEquals(OperatorRank.SPC, OperatorRank.forXp(245))
        assertEquals(RankTier.SOLDIER, OperatorRank.SPC.tier)

        assertEquals(OperatorRank.SGT, OperatorRank.forXp(410))
        assertEquals(RankTier.NCO, OperatorRank.SGT.tier)

        assertEquals(OperatorRank.CPT, OperatorRank.forXp(3250))
        assertEquals(RankTier.OFFICER, OperatorRank.CPT.tier)

        assertEquals(OperatorRank.BG, OperatorRank.forXp(7200))
        assertEquals(RankTier.FLAG_OFFICER, OperatorRank.BG.tier)

        assertEquals(OperatorRank.GEN, OperatorRank.forXp(13000))
        assertEquals(RankTier.FLAG_OFFICER, OperatorRank.GEN.tier)

        // Within-category XP advances automatically, but crossing categories is gated until player authorizes it
        assertEquals(OperatorRank.PFC, OperatorRank.forXp(140, RankTier.SOLDIER))
        assertEquals(OperatorRank.SPC, OperatorRank.forXp(380, RankTier.SOLDIER))
        assertEquals(OperatorRank.SPC, OperatorRank.forXp(9999, RankTier.SOLDIER))
        assertEquals(380, OperatorRank.clampXpToAuthorizedTier(500, RankTier.SOLDIER))

        assertEquals(OperatorRank.SSG, OperatorRank.forXp(2500, RankTier.NCO))
        assertEquals(800, OperatorRank.clampXpToAuthorizedTier(2500, RankTier.NCO))

        assertEquals(RankTier.SNCO, OperatorRank.SFC.tier)
        assertEquals(RankTier.SNCO, OperatorRank.MSG.tier)
        assertEquals(RankTier.SNCO, OperatorRank.SGM.tier)
        assertEquals(OperatorRank.SGM, OperatorRank.forXp(2500, RankTier.SNCO))
        assertEquals(2000, OperatorRank.clampXpToAuthorizedTier(2500, RankTier.SNCO))

        assertEquals(OperatorRank.COL, OperatorRank.forXp(8000, RankTier.OFFICER))
        assertEquals(7100, OperatorRank.clampXpToAuthorizedTier(8000, RankTier.OFFICER))

        val spcSoldier = GameCatalog.initialOperators.first { it.id == "op_cipher" }.copy(xp = 380)
        assertEquals(OperatorRank.SPC, spcSoldier.rank)
        assertTrue(spcSoldier.isReadyForNextCategoryPromotion)
        val promotedToSgt = spcSoldier.copy(authorizedTier = RankTier.NCO)
        assertEquals(OperatorRank.SGT, promotedToSgt.rank)
        assertFalse(promotedToSgt.isReadyForNextCategoryPromotion)

        val ssgNco = GameCatalog.initialOperators.first { it.id == "op_vesper" }.copy(xp = 800, authorizedTier = RankTier.NCO)
        assertEquals(OperatorRank.SSG, ssgNco.rank)
        assertTrue(ssgNco.isReadyForNextCategoryPromotion)
        val promotedToSfc = ssgNco.copy(authorizedTier = RankTier.SNCO)
        assertEquals(OperatorRank.SFC, promotedToSfc.rank)
        assertFalse(promotedToSfc.isReadyForNextCategoryPromotion)

        val officerRanks = OperatorRank.entries.filter { it.tier == RankTier.OFFICER }
        assertEquals(
            listOf(OperatorRank.LT2, OperatorRank.LT1, OperatorRank.CPT, OperatorRank.MAJ, OperatorRank.LTC, OperatorRank.COL),
            officerRanks
        )
        assertEquals(6, officerRanks.map { it.insigniaCount }.distinct().size)
        assertFalse(OperatorRank.LT2.isFieldGradeOfficer)
        assertFalse(OperatorRank.LT1.isFieldGradeOfficer)
        assertFalse(OperatorRank.CPT.isFieldGradeOfficer)
        assertTrue(OperatorRank.MAJ.isFieldGradeOfficer)
        assertTrue(OperatorRank.LTC.isFieldGradeOfficer)
        assertTrue(OperatorRank.COL.isFieldGradeOfficer)
    }

    @Test
    fun trainingMissionsGuaranteeSurvivalAndGearImprovesHighRiskOdds() {
        val facilities = FacilityType.entries.map { FacilityState(it, 1) }
        val kestrel = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
        val basicTraining = GameCatalog.missions.first { it.id == "msn_basic_qual" }
        val highRiskOp = GameCatalog.missions.first { it.id == "msn_citadel_recovery" }

        assertEquals(
            100,
            TacticalCalculator.operatorSurvivalPct(basicTraining, kestrel, emptyList(), emptyList(), facilities)
        )

        val unarmoredSurvival = TacticalCalculator.operatorSurvivalPct(
            highRiskOp,
            kestrel,
            emptyList(),
            emptyList(),
            facilities
        )
        val armoredGear = GameCatalog.initialGear
            .filter { it.id in setOf("arm_avs_heavy", "utl_ifak_trauma", "hlm_ops_visor") }
            .map { it.copy(unlocked = true) }
        val armoredSurvival = TacticalCalculator.operatorSurvivalPct(
            highRiskOp,
            kestrel.copy(equippedGearIds = armoredGear.associate { it.slot to it.id }),
            armoredGear,
            emptyList(),
            facilities
        )
        assertTrue(armoredSurvival > unarmoredSurvival)
    }

    @Test
    fun deedRecommendationMapsRibbonsCordsSchoolInsigniaAndMedals() {
        val cipher = GameCatalog.initialOperators.first { it.id == "op_cipher" }
        val basicDrill = GameCatalog.missions.first { it.id == "msn_basic_qual" }
        val nightDrill = GameCatalog.missions.first { it.id == "msn_night_qual" }
        val extremeOp = GameCatalog.missions.first { it.id == "msn_deep_expedition" }

        // First basic drill recommends Army Service Ribbon; once owned, recommends Infantry Blue Shoulder Cord
        val (firstDrillAward, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = basicDrill,
            operator = cipher,
            outcome = OperatorOutcomeType.SAFE,
            equippedGear = emptyList(),
            existingCitationMedalIds = emptySet()
        )
        assertEquals("medal_basic_training", firstDrillAward)

        val (secondDrillAward, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = basicDrill,
            operator = cipher,
            outcome = OperatorOutcomeType.SAFE,
            equippedGear = emptyList(),
            existingCitationMedalIds = setOf("medal_basic_training")
        )
        assertEquals("cord_infantry_blue", secondDrillAward)

        val (airborneFirstAward, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = nightDrill,
            operator = cipher,
            outcome = OperatorOutcomeType.SAFE,
            equippedGear = emptyList(),
            existingCitationMedalIds = emptySet()
        )
        assertEquals("insig_airborne_tab", airborneFirstAward)

        val (airborneSecondAward, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = nightDrill,
            operator = cipher,
            outcome = OperatorOutcomeType.SAFE,
            equippedGear = emptyList(),
            existingCitationMedalIds = setOf("insig_airborne_tab")
        )
        assertEquals("insig_parachutist", airborneSecondAward)

        val (airborneThirdAward, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = nightDrill,
            operator = cipher,
            outcome = OperatorOutcomeType.SAFE,
            equippedGear = emptyList(),
            existingCitationMedalIds = setOf("insig_airborne_tab", "insig_parachutist")
        )
        assertEquals("cord_airborne_crimson", airborneThirdAward)

        val (woundedMedal, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = extremeOp,
            operator = cipher,
            outcome = OperatorOutcomeType.WOUNDED,
            equippedGear = emptyList(),
            existingCitationMedalIds = emptySet()
        )
        assertEquals("medal_purple_shield", woundedMedal)

        val (fallenMedal, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = extremeOp,
            operator = cipher,
            outcome = OperatorOutcomeType.FALLEN,
            equippedGear = emptyList(),
            existingCitationMedalIds = emptySet()
        )
        // Session 15: valor for the fallen comes from the valor roll
        assertEquals("medal_purple_shield", fallenMedal)
    }

    @Test
    fun postBasicRecruitNonInfantrySchoolsAndHqStaffBonusesWorkCorrectly() {
        val facilities = FacilityType.entries.map { FacilityState(it, 1) }
        assertEquals(8, TacticalCalculator.maxFieldRosterSlots(facilities))
        assertEquals(3, TacticalCalculator.maxHqStaffSlots(facilities))

        val baseRates = TacticalCalculator.productionRates(facilities, hqStaffCount = 0)
        val boostedRates = TacticalCalculator.productionRates(facilities, hqStaffCount = 2)
        assertEquals(baseRates.credits * 1.16, boostedRates.credits, 0.01)

        val medicRecruit = GameCatalog.createPostBasicRecruit(
            id = "op_med_test",
            specialty = OperatorSpecialty.MEDIC,
            existingCallsigns = emptySet(),
            seedIndex = 0
        )
        assertEquals(OperatorRank.PV2, medicRecruit.rank)
        assertEquals(DressUniformStyle.ARMY_SERVICE_GREEN, medicRecruit.dressStyle)
        assertEquals(HeadgearStyle.OBSIDIAN_BERET, medicRecruit.headgearStyle)
        assertEquals(null, medicRecruit.equippedLeftCordId)
        assertEquals(0, medicRecruit.missionsCompleted)
        assertEquals(false, medicRecruit.rank.isCareerEligibleForStaffOrTransfer)

        val signalsSchool = GameCatalog.missions.first { it.id == "msn_signals_school" }
        val medicalSchool = GameCatalog.missions.first { it.id == "msn_medical_school" }
        val engineerSchool = GameCatalog.missions.first { it.id == "msn_engineer_school" }
        val logisticsSchool = GameCatalog.missions.first { it.id == "msn_logistics_school" }
        val intelSchool = GameCatalog.missions.first { it.id == "msn_intel_school" }

        val (sigAward, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = signalsSchool,
            operator = medicRecruit.copy(specialty = OperatorSpecialty.SIGNALS),
            outcome = OperatorOutcomeType.SAFE,
            equippedGear = emptyList(),
            existingCitationMedalIds = setOf("medal_basic_training")
        )
        assertEquals("insig_cyber_crest", sigAward)

        val (medAward, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = medicalSchool,
            operator = medicRecruit,
            outcome = OperatorOutcomeType.SAFE,
            equippedGear = emptyList(),
            existingCitationMedalIds = setOf("medal_basic_training")
        )
        assertEquals("insig_cmb_medic", medAward)

        val (engAward, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = engineerSchool,
            operator = medicRecruit.copy(specialty = OperatorSpecialty.BREACHER),
            outcome = OperatorOutcomeType.SAFE,
            equippedGear = emptyList(),
            existingCitationMedalIds = setOf("medal_basic_training")
        )
        assertEquals("insig_eod_badge", engAward)

        val (logAward, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = logisticsSchool,
            operator = medicRecruit,
            outcome = OperatorOutcomeType.SAFE,
            equippedGear = emptyList(),
            existingCitationMedalIds = setOf("medal_basic_training")
        )
        assertEquals("insig_logistics_crest", logAward)

        val (intAward, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = intelSchool,
            operator = medicRecruit.copy(specialty = OperatorSpecialty.RECON),
            outcome = OperatorOutcomeType.SAFE,
            equippedGear = emptyList(),
            existingCitationMedalIds = setOf("medal_basic_training")
        )
        assertEquals("insig_intel_crest", intAward)

        val vesper = GameCatalog.initialOperators.first { it.id == "op_vesper" }
        assertTrue(vesper.rank.isCareerEligibleForStaffOrTransfer)

        val kestrel = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
        val (staffAward, _) = TacticalCalculator.recommendedMedalForHqStaffSupport(
            operator = kestrel.copy(status = OperatorStatus.HQ_STAFF),
            mission = GameCatalog.missions.first { it.id == "msn_convoy_escort" },
            existingCitationMedalIds = setOf("medal_basic_training", "cord_general_aiguillette")
        )
        assertEquals("insig_staff_crest", staffAward)

        // When all eligible staff/service awards are already owned or pending, return null (no duplicate spam)
        val (saturatedStaffAward, _) = TacticalCalculator.recommendedMedalForHqStaffSupport(
            operator = kestrel.copy(status = OperatorStatus.HQ_STAFF, staffOpsSupported = 20),
            mission = GameCatalog.missions.first { it.id == "msn_convoy_escort" },
            existingCitationMedalIds = setOf(
                "medal_basic_training",
                "cord_general_aiguillette",
                "insig_staff_crest",
                "medal_defense_superior",
                "medal_nco_dev",
                "medal_meritorious",
                "medal_army_commendation",
                "insig_logistics_crest",
                "medal_good_conduct"
            )
        )
        assertEquals(null, saturatedStaffAward)
    }

    @Test
    fun dressUniformTierEligibilityAllowsLowerAndCurrentTiers() {
        val kestrel = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
        assertEquals(RankTier.OFFICER, kestrel.rank.tier)
        val kestrelOwned = setOf("medal_basic_training", "medal_bronze_valor", "insig_ranger_tab", "cord_general_aiguillette")
        assertTrue(TacticalCalculator.evaluateDressStyleEligibility(DressUniformStyle.NCO_PARADE_BLUES, kestrel, kestrelOwned).eligible)
        assertTrue(TacticalCalculator.evaluateDressStyleEligibility(DressUniformStyle.OFFICER_SERVICE_BLUES, kestrel, kestrelOwned).eligible)
        assertTrue(TacticalCalculator.evaluateDressStyleEligibility(DressUniformStyle.HONOR_GUARD_IVORY, kestrel, kestrelOwned).eligible)
        assertFalse(TacticalCalculator.evaluateDressStyleEligibility(DressUniformStyle.FLAG_STAFF_FORMAL, kestrel, kestrelOwned).eligible)

        // Every rank tier (Soldier, NCO, Officer, Flag Officer) must have at least one Service uniform AND one Mess uniform
        val sampleByTier = listOf(
            GameCatalog.initialOperators.first { it.id == "op_cipher" },  // SOLDIER (0 missions)
            GameCatalog.initialOperators.first { it.id == "op_vesper" },  // NCO
            kestrel,                                                       // OFFICER
            GameCatalog.initialOperators.first { it.id == "op_specter" }  // FLAG_OFFICER
        )
        sampleByTier.forEach { sampleOp ->
            val unlocked = DressUniformStyle.entries.filter { style ->
                TacticalCalculator.evaluateDressStyleEligibility(style, sampleOp, emptySet()).eligible
            }
            assertTrue(
                "Expected ${sampleOp.rank.tier} to have a Service uniform unlocked",
                unlocked.any { !it.isCutawayMess }
            )
            assertTrue(
                "Expected ${sampleOp.rank.tier} to have a Mess uniform unlocked",
                unlocked.any { it.isCutawayMess }
            )
        }
    }

    @Test
    fun uniformItemsAndAwardsEnforceStrictServiceRequirements() {
        val cipher = GameCatalog.initialOperators.first { it.id == "op_cipher" }
        val cipherMedals = setOf("medal_basic_training")

        // Fresh post-Basic soldier cannot wear ASU Blues, Ceremonial Sash, White Gloves, Tan Beret, or Maroon Beret
        assertTrue(TacticalCalculator.evaluateDressStyleEligibility(DressUniformStyle.ARMY_SERVICE_GREEN, cipher, cipherMedals).eligible)
        assertFalse(TacticalCalculator.evaluateDressStyleEligibility(DressUniformStyle.ARMY_DRESS_BLUES, cipher, cipherMedals).eligible)
        assertFalse(TacticalCalculator.evaluateDressSashEligibility(cipher).eligible)
        assertFalse(TacticalCalculator.evaluateWhiteGlovesEligibility(cipher, cipherMedals).eligible)
        assertFalse(TacticalCalculator.evaluateHeadgearEligibility(HeadgearStyle.TAN_BERET, cipher, cipherMedals).eligible)
        assertFalse(TacticalCalculator.evaluateHeadgearEligibility(HeadgearStyle.MAROON_BERET, cipher, cipherMedals).eligible)
        assertFalse(TacticalCalculator.evaluateHeadgearEligibility(HeadgearStyle.EMERALD_BERET, cipher, cipherMedals).eligible)

        // Purple Shield of Sacrifice strictly requires being wounded or fallen — never just any safe mission
        val purpleShield = GameCatalog.medals.first { it.id == "medal_purple_shield" }
        val uninjuredVeteran = cipher.copy(missionsCompleted = 5, highestRiskSurvived = MissionRisk.HIGH, timesWounded = 0)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(purpleShield, uninjuredVeteran, cipherMedals).eligible)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(purpleShield, uninjuredVeteran.copy(timesWounded = 1), cipherMedals).eligible)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(purpleShield, cipher.copy(status = OperatorStatus.FALLEN), cipherMedals).eligible)

        // Ranger Tab requires Army Ranger & Sapper Course; Tan Beret requires Ranger Tab
        val rangerTab = GameCatalog.medals.first { it.id == "insig_ranger_tab" }
        assertFalse(TacticalCalculator.evaluateAwardEligibility(rangerTab, cipher, cipherMedals).eligible)
        val rangerGraduate = cipher.copy(completedMissionIds = setOf("msn_ranger_school"), missionsCompleted = 1)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(rangerTab, rangerGraduate, cipherMedals).eligible)
        assertTrue(TacticalCalculator.evaluateHeadgearEligibility(HeadgearStyle.TAN_BERET, rangerGraduate, cipherMedals + "insig_ranger_tab").eligible)

        // Signals Academy unlocks Cyber & Intel Crests; Airborne School unlocks Jump Wings & Maroon Beret
        val cyberCrest = GameCatalog.medals.first { it.id == "insig_cyber_crest" }
        assertFalse(TacticalCalculator.evaluateAwardEligibility(cyberCrest, cipher, cipherMedals).eligible)
        val sigGrad = cipher.copy(completedMissionIds = setOf("msn_signals_school"), missionsCompleted = 1)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(cyberCrest, sigGrad, cipherMedals).eligible)

        val jumpWings = GameCatalog.medals.first { it.id == "insig_parachutist" }
        assertFalse(TacticalCalculator.evaluateAwardEligibility(jumpWings, sigGrad, cipherMedals).eligible)
        val airborneGrad = cipher.copy(completedMissionIds = setOf("msn_night_qual"), missionsCompleted = 1)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(jumpWings, airborneGrad, cipherMedals).eligible)
        assertTrue(TacticalCalculator.evaluateHeadgearEligibility(HeadgearStyle.MAROON_BERET, airborneGrad, cipherMedals + "insig_parachutist").eligible)
    }

    @Test
    fun hqRolesUnlockWithCommandHqAndApplyRoleSpecificBonuses() {
        val baseFacilities = FacilityType.entries.map { FacilityState(it, 1) }
        val lv1Roles = TacticalCalculator.unlockedHqRoles(baseFacilities)
        assertEquals(
            listOf(
                HqStaffRole.CO_COMMANDING_OFFICER,
                HqStaffRole.XO_CHIEF_OF_STAFF,
                HqStaffRole.S1_PERSONNEL_HONORS
            ),
            lv1Roles
        )

        val lv5Facilities = baseFacilities.map {
            if (it.type == FacilityType.COMMAND_HQ) it.copy(level = 5) else it
        }
        assertEquals(7, TacticalCalculator.maxHqStaffSlots(lv5Facilities))
        assertEquals(7, TacticalCalculator.unlockedHqRoles(lv5Facilities).size)

        val cipher = GameCatalog.initialOperators.first { it.id == "op_cipher" }
        val vesper = GameCatalog.initialOperators.first { it.id == "op_vesper" }
        val kestrel = GameCatalog.initialOperators.first { it.id == "op_kestrel" }

        // PV2 cannot hold any HQ role
        assertFalse(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.S1_PERSONNEL_HONORS, cipher).eligible)

        // Experienced SGT (E-5, 3 ops) qualifies for S-1, but NOT CO (CPT+), XO (1LT+), or S-3 (SSG+ & Ranger School)
        assertTrue(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.S1_PERSONNEL_HONORS, vesper).eligible)
        assertFalse(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.XO_CHIEF_OF_STAFF, vesper).eligible)
        assertFalse(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.CO_COMMANDING_OFFICER, vesper).eligible)
        assertFalse(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.S3_OPS_DIRECTOR, vesper).eligible)

        // Specialized school & exact rank gating for S-3, S-2, and S-4
        val s3Candidate = vesper.copy(xp = OperatorRank.SSG.xpRequired, completedMissionIds = setOf("msn_ranger_school"))
        assertTrue(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.S3_OPS_DIRECTOR, s3Candidate).eligible)

        assertFalse(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.S2_INTEL_OVERWATCH, vesper).eligible)
        val s2SignalsCandidate = vesper.copy(completedMissionIds = setOf("msn_signals_school"))
        val s2IntelCandidate = vesper.copy(completedMissionIds = setOf("msn_intel_school"))
        assertTrue(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.S2_INTEL_OVERWATCH, s2SignalsCandidate).eligible)
        assertTrue(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.S2_INTEL_OVERWATCH, s2IntelCandidate).eligible)

        assertFalse(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.S4_LOGISTICS_MEDEVAC, vesper).eligible)
        val s4LogCandidate = vesper.copy(xp = OperatorRank.SSG.xpRequired, completedMissionIds = setOf("msn_logistics_school"))
        val s4MedCandidate = vesper.copy(xp = OperatorRank.SSG.xpRequired, completedMissionIds = setOf("msn_medical_school"))
        assertTrue(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.S4_LOGISTICS_MEDEVAC, s4LogCandidate).eligible)
        assertTrue(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.S4_LOGISTICS_MEDEVAC, s4MedCandidate).eligible)

        // CPT Officer (O-3, 5 ops) qualifies for Commanding Officer (CO) & XO
        assertTrue(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.CO_COMMANDING_OFFICER, kestrel).eligible)
        assertTrue(TacticalCalculator.evaluateHqRoleEligibility(HqStaffRole.XO_CHIEF_OF_STAFF, kestrel).eligible)

        val hqOps = listOf(
            kestrel.copy(status = OperatorStatus.HQ_STAFF, hqRole = HqStaffRole.CO_COMMANDING_OFFICER),
            s3Candidate.copy(status = OperatorStatus.HQ_STAFF, hqRole = HqStaffRole.S3_OPS_DIRECTOR)
        )
        val summary = TacticalCalculator.summarizeHqStaff(hqOps)
        assertEquals(2, summary.staffCount)
        assertEquals(26, summary.creditFlowPct)
        assertEquals(24, summary.supplyFlowPct)
        assertEquals(8, summary.durationReductionPct)
        assertEquals(16, summary.efficiencyBonusPct)
        assertEquals(5, summary.survivalBonusPct)
        assertEquals(15, summary.prestigeBonusPct)

        val baseRates = TacticalCalculator.productionRates(baseFacilities)
        val staffRates = TacticalCalculator.productionRates(baseFacilities, summary.staffCount, summary)
        assertEquals(baseRates.credits * 1.26, staffRates.credits, 0.01)
        assertEquals(baseRates.supplies * 1.24, staffRates.supplies, 0.01)
        assertEquals(0.92, TacticalCalculator.durationMultiplier(baseFacilities, summary), 0.001)
    }

    @Test
    fun hqStaffCanAttendSchoolsAndDiplomaticOpsButNotFieldCombat() {
        val kestrel = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
        val kestrelCo = kestrel.copy(status = OperatorStatus.HQ_STAFF, hqRole = HqStaffRole.CO_COMMANDING_OFFICER)

        val schoolMission = GameCatalog.missions.first { it.id == "msn_logistics_school" }
        val signalsOnlySchool = GameCatalog.missions.first { it.id == "msn_signals_school" }
        val diploMission = GameCatalog.missions.first { it.id == "msn_diplo_summit" }
        val fieldCombatMission = GameCatalog.missions.first { it.id == "msn_ridge_patrol" }

        assertTrue(TacticalCalculator.canOperatorDeployOnMission(kestrelCo, schoolMission, emptySet()).eligible)
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(kestrelCo, signalsOnlySchool, emptySet()).eligible)
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(kestrelCo, diploMission, emptySet()).eligible)
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(kestrelCo, fieldCombatMission, emptySet()).eligible)
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(kestrelCo, diploMission, setOf(kestrelCo.id)).eligible)
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(kestrel, fieldCombatMission, emptySet()).eligible)

        // Command Staff Gold Aiguillette strictly requires active HQ Staff status, not field COMMAND MOS
        val goldAiguillette = GameCatalog.medals.first { it.id == "cord_general_aiguillette" }
        assertNull(kestrel.equippedRightCordId)
        assertEquals("cord_infantry_blue", kestrel.equippedLeftCordId)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(goldAiguillette, kestrel, emptySet()).eligible)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(goldAiguillette, kestrelCo, emptySet()).eligible)
    }

    @Test
    fun specialtySchoolsEnforcePrerequisitesSuitabilityAttemptLimitsAndDifficultyOdds() {
        val facilities = FacilityType.entries.map { FacilityState(it, 1) }
        val basicSchool = GameCatalog.missions.first { it.id == "msn_basic_qual" }
        val signalsSchool = GameCatalog.missions.first { it.id == "msn_signals_school" }
        val airborneSchool = GameCatalog.missions.first { it.id == "msn_night_qual" }
        val rangerSchool = GameCatalog.missions.first { it.id == "msn_ranger_school" }

        val assaultRecruit = GameCatalog.createPostBasicRecruit(
            id = "op_assault_test",
            specialty = OperatorSpecialty.ASSAULT,
            existingCallsigns = emptySet(),
            seedIndex = 0
        )
        val cipher = GameCatalog.initialOperators.first { it.id == "op_cipher" }
        val vesper = GameCatalog.initialOperators.first { it.id == "op_vesper" }

        // Suitability check: Assault PV2 (baseTech = 8 -> total Tech 9) is not suited for Signals Academy (14+ Tech)
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(assaultRecruit, signalsSchool, emptySet()).eligible)
        // Signals PV2 Cipher (baseTech = 15 -> total Tech 16) IS suited for Signals Academy
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(cipher, signalsSchool, emptySet()).eligible)

        // Prerequisite check: Airborne requires 1 entry school; Assault recruit cannot jump straight into Airborne
        val suitedAssault = assaultRecruit.copy(baseStealth = 13, baseResilience = 13)
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(suitedAssault, airborneSchool, emptySet()).eligible)
        val postBasicAssault = suitedAssault.copy(completedMissionIds = setOf("msn_basic_qual"))
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(postBasicAssault, airborneSchool, emptySet()).eligible)
        // Once graduated from Basic, Basic cannot be repeated
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(postBasicAssault, basicSchool, emptySet()).eligible)

        // Ranger School requires BOTH Basics and Airborne/Night + 16 FP & 15 Res
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(postBasicAssault, rangerSchool, emptySet()).eligible)
        // Cipher with prereqs but low FP/Res is not suited for Ranger School
        val prereqCipher = cipher.copy(completedMissionIds = setOf("msn_basic_qual", "msn_night_qual"))
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(prereqCipher, rangerSchool, emptySet()).eligible)
        // Experienced SGT Vesper with both prereqs & stats IS eligible for Ranger School
        val qualifiedRangerCandidate = vesper.copy(
            completedMissionIds = setOf("msn_basic_qual", "msn_night_qual"),
            baseFirepower = 15,
            baseResilience = 14
        )
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(qualifiedRangerCandidate, rangerSchool, emptySet()).eligible)

        // Item 101: attempts past a school's old limit stay open (at extra cost); Basic is unlimited
        assertTrue(
            TacticalCalculator.canOperatorDeployOnMission(
                cipher.copy(schoolAttempts = mapOf("msn_signals_school" to 2)),
                signalsSchool,
                emptySet()
            ).eligible
        )
        assertTrue(
            TacticalCalculator.canOperatorDeployOnMission(
                cipher.copy(schoolAttempts = mapOf("msn_signals_school" to 3)),
                signalsSchool,
                emptySet()
            ).eligible
        )
        assertTrue(
            TacticalCalculator.canOperatorDeployOnMission(
                qualifiedRangerCandidate.copy(schoolAttempts = mapOf("msn_ranger_school" to 2)),
                rangerSchool,
                emptySet()
            ).eligible
        )
        assertTrue(
            TacticalCalculator.canOperatorDeployOnMission(
                assaultRecruit.copy(schoolAttempts = mapOf("msn_basic_qual" to 5)),
                basicSchool,
                emptySet()
            ).eligible
        )

        // Pass rate scales with school difficulty and rank/MOS suitability
        val basicOdds = TacticalCalculator.schoolPassOddsPct(basicSchool, assaultRecruit, emptyList())
        val rangerOdds = TacticalCalculator.schoolPassOddsPct(rangerSchool, qualifiedRangerCandidate, emptyList())
        assertTrue(basicOdds > rangerOdds)
    }

    @Test
    fun failedMissionsAndRoutineNonAwardOpsReturnNullMedal() {
        val facilities = FacilityType.entries.map { FacilityState(it, 1) }
        val kestrel = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
        val ridgePatrol = GameCatalog.missions.first { it.id == "msn_ridge_patrol" }
        val deepOp = GameCatalog.missions.first { it.id == "msn_deep_expedition" }
        val signalsSchool = GameCatalog.missions.first { it.id == "msn_signals_school" }

        // Failed specialty school grants no graduation award
        val (failedSchoolMedal, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = signalsSchool,
            operator = kestrel,
            outcome = OperatorOutcomeType.TRAINING_FAILED,
            equippedGear = emptyList(),
            existingCitationMedalIds = emptySet()
        )
        assertNull(failedSchoolMedal)

        // Surviving a first combat op earns the combat badge even if the mission failed
        val (firstCombatBadge, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = deepOp,
            operator = kestrel,
            outcome = OperatorOutcomeType.SAFE,
            equippedGear = emptyList(),
            existingCitationMedalIds = emptySet(),
            missionSucceeded = false
        )
        assertEquals(TacticalCalculator.combatBadgeIdFor(kestrel), firstCombatBadge)

        // Failed combat mission returns null award for an uninjured soldier who already has the combat badge
        val (failedSafeMedal, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = deepOp,
            operator = kestrel,
            outcome = OperatorOutcomeType.SAFE,
            equippedGear = emptyList(),
            existingCitationMedalIds = setOf(TacticalCalculator.combatBadgeIdFor(kestrel)),
            missionSucceeded = false
        )
        assertNull(failedSafeMedal)

        // Wounded on failed combat mission still qualifies for Purple Shield if unowned, else null (no extra valor award)
        val (failedWoundedFirst, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = deepOp,
            operator = kestrel,
            outcome = OperatorOutcomeType.WOUNDED,
            equippedGear = emptyList(),
            existingCitationMedalIds = emptySet(),
            missionSucceeded = false
        )
        assertEquals("medal_purple_shield", failedWoundedFirst)

        val (failedWoundedRepeat, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = deepOp,
            operator = kestrel,
            outcome = OperatorOutcomeType.WOUNDED,
            equippedGear = emptyList(),
            existingCitationMedalIds = setOf("medal_purple_shield", TacticalCalculator.combatBadgeIdFor(kestrel)),
            missionSucceeded = false
        )
        assertNull(failedWoundedRepeat)

        // Routine LOW-risk patrol on a veteran (highestRiskSurvived != null) with rollSeedPct >= 45 yields no medal
        val (routineUnworthyMedal, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = ridgePatrol,
            operator = kestrel,
            outcome = OperatorOutcomeType.SAFE,
            equippedGear = emptyList(),
            existingCitationMedalIds = setOf(TacticalCalculator.combatBadgeIdFor(kestrel)),
            missionSucceeded = true,
            rollSeedPct = 80
        )
        assertNull(routineUnworthyMedal)

        // Mission success odds reflect base rate + squad efficiency and enforce risk ceilings
        val soloOdds = TacticalCalculator.missionSuccessOddsPct(
            mission = deepOp,
            selectedOperators = listOf(kestrel),
            gearItems = emptyList(),
            citations = emptyList(),
            facilities = facilities
        )
        assertTrue(soloOdds in 12..62)

        val highOp = GameCatalog.missions.first { it.id == "msn_citadel_recovery" }
        val eliteOps = GameCatalog.initialOperators.take(4).map {
            it.copy(
                unlocked = true,
                xp = OperatorRank.GEN.xpRequired,
                authorizedTier = RankTier.FLAG_OFFICER,
                baseFirepower = 40,
                baseStealth = 40,
                baseTech = 40,
                baseResilience = 40
            )
        }
        val maxFacilities = FacilityType.entries.map { FacilityState(it, 6) }
        val highCapOdds = TacticalCalculator.missionSuccessOddsPct(
            mission = highOp,
            selectedOperators = eliteOps,
            gearItems = GameCatalog.initialGear,
            citations = emptyList(),
            facilities = maxFacilities
        )
        val extremeCapOdds = TacticalCalculator.missionSuccessOddsPct(
            mission = deepOp,
            selectedOperators = eliteOps,
            gearItems = GameCatalog.initialGear,
            citations = emptyList(),
            facilities = maxFacilities
        )
        assertTrue("Expected High-risk mission ceiling <= 70%, got $highCapOdds", highCapOdds <= 70)
        assertTrue("Expected Extreme-risk mission ceiling <= 62%, got $extremeCapOdds", extremeCapOdds <= 62)
    }

    @Test
    fun resolveWornRegaliaEnforcesRegulationCapsAndPrecedence() {
        val kestrel = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
            .copy(dressDisplayMode = DressDisplayMode.FULL_MEDALS)
            .withManualCords("cord_infantry_blue", "cord_general_aiguillette")
        // Give Kestrel every single award in the entire catalog
        val allAwards = GameCatalog.medals

        val wornFullMedals = TacticalCalculator.resolveWornRegalia(kestrel, allAwards)
        assertEquals("medal_sovereign_star", wornFullMedals.neckMedal?.id)
        assertEquals("cord_infantry_blue", wornFullMedals.leftCord?.id)
        assertEquals("cord_general_aiguillette", wornFullMedals.rightCord?.id)
        assertEquals(TacticalCalculator.MAX_WORN_SHOULDER_TABS, wornFullMedals.shoulderTabs.size)
        // All four tabs held: the stack is SF, Ranger, Sapper and the Airborne Tab drops off
        assertEquals(
            listOf("insig_sf_tab", "insig_ranger_tab", "insig_sapper_tab"),
            wornFullMedals.shoulderTabs.map { it.id }
        )
        assertEquals(TacticalCalculator.MAX_WORN_CHEST_BADGES, wornFullMedals.chestBadges.size)
        assertEquals(TacticalCalculator.MAX_WORN_HANGING_MEDALS, wornFullMedals.hangingMedals.size)
        assertEquals(TacticalCalculator.MAX_WORN_RIBBONS, wornFullMedals.ribbonRack.size)
        // Highest precedence hanging medals must be worn first
        assertEquals(
            listOf(
                "medal_distinguished_cross",
                "medal_distinguished_service",
                "medal_silver_vanguard",
                "medal_defense_superior"
            ),
            wornFullMedals.hangingMedals.map { it.id }
        )

        val wornRibbons = TacticalCalculator.resolveWornRegalia(
            kestrel.copy(dressDisplayMode = DressDisplayMode.RIBBONS),
            allAwards
        )
        assertEquals(9, wornRibbons.ribbonRack.size)
        assertTrue("medal_sovereign_star" in wornRibbons.wornIds)
        // Real-Army precedence (Session 15): the ninth ribbon is the Bronze Valor Star
        assertTrue("medal_bronze_valor" in wornRibbons.wornIds)
        assertFalse("medal_expeditionary" in wornRibbons.wornIds)
        assertFalse("medal_basic_training" in wornRibbons.wornIds)
    }

    @Test
    fun veteranRetirementRequiresLongServiceAppliesSpacingDampingAndRespectsCap() {
        val cipher = GameCatalog.initialOperators.first { it.id == "op_cipher" } // PV2, 0 ops
        val vesper = GameCatalog.initialOperators.first { it.id == "op_vesper" } // SGT, 3 ops
        val kestrel = GameCatalog.initialOperators.first { it.id == "op_kestrel" } // CPT, 5 ops

        // Starter and mid-career (< 18 ops) soldiers cannot retire early
        assertFalse(TacticalCalculator.evaluateRetirementEligibility(cipher).eligible)
        assertFalse(TacticalCalculator.evaluateRetirementEligibility(vesper).eligible)
        assertFalse(TacticalCalculator.evaluateRetirementEligibility(kestrel).eligible)
        assertFalse(TacticalCalculator.evaluateRetirementEligibility(kestrel.copy(missionsCompleted = 15)).eligible)

        // Long-serving veteran (18+ ops at SGT+) IS eligible when active & not deployed
        val veteran = kestrel.copy(missionsCompleted = 20)
        assertTrue(TacticalCalculator.evaluateRetirementEligibility(veteran).eligible)
        assertFalse(TacticalCalculator.evaluateRetirementEligibility(veteran.copy(status = OperatorStatus.DEPLOYED)).eligible)
        assertFalse(TacticalCalculator.evaluateRetirementEligibility(veteran.copy(status = OperatorStatus.RETIRED)).eligible)

        // Retired soldiers cannot deploy on missions
        val basicMission = GameCatalog.missions.first { it.id == "msn_basic_qual" }
        assertFalse(
            TacticalCalculator.canOperatorDeployOnMission(
                operator = veteran.copy(status = OperatorStatus.RETIRED),
                mission = basicMission,
                deployedOpIds = emptySet()
            ).eligible
        )

        val now = 1_000_000_000L
        // First retirement (lastRetirementAtMillis == 0) gets full 100% spacing and small boost (< 1.0%)
        val firstPreview = TacticalCalculator.previewRetirementLegacy(
            operator = veteran,
            currentLegacyBps = 0,
            lastRetirementAtMillis = 0L,
            nowMillis = now
        )
        assertEquals(100, firstPreview.spacingPct)
        assertEquals(0L, firstPreview.cooldownRemainingSec)
        assertTrue(firstPreview.gainBps in 1..100)
        assertEquals(firstPreview.gainBps, firstPreview.resultingBps)

        // Immediate back-to-back retirement reduces spacing to 20% and grants much smaller gain
        val rapidPreview = TacticalCalculator.previewRetirementLegacy(
            operator = veteran,
            currentLegacyBps = firstPreview.resultingBps,
            lastRetirementAtMillis = now,
            nowMillis = now + 5_000L
        )
        assertEquals(20, rapidPreview.spacingPct)
        assertTrue(rapidPreview.cooldownRemainingSec > 0L)
        assertTrue(rapidPreview.gainBps < firstPreview.gainBps)

        // Halfway through 30m cooldown -> ~60% spacing
        val halfCooldownPreview = TacticalCalculator.previewRetirementLegacy(
            operator = veteran,
            currentLegacyBps = firstPreview.resultingBps,
            lastRetirementAtMillis = now,
            nowMillis = now + TacticalCalculator.RETIREMENT_FULL_COOLDOWN_MILLIS / 2
        )
        assertEquals(60, halfCooldownPreview.spacingPct)

        // Near cap headroom tapering prevents exceeding MAX_VETERAN_LEGACY_BPS
        val nearCapPreview = TacticalCalculator.previewRetirementLegacy(
            operator = veteran,
            currentLegacyBps = TacticalCalculator.MAX_VETERAN_LEGACY_BPS - 3,
            lastRetirementAtMillis = 0L,
            nowMillis = now
        )
        assertTrue(nearCapPreview.resultingBps <= TacticalCalculator.MAX_VETERAN_LEGACY_BPS)

        val atCapPreview = TacticalCalculator.previewRetirementLegacy(
            operator = veteran,
            currentLegacyBps = TacticalCalculator.MAX_VETERAN_LEGACY_BPS,
            lastRetirementAtMillis = 0L,
            nowMillis = now
        )
        assertTrue(atCapPreview.atCap)
        assertEquals(0, atCapPreview.gainBps)
        assertEquals(TacticalCalculator.MAX_VETERAN_LEGACY_BPS, atCapPreview.resultingBps)

        // Base Ops productionRates applies Veteran Legacy multiplier and clamps at cap
        val facilities = FacilityType.entries.map { FacilityState(it, 1) }
        val baseRates = TacticalCalculator.productionRates(facilities, veteranLegacyBps = 0)
        val boostedRates = TacticalCalculator.productionRates(facilities, veteranLegacyBps = 600) // +6.00%
        val overCapRates = TacticalCalculator.productionRates(facilities, veteranLegacyBps = 5000) // clamped to 1200 (+12.00%)
        assertEquals(baseRates.credits * 1.06, boostedRates.credits, 0.01)
        assertEquals(baseRates.supplies * 1.06, boostedRates.supplies, 0.01)
        assertEquals(baseRates.intel * 1.06, boostedRates.intel, 0.01)
        assertEquals(baseRates.credits * 1.12, overCapRates.credits, 0.01)
    }

    @Test
    fun armoryGearEnforcesSchoolAndFacilityRequirements() {
        val baseFacilities = FacilityType.entries.map { FacilityState(it, 1) }
        val armoryLv2 = baseFacilities.map {
            if (it.type == FacilityType.ARMORY_WORKSHOP) it.copy(level = 2) else it
        }
        val armoryLv3 = baseFacilities.map {
            if (it.type == FacilityType.ARMORY_WORKSHOP) it.copy(level = 3) else it
        }

        val kestrel = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
        val vesper = GameCatalog.initialOperators.first { it.id == "op_vesper" }
        val cipher = GameCatalog.initialOperators.first { it.id == "op_cipher" }
        val starterSquad = listOf(kestrel, vesper, cipher)

        // Every pre-equipped starter loadout must be valid for its starting soldier
        starterSquad.flatMap { op -> op.equippedGearIds.values.map { op to GameCatalog.gearMetaById.getValue(it) } }
            .forEach { (owner, gear) ->
                assertTrue(
                    "Starter gear ${gear.id} should be equippable by ${owner.id}",
                    TacticalCalculator.evaluateGearEquipEligibility(gear, owner).eligible
                )
            }

        // Standard-issue kit has no school gate for Cipher, while school kit is gated
        val standardOptic = GameCatalog.gearMetaById.getValue("opt_acog_4x")
        val nvg = GameCatalog.gearMetaById.getValue("opt_pvs31_nvg")
        val saw = GameCatalog.gearMetaById.getValue("wpn_m249_saw")
        val rangerRifle = GameCatalog.gearMetaById.getValue("wpn_mk20_ssr")
        val clsAidBag = GameCatalog.gearMetaById.getValue("utl_cls_aid_bag")

        assertTrue(TacticalCalculator.evaluateGearEquipEligibility(standardOptic, cipher).eligible)
        assertFalse(TacticalCalculator.evaluateGearEquipEligibility(nvg, cipher).eligible)
        assertFalse(TacticalCalculator.evaluateGearEquipEligibility(saw, cipher).eligible)
        assertFalse(TacticalCalculator.evaluateGearEquipEligibility(rangerRifle, cipher).eligible)

        // Graduating the matching school immediately clears equip eligibility
        assertTrue(
            TacticalCalculator.evaluateGearEquipEligibility(
                saw,
                cipher.copy(completedMissionIds = setOf("msn_basic_qual"))
            ).eligible
        )
        assertTrue(TacticalCalculator.evaluateGearEquipEligibility(nvg, vesper).eligible)
        assertTrue(TacticalCalculator.evaluateGearEquipEligibility(rangerRifle, kestrel).eligible)

        // Requisition gating: clsAidBag needs Armory Lv.2 AND a Support School graduate
        assertFalse(
            TacticalCalculator.evaluateGearRequisitionEligibility(
                clsAidBag,
                baseFacilities,
                starterSquad
            ).eligible
        )
        // Even at Armory Lv.2, starterSquad has no unlocked Support School graduate yet
        assertFalse(
            TacticalCalculator.evaluateGearRequisitionEligibility(
                clsAidBag,
                armoryLv2,
                starterSquad
            ).eligible
        )
        // Once Cipher graduates Combat Medical Academy at Armory Lv.2, clsAidBag unlocks for requisition
        val withMedGrad = listOf(
            kestrel,
            vesper,
            cipher.copy(completedMissionIds = setOf("msn_medical_school"))
        )
        assertTrue(
            TacticalCalculator.evaluateGearRequisitionEligibility(
                clsAidBag,
                armoryLv2,
                withMedGrad
            ).eligible
        )
        // Engineer school unlocks breacher kit, NOT medical aid bag
        val withEngOnly = listOf(
            kestrel,
            vesper,
            cipher.copy(completedMissionIds = setOf("msn_engineer_school"))
        )
        assertFalse(
            TacticalCalculator.evaluateGearRequisitionEligibility(
                clsAidBag,
                armoryLv2,
                withEngOnly
            ).eligible
        )
        val breacherKit = GameCatalog.gearMetaById.getValue("utl_breacher_kit")
        assertTrue(
            TacticalCalculator.evaluateGearRequisitionEligibility(
                breacherKit,
                armoryLv2,
                withEngOnly
            ).eligible
        )

        // MK20 SSR Ranger Rifle needs Ranger graduate (Kestrel already has it) AND Armory Lv.3
        assertFalse(
            TacticalCalculator.evaluateGearRequisitionEligibility(
                rangerRifle,
                armoryLv2,
                starterSquad
            ).eligible
        )
        assertTrue(
            TacticalCalculator.evaluateGearRequisitionEligibility(
                rangerRifle,
                armoryLv3,
                starterSquad
            ).eligible
        )
    }

    @Test
    fun awardLimitsEnforceCampaignPerSoldierRankAndPosthumousRules() {
        val kestrel = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
        val vesper = GameCatalog.initialOperators.first { it.id == "op_vesper" }
        val cipher = GameCatalog.initialOperators.first { it.id == "op_cipher" }

        val sovereignStar = GameCatalog.medals.first { it.id == "medal_sovereign_star" }
        val serviceCross = GameCatalog.medals.first { it.id == "medal_distinguished_cross" }
        val defenseSuperior = GameCatalog.medals.first { it.id == "medal_defense_superior" }
        val goodConduct = GameCatalog.medals.first { it.id == "medal_good_conduct" }
        val basicTraining = GameCatalog.medals.first { it.id == "medal_basic_training" }
        val ncoDev = GameCatalog.medals.first { it.id == "medal_nco_dev" }
        val bronzeValor = GameCatalog.medals.first { it.id == "medal_bronze_valor" }
        val infCord = GameCatalog.medals.first { it.id == "cord_infantry_blue" }
        val rangerTab = GameCatalog.medals.first { it.id == "insig_ranger_tab" }

        // 1. The Sovereign Star needs a heroism citation; no career checklist unlocks it
        val veteran = vesper.copy(
            missionsCompleted = 12,
            combatMissionsCompleted = 12,
            highestRiskSurvived = MissionRisk.EXTREME
        )
        assertFalse(
            TacticalCalculator.evaluateAwardEligibility(sovereignStar, veteran, setOf("medal_distinguished_cross")).eligible
        )
        val rookie = kestrel.copy(missionsCompleted = 1, combatMissionsCompleted = 1, highestRiskSurvived = MissionRisk.LOW)
        assertTrue(
            TacticalCalculator.evaluateAwardEligibility(sovereignStar, rookie, emptySet(), heroismCited = true).eligible
        )

        val secondCandidate = kestrel.copy(
            missionsCompleted = 10,
            combatMissionsCompleted = 10,
            highestRiskSurvived = MissionRisk.EXTREME
        )

        // 2. Per-soldier cap = 1 still holds with a citation; other heroes (living or fallen) can still earn one
        val firstSovereignCit = AwardedMedal(
            id = "cit_moh_1",
            operatorId = veteran.id,
            medalId = sovereignStar.id,
            citationNote = "Held the ridge alone",
            awardedAtMillis = 100L
        )
        val fallenCandidate = cipher.copy(status = OperatorStatus.FALLEN, missionsCompleted = 1, combatMissionsCompleted = 1)
        assertTrue(
            TacticalCalculator.evaluateAwardEligibility(
                medal = sovereignStar,
                operator = fallenCandidate,
                ownedMedalIds = emptySet(),
                allCitations = listOf(firstSovereignCit),
                heroismCited = true
            ).eligible
        )
        assertFalse(
            TacticalCalculator.evaluateAwardEligibility(
                medal = sovereignStar,
                operator = veteran,
                ownedMedalIds = setOf(sovereignStar.id),
                allCitations = listOf(firstSovereignCit),
                heroismCited = true
            ).eligible
        )
        assertFalse(TacticalCalculator.isRepeatableAward(sovereignStar))

        // 3. A fallen soldier's deed follows the chain below the Star (the Star itself comes only from the roll)
        val deepOp = GameCatalog.missions.first { it.id == "msn_deep_expedition" }
        val (fallenAward, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = deepOp,
            operator = fallenCandidate,
            outcome = OperatorOutcomeType.FALLEN,
            equippedGear = emptyList(),
            existingCitationMedalIds = emptySet(),
            allCampaignCitations = listOf(firstSovereignCit)
        )
        // Session 15: valor comes from the valor roll; the routine deed for the fallen is the Purple Shield
        assertEquals("medal_purple_shield", fallenAward)

        // 4. Sovereign Star uses a per-soldier cap (1 max/soldier) instead of a global campaign cap
        assertNull(TacticalCalculator.campaignCapForAward(sovereignStar.id))
        assertEquals(1, TacticalCalculator.perSoldierCapForAward(sovereignStar.id))
        assertNull(TacticalCalculator.campaignCapForAward(serviceCross.id))
        assertEquals(TacticalCalculator.MAX_REPEAT_AWARDS, TacticalCalculator.perSoldierCapForAward(serviceCross.id))
        assertNull(TacticalCalculator.campaignCapForAward(defenseSuperior.id))

        val crossCitations = listOf(
            AwardedMedal(id = "c1", operatorId = "op_a", medalId = serviceCross.id, citationNote = "c1", awardedAtMillis = 1L),
            AwardedMedal(id = "c2", operatorId = "op_b", medalId = serviceCross.id, citationNote = "c2", awardedAtMillis = 2L)
        )
        assertTrue(
            TacticalCalculator.evaluateAwardEligibility(
                medal = serviceCross,
                operator = secondCandidate,
                ownedMedalIds = setOf("medal_bronze_valor"),
                allCitations = crossCitations,
                heroismCited = true
            ).eligible
        )
        val fullDefSupCitations = listOf(
            AwardedMedal(id = "d1", operatorId = "op_a", medalId = defenseSuperior.id, citationNote = "d1", awardedAtMillis = 1L),
            AwardedMedal(id = "d2", operatorId = "op_b", medalId = defenseSuperior.id, citationNote = "d2", awardedAtMillis = 2L),
            AwardedMedal(id = "d3", operatorId = "op_c", medalId = defenseSuperior.id, citationNote = "d3", awardedAtMillis = 3L)
        )
        assertTrue(
            TacticalCalculator.evaluateAwardEligibility(
                medal = defenseSuperior,
                operator = secondCandidate.copy(
                    status = OperatorStatus.HQ_STAFF,
                    staffOpsSupported = 5
                ),
                ownedMedalIds = emptySet(),
                allCitations = fullDefSupCitations
            ).eligible
        )

        // 5. Army Good Conduct Ribbon restricted to Enlisted/NCO (E-1 to E-9)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(goodConduct, vesper, emptySet()).eligible)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(goodConduct, cipher.copy(missionsCompleted = 2), emptySet()).eligible)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(goodConduct, kestrel, emptySet()).eligible)

        // 6. Fallen soldiers restricted to posthumous combat/sacrifice/service honors
        val qualifiedFallen = fallenCandidate.copy(
            xp = OperatorRank.SGT.xpRequired,
            authorizedTier = RankTier.NCO,
            highestRiskSurvived = MissionRisk.MODERATE,
            completedMissionIds = GameCatalog.missions.map { it.id }.toSet()
        )
        assertTrue(TacticalCalculator.evaluateAwardEligibility(bronzeValor, qualifiedFallen, emptySet(), heroismCited = true).eligible)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(basicTraining, qualifiedFallen, emptySet()).eligible)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(ncoDev, qualifiedFallen, emptySet()).eligible)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(infCord, qualifiedFallen, emptySet()).eligible)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(rangerTab, qualifiedFallen, emptySet()).eligible)

        // 7. Unlimited earned awards per soldier: owning 3+ cords NEVER blocks HQ Staff Gold Aiguillette or 4th+ cord,
        // and owning 9+ medals/ribbons or 6+ school badges NEVER blocks earning more
        val existingCords = setOf(
            "cord_infantry_blue",
            "cord_valor_fourragere",
            "cord_airborne_crimson",
            "cord_sf_emerald"
        )
        val promotedHqNco = vesper.copy(
            status = OperatorStatus.HQ_STAFF,
            hqRole = HqStaffRole.S1_PERSONNEL_HONORS,
            missionsCompleted = 8,
            timesWounded = 1,
            highestRiskSurvived = MissionRisk.EXTREME,
            completedMissionIds = GameCatalog.missions.map { it.id }.toSet()
        )
        val goldAiguillette = GameCatalog.medals.first { it.id == "cord_general_aiguillette" }
        assertTrue(
            "HQ Staff NCO/Officer with 4 cords must still be able to earn HQ Gold Aiguillette",
            TacticalCalculator.evaluateAwardEligibility(
                medal = goldAiguillette,
                operator = promotedHqNco,
                ownedMedalIds = existingCords
            ).eligible
        )

        val nineMedalsAndRibbons = setOf(
            "medal_distinguished_cross",
            "medal_defense_superior",
            "medal_silver_vanguard",
            "medal_purple_shield",
            "medal_bronze_valor",
            "medal_meritorious",
            "medal_army_commendation",
            "medal_expeditionary",
            "medal_good_conduct"
        )
        assertTrue(
            TacticalCalculator.evaluateAwardEligibility(
                medal = GameCatalog.medals.first { it.id == "medal_night_owl" },
                operator = promotedHqNco,
                ownedMedalIds = nineMedalsAndRibbons
            ).eligible
        )

        val sixInsignia = setOf(
            "insig_ranger_tab",
            "insig_master_para",
            "insig_air_assault",
            "insig_parachutist",
            "insig_pathfinder",
            "insig_cyber_crest"
        )
        assertTrue(
            TacticalCalculator.evaluateAwardEligibility(
                medal = GameCatalog.medals.first { it.id == "insig_cmb_medic" },
                operator = promotedHqNco,
                ownedMedalIds = sixInsignia
            ).eligible
        )
    }

    @Test
    fun ribbonRackLayoutCentersPartialTopRowAndTailorOverridesSelectExactSlots() {
        val sample = (1..9).toList()
        assertEquals(listOf(listOf(1)), TacticalCalculator.layoutRibbonRackRows(sample.take(1)))
        assertEquals(listOf(listOf(1, 2)), TacticalCalculator.layoutRibbonRackRows(sample.take(2)))
        assertEquals(listOf(listOf(1, 2, 3)), TacticalCalculator.layoutRibbonRackRows(sample.take(3)))
        assertEquals(listOf(listOf(1), listOf(2, 3, 4)), TacticalCalculator.layoutRibbonRackRows(sample.take(4)))
        assertEquals(listOf(listOf(1, 2), listOf(3, 4, 5)), TacticalCalculator.layoutRibbonRackRows(sample.take(5)))
        assertEquals(listOf(listOf(1, 2, 3), listOf(4, 5, 6)), TacticalCalculator.layoutRibbonRackRows(sample.take(6)))
        assertEquals(
            listOf(listOf(1), listOf(2, 3, 4), listOf(5, 6, 7)),
            TacticalCalculator.layoutRibbonRackRows(sample.take(7))
        )
        assertEquals(
            listOf(listOf(1, 2), listOf(3, 4, 5), listOf(6, 7, 8)),
            TacticalCalculator.layoutRibbonRackRows(sample.take(8))
        )
        assertEquals(
            listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)),
            TacticalCalculator.layoutRibbonRackRows(sample.take(9))
        )

        val kestrel = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
            .withManualCords("cord_infantry_blue", "cord_valor_fourragere").copy(
            dressDisplayMode = DressDisplayMode.RIBBONS,
            tailorOverrides = UniformTailorOverrides(
                unitPatch = UnitPatchStyle.DIVISION_2,
                neckSlot = UniformTailorOverrides.SLOT_NONE,
                tabSlot1 = "insig_sapper_tab",
                tabSlot2 = UniformTailorOverrides.SLOT_NONE,
                pinSlot1 = "insig_eod_badge",
                pinSlot2 = UniformTailorOverrides.SLOT_NONE,
                pinSlot3 = "insig_staff_crest",
                medalSlot1 = "medal_night_owl",
                medalSlot2 = "medal_basic_training",
                medalSlot3 = UniformTailorOverrides.SLOT_NONE,
                medalSlot4 = UniformTailorOverrides.SLOT_NONE
            )
        )
        val resolved = TacticalCalculator.resolveWornRegalia(kestrel, GameCatalog.medals)
        assertNull(resolved.neckMedal)
        assertEquals("cord_infantry_blue", resolved.leftCord?.id)
        assertEquals("cord_valor_fourragere", resolved.rightCord?.id)
        assertEquals("insig_sapper_tab", resolved.tabSlot1?.id)
        assertNull(resolved.tabSlot2)
        assertEquals("insig_eod_badge", resolved.chestPinAboveRack?.id)
        assertNull(resolved.chestPinLeftPocket)
        assertEquals("insig_staff_crest", resolved.chestPinRightPocket?.id)
        assertEquals(listOf("medal_night_owl", "medal_basic_training"), resolved.ribbonRack.map { it.id })

        val cleanResolved = TacticalCalculator.resolveWornRegalia(
            kestrel.copy(dressDisplayMode = DressDisplayMode.CLEAN),
            GameCatalog.medals
        )
        assertTrue(cleanResolved.ribbonRack.isEmpty())
        assertTrue(cleanResolved.hangingMedals.isEmpty())
    }

    @Test
    fun cordsEnforceShoulderRestrictionsAndChestBadgesEnforceThreeSlotPositions() {
        val baseOp = GameCatalog.initialOperators.first { it.id == "op_kestrel" }

        // Swapped cords (Staff Aiguillette on Left, Infantry Cord on Right) must be rejected
        val illegalCordPlacement = TacticalCalculator.resolveWornRegalia(
            baseOp.withManualCords("cord_general_aiguillette", "cord_infantry_blue"),
            GameCatalog.medals
        )
        assertNull(illegalCordPlacement.leftCord)
        assertNull(illegalCordPlacement.rightCord)

        // Left shoulder only accepts branch/school cords; Right shoulder only accepts Staff Aiguillette & Fourragere
        val leftOptions = UniformRegaliaRules.cordsForShoulder(
            medals = GameCatalog.medals,
            isRightShoulder = false,
            specialty = OperatorSpecialty.ASSAULT
        ).map { it.id }
        assertTrue("cord_infantry_blue" in leftOptions)
        assertFalse("cord_general_aiguillette" in leftOptions)
        assertFalse("cord_valor_fourragere" in leftOptions)
        assertEquals("cord_sf_emerald", leftOptions.first())

        val rightOptions = UniformRegaliaRules.cordsForShoulder(
            medals = GameCatalog.medals,
            isRightShoulder = true,
            specialty = OperatorSpecialty.ASSAULT
        ).map { it.id }
        assertEquals(setOf("cord_valor_fourragere", "cord_general_aiguillette"), rightOptions.toSet())

        // Chest badge slot restrictions
        val aboveIds = UniformRegaliaRules.chestBadgesForSlot(
            GameCatalog.medals,
            ChestBadgeSlot.ABOVE_RACK,
            OperatorSpecialty.ASSAULT
        ).map { it.id }
        val leftIds = UniformRegaliaRules.chestBadgesForSlot(
            GameCatalog.medals,
            ChestBadgeSlot.LEFT_POCKET,
            OperatorSpecialty.ASSAULT
        ).map { it.id }
        val rightIds = UniformRegaliaRules.chestBadgesForSlot(
            GameCatalog.medals,
            ChestBadgeSlot.RIGHT_POCKET,
            OperatorSpecialty.ASSAULT
        ).map { it.id }

        assertTrue("insig_eib_rifle" in aboveIds)
        assertTrue("insig_cmb_medic" in aboveIds)
        assertFalse("insig_staff_crest" in aboveIds)
        assertFalse("insig_logistics_crest" in aboveIds)
        assertFalse("insig_cyber_crest" in aboveIds)

        assertFalse("insig_eib_rifle" in leftIds)
        assertFalse("insig_cmb_medic" in leftIds)
        assertFalse("insig_staff_crest" in leftIds)
        assertTrue("insig_pathfinder" in leftIds)
        assertTrue("insig_eod_badge" in leftIds)

        assertTrue("insig_staff_crest" in rightIds)
        assertTrue("insig_logistics_crest" in rightIds)
        assertFalse("insig_eib_rifle" in rightIds)
        assertFalse("insig_master_para" in rightIds)

        // Attempting to force a badge into a disallowed chest slot falls back safely
        val forcedIllegalPins = TacticalCalculator.resolveWornRegalia(
            baseOp.copy(
                tailorOverrides = UniformTailorOverrides(
                    pinSlot1 = "insig_staff_crest",
                    pinSlot2 = "insig_eib_rifle",
                    pinSlot3 = "insig_air_assault"
                )
            ),
            GameCatalog.medals.filter { it.id in setOf("insig_staff_crest", "insig_eib_rifle") }
        )
        assertEquals("insig_eib_rifle", forcedIllegalPins.chestPinAboveRack?.id)
        assertNull(forcedIllegalPins.chestPinLeftPocket)
        assertEquals("insig_staff_crest", forcedIllegalPins.chestPinRightPocket?.id)
    }

    @Test
    fun mosDeterminesAutoFrontAndCenterBadgeEvenOverHigherPrecedenceBadges() {
        val base = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
            .copy(tailorOverrides = UniformTailorOverrides())

        // Infantryman holding Master Jump (prec 26), EOD (prec 30), EIB (prec 34), Logistics (prec 36)
        // must put EIB Rifle front & center above the ribbon rack!
        val infantryAwards = GameCatalog.medals.filter {
            it.id in setOf("insig_master_para", "insig_eod_badge", "insig_eib_rifle", "insig_logistics_crest")
        }
        val infantryWorn = TacticalCalculator.resolveWornRegalia(
            base.copy(specialty = OperatorSpecialty.ASSAULT),
            infantryAwards
        )
        assertEquals("insig_eib_rifle", infantryWorn.chestPinAboveRack?.id)
        assertEquals("insig_master_para", infantryWorn.chestPinLeftPocket?.id)
        assertEquals("insig_logistics_crest", infantryWorn.chestPinRightPocket?.id)

        // Combat Medic holding Master Jump (prec 26), Air Assault (prec 33), and Medical Badge (prec 35)
        // must put Combat Medical Badge front & center above the ribbon rack!
        val medicAwards = GameCatalog.medals.filter {
            it.id in setOf("insig_master_para", "insig_air_assault", "insig_cmb_medic")
        }
        val medicWorn = TacticalCalculator.resolveWornRegalia(
            base.copy(specialty = OperatorSpecialty.MEDIC),
            medicAwards
        )
        assertEquals("insig_cmb_medic", medicWorn.chestPinAboveRack?.id)
        assertEquals("insig_air_assault", medicWorn.chestPinLeftPocket?.id)

        // Breacher (12B) puts EOD Badge above rack ahead of Master Jump; tabs follow regulation order (Ranger above Sapper)
        val breacherAwards = GameCatalog.medals.filter {
            it.id in setOf("insig_master_para", "insig_eod_badge", "insig_ranger_tab", "insig_sapper_tab")
        }
        val breacherWorn = TacticalCalculator.resolveWornRegalia(
            base.copy(specialty = OperatorSpecialty.BREACHER),
            breacherAwards
        )
        assertEquals("insig_eod_badge", breacherWorn.chestPinAboveRack?.id)
        assertEquals("insig_master_para", breacherWorn.chestPinLeftPocket?.id)
        assertEquals("insig_ranger_tab", breacherWorn.tabSlot1?.id)
        assertEquals("insig_sapper_tab", breacherWorn.tabSlot2?.id)

        // Signals (25B) prioritizes Cyber Crest on pocket slots and Signals Cord first on left shoulder
        val signalsAwards = GameCatalog.medals.filter {
            it.id in setOf("insig_air_assault", "insig_cyber_crest", "insig_intel_crest")
        }
        val signalsWorn = TacticalCalculator.resolveWornRegalia(
            base.copy(specialty = OperatorSpecialty.SIGNALS),
            signalsAwards
        )
        assertEquals("insig_air_assault", signalsWorn.chestPinAboveRack?.id)
        assertEquals("insig_cyber_crest", signalsWorn.chestPinLeftPocket?.id)
        assertEquals("insig_intel_crest", signalsWorn.chestPinRightPocket?.id)
    }

    @Test
    fun separatedSchoolsAirborneTabAndSpecialForcesCourseWorkEndToEnd() {
        val facilities = FacilityType.entries.map { FacilityState(it, 1) }
        val armoryLv3 = facilities.map {
            if (it.type == FacilityType.ARMORY_WORKSHOP) it.copy(level = 3) else it
        }

        // All 15 schools plus Rifle Qualification must exist with distinct IDs
        val schoolIds = GameCatalog.missions.filter { it.scope == MissionScope.TRAINING }.map { it.id }.toSet()
        assertEquals(
            setOf(
                "msn_basic_qual",
                "msn_medical_school",
                "msn_engineer_school",
                "msn_logistics_school",
                "msn_signals_school",
                "msn_intel_school",
                "msn_night_qual",
                "msn_ranger_school",
                "msn_sf_school",
                "msn_nco_school",
                "msn_snco_school",
                "msn_ocs_school",
                "msn_sapper_school",
                "msn_pathfinder_school",
                "msn_eib_testing",
                "msn_rifle_qual",
                "msn_wocs",
                "msn_flight_school",
                "msn_flight_hours",
                "msn_combat_diver_school",
                "msn_mff_school",
                "msn_air_assault_school"
            ),
            schoolIds
        )

        val cipher = GameCatalog.initialOperators.first { it.id == "op_cipher" }
        val vesper = GameCatalog.initialOperators.first { it.id == "op_vesper" }
        val kestrel = GameCatalog.initialOperators.first { it.id == "op_kestrel" }

        // Each separated school unlocks its own distinct shoulder cord & badge/tab
        val engGrad = cipher.copy(completedMissionIds = setOf("msn_engineer_school"), missionsCompleted = 1)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(GameCatalog.medals.first { it.id == "cord_engineer_scarlet" }, engGrad, emptySet()).eligible)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(GameCatalog.medals.first { it.id == "insig_eod_badge" }, engGrad, emptySet()).eligible)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(GameCatalog.medals.first { it.id == "insig_cmb_medic" }, engGrad, emptySet()).eligible)

        val logGrad = cipher.copy(completedMissionIds = setOf("msn_logistics_school"), missionsCompleted = 1)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(GameCatalog.medals.first { it.id == "cord_logistics_buff" }, logGrad, emptySet()).eligible)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(GameCatalog.medals.first { it.id == "insig_logistics_crest" }, logGrad, emptySet()).eligible)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(GameCatalog.medals.first { it.id == "insig_eod_badge" }, logGrad, emptySet()).eligible)

        val intelGrad = cipher.copy(completedMissionIds = setOf("msn_intel_school"), missionsCompleted = 1)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(GameCatalog.medals.first { it.id == "cord_intel_teal" }, intelGrad, emptySet()).eligible)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(GameCatalog.medals.first { it.id == "insig_intel_crest" }, intelGrad, emptySet()).eligible)
        // Pathfinder now comes only from Pathfinder School
        assertFalse(TacticalCalculator.evaluateAwardEligibility(GameCatalog.medals.first { it.id == "insig_pathfinder" }, intelGrad, emptySet()).eligible)
        val pathfinderGrad = cipher.copy(completedMissionIds = setOf("msn_pathfinder_school"), missionsCompleted = 1)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(GameCatalog.medals.first { it.id == "insig_pathfinder" }, pathfinderGrad, emptySet()).eligible)

        // Airborne School grants Airborne Tab, Jump Wings, Airborne Crimson Cord & Maroon Beret
        val abnGrad = cipher.copy(completedMissionIds = setOf("msn_night_qual"), missionsCompleted = 1)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(GameCatalog.medals.first { it.id == "insig_airborne_tab" }, abnGrad, emptySet()).eligible)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(GameCatalog.medals.first { it.id == "cord_airborne_crimson" }, abnGrad, emptySet()).eligible)
        assertTrue(TacticalCalculator.evaluateHeadgearEligibility(HeadgearStyle.MAROON_BERET, abnGrad, setOf("insig_airborne_tab")).eligible)

        // Special Forces School (SFQC): realistic prerequisites & hardest difficulty
        val sfSchool = GameCatalog.missions.first { it.id == "msn_sf_school" }
        val sfTab = GameCatalog.medals.first { it.id == "insig_sf_tab" }
        val sfCord = GameCatalog.medals.first { it.id == "cord_sf_emerald" }
        val rangerSchool = GameCatalog.missions.first { it.id == "msn_ranger_school" }

        // Blocked if SOLDIER rank even with all prereqs & stats
        val soldierHighStats = cipher.copy(
            completedMissionIds = setOf("msn_basic_qual", "msn_night_qual", "msn_ranger_school"),
            highestRiskSurvived = MissionRisk.MODERATE,
            baseFirepower = 20,
            baseStealth = 20,
            baseResilience = 20
        )
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(soldierHighStats, sfSchool, emptySet()).eligible)

        // Blocked without Moderate+ combat risk; Ranger School is no longer required
        val ncoMissingCombat = kestrel.copy(highestRiskSurvived = MissionRisk.LOW, baseFirepower = 20, baseStealth = 20, baseResilience = 20)
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(ncoMissingCombat, sfSchool, emptySet()).eligible)

        val ncoMissingRanger = vesper.copy(
            completedMissionIds = setOf("msn_basic_qual", "msn_night_qual"),
            highestRiskSurvived = MissionRisk.MODERATE,
            baseFirepower = 20,
            baseStealth = 20,
            baseResilience = 20
        )
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(ncoMissingRanger, sfSchool, emptySet()).eligible)

        // Qualified Ranger NCO/Officer with Moderate+ combat survival & high stats IS eligible for SFQC
        val sfCandidate = kestrel.copy(
            completedMissionIds = setOf("msn_basic_qual", "msn_night_qual", "msn_ranger_school"),
            highestRiskSurvived = MissionRisk.MODERATE,
            baseFirepower = 17,
            baseStealth = 16,
            baseResilience = 17
        )
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(sfCandidate, sfSchool, emptySet()).eligible)

        // SFQC is harder than Ranger School (lower cap & lower odds for identical candidate)
        val rangerOdds = TacticalCalculator.schoolPassOddsPct(rangerSchool, sfCandidate, emptyList())
        val sfOdds = TacticalCalculator.schoolPassOddsPct(sfSchool, sfCandidate, emptyList())
        assertTrue("Expected SFQC ($sfOdds%) harder than Ranger ($rangerOdds%)", sfOdds < rangerOdds)
        val maxedGodOp = sfCandidate.copy(
            xp = OperatorRank.GEN.xpRequired,
            authorizedTier = RankTier.FLAG_OFFICER,
            baseFirepower = 40,
            baseStealth = 40,
            baseTech = 40,
            baseResilience = 40
        )
        assertTrue(TacticalCalculator.schoolPassOddsPct(sfSchool, maxedGodOp, emptyList()) <= 52)

        // SFQC produces the best warriors (+11 permanent base stats) and awards SF Tab, Emerald Cord, Green Beret & SF gear
        assertEquals(11, sfSchool.schoolRequirements?.graduationStatBonus?.totalPower)
        val (sfDeedAward, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = sfSchool,
            operator = sfCandidate,
            outcome = OperatorOutcomeType.TRAINING_PASSED,
            equippedGear = emptyList(),
            existingCitationMedalIds = emptySet()
        )
        assertEquals("insig_sf_tab", sfDeedAward)

        val sfGrad = sfCandidate.copy(completedMissionIds = sfCandidate.completedMissionIds + "msn_sf_school", missionsCompleted = 6)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(sfTab, sfGrad, emptySet()).eligible)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(sfCord, sfGrad, emptySet()).eligible)
        assertTrue(TacticalCalculator.evaluateHeadgearEligibility(HeadgearStyle.EMERALD_BERET, sfGrad, setOf("insig_sf_tab")).eligible)
        assertFalse(TacticalCalculator.evaluateHeadgearEligibility(HeadgearStyle.EMERALD_BERET, kestrel, setOf("insig_ranger_tab")).eligible)

        val sfCarbine = GameCatalog.gearMetaById.getValue("wpn_sf_urg_i")
        assertFalse(TacticalCalculator.evaluateGearEquipEligibility(sfCarbine, kestrel).eligible)
        assertTrue(TacticalCalculator.evaluateGearEquipEligibility(sfCarbine, sfGrad).eligible)
        assertTrue(TacticalCalculator.evaluateGearRequisitionEligibility(sfCarbine, armoryLv3, listOf(sfGrad)).eligible)
    }

    @Test
    fun autoDistributeFieldGearOptimizesByMosTrainingsAndRespectsDeployedAndHqStaff() {
        val facilities = FacilityType.entries.map { FacilityState(it, 3) }
        val kestrelHq = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
            .copy(status = OperatorStatus.HQ_STAFF, hqRole = HqStaffRole.CO_COMMANDING_OFFICER)
        val vesperDeployed = GameCatalog.initialOperators.first { it.id == "op_vesper" }
            .copy(status = OperatorStatus.DEPLOYED)
        val cipherField = GameCatalog.initialOperators.first { it.id == "op_cipher" }
            .copy(completedMissionIds = setOf("msn_basic_qual", "msn_signals_school", "msn_night_qual"))
        val bastionField = GameCatalog.initialOperators.first { it.id == "op_bastion" }
            .copy(unlocked = true, status = OperatorStatus.READY)
        val rookField = GameCatalog.initialOperators.first { it.id == "op_rook" }
            .copy(unlocked = true, status = OperatorStatus.READY)
        val retiredOp = GameCatalog.createPostBasicRecruit(
            id = "op_retired",
            specialty = OperatorSpecialty.ASSAULT,
            existingCallsigns = emptySet(),
            seedIndex = 0
        ).copy(status = OperatorStatus.RETIRED)

        val deployment = ActiveDeployment(
            id = "dep_1",
            templateId = "msn_ridge_patrol",
            operatorIds = listOf(vesperDeployed.id),
            startedAtMillis = 0L,
            durationMillis = 60_000L,
            efficiencyPct = 100,
            avgSurvivalPct = 90
        )

        // Unlock everything except the SF items; nobody on standby has Ranger School
        val unlockedPool = GameCatalog.initialGear.map { gear ->
            gear.copy(unlocked = gear.id !in setOf("wpn_sf_urg_i", "arm_sf_spear"))
        }
        val deployedKit = mapOf(GearSlot.WEAPON to "wpn_mk14_ebr", GearSlot.OPTICS to "opt_pvs31_nvg")
        val vesperWithKit = vesperDeployed.copy(equippedGearIds = deployedKit)
        val hqWithStaleKit = kestrelHq.copy(equippedGearIds = mapOf(GearSlot.ARMOR to "arm_jpc_light"))

        val operators = listOf(hqWithStaleKit, vesperWithKit, cipherField, bastionField, rookField, retiredOp)
        val result = TacticalCalculator.autoDistributeFieldGear(
            operators = operators,
            gearItems = unlockedPool,
            citations = emptyList(),
            facilities = facilities,
            deployments = listOf(deployment)
        )
        val byId = result.updatedOperators.associateBy { it.id }
        fun kit(op: Operator) = byId.getValue(op.id).equippedGearIds

        // 1. Deployed soldier keeps existing kit untouched
        assertEquals(deployedKit, kit(vesperDeployed))

        // 2. HQ staff and retired soldiers carry nothing
        assertTrue(kit(kestrelHq).isEmpty())
        assertTrue(kit(retiredOp).isEmpty())

        // 3. Locked and Ranger-gated items go to nobody
        val carried = result.updatedOperators.flatMap { it.equippedGearIds.values }.toSet()
        assertFalse("wpn_sf_urg_i" in carried)
        assertFalse("arm_sf_spear" in carried)
        assertFalse("wpn_mk20_ssr" in carried)
        assertFalse("arm_ranger_crye" in carried)

        // 4. Specialist kit routes to the right MOS and graduates
        assertEquals("utl_cls_aid_bag", kit(rookField)[GearSlot.UTILITY])
        assertEquals("arm_avs_heavy", kit(bastionField)[GearSlot.ARMOR])
        assertEquals("utl_breacher_kit", kit(bastionField)[GearSlot.UTILITY])
        assertEquals("opt_sigint_lrf", kit(cipherField)[GearSlot.OPTICS])
        assertEquals("hlm_ops_visor", kit(cipherField)[GearSlot.HELMET])

        // 5. Every slot is filled whenever the soldier qualifies for something unlocked in it
        for (op in listOf(cipherField, bastionField, rookField)) {
            for (slot in GearSlot.entries) {
                val reason = GearAutoDistributor.emptySlotReason(op, slot, unlockedPool)
                assertEquals("${op.id} $slot", reason == null, slot in kit(op))
            }
        }

        // 6. Repeating auto-distribute on the optimized output changes nothing
        val secondPass = TacticalCalculator.autoDistributeFieldGear(
            operators = result.updatedOperators,
            gearItems = unlockedPool,
            citations = emptyList(),
            facilities = facilities,
            deployments = listOf(deployment)
        )
        assertTrue("Expected idempotent zero-change second pass", secondPass.changedOperators.isEmpty())

        // 7. Gear is shared: two identical soldiers both carry the best rifle
        val r1 = GameCatalog.createPostBasicRecruit("op_r1", OperatorSpecialty.ASSAULT, emptySet(), 1)
            .copy(completedMissionIds = setOf("msn_basic_qual"))
        val r2 = GameCatalog.createPostBasicRecruit("op_r2", OperatorSpecialty.ASSAULT, setOf(r1.callsign), 2)
            .copy(completedMissionIds = setOf("msn_basic_qual"))
        val rifles = listOf("wpn_hk416_cqb", "wpn_m4_carbine").map { GameCatalog.gearMetaById.getValue(it).copy(unlocked = true) }
        val shared = TacticalCalculator.autoDistributeFieldGear(listOf(r1, r2), rifles, emptyList(), facilities, emptyList())
        val picks = shared.updatedOperators.map { it.equippedGearIds[GearSlot.WEAPON] }
        assertEquals(1, picks.distinct().size)
        assertTrue(picks.all { it != null })
    }

    @Test
    fun emptySlotExplainsWhy() {
        val cipher = GameCatalog.initialOperators.first { it.id == "op_cipher" }.copy(completedMissionIds = setOf("msn_basic_qual"))
        val rangerOnly = listOf(GameCatalog.gearMetaById.getValue("wpn_mk20_ssr").copy(unlocked = true))
        val reason = GearAutoDistributor.emptySlotReason(cipher, GearSlot.WEAPON, rangerOnly)
        assertTrue(reason, reason!!.contains("Ranger"))
        val nothingUnlocked = GearAutoDistributor.emptySlotReason(cipher, GearSlot.WEAPON, rangerOnly.map { it.copy(unlocked = false) })
        assertTrue(nothingUnlocked!!.contains("unlocked"))
        assertNull(GearAutoDistributor.emptySlotReason(cipher, GearSlot.WEAPON, listOf(GameCatalog.gearMetaById.getValue("wpn_m4_carbine").copy(unlocked = true))))
    }

    @Test
    fun specialtySchoolMosRestrictionsEnforceSquadDiversity() {
        val basicSchool = GameCatalog.missions.first { it.id == "msn_basic_qual" }
        val medicalSchool = GameCatalog.missions.first { it.id == "msn_medical_school" }
        val engineerSchool = GameCatalog.missions.first { it.id == "msn_engineer_school" }
        val logisticsSchool = GameCatalog.missions.first { it.id == "msn_logistics_school" }
        val signalsSchool = GameCatalog.missions.first { it.id == "msn_signals_school" }
        val intelSchool = GameCatalog.missions.first { it.id == "msn_intel_school" }
        val airborneSchool = GameCatalog.missions.first { it.id == "msn_night_qual" }
        val rangerSchool = GameCatalog.missions.first { it.id == "msn_ranger_school" }
        val sfSchool = GameCatalog.missions.first { it.id == "msn_sf_school" }

        // Give every MOS high stats, NCO rank, Moderate combat survival & prereqs so ONLY MOS restriction is tested
        fun maxedOp(specialty: OperatorSpecialty): Operator =
            GameCatalog.createPostBasicRecruit("op_${specialty.name}", specialty, emptySet(), 0).copy(
                xp = OperatorRank.SFC.xpRequired,
                authorizedTier = RankTier.SNCO,
                baseFirepower = 22,
                baseStealth = 22,
                baseTech = 22,
                baseResilience = 22,
                highestRiskSurvived = MissionRisk.MODERATE,
                completedMissionIds = setOf("msn_basic_qual", "msn_night_qual", "msn_ranger_school")
            )

        fun maxedNoPrereq(specialty: OperatorSpecialty): Operator =
            maxedOp(specialty).copy(completedMissionIds = setOf("msn_basic_qual"))

        // Basic & Airborne are open to all 6 MOS
        for (mos in OperatorSpecialty.entries) {
            val rookie = GameCatalog.createPostBasicRecruit("r_${mos.name}", mos, emptySet(), 0)
            assertTrue("${mos.name} should attend Basics", TacticalCalculator.canOperatorDeployOnMission(rookie, basicSchool, emptySet()).eligible)
            assertTrue("${mos.name} should attend Airborne", TacticalCalculator.canOperatorDeployOnMission(maxedNoPrereq(mos), airborneSchool, emptySet()).eligible)
        }

        // Medical Academy: MEDIC only
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(maxedNoPrereq(OperatorSpecialty.MEDIC), medicalSchool, emptySet()).eligible)
        for (mos in OperatorSpecialty.entries.filter { it != OperatorSpecialty.MEDIC }) {
            assertFalse("${mos.name} blocked from Medical Academy", TacticalCalculator.canOperatorDeployOnMission(maxedNoPrereq(mos), medicalSchool, emptySet()).eligible)
        }

        // Engineer & EOD: BREACHER only
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(maxedNoPrereq(OperatorSpecialty.BREACHER), engineerSchool, emptySet()).eligible)
        for (mos in OperatorSpecialty.entries.filter { it != OperatorSpecialty.BREACHER }) {
            assertFalse("${mos.name} blocked from Engineer & EOD", TacticalCalculator.canOperatorDeployOnMission(maxedNoPrereq(mos), engineerSchool, emptySet()).eligible)
        }

        // Cyber & Signals: SIGNALS only
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(maxedNoPrereq(OperatorSpecialty.SIGNALS), signalsSchool, emptySet()).eligible)
        for (mos in OperatorSpecialty.entries.filter { it != OperatorSpecialty.SIGNALS }) {
            assertFalse("${mos.name} blocked from Cyber & Signals", TacticalCalculator.canOperatorDeployOnMission(maxedNoPrereq(mos), signalsSchool, emptySet()).eligible)
        }

        // Quartermaster & Logistics: COMMAND, MEDIC, BREACHER
        val logisticsAllowed = setOf(OperatorSpecialty.COMMAND, OperatorSpecialty.MEDIC, OperatorSpecialty.BREACHER)
        for (mos in OperatorSpecialty.entries) {
            assertEquals(
                "Logistics MOS check for ${mos.name}",
                mos in logisticsAllowed,
                TacticalCalculator.canOperatorDeployOnMission(maxedNoPrereq(mos), logisticsSchool, emptySet()).eligible
            )
        }

        // Military Intel & Pathfinder: RECON, SIGNALS, COMMAND
        val intelAllowed = setOf(OperatorSpecialty.RECON, OperatorSpecialty.SIGNALS, OperatorSpecialty.COMMAND)
        for (mos in OperatorSpecialty.entries) {
            assertEquals(
                "Intel MOS check for ${mos.name}",
                mos in intelAllowed,
                TacticalCalculator.canOperatorDeployOnMission(maxedNoPrereq(mos), intelSchool, emptySet()).eligible
            )
        }

        // Ranger Course is open to every MOS
        for (mos in OperatorSpecialty.entries) {
            assertEquals(
                "Ranger MOS check for ${mos.name}",
                true,
                TacticalCalculator.canOperatorDeployOnMission(maxedOp(mos).copy(completedMissionIds = setOf("msn_basic_qual", "msn_night_qual")), rangerSchool, emptySet()).eligible
            )
            // SFQC: combat MOS (Infantry, Recon, Engineer) plus Command and Medic; not Signals or Aviation
            assertEquals(
                "SFQC MOS check for ${mos.name}",
                mos != OperatorSpecialty.SIGNALS && mos != OperatorSpecialty.AVIATION,
                TacticalCalculator.canOperatorDeployOnMission(maxedOp(mos), sfSchool, emptySet()).eligible
            )
        }
    }

    @Test
    fun promotionBoardEnforcesLeadershipSchoolsMeritGatesAndBoardOpinions() {
        val facilities = FacilityType.entries.map { FacilityState(it, 2) }
        val baseSoldier = GameCatalog.initialOperators.first { it.id == "op_cipher" }

        // 1. Soldier -> NCO gate requires XP >= 380, BLC (msn_nco_school), and 2+ Ops
        val spcMissingSchool = baseSoldier.copy(
            xp = 380,
            authorizedTier = RankTier.SOLDIER,
            missionsCompleted = 2,
            completedMissionIds = setOf("msn_basic_qual", "msn_signals_school")
        )
        val missingBlcEval = TacticalCalculator.evaluatePromotionBoard(
            operator = spcMissingSchool,
            allOperators = listOf(spcMissingSchool),
            citations = emptyList(),
            gearItems = emptyList(),
            facilities = facilities
        )
        assertTrue(missingBlcEval.isCrossTierGate)
        assertEquals(OperatorRank.SGT, missingBlcEval.nextRank)
        assertFalse(missingBlcEval.allMeritsMet)
        assertEquals(BoardConsensus.BLOCKED, missingBlcEval.consensus)
        assertFalse(missingBlcEval.meritChecks.first { it.id == "school_blc" }.satisfied)

        val spcQualified = spcMissingSchool.copy(
            completedMissionIds = spcMissingSchool.completedMissionIds + "msn_nco_school",
            missionsCompleted = 3
        )
        val ncoBoardPass = TacticalCalculator.evaluatePromotionBoard(
            operator = spcQualified,
            allOperators = listOf(spcQualified),
            citations = listOf(
                AwardedMedal("c1", spcQualified.id, "medal_good_conduct", "Good standing", awardedAtMillis = 1L)
            ),
            gearItems = emptyList(),
            facilities = facilities
        )
        assertTrue(ncoBoardPass.allMeritsMet)
        assertEquals(3, ncoBoardPass.opinions.size)
        assertTrue(ncoBoardPass.consensus != BoardConsensus.BLOCKED)

        // 2. NCO -> SNCO gate requires XP >= 800, SLC (msn_snco_school), 5+ Ops, 1+ Decoration, Field/HQ credibility
        val ssgMissingSlc = GameCatalog.initialOperators.first { it.id == "op_vesper" }.copy(
            xp = 800,
            authorizedTier = RankTier.NCO,
            missionsCompleted = 5,
            completedMissionIds = setOf("msn_basic_qual", "msn_night_qual", "msn_nco_school"),
            highestRiskSurvived = MissionRisk.LOW
        )
        val sncoBlocked = TacticalCalculator.evaluatePromotionBoard(
            operator = ssgMissingSlc,
            allOperators = listOf(ssgMissingSlc),
            citations = emptyList(),
            gearItems = emptyList(),
            facilities = facilities
        )
        assertEquals(OperatorRank.SFC, sncoBlocked.nextRank)
        assertFalse(sncoBlocked.allMeritsMet)
        assertFalse(sncoBlocked.meritChecks.first { it.id == "school_slc" }.satisfied)
        assertFalse(sncoBlocked.meritChecks.first { it.id == "honors_snco" }.satisfied)

        val ssgQualified = ssgMissingSlc.copy(
            completedMissionIds = ssgMissingSlc.completedMissionIds + "msn_snco_school"
        )
        val staffedCo = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
            .copy(status = OperatorStatus.HQ_STAFF, hqRole = HqStaffRole.CO_COMMANDING_OFFICER)
        val sncoReady = TacticalCalculator.evaluatePromotionBoard(
            operator = ssgQualified,
            allOperators = listOf(ssgQualified, staffedCo),
            citations = listOf(
                AwardedMedal("c2", ssgQualified.id, "medal_nco_dev", "BLC/SLC Graduate", awardedAtMillis = 2L)
            ),
            gearItems = emptyList(),
            facilities = facilities
        )
        assertTrue(sncoReady.allMeritsMet)
        assertTrue(sncoReady.opinions.any { it.reviewerName.contains("KESTREL") })

        // 3. SNCO -> Officer gate requires OCS (msn_ocs_school), XP >= 2000, 8+ Ops, 2+ Honors, Mod+ Combat or 3+ HQ Ops
        val bastionAtSgm = GameCatalog.initialOperators.first { it.id == "op_bastion" }.copy(
            unlocked = true,
            xp = 2000,
            authorizedTier = RankTier.SNCO,
            missionsCompleted = 8,
            highestRiskSurvived = MissionRisk.MODERATE,
            completedMissionIds = setOf("msn_basic_qual", "msn_engineer_school", "msn_nco_school", "msn_snco_school")
        )
        val ocsMissingEval = TacticalCalculator.evaluatePromotionBoard(
            operator = bastionAtSgm,
            allOperators = listOf(bastionAtSgm),
            citations = listOf(
                AwardedMedal("c3", bastionAtSgm.id, "medal_good_conduct", "Service", awardedAtMillis = 3L),
                AwardedMedal("c4", bastionAtSgm.id, "medal_bronze_valor", "Combat valor", awardedAtMillis = 4L)
            ),
            gearItems = emptyList(),
            facilities = facilities
        )
        assertEquals(OperatorRank.LT2, ocsMissingEval.nextRank)
        assertFalse(ocsMissingEval.allMeritsMet)
        assertFalse(ocsMissingEval.meritChecks.first { it.id == "school_ocs" }.satisfied)

        // Verify Good Conduct ribbon still allows SNCO (E-7..E-9)
        assertTrue(
            TacticalCalculator.evaluateAwardEligibility(
                GameCatalog.medals.first { it.id == "medal_good_conduct" },
                bastionAtSgm,
                emptySet()
            ).eligible
        )

        val bastionWithOcs = bastionAtSgm.copy(
            completedMissionIds = bastionAtSgm.completedMissionIds + "msn_ocs_school"
        )
        val ocsReadyEval = TacticalCalculator.evaluatePromotionBoard(
            operator = bastionWithOcs,
            allOperators = listOf(bastionWithOcs),
            citations = listOf(
                AwardedMedal("c3", bastionAtSgm.id, "medal_good_conduct", "Service", awardedAtMillis = 3L),
                AwardedMedal("c4", bastionAtSgm.id, "medal_bronze_valor", "Combat valor", awardedAtMillis = 4L)
            ),
            gearItems = emptyList(),
            facilities = facilities
        )
        assertTrue(ocsReadyEval.allMeritsMet)
        assertEquals(RankTier.OFFICER, ocsReadyEval.nextRank?.tier)

        // Verify SLC requires BLC + NCO, and OCS requires NCO+ & Moderate combat
        val ncoSchool = GameCatalog.missions.first { it.id == "msn_nco_school" }
        val slcSchool = GameCatalog.missions.first { it.id == "msn_snco_school" }
        val ocsSchool = GameCatalog.missions.first { it.id == "msn_ocs_school" }
        assertTrue(
            TacticalCalculator.canOperatorDeployOnMission(
                baseSoldier.copy(completedMissionIds = setOf("msn_signals_school")),
                ncoSchool,
                emptySet()
            ).eligible
        )
        assertFalse(
            TacticalCalculator.canOperatorDeployOnMission(
                baseSoldier.copy(completedMissionIds = setOf("msn_nco_school")),
                slcSchool,
                emptySet()
            ).eligible
        )
        assertTrue(
            TacticalCalculator.canOperatorDeployOnMission(
                ssgMissingSlc,
                slcSchool,
                emptySet()
            ).eligible
        )
        assertFalse(
            TacticalCalculator.canOperatorDeployOnMission(
                ssgMissingSlc, // LOW risk only -> blocked from OCS until Moderate+
                ocsSchool,
                emptySet()
            ).eligible
        )
        assertTrue(
            TacticalCalculator.canOperatorDeployOnMission(
                ssgMissingSlc.copy(highestRiskSurvived = MissionRisk.MODERATE, baseTech = 14, baseResilience = 15),
                ocsSchool,
                emptySet()
            ).eligible
        )
    }

    @Test
    fun flagOfficerPromotionAndOcsDoNotRequireCombatOrExtremeMissions() {
        val facilities = FacilityType.entries.map { FacilityState(it, 3) }

        // 1. Career HQ / Non-combat Colonel promotes to Brigadier General with zero combat history
        val careerStaffColonel = GameCatalog.initialOperators.first { it.id == "op_kestrel" }.copy(
            xp = 7100,
            authorizedTier = RankTier.OFFICER,
            status = OperatorStatus.HQ_STAFF,
            hqRole = HqStaffRole.CO_COMMANDING_OFFICER,
            missionsCompleted = 4,
            staffOpsSupported = 8,
            highestRiskSurvived = null, // Never fought in combat, no Extreme/High missions
            completedMissionIds = setOf("msn_ocs_school", "msn_command_school", "msn_intel_school")
        )
        val citations = listOf(
            AwardedMedal("c1", careerStaffColonel.id, "medal_good_conduct", "Distinguished staff record", awardedAtMillis = 1L),
            AwardedMedal("c2", careerStaffColonel.id, "medal_army_commendation", "Senior command liaison", awardedAtMillis = 2L),
            AwardedMedal("c3", careerStaffColonel.id, "medal_meritorious", "Campaign planning", awardedAtMillis = 3L),
            AwardedMedal("c4", careerStaffColonel.id, "medal_defense_superior", "Theater coordination", awardedAtMillis = 4L)
        )
        val flagEval = TacticalCalculator.evaluatePromotionBoard(
            operator = careerStaffColonel,
            allOperators = listOf(careerStaffColonel),
            citations = citations,
            gearItems = emptyList(),
            facilities = facilities
        )
        assertTrue(flagEval.isCrossTierGate)
        assertEquals(OperatorRank.BG, flagEval.nextRank)
        assertTrue(flagEval.allMeritsMet)
        assertTrue(flagEval.meritChecks.first { it.id == "command_flag" }.satisfied)
        assertTrue(flagEval.consensus != BoardConsensus.BLOCKED)

        // 2. Career HQ Staff NCO enters OCS without combat risk requirement
        val ocsSchool = GameCatalog.missions.first { it.id == "msn_ocs_school" }
        val staffNco = GameCatalog.initialOperators.first { it.id == "op_vesper" }.copy(
            status = OperatorStatus.HQ_STAFF,
            hqRole = HqStaffRole.S1_PERSONNEL_HONORS,
            authorizedTier = RankTier.NCO,
            xp = 900,
            baseTech = 15,
            baseResilience = 15,
            highestRiskSurvived = null,
            staffOpsSupported = 3,
            completedMissionIds = setOf("msn_nco_school")
        )
        assertTrue(
            TacticalCalculator.canOperatorDeployOnMission(
                operator = staffNco,
                mission = ocsSchool,
                deployedOpIds = emptySet()
            ).eligible
        )
    }

    @Test
    fun squadCohesionRulesPrioritizesLeaderCapsAtFourAndBoostsOddsAndGain() {
        val allOps = GameCatalog.initialOperators.take(5).map { it.copy(unlocked = true) }
        val squad = TacticalSquad(
            id = "sq_test",
            name = "Bravo Squad",
            memberIds = allOps.take(4).map { it.id },
            leaderId = allOps[2].id, // 3rd member designated as Squad Leader
            cohesionXp = 650, // VETERAN tier (65%)
            missionsTogether = 7
        )

        // Leader should always be ordered first, capped to maxCount
        val ordered = SquadCohesionRules.orderedMembersForDeployment(
            squad = squad,
            eligibleOperatorIds = allOps.map { it.id }.toSet(),
            maxCount = MAX_SQUAD_SIZE
        )
        assertEquals(squad.memberIds.size, ordered.size)
        assertEquals(allOps[2].id, ordered.first())

        val twoMax = SquadCohesionRules.orderedMembersForDeployment(
            squad = squad,
            eligibleOperatorIds = allOps.map { it.id }.toSet(),
            maxCount = 2
        )
        assertEquals(2, twoMax.size)
        assertEquals(allOps[2].id, twoMax.first())

        // Solo member gets no cohesion bonus
        assertNull(SquadCohesionRules.evaluateActiveBonus(listOf(allOps[2]), listOf(squad)))

        // Full 4-man squad with leader gets full bonus vs leader-absent 60% penalty
        val withLeaderOps = ordered.map { id -> allOps.first { it.id == id } }
        val withoutLeaderOps = listOf(allOps[0], allOps[1], allOps[3])
        val bonusWithLeader = SquadCohesionRules.evaluateActiveBonus(withLeaderOps, listOf(squad))!!
        val bonusNoLeader = SquadCohesionRules.evaluateActiveBonus(withoutLeaderOps, listOf(squad))!!
        assertTrue(bonusWithLeader.leaderPresent)
        assertFalse(bonusNoLeader.leaderPresent)
        assertTrue(bonusWithLeader.efficiencyBonusPct > bonusNoLeader.efficiencyBonusPct)
        assertTrue(bonusWithLeader.successOddsBonusPct > bonusNoLeader.successOddsBonusPct)

        // TacticalCalculator incorporates squad cohesion into efficiency, survival, and mission odds
        val mission = GameCatalog.missions.first { it.id == "msn_citadel_recovery" }
        assertEquals(MAX_SQUAD_SIZE, mission.maxOperators)
        val facilities = FacilityType.entries.map { FacilityState(it, 2) }
        val baseOdds = TacticalCalculator.missionSuccessOddsPct(
            mission, withLeaderOps, emptyList(), emptyList(), facilities, squads = emptyList()
        )
        val cohesiveOdds = TacticalCalculator.missionSuccessOddsPct(
            mission, withLeaderOps, emptyList(), emptyList(), facilities, squads = listOf(squad)
        )
        val cohesiveEff = TacticalCalculator.missionEfficiencyPct(
            mission, withLeaderOps, emptyList(), emptyList(), facilities, squads = listOf(squad)
        )
        val baseEff = TacticalCalculator.missionEfficiencyPct(
            mission, withLeaderOps, emptyList(), emptyList(), facilities, squads = emptyList()
        )
        assertTrue(cohesiveOdds > baseOdds)
        assertTrue(cohesiveEff > baseEff)

        // Cohesion gain increases when leader deploys and squad succeeds
        val gainWithLeader = SquadCohesionRules.calculateCohesionGain(squad, withLeaderOps, mission, missionSucceeded = true)!!
        val gainWithoutLeader = SquadCohesionRules.calculateCohesionGain(squad, withoutLeaderOps, mission, missionSucceeded = true)!!
        assertTrue(gainWithLeader.gainedXp > gainWithoutLeader.gainedXp)
    }

    @Test
    fun freeTailorOverrideUnlocksCrossShoulderCordsAndChestSlotsWithoutBreakingDefaults() {
        val allAwards = GameCatalog.medals
        val infOp = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
        val nonAboveRackBadge = GameCatalog.medals.first {
            it.category == AwardCategory.SCHOOL_INSIGNIA &&
                it.insigniaShape != InsigniaShape.TAB &&
                !it.isAllowedInChestSlot(ChestBadgeSlot.ABOVE_RACK)
        }
        val aboveRackBadge = GameCatalog.medals.first {
            it.isAllowedInChestSlot(ChestBadgeSlot.ABOVE_RACK)
        }

        // Default mode enforces cord shoulder & badge slot regulations
        val strictWorn = TacticalCalculator.resolveWornRegalia(
            // RIGHT_ONLY cord on left shoulder and LEFT_ONLY cord on right shoulder -> ignored under strict rules
            operator = infOp.withManualCords("cord_general_aiguillette", "cord_infantry_blue").copy(
                tailorOverrides = UniformTailorOverrides(
                    pinSlot1 = nonAboveRackBadge.id,
                    pinSlot2 = UniformTailorOverrides.SLOT_NONE,
                    pinSlot3 = UniformTailorOverrides.SLOT_NONE,
                    freeTailorOverride = false
                )
            ),
            awardedMedals = allAwards
        )
        assertNull(strictWorn.leftCord)
        assertNull(strictWorn.rightCord)
        assertFalse(nonAboveRackBadge.id in strictWorn.chestBadges.map { it.id })

        // With Free Custom Override enabled, player can place any earned cord on either shoulder and any earned badge in any chest slot
        val freeWorn = TacticalCalculator.resolveWornRegalia(
            operator = infOp.withManualCords("cord_general_aiguillette", "cord_infantry_blue").copy(
                tailorOverrides = UniformTailorOverrides(
                    pinSlot1 = nonAboveRackBadge.id,
                    pinSlot2 = aboveRackBadge.id,
                    pinSlot3 = UniformTailorOverrides.SLOT_NONE,
                    freeTailorOverride = true
                )
            ),
            awardedMedals = allAwards
        )
        assertEquals("cord_general_aiguillette", freeWorn.leftCord?.id)
        assertEquals("cord_infantry_blue", freeWorn.rightCord?.id)
        assertEquals(listOf(nonAboveRackBadge.id, aboveRackBadge.id), freeWorn.chestBadges.map { it.id })

        // Repeatability rules: medals/ribbons other than Sovereign Star are repeatable; cords & badges are not
        assertTrue(TacticalCalculator.isRepeatableAward(GameCatalog.medals.first { it.id == "medal_purple_shield" }))
        assertFalse(TacticalCalculator.isRepeatableAward(GameCatalog.medals.first { it.id == "medal_sovereign_star" }))
        assertFalse(TacticalCalculator.isRepeatableAward(GameCatalog.medals.first { it.id == "cord_infantry_blue" }))
        assertFalse(TacticalCalculator.isRepeatableAward( aboveRackBadge ))
    }

    @Test
    fun uniformRegaliaHelpersResolveCombatPatchOverseasBarsMarksmanshipLeaderPinDevicesAndInspection() {
        val baseOp = GameCatalog.initialOperators.first { it.id == "op_cipher" }.copy(
            specialty = OperatorSpecialty.ASSAULT,
            missionsCompleted = 0,
            highestRiskSurvived = null
        )

        // Combat patch comes only from campaign history: none without it, newest campaign on Auto,
        // and a manual pick must be a patch the soldier earned
        assertEquals(UnitPatchStyle.NONE, UniformRegaliaRules.resolveCombatPatch(baseOp))
        assertEquals(
            UnitPatchStyle.NONE,
            UniformRegaliaRules.resolveCombatPatch(baseOp.copy(missionsCompleted = 2, highestRiskSurvived = MissionRisk.MODERATE))
        )
        val veteran = baseOp.copy(
            combatPatchHistory = emptyList<CombatPatchRecord>()
                .recordCampaignPatch("camp_a", UnitPatchStyle.DIVISION_1, 100L)
                .recordCampaignPatch("camp_b", UnitPatchStyle.DIVISION_4, 200L)
        )
        assertEquals(UnitPatchStyle.DIVISION_4, UniformRegaliaRules.resolveCombatPatch(veteran))
        assertEquals(
            UnitPatchStyle.DIVISION_1,
            UniformRegaliaRules.resolveCombatPatch(
                veteran.copy(tailorOverrides = UniformTailorOverrides(combatPatch = UnitPatchStyle.DIVISION_1))
            )
        )
        assertEquals(
            UnitPatchStyle.DIVISION_4,
            UniformRegaliaRules.resolveCombatPatch(
                veteran.copy(tailorOverrides = UniformTailorOverrides(combatPatch = UnitPatchStyle.DIVISION_6))
            )
        )

        // Overseas bars: 0 before combat, scales with deployments up to 5, and respects showOverseasBars = false
        assertEquals(0, UniformRegaliaRules.resolveOverseasBars(baseOp))
        assertEquals(
            2,
            UniformRegaliaRules.resolveOverseasBars(baseOp.copy(missionsCompleted = 5, highestRiskSurvived = MissionRisk.LOW))
        )
        assertEquals(
            5,
            UniformRegaliaRules.resolveOverseasBars(baseOp.copy(missionsCompleted = 20, highestRiskSurvived = MissionRisk.HIGH))
        )
        assertEquals(
            0,
            UniformRegaliaRules.resolveOverseasBars(
                baseOp.copy(
                    missionsCompleted = 10,
                    highestRiskSurvived = MissionRisk.HIGH,
                    tailorOverrides = UniformTailorOverrides(showOverseasBars = false)
                )
            )
        )

        // Marksmanship shows the awarded qualification and honors the override
        assertEquals(
            MarksmanshipBadgeTier.EXPERT,
            UniformRegaliaRules.resolveMarksmanshipTier(baseOp.copy(marksmanshipQual = MarksmanshipBadgeTier.EXPERT), emptyList())
        )
        assertEquals(
            MarksmanshipBadgeTier.NONE,
            UniformRegaliaRules.resolveMarksmanshipTier(baseOp.copy(marksmanshipQual = null), emptyList())
        )
        assertEquals(
            MarksmanshipBadgeTier.MARKSMAN,
            UniformRegaliaRules.resolveMarksmanshipTier(
                baseOp.copy(tailorOverrides = UniformTailorOverrides(marksmanshipBadge = MarksmanshipBadgeTier.MARKSMAN)),
                emptyList()
            )
        )

        // Squad leader pin resolves from squad leadership and manual override
        val squad = TacticalSquad(
            id = "sq_alpha",
            name = "Alpha",
            memberIds = listOf("op_kestrel", "op_cipher"),
            leaderId = "op_kestrel"
        )
        assertFalse(UniformRegaliaRules.isLeaderPinShown(baseOp, listOf(squad)))
        assertTrue(UniformRegaliaRules.isLeaderPinShown(baseOp.copy(id = "op_kestrel"), listOf(squad)))
        assertTrue(
            UniformRegaliaRules.isLeaderPinShown(
                baseOp.copy(tailorOverrides = UniformTailorOverrides(leaderRoleOverride = LeaderRole.SQUAD_LEADER)),
                listOf(squad)
            )
        )

        // Award devices: Oak Leaf Clusters & Valor "V"
        val achMedal = GameCatalog.medals.first { it.id == "medal_army_commendation" }
        val valorOp = baseOp.copy(highestRiskSurvived = MissionRisk.HIGH)
        val cits1 = listOf(AwardedMedal("c1", baseOp.id, achMedal.id, "Service", awardedAtMillis = 1L))
        assertEquals(0, UniformRegaliaRules.resolveAwardDevices(baseOp, achMedal.id, cits1).bronzeOakLeaves)

        val cits2 = cits1 + AwardedMedal("c2", baseOp.id, achMedal.id, "Second", awardedAtMillis = 2L)
        val dev2 = UniformRegaliaRules.resolveAwardDevices(baseOp, achMedal.id, cits2)
        assertEquals(1, dev2.bronzeOakLeaves)
        assertEquals(0, dev2.silverOakLeaves)

        val cits6 = (1..6).map { idx -> AwardedMedal("c$idx", baseOp.id, achMedal.id, "Award $idx", awardedAtMillis = idx.toLong()) }
        val dev6 = UniformRegaliaRules.resolveAwardDevices(baseOp, achMedal.id, cits6)
        assertEquals(1, dev6.silverOakLeaves)
        assertEquals(0, dev6.bronzeOakLeaves)
        assertTrue(UniformRegaliaRules.resolveAwardDevices(valorOp, "medal_bronze_valor", listOf(
            AwardedMedal("cv", valorOp.id, "medal_bronze_valor", "Heroism under fire", awardedAtMillis = 1L)
        )).hasValorV)

        // Uniform Inspection & +8% Efficiency Readiness Buff
        val vetOp = GameCatalog.initialOperators.first { it.id == "op_kestrel" }.copy(
            missionsCompleted = 8,
            highestRiskSurvived = MissionRisk.HIGH,
            completedMissionIds = setOf("msn_basic_qual", "msn_ranger_school")
        )
        val report = UniformRegaliaRules.evaluateUniformInspection(
            operators = listOf(vetOp),
            citations = listOf(AwardedMedal("c1", vetOp.id, "medal_silver_vanguard", "Valor", awardedAtMillis = 1L)),
            gearItems = emptyList(),
            squads = listOf(squad),
            subjectSquad = null
        )
        assertTrue(report.scorePct in 1..100)
        assertTrue(report.prestigeEarned > 0)
        assertEquals(180, report.buffDurationSec)

        // A demanding op keeps the veteran below the efficiency cap so the +8 buff shows in full
        val mission = GameCatalog.missions.first { it.id == "msn_deep_expedition" }
        val facilities = FacilityType.entries.map { FacilityState(it, 1) }
        val normalEff = TacticalCalculator.missionEfficiencyPct(
            mission, listOf(vetOp), emptyList(), emptyList(), facilities, inspectionBuffActive = false
        )
        val buffedEff = TacticalCalculator.missionEfficiencyPct(
            mission, listOf(vetOp), emptyList(), emptyList(), facilities, inspectionBuffActive = true
        )
        assertEquals(normalEff + 8, buffedEff)
    }

    @Test
    fun wardrobeSystemEnforcesKitRulesSashRestrictionSmartDutyAndBonuses() {
        val kestrel = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
        val hqKestrel = kestrel.copy(status = OperatorStatus.HQ_STAFF, hqRole = HqStaffRole.CO_COMMANDING_OFFICER)
        val flagOp = GameCatalog.initialOperators.first { it.id == "op_specter" }.copy(
            unlocked = true,
            status = OperatorStatus.READY
        )

        // 1. Smart Duty-Dress ON mapping for Field Operator
        assertEquals(
            WardrobeKit.FIELD,
            TacticalCalculator.resolveEffectiveWardrobeKit(kestrel, smartWardrobeEnabled = true, DutyUniformContext.COMBAT_DEPLOYMENT)
        )
        assertEquals(
            WardrobeKit.BRAVOS,
            TacticalCalculator.resolveEffectiveWardrobeKit(kestrel, smartWardrobeEnabled = true, DutyUniformContext.ON_BASE)
        )
        assertEquals(
            WardrobeKit.BRAVOS,
            TacticalCalculator.resolveEffectiveWardrobeKit(kestrel, smartWardrobeEnabled = true, DutyUniformContext.TRAINING_MISSION)
        )
        assertEquals(
            WardrobeKit.ALPHAS,
            TacticalCalculator.resolveEffectiveWardrobeKit(kestrel, smartWardrobeEnabled = true, DutyUniformContext.HQ_ASSIGNMENT)
        )
        assertEquals(
            WardrobeKit.ALPHAS,
            TacticalCalculator.resolveEffectiveWardrobeKit(kestrel, smartWardrobeEnabled = true, DutyUniformContext.PROMOTION_BOARD)
        )
        assertEquals(
            WardrobeKit.ALPHAS,
            TacticalCalculator.resolveEffectiveWardrobeKit(kestrel, smartWardrobeEnabled = true, DutyUniformContext.MEMORIAL_HONORS)
        )
        assertEquals(
            WardrobeKit.MESS,
            TacticalCalculator.resolveEffectiveWardrobeKit(kestrel, smartWardrobeEnabled = true, DutyUniformContext.DIPLOMATIC_MISSION)
        )

        // 2. Smart Duty-Dress OFF respects manually equipped activeWardrobeKit, while HQ Staff excludes Field
        val manualMessOp = kestrel.copy(activeWardrobeKit = WardrobeKit.MESS)
        assertEquals(
            WardrobeKit.MESS,
            TacticalCalculator.resolveEffectiveWardrobeKit(manualMessOp, smartWardrobeEnabled = false, DutyUniformContext.ON_BASE)
        )
        assertFalse(WardrobeKit.FIELD.isAvailableFor(hqKestrel))
        assertTrue(WardrobeKit.BRAVOS.isAvailableFor(hqKestrel))
        assertEquals(
            WardrobeKit.ALPHAS,
            TacticalCalculator.resolveEffectiveWardrobeKit(
                hqKestrel.copy(activeWardrobeKit = WardrobeKit.FIELD),
                smartWardrobeEnabled = false,
                DutyUniformContext.ON_BASE
            )
        )
        assertEquals(
            WardrobeKit.ALPHAS,
            TacticalCalculator.resolveEffectiveWardrobeKit(
                hqKestrel,
                smartWardrobeEnabled = true,
                DutyUniformContext.COMBAT_DEPLOYMENT
            )
        )

        // 3. Per-kit regulation enforcement: Bravos (Beret + Ribbons + No gloves/sash/cords), Alphas (No Beret), Mess (Cutaway + Mini medals + No Beret/cords/sash)
        val decoratedSquadLeader = kestrel.copy(
            equippedLeftCordId = "cord_infantry_blue",
            equippedRightCordId = "cord_general_aiguillette",
            hasWhiteGloves = true,
            hasDressSash = true,
            headgearStyle = HeadgearStyle.TAN_BERET,
            dressDisplayMode = DressDisplayMode.FULL_MEDALS
        )
        val alphaSquad = TacticalSquad(
            id = "sq_alpha",
            name = "Alpha",
            memberIds = listOf(kestrel.id),
            leaderId = kestrel.id
        )

        val bravosLook = TacticalCalculator.resolveKitAppearance(decoratedSquadLeader, WardrobeKit.BRAVOS, listOf(alphaSquad))
        assertEquals(HeadgearKind.BERET, bravosLook.headgearStyle.kind)
        assertEquals(DressDisplayMode.RIBBONS, bravosLook.dressDisplayMode)
        assertFalse(bravosLook.hasWhiteGloves)
        assertFalse(bravosLook.hasDressSash)
        assertEquals("cord_infantry_blue", bravosLook.equippedLeftCordId)
        assertEquals("cord_general_aiguillette", bravosLook.equippedRightCordId)

        val alphasLook = TacticalCalculator.resolveKitAppearance(decoratedSquadLeader, WardrobeKit.ALPHAS, listOf(alphaSquad))
        assertTrue(alphasLook.headgearStyle.kind != HeadgearKind.BERET)
        assertFalse(alphasLook.dressStyle.isCutawayMess)
        assertTrue(alphasLook.hasWhiteGloves)
        assertTrue(alphasLook.hasDressSash)
        assertEquals("cord_infantry_blue", alphasLook.equippedLeftCordId)

        val messLook = TacticalCalculator.resolveKitAppearance(decoratedSquadLeader, WardrobeKit.MESS, listOf(alphaSquad))
        assertTrue(messLook.dressStyle.isCutawayMess)
        assertTrue(messLook.headgearStyle.kind != HeadgearKind.BERET)
        assertEquals(DressDisplayMode.FULL_MEDALS, messLook.dressDisplayMode)
        assertFalse(messLook.hasDressSash)
        assertNull(messLook.equippedLeftCordId)
        assertNull(messLook.equippedRightCordId)

        assertFalse(TacticalCalculator.evaluateHeadgearEligibility(HeadgearStyle.TAN_BERET, kestrel, setOf("insig_ranger_tab"), WardrobeKit.ALPHAS).eligible)
        assertFalse(TacticalCalculator.evaluateHeadgearEligibility(HeadgearStyle.OFFICER_PEAKED_CAP, kestrel, emptySet(), WardrobeKit.BRAVOS).eligible)
        assertFalse(TacticalCalculator.evaluateDressStyleEligibility(DressUniformStyle.OFFICER_SERVICE_BLUES, kestrel, emptySet(), WardrobeKit.MESS).eligible)
        assertFalse(TacticalCalculator.evaluateDressStyleEligibility(DressUniformStyle.OFFICER_MESS_BLUES, kestrel, emptySet(), WardrobeKit.ALPHAS).eligible)

        // 4. Sam Browne / Waist Sash strictly limited to HQ Staff or Squad Leaders (denied even to Flag Officer if neither)
        assertFalse(TacticalCalculator.evaluateDressSashEligibility(flagOp, emptyList()).eligible)
        assertTrue(TacticalCalculator.evaluateDressSashEligibility(hqKestrel, emptyList()).eligible)
        assertTrue(TacticalCalculator.evaluateDressSashEligibility(kestrel, listOf(alphaSquad)).eligible)

        // 5. Mess Dress Diplomatic Bonus & Bravos Training Bonus
        val facilities = FacilityType.entries.map { FacilityState(it, 2) }
        val diploMission = GameCatalog.missions.first { it.id == "msn_diplo_summit" }
        val fieldInFieldGear = kestrel.copy(activeWardrobeKit = WardrobeKit.FIELD)
        val fieldInMessGear = kestrel.copy(activeWardrobeKit = WardrobeKit.MESS)

        val smartDiploBonus = TacticalCalculator.evaluateMissionKitBonus(diploMission, listOf(fieldInFieldGear), smartWardrobeEnabled = true)
        assertTrue(smartDiploBonus.allMatched)
        assertEquals(8, smartDiploBonus.successOddsBonusPct)
        assertEquals(10, smartDiploBonus.efficiencyBonusPct)
        assertEquals(1.20, smartDiploBonus.prestigeMult, 0.001)
        assertEquals(1.15, smartDiploBonus.creditMult, 0.001)

        val manualWrongKitDiplo = TacticalCalculator.evaluateMissionKitBonus(diploMission, listOf(fieldInFieldGear), smartWardrobeEnabled = false)
        assertFalse(manualWrongKitDiplo.allMatched)
        assertEquals(0, manualWrongKitDiplo.successOddsBonusPct)

        val manualRightKitDiplo = TacticalCalculator.evaluateMissionKitBonus(diploMission, listOf(fieldInMessGear), smartWardrobeEnabled = false)
        assertTrue(manualRightKitDiplo.allMatched)
        // A fresh recruit stays under the odds cap, so the Mess kit bonus is visible
        val envoy = GameCatalog.createPostBasicRecruit("op_envoy", OperatorSpecialty.COMMAND, emptySet(), 0)
        val treaty = GameCatalog.missions.first { it.id == "msn_diplo_treaty" }
        val oddsWithoutMess = TacticalCalculator.missionSuccessOddsPct(
            treaty, listOf(envoy.copy(activeWardrobeKit = WardrobeKit.FIELD)), emptyList(), emptyList(), facilities, smartWardrobeEnabled = false
        )
        val oddsWithMess = TacticalCalculator.missionSuccessOddsPct(
            treaty, listOf(envoy.copy(activeWardrobeKit = WardrobeKit.MESS)), emptyList(), emptyList(), facilities, smartWardrobeEnabled = false
        )
        assertTrue(oddsWithMess > oddsWithoutMess)

        val nightSchool = GameCatalog.missions.first { it.id == "msn_night_qual" }
        // The uniform worn no longer changes school odds
        val fieldPass = TacticalCalculator.schoolPassOddsPct(nightSchool, fieldInFieldGear, emptyList())
        val messPass = TacticalCalculator.schoolPassOddsPct(nightSchool, fieldInMessGear, emptyList())
        assertEquals(fieldPass, messPass)
    }

    @Test
    fun variedAwardCriteriaSeparateValorTiersAndExcludeTraining() {
        val kestrel = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
        val bronze = GameCatalog.medals.first { it.id == "medal_bronze_valor" }
        val silver = GameCatalog.medals.first { it.id == "medal_silver_vanguard" }
        val cross = GameCatalog.medals.first { it.id == "medal_distinguished_cross" }
        val sovereign = GameCatalog.medals.first { it.id == "medal_sovereign_star" }
        val fourragere = GameCatalog.medals.first { it.id == "cord_valor_fourragere" }
        val defSup = GameCatalog.medals.first { it.id == "medal_defense_superior" }
        val meritorious = GameCatalog.medals.first { it.id == "medal_meritorious" }
        val arcom = GameCatalog.medals.first { it.id == "medal_army_commendation" }
        val expRibbon = GameCatalog.medals.first { it.id == "medal_expeditionary" }

        // 1. Completing multiple schools only (0 combat ops) never unlocks Expeditionary, Bronze, Silver, Fourragere, Service Cross, or Sovereign Star
        val schoolOnlyOp = kestrel.copy(
            missionsCompleted = 0,
            combatMissionsCompleted = 0,
            completedMissionIds = setOf("msn_basic_qual", "msn_night_qual", "msn_ranger_school", "msn_ocs_school"),
            highestRiskSurvived = null
        )
        assertFalse(TacticalCalculator.evaluateAwardEligibility(expRibbon, schoolOnlyOp, emptySet()).eligible)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(bronze, schoolOnlyOp, emptySet()).eligible)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(silver, schoolOnlyOp, emptySet()).eligible)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(fourragere, schoolOnlyOp, emptySet()).eligible)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(cross, schoolOnlyOp, emptySet()).eligible)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(arcom, schoolOnlyOp, emptySet()).eligible)

        // 2. Session 15 (item 89): valor awards come only from a valor roll. No service record unlocks them,
        //    and any citation does, whatever the record.
        val decorated = kestrel.copy(
            missionsCompleted = 12,
            combatMissionsCompleted = 12,
            completedMissionIds = setOf("msn_deep_expedition", "msn_citadel_recovery", "msn_convoy_escort"),
            highestRiskSurvived = MissionRisk.EXTREME
        )
        for (valor in listOf(bronze, silver, cross)) {
            assertFalse(valor.id, TacticalCalculator.evaluateAwardEligibility(valor, decorated, emptySet()).eligible)
            assertTrue(valor.id, TacticalCalculator.evaluateAwardEligibility(valor, schoolOnlyOp, emptySet(), heroismCited = true).eligible)
        }
        assertTrue(TacticalCalculator.evaluateAwardEligibility(expRibbon, decorated, emptySet()).eligible)

        // 3. The valor cord follows a valor award, not a mission count
        assertFalse(TacticalCalculator.evaluateAwardEligibility(fourragere, decorated, emptySet()).eligible)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(fourragere, schoolOnlyOp, setOf("medal_bronze_valor")).eligible)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(fourragere, schoolOnlyOp, setOf("medal_sovereign_star")).eligible)

        // 6. Defense Superior rewards Coalition Diplomacy / Long HQ Tenure rather than raw combat ops
        val diploOfficer = kestrel.copy(
            missionsCompleted = 2,
            combatMissionsCompleted = 0,
            completedMissionIds = setOf("msn_diplo_summit", "msn_staff_liaison"),
            highestRiskSurvived = null
        )
        assertTrue(TacticalCalculator.evaluateAwardEligibility(defSup, diploOfficer, emptySet()).eligible)
        assertTrue(TacticalCalculator.evaluateAwardEligibility(meritorious, diploOfficer, emptySet()).eligible)
        assertFalse(TacticalCalculator.evaluateAwardEligibility(bronze, diploOfficer, emptySet()).eligible)

        // 7. Training schools (OCS / SNCO) never recommend Defense Superior or Meritorious Service Medal
        val ocsMission = GameCatalog.missions.first { it.id == "msn_ocs_school" }
        val (ocsAward, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = ocsMission,
            operator = schoolOnlyOp,
            outcome = OperatorOutcomeType.TRAINING_PASSED,
            equippedGear = emptyList(),
            existingCitationMedalIds = setOf("insig_staff_crest"),
            missionSucceeded = true
        )
        assertNull(ocsAward)
    }

    @Test
    fun sequentialCampaignProgressionFullReplayAndRotatingBoard() {
        val camp1 = GameCatalog.campaigns.first { it.id == "camp_veridian" }
        val camp2 = GameCatalog.campaigns.first { it.id == "camp_boreal" }
        val camp3 = GameCatalog.campaigns.first { it.id == "camp_obsidian" }
        val camp4 = GameCatalog.campaigns.first { it.id == "camp_kestrel" }

        // 1. Only Campaign I is unlocked initially; Campaigns II-IV are locked
        assertTrue(TacticalCalculator.isCampaignUnlocked(camp1, emptySet()))
        assertFalse(TacticalCalculator.isCampaignUnlocked(camp2, emptySet()))
        assertFalse(TacticalCalculator.isCampaignUnlocked(camp3, emptySet()))
        assertFalse(TacticalCalculator.isCampaignUnlocked(camp4, emptySet()))
        assertFalse(TacticalCalculator.isAllCampaignsCleared(emptySet()))

        // 2. Stages within Campaign I unlock sequentially
        val c1s1 = GameCatalog.campaignMissionsById.getValue("camp_veridian_1")
        val c1s2 = GameCatalog.campaignMissionsById.getValue("camp_veridian_2")
        val c1s3 = GameCatalog.campaignMissionsById.getValue("camp_veridian_3")
        assertTrue(TacticalCalculator.isCampaignStageUnlocked(c1s1, emptySet(), emptySet()))
        assertFalse(TacticalCalculator.isCampaignStageUnlocked(c1s2, emptySet(), emptySet()))
        assertFalse(TacticalCalculator.isCampaignStageUnlocked(c1s3, setOf("camp_veridian_1"), emptySet()))
        assertTrue(TacticalCalculator.isCampaignStageUnlocked(c1s2, setOf("camp_veridian_1"), emptySet()))
        assertTrue(TacticalCalculator.isCampaignStageUnlocked(c1s3, setOf("camp_veridian_1", "camp_veridian_2"), emptySet()))

        // 3. Clearing Campaign I unlocks Campaign II and applies -8% supply discount; II & IV mix Diplomatic stages while I & III stay combat-only
        val afterCamp1 = setOf("camp_veridian")
        assertTrue(TacticalCalculator.isCampaignUnlocked(camp2, afterCamp1))
        assertFalse(TacticalCalculator.isCampaignUnlocked(camp3, afterCamp1))
        assertEquals(0.92, TacticalCalculator.campaignSupplyCostMultiplier(afterCamp1), 0.0001)
        assertTrue(camp1.stageMissionIds.none { GameCatalog.campaignMissionsById.getValue(it).scope == MissionScope.STAFF_DIPLOMATIC })
        assertEquals(MissionScope.STAFF_DIPLOMATIC, GameCatalog.campaignMissionsById.getValue("camp_boreal_2").scope)
        assertTrue(camp3.stageMissionIds.none { GameCatalog.campaignMissionsById.getValue(it).scope == MissionScope.STAFF_DIPLOMATIC })
        assertEquals(MissionScope.STAFF_DIPLOMATIC, GameCatalog.campaignMissionsById.getValue("camp_kestrel_2").scope)

        // 4. Clearing all 4 campaigns unlocks Full Free Replay across every campaign and stage + all Theater Perks
        val allCleared = GameCatalog.campaigns.map { it.id }.toSet()
        assertTrue(TacticalCalculator.isAllCampaignsCleared(allCleared))
        // (the SF campaign is gated by the SF squad instead; see MissionNamesTest)
        GameCatalog.campaigns.filter { !it.isSpecialForces }.forEach { camp ->
            assertTrue(TacticalCalculator.isCampaignUnlocked(camp, allCleared))
            camp.stageMissionIds.forEach { stageId ->
                val stage = GameCatalog.campaignMissionsById.getValue(stageId)
                assertTrue(TacticalCalculator.isCampaignStageUnlocked(stage, emptySet(), allCleared))
            }
        }
        // Campaign X adds +5% on top of the Boreal and Obsidian perks
        assertEquals(1.155, TacticalCalculator.campaignIntelYieldMultiplier(allCleared), 0.0001)
        assertEquals(1.155, TacticalCalculator.campaignCreditYieldMultiplier(allCleared), 0.0001)
        assertEquals(1.10, TacticalCalculator.campaignPrestigeAndXpMultiplier(allCleared), 0.0001)

        // 5. Rotating standalone board always picks 4 distinct contracts covering low/mod combat, high/extreme combat, and diplomatic/staff
        val seededRandom = kotlin.random.Random(42)
        val firstBatch = TacticalCalculator.pickNextRotatingMissions(previousIds = emptyList(), random = seededRandom)
        assertEquals(TacticalCalculator.ROTATING_BOARD_SIZE, firstBatch.size)
        assertEquals(TacticalCalculator.ROTATING_BOARD_SIZE, firstBatch.toSet().size)
        val firstMissions = firstBatch.map { id -> GameCatalog.rotatingStandalonePool.first { it.id == id } }
        assertTrue(firstMissions.any { it.scope == MissionScope.FIELD_COMBAT && (it.risk == MissionRisk.LOW || it.risk == MissionRisk.MODERATE) })
        assertTrue(firstMissions.any { it.scope == MissionScope.FIELD_COMBAT && (it.risk == MissionRisk.HIGH || it.risk == MissionRisk.EXTREME) })
        assertTrue(firstMissions.any { it.scope == MissionScope.STAFF_DIPLOMATIC })

        val secondBatch = TacticalCalculator.pickNextRotatingMissions(previousIds = firstBatch, random = seededRandom)
        assertEquals(TacticalCalculator.ROTATING_BOARD_SIZE, secondBatch.size)
        assertEquals(TacticalCalculator.ROTATING_BOARD_SIZE, secondBatch.toSet().size)
        assertTrue(secondBatch.any { it !in firstBatch })
    }

    @Test
    fun autoBeretWearsHighestEarnedUnlessPlayerPicked() {
        val base = GameCatalog.initialOperators.first { it.id == "op_kestrel" }.copy(
            completedMissionIds = emptySet(),
            headgearStyle = HeadgearStyle.OBSIDIAN_BERET
        )
        // A stored black beret no longer blocks an upgrade
        val airborne = base.copy(completedMissionIds = setOf("msn_night_qual"))
        assertEquals(HeadgearStyle.MAROON_BERET, TacticalCalculator.defaultHeadgearForKit(WardrobeKit.BRAVOS, airborne, emptySet()))
        // Tan (Ranger) outranks maroon (Airborne)
        val ranger = airborne.copy(completedMissionIds = airborne.completedMissionIds + "msn_ranger_school")
        val rangerOwned = setOf("insig_ranger_tab")
        val rangerAuto = TacticalCalculator.defaultHeadgearForKit(WardrobeKit.BRAVOS, ranger, rangerOwned)
        assertEquals(HeadgearStyle.TAN_BERET, rangerAuto)
        // An explicit pick holds
        assertEquals(
            HeadgearStyle.OBSIDIAN_BERET,
            TacticalCalculator.defaultHeadgearForKit(WardrobeKit.BRAVOS, ranger, rangerOwned, preferred = HeadgearStyle.OBSIDIAN_BERET)
        )
        assertEquals(
            listOf(HeadgearStyle.EMERALD_BERET, HeadgearStyle.TAN_BERET, HeadgearStyle.MAROON_BERET, HeadgearStyle.OBSIDIAN_BERET),
            TacticalCalculator.BERET_LADDER
        )
    }

    @Test
    fun autoCordsFollowMosAndHqStatusAndManualPickOverrides() {
        val medals = GameCatalog.medals.associateBy { it.id }
        val base = GameCatalog.initialOperators.first { it.id == "op_kestrel" }.copy(
            specialty = OperatorSpecialty.ASSAULT,
            status = OperatorStatus.READY,
            equippedLeftCordId = "cord_signals_orange",
            equippedRightCordId = null
        )
        val owned = listOf("cord_signals_orange", "cord_infantry_blue", "cord_valor_fourragere", "cord_general_aiguillette")
            .map { medals.getValue(it) }

        // Auto ignores the stale stored id and wears the MOS-preferred cord; field soldiers wear the valor cord
        val field = TacticalCalculator.resolveWornRegalia(base, owned)
        assertEquals("cord_infantry_blue", field.leftCord?.id)
        assertEquals("cord_valor_fourragere", field.rightCord?.id)

        // HQ staff wear the gold aiguillette first
        val hq = TacticalCalculator.resolveWornRegalia(base.copy(status = OperatorStatus.HQ_STAFF), owned)
        assertEquals("cord_general_aiguillette", hq.rightCord?.id)

        // A newly earned better cord shows without any re-save
        val onlySignals = owned.filter { it.id != "cord_infantry_blue" }
        assertEquals("cord_signals_orange", TacticalCalculator.resolveWornRegalia(base, onlySignals).leftCord?.id)

        // A manual pick overrides Auto; AUTO on one shoulder keeps that shoulder automatic
        val manual = base.copy(status = OperatorStatus.HQ_STAFF)
            .withManualCords("cord_signals_orange", UniformTailorOverrides.SLOT_AUTO)
        val manualWorn = TacticalCalculator.resolveWornRegalia(manual, owned)
        assertEquals("cord_signals_orange", manualWorn.leftCord?.id)
        assertEquals("cord_general_aiguillette", manualWorn.rightCord?.id)
        // "None Worn" is respected
        assertNull(TacticalCalculator.resolveWornRegalia(base.withManualCords(null, null), owned).leftCord)
    }

    @Test
    fun debriefFlagsPromotionOnlyWhenBoardMeritsAreMet() {
        val facilities = FacilityType.entries.map { FacilityState(it, 3) }
        // Enough XP for Sergeant, but no NCO Academy: the board blocks, so the debrief must not flag
        val specialist = GameCatalog.createPostBasicRecruit("op_spc", OperatorSpecialty.ASSAULT, emptySet(), 0)
            .copy(xp = OperatorRank.SGT.xpRequired, authorizedTier = RankTier.SOLDIER, missionsCompleted = 12)
        val board = TacticalCalculator.evaluatePromotionBoard(specialist, listOf(specialist), emptyList(), emptyList(), facilities)
        assertTrue(board.isCrossTierGate)
        assertFalse(board.allMeritsMet)
        assertNull(TacticalCalculator.promotionBoardReadyCode(specialist, listOf(specialist), emptyList(), emptyList(), facilities))
        // Fallen soldiers are never flagged
        assertNull(
            TacticalCalculator.promotionBoardReadyCode(
                specialist.copy(status = OperatorStatus.FALLEN), listOf(specialist), emptyList(), emptyList(), facilities
            )
        )
    }

    @Test
    fun schoolsHaveNoEnrollmentCapButMissionsKeepTeamSizes() {
        val schools = GameCatalog.missions.filter { it.scope == MissionScope.TRAINING }
        assertEquals(22, schools.size)
        schools.forEach { school ->
            assertFalse(school.id, school.hasClassCap)
            assertTrue(school.id, 15 in school.minOperators..school.maxOperators)
            assertEquals("1+", school.teamSizeLabel)
        }
        GameCatalog.missions.filter { it.scope != MissionScope.TRAINING }.forEach { mission ->
            assertTrue(mission.id, mission.hasClassCap)
            // One-squad missions stay at 6 or fewer; platoon missions take 8-12
            assertTrue(mission.id, mission.maxOperators <= if (mission.requiredSquads > 1) 12 else 6)
        }
        // A class of 15 fills from the roster without being trimmed
        val fifteen = (1..15).map { "op_$it" }
        val squad = TacticalSquad(id = "sq", name = "Big", memberIds = fifteen, leaderId = "op_1")
        val sf = GameCatalog.missions.first { it.id == "msn_sf_school" }
        assertEquals(15, SquadCohesionRules.orderedMembersForDeployment(squad, fifteen.toSet(), sf.maxOperators).size)
    }

    @Test
    fun leftCordAutoOrderPutsSfFirstAndFollowsMosAndBillet() {
        val medals = GameCatalog.medals.associateBy { it.id }
        fun cords(vararg ids: String) = ids.map { medals.getValue(it) }
        val base = GameCatalog.initialOperators.first { it.id == "op_kestrel" }.copy(status = OperatorStatus.READY, hqRole = null)
        fun left(op: Operator, owned: List<MedalDefinition>) = TacticalCalculator.resolveWornRegalia(op, owned).leftCord?.id

        // 11B keeps Infantry Blue over other school cords, until SF
        val assault = base.copy(specialty = OperatorSpecialty.ASSAULT)
        val schoolCords = cords("cord_infantry_blue", "cord_engineer_scarlet", "cord_intel_teal")
        assertEquals("cord_infantry_blue", left(assault, schoolCords))
        assertEquals("cord_sf_emerald", left(assault, schoolCords + cords("cord_sf_emerald")))

        // Other MOSs show a school cord before Infantry Blue; their own branch cord leads
        assertEquals("cord_intel_teal", left(base.copy(specialty = OperatorSpecialty.BREACHER), cords("cord_infantry_blue", "cord_intel_teal")))
        assertEquals("cord_engineer_scarlet", left(base.copy(specialty = OperatorSpecialty.BREACHER), schoolCords))
        assertEquals("cord_sf_emerald", left(base.copy(specialty = OperatorSpecialty.RECON), cords("cord_airborne_crimson", "cord_sf_emerald")))
        assertEquals("cord_infantry_blue", left(base.copy(specialty = OperatorSpecialty.MEDIC), cords("cord_infantry_blue")))

        // Command: no billet takes the highest command-related cord (Intel, Logistics, Infantry)
        val command = base.copy(specialty = OperatorSpecialty.COMMAND)
        val commandCords = cords("cord_infantry_blue", "cord_logistics_buff", "cord_intel_teal", "cord_airborne_crimson")
        assertEquals("cord_intel_teal", left(command, commandCords))
        assertEquals("cord_logistics_buff", left(command, cords("cord_infantry_blue", "cord_logistics_buff")))
        // Billets: S-3 Infantry, S-4 Logistics, S-2 Intel; S-1, CO and XO use the no-billet order
        fun inBillet(role: HqStaffRole) = command.copy(status = OperatorStatus.HQ_STAFF, hqRole = role)
        assertEquals("cord_infantry_blue", left(inBillet(HqStaffRole.S3_OPS_DIRECTOR), commandCords))
        assertEquals("cord_logistics_buff", left(inBillet(HqStaffRole.S4_LOGISTICS_MEDEVAC), commandCords))
        assertEquals("cord_intel_teal", left(inBillet(HqStaffRole.S2_INTEL_OVERWATCH), commandCords))
        assertEquals("cord_intel_teal", left(inBillet(HqStaffRole.S1_PERSONNEL_HONORS), commandCords))
        assertEquals("cord_intel_teal", left(inBillet(HqStaffRole.CO_COMMANDING_OFFICER), commandCords))
        assertEquals("cord_intel_teal", left(inBillet(HqStaffRole.XO_CHIEF_OF_STAFF), commandCords))
        // Billet cord not earned yet falls back to the no-billet order; SF still wins
        assertEquals("cord_intel_teal", left(inBillet(HqStaffRole.S3_OPS_DIRECTOR), cords("cord_intel_teal", "cord_airborne_crimson")))
        assertEquals("cord_sf_emerald", left(inBillet(HqStaffRole.S4_LOGISTICS_MEDEVAC), commandCords + cords("cord_sf_emerald")))
        // A CO from another MOS keeps their MOS cord
        val medicCo = base.copy(specialty = OperatorSpecialty.MEDIC, status = OperatorStatus.HQ_STAFF, hqRole = HqStaffRole.CO_COMMANDING_OFFICER)
        assertEquals("cord_medical_maroon", left(medicCo, cords("cord_infantry_blue", "cord_medical_maroon", "cord_intel_teal")))
    }

    @Test
    fun schoolsChargeSuppliesPerSoldierAndMissionsPerTeam() {
        val school = GameCatalog.missions.first { it.id == "msn_ranger_school" }
        val mission = GameCatalog.missions.first { it.scope != MissionScope.TRAINING }
        val one = TacticalCalculator.dispatchSupplyCost(school, 1, 1.0)
        assertEquals(one * 15, TacticalCalculator.dispatchSupplyCost(school, 15, 1.0))
        assertEquals(
            TacticalCalculator.dispatchSupplyCost(mission, 1, 1.0),
            TacticalCalculator.dispatchSupplyCost(mission, 4, 1.0)
        )
    }

    @Test
    fun appearanceIsStablePerSoldierVariedAcrossRosterAndOverridable() {
        val recruits = (0 until 60).map { GameCatalog.createPostBasicRecruit("op_look_$it", OperatorSpecialty.ASSAULT, emptySet(), it) }
        // Same soldier, same look
        recruits.forEach { r -> assertEquals(r.skinTone, r.copy(xp = r.xp + 50).skinTone) }
        // A roster covers the whole palette and every build
        assertEquals(SkinTone.entries.toSet(), recruits.map { it.skinTone }.toSet())
        assertEquals(BodyBuild.entries.toSet(), recruits.map { it.bodyBuild }.toSet())
        // Overrides win; clearing returns to the derived look
        val r = recruits.first()
        assertEquals(SkinTone.DEEP, r.copy(skinToneOverride = SkinTone.DEEP).skinTone)
        assertEquals(BodyBuild.BROAD, r.copy(bodyBuildOverride = BodyBuild.BROAD).bodyBuild)
        assertEquals(r.skinTone, r.copy(skinToneOverride = SkinTone.DEEP).copy(skinToneOverride = null).skinTone)
        // The Tailor request carries the overrides through
        val req = UniformTailorRequest.fromOperator(r.copy(skinToneOverride = SkinTone.FAIR, bodyBuildOverride = BodyBuild.SLIM))
        assertEquals(SkinTone.FAIR, req.skinToneOverride)
        assertEquals(BodyBuild.SLIM, req.bodyBuildOverride)
    }

    @Test
    fun squadsRunTwoToSixWithFullStrengthAndMissionBands() {
        assertEquals(6, MAX_SQUAD_SIZE)
        assertEquals(2, MIN_SQUAD_SIZE)
        // Mission bands: recon/staff/diplomatic 1-3, combat ops 3-6
        GameCatalog.missions.filter { it.scope == MissionScope.STAFF_DIPLOMATIC }.forEach {
            assertEquals(it.id, 1 to 3, it.minOperators to it.maxOperators)
        }
        assertEquals(1 to 3, GameCatalog.missions.first { it.id == "msn_ridge_patrol" }.let { it.minOperators to it.maxOperators })
        assertEquals(3 to 6, GameCatalog.missions.first { it.id == "msn_citadel_recovery" }.let { it.minOperators to it.maxOperators })

        // Average power: six copies of a soldier score the same as three
        val mission = GameCatalog.missions.first { it.id == "msn_citadel_recovery" }
        val facilities = FacilityType.entries.map { FacilityState(it, 2) }
        val troops = (1..6).map { GameCatalog.createPostBasicRecruit("op_t$it", OperatorSpecialty.ASSAULT, emptySet(), 0).copy(id = "op_t$it") }
        assertEquals(
            TacticalCalculator.missionEfficiencyPct(mission, troops.take(3), emptyList(), emptyList(), facilities),
            TacticalCalculator.missionEfficiencyPct(mission, troops, emptyList(), emptyList(), facilities)
        )

        // Full Strength at 4+ deployed members of a 4+ squad
        val squad = TacticalSquad("sq_fs", "Alpha", troops.map { it.id }, troops.first().id, cohesionXp = 300)
        val four = SquadCohesionRules.evaluateActiveBonus(troops.take(4), listOf(squad))!!
        val three = SquadCohesionRules.evaluateActiveBonus(troops.take(3), listOf(squad))!!
        assertTrue(four.fullStrength)
        assertFalse(three.fullStrength)

        // Cohesion headcount: a squad of 6 gains only slightly more than a squad of 4
        assertEquals(60, SquadCohesionRules.cohesionHeadcountXp(4))
        assertEquals(70, SquadCohesionRules.cohesionHeadcountXp(6))
        assertEquals(30, SquadCohesionRules.cohesionHeadcountXp(2))
    }

    @Test
    fun teamLeaderNeedsFivePlusSergeantAndNotTheSquadLeader() {
        fun soldier(i: Int, rank: OperatorRank) = GameCatalog.createPostBasicRecruit("op_tl$i", OperatorSpecialty.ASSAULT, emptySet(), i)
            .copy(id = "op_tl$i", xp = rank.xpRequired, authorizedTier = rank.tier)
        val ops = listOf(soldier(1, OperatorRank.SSG), soldier(2, OperatorRank.SGT), soldier(3, OperatorRank.SPC), soldier(4, OperatorRank.PFC), soldier(5, OperatorRank.PFC))
        val five = TacticalSquad("sq_tl", "Alpha", ops.map { it.id }, ops[0].id, teamLeaderId = ops[1].id)
        assertEquals(ops[1].id, SquadCohesionRules.validTeamLeaderId(five, ops))
        // Under SGT, the squad leader, or a squad of 4 all fail
        assertNull(SquadCohesionRules.validTeamLeaderId(five.copy(teamLeaderId = ops[2].id), ops))
        assertNull(SquadCohesionRules.validTeamLeaderId(five.copy(teamLeaderId = ops[0].id), ops))
        assertNull(SquadCohesionRules.validTeamLeaderId(five.copy(memberIds = ops.take(4).map { it.id }), ops))
        // Dropping below 5 clears it automatically
        val pruned = SquadCohesionRules.pruneUnavailableMembers(listOf(five), ops.take(4)).first()
        assertNull(pruned.teamLeaderId)
        // Uniform roles and the Tailor override
        assertEquals(LeaderRole.SQUAD_LEADER, UniformRegaliaRules.resolveLeaderRole(ops[0], listOf(five)))
        assertEquals(LeaderRole.TEAM_LEADER, UniformRegaliaRules.resolveLeaderRole(ops[1], listOf(five)))
        assertEquals(LeaderRole.NONE, UniformRegaliaRules.resolveLeaderRole(ops[2], listOf(five)))
        val hidden = ops[1].copy(tailorOverrides = UniformTailorOverrides(leaderRoleOverride = LeaderRole.NONE))
        assertEquals(LeaderRole.NONE, UniformRegaliaRules.resolveLeaderRole(hidden, listOf(five)))
        // Team leader adds cohesion when present
        val mission = GameCatalog.missions.first { it.id == "msn_convoy_escort" }
        val withTl = SquadCohesionRules.calculateCohesionGain(five, ops, mission, true)!!.gainedXp
        val withoutTl = SquadCohesionRules.calculateCohesionGain(five.copy(teamLeaderId = null), ops, mission, true)!!.gainedXp
        assertEquals(SquadCohesionRules.TEAM_LEADER_COHESION_XP, withTl - withoutTl)
    }

    @Test
    fun cohesionDropsWithTurnoverAndResetsWhenNobodyRemains() {
        val squad = TacticalSquad("sq_c", "Alpha", listOf("a", "b", "c", "d"), "a", cohesionXp = 800, missionsTogether = 9)
        // Swap one member: keeps 75%
        assertEquals(600, SquadCohesionRules.cohesionAfterRosterChange(squad, listOf("a", "b", "c", "e"), "a"))
        // Swap the leader too: an extra 15% off
        assertEquals(510, SquadCohesionRules.cohesionAfterRosterChange(squad, listOf("e", "b", "c", "d"), "b"))
        // Nobody left: reset
        val fresh = SquadCohesionRules.applyRosterChange(squad, listOf("x", "y"), "x")
        assertEquals(0, fresh.cohesionXp)
        assertEquals(0, fresh.missionsTogether)
        // Losing a member to the memorial costs cohesion too
        val ops = listOf("a", "b", "c").map { id ->
            GameCatalog.createPostBasicRecruit(id, OperatorSpecialty.ASSAULT, emptySet(), 0).copy(id = id)
        }
        val pruned = SquadCohesionRules.pruneUnavailableMembers(listOf(squad), ops).first()
        assertEquals(600, pruned.cohesionXp)
    }

    @Test
    fun unitCitationIsEarnedBySquadsAndWornAfterTransfer() {
        val ops = (1..4).map { GameCatalog.createPostBasicRecruit("op_u$it", OperatorSpecialty.ASSAULT, emptySet(), it).copy(id = "op_u$it") }
        val squad = TacticalSquad("sq_u", "Alpha", ops.map { it.id }, ops[0].id)
        val high = GameCatalog.missions.first { it.id == "msn_citadel_recovery" }
        val moderate = GameCatalog.missions.first { it.id == "msn_convoy_escort" }
        val citation = SquadCohesionRules.earnedUnitCitation(squad, ops, high, true, 1L)
        assertNotNull(citation)
        assertNull(SquadCohesionRules.earnedUnitCitation(squad, ops, moderate, true, 1L))
        assertNull(SquadCohesionRules.earnedUnitCitation(squad, ops, high, false, 1L))
        val oneFallen = ops.mapIndexed { i, o -> if (i == 3) o.copy(status = OperatorStatus.FALLEN) else o }
        assertNull(SquadCohesionRules.earnedUnitCitation(squad, oneFallen, high, true, 1L))
        // Members wear it; a soldier who transfers out keeps it; newcomers don't get it
        val cited = squad.copy(unitCitations = listOf(citation!!))
        val afterTransfer = cited.copy(memberIds = ops.take(3).map { it.id } + "op_new")
        assertTrue(UniformRegaliaRules.hasUnitCitation(ops[3], listOf(afterTransfer)))
        assertFalse(UniformRegaliaRules.hasUnitCitation(ops[0].copy(id = "op_new"), listOf(afterTransfer)))
        // Valor medals alone no longer show it
        assertFalse(UniformRegaliaRules.hasUnitCitation(ops[0], emptyList()))
    }

    @Test
    fun squadStandardAppliesLiveToMembersWithManualOverrideAndFallback() {
        val a = GameCatalog.createPostBasicRecruit("op_sa", OperatorSpecialty.ASSAULT, emptySet(), 1).copy(id = "op_sa")
        val b = GameCatalog.createPostBasicRecruit("op_sb", OperatorSpecialty.ASSAULT, emptySet(), 2).copy(id = "op_sb")
        val squad = TacticalSquad(
            "sq_std", "Alpha", listOf(a.id, b.id), a.id,
            standardCamo = CamoPattern.ARID_SAND,
            standardBravosHeadgear = HeadgearStyle.MAROON_BERET
        )
        // Members wear the standard camo; a soldier outside the squad keeps their own
        assertEquals(CamoPattern.ARID_SAND, TacticalCalculator.resolveKitAppearance(a, WardrobeKit.FIELD, listOf(squad)).camoPattern)
        val leaver = TacticalCalculator.resolveKitAppearance(a, WardrobeKit.FIELD, listOf(squad.copy(memberIds = listOf(b.id))))
        assertEquals(a.camoPattern, leaver.camoPattern)
        // A manual Tailor pick overrides the standard
        val manual = a.copy(wardrobe = a.wardrobe.withKit(WardrobeKit.FIELD, a.wardrobe.field.copy(camoPattern = CamoPattern.ALPINE_FROST)))
        assertEquals(CamoPattern.ALPINE_FROST, TacticalCalculator.resolveKitAppearance(manual, WardrobeKit.FIELD, listOf(squad)).camoPattern)
        // Unqualified for the maroon beret: falls back to Auto and is flagged
        val bravos = TacticalCalculator.resolveKitAppearance(a, WardrobeKit.BRAVOS, listOf(squad))
        assertEquals(HeadgearStyle.OBSIDIAN_BERET, bravos.headgearStyle)
        val flags = TacticalCalculator.squadStandardFallbacks(a, listOf(squad), WardrobeKit.BRAVOS, emptySet())
        assertEquals(1, flags.size)
        assertTrue(flags.first(), flags.first().contains("Black") || flags.first().contains("Obsidian") || flags.first().contains(HeadgearStyle.OBSIDIAN_BERET.label))
        // Once qualified, the standard beret shows with no flag
        val airborne = a.copy(completedMissionIds = a.completedMissionIds + "msn_night_qual")
        assertEquals(HeadgearStyle.MAROON_BERET, TacticalCalculator.resolveKitAppearance(airborne, WardrobeKit.BRAVOS, listOf(squad)).headgearStyle)
        assertTrue(TacticalCalculator.squadStandardFallbacks(airborne, listOf(squad), WardrobeKit.BRAVOS, emptySet()).isEmpty())
        // HQ staff don't follow a field squad standard
        val hq = a.copy(status = OperatorStatus.HQ_STAFF)
        assertNull(TacticalCalculator.standardSquadFor(hq, listOf(squad)))
    }

    @Test
    fun buildingsUnlockByHqLevelCapAtTenAndBuildOnTimers() {
        fun base(hq: Int, vararg extra: Pair<FacilityType, Int>) = FacilityType.entries.map { t ->
            FacilityState(t, extra.toMap()[t] ?: if (t == FacilityType.COMMAND_HQ) hq else t.startLevel)
        }
        val start = base(1)
        // New buildings start as empty lots behind HQ levels
        assertEquals(0, TacticalCalculator.facilityLevel(start, FacilityType.DFAC))
        assertEquals("Requires Command HQ Lv 2", TacticalCalculator.facilityUpgradeBlocker(FacilityType.DFAC, start))
        assertNull(TacticalCalculator.facilityUpgradeBlocker(FacilityType.DFAC, base(2)))
        assertNotNull(TacticalCalculator.facilityUpgradeBlocker(FacilityType.AIRFIELD, base(3)))
        assertNull(TacticalCalculator.facilityUpgradeBlocker(FacilityType.AIRFIELD, base(4)))
        // Cap at 10
        assertEquals("Max level", TacticalCalculator.facilityUpgradeBlocker(FacilityType.ARMORY_WORKSHOP, base(1, FacilityType.ARMORY_WORKSHOP to 10)))
        // Timers: 5 minutes for Lv 2, about 7 hours for Lv 10
        assertEquals(300L, facilityBuildSeconds(2))
        assertTrue(facilityBuildSeconds(10) in (6 * 3600L)..(8 * 3600L))
        // One crew, a second at HQ 5
        val busy = start.map { if (it.type == FacilityType.BARRACKS) it.copy(upgradeEndsAtMillis = 10_000L, upgradeStartedAtMillis = 1L) else it }
        assertNotNull(TacticalCalculator.facilityUpgradeBlocker(FacilityType.ARMORY_WORKSHOP, busy))
        val busyHq5 = busy.map { if (it.type == FacilityType.COMMAND_HQ) it.copy(level = 5) else it }
        assertNull(TacticalCalculator.facilityUpgradeBlocker(FacilityType.ARMORY_WORKSHOP, busyHq5))
        // Timer completion and Rush pricing
        val done = TacticalCalculator.completedUpgrades(busy, 10_000L)
        assertEquals(2, done.single { it.type == FacilityType.BARRACKS }.level)
        assertTrue(TacticalCalculator.completedUpgrades(busy, 9_999L).isEmpty())
        val running = FacilityState(FacilityType.BARRACKS, 4, upgradeEndsAtMillis = 30 * 60_000L)
        assertEquals(30, TacticalCalculator.rushPrestigeCost(running, 0L))
        assertEquals(RUSH_MIN_PRESTIGE, TacticalCalculator.rushPrestigeCost(running, 30 * 60_000L - 1_000L))
    }

    @Test
    fun buildingPerksApply() {
        fun with(t: FacilityType, lv: Int) = FacilityType.entries.map { FacilityState(it, if (it == t) lv else it.startLevel) }
        // Barracks drives the roster
        assertEquals(8, TacticalCalculator.maxFieldRosterSlots(with(FacilityType.BARRACKS, 1)))
        assertEquals(12, TacticalCalculator.maxFieldRosterSlots(with(FacilityType.BARRACKS, 5)))
        // Range: +1%/lv, +2%/lv for Ranger, still capped
        val recruit = GameCatalog.createPostBasicRecruit("op_r", OperatorSpecialty.ASSAULT, emptySet(), 0)
        val signals = GameCatalog.missions.first { it.id == "msn_signals_school" }
        val ranger = GameCatalog.missions.first { it.id == "msn_ranger_school" }
        assertEquals(3, TacticalCalculator.rangeSchoolBonusPct(signals, with(FacilityType.TRAINING_RANGE, 3)))
        assertEquals(6, TacticalCalculator.rangeSchoolBonusPct(ranger, with(FacilityType.TRAINING_RANGE, 3)))
        val noRange = TacticalCalculator.schoolPassOddsPct(signals, recruit, emptyList(), facilities = with(FacilityType.TRAINING_RANGE, 0))
        val range3 = TacticalCalculator.schoolPassOddsPct(signals, recruit, emptyList(), facilities = with(FacilityType.TRAINING_RANGE, 3))
        assertTrue(range3 >= noRange)
        assertTrue(TacticalCalculator.schoolPassOddsPct(ranger, recruit, emptyList(), facilities = with(FacilityType.TRAINING_RANGE, 10)) <= 68)
        // Aid Station and DFAC
        assertEquals(0, TacticalCalculator.aidSaveChancePct(with(FacilityType.AID_STATION, 0)))
        assertEquals(25, TacticalCalculator.aidSaveChancePct(with(FacilityType.AID_STATION, 5)))
        assertTrue(TacticalCalculator.aidRecoveryMultiplier(with(FacilityType.AID_STATION, 5)) < 1.0)
        assertEquals(1.25, TacticalCalculator.dfacCohesionMultiplier(with(FacilityType.DFAC, 5)), 0.001)
        assertTrue(TacticalCalculator.dfacPetitionFactor(with(FacilityType.DFAC, 5)) < 1.0)
        // Unbuilt buildings produce nothing
        assertEquals(0.0, FacilityType.DFAC.productionPerSec(0), 0.0)
    }

    @Test
    fun rifleQualificationIsEarnedStoredAndAwardedByThePlayer() {
        val facNoRange = FacilityType.entries.map { FacilityState(it, it.startLevel) }
        val facRange5 = facNoRange.map { if (it.type == FacilityType.TRAINING_RANGE) it.copy(level = 5) else it }
        val soldier = GameCatalog.createPostBasicRecruit("op_q", OperatorSpecialty.ASSAULT, emptySet(), 0)
            .copy(completedMissionIds = setOf("msn_basic_qual"))
        // Score: own firepower + Range + roll; gear plays no part
        val fp = TacticalCalculator.soldierStats(soldier, emptyList()).firepower
        assertEquals(fp + 7, TacticalCalculator.rifleQualScore(soldier, emptyList(), facNoRange, 7))
        assertEquals(fp + 5 + 7, TacticalCalculator.rifleQualScore(soldier, emptyList(), facRange5, 7))
        // A better result waits for the player; the uniform shows nothing until it's awarded
        val (pendingOp, first) = TacticalCalculator.applyRifleQualification(soldier, SHARPSHOOTER_SCORE, 2)
        assertTrue(first.improved)
        assertEquals(MarksmanshipBadgeTier.SHARPSHOOTER, pendingOp.pendingMarksmanshipQual)
        assertEquals(MarksmanshipBadgeTier.NONE, UniformRegaliaRules.resolveMarksmanshipTier(pendingOp, emptyList()))
        val awarded = pendingOp.copy(marksmanshipQual = pendingOp.pendingMarksmanshipQual, pendingMarksmanshipQual = null)
        assertEquals(MarksmanshipBadgeTier.SHARPSHOOTER, UniformRegaliaRules.resolveMarksmanshipTier(awarded, emptyList()))
        // A lower score never lowers the badge
        val (lower, second) = TacticalCalculator.applyRifleQualification(awarded, 5, 1)
        assertFalse(second.improved)
        assertEquals(MarksmanshipBadgeTier.SHARPSHOOTER, lower.marksmanshipQual)
        assertNull(lower.pendingMarksmanshipQual)
        // Gear no longer changes the badge
        val rifle = GameCatalog.initialGear.first { it.slot == GearSlot.WEAPON }.copy(unlocked = true, tier = 5)
        assertEquals(MarksmanshipBadgeTier.SHARPSHOOTER, UniformRegaliaRules.resolveMarksmanshipTier(awarded, listOf(rifle)))
        // Item 101: requal past the old limit (at extra cost) until Expert, then closed
        val qual = GameCatalog.missions.first { it.id == TacticalCalculator.RIFLE_QUAL_MISSION_ID }
        val graduated = awarded.copy(completedMissionIds = awarded.completedMissionIds + qual.id)
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(graduated, qual, emptySet()).eligible)
        val usedUp = graduated.copy(schoolAttempts = mapOf(qual.id to 3))
        assertTrue(TacticalCalculator.canOperatorDeployOnMission(usedUp, qual, emptySet()).eligible)
        assertTrue(TacticalCalculator.schoolRetrySurcharge(qual, listOf(usedUp), 1.0) > 0)
        val expert = usedUp.copy(marksmanshipQual = MarksmanshipBadgeTier.EXPERT)
        val blocked = TacticalCalculator.canOperatorDeployOnMission(expert, qual, emptySet())
        assertFalse(blocked.eligible)
        assertTrue(blocked.reasonText.contains("Already qualified Expert"))
        // Needs Marksmanship & Tactical Basics first
        assertFalse(TacticalCalculator.canOperatorDeployOnMission(soldier.copy(completedMissionIds = emptySet()), qual, emptySet()).eligible)
    }

    @Test
    fun heroismOddsFollowMissionScopeRiskAndCasualties() {
        fun mission(scope: MissionScope, risk: MissionRisk) =
            GameCatalog.missions.first { it.scope == scope }.copy(scope = scope, risk = risk)
        val combatLow = mission(MissionScope.FIELD_COMBAT, MissionRisk.LOW)
        val combatExtreme = mission(MissionScope.FIELD_COMBAT, MissionRisk.EXTREME)
        assertEquals(TacticalCalculator.HEROISM_ODDS_LOW, TacticalCalculator.heroismOdds(combatLow, OperatorOutcomeType.SAFE, true, false), 1e-9)
        assertEquals(TacticalCalculator.HEROISM_ODDS_EXTREME, TacticalCalculator.heroismOdds(combatExtreme, OperatorOutcomeType.SAFE, true, false), 1e-9)
        assertEquals(TacticalCalculator.HEROISM_ODDS_EXTREME * 2, TacticalCalculator.heroismOdds(combatExtreme, OperatorOutcomeType.FALLEN, false, true), 1e-9)

        val diploLow = mission(MissionScope.STAFF_DIPLOMATIC, MissionRisk.LOW)
        val diploMod = mission(MissionScope.STAFF_DIPLOMATIC, MissionRisk.MODERATE)
        assertEquals(0.0, TacticalCalculator.heroismOdds(diploLow, OperatorOutcomeType.WOUNDED, false, true), 0.0)
        assertEquals(0.0, TacticalCalculator.heroismOdds(diploMod, OperatorOutcomeType.SAFE, true, false), 0.0)
        assertEquals(TacticalCalculator.HEROISM_ODDS_MODERATE, TacticalCalculator.heroismOdds(diploMod, OperatorOutcomeType.SAFE, true, true), 1e-9)
        assertEquals(TacticalCalculator.HEROISM_ODDS_MODERATE, TacticalCalculator.heroismOdds(diploMod, OperatorOutcomeType.SAFE, false, false), 1e-9)

        // Schools and training never roll
        GameCatalog.missions.filter { it.scope == MissionScope.TRAINING || it.risk.isTraining }.forEach { m ->
            assertEquals(0.0, TacticalCalculator.heroismOdds(m, OperatorOutcomeType.TRAINING_PASSED, true, true), 0.0)
            assertEquals(0.0, TacticalCalculator.heroismOdds(m, OperatorOutcomeType.SAFE, false, true), 0.0)
        }
    }

    @Test
    fun repeatableAwardsComeBackButOneTimeAndPendingAwardsDoNot() {
        val kestrel = GameCatalog.initialOperators.first { it.id == "op_kestrel" }
        val extremeOp = GameCatalog.missions.first { it.risk == MissionRisk.EXTREME && it.scope == MissionScope.FIELD_COMBAT }
        val wounded = kestrel.copy(missionsCompleted = 3, combatMissionsCompleted = 3, highestRiskSurvived = MissionRisk.HIGH, timesWounded = 1)
        // Purple Shield already held (and not pending): a new wound earns it again
        val (again, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = extremeOp,
            operator = wounded,
            outcome = OperatorOutcomeType.WOUNDED,
            equippedGear = emptyList(),
            existingCitationMedalIds = setOf("medal_purple_shield"),
            ownedMedalIds = setOf("medal_purple_shield")
        )
        assertEquals("medal_purple_shield", again)
        // Already waiting in Pending Awards: not suggested a second time
        val (pendingSkip, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = extremeOp,
            operator = wounded,
            outcome = OperatorOutcomeType.WOUNDED,
            equippedGear = emptyList(),
            existingCitationMedalIds = setOf("medal_purple_shield"),
            ownedMedalIds = emptySet()
        )
        assertTrue(pendingSkip != "medal_purple_shield")
        // One-time awards stay skipped once held
        val ranger = GameCatalog.missions.first { it.id == "msn_ranger_school" }
        val (tab, _) = TacticalCalculator.recommendedMedalForMissionDeed(
            mission = ranger,
            operator = kestrel,
            outcome = OperatorOutcomeType.TRAINING_PASSED,
            equippedGear = emptyList(),
            existingCitationMedalIds = setOf("insig_ranger_tab"),
            ownedMedalIds = setOf("insig_ranger_tab")
        )
        assertNull(tab)
    }
}
