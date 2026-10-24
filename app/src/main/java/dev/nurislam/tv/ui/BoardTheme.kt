package dev.nurislam.tv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import dev.nurislam.tv.settings.*
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

data class BoardAppearance(
    val Background: Color = BoardColors.Background,
    val Deep: Color = BoardColors.Deep,
    val Card: Color = BoardColors.Card,
    val Emerald: Color = BoardColors.Emerald,
    val Highlight: Color = BoardColors.Highlight,
    val Text: Color = BoardColors.Text,
    val Secondary: Color = BoardColors.Secondary,
    val Gold: Color = BoardColors.Gold,
    val Outline: Color = BoardColors.Outline,
    val ornate: Boolean = true,
    val fontFamily: FontFamily = FontFamily.Serif,
)
val LocalBoardAppearance = staticCompositionLocalOf { BoardAppearance() }
val LocalBoardSettings = staticCompositionLocalOf { AppSettings() }

fun appearanceFor(id: BoardThemeId) = when (id) {
    BoardThemeId.ELEGANT -> BoardAppearance()
    BoardThemeId.CLASSIC -> BoardAppearance(
        Background = Color(0xFF084F26), Deep = Color(0xFF06391F), Card = Color(0xFF082F1D),
        Emerald = Color(0xFF0B512B), Highlight = Color(0xFFFFD76A), Text = Color.White,
        Secondary = Color(0xFFDDE5D7), Gold = Color(0xFFFFD76A), Outline = Color(0xFFC6AA61),
        ornate = false, fontFamily = FontFamily.SansSerif,
    )
}

@Composable
fun NurIslamTheme(id: BoardThemeId = BoardThemeId.ELEGANT, content: @Composable () -> Unit) {
    val colors = remember(id) { appearanceFor(id) }
    CompositionLocalProvider(LocalBoardAppearance provides colors) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = colors.Gold, onPrimary = colors.Deep,
        surface = colors.Card, onSurface = colors.Text,
        background = colors.Background, onBackground = colors.Text,
    ), content = content)
}
}
