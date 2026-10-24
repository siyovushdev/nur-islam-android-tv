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
import dev.nurislam.tv.domain.*
import dev.nurislam.tv.iqamah.*
import dev.nurislam.tv.model.MoscowZone
import dev.nurislam.tv.settings.*
import dev.nurislam.tv.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.*

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 35], qualifiers = "w411dp-h891dp-xhdpi")
class IqamahUiTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: org.junit.rules.RuleChain = org.junit.rules.RuleChain.outerRule(object : org.junit.rules.ExternalResource() {
        override fun before() {
            val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<NurIslamApplication>()
            org.robolectric.Shadows.shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        }
    }).around(compose)
    private val date = LocalDate.of(2026, 10, 24)
    private val calendar = ScheduleFixtures.schedule(meaning = "PRAYER_START")
    private val schedules = ScheduleState(calendar, loaded = true)
    private val state = mutableStateOf(IqamahState(loaded = true))
    private var saved: IqamahConfig? = null
    private var closed = 0
    private fun show(initial: IqamahConfig = IqamahConfig(), touch: Boolean = true, knownFajr: Boolean = true) {
        state.value = IqamahState(initial, loaded = true)
        val c = if (knownFajr) schedules else ScheduleState(ScheduleFixtures.schedule(), loaded = true)
        compose.activity.setContent { NurIslamTheme {
            IqamahPanel(state.value, c, false, null, touch, false, {}, { config -> saved = config; state.value = IqamahState(config, initial, true) }, {}, {}, { closed++ })
        } }
    }
    private fun tap(text: String) {
        compose.onNodeWithTag("iqamah-list").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
    }
    private fun key(k: Key, n: Int = 1) { repeat(n) { compose.onRoot().performKeyInput { pressKey(k) } } }
    private fun number(digits: String) {
        compose.onNodeWithTag("iqamah-number").performTextReplacement(digits)
        compose.onNodeWithText("Применить ввод").performClick()
    }
    private fun image(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File(System.getProperty("nurIslam.screenshotDir"), "iqamah-$name-${BuildConfig.BUILD_TYPE}-api${android.os.Build.VERSION.SDK_INT}.png")
            file.parentFile!!.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun touchFullFixedRuleSourceConfirmationSaveAndReturn() {
        show()
        tap("Функция икамата")
        tap("Источник правил: не указан")
        compose.onNodeWithTag("label-value").performTextReplacement("ТЕСТ ДЛЯ ТЕЛЕФОНА")
        compose.onNodeWithText("Сохранить").performClick()
        tap("Аср")
        image("rule-off")
        tap("Режим: Отключено")
        tap("Значение: не задано"); number("1630")
        tap("Дата: ${Instant.now().atZone(MoscowZone).toLocalDate()}"); number("20261024")
        tap("Добавить в черновик")
        compose.onNodeWithTag("iqamah-save").assertIsDisplayed().performClick()
        assertNull(saved)
        image("confirm")
        tap("Подтвердить сохранение")
        assertEquals(990, saved!!.rule(IqamahPrayer.ASR, date)!!.value)
        assertFalse(saved!!.approved)
        compose.onNodeWithText("Правила сохранены").assertIsDisplayed()
        tap("Вернуться к табло"); assertEquals(1, closed)
    }
    @Test fun invalidNumberCancelAndDiscardDoNotSave() {
        show(); tap("Фаджр"); tap("Режим: Отключено"); tap("Значение: не задано"); number("2561")
        compose.onNodeWithText("Понятно").assertIsDisplayed().performClick()
        tap("Добавить в черновик")
        compose.onNodeWithText("Понятно").performClick()
        assertNull(saved)
        compose.onNodeWithText("Назад / отмена").performClick()
        tap("Функция икамата")
        compose.onNodeWithText("Назад / отмена").performClick()
        tap("Отменить изменения и выйти"); assertEquals(1, closed); assertNull(saved)
    }
    @Test fun unknownFajrAndJumuahNeverOfferRelativeModeOrFalseApproval() {
        show(knownFajr = false)
        tap("Фаджр"); tap("Режим: Отключено")
        compose.onNodeWithText("Базовое поле подтверждено как начало молитвы").performScrollTo().assertIsNotEnabled()
        tap("Режим: Точное время")
        compose.onNodeWithText("Режим: Отключено").assertIsDisplayed()
        compose.onNodeWithText("Назад / отмена").performClick()
        tap("Джума (пятница)"); tap("Режим: Отключено"); tap("Режим: Точное время")
        compose.onNodeWithText("Режим: Отключено").assertIsDisplayed()
    }
    @Test fun dpadEntersIntervalAndConfirmsBaseWithoutKeyboard() {
        show(touch = false)
        // Main: five global settings, then Fajr, Dhuhr, Asr.
        key(Key.DirectionDown, 7); key(Key.Enter)
        key(Key.DirectionDown, 2); key(Key.Enter); key(Key.Enter) // OFFSET, no invented value.
        key(Key.DirectionDown); key(Key.Enter)
        // Keypad starts at 0. Enter 12 explicitly using only the remote.
        key(Key.DirectionRight); key(Key.Enter); key(Key.DirectionRight); key(Key.Enter)
        key(Key.DirectionDown, 3); key(Key.DirectionRight); key(Key.Enter) // Done (index12).
        compose.onNodeWithText("Значение: 12").assertIsDisplayed()
        key(Key.DirectionDown, 4); key(Key.Enter) // Explicit base checkbox.
        compose.onNodeWithText("Базовое поле подтверждено как начало молитвы").assertIsOn()
        key(Key.DirectionDown); key(Key.Enter)
        assertNull(saved)
        // Footer after 14 main actions: back at14, save15. UP wraps to Save.
        key(Key.DirectionUp); key(Key.Enter)
        key(Key.DirectionDown); key(Key.Enter)
        assertEquals(12, saved!!.rules.single().value)
        assertEquals(calendarFingerprint(calendar.json), saved!!.rules.single().baseCalendar)
        image("dpad-saved")
    }
    @Test fun editingAndDeletingExceptionKeepsSeasonalRule() {
        val regular = IqamahRule(IqamahPrayer.ASR, date, mode = IqamahMode.FIXED, value = 900)
        val exception = regular.copy(exception = true, value = 930)
        show(IqamahConfig(rules = listOf(regular, exception)))
        tap("Даты изменений и исключения (2)")
        tap("Аср: 15:30")
        tap("Удалить запись из черновика")
        compose.onNodeWithTag("iqamah-save").performClick(); tap("Подтвердить сохранение")
        assertEquals(listOf(regular), saved!!.rules)
    }
    @Test fun editsClearApprovalAndTestSourceCannotBeApproved() {
        show(IqamahConfig(enabled = true, approved = true, source = "Протокол администрации 42"))
        compose.onNodeWithTag("iqamah-list").performScrollToNode(hasText("Правила утверждены мечетью"))
        compose.onNodeWithText("Правила утверждены мечетью").assertIsOn()
        tap("Функция икамата")
        compose.onNodeWithText("Правила утверждены мечетью").assertIsOff()
        tap("Источник правил: Протокол администрации 42")
        compose.onNodeWithTag("label-value").performTextReplacement("ТЕСТ ДЛЯ ТЕЛЕФОНА")
        compose.onNodeWithText("Сохранить").performClick()
        compose.onNodeWithText("Правила утверждены мечетью").performScrollTo().assertIsOff().assertIsNotEnabled()
        compose.onNodeWithTag("iqamah-save").performClick()
        tap("Вернуться к черновику")
        assertNull(saved)
    }
    @Test fun rollbackRequiresConfirmationAndUpdatesEditorToRestoredRules() {
        val previous = IqamahConfig(source = "TEST previous")
        val current = IqamahConfig(source = "TEST current")
        state.value = IqamahState(current, previous, true)
        var rollbacks = 0
        compose.activity.setContent { NurIslamTheme {
            IqamahPanel(state.value, schedules, false, null, true, false, {}, {}, {
                rollbacks++; state.value = IqamahState(previous, current, true)
            }, {}, {})
        } }
        tap("Откат сохранённых правил")
        assertEquals(0, rollbacks)
        compose.onNodeWithText("Назад / отмена").performClick()
        assertEquals(0, rollbacks)
        tap("Откат сохранённых правил"); tap("Подтвердить откат")
        assertEquals(1, rollbacks)
        tap("Продолжить настройку")
        compose.onNodeWithText("Источник правил: TEST previous").assertIsDisplayed()
        compose.onNodeWithTag("iqamah-save").assertDoesNotExist()
    }
    @Test fun delayedLoadAndStorageErrorKeepActualRulesAndEditableDraft() {
        val message = mutableStateOf<String?>(null)
        state.value = IqamahState()
        compose.activity.setContent { NurIslamTheme {
            IqamahPanel(state.value, schedules, false, message.value, true, false, {}, { message.value = "Ошибка записи: диск недоступен" }, {}, { message.value = null }, {})
        } }
        compose.onNodeWithText("Загрузка правил икамата…").assertIsDisplayed()
        compose.runOnIdle { state.value = IqamahState(IqamahConfig(source = "TEST persisted"), loaded = true) }
        compose.onNodeWithText("Источник правил: TEST persisted").assertIsDisplayed()
        tap("Функция икамата")
        compose.onNodeWithTag("iqamah-save").performClick(); tap("Подтвердить сохранение")
        compose.onNodeWithText("Понятно").assertIsDisplayed().performClick()
        tap("Вернуться к черновику")
        compose.onNodeWithText("Функция икамата").assertIsOn()
        compose.onNodeWithTag("iqamah-save").assertIsEnabled()
        assertFalse(state.value.config.enabled)
    }
    @Test @Config(sdk = [35], qualifiers = "w800dp-h360dp-xhdpi")
    fun longSourceAndLandscapeKeepSaveAndCancelVisible() {
        show(IqamahConfig(source = "Протокол ".repeat(20)))
        tap("Функция икамата")
        compose.onNodeWithTag("iqamah-list").performScrollToNode(hasText("Даты изменений и исключения (0)"))
        compose.onNodeWithTag("iqamah-save").assertIsDisplayed()
        compose.onNodeWithText("Назад / отмена").assertIsDisplayed()
        image("editor-landscape")
    }
    @Test @Config(sdk = [35], qualifiers = "w960dp-h540dp-xhdpi")
    fun countdownArrivalAndDimFitBothThemesAllModesAndScales() {
        val target = date.atTime(15, 27).atZone(MoscowZone).toInstant()
        val settings = mutableStateOf(AppSettings())
        val status = mutableStateOf<IqamahStatus>(IqamahStatus.Waiting(IqamahEvent(IqamahPrayer.ASR, target, target.minusSeconds(720), IqamahRule(IqamahPrayer.ASR, date, mode = IqamahMode.OFFSET, value = 12, baseCalendar = calendarFingerprint(calendar.json)), true)))
        val clock = mutableStateOf(target.minusSeconds(70))
        compose.activity.setContent { NurIslamTheme(settings.value.theme) { CompositionLocalProvider(LocalBoardSettings provides settings.value) {
            RotatedBoard(settings.value.displayMode, Modifier.fillMaxSize()) {
                PrayerBoardScreen(clock.value, clock, calendar.day(date), null, settings.value.displayMode == DisplayMode.LANDSCAPE, false, iqamah = status.value)
            }
        } } }
        for (theme in BoardThemeId.entries) for (mode in listOf(DisplayMode.LANDSCAPE, DisplayMode.PORTRAIT_NATIVE, DisplayMode.PORTRAIT_ROTATE_CW, DisplayMode.PORTRAIT_ROTATE_CCW)) for (scale in listOf(.85f, 1f, 1.15f)) {
            compose.runOnIdle { settings.value = AppSettings(theme = theme, displayMode = mode, textScale = scale) }
            compose.onNodeWithTag("iqamah-time").assertIsDisplayed().assert(hasAnyDescendant(hasText("15:27")))
            compose.onNodeWithTag("countdown").assertIsDisplayed().assert(hasAnyDescendant(hasText("00:01:10")))
            compose.onNodeWithTag("row-ISHA").assertIsDisplayed()
            if (scale == 1f) image("${theme.name.lowercase()}-${mode.name.lowercase()}-waiting")
        }
        val event = (status.value as IqamahStatus.Waiting).event
        compose.runOnIdle { clock.value = target; status.value = IqamahStatus.Now(event, target.plusSeconds(60), false) }
        compose.onNodeWithTag("iqamah-arrival").assertIsDisplayed()
        image("arrival")
        compose.runOnIdle { status.value = IqamahStatus.Now(event, target.plusSeconds(300), true) }
        compose.onNodeWithTag("iqamah-dim").assertIsDisplayed()
        image("optional-dim")
    }
}
