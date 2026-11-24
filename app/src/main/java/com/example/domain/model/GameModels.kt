package com.example.domain.model

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

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
    FLAG_OFFICER("General / Flag Officer", "Flag Officer", 5),
    /** Warrant officers: a separate track entered through WOCS. Same order as commissioned officers. */
    WARRANT("Warrant Officer", "Warrant", 4);

    val isEnlisted: Boolean get() = this == SOLDIER || this == NCO || this == SNCO
    val isNcoBand: Boolean get() = this == NCO || this == SNCO
    /** Wears officer uniforms: commissioned, flag and warrant officers. */
    val isOfficer: Boolean get() = this == OFFICER || this == FLAG_OFFICER || this == WARRANT
    val isCommissioned: Boolean get() = this == OFFICER || this == FLAG_OFFICER
}

enum class FacilityType(
    val id: String,
    val title: String,
    val subtitle: String,
    val primaryResource: ResourceType,
    val baseRatePerSec: Double,
    val baseCreditCost: Double,
    val baseSupplyCost: Double,
    val perkDescription: String,
    /** Command HQ level needed before this building can be built. */
    val unlockHqLevel: Int = 1,
    /** Level at the start of a new game; 0 = an empty lot. */
    val startLevel: Int = 1
) {
    COMMAND_HQ(
        id = "command_hq",
        title = "Command HQ",
        subtitle = "Directorate operations and staff wing",
        primaryResource = ResourceType.CREDITS,
        baseRatePerSec = 2.5,
        baseCreditCost = 80.0,
        baseSupplyCost = 30.0,
        perkDescription = "+1 HQ staff slot/Lv • +1 mission slot at Lv 3 and Lv 6 • second construction crew at Lv 5 • unlocks new buildings"
    ),
    BARRACKS(
        id = "barracks",
        title = "Barracks",
        subtitle = "Field soldier housing",
        primaryResource = ResourceType.CREDITS,
        baseRatePerSec = 0.0,
        baseCreditCost = 70.0,
        baseSupplyCost = 35.0,
        perkDescription = "Field roster: 8 soldiers at Lv 1, +3 per level"
    ),
    LOGISTICS_DEPOT(
        id = "logistics_depot",
        title = "Quartermaster",
        subtitle = "Ordnance, rations and supply",
        primaryResource = ResourceType.SUPPLIES,
        baseRatePerSec = 2.0,
        baseCreditCost = 65.0,
        baseSupplyCost = 20.0,
        perkDescription = "Generates supplies • -3% gear and mission supply cost per level"
    ),
    ARMORY_WORKSHOP(
        id = "armory_workshop",
        title = "Armory",
        subtitle = "Weapon tuning and gear requisition",
        primaryResource = ResourceType.CREDITS,
        baseRatePerSec = 1.4,
        baseCreditCost = 110.0,
        baseSupplyCost = 55.0,
        perkDescription = "+5% gear stat bonus per level • unlocks higher gear tiers • generates credits"
    ),
    RECON_ARRAY(
        id = "recon_array",
        title = "Intel Center",
        subtitle = "Signals, overwatch and mission planning",
        primaryResource = ResourceType.INTEL,
        baseRatePerSec = 0.85,
        baseCreditCost = 100.0,
        baseSupplyCost = 45.0,
        perkDescription = "Generates intel • -4% mission duration and +2% survival per level"
    ),
    DFAC(
        id = "dfac",
        title = "DFAC",
        subtitle = "Dining facility",
        primaryResource = ResourceType.CREDITS,
        baseRatePerSec = 0.0,
        baseCreditCost = 75.0,
        baseSupplyCost = 40.0,
        perkDescription = "+5% squad cohesion gains per level • fewer transfer and retirement requests",
        unlockHqLevel = 2,
        startLevel = 0
    ),
    TRAINING_RANGE(
        id = "training_range",
        title = "Training Range",
        subtitle = "Ranges, lanes and qualification",
        primaryResource = ResourceType.CREDITS,
        baseRatePerSec = 0.0,
        baseCreditCost = 90.0,
        baseSupplyCost = 50.0,
        perkDescription = "+1% school pass rate per level (+2% for SF, Ranger and EIB) • hosts Rifle Qualification and raises scores",
        unlockHqLevel = 3,
        startLevel = 0
    ),
    AID_STATION(
        id = "aid_station",
        title = "Aid Station",
        subtitle = "Battalion aid and recovery",
        primaryResource = ResourceType.SUPPLIES,
        baseRatePerSec = 0.0,
        baseCreditCost = 85.0,
        baseSupplyCost = 45.0,
        perkDescription = "-7% medbay recovery time per level • +5% chance per level a casualty is wounded instead of killed",
        unlockHqLevel = 3,
        startLevel = 0
    ),
    AIRFIELD(
        id = "airfield",
        title = "Airfield",
        subtitle = "Runway, helipad and hangar",
        primaryResource = ResourceType.CREDITS,
        baseRatePerSec = 0.0,
        baseCreditCost = 140.0,
        baseSupplyCost = 70.0,
        perkDescription = "Base for flight training and helicopter insertion (arriving with the aviation updates)",
        unlockHqLevel = 4,
        startLevel = 0
    ),
    /** Item 118: houses the SF squads apart from the Barracks; buildable once the first SF squad forms. */
    SF_COMPOUND(
        id = "sf_compound",
        title = "SF Compound",
        subtitle = "Special Forces team rooms and rehearsal yard",
        primaryResource = ResourceType.CREDITS,
        baseRatePerSec = 0.0,
        baseCreditCost = 160.0,
        baseSupplyCost = 80.0,
        perkDescription = "Houses 6 SF soldiers at Lv 1, 12 at Lv 4, 18 at Lv 7 (up to 3 SF squads) • -1% blown cover and 4% faster SF recovery per level • SF schools +5% at Lv 10",
        startLevel = 0
    );

    fun productionPerSec(level: Int): Double =
        if (level <= 0) 0.0 else baseRatePerSec * (1.0 + (level - 1) * 0.45)

    fun upgradeCreditCost(currentLevel: Int): Double =
        (baseCreditCost * 1.48.pow(currentLevel - 1)).roundToInt().toDouble()

    fun upgradeSupplyCost(currentLevel: Int): Double =
        (baseSupplyCost * 1.42.pow(currentLevel - 1)).roundToInt().toDouble()

    /** Seconds to build from [currentLevel] to the next level. */
    fun buildSeconds(currentLevel: Int): Long =
        com.example.domain.GameTiming.seconds(facilityBuildSeconds(currentLevel + 1))
}

/** Random part of a rifle qualification score: 0 until this value. */
const val RIFLE_QUAL_ROLL_MAX = 12
const val SHARPSHOOTER_SCORE = 22
const val EXPERT_SCORE = 30

fun rifleQualRank(tier: MarksmanshipBadgeTier?): Int =
    listOf(MarksmanshipBadgeTier.MARKSMAN, MarksmanshipBadgeTier.SHARPSHOOTER, MarksmanshipBadgeTier.EXPERT).indexOf(tier)

fun rifleQualTierForScore(score: Int): MarksmanshipBadgeTier = when {
    score >= EXPERT_SCORE -> MarksmanshipBadgeTier.EXPERT
    score >= SHARPSHOOTER_SCORE -> MarksmanshipBadgeTier.SHARPSHOOTER
    else -> MarksmanshipBadgeTier.MARKSMAN
}

/** Starting qualification for soldiers who arrive already through basic: their firepower plus an average roll. */
fun startingRifleQualification(operator: Operator): MarksmanshipBadgeTier? =
    if ("msn_basic_qual" in operator.completedMissionIds) rifleQualTierForScore(operator.baseFirepower + RIFLE_QUAL_ROLL_MAX / 2) else null

/** Highest level any building reaches. */
const val MAX_FACILITY_LEVEL = 10
/** First build of an empty lot (to Lv 1). */
const val FIRST_BUILD_SECONDS = 180L
/** Upgrade to Lv 2 takes this long; each level after multiplies by [BUILD_TIME_GROWTH]. */
const val LEVEL_TWO_BUILD_SECONDS = 300L
/** 5 minutes at Lv 2 grows to about 7 hours at Lv 10. */
const val BUILD_TIME_GROWTH = 1.74
/** Prestige per remaining minute to finish a build now. */
const val RUSH_PRESTIGE_PER_MINUTE = 1.0
const val RUSH_MIN_PRESTIGE = 5

fun facilityBuildSeconds(targetLevel: Int): Long = when {
    targetLevel <= 1 -> FIRST_BUILD_SECONDS
    else -> (LEVEL_TWO_BUILD_SECONDS * BUILD_TIME_GROWTH.pow(targetLevel - 2)).roundToLong()
}

data class FacilityState(
    val type: FacilityType,
    val level: Int,
    /** When the running upgrade finishes; 0 when the building is idle. */
    val upgradeEndsAtMillis: Long = 0L,
    val upgradeStartedAtMillis: Long = 0L
) {
    val isUpgrading: Boolean get() = upgradeEndsAtMillis > 0L
    val isBuilt: Boolean get() = level > 0
}

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
    GEN("GEN", "O-10", "General", RankTier.FLAG_OFFICER, 12500, 4),

    // WARRANT OFFICER (W-1 to W-5): its own track, kept last so enlisted/commissioned ordering is unchanged.
    // A warrant officer's grade is stored on the soldier (warrantGrade), not derived from XP; xpRequired is
    // the XP each grade needs before its promotion board can sit.
    WO1("WO1", "W-1", "Warrant Officer 1", RankTier.WARRANT, 2000, 1),
    CW2("CW2", "W-2", "Chief Warrant Officer 2", RankTier.WARRANT, 2600, 2),
    CW3("CW3", "W-3", "Chief Warrant Officer 3", RankTier.WARRANT, 3300, 3),
    CW4("CW4", "W-4", "Chief Warrant Officer 4", RankTier.WARRANT, 4100, 4),
    CW5("CW5", "W-5", "Chief Warrant Officer 5", RankTier.WARRANT, 5000, 5),

    // Item 110: the Command Sergeant Major's grade (E-9, same as SGM). Never comes from XP; a SGM seated
    // as the CSM wears it. Kept last so no other ordinal moves.
    CSM("CSM", "E-9", "Command Sergeant Major", RankTier.SNCO, 1520, 7);

    /** Officer uniforms and courtesies: commissioned, flag and warrant officers. */
    val isOfficer: Boolean get() = tier.isOfficer
    val isCommissioned: Boolean get() = tier == RankTier.OFFICER || tier == RankTier.FLAG_OFFICER
    val isWarrant: Boolean get() = tier == RankTier.WARRANT
    /** 1..5 for WO1..CW5, 0 otherwise. */
    val warrantGrade: Int get() = if (isWarrant) insigniaCount else 0
    /** 1..4 for BG..GEN, 0 otherwise. */
    val flagGrade: Int get() = if (tier == RankTier.FLAG_OFFICER) ordinal - BG.ordinal + 1 else 0

    /**
     * Position on one seniority ladder for stat bonuses, sorting and minimum-rank checks. Warrant grades sit
     * between senior NCOs and company-grade officers: WO1 with MSG, CW2 with 2LT, up to CW5 with MAJ.
     */
    val seniorityIndex: Int get() = when (this) {
        WO1 -> MSG.ordinal
        CW2 -> LT2.ordinal
        CW3 -> LT1.ordinal
        CW4 -> CPT.ordinal
        CW5 -> MAJ.ordinal
        CSM -> SGM.ordinal
        else -> ordinal
    }
    val isFieldGradeOfficer: Boolean get() = this == MAJ || this == LTC || this == COL
    val isCareerEligibleForStaffOrTransfer: Boolean get() = ordinal >= SGT.ordinal

    fun promotionCreditCost(): Double = when (tier) {
        RankTier.SOLDIER -> 75.0
        RankTier.NCO -> 140.0
        RankTier.SNCO -> 200.0
        RankTier.OFFICER -> 260.0
        RankTier.FLAG_OFFICER -> 480.0
        RankTier.WARRANT -> 200.0
    }

    fun promotionPrestigeCost(): Int = when (tier) {
        RankTier.SOLDIER -> 4
        RankTier.NCO -> 8
        RankTier.SNCO -> 11
        RankTier.OFFICER -> 14
        RankTier.FLAG_OFFICER -> 24
        RankTier.WARRANT -> 12
    }

    companion object {
        /** Enlisted and commissioned ranks by XP. Warrant grades never come from XP (see [forWarrantGrade]). */
        fun forXp(xp: Int, maxTier: RankTier = RankTier.FLAG_OFFICER): OperatorRank {
            if (maxTier == RankTier.WARRANT) return forXp(xp, RankTier.SNCO)
            var current = PVT
            for (rank in entries) {
                if (rank.isWarrant || rank == CSM) continue
                if (rank.tier.order <= maxTier.order && xp >= rank.xpRequired) {
                    current = rank
                }
            }
            return current
        }

        val warrantRanks: List<OperatorRank> get() = entries.filter { it.isWarrant }

        fun forWarrantGrade(grade: Int): OperatorRank = warrantRanks[(grade.coerceIn(1, 5)) - 1]

        val flagRanks: List<OperatorRank> get() = listOf(BG, MG, LTG, GEN)

        fun forFlagGrade(grade: Int): OperatorRank = flagRanks[(grade.coerceIn(1, 4)) - 1]

        fun ceilingXpForTier(tier: RankTier): Int? =
            if (tier == RankTier.WARRANT) null
            else entries.firstOrNull { !it.isWarrant && it.tier.order > tier.order }?.xpRequired

        fun clampXpToAuthorizedTier(xp: Int, authorizedTier: RankTier): Int {
            val ceiling = ceilingXpForTier(authorizedTier)
            return if (ceiling != null) xp.coerceAtMost(ceiling) else xp
        }

        fun nextRank(current: OperatorRank): OperatorRank? {
            if (current.isWarrant) {
                return if (current == CW5) null else forWarrantGrade(current.warrantGrade + 1)
            }
            if (current == GEN) return null
            if (current == CSM) return nextRank(SGM)
            val idx = entries.indexOf(current)
            return if (idx in 0 until entries.lastIndex) entries[idx + 1] else null
        }

        /** Warrant grades and every general's star (item 61) are each their own board, like a tier crossing. */
        fun isBoardGate(from: OperatorRank, to: OperatorRank): Boolean =
            to.tier != from.tier || to.isWarrant || to.tier == RankTier.FLAG_OFFICER
    }
}

enum class OperatorSpecialty(val label: String, val roleTag: String, val recruitSummary: String) {
    ASSAULT("Infantry", "11B INF", "Direct action & perimeter assault"),
    RECON("Recon & Aviation", "19D CAV", "Pathfinder scout & air-assault recon"),
    SIGNALS("Cyber & Signals", "25B SIG", "Encrypted comms, UAV telemetry & EW"),
    BREACHER("Engineer & EOD", "12B ENG", "Ordnance disposal, sapper & bridging"),
    MEDIC("Medical Corps", "68W MED", "Trauma stabilization & medevac boost"),
    COMMAND("Staff & Command", "HQ CMD", "Operations coordination & leadership"),
    /** Warrant officer aviators. Reached only through Flight School; never recruited as enlisted. */
    AVIATION("Army Aviation", "153A AVN", "Helicopter pilot flying insertions and support")
}

/** Where a new recruit comes from (item 31). Each arrives at a set rank with that school's record. */
enum class RecruitSource(
    val label: String,
    val arrivalRank: OperatorRank,
    val creditCost: Double,
    /** School marked complete on arrival; null for Reception Station */
    val schoolId: String?
) {
    RECEPTION("Reception Station", OperatorRank.PV2, 45.0, null),
    NCO_ACADEMY("NCO Academy", OperatorRank.SGT, 110.0, "msn_nco_school"),
    SENIOR_LEADER_COURSE("Senior Leader Course", OperatorRank.SFC, 200.0, "msn_snco_school"),
    OCS("Officer Candidate School", OperatorRank.LT2, 220.0, "msn_ocs_school"),
    WOCS_TECHNICAL("WOCS: Technical", OperatorRank.WO1, 180.0, "msn_wocs"),
    WOCS_AVIATOR("WOCS: Aviator", OperatorRank.WO1, 240.0, "msn_wocs");

    val summary: String get() = "${arrivalRank.code} (${arrivalRank.payGrade}) • ${creditCost.toInt()} CR"
}

/** Item 69: per-soldier effects from soldier events. */
data class SoldierEventBuffs(
    val schoolDiscountUntilMillis: Long = 0L,
    val opRequestType: com.example.domain.OpRequestType? = null,
    val opRequestUntilMillis: Long = 0L,
    val rifleQualBonusPct: Int = 0,
    val leadsNextMission: Boolean = false
) {
    fun toCsv(): String = listOf(
        schoolDiscountUntilMillis, opRequestType?.name ?: "", opRequestUntilMillis, rifleQualBonusPct, leadsNextMission
    ).joinToString("|")

    companion object {
        fun fromCsv(csv: String): SoldierEventBuffs {
            val p = csv.split("|")
            if (p.size < 5) return SoldierEventBuffs()
            return SoldierEventBuffs(
                schoolDiscountUntilMillis = p[0].toLongOrNull() ?: 0L,
                opRequestType = com.example.domain.OpRequestType.entries.find { it.name == p[1] },
                opRequestUntilMillis = p[2].toLongOrNull() ?: 0L,
                rifleQualBonusPct = p[3].toIntOrNull() ?: 0,
                leadsNextMission = p[4] == "true"
            )
        }
    }
}

enum class OperatorStatus(val label: String) {
    READY("READY"),
    DEPLOYED("DEPLOYED"),
    RECOVERING("MEDBAY"),
    HQ_STAFF("HQ STAFF"),
    RETIRED("RETIRED"),
    FALLEN("MEMORIAL"),
    /** Item 46: transferred to another command; kept on file so names and callsigns aren't reused. */
    TRANSFERRED("TRANSFERRED"),
    /** Item 69: away on leave or a short duty from an event; back at recoveryUntilMillis. */
    ON_LEAVE("LEAVE"),
    /** Item 63: held by the enemy. Keeps a roster slot until they come home. */
    CAPTURED("POW");

    val isOutOfActiveService: Boolean get() = this == FALLEN || this == RETIRED || this == TRANSFERRED
}

/**
 * Item 107: each HQ billet's top rank, so staffers stop coming up for promotion there.
 * CO, Deputy Commander and Command Chief Warrant Officer have no cap. The S-3 is the Operations Sergeant
 * Major (enlisted only) and stops at SGM.
 */
/** Item 150: chain of command state the rank caps need, set from the live roster by the view model. */
object HqChain {
    /** Rank of the seated CO, or null when the CO seat is empty. */
    @Volatile var commanderRank: OperatorRank? = null

    private val commissionedLadder = listOf(
        OperatorRank.LT2, OperatorRank.LT1, OperatorRank.CPT, OperatorRank.MAJ, OperatorRank.LTC,
        OperatorRank.COL, OperatorRank.BG, OperatorRank.MG, OperatorRank.LTG, OperatorRank.GEN
    )

    /** The Deputy Commander tops out one rank below the CO (LTG while the CO seat is empty). */
    fun deputyCap(): OperatorRank {
        val co = commanderRank ?: return OperatorRank.LTG
        val idx = commissionedLadder.indexOf(co)
        return if (idx > 0) commissionedLadder[idx - 1] else commissionedLadder.first()
    }
}

fun HqStaffRole.rankCapFor(rank: OperatorRank): OperatorRank? = when (this) {
    HqStaffRole.CO_COMMANDING_OFFICER, HqStaffRole.CCWO_COMMAND_CHIEF_WARRANT,
    HqStaffRole.CSM_COMMAND_SERGEANT_MAJOR -> null
    HqStaffRole.DEPUTY_COMMANDER -> HqChain.deputyCap()
    HqStaffRole.XO_CHIEF_OF_STAFF -> OperatorRank.COL
    HqStaffRole.S3_OPS_DIRECTOR -> if (rank.tier.isEnlisted) OperatorRank.SGM else null
    HqStaffRole.AIR_OPS_OFFICER -> if (rank.isWarrant) OperatorRank.CW4 else OperatorRank.MAJ
    else -> when {
        rank.isWarrant -> OperatorRank.CW4
        rank.tier.isEnlisted -> OperatorRank.MSG
        else -> OperatorRank.MAJ
    }
}

/** Item 107: billets enlisted soldiers only can hold. */
val HqStaffRole.isEnlistedOnly: Boolean get() = this == HqStaffRole.S3_OPS_DIRECTOR || this == HqStaffRole.CSM_COMMAND_SERGEANT_MAJOR

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
    val xpBonusPct: Int = 0,
    /** Billet needs a warrant officer aviator (153A). */
    val requiresAviator: Boolean = false,
    /** Billet needs a technical warrant officer (warrant, not an aviator). */
    val requiresTechnicalWarrant: Boolean = false,
    /** Cut to warrant officer promotion costs while filled. */
    val warrantPromotionDiscountPct: Int = 0,
    /** Cut to helicopter insertion supply cost and risk while filled (insertions arrive in Session 10). */
    val heloInsertionBonusPct: Int = 0,
    /** Item 102: pass bonus on SF missions and SF campaign stages, and on SF schools. */
    val sfMissionPassBonusPct: Int = 0,
    val sfSchoolPassBonusPct: Int = 0,
    /** Item 102: the billet needs an SF-qualified soldier, SFC or above (or an officer or warrant). */
    val requiresSfQualified: Boolean = false,
    /** Item 110: cheaper enlisted promotion boards, easier NCO schools and faster cohesion. */
    val enlistedBoardDiscountPct: Int = 0,
    val ncoSchoolPassBonusPct: Int = 0,
    val cohesionGainBonusPct: Int = 0
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
    /** Item 110: the CO's senior enlisted partner; unlocks with the XO. */
    CSM_COMMAND_SERGEANT_MAJOR(
        slotOrder = 2,
        requiredHqLevel = 1,
        shortCode = "CSM",
        title = "Command Sergeant Major",
        description = "The command's senior enlisted leader. Smooths enlisted promotion boards, sharpens the NCO schools and pulls squads together faster.",
        minRank = OperatorRank.SGM,
        minMissionsRequired = 4,
        survivalBonusPct = 1,
        efficiencyBonusPct = 3,
        xpBonusPct = 5,
        enlistedBoardDiscountPct = 20,
        ncoSchoolPassBonusPct = 5,
        cohesionGainBonusPct = 20
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
    /** Item 102: the HQ's Special Forces officer; unlocks with the S-4. */
    SOF_SPECIAL_OPERATIONS(
        slotOrder = 6,
        requiredHqLevel = 4,
        shortCode = "SOF",
        title = "SOF: Special Operations Officer",
        description = "SF-qualified staff officer backing the SF squad: better odds on SF missions, SF campaign stages and SF schools.",
        minRank = OperatorRank.SFC,
        minMissionsRequired = 4,
        requiredSchoolIds = setOf("msn_sf_school"),
        requiredSchoolLabel = "Special Forces Qualification Course",
        intelFlowPct = 6,
        survivalBonusPct = 2,
        efficiencyBonusPct = 3,
        sfMissionPassBonusPct = 5,
        sfSchoolPassBonusPct = 6,
        requiresSfQualified = true
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
    ),
    AIR_OPS_OFFICER(
        slotOrder = 8,
        requiredHqLevel = 6,
        shortCode = "AIR",
        title = "Air Operations Officer",
        description = "Senior warrant aviator planning lift, routing and air support. Helicopter insertions cost less supply and carry less risk.",
        minRank = OperatorRank.CW2,
        minMissionsRequired = 3,
        supplyFlowPct = 6,
        durationReductionPct = 4,
        survivalBonusPct = 2,
        efficiencyBonusPct = 3,
        requiresAviator = true,
        heloInsertionBonusPct = 20
    ),
    CCWO_COMMAND_CHIEF_WARRANT(
        slotOrder = 9,
        requiredHqLevel = 7,
        shortCode = "CCWO",
        title = "Command Chief Warrant Officer",
        description = "The command's senior technical warrant officer. Mentors the warrant corps (cheaper warrant promotions) and sharpens HQ technical and intelligence work.",
        minRank = OperatorRank.CW4,
        minMissionsRequired = 6,
        intelFlowPct = 10,
        efficiencyBonusPct = 4,
        xpBonusPct = 5,
        requiresTechnicalWarrant = true,
        warrantPromotionDiscountPct = 25
    );

    val requiredTier: RankTier get() = minRank.tier

    val requirementSummary: String
        get() = buildString {
            append("${minRank.code} (${minRank.payGrade})+ • ${minMissionsRequired}+ Ops")
            if (requiresAviator) append(" • Warrant Aviator")
            if (requiresTechnicalWarrant) append(" • Technical Warrant")
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
            if (sfMissionPassBonusPct > 0) add("+${sfMissionPassBonusPct}% SF Ops")
            if (sfSchoolPassBonusPct > 0) add("+${sfSchoolPassBonusPct}% SF Schools")
            if (enlistedBoardDiscountPct > 0) add("-${enlistedBoardDiscountPct}% Enlisted Boards")
            if (ncoSchoolPassBonusPct > 0) add("+${ncoSchoolPassBonusPct}% NCO Schools")
            if (cohesionGainBonusPct > 0) add("+${cohesionGainBonusPct}% Cohesion")
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
        !(this == FIELD && (operator.status == OperatorStatus.HQ_STAFF || operator.isAviator))

    /** Item 93: aviators wear a flight suit in place of Bravos. */
    fun shortLabelFor(operator: Operator): String =
        if (this == BRAVOS && operator.isAviator) "Flight Suit" else shortLabel

    fun labelFor(operator: Operator): String =
        if (this == BRAVOS && operator.isAviator) "Flight Suit" else label
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

/** Item 58/68: the SF patch and beret flash colors. */
const val SF_PATCH_BLACK = 0xFF141414
const val SF_PATCH_RED = 0xFFB3202A
const val SF_PATCH_RED_BRIGHT = 0xFFE0444C

enum class UnitPatchStyle(
    val label: String,
    val subtitle: String,
    val primaryHex: Long,
    val accentHex: Long
) {
    /** Tailor setting only: wear the squad patch, High Command for HQ staff, or Unassigned. */
    AUTO_MOS("Auto (Squad Patch)", "Squad patch; High Command for HQ staff; Aviation Brigade for aviators; Unassigned without a squad", 0xFF3A4556, 0xFFD8B24C),
    DIVISION_1("1st Aegis Division", "Cobalt and gold shield with a single star", 0xFF1D4E89, 0xFFD8B24C),
    DIVISION_2("2nd Aegis Division", "Crimson and silver shield with crossed sabers", 0xFF8A1F2B, 0xFFD9DEE6),
    DIVISION_3("3rd Aegis Division", "Black and gold shield with a lion head", 0xFF15171C, 0xFFD8B24C),
    DIVISION_4("4th Aegis Division", "Forest green and white shield with an oak leaf", 0xFF234A2E, 0xFFF2F4F2),
    DIVISION_5("5th Aegis Division", "Slate gray and orange shield with a tower", 0xFF4A5563, 0xFFE8862A),
    DIVISION_6("6th Aegis Division", "Navy and silver shield with an eagle", 0xFF13213F, 0xFFC9D1DC),
    SUPREME_HQ("Aegis High Command", "Worn by HQ staff only", 0xFF141E33, 0xFFF5D576),
    /** Item 58: the SF squad's arrowhead patch (dagger and lightning), black and red; subdued in the field. */
    SPECIAL_FORCES("Special Forces", "Black and red arrowhead with dagger and lightning; worn by the SF squad", 0xFF141414, 0xFFB3202A),
    /** Item 111: SF aviators' patch. */
    SOA("Special Operations Aviation", "Black and gold shield with a night-sky moon and rotor; worn by SF aviators", 0xFF0F1116, 0xFFD8B24C),
    /** Item 100: the aviators' own unit patch. */
    AVIATION_BRIGADE("Aegis Aviation Brigade", "Sky blue and gold shield with winged rotor; worn by aviators", 0xFF1E3550, 0xFF8EC3EA),
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
    AUTO("Auto (Rifle Qualification)", "Auto Qual", "Shows the soldier's awarded rifle qualification", 0xFFC8D3E0),
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
    /** Tailor Studio override for the leader pin and tab; null = Auto from squad roles */
    val leaderRoleOverride: LeaderRole? = null,
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
    val accentHex: Long,
    /** Item 94: terrain this pattern is made for (+3% pass, +2 survival when most of the team matches). */
    val suitedTerrains: Set<Terrain>,
    /** Terrain where this pattern clearly stands out (a small penalty). */
    val wrongTerrains: Set<Terrain>
) {
    WOODLAND_OLIVE("Army Scorpion OCP", 0xFF4F5D3B, 0xFF313924, 0xFF7B855B,
        setOf(Terrain.WOODLAND, Terrain.MOUNTAIN), setOf(Terrain.ARCTIC)),
    NIGHT_CHARCOAL("Night Ops Slate", 0xFF262D38, 0xFF171B22, 0xFF3E4A59,
        setOf(Terrain.URBAN), setOf(Terrain.DESERT, Terrain.ARCTIC)),
    ALPINE_FROST("Alpine Frost", 0xFF7D8B96, 0xFF49545E, 0xFFB8C4CE,
        setOf(Terrain.ARCTIC, Terrain.MOUNTAIN), setOf(Terrain.JUNGLE, Terrain.DESERT, Terrain.WOODLAND)),
    ARID_SAND("Arid Canyon", 0xFF8C7A5B, 0xFF5D4F38, 0xFFB5A17D,
        setOf(Terrain.DESERT), setOf(Terrain.ARCTIC, Terrain.JUNGLE, Terrain.WOODLAND)),
    RANGER_GREEN("Ranger Olive", 0xFF36452E, 0xFF212B1C, 0xFF566B4A,
        setOf(Terrain.JUNGLE, Terrain.WOODLAND), setOf(Terrain.ARCTIC, Terrain.DESERT)),
    LITTORAL_TIDE("Littoral Tide", 0xFF4A5A66, 0xFF2C3740, 0xFF7C8E99,
        setOf(Terrain.COASTAL), setOf(Terrain.JUNGLE, Terrain.DESERT)),
    /** Item 93: the aviator flight suit's solid sage. Not a field camo choice. */
    FLIGHT_SAGE("Flight Suit Sage", 0xFF6E775B, 0xFF5D654C, 0xFF7C866A, emptySet(), emptySet()),
    /** Item 113: SF aviators' black flight suit. */
    FLIGHT_BLACK("Flight Suit Black", 0xFF1E2023, 0xFF16181A, 0xFF2A2D31, emptySet(), emptySet());

    companion object {
        /** Patterns a player can pick for field uniforms. */
        val SELECTABLE: List<CamoPattern> get() = entries.filter { it != FLIGHT_SAGE && it != FLIGHT_BLACK }
    }

    fun fitFor(terrain: Terrain): CamoFit = when (terrain) {
        in suitedTerrains -> CamoFit.MATCH
        in wrongTerrains -> CamoFit.WRONG
        else -> CamoFit.NEUTRAL
    }
}

enum class CamoFit { MATCH, NEUTRAL, WRONG }

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
    EMERALD_BERET("Special Forces Green Beret", "Special Forces School (SFQC), SF Tab, or Emerald Cord", RankTier.SOLDIER, HeadgearKind.BERET, 0xFF254B34),
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
    STAFF_CREST,
    AVIATOR_WINGS,
    SENIOR_AVIATOR_WINGS,
    MASTER_AVIATOR_WINGS,
    DIVER_BADGE,
    MFF_WINGS
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

/** Skin tones for the uniform figures; [shadowHex] shades the neck, ears, hands and features. */
enum class SkinTone(val label: String, val toneHex: Long, val shadowHex: Long) {
    FAIR("Fair", 0xFFF1D6C0, 0xFFCFAE96),
    LIGHT("Light", 0xFFE6C1A1, 0xFFC09A7B),
    LIGHT_MEDIUM("Light Medium", 0xFFD1A98E, 0xFFA98067),
    MEDIUM("Medium", 0xFFC08A63, 0xFF96664A),
    OLIVE("Olive", 0xFFAF8458, 0xFF86603D),
    TAN("Tan", 0xFF9C6B47, 0xFF754D32),
    BROWN("Brown", 0xFF7A4C32, 0xFF573423),
    DEEP("Deep", 0xFF553424, 0xFF3A2218)
}

/** Body builds; [widthScale] widens or narrows the shoulders and torso. */
enum class BodyBuild(val label: String, val widthScale: Float) {
    SLIM("Slim", 0.90f),
    AVERAGE("Average", 1.0f),
    BROAD("Broad", 1.12f)
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
    val equippedGearIds: Map<GearSlot, String> = emptyMap(),
    /** Player-chosen skin tone (index into [SkinTone.entries]); null = derived from the soldier id */
    val skinToneOverride: SkinTone? = null,
    /** Player-chosen build; null = derived from the soldier id */
    val bodyBuildOverride: BodyBuild? = null,
    /** Item 69: what answered soldier events left on this soldier. */
    val eventBuffs: SoldierEventBuffs = SoldierEventBuffs(),
    /** Item 63: when they were captured, when captivity resolves on its own, where, and how often. */
    val capturedAtMillis: Long = 0L,
    val captivityResolveAtMillis: Long = 0L,
    val captureTheater: TheaterRegion? = null,
    val timesCaptured: Int = 0,
    /** Item 63: failed talks so far, when talks can reopen, and whether the prisoner was moved (no rescue). */
    val powTalksFailures: Int = 0,
    val powTalksCooldownUntilMillis: Long = 0L,
    val powRescueClosed: Boolean = false,
    /** Item 62: regions whose language and culture this soldier learned at the Language school. */
    val languageRegions: Set<TheaterRegion> = emptySet(),
    /** Awarded rifle qualification (Marksman, Sharpshooter or Expert); null = not yet qualified */
    val marksmanshipQual: MarksmanshipBadgeTier? = null,
    /** A better qualification waiting for the player to award it */
    val pendingMarksmanshipQual: MarksmanshipBadgeTier? = null,
    /** Score from the most recent qualification */
    val lastQualScore: Int = 0,
    /** 1..5 for WO1..CW5 while [authorizedTier] is WARRANT; 0 otherwise */
    val warrantGrade: Int = 0,
    /** Item 61: 1..4 for BG..GEN once promoted by a general's board; 0 means the rank follows XP. */
    val flagGrade: Int = 0,
    /** Aviator skill, raised by Flight School and Flight Hours. Lowers helicopter insertion risk and time. */
    val flightSkill: Int = 0,
    /** Combat missions flown as the pilot (Flight Hours never count). Drives the Air Medal. */
    val combatSortiesFlown: Int = 0,
    /** Item 39: field combat missions finished after an Airborne or HALO insertion (stars on jump wings). */
    val combatJumps: Int = 0,
    /** Item 40: experience points per stat from missions; gains come from TacticalCalculator.experienceGain. */
    val expPointsFirepower: Int = 0,
    val expPointsStealth: Int = 0,
    val expPointsTech: Int = 0,
    val expPointsResilience: Int = 0,
    /** Unit awards earned with a squad that was later disbanded or left; the soldier keeps wearing them. */
    val retainedUnitAwards: List<UnitAwardType> = emptyList(),
    /** Item 83: flight hours logged in training and on combat sorties. */
    val flightHoursLogged: Int = 0,
    /** Item 52: missions and schools finished since the last Good Conduct award, wound or failed mission. */
    val goodConductStreak: Int = 0,
    /** Item 52: every mission, school and HQ support op this soldier finishes (drives award cooldowns). */
    val serviceActions: Int = 0,
    /** Item 52: [serviceActions] when the last Army Commendation came through the cooldown path. */
    val lastArcomAtAction: Int = -100,
    /** Item 52: [serviceActions] when the last Army Achievement Medal was recommended. */
    val lastAamAtAction: Int = -100
) {
    fun expPoints(stat: StatType): Int = when (stat) {
        StatType.FIREPOWER -> expPointsFirepower
        StatType.STEALTH -> expPointsStealth
        StatType.TECH -> expPointsTech
        StatType.RESILIENCE -> expPointsResilience
    }

    fun withExpPoints(stat: StatType, points: Int): Operator = when (stat) {
        StatType.FIREPOWER -> copy(expPointsFirepower = points)
        StatType.STEALTH -> copy(expPointsStealth = points)
        StatType.TECH -> copy(expPointsTech = points)
        StatType.RESILIENCE -> copy(expPointsResilience = points)
    }

    val isWarrantOfficer: Boolean get() = authorizedTier == RankTier.WARRANT
    val isAviator: Boolean get() = specialty == OperatorSpecialty.AVIATION
    /** Technical warrant officers keep their MOS; aviators are the 153A pilots. */
    val isTechnicalWarrant: Boolean get() = isWarrantOfficer && !isAviator

    /** Skin tone shown on every uniform: the override, or a stable pick from the soldier id */
    val skinTone: SkinTone
        get() = skinToneOverride ?: SkinTone.entries[Math.floorMod(id.hashCode(), SkinTone.entries.size)]

    /** Build shown on every uniform: the override, or a stable pick from the soldier id */
    val bodyBuild: BodyBuild
        get() = bodyBuildOverride ?: BodyBuild.entries[Math.floorMod("$id#build".hashCode(), BodyBuild.entries.size)]

    /** The rank this soldier's XP and boards have earned, before any HQ billet cap. */
    val earnedRank: OperatorRank
        get() = when {
            authorizedTier == RankTier.WARRANT -> OperatorRank.forWarrantGrade(warrantGrade)
            // Item 61: generals hold their grade until the next board
            authorizedTier == RankTier.FLAG_OFFICER && flagGrade > 0 -> OperatorRank.forFlagGrade(flagGrade)
            else -> OperatorRank.forXp(xp, authorizedTier)
        }

    /** Item 107: the HQ billet's top rank for this soldier, if seated in one that has a cap. */
    val hqRankCap: OperatorRank?
        get() = if (status == OperatorStatus.HQ_STAFF) hqRole?.rankCapFor(earnedRank) else null

    /** Displayed rank: what they've earned, held at their HQ billet's top rank while seated there. */
    val rank: OperatorRank
        get() {
            val earned = earnedRank
            // Item 110: a SGM seated as Command Sergeant Major wears the CSM grade
            if (earned == OperatorRank.SGM && status == OperatorStatus.HQ_STAFF && hqRole == HqStaffRole.CSM_COMMAND_SERGEANT_MAJOR) {
                return OperatorRank.CSM
            }
            val cap = hqRankCap ?: return earned
            return if (earned.seniorityIndex > cap.seniorityIndex) cap else earned
        }

    /** True when an HQ billet's cap stops this soldier from rising further. */
    val isAtHqRankCap: Boolean
        get() = hqRankCap?.let { rank.seniorityIndex >= it.seniorityIndex } == true

    val isAtCategoryPromotionGate: Boolean
        get() {
            val next = OperatorRank.nextRank(rank) ?: return false
            return OperatorRank.isBoardGate(rank, next)
        }

    val isReadyForNextCategoryPromotion: Boolean
        get() {
            if (isAtHqRankCap) return false
            val next = OperatorRank.nextRank(rank) ?: return false
            return OperatorRank.isBoardGate(rank, next) && xp >= next.xpRequired
        }

    /** This soldier at [next] rank: XP raised to its floor, tier authorized, warrant grade set. */
    fun promotedTo(next: OperatorRank): Operator = copy(
        xp = maxOf(xp, next.xpRequired),
        authorizedTier = if (next.isWarrant || next.tier.order > authorizedTier.order || authorizedTier == RankTier.WARRANT) next.tier else authorizedTier,
        warrantGrade = next.warrantGrade,
        flagGrade = next.flagGrade
    )
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
    val headgearAuto: Boolean = false,
    /** True only when the player picked these by hand; untouched fields keep Auto or the squad standard */
    val dressTouched: Boolean = false,
    val camoTouched: Boolean = false,
    val glovesTouched: Boolean = false,
    /** True when the player picked "Auto" for these, clearing a saved manual pick */
    val dressAuto: Boolean = false,
    val camoAuto: Boolean = false,
    val glovesAuto: Boolean = false,
    /** Clears this kit's manual coat, headgear, camo and gloves so the squad standard applies again */
    val followSquadStandard: Boolean = false,
    /** Appearance overrides; null = Auto (derived from the soldier id) */
    val skinToneOverride: SkinTone? = null,
    val bodyBuildOverride: BodyBuild? = null
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
            wardrobeKit = kit,
            skinToneOverride = op.skinToneOverride,
            bodyBuildOverride = op.bodyBuildOverride
        )
    }
}

/**
 * Where a mission happens. [terrain] names the ground (camouflage matching in Session 16 uses it) and
 * [accentArgb] gives each theater its own look on mission cards (item 48).
 */
enum class TheaterRegion(val label: String, val terrain: Terrain, val accentArgb: Long) {
    HQ_GROUNDS("FOB Training & Schools", Terrain.GARRISON, 0xFF8A93A0),
    DIPLOMATIC_CORPS("Allied Diplomatic Wing", Terrain.GARRISON, 0xFFB9A46A),
    VERIDIAN_RIDGE("Veridian Ridge", Terrain.WOODLAND, 0xFF5E8C4A),
    KESTREL_STRAIT("Kestrel Strait", Terrain.COASTAL, 0xFF3F8FB5),
    BOREAL_RELAY("Boreal Relay", Terrain.ARCTIC, 0xFFA9D3E8),
    OBSIDIAN_HARBOR("Obsidian Harbor", Terrain.URBAN, 0xFF6F6A80),
    SAHR_EXPANSE("Sahr Expanse", Terrain.DESERT, 0xFFD2A45C),
    VERDANCE_BASIN("Verdance Basin", Terrain.JUNGLE, 0xFF2F9E6B),
    MERIDIAN_CITY("Meridian City", Terrain.URBAN, 0xFFC0784E),
    KARST_PEAKS("Karst Peaks", Terrain.MOUNTAIN, 0xFF9C8FB8),
    TALON_ARCHIPELAGO("Talon Archipelago", Terrain.COASTAL, 0xFF3DB8B0),
    IRONHOLD("Ironhold", Terrain.URBAN, 0xFFB24A4A)
}

/** The ground a theater is fought on. */
enum class Terrain(val label: String) {
    GARRISON("Garrison"),
    WOODLAND("Woodland"),
    COASTAL("Coastal"),
    ARCTIC("Arctic"),
    URBAN("Urban"),
    DESERT("Desert"),
    JUNGLE("Jungle"),
    MOUNTAIN("High Mountain")
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
    /** A specific minimum rank, when the tier alone is too coarse (for example SF School at SPC). */
    val minRank: OperatorRank? = null,
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
    val stageMissionIds: List<String>,
    /** Item 36: Special Forces campaign (Unconventional Warfare); not one of the ten theater campaigns. */
    val isSpecialForces: Boolean = false
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
    /** Target power per soldier: schools compare one soldier to it, missions compare the team's average. */
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
    val contractTag: String? = null,
    /** Item 32: specialties that clearly help on this mission. Empty for general missions (no MOS bonus). */
    val recommendedSpecialties: List<OperatorSpecialty> = emptyList(),
    /** Item 33: how the team can get there. Ground is always allowed. */
    val allowedInsertions: List<InsertionMethod> = listOf(InsertionMethod.GROUND),
    /** Item 35: 1 for normal missions, 2 for platoon missions (two squads plus a platoon leader). */
    val requiredSquads: Int = 1,
    /** Item 60: a school run away from the unit (temporary duty); soldiers show as TDY while there. */
    val isTdy: Boolean = false,
    /** Item 36: Special Forces team mission (exactly 4 soldiers, 3+ SF-qualified). */
    val requiresSfTeam: Boolean = false,
    /** Item 36: Direct Action, Special Reconnaissance, Foreign Internal Defense or Unconventional Warfare. */
    val sfMissionType: String? = null,
    /** Item 62: a clandestine mission (SF squad only, language-qualified, blown-cover risk) in this theater. */
    val isClandestine: Boolean = false,
    /** Item 98: the insertion this mission is built around (about +8% pass). */
    val signatureInsertion: InsertionMethod? = null,
    /** Item 40: stats this mission trains through experience. Empty for schools. */
    val trainsStats: Set<StatType> = emptySet(),
    /** On the water: Maritime insertion is possible (SF missions only). */
    val coastal: Boolean = theater == TheaterRegion.KESTREL_STRAIT || theater == TheaterRegion.OBSIDIAN_HARBOR ||
        theater == TheaterRegion.TALON_ARCHIPELAGO
) {
    /** False for schools, which take any number of eligible soldiers in one class */
    val hasClassCap: Boolean get() = maxOperators != NO_CLASS_CAP

    /** "1-4" for missions, "1+" for schools */
    val teamSizeLabel: String get() = if (hasClassCap) "$minOperators-$maxOperators" else "$minOperators+"

    companion object {
        const val NO_CLASS_CAP = Int.MAX_VALUE
    }
}

/** The four soldier stats, used where a mission trains specific stats (item 40). */
enum class StatType(val label: String, val shortLabel: String) {
    FIREPOWER("Firepower", "FP"),
    STEALTH("Stealth", "ST"),
    TECH("Tech", "TC"),
    RESILIENCE("Resilience", "RS")
}

/** Item 33: how a team reaches the objective. All tuning numbers live in TacticalCalculator. */
enum class InsertionMethod(val label: String, val shortLabel: String) {
    GROUND("Ground / Vehicle", "Ground"),
    HELICOPTER("Helicopter", "Helo"),
    AIRBORNE("Airborne (static line)", "Airborne"),
    HALO("HALO free fall", "HALO"),
    MARITIME("Maritime (combat divers)", "Maritime"),
    /** Item 59: helicopter with the team roping in; Air Assault-qualified teams, Airfield level 3. */
    FAST_ROPE("Fast Rope (Air Assault)", "Fast Rope");

    /** Flown in by an Aegis pilot: uses a pilot, the Airfield and its helicopter limit. */
    val isHelicopter: Boolean get() = this == HELICOPTER || this == FAST_ROPE
}

/** Leadership a soldier's uniform shows: a pin above the nameplate on dress uniforms, a chest tab in the field. */
enum class LeaderRole(val label: String) {
    NONE("None"),
    TEAM_LEADER("Team Leader"),
    SQUAD_LEADER("Squad Leader")
}

/** A gold-framed unit citation earned by a squad on a demanding combat op. */
data class UnitCitation(
    val missionId: String,
    val missionTitle: String,
    val earnedAtMillis: Long,
    /** Squad members at the time; they keep wearing it after transferring out. */
    val memberIds: List<String>,
    /** Item 55: which unit award this is. */
    val type: UnitAwardType = UnitAwardType.MERITORIOUS_UNIT_COMMENDATION
)

/**
 * Item 55: unit awards a squad earns together. Worn on the right chest above the nameplate, one emblem per
 * type in precedence order, with oak leaf clusters for repeats. Colors are the emblem's stripes, edge first.
 */
enum class UnitAwardType(val label: String, val shortLabel: String, val precedence: Int, val stripes: List<Long>, val frameHex: Long) {
    PRESIDENTIAL_UNIT_CITATION("Presidential Unit Citation", "PUC", 1, listOf(0xFF1E4C8E), 0xFFD4AF37),
    VALOROUS_UNIT_AWARD("Valorous Unit Award", "VUA", 2, listOf(0xFFB22234, 0xFFFFFFFF, 0xFF1E4C8E, 0xFFFFFFFF, 0xFFB22234), 0xFFD4AF37),
    MERITORIOUS_UNIT_COMMENDATION("Meritorious Unit Commendation", "MUC", 3, listOf(0xFFC8102E), 0xFFD4AF37),
    SUPERIOR_UNIT_AWARD("Superior Unit Award", "SUA", 4, listOf(0xFFC8102E, 0xFF2E7D32, 0xFFC8102E), 0xFFD4AF37);

    companion object {
        fun fromName(n: String): UnitAwardType? = entries.find { it.name == n }
    }
}

/** A squad can name a Team Leader once it has this many members. */
const val TEAM_LEADER_MIN_SQUAD_SIZE = 5

const val MAX_SQUAD_SIZE = 6
const val MIN_SQUAD_SIZE = 2
/** A squad with at least this many members fights at Full Strength (small survival and odds boost). */
const val FULL_STRENGTH_SQUAD_SIZE = 4
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
    /** Second-in-command for squads of 5+: SGT or above, never the squad leader. */
    val teamLeaderId: String? = null,
    /** Unit citations this squad earned; each lists who was a member when it was earned. */
    val unitCitations: List<UnitCitation> = emptyList(),
    val standardCamo: CamoPattern? = null,
    val standardHeadgear: HeadgearStyle? = null,
    val standardDressStyle: DressUniformStyle? = null,
    val standardBravosHeadgear: HeadgearStyle? = null,
    val standardBravosDressStyle: DressUniformStyle? = null,
    val standardMessDressStyle: DressUniformStyle? = null,
    val standardMessHeadgear: HeadgearStyle? = null,
    val standardAlphasGloves: Boolean? = null,
    val standardMessGloves: Boolean? = null,
    /** Item 58: the command's one Special Forces squad (SF-qualified members only, up to 6). */
    val isSpecialForces: Boolean = false
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
    val successOddsBonusPct: Int,
    val fullStrength: Boolean = false,
    /** Soldiers from another squad joined for this mission: bonus cut 25% for this mission only. */
    val mixedSquads: Boolean = false
)

data class ActiveDeployment(
    val id: String,
    val templateId: String,
    val operatorIds: List<String>,
    val startedAtMillis: Long,
    val durationMillis: Long,
    val efficiencyPct: Int,
    val avgSurvivalPct: Int,
    val squadId: String? = null,
    val insertion: InsertionMethod = InsertionMethod.GROUND,
    /** Helicopter pilot riding this mission; also listed in [operatorIds] so they're marked deployed. */
    val pilotId: String? = null,
    /** Item 35: the officer leading a two-squad mission (one of the team). */
    val platoonLeaderId: String? = null,
    /** Item 50: the mission's name when it launched, kept until it's claimed even if the board rotates. */
    val missionTitle: String? = null,
    /** Item 95: hours booked for a Flight Hours session (1..10); 0 for everything else. */
    val flightHours: Int = 0,
    /** Item 63: the POW a rescue or negotiation is for. */
    val targetOperatorId: String? = null,
    /** Item 62: the region a Language school class studies. */
    val languageRegion: TheaterRegion? = null,
    /** Item 62: when cover blows on a clandestine mission (0 = it holds), whether it has, and whether the team pushed on. */
    val coverBlownAtMillis: Long = 0L,
    val coverBlown: Boolean = false,
    val coverPushOn: Boolean = false
) {
    /** The soldiers on the ground (everyone but the pilot). */
    val groundOperatorIds: List<String> get() = operatorIds.filter { it != pilotId }

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
    STAFF_SUPPORT("HQ Staff Support"),
    /** Item 63 */
    CAPTURED("Captured (POW)")
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
