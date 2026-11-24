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

    // Ground with a few darker patches
    drawRect(Ground)
    listOf(Triple(30f, 70f, 22f), Triple(160f, 140f, 26f), Triple(60f, 260f, 20f), Triple(175f, 250f, 18f)).forEach { (x, y, rad) ->
        drawOval(GroundDark, Offset((x - rad) * u, (y - rad * 0.6f) * u), Size(rad * 2 * u, rad * 1.2f * u))
    }

    // Roads: dirt, gravel, paved
    val roadCol = when (band) { 1 -> Color(0xFF8C7A55); 2 -> Color(0xFF9A988F); else -> Color(0xFF55585A) }
    listOf(
        r(68f, 88f, 3f, 200f), r(129f, 88f, 3f, 344f), r(10f, 155f, 180f, 6f), r(10f, 223f, 180f, 6f), r(10f, 88f, 180f, 5f),
        r(10f, 287f, 180f, 6f), r(10f, 371f, 180f, 6f), r(129f, 430f, 61f, 5f)
    ).forEach {
        drawRect(roadCol, it.topLeft, it.size)
    }
    if (band == 3) {
        drawLine(Color(0xFFE8E2C8), Offset(10f * u, 158f * u), Offset(190f * u, 158f * u), strokeWidth = 0.6f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * u, 3f * u)))
    }

    // Session 37: more detail: a tree line outside the wall, traffic on the roads, street lamps on a paved post
    for (i in 0..15) {
        val tx = (8f + i * 12.2f) * u
        val ty = (12f + (i % 3) * 2.5f) * u
        drawCircle(Color(0xFF3F4C2E), (4.2f + (i % 2)) * u, Offset(tx, ty))
        drawCircle(Color(0xFF4E5E38), (2.6f + (i % 2) * 0.6f) * u, Offset(tx - 1.2f * u, ty - 1.2f * u))
    }
    if (band >= 2) {
        listOf(Triple(40f, 156f, Color(0xFF55603F)), Triple(150f, 224f, Color(0xFF6B6F72)), Triple(96f, 288f, Color(0xFF55603F)), Triple(130f, 400f, Color(0xFF6B6F72)))
            .forEachIndexed { idx, (vx, vy, col) ->
                val vertical = idx == 3
                val sz = if (vertical) Size(3.4f * u, 7f * u) else Size(7f * u, 3.4f * u)
                drawRoundRect(col, Offset(vx * u, vy * u), sz, CornerRadius(0.8f * u))
                drawRect(Color(0xFF2A2E25).copy(alpha = 0.6f), Offset(vx * u + (if (vertical) 0.5f else 5f) * u, vy * u + (if (vertical) 0.5f else 0.5f) * u),
                    if (vertical) Size(2.4f * u, 1.6f * u) else Size(1.6f * u, 2.4f * u))
            }
    }

    // Item 159: a home installation's perimeter: wire fence, then chain-link on posts, then a masonry wall
    val wall = r(3f, 26f, 194f, 410f)
    fun fencePosts(step: Float, radius: Float, col: Color) {
        var x = wall.left
        while (x <= wall.right) { drawCircle(col, radius, Offset(x, wall.top)); drawCircle(col, radius, Offset(x, wall.bottom)); x += step * u }
        var y = wall.top
        while (y <= wall.bottom) { drawCircle(col, radius, Offset(wall.left, y)); drawCircle(col, radius, Offset(wall.right, y)); y += step * u }
    }
    when (band) {
        1 -> {
            drawRect(Color(0xFF9DA39A), wall.topLeft, wall.size, style = Stroke(width = 0.7f * u))
            fencePosts(12f, 0.9f * u, Color(0xFF7A6A4C))
        }
        2 -> {
            drawRect(Color(0xFFB8BDC2), wall.topLeft, wall.size, style = Stroke(width = 1.4f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.8f * u, 0.6f * u))))
            fencePosts(10f, 1.1f * u, Color(0xFF8A8F94))
        }
        else -> {
            drawRect(Color(0xFFB9A88A), wall.topLeft, wall.size, style = Stroke(width = 3.2f * u))
            drawRect(Ink.copy(alpha = 0.25f), wall.topLeft, wall.size, style = Stroke(width = 0.5f * u))
            fencePosts(16f, 1.8f * u, Color(0xFFCFC3A6))
        }
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
                drawRect(Color(0xFFC8C3B3), Offset(rect.left + 3 * u, rect.top + 3 * u), Size(rect.width - 6 * u, rect.height - 14 * u),
                    style = Stroke(width = 0.8f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * u, 2f * u))))
                drawCentered(tm, if (type == FacilityType.SF_COMPOUND) "Form an SF squad" else "HQ ${type.unlockHqLevel}",
                    Offset(rect.center.x, rect.center.y - 5 * u), 9f, Color(0xFFE6E0CC))
                drawPlotLabel(tm, plot.label, rect, u, Color(0xFFC8C3B3))
            }
            state.level <= 0 && !state.isUpgrading -> {
                // Empty lot ready to build: survey stakes and string line
                val l = rect.left + 8 * u; val t = rect.top + 10 * u; val rr = rect.right - 8 * u; val b = rect.bottom - 16 * u
                drawRect(Color(0xFFE0B655), Offset(l, t), Size(rr - l, b - t), style = Stroke(width = 0.6f * u))
                listOf(Offset(l, t), Offset(rr, t), Offset(l, b), Offset(rr, b)).forEach { drawCircle(Color(0xFFE0B655), 1.2f * u, it) }
                drawCentered(tm, "Build", Offset(rect.center.x, rect.center.y - 3 * u), 9f, Color(0xFFF3DFA2))
                drawPlotLabel(tm, plot.label, rect, u, Label)
            }
            else -> {
                if (type == FacilityType.AIRFIELD) drawAirfield(rect, u, buildingArtStage(state.level.coerceAtLeast(1)))
                // Session 38: the Main Gate's own art (guard shack, MP station, hardened gate)
                else if (type == FacilityType.MAIN_GATE) drawGate(rect, u, buildingArtStage(state.level.coerceAtLeast(1)))
                else drawBuilding(type, rect, u, buildingArtStage(state.level.coerceAtLeast(1)))
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

private fun DrawScope.drawBuilding(type: FacilityType, rect: Rect, u: Float, stage: Int) {
    val cx = rect.center.x
    val base = rect.bottom - 12 * u
    val w = rect.width
    when (stage) {
        1 -> {
            // GP tent: canvas with a ridge and a door flap
            val half = w * 0.32f
            val tent = Path().apply { moveTo(cx - half, base); lineTo(cx - half * 0.7f, base - 20 * u); lineTo(cx + half * 0.7f, base - 20 * u); lineTo(cx + half, base); close() }
            drawPath(tent, Canvas1)
            drawLine(Canvas1Shade, Offset(cx - half * 0.7f, base - 20 * u), Offset(cx + half * 0.7f, base - 20 * u), strokeWidth = 1.2f * u)
            drawPath(Path().apply { moveTo(cx - 4 * u, base); lineTo(cx, base - 10 * u); lineTo(cx + 4 * u, base); close() }, Canvas1Shade)
            drawPath(tent, Ink.copy(alpha = 0.5f), style = Stroke(width = 0.5f * u))
        }
        2 -> {
            // Wood frame hut: plank walls under a pitched roof
            val half = w * 0.33f
            drawRect(Wood, Offset(cx - half, base - 16 * u), Size(half * 2, 16 * u))
            for (i in 1..3) drawLine(WoodRoof, Offset(cx - half, base - 16 * u + i * 4 * u), Offset(cx + half, base - 16 * u + i * 4 * u), strokeWidth = 0.4f * u)
            drawPath(Path().apply { moveTo(cx - half - 2 * u, base - 16 * u); lineTo(cx, base - 27 * u); lineTo(cx + half + 2 * u, base - 16 * u); close() }, WoodRoof)
            drawRect(Ink.copy(alpha = 0.7f), Offset(cx - 3 * u, base - 9 * u), Size(6 * u, 9 * u))
        }
        else -> {
            // Block building (stage 3), inside its own walled yard at Lv 10 (stage 4)
            val half = w * if (stage == 4) 0.30f else 0.34f
            if (stage == 4) {
                drawRect(Concrete, Offset(rect.left + 3 * u, rect.top + 4 * u), Size(rect.width - 6 * u, rect.height - 16 * u), style = Stroke(width = 1.4f * u))
            }
            drawRect(Block, Offset(cx - half, base - 22 * u), Size(half * 2, 22 * u))
            drawRect(BlockRoof, Offset(cx - half - 1 * u, base - 24 * u), Size(half * 2 + 2 * u, 3 * u))
            for (i in -1..1) drawRect(Color(0xFF3F4A55), Offset(cx + i * half * 0.6f - 2.5f * u, base - 17 * u), Size(5 * u, 4 * u))
            drawRect(Ink.copy(alpha = 0.75f), Offset(cx - 3 * u, base - 9 * u), Size(6 * u, 9 * u))
        }
    }
    drawBuildingSignature(type, rect, u, stage)
}

/** A small detail that tells the buildings apart at a glance. */
private fun DrawScope.drawBuildingSignature(type: FacilityType, rect: Rect, u: Float, stage: Int) {
    val cx = rect.center.x
    val base = rect.bottom - 12 * u
    val right = rect.right - 6 * u
    when (type) {
        FacilityType.COMMAND_HQ -> {
            drawLine(Color(0xFFDADADA), Offset(right, base), Offset(right, base - 30 * u), strokeWidth = 0.8f * u)
            drawRect(Color(0xFF2F5FA6), Offset(right, base - 30 * u), Size(8 * u, 5 * u))
            drawRect(Color(0xFFE8C860), Offset(right + 1 * u, base - 29 * u), Size(2 * u, 2 * u))
        }
        FacilityType.BARRACKS -> if (stage == 1) {
            // A second, smaller tent beside the first
            val sx = rect.left + 9 * u
            drawPath(Path().apply { moveTo(sx - 6 * u, base); lineTo(sx, base - 11 * u); lineTo(sx + 6 * u, base); close() }, Canvas1)
        } else {
            for (i in 0..2) drawRect(Color(0xFFE4D79F), Offset(cx - 13 * u + i * 9 * u, base - 13 * u), Size(4 * u, 2 * u))
        }
        FacilityType.LOGISTICS_DEPOT -> {
            for ((dx, dy) in listOf(0f to 0f, 6f to 0f, 3f to -5f)) {
                drawRect(Color(0xFF7A6A3C), Offset(right - 10 * u + dx * u, base - 5 * u + dy * u), Size(5 * u, 5 * u))
                drawRect(Ink.copy(alpha = 0.5f), Offset(right - 10 * u + dx * u, base - 5 * u + dy * u), Size(5 * u, 5 * u), style = Stroke(0.4f * u))
            }
        }
        FacilityType.ARMORY_WORKSHOP -> {
            // Sandbag berm and a red ordnance stripe
            for (i in 0..3) drawOval(Color(0xFFB8A57A), Offset(rect.left + 4 * u + i * 4.5f * u, base - 3 * u), Size(5 * u, 3 * u))
            drawRect(Color(0xFFA33A2F), Offset(cx - 8 * u, base - (if (stage == 1) 14 else 20) * u), Size(16 * u, 1.6f * u))
        }
        FacilityType.RECON_ARRAY -> {
            drawLine(Color(0xFFCCCCCC), Offset(right, base), Offset(right, base - 32 * u), strokeWidth = 0.8f * u)
            drawLine(Color(0xFFCCCCCC), Offset(right - 3 * u, base - 24 * u), Offset(right + 3 * u, base - 24 * u), strokeWidth = 0.6f * u)
            drawArc(Color(0xFFE2E2E2), 200f, 140f, false, Offset(rect.left + 5 * u, base - 30 * u), Size(10 * u, 10 * u), style = Stroke(1.2f * u))
        }
        FacilityType.DFAC -> {
            drawRect(Color(0xFF6B6B6B), Offset(cx + 10 * u, base - (if (stage == 1) 22 else 30) * u), Size(3 * u, 8 * u))
            drawCircle(Color.White.copy(alpha = 0.5f), 2.5f * u, Offset(cx + 12 * u, base - (if (stage == 1) 26 else 34) * u))
        }
        FacilityType.TRAINING_RANGE -> {
            // Target stands down the lane
            for (i in 0..2) {
                val tx = rect.left + 8 * u + i * 8 * u
                drawLine(Color(0xFF5A4630), Offset(tx, rect.top + 15 * u), Offset(tx, rect.top + 8 * u), strokeWidth = 0.6f * u)
                drawCircle(Color(0xFFEDE3C6), 2.6f * u, Offset(tx, rect.top + 7 * u))
                drawCircle(Color(0xFFA33A2F), 1f * u, Offset(tx, rect.top + 7 * u))
            }
        }
        FacilityType.AID_STATION -> {
            val cy = base - (if (stage == 1) 12 else 19) * u
            drawRect(Color.White, Offset(cx - 4 * u, cy - 4 * u), Size(8 * u, 8 * u))
            drawRect(Color(0xFFC0322B), Offset(cx - 1 * u, cy - 3 * u), Size(2 * u, 6 * u))
            drawRect(Color(0xFFC0322B), Offset(cx - 3 * u, cy - 1 * u), Size(6 * u, 2 * u))
        }
        // Item 159: 34-foot jump towers from the start, then a rappel tower; the black and gold tab pennant
        FacilityType.AIRBORNE_RANGER_CENTER -> {
            val steel = Color(0xFF8A8F94)
            val tx = rect.right - 15 * u
            val towerH = (if (stage == 1) 24 else 30) * u
            drawLine(steel, Offset(tx, base), Offset(tx, base - towerH), strokeWidth = 1f * u)
            drawLine(steel, Offset(tx + 8 * u, base), Offset(tx + 8 * u, base - towerH), strokeWidth = 1f * u)
            drawLine(steel, Offset(tx, base - towerH * 0.5f), Offset(tx + 8 * u, base - towerH * 0.5f), strokeWidth = 0.5f * u)
            drawRect(Color(0xFF6E7378), Offset(tx - 1 * u, base - towerH - 2 * u), Size(10 * u, 3 * u))
            // Cable run off the tower
            drawLine(Color(0xFFDADADA), Offset(tx + 9 * u, base - towerH), Offset(rect.right - 2 * u, base - 4 * u), strokeWidth = 0.4f * u)
            if (stage >= 2) {
                // Black and gold pennant flying from the tower platform
                val py = base - towerH - 2 * u
                drawLine(Color(0xFFDADADA), Offset(tx + 4 * u, py), Offset(tx + 4 * u, py - 7 * u), strokeWidth = 0.6f * u)
                drawRect(Color(0xFF1A1A1A), Offset(tx + 4 * u, py - 7 * u), Size(7 * u, 4 * u))
                drawRect(Color(0xFFE0B84A), Offset(tx + 4 * u, py - 5.6f * u), Size(7 * u, 1f * u))
            }
            if (stage >= 3) {
                // Rappel tower at the lot's left edge: a timber tower with two ropes down its face
                val rx = rect.left + 3 * u
                drawRect(Wood, Offset(rx, base - 30 * u), Size(6 * u, 30 * u))
                drawRect(WoodRoof, Offset(rx - 1 * u, base - 32 * u), Size(8 * u, 3 * u))
                drawLine(Color(0xFFEDE3C6), Offset(rx + 1.8f * u, base - 29 * u), Offset(rx + 1.8f * u, base), strokeWidth = 0.4f * u)
                drawLine(Color(0xFFEDE3C6), Offset(rx + 4.2f * u, base - 29 * u), Offset(rx + 4.2f * u, base), strokeWidth = 0.4f * u)
            }
        }
        // Item 118: the SF Compound's own fence, then team rooms and a dive tank as it grows (item 159: jump tower moved)
        FacilityType.SF_COMPOUND -> {
            val fence = Rect(Offset(rect.left + 2 * u, rect.top + 4 * u), Size(rect.width - 4 * u, rect.height - 16 * u))
            drawRect(if (stage >= 3) Concrete else Color(0xFF9DA39A), fence.topLeft, fence.size,
                style = Stroke(width = (if (stage >= 3) 1.6f else 0.8f) * u, pathEffect = if (stage >= 3) null else PathEffect.dashPathEffect(floatArrayOf(1.5f * u, 1.5f * u))))
            // Black and red SF pennant
            drawLine(Color(0xFFDADADA), Offset(rect.left + 8 * u, base), Offset(rect.left + 8 * u, base - 26 * u), strokeWidth = 0.8f * u)
            drawRect(Color(SF_PATCH_BLACK), Offset(rect.left + 8 * u, base - 26 * u), Size(9 * u, 5 * u))
            drawRect(Color(SF_PATCH_RED), Offset(rect.left + 8 * u, base - 23.5f * u), Size(9 * u, 1.4f * u))
            if (stage == 4) {
                // Dive tank
                drawRect(Color(0xFF2F6F8F), Offset(rect.left + 22 * u, base - 8 * u), Size(16 * u, 7 * u))
                drawRect(Color(0xFFBFD9E6), Offset(rect.left + 22 * u, base - 8 * u), Size(16 * u, 7 * u), style = Stroke(0.5f * u))
            }
        }
        else -> Unit
    }
}

private fun DrawScope.drawAirfield(rect: Rect, u: Float, stage: Int) {
    val stripTop = rect.top + 10 * u
    val stripW = rect.width - 62 * u
    when (stage) {
        1 -> drawRect(Color(0xFF9A8458), Offset(rect.left + 6 * u, stripTop), Size(stripW, 16 * u))
        else -> {
            drawRect(Color(0xFF4F5254), Offset(rect.left + 6 * u, stripTop), Size(stripW, 16 * u))
            drawLine(Color(0xFFEDEBE0), Offset(rect.left + 12 * u, stripTop + 8 * u), Offset(rect.left + stripW, stripTop + 8 * u), strokeWidth = 0.8f * u,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * u, 4f * u)))
        }
    }
    val pad = Offset(rect.right - 28 * u, stripTop + 10 * u)
    drawCircle(if (stage == 1) Color(0xFF9A8458) else Color(0xFF4F5254), (if (stage >= 3) 15f else 11f) * u, pad)
    if (stage >= 2) {
        drawCircle(Color(0xFFEDEBE0), (if (stage >= 3) 13f else 9f) * u, pad, style = Stroke(0.7f * u))
        val h = 4 * u
        drawLine(Color(0xFFEDEBE0), Offset(pad.x - h, pad.y - h), Offset(pad.x - h, pad.y + h), strokeWidth = 1f * u)
        drawLine(Color(0xFFEDEBE0), Offset(pad.x + h, pad.y - h), Offset(pad.x + h, pad.y + h), strokeWidth = 1f * u)
        drawLine(Color(0xFFEDEBE0), Offset(pad.x - h, pad.y), Offset(pad.x + h, pad.y), strokeWidth = 1f * u)
    }
    if (stage >= 3) {
        // Hangar with an arched roof
        val hx = rect.left + 8 * u
        val hy = rect.bottom - 22 * u
        drawRect(Block, Offset(hx, hy), Size(26 * u, 10 * u))
        drawArc(BlockRoof, 180f, 180f, true, Offset(hx, hy - 6 * u), Size(26 * u, 12 * u))
    }
    if (stage == 4) drawRect(Concrete, Offset(rect.left + 3 * u, rect.top + 3 * u), Size(rect.width - 6 * u, rect.height - 14 * u), style = Stroke(1.2f * u))
}

/** Session 38 (item 72): the Access Control Point by art stage: 1 guard shack, 2-3 MP station, 4 hardened gate. */
private fun DrawScope.drawGate(rect: Rect, u: Float, stage: Int) {
    val cx = rect.center.x
    val base = rect.bottom - 14 * u
    drawLine(Color(0xFFDADADA), Offset(cx, base), Offset(cx, base - 28 * u), strokeWidth = 0.8f * u)
    drawRect(Color(0xFF2F5FA6), Offset(cx, base - 28 * u), Size(10 * u, 6 * u))
    // Barrier arm
    drawLine(Color(0xFFD8D3C0), Offset(rect.left + 6 * u, base), Offset(rect.right - 18 * u, base), strokeWidth = 1.4f * u)
    for (i in 0..4) drawLine(Color(0xFFC0322B), Offset(rect.left + 8 * u + i * 6 * u, base), Offset(rect.left + 10 * u + i * 6 * u, base), strokeWidth = 1.4f * u)
    when (stage) {
        1 -> {
            // Guard shack
            drawRect(Wood, Offset(rect.right - 16 * u, base - 10 * u), Size(10 * u, 10 * u))
            drawRect(WoodRoof, Offset(rect.right - 17 * u, base - 12 * u), Size(12 * u, 2.4f * u))
        }
        2, 3 -> {
            // MP station: a block building with a blue light bar and "MP" stripe
            drawRect(Block, Offset(rect.right - 20 * u, base - 14 * u), Size(16 * u, 14 * u))
            drawRect(BlockRoof, Offset(rect.right - 21 * u, base - 16 * u), Size(18 * u, 2.6f * u))
            drawRect(Color(0xFF1F2A3A), Offset(rect.right - 18 * u, base - 11 * u), Size(12 * u, 3 * u))
            drawRect(Color(0xFF4A8BE0), Offset(rect.right - 15 * u, base - 18 * u), Size(6 * u, 1.6f * u))
            if (stage == 3) drawRect(Color(0xFF9DA39A), Offset(rect.left + 6 * u, base - 4 * u), Size(rect.width - 28 * u, 0.8f * u))
        }
        else -> {
            // Hardened gate: concrete overwatch, a canopy over the lanes and vehicle barriers
            drawRect(Concrete, Offset(rect.right - 20 * u, base - 18 * u), Size(16 * u, 18 * u))
            drawRect(Ink.copy(alpha = 0.5f), Offset(rect.right - 18 * u, base - 15 * u), Size(12 * u, 2.6f * u))
            drawRect(Color(0xFF4A8BE0), Offset(rect.right - 15 * u, base - 20 * u), Size(6 * u, 1.6f * u))
            drawRect(Color(0xFF6E7378), Offset(rect.left + 4 * u, base - 22 * u), Size(rect.width - 26 * u, 2.4f * u))
            for (i in 0..3) drawRect(Concrete, Offset(rect.left + 6 * u + i * 8 * u, base + 2.5f * u), Size(5 * u, 3 * u))
        }
    }
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
