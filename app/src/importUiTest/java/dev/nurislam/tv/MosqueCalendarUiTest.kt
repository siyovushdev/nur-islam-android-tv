package dev.nurislam.tv

import android.net.Uri
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nurislam.tv.calendar.*
import dev.nurislam.tv.data.*
import dev.nurislam.tv.settings.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.*

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk=[26,35])
class MosqueCalendarUiTest {
    val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule val lifecycle:RuleChain=RuleChain.outerRule(object:ExternalResource(){override fun before(){val app=ApplicationProvider.getApplicationContext<android.app.Application>();shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")}}).around(compose)
    private val app get()=compose.activity.application as NurIslamApplication
    private val vm get()=androidx.lifecycle.ViewModelProvider(compose.activity)[BoardViewModel::class.java]
    private val today get()=Instant.now().atZone(ZoneId.of("Asia/Yekaterinburg")).toLocalDate()
    private fun waitIo(){compose.waitUntil(15_000){shadowOf(android.os.Looper.getMainLooper()).idle();!vm.busy.value};compose.waitForIdle()}
    private fun open(){compose.onNodeWithTag("board-settings").performClick()}
    private fun tap(s:String){compose.onNodeWithTag("admin-list").performScrollToNode(hasText(s));compose.onNodeWithText(s).performClick();waitIo()}
    private fun import(text:String){val file=File(app.cacheDir,"calendar.json");file.writeText(text);compose.runOnIdle{vm.import(Uri.fromFile(file))};waitIo()}
    private fun calendar(revision:String="one",sections:Boolean=true)= """{
      "type":"mosqueCalendar","schemaVersion":1,"mosque":{"id":"mosque-a","name":"Мечеть А","cityId":"test-mosque","city":"Екатеринбург","timezone":"Asia/Yekaterinburg"},
      "validFrom":"${today.minusYears(1)}","validUntil":"${today.plusYears(1)}","source":"ТЕСТ календаря","revision":"$revision"
      ${if(sections) """, "iqamah":{"rules":[{"prayer":"ASR","from":"${today.withDayOfYear(1)}","mode":"OFFSET","minutes":10}]},
      "ramadan":[{"firstFast":"${today.plusDays(5)}","eid":"${today.plusDays(34)}"}],
      "events":[{"id":"meeting","title":"Встреча сегодня","from":"$today"}]""" else ""} }"""
    private fun loadSchedule(){compose.waitUntil(10_000){app.schedules.state.value.loaded&&app.mosqueCalendar.state.value.loaded};runBlocking{app.schedules.apply(ScheduleJson.parse(ScheduleFixtures.json(today.year,meaning="PRAYER_START").apply{addProperty("timezone","Asia/Yekaterinburg")}.toString().toByteArray()))}}
    @Test fun calendarBeforeScheduleUsesItsOwnCityAndKeepsPrayerTimesUnavailable() {
        compose.waitUntil(10_000) { app.schedules.state.value.loaded && app.mosqueCalendar.state.value.loaded }
        open(); import(calendar()); compose.onNodeWithTag("calendar-apply").performClick(); waitIo()
        compose.onNodeWithText("Вернуться к табло").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("ramadan-status").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Екатеринбург").assertIsDisplayed()
        compose.onNodeWithText("Мечеть А").assertIsDisplayed()
        compose.onAllNodesWithText("Казань").assertCountEquals(0)
        assertNull(app.schedules.state.value.current)
        compose.onNodeWithTag("load-schedule").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Екатеринбург").assertIsDisplayed()
    }

    @Test fun importCalendarOneApplyDisplaysRamadanEventAndSurvivesRecreateWithoutChangingScheduleOrSettings(){
        loadSchedule();val before=app.schedules.state.value.current!!.json;val display=app.settings.state.value
        open();import(calendar());compose.onNodeWithTag("calendar-preview").assertIsDisplayed();assertNull(app.mosqueCalendar.state.value.current)
        compose.onNodeWithTag("calendar-apply").assertIsEnabled();compose.onRoot().performKeyInput{pressKey(Key.Enter)};waitIo()
        assertEquals("one",app.mosqueCalendar.state.value.current!!.revision);assertEquals(before,app.schedules.state.value.current!!.json);assertEquals(display,app.settings.state.value)
        compose.onNodeWithText("Вернуться к табло").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("ramadan-status").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("ramadan-status").assert(hasAnyDescendant(hasText("Встреча сегодня",substring=true)))
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("ramadan-status").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("ramadan-status").assertExists();assertEquals(before,app.schedules.state.value.current!!.json)
        val restored=MosqueCalendarRepository(AtomicScheduleStorage(File(app.filesDir,"mosque-calendar-store.json")));runBlocking{restored.load()};assertEquals(calendar().let{MosqueCalendarJson.parse(it.toByteArray()).json},restored.state.value.current!!.json)
    }
    @Test fun omissionReplacesWholeCalendarRollbackRestoresAndScheduleImportIsIndependent(){
        loadSchedule();open();import(calendar());compose.onNodeWithTag("calendar-apply").performClick();waitIo();compose.onNodeWithText("Вернуться к табло").performClick()
        open();import(calendar("two",false));compose.onNodeWithText("Рамадан: не настроен",substring=true).assertExists();compose.onNodeWithTag("calendar-apply").performClick();waitIo()
        assertTrue(app.mosqueCalendar.state.value.current!!.events.isEmpty());assertFalse(app.mosqueCalendar.state.value.current!!.iqamah.enabled)
        compose.onNodeWithText("Вернуться к табло").performClick();open();tap("Загруженные данные");tap("Вернуть предыдущий календарь мечети")
        assertEquals("one",app.mosqueCalendar.state.value.current!!.revision)
        val before=app.mosqueCalendar.state.value.current!!.json
        runBlocking{app.schedules.apply(ScheduleFixtures.schedule(today.year,revision="other-place"))};assertEquals(before,app.mosqueCalendar.state.value.current!!.json)
        assertNotNull(app.mosqueCalendar.state.value.current!!.compatibility(app.schedules.state.value))
        compose.onNodeWithText("Вернуться к табло").performClick();compose.onNodeWithTag("ramadan-status").assertDoesNotExist()
    }
    @Test fun cancelMalformedAndConflictKeepPreviousCalendar(){
        loadSchedule();runBlocking{app.mosqueCalendar.apply(MosqueCalendarJson.parse(calendar().toByteArray()))};val before=app.mosqueCalendar.state.value.current!!.json
        open();import(calendar("two",false));compose.onNodeWithTag("calendar-cancel").performClick();assertEquals(before,app.mosqueCalendar.state.value.current!!.json)
        import("{broken}");assertNotNull(vm.message.value);assertEquals(before,app.mosqueCalendar.state.value.current!!.json);compose.runOnIdle{vm.dismissMessage()}
        import(calendar("bad").replace("\"prayer\":\"ASR\"","\"prayer\":\"JUMUAH\"").replace("\"mode\":\"OFFSET\",\"minutes\":10","\"mode\":\"FIXED\",\"time\":\"15:27\""))
        compose.onNodeWithTag("calendar-apply").performClick();waitIo();assertTrue(vm.message.value!!.contains("Аср"));assertEquals(before,app.mosqueCalendar.state.value.current!!.json)
    }
    @Test fun legacyScheduleAndNewCalendarCanBeLoadedThroughEitherEntryWithoutAutoApply(){
        loadSchedule();open();tap("Загрузить расписание")
        val request=shadowOf(compose.activity).nextStartedActivityForResult
        val file=File(app.cacheDir,"pending-mosque.json").apply{writeText(calendar())}
        compose.activityRule.scenario.recreate()
        compose.runOnIdle{assertTrue(compose.activity.activityResultRegistry.dispatchResult(request.requestCode,android.app.Activity.RESULT_OK,android.content.Intent().setData(Uri.fromFile(file))))}
        compose.waitUntil(15_000){shadowOf(android.os.Looper.getMainLooper()).idle();vm.calendarCandidate.value!=null&&!vm.busy.value}
        compose.onNodeWithTag("calendar-preview").assertIsDisplayed();assertNull(app.mosqueCalendar.state.value.current)
    }
    @Test fun legacyManualDataBindToOriginalPlaceAndNeverFollowAnotherMosque() {
        loadSchedule()
        val config=dev.nurislam.tv.iqamah.IqamahConfig(enabled=true,approved=true,source="Контрольная сохранённая настройка",rules=listOf(dev.nurislam.tv.iqamah.IqamahRule(dev.nurislam.tv.iqamah.IqamahPrayer.ASR,today,mode=dev.nurislam.tv.iqamah.IqamahMode.FIXED,value=16*60)))
        runBlocking{app.iqamah.save(config)}
        // A new VM simulates the first start after upgrading a stored 0.3 configuration.
        compose.runOnIdle { compose.activity.viewModelStore.clear() }
        compose.activityRule.scenario.recreate();compose.waitUntil(10_000){vm.legacyReady.value}
        assertTrue(vm.legacyMatches(app.schedules.state.value))
        val other=ScheduleJson.parse(ScheduleFixtures.json(today.year).apply{addProperty("locationId","other-city")}.toString().toByteArray())
        runBlocking{app.schedules.apply(other)}
        assertFalse(vm.legacyMatches(app.schedules.state.value));assertEquals(config,app.iqamah.state.value.config)
        runBlocking{app.schedules.rollback()};assertTrue(vm.legacyMatches(app.schedules.state.value))
    }

}
