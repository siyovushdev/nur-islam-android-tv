package dev.nurislam.tv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.nurislam.tv.domain.DisplayMode
import dev.nurislam.tv.domain.resolveDisplayMode

/** Fit the complete TV board on a phone, including sideways previews. Controls stay upright. */
@Composable
fun PhoneBoardViewport(mode: DisplayMode, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val resolved = resolveDisplayMode(mode, constraints.maxWidth, constraints.maxHeight)
        val ratio = if (resolved == DisplayMode.PORTRAIT_NATIVE) 9f / 16f else 16f / 9f
        val width = minOf(maxWidth, maxHeight * ratio)
        Box(Modifier.width(width).height(width / ratio).testTag("phone-viewport")) { content() }
    }
}
