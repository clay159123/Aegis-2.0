package com.example.domain

import com.example.data.catalog.GameCatalog
import com.example.domain.model.AwardCategory
import com.example.domain.model.AwardedMedal
import com.example.domain.model.HqStaffRole
import com.example.domain.model.InsigniaShape
import com.example.domain.model.MedalDefinition
import com.example.domain.model.MissionRisk
import com.example.domain.model.MissionScope
import com.example.domain.model.MissionTemplate
import com.example.domain.model.Operator
import com.example.domain.model.OperatorOutcomeType
import com.example.domain.model.TheaterRegion
import kotlin.random.Random

/**
 * Session 15 award rules: chance-based valor (item 89), repeat awards from routine service (item 52),
 * cords that come with their award (item 53), award groups (item 54), and the new awards (item 55).
 */
object AwardRules {
    const val DSC_ID = "medal_distinguished_cross"
    const val SILVER_VANGUARD_ID = "medal_silver_vanguard"
    const val BRONZE_VALOR_ID = "medal_bronze_valor"
    const val VALOR_CORD_ID = "cord_valor_fourragere"
    const val DSM_ID = "medal_distinguished_service"
    const val LEGION_OF_MERIT_ID = "medal_legion_merit"
    const val DFC_ID = "medal_flying_cross"
    const val SOLDIERS_MEDAL_ID = "medal_soldiers"
    const val JSCM_ID = "medal_joint_commendation"
    const val JSAM_ID = "medal_joint_achievement"
    const val AAM_ID = "medal_army_achievement"
    const val ARCOM_ID = "medal_army_commendation"
    const val GOOD_CONDUCT_ID = "medal_good_conduct"
    const val AFSM_ID = "medal_armed_forces_service"
    const val HUMANITARIAN_ID = "medal_humanitarian"
    const val OVERSEAS_SERVICE_ID = "medal_overseas_service"
    const val NATO_ID = "medal_nato"
    const val UN_ID = "medal_un"
    const val AIGUILLETTE_ID = "cord_general_aiguillette"

    // Item 89: valor odds as multiples of the Sovereign Star heroism odds, High and Extreme field ops only
    const val SERVICE_CROSS_ODDS_MULTIPLIER = 3.0
    const val SILVER_VANGUARD_ODDS_MULTIPLIER = 5.0
    const val BRONZE_VALOR_ODDS_MULTIPLIER = 10.0
    /** A wound or a failed mission each raise the valor odds a little. */
    const val VALOR_WOUNDED_MULTIPLIER = 1.25
    const val VALOR_FAILED_MISSION_MULTIPLIER = 1.25
    /** Pilot of a High or Extreme combat sortie: DFC odds as a multiple of the heroism odds. */
    const val DFC_ODDS_MULTIPLIER = 4.0
    const val DFC_CASUALTY_MULTIPLIER = 1.5
    /** Heroism outside combat, per soldier at field schools and on diplomatic or staff ops. */
    const val SOLDIERS_MEDAL_ODDS = 0.015

    // Item 52: routine service awards
    const val GOOD_CONDUCT_STREAK = 6
    const val ARCOM_COOLDOWN_ACTIONS = 4
    const val ARCOM_CHANCE = 0.40
    const val AAM_COOLDOWN_ACTIONS = 3
    const val AAM_CHANCE = 0.50
    /** A school this hard (base pass rate at or below) counts as a strong result for the ARCOM. */
    const val HARD_SCHOOL_PASS_RATE = 70
    const val JSCM_CHANCE = 0.35
    const val JSAM_CHANCE = 0.50
    const val SENIOR_LEADER_AWARD_CHANCE = 0.20
    const val DSM_STAFF_CHANCE = 0.12

    /** Awards earned only through a roll on a mission, never by meeting a checklist. */
    val ROLL_ONLY_IDS: Set<String> = setOf(DSC_ID, SILVER_VANGUARD_ID, BRONZE_VALOR_ID, DFC_ID, SOLDIERS_MEDAL_ID)

    /** Valor awards; the valor cord follows them. */
    val VALOR_AWARD_IDS: Set<String> = setOf(
        TacticalCalculator.SOVEREIGN_STAR_ID, DSC_ID, SILVER_VANGUARD_ID, BRONZE_VALOR_ID, DFC_ID, SOLDIERS_MEDAL_ID
    )

    /** The six theaters beyond the home theaters (Overseas Service Ribbon). */
    val OVERSEAS_THEATERS: Set<TheaterRegion> = setOf(
        TheaterRegion.SAHR_EXPANSE, TheaterRegion.VERDANCE_BASIN, TheaterRegion.MERIDIAN_CITY,
        TheaterRegion.KARST_PEAKS, TheaterRegion.TALON_ARCHIPELAGO, TheaterRegion.IRONHOLD
    )

    /** Foreign Internal Defense and relief missions (Humanitarian Service Medal). */
    val HUMANITARIAN_MISSION_IDS: Set<String> = setOf(
        "msn_sf_fid_partner", "msn_sf_fid_coastal", "msn_sa_medevac_lift", "msn_x_well_bombing",
        "msn_x_crash_recovery", "camp_verdance_1", "camp_verdance_3", "camp_karst_2", "camp_sahr_2"
    )

    private fun isDiplomaticCampaignStage(m: MissionTemplate) =
        m.scope == MissionScope.STAFF_DIPLOMATIC && m.campaignId != null

    private fun isDiplomaticSingle(m: MissionTemplate) =
        m.scope == MissionScope.STAFF_DIPLOMATIC && m.campaignId == null

    /** How many different missions of each campaign-style award this soldier has completed. */
    fun starAwardMissionCount(op: Operator, medalId: String): Int {
        val missions = op.completedMissionIds.mapNotNull { id -> GameCatalog.missions.find { it.id == id } }
        return when (medalId) {
            HUMANITARIAN_ID -> missions.count { it.id in HUMANITARIAN_MISSION_IDS }
            NATO_ID -> missions.count { isDiplomaticCampaignStage(it) }
            UN_ID -> missions.count { isDiplomaticSingle(it) }
            else -> 0
        }
    }

    // ============================================================
    // Item 53: cords that come with their award
    // ============================================================

    private val companionCords: Map<String, String> = mapOf(
        "insig_sf_tab" to "cord_sf_emerald",
        "insig_parachutist" to "cord_airborne_crimson",
        "insig_airborne_tab" to "cord_airborne_crimson",
        "insig_eod_badge" to "cord_engineer_scarlet",
        "insig_sapper_tab" to "cord_engineer_scarlet",
        "insig_cyber_crest" to "cord_signals_orange",
        "insig_intel_crest" to "cord_intel_teal",
        "insig_cmb_medic" to "cord_medical_maroon",
        "insig_combat_medic" to "cord_medical_maroon",
        "insig_logistics_crest" to "cord_logistics_buff",
        "insig_aviator_wings" to "cord_aviation",
        "insig_cib" to "cord_infantry_blue",
        "insig_eib_rifle" to "cord_infantry_blue"
    ) + VALOR_AWARD_IDS.associateWith { VALOR_CORD_ID }

    /** The cord granted automatically with this award, if it has one. */
    fun companionCordFor(medalId: String): String? = companionCords[medalId]

    // ============================================================
    // Item 89: valor rolls
    // ============================================================

    /**
     * The valor award one soldier earns on this mission for [roll] in 0..1, or null. High and Extreme field
     * ops only. Odds are multiples of the Sovereign Star's: Service Cross 3x, Silver Vanguard 5x, Bronze
     * Valor 10x. A wound, a failed mission and a fallen soldier each raise them.
     */
    fun valorAwardFor(mission: MissionTemplate, outcome: OperatorOutcomeType, missionSucceeded: Boolean, roll: Double): String? {
        if (mission.scope != MissionScope.FIELD_COMBAT) return null
        if (mission.risk != MissionRisk.HIGH && mission.risk != MissionRisk.EXTREME) return null
        if (outcome != OperatorOutcomeType.SAFE && outcome != OperatorOutcomeType.WOUNDED && outcome != OperatorOutcomeType.FALLEN) return null
        val base = if (mission.risk == MissionRisk.EXTREME) TacticalCalculator.HEROISM_ODDS_EXTREME else TacticalCalculator.HEROISM_ODDS_HIGH
        var mult = 1.0
        if (outcome == OperatorOutcomeType.FALLEN) mult *= TacticalCalculator.HEROISM_FALLEN_MULTIPLIER
        if (outcome == OperatorOutcomeType.WOUNDED) mult *= VALOR_WOUNDED_MULTIPLIER
        if (!missionSucceeded) mult *= VALOR_FAILED_MISSION_MULTIPLIER
        return when {
            roll < base * SERVICE_CROSS_ODDS_MULTIPLIER * mult -> DSC_ID
            roll < base * SILVER_VANGUARD_ODDS_MULTIPLIER * mult -> SILVER_VANGUARD_ID
            roll < base * BRONZE_VALOR_ODDS_MULTIPLIER * mult -> BRONZE_VALOR_ID
            else -> null
        }
    }

    /** Odds the pilot of this sortie earns the Distinguished Flying Cross. */
    fun dfcOdds(mission: MissionTemplate, anyCasualty: Boolean): Double {
        if (mission.scope != MissionScope.FIELD_COMBAT) return 0.0
        val base = when (mission.risk) {
            MissionRisk.HIGH -> TacticalCalculator.HEROISM_ODDS_HIGH
            MissionRisk.EXTREME -> TacticalCalculator.HEROISM_ODDS_EXTREME
            else -> return 0.0
        }
        return base * DFC_ODDS_MULTIPLIER * if (anyCasualty) DFC_CASUALTY_MULTIPLIER else 1.0
    }

    /** Field schools where a training accident can call for heroism (Soldier's Medal). */
    private val fieldSchoolIds = setOf(
        "msn_night_qual", "msn_ranger_school", "msn_sapper_school", "msn_sf_school", "msn_pathfinder_school",
        TacticalCalculator.MFF_SCHOOL_ID, TacticalCalculator.COMBAT_DIVER_SCHOOL_ID, TacticalCalculator.FLIGHT_SCHOOL_MISSION_ID
    )

    /** Odds of a Soldier's Medal for one soldier on this mission. */
    fun soldiersMedalOdds(mission: MissionTemplate, outcome: OperatorOutcomeType): Double = when {
        outcome == OperatorOutcomeType.FALLEN -> 0.0
        mission.scope == MissionScope.TRAINING && mission.id in fieldSchoolIds -> SOLDIERS_MEDAL_ODDS
        mission.scope == MissionScope.STAFF_DIPLOMATIC -> SOLDIERS_MEDAL_ODDS
        else -> 0.0
    }

    fun heroismText(medalId: String, mission: MissionTemplate, outcome: OperatorOutcomeType): String {
        val name = GameCatalog.medals.find { it.id == medalId }?.name ?: "a valor award"
        return when {
            medalId == DFC_ID -> "Flew through heavy fire on ${mission.title} to put the team in and bring them out. Recommended for the $name."
            medalId == SOLDIERS_MEDAL_ID -> "Risked their life to save others during ${mission.title}, away from enemy fire. Recommended for the $name."
            outcome == OperatorOutcomeType.FALLEN -> "Fell in action on ${mission.title} after holding the line for the team. Recommended for the $name."
            outcome == OperatorOutcomeType.WOUNDED -> "Kept fighting on ${mission.title} after being wounded and got the team through. Recommended for the $name."
            else -> "Acted with gallantry under fire on ${mission.title}. Recommended for the $name."
        }
    }

    // ============================================================
    // Item 52 and 55: routine service awards after each mission, school or staff op
    // ============================================================

    data class ServiceAwardResult(val operator: Operator, val medalIds: List<String>)

    /**
     * Advances this soldier's service record after one mission, school or HQ support op and returns the
     * routine awards it earned (each only once per call). [op] already has this mission's results applied.
     * [held] counts awards already held; [pending] lists awards already waiting.
     */
    fun serviceAwardsFor(
        op: Operator,
        mission: MissionTemplate,
        outcome: OperatorOutcomeType,
        missionSucceeded: Boolean,
        held: Map<String, Int>,
        pending: Set<String>,
        rng: Random,
        isSquadOrPlatoonLeader: Boolean = false
    ): ServiceAwardResult {
        if (outcome == OperatorOutcomeType.FALLEN) return ServiceAwardResult(op, emptyList())
        val awards = mutableListOf<String>()
        fun eligible(id: String): Boolean {
            if (id in pending || id in awards) return false
            if ((held[id] ?: 0) >= (TacticalCalculator.perSoldierCapForAward(id) ?: Int.MAX_VALUE)) return false
            val medal = GameCatalog.medals.find { it.id == id } ?: return false
            val heldIds = held.filterValues { it > 0 }.keys
            return TacticalCalculator.evaluateAwardEligibility(medal, op, heldIds).eligible
        }
        fun firstTime(id: String) = (held[id] ?: 0) == 0 && eligible(id)

        val actions = op.serviceActions + 1
        val isSchool = mission.scope == MissionScope.TRAINING
        val isStaff = outcome == OperatorOutcomeType.STAFF_SUPPORT
        val passedSchool = outcome == OperatorOutcomeType.TRAINING_PASSED
        val succeeded = missionSucceeded && outcome != OperatorOutcomeType.TRAINING_FAILED

        // Good Conduct: every N missions or schools with no wound and no failed mission since the last one
        var streak = when {
            outcome == OperatorOutcomeType.WOUNDED -> 0
            !isSchool && !missionSucceeded && !isStaff -> 0
            else -> op.goodConductStreak + 1
        }
        if (streak >= GOOD_CONDUCT_STREAK && eligible(GOOD_CONDUCT_ID)) {
            awards += GOOD_CONDUCT_ID
            streak = 0
        }

        // ARCOM for strong results; AAM for smaller wins. Both on cooldowns.
        var lastArcom = op.lastArcomAtAction
        var lastAam = op.lastAamAtAction
        val strong = (passedSchool && (mission.schoolRequirements?.basePassRatePct ?: 100) <= HARD_SCHOOL_PASS_RATE) ||
            (!isSchool && !isStaff && succeeded && mission.risk.baseDangerPenalty >= MissionRisk.MODERATE.baseDangerPenalty)
        val small = passedSchool || isStaff || (!isSchool && succeeded && mission.risk == MissionRisk.LOW)
        if (strong && actions - lastArcom >= ARCOM_COOLDOWN_ACTIONS && rng.nextDouble() < ARCOM_CHANCE && eligible(ARCOM_ID)) {
            awards += ARCOM_ID
            lastArcom = actions
        } else if (small && actions - lastAam >= AAM_COOLDOWN_ACTIONS && rng.nextDouble() < AAM_CHANCE && eligible(AAM_ID)) {
            awards += AAM_ID
            lastAam = actions
        }

        if (succeeded) {
            // One-time service awards
            if (mission.scope == MissionScope.FIELD_COMBAT && mission.theater in OVERSEAS_THEATERS && firstTime(OVERSEAS_SERVICE_ID)) {
                awards += OVERSEAS_SERVICE_ID
            }
            if ((mission.scope == MissionScope.STAFF_DIPLOMATIC || isStaff) && firstTime(AFSM_ID)) awards += AFSM_ID

            // Joint service awards on diplomatic work
            if (mission.scope == MissionScope.STAFF_DIPLOMATIC && !isStaff) {
                if (mission.risk == MissionRisk.LOW) {
                    if (rng.nextDouble() < JSAM_CHANCE && eligible(JSAM_ID)) awards += JSAM_ID
                } else if (rng.nextDouble() < JSCM_CHANCE && eligible(JSCM_ID)) {
                    awards += JSCM_ID
                }
            }

            // Campaign-style awards: one service star per different qualifying mission
            for (id in listOf(HUMANITARIAN_ID, NATO_ID, UN_ID)) {
                if (isStaff) break
                if ((held[id] ?: 0) < starAwardMissionCount(op, id) && eligible(id)) awards += id
            }

            // Senior leaders: Legion of Merit for leading through a High or Extreme mission
            val hardMission = mission.risk == MissionRisk.HIGH || mission.risk == MissionRisk.EXTREME
            if (isSquadOrPlatoonLeader && hardMission && mission.scope == MissionScope.FIELD_COMBAT &&
                rng.nextDouble() < SENIOR_LEADER_AWARD_CHANCE && eligible(LEGION_OF_MERIT_ID)
            ) awards += LEGION_OF_MERIT_ID

            // HQ duty: senior staff may earn the DSM or the Legion of Merit
            if (isStaff) {
                val seniorBillet = op.hqRole in setOf(
                    HqStaffRole.CO_COMMANDING_OFFICER, HqStaffRole.XO_CHIEF_OF_STAFF, HqStaffRole.DEPUTY_COMMANDER
                )
                if (seniorBillet && rng.nextDouble() < DSM_STAFF_CHANCE && eligible(DSM_ID)) {
                    awards += DSM_ID
                } else if (rng.nextDouble() < SENIOR_LEADER_AWARD_CHANCE / 2 && eligible(LEGION_OF_MERIT_ID)) {
                    awards += LEGION_OF_MERIT_ID
                }
            }
        }

        return ServiceAwardResult(
            op.copy(
                serviceActions = actions,
                goodConductStreak = streak,
                lastArcomAtAction = lastArcom,
                lastAamAtAction = lastAam
            ),
            awards
        )
    }

    /** Citation text for a routine service award. */
    fun serviceDeedText(medalId: String, mission: MissionTemplate): String = when (medalId) {
        GOOD_CONDUCT_ID -> "Six straight missions and schools without a wound or a failed mission, most recently ${mission.title}."
        ARCOM_ID -> "Strong performance on ${mission.title}."
        AAM_ID -> "Solid work on ${mission.title}."
        AFSM_ID -> "First service on a diplomatic or HQ support operation: ${mission.title}."
        OVERSEAS_SERVICE_ID -> "First field mission beyond the home theaters: ${mission.title} in ${mission.theater.label}."
        JSAM_ID -> "Represented the command well in joint diplomatic work on ${mission.title}."
        JSCM_ID -> "Distinguished joint and coalition work on ${mission.title}."
        HUMANITARIAN_ID -> "Relief and partner-force support on ${mission.title}."
        NATO_ID -> "Coalition service on ${mission.title}."
        UN_ID -> "Peacekeeping and diplomatic service on ${mission.title}."
        LEGION_OF_MERIT_ID -> "Led the team through ${mission.title} with exceptional judgment."
        DSM_ID -> "Exceptional senior command service supporting ${mission.title}."
        else -> "Earned on ${mission.title}."
    }

    // ============================================================
    // Eligibility for the new awards (called from evaluateAwardEligibility)
    // ============================================================

    val NEW_AWARD_IDS: Set<String> = setOf(
        DSM_ID, LEGION_OF_MERIT_ID, DFC_ID, SOLDIERS_MEDAL_ID, JSCM_ID, JSAM_ID, AAM_ID, AFSM_ID,
        HUMANITARIAN_ID, OVERSEAS_SERVICE_ID, NATO_ID, UN_ID
    )

    fun eligibilityGaps(
        medalId: String,
        op: Operator,
        heroismCited: Boolean,
        heldCount: Int
    ): List<String> {
        val missing = mutableListOf<String>()
        val careerOps = op.missionsCompleted + op.staffOpsSupported
        val diploOps = op.completedMissionIds.count { id ->
            GameCatalog.missions.find { it.id == id }?.scope == MissionScope.STAFF_DIPLOMATIC
        }
        when (medalId) {
            DSM_ID -> {
                if (op.rank.tier.isEnlisted) missing += "Officers and warrant officers only"
                if (careerOps < 10) missing += "Complete 10+ ops ($careerOps/10)"
            }
            LEGION_OF_MERIT_ID -> {
                if (op.rank.tier.order < com.example.domain.model.RankTier.SNCO.order) missing += "SNCO, officer or warrant officer only"
                if (careerOps < 8) missing += "Complete 8+ ops ($careerOps/8)"
            }
            DFC_ID -> {
                if (!op.isAviator) missing += "Aviators only"
                if (!heroismCited) missing += "Needs a heroism citation from a combat sortie"
            }
            SOLDIERS_MEDAL_ID -> if (!heroismCited) missing += "Needs a heroism citation from a school or staff op"
            JSCM_ID -> if (diploOps < 2) missing += "Complete 2+ diplomatic ops ($diploOps/2)"
            JSAM_ID -> if (diploOps < 1) missing += "Complete a diplomatic op"
            AAM_ID -> if (careerOps < 1 && op.completedMissionIds.isEmpty()) missing += "Complete a mission, school or HQ support op"
            AFSM_ID -> if (diploOps < 1 && op.staffOpsSupported < 1) missing += "Complete a diplomatic op or HQ support op"
            OVERSEAS_SERVICE_ID -> {
                val overseas = op.completedMissionIds.any { id ->
                    GameCatalog.missions.find { it.id == id }?.let { it.scope == MissionScope.FIELD_COMBAT && it.theater in OVERSEAS_THEATERS } == true
                }
                if (!overseas) missing += "Complete a field mission in a theater beyond the home theaters"
            }
            HUMANITARIAN_ID, NATO_ID, UN_ID -> {
                val count = starAwardMissionCount(op, medalId)
                if (count == 0) missing += when (medalId) {
                    HUMANITARIAN_ID -> "Complete a relief or Foreign Internal Defense mission"
                    NATO_ID -> "Complete a diplomatic campaign stage"
                    else -> "Complete a single diplomatic mission"
                } else if (heldCount >= count) {
                    missing += "One per different mission ($heldCount/$count held)"
                }
            }
        }
        return missing
    }

    // ============================================================
    // Item 54: award groups on the Uniform tab
    // ============================================================

    enum class AwardGroup(val label: String) {
        VALOR("Valor"),
        SERVICE_MEDALS("Service Medals"),
        RIBBONS("Ribbons"),
        UNIT_AWARDS("Unit Awards"),
        TABS("Tabs"),
        BADGES("Badges"),
        CORDS("Cords")
    }

    private val valorGroupIds = VALOR_AWARD_IDS + "medal_purple_shield"

    fun groupFor(medal: MedalDefinition): AwardGroup = when {
        medal.id in valorGroupIds -> AwardGroup.VALOR
        medal.category == AwardCategory.MEDAL -> AwardGroup.SERVICE_MEDALS
        medal.category == AwardCategory.RIBBON -> AwardGroup.RIBBONS
        medal.category == AwardCategory.SHOULDER_CORD -> AwardGroup.CORDS
        medal.insigniaShape == InsigniaShape.TAB -> AwardGroup.TABS
        else -> AwardGroup.BADGES
    }

    /**
     * Every award this soldier doesn't hold yet and qualifies for right now, in an order that lets chained
     * awards come through (jump wings before Master Parachutist). Roll-only awards count only when a
     * citation is waiting for them. Used by Award All Eligible on the Uniform tab.
     */
    fun eligibleFirstTimeAwards(op: Operator, citations: List<AwardedMedal>, pendingMedalIds: Set<String>): List<MedalDefinition> {
        val owned = citations.filter { it.operatorId == op.id }.map { it.medalId }.toMutableSet()
        val picks = mutableListOf<MedalDefinition>()
        var changed = true
        while (changed) {
            changed = false
            for (medal in GameCatalog.medals.sortedBy { it.precedenceOrder }) {
                if (medal.id in owned) continue
                val cited = medal.id in pendingMedalIds
                if (TacticalCalculator.evaluateAwardEligibility(medal, op, owned, citations, heroismCited = cited).eligible) {
                    owned += medal.id
                    picks += medal
                    changed = true
                }
            }
        }
        return picks
    }

    /** Held count per award for one soldier. */
    fun heldCounts(operatorId: String, citations: List<AwardedMedal>): Map<String, Int> =
        citations.filter { it.operatorId == operatorId }.groupingBy { it.medalId }.eachCount()
}
