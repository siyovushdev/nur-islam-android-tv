package dev.nurislam.tv

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nurislam.tv.data.*
import dev.nurislam.tv.model.ScheduleEvent
import dev.nurislam.tv.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 35])
class PhoneImportReviewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var applications = 0
    private var cancellations = 0
    private val busy = mutableStateOf(false)
    private fun candidate(approved: Boolean = true, test: Boolean = false): ValidatedSchedule {
        // Fabricated UI fixtures only; source labels exercise the approval states, not authenticity.
        val json = ScheduleFixtures.json(approved = approved).apply {
            addProperty("source", if (test) "ТЕСТ: конвертация Excel, не официальный календарь" else "Календарь мечети, протокол 42")
        }
        return ScheduleJson.parse(json.toString().toByteArray())
    }
    private fun show(test: Boolean = false, approved: Boolean = true) {
        val value = candidate(approved, test)
        compose.activity.setContent {
            NurIslamTheme { ScheduleImportPreview(value, ScheduleState(), busy.value, true,
                onApply = { applications++ }, onCancel = { cancellations++ }) }
        }
    }
    private fun image(name: String) {
        val file = File(System.getProperty("nurIslam.screenshotDir"), "$name-api${android.os.Build.VERSION.SDK_INT}.png")
        file.parentFile!!.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun key(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }
    @Test fun touchCheckboxControlsPrimaryActionAndTableHasNoClickActions() {
        show()
        compose.onNodeWithText("Проверка расписания").assertIsDisplayed()
        compose.onNodeWithTag("import-confirmation").assertIsOff()
        compose.onNodeWithTag("import-apply").assertIsNotEnabled().performClick()
        assertEquals(0, applications)
        image("import-after-unconfirmed")
        for (event in ScheduleEvent.entries) {
            compose.onNodeWithTag("import-row-${event.name}").performScrollTo().assertHasNoClickAction()
        }
        compose.onNodeWithTag("import-apply").assertIsDisplayed()
        compose.onNodeWithTag("import-cancel").assertIsDisplayed()
        compose.onNodeWithTag("import-confirmation").performClick().assertIsOn()
        compose.onNodeWithTag("import-apply").assertIsEnabled().performClick()
        assertEquals(1, applications)
        image("import-after-confirmed")
        compose.onNodeWithTag("import-confirmation").performClick().assertIsOff()
        compose.onNodeWithTag("import-apply").assertIsNotEnabled()
    }
    @Test fun unapprovedAndExplicitExcelTestSourcesStayBlockedEvenWithApprovedTrue() {
        assertFalse(isTestScheduleSource("Latest calendar, протокол 42"))
        assertTrue(isTestScheduleSource("ТЕСТОВОЕ расписание из Excel"))
        show(test = true)
        compose.onNodeWithTag("import-source-warning").assertTextContains("ТЕСТОВЫЙ", substring = true)
        compose.onNodeWithTag("import-confirmation").assertIsNotEnabled().assertIsOff()
        compose.onNodeWithTag("import-apply").assertIsNotEnabled().performClick()
        key(Key.DirectionDown); key(Key.Enter); key(Key.DirectionDown); key(Key.Enter)
        assertEquals(0, applications)
        image("import-after-test-source")
    }
    @Test fun unapprovedFileCannotBeConfirmed() {
        show(approved = false)
        compose.onNodeWithTag("import-confirmation").assertIsNotEnabled()
        compose.onNodeWithTag("import-apply").assertIsNotEnabled()
    }
    @Test fun dpadFocusConfirmationApplyAndCancel() {
        show()
        compose.onNodeWithTag("import-cancel").assertIsSelected()
        key(Key.DirectionDown)
        compose.onNodeWithTag("import-confirmation").assertIsSelected()
        key(Key.Enter)
        compose.onNodeWithTag("import-confirmation").assertIsOn()
        key(Key.DirectionDown)
        compose.onNodeWithTag("import-apply").assertIsSelected()
        key(Key.Enter)
        assertEquals(1, applications)
        key(Key.DirectionDown); key(Key.Enter)
        assertEquals(1, cancellations)
    }
    @Test fun busyCommitDisablesControlsAndCancelBeforeCommitIsExplicit() {
        show()
        compose.onNodeWithTag("import-cancel").performClick()
        assertEquals(1, cancellations)
        compose.onNodeWithTag("import-confirmation").performClick()
        compose.runOnIdle { busy.value = true }
        compose.onNodeWithTag("import-apply").assertIsNotEnabled()
        compose.onNodeWithTag("import-cancel").assertIsNotEnabled()
        key(Key.Enter)
        assertEquals(1, cancellations)
        assertEquals(0, applications)
    }
    @Test @Config(sdk = [35], qualifiers = "w800dp-h360dp-xhdpi")
    fun landscapeActionsRemainVisibleAfterScrollingLongSource() {
        val value = ScheduleFixtures.json().apply { addProperty("source", "Календарь мечети — ".repeat(10)) }
            .let { ScheduleJson.parse(it.toString().toByteArray()) }
        compose.activity.setContent { NurIslamTheme { ScheduleImportPreview(value, ScheduleState(), false, true, {}, {}) } }
        compose.onNodeWithTag("import-row-ISHA").performScrollTo()
        compose.onNodeWithTag("import-confirmation").assertIsDisplayed()
        compose.onNodeWithTag("import-apply").assertIsDisplayed()
        compose.onNodeWithTag("import-cancel").assertIsDisplayed()
        image("import-after-landscape")
    }
    @Test fun successHasExplicitReturnToBoardAndDpad() {
        compose.activity.setContent { NurIslamTheme { ScheduleImportResult(candidate(), true, { applications++ }, { cancellations++ }) } }
        compose.onNodeWithText("Расписание применено").assertIsDisplayed()
        image("import-success")
        key(Key.Enter)
        assertEquals(1, applications)
        key(Key.DirectionDown); key(Key.Enter)
        assertEquals(1, cancellations)
        compose.onNodeWithText("Вернуться к табло").performClick()
        assertEquals(2, applications)
    }
}
