package dev.nurislam.tv

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import dev.nurislam.tv.data.DemoScheduleRepository
import dev.nurislam.tv.domain.*
import dev.nurislam.tv.model.ScheduleEvent
import dev.nurislam.tv.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BoardUiTest {
    @get:Rule val compose = createComposeRule()
    private val instant = Instant.parse("2026-10-23T11:20:00Z")
    private val day = DemoScheduleRepository().day(LocalDate.of(2026, 10, 23))!!

    private fun board(landscape: Boolean, unavailable: Boolean = false, state: State<Instant> = mutableStateOf(instant)) {
        compose.setContent {
            NurIslamTheme {
                Box(Modifier.requiredSize(if (landscape) 960.dp else 540.dp, if (landscape) 540.dp else 960.dp)) {
                    PrayerBoardScreen(instant, state, if (unavailable) null else day,
                        if (unavailable) null else nextPrayer(instant, listOf(day)), landscape, !unavailable)
                }
            }
        }
    }
    private fun saveScreenshot(name: String) {
        val bitmap = compose.captureRenderedNode("prayer-board").asAndroidBitmap()
        val dir = System.getProperty("nurIslam.screenshotDir")?.let(::File)
            ?: File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun portraitShowsAllEightRowsAndSeparateCountdown() {
        board(false)
        ScheduleEvent.entries.forEach { compose.onNodeWithTag("row-${it.name}").assertIsDisplayed() }
        compose.onNodeWithTag("next-time").assert(hasAnyDescendant(hasText("15:15")))
        compose.onNodeWithTag("countdown").assert(hasAnyDescendant(hasText("00:55:00")))
        compose.onNodeWithTag("demo-label").assertIsDisplayed()
        saveScreenshot("portrait")
    }
    @Test fun landscapeShowsAllEightRows() {
        board(true)
        ScheduleEvent.entries.forEach { compose.onNodeWithTag("row-${it.name}").assertIsDisplayed() }
        saveScreenshot("landscape")
    }
    @Test fun unavailableKeepsClockAndNeverShowsSampleTimes() {
        board(true, unavailable = true)
        compose.onNodeWithText("Расписание недоступно").assertIsDisplayed()
        compose.onAllNodesWithText("15:15").assertCountEquals(0)
        compose.onNodeWithText("14:20").assertIsDisplayed()
        saveScreenshot("unavailable")
    }
    @Test fun countdownRespondsToSystemTimeJump() {
        val state = mutableStateOf(instant)
        board(true, state = state)
        compose.runOnIdle { state.value = instant.plusSeconds(65) }
        compose.onNodeWithText("00:53:55").assertIsDisplayed()
    }
    @Test fun clockwiseCornersRenderWithoutClipping() = corners(DisplayMode.PORTRAIT_ROTATE_CW)
    @Test fun counterclockwiseCornersRenderWithoutClipping() = corners(DisplayMode.PORTRAIT_ROTATE_CCW)
    private fun corners(mode: DisplayMode) {
        compose.setContent {
            RotatedBoard(mode, Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Box(Modifier.align(Alignment.TopStart).size(30.dp).background(Color.Red))
                    Box(Modifier.align(Alignment.TopEnd).size(30.dp).background(Color.Green))
                    Box(Modifier.align(Alignment.BottomStart).size(30.dp).background(Color.Blue))
                    Box(Modifier.align(Alignment.BottomEnd).size(30.dp).background(Color.Yellow))
                }
            }
        }
        val image = compose.captureRenderedNode()
        val pixels = image.toPixelMap()
        val actual = listOf(pixels[5, 5], pixels[image.width - 6, 5], pixels[5, image.height - 6], pixels[image.width - 6, image.height - 6])
        val expected = if (mode == DisplayMode.PORTRAIT_ROTATE_CW) listOf(Color.Blue, Color.Red, Color.Yellow, Color.Green)
            else listOf(Color.Green, Color.Yellow, Color.Red, Color.Blue)
        expected.zip(actual).forEach { (want, got) -> assertEquals(want, got) }
    }
}
