package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.SquadCohesionRules
import com.example.domain.model.*
import com.example.ui.theme.LocalAegisColors

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
        standardPatch: UnitPatchStyle,
        standardCamo: CamoPattern?,
        standardHeadgear: HeadgearStyle?,
        standardDressStyle: DressUniformStyle?
    ) -> Unit,
    onSaveWithStandard: ((
        squadId: String?,
        name: String,
        memberIds: List<String>,
        leaderId: String?,
        standard: SquadUniformStandard,
        applyImmediately: Boolean
    ) -> Unit)? = null,
    onDisband: (String) -> Unit,
    onDismiss: () -> Unit,
    onApplyStandard: ((String) -> Unit)? = null
) {
    val extra = LocalAegisColors.current
    var squadName by remember(editingSquad?.id) {
        mutableStateOf(
            editingSquad?.name ?: SquadCohesionRules.nextDefaultSquadName(allSquads)
        )
    }
    var memberIds by remember(editingSquad?.id) {
        mutableStateOf(editingSquad?.memberIds ?: emptyList())
    }
    var leaderId by remember(editingSquad?.id) {
        mutableStateOf(editingSquad?.leaderId)
    }
    var standardPatch by remember(editingSquad?.id) {
        mutableStateOf(editingSquad?.standardPatch ?: UnitPatchStyle.AUTO_MOS)
    }
    var standardCamo by remember(editingSquad?.id) {
        mutableStateOf(editingSquad?.standardCamo)
    }
    var standardBravosHeadgear by remember(editingSquad?.id) {
        mutableStateOf(editingSquad?.standardBravosHeadgear?.takeIf { it.kind == HeadgearKind.BERET })
    }
    var standardBravosDressStyle by remember(editingSquad?.id) {
        mutableStateOf(editingSquad?.standardBravosDressStyle?.takeIf { !it.isCutawayMess })
    }
    var standardAlphasHeadgear by remember(editingSquad?.id) {
        mutableStateOf(editingSquad?.standardHeadgear?.takeIf { it.kind != HeadgearKind.BERET })
    }
    var standardAlphasDressStyle by remember(editingSquad?.id) {
        mutableStateOf(editingSquad?.standardDressStyle?.takeIf { !it.isCutawayMess })
    }
    var standardMessDressStyle by remember(editingSquad?.id) {
        mutableStateOf(editingSquad?.standardMessDressStyle?.takeIf { it.isCutawayMess })
    }
    var standardMessHeadgear by remember(editingSquad?.id) {
        mutableStateOf(editingSquad?.standardMessHeadgear?.takeIf { it.kind != HeadgearKind.BERET })
    }
    var standardWhiteGloves by remember(editingSquad?.id) {
        mutableStateOf(editingSquad?.standardWhiteGloves)
    }

    fun buildStandard(): SquadUniformStandard = SquadUniformStandard(
        patch = standardPatch,
        camo = standardCamo,
        bravosHeadgear = standardBravosHeadgear,
        bravosDressStyle = standardBravosDressStyle,
        alphasHeadgear = standardAlphasHeadgear,
        alphasDressStyle = standardAlphasDressStyle,
        messDressStyle = standardMessDressStyle,
        messHeadgear = standardMessHeadgear,
        whiteGloves = standardWhiteGloves
    )

    val opsById = remember(fieldOperators) { fieldOperators.associateBy { it.id } }
    val otherSquadByMember = remember(allSquads, editingSquad?.id) {
        buildMap {
            allSquads.filter { it.id != editingSquad?.id }.forEach { sq ->
                sq.memberIds.forEach { put(it, sq.name) }
            }
        }
    }
    val currentMembers = memberIds.mapNotNull { opsById[it] }
    val availableCandidates = fieldOperators.filter { it.id !in memberIds }

    BackHandler { onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(Icons.Filled.Groups, contentDescription = null, tint = extra.brassBright)
        },
        title = {
            Text(
                text = if (editingSquad != null) "Manage ${editingSquad.name}" else "Form Combat Squad",
                style = MaterialTheme.typography.titleLarge,
                color = extra.brassBright
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag("squad_editor_dialog"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = squadName,
                    onValueChange = { squadName = it.take(24) },
                    label = { Text("Squad Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("squad_name_input")
                )

                Text(
                    text = "SQUAD ROSTER (${currentMembers.size}/$MAX_SQUAD_SIZE) • DESIGNATE LEADER",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.textMuted
                )

                if (currentMembers.isEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, extra.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Add up to $MAX_SQUAD_SIZE field soldiers below. First or chosen soldier can be set as Squad Leader.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else {
                    currentMembers.forEach { member ->
                        val isLeader = member.id == leaderId
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (isLeader) {
                                extra.brassContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isLeader) extra.brass else extra.borderSubtle
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${member.rank.code} ${member.callsign}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isLeader) extra.brassBright else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isLeader) "★ Designated Squad Leader • ${member.specialty.label}" else member.specialty.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isLeader) extra.brass else extra.textMuted
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { leaderId = member.id },
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                            .testTag("set_leader_${member.id}")
                                    ) {
                                        Icon(
                                            imageVector = if (isLeader) Icons.Filled.Star else Icons.Filled.StarOutline,
                                            contentDescription = "Designate ${member.callsign} as Squad Leader",
                                            tint = if (isLeader) extra.brassBright else extra.textMuted
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val nextMembers = memberIds.filter { it != member.id }
                                            memberIds = nextMembers
                                            if (leaderId == member.id) {
                                                leaderId = SquadCohesionRules.pickDefaultLeaderId(nextMembers, fieldOperators)
                                            }
                                        },
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                            .testTag("remove_squad_member_${member.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove ${member.callsign} from squad",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (availableCandidates.isNotEmpty()) {
                    HorizontalDivider(color = extra.borderSubtle)
                    Text(
                        text = if (currentMembers.size >= MAX_SQUAD_SIZE) {
                            "SQUAD FULL ($MAX_SQUAD_SIZE/$MAX_SQUAD_SIZE MAX)"
                        } else {
                            "ASSIGN FIELD SOLDIERS"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.textMuted
                    )
                    availableCandidates.forEach { candidate ->
                        val existingSquadName = otherSquadByMember[candidate.id]
                        val canAdd = currentMembers.size < MAX_SQUAD_SIZE
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, extra.borderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable(enabled = canAdd) {
                                    val nextMembers = (memberIds + candidate.id).take(MAX_SQUAD_SIZE)
                                    memberIds = nextMembers
                                    if (leaderId == null || leaderId !in nextMembers) {
                                        leaderId = candidate.id
                                    }
                                }
                                .testTag("add_squad_member_${candidate.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${candidate.rank.code} ${candidate.callsign} • ${candidate.specialty.roleTag}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (canAdd) MaterialTheme.colorScheme.onSurface else extra.textMuted
                                    )
                                    Text(
                                        text = if (existingSquadName != null) {
                                            "Currently in $existingSquadName (will transfer)"
                                        } else {
                                            "${candidate.specialty.label} • Unassigned"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (existingSquadName != null) extra.amberTelemetry else extra.textMuted
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.AddCircleOutline,
                                    contentDescription = "Add ${candidate.callsign}",
                                    tint = if (canAdd) extra.signalsCyan else extra.textMuted
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = extra.borderSubtle)
                Text(
                    text = "SQUAD UNIFORM STANDARD (4-KIT)",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.brassBright
                )
                Text(
                    text = "Set shared Field, Bravos, Alphas & Mess looks while preserving individual honors & rank gates.",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.textMuted
                )

                val patchOptions = remember {
                    UnitPatchStyle.entries.map { patch ->
                        DropdownOptionItem(
                            value = patch,
                            title = if (patch == UnitPatchStyle.AUTO_MOS) "Auto (Individual MOS Patch)" else patch.label,
                            subtitle = patch.subtitle,
                            primaryColor = Color(patch.primaryHex),
                            trimColor = Color(patch.accentHex)
                        )
                    }
                }
                SingleChoiceDropdown(
                    label = "Squad Unit Patch (SSI)",
                    selectedText = if (standardPatch == UnitPatchStyle.AUTO_MOS) "Auto (Individual MOS Patch)" else standardPatch.label,
                    selectedPrimaryColor = Color(standardPatch.primaryHex),
                    selectedTrimColor = Color(standardPatch.accentHex),
                    options = patchOptions,
                    selectedValue = standardPatch,
                    onSelect = { standardPatch = it },
                    fieldTestTag = "squad_standard_patch_dropdown"
                )

                val camoOptions = remember {
                    listOf(
                        DropdownOptionItem<CamoPattern?>(
                            value = null,
                            title = "Keep Individual Camo",
                            subtitle = "Do not override soldier's Field camo"
                        )
                    ) + CamoPattern.entries.map { camo ->
                        DropdownOptionItem<CamoPattern?>(
                            value = camo,
                            title = camo.label,
                            primaryColor = Color(camo.primaryHex),
                            trimColor = Color(camo.accentHex)
                        )
                    }
                }
                SingleChoiceDropdown(
                    label = "Field • Camo Pattern",
                    selectedText = standardCamo?.label ?: "Keep Individual Camo",
                    selectedPrimaryColor = standardCamo?.let { Color(it.primaryHex) },
                    selectedTrimColor = standardCamo?.let { Color(it.accentHex) },
                    options = camoOptions,
                    selectedValue = standardCamo,
                    onSelect = { standardCamo = it },
                    fieldTestTag = "squad_standard_camo_dropdown"
                )

                val beretOptions = remember {
                    listOf(
                        DropdownOptionItem<HeadgearStyle?>(
                            value = null,
                            title = "Keep Individual Beret",
                            subtitle = "Respect each soldier's Bravos beret"
                        )
                    ) + HeadgearStyle.entries.filter { it.kind == HeadgearKind.BERET }.map { hg ->
                        DropdownOptionItem<HeadgearStyle?>(
                            value = hg,
                            title = hg.label,
                            subtitle = hg.requirementHint,
                            primaryColor = Color(hg.colorHex),
                            trimColor = extra.brassBright
                        )
                    }
                }
                SingleChoiceDropdown(
                    label = "Bravos • Beret Standard",
                    selectedText = standardBravosHeadgear?.label ?: "Keep Individual Beret",
                    selectedPrimaryColor = standardBravosHeadgear?.let { Color(it.colorHex) },
                    selectedTrimColor = standardBravosHeadgear?.let { extra.brassBright },
                    options = beretOptions,
                    selectedValue = standardBravosHeadgear,
                    onSelect = { standardBravosHeadgear = it },
                    fieldTestTag = "squad_standard_bravos_headgear_dropdown"
                )

                val serviceDressOptions = remember {
                    listOf(
                        DropdownOptionItem<DressUniformStyle?>(
                            value = null,
                            title = "Keep Individual Cut",
                            subtitle = "Respect each soldier's tailored service uniform"
                        )
                    ) + DressUniformStyle.entries.filter { !it.isCutawayMess }.map { ds ->
                        DropdownOptionItem<DressUniformStyle?>(
                            value = ds,
                            title = ds.label,
                            subtitle = ds.requirementHint,
                            primaryColor = Color(ds.tunicHex),
                            trimColor = Color(ds.trimHex)
                        )
                    }
                }
                SingleChoiceDropdown(
                    label = "Bravos • Shirt / Service Standard",
                    selectedText = standardBravosDressStyle?.label ?: "Keep Individual Cut",
                    selectedPrimaryColor = standardBravosDressStyle?.let { Color(it.tunicHex) },
                    selectedTrimColor = standardBravosDressStyle?.let { Color(it.trimHex) },
                    options = serviceDressOptions,
                    selectedValue = standardBravosDressStyle,
                    onSelect = { standardBravosDressStyle = it },
                    fieldTestTag = "squad_standard_bravos_dress_dropdown"
                )

                SingleChoiceDropdown(
                    label = "Alphas • Service Coat",
                    selectedText = standardAlphasDressStyle?.label ?: "Keep Individual Cut",
                    selectedPrimaryColor = standardAlphasDressStyle?.let { Color(it.tunicHex) },
                    selectedTrimColor = standardAlphasDressStyle?.let { Color(it.trimHex) },
                    options = serviceDressOptions,
                    selectedValue = standardAlphasDressStyle,
                    onSelect = { standardAlphasDressStyle = it },
                    fieldTestTag = "squad_standard_dress_dropdown"
                )

                val capOptions = remember {
                    listOf(
                        DropdownOptionItem<HeadgearStyle?>(
                            value = null,
                            title = "Keep Individual Cap",
                            subtitle = "Respect each soldier's service/peaked cap"
                        )
                    ) + HeadgearStyle.entries.filter { it.kind != HeadgearKind.BERET }.map { hg ->
                        DropdownOptionItem<HeadgearStyle?>(
                            value = hg,
                            title = hg.label,
                            subtitle = hg.requirementHint,
                            primaryColor = Color(hg.colorHex),
                            trimColor = extra.brassBright
                        )
                    }
                }
                SingleChoiceDropdown(
                    label = "Alphas • Service Cap",
                    selectedText = standardAlphasHeadgear?.label ?: "Keep Individual Cap",
                    selectedPrimaryColor = standardAlphasHeadgear?.let { Color(it.colorHex) },
                    selectedTrimColor = standardAlphasHeadgear?.let { extra.brassBright },
                    options = capOptions,
                    selectedValue = standardAlphasHeadgear,
                    onSelect = { standardAlphasHeadgear = it },
                    fieldTestTag = "squad_standard_headgear_dropdown"
                )

                val messDressOptions = remember {
                    listOf(
                        DropdownOptionItem<DressUniformStyle?>(
                            value = null,
                            title = "Keep Individual Mess Jacket",
                            subtitle = "Respect each soldier's formal Mess cut"
                        )
                    ) + DressUniformStyle.entries.filter { it.isCutawayMess }.map { ds ->
                        DropdownOptionItem<DressUniformStyle?>(
                            value = ds,
                            title = ds.label,
                            subtitle = ds.requirementHint,
                            primaryColor = Color(ds.tunicHex),
                            trimColor = Color(ds.trimHex)
                        )
                    }
                }
                SingleChoiceDropdown(
                    label = "Mess • Formal Cutaway Jacket",
                    selectedText = standardMessDressStyle?.label ?: "Keep Individual Mess Jacket",
                    selectedPrimaryColor = standardMessDressStyle?.let { Color(it.tunicHex) },
                    selectedTrimColor = standardMessDressStyle?.let { Color(it.trimHex) },
                    options = messDressOptions,
                    selectedValue = standardMessDressStyle,
                    onSelect = { standardMessDressStyle = it },
                    fieldTestTag = "squad_standard_mess_dress_dropdown"
                )

                SingleChoiceDropdown(
                    label = "Mess • Formal Cap",
                    selectedText = standardMessHeadgear?.label ?: "Keep Individual Cap",
                    selectedPrimaryColor = standardMessHeadgear?.let { Color(it.colorHex) },
                    selectedTrimColor = standardMessHeadgear?.let { extra.brassBright },
                    options = capOptions,
                    selectedValue = standardMessHeadgear,
                    onSelect = { standardMessHeadgear = it },
                    fieldTestTag = "squad_standard_mess_headgear_dropdown"
                )

                val glovesOptions = remember {
                    listOf(
                        DropdownOptionItem<Boolean?>(
                            value = null,
                            title = "Keep Individual Gloves",
                            subtitle = "Respect each soldier's Alphas & Mess glove setting"
                        ),
                        DropdownOptionItem<Boolean?>(
                            value = true,
                            title = "White Parade Gloves Worn",
                            subtitle = "Uniform white gloves in Alphas & Mess"
                        ),
                        DropdownOptionItem<Boolean?>(
                            value = false,
                            title = "No Gloves (Bare Hands)",
                            subtitle = "Bare hands in Alphas & Mess"
                        )
                    )
                }
                SingleChoiceDropdown(
                    label = "Alphas & Mess • White Gloves",
                    selectedText = when (standardWhiteGloves) {
                        null -> "Keep Individual Gloves"
                        true -> "White Parade Gloves Worn"
                        false -> "No Gloves (Bare Hands)"
                    },
                    options = glovesOptions,
                    selectedValue = standardWhiteGloves,
                    onSelect = { standardWhiteGloves = it },
                    fieldTestTag = "squad_standard_gloves_dropdown"
                )

                if (editingSquad != null) {
                    if (onApplyStandard != null || onSaveWithStandard != null) {
                        FilledTonalButton(
                            onClick = {
                                val standard = buildStandard()
                                if (onSaveWithStandard != null) {
                                    onSaveWithStandard(
                                        editingSquad.id,
                                        squadName,
                                        memberIds,
                                        leaderId,
                                        standard,
                                        true
                                    )
                                } else {
                                    onSave(
                                        editingSquad.id,
                                        squadName,
                                        memberIds,
                                        leaderId,
                                        standardPatch,
                                        standardCamo,
                                        standardAlphasHeadgear,
                                        standardAlphasDressStyle
                                    )
                                    onApplyStandard?.invoke(editingSquad.id)
                                }
                                onDismiss()
                            },
                            enabled = memberIds.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("dialog_apply_squad_standard")
                        ) {
                            Icon(Icons.Filled.Checkroom, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save & Dress Squad in Standard")
                        }
                    }
                    HorizontalDivider(color = extra.borderSubtle)
                    OutlinedButton(
                        onClick = {
                            onDisband(editingSquad.id)
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("disband_squad_button")
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Disband ${editingSquad.name}")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val standard = buildStandard()
                    if (onSaveWithStandard != null) {
                        onSaveWithStandard(
                            editingSquad?.id,
                            squadName,
                            memberIds,
                            leaderId,
                            standard,
                            false
                        )
                    } else {
                        onSave(
                            editingSquad?.id,
                            squadName,
                            memberIds,
                            leaderId,
                            standardPatch,
                            standardCamo,
                            standardAlphasHeadgear,
                            standardAlphasDressStyle
                        )
                    }
                    onDismiss()
                },
                enabled = memberIds.isNotEmpty(),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("save_squad_button")
            ) {
                Text("Save Squad")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("cancel_squad_dialog")
            ) {
                Text("Cancel")
            }
        }
    )
}
