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
    @org.junit.Before fun completeInitialSetup() { compose.onNodeWithTag("device-setup").assertDoesNotExist() }
    private val app get() = compose.activity.application as NurIslamApplication
    private fun key(key: Key, count: Int = 1) { compose.onRoot().performKeyInput { repeat(count) { pressKey(key) } } }
    private fun settings() { key(Key.DirectionCenter); compose.onNodeWithText("Загрузить расписание").assertIsDisplayed() }
    private fun tap(text: String) { compose.onNodeWithTag("admin-list").performScrollToNode(hasText(text)); compose.onNodeWithText(text).performClick(); waitIo() }
    private fun orientation() { settings(); tap("Внешний вид"); tap("Ориентация экрана"); tap(app.getString(R.string.mode_native)) }
    private fun waitIo() { compose.waitUntil(10_000) { !androidx.lifecycle.ViewModelProvider(compose.activity)[BoardViewModel::class.java].busy.value }; compose.waitForIdle() }

    @Test fun releaseRemoteEntryThemePersistsAfterRecreate() {
        settings(); key(Key.DirectionDown, 2); key(Key.Enter); key(Key.Enter); waitIo()
        assertEquals(BoardThemeId.CLASSIC,app.settings.state.value.theme)
        key(Key.Back); key(Key.Back)
        compose.onNodeWithTag("admin-panel").assertDoesNotExist()
        compose.activityRule.scenario.recreate(); settings(); tap("Внешний вид")
        compose.onNodeWithText("Тема: CLASSIC").assertIsDisplayed()
    }

    @Test fun dataPageDpadSkipsInformationAndAlwaysKeepsAnActionHighlighted() {
        settings(); tap("Загруженные данные")
        // First available action is the folder picker, preceded by two read-only rows.
        key(Key.DirectionUp)
        key(Key.DirectionDown)
        compose.onNodeWithText("Выбрать папку с файлами").assert(
            SemanticsMatcher.expectValue(dev.nurislam.tv.ui.ImportActionHighlighted, true))
        key(Key.DirectionUp); key(Key.Enter)
        compose.onNodeWithText("Загрузить расписание").assertIsDisplayed()
    }

    @Test fun orientationIsNotSavedWithoutConfirmationAndSurvivesOnlyWhenConfirmed() {
        orientation(); assertEquals("AUTO",app.settings.state.value.displayMode.name)
        key(Key.Back); compose.onNodeWithTag("orientation-confirm").assertDoesNotExist()
        key(Key.Enter); compose.onNodeWithTag("orientation-confirm").assertIsDisplayed()
        key(Key.DirectionDown); key(Key.Enter); waitIo()
        assertEquals("PORTRAIT_NATIVE",app.settings.state.value.displayMode.name)
        compose.activityRule.scenario.recreate(); assertEquals("PORTRAIT_NATIVE",app.settings.state.value.displayMode.name)
    }

    @Test fun orientationTimeoutAndRecreateReturnToConfirmedMode() {
        orientation(); shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(16)); compose.mainClock.advanceTimeBy(16_000)
        compose.onNodeWithTag("orientation-confirm").assertDoesNotExist(); assertEquals("AUTO",app.settings.state.value.displayMode.name)
        key(Key.Enter); compose.onNodeWithTag("orientation-confirm").assertIsDisplayed()
        compose.activityRule.scenario.recreate(); assertEquals("AUTO",app.settings.state.value.displayMode.name)
    }

    @Test fun inboxPreviewApplyAndRollbackUpdateRepositoryWithoutRestart() {
        compose.waitUntil(5_000) { app.schedules.state.value.loaded }
        val year=Instant.now().atZone(app.schedules.state.value.zone).year
        val file=java.io.File(app.getExternalFilesDir(null),"incoming-schedule.json")
        file.writeText(ScheduleFixtures.json(year,"one",approved=false).toString())
        settings(); tap("Для специалиста"); tap("Резервный импорт из папки приложения")
        compose.onNodeWithTag("import-apply").assertIsEnabled(); assertNull(app.schedules.state.value.current)
        key(Key.Enter); waitIo(); assertEquals("one",app.schedules.state.value.current!!.metadata.revision)
        compose.onNodeWithText("Вернуться к табло").performClick()
        file.writeText(ScheduleFixtures.json(year,"two",asr="16:00").toString())
        settings(); tap("Для специалиста"); tap("Резервный импорт из папки приложения"); key(Key.Enter); waitIo()
        compose.onNodeWithText("Вернуться к табло").performClick()
        settings(); tap("Загруженные данные"); tap("Вернуть предыдущее расписание")
        assertEquals("one",app.schedules.state.value.current!!.metadata.revision)
        val restored=dev.nurislam.tv.data.LocalScheduleRepository(dev.nurislam.tv.data.AtomicScheduleStorage(java.io.File(app.filesDir,"schedule-store.json")))
        runBlocking { restored.load() }; assertEquals("one",restored.state.value.current!!.metadata.revision); file.delete()
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

    @Test fun safUriPreservesSourceFlagsWithoutApprovalGate() {
        val uri = android.net.Uri.parse("content://test.documents/calendar.json")
        shadowOf(app.contentResolver).registerInputStream(uri, java.io.ByteArrayInputStream(ScheduleFixtures.json(approved = false).toString().toByteArray()))
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[BoardViewModel::class.java]
        compose.runOnIdle { vm.import(uri) }; waitIo()
        assertNotNull(vm.candidate.value)
        assertFalse(vm.candidate.value!!.metadata.approved)
        compose.runOnIdle { vm.applyCandidate() }; waitIo()
        assertNotNull(app.schedules.state.value.current)
        assertFalse(app.schedules.state.value.current!!.metadata.approved)
    }

    @Test fun noDataGivesDirectImportAndOneOkMenu() {
        compose.onNodeWithTag("load-schedule").assertIsDisplayed()
        settings(); key(Key.Enter)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT,shadowOf(compose.activity).nextStartedActivityForResult.intent.action)
    }

    @Test fun releaseMenuAndThemeSwitchWorkInEveryDisplayMode() {
        for(mode in dev.nurislam.tv.domain.DisplayMode.entries) {
            runBlocking { app.settings.update { it.copy(displayMode=mode,theme=BoardThemeId.ELEGANT) } }
            settings(); key(Key.DirectionDown,2);key(Key.Enter);key(Key.Enter);waitIo()
            assertEquals(BoardThemeId.CLASSIC,app.settings.state.value.theme)
            key(Key.Back);key(Key.Back);compose.onNodeWithTag("admin-panel").assertDoesNotExist()
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
