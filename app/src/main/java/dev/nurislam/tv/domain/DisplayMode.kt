package dev.nurislam.tv.domain

enum class DisplayMode { AUTO, PORTRAIT_NATIVE, PORTRAIT_ROTATE_CW, PORTRAIT_ROTATE_CCW, LANDSCAPE }

data class DisplayGeometry(val logicalWidth: Int, val logicalHeight: Int, val rotation: Float)
data class Point(val x: Float, val y: Float)

fun resolveDisplayMode(mode: DisplayMode, width: Int, height: Int): DisplayMode {
    require(width > 0 && height > 0)
    return if (mode != DisplayMode.AUTO) mode else if (height > width) DisplayMode.PORTRAIT_NATIVE else DisplayMode.LANDSCAPE
}

fun displayGeometry(mode: DisplayMode, width: Int, height: Int): DisplayGeometry = when (resolveDisplayMode(mode, width, height)) {
    DisplayMode.PORTRAIT_ROTATE_CW -> DisplayGeometry(height, width, 90f)
    DisplayMode.PORTRAIT_ROTATE_CCW -> DisplayGeometry(height, width, -90f)
    else -> DisplayGeometry(width, height, 0f)
}

/** Centered quarter-turn used by RotatedBoard; all four corners land on the window edges. */
fun logicalToPhysical(point: Point, geometry: DisplayGeometry): Point = when (geometry.rotation) {
    90f -> Point(geometry.logicalHeight - point.y, point.x)
    -90f -> Point(point.y, geometry.logicalWidth - point.x)
    else -> point
}
