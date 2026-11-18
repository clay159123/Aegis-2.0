package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.MedalDefinition
import com.example.domain.model.PendingDeed
import com.example.ui.theme.LocalAegisColors

/**
 * One newly earned award: soldier, award, why it was earned, Award or Skip, and an "n of N" counter.
 * Skipping leaves the award in Pending Awards on the Honors screen.
 */
@Composable
fun AwardPromptDialog(
    deed: PendingDeed,
    medal: MedalDefinition,
    soldierLabel: String,
    position: Int,
    total: Int,
    canAfford: Boolean,
    /** For a repeat award, what the soldier will receive, e.g. "2nd award: 1 Bronze OLC" */
    repeatLabel: String? = null,
    onAward: () -> Unit,
    onSkip: () -> Unit
) {
    val extra = LocalAegisColors.current
    AlertDialog(
        onDismissRequest = onSkip,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
                if (total > 1) {
                    Text(
                        "Award $position of $total",
                        style = MaterialTheme.typography.labelMedium,
                        color = extra.textMuted,
                        modifier = Modifier.testTag("award_prompt_counter")
                    )
                }
                Text("Award earned: $soldierLabel", style = MaterialTheme.typography.titleLarge, color = extra.brassBright)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("award_prompt_dialog")) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AwardVisualThumbnail(medal = medal)
                    Column {
                        Text(medal.name, style = MaterialTheme.typography.titleMedium)
                        Text(deed.missionTitle, style = MaterialTheme.typography.labelSmall, color = extra.brassBright)
                    }
                }
                repeatLabel?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = extra.signalsCyan, modifier = Modifier.testTag("award_prompt_repeat"))
                }
                Text(deed.deedSummary, style = MaterialTheme.typography.bodySmall)
                Text(
                    if (canAfford) "Cost: ${medal.prestigeCost} PRS" else "Need ${medal.prestigeCost} PRS to award",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (canAfford) extra.textMuted else extra.amberTelemetry
                )
                Text("Skipped awards wait in Pending Awards on the Honors screen.", style = MaterialTheme.typography.labelSmall, color = extra.textMuted)
            }
        },
        confirmButton = {
            Button(
                onClick = onAward,
                enabled = canAfford,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("award_prompt_award")
            ) { Text("Award") }
        },
        dismissButton = {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("award_prompt_skip")
            ) { Text("Skip") }
        }
    )
}
