package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.TacticalCalculator
import com.example.ui.theme.LocalAegisColors

/** One rifle qualification result: old badge to new badge with an Award button, or a short "No change". */
@Composable
fun RifleQualResultDialog(
    result: TacticalCalculator.RifleQualResult,
    remaining: Int,
    onAward: () -> Unit,
    onDismiss: () -> Unit
) {
    val extra = LocalAegisColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = if (result.improved) "Rifle qualification: ${result.callsign}" else "No change: ${result.callsign}",
                style = MaterialTheme.typography.titleLarge,
                color = extra.brassBright
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("rifle_qual_dialog")) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(result.previous?.shortLabel ?: "Unqualified", style = MaterialTheme.typography.titleMedium, color = extra.textMuted)
                    Text("→", style = MaterialTheme.typography.titleMedium)
                    Text(
                        result.achieved.shortLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (result.improved) extra.brassBright else extra.textMuted
                    )
                }
                Text("Score ${result.score} (Sharpshooter at ${com.example.domain.model.SHARPSHOOTER_SCORE}, Expert at ${com.example.domain.model.EXPERT_SCORE})", style = MaterialTheme.typography.bodySmall)
                if (!result.improved) {
                    Text("A lower score never lowers the badge.", style = MaterialTheme.typography.bodySmall, color = extra.textMuted)
                }
                result.attemptsLeft?.let {
                    Text("$it requalification attempt${if (it == 1) "" else "s"} left", style = MaterialTheme.typography.labelMedium, color = extra.amberTelemetry)
                }
                if (remaining > 1) {
                    Text("${remaining - 1} more result${if (remaining > 2) "s" else ""} after this", style = MaterialTheme.typography.labelSmall, color = extra.textMuted)
                }
            }
        },
        confirmButton = {
            if (result.improved) {
                Button(
                    onClick = onAward,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("award_rifle_qual")
                ) { Text("Award Badge") }
            } else {
                Button(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("OK") }
            }
        },
        dismissButton = {
            if (result.improved) {
                TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Later") }
            }
        }
    )
}
