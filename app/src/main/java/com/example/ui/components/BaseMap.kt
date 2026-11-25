package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.TacticalCalculator
import com.example.domain.model.*
import com.example.ui.theme.LocalAegisColors

/** Map units: the base is drawn in a 200 x 440 grid (Session 37 added the Parade Ground row) scaled to the card width (Session 22 added a fifth row). */
private const val MAP_W = 200f
private const val MAP_H = 440f

/** A fixed plot on the base map. */
internal data class BasePlot(val type: FacilityType?, val label: String, val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * Agreed layout: Airfield across the top; Range, HQ, Intel; Barracks, DFAC, Aid Station; Armory, Quartermaster and the
 * Airborne & Ranger Training Center (item 159, the old open lot); then the SF Compound (two plots, its own fence) and
 * the Main Gate on the wall.
 */
internal val BASE_PLOTS = listOf(
    BasePlot(FacilityType.AIRFIELD, "Airfield", 10f, 32f, 180f, 54f),
    BasePlot(FacilityType.TRAINING_RANGE, "Range", 10f, 94f, 58f, 60f),
    BasePlot(FacilityType.COMMAND_HQ, "Command HQ", 71f, 94f, 58f, 60f),
    BasePlot(FacilityType.RECON_ARRAY, "Intel", 132f, 94f, 58f, 60f),
    BasePlot(FacilityType.BARRACKS, "Barracks", 10f, 162f, 58f, 60f),
    BasePlot(FacilityType.DFAC, "DFAC", 71f, 162f, 58f, 60f),
    BasePlot(FacilityType.AID_STATION, "Aid Station", 132f, 162f, 58f, 60f),
    BasePlot(FacilityType.ARMORY_WORKSHOP, "Armory", 10f, 230f, 58f, 56f),
    BasePlot(FacilityType.LOGISTICS_DEPOT, "Quartermaster", 71f, 230f, 58f, 56f),
    BasePlot(FacilityType.AIRBORNE_RANGER_CENTER, "Abn & Ranger", 132f, 230f, 58f, 56f),
    // Session 37: the parade ground (Memorial Wall, item 172) and the Heritage Walk (campaign monuments, item 171)
    BasePlot(null, PARADE_LABEL, 10f, 294f, 117f, 76f),
    BasePlot(null, HERITAGE_LABEL, 132f, 294f, 58f, 76f),
    BasePlot(FacilityType.SF_COMPOUND, "SF Compound", 10f, 378f, 119f, 56f),
    BasePlot(FacilityType.MAIN_GATE, "Main Gate", 132f, 378f, 58f, 56f)
)

internal const val PARADE_LABEL = "Parade Ground"
internal const val HERITAGE_LABEL = "Heritage Walk"


/** Art stage for a level: 1 tent (Lv 1-3), 2 wood frame (4-6), 3 block (7-9), 4 compound (10). */
internal fun buildingArtStage(level: Int): Int = when {
    level >= 10 -> 4
    level >= 7 -> 3
    level >= 4 -> 2
    else -> 1
}

/** Perimeter and roads follow Command HQ: 1 wire fence and dirt (HQ 1-3), 2 chain-link and gravel (4-6), 3 masonry wall and paved (7-10). */
internal fun baseDevelopmentBand(hqLevel: Int): Int = when {
    hqLevel >= 7 -> 3
    hqLevel >= 4 -> 2
    else -> 1
}

/** One-line perk summary for [type] at [level]. */
fun facilityPerkSummary(type: FacilityType, level: Int): String {
    if (level <= 0) return "Not built"
    val rate = type.productionPerSec(level)
    val rateText = if (rate > 0) "${"%.1f".format(rate)} ${type.primaryResource.shortCode}/s • " else ""
    return rateText + when (type) {
        FacilityType.COMMAND_HQ -> {
            val staff = (2 + level).coerceAtMost(HqStaffRole.entries.size)
            val missions = 2 + (if (level >= 3) 1 else 0) + (if (level >= 6) 1 else 0) + (if (level >= 9) 1 else 0)
            "$staff staff slots • $missions mission slots • ${if (level >= 5) 2 else 1} construction crew${if (level >= 5) "s" else ""}"
        }
        FacilityType.BARRACKS -> "Field roster ${com.example.domain.CompoundRules.barracksSlots(level)}"
        FacilityType.SF_COMPOUND -> com.example.domain.CompoundRules.perkSummary(level)
        FacilityType.LOGISTICS_DEPOT -> "Supply costs -${3 * (level - 1)}%"
        FacilityType.ARMORY_WORKSHOP -> "Gear stats +${5 * (level - 1)}%"
        FacilityType.RECON_ARRAY -> "Missions -${4 * (level - 1)}% time • +${2 * (level - 1)}% survival"
        FacilityType.DFAC -> "Cohesion gains +${5 * level}% • requests -${6 * level}%"
        FacilityType.TRAINING_RANGE -> "Basics, Rifle Qual, EIB, Sniper +${2 * level}%"
        FacilityType.AIRBORNE_RANGER_CENTER -> "Airborne, Air Assault, Pathfinder, Ranger, MFF +${2 * level}%"
        FacilityType.MAIN_GATE -> "Morale baseline +$level • recruits -${com.example.domain.TacticalCalculator.GATE_RECRUIT_DISCOUNT_PER_LEVEL * level}%"
        FacilityType.AID_STATION -> "Recovery -${7 * level}% • ${5 * level}% casualty save"
        FacilityType.AIRFIELD -> com.example.domain.TacticalCalculator.airfieldPerkSummary(level)
    }
}

/**
 * The base as a map: fixed plots, buildings drawn by level, walls and roads by Command HQ level,
 * and a progress ring on anything under construction. Tapping a plot opens its building panel.
 */
@Composable
fun BaseMapCard(
    facilities: List<FacilityState>,
    resources: ResourceBundle,
    nowMillis: Long,
    onUpgrade: (FacilityType) -> Unit,
    onRush: (FacilityType) -> Unit,
    modifier: Modifier = Modifier,
    /** Session 37: soldiers lost; the Memorial Wall appears on the Parade Ground after the first. */
    fallenCount: Int = 0,
    /** Session 37: finished campaigns get a monument on the Heritage Walk. */
    completedCampaignIds: Set<String> = emptySet(),
    /** Soldiers, for each campaign record (who wears its combat patch). */
    operators: List<Operator> = emptyList(),
    /** Tapping the Memorial Wall opens the Honors Memorial. */
    onOpenMemorial: (() -> Unit)? = null,
    /** Session 38: tapping the Parade Ground opens its activities (and the Memorial from there). */
    onOpenParadeGround: (() -> Unit)? = null,
    /** Session 37: the HQ Base Map tab lets the map zoom (scroll sideways when zoomed). */
    zoomable: Boolean = false
) {
    val extra = LocalAegisColors.current
    val textMeasurer = rememberTextMeasurer()
    var selected by remember { mutableStateOf<FacilityType?>(null) }
    var showHeritage by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(1f) }
    val monuments = remember(completedCampaignIds) { monumentCampaigns(completedCampaignIds) }
    val hqLevel = TacticalCalculator.facilityLevel(facilities, FacilityType.COMMAND_HQ)
    val crews = TacticalCalculator.constructionCrews(facilities)
    val building = facilities.count { it.isUpgrading }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Construction crews: ${crews - building} of $crews free" + if (crews < 2) " • second crew at HQ Lv 5" else "",
            style = MaterialTheme.typography.labelSmall,
            color = extra.textMuted
        )
        if (zoomable) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Zoom", style = MaterialTheme.typography.labelSmall, color = extra.textMuted)
                listOf(1f, 1.5f, 2f).forEach { z ->
                    FilterChip(
                        selected = zoom == z,
                        onClick = { zoom = z },
                        label = { Text(if (z == 1f) "1x" else "${z}x") },
                        modifier = Modifier.testTag("base_map_zoom_${(z * 10).toInt()}")
                    )
                }
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
          val viewW = maxWidth
          Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
          BoxWithConstraints(
            modifier = Modifier
                .width(viewW * zoom)
                .aspectRatio(MAP_W / MAP_H)
                .testTag("base_map")
          ) {
            val unitPx = constraints.maxWidth / MAP_W
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawBase(facilities, hqLevel, nowMillis, textMeasurer, fallenCount, monuments)
            }
            // Session 37: the Memorial Wall and the Heritage Walk are tappable too
            val density = androidx.compose.ui.platform.LocalDensity.current
            BASE_PLOTS.filter { it.type == null }.forEach { plot ->
                val enabled = when (plot.label) {
                    PARADE_LABEL -> onOpenParadeGround != null || (fallenCount > 0 && onOpenMemorial != null)
                    else -> true
                }
                if (enabled) with(density) {
                    Box(
                        modifier = Modifier
                            .offset(x = (plot.x * unitPx).toDp(), y = (plot.y * unitPx).toDp())
                            .size((plot.w * unitPx).toDp(), (plot.h * unitPx).toDp())
                            .clickable {
                                if (plot.label == PARADE_LABEL) (onOpenParadeGround ?: onOpenMemorial)?.invoke() else showHeritage = true
                            }
                            .semantics { contentDescription = if (plot.label == PARADE_LABEL) "Parade Ground and Memorial Wall, $fallenCount names" else "Heritage Walk, ${monuments.size} monuments" }
                            .testTag(if (plot.label == PARADE_LABEL) "base_memorial_wall" else "base_heritage_walk")
                    )
                }
            }
            // Tap targets over each plot (also give each building an accessible label)
            BASE_PLOTS.filter { it.type != null }.forEach { plot ->
                val type = plot.type!!
                val level = TacticalCalculator.facilityLevel(facilities, type)
                with(density) {
                    Box(
                        modifier = Modifier
                            .offset(x = (plot.x * unitPx).toDp(), y = (plot.y * unitPx).toDp())
                            .size((plot.w * unitPx).toDp(), (plot.h * unitPx).toDp())
                            .clickable { selected = type }
                            .semantics { contentDescription = "${type.title} level $level" }
                            .testTag("base_plot_${type.id}")
                    )
                }
            }
          }
          }
        }
    }

    if (showHeritage) {
        HeritageWalkDialog(monuments = monuments, operators = operators, onDismiss = { showHeritage = false })
    }

    selected?.let { type ->
        BuildingPanel(
            type = type,
            facilities = facilities,
            resources = resources,
            nowMillis = nowMillis,
            onUpgrade = { onUpgrade(type) },
            onRush = { onRush(type) },
            onDismiss = { selected = null }
        )
    }
}

/** Session 37 (item 171): the record behind each campaign monument. */
@Composable
private fun HeritageWalkDialog(monuments: List<TheaterCampaign>, operators: List<Operator>, onDismiss: () -> Unit) {
    val extra = LocalAegisColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Heritage Walk", style = MaterialTheme.typography.titleLarge, color = extra.brassBright) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).testTag("heritage_dialog"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (monuments.isEmpty()) {
                    Text(
                        "No monuments yet. Each campaign the command finishes raises one here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.textMuted
                    )
                }
                monuments.forEach { c ->
                    val patchWearers = operators.count { op -> op.combatPatchHistory.any { it.campaignId == c.id } }
                    val stages = c.stageMissionIds.size
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, extra.borderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(c.title, style = MaterialTheme.typography.titleSmall, color = extra.brassBright)
                            Text(
                                "${c.theater.label} • ${c.difficultyLabel} • $stages stages",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.textMuted
                            )
                            Text(c.summary, style = MaterialTheme.typography.bodySmall)
                            Text("Theater perk: ${c.theaterBuffLabel}", style = MaterialTheme.typography.labelSmall, color = extra.signalsCyan)
                            Text(
                                "$patchWearers soldier${if (patchWearers == 1) "" else "s"} wear its combat patch",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.textMuted
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Close") }
        }
    )
}

@Composable
private fun BuildingPanel(
    type: FacilityType,
    facilities: List<FacilityState>,
    resources: ResourceBundle,
    nowMillis: Long,
    onUpgrade: () -> Unit,
    onRush: () -> Unit,
    onDismiss: () -> Unit
) {
    val extra = LocalAegisColors.current
    val state = facilities.find { it.type == type } ?: FacilityState(type, type.startLevel)
    val level = state.level
    val blocker = TacticalCalculator.facilityUpgradeBlocker(type, facilities)
    val creditCost = type.upgradeCreditCost(level)
    val supplyCost = type.upgradeSupplyCost(level)
    val canAfford = resources.credits >= creditCost && resources.supplies >= supplyCost
    val rushCost = TacticalCalculator.rushPrestigeCost(state, nowMillis)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = if (level > 0) "${type.title} • Lv $level" else type.title,
                style = MaterialTheme.typography.titleLarge,
                color = extra.brassBright
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("building_panel_${type.id}")) {
                Text(type.subtitle, style = MaterialTheme.typography.bodySmall, color = extra.textMuted)
                Text(type.perkDescription, style = MaterialTheme.typography.bodySmall)
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, extra.borderSubtle)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Now: ${facilityPerkSummary(type, level)}", style = MaterialTheme.typography.labelMedium)
                        if (level < MAX_FACILITY_LEVEL) {
                            Text(
                                "Lv ${level + 1}: ${facilityPerkSummary(type, level + 1)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = extra.signalsCyan
                            )
                        }
                    }
                }
                if (state.isUpgrading) {
                    val left = ((state.upgradeEndsAtMillis - nowMillis).coerceAtLeast(0L) / 1000L)
                    val total = ((state.upgradeEndsAtMillis - state.upgradeStartedAtMillis).coerceAtLeast(1L)).toFloat()
                    LinearProgressIndicator(
                        progress = { ((nowMillis - state.upgradeStartedAtMillis) / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Building Lv ${level + 1} • ${TacticalCalculator.formatBuildTime(left)} left. The building keeps working at Lv $level until it finishes.",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.amberTelemetry
                    )
                } else if (level < MAX_FACILITY_LEVEL) {
                    Text(
                        "Cost: ${creditCost.toInt()} CR • ${supplyCost.toInt()} SUP • ${TacticalCalculator.formatBuildTime(type.buildSeconds(level))} build",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (canAfford) extra.brassBright else MaterialTheme.colorScheme.error
                    )
                }
                if (blocker != null && !state.isUpgrading) {
                    Text(blocker, style = MaterialTheme.typography.labelSmall, color = extra.amberTelemetry)
                }
            }
        },
        confirmButton = {
            if (state.isUpgrading) {
                Button(
                    onClick = onRush,
                    enabled = resources.prestige >= rushCost,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("rush_facility_${type.id}")
                ) { Text("Rush ($rushCost PRS)") }
            } else if (level < MAX_FACILITY_LEVEL) {
                Button(
                    onClick = { onUpgrade(); onDismiss() },
                    enabled = blocker == null && canAfford,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("upgrade_facility_${type.id}")
                ) { Text(if (level == 0) "Build" else "Upgrade") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Close") }
        }
    )
}

// ---------- Drawing ----------

private val Ground = Color(0xFF5E6B45)
private val GroundDark = Color(0xFF4C5838)
private val Canvas1 = Color(0xFFC9B98E)
private val Canvas1Shade = Color(0xFF9E8F68)
private val Wood = Color(0xFF8A6542)
private val WoodRoof = Color(0xFF5E4430)
private val Block = Color(0xFFB9B4A6)
private val BlockRoof = Color(0xFF6F6D66)
private val Concrete = Color(0xFFD3D0C6)
private val Ink = Color(0xFF1E211A)
private val Label = Color(0xFFF2EEDF)
private val BuildAmber = Color(0xFFF2A93B)

private fun DrawScope.drawBase(
    facilities: List<FacilityState>,
    hqLevel: Int,
    now: Long,
    tm: TextMeasurer,
    fallenCount: Int = 0,
    monumentCampaigns: List<TheaterCampaign> = emptyList()
) {
    val u = size.width / MAP_W
    fun r(x: Float, y: Float, w: Float, h: Float) = Rect(Offset(x * u, y * u), Size(w * u, h * u))
    val band = baseDevelopmentBand(hqLevel)

    // Art 2a: grass with texture, then roads, trees and the perimeter by Command HQ band
    drawRect(BaseArt.Grass)
    listOf(Triple(30f, 70f, 22f), Triple(160f, 140f, 26f), Triple(60f, 260f, 20f), Triple(175f, 250f, 18f)).forEach { (x, y, rad) ->
        drawOval(BaseArt.GrassDark.copy(alpha = 0.22f), Offset((x - rad) * u, (y - rad * 0.6f) * u), Size(rad * 2 * u, rad * 1.2f * u))
    }
    with(BaseArt) {
        grassTexture(u, MAP_W, MAP_H)
        val roadRects = listOf(
            r(68f, 88f, 3f, 200f), r(129f, 88f, 3f, 344f), r(10f, 155f, 180f, 6f), r(10f, 223f, 180f, 6f), r(10f, 88f, 180f, 5f),
            r(10f, 287f, 180f, 6f), r(10f, 371f, 180f, 6f), r(129f, 430f, 61f, 5f)
        )
        roads(u, band, roadRects) { it.width > it.height }
        // Tree line outside the wall
        for (i in 0..15) tree((8f + i * 12.2f) * u, (16f + (i % 3) * 2.5f) * u, 4.2f + (i % 2), 1f * u)
        // Trees inside the wall along the margins: a few at first, planted rows once the base grows
        val margin = listOf(64f, 120f, 190f, 258f, 330f, 405f)
        val count = when (band) { 1 -> 2; 2 -> 4; else -> 6 }
        margin.take(count).forEachIndexed { i, y ->
            tree(6.5f * u, y * u, 2.3f + (i % 2) * 0.3f, u)
            tree(193.5f * u, (y + 9f) * u, 2.3f + ((i + 1) % 2) * 0.3f, u)
        }
        if (band >= 2) {
            for (y in listOf(155f, 223f, 287f, 371f)) { streetlight(72.6f, y - 0.6f, u); streetlight(133.6f, y - 0.6f, u) }
            car(40f, 156.5f, u, Color(0xFF55603F)); car(150f, 224.5f, u, Color(0xFF6B6F72)); car(96f, 288.5f, u, Color(0xFF8E4A2E))
            car(129.2f, 398f, u, Color(0xFF55603F), vertical = true)
        }
        perimeter(u, band, r(3f, 26f, 194f, 410f))
    }

    BASE_PLOTS.forEach { plot ->
        val rect = r(plot.x, plot.y, plot.w, plot.h)
        val type = plot.type
        if (type == null) {
            when (plot.label) {
                PARADE_LABEL -> drawParadeGround(rect, u, band, fallenCount)
                else -> drawHeritageWalk(rect, u, monumentCampaigns)
            }
            drawPlotLabel(tm, if (plot.label == PARADE_LABEL && fallenCount > 0) "Parade Ground • Memorial" else plot.label, rect, u, Label)
            return@forEach
        }
        val state = facilities.find { it.type == type } ?: FacilityState(type, type.startLevel)
        val unlocked = TacticalCalculator.isFacilityUnlocked(type, facilities)
        when {
            !unlocked -> {
                // Art 2a: an unbuilt lot is plain grass; the label says what it needs
                drawPlotLabel(tm, "${plot.label} • " + if (type == FacilityType.SF_COMPOUND) "form an SF squad" else "HQ ${type.unlockHqLevel}", rect, u, Color(0xFFC8C3B3))
            }
            state.level <= 0 && !state.isUpgrading -> {
                drawPlotLabel(tm, "${plot.label} • Build", rect, u, Color(0xFFF3DFA2))
            }
            else -> {
                with(BaseArt) { drawFacility(type, rect, u, buildingArtStage(state.level.coerceAtLeast(1))) }
                if (state.isUpgrading) drawConstruction(rect, u, state, now)
                drawPlotLabel(tm, "${plot.label} ${state.level}", rect, u, Label)
            }
        }
    }
}

private fun DrawScope.drawCentered(tm: TextMeasurer, text: String, center: Offset, sizeSp: Float, color: Color) {
    val m = tm.measure(text, TextStyle(color = color, fontSize = sizeSp.sp, fontWeight = FontWeight.Medium))
    drawText(m, topLeft = Offset(center.x - m.size.width / 2f, center.y - m.size.height / 2f))
}

private fun DrawScope.drawPlotLabel(tm: TextMeasurer, text: String, rect: Rect, u: Float, color: Color) {
    val m = tm.measure(text, TextStyle(color = color, fontSize = (4.6f * u / density).coerceIn(7f, 11f).sp, fontWeight = FontWeight.Medium))
    val y = rect.bottom - m.size.height - 0.5f * u
    drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(rect.center.x - m.size.width / 2f - 2 * u, y), Size(m.size.width + 4 * u, m.size.height.toFloat()), CornerRadius(2 * u))
    drawText(m, topLeft = Offset(rect.center.x - m.size.width / 2f, y))
}

private fun DrawScope.drawConstruction(rect: Rect, u: Float, state: FacilityState, now: Long) {
    // Scaffolding over the building
    val l = rect.left + 8 * u; val r = rect.right - 8 * u; val t = rect.top + 14 * u; val b = rect.bottom - 12 * u
    val sc = BuildAmber.copy(alpha = 0.8f)
    var x = l
    while (x <= r) { drawLine(sc, Offset(x, t), Offset(x, b), strokeWidth = 0.5f * u); x += 7 * u }
    var y = t
    while (y <= b) { drawLine(sc, Offset(l, y), Offset(r, y), strokeWidth = 0.5f * u); y += 7 * u }
    // Progress ring
    val total = (state.upgradeEndsAtMillis - state.upgradeStartedAtMillis).coerceAtLeast(1L).toFloat()
    val frac = ((now - state.upgradeStartedAtMillis) / total).coerceIn(0f, 1f)
    val c = Offset(rect.right - 8 * u, rect.top + 8 * u)
    drawCircle(Color.Black.copy(alpha = 0.45f), 6.5f * u, c)
    drawArc(BuildAmber, -90f, 360f * frac, false, Offset(c.x - 5 * u, c.y - 5 * u), Size(10 * u, 10 * u), style = Stroke(width = 2f * u, cap = StrokeCap.Round))
}

// ---------- Session 37: Parade Ground with the Memorial Wall (item 172) and the Heritage Walk (item 171) ----------

/** Monument colors, one per campaign in campaign order. */
private val MonumentStone = listOf(
    Color(0xFFCFC6B0), Color(0xFFB8BEC4), Color(0xFFD7C08A), Color(0xFFAFA79A), Color(0xFFC9B79C),
    Color(0xFFBFC7B5), Color(0xFFD2B48C), Color(0xFFA9B2BA), Color(0xFFC4BBA8), Color(0xFFB7A98F), Color(0xFF8F9A88)
)

private fun DrawScope.drawParadeGround(rect: Rect, u: Float, band: Int, fallenCount: Int) {
    // Drill field: cut grass with mowing stripes and a gravel border
    val field = Rect(Offset(rect.left + 3 * u, rect.top + 18 * u), Size(rect.width - 6 * u, rect.height - 30 * u))
    drawRect(if (band >= 2) Color(0xFF6E7F4E) else Color(0xFF677648), field.topLeft, field.size)
    var sx = field.left
    var stripe = 0
    while (sx < field.right) {
        if (stripe % 2 == 0) drawRect(Color.White.copy(alpha = 0.05f), Offset(sx, field.top), Size(6f * u, field.height))
        sx += 6f * u; stripe++
    }
    drawRect(Color(0xFFBDB49B), field.topLeft, field.size, style = Stroke(0.8f * u))
    // Reviewing stand and three flagpoles at the far end
    drawRect(Wood, Offset(field.left + 2 * u, field.bottom - 9 * u), Size(16 * u, 7 * u))
    drawRect(WoodRoof, Offset(field.left + 1 * u, field.bottom - 11 * u), Size(18 * u, 2.4f * u))
    listOf(Color(0xFF2F5FA6), Color(0xFFB22234), Color(0xFFE0B84A)).forEachIndexed { i, flag ->
        val px = field.left + 24 * u + i * 5 * u
        drawLine(Color(0xFFDADADA), Offset(px, field.bottom - 2 * u), Offset(px, field.bottom - 20 * u), strokeWidth = 0.6f * u)
        drawRect(flag, Offset(px, field.bottom - 20 * u), Size(4 * u, 2.6f * u))
    }
    // A formation drilling on the field
    for (row in 0..2) for (col in 0..5) {
        drawCircle(Color(0xFF2E3324), 0.9f * u, Offset(field.left + 52 * u + col * 5.5f * u, field.top + 12 * u + row * 6 * u))
    }
    // Memorial Wall: built once the command has lost a soldier; one name mark per soldier (up to 48)
    if (fallenCount > 0) {
        val wall = Rect(Offset(rect.left + 8 * u, rect.top + 4 * u), Size(rect.width - 16 * u, 10 * u))
        drawRoundRect(Color(0xFF26292E), wall.topLeft, wall.size, CornerRadius(1.2f * u))
        drawRoundRect(Color(0xFF51565E), wall.topLeft, wall.size, CornerRadius(1.2f * u), style = Stroke(0.6f * u))
        val marks = fallenCount.coerceAtMost(48)
        val perRow = 24
        for (i in 0 until marks) {
            val rowY = wall.top + (2.6f + (i / perRow) * 3.6f) * u
            // Each name: a short engraved block of gold lettering
            val mx = wall.left + 4 * u + (i % perRow) * ((wall.width - 8 * u) / perRow)
            drawRect(Color(0xFFD8C27A), Offset(mx, rowY - 0.5f * u), Size(1.8f * u, 1f * u))
        }
        // Wreath at the center
        drawCircle(Color(0xFF2D6A3E), 3.2f * u, Offset(wall.center.x, wall.bottom + 2.6f * u), style = Stroke(1.2f * u))
        drawCircle(Color(0xFFB22234), 0.9f * u, Offset(wall.center.x, wall.bottom + 5.2f * u))
    }
}

private fun DrawScope.drawHeritageWalk(rect: Rect, u: Float, campaigns: List<TheaterCampaign>) {
    // A stone path with a monument for each finished campaign, two on each side of it
    drawRect(Color(0xFFB9AE95), Offset(rect.center.x - 2 * u, rect.top + 4 * u), Size(4 * u, rect.height - 16 * u))
    monumentSlots(rect, u).forEachIndexed { i, c ->
        val campaign = campaigns.getOrNull(i)
        if (campaign == null) {
            // Future spot: a faint survey marker
            drawCircle(Color(0xFFC8C3B3).copy(alpha = 0.35f), 1.2f * u, c)
            return@forEachIndexed
        }
        val stone = MonumentStone[(campaign.order - 1).coerceIn(0, MonumentStone.lastIndex)]
        // Plinth
        drawRect(Color(0xFF8D877A), Offset(c.x - 4.4f * u, c.y + 1.6f * u), Size(8.8f * u, 2.4f * u))
        if (campaign.isSpecialForces) {
            // SF campaign: a dagger-topped column in SF black and red
            drawRect(Color(0xFF1C1C1C), Offset(c.x - 1.3f * u, c.y - 8 * u), Size(2.6f * u, 9.6f * u))
            drawLine(Color(0xFFC62828), Offset(c.x, c.y - 11 * u), Offset(c.x, c.y - 8 * u), strokeWidth = 1f * u)
        } else if (campaign.order % 2 == 0) {
            // Obelisk
            drawPath(Path().apply {
                moveTo(c.x - 1.8f * u, c.y + 1.6f * u); lineTo(c.x - 1.1f * u, c.y - 8 * u)
                lineTo(c.x, c.y - 10 * u); lineTo(c.x + 1.1f * u, c.y - 8 * u); lineTo(c.x + 1.8f * u, c.y + 1.6f * u); close()
            }, stone)
        } else {
            // Statue on a block: a soldier figure
            drawRect(stone, Offset(c.x - 2.6f * u, c.y - 2.4f * u), Size(5.2f * u, 4 * u))
            drawCircle(stone, 1.3f * u, Offset(c.x, c.y - 8.2f * u))
            drawRect(stone, Offset(c.x - 1.4f * u, c.y - 6.8f * u), Size(2.8f * u, 4.4f * u))
        }
    }
}

/** Monument spots in the Heritage Walk plot, in campaign order (four columns around the path, three rows). */
internal fun monumentSlots(rect: Rect, u: Float): List<Offset> = (0 until 12).map { i ->
    // Two columns each side of the center path, three rows
    val col = i % 4
    val row = i / 4
    val x = listOf(8f, 19f, 39f, 50f)[col]
    Offset(rect.left + x * u, rect.top + (20f + row * 18f) * u)
}

/** The campaigns with a monument, in campaign order (theater campaigns first, the SF campaign last). */
fun monumentCampaigns(completedCampaignIds: Set<String>): List<TheaterCampaign> =
    com.example.data.catalog.GameCatalog.campaigns
        .filter { it.id in completedCampaignIds }
        .sortedWith(compareBy<TheaterCampaign> { it.isSpecialForces }.thenBy { it.order })
