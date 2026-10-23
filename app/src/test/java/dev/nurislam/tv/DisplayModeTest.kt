package dev.nurislam.tv

import dev.nurislam.tv.domain.*
import org.junit.Assert.*
import org.junit.Test

class DisplayModeTest {
    @Test fun autoUsesWindowOnlyAndNeverGuessesPhysicalRotation() {
        assertEquals(DisplayMode.LANDSCAPE, resolveDisplayMode(DisplayMode.AUTO, 1920, 1080))
        assertEquals(DisplayMode.PORTRAIT_NATIVE, resolveDisplayMode(DisplayMode.AUTO, 1080, 1920))
        assertEquals(DisplayMode.LANDSCAPE, resolveDisplayMode(DisplayMode.AUTO, 100, 100))
        DisplayMode.entries.drop(1).forEach { assertEquals(it, resolveDisplayMode(it, 1920, 1080)) }
    }

    @Test fun quarterTurnsCoverWindowExactlyAt720p1080pAnd4k() {
        for ((w, h) in listOf(1280 to 720, 1920 to 1080, 3840 to 2160)) {
            for (mode in listOf(DisplayMode.PORTRAIT_ROTATE_CW, DisplayMode.PORTRAIT_ROTATE_CCW)) {
                val g = displayGeometry(mode, w, h)
                assertEquals(h, g.logicalWidth)
                assertEquals(w, g.logicalHeight)
                val corners = listOf(Point(0f, 0f), Point(h.toFloat(), 0f), Point(0f, w.toFloat()), Point(h.toFloat(), w.toFloat()))
                assertEquals(setOf(Point(0f, 0f), Point(w.toFloat(), 0f), Point(0f, h.toFloat()), Point(w.toFloat(), h.toFloat())),
                    corners.map { logicalToPhysical(it, g) }.toSet())
                val expectedOrigin = if (mode == DisplayMode.PORTRAIT_ROTATE_CW) Point(w.toFloat(), 0f) else Point(0f, h.toFloat())
                assertEquals(expectedOrigin, logicalToPhysical(Point(0f, 0f), g))
            }
        }
    }

    @Test(expected = IllegalArgumentException::class) fun invalidWindowIsRejected() { displayGeometry(DisplayMode.AUTO, 0, 1080) }
}
