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
import kotlin.math.max
import kotlin.math.roundToLong

data class AutoGearDistributionResult(
    val updatedGear: List<GearItem>,
    val changedItems: List<GearItem>,
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

    fun distributeFieldGear(
        operators: List<Operator>,
        gearItems: List<GearItem>,
        citations: List<AwardedMedal>,
        facilities: List<FacilityState>,
        deployments: List<ActiveDeployment>
    ): AutoGearDistributionResult {
        val deployedOpIds = deployments.flatMap { it.operatorIds }.toSet() +
            operators.filter { it.status == OperatorStatus.DEPLOYED }.map { it.id }

        val activeFieldOps = operators.filter {
            it.unlocked &&
                !it.status.isOutOfActiveService &&
                it.status != OperatorStatus.HQ_STAFF
        }
        val skippedDeployedCount = activeFieldOps.count { it.id in deployedOpIds }
        val targetOps = activeFieldOps.filter { it.id !in deployedOpIds }

        if (targetOps.isEmpty()) {
            // Still ensure no locked/HQ/retired/fallen operator retains unlocked gear accidentally.
            val cleaned = gearItems.map { gear ->
                val owner = gear.equippedByOperatorId
                if (gear.unlocked && owner != null && owner !in deployedOpIds && operators.none { it.id == owner && it.id !in deployedOpIds && it.unlocked && !it.status.isOutOfActiveService && it.status != OperatorStatus.HQ_STAFF }) {
                    gear.copy(equippedByOperatorId = null)
                } else {
                    gear
                }
            }
            val changed = cleaned.zip(gearItems).filter { (a, b) -> a != b }.map { it.first }
            return AutoGearDistributionResult(
                updatedGear = cleaned,
                changedItems = changed,
                targetOperatorCount = 0,
                outfittedOperatorCount = 0,
                totalEquippedCount = 0,
                skippedDeployedCount = skippedDeployedCount
            )
        }

        val gearMult = TacticalCalculator.gearBonusMultiplier(facilities)
        val intrinsicByOpId = targetOps.associate { op ->
            op.id to intrinsicSkillProfile(op, citations)
        }

        // Determine new owner for every unlocked non-deployed item, slot by slot, using exact max-weight bipartite matching.
        val assignedOwnerByGearId = mutableMapOf<String, String?>()

        for (slot in GearSlot.entries) {
            val poolItems = gearItems.filter { gear ->
                gear.slot == slot &&
                    gear.unlocked &&
                    (gear.equippedByOperatorId == null || gear.equippedByOperatorId !in deployedOpIds)
            }
            if (poolItems.isEmpty()) continue

            val m = targetOps.size
            val k = poolItems.size
            val n = max(m, k)
            val eligible = Array(m) { i ->
                BooleanArray(k) { j ->
                    TacticalCalculator.evaluateGearEquipEligibility(poolItems[j], targetOps[i]).eligible
                }
            }
            val weights = Array(n) { i ->
                LongArray(n) { j ->
                    if (i < m && j < k && eligible[i][j]) {
                        scoreGearForOperator(
                            gear = poolItems[j],
                            operator = targetOps[i],
                            intrinsicSkills = intrinsicByOpId.getValue(targetOps[i].id),
                            gearMult = gearMult
                        )
                    } else {
                        0L
                    }
                }
            }

            val rowToCol = solveMaxWeightMatching(weights, n)
            val matchedGearIds = mutableSetOf<String>()
            for (i in 0 until m) {
                val j = rowToCol[i]
                if (j in 0 until k && eligible[i][j] && weights[i][j] > 0L) {
                    val gear = poolItems[j]
                    assignedOwnerByGearId[gear.id] = targetOps[i].id
                    matchedGearIds += gear.id
                }
            }
            for (gear in poolItems) {
                if (gear.id !in matchedGearIds) {
                    assignedOwnerByGearId[gear.id] = null
                }
            }
        }

        val updatedGear = gearItems.map { gear ->
            if (!gear.unlocked) {
                if (gear.equippedByOperatorId != null) gear.copy(equippedByOperatorId = null) else gear
            } else if (gear.equippedByOperatorId in deployedOpIds) {
                gear
            } else if (gear.id in assignedOwnerByGearId) {
                val nextOwner = assignedOwnerByGearId[gear.id]
                if (gear.equippedByOperatorId != nextOwner) {
                    gear.copy(equippedByOperatorId = nextOwner)
                } else {
                    gear
                }
            } else {
                gear
            }
        }

        val changedItems = updatedGear.zip(gearItems)
            .filter { (after, before) -> after != before }
            .map { it.first }

        val targetIds = targetOps.map { it.id }.toSet()
        val equippedForTargets = updatedGear.filter { it.unlocked && it.equippedByOperatorId in targetIds }
        val outfittedOpCount = equippedForTargets.mapNotNull { it.equippedByOperatorId }.distinct().size

        return AutoGearDistributionResult(
            updatedGear = updatedGear,
            changedItems = changedItems,
            targetOperatorCount = targetOps.size,
            outfittedOperatorCount = outfittedOpCount,
            totalEquippedCount = equippedForTargets.size,
            skippedDeployedCount = skippedDeployedCount
        )
    }

    private fun intrinsicSkillProfile(
        operator: Operator,
        citations: List<AwardedMedal>
    ): StatProfile {
        val rankIndex = com.example.domain.model.OperatorRank.entries.indexOf(operator.rank)
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
        val rankMult = 1.0 + operator.rank.ordinal.coerceAtMost(18) * 0.012
        val trainingDepthMult = 1.0 + operator.completedMissionIds.size.coerceAtMost(12) * 0.008
        score *= (readinessMult * rankMult * trainingDepthMult)

        // Idempotent stability epsilon so identical soldiers do not swap equal kit on repeat taps.
        val stabilityTieBreaker = if (gear.equippedByOperatorId == operator.id) 2L else 0L

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
        }
    }

    /**
     * Exact O(N^3) Kuhn-Munkres / Hungarian algorithm solving maximum-weight bipartite matching.
     * Returns array `rowToCol` where `rowToCol[i]` is the matched column index for row `i`.
     */
    private fun solveMaxWeightMatching(weights: Array<LongArray>, n: Int): IntArray {
        var maxWeight = 0L
        for (i in 0 until n) {
            for (j in 0 until n) {
                if (weights[i][j] > maxWeight) maxWeight = weights[i][j]
            }
        }
        if (maxWeight == 0L) return IntArray(n) { -1 }

        val u = LongArray(n + 1)
        val v = LongArray(n + 1)
        val p = IntArray(n + 1)
        val way = IntArray(n + 1)
        val inf = Long.MAX_VALUE / 4

        for (i in 1..n) {
            p[0] = i
            var j0 = 0
            val minv = LongArray(n + 1) { inf }
            val used = BooleanArray(n + 1)
            do {
                used[j0] = true
                val i0 = p[j0]
                var delta = inf
                var j1 = 0
                for (j in 1..n) {
                    if (!used[j]) {
                        val cost = maxWeight - weights[i0 - 1][j - 1]
                        val cur = cost - u[i0] - v[j]
                        if (cur < minv[j]) {
                            minv[j] = cur
                            way[j] = j0
                        }
                        if (minv[j] < delta) {
                            delta = minv[j]
                            j1 = j
                        }
                    }
                }
                for (j in 0..n) {
                    if (used[j]) {
                        u[p[j]] += delta
                        v[j] -= delta
                    } else {
                        minv[j] -= delta
                    }
                }
                j0 = j1
            } while (p[j0] != 0)

            do {
                val j1 = way[j0]
                p[j0] = p[j1]
                j0 = j1
            } while (j0 != 0)
        }

        val rowToCol = IntArray(n) { -1 }
        for (j in 1..n) {
            if (p[j] in 1..n) {
                rowToCol[p[j] - 1] = j - 1
            }
        }
        return rowToCol
    }
}
