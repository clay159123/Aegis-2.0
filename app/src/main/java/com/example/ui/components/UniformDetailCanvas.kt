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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
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
    leaderRole: LeaderRole = UniformRegaliaRules.resolveLeaderRole(operator, squads),
    kitOverride: WardrobeKit? = null
) {
    val extra = LocalAegisColors.current
    val textMeasurer = rememberTextMeasurer()
    val ownedIds = awardedMedals.map { it.id }.toSet()
    val inferredKit = (kitOverride ?: when {
        operator.uniformMode == UniformMode.FIELD_TACTICAL -> WardrobeKit.FIELD
        operator.dressStyle.isCutawayMess -> WardrobeKit.MESS
        operator.activeWardrobeKit == WardrobeKit.BRAVOS -> WardrobeKit.BRAVOS
        else -> WardrobeKit.ALPHAS
    }).let { k ->
        // Item 112: pilots have no field uniform; the flight suit stands in everywhere
        if (k == WardrobeKit.FIELD && operator.isAviator) WardrobeKit.BRAVOS else k
    }
    val renderOperator = (if (kitOverride != null) {
        // An explicit kit comes from the duty resolver, which only puts HQ staff in Field at a field school
        TacticalCalculator.resolveKitAppearance(operator, kitOverride, squads, ownedIds, allowFieldForHqStaff = true)
    } else {
        operator
    }).let { op ->
        // Left-shoulder patch follows squad membership (or HQ / Unassigned) at render time
        op.copy(tailorOverrides = op.tailorOverrides.copy(unitPatch = UniformRegaliaRules.resolveUnitPatch(op, squads)))
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
    // The gold-framed unit citation is a squad award: worn by everyone who was in the squad when it was earned
    val unitAwards = if (renderOperator.tailorOverrides.showUnitCitation) {
        UniformRegaliaRules.unitAwardsFor(renderOperator, squads)
    } else {
        emptyList()
    }
    // Item 142: one environment school badge above the name plate and unit awards
    val environmentBadge = UniformRegaliaRules.environmentBadgeFor(renderOperator, ownedIds)
    val combatPatch = UniformRegaliaRules.resolveCombatPatch(renderOperator)
    val sterileField = UniformRegaliaRules.isSfSquadMember(renderOperator, squads)
    val marksmanshipTier = UniformRegaliaRules.resolveMarksmanshipTier(renderOperator, equippedGear)
    val overseasBars = UniformRegaliaRules.resolveOverseasBars(renderOperator)
    val awardDevices = (rackDecorations + hangingMedals)
        .distinctBy { it.id }
        .associate { medal ->
            medal.id to UniformRegaliaRules.resolveAwardDevices(renderOperator, medal.id, allCitations)
        }

    val equippedWeaponId = equippedGear.firstOrNull { it.slot == GearSlot.WEAPON }?.id
    val hasNvg = equippedGear.any { it.slot == GearSlot.OPTICS && it.id.contains("nvg", ignoreCase = true) }
    val armorId = equippedGear.firstOrNull { it.slot == GearSlot.ARMOR }?.id
    val utilityId = equippedGear.firstOrNull { it.slot == GearSlot.UTILITY }?.id
    val helmetId = equippedGear.firstOrNull { it.slot == GearSlot.HELMET }?.id
    val hasHelmet = helmetId != null
    val hasWeapon = equippedWeaponId != null
    val skinTone = Color(renderOperator.skinTone.toneHex)
    val skinShadow = Color(renderOperator.skinTone.shadowHex)
    val buildScale = renderOperator.bodyBuild.widthScale
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

            // Body build widens or narrows the figure; the head draws itself back at normal width
            withTransform({ scale(buildScale, 1f, pivot = Offset(cx, h * 0.5f)) }) {
            when (inferredKit) {
                WardrobeKit.FIELD -> drawFieldTacticalFigure(
                    beardColor = if (TacticalCalculator.sfTier(renderOperator) >= 3) sfBeardColor(renderOperator) else null,
                    w = w,
                    h = h,
                    cx = cx,
                    operator = renderOperator,
                    wornRegalia = if (sterileField) wornRegalia.copy(shoulderTabs = emptyList(), chestBadges = emptyList(),
                        chestPinAboveRack = null, chestPinLeftPocket = null, chestPinRightPocket = null) else wornRegalia,
                    combatPatch = if (sterileField) UnitPatchStyle.NONE else combatPatch,
                    sterile = sterileField,
                    leaderRole = leaderRole,
                    equippedWeaponId = equippedWeaponId,
                    skinTone = skinTone,
                    skinShadow = skinShadow,
                    visorTone = visorTone,
                    armorId = armorId,
                    utilityId = utilityId,
                    helmetId = helmetId,
                    hasHelmet = hasHelmet,
                    hasWeapon = hasWeapon,
                    hasNvg = hasNvg,
                    textMeasurer = textMeasurer
                )
                // Item 93: aviators wear the flight suit in the Bravos slot
                WardrobeKit.BRAVOS -> if (renderOperator.isAviator) drawFieldTacticalFigure(
                    w = w,
                    h = h,
                    cx = cx,
                    // Item 113: SF aviators fly in black
                    operator = renderOperator.copy(
                        camoPattern = if (TacticalCalculator.isSfAviator(renderOperator)) CamoPattern.FLIGHT_BLACK else CamoPattern.FLIGHT_SAGE
                    ),
                    wornRegalia = wornRegalia,
                    combatPatch = combatPatch,
                    leaderRole = LeaderRole.NONE,
                    equippedWeaponId = null,
                    skinTone = skinTone,
                    skinShadow = skinShadow,
                    visorTone = visorTone,
                    armorId = null,
                    utilityId = null,
                    helmetId = null,
                    hasHelmet = false,
                    hasWeapon = false,
                    hasNvg = false,
                    textMeasurer = textMeasurer,
                    flightSuit = true,
                    // Item 138: wings on the flight suit match the pilot's aviator badge level
                    aviatorWings = when {
                        "insig_master_aviator_wings" in ownedIds -> InsigniaShape.MASTER_AVIATOR_WINGS
                        "insig_senior_aviator_wings" in ownedIds -> InsigniaShape.SENIOR_AVIATOR_WINGS
                        else -> InsigniaShape.AVIATOR_WINGS
                    }
                ) else drawBravosShortSleeveFigure(
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
                    unitAwards = unitAwards,
                    marksmanshipTier = marksmanshipTier,
                    leaderRole = leaderRole,
                    awardDevices = awardDevices,
                    textMeasurer = textMeasurer,
                    environmentBadge = environmentBadge
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
                    unitAwards = unitAwards,
                    combatPatch = combatPatch,
                    marksmanshipTier = marksmanshipTier,
                    overseasBars = overseasBars,
                    leaderRole = leaderRole,
                    awardDevices = awardDevices,
                    textMeasurer = textMeasurer,
                    squads = squads,
                    kit = inferredKit,
                    environmentBadge = environmentBadge
                )
            }
            }
        }
    }
}

/** Draws [block] at natural width inside a figure scaled by the soldier's build, so heads keep their shape. */
internal inline fun DrawScope.unscaledForBuild(operator: Operator, cx: Float, block: DrawScope.() -> Unit) {
    val s = operator.bodyBuild.widthScale
    if (s == 1f) block() else withTransform({ scale(1f / s, 1f, pivot = Offset(cx, 0f)) }) { block() }
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
    unitAwards: List<Pair<UnitAwardType, Int>>,
    combatPatch: UnitPatchStyle,
    marksmanshipTier: MarksmanshipBadgeTier,
    overseasBars: Int,
    leaderRole: LeaderRole,
    awardDevices: Map<String, UniformRegaliaRules.AwardDeviceInfo>,
    textMeasurer: TextMeasurer,
    squads: List<TacticalSquad> = emptyList(),
    kit: WardrobeKit = WardrobeKit.ALPHAS,
    environmentBadge: MedalDefinition? = null
) {
    val style = operator.dressStyle
    val tunicCol = Color(style.tunicHex)
    val lapelCol = Color(style.lapelHex)
    val trimCol = Color(style.trimHex)
    val trouserStripeCol = Color(style.trouserStripeHex)
    // Berets were already resolved by resolveKitAppearance; only swap one for a cap on a non-Bravos kit
    val resolvedHeadgear = if (operator.headgearStyle.kind == HeadgearKind.BERET && kit != WardrobeKit.BRAVOS) {
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
    // Mess dress carries no collar brass; the narrow mess lapels would push it onto the shirt
    if (!style.isCutawayMess) drawBranchCollarInsignia(cx, h, w, operator.specialty, tier, trimCol)

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
        (tier == RankTier.OFFICER || tier == RankTier.WARRANT) && style == DressUniformStyle.HONOR_GUARD_IVORY -> Color(0xFF0C1526)
        tier == RankTier.OFFICER || tier == RankTier.WARRANT -> lapelCol
        else -> tunicCol
    }
    val epauletBorder = if (tier.isOfficer) trimCol else lapelCol
    val isFieldGrade = operator.rank.isFieldGradeOfficer
    val epTop = if ((tier == RankTier.OFFICER || tier == RankTier.WARRANT)) h * 0.296f else h * 0.298f
    val epHeight = if ((tier == RankTier.OFFICER || tier == RankTier.WARRANT)) h * 0.036f else h * 0.032f
    val epWidth = if ((tier == RankTier.OFFICER || tier == RankTier.WARRANT)) w * 0.114f else w * 0.110f
    val epLeftX = cx - w * 0.146f - epWidth
    val epRightX = cx + w * 0.146f
    val epStroke = when {
        tier == RankTier.FLAG_OFFICER || isFieldGrade -> 2.0f
        tier == RankTier.OFFICER || tier == RankTier.WARRANT -> 1.6f
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
    } else if (tier == RankTier.OFFICER || tier == RankTier.WARRANT) {
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
    // Up to 3 tabs stack top-down in precedence order; a full stack uses slightly slimmer tabs
    val wornTabs = wornRegalia.shoulderTabs.take(TacticalCalculator.MAX_WORN_SHOULDER_TABS)
    val tabHeightFrac = if (wornTabs.size >= 3) 0.019f else 0.021f
    val tabStepFrac = tabHeightFrac + 0.004f
    wornTabs.forEachIndexed { idx, tab ->
        drawWrappedShoulderTab(
            sideSign = 1f,
            topFrac = 0.344f + idx * tabStepFrac,
            tabColor = Color(tab.insigniaColorHex),
            sleeveFaceX = ::sleeveFaceX,
            h = h,
            heightFrac = tabHeightFrac
        )
    }

    val ssiTopFrac = if (wornTabs.isEmpty()) 0.350f else 0.344f + wornTabs.size * tabStepFrac + 0.006f
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
        unitAwards = unitAwards,
        leaderRole = leaderRole,
        textMeasurer = textMeasurer,
        environmentBadge = environmentBadge,
        sfTier = com.example.domain.TacticalCalculator.sfTier(operator)
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
        RankTier.OFFICER, RankTier.WARRANT -> {
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

    // Session 41 (item 73): one stripe per 3 years of enlisted service
    val stripeCount = if (operator.tailorOverrides.showServiceStripes) {
        com.example.domain.GameCalendar.serviceStripes(operator).coerceIn(0, 8)
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
            col = Color(insig.insigniaColorHex),
            combatJumps = operator.combatJumps
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
            col = Color(leftPocketSchoolPin.insigniaColorHex),
            combatJumps = operator.combatJumps
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
                col = Color(insig.insigniaColorHex),
                combatJumps = operator.combatJumps
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
            center = Offset(rightChestCenterX, h * (rightPocketBadgeY(com.example.domain.TacticalCalculator.sfTier(operator)) - CHEST_LIFT)),
            badgeW = w * 0.088f,
            badgeH = h * 0.044f,
            col = Color(insig.insigniaColorHex),
            combatJumps = operator.combatJumps
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
    unscaledForBuild(operator, cx) {
        drawRect(skinShadow, Offset(cx - w * 0.052f, h * 0.242f), Size(w * 0.104f, h * 0.062f))
        // Art 1: a face of their own (head shape, hair, features, age)
        with(SoldierFace) {
            drawHead(
                box = androidx.compose.ui.geometry.Rect(cx - w * 0.102f, h * 0.082f, cx + w * 0.102f, h * 0.266f),
                op = operator, skin = skinTone, shadow = skinShadow,
                cover = if (operator.headgearStyle.kind == HeadgearKind.BERET) SoldierFace.Cover.BERET else SoldierFace.Cover.CAP,
                age = SoldierFace.ageOf(operator)
            )
        }

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
            // Item 66: squad members wear a flash in their squad's division patch colors (fill and diagonal
            // stripe); soldiers with no squad wear the plain Army flash for their beret
            // Item 68: the SF squad wears a black and red SF flash
            val squadPatch = operator.tailorOverrides.unitPatch.takeIf { it.isDivision || it.isSfTeamPatch }
            val flashFill = squadPatch?.let { Color(it.primaryHex) } ?: when (operator.headgearStyle) {
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
            if (squadPatch != null) {
                drawLine(
                    color = Color(squadPatch.accentHex),
                    start = Offset(flashCx - flashW * 0.34f, flashTopY + flashH * 0.72f),
                    end = Offset(flashCx + flashW * 0.34f, flashTopY + flashH * 0.14f),
                    strokeWidth = 1.5f
                )
            } else if (operator.headgearStyle == HeadgearStyle.MAROON_BERET) {
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
}

private fun DrawScope.drawWrappedShoulderTab(
    sideSign: Float,
    topFrac: Float,
    tabColor: Color,
    sleeveFaceX: (Float, Float, Float) -> Float,
    h: Float,
    heightFrac: Float = 0.021f
) {
    val botFrac = topFrac + heightFrac
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

    val shieldPrimary = Color(patchStyle.primaryHex)
    val patchFlashCol = Color(patchStyle.accentHex)

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
    drawUnitPatchEmblem(
        patchStyle = patchStyle,
        center = emblemCenter,
        unitW = w,
        unitH = h,
        accent = patchFlashCol
    )
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
        OperatorRank.SGM, OperatorRank.CSM -> Triple(3, 3, true)
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
        // Item 110: the Command Sergeant Major's star sits in a wreath
        if (rank == OperatorRank.CSM) {
            val r = w * 0.0145f
            drawArc(trimCol, startAngle = 20f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(sx - r, h * starFrac - r), size = Size(r * 2f, r * 2f), style = Stroke(width = 1.2f, cap = StrokeCap.Round))
        }
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
    col: Color,
    /** Item 39: combat jump stars on jump wings (0..5). */
    combatJumps: Int = 0
) {
    val cx = center.x
    val cy = center.y
    val silver = Color(0xFFE0E6F0)
    val gold = Color(0xFFF2D272)
    val darkBack = Color(0xFF121722)

    when (shape) {
        // EIB: musket on an infantry-blue bar, no wreath. CIB: the same inside an oak wreath.
        InsigniaShape.RIFLE_BADGE, InsigniaShape.CIB_BADGE -> {
            if (shape == InsigniaShape.CIB_BADGE) {
                drawLaurelWreathArcs(center, badgeH * 0.62f, silver, (badgeH * 0.11f).coerceAtLeast(1.3f))
            }
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
        // CAB: bayonet over a grenade inside an oak wreath
        InsigniaShape.CAB_BADGE -> {
            drawLaurelWreathArcs(center, badgeH * 0.62f, silver, (badgeH * 0.11f).coerceAtLeast(1.3f))
            drawOval(col, topLeft = Offset(cx - badgeH * 0.17f, cy - badgeH * 0.06f), size = Size(badgeH * 0.34f, badgeH * 0.40f))
            drawOval(silver, topLeft = Offset(cx - badgeH * 0.17f, cy - badgeH * 0.06f), size = Size(badgeH * 0.34f, badgeH * 0.40f), style = Stroke(1.0f))
            drawLine(silver, Offset(cx, cy - badgeH * 0.46f), Offset(cx, cy + badgeH * 0.30f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            drawLine(silver, Offset(cx - badgeH * 0.12f, cy - badgeH * 0.12f), Offset(cx + badgeH * 0.12f, cy - badgeH * 0.12f), strokeWidth = 1.4f, cap = StrokeCap.Round)
        }
        // EFMB: medical cross over a stretcher, no wreath. CMB: cross and caduceus inside an oak wreath.
        InsigniaShape.MEDICAL_BADGE, InsigniaShape.COMBAT_MEDICAL_BADGE -> {
            val combat = shape == InsigniaShape.COMBAT_MEDICAL_BADGE
            if (combat) {
                drawLaurelWreathArcs(center, badgeH * 0.58f, silver, (badgeH * 0.11f).coerceAtLeast(1.3f))
            } else {
                // Stretcher: two poles with handles behind the cross
                drawLine(silver, Offset(cx - badgeW * 0.44f, cy - badgeH * 0.10f), Offset(cx + badgeW * 0.44f, cy - badgeH * 0.10f), strokeWidth = 1.4f, cap = StrokeCap.Round)
                drawLine(silver, Offset(cx - badgeW * 0.44f, cy + badgeH * 0.10f), Offset(cx + badgeW * 0.44f, cy + badgeH * 0.10f), strokeWidth = 1.4f, cap = StrokeCap.Round)
            }
            drawCircle(darkBack, radius = badgeH * 0.42f, center = center)
            drawCircle(col, radius = badgeH * 0.42f, center = center, style = Stroke(1.2f))
            drawLine(col, Offset(cx, cy - badgeH * 0.26f), Offset(cx, cy + badgeH * 0.26f), strokeWidth = 2.4f)
            drawLine(col, Offset(cx - badgeH * 0.26f, cy), Offset(cx + badgeH * 0.26f, cy), strokeWidth = 2.4f)
            if (combat) {
                // Caduceus: gold staff with a wing bar and a coiled snake
                drawLine(gold, Offset(cx, cy - badgeH * 0.34f), Offset(cx, cy + badgeH * 0.34f), strokeWidth = 1.2f)
                drawLine(gold, Offset(cx - badgeH * 0.14f, cy - badgeH * 0.28f), Offset(cx + badgeH * 0.14f, cy - badgeH * 0.28f), strokeWidth = 1.1f, cap = StrokeCap.Round)
                drawArc(gold, startAngle = 90f, sweepAngle = 180f, useCenter = false, topLeft = Offset(cx - badgeH * 0.08f, cy - badgeH * 0.18f), size = Size(badgeH * 0.16f, badgeH * 0.16f), style = Stroke(1.0f))
                drawArc(gold, startAngle = -90f, sweepAngle = 180f, useCenter = false, topLeft = Offset(cx - badgeH * 0.08f, cy - badgeH * 0.02f), size = Size(badgeH * 0.16f, badgeH * 0.16f), style = Stroke(1.0f))
            }
        }
        InsigniaShape.WINGS, InsigniaShape.STAR_WINGS, InsigniaShape.SENIOR_PARA_WINGS -> {
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
            } else if (shape == InsigniaShape.SENIOR_PARA_WINGS) {
                drawStar(Offset(cx, cy - badgeH * 0.32f), badgeH * 0.16f, badgeH * 0.07f, col)
            }
            // Combat jump device: small bronze stars along the top of the wings, centered
            val jumpStars = combatJumps.coerceIn(0, 5)
            if (jumpStars > 0) {
                val bronze = Color(0xFFC08A3E)
                val r = (badgeH * 0.085f).coerceAtLeast(0.9f)
                val step = badgeW * 0.15f
                val startX = cx - step * (jumpStars - 1) / 2f
                repeat(jumpStars) { i ->
                    drawStar(Offset(startX + step * i, cy - badgeH * 0.14f), r, r * 0.45f, bronze)
                }
            }
        }
        InsigniaShape.MOUNTAIN_BADGE, InsigniaShape.JUNGLE_BADGE, InsigniaShape.ARCTIC_BADGE, InsigniaShape.SNIPER_BADGE ->
            drawEnvironmentOrSniperBadge(shape, center, badgeW, badgeH, col)
        InsigniaShape.MFF_WINGS -> {
            // Wings with a parachute and an upright dagger through the middle
            drawLine(col, Offset(cx - badgeW * 0.48f, cy + badgeH * 0.02f), Offset(cx + badgeW * 0.48f, cy + badgeH * 0.02f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            drawLine(col, Offset(cx - badgeW * 0.38f, cy + badgeH * 0.16f), Offset(cx + badgeW * 0.38f, cy + badgeH * 0.16f), strokeWidth = 1.6f, cap = StrokeCap.Round)
            drawArc(col, 180f, 180f, true, Offset(cx - badgeW * 0.15f, cy - badgeH * 0.28f), Size(badgeW * 0.30f, badgeH * 0.40f))
            drawLine(darkBack, Offset(cx, cy - badgeH * 0.42f), Offset(cx, cy + badgeH * 0.30f), strokeWidth = 2.0f)
            drawLine(gold, Offset(cx, cy - badgeH * 0.42f), Offset(cx, cy + badgeH * 0.30f), strokeWidth = 1.1f, cap = StrokeCap.Round)
            drawLine(gold, Offset(cx - badgeW * 0.06f, cy + badgeH * 0.18f), Offset(cx + badgeW * 0.06f, cy + badgeH * 0.18f), strokeWidth = 1.2f)
            val mffStars = combatJumps.coerceIn(0, 5)
            if (mffStars > 0) {
                val bronze = Color(0xFFC08A3E)
                val r = (badgeH * 0.085f).coerceAtLeast(0.9f)
                val step = badgeW * 0.15f
                val startX = cx - step * (mffStars - 1) / 2f
                repeat(mffStars) { i -> drawStar(Offset(startX + step * i, cy - badgeH * 0.10f), r, r * 0.45f, bronze) }
            }
        }
        InsigniaShape.DIVER_BADGE -> {
            // Dive helmet with a faceplate, over a pair of crossed tridents
            drawLine(col, Offset(cx - badgeW * 0.34f, cy + badgeH * 0.34f), Offset(cx + badgeW * 0.34f, cy - badgeH * 0.30f), strokeWidth = 1.3f, cap = StrokeCap.Round)
            drawLine(col, Offset(cx + badgeW * 0.34f, cy + badgeH * 0.34f), Offset(cx - badgeW * 0.34f, cy - badgeH * 0.30f), strokeWidth = 1.3f, cap = StrokeCap.Round)
            drawCircle(col, radius = badgeH * 0.30f, center = Offset(cx, cy))
            drawCircle(darkBack, radius = badgeH * 0.13f, center = Offset(cx, cy - badgeH * 0.02f))
            drawRect(col, Offset(cx - badgeW * 0.14f, cy + badgeH * 0.24f), Size(badgeW * 0.28f, badgeH * 0.10f))
        }
        InsigniaShape.AVIATOR_WINGS, InsigniaShape.SENIOR_AVIATOR_WINGS, InsigniaShape.MASTER_AVIATOR_WINGS -> {
            // Long straight wings with a shield in the middle; a star (Senior) or wreathed star (Master) above
            drawLine(col, Offset(cx - badgeW * 0.50f, cy - badgeH * 0.02f), Offset(cx + badgeW * 0.50f, cy - badgeH * 0.02f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            drawLine(col, Offset(cx - badgeW * 0.42f, cy + badgeH * 0.13f), Offset(cx + badgeW * 0.42f, cy + badgeH * 0.13f), strokeWidth = 1.6f, cap = StrokeCap.Round)
            drawLine(col, Offset(cx - badgeW * 0.30f, cy + badgeH * 0.26f), Offset(cx + badgeW * 0.30f, cy + badgeH * 0.26f), strokeWidth = 1.2f, cap = StrokeCap.Round)
            val shield = Path().apply {
                moveTo(cx - badgeW * 0.10f, cy - badgeH * 0.18f)
                lineTo(cx + badgeW * 0.10f, cy - badgeH * 0.18f)
                lineTo(cx + badgeW * 0.10f, cy + badgeH * 0.08f)
                quadraticTo(cx + badgeW * 0.08f, cy + badgeH * 0.26f, cx, cy + badgeH * 0.34f)
                quadraticTo(cx - badgeW * 0.08f, cy + badgeH * 0.26f, cx - badgeW * 0.10f, cy + badgeH * 0.08f)
                close()
            }
            drawPath(shield, col)
            drawPath(shield, darkBack.copy(alpha = 0.55f), style = Stroke(width = 0.9f))
            if (shape == InsigniaShape.MASTER_AVIATOR_WINGS) {
                drawLaurelWreathArcs(Offset(cx, cy - badgeH * 0.34f), badgeH * 0.20f, col, 1.2f)
            }
            if (shape != InsigniaShape.AVIATOR_WINGS) {
                drawStar(Offset(cx, cy - badgeH * 0.34f), badgeH * 0.15f, badgeH * 0.065f, col)
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
    leaderRole: LeaderRole,
    equippedWeaponId: String?,
    skinTone: Color,
    skinShadow: Color,
    visorTone: Color,
    armorId: String?,
    utilityId: String?,
    helmetId: String?,
    hasHelmet: Boolean,
    hasWeapon: Boolean,
    hasNvg: Boolean,
    textMeasurer: TextMeasurer,
    flightSuit: Boolean = false,
    /** Item 58: SF squad field uniforms carry no name, rank, tabs, badges or awards. */
    sterile: Boolean = false,
    /** Item 138: which aviator wings the flight suit name tag carries. */
    aviatorWings: InsigniaShape = InsigniaShape.AVIATOR_WINGS,
    /** Session 30: SF3 soldiers wear a beard in the field (null = clean-shaven). */
    beardColor: Color? = null
) {
    val primaryCamo = Color(operator.camoPattern.primaryHex)
    // Item 105: sewn-on items are subdued in the colors of the camo they're sewn onto
    val (subduedLow, subduedHigh) = subduedPaletteFor(operator.camoPattern)
    val threadCol = lerp(subduedLow, subduedHigh, 0.88f)
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
    val uniformOutline = Path().apply {
        addPath(sleeveL)
        addPath(sleeveR)
        addPath(torsoPath)
    }
    // Art 4a: the pattern's own shapes (flight suits stay one color with a faint weave)
    with(FieldCamo) {
        drawCamoPattern(operator.camoPattern, uniformOutline, Rect(cx - w * 0.42f, h * 0.33f, cx + w * 0.42f, h * 0.97f), w * 0.040f,
            primaryCamo, secondaryCamo, accentCamo)
    }
    if (!flightSuit) drawFieldUniformTexture(w, h, cx, uniformOutline, primaryCamo, secondaryCamo, accentCamo)
    // Art 4a: arms read apart from the body: a fold where each sleeve meets the torso, reinforced elbows, one-side shading
    for (s in listOf(-1f, 1f)) {
        drawLine(Color.Black.copy(alpha = 0.32f), Offset(cx + s * w * 0.251f, h * 0.50f), Offset(cx + s * w * 0.258f, h * 0.96f), strokeWidth = w * 0.007f)
        drawLine(Color.White.copy(alpha = 0.06f), Offset(cx + s * w * 0.262f, h * 0.52f), Offset(cx + s * w * 0.268f, h * 0.96f), strokeWidth = w * 0.004f)
    }
    with(FieldCamo) { shadeFigure(uniformOutline, cx, w) }
    drawFieldBeltAndTrousers(w, h, cx, primaryCamo, secondaryCamo, utilityId)

    // Field sleeve cross-section (u = 0 inner seam, 1 outer silhouette), following the sleeve paths above
    fun fieldSleeveFaceX(sideSign: Float, yFrac: Float, u: Float): Float {
        val inner = if (yFrac <= 0.54f) 0.254f - (yFrac - 0.378f).coerceAtLeast(0f) / 0.162f * 0.010f else 0.244f
        // Outer edge: sample the sleeve's cubic for the matching height
        var best = 0.254f
        var bestDy = Float.MAX_VALUE
        for (i in 0..40) {
            val t = i / 40f
            val mt = 1f - t
            val bx = mt * mt * mt * 0.254f + 3 * mt * mt * t * 0.350f + 3 * mt * t * t * 0.382f + t * t * t * 0.396f
            val by = mt * mt * mt * 0.378f + 3 * mt * mt * t * 0.405f + 3 * mt * t * t * 0.485f + t * t * t * 0.96f
            val dy = kotlin.math.abs(by - yFrac)
            if (dy < bestDy) { bestDy = dy; best = bx }
        }
        val outer = best
        return cx + sideSign * w * (inner + (outer - inner) * u)
    }

    // Wearer's left sleeve: subdued tab stack (SF, Ranger, Sapper, Airborne; max 3) above the subdued squad patch
    val wornTabs = wornRegalia.shoulderTabs.take(TacticalCalculator.MAX_WORN_SHOULDER_TABS)
    val tabHeightFrac = if (wornTabs.size >= 3) 0.017f else 0.019f
    val tabStepFrac = tabHeightFrac + 0.003f
    val tabsTopFrac = 0.432f
    val patchHeightFrac = 0.064f
    val squadPatch = operator.tailorOverrides.unitPatch
    val showSquadPatch = squadPatch != UnitPatchStyle.NONE && squadPatch != UnitPatchStyle.AUTO_MOS
    val squadPatchTopFrac = tabsTopFrac + wornTabs.size * tabStepFrac + 0.004f
    val showCombatPatch = combatPatch != UnitPatchStyle.NONE && combatPatch != UnitPatchStyle.AUTO_MOS

    // Velcro boxes sized to what they hold, so every tab and patch sits inside one
    val boxPad = 0.008f
    val emptyBoxBottom = tabsTopFrac + patchHeightFrac
    val leftBoxBottom = maxOf(
        emptyBoxBottom,
        if (showSquadPatch) squadPatchTopFrac + patchHeightFrac else tabsTopFrac + wornTabs.size * tabStepFrac
    ) + boxPad
    drawFieldVelcroBox(1f, tabsTopFrac - boxPad - 0.004f, leftBoxBottom, ::fieldSleeveFaceX, h, secondaryCamo)
    drawFieldVelcroBox(-1f, tabsTopFrac - boxPad - 0.004f, emptyBoxBottom + boxPad, ::fieldSleeveFaceX, h, secondaryCamo)
    drawFieldSleevePockets(::fieldSleeveFaceX, maxOf(leftBoxBottom, emptyBoxBottom + boxPad) + 0.014f, h, secondaryCamo)
    if (!flightSuit) {
        // Art 4a: reinforced elbow patches with a stitched edge
        for (side in listOf(-1f, 1f)) {
            val top = 0.735f; val bot = 0.835f
            val p = Path().apply {
                moveTo(fieldSleeveFaceX(side, top, 0.22f), h * top)
                quadraticTo(fieldSleeveFaceX(side, top - 0.012f, 0.6f), h * (top - 0.012f), fieldSleeveFaceX(side, top, 0.96f), h * top)
                lineTo(fieldSleeveFaceX(side, bot, 0.96f), h * bot)
                quadraticTo(fieldSleeveFaceX(side, bot + 0.012f, 0.6f), h * (bot + 0.012f), fieldSleeveFaceX(side, bot, 0.22f), h * bot)
                close()
            }
            drawPath(p, Color.Black.copy(alpha = 0.14f))
            drawPath(p, Color.Black.copy(alpha = 0.32f), style = Stroke(width = 0.8f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2f, 1.6f))))
        }
        // Pen pocket on the wearer's left forearm
        val penTop = 0.865f
        drawLine(Color.Black.copy(alpha = 0.3f), Offset(fieldSleeveFaceX(1f, penTop, 0.40f), h * penTop), Offset(fieldSleeveFaceX(1f, penTop, 0.80f), h * penTop), strokeWidth = 1f)
        for (u in listOf(0.48f, 0.58f, 0.68f)) drawLine(Color.Black.copy(alpha = 0.25f), Offset(fieldSleeveFaceX(1f, penTop, u), h * penTop), Offset(fieldSleeveFaceX(1f, penTop + 0.05f, u), h * (penTop + 0.05f)), strokeWidth = 0.8f)
    }

    subduedLayer(subduedLow, subduedHigh) {
        wornTabs.forEachIndexed { idx, tab ->
            drawWrappedShoulderTab(
                sideSign = 1f,
                topFrac = tabsTopFrac + idx * tabStepFrac,
                tabColor = Color(tab.insigniaColorHex),
                sleeveFaceX = ::fieldSleeveFaceX,
                h = h,
                heightFrac = tabHeightFrac
            )
        }
        if (showSquadPatch) {
            drawWrappedUnitShoulderPatch(
                sideSign = 1f,
                topFrac = squadPatchTopFrac,
                patchStyle = squadPatch,
                specialty = operator.specialty,
                trimCol = Color(0xFF8A8A7A),
                accentCol = accentCamo,
                sleeveFaceX = ::fieldSleeveFaceX,
                w = w,
                h = h
            )
        }
        // Wearer's right sleeve: subdued combat patch from the most recent campaign; blank if none
        if (showCombatPatch) {
            drawWrappedUnitShoulderPatch(
                sideSign = -1f,
                topFrac = tabsTopFrac,
                patchStyle = combatPatch,
                specialty = operator.specialty,
                trimCol = Color(0xFF8A8A7A),
                accentCol = accentCamo,
                sleeveFaceX = ::fieldSleeveFaceX,
                w = w,
                h = h
            )
        }
    }

    if (!flightSuit) drawFieldLoadBearing(w, h, cx, fieldLoadBearingStyleFor(armorId), utilityId, secondaryCamo, accentCamo)

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
    if (!sterile) {
    val tabCenter = Offset(cx, rankTabTop + rankTabH * 0.5f)
    when (operator.rank.tier) {
        RankTier.FLAG_OFFICER -> {
            val stars = operator.rank.insigniaCount.coerceIn(1, 4)
            val starR = (rankTabW * 0.11f).coerceAtLeast(1.8f)
            val stepX = rankTabW / (stars + 1)
            repeat(stars) { s ->
                drawStar(Offset(rankTabLeft + stepX * (s + 1), tabCenter.y), starR, starR * 0.45f, threadCol)
            }
        }
        RankTier.OFFICER, RankTier.WARRANT -> {
            drawOfficerRankDevice(
                rank = operator.rank,
                center = tabCenter,
                boxW = rankTabW * 0.78f,
                boxH = rankTabH * 0.78f,
                outlineColor = threadCol,
                subdued = true
            )
        }
        RankTier.SNCO, RankTier.NCO, RankTier.SOLDIER -> {
            if (operator.rank == OperatorRank.SPC) {
                val spcCol = threadCol
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
                val chevCol = threadCol
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

    }

    // Leader tab below the rank: Squad Leader green with brass border and gold star,
    // Team Leader green with silver border and silver chevron
    if (leaderRole != LeaderRole.NONE && !sterile) {
        val isTeamLeader = leaderRole == LeaderRole.TEAM_LEADER
        val leadW = rankTabW * if (isTeamLeader) 0.9f else 1f
        val leadH = h * 0.016f
        val leadTop = rankTabTop + rankTabH + h * 0.004f
        val leadBorder = if (isTeamLeader) Color(0xFFD8DEE8) else Color(0xFFF2D272)
        drawRoundRect(Color(0xFF285938), Offset(cx - leadW / 2f, leadTop), Size(leadW, leadH), CornerRadius(2f, 2f))
        drawRoundRect(leadBorder, Offset(cx - leadW / 2f, leadTop), Size(leadW, leadH), CornerRadius(2f, 2f), style = Stroke(width = 1.0f))
        val leadCenter = Offset(cx, leadTop + leadH / 2f)
        if (isTeamLeader) {
            drawLeaderChevron(leadCenter, leadH * 0.55f, leadH * 0.45f, Color(0xFFE6EBF2))
        } else {
            drawMiniStar(leadCenter, (leadH * 0.40f).coerceAtLeast(1.3f), (leadH * 0.18f).coerceAtLeast(0.6f), Color(0xFFF5D576))
        }
    }

    // Name tape (wearer's right) and AEGIS branch tape (wearer's left), both camo with thread lettering
    val tapeW = w * 0.128f
    val tapeH = h * 0.024f
    val tapeTop = h * 0.512f
    val nameCx = cx - w * 0.112f
    val branchCx = cx + w * 0.112f
    if (flightSuit) {
        // Item 93: flight suit front zipper and the black leather name tag with gold aviator wings
        drawLine(Color.Black.copy(alpha = 0.45f), Offset(cx, h * 0.31f), Offset(cx, h * 0.86f), strokeWidth = 1.6f)
        drawLine(Color(0xFFB8BCA8).copy(alpha = 0.6f), Offset(cx + 1.2f, h * 0.31f), Offset(cx + 1.2f, h * 0.86f), strokeWidth = 0.6f)
        val tagW = w * 0.150f
        val tagH = h * 0.046f
        val tagTop = h * 0.470f
        drawRoundRect(Color(0xFF15120F), Offset(branchCx - tagW / 2f, tagTop), Size(tagW, tagH), CornerRadius(2f, 2f))
        drawRoundRect(Color(0xFF3A332B), Offset(branchCx - tagW / 2f, tagTop), Size(tagW, tagH), CornerRadius(2f, 2f), style = Stroke(width = 0.9f))
        // Wings across the top of the tag
        val wingY = tagTop + tagH * 0.30f
        val gold = Color(0xFFE2C35E)
        drawCircle(gold, radius = tagH * 0.13f, center = Offset(branchCx, wingY))
        for (side in listOf(-1f, 1f)) {
            val wing = Path().apply {
                moveTo(branchCx + side * tagH * 0.12f, wingY - tagH * 0.06f)
                lineTo(branchCx + side * tagW * 0.42f, wingY - tagH * 0.12f)
                lineTo(branchCx + side * tagW * 0.30f, wingY + tagH * 0.06f)
                lineTo(branchCx + side * tagH * 0.12f, wingY + tagH * 0.06f)
                close()
            }
            drawPath(wing, gold)
        }
        // Item 138: Senior wings add a star above the shield; Master wings set it in a wreath
        if (aviatorWings != InsigniaShape.AVIATOR_WINGS) {
            val starAt = Offset(branchCx, tagTop - tagH * 0.16f)
            if (aviatorWings == InsigniaShape.MASTER_AVIATOR_WINGS) {
                drawLaurelWreathArcs(starAt, tagH * 0.30f, gold, 0.9f)
            }
            drawStar(starAt, tagH * 0.20f, tagH * 0.085f, gold)
        }
        drawFieldTapeText(operator.callsign.trim().uppercase().take(10), Offset(branchCx, tagTop + tagH * 0.72f), tagW, tagH * 0.5f, gold, textMeasurer)
    } else {
        listOf(nameCx to operator.callsign.trim().uppercase().take(10), branchCx to "AEGIS").forEach { (tx, label) ->
            drawRoundRect(secondaryCamo, Offset(tx - tapeW / 2f, tapeTop), Size(tapeW, tapeH), CornerRadius(1.5f, 1.5f))
            drawRoundRect(threadCol.copy(alpha = 0.55f), Offset(tx - tapeW / 2f, tapeTop), Size(tapeW, tapeH), CornerRadius(1.5f, 1.5f), style = Stroke(width = 0.8f))
            if (!sterile) drawFieldTapeText(label, Offset(tx, tapeTop + tapeH / 2f), tapeW, tapeH, threadCol, textMeasurer)
        }
    }

    // Above the AEGIS tape: Slot 1 (combat/expert) badge with the Slot 2 skill badge below it, sewn on subdued
    val fieldBadges = if (flightSuit || sterile) emptyList() else listOfNotNull(wornRegalia.chestPinAboveRack, wornRegalia.chestPinLeftPocket)
    val badgeW = w * 0.086f
    val badgeH = h * 0.022f
    subduedLayer(subduedLow, subduedHigh) {
        fieldBadges.forEachIndexed { idx, badge ->
            val shape = badge.insigniaShape ?: return@forEachIndexed
            val centerY = tapeTop - h * 0.016f - (fieldBadges.size - 1 - idx) * (badgeH + h * 0.010f)
            drawDetailedChestSchoolBadge(
                shape = shape,
                center = Offset(branchCx, centerY),
                badgeW = badgeW,
                badgeH = badgeH,
                col = Color(badge.insigniaColorHex),
                combatJumps = operator.combatJumps
            )
        }
    }

    if (hasWeapon) {
        drawEnhancedFieldWeapon(w = w, h = h, cx = cx, weaponId = equippedWeaponId, suppressed = utilityId == "utl_suppressor")
    }

    unscaledForBuild(operator, cx) {
    drawRect(skinShadow, Offset(cx - w * 0.05f, h * 0.30f), Size(w * 0.10f, h * 0.08f))
    // Art 4a: the face stays visible; headgear follows the mission (cap at home, helmet or boonie on ops)
    val headBox = androidx.compose.ui.geometry.Rect(cx - w * 0.095f, h * 0.14f, cx + w * 0.095f, h * 0.33f)
    with(SoldierFace) {
        drawHead(
            box = headBox,
            op = operator, skin = skinTone, shadow = skinShadow,
            cover = SoldierFace.Cover.CAP,
            age = SoldierFace.ageOf(operator)
        )
    }
    // Session 30: the SF3 field beard; the mouth (and its morale) shows over it
    if (beardColor != null && !flightSuit) {
        drawArc(beardColor, 0f, 180f, true, Offset(cx - w * 0.097f, h * 0.196f), Size(w * 0.194f, h * 0.142f))
        drawRect(beardColor, Offset(cx - w * 0.094f, h * 0.236f), Size(w * 0.188f, h * 0.034f))
        drawRect(skinTone, Offset(cx - w * 0.07f, h * 0.200f), Size(w * 0.14f, h * 0.034f))
        with(SoldierFace) { drawMouth(headBox, operator, skinShadow.copy(alpha = 0.95f)) }
    }
    val deployed = operator.status == OperatorStatus.DEPLOYED
    val headgear = when {
        flightSuit || !deployed -> FieldCamo.FieldHeadgear.PATROL_CAP
        helmetId == "hlm_mesh_comms" -> FieldCamo.FieldHeadgear.HEADSET_CAP
        hasHelmet -> FieldCamo.FieldHeadgear.HELMET
        else -> FieldCamo.FieldHeadgear.BOONIE
    }
    with(FieldCamo) {
        when (headgear) {
            FieldCamo.FieldHeadgear.PATROL_CAP -> patrolCap(w, h, cx, operator.camoPattern, primaryCamo, secondaryCamo, accentCamo)
            FieldCamo.FieldHeadgear.HEADSET_CAP -> { patrolCap(w, h, cx, operator.camoPattern, primaryCamo, secondaryCamo, accentCamo); drawFieldHeadset(w, h, cx) }
            FieldCamo.FieldHeadgear.BOONIE -> boonie(w, h, cx, operator.camoPattern, primaryCamo, secondaryCamo, accentCamo)
            FieldCamo.FieldHeadgear.HELMET -> combatHelmet(w, h, cx, operator.camoPattern, primaryCamo, secondaryCamo, accentCamo, hasNvg)
        }
    }
    }
}

/** Draws [block] as subdued embroidery: every color maps by brightness onto the [low]..[high] thread range. */
private fun DrawScope.subduedLayer(low: Color, high: Color, block: DrawScope.() -> Unit) {
    val lr = low.red * 255f; val lg = low.green * 255f; val lb = low.blue * 255f
    val dr = high.red * 255f - lr; val dg = high.green * 255f - lg; val db = high.blue * 255f - lb
    val toThread = ColorMatrix(
        floatArrayOf(
            0.299f * dr / 255f, 0.587f * dr / 255f, 0.114f * dr / 255f, 0f, lr,
            0.299f * dg / 255f, 0.587f * dg / 255f, 0.114f * dg / 255f, 0f, lg,
            0.299f * db / 255f, 0.587f * db / 255f, 0.114f * db / 255f, 0f, lb,
            0f, 0f, 0f, 1f, 0f
        )
    )
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(toThread) }
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), paint)
        block()
        canvas.restore()
    }
}

/** Centered block lettering for field tapes, shrunk to fit the tape. */
private fun DrawScope.drawFieldTapeText(
    label: String,
    center: Offset,
    tapeW: Float,
    tapeH: Float,
    color: Color,
    textMeasurer: TextMeasurer
) {
    val fontSp = (tapeH * 0.62f / density).coerceIn(3.5f, 9f)
    var measured = textMeasurer.measure(
        text = label,
        style = TextStyle(color = color, fontSize = fontSp.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp),
        maxLines = 1,
        softWrap = false
    )
    if (measured.size.width > tapeW * 0.92f) {
        val shrink = tapeW * 0.92f / measured.size.width
        measured = textMeasurer.measure(
            text = label,
            style = TextStyle(color = color, fontSize = (fontSp * shrink).coerceAtLeast(3f).sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            softWrap = false
        )
    }
    drawText(measured, topLeft = Offset(center.x - measured.size.width / 2f, center.y - measured.size.height / 2f))
}

/** A camo velcro panel on the field sleeve spanning [topFrac]..[botFrac], wider than the patches it holds. */
private fun DrawScope.drawFieldVelcroBox(
    sideSign: Float,
    topFrac: Float,
    botFrac: Float,
    sleeveFaceX: (Float, Float, Float) -> Float,
    h: Float,
    color: Color
) {
    val uIn = 0.10f
    val uOut = 0.97f
    val inTop = sleeveFaceX(sideSign, topFrac, uIn)
    val outTop = sleeveFaceX(sideSign, topFrac, uOut)
    val inBot = sleeveFaceX(sideSign, botFrac, uIn)
    val outBot = sleeveFaceX(sideSign, botFrac, uOut)
    val rise = h * 0.006f
    val box = Path().apply {
        moveTo(inTop, h * topFrac + rise * 0.4f)
        quadraticTo((inTop + outTop) / 2f, h * topFrac - rise, outTop, h * topFrac + rise * 0.4f)
        lineTo(outBot, h * botFrac)
        quadraticTo((inBot + outBot) / 2f, h * botFrac + rise * 0.8f, inBot, h * botFrac)
        close()
    }
    drawPath(box, color = color)
    drawPath(
        box,
        brush = Brush.horizontalGradient(
            colors = if (sideSign > 0f) {
                listOf(Color.White.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.22f))
            } else {
                listOf(Color.Black.copy(alpha = 0.22f), Color.White.copy(alpha = 0.08f))
            },
            startX = minOf(inTop, outTop),
            endX = maxOf(inTop, outTop)
        )
    )
}

/** Item 105: dark backing and light thread for subdued patches, tabs and tapes on each camo. */
private fun subduedPaletteFor(camo: CamoPattern): Pair<Color, Color> = when (camo) {
    CamoPattern.WOODLAND_OLIVE -> Color(0xFF2B2E27) to Color(0xFFCDBD93)
    CamoPattern.NIGHT_CHARCOAL -> Color(0xFF111419) to Color(0xFF8E97A4)
    CamoPattern.ALPINE_FROST -> Color(0xFF39414A) to Color(0xFFDCE3EA)
    CamoPattern.ARID_SAND -> Color(0xFF4A3D2A) to Color(0xFFE2D2AE)
    CamoPattern.RANGER_GREEN -> Color(0xFF1C241A) to Color(0xFFA3B288)
    CamoPattern.LITTORAL_TIDE -> Color(0xFF1E262D) to Color(0xFFA9B8C2)
    CamoPattern.FLIGHT_SAGE -> Color(0xFF2E3326) to Color(0xFFBFC4A4)
    CamoPattern.FLIGHT_BLACK -> Color(0xFF0C0D0F) to Color(0xFF7A7F86)
}

/** Session 30: a natural beard color picked per soldier, so it never changes between screens. */
internal fun sfBeardColor(op: Operator): Color {
    val palette = listOf(0xFF1C1712, 0xFF3B2A1E, 0xFF5A3E28, 0xFF7A4A2A, 0xFFA8834F, 0xFF8E8A84)
    return Color(palette[Math.floorMod(op.id.hashCode(), palette.size)])
}
