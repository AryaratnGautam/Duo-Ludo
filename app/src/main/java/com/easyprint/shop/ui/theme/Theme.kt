package com.easyprint.shop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import java.util.Locale

private val EasyPrintDaylightColorScheme = lightColorScheme(
    primary = InkTealPrimary,
    onPrimary = CrispPaperSurface,
    primaryContainer = InkTealContainer,
    onPrimaryContainer = InkTealDark,
    secondary = CharcoalInk,
    onSecondary = CrispPaperSurface,
    secondaryContainer = SoftSlateContainer,
    onSecondaryContainer = CharcoalInk,
    background = DaylightStoneBackground,
    onBackground = CharcoalInk,
    surface = CrispPaperSurface,
    onSurface = CharcoalInk,
    surfaceVariant = SoftSlateContainer,
    onSurfaceVariant = MutedSlateText,
    outline = SoftSlateBorder,
    error = StatusRedText,
    onError = CrispPaperSurface,
    errorContainer = StatusRedContainer,
    onErrorContainer = StatusRedText
)

private val EasyPrintShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun EasyPrintTheme(content: @Composable () -> Unit) {
    val locale = Locale.getDefault()
    val typography = remember(locale.language) { buildEasyPrintTypography(locale) }

    MaterialTheme(
        colorScheme = EasyPrintDaylightColorScheme,
        typography = typography,
        shapes = EasyPrintShapes,
        content = content
    )
}
