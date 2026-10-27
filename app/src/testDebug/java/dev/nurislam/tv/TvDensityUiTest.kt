package dev.nurislam.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nurislam.tv.data.ScheduleState
import dev.nurislam.tv.domain.DisplayMode
import dev.nurislam.tv.settings.*
import dev.nurislam.tv.setup.DeviceStatus
import dev.nurislam.tv.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Physical 1920x1080 at 320 dpi: the real TV window is 960x540 dp, not 1920x1080 dp. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-xhdpi")
class TvDensityUiTest {
    @get:Rule val compose = createComposeRule()
    private fun compactLines(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { layout ->
            val fontPixels = with(layout.layoutInput.density) { layout.layoutInput.style.fontSize.toPx() }
            assertTrue("Unscaled line spacing: ${layout.layoutInput.text}: ${layout.size.height}px / ${layout.lineCount} lines, font ${fontPixels}px",
                layout.size.height <= fontPixels * layout.lineCount * 1.6f + 2)
        }
    }
    @Test fun settingsLinesScaleWithFontOnDenseTvInBothThemesAndRotations() {
        val settings = mutableStateOf(AppSettings())
        compose.setContent {
            NurIslamTheme(settings.value.theme) {
                RotatedBoard(settings.value.displayMode, Modifier.fillMaxSize()) {
                    AdminPanel(settings.value, ScheduleState(loaded = true), null, false, null, "TEST", "test-folder",
                        onSettings = {}, onOrientation = {}, onPickFile = {}, onInbox = {}, onApply = {}, onRollback = {},
                        onDismissCandidate = {}, onDismissMessage = {}, onHomeSettings = {}, onOtherLauncher = {}, onClose = {})
                }
            }
        }
        for (theme in BoardThemeId.entries) for (mode in listOf(DisplayMode.LANDSCAPE, DisplayMode.PORTRAIT_ROTATE_CW, DisplayMode.PORTRAIT_ROTATE_CCW)) {
            compose.runOnIdle { settings.value = settings.value.copy(theme = theme, displayMode = mode) }
            compactLines(compose.onNodeWithText("Один JSON: икамат, Джума, Рамадан и события", useUnmergedTree = true))
        }
    }
    @Test fun multilineWizardFactsDoNotInheritFixedBodyLineHeight() {
        compose.setContent {
            NurIslamTheme {
                DeviceSetupWizard(DeviceStatus("14 / API 34", "dev.nurislam.tv.debug"), false, null, false, false, null, {}, {})
            }
        }
        compactLines(compose.onNodeWithText("Android 14 / API 34", substring = true, useUnmergedTree = true))
    }
    @Test @Config(sdk = [34, 35])
    fun liveThemeSwitchKeepsTheSameReadableFontSizesAsFreshClassic() {
        val theme = mutableStateOf(BoardThemeId.ELEGANT)
        val visible = mutableStateOf(true)
        val scale = mutableStateOf(1.15f)
        val now = mutableStateOf(java.time.Instant.parse("2026-10-23T11:20:00Z"))
        val day = dev.nurislam.tv.data.DemoScheduleFixture().day(java.time.LocalDate.of(2026, 10, 23))!!
        compose.setContent {
            CompositionLocalProvider(LocalBoardSettings provides AppSettings(theme = theme.value, textScale = scale.value)) {
                NurIslamTheme(theme.value) {
                    if (visible.value) PrayerBoardScreen(now.value, now, day, dev.nurislam.tv.domain.nextPrayer(now.value, listOf(day)), true, true)
                }
            }
        }
        fun sizes(): List<Float> = listOf("05:08", "Нур Ислам", "Казан · Казань").map { text ->
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertEquals(1, results.size)
            results.single().getBoundingBox(0).height
        }
        sizes() // first render uses Serif, as on a fresh installation
        compose.runOnIdle { theme.value = BoardThemeId.CLASSIC }
        for (factor in listOf(1.15f, .85f, 1f, 1.15f)) {
            compose.runOnIdle { scale.value = factor }
            val switched = sizes()
            compose.runOnIdle { visible.value = false }
            compose.runOnIdle { visible.value = true }
            val fresh = sizes()
            fresh.zip(switched).forEach { (expected, actual) -> assertEquals("Live theme/scale must match a fresh render", expected, actual, 1f) }
        }
    }

}
