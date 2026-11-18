package com.example.domain

import com.example.domain.model.ActiveDeployment
import com.example.domain.model.AwardedMedal
import com.example.domain.model.FacilityState
import com.example.domain.model.GearItem
import com.example.domain.model.GearSlot
import com.example.domain.model.Operator
import com.example.domain.model.OperatorSpecialty
import com.example.domain.model.OperatorStatus
import com.example.domain.model.StatProfile
import kotlin.math.roundToLong

data class AutoGearDistributionResult(
    val updatedOperators: List<Operator>,
    val changedOperators: List<Operator>,
    val targetOperatorCount: Int,
    val outfittedOperatorCount: Int,
    val totalEquippedCount: Int,
    val skippedDeployedCount: Int
)

object GearAutoDistributor {

    private data class SpecialtyWeights(
        val firepower: Double,
        val stealth: Double,
        val tech: Double,
        val resilience: Double,
        val survival: Double,
        val weightPenalty: Double
    )

    /** Field soldiers who carry kit: unlocked, active and not on HQ staff. */
    fun carriesFieldKit(operator: Operator): Boolean =
        operator.unlocked &&
            !operator.status.isOutOfActiveService &&
            operator.status != OperatorStatus.HQ_STAFF

    /**
     * Gear types are shared, so every standby field soldier independently gets the highest-scoring
     * unlocked item they qualify for in every slot. A slot stays empty only when nothing qualifies.
     * Deployed soldiers keep their kit; HQ staff, retired, fallen and locked soldiers carry none.
     */
    fun distributeFieldGear(
        operators: List<Operator>,
        gearItems: List<GearItem>,
        citations: List<AwardedMedal>,
        facilities: List<FacilityState>,
        deployments: List<ActiveDeployment>
    ): AutoGearDistributionResult {
        val deployedOpIds = deployments.flatMap { it.operatorIds }.toSet() +
            operators.filter { it.status == OperatorStatus.DEPLOYED }.map { it.id }
        val unlockedGear = gearItems.filter { it.unlocked }
        val gearMult = TacticalCalculator.gearBonusMultiplier(facilities)

        val fieldOps = operators.filter { carriesFieldKit(it) }
        val skippedDeployedCount = fieldOps.count { it.id in deployedOpIds }
        val targetIds = fieldOps.filter { it.id !in deployedOpIds }.map { it.id }.toSet()

        val updatedOperators = operators.map { op ->
            when {
                op.id in deployedOpIds -> op
                op.id !in targetIds -> if (op.equippedGearIds.isEmpty()) op else op.copy(equippedGearIds = emptyMap())
                else -> {
                    val skills = intrinsicSkillProfile(op, citations)
                    val loadout = GearSlot.entries.mapNotNull { slot ->
                        unlockedGear
                            .filter { it.slot == slot && TacticalCalculator.evaluateGearEquipEligibility(it, op).eligible }
                            .maxByOrNull { scoreGearForOperator(it, op, skills, gearMult) }
                            ?.let { slot to it.id }
                    }.toMap()
                    if (loadout == op.equippedGearIds) op else op.copy(equippedGearIds = loadout)
                }
            }
        }

        val beforeById = operators.associateBy { it.id }
        val changedOperators = updatedOperators.filter { it != beforeById[it.id] }
        val targets = updatedOperators.filter { it.id in targetIds }

        return AutoGearDistributionResult(
            updatedOperators = updatedOperators,
            changedOperators = changedOperators,
            targetOperatorCount = targetIds.size,
            outfittedOperatorCount = targets.count { it.equippedGearIds.isNotEmpty() },
            totalEquippedCount = targets.sumOf { it.equippedGearIds.size },
            skippedDeployedCount = skippedDeployedCount
        )
    }

    /**
     * Why [operator] has nothing in [slot], or null when something they qualify for is unlocked.
     */
    fun emptySlotReason(operator: Operator, slot: GearSlot, gearItems: List<GearItem>): String? {
        val inSlot = gearItems.filter { it.slot == slot }
        val unlocked = inSlot.filter { it.unlocked }
        if (unlocked.any { TacticalCalculator.evaluateGearEquipEligibility(it, operator).eligible }) return null
        if (unlocked.isEmpty()) {
            return if (inSlot.isEmpty()) "No ${slot.label} in the catalog" else "No ${slot.label} unlocked in the Armory yet"
        }
        val needs = unlocked.mapNotNull { gear ->
            TacticalCalculator.evaluateGearEquipEligibility(gear, operator).missingRequirements.firstOrNull()
        }.distinct()
        return "Not qualified for any unlocked ${slot.label}: " + needs.joinToString(" or ")
    }

    private fun intrinsicSkillProfile(
        operator: Operator,
        citations: List<AwardedMedal>
    ): StatProfile {
        val rankIndex = operator.rank.seniorityIndex
        val rankBonus = rankIndex + operator.rank.tier.order * 2
        val opMedals = citations.filter { it.operatorId == operator.id }
            .mapNotNull { cit -> com.example.data.catalog.GameCatalog.medals.find { it.id == cit.medalId } }
        var medFp = 0
        var medSt = 0
        var medTc = 0
        var medRs = 0
        for (m in opMedals) {
            medFp += m.firepowerBonus
            medSt += m.stealthBonus
            medTc += m.techBonus
            medRs += m.resilienceBonus
        }
        return StatProfile(
            firepower = operator.baseFirepower + rankBonus + medFp,
            stealth = operator.baseStealth + rankBonus + medSt,
            tech = operator.baseTech + rankBonus + medTc,
            resilience = operator.baseResilience + rankBonus + medRs
        )
    }

    internal fun scoreGearForOperator(
        gear: GearItem,
        operator: Operator,
        intrinsicSkills: StatProfile,
        gearMult: Double
    ): Long {
        val mos = when (operator.specialty) {
            OperatorSpecialty.ASSAULT -> SpecialtyWeights(1.45, 0.90, 0.70, 1.25, 1.15, 0.0)
            OperatorSpecialty.RECON -> SpecialtyWeights(1.10, 1.60, 1.05, 0.80, 1.00, 14.0)
            OperatorSpecialty.SIGNALS -> SpecialtyWeights(0.70, 1.15, 1.65, 0.90, 1.00, 10.0)
            OperatorSpecialty.BREACHER -> SpecialtyWeights(1.35, 0.65, 1.20, 1.50, 1.15, 0.0)
            OperatorSpecialty.MEDIC -> SpecialtyWeights(0.65, 0.90, 1.25, 1.60, 1.45, 5.0)
            OperatorSpecialty.COMMAND -> SpecialtyWeights(1.20, 1.00, 1.20, 1.20, 1.20, 4.0)
            // Aviators: light kit, comms and optics over heavy armor and big guns
            OperatorSpecialty.AVIATION -> SpecialtyWeights(0.70, 1.10, 1.55, 0.85, 1.10, 8.0)
        }

        // Blend MOS doctrine weights (70%) with individual operator's earned skill & medal distribution (30%).
        val avgSkill = ((intrinsicSkills.firepower + intrinsicSkills.stealth +
            intrinsicSkills.tech + intrinsicSkills.resilience) / 4.0).coerceAtLeast(1.0)
        val fpBias = (intrinsicSkills.firepower / avgSkill).coerceIn(0.75, 1.35)
        val stBias = (intrinsicSkills.stealth / avgSkill).coerceIn(0.75, 1.35)
        val tcBias = (intrinsicSkills.tech / avgSkill).coerceIn(0.75, 1.35)
        val rsBias = (intrinsicSkills.resilience / avgSkill).coerceIn(0.75, 1.35)

        val wFp = mos.firepower * 0.70 + fpBias * 0.30
        val wSt = mos.stealth * 0.70 + stBias * 0.30
        val wTc = mos.tech * 0.70 + tcBias * 0.30
        val wRs = mos.resilience * 0.70 + rsBias * 0.30

        val weightedStatPoints =
            (gear.firepower * gearMult) * wFp +
                (gear.stealth * gearMult) * wSt +
                (gear.tech * gearMult) * wTc +
                (gear.resilience * gearMult) * wRs +
                gear.survivalBonusPct * mos.survival

        var score = weightedStatPoints * 100.0

        // School-training & MOS role synergy (breaks ties cleanly & rewards sending trained specialists).
        score += schoolTrainingSynergy(gear, operator)
        score += tacticalRoleAffinity(gear, operator)

        // Lightweight agility tie-breaker for recon/signals/command kits.
        score -= gear.weightKg * mos.weightPenalty

        // Readiness & veteran priority scalar: Ready frontline soldiers claim top gear over resting Medbay;
        // higher rank & multi-school veterans break remaining ties while keeping clear specialist matches intact.
        val readinessMult = if (operator.status == OperatorStatus.READY) 1.12 else 1.0
        val rankMult = 1.0 + operator.rank.seniorityIndex.coerceAtMost(18) * 0.012
        val trainingDepthMult = 1.0 + operator.completedMissionIds.size.coerceAtMost(12) * 0.008
        score *= (readinessMult * rankMult * trainingDepthMult)

        // Idempotent stability epsilon so identical soldiers do not swap equal kit on repeat taps.
        val stabilityTieBreaker = if (operator.equippedGearIds[gear.slot] == gear.id) 2L else 0L

        return score.roundToLong().coerceAtLeast(10L) + stabilityTieBreaker
    }

    private fun schoolTrainingSynergy(gear: GearItem, operator: Operator): Double {
        val schools = operator.completedMissionIds
        var bonus = 0.0

        when (gear.requiredSchoolId) {
            "msn_medical_school" -> {
                bonus += if (operator.specialty == OperatorSpecialty.MEDIC) 280.0 else 120.0
            }
            "msn_engineer_school" -> {
                bonus += when (operator.specialty) {
                    OperatorSpecialty.BREACHER -> 280.0
                    OperatorSpecialty.ASSAULT -> 140.0
                    else -> 90.0
                }
            }
            "msn_signals_school" -> {
                bonus += when (operator.specialty) {
                    OperatorSpecialty.SIGNALS -> 280.0
                    OperatorSpecialty.RECON, OperatorSpecialty.COMMAND -> 140.0
                    else -> 90.0
                }
            }
            "msn_night_qual" -> {
                bonus += when (operator.specialty) {
                    OperatorSpecialty.RECON -> 210.0
                    OperatorSpecialty.ASSAULT, OperatorSpecialty.SIGNALS -> 130.0
                    else -> 80.0
                }
            }
            "msn_ranger_school" -> {
                bonus += when (operator.specialty) {
                    OperatorSpecialty.RECON, OperatorSpecialty.ASSAULT -> 220.0
                    OperatorSpecialty.COMMAND -> 160.0
                    else -> 100.0
                }
            }
            "msn_sf_school" -> {
                bonus += when (operator.specialty) {
                    OperatorSpecialty.ASSAULT, OperatorSpecialty.RECON -> 240.0
                    OperatorSpecialty.COMMAND -> 170.0
                    else -> 120.0
                }
            }
        }

        // Additional cross-training bonuses based on completed schools even on standard-issue gear.
        if ("msn_medical_school" in schools && gear.survivalBonusPct >= 10) {
            bonus += 90.0
        }
        if (("msn_signals_school" in schools || "msn_intel_school" in schools) &&
            (gear.slot == GearSlot.OPTICS || gear.tech >= 5)
        ) {
            bonus += 85.0
        }
        if ("msn_night_qual" in schools && gear.stealth >= 4) {
            bonus += 70.0
        }
        if ("msn_engineer_school" in schools && gear.resilience >= 5) {
            bonus += 70.0
        }

        return bonus
    }

    private fun tacticalRoleAffinity(gear: GearItem, operator: Operator): Double {
        return when (operator.specialty) {
            OperatorSpecialty.MEDIC -> when (gear.id) {
                "utl_cls_aid_bag", "utl_ifak_trauma" -> 180.0
                else -> 0.0
            }
            OperatorSpecialty.SIGNALS -> when (gear.id) {
                "utl_micro_uav", "hlm_mesh_comms", "hlm_ops_visor", "opt_sigint_lrf" -> 160.0
                else -> 0.0
            }
            OperatorSpecialty.RECON -> when (gear.id) {
                "opt_pvs31_nvg", "arm_recon_rig", "utl_suppressor", "wpn_mk20_ssr", "wpn_mk14_ebr" -> 140.0
                else -> 0.0
            }
            OperatorSpecialty.BREACHER -> when (gear.id) {
                "utl_breacher_kit", "arm_avs_heavy", "wpn_m249_saw" -> 170.0
                else -> 0.0
            }
            OperatorSpecialty.ASSAULT -> when (gear.id) {
                "wpn_sf_urg_i", "wpn_hk416_cqb", "arm_ranger_crye", "arm_sf_spear" -> 120.0
                else -> 0.0
            }
            OperatorSpecialty.COMMAND -> when (gear.id) {
                "utl_ifak_trauma", "hlm_ops_visor", "arm_jpc_light", "wpn_m4_carbine" -> 90.0
                else -> 0.0
            }
            OperatorSpecialty.AVIATION -> when (gear.id) {
                "arm_jpc_light", "hlm_mesh_comms", "hlm_ops_visor", "opt_pvs31_nvg", "utl_ifak_trauma" -> 130.0
                "arm_avs_heavy", "wpn_m249_saw" -> -120.0
                else -> 0.0
            }
        }
    }
}
