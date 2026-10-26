package dev.nurislam.tv.ui

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import dev.nurislam.tv.R
import dev.nurislam.tv.data.*
import dev.nurislam.tv.domain.DisplayMode
import dev.nurislam.tv.settings.*
import java.time.Instant

@Composable
fun AdminPanel(
    settings: AppSettings,
    schedules: ScheduleState,
    candidate: ValidatedSchedule?,
    busy: Boolean,
    message: String?,
    version: String,
    inboxPath: String,
    focusToken: Any? = null,
    touchMode: Boolean = false,
    appliedSchedule: ValidatedSchedule? = null,
    iqamahCalendarNotice: String? = null,
    onSettings: ((AppSettings) -> AppSettings) -> Unit,
    onOrientation: (DisplayMode) -> Unit,
    onPickFile: () -> Unit,
    onInbox: () -> Unit,
    onApply: () -> Unit,
    onRollback: () -> Unit,
    onDismissCandidate: () -> Unit,
    onDismissMessage: () -> Unit,
    onTimeSettings: () -> Unit = {},
    onDeviceSetup: (() -> Unit)? = null,
    onIqamah: (() -> Unit)? = null,
    onImportFolder: (() -> Unit)? = null,
    onRamadan: (() -> Unit)? = null,
    onHomeSettings: () -> Unit,
    onOtherLauncher: () -> Unit,
    onClose: () -> Unit,
    calendar: dev.nurislam.tv.calendar.MosqueCalendarState = dev.nurislam.tv.calendar.MosqueCalendarState(),
    calendarCandidate: dev.nurislam.tv.calendar.MosqueCalendar? = null,
    onApplyCalendar: () -> Unit = {},
    onCancelCalendar: () -> Unit = {},
    onRollbackCalendar: () -> Unit = {},
    calendarDiagnostic: String? = null,
 ) {
    var page by remember { mutableStateOf("main") }
    fun back() { if (page == "main") onClose() else page = "main" }
    if (message != null) {
        SettingsMenu("Результат", listOf(SettingsAction(message, enabled = false, informational = true) {},
            SettingsAction("Вернуться к табло") { onDismissMessage(); onClose() }), touchMode, busy, onBack = onDismissMessage, tag = "admin")
        return
    }
    if (candidate != null) { ScheduleImportPreview(candidate, schedules, busy, touchMode, onApply, onDismissCandidate); return }
    if (calendarCandidate != null) {
        val c = calendarCandidate
        val sections = "Икамат: ${if (c.iqamah.rules.isEmpty()) "не настроен" else "${c.iqamah.rules.size} правил"}\nРамадан: ${if (c.ramadan.dates.isEmpty()) "не настроен" else "${c.ramadan.dates.size} периодов"}\nСобытия: ${c.events.size}"
        DocumentPreview("Календарь мечети", "${c.name}\n${c.city} · ${c.zone.id}\n${c.from} — ${c.until}\n\n$sections",
            "Источник: ${c.source}\nВерсия: ${c.revision}\n\n" + c.iqamah.rules.joinToString("\n") { "${it.prayer.title}: ${it.mode} ${if(it.mode == dev.nurislam.tv.iqamah.IqamahMode.FIXED) "%02d:%02d".format(it.value/60,it.value%60) else it.value} · с ${it.from}${if(it.exception) " · один день" else ""}" } +
                "\n" + c.ramadan.dates.joinToString("\n") { "Первый пост: ${it.firstFast} · Ураза-байрам: ${it.eid ?: "не задан"}" } + "\n" + c.events.joinToString("\n") { "${it.from}: ${it.title}" },
            c.demo, busy, touchMode, onApplyCalendar, onCancelCalendar,
            warning = c.compatibility(schedules) ?: if (schedules.current == null) "Расписание не загружено. Икамат станет доступен после загрузки совместимого расписания. Рамадан и события работают отдельно." else "Заменит прежний календарь целиком. Отсутствующие разделы будут отключены. Времена намазов и оформление сохранятся.")
        return
    }
    val c = calendar.current
    val data = schedules.current
    val actions = when (page) {
        "main" -> listOfNotNull(
            SettingsAction("Загрузить расписание", "Готовый JSON или поддерживаемый Excel") { onPickFile() },
            SettingsAction("Загрузить календарь мечети", "Один JSON: икамат, Джума, Рамадан и события") { onPickFile() },
            SettingsAction("Внешний вид", "Тема, язык, масштаб и ориентация") { page = "appearance" },
            if (touchMode) null else SettingsAction("Запуск при включении телевизора", "Необязательная настройка основного экрана") { onDeviceSetup?.invoke() },
            SettingsAction("Загруженные данные", data?.let { "${it.locationName} · ${it.metadata.year}" } ?: "Расписание пока не загружено") { page = "data" },
            SettingsAction("Для специалиста", "Диагностика и прежние ручные настройки") { page = "expert" },
            SettingsAction("Вернуться к табло") { onClose() })
        "appearance" -> listOf(
            SettingsAction("Тема: ${settings.theme}") { onSettings { it.copy(theme = if (it.theme == BoardThemeId.ELEGANT) BoardThemeId.CLASSIC else BoardThemeId.ELEGANT) } },
            SettingsAction("Ориентация экрана") { page = "orientation" },
            SettingsAction("Масштаб текста: ${(settings.textScale * 100).toInt()}%") { onSettings { it.copy(textScale = when { it.textScale < 1f -> 1f; it.textScale == 1f -> 1.15f; else -> .85f }) } },
            SettingsAction("Язык: ${when(settings.language) { BoardLanguage.RUSSIAN -> "Русский"; BoardLanguage.TATAR -> "Татарский"; BoardLanguage.BOTH -> "Татарский + русский" }}") { onSettings { it.copy(language = BoardLanguage.entries[(it.language.ordinal+1)%BoardLanguage.entries.size]) } })
        "orientation" -> DisplayMode.entries.map { mode ->
            val label = stringResource(when(mode) { DisplayMode.AUTO -> R.string.mode_auto; DisplayMode.PORTRAIT_NATIVE -> R.string.mode_native; DisplayMode.PORTRAIT_ROTATE_CW -> R.string.mode_cw; DisplayMode.PORTRAIT_ROTATE_CCW -> R.string.mode_ccw; DisplayMode.LANDSCAPE -> R.string.mode_landscape })
            SettingsAction(label, if (mode == settings.displayMode) "Текущий режим" else "Примерка на 15 секунд, затем подтверждение") { if(mode != settings.displayMode) onOrientation(mode) }
        }
        "data" -> listOfNotNull(
            SettingsAction("Расписание", data?.let { "${it.locationName} · ${it.metadata.year} · ${it.metadata.timezone.id}\n${it.metadata.source}" } ?: "Не загружено", enabled = false, informational = true) {},
            SettingsAction("Календарь мечети", c?.let { "${it.name} · ${it.from} — ${it.until}\n${it.source}" } ?: if(calendar.hasDocument) "Не удалось прочитать" else "Не загружен. Сохранённые ручные настройки продолжают работать.", enabled = false, informational = true) {},
            calendarDiagnostic?.let { SettingsAction("Проверьте календарь", it, enabled = false, informational = true) {} },
            SettingsAction("Выбрать папку с файлами", "Для USB и удобного выбора пультом", enabled = onImportFolder != null) { onImportFolder?.invoke() },
            SettingsAction("Вернуть предыдущее расписание", enabled = schedules.previous != null) { onRollback() },
            SettingsAction("Вернуть предыдущий календарь мечети", enabled = calendar.previous != null) { onRollbackCalendar() })
        else -> listOfNotNull(
            SettingsAction("Диагностика", "Версия $version\nЧасовой пояс: ${schedules.zone.id}\n${schedules.problem ?: "Расписание прочитано"}\n${calendar.problem ?: "Календарь прочитан"}\n${calendarDiagnostic ?: ""}", enabled = false, informational = true) {},
            SettingsAction("Дата и время устройства") { onTimeSettings() },
            if (!calendar.hasDocument) SettingsAction("Ручной икамат (прежний способ)", enabled = onIqamah != null) { onIqamah?.invoke() } else null,
            if (!calendar.hasDocument) SettingsAction("Ручные даты Рамадана (прежний способ)", enabled = onRamadan != null) { onRamadan?.invoke() } else null,
            SettingsAction("Резервный импорт из папки приложения", inboxPath) { onInbox() })
    }
    SettingsMenu(when(page) { "main" -> "Настройки"; "appearance" -> "Внешний вид"; "orientation" -> "Ориентация экрана"; "data" -> "Загруженные данные"; else -> "Для специалиста" },
        actions, touchMode, busy, ::back, tag = "admin", focusToken = focusToken)
}
