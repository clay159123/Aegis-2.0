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
    val rotationUpdatedAtMillis: Long = 0L
)

@Entity(tableName = "facilities")
data class FacilityEntity(
    @PrimaryKey val typeId: String,
    val level: Int
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
    val equippedByOperatorId: String?,
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
    val squadId: String? = null
)

@Entity(tableName = "squads")
data class SquadEntity(
    @PrimaryKey val id: String,
    val name: String,
    val memberIdsCsv: String,
    val leaderId: String?,
    val cohesionXp: Int = 0,
    val missionsTogether: Int = 0,
    val createdAtMillis: Long = 0L,
    val standardPatchName: String = "AUTO_MOS",
    val standardCamoName: String? = null,
    val standardHeadgearName: String? = null,
    val standardDressStyleName: String? = null,
    val standardBravosHeadgearName: String? = null,
    val standardBravosDressStyleName: String? = null,
    val standardMessDressStyleName: String? = null,
    val standardMessHeadgearName: String? = null,
    val standardWhiteGloves: Boolean? = null
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
