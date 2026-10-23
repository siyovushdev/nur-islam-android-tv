package dev.nurislam.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import dev.nurislam.tv.domain.DisplayMode
import dev.nurislam.tv.domain.displayGeometry

/** Measure in logical coordinates FIRST; rotating a landscape-sized child would clip it. */
@Composable
fun RotatedBoard(mode: DisplayMode, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val geometry = displayGeometry(mode, width, height)
        val child = measurables.single().measure(Constraints.fixed(geometry.logicalWidth, geometry.logicalHeight))
        layout(width, height) {
            val x = (width - child.width) / 2
            val y = (height - child.height) / 2
            child.placeWithLayer(x, y) {
                translationX = (width - child.width) / 2f - x
                translationY = (height - child.height) / 2f - y
                rotationZ = geometry.rotation
            }
        }
    }
}
