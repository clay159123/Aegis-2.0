package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.TacticalCalculator
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import com.example.ui.theme.LocalAegisColors

private enum class InspectionCamera(
    val label: String,
    val tag: String,
    val scale: Float,
    val txFrac: Float,
    val tyFrac: Float
) {
    FULL("Full", "zoom_view_full", 1f, 0f, 0f),
    CHEST("Chest & Pins", "zoom_view_chest", 2.0f, 0f, 0.14f),
    LEFT_SLEEVE("Left Sleeve", "zoom_view_left_sleeve", 2.15f, -0.34f, -0.05f),
    RIGHT_SLEEVE("Right Sleeve", "zoom_view_right_sleeve", 2.15f, 0.34f, -0.05f)
}

private enum class UniformHotspot(val label: String, val tag: String) {
    NAMEPLATE("Nameplate & Leader", "hotspot_nameplate"),
    COMBAT_PATCH("Combat Patch", "hotspot_combat_patch"),
    OVERSEAS_BARS("Overseas Bars", "hotspot_overseas_bars"),
    MARKSMANSHIP("Marksmanship", "hotspot_marksmanship"),
    CORDS("Shoulder Cords", "hotspot_cords"),
    CHEST_RACK("Medals & Devices", "hotspot_chest_rack")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UniformInspectionDialog(
    operator: Operator,
    equippedGear: List<GearItem>,
    awardedMedals: List<MedalDefinition>,
    allCitations: List<AwardedMedal> = emptyList(),
    squads: List<TacticalSquad> = emptyList(),
    kitOverride: WardrobeKit? = null,
    onToggleUniformMode: (() -> Unit)? = null,
    onToggleDressDisplayMode: (() -> Unit)? = null,
    onInspectAward: ((MedalDefinition) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val extra = LocalAegisColors.current
    var camera by remember(operator.id) { mutableStateOf(InspectionCamera.FULL) }
    var selectedHotspot by remember(operator.id) { mutableStateOf<UniformHotspot?>(null) }

    BackHandler { onDismiss() }

    val distinctAwards = remember(awardedMedals) { awardedMedals.distinctBy { it.id } }
    val effectiveOp = remember(operator, kitOverride, squads, distinctAwards) {
        if (kitOverride != null) {
            TacticalCalculator.resolveKitAppearance(
                operator = operator,
                kit = kitOverride,
                squads = squads,
                ownedMedalIds = distinctAwards.map { it.id }.toSet()
            )
        } else {
            operator
        }
    }
    val effectiveCitations = remember(allCitations, operator.id, awardedMedals) {
        val opCits = allCitations.filter { it.operatorId == operator.id }
        if (opCits.isNotEmpty()) {
            opCits
        } else {
            awardedMedals.mapIndexed { idx, medal ->
                AwardedMedal(
                    id = "fallback_$idx",
                    operatorId = operator.id,
                    medalId = medal.id,
                    citationNote = "",
                    awardedAtMillis = idx.toLong()
                )
            }
        }
    }

    val wornRegalia = TacticalCalculator.resolveWornRegalia(effectiveOp, distinctAwards)
    val leftCord = wornRegalia.leftCord
    val rightCord = wornRegalia.rightCord
    val serviceStripes = if (effectiveOp.tailorOverrides.showServiceStripes) {
        (effectiveOp.missionsCompleted / 2).coerceIn(0, 4)
    } else {
        0
    }
    val overseasBars = UniformRegaliaRules.resolveOverseasBars(effectiveOp)
    val combatPatch = UniformRegaliaRules.resolveCombatPatch(effectiveOp)
    val marksmanshipTier = UniformRegaliaRules.resolveMarksmanshipTier(effectiveOp, equippedGear)
    val leaderPinShown = UniformRegaliaRules.isLeaderPinShown(effectiveOp, squads)
    val operatorSquad = squads.find { effectiveOp.id in it.memberIds }

    val wornList = distinctAwards.filter { it.id in wornRegalia.wornIds }.sortedBy { it.precedenceOrder }
    val recordOnlyList = distinctAwards.filter { it.id !in wornRegalia.wornIds }.sortedBy { it.precedenceOrder }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "INSPECTION • ${effectiveOp.activeWardrobeKit.label.uppercase()} • ${effectiveOp.rank.tier.label.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.signalsCyan
                )
                Text(
                    text = "${effectiveOp.rank.code} ${effectiveOp.callsign} (${effectiveOp.rank.title})",
                    style = MaterialTheme.typography.titleLarge,
                    color = extra.brassBright
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(268.dp)
                        .height(348.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clipToBounds()
                ) {
                    OperatorUniformPreview(
                        operator = operator,
                        equippedGear = equippedGear,
                        awardedMedals = awardedMedals,
                        allCitations = allCitations,
                        squads = squads,
                        kitOverride = kitOverride,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = camera.scale
                                scaleY = camera.scale
                                translationX = size.width * camera.txFrac
                                translationY = size.height * camera.tyFrac
                            }
                    )
                }

                // Camera Zoom Switcher
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    InspectionCamera.entries.forEach { cam ->
                        FilterChip(
                            selected = camera == cam,
                            onClick = { camera = cam },
                            leadingIcon = if (camera == cam) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.CenterFocusStrong,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            label = { Text(cam.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .testTag(cam.tag)
                        )
                    }
                }

                // Tap-to-Inspect Hotspot Chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    UniformHotspot.entries.forEach { spot ->
                        val active = selectedHotspot == spot
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedHotspot = if (active) null else spot
                                camera = when (spot) {
                                    UniformHotspot.COMBAT_PATCH, UniformHotspot.OVERSEAS_BARS -> InspectionCamera.RIGHT_SLEEVE
                                    UniformHotspot.NAMEPLATE, UniformHotspot.MARKSMANSHIP, UniformHotspot.CHEST_RACK -> InspectionCamera.CHEST
                                    UniformHotspot.CORDS -> InspectionCamera.FULL
                                }
                            },
                            label = { Text(spot.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .testTag(spot.tag)
                        )
                    }
                }

                selectedHotspot?.let { spot ->
                    val detailText = when (spot) {
                        UniformHotspot.NAMEPLATE -> buildString {
                            append("Engraved right-chest plate reads \"${operator.callsign.uppercase()}\". ")
                            if (leaderPinShown) {
                                append("Surmounted by brass Squad Leader Star Crest")
                                if (operatorSquad != null) append(" (${operatorSquad.name})")
                                append(".")
                            } else {
                                append("No Squad Leader pin currently worn.")
                            }
                        }
                        UniformHotspot.COMBAT_PATCH -> if (combatPatch != UnitPatchStyle.NONE) {
                            "Right-shoulder Combat Patch: ${combatPatch.label} (worn by veterans of deployed combat operations)."
                        } else {
                            "Right shoulder sleeve currently bare — deploy on a Moderate+ combat mission or set in Tailor."
                        }
                        UniformHotspot.OVERSEAS_BARS -> if (overseasBars > 0) {
                            "$overseasBars gold Overseas Service Bar(s) on right cuff; $serviceStripes diagonal Service Hash Stripe(s) on left cuff."
                        } else {
                            "No Overseas Service Bars earned yet (complete combat deployments or customize)."
                        }
                        UniformHotspot.MARKSMANSHIP -> if (marksmanshipTier != MarksmanshipBadgeTier.NONE) {
                            "${marksmanshipTier.label} Qualification Badge with Rifle/Carbine clasp pinned on the left breast pocket."
                        } else {
                            "No Marksmanship Qualification Badge pinned."
                        }
                        UniformHotspot.CORDS -> buildString {
                            append("Left Shoulder: ${leftCord?.name ?: "None"} • Right Shoulder: ${rightCord?.name ?: "None"}.")
                            if (operator.tailorOverrides.freeTailorOverride) {
                                append(" Free Tailor Override active.")
                            }
                        }
                        UniformHotspot.CHEST_RACK -> {
                            val repeatSummary = wornList.mapNotNull { m ->
                                val dev = UniformRegaliaRules.resolveAwardDevices(operator, m.id, effectiveCitations)
                                val summary = dev.summaryLabel
                                if (summary != null) {
                                    "${m.shortName}: $summary"
                                } else null
                            }
                            if (repeatSummary.isEmpty()) {
                                "Displaying ${wornList.size} earned decoration(s) in military precedence order."
                            } else {
                                "Repeat & Valor Devices: ${repeatSummary.joinToString(" • ")}"
                            }
                        }
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = extra.signalsContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, extra.signalsCyan.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hotspot_detail_card")
                    ) {
                        Text(
                            text = detailText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (onToggleUniformMode != null || onToggleDressDisplayMode != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onToggleUniformMode != null) {
                            OutlinedButton(
                                onClick = onToggleUniformMode,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 44.dp)
                            ) {
                                Text(
                                    if (operator.uniformMode == UniformMode.DRESS_UNIFORM) "View Field ACU" else "View Dress",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        if (onToggleDressDisplayMode != null) {
                            OutlinedButton(
                                onClick = onToggleDressDisplayMode,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 44.dp)
                            ) {
                                Text(
                                    if (operator.dressDisplayMode == DressDisplayMode.FULL_MEDALS) "Show Ribbons" else "Show Medals",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                // Detailed Regalia Spec Card
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, extra.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "TURN-OUT SPECIFICATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.brassBright
                        )
                        InspectionSpecRow("Wardrobe Kit", effectiveOp.activeWardrobeKit.label)
                        InspectionSpecRow("Uniform Cut", "${effectiveOp.dressStyle.label} (${effectiveOp.dressDisplayMode.label})")
                        InspectionSpecRow("Rank & Grade", "${effectiveOp.rank.title} (${effectiveOp.rank.payGrade})")
                        InspectionSpecRow("Unit / Combat SSI", "${UniformRegaliaRules.resolveUnitPatch(effectiveOp, squads).label} / ${combatPatch.label}")
                        InspectionSpecRow("Squad Leader Pin", if (leaderPinShown) "Worn above \"${effectiveOp.callsign}\" nameplate" else "None")
                        InspectionSpecRow(
                            "Shoulder Tabs",
                            wornRegalia.shoulderTabs.joinToString(" • ") { it.shortName }.ifEmpty { "None worn" }
                        )
                        InspectionSpecRow(
                            "School & Rifle Pins",
                            (wornRegalia.chestBadges.map { it.shortName } + listOfNotNull(marksmanshipTier.label.takeIf { marksmanshipTier != MarksmanshipBadgeTier.NONE && effectiveOp.activeWardrobeKit != WardrobeKit.MESS }))
                                .joinToString(" • ").ifEmpty { "None worn" }
                        )
                        InspectionSpecRow("Headgear", effectiveOp.headgearStyle.label)
                        InspectionSpecRow("Left / Right Cords", "${leftCord?.shortName ?: "None"} / ${rightCord?.shortName ?: "None"}")
                        InspectionSpecRow(
                            "Ceremonial Accents",
                            buildList {
                                if (effectiveOp.hasDressSash) {
                                    add(if (effectiveOp.rank.tier == RankTier.FLAG_OFFICER) "Crimson General Sash" else "Sam Browne / Waist Sash")
                                }
                                if (effectiveOp.hasWhiteGloves) add("White Parade Gloves") else add("Bare Hands")
                                if (serviceStripes > 0) add("$serviceStripes Hash Stripe(s)")
                                if (overseasBars > 0) add("$overseasBars Overseas Bar(s)")
                                if (effectiveOp.tailorOverrides.freeTailorOverride) add("Free Tailor Override")
                            }.joinToString(" • ")
                        )
                    }
                }

                // Interactive Worn vs. Service Record Decorations
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (distinctAwards.isEmpty()) {
                            "WORN ON UNIFORM (NONE)"
                        } else {
                            "WORN ON UNIFORM (${wornList.size}/${distinctAwards.size}) • TAP TO INSPECT"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.signalsCyan
                    )
                    Text(
                        text = "Unlimited earned career honors (all grant full stats) • Customize worn pins, cords, tabs, patches & medals in Tailor",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.textMuted
                    )
                    if (distinctAwards.isEmpty()) {
                        Text(
                            text = "No decorations pinned yet. Deploy or complete school drills to earn honors.",
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.textMuted
                        )
                    } else {
                        wornList.forEach { medal ->
                            val dev = UniformRegaliaRules.resolveAwardDevices(operator, medal.id, effectiveCitations)
                            InspectionAwardRow(
                                medal = medal,
                                awardCount = dev.awardCount,
                                deviceLabel = dev.summaryLabel,
                                statusLabel = "WORN",
                                onInspectAward = onInspectAward
                            )
                        }
                        if (recordOnlyList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "SERVICE RECORD ARCHIVE (${recordOnlyList.size}) • FULL BONUSES ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.brassBright
                            )
                            recordOnlyList.forEach { medal ->
                                val dev = UniformRegaliaRules.resolveAwardDevices(operator, medal.id, effectiveCitations)
                                InspectionAwardRow(
                                    medal = medal,
                                    awardCount = dev.awardCount,
                                    deviceLabel = dev.summaryLabel,
                                    statusLabel = "RECORD",
                                    onInspectAward = onInspectAward
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("close_uniform_inspection")
            ) {
                Text("Dismiss Inspection")
            }
        }
    )
}

@Composable
private fun InspectionSpecRow(label: String, value: String) {
    val extra = LocalAegisColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = extra.textMuted,
            modifier = Modifier.width(102.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InspectionAwardRow(
    medal: MedalDefinition,
    awardCount: Int,
    deviceLabel: String?,
    statusLabel: String,
    onInspectAward: ((MedalDefinition) -> Unit)?
) {
    val extra = LocalAegisColors.current
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, extra.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onInspectAward != null) {
                    Modifier.clickable { onInspectAward(medal) }
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AwardVisualThumbnail(medal = medal)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (awardCount > 1) "${medal.name} (×$awardCount)" else medal.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (medal.tier == MedalTier.SUPREME) extra.brassBright else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = buildString {
                        append("#${medal.precedenceOrder} • ${medal.category.label}")
                        if (!deviceLabel.isNullOrBlank()) append(" • $deviceLabel")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (!deviceLabel.isNullOrBlank()) extra.brassBright else extra.textMuted
                )
            }
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (statusLabel == "WORN") extra.signalsCyan else extra.textMuted
            )
        }
    }
}

