package dev.nurislam.tv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

object BoardColors {
    val Background = Color(0xFF063526)
    val Deep = Color(0xFF03221B)
    val Card = Color(0xFF05291F)
    val Emerald = Color(0xFF0A4B37)
    val Highlight = Color(0xFF195A46)
    val Text = Color(0xFFF6EFD9)
    val Secondary = Color(0xFFB9C8B9)
    val Gold = Color(0xFFE6C37B)
    val Outline = Color(0xFFD1B174)
}
object BoardSpacing {
    const val SafeInset = 0.055f
    const val PanelRadius = 36f
    const val Gap = 20f
}
val BoardTextStyle = TextStyle(fontFamily = FontFamily.Serif, color = BoardColors.Text)
val BoardDigitsStyle = BoardTextStyle.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", letterSpacing = 0.sp)

@Composable
fun NurIslamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = BoardColors.Gold, onPrimary = BoardColors.Deep,
        surface = BoardColors.Card, onSurface = BoardColors.Text,
        background = BoardColors.Background, onBackground = BoardColors.Text,
    ), content = content)
}
