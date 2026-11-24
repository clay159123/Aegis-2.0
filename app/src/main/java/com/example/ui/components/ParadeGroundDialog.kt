package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.catalog.GameCatalog
import com.example.domain.GameTiming
import com.example.domain.MoraleRules
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import com.example.ui.theme.LocalAegisColors
import com.example.ui.viewmodel.CommandUiState

/**
 * Session 38 (item 70): the Parade Ground's activities. Each costs resources by type and has a cooldown;
 * every one lifts morale and keeps the base's morale baseline up for an hour.
 */
@Composable
fun ParadeGroundDialog(
    uiState: CommandUiState,
    onUnitRun: (String) -> Unit,
    onCookout: () -> Unit,
    onAwardFormation: () -> Unit,
    onOpenMemorial: () -> Unit,
    onDismiss: () -> Unit
) {
    val extra = LocalAegisColors.current
    val now = uiState.nowMillis
    val onBase = uiState.operators.filter { it.unlocked && (it.status == OperatorStatus.READY || it.status == OperatorStatus.HQ_STAFF) }
    val fallen = uiState.operators.count { it.status == OperatorStatus.FALLEN }
    val baseline = MoraleRules.baseline(uiState.facilities, uiState.lastParadeActivityAtMillis, now)
    val (cookSup, cookCr) = MoraleRules.cookoutCost(onBase.size)
    val cookLeft = uiState.lastCookoutAtMillis + GameTiming.millis(MoraleRules.COOKOUT_COOLDOWN_MILLIS) - now
    val formationCost = uiState.pendingDeeds.mapNotNull { d -> GameCatalog.medals.find { it.id == d.recommendedMedalId } }.sumOf { it.prestigeCost }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Parade Ground", style = MaterialTheme.typography.titleLarge, color = extra.brassBright) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()).testTag("parade_dialog"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Morale baseline $baseline • soldiers drift toward it. An activity here adds +${MoraleRules.BASELINE_PARADE_RECENT} for an hour.",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.textMuted
                )
                ActivityCard(
                    title = "Unit Run",
                    detail = "One squad • ${MoraleRules.UNIT_RUN_SUPPLIES.toInt()} SUP • +${MoraleRules.UNIT_RUN_MORALE} morale for members on base"
                ) {
                    uiState.squads.forEach { sq ->
                        val left = sq.lastRunAtMillis + GameTiming.millis(MoraleRules.UNIT_RUN_COOLDOWN_MILLIS) - now
                        val ready = sq.lastRunAtMillis == 0L || left <= 0
                        OutlinedButton(
                            onClick = { onUnitRun(sq.id) },
                            enabled = ready && uiState.resources.supplies >= MoraleRules.UNIT_RUN_SUPPLIES,
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).testTag("parade_run_${sq.id}")
                        ) {
                            Text(if (ready) "Run: ${sq.name}" else "${sq.name} • ${TacticalCalculator.formatBuildTime(left / 1000)}")
                        }
                    }
                    if (uiState.squads.isEmpty()) Text("No squads yet.", style = MaterialTheme.typography.labelSmall, color = extra.textMuted)
                }
                ActivityCard(
                    title = "Cookout",
                    detail = "Whole base (${onBase.size} on base) • ${cookSup.toInt()} SUP + ${cookCr.toInt()} CR • +${MoraleRules.COOKOUT_MORALE} morale each"
                ) {
                    Button(
                        onClick = onCookout,
                        enabled = (uiState.lastCookoutAtMillis == 0L || cookLeft <= 0) && uiState.resources.supplies >= cookSup && uiState.resources.credits >= cookCr,
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).testTag("parade_cookout")
                    ) {
                        Text(if (uiState.lastCookoutAtMillis == 0L || cookLeft <= 0) "Hold a Cookout" else "Next cookout in ${TacticalCalculator.formatBuildTime(cookLeft / 1000)}")
                    }
                }
                ActivityCard(
                    title = "Award Formation",
                    detail = "Present every pending award at once • same prestige as one by one • +${MoraleRules.FORMATION_MORALE_PER_AWARD} extra morale per award • squads +cohesion"
                ) {
                    Button(
                        onClick = onAwardFormation,
                        enabled = uiState.pendingDeeds.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).testTag("parade_formation")
                    ) {
                        Text(if (uiState.pendingDeeds.isEmpty()) "No pending awards" else "Form up: ${uiState.pendingDeeds.size} awards • $formationCost PRS")
                    }
                }
                if (fallen > 0) {
                    OutlinedButton(
                        onClick = onOpenMemorial,
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).testTag("parade_memorial")
                    ) { Text("Visit the Memorial Wall ($fallen)") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Close") }
        }
    )
}

@Composable
private fun ActivityCard(title: String, detail: String, content: @Composable ColumnScope.() -> Unit) {
    val extra = LocalAegisColors.current
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, extra.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = extra.brassBright)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = extra.textMuted)
            content()
        }
    }
}
