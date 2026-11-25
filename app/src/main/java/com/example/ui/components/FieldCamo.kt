package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import com.example.domain.model.CamoPattern
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Art 4a: real camouflage shapes per pattern (in place of the old round blotches), field headgear that follows
 * the mission (patrol cap at home, helmet with goggles pushed up or a boonie on ops), and flat one-side shading.
 * Patterns are drawn from a fixed seed so a uniform never shimmers between frames.
 */
internal object FieldCamo {
    private class Rng(seed: Int) {
        private var s = seed.toLong() * 6364136223846793005L + 1442695040888963407L
        fun f(): Float { s = s * 6364136223846793005L + 1442695040888963407L; return ((s ushr 40).toInt() and 0xFFFFFF) / 16777216f }
        fun r(a: Float, b: Float) = a + (b - a) * f()
    }

    /** A soft organic blob: jittered points around a center joined by curves through their midpoints. */
    private fun blob(c: Offset, rx: Float, ry: Float, rng: Rng, points: Int = 8, jitter: Float = 0.38f): Path {
        val pts = (0 until points).map { i ->
            val a = 2 * PI * i / points + rng.r(-0.25f, 0.25f)
            val k = 1f - jitter + rng.f() * jitter * 1.4f
            Offset(c.x + (cos(a) * rx * k).toFloat(), c.y + (sin(a) * ry * k).toFloat())
        }
        fun mid(a: Offset, b: Offset) = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
        return Path().apply {
            val m0 = mid(pts[0], pts[1])
            moveTo(m0.x, m0.y)
            for (i in 1..points) {
                val p = pts[i % points]; val m = mid(p, pts[(i + 1) % points])
                quadraticTo(p.x, p.y, m.x, m.y)
            }
            close()
        }
    }

    /** A hard-edged polygon (no smoothing), for broken snow-and-rock patches and night shards. */
    private fun shard(c: Offset, r: Float, rng: Rng, points: Int): Path = Path().apply {
        val start = rng.r(0f, 6.28f)
        for (i in 0 until points) {
            val a = start + 2 * PI * i / points + rng.r(-0.35f, 0.35f)
            val k = rng.r(0.45f, 1.1f)
            val x = c.x + (cos(a) * r * k).toFloat(); val y = c.y + (sin(a) * r * k * 0.8f).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    /** A wavy band across [b] at height [y] (top edge) with thickness [t]. */
    private fun band(b: Rect, y: Float, t: Float, amp: Float, wave: Float, phase: Float, tilt: Float, x0: Float = b.left, x1: Float = b.right): Path = Path().apply {
        val steps = 24
        for (i in 0..steps) {
            val x = x0 + (x1 - x0) * i / steps
            val yy = y + (sin((x / wave) + phase) * amp).toFloat() + (x - b.left) * tilt
            if (i == 0) moveTo(x, yy) else lineTo(x, yy)
        }
        for (i in steps downTo 0) {
            val x = x0 + (x1 - x0) * i / steps
            val thick = t * (0.7f + 0.3f * sin(x / (wave * 0.7f) + phase * 1.7f).toFloat())
            val yy = y + (sin((x / wave) + phase + 0.6f) * amp).toFloat() + (x - b.left) * tilt + thick
            lineTo(x, yy)
        }
        close()
    }

    /**
     * Fills [clip] with [camo]'s pattern over [b]. [scale] is the pattern's feature size in pixels (about 4% of the
     * figure width on the uniform, smaller on a cap).
     */
    fun DrawScope.drawCamoPattern(camo: CamoPattern, clip: Path, b: Rect, scale: Float, primary: Color, secondary: Color, accent: Color, seed: Int = 11) {
        val rng = Rng(seed + camo.ordinal * 97)
        clipPath(clip) {
            when (camo) {
                CamoPattern.WOODLAND_OLIVE -> {
                    // Scorpion OCP: layered soft blotches, taller than wide, with small dark flecks and light twigs
                    repeat(22) {
                        val c = Offset(rng.r(b.left, b.right), rng.r(b.top, b.bottom))
                        drawPath(blob(c, scale * rng.r(0.9f, 1.7f), scale * rng.r(1.3f, 2.4f), rng, points = 9, jitter = 0.6f), accent.copy(alpha = 0.62f))
                    }
                    repeat(26) {
                        val c = Offset(rng.r(b.left, b.right), rng.r(b.top, b.bottom))
                        drawPath(blob(c, scale * rng.r(0.6f, 1.2f), scale * rng.r(0.9f, 1.8f), rng, points = 8, jitter = 0.6f), secondary.copy(alpha = 0.72f))
                    }
                    repeat(40) {
                        val c = Offset(rng.r(b.left, b.right), rng.r(b.top, b.bottom))
                        drawPath(blob(c, scale * rng.r(0.16f, 0.32f), scale * rng.r(0.22f, 0.45f), rng, points = 6), Color(0xFF3A3324).copy(alpha = 0.7f))
                    }
                    repeat(16) {
                        val x = rng.r(b.left, b.right); val y = rng.r(b.top, b.bottom)
                        drawLine(Color(0xFFB9B48C).copy(alpha = 0.45f), Offset(x, y), Offset(x + scale * rng.r(-0.5f, 0.5f), y + scale * rng.r(0.6f, 1.2f)), strokeWidth = scale * 0.12f, cap = StrokeCap.Round)
                    }
                }
                CamoPattern.ARID_SAND -> {
                    // Arid Canyon: horizontal strata like canyon rock, broken into runs of different lengths
                    var y = b.top - scale
                    var k = 0
                    while (y < b.bottom) {
                        val x0 = rng.r(b.left - scale * 4, b.left + (b.width) * 0.4f)
                        val x1 = rng.r(x0 + b.width * 0.35f, b.right + scale * 4)
                        val col = if (k % 3 == 0) accent.copy(alpha = 0.6f) else secondary.copy(alpha = 0.55f)
                        drawPath(band(b, y, scale * rng.r(0.35f, 0.8f), scale * 0.35f, scale * 2.2f, rng.r(0f, 6f), rng.r(-0.03f, 0.03f), x0, x1), col)
                        y += scale * rng.r(0.7f, 1.3f); k++
                    }
                    repeat(30) {
                        val x = rng.r(b.left, b.right); val yy = rng.r(b.top, b.bottom)
                        drawLine(Color(0xFF5A4A33).copy(alpha = 0.4f), Offset(x, yy), Offset(x + scale * rng.r(0.6f, 1.6f), yy + scale * rng.r(-0.1f, 0.1f)), strokeWidth = scale * 0.12f, cap = StrokeCap.Round)
                    }
                }
                CamoPattern.ALPINE_FROST -> {
                    // Alpine Frost: broken gray patches like rock showing through snow
                    repeat(24) {
                        val c = Offset(rng.r(b.left, b.right), rng.r(b.top, b.bottom))
                        drawPath(shard(c, scale * rng.r(0.8f, 1.8f), rng, (4..6).random(kotlin.random.Random(it + seed))), secondary.copy(alpha = 0.55f))
                    }
                    repeat(18) {
                        val c = Offset(rng.r(b.left, b.right), rng.r(b.top, b.bottom))
                        drawPath(shard(c, scale * rng.r(0.4f, 0.9f), rng, 5), accent.copy(alpha = 0.7f))
                    }
                    repeat(24) {
                        val c = Offset(rng.r(b.left, b.right), rng.r(b.top, b.bottom))
                        drawPath(shard(c, scale * rng.r(0.12f, 0.3f), rng, 4), Color(0xFF6B737C).copy(alpha = 0.55f))
                    }
                }
                CamoPattern.LITTORAL_TIDE -> {
                    // Littoral Tide: wavy bands running slightly downhill, like surf lines
                    var y = b.top - scale * 3
                    var k = 0
                    while (y < b.bottom + scale) {
                        val col = if (k % 2 == 0) secondary.copy(alpha = 0.6f) else accent.copy(alpha = 0.5f)
                        drawPath(band(b, y, scale * rng.r(0.5f, 1.0f), scale * 0.7f, scale * 1.6f, rng.r(0f, 6f), 0.12f), col)
                        y += scale * rng.r(1.1f, 1.7f); k++
                    }
                    repeat(20) {
                        val c = Offset(rng.r(b.left, b.right), rng.r(b.top, b.bottom))
                        drawPath(blob(c, scale * rng.r(0.15f, 0.3f), scale * rng.r(0.1f, 0.2f), rng, 6), Color(0xFF1B2329).copy(alpha = 0.5f))
                    }
                }
                CamoPattern.NIGHT_CHARCOAL -> {
                    // Night Ops Slate: fine angular shards in two tones
                    repeat(70) {
                        val c = Offset(rng.r(b.left, b.right), rng.r(b.top, b.bottom))
                        drawPath(shard(c, scale * rng.r(0.25f, 0.75f), rng, 3), (if (it % 3 == 0) accent else secondary).copy(alpha = 0.7f))
                    }
                }
                CamoPattern.RANGER_GREEN -> {
                    // Ranger Olive: near-solid with a faint ripstop weave and soft fading
                    var x = b.left - b.height
                    while (x < b.right) {
                        drawLine(Color.Black.copy(alpha = 0.06f), Offset(x, b.top), Offset(x + b.height, b.bottom), strokeWidth = 0.8f)
                        drawLine(Color.White.copy(alpha = 0.035f), Offset(x + b.height, b.top), Offset(x, b.bottom), strokeWidth = 0.8f)
                        x += scale * 0.45f
                    }
                    repeat(8) {
                        val c = Offset(rng.r(b.left, b.right), rng.r(b.top, b.bottom))
                        drawPath(blob(c, scale * rng.r(1.4f, 2.6f), scale * rng.r(1.2f, 2.2f), rng), secondary.copy(alpha = 0.16f))
                    }
                }
                CamoPattern.FLIGHT_SAGE, CamoPattern.FLIGHT_BLACK -> {
                    // Flight suits are a single color; only a faint fabric weave
                    var x = b.left - b.height
                    while (x < b.right) {
                        drawLine(Color.Black.copy(alpha = 0.04f), Offset(x, b.top), Offset(x + b.height, b.bottom), strokeWidth = 0.7f)
                        x += scale * 0.5f
                    }
                }
            }
        }
    }

    /** Flat one-side shading over [clip]: light from the upper left, the right side in shadow. */
    fun DrawScope.shadeFigure(clip: Path, cx: Float, w: Float) {
        clipPath(clip) {
            drawRect(
                Brush.horizontalGradient(
                    0f to Color.White.copy(alpha = 0.07f), 0.45f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.26f),
                    startX = cx - w * 0.42f, endX = cx + w * 0.42f
                )
            )
        }
    }

    // ---------------- Headgear ----------------

    /** What the soldier wears on his head in the field kit. */
    enum class FieldHeadgear { PATROL_CAP, BOONIE, HELMET, HEADSET_CAP }

    fun DrawScope.patrolCap(w: Float, h: Float, cx: Float, camo: CamoPattern, primary: Color, secondary: Color, accent: Color) {
        val crown = Path().apply {
            moveTo(cx - w * 0.097f, h * 0.192f)
            lineTo(cx - w * 0.103f, h * 0.121f)
            quadraticTo(cx, h * 0.113f, cx + w * 0.103f, h * 0.121f)
            lineTo(cx + w * 0.097f, h * 0.192f)
            close()
        }
        drawPath(crown, primary)
        drawCamoPattern(camo, crown, Rect(cx - w * 0.11f, h * 0.11f, cx + w * 0.11f, h * 0.20f), w * 0.022f, primary, secondary, accent, seed = 5)
        shadeFigure(crown, cx, w * 0.3f)
        // Flat top edge and the crown seam
        drawLine(Color.Black.copy(alpha = 0.3f), Offset(cx - w * 0.103f, h * 0.121f), Offset(cx + w * 0.103f, h * 0.121f), strokeWidth = 1.2f)
        drawLine(Color.Black.copy(alpha = 0.18f), Offset(cx, h * 0.118f), Offset(cx, h * 0.19f), strokeWidth = 0.8f)
        // Curved brim
        val brim = Path().apply {
            moveTo(cx - w * 0.092f, h * 0.188f)
            quadraticTo(cx, h * 0.180f, cx + w * 0.092f, h * 0.188f)
            quadraticTo(cx, h * 0.214f, cx - w * 0.092f, h * 0.188f)
            close()
        }
        drawPath(brim, lerpDark(primary, 0.35f))
        drawPath(brim, Color.Black.copy(alpha = 0.3f), style = Stroke(0.8f))
    }

    fun DrawScope.boonie(w: Float, h: Float, cx: Float, camo: CamoPattern, primary: Color, secondary: Color, accent: Color) {
        val crown = Path().apply {
            moveTo(cx - w * 0.094f, h * 0.188f)
            cubicTo(cx - w * 0.098f, h * 0.112f, cx + w * 0.098f, h * 0.112f, cx + w * 0.094f, h * 0.188f)
            close()
        }
        val brim = Path().apply { addOval(Rect(cx - w * 0.150f, h * 0.172f, cx + w * 0.150f, h * 0.206f)) }
        drawPath(brim, lerpDark(primary, 0.2f))
        drawCamoPattern(camo, brim, Rect(cx - w * 0.16f, h * 0.17f, cx + w * 0.16f, h * 0.21f), w * 0.02f, primary, secondary, accent, seed = 9)
        drawPath(crown, primary)
        drawCamoPattern(camo, crown, Rect(cx - w * 0.11f, h * 0.11f, cx + w * 0.11f, h * 0.19f), w * 0.02f, primary, secondary, accent, seed = 7)
        shadeFigure(crown, cx, w * 0.3f)
        drawRect(lerpDark(primary, 0.4f), Offset(cx - w * 0.094f, h * 0.176f), Size(w * 0.188f, h * 0.010f))
        drawOval(Color.Black.copy(alpha = 0.25f), Offset(cx - w * 0.150f, h * 0.172f), Size(w * 0.300f, h * 0.034f), style = Stroke(0.8f))
    }

    /**
     * Combat helmet with a camo cover, side rails, comms cups, the NVG mount, and goggles pushed up onto the front.
     * With NVGs equipped the tubes sit flipped up on the mount.
     */
    fun DrawScope.combatHelmet(w: Float, h: Float, cx: Float, camo: CamoPattern, primary: Color, secondary: Color, accent: Color, hasNvg: Boolean) {
        // Comms cups over the ears
        drawRoundRect(Color(0xFF1D222B), Offset(cx - w * 0.123f, h * 0.196f), Size(w * 0.036f, h * 0.064f), CornerRadius(6f, 6f))
        drawRoundRect(Color(0xFF1D222B), Offset(cx + w * 0.087f, h * 0.196f), Size(w * 0.036f, h * 0.064f), CornerRadius(6f, 6f))
        val shell = Path().apply {
            moveTo(cx - w * 0.118f, h * 0.208f)
            cubicTo(cx - w * 0.124f, h * 0.098f, cx + w * 0.124f, h * 0.098f, cx + w * 0.118f, h * 0.208f)
            // High-cut ears: the rim steps up over the cups
            lineTo(cx + w * 0.090f, h * 0.196f)
            lineTo(cx + w * 0.084f, h * 0.206f)
            quadraticTo(cx, h * 0.200f, cx - w * 0.084f, h * 0.206f)
            lineTo(cx - w * 0.090f, h * 0.196f)
            close()
        }
        drawPath(shell, primary)
        drawCamoPattern(camo, shell, Rect(cx - w * 0.13f, h * 0.09f, cx + w * 0.13f, h * 0.21f), w * 0.02f, primary, secondary, accent, seed = 3)
        shadeFigure(shell, cx, w * 0.32f)
        drawPath(shell, Color.Black.copy(alpha = 0.35f), style = Stroke(1f))
        // Side rails
        for (s in listOf(-1f, 1f)) drawRoundRect(Color(0xFF2A2E33), Offset(cx + s * w * 0.104f - w * 0.008f, h * 0.150f), Size(w * 0.016f, h * 0.040f), CornerRadius(2f, 2f))
        // Goggles pushed up: strap around the shell and the lens frame on the front
        drawLine(Color(0xFF2A2E33), Offset(cx - w * 0.116f, h * 0.170f), Offset(cx + w * 0.116f, h * 0.170f), strokeWidth = h * 0.010f)
        drawRoundRect(Color(0xFF2A2E33), Offset(cx - w * 0.072f, h * 0.152f), Size(w * 0.144f, h * 0.034f), CornerRadius(8f, 8f))
        drawRoundRect(Color(0xFF5C4A2A), Offset(cx - w * 0.064f, h * 0.157f), Size(w * 0.128f, h * 0.024f), CornerRadius(6f, 6f))
        drawLine(Color.White.copy(alpha = 0.35f), Offset(cx - w * 0.050f, h * 0.161f), Offset(cx - w * 0.020f, h * 0.161f), strokeWidth = 1.2f, cap = StrokeCap.Round)
        // NVG shroud on the front of the shell
        drawRoundRect(Color(0xFF1D222B), Offset(cx - w * 0.020f, h * 0.124f), Size(w * 0.040f, h * 0.026f), CornerRadius(3f, 3f))
        if (hasNvg) {
            // Tubes flipped up above the shroud
            drawRect(Color(0xFF4A5058), Offset(cx - w * 0.008f, h * 0.112f), Size(w * 0.016f, h * 0.014f))
            drawRoundRect(Color(0xFF4A5058), Offset(cx - w * 0.050f, h * 0.094f), Size(w * 0.100f, h * 0.020f), CornerRadius(4f, 4f))
            for (s in listOf(-1f, 1f)) {
                drawRoundRect(Color(0xFF5C636C), Offset(cx + s * w * 0.030f - w * 0.019f, h * 0.088f), Size(w * 0.038f, h * 0.030f), CornerRadius(5f, 5f))
                drawCircle(Color(0xFF2A2E33), w * 0.013f, Offset(cx + s * w * 0.030f, h * 0.103f))
                drawCircle(Color(0xFF5AC8FA).copy(alpha = 0.7f), w * 0.007f, Offset(cx + s * w * 0.030f, h * 0.101f))
            }
        }
    }

    private fun lerpDark(c: Color, f: Float) = Color(c.red * (1 - f), c.green * (1 - f), c.blue * (1 - f), c.alpha)
}

/**
 * Art 4b: field gear. Carrier colors by camo, forearms and gloves on the weapon, the flight suit's pockets and
 * survival vest, the rotary-wing aircrew helmet, and the SF ball cap and shemagh.
 */
internal object FieldGearArt {
    /** Carrier and pouch color that goes with each camo (coyote, ranger green, wolf gray, black). */
    fun gearColor(camo: CamoPattern): Color = when (camo) {
        CamoPattern.WOODLAND_OLIVE -> Color(0xFF7E6B4C)
        CamoPattern.RANGER_GREEN -> Color(0xFF3F4734)
        CamoPattern.ARID_SAND -> Color(0xFF8E7A58)
        CamoPattern.ALPINE_FROST -> Color(0xFFB5BCC0)
        CamoPattern.LITTORAL_TIDE -> Color(0xFF4F5B63)
        CamoPattern.NIGHT_CHARCOAL, CamoPattern.FLIGHT_BLACK -> Color(0xFF202328)
        CamoPattern.FLIGHT_SAGE -> Color(0xFF4F5640)
    }

    fun gloveColor(camo: CamoPattern): Color = when (camo) {
        CamoPattern.NIGHT_CHARCOAL, CamoPattern.FLIGHT_BLACK -> Color(0xFF1A1C20)
        CamoPattern.FLIGHT_SAGE -> Color(0xFFB09A72)
        CamoPattern.ALPINE_FROST -> Color(0xFF6B7178)
        else -> Color(0xFF6E5E44)
    }

    /** A gloved hand: palm, wrapped fingers and a knuckle seam. */
    fun DrawScope.glove(c: Offset, r: Float, col: Color) {
        drawRoundRect(col, Offset(c.x - r, c.y - r * 0.8f), Size(r * 2f, r * 1.6f), CornerRadius(r * 0.7f, r * 0.7f))
        for (i in 0..2) drawLine(Color.Black.copy(alpha = 0.3f), Offset(c.x - r * 0.55f + i * r * 0.55f, c.y - r * 0.7f), Offset(c.x - r * 0.55f + i * r * 0.55f, c.y + r * 0.1f), strokeWidth = r * 0.12f)
        drawLine(Color.White.copy(alpha = 0.15f), Offset(c.x - r * 0.7f, c.y - r * 0.55f), Offset(c.x + r * 0.5f, c.y - r * 0.55f), strokeWidth = r * 0.14f, cap = StrokeCap.Round)
    }

    /** A forearm in the uniform's sleeve from the elbow to the wrist, with a cuff. */
    fun DrawScope.forearm(elbow: Offset, wrist: Offset, width: Float, sleeve: Color) {
        drawLine(Color.Black.copy(alpha = 0.35f), elbow, wrist, strokeWidth = width * 1.12f, cap = StrokeCap.Round)
        drawLine(sleeve, elbow, wrist, strokeWidth = width, cap = StrokeCap.Round)
        val dx = wrist.x - elbow.x; val dy = wrist.y - elbow.y
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val cuff = Offset(wrist.x - dx / len * width * 0.45f, wrist.y - dy / len * width * 0.45f)
        drawLine(Color.Black.copy(alpha = 0.25f), cuff, wrist, strokeWidth = width * 1.02f)
    }

    /** Admin pouch, radio with antenna and a tourniquet on the carrier (drawn after the carrier itself). */
    fun DrawScope.carrierDetails(w: Float, h: Float, cx: Float, gear: Color, top: Float) {
        val strap = Color(0xFF23261F)
        // Shoulder-strap buckles
        for (s in listOf(-1f, 1f)) drawRoundRect(Color(0xFF15171A), Offset(cx + s * w * 0.135f - w * 0.018f, h * 0.392f), Size(w * 0.036f, h * 0.012f), CornerRadius(2f, 2f))
        // Radio on the wearer's left shoulder with its whip antenna
        val rx = cx + w * 0.175f
        drawRoundRect(Color(0xFF2A2E33), Offset(rx, h * (top + 0.005f)), Size(w * 0.050f, h * 0.070f), CornerRadius(3f, 3f))
        drawRoundRect(gear, Offset(rx - w * 0.004f, h * (top + 0.040f)), Size(w * 0.058f, h * 0.050f), CornerRadius(3f, 3f))
        drawLine(Color(0xFF15171A), Offset(rx + w * 0.036f, h * (top + 0.005f)), Offset(rx + w * 0.060f, h * (top - 0.13f)), strokeWidth = 1.4f, cap = StrokeCap.Round)
        // Tourniquet in a band on the wearer's right shoulder strap
        val tx = cx - w * 0.150f
        drawRoundRect(Color(0xFF1E1F1C), Offset(tx - w * 0.018f, h * (top - 0.040f)), Size(w * 0.036f, h * 0.050f), CornerRadius(3f, 3f))
        drawRect(Color(0xFFB8322B), Offset(tx - w * 0.010f, h * (top - 0.034f)), Size(w * 0.020f, h * 0.008f))
        // Bungee on the plate bag edge
        drawLine(strap.copy(alpha = 0.7f), Offset(cx - w * 0.12f, h * (top + 0.004f)), Offset(cx + w * 0.12f, h * (top + 0.004f)), strokeWidth = 1.2f)
    }

    /** Flight suit: zippered chest and sleeve pockets (name tag and wings draw over the wearer's left pocket). */
    fun DrawScope.flightSuitPockets(w: Float, h: Float, cx: Float, suit: Color) {
        val zip = Color(0xFF2A2C26)
        for (s in listOf(-1f, 1f)) {
            val l = cx + s * w * 0.04f; val r = cx + s * w * 0.20f
            val p = Path().apply { moveTo(l, h * 0.440f); lineTo(r, h * 0.440f); lineTo(r, h * 0.560f); lineTo(l, h * 0.560f); close() }
            drawPath(p, Color.Black.copy(alpha = 0.10f))
            drawPath(p, Color.Black.copy(alpha = 0.30f), style = Stroke(0.9f))
            // Slanted zipper across the pocket
            drawLine(zip, Offset(l + s * w * 0.010f, h * 0.452f), Offset(r - s * w * 0.010f, h * 0.470f), strokeWidth = 1.3f)
            drawRect(Color(0xFFB9BBB0), Offset(r - s * w * 0.016f - w * 0.004f, h * 0.466f), Size(w * 0.008f, h * 0.010f))
        }
        // Thigh-height leg pocket zips showing above the belt line are hidden; add sleeve zip pockets
        for (s in listOf(-1f, 1f)) drawLine(zip, Offset(cx + s * w * 0.29f, h * 0.62f), Offset(cx + s * w * 0.33f, h * 0.70f), strokeWidth = 1.2f)
    }

    /** Aircrew survival vest over the flight suit: mesh panels with pouches, a chest holster and a radio. */
    fun DrawScope.survivalVest(w: Float, h: Float, cx: Float, vest: Color) {
        val strap = Color(0xFF23261F)
        for (s in listOf(-1f, 1f)) {
            val panel = Path().apply {
                moveTo(cx + s * w * 0.03f, h * 0.40f)
                lineTo(cx + s * w * 0.12f, h * 0.355f)
                lineTo(cx + s * w * 0.235f, h * 0.40f)
                lineTo(cx + s * w * 0.240f, h * 0.80f)
                lineTo(cx + s * w * 0.03f, h * 0.80f)
                close()
            }
            drawPath(panel, vest)
            // Mesh
            clipPath(panel) {
                var y = h * 0.36f
                while (y < h * 0.80f) { drawLine(Color.Black.copy(alpha = 0.16f), Offset(cx - w * 0.3f, y), Offset(cx + w * 0.3f, y), strokeWidth = 0.6f); y += h * 0.010f }
                var x = cx - w * 0.3f
                while (x < cx + w * 0.3f) { drawLine(Color.Black.copy(alpha = 0.12f), Offset(x, h * 0.35f), Offset(x, h * 0.80f), strokeWidth = 0.6f); x += w * 0.012f }
            }
            drawPath(panel, Color.Black.copy(alpha = 0.35f), style = Stroke(1f))
            // Pouch row near the bottom
            for (k in 0..1) {
                val px = cx + s * w * (0.06f + k * 0.085f) - (if (s < 0) w * 0.075f else 0f)
                drawRoundRect(lerp(vest, Color.Black, 0.15f), Offset(px, h * 0.66f), Size(w * 0.075f, h * 0.085f), CornerRadius(3f, 3f))
                drawLine(Color.Black.copy(alpha = 0.35f), Offset(px, h * 0.685f), Offset(px + w * 0.075f, h * 0.685f), strokeWidth = 1f)
            }
        }
        // Waist strap
        drawRect(strap, Offset(cx - w * 0.24f, h * 0.775f), Size(w * 0.48f, h * 0.016f))
        // Chest holster with the pistol grip on the wearer's right
        val hx = cx - w * 0.19f
        drawRoundRect(Color(0xFF1E2024), Offset(hx, h * 0.56f), Size(w * 0.080f, h * 0.075f), CornerRadius(4f, 4f))
        drawRoundRect(Color(0xFF2E3238), Offset(hx + w * 0.020f, h * 0.535f), Size(w * 0.030f, h * 0.040f), CornerRadius(2f, 2f))
        // Survival radio on the wearer's left with its stub antenna
        val rx = cx + w * 0.135f
        drawRoundRect(Color(0xFF2A2E33), Offset(rx, h * 0.585f), Size(w * 0.050f, h * 0.065f), CornerRadius(3f, 3f))
        drawLine(Color(0xFF15171A), Offset(rx + w * 0.040f, h * 0.585f), Offset(rx + w * 0.046f, h * 0.545f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    }

    /**
     * Rotary-wing aircrew helmet: a full round shell over the ears, the visor housing across the brow with the
     * tinted visor raised, an NVG mount plate on the front and a boom mic.
     */
    fun DrawScope.aircrewHelmet(w: Float, h: Float, cx: Float, shellCol: Color) {
        val shell = Path().apply {
            moveTo(cx - w * 0.122f, h * 0.268f)
            lineTo(cx - w * 0.124f, h * 0.190f)
            cubicTo(cx - w * 0.128f, h * 0.090f, cx + w * 0.128f, h * 0.090f, cx + w * 0.124f, h * 0.190f)
            lineTo(cx + w * 0.122f, h * 0.268f)
            quadraticTo(cx + w * 0.100f, h * 0.278f, cx + w * 0.090f, h * 0.262f)
            lineTo(cx + w * 0.090f, h * 0.200f)
            quadraticTo(cx, h * 0.188f, cx - w * 0.090f, h * 0.200f)
            lineTo(cx - w * 0.090f, h * 0.262f)
            quadraticTo(cx - w * 0.100f, h * 0.278f, cx - w * 0.122f, h * 0.268f)
            close()
        }
        drawPath(shell, shellCol)
        with(FieldCamo) { shadeFigure(shell, cx, w * 0.32f) }
        drawPath(shell, Color.Black.copy(alpha = 0.4f), style = Stroke(1f))
        // Edge roll around the face opening
        drawLine(Color(0xFF1A1C1F), Offset(cx - w * 0.090f, h * 0.200f), Offset(cx - w * 0.090f, h * 0.262f), strokeWidth = w * 0.010f)
        drawLine(Color(0xFF1A1C1F), Offset(cx + w * 0.090f, h * 0.200f), Offset(cx + w * 0.090f, h * 0.262f), strokeWidth = w * 0.010f)
        // Visor housing with the raised visor showing under its lip
        drawRoundRect(lerp(shellCol, Color.Black, 0.18f), Offset(cx - w * 0.108f, h * 0.140f), Size(w * 0.216f, h * 0.034f), CornerRadius(10f, 10f))
        drawRoundRect(Color(0xFF3A3326).copy(alpha = 0.9f), Offset(cx - w * 0.096f, h * 0.172f), Size(w * 0.192f, h * 0.016f), CornerRadius(6f, 6f))
        drawLine(Color.White.copy(alpha = 0.3f), Offset(cx - w * 0.07f, h * 0.176f), Offset(cx - w * 0.02f, h * 0.176f), strokeWidth = 1.1f, cap = StrokeCap.Round)
        // Visor knob and the NVG mount plate
        drawCircle(Color(0xFF15171A), w * 0.010f, Offset(cx + w * 0.080f, h * 0.152f))
        drawRoundRect(Color(0xFF15171A), Offset(cx - w * 0.024f, h * 0.118f), Size(w * 0.048f, h * 0.026f), CornerRadius(3f, 3f))
        // Boom mic from the wearer's left ear cup to the mouth, and the chin strap
        drawLine(Color(0xFF15171A), Offset(cx + w * 0.105f, h * 0.250f), Offset(cx + w * 0.030f, h * 0.288f), strokeWidth = 2f, cap = StrokeCap.Round)
        drawCircle(Color(0xFF15171A), w * 0.010f, Offset(cx + w * 0.026f, h * 0.290f))
        drawArc(Color(0xFF2A2C2E), 20f, 140f, false, Offset(cx - w * 0.092f, h * 0.250f), Size(w * 0.184f, h * 0.090f), style = Stroke(1.4f))
    }

    /** SF ball cap (camo or coyote) with a small velcro patch on the front. */
    fun DrawScope.ballCap(w: Float, h: Float, cx: Float, capCol: Color) {
        val crown = Path().apply {
            moveTo(cx - w * 0.100f, h * 0.190f)
            cubicTo(cx - w * 0.104f, h * 0.104f, cx + w * 0.104f, h * 0.104f, cx + w * 0.100f, h * 0.190f)
            close()
        }
        drawPath(crown, capCol)
        with(FieldCamo) { shadeFigure(crown, cx, w * 0.3f) }
        drawLine(Color.Black.copy(alpha = 0.2f), Offset(cx, h * 0.116f), Offset(cx, h * 0.188f), strokeWidth = 0.8f)
        drawRoundRect(lerp(capCol, Color.Black, 0.25f), Offset(cx - w * 0.030f, h * 0.140f), Size(w * 0.060f, h * 0.026f), CornerRadius(2f, 2f))
        // Brim
        val brim = Path().apply {
            moveTo(cx - w * 0.094f, h * 0.186f)
            quadraticTo(cx, h * 0.176f, cx + w * 0.094f, h * 0.186f)
            quadraticTo(cx, h * 0.222f, cx - w * 0.094f, h * 0.186f)
            close()
        }
        drawPath(brim, lerp(capCol, Color.Black, 0.2f))
    }

    /** Shemagh wrapped around the neck and shoulders. */
    fun DrawScope.shemagh(w: Float, h: Float, cx: Float) {
        val cloth = Color(0xFFD9CFB4)
        val pattern = Color(0xFF6E5A3E)
        val wrap = Path().apply {
            moveTo(cx - w * 0.120f, h * 0.300f)
            quadraticTo(cx, h * 0.330f, cx + w * 0.120f, h * 0.300f)
            lineTo(cx + w * 0.150f, h * 0.370f)
            quadraticTo(cx, h * 0.420f, cx - w * 0.150f, h * 0.370f)
            close()
        }
        drawPath(wrap, cloth)
        clipPath(wrap) {
            var x = cx - w * 0.16f
            while (x < cx + w * 0.16f) {
                drawLine(pattern.copy(alpha = 0.55f), Offset(x, h * 0.29f), Offset(x + w * 0.05f, h * 0.43f), strokeWidth = 0.9f)
                drawLine(pattern.copy(alpha = 0.55f), Offset(x + w * 0.05f, h * 0.29f), Offset(x, h * 0.43f), strokeWidth = 0.9f)
                x += w * 0.022f
            }
        }
        drawPath(wrap, Color.Black.copy(alpha = 0.25f), style = Stroke(1f))
        // Folds and a hanging tail with tassels
        drawArc(Color.Black.copy(alpha = 0.2f), 20f, 140f, false, Offset(cx - w * 0.12f, h * 0.31f), Size(w * 0.24f, h * 0.06f), style = Stroke(1f))
        val tail = Path().apply { moveTo(cx + w * 0.07f, h * 0.38f); lineTo(cx + w * 0.13f, h * 0.38f); lineTo(cx + w * 0.115f, h * 0.47f); lineTo(cx + w * 0.085f, h * 0.47f); close() }
        drawPath(tail, cloth)
        drawPath(tail, Color.Black.copy(alpha = 0.25f), style = Stroke(0.8f))
        for (i in 0..3) drawLine(pattern, Offset(cx + w * (0.087f + i * 0.008f), h * 0.47f), Offset(cx + w * (0.087f + i * 0.008f), h * 0.482f), strokeWidth = 0.8f)
    }
}
