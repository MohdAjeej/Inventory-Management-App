package com.b2binventory.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val Light = lightColorScheme(
    primary = Color(0xFF1F5FAD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF00796B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8EDE6),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF8A5100),
    tertiaryContainer = Color(0xFFFFDCBE),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE4E8F0),
    surfaceContainerLow = Color(0xFFF2F4F8),
    outline = Color(0xFF737782),
    outlineVariant = Color(0xFFC6CAD3),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF002F65),
    primaryContainer = Color(0xFF00468E),
    onPrimaryContainer = Color(0xFFD7E3FF),
    secondary = Color(0xFF84D5C7),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005047),
    tertiary = Color(0xFFFFB870),
    tertiaryContainer = Color(0xFF693C00),
    background = Color(0xFF111318),
    surface = Color(0xFF1A1C21),
    surfaceVariant = Color(0xFF43474E),
    surfaceContainerLow = Color(0xFF191C20),
    outline = Color(0xFF8D919A),
    outlineVariant = Color(0xFF43474E),
)

/** Status colours used for stock levels, consistent in light and dark themes. */
object StatusColors {
    val ok = Color(0xFF2E7D32)
    val low = Color(0xFFE08600)
    val out = Color(0xFFC62828)
}

@Composable
fun InventoryTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
        ),
        content = content,
    )
}
