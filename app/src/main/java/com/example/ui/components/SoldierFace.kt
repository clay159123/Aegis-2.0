package com.example.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.example.domain.GameCalendar
import com.example.domain.MoraleRules
import com.example.domain.model.Operator
import com.example.domain.model.SkinTone
import kotlin.math.abs

/**
 * Art 1 (item 65): every soldier gets a face of their own, fixed from their id (and a Tailor reroll seed):
 * head and jaw shape, ears, brows, eyes, nose, mouth, hair style and color, a few marks, and age showing
 * (gray at the temples, lines, a receding line) as the years go by. Clean flat style, drawn inside the
 * same head box the uniforms already use, so caps, berets and helmets still sit right.
 */
internal object SoldierFace {
    enum class HeadShape { OVAL, ROUND, SQUARE, LONG, HEART }
    enum class Hair { BUZZ, HIGH_AND_TIGHT, CREW, SIDE_PART, BALD, FLAT_TOP }

    data class Traits(
        val head: HeadShape,
        val earScale: Float,
        val browStyle: Int,
        val eyeStyle: Int,
        val noseStyle: Int,
        val mouthStyle: Int,
        val hair: Hair,
        val hairColor: Color,
        val freckles: Boolean,
        val mole: Boolean,
        val scar: Boolean
    )

    private val DARK_HAIR = listOf(0xFF141210, 0xFF1E1813, 0xFF2A1E16)
    private val ANY_HAIR = listOf(0xFF141210, 0xFF2A1E16, 0xFF3B2A1E, 0xFF5A3E28, 0xFF7A5634, 0xFFB08A52, 0xFFD2B27A, 0xFF8E3E22)

    /** A small deterministic generator so a soldier's face never changes unless rerolled. */
    private class Seq(seed: Int) {
        private var s = seed.toLong() xor 0x5DEECE66DL
        fun next(n: Int): Int {
            s = (s * 6364136223846793005L + 1442695040888963407L)
            return (abs((s ushr 33).toInt()) % n)
        }
        fun chance(pct: Int) = next(100) < pct
    }

    fun traits(op: Operator): Traits {
        val r = Seq(op.id.hashCode() * 31 + op.faceSeed * 7919)
        val darkSkin = op.skinTone.ordinal >= SkinTone.TAN.ordinal
        val lightSkin = op.skinTone.ordinal <= SkinTone.LIGHT.ordinal
        val palette = if (darkSkin) DARK_HAIR else ANY_HAIR
        val hair = listOf(Hair.BUZZ, Hair.HIGH_AND_TIGHT, Hair.CREW, Hair.SIDE_PART, Hair.BALD, Hair.FLAT_TOP)[
            listOf(22, 22, 22, 18, 6, 10).let { w -> var roll = r.next(100); var i = 0; while (roll >= w[i]) { roll -= w[i]; i++ }; i }]
        return Traits(
            head = HeadShape.entries[r.next(5)],
            earScale = 0.85f + r.next(4) * 0.1f,
            browStyle = r.next(4),
            eyeStyle = r.next(4),
            noseStyle = r.next(4),
            mouthStyle = r.next(3),
            hair = hair,
            hairColor = Color(palette[r.next(palette.size)]),
            freckles = lightSkin && r.chance(25),
            mole = r.chance(12),
            scar = op.timesWounded >= 2 && r.chance(60)
        )
    }

    /** What sits on the head, which decides how much hair and face show. */
    enum class Cover { CAP, BERET, BARE, FIELD_VISOR }

    /**
     * Draws ears, the head, hair and the face inside [box] (the head's bounding box). Headgear is drawn after,
     * by the caller. [age] in years (0 when unknown).
     */
    fun DrawScope.drawHead(box: Rect, op: Operator, skin: Color, shadow: Color, cover: Cover, age: Int) {
        val t = traits(op)
        val W = box.width
        val H = box.height
        val cx = box.center.x
        val T = box.top
        fun x(f: Float) = cx + W * f
        fun y(f: Float) = T + H * f
        val gray = Color(0xFFB9B6B0)
        val graying = ((age - 36) / 18f).coerceIn(0f, 0.85f)
        val hairCol = lerp(t.hairColor, gray, graying)
        val templeCol = lerp(t.hairColor, gray, (graying * 1.6f).coerceAtMost(1f))
        val line = shadow.copy(alpha = 0.95f)
        val featureDark = Color(0xFF241C18)

        // Ears
        val earH = H * 0.24f * t.earScale
        val earW = W * 0.11f * t.earScale
        drawOval(shadow, Offset(x(-0.5f) - earW * 0.45f, y(0.42f)), Size(earW, earH))
        drawOval(shadow, Offset(x(0.5f) - earW * 0.55f, y(0.42f)), Size(earW, earH))

        // Head and jaw
        val (jawHalf, jawY, chinHalf) = when (t.head) {
            HeadShape.OVAL -> Triple(0.44f, 0.80f, 0.20f)
            HeadShape.ROUND -> Triple(0.48f, 0.78f, 0.27f)
            HeadShape.SQUARE -> Triple(0.47f, 0.86f, 0.30f)
            HeadShape.LONG -> Triple(0.40f, 0.84f, 0.17f)
            HeadShape.HEART -> Triple(0.38f, 0.78f, 0.11f)
        }
        val head = Path().apply {
            moveTo(box.left, y(0.5f))
            arcTo(Rect(box.left, T, box.right, T + H), 180f, 180f, false)
            cubicTo(box.right, y(0.62f), x(jawHalf + 0.02f), y(jawY - 0.08f), x(jawHalf), y(jawY))
            quadraticBezierTo(x(chinHalf + 0.06f), y(0.99f), x(chinHalf * 0.6f), y(1.0f))
            lineTo(x(-chinHalf * 0.6f), y(1.0f))
            quadraticBezierTo(x(-chinHalf - 0.06f), y(0.99f), x(-jawHalf), y(jawY))
            cubicTo(x(-jawHalf - 0.02f), y(jawY - 0.08f), box.left, y(0.62f), box.left, y(0.5f))
            close()
        }
        drawPath(head, skin)

        // Hair (only the parts the headgear leaves showing)
        when (t.hair) {
            Hair.BALD -> drawArc(Color.White.copy(alpha = 0.18f), 200f, 60f, false, Offset(x(-0.32f), y(0.06f)), Size(W * 0.5f, H * 0.3f), style = Stroke(W * 0.03f))
            else -> {
                val receding = age >= 48
                val bareTop = cover == Cover.BARE || cover == Cover.FIELD_VISOR
                val sideTop = if (bareTop) 0.10f else 0.30f
                // Sides above the ears (sideburns), grayer first
                val sideW = when (t.hair) { Hair.HIGH_AND_TIGHT, Hair.BUZZ -> 0.05f; else -> 0.08f }
                drawRect(templeCol, Offset(x(-0.5f) + W * 0.01f, y(sideTop)), Size(W * sideW, H * (0.44f - sideTop)))
                drawRect(templeCol, Offset(x(0.5f) - W * (0.01f + sideW), y(sideTop)), Size(W * sideW, H * (0.44f - sideTop)))
                if (bareTop) {
                    val crown = when (t.hair) { Hair.BUZZ -> 0.18f; Hair.HIGH_AND_TIGHT -> 0.20f; Hair.FLAT_TOP -> 0.26f; else -> 0.24f }
                    val topY = if (receding) 0.16f else 0.06f
                    val crownPath = Path().apply {
                        moveTo(box.left + W * 0.04f, y(0.34f))
                        arcTo(Rect(box.left, T - H * if (t.hair == Hair.FLAT_TOP) 0.06f else 0f, box.right, T + H * 0.9f), 180f, 180f, false)
                        lineTo(box.right - W * 0.04f, y(0.34f))
                        quadraticBezierTo(cx, y(topY + crown), box.left + W * 0.04f, y(0.34f))
                        close()
                    }
                    val crownCol = if (t.hair == Hair.HIGH_AND_TIGHT || t.hair == Hair.BUZZ) hairCol.copy(alpha = 0.75f) else hairCol
                    drawPath(crownPath, crownCol)
                    if (t.hair == Hair.SIDE_PART) drawLine(skin.copy(alpha = 0.6f), Offset(x(-0.18f), y(0.03f)), Offset(x(-0.24f), y(0.22f)), strokeWidth = W * 0.015f)
                }
            }
        }

        // Face
        val eyeY = y(0.47f)
        val eyeDx = W * if (t.eyeStyle == 3) 0.19f else 0.165f
        val browY = y(0.385f)
        val showEyes = cover != Cover.FIELD_VISOR
        if (showEyes) {
            // Brows, in the hair color
            val browCol = lerp(t.hairColor, gray, graying * 0.6f)
            val bw = W * (if (t.browStyle == 1) 0.035f else 0.022f)
            for (s in listOf(-1f, 1f)) {
                val inner = Offset(cx + s * W * 0.08f, browY + if (t.browStyle == 2) H * 0.01f else 0f)
                val outer = Offset(cx + s * W * 0.25f, browY - if (t.browStyle == 2) H * 0.02f else 0f)
                if (t.browStyle == 3) drawArc(browCol, if (s < 0) 200f else 280f, 60f, false,
                    Offset(cx + s * W * 0.165f - W * 0.09f, browY - H * 0.02f), Size(W * 0.18f, H * 0.08f), style = Stroke(bw, cap = StrokeCap.Round))
                else drawLine(browCol, inner, outer, strokeWidth = bw, cap = StrokeCap.Round)
            }
            // Eyes
            for (s in listOf(-1f, 1f)) {
                val c = Offset(cx + s * eyeDx, eyeY)
                when (t.eyeStyle) {
                    1 -> {
                        drawOval(Color(0xFFF4F1EC), Offset(c.x - W * 0.055f, c.y - H * 0.025f), Size(W * 0.11f, H * 0.05f))
                        drawCircle(featureDark, radius = W * 0.028f, center = c)
                    }
                    2 -> {
                        // Heavy lids: a pupil under a lid line
                        drawCircle(featureDark, radius = W * 0.030f, center = Offset(c.x, c.y + H * 0.004f))
                        drawLine(line, Offset(c.x - W * 0.05f, c.y - H * 0.016f), Offset(c.x + W * 0.05f, c.y - H * 0.016f), strokeWidth = W * 0.016f, cap = StrokeCap.Round)
                    }
                    else -> drawCircle(featureDark, radius = W * 0.034f, center = c)
                }
            }
            // Age lines under the eyes and on the brow
            if (age >= 40) {
                for (s in listOf(-1f, 1f)) drawArc(line.copy(alpha = 0.5f), 20f, 140f, false,
                    Offset(cx + s * eyeDx - W * 0.05f, eyeY + H * 0.02f), Size(W * 0.10f, H * 0.04f), style = Stroke(W * 0.010f))
                if (cover == Cover.BARE || age >= 50) drawLine(line.copy(alpha = 0.45f), Offset(x(-0.14f), y(0.30f)), Offset(x(0.14f), y(0.30f)), strokeWidth = W * 0.010f)
            }
        }
        // Nose
        val noseTop = y(0.50f)
        val noseBot = y(0.63f)
        when (t.noseStyle) {
            0 -> drawLine(line, Offset(cx + W * 0.01f, noseTop), Offset(cx + W * 0.035f, noseBot), strokeWidth = W * 0.018f, cap = StrokeCap.Round)
            1 -> {
                drawLine(line, Offset(cx, noseTop), Offset(cx, noseBot), strokeWidth = W * 0.016f, cap = StrokeCap.Round)
                drawLine(line, Offset(cx - W * 0.03f, noseBot), Offset(cx + W * 0.03f, noseBot), strokeWidth = W * 0.016f, cap = StrokeCap.Round)
            }
            2 -> {
                // Broad nose: a short bridge and two nostrils (a curve here read as a second mouth)
                drawLine(line, Offset(cx, noseTop + H * 0.03f), Offset(cx, noseBot - H * 0.02f), strokeWidth = W * 0.016f, cap = StrokeCap.Round)
                drawCircle(line, radius = W * 0.014f, center = Offset(cx - W * 0.035f, noseBot))
                drawCircle(line, radius = W * 0.014f, center = Offset(cx + W * 0.035f, noseBot))
            }
            else -> drawArc(line, 270f, 160f, false, Offset(cx - W * 0.02f, noseTop), Size(W * 0.06f, noseBot - noseTop), style = Stroke(W * 0.018f, cap = StrokeCap.Round))
        }
        // Mouth
        val mY = y(0.75f)
        // Width is the soldier's own; the expression follows current morale
        val mHalf = W * when (t.mouthStyle) { 0 -> 0.07f; 1 -> 0.085f; else -> 0.10f }
        val mouthCol = featureDark.copy(alpha = 0.85f)
        val mStroke = Stroke(W * 0.02f, cap = StrokeCap.Round)
        when (MoraleRules.band(op)) {
            MoraleRules.Band.HIGH -> drawArc(mouthCol, 20f, 140f, false, Offset(cx - mHalf, mY - H * 0.04f), Size(mHalf * 2, H * 0.06f), style = mStroke)
            MoraleRules.Band.STEADY -> drawLine(mouthCol, Offset(cx - mHalf, mY), Offset(cx + mHalf, mY), strokeWidth = W * 0.02f, cap = StrokeCap.Round)
            MoraleRules.Band.LOW -> drawArc(mouthCol, 205f, 130f, false, Offset(cx - mHalf, mY), Size(mHalf * 2, H * 0.03f), style = mStroke)
            MoraleRules.Band.BROKEN -> drawArc(mouthCol, 200f, 140f, false, Offset(cx - mHalf, mY), Size(mHalf * 2, H * 0.06f), style = mStroke)
        }
        // Marks
        if (t.freckles) listOf(-0.22f to 0.57f, -0.17f to 0.60f, -0.25f to 0.61f, 0.22f to 0.57f, 0.17f to 0.60f, 0.25f to 0.61f).forEach { (fx, fy) ->
            drawCircle(shadow.copy(alpha = 0.7f), radius = W * 0.011f, center = Offset(x(fx), y(fy)))
        }
        if (t.mole) drawCircle(featureDark.copy(alpha = 0.6f), radius = W * 0.014f, center = Offset(x(0.24f), y(0.68f)))
        if (t.scar) drawLine(lerp(skin, Color(0xFFE8C8C0), 0.5f), Offset(x(-0.30f), y(0.52f)), Offset(x(-0.20f), y(0.68f)), strokeWidth = W * 0.016f, cap = StrokeCap.Round)
    }

    fun ageOf(op: Operator, today: Double = GameCalendar.today): Int = if (op.joinedDay < 0) 0 else GameCalendar.age(op, today)
}
