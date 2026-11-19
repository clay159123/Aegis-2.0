package com.example.domain

import com.example.domain.model.*

/**
 * Enforces authentic uniform placement rules for shoulder cords and the three
 * chest badge positions, plus MOS-driven display priority when slots are set
 * to Auto or sorted in Tailor dropdowns.
 */
object UniformRegaliaRules {

    /**
     * Left-shoulder Auto order per MOS. SF Emerald always leads once earned. Assault (11B) wears
     * Infantry Blue next; every other MOS wears its own branch cord next and Infantry Blue last,
     * because every soldier earns Infantry Blue at Marksmanship & Tactical Basics.
     * Command (no billet, S-1, CO, XO, Deputy): highest command-related cord by precedence.
     */
    private val preferredCordOrderBySpecialty: Map<OperatorSpecialty, List<String>> = mapOf(
        OperatorSpecialty.ASSAULT to listOf("cord_sf_emerald", "cord_infantry_blue", "cord_airborne_crimson", "cord_engineer_scarlet", "cord_intel_teal", "cord_medical_maroon", "cord_signals_orange", "cord_logistics_buff"),
        OperatorSpecialty.RECON to listOf("cord_sf_emerald", "cord_airborne_crimson", "cord_intel_teal", "cord_engineer_scarlet", "cord_signals_orange", "cord_medical_maroon", "cord_logistics_buff", "cord_infantry_blue"),
        OperatorSpecialty.SIGNALS to listOf("cord_sf_emerald", "cord_signals_orange", "cord_intel_teal", "cord_airborne_crimson", "cord_logistics_buff", "cord_engineer_scarlet", "cord_medical_maroon", "cord_infantry_blue"),
        OperatorSpecialty.BREACHER to listOf("cord_sf_emerald", "cord_engineer_scarlet", "cord_airborne_crimson", "cord_intel_teal", "cord_logistics_buff", "cord_signals_orange", "cord_medical_maroon", "cord_infantry_blue"),
        OperatorSpecialty.MEDIC to listOf("cord_sf_emerald", "cord_medical_maroon", "cord_airborne_crimson", "cord_logistics_buff", "cord_intel_teal", "cord_engineer_scarlet", "cord_signals_orange", "cord_infantry_blue"),
        OperatorSpecialty.COMMAND to listOf("cord_sf_emerald", "cord_intel_teal", "cord_logistics_buff", "cord_infantry_blue", "cord_airborne_crimson", "cord_engineer_scarlet", "cord_signals_orange", "cord_medical_maroon"),
        OperatorSpecialty.AVIATION to listOf("cord_sf_emerald", "cord_aviation", "cord_airborne_crimson", "cord_intel_teal", "cord_signals_orange", "cord_engineer_scarlet", "cord_logistics_buff", "cord_medical_maroon", "cord_infantry_blue")
    )

    /** The cord each HQ staff billet puts first for a Command-MOS soldier (after SF Emerald). */
    private val billetCordForCommand: Map<HqStaffRole, String> = mapOf(
        HqStaffRole.S2_INTEL_OVERWATCH to "cord_intel_teal",
        HqStaffRole.S3_OPS_DIRECTOR to "cord_infantry_blue",
        HqStaffRole.S4_LOGISTICS_MEDEVAC to "cord_logistics_buff"
    )

    /**
     * Left-shoulder Auto order for this soldier. Command-MOS soldiers in an S-2, S-3 or S-4 billet
     * put that billet's cord first after SF Emerald; everyone else follows their MOS order.
     */
    fun leftCordAutoOrder(operator: Operator): List<String> {
        val base = preferredCordOrderBySpecialty.getValue(operator.specialty)
        if (operator.specialty != OperatorSpecialty.COMMAND || operator.status != OperatorStatus.HQ_STAFF) return base
        val billetCord = operator.hqRole?.let { billetCordForCommand[it] } ?: return base
        return listOf("cord_sf_emerald", billetCord) + base.filter { it != "cord_sf_emerald" && it != billetCord }
    }

    const val CORD_HQ_AIGUILLETTE = "cord_general_aiguillette"
    const val CORD_VALOR = "cord_valor_fourragere"

    /** Right shoulder Auto order: HQ staff wear the gold aiguillette first; field soldiers wear only the valor cord. */
    fun rightCordAutoOrder(isHqStaff: Boolean): List<String> =
        if (isHqStaff) listOf(CORD_HQ_AIGUILLETTE, CORD_VALOR) else listOf(CORD_VALOR)

    /**
     * Auto cord for one shoulder from the soldier's owned cords.
     * Left: highest MOS-preferred branch cord. Right: see [rightCordAutoOrder].
     */
    fun autoCordForShoulder(
        operator: Operator,
        ownedMedals: List<MedalDefinition>,
        isRightShoulder: Boolean
    ): MedalDefinition? {
        val owned = ownedMedals.filter { isCordAllowedOnShoulder(it, isRightShoulder) }
        return if (isRightShoulder) {
            val byId = owned.associateBy { it.id }
            rightCordAutoOrder(operator.status == OperatorStatus.HQ_STAFF).firstNotNullOfOrNull { byId[it] }
        } else {
            val order = leftCordAutoOrder(operator)
            owned.minWithOrNull(
                compareBy<MedalDefinition> { order.indexOf(it.id).let { idx -> if (idx < 0) Int.MAX_VALUE else idx } }
                    .thenBy { it.precedenceOrder }
            )
        }
    }

    private val preferredBadgesBySpecialtyAndSlot: Map<OperatorSpecialty, Map<ChestBadgeSlot, List<String>>> = mapOf(
        OperatorSpecialty.ASSAULT to mapOf(
            ChestBadgeSlot.ABOVE_RACK to listOf(
                "insig_mff_wings",
                "insig_combat_diver",
                "insig_master_para",
                "insig_air_assault",
                "insig_parachutist",
                "insig_eod_badge",
                "insig_pathfinder"
            ),
            ChestBadgeSlot.LEFT_POCKET to listOf(
                "insig_mff_wings",
                "insig_combat_diver",
                "insig_master_para",
                "insig_air_assault",
                "insig_parachutist",
                "insig_pathfinder",
                "insig_eod_badge",
                "insig_intel_crest",
                "insig_cyber_crest"
            ),
            ChestBadgeSlot.RIGHT_POCKET to listOf(
                "insig_staff_crest",
                "insig_logistics_crest",
                "insig_intel_crest",
                "insig_cyber_crest"
            )
        ),
        OperatorSpecialty.MEDIC to mapOf(
            ChestBadgeSlot.ABOVE_RACK to listOf(
                "insig_air_assault",
                "insig_mff_wings",
                "insig_combat_diver",
                "insig_master_para",
                "insig_parachutist",
                "insig_pathfinder",
                "insig_eod_badge"
            ),
            ChestBadgeSlot.LEFT_POCKET to listOf(
                "insig_air_assault",
                "insig_mff_wings",
                "insig_combat_diver",
                "insig_master_para",
                "insig_parachutist",
                "insig_pathfinder",
                "insig_intel_crest",
                "insig_eod_badge",
                "insig_cyber_crest"
            ),
            ChestBadgeSlot.RIGHT_POCKET to listOf(
                "insig_staff_crest",
                "insig_logistics_crest",
                "insig_intel_crest",
                "insig_cyber_crest"
            )
        ),
        OperatorSpecialty.BREACHER to mapOf(
            ChestBadgeSlot.ABOVE_RACK to listOf(
                "insig_eod_badge",
                "insig_mff_wings",
                "insig_combat_diver",
                "insig_master_para",
                "insig_air_assault",
                "insig_parachutist",
                "insig_pathfinder"
            ),
            ChestBadgeSlot.LEFT_POCKET to listOf(
                "insig_eod_badge",
                "insig_pathfinder",
                "insig_air_assault",
                "insig_mff_wings",
                "insig_combat_diver",
                "insig_master_para",
                "insig_parachutist",
                "insig_cyber_crest",
                "insig_intel_crest"
            ),
            ChestBadgeSlot.RIGHT_POCKET to listOf(
                "insig_logistics_crest",
                "insig_staff_crest",
                "insig_cyber_crest",
                "insig_intel_crest"
            )
        ),
        OperatorSpecialty.RECON to mapOf(
            ChestBadgeSlot.ABOVE_RACK to listOf(
                "insig_mff_wings",
                "insig_combat_diver",
                "insig_master_para",
                "insig_pathfinder",
                "insig_parachutist",
                "insig_air_assault",
                "insig_eod_badge"
            ),
            ChestBadgeSlot.LEFT_POCKET to listOf(
                "insig_pathfinder",
                "insig_intel_crest",
                "insig_mff_wings",
                "insig_combat_diver",
                "insig_master_para",
                "insig_parachutist",
                "insig_air_assault",
                "insig_cyber_crest",
                "insig_eod_badge"
            ),
            ChestBadgeSlot.RIGHT_POCKET to listOf(
                "insig_intel_crest",
                "insig_staff_crest",
                "insig_cyber_crest",
                "insig_logistics_crest"
            )
        ),
        OperatorSpecialty.SIGNALS to mapOf(
            ChestBadgeSlot.ABOVE_RACK to listOf(
                "insig_pathfinder",
                "insig_eod_badge",
                "insig_mff_wings",
                "insig_combat_diver",
                "insig_master_para",
                "insig_air_assault",
                "insig_parachutist"
            ),
            ChestBadgeSlot.LEFT_POCKET to listOf(
                "insig_cyber_crest",
                "insig_intel_crest",
                "insig_pathfinder",
                "insig_eod_badge",
                "insig_air_assault",
                "insig_mff_wings",
                "insig_combat_diver",
                "insig_master_para",
                "insig_parachutist"
            ),
            ChestBadgeSlot.RIGHT_POCKET to listOf(
                "insig_cyber_crest",
                "insig_intel_crest",
                "insig_staff_crest",
                "insig_logistics_crest"
            )
        ),
        OperatorSpecialty.COMMAND to mapOf(
            ChestBadgeSlot.ABOVE_RACK to listOf(
                "insig_mff_wings",
                "insig_combat_diver",
                "insig_master_para",
                "insig_air_assault",
                "insig_eod_badge",
                "insig_pathfinder",
                "insig_parachutist"
            ),
            ChestBadgeSlot.LEFT_POCKET to listOf(
                "insig_intel_crest",
                "insig_pathfinder",
                "insig_cyber_crest",
                "insig_mff_wings",
                "insig_combat_diver",
                "insig_master_para",
                "insig_air_assault",
                "insig_parachutist",
                "insig_eod_badge"
            ),
            ChestBadgeSlot.RIGHT_POCKET to listOf(
                "insig_staff_crest",
                "insig_logistics_crest",
                "insig_intel_crest",
                "insig_cyber_crest"
            )
        ),
        OperatorSpecialty.AVIATION to mapOf(
            ChestBadgeSlot.ABOVE_RACK to listOf(
                "insig_master_aviator_wings",
                "insig_senior_aviator_wings",
                "insig_aviator_wings",
                "insig_mff_wings",
                "insig_combat_diver",
                "insig_master_para",
                "insig_air_assault",
                "insig_parachutist"
            ),
            ChestBadgeSlot.LEFT_POCKET to listOf(
                "insig_master_aviator_wings",
                "insig_senior_aviator_wings",
                "insig_aviator_wings",
                "insig_air_assault",
                "insig_parachutist",
                "insig_pathfinder"
            ),
            ChestBadgeSlot.RIGHT_POCKET to listOf(
                "insig_staff_crest",
                "insig_intel_crest",
                "insig_cyber_crest",
                "insig_logistics_crest"
            )
        )
    )

    /**
     * Real-world tab order, top to bottom, for every MOS: Special Forces, Ranger, Sapper,
     * then Airborne. The stack always draws in this order.
     */
    val TAB_PRECEDENCE: List<String> = listOf(
        "insig_sf_tab",
        "insig_ranger_tab",
        "insig_sapper_tab",
        "insig_airborne_tab"
    )

    fun isCordAllowedOnShoulder(medal: MedalDefinition, isRightShoulder: Boolean): Boolean =
        medal.category == AwardCategory.SHOULDER_CORD && medal.isRightShoulder == isRightShoulder

    fun mosCordPriority(specialty: OperatorSpecialty, medalId: String): Int {
        val leftIdx = preferredCordOrderBySpecialty[specialty]?.indexOf(medalId) ?: -1
        if (leftIdx >= 0) return leftIdx
        val rightList = listOf(CORD_VALOR, CORD_HQ_AIGUILLETTE)
        val rightIdx = rightList.indexOf(medalId)
        return if (rightIdx >= 0) rightIdx else 999
    }

    fun isPrimaryMosCord(specialty: OperatorSpecialty, medalId: String): Boolean {
        // SF Emerald leads every list; the MOS signature cord is the first one after it
        val leftTop = preferredCordOrderBySpecialty[specialty]?.firstOrNull { it != "cord_sf_emerald" }
        val rightTop = CORD_VALOR
        return medalId == leftTop || medalId == rightTop
    }

    fun cordsForShoulder(
        medals: List<MedalDefinition>,
        isRightShoulder: Boolean,
        specialty: OperatorSpecialty
    ): List<MedalDefinition> = medals
        .filter { isCordAllowedOnShoulder(it, isRightShoulder) }
        .sortedWith(
            compareBy<MedalDefinition> { mosCordPriority(specialty, it.id) }
                .thenBy { it.precedenceOrder }
        )

    fun mosBadgePriority(
        specialty: OperatorSpecialty,
        medalId: String,
        slot: ChestBadgeSlot
    ): Int {
        val idx = preferredBadgesBySpecialtyAndSlot[specialty]?.get(slot)?.indexOf(medalId) ?: -1
        return if (idx >= 0) idx else 999
    }

    fun primaryBadgeIdsForSlot(specialty: OperatorSpecialty, slot: ChestBadgeSlot): Set<String> =
        preferredBadgesBySpecialtyAndSlot[specialty]?.get(slot)?.firstOrNull()?.let { setOf(it) } ?: emptySet()

    fun chestBadgesForSlot(
        medals: List<MedalDefinition>,
        slot: ChestBadgeSlot,
        specialty: OperatorSpecialty
    ): List<MedalDefinition> = medals
        .filter { it.isAllowedInChestSlot(slot) }
        .sortedWith(
            compareBy<MedalDefinition> { mosBadgePriority(specialty, it.id, slot) }
                .thenBy { it.precedenceOrder }
        )

    fun tabPrecedence(medalId: String): Int {
        val idx = TAB_PRECEDENCE.indexOf(medalId)
        return if (idx >= 0) idx else 999
    }

    /** Shoulder tabs from [medals], in [TAB_PRECEDENCE] order. */
    fun tabsInPrecedenceOrder(medals: List<MedalDefinition>): List<MedalDefinition> = medals
        .filter { it.category == AwardCategory.SCHOOL_INSIGNIA && it.insigniaShape == InsigniaShape.TAB }
        .sortedWith(compareBy<MedalDefinition> { tabPrecedence(it.id) }.thenBy { it.precedenceOrder })

    /**
     * Resolves the 3 distinct chest badge slots (ABOVE_RACK, LEFT_POCKET, RIGHT_POCKET):
     * 1. Explicit legal overrides claim their badge first so AUTO slots never duplicate them.
     * 2. Remaining AUTO slots (or invalid legacy overrides) select the top unused legal badge
     *    ranked by MOS priority first, then catalog precedence.
     */
    /**
     * Combat and expert badges, best first, for this soldier's MOS. These always take
     * Pin Slot 1 on Auto, ahead of every skill badge:
     * Infantry and SF-qualified: CIB then EIB; Medics: CMB then EFMB; everyone else: CAB.
     */
    fun combatExpertBadgeOrder(operator: Operator): List<String> = when {
        operator.specialty == OperatorSpecialty.MEDIC -> listOf("insig_combat_medic", "insig_cmb_medic")
        TacticalCalculator.isInfantryOrSfQualified(operator) -> listOf("insig_cib", "insig_eib_rifle")
        else -> listOf("insig_cab")
    }

    /** Every combat/expert badge id, for keeping them out of the skill-badge fallbacks. */
    val COMBAT_EXPERT_BADGE_IDS: Set<String> = setOf(
        "insig_cib", "insig_eib_rifle", "insig_cab", "insig_combat_medic", "insig_cmb_medic"
    )

    const val STAFF_CREST_ID = "insig_staff_crest"

    fun resolveChestBadgeSlots(
        operator: Operator,
        ownedBadgeMedals: List<MedalDefinition>,
        overrides: UniformTailorOverrides
    ): List<MedalDefinition?> {
        val slots = listOf(
            ChestBadgeSlot.ABOVE_RACK to overrides.pinSlot1,
            ChestBadgeSlot.LEFT_POCKET to overrides.pinSlot2,
            ChestBadgeSlot.RIGHT_POCKET to overrides.pinSlot3
        )
        val ownedById = ownedBadgeMedals.associateBy { it.id }
        val usedIds = mutableSetOf<String>()
        val result = arrayOfNulls<MedalDefinition?>(slots.size)
        val needsAuto = BooleanArray(slots.size)

        slots.forEachIndexed { idx, (slot, raw) ->
            when (raw) {
                UniformTailorOverrides.SLOT_NONE -> Unit
                UniformTailorOverrides.SLOT_AUTO -> needsAuto[idx] = true
                else -> {
                    // Slot 3 (right pocket) accepts any owned badge, so a manual pick there is always legal
                    val match = ownedById[raw]?.takeIf {
                        (overrides.freeTailorOverride || slot == ChestBadgeSlot.RIGHT_POCKET || it.isAllowedInChestSlot(slot)) &&
                            it.id !in usedIds
                    }
                    if (match != null) {
                        result[idx] = match
                        usedIds += match.id
                    } else {
                        needsAuto[idx] = true
                    }
                }
            }
        }

        slots.forEachIndexed { idx, (slot, _) ->
            if (needsAuto[idx]) {
                val pick = when (slot) {
                    // Slot 1: the soldier's best combat/expert badge, else the best skill badge
                    ChestBadgeSlot.ABOVE_RACK ->
                        combatExpertBadgeOrder(operator).firstNotNullOfOrNull { id -> ownedById[id]?.takeIf { id !in usedIds } }
                            // any other combat/expert badge held (for example after a change of MOS)
                            ?: ownedBadgeMedals.sortedBy { it.precedenceOrder }
                                .firstOrNull { it.id in COMBAT_EXPERT_BADGE_IDS && it.id !in usedIds }
                            ?: chestBadgesForSlot(ownedBadgeMedals, slot, operator.specialty)
                                .firstOrNull { it.id !in usedIds && it.id !in COMBAT_EXPERT_BADGE_IDS }
                    // Slot 2: best skill badge not already worn
                    ChestBadgeSlot.LEFT_POCKET ->
                        chestBadgesForSlot(ownedBadgeMedals, slot, operator.specialty)
                            .firstOrNull { it.id !in usedIds && it.id !in COMBAT_EXPERT_BADGE_IDS }
                    // Slot 3: an identification crest, else a catch-all for any unworn badge
                    // Item 92: the War College / General Staff crest always sits below the name tag, whatever the MOS
                    ChestBadgeSlot.RIGHT_POCKET ->
                        ownedById[STAFF_CREST_ID]?.takeIf { it.id !in usedIds }
                            ?: chestBadgesForSlot(ownedBadgeMedals, slot, operator.specialty).firstOrNull { it.id !in usedIds }
                            ?: ownedBadgeMedals.sortedBy { it.precedenceOrder }.firstOrNull { it.id !in usedIds }
                }
                if (pick != null) {
                    result[idx] = pick
                    usedIds += pick.id
                }
            }
        }

        return result.toList()
    }

    /**
     * Left shoulder (unit patch). Auto follows the soldier's situation at render time:
     * HQ staff wear High Command, aviators wear the Aviation Brigade, squad members wear their squad's division patch, and
     * anyone else wears the Unassigned patch. A Tailor Studio pick overrides Auto.
     */
    fun resolveUnitPatch(operator: Operator, squads: List<TacticalSquad>): UnitPatchStyle {
        val chosen = operator.tailorOverrides.unitPatch
        if (chosen != UnitPatchStyle.AUTO_MOS) return chosen
        if (operator.status == OperatorStatus.HQ_STAFF) return UnitPatchStyle.SUPREME_HQ
        // Item 100: aviators wear the Aviation Brigade patch
        if (operator.specialty == OperatorSpecialty.AVIATION) return UnitPatchStyle.AVIATION_BRIGADE
        return squads.firstOrNull { operator.id in it.memberIds }?.standardPatch
            ?.takeIf { it.isDivision }
            ?: UnitPatchStyle.UNASSIGNED
    }

    /** Combat patches the soldier has earned, newest campaign first. */
    fun earnedCombatPatches(operator: Operator): List<UnitPatchStyle> =
        operator.combatPatchHistory
            .sortedByDescending { it.recordedAtMillis }
            .map { it.patch }
            .distinct()

    /**
     * Right shoulder (combat patch): the squad patch worn in the soldier's most recent
     * campaign. No campaign history means no combat patch. A manual pick must be one the
     * soldier actually earned.
     */
    fun resolveCombatPatch(operator: Operator): UnitPatchStyle {
        val chosen = operator.tailorOverrides.combatPatch
        if (chosen == UnitPatchStyle.NONE) return UnitPatchStyle.NONE
        val earned = earnedCombatPatches(operator)
        if (chosen != UnitPatchStyle.AUTO_MOS && chosen in earned) return chosen
        return earned.firstOrNull() ?: UnitPatchStyle.NONE
    }

    fun resolveMarksmanshipTier(
        operator: Operator,
        equippedGear: List<GearItem>
    ): MarksmanshipBadgeTier {
        // The badge is the soldier's awarded qualification; gear never changes it
        val overrideTier = operator.tailorOverrides.marksmanshipBadge
        if (overrideTier != MarksmanshipBadgeTier.AUTO) return overrideTier
        return operator.marksmanshipQual ?: MarksmanshipBadgeTier.NONE
    }

    fun resolveOverseasBars(operator: Operator): Int {
        if (!operator.tailorOverrides.showOverseasBars) return 0
        val highest = operator.highestRiskSurvived ?: return 0
        if (highest.baseDangerPenalty < MissionRisk.LOW.baseDangerPenalty) return 0
        return ((operator.missionsCompleted + 1) / 3).coerceIn(1, 5)
    }

    /** The leader role a soldier's uniform shows: the Tailor override, else their role in their squad. */
    fun resolveLeaderRole(operator: Operator, squads: List<TacticalSquad>): LeaderRole {
        operator.tailorOverrides.leaderRoleOverride?.let { return it }
        return when {
            squads.any { it.leaderId == operator.id } -> LeaderRole.SQUAD_LEADER
            squads.any { it.teamLeaderId == operator.id } -> LeaderRole.TEAM_LEADER
            else -> LeaderRole.NONE
        }
    }

    /** True when the soldier was a member of a squad when it earned a unit citation. */
    fun hasUnitCitation(operator: Operator, squads: List<TacticalSquad>): Boolean =
        unitAwardsFor(operator, squads).isNotEmpty()

    /**
     * Item 55: each unit award type this soldier wears, with how many times it was earned, in precedence
     * order. Includes awards kept from squads they left or that disbanded.
     */
    fun unitAwardsFor(operator: Operator, squads: List<TacticalSquad>): List<Pair<UnitAwardType, Int>> {
        val earned = operator.retainedUnitAwards + squads.flatMap { squad ->
            squad.unitCitations.filter { operator.id in it.memberIds }.map { it.type }
        }
        return earned.groupingBy { it }.eachCount().toList().sortedBy { it.first.precedence }
    }

    /** Oak leaf clusters for a unit award earned [count] times. */
    fun unitAwardDevices(count: Int): AwardDeviceInfo {
        val additional = (count - 1).coerceAtLeast(0)
        return AwardDeviceInfo(count.coerceAtLeast(1), additional % 5, additional / 5, false)
    }

    fun isLeaderPinShown(operator: Operator, squads: List<TacticalSquad>): Boolean =
        resolveLeaderRole(operator, squads) != LeaderRole.NONE

    data class AwardDeviceInfo(
        val awardCount: Int,
        val bronzeOakLeaves: Int,
        val silverOakLeaves: Int,
        val hasValorV: Boolean,
        /** Item 55: campaign-style awards (Humanitarian, NATO, UN) repeat with service stars instead of oak leaves. */
        val bronzeServiceStars: Int = 0,
        val silverServiceStars: Int = 0
    ) {
        val hasAnyDevice: Boolean
            get() = bronzeOakLeaves > 0 || silverOakLeaves > 0 || hasValorV || bronzeServiceStars > 0 || silverServiceStars > 0

        val summaryLabel: String?
            get() = buildList {
                if (hasValorV) add("\"V\" Valor")
                if (silverOakLeaves > 0) add("$silverOakLeaves Silver OLC")
                if (bronzeOakLeaves > 0) add("$bronzeOakLeaves Bronze OLC")
                if (silverServiceStars > 0) add("$silverServiceStars Silver Service Star")
                if (bronzeServiceStars > 0) add("$bronzeServiceStars Bronze Service Star")
            }.joinToString(" + ").ifEmpty { null }
    }

    /** Awards that repeat with bronze service stars (5 bronze become 1 silver) instead of oak leaf clusters. */
    val SERVICE_STAR_AWARD_IDS: Set<String> = setOf("medal_humanitarian", "medal_nato", "medal_un", "medal_pow")

    fun resolveAwardDevices(
        operator: Operator,
        medalId: String,
        allCitations: List<AwardedMedal>
    ): AwardDeviceInfo {
        val matching = allCitations.filter { it.operatorId == operator.id && it.medalId == medalId }
        val count = matching.size.coerceAtLeast(1)
        val additional = (count - 1).coerceAtLeast(0)
        if (medalId in SERVICE_STAR_AWARD_IDS) {
            return AwardDeviceInfo(count, 0, 0, false, bronzeServiceStars = additional % 5, silverServiceStars = additional / 5)
        }
        val silverLeaves = additional / 5
        val bronzeLeaves = additional % 5
        val valorEligibleIds = setOf(
            "medal_bronze_valor",
            "medal_army_commendation",
            "medal_meritorious"
        )
        val hasValor = medalId in valorEligibleIds && (
            operator.highestRiskSurvived == MissionRisk.HIGH ||
                operator.highestRiskSurvived == MissionRisk.EXTREME ||
                matching.any {
                    it.citationNote.contains("valor", ignoreCase = true) ||
                        it.citationNote.contains("hero", ignoreCase = true) ||
                        it.citationNote.contains("under fire", ignoreCase = true)
                }
            )
        return AwardDeviceInfo(
            awardCount = count,
            bronzeOakLeaves = bronzeLeaves,
            silverOakLeaves = silverLeaves,
            hasValorV = hasValor
        )
    }

    fun evaluateUniformInspection(
        operators: List<Operator>,
        citations: List<AwardedMedal>,
        gearItems: List<GearItem>,
        squads: List<TacticalSquad>,
        subjectSquad: TacticalSquad? = null,
        inspectedKit: WardrobeKit? = null
    ): UniformInspectionReport {
        val activeSubjects = operators.ifEmpty { return UniformInspectionReport("None", "C", 50, 5, 60, listOf("No personnel inspected."), emptyList()) }
        var totalScore = 0
        val highlights = mutableListOf<String>()
        val findings = mutableListOf<String>()

        activeSubjects.forEach { rawOp ->
            val ownedIds = citations.filter { it.operatorId == rawOp.id }.map { it.medalId }.toSet()
            val kit = inspectedKit ?: if (rawOp.uniformMode == UniformMode.FIELD_TACTICAL) WardrobeKit.FIELD else rawOp.activeWardrobeKit
            val op = if (inspectedKit != null) {
                TacticalCalculator.resolveKitAppearance(rawOp, kit, squads, ownedIds)
            } else {
                rawOp
            }
            var opScore = 35
            val opMedals = citations.filter { it.operatorId == op.id }
                .mapNotNull { c -> com.example.data.catalog.GameCatalog.medals.find { it.id == c.medalId } }
                .distinctBy { it.id }

            when (kit) {
                WardrobeKit.ALPHAS, WardrobeKit.MESS -> {
                    opScore += 15
                    if (kit == WardrobeKit.MESS) {
                        highlights += "${op.rank.code} ${op.callsign}: Evening Mess Cutaway & Miniature Medals inspected"
                    }
                }
                WardrobeKit.BRAVOS -> {
                    opScore += 12
                    highlights += "${op.rank.code} ${op.callsign}: Daily Garrison Bravos short-sleeve turn-out verified"
                }
                WardrobeKit.FIELD -> {
                    findings += "${op.callsign}: Standing in Field ACU rather than Dress Uniform (-15 pts)"
                }
            }

            if (op.dressStyle.requiredTier == op.rank.tier || (op.rank.tier.isOfficer && op.dressStyle.requiredTier.isOfficer)) {
                opScore += 15
            } else {
                opScore += 8
                findings += "${op.callsign}: Upgrade to ${op.rank.tier.shortLabel} Coat Cut for full formal turn-out"
            }

            if (op.dressDisplayMode != DressDisplayMode.CLEAN && opMedals.isNotEmpty()) {
                opScore += 15
                highlights += "${op.rank.code} ${op.callsign}: Full honors & precedence rack displayed (${opMedals.size} awards)"
            } else if (opMedals.isEmpty()) {
                opScore += 10
            } else {
                findings += "${op.callsign}: Chest rack hidden (Clean mode)"
            }

            if (kit == WardrobeKit.BRAVOS || op.hasWhiteGloves) opScore += 5
            val sashAllowed = TacticalCalculator.evaluateDressSashEligibility(op, squads).eligible
            if (kit == WardrobeKit.BRAVOS || kit == WardrobeKit.MESS || op.hasDressSash || !sashAllowed) {
                opScore += 5
            }

            val opGear = gearItems.equippedBy(op)
            val markTier = resolveMarksmanshipTier(op, opGear)
            if (kit == WardrobeKit.MESS || markTier != MarksmanshipBadgeTier.NONE) {
                opScore += 5
            }
            if (isLeaderPinShown(op, squads)) {
                opScore += 5
                highlights += "${op.rank.code} ${op.callsign}: Designated Squad Leader Crest verified above nameplate"
            } else if (subjectSquad == null) {
                opScore += 5
            }
            if (kit == WardrobeKit.MESS) opScore += 3
            totalScore += opScore.coerceIn(0, 100)
        }

        var avgScore = (totalScore / activeSubjects.size).coerceIn(0, 100)
        if (subjectSquad != null) {
            val targetPatch = subjectSquad.standardPatch
            val alignedPatchCount = activeSubjects.count {
                resolveUnitPatch(it, listOf(subjectSquad)) == targetPatch
            }
            val alignedCamoCount = if (subjectSquad.standardCamo == null) activeSubjects.size else {
                activeSubjects.count { it.camoPattern == subjectSquad.standardCamo }
            }
            if (alignedPatchCount == activeSubjects.size && alignedCamoCount == activeSubjects.size) {
                avgScore = (avgScore + 8).coerceAtMost(100)
                highlights += "${subjectSquad.name}: 100% Squad Uniform Standard alignment (+8 Cohesion Turn-out)"
            } else {
                findings += "${subjectSquad.name}: Use 'Dress Squad in Standard' in Squad Editor to align patches/camo"
            }
        }

        val gradeLetter = when {
            avgScore >= 94 -> "S (Distinguished)"
            avgScore >= 85 -> "A+"
            avgScore >= 75 -> "A"
            avgScore >= 62 -> "B"
            else -> "C"
        }
        val basePrestige = if (subjectSquad != null) 22 else if (inspectedKit == WardrobeKit.MESS) 16 else 12
        val prestigeEarned = (basePrestige + (avgScore / 5)).coerceIn(10, 52)
        val label = subjectSquad?.name ?: "${activeSubjects.first().rank.code} ${activeSubjects.first().callsign}"

        return UniformInspectionReport(
            subjectLabel = label,
            gradeLetter = gradeLetter,
            scorePct = avgScore,
            prestigeEarned = prestigeEarned,
            buffDurationSec = 180,
            findings = findings.distinct().take(4),
            highlights = highlights.distinct().take(4)
        )
    }
}
