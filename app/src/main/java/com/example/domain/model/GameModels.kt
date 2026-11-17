package com.example.domain.model

import kotlin.math.pow
import kotlin.math.roundToInt

enum class ResourceType(val label: String, val shortCode: String) {
    CREDITS("Credits", "CR"),
    SUPPLIES("Supplies", "SUP"),
    INTEL("Intel", "INT"),
    PRESTIGE("Prestige", "PRS")
}

data class ResourceBundle(
    val credits: Double = 0.0,
    val supplies: Double = 0.0,
    val intel: Double = 0.0,
    val prestige: Double = 0.0
) {
    operator fun plus(other: ResourceBundle): ResourceBundle = ResourceBundle(
        credits = credits + other.credits,
        supplies = supplies + other.supplies,
        intel = intel + other.intel,
        prestige = prestige + other.prestige
    )
}

enum class ServiceBranch(val label: String, val shortCode: String) {
    ARMY("Army Directorate", "ARMY")
}

enum class RankTier(val label: String, val shortLabel: String, val order: Int) {
    SOLDIER("Soldier (Junior Enlisted)", "Soldier", 1),
    NCO("Non-Commissioned Officer", "NCO", 2),
    SNCO("Senior Non-Commissioned Officer", "SNCO", 3),
    OFFICER("Commissioned Officer", "Officer", 4),
    FLAG_OFFICER("General / Flag Officer", "Flag Officer", 5);

    val isEnlisted: Boolean get() = this == SOLDIER || this == NCO || this == SNCO
    val isNcoBand: Boolean get() = this == NCO || this == SNCO
    val isOfficer: Boolean get() = this == OFFICER || this == FLAG_OFFICER
}

enum class FacilityType(
    val id: String,
    val title: String,
    val subtitle: String,
    val primaryResource: ResourceType,
    val baseRatePerSec: Double,
    val baseCreditCost: Double,
    val baseSupplyCost: Double,
    val perkDescription: String
) {
    COMMAND_HQ(
        id = "command_hq",
        title = "Command HQ",
        subtitle = "Army Directorate Ops, Roster Slots & Staff Wing",
        primaryResource = ResourceType.CREDITS,
        baseRatePerSec = 2.5,
        baseCreditCost = 80.0,
        baseSupplyCost = 30.0,
        perkDescription = "+1 Roster & Staff Slot/Lv • +1 Tasking Slot at Lv.3 & Lv.6"
    ),
    LOGISTICS_DEPOT(
        id = "logistics_depot",
        title = "Quartermaster & Medical",
        subtitle = "Ordnance, Rations & Medevac Bay",
        primaryResource = ResourceType.SUPPLIES,
        baseRatePerSec = 2.0,
        baseCreditCost = 65.0,
        baseSupplyCost = 20.0,
        perkDescription = "Boosts Supply flow • Cuts Gear & Mission Supply cost"
    ),
    RECON_ARRAY(
        id = "recon_array",
        title = "Signals & Intel Desk",
        subtitle = "Orbital Overwatch & Mission Planning",
        primaryResource = ResourceType.INTEL,
        baseRatePerSec = 0.85,
        baseCreditCost = 100.0,
        baseSupplyCost = 45.0,
        perkDescription = "-4% Mission Duration/Lv • +2% Survival Odds/Lv"
    ),
    ARMORY_WORKSHOP(
        id = "armory_workshop",
        title = "Armory & Ceremonial Tailor",
        subtitle = "Weapon Tuning & Army Dress Regalia",
        primaryResource = ResourceType.CREDITS,
        baseRatePerSec = 1.4,
        baseCreditCost = 110.0,
        baseSupplyCost = 55.0,
        perkDescription = "+5% Gear Stat Bonus per Lv • Generates Credits"
    );

    fun productionPerSec(level: Int): Double = baseRatePerSec * (1.0 + (level - 1) * 0.45)

    fun upgradeCreditCost(currentLevel: Int): Double =
        (baseCreditCost * 1.48.pow(currentLevel - 1)).roundToInt().toDouble()

    fun upgradeSupplyCost(currentLevel: Int): Double =
        (baseSupplyCost * 1.42.pow(currentLevel - 1)).roundToInt().toDouble()
}

data class FacilityState(
    val type: FacilityType,
    val level: Int
)

enum class OperatorRank(
    val code: String,
    val payGrade: String,
    val title: String,
    val tier: RankTier,
    val xpRequired: Int,
    val insigniaCount: Int
) {
    // SOLDIER (E-1 to E-4)
    PVT("PVT", "E-1", "Private", RankTier.SOLDIER, 0, 0),
    PV2("PV2", "E-2", "Private Second Class", RankTier.SOLDIER, 60, 1),
    PFC("PFC", "E-3", "Private First Class", RankTier.SOLDIER, 140, 2),
    SPC("SPC", "E-4", "Specialist", RankTier.SOLDIER, 240, 3),

    // NCO (E-5 to E-6)
    SGT("SGT", "E-5", "Sergeant", RankTier.NCO, 380, 3),
    SSG("SSG", "E-6", "Staff Sergeant", RankTier.NCO, 560, 4),

    // SENIOR NCO (E-7 to E-9)
    SFC("SFC", "E-7", "Sergeant First Class", RankTier.SNCO, 800, 5),
    MSG("MSG", "E-8", "Master Sergeant", RankTier.SNCO, 1120, 6),
    SGM("SGM", "E-9", "Sergeant Major", RankTier.SNCO, 1520, 7),

    // OFFICER (O-1 to O-6)
    LT2("2LT", "O-1", "Second Lieutenant", RankTier.OFFICER, 2000, 1),
    LT1("1LT", "O-2", "First Lieutenant", RankTier.OFFICER, 2550, 2),
    CPT("CPT", "O-3", "Captain", RankTier.OFFICER, 3200, 3),
    MAJ("MAJ", "O-4", "Major", RankTier.OFFICER, 3950, 4),
    LTC("LTC", "O-5", "Lieutenant Colonel", RankTier.OFFICER, 4850, 5),
    COL("COL", "O-6", "Colonel", RankTier.OFFICER, 5900, 6),

    // FLAG OFFICER (O-7 to O-10)
    BG("BG", "O-7", "Brigadier General", RankTier.FLAG_OFFICER, 7100, 1),
    MG("MG", "O-8", "Major General", RankTier.FLAG_OFFICER, 8600, 2),
    LTG("LTG", "O-9", "Lieutenant General", RankTier.FLAG_OFFICER, 10400, 3),
    GEN("GEN", "O-10", "General", RankTier.FLAG_OFFICER, 12500, 4);

    val isOfficer: Boolean get() = tier == RankTier.OFFICER || tier == RankTier.FLAG_OFFICER
    val isFieldGradeOfficer: Boolean get() = this == MAJ || this == LTC || this == COL
    val isCareerEligibleForStaffOrTransfer: Boolean get() = ordinal >= SGT.ordinal

    fun promotionCreditCost(): Double = when (tier) {
        RankTier.SOLDIER -> 75.0
        RankTier.NCO -> 140.0
        RankTier.SNCO -> 200.0
        RankTier.OFFICER -> 260.0
        RankTier.FLAG_OFFICER -> 480.0
    }

    fun promotionPrestigeCost(): Int = when (tier) {
        RankTier.SOLDIER -> 4
        RankTier.NCO -> 8
        RankTier.SNCO -> 11
        RankTier.OFFICER -> 14
        RankTier.FLAG_OFFICER -> 24
    }

    companion object {
        fun forXp(xp: Int, maxTier: RankTier = RankTier.FLAG_OFFICER): OperatorRank {
            var current = PVT
            for (rank in entries) {
                if (rank.tier.order <= maxTier.order && xp >= rank.xpRequired) {
                    current = rank
                }
            }
            return current
        }

        fun ceilingXpForTier(tier: RankTier): Int? =
            entries.firstOrNull { it.tier.order > tier.order }?.xpRequired

        fun clampXpToAuthorizedTier(xp: Int, authorizedTier: RankTier): Int {
            val ceiling = ceilingXpForTier(authorizedTier)
            return if (ceiling != null) xp.coerceAtMost(ceiling) else xp
        }

        fun nextRank(current: OperatorRank): OperatorRank? {
            val idx = entries.indexOf(current)
            return if (idx in 0 until entries.lastIndex) entries[idx + 1] else null
        }
    }
}

enum class OperatorSpecialty(val label: String, val roleTag: String, val recruitSummary: String) {
    ASSAULT("Infantry", "11B INF", "Direct action & perimeter assault"),
    RECON("Recon & Aviation", "19D CAV", "Pathfinder scout & air-assault recon"),
    SIGNALS("Cyber & Signals", "25B SIG", "Encrypted comms, UAV telemetry & EW"),
    BREACHER("Engineer & EOD", "12B ENG", "Ordnance disposal, sapper & bridging"),
    MEDIC("Medical Corps", "68W MED", "Trauma stabilization & medevac boost"),
    COMMAND("Staff & Command", "HQ CMD", "Operations coordination & leadership")
}

enum class OperatorStatus(val label: String) {
    READY("READY"),
    DEPLOYED("DEPLOYED"),
    RECOVERING("MEDBAY"),
    HQ_STAFF("HQ STAFF"),
    RETIRED("RETIRED"),
    FALLEN("MEMORIAL");

    val isOutOfActiveService: Boolean get() = this == FALLEN || this == RETIRED
}

enum class HqStaffRole(
    val slotOrder: Int,
    val requiredHqLevel: Int,
    val shortCode: String,
    val title: String,
    val description: String,
    val minRank: OperatorRank,
    val minMissionsRequired: Int,
    val requiredSchoolIds: Set<String> = emptySet(),
    val requiredSchoolLabel: String? = null,
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
    CO_COMMANDING_OFFICER(
        slotOrder = 1,
        requiredHqLevel = 1,
        shortCode = "CO",
        title = "Commanding Officer (CO)",
        description = "Senior tactical & garrison commander directing all Directorate task forces, readiness & honors.",
        minRank = OperatorRank.CPT,
        minMissionsRequired = 4,
        creditFlowPct = 18,
        supplyFlowPct = 18,
        intelFlowPct = 18,
        survivalBonusPct = 3,
        efficiencyBonusPct = 8,
        prestigeBonusPct = 15,
        xpBonusPct = 5
    ),
    XO_CHIEF_OF_STAFF(
        slotOrder = 2,
        requiredHqLevel = 1,
        shortCode = "XO",
        title = "Executive Officer / Chief of Staff",
        description = "Directs HQ battle rhythm, cross-section synchronization & Directorate staff output.",
        minRank = OperatorRank.LT1,
        minMissionsRequired = 3,
        creditFlowPct = 12,
        supplyFlowPct = 12,
        intelFlowPct = 12,
        survivalBonusPct = 2,
        efficiencyBonusPct = 5,
        prestigeBonusPct = 10
    ),
    S1_PERSONNEL_HONORS(
        slotOrder = 3,
        requiredHqLevel = 1,
        shortCode = "S-1",
        title = "S-1 Personnel & Honors Chief",
        description = "Oversees promotions, valor citations, awards boards & NCO/Officer development.",
        minRank = OperatorRank.SGT,
        minMissionsRequired = 2,
        creditFlowPct = 6,
        supplyFlowPct = 6,
        intelFlowPct = 6,
        survivalBonusPct = 1,
        efficiencyBonusPct = 4,
        prestigeBonusPct = 20,
        xpBonusPct = 15
    ),
    S3_OPS_DIRECTOR(
        slotOrder = 4,
        requiredHqLevel = 2,
        shortCode = "S-3",
        title = "S-3 Operations Sergeant Major",
        description = "Specialized Ranger/Sapper operations chief accelerating mission planning & combat execution.",
        minRank = OperatorRank.SSG,
        minMissionsRequired = 3,
        requiredSchoolIds = setOf("msn_ranger_school", "msn_sapper_school"),
        requiredSchoolLabel = "Army Ranger or Sapper Leader Course",
        creditFlowPct = 8,
        supplyFlowPct = 6,
        intelFlowPct = 6,
        durationReductionPct = 8,
        survivalBonusPct = 2,
        efficiencyBonusPct = 8,
        xpBonusPct = 5
    ),
    S2_INTEL_OVERWATCH(
        slotOrder = 5,
        requiredHqLevel = 3,
        shortCode = "S-2",
        title = "S-2 Intelligence & Signals Chief",
        description = "Signals or Military Intelligence graduate running orbital telemetry, SIGINT & early threat warning.",
        minRank = OperatorRank.SGT,
        minMissionsRequired = 2,
        requiredSchoolIds = setOf("msn_signals_school", "msn_intel_school"),
        requiredSchoolLabel = "Cyber/Signals or Military Intel School",
        creditFlowPct = 6,
        supplyFlowPct = 4,
        intelFlowPct = 26,
        durationReductionPct = 5,
        survivalBonusPct = 4,
        efficiencyBonusPct = 4
    ),
    S4_LOGISTICS_MEDEVAC(
        slotOrder = 6,
        requiredHqLevel = 4,
        shortCode = "S-4",
        title = "S-4 Quartermaster & Medevac",
        description = "Medical or Logistics graduate cutting mission supply burn & speeding theater sustainment.",
        minRank = OperatorRank.SSG,
        minMissionsRequired = 2,
        requiredSchoolIds = setOf("msn_logistics_school", "msn_medical_school"),
        requiredSchoolLabel = "Medical or Quartermaster Logistics School",
        creditFlowPct = 6,
        supplyFlowPct = 24,
        intelFlowPct = 4,
        supplyDiscountPct = 10,
        survivalBonusPct = 3,
        efficiencyBonusPct = 3
    ),
    DEPUTY_COMMANDER(
        slotOrder = 7,
        requiredHqLevel = 5,
        shortCode = "DCG",
        title = "Deputy Directorate Commander",
        description = "Field-grade Airborne general staff advisor multiplying combat effectiveness & strategic prestige.",
        minRank = OperatorRank.MAJ,
        minMissionsRequired = 4,
        requiredSchoolIds = setOf("msn_night_qual"),
        requiredSchoolLabel = "Army Airborne & Parachutist School",
        creditFlowPct = 26,
        supplyFlowPct = 12,
        intelFlowPct = 12,
        durationReductionPct = 4,
        survivalBonusPct = 3,
        efficiencyBonusPct = 8,
        prestigeBonusPct = 15,
        xpBonusPct = 10
    );

    val requiredTier: RankTier get() = minRank.tier

    val requirementSummary: String
        get() = buildString {
            append("${minRank.code} (${minRank.payGrade})+ • ${minMissionsRequired}+ Ops")
            if (requiredSchoolLabel != null) append(" • School: $requiredSchoolLabel")
        }

    val bonusSummary: String
        get() = buildList {
            if (creditFlowPct == supplyFlowPct && supplyFlowPct == intelFlowPct && creditFlowPct > 0) {
                add("+$creditFlowPct% All Flow")
            } else {
                if (creditFlowPct > 0) add("+$creditFlowPct% CR")
                if (supplyFlowPct > 0) add("+$supplyFlowPct% SUP")
                if (intelFlowPct > 0) add("+$intelFlowPct% INT")
            }
            if (durationReductionPct > 0) add("-${durationReductionPct}% Time")
            if (supplyDiscountPct > 0) add("-${supplyDiscountPct}% Op Cost")
            if (survivalBonusPct > 0) add("+${survivalBonusPct}% Surv")
            if (efficiencyBonusPct > 0) add("+${efficiencyBonusPct}% Eff")
            if (prestigeBonusPct > 0) add("+${prestigeBonusPct}% PRS")
            if (xpBonusPct > 0) add("+${xpBonusPct}% XP")
        }.joinToString(" • ")
}

enum class UniformMode(val label: String) {
    FIELD_TACTICAL("Field ACU"),
    DRESS_UNIFORM("Dress Uniform")
}

enum class WardrobeKit(
    val label: String,
    val shortLabel: String,
    val dutyHint: String
) {
    FIELD("Field Combat", "Field", "Combat deployments (Field Operators only)"),
    BRAVOS("Service Bravos", "Bravos", "Daily on-base & Training Schools"),
    ALPHAS("Service Alphas", "Alphas", "HQ assignments, Boards & Inspections"),
    MESS("Evening Mess", "Mess", "Diplomatic missions & formal galas");

    fun isAvailableFor(operator: Operator): Boolean =
        !(this == FIELD && operator.status == OperatorStatus.HQ_STAFF)
}

enum class DutyUniformContext {
    ON_BASE,
    COMBAT_DEPLOYMENT,
    TRAINING_MISSION,
    FIELD_SCHOOL,
    DIPLOMATIC_MISSION,
    HQ_ASSIGNMENT,
    PROMOTION_BOARD,
    MEMORIAL_HONORS
}

data class WardrobeKitSettings(
    val dressStyle: DressUniformStyle? = null,
    val headgearStyle: HeadgearStyle? = null,
    val camoPattern: CamoPattern? = null,
    val dressDisplayMode: DressDisplayMode? = null,
    val hasWhiteGloves: Boolean? = null,
    val hasDressSash: Boolean? = null,
    val equippedLeftCordId: String? = null,
    val equippedRightCordId: String? = null,
    val cordCustomized: Boolean = false
)

data class OperatorWardrobe(
    val field: WardrobeKitSettings = WardrobeKitSettings(),
    val bravos: WardrobeKitSettings = WardrobeKitSettings(),
    val alphas: WardrobeKitSettings = WardrobeKitSettings(),
    val mess: WardrobeKitSettings = WardrobeKitSettings()
) {
    fun forKit(kit: WardrobeKit): WardrobeKitSettings = when (kit) {
        WardrobeKit.FIELD -> field
        WardrobeKit.BRAVOS -> bravos
        WardrobeKit.ALPHAS -> alphas
        WardrobeKit.MESS -> mess
    }

    fun withKit(kit: WardrobeKit, settings: WardrobeKitSettings): OperatorWardrobe = when (kit) {
        WardrobeKit.FIELD -> copy(field = settings)
        WardrobeKit.BRAVOS -> copy(bravos = settings)
        WardrobeKit.ALPHAS -> copy(alphas = settings)
        WardrobeKit.MESS -> copy(mess = settings)
    }
}

enum class DressDisplayMode(val label: String) {
    RIBBONS("Ribbon Rack"),
    FULL_MEDALS("Full Medals"),
    CLEAN("None Worn")
}

enum class UnitPatchStyle(
    val label: String,
    val subtitle: String,
    val primaryHex: Long,
    val accentHex: Long
) {
    /** Tailor setting only: wear the squad patch, High Command for HQ staff, or Unassigned. */
    AUTO_MOS("Auto (Squad Patch)", "Squad patch; High Command for HQ staff; Unassigned without a squad", 0xFF3A4556, 0xFFD8B24C),
    DIVISION_1("1st Aegis Division", "Cobalt and gold shield with a single star", 0xFF1D4E89, 0xFFD8B24C),
    DIVISION_2("2nd Aegis Division", "Crimson and silver shield with crossed sabers", 0xFF8A1F2B, 0xFFD9DEE6),
    DIVISION_3("3rd Aegis Division", "Black and gold shield with a lion head", 0xFF15171C, 0xFFD8B24C),
    DIVISION_4("4th Aegis Division", "Forest green and white shield with an oak leaf", 0xFF234A2E, 0xFFF2F4F2),
    DIVISION_5("5th Aegis Division", "Slate gray and orange shield with a tower", 0xFF4A5563, 0xFFE8862A),
    DIVISION_6("6th Aegis Division", "Navy and silver shield with an eagle", 0xFF13213F, 0xFFC9D1DC),
    SUPREME_HQ("Aegis High Command", "Worn by HQ staff only", 0xFF141E33, 0xFFF5D576),
    UNASSIGNED("Unassigned", "Generic Aegis patch for field soldiers without a squad", 0xFF5B6472, 0xFFB8C0CC),
    NONE("None Worn", "Plain sleeve with no unit shoulder patch", 0xFF1C222D, 0xFF4A5568);

    val isDivision: Boolean get() = this in DIVISIONS

    companion object {
        val DIVISIONS: List<UnitPatchStyle> = listOf(DIVISION_1, DIVISION_2, DIVISION_3, DIVISION_4, DIVISION_5, DIVISION_6)

        /** First division patch no other squad is using, for a newly formed squad. */
        fun firstUnusedDivision(squads: List<TacticalSquad>, excludingSquadId: String? = null): UnitPatchStyle {
            val used = squads.filter { it.id != excludingSquadId }.map { it.standardPatch }.toSet()
            return DIVISIONS.firstOrNull { it !in used } ?: DIVISION_1
        }
    }
}

/** The squad patch a soldier wore in one campaign; the newest record becomes their combat patch. */
data class CombatPatchRecord(
    val campaignId: String,
    val patch: UnitPatchStyle,
    val recordedAtMillis: Long
)

/** Replaces this campaign's record (latest mission in a campaign wins) and keeps one per campaign. */
fun List<CombatPatchRecord>.recordCampaignPatch(
    campaignId: String,
    patch: UnitPatchStyle,
    atMillis: Long
): List<CombatPatchRecord> =
    filterNot { it.campaignId == campaignId } + CombatPatchRecord(campaignId, patch, atMillis)

enum class MarksmanshipBadgeTier(
    val label: String,
    val shortLabel: String,
    val subtitle: String,
    val badgeColorHex: Long
) {
    AUTO("Auto (Weapon & FP)", "Auto Qual", "Scales automatically with FP & Rifle tier", 0xFFC8D3E0),
    NONE("None Worn", "None", "Leave left pocket clean below flap", 0xFF707B89),
    MARKSMAN("Marksman Qualification", "Marksman", "Silver Marksman Cross + Carbine Clasp", 0xFFB8C4D4),
    SHARPSHOOTER("Sharpshooter Qualification", "Sharpshooter", "Silver Sharpshooter Cross + Rifle Clasp", 0xFFD4DDE8),
    EXPERT("Expert Marksman (Wreath)", "Expert", "Wreathed Expert Cross + Rifle & Pistol Clasps", 0xFFF2D272)
}

data class UniformTailorOverrides(
    val unitPatch: UnitPatchStyle = UnitPatchStyle.AUTO_MOS,
    val combatPatch: UnitPatchStyle = UnitPatchStyle.AUTO_MOS,
    val marksmanshipBadge: MarksmanshipBadgeTier = MarksmanshipBadgeTier.AUTO,
    val neckSlot: String = SLOT_AUTO,
    val tabSlot1: String = SLOT_AUTO,
    val tabSlot2: String = SLOT_AUTO,
    val tabSlot3: String = SLOT_AUTO,
    val pinSlot1: String = SLOT_AUTO,
    val pinSlot2: String = SLOT_AUTO,
    val pinSlot3: String = SLOT_AUTO,
    val medalSlot1: String = SLOT_AUTO,
    val medalSlot2: String = SLOT_AUTO,
    val medalSlot3: String = SLOT_AUTO,
    val medalSlot4: String = SLOT_AUTO,
    val showUnitCitation: Boolean = true,
    val showServiceStripes: Boolean = true,
    val showOverseasBars: Boolean = true,
    val leaderPinOverride: Boolean? = null,
    val freeTailorOverride: Boolean = false
) {
    companion object {
        const val SLOT_AUTO = "AUTO"
        const val SLOT_NONE = "NONE"
    }
}

enum class CamoPattern(
    val label: String,
    val primaryHex: Long,
    val secondaryHex: Long,
    val accentHex: Long
) {
    WOODLAND_OLIVE("Army Scorpion OCP", 0xFF4F5D3B, 0xFF313924, 0xFF7B855B),
    NIGHT_CHARCOAL("Night Ops Slate", 0xFF262D38, 0xFF171B22, 0xFF3E4A59),
    ALPINE_FROST("Alpine Frost", 0xFF7D8B96, 0xFF49545E, 0xFFB8C4CE),
    ARID_SAND("Arid Canyon", 0xFF8C7A5B, 0xFF5D4F38, 0xFFB5A17D),
    RANGER_GREEN("Ranger Olive", 0xFF36452E, 0xFF212B1C, 0xFF566B4A)
}

enum class DressUniformStyle(
    val label: String,
    val shortLabel: String,
    val subtitle: String,
    val requirementHint: String,
    val requiredTier: RankTier,
    val tunicHex: Long,
    val lapelHex: Long,
    val trimHex: Long,
    val trouserStripeHex: Long,
    val isCutawayMess: Boolean = false
) {
    ARMY_SERVICE_GREEN(
        label = "Army Service Green (AGSU)",
        shortLabel = "AGSU Service",
        subtitle = "All-Ranks Olive Coat, Khaki Trousers & Tie",
        requirementHint = "All Ranks • Garrison Service",
        requiredTier = RankTier.SOLDIER,
        tunicHex = 0xFF3A482F,
        lapelHex = 0xFF26301E,
        trimHex = 0xFFD8B24C,
        trouserStripeHex = 0xFF26301E
    ),
    ARMY_DRESS_BLUES(
        label = "Army Service Blues (ASU)",
        shortLabel = "ASU Service",
        subtitle = "All-Ranks Navy Service Coat & Tie",
        requirementHint = "All Ranks • 1+ School or Op",
        requiredTier = RankTier.SOLDIER,
        tunicHex = 0xFF1D314F,
        lapelHex = 0xFF122036,
        trimHex = 0xFFD8B24C,
        trouserStripeHex = 0xFF4A78A8
    ),
    ENLISTED_MESS_BLUES(
        label = "Army Blue Mess (Enlisted/NCO)",
        shortLabel = "Enlisted Mess",
        subtitle = "Cutaway Navy Mess Jacket, Cummerbund & Bowtie",
        requirementHint = "All Ranks (E-1+) • Evening Mess",
        requiredTier = RankTier.SOLDIER,
        tunicHex = 0xFF192B47,
        lapelHex = 0xFF0E1A2E,
        trimHex = 0xFFE2BE5A,
        trouserStripeHex = 0xFF4A78A8,
        isCutawayMess = true
    ),
    NCO_PARADE_BLUES(
        label = "NCO Service & Parade Blues",
        shortLabel = "NCO Service",
        subtitle = "Royal-Blue NCO Service Coat, Tie & Gold Trim",
        requirementHint = "NCO Rank (E-5+)",
        requiredTier = RankTier.NCO,
        tunicHex = 0xFF213C63,
        lapelHex = 0xFF132540,
        trimHex = 0xFFE8C666,
        trouserStripeHex = 0xFFE2BE5A
    ),
    OFFICER_SERVICE_BLUES(
        label = "Officer Service Dress Blues",
        shortLabel = "Officer Service",
        subtitle = "Midnight Navy Officer Coat, Tie & Sam Browne",
        requirementHint = "Officer Rank (O-1+)",
        requiredTier = RankTier.OFFICER,
        tunicHex = 0xFF15243D,
        lapelHex = 0xFF0B1424,
        trimHex = 0xFFF0CB65,
        trouserStripeHex = 0xFFF0CB65
    ),
    OFFICER_MESS_BLUES(
        label = "Officer Evening Mess Blues",
        shortLabel = "Officer Mess",
        subtitle = "Midnight Navy Cutaway Mess Coat & Bowtie",
        requirementHint = "Officer Rank (O-1+)",
        requiredTier = RankTier.OFFICER,
        tunicHex = 0xFF121A2B,
        lapelHex = 0xFF090E18,
        trimHex = 0xFFF0CB65,
        trouserStripeHex = 0xFFF0CB65,
        isCutawayMess = true
    ),
    HONOR_GUARD_IVORY(
        label = "White Evening Mess (Honor)",
        shortLabel = "White Mess",
        subtitle = "Ceremonial Ivory Mess Jacket & Crimson Trim",
        requirementHint = "Officer+ & Valor/Supreme Honor or HQ Staff",
        requiredTier = RankTier.OFFICER,
        tunicHex = 0xFFE2DDD0,
        lapelHex = 0xFFB8B1A0,
        trimHex = 0xFFC89D32,
        trouserStripeHex = 0xFFC89D32,
        isCutawayMess = true
    ),
    FLAG_STAFF_FORMAL(
        label = "General Staff Service Ceremonial",
        shortLabel = "General Service",
        subtitle = "Flag Officer Full Coat, Bullion & Dual Oak Cuffs",
        requirementHint = "Flag Officer (O-7+)",
        requiredTier = RankTier.FLAG_OFFICER,
        tunicHex = 0xFF0E131F,
        lapelHex = 0xFF070A12,
        trimHex = 0xFFF5D576,
        trouserStripeHex = 0xFF8C2525
    ),
    FLAG_MESS_FORMAL(
        label = "General Staff Evening Mess",
        shortLabel = "General Mess",
        subtitle = "Flag Officer Bullion Cutaway Mess & Crimson Trim",
        requirementHint = "Flag Officer (O-7+)",
        requiredTier = RankTier.FLAG_OFFICER,
        tunicHex = 0xFF0B101B,
        lapelHex = 0xFF050810,
        trimHex = 0xFFF5D576,
        trouserStripeHex = 0xFF8C2525,
        isCutawayMess = true
    )
}

enum class HeadgearKind { BERET, SERVICE_CAP, OFFICER_CAP, GENERAL_CAP }

enum class HeadgearStyle(
    val label: String,
    val requirementHint: String,
    val requiredTier: RankTier,
    val kind: HeadgearKind,
    val colorHex: Long
) {
    OBSIDIAN_BERET("Standard Army Black Beret", "Post-Basic standard", RankTier.SOLDIER, HeadgearKind.BERET, 0xFF20252E),
    MAROON_BERET("Airborne Maroon Beret", "Airborne School, Airborne Tab, Jump Wings, or Airborne Cord", RankTier.SOLDIER, HeadgearKind.BERET, 0xFF6E2228),
    TAN_BERET("Ranger Tan Beret", "Army Ranger Course or Ranger Tab", RankTier.SOLDIER, HeadgearKind.BERET, 0xFF9C845E),
    EMERALD_BERET("Special Forces Green Beret", "Special Forces School (SFQC), SF Tab, or Emerald Cord (NCO+)", RankTier.NCO, HeadgearKind.BERET, 0xFF254B34),
    ENLISTED_SERVICE_CAP("Enlisted Service Visor Cap", "All Ranks (E-1+) • Service Alphas", RankTier.SOLDIER, HeadgearKind.SERVICE_CAP, 0xFF1F2B3C),
    NCO_SERVICE_CAP("NCO Service Visor Cap", "NCO Rank (E-5+)", RankTier.NCO, HeadgearKind.SERVICE_CAP, 0xFF1A2536),
    OFFICER_PEAKED_CAP("Officer Gold-Braided Cap", "Officer Rank (O-1+)", RankTier.OFFICER, HeadgearKind.OFFICER_CAP, 0xFF182233),
    FLAG_GENERAL_CAP("General's Oak-Leaf Visor Cap", "Flag Officer (O-7+)", RankTier.FLAG_OFFICER, HeadgearKind.GENERAL_CAP, 0xFF101624)
}

enum class AwardCategory(val label: String, val pluralLabel: String) {
    MEDAL("Medal", "Medals"),
    RIBBON("Ribbon", "Ribbons"),
    SHOULDER_CORD("Shoulder Cord", "Shoulder Cords"),
    SCHOOL_INSIGNIA("School Insignia", "School Insignia")
}

enum class InsigniaShape {
    WINGS,
    STAR_WINGS,
    AIR_ASSAULT_WINGS,
    TAB,
    RIFLE_BADGE,
    MEDICAL_BADGE,
    CIB_BADGE,
    CAB_BADGE,
    COMBAT_MEDICAL_BADGE,
    TORCH_CREST,
    CYBER_CREST,
    INTEL_CREST,
    EOD_BADGE,
    LOGISTICS_CREST,
    STAFF_CREST
}

enum class ChestBadgeSlot(val label: String, val ruleHint: String) {
    ABOVE_RACK(
        label = "Above Ribbon Rack",
        ruleHint = "Combat & Special Skill badges (EIB, Combat Med, Jump Wings, Air Assault, EOD, Pathfinder)"
    ),
    LEFT_POCKET(
        label = "Left Breast Pocket",
        ruleHint = "Pocket skill & tech badges (Pathfinder, EOD, Jump Wings, Air Assault, Cyber, Intel)"
    ),
    RIGHT_POCKET(
        label = "Right Breast Pocket",
        ruleHint = "Command Staff, Logistics, Cyber & Intelligence identification crests"
    )
}

enum class CordShoulderSlot(
    val isRightShoulder: Boolean,
    val label: String,
    val ruleHint: String
) {
    LEFT_BRANCH(
        isRightShoulder = false,
        label = "Left Shoulder • Branch Cord",
        ruleHint = "MOS & School cords only (Infantry, SF, Airborne, Engineer, Signal, Intel, Medical, Logistics)"
    ),
    RIGHT_STAFF_VALOR(
        isRightShoulder = true,
        label = "Right Shoulder • Staff & Valor",
        ruleHint = "Command Staff Gold Aiguillette & Valor Braided Fourragere only"
    )
}

enum class MedalTier(val label: String, val precedenceRank: Int) {
    SUPREME("Supreme Honor", 1),
    VALOR("Valor & Heroism", 2),
    SERVICE("Campaign & Service", 3),
    TRAINING("Training & Qualification", 4)
}

data class MedalDefinition(
    val id: String,
    val name: String,
    val shortName: String,
    val category: AwardCategory = AwardCategory.MEDAL,
    val tier: MedalTier,
    val precedenceOrder: Int,
    val minTierRequired: RankTier = RankTier.SOLDIER,
    val isNeckMedallion: Boolean = false,
    val cordHex: Long? = null,
    val isRightShoulder: Boolean = false,
    val insigniaShape: InsigniaShape? = null,
    val insigniaColorHex: Long = 0xFFDDE3EC,
    val allowedBadgeSlots: Set<ChestBadgeSlot> = emptySet(),
    val description: String,
    val qualificationRule: String,
    val ribbonStripeHexes: List<Long>,
    val prestigeCost: Int,
    val minMissionsRequired: Int,
    val firepowerBonus: Int,
    val stealthBonus: Int,
    val techBonus: Int,
    val resilienceBonus: Int,
    val yieldBonusPct: Int
) {
    val cordShoulderSlot: CordShoulderSlot?
        get() = if (category != AwardCategory.SHOULDER_CORD) {
            null
        } else if (isRightShoulder) {
            CordShoulderSlot.RIGHT_STAFF_VALOR
        } else {
            CordShoulderSlot.LEFT_BRANCH
        }

    fun isAllowedInChestSlot(slot: ChestBadgeSlot): Boolean =
        category == AwardCategory.SCHOOL_INSIGNIA &&
            insigniaShape != InsigniaShape.TAB &&
            slot in allowedBadgeSlots
}

data class AwardedMedal(
    val id: String,
    val operatorId: String,
    val medalId: String,
    val citationNote: String,
    val missionTitle: String = "HQ Citation",
    val awardedAtMillis: Long
)

data class PendingDeed(
    val id: String,
    val operatorId: String,
    val missionTitle: String,
    val deedSummary: String,
    val recommendedMedalId: String,
    val createdAtMillis: Long
)

enum class GearSlot(val label: String, val shortLabel: String) {
    WEAPON("Primary Weapon", "WPN"),
    OPTICS("Optics & NVG", "OPT"),
    ARMOR("Plate Carrier", "ARM"),
    HELMET("Helmet & Comms", "HLM"),
    UTILITY("Field Utility", "UTL")
}

/** The unlocked gear in [operator]'s own loadout. */
fun List<GearItem>.equippedBy(operator: Operator): List<GearItem> =
    filter { it.unlocked && operator.equippedGearIds[it.slot] == it.id }

/** The ids of soldiers in [operators] who carry this gear type. */
fun GearItem.carriedBy(operators: List<Operator>): List<Operator> =
    operators.filter { it.equippedGearIds[slot] == id }

data class GearItem(
    val id: String,
    val name: String,
    val slot: GearSlot,
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
    val description: String,
    val requiredSchoolId: String? = null,
    val requiredSchoolLabel: String? = null,
    val requiredArmoryLevel: Int = 1
) {
    val firepower: Int get() = firepowerPerTier * tier
    val stealth: Int get() = stealthPerTier * tier
    val tech: Int get() = techPerTier * tier
    val resilience: Int get() = resiliencePerTier * tier

    val hasQualificationGate: Boolean
        get() = requiredSchoolId != null || requiredArmoryLevel > 1

    val requirementSummary: String?
        get() = buildList {
            if (requiredArmoryLevel > 1) add("Armory Lv.$requiredArmoryLevel")
            if (requiredSchoolLabel != null) add("School: $requiredSchoolLabel")
            else if (requiredSchoolId != null) add("School Required")
        }.takeIf { it.isNotEmpty() }?.joinToString(" • ")

    fun nextActionCreditCost(): Double =
        if (!unlocked) baseCreditCost else (baseCreditCost * 0.7 * tier).roundToInt().toDouble()

    fun nextActionSupplyCost(): Double =
        if (!unlocked) baseSupplyCost else (baseSupplyCost * 0.7 * tier).roundToInt().toDouble()
}

data class StatProfile(
    val firepower: Int,
    val stealth: Int,
    val tech: Int,
    val resilience: Int
) {
    val totalPower: Int get() = firepower + stealth + tech + resilience
}

data class Operator(
    val id: String,
    val name: String,
    val callsign: String,
    val branch: ServiceBranch = ServiceBranch.ARMY,
    val xp: Int,
    val specialty: OperatorSpecialty,
    val status: OperatorStatus = OperatorStatus.READY,
    val recoveryUntilMillis: Long = 0L,
    val transferRequested: Boolean = false,
    val retirementRequested: Boolean = false,
    val petitionCooldownUntilOps: Int = 0,
    val staffOpsSupported: Int = 0,
    val hqRole: HqStaffRole? = null,
    val baseFirepower: Int,
    val baseStealth: Int,
    val baseTech: Int,
    val baseResilience: Int,
    val uniformMode: UniformMode,
    val dressDisplayMode: DressDisplayMode = DressDisplayMode.RIBBONS,
    val camoPattern: CamoPattern,
    val dressStyle: DressUniformStyle,
    val headgearStyle: HeadgearStyle,
    val equippedLeftCordId: String? = null,
    val equippedRightCordId: String? = null,
    val hasWhiteGloves: Boolean = true,
    val hasDressSash: Boolean = false,
    val tailorOverrides: UniformTailorOverrides = UniformTailorOverrides(),
    val activeWardrobeKit: WardrobeKit = if (uniformMode == UniformMode.FIELD_TACTICAL) WardrobeKit.FIELD else WardrobeKit.ALPHAS,
    val wardrobe: OperatorWardrobe = OperatorWardrobe(),
    val missionsCompleted: Int,
    val combatMissionsCompleted: Int = 0,
    val completedMissionIds: Set<String> = emptySet(),
    val schoolAttempts: Map<String, Int> = emptyMap(),
    val highestRiskSurvived: MissionRisk? = null,
    val timesWounded: Int = 0,
    /** One record per campaign fought, holding the squad patch worn there. */
    val combatPatchHistory: List<CombatPatchRecord> = emptyList(),
    val unlocked: Boolean,
    val recruitCreditCost: Double,
    val recruitIntelCost: Double,
    val bio: String,
    val authorizedTier: RankTier = OperatorRank.forXp(xp).tier,
    /** This soldier's own loadout: one unlocked gear type per slot. Gear types are shared, so any number of soldiers can carry the same item. */
    val equippedGearIds: Map<GearSlot, String> = emptyMap()
) {
    val rank: OperatorRank get() = OperatorRank.forXp(xp, authorizedTier)

    val isAtCategoryPromotionGate: Boolean
        get() {
            val next = OperatorRank.nextRank(rank) ?: return false
            return next.tier != rank.tier
        }

    val isReadyForNextCategoryPromotion: Boolean
        get() {
            val next = OperatorRank.nextRank(rank) ?: return false
            return next.tier != rank.tier && xp >= next.xpRequired
        }
}

data class UniformTailorRequest(
    val callsign: String,
    val uniformMode: UniformMode,
    val dressDisplayMode: DressDisplayMode,
    val camoPattern: CamoPattern,
    val dressStyle: DressUniformStyle,
    val headgearStyle: HeadgearStyle,
    val equippedLeftCordId: String?,
    val equippedRightCordId: String?,
    val hasWhiteGloves: Boolean,
    val hasDressSash: Boolean,
    val tailorOverrides: UniformTailorOverrides = UniformTailorOverrides(),
    val wardrobeKit: WardrobeKit = if (uniformMode == UniformMode.FIELD_TACTICAL) WardrobeKit.FIELD else WardrobeKit.ALPHAS,
    /** True only when the player picked headgear by hand in this request */
    val headgearTouched: Boolean = false,
    /** True when the player picked "Auto" headgear, clearing a saved manual pick */
    val headgearAuto: Boolean = false
) {
    companion object {
        fun fromOperator(
            op: Operator,
            kit: WardrobeKit = op.activeWardrobeKit
        ): UniformTailorRequest = UniformTailorRequest(
            callsign = op.callsign,
            uniformMode = if (kit == WardrobeKit.FIELD) UniformMode.FIELD_TACTICAL else UniformMode.DRESS_UNIFORM,
            dressDisplayMode = op.wardrobe.forKit(kit).dressDisplayMode ?: op.dressDisplayMode,
            camoPattern = op.wardrobe.forKit(kit).camoPattern ?: op.camoPattern,
            dressStyle = op.wardrobe.forKit(kit).dressStyle ?: op.dressStyle,
            headgearStyle = op.wardrobe.forKit(kit).headgearStyle ?: op.headgearStyle,
            // Cords are shared by Alphas and Bravos and stored on the Alphas settings; AUTO = resolve at render time
            equippedLeftCordId = op.wardrobe.alphas.let { if (it.cordCustomized) it.equippedLeftCordId else UniformTailorOverrides.SLOT_AUTO },
            equippedRightCordId = op.wardrobe.alphas.let { if (it.cordCustomized) it.equippedRightCordId else UniformTailorOverrides.SLOT_AUTO },
            // Mess keeps its own default (gloves on) and never inherits the Alphas/top-level setting
            hasWhiteGloves = op.wardrobe.forKit(kit).hasWhiteGloves
                ?: if (kit == WardrobeKit.MESS) true else op.hasWhiteGloves,
            hasDressSash = op.wardrobe.forKit(kit).hasDressSash ?: op.hasDressSash,
            tailorOverrides = op.tailorOverrides,
            wardrobeKit = kit
        )
    }
}

enum class TheaterRegion(val label: String) {
    HQ_GROUNDS("FOB Training & Schools"),
    DIPLOMATIC_CORPS("Allied Diplomatic Wing"),
    VERIDIAN_RIDGE("Veridian Ridge"),
    KESTREL_STRAIT("Kestrel Strait"),
    BOREAL_RELAY("Boreal Relay"),
    OBSIDIAN_HARBOR("Obsidian Harbor")
}

enum class MissionScope(val label: String, val hqStaffAllowed: Boolean) {
    TRAINING("School & Training", hqStaffAllowed = true),
    STAFF_DIPLOMATIC("Diplomatic & General Staff", hqStaffAllowed = true),
    FIELD_COMBAT("Field Combat Op", hqStaffAllowed = false)
}

enum class MissionRisk(val label: String, val baseDangerPenalty: Int, val isTraining: Boolean = false) {
    TRAINING("School / Drill • Safe", 0, isTraining = true),
    LOW("Low Risk", 6),
    MODERATE("Moderate Risk", 16),
    HIGH("High Risk", 30),
    EXTREME("Extreme Hazard", 42)
}

data class SchoolRequirements(
    val basePassRatePct: Int = 90,
    val maxAttempts: Int? = null,
    val prerequisiteAllIds: Set<String> = emptySet(),
    val prerequisiteAnyIds: Set<String> = emptySet(),
    val minRankTier: RankTier = RankTier.SOLDIER,
    val minCombatRisk: MissionRisk? = null,
    val minFirepower: Int = 0,
    val minStealth: Int = 0,
    val minTech: Int = 0,
    val minResilience: Int = 0,
    val allowedSpecialties: Set<OperatorSpecialty> = OperatorSpecialty.entries.toSet(),
    val naturalFitSpecialties: Set<OperatorSpecialty> = emptySet(),
    val suitabilitySummary: String = "Open to all MOS",
    val graduationStatBonus: StatProfile = StatProfile(0, 0, 0, 0),
    val graduationRewardsSummary: String = "",
    /** Uniform worn while attending: FIELD for field schools, BRAVOS for classroom schools. */
    val uniformKit: WardrobeKit = WardrobeKit.BRAVOS
)

data class TheaterCampaign(
    val id: String,
    val order: Int,
    val title: String,
    val theater: TheaterRegion,
    val difficultyLabel: String,
    val summary: String,
    val rewardSummary: String,
    val completionBonusCredits: Double,
    val completionBonusSupplies: Double,
    val completionBonusIntel: Double,
    val completionBonusPrestige: Double,
    val theaterBuffLabel: String,
    val stageMissionIds: List<String>
)

data class MissionTemplate(
    val id: String,
    val title: String,
    val theater: TheaterRegion,
    val risk: MissionRisk,
    val scope: MissionScope = MissionScope.FIELD_COMBAT,
    val schoolRequirements: SchoolRequirements? = null,
    val baseSuccessRatePct: Int = 100,
    val briefing: String,
    val durationSeconds: Int,
    val minOperators: Int,
    val maxOperators: Int,
    val recommendedPower: Int,
    val primaryFocus: String,
    val isNightOp: Boolean = false,
    val supplyCost: Double,
    val rewardCredits: Double,
    val rewardSupplies: Double,
    val rewardIntel: Double,
    val rewardPrestige: Double,
    val rewardXp: Int,
    val commsFeed: List<String>,
    val campaignId: String? = null,
    val campaignStageOrder: Int? = null,
    val isEvergreenStandalone: Boolean = false,
    val isRotatingStandalone: Boolean = false,
    val contractTag: String? = null
) {
    /** False for schools, which take any number of eligible soldiers in one class */
    val hasClassCap: Boolean get() = maxOperators != NO_CLASS_CAP

    /** "1-4" for missions, "1+" for schools */
    val teamSizeLabel: String get() = if (hasClassCap) "$minOperators-$maxOperators" else "$minOperators+"

    companion object {
        const val NO_CLASS_CAP = Int.MAX_VALUE
    }
}

const val MAX_SQUAD_SIZE = 4
const val MAX_SQUAD_COHESION_XP = 1000

enum class SquadCohesionTier(
    val label: String,
    val minScore: Int,
    val maxEfficiencyBonusPct: Int,
    val maxSurvivalBonusPct: Int,
    val maxSuccessOddsBonusPct: Int
) {
    FORMING("Forming", 0, 3, 1, 1),
    DRILLED("Drilled", 20, 6, 3, 3),
    SYNCHRONIZED("Synchronized", 45, 10, 5, 5),
    VETERAN("Veteran Unit", 70, 14, 8, 7),
    ELITE("Elite Cohesion", 90, 18, 10, 9);

    companion object {
        fun forScore(score: Int): SquadCohesionTier =
            entries.lastOrNull { score >= it.minScore } ?: FORMING
    }
}

data class SquadUniformStandard(
    val patch: UnitPatchStyle = UnitPatchStyle.AUTO_MOS,
    val camo: CamoPattern? = null,
    val bravosHeadgear: HeadgearStyle? = null,
    val bravosDressStyle: DressUniformStyle? = null,
    val alphasHeadgear: HeadgearStyle? = null,
    val alphasDressStyle: DressUniformStyle? = null,
    val messDressStyle: DressUniformStyle? = null,
    val messHeadgear: HeadgearStyle? = null,
    val alphasGloves: Boolean? = null,
    val messGloves: Boolean? = null
)

data class TacticalSquad(
    val id: String,
    val name: String,
    val memberIds: List<String>,
    val leaderId: String?,
    val cohesionXp: Int = 0,
    val missionsTogether: Int = 0,
    val createdAtMillis: Long = 0L,
    val standardPatch: UnitPatchStyle = UnitPatchStyle.DIVISION_1,
    val standardCamo: CamoPattern? = null,
    val standardHeadgear: HeadgearStyle? = null,
    val standardDressStyle: DressUniformStyle? = null,
    val standardBravosHeadgear: HeadgearStyle? = null,
    val standardBravosDressStyle: DressUniformStyle? = null,
    val standardMessDressStyle: DressUniformStyle? = null,
    val standardMessHeadgear: HeadgearStyle? = null,
    val standardAlphasGloves: Boolean? = null,
    val standardMessGloves: Boolean? = null
) {
    val clampedCohesionXp: Int get() = cohesionXp.coerceIn(0, MAX_SQUAD_COHESION_XP)
    val cohesionScore: Int get() = (clampedCohesionXp / 10).coerceIn(0, 100)
    val cohesionTier: SquadCohesionTier get() = SquadCohesionTier.forScore(cohesionScore)
    val cohesionProgressFraction: Float get() = (clampedCohesionXp / MAX_SQUAD_COHESION_XP.toFloat()).coerceIn(0f, 1f)

    val uniformStandard: SquadUniformStandard
        get() = SquadUniformStandard(
            patch = standardPatch,
            camo = standardCamo,
            bravosHeadgear = standardBravosHeadgear,
            bravosDressStyle = standardBravosDressStyle,
            alphasHeadgear = standardHeadgear,
            alphasDressStyle = standardDressStyle,
            messDressStyle = standardMessDressStyle,
            messHeadgear = standardMessHeadgear,
            alphasGloves = standardAlphasGloves,
            messGloves = standardMessGloves
        )

    val hasUniformStandard: Boolean
        get() = standardCamo != null ||
            standardHeadgear != null ||
            standardDressStyle != null ||
            standardBravosHeadgear != null ||
            standardBravosDressStyle != null ||
            standardMessDressStyle != null ||
            standardMessHeadgear != null ||
            standardAlphasGloves != null ||
            standardMessGloves != null

    val standardSummaryParts: List<String>
        get() = buildList {
            add("SSI: ${standardPatch.label}")
            standardCamo?.let { add("Field: ${it.label}") }
            val bravosParts = listOfNotNull(standardBravosDressStyle?.shortLabel, standardBravosHeadgear?.label)
            if (bravosParts.isNotEmpty()) add("Bravos: ${bravosParts.joinToString(" / ")}")
            val alphasParts = listOfNotNull(standardDressStyle?.shortLabel, standardHeadgear?.label)
            if (alphasParts.isNotEmpty()) add("Alphas: ${alphasParts.joinToString(" / ")}")
            val messParts = listOfNotNull(standardMessDressStyle?.shortLabel, standardMessHeadgear?.label)
            if (messParts.isNotEmpty()) add("Mess: ${messParts.joinToString(" / ")}")
            standardAlphasGloves?.let { add(if (it) "Alphas: White Gloves" else "Alphas: No Gloves") }
            standardMessGloves?.let { add(if (it) "Mess: White Gloves" else "Mess: No Gloves") }
        }
}

data class UniformInspectionReport(
    val subjectLabel: String,
    val gradeLetter: String,
    val scorePct: Int,
    val prestigeEarned: Int,
    val buffDurationSec: Int,
    val findings: List<String>,
    val highlights: List<String>
)

data class ActiveSquadCohesionBonus(
    val squad: TacticalSquad,
    val deployedMemberCount: Int,
    val leaderPresent: Boolean,
    val cohesionScore: Int,
    val tier: SquadCohesionTier,
    val efficiencyBonusPct: Int,
    val survivalBonusPct: Int,
    val successOddsBonusPct: Int
)

data class ActiveDeployment(
    val id: String,
    val templateId: String,
    val operatorIds: List<String>,
    val startedAtMillis: Long,
    val durationMillis: Long,
    val efficiencyPct: Int,
    val avgSurvivalPct: Int,
    val squadId: String? = null
) {
    fun progressFraction(nowMillis: Long): Float {
        if (durationMillis <= 0L) return 1f
        return ((nowMillis - startedAtMillis).toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
    }

    fun remainingSeconds(nowMillis: Long): Long {
        val rem = (startedAtMillis + durationMillis) - nowMillis
        return (rem / 1000L).coerceAtLeast(0L)
    }

    fun isComplete(nowMillis: Long): Boolean = nowMillis >= startedAtMillis + durationMillis
}

data class OperatorDebriefOutcome(
    val operatorId: String,
    val callsign: String,
    val rankCode: String,
    val outcome: OperatorOutcomeType,
    val deedText: String,
    val recommendedMedalId: String?,
    val xpEarned: Int,
    val promotedToRankCode: String? = null,
    val readyForCategoryRankCode: String? = null
)

enum class OperatorOutcomeType(val label: String) {
    SAFE("Returned Safe"),
    TRAINING_PASSED("Course Graduated"),
    TRAINING_FAILED("Course Failed"),
    WOUNDED("Medevaced (Recovering)"),
    FALLEN("Fallen in Action"),
    STAFF_SUPPORT("HQ Staff Support")
}

data class OfflineReport(
    val elapsedSeconds: Long,
    val creditsEarned: Double,
    val suppliesEarned: Double,
    val intelEarned: Double,
    val finishedOpsCount: Int
)

data class DebriefSummary(
    val missionTitle: String,
    val theaterName: String,
    val missionSucceeded: Boolean = true,
    val isTrainingMission: Boolean = false,
    val efficiencyPct: Int,
    val creditsEarned: Double,
    val suppliesEarned: Double,
    val intelEarned: Double,
    val prestigeEarned: Double,
    val operatorOutcomes: List<OperatorDebriefOutcome>,
    val squadCohesionNote: String? = null,
    val campaignProgressNote: String? = null
)
