package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.TacticalCalculator
import com.example.domain.model.Operator
import com.example.domain.model.OperatorStatus
import com.example.ui.theme.LocalAegisColors
import com.example.ui.viewmodel.CommandUiState

@Composable
fun RetirementConfirmationDialog(
    operator: Operator,
    uiState: CommandUiState,
    onApproveRetirement: () -> Unit,
    onDenyAndKeepActive: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val extra = LocalAegisColors.current
    val preview = TacticalCalculator.previewRetirementLegacy(
        operator = operator,
        currentLegacyBps = uiState.veteranLegacyBps,
        lastRetirementAtMillis = uiState.lastRetirementAtMillis,
        nowMillis = uiState.nowMillis
    )
    val totalOps = operator.missionsCompleted + operator.staffOpsSupported
    val awardsCount = uiState.citations.count { it.operatorId == operator.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Filled.MilitaryTech,
                contentDescription = null,
                tint = extra.brassBright
            )
        },
        title = {
            Text(
                text = if (operator.retirementRequested) {
                    "Review Retirement Request"
                } else {
                    "Honorable Retirement Orders"
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${operator.rank.code} ${operator.callsign} (${operator.rank.title} ${operator.name})",
                    style = MaterialTheme.typography.titleSmall,
                    color = extra.brassBright
                )
                Text(
                    text = "${operator.missionsCompleted} Field Ops • ${operator.staffOpsSupported} HQ Staff Ops • $totalOps Total Ops • $awardsCount Honors",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.textMuted
                )

                Text(
                    text = if (operator.status == OperatorStatus.HQ_STAFF) {
                        "Approving honorable retirement vacates the ${operator.hqRole?.shortCode ?: "HQ Staff"} billet, moves ${operator.callsign} permanently to the Retired Veterans Honor Roll, and grants +${preview.prestigeAward} PRS."
                    } else {
                        "Approving honorable retirement moves ${operator.callsign} permanently off the active field roster to the Retired Veterans Honor Roll, returns all equipped gear to the Armory, frees 1 roster slot, and grants +${preview.prestigeAward} PRS."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = extra.signalsContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, extra.signalsCyan.copy(alpha = 0.55f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "PERMANENT VETERAN LEGACY BONUS",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.signalsCyan
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Base Ops Resource Gain",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (preview.atCap) "CAP REACHED (+0.00%)" else "${preview.gainPctText} CR / SUP / INT",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (preview.atCap) extra.amberTelemetry else extra.brassBright
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Lifetime Endowment",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${TacticalCalculator.formatBpsPercent(preview.currentBps)} → ${preview.resultingPctText} (${preview.capPctText} cap)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Retirement Spacing Factor",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${preview.spacingPct}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (preview.spacingPct < 100) extra.amberTelemetry else MaterialTheme.colorScheme.primary
                            )
                        }
                        if (preview.cooldownRemainingSec > 0L && !preview.atCap) {
                            Text(
                                text = "Recent retirement damping active — full 100% spacing restores in ${formatCountdown(preview.cooldownRemainingSec)}.",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.amberTelemetry
                            )
                        } else if (!preview.atCap) {
                            Text(
                                text = "Full 100% spacing ready (scales down near the ${preview.capPctText} lifetime cap).",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.textMuted
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApproveRetirement,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("confirm_approve_retirement_${operator.id}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = extra.brass,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text("Approve (+${preview.prestigeAward} PRS)")
            }
        },
        dismissButton = {
            if (operator.retirementRequested) {
                OutlinedButton(
                    onClick = onDenyAndKeepActive,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("confirm_deny_retirement_${operator.id}")
                ) {
                    Text("Deny & Retain")
                }
            } else {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("cancel_retirement_dialog")
                ) {
                    Text("Keep Active")
                }
            }
        }
    )
}
