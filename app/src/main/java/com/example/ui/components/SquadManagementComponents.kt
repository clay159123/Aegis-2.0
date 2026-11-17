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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.SquadCohesionRules
import com.example.domain.model.*
import com.example.ui.theme.LocalAegisColors

@Composable
fun FieldSquadsSection(
    squads: List<TacticalSquad>,
    operators: List<Operator>,
    deployedOpIds: Set<String>,
    smartWardrobeEnabled: Boolean = true,
    onCreateSquadClick: () -> Unit,
    onEditSquadClick: (TacticalSquad) -> Unit,
    onApplySquadStandard: (String) -> Unit = {},
    onConductSquadInspection: (String) -> Unit = {},
    onSwitchSquadKit: (String, WardrobeKit) -> Unit = { _, _ -> }
) {
    val extra = LocalAegisColors.current
    val opsById = remember(operators) { operators.associateBy { it.id } }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = "Combat Squads (${squads.size})",
            subtitle = "$MIN_SQUAD_SIZE–$MAX_SQUAD_SIZE soldiers/squad • Full Strength at $FULL_STRENGTH_SQUAD_SIZE+ • Deploy together to build Cohesion",
            trailing = {
                FilledTonalButton(
                    onClick = onCreateSquadClick,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("create_squad_button")
                ) {
                    Icon(Icons.Filled.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Squad")
                }
            }
        )

        if (squads.isEmpty()) {
            TacticalPanelCard(borderColor = extra.borderSubtle) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "No Combat Squads Formed",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Form a $MIN_SQUAD_SIZE–$MAX_SQUAD_SIZE soldier squad with a designated Squad Leader to select the whole team in 1 tap and build mission Cohesion.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onCreateSquadClick,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("empty_form_squad_button")
                    ) {
                        Text("Form")
                    }
                }
            }
        } else {
            squads.forEach { squad ->
                val members = squad.memberIds.mapNotNull { opsById[it] }
                val leader = squad.leaderId?.let { opsById[it] }
                val readyCount = members.count {
                    it.id !in deployedOpIds && it.status == OperatorStatus.READY
                }
                val activePreviewBonus = SquadCohesionRules.evaluateActiveBonus(members, listOf(squad))
                val tier = squad.cohesionTier
                val cohesionColor = when (tier) {
                    SquadCohesionTier.ELITE, SquadCohesionTier.VETERAN -> extra.brassBright
                    SquadCohesionTier.SYNCHRONIZED, SquadCohesionTier.DRILLED -> extra.signalsCyan
                    SquadCohesionTier.FORMING -> MaterialTheme.colorScheme.primary
                }
                val unanimousKit = members.map { it.activeWardrobeKit }.distinct().singleOrNull()

                TacticalPanelCard(
                    borderColor = cohesionColor.copy(alpha = 0.55f),
                    modifier = Modifier.testTag("squad_card_${squad.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = squad.name.uppercase(),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = extra.brassBright
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = cohesionColor.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "${members.size}/$MAX_SQUAD_SIZE • $readyCount READY" +
                                            if (members.size >= FULL_STRENGTH_SQUAD_SIZE) " • FULL STRENGTH" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cohesionColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (leader != null) {
                                    "★ Squad Leader: ${leader.rank.code} ${leader.callsign} (${leader.specialty.roleTag})"
                                } else {
                                    "No Squad Leader designated — tap Manage to assign"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (leader != null) MaterialTheme.colorScheme.onSurface else extra.amberTelemetry
                            )
                        }
                        FilledTonalButton(
                            onClick = { onEditSquadClick(squad) },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("manage_squad_${squad.id}")
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Manage")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Cohesion Bar & Tier
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "COHESION ${squad.cohesionScore}% • ${tier.label.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = cohesionColor
                        )
                        Text(
                            text = buildString {
                                append("${squad.missionsTogether} Ops Together")
                                if (activePreviewBonus != null) {
                                    append(" • +${activePreviewBonus.efficiencyBonusPct}% Eff")
                                    if (activePreviewBonus.survivalBonusPct > 0) {
                                        append(" / +${activePreviewBonus.survivalBonusPct}% Surv")
                                    }
                                    if (activePreviewBonus.successOddsBonusPct > 0) {
                                        append(" / +${activePreviewBonus.successOddsBonusPct}% Odds")
                                    }
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.textMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { squad.cohesionProgressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(MaterialTheme.shapes.extraSmall),
                        color = cohesionColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )

                    if (members.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            members.forEach { member ->
                                val isLeader = member.id == squad.leaderId
                                val isDeployed = member.id in deployedOpIds || member.status == OperatorStatus.DEPLOYED
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = if (isLeader) {
                                        extra.brassContainer.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isLeader) extra.brass else extra.borderSubtle
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Text(
                                            text = if (isLeader) "★ LDR ${member.rank.code}" else member.rank.code,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isLeader) extra.brassBright else extra.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = member.callsign,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isDeployed) "DEPLOYED" else if (member.status == OperatorStatus.RECOVERING) "MEDBAY" else member.specialty.roleTag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isDeployed) extra.signalsCyan else extra.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        if (squad.hasUniformStandard) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = squad.standardSummaryParts.joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.brassBright,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (smartWardrobeEnabled) "SQUAD KIT (AUTO-DUTY ON)" else "EQUIP SQUAD KIT",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.textMuted
                            )
                            Text(
                                text = unanimousKit?.shortLabel?.let { "Set: $it" } ?: "Mixed Kits",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.signalsCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            WardrobeKit.entries.forEach { kit ->
                                val selected = unanimousKit == kit
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable { onSwitchSquadKit(squad.id, kit) }
                                        .testTag("squad_kit_switch_${squad.id}_${kit.name.lowercase()}"),
                                    shape = MaterialTheme.shapes.small,
                                    color = if (selected) {
                                        extra.signalsContainer.copy(alpha = 0.72f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) extra.signalsCyan else extra.borderSubtle
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = kit.shortLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (selected) extra.signalsCyan else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onApplySquadStandard(squad.id) },
                                modifier = Modifier
                                    .weight(1.1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("apply_squad_standard_${squad.id}"),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Filled.Checkroom, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Dress Standard", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { onConductSquadInspection(squad.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("inspect_squad_${squad.id}"),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Filled.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Inspect", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
