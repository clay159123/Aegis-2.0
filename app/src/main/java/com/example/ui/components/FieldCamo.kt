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
