package com.b2binventory.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF1F5FAD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7F8),
    onPrimaryContainer = Color(0xFF0B2A52),
    secondary = Color(0xFF3D5A80),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE6F3),
    onSecondaryContainer = Color(0xFF14263D),
    tertiary = Color(0xFF00796B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDEDE8),
    onTertiaryContainer = Color(0xFF00201C),
    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFFDE3E1),
    onErrorContainer = Color(0xFF5F1412),
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF15191F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15191F),
    surfaceVariant = Color(0xFFEEF2F7),
    onSurfaceVariant = Color(0xFF55606E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF2F5F9),
    surfaceContainerHigh = Color(0xFFECF0F5),
    surfaceContainerHighest = Color(0xFFE5EAF1),
    outline = Color(0xFF8A94A3),
    outlineVariant = Color(0xFFDDE3EA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF002F65),
    primaryContainer = Color(0xFF1B4377),
    onPrimaryContainer = Color(0xFFD7E3FF),
    secondary = Color(0xFFB7C8E1),
    onSecondary = Color(0xFF213246),
    secondaryContainer = Color(0xFF2E4258),
    onSecondaryContainer = Color(0xFFD5E3F8),
    tertiary = Color(0xFF84D5C7),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF005047),
    onTertiaryContainer = Color(0xFFA0F2E3),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1216),
    onBackground = Color(0xFFE2E6EC),
    surface = Color(0xFF171B21),
    onSurface = Color(0xFFE2E6EC),
    surfaceVariant = Color(0xFF232A33),
    onSurfaceVariant = Color(0xFFAEB7C4),
    surfaceContainerLowest = Color(0xFF0B0E11),
    surfaceContainerLow = Color(0xFF151A1F),
    surfaceContainer = Color(0xFF1B2128),
    surfaceContainerHigh = Color(0xFF222932),
    surfaceContainerHighest = Color(0xFF2B333D),
    outline = Color(0xFF7C8796),
    outlineVariant = Color(0xFF2F3843),
)

/** Status colours (success / warning / danger) that stay readable in light and dark themes. */
@Immutable
data class AppColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
)

private val LightAppColors = AppColors(
    success = Color(0xFF2E7D32), successContainer = Color(0xFFE3F3E4), onSuccessContainer = Color(0xFF14421A),
    warning = Color(0xFFB45F06), warningContainer = Color(0xFFFFF0D9), onWarningContainer = Color(0xFF5C3100),
    danger = Color(0xFFC62828), dangerContainer = Color(0xFFFDE3E1), onDangerContainer = Color(0xFF5F1412),
    info = Color(0xFF1F5FAD), infoContainer = Color(0xFFDCE7F8), onInfoContainer = Color(0xFF0B2A52),
)

private val DarkAppColors = AppColors(
    success = Color(0xFF81C995), successContainer = Color(0xFF1D3A24), onSuccessContainer = Color(0xFFC3EFCB),
    warning = Color(0xFFF2B866), warningContainer = Color(0xFF45300F), onWarningContainer = Color(0xFFFFDDB0),
    danger = Color(0xFFFF8A80), dangerContainer = Color(0xFF4A1A18), onDangerContainer = Color(0xFFFFDAD6),
    info = Color(0xFFAAC7FF), infoContainer = Color(0xFF1B3557), onInfoContainer = Color(0xFFD7E3FF),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

/** Access with `MaterialTheme.appColors`. */
val MaterialTheme.appColors: AppColors
    @Composable @ReadOnlyComposable get() = LocalAppColors.current

/** Stock status colours, theme-aware. */
object StatusColors {
    val ok: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.success
    val low: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.warning
    val out: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.danger
}

/** Spacing scale used by shared components. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

private val base = Typography()

private val AppTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

/** Large numbers on metric cards. */
val MetricTextStyle: TextStyle
    @Composable @ReadOnlyComposable get() = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)

@Composable
fun InventoryTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppColors provides if (darkTheme) DarkAppColors else LightAppColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) Dark else Light,
            typography = AppTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(6.dp),
                small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(12.dp),
                large = RoundedCornerShape(16.dp),
                extraLarge = RoundedCornerShape(24.dp),
            ),
            content = content,
        )
    }
}
