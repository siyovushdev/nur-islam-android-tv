package dev.nurislam.tv

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.view.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.nurislam.tv.data.ScheduleState
import dev.nurislam.tv.domain.DisplayMode
import dev.nurislam.tv.settings.*
import dev.nurislam.tv.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AdminScreenshotsTest {
    @get:Rule val compose = createComposeRule()
    @Test @Config(qualifiers = "w1080dp-h1920dp-mdpi")
    fun portrait() = render("portrait", DisplayMode.PORTRAIT_NATIVE)
    @Test @Config(qualifiers = "w1920dp-h1080dp-mdpi")
    fun landscape() = render("landscape", DisplayMode.LANDSCAPE)
    @Test @Config(qualifiers = "w1920dp-h1080dp-mdpi")
    fun clockwise() = render("cw", DisplayMode.PORTRAIT_ROTATE_CW)
    @Test @Config(qualifiers = "w1920dp-h1080dp-mdpi")
    fun counterclockwise() = render("ccw", DisplayMode.PORTRAIT_ROTATE_CCW)

    private fun key(key: Key, count: Int = 1) = compose.onRoot().performKeyInput { repeat(count) { pressKey(key) } }
    private fun image(name: String) {
        val file = File(requireNotNull(System.getProperty("nurIslam.screenshotDir")), "$name.png")
        file.parentFile!!.mkdirs()
        compose.captureRenderedNode().asAndroidBitmap().let { bitmap -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    private fun render(name: String, mode: DisplayMode) {
        compose.runOnUiThread {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
        val settings = mutableStateOf(AppSettings(displayMode = mode))
        val pending = mutableStateOf<OrientationPreview?>(null)
        compose.setContent {
            NurIslamTheme(settings.value.theme) {
                RotatedBoard(pending.value?.candidate ?: settings.value.displayMode, Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        AdminPanel(settings.value, ScheduleState(loaded = true), null, false, null, "0.2.0 · TEST RENDER", "Android/data/dev.nurislam.tv/files",
                            focusToken = pending.value,
                            onSettings = { settings.value = it(settings.value) },
                            onOrientation = { pending.value = OrientationPreview(it, SystemClock.elapsedRealtime() + 15_000) },
                            onPickFile = {}, onInbox = {}, onApply = {}, onRollback = {}, onDismissCandidate = {}, onDismissMessage = {}, onHomeSettings = {}, onOtherLauncher = {}, onClose = {})
                        pending.value?.let { OrientationConfirmation(it, { pending.value = null }, { pending.value = null }) }
                    }
                }
            }
        }
        key(Key.DirectionDown, 2); key(Key.Enter)
        BoardThemeId.entries.forEach { id ->
            compose.runOnIdle { settings.value = settings.value.copy(theme = id) }
            compose.onNodeWithText("Тема: $id").assertIsDisplayed()
            image("admin-${id.name.lowercase()}-$name")
        }
        key(Key.DirectionDown); key(Key.Enter)
        compose.onNodeWithText("Ориентация экрана").assertIsDisplayed()
        image("orientation-$name")
    }
}
