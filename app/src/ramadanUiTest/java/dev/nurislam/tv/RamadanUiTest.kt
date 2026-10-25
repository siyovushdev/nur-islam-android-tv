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
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nurislam.tv.data.*
import dev.nurislam.tv.domain.*
import dev.nurislam.tv.iqamah.*
import dev.nurislam.tv.model.*
import dev.nurislam.tv.ramadan.*
import dev.nurislam.tv.settings.*
import dev.nurislam.tv.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 35], qualifiers = "w411dp-h891dp-xhdpi")
class RamadanUiTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() { val app = ApplicationProvider.getApplicationContext<android.app.Application>(); shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION") }
    }).around(compose)
    private var saved: RamadanConfig? = null
    private var closed = false
    private val state = mutableStateOf(RamadanState(loaded = true))
    private val first = Instant.now().atZone(MoscowZone).toLocalDate().plusDays(5)
    private fun show(touch: Boolean = true, config: RamadanConfig = RamadanConfig()) {
        state.value = RamadanState(config, loaded = true)
        compose.activity.setContent { NurIslamTheme { RamadanPanel(state.value, false, null, touch, false, {},
            { saved = it; state.value = RamadanState(it, config, true) }, {}, {}, { closed = true }) } }
    }
    private fun tap(text: String) { compose.onNodeWithTag("ramadan-list").performScrollToNode(hasText(text)); compose.onNodeWithText(text).performClick() }
    private fun key(k: Key, n: Int = 1) { repeat(n) { compose.onRoot().performKeyInput { pressKey(k) } } }
    private fun number(digits: String) { compose.onNodeWithTag("ramadan-number").performTextReplacement(digits); compose.onNodeWithText("Применить ввод").performClick() }
    private fun digits(d: LocalDate) = d.toString().replace("-", "")
    private fun remoteNumber(value: String) {
        var selected = 11
        key(Key.DirectionRight, 11); key(Key.Enter) // Clear, no physical keyboard/IME.
        value.forEach { ch -> val digit = ch.digitToInt(); key(Key.DirectionRight, (digit - selected + 14) % 14); key(Key.Enter); selected = digit }
        key(Key.DirectionRight, (12 - selected + 14) % 14); key(Key.Enter)
    }
    private fun image(name: String) {
        if (android.os.Build.VERSION.SDK_INT != 35) return
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val b = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); view.draw(Canvas(b))
            val f = File(System.getProperty("nurIslam.screenshotDir"), "ramadan-$name-${BuildConfig.BUILD_TYPE}.png")
            f.parentFile!!.mkdirs(); f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun touchCreatesConfirmsSavesAndReturnsWithoutInventedDates() {
        show()
        compose.onNodeWithText("Блок выключен").assertExists()
        tap("Показывать блок Рамадана"); tap("Добавить даты Рамадана")
        tap("Первый день поста: не задан"); number(digits(first))
        tap("Ураза-байрам: не задан"); number(digits(first.plusDays(29)))
        tap("Источник: не указан")
        compose.onNodeWithTag("label-value").performTextReplacement("Контрольный протокол для UI-проверки")
        compose.onNodeWithText("Сохранить").performClick()
        compose.onNodeWithText("Даты утверждены мечетью").performScrollTo().assertIsOff()
        tap("Даты утверждены мечетью"); tap("Добавить даты в черновик")
        compose.onNodeWithText("До первого дня поста Рамадан:", substring = true).assertExists()
        compose.onNodeWithTag("ramadan-save").assertIsDisplayed().performClick()
        assertNull(saved); image("confirm")
        tap("Подтвердить и сохранить даты")
        assertTrue(saved!!.enabled); assertTrue(saved!!.dates.single().approved)
        assertEquals(first, saved!!.dates.single().firstFast)
        tap("Вернуться к табло"); assertTrue(closed)
    }
    @Test fun dpadEditsBothDatesConfirmsAndSavesWithoutKeyboard() {
        val d = RamadanDates(first.year, first, first.plusDays(29), "Контрольный протокол UI", false)
        show(false, RamadanConfig(false, listOf(d)))
        key(Key.Enter); key(Key.DirectionDown, 3); key(Key.Enter)
        key(Key.Enter); remoteNumber(digits(first.plusDays(1)))
        key(Key.DirectionDown); key(Key.Enter); remoteNumber(digits(first.plusDays(30)))
        key(Key.DirectionDown, 3); key(Key.Enter) // Source approval checkbox.
        compose.onNodeWithText("Даты утверждены мечетью").assertIsOn()
        key(Key.DirectionDown, 2); key(Key.Enter) // Add date draft.
        key(Key.DirectionUp); key(Key.Enter) // Pinned Save.
        key(Key.DirectionDown); key(Key.Enter)
        assertEquals(first.plusDays(1), saved!!.dates.single().firstFast)
        assertTrue(saved!!.dates.single().approved)
        key(Key.Enter); assertTrue(closed)
    }
    @Test fun testSourceInvalidDatesCancelAndErrorsKeepControlsReachable() {
        show(); tap("Добавить даты Рамадана")
        tap("Первый день поста: не задан"); number("20260229")
        tap("Источник: не указан"); compose.onNodeWithTag("label-value").performTextReplacement("ТЕСТ ДЛЯ ТЕЛЕФОНА")
        compose.onNodeWithText("Сохранить").performClick()
        compose.onNodeWithText("Даты утверждены мечетью").performScrollTo().assertIsNotEnabled()
        tap("Добавить даты в черновик")
        compose.onNodeWithText("Понятно").assertIsDisplayed().assert(SemanticsMatcher.expectValue(ImportActionHighlighted, true))
        key(Key.Enter)
        compose.onNodeWithText("Добавить даты в черновик").assertExists()
        compose.onNodeWithText("Назад / отмена").performClick()
        compose.onNodeWithText("Назад / отмена").performClick(); assertTrue(closed); assertNull(saved)
    }
    @Test fun actualActivityPersistsCounterAndHomeDiscardsModalState() {
        val app = compose.activity.application as NurIslamApplication
        compose.waitUntil(10_000) { app.ramadan.state.value.loaded }
        val d = RamadanDates(first.year, first, first.plusDays(29), "Контрольный протокол UI", true)
        kotlinx.coroutines.runBlocking { app.ramadan.save(RamadanConfig(false, listOf(d))) }
        if (!BuildConfig.PHONE_PREVIEW) compose.onNodeWithText("Пропустить и открыть табло").performClick()
        fun settings() {
            if (BuildConfig.PHONE_PREVIEW) compose.onNodeWithTag("phone-settings").performClick()
            else { key(Key.DirectionCenter, 5); key(Key.DirectionDown); key(Key.Enter) }
        }
        fun openRamadan() { settings(); compose.onNodeWithTag("admin-list").performScrollToNode(hasText("Рамадан")); compose.onNodeWithText("Рамадан").performClick() }
        openRamadan(); key(Key.Enter); key(Key.DirectionUp); key(Key.Enter); key(Key.DirectionDown); key(Key.Enter)
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[BoardViewModel::class.java]
        compose.waitUntil(10_000) { shadowOf(android.os.Looper.getMainLooper()).idle(); !vm.busy.value }
        assertTrue(app.ramadan.state.value.config.enabled)
        tap("Вернуться к табло")
        compose.onNodeWithTag("ramadan-status").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("ramadan-status").assertExists()
        val restored = RamadanRepository(AtomicScheduleStorage(File(app.filesDir, "ramadan-store.json")))
        kotlinx.coroutines.runBlocking { restored.load() }
        assertTrue(restored.state.value.config.enabled)
        assertEquals(d, restored.state.value.config.dates.single())
        if (!BuildConfig.PHONE_PREVIEW) {
            openRamadan(); tap("Добавить даты Рамадана")
            compose.runOnIdle { androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(compose.activity, android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)) }
            compose.onNodeWithTag("ramadan-panel").assertDoesNotExist()
            settings(); compose.onNodeWithTag("admin-panel").assertExists()
        }
    }
    @Test @Config(sdk = [35], qualifiers = "w960dp-h540dp-xhdpi")
    fun bothThemesAllRotationsScalesAndLanguagesKeepPrayerRowsAndIqamahUnchanged() = boardMatrix(listOf(DisplayMode.LANDSCAPE, DisplayMode.PORTRAIT_ROTATE_CW, DisplayMode.PORTRAIT_ROTATE_CCW))
    @Test @Config(sdk = [35], qualifiers = "w540dp-h960dp-xhdpi")
    fun bothThemesNativePortraitScalesAndLanguagesKeepPrayerRowsAndIqamahUnchanged() = boardMatrix(listOf(DisplayMode.PORTRAIT_NATIVE))
    private fun boardMatrix(displays: List<DisplayMode>) {
        var options by mutableStateOf(AppSettings())
        var mode by mutableStateOf(displays.first())
        var visible by mutableStateOf(false)
        val day = LocalDate.of(2026, 2, 15)
        val now = mutableStateOf(day.atTime(12, 6).atZone(MoscowZone).toInstant())
        // Fabricated calendar exercises approved-source UI, never shipped as runtime data.
        val schedule = ScheduleJson.parse(ScheduleFixtures.json(meaning = "PRAYER_START").apply { addProperty("source", "Контрольный протокол UI") }.toString().toByteArray())
        val schedules = ScheduleState(schedule, loaded = true)
        val config = RamadanConfig(true, listOf(RamadanDates(2026, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 2), "Контрольный протокол UI", true)))
        val iqa = IqamahConfig(true, true, "Контрольный протокол UI", rules = listOf(IqamahRule(IqamahPrayer.DHUHR, day, mode = IqamahMode.FIXED, value = 730, baseCalendar = calendarFingerprint(schedule.json))))
        compose.activity.setContent { NurIslamTheme(options.theme) { CompositionLocalProvider(LocalBoardSettings provides options) {
            Box(Modifier.requiredSize(if (mode == DisplayMode.PORTRAIT_NATIVE) 540.dp else 960.dp, if (mode == DisplayMode.PORTRAIT_NATIVE) 960.dp else 540.dp)) {
                RotatedBoard(mode) { PrayerBoardScreen(now.value, now, schedule.day(day), (prayerStatus(now.value, schedules) as? PrayerStatus.Upcoming)?.prayer,
                    mode == DisplayMode.LANDSCAPE, false, notice = "Контрольный пример UI", iqamah = iqamahStatus(now.value, iqa, schedules, false),
                    ramadan = if (visible) ramadanText(ramadanStatus(now.value, config), options.language) else null) }
            }
        } } }
        for (theme in BoardThemeId.entries) for (display in displays) for (scale in listOf(.85f, 1f, 1.15f)) for (language in BoardLanguage.entries) {
            compose.runOnIdle { options = options.copy(theme = theme, textScale = scale, language = language); mode = display; visible = false }
            val before = compose.onNodeWithTag("row-ASR").fetchSemanticsNode().boundsInRoot
            compose.runOnIdle { visible = true }
            assertEquals(before, compose.onNodeWithTag("row-ASR").fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithTag("ramadan-status").assertExists()
            compose.onNodeWithTag("iqamah-time").assertExists()
            ScheduleEvent.entries.forEach { compose.onNodeWithTag("row-${it.name}").assertExists() }
            val action = androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult
            val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(action))
            repeat(texts.fetchSemanticsNodes().size) { i ->
                val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>(); texts[i].performSemanticsAction(action) { it(results) }
                results.forEach { assertFalse("Clipped $theme/$display/$scale/$language: ${it.layoutInput.text}", it.hasVisualOverflow) }
            }
            if (scale == 1f && language == BoardLanguage.BOTH) image("board-$theme-$display")
        }
    }
}
