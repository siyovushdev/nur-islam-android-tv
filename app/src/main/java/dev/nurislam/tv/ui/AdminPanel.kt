package dev.nurislam.tv.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.nurislam.tv.R
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nurislam.tv.data.*
import dev.nurislam.tv.domain.DisplayMode
import dev.nurislam.tv.model.MoscowZone
import dev.nurislam.tv.settings.*
import java.time.Instant
import java.time.ZoneId

private data class AdminItem(val title: String, val detail: String = "", val enabled: Boolean = true, val action: () -> Unit)
private enum class AdminPage { ENTRY, SETTINGS, DISPLAY, SCHEDULE, PREVIEW, ROLLBACK, DIAGNOSTICS, HOME }

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
    onHomeSettings: () -> Unit,
    onOtherLauncher: () -> Unit,
    onClose: () -> Unit,
) {
    var page by remember { mutableStateOf(if (touchMode) AdminPage.SETTINGS else AdminPage.ENTRY) }
    var selection by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val scroll = rememberLazyListState()
    fun navigate(next: AdminPage) { page = next; selection = 0 }
    fun back() {
        when {
            edit != null -> edit = null
            message != null -> onDismissMessage()
            page == AdminPage.ENTRY || page == AdminPage.SETTINGS -> onClose()
            page == AdminPage.PREVIEW -> { onDismissCandidate(); navigate(AdminPage.SCHEDULE) }
            else -> navigate(AdminPage.SETTINGS)
        }
    }
    BackHandler { back() }
    LaunchedEffect(candidate) { if (candidate != null) navigate(AdminPage.PREVIEW) }
    LaunchedEffect(page, message, edit, focusToken, candidate, appliedSchedule) {
        if (message != null) selection = 0
        if (edit == null && (candidate == null || message != null) && appliedSchedule == null) focus.requestFocus()
    }
    LaunchedEffect(selection, page) { scroll.scrollToItem(selection.coerceAtLeast(0)) }
    if (edit != null) {
        RemoteLabelEditor(if (edit == "name") settings.mosqueName else settings.location,
            if (edit == "name") 60 else 80, onCancel = { edit = null }, onSave = { text ->
                val field = edit
                onSettings { if (field == "name") it.copy(mosqueName = text) else it.copy(location = text) }
                edit = null
            }, touchMode = touchMode)
        return
    }
    if (appliedSchedule != null) {
        ScheduleImportResult(appliedSchedule, touchMode, onBoard = onClose, onDismiss = {
            onDismissMessage(); navigate(AdminPage.SCHEDULE)
        })
        return
    }
    if (candidate != null && message == null) {
        ScheduleImportPreview(candidate, schedules, busy, touchMode, onApply = onApply, onCancel = {
            onDismissCandidate(); navigate(AdminPage.SCHEDULE)
        })
        return
    }
    val data = schedules.current
    val entries = when {
        message != null -> listOf(AdminItem("Понятно", message) { onDismissMessage() })
        page == AdminPage.ENTRY -> listOf(AdminItem("Вернуться к табло") { onClose() }, AdminItem("Открыть настройки администратора", "Изменения расписания требуют отдельного подтверждения") { navigate(AdminPage.SETTINGS) })
        page == AdminPage.SETTINGS -> listOfNotNull(
            AdminItem("Вернуться к табло") { onClose() },
            AdminItem("Тема: ${settings.theme}", if (touchMode) "Нажмите для переключения Elegant / Classic" else "OK — переключить Elegant / Classic") { onSettings { it.copy(theme = if (it.theme == BoardThemeId.ELEGANT) BoardThemeId.CLASSIC else BoardThemeId.ELEGANT) } },
            AdminItem("Ориентация: ${settings.displayMode}", "Выбрать режим экрана; подтверждение за 15 секунд") { navigate(AdminPage.DISPLAY); selection = settings.displayMode.ordinal + 1 },
            AdminItem("Масштаб текста: ${(settings.textScale * 100).toInt()}%", "${if (touchMode) "Нажмите" else "OK"} — 85 / 100 / 115%; текст подбирается под доступное место") { onSettings { it.copy(textScale = when { it.textScale < 1f -> 1f; it.textScale == 1f -> 1.15f; else -> .85f }) } },
            AdminItem("Язык: ${when (settings.language) { BoardLanguage.BOTH -> "Татарский + русский"; BoardLanguage.TATAR -> "Татарский"; BoardLanguage.RUSSIAN -> "Русский" }}") { onSettings { it.copy(language = BoardLanguage.entries[(it.language.ordinal + 1) % BoardLanguage.entries.size]) } },
            AdminItem("Название: ${settings.mosqueName}", if (touchMode) "Ввод с клавиатуры телефона" else "Экранная клавиатура управляется D-pad") { edit = "name" },
            AdminItem("Местоположение: ${settings.location}", "Меняет подпись, но не часовой пояс и расписание") { edit = "location" },
            AdminItem("Расписание", data?.let { "Последний импорт: ${it.metadata.year} · ${it.metadata.revision}" } ?: "Нет утверждённого календаря") { navigate(AdminPage.SCHEDULE) },
            AdminItem("Диагностика") { navigate(AdminPage.DIAGNOSTICS) },
            if (touchMode) null else AdminItem("Основной экран HOME / безопасный выход") { navigate(AdminPage.HOME) },
            AdminItem("Икамат: не настроен", "Нужны утверждённые правила мечети. Автоматические интервалы отключены.", false) {},
        )
        page == AdminPage.DISPLAY -> listOf(AdminItem("Назад") { navigate(AdminPage.SETTINGS) }) + DisplayMode.entries.map { mode ->
            val label = stringResource(when (mode) {
                DisplayMode.AUTO -> R.string.mode_auto
                DisplayMode.PORTRAIT_NATIVE -> R.string.mode_native
                DisplayMode.PORTRAIT_ROTATE_CW -> R.string.mode_cw
                DisplayMode.PORTRAIT_ROTATE_CCW -> R.string.mode_ccw
                DisplayMode.LANDSCAPE -> R.string.mode_landscape
            })
            AdminItem(label, if (mode == settings.displayMode) "Текущий сохранённый режим" else "Предпросмотр, затем подтверждение. AUTO не определяет физический поворот панели.") { if (mode != settings.displayMode) onOrientation(mode) }
        }
        page == AdminPage.SCHEDULE -> listOf(
            AdminItem("Назад") { navigate(AdminPage.SETTINGS) },
            AdminItem("Импорт JSON — выбрать файл", "Системный файловый диалог (SAF); доступность зависит от прошивки") { onPickFile() },
            AdminItem("Импорт из локальной папки", "incoming-schedule.json · резервный способ через ADB/USB. $inboxPath") { onInbox() },
            AdminItem("Откат к предыдущему расписанию", schedules.previous?.let { "${it.metadata.year} / ${it.metadata.revision}" } ?: "Резервной версии пока нет", schedules.previous != null) { navigate(AdminPage.ROLLBACK) },
            AdminItem("Последний применённый файл", data?.let { "${it.locationName}; ${it.metadata.year}; ${it.metadata.source}; revision=${it.metadata.revision}; approved=${it.metadata.approved}; ${it.days.size} дней; Фаджр=${it.fajrMeaning}" } ?: "Расписание не загружено", false) {},
            AdminItem("Состояние хранилища", schedules.problem ?: if (schedules.loaded) "Проверено; сеть не требуется" else "Загрузка…", false) {},
        )
        page == AdminPage.ROLLBACK -> listOf(
            AdminItem("Отменить") { navigate(AdminPage.SCHEDULE) },
            AdminItem("Подтвердить откат", schedules.previous?.let { "Вернуть ${it.metadata.year} / ${it.metadata.revision}. Текущая версия станет резервной." } ?: "Нет резервной версии", schedules.previous != null) { onRollback(); navigate(AdminPage.SCHEDULE) },
        )
        page == AdminPage.HOME -> listOf(
            AdminItem("Назад") { navigate(AdminPage.SETTINGS) },
            AdminItem("Мастер HOME / kiosk", "Проверка HOME, Device Owner, пробный Lock Task и восстановление Launcher.", onDeviceSetup != null) { onDeviceSetup?.invoke() },
            AdminItem("Выбрать основной HOME-экран", "Откроются настройки Android. Выбор делает администратор; не все TV поддерживают смену HOME.") { onHomeSettings() },
            AdminItem("Открыть другой лаунчер", "Выберите установленный основной экран. Это не отменяет HOME по умолчанию.") { onOtherLauncher() },
            AdminItem("Как отключить автозапуск", "Сначала мастер HOME / kiosk → отключить kiosk → восстановить Launcher. Затем назначьте штатный HOME в Android. Инструкция: docs/TV_SETUP.md.", false) {},
        )
        else -> listOf(
            AdminItem("Назад") { navigate(AdminPage.SETTINGS) },
            AdminItem("Версия $version", "${settings.theme}; ${settings.displayMode}; масштаб=${settings.textScale}; язык=${settings.language}", false) {},
            AdminItem("Время и часовой пояс", "Система: ${Instant.now()} / ${ZoneId.systemDefault().id}. Табло: Europe/Moscow. Ошибочные часы исправляются в настройках устройства.", false) {},
            AdminItem("Открыть настройки даты и времени", "Если часы или дата неверны, исправьте их в Android. Приложение не угадывает правильное время без источника.") { onTimeSettings() },
            AdminItem("Календарь на дату устройства", schedules.scheduleFor(Instant.now().atZone(MoscowZone).toLocalDate())?.let { "${it.metadata.year}; ${it.metadata.source}; ${it.metadata.revision}; approved=${it.metadata.approved}; ${it.days.size} дней; ${it.locationId}" } ?: "Нет утверждённых данных", false) {},
            AdminItem("Офлайн-работа", "Сетевых разрешений нет. Импорт и откат локальные. SAF/USB, HOME, холодный запуск, overscan и 72 часа требуют проверки на целевом ТВ.", false) {},
            AdminItem("Последнее состояние", schedules.problem ?: "Хранилище без зарегистрированных ошибок", false) {},
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(LocalBoardAppearance.current.Deep).testTag("admin-panel")
        .onPreviewKeyEvent { e ->
            if (e.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) false else {
                when (e.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BACK -> back()
                    KeyEvent.KEYCODE_DPAD_DOWN -> selection = (selection + 1) % entries.size
                    KeyEvent.KEYCODE_DPAD_UP -> selection = (selection + entries.size - 1) % entries.size
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> entries[selection.coerceIn(entries.indices)].let { if (it.enabled && !busy) it.action() }
                }
                true
            }
        }.focusRequester(focus).focusable()) {
        val s = if (touchMode) .5f else minOf(maxWidth.value / 1080f, maxHeight.value / 1080f)
        val colors = LocalBoardAppearance.current
        Column(Modifier.fillMaxSize().padding(horizontal = maxWidth * .055f, vertical = maxHeight * .05f)) {
            Text(if (busy) "Выполняется операция…" else if (page == AdminPage.ENTRY) "Вход в настройки" else when (page) {
                AdminPage.ENTRY -> "Вход в настройки"
                AdminPage.SETTINGS -> "Настройки"
                AdminPage.DISPLAY -> "Ориентация экрана"
                AdminPage.SCHEDULE -> "Расписание"
                AdminPage.PREVIEW -> "Проверка импорта"
                AdminPage.ROLLBACK -> "Откат расписания"
                AdminPage.DIAGNOSTICS -> "Диагностика"
                AdminPage.HOME -> "Основной экран HOME"
            }, color = colors.Gold, fontSize = (40 * s).sp, modifier = Modifier.padding(bottom = (20 * s).dp))
            Text(if (touchMode) "Нажмите пункт · Прокрутите список · Назад — отмена" else "↑ ↓ — выбор · OK — действие · BACK — назад", color = colors.Secondary, fontSize = (24 * s).sp, modifier = Modifier.padding(bottom = (20 * s).dp))
            LazyColumn(state = scroll, modifier = Modifier.weight(1f).fillMaxWidth().testTag("admin-list"), verticalArrangement = Arrangement.spacedBy((10 * s).dp)) {
                itemsIndexed(entries) { index, entry ->
                    val active = selection == index
                    val shape = RoundedCornerShape((12 * s).dp)
                    Column(Modifier.fillMaxWidth().semantics { selected = active }.testTag("admin-item-$index")
                        .background(if (active) colors.Gold else colors.Card, shape)
                        .border((2 * s).dp, if (active) colors.Gold else colors.Outline.copy(alpha = .4f), shape)
                        .clickable(enabled = entry.enabled && !busy) { selection = index; entry.action() }
                        .then(if (touchMode) Modifier.heightIn(min = 48.dp) else Modifier).padding((20 * s).dp)) {
                        Text(entry.title, color = if (active) colors.Deep else colors.Text, fontSize = (32 * s).sp, fontWeight = FontWeight.Bold)
                        if (entry.detail.isNotEmpty()) Text(entry.detail, color = if (active) colors.Deep else colors.Secondary, fontSize = (24 * s).sp)
                    }
                }
            }
        }
    }
}
