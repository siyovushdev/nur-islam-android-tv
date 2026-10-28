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
    private fun SemanticsNodeInteraction.assertHighlighted(value: Boolean): SemanticsNodeInteraction =
        assert(SemanticsMatcher.expectValue(BoardRowHighlighted, value))
    private fun noTextOverflow() {
        val action = androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(action))
        repeat(nodes.fetchSemanticsNodes().size) { index ->
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            nodes[index].performSemanticsAction(action) { it(results) }
            results.forEach { assertFalse("Clipped text: ${it.layoutInput.text}", it.hasVisualOverflow) }
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
        compose.onNodeWithText("Назад").performClick()
        tap("Функция икамата")
        compose.onNodeWithText("Назад").performClick()
        tap("Отменить изменения и выйти"); assertEquals(1, closed); assertNull(saved)
    }
    @Test fun unknownFajrAndJumuahNeverOfferRelativeModeOrFalseApproval() {
        show(knownFajr = false)
        tap("Фаджр"); tap("Режим: Отключено")
        compose.onNodeWithText("Базовое поле подтверждено как начало молитвы").performScrollTo().assertIsNotEnabled()
        tap("Режим: Точное время")
        compose.onNodeWithText("Режим: Отключено").assertIsDisplayed()
        compose.onNodeWithText("Назад").performClick()
        tap("Джума (пятница)"); tap("Режим: Отключено"); tap("Режим: Точное время")
        compose.onNodeWithText("Режим: Отключено").assertIsDisplayed()
    }
    @Test fun dpadEntersIntervalAndConfirmsBaseWithoutKeyboard() {
        show(touch = false)
        key(Key.Enter) // Enable explicitly; no default intervals.
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
        // Use the rules just entered, not a hand-built status, through every board transition.
        val entered = saved!!
        val day = entered.rules.single().from
        val start = day.atTime(15, 15).atZone(MoscowZone).toInstant()
        val clock = mutableStateOf(start.minusSeconds(1))
        compose.activity.setContent { NurIslamTheme {
            val status = prayerStatus(clock.value, schedules)
            PrayerBoardScreen(clock.value, clock, calendar.day(day), (status as? PrayerStatus.Upcoming)?.prayer,
                false, false, status = status, iqamah = iqamahStatus(clock.value, entered, schedules, allowDemo = true))
        } }
        compose.onNodeWithText("До икамата").assertDoesNotExist()
        compose.runOnIdle { clock.value = start }
        compose.onNodeWithText("До икамата").assertIsDisplayed()
        compose.onNodeWithTag("row-ASR").assertHighlighted(true).assertHasNoClickAction()
            .assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected))
        compose.onNodeWithTag("row-MAGHRIB").assertHighlighted(false)
        compose.onNodeWithTag("countdown").assert(hasAnyDescendant(hasText("00:12:00")))
        compose.runOnIdle { clock.value = start.plusSeconds(719) }
        compose.onNodeWithTag("countdown").assert(hasAnyDescendant(hasText("00:00:01")))
        compose.runOnIdle { clock.value = start.plusSeconds(720) }
        compose.onNodeWithTag("iqamah-arrival").assertIsDisplayed()
        compose.onNodeWithTag("row-ASR").assertHighlighted(true)
        compose.runOnIdle { clock.value = start.plusSeconds(780) }
        compose.onNodeWithTag("iqamah-arrival").assertDoesNotExist()
        compose.onNodeWithText("До икамата").assertDoesNotExist()
        compose.onNodeWithTag("row-ASR").assertHighlighted(false)
        compose.onNodeWithTag("row-MAGHRIB").assertHighlighted(true)
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
        compose.onNodeWithText("Назад").performClick()
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
    @Test fun conflictingDraftCannotBeSavedByTouchOrRemoteButDisablingRemainsPossible() {
        val from = Instant.now().atZone(MoscowZone).toLocalDate()
        val rules = listOf(IqamahRule(IqamahPrayer.DHUHR, from, mode = IqamahMode.FIXED, value = 950), IqamahRule(IqamahPrayer.ASR, from, mode = IqamahMode.FIXED, value = 930))
        show(IqamahConfig(enabled = true, source = "TEST", rules = rules))
        tap("Затемнение: выключено")
        number("1") // Make the draft dirty without changing the conflicting times.
        compose.onNodeWithTag("iqamah-save").performClick()
        compose.onNodeWithText("Подтвердить сохранение").assertIsNotEnabled()
        key(Key.DirectionDown); key(Key.Enter)
        assertNull(saved)
        compose.onAllNodesWithText("Конфликт икаматов").onFirst().performScrollTo().assertIsDisplayed()
        image("conflict-save-blocked")
        tap("Вернуться к черновику")
        tap("Функция икамата")
        compose.onNodeWithTag("iqamah-save").performClick()
        compose.onNodeWithText("Подтвердить сохранение").assertIsEnabled().performClick()
        assertFalse(saved!!.enabled)
        assertEquals(listOf(950, 930), saved!!.rules.map { it.value })
    }
    @Test fun midnightArrivalUsesExplicitPreviousDayRowAndReturnsToToday() {
        val current = date.plusDays(1)
        val target = date.atTime(23, 59).atZone(MoscowZone).toInstant()
        val event = IqamahEvent(IqamahPrayer.ISHA, target, null, IqamahRule(IqamahPrayer.ISHA, date, mode = IqamahMode.FIXED, value = 1439), true)
        val status = mutableStateOf<IqamahStatus>(IqamahStatus.Now(event, target.plusSeconds(300), false))
        val now = mutableStateOf(target.plusSeconds(120))
        val next = NextPrayer(dev.nurislam.tv.model.ScheduleEvent.FAJR_IN_MOSQUE, current.atTime(5, 30).atZone(MoscowZone).toInstant())
        compose.activity.setContent { NurIslamTheme { PrayerBoardScreen(now.value, now, calendar.day(current), next, false, false, iqamah = status.value) } }
        compose.onNodeWithTag("row-ISHA-iqamah").assertHighlighted(true).assert(hasAnyDescendant(hasText(date.toString())))
        compose.onNodeWithTag("row-ISHA").assertHighlighted(false).assert(hasAnyDescendant(hasText("19:02")))
        compose.runOnIdle { status.value = IqamahStatus.Idle(null, "") }
        compose.onNodeWithTag("row-ISHA-iqamah").assertDoesNotExist()
        compose.onNodeWithTag("row-FAJR_IN_MOSQUE").assertHighlighted(true)
    }
    @Test fun actualFridayJumuahReturnsHighlightToAsrAfterArrival() {
        val friday = date.minusDays(1)
        val at = friday.atTime(13, 0).atZone(MoscowZone).toInstant()
        val config = IqamahConfig(enabled = true, source = "TEST", rules = listOf(IqamahRule(IqamahPrayer.JUMUAH, friday, mode = IqamahMode.FIXED, value = 780)))
        val now = mutableStateOf(at)
        compose.activity.setContent { NurIslamTheme {
            val next = (prayerStatus(now.value, schedules) as? PrayerStatus.Upcoming)?.prayer
            PrayerBoardScreen(now.value, now, calendar.day(friday), next, false, false, iqamah = iqamahStatus(now.value, config, schedules, true))
        } }
        compose.onNodeWithTag("row-JUMUAH").assertHighlighted(true).assert(hasAnyDescendant(hasText("13:00")))
        compose.onNodeWithTag("row-DHUHR").assertHighlighted(false).assert(hasAnyDescendant(hasText("12:05")))
        compose.onNodeWithTag("row-ASR").assertHighlighted(false)
        compose.runOnIdle { now.value = at.plusSeconds(60) }
        compose.onNodeWithTag("row-JUMUAH").assertDoesNotExist()
        compose.onNodeWithTag("row-ASR").assertHighlighted(true)
    }
    @Test fun actualConflictBoardCanBeSafelyDisabledUsingOnlyDpad() {
        val app = compose.activity.application as NurIslamApplication
        compose.waitUntil(10_000) { app.iqamah.state.value.loaded }
        val today = Instant.now().atZone(MoscowZone).toLocalDate()
        // Synthetic fixture labels exercise Release's approved-source branch only.
        val calendar = ScheduleJson.parse(ScheduleFixtures.json(today.year, meaning = "PRAYER_START").apply { addProperty("source", "Fixture protocol 42") }.toString().toByteArray())
        val bad = IqamahConfig(true, true, "Fixture protocol 42", rules = listOf(
            IqamahRule(IqamahPrayer.DHUHR, today, mode = IqamahMode.FIXED, value = 950),
            IqamahRule(IqamahPrayer.ASR, today, mode = IqamahMode.FIXED, value = 930)))
        kotlinx.coroutines.runBlocking { app.schedules.apply(calendar); app.iqamah.save(bad) }
        compose.onNodeWithTag("iqamah-conflict").assertIsDisplayed()
        key(Key.Menu)
        key(Key.DirectionDown, if (BuildConfig.PHONE_PREVIEW) 4 else 5); key(Key.Enter)
        key(Key.DirectionDown); key(Key.Enter)
        compose.onNodeWithText("Функция икамата").assertIsOn()
        key(Key.Enter) // Disable without silently rewriting either time.
        key(Key.DirectionUp); key(Key.Enter) // Save footer.
        key(Key.DirectionDown); key(Key.Enter) // Confirm.
        compose.waitUntil(10_000) { !androidx.lifecycle.ViewModelProvider(compose.activity)[BoardViewModel::class.java].busy.value }
        compose.onNodeWithText("Правила сохранены").assertIsDisplayed()
        key(Key.Enter) // Return to board (first enabled action).
        compose.onNodeWithTag("iqamah-conflict").assertDoesNotExist()
        assertFalse(app.iqamah.state.value.config.enabled)
        assertEquals(listOf(950, 930), app.iqamah.state.value.config.rules.map { it.value })
    }
    @Test fun jumuahWithoutAsrRuleShowsBoundaryConflictAndBlocksTouchAndDpadSave() {
        show(IqamahConfig(source = "TEST", rules = listOf(IqamahRule(IqamahPrayer.JUMUAH, date.minusDays(1), mode = IqamahMode.FIXED, value = 927))))
        key(Key.Enter) // Enable.
        key(Key.DirectionUp); key(Key.Enter) // Save footer.
        compose.onNodeWithText("Подтвердить сохранение").assertIsNotEnabled().performClick()
        key(Key.DirectionDown); key(Key.Enter)
        assertNull(saved)
        compose.onNodeWithTag("iqamah-list").performScrollToNode(hasText("Конфликт икаматов"))
        compose.onAllNodesWithText("начало молитвы Аср", substring = true).onFirst().assertIsDisplayed()
        image("jumuah-asr-boundary")
    }
    @Test fun unconfirmedBoundaryRemainsVisibleOnBoardAndAtConfirmationWithoutFalseBlocking() {
        val day = date
        val c = IqamahConfig(true, false, "TEST", rules = listOf(IqamahRule(IqamahPrayer.ISHA, day, mode = IqamahMode.FIXED, value = 1200)))
        val unknown = ScheduleState(ScheduleFixtures.schedule(), loaded = true)
        val clock = mutableStateOf(day.atTime(20, 0).atZone(MoscowZone).toInstant())
        compose.activity.setContent { NurIslamTheme {
            PrayerBoardScreen(clock.value, clock, unknown.current!!.day(day), null, false, false, iqamah = iqamahStatus(clock.value, c, unknown, true))
        } }
        compose.onNodeWithTag("iqamah-boundary-warning").assertIsDisplayed()
        compose.onNodeWithText("Проверьте правила икамата в настройках").assertIsDisplayed()
        compose.onNodeWithTag("iqamah-arrival").assertIsDisplayed()
        image("unknown-boundary")
        show(c.copy(enabled = false), knownFajr = false)
        tap("Функция икамата")
        compose.onNodeWithTag("iqamah-save").performClick()
        compose.onNodeWithText("Подтвердить сохранение").assertIsEnabled()
        compose.onNodeWithTag("iqamah-list").performScrollToNode(hasText("Граница молитвы не подтверждена"))
        compose.onAllNodesWithText("UNCONFIRMED", substring = true).onFirst().assertIsDisplayed()
        tap("Подтвердить сохранение")
        assertTrue(saved!!.enabled)
    }
    @Test @Config(sdk = [35], qualifiers = "w800dp-h360dp-xhdpi")
    fun longSourceAndLandscapeKeepSaveAndCancelVisible() {
        show(IqamahConfig(source = "Протокол ".repeat(20)))
        tap("Функция икамата")
        compose.onNodeWithTag("iqamah-list").performScrollToNode(hasText("Даты изменений и исключения (0)"))
        compose.onNodeWithTag("iqamah-save").assertIsDisplayed()
        compose.onNodeWithText("Назад").assertIsDisplayed()
        image("editor-landscape")
    }
    @Test @Config(sdk = [35], qualifiers = "w960dp-h540dp-xhdpi")
    fun countdownArrivalAndDimFitBothThemesAllModesAndScales() {
        val boardDate = date.minusDays(1) // Friday, including the explicit Jumuah row.
        val target = boardDate.atTime(15, 27).atZone(MoscowZone).toInstant()
        val settings = mutableStateOf(AppSettings())
        val status = mutableStateOf<IqamahStatus>(IqamahStatus.Waiting(IqamahEvent(IqamahPrayer.ASR, target, target.minusSeconds(720), IqamahRule(IqamahPrayer.ASR, boardDate, mode = IqamahMode.OFFSET, value = 12, baseCalendar = calendarFingerprint(calendar.json)), true)))
        val clock = mutableStateOf(target.minusSeconds(70))
        compose.activity.setContent { NurIslamTheme(settings.value.theme) { CompositionLocalProvider(LocalBoardSettings provides settings.value) {
            RotatedBoard(settings.value.displayMode, Modifier.fillMaxSize()) {
                PrayerBoardScreen(clock.value, clock, calendar.day(boardDate), null, settings.value.displayMode == DisplayMode.LANDSCAPE, false, iqamah = status.value)
            }
        } } }
        for (theme in BoardThemeId.entries) for (mode in listOf(DisplayMode.LANDSCAPE, DisplayMode.PORTRAIT_NATIVE, DisplayMode.PORTRAIT_ROTATE_CW, DisplayMode.PORTRAIT_ROTATE_CCW)) for (scale in listOf(.85f, 1f, 1.15f)) {
            compose.runOnIdle { settings.value = AppSettings(theme = theme, displayMode = mode, textScale = scale) }
            compose.onNodeWithTag("iqamah-time").assertIsDisplayed().assert(hasAnyDescendant(hasText("15:27")))
            compose.onNodeWithTag("countdown").assertIsDisplayed().assert(hasAnyDescendant(hasText("00:01:10")))
            compose.onNodeWithTag("row-ISHA").assertIsDisplayed()
            compose.onNodeWithTag("row-ASR").assertHighlighted(true)
            noTextOverflow()
            if (scale == 1f) image("${theme.name.lowercase()}-${mode.name.lowercase()}-waiting")
            val event = (status.value as IqamahStatus.Waiting).event
            compose.runOnIdle { status.value = IqamahStatus.Now(event, target.plusSeconds(60), false) }
            compose.onNodeWithTag("iqamah-arrival").assertIsDisplayed()
            compose.onNodeWithTag("row-ASR").assertHighlighted(true)
            noTextOverflow()
            compose.runOnIdle { status.value = IqamahStatus.Now(event, target.plusSeconds(300), true) }
            compose.onNodeWithTag("iqamah-dim").assertIsDisplayed()
            noTextOverflow()
            val jumuahAt = boardDate.atTime(13, 0).atZone(MoscowZone).toInstant()
            val jumuah = IqamahEvent(IqamahPrayer.JUMUAH, jumuahAt, null, IqamahRule(IqamahPrayer.JUMUAH, boardDate, mode = IqamahMode.FIXED, value = 780), true)
            compose.runOnIdle { clock.value = jumuahAt; status.value = IqamahStatus.Now(jumuah, jumuahAt.plusSeconds(60), false) }
            compose.onNodeWithTag("row-JUMUAH").assertIsDisplayed().assertHighlighted(true)
            compose.onNodeWithTag("row-JUMUAH").assert(hasAnyDescendant(hasText("Джума (пятница) · Икамат")))
            compose.onNodeWithTag("row-DHUHR").assertHighlighted(false).assert(hasAnyDescendant(hasText("12:05")))
            noTextOverflow()
            if (scale == 1f && mode == DisplayMode.LANDSCAPE) image("${theme.name.lowercase()}-jumuah")
            compose.runOnIdle { status.value = IqamahStatus.Conflict("Конфликт икаматов. Зухр 2026-10-24 15:30 / Аср 2026-10-24 15:20. Икамат на дату приостановлен; исправьте правила в настройках.") }
            compose.onNodeWithTag("iqamah-conflict").assertIsDisplayed()
            compose.onNodeWithTag("row-ASR").assertHighlighted(false)
            compose.onNodeWithTag("row-JUMUAH").assertDoesNotExist()
            noTextOverflow()
            if (scale == 1f && mode == DisplayMode.LANDSCAPE) image("${theme.name.lowercase()}-conflict")
            val unknown = ScheduleState(ScheduleFixtures.schedule(), loaded = true)
            val ishaAt = boardDate.atTime(20, 0).atZone(MoscowZone).toInstant()
            val ishaRules = IqamahConfig(true, false, "TEST", rules = listOf(IqamahRule(IqamahPrayer.ISHA, boardDate, mode = IqamahMode.FIXED, value = 1200)))
            compose.runOnIdle { clock.value = ishaAt; status.value = iqamahStatus(ishaAt, ishaRules, unknown, true) }
            compose.onNodeWithTag("iqamah-boundary-warning").assertIsDisplayed()
            compose.onNodeWithTag("row-ISHA").assertHighlighted(true)
            noTextOverflow()
            compose.runOnIdle { clock.value = target.minusSeconds(70); status.value = IqamahStatus.Waiting(event) }
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
