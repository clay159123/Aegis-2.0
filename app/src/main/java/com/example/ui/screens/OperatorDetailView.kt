package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.catalog.GameCatalog
import com.example.domain.GearAutoDistributor
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import com.example.ui.components.*
import com.example.ui.theme.LocalAegisColors
import com.example.ui.viewmodel.CommandUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class OperatorDetailTab(val label: String) {
    LOADOUT("Gear Kit"),
    UNIFORM("Uniforms"),
    SERVICE("Service")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperatorDetailView(
    operator: Operator,
    uiState: CommandUiState,
    isDeployed: Boolean,
    onBack: () -> Unit,
    onToggleUniformMode: () -> Unit,
    onCustomizeOperator: (UniformTailorRequest) -> Unit,
    onEquipGear: (String) -> Unit,
    onUnequipGear: (operatorId: String, gearId: String) -> Unit,
    onPurchaseOrUpgradeGear: (String) -> Unit,
    onAssignToHqStaff: (HqStaffRole) -> Unit = {},
    onReassignHqStaffRole: (HqStaffRole) -> Unit = {},
    onApproveTransferOut: () -> Unit = {},
    onDeclineTransferRequest: () -> Unit = {},
    onApproveRetirement: () -> Unit = {},
    onDenyRetirementRequest: () -> Unit = {},
    onSelectWardrobeKit: (WardrobeKit) -> Unit = {},
    onAwardMarksmanship: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val extra = LocalAegisColors.current
    var activeTab by remember { mutableStateOf(OperatorDetailTab.LOADOUT) }
    var pickingSlot by remember { mutableStateOf<GearSlot?>(null) }
    var showTransferConfirm by remember { mutableStateOf(false) }
    var showRetirementConfirm by remember { mutableStateOf(false) }
    var showAssignHqRoleDialog by remember { mutableStateOf(false) }
    var showUniformInspection by remember { mutableStateOf(false) }
    var inspectedAward by remember { mutableStateOf<MedalDefinition?>(null) }

    BackHandler(enabled = pickingSlot == null && !showTransferConfirm && !showRetirementConfirm && !showAssignHqRoleDialog && !showUniformInspection && inspectedAward == null) {
        onBack()
    }
    if (inspectedAward != null) {
        BackHandler { inspectedAward = null }
    } else if (showUniformInspection) {
        BackHandler { showUniformInspection = false }
    } else if (pickingSlot != null) {
        BackHandler { pickingSlot = null }
    } else if (showRetirementConfirm) {
        BackHandler { showRetirementConfirm = false }
    } else if (showTransferConfirm) {
        BackHandler { showTransferConfirm = false }
    }

    val equippedGear = remember(uiState.gearItems, operator.id) {
        uiState.gearItems.equippedBy(operator)
    }
    val operatorCitations = remember(uiState.citations, operator.id) {
        uiState.citations.filter { it.operatorId == operator.id }
    }
    val awardedMedals = remember(operatorCitations) {
        operatorCitations.mapNotNull { cit -> GameCatalog.medals.find { it.id == cit.medalId } }
            .sortedBy { it.precedenceOrder }
    }
    val stats = remember(operator, uiState.gearItems, uiState.citations, uiState.facilities) {
        TacticalCalculator.operatorStats(operator, uiState.gearItems, uiState.citations, uiState.facilities)
    }
    val loadoutWeight = remember(equippedGear) { equippedGear.sumOf { it.weightKg } }
    val nextRank = OperatorRank.nextRank(operator.rank)
    val lockedForGear = isDeployed || operator.status != OperatorStatus.READY
    val dutyCtx = remember(operator.status, uiState.deployments, operator.id) {
        TacticalCalculator.resolveDutyContextForOperator(operator, uiState.deployments)
    }
    val effectiveDutyKit = remember(operator, uiState.smartWardrobeEnabled, dutyCtx) {
        TacticalCalculator.resolveEffectiveWardrobeKit(operator, uiState.smartWardrobeEnabled, dutyCtx)
    }
    var selectedTailorKit by remember(operator.id) {
        mutableStateOf(
            if (operator.activeWardrobeKit.isAvailableFor(operator)) operator.activeWardrobeKit else WardrobeKit.ALPHAS
        )
    }
    LaunchedEffect(operator.id, operator.activeWardrobeKit, operator.status) {
        selectedTailorKit = if (operator.activeWardrobeKit.isAvailableFor(operator)) {
            operator.activeWardrobeKit
        } else {
            WardrobeKit.ALPHAS
        }
    }
    val portraitKit = if (activeTab == OperatorDetailTab.UNIFORM) selectedTailorKit else effectiveDutyKit

    if (pickingSlot != null) {
        BackHandler { pickingSlot = null }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("operator_detail_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to roster"
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RankInsigniaBadge(rank = operator.rank)
                            Text(
                                text = operator.callsign,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = buildString {
                                append("${operator.rank.title} ${operator.name} • ${operator.specialty.roleTag}")
                                if (operator.status == OperatorStatus.HQ_STAFF) {
                                    append(" • HQ ${operator.hqRole?.shortCode ?: "STAFF"}")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (operator.status == OperatorStatus.HQ_STAFF) extra.signalsCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FilledTonalButton(
                    onClick = {
                        if (operator.status == OperatorStatus.HQ_STAFF) {
                            val nextHqKit = if (selectedTailorKit == WardrobeKit.ALPHAS) WardrobeKit.BRAVOS else WardrobeKit.ALPHAS
                            selectedTailorKit = nextHqKit
                            onSelectWardrobeKit(nextHqKit)
                        } else {
                            onToggleUniformMode()
                        }
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("toggle_uniform_mode"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (portraitKit != WardrobeKit.FIELD) {
                            extra.brassContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    )
                ) {
                    Icon(
                        imageVector = if (portraitKit != WardrobeKit.FIELD) {
                            Icons.Filled.Checkroom
                        } else {
                            Icons.Filled.Shield
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = portraitKit.shortLabel,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // Uniform Figure & Live Stats Header Card
        item {
            TacticalPanelCard(
                borderColor = if (portraitKit != WardrobeKit.FIELD) {
                    extra.brass.copy(alpha = 0.6f)
                } else {
                    extra.borderSubtle
                }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(228.dp)
                                .height(294.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { showUniformInspection = true }
                                .testTag("inspect_operator_uniform")
                        ) {
                            OperatorUniformPreview(
                                operator = operator,
                                equippedGear = equippedGear,
                                awardedMedals = awardedMedals,
                                allCitations = uiState.citations,
                                squads = uiState.squads,
                                kitOverride = portraitKit,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = Color(0xCC11161F),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = "INSPECT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.brassBright,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "COMBAT POWER ${stats.totalPower}",
                            style = MaterialTheme.typography.titleMedium,
                            color = extra.brassBright
                        )
                        Text(
                            text = String.format(Locale.US, "Load: %.1f kg", loadoutWeight),
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.textMuted
                        )
                    }

                    // Rifle qualification: awarded badge, attempts left, and any badge waiting to be awarded
                    val requalsUsed = operator.schoolAttempts[TacticalCalculator.RIFLE_QUAL_MISSION_ID] ?: 0
                    Text(
                        text = "Rifle qual: ${operator.marksmanshipQual?.shortLabel ?: "Unqualified"}" +
                            (if (operator.lastQualScore > 0) " • last score ${operator.lastQualScore}" else "") +
                            " • ${(TacticalCalculator.MAX_REQUAL_ATTEMPTS - requalsUsed).coerceAtLeast(0)} requals left",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.textMuted
                    )
                    operator.pendingMarksmanshipQual?.let { pending ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${pending.shortLabel} badge ready to award",
                                style = MaterialTheme.typography.labelMedium,
                                color = extra.brassBright
                            )
                            Button(
                                onClick = onAwardMarksmanship,
                                modifier = Modifier.defaultMinSize(minHeight = 40.dp).testTag("award_pending_marksmanship")
                            ) { Text("Award Badge") }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatMeterRow("FPW", stats.firepower, accent = extra.amberTelemetry)
                            StatMeterRow("STL", stats.stealth, accent = MaterialTheme.colorScheme.primary)
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatMeterRow("TCH", stats.tech, accent = extra.signalsCyan)
                            StatMeterRow("RES", stats.resilience, accent = extra.brass)
                        }
                    }

                    // Item 40: experience gains, on their own line, with points to the next +1
                    Text(
                        text = "Experience: " + StatType.entries.joinToString("  •  ") { stat ->
                            val pts = operator.expPoints(stat)
                            val gain = TacticalCalculator.experienceGain(pts)
                            val next = TacticalCalculator.pointsToNextExperienceGain(pts)
                            "${stat.shortLabel} +$gain" + if (next != null) " ($next to next)" else " (max)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("operator_experience_line")
                    )
                    if (operator.combatJumps > 0) {
                        Text(
                            text = "Combat jumps: ${operator.combatJumps}",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.brassBright
                        )
                    }

                    val xpProgress = if (nextRank != null) {
                        val span = (nextRank.xpRequired - operator.rank.xpRequired).coerceAtLeast(1)
                        ((operator.xp - operator.rank.xpRequired).toFloat() / span.toFloat()).coerceIn(0f, 1f)
                    } else 1f
                    val crossesTier = nextRank != null && OperatorRank.isBoardGate(operator.rank, nextRank)
                    val readyAtGate = crossesTier && operator.xp >= nextRank!!.xpRequired
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                nextRank == null -> "MAX ARMY RANK • ${operator.xp} XP"
                                readyAtGate -> "READY FOR ${nextRank.tier.shortLabel.uppercase()} • ${operator.rank.code} → ${nextRank.code} (Promote in Uniforms)"
                                crossesTier -> "XP ${operator.xp} / ${nextRank.xpRequired} (${nextRank.code} Gate • Player Promotion)"
                                else -> "XP ${operator.xp} / ${nextRank.xpRequired} (${nextRank.code} ${nextRank.payGrade})"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (readyAtGate) extra.signalsCyan else extra.textMuted,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${awardedMedals.size} Honors",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.brassBright
                        )
                    }
                    LinearProgressIndicator(
                        progress = { xpProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(MaterialTheme.shapes.extraSmall),
                        color = extra.brass,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OperatorDetailTab.entries.forEach { tab ->
                    FilterChip(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        label = { Text(tab.label) },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("op_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }

        when (activeTab) {
            OperatorDetailTab.LOADOUT -> {
                item {
                    val loadoutWeightKg = remember(operator.id, uiState.gearItems) {
                        TacticalCalculator.operatorLoadoutWeight(operator, uiState.gearItems)
                    }
                    val armoryLevel = remember(uiState.facilities) {
                        TacticalCalculator.facilityLevel(uiState.facilities, FacilityType.ARMORY_WORKSHOP)
                    }
                    TacticalPanelCard(
                        containerColor = extra.signalsContainer.copy(alpha = 0.2f),
                        borderColor = extra.borderSubtle
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Kit ${equippedGear.size}/5 • ${String.format(Locale.US, "%.1f", loadoutWeightKg)} kg",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Armory Lv.$armoryLevel (+${(armoryLevel - 1) * 5}% Stats)",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.signalsCyan
                            )
                        }
                    }
                }
                if (lockedForGear) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = extra.signalsContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Kit locked while status is ${operator.status.label}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = extra.signalsCyan,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                items(GearSlot.entries, key = { it.name }) { slot ->
                    val item = equippedGear.find { it.slot == slot }
                    TacticalPanelCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = slot.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.textMuted
                                )
                                Text(
                                    text = item?.name ?: "Empty Slot",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (item != null) MaterialTheme.colorScheme.onSurface else extra.textMuted
                                )
                                if (item != null) {
                                    Text(
                                        text = "Mk.${item.tier} • ${item.weightKg} kg • +${item.survivalBonusPct}% Surv • FP+${item.firepower} ST+${item.stealth} TC+${item.tech} RS+${item.resilience}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    item.requirementSummary?.let { reqText ->
                                        Text(
                                            text = reqText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = extra.brassBright
                                        )
                                    }
                                } else {
                                    // Explain an empty slot: nothing unlocked, or nothing this soldier qualifies for
                                    GearAutoDistributor.emptySlotReason(operator, slot, uiState.gearItems)?.let { reason ->
                                        Text(
                                            text = reason,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = extra.amberTelemetry,
                                            modifier = Modifier.testTag("empty_slot_reason_${slot.name.lowercase()}")
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (item != null && !lockedForGear) {
                                    IconButton(
                                        onClick = { onUnequipGear(operator.id, item.id) },
                                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Unequip ${item.name}",
                                            tint = extra.textMuted
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { pickingSlot = slot },
                                    enabled = !lockedForGear,
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("swap_slot_${slot.name.lowercase()}")
                                ) {
                                    Text(if (item == null) "Equip" else "Swap")
                                }
                            }
                        }
                    }
                }
            }

            OperatorDetailTab.UNIFORM -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            WardrobeKit.entries.forEach { kit ->
                                val available = kit.isAvailableFor(operator)
                                FilterChip(
                                    selected = selectedTailorKit == kit,
                                    enabled = available,
                                    onClick = {
                                        selectedTailorKit = kit
                                        onSelectWardrobeKit(kit)
                                    },
                                    label = {
                                        Text(
                                            text = if (!available) "${kit.shortLabel} (HQ)" else kit.shortLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("detail_wardrobe_tab_${kit.name.lowercase()}")
                                )
                            }
                        }
                        UniformTailorStudioCard(
                            operator = operator,
                            awardedDecorations = awardedMedals,
                            onApply = onCustomizeOperator,
                            equippedGear = equippedGear,
                            squads = uiState.squads,
                            activeKit = selectedTailorKit
                        )
                    }
                }
            }

            OperatorDetailTab.SERVICE -> {
                item {
                    TacticalPanelCard {
                        Text(
                            text = operator.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (operator.status == OperatorStatus.HQ_STAFF) {
                                "${operator.specialty.label} (${operator.specialty.roleTag}) • HQ Staff (${operator.staffOpsSupported} Ops Supported, ${operator.missionsCompleted} Field Ops) • ${awardedMedals.size} Honors"
                            } else {
                                "${operator.specialty.label} (${operator.specialty.roleTag}) • ${operator.missionsCompleted} Ops • ${awardedMedals.size} Honors"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = extra.brassBright
                        )
                    }
                }

                if (operator.status == OperatorStatus.RETIRED) {
                    item {
                        TacticalPanelCard(
                            containerColor = extra.brassContainer.copy(alpha = 0.35f),
                            borderColor = extra.brass
                        ) {
                            Text(
                                text = "HONORABLY RETIRED VETERAN",
                                style = MaterialTheme.typography.labelLarge,
                                color = extra.brassBright
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${operator.rank.code} ${operator.callsign} retired with honor after ${operator.missionsCompleted} Field Ops and ${operator.staffOpsSupported} HQ Staff Ops. Their permanent Veteran Legacy (${uiState.veteranLegacyPctText} total Base Ops endowment) strengthens HQ production.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Career Crossroads: HQ Staff Billet OR Transfer to Another Unit OR Honorable Retirement (SGT E-5+)
                if (!operator.status.isOutOfActiveService) {
                    item {
                        val eligible = operator.rank.isCareerEligibleForStaffOrTransfer
                        val canAssignStaff = eligible &&
                            !isDeployed &&
                            operator.status == OperatorStatus.READY &&
                            uiState.hqStaffCount < uiState.maxHqStaffSlots
                        val canTransfer = eligible && !isDeployed
                        val retirementElig = TacticalCalculator.evaluateRetirementEligibility(operator)
                        val retirementPreview = TacticalCalculator.previewRetirementLegacy(
                            operator = operator,
                            currentLegacyBps = uiState.veteranLegacyBps,
                            lastRetirementAtMillis = uiState.lastRetirementAtMillis,
                            nowMillis = uiState.nowMillis
                        )
                        val hasPendingPetition = operator.transferRequested || operator.retirementRequested

                        TacticalPanelCard(
                            containerColor = if (hasPendingPetition) extra.brassContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                            borderColor = when {
                                operator.retirementRequested -> extra.signalsCyan
                                operator.transferRequested -> extra.brass
                                else -> extra.borderSubtle
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when {
                                        operator.retirementRequested -> "RETIREMENT REQUEST PENDING • COMMAND DECISION"
                                        operator.transferRequested -> "PCS REQUEST PENDING • CAREER CROSSROADS"
                                        else -> "ARMY CAREER CROSSROADS (SGT E-5+)"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (hasPendingPetition) extra.brassBright else extra.signalsCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = when {
                                    !eligible -> "${operator.rank.code} is currently ${operator.rank.title}. Reaching SGT (E-5) unlocks HQ Staff billets, unit transfers, or long-service Honorable Retirement (${TacticalCalculator.MIN_RETIREMENT_OPS}+ ops)."
                                    operator.retirementRequested -> "${operator.rank.code} ${operator.callsign} has petitioned for Honorable Retirement after ${operator.missionsCompleted + operator.staffOpsSupported} ops. Approving grants +${retirementPreview.prestigeAward} PRS and ${retirementPreview.gainPctText} permanent Veteran Legacy (${retirementPreview.spacingPct}% spacing), freeing their slot. Denying keeps them on active duty."
                                    operator.status == OperatorStatus.HQ_STAFF -> {
                                        val role = operator.hqRole
                                        if (role != null) {
                                            "Serving as ${role.shortCode} (${role.title}): ${role.bonusSummary}. Continues earning XP & honors supporting field ops."
                                        } else {
                                            "${operator.callsign} serves on HQ Staff and continues earning XP & honors. Assign a specialized billet below or approve a unit transfer."
                                        }
                                    }
                                    operator.transferRequested -> "${operator.rank.code} ${operator.callsign} has requested transfer orders to another Army command. Assign an open HQ Role, approve the transfer (+10 PRS, loses tracking & frees slot), or retain."
                                    else -> "Senior soldier eligible for an open HQ Staff role (${uiState.hqStaffCount}/${uiState.maxHqStaffSlots} billets filled), transfer (+10 PRS), or Honorable Retirement (${TacticalCalculator.MIN_RETIREMENT_OPS}+ ops for Veteran Legacy)."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (eligible) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (operator.retirementRequested) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { showRetirementConfirm = true },
                                                enabled = !isDeployed,
                                                modifier = Modifier
                                                    .weight(1.3f)
                                                    .defaultMinSize(minHeight = 48.dp)
                                                    .testTag("review_retirement_${operator.id}"),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = extra.brass,
                                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                                )
                                            ) {
                                                Text("Approve (${retirementPreview.gainPctText})")
                                            }
                                            OutlinedButton(
                                                onClick = onDenyRetirementRequest,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .defaultMinSize(minHeight = 48.dp)
                                                    .testTag("deny_retirement_${operator.id}")
                                            ) {
                                                Text("Deny & Retain")
                                            }
                                        }
                                    }

                                    if (operator.status != OperatorStatus.HQ_STAFF) {
                                        Button(
                                            onClick = { showAssignHqRoleDialog = true },
                                            enabled = canAssignStaff,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("assign_hq_staff_${operator.id}")
                                        ) {
                                            Icon(Icons.Filled.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                if (uiState.hqStaffCount >= uiState.maxHqStaffSlots) {
                                                    "HQ Staff Full (${uiState.hqStaffCount}/${uiState.maxHqStaffSlots})"
                                                } else {
                                                    "Choose Open HQ Role (Frees Field Slot)"
                                                }
                                            )
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { showAssignHqRoleDialog = true },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("reassign_hq_staff_${operator.id}")
                                        ) {
                                            Icon(Icons.Filled.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Change HQ Billet Role")
                                        }
                                    }

                                    if (!operator.retirementRequested) {
                                        OutlinedButton(
                                            onClick = { showRetirementConfirm = true },
                                            enabled = retirementElig.eligible && !isDeployed,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("retire_operator_${operator.id}")
                                        ) {
                                            Icon(Icons.Filled.MilitaryTech, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                if (retirementElig.eligible) {
                                                    "Honorable Retirement (${retirementPreview.gainPctText} Legacy)"
                                                } else {
                                                    "Retire: ${retirementElig.reasonText}"
                                                }
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (operator.transferRequested) {
                                            OutlinedButton(
                                                onClick = onDeclineTransferRequest,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .defaultMinSize(minHeight = 48.dp)
                                            ) {
                                                Text("Retain")
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = { showTransferConfirm = true },
                                            enabled = canTransfer,
                                            modifier = Modifier
                                                .weight(1.2f)
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("transfer_unit_${operator.id}"),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Text("Transfer Unit (+10 PRS)")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (operatorCitations.isEmpty()) {
                    item {
                        TacticalPanelCard {
                            Text(
                                text = "No honors pinned yet. Visit the Uniforms tab to award ribbons, medals, cords, or specialty school insignia.",
                                style = MaterialTheme.typography.bodySmall,
                                color = extra.textMuted
                            )
                        }
                    }
                } else {
                    items(operatorCitations, key = { it.id }) { citation ->
                        val medal = GameCatalog.medals.find { it.id == citation.medalId }
                        if (medal != null) {
                            val dateStr = remember(citation.awardedAtMillis) {
                                SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(citation.awardedAtMillis))
                            }
                            TacticalPanelCard(borderColor = extra.brass.copy(alpha = 0.45f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AwardVisualThumbnail(medal = medal)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = medal.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = extra.brassBright
                                        )
                                        Text(
                                            text = "${medal.category.label} • ${citation.missionTitle} • $dateStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = extra.textMuted
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "\"${citation.citationNote}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val slotToPick = pickingSlot
    if (slotToPick != null) {
        val slotItems = uiState.gearItems.filter { it.slot == slotToPick }
        ModalBottomSheet(
            onDismissRequest = { pickingSlot = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${slotToPick.label.uppercase()} ARMORY",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                slotItems.forEach { gear ->
                    val equippedHere = operator.equippedGearIds[gear.slot] == gear.id
                    val crCost = gear.nextActionCreditCost()
                    val supCost = gear.nextActionSupplyCost()
                    val canAfford = uiState.resources.credits >= crCost && uiState.resources.supplies >= supCost
                    val equipElig = TacticalCalculator.evaluateGearEquipEligibility(gear, operator)
                    val reqElig = TacticalCalculator.evaluateGearRequisitionEligibility(
                        gear = gear,
                        facilities = uiState.facilities,
                        operators = uiState.operators
                    )

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            when {
                                equippedHere -> MaterialTheme.colorScheme.primary
                                !equipElig.eligible -> MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                                else -> extra.borderSubtle
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (gear.unlocked) "${gear.name} (Mk.${gear.tier})" else "${gear.name} [LOCKED]",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${gear.weightKg} kg • +${gear.survivalBonusPct}% Surv • FP+${gear.firepower} ST+${gear.stealth} TC+${gear.tech} RS+${gear.resilience}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = gear.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            gear.requirementSummary?.let { reqText ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (!equipElig.eligible) {
                                        "${operator.callsign} unqualified • ${equipElig.reasonText}"
                                    } else {
                                        reqText
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (equipElig.eligible && reqElig.eligible) {
                                        extra.brassBright
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (gear.unlocked) {
                                    Button(
                                        onClick = {
                                            onEquipGear(gear.id)
                                            pickingSlot = null
                                        },
                                        enabled = !equippedHere && equipElig.eligible,
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("equip_gear_${gear.id}")
                                    ) {
                                        Text(
                                            when {
                                                equippedHere -> "Equipped"
                                                !equipElig.eligible -> "Needs School"
                                                else -> "Equip"
                                            }
                                        )
                                    }
                                    if (gear.tier < gear.maxTier) {
                                        OutlinedButton(
                                            onClick = { onPurchaseOrUpgradeGear(gear.id) },
                                            enabled = reqElig.eligible && canAfford,
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = 48.dp)
                                        ) {
                                            Text(
                                                if (!reqElig.eligible) "Armory Lv.${gear.requiredArmoryLevel}"
                                                else "Mk.${gear.tier + 1} (${crCost.toInt()}CR/${supCost.toInt()}S)"
                                            )
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { onPurchaseOrUpgradeGear(gear.id) },
                                        enabled = reqElig.eligible && canAfford,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("requisition_gear_${gear.id}")
                                    ) {
                                        Text(
                                            if (!reqElig.eligible) {
                                                "Locked: ${reqElig.reasonText}"
                                            } else {
                                                "Requisition (${crCost.toInt()} CR • ${supCost.toInt()} SUP)"
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRetirementConfirm) {
        RetirementConfirmationDialog(
            operator = operator,
            uiState = uiState,
            onApproveRetirement = {
                showRetirementConfirm = false
                onApproveRetirement()
                onBack()
            },
            onDenyAndKeepActive = {
                showRetirementConfirm = false
                if (operator.retirementRequested) {
                    onDenyRetirementRequest()
                }
            },
            onDismiss = { showRetirementConfirm = false }
        )
    }

    if (showTransferConfirm) {
        AlertDialog(
            onDismissRequest = { showTransferConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = extra.amberTelemetry)
            },
            title = {
                Text("Transfer ${operator.rank.code} ${operator.callsign}?", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text(
                    text = "Approving PCS orders removes ${operator.callsign} permanently from Aegis HQ. We lose command control and career tracking, equipped gear returns to the Armory, a roster slot opens, and HQ receives +10 PRS.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTransferConfirm = false
                        onApproveTransferOut()
                        onBack()
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("confirm_transfer_out"),
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
                    onClick = { showTransferConfirm = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUniformInspection) {
        val inspectDisplayTarget = if (portraitKit == WardrobeKit.FIELD) WardrobeKit.ALPHAS else portraitKit
        val inspectCurMode = TacticalCalculator.resolveKitAppearance(
            operator = operator,
            kit = inspectDisplayTarget,
            squads = uiState.squads
        ).dressDisplayMode
        UniformInspectionDialog(
            operator = operator,
            equippedGear = equippedGear,
            awardedMedals = awardedMedals,
            allCitations = uiState.citations,
            squads = uiState.squads,
            kitOverride = portraitKit,
            onToggleUniformMode = onToggleUniformMode,
            onToggleDressDisplayMode = {
                onCustomizeOperator(
                    UniformTailorRequest.fromOperator(operator, inspectDisplayTarget).copy(
                        uniformMode = UniformMode.DRESS_UNIFORM,
                        wardrobeKit = inspectDisplayTarget,
                        dressDisplayMode = if (inspectCurMode == DressDisplayMode.FULL_MEDALS) {
                            DressDisplayMode.RIBBONS
                        } else {
                            DressDisplayMode.FULL_MEDALS
                        }
                    )
                )
            },
            onInspectAward = { inspectedAward = it },
            onDismiss = { showUniformInspection = false }
        )
    }

    if (showAssignHqRoleDialog) {
        AssignOperatorToHqRoleDialog(
            operator = operator,
            uiState = uiState,
            onSelectRole = { role ->
                if (operator.status == OperatorStatus.HQ_STAFF) {
                    onReassignHqStaffRole(role)
                } else {
                    onAssignToHqStaff(role)
                }
                showAssignHqRoleDialog = false
            },
            onDismiss = { showAssignHqRoleDialog = false }
        )
    }

    val awardToInspect = inspectedAward
    if (awardToInspect != null) {
        AwardInspectionDialog(
            medal = awardToInspect,
            heldByOperators = operatorCitations.filter { it.medalId == awardToInspect.id }.map { operator to it },
            onDismiss = { inspectedAward = null }
        )
    }
}
