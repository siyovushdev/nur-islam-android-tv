package dev.nurislam.tv

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nurislam.tv.domain.DisplayMode
import dev.nurislam.tv.model.MoscowZone
import dev.nurislam.tv.model.ScheduleEvent
import dev.nurislam.tv.settings.BoardThemeId
import dev.nurislam.tv.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 35])
class PhoneActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as NurIslamApplication
    private fun waitIo() {
        compose.waitUntil(10_000) { !androidx.lifecycle.ViewModelProvider(compose.activity)[BoardViewModel::class.java].busy.value }
        compose.waitForIdle()
    }
    private fun openSettings() { compose.onNodeWithTag("phone-settings").performClick() }
    private fun tap(text: String) {
        compose.onNodeWithTag("admin-list").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
        waitIo()
    }
    private fun screenshot(name: String) {
        val file = File(System.getProperty("nurIslam.screenshotDir"), "phone-$name-api${android.os.Build.VERSION.SDK_INT}.png")
        file.parentFile!!.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun phoneLauncherHasNoHomeAdminOrProvisioningEntryPoints() {
        val pm = app.packageManager
        assertEquals("dev.nurislam.tv.phone.debug", app.packageName)
        assertNotNull(pm.getLaunchIntentForPackage(app.packageName))
        assertTrue(pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(app.packageName), 0).isEmpty())
        val info = pm.getPackageInfo(app.packageName, PackageManager.GET_RECEIVERS or PackageManager.GET_ACTIVITIES or PackageManager.GET_PERMISSIONS)
        assertFalse(info.receivers.orEmpty().any { it.name.endsWith("MosqueDeviceAdmin") })
        assertFalse(info.activities.orEmpty().any { it.name.endsWith("ProvisioningActivity") })
        assertFalse(info.requestedPermissions.orEmpty().contains("android.permission.INTERNET"))
        compose.onNodeWithTag("phone-settings").assertIsDisplayed()
        compose.onNodeWithText("Пропустить и открыть табло").assertDoesNotExist()
        openSettings()
        compose.onNodeWithTag("admin-list").performScrollToIndex(9)
        compose.onNodeWithText("Основной экран HOME / безопасный выход").assertDoesNotExist()
        compose.onNodeWithText("Мастер HOME / kiosk").assertDoesNotExist()
    }
    @Test fun touchThemeScaleAndKeyboardPersistAfterActivityRecreation() {
        openSettings()
        tap("Тема: ELEGANT")
        assertEquals(BoardThemeId.CLASSIC, app.settings.state.value.theme)
        tap("Масштаб текста: 100%")
        assertEquals(1.15f, app.settings.state.value.textScale)
        tap("Название: Нур Ислам")
        compose.onNodeWithTag("label-value").performTextReplacement("Телефон · тест")
        compose.onNodeWithText("Сохранить").performClick(); waitIo()
        assertEquals("Телефон · тест", app.settings.state.value.mosqueName)
        screenshot("settings")
        compose.activityRule.scenario.recreate()
        val saved = SettingsRepository(app.getSharedPreferences("display", 0)).state.value
        assertEquals(app.settings.state.value, saved)
        openSettings()
        compose.onNodeWithText("Тема: CLASSIC").assertIsDisplayed()
    }
    @Test fun touchOrientationCanBeCancelledConfirmedOrTimedOut() {
        openSettings()
        tap("Ориентация: AUTO")
        val cw = app.getString(R.string.mode_cw)
        tap(cw)
        compose.onNodeWithText("Вернуть прежний режим").performClick()
        assertEquals(DisplayMode.AUTO, app.settings.state.value.displayMode)
        tap(cw)
        compose.onNodeWithText("Подтвердить и сохранить").performClick(); waitIo()
        assertEquals(DisplayMode.PORTRAIT_ROTATE_CW, app.settings.state.value.displayMode)
        tap(app.getString(R.string.mode_ccw))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(16))
        compose.mainClock.advanceTimeBy(16_000)
        compose.onNodeWithTag("orientation-confirm").assertDoesNotExist()
        assertEquals(DisplayMode.PORTRAIT_ROTATE_CW, app.settings.state.value.displayMode)
        compose.activityRule.scenario.recreate()
        assertEquals(DisplayMode.PORTRAIT_ROTATE_CW, app.settings.state.value.displayMode)
    }
    @Test fun bothThemesAndAllOrientationsFitPhoneAtEveryScale() = boardFits("portrait")

    @Test @Config(sdk = [35], qualifiers = "w800dp-h360dp-xhdpi")
    fun landscapePhoneFitsAllModes() = boardFits("landscape")

    @Test @Config(sdk = [35], qualifiers = "w320dp-h568dp-hdpi")
    fun smallPhoneFitsAllModes() = boardFits("small")

    private fun boardFits(size: String) {
        val year = Instant.now().atZone(MoscowZone).year
        runBlocking { app.schedules.apply(ScheduleFixtures.schedule(year, meaning = "PRAYER_START")) }
        for (theme in BoardThemeId.entries) {
            for (mode in DisplayMode.entries) for (scale in listOf(.85f, 1f, 1.15f)) {
                runBlocking { app.settings.update { it.copy(theme = theme, displayMode = mode, textScale = scale) } }
                compose.waitForIdle()
                val viewport = compose.onNodeWithTag("phone-viewport").fetchSemanticsNode().boundsInRoot
                val board = compose.onNodeWithTag("prayer-board").fetchSemanticsNode().boundsInRoot
                assertTrue("$theme / $mode / $scale: board left", board.left >= viewport.left - 1f)
                assertTrue("$theme / $mode / $scale: board right", board.right <= viewport.right + 1f)
                assertTrue("$theme / $mode / $scale: board top", board.top >= viewport.top - 1f)
                assertTrue("$theme / $mode / $scale: board bottom", board.bottom <= viewport.bottom + 1f)
                ScheduleEvent.entries.forEach { compose.onNodeWithTag("row-${it.name}").assertIsDisplayed() }
                compose.onNodeWithTag("row-ASR").assert(hasAnyDescendant(hasText("15:15")))
                if (mode == DisplayMode.AUTO && scale == 1f) screenshot("$size-${theme.name.lowercase()}")
            }
        }
    }
    @Test fun phoneSafImportPreviewApplyAndRollbackUpdateVisibleSchedule() {
        val uri = android.net.Uri.parse("content://test.documents/phone-calendar.json")
        val year = Instant.now().atZone(MoscowZone).year
        shadowOf(app.contentResolver).registerInputStream(uri, ScheduleFixtures.json(year, "phone-one", meaning = "PRAYER_START").toString().byteInputStream())
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[BoardViewModel::class.java]
        openSettings(); tap("Расписание")
        compose.runOnIdle { vm.import(uri) }; waitIo()
        tap("Подтверждаю: источник утверждён мечетью")
        tap("Применить расписание")
        assertEquals("phone-one", app.schedules.state.value.current!!.metadata.revision)
        compose.onNodeWithTag("phone-settings").performClick()
        compose.onNodeWithTag("row-ASR").assert(hasAnyDescendant(hasText("15:15")))
        runBlocking { app.schedules.apply(ScheduleFixtures.schedule(year, "phone-two", asr = "16:00")) }
        compose.onNodeWithTag("row-ASR").assert(hasAnyDescendant(hasText("16:00")))
        openSettings(); tap("Расписание"); tap("Откат к предыдущему расписанию"); tap("Подтвердить откат")
        compose.onNodeWithTag("phone-settings").performClick()
        compose.onNodeWithTag("row-ASR").assert(hasAnyDescendant(hasText("15:15")))
    }
}
