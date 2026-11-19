package com.example.domain

import com.example.data.catalog.GameCatalog
import com.example.domain.model.*
import kotlin.random.Random

/**
 * Item 62: language and culture school, clandestine missions and blown cover.
 * Item 98: Op Free Strait, the 7-stage SF campaign (signature insertions, the guerrilla force, the completion perk).
 */
object ClandestineRules {
    const val LANGUAGE_SCHOOL_ID = "msn_language_school"
    const val FREE_STRAIT_ID = TacticalCalculator.UW_CAMPAIGN_ID

    /** The regions a language can be learned for: the theaters of the ten campaigns. */
    val languageRegions: List<TheaterRegion>
        get() = GameCatalog.campaigns.filter { !it.isSpecialForces }.map { it.theater }.distinct()

    fun knowsLanguage(op: Operator, region: TheaterRegion): Boolean = region in op.languageRegions

    /** Regions this soldier hasn't learned yet. */
    fun regionsToLearn(op: Operator): List<TheaterRegion> = languageRegions.filter { it !in op.languageRegions }

    // ---------- Clandestine teams ----------

    /** At least this many on the team must speak the region's language (they run the cover). */
    const val MIN_LANGUAGE_QUALIFIED = 2

    /** Why this team can't run a clandestine mission, or null. All must be in the SF squad. */
    fun teamBlocker(mission: MissionTemplate, team: List<Operator>, squads: List<TacticalSquad>): String? {
        if (!mission.isClandestine) return null
        val sf = TacticalCalculator.sfSquadOf(squads) ?: return "Form an SF squad first"
        team.firstOrNull { it.id !in sf.memberIds }?.let { return "Clandestine missions are SF squad only (${it.callsign} isn't in ${sf.name})" }
        val speakers = team.count { knowsLanguage(it, mission.theater) }
        if (speakers < MIN_LANGUAGE_QUALIFIED) {
            return "Needs $MIN_LANGUAGE_QUALIFIED+ soldiers who speak the ${mission.theater.label} language ($speakers/$MIN_LANGUAGE_QUALIFIED)"
        }
        return null
    }

    // ---------- Blown cover ----------

    const val BASE_BLOWN_COVER_PCT = 40
    /** Each point of the team's average Stealth and Tech above 20 lowers the risk this much. */
    const val STAT_CUT_PER_POINT = 1.5
    const val LANGUAGE_CUT_PCT = 6
    const val FREE_STRAIT_CUT_PCT = 10
    const val MIN_BLOWN_COVER_PCT = 5
    const val MAX_BLOWN_COVER_PCT = 75
    /** A blown cover leaves a very hard mission's length on the clock, and costs this much pass chance. */
    const val VERY_HARD_SECONDS = 300
    const val BLOWN_COVER_PASS_PENALTY = 15
    /** Aborting after a blown cover: a quicker exfil, so casualty odds are halved. */
    const val EXFIL_CASUALTY_FACTOR = 0.5

    fun blownCoverChancePct(mission: MissionTemplate, team: List<Operator>, clearedCampaignIds: Set<String> = TacticalCalculator.clearedCampaignIds): Int {
        if (!mission.isClandestine || team.isEmpty()) return 0
        val avgStealthTech = team.map { (it.baseStealth + it.baseTech) / 2.0 }.average()
        val statCut = ((avgStealthTech - 20).coerceAtLeast(0.0) * STAT_CUT_PER_POINT).toInt()
        val languageCut = team.count { knowsLanguage(it, mission.theater) } * LANGUAGE_CUT_PCT
        val perk = if (FREE_STRAIT_ID in clearedCampaignIds) FREE_STRAIT_CUT_PCT else 0
        return (BASE_BLOWN_COVER_PCT - statCut - languageCut - perk).coerceIn(MIN_BLOWN_COVER_PCT, MAX_BLOWN_COVER_PCT)
    }

    /** When cover will blow (25% to 75% of the way in), or 0 if it holds. Rolled at launch. */
    fun rollCoverBlownAt(chancePct: Int, startedAt: Long, durationMillis: Long, random: Random = Random): Long {
        if (random.nextInt(100) >= chancePct) return 0L
        return startedAt + (durationMillis * (0.25 + random.nextDouble() * 0.5)).toLong()
    }

    /** Cover is blown: the clock drops to a very hard mission's length (never longer than what's left). */
    fun blowCover(d: ActiveDeployment, now: Long): ActiveDeployment {
        val left = (d.startedAtMillis + d.durationMillis - now).coerceAtLeast(0L)
        val newLeft = minOf(left, GameTiming.seconds(VERY_HARD_SECONDS.toLong()) * 1000L)
        return d.copy(coverBlown = true, durationMillis = (now - d.startedAtMillis) + newLeft)
    }

    fun coverBlowsNow(d: ActiveDeployment, now: Long): Boolean = !d.coverBlown && d.coverBlownAtMillis in 1..now

    // ---------- Op Free Strait ----------

    const val SIGNATURE_INSERTION_BONUS_PCT = 8
    const val GUERRILLA_PASS_PER_STAGE = 2
    const val GUERRILLA_SURVIVAL_PER_STAGE = 1
    /** Completion perk: +5% on every SF mission (and lower blown-cover risk, above). */
    const val FREE_STRAIT_SF_PASS_BONUS = 5
    const val PRISON_CAMP_STAGE_ID = "camp_uw_6"

    /** Cleared Op Free Strait stages. Set from game state. */
    @Volatile var clearedStageIds: Set<String> = emptySet()

    /** The guerrilla force: one company per cleared stage of Op Free Strait. */
    fun guerrillaStrength(cleared: Set<String> = clearedStageIds): Int =
        GameCatalog.campaigns.firstOrNull { it.id == FREE_STRAIT_ID }?.stageMissionIds?.count { it in cleared } ?: 0

    fun guerrillaPassBonus(mission: MissionTemplate): Int =
        if (mission.campaignId == FREE_STRAIT_ID) guerrillaStrength() * GUERRILLA_PASS_PER_STAGE else 0

    fun guerrillaSurvivalBonus(mission: MissionTemplate): Int =
        if (mission.campaignId == FREE_STRAIT_ID) guerrillaStrength() * GUERRILLA_SURVIVAL_PER_STAGE else 0

    fun signatureBonus(mission: MissionTemplate, insertion: InsertionMethod): Int =
        if (mission.signatureInsertion != null && mission.signatureInsertion == insertion) SIGNATURE_INSERTION_BONUS_PCT else 0

    fun freeStraitSfBonus(mission: MissionTemplate, cleared: Set<String> = TacticalCalculator.clearedCampaignIds): Int =
        if (TacticalCalculator.isSfOperation(mission) && FREE_STRAIT_ID in cleared) FREE_STRAIT_SF_PASS_BONUS else 0
}
