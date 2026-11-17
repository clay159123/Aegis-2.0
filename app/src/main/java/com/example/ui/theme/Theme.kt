package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TacticalDarkColorScheme = darkColorScheme(
    primary = OlivePrimary,
    onPrimary = ObsidianBg,
    primaryContainer = SageContainer,
    onPrimaryContainer = SageOnContainer,
    secondary = BrassGold,
    onSecondary = ObsidianBg,
    secondaryContainer = BrassContainer,
    onSecondaryContainer = BrassGoldBright,
    tertiary = CyanSignals,
    onTertiary = ObsidianBg,
    tertiaryContainer = CyanContainer,
    onTertiaryContainer = TextPrimary,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = SlateSurfaceElevated,
    surfaceContainerHigh = SlateSurfaceHigh,
    outline = BorderSubtle,
    outlineVariant = BorderSubtle.copy(alpha = 0.55f),
    error = CrimsonAlert,
    onError = ObsidianBg
)

@Immutable
data class AegisExtraColors(
    val brass: Color = BrassGold,
    val brassBright: Color = BrassGoldBright,
    val brassContainer: Color = BrassContainer,
    val amberTelemetry: Color = AmberTelemetry,
    val signalsCyan: Color = CyanSignals,
    val signalsContainer: Color = CyanContainer,
    val oliveDeep: Color = OliveDeep,
    val textMuted: Color = TextMuted,
    val borderSubtle: Color = BorderSubtle
)

val LocalAegisColors = staticCompositionLocalOf { AegisExtraColors() }

val TacticalShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun AegisOpsTheme(
    content: @Composable () -> Unit
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalAegisColors provides AegisExtraColors()
    ) {
        MaterialTheme(
            colorScheme = TacticalDarkColorScheme,
            typography = AegisTypography,
            shapes = TacticalShapes,
            content = content
        )
    }
}
