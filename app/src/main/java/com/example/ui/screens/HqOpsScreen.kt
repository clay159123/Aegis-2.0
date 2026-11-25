package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.catalog.GameCatalog
import com.example.domain.SquadCohesionRules
import com.example.domain.TacticalCalculator
import com.example.domain.GameCalendar
import com.example.domain.PowRules
import com.example.domain.model.*
import com.example.ui.components.*
import com.example.ui.theme.LocalAegisColors
import com.example.ui.viewmodel.CommandUiState
import kotlin.math.roundToInt

private enum class OpsBoardTab(val label: String) {
    ALL("All"),
    CAMPAIGNS("Campaigns"),
    COMBAT("Combat"),
    SCHOOLS("Schools"),
    STAFF("Staff")
}

private data class OpsMissionGroup(
    val id: String,
    val title: String,
    val scope: MissionScope,
    val missions: List<MissionTemplate>
)

/** Item 155: one group per school category, in pipeline order; anything uncategorized lands in "Other Schools". */
private fun schoolGroups(): List<OpsMissionGroup> {
    val byId = GameCatalog.missions.filter { it.scope == MissionScope.TRAINING }.associateBy { it.id }
    val placed = mutableSetOf<String>()
    val groups = GameCatalog.schoolCategories.mapNotNull { cat ->
        val missions = cat.missionIds.mapNotNull { byId[it] }
        placed += missions.map { it.id }
        if (missions.isEmpty()) null
        else OpsMissionGroup("school_${cat.id}", cat.title, MissionScope.TRAINING, missions)
    }
    val rest = byId.values.filter { it.id !in placed }
    return if (rest.isEmpty()) groups else groups + OpsMissionGroup("school_other", "Other Schools", MissionScope.TRAINING, rest)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HqOpsScreen(
    uiState: CommandUiState,
    onReadinessDrill: () -> Unit,
    onUpgradeFacility: (FacilityType) -> Unit,
    onRushFacility: (FacilityType) -> Unit = {},
    onStartDeployment: (MissionTemplate, List<String>) -> Unit,
    onStartDeploymentWithSquad: ((MissionTemplate, List<String>, String?) -> Unit)? = null,
    onStartFlightHours: ((MissionTemplate, List<String>, Int) -> Unit)? = null,
    /** Item 34: field missions launch from the prep screen with an insertion method and optional pilot. */
    onLaunchPrepared: ((MissionTemplate, List<String>, String?, InsertionMethod, String?, String?) -> Unit)? = null,
    onAutoGear: () -> Unit = {},
    onClaimDeployment: (String) -> Unit,
    onCancelDeployment: (String) -> Unit = {},
    /** Item 63: pick the POW a rescue or release talks are for, before staging. */
    onSelectPowTarget: (String) -> Unit = {},
    /** Item 62: the region a Language school class studies. */
    onSelectLanguageRegion: (TheaterRegion) -> Unit = {},
    onTreatWounded: (String) -> Unit,
    onNavigateToDress: () -> Unit,
    onNavigateToSquad: () -> Unit,
    /** Session 39: open the Uniform tab on a soldier up for promotion. */
    onReviewPromotion: (String) -> Unit = { onNavigateToDress() },
    /** Session 41: testing build only, tapping the calendar advances it a month. */
    onCalendarTap: (() -> Unit)? = null,
    /** Session 42: Rush ops, schools and leave for Prestige. */
    onRushDeployment: ((String) -> Unit)? = null,
    onRushLeave: ((String) -> Unit)? = null,
    onSwitchOperatorsToKit: (List<String>, WardrobeKit) -> Unit = { _, _ -> },
    onSetTeamCamo: (List<String>, CamoPattern?) -> Unit = { _, _ -> },
    onRefreshRotatingMissions: () -> Unit = {},
    onOpenCommandGuide: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val extra = LocalAegisColors.current
    var showFacilities by remember { mutableStateOf(false) }
    var showActiveOps by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(OpsBoardTab.ALL) }
    // Session 31: Ready only starts on, and checks full team rules
    var readyOnly by remember { mutableStateOf(true) }
    // Playtest fix: every collapsible section starts collapsed
    var collapsedGroupIds by remember {
        mutableStateOf(
            setOf("campaigns", "combat", "special_forces", "staff", "school_other") +
                GameCatalog.schoolCategories.map { "school_${it.id}" }
        )
    }
    var expandedMissionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandedCampaignIds by remember { mutableStateOf(setOf("camp_veridian")) }
    var flightHours by remember { mutableStateOf(TacticalCalculator.FLIGHT_HOURS_PER_SESSION) }
    var languageRegion by remember { mutableStateOf<TheaterRegion?>(null) }
    val theaterCampaignIds = remember { GameCatalog.campaigns.filter { !it.isSpecialForces }.map { it.id }.toSet() }
    val theaterTotal = theaterCampaignIds.size
    val theaterCleared = uiState.completedCampaignIds.count { it in theaterCampaignIds }
    var stagingMission by remember { mutableStateOf<MissionTemplate?>(null) }
    var prepMission by remember { mutableStateOf<MissionTemplate?>(null) }
    var prepTeamIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var prepSquadId by remember { mutableStateOf<String?>(null) }
    var selectedOperatorIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedSquadId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.completedCampaignIds) {
        val nextActive = GameCatalog.campaigns.firstOrNull {
            TacticalCalculator.isCampaignUnlocked(it, uiState.completedCampaignIds) &&
                it.id !in uiState.completedCampaignIds
        }
        if (nextActive != null && nextActive.id !in expandedCampaignIds) {
            expandedCampaignIds = expandedCampaignIds + nextActive.id
        }
    }

    if (stagingMission != null) {
        BackHandler {
            stagingMission = null
            selectedOperatorIds = emptyList()
            selectedSquadId = null
        }
    }

    val deployedOpIds = remember(uiState.deployments) {
        uiState.deployments.flatMap { it.operatorIds }.toSet()
    }
    val woundedOps = remember(uiState.operators) {
        uiState.operators.filter { it.unlocked && it.status == OperatorStatus.RECOVERING }
    }
    val durationMult = TacticalCalculator.durationMultiplier(uiState.facilities, uiState.hqStaffSummary)
    val supplyMult = TacticalCalculator.supplyCostMultiplier(
        facilities = uiState.facilities,
        hqStaffSummary = uiState.hqStaffSummary,
        completedCampaignIds = uiState.completedCampaignIds
    )

    val qualifiedByMissionId = remember(
        uiState.operators,
        deployedOpIds,
        uiState.gearItems,
        uiState.citations,
        uiState.facilities
    ) {
        GameCatalog.missions.associate { mission ->
            val count = uiState.operators.count { op ->
                TacticalCalculator.canOperatorDeployOnMission(
                    operator = op,
                    mission = mission,
                    deployedOpIds = deployedOpIds,
                    gearItems = uiState.gearItems,
                    citations = uiState.citations,
                    facilities = uiState.facilities
                ).eligible
            }
            mission.id to count
        }
    }

    // Session 31: a mission is ready only when the eligible soldiers can form a valid team
    val readyByMissionId = remember(uiState.operators, deployedOpIds, uiState.gearItems, uiState.citations, uiState.facilities, uiState.squads) {
        GameCatalog.missions.associate { mission ->
            val eligible = uiState.operators.filter { op ->
                TacticalCalculator.canOperatorDeployOnMission(
                    operator = op, mission = mission, deployedOpIds = deployedOpIds, gearItems = uiState.gearItems,
                    citations = uiState.citations, facilities = uiState.facilities
                ).eligible
            }
            mission.id to TacticalCalculator.canFieldTeam(mission, eligible, uiState.squads)
        }
    }

    val activeRotatingSet = remember(uiState.rotatingMissionIds) {
        val ids = uiState.rotatingMissionIds
        if (ids.isNotEmpty()) {
            ids.toSet()
        } else {
            GameCatalog.rotatingStandalonePool.take(TacticalCalculator.ROTATING_BOARD_SIZE).map { it.id }.toSet()
        }
    }

    // Item 58: SF missions and the SF campaign stay hidden until the command forms an SF squad
    val sfUnlocked = TacticalCalculator.sfMissionsUnlocked(uiState.squads)
    val orderedGroups = remember(activeRotatingSet, sfUnlocked) {
        listOfNotNull(
            OpsMissionGroup(
                id = "combat",
                title = "Field Combat Ops",
                scope = MissionScope.FIELD_COMBAT,
                missions = GameCatalog.missions.filter {
                    it.scope == MissionScope.FIELD_COMBAT &&
                        it.campaignId == null &&
                        (it.isEvergreenStandalone || it.id in activeRotatingSet)
                }
            ),
            if (sfUnlocked) OpsMissionGroup(
                id = "special_forces",
                title = "Special Forces Ops",
                scope = MissionScope.FIELD_COMBAT,
                missions = GameCatalog.missions.filter { it.requiresSfTeam && it.campaignId == null }
            ) else null,
        ) + schoolGroups() + listOf(
            OpsMissionGroup(
                id = "staff",
                title = "Diplomatic & Staff Ops",
                scope = MissionScope.STAFF_DIPLOMATIC,
                missions = GameCatalog.missions.filter {
                    it.scope == MissionScope.STAFF_DIPLOMATIC &&
                        it.campaignId == null &&
                        (it.isEvergreenStandalone || it.id in activeRotatingSet)
                }
            )
        )
    }

    val visibleCampaigns = remember(readyOnly, readyByMissionId, uiState.completedCampaignStageIds, uiState.completedCampaignIds, sfUnlocked) {
        val shownCampaigns = GameCatalog.campaigns.filter { !it.isSpecialForces || sfUnlocked }
        if (!readyOnly) {
            shownCampaigns
        } else {
            shownCampaigns.filter { camp ->
                camp.stageMissionIds.any { stageId ->
                    val mission = GameCatalog.campaignMissionsById[stageId] ?: return@any false
                    TacticalCalculator.isCampaignStageUnlocked(
                        mission = mission,
                        completedStageIds = uiState.completedCampaignStageIds,
                        completedCampaignIds = uiState.completedCampaignIds
                    ) && readyByMissionId[stageId] == true
                }
            }
        }
    }

    val visibleGroups = remember(selectedTab, readyOnly, readyByMissionId, orderedGroups) {
        val scoped = when (selectedTab) {
            OpsBoardTab.ALL -> orderedGroups
            OpsBoardTab.CAMPAIGNS -> emptyList()
            OpsBoardTab.COMBAT -> orderedGroups.filter { it.scope == MissionScope.FIELD_COMBAT }
            OpsBoardTab.SCHOOLS -> orderedGroups.filter { it.scope == MissionScope.TRAINING }
            OpsBoardTab.STAFF -> orderedGroups.filter { it.scope == MissionScope.STAFF_DIPLOMATIC }
        }
        if (!readyOnly) {
            scoped
        } else {
            scoped.mapNotNull { group ->
                val readyMissions = group.missions.filter { m -> readyByMissionId[m.id] == true }
                if (readyMissions.isEmpty()) null else group.copy(missions = readyMissions)
            }
        }
    }

    val openMissionStaging: (MissionTemplate) -> Unit = { mission ->
        stagingMission = mission
        val readyOps = uiState.operators.filter {
            TacticalCalculator.canOperatorDeployOnMission(
                operator = it,
                mission = mission,
                deployedOpIds = deployedOpIds,
                gearItems = uiState.gearItems,
                citations = uiState.citations,
                facilities = uiState.facilities
            ).eligible
        }
        val readyIds = readyOps.map { it.id }.toSet()
        // Item 58: SF missions are SF-squad-first; other SF-qualified soldiers fill out a short team
        // Item 119: the SF squad with the most soldiers ready goes first
        val sfSquad = uiState.squads.filter { it.isSpecialForces }
            .maxByOrNull { sq -> SquadCohesionRules.orderedMembersForDeployment(sq, readyIds, mission.maxOperators).size }
        val matchingSquad = if (mission.requiresSfTeam) null else uiState.squads.firstOrNull { sq ->
            SquadCohesionRules.orderedMembersForDeployment(
                squad = sq,
                eligibleOperatorIds = readyIds,
                maxCount = mission.maxOperators
            ).size >= mission.minOperators
        }
        if (mission.requiresSfTeam && sfSquad != null) {
            val core = SquadCohesionRules.orderedMembersForDeployment(sfSquad, readyIds, mission.maxOperators)
            // Clandestine teams fill only from other SF squads
            val sfIds = com.example.domain.CompoundRules.sfSquadMemberIds(uiState.squads)
            val fill = readyOps.map { it.id }.filter { it !in core && (!mission.isClandestine || it in sfIds) }
                .take((mission.minOperators - core.size).coerceAtLeast(0))
            selectedSquadId = sfSquad.id
            selectedOperatorIds = core + fill
        } else if (matchingSquad != null && !mission.risk.isTraining) {
            selectedSquadId = matchingSquad.id
            selectedOperatorIds = SquadCohesionRules.orderedMembersForDeployment(
                squad = matchingSquad,
                eligibleOperatorIds = readyIds,
                maxCount = mission.maxOperators
            )
        } else {
            selectedSquadId = null
            selectedOperatorIds = readyOps.take(mission.minOperators).map { it.id }
        }
    }

    val returnedCount = uiState.deployments.count { it.isComplete(uiState.nowMillis) }

    val promotable = remember(uiState.operators, uiState.citations, uiState.gearItems, uiState.facilities) {
        TacticalCalculator.promotableSoldiers(uiState.operators, uiState.citations, uiState.gearItems, uiState.facilities)
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("ops_board_list"),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Compact HQ Command Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, extra.borderSubtle),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_fob_banner),
                        contentDescription = stringResource(id = R.string.cd_fob_banner),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Item 104: the status chip shrinks and wraps as stats are added, so it can never
                            // push the Settings and Guide buttons off the screen
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(end = 6.dp)
                            ) {
                                Text(
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    text = buildString {
                                        append("AEGIS HQ • ${uiState.occupiedDeploymentSlots}/${uiState.maxDeploymentSlots} OPS")
                                        if (uiState.hqStaffCount > 0) {
                                            append(" • STAFF ${uiState.hqStaffCount}/${uiState.maxHqStaffSlots}")
                                        }
                                        if (uiState.veteranLegacyBps > 0) {
                                            append(" • LEGACY ${uiState.veteranLegacyPctText}")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showFacilities = !showFacilities },
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("toggle_hq_facilities"),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AccountBalance,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (showFacilities) "Hide HQ" else "HQ Upgrades",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    border = BorderStroke(1.dp, extra.borderSubtle)
                                ) {
                                    IconButton(
                                        onClick = onOpenSettings,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("open_settings")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Settings,
                                            contentDescription = "Settings",
                                            tint = extra.brassBright
                                        )
                                    }
                                }
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    border = BorderStroke(1.dp, extra.borderSubtle)
                                ) {
                                    IconButton(
                                        onClick = onOpenCommandGuide,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("open_command_guide")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                            contentDescription = "Command Guide and Reset Save",
                                            tint = extra.brassBright
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "HEADQUARTERS OPS",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                // Session 41 (item 73): the unit calendar; it runs only while something is in progress
                                val busy = GameCalendar.activeMillisBetween(
                                    GameCalendar.activeIntervals(uiState.operators, uiState.deployments, uiState.squads),
                                    uiState.nowMillis - 1, uiState.nowMillis
                                ) > 0
                                Text(
                                    text = GameCalendar.label(uiState.calendarDays).uppercase() + if (busy) "" else " • PAUSED",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = extra.brassBright,
                                    modifier = Modifier
                                        .then(if (onCalendarTap != null) Modifier.clickable { onCalendarTap() } else Modifier)
                                        .padding(vertical = 2.dp)
                                        .testTag("ops_calendar")
                                )

                            }
                            Button(
                                onClick = onReadinessDrill,
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("readiness_drill_button"),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Drill")
                            }
                        }
                    }
                }
            }
        }

        // Collapsible HQ Facilities Panel
        item {
            AnimatedVisibility(visible = showFacilities) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BaseMapCard(
                        facilities = uiState.facilities,
                        resources = uiState.resources,
                        nowMillis = uiState.nowMillis,
                        onUpgrade = onUpgradeFacility,
                        onRush = onRushFacility,
                        fallenCount = uiState.operators.count { it.status == OperatorStatus.FALLEN },
                        completedCampaignIds = uiState.completedCampaignIds,
                        operators = uiState.operators
                    )

                    TacticalPanelCard(
                        containerColor = extra.signalsContainer.copy(alpha = 0.25f),
                        borderColor = extra.signalsCyan.copy(alpha = 0.5f)
                    ) {
                        val nextPreviewCooldownSec = if (uiState.lastRetirementAtMillis <= 0L) 0L else {
                            val elapsed = (uiState.nowMillis - uiState.lastRetirementAtMillis).coerceAtLeast(0L)
                            ((TacticalCalculator.RETIREMENT_FULL_COOLDOWN_MILLIS - elapsed).coerceAtLeast(0L) + 999L) / 1000L
                        }
                        Column {
                            Text(
                                text = "VETERAN LEGACY • BASE OPS ENDOWMENT",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.signalsCyan
                            )
                            Text(
                                text = "${uiState.veteranLegacyPctText} CR / SUP / INT (${uiState.retiredVeteransCount} Retired • Cap ${TacticalCalculator.formatBpsPercent(TacticalCalculator.MAX_VETERAN_LEGACY_BPS)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = extra.brassBright
                            )
                            if (nextPreviewCooldownSec > 0L && uiState.veteranLegacyBps < TacticalCalculator.MAX_VETERAN_LEGACY_BPS) {
                                Text(
                                    text = "Retirement spacing cooldown: ${formatCountdown(nextPreviewCooldownSec)} to 100%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.amberTelemetry
                                )
                            }
                        }
                    }
                }
            }
        }

        // Pending Medal Recommendations Alert
        if (uiState.pendingDeeds.isNotEmpty()) {
            item {
                TacticalPanelCard(
                    containerColor = extra.brassContainer.copy(alpha = 0.5f),
                    borderColor = extra.brass
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${uiState.pendingDeeds.size} AWARD RECOMMENDATION(S)",
                                style = MaterialTheme.typography.labelLarge,
                                color = extra.brassBright
                            )
                            Text(
                                text = "Operators earned mission deeds awaiting medal presentation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = onNavigateToDress,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("go_to_pending_awards"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = extra.brass,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Text("Present")
                        }
                    }
                }
            }
        }

        // Session 39 (item 181): soldiers ready to pin a rank or go before a board
        if (promotable.isNotEmpty()) {
            item(key = "ops_promotions_alert") {
                TacticalPanelCard(
                    containerColor = extra.brassContainer.copy(alpha = 0.35f),
                    borderColor = extra.brassBright,
                    modifier = Modifier.testTag("ops_promotions_alert")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${promotable.size} UP FOR PROMOTION",
                                style = MaterialTheme.typography.labelLarge,
                                color = extra.brassBright,
                                modifier = Modifier.testTag("ops_promotions_count")
                            )
                            Text(
                                text = promotable.take(4).joinToString(", ") { "${it.rank.code} ${it.callsign}" } +
                                    if (promotable.size > 4) " +${promotable.size - 4} more" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { onReviewPromotion(promotable.first().id) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("ops_review_promotions"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = extra.brass,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) { Text("Review") }
                    }
                }
            }
        }

        // Session 39 (item 186): who is on leave and when they're back
        val onLeave = uiState.operators.filter { it.unlocked && it.status == OperatorStatus.ON_LEAVE }
            .sortedBy { it.recoveryUntilMillis }
        if (onLeave.isNotEmpty()) {
            item(key = "ops_on_leave") {
                TacticalPanelCard(borderColor = extra.amberTelemetry.copy(alpha = 0.6f), modifier = Modifier.testTag("ops_on_leave")) {
                    Text(
                        text = "ON LEAVE (${onLeave.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = extra.amberTelemetry
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    onLeave.forEach { op ->
                        val remSec = ((op.recoveryUntilMillis - uiState.nowMillis + 999L) / 1000L).coerceAtLeast(0L)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag("ops_leave_${op.id}"),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${op.rank.code} ${op.callsign}" + (op.hqRole?.let { " • ${it.shortCode}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "back in ${formatCountdown(remSec)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.amberTelemetry
                                )
                                if (onRushLeave != null) {
                                    TextButton(
                                        onClick = { onRushLeave(op.id) },
                                        modifier = Modifier.testTag("rush_leave_${op.id}")
                                    ) {
                                        Text("Recall ${TacticalCalculator.rushPrestigeCostFor(op.recoveryUntilMillis - uiState.nowMillis)} PRS", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Medbay Recovery Bay
        if (woundedOps.isNotEmpty()) {
            item {
                TacticalPanelCard(borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.65f)) {
                    Text(
                        text = "HQ MEDEVAC RECOVERY BAY",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    woundedOps.forEach { op ->
                        val remSec = ((op.recoveryUntilMillis - uiState.nowMillis) / 1000L).coerceAtLeast(0L)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${op.rank.code} ${op.callsign}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Recovering • ${formatCountdown(remSec)} remaining",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.amberTelemetry
                                )
                            }
                            OutlinedButton(
                                onClick = { onTreatWounded(op.id) },
                                enabled = uiState.resources.supplies >= 20.0,
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("Treat (20 SUP)")
                            }
                        }
                    }
                }
            }
        }

        // Item 63: soldiers held by the enemy, with the ways to bring them home
        val pows = uiState.operators.filter { it.status == OperatorStatus.CAPTURED }
        if (pows.isNotEmpty()) {
            item {
                PowListPanel(
                    pows = pows,
                    deployments = uiState.deployments,
                    prestige = uiState.resources.prestige,
                    nowMillis = uiState.nowMillis,
                    onRescue = { id ->
                        onSelectPowTarget(id)
                        GameCatalog.missions.find { it.id == PowRules.RESCUE_MISSION_ID }?.let(openMissionStaging)
                    },
                    onNegotiate = { id ->
                        onSelectPowTarget(id)
                        GameCatalog.missions.find { it.id == PowRules.NEGOTIATION_MISSION_ID }?.let(openMissionStaging)
                    }
                )
            }
        }

        // Collapsible Active Deployments
        if (uiState.deployments.isNotEmpty()) {
            item {
                OpsSectionBanner(
                    title = "Active Missions (${uiState.occupiedDeploymentSlots}/${uiState.maxDeploymentSlots})",
                    badgeText = if (returnedCount > 0) "$returnedCount RETURNED" else "LIVE TIMERS",
                    badgeAccent = if (returnedCount > 0) extra.brassBright else extra.signalsCyan,
                    expanded = showActiveOps,
                    onClick = { showActiveOps = !showActiveOps },
                    testTag = "ops_section_active"
                )
            }
            if (showActiveOps) {
                items(uiState.deployments, key = { it.id }) { deployment ->
                    ActiveHqDeploymentCard(
                        deployment = deployment,
                        operators = uiState.operators,
                        squads = uiState.squads,
                        nowMillis = uiState.nowMillis,
                        onClaim = { onClaimDeployment(deployment.id) },
                        onCancel = { onCancelDeployment(deployment.id) },
                        onRush = onRushDeployment?.let { r -> { r(deployment.id) } },
                        rushCost = TacticalCalculator.rushPrestigeCostFor(deployment.startedAtMillis + deployment.durationMillis - uiState.nowMillis)
                    )
                }
            }
        }

        // Mission Board Category Tabs & Quick Filter Controls
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    title = "Campaigns, Rotating Ops & Schools",
                    subtitle = "Advance Campaigns I to X in order or take rotating single missions"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OpsBoardTab.entries.forEach { tab ->
                        val tabCount = when (tab) {
                            OpsBoardTab.ALL -> orderedGroups.sumOf { it.missions.size } + GameCatalog.campaigns.size
                            OpsBoardTab.CAMPAIGNS -> GameCatalog.campaigns.size
                            OpsBoardTab.COMBAT -> orderedGroups.first { it.scope == MissionScope.FIELD_COMBAT }.missions.size
                            OpsBoardTab.SCHOOLS -> orderedGroups.filter { it.scope == MissionScope.TRAINING }.sumOf { it.missions.size }
                            OpsBoardTab.STAFF -> orderedGroups.first { it.scope == MissionScope.STAFF_DIPLOMATIC }.missions.size
                        }
                        val tabIcon = when (tab) {
                            OpsBoardTab.ALL -> Icons.Filled.GridView
                            OpsBoardTab.CAMPAIGNS -> Icons.Filled.Flag
                            OpsBoardTab.COMBAT -> Icons.Filled.GpsFixed
                            OpsBoardTab.SCHOOLS -> Icons.Filled.School
                            OpsBoardTab.STAFF -> Icons.Filled.Public
                        }
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            label = { Text("${tab.label} ($tabCount)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = tabIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("ops_tab_${tab.name.lowercase()}")
                        )
                    }

                    FilterChip(
                        selected = readyOnly,
                        onClick = { readyOnly = !readyOnly },
                        label = { Text("Ready Only") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("ops_filter_ready")
                    )
                }
            }
        }

        val showCampaignsSection = selectedTab == OpsBoardTab.ALL || selectedTab == OpsBoardTab.CAMPAIGNS
        val showRotationBanner = selectedTab == OpsBoardTab.ALL || selectedTab == OpsBoardTab.COMBAT || selectedTab == OpsBoardTab.STAFF

        if (showCampaignsSection) {
            val isCampaignsExpanded = selectedTab == OpsBoardTab.CAMPAIGNS || "campaigns" !in collapsedGroupIds
            if (selectedTab == OpsBoardTab.ALL) {
                item(key = "header_campaigns") {
                    OpsSectionBanner(
                        title = "Theater Campaigns (${theaterCleared}/${theaterTotal})",
                        badgeText = if (uiState.allCampaignsCleared) "ALL UNLOCKED" else "SEQUENTIAL",
                        badgeAccent = if (uiState.allCampaignsCleared) extra.brassBright else extra.signalsCyan,
                        expanded = isCampaignsExpanded,
                        onClick = {
                            collapsedGroupIds = if ("campaigns" in collapsedGroupIds) {
                                collapsedGroupIds - "campaigns"
                            } else {
                                collapsedGroupIds + "campaigns"
                            }
                        },
                        testTag = "ops_section_campaigns"
                    )
                }
            }

            if (isCampaignsExpanded) {
                item(key = "campaigns_summary_banner") {
                    TacticalPanelCard(
                        containerColor = if (uiState.allCampaignsCleared) {
                            extra.brassContainer.copy(alpha = 0.38f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        borderColor = if (uiState.allCampaignsCleared) extra.brass else extra.borderSubtle
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (uiState.allCampaignsCleared) {
                                    "ALL 10 CAMPAIGNS CLEARED: FULL FREE REPLAY ACTIVE"
                                } else {
                                    "SEQUENTIAL THEATER CAMPAIGNS ($theaterCleared/$theaterTotal)"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (uiState.allCampaignsCleared) extra.brassBright else extra.signalsCyan
                            )
                            Text(
                                text = if (uiState.allCampaignsCleared) {
                                    "Every stage across Campaigns I to X is open for replay, and all 10 Theater Perks are active."
                                } else {
                                    "Clear Campaigns I to X in order, each harder than the last, to earn permanent Theater Perks and open every stage for replay."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(visibleCampaigns, key = { it.id }) { campaign ->
                    val stageMissions = campaign.stageMissionIds.mapNotNull { id ->
                        GameCatalog.campaignMissionsById[id]
                    }
                    TheaterCampaignCard(
                        campaign = campaign,
                        stageMissions = stageMissions,
                        completedStageIds = uiState.completedCampaignStageIds,
                        completedCampaignIds = uiState.completedCampaignIds,
                        allCampaignsCleared = uiState.allCampaignsCleared,
                        expanded = campaign.id in expandedCampaignIds,
                        onToggleExpand = {
                            expandedCampaignIds = if (campaign.id in expandedCampaignIds) {
                                expandedCampaignIds - campaign.id
                            } else {
                                expandedCampaignIds + campaign.id
                            }
                        },
                        qualifiedByMissionId = qualifiedByMissionId,
                        durationMult = durationMult,
                        supplyMult = supplyMult,
                        currentSupplies = uiState.resources.supplies,
                        slotsFull = uiState.occupiedDeploymentSlots >= uiState.maxDeploymentSlots,
                        expandedMissionIds = expandedMissionIds,
                        onToggleMissionDetails = { mId ->
                            expandedMissionIds = if (mId in expandedMissionIds) {
                                expandedMissionIds - mId
                            } else {
                                expandedMissionIds + mId
                            }
                        },
                        onAssembleMission = openMissionStaging
                    )
                }
            }
        }

        if (showRotationBanner) {
            item(key = "rotating_contracts_banner") {
                RotatingContractsBanner(
                    remainingSec = uiState.rotationRemainingSec,
                    currentIntel = uiState.resources.intel,
                    activeRotatingCount = activeRotatingSet.size,
                    onRefresh = onRefreshRotatingMissions
                )
            }
        }

        if (visibleGroups.isEmpty() && (!showCampaignsSection || visibleCampaigns.isEmpty())) {
            item {
                TacticalPanelCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No ops currently have qualified personnel available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                readyOnly = false
                                selectedTab = OpsBoardTab.ALL
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Show All Ops")
                        }
                    }
                }
            }
        } else {
            visibleGroups.forEach { group ->
                // Item 155: school categories get headers on the Schools tab too
                val showHeader = selectedTab == OpsBoardTab.ALL || selectedTab == OpsBoardTab.SCHOOLS
                val isGroupExpanded = !showHeader || group.id !in collapsedGroupIds
                val readyInGroup = group.missions.count { m ->
                    (qualifiedByMissionId[m.id] ?: 0) >= m.minOperators
                }

                if (showHeader) {
                    item(key = "header_${group.id}") {
                        OpsSectionBanner(
                            title = "${group.title} (${group.missions.size})",
                            badgeText = "$readyInGroup READY",
                            badgeAccent = if (readyInGroup > 0) MaterialTheme.colorScheme.primary else extra.amberTelemetry,
                            expanded = isGroupExpanded,
                            onClick = {
                                collapsedGroupIds = if (group.id in collapsedGroupIds) {
                                    collapsedGroupIds - group.id
                                } else {
                                    collapsedGroupIds + group.id
                                }
                            },
                            testTag = "ops_section_${group.id}"
                        )
                    }
                }

                if (isGroupExpanded) {
                    items(group.missions, key = { it.id }) { mission ->
                        val adjustedSec = (mission.durationSeconds * durationMult).roundToInt()
                        val adjustedSup = (mission.supplyCost * supplyMult).roundToInt()
                        val slotsFull = mission.scope != MissionScope.TRAINING &&
                            uiState.occupiedDeploymentSlots >= uiState.maxDeploymentSlots
                        val qualifiedCount = qualifiedByMissionId[mission.id] ?: 0
                        val isExpanded = mission.id in expandedMissionIds

                        CompactHqMissionCard(
                            mission = mission,
                            adjustedDurationSec = adjustedSec,
                            adjustedSupplyCost = adjustedSup,
                            canAffordSupply = uiState.resources.supplies >= adjustedSup,
                            slotsFull = slotsFull,
                            qualifiedCount = qualifiedCount,
                            expanded = isExpanded,
                            onToggleExpand = {
                                expandedMissionIds = if (isExpanded) {
                                    expandedMissionIds - mission.id
                                } else {
                                    expandedMissionIds + mission.id
                                }
                            },
                            onAssemble = { openMissionStaging(mission) }
                        )
                    }
                }
            }
        }
    }

    val missionForPrep = prepMission
    val launchPrepared = onLaunchPrepared
    if (missionForPrep != null && launchPrepared != null) {
        com.example.ui.components.MissionPrepDialog(
            mission = missionForPrep,
            uiState = uiState,
            initialTeamIds = prepTeamIds,
            deployedOpIds = deployedOpIds,
            onAutoGear = onAutoGear,
            onFieldKit = { ids -> onSwitchOperatorsToKit(ids, WardrobeKit.FIELD) },
            onSetTeamCamo = onSetTeamCamo,
            onLaunch = { ids, insertion, pilotId, platoonLeaderId ->
                launchPrepared(missionForPrep, ids, prepSquadId, insertion, pilotId, platoonLeaderId)
                prepMission = null
                prepTeamIds = emptyList()
                prepSquadId = null
            },
            onBack = {
                prepMission = null
                prepTeamIds = emptyList()
                prepSquadId = null
            }
        )
    }

    val missionToStage = stagingMission
    if (missionToStage != null) {
        // Item 131: a stray swipe no longer throws away the team; close with the X, Cancel, Back or a tap outside
        val stagingSheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden }
        )
        ModalBottomSheet(
            onDismissRequest = {
                stagingMission = null
                selectedOperatorIds = emptyList()
                selectedSquadId = null
            },
            sheetState = stagingSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            HqStagingSheet(
                mission = missionToStage,
                uiState = uiState,
                deployedOpIds = deployedOpIds,
                selectedOperatorIds = selectedOperatorIds,
                selectedSquadId = selectedSquadId,
                onSelectSquad = { squad ->
                    val readyIds = uiState.operators.filter {
                        TacticalCalculator.canOperatorDeployOnMission(
                            operator = it,
                            mission = missionToStage,
                            deployedOpIds = deployedOpIds,
                            gearItems = uiState.gearItems,
                            citations = uiState.citations,
                            facilities = uiState.facilities
                        ).eligible
                    }.map { it.id }.toSet()
                    val squadMembers = SquadCohesionRules.orderedMembersForDeployment(
                        squad = squad,
                        eligibleOperatorIds = readyIds,
                        maxCount = missionToStage.maxOperators
                    )
                    val currentSquadId = selectedSquadId
                    // Platoon missions: a second squad joins the first instead of replacing it
                    selectedOperatorIds = if (missionToStage.requiredSquads > 1 && currentSquadId != null && currentSquadId != squad.id) {
                        (selectedOperatorIds + squadMembers).distinct().take(missionToStage.maxOperators)
                    } else {
                        squadMembers
                    }
                    selectedSquadId = if (missionToStage.requiredSquads > 1 && currentSquadId != null) currentSquadId else squad.id
                },
                onSetTeam = { ids ->
                    selectedSquadId = null
                    selectedOperatorIds = ids.distinct().take(missionToStage.maxOperators)
                },
                onToggleOperator = { opId ->
                    selectedSquadId = null
                    selectedOperatorIds = if (opId in selectedOperatorIds) {
                        selectedOperatorIds - opId
                    } else if (selectedOperatorIds.size < missionToStage.maxOperators) {
                        selectedOperatorIds + opId
                    } else {
                        selectedOperatorIds
                    }
                },
                onSwitchOperatorsToKit = onSwitchOperatorsToKit,
                flightHours = flightHours,
                onFlightHoursChange = { flightHours = it },
                languageRegion = languageRegion,
                onLanguageRegionChange = { region ->
                    languageRegion = region
                    onSelectLanguageRegion(region)
                    // Item 134: drop anyone already speaking the new region from the class
                    selectedOperatorIds = selectedOperatorIds.filter { id ->
                        uiState.operators.find { it.id == id }?.let { !com.example.domain.ClandestineRules.knowsLanguage(it, region) } ?: false
                    }
                },
                onEditKits = {
                    stagingMission = null
                    selectedOperatorIds = emptyList()
                    selectedSquadId = null
                    onNavigateToSquad()
                },
                onLaunch = {
                    val launchWithSquad = onStartDeploymentWithSquad
                    val launchFlightHours = onStartFlightHours
                    if (missionToStage.id == TacticalCalculator.FLIGHT_HOURS_MISSION_ID && launchFlightHours != null) {
                        launchFlightHours(missionToStage, selectedOperatorIds, flightHours)
                    } else if (missionToStage.scope == MissionScope.FIELD_COMBAT && onLaunchPrepared != null) {
                        // Field missions get the prep screen first
                        prepMission = missionToStage
                        prepTeamIds = selectedOperatorIds
                        prepSquadId = selectedSquadId
                    } else if (launchWithSquad != null) {
                        launchWithSquad(missionToStage, selectedOperatorIds, selectedSquadId)
                    } else {
                        onStartDeployment(missionToStage, selectedOperatorIds)
                    }
                    stagingMission = null
                    selectedOperatorIds = emptyList()
                    selectedSquadId = null
                },
                onCancel = {
                    stagingMission = null
                    selectedOperatorIds = emptyList()
                    selectedSquadId = null
                }
            )
        }
    }
}

@Composable
private fun OpsSectionBanner(
    title: String,
    badgeText: String,
    badgeAccent: Color,
    expanded: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val extra = LocalAegisColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, extra.borderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = badgeAccent.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeAccent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse section" else "Expand section",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


/** Item 63: the POW list. Rescue while the window is open, open release talks, or wait it out. */
@Composable
private fun PowListPanel(
    pows: List<Operator>,
    deployments: List<ActiveDeployment>,
    prestige: Double,
    nowMillis: Long,
    onRescue: (String) -> Unit,
    onNegotiate: (String) -> Unit
) {
    val extra = LocalAegisColors.current
    TacticalPanelCard(
        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
        modifier = Modifier.testTag("pow_list")
    ) {
        Text(
            "PRISONERS OF WAR (${pows.size})",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.error
        )
        Text(
            "Held soldiers keep their roster slot. Rescue them while the window is open, open release talks " +
                "(${PowRules.NEGOTIATION_PRESTIGE_COST.toInt()} PRS, ${PowRules.GENERAL_NEGOTIATION_PRESTIGE_COST.toInt()} with a general; senior people at the table help), " +
                "or wait: they're released or lost when the time runs out.",
            style = MaterialTheme.typography.labelSmall,
            color = extra.textMuted
        )
        pows.forEach { pow ->
            val inProgress = deployments.any { it.targetOperatorId == pow.id }
            val rescueLeft = PowRules.rescueWindowRemainingMillis(pow, nowMillis)
            val waitLeft = (pow.captivityResolveAtMillis - nowMillis).coerceAtLeast(0L)
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, extra.borderSubtle),
                modifier = Modifier.fillMaxWidth().testTag("pow_${pow.id}")
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${pow.rank.code} ${pow.callsign}" + (if (TacticalCalculator.isSereQualified(pow)) " • SERE" else ""),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "Held in ${pow.captureTheater?.label ?: "enemy territory"} • " +
                            (if (inProgress) "operation under way" else "release or loss in ${formatMinSec(waitLeft)}") +
                            " • ${PowRules.releaseSurvivalPct(pow)}% survive the wait" +
                            (if (pow.powTalksFailures > 0) " • captors hardened -${pow.powTalksFailures * PowRules.HARDENED_PENALTY_PCT}%" else "") +
                            (if (pow.powRescueClosed) " • prisoner moved" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.textMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onRescue(pow.id) },
                            enabled = !inProgress && rescueLeft > 0L,
                            modifier = Modifier.weight(1f).testTag("pow_rescue_${pow.id}")
                        ) { Text(if (rescueLeft > 0L) "Rescue (${formatMinSec(rescueLeft)})" else "Rescue closed", maxLines = 1) }
                        val cooling = PowRules.talksCoolingDown(pow, nowMillis)
                        OutlinedButton(
                            onClick = { onNegotiate(pow.id) },
                            enabled = !inProgress && !cooling && prestige >= PowRules.GENERAL_NEGOTIATION_PRESTIGE_COST,
                            modifier = Modifier.weight(1f).testTag("pow_negotiate_${pow.id}")
                        ) {
                            Text(
                                if (cooling) "Talks (${formatMinSec(pow.powTalksCooldownUntilMillis - nowMillis)})" else "Talks (${PowRules.GENERAL_NEGOTIATION_PRESTIGE_COST.toInt()}-${PowRules.NEGOTIATION_PRESTIGE_COST.toInt()} PRS)",
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatMinSec(millis: Long): String {
    val total = (millis + 999L) / 1000L
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
