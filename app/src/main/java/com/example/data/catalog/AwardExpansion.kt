package com.example.data.catalog

import com.example.domain.model.AwardCategory
import com.example.domain.model.MedalDefinition
import com.example.domain.model.MedalTier

/**
 * Session 15 (item 55): more real-Army awards, as game versions. No Reserve or Guard awards and no
 * real-world campaign awards. Which ones repeat, and how, lives in [com.example.domain.AwardRules].
 */
internal object AwardExpansion {
    private fun award(
        id: String, name: String, short: String, category: AwardCategory, tier: MedalTier, precedence: Int,
        description: String, rule: String, stripes: List<Long>, cost: Int, minOps: Int,
        fp: Int, st: Int, tc: Int, rs: Int, yieldPct: Int
    ) = MedalDefinition(
        id = id, name = name, shortName = short, category = category, tier = tier, precedenceOrder = precedence,
        description = description, qualificationRule = rule, ribbonStripeHexes = stripes, prestigeCost = cost,
        minMissionsRequired = minOps, firepowerBonus = fp, stealthBonus = st, techBonus = tc,
        resilienceBonus = rs, yieldBonusPct = yieldPct
    )

    val medals: List<MedalDefinition> = listOf(
        award("medal_distinguished_service", "Army Distinguished Service Medal", "DSM", AwardCategory.MEDAL, MedalTier.SUPREME, 3,
            "Exceptionally meritorious service in a position of great responsibility.",
            "Officers and warrant officers with 10+ ops, for senior HQ duty or leading a campaign to victory. Repeats with oak leaf clusters.",
            listOf(0xFFFFFFFF, 0xFFC8102E, 0xFF1E3A6E, 0xFFC8102E, 0xFFFFFFFF), 30, 10, 3, 2, 5, 4, 14),
        award("medal_legion_merit", "Legion of Merit", "LOM", AwardCategory.MEDAL, MedalTier.SERVICE, 6,
            "Exceptionally meritorious conduct by senior leaders.",
            "SNCOs, officers and warrant officers with 8+ ops, for leading a squad or platoon through a High or Extreme mission, or for HQ duty. Repeats with oak leaf clusters.",
            listOf(0xFFFFFFFF, 0xFF8B1A3A, 0xFF8B1A3A, 0xFF8B1A3A, 0xFFFFFFFF), 22, 8, 2, 2, 4, 3, 11),
        award("medal_flying_cross", "Distinguished Flying Cross", "DFC", AwardCategory.MEDAL, MedalTier.VALOR, 7,
            "Heroism or extraordinary achievement while flying.",
            "Aviators only, through a heroism roll while flying a High or Extreme combat sortie. Repeats with oak leaf clusters.",
            listOf(0xFFC8102E, 0xFFFFFFFF, 0xFF1E3A6E, 0xFF1E3A6E, 0xFFFFFFFF, 0xFFC8102E, 0xFFFFFFFF, 0xFF1E3A6E, 0xFF1E3A6E, 0xFFFFFFFF, 0xFFC8102E), 22, 0, 1, 3, 3, 3, 10),
        award("medal_soldiers", "Soldier's Medal", "SM", AwardCategory.MEDAL, MedalTier.VALOR, 8,
            "Heroism not involving actual conflict with an enemy.",
            "Through a heroism roll at a field school or on a diplomatic or staff op. Repeats with oak leaf clusters.",
            listOf(0xFF1E4C8E, 0xFFFFFFFF, 0xFFC8102E, 0xFFFFFFFF, 0xFFC8102E, 0xFFFFFFFF, 0xFF1E4C8E), 18, 0, 2, 1, 2, 4, 8),
        award("medal_joint_commendation", "Joint Service Commendation Medal", "JSCM", AwardCategory.MEDAL, MedalTier.SERVICE, 13,
            "Meritorious achievement in joint and coalition work.",
            "2+ diplomatic ops; earned on successful Moderate+ diplomatic ops. Repeats with oak leaf clusters.",
            listOf(0xFF2E7D32, 0xFFFFFFFF, 0xFF4FA3D9, 0xFFFFFFFF, 0xFF2E7D32), 10, 0, 1, 1, 3, 2, 6),
        award("medal_joint_achievement", "Joint Service Achievement Medal", "JSAM", AwardCategory.MEDAL, MedalTier.SERVICE, 15,
            "Achievement in joint and diplomatic work.",
            "1+ diplomatic op; earned on successful Low diplomatic ops. Repeats with oak leaf clusters.",
            listOf(0xFFC8102E, 0xFFFFFFFF, 0xFF4FA3D9, 0xFFFFFFFF, 0xFFC8102E), 7, 0, 1, 1, 2, 1, 4),
        award("medal_army_achievement", "Army Achievement Medal", "AAM", AwardCategory.MEDAL, MedalTier.SERVICE, 16,
            "Meritorious service and achievement in smaller wins.",
            "Passing a school, a Low mission or HQ staff work, with a cooldown. Repeats with oak leaf clusters.",
            listOf(0xFF3E8E41, 0xFF1E3A6E, 0xFFFFFFFF, 0xFF1E3A6E, 0xFF3E8E41), 6, 0, 1, 1, 1, 1, 4),
        award("medal_armed_forces_service", "Armed Forces Service Medal", "AFSM", AwardCategory.MEDAL, MedalTier.SERVICE, 21,
            "Service in a military operation outside of combat.",
            "First diplomatic op or HQ staff support op. One time.",
            listOf(0xFF2E7D32, 0xFF1E4C8E, 0xFFF2C94C, 0xFF1E4C8E, 0xFF2E7D32), 5, 0, 0, 1, 1, 1, 3),
        award("medal_humanitarian", "Humanitarian Service Medal", "HSM", AwardCategory.MEDAL, MedalTier.SERVICE, 22,
            "Relief and partner-force support to people in need.",
            "Foreign Internal Defense and relief missions. A bronze service star for each different mission.",
            listOf(0xFF1E3A6E, 0xFF7B4FA0, 0xFFFFFFFF, 0xFF7B4FA0, 0xFF1E3A6E), 5, 0, 0, 0, 1, 2, 3),
        award("medal_overseas_service", "Overseas Service Ribbon", "OSR", AwardCategory.RIBBON, MedalTier.SERVICE, 26,
            "Service in a theater beyond the home theaters.",
            "First field mission in Sahr Expanse, Verdance Basin, Meridian City, Karst Peaks, Talon Archipelago or Ironhold. One time.",
            listOf(0xFF1E3A6E, 0xFFF2C94C, 0xFFC8102E, 0xFFF2C94C, 0xFF1E3A6E), 3, 0, 1, 1, 0, 1, 2),
        award("medal_nato", "Allied Coalition Medal", "ACM", AwardCategory.MEDAL, MedalTier.SERVICE, 27,
            "Service on coalition diplomatic stages of a campaign (game version of the NATO Medal).",
            "Diplomatic campaign stages. A bronze service star for each different stage.",
            listOf(0xFF14306E, 0xFF14306E, 0xFF14306E, 0xFFFFFFFF, 0xFF14306E, 0xFF14306E, 0xFFFFFFFF, 0xFF14306E, 0xFF14306E, 0xFF14306E), 6, 0, 1, 1, 2, 1, 4),
        award("medal_un", "International Peacekeeping Medal", "IPM", AwardCategory.MEDAL, MedalTier.SERVICE, 28,
            "Service on peacekeeping and diplomatic missions (game version of the UN Medal).",
            "Single diplomatic missions. A bronze service star for each different mission.",
            listOf(0xFFFFFFFF, 0xFF5B92E5, 0xFF5B92E5, 0xFF5B92E5, 0xFF5B92E5, 0xFF5B92E5, 0xFF5B92E5, 0xFF5B92E5, 0xFFFFFFFF), 6, 0, 0, 1, 2, 1, 4)
    )

    /**
     * Item 55: real-Army precedence for every medal and ribbon (new and existing). Cords and school
     * insignia move to 100+ so they always sort after the ribbon rack.
     */
    val precedence: Map<String, Int> = mapOf(
        "medal_sovereign_star" to 1, "medal_distinguished_cross" to 2, "medal_distinguished_service" to 3,
        "medal_silver_vanguard" to 4, "medal_defense_superior" to 5, "medal_legion_merit" to 6,
        "medal_flying_cross" to 7, "medal_soldiers" to 8, "medal_bronze_valor" to 9, "medal_purple_shield" to 10,
        "medal_meritorious" to 11, "medal_air_medal" to 12, "medal_joint_commendation" to 13,
        "medal_army_commendation" to 14, "medal_joint_achievement" to 15, "medal_army_achievement" to 16, "medal_pow" to 17,
        "medal_good_conduct" to 18, "medal_national_defense" to 19, "medal_expeditionary" to 20,
        "medal_armed_forces_service" to 21, "medal_humanitarian" to 22, "medal_nco_dev" to 23,
        // Item 157: Army Service and Overseas Service ribbons follow the NCO ribbon; the game's own training
        // ribbons (SERE, Night Ops) come after them; international medals last, UN before NATO
        "medal_basic_training" to 24, "medal_overseas_service" to 25, "medal_sere" to 26, "medal_night_owl" to 27,
        "medal_un" to 28, "medal_nato" to 29,
        // Item 157: cords, in one block after the ribbon rack
        "cord_general_aiguillette" to 101, "cord_valor_fourragere" to 102, "cord_sf_emerald" to 103,
        "cord_airborne_crimson" to 104, "cord_aviation" to 105, "cord_infantry_blue" to 106,
        "cord_engineer_scarlet" to 107, "cord_signals_orange" to 108, "cord_intel_teal" to 109,
        "cord_medical_maroon" to 110, "cord_logistics_buff" to 111,
        // Item 157: tabs, combat and expert badges, aviation, parachutist and skill badges, then crests
        "insig_sf_tab" to 121, "insig_ranger_tab" to 122, "insig_sapper_tab" to 123, "insig_airborne_tab" to 124,
        "insig_cib" to 125, "insig_combat_medic" to 126, "insig_cab" to 127, "insig_eib_rifle" to 128,
        "insig_cmb_medic" to 129, "insig_master_aviator_wings" to 130, "insig_senior_aviator_wings" to 131,
        "insig_aviator_wings" to 132, "insig_master_para" to 133, "insig_mff_wings" to 134, "insig_parachutist" to 135,
        "insig_combat_diver" to 136, "insig_air_assault" to 137, "insig_pathfinder" to 138, "insig_eod_badge" to 139,
        "insig_sniper" to 140, "insig_mountain" to 141, "insig_jungle" to 142, "insig_arctic" to 143,
        "insig_staff_crest" to 144, "insig_intel_crest" to 145, "insig_cyber_crest" to 146, "insig_logistics_crest" to 147
    )

    fun withPrecedence(m: MedalDefinition): MedalDefinition =
        precedence[m.id]?.let { m.copy(precedenceOrder = it) }
            ?: if (m.category == AwardCategory.SHOULDER_CORD || m.category == AwardCategory.SCHOOL_INSIGNIA) {
                m.copy(precedenceOrder = m.precedenceOrder + 200)
            } else m
}
