package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.UniformInspectionReport
import com.example.ui.theme.LocalAegisColors

@Composable
fun UniformInspectionReportDialog(
    report: UniformInspectionReport,
    onDismiss: () -> Unit
) {
    val extra = LocalAegisColors.current
    val gradeColor = when (report.gradeLetter) {
        "S", "A" -> extra.signalsCyan
        "B" -> extra.brassBright
        else -> extra.amberTelemetry
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Filled.MilitaryTech,
                contentDescription = null,
                tint = gradeColor
            )
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "FORMAL UNIFORM & TURN-OUT INSPECTION",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.signalsCyan
                )
                Text(
                    text = report.subjectLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = extra.brassBright
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag("uniform_inspection_report_dialog"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = gradeColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, gradeColor.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "GRADE ${report.gradeLetter} • ${report.scorePct}%",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = gradeColor
                            )
                            Text(
                                text = "+${report.prestigeEarned} Prestige Awarded",
                                style = MaterialTheme.typography.labelMedium,
                                color = extra.brassBright
                            )
                        }
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = extra.signalsContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bolt,
                                    contentDescription = null,
                                    tint = extra.signalsCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "+8% Eff • +10% PRS (${(report.buffDurationSec / 60).coerceAtLeast(1)}m)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.signalsCyan
                                )
                            }
                        }
                    }
                }

                if (report.highlights.isNotEmpty()) {
                    Text(
                        text = "COMMENDATIONS & STANDOUT STANDARDS",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.brassBright
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, extra.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            report.highlights.forEach { item ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = extra.signalsCyan,
                                        modifier = Modifier
                                            .size(15.dp)
                                            .padding(top = 1.dp)
                                    )
                                    Text(
                                        text = item,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "INSPECTOR FINDINGS",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.textMuted
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, extra.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (report.findings.isEmpty()) {
                            Text(
                                text = "Zero turn-out deficiencies recorded. Exemplary ceremonial alignment.",
                                style = MaterialTheme.typography.bodySmall,
                                color = extra.signalsCyan
                            )
                        } else {
                            report.findings.forEach { finding ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.WarningAmber,
                                        contentDescription = null,
                                        tint = extra.amberTelemetry,
                                        modifier = Modifier
                                            .size(15.dp)
                                            .padding(top = 1.dp)
                                    )
                                    Text(
                                        text = finding,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("dismiss_inspection_report")
            ) {
                Text("Dismiss")
            }
        }
    )
}
