package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath

/** How the soldier's armor item looks on the field figure. */
internal enum class FieldLoadBearingStyle { NONE, CHEST_RIG, LIGHT_CARRIER, HEAVY_CARRIER }

internal fun fieldLoadBearingStyleFor(armorId: String?): FieldLoadBearingStyle = when (armorId) {
    null -> FieldLoadBearingStyle.NONE
    "arm_recon_rig" -> FieldLoadBearingStyle.CHEST_RIG
    "arm_avs_heavy", "arm_iotv_patrol" -> FieldLoadBearingStyle.HEAVY_CARRIER
    else -> FieldLoadBearingStyle.LIGHT_CARRIER
}

private val webbing = Color(0xFF1C1F18)

/** Collar, zipper and shoulder seams on the base field uniform (Art 4a: the pattern itself is FieldCamo). */
internal fun DrawScope.drawFieldUniformTexture(
    w: Float,
    h: Float,
    cx: Float,
    clip: Path,
    primary: Color,
    secondary: Color,
    accent: Color
) {
    // Mandarin collar standing open at the neck
    val collarL = Path().apply {
        moveTo(cx - w * 0.050f, h * 0.330f)
        lineTo(cx - w * 0.118f, h * 0.348f)
        lineTo(cx - w * 0.104f, h * 0.384f)
        lineTo(cx - w * 0.012f, h * 0.392f)
        close()
    }
    val collarR = Path().apply {
        moveTo(cx + w * 0.050f, h * 0.330f)
        lineTo(cx + w * 0.118f, h * 0.348f)
        lineTo(cx + w * 0.104f, h * 0.384f)
        lineTo(cx + w * 0.012f, h * 0.392f)
        close()
    }
    drawPath(collarL, secondary)
    drawPath(collarR, secondary)
    drawPath(collarL, Color.Black.copy(alpha = 0.35f), style = Stroke(width = 1f))
    drawPath(collarR, Color.Black.copy(alpha = 0.35f), style = Stroke(width = 1f))
    // Center zipper
    drawLine(Color.Black.copy(alpha = 0.40f), Offset(cx, h * 0.392f), Offset(cx, h * 0.86f), strokeWidth = 1.6f)
    // Shoulder seams
    drawLine(Color.Black.copy(alpha = 0.22f), Offset(cx - w * 0.14f, h * 0.352f), Offset(cx - w * 0.254f, h * 0.380f), strokeWidth = 1.2f)
    drawLine(Color.Black.copy(alpha = 0.22f), Offset(cx + w * 0.14f, h * 0.352f), Offset(cx + w * 0.254f, h * 0.380f), strokeWidth = 1.2f)
}

/** Slanted sleeve pocket flaps below the velcro boxes. */
internal fun DrawScope.drawFieldSleevePockets(
    sleeveFaceX: (Float, Float, Float) -> Float,
    topFrac: Float,
    h: Float,
    secondary: Color
) {
    for (side in listOf(-1f, 1f)) {
        val top = topFrac
        val bot = topFrac + 0.050f
        val p = Path().apply {
            moveTo(sleeveFaceX(side, top, 0.12f), h * top)
            lineTo(sleeveFaceX(side, top, 0.95f), h * (top - 0.006f))
            lineTo(sleeveFaceX(side, bot, 0.95f), h * bot)
            lineTo(sleeveFaceX(side, bot, 0.12f), h * (bot + 0.004f))
            close()
        }
        drawPath(p, secondary.copy(alpha = 0.55f))
        drawPath(p, Color.Black.copy(alpha = 0.30f), style = Stroke(width = 1f))
        drawLine(
            Color.Black.copy(alpha = 0.30f),
            Offset(sleeveFaceX(side, top + 0.014f, 0.12f), h * (top + 0.014f)),
            Offset(sleeveFaceX(side, top + 0.014f, 0.95f), h * (top + 0.008f)),
            strokeWidth = 1f
        )
    }
}

/** Trousers, rigger's belt with cobra buckle, and a pouch for the soldier's utility item. */
internal fun DrawScope.drawFieldBeltAndTrousers(
    w: Float,
    h: Float,
    cx: Float,
    primary: Color,
    secondary: Color,
    utilityId: String?
) {
    val beltTop = h * 0.858f
    val beltH = h * 0.026f
    // Trousers below the belt
    drawRect(primary.copy(red = primary.red * 0.92f, green = primary.green * 0.92f, blue = primary.blue * 0.92f), Offset(cx - w * 0.26f, beltTop + beltH), Size(w * 0.52f, h * 0.96f - beltTop - beltH))
    drawLine(Color.Black.copy(alpha = 0.35f), Offset(cx, beltTop + beltH), Offset(cx, h * 0.96f), strokeWidth = 1.4f)
    // Belt and buckle
    drawRect(webbing, Offset(cx - w * 0.262f, beltTop), Size(w * 0.524f, beltH))
    drawLine(Color.White.copy(alpha = 0.08f), Offset(cx - w * 0.262f, beltTop + 1.5f), Offset(cx + w * 0.262f, beltTop + 1.5f), strokeWidth = 1f)
    drawRoundRect(Color(0xFF5B5E57), Offset(cx - w * 0.030f, beltTop - h * 0.002f), Size(w * 0.060f, beltH + h * 0.004f), CornerRadius(2f, 2f))
    drawRoundRect(Color(0xFF2A2C28), Offset(cx - w * 0.020f, beltTop + h * 0.004f), Size(w * 0.040f, beltH - h * 0.008f), CornerRadius(1.5f, 1.5f))
    // Belt keepers
    for (dx in listOf(-0.20f, -0.11f, 0.11f, 0.20f)) {
        drawRect(Color.Black.copy(alpha = 0.35f), Offset(cx + w * dx - 1.5f, beltTop), Size(3f, beltH))
    }
    // Pouch on the belt for the utility item; an IFAK otherwise
    val pouchCol = secondary
    when (utilityId) {
        "utl_cls_aid_bag" -> {
            drawRoundRect(pouchCol, Offset(cx + w * 0.105f, beltTop - h * 0.040f), Size(w * 0.120f, h * 0.078f), CornerRadius(4f, 4f))
            drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(cx + w * 0.105f, beltTop - h * 0.040f), Size(w * 0.120f, h * 0.078f), CornerRadius(4f, 4f), style = Stroke(1f))
            val c = Offset(cx + w * 0.165f, beltTop - h * 0.002f)
            drawRect(Color(0xFFB3A27A), Offset(c.x - w * 0.006f, c.y - h * 0.016f), Size(w * 0.012f, h * 0.032f))
            drawRect(Color(0xFFB3A27A), Offset(c.x - w * 0.018f, c.y - h * 0.0055f), Size(w * 0.036f, h * 0.011f))
        }
        "utl_breacher_kit" -> {
            drawRoundRect(pouchCol, Offset(cx + w * 0.120f, beltTop - h * 0.030f), Size(w * 0.110f, h * 0.090f), CornerRadius(3f, 3f))
            drawRoundRect(webbing, Offset(cx + w * 0.128f, beltTop - h * 0.024f), Size(w * 0.094f, h * 0.020f), CornerRadius(2f, 2f))
            drawLine(Color(0xFFB3A27A), Offset(cx + w * 0.135f, beltTop + h * 0.030f), Offset(cx + w * 0.215f, beltTop + h * 0.030f), strokeWidth = 1.4f)
        }
        else -> {
            // IFAK with its red pull tab
            drawRoundRect(pouchCol, Offset(cx + w * 0.135f, beltTop - h * 0.018f), Size(w * 0.072f, h * 0.058f), CornerRadius(3f, 3f))
            drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(cx + w * 0.135f, beltTop - h * 0.018f), Size(w * 0.072f, h * 0.058f), CornerRadius(3f, 3f), style = Stroke(1f))
            drawRect(Color(0xFF8E2A26), Offset(cx + w * 0.166f, beltTop + h * 0.040f), Size(w * 0.012f, h * 0.012f))
        }
    }
    // Dump pouch on the other hip
    drawRoundRect(pouchCol.copy(alpha = 0.9f), Offset(cx - w * 0.205f, beltTop - h * 0.010f), Size(w * 0.075f, h * 0.050f), CornerRadius(3f, 3f))
    drawLine(Color.Black.copy(alpha = 0.3f), Offset(cx - w * 0.205f, beltTop + h * 0.006f), Offset(cx - w * 0.130f, beltTop + h * 0.006f), strokeWidth = 1f)
}

private fun DrawScope.drawMolleRows(left: Float, top: Float, width: Float, rows: Int, stepY: Float, color: Color) {
    repeat(rows) { r ->
        val y = top + r * stepY
        drawLine(color, Offset(left, y), Offset(left + width, y), strokeWidth = 1.6f)
        var x = left + width * 0.08f
        while (x < left + width) {
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(x, y - 1.5f), Offset(x, y + 1.5f), strokeWidth = 1f)
            x += width * 0.12f
        }
    }
}

private fun DrawScope.drawMagPouches(left: Float, top: Float, count: Int, pouchW: Float, pouchH: Float, gap: Float, color: Color) {
    repeat(count) { i ->
        val x = left + i * (pouchW + gap)
        drawRoundRect(color, Offset(x, top), Size(pouchW, pouchH), CornerRadius(3f, 3f))
        drawRoundRect(Color.Black.copy(alpha = 0.32f), Offset(x, top), Size(pouchW, pouchH), CornerRadius(3f, 3f), style = Stroke(1f))
        // Bungee retention and the top of the magazine
        drawLine(Color.Black.copy(alpha = 0.45f), Offset(x + pouchW * 0.2f, top + pouchH * 0.22f), Offset(x + pouchW * 0.8f, top + pouchH * 0.22f), strokeWidth = 1.4f, cap = StrokeCap.Round)
        drawRoundRect(Color(0xFF26282A), Offset(x + pouchW * 0.25f, top - pouchH * 0.10f), Size(pouchW * 0.5f, pouchH * 0.16f), CornerRadius(1.5f, 1.5f))
    }
}

/**
 * The soldier's armor item: chest rig, light plate carrier or heavy carrier, plus a drone pouch
 * when they carry the micro-UAV. Name tapes, rank and badges draw on top of this.
 */
internal fun DrawScope.drawFieldLoadBearing(
    w: Float,
    h: Float,
    cx: Float,
    style: FieldLoadBearingStyle,
    utilityId: String?,
    secondary: Color,
    accent: Color
) {
    val gear = secondary
    val strap = Color(0xFF23261F)
    when (style) {
        FieldLoadBearingStyle.NONE -> Unit
        FieldLoadBearingStyle.CHEST_RIG -> {
            // H-harness straps over the shoulders down to a low chest rig
            drawLine(strap, Offset(cx - w * 0.12f, h * 0.36f), Offset(cx - w * 0.15f, h * 0.60f), strokeWidth = w * 0.022f)
            drawLine(strap, Offset(cx + w * 0.12f, h * 0.36f), Offset(cx + w * 0.15f, h * 0.60f), strokeWidth = w * 0.022f)
            drawLine(strap, Offset(cx - w * 0.15f, h * 0.48f), Offset(cx + w * 0.15f, h * 0.48f), strokeWidth = w * 0.010f)
            drawRoundRect(gear, Offset(cx - w * 0.19f, h * 0.585f), Size(w * 0.38f, h * 0.130f), CornerRadius(6f, 6f))
            drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(cx - w * 0.19f, h * 0.585f), Size(w * 0.38f, h * 0.130f), CornerRadius(6f, 6f), style = Stroke(1.2f))
            drawMagPouches(cx - w * 0.170f, h * 0.612f, 4, w * 0.074f, h * 0.088f, w * 0.012f, gear.copy(alpha = 1f))
            drawLine(strap, Offset(cx - w * 0.19f, h * 0.705f), Offset(cx - w * 0.26f, h * 0.72f), strokeWidth = w * 0.012f)
            drawLine(strap, Offset(cx + w * 0.19f, h * 0.705f), Offset(cx + w * 0.26f, h * 0.72f), strokeWidth = w * 0.012f)
        }
        FieldLoadBearingStyle.LIGHT_CARRIER, FieldLoadBearingStyle.HEAVY_CARRIER -> {
            val heavy = style == FieldLoadBearingStyle.HEAVY_CARRIER
            val halfW = if (heavy) 0.185f else 0.160f
            val top = if (heavy) 0.405f else 0.420f
            val bottom = if (heavy) 0.790f else 0.750f
            // Shoulder straps and the cummerbund wrapping to the sides
            drawRoundRect(strap, Offset(cx - w * (halfW - 0.010f), h * 0.355f), Size(w * 0.060f, h * (top - 0.355f + 0.02f)), CornerRadius(4f, 4f))
            drawRoundRect(strap, Offset(cx + w * (halfW - 0.070f), h * 0.355f), Size(w * 0.060f, h * (top - 0.355f + 0.02f)), CornerRadius(4f, 4f))
            drawRect(gear.copy(alpha = 0.92f), Offset(cx - w * 0.262f, h * (bottom - 0.150f)), Size(w * 0.524f, h * 0.110f))
            drawMolleRows(cx - w * 0.255f, h * (bottom - 0.135f), w * 0.51f, 3, h * 0.032f, strap)
            // Front plate bag
            drawRoundRect(gear, Offset(cx - w * halfW, h * top), Size(w * halfW * 2f, h * (bottom - top)), CornerRadius(10f, 10f))
            drawRoundRect(Color.Black.copy(alpha = 0.32f), Offset(cx - w * halfW, h * top), Size(w * halfW * 2f, h * (bottom - top)), CornerRadius(10f, 10f), style = Stroke(1.4f))
            // Velcro field for tapes and rank, then MOLLE below
            drawRoundRect(accent.copy(alpha = 0.25f), Offset(cx - w * (halfW - 0.020f), h * (top + 0.012f)), Size(w * (halfW - 0.020f) * 2f, h * 0.130f), CornerRadius(4f, 4f))
            drawMolleRows(cx - w * (halfW - 0.015f), h * (top + 0.165f), w * (halfW - 0.015f) * 2f, if (heavy) 3 else 2, h * 0.030f, strap)
            // Triple magazine shingle on the front flap
            val pouchW = w * (if (heavy) 0.098f else 0.090f)
            val gap = w * 0.010f
            val rowLeft = cx - (pouchW * 3 + gap * 2) / 2f
            drawMagPouches(rowLeft, h * (bottom - 0.115f), 3, pouchW, h * 0.095f, gap, gear)
            if (heavy) {
                // Collar protector and groin flap
                drawRoundRect(gear, Offset(cx - w * 0.115f, h * 0.352f), Size(w * 0.230f, h * 0.050f), CornerRadius(8f, 8f))
                drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(cx - w * 0.115f, h * 0.352f), Size(w * 0.230f, h * 0.050f), CornerRadius(8f, 8f), style = Stroke(1f))
                drawRoundRect(gear, Offset(cx - w * 0.080f, h * bottom - 2f), Size(w * 0.160f, h * 0.060f), CornerRadius(6f, 6f))
                drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(cx - w * 0.080f, h * bottom - 2f), Size(w * 0.160f, h * 0.060f), CornerRadius(6f, 6f), style = Stroke(1f))
            }
        }
    }
    // Micro-UAV pouch high on the left side with its antenna
    if (utilityId == "utl_micro_uav") {
        val px = cx - w * 0.235f
        val py = h * 0.60f
        drawRoundRect(gear, Offset(px, py), Size(w * 0.060f, h * 0.070f), CornerRadius(3f, 3f))
        drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(px, py), Size(w * 0.060f, h * 0.070f), CornerRadius(3f, 3f), style = Stroke(1f))
        drawLine(Color(0xFF15171A), Offset(px + w * 0.045f, py), Offset(px + w * 0.060f, py - h * 0.060f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    }
}

/** Ear cups and boom mic for the comms headset (worn with or without a helmet shell). */
internal fun DrawScope.drawFieldHeadset(w: Float, h: Float, cx: Float) {
    val cup = Color(0xFF1D2026)
    drawRoundRect(cup, Offset(cx - w * 0.118f, h * 0.200f), Size(w * 0.036f, h * 0.070f), CornerRadius(8f, 8f))
    drawRoundRect(cup, Offset(cx + w * 0.082f, h * 0.200f), Size(w * 0.036f, h * 0.070f), CornerRadius(8f, 8f))
    drawArc(cup, 180f, 180f, false, Offset(cx - w * 0.102f, h * 0.130f), Size(w * 0.204f, h * 0.140f), style = Stroke(width = 3f))
    drawLine(cup, Offset(cx - w * 0.090f, h * 0.262f), Offset(cx - w * 0.030f, h * 0.292f), strokeWidth = 2.4f, cap = StrokeCap.Round)
}
