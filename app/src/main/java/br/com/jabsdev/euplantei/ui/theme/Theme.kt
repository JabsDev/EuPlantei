package br.com.jabsdev.euplantei.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = PaleLeaf,
    onPrimaryContainer = ForestGreenDark,
    secondary = Terracotta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF3B0900),
    tertiary = Bark,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF1DFD8),
    onTertiaryContainer = Color(0xFF2B170F),
    background = Cream,
    onBackground = Ink,
    surface = WarmWhite,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE5E9DF),
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color(0xFFFFFBF5),
    surfaceContainerLow = Color(0xFFF8F3EA),
    surfaceContainer = Color(0xFFF2EDE3),
    surfaceContainerHigh = Color(0xFFECE6DB),
    surfaceContainerHighest = Color(0xFFE6E0D5),
    outline = Color(0xFF74796F),
    outlineVariant = Color(0xFFC4C9BE),
    error = Danger,
    onError = Color.White,
    errorContainer = DangerLight,
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF2F312D),
    inverseOnSurface = Color(0xFFF0F1EB),
    inversePrimary = SoftLeaf,
    scrim = Color(0x99000000),
)

private val DarkColors = darkColorScheme(
    primary = NightPrimary,
    onPrimary = Color(0xFF003910),
    primaryContainer = Color(0xFF14521B),
    onPrimaryContainer = SoftLeaf,
    secondary = NightSecondary,
    onSecondary = Color(0xFF55200F),
    secondaryContainer = Color(0xFF73351F),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFE0C0B4),
    onTertiary = Color(0xFF402A21),
    tertiaryContainer = Color(0xFF594137),
    onTertiaryContainer = Color(0xFFF1DFD8),
    background = NightForest,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = Color(0xFF3F4A3E),
    onSurfaceVariant = NightMuted,
    surfaceContainerLowest = Color(0xFF09170D),
    surfaceContainerLow = Color(0xFF122416),
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceHigh,
    surfaceContainerHighest = Color(0xFF283F2D),
    outline = Color(0xFF8B9588),
    outlineVariant = Color(0xFF3F4A3E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = DangerLight,
    inverseSurface = NightInk,
    inverseOnSurface = Color(0xFF2F312D),
    inversePrimary = ForestGreen,
    scrim = Color(0xCC000000),
)

val PlantShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun EuPlanteiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PlantTypography,
        shapes = PlantShapes,
        content = content,
    )
}
