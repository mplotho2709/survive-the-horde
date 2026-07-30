package com.triathlonplanner.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFFC7431C), // carries white text at ~5:1
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D8),
    onPrimaryContainer = Color(0xFF5C1B08),
    secondary = Color(0xFF3E4A5A),
    onSecondary = Color.White,
    tertiary = Color(0xFF2A78D6),
    onTertiary = Color.White,
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF101418),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101418),
    surfaceVariant = Color(0xFFEFF1F4),
    onSurfaceVariant = Color(0xFF5A6472),
    outline = Color(0xFFE2E5EA),
    outlineVariant = Color(0xFFE2E5EA),
    error = Color(0xFFC0362C),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8B5E),
    onPrimary = Color(0xFF3A1305),
    primaryContainer = Color(0xFF6B2A11),
    onPrimaryContainer = Color(0xFFFFDACA),
    secondary = Color(0xFFB4C0D0),
    onSecondary = Color(0xFF1C242E),
    tertiary = Color(0xFF3987E5),
    onTertiary = Color(0xFF00203F),
    background = Color(0xFF0E1114),
    onBackground = Color(0xFFECEFF3),
    surface = Color(0xFF171B20),
    onSurface = Color(0xFFECEFF3),
    surfaceVariant = Color(0xFF212831),
    onSurfaceVariant = Color(0xFF9BA6B4),
    outline = Color(0xFF2C343E),
    outlineVariant = Color(0xFF2C343E),
    error = Color(0xFFE3675C),
    onError = Color(0xFF3A0906),
)

/**
 * Type scale tuned for a training app: numbers are the content, so headline steps are heavy and
 * tightly tracked, while labels are small and letter-spaced so they recede behind the values they
 * annotate. That inversion - loud value, quiet label - is most of what makes a stat row scannable.
 */
private val AppTypography: Typography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-1).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = (-0.3).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = (-0.2).sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.3.sp),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.8.sp),
    )
}

/** Numeric readout style - heavier and tighter than any body step so values dominate their labels. */
val MetricValueStyle: TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 25.sp,
    letterSpacing = (-0.6).sp,
)

object AppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp

    /** Standard screen gutter - every screen uses this so edges line up across tabs. */
    val gutter = 16.dp
}

object AppRadius {
    val card = 16.dp
    val cell = 10.dp
    val pill = 999.dp
}

@Composable
fun TriathlonPlannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppAccents provides accentsFor(darkTheme)) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
