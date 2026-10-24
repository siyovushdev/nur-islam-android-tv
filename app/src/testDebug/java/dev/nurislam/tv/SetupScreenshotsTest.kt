package dev.nurislam.tv

import android.app.ActivityManager
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.nurislam.tv.domain.DisplayMode
import dev.nurislam.tv.settings.BoardThemeId
import dev.nurislam.tv.setup.DeviceStatus
import dev.nurislam.tv.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.Duration

/** Real native Compose render with simulated device facts; never hardware proof of provisioning. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SetupScreenshotsTest {
    @get:Rule val compose = createComposeRule()
    @Test @Config(qualifiers = "w1080dp-h1920dp-mdpi")
    fun elegantPortrait() = render("portrait", DisplayMode.PORTRAIT_NATIVE, BoardThemeId.ELEGANT)
    @Test @Config(qualifiers = "w1920dp-h1080dp-mdpi")
    fun elegantLandscape() = render("landscape", DisplayMode.LANDSCAPE, BoardThemeId.ELEGANT)
    @Test @Config(qualifiers = "w1920dp-h1080dp-mdpi")
    fun elegantCw() = render("cw", DisplayMode.PORTRAIT_ROTATE_CW, BoardThemeId.ELEGANT)
    @Test @Config(qualifiers = "w1920dp-h1080dp-mdpi")
    fun elegantCcw() = render("ccw", DisplayMode.PORTRAIT_ROTATE_CCW, BoardThemeId.ELEGANT)
    @Test @Config(qualifiers = "w1080dp-h1920dp-mdpi")
    fun classicPortrait() = render("portrait", DisplayMode.PORTRAIT_NATIVE, BoardThemeId.CLASSIC)
    @Test @Config(qualifiers = "w1920dp-h1080dp-mdpi")
    fun classicLandscape() = render("landscape", DisplayMode.LANDSCAPE, BoardThemeId.CLASSIC)
    @Test @Config(qualifiers = "w1920dp-h1080dp-mdpi")
    fun classicCw() = render("cw", DisplayMode.PORTRAIT_ROTATE_CW, BoardThemeId.CLASSIC)
    @Test @Config(qualifiers = "w1920dp-h1080dp-mdpi")
    fun classicCcw() = render("ccw", DisplayMode.PORTRAIT_ROTATE_CCW, BoardThemeId.CLASSIC)

    private fun key(key: Key, count: Int = 1) = compose.onRoot().performKeyInput { repeat(count) { pressKey(key) } }
    private fun capture(name: String) {
        val file = File(requireNotNull(System.getProperty("nurIslam.screenshotDir")), "$name.png")
        file.parentFile!!.mkdirs()
        compose.captureRenderedNode().asAndroidBitmap().let { bmp -> file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    private fun render(name: String, mode: DisplayMode, theme: BoardThemeId) {
        compose.runOnUiThread {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
        val status = mutableStateOf(DeviceStatus("15 / API 35 · TEST RENDER", "dev.nurislam.tv", deviceOwner = true, lockPermitted = true))
        val trial = mutableStateOf<Long?>(null)
        val proof = mutableStateOf(false)
        var cancelled = false
        compose.setContent {
            NurIslamTheme(theme) {
                RotatedBoard(mode, Modifier.fillMaxSize()) {
                    DeviceSetupWizard(status.value, false, trial.value, proof.value, false, null,
                        onDismissMessage = {}, onAction = { action ->
                            when (action) {
                                SetupAction.START_TRIAL -> {
                                    status.value = status.value.copy(lockState = ActivityManager.LOCK_TASK_MODE_LOCKED)
                                    trial.value = SystemClock.elapsedRealtime() + 20_000
                                }
                                SetupAction.CANCEL_TRIAL -> { trial.value = null; cancelled = true }
                                SetupAction.FINISH_TRIAL -> { trial.value = null; proof.value = true; status.value = status.value.copy(lockState = ActivityManager.LOCK_TASK_MODE_NONE) }
                                else -> Unit
                            }
                        })
                }
            }
        }
        val prefix = "setup-${theme.name.lowercase()}-$name"
        capture("$prefix-check")
        key(Key.Enter)
        compose.onNodeWithText("Сделать Нур Ислам главным экраном").assertIsDisplayed()
        capture("$prefix-home")
        key(Key.Enter)
        compose.onNodeWithText("Проверить безопасный выход").assertIsDisplayed()
        capture("$prefix-kiosk")
        key(Key.DirectionDown, 2); key(Key.Enter)
        compose.onNodeWithText("Завершить пробу с пульта").assertIsDisplayed()
        capture("$prefix-trial")
        // Timeout must cancel without setting proof. Then prove the remote exit explicitly.
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(21))
        compose.mainClock.advanceTimeBy(21_000)
        compose.runOnIdle { assertTrue(cancelled); assertFalse(proof.value); status.value = status.value.copy(lockState = ActivityManager.LOCK_TASK_MODE_NONE) }
        key(Key.DirectionDown, 2); key(Key.Enter)
        key(Key.DirectionDown); key(Key.Enter)
        compose.runOnIdle { assertTrue(proof.value) }
        key(Key.DirectionDown, 3); key(Key.Enter)
        compose.onNodeWithText("Отменить").assertIsDisplayed()
        capture("$prefix-confirm")
        key(Key.Back); key(Key.Enter)
        compose.onNodeWithText("Сохранить и открыть табло").assertIsDisplayed()
        capture("$prefix-summary")
    }
}
