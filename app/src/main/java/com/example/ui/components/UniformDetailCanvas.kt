package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.data.catalog.GameCatalog
import com.example.domain.TacticalCalculator
import com.example.domain.UniformRegaliaRules
import com.example.domain.model.*
import com.example.ui.theme.LocalAegisColors
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun OperatorUniformPreview(
    operator: Operator,
    equippedGear: List<GearItem>,
    awardedMedals: List<MedalDefinition>,
    modifier: Modifier = Modifier,
    allCitations: List<AwardedMedal> = emptyList(),
    squads: List<TacticalSquad> = emptyList(),
    isSquadLeader: Boolean = UniformRegaliaRules.isLeaderPinShown(operator, squads),
    kitOverride: WardrobeKit? = null
) {
    val extra = LocalAegisColors.current
    val textMeasurer = rememberTextMeasurer()
    val ownedIds = awardedMedals.map { it.id }.toSet()
    val inferredKit = kitOverride ?: when {
        operator.uniformMode == UniformMode.FIELD_TACTICAL -> WardrobeKit.FIELD
        operator.dressStyle.isCutawayMess -> WardrobeKit.MESS
        operator.activeWardrobeKit == WardrobeKit.BRAVOS -> WardrobeKit.BRAVOS
        else -> WardrobeKit.ALPHAS
    }
    val renderOperator = if (kitOverride != null) {
        // An explicit kit comes from the duty resolver, which only puts HQ staff in Field at a field school
        TacticalCalculator.resolveKitAppearance(operator, kitOverride, squads, ownedIds, allowFieldForHqStaff = true)
    } else {
        operator
    }

    val bgTop = MaterialTheme.colorScheme.surfaceContainerHigh
    val bgBottom = MaterialTheme.colorScheme.surfaceContainerLowest
    val borderCol = when {
        renderOperator.status.isOutOfActiveService -> extra.brass.copy(alpha = 0.85f)
        renderOperator.rank.tier == RankTier.FLAG_OFFICER -> extra.brassBright.copy(alpha = 0.85f)
        inferredKit != WardrobeKit.FIELD -> extra.brass.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    }

    val wornRegalia = TacticalCalculator.resolveWornRegalia(renderOperator, awardedMedals)
    val rackDecorations = wornRegalia.ribbonRack
    val hangingMedals = wornRegalia.hangingMedals

    val leftCordDef = wornRegalia.leftCord
    val rightCordDef = wornRegalia.rightCord
    val neckMedalDef = wornRegalia.neckMedal
    val hasUnitCitation = renderOperator.tailorOverrides.showUnitCitation &&
        awardedMedals.any { it.tier == MedalTier.SUPREME || it.tier == MedalTier.VALOR }
    val combatPatch = UniformRegaliaRules.resolveCombatPatch(renderOperator)
    val marksmanshipTier = UniformRegaliaRules.resolveMarksmanshipTier(renderOperator, equippedGear)
    val overseasBars = UniformRegaliaRules.resolveOverseasBars(renderOperator)
    val awardDevices = (rackDecorations + hangingMedals)
        .distinctBy { it.id }
        .associate { medal ->
            medal.id to UniformRegaliaRules.resolveAwardDevices(renderOperator, medal.id, allCitations)
        }

    val equippedWeaponId = equippedGear.firstOrNull { it.slot == GearSlot.WEAPON }?.id
    val hasNvg = equippedGear.any { it.slot == GearSlot.OPTICS && it.id.contains("nvg", ignoreCase = true) }
    val hasArmor = equippedGear.any { it.slot == GearSlot.ARMOR }
    val hasHelmet = equippedGear.any { it.slot == GearSlot.HELMET }
    val hasWeapon = equippedWeaponId != null
    val skinTone = Color(0xFFD1A98E)
    val skinShadow = Color(0xFFA98067)
    val visorTone = Color(0xFF1A242F)

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
            .border(1.dp, borderCol, MaterialTheme.shapes.medium)
            .semantics {
                contentDescription = "${renderOperator.rank.code} ${renderOperator.callsign} ${inferredKit.label} preview"
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val tier = renderOperator.rank.tier

            // Subtle tactical radar/spotlight backdrop rings
            val gridColor = Color.White.copy(alpha = 0.04f)
            for (i in 1..4) {
                drawLine(gridColor, Offset(0f, h * i / 5f), Offset(w, h * i / 5f), 1f)
            }
            drawCircle(
                color = gridColor,
                radius = w * 0.36f,
                center = Offset(cx, h * 0.42f),
                style = Stroke(width = 1.5f)
            )
            if (inferredKit != WardrobeKit.FIELD) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(renderOperator.dressStyle.trimHex).copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(cx, h * 0.48f),
                        radius = w * 0.52f
                    ),
                    radius = w * 0.52f,
                    center = Offset(cx, h * 0.48f)
                )
            }

            when (inferredKit) {
                WardrobeKit.FIELD -> drawFieldTacticalFigure(
                    w = w,
                    h = h,
                    cx = cx,
                    operator = renderOperator,
                    wornRegalia = wornRegalia,
                    combatPatch = combatPatch,
                    isSquadLeader = isSquadLeader,
                    equippedWeaponId = equippedWeaponId,
                    skinTone = skinTone,
                    skinShadow = skinShadow,
                    visorTone = visorTone,
                    hasArmor = hasArmor,
                    hasHelmet = hasHelmet,
                    hasWeapon = hasWeapon,
                    hasNvg = hasNvg
                )
                WardrobeKit.BRAVOS -> drawBravosShortSleeveFigure(
                    w = w,
                    h = h,
                    cx = cx,
                    operator = renderOperator,
                    tier = tier,
                    skinTone = skinTone,
                    skinShadow = skinShadow,
                    rackDecorations = rackDecorations,
                    wornRegalia = wornRegalia,
                    leftCordDef = leftCordDef,
                    rightCordDef = rightCordDef,
                    hasUnitCitation = hasUnitCitation,
                    marksmanshipTier = marksmanshipTier,
                    showLeaderPin = isSquadLeader,
                    awardDevices = awardDevices,
                    textMeasurer = textMeasurer
                )
                WardrobeKit.ALPHAS, WardrobeKit.MESS -> drawDressUniformFigure(
                    w = w,
                    h = h,
                    cx = cx,
                    operator = renderOperator,
                    tier = tier,
                    skinTone = skinTone,
                    skinShadow = skinShadow,
                    rackDecorations = rackDecorations,
                    hangingMedals = hangingMedals,
                    wornRegalia = wornRegalia,
                    leftCordDef = if (inferredKit == WardrobeKit.MESS) null else leftCordDef,
                    rightCordDef = if (inferredKit == WardrobeKit.MESS) null else rightCordDef,
                    neckMedalDef = neckMedalDef,
                    hasUnitCitation = hasUnitCitation,
                    combatPatch = combatPatch,
                    marksmanshipTier = marksmanshipTier,
                    overseasBars = overseasBars,
                    showLeaderPin = isSquadLeader,
                    awardDevices = awardDevices,
                    textMeasurer = textMeasurer,
                    squads = squads,
                    kit = inferredKit
                )
            }
        }
    }
}

private fun DrawScope.drawDressUniformFigure(
    w: Float,
    h: Float,
    cx: Float,
    operator: Operator,
    tier: RankTier,
    skinTone: Color,
    skinShadow: Color,
    rackDecorations: List<MedalDefinition>,
    hangingMedals: List<MedalDefinition>,
    wornRegalia: TacticalCalculator.WornRegaliaSummary,
    leftCordDef: MedalDefinition?,
    rightCordDef: MedalDefinition?,
    neckMedalDef: MedalDefinition?,
    hasUnitCitation: Boolean,
    combatPatch: UnitPatchStyle,
    marksmanshipTier: MarksmanshipBadgeTier,
    overseasBars: Int,
    showLeaderPin: Boolean,
    awardDevices: Map<String, UniformRegaliaRules.AwardDeviceInfo>,
    textMeasurer: TextMeasurer,
    squads: List<TacticalSquad> = emptyList(),
    kit: WardrobeKit = WardrobeKit.ALPHAS
) {
    val style = operator.dressStyle
    val tunicCol = Color(style.tunicHex)
    val lapelCol = Color(style.lapelHex)
    val trimCol = Color(style.trimHex)
    val trouserStripeCol = Color(style.trouserStripeHex)
    val resolvedHeadgear = if (operator.headgearStyle.kind == HeadgearKind.BERET) {
        TacticalCalculator.defaultHeadgearForKit(kit, operator)
    } else {
        operator.headgearStyle
    }
    val headgearCol = Color(resolvedHeadgear.colorHex)
    val trouserBaseCol = when (style) {
        DressUniformStyle.ARMY_SERVICE_GREEN -> Color(0xFF938770)
        DressUniformStyle.HONOR_GUARD_IVORY, DressUniformStyle.FLAG_MESS_FORMAL -> Color(0xFF0E1522)
        else -> lapelCol.copy(alpha = 0.92f)
    }

    // Unified sleeve inner & outer contour functions so sleeves, armhole seams,
    // shoulder tabs, SSI unit shields, rank chevrons, and service stripes align cleanly.
    fun sleeveInnerX(sideSign: Float, yFrac: Float): Float {
        val x = when {
            yFrac <= 0.520f -> {
                val t = ((yFrac - 0.320f) / (0.520f - 0.320f)).coerceIn(0f, 1f)
                0.252f - t * 0.008f
            }
            else -> {
                val t = ((yFrac - 0.520f) / (0.895f - 0.520f)).coerceIn(0f, 1f)
                0.244f + t * 0.052f
            }
        }
        return cx + sideSign * w * x
    }

    fun sleeveOuterX(sideSign: Float, yFrac: Float): Float {
        val x = when {
            yFrac <= 0.455f -> {
                val t = ((yFrac - 0.314f) / (0.455f - 0.314f)).coerceIn(0f, 1f)
                val ease = sin(t * (PI.toFloat() * 0.5f))
                0.280f + ease * 0.106f
            }
            else -> {
                val t = ((yFrac - 0.455f) / (0.895f - 0.455f)).coerceIn(0f, 1f)
                0.386f + t * 0.022f
            }
        }
        return cx + sideSign * w * x
    }

    // Helper returning point across sleeve cross-section: u = 0 (inner seam) -> 1 (outer silhouette)
    fun sleeveFaceX(sideSign: Float, yFrac: Float, u: Float): Float {
        val inner = sleeveInnerX(sideSign, yFrac)
        val outer = sleeveOuterX(sideSign, yFrac)
        return inner + (outer - inner) * u
    }

    fun buildSleevePath(sideSign: Float): Path {
        val steps = 14
        val topF = 0.314f
        val botF = 0.895f
        return Path().apply {
            moveTo(sleeveInnerX(sideSign, topF), h * topF)
            for (i in 0..steps) {
                val f = topF + (botF - topF) * (i / steps.toFloat())
                lineTo(sleeveOuterX(sideSign, f), h * f)
            }
            for (i in steps downTo 0) {
                val f = topF + (botF - topF) * (i / steps.toFloat())
                lineTo(sleeveInnerX(sideSign, f), h * f)
            }
            close()
        }
    }

    // 1. Dress Trousers base
    val trouserTop = if (style.isCutawayMess) h * 0.768f else h * 0.875f
    val waistHalf = if (tier.isOfficer) w * 0.244f else w * 0.258f
    val legPath = Path().apply {
        moveTo(cx - waistHalf, trouserTop)
        lineTo(cx + waistHalf, trouserTop)
        lineTo(cx + w * 0.272f, h * 0.972f)
        lineTo(cx + w * 0.026f, h * 0.972f)
        lineTo(cx, h * 0.895f)
        lineTo(cx - w * 0.026f, h * 0.972f)
        lineTo(cx - w * 0.272f, h * 0.972f)
        close()
    }
    drawPath(legPath, color = trouserBaseCol)

    val stripeWidth = if (tier == RankTier.FLAG_OFFICER) (w * 0.020f).coerceAtLeast(2.8f) else (w * 0.013f).coerceAtLeast(1.8f)
    drawLine(
        color = trouserStripeCol,
        start = Offset(cx - waistHalf * 0.96f, trouserTop + h * 0.008f),
        end = Offset(cx - w * 0.264f, h * 0.968f),
        strokeWidth = stripeWidth
    )
    drawLine(
        color = trouserStripeCol,
        start = Offset(cx + waistHalf * 0.96f, trouserTop + h * 0.008f),
        end = Offset(cx + w * 0.264f, h * 0.968f),
        strokeWidth = stripeWidth
    )

    // 2. Broad Anatomical Dress Sleeves with Cylindrical Arm Shading
    val sleevePathL = buildSleevePath(-1f)
    val sleevePathR = buildSleevePath(1f)
    drawPath(sleevePathL, color = tunicCol)
    drawPath(sleevePathR, color = tunicCol)
    drawPath(
        sleevePathL,
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Black.copy(alpha = 0.24f), Color.White.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.14f)),
            startX = cx - w * 0.408f,
            endX = cx - w * 0.244f
        )
    )
    drawPath(
        sleevePathR,
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Black.copy(alpha = 0.14f), Color.White.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.24f)),
            startX = cx + w * 0.244f,
            endX = cx + w * 0.408f
        )
    )

    // 3. Tailored Coat Body (Cutaway Mess vs Full Tunic)
    if (style.isCutawayMess) {
        val vestPath = Path().apply {
            moveTo(cx - w * 0.118f, h * 0.296f)
            lineTo(cx + w * 0.118f, h * 0.296f)
            lineTo(cx + w * 0.176f, h * 0.790f)
            lineTo(cx - w * 0.176f, h * 0.790f)
            close()
        }
        drawPath(vestPath, color = Color(0xFFF1F4F8))
        val cummCol = if (style == DressUniformStyle.HONOR_GUARD_IVORY || style == DressUniformStyle.FLAG_MESS_FORMAL) Color(0xFF8C2525) else lapelCol
        drawRect(
            color = cummCol,
            topLeft = Offset(cx - w * 0.196f, h * 0.722f),
            size = Size(w * 0.392f, h * 0.068f)
        )
        drawLine(trimCol.copy(alpha = 0.75f), Offset(cx - w * 0.186f, h * 0.744f), Offset(cx + w * 0.186f, h * 0.744f), strokeWidth = 1f)
        drawLine(trimCol.copy(alpha = 0.75f), Offset(cx - w * 0.186f, h * 0.768f), Offset(cx + w * 0.186f, h * 0.768f), strokeWidth = 1f)

        val messLeft = Path().apply {
            moveTo(cx - w * 0.254f, h * 0.326f)
            lineTo(cx - w * 0.146f, h * 0.294f)
            lineTo(cx - w * 0.056f, h * 0.565f)
            lineTo(cx - w * 0.175f, h * 0.730f)
            lineTo(cx - w * 0.242f, h * 0.730f)
            close()
        }
        val messRight = Path().apply {
            moveTo(cx + w * 0.254f, h * 0.326f)
            lineTo(cx + w * 0.146f, h * 0.294f)
            lineTo(cx + w * 0.056f, h * 0.565f)
            lineTo(cx + w * 0.175f, h * 0.730f)
            lineTo(cx + w * 0.242f, h * 0.730f)
            close()
        }
        drawPath(messLeft, color = tunicCol)
        drawPath(messRight, color = tunicCol)
        drawLine(trimCol, Offset(cx - w * 0.056f, h * 0.565f), Offset(cx - w * 0.175f, h * 0.730f), strokeWidth = 1.6f)
        drawLine(trimCol, Offset(cx + w * 0.056f, h * 0.565f), Offset(cx + w * 0.175f, h * 0.730f), strokeWidth = 1.6f)
        for (i in 0..2) {
            drawCircle(trimCol, radius = w * 0.011f, center = Offset(cx, h * (0.435f + i * 0.064f)))
        }
        val bowL = Path().apply {
            moveTo(cx, h * 0.324f)
            lineTo(cx - w * 0.046f, h * 0.308f)
            lineTo(cx - w * 0.046f, h * 0.340f)
            close()
        }
        val bowR = Path().apply {
            moveTo(cx, h * 0.324f)
            lineTo(cx + w * 0.046f, h * 0.308f)
            lineTo(cx + w * 0.046f, h * 0.340f)
            close()
        }
        drawPath(bowL, color = Color(0xFF10141C))
        drawPath(bowR, color = Color(0xFF10141C))
        drawCircle(Color(0xFF181E29), radius = w * 0.013f, center = Offset(cx, h * 0.324f))
    } else {
        // Tailored Full Dress Tunic with crisp armhole seam separation
        val coatPath = Path().apply {
            moveTo(cx - w * 0.148f, h * 0.294f)
            lineTo(cx + w * 0.148f, h * 0.294f)
            lineTo(cx + w * 0.254f, h * 0.326f)
            lineTo(cx + w * 0.244f, h * 0.520f)
            lineTo(cx + w * 0.238f, h * 0.750f)
            lineTo(cx + w * 0.268f, h * 0.905f)
            lineTo(cx - w * 0.268f, h * 0.905f)
            lineTo(cx - w * 0.238f, h * 0.750f)
            lineTo(cx - w * 0.244f, h * 0.520f)
            lineTo(cx - w * 0.254f, h * 0.326f)
            close()
        }
        drawPath(coatPath, color = tunicCol)
        drawPath(
            coatPath,
            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent, Color.Black.copy(alpha = 0.18f)))
        )

        // Armhole shoulder seam lines cleanly framing the chest against sleeves
        drawLine(Color.Black.copy(alpha = 0.26f), Offset(cx - w * 0.254f, h * 0.326f), Offset(cx - w * 0.244f, h * 0.520f), strokeWidth = 1.4f)
        drawLine(Color.Black.copy(alpha = 0.26f), Offset(cx + w * 0.254f, h * 0.326f), Offset(cx + w * 0.244f, h * 0.520f), strokeWidth = 1.4f)

        // Dress Shirt V-opening + Four-in-Hand Tie + Brass Tie Clasp
        val shirtPath = Path().apply {
            moveTo(cx - w * 0.072f, h * 0.294f)
            lineTo(cx, h * 0.532f)
            lineTo(cx + w * 0.072f, h * 0.294f)
            close()
        }
        drawPath(shirtPath, color = if (style == DressUniformStyle.ARMY_SERVICE_GREEN) Color(0xFFE7E0CB) else Color(0xFFF0F4FA))
        val tieColor = if (style == DressUniformStyle.ARMY_SERVICE_GREEN) Color(0xFF28321F) else Color(0xFF111621)
        val tiePath = Path().apply {
            moveTo(cx - w * 0.017f, h * 0.312f)
            lineTo(cx + w * 0.017f, h * 0.312f)
            lineTo(cx + w * 0.021f, h * 0.485f)
            lineTo(cx, h * 0.526f)
            lineTo(cx - w * 0.021f, h * 0.485f)
            close()
        }
        drawPath(tiePath, color = tieColor)
        drawLine(trimCol, Offset(cx - w * 0.017f, h * 0.440f), Offset(cx + w * 0.017f, h * 0.440f), strokeWidth = 1.6f)

        // Tailored Upper Breast Pockets (centered cleanly at cx ± 0.168f)
        val pocketOutline = lapelCol.copy(alpha = 0.72f)
        val pocketFill = lapelCol.copy(alpha = 0.22f)
        val flapHighlight = trimCol.copy(alpha = 0.35f)
        val leftPocketX = cx + w * 0.104f
        val rightPocketX = cx - w * 0.232f
        val pocketW = w * 0.128f
        val pocketTopY = h * CHEST_POCKET_TOP_FRACTION
        val pocketH = h * 0.125f
        val flapH = h * 0.032f

        listOf(rightPocketX, leftPocketX).forEach { px ->
            drawRoundRect(pocketFill, Offset(px, pocketTopY), Size(pocketW, pocketH), CornerRadius(3f, 3f))
            drawRoundRect(pocketOutline, Offset(px, pocketTopY), Size(pocketW, pocketH), CornerRadius(3f, 3f), style = Stroke(1.1f))
            drawRoundRect(tunicCol, Offset(px - 1f, pocketTopY), Size(pocketW + 2f, flapH), CornerRadius(2f, 2f))
            drawRoundRect(pocketOutline, Offset(px - 1f, pocketTopY), Size(pocketW + 2f, flapH), CornerRadius(2f, 2f), style = Stroke(1.0f))
            drawCircle(trimCol, radius = w * 0.0058f, center = Offset(px + pocketW / 2f, pocketTopY + flapH * 0.64f))
            drawLine(flapHighlight, Offset(px + 2f, pocketTopY + flapH), Offset(px + pocketW - 2f, pocketTopY + flapH), strokeWidth = 0.8f)
        }

        // Lower Hip Pocket Flaps on Heritage Coats
        if (style == DressUniformStyle.ARMY_SERVICE_GREEN || style == DressUniformStyle.ARMY_DRESS_BLUES) {
            val flapStroke = trimCol.copy(alpha = 0.5f)
            drawRoundRect(lapelCol.copy(alpha = 0.35f), Offset(cx - w * 0.214f, h * 0.818f), Size(w * 0.120f, h * 0.072f), CornerRadius(3f, 3f))
            drawRoundRect(lapelCol.copy(alpha = 0.35f), Offset(cx + w * 0.094f, h * 0.818f), Size(w * 0.120f, h * 0.072f), CornerRadius(3f, 3f))
            drawLine(flapStroke, Offset(cx - w * 0.214f, h * 0.824f), Offset(cx - w * 0.094f, h * 0.824f), strokeWidth = 1.1f)
            drawLine(flapStroke, Offset(cx + w * 0.094f, h * 0.824f), Offset(cx + w * 0.214f, h * 0.824f), strokeWidth = 1.1f)
        }
    }

    // 4. Coat Lapels + Distinct Branch Collar Brass
    if (style.isCutawayMess) {
        val messLapelL = Path().apply {
            moveTo(cx - w * 0.146f, h * 0.294f)
            lineTo(cx - w * 0.056f, h * 0.565f)
            lineTo(cx - w * 0.118f, h * 0.455f)
            close()
        }
        val messLapelR = Path().apply {
            moveTo(cx + w * 0.146f, h * 0.294f)
            lineTo(cx + w * 0.056f, h * 0.565f)
            lineTo(cx + w * 0.118f, h * 0.455f)
            close()
        }
        drawPath(messLapelL, color = lapelCol)
        drawPath(messLapelR, color = lapelCol)
        if (tier.isOfficer) {
            drawLine(trimCol.copy(alpha = 0.65f), Offset(cx - w * 0.146f, h * 0.294f), Offset(cx - w * 0.118f, h * 0.455f), strokeWidth = 1.1f)
            drawLine(trimCol.copy(alpha = 0.65f), Offset(cx + w * 0.146f, h * 0.294f), Offset(cx + w * 0.118f, h * 0.455f), strokeWidth = 1.1f)
        }
    } else {
        val leftLapel = Path().apply {
            moveTo(cx - w * 0.110f, h * 0.294f)
            lineTo(cx - w * 0.008f, h * 0.532f)
            lineTo(cx - w * 0.088f, h * 0.440f)
            close()
        }
        val rightLapel = Path().apply {
            moveTo(cx + w * 0.110f, h * 0.294f)
            lineTo(cx + w * 0.008f, h * 0.532f)
            lineTo(cx + w * 0.088f, h * 0.440f)
            close()
        }
        drawPath(leftLapel, color = lapelCol)
        drawPath(rightLapel, color = lapelCol)
        if (tier.isOfficer) {
            drawLine(trimCol.copy(alpha = 0.65f), Offset(cx - w * 0.110f, h * 0.294f), Offset(cx - w * 0.088f, h * 0.440f), strokeWidth = 1.1f)
            drawLine(trimCol.copy(alpha = 0.65f), Offset(cx + w * 0.110f, h * 0.294f), Offset(cx + w * 0.088f, h * 0.440f), strokeWidth = 1.1f)
        }
    }
    drawBranchCollarInsignia(cx, h, w, operator.specialty, tier, trimCol)

    // 5. Sovereign Star Neck Medallion
    if (neckMedalDef != null) {
        val neckPath = Path().apply {
            moveTo(cx - w * 0.054f, h * 0.298f)
            lineTo(cx, h * 0.372f)
            lineTo(cx + w * 0.054f, h * 0.298f)
        }
        drawPath(neckPath, color = Color(0xFF2B60A8), style = Stroke(width = (w * 0.019f).coerceAtLeast(2.6f), cap = StrokeCap.Round))
        drawDetailedMedalPendant(neckMedalDef.id, neckMedalDef.tier, Offset(cx, h * 0.396f), w * 0.030f)
    }

    // 6. Shoulder Boards / Epaulets + Distinct Officer & Flag Officer Rank Insignia
    val epauletColor = when {
        tier == RankTier.FLAG_OFFICER -> trimCol
        tier == RankTier.OFFICER && style == DressUniformStyle.HONOR_GUARD_IVORY -> Color(0xFF0C1526)
        tier == RankTier.OFFICER -> lapelCol
        else -> tunicCol
    }
    val epauletBorder = if (tier.isOfficer) trimCol else lapelCol
    val isFieldGrade = operator.rank.isFieldGradeOfficer
    val epTop = if (tier == RankTier.OFFICER) h * 0.296f else h * 0.298f
    val epHeight = if (tier == RankTier.OFFICER) h * 0.036f else h * 0.032f
    val epWidth = if (tier == RankTier.OFFICER) w * 0.114f else w * 0.110f
    val epLeftX = cx - w * 0.146f - epWidth
    val epRightX = cx + w * 0.146f
    val epStroke = when {
        tier == RankTier.FLAG_OFFICER || isFieldGrade -> 2.0f
        tier == RankTier.OFFICER -> 1.6f
        else -> 1.1f
    }
    drawRoundRect(epauletColor, Offset(epLeftX, epTop), Size(epWidth, epHeight), CornerRadius(4f, 4f))
    drawRoundRect(epauletBorder, Offset(epLeftX, epTop), Size(epWidth, epHeight), CornerRadius(4f, 4f), style = Stroke(epStroke))
    drawRoundRect(epauletColor, Offset(epRightX, epTop), Size(epWidth, epHeight), CornerRadius(4f, 4f))
    drawRoundRect(epauletBorder, Offset(epRightX, epTop), Size(epWidth, epHeight), CornerRadius(4f, 4f), style = Stroke(epStroke))

    if (tier == RankTier.FLAG_OFFICER) {
        val starCount = operator.rank.insigniaCount.coerceIn(1, 4)
        repeat(starCount) { s ->
            val offset = (s - (starCount - 1) / 2f) * (w * 0.023f)
            drawStar(Offset(cx - w * 0.201f + offset, h * 0.314f), w * 0.013f, w * 0.0055f, Color(0xFF14181F))
            drawStar(Offset(cx + w * 0.201f + offset, h * 0.314f), w * 0.013f, w * 0.0055f, Color(0xFF14181F))
        }
    } else if (tier == RankTier.OFFICER) {
        val epCenterY = epTop + epHeight * 0.5f
        val leftCenter = Offset(epLeftX + epWidth * 0.5f, epCenterY)
        val rightCenter = Offset(epRightX + epWidth * 0.5f, epCenterY)
        drawOfficerRankDevice(
            rank = operator.rank,
            center = leftCenter,
            boxW = epWidth * 0.68f,
            boxH = epHeight * 0.78f,
            outlineColor = Color(0xFF0A101B)
        )
        drawOfficerRankDevice(
            rank = operator.rank,
            center = rightCenter,
            boxW = epWidth * 0.68f,
            boxH = epHeight * 0.78f,
            outlineColor = Color(0xFF0A101B)
        )
    } else {
        drawCircle(trimCol, radius = w * 0.010f, center = Offset(cx - w * 0.201f, h * 0.314f))
        drawCircle(trimCol, radius = w * 0.010f, center = Offset(cx + w * 0.201f, h * 0.314f))
    }

    // 7. Crisp Wrapped Shoulder Tabs + Directorate Heraldic SSI Shield on Outer Upper Deltoid (Wearer's Left Sleeve = +1f)
    wornRegalia.tabSlot1?.let { tab1 ->
        drawWrappedShoulderTab(
            sideSign = 1f,
            topFrac = 0.344f,
            tabColor = Color(tab1.insigniaColorHex),
            sleeveFaceX = ::sleeveFaceX,
            h = h
        )
    }
    wornRegalia.tabSlot2?.let { tab2 ->
        val slot2Frac = if (wornRegalia.tabSlot1 != null) 0.369f else 0.344f
        drawWrappedShoulderTab(
            sideSign = 1f,
            topFrac = slot2Frac,
            tabColor = Color(tab2.insigniaColorHex),
            sleeveFaceX = ::sleeveFaceX,
            h = h
        )
    }

    val wornTabCount = listOfNotNull(wornRegalia.tabSlot1, wornRegalia.tabSlot2).size
    val ssiTopFrac = 0.350f + wornTabCount * 0.026f
    val chosenPatch = operator.tailorOverrides.unitPatch
    if (chosenPatch != UnitPatchStyle.NONE) {
        drawWrappedUnitShoulderPatch(
            sideSign = 1f,
            topFrac = ssiTopFrac,
            patchStyle = chosenPatch,
            specialty = operator.specialty,
            trimCol = trimCol,
            accentCol = Color(operator.dressStyle.trouserStripeHex),
            sleeveFaceX = ::sleeveFaceX,
            w = w,
            h = h
        )
    }

    // Wartime Combat Patch (SSI-FWTS) on Wearer's Right Sleeve (sideSign = -1f)
    if (combatPatch != UnitPatchStyle.NONE) {
        drawWrappedUnitShoulderPatch(
            sideSign = -1f,
            topFrac = 0.352f,
            patchStyle = combatPatch,
            specialty = operator.specialty,
            trimCol = trimCol,
            accentCol = Color(operator.dressStyle.trouserStripeHex),
            sleeveFaceX = ::sleeveFaceX,
            w = w,
            h = h
        )
    }

    // Crisp Enlisted & NCO Sleeve Rank Chevrons / Specialist Shield / Rockers (both mid-sleeves, y = 0.495f)
    if (!tier.isOfficer && operator.rank != OperatorRank.PVT) {
        drawWrappedSleeveRankInsignia(
            sideSign = -1f,
            rank = operator.rank,
            tunicCol = tunicCol,
            lapelCol = lapelCol,
            trimCol = trimCol,
            sleeveFaceX = ::sleeveFaceX,
            w = w,
            h = h
        )
        drawWrappedSleeveRankInsignia(
            sideSign = 1f,
            rank = operator.rank,
            tunicCol = tunicCol,
            lapelCol = lapelCol,
            trimCol = trimCol,
            sleeveFaceX = ::sleeveFaceX,
            w = w,
            h = h
        )
    }

    // 8. Braided Shoulder Cords & General Staff Aiguillettes (Left = Branch/School, Right = Staff/Valor)
    drawShoulderCords(
        w = w,
        h = h,
        cx = cx,
        leftCordDef = leftCordDef,
        rightCordDef = rightCordDef,
        trimCol = trimCol
    )

    // 9. Wearer's Right Chest: Unit Citation Frame + Squad Leader Pin (above nameplate) + Engraved Nameplate
    val rightChestCenterX = cx - w * 0.168f
    drawEngravedNameplateAndLeaderPin(
        rightChestCenterX = rightChestCenterX,
        w = w,
        h = h,
        trimCol = trimCol,
        callsign = operator.callsign,
        hasUnitCitation = hasUnitCitation,
        showLeaderPin = showLeaderPin,
        textMeasurer = textMeasurer
    )

    // Center Brass Coat Buttons
    if (!style.isCutawayMess) {
        for (i in 0..2) {
            drawCircle(trimCol, radius = w * 0.012f, center = Offset(cx, h * (0.585f + i * 0.072f)))
        }
    }

    // 10. Officer Sam Browne Cross-Strap & Ceremonial Waist Sash / Dress Belt (HQ Staff & Squad Leaders only)
    val sashAuthorized = operator.tailorOverrides.freeTailorOverride ||
        TacticalCalculator.evaluateDressSashEligibility(operator, squads).eligible
    val wearSash = operator.hasDressSash && sashAuthorized && !style.isCutawayMess
    if (tier.isOfficer && wearSash) {
        drawLine(
            color = if (tier == RankTier.FLAG_OFFICER) Color(0xFF7D232A) else Color(0xFF2D1E14),
            start = Offset(cx + w * 0.155f, h * 0.325f),
            end = Offset(cx - w * 0.195f, h * 0.768f),
            strokeWidth = (w * 0.021f).coerceAtLeast(2.8f)
        )
        drawCircle(trimCol, radius = w * 0.012f, center = Offset(cx + w * 0.142f, h * 0.344f))
    }

    if (!style.isCutawayMess) {
        if (wearSash) {
            val sashColor = if (tier == RankTier.FLAG_OFFICER) Color(0xFF8C2525) else trimCol.copy(alpha = 0.88f)
            drawRect(sashColor, Offset(cx - w * 0.238f, h * 0.742f), Size(w * 0.476f, h * 0.054f))
        }
        drawRect(lapelCol, Offset(cx - w * 0.238f, h * 0.752f), Size(w * 0.476f, h * 0.036f))
        drawRoundRect(trimCol, Offset(cx - w * 0.042f, h * 0.747f), Size(w * 0.084f, h * 0.046f), CornerRadius(3f, 3f))
    }

    // 11. Tier-Differentiated Sleeve Cuff Oak-Leaf Braid + Mess Officer Sleeve Knots + Service Stripes
    fun drawCuffLine(sideSign: Float, yFrac: Float, color: Color, strokeWidth: Float) {
        drawLine(
            color = color,
            start = Offset(sleeveInnerX(sideSign, yFrac) + sideSign * 1f, h * yFrac),
            end = Offset(sleeveOuterX(sideSign, yFrac) - sideSign * 1f, h * yFrac),
            strokeWidth = strokeWidth
        )
    }
    when (tier) {
        RankTier.FLAG_OFFICER -> {
            drawCuffLine(-1f, 0.850f, trimCol, 4.0f)
            drawCuffLine(-1f, 0.875f, trimCol, 2.4f)
            drawCuffLine(1f, 0.850f, trimCol, 4.0f)
            drawCuffLine(1f, 0.875f, trimCol, 2.4f)
        }
        RankTier.OFFICER -> {
            if (operator.rank.isFieldGradeOfficer) {
                drawCuffLine(-1f, 0.850f, trimCol.copy(alpha = 0.88f), 1.5f)
                drawCuffLine(-1f, 0.868f, trimCol, 3.2f)
                drawCuffLine(1f, 0.850f, trimCol.copy(alpha = 0.88f), 1.5f)
                drawCuffLine(1f, 0.868f, trimCol, 3.2f)
            } else {
                drawCuffLine(-1f, 0.864f, trimCol, 2.6f)
                drawCuffLine(1f, 0.864f, trimCol, 2.6f)
            }
        }
        RankTier.SNCO -> {
            drawCuffLine(-1f, 0.860f, trimCol.copy(alpha = 0.90f), 2.3f)
            drawCuffLine(1f, 0.860f, trimCol.copy(alpha = 0.90f), 2.3f)
            drawCuffLine(-1f, 0.876f, trimCol.copy(alpha = 0.75f), 1.4f)
            drawCuffLine(1f, 0.876f, trimCol.copy(alpha = 0.75f), 1.4f)
        }
        RankTier.NCO -> {
            drawCuffLine(-1f, 0.872f, trimCol.copy(alpha = 0.80f), 1.9f)
            drawCuffLine(1f, 0.872f, trimCol.copy(alpha = 0.80f), 1.9f)
        }
        RankTier.SOLDIER -> Unit
    }
    if (style.isCutawayMess && tier.isOfficer) {
        // Formal Austrian sleeve quatrefoil knot above the cuff band on Officer & General Mess jackets
        for (sideSign in listOf(-1f, 1f)) {
            val knotX = sleeveFaceX(sideSign, 0.834f, 0.54f)
            drawCircle(trimCol, radius = w * 0.015f, center = Offset(knotX, h * 0.832f), style = Stroke(1.6f))
            drawCircle(trimCol, radius = w * 0.008f, center = Offset(knotX, h * 0.816f), style = Stroke(1.3f))
        }
    }

    val stripeCount = if (operator.tailorOverrides.showServiceStripes) {
        (operator.missionsCompleted / 2).coerceIn(0, 4)
    } else {
        0
    }
    if (stripeCount > 0) {
        drawWrappedServiceStripes(
            sideSign = 1f,
            stripeCount = stripeCount,
            lapelCol = lapelCol,
            trimCol = trimCol,
            sleeveFaceX = ::sleeveFaceX,
            w = w,
            h = h
        )
    }

    if (overseasBars > 0) {
        drawWrappedOverseasBars(
            sideSign = -1f,
            barCount = overseasBars,
            lapelCol = lapelCol,
            trimCol = trimCol,
            sleeveFaceX = ::sleeveFaceX,
            w = w,
            h = h
        )
    }

    val cuffShirtCol = when {
        style.isCutawayMess -> Color(0xFFF1F4F8)
        style == DressUniformStyle.ARMY_SERVICE_GREEN -> Color(0xFFE7E0CB)
        else -> Color(0xFFF0F4FA)
    }
    for (sideSign in listOf(-1f, 1f)) {
        val sleeveLeft = minOf(sleeveInnerX(sideSign, 0.895f), sleeveOuterX(sideSign, 0.895f))
        val sleeveW = kotlin.math.abs(sleeveOuterX(sideSign, 0.895f) - sleeveInnerX(sideSign, 0.895f))
        val cuffX = sleeveLeft + sleeveW * 0.06f
        val cuffW = sleeveW * 0.88f
        val handX = sleeveLeft + sleeveW * 0.10f
        val handW = sleeveW * 0.80f
        val handTopY = h * 0.899f
        val handH = h * 0.050f
        val thumbW = handW * 0.28f
        val thumbX = if (sideSign < 0f) {
            handX + handW - thumbW * 0.78f
        } else {
            handX - thumbW * 0.22f
        }

        // Crisp dress shirt cuff peeking just beneath the coat sleeve hem
        drawRoundRect(
            color = cuffShirtCol,
            topLeft = Offset(cuffX, h * 0.893f),
            size = Size(cuffW, h * 0.010f),
            cornerRadius = CornerRadius(2f, 2f)
        )

        if (operator.hasWhiteGloves) {
            val gloveBase = Color(0xFFF0F2F5)
            val gloveSeam = Color(0xFFC5CEDB)
            drawRoundRect(
                color = gloveBase,
                topLeft = Offset( sleeveLeft + sleeveW * 0.08f, h * 0.896f),
                size = Size(sleeveW * 0.84f, h * 0.052f),
                cornerRadius = CornerRadius(6f, 6f)
            )
            drawRoundRect(
                color = Color(0xFFF7F9FC),
                topLeft = Offset(thumbX, h * 0.903f),
                size = Size(thumbW, h * 0.033f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawRoundRect(
                color = gloveSeam,
                topLeft = Offset(thumbX, h * 0.903f),
                size = Size(thumbW, h * 0.033f),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = 0.9f)
            )
            for (rib in -1..1) {
                val rx = handX + handW * (0.50f + rib * 0.14f)
                drawLine(
                    color = gloveSeam,
                    start = Offset(rx, h * 0.906f),
                    end = Offset(rx, h * 0.932f),
                    strokeWidth = 0.9f,
                    cap = StrokeCap.Round
                )
            }
        } else {
            // Natural bare hands at attention matching the soldier's skin tone & shading
            drawRoundRect(
                color = skinTone,
                topLeft = Offset(handX, handTopY),
                size = Size(handW, handH),
                cornerRadius = CornerRadius(6f, 6f)
            )
            // Subtle wrist shadow beneath shirt cuff
            drawRect(
                color = skinShadow.copy(alpha = 0.55f),
                topLeft = Offset(handX + 1f, handTopY),
                size = Size((handW - 2f).coerceAtLeast(1f), h * 0.007f)
            )
            // Tucked thumb along trouser seam at attention
            drawRoundRect(
                color = skinTone,
                topLeft = Offset(thumbX, handTopY + handH * 0.12f),
                size = Size(thumbW, handH * 0.64f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawRoundRect(
                color = skinShadow.copy(alpha = 0.72f),
                topLeft = Offset(thumbX, handTopY + handH * 0.12f),
                size = Size(thumbW, handH * 0.64f),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = 1.0f)
            )
            // Subtle curled finger creases & crisp hand contour
            for (finger in 1..2) {
                val fx = handX + handW * (finger / 3f)
                drawLine(
                    color = skinShadow.copy(alpha = 0.58f),
                    start = Offset(fx, handTopY + handH * 0.52f),
                    end = Offset(fx, handTopY + handH * 0.94f),
                    strokeWidth = 1.0f,
                    cap = StrokeCap.Round
                )
            }
            drawRoundRect(
                color = skinShadow.copy(alpha = 0.60f),
                topLeft = Offset(handX, handTopY),
                size = Size(handW, handH),
                cornerRadius = CornerRadius(6f, 6f),
                style = Stroke(width = 1.0f)
            )
        }
    }

    // 12. Wearer's Left Chest: Mess Miniature Medals, Full Hanging Medals, Ribbon Rack, OR Clean Tunic
    val leftChestCenterX = cx + w * 0.168f
    var topDecorationY = h * (0.528f - CHEST_LIFT)
    if (operator.dressDisplayMode == DressDisplayMode.CLEAN) {
        // Clean tunic chest: no hanging medals or ribbon rack mounted
        topDecorationY = h * (0.528f - CHEST_LIFT)
    } else if (style.isCutawayMess && (hangingMedals.isNotEmpty() || rackDecorations.isNotEmpty())) {
        // Cutaway Mess Dress wears compact anodized Miniature Medals pinned on the left lapel/chest
        val miniAwards = (hangingMedals + rackDecorations).distinctBy { it.id }.take(6)
        val topRowCount = if (miniAwards.size <= 3) miniAwards.size else 3
        val miniRows = listOfNotNull(
            miniAwards.take(topRowCount),
            miniAwards.drop(topRowCount).takeIf { it.isNotEmpty() }
        )
        val medW = w * 0.026f
        val ribH = h * 0.022f
        val gapX = w * 0.004f
        val medRadius = medW * 0.44f
        val broochH = h * 0.0035f
        val linkH = h * 0.004f
        val medalTotalH = broochH + ribH + linkH + (medRadius * 2f)
        val rowStepY = medalTotalH + h * 0.008f
        val startTopY = h * (0.468f - CHEST_LIFT)
        topDecorationY = startTopY

        miniRows.forEachIndexed { rowIdx, rowItems ->
            val rowWidth = rowItems.size * medW + (rowItems.size - 1) * gapX
            val rowStartX = leftChestCenterX - rowWidth / 2f
            val my = startTopY + rowIdx * rowStepY
            drawRect(trimCol, Offset(rowStartX - 2f, my), Size(rowWidth + 4f, broochH))
            rowItems.forEachIndexed { colIdx, medal ->
                val mx = rowStartX + colIdx * (medW + gapX)
                val ribTopY = my + broochH
                val stripes = medal.ribbonStripeHexes.size.coerceAtLeast(1)
                val sw = medW / stripes
                medal.ribbonStripeHexes.forEachIndexed { sIdx, hex ->
                    drawRect(Color(hex), Offset(mx + sIdx * sw, ribTopY), Size(sw + 0.5f, ribH))
                }
                drawRect(trimCol, Offset(mx, ribTopY), Size(medW, ribH), style = Stroke(0.8f))
                val linkTopY = ribTopY + ribH
                val pendantCenterY = linkTopY + linkH + medRadius * 0.90f
                drawLine(
                    trimCol,
                    Offset(mx + medW / 2f, linkTopY),
                    Offset(mx + medW / 2f, pendantCenterY - medRadius * 0.72f),
                    strokeWidth = 1.1f
                )
                drawDetailedMedalPendant(
                    medalId = medal.id,
                    tier = medal.tier,
                    center = Offset(mx + medW / 2f, pendantCenterY),
                    radius = medRadius
                )
            }
        }
    } else if (operator.dressDisplayMode == DressDisplayMode.FULL_MEDALS && hangingMedals.isNotEmpty()) {
        val displayHanging = hangingMedals.take(TacticalCalculator.MAX_WORN_HANGING_MEDALS)
        val maxCols = displayHanging.size.coerceIn(1, TacticalCalculator.MAX_WORN_HANGING_MEDALS)
        val medW = if (maxCols <= 3) w * 0.040f else w * 0.032f
        val ribH = if (maxCols <= 3) h * 0.034f else h * 0.030f
        val gapX = if (maxCols <= 3) w * 0.006f else w * 0.004f
        val medRadius = medW * 0.45f
        val broochH = h * 0.005f
        val linkH = h * 0.006f
        val medalTotalH = broochH + ribH + linkH + (medRadius * 2f)
        val rowStepY = medalTotalH * 0.92f

        val totalRows = 1
        val bottomRowBroochY = h * (0.556f - CHEST_LIFT) - medalTotalH
        val startTopY = bottomRowBroochY - (totalRows - 1) * rowStepY
        topDecorationY = startTopY

        displayHanging.forEachIndexed { idx, medal ->
            val row = 0
            val itemsInRow = displayHanging.size
            val col = idx
            val rowWidth = itemsInRow * medW + (itemsInRow - 1) * gapX
            val rowStartX = leftChestCenterX - rowWidth / 2f
            val mx = rowStartX + col * (medW + gapX)
            val my = startTopY + row * rowStepY

            drawRect(trimCol, Offset(mx - 1f, my), Size(medW + 2f, broochH))

            val ribTopY = my + broochH
            val stripes = medal.ribbonStripeHexes.size.coerceAtLeast(1)
            val sw = medW / stripes
            medal.ribbonStripeHexes.forEachIndexed { sIdx, hex ->
                drawRect(Color(hex), Offset(mx + sIdx * sw, ribTopY), Size(sw + 0.5f, ribH))
            }
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Black.copy(alpha = 0.24f))),
                topLeft = Offset(mx, ribTopY),
                size = Size(medW, ribH)
            )
            drawRect(trimCol, Offset(mx, ribTopY), Size(medW, ribH), style = Stroke(0.9f))
            awardDevices[medal.id]?.let { dev ->
                if (dev.hasAnyDevice) {
                    drawRibbonDeviceOverlay(
                        rectTopLeft = Offset(mx, ribTopY + ribH * 0.32f),
                        rectSize = Size(medW, ribH * 0.44f),
                        deviceInfo = dev
                    )
                }
            }

            val linkTopY = ribTopY + ribH
            val pendantCenterY = linkTopY + linkH + medRadius * 0.92f
            drawLine(
                trimCol,
                Offset(mx + medW / 2f, linkTopY),
                Offset(mx + medW / 2f, pendantCenterY - medRadius * 0.72f),
                strokeWidth = 1.4f
            )
            drawDetailedMedalPendant(
                medalId = medal.id,
                tier = medal.tier,
                center = Offset(mx + medW / 2f, pendantCenterY),
                radius = medRadius
            )
        }
    } else if (rackDecorations.isNotEmpty()) {
        val rackCols = 3
        val ribW = w * 0.045f
        val ribH = h * 0.019f
        val gapX = w * 0.0035f
        val gapY = h * 0.003f
        val totalRackW = rackCols * ribW + (rackCols - 1) * gapX
        val rackLeftEdge = leftChestCenterX - totalRackW / 2f
        val baseBottomY = h * (0.532f - CHEST_LIFT)
        val displayMedals = rackDecorations.take(TacticalCalculator.MAX_WORN_RIBBONS)
        // AR 670-1 row partitioning: partial counts (1 or 2) sit centered on the TOP row over full 3-ribbon lower rows
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
                val stripes = medal.ribbonStripeHexes.size.coerceAtLeast(1)
                val sw = ribW / stripes
                medal.ribbonStripeHexes.forEachIndexed { sIdx, hex ->
                    drawRect(Color(hex), Offset(rx + sIdx * sw, ry), Size(sw + 0.5f, ribH))
                }
                drawRect(trimCol, Offset(rx, ry), Size(ribW, ribH), style = Stroke(0.9f))
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

    // 13. Positional Specialty School Insignia Badges (Slot 1: Above Rack, Slot 2: Left Pocket, Slot 3: Right Pocket)
    wornRegalia.chestPinAboveRack?.let { insig ->
        val aboveY = (topDecorationY - h * 0.030f).coerceIn(h * (0.368f - CHEST_LIFT), h * (0.502f - CHEST_LIFT))
        drawDetailedChestSchoolBadge(
            shape = insig.insigniaShape ?: InsigniaShape.WINGS,
            center = Offset(leftChestCenterX, aboveY),
            badgeW = w * 0.088f,
            badgeH = h * 0.044f,
            col = Color(insig.insigniaColorHex)
        )
    }
    val showMarksmanshipOnPocket = !style.isCutawayMess && marksmanshipTier != MarksmanshipBadgeTier.NONE
    val leftPocketSchoolPin = wornRegalia.chestPinLeftPocket
    if (leftPocketSchoolPin != null && showMarksmanshipOnPocket) {
        // AR 670-1 two-badge pocket wear: specialty badge medial, marksmanship badge lateral on left pocket
        drawDetailedChestSchoolBadge(
            shape = leftPocketSchoolPin.insigniaShape ?: InsigniaShape.WINGS,
            center = Offset(leftChestCenterX - w * 0.029f, h * (0.638f - CHEST_LIFT)),
            badgeW = w * 0.054f,
            badgeH = h * 0.036f,
            col = Color(leftPocketSchoolPin.insigniaColorHex)
        )
        drawMarksmanshipBadge(
            tier = marksmanshipTier,
            center = Offset(leftChestCenterX + w * 0.031f, h * (0.638f - CHEST_LIFT)),
            badgeW = w * 0.050f,
            badgeH = h * 0.038f
        )
    } else {
        leftPocketSchoolPin?.let { insig ->
            drawDetailedChestSchoolBadge(
                shape = insig.insigniaShape ?: InsigniaShape.WINGS,
                center = Offset(leftChestCenterX, h * (0.638f - CHEST_LIFT)),
                badgeW = w * 0.088f,
                badgeH = h * 0.044f,
                col = Color(insig.insigniaColorHex)
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
        drawDetailedChestSchoolBadge(
            shape = insig.insigniaShape ?: InsigniaShape.WINGS,
            center = Offset(rightChestCenterX, h * (0.638f - CHEST_LIFT)),
            badgeW = w * 0.088f,
            badgeH = h * 0.044f,
            col = Color(insig.insigniaColorHex)
        )
    }

    // 14 & 15. Sculpted Neck, Head & Dress Headgear (reused by Alphas, Mess & Bravos)
    drawHeadAndDressHeadgear(
        w = w,
        h = h,
        cx = cx,
        operator = operator.copy(headgearStyle = resolvedHeadgear),
        tier = tier,
        skinTone = skinTone,
        skinShadow = skinShadow,
        headgearCol = headgearCol,
        trimCol = trimCol
    )
}

internal fun DrawScope.drawHeadAndDressHeadgear(
    w: Float,
    h: Float,
    cx: Float,
    operator: Operator,
    tier: RankTier,
    skinTone: Color,
    skinShadow: Color,
    headgearCol: Color,
    trimCol: Color
) {
    drawRect(skinShadow, Offset(cx - w * 0.052f, h * 0.242f), Size(w * 0.104f, h * 0.062f))
    drawOval(skinShadow, Offset(cx - w * 0.112f, h * 0.158f), Size(w * 0.022f, h * 0.044f))
    drawOval(skinShadow, Offset(cx + w * 0.090f, h * 0.158f), Size(w * 0.022f, h * 0.044f))
    drawOval(skinTone, Offset(cx - w * 0.102f, h * 0.082f), Size(w * 0.204f, h * 0.184f))
    drawLine(skinShadow, Offset(cx - w * 0.052f, h * 0.152f), Offset(cx - w * 0.016f, h * 0.154f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(skinShadow, Offset(cx + w * 0.016f, h * 0.154f), Offset(cx + w * 0.052f, h * 0.152f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawCircle(Color(0xFF282220), radius = w * 0.0095f, center = Offset(cx - w * 0.034f, h * 0.168f))
    drawCircle(Color(0xFF282220), radius = w * 0.0095f, center = Offset(cx + w * 0.034f, h * 0.168f))

    fun skullHalfW(yFrac: Float): Float {
        val dy = ((0.174f - yFrac) / 0.092f).coerceIn(-1f, 1f)
        return w * 0.102f * sqrt((1f - dy * dy).coerceAtLeast(0f))
    }

    // 15. Detailed Ceremonial Headgear
    if (operator.headgearStyle.kind != HeadgearKind.BERET) {
        val capBandW = skullHalfW(0.118f) + w * 0.008f
        val crownPath = Path().apply {
            moveTo(cx - w * 0.152f, h * 0.060f)
            lineTo(cx + w * 0.152f, h * 0.060f)
            lineTo(cx + capBandW, h * 0.122f)
            lineTo(cx - capBandW, h * 0.122f)
            close()
        }
        drawPath(crownPath, color = headgearCol)
        drawRect(trimCol, Offset(cx - capBandW + w * 0.003f, h * 0.106f), Size((capBandW - w * 0.003f) * 2f, h * 0.016f))
        drawArc(
            color = Color(0xFF0A0D14),
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(cx - capBandW + w * 0.004f, h * 0.109f),
            size = Size((capBandW - w * 0.004f) * 2f, h * 0.044f)
        )
        drawArc(
            color = Color.White.copy(alpha = 0.22f),
            startAngle = 25f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(cx - w * 0.068f, h * 0.115f),
            size = Size(w * 0.136f, h * 0.022f),
            style = Stroke(1.3f, cap = StrokeCap.Round)
        )
        if (operator.headgearStyle.kind == HeadgearKind.OFFICER_CAP ||
            operator.headgearStyle.kind == HeadgearKind.GENERAL_CAP ||
            tier.isOfficer
        ) {
            val visorStroke = when {
                tier == RankTier.FLAG_OFFICER -> 3.4f
                operator.rank.isFieldGradeOfficer -> 2.3f
                else -> 1.25f
            }
            drawArc(
                color = trimCol,
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(cx - w * 0.082f, h * 0.114f),
                size = Size(w * 0.164f, h * 0.034f),
                style = Stroke(width = visorStroke, cap = StrokeCap.Round)
            )
            // Field-Grade (MAJ–COL) & Flag Officer visor oak-leaf "scrambled eggs" darts
            if (tier == RankTier.FLAG_OFFICER || operator.rank.isFieldGradeOfficer) {
                for (side in listOf(-1f, 1f)) {
                    for (leafIdx in 0..2) {
                        val lx = cx + side * w * (0.034f + leafIdx * 0.017f)
                        val ly = h * (0.132f - leafIdx * 0.002f)
                        drawLine(
                            color = trimCol,
                            start = Offset(lx, ly),
                            end = Offset(lx + side * w * 0.009f, ly - h * 0.006f),
                            strokeWidth = if (tier == RankTier.FLAG_OFFICER) 1.7f else 1.3f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
        drawStar(Offset(cx, h * 0.084f), w * 0.023f, w * 0.0095f, trimCol)
    } else {
        // Skull-fitted molded military beret: snug leather sweatband around cranium & wool drape hugging right temple
        val bandLeftY = 0.124f
        val bandRightY = 0.116f
        val bandLeftX = cx - skullHalfW(bandLeftY) - w * 0.0035f
        val bandRightX = cx + skullHalfW(bandRightY) + w * 0.0035f
        val templeCurveX = cx + skullHalfW(0.100f) + w * 0.009f

        // Subtle brow shadow under sweatband so beret seats naturally on the forehead
        val browShadowPath = Path().apply {
            moveTo(bandLeftX + w * 0.002f, h * (bandLeftY + 0.010f))
            quadraticTo(cx, h * 0.134f, bandRightX - w * 0.002f, h * (bandRightY + 0.011f))
        }
        drawPath(
            browShadowPath,
            color = Color.Black.copy(alpha = 0.24f),
            style = Stroke(width = 2.6f, cap = StrokeCap.Round)
        )

        val beretCrown = Path().apply {
            moveTo(bandLeftX, h * (bandLeftY + 0.004f))
            // Natural wool drape overhanging just past right temple/ear edge without ballooning
            cubicTo(
                bandLeftX - w * 0.016f, h * 0.146f,
                cx - w * 0.124f, h * 0.146f,
                cx - w * 0.124f, h * 0.114f
            )
            // Molded wool sweeps smoothly up and over top of skull
            cubicTo(
                cx - w * 0.124f, h * 0.086f,
                cx - w * 0.086f, h * 0.068f,
                cx - w * 0.028f, h * 0.067f
            )
            // Contours across cranium to left-brow flash stiffener
            cubicTo(
                cx + w * 0.024f, h * 0.066f,
                cx + w * 0.060f, h * 0.074f,
                cx + w * 0.073f, h * 0.090f
            )
            // Tapers cleanly along left temple curve directly into sweatband
            quadraticTo(templeCurveX, h * 0.102f, bandRightX, h * (bandRightY + 0.002f))
            quadraticTo(cx, h * 0.122f, bandLeftX, h * (bandLeftY + 0.004f))
            close()
        }
        drawPath(beretCrown, color = headgearCol)

        // Cylindrical wool dome shading & crisp silhouette separation
        drawPath(
            beretCrown,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.28f),
                    Color.White.copy(alpha = 0.14f),
                    Color.Black.copy(alpha = 0.16f)
                ),
                start = Offset(cx - w * 0.122f, h * 0.136f),
                end = Offset(cx + w * 0.072f, h * 0.072f)
            )
        )
        drawPath(
            beretCrown,
            color = Color.Black.copy(alpha = 0.34f),
            style = Stroke(width = 1.0f, join = StrokeJoin.Round)
        )

        // Fitted black leather sweatband curving across the forehead
        val bandPath = Path().apply {
            moveTo(bandLeftX, h * bandLeftY)
            quadraticTo(cx, h * 0.122f, bandRightX, h * bandRightY)
            lineTo(bandRightX, h * (bandRightY + 0.0095f))
            quadraticTo(cx, h * 0.131f, bandLeftX, h * (bandLeftY + 0.0095f))
            close()
        }
        drawPath(bandPath, color = Color(0xFF0C1017))
        drawPath(
            Path().apply {
                moveTo(bandLeftX + w * 0.003f, h * (bandLeftY + 0.001f))
                quadraticTo(cx, h * 0.123f, bandRightX - w * 0.003f, h * (bandRightY + 0.001f))
            },
            color = Color.White.copy(alpha = 0.20f),
            style = Stroke(width = 0.9f, cap = StrokeCap.Round)
        )

        // Underside drape fold shadow & subtle wool pleat tucked over right temple
        val drapeFoldShadow = Path().apply {
            moveTo(bandLeftX, h * (bandLeftY + 0.005f))
            cubicTo(
                bandLeftX - w * 0.015f, h * 0.144f,
                cx - w * 0.120f, h * 0.143f,
                cx - w * 0.118f, h * 0.120f
            )
            quadraticTo(cx - w * 0.108f, h * 0.133f, bandLeftX, h * (bandLeftY + 0.005f))
            close()
        }
        drawPath(drapeFoldShadow, color = Color.Black.copy(alpha = 0.32f))
        drawPath(
            Path().apply {
                moveTo(cx - w * 0.056f, h * 0.082f)
                quadraticTo(cx - w * 0.092f, h * 0.102f, cx - w * 0.104f, h * 0.132f)
            },
            color = Color.Black.copy(alpha = 0.22f),
            style = Stroke(width = 1.1f, cap = StrokeCap.Round)
        )

        // Heraldic shield-shaped Beret Flash + gold DI crest centered neatly above left eye
        val flashCx = cx + w * 0.034f
        val flashTopY = h * 0.080f
        val flashW = w * 0.036f
        val flashH = h * 0.026f
        val flashFill = when (operator.headgearStyle) {
            HeadgearStyle.TAN_BERET -> Color(0xFF14181F)
            HeadgearStyle.MAROON_BERET -> Color(0xFF1C4378)
            HeadgearStyle.EMERALD_BERET -> Color(0xFF143D2C)
            else -> Color(0xFF1B3A6B)
        }
        val flashPath = Path().apply {
            moveTo(flashCx - flashW * 0.5f, flashTopY)
            lineTo(flashCx + flashW * 0.5f, flashTopY)
            lineTo(flashCx + flashW * 0.5f, flashTopY + flashH * 0.62f)
            quadraticTo(flashCx, flashTopY + flashH * 1.12f, flashCx - flashW * 0.5f, flashTopY + flashH * 0.62f)
            close()
        }
        drawPath(flashPath, color = flashFill)
        if (operator.headgearStyle == HeadgearStyle.MAROON_BERET) {
            drawLine(
                color = Color.White.copy(alpha = 0.85f),
                start = Offset(flashCx - flashW * 0.34f, flashTopY + flashH * 0.70f),
                end = Offset(flashCx + flashW * 0.34f, flashTopY + flashH * 0.14f),
                strokeWidth = 1.4f
            )
        } else if (operator.headgearStyle == HeadgearStyle.TAN_BERET) {
            drawLine(
                color = Color(0xFFA62E35),
                start = Offset(flashCx - flashW * 0.34f, flashTopY + flashH * 0.16f),
                end = Offset(flashCx + flashW * 0.34f, flashTopY + flashH * 0.68f),
                strokeWidth = 1.4f
            )
        } else if (operator.headgearStyle == HeadgearStyle.EMERALD_BERET) {
            drawLine(
                color = Color(0xFFF2D272),
                start = Offset(flashCx - flashW * 0.34f, flashTopY + flashH * 0.72f),
                end = Offset(flashCx + flashW * 0.34f, flashTopY + flashH * 0.14f),
                strokeWidth = 1.5f
            )
        }
        drawPath(flashPath, color = trimCol, style = Stroke(width = 1.1f, join = StrokeJoin.Round))
        if (tier.isOfficer) {
            drawOfficerRankDevice(
                rank = operator.rank,
                center = Offset(flashCx, flashTopY + flashH * 0.44f),
                boxW = flashW * 0.66f,
                boxH = flashH * 0.54f,
                outlineColor = Color(0xFF0A101B)
            )
        } else {
            drawStar(Offset(flashCx, flashTopY + flashH * 0.44f), w * 0.0092f, w * 0.0040f, trimCol)
        }
    }
}

private fun DrawScope.drawWrappedShoulderTab(
    sideSign: Float,
    topFrac: Float,
    tabColor: Color,
    sleeveFaceX: (Float, Float, Float) -> Float,
    h: Float
) {
    val botFrac = topFrac + 0.021f
    val yTop = h * topFrac
    val yBot = h * botFrac
    val rise = h * 0.007f

    val inTop = sleeveFaceX(sideSign, topFrac, 0.20f)
    val outTop = sleeveFaceX(sideSign, topFrac, 0.88f)
    val inBot = sleeveFaceX(sideSign, botFrac, 0.20f)
    val outBot = sleeveFaceX(sideSign, botFrac, 0.88f)
    val midTopX = (inTop + outTop) * 0.50f
    val midBotX = (inBot + outBot) * 0.50f

    val tabPath = Path().apply {
        moveTo(inTop, yTop + rise * 0.35f)
        quadraticTo(midTopX, yTop - rise, outTop, yTop + rise * 0.35f)
        lineTo(outBot, yBot + rise * 0.15f)
        quadraticTo(midBotX, yBot - rise * 0.85f, inBot, yBot + rise * 0.15f)
        close()
    }
    val isSfTab = tabColor == Color(0xFF36D1C4)
    val tabBackCol = if (isSfTab) Color(0xFF1B5B63) else Color(0xFF10141C)
    val tabAccentCol = if (isSfTab) Color(0xFFF2D272) else tabColor
    drawPath(tabPath, color = tabBackCol)
    drawPath(
        tabPath,
        brush = Brush.horizontalGradient(
            colors = if (sideSign > 0f) {
                listOf(Color.White.copy(alpha = 0.08f), Color.Transparent, Color.Black.copy(alpha = 0.22f))
            } else {
                listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent, Color.White.copy(alpha = 0.08f))
            },
            startX = minOf(inTop, outTop),
            endX = maxOf(inTop, outTop)
        )
    )
    drawPath(tabPath, color = tabAccentCol, style = Stroke(width = 1.4f, join = StrokeJoin.Round))

    // Woven gold/crimson/teal block lettering stitches following the tab arch
    val segCount = 5
    for (i in 0 until segCount) {
        val f0 = 0.18f + i * 0.145f
        val f1 = f0 + 0.095f
        val sx0 = inTop + (outTop - inTop) * f0
        val sx1 = inTop + (outTop - inTop) * f1
        val arch0 = sin(f0 * PI.toFloat()) * rise * 0.65f
        val arch1 = sin(f1 * PI.toFloat()) * rise * 0.65f
        val yMid = (yTop + yBot) * 0.5f + rise * 0.15f
        drawLine(
            color = tabAccentCol,
            start = Offset(sx0, yMid - arch0),
            end = Offset(sx1, yMid - arch1),
            strokeWidth = (h * 0.0065f).coerceAtLeast(1.8f),
            cap = StrokeCap.Butt
        )
    }
}

private fun DrawScope.drawWrappedUnitShoulderPatch(
    sideSign: Float,
    topFrac: Float,
    patchStyle: UnitPatchStyle,
    specialty: OperatorSpecialty,
    trimCol: Color,
    accentCol: Color,
    sleeveFaceX: (Float, Float, Float) -> Float,
    w: Float,
    h: Float
) {
    val botFrac = topFrac + 0.064f
    val shoulderFrac = topFrac + 0.040f
    val midFrac = topFrac + 0.028f

    val yTop = h * topFrac
    val yShoulder = h * shoulderFrac
    val yBot = h * botFrac

    val uIn = 0.20f
    val uOut = 0.88f
    val inTop = sleeveFaceX(sideSign, topFrac, uIn)
    val outTop = sleeveFaceX(sideSign, topFrac, uOut)
    val inSide = sleeveFaceX(sideSign, shoulderFrac, uIn + 0.02f)
    val outSide = sleeveFaceX(sideSign, shoulderFrac, uOut - 0.02f)
    val midX = sleeveFaceX(sideSign, midFrac, 0.54f)
    val apexX = sleeveFaceX(sideSign, botFrac, 0.54f)

    // Proper heraldic U.S. Army Heater Shield (flat-arched top, straight parallel upper walls, rounded base into shield point)
    val shieldPath = Path().apply {
        moveTo(inTop, yTop + h * 0.003f)
        quadraticTo(midX, yTop - h * 0.005f, outTop, yTop + h * 0.003f)
        lineTo(outSide, yShoulder)
        quadraticTo(outSide, yBot - h * 0.006f, apexX, yBot)
        quadraticTo(inSide, yBot - h * 0.006f, inSide, yShoulder)
        close()
    }

    val shieldPrimary = when (patchStyle) {
        UnitPatchStyle.AUTO_MOS -> when (specialty) {
            OperatorSpecialty.ASSAULT -> Color(0xFF1D4E89)   // Infantry Cobalt Blue
            OperatorSpecialty.RECON -> Color(0xFF23533A)     // Airborne/Recon Hunter Green
            OperatorSpecialty.SIGNALS -> Color(0xFF1A3A5C)   // Signal Corps Deep Azure
            OperatorSpecialty.BREACHER -> Color(0xFF7A2229)  // Engineer Scarlet
            OperatorSpecialty.MEDIC -> Color(0xFF4F2442)     // Medical Maroon
            OperatorSpecialty.COMMAND -> Color(0xFF1B2E4B)   // HQ Command Sapphire
        }
        else -> Color(patchStyle.primaryHex)
    }
    val patchFlashCol = when (patchStyle) {
        UnitPatchStyle.AUTO_MOS -> accentCol
        else -> Color(patchStyle.accentHex)
    }

    drawPath(shieldPath, color = shieldPrimary)

    // Diagonal heraldic lower bend / chevron flash inside shield for sharp detail
    val flashPath = Path().apply {
        moveTo(inSide, yShoulder - h * 0.004f)
        lineTo(midX, yShoulder - h * 0.013f)
        lineTo(outSide, yShoulder - h * 0.004f)
        quadraticTo(outSide, yBot - h * 0.006f, apexX, yBot)
        quadraticTo(inSide, yBot - h * 0.006f, inSide, yShoulder - h * 0.004f)
        close()
    }
    drawPath(flashPath, color = patchFlashCol.copy(alpha = 0.85f))

    // Subtle cylindrical sleeve wrap highlight + outer curvature shadow (keeps colors vivid, never black)
    drawPath(
        shieldPath,
        brush = Brush.horizontalGradient(
            colors = if (sideSign > 0f) {
                listOf(Color.White.copy(alpha = 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.24f))
            } else {
                listOf(Color.Black.copy(alpha = 0.24f), Color.Transparent, Color.White.copy(alpha = 0.12f))
            },
            startX = minOf(inTop, outTop),
            endX = maxOf(inTop, outTop)
        )
    )

    // Gold merrowed embroidered border + distinct emblem motif inside shield
    drawPath(shieldPath, color = trimCol, style = Stroke(width = (w * 0.007f).coerceAtLeast(1.5f), join = StrokeJoin.Round))
    val emblemCenter = Offset(midX, h * (topFrac + 0.022f))
    when (patchStyle) {
        UnitPatchStyle.MEDICAL_COMMAND -> {
            drawLine(Color.White, Offset(midX, emblemCenter.y - h * 0.010f), Offset(midX, emblemCenter.y + h * 0.010f), strokeWidth = 2.0f)
            drawLine(Color.White, Offset(midX - w * 0.011f, emblemCenter.y), Offset(midX + w * 0.011f, emblemCenter.y), strokeWidth = 2.0f)
        }
        UnitPatchStyle.SIGNAL_DIRECTORATE -> {
            val bolt = Path().apply {
                moveTo(midX + w * 0.006f, emblemCenter.y - h * 0.011f)
                lineTo(midX - w * 0.007f, emblemCenter.y + h * 0.001f)
                lineTo(midX + w * 0.003f, emblemCenter.y + h * 0.001f)
                lineTo(midX - w * 0.006f, emblemCenter.y + h * 0.013f)
            }
            drawPath(bolt, color = Color(0xFF5AC8FA), style = Stroke(width = 1.8f, cap = StrokeCap.Round))
        }
        UnitPatchStyle.INTEL_OVERWATCH -> {
            val diamond = Path().apply {
                moveTo(midX, emblemCenter.y - h * 0.012f)
                lineTo(midX + w * 0.011f, emblemCenter.y)
                lineTo(midX, emblemCenter.y + h * 0.012f)
                lineTo(midX - w * 0.011f, emblemCenter.y)
                close()
            }
            drawPath(diamond, color = Color(0xFF63B8D6), style = Stroke(width = 1.5f))
            drawCircle(Color.White, radius = w * 0.0035f, center = emblemCenter)
        }
        UnitPatchStyle.SPECIAL_FORCES -> {
            // Iconic SF dagger flanked by three lightning/chevron flashes
            drawLine(Color(0xFFF2D272), Offset(midX, emblemCenter.y - h * 0.012f), Offset(midX, yBot - h * 0.008f), strokeWidth = 1.9f, cap = StrokeCap.Round)
            drawLine(Color(0xFFF2D272), Offset(midX - w * 0.010f, emblemCenter.y + h * 0.003f), Offset(midX + w * 0.010f, emblemCenter.y + h * 0.003f), strokeWidth = 1.6f)
            drawStar(Offset(midX, emblemCenter.y - h * 0.010f), w * 0.0065f, w * 0.003f, Color(0xFFF2D272))
        }
        UnitPatchStyle.SAPPER_CITADEL -> {
            drawRect(Color(0xFFF2D272), Offset(midX - w * 0.012f, emblemCenter.y - h * 0.006f), Size(w * 0.024f, h * 0.014f), style = Stroke(1.5f))
            drawLine(Color(0xFFF2D272), Offset(midX, emblemCenter.y - h * 0.006f), Offset(midX, emblemCenter.y + h * 0.008f), strokeWidth = 1.4f)
        }
        UnitPatchStyle.LOGISTICS_CORPS -> {
            drawCircle(Color(0xFFF2D272), radius = w * 0.012f, center = emblemCenter, style = Stroke(1.5f))
            drawLine(Color(0xFFF2D272), Offset(midX - w * 0.009f, emblemCenter.y), Offset(midX + w * 0.009f, emblemCenter.y), strokeWidth = 1.3f)
            drawLine(Color(0xFFF2D272), Offset(midX, emblemCenter.y - h * 0.009f), Offset(midX, emblemCenter.y + h * 0.009f), strokeWidth = 1.3f)
        }
        UnitPatchStyle.SKYWARD_AIRBORNE -> {
            drawLine(Color.White, Offset(midX - w * 0.015f, emblemCenter.y), Offset(midX + w * 0.015f, emblemCenter.y), strokeWidth = 1.9f, cap = StrokeCap.Round)
            drawStar(emblemCenter, w * 0.009f, w * 0.004f, Color(0xFFF5D576))
        }
        else -> {
            drawStar(emblemCenter, w * 0.012f, w * 0.0052f, Color(0xFFF5D576))
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(midX, h * (topFrac + 0.036f)),
                end = Offset(apexX, yBot - h * 0.007f),
                strokeWidth = 1.4f,
                cap = StrokeCap.Round
            )
        }
    }
}

internal fun DrawScope.drawWrappedSleeveRankInsignia(
    sideSign: Float,
    rank: OperatorRank,
    tunicCol: Color,
    lapelCol: Color,
    trimCol: Color,
    sleeveFaceX: (Float, Float, Float) -> Float,
    w: Float,
    h: Float,
    startFrac: Float = 0.495f
) {
    val isSpecialist = rank == OperatorRank.SPC
    val (chevrons, rockers, hasStar) = when (rank) {
        OperatorRank.PV2 -> Triple(1, 0, false)
        OperatorRank.PFC -> Triple(1, 1, false)
        OperatorRank.SPC -> Triple(0, 0, false)
        OperatorRank.SGT -> Triple(3, 0, false)
        OperatorRank.SSG -> Triple(3, 1, false)
        OperatorRank.SFC -> Triple(3, 2, false)
        OperatorRank.MSG -> Triple(3, 3, false)
        OperatorRank.SGM -> Triple(3, 3, true)
        else -> Triple(rank.insigniaCount.coerceAtMost(3), (rank.insigniaCount - 3).coerceIn(0, 3), false)
    }

    val chevStep = 0.0135f
    val rockStep = 0.0120f
    val stripeThick = h * 0.0078f
    val vRise = h * 0.0105f
    val edgeOutline = lapelCol.copy(alpha = 0.72f)

    if (isSpecialist) {
        val botFrac = startFrac + 0.056f
        val shFrac = startFrac + 0.036f
        val midTopX = sleeveFaceX(sideSign, startFrac, 0.54f)
        val inTop = sleeveFaceX(sideSign, startFrac, 0.24f)
        val outTop = sleeveFaceX(sideSign, startFrac, 0.84f)
        val inSide = sleeveFaceX(sideSign, shFrac, 0.24f)
        val outSide = sleeveFaceX(sideSign, shFrac, 0.84f)
        val apexX = sleeveFaceX(sideSign, botFrac, 0.54f)

        val shieldPath = Path().apply {
            moveTo(inTop, h * (startFrac + 0.012f))
            quadraticTo(midTopX, h * (startFrac - 0.006f), outTop, h * (startFrac + 0.012f))
            lineTo(outSide, h * shFrac)
            quadraticTo(outSide, h * botFrac - h * 0.004f, apexX, h * botFrac)
            quadraticTo(inSide, h * botFrac - h * 0.004f, inSide, h * shFrac)
            close()
        }
        drawPath(shieldPath, color = tunicCol)
        drawPath(shieldPath, color = trimCol, style = Stroke(width = 1.4f, join = StrokeJoin.Round))
        // Crisp Specialist eagle wings & head silhouette stitched directly onto the uniform shield
        val cy = h * (startFrac + 0.028f)
        val wingHalf = kotlin.math.abs(outTop - inTop) * 0.34f
        drawLine(trimCol, Offset(midTopX - wingHalf, cy - h * 0.004f), Offset(midTopX, cy + h * 0.003f), strokeWidth = 2.1f, cap = StrokeCap.Round)
        drawLine(trimCol, Offset(midTopX + wingHalf, cy - h * 0.004f), Offset(midTopX, cy + h * 0.003f), strokeWidth = 2.1f, cap = StrokeCap.Round)
        drawCircle(trimCol, radius = (w * 0.007f).coerceAtLeast(1.8f), center = Offset(midTopX, cy - h * 0.005f))
        return
    }

    // Filled symmetric upward-pointing chevrons embroidered directly on sleeve fabric (no black box)
    repeat(chevrons) { c ->
        val cFrac = startFrac + 0.015f + c * chevStep
        val cy = h * cFrac
        val inX = sleeveFaceX(sideSign, cFrac, 0.26f)
        val outX = sleeveFaceX(sideSign, cFrac, 0.82f)
        val leftX = minOf(inX, outX)
        val rightX = maxOf(inX, outX)
        val midX = (leftX + rightX) * 0.5f

        val chevPoly = Path().apply {
            moveTo(midX, cy - vRise)
            lineTo(rightX, cy + vRise * 0.42f)
            lineTo(rightX, cy + vRise * 0.42f + stripeThick)
            lineTo(midX, cy - vRise + stripeThick)
            lineTo(leftX, cy + vRise * 0.42f + stripeThick)
            lineTo(leftX, cy + vRise * 0.42f)
            close()
        }
        drawPath(chevPoly, color = trimCol)
        drawPath(chevPoly, color = edgeOutline, style = Stroke(width = (w * 0.002f).coerceAtLeast(0.6f), join = StrokeJoin.Miter))
    }

    // Filled concentric rockers cleanly spaced beneath chevrons
    val rockerStartFrac = startFrac + 0.018f + chevrons * chevStep + (if (hasStar) 0.012f else 0.004f)
    val rockerDip = h * 0.010f
    repeat(rockers) { rk ->
        val rFrac = rockerStartFrac + rk * rockStep
        val ry = h * rFrac
        val inX = sleeveFaceX(sideSign, rFrac, 0.26f)
        val outX = sleeveFaceX(sideSign, rFrac, 0.82f)
        val leftX = minOf(inX, outX)
        val rightX = maxOf(inX, outX)
        val midX = (leftX + rightX) * 0.5f

        val rockerPoly = Path().apply {
            moveTo(leftX, ry - h * 0.002f)
            quadraticTo(midX, ry + rockerDip, rightX, ry - h * 0.002f)
            lineTo(rightX, ry - h * 0.002f + stripeThick)
            quadraticTo(midX, ry + rockerDip + stripeThick, leftX, ry - h * 0.002f + stripeThick)
            close()
        }
        drawPath(rockerPoly, color = trimCol)
        drawPath(rockerPoly, color = edgeOutline, style = Stroke(width = (w * 0.002f).coerceAtLeast(0.6f), join = StrokeJoin.Round))
    }

    if (hasStar) {
        val starFrac = startFrac + 0.020f + chevrons * chevStep
        val sx = sleeveFaceX(sideSign, starFrac, 0.54f)
        drawStar(Offset(sx, h * starFrac), w * 0.009f, w * 0.004f, trimCol)
    }
}

private fun DrawScope.drawWrappedServiceStripes(
    sideSign: Float,
    stripeCount: Int,
    lapelCol: Color,
    trimCol: Color,
    sleeveFaceX: (Float, Float, Float) -> Float,
    w: Float,
    h: Float
) {
    val barRise = h * 0.015f
    val barThick = h * 0.0085f
    val stepFrac = 0.0185f
    val baseFrac = 0.816f
    val edgeOutline = lapelCol.copy(alpha = 0.72f)

    // Filled diagonal parallelogram bars stitched directly onto sleeve fabric (no black backing box)
    repeat(stripeCount) { s ->
        val f = baseFrac - s * stepFrac
        val yLeft = h * f
        val xLeft = minOf(sleeveFaceX(sideSign, f, 0.28f), sleeveFaceX(sideSign, f, 0.80f))
        val xRight = maxOf(sleeveFaceX(sideSign, f, 0.28f), sleeveFaceX(sideSign, f, 0.80f))
        val yRight = yLeft - barRise

        val barPoly = Path().apply {
            moveTo(xLeft, yLeft - barThick * 0.5f)
            lineTo(xRight, yRight - barThick * 0.5f)
            lineTo(xRight, yRight + barThick * 0.5f)
            lineTo(xLeft, yLeft + barThick * 0.5f)
            close()
        }
        drawPath(barPoly, color = trimCol)
        drawPath(barPoly, color = edgeOutline, style = Stroke(width = (w * 0.002f).coerceAtLeast(0.6f), join = StrokeJoin.Miter))
    }
}

internal fun DrawScope.drawDetailedChestSchoolBadge(
    shape: InsigniaShape,
    center: Offset,
    badgeW: Float,
    badgeH: Float,
    col: Color
) {
    val cx = center.x
    val cy = center.y
    val silver = Color(0xFFE0E6F0)
    val gold = Color(0xFFF2D272)
    val darkBack = Color(0xFF121722)

    when (shape) {
        InsigniaShape.RIFLE_BADGE -> {
            drawLaurelWreathArcs(center, badgeH * 0.62f, silver, (badgeH * 0.11f).coerceAtLeast(1.3f))
            drawRoundRect(
                color = col,
                topLeft = Offset(cx - badgeW * 0.44f, cy - badgeH * 0.28f),
                size = Size(badgeW * 0.88f, badgeH * 0.56f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            drawRoundRect(
                color = silver,
                topLeft = Offset(cx - badgeW * 0.44f, cy - badgeH * 0.28f),
                size = Size(badgeW * 0.88f, badgeH * 0.56f),
                cornerRadius = CornerRadius(3f, 3f),
                style = Stroke(1.2f)
            )
            // Musket barrel, bayonet & curved stock
            drawLine(silver, Offset(cx - badgeW * 0.34f, cy + badgeH * 0.03f), Offset(cx + badgeW * 0.34f, cy - badgeH * 0.03f), strokeWidth = 1.9f, cap = StrokeCap.Round)
            drawLine(silver, Offset(cx - badgeW * 0.32f, cy + badgeH * 0.12f), Offset(cx - badgeW * 0.16f, cy + badgeH * 0.02f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        }
        InsigniaShape.MEDICAL_BADGE -> {
            drawLaurelWreathArcs(center, badgeH * 0.58f, silver, (badgeH * 0.11f).coerceAtLeast(1.3f))
            drawCircle(darkBack, radius = badgeH * 0.42f, center = center)
            drawCircle(col, radius = badgeH * 0.42f, center = center, style = Stroke(1.2f))
            drawLine(col, Offset(cx, cy - badgeH * 0.26f), Offset(cx, cy + badgeH * 0.26f), strokeWidth = 2.4f)
            drawLine(col, Offset(cx - badgeH * 0.26f, cy), Offset(cx + badgeH * 0.26f, cy), strokeWidth = 2.4f)
        }
        InsigniaShape.WINGS, InsigniaShape.STAR_WINGS -> {
            // Tapered 3-feather swept wings + ribbed parachute canopy + shroud lines
            drawLine(col, Offset(cx - badgeW * 0.48f, cy - badgeH * 0.04f), Offset(cx + badgeW * 0.48f, cy - badgeH * 0.04f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            drawLine(col, Offset(cx - badgeW * 0.40f, cy + badgeH * 0.12f), Offset(cx + badgeW * 0.40f, cy + badgeH * 0.12f), strokeWidth = 1.7f, cap = StrokeCap.Round)
            drawLine(col, Offset(cx - badgeW * 0.28f, cy + badgeH * 0.26f), Offset(cx + badgeW * 0.28f, cy + badgeH * 0.26f), strokeWidth = 1.3f, cap = StrokeCap.Round)
            drawArc(
                color = col,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(cx - badgeW * 0.16f, cy - badgeH * 0.26f),
                size = Size(badgeW * 0.32f, badgeH * 0.44f)
            )
            drawLine(col, Offset(cx - badgeW * 0.11f, cy - badgeH * 0.04f), Offset(cx, cy + badgeH * 0.34f), strokeWidth = 1.2f)
            drawLine(col, Offset(cx + badgeW * 0.11f, cy - badgeH * 0.04f), Offset(cx, cy + badgeH * 0.34f), strokeWidth = 1.2f)
            if (shape == InsigniaShape.STAR_WINGS) {
                drawLaurelWreathArcs(Offset(cx, cy - badgeH * 0.32f), badgeH * 0.22f, gold, 1.3f)
                drawStar(Offset(cx, cy - badgeH * 0.32f), badgeH * 0.18f, badgeH * 0.08f, gold)
            }
        }
        InsigniaShape.AIR_ASSAULT_WINGS -> {
            drawLine(col, Offset(cx - badgeW * 0.46f, cy + badgeH * 0.04f), Offset(cx + badgeW * 0.46f, cy + badgeH * 0.04f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            drawLine(col, Offset(cx - badgeW * 0.34f, cy + badgeH * 0.18f), Offset(cx + badgeW * 0.34f, cy + badgeH * 0.18f), strokeWidth = 1.5f, cap = StrokeCap.Round)
            // Helicopter rotor blade & pod
            drawLine(col, Offset(cx - badgeW * 0.26f, cy - badgeH * 0.28f), Offset(cx + badgeW * 0.26f, cy - badgeH * 0.28f), strokeWidth = 1.7f, cap = StrokeCap.Round)
            drawLine(col, Offset(cx, cy - badgeH * 0.28f), Offset(cx, cy + badgeH * 0.28f), strokeWidth = 1.6f)
            drawOval(col, topLeft = Offset(cx - badgeW * 0.10f, cy - badgeH * 0.12f), size = Size(badgeW * 0.20f, badgeH * 0.28f))
        }
        InsigniaShape.TORCH_CREST -> {
            drawLine(col, Offset(cx - badgeW * 0.42f, cy + badgeH * 0.08f), Offset(cx + badgeW * 0.42f, cy + badgeH * 0.08f), strokeWidth = 2.1f, cap = StrokeCap.Round)
            drawLine(col, Offset(cx, cy - badgeH * 0.16f), Offset(cx, cy + badgeH * 0.38f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            drawStar(Offset(cx, cy - badgeH * 0.26f), badgeH * 0.22f, badgeH * 0.10f, Color(0xFFFF9F43))
        }
        InsigniaShape.CYBER_CREST -> {
            drawHexagon(center, badgeH * 0.50f, darkBack, col)
            val bolt = Path().apply {
                moveTo(cx + badgeW * 0.08f, cy - badgeH * 0.32f)
                lineTo(cx - badgeW * 0.09f, cy + badgeH * 0.03f)
                lineTo(cx + badgeW * 0.03f, cy + badgeH * 0.03f)
                lineTo(cx - badgeW * 0.08f, cy + badgeH * 0.34f)
            }
            drawPath(bolt, color = col, style = Stroke(width = 1.8f, cap = StrokeCap.Round))
        }
        InsigniaShape.INTEL_CREST -> {
            val diamond = Path().apply {
                moveTo(cx, cy - badgeH * 0.48f)
                lineTo(cx + badgeW * 0.28f, cy)
                lineTo(cx, cy + badgeH * 0.48f)
                lineTo(cx - badgeW * 0.28f, cy)
                close()
            }
            drawPath(diamond, color = darkBack)
            drawPath(diamond, color = col, style = Stroke(1.6f, join = StrokeJoin.Round))
            drawStar(center, badgeH * 0.24f, badgeH * 0.09f, Color.White, points = 4)
        }
        InsigniaShape.EOD_BADGE -> {
            drawLaurelWreathArcs(center, badgeH * 0.56f, col, 1.8f)
            drawStar(Offset(cx, cy - badgeH * 0.24f), badgeH * 0.20f, badgeH * 0.09f, Color(0xFFFFC145))
            drawOval(col, topLeft = Offset(cx - badgeW * 0.11f, cy - badgeH * 0.04f), size = Size(badgeW * 0.22f, badgeH * 0.40f))
        }
        InsigniaShape.LOGISTICS_CREST -> {
            drawCircle(darkBack, radius = badgeH * 0.45f, center = center)
            drawCircle(col, radius = badgeH * 0.45f, center = center, style = Stroke(1.8f))
            drawLine(col, Offset(cx - badgeH * 0.36f, cy), Offset(cx + badgeH * 0.36f, cy), strokeWidth = 1.6f)
            drawLine(col, Offset(cx, cy - badgeH * 0.36f), Offset(cx, cy + badgeH * 0.36f), strokeWidth = 1.6f)
            drawCircle(col, radius = badgeH * 0.14f, center = center)
        }
        InsigniaShape.STAFF_CREST -> {
            drawLine(col, Offset(cx - badgeW * 0.28f, cy - badgeH * 0.34f), Offset(cx + badgeW * 0.28f, cy + badgeH * 0.34f), strokeWidth = 1.9f, cap = StrokeCap.Round)
            drawLine(col, Offset(cx + badgeW * 0.28f, cy - badgeH * 0.34f), Offset(cx - badgeW * 0.28f, cy + badgeH * 0.34f), strokeWidth = 1.9f, cap = StrokeCap.Round)
            drawCircle(darkBack, radius = badgeH * 0.42f, center = center)
            drawCircle(col, radius = badgeH * 0.42f, center = center, style = Stroke(1.6f))
            drawStar(center, badgeH * 0.30f, badgeH * 0.13f, col)
        }
        InsigniaShape.TAB -> {
            drawLaurelWreathArcs(center, badgeH * 0.50f, col, 1.6f)
        }
    }
}

private fun DrawScope.drawBranchCollarInsignia(
    cx: Float,
    h: Float,
    w: Float,
    specialty: OperatorSpecialty,
    tier: RankTier,
    trimCol: Color
) {
    val lx = cx - w * 0.076f
    val rx = cx + w * 0.076f
    val y = h * 0.376f
    if (!tier.isOfficer) {
        drawCircle(trimCol, radius = w * 0.015f, center = Offset(lx, y))
        drawCircle(trimCol, radius = w * 0.015f, center = Offset(rx, y))
        drawCircle(Color(0xFF14181F).copy(alpha = 0.35f), radius = w * 0.0065f, center = Offset(lx, y))
        drawCircle(Color(0xFF14181F).copy(alpha = 0.35f), radius = w * 0.0065f, center = Offset(rx, y))
        return
    }
    val span = w * 0.014f
    when (specialty) {
        OperatorSpecialty.ASSAULT, OperatorSpecialty.RECON -> {
            drawLine(trimCol, Offset(lx - span, y - span), Offset(lx + span, y + span), strokeWidth = 1.8f)
            drawLine(trimCol, Offset(lx + span, y - span), Offset(lx - span, y + span), strokeWidth = 1.8f)
            drawLine(trimCol, Offset(rx - span, y - span), Offset(rx + span, y + span), strokeWidth = 1.8f)
            drawLine(trimCol, Offset(rx + span, y - span), Offset(rx - span, y + span), strokeWidth = 1.8f)
        }
        OperatorSpecialty.MEDIC -> {
            drawLine(trimCol, Offset(lx, y - span), Offset(lx, y + span), strokeWidth = 2.0f)
            drawLine(trimCol, Offset(lx - span, y), Offset(lx + span, y), strokeWidth = 2.0f)
            drawLine(trimCol, Offset(rx, y - span), Offset(rx, y + span), strokeWidth = 2.0f)
            drawLine(trimCol, Offset(rx - span, y), Offset(rx + span, y), strokeWidth = 2.0f)
        }
        OperatorSpecialty.BREACHER -> {
            drawRect(trimCol, Offset(lx - span, y - span * 0.7f), Size(span * 2f, span * 1.4f))
            drawRect(trimCol, Offset(rx - span, y - span * 0.7f), Size(span * 2f, span * 1.4f))
        }
        else -> {
            drawStar(Offset(lx, y), span * 1.15f, span * 0.5f, trimCol)
            drawStar(Offset(rx, y), span * 1.15f, span * 0.5f, trimCol)
        }
    }
}

private fun DrawScope.drawFieldTacticalFigure(
    w: Float,
    h: Float,
    cx: Float,
    operator: Operator,
    wornRegalia: TacticalCalculator.WornRegaliaSummary,
    combatPatch: UnitPatchStyle,
    isSquadLeader: Boolean,
    equippedWeaponId: String?,
    skinTone: Color,
    skinShadow: Color,
    visorTone: Color,
    hasArmor: Boolean,
    hasHelmet: Boolean,
    hasWeapon: Boolean,
    hasNvg: Boolean
) {
    val primaryCamo = Color(operator.camoPattern.primaryHex)
    val secondaryCamo = Color(operator.camoPattern.secondaryHex)
    val accentCamo = Color(operator.camoPattern.accentHex)

    // Broad Tactical Combat Shirt Sleeves
    val sleeveL = Path().apply {
        moveTo(cx - w * 0.254f, h * 0.378f)
        cubicTo(cx - w * 0.350f, h * 0.405f, cx - w * 0.382f, h * 0.485f, cx - w * 0.396f, h * 0.96f)
        lineTo(cx - w * 0.256f, h * 0.96f)
        lineTo(cx - w * 0.244f, h * 0.54f)
        close()
    }
    val sleeveR = Path().apply {
        moveTo(cx + w * 0.254f, h * 0.378f)
        cubicTo(cx + w * 0.350f, h * 0.405f, cx + w * 0.382f, h * 0.485f, cx + w * 0.396f, h * 0.96f)
        lineTo(cx + w * 0.256f, h * 0.96f)
        lineTo(cx + w * 0.244f, h * 0.54f)
        close()
    }
    drawPath(sleeveL, color = primaryCamo)
    drawPath(sleeveR, color = primaryCamo)

    val torsoPath = Path().apply {
        moveTo(cx - w * 0.26f, h * 0.96f)
        lineTo(cx - w * 0.254f, h * 0.378f)
        lineTo(cx - w * 0.14f, h * 0.35f)
        lineTo(cx + w * 0.14f, h * 0.35f)
        lineTo(cx + w * 0.254f, h * 0.378f)
        lineTo(cx + w * 0.26f, h * 0.96f)
        close()
    }
    drawPath(torsoPath, color = primaryCamo)

    // Wrapped BDU Shoulder Pocket & Velcro IFF Patches positioned cleanly on the outer deltoid sleeves
    val leftShoulderPatch = Path().apply {
        moveTo(cx - w * 0.352f, h * 0.440f)
        quadraticTo(cx - w * 0.314f, h * 0.432f, cx - w * 0.278f, h * 0.445f)
        lineTo(cx - w * 0.284f, h * 0.548f)
        quadraticTo(cx - w * 0.322f, h * 0.556f, cx - w * 0.364f, h * 0.544f)
        close()
    }
    val rightShoulderPatch = Path().apply {
        moveTo(cx + w * 0.278f, h * 0.445f)
        quadraticTo(cx + w * 0.314f, h * 0.432f, cx + w * 0.352f, h * 0.440f)
        lineTo(cx + w * 0.364f, h * 0.544f)
        quadraticTo(cx + w * 0.322f, h * 0.556f, cx + w * 0.284f, h * 0.548f)
        close()
    }
    drawPath(leftShoulderPatch, color = secondaryCamo)
    drawPath(rightShoulderPatch, color = secondaryCamo)
    drawPath(
        leftShoulderPatch,
        brush = Brush.horizontalGradient(
            listOf(Color.Black.copy(alpha = 0.22f), Color.White.copy(alpha = 0.08f)),
            startX = cx - w * 0.364f,
            endX = cx - w * 0.278f
        )
    )
    drawPath(
        rightShoulderPatch,
        brush = Brush.horizontalGradient(
            listOf(Color.White.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.22f)),
            startX = cx + w * 0.278f,
            endX = cx + w * 0.364f
        )
    )
    wornRegalia.shoulderTabs.take(2).forEachIndexed { idx, tab ->
        val tabYShift = idx * h * 0.012f
        val arcCol = if (tab.id == "insig_sf_tab") Color(0xFFF2D272) else Color(tab.insigniaColorHex)
        drawLine(
            arcCol,
            Offset(cx + w * 0.292f, h * 0.453f + tabYShift),
            Offset(cx + w * 0.346f, h * 0.448f + tabYShift),
            strokeWidth = 2.6f,
            cap = StrokeCap.Round
        )
    }
    val chosenPatch = operator.tailorOverrides.unitPatch
    if (chosenPatch != UnitPatchStyle.NONE) {
        val patchColor = if (chosenPatch == UnitPatchStyle.AUTO_MOS) accentCamo else Color(chosenPatch.primaryHex)
        val patchBorder = if (chosenPatch == UnitPatchStyle.AUTO_MOS) Color(0xFFD8B24C) else Color(chosenPatch.accentHex)
        val patchTopY = h * (0.470f + wornRegalia.shoulderTabs.size.coerceAtMost(2) * 0.008f)
        drawRoundRect(
            color = patchColor,
            topLeft = Offset(cx + w * 0.298f, patchTopY),
            size = Size(w * 0.044f, h * 0.050f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = patchBorder,
            topLeft = Offset(cx + w * 0.298f, patchTopY),
            size = Size(w * 0.044f, h * 0.050f),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 1.2f)
        )
    }
    drawFieldCombatPatchAndLeaderTab(
        w = w,
        h = h,
        cx = cx,
        specialty = operator.specialty,
        combatPatch = combatPatch,
        isSquadLeader = isSquadLeader,
        accentCamo = accentCamo
    )

    if (hasArmor) {
        drawRoundRect(secondaryCamo, Offset(cx - w * 0.165f, h * 0.42f), Size(w * 0.33f, h * 0.38f), CornerRadius(10f, 10f))
        for (row in 0..2) {
            drawLine(accentCamo, Offset(cx - w * 0.135f, h * (0.49f + row * 0.045f)), Offset(cx + w * 0.135f, h * (0.49f + row * 0.045f)), strokeWidth = 2.5f)
        }
        for (p in 0..2) {
            drawRoundRect(primaryCamo, Offset(cx - w * 0.13f + p * w * 0.092f, h * 0.64f), Size(w * 0.078f, h * 0.12f), CornerRadius(4f, 4f))
        }
    }

    // Center Chest ACU Velcro Rank Patch (visible on both plate carrier and combat shirt)
    val rankTabW = w * 0.074f
    val rankTabH = h * 0.042f
    val rankTabLeft = cx - rankTabW * 0.5f
    val rankTabTop = h * 0.436f
    drawRoundRect(
        color = primaryCamo.copy(alpha = 0.94f),
        topLeft = Offset(rankTabLeft, rankTabTop),
        size = Size(rankTabW, rankTabH),
        cornerRadius = CornerRadius(3f, 3f)
    )
    drawRoundRect(
        color = accentCamo,
        topLeft = Offset(rankTabLeft, rankTabTop),
        size = Size(rankTabW, rankTabH),
        cornerRadius = CornerRadius(3f, 3f),
        style = Stroke(width = 1.1f)
    )
    val tabCenter = Offset(cx, rankTabTop + rankTabH * 0.5f)
    when (operator.rank.tier) {
        RankTier.FLAG_OFFICER -> {
            val stars = operator.rank.insigniaCount.coerceIn(1, 4)
            val starR = (rankTabW * 0.11f).coerceAtLeast(1.8f)
            val stepX = rankTabW / (stars + 1)
            repeat(stars) { s ->
                drawStar(Offset(rankTabLeft + stepX * (s + 1), tabCenter.y), starR, starR * 0.45f, Color(0xFF14181E))
            }
        }
        RankTier.OFFICER -> {
            drawOfficerRankDevice(
                rank = operator.rank,
                center = tabCenter,
                boxW = rankTabW * 0.78f,
                boxH = rankTabH * 0.78f,
                outlineColor = Color(0xFF10141B),
                subdued = true
            )
        }
        RankTier.SNCO, RankTier.NCO, RankTier.SOLDIER -> {
            if (operator.rank == OperatorRank.SPC) {
                val spcCol = Color(0xFF1A2029)
                val shield = Path().apply {
                    moveTo(cx - rankTabW * 0.24f, rankTabTop + rankTabH * 0.22f)
                    quadraticTo(cx, rankTabTop + rankTabH * 0.12f, cx + rankTabW * 0.24f, rankTabTop + rankTabH * 0.22f)
                    lineTo(cx + rankTabW * 0.22f, rankTabTop + rankTabH * 0.68f)
                    quadraticTo(cx, rankTabTop + rankTabH * 0.88f, cx - rankTabW * 0.22f, rankTabTop + rankTabH * 0.68f)
                    close()
                }
                drawPath(shield, color = spcCol, style = Stroke(width = 1.3f))
            } else {
                val (chevrons, rockers) = when (operator.rank) {
                    OperatorRank.PVT -> 0 to 0
                    OperatorRank.PV2 -> 1 to 0
                    OperatorRank.PFC -> 1 to 1
                    else -> operator.rank.insigniaCount.coerceAtMost(3) to (operator.rank.insigniaCount - 3).coerceIn(0, 3)
                }
                val totalBands = chevrons + rockers
                val bandStep = rankTabH * 0.115f
                val startY = if (totalBands <= 2) rankTabTop + rankTabH * 0.42f else rankTabTop + rankTabH * 0.20f
                val chevCol = Color(0xFF14181E)
                repeat(chevrons) { c ->
                    val cy = startY + c * bandStep
                    val p = Path().apply {
                        moveTo(cx - rankTabW * 0.28f, cy + rankTabH * 0.10f)
                        lineTo(cx, cy - rankTabH * 0.04f)
                        lineTo(cx + rankTabW * 0.28f, cy + rankTabH * 0.10f)
                    }
                    drawPath(p, color = chevCol, style = Stroke(width = 1.4f, cap = StrokeCap.Round))
                }
                repeat(rockers) { rk ->
                    val ry = startY + chevrons * bandStep + rankTabH * 0.03f + rk * bandStep
                    drawArc(
                        color = chevCol,
                        startAngle = 20f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(cx - rankTabW * 0.24f, ry - rankTabH * 0.04f),
                        size = Size(rankTabW * 0.48f, rankTabH * 0.12f),
                        style = Stroke(width = 1.35f, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }

    if (hasWeapon) {
        drawEnhancedFieldWeapon(w = w, h = h, cx = cx, weaponId = equippedWeaponId)
    }

    drawRect(skinShadow, Offset(cx - w * 0.05f, h * 0.30f), Size(w * 0.10f, h * 0.08f))
    drawOval(skinTone, Offset(cx - w * 0.095f, h * 0.14f), Size(w * 0.19f, h * 0.19f))
    drawRoundRect(visorTone, Offset(cx - w * 0.075f, h * 0.205f), Size(w * 0.15f, h * 0.042f), CornerRadius(6f, 6f))

    if (hasHelmet) {
        drawArc(secondaryCamo, 180f, 180f, true, Offset(cx - w * 0.115f, h * 0.115f), Size(w * 0.23f, h * 0.17f))
        drawRoundRect(Color(0xFF1D222B), Offset(cx - w * 0.125f, h * 0.20f), Size(w * 0.035f, h * 0.065f), CornerRadius(6f, 6f))
        drawRoundRect(Color(0xFF1D222B), Offset(cx + w * 0.09f, h * 0.20f), Size(w * 0.035f, h * 0.065f), CornerRadius(6f, 6f))
        if (hasNvg) {
            drawCircle(Color(0xFF5AC8FA), radius = w * 0.022f, center = Offset(cx - w * 0.032f, h * 0.165f))
            drawCircle(Color(0xFF5AC8FA), radius = w * 0.022f, center = Offset(cx + w * 0.032f, h * 0.165f))
        }
    }
}
