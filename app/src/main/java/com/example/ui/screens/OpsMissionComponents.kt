package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.catalog.GameCatalog
import com.example.domain.displayTitle
import com.example.domain.SquadCohesionRules
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import com.example.ui.components.RankInsigniaBadge
import com.example.ui.components.SfMarker
import com.example.ui.components.TacticalPanelCard
import com.example.ui.components.formatCountdown
import com.example.ui.theme.LocalAegisColors
import com.example.ui.viewmodel.CommandUiState
import kotlin.math.roundToInt

@Composable
internal fun ActiveHqDeploymentCard(
    deployment: ActiveDeployment,
    operators: List<Operator>,
    squads: List<TacticalSquad> = emptyList(),
    nowMillis: Long,
    onClaim: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val extra = LocalAegisColors.current
    val mission = GameCatalog.missions.find { it.id == deployment.templateId } ?: return
    var confirmCancel by remember { mutableStateOf(false) }
    val complete = deployment.isComplete(nowMillis)
    val progress = deployment.progressFraction(nowMillis)
    val remaining = deployment.remainingSeconds(nowMillis)
    val squadCallsigns = operators.filter { it.id in deployment.operatorIds }
        .joinToString(" • ") { "${it.rank.code} ${it.callsign}" }
    val activeSquad = deployment.squadId?.let { id -> squads.find { it.id == id } }
        ?: squads.firstOrNull { sq -> deployment.operatorIds.count { it in sq.memberIds } >= 2 }

    val commsIndex = when {
        complete -> mission.commsFeed.lastIndex
        progress > 0.55f && mission.commsFeed.size > 1 -> 1
        else -> 0
    }

    TacticalPanelCard(
        borderColor = if (complete) extra.brass else extra.signalsCyan.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${mission.theater.label.uppercase()} • ${mission.risk.label.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (complete) extra.brassBright else extra.signalsCyan
                )
                Text(
                    text = deployment.missionTitle ?: mission.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (complete) extra.brassContainer else extra.signalsContainer
            ) {
                Text(
                    text = if (complete) "RETURNED" else formatCountdown(remaining),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (complete) extra.brassBright else extra.signalsCyan,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        val metricLabel = if (mission.risk.isTraining) "Est. Pass" else "Survival"
        // Item 35: platoon missions list both squads and the platoon leader
        val deployedSquads = squads.filter { sq -> deployment.groundOperatorIds.count { it in sq.memberIds } >= 2 }
        if (mission.requiredSquads > 1 && deployedSquads.size > 1) {
            Text(
                text = "★ " + deployedSquads.joinToString(" + ") { it.name },
                style = MaterialTheme.typography.labelSmall,
                color = extra.brassBright
            )
        } else if (activeSquad != null) {
            Text(
                text = "★ ${activeSquad.name} (${activeSquad.cohesionScore}% Cohesion)",
                style = MaterialTheme.typography.labelSmall,
                color = extra.brassBright
            )
        }
        val leaderLine = listOfNotNull(
            deployment.platoonLeaderId?.let { id -> operators.find { it.id == id } }?.let { "Platoon Leader ${it.rank.code} ${it.callsign}" },
            deployment.pilotId?.let { id -> operators.find { it.id == id } }?.let { "Pilot ${it.rank.code} ${it.callsign}" },
            deployment.insertion.takeIf { it != InsertionMethod.GROUND }?.shortLabel
        )
        if (leaderLine.isNotEmpty()) {
            Text(leaderLine.joinToString(" • "), style = MaterialTheme.typography.labelSmall, color = extra.signalsCyan)
        }
        Text(
            text = "Team: $squadCallsigns • Eff ${deployment.efficiencyPct}% • $metricLabel ~${deployment.avgSurvivalPct}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(MaterialTheme.shapes.extraSmall),
            color = if (complete) extra.brass else extra.signalsCyan,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "> ${mission.commsFeed.getOrElse(commsIndex) { "" }}",
            style = MaterialTheme.typography.labelSmall,
            color = extra.textMuted
        )

        if (complete) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onClaim,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("claim_deployment_${deployment.id}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = extra.brass,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(Icons.Filled.AssignmentTurnedIn, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Debrief & Review Deeds")
            }
        } else if (onCancel != null) {
            // Item 103: cancel a school or abort a mission, with confirmation
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { confirmCancel = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .testTag("cancel_deployment_${deployment.id}"),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(if (mission.risk.isTraining) "Cancel Class" else "Abort Mission")
            }
        }
    }

    if (confirmCancel && onCancel != null) {
        val title = deployment.missionTitle ?: mission.displayTitle
        val (woundPct, deathPct) = TacticalCalculator.abortCasualtyOddsPct(mission.risk, progress, deployment.insertion)
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(if (mission.risk.isTraining) "Cancel $title?" else "Abort $title?") },
            text = {
                Text(
                    if (mission.risk.isTraining) {
                        "Everyone returns to the unit. The attempt still counts and the cost is not refunded."
                    } else {
                        "The team pulls out with no rewards. Each soldier risks about ${"%.1f".format(woundPct)}% wounded" +
                            (if (deathPct > 0.0) " and ${"%.1f".format(deathPct)}% killed" else "") +
                            " during the withdrawal. Good Conduct streaks reset."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmCancel = false; onCancel() },
                    modifier = Modifier.testTag("confirm_cancel_deployment")
                ) { Text(if (mission.risk.isTraining) "Cancel Class" else "Abort", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep Going") } }
        )
    }
}

@Composable
internal fun CompactHqMissionCard(
    mission: MissionTemplate,
    adjustedDurationSec: Int,
    adjustedSupplyCost: Int,
    canAffordSupply: Boolean,
    slotsFull: Boolean,
    qualifiedCount: Int,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onAssemble: () -> Unit,
    isStageLocked: Boolean = false,
    lockReasonText: String? = null,
    isStageCompleted: Boolean = false
) {
    val extra = LocalAegisColors.current
    val riskColor = when (mission.risk) {
        MissionRisk.TRAINING -> MaterialTheme.colorScheme.primary
        MissionRisk.LOW -> extra.signalsCyan
        MissionRisk.MODERATE -> extra.amberTelemetry
        MissionRisk.HIGH -> MaterialTheme.colorScheme.error
        MissionRisk.EXTREME -> extra.brassBright
    }
    val hasMinQualified = qualifiedCount >= mission.minOperators
    val schoolReq = mission.schoolRequirements
    val uniformReq = TacticalCalculator.missionUniformRequirement(mission)
    val kitBadgeColor = when (uniformReq.kit) {
        WardrobeKit.FIELD -> MaterialTheme.colorScheme.primary
        WardrobeKit.BRAVOS -> extra.signalsCyan
        WardrobeKit.ALPHAS -> extra.brassBright
        WardrobeKit.MESS -> extra.brassBright
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mission_card_${mission.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (expanded) riskColor.copy(alpha = 0.65f) else extra.borderSubtle
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        // Item 48: each theater has its own color stripe down the card's left edge
        val theaterAccent = Color(mission.theater.accentArgb)
        Column(
            modifier = Modifier
                .drawBehind { drawRect(theaterAccent, size = Size(4.dp.toPx(), size.height)) }
                .padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = buildString {
                                append("${mission.theater.label.uppercase()} • ${mission.risk.label.uppercase()}")
                                if (mission.scope.hqStaffAllowed && mission.scope != MissionScope.TRAINING) {
                                    append(" • HQ OK")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isStageLocked) extra.textMuted else riskColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        mission.contractTag?.let { tag ->
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = extra.brassContainer.copy(alpha = 0.65f)
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.brassBright,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (isStageCompleted) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "CLEARED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = mission.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isStageLocked) extra.textMuted else MaterialTheme.colorScheme.onSurface,
                        maxLines = if (expanded) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = formatCountdown(adjustedDurationSec.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${if (mission.hasClassCap) "Team" else "Class"} ${mission.teamSizeLabel} • $adjustedSupplyCost SUP${if (mission.hasClassCap) "" else "/soldier"} • ~${mission.baseSuccessRatePct}% Base",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (canAffordSupply) extra.textMuted else MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append("+${mission.rewardCredits.toInt()} CR • +${mission.rewardPrestige.toInt()} PRS • +${mission.rewardXp} XP")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.brassBright,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = kitBadgeColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, kitBadgeColor.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .testTag("mission_uniform_req_${mission.id}")
                    ) {
                        Text(
                            text = uniformReq.shortBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = kitBadgeColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (isStageLocked && lockReasonText != null) {
                        Text(
                            text = lockReasonText,
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.amberTelemetry,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (hasMinQualified) "$qualifiedCount Ready" else "$qualifiedCount/${mission.minOperators} Ready",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (hasMinQualified) MaterialTheme.colorScheme.primary else extra.amberTelemetry
                        )
                        Row(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable(onClick = onToggleExpand)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("toggle_mission_details_${mission.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (expanded) "Hide brief" else "Brief & reqs",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.signalsCyan
                            )
                            Icon(
                                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (expanded) "Hide details" else "Show details",
                                tint = extra.signalsCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onAssemble,
                    enabled = !slotsFull && !isStageLocked,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("assemble_mission_${mission.id}"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    if (isStageLocked) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Locked")
                    } else {
                        Text(if (slotsFull) "Full" else "Assign")
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(color = extra.borderSubtle)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = mission.briefing,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Focus: ${mission.primaryFocus} (${if (mission.hasClassCap) "Avg Power" else "Power"} ${mission.recommendedPower}+${if (mission.hasClassCap) " per soldier" else ""})",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.textMuted
                    )
                    if (schoolReq != null) {
                        Text(
                            text = "Requirements: ${schoolReq.suitabilitySummary}",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.signalsCyan
                        )
                        if (schoolReq.graduationRewardsSummary.isNotBlank()) {
                            Text(
                                text = "Grad Rewards: ${schoolReq.graduationRewardsSummary}",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.brassBright
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HqStagingSheet(
    mission: MissionTemplate,
    uiState: CommandUiState,
    deployedOpIds: Set<String>,
    selectedOperatorIds: List<String>,
    selectedSquadId: String? = null,
    onSelectSquad: (TacticalSquad) -> Unit = {},
    onToggleOperator: (String) -> Unit,
    onSwitchOperatorsToKit: (List<String>, WardrobeKit) -> Unit = { _, _ -> },
    onEditKits: () -> Unit,
    onLaunch: () -> Unit,
    onCancel: () -> Unit,
    flightHours: Int = TacticalCalculator.FLIGHT_HOURS_PER_SESSION,
    onFlightHoursChange: (Int) -> Unit = {}
) {
    val extra = LocalAegisColors.current
    val unlockedOps = uiState.operators.filter { it.unlocked && !it.status.isOutOfActiveService }
    val evaluatedOps = unlockedOps.map { op ->
        op to TacticalCalculator.canOperatorDeployOnMission(
            operator = op,
            mission = mission,
            deployedOpIds = deployedOpIds,
            gearItems = uiState.gearItems,
            citations = uiState.citations,
            facilities = uiState.facilities
        )
    }
    val readyOperators = evaluatedOps.filter { it.second.eligible }.map { it.first }
    val readyOpIds = readyOperators.map { it.id }.toSet()
    val ineligibleOperators = evaluatedOps.filterNot { it.second.eligible }

    val selectedOps = readyOperators.filter { it.id in selectedOperatorIds }
    val uniformReq = TacticalCalculator.missionUniformRequirement(mission)
    val kitBonus = TacticalCalculator.evaluateMissionKitBonus(
        mission = mission,
        selectedOperators = selectedOps,
        smartWardrobeEnabled = uiState.smartWardrobeEnabled
    )
    val efficiency = TacticalCalculator.missionEfficiencyPct(
        mission = mission,
        selectedOperators = selectedOps,
        gearItems = uiState.gearItems,
        citations = uiState.citations,
        facilities = uiState.facilities,
        hqStaffCount = uiState.hqStaffCount,
        hqStaffSummary = uiState.hqStaffSummary,
        squads = uiState.squads,
        inspectionBuffActive = uiState.inspectionBuffActive,
        smartWardrobeEnabled = uiState.smartWardrobeEnabled
    )
    val avgSurvival = TacticalCalculator.squadAvgSurvivalPct(
        mission, selectedOps, uiState.gearItems, uiState.citations, uiState.facilities, uiState.hqStaffCount, uiState.hqStaffSummary, uiState.squads,
        uiState.smartWardrobeEnabled
    )
    val successOdds = TacticalCalculator.missionSuccessOddsPct(
        mission = mission,
        selectedOperators = selectedOps,
        gearItems = uiState.gearItems,
        citations = uiState.citations,
        facilities = uiState.facilities,
        hqStaffCount = uiState.hqStaffCount,
        hqStaffSummary = uiState.hqStaffSummary,
        squads = uiState.squads,
        smartWardrobeEnabled = uiState.smartWardrobeEnabled
    )
    val activeSquadBonus = SquadCohesionRules.evaluateActiveBonus(selectedOps, uiState.squads, mission.requiredSquads > 1)
    // Schools charge per enrolled soldier; missions charge one fee per team
    val supplyCost = (TacticalCalculator.dispatchSupplyCost(
        mission = mission,
        soldierCount = selectedOperatorIds.size,
        multiplier = TacticalCalculator.supplyCostMultiplier(
            facilities = uiState.facilities,
            hqStaffSummary = uiState.hqStaffSummary,
            completedCampaignIds = uiState.completedCampaignIds
        )
    ) * com.example.domain.SoldierEvents.schoolDiscountFactor(mission, selectedOps, uiState.nowMillis)).roundToInt()
        // Item 96: retrying an SF school past the old limit costs more
        .plus(
            TacticalCalculator.schoolRetrySurcharge(
                mission, selectedOps,
                TacticalCalculator.supplyCostMultiplier(uiState.facilities, uiState.hqStaffSummary, uiState.completedCampaignIds)
            )
        )
    val validCount = selectedOperatorIds.size in mission.minOperators..mission.maxOperators
    val canAfford = uiState.resources.supplies >= supplyCost

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = mission.displayTitle.uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onEditKits,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Edit Loadouts")
            }
        }

        Text(
            text = mission.briefing,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Item 95: pick how many hours to fly
        if (mission.id == TacticalCalculator.FLIGHT_HOURS_MISSION_ID) {
            val hoursMult = TacticalCalculator.flightHoursDurationMultiplier(flightHours)
            Text(
                "Flight Hours: $flightHours h • about ${formatCountdown((mission.durationSeconds * hoursMult).toLong())} • " +
                    "+${flightHours * TacticalCalculator.FLIGHT_SKILL_PER_HOUR + TacticalCalculator.airfieldFlightSkillBonus(TacticalCalculator.facilityLevel(uiState.facilities, FacilityType.AIRFIELD))} skill if passed",
                style = MaterialTheme.typography.labelMedium,
                color = extra.brassBright
            )
            Slider(
                value = flightHours.toFloat(),
                onValueChange = { onFlightHoursChange(it.roundToInt()) },
                valueRange = TacticalCalculator.MIN_FLIGHT_HOURS.toFloat()..TacticalCalculator.MAX_FLIGHT_HOURS.toFloat(),
                steps = TacticalCalculator.MAX_FLIGHT_HOURS - TacticalCalculator.MIN_FLIGHT_HOURS - 1,
                modifier = Modifier.testTag("flight_hours_slider")
            )
            Text(
                "A failed session still logs about half the hours.",
                style = MaterialTheme.typography.labelSmall,
                color = extra.textMuted
            )
        }

        mission.schoolRequirements?.let { schoolReq ->
            Text(
                text = "Requirements: ${schoolReq.suitabilitySummary}",
                style = MaterialTheme.typography.labelSmall,
                color = extra.signalsCyan
            )
            if (schoolReq.graduationRewardsSummary.isNotBlank()) {
                Text(
                    text = "Grad Rewards: ${schoolReq.graduationRewardsSummary}",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.brassBright
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (mission.risk.isTraining) "COURSE PASS ODDS" else "MISSION SUCCESS",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.textMuted
                )
                Text(
                    text = if (selectedOps.isEmpty()) "—" else "$successOdds%",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = when {
                        successOdds >= 82 -> MaterialTheme.colorScheme.primary
                        successOdds >= 65 -> extra.amberTelemetry
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (mission.risk.isTraining) "RISK" else "SURVIVAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.textMuted
                )
                Text(
                    text = if (mission.risk.isTraining) "Safe" else if (selectedOps.isEmpty()) "—" else "$avgSurvival%",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = when {
                        mission.risk.isTraining || avgSurvival >= 92 -> MaterialTheme.colorScheme.primary
                        avgSurvival >= 78 -> extra.amberTelemetry
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("EFFICIENCY", style = MaterialTheme.typography.labelSmall, color = extra.textMuted)
                Text(
                    text = if (selectedOps.isEmpty()) "—" else "$efficiency%",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = extra.brassBright
                )
            }
        }

        // Item 82: aviator-only training (Flight Hours) has no squads to pick
        val aviatorsOnly = mission.schoolRequirements?.allowedSpecialties == setOf(OperatorSpecialty.AVIATION)
        if (uiState.squads.isNotEmpty() && readyOperators.isNotEmpty() && !aviatorsOnly) {
            Text(
                text = "SELECT SQUAD (ONE-TAP TEAM • LEADER FIRST)",
                style = MaterialTheme.typography.labelSmall,
                color = extra.textMuted
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.squads.forEach { squad ->
                    val deployableForSquad = SquadCohesionRules.orderedMembersForDeployment(
                        squad = squad,
                        eligibleOperatorIds = readyOpIds,
                        maxCount = mission.maxOperators
                    )
                    val leaderOp = squad.leaderId?.let { id -> uiState.operators.find { it.id == id } }
                    val isSquadSelected = selectedSquadId == squad.id ||
                        (deployableForSquad.isNotEmpty() && deployableForSquad.toSet() == selectedOperatorIds.toSet())
                    val canDeploySquad = deployableForSquad.size >= mission.minOperators ||
                        (squad.isSpecialForces && mission.requiresSfTeam && deployableForSquad.size >= TacticalCalculator.SF_SQUAD_CORE)
                    val sfRed = Color(SF_PATCH_RED_BRIGHT)

                    Surface(
                        modifier = Modifier
                            .widthIn(min = 160.dp)
                            .defaultMinSize(minHeight = 56.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(enabled = canDeploySquad) { onSelectSquad(squad) }
                            .testTag("stage_squad_${squad.id}"),
                        color = if (isSquadSelected) {
                            extra.brassContainer.copy(alpha = 0.72f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = BorderStroke(
                            if (isSquadSelected || squad.isSpecialForces) 1.5.dp else 1.dp,
                            when {
                                squad.isSpecialForces -> sfRed
                                isSquadSelected -> extra.brassBright
                                else -> extra.borderSubtle
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (squad.isSpecialForces) SfMarker()
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = if (isSquadSelected) extra.brassBright else extra.signalsCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = squad.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (canDeploySquad) MaterialTheme.colorScheme.onSurface else extra.textMuted
                                )
                            }
                            Text(
                                text = buildString {
                                    append("${deployableForSquad.size}/${squad.memberIds.size} Ready")
                                    leaderOp?.let { append(" • LDR ${it.callsign}") }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (canDeploySquad) extra.signalsCyan else MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Cohesion ${squad.cohesionScore}% (${squad.cohesionTier.label})",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.brassBright
                            )
                        }
                    }
                }
            }
        }

        // Uniform Protocol & Bonus Status Card
        val kitHighlight = kitBonus.allMatched || uniformReq.isMandatory
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("staging_uniform_protocol_${mission.id}"),
            shape = MaterialTheme.shapes.small,
            color = if (kitBonus.allMatched) {
                extra.signalsContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            border = BorderStroke(
                1.dp,
                if (kitBonus.allMatched) extra.signalsCyan.copy(alpha = 0.65f) else extra.borderSubtle
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = buildString {
                            append(if (uniformReq.isMandatory) "MANDATORY UNIFORM: " else "RECOMMENDED UNIFORM: ")
                            append(uniformReq.kit.label.uppercase())
                            if (uiState.smartWardrobeEnabled) append(" (AUTO)")
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (kitHighlight) extra.signalsCyan else extra.brassBright
                    )
                    val noteText = kitBonus.summaryLabel
                        ?: if (uniformReq.isMandatory) {
                            if (uiState.smartWardrobeEnabled) {
                                "Smart Duty-Dress auto-equips Field ACU on dispatch"
                            } else {
                                "Field Ops deploy in tactical ACU combat kit"
                            }
                        } else {
                            uniformReq.bonusSummary ?: uniformReq.kit.dutyHint
                        }
                    Text(
                        text = noteText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!uiState.smartWardrobeEnabled && !kitBonus.allMatched && selectedOps.isNotEmpty() &&
                    selectedOps.any { uniformReq.kit.isAvailableFor(it) }
                ) {
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { onSwitchOperatorsToKit(selectedOperatorIds, uniformReq.kit) },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("staging_equip_kit_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Equip ${uniformReq.kit.shortLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (activeSquadBonus != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("staging_cohesion_bonus_banner"),
                shape = MaterialTheme.shapes.small,
                color = extra.brassContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, extra.brass.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = null,
                        tint = extra.brassBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${activeSquadBonus.squad.name.uppercase()} • ${activeSquadBonus.tier.label.uppercase()} (${activeSquadBonus.cohesionScore}%)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = extra.brassBright
                        )
                        Text(
                            text = buildString {
                                append("+${activeSquadBonus.efficiencyBonusPct}% Eff")
                                if (!mission.risk.isTraining && activeSquadBonus.survivalBonusPct > 0) {
                                    append(" • +${activeSquadBonus.survivalBonusPct}% Surv")
                                }
                                if (!mission.risk.isTraining && activeSquadBonus.successOddsBonusPct > 0) {
                                    append(" • +${activeSquadBonus.successOddsBonusPct}% Odds")
                                }
                                append(if (activeSquadBonus.leaderPresent) " • Leader Leading" else " • Leader Absent (60%)")
                                if (activeSquadBonus.fullStrength) append(" • Full Strength")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (readyOperators.isEmpty()) {
            Text(
                text = if (mission.risk.isTraining) {
                    "No personnel currently meet prerequisites & aptitude for this course."
                } else {
                    "No ready personnel available at HQ right now."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                readyOperators.forEach { op ->
                    val isSelected = op.id in selectedOperatorIds
                    val opGear = uiState.gearItems.equippedBy(op)
                    val opOdds = if (mission.risk.isTraining) {
                        TacticalCalculator.schoolPassOddsPct(
                            mission = mission,
                            operator = op,
                            citations = uiState.citations,
                            hqStaffSummary = uiState.hqStaffSummary,
                            facilities = uiState.facilities
                        )
                    } else {
                        TacticalCalculator.operatorSurvivalPct(
                            mission, op, uiState.gearItems, uiState.citations, uiState.facilities, uiState.hqStaffCount, uiState.hqStaffSummary
                        )
                    }
                    val attemptsUsed = op.schoolAttempts[mission.id] ?: 0
                    val maxAttempts = mission.schoolRequirements?.maxAttempts

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 52.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onToggleOperator(op.id) }
                            .testTag("stage_op_${op.id}"),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else extra.borderSubtle
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                RankInsigniaBadge(rank = op.rank)
                                if (TacticalCalculator.isSfQualified(op)) SfMarker()
                                Column {
                                    Text(
                                        text = buildString {
                                            append("${op.callsign} • ")
                                            if (op.status == OperatorStatus.HQ_STAFF) {
                                                append("HQ ${op.hqRole?.shortCode ?: "STAFF"}")
                                            } else {
                                                append("${opGear.size}/5 Kit")
                                            }
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (op.status == OperatorStatus.HQ_STAFF) extra.signalsCyan else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = buildString {
                                            append(op.specialty.label)
                                            if (mission.risk.isTraining) {
                                                append(" • Pass $opOdds%")
                                                if (maxAttempts != null) append(" (${attemptsUsed}/$maxAttempts used)")
                                            } else {
                                                append(" • Survival $opOdds%")
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (opOdds >= 80) MaterialTheme.colorScheme.primary else extra.amberTelemetry
                                    )
                                }
                            }
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleOperator(op.id) }
                            )
                        }
                    }
                }
            }
        }

        if (ineligibleOperators.isNotEmpty()) {
            HorizontalDivider(color = extra.borderSubtle)
            Text(
                text = "UNSUITED / LOCKED FOR THIS OP",
                style = MaterialTheme.typography.labelSmall,
                color = extra.textMuted
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ineligibleOperators.take(4).forEach { (op, elig) ->
                    Text(
                        text = "• ${op.rank.code} ${op.callsign}: ${elig.reasonText}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onLaunch,
                enabled = validCount && canAfford,
                modifier = Modifier
                    .weight(1.4f)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("launch_mission_${mission.id}")
            ) {
                Text(if (!canAfford) "Need $supplyCost SUP" else "Dispatch ($supplyCost SUP)")
            }
        }
    }
}

@Composable
internal fun RotatingContractsBanner(
    remainingSec: Long,
    currentIntel: Double,
    activeRotatingCount: Int,
    onRefresh: () -> Unit
) {
    val extra = LocalAegisColors.current
    val intelCost = TacticalCalculator.ROTATION_REFRESH_INTEL_COST.toInt()
    val canRefresh = currentIntel >= TacticalCalculator.ROTATION_REFRESH_INTEL_COST
    TacticalPanelCard(
        containerColor = extra.signalsContainer.copy(alpha = 0.25f),
        borderColor = extra.signalsCyan.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = extra.signalsCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "ROTATING STANDALONE OPS ($activeRotatingCount ACTIVE)",
                        style = MaterialTheme.typography.labelLarge,
                        color = extra.signalsCyan
                    )
                }
                Text(
                    text = "Next 4m rotation: ${formatCountdown(remainingSec)} • Evergreen ops always live",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onRefresh,
                enabled = canRefresh,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("refresh_rotating_ops"),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Cycle rotating contracts",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$intelCost INT",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
internal fun TheaterCampaignCard(
    campaign: TheaterCampaign,
    stageMissions: List<MissionTemplate>,
    completedStageIds: Set<String>,
    completedCampaignIds: Set<String>,
    allCampaignsCleared: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    qualifiedByMissionId: Map<String, Int>,
    durationMult: Double,
    supplyMult: Double,
    currentSupplies: Double,
    slotsFull: Boolean,
    expandedMissionIds: Set<String>,
    onToggleMissionDetails: (String) -> Unit,
    onAssembleMission: (MissionTemplate) -> Unit
) {
    val extra = LocalAegisColors.current
    val isUnlocked = TacticalCalculator.isCampaignUnlocked(campaign, completedCampaignIds)
    val isCampaignCleared = campaign.id in completedCampaignIds
    val clearedStagesCount = campaign.stageMissionIds.count { it in completedStageIds }

    val statusText = when {
        isCampaignCleared && allCampaignsCleared -> "FULL REPLAY"
        isCampaignCleared -> "CLEARED"
        isUnlocked -> "ACTIVE"
        else -> "LOCKED"
    }
    val statusColor = when {
        isCampaignCleared -> extra.brassBright
        isUnlocked -> extra.signalsCyan
        else -> extra.textMuted
    }

    TacticalPanelCard(
        modifier = Modifier.testTag("campaign_card_${campaign.id}"),
        containerColor = if (isUnlocked) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        borderColor = when {
            isCampaignCleared -> extra.brass.copy(alpha = 0.75f)
            isUnlocked -> extra.signalsCyan.copy(alpha = 0.6f)
            else -> extra.borderSubtle
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onToggleExpand)
                .padding(vertical = 2.dp)
                .testTag("campaign_toggle_${campaign.id}"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = statusColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "${campaign.theater.label.uppercase()} • ${campaign.difficultyLabel.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUnlocked) Color(campaign.theater.accentArgb) else extra.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = campaign.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else extra.textMuted
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$clearedStagesCount/${campaign.stageMissionIds.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse campaign" else "Expand campaign",
                    tint = extra.signalsCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = campaign.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))

        // Stage Stepper Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            stageMissions.forEach { stage ->
                val stageNum = stage.campaignStageOrder ?: 1
                val done = stage.id in completedStageIds
                val stageUnlocked = TacticalCalculator.isCampaignStageUnlocked(
                    mission = stage,
                    completedStageIds = completedStageIds,
                    completedCampaignIds = completedCampaignIds
                )
                val isDiplo = stage.scope == MissionScope.STAFF_DIPLOMATIC
                val chipColor = when {
                    done -> MaterialTheme.colorScheme.primary
                    stageUnlocked -> extra.signalsCyan
                    else -> extra.textMuted
                }
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = chipColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, chipColor.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                done -> Icons.Filled.CheckCircle
                                stageUnlocked -> Icons.Filled.Star
                                else -> Icons.Filled.Lock
                            },
                            contentDescription = null,
                            tint = chipColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (isDiplo) "S$stageNum Diplo" else "Stage $stageNum",
                            style = MaterialTheme.typography.labelSmall,
                            color = chipColor
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isCampaignCleared) {
                "Theater Perk ACTIVE: ${campaign.theaterBuffLabel}"
            } else {
                campaign.rewardSummary
            },
            style = MaterialTheme.typography.labelSmall,
            color = extra.brassBright
        )

        if (!isUnlocked) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Locked — Clear Campaign ${campaign.order - 1} to authorize operations here.",
                style = MaterialTheme.typography.labelSmall,
                color = extra.amberTelemetry
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                stageMissions.forEach { stage ->
                    val stageUnlocked = TacticalCalculator.isCampaignStageUnlocked(
                        mission = stage,
                        completedStageIds = completedStageIds,
                        completedCampaignIds = completedCampaignIds
                    )
                    val stageNum = stage.campaignStageOrder ?: 1
                    val lockReason = when {
                        stageUnlocked -> null
                        !isUnlocked -> "Clear Campaign ${campaign.order - 1} to unlock"
                        else -> "Clear Stage ${stageNum - 1} first"
                    }
                    val adjustedSec = (stage.durationSeconds * durationMult).roundToInt()
                    val adjustedSup = (stage.supplyCost * supplyMult).roundToInt()
                    val qualifiedCount = qualifiedByMissionId[stage.id] ?: 0

                    CompactHqMissionCard(
                        mission = stage,
                        adjustedDurationSec = adjustedSec,
                        adjustedSupplyCost = adjustedSup,
                        canAffordSupply = currentSupplies >= adjustedSup,
                        slotsFull = slotsFull,
                        qualifiedCount = qualifiedCount,
                        expanded = stage.id in expandedMissionIds,
                        onToggleExpand = { onToggleMissionDetails(stage.id) },
                        onAssemble = { onAssembleMission(stage) },
                        isStageLocked = !stageUnlocked,
                        lockReasonText = lockReason,
                        isStageCompleted = stage.id in completedStageIds
                    )
                }
            }
        }
    }
}
