package dev.nurislam.tv

import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nurislam.tv.data.*
import dev.nurislam.tv.settings.AppSettings
import dev.nurislam.tv.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.ExternalResource
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The same preview controls run in TV Debug and Release, without any Debug approval bypass. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 35])
class ScheduleImportUiTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            // Android grants the app's own signature permission. Robolectric API 26 needs it explicitly.
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
            shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        }
    }).around(compose)
    private val pending = mutableStateOf<ValidatedSchedule?>(null)
    private val applied = mutableStateOf<ValidatedSchedule?>(null)
    private var appliedCount = 0
    private var cancelCount = 0
    private var closed by mutableStateOf(false)
    private fun value(approved: Boolean = true, revision: String = "first", source: String = "Календарь мечети, протокол 42") = ScheduleFixtures.json(approved = approved, revision = revision).apply {
        addProperty("source", source) // Fabricated test fixture, never runtime data.
    }.let { ScheduleJson.parse(it.toString().toByteArray()) }
    private fun show(approved: Boolean = true, source: String = "Календарь мечети, протокол 42") {
        pending.value = value(approved, source = source)
        compose.activity.setContent {
            NurIslamTheme {
                if (closed) Text("Табло открыто")
                else AdminPanel(AppSettings(), ScheduleState(), pending.value, false, null, "TEST", "", appliedSchedule = applied.value,
                    onSettings = {}, onOrientation = {}, onPickFile = {}, onInbox = {}, onApply = {
                        appliedCount++; applied.value = pending.value; pending.value = null
                    }, onRollback = {}, onDismissCandidate = { cancelCount++; pending.value = null },
                    onDismissMessage = { applied.value = null }, onHomeSettings = {}, onOtherLauncher = {}, onClose = { closed = true })
            }
        }
    }
    private fun key(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }
    @Test fun dpadVisitsActionsSkipsInformationAndReturnsToBoardAfterSuccess() {
        show()
        compose.onNodeWithTag("import-cancel").assert(SemanticsMatcher.expectValue(ImportActionHighlighted, true))
        compose.onNodeWithTag("import-apply").assertIsNotEnabled()
        key(Key.DirectionDown)
        compose.onNodeWithTag("import-confirmation").assert(SemanticsMatcher.expectValue(ImportActionHighlighted, true)).assertIsOff()
        key(Key.Enter)
        compose.onNodeWithTag("import-confirmation").assertIsOn()
        compose.onNodeWithTag("import-confirmation").assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected))
        compose.onNodeWithTag("import-apply").assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected))
        key(Key.DirectionDown)
        compose.onNodeWithTag("import-apply").assert(SemanticsMatcher.expectValue(ImportActionHighlighted, true)).assertIsEnabled()
        key(Key.Enter)
        assertEquals(1, appliedCount)
        compose.onNodeWithTag("import-result").assertIsDisplayed()
        key(Key.Enter)
        compose.onNodeWithText("Табло открыто").assertIsDisplayed()
    }
    @Test fun dpadCancelReturnsToScheduleWithoutApplying() {
        show(); key(Key.Enter)
        assertEquals(1, cancelCount)
        assertEquals(0, appliedCount)
        compose.onNodeWithText("Импорт JSON / XLSX — выбрать файл").assertIsDisplayed()
    }
    @Test fun replacingCandidateClearsConfirmation() {
        show()
        key(Key.DirectionDown); key(Key.Enter)
        compose.onNodeWithTag("import-confirmation").assertIsOn()
        compose.onNodeWithTag("import-confirmation").assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected))
        compose.onNodeWithTag("import-apply").assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected))
        compose.runOnIdle { pending.value = value(revision = "replacement") }
        compose.onNodeWithTag("import-confirmation").assertIsOff()
        compose.onNodeWithTag("import-apply").assertIsNotEnabled()
    }
    @Test fun unapprovedCannotBeConfirmedOrAppliedInReleaseEither() {
        show(approved = false)
        key(Key.DirectionDown); key(Key.Enter); key(Key.DirectionDown); key(Key.Enter)
        compose.onNodeWithTag("import-confirmation").assertIsNotEnabled().assertIsOff()
        compose.onNodeWithTag("import-apply").assertIsNotEnabled()
        assertEquals(0, appliedCount)
    }
    @Test fun viewModelRejectsTestSourceEvenWhenUiConfirmationIsBypassed() {
        assertFalse(BuildConfig.PHONE_PREVIEW)
        val app = compose.activity.application as NurIslamApplication
        compose.waitUntil(10_000) { app.schedules.state.value.loaded }
        val file = java.io.File(app.cacheDir, "qa-calendar.json").apply { writeText(value(source = "ТЕСТ ДЛЯ ТЕЛЕФОНА").json) }
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[BoardViewModel::class.java]
        compose.runOnIdle { vm.import(android.net.Uri.fromFile(file)) }
        compose.waitUntil(10_000) { shadowOf(android.os.Looper.getMainLooper()).idle(); !vm.busy.value }
        assertTrue(isTestSource(vm.candidate.value!!.metadata.source))
        compose.runOnIdle { vm.applyCandidate(excelConfirmed = true) }
        compose.waitUntil(10_000) { shadowOf(android.os.Looper.getMainLooper()).idle(); !vm.busy.value }
        assertNull(app.schedules.state.value.current)
        assertTrue(vm.message.value!!.contains("Тестовый источник нельзя"))
        file.delete()
    }
    @Test fun approvedPhoneTestSourceRemainsBlockedInTvBuilds() {
        assertFalse(BuildConfig.PHONE_PREVIEW)
        show(source = "ТЕСТ ДЛЯ ТЕЛЕФОНА")
        compose.onNodeWithTag("import-source-warning").assertIsDisplayed()
        compose.onNodeWithText("Понимаю: тестовое расписание не утверждено мечетью").assertDoesNotExist()
        compose.onNodeWithTag("import-confirmation").assertIsNotEnabled().assertIsOff()
        key(Key.DirectionDown); key(Key.Enter); key(Key.DirectionDown); key(Key.Enter)
        compose.onNodeWithTag("import-apply").assertIsNotEnabled()
        assertEquals(0, appliedCount)
    }

}
