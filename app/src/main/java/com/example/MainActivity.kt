package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.catalog.GameCatalog
import com.example.data.local.AegisDatabase
import com.example.data.local.AegisRepository
import com.example.domain.model.DebriefSummary
import com.example.domain.model.OfflineReport
import com.example.domain.model.OperatorOutcomeType
import com.example.ui.components.AwardVisualThumbnail
import com.example.ui.components.CommandersGuideDialog
import com.example.ui.components.ResourceHudBar
import com.example.ui.components.UniformInspectionReportDialog
import com.example.ui.components.formatCompact
import com.example.ui.components.formatCountdown
import com.example.ui.screens.DressAwardsScreen
import com.example.ui.screens.HonorsScreen
import com.example.ui.screens.HqCommandScreen
import com.example.ui.screens.HqOpsScreen
import com.example.ui.screens.SquadArmoryScreen
import com.example.ui.theme.AegisOpsTheme
import com.example.ui.theme.LocalAegisColors
import com.example.ui.viewmodel.CommandViewModel

const val ROUTE_OPS = "ops"
const val ROUTE_HQ = "hq"
const val ROUTE_SQUAD = "squad"
const val ROUTE_DRESS = "dress"
const val ROUTE_HONORS = "honors"

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopLevelDestinations = listOf(
    NavDestination(
        route = ROUTE_OPS,
        labelRes = R.string.nav_ops,
        selectedIcon = Icons.Filled.Radar,
        unselectedIcon = Icons.Outlined.Radar,
        testTag = "nav_ops"
    ),
    NavDestination(
        route = ROUTE_HQ,
        labelRes = R.string.nav_hq,
        selectedIcon = Icons.Filled.AccountBalance,
        unselectedIcon = Icons.Outlined.AccountBalance,
        testTag = "nav_hq"
    ),
    NavDestination(
        route = ROUTE_SQUAD,
        labelRes = R.string.nav_squad,
        selectedIcon = Icons.Filled.Groups,
        unselectedIcon = Icons.Outlined.Groups,
        testTag = "nav_squad"
    ),
    NavDestination(
        route = ROUTE_DRESS,
        labelRes = R.string.nav_dress,
        selectedIcon = Icons.Filled.Checkroom,
        unselectedIcon = Icons.Outlined.Checkroom,
        testTag = "nav_dress"
    ),
    NavDestination(
        route = ROUTE_HONORS,
        labelRes = R.string.nav_honors,
        selectedIcon = Icons.Filled.MilitaryTech,
        unselectedIcon = Icons.Outlined.MilitaryTech,
        testTag = "nav_honors"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AegisOpsTheme {
                AegisCommandApp()
            }
        }
    }
}

@Composable
fun AegisCommandApp() {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) {
        AegisRepository(AegisDatabase.getInstance(context).aegisDao())
    }
    val viewModel: CommandViewModel = viewModel(
        factory = CommandViewModel.factory(repository)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val prefs = remember(context) {
        context.getSharedPreferences("aegis_ops_prefs", Context.MODE_PRIVATE)
    }
    var showGuideDialog by remember { mutableStateOf(false) }
    var isFirstTimeBrief by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && !prefs.getBoolean("has_seen_guide", false)) {
            isFirstTimeBrief = true
            showGuideDialog = true
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_OPS
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusBanner) {
        val msg = uiState.statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.clearBanner()
        }
    }

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val readyOpsCount = remember(uiState.deployments, uiState.nowMillis) {
        uiState.deployments.count { it.isComplete(uiState.nowMillis) }
    }
    val pendingDeedsCount = uiState.pendingDeeds.size
    val pendingPersonnelRequestsCount = remember(uiState.operators) {
        uiState.operators.count {
            it.unlocked && !it.status.isOutOfActiveService && (it.retirementRequested || it.transferRequested)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideLayout = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!isWideLayout) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        TopLevelDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val label = stringResource(id = dest.labelRes)
                            val badgeCount = when (dest.route) {
                                ROUTE_OPS -> readyOpsCount
                                ROUTE_SQUAD -> pendingPersonnelRequestsCount
                                ROUTE_DRESS -> pendingDeedsCount
                                else -> 0
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (badgeCount > 0) {
                                                Badge { Text(badgeCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = label
                                        )
                                    }
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideLayout) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TopLevelDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val label = stringResource(id = dest.labelRes)
                            val badgeCount = when (dest.route) {
                                ROUTE_OPS -> readyOpsCount
                                ROUTE_SQUAD -> pendingPersonnelRequestsCount
                                ROUTE_DRESS -> pendingDeedsCount
                                else -> 0
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (badgeCount > 0) {
                                                Badge { Text(badgeCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = label
                                        )
                                    }
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (uiState.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        ResourceHudBar(
                            resources = uiState.resources,
                            rates = uiState.ratesPerSec,
                            onPrestigeClick = viewModel::grantCheatPrestige,
                            modifier = Modifier.widthIn(max = 720.dp)
                        )

                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_OPS,
                            modifier = Modifier
                                .weight(1f)
                                .widthIn(max = 720.dp)
                                .fillMaxWidth()
                        ) {
                            composable(ROUTE_OPS) {
                                HqOpsScreen(
                                    uiState = uiState,
                                    onReadinessDrill = viewModel::conductReadinessDrill,
                                    onUpgradeFacility = viewModel::upgradeFacility,
                                    onRushFacility = viewModel::rushFacility,
                                    onStartDeployment = { m, ids -> viewModel.startDeployment(m, ids) },
                                    onStartDeploymentWithSquad = viewModel::startDeployment,
                                    onLaunchPrepared = viewModel::startDeployment,
                                    onAutoGear = viewModel::autoDistributeFieldGear,
                                    onClaimDeployment = viewModel::claimDeployment,
                                    onTreatWounded = viewModel::treatWoundedOperator,
                                    onNavigateToDress = { navigateTo(ROUTE_DRESS) },
                                    onNavigateToSquad = { navigateTo(ROUTE_SQUAD) },
                                    onSwitchOperatorsToKit = viewModel::switchOperatorsToKit,
                                    onRefreshRotatingMissions = viewModel::refreshRotatingMissions,
                                    onOpenCommandGuide = {
                                        isFirstTimeBrief = false
                                        showGuideDialog = true
                                    }
                                )
                            }
                            composable(ROUTE_HQ) {
                                HqCommandScreen(
                                    uiState = uiState,
                                    onAssignToHqStaff = viewModel::assignToHqStaff,
                                    onReassignHqStaffRole = viewModel::reassignHqStaffRole,
                                    onPromoteOperator = viewModel::promoteOperator,
                                    onStartDeployment = viewModel::startDeployment,
                                    onApproveTransferOut = viewModel::approveTransferOut,
                                    onApproveRetirement = viewModel::approveRetirement,
                                    onDenyRetirementRequest = viewModel::denyRetirementRequest,
                                    onUpgradeFacility = viewModel::upgradeFacility,
                                    onRushFacility = viewModel::rushFacility,
                                    onToggleUniformMode = viewModel::toggleOperatorUniformMode,
                                    onToggleDressDisplayMode = viewModel::toggleDressDisplayMode,
                                    onNavigateToSquad = { navigateTo(ROUTE_SQUAD) },
                                    onNavigateToDress = { navigateTo(ROUTE_DRESS) },
                                    onNavigateToOps = { navigateTo(ROUTE_OPS) }
                                )
                            }
                            composable(ROUTE_SQUAD) {
                                SquadArmoryScreen(
                                    uiState = uiState,
                                    onRecruitOperator = viewModel::recruitOperator,
                                    onToggleUniformMode = viewModel::toggleOperatorUniformMode,
                                    onCustomizeOperator = viewModel::customizeOperator,
                                    onEquipGear = viewModel::equipGear,
                                    onUnequipGear = viewModel::unequipGear,
                                    onAutoDistributeGear = viewModel::autoDistributeFieldGear,
                                    onPurchaseOrUpgradeGear = viewModel::purchaseOrUpgradeGear,
                                    onEnlistRecruit = viewModel::enlistRecruit,
                                    onAssignToHqStaff = viewModel::assignToHqStaff,
                                    onReassignHqStaffRole = viewModel::reassignHqStaffRole,
                                    onApproveTransferOut = viewModel::approveTransferOut,
                                    onDeclineTransferRequest = viewModel::declineTransferRequest,
                                    onApproveRetirement = viewModel::approveRetirement,
                                    onDenyRetirementRequest = viewModel::denyRetirementRequest,
                                    onSaveSquad = viewModel::saveSquadWithStandard,
                                    onDisbandSquad = viewModel::disbandSquad,
                                    onAwardMarksmanship = viewModel::awardMarksmanship,
                                    onConductSquadInspection = { squadId ->
                                        viewModel.conductUniformInspection(squadId = squadId)
                                    },
                                    onSwitchSquadKit = viewModel::switchSquadWardrobeKit,
                                    onSelectWardrobeKit = viewModel::selectWardrobeKit,
                                    onNavigateToHq = { navigateTo(ROUTE_HQ) }
                                )
                            }
                            composable(ROUTE_DRESS) {
                                DressAwardsScreen(
                                    uiState = uiState,
                                    onToggleUniformMode = viewModel::toggleOperatorUniformMode,
                                    onToggleDressDisplayMode = viewModel::toggleDressDisplayMode,
                                    onPromoteOperator = viewModel::promoteOperator,
                                    onCustomizeOperator = viewModel::customizeOperator,
                                    onAwardMedal = viewModel::awardMedal,
                                    onDismissDeed = viewModel::dismissPendingDeed,
                                    onConductInspection = { opId ->
                                        viewModel.conductUniformInspection(operatorId = opId)
                                    },
                                    onNavigateToSquad = { navigateTo(ROUTE_SQUAD) },
                                    onSelectWardrobeKit = viewModel::selectWardrobeKit,
                                    onToggleSmartWardrobe = viewModel::toggleSmartWardrobe,
                                    onConductKitInspection = { opId, kit ->
                                        viewModel.conductUniformInspection(operatorId = opId, inspectedKit = kit)
                                    }
                                )
                            }
                            composable(ROUTE_HONORS) {
                                HonorsScreen(uiState = uiState)
                            }
                        }
                    }
                }
            }
        }
    }

    // Rifle qualification results, one at a time, after any debrief is closed
    val rifleQueue by viewModel.rifleQualQueue.collectAsStateWithLifecycle()
    if (uiState.lastDebrief == null) {
        rifleQueue.firstOrNull()?.let { result ->
            com.example.ui.components.RifleQualResultDialog(
                result = result,
                remaining = rifleQueue.size,
                onAward = { viewModel.awardMarksmanship(result.operatorId) },
                onDismiss = viewModel::dismissRifleQualResult
            )
        }
    }

    // Newly earned awards, one card at a time, after the debrief and any rifle results are closed
    val awardQueue by viewModel.awardQueue.collectAsStateWithLifecycle()
    val awardQueueTotal by viewModel.awardQueueTotal.collectAsStateWithLifecycle()
    if (uiState.lastDebrief == null && rifleQueue.isEmpty()) {
        awardQueue.firstOrNull()?.let { deed ->
            val medal = com.example.data.catalog.GameCatalog.medals.find { it.id == deed.recommendedMedalId }
            val op = uiState.operators.find { it.id == deed.operatorId }
            if (medal == null || op == null) {
                LaunchedEffect(deed.id) { viewModel.skipAwardPrompt() }
            } else {
                com.example.ui.components.AwardPromptDialog(
                    deed = deed,
                    medal = medal,
                    soldierLabel = "${op.rank.code} ${op.callsign}",
                    position = (awardQueueTotal - awardQueue.size + 1).coerceAtLeast(1),
                    total = maxOf(awardQueueTotal, awardQueue.size),
                    canAfford = uiState.resources.prestige >= medal.prestigeCost,
                    repeatLabel = com.example.domain.TacticalCalculator.repeatAwardLabel(op, medal, uiState.citations),
                    onAward = viewModel::awardFromQueue,
                    onSkip = viewModel::skipAwardPrompt
                )
            }
        }
    }

    uiState.offlineReport?.let { report ->
        OfflineReportDialog(
            report = report,
            onDismiss = viewModel::dismissOfflineReport
        )
    }

    uiState.lastDebrief?.let { debrief ->
        DebriefSummaryDialog(
            debrief = debrief,
            onDismiss = viewModel::dismissDebrief,
            onGoToAwards = {
                viewModel.dismissDebrief()
                navigateTo(ROUTE_DRESS)
            }
        )
    }

    uiState.lastInspectionReport?.let { inspectionReport ->
        UniformInspectionReportDialog(
            report = inspectionReport,
            onDismiss = viewModel::dismissInspectionReport
        )
    }

    if (showGuideDialog) {
        CommandersGuideDialog(
            isFirstTimeBrief = isFirstTimeBrief,
            onDismiss = {
                prefs.edit().putBoolean("has_seen_guide", true).apply()
                showGuideDialog = false
            },
            onConfirmResetCampaign = {
                prefs.edit().putBoolean("has_seen_guide", true).apply()
                viewModel.resetCampaign()
            }
        )
    }
}

@Composable
private fun OfflineReportDialog(
    report: OfflineReport,
    onDismiss: () -> Unit
) {
    val extra = LocalAegisColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                tint = extra.signalsCyan
            )
        },
        title = {
            Text("Welcome Back, Commander", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Headquarters operations continued smoothly while away (${formatCountdown(report.elapsedSeconds)}).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "+${formatCompact(report.creditsEarned)} CR  •  +${formatCompact(report.suppliesEarned)} SUP  •  +${formatCompact(report.intelEarned)} INT",
                    style = MaterialTheme.typography.labelLarge,
                    color = extra.brassBright
                )
                if (report.finishedOpsCount > 0) {
                    Text(
                        text = "${report.finishedOpsCount} mission(s) completed and awaiting After-Action Debrief.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("dismiss_offline_report")
            ) {
                Text("Resume HQ")
            }
        }
    )
}

@Composable
private fun DebriefSummaryDialog(
    debrief: DebriefSummary,
    onDismiss: () -> Unit,
    onGoToAwards: () -> Unit
) {
    val extra = LocalAegisColors.current
    val hasRecommendedAward = debrief.operatorOutcomes.any { it.recommendedMedalId != null }
    val statusHeading = when {
        debrief.isTrainingMission && debrief.missionSucceeded -> "Course Evaluation Complete"
        debrief.isTrainingMission -> "Course Evaluation Failed"
        debrief.missionSucceeded -> "Mission Accomplished"
        else -> "Mission Failed • Returned to HQ"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = if (debrief.missionSucceeded) Icons.Filled.Verified else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (debrief.missionSucceeded) extra.brassBright else MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text("After-Action Debrief", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = statusHeading.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (debrief.missionSucceeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Text(
                    text = "${debrief.missionTitle} • ${debrief.efficiencyPct}% Efficiency",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "+${formatCompact(debrief.creditsEarned)} CR • +${formatCompact(debrief.suppliesEarned)} SUP • +${formatCompact(debrief.intelEarned)} INT • +${formatCompact(debrief.prestigeEarned)} PRS",
                    style = MaterialTheme.typography.labelMedium,
                    color = extra.brassBright
                )
                debrief.campaignProgressNote?.let { campNote ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = extra.brassContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, extra.brass)
                    ) {
                        Text(
                            text = campNote,
                            style = MaterialTheme.typography.labelMedium,
                            color = extra.brassBright,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                debrief.squadCohesionNote?.let { cohesionNote ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = extra.signalsContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, extra.signalsCyan.copy(alpha = 0.65f))
                    ) {
                        Text(
                            text = "★ $cohesionNote",
                            style = MaterialTheme.typography.labelMedium,
                            color = extra.signalsCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                HorizontalDivider(color = extra.borderSubtle)

                debrief.operatorOutcomes.forEach { opOut ->
                    val recMedal = opOut.recommendedMedalId?.let { id ->
                        GameCatalog.medals.find { it.id == id }
                    }
                    val statusColor = when (opOut.outcome) {
                        OperatorOutcomeType.SAFE, OperatorOutcomeType.TRAINING_PASSED -> MaterialTheme.colorScheme.primary
                        OperatorOutcomeType.WOUNDED -> extra.amberTelemetry
                        OperatorOutcomeType.FALLEN, OperatorOutcomeType.TRAINING_FAILED -> MaterialTheme.colorScheme.error
                        OperatorOutcomeType.STAFF_SUPPORT -> extra.signalsCyan
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, extra.borderSubtle)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${opOut.rankCode} ${opOut.callsign}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${opOut.outcome.label} (+${opOut.xpEarned} XP)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor
                                )
                            }
                            opOut.promotedToRankCode?.let { newRankCode ->
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = extra.brass.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, extra.brass.copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        text = "RANK UP \u2192 $newRankCode",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extra.brassBright,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            opOut.readyForCategoryRankCode?.let { gateRankCode ->
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = extra.signalsContainer,
                                    border = BorderStroke(1.dp, extra.signalsCyan.copy(alpha = 0.65f))
                                ) {
                                    Text(
                                        text = "PROMOTION BOARD READY \u2192 $gateRankCode (Approve in Uniforms)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extra.signalsCyan,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = opOut.deedText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (recMedal != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AwardVisualThumbnail(medal = recMedal)
                                    Text(
                                        text = "Recommended (${recMedal.category.label}): ${recMedal.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extra.brassBright
                                    )
                                }
                            } else {
                                Text(
                                    text = "Service count & promotion XP logged — no award citation issued.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.textMuted
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (hasRecommendedAward) {
                Button(
                    onClick = onGoToAwards,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("debrief_present_awards"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = extra.brass,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text("Present Awards")
                }
            } else {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("dismiss_debrief_dialog")
                ) {
                    Text("Acknowledge")
                }
            }
        },
        dismissButton = {
            if (hasRecommendedAward) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("dismiss_debrief_dialog")
                ) {
                    Text("Close")
                }
            }
        }
    )
}
