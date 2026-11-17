package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.catalog.GameCatalog
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import com.example.ui.components.*
import com.example.ui.theme.LocalAegisColors
import com.example.ui.viewmodel.CommandUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadArmoryScreen(
    uiState: CommandUiState,
    onRecruitOperator: (String) -> Unit,
    onToggleUniformMode: (String) -> Unit,
    onCustomizeOperator: (
        operatorId: String,
        request: UniformTailorRequest
    ) -> Unit,
    onEquipGear: (operatorId: String, gearId: String) -> Unit,
    onUnequipGear: (String) -> Unit,
    onAutoDistributeGear: () -> Unit = {},
    onPurchaseOrUpgradeGear: (String) -> Unit,
    onEnlistPostBasicSoldier: (OperatorSpecialty) -> Unit = {},
    onAssignToHqStaff: (String, HqStaffRole) -> Unit = { _, _ -> },
    onReassignHqStaffRole: (String, HqStaffRole) -> Unit = { _, _ -> },
    onApproveTransferOut: (String) -> Unit = {},
    onDeclineTransferRequest: (String) -> Unit = {},
    onApproveRetirement: (String) -> Unit = {},
    onDenyRetirementRequest: (String) -> Unit = {},
    onSaveSquad: (
        squadId: String?,
        name: String,
        memberIds: List<String>,
        leaderId: String?,
        standardPatch: UnitPatchStyle,
        standardCamo: CamoPattern?,
        standardHeadgear: HeadgearStyle?,
        standardDressStyle: DressUniformStyle?
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onSaveSquadWithStandard: ((
        squadId: String?,
        name: String,
        memberIds: List<String>,
        leaderId: String?,
        standard: SquadUniformStandard,
        applyImmediately: Boolean
    ) -> Unit)? = null,
    onDisbandSquad: (String) -> Unit = {},
    onApplySquadStandard: (String) -> Unit = {},
    onConductSquadInspection: (String) -> Unit = {},
    onSwitchSquadKit: (String, WardrobeKit) -> Unit = { _, _ -> },
    onSelectWardrobeKit: (String, WardrobeKit) -> Unit = { _, _ -> },
    onNavigateToHq: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val extra = LocalAegisColors.current
    var selectedOperatorId by rememberSaveable { mutableStateOf<String?>(null) }
    var showArmoryDrawer by remember { mutableStateOf(false) }
    var selectedRecruitMos by remember { mutableStateOf(OperatorSpecialty.SIGNALS) }
    var pendingTransferConfirmOp by remember { mutableStateOf<Operator?>(null) }
    var pendingRetirementConfirmOp by remember { mutableStateOf<Operator?>(null) }
    var hqRoleTargetOpId by remember { mutableStateOf<String?>(null) }
    var showSquadDialog by remember { mutableStateOf(false) }
    var editingSquad by remember { mutableStateOf<TacticalSquad?>(null) }

    val selectedOperator = uiState.operators.find { it.id == selectedOperatorId }
    val deployedOpIds = remember(uiState.deployments) {
        uiState.deployments.flatMap { it.operatorIds }.toSet()
    }

    if (showSquadDialog) {
        BackHandler {
            showSquadDialog = false
            editingSquad = null
        }
    } else if (showArmoryDrawer) {
        BackHandler { showArmoryDrawer = false }
    }

    if (selectedOperator != null) {
        OperatorDetailView(
            operator = selectedOperator,
            uiState = uiState,
            isDeployed = selectedOperator.id in deployedOpIds,
            onBack = { selectedOperatorId = null },
            onToggleUniformMode = { onToggleUniformMode(selectedOperator.id) },
            onCustomizeOperator = { req ->
                onCustomizeOperator(selectedOperator.id, req)
            },
            onEquipGear = { gearId -> onEquipGear(selectedOperator.id, gearId) },
            onUnequipGear = onUnequipGear,
            onPurchaseOrUpgradeGear = onPurchaseOrUpgradeGear,
            onAssignToHqStaff = { role -> onAssignToHqStaff(selectedOperator.id, role) },
            onReassignHqStaffRole = { role -> onReassignHqStaffRole(selectedOperator.id, role) },
            onApproveTransferOut = { onApproveTransferOut(selectedOperator.id) },
            onDeclineTransferRequest = { onDeclineTransferRequest(selectedOperator.id) },
            onApproveRetirement = { onApproveRetirement(selectedOperator.id) },
            onDenyRetirementRequest = { onDenyRetirementRequest(selectedOperator.id) },
            onSelectWardrobeKit = { kit -> onSelectWardrobeKit(selectedOperator.id, kit) },
            modifier = modifier
        )
        return
    }

    val fieldOps = uiState.operators.filter {
        it.unlocked && !it.status.isOutOfActiveService && it.status != OperatorStatus.HQ_STAFF
    }
    val hqStaffOps = uiState.operators.filter {
        it.unlocked && it.status == OperatorStatus.HQ_STAFF
    }
    val retirementRequestOps = uiState.operators.filter {
        it.unlocked && it.retirementRequested && !it.status.isOutOfActiveService
    }
    val transferRequestOps = uiState.operators.filter {
        it.unlocked && it.transferRequested && !it.status.isOutOfActiveService
    }
    val recruitableOps = uiState.operators.filter { !it.unlocked }
    val fieldSlotsFull = fieldOps.size >= uiState.maxFieldRosterSlots
    val autoEquipEligibleCount = fieldOps.count {
        it.id !in deployedOpIds && it.status != OperatorStatus.DEPLOYED
    }
    val deployedFieldCount = fieldOps.size - autoEquipEligibleCount
    val hasUnlockedGear = remember(uiState.gearItems) {
        uiState.gearItems.any { it.unlocked }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FieldSquadsSection(
                squads = uiState.squads,
                operators = uiState.operators,
                deployedOpIds = deployedOpIds,
                smartWardrobeEnabled = uiState.smartWardrobeEnabled,
                onCreateSquadClick = {
                    editingSquad = null
                    showSquadDialog = true
                },
                onEditSquadClick = { squad ->
                    editingSquad = squad
                    showSquadDialog = true
                },
                onApplySquadStandard = onApplySquadStandard,
                onConductSquadInspection = onConductSquadInspection,
                onSwitchSquadKit = onSwitchSquadKit
            )
        }

        item {
            SectionHeader(
                title = "Active Field Units (${fieldOps.size}/${uiState.maxFieldRosterSlots})",
                subtitle = "Soldiers, NCOs & Officers deployable on field ops • SGT+ eligible for Staff or Transfer"
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onAutoDistributeGear,
                    enabled = autoEquipEligibleCount > 0 && hasUnlockedGear,
                    modifier = Modifier
                        .weight(1.15f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("auto_distribute_gear_button")
                ) {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto-Gear", maxLines = 1)
                }
                FilledTonalButton(
                    onClick = { showArmoryDrawer = true },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("open_armory_button")
                ) {
                    Icon(Icons.Filled.Handyman, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Armory", maxLines = 1)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = buildString {
                    append("Auto-Gear optimizes kit by MOS, schools & skills ($autoEquipEligibleCount standby field")
                    if (deployedFieldCount > 0) {
                        append(" • $deployedFieldCount deployed locked")
                    }
                    append(")")
                },
                style = MaterialTheme.typography.labelSmall,
                color = extra.textMuted
            )
        }

        // Veteran Legacy Endowment Banner (when 1+ veterans retired)
        if (uiState.retiredVeteransCount > 0) {
            item {
                TacticalPanelCard(
                    containerColor = extra.signalsContainer.copy(alpha = 0.25f),
                    borderColor = extra.signalsCyan.copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "VETERAN LEGACY ENDOWMENT",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.signalsCyan
                            )
                            Text(
                                text = "${uiState.veteranLegacyPctText} Base Ops Production • ${uiState.retiredVeteransCount} Retired Veteran(s)",
                                style = MaterialTheme.typography.labelMedium,
                                color = extra.brassBright
                            )
                        }
                        Text(
                            text = "Cap ${TacticalCalculator.formatBpsPercent(TacticalCalculator.MAX_VETERAN_LEGACY_BPS)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.textMuted
                        )
                    }
                }
            }
        }

        // Long-Serving Veteran Retirement Requests (Approve / Deny)
        items(retirementRequestOps, key = { "ret_req_${it.id}" }) { op ->
            val preview = TacticalCalculator.previewRetirementLegacy(
                operator = op,
                currentLegacyBps = uiState.veteranLegacyBps,
                lastRetirementAtMillis = uiState.lastRetirementAtMillis,
                nowMillis = uiState.nowMillis
            )
            TacticalPanelCard(
                containerColor = extra.signalsContainer.copy(alpha = 0.42f),
                borderColor = extra.signalsCyan
            ) {
                Text(
                    text = "RETIREMENT REQUEST • ${op.rank.code} ${op.callsign}",
                    style = MaterialTheme.typography.labelLarge,
                    color = extra.brassBright
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${op.rank.title} ${op.name} petitions for Honorable Retirement after ${op.missionsCompleted + op.staffOpsSupported} ops. Approving grants +${preview.prestigeAward} PRS and ${preview.gainPctText} Veteran Legacy (${preview.spacingPct}% spacing), freeing their slot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { pendingRetirementConfirmOp = op },
                        enabled = op.id !in deployedOpIds && op.status != OperatorStatus.DEPLOYED,
                        modifier = Modifier
                            .weight(1.3f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("approve_retirement_req_${op.id}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = extra.brass,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Text("Approve (${preview.gainPctText})", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onDenyRetirementRequest(op.id) },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("deny_retirement_req_${op.id}")
                    ) {
                        Text("Deny & Retain", maxLines = 1)
                    }
                }
            }
        }

        // PCS Transfer Requests From Senior Soldiers (SGT+)
        items(transferRequestOps, key = { "req_${it.id}" }) { op ->
            val canMoveToStaff = op.status == OperatorStatus.READY &&
                op.id !in deployedOpIds &&
                uiState.hqStaffCount < uiState.maxHqStaffSlots
            TacticalPanelCard(
                containerColor = extra.brassContainer.copy(alpha = 0.45f),
                borderColor = extra.brass
            ) {
                Text(
                    text = "PCS TRANSFER REQUEST • ${op.rank.code} ${op.callsign}",
                    style = MaterialTheme.typography.labelLarge,
                    color = extra.brassBright
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${op.rank.title} ${op.name} requests transfer to another Army unit. You can approve the transfer (frees a slot, loses control/tracking, +10 PRS), promote to HQ Staff, or retain.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (canMoveToStaff) {
                        Button(
                            onClick = { hqRoleTargetOpId = op.id },
                            modifier = Modifier
                                .weight(1.1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("HQ Role", maxLines = 1)
                        }
                    }
                    OutlinedButton(
                        onClick = { pendingTransferConfirmOp = op },
                        modifier = Modifier
                            .weight(1.1f)
                            .defaultMinSize(minHeight = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Transfer Out", maxLines = 1)
                    }
                    TextButton(
                        onClick = { onDeclineTransferRequest(op.id) },
                        modifier = Modifier
                            .weight(0.8f)
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Retain", maxLines = 1)
                    }
                }
            }
        }

        items(fieldOps, key = { it.id }) { op ->
            val equipped = uiState.gearItems.filter { it.equippedByOperatorId == op.id }
            val opMedals = uiState.citations.filter { it.operatorId == op.id }
                .mapNotNull { cit -> GameCatalog.medals.find { it.id == cit.medalId } }
                .sortedBy { it.precedenceOrder }
            val stats = TacticalCalculator.operatorStats(
                op, uiState.gearItems, uiState.citations, uiState.facilities
            )
            val isDeployed = op.id in deployedOpIds
            val statusLabel = when {
                isDeployed -> "DEPLOYED"
                op.status == OperatorStatus.RECOVERING -> "MEDBAY"
                else -> "READY"
            }

            TacticalPanelCard(
                modifier = Modifier
                    .clickable { selectedOperatorId = op.id }
                    .testTag("operator_card_${op.id}"),
                borderColor = when {
                    op.retirementRequested -> extra.signalsCyan
                    op.transferRequested -> extra.amberTelemetry
                    op.uniformMode == UniformMode.DRESS_UNIFORM -> extra.brass.copy(alpha = 0.5f)
                    else -> extra.borderSubtle
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dutyCtx = TacticalCalculator.resolveDutyContextForOperator(op, uiState.deployments)
                    val previewKit = TacticalCalculator.resolveEffectiveWardrobeKit(
                        operator = op,
                        smartWardrobeEnabled = uiState.smartWardrobeEnabled,
                        dutyContext = dutyCtx
                    )
                    OperatorUniformPreview(
                        operator = op,
                        equippedGear = equipped,
                        awardedMedals = opMedals,
                        allCitations = uiState.citations,
                        squads = uiState.squads,
                        kitOverride = previewKit,
                        modifier = Modifier
                            .width(82.dp)
                            .height(114.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                RankInsigniaBadge(rank = op.rank)
                                Text(
                                    text = op.callsign,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = when (statusLabel) {
                                    "DEPLOYED" -> extra.signalsContainer
                                    "MEDBAY" -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                }
                            ) {
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (statusLabel) {
                                        "DEPLOYED" -> extra.signalsCyan
                                        "MEDBAY" -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = "${op.rank.title} ${op.name} • ${op.specialty.roleTag}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val assignedSquad = uiState.squads.firstOrNull { op.id in it.memberIds }
                        if (assignedSquad != null) {
                            val isSquadLeader = assignedSquad.leaderId == op.id
                            Text(
                                text = if (isSquadLeader) {
                                    "★ ${assignedSquad.name} • Squad Leader (${assignedSquad.cohesionScore}% Cohesion)"
                                } else {
                                    "${assignedSquad.name} • Member (${assignedSquad.cohesionScore}% Cohesion)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSquadLeader) extra.brassBright else extra.signalsCyan
                            )
                        }

                        Text(
                            text = "PWR ${stats.totalPower}  (FP ${stats.firepower} • ST ${stats.stealth} • TC ${stats.tech} • RS ${stats.resilience})",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.amberTelemetry
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${equipped.size}/5 Kit",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.textMuted
                            )
                            val rackOnly = opMedals.filter {
                                it.category == AwardCategory.MEDAL || it.category == AwardCategory.RIBBON
                            }
                            if (rackOnly.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
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
            }
        }

        // HQ Staff Wing Section (SGT E-5+ Support Cadre)
        item {
            Spacer(modifier = Modifier.height(6.dp))
            SectionHeader(
                title = "HQ Staff Wing (${hqStaffOps.size}/${uiState.maxHqStaffSlots})",
                subtitle = if (hqStaffOps.isEmpty()) {
                    "Open HQ billets await SGT (E-5)+ & Officers — assign roles in the HQ tab or Service view"
                } else {
                    "+${uiState.hqStaffSummary.survivalBonusPct}% Survival • +${uiState.hqStaffSummary.efficiencyBonusPct}% Eff • +${uiState.hqStaffSummary.prestigeBonusPct}% PRS"
                },
                trailing = {
                    TextButton(
                        onClick = onNavigateToHq,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("squad_open_hq_tab")
                    ) {
                        Text("HQ Tab")
                    }
                }
            )
        }

        items(hqStaffOps, key = { "staff_${it.id}" }) { op ->
            val opMedals = uiState.citations.filter { it.operatorId == op.id }
                .mapNotNull { cit -> GameCatalog.medals.find { it.id == cit.medalId } }
                .sortedBy { it.precedenceOrder }
            TacticalPanelCard(
                modifier = Modifier
                    .clickable { selectedOperatorId = op.id }
                    .testTag("staff_card_${op.id}"),
                borderColor = extra.signalsCyan.copy(alpha = 0.55f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val staffKit = TacticalCalculator.resolveEffectiveWardrobeKit(
                        operator = op,
                        smartWardrobeEnabled = uiState.smartWardrobeEnabled,
                        dutyContext = DutyUniformContext.HQ_ASSIGNMENT
                    )
                    OperatorUniformPreview(
                        operator = op,
                        equippedGear = emptyList(),
                        awardedMedals = opMedals,
                        allCitations = uiState.citations,
                        squads = uiState.squads,
                        kitOverride = staffKit,
                        modifier = Modifier
                            .width(74.dp)
                            .height(104.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                RankInsigniaBadge(rank = op.rank)
                                Text(
                                    text = op.callsign,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = extra.signalsContainer
                            ) {
                                Text(
                                    text = op.hqRole?.shortCode?.let { "HQ $it" } ?: "HQ STAFF",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.signalsCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${op.rank.title} ${op.name} • ${op.hqRole?.title ?: op.specialty.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Supported ${op.staffOpsSupported} Field Ops • ${opMedals.size} Honors (+30% Staff XP/op)",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.brassBright
                        )
                    }
                }
            }
        }

        // Enlist Post-Basic Soldier (PV2 E-2, Non-Infantry & Infantry MOS options, No Special Schools)
        item {
            Spacer(modifier = Modifier.height(6.dp))
            SectionHeader(
                title = "Reception Station • Enlist Post-Basic Soldier",
                subtitle = "Arrives at PV2 (E-2) in bare AGSU with Army Service Ribbon & 0 special schools"
            )
            Spacer(modifier = Modifier.height(6.dp))
            val canEnlist = !fieldSlotsFull && uiState.resources.credits >= 45.0
            TacticalPanelCard(borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) {
                RecruitMosDropdown(
                    selectedMos = selectedRecruitMos,
                    onSelect = { selectedRecruitMos = it },
                    testTag = "dropdown_recruit_mos"
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onEnlistPostBasicSoldier(selectedRecruitMos) },
                    enabled = canEnlist,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("enlist_post_basic_button")
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        when {
                            fieldSlotsFull -> "Field Roster Full (${fieldOps.size}/${uiState.maxFieldRosterSlots})"
                            uiState.resources.credits < 45.0 -> "Need 45 CR to Enlist PV2"
                            else -> "Enlist Post-Basic PV2 (45 CR)"
                        }
                    )
                }
            }
        }

        if (recruitableOps.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                SectionHeader(
                    title = "Army Directorate Cadre Transfers",
                    subtitle = "Commissioned Officers, NCOs & Flag Officers awaiting HQ orders"
                )
            }

            items(recruitableOps, key = { it.id }) { op ->
                val canRecruit = !fieldSlotsFull &&
                    uiState.resources.credits >= op.recruitCreditCost &&
                    uiState.resources.intel >= op.recruitIntelCost
                TacticalPanelCard {
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
                                RankInsigniaBadge(rank = op.rank)
                                Text(
                                    text = "${op.callsign} • ${op.rank.tier.shortLabel}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = extra.brassBright
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${op.rank.title} ${op.name} — ${op.bio}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "RECRUIT: ${op.recruitCreditCost.toInt()} CR • ${op.recruitIntelCost.toInt()} INT",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (canRecruit) extra.brassBright else MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = { onRecruitOperator(op.id) },
                            enabled = canRecruit,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("recruit_${op.id}")
                        ) {
                            Text("Recruit")
                        }
                    }
                }
            }
        }
    }

    if (showArmoryDrawer) {
        ModalBottomSheet(
            onDismissRequest = { showArmoryDrawer = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ArmoryCatalogSheet(
                uiState = uiState,
                onPurchaseOrUpgradeGear = onPurchaseOrUpgradeGear
            )
        }
    }

    pendingTransferConfirmOp?.let { opToTransfer ->
        AlertDialog(
            onDismissRequest = { pendingTransferConfirmOp = null },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = extra.amberTelemetry)
            },
            title = {
                Text("Approve PCS Transfer for ${opToTransfer.rank.code} ${opToTransfer.callsign}?")
            },
            text = {
                Text(
                    text = "Aegis HQ will lose control and tracking of ${opToTransfer.callsign}, freeing up 1 roster slot and granting +10 PRS. Equipped gear returns to Armory.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onApproveTransferOut(opToTransfer.id)
                        pendingTransferConfirmOp = null
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
                    onClick = { pendingTransferConfirmOp = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingRetirementConfirmOp?.let { opToRetire ->
        RetirementConfirmationDialog(
            operator = opToRetire,
            uiState = uiState,
            onApproveRetirement = {
                onApproveRetirement(opToRetire.id)
                pendingRetirementConfirmOp = null
            },
            onDenyAndKeepActive = {
                if (opToRetire.retirementRequested) {
                    onDenyRetirementRequest(opToRetire.id)
                }
                pendingRetirementConfirmOp = null
            },
            onDismiss = { pendingRetirementConfirmOp = null }
        )
    }

    val roleTargetOp = uiState.operators.find { it.id == hqRoleTargetOpId }
    if (roleTargetOp != null) {
        AssignOperatorToHqRoleDialog(
            operator = roleTargetOp,
            uiState = uiState,
            onSelectRole = { role ->
                if (roleTargetOp.status == OperatorStatus.HQ_STAFF) {
                    onReassignHqStaffRole(roleTargetOp.id, role)
                } else {
                    onAssignToHqStaff(roleTargetOp.id, role)
                }
                hqRoleTargetOpId = null
            },
            onDismiss = { hqRoleTargetOpId = null }
        )
    }

    if (showSquadDialog) {
        SquadEditorDialog(
            editingSquad = editingSquad,
            allSquads = uiState.squads,
            fieldOperators = fieldOps,
            onSave = onSaveSquad,
            onSaveWithStandard = onSaveSquadWithStandard,
            onDisband = onDisbandSquad,
            onApplyStandard = onApplySquadStandard,
            onDismiss = {
                showSquadDialog = false
                editingSquad = null
            }
        )
    }
}

@Composable
private fun ArmoryCatalogSheet(
    uiState: CommandUiState,
    onPurchaseOrUpgradeGear: (String) -> Unit
) {
    val extra = LocalAegisColors.current
    var selectedSlot by remember { mutableStateOf<GearSlot?>(null) }
    val filtered = remember(uiState.gearItems, selectedSlot) {
        if (selectedSlot == null) uiState.gearItems else uiState.gearItems.filter { it.slot == selectedSlot }
    }
    val armoryLevel = remember(uiState.facilities) {
        TacticalCalculator.facilityLevel(uiState.facilities, FacilityType.ARMORY_WORKSHOP)
    }
    val armoryBonusPct = (armoryLevel - 1) * 5

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 580.dp)
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "QUARTERMASTER ARMORY",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = extra.signalsContainer
            ) {
                Text(
                    text = "Armory Lv.$armoryLevel (+${armoryBonusPct}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.signalsCyan,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Text(
            text = "Standard kit equips any soldier. Specialty kit locks behind School Training & Armory Bay tiers.",
            style = MaterialTheme.typography.bodySmall,
            color = extra.textMuted
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedSlot == null,
                onClick = { selectedSlot = null },
                label = { Text("ALL") },
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("armory_filter_all")
            )
            GearSlot.entries.forEach { slot ->
                FilterChip(
                    selected = selectedSlot == slot,
                    onClick = { selectedSlot = slot },
                    label = { Text(slot.shortLabel) },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("armory_filter_${slot.shortLabel.lowercase()}")
                )
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { gear ->
                val crCost = gear.nextActionCreditCost()
                val supCost = gear.nextActionSupplyCost()
                val reqElig = TacticalCalculator.evaluateGearRequisitionEligibility(
                    gear = gear,
                    facilities = uiState.facilities,
                    operators = uiState.operators
                )
                val canAfford = uiState.resources.credits >= crCost && uiState.resources.supplies >= supCost
                val holder = gear.equippedByOperatorId?.let { id ->
                    uiState.operators.find { it.id == id }?.callsign
                }
                val qualifiedGraduates = if (gear.requiredSchoolId != null) {
                    uiState.operators
                        .filter {
                            it.unlocked &&
                                !it.status.isOutOfActiveService &&
                                gear.requiredSchoolId in it.completedMissionIds
                        }
                        .map { it.callsign }
                } else {
                    emptyList()
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (!gear.unlocked && !reqElig.eligible) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
                        } else {
                            extra.borderSubtle
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (gear.unlocked) "${gear.name} • Mk.${gear.tier}" else "${gear.name} [LOCKED]",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (gear.unlocked || reqElig.eligible) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        extra.textMuted
                                    }
                                )
                                Text(
                                    text = "${gear.slot.label} • ${gear.weightKg} kg • +${gear.survivalBonusPct}% Surv" +
                                        (if (holder != null) " • On $holder" else ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.textMuted
                                )
                                Text(
                                    text = "FP+${gear.firepower} ST+${gear.stealth} TC+${gear.tech} RS+${gear.resilience}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (!gear.unlocked || gear.tier < gear.maxTier) {
                                Button(
                                    onClick = { onPurchaseOrUpgradeGear(gear.id) },
                                    enabled = reqElig.eligible && canAfford,
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("armory_action_${gear.id}")
                                ) {
                                    Text(
                                        text = when {
                                            !reqElig.eligible && armoryLevel < gear.requiredArmoryLevel ->
                                                "Armory Lv.${gear.requiredArmoryLevel}"
                                            !reqElig.eligible -> "Need School"
                                            !gear.unlocked -> "Buy ${crCost.toInt()}CR/${supCost.toInt()}S"
                                            else -> "Mk.${gear.tier + 1} (${crCost.toInt()}CR)"
                                        },
                                        maxLines = 1
                                    )
                                }
                            } else {
                                Text("MAX", style = MaterialTheme.typography.labelMedium, color = extra.brassBright)
                            }
                        }

                        Text(
                            text = gear.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val reqSummary = gear.requirementSummary
                        if (reqSummary != null) {
                            val reqSatisfied = reqElig.eligible
                            Text(
                                text = buildString {
                                    append(reqSummary)
                                    if (qualifiedGraduates.isNotEmpty()) {
                                        append(" • Qualified: ${qualifiedGraduates.joinToString(", ")}")
                                    } else if (gear.requiredSchoolId != null) {
                                        append(" • No active graduates yet")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (reqSatisfied) extra.brassBright else MaterialTheme.colorScheme.error
                            )
                        }
                        if (!gear.unlocked || gear.tier < gear.maxTier) {
                            Text(
                                text = "Cost: ${crCost.toInt()} CR • ${supCost.toInt()} SUP",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (canAfford) extra.textMuted else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
