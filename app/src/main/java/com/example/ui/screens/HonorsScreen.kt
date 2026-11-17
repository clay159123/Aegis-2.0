package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.catalog.GameCatalog
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import com.example.ui.components.*
import com.example.ui.theme.LocalAegisColors
import com.example.ui.viewmodel.CommandUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HonorsScreen(
    uiState: CommandUiState,
    modifier: Modifier = Modifier
) {
    val extra = LocalAegisColors.current
    val activeOps = remember(uiState.operators) {
        uiState.operators.filter { it.unlocked && !it.status.isOutOfActiveService }
    }
    val retiredOps = remember(uiState.operators) {
        uiState.operators.filter { it.unlocked && it.status == OperatorStatus.RETIRED }
    }
    val fallenOps = remember(uiState.operators) {
        uiState.operators.filter { it.status == OperatorStatus.FALLEN }
    }
    var inspectedOp by remember { mutableStateOf<Operator?>(null) }
    var inspectedKit by remember { mutableStateOf<WardrobeKit?>(null) }
    var inspectedAward by remember { mutableStateOf<MedalDefinition?>(null) }

    if (inspectedAward != null) {
        BackHandler { inspectedAward = null }
    } else if (inspectedOp != null) {
        BackHandler {
            inspectedOp = null
            inspectedKit = null
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, extra.brass.copy(alpha = 0.55f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(152.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_parade_grounds),
                        contentDescription = stringResource(id = R.string.cd_parade_banner),
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
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = "ARMY HALL OF HONOR & PARADE",
                            style = MaterialTheme.typography.headlineMedium,
                            color = extra.brassBright
                        )
                        Text(
                            text = "Army Rank Hierarchy, Dress Uniform Standards, & Directorate Citation Ledger.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Dress Uniform Parade Lineup
        item {
            SectionHeader(
                title = "Formal Parade Lineup",
                subtitle = "Active Army personnel standing inspection in Dress Uniform"
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                activeOps.forEach { op ->
                    val paradeKit = TacticalCalculator.resolveEffectiveWardrobeKit(
                        operator = op,
                        smartWardrobeEnabled = uiState.smartWardrobeEnabled,
                        dutyContext = DutyUniformContext.PROMOTION_BOARD
                    )
                    val opMedals = uiState.citations.filter { it.operatorId == op.id }
                        .mapNotNull { cit -> GameCatalog.medals.find { it.id == cit.medalId } }
                        .sortedBy { it.precedenceOrder }
                    val opGear = uiState.gearItems.filter { it.equippedByOperatorId == op.id }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(164.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                inspectedOp = op
                                inspectedKit = paradeKit
                            }
                            .padding(10.dp)
                    ) {
                        OperatorUniformPreview(
                            operator = op,
                            equippedGear = opGear,
                            awardedMedals = opMedals,
                            allCitations = uiState.citations,
                            squads = uiState.squads,
                            kitOverride = paradeKit,
                            modifier = Modifier
                                .width(144.dp)
                                .height(186.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${op.rank.code} ${op.callsign}",
                            style = MaterialTheme.typography.labelMedium,
                            color = extra.brassBright,
                            maxLines = 1
                        )
                        Text(
                            text = "${op.rank.tier.shortLabel} • ${opMedals.size} Honors",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.textMuted,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Army Rank Structure & Uniform Tiers Reference
        item {
            SectionHeader(
                title = "Army Rank Structure & Uniform Tiers",
                subtitle = "Five distinct promotion tiers from Junior Enlisted Soldier to 4-Star General"
            )
            Spacer(modifier = Modifier.height(4.dp))
            TacticalPanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RankTier.entries.forEach { tier ->
                        val ranksInTier = OperatorRank.entries.filter { it.tier == tier }
                        val uniformPerks = when (tier) {
                            RankTier.SOLDIER -> "AGSU Service Green & Dress Blues, Berets, Collar Discs, Sleeve Chevrons"
                            RankTier.NCO -> "NCO Parade Dress Blues, Service Visor Cap, Sleeve Rockers & Gold Cuff Trim"
                            RankTier.SNCO -> "Senior Enlisted Parade Blues, Dual-Braid Cuffs, Senior Visor Cap & SLC Promotion Board"
                            RankTier.OFFICER -> "Officer Mess Blues, Ivory Ceremonial, Peaked Cap, Gold Epaulets & Sam Browne"
                            RankTier.FLAG_OFFICER -> "General Staff Formal, 1–4 Star Bullion Epaulets, Dual Oak-Leaf Cuffs, Crimson Sash"
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "${tier.label.uppercase()} (${ranksInTier.first().payGrade} – ${ranksInTier.last().payGrade})",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (tier == RankTier.FLAG_OFFICER || tier == RankTier.OFFICER) extra.brassBright else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = ranksInTier.joinToString(" • ") { "${it.code} (${it.payGrade})" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Uniforms: $uniformPerks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (tier != RankTier.FLAG_OFFICER) {
                            HorizontalDivider(color = extra.borderSubtle)
                        }
                    }
                }
            }
        }

        // Retired Veterans Honor Roll
        if (retiredOps.isNotEmpty() || uiState.retiredVeteransCount > 0) {
            item {
                SectionHeader(
                    title = "Retired Veterans Honor Roll (${retiredOps.size})",
                    subtitle = "Veteran Legacy Endowment: ${uiState.veteranLegacyPctText} permanent Base Ops output (+12.00% cap)"
                )
            }
            items(retiredOps, key = { "ret_${it.id}" }) { vet ->
                val opMedals = uiState.citations.filter { it.operatorId == vet.id }
                    .mapNotNull { cit -> GameCatalog.medals.find { it.id == cit.medalId } }
                    .sortedBy { it.precedenceOrder }
                TacticalPanelCard(borderColor = extra.signalsCyan.copy(alpha = 0.6f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OperatorUniformPreview(
                            operator = vet,
                            equippedGear = emptyList(),
                            awardedMedals = opMedals,
                            allCitations = uiState.citations,
                            squads = uiState.squads,
                            kitOverride = WardrobeKit.ALPHAS,
                            modifier = Modifier
                                .width(110.dp)
                                .height(144.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    inspectedOp = vet
                                    inspectedKit = WardrobeKit.ALPHAS
                                }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${vet.rank.title.uppercase()} ${vet.name.uppercase()}",
                                style = MaterialTheme.typography.titleSmall,
                                color = extra.brassBright
                            )
                            Text(
                                text = "Callsign \"${vet.callsign}\" • ${vet.missionsCompleted} Field • ${vet.staffOpsSupported} Staff Ops",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Honorably Retired • ${opMedals.size} Citations Preserved",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.signalsCyan
                            )
                            if (opMedals.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    opMedals.filter {
                                        it.category == AwardCategory.MEDAL || it.category == AwardCategory.RIBBON
                                    }.take(6).forEach { medal ->
                                        MedalRibbonBar(
                                            stripeHexes = medal.ribbonStripeHexes,
                                            width = 24.dp,
                                            height = 8.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Memorial Wall (if any operators fell in action)
        if (fallenOps.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "HQ Memorial Honor Roll",
                    subtitle = "Remembering soldiers who gave all — posthumous honors remain pinned in perpetuity"
                )
            }
            items(fallenOps, key = { it.id }) { fallen ->
                val opMedals = uiState.citations.filter { it.operatorId == fallen.id }
                    .mapNotNull { cit -> GameCatalog.medals.find { it.id == cit.medalId } }
                    .sortedBy { it.precedenceOrder }
                TacticalPanelCard(borderColor = extra.brass) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                inspectedOp = fallen
                                inspectedKit = WardrobeKit.ALPHAS
                            }
                            .testTag("inspect_fallen_${fallen.id}"),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OperatorUniformPreview(
                            operator = fallen,
                            equippedGear = emptyList(),
                            awardedMedals = opMedals,
                            allCitations = uiState.citations,
                            squads = uiState.squads,
                            kitOverride = WardrobeKit.ALPHAS,
                            modifier = Modifier
                                .width(110.dp)
                                .height(144.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${fallen.rank.title.uppercase()} ${fallen.name.uppercase()}",
                                style = MaterialTheme.typography.titleSmall,
                                color = extra.brassBright
                            )
                            Text(
                                text = "Callsign \"${fallen.callsign}\" • ${fallen.missionsCompleted} Ops Served",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Tap to inspect full dress honors",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.textMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                opMedals.take(6).forEach { medal ->
                                    MedalRibbonBar(
                                        stripeHexes = medal.ribbonStripeHexes,
                                        width = 24.dp,
                                        height = 8.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Precedence Reference Chart across all 25 Honors & Regalia
        item {
            SectionHeader(
                title = "Order of Precedence & Regalia (${GameCatalog.medals.size})",
                subtitle = "Medals, Ribbons, Shoulder Cords & Specialty School Insignia"
            )
            Spacer(modifier = Modifier.height(4.dp))
            TacticalPanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameCatalog.medals.forEach { medal ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { inspectedAward = medal }
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "#${medal.precedenceOrder}",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.textMuted,
                                modifier = Modifier.width(24.dp)
                            )
                            AwardVisualThumbnail(medal = medal)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = medal.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (medal.isNeckMedallion) extra.brassBright else MaterialTheme.colorScheme.onSurface
                                )
                                val perSoldierCap = TacticalCalculator.perSoldierCapForAward(medal.id)
                                val heldCount = uiState.citations.count { it.medalId == medal.id }
                                val capSuffix = when {
                                    perSoldierCap != null -> " • $perSoldierCap Max / Soldier" +
                                        if (heldCount > 0) " ($heldCount Awarded)" else ""
                                    else -> ""
                                }
                                Text(
                                    text = "${medal.category.label} • ${medal.tier.label} • ${medal.prestigeCost} PRS$capSuffix",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (perSoldierCap != null && heldCount > 0) extra.brassBright else extra.textMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // Citation Ledger
        if (uiState.citations.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Directorate Citation Ledger",
                    subtitle = "Permanent record of awarded medals, ribbons, cords, and school insignia"
                )
            }
            items(uiState.citations.take(20), key = { it.id }) { cit ->
                val op = uiState.operators.find { it.id == cit.operatorId }
                val medal = GameCatalog.medals.find { it.id == cit.medalId }
                if (op != null && medal != null) {
                    val dateStr = remember(cit.awardedAtMillis) {
                        SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(cit.awardedAtMillis))
                    }
                    TacticalPanelCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AwardVisualThumbnail(medal = medal)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${op.rank.code} ${op.callsign} — ${medal.name}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = extra.brassBright
                                )
                                Text(
                                    text = "${medal.category.label} • ${cit.missionTitle} • $dateStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.textMuted
                                )
                                Text(
                                    text = "\"${cit.citationNote}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val opToInspect = inspectedOp
    if (opToInspect != null) {
        val opMedals = uiState.citations.filter { it.operatorId == opToInspect.id }
            .mapNotNull { cit -> GameCatalog.medals.find { it.id == cit.medalId } }
            .sortedBy { it.precedenceOrder }
        val opGear = uiState.gearItems.filter { it.equippedByOperatorId == opToInspect.id }
        UniformInspectionDialog(
            operator = opToInspect,
            equippedGear = opGear,
            awardedMedals = opMedals,
            allCitations = uiState.citations,
            squads = uiState.squads,
            kitOverride = inspectedKit ?: WardrobeKit.ALPHAS,
            onInspectAward = { inspectedAward = it },
            onDismiss = {
                inspectedOp = null
                inspectedKit = null
            }
        )
    }

    val awardToInspect = inspectedAward
    if (awardToInspect != null) {
        val holders = uiState.citations
            .filter { it.medalId == awardToInspect.id }
            .mapNotNull { cit ->
                val op = uiState.operators.find { it.id == cit.operatorId }
                if (op != null) op to cit else null
            }
        AwardInspectionDialog(
            medal = awardToInspect,
            heldByOperators = holders,
            onDismiss = { inspectedAward = null }
        )
    }
}
