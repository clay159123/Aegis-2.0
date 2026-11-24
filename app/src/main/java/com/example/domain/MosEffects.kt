package com.example.domain

import com.example.domain.model.*

/**
 * Item 165: each MOS on the team adds one signature effect, counted once per MOS (two medics help no more
 * than one). Aviators flying the insertion don't count; they aren't part of the ground team.
 */
object MosEffects {
    /** Infantry: assault and strike missions. */
    const val INFANTRY_PASS_PCT = 2
    /** Engineer: breach and EOD missions. */
    const val ENGINEER_PASS_PCT = 2
    /** Recon: harder to capture and to spot. */
    const val RECON_CAPTURE_FACTOR = 0.8
    const val RECON_BLOWN_COVER_CUT_PCT = 5
    /** Signals: shorter missions. */
    const val SIGNALS_DURATION_FACTOR = 0.9
    /** Engineer: fewer casualties (a would-be fatality pulled back as wounded). */
    const val ENGINEER_CASUALTY_SAVE_PCT = 5
    /** Medic: more would-be fatalities saved, and faster recovery for the wounded. */
    const val MEDIC_CASUALTY_SAVE_PCT = 10
    const val MEDIC_RECOVERY_FACTOR = 0.85
    /** Command: faster squad cohesion. */
    const val COMMAND_COHESION_FACTOR = 1.15

    private val ASSAULT_KEYWORDS = listOf("Assault", "Strike", "Armor", "Firepower", "Raid")
    private val BREACH_KEYWORDS = listOf("EOD", "Breach", "Engineer", "Sapper", "Demolition")

    private fun ground(team: List<Operator>) = team.filter { !it.isAviator }
    fun has(team: List<Operator>, mos: OperatorSpecialty): Boolean = ground(team).any { it.specialty == mos }

    private fun focusMatches(mission: MissionTemplate, words: List<String>) =
        words.any { mission.primaryFocus.contains(it, ignoreCase = true) || mission.title.contains(it, ignoreCase = true) }

    fun isAssaultMission(mission: MissionTemplate) = focusMatches(mission, ASSAULT_KEYWORDS)
    fun isBreachMission(mission: MissionTemplate) = focusMatches(mission, BREACH_KEYWORDS)

    /** Pass bonus on field missions: Infantry on assault/strike work, Engineers on breach/EOD work. */
    fun passBonusPct(mission: MissionTemplate, team: List<Operator>): Int {
        if (mission.scope != MissionScope.FIELD_COMBAT) return 0
        var pct = 0
        if (isAssaultMission(mission) && has(team, OperatorSpecialty.ASSAULT)) pct += INFANTRY_PASS_PCT
        if (isBreachMission(mission) && has(team, OperatorSpecialty.BREACHER)) pct += ENGINEER_PASS_PCT
        return pct
    }

    fun captureFactor(team: List<Operator>): Double = if (has(team, OperatorSpecialty.RECON)) RECON_CAPTURE_FACTOR else 1.0
    fun blownCoverCutPct(team: List<Operator>): Int = if (has(team, OperatorSpecialty.RECON)) RECON_BLOWN_COVER_CUT_PCT else 0

    /** Signals shorten every mission except school (a class runs its own clock). */
    fun durationFactor(mission: MissionTemplate, team: List<Operator>): Double =
        if (mission.scope != MissionScope.TRAINING && has(team, OperatorSpecialty.SIGNALS)) SIGNALS_DURATION_FACTOR else 1.0

    fun casualtySavePct(team: List<Operator>): Int =
        (if (has(team, OperatorSpecialty.BREACHER)) ENGINEER_CASUALTY_SAVE_PCT else 0) +
            (if (has(team, OperatorSpecialty.MEDIC)) MEDIC_CASUALTY_SAVE_PCT else 0)

    fun recoveryFactor(team: List<Operator>): Double = if (has(team, OperatorSpecialty.MEDIC)) MEDIC_RECOVERY_FACTOR else 1.0
    fun cohesionFactor(team: List<Operator>): Double = if (has(team, OperatorSpecialty.COMMAND)) COMMAND_COHESION_FACTOR else 1.0

    /** Short lines for the staging screen: what this team's MOS mix brings to [mission]. */
    fun summary(mission: MissionTemplate, team: List<Operator>): List<String> {
        if (mission.scope == MissionScope.TRAINING) return emptyList()
        val field = mission.scope == MissionScope.FIELD_COMBAT
        return buildList {
            if (field && isAssaultMission(mission) && has(team, OperatorSpecialty.ASSAULT)) add("Infantry +$INFANTRY_PASS_PCT% (assault)")
            if (field && isBreachMission(mission) && has(team, OperatorSpecialty.BREACHER)) add("Engineer +$ENGINEER_PASS_PCT% (breach)")
            if (field && has(team, OperatorSpecialty.RECON)) add("Recon: harder to capture")
            if (has(team, OperatorSpecialty.SIGNALS)) add("Signals -${((1 - SIGNALS_DURATION_FACTOR) * 100).toInt()}% time")
            if (field && has(team, OperatorSpecialty.BREACHER)) add("Engineer: fewer casualties")
            if (field && has(team, OperatorSpecialty.MEDIC)) add("Medic: casualty care")
            if (has(team, OperatorSpecialty.COMMAND)) add("Command: faster cohesion")
        }
    }

    /** Item 168: the ground MOS a squad doesn't have yet (one of each is the usual build). */
    val SQUAD_MOS: List<OperatorSpecialty> = listOf(
        OperatorSpecialty.ASSAULT, OperatorSpecialty.RECON, OperatorSpecialty.SIGNALS,
        OperatorSpecialty.BREACHER, OperatorSpecialty.MEDIC, OperatorSpecialty.COMMAND
    )

    fun missingMos(members: List<Operator>): List<OperatorSpecialty> = SQUAD_MOS.filter { mos -> members.none { it.specialty == mos } }
}
