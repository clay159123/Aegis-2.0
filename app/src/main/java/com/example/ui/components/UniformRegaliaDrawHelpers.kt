package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.example.domain.model.LeaderRole
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.MarksmanshipBadgeTier
import com.example.domain.model.MedalDefinition
import com.example.domain.model.OperatorSpecialty
import com.example.domain.model.UnitPatchStyle
import com.example.domain.model.UnitAwardType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Top edge of the breast pockets as a fraction of figure height, shared by every dress
 * coat and the Bravos shirt. Ribbons, medals, nameplate, unit citation, leader pin and
 * pocket badges are all placed relative to it, so changing this one value moves the
 * pockets and everything on them together.
 */
internal const val CHEST_POCKET_TOP_FRACTION = 0.505f

/** Pocket top the original chest offsets were tuned against. */
private const val LEGACY_CHEST_POCKET_TOP_FRACTION = 0.565f

/** How far the chest group sits above its original position. */
internal const val CHEST_LIFT = LEGACY_CHEST_POCKET_TOP_FRACTION - CHEST_POCKET_TOP_FRACTION

internal fun DrawScope.drawEngravedNameplateAndLeaderPin(
    rightChestCenterX: Float,
    w: Float,
    h: Float,
    trimCol: Color,
    callsign: String,
    unitAwards: List<Pair<UnitAwardType, Int>>,
    leaderRole: LeaderRole,
    textMeasurer: TextMeasurer
) {
    val showLeaderPin = leaderRole != LeaderRole.NONE
    // 1. Unit award emblems in precedence order, side by side, shifted higher if the Leader Pin is present.
    //    Up to 3 per row (a second row sits above the first), each with gold frame and oak leaf clusters.
    if (unitAwards.isNotEmpty()) {
        val baseTop = if (showLeaderPin) h * (0.514f - CHEST_LIFT) else h * (0.526f - CHEST_LIFT)
        val emblemW = w * 0.050f
        val emblemH = h * 0.018f
        val gap = w * 0.004f
        unitAwards.take(6).chunked(3).forEachIndexed { row, rowAwards ->
            val rowW = rowAwards.size * emblemW + (rowAwards.size - 1) * gap
            val top = baseTop - row * (emblemH + gap)
            rowAwards.forEachIndexed { i, (type, count) ->
                val left = rightChestCenterX - rowW / 2f + i * (emblemW + gap)
                val stripeW = emblemW / type.stripes.size
                type.stripes.forEachIndexed { s, hex ->
                    drawRect(Color(hex), Offset(left + s * stripeW, top), Size(stripeW + 0.5f, emblemH))
                }
                drawRect(Color(type.frameHex), Offset(left, top), Size(emblemW, emblemH), style = Stroke(1.3f))
                drawRibbonDeviceOverlay(Offset(left, top), Size(emblemW, emblemH), UniformRegaliaRules.unitAwardDevices(count))
            }
        }
    }

    // 2. Leader pin directly above the nameplate: Squad Leader crimson with a gold star,
    //    Team Leader slightly smaller in dark blue with a silver border and chevron
    if (showLeaderPin) {
        val isTeamLeader = leaderRole == LeaderRole.TEAM_LEADER
        val scale = if (isTeamLeader) 0.85f else 1f
        val pinCenterY = h * (0.552f - CHEST_LIFT)
        val pinHalfW = (w * 0.024f * scale).coerceAtLeast(3.0f)
        val pinHalfH = (h * 0.0085f * scale).coerceAtLeast(1.6f)
        val enamel = if (isTeamLeader) Color(0xFF1E3A6E) else Color(0xFF8C2525)
        val border = if (isTeamLeader) Color(0xFFD8DEE8) else Color(0xFFF5D576)
        drawRoundRect(
            color = enamel,
            topLeft = Offset(rightChestCenterX - pinHalfW, pinCenterY - pinHalfH),
            size = Size(pinHalfW * 2f, pinHalfH * 2f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = border,
            topLeft = Offset(rightChestCenterX - pinHalfW, pinCenterY - pinHalfH),
            size = Size(pinHalfW * 2f, pinHalfH * 2f),
            cornerRadius = CornerRadius(2f, 2f),
            style = Stroke(width = 1.1f)
        )
        if (isTeamLeader) {
            drawLeaderChevron(Offset(rightChestCenterX, pinCenterY), pinHalfH * 1.1f, pinHalfH * 0.9f, Color(0xFFE6EBF2))
        } else {
            drawMiniStar(
                center = Offset(rightChestCenterX, pinCenterY),
                outerR = (pinHalfH * 0.78f).coerceAtLeast(1.4f),
                innerR = (pinHalfH * 0.36f).coerceAtLeast(0.6f),
                color = Color(0xFFF8E18A)
            )
        }
    }

    // 3. Engraved Callsign Nameplate
    val plateX = rightChestCenterX - w * 0.056f
    val plateY = h * (0.568f - CHEST_LIFT)
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
        // Regulation: four devices at most, silver before bronze
        repeat(deviceInfo.silverOakLeaves) { add(1) } // 1 = Silver OLC
        repeat(deviceInfo.bronzeOakLeaves) { add(2) } // 2 = Bronze OLC
        repeat(deviceInfo.silverServiceStars) { add(3) } // 3 = Silver service star
        repeat(deviceInfo.bronzeServiceStars) { add(4) } // 4 = Bronze service star
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
            3, 4 -> {
                // Five-point service star
                val fill = if (kind == 3) Color(0xFFF0F4FA) else Color(0xFFD2873E)
                val star = Path().apply {
                    for (p in 0 until 10) {
                        val rad = if (p % 2 == 0) r * 1.05f else r * 0.45f
                        val a = Math.toRadians(-90.0 + p * 36.0)
                        val px = x + (rad * kotlin.math.cos(a)).toFloat()
                        val py = cy + (rad * kotlin.math.sin(a)).toFloat()
                        if (p == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(star, color = fill)
                drawPath(star, color = Color(0xFF10141B), style = Stroke(width = 0.55f))
            }
        }
    }
}

internal fun DrawScope.drawEnhancedFieldWeapon(
    w: Float,
    h: Float,
    cx: Float,
    weaponId: String?,
    suppressed: Boolean = false
) {
    val metal = Color(0xFF1A1E24)
    val furniture = when {
        weaponId?.contains("mk20") == true || weaponId?.contains("hk416") == true || weaponId?.contains("urg") == true -> Color(0xFF8C7A56)
        else -> Color(0xFF2A3038)
    }
    val glass = Color(0xFF5AC8FA)
    val kind = when {
        weaponId == null -> return
        weaponId.contains("mk14") || weaponId.contains("mk20") -> "dmr"
        weaponId.contains("mp7") -> "pdw"
        weaponId.contains("m249") -> "lmg"
        else -> "rifle"
    }
    // Held at low ready across the waist: stock at the wearer's right hip, muzzle up toward the left shoulder
    val length = w * when (kind) { "dmr" -> 0.70f; "pdw" -> 0.40f; "lmg" -> 0.68f; else -> 0.60f }
    val stock = Offset(cx - w * 0.24f, h * 0.835f)
    val angle = -22f
    val u = length // local units: x along the barrel, y across (down positive)
    // Sling from the stock up over the shoulder, behind the rifle
    drawLine(Color(0xFF23261F).copy(alpha = 0.85f), stock, Offset(cx - w * 0.12f, h * 0.36f), strokeWidth = w * 0.007f, cap = StrokeCap.Round)
    withTransform({
        translate(stock.x, stock.y)
        rotate(angle, pivot = Offset.Zero)
    }) {
        fun rect(x0: Float, y0: Float, x1: Float, y1: Float, c: Color, r: Float = 2f) =
            drawRoundRect(c, Offset(x0 * u, y0 * u), Size((x1 - x0) * u, (y1 - y0) * u), CornerRadius(r, r))
        when (kind) {
            "pdw" -> {
                rect(0.00f, -0.035f, 0.18f, 0.020f, metal)                 // collapsed stock
                rect(0.14f, -0.060f, 0.70f, 0.040f, furniture, 4f)         // body
                rect(0.30f, 0.030f, 0.40f, 0.200f, metal)                  // grip + magazine
                rect(0.48f, -0.110f, 0.62f, -0.060f, metal)                // red dot
                drawCircle(glass, u * 0.015f, Offset(u * 0.60f, -u * 0.085f))
                rect(0.70f, -0.025f, if (suppressed) 1.00f else 0.82f, 0.015f, metal)
            }
            "lmg" -> {
                rect(0.00f, -0.050f, 0.20f, 0.040f, furniture, 3f)
                rect(0.18f, -0.060f, 0.55f, 0.040f, metal)                 // receiver
                rect(0.30f, 0.040f, 0.36f, 0.130f, metal)                  // grip
                rect(0.38f, 0.040f, 0.54f, 0.170f, Color(0xFF3A4A30), 3f)  // ammo box
                rect(0.40f, -0.105f, 0.50f, -0.060f, metal)                // carry handle
                rect(0.55f, -0.035f, 0.78f, 0.025f, furniture)             // handguard
                rect(0.78f, -0.015f, 1.00f, 0.010f, metal)                 // barrel
                drawLine(metal, Offset(u * 0.80f, u * 0.01f), Offset(u * 0.86f, u * 0.10f), strokeWidth = u * 0.012f)
                drawLine(metal, Offset(u * 0.82f, u * 0.01f), Offset(u * 0.78f, u * 0.10f), strokeWidth = u * 0.012f)
            }
            else -> {
                val dmr = kind == "dmr"
                rect(0.00f, -0.040f, 0.16f, 0.045f, furniture, 3f)         // stock
                rect(0.10f, -0.060f, 0.18f, -0.035f, furniture)            // cheek riser
                rect(0.15f, -0.020f, 0.24f, 0.010f, metal)                 // buffer tube
                rect(0.22f, -0.045f, 0.47f, 0.030f, metal)                 // receiver
                rect(0.31f, 0.025f, 0.36f, 0.120f, metal)                  // pistol grip
                rect(0.39f, 0.025f, 0.47f, if (dmr) 0.120f else 0.150f, if (dmr) metal else furniture, 2f) // magazine
                rect(0.47f, -0.040f, if (dmr) 0.80f else 0.76f, 0.025f, furniture, 3f) // handguard
                val muzzleEnd = if (suppressed || dmr) 1.00f else 0.90f
                rect(if (dmr) 0.80f else 0.76f, -0.014f, muzzleEnd, 0.008f, metal)
                if (suppressed || dmr) rect(0.86f, -0.024f, 1.00f, 0.018f, metal, 3f)
                // Optic: scope on the DMR, holographic sight on carbines
                if (dmr) {
                    rect(0.24f, -0.115f, 0.46f, -0.065f, metal, 4f)
                    drawCircle(glass, u * 0.016f, Offset(u * 0.45f, -u * 0.090f))
                    drawLine(metal, Offset(u * 0.70f, u * 0.025f), Offset(u * 0.66f, u * 0.11f), strokeWidth = u * 0.010f)
                } else {
                    rect(0.30f, -0.100f, 0.41f, -0.045f, metal)
                    drawCircle(glass, u * 0.013f, Offset(u * 0.40f, -u * 0.072f))
                    rect(0.58f, -0.075f, 0.66f, -0.040f, Color(0xFF3B3F2E))   // laser module
                }
            }
        }
    }
    // Gloved hands: firing hand on the grip, support hand on the handguard
    val glove = Color(0xFF3A3528)
    val gripAt = when (kind) { "pdw" -> 0.36f; "lmg" -> 0.33f; else -> 0.335f }
    val supportAt = when (kind) { "pdw" -> 0.58f; "lmg" -> 0.66f; "dmr" -> 0.66f; else -> 0.62f }
    withTransform({
        translate(stock.x, stock.y)
        rotate(angle, pivot = Offset.Zero)
    }) {
        drawOval(glove, Offset(u * (gripAt - 0.045f), u * 0.030f), Size(u * 0.090f, u * 0.075f))
        drawOval(glove, Offset(u * (supportAt - 0.050f), -u * 0.050f), Size(u * 0.100f, u * 0.085f))
        drawOval(Color.Black.copy(alpha = 0.25f), Offset(u * (supportAt - 0.050f), -u * 0.050f), Size(u * 0.100f, u * 0.085f), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
    }
}

internal fun DrawScope.drawMiniStar(
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

/**
 * Braided shoulder cords and aiguillettes (left = branch/school, right = staff/valor).
 * Shared by the dress coats and the Bravos shirt. [inset] moves the anchor toward the
 * body center and [drop] moves it down, both as fractions of the figure size, so each
 * garment can line the cord up with its own shoulder seam.
 */
internal fun DrawScope.drawShoulderCords(
    w: Float,
    h: Float,
    cx: Float,
    leftCordDef: MedalDefinition?,
    rightCordDef: MedalDefinition?,
    trimCol: Color,
    inset: Float = 0f,
    drop: Float = 0f
) {
    if (leftCordDef != null) {
        val cordCol = Color(leftCordDef.cordHex ?: 0xFF2C6FB8)
        val cordOuter = Path().apply {
            moveTo(cx - w * (0.252f - inset), h * (0.326f + drop))
            quadraticTo(cx - w * (0.262f - inset), h * (0.505f + drop), cx - w * (0.210f - inset), h * (0.480f + drop))
        }
        val cordInner = Path().apply {
            moveTo(cx - w * (0.238f - inset), h * (0.328f + drop))
            quadraticTo(cx - w * (0.244f - inset), h * (0.465f + drop), cx - w * (0.214f - inset), h * (0.456f + drop))
        }
        drawPath(cordOuter, color = cordCol, style = Stroke(width = 4.0f, cap = StrokeCap.Round))
        drawPath(
            cordInner,
            color = if (leftCordDef.id == "cord_general_aiguillette") Color(0xFFFFEA9F) else cordCol.copy(alpha = 0.85f),
            style = Stroke(width = 2.2f, cap = StrokeCap.Round)
        )
        when (leftCordDef.id) {
            "cord_valor_fourragere" -> {
                drawPath(cordOuter, color = Color(0xFF9C2D2D), style = Stroke(width = 1.5f, cap = StrokeCap.Round))
                drawLine(trimCol, Offset(cx - w * (0.216f - inset), h * (0.484f + drop)), Offset(cx - w * (0.212f - inset), h * (0.540f + drop)), strokeWidth = 2.4f, cap = StrokeCap.Round)
            }
            "cord_general_aiguillette" -> {
                drawLine(cordCol, Offset(cx - w * (0.232f - inset), h * (0.348f + drop)), Offset(cx - w * (0.222f - inset), h * (0.515f + drop)), strokeWidth = 1.6f)
                drawLine(Color(0xFFF5D576), Offset(cx - w * (0.222f - inset), h * (0.515f + drop)), Offset(cx - w * (0.218f - inset), h * (0.548f + drop)), strokeWidth = 2.4f, cap = StrokeCap.Round)
            }
            "cord_sf_emerald" -> {
                drawPath(cordOuter, color = Color(0xFFF2D272).copy(alpha = 0.85f), style = Stroke(width = 1.3f, cap = StrokeCap.Round))
            }
            "cord_signals_orange", "cord_medical_maroon", "cord_engineer_scarlet" -> {
                drawPath(cordOuter, color = Color.White.copy(alpha = 0.78f), style = Stroke(width = 1.3f, cap = StrokeCap.Round))
            }
            "cord_intel_teal" -> {
                drawPath(cordOuter, color = Color(0xFFDDE3EC).copy(alpha = 0.80f), style = Stroke(width = 1.3f, cap = StrokeCap.Round))
            }
            "cord_logistics_buff" -> {
                drawPath(cordOuter, color = Color(0xFF9C2D2D).copy(alpha = 0.80f), style = Stroke(width = 1.3f, cap = StrokeCap.Round))
            }
            "cord_aviation" -> {
                drawPath(cordOuter, color = Color(0xFFE89A2B).copy(alpha = 0.85f), style = Stroke(width = 1.3f, cap = StrokeCap.Round))
            }
        }
    }

    if (rightCordDef != null) {
        val aigCol = Color(rightCordDef.cordHex ?: 0xFFF2D272)
        val braidOuter = Path().apply {
            moveTo(cx + w * (0.250f - inset), h * (0.326f + drop))
            quadraticTo(cx + w * (0.256f - inset), h * (0.520f + drop), cx + w * (0.216f - inset), h * (0.486f + drop))
        }
        val braidInner = Path().apply {
            moveTo(cx + w * (0.236f - inset), h * (0.328f + drop))
            quadraticTo(cx + w * (0.240f - inset), h * (0.472f + drop), cx + w * (0.216f - inset), h * (0.456f + drop))
        }
        drawPath(braidOuter, color = aigCol, style = Stroke(width = 3.8f, cap = StrokeCap.Round))
        drawPath(
            braidInner,
            color = if (rightCordDef.id == "cord_general_aiguillette") Color(0xFFFFEA9F) else aigCol.copy(alpha = 0.85f),
            style = Stroke(width = 2.0f, cap = StrokeCap.Round)
        )
        when (rightCordDef.id) {
            "cord_general_aiguillette" -> {
                drawLine(aigCol, Offset(cx + w * (0.232f - inset), h * (0.348f + drop)), Offset(cx + w * (0.222f - inset), h * (0.515f + drop)), strokeWidth = 1.6f)
                drawLine(Color(0xFFF5D576), Offset(cx + w * (0.222f - inset), h * (0.515f + drop)), Offset(cx + w * (0.218f - inset), h * (0.548f + drop)), strokeWidth = 2.4f, cap = StrokeCap.Round)
            }
            "cord_valor_fourragere" -> {
                drawPath(braidOuter, color = Color(0xFF9C2D2D), style = Stroke(width = 1.5f, cap = StrokeCap.Round))
                drawLine(trimCol, Offset(cx + w * (0.216f - inset), h * (0.484f + drop)), Offset(cx + w * (0.212f - inset), h * (0.540f + drop)), strokeWidth = 2.4f, cap = StrokeCap.Round)
            }
            "cord_sf_emerald" -> {
                drawPath(braidOuter, color = Color(0xFFF2D272).copy(alpha = 0.85f), style = Stroke(width = 1.3f, cap = StrokeCap.Round))
            }
            "cord_signals_orange", "cord_medical_maroon", "cord_engineer_scarlet" -> {
                drawPath(braidOuter, color = Color.White.copy(alpha = 0.78f), style = Stroke(width = 1.3f, cap = StrokeCap.Round))
            }
            "cord_intel_teal" -> {
                drawPath(braidOuter, color = Color(0xFFDDE3EC).copy(alpha = 0.80f), style = Stroke(width = 1.3f, cap = StrokeCap.Round))
            }
            "cord_logistics_buff" -> {
                drawPath(braidOuter, color = Color(0xFF9C2D2D).copy(alpha = 0.80f), style = Stroke(width = 1.3f, cap = StrokeCap.Round))
            }
            "cord_aviation" -> {
                drawPath(braidOuter, color = Color(0xFFE89A2B).copy(alpha = 0.85f), style = Stroke(width = 1.3f, cap = StrokeCap.Round))
            }
        }
    }
}

/**
 * Emblem in the upper field of a unit shoulder patch. [unitW]/[unitH] are the figure's
 * width and height, so emblems scale with the uniform drawing.
 */
internal fun DrawScope.drawUnitPatchEmblem(
    patchStyle: UnitPatchStyle,
    center: Offset,
    unitW: Float,
    unitH: Float,
    accent: Color
) {
    val rx = unitW * 0.012f
    val ry = unitH * 0.011f
    val stroke = (unitW * 0.0045f).coerceAtLeast(1.2f)
    when (patchStyle) {
        // 1st: single star
        UnitPatchStyle.DIVISION_1 -> drawStar(center, rx * 1.05f, rx * 0.45f, accent)

        // 2nd: crossed sabers with hilts
        UnitPatchStyle.DIVISION_2 -> {
            val a1 = Offset(center.x - rx, center.y + ry)
            val b1 = Offset(center.x + rx, center.y - ry)
            val a2 = Offset(center.x + rx, center.y + ry)
            val b2 = Offset(center.x - rx, center.y - ry)
            drawLine(accent, a1, b1, strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(accent, a2, b2, strokeWidth = stroke, cap = StrokeCap.Round)
            val guard = rx * 0.35f
            drawLine(accent, Offset(a1.x - guard * 0.2f, a1.y - guard), Offset(a1.x + guard, a1.y + guard * 0.2f), strokeWidth = stroke * 0.8f)
            drawLine(accent, Offset(a2.x - guard, a2.y + guard * 0.2f), Offset(a2.x + guard * 0.2f, a2.y - guard), strokeWidth = stroke * 0.8f)
        }

        // 3rd: lion head (mane ring with a face and ears)
        UnitPatchStyle.DIVISION_3 -> {
            for (i in 0 until 10) {
                val ang = (i / 10f) * 2f * PI.toFloat()
                val inner = Offset(center.x + cos(ang) * rx * 0.55f, center.y + sin(ang) * ry * 0.55f)
                val outer = Offset(center.x + cos(ang) * rx * 1.05f, center.y + sin(ang) * ry * 1.05f)
                drawLine(accent, inner, outer, strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
            }
            drawCircle(accent, radius = rx * 0.55f, center = center)
            drawCircle(Color.Black.copy(alpha = 0.7f), radius = rx * 0.10f, center = Offset(center.x - rx * 0.2f, center.y - ry * 0.1f))
            drawCircle(Color.Black.copy(alpha = 0.7f), radius = rx * 0.10f, center = Offset(center.x + rx * 0.2f, center.y - ry * 0.1f))
        }

        // 4th: oak leaf with a midrib
        UnitPatchStyle.DIVISION_4 -> {
            val leaf = Path().apply {
                moveTo(center.x, center.y - ry * 1.1f)
                quadraticTo(center.x + rx * 1.1f, center.y - ry * 0.4f, center.x + rx * 0.5f, center.y)
                quadraticTo(center.x + rx * 1.0f, center.y + ry * 0.4f, center.x, center.y + ry * 1.1f)
                quadraticTo(center.x - rx * 1.0f, center.y + ry * 0.4f, center.x - rx * 0.5f, center.y)
                quadraticTo(center.x - rx * 1.1f, center.y - ry * 0.4f, center.x, center.y - ry * 1.1f)
                close()
            }
            drawPath(leaf, color = accent)
            drawLine(Color.Black.copy(alpha = 0.45f), Offset(center.x, center.y - ry * 0.9f), Offset(center.x, center.y + ry * 1.3f), strokeWidth = stroke * 0.6f)
        }

        // 5th: castle tower with crenellations
        UnitPatchStyle.DIVISION_5 -> {
            val left = center.x - rx * 0.7f
            val top = center.y - ry * 0.6f
            val width = rx * 1.4f
            val height = ry * 1.6f
            drawRect(accent, Offset(left, top), Size(width, height))
            val merlonW = width / 5f
            for (i in 0 until 3) {
                drawRect(accent, Offset(left + i * 2 * merlonW, top - ry * 0.45f), Size(merlonW, ry * 0.45f))
            }
            drawRect(Color.Black.copy(alpha = 0.55f), Offset(center.x - merlonW * 0.5f, top + height * 0.5f), Size(merlonW, height * 0.5f))
        }

        // 6th: eagle with spread wings
        UnitPatchStyle.DIVISION_6 -> {
            val wings = Path().apply {
                moveTo(center.x - rx * 1.1f, center.y - ry * 0.5f)
                quadraticTo(center.x - rx * 0.5f, center.y + ry * 0.1f, center.x, center.y + ry * 0.2f)
                quadraticTo(center.x + rx * 0.5f, center.y + ry * 0.1f, center.x + rx * 1.1f, center.y - ry * 0.5f)
                quadraticTo(center.x + rx * 0.6f, center.y + ry * 0.6f, center.x, center.y + ry * 0.7f)
                quadraticTo(center.x - rx * 0.6f, center.y + ry * 0.6f, center.x - rx * 1.1f, center.y - ry * 0.5f)
                close()
            }
            drawPath(wings, color = accent)
            drawCircle(accent, radius = rx * 0.28f, center = Offset(center.x, center.y - ry * 0.25f))
            drawLine(accent, Offset(center.x, center.y + ry * 0.5f), Offset(center.x, center.y + ry * 1.1f), strokeWidth = stroke)
        }

        // High Command: gold star over a white sword line
        UnitPatchStyle.SUPREME_HQ -> {
            drawStar(center, rx, rx * 0.43f, Color(0xFFF5D576))
            drawLine(
                Color.White.copy(alpha = 0.9f),
                Offset(center.x, center.y + ry * 1.1f),
                Offset(center.x, center.y + ry * 2.2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }

        // Special Forces: arrowhead with an upright dagger crossed by three lightning bolts
        UnitPatchStyle.SPECIAL_FORCES -> {
            val head = Path().apply {
                moveTo(center.x, center.y - ry * 1.25f)
                lineTo(center.x + rx * 0.95f, center.y + ry * 0.55f)
                quadraticTo(center.x, center.y + ry * 0.2f, center.x - rx * 0.95f, center.y + ry * 0.55f)
                close()
            }
            drawPath(head, color = accent, style = Stroke(width = stroke, join = StrokeJoin.Round))
            // Dagger: blade, guard and grip
            val steel = accent.copy(alpha = 0.95f)
            drawLine(steel, Offset(center.x, center.y - ry * 0.95f), Offset(center.x, center.y + ry * 0.35f), strokeWidth = stroke * 1.1f, cap = StrokeCap.Round)
            drawLine(steel, Offset(center.x - rx * 0.32f, center.y + ry * 0.12f), Offset(center.x + rx * 0.32f, center.y + ry * 0.12f), strokeWidth = stroke * 0.8f)
            // Three lightning bolts, lower left to upper right
            for (k in -1..1) {
                val oy = k * ry * 0.32f
                val bolt = Path().apply {
                    moveTo(center.x - rx * 0.6f, center.y + ry * 0.1f + oy)
                    lineTo(center.x - rx * 0.12f, center.y - ry * 0.12f + oy)
                    lineTo(center.x + rx * 0.08f, center.y + ry * 0.02f + oy)
                    lineTo(center.x + rx * 0.6f, center.y - ry * 0.25f + oy)
                }
                drawPath(bolt, color = accent, style = Stroke(width = stroke * 0.6f, join = StrokeJoin.Miter))
            }
        }

        // Aviation Brigade: spread wings around a three-blade rotor hub
        UnitPatchStyle.AVIATION_BRIGADE -> {
            val gold = Color(0xFFD8B24C)
            for (side in listOf(-1f, 1f)) {
                val wing = Path().apply {
                    moveTo(center.x + side * rx * 0.25f, center.y)
                    quadraticTo(center.x + side * rx * 0.8f, center.y - ry * 0.75f, center.x + side * rx * 1.15f, center.y - ry * 0.55f)
                    quadraticTo(center.x + side * rx * 0.8f, center.y + ry * 0.05f, center.x + side * rx * 0.25f, center.y + ry * 0.3f)
                    close()
                }
                drawPath(wing, color = accent)
            }
            for (k in 0 until 3) {
                val a = Math.toRadians(-90.0 + k * 120.0)
                drawLine(
                    gold,
                    center,
                    Offset(center.x + (rx * 0.62f * kotlin.math.cos(a)).toFloat(), center.y + (ry * 0.62f * kotlin.math.sin(a)).toFloat()),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
            drawCircle(gold, radius = rx * 0.2f, center = center)
        }

        // Unassigned: plain ring
        UnitPatchStyle.UNASSIGNED -> drawCircle(accent, radius = rx * 0.75f, center = center, style = Stroke(width = stroke))

        UnitPatchStyle.AUTO_MOS, UnitPatchStyle.NONE -> Unit
    }
}

/** A single chevron (point up) for Team Leader pins and tabs. */
internal fun DrawScope.drawLeaderChevron(center: Offset, halfW: Float, halfH: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x - halfW, center.y + halfH * 0.55f)
        lineTo(center.x, center.y - halfH * 0.45f)
        lineTo(center.x + halfW, center.y + halfH * 0.55f)
    }
    drawPath(p, color = color, style = Stroke(width = (halfH * 0.55f).coerceAtLeast(1.1f), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
