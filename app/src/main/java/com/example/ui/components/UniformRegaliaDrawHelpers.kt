package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.MarksmanshipBadgeTier
import com.example.domain.model.OperatorSpecialty
import com.example.domain.model.UnitPatchStyle
import kotlin.math.cos
import kotlin.math.sin

internal fun DrawScope.drawEngravedNameplateAndLeaderPin(
    rightChestCenterX: Float,
    w: Float,
    h: Float,
    trimCol: Color,
    callsign: String,
    hasUnitCitation: Boolean,
    showLeaderPin: Boolean,
    textMeasurer: TextMeasurer
) {
    // 1. Unit Citation Bar shifted cleanly higher if Leader Pin is present so all 3 items breathe
    if (hasUnitCitation) {
        val citTop = if (showLeaderPin) h * 0.514f else h * 0.526f
        drawRect(
            color = Color(0xFF1E4C8E),
            topLeft = Offset(rightChestCenterX - w * 0.042f, citTop),
            size = Size(w * 0.084f, h * 0.020f)
        )
        drawRect(
            color = trimCol,
            topLeft = Offset(rightChestCenterX - w * 0.042f, citTop),
            size = Size(w * 0.084f, h * 0.020f),
            style = Stroke(1.3f)
        )
    }

    // 2. Small Brass/Enamel Squad Leader Pin directly above the nameplate
    if (showLeaderPin) {
        val pinCenterY = h * 0.552f
        val pinHalfW = (w * 0.024f).coerceAtLeast(3.5f)
        val pinHalfH = (h * 0.0085f).coerceAtLeast(1.8f)
        // Shield bar backing in command crimson & antique brass
        drawRoundRect(
            color = Color(0xFF8C2525),
            topLeft = Offset(rightChestCenterX - pinHalfW, pinCenterY - pinHalfH),
            size = Size(pinHalfW * 2f, pinHalfH * 2f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = Color(0xFFF5D576),
            topLeft = Offset(rightChestCenterX - pinHalfW, pinCenterY - pinHalfH),
            size = Size(pinHalfW * 2f, pinHalfH * 2f),
            cornerRadius = CornerRadius(2f, 2f),
            style = Stroke(width = 1.1f)
        )
        // Miniature 5-point command star & chevron wings inside the pin
        drawMiniStar(
            center = Offset(rightChestCenterX, pinCenterY),
            outerR = (pinHalfH * 0.78f).coerceAtLeast(1.4f),
            innerR = (pinHalfH * 0.36f).coerceAtLeast(0.6f),
            color = Color(0xFFF8E18A)
        )
    }

    // 3. Engraved Callsign Nameplate
    val plateX = rightChestCenterX - w * 0.056f
    val plateY = h * 0.568f
    val plateW = w * 0.112f
    val plateH = h * 0.026f

    drawRoundRect(
        color = Color(0xFF14181F),
        topLeft = Offset(plateX, plateY),
        size = Size(plateW, plateH),
        cornerRadius = CornerRadius(2f, 2f)
    )
    drawRoundRect(
        color = trimCol,
        topLeft = Offset(plateX, plateY),
        size = Size(plateW, plateH),
        cornerRadius = CornerRadius(2f, 2f),
        style = Stroke(1.2f)
    )

    val label = callsign.trim().uppercase().take(10).ifEmpty { "AEGIS" }
    val fontSizeSp = (plateH * 0.28f / density).coerceIn(4.5f, 8.5f).sp
    val measured = textMeasurer.measure(
        text = label,
        style = TextStyle(
            color = Color(0xFFF0F4FA),
            fontSize = fontSizeSp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp
        ),
        maxLines = 1,
        softWrap = false
    )
    val tx = (rightChestCenterX - measured.size.width / 2f)
        .coerceIn(plateX + 2f, (plateX + plateW - measured.size.width - 2f).coerceAtLeast(plateX + 2f))
    val ty = plateY + (plateH - measured.size.height) / 2f
    clipRect(
        left = plateX + 1.5f,
        top = plateY + 1f,
        right = plateX + plateW - 1.5f,
        bottom = plateY + plateH - 1f
    ) {
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(tx, ty)
        )
    }
}

internal fun DrawScope.drawWrappedOverseasBars(
    sideSign: Float,
    barCount: Int,
    lapelCol: Color,
    trimCol: Color,
    sleeveFaceX: (Float, Float, Float) -> Float,
    w: Float,
    h: Float
) {
    if (barCount <= 0) return
    val barThick = h * 0.0080f
    val stepFrac = 0.0145f
    val baseFrac = 0.818f
    val edgeOutline = lapelCol.copy(alpha = 0.82f)

    // Horizontal gold overseas combat bars stacked above the right cuff
    repeat(barCount.coerceIn(1, 5)) { idx ->
        val f = baseFrac - idx * stepFrac
        val yMid = h * f
        val xLeft = minOf(sleeveFaceX(sideSign, f, 0.30f), sleeveFaceX(sideSign, f, 0.78f))
        val xRight = maxOf(sleeveFaceX(sideSign, f, 0.30f), sleeveFaceX(sideSign, f, 0.78f))
        val barRect = Size((xRight - xLeft).coerceAtLeast(2f), barThick)
        drawRect(
            color = Color(0xFF10151E),
            topLeft = Offset(xLeft - 1f, yMid - barThick * 0.5f - 0.8f),
            size = Size(barRect.width + 2f, barRect.height + 1.6f)
        )
        drawRect(
            color = trimCol,
            topLeft = Offset(xLeft, yMid - barThick * 0.5f),
            size = barRect
        )
        drawRect(
            color = edgeOutline,
            topLeft = Offset(xLeft, yMid - barThick * 0.5f),
            size = barRect,
            style = Stroke(width = (w * 0.002f).coerceAtLeast(0.5f))
        )
    }
}

internal fun DrawScope.drawMarksmanshipBadge(
    tier: MarksmanshipBadgeTier,
    center: Offset,
    badgeW: Float,
    badgeH: Float
) {
    if (tier == MarksmanshipBadgeTier.NONE || tier == MarksmanshipBadgeTier.AUTO) return
    val cx = center.x
    val cy = center.y
    val metal = Color(tier.badgeColorHex)
    val darkBacking = Color(0xFF121822)

    // Clasp Bar(s) & suspension rings above pendant
    val claspW = badgeW * 0.76f
    val claspH = badgeH * 0.14f
    val claspCount = if (tier == MarksmanshipBadgeTier.EXPERT) 2 else 1
    var lastClaspBottomY = cy - badgeH * 0.30f
    repeat(claspCount) { idx ->
        val topY = cy - badgeH * 0.46f + idx * (claspH * 1.22f)
        lastClaspBottomY = topY + claspH
        drawRect(darkBacking, Offset(cx - claspW * 0.5f, topY), Size(claspW, claspH))
        drawRect(metal, Offset(cx - claspW * 0.5f, topY), Size(claspW, claspH), style = Stroke(1.0f))
        drawLine(
            metal,
            Offset(cx - claspW * 0.32f, topY + claspH * 0.5f),
            Offset(cx + claspW * 0.32f, topY + claspH * 0.5f),
            strokeWidth = 1.0f
        )
    }

    // Qualification Maltese Cross & Target Ring
    val crossY = cy + badgeH * 0.18f
    val r = badgeH * 0.27f
    val linkTopY = lastClaspBottomY
    val linkBotY = crossY - r * 0.88f
    if (linkBotY > linkTopY) {
        drawLine(metal, Offset(cx - claspW * 0.22f, linkTopY), Offset(cx - claspW * 0.16f, linkBotY), strokeWidth = 1.0f)
        drawLine(metal, Offset(cx + claspW * 0.22f, linkTopY), Offset(cx + claspW * 0.16f, linkBotY), strokeWidth = 1.0f)
    }
    if (tier == MarksmanshipBadgeTier.EXPERT) {
        // Expert wreath ring
        drawArc(
            color = metal,
            startAngle = 25f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(cx - r * 1.32f, crossY - r * 1.1f),
            size = Size(r * 2.64f, r * 2.2f),
            style = Stroke(width = 1.5f, cap = StrokeCap.Round)
        )
        drawArc(
            color = metal,
            startAngle = 205f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(cx - r * 1.32f, crossY - r * 1.1f),
            size = Size(r * 2.64f, r * 2.2f),
            style = Stroke(width = 1.5f, cap = StrokeCap.Round)
        )
    }

    if (tier == MarksmanshipBadgeTier.SHARPSHOOTER || tier == MarksmanshipBadgeTier.EXPERT) {
        val arm = r * 0.88f
        val crossPath = Path().apply {
            moveTo(cx - arm * 0.4f, crossY - arm)
            lineTo(cx + arm * 0.4f, crossY - arm)
            lineTo(cx + arm * 0.24f, crossY - arm * 0.24f)
            lineTo(cx + arm, crossY - arm * 0.4f)
            lineTo(cx + arm, crossY + arm * 0.4f)
            lineTo(cx + arm * 0.24f, crossY + arm * 0.24f)
            lineTo(cx + arm * 0.4f, crossY + arm)
            lineTo(cx - arm * 0.4f, crossY + arm)
            lineTo(cx - arm * 0.24f, crossY + arm * 0.24f)
            lineTo(cx - arm, crossY + arm * 0.4f)
            lineTo(cx - arm, crossY - arm * 0.4f)
            lineTo(cx - arm * 0.24f, crossY - arm * 0.24f)
            close()
        }
        drawPath(crossPath, color = metal)
        drawPath(crossPath, color = darkBacking, style = Stroke(width = 0.8f, join = StrokeJoin.Round))
    } else {
        // Marksman cross pattee simplified
        drawLine(metal, Offset(cx, crossY - r * 0.85f), Offset(cx, crossY + r * 0.85f), strokeWidth = 2.1f)
        drawLine(metal, Offset(cx - r * 0.85f, crossY), Offset(cx + r * 0.85f, crossY), strokeWidth = 2.1f)
    }
    drawCircle(darkBacking, radius = r * 0.34f, center = Offset(cx, crossY))
    drawCircle(metal, radius = r * 0.20f, center = Offset(cx, crossY))
}

internal fun DrawScope.drawRibbonDeviceOverlay(
    rectTopLeft: Offset,
    rectSize: Size,
    deviceInfo: UniformRegaliaRules.AwardDeviceInfo
) {
    if (!deviceInfo.hasAnyDevice) return
    val cy = rectTopLeft.y + rectSize.height * 0.5f
    val devTokens = buildList {
        if (deviceInfo.hasValorV) add(0) // 0 = Brass V device
        repeat(deviceInfo.silverOakLeaves.coerceAtMost(2)) { add(1) } // 1 = Silver OLC
        repeat(deviceInfo.bronzeOakLeaves.coerceAtMost(3)) { add(2) } // 2 = Bronze OLC
    }.take(4)

    val spacing = (rectSize.width / (devTokens.size + 1)).coerceAtLeast(2.2f)
    val startX = rectTopLeft.x + (rectSize.width - (devTokens.size - 1) * spacing) * 0.5f
    val r = (rectSize.height * 0.26f).coerceIn(1.3f, 3.0f)

    devTokens.forEachIndexed { idx, kind ->
        val x = startX + idx * spacing
        when (kind) {
            0 -> {
                // Miniature Bronze "V" Valor device
                val vp = Path().apply {
                    moveTo(x - r * 0.9f, cy - r * 0.95f)
                    lineTo(x, cy + r * 0.95f)
                    lineTo(x + r * 0.9f, cy - r * 0.95f)
                }
                drawPath(vp, color = Color(0xFF10141B), style = Stroke(width = 2.0f, cap = StrokeCap.Round))
                drawPath(vp, color = Color(0xFFF5D576), style = Stroke(width = 1.25f, cap = StrokeCap.Round))
            }
            1, 2 -> {
                val fill = if (kind == 1) Color(0xFFF0F4FA) else Color(0xFFD2873E)
                val diamond = Path().apply {
                    moveTo(x, cy - r)
                    lineTo(x + r * 0.85f, cy)
                    lineTo(x, cy + r)
                    lineTo(x - r * 0.85f, cy)
                    close()
                }
                drawPath(diamond, color = fill)
                drawPath(diamond, color = Color(0xFF10141B), style = Stroke(width = 0.65f))
            }
        }
    }
}

internal fun DrawScope.drawEnhancedFieldWeapon(
    w: Float,
    h: Float,
    cx: Float,
    weaponId: String?
) {
    val rifleMetal = Color(0xFF161B23)
    val furniture = when {
        weaponId?.contains("mk20") == true || weaponId?.contains("hk416") == true -> Color(0xFF8C7A56)
        else -> Color(0xFF28303D)
    }
    val opticGlass = Color(0xFF5AC8FA)

    when {
        weaponId?.contains("mk14") == true || weaponId?.contains("mk20") == true -> {
            // Precision Long DMR / Sniper Rifle slung diagonally across chest
            drawLine(rifleMetal, Offset(cx - w * 0.22f, h * 0.36f), Offset(cx + w * 0.26f, h * 0.91f), strokeWidth = 6.2f, cap = StrokeCap.Round)
            // Receiver & chassis body
            drawLine(furniture, Offset(cx - w * 0.08f, h * 0.51f), Offset(cx + w * 0.12f, h * 0.74f), strokeWidth = 9.5f, cap = StrokeCap.Butt)
            // Telescopic scope mounted cleanly above receiver
            drawLine(rifleMetal, Offset(cx - w * 0.02f, h * 0.49f), Offset(cx + w * 0.10f, h * 0.63f), strokeWidth = 4.2f, cap = StrokeCap.Round)
            drawCircle(opticGlass, radius = w * 0.008f, center = Offset(cx + w * 0.10f, h * 0.63f))
            // Suppressor & folded bipod
            drawLine(Color(0xFF222A35), Offset(cx + w * 0.20f, h * 0.84f), Offset(cx + w * 0.27f, h * 0.92f), strokeWidth = 7.4f, cap = StrokeCap.Round)
        }
        weaponId?.contains("mp7") == true -> {
            // Compact PDW + suppressor & micro red-dot
            drawLine(rifleMetal, Offset(cx - w * 0.12f, h * 0.49f), Offset(cx + w * 0.16f, h * 0.77f), strokeWidth = 7.2f, cap = StrokeCap.Round)
            drawLine(furniture, Offset(cx - w * 0.06f, h * 0.55f), Offset(cx + w * 0.08f, h * 0.69f), strokeWidth = 9.0f, cap = StrokeCap.Butt)
            drawCircle(opticGlass, radius = w * 0.007f, center = Offset(cx + w * 0.01f, h * 0.56f))
        }
        weaponId?.contains("m249") == true -> {
            // Heavy Squad Automatic Weapon + ammo box drum + carry handle
            drawLine(rifleMetal, Offset(cx - w * 0.20f, h * 0.40f), Offset(cx + w * 0.24f, h * 0.88f), strokeWidth = 7.6f, cap = StrokeCap.Round)
            drawRoundRect(
                color = Color(0xFF3A4A30),
                topLeft = Offset(cx - w * 0.02f, h * 0.62f),
                size = Size(w * 0.075f, h * 0.060f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            drawLine(furniture, Offset(cx - w * 0.08f, h * 0.53f), Offset(cx + w * 0.09f, h * 0.71f), strokeWidth = 10.5f, cap = StrokeCap.Butt)
        }
        else -> {
            // M4A1 / XM7 Tactical Carbine with holographic optic & PEQ laser
            drawLine(rifleMetal, Offset(cx - w * 0.18f, h * 0.41f), Offset(cx + w * 0.22f, h * 0.88f), strokeWidth = 6.8f, cap = StrokeCap.Round)
            drawLine(furniture, Offset(cx - w * 0.06f, h * 0.53f), Offset(cx + w * 0.10f, h * 0.71f), strokeWidth = 9.2f, cap = StrokeCap.Butt)
            drawRoundRect(
                color = Color(0xFF1F2733),
                topLeft = Offset(cx + w * 0.005f, h * 0.545f),
                size = Size(w * 0.036f, h * 0.022f),
                cornerRadius = CornerRadius(2f, 2f)
            )
            drawCircle(opticGlass, radius = w * 0.006f, center = Offset(cx + w * 0.035f, h * 0.556f))
        }
    }
}

internal fun DrawScope.drawFieldCombatPatchAndLeaderTab(
    w: Float,
    h: Float,
    cx: Float,
    specialty: OperatorSpecialty,
    combatPatch: UnitPatchStyle,
    isSquadLeader: Boolean,
    accentCamo: Color
) {
    // Combat Patch on wearer's right sleeve (viewer's left side of field shirt)
    if (combatPatch != UnitPatchStyle.NONE) {
        val patchColor = if (combatPatch == UnitPatchStyle.AUTO_MOS) accentCamo else Color(combatPatch.primaryHex)
        val patchBorder = if (combatPatch == UnitPatchStyle.AUTO_MOS) Color(0xFFD8B24C) else Color(combatPatch.accentHex)
        drawRoundRect(
            color = patchColor,
            topLeft = Offset(cx - w * 0.342f, h * 0.470f),
            size = Size(w * 0.044f, h * 0.050f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = patchBorder,
            topLeft = Offset(cx - w * 0.342f, h * 0.470f),
            size = Size(w * 0.044f, h * 0.050f),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 1.2f)
        )
        // Subtle specialty cross/star accent inside combat patch
        drawMiniStar(
            center = Offset(cx - w * 0.320f, h * 0.495f),
            outerR = (w * 0.011f).coerceAtLeast(1.8f),
            innerR = (w * 0.005f).coerceAtLeast(0.8f),
            color = if (specialty == OperatorSpecialty.MEDIC) Color.White else patchBorder
        )
    }

    // Green/Brass Squad Leader Shoulder-Loop / Chest Identification Tab
    if (isSquadLeader) {
        val tabW = w * 0.078f
        val tabH = h * 0.013f
        val tabLeft = cx - tabW * 0.5f
        val tabTop = h * 0.482f
        drawRoundRect(
            color = Color(0xFF285938),
            topLeft = Offset(tabLeft, tabTop),
            size = Size(tabW, tabH),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = Color(0xFFF2D272),
            topLeft = Offset(tabLeft, tabTop),
            size = Size(tabW, tabH),
            cornerRadius = CornerRadius(2f, 2f),
            style = Stroke(width = 1.0f)
        )
        drawMiniStar(
            center = Offset(cx, tabTop + tabH * 0.5f),
            outerR = (tabH * 0.42f).coerceAtLeast(1.3f),
            innerR = (tabH * 0.19f).coerceAtLeast(0.6f),
            color = Color(0xFFF5D576)
        )
    }
}

private fun DrawScope.drawMiniStar(
    center: Offset,
    outerR: Float,
    innerR: Float,
    color: Color,
    points: Int = 5
) {
    val path = Path()
    val step = Math.PI / points
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outerR else innerR
        val angle = -Math.PI / 2.0 + i * step
        val px = center.x + (r * cos(angle)).toFloat()
        val py = center.y + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, color = color)
}
