package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.domain.model.OperatorRank
import com.example.domain.model.RankTier
import com.example.ui.theme.LocalAegisColors

@Composable
fun RankInsigniaBadge(
    rank: OperatorRank,
    modifier: Modifier = Modifier
) {
    val extra = LocalAegisColors.current
    val badgeBg = when (rank.tier) {
        RankTier.FLAG_OFFICER -> Color(0xFF282012)
        RankTier.OFFICER, RankTier.WARRANT -> extra.brassContainer
        RankTier.SNCO -> Color(0xFF1A2A34)
        RankTier.NCO -> Color(0xFF1F2B38)
        RankTier.SOLDIER -> MaterialTheme.colorScheme.primaryContainer
    }
    val badgeBorder = when (rank.tier) {
        RankTier.FLAG_OFFICER -> extra.brassBright
        RankTier.OFFICER, RankTier.WARRANT -> extra.brass
        RankTier.SNCO -> extra.brassBright
        RankTier.NCO -> extra.signalsCyan
        RankTier.SOLDIER -> MaterialTheme.colorScheme.primary
    }
    val badgeText = when (rank.tier) {
        RankTier.FLAG_OFFICER -> extra.brassBright
        RankTier.OFFICER, RankTier.WARRANT -> extra.brassBright
        RankTier.SNCO -> extra.brassBright
        RankTier.NCO -> extra.signalsCyan
        RankTier.SOLDIER -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(badgeBg)
            .border(1.dp, badgeBorder.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val w = size.width
            val h = size.height
            when (rank.tier) {
                RankTier.FLAG_OFFICER -> {
                    val stars = rank.insigniaCount.coerceIn(1, 4)
                    val r = if (stars <= 2) w * 0.22f else w * 0.16f
                    val gap = w / (stars + 1)
                    repeat(stars) { i ->
                        drawStar(Offset(gap * (i + 1), h * 0.5f), r, r * 0.45f, badgeText)
                    }
                }
                RankTier.OFFICER, RankTier.WARRANT -> {
                    drawOfficerRankDevice(
                        rank = rank,
                        center = Offset(w * 0.5f, h * 0.5f),
                        boxW = w * 0.88f,
                        boxH = h * 0.86f,
                        outlineColor = Color(0xFF101622)
                    )
                }
                RankTier.SNCO, RankTier.NCO, RankTier.SOLDIER -> {
                    val (chevrons, rockers) = when (rank) {
                        OperatorRank.PVT -> 0 to 0
                        OperatorRank.PV2 -> 1 to 0
                        OperatorRank.PFC -> 1 to 1
                        OperatorRank.SPC -> 0 to 0
                        else -> rank.insigniaCount.coerceAtMost(3) to (rank.insigniaCount - 3).coerceIn(0, 3)
                    }
                    if (rank == OperatorRank.SPC) {
                        val shield = Path().apply {
                            moveTo(w * 0.18f, h * 0.24f)
                            quadraticTo(w * 0.5f, h * 0.10f, w * 0.82f, h * 0.24f)
                            lineTo(w * 0.80f, h * 0.62f)
                            quadraticTo(w * 0.5f, h * 0.90f, w * 0.20f, h * 0.62f)
                            close()
                        }
                        drawPath(shield, color = badgeText, style = Stroke(width = 1.5f))
                        drawLine(badgeText, Offset(w * 0.30f, h * 0.45f), Offset(w * 0.50f, h * 0.54f), strokeWidth = 1.6f, cap = StrokeCap.Round)
                        drawLine(badgeText, Offset(w * 0.70f, h * 0.45f), Offset(w * 0.50f, h * 0.54f), strokeWidth = 1.6f, cap = StrokeCap.Round)
                    } else {
                        val bandStep = h * 0.125f
                        val totalBands = chevrons + rockers
                        val topY = if (totalBands <= 2) h * 0.40f else h * 0.18f
                        repeat(chevrons) { i ->
                            val cy = topY + i * bandStep
                            val path = Path().apply {
                                moveTo(w * 0.16f, cy + h * 0.10f)
                                lineTo(w * 0.50f, cy - h * 0.05f)
                                lineTo(w * 0.84f, cy + h * 0.10f)
                            }
                            drawPath(path, color = badgeText, style = Stroke(width = 1.55f, cap = StrokeCap.Round))
                        }
                        repeat(rockers) { rk ->
                            val ry = topY + chevrons * bandStep + h * 0.02f + rk * bandStep
                            drawArc(
                                color = badgeText,
                                startAngle = 20f,
                                sweepAngle = 140f,
                                useCenter = false,
                                topLeft = Offset(w * 0.18f, ry - h * 0.04f),
                                size = Size(w * 0.64f, h * 0.12f),
                                style = Stroke(width = 1.5f, cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = "${rank.code} • ${rank.payGrade}",
            style = MaterialTheme.typography.labelSmall,
            color = badgeText
        )
    }
}

/**
 * Draws authentic US Army officer rank insignia for O-1 through O-6:
 * - LT2 (O-1): Single gold bar
 * - LT1 (O-2): Single silver bar
 * - CPT (O-3): Twin connected silver bars
 * - MAJ (O-4): Gold oak leaf
 * - LTC (O-5): Silver oak leaf
 * - COL (O-6): Silver spread-wing eagle
 */
internal fun DrawScope.drawOfficerRankDevice(
    rank: OperatorRank,
    center: Offset,
    boxW: Float,
    boxH: Float,
    outlineColor: Color = Color(0xFF0E1420),
    subdued: Boolean = false
) {
    val cx = center.x
    val cy = center.y
    val goldCol = if (subdued) Color(0xFFB58E35) else Color(0xFFF0C64C)
    val silverCol = if (subdued) Color(0xFF9EA8B8) else Color(0xFFEAF1FA)
    val strokeW = (minOf(boxW, boxH) * 0.065f).coerceIn(0.75f, 1.5f)

    when (rank) {
        OperatorRank.WO1, OperatorRank.CW2, OperatorRank.CW3, OperatorRank.CW4, OperatorRank.CW5 -> {
            // Warrant bar: silver with black squares (WO1-CW4), or a black center line (CW5)
            val barW = (boxW * 0.30f).coerceAtLeast(3.2f)
            val barH = boxH * 0.80f
            val left = cx - barW * 0.5f
            val top = cy - barH * 0.5f
            drawRoundRect(silverCol, Offset(left, top), Size(barW, barH), CornerRadius(1.2f, 1.2f))
            val black = Color(0xFF111111)
            if (rank == OperatorRank.CW5) {
                val lineW = (barW * 0.22f).coerceAtLeast(0.8f)
                drawRect(black, Offset(cx - lineW * 0.5f, top + barH * 0.08f), Size(lineW, barH * 0.84f))
            } else {
                val squares = rank.warrantGrade
                val sq = minOf(barW * 0.55f, barH / (squares * 2f + 1f))
                val step = barH / (squares + 1)
                repeat(squares) { i ->
                    val sy = top + step * (i + 1) - sq * 0.5f
                    drawRect(black, Offset(cx - sq * 0.5f, sy), Size(sq, sq))
                }
            }
            drawRoundRect(
                color = outlineColor.copy(alpha = 0.72f),
                topLeft = Offset(left, top),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(1.2f, 1.2f),
                style = Stroke(width = strokeW)
            )
        }
        OperatorRank.LT2, OperatorRank.LT1 -> {
            val metal = if (rank == OperatorRank.LT2) goldCol else silverCol
            val barW = (boxW * 0.28f).coerceAtLeast(3.0f)
            val barH = boxH * 0.78f
            val left = cx - barW * 0.5f
            val top = cy - barH * 0.5f
            drawRoundRect(
                color = metal,
                topLeft = Offset(left, top),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(1.2f, 1.2f)
            )
            drawRoundRect(
                color = outlineColor.copy(alpha = 0.72f),
                topLeft = Offset(left, top),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(1.2f, 1.2f),
                style = Stroke(width = strokeW)
            )
        }
        OperatorRank.CPT -> {
            val barW = (boxW * 0.21f).coerceAtLeast(2.4f)
            val barH = boxH * 0.76f
            val spacing = boxW * 0.22f
            val leftBarX = cx - spacing * 0.5f - barW
            val rightBarX = cx + spacing * 0.5f
            val top = cy - barH * 0.5f
            val tieH = (barH * 0.11f).coerceAtLeast(1.3f)

            // Horizontal connector straps (top and bottom) linking the twin silver bars
            drawRect(
                color = silverCol,
                topLeft = Offset(leftBarX + barW * 0.5f, top + barH * 0.18f),
                size = Size((rightBarX - leftBarX), tieH)
            )
            drawRect(
                color = silverCol,
                topLeft = Offset(leftBarX + barW * 0.5f, top + barH * 0.82f - tieH),
                size = Size((rightBarX - leftBarX), tieH)
            )
            listOf(leftBarX, rightBarX).forEach { bx ->
                drawRoundRect(
                    color = silverCol,
                    topLeft = Offset(bx, top),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(1.0f, 1.0f)
                )
                drawRoundRect(
                    color = outlineColor.copy(alpha = 0.72f),
                    topLeft = Offset(bx, top),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(1.0f, 1.0f),
                    style = Stroke(width = strokeW)
                )
            }
        }
        OperatorRank.MAJ, OperatorRank.LTC -> {
            val metal = if (rank == OperatorRank.MAJ) goldCol else silverCol
            val hw = minOf(boxW * 0.42f, boxH * 0.48f)
            val hh = boxH * 0.44f
            val leafPath = Path().apply {
                moveTo(cx, cy - hh)
                // Right upper, mid, and lower oak lobes
                cubicTo(cx + hw * 0.34f, cy - hh * 0.88f, cx + hw * 0.62f, cy - hh * 0.60f, cx + hw * 0.58f, cy - hh * 0.36f)
                lineTo(cx + hw * 0.30f, cy - hh * 0.22f)
                cubicTo(cx + hw * 0.96f, cy - hh * 0.24f, cx + hw * 1.02f, cy + hh * 0.14f, cx + hw * 0.88f, cy + hh * 0.28f)
                lineTo(cx + hw * 0.44f, cy + hh * 0.32f)
                cubicTo(cx + hw * 0.84f, cy + hh * 0.42f, cx + hw * 0.74f, cy + hh * 0.70f, cx + hw * 0.40f, cy + hh * 0.70f)
                lineTo(cx + hw * 0.10f, cy + hh * 0.68f)
                lineTo(cx + hw * 0.08f, cy + hh * 0.96f)
                lineTo(cx - hw * 0.08f, cy + hh * 0.96f)
                lineTo(cx - hw * 0.10f, cy + hh * 0.68f)
                // Left mirrored oak lobes
                lineTo(cx - hw * 0.40f, cy + hh * 0.70f)
                cubicTo(cx - hw * 0.74f, cy + hh * 0.70f, cx - hw * 0.84f, cy + hh * 0.42f, cx - hw * 0.44f, cy + hh * 0.32f)
                lineTo(cx - hw * 0.88f, cy + hh * 0.28f)
                cubicTo(cx - hw * 1.02f, cy + hh * 0.14f, cx - hw * 0.96f, cy - hh * 0.24f, cx - hw * 0.30f, cy - hh * 0.22f)
                lineTo(cx - hw * 0.58f, cy - hh * 0.36f)
                cubicTo(cx - hw * 0.62f, cy - hh * 0.60f, cx - hw * 0.34f, cy - hh * 0.88f, cx, cy - hh)
                close()
            }
            drawPath(leafPath, color = metal)
            drawPath(leafPath, color = outlineColor.copy(alpha = 0.75f), style = Stroke(width = strokeW, join = StrokeJoin.Round))
            // Midrib vein for crisp silhouette legibility at small sizes
            drawLine(
                color = outlineColor.copy(alpha = 0.55f),
                start = Offset(cx, cy - hh * 0.68f),
                end = Offset(cx, cy + hh * 0.64f),
                strokeWidth = (strokeW * 0.9f).coerceAtLeast(0.75f),
                cap = StrokeCap.Round
            )
        }
        OperatorRank.COL -> {
            val hw = boxW * 0.49f
            val hh = boxH * 0.45f
            val eaglePath = Path().apply {
                // Eagle head and beak
                moveTo(cx - hw * 0.02f, cy - hh * 0.92f)
                lineTo(cx + hw * 0.11f, cy - hh * 0.68f)
                lineTo(cx + hw * 0.12f, cy - hh * 0.28f)
                // Right outstretched wing leading edge + notched feather trailing edge
                lineTo(cx + hw * 0.98f, cy - hh * 0.64f)
                lineTo(cx + hw * 0.86f, cy - hh * 0.18f)
                lineTo(cx + hw * 0.68f, cy + hh * 0.06f)
                lineTo(cx + hw * 0.42f, cy + hh * 0.24f)
                lineTo(cx + hw * 0.18f, cy + hh * 0.26f)
                // Tail fan
                lineTo(cx + hw * 0.24f, cy + hh * 0.86f)
                lineTo(cx, cy + hh * 0.96f)
                lineTo(cx - hw * 0.24f, cy + hh * 0.86f)
                // Left wing trailing edge back up to wingtip
                lineTo(cx - hw * 0.18f, cy + hh * 0.26f)
                lineTo(cx - hw * 0.42f, cy + hh * 0.24f)
                lineTo(cx - hw * 0.68f, cy + hh * 0.06f)
                lineTo(cx - hw * 0.86f, cy - hh * 0.18f)
                lineTo(cx - hw * 0.98f, cy - hh * 0.64f)
                lineTo(cx - hw * 0.12f, cy - hh * 0.28f)
                lineTo(cx - hw * 0.14f, cy - hh * 0.62f)
                lineTo(cx - hw * 0.22f, cy - hh * 0.68f)
                close()
            }
            drawPath(eaglePath, color = silverCol)
            drawPath(eaglePath, color = outlineColor.copy(alpha = 0.78f), style = Stroke(width = strokeW, join = StrokeJoin.Round))
            // Chest shield accent distinguishing full-bird Colonel crest
            drawOval(
                color = outlineColor.copy(alpha = 0.55f),
                topLeft = Offset(cx - hw * 0.12f, cy - hh * 0.16f),
                size = Size(hw * 0.24f, hh * 0.42f),
                style = Stroke(width = (strokeW * 0.85f).coerceAtLeast(0.75f))
            )
        }
        else -> {
            val stars = rank.insigniaCount.coerceIn(1, 4)
            val r = minOf(boxW, boxH) * 0.22f
            drawStar(center, r, r * 0.45f, silverCol)
        }
    }
}

/**
 * Item 68 (early): a small "SF" chip on SF-qualified soldiers' cards, in Green Beret green with gold
 * letters, so they're easy to spot in rosters and pickers.
 */
@Composable
fun SfMarker(modifier: Modifier = Modifier, tier: Int = 0) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF254B34))
            .border(1.dp, Color(0xFFD4B24C), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        // Session 30: the soldier's (or ODA's) SF tier inside the green square
        Text(if (tier > 0) "SF$tier" else "SF", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF0D77A))
    }
}

/** A name line followed by the SF chip when the soldier is SF-qualified. */
@Composable
fun NameWithSfMarker(
    text: String,
    sfQualified: Boolean,
    style: androidx.compose.ui.text.TextStyle,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    sfTier: Int = 0,
    /** Session 31: when given, the badge opens the soldier's SF progress on tap. */
    operator: com.example.domain.model.Operator? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text,
            style = style,
            color = color,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (sfQualified) {
            if (operator != null) SfTierBadge(operator) else SfMarker(tier = sfTier)
        }
    }
}

/** Session 31: the next SF tier's checklist, used on the soldier record and in the badge popup. */
@Composable
fun SfProgressList(operator: com.example.domain.model.Operator, modifier: Modifier = Modifier) {
    val (next, steps) = com.example.domain.TacticalCalculator.sfNextTierChecklist(operator)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (next == null) {
            Text("SF3: top tier reached.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF0D77A))
        } else {
            Text("Next: SF$next", style = MaterialTheme.typography.labelLarge, color = Color(0xFFF0D77A))
            steps.forEach { step ->
                Text(
                    (if (step.done) "✓ " else "✗ ") + step.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (step.done) Color(0xFF8BC48A) else Color(0xFFE0A86A)
                )
            }
        }
    }
}

/** Session 31: the SF tier chip that opens the soldier's progress to the next tier. */
@Composable
fun SfTierBadge(operator: com.example.domain.model.Operator, modifier: Modifier = Modifier) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    SfMarker(
        tier = com.example.domain.TacticalCalculator.sfTier(operator),
        modifier = modifier.clickable { open = true }.testTag("sf_badge_${operator.id}")
    )
    if (open) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { open = false }) { Text("Close") } },
            title = { Text("${operator.rank.code} ${operator.callsign} • SF${com.example.domain.TacticalCalculator.sfTier(operator)}") },
            text = { SfProgressList(operator) }
        )
    }
}
