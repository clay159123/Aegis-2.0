package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "commander_state")
data class CommanderStateEntity(
    @PrimaryKey val id: Int = 1,
    val credits: Double,
    val supplies: Double,
    val intel: Double,
    val prestige: Double,
    val totalMissionsCompleted: Int,
    val lastSimulatedAtMillis: Long,
    val veteranLegacyBps: Int = 0,
    val retiredVeteransCount: Int = 0,
    val lastRetirementAtMillis: Long = 0L,
    val lastInspectionAtMillis: Long = 0L,
    val inspectionBuffUntilMillis: Long = 0L,
    val smartWardrobeEnabled: Boolean = true,
    val completedCampaignStageIdsCsv: String = "",
    val completedCampaignIdsCsv: String = "",
    val rotatingMissionIdsCsv: String = "",
    val rotationUpdatedAtMillis: Long = 0L,
    val saveFormatVersion: Int = 0,
    /** Item 69: the waiting soldier event, the last event check, and command-wide event effects. */
    val pendingEventCsv: String = "",
    val lastEventCheckAtMillis: Long = 0L,
    val heloGroundedUntilMillis: Long = 0L,
    val recruitDiscountPct: Int = 0,
    val sfRehearsalBonusPct: Int = 0
)

@Entity(tableName = "facilities")
data class FacilityEntity(
    @PrimaryKey val typeId: String,
    val level: Int,
    val upgradeEndsAtMillis: Long = 0L,
    val upgradeStartedAtMillis: Long = 0L
)

@Entity(tableName = "operators")
data class OperatorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val callsign: String,
    val branchName: String,
    val xp: Int,
    val authorizedTierName: String = "SOLDIER",
    val specialtyName: String,
    val statusName: String,
    val recoveryUntilMillis: Long,
    val transferRequested: Boolean = false,
    val retirementRequested: Boolean = false,
    val petitionCooldownUntilOps: Int = 0,
    val staffOpsSupported: Int = 0,
    val hqRoleName: String? = null,
    val baseFirepower: Int,
    val baseStealth: Int,
    val baseTech: Int,
    val baseResilience: Int,
    val uniformModeName: String,
    val dressDisplayModeName: String,
    val camoPatternName: String,
    val dressStyleName: String,
    val headgearStyleName: String,
    val equippedLeftCordId: String?,
    val equippedRightCordId: String?,
    val hasWhiteGloves: Boolean,
    val hasDressSash: Boolean,
    val tailorOverridesCsv: String = "",
    val activeWardrobeKitName: String = "ALPHAS",
    val wardrobeCsv: String = "",
    val missionsCompleted: Int,
    val combatMissionsCompleted: Int = 0,
    val completedMissionIdsCsv: String = "",
    val schoolAttemptsCsv: String = "",
    val highestRiskName: String? = null,
    val timesWounded: Int = 0,
    val combatPatchHistoryCsv: String = "",
    val equippedGearCsv: String = "",
    val skinToneName: String? = null,
    val marksmanshipQualName: String? = null,
    val pendingMarksmanshipQualName: String? = null,
    val lastQualScore: Int = 0,
    val warrantGrade: Int = 0,
    val flagGrade: Int = 0,
    val flightSkill: Int = 0,
    val combatSortiesFlown: Int = 0,
    val combatJumps: Int = 0,
    val expPointsFirepower: Int = 0,
    val expPointsStealth: Int = 0,
    val expPointsTech: Int = 0,
    val expPointsResilience: Int = 0,
    val retainedUnitAwardsCsv: String = "",
    val flightHoursLogged: Int = 0,
    val goodConductStreak: Int = 0,
    val serviceActions: Int = 0,
    val lastArcomAtAction: Int = -100,
    val lastAamAtAction: Int = -100,
    val bodyBuildName: String? = null,
    val eventBuffsCsv: String = "",
    val unlocked: Boolean,
    val recruitCreditCost: Double,
    val recruitIntelCost: Double,
    val bio: String
)

@Entity(tableName = "gear_items")
data class GearEntity(
    @PrimaryKey val id: String,
    val name: String,
    val slotName: String,
    val tier: Int,
    val maxTier: Int,
    val weightKg: Double,
    val firepowerPerTier: Int,
    val stealthPerTier: Int,
    val techPerTier: Int,
    val resiliencePerTier: Int,
    val survivalBonusPct: Int,
    val unlocked: Boolean,
    val baseCreditCost: Double,
    val baseSupplyCost: Double,
    val description: String
)

@Entity(tableName = "deployments")
data class DeploymentEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val operatorIdsCsv: String,
    val startedAtMillis: Long,
    val durationMillis: Long,
    val efficiencyPct: Int,
    val avgSurvivalPct: Int,
    val squadId: String? = null,
    val insertionName: String = "GROUND",
    val pilotId: String? = null,
    val platoonLeaderId: String? = null,
    val missionTitle: String? = null,
    val flightHours: Int = 0
)

@Entity(tableName = "squads")
data class SquadEntity(
    @PrimaryKey val id: String,
    val name: String,
    val memberIdsCsv: String,
    val leaderId: String?,
    val teamLeaderId: String? = null,
    val unitCitationsCsv: String = "",
    val cohesionXp: Int = 0,
    val missionsTogether: Int = 0,
    val createdAtMillis: Long = 0L,
    val standardPatchName: String = "DIVISION_1",
    val standardCamoName: String? = null,
    val standardHeadgearName: String? = null,
    val standardDressStyleName: String? = null,
    val standardBravosHeadgearName: String? = null,
    val standardBravosDressStyleName: String? = null,
    val standardMessDressStyleName: String? = null,
    val standardMessHeadgearName: String? = null,
    val standardAlphasGloves: Boolean? = null,
    val standardMessGloves: Boolean? = null,
    val isSpecialForces: Boolean = false
)

@Entity(tableName = "citations")
data class CitationEntity(
    @PrimaryKey val id: String,
    val operatorId: String,
    val medalId: String,
    val citationNote: String,
    val missionTitle: String,
    val awardedAtMillis: Long
)

@Entity(tableName = "pending_deeds")
data class PendingDeedEntity(
    @PrimaryKey val id: String,
    val operatorId: String,
    val missionTitle: String,
    val deedSummary: String,
    val recommendedMedalId: String,
    val createdAtMillis: Long
)
