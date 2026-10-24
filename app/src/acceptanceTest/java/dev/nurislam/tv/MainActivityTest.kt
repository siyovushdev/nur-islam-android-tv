package dev.nurislam.tv

import android.content.Intent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nurislam.tv.data.ScheduleJson
import dev.nurislam.tv.settings.BoardThemeId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import java.time.Duration
import java.time.LocalDate

/** Runs against the actual Release MainActivity too; no debug-only menu or bundled calendar. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @org.junit.Before fun completeInitialSetup() {
        // Existing board/settings scenarios begin after the separately tested first-run wizard.
        compose.onNodeWithText("Пропустить и открыть табло").performClick()
    }
    private val app get() = compose.activity.application as NurIslamApplication
    private fun key(key: Key, count: Int = 1) { compose.onRoot().performKeyInput { repeat(count) { pressKey(key) } } }
    private fun settings() {
        key(Key.DirectionCenter, 5)
        compose.onNodeWithText("Вход в настройки").assertIsDisplayed()
        key(Key.DirectionDown); key(Key.Enter)
        compose.onNodeWithText("Тема: ELEGANT").assertIsDisplayed()
    }
    private fun waitIo() { compose.waitUntil(10_000) { !androidx.lifecycle.ViewModelProvider(compose.activity)[BoardViewModel::class.java].busy.value }; compose.waitForIdle() }

    @Test fun releaseRemoteEntryThemePersistsAfterRecreate() {
        settings()
        key(Key.DirectionDown); key(Key.Enter); waitIo()
        compose.onNodeWithText("Тема: CLASSIC").assertIsDisplayed()
        assertEquals(BoardThemeId.CLASSIC, app.settings.state.value.theme)
        key(Key.Back)
        compose.activityRule.scenario.recreate()
        assertEquals(BoardThemeId.CLASSIC, app.settings.state.value.theme)
        key(Key.DirectionCenter, 5); key(Key.DirectionDown); key(Key.Enter)
        compose.onNodeWithText("Тема: CLASSIC").assertIsDisplayed()
    }

    @Test fun orientationIsNotSavedWithoutConfirmationAndSurvivesOnlyWhenConfirmed() {
        settings()
        key(Key.DirectionDown, 2); key(Key.Enter); key(Key.DirectionDown); key(Key.Enter)
        compose.onNodeWithTag("orientation-confirm").assertIsDisplayed()
        assertEquals("AUTO", app.settings.state.value.displayMode.name)
        key(Key.Back)
        compose.onNodeWithTag("orientation-confirm").assertDoesNotExist()
        key(Key.Enter)
        key(Key.DirectionDown); key(Key.Enter); waitIo()
        compose.waitUntil(5_000) { app.settings.state.value.displayMode.name == "PORTRAIT_NATIVE" }
        compose.activityRule.scenario.recreate()
        assertEquals("PORTRAIT_NATIVE", app.settings.state.value.displayMode.name)
    }

    @Test fun orientationTimeoutAndRecreateReturnToConfirmedMode() {
        settings(); key(Key.DirectionDown, 2); key(Key.Enter); key(Key.DirectionDown); key(Key.Enter)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(16))
        compose.mainClock.advanceTimeBy(16_000)
        compose.onNodeWithTag("orientation-confirm").assertDoesNotExist()
        assertEquals("AUTO", app.settings.state.value.displayMode.name)
        // The settings root must regain D-pad focus after timeout.
        key(Key.Enter)
        compose.onNodeWithTag("orientation-confirm").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("orientation-confirm").assertDoesNotExist()
        assertEquals("AUTO", app.settings.state.value.displayMode.name)
    }

    @Test fun inboxPreviewConfirmationAndRollbackUpdateRepositoryWithoutRestart() {
        compose.waitUntil(5_000) { app.schedules.state.value.loaded }
        val year = Instant.now().atZone(dev.nurislam.tv.model.MoscowZone).year.coerceIn(2000, 2200)
        val file = java.io.File(app.getExternalFilesDir(null), "incoming-schedule.json")
        file.writeText(ScheduleFixtures.json(year, "revision-one", meaning = "PRAYER_START").toString())
        settings()
        key(Key.DirectionDown, 7); key(Key.Enter)
        key(Key.DirectionDown, 2); key(Key.Enter); waitIo()
        compose.onNodeWithText("Подтверждаю: источник утверждён мечетью").assertIsDisplayed()
        assertNull(app.schedules.state.value.current)
        key(Key.DirectionDown, 2); key(Key.Enter)
        key(Key.DirectionDown); key(Key.Enter); waitIo()
        assertEquals("revision-one", app.schedules.state.value.current!!.metadata.revision)
        compose.onNodeWithText("Понятно").performClick()
        file.writeText(ScheduleFixtures.json(year, "revision-two", asr = "16:00", meaning = "PRAYER_START").toString())
        key(Key.DirectionDown, 2); key(Key.Enter); waitIo()
        key(Key.DirectionDown, 2); key(Key.Enter); key(Key.DirectionDown); key(Key.Enter); waitIo()
        assertEquals("revision-two", app.schedules.state.value.current!!.metadata.revision)
        compose.onNodeWithText("Понятно").performClick()
        key(Key.DirectionDown, 3); key(Key.Enter)
        key(Key.DirectionDown); key(Key.Enter); waitIo()
        assertEquals("revision-one", app.schedules.state.value.current!!.metadata.revision)
        val restored = dev.nurislam.tv.data.LocalScheduleRepository(dev.nurislam.tv.data.AtomicScheduleStorage(java.io.File(app.filesDir, "schedule-store.json")))
        runBlocking { restored.load() }
        assertEquals("revision-one", restored.state.value.current!!.metadata.revision)
        file.delete()
    }

    @Test fun missingAndInvalidInboxShowErrorsAndPreserveCurrentData() {
        compose.waitUntil(5_000) { app.schedules.state.value.loaded }
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[BoardViewModel::class.java]
        val file = java.io.File(app.getExternalFilesDir(null), "incoming-schedule.json")
        file.delete(); compose.runOnIdle { vm.importInbox() }; waitIo()
        assertNotNull(vm.message.value); assertNull(app.schedules.state.value.current)
        file.writeText("{broken}"); compose.runOnIdle { vm.importInbox() }; waitIo()
        assertNotNull(vm.message.value); assertNull(app.schedules.state.value.current)
    }

    @Test fun importedScheduleReplacesVisibleRowsImmediatelyAndRollbackRestoresThem() {
        compose.waitUntil(5_000) { app.schedules.state.value.loaded }
        val year = Instant.now().atZone(dev.nurislam.tv.model.MoscowZone).year
        runBlocking { app.schedules.apply(ScheduleFixtures.schedule(year, "first")) }
        compose.onNodeWithTag("row-ASR").assert(hasAnyDescendant(hasText("15:15")))
        runBlocking { app.schedules.apply(ScheduleFixtures.schedule(year, "second", asr = "16:00")) }
        compose.onNodeWithTag("row-ASR").assert(hasAnyDescendant(hasText("16:00")))
        runBlocking { app.schedules.rollback() }
        compose.onNodeWithTag("row-ASR").assert(hasAnyDescendant(hasText("15:15")))
        runBlocking { app.schedules.apply(ScheduleFixtures.schedule(year + 1, "next-year")) }
        compose.onNodeWithTag("row-ASR").assert(hasAnyDescendant(hasText("15:15")))
        runBlocking { app.schedules.apply(ScheduleFixtures.schedule(year + 2, "unsupported-year")) }
        compose.onNodeWithText("Расписание недоступно", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("15:15").assertCountEquals(0)
    }

    @Test fun safUriIsParsedButUnapprovedDataCannotBeApplied() {
        val uri = android.net.Uri.parse("content://test.documents/calendar.json")
        shadowOf(app.contentResolver).registerInputStream(uri, java.io.ByteArrayInputStream(ScheduleFixtures.json(approved = false).toString().toByteArray()))
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[BoardViewModel::class.java]
        compose.runOnIdle { vm.import(uri) }; waitIo()
        assertNotNull(vm.candidate.value)
        assertFalse(vm.candidate.value!!.metadata.approved)
        compose.runOnIdle { vm.applyCandidate() }; waitIo()
        assertNull(app.schedules.state.value.current)
        assertNotNull(vm.message.value)
    }

    @Test fun labelEditorWorksWithDpadWithoutSystemKeyboard() {
        settings(); key(Key.DirectionDown, 5); key(Key.Enter)
        compose.onNodeWithTag("label-editor").assertIsDisplayed()
        key(Key.Enter) // Cyrillic А.
        compose.onNodeWithTag("label-value").assert(hasText("Нур ИсламА"))
        key(Key.Back)
        assertEquals("Нур Ислам", app.settings.state.value.mosqueName)
        key(Key.Enter); key(Key.Enter)
        key(Key.DirectionLeft, 2); key(Key.Enter); waitIo()
        assertEquals("Нур ИсламА", app.settings.state.value.mosqueName)
    }

    @Test fun releaseMenuAndThemeSwitchWorkInEveryDisplayMode() {
        for (mode in dev.nurislam.tv.domain.DisplayMode.entries) {
            runBlocking { app.settings.update { it.copy(displayMode = mode, theme = BoardThemeId.ELEGANT) } }
            settings()
            key(Key.DirectionDown); key(Key.Enter); waitIo()
            compose.onNodeWithText("Тема: CLASSIC").assertIsDisplayed()
            key(Key.Back)
            compose.onNodeWithTag("admin-panel").assertDoesNotExist()
        }
    }

    @Test fun cancellingBlockedImportKeepsBoardUsableAndDoesNotPublishLateCandidate() {
        val uri = android.net.Uri.parse("content://test.documents/blocked.json")
        val entered = java.util.concurrent.CountDownLatch(1)
        val closed = java.util.concurrent.CountDownLatch(1)
        val input = object : java.io.InputStream() {
            override fun read(): Int { entered.countDown(); closed.await(); throw java.io.IOException("Closed") }
            override fun close() { closed.countDown() }
        }
        shadowOf(app.contentResolver).registerInputStream(uri, input)
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[BoardViewModel::class.java]
        compose.runOnIdle { vm.import(uri) }
        assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
        compose.runOnIdle { vm.cancelPendingImport() }
        assertTrue(closed.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertFalse(vm.busy.value)
        assertNull(vm.candidate.value)
        settings()
    }

    @Test fun homeIntentReturnsToPublicBoard() {
        settings()
        compose.runOnIdle { androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(compose.activity, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) }
        compose.onNodeWithTag("admin-panel").assertDoesNotExist()
    }

    @Test fun nativeManifestOffersHomeAndDoesNotRequestNetwork() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(compose.activity.packageName)
        assertTrue(compose.activity.packageManager.queryIntentActivities(home, 0).isNotEmpty())
        val info = compose.activity.packageManager.getPackageInfo(compose.activity.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        assertFalse(info.requestedPermissions.orEmpty().contains("android.permission.INTERNET"))
        assertTrue(compose.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
    }
}
