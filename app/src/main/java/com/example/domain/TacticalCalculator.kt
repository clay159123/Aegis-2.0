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

    // Heroism roll for the Sovereign Star, per soldier per eligible mission (fraction, 0.01 = 1%)
    const val HEROISM_ODDS_LOW = 0.005
    const val HEROISM_ODDS_MODERATE = 0.01
    const val HEROISM_ODDS_HIGH = 0.025
    const val HEROISM_ODDS_EXTREME = 0.05
    const val HEROISM_FALLEN_MULTIPLIER = 2.0
    const val SOVEREIGN_STAR_ID = "medal_sovereign_star"

    /** Mission scopes that can ever roll for heroism. Schools and training never roll. */
    val HEROISM_ROLL_SCOPES: Set<MissionScope> = setOf(MissionScope.FIELD_COMBAT, MissionScope.STAFF_DIPLOMATIC)

    /**
     * Odds that one soldier earns a Sovereign Star heroism citation on this mission, or 0.0 when the
     * mission can't roll. Field combat always rolls. Diplomatic and staff missions roll only at Moderate
     * risk or higher, and only when someone was wounded or killed or the mission failed. Fallen soldiers
     * roll at double odds.
     */
    fun heroismOdds(
        mission: MissionTemplate,
        outcome: OperatorOutcomeType,
        missionSucceeded: Boolean,
        anyCasualty: Boolean
    ): Double {
        if (mission.scope !in HEROISM_ROLL_SCOPES || mission.risk.isTraining) return 0.0
        if (outcome == OperatorOutcomeType.STAFF_SUPPORT ||
            outcome == OperatorOutcomeType.TRAINING_PASSED ||
            outcome == OperatorOutcomeType.TRAINING_FAILED
        ) return 0.0
        if (mission.scope == MissionScope.STAFF_DIPLOMATIC) {
            if (mission.risk.baseDangerPenalty < MissionRisk.MODERATE.baseDangerPenalty) return 0.0
            if (!anyCasualty && missionSucceeded) return 0.0
        }
        val base = when (mission.risk) {
            MissionRisk.LOW -> HEROISM_ODDS_LOW
            MissionRisk.MODERATE -> HEROISM_ODDS_MODERATE
            MissionRisk.HIGH -> HEROISM_ODDS_HIGH
            MissionRisk.EXTREME -> HEROISM_ODDS_EXTREME
            else -> 0.0
        }
        return if (outcome == OperatorOutcomeType.FALLEN) base * HEROISM_FALLEN_MULTIPLIER else base
    }

    /** Short citation text for a heroism roll hit. */
    fun heroismDeedText(mission: MissionTemplate, outcome: OperatorOutcomeType): String =
        if (outcome == OperatorOutcomeType.FALLEN) {
            "Gave the last full measure of devotion during ${mission.title}, shielding the team at the cost of their own life. Recommended for the Sovereign Star."
        } else {
            "Acted with conspicuous gallantry above and beyond the call of duty during ${mission.title}, turning the fight at great personal risk. Recommended for the Sovereign Star."
        }
    const val SCHOOL_BASELINE_PASS_BONUS = 4
    const val STANDALONE_ROTATION_INTERVAL_MILLIS = 4L * 60_000L // 4 minutes
    const val ROTATION_REFRESH_INTEL_COST = 15.0

    fun campaignCapForAward(medalId: String): Int? = null

    /** Ribbons and medals that can only ever be earned once per soldier. */
    val ONE_TIME_RIBBON_IDS: Set<String> = setOf(
        "medal_basic_training", // Army Service Ribbon
        "medal_national_defense", // National Defense Service (on joining)
        "medal_night_owl",      // Night Ops Qualification Ribbon
        "medal_armed_forces_service", // Armed Forces Service Medal (item 55)
        "medal_overseas_service" // Overseas Service Ribbon (item 55)
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
            AwardCategory.MEDAL, AwardCategory.RIBBON -> MAX_REPEAT_AWARDS
        }
    }

    /**
     * Army regulations allow at most four devices on one ribbon, and a silver oak leaf cluster (or silver
     * service star) stands for five bronze ones. Four silver devices = 20 more awards, so 21 is the most
     * any repeatable award can be held.
     */
    const val MAX_RIBBON_DEVICES = 4
    const val MAX_REPEAT_AWARDS = 1 + MAX_RIBBON_DEVICES * 5

    /** True when this soldier already holds the most of [medalId] allowed. */
    fun isAtAwardLimit(operatorId: String, medalId: String, citations: List<AwardedMedal>): Boolean {
        val cap = perSoldierCapForAward(medalId) ?: return false
        return citations.count { it.operatorId == operatorId && it.medalId == medalId } >= cap
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

    /** The ten theater campaigns; the SF Unconventional Warfare campaign is endgame and doesn't count. */
    fun isAllCampaignsCleared(completedCampaignIds: Set<String>): Boolean =
        GameCatalog.campaigns.any { !it.isSpecialForces } &&
            GameCatalog.campaigns.filter { !it.isSpecialForces }.all { it.id in completedCampaignIds }

    /** Item 49/58: the SF campaign and SF missions open once the command has an SF squad. Set from game state. */
    @Volatile
    var sfSquadEstablished: Boolean = false

    /**
     * Item 103: wound and death odds (percent) for each soldier when a mission is aborted. The base odds
     * apply at the midpoint and scale with progress; helicopter insertions pull the team out faster.
     */
    fun abortCasualtyOddsPct(risk: MissionRisk, progress: Float, insertion: InsertionMethod): Pair<Double, Double> {
        val (wound, death) = when (risk) {
            MissionRisk.TRAINING -> 0.0 to 0.0
            MissionRisk.LOW -> 2.0 to 0.0
            MissionRisk.MODERATE -> 4.0 to 0.5
            MissionRisk.HIGH -> 7.0 to 1.0
            MissionRisk.EXTREME -> 10.0 to 2.0
        }
        val scale = (progress * 2.0).coerceIn(0.2, 2.0) * if (insertion.isHelicopter) ABORT_HELICOPTER_FACTOR else 1.0
        return (wound * scale) to (death * scale)
    }

    const val ABORT_HELICOPTER_FACTOR = 0.8

    /** Item 60: soldiers away on temporary duty (a TDY school). Set from game state. */
    @Volatile
    var tdyOperatorIds: Set<String> = emptySet()

    fun tdyOperatorIdsFor(deployments: List<ActiveDeployment>): Set<String> =
        deployments.filter { d -> GameCatalog.missions.find { it.id == d.templateId }?.isTdy == true }
            .flatMap { it.operatorIds }.toSet()

    fun isCampaignUnlocked(
        campaign: TheaterCampaign,
        completedCampaignIds: Set<String>
    ): Boolean {
        if (campaign.isSpecialForces) return sfSquadEstablished
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
        val campaign = GameCatalog.campaigns.find { it.id == campId } ?: return true
        if (isAllCampaignsCleared(completedCampaignIds) && !campaign.isSpecialForces) return true
        if (!isCampaignUnlocked(campaign, completedCampaignIds)) return false
        val stageOrder = mission.campaignStageOrder ?: 1
        if (stageOrder <= 1) return true
        val prevStageId = campaign.stageMissionIds.getOrNull(stageOrder - 2) ?: return true
        return prevStageId in completedStageIds
    }

    fun campaignSupplyCostMultiplier(completedCampaignIds: Set<String>): Double =
        if ("camp_veridian" in completedCampaignIds) 0.92 else 1.0

    fun campaignIntelYieldMultiplier(completedCampaignIds: Set<String>): Double =
        (if ("camp_boreal" in completedCampaignIds) 1.10 else 1.0) * ironholdYield(completedCampaignIds)

    fun campaignCreditYieldMultiplier(completedCampaignIds: Set<String>): Double =
        (if ("camp_obsidian" in completedCampaignIds) 1.10 else 1.0) * ironholdYield(completedCampaignIds)

    fun campaignPrestigeAndXpMultiplier(completedCampaignIds: Set<String>): Double =
        if ("camp_kestrel" in completedCampaignIds) 1.10 else 1.0

    /** Cleared campaigns, set from game state, for theater perks applied deep in the calculations. */
    @Volatile
    var clearedCampaignIds: Set<String> = emptySet()

    /** Campaign X: +5% credits, supplies and intel. */
    private fun ironholdYield(completedCampaignIds: Set<String>): Double =
        if ("camp_ironhold" in completedCampaignIds) 1.05 else 1.0

    /** Campaign V (+10%) and X (+5%): supplies earned on missions. */
    fun campaignSupplyYieldMultiplier(completedCampaignIds: Set<String>): Double =
        (if ("camp_sahr" in completedCampaignIds) 1.10 else 1.0) * ironholdYield(completedCampaignIds)

    /** Campaign VI: missions run 8% shorter. */
    fun campaignDurationMultiplier(completedCampaignIds: Set<String> = clearedCampaignIds): Double =
        if ("camp_verdance" in completedCampaignIds) 0.92 else 1.0

    /** Campaign VII: +2% pass chance on field ops. */
    fun campaignFieldPassBonus(completedCampaignIds: Set<String> = clearedCampaignIds): Int =
        if ("camp_meridian" in completedCampaignIds) 2 else 0

    /** Campaign VIII: Medbay recovery 20% faster. */
    fun campaignRecoveryMultiplier(completedCampaignIds: Set<String> = clearedCampaignIds): Double =
        if ("camp_karst" in completedCampaignIds) 0.80 else 1.0

    /** Campaign IX: +2 survival on field ops. */
    fun campaignSurvivalBonus(completedCampaignIds: Set<String> = clearedCampaignIds): Int =
        if ("camp_talon" in completedCampaignIds) 2 else 0

    /** Single missions on the rotating board at once. */
    const val ROTATING_BOARD_SIZE = 5

    fun pickNextRotatingMissions(
        previousIds: List<String> = emptyList(),
        random: kotlin.random.Random = kotlin.random.Random
    ): List<String> {
        val pool = GameCatalog.rotatingStandalonePool
        if (pool.size <= ROTATING_BOARD_SIZE) return pool.map { it.id }

        val prevSet = previousIds.toSet()
        val picked = mutableListOf<MissionTemplate>()

        fun pickFrom(candidates: List<MissionTemplate>) {
            val eligible = candidates.filter { it !in picked }
            if (eligible.isEmpty()) return
            val fresh = eligible.filter { it.id !in prevSet }
            val source = if (fresh.isNotEmpty()) fresh else eligible
            picked += source[random.nextInt(source.size)]
        }

        fun fieldAt(vararg risks: MissionRisk) = pool.filter {
            it.scope == MissionScope.FIELD_COMBAT && it.requiredSquads == 1 && it.risk in risks
        }

        // Item 88: every board has a one-squad High and a one-squad Extreme mission.
        // 1. Low/Moderate field, 2. High field, 3. Extreme field, 4. Diplomatic/Staff, 5. Wildcard
        pickFrom(fieldAt(MissionRisk.LOW, MissionRisk.MODERATE))
        pickFrom(fieldAt(MissionRisk.HIGH))
        pickFrom(fieldAt(MissionRisk.EXTREME))
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
        "medal_expeditionary",
        "medal_flying_cross"
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

        // Cords resolve at render time: Auto picks from owned cords, a manual pick is honored if still owned
        val cordSettings = operator.wardrobe.alphas
        val leftChoice = if (cordSettings.cordCustomized) cordSettings.equippedLeftCordId else UniformTailorOverrides.SLOT_AUTO
        val rightChoice = if (cordSettings.cordCustomized) cordSettings.equippedRightCordId else UniformTailorOverrides.SLOT_AUTO
        val leftCord = if (leftChoice == UniformTailorOverrides.SLOT_AUTO) {
            UniformRegaliaRules.autoCordForShoulder(operator, sorted, isRightShoulder = false)
        } else {
            leftChoice?.let { id ->
                ownedById[id]?.takeIf {
                    it.category == AwardCategory.SHOULDER_CORD &&
                        (overrides.freeTailorOverride || UniformRegaliaRules.isCordAllowedOnShoulder(it, isRightShoulder = false))
                }
            }
        }
        val rightCord = if (rightChoice == UniformTailorOverrides.SLOT_AUTO) {
            UniformRegaliaRules.autoCordForShoulder(operator, sorted, isRightShoulder = true)
                ?.takeIf { it.id != leftCord?.id }
        } else {
            rightChoice
                ?.takeIf { it != leftCord?.id }
                ?.let { id ->
                    ownedById[id]?.takeIf {
                        it.category == AwardCategory.SHOULDER_CORD &&
                            (overrides.freeTailorOverride || UniformRegaliaRules.isCordAllowedOnShoulder(it, isRightShoulder = true))
                    }
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
        facilities.find { it.type == type }?.level ?: type.startLevel

    /** A building can be built or upgraded once Command HQ reaches its unlock level. */
    fun isFacilityUnlocked(type: FacilityType, facilities: List<FacilityState>): Boolean =
        facilityLevel(facilities, FacilityType.COMMAND_HQ) >= type.unlockHqLevel

    /** Construction crews: one to start, a second at Command HQ Lv 5. */
    fun constructionCrews(facilities: List<FacilityState>): Int =
        if (facilityLevel(facilities, FacilityType.COMMAND_HQ) >= 5) 2 else 1

    /** Why [type] can't start an upgrade right now, or null when it can. */
    fun facilityUpgradeBlocker(type: FacilityType, facilities: List<FacilityState>): String? {
        val state = facilities.find { it.type == type }
        val level = state?.level ?: type.startLevel
        return when {
            !isFacilityUnlocked(type, facilities) -> "Requires Command HQ Lv ${type.unlockHqLevel}"
            level >= MAX_FACILITY_LEVEL -> "Max level"
            state?.isUpgrading == true -> "Already under construction"
            facilities.count { it.isUpgrading } >= constructionCrews(facilities) ->
                if (constructionCrews(facilities) < 2) "Construction crew busy (second crew at HQ Lv 5)" else "Both construction crews busy"
            else -> null
        }
    }

    /** Prestige to finish a running build now, scaled by the time left. */
    fun rushPrestigeCost(facility: FacilityState, nowMillis: Long): Int {
        if (!facility.isUpgrading) return 0
        val minutesLeft = ((facility.upgradeEndsAtMillis - nowMillis).coerceAtLeast(0L) / 60_000.0)
        return kotlin.math.ceil(minutesLeft * RUSH_PRESTIGE_PER_MINUTE).toInt().coerceAtLeast(RUSH_MIN_PRESTIGE)
    }

    /** Buildings whose timers have run out, at their new level. */
    fun completedUpgrades(facilities: List<FacilityState>, nowMillis: Long): List<FacilityState> =
        facilities.filter { it.isUpgrading && nowMillis >= it.upgradeEndsAtMillis }
            .map { it.copy(level = (it.level + 1).coerceAtMost(MAX_FACILITY_LEVEL), upgradeEndsAtMillis = 0L, upgradeStartedAtMillis = 0L) }

    /** DFAC: +5% squad cohesion gains per level. */
    fun dfacCohesionMultiplier(facilities: List<FacilityState>): Double =
        1.0 + facilityLevel(facilities, FacilityType.DFAC).coerceAtLeast(0) * 0.05

    /** DFAC: scales transfer and retirement request chances down by 6% per level (to 40% at most). */
    fun dfacPetitionFactor(facilities: List<FacilityState>): Double =
        (1.0 - facilityLevel(facilities, FacilityType.DFAC).coerceAtLeast(0) * 0.06).coerceAtLeast(0.4)

    /** Training Range: school pass bonus, +1% per level or +2% for SF selection, Ranger and EIB Testing. */
    fun rangeSchoolBonusPct(mission: MissionTemplate, facilities: List<FacilityState>): Int {
        val lv = facilityLevel(facilities, FacilityType.TRAINING_RANGE).coerceAtLeast(0)
        val perLevel = if (mission.id in RANGE_DOUBLE_BONUS_SCHOOLS) 2 else 1
        return lv * perLevel
    }

    /** Item 106: the Range trains shooters, so EIB and Ranger gain double; SF assessment gains the normal +1. */
    val RANGE_DOUBLE_BONUS_SCHOOLS = setOf("msn_ranger_school", "msn_eib_testing")

    /** Aid Station: medbay recovery time multiplier, -7% per level (floor 37%). */
    fun aidRecoveryMultiplier(facilities: List<FacilityState>): Double =
        (1.0 - facilityLevel(facilities, FacilityType.AID_STATION).coerceAtLeast(0) * 0.07).coerceAtLeast(0.37) *
            campaignRecoveryMultiplier()

    /** Aid Station: chance a would-be fatality is pulled back as wounded instead, +5% per level. */
    fun aidSaveChancePct(facilities: List<FacilityState>): Int =
        facilityLevel(facilities, FacilityType.AID_STATION).coerceAtLeast(0) * 5

    const val BASE_RECOVERY_MILLIS = 45_000L

    // ---------- Rifle qualification ----------
    const val RIFLE_QUAL_MISSION_ID = "msn_rifle_qual"

    // ---- Warrant officers and aviation (Session 9) ----
    const val OCS_MISSION_ID = "msn_ocs_school"
    const val WOCS_MISSION_ID = "msn_wocs"
    const val FLIGHT_SCHOOL_MISSION_ID = "msn_flight_school"
    const val FLIGHT_HOURS_MISSION_ID = "msn_flight_hours"
    const val AVIATOR_WINGS_ID = "insig_aviator_wings"
    const val SENIOR_AVIATOR_WINGS_ID = "insig_senior_aviator_wings"
    const val MASTER_AVIATOR_WINGS_ID = "insig_master_aviator_wings"
    const val AIR_MEDAL_ID = "medal_air_medal"
    /** Flight skill a new Flight School graduate starts with. */
    const val FLIGHT_SCHOOL_STARTING_SKILL = 10
    /** Item 95: flight skill per hour flown in a passed Flight Hours session, up to [MAX_FLIGHT_SKILL]. */
    const val FLIGHT_SKILL_PER_HOUR = 2
    const val MAX_FLIGHT_SKILL = 100
    /** Item 95: a Flight Hours session books 1 to 10 hours; the base mission length is for [FLIGHT_HOURS_PER_SESSION]. */
    const val MIN_FLIGHT_HOURS = 1
    const val MAX_FLIGHT_HOURS = 10
    const val FLIGHT_HOURS_PER_SESSION = 2

    /** Mission length multiplier for a Flight Hours session of [hours]. */
    fun flightHoursDurationMultiplier(hours: Int): Double =
        hours.coerceIn(MIN_FLIGHT_HOURS, MAX_FLIGHT_HOURS).toDouble() / FLIGHT_HOURS_PER_SESSION

    /**
     * A finished Flight Hours session. Passed: every hour is logged and builds skill, plus the Airfield's
     * per-session bonus. Failed: half the hours (at least 1) are logged at half the skill rate.
     */
    fun applyFlightHours(op: Operator, hours: Int, passed: Boolean, airfield: Int = airfieldLevel): Operator {
        val booked = hours.coerceIn(MIN_FLIGHT_HOURS, MAX_FLIGHT_HOURS)
        val logged = if (passed) booked else ((booked + 1) / 2).coerceAtLeast(1)
        val skill = if (passed) logged * FLIGHT_SKILL_PER_HOUR + airfieldFlightSkillBonus(airfield) else logged * FLIGHT_SKILL_PER_HOUR / 2
        return op.copy(
            flightSkill = (op.flightSkill + skill).coerceAtMost(MAX_FLIGHT_SKILL),
            flightHoursLogged = op.flightHoursLogged + logged
        )
    }

    // ============================================================
    // Item 97: Airfield levels
    // ============================================================
    /** The current Airfield level, set from game state for perks applied deep in the calculations. */
    @Volatile
    var airfieldLevel: Int = 0

    /** Helicopter survival: +1 for every two Airfield levels. */
    fun airfieldHeloSurvivalBonus(level: Int = airfieldLevel): Int = level.coerceAtLeast(0) / 2
    /** Helicopter time: 2% less per Airfield level. */
    const val AIRFIELD_HELO_TIME_CUT_PER_LEVEL = 0.02
    /** Flight School pass chance: +2% per Airfield level. */
    fun airfieldFlightSchoolBonus(level: Int = airfieldLevel): Int = level.coerceAtLeast(0) * 2
    /** Flight Hours: +1 flight skill per passed session for every two Airfield levels. */
    fun airfieldFlightSkillBonus(level: Int = airfieldLevel): Int = level.coerceAtLeast(0) / 2
    /** Airborne and HALO scatter: 1 point lower per Airfield level. */
    fun airfieldScatterCut(level: Int = airfieldLevel): Int = level.coerceAtLeast(0)
    const val AIRFIELD_FAST_ROPE_LEVEL = 3
    const val AIRFIELD_NIGHT_FLYING_LEVEL = 5
    const val NIGHT_FLYING_PASS_BONUS = 3

    /** Helicopter missions that can be in the air at once: 1, then 2 from level 4, then 3 from level 8. */
    fun maxHelicopterMissions(level: Int = airfieldLevel): Int = when {
        level >= 8 -> 3
        level >= 4 -> 2
        else -> 1
    }

    /** One-line perk list for the Airfield building card. */
    fun airfieldPerkSummary(level: Int): String = buildList {
        add("${maxHelicopterMissions(level)} helo mission${if (maxHelicopterMissions(level) > 1) "s" else ""} at once")
        add("Helo -${(level * AIRFIELD_HELO_TIME_CUT_PER_LEVEL * 100).roundToInt()}% time, +${airfieldHeloSurvivalBonus(level)} survival")
        add("-${(level * HELO_AIRFIELD_DISCOUNT_PER_LEVEL * 100).roundToInt().coerceAtMost((HELO_MAX_AIRFIELD_DISCOUNT * 100).roundToInt())}% helo supply")
        add("Flight School +${airfieldFlightSchoolBonus(level)}%")
        if (airfieldFlightSkillBonus(level) > 0) add("Flight Hours +${airfieldFlightSkillBonus(level)} skill")
        add("Drop scatter -${airfieldScatterCut(level)}")
        if (level >= AIRFIELD_FAST_ROPE_LEVEL) add("Fast Rope")
        if (level >= AIRFIELD_NIGHT_FLYING_LEVEL) add("Night flying +$NIGHT_FLYING_PASS_BONUS% pass")
    }.joinToString(" • ")

    // ============================================================
    // Item 96: SF-only schools have no hard attempt limit; retries past the old limit cost more
    // ============================================================
    val SF_RETRY_SCHOOL_IDS: Set<String> = setOf("msn_sf_school", "msn_combat_diver_school", "msn_mff_school")
    /** Each retry past the old limit adds this share of the school's supply cost for that soldier. */
    const val SF_RETRY_SURCHARGE_PER_ATTEMPT = 0.5

    /** Item 101: extra supply for soldiers retrying any school past its old attempt limit. */
    fun schoolRetrySurcharge(mission: MissionTemplate, team: List<Operator>, multiplier: Double): Int {
        val limit = mission.schoolRequirements?.maxAttempts ?: return 0
        return team.sumOf { op ->
            val over = (op.schoolAttempts[mission.id] ?: 0) - limit + 1
            if (over > 0) mission.supplyCost * multiplier * SF_RETRY_SURCHARGE_PER_ATTEMPT * over else 0.0
        }.roundToInt()
    }
    /** Hours logged per combat sortie flown as pilot. */
    const val FLIGHT_HOURS_PER_SORTIE = 1
    const val SENIOR_WINGS_FLIGHT_SKILL = 40
    const val MASTER_WINGS_FLIGHT_SKILL = 65
    /** Combat missions flown as pilot per Air Medal (first award and each oak leaf cluster). */
    const val SORTIES_PER_AIR_MEDAL = 3
    /** Squad power added by a technical warrant officer in the squad. */
    const val TECH_WARRANT_SQUAD_TECH_BONUS = 2
    /** Pass-chance bonus (percentage points) when a technical warrant officer's MOS fits the mission. */
    const val TECH_WARRANT_MOS_PASS_BONUS = 3

    /** Ops a warrant officer needs before the board for [next] grade (CW2..CW5). */
    fun warrantOpsRequired(next: OperatorRank): Int = when (next) {
        OperatorRank.CW2 -> 3
        OperatorRank.CW3 -> 6
        OperatorRank.CW4 -> 10
        OperatorRank.CW5 -> 15
        else -> 0
    }

    /** Flight skill an aviator needs before the board for [next] grade (CW2..CW5). */
    fun warrantFlightSkillRequired(next: OperatorRank): Int = when (next) {
        OperatorRank.CW2 -> 20
        OperatorRank.CW3 -> 35
        OperatorRank.CW4 -> 50
        OperatorRank.CW5 -> 65
        else -> 0
    }

    // ---- Session 10: MOS fit and insertion methods (items 32, 33) ----
    /** Pass chance per recommended MOS on the team (counted once per MOS). Also used for SF coverage (item 36). */
    const val MOS_COVERAGE_BONUS_PCT = 3
    const val MOS_COVERAGE_BONUS_CAP_PCT = 12

    const val AIRBORNE_SCHOOL_ID = "msn_night_qual"
    const val COMBAT_DIVER_SCHOOL_ID = "msn_combat_diver_school"
    const val COMBAT_DIVER_BADGE_ID = "insig_combat_diver"
    /** Military Free Fall school arrives with SF missions (item 36). */
    const val MFF_SCHOOL_ID = "msn_mff_school"

    const val HELO_DURATION_MULT = 0.75
    /** Each point of pilot flight skill trims helicopter time a little more, down to [HELO_MIN_DURATION_MULT]. */
    const val HELO_DURATION_PER_SKILL = 0.001
    const val HELO_MIN_DURATION_MULT = 0.65
    /** Casualty risk cut: survival points added for every soldier. */
    const val HELO_SURVIVAL_BONUS = 3
    /** Extra survival per 20 points of pilot flight skill. */
    const val HELO_SURVIVAL_PER_20_SKILL = 1
    const val HELO_BASE_SUPPLY_COST = 12.0
    /** Each Airfield level cuts helicopter supply cost by this share, up to [HELO_MAX_AIRFIELD_DISCOUNT]. */
    const val HELO_AIRFIELD_DISCOUNT_PER_LEVEL = 0.06
    const val HELO_MAX_AIRFIELD_DISCOUNT = 0.5

    const val AIRBORNE_DURATION_MULT = 0.6
    const val AIRBORNE_SCATTER_CHANCE_PCT = 15
    const val SCATTER_PASS_PENALTY = 10
    const val SCATTER_SURVIVAL_PENALTY = 6

    const val HALO_DURATION_MULT = 0.6
    const val HALO_STEALTH_PASS_BONUS = 15
    const val HALO_SCATTER_CHANCE_PCT = 5

    const val MARITIME_DURATION_MULT = 0.85
    const val MARITIME_STEALTH_PASS_BONUS = 10

    private val stealthFocusWords = listOf("Stealth", "Recon", "Infiltration", "Night", "Intel")

    /** Missions that reward a quiet approach (HALO and Maritime bonuses apply). */
    fun isStealthWeighted(mission: MissionTemplate): Boolean =
        stealthFocusWords.any { mission.primaryFocus.contains(it, ignoreCase = true) }

    // ---- Session 11: platoon missions (item 35) and Special Forces (item 36) ----
    const val PLATOON_MAX_SQUADS = 2
    const val PLATOON_LEADER_PASS_BONUS = 5
    const val PLATOON_LEADER_SURVIVAL_BONUS = 2
    /** Two-squad missions pay about this much more credit and prestige than a comparable one-squad mission. */
    const val PLATOON_REWARD_MULT = 1.6
    /** Item 58: SF teams are 4 to 6 SF-qualified soldiers, built around the SF squad. */
    const val SF_MIN_TEAM = 4
    const val SF_MAX_TEAM = 6
    /** SF squad members a team needs; the rest can be SF-qualified soldiers on temporary reassignment. */
    const val SF_SQUAD_CORE = 2
    const val SF_SCHOOL_ID = "msn_sf_school"
    const val MFF_BADGE_ID = "insig_mff_wings"
    const val UW_CAMPAIGN_ID = "camp_uw"

    /** Item 102: SF schools the SOF officer supports (Language school joins in Session 21). */
    val SF_SCHOOL_IDS: Set<String> = setOf("msn_sf_school", "msn_combat_diver_school", "msn_mff_school")

    /** SF missions and every stage of the SF campaign. */
    fun isSfOperation(mission: MissionTemplate): Boolean =
        mission.requiresSfTeam || GameCatalog.campaigns.any { it.id == mission.campaignId && it.isSpecialForces }

    /** Item 58: the command's SF squad, if it has one. */
    fun sfSquadOf(squads: List<TacticalSquad>): TacticalSquad? = squads.firstOrNull { it.isSpecialForces && it.memberIds.isNotEmpty() }

    /** True once the command has an SF squad, which opens SF missions and the SF campaign. */
    fun sfMissionsUnlocked(squads: List<TacticalSquad>): Boolean = sfSquadOf(squads) != null

    /** The platoon leader on a two-squad mission: the chosen officer, or the senior commissioned officer on the team. */
    fun platoonLeaderFor(mission: MissionTemplate, team: List<Operator>, chosenId: String? = null): Operator? {
        if (mission.requiredSquads < 2) return null
        val officers = team.filter { it.rank.isCommissioned }
        return officers.find { it.id == chosenId } ?: officers.maxByOrNull { it.rank.seniorityIndex }
    }

    /**
     * Why this team can't take the mission as a whole, or null. Covers SF team makeup and platoon missions
     * (two squads plus a commissioned platoon leader). Per-soldier checks stay in canOperatorDeployOnMission.
     */
    fun teamBlocker(mission: MissionTemplate, team: List<Operator>, squads: List<TacticalSquad>, platoonLeaderId: String? = null): String? {
        if (mission.requiresSfTeam) {
            if (team.size !in SF_MIN_TEAM..SF_MAX_TEAM) return "SF teams are $SF_MIN_TEAM to $SF_MAX_TEAM soldiers (${team.size} selected)"
            team.firstOrNull { !isSfQualified(it) }?.let { return "${it.callsign} isn't SF-qualified" }
            val sfSquad = sfSquadOf(squads) ?: return "Form an SF squad first"
            val core = team.count { it.id in sfSquad.memberIds }
            if (core < SF_SQUAD_CORE) return "Needs $SF_SQUAD_CORE+ from ${sfSquad.name} ($core/$SF_SQUAD_CORE)"
        }
        if (mission.requiredSquads >= 2) {
            val squadsOnTeam = squads.count { sq -> team.count { it.id in sq.memberIds } >= 2 }
            if (squadsOnTeam < 2) return "Needs two squads (2+ soldiers from each)"
            if (platoonLeaderFor(mission, team, platoonLeaderId) == null) return "Needs an officer (2LT+) as platoon leader"
            if (platoonLeaderId != null && team.none { it.id == platoonLeaderId && it.rank.isCommissioned }) {
                return "Platoon leader must be a commissioned officer on the team"
            }
        }
        return null
    }

    /** Recommended MOS the team covers, and the pass bonus (+3% each, up to +12%). Untagged missions: none. */
    fun mosCoverage(mission: MissionTemplate, team: List<Operator>): Pair<Set<OperatorSpecialty>, Int> {
        // SF teams: every different MOS on the team counts (a medic and an engineer beat four riflemen)
        if (mission.requiresSfTeam) {
            val distinct = team.map { it.specialty }.toSet()
            return distinct to (distinct.size * MOS_COVERAGE_BONUS_PCT).coerceAtMost(MOS_COVERAGE_BONUS_CAP_PCT)
        }
        if (mission.recommendedSpecialties.isEmpty()) return emptySet<OperatorSpecialty>() to 0
        val covered = mission.recommendedSpecialties.filter { mos -> team.any { it.specialty == mos } }.toSet()
        return covered to (covered.size * MOS_COVERAGE_BONUS_PCT).coerceAtMost(MOS_COVERAGE_BONUS_CAP_PCT)
    }

    fun isAirborneQualified(op: Operator): Boolean = AIRBORNE_SCHOOL_ID in op.completedMissionIds
    fun isFreeFallQualified(op: Operator): Boolean = MFF_SCHOOL_ID in op.completedMissionIds
    fun isCombatDiver(op: Operator): Boolean = COMBAT_DIVER_SCHOOL_ID in op.completedMissionIds
    const val AIR_ASSAULT_SCHOOL_ID = "msn_air_assault_school"
    /** Items 111/115: SERE and the SF Aviation school; only SF aviators fly SF missions. */
    const val SERE_SCHOOL_ID = "msn_sere_school"
    const val SF_AVIATION_SCHOOL_ID = "msn_sf_aviation_school"
    fun isSereQualified(op: Operator): Boolean = SERE_SCHOOL_ID in op.completedMissionIds
    fun isSfAviator(op: Operator): Boolean = op.isAviator && SF_AVIATION_SCHOOL_ID in op.completedMissionIds
    fun isAirAssaultQualified(op: Operator): Boolean = AIR_ASSAULT_SCHOOL_ID in op.completedMissionIds
    /** Fast Rope: a little quicker than landing, and a bonus on assault and urban missions. */
    const val FAST_ROPE_DURATION_FACTOR = 0.95
    const val FAST_ROPE_ASSAULT_PASS_BONUS = 4

    private fun isAssaultOrUrban(m: MissionTemplate): Boolean =
        m.theater.terrain == Terrain.URBAN || listOf("Assault", "Breach", "Strike").any { it in m.primaryFocus }

    /** Why this team can't use [method] on [mission], or null when it can. */
    fun insertionBlocker(
        mission: MissionTemplate,
        method: InsertionMethod,
        team: List<Operator>,
        pilot: Operator?,
        facilities: List<FacilityState>
    ): String? {
        if (method !in mission.allowedInsertions) return "Not available for this mission"
        return when (method) {
            InsertionMethod.GROUND -> null
            InsertionMethod.HELICOPTER -> when {
                facilityLevel(facilities, FacilityType.AIRFIELD) < 1 -> "Needs an Airfield"
                SoldierEvents.effects.heloGroundedUntilMillis > System.currentTimeMillis() -> "Aircraft grounded for maintenance"
                pilot == null -> "Assign a pilot from the Aviation Pool"
                !(pilot.isAviator && pilot.isWarrantOfficer) -> "Pilot must be a warrant officer aviator"
                isSfOperation(mission) && !isSfAviator(pilot) -> "SF missions need an SF aviator"
                else -> null
            }
            InsertionMethod.FAST_ROPE -> when {
                facilityLevel(facilities, FacilityType.AIRFIELD) < AIRFIELD_FAST_ROPE_LEVEL -> "Needs Airfield level $AIRFIELD_FAST_ROPE_LEVEL"
                SoldierEvents.effects.heloGroundedUntilMillis > System.currentTimeMillis() -> "Aircraft grounded for maintenance"
                pilot == null -> "Assign a pilot from the Aviation Pool"
                !(pilot.isAviator && pilot.isWarrantOfficer) -> "Pilot must be a warrant officer aviator"
                isSfOperation(mission) && !isSfAviator(pilot) -> "SF missions need an SF aviator"
                else -> team.firstOrNull { !isAirAssaultQualified(it) }?.let { "${it.callsign} isn't Air Assault qualified" }
            }
            InsertionMethod.AIRBORNE -> team.firstOrNull { !isAirborneQualified(it) }
                ?.let { "${it.callsign} isn't Airborne-qualified" }
            InsertionMethod.HALO -> if (!mission.requiresSfTeam) "SF missions only"
                else team.firstOrNull { !isFreeFallQualified(it) }?.let { "${it.callsign} isn't Military Free Fall qualified" }
            InsertionMethod.MARITIME -> when {
                !mission.requiresSfTeam -> "SF missions only"
                !mission.coastal -> "Coastal missions only"
                else -> team.firstOrNull { !isCombatDiver(it) }?.let { "${it.callsign} isn't a Combat Diver" }
            }
        }
    }

    fun insertionDurationMultiplier(method: InsertionMethod, pilot: Operator?): Double = when (method) {
        InsertionMethod.GROUND -> 1.0
        InsertionMethod.HELICOPTER, InsertionMethod.FAST_ROPE ->
            (HELO_DURATION_MULT - (pilot?.flightSkill ?: 0) * HELO_DURATION_PER_SKILL).coerceAtLeast(HELO_MIN_DURATION_MULT) *
                (1.0 - airfieldLevel.coerceAtLeast(0) * AIRFIELD_HELO_TIME_CUT_PER_LEVEL).coerceAtLeast(0.7) *
                (if (method == InsertionMethod.FAST_ROPE) FAST_ROPE_DURATION_FACTOR else 1.0)
        InsertionMethod.AIRBORNE -> AIRBORNE_DURATION_MULT
        InsertionMethod.HALO -> HALO_DURATION_MULT
        InsertionMethod.MARITIME -> MARITIME_DURATION_MULT
    }

    fun scatterChancePct(method: InsertionMethod): Int = when (method) {
        InsertionMethod.AIRBORNE -> (AIRBORNE_SCATTER_CHANCE_PCT - airfieldScatterCut()).coerceAtLeast(3)
        InsertionMethod.HALO -> (HALO_SCATTER_CHANCE_PCT - airfieldScatterCut()).coerceAtLeast(1)
        else -> 0
    }

    /** Pass-chance points from the insertion (a scattered drop costs points). */
    fun insertionPassDelta(mission: MissionTemplate, method: InsertionMethod, scattered: Boolean): Int {
        val bonus = when (method) {
            InsertionMethod.HALO -> if (isStealthWeighted(mission)) HALO_STEALTH_PASS_BONUS else 0
            InsertionMethod.MARITIME -> if (isStealthWeighted(mission)) MARITIME_STEALTH_PASS_BONUS else 0
            InsertionMethod.HELICOPTER ->
                if (mission.isNightOp && airfieldLevel >= AIRFIELD_NIGHT_FLYING_LEVEL) NIGHT_FLYING_PASS_BONUS else 0
            InsertionMethod.FAST_ROPE ->
                (if (mission.isNightOp && airfieldLevel >= AIRFIELD_NIGHT_FLYING_LEVEL) NIGHT_FLYING_PASS_BONUS else 0) +
                    (if (isAssaultOrUrban(mission)) FAST_ROPE_ASSAULT_PASS_BONUS else 0)
            else -> 0
        }
        return bonus - if (scattered) SCATTER_PASS_PENALTY else 0
    }

    /** Survival points from the insertion for each soldier: helicopters cut risk; scattered drops raise it. */
    fun insertionSurvivalDelta(method: InsertionMethod, pilot: Operator?, airOpsBonusPct: Int, scattered: Boolean): Int {
        val helo = if (method.isHelicopter) {
            val skill = (pilot?.flightSkill ?: 0) / 20 * HELO_SURVIVAL_PER_20_SKILL
            val airOps = (HELO_SURVIVAL_BONUS * airOpsBonusPct / 100.0).roundToInt()
            HELO_SURVIVAL_BONUS + skill + airOps + airfieldHeloSurvivalBonus()
        } else 0
        return helo - if (scattered) SCATTER_SURVIVAL_PENALTY else 0
    }

    /** Extra supply for a helicopter lift: Airfield levels and the Air Operations Officer cut it. */
    fun insertionSupplyCost(method: InsertionMethod, facilities: List<FacilityState>, airOpsBonusPct: Int): Int {
        if (!method.isHelicopter) return 0
        val airfield = facilityLevel(facilities, FacilityType.AIRFIELD).coerceAtLeast(0)
        val fieldCut = (airfield * HELO_AIRFIELD_DISCOUNT_PER_LEVEL).coerceAtMost(HELO_MAX_AIRFIELD_DISCOUNT)
        return (HELO_BASE_SUPPLY_COST * (1.0 - fieldCut) * (1.0 - airOpsBonusPct / 100.0)).roundToInt()
    }

    /** The seated Air Operations Officer's helicopter bonus, or 0. */
    fun airOpsBonusPct(operators: List<Operator>): Int =
        operators.filter { it.status == OperatorStatus.HQ_STAFF }.maxOfOrNull { it.hqRole?.heloInsertionBonusPct ?: 0 } ?: 0

    /** Everything the prep screen shows, and what launch uses. Ground team only: the pilot is separate. */
    data class MissionPlanPreview(
        val passPct: Int,
        val survivalPct: Int,
        val durationSeconds: Int,
        val supplyCost: Int,
        val insertionBlocker: String?,
        /** Team makeup problem (SF team, two squads, platoon leader), or null. */
        val teamBlocker: String?,
        val platoonLeader: Operator?,
        val coveredMos: Set<OperatorSpecialty>,
        val mosBonusPct: Int,
        val scatterChancePct: Int,
        val cohesionOddsBonusPct: Int,
        val mixedSquads: Boolean,
        val breakdown: List<Pair<String, String>>
    )

    fun previewMissionPlan(
        mission: MissionTemplate,
        team: List<Operator>,
        insertion: InsertionMethod,
        pilot: Operator?,
        allOperators: List<Operator>,
        gearItems: List<GearItem>,
        citations: List<AwardedMedal>,
        facilities: List<FacilityState>,
        hqStaffCount: Int,
        hqStaffSummary: HqStaffSummary?,
        squads: List<TacticalSquad>,
        smartWardrobeEnabled: Boolean,
        completedCampaignIds: Set<String> = emptySet(),
        platoonLeaderId: String? = null
    ): MissionPlanPreview {
        val airOps = airOpsBonusPct(allOperators)
        val blocker = insertionBlocker(mission, insertion, team, pilot, facilities)
        val teamProblem = teamBlocker(mission, team, squads, platoonLeaderId)
        val pass = missionSuccessOddsPct(
            mission, team, gearItems, citations, facilities, hqStaffCount, hqStaffSummary, squads, smartWardrobeEnabled,
            insertion = insertion
        )
        val survival = (squadAvgSurvivalPct(mission, team, gearItems, citations, facilities, hqStaffCount, hqStaffSummary, squads, smartWardrobeEnabled) +
            insertionSurvivalDelta(insertion, pilot, airOps, scattered = false)).coerceIn(42, 98)
        val duration = (mission.durationSeconds * durationMultiplier(facilities, hqStaffSummary) *
            insertionDurationMultiplier(insertion, pilot)).roundToInt()
        val supply = (dispatchSupplyCost(mission, team.size, supplyCostMultiplier(facilities, hqStaffSummary, completedCampaignIds)) *
            SoldierEvents.schoolDiscountFactor(mission, team, System.currentTimeMillis())).roundToInt() +
            insertionSupplyCost(insertion, facilities, airOps)
        val (covered, mosBonus) = mosCoverage(mission, team)
        val cohesion = SquadCohesionRules.evaluateActiveBonus(team, squads, mission.requiredSquads > 1)
        val scatter = scatterChancePct(insertion)
        val breakdown = buildList {
            if (mission.requiresSfTeam) {
                add("SF team" to "${team.count { isSfQualified(it) }}/${team.size} SF-qualified")
                add("MOS coverage" to "+$mosBonus% (${covered.size} different MOS)")
            } else if (mission.recommendedSpecialties.isNotEmpty()) {
                add("MOS coverage" to "+$mosBonus% (${covered.size}/${mission.recommendedSpecialties.size})")
            }
            platoonLeaderFor(mission, team, platoonLeaderId)?.let {
                add("Platoon leader" to "${it.rank.code} ${it.callsign} • +$PLATOON_LEADER_PASS_BONUS% pass")
            }
            add("Insertion" to buildString {
                append(insertion.shortLabel)
                val passDelta = insertionPassDelta(mission, insertion, false)
                if (passDelta != 0) append(" • +$passDelta% pass")
                val survDelta = insertionSurvivalDelta(insertion, pilot, airOps, false)
                if (survDelta != 0) append(" • +$survDelta survival")
                if (scatter > 0) append(" • $scatter% scatter risk")
            })
            if (mission.scope == MissionScope.FIELD_COMBAT && mission.theater.terrain != Terrain.GARRISON) {
                add("Camo" to "${mission.theater.terrain.label}: ${teamCamoEffect(mission, team, squads, smartWardrobeEnabled).label}")
            }
            add("Cohesion" to when {
                cohesion == null -> "None"
                cohesion.mixedSquads -> "+${cohesion.successOddsBonusPct}% (mixed squads, cut 25%)"
                else -> "+${cohesion.successOddsBonusPct}% (${cohesion.squad.name})"
            })
            val wo = technicalWarrantOddsBonus(mission, team)
            if (wo > 0) add("Warrant officer" to "+$wo%")
            val officer = team.filter { it.rank.isCommissioned }.maxByOrNull { it.rank.seniorityIndex }
            if (officer != null && mission.requiredSquads < 2) add("Officer" to "${officer.rank.code} ${officer.callsign} leading")
        }
        return MissionPlanPreview(
            passPct = pass,
            survivalPct = survival,
            durationSeconds = duration,
            supplyCost = supply,
            insertionBlocker = blocker,
            teamBlocker = teamProblem,
            platoonLeader = platoonLeaderFor(mission, team, platoonLeaderId),
            coveredMos = covered,
            mosBonusPct = mosBonus,
            scatterChancePct = scatter,
            cohesionOddsBonusPct = cohesion?.successOddsBonusPct ?: 0,
            mixedSquads = cohesion?.mixedSquads == true,
            breakdown = breakdown
        )
    }

    // ---- Session 10b: experience (item 40) and combat jumps (item 39) ----
    /** Experience points at which a stat gains +1: up to +6 per stat, each step slower. */
    val EXPERIENCE_GAIN_THRESHOLDS: List<Int> = listOf(4, 10, 18, 28, 40, 54)
    const val EXP_POINTS_LOW = 1
    const val EXP_POINTS_MODERATE = 2
    const val EXP_POINTS_HIGH = 3
    const val EXP_POINTS_EXTREME = 4
    const val EXP_POINTS_HQ_STAFF_TECH = 1
    const val EXP_POINTS_RIFLE_QUAL_FIREPOWER = 1
    /** Combat jump stars shown on jump wings. A sixth jump and beyond still shows five. */
    const val MAX_COMBAT_JUMP_STARS = 5

    /** Stat bonus earned from [points] of experience (0..6). */
    fun experienceGain(points: Int): Int = EXPERIENCE_GAIN_THRESHOLDS.count { points >= it }

    /** Points still needed for the next +1, or null once maxed. */
    fun pointsToNextExperienceGain(points: Int): Int? =
        EXPERIENCE_GAIN_THRESHOLDS.firstOrNull { points < it }?.let { it - points }

    /**
     * Experience each trained stat earns on this mission: by risk, halved (rounded up) on a failure.
     * Fallen soldiers, schools and training earn nothing.
     */
    fun missionExperiencePoints(mission: MissionTemplate, outcome: OperatorOutcomeType, missionSucceeded: Boolean): Int {
        if (mission.scope == MissionScope.TRAINING || mission.risk.isTraining) return 0
        if (outcome == OperatorOutcomeType.FALLEN || outcome == OperatorOutcomeType.STAFF_SUPPORT) return 0
        val base = when (mission.risk) {
            MissionRisk.LOW -> EXP_POINTS_LOW
            MissionRisk.MODERATE -> EXP_POINTS_MODERATE
            MissionRisk.HIGH -> EXP_POINTS_HIGH
            MissionRisk.EXTREME -> EXP_POINTS_EXTREME
            else -> 0
        }
        return if (missionSucceeded) base else (base + 1) / 2
    }

    /** Adds [points] to each of [stats]; returns the soldier and a debrief note for any +1 that landed. */
    fun applyExperience(op: Operator, stats: Set<StatType>, points: Int): Pair<Operator, String> {
        if (points <= 0 || stats.isEmpty()) return op to ""
        var updated = op
        val gains = mutableListOf<String>()
        for (stat in stats) {
            val before = updated.expPoints(stat)
            val after = before + points
            updated = updated.withExpPoints(stat, after)
            val delta = experienceGain(after) - experienceGain(before)
            if (delta > 0) gains += "${stat.label} +$delta (experience)"
        }
        return updated to if (gains.isEmpty()) "" else " ${gains.joinToString(", ")}."
    }

    /**
     * For an award the soldier already holds, what this one adds, e.g. "2nd award: 1 Bronze OLC".
     * Null for a first award.
     */
    fun repeatAwardLabel(op: Operator, medal: MedalDefinition, citations: List<AwardedMedal>): String? {
        val held = citations.count { it.operatorId == op.id && it.medalId == medal.id }
        if (held == 0) return null
        val next = held + 1
        val withNext = citations + AwardedMedal("preview", op.id, medal.id, "", awardedAtMillis = 0L)
        val device = UniformRegaliaRules.resolveAwardDevices(op, medal.id, withNext).summaryLabel
            ?.replace("OLC", "Oak Leaf Cluster")
        val ordinal = when {
            next % 100 in 11..13 -> "${next}th"
            next % 10 == 1 -> "${next}st"
            next % 10 == 2 -> "${next}nd"
            next % 10 == 3 -> "${next}rd"
            else -> "${next}th"
        }
        return "$ordinal award" + (device?.let { ": $it" } ?: "")
    }

    /** Combat jump stars to draw on this soldier's jump wings (0..5). */
    fun combatJumpStars(op: Operator): Int = op.combatJumps.coerceIn(0, MAX_COMBAT_JUMP_STARS)

    /** True once a soldier has started (attempted) or finished this school. */
    private fun hasEnteredSchool(operator: Operator, missionId: String): Boolean =
        missionId in operator.completedMissionIds || (operator.schoolAttempts[missionId] ?: 0) > 0
    const val MAX_REQUAL_ATTEMPTS = 3
    const val QUAL_ROLL_MAX = RIFLE_QUAL_ROLL_MAX
    const val RANGE_QUAL_BONUS_PER_LEVEL = 1

    fun qualRank(tier: MarksmanshipBadgeTier?): Int = rifleQualRank(tier)

    fun qualTierForScore(score: Int): MarksmanshipBadgeTier = rifleQualTierForScore(score)

    /** Score: the soldier's own firepower (no gear) + Training Range bonus + [roll]. */
    fun rifleQualScore(operator: Operator, citations: List<AwardedMedal>, facilities: List<FacilityState>, roll: Int): Int =
        ((soldierStats(operator, citations).firepower +
            facilityLevel(facilities, FacilityType.TRAINING_RANGE).coerceAtLeast(0) * RANGE_QUAL_BONUS_PER_LEVEL +
            roll.coerceIn(0, QUAL_ROLL_MAX)) * (100 + operator.eventBuffs.rifleQualBonusPct)) / 100

    /** The best badge the soldier holds or has waiting. */
    fun bestQualification(operator: Operator): MarksmanshipBadgeTier? =
        listOfNotNull(operator.marksmanshipQual, operator.pendingMarksmanshipQual).maxByOrNull { qualRank(it) }

    data class RifleQualResult(
        val operatorId: String,
        val callsign: String,
        val score: Int,
        val previous: MarksmanshipBadgeTier?,
        val achieved: MarksmanshipBadgeTier,
        val attemptsLeft: Int?
    ) {
        /** Only a better badge than the soldier holds is worth awarding. */
        val improved: Boolean get() = qualRank(achieved) > qualRank(previous)
    }

    /**
     * Applies a qualification score: a better badge waits for the player to award it
     * (pending); a lower score never lowers the badge.
     */
    fun applyRifleQualification(operator: Operator, score: Int, attemptsLeft: Int?): Pair<Operator, RifleQualResult> {
        val previous = bestQualification(operator)
        val result = RifleQualResult(operator.id, operator.callsign, score, previous, qualTierForScore(score), attemptsLeft)
        val updated = if (result.improved) {
            operator.copy(pendingMarksmanshipQual = result.achieved, lastQualScore = score)
        } else {
            operator.copy(lastQualScore = score)
        }
        return updated to result
    }


    /** "45s", "5m", "1h 12m", "7h" */
    fun formatBuildTime(seconds: Long): String {
        val s = seconds.coerceAtLeast(0L)
        val h = s / 3600
        val m = (s % 3600) / 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            m > 0 -> "${m}m"
            else -> "${s}s"
        }
    }

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
        val xpBonusPct: Int = 0,
        /** Items 102/110: billet-specific bonuses (the best seated billet counts). */
        val sfMissionPassBonusPct: Int = 0,
        val sfSchoolPassBonusPct: Int = 0,
        val ncoSchoolPassBonusPct: Int = 0,
        val cohesionGainBonusPct: Int = 0
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
            xpBonusPct = xp,
            sfMissionPassBonusPct = staff.maxOf { it.hqRole?.sfMissionPassBonusPct ?: 0 },
            sfSchoolPassBonusPct = staff.maxOf { it.hqRole?.sfSchoolPassBonusPct ?: 0 },
            ncoSchoolPassBonusPct = staff.maxOf { it.hqRole?.ncoSchoolPassBonusPct ?: 0 },
            cohesionGainBonusPct = staff.maxOf { it.hqRole?.cohesionGainBonusPct ?: 0 }
        )
    }

    fun unlockedHqRoles(facilities: List<FacilityState>): List<HqStaffRole> {
        val slots = maxHqStaffSlots(facilities)
        return HqStaffRole.entries.filter { it.slotOrder <= slots }.sortedBy { it.slotOrder }
    }

    fun evaluateHqRoleEligibility(role: HqStaffRole, operator: Operator): EligibilityResult {
        val missing = mutableListOf<String>()
        val rankShort = if (role.minRank.isWarrant) {
            !operator.rank.isWarrant || operator.rank.warrantGrade < role.minRank.warrantGrade
        } else {
            operator.rank.seniorityIndex < role.minRank.seniorityIndex
        }
        if (rankShort) {
            missing += "Requires ${role.minRank.code} (${role.minRank.payGrade})+ (currently ${operator.rank.code})"
        }
        if (role.requiresSfQualified && !isSfQualified(operator)) {
            missing += "Requires Special Forces qualification"
        }
        if (role.isEnlistedOnly && !operator.rank.tier.isEnlisted) {
            missing += "Enlisted only (${role.title})"
        }
        if (role.requiresAviator && !(operator.isWarrantOfficer && operator.isAviator)) {
            missing += "Requires a warrant officer aviator (153A)"
        }
        if (role.requiresTechnicalWarrant && !operator.isTechnicalWarrant) {
            missing += "Requires a technical warrant officer"
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
            OperatorStatus.TRANSFERRED -> return EligibilityResult(false, listOf("Transferred out"))
            OperatorStatus.RECOVERING -> return EligibilityResult(false, listOf("In Medbay"))
            OperatorStatus.ON_LEAVE -> return EligibilityResult(false, listOf("On leave"))
            OperatorStatus.CAPTURED -> return EligibilityResult(false, listOf("Held as a POW"))
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

        if (mission.requiresSfTeam && !isSfQualified(operator)) {
            return EligibilityResult(false, listOf("SF-qualified soldiers only"))
        }
        if (operator.isAviator && mission.scope == MissionScope.FIELD_COMBAT) {
            return EligibilityResult(false, listOf("Aviators fly insertions; they don't join ground teams"))
        }

        if (mission.scope == MissionScope.TRAINING) {
            val repeatable = mission.id == RIFLE_QUAL_MISSION_ID || mission.id == FLIGHT_HOURS_MISSION_ID
            if (mission.id in operator.completedMissionIds && !repeatable) {
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
                // Item 77: NCO Academy and the Senior Leader Course are for enlisted soldiers
                if ((mission.id == "msn_nco_school" || mission.id == "msn_snco_school") && !operator.rank.tier.isEnlisted) {
                    missing += "Enlisted leadership school: officers and warrant officers don't attend"
                }
                // Item 29: Officer Candidate School and Warrant Officer Candidate School are either-or
                if (mission.id == OCS_MISSION_ID && (operator.isWarrantOfficer || hasEnteredSchool(operator, WOCS_MISSION_ID))) {
                    missing += "On the Warrant Officer path (WOCS): OCS is closed"
                }
                if (mission.id == WOCS_MISSION_ID) {
                    if (hasEnteredSchool(operator, OCS_MISSION_ID) || operator.rank.isCommissioned) {
                        missing += "On the commissioned path (OCS): WOCS is closed"
                    } else if (!operator.rank.tier.isEnlisted) {
                        missing += "Enlisted soldiers only"
                    }
                }
                if (mission.id == FLIGHT_SCHOOL_MISSION_ID) {
                    if (!operator.isWarrantOfficer) missing += "Warrant officers only (graduate WOCS first)"
                    if (facilityLevel(facilities, FacilityType.AIRFIELD) < 1) missing += "Build an Airfield first"
                }
                if (mission.id == FLIGHT_HOURS_MISSION_ID) {
                    if (!operator.isAviator) missing += "Aviators only (graduate Flight School first)"
                    if (facilityLevel(facilities, FacilityType.AIRFIELD) < 1) missing += "Needs an Airfield"
                    if (operator.flightSkill >= MAX_FLIGHT_SKILL) missing += "Flight skill maxed ($MAX_FLIGHT_SKILL)"
                }
                // Item 101: no school washes anyone out; retries past the old limit cost more (schoolRetrySurcharge).
                // Rifle Qual stays open until the soldier reaches Expert.
                if (mission.id == RIFLE_QUAL_MISSION_ID && bestQualification(operator) == MarksmanshipBadgeTier.EXPERT) {
                    missing += "Already qualified Expert"
                }

                if (req.minRankTier.order > RankTier.SOLDIER.order && operator.rank.tier.order < req.minRankTier.order) {
                    missing += "Requires ${req.minRankTier.shortLabel}+ rank (currently ${operator.rank.code})"
                }
                req.minRank?.let { min ->
                    if (operator.rank.seniorityIndex < min.seniorityIndex) {
                        missing += "Requires ${min.code} (${min.payGrade})+ (currently ${operator.rank.code})"
                    }
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
            DutyUniformContext.FIELD_SCHOOL -> return if (operator.isAviator) WardrobeKit.BRAVOS else WardrobeKit.FIELD
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
        } else if (operator.status == OperatorStatus.HQ_STAFF && dutyContext == DutyUniformContext.DIPLOMATIC_MISSION) {
            // Item 91: HQ staff change into Mess for diplomatic missions
            WardrobeKit.MESS
        } else {
            operator.activeWardrobeKit
        }
        // Item 93: aviators have no field uniform; their working uniform is the flight suit (Bravos slot)
        if (resolved == WardrobeKit.FIELD && operator.isAviator) return WardrobeKit.BRAVOS
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
                RankTier.OFFICER, RankTier.WARRANT -> DressUniformStyle.OFFICER_MESS_BLUES
                RankTier.SNCO, RankTier.NCO, RankTier.SOLDIER -> DressUniformStyle.ENLISTED_MESS_BLUES
            }
            WardrobeKit.ALPHAS, WardrobeKit.BRAVOS, WardrobeKit.FIELD -> when (tier) {
                RankTier.FLAG_OFFICER -> DressUniformStyle.FLAG_STAFF_FORMAL
                RankTier.OFFICER, RankTier.WARRANT -> DressUniformStyle.OFFICER_SERVICE_BLUES
                RankTier.SNCO, RankTier.NCO -> DressUniformStyle.NCO_PARADE_BLUES
                RankTier.SOLDIER -> DressUniformStyle.ARMY_SERVICE_GREEN
            }
        }
    }

    /** Bravos beret Auto order, highest first: SF, Ranger, Airborne, then the black beret. */
    val BERET_LADDER: List<HeadgearStyle> = listOf(
        HeadgearStyle.EMERALD_BERET,
        HeadgearStyle.TAN_BERET,
        HeadgearStyle.MAROON_BERET,
        HeadgearStyle.OBSIDIAN_BERET
    )

    fun defaultHeadgearForKit(
        kit: WardrobeKit,
        operator: Operator,
        ownedMedalIds: Set<String> = emptySet(),
        preferred: HeadgearStyle? = null
    ): HeadgearStyle {
        val tier = operator.rank.tier
        return when (kit) {
            WardrobeKit.BRAVOS -> {
                // Only an explicit player pick ([preferred]) holds; otherwise wear the highest earned beret
                if (preferred != null && preferred.kind == HeadgearKind.BERET) return preferred
                BERET_LADDER.firstOrNull { evaluateHeadgearEligibility(it, operator, ownedMedalIds).eligible }
                    ?: HeadgearStyle.OBSIDIAN_BERET
            }
            WardrobeKit.ALPHAS, WardrobeKit.MESS -> {
                if (preferred != null && preferred.kind != HeadgearKind.BERET) return preferred
                when (tier) {
                    RankTier.FLAG_OFFICER -> HeadgearStyle.FLAG_GENERAL_CAP
                    RankTier.OFFICER, RankTier.WARRANT -> HeadgearStyle.OFFICER_PEAKED_CAP
                    RankTier.SNCO, RankTier.NCO -> HeadgearStyle.NCO_SERVICE_CAP
                    RankTier.SOLDIER -> HeadgearStyle.ENLISTED_SERVICE_CAP
                }
            }
            WardrobeKit.FIELD -> operator.headgearStyle
        }
    }

    /** One kit's squad standard as it applies to a soldier: items they don't qualify for are dropped (null). */
    data class KitStandard(
        val camo: CamoPattern? = null,
        val dressStyle: DressUniformStyle? = null,
        val headgear: HeadgearStyle? = null,
        val gloves: Boolean? = null
    )

    /** The squad whose uniform standard a soldier follows: their field squad (HQ staff follow none). */
    fun standardSquadFor(operator: Operator, squads: List<TacticalSquad>): TacticalSquad? =
        if (operator.status == OperatorStatus.HQ_STAFF) null else squads.firstOrNull { operator.id in it.memberIds }

    /** The squad standard for [kit] as written, ignoring whether the soldier qualifies. */
    fun rawKitStandard(squad: TacticalSquad?, kit: WardrobeKit): KitStandard = when {
        squad == null -> KitStandard()
        kit == WardrobeKit.FIELD -> KitStandard(camo = squad.standardCamo)
        kit == WardrobeKit.BRAVOS -> KitStandard(dressStyle = squad.standardBravosDressStyle, headgear = squad.standardBravosHeadgear)
        kit == WardrobeKit.ALPHAS -> KitStandard(dressStyle = squad.standardDressStyle, headgear = squad.standardHeadgear, gloves = squad.standardAlphasGloves)
        else -> KitStandard(dressStyle = squad.standardMessDressStyle, headgear = squad.standardMessHeadgear, gloves = squad.standardMessGloves)
    }

    /**
     * Item 56: a squad standard names a style ("service cap", "service blues", "mess blues"); each soldier
     * wears their own tier's version of it. Berets, AGSU and Honor Guard are the same for every tier.
     */
    fun tierVersionOf(std: KitStandard, tier: RankTier): KitStandard =
        std.copy(dressStyle = std.dressStyle?.let { tierVersionOf(it, tier) }, headgear = std.headgear?.let { tierVersionOf(it, tier) })

    fun tierVersionOf(style: DressUniformStyle, tier: RankTier): DressUniformStyle {
        val serviceBlues = setOf(
            DressUniformStyle.ARMY_DRESS_BLUES, DressUniformStyle.NCO_PARADE_BLUES,
            DressUniformStyle.OFFICER_SERVICE_BLUES, DressUniformStyle.FLAG_STAFF_FORMAL
        )
        val messBlues = setOf(DressUniformStyle.ENLISTED_MESS_BLUES, DressUniformStyle.OFFICER_MESS_BLUES, DressUniformStyle.FLAG_MESS_FORMAL)
        return when (style) {
            in serviceBlues -> when (tier) {
                RankTier.SOLDIER -> DressUniformStyle.ARMY_DRESS_BLUES
                RankTier.NCO, RankTier.SNCO -> DressUniformStyle.NCO_PARADE_BLUES
                RankTier.OFFICER, RankTier.WARRANT -> DressUniformStyle.OFFICER_SERVICE_BLUES
                RankTier.FLAG_OFFICER -> DressUniformStyle.FLAG_STAFF_FORMAL
            }
            in messBlues -> when (tier) {
                RankTier.SOLDIER, RankTier.NCO, RankTier.SNCO -> DressUniformStyle.ENLISTED_MESS_BLUES
                RankTier.OFFICER, RankTier.WARRANT -> DressUniformStyle.OFFICER_MESS_BLUES
                RankTier.FLAG_OFFICER -> DressUniformStyle.FLAG_MESS_FORMAL
            }
            else -> style
        }
    }

    fun tierVersionOf(headgear: HeadgearStyle, tier: RankTier): HeadgearStyle =
        if (headgear.kind == HeadgearKind.BERET) headgear else when (tier) {
            RankTier.SOLDIER -> HeadgearStyle.ENLISTED_SERVICE_CAP
            RankTier.NCO, RankTier.SNCO -> HeadgearStyle.NCO_SERVICE_CAP
            RankTier.OFFICER, RankTier.WARRANT -> HeadgearStyle.OFFICER_PEAKED_CAP
            RankTier.FLAG_OFFICER -> HeadgearStyle.FLAG_GENERAL_CAP
        }

    fun squadStandardFor(
        operator: Operator,
        squads: List<TacticalSquad>,
        kit: WardrobeKit,
        ownedMedalIds: Set<String>
    ): KitStandard {
        val raw = tierVersionOf(rawKitStandard(standardSquadFor(operator, squads), kit), operator.rank.tier)
        val free = operator.tailorOverrides.freeTailorOverride
        return raw.copy(
            dressStyle = raw.dressStyle?.takeIf { free || evaluateDressStyleEligibility(it, operator, ownedMedalIds, kit).eligible },
            headgear = raw.headgear?.takeIf { free || evaluateHeadgearEligibility(it, operator, ownedMedalIds, kit).eligible }
        )
    }

    /**
     * Standard items this soldier can't wear, each with what they wear instead,
     * e.g. "Maroon Beret: Requires Airborne School → wears Black Beret".
     */
    fun squadStandardFallbacks(
        operator: Operator,
        squads: List<TacticalSquad>,
        kit: WardrobeKit,
        ownedMedalIds: Set<String>
    ): List<String> {
        if (operator.tailorOverrides.freeTailorOverride) return emptyList()
        val raw = tierVersionOf(rawKitStandard(standardSquadFor(operator, squads), kit), operator.rank.tier)
        val worn = resolveKitAppearance(operator, kit, squads, ownedMedalIds)
        return buildList {
            raw.dressStyle?.let { want ->
                val check = evaluateDressStyleEligibility(want, operator, ownedMedalIds, kit)
                if (!check.eligible) add("${want.shortLabel}: ${check.missingRequirements.firstOrNull() ?: "not qualified"} → wears ${worn.dressStyle.shortLabel}")
            }
            raw.headgear?.let { want ->
                val check = evaluateHeadgearEligibility(want, operator, ownedMedalIds, kit)
                if (!check.eligible) add("${want.label}: ${check.missingRequirements.firstOrNull() ?: "not qualified"} → wears ${worn.headgearStyle.label}")
            }
        }
    }

    // ============================================================
    // Item 94: camouflage matched to the theater's terrain
    // ============================================================
    const val CAMO_MATCH_PASS_BONUS = 3
    const val CAMO_MATCH_SURVIVAL_BONUS = 2
    const val CAMO_WRONG_PASS_PENALTY = 2
    const val CAMO_WRONG_SURVIVAL_PENALTY = 1

    /** The pattern Smart Duty-Dress picks for this terrain, or null where nothing matches (garrison). */
    fun bestCamoFor(terrain: Terrain): CamoPattern? = CamoPattern.SELECTABLE.firstOrNull { terrain in it.suitedTerrains }

    /**
     * The field camo this soldier wears on this mission: their own Tailor pick, then (with Smart
     * Duty-Dress on) the pattern matching the terrain, then the squad standard, then their default.
     */
    fun missionCamo(op: Operator, mission: MissionTemplate, squads: List<TacticalSquad>, smartWardrobeEnabled: Boolean): CamoPattern =
        op.wardrobe.field.camoPattern
            ?: (if (smartWardrobeEnabled) bestCamoFor(mission.theater.terrain) else null)
            ?: squads.firstOrNull { op.id in it.memberIds }?.standardCamo
            ?: op.camoPattern

    data class CamoEffect(val matched: Int, val wrong: Int, val teamSize: Int, val passDelta: Int, val survivalDelta: Int) {
        val label: String get() = when {
            passDelta > 0 -> "$matched/$teamSize matched • +$passDelta% pass, +$survivalDelta survival"
            passDelta < 0 -> "$wrong/$teamSize wrong for the ground • $passDelta% pass, $survivalDelta survival"
            else -> "$matched/$teamSize matched • no effect"
        }
    }

    /** Field missions only: most of the team in matching camo helps; most of it clearly wrong hurts. */
    fun teamCamoEffect(mission: MissionTemplate, team: List<Operator>, squads: List<TacticalSquad>, smartWardrobeEnabled: Boolean): CamoEffect {
        val ground = team.filter { !it.isAviator }
        if (mission.scope != MissionScope.FIELD_COMBAT || ground.isEmpty() || mission.theater.terrain == Terrain.GARRISON) {
            return CamoEffect(0, 0, ground.size, 0, 0)
        }
        val fits = ground.map { missionCamo(it, mission, squads, smartWardrobeEnabled).fitFor(mission.theater.terrain) }
        val matched = fits.count { it == CamoFit.MATCH }
        val wrong = fits.count { it == CamoFit.WRONG }
        return when {
            matched * 2 >= ground.size -> CamoEffect(matched, wrong, ground.size, CAMO_MATCH_PASS_BONUS, CAMO_MATCH_SURVIVAL_BONUS)
            wrong * 2 > ground.size -> CamoEffect(matched, wrong, ground.size, -CAMO_WRONG_PASS_PENALTY, -CAMO_WRONG_SURVIVAL_PENALTY)
            else -> CamoEffect(matched, wrong, ground.size, 0, 0)
        }
    }

    fun resolveKitAppearance(
        operator: Operator,
        kit: WardrobeKit,
        squads: List<TacticalSquad> = emptyList(),
        ownedMedalIds: Set<String> = emptySet(),
        allowFieldForHqStaff: Boolean = false
    ): Operator {
        val effectiveKit = if (kit == WardrobeKit.FIELD && operator.isAviator) {
            WardrobeKit.BRAVOS
        } else if (kit.isAvailableFor(operator) || (allowFieldForHqStaff && kit == WardrobeKit.FIELD)) {
            kit
        } else {
            WardrobeKit.ALPHAS
        }
        val settings = operator.wardrobe.forKit(effectiveKit)
        val allowFree = operator.tailorOverrides.freeTailorOverride
        val sashAllowed = allowFree || evaluateDressSashEligibility(operator, squads).eligible
        // Order for each item: the soldier's own Tailor pick, then the squad standard (if they qualify), then Auto
        val std = squadStandardFor(operator, squads, effectiveKit, ownedMedalIds)

        return when (effectiveKit) {
            WardrobeKit.FIELD -> operator.copy(
                activeWardrobeKit = WardrobeKit.FIELD,
                uniformMode = UniformMode.FIELD_TACTICAL,
                camoPattern = settings.camoPattern ?: std.camo ?: operator.camoPattern
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
                    // Item 57: Bravos follow the soldier's Alphas uniform family unless set separately
                    dressStyle = defaultDressStyleForKit(
                        WardrobeKit.BRAVOS,
                        operator.rank.tier,
                        settings.dressStyle ?: std.dressStyle ?: operator.wardrobe.alphas.dressStyle
                            ?: operator.dressStyle.takeIf { !it.isCutawayMess }
                    ),
                    headgearStyle = defaultHeadgearForKit(WardrobeKit.BRAVOS, operator, ownedMedalIds, settings.headgearStyle ?: std.headgear),
                    dressDisplayMode = bravosDisplay,
                    hasWhiteGloves = false,
                    hasDressSash = false,
                    // Shoulder cords are worn on the Bravos shirt, same selection as Alphas (resolved in resolveWornRegalia)
                )
            }
            WardrobeKit.ALPHAS -> {
                // Item 91: HQ staff wear the waist sash by default
                val rawSash = settings.hasDressSash ?: (operator.hasDressSash || operator.status == OperatorStatus.HQ_STAFF)
                operator.copy(
                    activeWardrobeKit = WardrobeKit.ALPHAS,
                    uniformMode = UniformMode.DRESS_UNIFORM,
                    dressStyle = defaultDressStyleForKit(
                        WardrobeKit.ALPHAS,
                        operator.rank.tier,
                        settings.dressStyle ?: std.dressStyle ?: operator.dressStyle.takeIf { !it.isCutawayMess }
                    ),
                    headgearStyle = defaultHeadgearForKit(
                        WardrobeKit.ALPHAS,
                        operator,
                        ownedMedalIds,
                        settings.headgearStyle ?: std.headgear
                    ),
                    dressDisplayMode = settings.dressDisplayMode ?: operator.dressDisplayMode,
                    hasWhiteGloves = settings.hasWhiteGloves ?: std.gloves ?: operator.hasWhiteGloves,
                    hasDressSash = rawSash && sashAllowed
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
                        settings.dressStyle ?: std.dressStyle ?: operator.dressStyle.takeIf { it.isCutawayMess }
                    ),
                    headgearStyle = defaultHeadgearForKit(
                        WardrobeKit.MESS,
                        operator,
                        ownedMedalIds,
                        settings.headgearStyle ?: std.headgear
                    ),
                    dressDisplayMode = messDisplay,
                    hasWhiteGloves = settings.hasWhiteGloves ?: std.gloves ?: true,
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
        hqStaffSummary: HqStaffSummary? = null,
        facilities: List<FacilityState> = emptyList()
    ): Int {
        val req = mission.schoolRequirements ?: return 90
        val stats = soldierStats(operator, citations)
        val rankBonus = operator.rank.seniorityIndex.coerceAtMost(10)
        val mosFitBonus = if (operator.specialty in req.naturalFitSpecialties) 5 else 0
        val statMargin = ((stats.totalPower - mission.recommendedPower) / 5).coerceIn(-16, 10)
        val staffBonus = (hqStaffSummary?.efficiencyBonusPct ?: 0) / 6
        // Flat +4 keeps overall pass rates where they were when Bravos gave +4; the uniform
        // itself no longer matters.
        val baselineBonus = SCHOOL_BASELINE_PASS_BONUS
        // Item 106: caps follow the real difficulty ladder, hardest last:
        // Night Qual > Pathfinder > MFF > Sapper > EIB > Ranger > Combat Diver > SF
        val maxCap = when (mission.id) {
            "msn_sf_school" -> 45
            "msn_combat_diver_school" -> 52
            "msn_ranger_school" -> 56
            "msn_eib_testing" -> 62
            "msn_sapper_school" -> 68
            "msn_mff_school" -> 70
            "msn_pathfinder_school" -> 78
            "msn_night_qual" -> 76
            AIR_ASSAULT_SCHOOL_ID -> 74
            SERE_SCHOOL_ID -> 72
            SF_AVIATION_SCHOOL_ID -> 56
            "msn_intel_school", "msn_ocs_school" -> 80
            "msn_signals_school", "msn_medical_school", "msn_engineer_school", "msn_logistics_school" -> 84
            "msn_snco_school" -> 86
            "msn_nco_school" -> 92
            else -> 94
        }
        // Training Range bonus still stops at the school's cap
        val rangeBonus = rangeSchoolBonusPct(mission, facilities)
        val airfieldBonus = if (mission.id == FLIGHT_SCHOOL_MISSION_ID) airfieldFlightSchoolBonus(facilityLevel(facilities, FacilityType.AIRFIELD)) else 0
        // Items 102/110: the SOF officer helps SF schools; the CSM helps the NCO schools
        val billetBonus = when (mission.id) {
            in SF_SCHOOL_IDS -> hqStaffSummary?.sfSchoolPassBonusPct ?: 0
            "msn_nco_school", "msn_snco_school" -> hqStaffSummary?.ncoSchoolPassBonusPct ?: 0
            else -> 0
        }
        return (req.basePassRatePct + rankBonus + mosFitBonus + statMargin + staffBonus + baselineBonus + rangeBonus + airfieldBonus + billetBonus).coerceIn(15, maxCap)
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
        smartWardrobeEnabled: Boolean = false,
        insertion: InsertionMethod = InsertionMethod.GROUND,
        scatteredDrop: Boolean = false
    ): Int {
        if (selectedOperators.isEmpty()) return 0
        if (mission.risk.isTraining) {
            return selectedOperators.map {
                schoolPassOddsPct(mission, it, citations, hqStaffSummary, facilities)
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
        val squadBonus = SquadCohesionRules.evaluateActiveBonus(selectedOperators, squads, mission.requiredSquads > 1)
        val cohesionOddsBonus = squadBonus?.successOddsBonusPct ?: 0
        val kitOddsBonus = evaluateMissionKitBonus(mission, selectedOperators, smartWardrobeEnabled).successOddsBonusPct
        val (minOdds, maxOdds) = when (mission.risk) {
            MissionRisk.TRAINING -> 15 to 94
            MissionRisk.LOW -> 25 to 90
            MissionRisk.MODERATE -> 20 to 80
            MissionRisk.HIGH -> 16 to 70
            MissionRisk.EXTREME -> 12 to 62
        }
        val warrantBonus = if (mission.scope == MissionScope.TRAINING) 0 else technicalWarrantOddsBonus(mission, selectedOperators)
        val mosBonus = mosCoverage(mission, selectedOperators).second
        val insertionDelta = insertionPassDelta(mission, insertion, scatteredDrop)
        val platoonBonus = if (platoonLeaderFor(mission, selectedOperators) != null) PLATOON_LEADER_PASS_BONUS else 0
        val theaterPerk = if (mission.scope == MissionScope.FIELD_COMBAT) campaignFieldPassBonus() else 0
        val camo = teamCamoEffect(mission, selectedOperators, squads, smartWardrobeEnabled).passDelta
        // Item 102: the SOF officer backs SF missions and SF campaign stages
        val sofBonus = if (isSfOperation(mission)) (hqStaffSummary?.sfMissionPassBonusPct ?: 0) + SoldierEvents.effects.sfRehearsalBonusPct else 0
        // Item 69: a lieutenant running their first plan costs a little
        val ltPlan = if (selectedOperators.any { it.eventBuffs.leadsNextMission }) -SoldierEvents.LT_PLAN_PASS_PENALTY else 0
        // Item 63: who sits at the table for release talks, and how hard the captors have become
        val talks = if (mission.id == PowRules.NEGOTIATION_MISSION_ID) PowRules.talksModifierPct(selectedOperators, PowRules.activeTalksFailures) else 0
        return (mission.baseSuccessRatePct + effDelta + staffBonus + cohesionOddsBonus + kitOddsBonus + warrantBonus + mosBonus + insertionDelta + platoonBonus + theaterPerk + camo + sofBonus + ltPlan + talks)
            .coerceIn(minOdds, maxOdds)
    }

    /** Keywords in a mission's focus that match each MOS, for the technical warrant officer bonus. */
    private val mosFocusKeywords: Map<OperatorSpecialty, List<String>> = mapOf(
        OperatorSpecialty.ASSAULT to listOf("Assault", "Strike", "Armor", "Firepower"),
        OperatorSpecialty.RECON to listOf("Recon", "Intel", "Stealth", "Infiltration"),
        OperatorSpecialty.SIGNALS to listOf("Signals", "Tech", "EW", "Cyber"),
        OperatorSpecialty.BREACHER to listOf("EOD", "Breach", "Engineer", "Sapper"),
        OperatorSpecialty.MEDIC to listOf("Trauma", "Medical", "Rescue"),
        OperatorSpecialty.COMMAND to listOf("Diplomatic", "Staff", "Logistics", "Coalition"),
        OperatorSpecialty.AVIATION to emptyList()
    )

    /**
     * Technical warrant officers lift their team: +1 point for the technical edge (about +2 Tech of squad
     * power) and [TECH_WARRANT_MOS_PASS_BONUS] more when the mission's focus matches their MOS. Best WO counts once.
     */
    fun technicalWarrantOddsBonus(mission: MissionTemplate, team: List<Operator>): Int =
        team.filter { it.isTechnicalWarrant }.maxOfOrNull { wo ->
            val fits = mosFocusKeywords[wo.specialty].orEmpty().any { mission.primaryFocus.contains(it, ignoreCase = true) }
            1 + if (fits) TECH_WARRANT_MOS_PASS_BONUS else 0
        } ?: 0

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
            credits = GameTiming.income(credits * crMult * legacyMult),
            supplies = GameTiming.income(supplies * supMult * legacyMult),
            intel = GameTiming.income(intel * intMult * legacyMult)
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
            RankTier.OFFICER, RankTier.WARRANT -> 15
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

    /** Field roster comes from the Barracks: 8 at Lv 1, +1 per level after. */
    fun maxFieldRosterSlots(facilities: List<FacilityState>): Int {
        val barracksLevel = facilityLevel(facilities, FacilityType.BARRACKS)
        return 8 + (barracksLevel - 1).coerceAtLeast(0)
    }

    fun maxHqStaffSlots(facilities: List<FacilityState>): Int {
        val hqLevel = facilityLevel(facilities, FacilityType.COMMAND_HQ)
        // Ladder positions; the CSM and SOF billets share a rung with the XO and S-4
        return (2 + hqLevel).coerceAtMost(HqStaffRole.entries.maxOf { it.slotOrder })
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
        return (facMult * staffMult * campaignDurationMultiplier()).coerceAtLeast(0.50)
    }

    fun gearBonusMultiplier(facilities: List<FacilityState>): Double {
        val armoryLevel = facilityLevel(facilities, FacilityType.ARMORY_WORKSHOP)
        return 1.0 + (armoryLevel - 1) * 0.05
    }

    /**
     * Supplies to dispatch [mission] with [soldierCount] soldiers. Schools charge per soldier;
     * field and diplomatic missions charge one flat fee for the team.
     */
    fun dispatchSupplyCost(mission: MissionTemplate, soldierCount: Int, multiplier: Double): Int {
        val perUnit = mission.supplyCost * multiplier
        val units = if (mission.hasClassCap) 1 else soldierCount.coerceAtLeast(1)
        return (perUnit * units).roundToInt()
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
        val rankIndex = operator.rank.seniorityIndex
        val rankBonus = rankIndex + operator.rank.tier.order * 2
        val gearMult = gearBonusMultiplier(facilities)

        val equipped = gearItems.equippedBy(operator)
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
            firepower = operator.baseFirepower + rankBonus + gearFp + medFp + experienceGain(operator.expPointsFirepower),
            stealth = operator.baseStealth + rankBonus + gearSt + medSt + experienceGain(operator.expPointsStealth),
            tech = operator.baseTech + rankBonus + gearTc + medTc + experienceGain(operator.expPointsTech),
            resilience = operator.baseResilience + rankBonus + gearRs + medRs + experienceGain(operator.expPointsResilience)
        )
    }

    fun operatorLoadoutWeight(operator: Operator, gearItems: List<GearItem>): Double =
        gearItems.equippedBy(operator).sumOf { it.weightKg }

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
        val equipped = gearItems.equippedBy(operator)
        val gearSurvival = equipped.sumOf { it.survivalBonusPct }
        val reconBonus = (facilityLevel(facilities, FacilityType.RECON_ARRAY) - 1).coerceAtLeast(0) * 2
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
        squads: List<TacticalSquad> = emptyList(),
        smartWardrobeEnabled: Boolean = false
    ): Int {
        if (selectedOperators.isEmpty()) return 0
        val baseAvg = selectedOperators.map {
            operatorSurvivalPct(mission, it, gearItems, citations, facilities, hqStaffCount, hqStaffSummary)
        }.average()
        if (mission.risk.isTraining) return baseAvg.roundToInt()
        val cohesionSurv = SquadCohesionRules.evaluateActiveBonus(selectedOperators, squads, mission.requiredSquads > 1)?.survivalBonusPct ?: 0
        val platoonSurv = if (platoonLeaderFor(mission, selectedOperators) != null) PLATOON_LEADER_SURVIVAL_BONUS else 0
        val theaterPerk = if (mission.scope == MissionScope.FIELD_COMBAT) campaignSurvivalBonus() else 0
        val camo = teamCamoEffect(mission, selectedOperators, squads, smartWardrobeEnabled).survivalDelta
        return (baseAvg + cohesionSurv + platoonSurv + theaterPerk + camo).roundToInt().coerceIn(42, 98)
    }

    fun isRepeatableAward(medal: MedalDefinition): Boolean =
        (medal.category == AwardCategory.MEDAL || medal.category == AwardCategory.RIBBON) &&
            medal.id != "medal_sovereign_star" &&
            medal.id !in ONE_TIME_RIBBON_IDS

    /**
     * Item 43: every non-repeatable award, badge, tab and cord this soldier's record already earns, plus the
     * National Defense Service ribbon. Runs until nothing new qualifies, so awards that need another award
     * first (Master Parachutist after jump wings) come through. Used for starting soldiers and cadre transfers.
     */
    fun earnedStartingAwards(op: Operator, alreadyHeld: Set<String> = emptySet()): List<String> {
        val owned = alreadyHeld.toMutableSet()
        val granted = mutableListOf<String>()
        if ("medal_national_defense" !in owned) { owned += "medal_national_defense"; granted += "medal_national_defense" }
        var changed = true
        while (changed) {
            changed = false
            for (medal in GameCatalog.medals) {
                if (medal.id in owned || isRepeatableAward(medal)) continue
                if (evaluateAwardEligibility(medal, op, owned).eligible) {
                    owned += medal.id
                    granted += medal.id
                    changed = true
                }
            }
        }
        return granted
    }

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
        // Difficulty compares the team's average power per soldier, so extra soldiers don't trivialize a mission;
        // they help through MOS coverage, squad bonuses and survival instead
        val averagePower = selectedOperators.map {
            operatorStats(it, gearItems, citations, facilities).totalPower
        }.average()
        val staffBonus = ((hqStaffSummary?.efficiencyBonusPct ?: (hqStaffCount.coerceAtLeast(0) * 4)) / 2).coerceAtMost(14)
        val cohesionEff = SquadCohesionRules.evaluateActiveBonus(selectedOperators, squads, mission.requiredSquads > 1)?.efficiencyBonusPct ?: 0
        val inspectionBonus = if (inspectionBuffActive) 8 else 0
        val kitEffBonus = evaluateMissionKitBonus(mission, selectedOperators, smartWardrobeEnabled).efficiencyBonusPct
        val baseRatio = (averagePower / mission.recommendedPower.coerceAtLeast(1)) * 100.0
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
        allCitations: List<AwardedMedal> = emptyList(),
        heroismCited: Boolean = false
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
                // Earned only through a heroism roll on a mission, never by meeting a checklist
                if (!heroismCited) {
                    missing += "Needs a heroism citation from a combat op"
                }
            }
            // Item 89: valor awards come only from a valor roll on a High or Extreme mission
            "medal_distinguished_cross", "medal_silver_vanguard", "medal_bronze_valor" -> {
                if (!heroismCited) {
                    missing += "Needs a valor citation from a High or Extreme combat op"
                }
            }
            in AwardRules.NEW_AWARD_IDS -> {
                missing += AwardRules.eligibilityGaps(
                    medal.id, operator, heroismCited,
                    heldCount = allCitations.count { it.medalId == medal.id && it.operatorId == operator.id }
                        .coerceAtLeast(if (medal.id in ownedMedalIds) 1 else 0)
                )
            }
            "medal_defense_superior" -> {
                val staffMet = isHqStaff && operator.staffOpsSupported >= 5
                val diploMet = distinctDiploMissions >= 2
                if (!staffMet && !diploMet) {
                    missing += "HQ Staff (5+ Ops Supported) or 2+ Diplomatic Ops"
                }
            }
            "medal_purple_shield" -> {
                if (operator.timesWounded <= 0 && !isFallen) {
                    missing += "Must be Wounded/Medevaced in combat (or Fallen)"
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
            PowRules.POW_MEDAL_ID -> {
                if (operator.timesCaptured < 1 || operator.status == OperatorStatus.CAPTURED) {
                    missing += "Held as a prisoner of war and returned home"
                }
            }
            "medal_sere" -> {
                if (SERE_SCHOOL_ID !in operator.completedMissionIds) {
                    missing += "Graduate SERE school"
                }
            }
            "medal_nco_dev" -> {
                if ("msn_nco_school" !in operator.completedMissionIds && "msn_snco_school" !in operator.completedMissionIds) {
                    missing += "Graduate the NCO Academy or Senior Leader Course"
                }
            }
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
                // Item 89: the valor cord follows the soldier's valor awards
                if (ownedMedalIds.none { it in AwardRules.VALOR_AWARD_IDS }) {
                    missing += "Hold a valor award"
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
            "medal_national_defense" -> Unit // every soldier, on joining
            MFF_BADGE_ID -> {
                if (MFF_SCHOOL_ID !in operator.completedMissionIds) missing += "Graduate Military Free Fall School"
            }
            COMBAT_DIVER_BADGE_ID -> {
                if (COMBAT_DIVER_SCHOOL_ID !in operator.completedMissionIds) {
                    missing += "Graduate Combat Diver Qualification"
                }
            }
            AVIATOR_WINGS_ID -> {
                if (FLIGHT_SCHOOL_MISSION_ID !in operator.completedMissionIds) {
                    missing += "Graduate Flight School"
                }
                if (SENIOR_AVIATOR_WINGS_ID in ownedMedalIds || MASTER_AVIATOR_WINGS_ID in ownedMedalIds) {
                    missing += "Already wears higher aviator wings"
                }
            }
            SENIOR_AVIATOR_WINGS_ID -> {
                if (AVIATOR_WINGS_ID !in ownedMedalIds) missing += "Hold the Aviator Badge first"
                if (MASTER_AVIATOR_WINGS_ID in ownedMedalIds) missing += "Already wears Master wings"
                if (operator.rank.warrantGrade < 3) missing += "Reach CW3 (currently ${operator.rank.code})"
                if (operator.flightSkill < SENIOR_WINGS_FLIGHT_SKILL) {
                    missing += "Flight skill ${operator.flightSkill}/$SENIOR_WINGS_FLIGHT_SKILL"
                }
            }
            MASTER_AVIATOR_WINGS_ID -> {
                if (SENIOR_AVIATOR_WINGS_ID !in ownedMedalIds) missing += "Hold the Senior Aviator Badge first"
                if (operator.rank.warrantGrade < 4) missing += "Reach CW4 (currently ${operator.rank.code})"
                if (operator.flightSkill < MASTER_WINGS_FLIGHT_SKILL) {
                    missing += "Flight skill ${operator.flightSkill}/$MASTER_WINGS_FLIGHT_SKILL"
                }
            }
            "cord_aviation" -> {
                if (FLIGHT_SCHOOL_MISSION_ID !in operator.completedMissionIds) {
                    missing += "Graduate Flight School"
                }
            }
            AIR_MEDAL_ID -> {
                val held = allCitations.count { it.operatorId == operator.id && it.medalId == AIR_MEDAL_ID }
                val needed = SORTIES_PER_AIR_MEDAL * (held + 1)
                if (operator.combatSortiesFlown < needed) {
                    missing += "Fly ${needed} combat missions as pilot (${operator.combatSortiesFlown}/$needed)"
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
                if (AIR_ASSAULT_SCHOOL_ID !in operator.completedMissionIds) {
                    missing += "Graduate Army Air Assault School"
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
        allCampaignCitations: List<AwardedMedal> = emptyList(),
        ownedMedalIds: Set<String>? = null
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
                OperatorOutcomeType.CAPTURED -> OperatorStatus.CAPTURED
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
                if (id in existingCitationMedalIds) {
                    // Repeatable awards come back when a mission earns them again; one-time awards and
                    // anything already waiting in Pending Awards are skipped
                    val owned = ownedMedalIds ?: return@firstOrNull false
                    if (id !in owned || perSoldierCapForAward(id) == 1) return@firstOrNull false
                }
                val def = GameCatalog.medals.find { it.id == id } ?: return@firstOrNull false
                evaluateAwardEligibility(def, effectiveOp, existingCitationMedalIds, allCampaignCitations).eligible
            }
            if (primary != null) return primary

            if (includeGeneralFallback) {
                val generalFallback = listOf("medal_meritorious")
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
            // Item 63: the POW Medal waits until they come home
            OperatorOutcomeType.CAPTURED -> null to "Captured by the enemy during ${mission.title}."
            OperatorOutcomeType.FALLEN -> {
                val medalId = firstEligibleUnowned(
                    listOf("medal_purple_shield"), includeGeneralFallback = false
                )
                medalId to "Gave the last full measure of devotion holding the objective during ${mission.title}."
            }
            OperatorOutcomeType.WOUNDED -> {
                val candidates = buildList {
                    add("medal_purple_shield")
                    if (isCombatMission) add(combatBadgeIdFor(operator))
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
                                    "medal_night_owl"
                                )
                                val medalId = firstEligibleUnowned(candidates, includeGeneralFallback = false)
                                medalId to "Graduated ${mission.title} static-line jumps and night drop zones. Maroon Beret authorized."
                            }
                            SERE_SCHOOL_ID -> {
                                val medalId = firstEligibleUnowned(listOf("medal_sere"), includeGeneralFallback = false)
                                medalId to "Graduated ${mission.title}: survived, evaded the hunter force and held up under interrogation."
                            }
                            SF_AVIATION_SCHOOL_ID -> {
                                null to "Graduated ${mission.title}. Cleared to fly Special Forces missions."
                            }
                            AIR_ASSAULT_SCHOOL_ID -> {
                                val medalId = firstEligibleUnowned(listOf("insig_air_assault"), includeGeneralFallback = false)
                                medalId to "Graduated ${mission.title}: sling loads, rappelling and the fast rope."
                            }
                            "msn_ranger_school" -> {
                                // Ranger awards the Ranger Tab only, for every MOS
                                val medalId = firstEligibleUnowned(listOf("insig_ranger_tab"), includeGeneralFallback = false)
                                medalId to "Graduated ${mission.title} in small-unit tactics, mountaineering & patrol leadership — Tan Beret authorized."
                            }
                            MFF_SCHOOL_ID -> {
                                val medalId = firstEligibleUnowned(listOf(MFF_BADGE_ID), includeGeneralFallback = false)
                                medalId to "Graduated ${mission.title}: night oxygen jumps with full combat equipment."
                            }
                            COMBAT_DIVER_SCHOOL_ID -> {
                                val medalId = firstEligibleUnowned(listOf(COMBAT_DIVER_BADGE_ID), includeGeneralFallback = false)
                                medalId to "Graduated ${mission.title}: closed-circuit dives and surf passage complete."
                            }
                            FLIGHT_SCHOOL_MISSION_ID -> {
                                val medalId = firstEligibleUnowned(listOf(AVIATOR_WINGS_ID, "cord_aviation"), includeGeneralFallback = false)
                                medalId to "Graduated ${mission.title}: primary, instrument and combat skills flights complete."
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
                                // OCS commissions officers; the staff crest comes from HQ staff work
                                val candidates = emptyList<String>()
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
                            val priorityCandidates = listOf("medal_defense_superior")
                            firstEligibleUnowned(priorityCandidates, includeGeneralFallback = false)
                                ?: if (rollSeedPct < 35) {
                                    firstEligibleUnowned(
                                        listOf("insig_staff_crest", "medal_meritorious"),
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
                                    listOf("medal_expeditionary", "medal_nco_dev"),
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
                            val priorityCandidates = listOf("medal_defense_superior")
                            firstEligibleUnowned(priorityCandidates, includeGeneralFallback = false)
                                ?: if (rollSeedPct < 40) {
                                    firstEligibleUnowned(
                                        listOf("insig_staff_crest", "medal_meritorious"),
                                        includeGeneralFallback = false
                                    )
                                } else null
                        } else {
                            val milestoneCandidates = buildList {
                                if (mission.isNightOp && hasNvg) add("medal_night_owl")
                                add("medal_expeditionary")
                            }
                            firstEligibleUnowned(milestoneCandidates, includeGeneralFallback = false)
                                ?: if (rollSeedPct < 30) {
                                    firstEligibleUnowned(
                                        listOf("medal_meritorious"),
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
                        // Valor comes from the valor roll (item 89); this is the routine citation
                        val milestoneCandidates = listOf("insig_master_para")
                        val medalId = firstEligibleUnowned(milestoneCandidates, includeGeneralFallback = false)
                            ?: if (rollSeedPct < 35) {
                                firstEligibleUnowned(
                                    listOf("medal_meritorious"),
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
                        val medalId = if (rollSeedPct < 45) {
                                firstEligibleUnowned(
                                    listOf("medal_meritorious"),
                                    includeGeneralFallback = false
                                )
                            } else null
                        val deed = if (medalId == null) {
                            "Survived extreme-hazard strike on ${mission.title} (+XP & Op count)."
                        } else {
                            "Served with distinction under extreme hazard on ${mission.title}."
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
                HqStaffRole.CSM_COMMAND_SERGEANT_MAJOR -> {
                    add("medal_meritorious")
                }
                HqStaffRole.SOF_SPECIAL_OPERATIONS -> {
                    add("medal_meritorious")
                    if (effectiveStaffOp.rank.tier.isOfficer) add("insig_staff_crest")
                }
                HqStaffRole.S1_PERSONNEL_HONORS, HqStaffRole.S3_OPS_DIRECTOR -> {
                    add("medal_meritorious")
                    if (effectiveStaffOp.rank.tier.isOfficer) add("insig_staff_crest")
                }
                HqStaffRole.AIR_OPS_OFFICER, HqStaffRole.CCWO_COMMAND_CHIEF_WARRANT -> {
                    add("medal_meritorious")
                    add("insig_staff_crest")
                }
                null -> {
                    if (effectiveStaffOp.rank.tier.isOfficer) {
                        add("insig_staff_crest")
                        add("medal_defense_superior")
                    }
                }
            }
            if (effectiveStaffOp.rank.tier.order >= RankTier.NCO.order) {
                add("medal_nco_dev")
            }
            add("medal_meritorious")
            add("insig_logistics_crest")
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

    /**
     * The rank code to flag on the debrief as "ready for promotion", or null.
     * Uses the same check as the Promotion Board, so only soldiers who meet every merit check are flagged.
     */
    fun promotionBoardReadyCode(
        candidate: Operator,
        allOperators: List<Operator>,
        citations: List<AwardedMedal>,
        gearItems: List<GearItem>,
        facilities: List<FacilityState>
    ): String? {
        if (candidate.status == OperatorStatus.FALLEN || candidate.status.isOutOfActiveService) return null
        val roster = allOperators.map { if (it.id == candidate.id) candidate else it }
        val board = evaluatePromotionBoard(candidate, roster, citations, gearItems, facilities)
        return board.nextRank?.takeIf { board.isCrossTierGate && board.allMeritsMet }?.code
    }

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
