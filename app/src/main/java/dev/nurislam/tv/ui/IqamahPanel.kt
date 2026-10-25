package dev.nurislam.tv.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nurislam.tv.data.*
import dev.nurislam.tv.domain.formatTime
import dev.nurislam.tv.iqamah.*
import dev.nurislam.tv.model.MoscowZone
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun IqamahPanel(state: IqamahState, schedules: ScheduleState, busy: Boolean, message: String?, touchMode: Boolean,
    clockProblem: Boolean, onClockConfirm: () -> Unit, onSave: (IqamahConfig) -> Unit, onRollback: () -> Unit,
    onDismissMessage: () -> Unit, onClose: () -> Unit) {
    var draft by remember { mutableStateOf(state.config) }
    var initialized by remember { mutableStateOf(state.loaded) }
    var page by remember { mutableStateOf("main") }
    var prayer by remember { mutableStateOf(IqamahPrayer.FAJR) }
    var date by remember { mutableStateOf(Instant.now().atZone(MoscowZone).toLocalDate().toString()) }
    var exception by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(IqamahMode.OFF) }
    var value by remember { mutableStateOf("") }
    var base by remember { mutableStateOf(false) }
    var number by remember { mutableStateOf<String?>(null) }
    var textEdit by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var originalRule by remember { mutableStateOf<IqamahRule?>(null) }
    var rollbackPending by remember { mutableStateOf(false) }
    var savedPending by remember { mutableStateOf<IqamahConfig?>(null) }
    LaunchedEffect(state.config, state.loaded) { if (!initialized && state.loaded) { draft = state.config; initialized = true }; if (savedPending == state.config || rollbackPending) { draft = state.config; savedPending = null; rollbackPending = false; page = "saved" } }
    if (!state.loaded) {
        SettingsMenu("Загрузка правил икамата…", emptyList(), touchMode, busy = false, onBack = onClose)
        return
    }
    fun change(block: (IqamahConfig) -> IqamahConfig) { draft = block(draft.copy(approved = false)) }
    fun back() {
        if (message != null) onDismissMessage()
        else if (page == "main") { if (draft != state.config) page = "discard" else onClose() }
        else page = "main"
    }
    if (textEdit) {
        RemoteLabelEditor(draft.source, 200, onSave = { text -> change { it.copy(source = text) }; textEdit = false }, onCancel = { textEdit = false }, touchMode = touchMode, title = "Источник правил икамата", label = "Решение/протокол или ТЕСТ ДЛЯ ТЕЛЕФОНА")
        return
    }
    if (number != null) {
        val kind = number!!
        NumberEditor(when (kind) { "date" -> date.replace("-", ""); "dim" -> draft.dimMinutes.toString(); else -> value.replace(":", "") },
            when (kind) { "date" -> "Дата YYYYMMDD"; "dim" -> "Затемнение, минут (0 — выключено)"; else -> if (mode == IqamahMode.FIXED) "Время HHmm, например 0530" else "Минут после подтверждённого начала (0–720)" },
            if (kind == "date") 8 else if (mode == IqamahMode.FIXED && kind == "value") 4 else 3,
            onCancel = { number = null }, onSave = { digits ->
                try {
                    when (kind) {
                        "date" -> { require(digits.length == 8); val d = LocalDate.parse("${digits.take(4)}-${digits.substring(4,6)}-${digits.takeLast(2)}"); require(d.year in 2000..2200); date = d.toString(); base = false }
                        "dim" -> { val v = digits.toInt(); require(v in 0..60); change { it.copy(dimMinutes = v) } }
                        else -> { if (mode == IqamahMode.FIXED) { require(digits.length == 4); LocalTime.of(digits.take(2).toInt(), digits.takeLast(2).toInt()); value = "${digits.take(2)}:${digits.takeLast(2)}" }
                            else { require(digits.toInt() in 0..720); value = digits.toInt().toString() } }
                    }
                    number = null
                } catch (_: Exception) { error = "Неверное значение. Дата YYYYMMDD, время HHmm; проверьте диапазон."; number = null }
            }, touchMode = touchMode)
        return
    }
    val selectedDate = runCatching { LocalDate.parse(date) }.getOrNull()
    val calendar = selectedDate?.let(schedules::scheduleFor)
    val canBind = calendar != null && prayer != IqamahPrayer.JUMUAH && (prayer != IqamahPrayer.FAJR || calendar.fajrMeaning == FajrMeaning.PRAYER_START)
    fun editRule(r: IqamahRule? = null) {
        originalRule = r
        mode = r?.mode ?: IqamahMode.OFF
        value = r?.let { if (it.mode == IqamahMode.FIXED) "%02d:%02d".format(it.value / 60, it.value % 60) else if (it.mode == IqamahMode.OFFSET) it.value.toString() else "" } ?: ""
        date = r?.from?.toString() ?: Instant.now().atZone(MoscowZone).toLocalDate().toString()
        exception = r?.exception ?: false
        base = r?.baseCalendar != null && r.baseCalendar == schedules.scheduleFor(LocalDate.parse(date))?.let { calendarFingerprint(it.json) }
        page = "rule"
    }
    val today = Instant.now().atZone(MoscowZone).toLocalDate()
    val conflicts = if (page == "confirm") remember(draft, schedules, today) { iqamahConflicts(draft, schedules, today) } else emptyList()
    val boundaryWarnings = if (page == "confirm") remember(draft, schedules, today) { iqamahBoundaryWarnings(draft, schedules, today) } else emptyList()
    val canSave = !draft.enabled || conflicts.isEmpty()
    val entries = when {
        error != null -> listOf(SettingsAction("Понятно", error!!) { error = null })
        message != null -> listOf(SettingsAction("Понятно", message) { onDismissMessage(); savedPending = null; rollbackPending = false })
        page == "saved" -> listOf(SettingsAction("Правила сохранены", "Табло обновлено; после перезапуска правила восстановятся.", false, informational = true) {}, SettingsAction("Вернуться к табло") { onClose() }, SettingsAction("Продолжить настройку") { page = "main" })
        page == "discard" -> listOf(SettingsAction("Продолжить редактирование") { page = "main" }, SettingsAction("Отменить изменения и выйти") { onClose() })
        page == "confirm" -> listOf(
            SettingsAction("Вернуться к черновику") { page = "main" },
            SettingsAction("Подтвердить сохранение", "${if (draft.approved) "Правила отмечены администратором как утверждённые: ${draft.source}" else "ТЕСТОВЫЕ ПРАВИЛА — не утверждены мечетью"}. Записей: ${draft.rules.size}. Затемнение: ${draft.dimMinutes} мин. Время JSON не меняется.${if (!canSave) " Сохранение включённой функции заблокировано: исправьте конфликты либо выключите икамат." else ""}", !busy && state.loaded && canSave) { savedPending = draft; onSave(draft) },
        ) + conflicts.take(5).map { conflict -> SettingsAction("Конфликт икаматов", conflict.detail, false, informational = true) {} } +
            (if (conflicts.size > 5) listOf(SettingsAction("Ещё конфликтов: ${conflicts.size - 5}", "Проверьте даты изменений и исключения", false, informational = true) {}) else emptyList()) +
            boundaryWarnings.take(5).map { warning -> SettingsAction("Граница молитвы не подтверждена", warning, false, informational = true) {} }
        page == "rule" -> listOf(
            SettingsAction("Дата: $date", "Действует с этой даты до следующей записи; исключение — только на выбранный день") { number = "date" },
            SettingsAction("Разовое исключение", checked = exception) { exception = !exception },
            SettingsAction("Режим: ${when (mode) { IqamahMode.OFF -> "Отключено"; IqamahMode.FIXED -> "Точное время"; IqamahMode.OFFSET -> "После начала" }}", "Для Джума только отдельное точное время. Интервал не задаётся по умолчанию.") {
                mode = when (mode) { IqamahMode.OFF -> IqamahMode.FIXED; IqamahMode.FIXED -> if (canBind) IqamahMode.OFFSET else IqamahMode.OFF; IqamahMode.OFFSET -> IqamahMode.OFF }
                value = ""; base = false
            },
            SettingsAction("Значение: ${value.ifEmpty { "не задано" }}", if (mode == IqamahMode.FIXED) "Отдельный икамат HH:mm, без изменения JSON" else "Введённые минуты, без скрытой прибавки", mode != IqamahMode.OFF) { number = "value" },
            SettingsAction("Базовое поле подтверждено как начало молитвы", if (canBind) "${prayer.event} · ${calendar!!.metadata.year} / ${calendar.metadata.revision}. Подтверждаю семантику именно этого поля и календаря; не время икамата." else "Недоступно: нет календаря / Джума / Фаджр не PRAYER_START. «Мәчеттә укыла» не является автоматически азаном.", canBind && mode != IqamahMode.OFF, base) { base = !base },
            SettingsAction("Добавить в черновик", "Не записывает на диск до общего сохранения") {
                try {
                    val d = requireNotNull(selectedDate)
                    val v = when (mode) { IqamahMode.OFF -> 0; IqamahMode.FIXED -> { require(value.matches(Regex("[0-9]{2}:[0-9]{2}"))); LocalTime.parse(value).let { it.hour * 60 + it.minute } }; IqamahMode.OFFSET -> { require(base && canBind); value.toInt() } }
                    val r = IqamahRule(prayer, d, exception, mode, v, if (base && canBind && mode != IqamahMode.OFF) calendarFingerprint(calendar!!.json) else null)
                    change { it.copy(rules = it.rules.filterNot { old -> old.prayer == prayer && old.from == d && old.exception == exception } + r) }
                    page = "main"
                } catch (_: Exception) { error = "Не задано корректное время/интервал или не подтверждена база. Запись не добавлена." }
            },
        ) + if (originalRule != null) listOf(SettingsAction("Удалить запись из черновика", "Будет удалена только выбранная запись, после общего сохранения") {
            val old = originalRule!!
            change { it.copy(rules = it.rules.filterNot { r -> r.prayer == old.prayer && r.from == old.from && r.exception == old.exception }) }
            page = "main"
        }) else emptyList()
        page == "history" -> listOf(SettingsAction("Назад") { page = "main" }) + draft.rules.sortedWith(compareBy({ it.from }, { it.prayer.ordinal })).map { r ->
            SettingsAction("${r.prayer.title}: ${ruleLabel(r)}", "${if (r.exception) "Исключение" else "С"} ${r.from} · OK — редактировать") { prayer = r.prayer; editRule(r) }
        }
        page == "today" -> iqamahDay(Instant.now().atZone(MoscowZone).toLocalDate(), draft, schedules).map { row ->
            SettingsAction(row.prayer.title, "Начало: ${row.start?.atZone(MoscowZone)?.toLocalTime()?.let(::formatTime) ?: "не подтверждено"} · Икамат: ${row.event?.at?.atZone(MoscowZone)?.toLocalTime()?.let(::formatTime) ?: "—"}\n${row.detail}", false, informational = true) {}
        }
        else -> listOf(
            SettingsAction("Функция икамата", "По умолчанию отключена, все пять правил пусты", checked = draft.enabled) { change { it.copy(enabled = !it.enabled) } },
            SettingsAction("Источник правил: ${draft.source.ifBlank { "не указан" }}", "Протокол/решение администрации; тестовая запись не становится официальной") { textEdit = true },
            SettingsAction("Правила утверждены мечетью", "Изменение параметров снимает подтверждение. Без него только демонстрация Debug; Release не запускает икамат.", draft.source.isNotBlank() && !isTestSource(draft.source), draft.approved) { draft = draft.copy(approved = !draft.approved) },
            SettingsAction("Затемнение: ${if (draft.dimMinutes == 0) "выключено" else "${draft.dimMinutes} мин"}", "Не меняет время икамата. Без затемнения надпись «Икамат» показывается 60 секунд.") { number = "dim" },
            SettingsAction("Часы проверены", "${if (clockProblem) "Обнаружен скачок времени! " else ""}Табло Europe/Moscow; система ${Instant.now()}. Исправьте часы Android, затем подтвердите.") { onClockConfirm() },
        ) + IqamahPrayer.entries.map { p ->
            val effective = draft.rule(p, Instant.now().atZone(MoscowZone).toLocalDate())
            SettingsAction(p.title, "${effective?.let { ruleLabel(it) + " · " + (if (it.exception) "исключение " else "с ") + it.from } ?: "Не настроено"}. OK — новое изменение / исключение.") { prayer = p; editRule() }
        } + listOf(
            SettingsAction("Сегодня: начало / икамат / правило") { page = "today" },
            SettingsAction("Даты изменений и исключения (${draft.rules.size})") { page = "history" },
            SettingsAction("Откат сохранённых правил", "Требует подтверждения; черновик будет заменён", state.previous != null && state.previous != state.config && !busy) { page = "rollback" },
        )
    }
    if (page == "rollback" && error == null && message == null) {
        SettingsMenu("Откат икамата", listOf(SettingsAction("Подтвердить откат", "Вернуть предыдущую сохранённую версию", !busy) { rollbackPending = true; onRollback(); page = "main" }), touchMode, busy, onBack = { page = "main" })
        return
    }
    val footerSave = if (page == "main" && draft != state.config) ({ page = "confirm" }) else null
    SettingsMenu(when (page) { "rule" -> "Икамат: ${prayer.title}"; "today" -> "Сегодня · Europe/Moscow"; "history" -> "Изменения и исключения"; "confirm" -> "Сохранить правила?"; else -> "Икамат${if (draft != state.config) " · черновик" else ""}" },
        entries, touchMode, busy, onBack = ::back, onSave = footerSave,
        notice = state.problem ?: if (!draft.approved) "ТЕСТОВЫЕ ПРАВИЛА · не утверждены мечетью" else "Источник: ${draft.source}")
}
fun ruleLabel(rule: IqamahRule): String = when (rule.mode) { IqamahMode.OFF -> "отключено"; IqamahMode.FIXED -> "%02d:%02d".format(rule.value / 60, rule.value % 60); IqamahMode.OFFSET -> "+${rule.value} мин от подтверждённого начала" }
