package com.example.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.domain.model.FacilityType
import com.example.domain.model.SF_PATCH_BLACK
import com.example.domain.model.SF_PATCH_RED

/**
 * Art 2a: the base map in a three-quarter view. Every structure shows its roof and its front wall, casts a
 * soft shadow to the right, and each facility has its own silhouette that grows in four steps
 * (canvas Lv 1-3, wood Lv 4-6, block Lv 7-9, expanded Lv 10). Plot coordinates are map units from the plot's
 * top-left corner; [base] is the ground line along a structure's front wall.
 */
internal object BaseArt {
    enum class Roof { FLAT, GABLE, ARCH }

    // Palette
    val Grass = Color(0xFF5F6D45)
    val GrassLight = Color(0xFF6C7B4F)
    val GrassDark = Color(0xFF4F5B39)
    val CanvasWall = Color(0xFFC6B68B)
    val CanvasLight = Color(0xFFD8CA9F)
    val CanvasDark = Color(0xFFA99A70)
    val WoodWall = Color(0xFF9A7048)
    val WoodRoof = Color(0xFF5E4430)
    val Concrete = Color(0xFFD2CDC0)
    val Tar = Color(0xFF5E5D59)
    val Glass = Color(0xFF3D5163)
    val GlassLit = Color(0xFF8FB4CC)
    val Steel = Color(0xFF8A8F94)
    val Ink = Color(0xFF1E211A)
    val Shadow = Color(0xFF1B2114)
    val Brick = Color(0xFFAE6446)
    val Slate = Color(0xFF4C5158)
    val Tan = Color(0xFFD3C39E)
    val White = Color(0xFFECEAE4)
    val Red = Color(0xFFC0322B)

    /** A plot in map units. */
    class Lot(val rect: Rect, val u: Float) {
        fun x(v: Float) = rect.left + v * u
        fun y(v: Float) = rect.top + v * u
        fun o(px: Float, py: Float) = Offset(x(px), y(py))
        val base get() = rect.height / u - 13f
        val w get() = rect.width / u
    }

    private fun lighter(c: Color, f: Float = 0.18f) = Color(
        (c.red + (1 - c.red) * f).coerceIn(0f, 1f), (c.green + (1 - c.green) * f).coerceIn(0f, 1f), (c.blue + (1 - c.blue) * f).coerceIn(0f, 1f), c.alpha)
    private fun darker(c: Color, f: Float = 0.2f) = Color(c.red * (1 - f), c.green * (1 - f), c.blue * (1 - f), c.alpha)

    /** Soft ground shadow to the right of a structure (light from the upper left). */
    private fun DrawScope.shadow(l: Lot, x: Float, base: Float, w: Float, d: Float, h: Float) {
        val sh = (h * 0.45f).coerceAtLeast(1.5f)
        val p = Path().apply {
            moveTo(l.x(x + w), l.y(base))
            lineTo(l.x(x + w + sh), l.y(base - sh * 0.35f))
            lineTo(l.x(x + w + sh), l.y(base - d - sh * 0.35f))
            lineTo(l.x(x + w), l.y(base - d))
            close()
        }
        drawPath(p, Shadow.copy(alpha = 0.28f))
        drawRect(Shadow.copy(alpha = 0.18f), l.o(x - 0.4f, base - 0.4f), Size((w + 0.8f) * l.u, 1.2f * l.u))
    }

    /**
     * A box structure: roof face above, front wall below. Returns the front wall in pixels for windows and doors.
     */
    fun DrawScope.box(
        l: Lot, x: Float, base: Float, w: Float, d: Float, h: Float,
        wall: Color, roof: Color, style: Roof = Roof.FLAT, shadow: Boolean = true
    ): Rect {
        if (shadow) shadow(l, x, base, w, d, h)
        val front = Rect(l.o(x, base - h), Size(w * l.u, h * l.u))
        when (style) {
            Roof.FLAT -> {
                drawRect(roof, l.o(x, base - h - d), Size(w * l.u, d * l.u))
                drawRect(lighter(roof, 0.28f), l.o(x, base - h - d), Size(w * l.u, d * l.u), style = Stroke(0.6f * l.u))
                drawRect(wall, front.topLeft, front.size)
                drawRect(darker(wall, 0.25f), front.topLeft, Size(front.width, 0.7f * l.u))
            }
            Roof.GABLE -> {
                // Ridge runs left to right: the far slope catches the light, the near slope is darker
                drawRect(lighter(roof, 0.12f), l.o(x - 0.8f, base - h - d), Size((w + 1.6f) * l.u, d * 0.5f * l.u))
                drawRect(roof, l.o(x - 0.8f, base - h - d * 0.5f), Size((w + 1.6f) * l.u, d * 0.5f * l.u + 0.8f * l.u))
                drawLine(darker(roof, 0.35f), l.o(x - 0.8f, base - h - d * 0.5f), l.o(x + w + 0.8f, base - h - d * 0.5f), strokeWidth = 0.5f * l.u)
                drawRect(wall, front.topLeft, front.size)
                drawRect(darker(roof, 0.2f), front.topLeft, Size(front.width, 0.6f * l.u))
            }
            Roof.ARCH -> {
                // Quonset: the ridge runs front to back, the front wall is an arch end
                val bands = 4
                for (i in 0 until bands) {
                    val f = if (i == 1) 0.22f else if (i == 2) 0.08f else 0f
                    drawRect(lighter(roof, f), l.o(x + i * w / bands, base - h - d + h * 0.3f), Size(w / bands * l.u + 0.3f * l.u, d * l.u))
                }
                for (i in 1 until 10) drawLine(darker(roof, 0.15f), l.o(x, base - h - d + h * 0.3f + i * d / 10f), l.o(x + w, base - h - d + h * 0.3f + i * d / 10f), strokeWidth = 0.25f * l.u)
                val arch = Path().apply {
                    moveTo(l.x(x), l.y(base))
                    lineTo(l.x(x), l.y(base - h * 0.45f))
                    cubicTo(l.x(x), l.y(base - h * 1.05f), l.x(x + w), l.y(base - h * 1.05f), l.x(x + w), l.y(base - h * 0.45f))
                    lineTo(l.x(x + w), l.y(base))
                    close()
                }
                drawPath(arch, wall)
                drawPath(arch, darker(wall, 0.3f), style = Stroke(0.5f * l.u))
            }
        }
        return front
    }

    /** A row (or rows) of windows across a front wall, leaving room for a door at [skipCenter]. */
    fun DrawScope.windows(l: Lot, front: Rect, cols: Int, rows: Int = 1, lit: Boolean = false, skipCenter: Boolean = false, color: Color = Glass) {
        val u = l.u
        val ww = 2.6f * u
        val wh = 2.2f * u
        val gapX = (front.width - cols * ww) / (cols + 1)
        val rowH = front.height / (rows + 0.6f)
        for (r in 0 until rows) for (c in 0 until cols) {
            if (skipCenter && r == rows - 1 && cols % 2 == 1 && c == cols / 2) continue
            val left = front.left + gapX + c * (ww + gapX)
            val top = front.top + rowH * (r + 0.45f)
            drawRect(if (lit && (r + c) % 3 == 0) GlassLit else color, Offset(left, top), Size(ww, wh))
            drawRect(lighter(color, 0.5f).copy(alpha = 0.5f), Offset(left, top), Size(ww, 0.4f * u))
        }
    }

    fun DrawScope.door(l: Lot, front: Rect, cxPx: Float = front.center.x, w: Float = 3.2f, h: Float = 5.5f, color: Color = Color(0xFF3A3530)) {
        drawRect(color, Offset(cxPx - w * l.u / 2, front.bottom - h * l.u), Size(w * l.u, h * l.u))
    }

    /** A GP tent seen from the front corner: a pentagon end and two roof slopes running back. */
    fun DrawScope.tent(l: Lot, x: Float, base: Float, w: Float, d: Float, h: Float, cloth: Color = CanvasWall) {
        shadow(l, x, base, w, d, h * 0.8f)
        val eave = base - h * 0.45f
        val ridge = base - h
        val leftSlope = Path().apply {
            moveTo(l.x(x), l.y(eave)); lineTo(l.x(x + w / 2), l.y(ridge)); lineTo(l.x(x + w / 2), l.y(ridge - d)); lineTo(l.x(x), l.y(eave - d)); close()
        }
        val rightSlope = Path().apply {
            moveTo(l.x(x + w / 2), l.y(ridge)); lineTo(l.x(x + w), l.y(eave)); lineTo(l.x(x + w), l.y(eave - d)); lineTo(l.x(x + w / 2), l.y(ridge - d)); close()
        }
        drawPath(leftSlope, lighter(cloth, 0.2f))
        drawPath(rightSlope, darker(cloth, 0.12f))
        val end = Path().apply {
            moveTo(l.x(x), l.y(base)); lineTo(l.x(x), l.y(eave)); lineTo(l.x(x + w / 2), l.y(ridge)); lineTo(l.x(x + w), l.y(eave)); lineTo(l.x(x + w), l.y(base)); close()
        }
        drawPath(end, cloth)
        drawPath(end, darker(cloth, 0.35f), style = Stroke(0.4f * l.u))
        // Door flap
        drawPath(Path().apply {
            moveTo(l.x(x + w / 2 - 2.2f), l.y(base)); lineTo(l.x(x + w / 2), l.y(base - h * 0.62f)); lineTo(l.x(x + w / 2 + 2.2f), l.y(base)); close()
        }, darker(cloth, 0.4f))
        // Guy lines
        drawLine(darker(cloth, 0.5f), l.o(x, eave), l.o(x - 2f, base), strokeWidth = 0.25f * l.u)
        drawLine(darker(cloth, 0.5f), l.o(x + w, eave), l.o(x + w + 2f, base), strokeWidth = 0.25f * l.u)
    }

    fun DrawScope.tree(cx: Float, cy: Float, r: Float, u: Float, autumn: Boolean = false) {
        drawOval(Shadow.copy(alpha = 0.25f), Offset(cx - r * 0.2f * u, cy + r * 0.2f * u), Size(r * 2.1f * u, r * 0.9f * u))
        drawRect(Color(0xFF5A4630), Offset(cx - 0.4f * u, cy - r * 0.2f * u), Size(0.8f * u, r * 0.7f * u))
        val leaf = if (autumn) Color(0xFF9A6A2E) else Color(0xFF3F5130)
        drawCircle(leaf, r * u, Offset(cx, cy - r * 0.9f * u))
        drawCircle(lighter(leaf, 0.16f), r * 0.62f * u, Offset(cx - r * 0.25f * u, cy - r * 1.15f * u))
    }

    fun DrawScope.flag(l: Lot, x: Float, base: Float, h: Float, col: Color, trim: Color? = null) {
        drawLine(Color(0xFFDADADA), l.o(x, base), l.o(x, base - h), strokeWidth = 0.6f * l.u)
        drawRect(col, l.o(x, base - h), Size(6f * l.u, 3.6f * l.u))
        if (trim != null) drawRect(trim, l.o(x, base - h + 1.4f), Size(6f * l.u, 0.9f * l.u))
        drawCircle(Color(0xFFE0C060), 0.5f * l.u, l.o(x, base - h))
    }

    private fun DrawScope.crate(l: Lot, x: Float, base: Float, s: Float, col: Color = Color(0xFF7A6A3C)) {
        box(l, x, base, s, s * 0.6f, s * 0.8f, col, lighter(col, 0.15f), shadow = false)
        drawRect(darker(col, 0.4f), l.o(x, base - s * 0.8f), Size(s * l.u, s * 0.8f * l.u), style = Stroke(0.25f * l.u))
    }

    private fun DrawScope.vehicle(l: Lot, x: Float, base: Float, len: Float, body: Color, cab: Color = Glass) {
        box(l, x, base, len, 2.6f, 2.2f, body, lighter(body, 0.15f))
        drawRect(cab, l.o(x + len - 2.2f, base - 2f), Size(1.6f * l.u, 1.2f * l.u))
        drawCircle(Ink, 0.6f * l.u, l.o(x + 1.2f, base))
        drawCircle(Ink, 0.6f * l.u, l.o(x + len - 1.2f, base))
    }

    private fun DrawScope.sandbags(l: Lot, x: Float, base: Float, count: Int) {
        for (i in 0 until count) drawOval(Color(0xFFB8A57A), l.o(x + i * 3.4f, base - 2f), Size(3.8f * l.u, 2.2f * l.u))
        for (i in 0 until count - 1) drawOval(Color(0xFFC7B58A), l.o(x + 1.7f + i * 3.4f, base - 3.6f), Size(3.8f * l.u, 2.2f * l.u))
    }

    private fun DrawScope.redCross(cx: Float, cy: Float, s: Float, u: Float) {
        drawRect(Color.White, Offset(cx - s * u, cy - s * u), Size(2 * s * u, 2 * s * u))
        drawRect(Red, Offset(cx - s * 0.28f * u, cy - s * 0.8f * u), Size(s * 0.56f * u, s * 1.6f * u))
        drawRect(Red, Offset(cx - s * 0.8f * u, cy - s * 0.28f * u), Size(s * 1.6f * u, s * 0.56f * u))
    }

    private fun DrawScope.paved(l: Lot, x: Float, y: Float, w: Float, h: Float, stripes: Int = 0) {
        drawRect(Color(0xFF6A6C6B), l.o(x, y), Size(w * l.u, h * l.u))
        for (i in 1..stripes) drawLine(Color(0xFFE6E2D2), l.o(x + i * w / (stripes + 1), y + 0.5f), l.o(x + i * w / (stripes + 1), y + h * 0.55f), strokeWidth = 0.3f * l.u)
    }

    // ---------------- Facilities ----------------

    fun DrawScope.drawFacility(type: FacilityType, rect: Rect, u: Float, stage: Int) {
        val l = Lot(rect, u)
        when (type) {
            FacilityType.COMMAND_HQ -> hq(l, stage)
            FacilityType.RECON_ARRAY -> intel(l, stage)
            FacilityType.BARRACKS -> barracks(l, stage)
            FacilityType.DFAC -> dfac(l, stage)
            FacilityType.AID_STATION -> aid(l, stage)
            FacilityType.ARMORY_WORKSHOP -> armory(l, stage)
            FacilityType.LOGISTICS_DEPOT -> quartermaster(l, stage)
            FacilityType.TRAINING_RANGE -> range(l, stage)
            FacilityType.AIRBORNE_RANGER_CENTER -> abnRanger(l, stage)
            FacilityType.SF_COMPOUND -> sfCompound(l, stage)
            FacilityType.AIRFIELD -> airfield(l, stage)
            FacilityType.MAIN_GATE -> gate(l, stage)
        }
    }

    private fun DrawScope.hq(l: Lot, s: Int) {
        val b = l.base
        val blue = Color(0xFF2F5FA6)
        when (s) {
            1 -> {
                tent(l, 8f, b, 30f, 14f, 15f)
                flag(l, 46f, b, 22f, blue)
                crate(l, 41f, b, 3f)
            }
            2 -> {
                val f = box(l, 7f, b, 36f, 12f, 13f, WoodWall, WoodRoof, Roof.GABLE)
                windows(l, f, 5, skipCenter = true); door(l, f)
                flag(l, 49f, b, 24f, blue)
            }
            3 -> {
                paved(l, 44f, b - 15f, 12f, 13f, stripes = 2)
                vehicle(l, 46f, b - 4f, 6f, Color(0xFF55603F))
                val f = box(l, 4f, b, 38f, 13f, 18f, Brick, Slate)
                windows(l, f, 5, rows = 2, skipCenter = true)
                // Columned entry
                val p = box(l, 17f, b, 12f, 3f, 12f, White, Concrete, shadow = false)
                for (i in 0..3) drawRect(Color(0xFFBDB8AA), Offset(p.left + (1.4f + i * 3f) * l.u, p.top + 1f * l.u), Size(0.8f * l.u, p.height - 1f * l.u))
                door(l, f)
                flag(l, 23f, b - 31f, 9f, blue)
            }
            else -> {
                box(l, 1f, b, 12f, 11f, 13f, Brick, Slate).let { windows(l, it, 2, rows = 2) }
                box(l, 45f, b, 12f, 11f, 13f, Brick, Slate).let { windows(l, it, 2, rows = 2) }
                val f = box(l, 12f, b, 34f, 14f, 23f, Brick, Slate)
                windows(l, f, 5, rows = 3, lit = true, skipCenter = true)
                val p = box(l, 22f, b, 14f, 3f, 14f, White, Concrete, shadow = false)
                for (i in 0..4) drawRect(Color(0xFFBDB8AA), Offset(p.left + (1.2f + i * 3f) * l.u, p.top + 1f * l.u), Size(0.8f * l.u, p.height - 1f * l.u))
                door(l, f)
                flag(l, 29f, b - 37f, 9f, blue)
            }
        }
    }

    private fun DrawScope.radome(l: Lot, cx: Float, bottom: Float, r: Float) {
        drawOval(Shadow.copy(alpha = 0.25f), l.o(cx - r * 0.6f, bottom - r * 0.3f), Size(r * 2.4f * l.u, r * 0.7f * l.u))
        drawCircle(Color(0xFFCFCCC4), r * l.u, l.o(cx, bottom - r))
        drawCircle(Color(0xFFF3F1EC), r * 0.82f * l.u, l.o(cx - r * 0.14f, bottom - r * 1.14f))
        drawLine(Color(0xFFD8D5CE), l.o(cx - r, bottom - r), l.o(cx + r, bottom - r), strokeWidth = 0.25f * l.u)
        drawArc(Color(0xFFD8D5CE), 180f, 180f, false, l.o(cx - r * 0.5f, bottom - 2 * r), Size(r * l.u, 2 * r * l.u), style = Stroke(0.25f * l.u))
    }

    private fun DrawScope.mast(l: Lot, x: Float, base: Float, h: Float) {
        drawLine(Steel, l.o(x - 1.2f, base), l.o(x, base - h), strokeWidth = 0.4f * l.u)
        drawLine(Steel, l.o(x + 1.2f, base), l.o(x, base - h), strokeWidth = 0.4f * l.u)
        var y = base - 3f
        while (y > base - h + 2f) { drawLine(Steel, l.o(x - 1.0f * (y - (base - h)) / h, y), l.o(x + 1.0f * (y - (base - h)) / h, y), strokeWidth = 0.25f * l.u); y -= 3f }
        drawLine(Color(0xFFC0322B), l.o(x, base - h), l.o(x, base - h - 1.2f), strokeWidth = 0.6f * l.u)
        drawLine(Color(0xFFDADADA), l.o(x - 2.5f, base - h * 0.8f), l.o(x + 2.5f, base - h * 0.8f), strokeWidth = 0.4f * l.u)
    }

    private fun DrawScope.intel(l: Lot, s: Int) {
        val b = l.base
        when (s) {
            1 -> {
                tent(l, 6f, b, 24f, 11f, 12f)
                // Field dish on a tripod
                drawLine(Steel, l.o(41f, b), l.o(44f, b - 8f), strokeWidth = 0.5f * l.u)
                drawLine(Steel, l.o(47f, b), l.o(44f, b - 8f), strokeWidth = 0.5f * l.u)
                drawOval(Color(0xFFE6E4DE), l.o(39f, b - 15f), Size(10f * l.u, 8f * l.u))
                drawOval(Color(0xFFB8B5AD), l.o(41f, b - 13f), Size(6f * l.u, 4f * l.u))
            }
            2 -> {
                val f = box(l, 4f, b, 30f, 11f, 11f, WoodWall, WoodRoof, Roof.GABLE)
                windows(l, f, 3, skipCenter = true); door(l, f)
                mast(l, 46f, b, 34f)
            }
            3 -> {
                val f = box(l, 3f, b, 30f, 12f, 10f, Concrete, Tar)
                windows(l, f, 3, skipCenter = true, color = Color(0xFF2C3540)); door(l, f)
                box(l, 36f, b, 16f, 9f, 5f, Concrete, Tar)
                radome(l, 44f, b - 13.5f, 7.5f)
                mast(l, 8f, b - 22f, 18f)
            }
            else -> {
                val f = box(l, 2f, b, 32f, 13f, 12f, Concrete, Tar)
                windows(l, f, 3, skipCenter = true, color = Color(0xFF2C3540)); door(l, f)
                radome(l, 24f, b - 25f, 5f)
                box(l, 37f, b, 17f, 9f, 6f, Concrete, Tar)
                radome(l, 45.5f, b - 14.5f, 8f)
                mast(l, 8f, b - 25f, 20f)
            }
        }
    }

    private fun DrawScope.barracks(l: Lot, s: Int) {
        val b = l.base
        when (s) {
            1 -> {
                tent(l, 3f, b - 4f, 22f, 11f, 12f)
                tent(l, 31f, b, 22f, 11f, 12f)
            }
            2 -> {
                val f = box(l, 3f, b, 52f, 11f, 11f, WoodWall, WoodRoof, Roof.GABLE)
                windows(l, f, 7, lit = true, skipCenter = true); door(l, f)
            }
            3 -> {
                val f = box(l, 3f, b, 52f, 12f, 17f, Tan, Slate)
                windows(l, f, 7, rows = 2, lit = true, skipCenter = true); door(l, f)
                box(l, 10f, b - 29f, 4f, 2.5f, 2f, Steel, lighter(Steel), shadow = false)
                box(l, 40f, b - 29f, 4f, 2.5f, 2f, Steel, lighter(Steel), shadow = false)
            }
            else -> {
                box(l, 8f, b - 16f, 44f, 9f, 13f, darker(Tan, 0.08f), Slate).let { windows(l, it, 6, rows = 2, lit = true) }
                val f = box(l, 2f, b, 54f, 12f, 17f, Tan, Slate)
                windows(l, f, 7, rows = 2, lit = true, skipCenter = true); door(l, f)
                tree(l.x(1.5f), l.y(b - 15f), 2.4f, l.u)
            }
        }
    }

    private fun DrawScope.smoke(l: Lot, x: Float, y: Float) {
        drawCircle(Color.White.copy(alpha = 0.45f), 1.6f * l.u, l.o(x, y))
        drawCircle(Color.White.copy(alpha = 0.3f), 2.2f * l.u, l.o(x + 1.5f, y - 3f))
    }

    private fun DrawScope.dfac(l: Lot, s: Int) {
        val b = l.base
        when (s) {
            1 -> {
                tent(l, 4f, b, 30f, 13f, 13f)
                box(l, 39f, b, 13f, 6f, 7f, Color(0xFF6E7A5A), Color(0xFF7D8968))
                drawRect(Steel, l.o(48f, b - 16f), Size(1.4f * l.u, 3.5f * l.u))
                smoke(l, 49f, b - 18f)
            }
            2 -> {
                val f = box(l, 4f, b, 46f, 12f, 11f, WoodWall, WoodRoof, Roof.GABLE)
                windows(l, f, 5, skipCenter = true); door(l, f, w = 4.5f)
                drawRect(Steel, l.o(40f, b - 27f), Size(1.6f * l.u, 6f * l.u))
                smoke(l, 41f, b - 29f)
            }
            else -> {
                val wide = s == 4
                val metal = Color(0xFF6F7F8C)
                val f = box(l, 2f, b, if (wide) 46f else 42f, 14f, 11f, Color(0xFFC9C6BC), metal)
                windows(l, f, if (wide) 7 else 5, skipCenter = true); door(l, f, w = 5f)
                for (i in 0..2) box(l, 9f + i * 12f, b - 15f, 3f, 2.5f, 2.2f, Steel, lighter(Steel), shadow = false)
                // Loading dock and a supply truck
                box(l, if (wide) 49f else 45f, b, 8f, 9f, 4f, Concrete, Tar)
                vehicle(l, if (wide) 49f else 45f, b - 9f, 7f, Color(0xFF55603F))
                if (wide) {
                    // Covered patio
                    drawRect(Color(0xFFA33A2F), l.o(4f, b - 5f), Size(14f * l.u, 1.2f * l.u))
                    for (i in 0..2) drawRect(Color(0xFF7A5A3A), l.o(5f + i * 4.5f, b + 0.6f), Size(3f * l.u, 0.9f * l.u))
                }
            }
        }
    }

    private fun DrawScope.ambulance(l: Lot, x: Float, base: Float) {
        box(l, x, base, 7f, 3f, 3.2f, Color(0xFFF2F0EA), Color(0xFFDCD9D2))
        drawRect(Red, l.o(x, base - 1.8f), Size(7f * l.u, 0.6f * l.u))
        drawRect(Glass, l.o(x + 5.2f, base - 3f), Size(1.4f * l.u, 1.1f * l.u))
        drawCircle(Ink, 0.6f * l.u, l.o(x + 1.3f, base)); drawCircle(Ink, 0.6f * l.u, l.o(x + 5.7f, base))
    }

    private fun DrawScope.aid(l: Lot, s: Int) {
        val b = l.base
        when (s) {
            1 -> {
                tent(l, 8f, b, 28f, 13f, 13f, cloth = Color(0xFFD9D4C2))
                redCross(l.x(22f), l.y(b - 15f), 2.6f, l.u)
                ambulance(l, 42f, b)
            }
            2 -> {
                val f = box(l, 5f, b, 34f, 12f, 11f, Color(0xFFE6E0D2), WoodRoof, Roof.GABLE)
                windows(l, f, 4); door(l, f)
                redCross(l.x(22f), l.y(b - 17f), 2.8f, l.u)
                ambulance(l, 43f, b)
            }
            else -> {
                val h = if (s == 4) 17f else 12f
                val f = box(l, 3f, b, 36f, 13f, h, White, Color(0xFF8E9296))
                windows(l, f, 5, rows = if (s == 4) 2 else 1, skipCenter = true); door(l, f, w = 4.5f, color = Color(0xFF4E6A80))
                redCross(l.x(21f), l.y(b - h - 6.5f), 3.6f, l.u)
                // Ambulance bay
                box(l, 41f, b, 15f, 9f, 8f, Concrete, Color(0xFFB0B3B6))
                drawRect(Color(0xFF2E3338), l.o(42f, b - 7f), Size(13f * l.u, 7f * l.u))
                ambulance(l, 45f, b + 0.5f)
            }
        }
    }

    private fun DrawScope.armory(l: Lot, s: Int) {
        val b = l.base
        val stripe = Color(0xFFA33A2F)
        when (s) {
            1 -> {
                tent(l, 10f, b, 26f, 12f, 12f, cloth = Color(0xFF8E8A62))
                sandbags(l, 4f, b + 1f, 3); sandbags(l, 38f, b + 1f, 4)
            }
            2 -> {
                val f = box(l, 6f, b, 34f, 11f, 11f, WoodWall, WoodRoof, Roof.GABLE)
                door(l, f, w = 5f, color = Color(0xFF3A3A3A))
                drawRect(stripe, Offset(f.left, f.top + 1.5f * l.u), Size(f.width, 1.1f * l.u))
                sandbags(l, 41f, b + 1f, 4)
            }
            else -> {
                val gray = Color(0xFFA2A6A4)
                val f = box(l, 3f, b, if (s == 4) 34f else 30f, 12f, 13f, gray, Tar)
                drawRect(stripe, Offset(f.left, f.top + 1.5f * l.u), Size(f.width, 1.1f * l.u))
                // Heavy steel door with a keypad
                drawRect(Color(0xFF4A4F54), Offset(f.center.x - 4f * l.u, f.bottom - 8f * l.u), Size(8f * l.u, 8f * l.u))
                drawLine(Color(0xFF6E7378), Offset(f.center.x, f.bottom - 8f * l.u), Offset(f.center.x, f.bottom), strokeWidth = 0.3f * l.u)
                drawRect(Color(0xFFE0C060), Offset(f.center.x + 4.6f * l.u, f.bottom - 5f * l.u), Size(0.9f * l.u, 1.2f * l.u))
                // Fenced yard
                val yx = if (s == 4) 39f else 36f
                drawRect(Color(0xFF8A8D7E), l.o(yx, b - 18f), Size((l.w - yx - 2f) * l.u, 18f * l.u))
                if (s == 4) {
                    // Earth-covered bunker
                    drawArc(Color(0xFF6F7650), 180f, 180f, true, l.o(yx + 1f, b - 14f), Size((l.w - yx - 4f) * l.u, 16f * l.u))
                    drawRect(Concrete, l.o(yx + 5f, b - 6f), Size(6f * l.u, 6f * l.u))
                    drawRect(Color(0xFF4A4F54), l.o(yx + 6.5f, b - 4.5f), Size(3f * l.u, 4.5f * l.u))
                } else {
                    crate(l, yx + 2f, b - 4f, 4f, Color(0xFF5E6B45)); crate(l, yx + 8f, b - 4f, 4f, Color(0xFF5E6B45)); crate(l, yx + 4f, b - 10f, 4f, Color(0xFF5E6B45))
                }
                drawRect(Color(0xFFB8BDC2), l.o(yx, b - 18f), Size((l.w - yx - 2f) * l.u, 18f * l.u),
                    style = Stroke(0.5f * l.u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.7f * l.u, 0.5f * l.u))))
            }
        }
    }

    private fun DrawScope.container(l: Lot, x: Float, base: Float, col: Color) {
        val f = box(l, x, base, 12f, 4f, 4.4f, col, lighter(col, 0.12f))
        for (i in 1..7) drawLine(darker(col, 0.25f), Offset(f.left + i * f.width / 8, f.top), Offset(f.left + i * f.width / 8, f.bottom), strokeWidth = 0.25f * l.u)
    }

    private fun DrawScope.quonset(l: Lot, x: Float, base: Float, w: Float) {
        val f = box(l, x, base, w, 16f, 12f, Color(0xFFB2A27A), Color(0xFF9A8C68), Roof.ARCH)
        // Roll-up door
        val dw = w * 0.42f
        drawRect(Color(0xFF8B8F8C), Offset(f.center.x - dw * l.u / 2, f.bottom - 7f * l.u), Size(dw * l.u, 7f * l.u))
        for (i in 1..5) drawLine(Color(0xFF6E7270), Offset(f.center.x - dw * l.u / 2, f.bottom - i * 1.2f * l.u), Offset(f.center.x + dw * l.u / 2, f.bottom - i * 1.2f * l.u), strokeWidth = 0.25f * l.u)
    }

    private fun DrawScope.quartermaster(l: Lot, s: Int) {
        val b = l.base
        when (s) {
            1 -> {
                tent(l, 4f, b, 22f, 11f, 11f, cloth = Color(0xFF7E8A5E))
                for ((dx, dy) in listOf(32f to 0f, 37f to 0f, 42f to 0f, 34.5f to -4f, 39.5f to -4f, 37f to -8f)) crate(l, dx, b + dy, 4.6f)
            }
            2 -> {
                val f = box(l, 3f, b, 32f, 12f, 11f, WoodWall, WoodRoof, Roof.GABLE)
                door(l, f, w = 8f, h = 7f)
                for ((dx, dy) in listOf(39f to 0f, 44f to 0f, 49f to 0f, 41.5f to -4f, 46.5f to -4f)) crate(l, dx, b + dy, 4.6f)
            }
            3 -> {
                quonset(l, 3f, b, 30f)
                container(l, 38f, b - 8f, Color(0xFF8E4A2E))
                container(l, 38f, b, Color(0xFF4E6A4A))
            }
            else -> {
                quonset(l, 1f, b, 24f)
                quonset(l, 28f, b - 3f, 20f)
                container(l, 44f, b + 0.5f, Color(0xFF8E4A2E))
                // Forklift
                box(l, 50f, b - 7f, 4f, 2.5f, 3f, Color(0xFFE0B84A), Color(0xFFE8C860), shadow = false)
            }
        }
    }

    private fun DrawScope.range(l: Lot, s: Int) {
        val b = l.base
        val lanes = when (s) { 1 -> 2; 2 -> 3; 3 -> 4; else -> 5 }
        // Impact berm along the back
        drawRoundRect(Color(0xFF7E6A48), l.o(2f, 4f), Size((l.w - 4f) * l.u, 9f * l.u), androidx.compose.ui.geometry.CornerRadius(4f * l.u))
        drawRoundRect(Color(0xFF9A8460), l.o(3f, 4f), Size((l.w - 6f) * l.u, 4.5f * l.u), androidx.compose.ui.geometry.CornerRadius(3f * l.u))
        // Lanes
        val laneW = (l.w - 8f) / lanes
        for (i in 0..lanes) drawLine(Color(0xFFE3DDC8).copy(alpha = 0.6f), l.o(4f + i * laneW, 13f), l.o(4f + i * laneW, b - 6f), strokeWidth = 0.3f * l.u)
        for (i in 0 until lanes) {
            val tx = 4f + laneW * (i + 0.5f)
            drawLine(Color(0xFF5A4630), l.o(tx, 15f), l.o(tx, 11f), strokeWidth = 0.5f * l.u)
            drawCircle(Color(0xFFEDE3C6), 2.2f * l.u, l.o(tx, 10.5f))
            drawCircle(Color(0xFFA33A2F), 0.8f * l.u, l.o(tx, 10.5f))
        }
        // Firing line
        drawRect(Color(0xFFB8AE92), l.o(3f, b - 6f), Size((l.w - 6f) * l.u, 2f * l.u))
        if (s >= 4) {
            // Covered firing line
            box(l, 3f, b - 3f, l.w - 6f, 4f, 5f, Color(0xFF7A5A3A).copy(alpha = 0f), Color(0xFF6E7378), shadow = false)
            for (i in 0..lanes) drawRect(Steel, l.o(3f + i * (l.w - 6f) / lanes, b - 8f), Size(0.6f * l.u, 5f * l.u))
        }
        if (s >= 3) {
            // Range control tower
            box(l, l.w - 11f, b + 1f, 7f, 5f, 9f, WoodWall, WoodRoof).let { drawRect(Glass, Offset(it.left + 1f * l.u, it.top + 1f * l.u), Size(5f * l.u, 2.5f * l.u)) }
            drawRect(Red, l.o(l.w - 8f, b - 20f), Size(3f * l.u, 2f * l.u))
            drawLine(Color(0xFFDADADA), l.o(l.w - 8f, b - 15f), l.o(l.w - 8f, b - 20f), strokeWidth = 0.4f * l.u)
        } else {
            sandbags(l, l.w - 16f, b + 1f, 4)
        }
    }

    private fun DrawScope.abnRanger(l: Lot, s: Int) {
        val b = l.base
        // Sawdust landing area
        drawOval(Color(0xFFC2AE82), l.o(16f, b - 10f), Size(36f * l.u, 9f * l.u))
        // 34-foot tower: four legs, a cab on top
        val tx = 38f
        val th = if (s == 1) 26f else 31f
        drawLine(Steel, l.o(tx, b - 3f), l.o(tx, b - th), strokeWidth = 0.8f * l.u)
        drawLine(Steel, l.o(tx + 9f, b - 3f), l.o(tx + 9f, b - th), strokeWidth = 0.8f * l.u)
        drawLine(darker(Steel), l.o(tx + 2f, b - 5f), l.o(tx + 2f, b - th - 2f), strokeWidth = 0.6f * l.u)
        drawLine(darker(Steel), l.o(tx + 11f, b - 5f), l.o(tx + 11f, b - th - 2f), strokeWidth = 0.6f * l.u)
        var y = b - 8f
        while (y > b - th + 2f) { drawLine(Steel, l.o(tx, y), l.o(tx + 9f, y - 3.5f), strokeWidth = 0.35f * l.u); drawLine(Steel, l.o(tx, y - 3.5f), l.o(tx + 9f, y), strokeWidth = 0.35f * l.u); y -= 5f }
        box(l, tx - 1f, b - th, 13f, 4f, 5f, Color(0xFF6E7378), Color(0xFF878C90), shadow = false).let {
            drawRect(Color(0xFF2A2E33), Offset(it.left + 4f * l.u, it.top + 0.8f * l.u), Size(4f * l.u, it.height - 0.8f * l.u))
        }
        // Cable run off the tower to the mound
        drawLine(Color(0xFFDADADA), l.o(tx + 6f, b - th - 2f), l.o(14f, b - 12f), strokeWidth = 0.4f * l.u)
        drawOval(Color(0xFF8C7650), l.o(9f, b - 13f), Size(10f * l.u, 4f * l.u))
        if (s >= 2) {
            val py = b - th - 4f
            drawLine(Color(0xFFDADADA), l.o(tx + 11f, py), l.o(tx + 11f, py - 7f), strokeWidth = 0.5f * l.u)
            drawRect(Color(0xFF1A1A1A), l.o(tx + 11f, py - 7f), Size(6f * l.u, 3.6f * l.u))
            drawRect(Color(0xFFE0B84A), l.o(tx + 11f, py - 5.6f), Size(6f * l.u, 0.9f * l.u))
        }
        if (s >= 3) {
            // Rappel tower
            val f = box(l, 3f, b, 8f, 5f, 30f, WoodWall, WoodRoof)
            drawLine(Color(0xFFEDE3C6), Offset(f.left + 2f * l.u, f.top + 1f * l.u), Offset(f.left + 2f * l.u, f.bottom), strokeWidth = 0.35f * l.u)
            drawLine(Color(0xFFEDE3C6), Offset(f.left + 5.5f * l.u, f.top + 1f * l.u), Offset(f.left + 5.5f * l.u, f.bottom), strokeWidth = 0.35f * l.u)
        }
        if (s >= 4) {
            // Mock aircraft door trainer
            val f = box(l, 15f, b + 1f, 16f, 5f, 5f, Color(0xFF7D8A6A), Color(0xFF8E9B7A))
            drawRect(Color(0xFF2A2E33), Offset(f.left + 10f * l.u, f.top + 0.8f * l.u), Size(2.6f * l.u, f.height - 0.8f * l.u))
            for (i in 0..2) drawRect(Glass, Offset(f.left + (1.5f + i * 2.8f) * l.u, f.top + 1.2f * l.u), Size(1.4f * l.u, 1.2f * l.u))
        }
    }

    private fun DrawScope.sfCompound(l: Lot, s: Int) {
        val b = l.base
        val wallCol = if (s >= 3) Color(0xFFC7BFAE) else Color(0xFF9DA39A)
        // Gravel yard inside the compound
        drawRect(Color(0xFF7E7B66), l.o(3f, 3f), Size((l.w - 6f) * l.u, (b - 1f) * l.u))
        // Back and side walls
        if (s >= 3) {
            drawRect(darker(wallCol, 0.1f), l.o(3f, 2f), Size((l.w - 6f) * l.u, 1.6f * l.u))
            drawRect(darker(wallCol, 0.1f), l.o(3f, 2f), Size(1.6f * l.u, (b + 1f) * l.u))
            drawRect(darker(wallCol, 0.1f), l.o(l.w - 4.6f, 2f), Size(1.6f * l.u, (b + 1f) * l.u))
        } else {
            drawRect(wallCol, l.o(3f, 2f), Size((l.w - 6f) * l.u, (b + 1f) * l.u),
                style = Stroke(0.6f * l.u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.2f * l.u, 1f * l.u))))
        }
        val black = Color(SF_PATCH_BLACK)
        val red = Color(SF_PATCH_RED)
        when (s) {
            1 -> {
                tent(l, 14f, b - 3f, 24f, 11f, 12f, cloth = Color(0xFF6E7650))
                tent(l, 44f, b - 3f, 24f, 11f, 12f, cloth = Color(0xFF6E7650))
            }
            2 -> {
                box(l, 12f, b - 3f, 30f, 11f, 11f, WoodWall, WoodRoof, Roof.GABLE).let { windows(l, it, 3, skipCenter = true); door(l, it) }
                box(l, 48f, b - 3f, 30f, 11f, 11f, WoodWall, WoodRoof, Roof.GABLE).let { windows(l, it, 3, skipCenter = true); door(l, it) }
            }
            else -> {
                val stone = Color(0xFF8C8778)
                box(l, 12f, b - 3f, 34f, 12f, 14f, stone, Slate).let { windows(l, it, 4, rows = 2, skipCenter = true, color = Color(0xFF2A3038)); door(l, it) }
                box(l, 52f, b - 3f, 28f, 11f, 11f, stone, Slate).let { windows(l, it, 3, skipCenter = true, color = Color(0xFF2A3038)); door(l, it) }
                if (s == 4) {
                    // Dive tank
                    drawRect(Concrete, l.o(85f, b - 18f), Size(26f * l.u, 13f * l.u))
                    drawRect(Color(0xFF2F6F8F), l.o(86.5f, b - 16.5f), Size(23f * l.u, 10f * l.u))
                    drawRect(Color(0xFF6FB3D0).copy(alpha = 0.5f), l.o(86.5f, b - 16.5f), Size(23f * l.u, 2f * l.u))
                } else {
                    box(l, 86f, b - 3f, 22f, 9f, 8f, stone, Slate).let { windows(l, it, 2, color = Color(0xFF2A3038)) }
                }
            }
        }
        // Front wall with its own gate
        if (s >= 3) {
            box(l, 3f, b + 2f, 50f, 1.6f, 3f, wallCol, lighter(wallCol, 0.1f), shadow = false)
            box(l, 63f, b + 2f, l.w - 66f, 1.6f, 3f, wallCol, lighter(wallCol, 0.1f), shadow = false)
            drawRect(Color(0xFF3A3530), l.o(53f, b - 1f), Size(10f * l.u, 3f * l.u))
        }
        // Black and red pennant
        drawLine(Color(0xFFDADADA), l.o(7f, b), l.o(7f, b - 27f), strokeWidth = 0.6f * l.u)
        drawRect(black, l.o(7f, b - 27f), Size(7f * l.u, 4f * l.u))
        drawRect(red, l.o(7f, b - 25.4f), Size(7f * l.u, 1f * l.u))
    }

    private fun DrawScope.helicopter(l: Lot, cx: Float, cy: Float) {
        drawOval(Shadow.copy(alpha = 0.3f), l.o(cx - 5f, cy + 1f), Size(12f * l.u, 3f * l.u))
        drawOval(Color(0xFF4F5A3C), l.o(cx - 5f, cy - 2.5f), Size(9f * l.u, 4f * l.u))
        drawRect(Color(0xFF4F5A3C), l.o(cx + 3f, cy - 1.3f), Size(7f * l.u, 1.2f * l.u))
        drawRect(Color(0xFF4F5A3C), l.o(cx + 9f, cy - 3f), Size(1f * l.u, 3f * l.u))
        drawOval(Glass, l.o(cx - 4.6f, cy - 1.8f), Size(3f * l.u, 2f * l.u))
        drawLine(Color(0xFF2A2E25), l.o(cx - 9f, cy - 3.4f), l.o(cx + 7f, cy - 2.6f), strokeWidth = 0.4f * l.u)
    }

    private fun DrawScope.airfield(l: Lot, s: Int) {
        val b = l.base
        val stripY = 4f
        val stripW = l.w - 56f
        // Runway
        if (s == 1) {
            drawRect(Color(0xFF9A8458), l.o(4f, stripY), Size(stripW * l.u, 13f * l.u))
            drawRect(Color(0xFF86734B), l.o(4f, stripY + 3f), Size(stripW * l.u, 1f * l.u))
            drawRect(Color(0xFF86734B), l.o(4f, stripY + 9f), Size(stripW * l.u, 1f * l.u))
        } else {
            drawRect(Color(0xFF4F5254), l.o(4f, stripY), Size(stripW * l.u, 13f * l.u))
            drawLine(Color(0xFFEDEBE0), l.o(14f, stripY + 6.5f), l.o(4f + stripW - 10f, stripY + 6.5f), strokeWidth = 0.7f * l.u,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * l.u, 4f * l.u)))
            for (i in 0..3) {
                drawRect(Color(0xFFEDEBE0), l.o(6f, stripY + 2f + i * 2.6f), Size(4f * l.u, 1f * l.u))
                drawRect(Color(0xFFEDEBE0), l.o(4f + stripW - 6f, stripY + 2f + i * 2.6f), Size(4f * l.u, 1f * l.u))
            }
        }
        // Helipad
        val pad = l.o(l.w - 28f, stripY + 13f)
        val padR = if (s >= 3) 13f else 10f
        drawCircle(if (s == 1) Color(0xFF9A8458) else Color(0xFF4F5254), padR * l.u, pad)
        if (s >= 2) {
            drawCircle(Color(0xFFEDEBE0), (padR - 1.8f) * l.u, pad, style = Stroke(0.6f * l.u))
            val h = 3.6f * l.u
            drawLine(Color(0xFFEDEBE0), Offset(pad.x - h, pad.y - h), Offset(pad.x - h, pad.y + h), strokeWidth = 1f * l.u)
            drawLine(Color(0xFFEDEBE0), Offset(pad.x + h, pad.y - h), Offset(pad.x + h, pad.y + h), strokeWidth = 1f * l.u)
            drawLine(Color(0xFFEDEBE0), Offset(pad.x - h, pad.y), Offset(pad.x + h, pad.y), strokeWidth = 1f * l.u)
        }
        // Windsock
        drawLine(Color(0xFFDADADA), l.o(l.w - 8f, b), l.o(l.w - 8f, b - 10f), strokeWidth = 0.5f * l.u)
        drawPath(Path().apply { moveTo(l.x(l.w - 8f), l.y(b - 10f)); lineTo(l.x(l.w - 3f), l.y(b - 9f)); lineTo(l.x(l.w - 8f), l.y(b - 8f)); close() }, Color(0xFFE07A2E))
        when (s) {
            1 -> tent(l, 8f, b, 20f, 9f, 10f)
            2 -> {
                box(l, 8f, b, 22f, 9f, 9f, WoodWall, WoodRoof, Roof.GABLE).let { windows(l, it, 2); door(l, it) }
                box(l, 36f, b, 6f, 4f, 13f, WoodWall, WoodRoof).let { drawRect(Glass, Offset(it.left + 0.8f * l.u, it.top + 1f * l.u), Size(4.4f * l.u, 2.6f * l.u)) }
            }
            else -> {
                // Apron in front of the hangars
                drawRect(Color(0xFF8E908C), l.o(4f, b - 4f), Size((stripW - 2f) * l.u, 6f * l.u))
                box(l, 6f, b - 4f, 30f, 12f, 14f, Color(0xFFBDB9AE), Color(0xFF8C8F92), Roof.ARCH).let {
                    drawRect(Color(0xFF2E3338), Offset(it.left + 5f * l.u, it.bottom - 9f * l.u), Size(20f * l.u, 9f * l.u))
                }
                if (s == 4) {
                    box(l, 40f, b - 4f, 26f, 12f, 12f, Color(0xFFBDB9AE), Color(0xFF8C8F92), Roof.ARCH).let {
                        drawRect(Color(0xFF2E3338), Offset(it.left + 4f * l.u, it.bottom - 8f * l.u), Size(18f * l.u, 8f * l.u))
                    }
                    helicopter(l, 84f, b - 2f)
                    helicopter(l, l.w - 30f, stripY + 13f)
                }
                // Control tower: a shaft with a glass cab
                val tx = if (s == 4) 104f else 46f
                box(l, tx, b, 5f, 4f, 18f, Concrete, Tar)
                box(l, tx - 1.5f, b - 18f, 8f, 5f, 4f, GlassLit, Tar, shadow = false)
            }
        }
    }

    private fun DrawScope.gate(l: Lot, s: Int) {
        val b = l.base
        val blue = Color(0xFF2F5FA6)
        // Entry road through the gate
        drawRect(Color(0xFF6A6C6B).copy(alpha = if (s >= 2) 1f else 0f), l.o(4f, b - 8f), Size(30f * l.u, 12f * l.u))
        // Barrier arm
        drawRect(Steel, l.o(5f, b - 2.5f), Size(1.6f * l.u, 3f * l.u))
        drawLine(Color(0xFFEDE8D8), l.o(6f, b - 2f), l.o(32f, b - 2f), strokeWidth = 1.1f * l.u)
        for (i in 0..4) drawLine(Red, l.o(8f + i * 5f, b - 2f), l.o(10f + i * 5f, b - 2f), strokeWidth = 1.1f * l.u)
        when (s) {
            1 -> {
                box(l, 38f, b, 9f, 6f, 9f, WoodWall, WoodRoof).let { drawRect(Glass, Offset(it.left + 1.2f * l.u, it.top + 1.5f * l.u), Size(6f * l.u, 2.4f * l.u)) }
                sandbags(l, 36f, b + 1.5f, 3)
                flag(l, 52f, b, 20f, blue)
            }
            2, 3 -> {
                val f = box(l, 37f, b, 16f, 9f, 11f, Concrete, Tar)
                drawRect(Color(0xFF1F2A3A), Offset(f.left + 2f * l.u, f.top + 2f * l.u), Size(12f * l.u, 2.6f * l.u))
                drawRect(Color(0xFF4A8BE0), l.o(42f, b - 21.5f), Size(6f * l.u, 1.2f * l.u))
                door(l, f, cxPx = f.right - 3f * l.u)
                flag(l, 55f, b, 22f, blue)
                if (s == 3) for (i in 0..3) box(l, 5f + i * 7f, b - 6.5f, 4f, 1.5f, 2f, Concrete, lighter(Concrete), shadow = false)
            }
            else -> {
                // Canopy over the lanes, an overwatch post and vehicle barriers
                for (i in 0..3) box(l, 5f + i * 7f, b - 6.5f, 4f, 1.5f, 2f, Concrete, lighter(Concrete), shadow = false)
                for (x in listOf(5f, 32f)) drawRect(Steel, l.o(x, b - 12f), Size(0.8f * l.u, 12f * l.u))
                box(l, 4f, b - 12f, 30f, 7f, 2f, Color(0xFF6E7378), Color(0xFF8A8F94), shadow = false)
                val f = box(l, 37f, b, 17f, 10f, 15f, Concrete, Tar)
                drawRect(Color(0xFF1F2A3A), Offset(f.left + 2f * l.u, f.top + 2f * l.u), Size(13f * l.u, 2.6f * l.u))
                drawRect(Color(0xFF4A8BE0), l.o(42f, b - 26.5f), Size(6f * l.u, 1.2f * l.u))
                door(l, f, cxPx = f.right - 3f * l.u)
                flag(l, 55f, b - 25f, 9f, blue)
            }
        }
    }

    // ---------------- Ground and infrastructure ----------------

    /** Grass texture: short darker tufts at fixed spots so the field never looks flat. */
    fun DrawScope.grassTexture(u: Float, w: Float, h: Float) {
        var seed = 7
        fun rnd(n: Int): Int { seed = (seed * 1103515245 + 12345) and 0x7fffffff; return seed % n }
        for (i in 0 until 260) {
            val x = rnd(w.toInt()).toFloat(); val y = rnd(h.toInt()).toFloat()
            val col = if (i % 3 == 0) GrassLight else GrassDark
            drawLine(col.copy(alpha = 0.55f), Offset(x * u, y * u), Offset((x + 0.5f) * u, (y - 1.4f) * u), strokeWidth = 0.35f * u)
            drawLine(col.copy(alpha = 0.55f), Offset((x + 0.8f) * u, y * u), Offset((x + 0.6f) * u, (y - 1.1f) * u), strokeWidth = 0.35f * u)
        }
    }

    /**
     * Roads by Command HQ band: 1 dirt tracks with ruts, 2 gravel with sidewalks and streetlights,
     * 3 asphalt with curbs, sidewalks, lane markings and planted trees.
     */
    fun DrawScope.roads(u: Float, band: Int, roads: List<Rect>, horizontal: (Rect) -> Boolean) {
        val surface = when (band) { 1 -> Color(0xFF8C7A55); 2 -> Color(0xFF9C9A92); else -> Color(0xFF4E5153) }
        val walk = Color(0xFFCBC6B6)
        if (band >= 2) roads.forEach { r ->
            // Sidewalks along each road
            val pad = 1.3f * u
            drawRect(walk, Offset(r.left - pad, r.top - pad), Size(r.width + pad * 2, r.height + pad * 2))
        }
        roads.forEach { r -> drawRect(surface, r.topLeft, r.size) }
        roads.forEach { r ->
            when (band) {
                1 -> {
                    val ruts = darker(surface, 0.15f)
                    if (horizontal(r)) {
                        drawLine(ruts, Offset(r.left, r.top + r.height * 0.3f), Offset(r.right, r.top + r.height * 0.3f), strokeWidth = 0.4f * u)
                        drawLine(ruts, Offset(r.left, r.top + r.height * 0.7f), Offset(r.right, r.top + r.height * 0.7f), strokeWidth = 0.4f * u)
                    } else {
                        drawLine(ruts, Offset(r.left + r.width * 0.5f, r.top), Offset(r.left + r.width * 0.5f, r.bottom), strokeWidth = 0.4f * u)
                    }
                }
                3 -> {
                    drawRect(Color(0xFFD8D3C4), r.topLeft, r.size, style = Stroke(0.35f * u))
                    if (horizontal(r) && r.height >= 5f * u) drawLine(Color(0xFFE8D27A), Offset(r.left + 2 * u, r.center.y), Offset(r.right - 2 * u, r.center.y),
                        strokeWidth = 0.35f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * u, 2.4f * u)))
                }
                else -> Unit
            }
        }
    }

    fun DrawScope.streetlight(x: Float, y: Float, u: Float) {
        drawLine(Color(0xFF55585A), Offset(x * u, y * u), Offset(x * u, (y - 6f) * u), strokeWidth = 0.4f * u)
        drawLine(Color(0xFF55585A), Offset(x * u, (y - 6f) * u), Offset((x + 1.6f) * u, (y - 6f) * u), strokeWidth = 0.4f * u)
        drawCircle(Color(0xFFF3E3A0), 0.6f * u, Offset((x + 1.6f) * u, (y - 5.6f) * u))
    }

    fun DrawScope.car(x: Float, y: Float, u: Float, col: Color, vertical: Boolean = false) {
        val sz = if (vertical) Size(3f * u, 6f * u) else Size(6f * u, 3f * u)
        drawRect(Shadow.copy(alpha = 0.3f), Offset((x + 0.6f) * u, (y + 0.6f) * u), sz)
        drawRect(col, Offset(x * u, y * u), sz)
        val cab = if (vertical) Rect(Offset((x + 0.5f) * u, (y + 1.2f) * u), Size(2f * u, 1.6f * u)) else Rect(Offset((x + 3.6f) * u, (y + 0.5f) * u), Size(1.6f * u, 2f * u))
        drawRect(Glass, cab.topLeft, cab.size)
    }

    fun DrawScope.perimeter(u: Float, band: Int, wall: Rect) {
        when (band) {
            1 -> {
                // Wire fence on wooden pickets
                drawRect(Color(0xFF9DA39A), wall.topLeft, wall.size, style = Stroke(0.5f * u))
                var x = wall.left
                while (x <= wall.right) { drawLine(Color(0xFF7A6A4C), Offset(x, wall.top), Offset(x, wall.top - 1.8f * u), strokeWidth = 0.6f * u); drawLine(Color(0xFF7A6A4C), Offset(x, wall.bottom), Offset(x, wall.bottom - 1.8f * u), strokeWidth = 0.6f * u); x += 10f * u }
            }
            2 -> {
                // Chain-link on steel posts
                drawRect(Color(0xFFB8BDC2).copy(alpha = 0.8f), wall.topLeft, wall.size, style = Stroke(1.1f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.6f * u, 0.5f * u))))
                var x = wall.left
                while (x <= wall.right) { drawLine(Steel, Offset(x, wall.top), Offset(x, wall.top - 2.4f * u), strokeWidth = 0.5f * u); drawLine(Steel, Offset(x, wall.bottom), Offset(x, wall.bottom - 2.4f * u), strokeWidth = 0.5f * u); x += 9f * u }
                var y = wall.top
                while (y <= wall.bottom) { drawCircle(Steel, 0.5f * u, Offset(wall.left, y)); drawCircle(Steel, 0.5f * u, Offset(wall.right, y)); y += 9f * u }
            }
            else -> {
                // Masonry wall with a cap and pillars, seen from above with its inner face
                val stone = Color(0xFFC2B190)
                drawRect(darker(stone, 0.25f), Offset(wall.left, wall.top), Size(wall.width, 2.2f * u))
                drawRect(stone, Offset(wall.left, wall.top - 1.2f * u), Size(wall.width, 1.4f * u))
                drawRect(stone, Offset(wall.left - 1f * u, wall.top), Size(2f * u, wall.height))
                drawRect(stone, Offset(wall.right - 1f * u, wall.top), Size(2f * u, wall.height))
                drawRect(darker(stone, 0.25f), Offset(wall.left, wall.bottom - 1f * u), Size(wall.width, 2.2f * u))
                drawRect(stone, Offset(wall.left, wall.bottom - 2.2f * u), Size(wall.width, 1.4f * u))
                var x = wall.left
                while (x <= wall.right) { drawRect(lighter(stone, 0.2f), Offset(x - 1.2f * u, wall.top - 1.8f * u), Size(2.4f * u, 2.4f * u)); x += 16f * u }
                var y = wall.top
                while (y <= wall.bottom) { drawRect(lighter(stone, 0.2f), Offset(wall.left - 1.4f * u, y - 1.2f * u), Size(2.8f * u, 2.4f * u)); drawRect(lighter(stone, 0.2f), Offset(wall.right - 1.4f * u, y - 1.2f * u), Size(2.8f * u, 2.4f * u)); y += 16f * u }
            }
        }
    }
}
