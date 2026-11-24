package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.catalog.GameCatalog
import com.example.domain.SquadCohesionRules
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import com.example.ui.theme.LocalAegisColors

private enum class SquadEditorTab(val label: String) { ROSTER("Roster"), UNIFORM("Uniform") }

/**
 * Squad editor: a Roster tab (members, leaders, name, cohesion-loss preview) and a Uniform tab
 * (division patch, live preview, and a Squad Standard switch per kit). Standards apply at render
 * time to whoever is in the squad; a soldier's own Tailor Studio pick still overrides them.
 */
@Composable
fun SquadEditorDialog(
    editingSquad: TacticalSquad?,
    allSquads: List<TacticalSquad>,
    fieldOperators: List<Operator>,
    onSave: (
        squadId: String?,
        name: String,
        memberIds: List<String>,
        leaderId: String?,
        teamLeaderId: String?,
        standard: SquadUniformStandard,
        specialForces: Boolean
    ) -> Unit,
    onDisband: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Item 119: how many SF squads the SF Compound allows. */
    sfSquadLimit: Int = 1,
    gearItems: List<GearItem> = emptyList(),
    citations: List<AwardedMedal> = emptyList()
) {
    val extra = LocalAegisColors.current
    val key = editingSquad?.id
    var tab by remember(key) { mutableStateOf(SquadEditorTab.ROSTER) }
    var squadName by remember(key) { mutableStateOf(editingSquad?.name ?: SquadCohesionRules.nextDefaultSquadName(allSquads)) }
    var memberIds by remember(key) { mutableStateOf(editingSquad?.memberIds ?: emptyList()) }
    var leaderId by remember(key) { mutableStateOf(editingSquad?.leaderId) }
    var teamLeaderId by remember(key) { mutableStateOf(editingSquad?.teamLeaderId) }
    // Item 58/87: normal or SF squad, chosen when forming it; only one SF squad at a time
    // Item 119: as many SF squads as the SF Compound allows (one without it)
    val otherSfSquads = allSquads.filter { it.isSpecialForces && it.id != key }
    val sfLockReason = if (otherSfSquads.size >= sfSquadLimit) {
        if (sfSquadLimit == 1) "${otherSfSquads.first().name} is the SF squad. Build the SF Compound for more."
        else "The SF Compound houses $sfSquadLimit SF squads at its level."
    } else null
    var specialForces by remember(key) { mutableStateOf(editingSquad?.isSpecialForces == true) }
    var standardPatch by remember(key) {
        mutableStateOf(editingSquad?.standardPatch?.takeIf { it.isDivision } ?: UnitPatchStyle.firstUnusedDivision(allSquads, excludingSquadId = key))
    }

    // Per-kit standards: a kit is On when its values are set
    var camo by remember(key) { mutableStateOf(editingSquad?.standardCamo) }
    var bravosBeret by remember(key) { mutableStateOf(editingSquad?.standardBravosHeadgear) }
    var bravosShirt by remember(key) { mutableStateOf(editingSquad?.standardBravosDressStyle) }
    var alphasCoat by remember(key) { mutableStateOf(editingSquad?.standardDressStyle) }
    var alphasCap by remember(key) { mutableStateOf(editingSquad?.standardHeadgear) }
    var alphasGloves by remember(key) { mutableStateOf(editingSquad?.standardAlphasGloves) }
    var messJacket by remember(key) { mutableStateOf(editingSquad?.standardMessDressStyle) }
    var messCap by remember(key) { mutableStateOf(editingSquad?.standardMessHeadgear) }
    var messGloves by remember(key) { mutableStateOf(editingSquad?.standardMessGloves) }
    var fieldOn by remember(key) { mutableStateOf(camo != null) }
    var bravosOn by remember(key) { mutableStateOf(bravosBeret != null || bravosShirt != null) }
    var alphasOn by remember(key) { mutableStateOf(alphasCoat != null || alphasCap != null || alphasGloves != null) }
    var messOn by remember(key) { mutableStateOf(messJacket != null || messCap != null || messGloves != null) }

    val opsById = remember(fieldOperators) { fieldOperators.associateBy { it.id } }
    val members = memberIds.mapNotNull { opsById[it] }
    val otherSquads = allSquads.filter { it.id != key }
    val ownedById = remember(citations) { citations.groupBy { it.operatorId }.mapValues { (_, c) -> c.map { it.medalId }.toSet() } }
    fun owned(op: Operator) = ownedById[op.id].orEmpty()

    fun buildStandard() = SquadUniformStandard(
        patch = standardPatch,
        camo = camo.takeIf { fieldOn },
        bravosHeadgear = bravosBeret.takeIf { bravosOn },
        bravosDressStyle = bravosShirt.takeIf { bravosOn },
        alphasHeadgear = alphasCap.takeIf { alphasOn },
        alphasDressStyle = alphasCoat.takeIf { alphasOn },
        messDressStyle = messJacket.takeIf { messOn },
        messHeadgear = messCap.takeIf { messOn },
        alphasGloves = alphasGloves.takeIf { alphasOn },
        messGloves = messGloves.takeIf { messOn }
    )

    // The squad as it would be saved, for the live preview and compliance flags
    val draftSquad = run {
        val std = buildStandard()
        (editingSquad ?: TacticalSquad(id = "sq_draft", name = squadName, memberIds = memberIds, leaderId = leaderId)).copy(
            name = squadName,
            memberIds = memberIds,
            leaderId = leaderId,
            teamLeaderId = teamLeaderId,
            standardPatch = std.patch,
            standardCamo = std.camo,
            standardBravosHeadgear = std.bravosHeadgear,
            standardBravosDressStyle = std.bravosDressStyle,
            standardHeadgear = std.alphasHeadgear,
            standardDressStyle = std.alphasDressStyle,
            standardMessDressStyle = std.messDressStyle,
            standardMessHeadgear = std.messHeadgear,
            standardAlphasGloves = std.alphasGloves,
            standardMessGloves = std.messGloves,
            isSpecialForces = specialForces
        )
    }
    val previewSquads = otherSquads + draftSquad

    /** Copies the squad leader's current look for [kit] into the standard. */
    fun matchLeader(kit: WardrobeKit) {
        val leader = leaderId?.let { opsById[it] } ?: members.firstOrNull() ?: return
        val look = TacticalCalculator.resolveKitAppearance(leader, kit, otherSquads, owned(leader))
        when (kit) {
            WardrobeKit.FIELD -> camo = look.camoPattern
            WardrobeKit.BRAVOS -> { bravosBeret = look.headgearStyle; bravosShirt = look.dressStyle }
            WardrobeKit.ALPHAS -> { alphasCoat = look.dressStyle; alphasCap = look.headgearStyle; alphasGloves = look.hasWhiteGloves }
            WardrobeKit.MESS -> { messJacket = look.dressStyle; messCap = look.headgearStyle; messGloves = look.hasWhiteGloves }
        }
    }

    BackHandler { onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = { Icon(Icons.Filled.Groups, contentDescription = null, tint = extra.brassBright) },
        title = {
            Text(
                text = if (editingSquad != null) "Manage ${editingSquad.name}" else "Form Combat Squad",
                style = MaterialTheme.typography.titleLarge,
                color = extra.brassBright
            )
        },
        text = {
            Column(modifier = Modifier.testTag("squad_editor_dialog")) {
                TabRow(selectedTabIndex = tab.ordinal, containerColor = Color.Transparent) {
                    SquadEditorTab.entries.forEach { t ->
                        Tab(
                            selected = tab == t,
                            onClick = { tab = t },
                            text = { Text(t.label) },
                            modifier = Modifier.testTag("squad_editor_tab_${t.name.lowercase()}")
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (tab) {
                        SquadEditorTab.ROSTER -> RosterTab(
                            editingSquad = editingSquad,
                            squadName = squadName,
                            onNameChange = { squadName = it.take(24) },
                            members = members,
                            memberIds = memberIds,
                            leaderId = leaderId,
                            teamLeaderId = teamLeaderId,
                            fieldOperators = fieldOperators,
                            otherSquads = otherSquads,
                            specialForces = specialForces,
                            sfLockedBy = sfLockReason,
                            onSpecialForcesChange = { sf ->
                                specialForces = sf
                                // Item 146: an auto-named squad follows the toggle (Echo Squad <-> ODA 1)
                                if (SquadCohesionRules.isDefaultSquadName(squadName)) {
                                    squadName = SquadCohesionRules.nextDefaultSquadName(allSquads.filter { it.id != editingSquad?.id }, sf)
                                }
                                // An SF squad keeps SF-qualified soldiers only
                                if (sf) memberIds = memberIds.filter { id -> fieldOperators.find { it.id == id }?.let(TacticalCalculator::isSfQualified) == true }
                            },
                            onMembersChange = { next ->
                                memberIds = next
                                if (leaderId !in next) leaderId = SquadCohesionRules.pickDefaultLeaderId(next, fieldOperators)
                                if (teamLeaderId !in next || next.size < TEAM_LEADER_MIN_SQUAD_SIZE) teamLeaderId = null
                            },
                            onLeaderChange = { id ->
                                leaderId = id
                                if (teamLeaderId == id) teamLeaderId = null
                            },
                            onTeamLeaderChange = { teamLeaderId = it }
                        )
                        SquadEditorTab.UNIFORM -> UniformTab(
                            members = members,
                            previewSquads = previewSquads,
                            gearItems = gearItems,
                            owned = ::owned,
                            standardPatch = standardPatch,
                            onPatchChange = { standardPatch = it },
                            specialForces = specialForces,
                            kitControls = { kit ->
                                when (kit) {
                                    WardrobeKit.FIELD -> KitStandardSection(kit, fieldOn, {
                                        fieldOn = it; if (it && camo == null) matchLeader(kit)
                                    }, { matchLeader(kit) }) {
                                        CamoStandardDropdown(camo) { camo = it }
                                    }
                                    WardrobeKit.BRAVOS -> KitStandardSection(kit, bravosOn, {
                                        bravosOn = it; if (it && (bravosBeret == null || bravosShirt == null)) matchLeader(kit)
                                    }, { matchLeader(kit) }) {
                                        HeadgearStandardDropdown("Beret", true, bravosBeret, "squad_standard_bravos_headgear_dropdown") { bravosBeret = it }
                                        DressStandardDropdown("Shirt", false, bravosShirt, "squad_standard_bravos_dress_dropdown") { bravosShirt = it }
                                    }
                                    WardrobeKit.ALPHAS -> KitStandardSection(kit, alphasOn, {
                                        alphasOn = it; if (it && (alphasCoat == null || alphasCap == null || alphasGloves == null)) matchLeader(kit)
                                    }, { matchLeader(kit) }) {
                                        DressStandardDropdown("Coat", false, alphasCoat, "squad_standard_dress_dropdown") { alphasCoat = it }
                                        HeadgearStandardDropdown("Cap", false, alphasCap, "squad_standard_headgear_dropdown") { alphasCap = it }
                                        GlovesStandardDropdown(alphasGloves, "squad_standard_alphas_gloves_dropdown") { alphasGloves = it }
                                    }
                                    WardrobeKit.MESS -> KitStandardSection(kit, messOn, {
                                        messOn = it; if (it && (messJacket == null || messCap == null || messGloves == null)) matchLeader(kit)
                                    }, { matchLeader(kit) }) {
                                        DressStandardDropdown("Jacket", true, messJacket, "squad_standard_mess_dress_dropdown") { messJacket = it }
                                        HeadgearStandardDropdown("Cap", false, messCap, "squad_standard_mess_headgear_dropdown") { messCap = it }
                                        GlovesStandardDropdown(messGloves, "squad_standard_mess_gloves_dropdown") { messGloves = it }
                                    }
                                }
                            }
                        )
                    }

                    if (editingSquad != null) {
                        HorizontalDivider(color = extra.borderSubtle)
                        OutlinedButton(
                            onClick = { onDisband(editingSquad.id); onDismiss() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("disband_squad_button")
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Disband ${editingSquad.name}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(key, squadName, memberIds, leaderId, teamLeaderId, buildStandard(), specialForces)
                    onDismiss()
                },
                enabled = memberIds.size >= MIN_SQUAD_SIZE,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("save_squad_button")
            ) { Text("Save Squad") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("cancel_squad_dialog")) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RosterTab(
    editingSquad: TacticalSquad?,
    squadName: String,
    onNameChange: (String) -> Unit,
    members: List<Operator>,
    memberIds: List<String>,
    leaderId: String?,
    teamLeaderId: String?,
    fieldOperators: List<Operator>,
    otherSquads: List<TacticalSquad>,
    specialForces: Boolean,
    sfLockedBy: String?,
    onSpecialForcesChange: (Boolean) -> Unit,
    onMembersChange: (List<String>) -> Unit,
    onLeaderChange: (String) -> Unit,
    onTeamLeaderChange: (String?) -> Unit
) {
    val extra = LocalAegisColors.current
    OutlinedTextField(
        value = squadName,
        onValueChange = onNameChange,
        label = { Text("Squad Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("squad_name_input")
    )

    SquadTypeSelector(specialForces = specialForces, sfLockedBy = sfLockedBy, onChange = onSpecialForcesChange)

    // Cohesion lost to turnover, shown before the player confirms
    if (editingSquad != null && (memberIds != editingSquad.memberIds || leaderId != editingSquad.leaderId)) {
        val afterXp = SquadCohesionRules.cohesionAfterRosterChange(editingSquad, memberIds, leaderId)
        val before = editingSquad.cohesionScore
        val after = (afterXp / 10).coerceIn(0, 100)
        Surface(
            shape = MaterialTheme.shapes.small,
            color = extra.amberTelemetry.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, extra.amberTelemetry.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth().testTag("cohesion_loss_preview")
        ) {
            Text(
                text = if (after < before) {
                    "Cohesion $before% → $after% (−${before - after}%) from roster turnover"
                } else {
                    "Cohesion stays at $before%"
                },
                style = MaterialTheme.typography.labelMedium,
                color = extra.amberTelemetry,
                modifier = Modifier.padding(10.dp)
            )
        }
    }

    Text(
        text = "SQUAD ROSTER (${members.size}/$MAX_SQUAD_SIZE) • $MIN_SQUAD_SIZE MIN • FULL STRENGTH AT ${if (specialForces) MAX_SQUAD_SIZE else FULL_STRENGTH_SQUAD_SIZE}",
        style = MaterialTheme.typography.labelSmall,
        color = extra.textMuted
    )
    if (members.isEmpty()) {
        Text(
            text = "Add $MIN_SQUAD_SIZE to $MAX_SQUAD_SIZE field soldiers below. A squad of $TEAM_LEADER_MIN_SQUAD_SIZE+ can also name a Team Leader.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    val draft = TacticalSquad("sq_check", "", memberIds, leaderId)
    members.forEach { member ->
        val isLeader = member.id == leaderId
        val isTeamLeader = member.id == teamLeaderId
        val tlBlocker = SquadCohesionRules.teamLeaderBlocker(draft, member)
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (isLeader) extra.brassContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, if (isLeader) extra.brass else if (isTeamLeader) extra.signalsCyan else extra.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    NameWithSfMarker(
                        text = "${member.rank.code} ${member.callsign}",
                        sfQualified = TacticalCalculator.isSfQualified(member),
                            sfTier = TacticalCalculator.sfTier(member),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isLeader) extra.brassBright else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            isLeader -> "★ Squad Leader • ${member.mosLabel}"
                            isTeamLeader -> "Team Leader • ${member.mosLabel}"
                            else -> member.mosLabel
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLeader) extra.brass else if (isTeamLeader) extra.signalsCyan else extra.textMuted
                    )
                }
                IconButton(
                    onClick = { onLeaderChange(member.id) },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).testTag("set_leader_${member.id}")
                ) {
                    Icon(
                        imageVector = if (isLeader) Icons.Filled.Star else Icons.Filled.StarOutline,
                        contentDescription = "Make ${member.callsign} Squad Leader",
                        tint = if (isLeader) extra.brassBright else extra.textMuted
                    )
                }
                if (members.size >= TEAM_LEADER_MIN_SQUAD_SIZE && !isLeader) {
                    IconButton(
                        onClick = { onTeamLeaderChange(if (isTeamLeader) null else member.id) },
                        enabled = isTeamLeader || tlBlocker == null,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).testTag("set_team_leader_${member.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = if (tlBlocker != null) "Team Leader: $tlBlocker" else "Make ${member.callsign} Team Leader",
                            tint = when {
                                isTeamLeader -> extra.signalsCyan
                                tlBlocker == null -> extra.textMuted
                                else -> extra.textMuted.copy(alpha = 0.3f)
                            }
                        )
                    }
                }
                IconButton(
                    onClick = { onMembersChange(memberIds.filter { it != member.id }) },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).testTag("remove_squad_member_${member.id}")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove ${member.callsign}", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (members.size >= TEAM_LEADER_MIN_SQUAD_SIZE) {
        Text(
            text = "Team Leader: SGT or above, not the Squad Leader. Tap the chevron to assign.",
            style = MaterialTheme.typography.labelSmall,
            color = extra.textMuted
        )
    }

    val otherSquadByMember = otherSquads.flatMap { sq -> sq.memberIds.map { it to sq.name } }.toMap()
    val candidates = fieldOperators.filter { it.id !in memberIds && (!specialForces || TacticalCalculator.isSfQualified(it)) }
    if (candidates.isNotEmpty()) {
        HorizontalDivider(color = extra.borderSubtle)
        val full = members.size >= MAX_SQUAD_SIZE
        Text(
            text = if (full) "SQUAD FULL ($MAX_SQUAD_SIZE/$MAX_SQUAD_SIZE)" else "ASSIGN FIELD SOLDIERS",
            style = MaterialTheme.typography.labelSmall,
            color = extra.textMuted
        )
        candidates.forEach { candidate ->
            val existing = otherSquadByMember[candidate.id]
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, extra.borderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = !full) { onMembersChange((memberIds + candidate.id).take(MAX_SQUAD_SIZE)) }
                    .testTag("add_squad_member_${candidate.id}")
            ) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        NameWithSfMarker(
                            text = "${candidate.rank.code} ${candidate.callsign} • ${candidate.mosTag}",
                            sfQualified = TacticalCalculator.isSfQualified(candidate),
                            sfTier = TacticalCalculator.sfTier(candidate),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (!full) MaterialTheme.colorScheme.onSurface else extra.textMuted
                        )
                        Text(
                            text = if (existing != null) "Currently in $existing (will transfer)" else "${candidate.mosLabel} • Unassigned",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (existing != null) extra.amberTelemetry else extra.textMuted
                        )
                    }
                    Icon(Icons.Filled.AddCircleOutline, contentDescription = "Add ${candidate.callsign}", tint = if (!full) extra.signalsCyan else extra.textMuted)
                }
            }
        }
    }
}

@Composable
private fun UniformTab(
    members: List<Operator>,
    previewSquads: List<TacticalSquad>,
    gearItems: List<GearItem>,
    owned: (Operator) -> Set<String>,
    standardPatch: UnitPatchStyle,
    onPatchChange: (UnitPatchStyle) -> Unit,
    specialForces: Boolean = false,
    kitControls: @Composable (WardrobeKit) -> Unit
) {
    val extra = LocalAegisColors.current
    var kit by remember { mutableStateOf(WardrobeKit.FIELD) }
    var previewIndex by remember { mutableIntStateOf(0) }

    val patchOptions = remember {
        UnitPatchStyle.DIVISIONS.map { patch ->
            DropdownOptionItem(patch, patch.label, patch.subtitle, Color(patch.primaryHex), Color(patch.accentHex))
        }
    }
    if (specialForces) {
        Text(
            "Unit patch: SF arrowhead in this team's colors (ODA 1 red, ODA 2 green, ODA 3 gold; subdued on field uniforms)",
            style = MaterialTheme.typography.labelMedium,
            color = Color(SF_PATCH_RED_BRIGHT)
        )
    } else SingleChoiceDropdown(
        label = "Division Patch (left shoulder)",
        selectedText = standardPatch.label,
        selectedPrimaryColor = Color(standardPatch.primaryHex),
        selectedTrimColor = Color(standardPatch.accentHex),
        options = patchOptions,
        selectedValue = standardPatch,
        onSelect = onPatchChange,
        fieldTestTag = "squad_standard_patch_dropdown"
    )

    TabRow(selectedTabIndex = kit.ordinal, containerColor = Color.Transparent) {
        WardrobeKit.entries.forEach { k ->
            Tab(
                selected = kit == k,
                onClick = { kit = k },
                text = { Text(k.shortLabel, maxLines = 1) },
                modifier = Modifier.testTag("squad_kit_tab_${k.name.lowercase()}")
            )
        }
    }

    // Live preview of the selected kit on one member at a time
    if (members.isNotEmpty()) {
        val idx = previewIndex.coerceIn(0, members.lastIndex)
        val member = members[idx]
        val medals = GameCatalog.medals.filter { it.id in owned(member) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = { previewIndex = (idx - 1 + members.size) % members.size },
                modifier = Modifier.testTag("squad_preview_prev")
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous member") }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                OperatorUniformPreview(
                    operator = member,
                    equippedGear = gearItems.equippedBy(member),
                    awardedMedals = medals,
                    squads = previewSquads,
                    kitOverride = kit,
                    modifier = Modifier.size(180.dp, 250.dp).testTag("squad_uniform_preview")
                )
                Text(
                    text = "${member.rank.code} ${member.callsign} (${idx + 1}/${members.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = extra.textMuted
                )
            }
            IconButton(
                onClick = { previewIndex = (idx + 1) % members.size },
                modifier = Modifier.testTag("squad_preview_next")
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next member") }
        }
    }

    kitControls(kit)

    // Compliance: members who can't wear part of the standard, and what they wear instead
    val flags = members.flatMap { m ->
        TacticalCalculator.squadStandardFallbacks(m, previewSquads, kit, owned(m)).map { "${m.callsign} • $it" }
    }
    flags.forEach { flag ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("squad_compliance_flag")) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = extra.amberTelemetry, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(flag, style = MaterialTheme.typography.labelSmall, color = extra.amberTelemetry, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun KitStandardSection(
    kit: WardrobeKit,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    onMatchLeader: () -> Unit,
    controls: @Composable ColumnScope.() -> Unit
) {
    val extra = LocalAegisColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${kit.label}: Squad Standard ${if (on) "On" else "Off"}", style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (on) "Members wear this unless they set their own in the Tailor Studio" else "Members dress individually",
                style = MaterialTheme.typography.labelSmall,
                color = extra.textMuted
            )
        }
        Switch(checked = on, onCheckedChange = onToggle, modifier = Modifier.testTag("squad_standard_switch_${kit.name.lowercase()}"))
    }
    if (on) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            controls()
            OutlinedButton(
                onClick = onMatchLeader,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).testTag("squad_match_leader_${kit.name.lowercase()}")
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Match Leader")
            }
        }
    }
}

@Composable
private fun CamoStandardDropdown(value: CamoPattern?, onSelect: (CamoPattern) -> Unit) {
    val options = remember { CamoPattern.SELECTABLE.map { DropdownOptionItem<CamoPattern?>(it, it.label, null, Color(it.primaryHex), Color(it.accentHex)) } }
    SingleChoiceDropdown(
        label = "Camo Pattern",
        selectedText = value?.label ?: "Choose",
        selectedPrimaryColor = value?.let { Color(it.primaryHex) },
        selectedTrimColor = value?.let { Color(it.accentHex) },
        options = options,
        selectedValue = value,
        onSelect = { it?.let(onSelect) },
        fieldTestTag = "squad_standard_camo_dropdown"
    )
}

@Composable
private fun HeadgearStandardDropdown(label: String, berets: Boolean, value: HeadgearStyle?, tag: String, onSelect: (HeadgearStyle) -> Unit) {
    val extra = LocalAegisColors.current
    val options = remember(berets) {
        HeadgearStyle.entries.filter { (it.kind == HeadgearKind.BERET) == berets }
            .map { DropdownOptionItem<HeadgearStyle?>(it, it.label, it.requirementHint, Color(it.colorHex), extra.brassBright) }
    }
    SingleChoiceDropdown(
        label = label,
        selectedText = value?.label ?: "Choose",
        selectedPrimaryColor = value?.let { Color(it.colorHex) },
        selectedTrimColor = extra.brassBright,
        options = options,
        selectedValue = value,
        onSelect = { it?.let(onSelect) },
        fieldTestTag = tag
    )
}

@Composable
private fun DressStandardDropdown(label: String, mess: Boolean, value: DressUniformStyle?, tag: String, onSelect: (DressUniformStyle) -> Unit) {
    val options = remember(mess) {
        DressUniformStyle.entries.filter { it.isCutawayMess == mess }
            .map { DropdownOptionItem<DressUniformStyle?>(it, it.label, it.requirementHint, Color(it.tunicHex), Color(it.trimHex)) }
    }
    SingleChoiceDropdown(
        label = label,
        selectedText = value?.label ?: "Choose",
        selectedPrimaryColor = value?.let { Color(it.tunicHex) },
        selectedTrimColor = value?.let { Color(it.trimHex) },
        options = options,
        selectedValue = value,
        onSelect = { it?.let(onSelect) },
        fieldTestTag = tag
    )
}

@Composable
private fun GlovesStandardDropdown(value: Boolean?, tag: String, onSelect: (Boolean) -> Unit) {
    val options = remember {
        listOf(
            DropdownOptionItem<Boolean?>(true, "White Gloves", "White parade gloves for the whole squad"),
            DropdownOptionItem<Boolean?>(false, "No Gloves", "Bare hands for the whole squad")
        )
    }
    SingleChoiceDropdown(
        label = "Gloves",
        selectedText = when (value) { true -> "White Gloves"; false -> "No Gloves"; null -> "Choose" },
        options = options,
        selectedValue = value,
        onSelect = { it?.let(onSelect) },
        fieldTestTag = tag
    )
}

/** Item 58/68/87: Normal or Special Forces squad, with the SF option clearly marked. */
@Composable
private fun SquadTypeSelector(specialForces: Boolean, sfLockedBy: String?, onChange: (Boolean) -> Unit) {
    val extra = LocalAegisColors.current
    Text("SQUAD TYPE", style = MaterialTheme.typography.labelSmall, color = extra.textMuted)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !specialForces,
            onClick = { onChange(false) },
            label = { Text("Normal Squad") },
            modifier = Modifier.weight(1f).testTag("squad_type_normal")
        )
        FilterChip(
            selected = specialForces,
            onClick = { onChange(true) },
            enabled = sfLockedBy == null || specialForces,
            leadingIcon = { SfMarker() },
            label = { Text("Special Forces") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(SF_PATCH_BLACK),
                selectedLabelColor = Color(SF_PATCH_RED_BRIGHT)
            ),
            modifier = Modifier.weight(1f).testTag("squad_type_sf")
        )
    }
    Text(
        text = when {
            sfLockedBy != null && !specialForces -> sfLockedBy
            specialForces -> "SF-qualified soldiers only, up to $MAX_SQUAD_SIZE. Sterile field uniforms and the SF arrowhead patch."
            else -> "Any field soldiers, $MIN_SQUAD_SIZE to $MAX_SQUAD_SIZE."
        },
        style = MaterialTheme.typography.labelSmall,
        color = if (specialForces) Color(SF_PATCH_RED_BRIGHT) else extra.textMuted
    )
}
