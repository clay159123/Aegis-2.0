package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.catalog.GameCatalog
import com.example.domain.PromotionEndorsementFocus
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import com.example.ui.components.*
import com.example.ui.theme.LocalAegisColors
import com.example.ui.viewmodel.CommandUiState

@Composable
fun HqCommandScreen(
    uiState: CommandUiState,
    onAssignToHqStaff: (operatorId: String, role: HqStaffRole) -> Unit,
    onReassignHqStaffRole: (operatorId: String, newRole: HqStaffRole) -> Unit,
    onPromoteOperator: (String, PromotionEndorsementFocus?) -> Unit,
    onStartDeployment: (MissionTemplate, List<String>) -> Unit,
    onApproveTransferOut: (String) -> Unit,
    onApproveRetirement: (String) -> Unit = {},
    onDenyRetirementRequest: (String) -> Unit = {},
    onUpgradeFacility: (FacilityType) -> Unit,
    onRushFacility: (FacilityType) -> Unit = {},
    onToggleUniformMode: (String) -> Unit,
    onToggleDressDisplayMode: (String) -> Unit,
    onNavigateToSquad: () -> Unit,
    onNavigateToDress: () -> Unit,
    onNavigateToOps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = LocalAegisColors.current
    var fillingRole by remember { mutableStateOf<HqStaffRole?>(null) }
    var pickingRoleForOpId by remember { mutableStateOf<String?>(null) }
    var dispatchingOpId by remember { mutableStateOf<String?>(null) }
    var pendingTransferOp by remember { mutableStateOf<Operator?>(null) }
    var pendingRetirementOp by remember { mutableStateOf<Operator?>(null) }
    var inspectingOpId by remember { mutableStateOf<String?>(null) }
    var inspectedAward by remember { mutableStateOf<MedalDefinition?>(null) }
    var promotionBoardOpId by remember { mutableStateOf<String?>(null) }

    if (promotionBoardOpId != null) {
        BackHandler { promotionBoardOpId = null }
    } else if (inspectedAward != null) {
        BackHandler { inspectedAward = null }
    } else if (inspectingOpId != null) {
        BackHandler { inspectingOpId = null }
    } else if (pendingRetirementOp != null) {
        BackHandler { pendingRetirementOp = null }
    } else if (pendingTransferOp != null) {
        BackHandler { pendingTransferOp = null }
    } else if (dispatchingOpId != null) {
        BackHandler { dispatchingOpId = null }
    }

    val deployedOpIds = remember(uiState.deployments) {
        uiState.deployments.flatMap { it.operatorIds }.toSet()
    }
    val hqLevel = TacticalCalculator.facilityLevel(uiState.facilities, FacilityType.COMMAND_HQ)
    val unlockedRoles = remember(uiState.facilities) {
        TacticalCalculator.unlockedHqRoles(uiState.facilities)
    }
    val lockedRoles = remember(unlockedRoles) {
        HqStaffRole.entries.filter { it !in unlockedRoles }.sortedBy { it.slotOrder }
    }

    val hqStaffOps = remember(uiState.operators) {
        uiState.operators.filter { it.unlocked && it.status == OperatorStatus.HQ_STAFF }
    }
    val staffByRole = remember(hqStaffOps) {
        hqStaffOps.filter { it.hqRole != null }.associateBy { it.hqRole!! }
    }
    // Any legacy HQ Staff member missing a formal billet falls gracefully into display below
    val unassignedRoleStaff = remember(hqStaffOps) {
        hqStaffOps.filter { it.hqRole == null }
    }

    val fieldOps = remember(uiState.operators) {
        uiState.operators.filter {
            it.unlocked && it.status != OperatorStatus.HQ_STAFF && !it.status.isOutOfActiveService
        }
    }
    val experiencedFieldCandidates = remember(fieldOps) {
        fieldOps.filter { it.rank.isCareerEligibleForStaffOrTransfer }
    }
    val openUnlockedRoles = remember(unlockedRoles, staffByRole) {
        unlockedRoles.filter { it !in staffByRole }
    }

    val summary = uiState.hqStaffSummary

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Directorate HQ Command Overview Card
        item {
            TacticalPanelCard(
                containerColor = extra.signalsContainer.copy(alpha = 0.28f),
                borderColor = extra.signalsCyan.copy(alpha = 0.55f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AEGIS DIRECTORATE HQ",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Command HQ Lv.$hqLevel • Senior NCOs (E-5+) & Officers (O-1+) lead HQ desks & earn honors supporting field units",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = extra.brassContainer
                    ) {
                        Text(
                            text = "${hqStaffOps.size}/${uiState.maxHqStaffSlots} FILLED",
                            style = MaterialTheme.typography.labelLarge,
                            color = extra.brassBright,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = extra.borderSubtle)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "ACTIVE HQ STAFF DIRECTIVE BONUSES",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.textMuted
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (hqStaffOps.isEmpty()) {
                    Text(
                        text = "No Directorate billets staffed yet. Assign experienced SGT+ or Officers into open HQ billets below to boost production, survival, mission speed & honors.",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.amberTelemetry
                    )
                } else {
                    val perkChips = buildList {
                        if (summary.creditFlowPct > 0) add("+${summary.creditFlowPct}% Credits")
                        if (summary.supplyFlowPct > 0) add("+${summary.supplyFlowPct}% Supplies")
                        if (summary.intelFlowPct > 0) add("+${summary.intelFlowPct}% Intel")
                        if (summary.durationReductionPct > 0) add("-${summary.durationReductionPct}% Op Time")
                        if (summary.supplyDiscountPct > 0) add("-${summary.supplyDiscountPct}% Op Cost")
                        if (summary.survivalBonusPct > 0) add("+${summary.survivalBonusPct}% Survival")
                        if (summary.efficiencyBonusPct > 0) add("+${summary.efficiencyBonusPct}% Eff")
                        if (summary.prestigeBonusPct > 0) add("+${summary.prestigeBonusPct}% Prestige")
                        if (summary.xpBonusPct > 0) add("+${summary.xpBonusPct}% XP")
                    }
                    Text(
                        text = perkChips.joinToString("  •  "),
                        style = MaterialTheme.typography.labelMedium,
                        color = extra.signalsCyan
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "VETERAN LEGACY BASE OPS: ${uiState.veteranLegacyPctText} (${uiState.retiredVeteransCount} Retired • ${TacticalCalculator.formatBpsPercent(TacticalCalculator.MAX_VETERAN_LEGACY_BPS)} cap)",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.brassBright
                )
            }
        }

        val hqRetirementPetitions = hqStaffOps.filter { it.retirementRequested }
        if (hqRetirementPetitions.isNotEmpty()) {
            items(hqRetirementPetitions, key = { "hq_ret_${it.id}" }) { staffOp ->
                val preview = TacticalCalculator.previewRetirementLegacy(
                    operator = staffOp,
                    currentLegacyBps = uiState.veteranLegacyBps,
                    lastRetirementAtMillis = uiState.lastRetirementAtMillis,
                    nowMillis = uiState.nowMillis
                )
                TacticalPanelCard(
                    containerColor = extra.signalsContainer.copy(alpha = 0.42f),
                    borderColor = extra.signalsCyan
                ) {
                    Text(
                        text = "HQ STAFF RETIREMENT REQUEST • ${staffOp.rank.code} ${staffOp.callsign}",
                        style = MaterialTheme.typography.labelLarge,
                        color = extra.brassBright
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${staffOp.hqRole?.title ?: "HQ Staff"} requests Honorable Retirement (+${preview.prestigeAward} PRS, ${preview.gainPctText} Veteran Legacy at ${preview.spacingPct}% spacing).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { pendingRetirementOp = staffOp },
                            enabled = staffOp.id !in deployedOpIds,
                            modifier = Modifier
                                .weight(1.3f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("hq_approve_retire_${staffOp.id}"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = extra.brass,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Text("Approve (${preview.gainPctText})", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { onDenyRetirementRequest(staffOp.id) },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("hq_deny_retire_${staffOp.id}")
                        ) {
                            Text("Deny & Retain", maxLines = 1)
                        }
                    }
                }
            }
        }

        // HQ Billets & Current Staff
        item {
            SectionHeader(
                title = "HQ Staff Billets (${hqStaffOps.size}/${uiState.maxHqStaffSlots})",
                subtitle = "${openUnlockedRoles.size} open billet(s) • Each billet grants distinct Directorate bonuses"
            )
        }

        items(unlockedRoles, key = { "billet_${it.name}" }) { role ->
            val occupant = staffByRole[role]
            if (occupant != null) {
                val opMedals = uiState.citations.filter { it.operatorId == occupant.id }
                    .mapNotNull { cit -> GameCatalog.medals.find { it.id == cit.medalId } }
                    .sortedBy { it.precedenceOrder }
                val canTransfer = occupant.rank.isCareerEligibleForStaffOrTransfer
                val isDeployed = occupant.id in deployedOpIds

                val canRetire = TacticalCalculator.evaluateRetirementEligibility(occupant).eligible
                // Items 126 and 135: the board knows the billet cap, enlisted billets and general seats
                val occupantBoard = TacticalCalculator.evaluatePromotionBoard(
                    occupant, uiState.operators, uiState.citations, uiState.gearItems, uiState.facilities
                )
                val crossesTier = occupantBoard.nextRank?.let { OperatorRank.isBoardGate(occupant.rank, it) } == true
                HqOccupiedBilletCard(
                    role = role,
                    operator = occupant,
                    resources = uiState.resources,
                    awardedMedals = opMedals,
                    squads = uiState.squads,
                    allCitations = uiState.citations,
                    smartWardrobeEnabled = uiState.smartWardrobeEnabled,
                    isOnTasking = isDeployed,
                    hasOtherOpenBillet = openUnlockedRoles.any {
                        TacticalCalculator.evaluateHqRoleEligibility(it, occupant).eligible
                    },
                    canTransfer = canTransfer,
                    canRetire = canRetire,
                    boardNextRank = occupantBoard.nextRank,
                    promotionBlockedReason = occupantBoard.blockedReason,
                    onPromote = {
                        if (crossesTier) {
                            promotionBoardOpId = occupant.id
                        } else {
                            onPromoteOperator(occupant.id, null)
                        }
                    },
                    onDispatchTasking = { dispatchingOpId = occupant.id },
                    onNavigateToAwards = onNavigateToDress,
                    onInspect = { inspectingOpId = occupant.id },
                    onChangeRole = { pickingRoleForOpId = occupant.id },
                    onRetire = { pendingRetirementOp = occupant },
                    onTransferOut = { pendingTransferOp = occupant }
                )
            } else {
                val readyCount = fieldOps.count { op ->
                    op.status == OperatorStatus.READY &&
                        op.id !in deployedOpIds &&
                        TacticalCalculator.evaluateHqRoleEligibility(role, op).eligible
                }
                HqOpenBilletCard(
                    role = role,
                    readyCandidateCount = readyCount,
                    onAssignClick = { fillingRole = role }
                )
            }
        }

        // Legacy / fallback display if a staff member has null hqRole
        if (unassignedRoleStaff.isNotEmpty()) {
            items(unassignedRoleStaff, key = { "legacy_staff_${it.id}" }) { op ->
                TacticalPanelCard(borderColor = extra.amberTelemetry) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${op.rank.code} ${op.callsign} • General HQ Staff",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Assign a formal open HQ billet to unlock specialized bonuses.",
                                style = MaterialTheme.typography.bodySmall,
                                color = extra.textMuted
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { pickingRoleForOpId = op.id },
                            enabled = openUnlockedRoles.isNotEmpty(),
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Set Billet")
                        }
                    }
                }
            }
        }

        // Eligible Field Candidates ready or advancing toward HQ Staff
        item {
            Spacer(modifier = Modifier.height(4.dp))
            SectionHeader(
                title = "Experienced Field Cadre (${experiencedFieldCandidates.size})",
                subtitle = "Senior NCOs (SGT E-5+) & Commissioned Officers eligible for HQ Staff posting",
                trailing = {
                    TextButton(
                        onClick = onNavigateToSquad,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Full Squad")
                    }
                }
            )
        }

        if (experiencedFieldCandidates.isEmpty()) {
            item {
                TacticalPanelCard {
                    Text(
                        text = "No SGT (E-5)+ NCOs or Officers in the active field roster right now. Promote troops or recruit Directorate cadre from Squad.",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.textMuted
                    )
                }
            }
        } else {
            items(experiencedFieldCandidates, key = { "cand_${it.id}" }) { op ->
                val isDeployed = op.id in deployedOpIds
                val isReady = op.status == OperatorStatus.READY && !isDeployed
                val eligibleOpenRoles = openUnlockedRoles.filter {
                    TacticalCalculator.evaluateHqRoleEligibility(it, op).eligible
                }
                val canMoveNow = isReady && eligibleOpenRoles.isNotEmpty()

                TacticalPanelCard(
                    borderColor = if (canMoveNow) extra.brass.copy(alpha = 0.55f) else extra.borderSubtle
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            RankInsigniaBadge(rank = op.rank)
                            Column {
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
                                Text(
                                    text = when {
                                        !isReady -> if (isDeployed) "Status: Currently Deployed" else "Status: ${op.status.label}"
                                        openUnlockedRoles.isEmpty() -> "All HQ billets currently filled"
                                        eligibleOpenRoles.isEmpty() -> "Needs more Ops for remaining open billets"
                                        else -> "Qualifies for: ${eligibleOpenRoles.joinToString(", ") { it.shortCode }}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (canMoveNow) extra.signalsCyan else extra.amberTelemetry
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { pickingRoleForOpId = op.id },
                            enabled = isReady && openUnlockedRoles.isNotEmpty(),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("hq_move_candidate_${op.id}")
                        ) {
                            Text("Assign Role")
                        }
                    }
                }
            }
        }

        // Future HQ Billets Locked Behind Command HQ Upgrades
        if (lockedRoles.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SectionHeader(
                    title = "Locked Senior Billets (${lockedRoles.size})",
                    subtitle = "Upgrade Command HQ below to open additional Directorate desks"
                )
            }
            items(lockedRoles, key = { "locked_${it.name}" }) { role ->
                TacticalPanelCard(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    borderColor = extra.borderSubtle
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = "Locked",
                                    tint = extra.textMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "${role.shortCode} • ${role.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = extra.textMuted
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Req: ${role.requirementSummary} • ${role.bonusSummary}",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.textMuted
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "HQ Lv.${role.requiredHqLevel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.amberTelemetry,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // HQ Infrastructure Facilities
        item {
            Spacer(modifier = Modifier.height(4.dp))
            SectionHeader(
                title = "Base",
                subtitle = "Tap a building to see its perks, build, upgrade or rush"
            )
        }
        item {
            BaseMapCard(
                facilities = uiState.facilities,
                resources = uiState.resources,
                nowMillis = uiState.nowMillis,
                onUpgrade = onUpgradeFacility,
                onRush = onRushFacility
            )
        }
    }

    val roleToFill = fillingRole
    if (roleToFill != null) {
        FillHqBilletCandidateDialog(
            role = roleToFill,
            uiState = uiState,
            deployedOpIds = deployedOpIds,
            onAssignOperator = { opId ->
                onAssignToHqStaff(opId, roleToFill)
                fillingRole = null
            },
            onDismiss = { fillingRole = null }
        )
    }

    val opForRolePick = uiState.operators.find { it.id == pickingRoleForOpId }
    if (opForRolePick != null) {
        AssignOperatorToHqRoleDialog(
            operator = opForRolePick,
            uiState = uiState,
            onSelectRole = { role ->
                if (opForRolePick.status == OperatorStatus.HQ_STAFF) {
                    onReassignHqStaffRole(opForRolePick.id, role)
                } else {
                    onAssignToHqStaff(opForRolePick.id, role)
                }
                pickingRoleForOpId = null
            },
            onDismiss = { pickingRoleForOpId = null }
        )
    }

    val opForDispatch = uiState.operators.find { it.id == dispatchingOpId }
    if (opForDispatch != null) {
        DispatchHqMemberTaskingDialog(
            operator = opForDispatch,
            uiState = uiState,
            deployedOpIds = deployedOpIds,
            onDispatchMission = { mission ->
                onStartDeployment(mission, listOf(opForDispatch.id))
                dispatchingOpId = null
            },
            onOpenOpsTab = {
                dispatchingOpId = null
                onNavigateToOps()
            },
            onDismiss = { dispatchingOpId = null }
        )
    }

    pendingRetirementOp?.let { opToRetire ->
        RetirementConfirmationDialog(
            operator = opToRetire,
            uiState = uiState,
            onApproveRetirement = {
                onApproveRetirement(opToRetire.id)
                pendingRetirementOp = null
            },
            onDenyAndKeepActive = {
                if (opToRetire.retirementRequested) {
                    onDenyRetirementRequest(opToRetire.id)
                }
                pendingRetirementOp = null
            },
            onDismiss = { pendingRetirementOp = null }
        )
    }

    pendingTransferOp?.let { opToTransfer ->
        AlertDialog(
            onDismissRequest = { pendingTransferOp = null },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = extra.amberTelemetry)
            },
            title = {
                Text("Transfer ${opToTransfer.rank.code} ${opToTransfer.callsign} to Another Unit?")
            },
            text = {
                Text(
                    text = "Approving external transfer removes ${opToTransfer.callsign} from Aegis HQ permanently, vacating their ${opToTransfer.hqRole?.shortCode ?: "HQ"} billet (+10 PRS). We lose command tracking of their career.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onApproveTransferOut(opToTransfer.id)
                        pendingTransferOp = null
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Approve Transfer")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingTransferOp = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val opToInspect = uiState.operators.find { it.id == inspectingOpId }
    if (opToInspect != null) {
        val opCitations = uiState.citations.filter { it.operatorId == opToInspect.id }
        val opMedals = opCitations.mapNotNull { cit -> GameCatalog.medals.find { it.id == cit.medalId } }
            .sortedBy { it.precedenceOrder }
        val opGear = uiState.gearItems.equippedBy(opToInspect)
        val inspectKit = TacticalCalculator.resolveEffectiveWardrobeKit(
            operator = opToInspect,
            smartWardrobeEnabled = uiState.smartWardrobeEnabled,
            dutyContext = DutyUniformContext.HQ_ASSIGNMENT
        )
        UniformInspectionDialog(
            operator = opToInspect,
            equippedGear = opGear,
            awardedMedals = opMedals,
            allCitations = uiState.citations,
            squads = uiState.squads,
            kitOverride = inspectKit,
            onToggleUniformMode = { onToggleUniformMode(opToInspect.id) },
            onToggleDressDisplayMode = { onToggleDressDisplayMode(opToInspect.id) },
            onInspectAward = { inspectedAward = it },
            onDismiss = { inspectingOpId = null }
        )
    }

    val awardToShow = inspectedAward
    if (awardToShow != null) {
        val holders = uiState.citations.filter { it.medalId == awardToShow.id }.mapNotNull { cit ->
            val holder = uiState.operators.find { it.id == cit.operatorId } ?: return@mapNotNull null
            holder to cit
        }
        AwardInspectionDialog(
            medal = awardToShow,
            heldByOperators = holders,
            onDismiss = { inspectedAward = null }
        )
    }

    val boardOp = promotionBoardOpId?.let { id -> uiState.operators.find { it.id == id } }
    if (boardOp != null) {
        val boardEval = TacticalCalculator.evaluatePromotionBoard(
            operator = boardOp,
            allOperators = uiState.operators,
            citations = uiState.citations,
            gearItems = uiState.gearItems,
            facilities = uiState.facilities
        )
        val boardMedals = uiState.citations
            .filter { it.operatorId == boardOp.id }
            .mapNotNull { cit -> GameCatalog.medals.find { it.id == cit.medalId } }
            .sortedBy { it.precedenceOrder }
        val boardGear = uiState.gearItems.equippedBy(boardOp)
        PromotionBoardDialog(
            operator = boardOp,
            evaluation = boardEval,
            resources = uiState.resources,
            equippedGear = boardGear,
            awardedMedals = boardMedals,
            allCitations = uiState.citations,
            squads = uiState.squads,
            onApprove = { focus ->
                onPromoteOperator(boardOp.id, focus)
                promotionBoardOpId = null
            },
            onDismiss = { promotionBoardOpId = null }
        )
    }
}

@Composable
private fun HqOccupiedBilletCard(
    role: HqStaffRole,
    operator: Operator,
    resources: ResourceBundle,
    awardedMedals: List<MedalDefinition>,
    squads: List<TacticalSquad> = emptyList(),
    allCitations: List<AwardedMedal> = emptyList(),
    smartWardrobeEnabled: Boolean = true,
    isOnTasking: Boolean,
    hasOtherOpenBillet: Boolean,
    canTransfer: Boolean,
    canRetire: Boolean,
    boardNextRank: OperatorRank? = OperatorRank.nextRank(operator.rank),
    promotionBlockedReason: String? = null,
    onPromote: () -> Unit,
    onDispatchTasking: () -> Unit,
    onNavigateToAwards: () -> Unit,
    onInspect: () -> Unit,
    onChangeRole: () -> Unit,
    onRetire: () -> Unit,
    onTransferOut: () -> Unit
) {
    val extra = LocalAegisColors.current
    val nextRank = boardNextRank
    val crCost = operator.rank.promotionCreditCost()
    val prsCost = operator.rank.promotionPrestigeCost()
    val crossesTierCard = nextRank != null && OperatorRank.isBoardGate(operator.rank, nextRank)
    // Session 33: inside a tier from SGT up, the rank is pinned once XP is there (free)
    val pinsRank = nextRank != null && !crossesTierCard && operator.pinnedRank != null && operator.rank.tier != RankTier.SOLDIER
    val canPromote = nextRank != null && when {
        crossesTierCard -> true
        pinsRank -> operator.pinEligibleRank != null && resources.credits >= crCost && resources.prestige >= prsCost
        else -> resources.credits >= crCost && resources.prestige >= prsCost
    }

    TacticalPanelCard(
        borderColor = if (isOnTasking) extra.amberTelemetry else extra.signalsCyan.copy(alpha = 0.65f),
        modifier = Modifier.testTag("hq_billet_occupied_${role.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = extra.signalsContainer
                ) {
                    Text(
                        text = role.shortCode,
                        style = MaterialTheme.typography.labelLarge,
                        color = extra.signalsCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = role.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = if (isOnTasking) extra.amberTelemetry.copy(alpha = 0.18f) else extra.brassContainer
            ) {
                Text(
                    text = if (isOnTasking) "ON TASKING" else "ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOnTasking) extra.amberTelemetry else extra.brassBright,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val previewKit = TacticalCalculator.resolveEffectiveWardrobeKit(
                operator = operator,
                smartWardrobeEnabled = smartWardrobeEnabled,
                dutyContext = DutyUniformContext.HQ_ASSIGNMENT
            )
            OperatorUniformPreview(
                operator = operator,
                equippedGear = emptyList(),
                awardedMedals = awardedMedals,
                allCitations = allCitations,
                squads = squads,
                kitOverride = previewKit,
                modifier = Modifier
                    .width(86.dp)
                    .height(118.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onInspect)
                    .testTag("inspect_hq_staff_${operator.id}")
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RankInsigniaBadge(rank = operator.rank)
                    Text(
                        text = operator.callsign,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${operator.rank.title} ${operator.name} • ${operator.mosTag}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Supported ${operator.staffOpsSupported} Ops • ${operator.missionsCompleted} Field Ops • ${awardedMedals.size} Honors",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.brassBright
                )
                val crossesTier = nextRank != null && OperatorRank.isBoardGate(operator.rank, nextRank)
                val readyAtGate = crossesTier && operator.xp >= nextRank!!.xpRequired
                Text(
                    text = when {
                        nextRank == null && promotionBlockedReason != null -> promotionBlockedReason
                        nextRank == null -> "Max Army Rank (${operator.xp} XP)"
                        readyAtGate -> "Category Gate Ready: ${operator.rank.code} → ${nextRank.code} (${nextRank.tier.shortLabel})"
                        crossesTier -> "Gate XP: ${operator.xp}/${nextRank.xpRequired} → ${nextRank.code} (Player)"
                        else -> "Rank XP: ${operator.xp}/${nextRank.xpRequired} → ${nextRank.code}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (readyAtGate) extra.signalsCyan else extra.textMuted
                )
                Text(
                    text = role.bonusSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.signalsCyan
                )
                val rackOnly = awardedMedals.filter {
                    it.category == AwardCategory.MEDAL || it.category == AwardCategory.RIBBON
                }
                if (rackOnly.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        rackOnly.take(5).forEach { medal ->
                            MedalRibbonBar(
                                stripeHexes = medal.ribbonStripeHexes,
                                width = 20.dp,
                                height = 7.dp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPromote,
                    enabled = canPromote,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("hq_promote_${operator.id}"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = when {
                            nextRank == null && promotionBlockedReason != null -> "Billet Top Rank"
                            nextRank == null -> "Max Rank (GEN)"
                            crossesTierCard -> "${nextRank.tier.shortLabel} Board (${nextRank.code})"
                            pinsRank && operator.pinEligibleRank != null -> "Pin ${nextRank.code} (${crCost.toInt()}C/${prsCost}P)"
                            pinsRank -> "${nextRank.code} at ${nextRank.xpRequired} XP"
                            else -> "Promote ${nextRank.code} (${crCost.toInt()}C/${prsCost}P)"
                        },
                        maxLines = 1
                    )
                }
                OutlinedButton(
                    onClick = onDispatchTasking,
                    enabled = !isOnTasking,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("hq_tasking_${operator.id}"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(if (isOnTasking) "On Tasking" else "School / Diplo", maxLines = 1)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToAwards,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("hq_awards_${operator.id}")
                ) {
                    Text("Awards", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onInspect,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Inspect", maxLines = 1)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onChangeRole,
                    enabled = hasOtherOpenBillet && !isOnTasking,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("change_hq_role_${operator.id}")
                ) {
                    Text("Change Role", maxLines = 1)
                }
                if (canRetire) {
                    OutlinedButton(
                        onClick = onRetire,
                        enabled = !isOnTasking,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("hq_retire_${operator.id}")
                    ) {
                        Text("Retire", maxLines = 1)
                    }
                }
                if (canTransfer) {
                    TextButton(
                        onClick = onTransferOut,
                        enabled = !isOnTasking,
                        modifier = Modifier
                            .weight(0.9f)
                            .defaultMinSize(minHeight = 48.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Transfer", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun HqOpenBilletCard(
    role: HqStaffRole,
    readyCandidateCount: Int,
    onAssignClick: () -> Unit
) {
    val extra = LocalAegisColors.current
    TacticalPanelCard(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        borderColor = extra.brass.copy(alpha = 0.55f),
        modifier = Modifier.testTag("hq_billet_open_${role.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = extra.brassContainer
                ) {
                    Text(
                        text = role.shortCode,
                        style = MaterialTheme.typography.labelLarge,
                        color = extra.brassBright,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = role.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = "OPEN BILLET",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.amberTelemetry,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = role.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Req: ${role.requirementSummary}",
            style = MaterialTheme.typography.labelSmall,
            color = extra.brassBright
        )
        Text(
            text = "Buffs: ${role.bonusSummary}",
            style = MaterialTheme.typography.labelSmall,
            color = extra.signalsCyan
        )

        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onAssignClick,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("fill_billet_${role.name.lowercase()}")
        ) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                if (readyCandidateCount > 0) {
                    "Assign Eligible NCO / Officer ($readyCandidateCount Ready)"
                } else {
                    "View Billet & Candidates"
                }
            )
        }
    }
}
