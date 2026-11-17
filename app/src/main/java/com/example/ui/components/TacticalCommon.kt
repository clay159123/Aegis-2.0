package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.ResourceBundle
import com.example.ui.theme.LocalAegisColors
import kotlin.math.roundToInt

fun formatCompact(value: Double): String {
    return when {
        value >= 1_000_000 -> String.format("%.2fM", value / 1_000_000.0)
        value >= 10_000 -> String.format("%.1fK", value / 1_000.0)
        else -> value.roundToInt().toString()
    }
}

fun formatRate(value: Double): String =
    if (value >= 10.0) "+${String.format("%.0f", value)}/s"
    else "+${String.format("%.1f", value)}/s"

fun formatCountdown(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format("%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}

@Composable
fun ResourceHudBar(
    resources: ResourceBundle,
    rates: ResourceBundle,
    modifier: Modifier = Modifier,
    onPrestigeClick: (() -> Unit)? = null
) {
    val extra = LocalAegisColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        border = BorderStroke(1.dp, extra.borderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ResourceChip(
                icon = Icons.Filled.Savings,
                label = "CR",
                value = formatCompact(resources.credits),
                rate = formatRate(rates.credits),
                accent = extra.brass,
                modifier = Modifier.weight(1f)
            )
            ResourceChip(
                icon = Icons.Filled.Inventory2,
                label = "SUP",
                value = formatCompact(resources.supplies),
                rate = formatRate(rates.supplies),
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            ResourceChip(
                icon = Icons.Filled.Radar,
                label = "INT",
                value = formatCompact(resources.intel),
                rate = formatRate(rates.intel),
                accent = extra.signalsCyan,
                modifier = Modifier.weight(1f)
            )
            ResourceChip(
                icon = Icons.Filled.MilitaryTech,
                label = "PRS",
                value = formatCompact(resources.prestige),
                rate = "HONOR",
                accent = extra.brassBright,
                onClick = onPrestigeClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("hud_prestige_chip")
            )
        }
    }
}

@Composable
private fun ResourceChip(
    icon: ImageVector,
    label: String,
    value: String,
    rate: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val extra = LocalAegisColors.current
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = extra.textMuted,
                maxLines = 1
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = rate,
            style = MaterialTheme.typography.labelSmall,
            color = accent.copy(alpha = 0.9f),
            maxLines = 1
        )
    }
}

@Composable
fun TacticalPanelCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = LocalAegisColors.current.borderSubtle,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun StatMeterRow(
    label: String,
    value: Int,
    maxScale: Int = 80,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val fraction = (value.toFloat() / maxScale.toFloat()).coerceIn(0.05f, 1f)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent)
            )
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(30.dp)
        )
    }
}
