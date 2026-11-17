package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.catalog.GameCatalog
import com.example.domain.TacticalCalculator
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import com.example.ui.theme.LocalAegisColors

private enum class TailorCategoryKey {
    COAT_HEADGEAR,
    PATCHES_TABS,
    CORDS,
    SCHOOL_PINS,
    MEDALS_RIBBONS,
    BELT_GLOVES_CITATIONS
}

@Composable
fun UniformTailorStudioCard(
    operator: Operator,
    awardedDecorations: List<MedalDefinition>,
    onApply: (UniformTailorRequest) -> Unit,
    onClose: (() -> Unit)? = null,
    equippedGear: List<GearItem> = emptyList(),
    squads: List<TacticalSquad> = emptyList(),
    activeKit: WardrobeKit = operator.activeWardrobeKit,
    modifier: Modifier = Modifier
) {
    val extra = LocalAegisColors.current
    var callsign by remember(operator.id) { mutableStateOf(operator.callsign) }
    var expandedCategories by remember(operator.id) {
        mutableStateOf(setOf(TailorCategoryKey.COAT_HEADGEAR))
    }

    val overrides = operator.tailorOverrides
    val freeOverride = overrides.freeTailorOverride
    val ownedMedalIds = remember(awardedDecorations) { awardedDecorations.map { it.id }.toSet() }
    val effectiveKit = remember(activeKit, operator.status) {
        if (activeKit.isAvailableFor(operator)) activeKit else WardrobeKit.ALPHAS
    }
    val kitPreviewOp = remember(operator, effectiveKit, squads, ownedMedalIds) {
        TacticalCalculator.resolveKitAppearance(operator, effectiveKit, squads, ownedMedalIds)
    }
    val wornSummary = remember(kitPreviewOp, awardedDecorations) {
        TacticalCalculator.resolveWornRegalia(kitPreviewOp, awardedDecorations)
    }
    val alphasSummary = remember(operator, awardedDecorations) {
        TacticalCalculator.resolveWornRegalia(operator, awardedDecorations)
    }
    val sashElig = remember(operator, squads) {
        TacticalCalculator.evaluateDressSashEligibility(operator, squads)
    }
    val glovesElig = remember(operator, ownedMedalIds) {
        TacticalCalculator.evaluateWhiteGlovesEligibility(operator, ownedMedalIds)
    }
    val resolvedMarksmanship = remember(operator, equippedGear) {
        UniformRegaliaRules.resolveMarksmanshipTier(operator, equippedGear)
    }
    val autoIsLeader = remember(operator.id, squads) {
        squads.any { it.leaderId == operator.id }
    }

    val tabCatalog = remember {
        UniformRegaliaRules.tabsInPrecedenceOrder(GameCatalog.medals)
    }
    // Recommend the highest-precedence tab the soldier has earned
    val primaryTabIds = remember(ownedMedalIds) {
        tabCatalog.firstOrNull { it.id in ownedMedalIds }?.let { setOf(it.id) } ?: emptySet()
    }
    val allChestBadges = remember {
        GameCatalog.medals.filter {
            it.category == AwardCategory.SCHOOL_INSIGNIA && it.insigniaShape != InsigniaShape.TAB
        }.sortedBy { it.precedenceOrder }
    }
    val aboveRackCatalog = remember(operator.specialty, freeOverride) {
        val reg = UniformRegaliaRules.chestBadgesForSlot(GameCatalog.medals, ChestBadgeSlot.ABOVE_RACK, operator.specialty)
        if (!freeOverride) reg else (reg + allChestBadges.filter { it !in reg })
    }
    val leftPocketCatalog = remember(operator.specialty, freeOverride) {
        val reg = UniformRegaliaRules.chestBadgesForSlot(GameCatalog.medals, ChestBadgeSlot.LEFT_POCKET, operator.specialty)
        if (!freeOverride) reg else (reg + allChestBadges.filter { it !in reg })
    }
    val rightPocketCatalog = remember(operator.specialty, freeOverride) {
        // Slot 3 is the catch-all: crests first, then every other chest badge
        val reg = UniformRegaliaRules.chestBadgesForSlot(GameCatalog.medals, ChestBadgeSlot.RIGHT_POCKET, operator.specialty)
        reg + allChestBadges.filter { it !in reg }
    }
    val neckCatalog = remember {
        GameCatalog.medals.filter {
            it.category == AwardCategory.MEDAL && it.isNeckMedallion
        }.sortedBy { it.precedenceOrder }
    }
    val medalRibbonCatalog = remember {
        GameCatalog.medals.filter {
            (it.category == AwardCategory.MEDAL || it.category == AwardCategory.RIBBON) && !it.isNeckMedallion
        }.sortedBy { it.precedenceOrder }
    }

    fun updateRequest(transform: (UniformTailorRequest) -> UniformTailorRequest) {
        val base = UniformTailorRequest.fromOperator(operator, effectiveKit).copy(callsign = callsign)
        onApply(transform(base).copy(wardrobeKit = effectiveKit))
    }

    fun updateOverrides(
        forceDressMode: Boolean = false,
        transform: (UniformTailorOverrides) -> UniformTailorOverrides
    ) {
        updateRequest { req ->
            req.copy(
                uniformMode = if (forceDressMode) UniformMode.DRESS_UNIFORM else req.uniformMode,
                tailorOverrides = transform(req.tailorOverrides)
            )
        }
    }

    fun toggleCategory(cat: TailorCategoryKey) {
        expandedCategories = if (cat in expandedCategories) {
            expandedCategories - cat
        } else {
            expandedCategories + cat
        }
    }

    TacticalPanelCard(
        modifier = modifier,
        borderColor = extra.brass.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TAILOR • ${effectiveKit.label.uppercase()} (${operator.rank.tier.shortLabel.uppercase()})",
                    style = MaterialTheme.typography.titleMedium,
                    color = extra.brassBright
                )
                Text(
                    text = "${effectiveKit.dutyHint} • Customize cuts, patches, cords, pins & medals",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.textMuted
                )
            }
            if (onClose != null) {
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Done")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        // Free Tailor Override Toggle (ensures player is never locked out of customization)
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (freeOverride) {
                extra.signalsContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            border = BorderStroke(1.dp, if (freeOverride) extra.signalsCyan else extra.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (freeOverride) "Free Custom Override: UNLOCKED" else "Free Custom Override: Regimental",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (freeOverride) extra.signalsCyan else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (freeOverride) {
                            "All coats, berets, gloves, sashes, shoulders & chest pin slots unlocked (★ MOS picks marked)"
                        } else {
                            "Follows MOS & rank regulations; toggle On anytime for unrestricted custom styling"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.textMuted
                    )
                }
                Switch(
                    checked = freeOverride,
                    onCheckedChange = { enabled ->
                        updateOverrides(forceDressMode = false) { it.copy(freeTailorOverride = enabled) }
                    },
                    modifier = Modifier.testTag("toggle_free_tailor_override")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = callsign,
                onValueChange = { callsign = it.uppercase().take(12) },
                label = { Text("Nameplate / Callsign") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("callsign_input")
            )
            FilledTonalButton(
                onClick = { updateRequest { it.copy(callsign = callsign) } },
                enabled = callsign.trim().isNotEmpty() && callsign.trim() != operator.callsign,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("save_uniform_button")
            ) {
                Text("Save")
            }
        }

        if (overrides != UniformTailorOverrides()) {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    updateRequest { req -> req.copy(tailorOverrides = UniformTailorOverrides()) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("reset_tailor_slots_button")
            ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reset Custom Slots to Auto Precedence")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 1. Coat, Camo & Headgear
            TailorAccordionSection(
                icon = Icons.Filled.Checkroom,
                title = "Coat, Camo & Headgear",
                summary = if (effectiveKit == WardrobeKit.FIELD) {
                    "${effectiveKit.shortLabel} • ${kitPreviewOp.camoPattern.label}"
                } else {
                    "${effectiveKit.shortLabel} • ${kitPreviewOp.dressStyle.shortLabel} • ${kitPreviewOp.headgearStyle.label}"
                },
                expanded = TailorCategoryKey.COAT_HEADGEAR in expandedCategories,
                onToggle = { toggleCategory(TailorCategoryKey.COAT_HEADGEAR) },
                testTag = "tailor_cat_coat"
            ) {
                val modeOptions = remember {
                    UniformMode.entries.map { mode ->
                        DropdownOptionItem(
                            value = mode,
                            title = mode.label,
                            subtitle = if (mode == UniformMode.DRESS_UNIFORM) {
                                "Ceremonial service or evening mess coat"
                            } else {
                                "Field ACU combat uniform & tactical kit"
                            },
                            primaryColor = if (mode == UniformMode.DRESS_UNIFORM) Color(0xFF1D314F) else Color(0xFF4F5D3B),
                            trimColor = Color(0xFFD8B24C)
                        )
                    }
                }
                SingleChoiceDropdown(
                    label = "Active Uniform View",
                    selectedText = kitPreviewOp.uniformMode.label,
                    selectedPrimaryColor = if (kitPreviewOp.uniformMode == UniformMode.DRESS_UNIFORM) Color(0xFF1D314F) else Color(0xFF4F5D3B),
                    selectedTrimColor = extra.brassBright,
                    options = modeOptions,
                    selectedValue = kitPreviewOp.uniformMode,
                    onSelect = { selected -> updateRequest { it.copy(uniformMode = selected) } },
                    fieldTestTag = "dropdown_uniform_mode"
                )
                DressStyleDropdown(
                    operator = kitPreviewOp,
                    ownedMedalIds = ownedMedalIds,
                    freeCustomOverride = freeOverride,
                    wardrobeKit = effectiveKit,
                    onSelect = { style ->
                        updateRequest { it.copy(dressStyle = style, uniformMode = UniformMode.DRESS_UNIFORM) }
                    },
                    testTag = "dropdown_dress_style"
                )
                HeadgearStyleDropdown(
                    operator = kitPreviewOp,
                    ownedMedalIds = ownedMedalIds,
                    freeCustomOverride = freeOverride,
                    wardrobeKit = effectiveKit,
                    onSelect = { hg ->
                        updateRequest {
                            it.copy(
                                headgearStyle = hg ?: it.headgearStyle,
                                headgearTouched = true,
                                headgearAuto = hg == null,
                                uniformMode = UniformMode.DRESS_UNIFORM
                            )
                        }
                    },
                    testTag = "dropdown_headgear_style"
                )
                CamoPatternDropdown(
                    selectedPattern = kitPreviewOp.camoPattern,
                    onSelect = { camo ->
                        updateRequest { it.copy(camoPattern = camo, wardrobeKit = WardrobeKit.FIELD, uniformMode = UniformMode.FIELD_TACTICAL) }
                    },
                    testTag = "dropdown_camo_pattern"
                )
                // Appearance: Auto keeps the soldier's stable look derived from their id
                val autoSkin = operator.copy(skinToneOverride = null).skinTone
                val skinOptions = remember(autoSkin) {
                    listOf(
                        DropdownOptionItem<SkinTone?>(
                            value = null,
                            title = "Auto (${autoSkin.label})",
                            subtitle = "Stays the same for this soldier",
                            primaryColor = Color(autoSkin.toneHex),
                            trimColor = extra.signalsCyan,
                            testTag = "dropdown_skin_tone_opt_auto"
                        )
                    ) + SkinTone.entries.map { tone ->
                        DropdownOptionItem<SkinTone?>(
                            value = tone,
                            title = tone.label,
                            primaryColor = Color(tone.toneHex),
                            trimColor = Color(tone.shadowHex),
                            testTag = "dropdown_skin_tone_opt_${tone.name.lowercase()}"
                        )
                    }
                }
                SingleChoiceDropdown(
                    label = "Skin Tone",
                    selectedText = operator.skinToneOverride?.label ?: "Auto (${autoSkin.label})",
                    selectedPrimaryColor = Color(operator.skinTone.toneHex),
                    selectedTrimColor = Color(operator.skinTone.shadowHex),
                    options = skinOptions,
                    selectedValue = operator.skinToneOverride,
                    onSelect = { tone -> updateRequest { it.copy(skinToneOverride = tone) } },
                    fieldTestTag = "dropdown_skin_tone"
                )
                val autoBuild = operator.copy(bodyBuildOverride = null).bodyBuild
                val buildOptions = remember(autoBuild) {
                    listOf(
                        DropdownOptionItem<BodyBuild?>(
                            value = null,
                            title = "Auto (${autoBuild.label})",
                            subtitle = "Stays the same for this soldier",
                            primaryColor = Color.Transparent,
                            trimColor = extra.signalsCyan,
                            testTag = "dropdown_body_build_opt_auto"
                        )
                    ) + BodyBuild.entries.map { build ->
                        DropdownOptionItem<BodyBuild?>(
                            value = build,
                            title = build.label,
                            primaryColor = Color.Transparent,
                            trimColor = extra.borderSubtle,
                            testTag = "dropdown_body_build_opt_${build.name.lowercase()}"
                        )
                    }
                }
                SingleChoiceDropdown(
                    label = "Build",
                    selectedText = operator.bodyBuildOverride?.label ?: "Auto (${autoBuild.label})",
                    selectedPrimaryColor = Color.Transparent,
                    selectedTrimColor = extra.borderSubtle,
                    options = buildOptions,
                    selectedValue = operator.bodyBuildOverride,
                    onSelect = { build -> updateRequest { it.copy(bodyBuildOverride = build) } },
                    fieldTestTag = "dropdown_body_build"
                )
            }

            // 2. Unit Patches & Shoulder Tabs
            val autoUnitPatch = UniformRegaliaRules.resolveUnitPatch(
                operator.copy(tailorOverrides = overrides.copy(unitPatch = UnitPatchStyle.AUTO_MOS)),
                squads
            )
            val patchLabel = if (overrides.unitPatch == UnitPatchStyle.AUTO_MOS) autoUnitPatch.label else overrides.unitPatch.label
            TailorAccordionSection(
                icon = Icons.Filled.Shield,
                title = "Unit Patches & Shoulder Tabs",
                summary = "$patchLabel • ${wornSummary.shoulderTabs.size}/${TacticalCalculator.MAX_WORN_SHOULDER_TABS} Tabs",
                expanded = TailorCategoryKey.PATCHES_TABS in expandedCategories,
                onToggle = { toggleCategory(TailorCategoryKey.PATCHES_TABS) },
                testTag = "tailor_cat_patches"
            ) {
                UnitPatchDropdown(
                    selectedPatch = overrides.unitPatch,
                    autoPatch = autoUnitPatch,
                    isHqStaff = operator.status == OperatorStatus.HQ_STAFF,
                    onSelect = { patch ->
                        updateOverrides(forceDressMode = false) { it.copy(unitPatch = patch) }
                    },
                    testTag = "dropdown_unit_patch"
                )
                CombatPatchDropdown(
                    selectedPatch = overrides.combatPatch,
                    earnedPatches = UniformRegaliaRules.earnedCombatPatches(operator),
                    onSelect = { patch ->
                        updateOverrides(forceDressMode = false) { it.copy(combatPatch = patch) }
                    },
                    testTag = "dropdown_combat_patch"
                )
                RegaliaSlotDropdown(
                    label = "Shoulder Tab (Slot 1)",
                    slotValue = overrides.tabSlot1,
                    candidateMedals = tabCatalog,
                    ownedMedalIds = ownedMedalIds,
                    autoTitle = "Auto (Highest Earned)",
                    autoHint = "Wears the highest earned tab (SF, Ranger, Sapper, Airborne)",
                    noneSubtitle = "Leave this tab slot empty",
                    recommendedIds = primaryTabIds,
                    otherSelectedIds = setOf(overrides.tabSlot2, overrides.tabSlot3),
                    onSelect = { v -> updateOverrides { it.copy(tabSlot1 = v) } },
                    testTag = "dropdown_tab_slot_1"
                )
                RegaliaSlotDropdown(
                    label = "Shoulder Tab (Slot 2)",
                    slotValue = overrides.tabSlot2,
                    candidateMedals = tabCatalog,
                    ownedMedalIds = ownedMedalIds,
                    autoTitle = "Auto (Next Earned)",
                    autoHint = "Wears the next earned tab, if any",
                    noneSubtitle = "Leave this tab slot empty",
                    recommendedIds = primaryTabIds,
                    otherSelectedIds = setOf(overrides.tabSlot1, overrides.tabSlot3),
                    onSelect = { v -> updateOverrides { it.copy(tabSlot2 = v) } },
                    testTag = "dropdown_tab_slot_2"
                )
                RegaliaSlotDropdown(
                    label = "Shoulder Tab (Slot 3)",
                    slotValue = overrides.tabSlot3,
                    candidateMedals = tabCatalog,
                    ownedMedalIds = ownedMedalIds,
                    autoTitle = "Auto (Next Earned)",
                    autoHint = "Wears a third earned tab, if any",
                    noneSubtitle = "Leave this tab slot empty",
                    recommendedIds = primaryTabIds,
                    otherSelectedIds = setOf(overrides.tabSlot1, overrides.tabSlot2),
                    onSelect = { v -> updateOverrides { it.copy(tabSlot3 = v) } },
                    testTag = "dropdown_tab_slot_3"
                )
                Text(
                    text = "Tabs always stack in regulation order: Special Forces, Ranger, Sapper, Airborne.",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.signalsCyan
                )
                BooleanChoiceDropdown(
                    label = "Left-Cuff Service Hash Stripes",
                    selectedValue = overrides.showServiceStripes,
                    trueTitle = "Shown on Lower Left Sleeve",
                    trueSubtitle = "Displays gold diagonal hash stripes per 2 ops completed",
                    falseTitle = "Hidden (Clean Sleeve)",
                    falseSubtitle = "Omit diagonal service stripes on dress coat",
                    primaryTrueColor = extra.brassBright,
                    onSelect = { v -> updateOverrides { it.copy(showServiceStripes = v) } },
                    testTag = "dropdown_service_stripes"
                )
                BooleanChoiceDropdown(
                    label = "Right-Cuff Overseas Service Bars",
                    selectedValue = overrides.showOverseasBars,
                    trueTitle = "Shown on Lower Right Sleeve",
                    trueSubtitle = "Horizontal gold combat deployment bars (up to 5)",
                    falseTitle = "Hidden (Clean Right Cuff)",
                    falseSubtitle = "Omit horizontal overseas bars on dress coat",
                    primaryTrueColor = extra.brassBright,
                    onSelect = { v -> updateOverrides { it.copy(showOverseasBars = v) } },
                    testTag = "dropdown_overseas_bars"
                )
            }

            // 3. Shoulder Cords & Aiguillettes
            val leftCordLabel = alphasSummary.leftCord?.shortName ?: "None"
            val rightCordLabel = alphasSummary.rightCord?.shortName ?: "None"
            TailorAccordionSection(
                icon = Icons.Filled.MilitaryTech,
                title = "Shoulder Cords & Aiguillettes",
                summary = if (effectiveKit == WardrobeKit.ALPHAS || effectiveKit == WardrobeKit.BRAVOS) {
                    "Left: $leftCordLabel • Right: $rightCordLabel"
                } else {
                    "Alphas & Bravos Only (Left: $leftCordLabel • Right: $rightCordLabel)"
                },
                expanded = TailorCategoryKey.CORDS in expandedCategories,
                onToggle = { toggleCategory(TailorCategoryKey.CORDS) },
                testTag = "tailor_cat_cords"
            ) {
                if (effectiveKit != WardrobeKit.ALPHAS && effectiveKit != WardrobeKit.BRAVOS) {
                    Text(
                        text = "Regulation note: ${effectiveKit.shortLabel} omits shoulder cords. Selections apply to Alphas and Bravos.",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.signalsCyan
                    )
                }
                val cordRequest = UniformTailorRequest.fromOperator(operator, WardrobeKit.ALPHAS)
                ShoulderCordDropdown(
                    label = CordShoulderSlot.LEFT_BRANCH.label,
                    isRightShoulder = false,
                    specialty = operator.specialty,
                    selectedCordId = cordRequest.equippedLeftCordId,
                    ownedMedalIds = ownedMedalIds,
                    otherShoulderCordId = alphasSummary.rightCord?.id,
                    freeCustomOverride = freeOverride,
                    autoCord = UniformRegaliaRules.autoCordForShoulder(operator, awardedDecorations, isRightShoulder = false),
                    onSelect = { cordId ->
                        updateRequest {
                            it.copy(equippedLeftCordId = cordId, uniformMode = UniformMode.DRESS_UNIFORM)
                        }
                    },
                    testTag = "dropdown_left_cord"
                )
                ShoulderCordDropdown(
                    label = CordShoulderSlot.RIGHT_STAFF_VALOR.label,
                    isRightShoulder = true,
                    specialty = operator.specialty,
                    selectedCordId = cordRequest.equippedRightCordId,
                    ownedMedalIds = ownedMedalIds,
                    otherShoulderCordId = alphasSummary.leftCord?.id,
                    freeCustomOverride = freeOverride,
                    autoCord = UniformRegaliaRules.autoCordForShoulder(operator, awardedDecorations, isRightShoulder = true),
                    onSelect = { cordId ->
                        updateRequest {
                            it.copy(equippedRightCordId = cordId, uniformMode = UniformMode.DRESS_UNIFORM)
                        }
                    },
                    testTag = "dropdown_right_cord"
                )
            }

            // 4. School Pins & Chest Badges
            val pinSummary = listOfNotNull(
                wornSummary.chestPinAboveRack?.shortName,
                wornSummary.chestPinLeftPocket?.shortName,
                wornSummary.chestPinRightPocket?.shortName
            ).joinToString(", ").ifEmpty { "None Worn" }
            TailorAccordionSection(
                icon = Icons.Filled.Verified,
                title = "School Pins & Chest Badges",
                summary = pinSummary,
                expanded = TailorCategoryKey.SCHOOL_PINS in expandedCategories,
                onToggle = { toggleCategory(TailorCategoryKey.SCHOOL_PINS) },
                testTag = "tailor_cat_pins"
            ) {
                RegaliaSlotDropdown(
                    label = "Pin Slot 1 • ${ChestBadgeSlot.ABOVE_RACK.label}",
                    slotValue = overrides.pinSlot1,
                    candidateMedals = aboveRackCatalog,
                    ownedMedalIds = ownedMedalIds,
                    autoTitle = "Auto (MOS Priority)",
                    autoHint = "${operator.specialty.roleTag} priority • ${ChestBadgeSlot.ABOVE_RACK.ruleHint}",
                    noneSubtitle = "Leave space above ribbon rack empty",
                    recommendedIds = UniformRegaliaRules.primaryBadgeIdsForSlot(
                        operator.specialty,
                        ChestBadgeSlot.ABOVE_RACK
                    ),
                    otherSelectedIds = setOf(overrides.pinSlot2, overrides.pinSlot3),
                    onSelect = { v -> updateOverrides { it.copy(pinSlot1 = v) } },
                    testTag = "dropdown_pin_slot_1"
                )
                RegaliaSlotDropdown(
                    label = "Pin Slot 2 • ${ChestBadgeSlot.LEFT_POCKET.label}",
                    slotValue = overrides.pinSlot2,
                    candidateMedals = leftPocketCatalog,
                    ownedMedalIds = ownedMedalIds,
                    autoTitle = "Auto (MOS Priority)",
                    autoHint = "${operator.specialty.roleTag} priority • ${ChestBadgeSlot.LEFT_POCKET.ruleHint}",
                    noneSubtitle = "Leave left pocket plain",
                    recommendedIds = UniformRegaliaRules.primaryBadgeIdsForSlot(
                        operator.specialty,
                        ChestBadgeSlot.LEFT_POCKET
                    ),
                    otherSelectedIds = setOf(overrides.pinSlot1, overrides.pinSlot3),
                    onSelect = { v -> updateOverrides { it.copy(pinSlot2 = v) } },
                    testTag = "dropdown_pin_slot_2"
                )
                RegaliaSlotDropdown(
                    label = "Pin Slot 3 • ${ChestBadgeSlot.RIGHT_POCKET.label}",
                    slotValue = overrides.pinSlot3,
                    candidateMedals = rightPocketCatalog,
                    ownedMedalIds = ownedMedalIds,
                    autoTitle = "Auto (MOS Priority)",
                    autoHint = "${operator.specialty.roleTag} priority • ${ChestBadgeSlot.RIGHT_POCKET.ruleHint}",
                    noneSubtitle = "Leave right pocket plain",
                    recommendedIds = UniformRegaliaRules.primaryBadgeIdsForSlot(
                        operator.specialty,
                        ChestBadgeSlot.RIGHT_POCKET
                    ),
                    otherSelectedIds = setOf(overrides.pinSlot1, overrides.pinSlot2),
                    onSelect = { v -> updateOverrides { it.copy(pinSlot3 = v) } },
                    testTag = "dropdown_pin_slot_3"
                )
                MarksmanshipBadgeDropdown(
                    selectedTier = overrides.marksmanshipBadge,
                    resolvedTier = resolvedMarksmanship,
                    onSelect = { tier -> updateOverrides { it.copy(marksmanshipBadge = tier) } },
                    testTag = "dropdown_marksmanship"
                )
            }

            // 5. Medals, Ribbons & Neck Honor
            val rackCountLabel = when (kitPreviewOp.dressDisplayMode) {
                DressDisplayMode.RIBBONS -> "${wornSummary.ribbonRack.size} Ribbons"
                DressDisplayMode.FULL_MEDALS -> if (effectiveKit == WardrobeKit.MESS) {
                    "${wornSummary.hangingMedals.size} Mini Medals"
                } else {
                    "${wornSummary.hangingMedals.size} Full Medals"
                }
                DressDisplayMode.CLEAN -> "No Chest Rack"
            }
            TailorAccordionSection(
                icon = Icons.Filled.WorkspacePremium,
                title = "Medals, Ribbons & Neck Honor",
                summary = "${kitPreviewOp.dressDisplayMode.label} ($rackCountLabel)",
                expanded = TailorCategoryKey.MEDALS_RIBBONS in expandedCategories,
                onToggle = { toggleCategory(TailorCategoryKey.MEDALS_RIBBONS) },
                testTag = "tailor_cat_medals"
            ) {
                val displayOptions = remember(effectiveKit) {
                    DressDisplayMode.entries.map { dm ->
                        val enabledForKit = when (effectiveKit) {
                            WardrobeKit.BRAVOS -> dm != DressDisplayMode.FULL_MEDALS
                            WardrobeKit.MESS -> dm != DressDisplayMode.RIBBONS
                            WardrobeKit.ALPHAS, WardrobeKit.FIELD -> true
                        }
                        DropdownOptionItem(
                            value = dm,
                            title = if (effectiveKit == WardrobeKit.MESS && dm == DressDisplayMode.FULL_MEDALS) {
                                "Miniature Medals"
                            } else {
                                dm.label
                            },
                            subtitle = when (dm) {
                                DressDisplayMode.RIBBONS -> if (enabledForKit) {
                                    "AR 670-1 3-per-row centered ribbon rack (up to 9)"
                                } else {
                                    "Evening Mess uses anodized Miniature Medals instead of ribbons"
                                }
                                DressDisplayMode.FULL_MEDALS -> when {
                                    !enabledForKit -> "Service Bravos wears Ribbon Rack only"
                                    effectiveKit == WardrobeKit.MESS -> "Anodized miniature medals pinned on mess lapel"
                                    else -> "Anodized full-size hanging medals (up to 4)"
                                }
                                DressDisplayMode.CLEAN -> "Hide chest ribbons & hanging medals"
                            },
                            primaryColor = when (dm) {
                                DressDisplayMode.RIBBONS -> Color(0xFF2C6FB8)
                                DressDisplayMode.FULL_MEDALS -> Color(0xFFD8B24C)
                                DressDisplayMode.CLEAN -> Color.Transparent
                            },
                            trimColor = Color(0xFFD8B24C),
                            enabled = enabledForKit
                        )
                    }
                }
                SingleChoiceDropdown(
                    label = "Chest Decoration Display",
                    selectedText = if (effectiveKit == WardrobeKit.MESS && kitPreviewOp.dressDisplayMode == DressDisplayMode.FULL_MEDALS) {
                        "Miniature Medals"
                    } else {
                        kitPreviewOp.dressDisplayMode.label
                    },
                    selectedPrimaryColor = when (kitPreviewOp.dressDisplayMode) {
                        DressDisplayMode.RIBBONS -> Color(0xFF2C6FB8)
                        DressDisplayMode.FULL_MEDALS -> Color(0xFFD8B24C)
                        DressDisplayMode.CLEAN -> Color.Transparent
                    },
                    selectedTrimColor = extra.brassBright,
                    options = displayOptions,
                    selectedValue = kitPreviewOp.dressDisplayMode,
                    onSelect = { dm ->
                        updateRequest { it.copy(dressDisplayMode = dm, uniformMode = UniformMode.DRESS_UNIFORM) }
                    },
                    fieldTestTag = "dropdown_dress_display_mode"
                )
                RegaliaSlotDropdown(
                    label = "Collar Neck Medallion",
                    slotValue = overrides.neckSlot,
                    candidateMedals = neckCatalog,
                    ownedMedalIds = ownedMedalIds,
                    autoHint = "Wears Sovereign Star neck ribbon if awarded",
                    noneSubtitle = "Hide collar neck medallion",
                    onSelect = { v -> updateOverrides { it.copy(neckSlot = v) } },
                    testTag = "dropdown_neck_slot"
                )
                RegaliaSlotDropdown(
                    label = "Featured Medal / Ribbon Slot 1",
                    slotValue = overrides.medalSlot1,
                    candidateMedals = medalRibbonCatalog,
                    ownedMedalIds = ownedMedalIds,
                    autoHint = "Lead top-precedence medal or ribbon",
                    noneSubtitle = "Omit Slot 1",
                    otherSelectedIds = setOf(overrides.medalSlot2, overrides.medalSlot3, overrides.medalSlot4),
                    onSelect = { v -> updateOverrides { it.copy(medalSlot1 = v) } },
                    testTag = "dropdown_medal_slot_1"
                )
                RegaliaSlotDropdown(
                    label = "Featured Medal / Ribbon Slot 2",
                    slotValue = overrides.medalSlot2,
                    candidateMedals = medalRibbonCatalog,
                    ownedMedalIds = ownedMedalIds,
                    autoHint = "Second medal or ribbon by precedence",
                    noneSubtitle = "Omit Slot 2",
                    otherSelectedIds = setOf(overrides.medalSlot1, overrides.medalSlot3, overrides.medalSlot4),
                    onSelect = { v -> updateOverrides { it.copy(medalSlot2 = v) } },
                    testTag = "dropdown_medal_slot_2"
                )
                RegaliaSlotDropdown(
                    label = "Featured Medal / Ribbon Slot 3",
                    slotValue = overrides.medalSlot3,
                    candidateMedals = medalRibbonCatalog,
                    ownedMedalIds = ownedMedalIds,
                    autoHint = "Third medal or ribbon by precedence",
                    noneSubtitle = "Omit Slot 3",
                    otherSelectedIds = setOf(overrides.medalSlot1, overrides.medalSlot2, overrides.medalSlot4),
                    onSelect = { v -> updateOverrides { it.copy(medalSlot3 = v) } },
                    testTag = "dropdown_medal_slot_3"
                )
                RegaliaSlotDropdown(
                    label = "Featured Medal / Ribbon Slot 4",
                    slotValue = overrides.medalSlot4,
                    candidateMedals = medalRibbonCatalog,
                    ownedMedalIds = ownedMedalIds,
                    autoHint = "Fourth medal or ribbon (remaining rack auto-fills)",
                    noneSubtitle = "Omit Slot 4",
                    otherSelectedIds = setOf(overrides.medalSlot1, overrides.medalSlot2, overrides.medalSlot3),
                    onSelect = { v -> updateOverrides { it.copy(medalSlot4 = v) } },
                    testTag = "dropdown_medal_slot_4"
                )
            }

            // 6. Belt, Sash, Gloves & Unit Citation
            val handsText = if (kitPreviewOp.hasWhiteGloves) "White Gloves" else "Bare Hands"
            val sashText = if (kitPreviewOp.hasDressSash) "Sash/Sam Browne" else "Standard Belt"
            TailorAccordionSection(
                icon = Icons.Filled.AutoAwesome,
                title = "Belt, Sash, Gloves & Citations",
                summary = "$handsText • $sashText",
                expanded = TailorCategoryKey.BELT_GLOVES_CITATIONS in expandedCategories,
                onToggle = { toggleCategory(TailorCategoryKey.BELT_GLOVES_CITATIONS) },
                testTag = "tailor_cat_accents"
            ) {
                BooleanChoiceDropdown(
                    label = "Hands & Parade Gloves",
                    selectedValue = kitPreviewOp.hasWhiteGloves,
                    trueTitle = "White Parade Gloves",
                    trueSubtitle = if (effectiveKit == WardrobeKit.MESS) {
                        "Formal evening mess white gloves"
                    } else {
                        "Formal white dress parade gloves"
                    },
                    falseTitle = "Bare Hands",
                    falseSubtitle = if (effectiveKit == WardrobeKit.BRAVOS) {
                        "Bravos short-sleeve shirt always shows bare forearms & hands"
                    } else {
                        "Natural bare hands with crisp dress shirt cuffs"
                    },
                    primaryTrueColor = Color(0xFFF3F5F8),
                    enabledTrue = when (effectiveKit) {
                        WardrobeKit.BRAVOS, WardrobeKit.FIELD -> freeOverride
                        WardrobeKit.MESS -> true
                        WardrobeKit.ALPHAS -> freeOverride || glovesElig.eligible || kitPreviewOp.hasWhiteGloves
                    },
                    lockedTrueReason = if (effectiveKit == WardrobeKit.BRAVOS) {
                        "Short-sleeve Bravos omits gloves"
                    } else {
                        glovesElig.reasonText
                    },
                    onSelect = { gloves ->
                        updateRequest { it.copy(hasWhiteGloves = gloves, uniformMode = UniformMode.DRESS_UNIFORM) }
                    },
                    testTag = "dropdown_gloves"
                )
                BooleanChoiceDropdown(
                    label = "Waist Sash / Sam Browne Belt",
                    selectedValue = kitPreviewOp.hasDressSash,
                    trueTitle = if (operator.rank.isOfficer) "Officer Sam Browne Cross-Strap" else "Ceremonial Crimson Waist Sash",
                    trueSubtitle = "Restricted to HQ Staff & Squad Leaders in Service Alphas",
                    falseTitle = "Standard Garrison Belt Only",
                    falseSubtitle = "Standard brass-buckle service belt",
                    primaryTrueColor = Color(0xFF9E1F2A),
                    enabledTrue = (effectiveKit == WardrobeKit.ALPHAS || freeOverride) &&
                        (freeOverride || sashElig.eligible || kitPreviewOp.hasDressSash),
                    lockedTrueReason = if (effectiveKit != WardrobeKit.ALPHAS) {
                        "Worn on Service Alphas only (HQ Staff & Squad Leaders)"
                    } else {
                        sashElig.reasonText
                    },
                    onSelect = { sash ->
                        updateRequest { it.copy(hasDressSash = sash, uniformMode = UniformMode.DRESS_UNIFORM) }
                    },
                    testTag = "dropdown_sash"
                )
                LeaderPinDropdown(
                    selectedOverride = overrides.leaderPinOverride,
                    autoIsLeader = autoIsLeader,
                    onSelect = { v -> updateOverrides { it.copy(leaderPinOverride = v) } },
                    testTag = "dropdown_leader_pin"
                )
                BooleanChoiceDropdown(
                    label = "Right-Chest Unit Citation Bar",
                    selectedValue = overrides.showUnitCitation,
                    trueTitle = "Gold-Framed Unit Citation Worn",
                    trueSubtitle = "Displays Presidential/Valorous Unit Citation when qualified",
                    falseTitle = "Hidden (No Unit Citation Bar)",
                    falseSubtitle = "Leave right chest above pocket clear",
                    primaryTrueColor = Color(0xFF1D4E89),
                    onSelect = { v -> updateOverrides { it.copy(showUnitCitation = v) } },
                    testTag = "dropdown_unit_citation"
                )
            }
        }
    }
}

@Composable
private fun TailorAccordionSection(
    icon: ImageVector,
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    testTag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val extra = LocalAegisColors.current
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
        border = BorderStroke(
            1.dp,
            if (expanded) extra.brass.copy(alpha = 0.7f) else extra.borderSubtle
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag(testTag),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (expanded) extra.brassBright else extra.signalsCyan,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (expanded) extra.brassBright else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.textMuted,
                        maxLines = 1
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = extra.brassBright
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}
