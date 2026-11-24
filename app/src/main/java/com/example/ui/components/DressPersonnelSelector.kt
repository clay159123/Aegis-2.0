package com.example.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.SquadCohesionRules
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import com.example.ui.theme.LocalAegisColors

sealed interface PersonnelGroupFilter {
    val key: String
    val label: String
    val testTag: String

    data object All : PersonnelGroupFilter {
        override val key: String = "all"
        override val label: String = "All"
        override val testTag: String = "dress_group_all"
    }

    data object ActionNeeded : PersonnelGroupFilter {
        override val key: String = "action"
        override val label: String = "Awards / Board"
        override val testTag: String = "dress_group_action"
    }

    data object HqStaff : PersonnelGroupFilter {
        override val key: String = "hq_staff"
        override val label: String = "Command Staff"
        override val testTag: String = "dress_group_hq"
    }

    data class Squad(val squadId: String, val squadName: String) : PersonnelGroupFilter {
        override val key: String = "squad_$squadId"
        override val label: String = squadName
        override val testTag: String = "dress_group_squad_$squadId"
    }

    data class FieldUnassigned(val hasSquads: Boolean) : PersonnelGroupFilter {
        override val key: String = "field_unassigned"
        override val label: String = if (hasSquads) "Unassigned" else "Field Ops"
        override val testTag: String = "dress_group_field"
    }

    data object Aviators : PersonnelGroupFilter {
        override val key: String = "aviators"
        override val label: String = "Aviators"
        override val testTag: String = "dress_group_aviators"
    }
}

enum class RosterSortMode(val label: String) {
    BY_BILLET("By Billet"),
    BY_RANK("Highest Rank"),
    BY_HONORS("Most Honors")
}

data class OrganizedRosterSection(
    val id: String,
    val title: String,
    val subtitle: String?,
    val accentIsCommand: Boolean = false,
    val operators: List<Operator>
)

internal fun buildOrganizedRosterSections(
    unlockedOps: List<Operator>,
    squads: List<TacticalSquad>
): List<OrganizedRosterSection> {
    val sections = mutableListOf<OrganizedRosterSection>()
    val byId = unlockedOps.associateBy { it.id }

    // 1. HQ Command Staff ordered by official billet slot (CO, XO, S-1, S-3, etc.) then rank
    val hqOps = unlockedOps
        .filter { it.status == OperatorStatus.HQ_STAFF }
        .sortedWith(
            compareBy<Operator> { it.hqRole?.slotOrder ?: 99 }
                .thenByDescending { it.rank.seniorityIndex }
                .thenByDescending { it.xp }
        )
    if (hqOps.isNotEmpty()) {
        sections += OrganizedRosterSection(
            id = "sec_hq_staff",
            title = "HQ Command Staff (${hqOps.size})",
            subtitle = "Directorate billets & senior staff officers",
            accentIsCommand = true,
            operators = hqOps
        )
    }

    // 2. Named Tactical Squads (leader first, then by rank descending)
    val assignedSquadMemberIds = mutableSetOf<String>()
    // Item 146: Squads A to D first, then the SF squads
    SquadCohesionRules.displayOrder(squads).forEach { squad ->
        val squadMembers = squad.memberIds
            .mapNotNull { byId[it] }
            .filter { it.status != OperatorStatus.HQ_STAFF && !it.status.isOutOfActiveService }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<Operator> { it.id == squad.leaderId }
                    .thenByDescending { it.rank.seniorityIndex }
                    .thenByDescending { it.xp }
            )
        if (squadMembers.isNotEmpty()) {
            assignedSquadMemberIds += squadMembers.map { it.id }
            val leaderOp = squadMembers.find { it.id == squad.leaderId }
            sections += OrganizedRosterSection(
                id = "sec_squad_${squad.id}",
                title = "${squad.name} (${squadMembers.size}/$MAX_SQUAD_SIZE)",
                subtitle = if (leaderOp != null) {
                    "Squad Leader: ${leaderOp.rank.code} ${leaderOp.callsign} • ${squad.missionsTogether} Ops together"
                } else {
                    "${squad.missionsTogether} Ops together"
                },
                operators = squadMembers
            )
        }
    }

    // 3. Aviators (item 81; item 147 puts them before Unassigned): pilots aren't squad members, so they get their own group
    val aviators = unlockedOps
        .filter { it.isAviator && it.status != OperatorStatus.HQ_STAFF && !it.status.isOutOfActiveService }
        .sortedWith(compareByDescending<Operator> { it.rank.seniorityIndex }.thenByDescending { it.flightSkill })
    if (aviators.isNotEmpty()) {
        sections += OrganizedRosterSection(
            id = "sec_aviators",
            title = "Aviators (${aviators.size})",
            subtitle = "Warrant officer pilots of the Aviation Pool",
            operators = aviators
        )
    }

    // 4. Unassigned Field Personnel (sorted by rank descending)
    val unassignedField = unlockedOps
        .filter {
            it.status != OperatorStatus.HQ_STAFF &&
                !it.status.isOutOfActiveService &&
                !it.isAviator &&
                it.id !in assignedSquadMemberIds
        }
        .sortedWith(
            compareByDescending<Operator> { it.rank.seniorityIndex }
                .thenByDescending { it.xp }
        )
    if (unassignedField.isNotEmpty()) {
        sections += OrganizedRosterSection(
            id = "sec_field_unassigned",
            title = if (squads.isNotEmpty()) {
                "Unassigned Field Roster (${unassignedField.size})"
            } else {
                "Field Roster (${unassignedField.size})"
            },
            subtitle = "Active deployable soldiers & operators",
            operators = unassignedField
        )
    }

    // Item 151: the fallen moved to the Memorial on the Honors page
    return sections
}

internal fun resolveOperatorAssignmentSummary(
    operator: Operator,
    squads: List<TacticalSquad>,
    isDeployed: Boolean
): String {
    if (operator.status == OperatorStatus.FALLEN) {
        return "Memorial Roll • ${operator.mosTag}"
    }
    if (operator.status == OperatorStatus.HQ_STAFF) {
        val role = operator.hqRole
        return if (role != null) {
            "HQ Staff • ${role.shortCode} (${role.title})"
        } else {
            "HQ Command Staff • ${operator.mosTag}"
        }
    }
    val squad = squads.find { operator.id in it.memberIds }
    val statusSuffix = when {
        operator.id in TacticalCalculator.tdyOperatorIds -> " • TDY"
        isDeployed || operator.status == OperatorStatus.DEPLOYED -> " • Deployed"
        operator.status == OperatorStatus.RECOVERING -> " • Medbay"
        else -> ""
    }
    return if (squad != null) {
        val leaderTag = if (squad.leaderId == operator.id) " ★ Leader" else ""
        "${squad.name}$leaderTag • ${operator.mosTag}$statusSuffix"
    } else {
        "Field Roster • ${operator.mosTag}$statusSuffix"
    }
}

@Composable
fun DressPersonnelSwitcherCard(
    unlockedOps: List<Operator>,
    selectedOp: Operator,
    squads: List<TacticalSquad>,
    deployments: List<ActiveDeployment>,
    pendingDeeds: List<PendingDeed>,
    citations: List<AwardedMedal>,
    gearItems: List<GearItem>,
    facilities: List<FacilityState>,
    onSelectOperator: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = LocalAegisColors.current
    var showDirectorySheet by rememberSaveable { mutableStateOf(false) }
    var activeFilterKey by rememberSaveable { mutableStateOf(PersonnelGroupFilter.All.key) }

    val deployedIds = remember(deployments) {
        deployments.flatMap { it.operatorIds }.toSet()
    }
    val deedCountsByOp = remember(pendingDeeds) {
        pendingDeeds.groupingBy { it.operatorId }.eachCount()
    }
    val honorCountsByOp = remember(citations) {
        citations
            .groupBy { it.operatorId }
            .mapValues { (_, list) -> list.map { it.medalId }.distinct().size }
    }
    // Session 33: soldiers XP has made promotable (the "P"), counted with board-ready soldiers in Action Needed
    val promotableIds = remember(unlockedOps) { unlockedOps.filter { it.pinEligibleRank != null }.map { it.id }.toSet() }
    val boardReadyIds = promotableIds + remember(unlockedOps, citations, gearItems, facilities) {
        unlockedOps.filter { op ->
            val next = OperatorRank.nextRank(op.rank)
            if (next == null || next.tier == op.rank.tier || op.status == OperatorStatus.FALLEN) {
                false
            } else {
                TacticalCalculator.evaluatePromotionBoard(
                    operator = op,
                    allOperators = unlockedOps,
                    citations = citations,
                    gearItems = gearItems,
                    facilities = facilities
                ).allMeritsMet
            }
        }.map { it.id }.toSet()
    }

    val organizedSections = remember(unlockedOps, squads) {
        buildOrganizedRosterSections(unlockedOps, squads)
    }
    val billetOrderedAll = remember(organizedSections) {
        organizedSections.flatMap { it.operators }
    }

    val assignedSquadIds = remember(squads, unlockedOps) {
        squads.flatMap { sq ->
            sq.memberIds.filter { id ->
                val op = unlockedOps.find { it.id == id }
                op != null && op.status != OperatorStatus.HQ_STAFF && !op.status.isOutOfActiveService
            }
        }.toSet()
    }

    val availableFilters = remember(unlockedOps, squads, deedCountsByOp, boardReadyIds, assignedSquadIds) {
        buildList {
            add(PersonnelGroupFilter.All to billetOrderedAll)
            val actionOps = billetOrderedAll.filter {
                (deedCountsByOp[it.id] ?: 0) > 0 || it.id in boardReadyIds
            }
            if (actionOps.isNotEmpty()) {
                add(PersonnelGroupFilter.ActionNeeded to actionOps)
            }
            val hqOps = billetOrderedAll.filter { it.status == OperatorStatus.HQ_STAFF }
            if (hqOps.isNotEmpty()) {
                add(PersonnelGroupFilter.HqStaff to hqOps)
            }
            SquadCohesionRules.displayOrder(squads).forEach { sq ->
                val members = billetOrderedAll.filter {
                    it.id in sq.memberIds &&
                        it.status != OperatorStatus.HQ_STAFF &&
                        !it.status.isOutOfActiveService
                }
                if (members.isNotEmpty()) {
                    add(PersonnelGroupFilter.Squad(sq.id, sq.name) to members)
                }
            }
            // Item 147: Aviators get their own chip ahead of Unassigned
            val aviatorOps = billetOrderedAll.filter { it.isAviator && it.status != OperatorStatus.HQ_STAFF && !it.status.isOutOfActiveService }
            if (aviatorOps.isNotEmpty()) {
                add(PersonnelGroupFilter.Aviators to aviatorOps)
            }
            val unassignedOps = billetOrderedAll.filter {
                it.status != OperatorStatus.HQ_STAFF &&
                    !it.status.isOutOfActiveService &&
                    !it.isAviator &&
                    it.id !in assignedSquadIds
            }
            if (unassignedOps.isNotEmpty()) {
                add(PersonnelGroupFilter.FieldUnassigned(hasSquads = squads.isNotEmpty()) to unassignedOps)
            }
        }
    }

    val resolvedFilterPair = availableFilters.find { it.first.key == activeFilterKey }
        ?: availableFilters.first()
    val currentGroupFilter = resolvedFilterPair.first
    val currentGroupOps = resolvedFilterPair.second

    val indexInGroup = currentGroupOps.indexOfFirst { it.id == selectedOp.id }
    val displayPos = if (indexInGroup >= 0) indexInGroup + 1 else 1
    val canStep = currentGroupOps.size > 1
    val totalActionCount = remember(deedCountsByOp, boardReadyIds, billetOrderedAll) {
        billetOrderedAll.count { (deedCountsByOp[it.id] ?: 0) > 0 || it.id in boardReadyIds }
    }

    val assignmentSummary = remember(selectedOp, squads, deployedIds) {
        resolveOperatorAssignmentSummary(
            operator = selectedOp,
            squads = squads,
            isDeployed = selectedOp.id in deployedIds
        )
    }

    TacticalPanelCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = extra.borderSubtle
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Prev Button + Tappable Soldier Roster Selector + Next Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = {
                        if (currentGroupOps.isNotEmpty()) {
                            val prevIdx = if (indexInGroup <= 0) {
                                currentGroupOps.lastIndex
                            } else {
                                indexInGroup - 1
                            }
                            onSelectOperator(currentGroupOps[prevIdx].id)
                        }
                    },
                    enabled = canStep,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, extra.borderSubtle, RoundedCornerShape(10.dp))
                        .testTag("dress_prev_operator")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous soldier",
                        tint = if (canStep) extra.brassBright else extra.textMuted
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, extra.brass.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showDirectorySheet = true }
                        .testTag("dress_open_roster_sheet")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Item 74: the name shrinks with an ellipsis; the counter never gets squeezed
                                Text(
                                    text = "${selectedOp.rank.code} ${selectedOp.callsign}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = extra.brassBright,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Text(
                                    text = "$displayPos/${currentGroupOps.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.signalsCyan,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Text(
                                text = assignmentSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (totalActionCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(extra.brass)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$totalActionCount!",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondary
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Filled.ManageSearch,
                                contentDescription = "Open full personnel roster directory",
                                tint = extra.brassBright,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Roster",
                                style = MaterialTheme.typography.labelMedium,
                                color = extra.brassBright
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        if (currentGroupOps.isNotEmpty()) {
                            val nextIdx = if (indexInGroup < 0 || indexInGroup >= currentGroupOps.lastIndex) {
                                0
                            } else {
                                indexInGroup + 1
                            }
                            onSelectOperator(currentGroupOps[nextIdx].id)
                        }
                    },
                    enabled = canStep,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, extra.borderSubtle, RoundedCornerShape(10.dp))
                        .testTag("dress_next_operator")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next soldier",
                        tint = if (canStep) extra.brassBright else extra.textMuted
                    )
                }
            }

            // Quick Billet / Squad Filter Strip (only shown when multiple groups exist)
            if (availableFilters.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    availableFilters.forEach { (filter, groupOps) ->
                        val selected = currentGroupFilter.key == filter.key
                        val isActionChip = filter == PersonnelGroupFilter.ActionNeeded
                        FilterChip(
                            selected = selected,
                            onClick = {
                                activeFilterKey = filter.key
                                if (groupOps.none { it.id == selectedOp.id }) {
                                    groupOps.firstOrNull()?.let { onSelectOperator(it.id) }
                                }
                            },
                            label = {
                                Text(
                                    text = "${filter.label} (${groupOps.size})",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = if (isActionChip) {
                                FilterChipDefaults.filterChipColors(
                                    containerColor = extra.brassContainer.copy(alpha = 0.45f),
                                    labelColor = extra.brassBright,
                                    selectedContainerColor = extra.brass,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                                )
                            } else {
                                FilterChipDefaults.filterChipColors()
                            },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .testTag(filter.testTag)
                        )
                    }
                }
            }
        }
    }

    if (showDirectorySheet) {
        DressPersonnelDirectorySheet(
            organizedSections = organizedSections,
            allOperators = billetOrderedAll,
            selectedOpId = selectedOp.id,
            squads = squads,
            deployedIds = deployedIds,
            deedCountsByOp = deedCountsByOp,
            honorCountsByOp = honorCountsByOp,
            boardReadyIds = boardReadyIds,
            onSelectOperator = { id ->
                onSelectOperator(id)
                showDirectorySheet = false
            },
            onDismiss = { showDirectorySheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DressPersonnelDirectorySheet(
    organizedSections: List<OrganizedRosterSection>,
    allOperators: List<Operator>,
    selectedOpId: String,
    squads: List<TacticalSquad>,
    deployedIds: Set<String>,
    deedCountsByOp: Map<String, Int>,
    honorCountsByOp: Map<String, Int>,
    boardReadyIds: Set<String>,
    onSelectOperator: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val extra = LocalAegisColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(RosterSortMode.BY_BILLET) }
    var onlyNeedsAttention by rememberSaveable { mutableStateOf(false) }
    val promotableIds = remember(allOperators) { allOperators.filter { it.pinEligibleRank != null }.map { it.id }.toSet() }

    val normalizedQuery = searchQuery.trim().lowercase()
    fun matchesOperator(op: Operator): Boolean {
        if (onlyNeedsAttention && (deedCountsByOp[op.id] ?: 0) == 0 && op.id !in boardReadyIds) {
            return false
        }
        if (normalizedQuery.isEmpty()) return true
        val squadName = squads.find { op.id in it.memberIds }?.name?.lowercase().orEmpty()
        val hqCode = op.hqRole?.shortCode?.lowercase().orEmpty()
        val hqTitle = op.hqRole?.title?.lowercase().orEmpty()
        return op.callsign.lowercase().contains(normalizedQuery) ||
            op.name.lowercase().contains(normalizedQuery) ||
            op.rank.code.lowercase().contains(normalizedQuery) ||
            op.rank.title.lowercase().contains(normalizedQuery) ||
            op.rank.payGrade.lowercase().contains(normalizedQuery) ||
            op.mosLabel.lowercase().contains(normalizedQuery) ||
            op.mosTag.lowercase().contains(normalizedQuery) ||
            squadName.contains(normalizedQuery) ||
            hqCode.contains(normalizedQuery) ||
            hqTitle.contains(normalizedQuery)
    }

    val hasAnyAttention = remember(deedCountsByOp, boardReadyIds, allOperators) {
        allOperators.any { (deedCountsByOp[it.id] ?: 0) > 0 || it.id in boardReadyIds }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("dress_roster_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PERSONNEL ROSTER DIRECTORY (${allOperators.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = extra.brassBright
                    )
                    Text(
                        text = "Grouped by HQ Command Staff, Tactical Squads & Field Roster",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.textMuted
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("close_dress_roster_sheet")
                ) {
                    Text("Close")
                }
            }

            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = {
                    Text("Search callsign, rank, MOS, squad, or HQ billet...")
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = extra.signalsCyan
                    )
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                } else null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = extra.brassBright,
                    unfocusedBorderColor = extra.borderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dress_roster_search_input")
            )

            // Sort & Needs-Attention Filter Row
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RosterSortMode.entries.forEach { mode ->
                    FilterChip(
                        selected = sortMode == mode,
                        onClick = { sortMode = mode },
                        label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .testTag("dress_sort_${mode.name.lowercase()}")
                    )
                }
                if (hasAnyAttention) {
                    FilterChip(
                        selected = onlyNeedsAttention,
                        onClick = { onlyNeedsAttention = !onlyNeedsAttention },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.WorkspacePremium,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Awards / Board Ready", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = extra.brassContainer.copy(alpha = 0.35f),
                            labelColor = extra.brassBright,
                            selectedContainerColor = extra.brass,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .testTag("dress_filter_attention_only")
                    )
                }
            }

            HorizontalDivider(color = extra.borderSubtle)

            // Roster List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (sortMode == RosterSortMode.BY_BILLET) {
                    val filteredSections = organizedSections.mapNotNull { sec ->
                        val matching = sec.operators.filter(::matchesOperator)
                        if (matching.isNotEmpty()) sec.copy(operators = matching) else null
                    }
                    if (filteredSections.isEmpty()) {
                        item {
                            EmptyRosterSearchNotice()
                        }
                    } else {
                        filteredSections.forEach { section ->
                            item(key = "hdr_${section.id}") {
                                RosterSectionHeader(
                                    title = section.title,
                                    subtitle = section.subtitle,
                                    accentIsCommand = section.accentIsCommand
                                )
                            }
                            items(
                                items = section.operators,
                                key = { op -> "${section.id}_${op.id}" }
                            ) { op ->
                                PersonnelDirectoryRow(
                                    operator = op,
                                    isSelected = op.id == selectedOpId,
                                    assignmentText = resolveOperatorAssignmentSummary(
                                        operator = op,
                                        squads = squads,
                                        isDeployed = op.id in deployedIds
                                    ),
                                    isSquadLeader = squads.any { it.leaderId == op.id && op.status != OperatorStatus.HQ_STAFF },
                                    pendingDeedCount = deedCountsByOp[op.id] ?: 0,
                                    isBoardReady = op.id in boardReadyIds && op.id !in promotableIds,
                                    isPromotable = op.id in promotableIds,
                                    honorCount = honorCountsByOp[op.id] ?: 0,
                                    onClick = { onSelectOperator(op.id) }
                                )
                            }
                        }
                    }
                } else {
                    val flatSorted = allOperators
                        .filter(::matchesOperator)
                        .let { list ->
                            when (sortMode) {
                                RosterSortMode.BY_RANK -> list.sortedWith(
                                    compareByDescending<Operator> { it.rank.seniorityIndex }
                                        .thenByDescending { it.xp }
                                )
                                RosterSortMode.BY_HONORS -> list.sortedWith(
                                    compareByDescending<Operator> { honorCountsByOp[it.id] ?: 0 }
                                        .thenByDescending { it.rank.seniorityIndex }
                                )
                                RosterSortMode.BY_BILLET -> list
                            }
                        }
                    if (flatSorted.isEmpty()) {
                        item {
                            EmptyRosterSearchNotice()
                        }
                    } else {
                        items(items = flatSorted, key = { it.id }) { op ->
                            PersonnelDirectoryRow(
                                operator = op,
                                isSelected = op.id == selectedOpId,
                                assignmentText = resolveOperatorAssignmentSummary(
                                    operator = op,
                                    squads = squads,
                                    isDeployed = op.id in deployedIds
                                ),
                                isSquadLeader = squads.any { it.leaderId == op.id && op.status != OperatorStatus.HQ_STAFF },
                                pendingDeedCount = deedCountsByOp[op.id] ?: 0,
                                isBoardReady = op.id in boardReadyIds && op.id !in promotableIds,
                                isPromotable = op.id in promotableIds,
                                honorCount = honorCountsByOp[op.id] ?: 0,
                                onClick = { onSelectOperator(op.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RosterSectionHeader(
    title: String,
    subtitle: String?,
    accentIsCommand: Boolean
) {
    val extra = LocalAegisColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (accentIsCommand) extra.signalsCyan else extra.brassBright
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = extra.textMuted
            )
        }
    }
}

@Composable
private fun EmptyRosterSearchNotice() {
    val extra = LocalAegisColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No personnel match the current search or filter.",
            style = MaterialTheme.typography.bodyMedium,
            color = extra.textMuted
        )
    }
}

@Composable
private fun PersonnelDirectoryRow(
    operator: Operator,
    isSelected: Boolean,
    assignmentText: String,
    isSquadLeader: Boolean,
    pendingDeedCount: Int,
    isBoardReady: Boolean,
    honorCount: Int,
    onClick: () -> Unit,
    isPromotable: Boolean = false
) {
    val extra = LocalAegisColors.current
    val borderCol = when {
        isSelected -> extra.brassBright
        pendingDeedCount > 0 -> extra.brass.copy(alpha = 0.8f)
        isBoardReady || isPromotable -> extra.signalsCyan.copy(alpha = 0.7f)
        else -> extra.borderSubtle
    }
    val containerCol = if (isSelected) {
        extra.brassContainer.copy(alpha = 0.32f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerCol,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderCol),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("dress_select_${operator.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RankInsigniaBadge(rank = operator.rank)
                    Text(
                        text = operator.callsign,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) extra.brassBright else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isSquadLeader) {
                        Text(
                            text = "★ LDR",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.brassBright
                        )
                    }
                }
                Text(
                    text = "${operator.name} • $assignmentText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (pendingDeedCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = extra.brass
                        ) {
                            Text(
                                text = if (pendingDeedCount > 1) "$pendingDeedCount DEEDS" else "DEED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    // Session 33: promotable inside the tier; the player pins the rank
                    if (isPromotable) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = extra.signalsContainer,
                            border = BorderStroke(1.dp, extra.signalsCyan.copy(alpha = 0.7f))
                        ) {
                            Text(
                                text = "P",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = extra.signalsCyan,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp).testTag("promotable_${operator.id}")
                            )
                        }
                    }
                    if (isBoardReady) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = extra.signalsContainer,
                            border = BorderStroke(1.dp, extra.signalsCyan.copy(alpha = 0.7f))
                        ) {
                            Text(
                                text = "BOARD",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.signalsCyan,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "$honorCount Honors • ${operator.activeWardrobeKit.shortLabelFor(operator)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.textMuted,
                    maxLines = 1
                )
            }
        }
    }
}
