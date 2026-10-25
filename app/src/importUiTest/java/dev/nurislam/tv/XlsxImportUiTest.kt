package dev.nurislam.tv

import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nurislam.tv.data.*
import dev.nurislam.tv.settings.*
import dev.nurislam.tv.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 35])
class XlsxImportUiTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() { val app = ApplicationProvider.getApplicationContext<android.app.Application>(); shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION") }
    }).around(compose)
    private fun captureImport(name: String) {
        if (android.os.Build.VERSION.SDK_INT != 35) return
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = File(System.getProperty("nurIslam.screenshotDir"), "$name-${BuildConfig.BUILD_TYPE}.png")
            file.parentFile!!.mkdirs(); file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun key(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }
    @Test fun completeExcelPreviewRequiresExplicitConfirmationAndKeepsTestIdentityOnPhone() {
        val value = ScheduleXlsx.parse(ScheduleXlsxTest.source(), BuildConfig.PHONE_PREVIEW)
        var applied: ValidatedSchedule? = null
        compose.activity.setContent { NurIslamTheme { ScheduleImportPreview(value, ScheduleState(), false, BuildConfig.PHONE_PREVIEW,
            onApply = { applied = ScheduleImport.confirmExcel(value) }, onCancel = {}) } }
        compose.onNodeWithTag("import-excel-notice").assertExists()
        compose.onNodeWithTag("import-apply").assertIsNotEnabled()
        captureImport("xlsx-preview-unconfirmed")
        if (BuildConfig.PHONE_PREVIEW) {
            compose.onNodeWithText("Понимаю: тестовое расписание не утверждено мечетью").assertIsDisplayed()
            compose.onNodeWithTag("import-confirmation").performClick()
            compose.onNodeWithTag("import-apply").performClick()
        } else {
            key(Key.DirectionDown); key(Key.Enter); key(Key.DirectionDown); key(Key.Enter)
        }
        compose.runOnIdle { assertTrue(applied!!.metadata.approved); assertEquals(BuildConfig.PHONE_PREVIEW, isTestSource(applied!!.metadata.source)) }
        compose.onNodeWithTag("import-apply").assertIsDisplayed()
        captureImport("xlsx-preview-confirmed")
    }
    @Test fun folderSupportsDpadNavigationBackAndTouchWithoutOpeningFilesAutomatically() {
        val root = Uri.parse("content://example.documents/tree/root")
        var chosen: Uri? = null
        var closed = false
        val load: suspend (String) -> List<ImportDocument> = { id -> if (id == "root") listOf(ImportDocument("sub", "USB", true)) else listOf(ImportDocument("sub/year.xlsx", "year.xlsx", false)) }
        compose.activity.setContent { NurIslamTheme { ImportFolderScreen(root, BuildConfig.PHONE_PREVIEW, { chosen = it }, {}, { closed = true }, load, false) } }
        compose.waitForIdle()
        key(Key.DirectionDown); key(Key.DirectionDown); key(Key.Enter)
        compose.onNodeWithText("year.xlsx").assertExists()
        assertNull(chosen)
        compose.onNodeWithTag("folder-item-2").performClick()
        assertEquals("content://example.documents/tree/root/document/sub%2Fyear.xlsx", chosen.toString())
        key(Key.Back)
        compose.onNodeWithText("Папка: USB").assertExists()
        key(Key.Back); assertTrue(closed)
    }
    @Test fun folderProviderFailureOffersSafeReselection() {
        var reselected = false
        compose.activity.setContent { NurIslamTheme { ImportFolderScreen(Uri.parse("content://example.documents/tree/root"), BuildConfig.PHONE_PREVIEW, {}, { reselected = true }, {}, { throw SecurityException("permission revoked") }, false) } }
        compose.onNodeWithText("Папка недоступна:", substring = true).assertExists()
        key(Key.DirectionDown); key(Key.Enter); assertTrue(reselected)
    }
    @Test fun explicitlyTestNamedExcelCannotBecomeOfficialInTvButPhoneCanAcknowledgeDemo() {
        val candidate = ScheduleImport.parse(ScheduleXlsxTest.source(), sourceName = "ТЕСТ ДЛЯ ТЕЛЕФОНА.xlsx")
        var applied = false
        assertTrue(isTestSource(candidate.metadata.source)); assertFalse(candidate.metadata.approved)
        compose.activity.setContent { NurIslamTheme { ScheduleImportPreview(candidate, ScheduleState(), false, BuildConfig.PHONE_PREVIEW, { applied = true }, {}) } }
        compose.onNodeWithTag("import-apply").assertIsNotEnabled()
        key(Key.DirectionDown); key(Key.Enter); key(Key.DirectionDown); key(Key.Enter)
        assertEquals(BuildConfig.PHONE_PREVIEW, applied)
        if (!BuildConfig.PHONE_PREVIEW) compose.onNodeWithTag("import-confirmation").assertIsNotEnabled().assertIsOff()
    }
    @Test fun invalidSavedFolderUriOffersReselectionWithoutCrashing() {
        var selected = false
        compose.activity.setContent { NurIslamTheme { ImportFolderScreen(Uri.parse("file:///invalid"), BuildConfig.PHONE_PREVIEW, {}, { selected = true }, {}) } }
        compose.onNodeWithText("Папка недоступна").assertExists()
        key(Key.Enter); assertTrue(selected)
        assertNull(DocumentFolder.treeOrNull("content://example.documents/not-a-tree"))
        assertNull(DocumentFolder.treeOrNull("bad"))
    }
    @Test fun futureImportWarnsIfItEvictsTheOnlyCalendarForToday() {
        val year = java.time.Instant.now().atZone(dev.nurislam.tv.model.MoscowZone).year
        val previous = ScheduleFixtures.schedule(year)
        val current = ScheduleFixtures.schedule(year + 1)
        val future = ScheduleFixtures.schedule(year + 2)
        compose.activity.setContent { NurIslamTheme { ScheduleImportPreview(future, ScheduleState(current, previous, true), false, BuildConfig.PHONE_PREVIEW, {}, {}) } }
        compose.onNodeWithTag("import-displaces-current-year").performScrollTo().assertExists()
    }
    @Test fun importRetainingCalendarForTodayDoesNotShowEvictionWarning() {
        val year = java.time.Instant.now().atZone(dev.nurislam.tv.model.MoscowZone).year
        compose.activity.setContent { NurIslamTheme { ScheduleImportPreview(ScheduleFixtures.schedule(year + 1), ScheduleState(ScheduleFixtures.schedule(year), loaded = true), false, BuildConfig.PHONE_PREVIEW, {}, {}) } }
        compose.onNodeWithTag("import-displaces-current-year").assertDoesNotExist()
    }
    @Test fun activityImportsExcelAndRestoresExactOriginalTimesAfterRestartAndRollback() {
        val app = compose.activity.application as NurIslamApplication
        compose.waitUntil(10_000) { app.schedules.state.value.loaded }
        if (BuildConfig.PHONE_PREVIEW) compose.onNodeWithTag("phone-settings").performClick()
        else {
            compose.onNodeWithText("Пропустить и открыть табло").performClick()
            repeat(5) { key(Key.DirectionCenter) }; key(Key.DirectionDown); key(Key.Enter)
        }
        compose.onNodeWithTag("admin-list").performScrollToNode(hasText("Расписание"))
        compose.onNodeWithText("Расписание").performClick()
        val file = File(app.cacheDir, "calendar-kazan.xlsx").apply { writeBytes(ScheduleXlsxTest.source()) }
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[BoardViewModel::class.java]
        compose.runOnIdle { vm.import(Uri.fromFile(file)) }
        compose.waitUntil(15_000) { shadowOf(android.os.Looper.getMainLooper()).idle(); !vm.busy.value }
        val original = vm.candidate.value!!
        assertFalse(original.metadata.approved)
        assertEquals(365, original.days.size)
        compose.runOnIdle { vm.applyCandidate() }
        compose.waitUntil(10_000) { shadowOf(android.os.Looper.getMainLooper()).idle(); !vm.busy.value }
        assertNull(app.schedules.state.value.current)
        assertTrue(vm.message.value!!.contains("Сначала подтвердите"))
        compose.runOnIdle { vm.dismissMessage() }
        compose.onNodeWithTag("import-confirmation").performClick()
        compose.onNodeWithTag("import-apply").performClick()
        compose.waitUntil(10_000) { shadowOf(android.os.Looper.getMainLooper()).idle(); !vm.busy.value }
        assertEquals(original.days, app.schedules.state.value.current!!.days)
        runBlocking { app.schedules.apply(ScheduleFixtures.schedule(revision = "other")) }
        runBlocking { app.schedules.rollback() }
        compose.activityRule.scenario.recreate()
        assertEquals(original.days, app.schedules.state.value.current!!.days)
        file.delete(); Unit
    }
}
