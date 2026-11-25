package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import com.example.domain.TacticalCalculator
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import kotlin.math.PI
import kotlin.math.sin

internal fun DrawScope.drawBravosShortSleeveFigure(
    w: Float,
    h: Float,
    cx: Float,
    operator: Operator,
    tier: RankTier,
    skinTone: Color,
    skinShadow: Color,
    rackDecorations: List<MedalDefinition>,
    wornRegalia: TacticalCalculator.WornRegaliaSummary,
    leftCordDef: MedalDefinition?,
    rightCordDef: MedalDefinition?,
    unitAwards: List<Pair<UnitAwardType, Int>>,
    marksmanshipTier: MarksmanshipBadgeTier,
    leaderRole: LeaderRole,
    awardDevices: Map<String, UniformRegaliaRules.AwardDeviceInfo>,
    textMeasurer: TextMeasurer,
    environmentBadge: MedalDefinition? = null
) {
    // Item 57: Bravos follow the uniform family. AGSU (green) at any rank: tan shirt and olive slacks.
    // Blue family: officers in sky-blue (flag officers pearl) with navy slacks and gold stripes; enlisted in
    // white with navy slacks and a light-blue stripe. Officers in AGSU and in Blues no longer look alike.
    val isOfficer = tier.isOfficer
    val greenFamily = operator.dressStyle == DressUniformStyle.ARMY_SERVICE_GREEN
    val shirtCol = when {
        greenFamily -> Color(0xFFD5C49A)
        tier == RankTier.FLAG_OFFICER -> Color(0xFFEAF0F7)
        isOfficer -> Color(0xFFD6E2F0)
        else -> Color(0xFFF2F2EE)
    }
    val shirtShadeCol = when {
        greenFamily -> Color(0xFFB3A178)
        tier == RankTier.FLAG_OFFICER -> Color(0xFFC5D0DE)
        isOfficer -> Color(0xFFB3C3D8)
        else -> Color(0xFFCBCCC6)
    }
    val trouserCol = if (greenFamily) Color(0xFF3E4A31) else Color(0xFF13223A)
    val trouserStripeCol = when {
        greenFamily -> Color(0xFF28311F)
        tier == RankTier.FLAG_OFFICER -> Color(0xFFF2D272)
        isOfficer -> Color(0xFFD8B24C)
        else -> Color(0xFF4A78A8)
    }
    val trimCol = if (isOfficer) Color(0xFFF0CB65) else Color(0xFFD8B24C)
    val beltCol = if (greenFamily) Color(0xFF2B3221) else Color(0xFF111622)
    val pocketOutline = shirtShadeCol.copy(alpha = 0.92f)

    // 1. Service Trousers starting cleanly below the tucked shirt belt line
    val trouserTop = h * 0.758f
    val waistHalf = if (isOfficer) w * 0.242f else w * 0.252f
    val legPath = Path().apply {
        moveTo(cx - waistHalf, trouserTop)
        lineTo(cx + waistHalf, trouserTop)
        lineTo(cx + w * 0.270f, h * 0.972f)
        lineTo(cx + w * 0.026f, h * 0.972f)
        lineTo(cx, h * 0.895f)
        lineTo(cx - w * 0.026f, h * 0.972f)
        lineTo(cx - w * 0.270f, h * 0.972f)
        close()
    }
    drawPath(legPath, color = trouserCol)
    drawLine(
        color = trouserStripeCol,
        start = Offset(cx - waistHalf * 0.96f, trouserTop + h * 0.010f),
        end = Offset(cx - w * 0.262f, h * 0.968f),
        strokeWidth = if (tier == RankTier.FLAG_OFFICER) 3.0f else 1.8f
    )
    drawLine(
        color = trouserStripeCol,
        start = Offset(cx + waistHalf * 0.96f, trouserTop + h * 0.010f),
        end = Offset(cx + w * 0.262f, h * 0.968f),
        strokeWidth = if (tier == RankTier.FLAG_OFFICER) 3.0f else 1.8f
    )

    // 2. Bare Forearms & Bare Hands extending below short-sleeve hem (no gloves in Bravos)
    fun armInnerX(sideSign: Float, yFrac: Float): Float {
        val x = when {
            yFrac <= 0.520f -> {
                val t = ((yFrac - 0.320f) / (0.520f - 0.320f)).coerceIn(0f, 1f)
                0.246f - t * 0.008f
            }
            // Art 5b: arms hang straighter, like the dress coats
            else -> {
                val t = ((yFrac - 0.520f) / (0.895f - 0.520f)).coerceIn(0f, 1f)
                0.238f + t * 0.026f
            }
        }
        return cx + sideSign * w * x
    }

    fun armOuterX(sideSign: Float, yFrac: Float): Float {
        val x = when {
            yFrac <= 0.455f -> {
                val t = ((yFrac - 0.314f) / (0.455f - 0.314f)).coerceIn(0f, 1f)
                val ease = sin(t * (PI.toFloat() * 0.5f))
                0.276f + ease * 0.102f
            }
            yFrac <= 0.556f -> {
                val t = ((yFrac - 0.455f) / (0.556f - 0.455f)).coerceIn(0f, 1f)
                0.378f + t * 0.006f
            }
            else -> {
                val t = ((yFrac - 0.556f) / (0.895f - 0.556f)).coerceIn(0f, 1f)
                0.376f - t * 0.018f
            }
        }
        return cx + sideSign * w * x
    }

    fun shortSleeveFaceX(sideSign: Float, yFrac: Float, u: Float): Float {
        val inner = armInnerX(sideSign, yFrac)
        val outer = armOuterX(sideSign, yFrac)
        return inner + (outer - inner) * u
    }

    val sleeveBottomFrac = 0.554f
    for (sideSign in listOf(-1f, 1f)) {
        // Bare forearm from hem down to wrist
        val forearmPath = Path().apply {
            val steps = 10
            moveTo(armInnerX(sideSign, sleeveBottomFrac - 0.008f), h * (sleeveBottomFrac - 0.008f))
            for (i in 0..steps) {
                val f = sleeveBottomFrac + (0.896f - sleeveBottomFrac) * (i / steps.toFloat())
                lineTo(armOuterX(sideSign, f), h * f)
            }
            for (i in steps downTo 0) {
                val f = sleeveBottomFrac + (0.896f - sleeveBottomFrac) * (i / steps.toFloat())
                lineTo(armInnerX(sideSign, f), h * f)
            }
            close()
        }
        drawPath(forearmPath, color = skinTone)
        // Shadow right under the short sleeve hem + cylindrical forearm contour
        drawRect(
            color = skinShadow.copy(alpha = 0.35f),
            topLeft = Offset(
                minOf(armInnerX(sideSign, sleeveBottomFrac), armOuterX(sideSign, sleeveBottomFrac)),
                h * sleeveBottomFrac
            ),
            size = Size(
                kotlin.math.abs(armOuterX(sideSign, sleeveBottomFrac) - armInnerX(sideSign, sleeveBottomFrac)),
                h * 0.016f
            )
        )
        drawPath(
            forearmPath,
            brush = Brush.horizontalGradient(
                colors = if (sideSign < 0f) {
                    listOf(skinShadow.copy(alpha = 0.35f), Color.Transparent, skinShadow.copy(alpha = 0.16f))
                } else {
                    listOf(skinShadow.copy(alpha = 0.16f), Color.Transparent, skinShadow.copy(alpha = 0.35f))
                },
                startX = minOf(armOuterX(sideSign, 0.72f), armInnerX(sideSign, 0.72f)),
                endX = maxOf(armOuterX(sideSign, 0.72f), armInnerX(sideSign, 0.72f))
            )
        )

        // Art 5b: relaxed hands, same as the dress coats
        val wristLeft = minOf(armInnerX(sideSign, 0.895f), armOuterX(sideSign, 0.895f))
        val wristW = kotlin.math.abs(armOuterX(sideSign, 0.895f) - armInnerX(sideSign, 0.895f))
        drawRelaxedHand(sideSign, wristLeft + wristW * 0.5f, h * 0.894f, wristW * 0.78f, h * 0.064f, skinTone, skinShadow)

        // Short shirt sleeve (shoulder to mid-bicep hem)
        val shortSleevePath = Path().apply {
            val steps = 8
            moveTo(armInnerX(sideSign, 0.314f), h * 0.314f)
            for (i in 0..steps) {
                val f = 0.314f + (sleeveBottomFrac - 0.314f) * (i / steps.toFloat())
                lineTo(armOuterX(sideSign, f), h * f)
            }
            for (i in steps downTo 0) {
                val f = 0.314f + (sleeveBottomFrac - 0.314f) * (i / steps.toFloat())
                lineTo(armInnerX(sideSign, f), h * f)
            }
            close()
        }
        drawPath(shortSleevePath, color = shirtCol)
        drawPath(
            shortSleevePath,
            brush = Brush.horizontalGradient(
                colors = if (sideSign < 0f) {
                    listOf(Color.Black.copy(alpha = 0.16f), Color.White.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.10f))
                } else {
                    listOf(Color.Black.copy(alpha = 0.10f), Color.White.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.16f))
                },
                startX = minOf(armOuterX(sideSign, 0.45f), armInnerX(sideSign, 0.45f)),
                endX = maxOf(armOuterX(sideSign, 0.45f), armInnerX(sideSign, 0.45f))
            )
        )
        // Stitched folded short-sleeve cuff line
        drawLine(
            color = shirtShadeCol,
            start = Offset(armInnerX(sideSign, sleeveBottomFrac - 0.012f), h * (sleeveBottomFrac - 0.012f)),
            end = Offset(armOuterX(sideSign, sleeveBottomFrac - 0.012f), h * (sleeveBottomFrac - 0.012f)),
            strokeWidth = 1.6f
        )
        drawLine(
            color = Color.Black.copy(alpha = 0.20f),
            start = Offset(armInnerX(sideSign, sleeveBottomFrac), h * sleeveBottomFrac),
            end = Offset(armOuterX(sideSign, sleeveBottomFrac), h * sleeveBottomFrac),
            strokeWidth = 1.4f
        )
    }

    // 3. Tailored Short-Sleeve Shirt Torso (tucked smoothly at waist belt)
    val shirtBodyPath = Path().apply {
        moveTo(cx - w * 0.146f, h * 0.294f)
        lineTo(cx + w * 0.146f, h * 0.294f)
        lineTo(cx + w * 0.246f, h * 0.322f)
        lineTo(cx + w * 0.238f, h * 0.520f)
        lineTo(cx + w * 0.234f, h * 0.758f)
        lineTo(cx - w * 0.234f, h * 0.758f)
        lineTo(cx - w * 0.238f, h * 0.520f)
        lineTo(cx - w * 0.246f, h * 0.322f)
        close()
    }
    drawPath(shirtBodyPath, color = shirtCol)
    drawPath(
        shirtBodyPath,
        brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent, Color.Black.copy(alpha = 0.12f)))
    )
    // Art 5b: light from the upper left, pressed creases through the pockets, blousing above the belt
    drawPath(shirtBodyPath, Brush.horizontalGradient(
        0f to Color.White.copy(alpha = 0.08f), 0.45f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.16f),
        startX = cx - w * 0.25f, endX = cx + w * 0.25f))
    for (s in listOf(-1f, 1f)) {
        drawLine(shirtShadeCol.copy(alpha = 0.55f), Offset(cx + s * w * 0.168f, h * 0.300f), Offset(cx + s * w * 0.168f, h * 0.745f), strokeWidth = 0.9f)
        for (k in 0..2) {
            val fx = cx + s * w * (0.070f + k * 0.055f)
            drawArc(shirtShadeCol.copy(alpha = 0.6f), 200f, 140f, false, Offset(fx - w * 0.022f, h * 0.728f), Size(w * 0.044f, h * 0.018f), style = Stroke(0.9f))
        }
    }
    // Armhole shoulder seams
    drawLine(shirtShadeCol, Offset(cx - w * 0.246f, h * 0.322f), Offset(cx - w * 0.238f, h * 0.520f), strokeWidth = 1.3f)
    drawLine(shirtShadeCol, Offset(cx + w * 0.246f, h * 0.322f), Offset(cx + w * 0.238f, h * 0.520f), strokeWidth = 1.3f)

    // Open-collar Class B neckline with white/black crew undershirt + center button placket
    val neckOpening = Path().apply {
        moveTo(cx - w * 0.058f, h * 0.294f)
        lineTo(cx, h * 0.382f)
        lineTo(cx + w * 0.058f, h * 0.294f)
        close()
    }
    drawPath(neckOpening, color = Color(0xFFF3F5F8))
    drawPath(
        Path().apply {
            moveTo(cx - w * 0.058f, h * 0.294f)
            lineTo(cx - w * 0.088f, h * 0.350f)
            lineTo(cx, h * 0.384f)
            close()
        },
        color = shirtShadeCol.copy(alpha = 0.55f)
    )
    drawPath(
        Path().apply {
            moveTo(cx + w * 0.058f, h * 0.294f)
            lineTo(cx + w * 0.088f, h * 0.350f)
            lineTo(cx, h * 0.384f)
            close()
        },
        color = shirtShadeCol.copy(alpha = 0.55f)
    )
    // Art 5b: collar points lying open on the shoulders
    for (s in listOf(-1f, 1f)) {
        val collar = Path().apply {
            moveTo(cx + s * w * 0.056f, h * 0.292f)
            lineTo(cx + s * w * 0.118f, h * 0.300f)
            lineTo(cx + s * w * 0.098f, h * 0.364f)
            lineTo(cx + s * w * 0.010f, h * 0.384f)
            close()
        }
        drawPath(collar, shirtCol)
        drawPath(collar, shirtShadeCol, style = Stroke(1f))
    }
    // Center placket strip & crisp buttons
    drawRect(
        color = shirtShadeCol.copy(alpha = 0.28f),
        topLeft = Offset(cx - w * 0.014f, h * 0.382f),
        size = Size(w * 0.028f, h * 0.370f)
    )
    drawLine(shirtShadeCol, Offset(cx - w * 0.014f, h * 0.382f), Offset(cx - w * 0.014f, h * 0.752f), strokeWidth = 0.8f)
    for (b in 0..3) {
        drawCircle(
            color = shirtShadeCol,
            radius = w * 0.0075f,
            center = Offset(cx, h * (0.432f + b * 0.075f))
        )
    }

    // 4. Breast Shirt Pockets (Left & Right)
    val leftPocketX = cx + w * 0.104f
    val rightPocketX = cx - w * 0.232f
    val pocketW = w * 0.128f
    val pocketTopY = h * CHEST_POCKET_TOP_FRACTION
    val pocketH = h * 0.122f
    val flapH = h * 0.030f
    listOf(rightPocketX, leftPocketX).forEach { px ->
        drawRoundRect(shirtShadeCol.copy(alpha = 0.18f), Offset(px, pocketTopY), Size(pocketW, pocketH), CornerRadius(3f, 3f))
        drawRoundRect(pocketOutline, Offset(px, pocketTopY), Size(pocketW, pocketH), CornerRadius(3f, 3f), style = Stroke(1.0f))
        drawRoundRect(shirtCol, Offset(px - 1f, pocketTopY), Size(pocketW + 2f, flapH), CornerRadius(2f, 2f))
        drawRoundRect(pocketOutline, Offset(px - 1f, pocketTopY), Size(pocketW + 2f, flapH), CornerRadius(2f, 2f), style = Stroke(1.0f))
        drawCircle(shirtShadeCol, radius = w * 0.0052f, center = Offset(px + pocketW / 2f, pocketTopY + flapH * 0.64f))
    }

    // 5. Standard Garrison Belt & Brass Buckle (no Sam Browne / Waist Sash on Bravos)
    drawRect(beltCol, Offset(cx - w * 0.238f, h * 0.748f), Size(w * 0.476f, h * 0.034f))
    drawRoundRect(trimCol, Offset(cx - w * 0.038f, h * 0.743f), Size(w * 0.076f, h * 0.044f), CornerRadius(3f, 3f))

    // 6. Shoulder Epaulets (Officer Rank Boards vs Shirt-Color Enlisted Tabs) + Sleeve Rank Chevrons
    val epColor = when {
        tier == RankTier.FLAG_OFFICER -> trimCol
        isOfficer -> Color(0xFF13223A)
        else -> shirtCol
    }
    val epBorder = if (isOfficer) trimCol else shirtShadeCol
    val epTop = h * 0.296f
    val epHeight = if (isOfficer) h * 0.035f else h * 0.028f
    val epWidth = w * 0.110f
    val epLeftX = cx - w * 0.146f - epWidth
    val epRightX = cx + w * 0.146f
    drawRoundRect(epColor, Offset(epLeftX, epTop), Size(epWidth, epHeight), CornerRadius(4f, 4f))
    drawRoundRect(epBorder, Offset(epLeftX, epTop), Size(epWidth, epHeight), CornerRadius(4f, 4f), style = Stroke(if (isOfficer) 1.7f else 1.0f))
    drawRoundRect(epColor, Offset(epRightX, epTop), Size(epWidth, epHeight), CornerRadius(4f, 4f))
    drawRoundRect(epBorder, Offset(epRightX, epTop), Size(epWidth, epHeight), CornerRadius(4f, 4f), style = Stroke(if (isOfficer) 1.7f else 1.0f))

    if (tier == RankTier.FLAG_OFFICER) {
        val starCount = operator.rank.insigniaCount.coerceIn(1, 4)
        repeat(starCount) { s ->
            val offset = (s - (starCount - 1) / 2f) * (w * 0.023f)
            drawStar(Offset(cx - w * 0.201f + offset, h * 0.314f), w * 0.013f, w * 0.0055f, Color(0xFF14181F))
            drawStar(Offset(cx + w * 0.201f + offset, h * 0.314f), w * 0.013f, w * 0.0055f, Color(0xFF14181F))
        }
    } else if (isOfficer) {
        val epCenterY = epTop + epHeight * 0.5f
        drawOfficerRankDevice(
            rank = operator.rank,
            center = Offset(epLeftX + epWidth * 0.5f, epCenterY),
            boxW = epWidth * 0.68f,
            boxH = epHeight * 0.78f,
            outlineColor = Color(0xFF0A101B)
        )
        drawOfficerRankDevice(
            rank = operator.rank,
            center = Offset(epRightX + epWidth * 0.5f, epCenterY),
            boxW = epWidth * 0.68f,
            boxH = epHeight * 0.78f,
            outlineColor = Color(0xFF0A101B)
        )
    } else if (operator.rank != OperatorRank.PVT) {
        // Enlisted/NCO sleeve chevrons placed cleanly in the mid-bicep short sleeve zone (y = 0.422f)
        val badgeBack = Color(0xFF28311F)
        val badgeGold = Color(0xFFE0BE5A)
        drawWrappedSleeveRankInsignia(
            sideSign = -1f,
            rank = operator.rank,
            tunicCol = badgeBack,
            lapelCol = badgeBack,
            trimCol = badgeGold,
            sleeveFaceX = ::shortSleeveFaceX,
            w = w,
            h = h,
            startFrac = 0.420f
        )
        drawWrappedSleeveRankInsignia(
            sideSign = 1f,
            rank = operator.rank,
            tunicCol = badgeBack,
            lapelCol = badgeBack,
            trimCol = badgeGold,
            sleeveFaceX = ::shortSleeveFaceX,
            w = w,
            h = h,
            startFrac = 0.420f
        )
    }

    // 6b. Shoulder cords: same braid art as the dress coats, anchored on the shirt's armhole seam
    drawShoulderCords(
        w = w,
        h = h,
        cx = cx,
        leftCordDef = leftCordDef,
        rightCordDef = rightCordDef,
        trimCol = trimCol,
        inset = 0.006f,
        drop = -0.004f
    )

    // 7. Right Chest: Unit Citation Bar + Squad Leader Pin + Engraved Nameplate
    val rightChestCenterX = cx - w * 0.168f
    drawEngravedNameplateAndLeaderPin(
        rightChestCenterX = rightChestCenterX,
        w = w,
        h = h,
        trimCol = trimCol,
        callsign = operator.callsign,
        unitAwards = unitAwards,
        leaderRole = leaderRole,
        textMeasurer = textMeasurer,
        environmentBadge = environmentBadge,
        sfTier = com.example.domain.TacticalCalculator.sfTier(operator)
    )

    // 8. Left Chest: Ribbons ONLY (no full hanging medals in Bravos) + School Pins + Marksmanship
    val leftChestCenterX = cx + w * 0.168f
    var topDecorationY = h * (0.528f - CHEST_LIFT)
    if (operator.dressDisplayMode != DressDisplayMode.CLEAN && rackDecorations.isNotEmpty()) {
        val rackCols = 3
        // Art 6a: ribbons at their real proportions (about 3.5 wide to 1 tall), not near-square tiles
        val ribW = w * 0.038f
        val ribH = h * 0.0080f
        val gapX = w * 0.0025f
        val gapY = h * 0.0022f
        val totalRackW = rackCols * ribW + (rackCols - 1) * gapX
        val rackLeftEdge = leftChestCenterX - totalRackW / 2f
        // Art 6a: the bottom row sits just above the pocket flap, as worn
        val baseBottomY = h * (0.550f - CHEST_LIFT)
        val displayMedals = rackDecorations.take(TacticalCalculator.MAX_WORN_RIBBONS)
        val rows = TacticalCalculator.layoutRibbonRackRows(displayMedals, rackCols)
        val totalRows = rows.size
        topDecorationY = baseBottomY - (totalRows - 1) * (ribH + gapY)

        rows.forEachIndexed { rowFromTop, rowItems ->
            val rowFromBottom = (totalRows - 1) - rowFromTop
            val itemsInThisRow = rowItems.size
            val rowTotalWidth = itemsInThisRow * ribW + (itemsInThisRow - 1) * gapX
            val rowCenterOffset = (totalRackW - rowTotalWidth) / 2f
            val ry = baseBottomY - rowFromBottom * (ribH + gapY)

            rowItems.forEachIndexed { colInRow, medal ->
                val rx = rackLeftEdge + rowCenterOffset + colInRow * (ribW + gapX)
                drawRibbonBar(Offset(rx, ry), Size(ribW, ribH), medal.ribbonStripeHexes)
                awardDevices[medal.id]?.let { dev ->
                    if (dev.hasAnyDevice) {
                        drawRibbonDeviceOverlay(
                            rectTopLeft = Offset(rx, ry),
                            rectSize = Size(ribW, ribH),
                            deviceInfo = dev
                        )
                    }
                }
            }
        }
    }

    wornRegalia.chestPinAboveRack?.let { insig ->
        val aboveY = (topDecorationY - h * 0.035f).coerceIn(h * (0.368f - CHEST_LIFT), h * (0.502f - CHEST_LIFT))
        drawBravosContrastSchoolBadge(
            shape = insig.insigniaShape ?: InsigniaShape.WINGS,
            center = Offset(leftChestCenterX, aboveY),
            badgeW = w * 0.088f,
            badgeH = h * 0.044f,
            col = Color(insig.insigniaColorHex),
            combatJumps = operator.combatJumps
        )
    }
    val showMarksmanshipOnPocket = marksmanshipTier != MarksmanshipBadgeTier.NONE
    val leftPocketSchoolPin = wornRegalia.chestPinLeftPocket
    if (leftPocketSchoolPin != null && showMarksmanshipOnPocket) {
        drawBravosContrastSchoolBadge(
            shape = leftPocketSchoolPin.insigniaShape ?: InsigniaShape.WINGS,
            center = Offset(leftChestCenterX - w * 0.029f, h * (0.638f - CHEST_LIFT)),
            badgeW = w * 0.054f,
            badgeH = h * 0.036f,
            col = Color(leftPocketSchoolPin.insigniaColorHex),
            combatJumps = operator.combatJumps
        )
        drawMarksmanshipBadge(
            tier = marksmanshipTier,
            center = Offset(leftChestCenterX + w * 0.031f, h * (0.638f - CHEST_LIFT)),
            badgeW = w * 0.050f,
            badgeH = h * 0.038f
        )
    } else {
        leftPocketSchoolPin?.let { insig ->
            drawBravosContrastSchoolBadge(
                shape = insig.insigniaShape ?: InsigniaShape.WINGS,
                center = Offset(leftChestCenterX, h * (0.638f - CHEST_LIFT)),
                badgeW = w * 0.088f,
                badgeH = h * 0.044f,
                col = Color(insig.insigniaColorHex),
                combatJumps = operator.combatJumps
            )
        }
        if (showMarksmanshipOnPocket) {
            drawMarksmanshipBadge(
                tier = marksmanshipTier,
                center = Offset(leftChestCenterX, h * (0.638f - CHEST_LIFT)),
                badgeW = w * 0.068f,
                badgeH = h * 0.042f
            )
        }
    }
    wornRegalia.chestPinRightPocket?.let { insig ->
        drawBravosContrastSchoolBadge(
            shape = insig.insigniaShape ?: InsigniaShape.WINGS,
            center = Offset(rightChestCenterX, h * (rightPocketBadgeY(com.example.domain.TacticalCalculator.sfTier(operator)) - CHEST_LIFT)),
            badgeW = w * 0.088f,
            badgeH = h * 0.044f,
            col = Color(insig.insigniaColorHex),
            combatJumps = operator.combatJumps
        )
    }

    // 9. Head, Jawline & Beret Headgear (Bravos strictly wears Beret)
    val beretStyle = if (operator.headgearStyle.kind == HeadgearKind.BERET) {
        operator.headgearStyle
    } else {
        HeadgearStyle.OBSIDIAN_BERET
    }
    drawHeadAndDressHeadgear(
        w = w,
        h = h,
        cx = cx,
        operator = operator.copy(headgearStyle = beretStyle),
        tier = tier,
        skinTone = skinTone,
        skinShadow = skinShadow,
        headgearCol = Color(beretStyle.colorHex),
        trimCol = trimCol
    )
}

/**
 * School badges on the light Bravos shirts (khaki enlisted, light blue officer) wash out,
 * so draw a dark drop shadow behind each badge and deepen very light badge colors.
 */
private fun DrawScope.drawBravosContrastSchoolBadge(
    shape: InsigniaShape,
    center: Offset,
    badgeW: Float,
    badgeH: Float,
    col: Color,
    combatJumps: Int = 0
) {
    // Art 6b: on the light Bravos shirt the badge gets darker silver and a stronger outline, wreaths included
    val bodyCol = if (col.luminance() > 0.45f) lerp(col, Color(0xFF4F5A6B), 0.30f) else col
    drawDetailedChestSchoolBadge(shape = shape, center = center, badgeW = badgeW, badgeH = badgeH, col = bodyCol, combatJumps = combatJumps, onLight = true)
}
