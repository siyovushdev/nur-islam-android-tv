package dev.nurislam.tv.ui

import androidx.compose.runtime.*
import dev.nurislam.tv.data.isTestSource
import dev.nurislam.tv.model.MoscowZone
import dev.nurislam.tv.ramadan.*
import dev.nurislam.tv.settings.BoardLanguage
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun RamadanPanel(state: RamadanState, busy: Boolean, message: String?, touchMode: Boolean, clockProblem: Boolean,
    onClockConfirm: () -> Unit, onSave: (RamadanConfig) -> Unit, onRollback: () -> Unit, onDismissMessage: () -> Unit, onClose: () -> Unit) {
    val zone = LocalBoardZone.current
    var draft by remember { mutableStateOf(state.config) }
    var initialized by remember { mutableStateOf(state.loaded) }
    var page by remember { mutableStateOf("main") }
    var first by remember { mutableStateOf("") }
    var eid by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var approved by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<LocalDate?>(null) }
    var number by remember { mutableStateOf<String?>(null) }
    var textEdit by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedPending by remember { mutableStateOf<RamadanConfig?>(null) }
    var rollingBack by remember { mutableStateOf(false) }
    LaunchedEffect(state.config, state.loaded) {
        if (!initialized && state.loaded) { draft = state.config; initialized = true }
        if (savedPending == state.config || rollingBack) { draft = state.config; savedPending = null; rollingBack = false; page = "saved" }
    }
    fun back() {
        when {
            message != null -> onDismissMessage()
            page == "main" -> if (draft != state.config) page = "discard" else onClose()
            else -> page = "main"
        }
    }
    if (!state.loaded) { SettingsMenu("Загрузка дат Рамадана…", emptyList(), touchMode, false, onClose, tag = "ramadan"); return }
    if (textEdit) {
        RemoteLabelEditor(source, 200, onSave = { source = it; approved = false; textEdit = false }, onCancel = { textEdit = false },
            touchMode = touchMode, title = "Источник дат Рамадана", label = "Источник и документ согласования")
        return
    }
    if (number != null) {
        val field = number!!
        NumberEditor(if (field == "first") first else eid, if (field == "first") "Первый день поста: ГГГГММДД" else "Ураза-байрам: ГГГГММДД (можно очистить)", 8,
            onSave = { if (field == "first") first = it else eid = it; approved = false; number = null }, onCancel = { number = null }, touchMode = touchMode, tag = "ramadan")
        return
    }
    fun parsed(value: String): LocalDate {
        require(value.matches(Regex("[0-9]{8}"))) { "Введите восемь цифр ГГГГММДД" }
        return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
    }
    fun previewDates(): RamadanDates { val d = parsed(first); return RamadanDates(d.year, d, if (eid.isEmpty()) null else parsed(eid), source, approved) }
    fun edit(d: RamadanDates?) {
        original = d?.firstFast; first = d?.firstFast?.format(DateTimeFormatter.BASIC_ISO_DATE) ?: ""
        eid = d?.eid?.format(DateTimeFormatter.BASIC_ISO_DATE) ?: ""; source = d?.source ?: ""; approved = d?.approved ?: false; page = "dates"
    }
    val preview = runCatching { previewDates() }
    val status = ramadanStatus(Instant.now(), draft, clockProblem, zone)
    val previewText = ramadanText(status, BoardLanguage.BOTH) ?: (status as? RamadanStatus.Hidden)?.reason ?: "Блок выключен"
    val entries = when {
        error != null -> listOf(SettingsAction("Понятно", error!!) { error = null })
        message != null -> listOf(SettingsAction("Понятно", message) { onDismissMessage(); savedPending = null; rollingBack = false })
        page == "saved" -> listOf(SettingsAction("Даты сохранены", "Табло обновлено. Настройки восстановятся после перезапуска.", false, informational = true) {},
            SettingsAction("Вернуться к табло") { onClose() }, SettingsAction("Продолжить настройку") { page = "main" })
        page == "discard" -> listOf(SettingsAction("Продолжить редактирование") { page = "main" }, SettingsAction("Отменить изменения и выйти") { onClose() })
        page == "delete" -> listOf(SettingsAction("Отменить удаление") { page = "dates" }, SettingsAction("Удалить период $original", "Применится после общего сохранения") {
            draft = draft.copy(dates = draft.dates.filterNot { it.firstFast == original }); page = "main"
        })
        page == "confirm" -> listOf(SettingsAction("Вернуться к черновику") { page = "main" },
            SettingsAction("Подтвердить и сохранить даты", "${if (draft.enabled) "Блок включён" else "Блок выключен"}. Периодов: ${draft.dates.size}. Неподтверждённые даты не появятся на табло. Времена молитв и икамат не меняются.") { savedPending = draft; onSave(draft) })
        page == "dates" -> listOf(
            SettingsAction("Первый день поста: ${first.ifEmpty { "не задан" }}", "Григорианская дата, не вечер начала религиозных суток") { number = "first" },
            SettingsAction("Ураза-байрам: ${eid.ifEmpty { "не задан" }}", "Необязательно; между датами должно быть 29 или 30 дней. Без этой даты 30-й день не объявляется днём поста.") { number = "eid" },
            SettingsAction("Источник: ${source.ifBlank { "не указан" }}") { textEdit = true },
            SettingsAction("Даты утверждены мечетью", "Подтвердите обе введённые даты по документу. Любое изменение снимает подтверждение.", source.isNotBlank() && !isTestSource(source), approved) { approved = !approved },
            SettingsAction("Проверка дат", preview.exceptionOrNull()?.message ?: "${preview.getOrNull()!!.firstFast} → ${preview.getOrNull()!!.eid ?: "окончание не задано"}. ${if (approved) "Подтверждены администратором" else "Не подтверждены — блок будет скрыт"}", false, informational = true) {},
            SettingsAction("Добавить даты в черновик", "Затем сохраните настройки целиком") {
                try {
                    val dates = preview.getOrThrow()
                    draft = draft.copy(dates = draft.dates.filterNot { it.firstFast == original } + dates); page = "main"
                } catch (e: Exception) { error = "Даты не добавлены: ${e.message ?: "неверный формат"}" }
            },
        ) + if (original != null) listOf(SettingsAction("Удалить этот период") { page = "delete" }) else emptyList()
        else -> listOf(
            SettingsAction("Показывать блок Рамадана", "По умолчанию выключен. Считает местные календарные дни мечети до первого дня поста.", checked = draft.enabled) { draft = draft.copy(enabled = !draft.enabled) },
            SettingsAction("Предпросмотр на дату устройства", previewText, false, informational = true) {},
            SettingsAction("Добавить даты Рамадана", "Без автоматически заданных дат и источников") { edit(null) },
        ) + draft.dates.sortedBy { it.firstFast }.map { d -> SettingsAction("Период ${d.year}: ${d.firstFast}", "Ураза-байрам: ${d.eid ?: "не задан"} · ${if (d.approved) "подтверждён" else "не подтверждён"} · ${d.source}") { edit(d) } } +
            listOf(SettingsAction("Откатить сохранённые даты", "Вернуть предыдущую версию отдельно от календаря намазов", state.previous != null) { page = "rollback" }) +
            if (clockProblem) listOf(SettingsAction("Проверил системные часы", "Сначала сверьте дату/время Android. Это действие не меняет системное время.") { onClockConfirm() }) else emptyList()
    }
    val actual = if (page == "rollback" && message == null && error == null) listOf(SettingsAction("Отменить откат") { page = "main" },
        SettingsAction("Подтвердить откат дат", "Текущие даты станут резервными") { rollingBack = true; onRollback() }) else entries
    SettingsMenu(if (page == "dates") "Даты Рамадана" else if (page == "confirm") "Проверка настроек Рамадана" else "Рамадан", actual, touchMode, busy,
        onBack = ::back, onSave = if (page == "main" && message == null && error == null) ({ page = "confirm" }) else null,
        notice = state.problem ?: "Даты независимы от расписания намазов · Нет автоматически утверждённых дат", tag = "ramadan")
}
