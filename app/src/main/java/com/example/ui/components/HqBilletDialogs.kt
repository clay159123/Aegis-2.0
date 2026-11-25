package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.catalog.GameCatalog
import com.example.domain.displayTitle
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import com.example.ui.theme.LocalAegisColors
import com.example.ui.viewmodel.CommandUiState
import kotlin.math.roundToInt

/**
 * Dialog for picking an open unlocked [HqStaffRole] for a given experienced NCO or Officer.
 * Works both for moving a field operator to HQ Staff and for reassigning an existing HQ Staffer.
 */
@Composable
fun AssignOperatorToHqRoleDialog(
    operator: Operator,
    uiState: CommandUiState,
    onSelectRole: (HqStaffRole) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val extra = LocalAegisColors.current
    val unlockedRoles = TacticalCalculator.unlockedHqRoles(uiState.facilities)
    val lockedRoles = HqStaffRole.entries.filter { it !in unlockedRoles }.sortedBy { it.slotOrder }
    val staffByRole = uiState.operators
        .filter { it.holdsHqSeat }
        .associateBy { it.hqRole!! }
    val isReassign = operator.status == OperatorStatus.HQ_STAFF

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Filled.AccountBalance,
                contentDescription = null,
                tint = extra.signalsCyan
            )
        },
        title = {
            Text(
                text = if (isReassign) {
                    "Reassign ${operator.rank.code} ${operator.callsign}"
                } else {
                    "Assign ${operator.rank.code} ${operator.callsign} to HQ"
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${operator.rank.title} (${operator.rank.payGrade}) • ${operator.missionsCompleted} Field Ops • ${operator.staffOpsSupported} Staff Ops. Select an open Directorate billet:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                unlockedRoles.forEach { role ->
                    val occupant = staffByRole[role]
                    val isCurrentRole = isReassign && operator.hqRole == role
                    val isOccupiedByOther = occupant != null && occupant.id != operator.id
                    val eligibility = TacticalCalculator.evaluateHqRoleEligibility(role, operator)
                    val canAssign = !isCurrentRole && !isOccupiedByOther && eligibility.eligible

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = when {
                            isCurrentRole -> extra.signalsContainer.copy(alpha = 0.5f)
                            canAssign -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                isCurrentRole -> extra.signalsCyan
                                canAssign -> extra.brass.copy(alpha = 0.7f)
                                else -> extra.borderSubtle
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${role.shortCode} • ${role.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (canAssign || isCurrentRole) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        extra.textMuted
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = when {
                                        isCurrentRole -> extra.signalsContainer
                                        isOccupiedByOther -> MaterialTheme.colorScheme.surfaceContainerHigh
                                        eligibility.eligible -> extra.brassContainer
                                        else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                                    }
                                ) {
                                    Text(
                                        text = when {
                                            isCurrentRole -> "CURRENT"
                                            isOccupiedByOther -> "BY ${occupant!!.callsign}"
                                            eligibility.eligible -> "OPEN"
                                            else -> "LOCKED"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            isCurrentRole -> extra.signalsCyan
                                            isOccupiedByOther -> extra.textMuted
                                            eligibility.eligible -> extra.brassBright
                                            else -> MaterialTheme.colorScheme.error
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = role.bonusSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.signalsCyan
                            )
                            Text(
                                text = "Req: ${role.requirementSummary}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (eligibility.eligible) extra.textMuted else MaterialTheme.colorScheme.error
                            )

                            if (!eligibility.eligible) {
                                Text(
                                    text = eligibility.reasonText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (canAssign) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Button(
                                    onClick = { onSelectRole(role) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("pick_hq_role_${role.name.lowercase()}")
                                ) {
                                    Text(if (isReassign) "Move to ${role.shortCode}" else "Assign as ${role.shortCode}")
                                }
                            }
                        }
                    }
                }

                if (lockedRoles.isNotEmpty()) {
                    HorizontalDivider(color = extra.borderSubtle, modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "FUTURE BILLETS (UPGRADE COMMAND HQ)",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.textMuted
                    )
                    lockedRoles.forEach { role ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${role.shortCode} • ${role.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.textMuted,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "HQ Lv.${role.requiredHqLevel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.amberTelemetry
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Close")
            }
        }
    )
}

/**
 * Dialog opened from an OPEN billet on the HQ tab to choose which eligible experienced
 * field NCO or Officer to move into that HQ Staff role.
 */
@Composable
fun FillHqBilletCandidateDialog(
    role: HqStaffRole,
    uiState: CommandUiState,
    deployedOpIds: Set<String>,
    onAssignOperator: (String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val extra = LocalAegisColors.current
    val fieldOps = uiState.operators.filter {
        it.unlocked && !it.isHqStaffer && !it.status.isOutOfActiveService
    }
    val evaluated = fieldOps.map { op ->
        val elig = TacticalCalculator.evaluateHqRoleEligibility(role, op)
        val isReady = op.status == OperatorStatus.READY && op.id !in deployedOpIds
        Triple(op, elig, isReady)
    }
    val readyEligible = evaluated.filter { (_, elig, isReady) -> elig.eligible && isReady }
    val otherCandidates = evaluated.filter { (_, elig, isReady) -> !(elig.eligible && isReady) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Filled.PersonAdd,
                contentDescription = null,
                tint = extra.brassBright
            )
        },
        title = {
            Text(
                text = "Fill ${role.shortCode} • ${role.title}",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = role.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = extra.signalsContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, extra.signalsCyan.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "REQUIREMENT: ${role.requirementSummary}",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.brassBright
                        )
                        Text(
                            text = role.bonusSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.signalsCyan
                        )
                    }
                }

                if (readyEligible.isEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, extra.borderSubtle)
                    ) {
                        Text(
                            text = "No READY field NCOs or Officers currently meet all requirements for ${role.shortCode}. Promote experienced troops or complete more ops first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.textMuted,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else {
                    Text(
                        text = "ELIGIBLE FIELD CANDIDATES",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.brassBright
                    )
                    readyEligible.forEach { (op, _, _) ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, extra.brass.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RankInsigniaBadge(rank = op.rank)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${op.callsign} • ${op.rank.title}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${op.mosLabel} • ${op.missionsCompleted} Ops Completed",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = extra.textMuted
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Eligible",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Button(
                                    onClick = { onAssignOperator(op.id) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("confirm_assign_${role.name.lowercase()}_${op.id}")
                                ) {
                                    Text("Move ${op.callsign} to ${role.shortCode} (Frees Field Slot)")
                                }
                            }
                        }
                    }
                }

                if (otherCandidates.isNotEmpty()) {
                    HorizontalDivider(color = extra.borderSubtle, modifier = Modifier.padding(vertical = 2.dp))
                    Text(
                        text = "FIELD PERSONNEL NOT YET READY / ELIGIBLE",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.textMuted
                    )
                    otherCandidates.forEach { (op, elig, isReady) ->
                        val reason = buildList {
                            if (!isReady) {
                                add(
                                    when {
                                        op.id in deployedOpIds -> "Currently Deployed"
                                        op.status == OperatorStatus.RECOVERING -> "In Medbay"
                                        else -> op.status.label
                                    }
                                )
                            }
                            if (!elig.eligible) add(elig.reasonText)
                        }.joinToString(" • ")

                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, extra.borderSubtle)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RankInsigniaBadge(rank = op.rank)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${op.callsign} • ${op.rank.code} (${op.missionsCompleted} Ops)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = reason,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = "Locked",
                                    tint = extra.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Close")
            }
        }
    )
}

/**
 * Dialog allowing HQ Staff members to be dispatched on authorized [MissionScope.TRAINING] (Schools)
 * or [MissionScope.STAFF_DIPLOMATIC] (Diplomatic & General Staff) ops while blocking Field Combat ops.
 */
@Composable
fun DispatchHqMemberTaskingDialog(
    operator: Operator,
    uiState: CommandUiState,
    deployedOpIds: Set<String>,
    onDispatchMission: (MissionTemplate) -> Unit,
    onOpenOpsTab: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val extra = LocalAegisColors.current
    val allowedMissions = GameCatalog.missions.filter { it.scope.hqStaffAllowed }
    val durationMult = TacticalCalculator.durationMultiplier(uiState.facilities, uiState.hqStaffSummary)
    val supplyMult = TacticalCalculator.supplyCostMultiplier(uiState.facilities, uiState.hqStaffSummary)
    val slotsFull = uiState.occupiedDeploymentSlots >= uiState.maxDeploymentSlots
    val alreadyDeployed = operator.id in deployedOpIds || operator.status == OperatorStatus.DEPLOYED

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Filled.FlightTakeoff,
                contentDescription = null,
                tint = extra.signalsCyan
            )
        },
        title = {
            Text(
                text = "Dispatch ${operator.rank.code} ${operator.callsign}",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "HQ Staff cannot deploy on Field Combat ops, but remain cleared for Schools, Diplomatic Delegations, and General Staff Liaison.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (alreadyDeployed) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = extra.amberTelemetry.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, extra.amberTelemetry)
                    ) {
                        Text(
                            text = "${operator.callsign} is already away on an active tasking.",
                            style = MaterialTheme.typography.labelMedium,
                            color = extra.amberTelemetry,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else if (slotsFull) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text(
                            text = "All HQ tasking slots are currently full (${uiState.occupiedDeploymentSlots}/${uiState.maxDeploymentSlots}).",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                allowedMissions.forEach { mission ->
                    val adjustedSec = (mission.durationSeconds * durationMult).roundToInt().toLong()
                    val adjustedSup = (mission.supplyCost * supplyMult).roundToInt()
                    val canAfford = uiState.resources.supplies >= adjustedSup
                    val isSchool = mission.scope == MissionScope.TRAINING
                    val badgeColor = if (isSchool) MaterialTheme.colorScheme.primary else extra.brassBright
                    val eligibility = TacticalCalculator.canOperatorDeployOnMission(
                        operator = operator,
                        mission = mission,
                        deployedOpIds = deployedOpIds,
                        gearItems = uiState.gearItems,
                        citations = uiState.citations,
                        facilities = uiState.facilities
                    )
                    val schoolReq = mission.schoolRequirements
                    val passOdds = if (isSchool) {
                        TacticalCalculator.schoolPassOddsPct(
                            mission = mission,
                            operator = operator,
                            citations = uiState.citations,
                            hqStaffSummary = uiState.hqStaffSummary,
                            facilities = uiState.facilities
                        )
                    } else null
                    val attemptsUsed = operator.schoolAttempts[mission.id] ?: 0
                    val attemptText = schoolReq?.maxAttempts?.let { max -> "Attempts: $attemptsUsed/$max" }

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (eligibility.eligible) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, extra.borderSubtle)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isSchool) Icons.Filled.School else Icons.Filled.Public,
                                        contentDescription = mission.scope.label,
                                        tint = badgeColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = mission.scope.label.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeColor
                                    )
                                }
                                Text(
                                    text = buildString {
                                        if (passOdds != null) append("Pass ~${passOdds}% • ")
                                        append(formatCountdown(adjustedSec))
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (passOdds != null && passOdds < 65) extra.amberTelemetry else extra.textMuted
                                )
                            }

                            Text(
                                text = mission.displayTitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (eligibility.eligible) MaterialTheme.colorScheme.onSurface else extra.textMuted
                            )
                            Text(
                                text = buildString {
                                    append("${mission.primaryFocus} • +${mission.rewardCredits.toInt()} CR • +${mission.rewardXp} XP")
                                    if (attemptText != null) append(" • $attemptText")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.brassBright
                            )
                            if (schoolReq != null) {
                                Text(
                                    text = schoolReq.suitabilitySummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.textMuted
                                )
                            }

                            if (!eligibility.eligible) {
                                Text(
                                    text = eligibility.reasonText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Spacer(modifier = Modifier.height(2.dp))
                                if (mission.minOperators == 1) {
                                    Button(
                                        onClick = { onDispatchMission(mission) },
                                        enabled = !alreadyDeployed && (mission.scope == MissionScope.TRAINING || !slotsFull) && canAfford,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("hq_dispatch_${operator.id}_${mission.id}")
                                    ) {
                                        Text(
                                            if (!canAfford) {
                                                "Need $adjustedSup SUP"
                                            } else {
                                                "Send ${operator.callsign} ($adjustedSup SUP)"
                                            }
                                        )
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = onOpenOpsTab,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("hq_dispatch_stage_${mission.id}")
                                    ) {
                                        Text("Requires ${mission.minOperators}+ Personnel • Stage on Ops Tab")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Close")
            }
        }
    )
}

