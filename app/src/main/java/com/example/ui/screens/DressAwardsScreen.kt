package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.catalog.GameCatalog
import com.example.domain.PromotionEndorsementFocus
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import com.example.ui.components.*
import com.example.ui.theme.LocalAegisColors
import com.example.ui.viewmodel.CommandUiState

@Composable
fun DressAwardsScreen(
    uiState: CommandUiState,
    onToggleUniformMode: (String) -> Unit,
    onToggleDressDisplayMode: (String) -> Unit,
    onPromoteOperator: (String, PromotionEndorsementFocus?) -> Unit,
    onCustomizeOperator: (
        operatorId: String,
        request: UniformTailorRequest
    ) -> Unit,
    onAwardMedal: (operatorId: String, medalId: String, citationNote: String, missionTitle: String) -> Unit,
    onDismissDeed: (String) -> Unit,
    onConductInspection: (String) -> Unit = {},
    onNavigateToSquad: () -> Unit = {},
    onSelectWardrobeKit: (String, WardrobeKit) -> Unit = { _, _ -> },
    onToggleSmartWardrobe: () -> Unit = {},
    onConductKitInspection: ((String, WardrobeKit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val extra = LocalAegisColors.current
    val unlockedOps = remember(uiState.operators) {
        uiState.operators.filter { it.unlocked && it.status != OperatorStatus.RETIRED }
    }
    var selectedOpId by rememberSaveable {
        mutableStateOf(
            uiState.pendingDeeds.firstOrNull()?.operatorId
                ?: unlockedOps.firstOrNull()?.id
        )
    }
    LaunchedEffect(unlockedOps) {
        if (selectedOpId == null || unlockedOps.none { it.id == selectedOpId }) {
            selectedOpId = uiState.pendingDeeds.firstOrNull()?.operatorId
                ?: unlockedOps.firstOrNull()?.id
        }
    }
    val selectedOp = unlockedOps.find { it.id == selectedOpId } ?: unlockedOps.firstOrNull()

    var showTailorPanel by remember { mutableStateOf(false) }
    var showUniformInspection by remember { mutableStateOf(false) }
    var inspectedAward by remember { mutableStateOf<MedalDefinition?>(null) }
    var selectedCategory by remember { mutableStateOf<AwardCategory?>(null) }
    var citationModalMedal by remember { mutableStateOf<Pair<MedalDefinition, PendingDeed?>?>(null) }
    var showPromotionBoard by remember { mutableStateOf(false) }

    if (showPromotionBoard) {
        BackHandler { showPromotionBoard = false }
    } else if (citationModalMedal != null) {
        BackHandler { citationModalMedal = null }
    } else if (inspectedAward != null) {
        BackHandler { inspectedAward = null }
    } else if (showUniformInspection) {
        BackHandler { showUniformInspection = false }
    }

    if (selectedOp == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            TacticalPanelCard(
                modifier = Modifier.widthIn(max = 520.dp),
                borderColor = extra.brass.copy(alpha = 0.55f)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Checkroom,
                        contentDescription = null,
                        tint = extra.brassBright,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "No Active or Memorial Personnel",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "All active roster billets are empty. Enlist a post-Basic recruit or manage personnel in Squad, or inspect Retired Veterans in the Hall of Honor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onNavigateToSquad,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("dress_empty_go_squad"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = extra.brass,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Groups,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Go to Squad")
                    }
                }
            }
        }
        return
    }

    val opGear = remember(uiState.gearItems, selectedOp.id) {
        uiState.gearItems.equippedBy(selectedOp)
    }
    val opCitations = remember(uiState.citations, selectedOp.id) {
        uiState.citations.filter { it.operatorId == selectedOp.id }
    }
    val ownedMedalIds = remember(opCitations) {
        opCitations.map { it.medalId }.toSet()
    }
    val citationCountsByMedalId = remember(opCitations) {
        opCitations.groupingBy { it.medalId }.eachCount()
    }
    val awardedMedalDefs = remember(opCitations) {
        opCitations.mapNotNull { c -> GameCatalog.medals.find { it.id == c.medalId } }
            .distinctBy { it.id }
            .sortedBy { it.precedenceOrder }
    }
    val opDeeds = remember(uiState.pendingDeeds, selectedOp.id) {
        uiState.pendingDeeds.filter { it.operatorId == selectedOp.id }
    }
    val nextRank = OperatorRank.nextRank(selectedOp.rank)
    var previewKit by remember(selectedOp.id) {
        mutableStateOf(
            if (selectedOp.activeWardrobeKit.isAvailableFor(selectedOp)) {
                selectedOp.activeWardrobeKit
            } else {
                WardrobeKit.ALPHAS
            }
        )
    }
    LaunchedEffect(selectedOp.id, selectedOp.activeWardrobeKit, selectedOp.status) {
        previewKit = if (selectedOp.activeWardrobeKit.isAvailableFor(selectedOp)) {
            selectedOp.activeWardrobeKit
        } else {
            WardrobeKit.ALPHAS
        }
    }

    val filteredCatalog = remember(selectedCategory) {
        if (selectedCategory == null) GameCatalog.medals
        else GameCatalog.medals.filter { it.category == selectedCategory }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(showTailorPanel) {
        if (showTailorPanel) {
            listState.animateScrollToItem(2)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("dress_awards_list"),
        contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Organized Personnel Roster Switcher & Categorized Directory
        item {
            DressPersonnelSwitcherCard(
                unlockedOps = unlockedOps,
                selectedOp = selectedOp,
                squads = uiState.squads,
                deployments = uiState.deployments,
                pendingDeeds = uiState.pendingDeeds,
                citations = uiState.citations,
                gearItems = uiState.gearItems,
                facilities = uiState.facilities,
                onSelectOperator = { id -> selectedOpId = id }
            )
        }

        // Army Dress Uniform & Rank Showcase Pedestal
        item {
            TacticalPanelCard(borderColor = extra.brass.copy(alpha = 0.65f)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header: Branch/Tier + Sovereign Star + Rank Insignia
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = selectedOp.branch.shortCode,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extra.signalsCyan,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = selectedOp.rank.tier.shortLabel.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.brassBright
                                )
                            }
                            Text(
                                text = selectedOp.callsign,
                                style = MaterialTheme.typography.headlineSmall,
                                color = extra.brassBright
                            )
                            Text(
                                text = "${selectedOp.rank.title} ${selectedOp.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            RankInsigniaBadge(rank = selectedOp.rank)
                            Text(
                                text = when (selectedOp.status) {
                                    OperatorStatus.FALLEN -> "Memorial • ${awardedMedalDefs.size} Honors"
                                    OperatorStatus.HQ_STAFF -> "HQ Staff (${selectedOp.staffOpsSupported} Ops) • ${awardedMedalDefs.size} Honors"
                                    else -> "${selectedOp.missionsCompleted} Ops • ${awardedMedalDefs.size} Honors"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selectedOp.status == OperatorStatus.HQ_STAFF) extra.signalsCyan else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (awardedMedalDefs.any { it.isNeckMedallion }) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = extra.brassContainer
                        ) {
                            Text(
                                text = "SOVEREIGN STAR RECIPIENT",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.brassBright,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Smart Duty-Dress Auto Mode Banner
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (uiState.smartWardrobeEnabled) {
                            extra.signalsContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            1.dp,
                            if (uiState.smartWardrobeEnabled) extra.signalsCyan.copy(alpha = 0.7f) else extra.borderSubtle
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (uiState.smartWardrobeEnabled) {
                                        "Smart Duty-Dress: AUTO"
                                    } else {
                                        "Smart Duty-Dress: MANUAL"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (uiState.smartWardrobeEnabled) extra.signalsCyan else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (uiState.smartWardrobeEnabled) {
                                        "Field (Combat) • Bravos (Base/School) • Alphas (HQ) • Mess (Diplo)"
                                    } else {
                                        "Soldiers always wear their selected Wardrobe slot across menus & ops"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.textMuted
                                )
                            }
                            Switch(
                                checked = uiState.smartWardrobeEnabled,
                                onCheckedChange = { onToggleSmartWardrobe() },
                                modifier = Modifier.testTag("toggle_smart_wardrobe")
                            )
                        }
                    }

                    // Large Centered Ceremonial Portrait
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(264.dp)
                                .height(340.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { showUniformInspection = true }
                                .testTag("inspect_dress_uniform")
                        ) {
                            OperatorUniformPreview(
                                operator = selectedOp,
                                equippedGear = opGear,
                                awardedMedals = awardedMedalDefs,
                                allCitations = uiState.citations,
                                squads = uiState.squads,
                                kitOverride = previewKit,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = Color(0xCC11161F),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = previewKit.shortLabel.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.signalsCyan,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = Color(0xCC11161F),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "INSPECT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.brassBright,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // 4-Kit Wardrobe Selector Strip
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            WardrobeKit.entries.forEach { kit ->
                                val available = kit.isAvailableFor(selectedOp)
                                val selected = previewKit == kit
                                FilterChip(
                                    selected = selected,
                                    enabled = available,
                                    onClick = {
                                        previewKit = kit
                                        onSelectWardrobeKit(selectedOp.id, kit)
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
                                        .testTag("wardrobe_tab_${kit.name.lowercase()}")
                                )
                            }
                        }
                        Text(
                            text = "${previewKit.label}: ${previewKit.dutyHint}",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.textMuted
                        )
                    }

                    // Rank Promotion Box
                    val boardEval = remember(
                        selectedOp,
                        uiState.operators,
                        uiState.citations,
                        uiState.gearItems,
                        uiState.facilities
                    ) {
                        TacticalCalculator.evaluatePromotionBoard(
                            operator = selectedOp,
                            allOperators = uiState.operators,
                            citations = uiState.citations,
                            gearItems = uiState.gearItems,
                            facilities = uiState.facilities
                        )
                    }
                    if (nextRank != null && selectedOp.status != OperatorStatus.FALLEN) {
                        val promoCr = boardEval.creditCost
                        val promoPrs = boardEval.prestigeCost
                        val canAfford = uiState.resources.credits >= promoCr && uiState.resources.prestige >= promoPrs
                        val crossesTier = boardEval.isCrossTierGate
                        val readyForBoard = crossesTier && boardEval.allMeritsMet
                        val metCount = boardEval.meritChecks.count { it.satisfied }
                        val totalChecks = boardEval.meritChecks.size

                        Text(
                            text = when {
                                readyForBoard -> "PROMOTION BOARD READY (${selectedOp.rank.code} → ${nextRank.code} ${nextRank.tier.shortLabel}) — Review Board & Pin"
                                crossesTier -> "PROMOTION BOARD GATE ($metCount/$totalChecks Merits • ${selectedOp.xp}/${nextRank.xpRequired} XP) — ${selectedOp.rank.code} → ${nextRank.code}"
                                else -> "WITHIN-CATEGORY XP: ${selectedOp.xp}/${nextRank.xpRequired} → ${nextRank.code} (auto on XP, or promote early)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                readyForBoard -> extra.signalsCyan
                                crossesTier -> extra.brassBright
                                else -> extra.textMuted
                            }
                        )
                        if (readyForBoard) {
                            Button(
                                onClick = { showPromotionBoard = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("promote_operator_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = extra.signalsCyan,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Convene ${nextRank.tier.shortLabel} Board: ${selectedOp.rank.code} → ${nextRank.code} (${promoCr.toInt()} CR / $promoPrs PRS)",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        } else if (crossesTier) {
                            OutlinedButton(
                                onClick = { showPromotionBoard = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("promote_operator_button"),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Inspect ${nextRank.tier.shortLabel} Board ($metCount/$totalChecks Merits) • ${promoCr.toInt()} CR / $promoPrs PRS",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onPromoteOperator(selectedOp.id, null) },
                                enabled = canAfford,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("promote_operator_button"),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Promote to ${nextRank.code} (${nextRank.payGrade}) • ${promoCr.toInt()} CR / $promoPrs PRS",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        }
                    } else if (nextRank == null) {
                        Text(
                            text = "MAXIMUM ARMY RANK (O-10)",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.brassBright
                        )
                    }

                    // Quick Uniform View, Inspection & Tailor Toggles
                    if (uiState.inspectionBuffActive) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = extra.signalsContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "INSPECTION READINESS BUFF ACTIVE: +8% EFF • +10% PRS (${uiState.inspectionBuffRemainingSec}s left)",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.signalsCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val nextKit = if (previewKit == WardrobeKit.FIELD) {
                                    WardrobeKit.ALPHAS
                                } else if (WardrobeKit.FIELD.isAvailableFor(selectedOp)) {
                                    WardrobeKit.FIELD
                                } else {
                                    WardrobeKit.BRAVOS
                                }
                                previewKit = nextKit
                                onSelectWardrobeKit(selectedOp.id, nextKit)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("dress_toggle_mode"),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(
                                if (previewKit == WardrobeKit.FIELD) "Dress" else if (WardrobeKit.FIELD.isAvailableFor(selectedOp)) "Field" else "Bravos",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }
                        OutlinedButton(
                            onClick = { onToggleDressDisplayMode(selectedOp.id) },
                            modifier = Modifier
                                .weight(1.1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("dress_toggle_display_mode"),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(
                                when {
                                    previewKit == WardrobeKit.MESS -> "Mini Medals"
                                    previewKit == WardrobeKit.BRAVOS -> "Ribbons"
                                    selectedOp.dressDisplayMode == DressDisplayMode.FULL_MEDALS -> "Medals"
                                    selectedOp.dressDisplayMode == DressDisplayMode.CLEAN -> "No Rack"
                                    else -> "Ribbons"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                if (onConductKitInspection != null) {
                                    onConductKitInspection(selectedOp.id, previewKit)
                                } else {
                                    onConductInspection(selectedOp.id)
                                }
                            },
                            enabled = selectedOp.status != OperatorStatus.FALLEN,
                            modifier = Modifier
                                .weight(1.1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("conduct_uniform_inspection_button"),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(
                                "Inspect",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }
                        Button(
                            onClick = { showTailorPanel = !showTailorPanel },
                            modifier = Modifier
                                .weight(1.05f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("toggle_tailor_panel"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = extra.brass,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(
                                if (showTailorPanel) "Done" else "Tailor",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Awarded Regalia Summary Strip
                if (awardedMedalDefs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = extra.borderSubtle)
                    Spacer(modifier = Modifier.height(8.dp))
                    val earnedMedalsAndRibbons = awardedMedalDefs.count {
                        it.category == AwardCategory.MEDAL || it.category == AwardCategory.RIBBON
                    }
                    val earnedCords = awardedMedalDefs.count { it.category == AwardCategory.SHOULDER_CORD }
                    val earnedInsignia = awardedMedalDefs.count { it.category == AwardCategory.SCHOOL_INSIGNIA }
                    Text(
                        text = "AWARDED HONORS, CORDS & SCHOOL INSIGNIA (${awardedMedalDefs.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.textMuted
                    )
                    Text(
                        text = "Career Record: $earnedMedalsAndRibbons Medals/Ribbons • $earnedCords Cords • $earnedInsignia School Insignia (all earned stats stack)",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.brassBright
                    )
                    Text(
                        text = "Open Tailor to choose exact patches, tabs, cords, school pins & medals displayed on the uniform",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.signalsCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        awardedMedalDefs.forEach { medal ->
                            val count = citationCountsByMedalId[medal.id] ?: 1
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(74.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { inspectedAward = medal }
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                            ) {
                                AwardVisualThumbnail(medal = medal)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (count > 1) "${medal.shortName} ×$count" else medal.shortName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (count > 1) extra.brassBright else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Collapsible Army Dress Uniform Tailoring Studio (Dropdown Categories)
        if (showTailorPanel) {
            item(key = "tailor_studio_panel") {
                UniformTailorStudioCard(
                    operator = selectedOp,
                    awardedDecorations = awardedMedalDefs,
                    onApply = { req -> onCustomizeOperator(selectedOp.id, req) },
                    onClose = { showTailorPanel = false },
                    equippedGear = opGear,
                    squads = uiState.squads,
                    activeKit = previewKit
                )
            }
        }

        // Pending Deeds Recommended From Recent Missions
        if (opDeeds.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recent Mission Deeds",
                    subtitle = "Recommended awards based on ${selectedOp.callsign}'s field actions"
                )
            }
            items(opDeeds, key = { it.id }) { deed ->
                val medal = GameCatalog.medals.find { it.id == deed.recommendedMedalId }
                if (medal != null) {
                    val alreadyHas = medal.id in ownedMedalIds
                    val isRepeatable = TacticalCalculator.isRepeatableAward(medal)
                    val heldCount = citationCountsByMedalId[medal.id] ?: 0
                    val eligibility = TacticalCalculator.evaluateAwardEligibility(
                        medal = medal,
                        operator = selectedOp,
                        ownedMedalIds = ownedMedalIds,
                        allCitations = uiState.citations,
                        heroismCited = true
                    )
                    val canAfford = uiState.resources.prestige >= medal.prestigeCost
                    TacticalPanelCard(
                        containerColor = extra.brassContainer.copy(alpha = 0.38f),
                        borderColor = extra.brass
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AwardVisualThumbnail(medal = medal)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${deed.missionTitle} • ${medal.category.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.brassBright
                                )
                                Text(
                                    text = "Recommend: ${medal.name}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = deed.deedSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onDismissDeed(deed.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("File Only")
                            }
                            Button(
                                onClick = { citationModalMedal = medal to deed },
                                enabled = (!alreadyHas || isRepeatable) && eligibility.eligible && canAfford,
                                modifier = Modifier
                                    .weight(1.6f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("present_deed_${deed.id}"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = extra.brass,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                )
                            ) {
                                Text(
                                    when {
                                        alreadyHas && !isRepeatable -> "Already Held"
                                        !eligibility.eligible -> "Locked: ${eligibility.missingRequirements.firstOrNull()}"
                                        !canAfford -> "Need ${medal.prestigeCost} PRS"
                                        alreadyHas && isRepeatable -> "Award Cluster ×${heldCount + 1} (${medal.prestigeCost} PRS)"
                                        else -> "Award (${medal.prestigeCost} PRS)"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Awardable Decorations Catalog + Category Tabs
        item {
            SectionHeader(
                title = "Army Awards & Regalia (${uiState.resources.prestige.toInt()} PRS)",
                subtitle = "Medals, Ribbons, Shoulder Cords & Specialty School Insignia"
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All (${GameCatalog.medals.size})") },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                )
                AwardCategory.entries.forEach { cat ->
                    val count = GameCatalog.medals.count { it.category == cat }
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text("${cat.pluralLabel} ($count)") },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("award_filter_${cat.name.lowercase()}")
                    )
                }
            }
        }

        items(filteredCatalog, key = { "${selectedOp.id}_${it.id}" }) { medal ->
            val alreadyHeld = medal.id in ownedMedalIds
            val isRepeatable = TacticalCalculator.isRepeatableAward(medal)
            val heldCount = citationCountsByMedalId[medal.id] ?: 0
            val hasDeed = opDeeds.any { it.recommendedMedalId == medal.id }
            val eligibility = TacticalCalculator.evaluateAwardEligibility(
                medal = medal,
                operator = selectedOp,
                ownedMedalIds = ownedMedalIds,
                allCitations = uiState.citations,
                heroismCited = hasDeed
            )
            val canAfford = uiState.resources.prestige >= medal.prestigeCost
            val perSoldierCap = TacticalCalculator.perSoldierCapForAward(medal.id)

            AwardCatalogCard(
                medal = medal,
                selectedOp = selectedOp,
                alreadyHeld = alreadyHeld,
                isRepeatable = isRepeatable,
                heldCount = heldCount,
                hasDeed = hasDeed,
                eligibility = eligibility,
                canAfford = canAfford,
                perSoldierCap = perSoldierCap,
                onInspect = { inspectedAward = medal },
                onConfer = { citationModalMedal = medal to opDeeds.firstOrNull { it.recommendedMedalId == medal.id } }
            )
        }
    }

    val modalInfo = citationModalMedal
    if (modalInfo != null) {
        val (medal, deed) = modalInfo
        var citationText by remember(medal.id, deed?.id) {
            mutableStateOf(
                deed?.deedSummary ?: medal.qualificationRule
            )
        }
        AlertDialog(
            onDismissRequest = { citationModalMedal = null },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = extra.brassBright)
            },
            title = {
                Text("Confer ${medal.name}", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AwardVisualThumbnail(medal = medal)
                        Text(
                            text = "${selectedOp.rank.code} ${selectedOp.callsign} • ${medal.prestigeCost} PRS",
                            style = MaterialTheme.typography.labelMedium,
                            color = extra.brassBright
                        )
                    }
                    OutlinedTextField(
                        value = citationText,
                        onValueChange = { citationText = it.take(160) },
                        label = { Text("Official Citation Text") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAwardMedal(
                            selectedOp.id,
                            medal.id,
                            citationText,
                            deed?.missionTitle ?: "HQ Ceremonial Citation"
                        )
                        citationModalMedal = null
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("confirm_award_medal"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = extra.brass,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text("Award & Pin")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { citationModalMedal = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUniformInspection) {
        UniformInspectionDialog(
            operator = selectedOp,
            equippedGear = opGear,
            awardedMedals = awardedMedalDefs,
            allCitations = uiState.citations,
            squads = uiState.squads,
            kitOverride = previewKit,
            onToggleUniformMode = { onToggleUniformMode(selectedOp.id) },
            onToggleDressDisplayMode = { onToggleDressDisplayMode(selectedOp.id) },
            onInspectAward = { medal -> inspectedAward = medal },
            onDismiss = { showUniformInspection = false }
        )
    }

    val awardToInspect = inspectedAward
    if (awardToInspect != null) {
        val holders = uiState.citations
            .filter { it.medalId == awardToInspect.id }
            .mapNotNull { cit ->
                val op = uiState.operators.find { it.id == cit.operatorId }
                if (op != null) op to cit else null
            }
        AwardInspectionDialog(
            medal = awardToInspect,
            heldByOperators = holders,
            onDismiss = { inspectedAward = null }
        )
    }

    if (showPromotionBoard && nextRank != null && OperatorRank.isBoardGate(selectedOp.rank, nextRank)) {
        val dialogEval = TacticalCalculator.evaluatePromotionBoard(
            operator = selectedOp,
            allOperators = uiState.operators,
            citations = uiState.citations,
            gearItems = uiState.gearItems,
            facilities = uiState.facilities
        )
        PromotionBoardDialog(
            operator = selectedOp,
            evaluation = dialogEval,
            resources = uiState.resources,
            equippedGear = opGear,
            awardedMedals = awardedMedalDefs,
            allCitations = uiState.citations,
            squads = uiState.squads,
            onApprove = { focus ->
                onPromoteOperator(selectedOp.id, focus)
                showPromotionBoard = false
            },
            onDismiss = { showPromotionBoard = false }
        )
    }
}

/** One award in the Dress & Awards catalog list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AwardCatalogCard(
    medal: MedalDefinition,
    selectedOp: Operator,
    alreadyHeld: Boolean,
    isRepeatable: Boolean,
    heldCount: Int,
    hasDeed: Boolean,
    eligibility: TacticalCalculator.EligibilityResult,
    canAfford: Boolean,
    perSoldierCap: Int?,
    onInspect: () -> Unit,
    onConfer: () -> Unit
) {
    val extra = LocalAegisColors.current
    TacticalPanelCard(
        borderColor = when {
            alreadyHeld -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            hasDeed && eligibility.eligible -> extra.brass
            else -> extra.borderSubtle
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onInspect)
                    .padding(4.dp)
            ) {
                AwardVisualThumbnail(medal = medal)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${medal.prestigeCost} PRS • Case",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.brassBright
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                // Tags wrap whole onto a new line instead of squeezing the last one into a column
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.testTag("award_tags_${medal.id}")
                ) {
                    Text(
                        text = medal.category.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        color = extra.signalsCyan
                    )
                    if (heldCount > 0) {
                        Text(
                            text = if (heldCount > 1) "• HELD ×$heldCount (OLC)" else "• HELD",
                            style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                            color = extra.brassBright
                        )
                    }
                    if (medal.minTierRequired != RankTier.SOLDIER) {
                        Text(
                            text = "• ${medal.minTierRequired.shortLabel}+",
                            style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                            color = extra.brassBright
                        )
                    }
                    if (perSoldierCap != null) {
                        Text(
                            text = "• $perSoldierCap MAX / SOLDIER",
                            style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                            color = if (alreadyHeld) extra.signalsCyan else extra.brassBright
                        )
                    }
                }
                Text(
                    text = medal.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = medal.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Req: ${medal.qualificationRule}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (alreadyHeld || eligibility.eligible) extra.brassBright else MaterialTheme.colorScheme.error
                )
                Text(
                    text = "FP+${medal.firepowerBonus} ST+${medal.stealthBonus} TC+${medal.techBonus} RS+${medal.resilienceBonus} • +${medal.yieldBonusPct}% Yield",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onConfer,
            enabled = (!alreadyHeld || isRepeatable) && eligibility.eligible && canAfford,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("confer_${medal.id}"),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (medal.tier == MedalTier.SUPREME) extra.brass else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                when {
                    alreadyHeld && !isRepeatable -> "Awarded on ${selectedOp.callsign}'s Uniform"
                    !eligibility.eligible -> "Locked: ${eligibility.missingRequirements.firstOrNull() ?: medal.qualificationRule}"
                    !canAfford -> "Need ${medal.prestigeCost} PRS"
                    alreadyHeld && isRepeatable -> "Award Again ×${heldCount + 1} (Oak Leaf Cluster • ${medal.prestigeCost} PRS)"
                    else -> "Award ${medal.category.label} to ${selectedOp.callsign}"
                },
                maxLines = 1
            )
        }
    }
}
