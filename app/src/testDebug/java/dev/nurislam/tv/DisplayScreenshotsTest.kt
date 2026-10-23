package dev.nurislam.tv

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.nurislam.tv.data.DemoScheduleRepository
import dev.nurislam.tv.domain.*
import dev.nurislam.tv.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DisplayScreenshotsTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(qualifiers = "w1080dp-h1920dp-mdpi")
    fun portrait1080() = render("portrait-1080", DisplayMode.PORTRAIT_NATIVE, 1080, 1920)
    @Test @Config(qualifiers = "w1920dp-h1080dp-mdpi")
    fun landscape1080() = render("landscape-1080", DisplayMode.LANDSCAPE, 1920, 1080)
    @Test @Config(qualifiers = "w1920dp-h1080dp-mdpi")
    fun rotatedCw1080() = render("rotate-cw-1080", DisplayMode.PORTRAIT_ROTATE_CW, 1920, 1080)
    @Test @Config(qualifiers = "w1920dp-h1080dp-mdpi")
    fun rotatedCcw1080() = render("rotate-ccw-1080", DisplayMode.PORTRAIT_ROTATE_CCW, 1920, 1080)
    @Test @Config(qualifiers = "w1280dp-h720dp-mdpi")
    fun landscape720() = render("landscape-720", DisplayMode.LANDSCAPE, 1280, 720)
    @Test @Config(qualifiers = "w3840dp-h2160dp-mdpi")
    fun landscape4k() = render("landscape-4k", DisplayMode.LANDSCAPE, 3840, 2160)
    @Test @Config(qualifiers = "w2160dp-h3840dp-mdpi")
    fun portrait4k() = render("portrait-4k", DisplayMode.PORTRAIT_NATIVE, 2160, 3840)
    @Test @Config(qualifiers = "w1080dp-h1920dp-mdpi")
    fun portraitFontScale130() = render("portrait-font130", DisplayMode.PORTRAIT_NATIVE, 1080, 1920, 1.3f)

    private fun render(name: String, mode: DisplayMode, width: Int, height: Int, fontScale: Float = 1f) {
        compose.runOnUiThread {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
        val now = mutableStateOf(Instant.parse("2026-10-23T11:20:00Z"))
        val day = DemoScheduleRepository().day(LocalDate.of(2026, 10, 23))!!
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                NurIslamTheme {
                    RotatedBoard(mode, Modifier.fillMaxSize()) {
                        PrayerBoardScreen(now.value, now, day, nextPrayer(now.value, listOf(day)), mode == DisplayMode.LANDSCAPE, true)
                    }
                }
            }
        }
        val image = compose.captureRenderedNode().asAndroidBitmap()
        assertEquals(width, image.width)
        assertEquals(height, image.height)
        val dir = File(requireNotNull(System.getProperty("nurIslam.screenshotDir"))).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
