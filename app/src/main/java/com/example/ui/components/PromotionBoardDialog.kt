package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.*
import com.example.domain.model.*
import com.example.ui.theme.LocalAegisColors

@Composable
fun PromotionBoardDialog(
    operator: Operator,
    evaluation: PromotionBoardEvaluation,
    resources: ResourceBundle,
    onApprove: (PromotionEndorsementFocus) -> Unit,
    onDismiss: () -> Unit,
    equippedGear: List<GearItem> = emptyList(),
    awardedMedals: List<MedalDefinition> = emptyList(),
    allCitations: List<AwardedMedal> = emptyList(),
    squads: List<TacticalSquad> = emptyList()
) {
    val extra = LocalAegisColors.current
    val nextRank = evaluation.nextRank ?: return
    val canAfford = resources.credits >= evaluation.creditCost &&
        resources.prestige >= evaluation.prestigeCost
    var selectedFocus by remember(operator.id, nextRank) {
        mutableStateOf(PromotionEndorsementFocus.COMBAT_LEADERSHIP)
    }

    BackHandler { onDismiss() }

    val consensusColor = when (evaluation.consensus) {
        BoardConsensus.UNANIMOUS -> extra.signalsCyan
        BoardConsensus.FAVORABLE -> extra.brassBright
        BoardConsensus.SPLIT -> extra.amberTelemetry
        BoardConsensus.BLOCKED -> MaterialTheme.colorScheme.error
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Filled.Gavel,
                contentDescription = null,
                tint = extra.brassBright
            )
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${evaluation.currentRank.tier.shortLabel.uppercase()} → ${nextRank.tier.shortLabel.uppercase()} BOARD",
                    style = MaterialTheme.typography.labelSmall,
                    color = consensusColor
                )
                Text(
                    text = "${operator.callsign}: ${evaluation.currentRank.code} → ${nextRank.code} (${nextRank.payGrade})",
                    style = MaterialTheme.typography.titleLarge,
                    color = extra.brassBright
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag("promotion_board_dialog"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Board Consensus Banner
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = consensusColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, consensusColor.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = null,
                                tint = consensusColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = evaluation.consensus.label.uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                color = consensusColor
                            )
                        }
                        Text(
                            text = evaluation.consensus.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Authorization Cost: ${evaluation.creditCost.toInt()} CR • ${evaluation.prestigeCost} PRS",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (canAfford) extra.brassBright else MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Before -> After Uniform Preview
                val beforePreviewOp = remember(operator) {
                    operator.copy(uniformMode = UniformMode.DRESS_UNIFORM, activeWardrobeKit = WardrobeKit.ALPHAS)
                }
                val afterPreviewOp = remember(operator, nextRank, squads) {
                    val upgradedDress = when (nextRank.tier) {
                        RankTier.FLAG_OFFICER -> DressUniformStyle.FLAG_STAFF_FORMAL
                        RankTier.OFFICER, RankTier.WARRANT -> DressUniformStyle.OFFICER_SERVICE_BLUES
                        RankTier.SNCO, RankTier.NCO -> DressUniformStyle.NCO_PARADE_BLUES
                        RankTier.SOLDIER -> DressUniformStyle.ARMY_SERVICE_GREEN
                    }
                    val upgradedHeadgear = when (nextRank.tier) {
                        RankTier.FLAG_OFFICER -> HeadgearStyle.FLAG_GENERAL_CAP
                        RankTier.OFFICER, RankTier.WARRANT -> HeadgearStyle.OFFICER_PEAKED_CAP
                        RankTier.SNCO, RankTier.NCO -> HeadgearStyle.NCO_SERVICE_CAP
                        RankTier.SOLDIER -> HeadgearStyle.ENLISTED_SERVICE_CAP
                    }
                    val sashAllowed = TacticalCalculator.evaluateDressSashEligibility(operator, squads).eligible
                    operator.copy(
                        uniformMode = UniformMode.DRESS_UNIFORM,
                        activeWardrobeKit = WardrobeKit.ALPHAS,
                        xp = nextRank.xpRequired.coerceAtLeast(operator.xp),
                        authorizedTier = nextRank.tier,
                        warrantGrade = nextRank.warrantGrade,
                        dressStyle = upgradedDress,
                        headgearStyle = upgradedHeadgear,
                        hasDressSash = operator.hasDressSash && sashAllowed,
                        wardrobe = operator.wardrobe.withKit(
                            WardrobeKit.ALPHAS,
                            operator.wardrobe.alphas.copy(
                                dressStyle = upgradedDress,
                                headgearStyle = upgradedHeadgear
                            )
                        )
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, extra.borderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("promotion_uniform_preview")
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "CEREMONIAL PINNING PREVIEW (${operator.rank.code} → ${nextRank.code})",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.brassBright
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                OperatorUniformPreview(
                                    operator = beforePreviewOp,
                                    equippedGear = equippedGear,
                                    awardedMedals = awardedMedals,
                                    allCitations = allCitations,
                                    squads = squads,
                                    kitOverride = WardrobeKit.ALPHAS,
                                    modifier = Modifier
                                        .width(96.dp)
                                        .height(124.dp)
                                )
                                Text(
                                    text = "CURRENT (${operator.rank.code})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.textMuted
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Promotes to",
                                tint = extra.brassBright
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                OperatorUniformPreview(
                                    operator = afterPreviewOp,
                                    equippedGear = equippedGear,
                                    awardedMedals = awardedMedals,
                                    allCitations = allCitations,
                                    squads = squads,
                                    kitOverride = WardrobeKit.ALPHAS,
                                    modifier = Modifier
                                        .width(96.dp)
                                        .height(124.dp)
                                )
                                Text(
                                    text = "PINNING (${nextRank.code})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.signalsCyan
                                )
                            }
                        }
                        Text(
                            text = "Unlocks ${nextRank.tier.label} uniform cut, sleeve/epaulet insignia & ${afterPreviewOp.headgearStyle.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 1. Merit Prerequisites Checklist
                Text(
                    text = "MERIT PREREQUISITES",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.textMuted
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, extra.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        evaluation.meritChecks.forEach { check ->
                            val checkColor = if (check.satisfied) extra.signalsCyan else MaterialTheme.colorScheme.error
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("board_merit_${check.id}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (check.satisfied) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                                        contentDescription = if (check.satisfied) "Met" else "Locked",
                                        tint = checkColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = check.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = check.statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = checkColor
                                )
                            }
                        }
                    }
                }

                // 2. Board Selection Opinions
                Text(
                    text = "BOARD SELECTION OPINIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.textMuted
                )
                evaluation.opinions.forEachIndexed { idx, opinion ->
                    val stanceColor = when (opinion.stance) {
                        BoardStance.STRONGLY_ENDORSE -> extra.signalsCyan
                        BoardStance.ENDORSE -> extra.brassBright
                        BoardStance.RESERVATIONS -> extra.amberTelemetry
                        BoardStance.DEFER -> MaterialTheme.colorScheme.error
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, stanceColor.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("board_opinion_$idx")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = opinion.reviewerRole.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extra.textMuted
                                    )
                                    Text(
                                        text = opinion.reviewerName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = stanceColor.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = opinion.stance.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = stanceColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = opinion.commentary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 3. Commander's Endorsement Citation Focus
                Text(
                    text = if (evaluation.allMeritsMet) {
                        "COMMANDER'S BOARD ENDORSEMENT (SELECT PERMANENT BONUS)"
                    } else {
                        "COMMANDER'S BOARD ENDORSEMENT (UNLOCKS ONCE MERITS PASS)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.textMuted
                )
                PromotionEndorsementFocus.entries.forEach { focus ->
                    val isSelected = selectedFocus == focus
                    val borderColor = when {
                        !evaluation.allMeritsMet -> extra.borderSubtle
                        isSelected -> extra.brassBright
                        else -> extra.borderSubtle
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (isSelected && evaluation.allMeritsMet) {
                            extra.brassContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        border = BorderStroke(if (isSelected && evaluation.allMeritsMet) 1.5.dp else 1.dp, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable(enabled = evaluation.allMeritsMet) { selectedFocus = focus }
                            .testTag("board_endorsement_${focus.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = if (evaluation.allMeritsMet) {
                                    { selectedFocus = focus }
                                } else {
                                    null
                                },
                                enabled = evaluation.allMeritsMet
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = focus.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (evaluation.allMeritsMet) MaterialTheme.colorScheme.onSurface else extra.textMuted
                                )
                                Text(
                                    text = focus.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (evaluation.allMeritsMet) MaterialTheme.colorScheme.onSurfaceVariant else extra.textMuted
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApprove(selectedFocus) },
                enabled = evaluation.allMeritsMet && canAfford,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("confirm_board_promotion"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = extra.signalsCyan,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.MilitaryTech,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    when {
                        !evaluation.allMeritsMet -> "Prerequisites Pending"
                        !canAfford -> "Need ${evaluation.creditCost.toInt()} CR / ${evaluation.prestigeCost} PRS"
                        else -> "Pin ${nextRank.code}"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("dismiss_board_promotion")
            ) {
                Text("Adjourn")
            }
        }
    )
}
