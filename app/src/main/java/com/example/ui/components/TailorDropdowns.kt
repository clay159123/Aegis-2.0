package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.catalog.GameCatalog
import com.example.domain.TacticalCalculator
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import com.example.ui.theme.LocalAegisColors

data class DropdownOptionItem<T>(
    val value: T,
    val title: String,
    val subtitle: String? = null,
    val primaryColor: Color? = null,
    val trimColor: Color? = null,
    val enabled: Boolean = true,
    val testTag: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SingleChoiceDropdown(
    label: String,
    selectedText: String,
    selectedPrimaryColor: Color? = null,
    selectedTrimColor: Color? = null,
    supportingHint: String? = null,
    options: List<DropdownOptionItem<T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fieldTestTag: String? = null
) {
    val extra = LocalAegisColors.current
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedText,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(label) },
                leadingIcon = if (selectedPrimaryColor != null) {
                    {
                        ColorSwatchDot(
                            primaryColor = selectedPrimaryColor,
                            trimColor = selectedTrimColor ?: extra.borderSubtle
                        )
                    }
                } else null,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = extra.brassBright,
                    unfocusedBorderColor = extra.borderSubtle,
                    focusedLabelColor = extra.brassBright,
                    unfocusedLabelColor = extra.textMuted
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .let { base -> if (fieldTestTag != null) base.testTag(fieldTestTag) else base }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                options.forEach { item ->
                    val isSelected = item.value == selectedValue
                    DropdownMenuItem(
                        enabled = item.enabled,
                        onClick = {
                            expanded = false
                            if (item.enabled) onSelect(item.value)
                        },
                        leadingIcon = if (item.primaryColor != null) {
                            {
                                ColorSwatchDot(
                                    primaryColor = item.primaryColor,
                                    trimColor = item.trimColor ?: extra.borderSubtle,
                                    modifier = Modifier.alpha(if (item.enabled) 1f else 0.45f)
                                )
                            }
                        } else null,
                        trailingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = extra.brassBright,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null,
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        isSelected -> extra.brassBright
                                        item.enabled -> MaterialTheme.colorScheme.onSurface
                                        else -> extra.textMuted
                                    }
                                )
                                if (!item.subtitle.isNullOrBlank()) {
                                    Text(
                                        text = item.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (item.enabled) extra.textMuted else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .let { base -> if (item.testTag != null) base.testTag(item.testTag) else base }
                    )
                }
            }
        }
        if (!supportingHint.isNullOrBlank()) {
            Text(
                text = supportingHint,
                style = MaterialTheme.typography.labelSmall,
                color = extra.textMuted,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun ColorSwatchDot(
    primaryColor: Color,
    trimColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(primaryColor)
            .border(1.5.dp, trimColor, CircleShape)
    )
}

@Composable
fun CamoPatternDropdown(
    selectedPattern: CamoPattern,
    onSelect: (CamoPattern) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "dropdown_camo_pattern"
) {
    val options = remember {
        CamoPattern.SELECTABLE.map { pattern ->
            DropdownOptionItem(
                value = pattern,
                title = pattern.label,
                subtitle = "Field ACU / Combat Shirt camouflage",
                primaryColor = Color(pattern.primaryHex),
                trimColor = Color(pattern.accentHex)
            )
        }
    }
    SingleChoiceDropdown(
        label = "Field ACU Camo Pattern",
        selectedText = selectedPattern.label,
        selectedPrimaryColor = Color(selectedPattern.primaryHex),
        selectedTrimColor = Color(selectedPattern.accentHex),
        options = options,
        selectedValue = selectedPattern,
        onSelect = onSelect,
        modifier = modifier,
        fieldTestTag = testTag
    )
}

@Composable
fun DressStyleDropdown(
    operator: Operator,
    ownedMedalIds: Set<String>,
    onSelect: (DressUniformStyle) -> Unit,
    modifier: Modifier = Modifier,
    freeCustomOverride: Boolean = false,
    wardrobeKit: WardrobeKit? = null,
    testTag: String = "dropdown_dress_style"
) {
    val options = remember(operator, ownedMedalIds, freeCustomOverride, wardrobeKit) {
        DressUniformStyle.entries.map { style ->
            val elig = TacticalCalculator.evaluateDressStyleEligibility(style, operator, ownedMedalIds, wardrobeKit)
            val isEnabled = elig.eligible || freeCustomOverride
            DropdownOptionItem(
                value = style,
                title = if (!elig.eligible && freeCustomOverride) "★ ${style.label} (Override)" else style.label,
                subtitle = when {
                    elig.eligible -> "${style.subtitle} • ${style.requirementHint}"
                    freeCustomOverride -> "Override unlocked • Reg: ${elig.missingRequirements.firstOrNull() ?: style.requirementHint}"
                    else -> "Locked: ${elig.missingRequirements.firstOrNull() ?: style.requirementHint}"
                },
                primaryColor = Color(style.tunicHex),
                trimColor = Color(style.trimHex),
                enabled = isEnabled,
                testTag = "dress_style_${style.name.lowercase()}"
            )
        }
    }
    SingleChoiceDropdown(
        label = "Dress Uniform Cut",
        selectedText = operator.dressStyle.label,
        selectedPrimaryColor = Color(operator.dressStyle.tunicHex),
        selectedTrimColor = Color(operator.dressStyle.trimHex),
        supportingHint = "${operator.dressStyle.subtitle} • ${operator.specialty.label} Collar Brass",
        options = options,
        selectedValue = operator.dressStyle,
        onSelect = onSelect,
        modifier = modifier,
        fieldTestTag = testTag
    )
}

@Composable
fun HeadgearStyleDropdown(
    operator: Operator,
    ownedMedalIds: Set<String>,
    onSelect: (HeadgearStyle?) -> Unit,
    modifier: Modifier = Modifier,
    freeCustomOverride: Boolean = false,
    wardrobeKit: WardrobeKit? = null,
    testTag: String = "dropdown_headgear_style"
) {
    val extra = LocalAegisColors.current
    // Auto = no saved manual pick for this kit; the game wears the highest earned beret or the rank's cap
    val isAuto = wardrobeKit != null && wardrobeKit != WardrobeKit.FIELD &&
        operator.wardrobe.forKit(wardrobeKit).headgearStyle == null
    val autoHeadgear = remember(operator, ownedMedalIds, wardrobeKit) {
        wardrobeKit?.takeIf { it != WardrobeKit.FIELD }?.let {
            TacticalCalculator.defaultHeadgearForKit(it, operator, ownedMedalIds, preferred = null)
        }
    }
    val options = remember(operator, ownedMedalIds, freeCustomOverride, wardrobeKit, autoHeadgear) {
        listOfNotNull(
            autoHeadgear?.let { auto ->
                DropdownOptionItem<HeadgearStyle?>(
                    value = null,
                    title = "Auto (${auto.label})",
                    subtitle = "Wears the highest headgear earned; updates as the soldier qualifies",
                    primaryColor = Color(auto.colorHex),
                    trimColor = extra.signalsCyan,
                    enabled = true,
                    testTag = "${testTag}_opt_auto"
                )
            }
        ) + HeadgearStyle.entries.map { hg ->
            val elig = TacticalCalculator.evaluateHeadgearEligibility(hg, operator, ownedMedalIds, wardrobeKit)
            val isEnabled = elig.eligible || freeCustomOverride
            DropdownOptionItem<HeadgearStyle?>(
                value = hg,
                title = if (!elig.eligible && freeCustomOverride) "★ ${hg.label} (Override)" else hg.label,
                subtitle = when {
                    elig.eligible -> hg.requirementHint
                    freeCustomOverride -> "Override unlocked • Reg: ${elig.missingRequirements.firstOrNull() ?: hg.requirementHint}"
                    else -> "Locked: ${elig.missingRequirements.firstOrNull() ?: hg.requirementHint}"
                },
                primaryColor = Color(hg.colorHex),
                trimColor = Color(operator.dressStyle.trimHex),
                enabled = isEnabled
            )
        }
    }
    SingleChoiceDropdown(
        label = "Army Headgear",
        selectedText = if (isAuto) "Auto (${operator.headgearStyle.label})" else operator.headgearStyle.label,
        selectedPrimaryColor = Color(operator.headgearStyle.colorHex),
        selectedTrimColor = Color(operator.dressStyle.trimHex),
        supportingHint = operator.headgearStyle.requirementHint,
        options = options,
        selectedValue = if (isAuto) null else operator.headgearStyle,
        onSelect = onSelect,
        modifier = modifier,
        fieldTestTag = testTag
    )
}

@Composable
fun ShoulderCordDropdown(
    label: String,
    isRightShoulder: Boolean,
    specialty: OperatorSpecialty,
    selectedCordId: String?,
    ownedMedalIds: Set<String>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    otherShoulderCordId: String? = null,
    freeCustomOverride: Boolean = false,
    /** The cord Auto currently resolves to, or null when Auto leaves the shoulder plain */
    autoCord: MedalDefinition? = null,
    testTag: String
) {
    val extra = LocalAegisColors.current
    val isAuto = selectedCordId == UniformTailorOverrides.SLOT_AUTO
    val shoulderSlot = if (isRightShoulder) CordShoulderSlot.RIGHT_STAFF_VALOR else CordShoulderSlot.LEFT_BRANCH
    val allowedCords = remember(isRightShoulder, specialty, freeCustomOverride) {
        if (freeCustomOverride) {
            val reg = UniformRegaliaRules.cordsForShoulder(GameCatalog.medals, isRightShoulder, specialty)
            val other = UniformRegaliaRules.cordsForShoulder(GameCatalog.medals, !isRightShoulder, specialty)
            reg + other
        } else {
            UniformRegaliaRules.cordsForShoulder(
                medals = GameCatalog.medals,
                isRightShoulder = isRightShoulder,
                specialty = specialty
            )
        }
    }
    val selectedCord = allowedCords.find { it.id == selectedCordId }

    val options = remember(allowedCords, ownedMedalIds, otherShoulderCordId, isRightShoulder, specialty, autoCord) {
        buildList {
            add(
                DropdownOptionItem<String?>(
                    value = UniformTailorOverrides.SLOT_AUTO,
                    title = "Auto (${autoCord?.shortName ?: "None Earned Yet"})",
                    subtitle = if (isRightShoulder) {
                        "HQ staff: Gold Aiguillette, then Valor Cord. Field: Valor Cord"
                    } else {
                        "Highest MOS-preferred cord earned; updates as new cords are awarded"
                    },
                    primaryColor = autoCord?.cordHex?.let { Color(it) } ?: Color.Transparent,
                    trimColor = extra.signalsCyan,
                    enabled = true,
                    testTag = "${testTag}_opt_auto"
                )
            )
            add(
                DropdownOptionItem<String?>(
                    value = null,
                    title = "None Worn",
                    subtitle = "Leave ${if (isRightShoulder) "right" else "left"} shoulder plain",
                    primaryColor = Color.Transparent,
                    trimColor = extra.borderSubtle,
                    enabled = true
                )
            )
            allowedCords.forEach { cord ->
                val owned = cord.id in ownedMedalIds
                val onOtherSide = otherShoulderCordId != null && cord.id == otherShoulderCordId
                val canEquip = owned && !onOtherSide
                val isMosTop = UniformRegaliaRules.isPrimaryMosCord(specialty, cord.id)
                val titleText = if (isMosTop) "★ ${cord.shortName}" else cord.shortName
                add(
                    DropdownOptionItem<String?>(
                        value = cord.id,
                        title = titleText,
                        subtitle = when {
                            onOtherSide -> "Equipped on opposite shoulder"
                            owned && isMosTop -> "MOS Signature (${specialty.roleTag}) • ${cord.name}"
                            owned -> cord.name
                            isMosTop -> "MOS Signature (${specialty.roleTag}) • Locked: ${cord.qualificationRule}"
                            else -> "Locked (Award): ${cord.qualificationRule}"
                        },
                        primaryColor = Color(cord.cordHex ?: 0xFF2C6FB8),
                        trimColor = Color(0xFFF2D272),
                        enabled = canEquip,
                        testTag = "${testTag}_opt_${cord.id}"
                    )
                )
            }
        }
    }

    SingleChoiceDropdown(
        label = label,
        selectedText = when {
            isAuto -> "Auto (${autoCord?.shortName ?: "None Earned Yet"})"
            else -> selectedCord?.shortName ?: "None Worn"
        },
        selectedPrimaryColor = (if (isAuto) autoCord else selectedCord)?.cordHex?.let { Color(it) } ?: Color.Transparent,
        selectedTrimColor = if (isAuto) extra.signalsCyan else if (selectedCord != null) Color(0xFFF2D272) else extra.borderSubtle,
        supportingHint = if (selectedCord != null) {
            "${shoulderSlot.ruleHint} • ${selectedCord.name}"
        } else {
            val ownedCount = allowedCords.count { it.id in ownedMedalIds }
            "${shoulderSlot.ruleHint} • $ownedCount/${allowedCords.size} earned"
        },
        options = options,
        selectedValue = if (isAuto) UniformTailorOverrides.SLOT_AUTO else selectedCord?.id,
        onSelect = onSelect,
        modifier = modifier,
        fieldTestTag = testTag
    )
}

@Composable
fun UnitPatchDropdown(
    selectedPatch: UnitPatchStyle,
    autoPatch: UnitPatchStyle,
    isHqStaff: Boolean,
    onSelect: (UnitPatchStyle) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "dropdown_unit_patch"
) {
    // High Command is reserved for HQ staff
    val choices = remember(isHqStaff) {
        buildList {
            add(UnitPatchStyle.AUTO_MOS)
            addAll(UnitPatchStyle.DIVISIONS)
            if (isHqStaff) add(UnitPatchStyle.SUPREME_HQ)
            add(UnitPatchStyle.UNASSIGNED)
            add(UnitPatchStyle.NONE)
        }
    }
    fun titleFor(patch: UnitPatchStyle): String =
        if (patch == UnitPatchStyle.AUTO_MOS) "Auto (${autoPatch.label})" else patch.label
    val options = choices.map { patch ->
        val shown = if (patch == UnitPatchStyle.AUTO_MOS) autoPatch else patch
        DropdownOptionItem(
            value = patch,
            title = titleFor(patch),
            subtitle = patch.subtitle,
            primaryColor = Color(shown.primaryHex),
            trimColor = Color(shown.accentHex),
            enabled = true,
            testTag = "unit_patch_${patch.name.lowercase()}"
        )
    }
    val shownSelected = if (selectedPatch == UnitPatchStyle.AUTO_MOS) autoPatch else selectedPatch
    SingleChoiceDropdown(
        label = "Unit Shoulder Patch (SSI)",
        selectedText = titleFor(selectedPatch),
        selectedPrimaryColor = Color(shownSelected.primaryHex),
        selectedTrimColor = Color(shownSelected.accentHex),
        supportingHint = "Auto follows the soldier's squad, HQ assignment, or Unassigned",
        options = options,
        selectedValue = selectedPatch,
        onSelect = onSelect,
        modifier = modifier,
        fieldTestTag = testTag
    )
}

@Composable
fun RegaliaSlotDropdown(
    label: String,
    slotValue: String,
    candidateMedals: List<MedalDefinition>,
    ownedMedalIds: Set<String>,
    autoHint: String,
    noneSubtitle: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    otherSelectedIds: Set<String> = emptySet(),
    autoTitle: String = "Auto (Highest Precedence)",
    recommendedIds: Set<String> = emptySet(),
    testTag: String
) {
    val extra = LocalAegisColors.current
    val selectedMedal = candidateMedals.find { it.id == slotValue }

    val options = remember(
        candidateMedals,
        ownedMedalIds,
        otherSelectedIds,
        autoTitle,
        autoHint,
        noneSubtitle,
        recommendedIds
    ) {
        buildList {
            add(
                DropdownOptionItem(
                    value = UniformTailorOverrides.SLOT_AUTO,
                    title = autoTitle,
                    subtitle = autoHint,
                    primaryColor = extra.signalsCyan,
                    trimColor = extra.brassBright,
                    enabled = true,
                    testTag = "${testTag}_opt_auto"
                )
            )
            add(
                DropdownOptionItem(
                    value = UniformTailorOverrides.SLOT_NONE,
                    title = "None Worn (Empty Slot)",
                    subtitle = noneSubtitle,
                    primaryColor = Color.Transparent,
                    trimColor = extra.borderSubtle,
                    enabled = true,
                    testTag = "${testTag}_opt_none"
                )
            )
            candidateMedals.forEach { medal ->
                val owned = medal.id in ownedMedalIds
                val inOtherSlot = medal.id in otherSelectedIds
                val isRecommended = medal.id in recommendedIds
                val swatchHex = medal.cordHex
                    ?: medal.ribbonStripeHexes.firstOrNull()
                    ?: medal.insigniaColorHex
                val trimHex = medal.ribbonStripeHexes.getOrNull(1) ?: medal.insigniaColorHex
                add(
                    DropdownOptionItem(
                        value = medal.id,
                        title = if (isRecommended) "★ ${medal.shortName}" else medal.shortName,
                        subtitle = when {
                            inOtherSlot -> "Selected in another slot"
                            owned && isRecommended -> "MOS Primary • ${medal.name}"
                            owned -> medal.name
                            isRecommended -> "MOS Primary • Locked: ${medal.qualificationRule}"
                            else -> "Locked: ${medal.qualificationRule}"
                        },
                        primaryColor = Color(swatchHex),
                        trimColor = Color(trimHex),
                        enabled = owned && !inOtherSlot,
                        testTag = "${testTag}_opt_${medal.id}"
                    )
                )
            }
        }
    }

    val selectedText = when (slotValue) {
        UniformTailorOverrides.SLOT_AUTO -> autoTitle
        UniformTailorOverrides.SLOT_NONE -> "None Worn (Empty Slot)"
        else -> selectedMedal?.shortName ?: autoTitle
    }
    val selectedPrimary = when (slotValue) {
        UniformTailorOverrides.SLOT_AUTO -> extra.signalsCyan
        UniformTailorOverrides.SLOT_NONE -> Color.Transparent
        else -> selectedMedal?.let {
            Color(it.cordHex ?: it.ribbonStripeHexes.firstOrNull() ?: it.insigniaColorHex)
        } ?: extra.signalsCyan
    }
    val selectedTrim = when (slotValue) {
        UniformTailorOverrides.SLOT_NONE -> extra.borderSubtle
        else -> extra.brassBright
    }
    val ownedCount = candidateMedals.count { it.id in ownedMedalIds }

    SingleChoiceDropdown(
        label = label,
        selectedText = selectedText,
        selectedPrimaryColor = selectedPrimary,
        selectedTrimColor = selectedTrim,
        supportingHint = selectedMedal?.name ?: "$autoHint • $ownedCount/${candidateMedals.size} earned",
        options = options,
        selectedValue = if (selectedMedal != null || slotValue == UniformTailorOverrides.SLOT_NONE) {
            slotValue
        } else {
            UniformTailorOverrides.SLOT_AUTO
        },
        onSelect = onSelect,
        modifier = modifier,
        fieldTestTag = testTag
    )
}

@Composable
fun BooleanChoiceDropdown(
    label: String,
    selectedValue: Boolean,
    trueTitle: String,
    trueSubtitle: String,
    falseTitle: String,
    falseSubtitle: String,
    primaryTrueColor: Color,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabledTrue: Boolean = true,
    lockedTrueReason: String? = null,
    testTag: String
) {
    val extra = LocalAegisColors.current
    val options = remember(
        trueTitle,
        trueSubtitle,
        falseTitle,
        falseSubtitle,
        primaryTrueColor,
        enabledTrue,
        lockedTrueReason
    ) {
        listOf(
            DropdownOptionItem(
                value = true,
                title = trueTitle,
                subtitle = if (enabledTrue) trueSubtitle else "Locked: ${lockedTrueReason ?: trueSubtitle}",
                primaryColor = primaryTrueColor,
                trimColor = extra.brassBright,
                enabled = enabledTrue
            ),
            DropdownOptionItem(
                value = false,
                title = falseTitle,
                subtitle = falseSubtitle,
                primaryColor = Color.Transparent,
                trimColor = extra.borderSubtle,
                enabled = true
            )
        )
    }
    SingleChoiceDropdown(
        label = label,
        selectedText = if (selectedValue) trueTitle else falseTitle,
        selectedPrimaryColor = if (selectedValue) primaryTrueColor else Color.Transparent,
        selectedTrimColor = if (selectedValue) extra.brassBright else extra.borderSubtle,
        supportingHint = if (selectedValue) {
            trueSubtitle
        } else if (!enabledTrue && !lockedTrueReason.isNullOrBlank()) {
            "$falseSubtitle • Unlock: $lockedTrueReason"
        } else {
            falseSubtitle
        },
        options = options,
        selectedValue = selectedValue,
        onSelect = onSelect,
        modifier = modifier,
        fieldTestTag = testTag
    )
}

@Composable
fun RecruitMosDropdown(
    selectedMos: OperatorSpecialty,
    onSelect: (OperatorSpecialty) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "dropdown_recruit_mos"
) {
    val extra = LocalAegisColors.current
    val options = remember {
        // Aviators (153A) come only from Flight School or the WOCS Aviator recruit source
        OperatorSpecialty.entries.filter { it != OperatorSpecialty.AVIATION }.map { mos ->
            DropdownOptionItem(
                value = mos,
                title = "${mos.roleTag} • ${mos.label}",
                subtitle = mos.recruitSummary,
                primaryColor = extra.signalsCyan,
                trimColor = extra.brassBright,
                enabled = true,
                testTag = "mos_option_${mos.name.lowercase()}"
            )
        }
    }
    SingleChoiceDropdown(
        label = "Army MOS Specialty",
        selectedText = "${selectedMos.roleTag} • ${selectedMos.label}",
        selectedPrimaryColor = extra.signalsCyan,
        selectedTrimColor = extra.brassBright,
        supportingHint = selectedMos.recruitSummary,
        options = options,
        selectedValue = selectedMos,
        onSelect = onSelect,
        modifier = modifier,
        fieldTestTag = testTag
    )
}

/** Item 31: where the recruit comes from, which sets arrival rank, record and cost. */
@Composable
fun RecruitSourceDropdown(
    selectedSource: RecruitSource,
    onSelect: (RecruitSource) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "dropdown_recruit_source"
) {
    val extra = LocalAegisColors.current
    val options = remember {
        RecruitSource.entries.map { src ->
            DropdownOptionItem(
                value = src,
                title = src.label,
                subtitle = "Arrives ${src.summary}",
                primaryColor = extra.brassBright,
                trimColor = extra.signalsCyan,
                enabled = true,
                testTag = "recruit_source_${src.name.lowercase()}"
            )
        }
    }
    SingleChoiceDropdown(
        label = "Recruit Source",
        selectedText = selectedSource.label,
        selectedPrimaryColor = extra.brassBright,
        selectedTrimColor = extra.signalsCyan,
        supportingHint = "Arrives ${selectedSource.summary}",
        options = options,
        selectedValue = selectedSource,
        onSelect = onSelect,
        modifier = modifier,
        fieldTestTag = testTag
    )
}

@Composable
fun CombatPatchDropdown(
    selectedPatch: UnitPatchStyle,
    earnedPatches: List<UnitPatchStyle>,
    onSelect: (UnitPatchStyle) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "dropdown_combat_patch"
) {
    // Only patches earned in a campaign can be worn on the right shoulder
    val latest = earnedPatches.firstOrNull()
    val autoTitle = if (latest != null) "Auto (${latest.label})" else "Auto (None Earned Yet)"
    val choices = buildList {
        add(UnitPatchStyle.AUTO_MOS)
        addAll(earnedPatches)
        add(UnitPatchStyle.NONE)
    }
    val options = choices.map { patch ->
        val shown = if (patch == UnitPatchStyle.AUTO_MOS) (latest ?: UnitPatchStyle.NONE) else patch
        DropdownOptionItem(
            value = patch,
            title = if (patch == UnitPatchStyle.AUTO_MOS) autoTitle else patch.label,
            subtitle = if (patch == UnitPatchStyle.AUTO_MOS) {
                "Squad patch worn in the most recent campaign"
            } else {
                patch.subtitle
            },
            primaryColor = Color(shown.primaryHex),
            trimColor = Color(shown.accentHex),
            enabled = true,
            testTag = "combat_patch_${patch.name.lowercase()}"
        )
    }
    val shownSelected = when {
        selectedPatch == UnitPatchStyle.AUTO_MOS -> latest ?: UnitPatchStyle.NONE
        selectedPatch in earnedPatches || selectedPatch == UnitPatchStyle.NONE -> selectedPatch
        else -> latest ?: UnitPatchStyle.NONE
    }
    SingleChoiceDropdown(
        label = "Combat Patch (Right Sleeve)",
        selectedText = if (selectedPatch == UnitPatchStyle.AUTO_MOS) autoTitle else shownSelected.label,
        selectedPrimaryColor = Color(shownSelected.primaryHex),
        selectedTrimColor = Color(shownSelected.accentHex),
        supportingHint = "Earned by fighting in a campaign with a squad",
        options = options,
        selectedValue = selectedPatch,
        onSelect = onSelect,
        modifier = modifier,
        fieldTestTag = testTag
    )
}

@Composable
fun MarksmanshipBadgeDropdown(
    selectedTier: MarksmanshipBadgeTier,
    resolvedTier: MarksmanshipBadgeTier,
    onSelect: (MarksmanshipBadgeTier) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "dropdown_marksmanship"
) {
    val options = remember(resolvedTier) {
        MarksmanshipBadgeTier.entries.map { tier ->
            DropdownOptionItem(
                value = tier,
                title = if (tier == MarksmanshipBadgeTier.AUTO) {
                    "Auto (Current Qual: ${resolvedTier.shortLabel})"
                } else {
                    tier.label
                },
                subtitle = tier.subtitle,
                primaryColor = Color(tier.badgeColorHex),
                trimColor = Color(0xFF14181F),
                enabled = true,
                testTag = "marksmanship_${tier.name.lowercase()}"
            )
        }
    }
    SingleChoiceDropdown(
        label = "Marksmanship Qualification Badge",
        selectedText = if (selectedTier == MarksmanshipBadgeTier.AUTO) {
            "Auto (${resolvedTier.shortLabel})"
        } else {
            selectedTier.label
        },
        selectedPrimaryColor = Color(selectedTier.badgeColorHex),
        selectedTrimColor = Color(0xFF14181F),
        supportingHint = "Silver/Gold Maltese Cross & Rifle/Pistol clasps worn on the left breast pocket",
        options = options,
        selectedValue = selectedTier,
        onSelect = onSelect,
        modifier = modifier,
        fieldTestTag = testTag
    )
}

@Composable
fun LeaderPinDropdown(
    selectedOverride: LeaderRole?,
    autoRole: LeaderRole,
    onSelect: (LeaderRole?) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "dropdown_leader_pin"
) {
    val extra = LocalAegisColors.current
    fun colorFor(role: LeaderRole): Color = when (role) {
        LeaderRole.SQUAD_LEADER -> Color(0xFF8C2525)
        LeaderRole.TEAM_LEADER -> Color(0xFF1E3A6E)
        LeaderRole.NONE -> Color.Transparent
    }
    val options = remember(autoRole) {
        listOf(
            DropdownOptionItem<LeaderRole?>(
                value = null,
                title = "Auto (${autoRole.label})",
                subtitle = "Follows the soldier's role in their squad",
                primaryColor = colorFor(autoRole),
                trimColor = extra.brassBright,
                testTag = "${testTag}_auto"
            ),
            DropdownOptionItem<LeaderRole?>(
                value = LeaderRole.SQUAD_LEADER,
                title = "Squad Leader Pin",
                subtitle = "Crimson pin with a gold star; green and brass tab in the field",
                primaryColor = colorFor(LeaderRole.SQUAD_LEADER),
                trimColor = extra.brassBright,
                testTag = "${testTag}_show"
            ),
            DropdownOptionItem<LeaderRole?>(
                value = LeaderRole.TEAM_LEADER,
                title = "Team Leader Pin",
                subtitle = "Blue pin with a silver chevron; green and silver tab in the field",
                primaryColor = colorFor(LeaderRole.TEAM_LEADER),
                trimColor = Color(0xFFD8DEE8),
                testTag = "${testTag}_team"
            ),
            DropdownOptionItem<LeaderRole?>(
                value = LeaderRole.NONE,
                title = "Hidden",
                subtitle = "Keep space above nameplate clear",
                primaryColor = Color.Transparent,
                trimColor = extra.borderSubtle,
                testTag = "${testTag}_hide"
            )
        )
    }
    val shown = selectedOverride ?: autoRole
    SingleChoiceDropdown(
        label = "Leader Pin",
        selectedText = when (selectedOverride) {
            null -> "Auto (${autoRole.label})"
            LeaderRole.SQUAD_LEADER -> "Squad Leader Pin"
            LeaderRole.TEAM_LEADER -> "Team Leader Pin"
            LeaderRole.NONE -> "Hidden"
        },
        selectedPrimaryColor = colorFor(shown),
        selectedTrimColor = extra.brassBright,
        options = options,
        selectedValue = selectedOverride,
        onSelect = onSelect,
        modifier = modifier,
        fieldTestTag = testTag
    )
}
