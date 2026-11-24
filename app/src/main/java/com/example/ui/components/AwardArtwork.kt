package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.*
import com.example.ui.theme.LocalAegisColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal fun DrawScope.drawStar(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float,
    color: Color,
    points: Int = 5
) {
    val path = Path()
    val startAngle = -PI / 2.0
    val step = PI / points
    for (i in 0 until points * 2) {
        val angle = startAngle + i * step
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color)
}

internal fun DrawScope.drawCrossPattee(
    center: Offset,
    halfSpan: Float,
    fillColor: Color,
    borderColor: Color
) {
    val armInner = halfSpan * 0.28f
    val armTip = halfSpan * 0.52f
    val path = Path().apply {
        // Top arm
        moveTo(center.x - armTip, center.y - halfSpan)
        lineTo(center.x + armTip, center.y - halfSpan)
        lineTo(center.x + armInner, center.y - armInner)
        // Right arm
        lineTo(center.x + halfSpan, center.y - armTip)
        lineTo(center.x + halfSpan, center.y + armTip)
        lineTo(center.x + armInner, center.y + armInner)
        // Bottom arm
        lineTo(center.x + armTip, center.y + halfSpan)
        lineTo(center.x - armTip, center.y + halfSpan)
        lineTo(center.x - armInner, center.y + armInner)
        // Left arm
        lineTo(center.x - halfSpan, center.y + armTip)
        lineTo(center.x - halfSpan, center.y - armTip)
        lineTo(center.x - armInner, center.y - armInner)
        close()
    }
    drawPath(path, color = fillColor)
    drawPath(path, color = borderColor, style = Stroke(width = (halfSpan * 0.14f).coerceAtLeast(1.1f), join = StrokeJoin.Round))
}

internal fun DrawScope.drawHeartShield(
    center: Offset,
    radius: Float,
    fillColor: Color,
    borderColor: Color
) {
    val path = Path().apply {
        moveTo(center.x, center.y + radius * 1.05f)
        cubicTo(
            center.x - radius * 1.15f, center.y + radius * 0.35f,
            center.x - radius * 1.1f, center.y - radius * 0.72f,
            center.x, center.y - radius * 0.32f
        )
        cubicTo(
            center.x + radius * 1.1f, center.y - radius * 0.72f,
            center.x + radius * 1.15f, center.y + radius * 0.35f,
            center.x, center.y + radius * 1.05f
        )
        close()
    }
    drawPath(path, color = fillColor)
    drawPath(path, color = borderColor, style = Stroke(width = (radius * 0.18f).coerceAtLeast(1.2f)))
}

internal fun DrawScope.drawHexagon(
    center: Offset,
    radius: Float,
    fillColor: Color,
    borderColor: Color
) {
    val path = Path()
    for (i in 0 until 6) {
        val angle = -PI / 2.0 + i * (PI / 3.0)
        val x = center.x + (radius * cos(angle)).toFloat()
        val y = center.y + (radius * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = fillColor)
    drawPath(path, color = borderColor, style = Stroke(width = (radius * 0.15f).coerceAtLeast(1.1f)))
}

internal fun DrawScope.drawLaurelWreathArcs(
    center: Offset,
    radius: Float,
    color: Color,
    strokeWidth: Float
) {
    drawArc(
        color = color,
        startAngle = 115f,
        sweepAngle = 125f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
    drawArc(
        color = color,
        startAngle = -60f,
        sweepAngle = 125f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

internal fun DrawScope.drawDetailedMedalPendant(
    medalId: String,
    tier: MedalTier,
    center: Offset,
    radius: Float
) {
    val gold = Color(0xFFF2D272)
    val bronze = Color(0xFFC48C4D)
    val silver = Color(0xFFE1E8F2)
    val darkCore = Color(0xFF141A21)

    when (medalId) {
        "medal_sovereign_star" -> {
            // Emerald wreath behind inverted/upright 5-point gold star
            drawLaurelWreathArcs(center, radius * 0.98f, Color(0xFF2D8A56), radius * 0.22f)
            drawStar(center, radius * 0.92f, radius * 0.42f, gold)
            drawCircle(color = Color(0xFF2B60A8), radius = radius * 0.28f, center = center)
            drawCircle(color = gold, radius = radius * 0.12f, center = center)
        }
        "medal_distinguished_cross" -> {
            drawLaurelWreathArcs(center, radius * 0.72f, Color(0xFF3E6F4E), radius * 0.18f)
            drawCrossPattee(center, radius * 0.96f, bronze, gold)
            drawCircle(color = gold, radius = radius * 0.26f, center = center)
            drawStar(center, radius * 0.18f, radius * 0.08f, darkCore)
        }
        "medal_defense_superior" -> {
            drawStar(center, radius * 0.96f, radius * 0.70f, silver, points = 8)
            drawHexagon(center, radius * 0.62f, Color(0xFF214A7A), gold)
            drawStar(center, radius * 0.34f, radius * 0.16f, Color(0xFF9C2D2D))
        }
        "medal_silver_vanguard" -> {
            drawCircle(color = bronze, radius = radius * 0.92f, center = center, style = Stroke(radius * 0.22f))
            drawStar(center, radius * 0.94f, radius * 0.40f, gold)
            drawStar(center, radius * 0.56f, radius * 0.24f, silver)
        }
        "medal_purple_shield" -> {
            drawHeartShield(center, radius * 0.88f, Color(0xFF6B3285), gold)
            drawCircle(color = gold, radius = radius * 0.25f, center = Offset(center.x, center.y - radius * 0.05f))
        }
        "medal_bronze_valor" -> {
            drawStar(center, radius * 0.95f, radius * 0.42f, bronze)
            drawStar(center, radius * 0.65f, radius * 0.29f, gold)
            drawCircle(color = darkCore, radius = radius * 0.18f, center = center)
        }
        "medal_meritorious" -> {
            drawLaurelWreathArcs(center, radius * 0.90f, gold, radius * 0.22f)
            drawStar(center, radius * 0.78f, radius * 0.34f, Color(0xFF9C2D2D))
            drawCircle(color = gold, radius = radius * 0.24f, center = center)
        }
        "medal_army_commendation" -> {
            drawHexagon(center, radius * 0.92f, Color(0xFF2B5B3F), bronze)
            drawHexagon(center, radius * 0.60f, bronze, gold)
            drawCircle(color = gold, radius = radius * 0.22f, center = center)
        }
        // Item 156: every other medal and ribbon gets its own pendant (no shared fallback disc)
        "medal_distinguished_service" -> {
            drawCircle(color = gold, radius = radius * 0.92f, center = center)
            drawCircle(color = Color(0xFF2B60A8), radius = radius * 0.64f, center = center)
            drawStar(center, radius * 0.52f, radius * 0.22f, gold)
        }
        "medal_legion_merit" -> {
            drawStar(center, radius * 0.96f, radius * 0.52f, Color(0xFFB22234))
            drawStar(center, radius * 0.62f, radius * 0.32f, Color.White)
            drawCircle(color = Color(0xFF2D8A56), radius = radius * 0.20f, center = center)
        }
        "medal_flying_cross" -> {
            drawCrossPattee(center, radius * 0.94f, bronze, gold)
            drawStar(center, radius * 0.78f, radius * 0.10f, silver, points = 4)
            drawCircle(color = gold, radius = radius * 0.14f, center = center)
        }
        "medal_soldiers" -> {
            drawStar(center, radius * 0.94f, radius * 0.87f, gold, points = 8)
            drawCircle(color = Color(0xFF1E3A6E), radius = radius * 0.52f, center = center)
            drawStar(center, radius * 0.34f, radius * 0.15f, silver)
        }
        "medal_air_medal" -> {
            drawStar(center, radius * 0.96f, radius * 0.36f, bronze, points = 8)
            drawStar(center, radius * 0.56f, radius * 0.24f, gold, points = 4)
            drawCircle(color = darkCore, radius = radius * 0.12f, center = center)
        }
        "medal_joint_commendation" -> {
            drawHexagon(center, radius * 0.90f, Color(0xFF2D6E8C), gold)
            drawStar(center, radius * 0.44f, radius * 0.19f, gold)
        }
        "medal_army_achievement" -> {
            drawHexagon(center, radius * 0.88f, bronze, gold)
            drawHeartShield(center, radius * 0.44f, Color(0xFF2B5B3F), gold)
        }
        "medal_joint_achievement" -> {
            drawCircle(color = bronze, radius = radius * 0.84f, center = center)
            drawStar(center, radius * 0.64f, radius * 0.28f, silver, points = 6)
            drawCircle(color = Color(0xFF2D6E8C), radius = radius * 0.16f, center = center)
        }
        "medal_pow" -> {
            drawCircle(color = bronze, radius = radius * 0.88f, center = center)
            drawCircle(color = darkCore, radius = radius * 0.70f, center = center, style = Stroke(radius * 0.10f))
            drawStar(center, radius * 0.46f, radius * 0.20f, darkCore)
        }
        "medal_good_conduct" -> {
            drawCircle(color = bronze, radius = radius * 0.88f, center = center)
            drawLaurelWreathArcs(center, radius * 0.70f, gold, radius * 0.16f)
            drawStar(center, radius * 0.36f, radius * 0.16f, Color(0xFFB22234))
        }
        "medal_national_defense" -> {
            drawCircle(color = bronze, radius = radius * 0.88f, center = center)
            drawHeartShield(center, radius * 0.56f, Color(0xFFB22234), gold)
        }
        "medal_expeditionary" -> {
            drawStar(center, radius * 0.94f, radius * 0.52f, bronze, points = 7)
            drawCircle(color = Color(0xFF2D4F38), radius = radius * 0.32f, center = center)
        }
        "medal_armed_forces_service" -> {
            drawCircle(color = bronze, radius = radius * 0.88f, center = center)
            drawCircle(color = darkCore, radius = radius * 0.60f, center = center)
            drawStar(center, radius * 0.50f, radius * 0.18f, gold, points = 4)
        }
        "medal_humanitarian" -> {
            drawCircle(color = silver, radius = radius * 0.88f, center = center)
            drawLaurelWreathArcs(center, radius * 0.70f, bronze, radius * 0.18f)
            drawCircle(color = bronze, radius = radius * 0.28f, center = center)
        }
        "medal_nco_dev" -> {
            drawStar(center, radius * 0.94f, radius * 0.64f, gold, points = 12)
            drawCircle(color = Color(0xFF1E3E66), radius = radius * 0.42f, center = center)
            drawStar(center, radius * 0.28f, radius * 0.12f, gold)
        }
        "medal_sere" -> {
            drawCircle(color = darkCore, radius = radius * 0.88f, center = center)
            drawLaurelWreathArcs(center, radius * 0.68f, silver, radius * 0.16f)
            drawCircle(color = silver, radius = radius * 0.20f, center = center)
        }
        "medal_night_owl" -> {
            drawCircle(color = Color(0xFF1B2433), radius = radius * 0.88f, center = center)
            drawCircle(color = gold, radius = radius * 0.52f, center = center)
            drawCircle(color = Color(0xFF1B2433), radius = radius * 0.46f, center = Offset(center.x + radius * 0.22f, center.y - radius * 0.08f))
        }
        "medal_basic_training" -> {
            drawCircle(color = Color(0xFFB22234), radius = radius * 0.88f, center = center)
            drawStar(center, radius * 0.62f, radius * 0.26f, gold)
        }
        "medal_overseas_service" -> {
            drawCircle(color = Color(0xFF1E3A6E), radius = radius * 0.88f, center = center)
            drawCircle(color = Color(0xFF63B8D6), radius = radius * 0.56f, center = center)
            drawStar(center, radius * 0.28f, radius * 0.12f, gold)
        }
        "medal_nato" -> {
            drawCircle(color = Color(0xFF14306E), radius = radius * 0.88f, center = center)
            drawCircle(color = Color.White, radius = radius * 0.80f, center = center, style = Stroke(radius * 0.06f))
            drawStar(center, radius * 0.72f, radius * 0.14f, Color.White, points = 4)
        }
        "medal_un" -> {
            drawCircle(color = Color(0xFF5B92E5), radius = radius * 0.88f, center = center)
            drawLaurelWreathArcs(center, radius * 0.66f, Color.White, radius * 0.14f)
            drawCircle(color = Color.White, radius = radius * 0.22f, center = center)
        }
        else -> {
            val base = if (tier == MedalTier.SUPREME) gold else bronze
            drawCircle(color = base, radius = radius * 0.86f, center = center)
            drawStar(center, radius * 0.54f, radius * 0.24f, darkCore)
        }
    }
}

/** Item 156: medals and ribbons with their own pendant art (anything else would fall back to the plain disc). */
internal val PENDANT_ART_IDS: Set<String> = setOf(
    "medal_sovereign_star", "medal_distinguished_cross", "medal_defense_superior", "medal_silver_vanguard",
    "medal_purple_shield", "medal_bronze_valor", "medal_meritorious", "medal_army_commendation",
    "medal_distinguished_service", "medal_legion_merit", "medal_flying_cross", "medal_soldiers", "medal_air_medal",
    "medal_joint_commendation", "medal_army_achievement", "medal_joint_achievement", "medal_pow", "medal_good_conduct",
    "medal_national_defense", "medal_expeditionary", "medal_armed_forces_service", "medal_humanitarian",
    "medal_nco_dev", "medal_sere", "medal_night_owl", "medal_basic_training", "medal_overseas_service",
    "medal_nato", "medal_un"
)

@Composable
fun MedalRibbonBar(
    stripeHexes: List<Long>,
    modifier: Modifier = Modifier,
    width: Dp = 44.dp,
    height: Dp = 14.dp,
    isSupremeFrame: Boolean = false
) {
    val extra = LocalAegisColors.current
    val borderColor = if (isSupremeFrame) extra.brassBright else extra.brass.copy(alpha = 0.85f)
    Canvas(
        modifier = modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(2.dp))
            .border(if (isSupremeFrame) 1.25.dp else 0.8.dp, borderColor, RoundedCornerShape(2.dp))
    ) {
        val count = stripeHexes.size.coerceAtLeast(1)
        val stripeW = size.width / count
        stripeHexes.forEachIndexed { index, hex ->
            drawRect(
                color = Color(hex),
                topLeft = Offset(index * stripeW, 0f),
                size = Size(stripeW + 0.5f, size.height)
            )
        }
        // Subtle woven silk moiré rib lines
        val ribStep = (size.height / 5f).coerceAtLeast(2f)
        var y = ribStep
        while (y < size.height) {
            drawLine(
                color = Color.Black.copy(alpha = 0.14f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 0.8f
            )
            y += ribStep
        }
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.22f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.28f)
                )
            ),
            size = size
        )
    }
}

@Composable
fun AwardVisualThumbnail(
    medal: MedalDefinition,
    modifier: Modifier = Modifier
) {
    val brass = LocalAegisColors.current.brassBright
    when (medal.category) {
        AwardCategory.RIBBON -> {
            Box(modifier = modifier.size(width = 52.dp, height = 28.dp), contentAlignment = Alignment.Center) {
                MedalRibbonBar(
                    stripeHexes = medal.ribbonStripeHexes,
                    width = 50.dp,
                    height = 15.dp,
                    isSupremeFrame = medal.tier == MedalTier.SUPREME || medal.tier == MedalTier.VALOR
                )
            }
        }
        AwardCategory.MEDAL -> {
            Canvas(modifier = modifier.size(width = 50.dp, height = 46.dp)) {
                val w = size.width
                val h = size.height
                val ribW = w * 0.56f
                val ribH = h * 0.36f
                val rx = (w - ribW) / 2f
                val topY = h * 0.04f
                val stripes = medal.ribbonStripeHexes.size.coerceAtLeast(1)
                val sw = ribW / stripes
                medal.ribbonStripeHexes.forEachIndexed { idx, hex ->
                    drawRect(
                        color = Color(hex),
                        topLeft = Offset(rx + idx * sw, topY),
                        size = Size(sw + 0.5f, ribH)
                    )
                }
                // Silk highlight + brass top brooch pin
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.22f))),
                    topLeft = Offset(rx, topY),
                    size = Size(ribW, ribH)
                )
                drawRect(color = brass, topLeft = Offset(rx, topY), size = Size(ribW, ribH), style = Stroke(1.3f))
                drawRect(color = brass, topLeft = Offset(rx - w * 0.04f, topY), size = Size(ribW + w * 0.08f, h * 0.06f))

                // Star-spangled neck ribbon accent or Bronze "V" device
                if (medal.isNeckMedallion) {
                    repeat(3) { s ->
                        drawCircle(Color.White, radius = w * 0.025f, center = Offset(rx + ribW * (0.25f + s * 0.25f), topY + ribH * 0.55f))
                    }
                } else if (medal.id == "medal_bronze_valor") {
                    drawRect(brass, topLeft = Offset(w / 2f - w * 0.06f, topY + ribH * 0.35f), size = Size(w * 0.12f, ribH * 0.35f))
                }

                // Ring link
                val cy = topY + ribH + h * 0.32f
                drawLine(brass, Offset(w / 2f, topY + ribH), Offset(w / 2f, cy - w * 0.21f), strokeWidth = (w * 0.045f).coerceAtLeast(1.6f))
                drawDetailedMedalPendant(
                    medalId = medal.id,
                    tier = medal.tier,
                    center = Offset(w / 2f, cy),
                    radius = w * 0.24f
                )
            }
        }
        AwardCategory.SHOULDER_CORD -> {
            val cordColor = Color(medal.cordHex ?: 0xFFD8B24C)
            val secondaryTwist = when (medal.id) {
                "cord_signals_orange", "cord_medical_maroon", "cord_engineer_scarlet" -> Color.White
                "cord_intel_teal" -> Color(0xFFDDE3EC)
                "cord_logistics_buff" -> Color(0xFF9C2D2D)
                "cord_sf_emerald" -> Color(0xFFF2D272)
                "cord_valor_fourragere" -> Color(0xFF9C2D2D)
                "cord_aviation" -> Color(0xFFE89A2B)
                "cord_general_aiguillette" -> Color(0xFFFFE796)
                else -> cordColor.copy(alpha = 0.65f)
            }
            Canvas(modifier = modifier.size(width = 50.dp, height = 42.dp)) {
                val w = size.width
                val h = size.height
                val loopOuter = Path().apply {
                    moveTo(w * 0.28f, h * 0.14f)
                    cubicTo(w * 0.10f, h * 0.56f, w * 0.22f, h * 0.84f, w * 0.54f, h * 0.80f)
                    cubicTo(w * 0.84f, h * 0.76f, w * 0.86f, h * 0.30f, w * 0.64f, h * 0.14f)
                }
                val loopInner = Path().apply {
                    moveTo(w * 0.36f, h * 0.17f)
                    cubicTo(w * 0.24f, h * 0.50f, w * 0.35f, h * 0.68f, w * 0.54f, h * 0.66f)
                    cubicTo(w * 0.72f, h * 0.64f, w * 0.74f, h * 0.36f, w * 0.58f, h * 0.17f)
                }
                drawPath(loopOuter, color = cordColor, style = Stroke(width = (w * 0.11f).coerceAtLeast(4.5f), cap = StrokeCap.Round))
                drawPath(loopOuter, color = secondaryTwist, style = Stroke(width = (w * 0.042f).coerceAtLeast(1.8f), cap = StrokeCap.Round))
                drawPath(loopInner, color = cordColor.copy(alpha = 0.9f), style = Stroke(width = (w * 0.075f).coerceAtLeast(3.0f), cap = StrokeCap.Round))
                // Braided shoulder knot + brass pencil-tip aglets (ferrules)
                drawCircle(color = brass, radius = w * 0.075f, center = Offset(w * 0.64f, h * 0.16f))
                drawLine(cordColor, Offset(w * 0.62f, h * 0.18f), Offset(w * 0.70f, h * 0.74f), strokeWidth = (w * 0.055f).coerceAtLeast(2.2f))
                drawLine(brass, Offset(w * 0.70f, h * 0.74f), Offset(w * 0.72f, h * 0.92f), strokeWidth = (w * 0.065f).coerceAtLeast(2.6f), cap = StrokeCap.Round)
                if (medal.isRightShoulder) {
                    drawLine(cordColor, Offset(w * 0.32f, h * 0.18f), Offset(w * 0.38f, h * 0.72f), strokeWidth = (w * 0.05f).coerceAtLeast(2.0f))
                    drawLine(brass, Offset(w * 0.38f, h * 0.72f), Offset(w * 0.39f, h * 0.90f), strokeWidth = (w * 0.065f).coerceAtLeast(2.6f), cap = StrokeCap.Round)
                }
            }
        }
        AwardCategory.SCHOOL_INSIGNIA -> {
            val col = Color(medal.insigniaColorHex)
            Canvas(modifier = modifier.size(width = 52.dp, height = 34.dp)) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f
                when (medal.insigniaShape ?: InsigniaShape.WINGS) {
                    InsigniaShape.TAB -> {
                        val isSfTab = medal.id == "insig_sf_tab" || col == Color(0xFF36D1C4)
                        val tabBack = if (isSfTab) Color(0xFF1B5B63) else Color(0xFF12161E)
                        val tabTrim = if (isSfTab) Color(0xFFF2D272) else col
                        val arcPath = Path().apply {
                            moveTo(w * 0.08f, h * 0.70f)
                            quadraticTo(cx, h * 0.08f, w * 0.92f, h * 0.70f)
                            lineTo(w * 0.82f, h * 0.84f)
                            quadraticTo(cx, h * 0.38f, w * 0.18f, h * 0.84f)
                            close()
                        }
                        drawPath(arcPath, color = tabBack)
                        drawPath(arcPath, color = tabTrim, style = Stroke(width = (w * 0.045f).coerceAtLeast(1.8f), join = StrokeJoin.Round))
                        // Woven lettering blocks along the curved tab arc
                        for (i in 0 until 6) {
                            val f0 = 0.24f + i * 0.092f
                            val f1 = f0 + 0.056f
                            val sx0 = w * f0
                            val sx1 = w * f1
                            val arch0 = kotlin.math.sin(((f0 - 0.20f) / 0.60f).coerceIn(0f, 1f) * kotlin.math.PI.toFloat()) * h * 0.18f
                            val arch1 = kotlin.math.sin(((f1 - 0.20f) / 0.60f).coerceIn(0f, 1f) * kotlin.math.PI.toFloat()) * h * 0.18f
                            drawLine(
                                color = tabTrim,
                                start = Offset(sx0, h * 0.64f - arch0),
                                end = Offset(sx1, h * 0.64f - arch1),
                                strokeWidth = (h * 0.11f).coerceAtLeast(2.4f),
                                cap = StrokeCap.Butt
                            )
                        }
                    }
                    InsigniaShape.WINGS, InsigniaShape.STAR_WINGS, InsigniaShape.SENIOR_PARA_WINGS -> {
                        // Sculpted layered feathers
                        drawLine(col, Offset(w * 0.08f, cy - h * 0.06f), Offset(w * 0.92f, cy - h * 0.06f), strokeWidth = (h * 0.11f).coerceAtLeast(3.0f), cap = StrokeCap.Round)
                        drawLine(col, Offset(w * 0.14f, cy + h * 0.10f), Offset(w * 0.86f, cy + h * 0.10f), strokeWidth = (h * 0.08f).coerceAtLeast(2.2f), cap = StrokeCap.Round)
                        drawLine(col, Offset(w * 0.24f, cy + h * 0.24f), Offset(w * 0.76f, cy + h * 0.24f), strokeWidth = (h * 0.06f).coerceAtLeast(1.6f), cap = StrokeCap.Round)
                        // Ribbed parachute canopy + shroud triangle
                        drawArc(
                            color = col,
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = Offset(cx - w * 0.14f, h * 0.24f),
                            size = Size(w * 0.28f, h * 0.42f)
                        )
                        drawLine(col, Offset(cx - w * 0.10f, h * 0.46f), Offset(cx, h * 0.78f), strokeWidth = 1.6f)
                        drawLine(col, Offset(cx + w * 0.10f, h * 0.46f), Offset(cx, h * 0.78f), strokeWidth = 1.6f)
                        if (medal.insigniaShape == InsigniaShape.STAR_WINGS) {
                            drawLaurelWreathArcs(Offset(cx, h * 0.22f), h * 0.18f, Color(0xFFF2D272), (h * 0.055f).coerceAtLeast(1.6f))
                            drawStar(Offset(cx, h * 0.22f), h * 0.15f, h * 0.065f, Color(0xFFF2D272))
                        } else if (medal.insigniaShape == InsigniaShape.SENIOR_PARA_WINGS) {
                            drawStar(Offset(cx, h * 0.22f), h * 0.14f, h * 0.06f, col)
                        }
                    }
                    InsigniaShape.MOUNTAIN_BADGE, InsigniaShape.JUNGLE_BADGE, InsigniaShape.ARCTIC_BADGE, InsigniaShape.SNIPER_BADGE ->
                        drawEnvironmentOrSniperBadge(medal.insigniaShape!!, Offset(cx, cy), w * 0.9f, h * 0.9f, col)
                    InsigniaShape.MFF_WINGS -> {
                        drawLine(col, Offset(w * 0.06f, cy + h * 0.02f), Offset(w * 0.94f, cy + h * 0.02f), strokeWidth = (h * 0.10f).coerceAtLeast(3.0f), cap = StrokeCap.Round)
                        drawLine(col, Offset(w * 0.16f, cy + h * 0.17f), Offset(w * 0.84f, cy + h * 0.17f), strokeWidth = (h * 0.075f).coerceAtLeast(2.2f), cap = StrokeCap.Round)
                        drawArc(col, 180f, 180f, true, Offset(cx - w * 0.14f, h * 0.20f), Size(w * 0.28f, h * 0.40f))
                        drawLine(Color(0xFF121722), Offset(cx, h * 0.06f), Offset(cx, h * 0.86f), strokeWidth = (h * 0.09f).coerceAtLeast(2.6f))
                        drawLine(Color(0xFFF2D272), Offset(cx, h * 0.06f), Offset(cx, h * 0.86f), strokeWidth = (h * 0.05f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
                        drawLine(Color(0xFFF2D272), Offset(cx - w * 0.06f, h * 0.68f), Offset(cx + w * 0.06f, h * 0.68f), strokeWidth = (h * 0.05f).coerceAtLeast(1.5f))
                    }
                    InsigniaShape.DIVER_BADGE -> {
                        drawLine(col, Offset(w * 0.18f, h * 0.90f), Offset(w * 0.82f, h * 0.10f), strokeWidth = (h * 0.06f).coerceAtLeast(1.8f), cap = StrokeCap.Round)
                        drawLine(col, Offset(w * 0.82f, h * 0.90f), Offset(w * 0.18f, h * 0.10f), strokeWidth = (h * 0.06f).coerceAtLeast(1.8f), cap = StrokeCap.Round)
                        drawCircle(col, radius = h * 0.30f, center = Offset(cx, cy))
                        drawCircle(Color(0xFF121722), radius = h * 0.13f, center = Offset(cx, cy - h * 0.02f))
                        drawRect(col, Offset(cx - w * 0.12f, cy + h * 0.24f), Size(w * 0.24f, h * 0.10f))
                    }
                    InsigniaShape.AVIATOR_WINGS, InsigniaShape.SENIOR_AVIATOR_WINGS, InsigniaShape.MASTER_AVIATOR_WINGS -> {
                        val shape = medal.insigniaShape
                        drawLine(col, Offset(w * 0.04f, cy - h * 0.02f), Offset(w * 0.96f, cy - h * 0.02f), strokeWidth = (h * 0.10f).coerceAtLeast(3.0f), cap = StrokeCap.Round)
                        drawLine(col, Offset(w * 0.12f, cy + h * 0.13f), Offset(w * 0.88f, cy + h * 0.13f), strokeWidth = (h * 0.075f).coerceAtLeast(2.2f), cap = StrokeCap.Round)
                        drawLine(col, Offset(w * 0.22f, cy + h * 0.26f), Offset(w * 0.78f, cy + h * 0.26f), strokeWidth = (h * 0.055f).coerceAtLeast(1.6f), cap = StrokeCap.Round)
                        val shield = Path().apply {
                            moveTo(cx - w * 0.09f, cy - h * 0.18f)
                            lineTo(cx + w * 0.09f, cy - h * 0.18f)
                            lineTo(cx + w * 0.09f, cy + h * 0.10f)
                            quadraticTo(cx + w * 0.07f, cy + h * 0.28f, cx, cy + h * 0.36f)
                            quadraticTo(cx - w * 0.07f, cy + h * 0.28f, cx - w * 0.09f, cy + h * 0.10f)
                            close()
                        }
                        drawPath(shield, col)
                        drawPath(shield, Color(0xFF121722).copy(alpha = 0.55f), style = Stroke(width = 1.1f))
                        if (shape == InsigniaShape.MASTER_AVIATOR_WINGS) {
                            drawLaurelWreathArcs(Offset(cx, h * 0.14f), h * 0.15f, col, (h * 0.05f).coerceAtLeast(1.4f))
                        }
                        if (shape != InsigniaShape.AVIATOR_WINGS) {
                            drawStar(Offset(cx, h * 0.14f), h * 0.12f, h * 0.05f, col)
                        }
                    }
                    // EIB: rifle bar only. CIB: rifle bar inside an oak wreath.
                    InsigniaShape.RIFLE_BADGE, InsigniaShape.CIB_BADGE -> {
                        val silver = Color(0xFFDDE3EC)
                        if (medal.insigniaShape == InsigniaShape.CIB_BADGE) {
                            drawLaurelWreathArcs(Offset(cx, cy), h * 0.42f, silver, (h * 0.09f).coerceAtLeast(2.5f))
                        }
                        drawRoundRect(
                            color = col,
                            topLeft = Offset(w * 0.14f, h * 0.28f),
                            size = Size(w * 0.72f, h * 0.44f),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                        drawRoundRect(
                            color = silver,
                            topLeft = Offset(w * 0.14f, h * 0.28f),
                            size = Size(w * 0.72f, h * 0.44f),
                            cornerRadius = CornerRadius(4f, 4f),
                            style = Stroke(1.6f)
                        )
                        // Musket silhouette + bayonet
                        drawLine(silver, Offset(w * 0.20f, cy + 1f), Offset(w * 0.80f, cy - 1f), strokeWidth = (h * 0.09f).coerceAtLeast(2.8f), cap = StrokeCap.Round)
                        drawLine(silver, Offset(w * 0.22f, cy + 4f), Offset(w * 0.34f, cy + 1f), strokeWidth = (h * 0.11f).coerceAtLeast(3.2f), cap = StrokeCap.Round)
                    }
                    // CAB: bayonet over a grenade inside an oak wreath
                    InsigniaShape.CAB_BADGE -> {
                        val silver = Color(0xFFDDE3EC)
                        drawLaurelWreathArcs(Offset(cx, cy), h * 0.44f, silver, (h * 0.08f).coerceAtLeast(2.2f))
                        drawOval(col, topLeft = Offset(cx - h * 0.14f, cy - h * 0.04f), size = Size(h * 0.28f, h * 0.32f))
                        drawLine(silver, Offset(cx, h * 0.12f), Offset(cx, h * 0.80f), strokeWidth = (w * 0.05f).coerceAtLeast(2.4f), cap = StrokeCap.Round)
                        drawLine(silver, Offset(cx - h * 0.10f, cy - h * 0.10f), Offset(cx + h * 0.10f, cy - h * 0.10f), strokeWidth = (w * 0.04f).coerceAtLeast(2.0f), cap = StrokeCap.Round)
                    }
                    // EFMB: cross over a stretcher. CMB: cross and caduceus inside an oak wreath.
                    InsigniaShape.MEDICAL_BADGE, InsigniaShape.COMBAT_MEDICAL_BADGE -> {
                        val combat = medal.insigniaShape == InsigniaShape.COMBAT_MEDICAL_BADGE
                        if (combat) {
                            drawLaurelWreathArcs(Offset(cx, cy), h * 0.44f, Color(0xFFDDE3EC), (h * 0.08f).coerceAtLeast(2.2f))
                        } else {
                            drawLine(Color(0xFFDDE3EC), Offset(w * 0.10f, cy - h * 0.08f), Offset(w * 0.90f, cy - h * 0.08f), strokeWidth = (h * 0.05f).coerceAtLeast(1.8f), cap = StrokeCap.Round)
                            drawLine(Color(0xFFDDE3EC), Offset(w * 0.10f, cy + h * 0.08f), Offset(w * 0.90f, cy + h * 0.08f), strokeWidth = (h * 0.05f).coerceAtLeast(1.8f), cap = StrokeCap.Round)
                        }
                        drawCircle(col.copy(alpha = 0.28f), radius = h * 0.35f, center = Offset(cx, cy))
                        drawLine(col, Offset(cx, h * 0.22f), Offset(cx, h * 0.78f), strokeWidth = (w * 0.08f).coerceAtLeast(3.2f))
                        drawLine(col, Offset(cx - w * 0.16f, cy), Offset(cx + w * 0.16f, cy), strokeWidth = (w * 0.08f).coerceAtLeast(3.2f))
                        if (combat) {
                            val gold = Color(0xFFF2D272)
                            drawLine(gold, Offset(cx, h * 0.16f), Offset(cx, h * 0.84f), strokeWidth = (w * 0.025f).coerceAtLeast(1.4f))
                            drawLine(gold, Offset(cx - h * 0.12f, h * 0.24f), Offset(cx + h * 0.12f, h * 0.24f), strokeWidth = (w * 0.025f).coerceAtLeast(1.4f), cap = StrokeCap.Round)
                        }
                    }
                    InsigniaShape.TORCH_CREST -> {
                        drawLine(col, Offset(w * 0.12f, cy + h * 0.06f), Offset(w * 0.88f, cy + h * 0.06f), strokeWidth = (h * 0.09f).coerceAtLeast(2.8f), cap = StrokeCap.Round)
                        drawLine(col, Offset(cx, h * 0.36f), Offset(cx, h * 0.84f), strokeWidth = (w * 0.07f).coerceAtLeast(2.6f), cap = StrokeCap.Round)
                        drawStar(Offset(cx, h * 0.26f), h * 0.19f, h * 0.09f, Color(0xFFFF9F43))
                    }
                    InsigniaShape.AIR_ASSAULT_WINGS -> {
                        drawLine(col, Offset(w * 0.10f, cy), Offset(w * 0.90f, cy), strokeWidth = (h * 0.10f).coerceAtLeast(3.0f), cap = StrokeCap.Round)
                        drawLine(col, Offset(w * 0.18f, cy + h * 0.15f), Offset(w * 0.82f, cy + h * 0.15f), strokeWidth = (h * 0.07f).coerceAtLeast(2.0f), cap = StrokeCap.Round)
                        drawLine(col, Offset(cx - w * 0.22f, h * 0.22f), Offset(cx + w * 0.22f, h * 0.22f), strokeWidth = (h * 0.08f).coerceAtLeast(2.4f), cap = StrokeCap.Round)
                        drawLine(col, Offset(cx, h * 0.22f), Offset(cx, h * 0.76f), strokeWidth = (w * 0.055f).coerceAtLeast(2.4f))
                        drawOval(col, topLeft = Offset(cx - w * 0.09f, cy - h * 0.10f), size = Size(w * 0.18f, h * 0.24f))
                    }
                    InsigniaShape.CYBER_CREST -> {
                        drawHexagon(Offset(cx, cy), h * 0.43f, col.copy(alpha = 0.22f), col)
                        val bolt = Path().apply {
                            moveTo(cx + w * 0.06f, h * 0.20f)
                            lineTo(cx - w * 0.08f, cy + h * 0.03f)
                            lineTo(cx + w * 0.02f, cy + h * 0.03f)
                            lineTo(cx - w * 0.06f, h * 0.80f)
                        }
                        drawPath(bolt, color = col, style = Stroke(width = (w * 0.052f).coerceAtLeast(2.2f), cap = StrokeCap.Round))
                    }
                    InsigniaShape.INTEL_CREST -> {
                        val diamond = Path().apply {
                            moveTo(cx, h * 0.12f)
                            lineTo(cx + w * 0.24f, cy)
                            lineTo(cx, h * 0.88f)
                            lineTo(cx - w * 0.24f, cy)
                            close()
                        }
                        drawPath(diamond, color = col.copy(alpha = 0.28f))
                        drawPath(diamond, color = col, style = Stroke((w * 0.045f).coerceAtLeast(2.0f)))
                        drawStar(Offset(cx, cy), h * 0.20f, h * 0.08f, Color.White, points = 4)
                    }
                    InsigniaShape.EOD_BADGE -> {
                        drawLaurelWreathArcs(Offset(cx, cy), h * 0.42f, col, (h * 0.085f).coerceAtLeast(2.4f))
                        drawStar(Offset(cx, h * 0.26f), h * 0.17f, h * 0.08f, Color(0xFFFFC145))
                        drawOval(col, topLeft = Offset(cx - w * 0.10f, cy - h * 0.04f), size = Size(w * 0.20f, h * 0.36f))
                    }
                    InsigniaShape.LOGISTICS_CREST -> {
                        drawCircle(col, radius = h * 0.38f, center = Offset(cx, cy), style = Stroke((w * 0.05f).coerceAtLeast(2.2f)))
                        drawLine(col, Offset(cx - w * 0.22f, cy), Offset(cx + w * 0.22f, cy), strokeWidth = 2.2f)
                        drawLine(col, Offset(cx, h * 0.14f), Offset(cx, h * 0.86f), strokeWidth = 2.2f)
                        drawCircle(col, radius = h * 0.12f, center = Offset(cx, cy))
                    }
                    InsigniaShape.STAFF_CREST -> {
                        drawLine(col, Offset(cx - w * 0.24f, h * 0.22f), Offset(cx + w * 0.24f, h * 0.78f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                        drawLine(col, Offset(cx + w * 0.24f, h * 0.22f), Offset(cx - w * 0.24f, h * 0.78f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                        drawCircle(Color(0xFF14181F), radius = h * 0.36f, center = Offset(cx, cy))
                        drawCircle(col, radius = h * 0.36f, center = Offset(cx, cy), style = Stroke(1.8f))
                        drawStar(Offset(cx, cy), h * 0.28f, h * 0.12f, col)
                    }
                }
            }
        }
    }
}

@Composable
fun AwardInspectionDialog(
    medal: MedalDefinition,
    heldByOperators: List<Pair<Operator, AwardedMedal>>,
    onDismiss: () -> Unit
) {
    val extra = LocalAegisColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "PRECEDENCE #${medal.precedenceOrder} • ${medal.category.label.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.signalsCyan
                )
                Text(
                    text = medal.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (medal.tier == MedalTier.SUPREME) extra.brassBright else MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Velvet-lined showcase display box
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = Color(0xFF10151D),
                    border = BorderStroke(1.2.dp, extra.brass.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val boxMod = when (medal.category) {
                            AwardCategory.MEDAL -> Modifier.size(width = 112.dp, height = 104.dp)
                            AwardCategory.RIBBON -> Modifier.size(width = 120.dp, height = 44.dp)
                            AwardCategory.SHOULDER_CORD -> Modifier.size(width = 112.dp, height = 94.dp)
                            AwardCategory.SCHOOL_INSIGNIA -> Modifier.size(width = 120.dp, height = 78.dp)
                        }
                        AwardVisualThumbnail(medal = medal, modifier = boxMod)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "SERVICE RIBBON:",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.textMuted
                            )
                            MedalRibbonBar(
                                stripeHexes = medal.ribbonStripeHexes,
                                width = 64.dp,
                                height = 18.dp,
                                isSupremeFrame = medal.tier == MedalTier.SUPREME || medal.tier == MedalTier.VALOR
                            )
                        }
                    }
                }

                Text(
                    text = medal.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "AUTHORIZATION & TACTICAL VALUE",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.brassBright
                        )
                        Text(
                            text = "• Tier: ${medal.tier.label} (${medal.minTierRequired.shortLabel}+) • ${medal.prestigeCost} PRS",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Qualification: ${medal.qualificationRule}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Stat Bonuses: FP +${medal.firepowerBonus}  ST +${medal.stealthBonus}  TC +${medal.techBonus}  RS +${medal.resilienceBonus}  • +${medal.yieldBonusPct}% Yield",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (heldByOperators.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "DIRECTORATE RECIPIENTS (${heldByOperators.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.signalsCyan
                        )
                        heldByOperators.take(5).forEach { (op, cit) ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "${op.rank.code} ${op.callsign} • ${cit.missionTitle}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = extra.brassBright
                                    )
                                    Text(
                                        text = "\"${cit.citationNote}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("close_award_inspection")
            ) {
                Text("Close Case")
            }
        }
    )
}

/** Items 142 and 143: Mountain, Jungle, Arctic and Sniper badges, drawn for the uniform and the awards screen. */
internal fun DrawScope.drawEnvironmentOrSniperBadge(shape: InsigniaShape, center: Offset, bw: Float, bh: Float, col: Color) {
    val cx = center.x
    val cy = center.y
    val dark = Color(0xFF121722)
    val sw = (bh * 0.07f).coerceAtLeast(1.0f)
    when (shape) {
        InsigniaShape.MOUNTAIN_BADGE -> {
            // Two silver peaks with snow caps over crossed ice axes
            drawLine(col, Offset(cx - bw * 0.36f, cy + bh * 0.42f), Offset(cx + bw * 0.36f, cy + bh * 0.12f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(col, Offset(cx + bw * 0.36f, cy + bh * 0.42f), Offset(cx - bw * 0.36f, cy + bh * 0.12f), strokeWidth = sw, cap = StrokeCap.Round)
            val back = Path().apply {
                moveTo(cx - bw * 0.05f, cy + bh * 0.22f); lineTo(cx + bw * 0.20f, cy - bh * 0.30f); lineTo(cx + bw * 0.46f, cy + bh * 0.22f); close()
            }
            val front = Path().apply {
                moveTo(cx - bw * 0.46f, cy + bh * 0.22f); lineTo(cx - bw * 0.12f, cy - bh * 0.44f); lineTo(cx + bw * 0.22f, cy + bh * 0.22f); close()
            }
            drawPath(back, col.copy(alpha = 0.75f))
            drawPath(front, col)
            drawPath(front, dark.copy(alpha = 0.5f), style = Stroke(width = sw * 0.5f))
            val cap = Path().apply {
                moveTo(cx - bw * 0.21f, cy - bh * 0.27f); lineTo(cx - bw * 0.12f, cy - bh * 0.44f); lineTo(cx - bw * 0.03f, cy - bh * 0.27f); close()
            }
            drawPath(cap, Color.White)
        }
        InsigniaShape.JUNGLE_BADGE -> {
            // Green palm on a black oval
            drawOval(dark, Offset(cx - bw * 0.46f, cy - bh * 0.46f), Size(bw * 0.92f, bh * 0.92f))
            drawOval(col, Offset(cx - bw * 0.46f, cy - bh * 0.46f), Size(bw * 0.92f, bh * 0.92f), style = Stroke(width = sw * 0.7f))
            drawLine(Color(0xFF9A7B4F), Offset(cx + bw * 0.04f, cy + bh * 0.38f), Offset(cx - bw * 0.02f, cy - bh * 0.12f), strokeWidth = sw * 1.2f, cap = StrokeCap.Round)
            for ((dx, dy) in listOf(-0.34f to 0.06f, 0.34f to 0.04f, -0.24f to -0.30f, 0.26f to -0.30f, 0f to -0.40f)) {
                val tip = Offset(cx + bw * dx, cy + bh * dy)
                val frond = Path().apply {
                    moveTo(cx - bw * 0.02f, cy - bh * 0.12f)
                    quadraticTo((cx + tip.x) / 2f, (cy - bh * 0.30f + tip.y) / 2f, tip.x, tip.y)
                }
                drawPath(frond, col, style = Stroke(width = sw * 1.3f, cap = StrokeCap.Round))
            }
        }
        InsigniaShape.ARCTIC_BADGE -> {
            // White snowflake on an ice-blue disc
            drawCircle(Color(0xFF2A4F78), radius = bh * 0.46f, center = center)
            drawCircle(col, radius = bh * 0.46f, center = center, style = Stroke(width = sw * 0.6f))
            for (k in 0 until 3) {
                val a = Math.toRadians(90.0 + k * 60.0)
                val dx = (kotlin.math.cos(a) * bh * 0.36f).toFloat()
                val dy = (kotlin.math.sin(a) * bh * 0.36f).toFloat()
                drawLine(Color.White, Offset(cx - dx, cy - dy), Offset(cx + dx, cy + dy), strokeWidth = sw, cap = StrokeCap.Round)
            }
            drawCircle(Color.White, radius = bh * 0.06f, center = center)
        }
        InsigniaShape.SNIPER_BADGE -> {
            // Crosshairs over a rifle
            drawLine(col, Offset(cx - bw * 0.48f, cy + bh * 0.10f), Offset(cx + bw * 0.48f, cy + bh * 0.10f), strokeWidth = sw * 1.2f, cap = StrokeCap.Round)
            drawLine(col, Offset(cx - bw * 0.48f, cy + bh * 0.10f), Offset(cx - bw * 0.36f, cy + bh * 0.30f), strokeWidth = sw * 1.4f, cap = StrokeCap.Round)
            drawCircle(col, radius = bh * 0.34f, center = center, style = Stroke(width = sw))
            drawLine(col, Offset(cx, cy - bh * 0.44f), Offset(cx, cy + bh * 0.44f), strokeWidth = sw * 0.6f)
            drawLine(col, Offset(cx - bh * 0.44f, cy), Offset(cx + bh * 0.44f, cy), strokeWidth = sw * 0.6f)
            drawCircle(Color(0xFFC8102E), radius = bh * 0.05f, center = center)
        }
        else -> Unit
    }
}
