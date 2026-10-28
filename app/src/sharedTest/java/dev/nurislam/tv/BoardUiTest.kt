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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import dev.nurislam.tv.data.DemoScheduleFixture
import dev.nurislam.tv.domain.*
import dev.nurislam.tv.model.ScheduleEvent
import dev.nurislam.tv.settings.BoardThemeId
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
    private val day = DemoScheduleFixture().day(LocalDate.of(2026, 10, 23))!!

    private fun board(landscape: Boolean, unavailable: Boolean = false, state: State<Instant> = mutableStateOf(instant), theme: State<BoardThemeId> = mutableStateOf(BoardThemeId.ELEGANT)) {
        compose.setContent {
            NurIslamTheme(theme.value) {
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
    @Test fun fajrNameSeparatesKnownStartFromLegacyMosqueColumnInBothThemes() {
        val meaning = mutableStateOf(dev.nurislam.tv.data.FajrMeaning.PRAYER_START)
        val theme = mutableStateOf(BoardThemeId.ELEGANT)
        compose.setContent {
            NurIslamTheme(theme.value) {
                CompositionLocalProvider(LocalBoardSettings provides dev.nurislam.tv.settings.AppSettings(language = dev.nurislam.tv.settings.BoardLanguage.RUSSIAN)) {
                    PrayerBoardScreen(instant, remember { mutableStateOf(instant) }, day, null, false, false, fajrMeaning = meaning.value)
                }
            }
        }
        for (style in BoardThemeId.entries) {
            compose.runOnIdle { theme.value = style; meaning.value = dev.nurislam.tv.data.FajrMeaning.PRAYER_START }
            compose.onNodeWithTag("row-FAJR_IN_MOSQUE").assert(hasAnyDescendant(hasText("Фаджр")))
            compose.runOnIdle { meaning.value = dev.nurislam.tv.data.FajrMeaning.UNCONFIRMED }
            compose.onNodeWithTag("row-FAJR_IN_MOSQUE").assert(hasAnyDescendant(hasText("Фаджр (в мечети)")))
        }
    }

    @Test fun portraitShowsAllEightRowsAndSeparateCountdown() {
        board(false)
        ScheduleEvent.entries.forEach { compose.onNodeWithTag("row-${it.name}").assertIsDisplayed() }
        compose.onNodeWithTag("next-time").assert(hasAnyDescendant(hasText("15:15")))
        compose.onNodeWithTag("countdown").assert(hasAnyDescendant(hasText("00:55:00")))
        compose.onNodeWithTag("demo-label").assertDoesNotExist()
        saveScreenshot("portrait")
    }
    @Test fun landscapeShowsAllEightRows() {
        board(true)
        ScheduleEvent.entries.forEach { compose.onNodeWithTag("row-${it.name}").assertIsDisplayed() }
        saveScreenshot("landscape")
    }
    @Test fun unavailableKeepsClockAndNeverShowsSampleTimes() {
        board(true, unavailable = true)
        compose.onNodeWithText("Расписание недоступно", substring = true).assertIsDisplayed()
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
    @Test fun mainClockBlinksOnlyItsColonWithoutMovingDigitsInPortrait() = clockBlink(false)
    @Test fun mainClockBlinksOnlyItsColonWithoutMovingDigitsInLandscape() = clockBlink(true)
    private fun clockBlink(landscape: Boolean) {
        val seconds = mutableStateOf(instant)
        val theme = mutableStateOf(BoardThemeId.ELEGANT)
        board(landscape, state = seconds, theme = theme)
        for (id in BoardThemeId.entries) {
            compose.runOnIdle { theme.value = id; seconds.value = instant }
            val clock = compose.onNodeWithTag("clock")
            val label = compose.onNode(hasAnyAncestor(hasTestTag("clock")) and hasText("14:20"), useUnmergedTree = true)
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val bounds = clock.fetchSemanticsNode().boundsInRoot
            val labelBounds = label.fetchSemanticsNode().boundsInRoot
            val colon = layouts.single().getBoundingBox(2).translate(labelBounds.topLeft - bounds.topLeft)
            val on = compose.captureRenderedNode("clock").toPixelMap()
            val row = compose.captureRenderedNode("row-ASR").toPixelMap()
            saveScreenshot("clock-colon-on-${id.name.lowercase()}-${if (landscape) "landscape" else "portrait"}")
            compose.runOnIdle { seconds.value = instant.plusSeconds(1) }
            label.assertTextEquals("14:20") // Accessibility never loses the separator.
            assertEquals(bounds, clock.fetchSemanticsNode().boundsInRoot)
            val off = compose.captureRenderedNode("clock").toPixelMap()
            var changes = 0
            for (y in 0 until on.height) for (x in 0 until on.width) if (on[x, y] != off[x, y]) {
                changes++
                assertTrue("Digit changed outside colon: $id/$landscape ($x,$y)", x >= colon.left - 1 && x <= colon.right + 1 && y >= colon.top - 1 && y <= colon.bottom + 1)
            }
            assertTrue("Colon did not blink: $id/$landscape", changes > 0)
            val sameRow = compose.captureRenderedNode("row-ASR").toPixelMap()
            for (y in 0 until row.height) for (x in 0 until row.width) assertEquals(row[x, y], sameRow[x, y])
            compose.onNodeWithTag("next-time").assert(hasAnyDescendant(hasText("15:15")))
            compose.onNodeWithTag("countdown").assert(hasAnyDescendant(hasText("00:54:59")))
            saveScreenshot("clock-colon-off-${id.name.lowercase()}-${if (landscape) "landscape" else "portrait"}")
            compose.runOnIdle { seconds.value = instant.plusSeconds(2) }
            val onAgain = compose.captureRenderedNode("clock").toPixelMap()
            for (y in 0 until on.height) for (x in 0 until on.width) assertEquals(on[x, y], onAgain[x, y])
        }
    }
    @Test fun unconfirmedFajrShowsUncertaintyInsteadOfDhuhrCountdown() {
        val morning = Instant.parse("2026-10-23T03:00:00Z")
        val clock = mutableStateOf(morning)
        compose.setContent {
            NurIslamTheme {
                Box(Modifier.requiredSize(960.dp, 540.dp)) {
                    PrayerBoardScreen(morning, clock, day, null, true, true,
                        status = prayerStatus(morning, listOf(day), dev.nurislam.tv.data.FajrMeaning.UNCONFIRMED))
                }
            }
        }
        compose.onNodeWithText("Время Фаджра требует подтверждения", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("next-time").assertDoesNotExist()
        compose.onNodeWithTag("countdown").assertDoesNotExist()
        saveScreenshot("fajr-unconfirmed")
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
