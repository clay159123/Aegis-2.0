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
import androidx.compose.ui.text.style.TextOverflow
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
    onUnequipGear: (operatorId: String, gearId: String) -> Unit,
    onAutoDistributeGear: () -> Unit = {},
    onPurchaseOrUpgradeGear: (String) -> Unit,
    onEnlistRecruit: (OperatorSpecialty, RecruitSource) -> Unit = { _, _ -> },
    onAssignToHqStaff: (String, HqStaffRole) -> Unit = { _, _ -> },
    onReassignHqStaffRole: (String, HqStaffRole) -> Unit = { _, _ -> },
    onReturnHqStaffToField: (String) -> Unit = {},
    onApproveTransferOut: (String) -> Unit = {},
    onDeclineTransferRequest: (String) -> Unit = {},
    onApproveRetirement: (String) -> Unit = {},
    onDenyRetirementRequest: (String) -> Unit = {},
    onSaveSquad: (
        squadId: String?,
        name: String,
        memberIds: List<String>,
        leaderId: String?,
        teamLeaderId: String?,
        standard: SquadUniformStandard
    ) -> Unit = { _, _, _, _, _, _ -> },
    onDisbandSquad: (String) -> Unit = {},
    onAwardMarksmanship: (String) -> Unit = {},
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
    var selectedRecruitSource by remember { mutableStateOf(RecruitSource.RECEPTION) }
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
            onAwardMarksmanship = { onAwardMarksmanship(selectedOperator.id) },
            onCustomizeOperator = { req ->
                onCustomizeOperator(selectedOperator.id, req)
            },
            onEquipGear = { gearId -> onEquipGear(selectedOperator.id, gearId) },
            onUnequipGear = onUnequipGear,
            onPurchaseOrUpgradeGear = onPurchaseOrUpgradeGear,
            onAssignToHqStaff = { role -> onAssignToHqStaff(selectedOperator.id, role) },
            onReassignHqStaffRole = { role -> onReassignHqStaffRole(selectedOperator.id, role) },
            onReturnToField = { onReturnHqStaffToField(selectedOperator.id) },
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
    // Ground soldiers first, then the aviation pool (153A pilots never join squads)
    val groundOps = fieldOps.filter { !it.isAviator }
    val aviationPool = fieldOps.filter { it.isAviator }
    val rosterOrder = groundOps + aviationPool
    val firstAviatorId = aviationPool.firstOrNull()?.id
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

    // Item 67: collapsible roster sections, remembered between visits
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("aegis_ops_prefs", android.content.Context.MODE_PRIVATE) }
    var openSections by remember { mutableStateOf(prefs.getStringSet("squad_open_sections", emptySet())!!.toSet()) }
    fun toggleSection(id: String) {
        openSections = if (id in openSections) openSections - id else openSections + id
        prefs.edit().putStringSet("squad_open_sections", openSections).apply()
    }
    val rosterSections = remember(groundOps, aviationPool, uiState.squads) {
        val squadSections = uiState.squads.map { squad ->
            val members = groundOps.filter { it.id in squad.memberIds }
            RosterSection(
                id = "sq_${squad.id}",
                title = squad.name,
                summary = "${members.size} soldier${if (members.size == 1) "" else "s"} • ${squad.cohesionTier.label} (${squad.cohesionScore})",
                members = members
            )
        }
        val inSquad = uiState.squads.flatMap { it.memberIds }.toSet()
        val unassigned = groundOps.filter { it.id !in inSquad }
        squadSections +
            listOfNotNull(
                RosterSection("unassigned", "Unassigned", "${unassigned.size} soldier${if (unassigned.size == 1) "" else "s"} without a squad", unassigned)
                    .takeIf { unassigned.isNotEmpty() },
                RosterSection("aviation", "Aviation Pool", "${aviationPool.size} pilot${if (aviationPool.size == 1) "" else "s"} • fly insertions and Flight Hours; no squads", aviationPool)
                    .takeIf { aviationPool.isNotEmpty() }
            )
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

        rosterSections.forEach { section ->
        item(key = "roster_hdr_${section.id}") {
            CollapsibleRosterHeader(
                title = section.title,
                summary = section.summary,
                expanded = section.id in openSections,
                onToggle = { toggleSection(section.id) },
                modifier = Modifier.testTag("roster_section_${section.id}")
            )
        }
        if (section.id in openSections) items(section.members, key = { it.id }) { op ->
            val equipped = uiState.gearItems.equippedBy(op)
            val opMedals = uiState.citations.filter { it.operatorId == op.id }
                .mapNotNull { cit -> GameCatalog.medals.find { it.id == cit.medalId } }
                .sortedBy { it.precedenceOrder }
            val stats = TacticalCalculator.operatorStats(
                op, uiState.gearItems, uiState.citations, uiState.facilities
            )
            val isDeployed = op.id in deployedOpIds
            val onTdy = isDeployed && op.id in TacticalCalculator.tdyOperatorIdsFor(uiState.deployments)
            val statusLabel = when {
                onTdy -> "TDY"
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
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                RankInsigniaBadge(rank = op.rank)
                                Text(
                                    text = op.callsign,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = when (statusLabel) {
                                    "DEPLOYED", "TDY" -> extra.signalsContainer
                                    "MEDBAY" -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                }
                            ) {
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (statusLabel) {
                                        "DEPLOYED", "TDY" -> extra.signalsCyan
                                        "MEDBAY" -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Item 68 (early): SF chip ahead of the soldier's name line
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (TacticalCalculator.isSfQualified(op)) SfMarker(modifier = Modifier.padding(top = 1.dp))
                            Text(
                                text = "${op.rank.title} ${op.name} • ${op.specialty.roleTag}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

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
                        // Item 83: pilots show their flight hours and skill
                        if (op.isAviator) {
                            Text(
                                text = "${op.flightHoursLogged} flight hours • Flight skill ${op.flightSkill} • ${op.combatSortiesFlown} combat sorties",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.signalsCyan,
                                modifier = Modifier.testTag("pilot_hours_${op.id}")
                            )
                        }

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

        }

        // HQ Staff Wing Section (SGT E-5+ Support Cadre)
        item {
            Spacer(modifier = Modifier.height(6.dp))
            CollapsibleRosterHeader(
                title = "HQ Staff Wing",
                summary = "${hqStaffOps.size}/${uiState.maxHqStaffSlots} billets filled",
                expanded = "hq" in openSections,
                onToggle = { toggleSection("hq") },
                modifier = Modifier.testTag("roster_section_hq")
            )
        }
        if ("hq" in openSections) item {
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

        if ("hq" in openSections) items(hqStaffOps, key = { "staff_${it.id}" }) { op ->
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
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                RankInsigniaBadge(rank = op.rank)
                                Text(
                                    text = op.callsign,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
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
                        // Item 68 (early): SF chip ahead of the soldier's name line
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (TacticalCalculator.isSfQualified(op)) SfMarker(modifier = Modifier.padding(top = 1.dp))
                            Text(
                                text = "${op.rank.title} ${op.name} • ${op.hqRole?.title ?: op.specialty.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Supported ${op.staffOpsSupported} Field Ops • ${opMedals.size} Honors (+30% Staff XP/op)",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.brassBright
                        )
                    }
                }
            }
        }

        // Recruiting (item 31): Reception Station PV2s, or leadership-school graduates at higher ranks
        item {
            CollapsibleRosterHeader(
                title = "Recruiting",
                summary = "${fieldOps.size}/${uiState.maxFieldRosterSlots} field slots used • ${recruitableOps.size} cadre transfers waiting",
                expanded = "recruiting" in openSections,
                onToggle = { toggleSection("recruiting") },
                modifier = Modifier.testTag("roster_section_recruiting")
            )
        }
        if ("recruiting" in openSections) item {
            Spacer(modifier = Modifier.height(6.dp))
            SectionHeader(
                title = "Recruiting • ${selectedRecruitSource.label}",
                subtitle = if (selectedRecruitSource == RecruitSource.RECEPTION) {
                    "Arrives at PV2 (E-2) in bare AGSU with Army Service Ribbon & 0 special schools"
                } else {
                    "Arrives ${selectedRecruitSource.summary} with that school's record, one MOS school and no combat history"
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
            val recruitCost = selectedRecruitSource.creditCost
            val canEnlist = !fieldSlotsFull && uiState.resources.credits >= recruitCost
            val arrival = selectedRecruitSource.arrivalRank
            TacticalPanelCard(borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) {
                RecruitSourceDropdown(
                    selectedSource = selectedRecruitSource,
                    onSelect = { selectedRecruitSource = it },
                    testTag = "dropdown_recruit_source"
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (selectedRecruitSource == RecruitSource.WOCS_AVIATOR) {
                    Text(
                        "153A Army Aviator: Flight School complete, Aviator Badge and starting flight skill ${TacticalCalculator.FLIGHT_SCHOOL_STARTING_SKILL}. Joins the Aviation Pool.",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.brassBright
                    )
                } else {
                    RecruitMosDropdown(
                        selectedMos = selectedRecruitMos,
                        onSelect = { selectedRecruitMos = it },
                        testTag = "dropdown_recruit_mos"
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onEnlistRecruit(selectedRecruitMos, selectedRecruitSource) },
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
                            uiState.resources.credits < recruitCost -> "Need ${recruitCost.toInt()} CR for a ${arrival.code}"
                            else -> "Recruit ${arrival.code} (${recruitCost.toInt()} CR)"
                        }
                    )
                }
            }
        }

        if (recruitableOps.isNotEmpty() && "recruiting" in openSections) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                SectionHeader(
                    title = "Army Directorate Cadre Transfers",
                    subtitle = "Commissioned Officers, NCOs & Flag Officers awaiting HQ orders"
                )
            }

            if ("recruiting" in openSections) items(recruitableOps, key = { it.id }) { op ->
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
                                if (TacticalCalculator.isSfQualified(op)) SfMarker()
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
            fieldOperators = groundOps,
            onSave = onSaveSquad,
            onDisband = onDisbandSquad,
            gearItems = uiState.gearItems,
            citations = uiState.citations,
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
                // Gear types are shared; show how many soldiers carry this one
                val carrierCount = gear.carriedBy(uiState.operators.filter { it.unlocked && !it.status.isOutOfActiveService }).size
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
                                        (if (carrierCount > 0) " • Carried by $carrierCount" else ""),
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

private data class RosterSection(val id: String, val title: String, val summary: String, val members: List<Operator>)

/** Item 67: a one-line section header (name and summary) that opens and closes its section. */
@Composable
private fun CollapsibleRosterHeader(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = LocalAegisColors.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (expanded) extra.brass.copy(alpha = 0.6f) else extra.borderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .defaultMinSize(minHeight = 40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(summary, style = MaterialTheme.typography.labelSmall, color = extra.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = extra.textMuted
            )
        }
    }
}
