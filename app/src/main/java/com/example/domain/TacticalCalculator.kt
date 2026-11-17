package com.example.domain

import com.example.data.catalog.GameCatalog
import com.example.domain.model.*
import kotlin.math.roundToInt

object TacticalCalculator {

    const val MAX_WORN_NECK_MEDALS = 1
    const val MAX_WORN_CORDS_PER_SHOULDER = 1
    const val MAX_WORN_SHOULDER_TABS = 3
    const val MAX_WORN_CHEST_BADGES = 3
    const val MAX_WORN_HANGING_MEDALS = 4
    const val MAX_WORN_RIBBONS = 9

    // Per-soldier uniqueness cap: Sovereign Star of Aegis is limited to 1 per soldier (multiple soldiers may earn it)
    const val MAX_SOVEREIGN_STAR_PER_SOLDIER = 1
    const val SCHOOL_BASELINE_PASS_BONUS = 4
    const val STANDALONE_ROTATION_INTERVAL_MILLIS = 4L * 60_000L // 4 minutes
    const val ROTATION_REFRESH_INTEL_COST = 15.0

    fun campaignCapForAward(medalId: String): Int? = null

    /** Ribbons and medals that can only ever be earned once per soldier. */
    val ONE_TIME_RIBBON_IDS: Set<String> = setOf(
        "medal_basic_training", // Army Service Ribbon
        "medal_night_owl"       // Night Ops Qualification Ribbon
    )

    /**
     * How many times one soldier can hold an award, or null for no limit. One-time awards
     * (every school insignia, every shoulder cord, the Sovereign Star and the ribbons in
     * [ONE_TIME_RIBBON_IDS]) are capped at 1. Everything else stays repeatable and stacks
     * through the oak leaf device system.
     */
    fun perSoldierCapForAward(medalId: String): Int? {
        if (medalId == "medal_sovereign_star") return MAX_SOVEREIGN_STAR_PER_SOLDIER
        if (medalId in ONE_TIME_RIBBON_IDS) return 1
        val category = GameCatalog.medals.find { it.id == medalId }?.category ?: return null
        return when (category) {
            AwardCategory.SCHOOL_INSIGNIA, AwardCategory.SHOULDER_CORD -> 1
            AwardCategory.MEDAL, AwardCategory.RIBBON -> null
        }
    }

    /** SF-qualified soldiers count as infantry for the CIB and EIB, whatever their MOS. */
    fun isSfQualified(operator: Operator): Boolean = "msn_sf_school" in operator.completedMissionIds

    fun isInfantryOrSfQualified(operator: Operator): Boolean =
        operator.specialty == OperatorSpecialty.ASSAULT || isSfQualified(operator)

    /** The combat badge this soldier earns by surviving combat: CIB, CMB or CAB. */
    fun combatBadgeIdFor(operator: Operator): String = when {
        operator.specialty == OperatorSpecialty.MEDIC -> "insig_combat_medic"
        isInfantryOrSfQualified(operator) -> "insig_cib"
        else -> "insig_cab"
    }

    fun isAllCampaignsCleared(completedCampaignIds: Set<String>): Boolean =
        GameCatalog.campaigns.isNotEmpty() &&
            GameCatalog.campaigns.all { it.id in completedCampaignIds }

    fun isCampaignUnlocked(
        campaign: TheaterCampaign,
        completedCampaignIds: Set<String>
    ): Boolean {
        if (isAllCampaignsCleared(completedCampaignIds)) return true
        if (campaign.order <= 1) return true
        val prevCampaign = GameCatalog.campaigns.find { it.order == campaign.order - 1 } ?: return true
        return prevCampaign.id in completedCampaignIds
    }

    fun isCampaignStageUnlocked(
        mission: MissionTemplate,
        completedStageIds: Set<String>,
        completedCampaignIds: Set<String>
    ): Boolean {
        val campId = mission.campaignId ?: return true
        if (isAllCampaignsCleared(completedCampaignIds)) return true
        val campaign = GameCatalog.campaigns.find { it.id == campId } ?: return true
        if (!isCampaignUnlocked(campaign, completedCampaignIds)) return false
        val stageOrder = mission.campaignStageOrder ?: 1
        if (stageOrder <= 1) return true
        val prevStageId = campaign.stageMissionIds.getOrNull(stageOrder - 2) ?: return true
        return prevStageId in completedStageIds
    }

    fun campaignSupplyCostMultiplier(completedCampaignIds: Set<String>): Double =
        if ("camp_veridian" in completedCampaignIds) 0.92 else 1.0

    fun campaignIntelYieldMultiplier(completedCampaignIds: Set<String>): Double =
        if ("camp_boreal" in completedCampaignIds) 1.10 else 1.0

    fun campaignCreditYieldMultiplier(completedCampaignIds: Set<String>): Double =
        if ("camp_obsidian" in completedCampaignIds) 1.10 else 1.0

    fun campaignPrestigeAndXpMultiplier(completedCampaignIds: Set<String>): Double =
        if ("camp_kestrel" in completedCampaignIds) 1.10 else 1.0

    fun pickNextRotatingMissions(
        previousIds: List<String> = emptyList(),
        random: kotlin.random.Random = kotlin.random.Random
    ): List<String> {
        val pool = GameCatalog.rotatingStandalonePool
        if (pool.size <= 4) return pool.map { it.id }

        val prevSet = previousIds.toSet()
        val picked = mutableListOf<MissionTemplate>()

        fun pickFrom(candidates: List<MissionTemplate>) {
            val eligible = candidates.filter { it !in picked }
            if (eligible.isEmpty()) return
            val fresh = eligible.filter { it.id !in prevSet }
            val source = if (fresh.isNotEmpty()) fresh else eligible
            picked += source[random.nextInt(source.size)]
        }

        // Balanced 4-contract rotating board:
        // 1. Entry/Moderate Field Combat, 2. High/Extreme Field Combat, 3. Diplomatic/Staff, 4. Wildcard
        pickFrom(
            pool.filter {
                it.scope == MissionScope.FIELD_COMBAT &&
                    (it.risk == MissionRisk.LOW || it.risk == MissionRisk.MODERATE)
            }
        )
        pickFrom(
            pool.filter {
                it.scope == MissionScope.FIELD_COMBAT &&
                    (it.risk == MissionRisk.HIGH || it.risk == MissionRisk.EXTREME)
            }
        )
        pickFrom(pool.filter { it.scope == MissionScope.STAFF_DIPLOMATIC })
        pickFrom(pool)

        return picked.map { it.id }
    }

    private val posthumousAllowedAwardIds: Set<String> = setOf(
        "medal_sovereign_star",
        "medal_distinguished_cross",
        "medal_silver_vanguard",
        "medal_purple_shield",
        "medal_bronze_valor",
        "medal_army_commendation",
        "medal_expeditionary"
    )

    const val MIN_TRANSFER_OPS = 10
    const val MIN_RETIREMENT_OPS = 18
    const val MIN_HQ_STAFF_RETIREMENT_SUPPORT_OPS = 14
    const val MIN_HQ_STAFF_RETIREMENT_TOTAL_OPS = 22
    const val PETITION_COOLDOWN_OPS = 8
    const val MAX_VETERAN_LEGACY_BPS = 1200 // +12.00% lifetime cap across Credits, Supplies, Intel
    const val RETIREMENT_FULL_COOLDOWN_MILLIS = 30L * 60_000L // 30m between retirements for 100% spacing factor

    data class VeteranLegacyPreview(
        val gainBps: Int,
        val currentBps: Int,
        val resultingBps: Int,
        val capBps: Int,
        val spacingPct: Int,
        val cooldownRemainingSec: Long,
        val atCap: Boolean,
        val prestigeAward: Int
    ) {
        val gainPctText: String get() = formatBpsPercent(gainBps)
        val resultingPctText: String get() = formatBpsPercent(resultingBps)
        val capPctText: String get() = formatBpsPercent(capBps)
    }

    fun formatBpsPercent(bps: Int): String =
        String.format(java.util.Locale.US, "+%.2f%%", bps.coerceAtLeast(0) / 100.0)

    data class WornRegaliaSummary(
        val neckMedal: MedalDefinition?,
        val leftCord: MedalDefinition?,
        val rightCord: MedalDefinition?,
        val tabSlot1: MedalDefinition?,
        val tabSlot2: MedalDefinition?,
        val tabSlot3: MedalDefinition?,
        val chestPinAboveRack: MedalDefinition?,
        val chestPinLeftPocket: MedalDefinition?,
        val chestPinRightPocket: MedalDefinition?,
        val shoulderTabs: List<MedalDefinition>,
        val chestBadges: List<MedalDefinition>,
        val hangingMedals: List<MedalDefinition>,
        val ribbonRack: List<MedalDefinition>,
        val wornIds: Set<String>
    )

    fun <T> layoutRibbonRackRows(items: List<T>, rackCols: Int = 3): List<List<T>> {
        if (items.isEmpty() || rackCols <= 0) return emptyList()
        val topCount = items.size % rackCols
        if (topCount == 0) return items.chunked(rackCols)
        return buildList {
            add(items.take(topCount))
            addAll(items.drop(topCount).chunked(rackCols))
        }
    }

    private fun resolvePositionalSlots(
        slotValues: List<String>,
        pool: List<MedalDefinition>
    ): List<MedalDefinition?> {
        val poolById = pool.associateBy { it.id }
        val usedIds = mutableSetOf<String>()
        val explicitResolved = slotValues.map { raw ->
            if (raw != UniformTailorOverrides.SLOT_AUTO && raw != UniformTailorOverrides.SLOT_NONE) {
                val match = poolById[raw]?.takeIf { it.id !in usedIds }
                if (match != null) usedIds += match.id
                match
            } else {
                null
            }
        }
        val autoIterator = pool.filter { it.id !in usedIds }.iterator()
        return slotValues.mapIndexed { idx, raw ->
            when (raw) {
                UniformTailorOverrides.SLOT_NONE -> null
                UniformTailorOverrides.SLOT_AUTO -> if (autoIterator.hasNext()) autoIterator.next() else null
                else -> explicitResolved[idx]
            }
        }
    }

    fun resolveWornRegalia(
        operator: Operator,
        awardedMedals: List<MedalDefinition>
    ): WornRegaliaSummary {
        val sorted = awardedMedals.sortedBy { it.precedenceOrder }
        val ownedById = sorted.associateBy { it.id }
        val overrides = operator.tailorOverrides

        val neckMedal = when (overrides.neckSlot) {
            UniformTailorOverrides.SLOT_NONE -> null
            UniformTailorOverrides.SLOT_AUTO -> sorted.firstOrNull { it.isNeckMedallion }
            else -> ownedById[overrides.neckSlot]?.takeIf { it.isNeckMedallion }
        }

        val leftCord = operator.equippedLeftCordId?.let { id ->
            ownedById[id]?.takeIf {
                it.category == AwardCategory.SHOULDER_CORD &&
                    (overrides.freeTailorOverride || UniformRegaliaRules.isCordAllowedOnShoulder(it, isRightShoulder = false))
            }
        }
        val rightCord = operator.equippedRightCordId
            ?.takeIf { it != leftCord?.id }
            ?.let { id ->
                ownedById[id]?.takeIf {
                    it.category == AwardCategory.SHOULDER_CORD &&
                        (overrides.freeTailorOverride || UniformRegaliaRules.isCordAllowedOnShoulder(it, isRightShoulder = true))
                }
            }

        val tabPool = UniformRegaliaRules.tabsInPrecedenceOrder(sorted)
        val resolvedTabs = resolvePositionalSlots(
            slotValues = listOf(overrides.tabSlot1, overrides.tabSlot2, overrides.tabSlot3),
            pool = tabPool
        )
        val tabSlot1 = resolvedTabs.getOrNull(0)
        val tabSlot2 = resolvedTabs.getOrNull(1)
        val tabSlot3 = resolvedTabs.getOrNull(2)
        // Whatever the slots hold, the stack always draws in real-world precedence order
        val shoulderTabs = resolvedTabs.filterNotNull()
            .sortedBy { UniformRegaliaRules.tabPrecedence(it.id) }

        val badgePool = sorted.filter {
            it.category == AwardCategory.SCHOOL_INSIGNIA && it.insigniaShape != InsigniaShape.TAB
        }
        val resolvedPins = UniformRegaliaRules.resolveChestBadgeSlots(
            operator = operator,
            ownedBadgeMedals = badgePool,
            overrides = overrides
        )
        val chestPinAboveRack = resolvedPins.getOrNull(0)
        val chestPinLeftPocket = resolvedPins.getOrNull(1)
        val chestPinRightPocket = resolvedPins.getOrNull(2)
        val chestBadges = resolvedPins.filterNotNull()

        val rawMedalSlots = listOf(
            overrides.medalSlot1,
            overrides.medalSlot2,
            overrides.medalSlot3,
            overrides.medalSlot4
        )

        val hangingPool = sorted.filter { it.category == AwardCategory.MEDAL && !it.isNeckMedallion }
        val hangingSlotSpecs = rawMedalSlots.map { raw ->
            if (raw == UniformTailorOverrides.SLOT_AUTO ||
                raw == UniformTailorOverrides.SLOT_NONE ||
                hangingPool.any { it.id == raw }
            ) {
                raw
            } else {
                UniformTailorOverrides.SLOT_AUTO
            }
        }
        val hangingMedals = if (operator.dressDisplayMode == DressDisplayMode.CLEAN) {
            emptyList()
        } else {
            resolvePositionalSlots(hangingSlotSpecs, hangingPool)
                .filterNotNull()
                .take(MAX_WORN_HANGING_MEDALS)
        }

        val ribbonPool = sorted.filter {
            it.category == AwardCategory.MEDAL || it.category == AwardCategory.RIBBON
        }
        val ribbonFeatured = resolvePositionalSlots(rawMedalSlots, ribbonPool).filterNotNull()
        val hasExplicitNoneInMedals = rawMedalSlots.any { it == UniformTailorOverrides.SLOT_NONE }
        val ribbonRack = if (operator.dressDisplayMode == DressDisplayMode.CLEAN) {
            emptyList()
        } else if (hasExplicitNoneInMedals) {
            ribbonFeatured.take(MAX_WORN_RIBBONS)
        } else {
            val featuredIds = ribbonFeatured.map { it.id }.toSet()
            val overflow = ribbonPool.filter { it.id !in featuredIds }
            (ribbonFeatured + overflow).take(MAX_WORN_RIBBONS)
        }

        val wornIds = buildSet {
            neckMedal?.let { add(it.id) }
            leftCord?.let { add(it.id) }
            rightCord?.let { add(it.id) }
            shoulderTabs.forEach { add(it.id) }
            chestBadges.forEach { add(it.id) }
            when (operator.dressDisplayMode) {
                DressDisplayMode.CLEAN -> Unit
                DressDisplayMode.FULL_MEDALS -> {
                    if (hangingMedals.isNotEmpty()) {
                        hangingMedals.forEach { add(it.id) }
                    } else {
                        ribbonRack.forEach { add(it.id) }
                    }
                }
                DressDisplayMode.RIBBONS -> {
                    ribbonRack.forEach { add(it.id) }
                }
            }
        }
        return WornRegaliaSummary(
            neckMedal = neckMedal,
            leftCord = leftCord,
            rightCord = rightCord,
            tabSlot1 = tabSlot1,
            tabSlot2 = tabSlot2,
            tabSlot3 = tabSlot3,
            chestPinAboveRack = chestPinAboveRack,
            chestPinLeftPocket = chestPinLeftPocket,
            chestPinRightPocket = chestPinRightPocket,
            shoulderTabs = shoulderTabs,
            chestBadges = chestBadges,
            hangingMedals = hangingMedals,
            ribbonRack = ribbonRack,
            wornIds = wornIds
        )
    }

    fun facilityLevel(facilities: List<FacilityState>, type: FacilityType): Int =
        facilities.find { it.type == type }?.level ?: 1

    data class HqStaffSummary(
        val staffCount: Int = 0,
        val creditFlowPct: Int = 0,
        val supplyFlowPct: Int = 0,
        val intelFlowPct: Int = 0,
        val durationReductionPct: Int = 0,
        val supplyDiscountPct: Int = 0,
        val survivalBonusPct: Int = 0,
        val efficiencyBonusPct: Int = 0,
        val prestigeBonusPct: Int = 0,
        val xpBonusPct: Int = 0
    ) {
        val prestigeMultiplier: Double get() = 1.0 + prestigeBonusPct / 100.0
        val xpMultiplier: Double get() = 1.0 + xpBonusPct / 100.0
    }

    fun summarizeHqStaff(operators: List<Operator>): HqStaffSummary {
        val staff = operators.filter { it.unlocked && it.status == OperatorStatus.HQ_STAFF }
        if (staff.isEmpty()) return HqStaffSummary()

        var cr = 0
        var sup = 0
        var intel = 0
        var dur = 0
        var costDisc = 0
        var surv = 0
        var eff = 0
        var prs = 0
        var xp = 0

        for (op in staff) {
            val role = op.hqRole
            if (role != null) {
                cr += role.creditFlowPct
                sup += role.supplyFlowPct
                intel += role.intelFlowPct
                dur += role.durationReductionPct
                costDisc += role.supplyDiscountPct
                surv += role.survivalBonusPct
                eff += role.efficiencyBonusPct
                prs += role.prestigeBonusPct
                xp += role.xpBonusPct
            } else {
                cr += 8
                sup += 8
                intel += 8
                surv += 2
                eff += 4
            }
        }
        return HqStaffSummary(
            staffCount = staff.size,
            creditFlowPct = cr,
            supplyFlowPct = sup,
            intelFlowPct = intel,
            durationReductionPct = dur.coerceAtMost(25),
            supplyDiscountPct = costDisc.coerceAtMost(25),
            survivalBonusPct = surv.coerceAtMost(15),
            efficiencyBonusPct = eff.coerceAtMost(35),
            prestigeBonusPct = prs,
            xpBonusPct = xp
        )
    }

    fun unlockedHqRoles(facilities: List<FacilityState>): List<HqStaffRole> {
        val slots = maxHqStaffSlots(facilities)
        return HqStaffRole.entries.filter { it.slotOrder <= slots }.sortedBy { it.slotOrder }
    }

    fun evaluateHqRoleEligibility(role: HqStaffRole, operator: Operator): EligibilityResult {
        val missing = mutableListOf<String>()
        if (operator.rank.ordinal < role.minRank.ordinal) {
            missing += "Requires ${role.minRank.code} (${role.minRank.payGrade})+ (currently ${operator.rank.code})"
        }
        val totalExpOps = operator.missionsCompleted + operator.staffOpsSupported
        if (totalExpOps < role.minMissionsRequired) {
            missing += "Requires ${role.minMissionsRequired}+ Ops ($totalExpOps/${role.minMissionsRequired})"
        }
        if (role.requiredSchoolIds.isNotEmpty()) {
            val hasAnyRequired = role.requiredSchoolIds.any { it in operator.completedMissionIds } ||
                (role == HqStaffRole.S4_LOGISTICS_MEDEVAC && "msn_support_school" in operator.completedMissionIds)
            if (!hasAnyRequired) {
                val label = role.requiredSchoolLabel
                    ?: role.requiredSchoolIds.firstOrNull()?.let { id ->
                        GameCatalog.missions.find { it.id == id }?.title
                    }
                    ?: role.requiredSchoolIds.first()
                missing += "Requires School: $label"
            }
        }
        return EligibilityResult(eligible = missing.isEmpty(), missingRequirements = missing)
    }

    fun canOperatorDeployOnMission(
        operator: Operator,
        mission: MissionTemplate,
        deployedOpIds: Set<String>,
        gearItems: List<GearItem> = emptyList(),
        citations: List<AwardedMedal> = emptyList(),
        facilities: List<FacilityState> = emptyList()
    ): EligibilityResult {
        if (!operator.unlocked) return EligibilityResult(false, listOf("Locked"))
        if (operator.id in deployedOpIds || operator.status == OperatorStatus.DEPLOYED) {
            return EligibilityResult(false, listOf("Already Deployed"))
        }
        when (operator.status) {
            OperatorStatus.FALLEN -> return EligibilityResult(false, listOf("Fallen"))
            OperatorStatus.RETIRED -> return EligibilityResult(false, listOf("Honorably Retired"))
            OperatorStatus.RECOVERING -> return EligibilityResult(false, listOf("In Medbay"))
            OperatorStatus.DEPLOYED -> return EligibilityResult(false, listOf("Already Deployed"))
            OperatorStatus.HQ_STAFF -> {
                if (!mission.scope.hqStaffAllowed) {
                    return EligibilityResult(
                        eligible = false,
                        missingRequirements = listOf("HQ Staff only deploy on Schools, Diplomatic & Staff Ops")
                    )
                }
            }
            OperatorStatus.READY -> Unit
        }

        if (mission.scope == MissionScope.TRAINING) {
            if (mission.id in operator.completedMissionIds) {
                return EligibilityResult(false, listOf("Already Graduated"))
            }
            val req = mission.schoolRequirements
            if (req != null) {
                val missing = mutableListOf<String>()
                if (req.allowedSpecialties.isNotEmpty() && operator.specialty !in req.allowedSpecialties) {
                    val allowedLabels = req.allowedSpecialties.joinToString(" / ") { it.label }
                    missing += "MOS Ineligible (${operator.specialty.roleTag}): Requires $allowedLabels"
                }
                if (mission.id == "msn_eib_testing" && !isInfantryOrSfQualified(operator)) {
                    missing += "Infantry or SF-qualified only"
                }
                val attemptsUsed = operator.schoolAttempts[mission.id] ?: 0
                if (req.maxAttempts != null && attemptsUsed >= req.maxAttempts) {
                    missing += "Washed out ($attemptsUsed/${req.maxAttempts} attempts used)"
                }

                if (req.minRankTier.order > RankTier.SOLDIER.order && operator.rank.tier.order < req.minRankTier.order) {
                    missing += "Requires ${req.minRankTier.shortLabel}+ rank (currently ${operator.rank.code})"
                }
                req.minCombatRisk?.let { minRisk ->
                    val maxHazard = operator.highestRiskSurvived?.baseDangerPenalty ?: -1
                    val staffBypass = mission.id == "msn_ocs_school" &&
                        (operator.status == OperatorStatus.HQ_STAFF || operator.staffOpsSupported >= 3)
                    if (!staffBypass && maxHazard < minRisk.baseDangerPenalty) {
                        missing += "Survive ${minRisk.label}+ Field Op first"
                    }
                }

                for (preId in req.prerequisiteAllIds) {
                    if (preId !in operator.completedMissionIds) {
                        val preTitle = GameCatalog.missions.find { it.id == preId }?.title?.removePrefix("School: ") ?: preId
                        missing += "Requires $preTitle"
                    }
                }
                if (req.prerequisiteAnyIds.isNotEmpty() && req.prerequisiteAnyIds.none { it in operator.completedMissionIds }) {
                    val anyLabels = req.prerequisiteAnyIds.joinToString(" or ") { id ->
                        GameCatalog.missions.find { it.id == id }?.title?.removePrefix("School: ") ?: id
                    }
                    missing += "Requires 1 entry school ($anyLabels)"
                }

                // Schools judge the soldier, not their kit: no gear or Armory bonus here
                val stats = soldierStats(operator, citations)
                if (req.minFirepower > 0 && stats.firepower < req.minFirepower) {
                    missing += "Not suited: Needs ${req.minFirepower}+ Firepower (${stats.firepower})"
                }
                if (req.minStealth > 0 && stats.stealth < req.minStealth) {
                    missing += "Not suited: Needs ${req.minStealth}+ Stealth (${stats.stealth})"
                }
                if (req.minTech > 0 && stats.tech < req.minTech) {
                    missing += "Not suited: Needs ${req.minTech}+ Tech (${stats.tech})"
                }
                if (req.minResilience > 0 && stats.resilience < req.minResilience) {
                    missing += "Not suited: Needs ${req.minResilience}+ Resilience (${stats.resilience})"
                }

                if (missing.isNotEmpty()) {
                    return EligibilityResult(eligible = false, missingRequirements = missing)
                }
            }
        }

        return EligibilityResult(eligible = true)
    }

    data class MissionUniformRequirement(
        val kit: WardrobeKit,
        val isMandatory: Boolean,
        val bonusSummary: String?,
        val shortBadge: String
    )

    data class MissionKitBonus(
        val recommendedKit: WardrobeKit,
        val allMatched: Boolean,
        val efficiencyBonusPct: Int = 0,
        val successOddsBonusPct: Int = 0,
        val prestigeMult: Double = 1.0,
        val creditMult: Double = 1.0,
        val xpMult: Double = 1.0,
        val summaryLabel: String? = null
    )

    fun missionUniformRequirement(mission: MissionTemplate): MissionUniformRequirement =
        when (mission.scope) {
            MissionScope.FIELD_COMBAT -> MissionUniformRequirement(
                kit = WardrobeKit.FIELD,
                isMandatory = true,
                bonusSummary = null,
                shortBadge = "Req: Field ACU"
            )
            MissionScope.TRAINING -> {
                val kit = schoolUniformKit(mission)
                MissionUniformRequirement(
                    kit = kit,
                    isMandatory = true,
                    bonusSummary = "+10% XP",
                    shortBadge = "Uniform: ${kit.shortLabel}"
                )
            }
            MissionScope.STAFF_DIPLOMATIC -> MissionUniformRequirement(
                kit = WardrobeKit.MESS,
                isMandatory = false,
                bonusSummary = "+8% Odds • +10% Eff • +20% PRS • +15% CR",
                shortBadge = "Rec: Mess (+20% PRS • +8% Odds)"
            )
        }

    fun resolveDutyContextForOperator(
        operator: Operator,
        deployments: List<ActiveDeployment> = emptyList()
    ): DutyUniformContext {
        val activeDep = deployments.firstOrNull { operator.id in it.operatorIds }
        if (activeDep != null) {
            val mission = GameCatalog.missions.find { it.id == activeDep.templateId }
            return when (mission?.scope) {
                MissionScope.FIELD_COMBAT -> DutyUniformContext.COMBAT_DEPLOYMENT
                MissionScope.TRAINING -> schoolDutyContext(mission)
                MissionScope.STAFF_DIPLOMATIC -> DutyUniformContext.DIPLOMATIC_MISSION
                null -> DutyUniformContext.COMBAT_DEPLOYMENT
            }
        }
        return when {
            operator.status == OperatorStatus.FALLEN || operator.status == OperatorStatus.RETIRED ->
                DutyUniformContext.MEMORIAL_HONORS
            operator.status == OperatorStatus.HQ_STAFF ->
                DutyUniformContext.HQ_ASSIGNMENT
            else ->
                DutyUniformContext.ON_BASE
        }
    }

    /** Uniform a school requires: Field for field schools, Bravos for classroom schools. */
    fun schoolUniformKit(mission: MissionTemplate): WardrobeKit =
        mission.schoolRequirements?.uniformKit ?: WardrobeKit.BRAVOS

    /** Duty context while attending a school, based on the uniform it requires. */
    fun schoolDutyContext(mission: MissionTemplate): DutyUniformContext =
        if (schoolUniformKit(mission) == WardrobeKit.FIELD) DutyUniformContext.FIELD_SCHOOL
        else DutyUniformContext.TRAINING_MISSION

    fun resolveEffectiveWardrobeKit(
        operator: Operator,
        smartWardrobeEnabled: Boolean,
        dutyContext: DutyUniformContext
    ): WardrobeKit {
        // Schools dress their students automatically, whatever the wardrobe setting.
        // Field schools put HQ staff in Field kit too, for the length of the course.
        when (dutyContext) {
            DutyUniformContext.FIELD_SCHOOL -> return WardrobeKit.FIELD
            DutyUniformContext.TRAINING_MISSION -> return WardrobeKit.BRAVOS
            else -> Unit
        }
        val resolved = if (smartWardrobeEnabled) {
            when (dutyContext) {
                DutyUniformContext.COMBAT_DEPLOYMENT, DutyUniformContext.FIELD_SCHOOL -> WardrobeKit.FIELD
                DutyUniformContext.TRAINING_MISSION, DutyUniformContext.ON_BASE -> WardrobeKit.BRAVOS
                DutyUniformContext.HQ_ASSIGNMENT,
                DutyUniformContext.PROMOTION_BOARD,
                DutyUniformContext.MEMORIAL_HONORS -> WardrobeKit.ALPHAS
                DutyUniformContext.DIPLOMATIC_MISSION -> WardrobeKit.MESS
            }
        } else {
            operator.activeWardrobeKit
        }
        return if (!resolved.isAvailableFor(operator)) WardrobeKit.ALPHAS else resolved
    }

    fun defaultDressStyleForKit(
        kit: WardrobeKit,
        tier: RankTier,
        preferred: DressUniformStyle? = null
    ): DressUniformStyle {
        if (preferred != null) {
            val match = when (kit) {
                WardrobeKit.MESS -> preferred.isCutawayMess
                WardrobeKit.ALPHAS, WardrobeKit.BRAVOS -> !preferred.isCutawayMess
                WardrobeKit.FIELD -> true
            }
            if (match && tier.order >= preferred.requiredTier.order) return preferred
        }
        return when (kit) {
            WardrobeKit.MESS -> when (tier) {
                RankTier.FLAG_OFFICER -> DressUniformStyle.FLAG_MESS_FORMAL
                RankTier.OFFICER -> DressUniformStyle.OFFICER_MESS_BLUES
                RankTier.SNCO, RankTier.NCO, RankTier.SOLDIER -> DressUniformStyle.ENLISTED_MESS_BLUES
            }
            WardrobeKit.ALPHAS, WardrobeKit.BRAVOS, WardrobeKit.FIELD -> when (tier) {
                RankTier.FLAG_OFFICER -> DressUniformStyle.FLAG_STAFF_FORMAL
                RankTier.OFFICER -> DressUniformStyle.OFFICER_SERVICE_BLUES
                RankTier.SNCO, RankTier.NCO -> DressUniformStyle.NCO_PARADE_BLUES
                RankTier.SOLDIER -> DressUniformStyle.ARMY_SERVICE_GREEN
            }
        }
    }

    fun defaultHeadgearForKit(
        kit: WardrobeKit,
        operator: Operator,
        ownedMedalIds: Set<String> = emptySet(),
        preferred: HeadgearStyle? = null
    ): HeadgearStyle {
        val tier = operator.rank.tier
        return when (kit) {
            WardrobeKit.BRAVOS -> {
                if (preferred != null && preferred.kind == HeadgearKind.BERET) return preferred
                if (operator.headgearStyle.kind == HeadgearKind.BERET) return operator.headgearStyle
                when {
                    evaluateHeadgearEligibility(HeadgearStyle.EMERALD_BERET, operator, ownedMedalIds).eligible ->
                        HeadgearStyle.EMERALD_BERET
                    evaluateHeadgearEligibility(HeadgearStyle.MAROON_BERET, operator, ownedMedalIds).eligible ->
                        HeadgearStyle.MAROON_BERET
                    evaluateHeadgearEligibility(HeadgearStyle.TAN_BERET, operator, ownedMedalIds).eligible ->
                        HeadgearStyle.TAN_BERET
                    else -> HeadgearStyle.OBSIDIAN_BERET
                }
            }
            WardrobeKit.ALPHAS, WardrobeKit.MESS -> {
                if (preferred != null && preferred.kind != HeadgearKind.BERET) return preferred
                if (operator.headgearStyle.kind != HeadgearKind.BERET) return operator.headgearStyle
                when (tier) {
                    RankTier.FLAG_OFFICER -> HeadgearStyle.FLAG_GENERAL_CAP
                    RankTier.OFFICER -> HeadgearStyle.OFFICER_PEAKED_CAP
                    RankTier.SNCO, RankTier.NCO -> HeadgearStyle.NCO_SERVICE_CAP
                    RankTier.SOLDIER -> HeadgearStyle.ENLISTED_SERVICE_CAP
                }
            }
            WardrobeKit.FIELD -> operator.headgearStyle
        }
    }

    fun resolveKitAppearance(
        operator: Operator,
        kit: WardrobeKit,
        squads: List<TacticalSquad> = emptyList(),
        ownedMedalIds: Set<String> = emptySet(),
        allowFieldForHqStaff: Boolean = false
    ): Operator {
        val effectiveKit = if (kit.isAvailableFor(operator) || (allowFieldForHqStaff && kit == WardrobeKit.FIELD)) {
            kit
        } else {
            WardrobeKit.ALPHAS
        }
        val settings = operator.wardrobe.forKit(effectiveKit)
        val allowFree = operator.tailorOverrides.freeTailorOverride
        val sashAllowed = allowFree || evaluateDressSashEligibility(operator, squads).eligible

        return when (effectiveKit) {
            WardrobeKit.FIELD -> operator.copy(
                activeWardrobeKit = WardrobeKit.FIELD,
                uniformMode = UniformMode.FIELD_TACTICAL,
                camoPattern = settings.camoPattern ?: operator.camoPattern
            )
            WardrobeKit.BRAVOS -> {
                val chosenDisplay = settings.dressDisplayMode ?: operator.dressDisplayMode
                val bravosDisplay = if (chosenDisplay == DressDisplayMode.CLEAN) {
                    DressDisplayMode.CLEAN
                } else {
                    DressDisplayMode.RIBBONS
                }
                operator.copy(
                    activeWardrobeKit = WardrobeKit.BRAVOS,
                    uniformMode = UniformMode.DRESS_UNIFORM,
                    dressStyle = defaultDressStyleForKit(WardrobeKit.BRAVOS, operator.rank.tier, settings.dressStyle),
                    headgearStyle = defaultHeadgearForKit(WardrobeKit.BRAVOS, operator, ownedMedalIds, settings.headgearStyle),
                    dressDisplayMode = bravosDisplay,
                    hasWhiteGloves = false,
                    hasDressSash = false,
                    // Shoulder cords are worn on the Bravos shirt, same selection as Alphas
                    equippedLeftCordId = if (settings.cordCustomized) settings.equippedLeftCordId else operator.equippedLeftCordId,
                    equippedRightCordId = if (settings.cordCustomized) settings.equippedRightCordId else operator.equippedRightCordId
                )
            }
            WardrobeKit.ALPHAS -> {
                val rawSash = settings.hasDressSash ?: operator.hasDressSash
                val leftCord = if (settings.cordCustomized) settings.equippedLeftCordId else operator.equippedLeftCordId
                val rightCord = if (settings.cordCustomized) settings.equippedRightCordId else operator.equippedRightCordId
                operator.copy(
                    activeWardrobeKit = WardrobeKit.ALPHAS,
                    uniformMode = UniformMode.DRESS_UNIFORM,
                    dressStyle = defaultDressStyleForKit(
                        WardrobeKit.ALPHAS,
                        operator.rank.tier,
                        settings.dressStyle ?: operator.dressStyle.takeIf { !it.isCutawayMess }
                    ),
                    headgearStyle = defaultHeadgearForKit(
                        WardrobeKit.ALPHAS,
                        operator,
                        ownedMedalIds,
                        settings.headgearStyle ?: operator.headgearStyle.takeIf { it.kind != HeadgearKind.BERET }
                    ),
                    dressDisplayMode = settings.dressDisplayMode ?: operator.dressDisplayMode,
                    hasWhiteGloves = settings.hasWhiteGloves ?: operator.hasWhiteGloves,
                    hasDressSash = rawSash && sashAllowed,
                    equippedLeftCordId = leftCord,
                    equippedRightCordId = rightCord
                )
            }
            WardrobeKit.MESS -> {
                val chosenDisplay = settings.dressDisplayMode ?: DressDisplayMode.FULL_MEDALS
                val messDisplay = if (chosenDisplay == DressDisplayMode.CLEAN) {
                    DressDisplayMode.CLEAN
                } else {
                    DressDisplayMode.FULL_MEDALS
                }
                operator.copy(
                    activeWardrobeKit = WardrobeKit.MESS,
                    uniformMode = UniformMode.DRESS_UNIFORM,
                    dressStyle = defaultDressStyleForKit(
                        WardrobeKit.MESS,
                        operator.rank.tier,
                        settings.dressStyle ?: operator.dressStyle.takeIf { it.isCutawayMess }
                    ),
                    headgearStyle = defaultHeadgearForKit(
                        WardrobeKit.MESS,
                        operator,
                        ownedMedalIds,
                        settings.headgearStyle ?: operator.headgearStyle.takeIf { it.kind != HeadgearKind.BERET }
                    ),
                    dressDisplayMode = messDisplay,
                    hasWhiteGloves = settings.hasWhiteGloves ?: true,
                    hasDressSash = false,
                    equippedLeftCordId = null,
                    equippedRightCordId = null
                )
            }
        }
    }

    fun evaluateMissionKitBonus(
        mission: MissionTemplate,
        selectedOperators: List<Operator>,
        smartWardrobeEnabled: Boolean
    ): MissionKitBonus {
        val req = missionUniformRequirement(mission)
        if (selectedOperators.isEmpty()) {
            return MissionKitBonus(recommendedKit = req.kit, allMatched = false)
        }
        val dutyCtx = when (mission.scope) {
            MissionScope.FIELD_COMBAT -> DutyUniformContext.COMBAT_DEPLOYMENT
            MissionScope.TRAINING -> schoolDutyContext(mission)
            MissionScope.STAFF_DIPLOMATIC -> DutyUniformContext.DIPLOMATIC_MISSION
        }
        val allMatched = selectedOperators.all { op ->
            resolveEffectiveWardrobeKit(op, smartWardrobeEnabled, dutyCtx) == req.kit
        }
        return when (mission.scope) {
            MissionScope.STAFF_DIPLOMATIC -> if (allMatched) {
                MissionKitBonus(
                    recommendedKit = WardrobeKit.MESS,
                    allMatched = true,
                    efficiencyBonusPct = 10,
                    successOddsBonusPct = 8,
                    prestigeMult = 1.20,
                    creditMult = 1.15,
                    summaryLabel = "Mess Dress Protocol: +8% Odds • +10% Eff • +20% PRS • +15% CR"
                )
            } else {
                MissionKitBonus(
                    recommendedKit = WardrobeKit.MESS,
                    allMatched = false,
                    summaryLabel = "Wear Mess Dress (or turn Smart Duty-Dress ON) for +20% PRS & +8% Odds"
                )
            }
            // Students are dressed in the school's uniform automatically, so the XP bonus always applies
            MissionScope.TRAINING -> MissionKitBonus(
                recommendedKit = req.kit,
                allMatched = true,
                xpMult = 1.10,
                summaryLabel = "${req.kit.label} school uniform: +10% Training XP"
            )
            MissionScope.FIELD_COMBAT -> MissionKitBonus(
                recommendedKit = WardrobeKit.FIELD,
                allMatched = allMatched
            )
        }
    }

    /**
     * Pass odds for a school. Uses the soldier's own stats only ([soldierStats]): carried
     * gear and the uniform worn have no effect on school results.
     */
    fun schoolPassOddsPct(
        mission: MissionTemplate,
        operator: Operator,
        citations: List<AwardedMedal>,
        hqStaffSummary: HqStaffSummary? = null
    ): Int {
        val req = mission.schoolRequirements ?: return 90
        val stats = soldierStats(operator, citations)
        val rankBonus = operator.rank.ordinal.coerceAtMost(10)
        val mosFitBonus = if (operator.specialty in req.naturalFitSpecialties) 5 else 0
        val statMargin = ((stats.totalPower - mission.recommendedPower) / 5).coerceIn(-16, 10)
        val staffBonus = (hqStaffSummary?.efficiencyBonusPct ?: 0) / 6
        // Flat +4 keeps overall pass rates where they were when Bravos gave +4; the uniform
        // itself no longer matters.
        val baselineBonus = SCHOOL_BASELINE_PASS_BONUS
        val maxCap = when (mission.id) {
            "msn_sf_school" -> 52
            "msn_ranger_school" -> 68
            "msn_sapper_school" -> 72
            "msn_eib_testing" -> 50
            "msn_pathfinder_school" -> 80
            "msn_night_qual" -> 76
            "msn_intel_school", "msn_ocs_school" -> 80
            "msn_signals_school", "msn_medical_school", "msn_engineer_school", "msn_logistics_school" -> 84
            "msn_snco_school" -> 86
            "msn_nco_school" -> 92
            else -> 94
        }
        return (req.basePassRatePct + rankBonus + mosFitBonus + statMargin + staffBonus + baselineBonus).coerceIn(15, maxCap)
    }

    fun missionSuccessOddsPct(
        mission: MissionTemplate,
        selectedOperators: List<Operator>,
        gearItems: List<GearItem>,
        citations: List<AwardedMedal>,
        facilities: List<FacilityState>,
        hqStaffCount: Int = 0,
        hqStaffSummary: HqStaffSummary? = null,
        squads: List<TacticalSquad> = emptyList(),
        smartWardrobeEnabled: Boolean = false
    ): Int {
        if (selectedOperators.isEmpty()) return 0
        if (mission.risk.isTraining) {
            return selectedOperators.map {
                schoolPassOddsPct(mission, it, citations, hqStaffSummary)
            }.average().roundToInt()
        }
        val efficiency = missionEfficiencyPct(
            mission = mission,
            selectedOperators = selectedOperators,
            gearItems = gearItems,
            citations = citations,
            facilities = facilities,
            hqStaffCount = hqStaffCount,
            hqStaffSummary = hqStaffSummary,
            squads = squads,
            smartWardrobeEnabled = smartWardrobeEnabled
        )
        val effDelta = if (efficiency < 100) {
            ((efficiency - 100) * 0.45).roundToInt()
        } else {
            ((efficiency - 100) * 0.20).roundToInt()
        }
        val staffBonus = (hqStaffSummary?.survivalBonusPct ?: 0) / 3
        val squadBonus = SquadCohesionRules.evaluateActiveBonus(selectedOperators, squads)
        val cohesionOddsBonus = squadBonus?.successOddsBonusPct ?: 0
        val kitOddsBonus = evaluateMissionKitBonus(mission, selectedOperators, smartWardrobeEnabled).successOddsBonusPct
        val (minOdds, maxOdds) = when (mission.risk) {
            MissionRisk.TRAINING -> 15 to 94
            MissionRisk.LOW -> 25 to 90
            MissionRisk.MODERATE -> 20 to 80
            MissionRisk.HIGH -> 16 to 70
            MissionRisk.EXTREME -> 12 to 62
        }
        return (mission.baseSuccessRatePct + effDelta + staffBonus + cohesionOddsBonus + kitOddsBonus)
            .coerceIn(minOdds, maxOdds)
    }

    fun productionRates(
        facilities: List<FacilityState>,
        hqStaffCount: Int = 0,
        hqStaffSummary: HqStaffSummary? = null,
        veteranLegacyBps: Int = 0
    ): ResourceBundle {
        var credits = 0.0
        var supplies = 0.0
        var intel = 0.0
        for (fac in facilities) {
            val rate = fac.type.productionPerSec(fac.level)
            when (fac.type.primaryResource) {
                ResourceType.CREDITS -> credits += rate
                ResourceType.SUPPLIES -> supplies += rate
                ResourceType.INTEL -> intel += rate
                ResourceType.PRESTIGE -> Unit
            }
        }
        val crMult = if (hqStaffSummary != null) {
            1.0 + hqStaffSummary.creditFlowPct / 100.0
        } else {
            1.0 + hqStaffCount.coerceAtLeast(0) * 0.08
        }
        val supMult = if (hqStaffSummary != null) {
            1.0 + hqStaffSummary.supplyFlowPct / 100.0
        } else {
            1.0 + hqStaffCount.coerceAtLeast(0) * 0.08
        }
        val intMult = if (hqStaffSummary != null) {
            1.0 + hqStaffSummary.intelFlowPct / 100.0
        } else {
            1.0 + hqStaffCount.coerceAtLeast(0) * 0.08
        }
        val legacyMult = 1.0 + (veteranLegacyBps.coerceIn(0, MAX_VETERAN_LEGACY_BPS) / 10_000.0)
        return ResourceBundle(
            credits = credits * crMult * legacyMult,
            supplies = supplies * supMult * legacyMult,
            intel = intel * intMult * legacyMult
        )
    }

    fun evaluateRetirementEligibility(operator: Operator): EligibilityResult {
        val missing = mutableListOf<String>()
        if (!operator.unlocked) missing += "Recruit not yet in service"
        if (operator.status == OperatorStatus.RETIRED) missing += "Already Honorably Retired"
        if (operator.status == OperatorStatus.FALLEN) missing += "Memorialized (Fallen)"
        if (operator.status == OperatorStatus.DEPLOYED) missing += "Currently deployed on mission"
        if (!operator.rank.isCareerEligibleForStaffOrTransfer) {
            missing += "Requires ${OperatorRank.SGT.code} (${OperatorRank.SGT.payGrade})+"
        }
        val totalOps = operator.missionsCompleted + operator.staffOpsSupported
        if (totalOps < MIN_RETIREMENT_OPS) {
            missing += "Requires $MIN_RETIREMENT_OPS+ Ops ($totalOps/$MIN_RETIREMENT_OPS)"
        }
        return EligibilityResult(eligible = missing.isEmpty(), missingRequirements = missing)
    }

    fun previewRetirementLegacy(
        operator: Operator,
        currentLegacyBps: Int,
        lastRetirementAtMillis: Long,
        nowMillis: Long
    ): VeteranLegacyPreview {
        val clampedCurrent = currentLegacyBps.coerceIn(0, MAX_VETERAN_LEGACY_BPS)
        val remainingBps = (MAX_VETERAN_LEGACY_BPS - clampedCurrent).coerceAtLeast(0)
        val totalOps = operator.missionsCompleted + operator.staffOpsSupported

        // Modest raw legacy per retiring veteran: 50 bps (+0.50%) base up to 100 bps (+1.00%) for senior Flag veterans
        val tierBonusBps = when (operator.rank.tier) {
            RankTier.SOLDIER, RankTier.NCO -> 0
            RankTier.SNCO -> 8
            RankTier.OFFICER -> 15
            RankTier.FLAG_OFFICER -> 30
        }
        val tenureBonusBps = (((totalOps - MIN_RETIREMENT_OPS).coerceAtLeast(0)) / 4).coerceAtMost(4) * 5
        val rawBps = 50 + tierBonusBps + tenureBonusBps

        // Time-between-retirements damping: back-to-back retirements yield 20%, recovering to 100% over 30m
        val elapsedMs = if (lastRetirementAtMillis <= 0L) {
            RETIREMENT_FULL_COOLDOWN_MILLIS
        } else {
            (nowMillis - lastRetirementAtMillis).coerceAtLeast(0L)
        }
        val elapsedRatio = (elapsedMs.toDouble() / RETIREMENT_FULL_COOLDOWN_MILLIS.toDouble()).coerceIn(0.0, 1.0)
        val spacingFactor = 0.20 + 0.80 * elapsedRatio
        val spacingPct = (spacingFactor * 100.0).roundToInt().coerceIn(20, 100)
        val cooldownRemainingSec = if (lastRetirementAtMillis <= 0L) {
            0L
        } else {
            ((RETIREMENT_FULL_COOLDOWN_MILLIS - elapsedMs).coerceAtLeast(0L) + 999L) / 1000L
        }

        // Diminishing headroom toward the lifetime cap prevents runaway resource scaling
        val headroomFactor = remainingBps.toDouble() / MAX_VETERAN_LEGACY_BPS.toDouble()
        val gainBps = if (remainingBps <= 0) {
            0
        } else {
            (rawBps * spacingFactor * headroomFactor).roundToInt().coerceIn(1, remainingBps)
        }
        val prestigeAward = 10 + operator.rank.tier.order * 2

        return VeteranLegacyPreview(
            gainBps = gainBps,
            currentBps = clampedCurrent,
            resultingBps = (clampedCurrent + gainBps).coerceAtMost(MAX_VETERAN_LEGACY_BPS),
            capBps = MAX_VETERAN_LEGACY_BPS,
            spacingPct = spacingPct,
            cooldownRemainingSec = cooldownRemainingSec,
            atCap = remainingBps <= 0,
            prestigeAward = prestigeAward
        )
    }

    fun maxFieldRosterSlots(facilities: List<FacilityState>): Int {
        val hqLevel = facilityLevel(facilities, FacilityType.COMMAND_HQ)
        return 4 + hqLevel
    }

    fun maxHqStaffSlots(facilities: List<FacilityState>): Int {
        val hqLevel = facilityLevel(facilities, FacilityType.COMMAND_HQ)
        return (2 + hqLevel).coerceAtMost(HqStaffRole.entries.size)
    }

    /** Number of active deployments that use an HQ tasking slot. Schools (TRAINING) never do. */
    fun occupiedDeploymentSlots(deployments: List<ActiveDeployment>): Int =
        deployments.count { deployment ->
            GameCatalog.missions.find { it.id == deployment.templateId }?.scope != MissionScope.TRAINING
        }

    fun maxDeploymentSlots(facilities: List<FacilityState>): Int {
        val hqLevel = facilityLevel(facilities, FacilityType.COMMAND_HQ)
        return 2 + (if (hqLevel >= 3) 1 else 0) + (if (hqLevel >= 6) 1 else 0)
    }

    fun offlineCapSeconds(facilities: List<FacilityState>): Long {
        val hqLevel = facilityLevel(facilities, FacilityType.COMMAND_HQ)
        return (4L + hqLevel) * 3600L
    }

    fun durationMultiplier(
        facilities: List<FacilityState>,
        hqStaffSummary: HqStaffSummary? = null
    ): Double {
        val reconLevel = facilityLevel(facilities, FacilityType.RECON_ARRAY)
        val facMult = (1.0 - (reconLevel - 1) * 0.04).coerceAtLeast(0.60)
        val staffMult = 1.0 - (hqStaffSummary?.durationReductionPct ?: 0) / 100.0
        return (facMult * staffMult).coerceAtLeast(0.50)
    }

    fun gearBonusMultiplier(facilities: List<FacilityState>): Double {
        val armoryLevel = facilityLevel(facilities, FacilityType.ARMORY_WORKSHOP)
        return 1.0 + (armoryLevel - 1) * 0.05
    }

    fun supplyCostMultiplier(
        facilities: List<FacilityState>,
        hqStaffSummary: HqStaffSummary? = null,
        completedCampaignIds: Set<String> = emptySet()
    ): Double {
        val logisticsLevel = facilityLevel(facilities, FacilityType.LOGISTICS_DEPOT)
        val facMult = (1.0 - (logisticsLevel - 1) * 0.03).coerceAtLeast(0.70)
        val staffMult = 1.0 - (hqStaffSummary?.supplyDiscountPct ?: 0) / 100.0
        val campaignMult = campaignSupplyCostMultiplier(completedCampaignIds)
        return (facMult * staffMult * campaignMult).coerceAtLeast(0.50)
    }

    /** The soldier's own stats: base + rank + medal bonuses, with no gear and no Armory multiplier. */
    fun soldierStats(operator: Operator, citations: List<AwardedMedal>): StatProfile =
        operatorStats(operator, gearItems = emptyList(), citations = citations, facilities = emptyList())

    fun operatorStats(
        operator: Operator,
        gearItems: List<GearItem>,
        citations: List<AwardedMedal>,
        facilities: List<FacilityState>
    ): StatProfile {
        val rankIndex = OperatorRank.entries.indexOf(operator.rank)
        val rankBonus = rankIndex + operator.rank.tier.order * 2
        val gearMult = gearBonusMultiplier(facilities)

        val equipped = gearItems.filter { it.equippedByOperatorId == operator.id }
        var gearFp = 0
        var gearSt = 0
        var gearTc = 0
        var gearRs = 0
        for (g in equipped) {
            gearFp += (g.firepower * gearMult).roundToInt()
            gearSt += (g.stealth * gearMult).roundToInt()
            gearTc += (g.tech * gearMult).roundToInt()
            gearRs += (g.resilience * gearMult).roundToInt()
        }

        val opMedals = citations.filter { it.operatorId == operator.id }
            .mapNotNull { cit -> GameCatalog.medals.find { it.id == cit.medalId } }
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
            firepower = operator.baseFirepower + rankBonus + gearFp + medFp,
            stealth = operator.baseStealth + rankBonus + gearSt + medSt,
            tech = operator.baseTech + rankBonus + gearTc + medTc,
            resilience = operator.baseResilience + rankBonus + gearRs + medRs
        )
    }

    fun operatorLoadoutWeight(operatorId: String, gearItems: List<GearItem>): Double =
        gearItems.filter { it.equippedByOperatorId == operatorId }.sumOf { it.weightKg }

    fun operatorYieldBonusPct(operatorId: String, citations: List<AwardedMedal>): Int =
        citations.filter { it.operatorId == operatorId }
            .mapNotNull { cit -> GameCatalog.medals.find { it.id == cit.medalId } }
            .sumOf { it.yieldBonusPct }

    fun operatorSurvivalPct(
        mission: MissionTemplate,
        operator: Operator,
        gearItems: List<GearItem>,
        citations: List<AwardedMedal>,
        facilities: List<FacilityState>,
        hqStaffCount: Int = 0,
        hqStaffSummary: HqStaffSummary? = null
    ): Int {
        if (mission.risk.isTraining) return 100
        val stats = operatorStats(operator, gearItems, citations, facilities)
        val equipped = gearItems.filter { it.equippedByOperatorId == operator.id }
        val gearSurvival = equipped.sumOf { it.survivalBonusPct }
        val reconBonus = (facilityLevel(facilities, FacilityType.RECON_ARRAY) - 1) * 2
        val staffBonus = hqStaffSummary?.survivalBonusPct ?: (hqStaffCount.coerceAtLeast(0) * 2).coerceAtMost(10)
        val resilienceFactor = (stats.resilience * 0.40 + stats.stealth * 0.25).roundToInt()
        val nightSynergy = if (mission.isNightOp && equipped.any { it.id.contains("nvg") || it.id.contains("thermal") }) 8 else 0
        val raw = 80 - mission.risk.baseDangerPenalty + (gearSurvival * 0.75).roundToInt() + reconBonus + staffBonus + resilienceFactor / 3 + nightSynergy
        return raw.coerceIn(42, 96)
    }

    fun squadAvgSurvivalPct(
        mission: MissionTemplate,
        selectedOperators: List<Operator>,
        gearItems: List<GearItem>,
        citations: List<AwardedMedal>,
        facilities: List<FacilityState>,
        hqStaffCount: Int = 0,
        hqStaffSummary: HqStaffSummary? = null,
        squads: List<TacticalSquad> = emptyList()
    ): Int {
        if (selectedOperators.isEmpty()) return 0
        val baseAvg = selectedOperators.map {
            operatorSurvivalPct(mission, it, gearItems, citations, facilities, hqStaffCount, hqStaffSummary)
        }.average()
        if (mission.risk.isTraining) return baseAvg.roundToInt()
        val cohesionSurv = SquadCohesionRules.evaluateActiveBonus(selectedOperators, squads)?.survivalBonusPct ?: 0
        return (baseAvg + cohesionSurv).roundToInt().coerceIn(42, 98)
    }

    fun isRepeatableAward(medal: MedalDefinition): Boolean =
        (medal.category == AwardCategory.MEDAL || medal.category == AwardCategory.RIBBON) &&
            medal.id != "medal_sovereign_star"

    fun missionEfficiencyPct(
        mission: MissionTemplate,
        selectedOperators: List<Operator>,
        gearItems: List<GearItem>,
        citations: List<AwardedMedal>,
        facilities: List<FacilityState>,
        hqStaffCount: Int = 0,
        hqStaffSummary: HqStaffSummary? = null,
        squads: List<TacticalSquad> = emptyList(),
        inspectionBuffActive: Boolean = false,
        smartWardrobeEnabled: Boolean = false
    ): Int {
        if (selectedOperators.isEmpty()) return 0
        val totalPower = selectedOperators.sumOf {
            operatorStats(it, gearItems, citations, facilities).totalPower
        }
        val staffBonus = ((hqStaffSummary?.efficiencyBonusPct ?: (hqStaffCount.coerceAtLeast(0) * 4)) / 2).coerceAtMost(14)
        val cohesionEff = SquadCohesionRules.evaluateActiveBonus(selectedOperators, squads)?.efficiencyBonusPct ?: 0
        val inspectionBonus = if (inspectionBuffActive) 8 else 0
        val kitEffBonus = evaluateMissionKitBonus(mission, selectedOperators, smartWardrobeEnabled).efficiencyBonusPct
        val baseRatio = (totalPower.toDouble() / mission.recommendedPower.coerceAtLeast(1)) * 100.0
        return (baseRatio + staffBonus + cohesionEff + inspectionBonus + kitEffBonus).roundToInt().coerceIn(45, 185)
    }

    data class EligibilityResult(
        val eligible: Boolean,
        val missingRequirements: List<String> = emptyList()
    ) {
        val reasonText: String get() = missingRequirements.joinToString(" • ")
    }

    fun evaluateGearRequisitionEligibility(
        gear: GearItem,
        facilities: List<FacilityState>,
        operators: List<Operator>
    ): EligibilityResult {
        val missing = mutableListOf<String>()
        val armoryLevel = facilityLevel(facilities, FacilityType.ARMORY_WORKSHOP)
        if (armoryLevel < gear.requiredArmoryLevel) {
            missing += "Requires Armory Bay Lv.${gear.requiredArmoryLevel}"
        }
        val reqSchoolId = gear.requiredSchoolId
        if (!gear.unlocked && reqSchoolId != null) {
            val hasGraduate = operators.any { op ->
                op.unlocked &&
                    !op.status.isOutOfActiveService &&
                    reqSchoolId in op.completedMissionIds
            }
            if (!hasGraduate) {
                val label = gear.requiredSchoolLabel ?: reqSchoolId
                missing += "Requires 1+ $label graduate"
            }
        }
        return EligibilityResult(eligible = missing.isEmpty(), missingRequirements = missing)
    }

    fun evaluateGearEquipEligibility(
        gear: GearItem,
        operator: Operator
    ): EligibilityResult {
        val reqSchoolId = gear.requiredSchoolId ?: return EligibilityResult(eligible = true)
        if (reqSchoolId in operator.completedMissionIds) {
            return EligibilityResult(eligible = true)
        }
        val label = gear.requiredSchoolLabel ?: reqSchoolId
        return EligibilityResult(
            eligible = false,
            missingRequirements = listOf("Requires School: $label")
        )
    }

    fun evaluateDressStyleEligibility(
        style: DressUniformStyle,
        operator: Operator,
        ownedMedalIds: Set<String>,
        kit: WardrobeKit? = null
    ): EligibilityResult {
        val missing = mutableListOf<String>()
        if (kit == WardrobeKit.MESS && !style.isCutawayMess) {
            missing += "Evening Mess requires a Cutaway Mess jacket"
        } else if ((kit == WardrobeKit.ALPHAS || kit == WardrobeKit.BRAVOS) && style.isCutawayMess) {
            missing += "${kit.shortLabel} uses Service Dress — wear Cutaway in Mess kit"
        }
        if (operator.rank.tier.order < style.requiredTier.order) {
            missing += "Requires ${style.requiredTier.shortLabel} rank"
        }
        when (style) {
            DressUniformStyle.ARMY_DRESS_BLUES -> {
                val hasActivity = operator.missionsCompleted >= 1 ||
                    operator.completedMissionIds.isNotEmpty() ||
                    operator.staffOpsSupported >= 1
                if (!hasActivity) {
                    missing += "Complete 1+ School or Field Op"
                }
            }
            DressUniformStyle.HONOR_GUARD_IVORY -> {
                val hasValorOrSupreme = ownedMedalIds.any { id ->
                    GameCatalog.medals.find { it.id == id }
                        ?.let { it.tier == MedalTier.VALOR || it.tier == MedalTier.SUPREME } == true
                }
                if (operator.status != OperatorStatus.HQ_STAFF && !hasValorOrSupreme) {
                    missing += "Hold Valor/Supreme honor or HQ Staff"
                }
            }
            else -> Unit
        }
        return EligibilityResult(eligible = missing.isEmpty(), missingRequirements = missing)
    }

    fun evaluateHeadgearEligibility(
        headgear: HeadgearStyle,
        operator: Operator,
        ownedMedalIds: Set<String>,
        kit: WardrobeKit? = null
    ): EligibilityResult {
        val missing = mutableListOf<String>()
        if (kit == WardrobeKit.BRAVOS && headgear.kind != HeadgearKind.BERET) {
            missing += "Bravos wear Berets only"
        } else if ((kit == WardrobeKit.ALPHAS || kit == WardrobeKit.MESS) && headgear.kind == HeadgearKind.BERET) {
            missing += "${kit.shortLabel} prohibits berets (use Visor/Peaked Cap)"
        }
        if (operator.rank.tier.order < headgear.requiredTier.order) {
            missing += "Requires ${headgear.requiredTier.shortLabel} rank"
        }
        when (headgear) {
            HeadgearStyle.MAROON_BERET -> {
                val airborneQualified = "msn_night_qual" in operator.completedMissionIds ||
                    "insig_airborne_tab" in ownedMedalIds ||
                    "insig_parachutist" in ownedMedalIds ||
                    "insig_master_para" in ownedMedalIds ||
                    "insig_air_assault" in ownedMedalIds ||
                    "cord_airborne_crimson" in ownedMedalIds
                if (!airborneQualified) {
                    missing += "Requires Airborne School, Airborne Tab, Jump Wings, or Airborne Cord"
                }
            }
            HeadgearStyle.TAN_BERET -> {
                val rangerQualified = "msn_ranger_school" in operator.completedMissionIds ||
                    "insig_ranger_tab" in ownedMedalIds
                if (!rangerQualified) {
                    missing += "Requires Army Ranger Course or Ranger Tab"
                }
            }
            HeadgearStyle.EMERALD_BERET -> {
                val sfQualified = "msn_sf_school" in operator.completedMissionIds ||
                    "insig_sf_tab" in ownedMedalIds ||
                    "cord_sf_emerald" in ownedMedalIds
                if (!sfQualified) {
                    missing += "Requires Special Forces School (SFQC), SF Tab, or Emerald Cord"
                }
            }
            else -> Unit
        }
        return EligibilityResult(eligible = missing.isEmpty(), missingRequirements = missing)
    }

    fun evaluateWhiteGlovesEligibility(
        operator: Operator,
        ownedMedalIds: Set<String>
    ): EligibilityResult {
        val hasHonor = "medal_good_conduct" in ownedMedalIds || ownedMedalIds.any { id ->
            GameCatalog.medals.find { it.id == id }
                ?.let { it.tier == MedalTier.VALOR || it.tier == MedalTier.SUPREME } == true
        }
        return if (operator.rank.tier.order >= RankTier.NCO.order || hasHonor) {
            EligibilityResult(eligible = true)
        } else {
            EligibilityResult(
                eligible = false,
                missingRequirements = listOf("Requires NCO+ rank, Good Conduct Ribbon, or Valor Honor")
            )
        }
    }

    fun evaluateDressSashEligibility(
        operator: Operator,
        squads: List<TacticalSquad> = emptyList()
    ): EligibilityResult {
        val isHqStaff = operator.status == OperatorStatus.HQ_STAFF
        val isSquadLeader = squads.any { it.leaderId == operator.id }
        return if (isHqStaff || isSquadLeader) {
            EligibilityResult(eligible = true)
        } else {
            EligibilityResult(
                eligible = false,
                missingRequirements = listOf("Restricted to HQ Staff & Squad Leaders only")
            )
        }
    }

    fun evaluateAwardEligibility(
        medal: MedalDefinition,
        operator: Operator,
        ownedMedalIds: Set<String>,
        allCitations: List<AwardedMedal> = emptyList()
    ): EligibilityResult {
        val missing = mutableListOf<String>()
        if (operator.rank.tier.order < medal.minTierRequired.order) {
            missing += "Requires ${medal.minTierRequired.shortLabel} rank"
        }

        val maxHazard = operator.highestRiskSurvived?.baseDangerPenalty ?: -1
        val isFallen = operator.status == OperatorStatus.FALLEN
        val isHqStaff = operator.status == OperatorStatus.HQ_STAFF
        val combatMissionIds = GameCatalog.missions
            .asSequence()
            .filter { it.scope == MissionScope.FIELD_COMBAT }
            .map { it.id }
            .toSet()
        val diploMissionIds = GameCatalog.missions
            .asSequence()
            .filter { it.scope == MissionScope.STAFF_DIPLOMATIC }
            .map { it.id }
            .toSet()
        val combatOps = operator.combatMissionsCompleted
        val distinctCombatMissions = operator.completedMissionIds.count { it in combatMissionIds }
        val distinctDiploMissions = operator.completedMissionIds.count { it in diploMissionIds }
        val careerOps = operator.missionsCompleted

        // 1. Per-soldier uniqueness cap (Sovereign Star of Aegis is capped at 1 per soldier, across any number of heroes)
        val perSoldierCap = perSoldierCapForAward(medal.id)
        if (perSoldierCap != null) {
            val heldByOperator = maxOf(
                if (medal.id in ownedMedalIds) 1 else 0,
                allCitations.count { it.medalId == medal.id && it.operatorId == operator.id }
            )
            if (heldByOperator >= perSoldierCap) {
                missing += "Limit $perSoldierCap per soldier (already awarded)"
            }
        }

        // 2. Fallen soldiers may only receive posthumous combat valor, sacrifice, or service honors
        if (isFallen && medal.id !in posthumousAllowedAwardIds) {
            missing += "Not awarded posthumously"
        }

        when (medal.id) {
            "medal_sovereign_star" -> {
                if (!isFallen) {
                    if (operator.highestRiskSurvived != MissionRisk.EXTREME) {
                        missing += "Survive Extreme-Hazard Combat Op (or Fallen)"
                    }
                    if (combatOps < medal.minMissionsRequired) {
                        missing += "Complete ${medal.minMissionsRequired}+ Combat Ops ($combatOps/${medal.minMissionsRequired})"
                    }
                    if ("medal_distinguished_cross" !in ownedMedalIds && "medal_silver_vanguard" !in ownedMedalIds) {
                        missing += "Hold Service Cross or Silver Vanguard Star"
                    }
                }
            }
            "medal_distinguished_cross" -> {
                if (!isFallen) {
                    if (maxHazard < MissionRisk.HIGH.baseDangerPenalty) {
                        missing += "Survive High or Extreme Combat Op"
                    }
                    if (combatOps < 6) {
                        missing += "Complete 6+ Combat Ops ($combatOps/6)"
                    }
                    val hasPriorValorOrWound = "medal_bronze_valor" in ownedMedalIds ||
                        "medal_silver_vanguard" in ownedMedalIds ||
                        operator.timesWounded > 0
                    if (!hasPriorValorOrWound) {
                        missing += "Hold Bronze/Silver Valor or Combat Wound"
                    }
                }
            }
            "medal_defense_superior" -> {
                val staffMet = isHqStaff && operator.staffOpsSupported >= 5
                val diploMet = distinctDiploMissions >= 2
                if (!staffMet && !diploMet) {
                    missing += "HQ Staff (5+ Ops Supported) or 2+ Diplomatic Ops"
                }
            }
            "medal_silver_vanguard" -> {
                if (maxHazard < MissionRisk.HIGH.baseDangerPenalty) {
                    missing += "Survive High+ Combat Op"
                }
                if (combatOps < 4) {
                    missing += "Complete 4+ Combat Ops ($combatOps/4)"
                }
            }
            "medal_purple_shield" -> {
                if (operator.timesWounded <= 0 && !isFallen) {
                    missing += "Must be Wounded/Medevaced in combat (or Fallen)"
                }
            }
            "medal_bronze_valor" -> {
                if (!isFallen) {
                    if (maxHazard < MissionRisk.MODERATE.baseDangerPenalty) {
                        missing += "Survive Moderate+ Combat Op"
                    }
                    if (combatOps < 2) {
                        missing += "Complete 2+ Combat Ops ($combatOps/2)"
                    }
                }
            }
            "medal_meritorious" -> {
                val staffMet = isHqStaff && operator.staffOpsSupported >= 3
                val diploMet = distinctDiploMissions >= 2
                val careerMet = careerOps >= 5
                if (!staffMet && !diploMet && !careerMet) {
                    missing += "HQ Staff (3+ Support Ops), 2+ Diplo Ops, or 5+ Career Ops"
                }
            }
            "medal_army_commendation" -> {
                val staffMet = isHqStaff && operator.staffOpsSupported >= 2
                if (!staffMet && careerOps < 3) {
                    missing += "Complete 3+ Non-Training Ops or HQ Staff (2+ Ops)"
                }
            }
            "medal_expeditionary" -> {
                if (combatOps < 1 || operator.highestRiskSurvived == null) {
                    missing += "Complete 1+ Field Combat Deployment (non-training)"
                }
            }
            "medal_nco_dev" -> Unit
            "medal_good_conduct" -> {
                if (!operator.rank.tier.isEnlisted) {
                    missing += "Enlisted & NCO only (E-1 to E-9)"
                }
                if (operator.status == OperatorStatus.RECOVERING) {
                    missing += "Clear Medbay recovery first"
                }
                if (careerOps < 2 && operator.staffOpsSupported < 2) {
                    missing += "Complete 2+ Non-Training Ops in good standing"
                }
            }
            "medal_night_owl" -> {
                val nightQualified = "msn_night_qual" in operator.completedMissionIds ||
                    "msn_harbor_overwatch" in operator.completedMissionIds ||
                    "msn_deep_expedition" in operator.completedMissionIds
                if (!nightQualified) {
                    missing += "Complete Night Ops School or Night Op"
                }
            }
            "medal_basic_training" -> Unit
            "cord_general_aiguillette" -> {
                if (!isHqStaff) {
                    missing += "Requires active HQ Staff billet"
                }
            }
            "cord_valor_fourragere" -> {
                if (maxHazard < MissionRisk.MODERATE.baseDangerPenalty) {
                    missing += "Survive Moderate+ Combat Op"
                }
                if (combatOps < 5) {
                    missing += "Complete 5+ Combat Ops ($combatOps/5)"
                }
                if (distinctCombatMissions < 3) {
                    missing += "Complete 3+ Distinct Combat Missions ($distinctCombatMissions/3)"
                }
            }
            "cord_sf_emerald" -> {
                if ("msn_sf_school" !in operator.completedMissionIds) {
                    missing += "Graduate Special Forces Qualification (SFQC)"
                }
            }
            "cord_airborne_crimson" -> {
                if ("msn_night_qual" !in operator.completedMissionIds) {
                    missing += "Graduate Army Airborne & Parachutist School"
                }
            }
            "cord_engineer_scarlet" -> {
                if ("msn_engineer_school" !in operator.completedMissionIds) {
                    missing += "Graduate Combat Engineer & EOD School"
                }
            }
            "cord_signals_orange" -> {
                if ("msn_signals_school" !in operator.completedMissionIds) {
                    missing += "Graduate Cyber, Signals & Electronic Warfare School"
                }
            }
            "cord_intel_teal" -> {
                if ("msn_intel_school" !in operator.completedMissionIds) {
                    missing += "Graduate Military Intelligence School"
                }
            }
            "cord_medical_maroon" -> {
                if ("msn_medical_school" !in operator.completedMissionIds) {
                    missing += "Graduate Combat Medical & Trauma Academy"
                }
            }
            "cord_logistics_buff" -> {
                if ("msn_logistics_school" !in operator.completedMissionIds) {
                    missing += "Graduate Quartermaster & Logistics Sustainment School"
                }
            }
            "cord_infantry_blue" -> {
                if ("msn_basic_qual" !in operator.completedMissionIds) {
                    missing += "Graduate Marksmanship & Tactical Basics School"
                }
            }
            "insig_staff_crest" -> {
                if (!isHqStaff && operator.staffOpsSupported <= 0) {
                    missing += "Assign to HQ Staff billet"
                }
            }
            "insig_sf_tab" -> {
                if ("msn_sf_school" !in operator.completedMissionIds) {
                    missing += "Graduate Special Forces Qualification (SFQC)"
                }
            }
            "insig_master_para" -> {
                if ("insig_parachutist" !in ownedMedalIds) {
                    missing += "Hold Basic Parachutist Jump Wings first"
                }
                if (operator.missionsCompleted < 3) {
                    missing += "Complete 3+ Ops (${operator.missionsCompleted}/3)"
                }
            }
            "insig_ranger_tab" -> {
                if ("msn_ranger_school" !in operator.completedMissionIds) {
                    missing += "Graduate Army Ranger Leadership Course"
                }
            }
            "insig_sapper_tab" -> {
                if ("msn_sapper_school" !in operator.completedMissionIds) {
                    missing += "Graduate the Sapper Leader Course"
                }
            }
            "insig_airborne_tab" -> {
                if ("msn_night_qual" !in operator.completedMissionIds) {
                    missing += "Graduate Army Airborne & Parachutist School"
                }
            }
            "insig_eod_badge" -> {
                if ("msn_engineer_school" !in operator.completedMissionIds) {
                    missing += "Graduate Combat Engineer & EOD School"
                }
            }
            "insig_cyber_crest" -> {
                if ("msn_signals_school" !in operator.completedMissionIds) {
                    missing += "Graduate Cyber, Signals & Electronic Warfare School"
                }
            }
            "insig_intel_crest" -> {
                if ("msn_intel_school" !in operator.completedMissionIds) {
                    missing += "Graduate Military Intelligence School"
                }
            }
            "insig_air_assault" -> {
                if ("msn_night_qual" !in operator.completedMissionIds) {
                    missing += "Graduate Army Airborne & Parachutist School"
                }
            }
            "insig_eib_rifle" -> {
                if (!isInfantryOrSfQualified(operator)) missing += "Infantry or SF-qualified only"
                if ("msn_eib_testing" !in operator.completedMissionIds) {
                    missing += "Pass Expert Infantryman Badge Testing"
                }
            }
            "insig_cib" -> {
                if (!isInfantryOrSfQualified(operator)) missing += "Infantry or SF-qualified only"
                if (operator.combatMissionsCompleted < 1) missing += "Survive a field combat operation"
            }
            "insig_combat_medic" -> {
                if (operator.specialty != OperatorSpecialty.MEDIC) missing += "Medics only"
                if (operator.combatMissionsCompleted < 1) missing += "Survive a field combat operation"
            }
            "insig_cab" -> {
                if (operator.specialty == OperatorSpecialty.MEDIC || isInfantryOrSfQualified(operator)) {
                    missing += "Not for Infantry, SF-qualified or Medics (they earn the CIB or CMB)"
                }
                if (operator.combatMissionsCompleted < 1) missing += "Survive a field combat operation"
            }
            "insig_cmb_medic" -> {
                if ("msn_medical_school" !in operator.completedMissionIds) {
                    missing += "Graduate Combat Medical & Trauma Academy"
                }
            }
            "insig_logistics_crest" -> {
                if ("msn_logistics_school" !in operator.completedMissionIds && !isHqStaff) {
                    missing += "Graduate Quartermaster Logistics School (or HQ Staff)"
                }
            }
            "insig_pathfinder" -> {
                if ("msn_pathfinder_school" !in operator.completedMissionIds) {
                    missing += "Graduate Pathfinder School"
                }
            }
            "insig_parachutist" -> {
                if ("msn_night_qual" !in operator.completedMissionIds) {
                    missing += "Graduate Army Airborne & Parachutist School"
                }
            }
            else -> {
                if (operator.missionsCompleted + operator.staffOpsSupported < medal.minMissionsRequired && !isFallen) {
                    missing += "Requires ${medal.minMissionsRequired}+ Ops"
                }
            }
        }

        return EligibilityResult(eligible = missing.isEmpty(), missingRequirements = missing)
    }

    fun recommendedMedalForMissionDeed(
        mission: MissionTemplate,
        operator: Operator,
        outcome: OperatorOutcomeType,
        equippedGear: List<GearItem>,
        existingCitationMedalIds: Set<String>,
        missionSucceeded: Boolean = true,
        rollSeedPct: Int = 0,
        allCampaignCitations: List<AwardedMedal> = emptyList()
    ): Pair<String?, String> {
        if (outcome == OperatorOutcomeType.TRAINING_FAILED) {
            return null to "Did not pass evaluation standards in ${mission.title}. Partial XP logged toward rank progression (no graduation award)."
        }

        val isCombatMission = mission.scope == MissionScope.FIELD_COMBAT
        val effectiveHighestRisk = if (isCombatMission && missionSucceeded && outcome != OperatorOutcomeType.FALLEN) {
            val existing = operator.highestRiskSurvived
            if (existing == null || mission.risk.baseDangerPenalty > existing.baseDangerPenalty) {
                mission.risk
            } else {
                existing
            }
        } else {
            operator.highestRiskSurvived
        }
        val effectiveOp = operator.copy(
            status = when (outcome) {
                OperatorOutcomeType.FALLEN -> OperatorStatus.FALLEN
                OperatorOutcomeType.WOUNDED -> OperatorStatus.RECOVERING
                OperatorOutcomeType.STAFF_SUPPORT -> OperatorStatus.HQ_STAFF
                OperatorOutcomeType.SAFE,
                OperatorOutcomeType.TRAINING_PASSED,
                OperatorOutcomeType.TRAINING_FAILED -> when (operator.status) {
                    OperatorStatus.HQ_STAFF -> OperatorStatus.HQ_STAFF
                    OperatorStatus.RECOVERING -> OperatorStatus.READY
                    else -> operator.status
                }
            },
            missionsCompleted = if (!mission.risk.isTraining && outcome != OperatorOutcomeType.FALLEN) {
                maxOf(operator.missionsCompleted, 1)
            } else {
                operator.missionsCompleted
            },
            combatMissionsCompleted = if (isCombatMission && outcome != OperatorOutcomeType.FALLEN) {
                maxOf(operator.combatMissionsCompleted, 1)
            } else {
                operator.combatMissionsCompleted
            },
            completedMissionIds = if (missionSucceeded && outcome != OperatorOutcomeType.FALLEN) {
                operator.completedMissionIds + mission.id
            } else {
                operator.completedMissionIds
            },
            highestRiskSurvived = effectiveHighestRisk,
            timesWounded = if (outcome == OperatorOutcomeType.WOUNDED) maxOf(operator.timesWounded, 1) else operator.timesWounded
        )

        fun firstEligibleUnowned(candidates: List<String>, includeGeneralFallback: Boolean = true): String? {
            val primary = candidates.firstOrNull { id ->
                if (id in existingCitationMedalIds) return@firstOrNull false
                val def = GameCatalog.medals.find { it.id == id } ?: return@firstOrNull false
                evaluateAwardEligibility(def, effectiveOp, existingCitationMedalIds, allCampaignCitations).eligible
            }
            if (primary != null) return primary

            if (includeGeneralFallback) {
                val generalFallback = listOf("medal_army_commendation", "medal_good_conduct")
                val secondary = generalFallback.firstOrNull { id ->
                    if (id in existingCitationMedalIds) return@firstOrNull false
                    val def = GameCatalog.medals.find { it.id == id } ?: return@firstOrNull false
                    evaluateAwardEligibility(def, effectiveOp, existingCitationMedalIds, allCampaignCitations).eligible
                }
                if (secondary != null) return secondary
            }
            return null
        }

        return when (outcome) {
            OperatorOutcomeType.FALLEN -> {
                val medalId = firstEligibleUnowned(
                    listOf("medal_sovereign_star", "medal_distinguished_cross", "medal_purple_shield")
                )
                medalId to "Gave the last full measure of devotion holding the objective during ${mission.title}."
            }
            OperatorOutcomeType.WOUNDED -> {
                val candidates = buildList {
                    add("medal_purple_shield")
                    if (isCombatMission) add(combatBadgeIdFor(operator))
                    if (missionSucceeded && isCombatMission) {
                        add("medal_bronze_valor")
                    }
                }
                val medalId = firstEligibleUnowned(candidates, includeGeneralFallback = false)
                val suffix = if (!missionSucceeded && medalId == null) {
                    " Mission objective missed; no additional citation warranted."
                } else {
                    ""
                }
                medalId to "Sustained field injuries during ${mission.title} and completed medevac.$suffix"
            }
            OperatorOutcomeType.STAFF_SUPPORT -> recommendedMedalForHqStaffSupport(
                operator = operator,
                mission = mission,
                existingCitationMedalIds = existingCitationMedalIds,
                allCampaignCitations = allCampaignCitations
            )
            OperatorOutcomeType.TRAINING_FAILED -> {
                null to "Did not meet graduation standard on ${mission.title}."
            }
            OperatorOutcomeType.TRAINING_PASSED, OperatorOutcomeType.SAFE -> {
                // Surviving a combat op (won or lost) earns the soldier's combat badge the first time
                if (isCombatMission && outcome == OperatorOutcomeType.SAFE) {
                    val combatBadge = firstEligibleUnowned(listOf(combatBadgeIdFor(operator)), includeGeneralFallback = false)
                    if (combatBadge != null) {
                        return combatBadge to "Engaged the enemy under fire during ${mission.title}."
                    }
                }
                if (!missionSucceeded) {
                    return null to "Objective not achieved on ${mission.title}. Counted toward service record & promotion XP; no award warranted."
                }

                when (mission.risk) {
                    MissionRisk.TRAINING -> {
                        when (mission.id) {
                            "msn_medical_school" -> {
                                val candidates = listOf(
                                    "insig_cmb_medic",
                                    "cord_medical_maroon"
                                )
                                val medalId = firstEligibleUnowned(candidates, includeGeneralFallback = false)
                                medalId to "Graduated ${mission.title} trauma stabilization, triage & combat medevac lanes."
                            }
                            "msn_engineer_school" -> {
                                val candidates = listOf(
                                    "insig_eod_badge",
                                    "cord_engineer_scarlet"
                                )
                                val medalId = firstEligibleUnowned(candidates, includeGeneralFallback = false)
                                medalId to "Graduated ${mission.title} ordnance render-safe and breaching lanes."
                            }
                            "msn_logistics_school" -> {
                                val candidates = listOf(
                                    "insig_logistics_crest",
                                    "cord_logistics_buff"
                                )
                                val medalId = firstEligibleUnowned(candidates, includeGeneralFallback = false)
                                medalId to "Graduated ${mission.title} aerial rig, convoy sustainment & quartermaster board."
                            }
                            "msn_signals_school" -> {
                                val candidates = listOf(
                                    "insig_cyber_crest",
                                    "cord_signals_orange"
                                )
                                val medalId = firstEligibleUnowned(candidates, includeGeneralFallback = false)
                                medalId to "Graduated ${mission.title} tactical mesh encryption & electronic warfare exam."
                            }
                            "msn_intel_school" -> {
                                val candidates = listOf(
                                    "insig_intel_crest",
                                    "cord_intel_teal"
                                )
                                val medalId = firstEligibleUnowned(candidates, includeGeneralFallback = false)
                                medalId to "Graduated ${mission.title} imagery fusion and SIGINT boards."
                            }
                            "msn_night_qual" -> {
                                val candidates = listOf(
                                    "insig_airborne_tab",
                                    "insig_parachutist",
                                    "cord_airborne_crimson",
                                    "medal_night_owl",
                                    "insig_air_assault"
                                )
                                val medalId = firstEligibleUnowned(candidates, includeGeneralFallback = false)
                                medalId to "Graduated ${mission.title} static-line jump, fast-rope air assault & night drop zones — Maroon Beret authorized."
                            }
                            "msn_ranger_school" -> {
                                // Ranger awards the Ranger Tab only, for every MOS
                                val medalId = firstEligibleUnowned(listOf("insig_ranger_tab"), includeGeneralFallback = false)
                                medalId to "Graduated ${mission.title} in small-unit tactics, mountaineering & patrol leadership — Tan Beret authorized."
                            }
                            "msn_sf_school" -> {
                                val candidates = listOf(
                                    "insig_sf_tab",
                                    "cord_sf_emerald"
                                )
                                val medalId = firstEligibleUnowned(candidates, includeGeneralFallback = false)
                                medalId to "Graduated ${mission.title} SFAS & Robin Sage unconventional warfare board — Green Beret authorized."
                            }
                            "msn_sapper_school" -> {
                                val medalId = firstEligibleUnowned(listOf("insig_sapper_tab"), includeGeneralFallback = false)
                                medalId to "Graduated ${mission.title} demolitions, breaching and mountaineering lanes."
                            }
                            "msn_pathfinder_school" -> {
                                val medalId = firstEligibleUnowned(listOf("insig_pathfinder"), includeGeneralFallback = false)
                                medalId to "Graduated ${mission.title} drop zone, landing zone and sling-load lanes."
                            }
                            "msn_eib_testing" -> {
                                val medalId = firstEligibleUnowned(listOf("insig_eib_rifle"), includeGeneralFallback = false)
                                medalId to "Passed every ${mission.title} lane to standard and earned the Expert Infantryman Badge."
                            }
                            "msn_nco_school" -> {
                                val candidates = listOf("medal_nco_dev")
                                val medalId = firstEligibleUnowned(candidates, includeGeneralFallback = false)
                                medalId to "Graduated ${mission.title} — qualified for Soldier → NCO Promotion Board."
                            }
                            "msn_snco_school" -> {
                                val candidates = listOf("medal_nco_dev")
                                val medalId = firstEligibleUnowned(candidates, includeGeneralFallback = false)
                                medalId to "Graduated ${mission.title} — qualified for NCO → SNCO Promotion Board."
                            }
                            "msn_ocs_school" -> {
                                val candidates = listOf("insig_staff_crest")
                                val medalId = firstEligibleUnowned(candidates, includeGeneralFallback = false)
                                medalId to "Graduated ${mission.title} — qualified for Enlisted → Officer Commissioning Board."
                            }
                            else -> {
                                val candidates = listOf(
                                    "medal_basic_training",
                                    "cord_infantry_blue"
                                )
                                val medalId = firstEligibleUnowned(candidates, includeGeneralFallback = false)
                                medalId to "Passed range qualification, land navigation, and uniform inspection in ${mission.title}."
                            }
                        }
                    }
                    MissionRisk.LOW -> {
                        val medalId = if (mission.scope == MissionScope.STAFF_DIPLOMATIC) {
                            val priorityCandidates = listOf("medal_defense_superior", "cord_general_aiguillette")
                            firstEligibleUnowned(priorityCandidates, includeGeneralFallback = false)
                                ?: if (rollSeedPct < 35) {
                                    firstEligibleUnowned(
                                        listOf("insig_staff_crest", "medal_meritorious", "medal_army_commendation", "medal_good_conduct"),
                                        includeGeneralFallback = false
                                    )
                                } else null
                        } else {
                            val isFirstCombatDeployment = operator.highestRiskSurvived == null &&
                                "medal_expeditionary" !in existingCitationMedalIds
                            val firstCombatRibbon = if (isFirstCombatDeployment) {
                                firstEligibleUnowned(listOf("medal_expeditionary"), includeGeneralFallback = false)
                            } else null
                            firstCombatRibbon ?: if (rollSeedPct < 25) {
                                firstEligibleUnowned(
                                    listOf("medal_expeditionary", "medal_army_commendation", "medal_good_conduct", "medal_nco_dev"),
                                    includeGeneralFallback = false
                                )
                            } else null
                        }
                        val deed = if (medalId == null) {
                            "Completed ${mission.title} cleanly (+XP & Op count); no new medal citation warranted."
                        } else if (mission.scope == MissionScope.STAFF_DIPLOMATIC) {
                            "Represented Aegis Directorate command on ${mission.title} with distinction."
                        } else {
                            "Executed ${mission.title} across ${mission.theater.label} with disciplined readiness."
                        }
                        medalId to deed
                    }
                    MissionRisk.MODERATE -> {
                        val hasNvg = equippedGear.any { it.id.contains("nvg") || it.id.contains("thermal") }
                        val medalId = if (mission.scope == MissionScope.STAFF_DIPLOMATIC) {
                            val priorityCandidates = listOf("medal_defense_superior", "cord_general_aiguillette")
                            firstEligibleUnowned(priorityCandidates, includeGeneralFallback = false)
                                ?: if (rollSeedPct < 40) {
                                    firstEligibleUnowned(
                                        listOf("insig_staff_crest", "medal_meritorious", "medal_army_commendation"),
                                        includeGeneralFallback = false
                                    )
                                } else null
                        } else {
                            val milestoneCandidates = buildList {
                                if (mission.isNightOp && hasNvg) add("medal_night_owl")
                                add("medal_bronze_valor")
                                add("medal_expeditionary")
                                add("cord_valor_fourragere")
                            }
                            firstEligibleUnowned(milestoneCandidates, includeGeneralFallback = false)
                                ?: if (rollSeedPct < 30) {
                                    firstEligibleUnowned(
                                        listOf("medal_meritorious", "medal_army_commendation", "medal_good_conduct"),
                                        includeGeneralFallback = false
                                    )
                                } else null
                        }
                        val deed = if (medalId == null) {
                            "Held sector cleanly during ${mission.title} (+XP & Op count); no new citation warranted."
                        } else if (mission.scope == MissionScope.STAFF_DIPLOMATIC) {
                            "Directed diplomatic security and ceasefire protocol cleanly on ${mission.title}."
                        } else {
                            "Maintained composure under contested conditions during ${mission.title}."
                        }
                        medalId to deed
                    }
                    MissionRisk.HIGH -> {
                        val milestoneCandidates = listOf(
                            "medal_distinguished_cross",
                            "medal_silver_vanguard",
                            "cord_valor_fourragere",
                            "medal_bronze_valor",
                            "insig_master_para"
                        )
                        val medalId = firstEligibleUnowned(milestoneCandidates, includeGeneralFallback = false)
                            ?: if (rollSeedPct < 35) {
                                firstEligibleUnowned(
                                    listOf("medal_meritorious", "medal_army_commendation"),
                                    includeGeneralFallback = false
                                )
                            } else null
                        val deed = if (medalId == null) {
                            "Executed high-hazard breach & extraction on ${mission.title} (+XP & Op count)."
                        } else {
                            "Executed high-hazard breach & extraction cleanly on ${mission.title}."
                        }
                        medalId to deed
                    }
                    MissionRisk.EXTREME -> {
                        val milestoneCandidates = listOf(
                            "medal_sovereign_star",
                            "medal_distinguished_cross",
                            "medal_silver_vanguard",
                            "cord_valor_fourragere"
                        )
                        val medalId = firstEligibleUnowned(milestoneCandidates, includeGeneralFallback = false)
                            ?: if (rollSeedPct < 45) {
                                firstEligibleUnowned(
                                    listOf("medal_bronze_valor", "medal_meritorious"),
                                    includeGeneralFallback = false
                                )
                            } else null
                        val deed = if (medalId == null) {
                            "Survived extreme-hazard strike on ${mission.title} (+XP & Op count)."
                        } else {
                            "Displayed extraordinary valor under extreme hazard on ${mission.title}."
                        }
                        medalId to deed
                    }
                }
            }
        }
    }

    fun recommendedMedalForHqStaffSupport(
        operator: Operator,
        mission: MissionTemplate,
        existingCitationMedalIds: Set<String>,
        allCampaignCitations: List<AwardedMedal> = emptyList()
    ): Pair<String?, String> {
        val effectiveStaffOp = operator.copy(
            status = OperatorStatus.HQ_STAFF,
            staffOpsSupported = maxOf(operator.staffOpsSupported, 1)
        )
        val candidates = buildList {
            when (operator.hqRole) {
                HqStaffRole.CO_COMMANDING_OFFICER, HqStaffRole.XO_CHIEF_OF_STAFF, HqStaffRole.DEPUTY_COMMANDER -> {
                    add("insig_staff_crest")
                    add("cord_general_aiguillette")
                    add("medal_defense_superior")
                }
                HqStaffRole.S2_INTEL_OVERWATCH -> {
                    add("insig_intel_crest")
                    add("insig_cyber_crest")
                    if (effectiveStaffOp.rank.tier.isOfficer) add("insig_staff_crest")
                }
                HqStaffRole.S4_LOGISTICS_MEDEVAC -> {
                    add("insig_logistics_crest")
                    add("medal_meritorious")
                }
                HqStaffRole.S1_PERSONNEL_HONORS, HqStaffRole.S3_OPS_DIRECTOR -> {
                    add("medal_meritorious")
                    add("medal_army_commendation")
                    if (effectiveStaffOp.rank.tier.isOfficer) add("insig_staff_crest")
                }
                null -> {
                    if (effectiveStaffOp.rank.tier.isOfficer) {
                        add("insig_staff_crest")
                        add("cord_general_aiguillette")
                        add("medal_defense_superior")
                    }
                }
            }
            if (effectiveStaffOp.rank.tier.order >= RankTier.NCO.order) {
                add("cord_general_aiguillette")
                add("medal_nco_dev")
            }
            add("medal_meritorious")
            add("medal_army_commendation")
            add("insig_logistics_crest")
            add("medal_good_conduct")
        }
        val medalId = candidates.firstOrNull { id ->
            if (id in existingCitationMedalIds) return@firstOrNull false
            val def = GameCatalog.medals.find { it.id == id } ?: return@firstOrNull false
            evaluateAwardEligibility(def, effectiveStaffOp, existingCitationMedalIds, allCampaignCitations).eligible
        }
        val rolePrefix = operator.hqRole?.let { "${it.shortCode} HQ Duty: " } ?: "HQ Staff: "
        return medalId to "${rolePrefix}Directed overwatch, readiness & task-force coordination supporting ${mission.title}."
    }

    fun autoDistributeFieldGear(
        operators: List<Operator>,
        gearItems: List<GearItem>,
        citations: List<AwardedMedal>,
        facilities: List<FacilityState>,
        deployments: List<ActiveDeployment>
    ): AutoGearDistributionResult = GearAutoDistributor.distributeFieldGear(
        operators = operators,
        gearItems = gearItems,
        citations = citations,
        facilities = facilities,
        deployments = deployments
    )

    fun evaluatePromotionBoard(
        operator: Operator,
        allOperators: List<Operator>,
        citations: List<AwardedMedal>,
        gearItems: List<GearItem>,
        facilities: List<FacilityState>
    ): PromotionBoardEvaluation = PromotionBoardEvaluator.evaluate(
        operator = operator,
        allOperators = allOperators,
        citations = citations,
        gearItems = gearItems,
        facilities = facilities
    )
}
