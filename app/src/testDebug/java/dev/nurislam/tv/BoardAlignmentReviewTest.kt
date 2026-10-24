package dev.nurislam.tv

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.view.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.nurislam.tv.data.DemoScheduleFixture
import dev.nurislam.tv.domain.*
import dev.nurislam.tv.model.ScheduleEvent
import dev.nurislam.tv.settings.*
import dev.nurislam.tv.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

/** Measure painted foreground independently of the intentionally asymmetric architectural art. */
@RunWith(AndroidJUnit4::class)
class BoardAlignmentReviewTest {
    @get:Rule val compose = createComposeRule()
    @Test @Config(qualifiers = "w1080dp-h1920dp-mdpi")
    fun portrait() = measure("portrait", DisplayMode.PORTRAIT_NATIVE)
    @Test @Config(qualifiers = "w1920dp-h1080dp-mdpi")
    fun clockwise() = measure("cw", DisplayMode.PORTRAIT_ROTATE_CW)
    @Test @Config(qualifiers = "w1920dp-h1080dp-mdpi")
    fun counterclockwise() = measure("ccw", DisplayMode.PORTRAIT_ROTATE_CCW)
    @Test @Config(qualifiers = "w1920dp-h1080dp-mdpi")
    fun landscape() = measure("landscape", DisplayMode.LANDSCAPE)

    private fun measure(name: String, mode: DisplayMode) {
        compose.runOnUiThread {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
        val showBoard = mutableStateOf(true)
        val theme = mutableStateOf(BoardThemeId.ELEGANT)
        val textScale = mutableStateOf(1f)
        val now = mutableStateOf(Instant.parse("2026-10-23T11:20:00Z"))
        val day = DemoScheduleFixture().day(LocalDate.of(2026, 10, 23))!!
        compose.setContent {
            CompositionLocalProvider(LocalBoardSettings provides AppSettings(textScale = textScale.value)) {
                NurIslamTheme(theme.value) {
                    RotatedBoard(mode, Modifier.fillMaxSize()) {
                        if (showBoard.value) PrayerBoardScreen(now.value, now, day, nextPrayer(now.value, listOf(day)), mode == DisplayMode.LANDSCAPE, false)
                        else if (LocalBoardAppearance.current.ornate) ArchitecturalBackground()
                        else {
                            val colors = LocalBoardAppearance.current
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.Emerald, colors.Background, colors.Deep))))
                        }
                    }
                }
            }
        }
        val dir = File(requireNotNull(System.getProperty("nurIslam.screenshotDir"))).apply { mkdirs() }
        val measurements = StringBuilder("theme,mode,scale,ink_top,ink_bottom_margin,center_offset_px\n")
        for (id in BoardThemeId.entries) for (scale in listOf(.85f, 1f, 1.15f)) {
            compose.runOnIdle { theme.value = id; textScale.value = scale; showBoard.value = true }
            val board = compose.onNodeWithTag("prayer-board").fetchSemanticsNode().boundsInRoot
            ScheduleEvent.entries.forEach { event ->
                val row = compose.onNodeWithTag("row-${event.name}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue("$id/$mode/$scale: row clipped", row.left >= board.left && row.right <= board.right && row.top >= board.top && row.bottom <= board.bottom)
            }
            val foreground = compose.captureRenderedNode().asAndroidBitmap()
            File(dir, "alignment-${id.name.lowercase()}-$name-$scale.png").outputStream().use { foreground.compress(Bitmap.CompressFormat.PNG, 100, it) }
            compose.runOnIdle { showBoard.value = false }
            val background = compose.captureRenderedNode().asAndroidBitmap()
            val a = IntArray(foreground.width * foreground.height)
            val b = IntArray(a.size)
            foreground.getPixels(a, 0, foreground.width, 0, 0, foreground.width, foreground.height)
            background.getPixels(b, 0, background.width, 0, 0, background.width, background.height)
            var top = Int.MAX_VALUE
            var bottom = -1
            for (index in a.indices) {
                val different = abs((a[index] and 255) - (b[index] and 255)) > 12 ||
                    abs((a[index] shr 8 and 255) - (b[index] shr 8 and 255)) > 12 ||
                    abs((a[index] shr 16 and 255) - (b[index] shr 16 and 255)) > 12
                if (different) {
                    val x = index % foreground.width
                    val y = index / foreground.width
                    val logicalY = when (mode) {
                        DisplayMode.PORTRAIT_ROTATE_CW -> foreground.width - 1 - x
                        DisplayMode.PORTRAIT_ROTATE_CCW -> x
                        else -> y
                    }
                    top = minOf(top, logicalY); bottom = maxOf(bottom, logicalY)
                }
            }
            val height = if (mode in listOf(DisplayMode.PORTRAIT_ROTATE_CW, DisplayMode.PORTRAIT_ROTATE_CCW)) foreground.width else foreground.height
            val bottomMargin = height - 1 - bottom
            measurements.append("$id,$mode,$scale,$top,$bottomMargin,${(top - bottomMargin) / 2f}\n")
            run {
                assertTrue("$mode / $scale: painted foreground not centered: top=$top bottom=$bottomMargin", abs(top - bottomMargin) <= 2)
            }
        }
        File(dir, "alignment-$name.csv").writeText(measurements.toString())
    }
}
