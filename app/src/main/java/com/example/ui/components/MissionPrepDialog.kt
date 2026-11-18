package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import com.example.ui.theme.LocalAegisColors
import com.example.ui.viewmodel.CommandUiState

/**
 * Item 34: the last stop before a field mission launches. Pick the insertion (and pilot), check
 * loadouts, swap soldiers in from other squads, and watch the odds move with every change.
 */
@Composable
fun MissionPrepDialog(
    mission: MissionTemplate,
    uiState: CommandUiState,
    initialTeamIds: List<String>,
    deployedOpIds: Set<String>,
    onAutoGear: () -> Unit,
    onFieldKit: (List<String>) -> Unit,
    onLaunch: (teamIds: List<String>, insertion: InsertionMethod, pilotId: String?) -> Unit,
    onBack: () -> Unit
) {
    val extra = LocalAegisColors.current
    var teamIds by remember(mission.id) { mutableStateOf(initialTeamIds) }
    val allowed = mission.allowedInsertions
    var insertion by remember(mission.id) { mutableStateOf(InsertionMethod.GROUND) }
    var pilotId by remember(mission.id) { mutableStateOf<String?>(null) }
    var swappingId by remember(mission.id) { mutableStateOf<String?>(null) }

    val opsById = uiState.operators.associateBy { it.id }
    val team = teamIds.mapNotNull { opsById[it] }
    val pilots = uiState.operators.filter {
        it.unlocked && it.isAviator && it.isWarrantOfficer && it.status == OperatorStatus.READY && it.id !in deployedOpIds
    }.sortedByDescending { it.flightSkill }
    val pilot = pilots.find { it.id == pilotId }
    val squadOf: (String) -> TacticalSquad? = { id -> uiState.squads.find { id in it.memberIds } }
    val bench = uiState.operators.filter { op ->
        op.id !in teamIds && TacticalCalculator.canOperatorDeployOnMission(
            op, mission, deployedOpIds, uiState.gearItems, uiState.citations, uiState.facilities
        ).eligible
    }

    val preview = TacticalCalculator.previewMissionPlan(
        mission = mission,
        team = team,
        insertion = insertion,
        pilot = pilot,
        allOperators = uiState.operators,
        gearItems = uiState.gearItems,
        citations = uiState.citations,
        facilities = uiState.facilities,
        hqStaffCount = uiState.hqStaffCount,
        hqStaffSummary = uiState.hqStaffSummary,
        squads = uiState.squads,
        smartWardrobeEnabled = uiState.smartWardrobeEnabled,
        completedCampaignIds = uiState.completedCampaignIds
    )
    val canAfford = uiState.resources.supplies >= preview.supplyCost

    AlertDialog(
        onDismissRequest = onBack,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
                Text("Mission Prep", style = MaterialTheme.typography.labelMedium, color = extra.textMuted)
                Text(mission.title, style = MaterialTheme.typography.titleMedium, color = extra.brassBright, maxLines = 2)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag("mission_prep_dialog"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Live odds
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            PrepStat("Pass", "${preview.passPct}%", extra.signalsCyan, "prep_pass")
                            PrepStat("Survival", "${preview.survivalPct}%", extra.brassBright, "prep_survival")
                            PrepStat("Time", "${preview.durationSeconds}s", MaterialTheme.colorScheme.onSurface, "prep_time")
                            PrepStat("Supply", "${preview.supplyCost}", if (canAfford) MaterialTheme.colorScheme.onSurface else extra.amberTelemetry, "prep_supply")
                        }
                        preview.breakdown.forEach { (label, value) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = extra.textMuted)
                                Text(value, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // MOS coverage, only on missions where a specialty matters
                if (mission.recommendedSpecialties.isNotEmpty()) {
                    Text("Recommended MOS", style = MaterialTheme.typography.labelMedium, color = extra.brassBright)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("prep_mos_row")) {
                        mission.recommendedSpecialties.forEach { mos ->
                            val ok = mos in preview.coveredMos
                            AssistChip(
                                onClick = {},
                                label = { Text((if (ok) "✓ " else "✗ ") + mos.roleTag.substringAfter(' ')) },
                                border = BorderStroke(1.dp, if (ok) extra.signalsCyan else extra.textMuted.copy(alpha = 0.5f))
                            )
                        }
                    }
                }

                // Insertion
                Text("Insertion", style = MaterialTheme.typography.labelMedium, color = extra.brassBright)
                InsertionMethod.entries.filter { it in allowed }.forEach { method ->
                    val blocker = TacticalCalculator.insertionBlocker(
                        mission, method, team, if (method == InsertionMethod.HELICOPTER) (pilot ?: pilots.firstOrNull()) else null, uiState.facilities
                    )
                    val selected = insertion == method
                    Surface(
                        onClick = {
                            if (blocker == null) {
                                insertion = method
                                if (method == InsertionMethod.HELICOPTER && pilotId == null) pilotId = pilots.firstOrNull()?.id
                            }
                        },
                        enabled = blocker == null,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) extra.brassBright else extra.textMuted.copy(alpha = 0.35f)),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("prep_insertion_${method.name.lowercase()}")
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                method.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (blocker == null) MaterialTheme.colorScheme.onSurface else extra.textMuted
                            )
                            Text(
                                blocker ?: insertionSummary(method, mission),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (blocker == null) extra.textMuted else extra.amberTelemetry
                            )
                        }
                    }
                }

                // Pilot
                if (insertion == InsertionMethod.HELICOPTER) {
                    Text("Pilot", style = MaterialTheme.typography.labelMedium, color = extra.brassBright)
                    pilots.forEach { p ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("prep_pilot_${p.id}")
                        ) {
                            RadioButton(selected = p.id == pilotId, onClick = { pilotId = p.id })
                            Text("${p.rank.code} ${p.callsign}", modifier = Modifier.weight(1f))
                            Text("Flight skill ${p.flightSkill}", style = MaterialTheme.typography.labelSmall, color = extra.textMuted)
                        }
                    }
                    Text(
                        "The pilot shares the mission's risk and earns XP and a combat sortie.",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.textMuted
                    )
                }

                // Team, loadouts and squad mixing
                Text("Team (${team.size}/${mission.maxOperators})", style = MaterialTheme.typography.labelMedium, color = extra.brassBright)
                team.forEach { op ->
                    val gear = uiState.gearItems.equippedBy(op)
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("${op.rank.code} ${op.callsign} • ${op.specialty.roleTag}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    (squadOf(op.id)?.name ?: "No squad") + " • " +
                                        (if (gear.isEmpty()) "No gear" else gear.joinToString { it.name.substringBefore(' ') }),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = { swappingId = if (swappingId == op.id) null else op.id },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("prep_swap_${op.id}")
                            ) { Text(if (swappingId == op.id) "Close" else "Swap") }
                        }
                        if (swappingId == op.id) {
                            if (bench.isEmpty()) {
                                Text("No other soldiers available.", style = MaterialTheme.typography.labelSmall, color = extra.textMuted)
                            }
                            bench.take(8).forEach { cand ->
                                TextButton(
                                    onClick = {
                                        teamIds = teamIds.map { if (it == op.id) cand.id else it }
                                        swappingId = null
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("prep_swap_in_${cand.id}")
                                ) {
                                    Text(
                                        "${cand.rank.code} ${cand.callsign} • ${cand.specialty.roleTag} (${squadOf(cand.id)?.name ?: "no squad"})",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
                if (preview.mixedSquads) {
                    Text(
                        "Mixed squads: cohesion bonus cut 25% for this mission only. Rosters stay the same.",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.amberTelemetry
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onAutoGear,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("prep_auto_gear")
                    ) { Text("Auto-gear") }
                    OutlinedButton(
                        onClick = { onFieldKit(teamIds) },
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("prep_field_kit")
                    ) { Text("Field kit") }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onLaunch(teamIds, insertion, pilotId.takeIf { insertion == InsertionMethod.HELICOPTER }) },
                enabled = preview.insertionBlocker == null && canAfford && team.size >= mission.minOperators,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("prep_launch")
            ) { Text("Launch") }
        },
        dismissButton = {
            TextButton(onClick = onBack, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Back") }
        }
    )
}

@Composable
private fun PrepStat(label: String, value: String, color: Color, tag: String) {
    val extra = LocalAegisColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.testTag(tag)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = extra.textMuted)
    }
}

private fun insertionSummary(method: InsertionMethod, mission: MissionTemplate): String = when (method) {
    InsertionMethod.GROUND -> "Baseline time and risk"
    InsertionMethod.HELICOPTER -> "Faster, lower casualty risk • needs a pilot and supply"
    InsertionMethod.AIRBORNE -> "Fastest • ${TacticalCalculator.AIRBORNE_SCATTER_CHANCE_PCT}% chance of a scattered drop"
    InsertionMethod.HALO -> "Fastest" + (if (TacticalCalculator.isStealthWeighted(mission)) " • +${TacticalCalculator.HALO_STEALTH_PASS_BONUS}% pass" else "") +
        " • ${TacticalCalculator.HALO_SCATTER_CHANCE_PCT}% scatter"
    InsertionMethod.MARITIME -> "Quicker" + if (TacticalCalculator.isStealthWeighted(mission)) " • +${TacticalCalculator.MARITIME_STEALTH_PASS_BONUS}% pass" else ""
}
