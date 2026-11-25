package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.ui.components.BaseArt.box
import com.example.ui.components.BaseArt.Season
import kotlin.math.cos
import kotlin.math.sin

/**
 * Art 2b: the Parade Ground (reviewing stand, bleachers, the marble Memorial Wall with a gold star for each soldier
 * lost), the Heritage Walk (one statue per campaign, each its own design and material, raised the first time the
 * campaign is finished), and construction sites (scaffolding, a tower crane, materials).
 */
internal object BaseLandmarks {
    private val Marble = Color(0xFFEDEBE6)
    private val MarbleShade = Color(0xFFCFCAC0)
    private val Gold = Color(0xFFE2C25A)
    private val Navy = Color(0xFF1F2E4A)

    /** How many gold stars fit on the wall face; past this the wall still counts them in its plaque. */
    const val WALL_STARS = 90

    fun DrawScope.star(c: Offset, r: Float, col: Color) {
        val p = Path()
        for (i in 0 until 10) {
            val a = Math.toRadians((-90 + i * 36).toDouble())
            val rr = if (i % 2 == 0) r else r * 0.45f
            val x = c.x + (rr * cos(a)).toFloat(); val y = c.y + (rr * sin(a)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        drawPath(p, col)
    }

    /** The Memorial Wall's face in pixels inside a Parade Ground plot (for its tap target too). */
    fun memorialWallRect(rect: Rect, u: Float): Rect = Rect(Offset(rect.left + 14f * u, rect.top + 2f * u), Size(89f * u, 16f * u))

    fun DrawScope.paradeGround(rect: Rect, u: Float, band: Int, fallen: Int) {
        val l = BaseArt.Lot(rect, u)
        val winter = BaseArt.season == Season.WINTER
        // Gravel border and a mowed field with stripes
        drawRect(Color(0xFFB9B09A), l.o(3f, 18f), Size(111f * u, 42f * u))
        val fieldCol = if (winter) Color(0xFFE9EEF0) else if (BaseArt.season == Season.SUMMER) Color(0xFF7A8450) else Color(0xFF6E8250)
        drawRect(fieldCol, l.o(5f, 19.5f), Size(107f * u, 39f * u))
        var sx = 5f; var i = 0
        while (sx < 112f) {
            if (i % 2 == 0) drawRect(Color.White.copy(alpha = if (winter) 0.25f else 0.07f), l.o(sx, 19.5f), Size(minOf(7f, 112f - sx) * u, 39f * u))
            sx += 7f; i++
        }
        // Formation on the field
        for (row in 0..2) for (col in 0..9) {
            val fx = 36f + col * 4.6f; val fy = 32f + row * 5f
            drawOval(BaseArt.Shadow.copy(alpha = 0.25f), l.o(fx - 0.2f, fy - 0.3f), Size(1.6f * u, 0.7f * u))
            drawRect(Color(0xFF2F3A28), l.o(fx - 0.5f, fy - 2.4f), Size(1f * u, 2.4f * u))
            drawCircle(Color(0xFF9A7A5E), 0.5f * u, l.o(fx, fy - 2.9f))
        }

        // Far end: the marble Memorial Wall
        box(l, 18f, 17f, 81f, 4f, 1.6f, MarbleShade, Color(0xFFDCD8CF))
        val face = box(l, 21f, 15.4f, 75f, 3f, 10f, Marble, Color(0xFFF6F5F2))
        drawRect(MarbleShade.copy(alpha = 0.6f), Offset(face.left, face.bottom - 0.8f * u), Size(face.width, 0.8f * u))
        // Marble veining
        drawLine(MarbleShade, Offset(face.left + 9f * u, face.top), Offset(face.left + 14f * u, face.bottom), strokeWidth = 0.15f * u)
        drawLine(MarbleShade, Offset(face.left + 52f * u, face.top), Offset(face.left + 47f * u, face.bottom), strokeWidth = 0.15f * u)
        val shown = fallen.coerceAtMost(WALL_STARS)
        val perRow = 30
        for (n in 0 until shown) {
            val r = n / perRow; val c = n % perRow
            val cx = face.left + (1.6f + c * (face.width / u - 3.2f) / (perRow - 1)) * u
            val cy = face.top + (2f + r * 2.8f) * u
            star(Offset(cx, cy), 0.95f * u, Gold)
        }
        // Flags at each end of the wall and a wreath at its center
        for (fx in listOf(13f, 104f)) BaseArt.run { flag(l, fx, 17f, 15f, Color(0xFF2F5FA6)) }
        drawLine(Color(0xFF6E5A3A), l.o(58.5f, 19.5f), l.o(58.5f, 16.5f), strokeWidth = 0.4f * u)
        drawCircle(Color(0xFF2D6A3E), 1.6f * u, l.o(58.5f, 16f), style = Stroke(0.8f * u))
        drawCircle(Color(0xFFB22234), 0.5f * u, l.o(58.5f, 17.4f))

        // Near end: bleachers either side and the reviewing stand with its podium
        bleachers(l, 6f, 61f, 30f)
        bleachers(l, 81f, 61f, 30f)
        box(l, 42f, 61f, 33f, 6f, 3f, Navy, Color(0xFF9A7A50))
        drawRect(Gold, l.o(42f, 58.6f), Size(33f * u, 0.4f * u))
        val pod = box(l, 56f, 58f, 5f, 2.4f, 4.6f, Color(0xFF6E4A2E), Color(0xFF7E5A3A), shadow = false)
        drawCircle(Gold, 1f * u, Offset(pod.center.x, pod.top + 1.8f * u))
        for (px in listOf(43f, 74f)) drawLine(Color(0xFFC8C3B3), l.o(px, 58f), l.o(px, 49f), strokeWidth = 0.5f * u)
        box(l, 41.5f, 49f, 34f, 5f, 1.2f, Navy, Color(0xFF2C3E60), shadow = false)
        drawRect(Gold, l.o(41.5f, 48.4f), Size(34f * u, 0.5f * u))
    }

    private fun DrawScope.bleachers(l: BaseArt.Lot, x: Float, base: Float, w: Float) {
        val seat = Color(0xFFB9BEC2)
        val riser = Color(0xFF6E7378)
        for (step in 2 downTo 0) {
            val f = box(l, x, base - step * 2.4f, w, 2.4f, 2f + step * 2f, riser, seat, shadow = step == 2)
            drawLine(Color(0xFF8C9196), Offset(f.left, f.top - 1.2f * l.u), Offset(f.right, f.top - 1.2f * l.u), strokeWidth = 0.25f * l.u)
        }
        // A few spectators
        for (k in 0..4) drawCircle(listOf(Color(0xFF3A4A6A), Color(0xFF8E4A2E), Color(0xFF5E6B45))[k % 3], 0.6f * l.u, l.o(x + 3f + k * 5.5f, base - 8.6f + (k % 2) * 2.4f))
    }

    // ---------------- Heritage Walk ----------------

    /** Statue spots (center x, ground y) in a Heritage Walk plot, in campaign order 1-10, then the SF campaign. */
    val STATUE_SPOTS: List<Pair<Float, Float>> = listOf(
        7f to 29f, 17f to 29f, 41f to 29f, 51f to 29f,
        7f to 43f, 17f to 43f, 41f to 43f, 51f to 43f,
        10f to 57f, 48f to 57f,
        29f to 16f
    )

    /** Which spot a campaign takes: theater campaigns by order, the SF campaign at the head of the walk. */
    fun spotIndex(order: Int, isSf: Boolean): Int = if (isSf) 10 else (order - 1).coerceIn(0, 9)

    fun DrawScope.heritageWalk(rect: Rect, u: Float, band: Int, raised: Map<Int, Boolean>) {
        val l = BaseArt.Lot(rect, u)
        // Stone path from the road up to a small plaza at the head of the walk
        val stone = Color(0xFFC9BFA6)
        drawRect(stone, l.o(25f, 20f), Size(8f * u, 43f * u))
        for (k in 0..9) drawLine(Color(0xFFAFA58C), l.o(25f, 22f + k * 4.2f), l.o(33f, 22f + k * 4.2f), strokeWidth = 0.25f * u)
        drawOval(stone, l.o(17f, 13f), Size(24f * u, 10f * u))
        drawOval(Color(0xFFAFA58C), l.o(17f, 13f), Size(24f * u, 10f * u), style = Stroke(0.3f * u))
        if (band >= 2) {
            // Benches facing the path
            for (by in listOf(36f, 50f)) for (bx in listOf(22f, 34f)) box(l, bx, by, 2f, 3f, 1f, Color(0xFF7A5A3A), Color(0xFF8E6A46), shadow = false)
        }
        if (band >= 3) {
            // Low hedges along the sides
            drawRoundRect(Color(0xFF3F5130).copy(alpha = if (BaseArt.season == Season.WINTER) 0.4f else 1f), l.o(1f, 20f), Size(2f * u, 42f * u), CornerRadius(1f * u))
            drawRoundRect(Color(0xFF3F5130).copy(alpha = if (BaseArt.season == Season.WINTER) 0.4f else 1f), l.o(55f, 20f), Size(2f * u, 42f * u), CornerRadius(1f * u))
        }
        // Back to front so nearer statues overlap the ones behind
        STATUE_SPOTS.withIndex().sortedBy { it.value.second }.forEach { (idx, spot) ->
            val foot = l.o(spot.first, spot.second)
            val scale = if (idx == 10) 1.15f else 0.95f
            if (raised[idx] == true) statue(idx, foot, scale * u) else barePedestal(foot, scale * u)
        }
    }

    // A statue design per spot: 0-9 the theater campaigns in order, 10 the SF campaign.
    private class Mat(val mid: Color, val light: Color, val dark: Color)
    private val BRONZE = Mat(Color(0xFFA9773F), Color(0xFFCB9C60), Color(0xFF74502A))
    private val COPPER = Mat(Color(0xFF5E9E88), Color(0xFF86C2AC), Color(0xFF3E7262))
    private val MARBLE = Mat(Color(0xFFE9E6E0), Color(0xFFFAF9F6), Color(0xFFBDB8AE))
    private val DARK_BRONZE = Mat(Color(0xFF5A4632), Color(0xFF7E664A), Color(0xFF3A2C1E))

    private enum class Ped { SQUARE, STEPPED, ROUND, ROUGH, TALL, GRANITE }

    /** The material and pedestal each statue uses (no two statues share a design). */
    private val DESIGNS: List<Pair<Mat, Ped>> = listOf(
        BRONZE to Ped.SQUARE,       // I   Whispering Pines: kneeling rifleman by a pine
        MARBLE to Ped.STEPPED,      // II  Cold Signal: radioman in a parka
        COPPER to Ped.ROUND,        // III Velvet Tide: "follow me" under a doorway arch
        BRONZE to Ped.ROUGH,        // IV  Sovereign Horizon: two soldiers raising a flag
        DARK_BRONZE to Ped.SQUARE,  // V   Iron Mirage: battlefield cross (rifle, helmet, boots)
        COPPER to Ped.STEPPED,      // VI  Green Veil: carrying a wounded buddy
        MARBLE to Ped.TALL,         // VII Glass Canyon: soldier at the salute
        BRONZE to Ped.ROUGH,        // VIII Thin Air: climber planting a flag on the summit
        COPPER to Ped.SQUARE,       // IX  Broken Chain: lookout beside an anchor
        MARBLE to Ped.STEPPED,      // X   Last Light: a squad of three
        DARK_BRONZE to Ped.GRANITE  // SF  Free Strait: Green Beret kneeling before the dagger
    )

    /** Pedestal height in statue units, so the figure stands on top. */
    private fun pedHeight(p: Ped) = when (p) { Ped.SQUARE -> 3f; Ped.STEPPED -> 3.4f; Ped.ROUND -> 2.6f; Ped.ROUGH -> 3f; Ped.TALL -> 5f; Ped.GRANITE -> 3.4f }

    private fun DrawScope.pedestal(foot: Offset, s: Float, p: Ped) {
        fun o(x: Float, y: Float) = Offset(foot.x + x * s, foot.y + y * s)
        drawOval(BaseArt.Shadow.copy(alpha = 0.28f), o(-3f, -0.8f), Size(8f * s, 2f * s))
        val granite = Color(0xFF9A9890); val graniteTop = Color(0xFFB8B6AE)
        when (p) {
            Ped.SQUARE -> { drawRect(granite, o(-2.8f, -3f), Size(5.6f * s, 3f * s)); drawRect(graniteTop, o(-2.8f, -4f), Size(5.6f * s, 1f * s)) }
            Ped.TALL -> { drawRect(granite, o(-2.2f, -5f), Size(4.4f * s, 5f * s)); drawRect(graniteTop, o(-2.2f, -5.9f), Size(4.4f * s, 0.9f * s))
                drawRect(Gold.copy(alpha = 0.8f), o(-1.2f, -3.4f), Size(2.4f * s, 1f * s)) }
            Ped.STEPPED -> {
                drawRect(granite, o(-3.4f, -1.4f), Size(6.8f * s, 1.4f * s)); drawRect(graniteTop, o(-3.4f, -2f), Size(6.8f * s, 0.6f * s))
                drawRect(granite, o(-2.5f, -3.4f), Size(5f * s, 1.4f * s)); drawRect(graniteTop, o(-2.5f, -4.2f), Size(5f * s, 0.8f * s))
            }
            Ped.ROUND -> {
                drawRect(granite, o(-2.6f, -2.6f), Size(5.2f * s, 2.6f * s))
                drawOval(granite, o(-2.6f, -0.6f), Size(5.2f * s, 1.2f * s))
                drawOval(graniteTop, o(-2.6f, -3.2f), Size(5.2f * s, 1.4f * s))
            }
            Ped.ROUGH -> {
                val rock = Path().apply {
                    moveTo(foot.x - 3.4f * s, foot.y); lineTo(foot.x - 3f * s, foot.y - 2.2f * s); lineTo(foot.x - 1.4f * s, foot.y - 3.4f * s)
                    lineTo(foot.x + 0.8f * s, foot.y - 3.1f * s); lineTo(foot.x + 2.8f * s, foot.y - 2.4f * s); lineTo(foot.x + 3.4f * s, foot.y); close()
                }
                drawPath(rock, Color(0xFF8E8A7E))
                drawLine(Color(0xFFA9A598), o(-2.6f, -2.4f), o(-0.6f, -3.2f), strokeWidth = 0.35f * s)
            }
            Ped.GRANITE -> {
                drawRect(Color(0xFF2C2C30), o(-3f, -3.4f), Size(6f * s, 3.4f * s)); drawRect(Color(0xFF48484E), o(-3f, -4.2f), Size(6f * s, 0.8f * s))
                drawRect(Color(SF_RED), o(-1.6f, -2.2f), Size(3.2f * s, 0.5f * s))
            }
        }
    }

    private const val SF_RED = 0xFFB3242B

    fun DrawScope.barePedestal(foot: Offset, s: Float) {
        drawOval(BaseArt.Shadow.copy(alpha = 0.2f), Offset(foot.x - 2.6f * s, foot.y - 0.6f * s), Size(6f * s, 1.4f * s))
        drawRect(Color(0xFFA6A49C), Offset(foot.x - 2.2f * s, foot.y - 2f * s), Size(4.4f * s, 2f * s))
        drawRect(Color(0xFFBDBBB3), Offset(foot.x - 2.2f * s, foot.y - 2.8f * s), Size(4.4f * s, 0.8f * s))
    }

    /** Draws statue [idx] standing at [foot] (the pedestal's ground center); [s] is pixels per statue unit (~15 units tall). */
    fun DrawScope.statue(idx: Int, foot: Offset, s: Float) {
        val (m, ped) = DESIGNS[idx.coerceIn(0, DESIGNS.lastIndex)]
        pedestal(foot, s, ped)
        val top = foot.y - (pedHeight(ped) + 0.8f) * s
        fun o(x: Float, y: Float) = Offset(foot.x + x * s, top + y * s)
        fun limb(a: Offset, b: Offset, w: Float = 0.9f, c: Color = m.mid) = drawLine(c, a, b, strokeWidth = w * s, cap = StrokeCap.Round)
        fun head(x: Float, y: Float, r: Float = 0.8f) { drawCircle(m.mid, r * s, o(x, y)); drawCircle(m.light, r * 0.45f * s, o(x - r * 0.3f, y - r * 0.3f)) }
        fun torso(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawRoundRect(m.mid, o(x1, y1), Size((x2 - x1) * s, (y2 - y1) * s), CornerRadius(0.5f * s))
            drawRect(m.dark.copy(alpha = 0.5f), o((x1 + x2) / 2 + (x2 - x1) * 0.15f, y1 + 0.3f), Size((x2 - x1) * 0.35f * s, (y2 - y1 - 0.6f) * s))
            drawLine(m.light, o(x1 + 0.3f, y1 + 0.4f), o(x1 + 0.3f, y2 - 0.4f), strokeWidth = 0.3f * s)
        }
        fun helmet(x: Float, y: Float, r: Float = 0.95f) = drawArc(m.dark, 180f, 180f, true, o(x - r, y - r), Size(2 * r * s, 2 * r * s))
        fun standing(x: Float, scale: Float = 1f) {
            limb(o(x - 0.5f * scale, -4.6f * scale), o(x - 0.7f * scale, 0f)); limb(o(x + 0.5f * scale, -4.6f * scale), o(x + 0.7f * scale, 0f))
            torso(x - 1.2f * scale, -8.6f * scale, x + 1.2f * scale, -4.2f * scale)
        }
        when (idx) {
            0 -> { // Kneeling rifleman beside a pine
                drawRect(m.dark, o(-3.6f, -4f), Size(0.6f * s, 4f * s))
                for (k in 0..2) drawPath(Path().apply { moveTo(foot.x - (5.2f - k * 0.5f) * s, top + (-2.5f - k * 2.2f) * s); lineTo(foot.x - 3.3f * s, top + (-6f - k * 2.2f) * s); lineTo(foot.x - (1.4f + k * 0.5f) * s, top + (-2.5f - k * 2.2f) * s); close() }, m.mid)
                limb(o(0.2f, -3.6f), o(1.8f, -3.2f)); limb(o(1.8f, -3.2f), o(1.9f, 0f))
                limb(o(-0.2f, -3.6f), o(-0.9f, -0.2f)); limb(o(-0.9f, -0.2f), o(-2.6f, 0f), 0.8f)
                torso(-1.1f, -7.8f, 1.1f, -3.4f)
                limb(o(2.4f, 0f), o(2.4f, -9.6f), 0.4f, m.dark)
                limb(o(0.9f, -7.2f), o(2.3f, -6f), 0.7f)
                limb(o(-0.9f, -7.2f), o(0.9f, -4.4f), 0.7f)
                head(0f, -8.8f); helmet(0f, -9f)
            }
            1 -> { // Radioman in a parka, hand to the headset, whip antenna
                limb(o(1f, -7.4f), o(2.6f, -14f), 0.25f, m.dark)
                standing(0f)
                drawPath(Path().apply { moveTo(foot.x - 1.6f * s, top - 5f * s); lineTo(foot.x + 1.6f * s, top - 5f * s); lineTo(foot.x + 1.9f * s, top - 2.6f * s); lineTo(foot.x - 1.9f * s, top - 2.6f * s); close() }, m.mid)
                torso(-1.5f, -8.8f, 1.5f, -4.4f)
                limb(o(-1.2f, -8f), o(-1.7f, -4.8f), 0.8f)
                limb(o(1.2f, -8f), o(1.4f, -9.6f), 0.8f)
                drawCircle(m.dark, 1.05f * s, o(0f, -9.8f)); head(0f, -9.8f, 0.75f)
            }
            2 -> { // "Follow me" beneath a doorway arch
                drawArc(m.dark, 180f, 180f, false, o(-3.4f, -12.4f), Size(6.8f * s, 6f * s), style = Stroke(0.6f * s))
                limb(o(-3.4f, -9.4f), o(-3.4f, 0f), 0.6f, m.dark); limb(o(3.4f, -9.4f), o(3.4f, 0f), 0.6f, m.dark)
                standing(0f)
                limb(o(1f, -8f), o(2.8f, -10.8f), 0.75f)
                limb(o(-1f, -8f), o(-1.6f, -5f), 0.75f)
                head(0f, -9.6f); helmet(0f, -9.8f)
            }
            3 -> { // Two soldiers raising a flag
                limb(o(-3f, 0f), o(2.6f, -13f), 0.35f, m.dark)
                drawRect(m.mid, o(2.6f, -13f), Size(3.6f * s, 2.2f * s))
                drawLine(m.light, o(2.6f, -12.4f), o(6.2f, -12.4f), strokeWidth = 0.25f * s)
                // Leaning figures pushing the pole up
                limb(o(-2.6f, -3.6f), o(-3.2f, 0f)); limb(o(-2f, -3.6f), o(-1.4f, 0f))
                limb(o(-2.3f, -3.8f), o(-0.8f, -7.4f), 1.9f)
                limb(o(-0.6f, -6.6f), o(-0.6f, -4.6f), 0.6f)
                head(-0.3f, -8.4f, 0.75f); helmet(-0.3f, -8.6f, 0.85f)
                limb(o(1f, -4.4f), o(0.6f, 0f)); limb(o(1.6f, -4.4f), o(2.2f, 0f))
                limb(o(1.3f, -4.6f), o(1.8f, -8.4f), 1.9f)
                limb(o(2.2f, -8f), o(1.2f, -9.4f), 0.6f)
                head(2.2f, -9.4f, 0.75f); helmet(2.2f, -9.6f, 0.85f)
            }
            4 -> { // Battlefield cross: boots, rifle bayonet down, helmet on top, dog tags
                drawRect(m.dark, o(-1.8f, -1.4f), Size(1.4f * s, 1.4f * s)); drawRect(m.dark, o(0.4f, -1.4f), Size(1.4f * s, 1.4f * s))
                drawRect(m.mid, o(-1.8f, -2.6f), Size(1.2f * s, 1.3f * s)); drawRect(m.mid, o(0.5f, -2.6f), Size(1.2f * s, 1.3f * s))
                limb(o(0f, -0.4f), o(0f, -8.4f), 0.55f, m.mid)
                drawRect(m.mid, o(-0.6f, -6.6f), Size(1.2f * s, 2.4f * s))
                drawArc(m.mid, 180f, 180f, true, o(-1.5f, -10.2f), Size(3f * s, 3f * s))
                drawRect(m.dark, o(-1.7f, -8.8f), Size(3.4f * s, 0.4f * s))
                drawLine(m.light, o(-0.2f, -8.2f), o(0.6f, -6.4f), strokeWidth = 0.18f * s)
                drawRect(m.light, o(0.4f, -6.5f), Size(0.5f * s, 0.7f * s))
            }
            5 -> { // Carrying a wounded buddy across the shoulders
                limb(o(-0.5f, -4.6f), o(-1.2f, 0f)); limb(o(0.5f, -4.6f), o(1f, 0f))
                torso(-1.2f, -8.4f, 1.2f, -4.2f)
                // The wounded soldier draped over the shoulders, behind the carrier's head
                limb(o(-3.6f, -10.2f), o(3f, -9.8f), 1.7f, m.mid)
                limb(o(3f, -9.4f), o(4.2f, -7f), 0.8f); limb(o(3f, -9.4f), o(4.6f, -8f), 0.8f)
                limb(o(-3.6f, -9.6f), o(-4.2f, -7.4f), 0.7f)
                head(-4.4f, -9.8f, 0.7f)
                limb(o(1f, -8f), o(3.2f, -8.6f), 0.7f)
                head(0.2f, -9.2f); helmet(0.2f, -9.4f)
            }
            6 -> { // At the salute
                standing(0f)
                limb(o(-1f, -8f), o(-1.4f, -4.8f), 0.75f)
                limb(o(1f, -8f), o(2.2f, -9.2f), 0.75f); limb(o(2.2f, -9.2f), o(0.9f, -9.9f), 0.6f)
                head(0f, -9.6f)
                drawArc(m.dark, 180f, 180f, true, o(-0.95f, -11.1f), Size(1.9f * s, 1.6f * s))
                drawLine(m.dark, o(-1.1f, -10.3f), o(1.3f, -10.3f), strokeWidth = 0.35f * s)
            }
            7 -> { // Climber planting a flag on the summit
                limb(o(1.8f, -14f), o(1.6f, -2f), 0.35f, m.dark)
                drawRect(m.mid, o(1.8f, -14f), Size(3.2f * s, 2f * s))
                limb(o(-0.4f, -4.6f), o(-1.8f, -2.2f)); limb(o(-1.8f, -2.2f), o(-1.6f, 0f))
                limb(o(0.4f, -4.6f), o(1.2f, -1.6f))
                torso(-1.2f, -8.6f, 1.2f, -4.2f)
                drawRoundRect(m.dark, o(-2.2f, -8.2f), Size(1.2f * s, 3.2f * s), CornerRadius(0.4f * s))
                limb(o(1f, -8f), o(1.7f, -10f), 0.7f); limb(o(0.6f, -7.2f), o(1.7f, -7f), 0.7f)
                head(0f, -9.6f, 0.75f)
                drawArc(m.dark, 180f, 180f, true, o(-0.85f, -10.5f), Size(1.7f * s, 1.4f * s))
            }
            8 -> { // Lookout beside an anchor, hand shading the eyes
                val ax = -3.2f
                limb(o(ax, -1f), o(ax, -8.4f), 0.55f, m.dark)
                limb(o(ax - 1.4f, -7.4f), o(ax + 1.4f, -7.4f), 0.45f, m.dark)
                drawCircle(m.dark, 0.7f * s, o(ax, -9.1f), style = Stroke(0.3f * s))
                drawArc(m.dark, 0f, 180f, false, o(ax - 1.8f, -2.8f), Size(3.6f * s, 2.6f * s), style = Stroke(0.5f * s))
                standing(0.8f)
                limb(o(-0.2f, -8f), o(-0.6f, -4.8f), 0.75f)
                limb(o(1.8f, -8f), o(2.4f, -9.4f), 0.75f); limb(o(2.4f, -9.4f), o(1.2f, -10.1f), 0.55f)
                head(0.8f, -9.6f)
            }
            9 -> { // A squad of three
                standing(-2.6f, 0.86f); limb(o(-3.5f, -7f), o(-3.8f, -4f), 0.7f); limb(o(-1.7f, -7f), o(-1.4f, -4f), 0.7f)
                head(-2.6f, -8.3f, 0.72f); helmet(-2.6f, -8.5f, 0.82f)
                standing(2.6f, 0.86f); limb(o(1.7f, -7f), o(1.4f, -4f), 0.7f); limb(o(3.5f, -7f), o(3.8f, -4f), 0.7f)
                head(2.6f, -8.3f, 0.72f); helmet(2.6f, -8.5f, 0.82f)
                standing(0f, 1.08f)
                limb(o(-2f, -4.4f), o(2.2f, -9.8f), 0.4f, m.dark)
                limb(o(-1.1f, -8.6f), o(-0.6f, -6f), 0.75f); limb(o(1.1f, -8.6f), o(1.2f, -8f), 0.75f)
                head(0f, -10.2f); helmet(0f, -10.4f)
            }
            else -> { // SF: kneeling Green Beret before an upright dagger
                // The dagger behind him
                limb(o(-2.6f, -2f), o(-2.6f, -13.6f), 0.7f, m.light)
                drawPath(Path().apply { moveTo(foot.x - 2.6f * s, top - 14.8f * s); lineTo(foot.x - 3.1f * s, top - 13.6f * s); lineTo(foot.x - 2.1f * s, top - 13.6f * s); close() }, m.light)
                limb(o(-4.2f, -5f), o(-1f, -5f), 0.55f, m.mid)
                limb(o(-2.6f, -5f), o(-2.6f, -2.4f), 0.6f, m.dark)
                drawCircle(m.mid, 0.5f * s, o(-2.6f, -2.2f))
                limb(o(0.6f, -3.6f), o(2.2f, -3.2f)); limb(o(2.2f, -3.2f), o(2.3f, 0f))
                limb(o(0.2f, -3.6f), o(-0.5f, -0.2f)); limb(o(-0.5f, -0.2f), o(-2f, 0f), 0.8f)
                torso(-0.7f, -7.8f, 1.5f, -3.4f)
                limb(o(2.9f, 0f), o(2.9f, -8.6f), 0.4f, m.dark)
                limb(o(1.3f, -7.2f), o(2.8f, -6f), 0.7f)
                limb(o(-0.5f, -7.2f), o(1.3f, -4.4f), 0.7f)
                head(0.4f, -8.8f)
                // Beret, pulled to the right
                drawOval(m.dark, o(-0.6f, -10f), Size(2.4f * s, 0.95f * s))
                drawCircle(Color(SF_RED), 0.22f * s, o(-0.2f, -9.6f))
            }
        }
    }

    // ---------------- Construction ----------------

    /** A building site: scaffolding across the front, a tower crane, a stack of materials. */
    fun DrawScope.constructionSite(rect: Rect, u: Float, frac: Float, wide: Boolean) {
        val l = BaseArt.Lot(rect, u)
        val b = l.base
        val pole = Color(0xFFC9A55A)
        val plank = Color(0xFF8E6A46)
        // Scaffolding: a frame of poles and two plank decks over the front of the building
        val left = 4f; val right = if (wide) l.w - 26f else l.w - 16f
        val topY = b - 20f
        var x = left
        while (x <= right + 0.1f) { drawLine(pole, l.o(x, b), l.o(x, topY), strokeWidth = 0.4f * u); x += (right - left) / 5f }
        for (y in listOf(b - 7f, b - 14f, topY)) {
            drawLine(pole, l.o(left, y), l.o(right, y), strokeWidth = 0.35f * u)
            drawRect(plank, l.o(left, y - 0.6f), Size((right - left) * u, 0.7f * u))
        }
        for (k in 0 until 5) {
            val x0 = left + k * (right - left) / 5f
            drawLine(pole.copy(alpha = 0.7f), l.o(x0, b - 7f), l.o(x0 + (right - left) / 5f, b - 14f), strokeWidth = 0.25f * u)
        }
        // Tower crane at the right edge
        val cx = l.w - 7f
        val yellow = Color(0xFFE0B84A)
        val mastTop = 6f
        drawLine(yellow, l.o(cx - 1f, b), l.o(cx - 1f, mastTop), strokeWidth = 0.4f * u)
        drawLine(yellow, l.o(cx + 1f, b), l.o(cx + 1f, mastTop), strokeWidth = 0.4f * u)
        var y = b - 2f
        while (y > mastTop + 2f) { drawLine(yellow, l.o(cx - 1f, y), l.o(cx + 1f, y - 2f), strokeWidth = 0.25f * u); y -= 2f }
        drawRect(Color(0xFF3A3F45), l.o(cx - 1.6f, mastTop - 0.2f), Size(3.2f * u, 2.4f * u))
        // Jib out over the building, counterweight behind
        val jibEnd = if (wide) l.w * 0.3f else 6f
        drawLine(yellow, l.o(cx + 4f, mastTop - 0.6f), l.o(jibEnd, mastTop - 0.6f), strokeWidth = 0.6f * u)
        drawLine(yellow, l.o(cx, mastTop - 4f), l.o(jibEnd + 6f, mastTop - 0.6f), strokeWidth = 0.25f * u)
        drawLine(yellow, l.o(cx, mastTop - 4f), l.o(cx, mastTop - 0.6f), strokeWidth = 0.4f * u)
        drawRect(Color(0xFF6E7378), l.o(cx + 2f, mastTop - 0.4f), Size(2.4f * u, 1.6f * u))
        // Hook and a load moving with progress
        val hookX = jibEnd + 3f + (cx - jibEnd - 6f) * frac
        drawLine(Color(0xFF2A2E33), l.o(hookX, mastTop - 0.4f), l.o(hookX, topY - 3f), strokeWidth = 0.2f * u)
        drawRect(Color(0xFF8E4A2E), l.o(hookX - 1.4f, topY - 3f), Size(2.8f * u, 1.2f * u))
        // Pallets of materials
        box(l, right + 1.5f, b + 0.5f, 4f, 2.5f, 1.6f, Color(0xFFB0A38A), Color(0xFFC4B8A0), shadow = false)
        box(l, right + 1.5f, b - 2f, 4f, 2.5f, 1.6f, Color(0xFF9DA39A), Color(0xFFB4BAB2), shadow = false)
    }
}
