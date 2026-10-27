package dev.nurislam.tv

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nurislam.tv.data.*
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
    private var applied=0; private var cancelled=0
    private val busy=mutableStateOf(false)
    private fun show(source: String="Календарь из файла", approved: Boolean=false) {
        val candidate=ScheduleJson.parse(ScheduleFixtures.json(approved=approved).apply { addProperty("source",source) }.toString().toByteArray())
        compose.activity.setContent { NurIslamTheme { ScheduleImportPreview(candidate,ScheduleState(),busy.value,BuildConfig.PHONE_PREVIEW,{ applied++ },{ cancelled++ }) } }
    }
    private fun key(k: Key)=compose.onRoot().performKeyInput { pressKey(k) }
    private fun image(name: String) {
        if(android.os.Build.VERSION.SDK_INT!=35)return
        compose.runOnIdle {
            val v=compose.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b))
            val f=File(System.getProperty("nurIslam.screenshotDir"),"$name-${BuildConfig.BUILD_TYPE}.png");f.parentFile!!.mkdirs();f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
    @Test fun validFileNeedsOnlyApplyAndNoApprovalControl() {
        show();compose.onNodeWithTag("import-apply").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("import-confirmation").assertDoesNotExist()
        image("simple-import-summary")
        compose.onNodeWithTag("import-apply").performClick();assertEquals(1,applied)
    }
    @Test fun detailsAreInformationAndActionsRemainVisible() {
        show("Источник ".repeat(20));compose.onNodeWithTag("import-details").performClick()
        compose.onNodeWithTag("import-information").performTouchInput { swipeUp() }
        compose.onNodeWithTag("import-apply").assertIsDisplayed();compose.onNodeWithTag("import-cancel").assertIsDisplayed()
        compose.onNodeWithText("Времена на",substring=true).assertHasNoClickAction()
        image("simple-import-details")
    }
    @Test fun dpadStartsOnApplyAndCanCancelWithoutPublishing() {
        show();compose.onNodeWithTag("import-apply").assert(SemanticsMatcher.expectValue(ImportActionHighlighted,true))
        key(Key.DirectionUp);key(Key.Enter);assertEquals(1,cancelled);assertEquals(0,applied)
    }
    @Test fun dpadAppliesExactlyOnceAndBusyCannotApplyOrCancel() {
        show();key(Key.Enter);assertEquals(1,applied)
        compose.runOnIdle { busy.value=true };compose.onNodeWithTag("import-apply").assertIsNotEnabled()
        key(Key.Enter);key(Key.Back);assertEquals(1,applied);assertEquals(0,cancelled)
    }
    @Test fun demoImportIsMarkedAndBehavesTheSameOnTvAndPhone() {
        show("ТЕСТ ДЛЯ ТЕЛЕФОНА",approved=false)
        compose.onNodeWithTag("import-source-warning").assertIsDisplayed()
        compose.onNodeWithText("Подтверждаю: источник утверждён мечетью").assertDoesNotExist()
        key(Key.Enter);assertEquals(1,applied)
    }
    @Test @Config(sdk=[35],qualifiers="w800dp-h360dp-xhdpi")
    fun landscapeShortWindowKeepsApplyAndCancelVisible() {
        show();compose.onNodeWithTag("import-details").performClick()
        compose.onNodeWithTag("import-apply").assertIsDisplayed();compose.onNodeWithTag("import-cancel").assertIsDisplayed()
        image("simple-import-landscape")
    }
}
