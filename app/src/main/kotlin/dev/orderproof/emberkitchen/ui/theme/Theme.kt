package dev.orderproof.emberkitchen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Ember Kitchen palette: warm orange, charcoal and cream. Text pairs meet WCAG AA contrast. */
object EmberColors {
    val Ember = Color(0xFFB5421A) // 5.6:1 against white text
    val EmberBright = Color(0xFFE8622C) // decorative accents only
    val EmberSoft = Color(0xFFFFE1CC)
    val Charcoal = Color(0xFF2A2725)
    val CharcoalSoft = Color(0xFF4A4542)
    val Cream = Color(0xFFFFF6EA)
    val CreamDeep = Color(0xFFF6E7D3)
    val Danger = Color(0xFF9F1D16)
    val DangerSoft = Color(0xFFFCE3DF)
    val Success = Color(0xFF2E6B3A)
}

private val EmberScheme = lightColorScheme(
    primary = EmberColors.Ember,
    onPrimary = Color.White,
    primaryContainer = EmberColors.EmberSoft,
    onPrimaryContainer = EmberColors.Charcoal,
    secondary = EmberColors.Charcoal,
    onSecondary = EmberColors.Cream,
    background = EmberColors.Cream,
    onBackground = EmberColors.Charcoal,
    surface = EmberColors.Cream,
    onSurface = EmberColors.Charcoal,
    surfaceVariant = EmberColors.CreamDeep,
    onSurfaceVariant = EmberColors.CharcoalSoft,
    error = EmberColors.Danger,
    onError = Color.White,
    errorContainer = EmberColors.DangerSoft,
    onErrorContainer = EmberColors.Danger,
)

@Composable
fun EmberTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EmberScheme, typography = Typography(), content = content)
}
