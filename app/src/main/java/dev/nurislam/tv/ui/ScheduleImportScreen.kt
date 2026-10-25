package dev.nurislam.tv.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nurislam.tv.BuildConfig
import dev.nurislam.tv.data.ScheduleState
import dev.nurislam.tv.data.ValidatedSchedule
import dev.nurislam.tv.domain.formatTime
import dev.nurislam.tv.model.MoscowZone
import dev.nurislam.tv.model.ScheduleEvent
import kotlinx.coroutines.launch
import java.time.Instant

// Visual D-pad highlight is not selection/check state. Selected would override
// ToggleableState in Android accessibility and make ordinary buttons checkable.
internal val ImportActionHighlighted = SemanticsPropertyKey<Boolean>("ImportActionHighlighted")

/** A warning based on explicit source labels, never evidence that other sources are official. */
fun isTestScheduleSource(source: String): Boolean = dev.nurislam.tv.data.isTestSource(source)

@Composable
fun ScheduleImportPreview(
    candidate: ValidatedSchedule,
    schedules: ScheduleState,
    busy: Boolean,
    touchMode: Boolean,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    var confirmed by remember(candidate) { mutableStateOf(false) }
    var selection by remember(candidate) { mutableIntStateOf(0) } // Cancel is the safe initial action.
    val focus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val testSource = isTestScheduleSource(candidate.metadata.source)
    val phoneTest = testSource && BuildConfig.PHONE_PREVIEW
    val eligible = candidate.metadata.approved && (!testSource || phoneTest)
    val canApply = eligible && confirmed && !busy
    fun apply() { if (canApply) onApply() }
    fun cancel() { if (!busy) onCancel() }
    LaunchedEffect(candidate) { focus.requestFocus() }
    BackHandler { cancel() }
    val today = Instant.now().atZone(MoscowZone).toLocalDate()
    val date = if (candidate.days.containsKey(today)) today else candidate.days.keys.minOrNull()!!
    val incoming = candidate.day(date)!!
    val current = schedules.scheduleFor(date)?.day(date)
    BoxWithConstraints(Modifier.fillMaxSize().background(LocalBoardAppearance.current.Deep).testTag("import-preview")
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            val handled = key.keyCode in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
            if (handled && key.action == KeyEvent.ACTION_DOWN) when (key.keyCode) {
                KeyEvent.KEYCODE_BACK -> cancel()
                KeyEvent.KEYCODE_DPAD_DOWN -> selection = (selection + 1) % 3
                KeyEvent.KEYCODE_DPAD_UP -> selection = (selection + 2) % 3
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> scope.launch {
                    scroll.animateScrollBy(scroll.viewportSize * if (key.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) .7f else -.7f)
                }
                else -> when (selection) {
                    0 -> cancel()
                    1 -> if (eligible && !busy) confirmed = !confirmed
                    2 -> apply()
                }
            }
            handled
        }.focusRequester(focus).focusable()) {
        val s = if (touchMode) .5f else minOf(maxWidth.value / 1080f, maxHeight.value / 1080f)
        val colors = LocalBoardAppearance.current
        val shape = RoundedCornerShape(8.dp)
        fun actionModifier(index: Int, tag: String) = Modifier.semantics(mergeDescendants = true) { this[ImportActionHighlighted] = selection == index }.testTag(tag)
            .border(if (selection == index) 2.dp else 0.dp, if (selection == index) colors.Gold else colors.Deep, shape)
        Column(Modifier.fillMaxSize().padding(if (touchMode) 12.dp else (32 * s).dp)) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).testTag("import-information")) {
                Text("Проверка расписания", color = colors.Gold, fontSize = (40 * s).sp, fontWeight = FontWeight.Bold)
                Text("${candidate.metadata.year} год · ${candidate.days.size} дней", color = colors.Text, fontSize = (30 * s).sp)
                Text("Источник: ${candidate.metadata.source}", color = colors.Text, fontSize = (26 * s).sp,
                    modifier = Modifier.padding(vertical = 8.dp))
                if (testSource || !candidate.metadata.approved) {
                    Text(if (testSource) "ТЕСТОВЫЙ ИСТОЧНИК — НЕ ОФИЦИАЛЬНЫЙ КАЛЕНДАРЬ" else "ИСТОЧНИК НЕ УТВЕРЖДЁН",
                        color = colors.Gold, fontWeight = FontWeight.Bold, fontSize = (28 * s).sp, modifier = Modifier.testTag("import-source-warning"))
                }
                Text("${candidate.locationName} · Europe/Moscow\nРевизия: ${candidate.metadata.revision} · approved=${candidate.metadata.approved}\nСмысл Фаджра: ${candidate.fajrMeaning}",
                    color = colors.Secondary, fontSize = (24 * s).sp)
                Text("Времена на $date — без корректировок", color = colors.Text, fontSize = (28 * s).sp,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("Событие", color = colors.Secondary, fontSize = (24 * s).sp, modifier = Modifier.weight(1f))
                    Text("Было", color = colors.Secondary, fontSize = (24 * s).sp, modifier = Modifier.width((108 * s).dp))
                    Text("Из файла", color = colors.Gold, fontSize = (24 * s).sp, modifier = Modifier.width((116 * s).dp))
                }
                ScheduleEvent.entries.forEach { event ->
                    HorizontalDivider(color = colors.Outline.copy(alpha = .35f))
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("import-row-${event.name}"), verticalAlignment = Alignment.CenterVertically) {
                        val labels = eventLabels(event)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(labels.first), color = colors.Text, fontSize = (26 * s).sp)
                            Text(stringResource(labels.second), color = colors.Secondary, fontSize = (22 * s).sp)
                        }
                        Text(current?.times?.get(event)?.let(::formatTime) ?: "—", color = colors.Secondary,
                            fontSize = (28 * s).sp, modifier = Modifier.width((108 * s).dp))
                        Text(formatTime(incoming.times.getValue(event)), color = colors.Gold, fontWeight = FontWeight.Bold,
                            fontSize = (28 * s).sp, modifier = Modifier.width((116 * s).dp))
                    }
                }
                Text("Сверьте источник, особенно Фаджр и Иша. approved=true и конвертация из Excel сами по себе не подтверждают утверждение мечетью.",
                    color = colors.Secondary, fontSize = (24 * s).sp, modifier = Modifier.padding(vertical = 8.dp))
                if (candidate.metadata.year != today.year) Text("Год отличается от даты устройства. Без календаря на текущий год табло не покажет времена.",
                    color = colors.Gold, fontSize = (24 * s).sp)
            }
            HorizontalDivider(color = colors.Outline, modifier = Modifier.padding(vertical = 8.dp))
            Column(Modifier.fillMaxWidth().testTag("import-actions")) {
                Row(actionModifier(1, "import-confirmation").fillMaxWidth()
                    .toggleable(value = confirmed, role = Role.Checkbox, enabled = eligible && !busy) { confirmed = it }
                    .heightIn(min = 48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = confirmed, onCheckedChange = null, enabled = eligible && !busy,
                        colors = CheckboxDefaults.colors(checkedColor = colors.Gold, checkmarkColor = colors.Deep, uncheckedColor = colors.Secondary))
                    Text(if (phoneTest) "Понимаю: тестовое расписание не утверждено мечетью" else "Подтверждаю: источник утверждён мечетью", color = if (eligible) colors.Text else colors.Secondary,
                        fontSize = (28 * s).sp, modifier = Modifier.padding(start = 8.dp))
                }
                Text(when {
                    !candidate.metadata.approved -> "Применение запрещено: approved=false. Нужен утверждённый файл."
                    testSource && !phoneTest -> "Тестовый файл можно просмотреть, но нельзя применить для работы мечети."
                    busy -> "Применение расписания…"
                    phoneTest -> if (confirmed) "Будет применено только для демонстрации, не для работы мечети."
                        else "Для демонстрации подтвердите, что понимаете тестовый статус."
                    !confirmed -> "Сначала подтвердите источник выше."
                    else -> if (schedules.current != null) "Готово к применению. Текущая версия сохранится для отката."
                        else "Готово к применению. Первый календарь будет сохранён."
                }, color = colors.Secondary, fontSize = (24 * s).sp, modifier = Modifier.padding(vertical = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = ::cancel, enabled = !busy, shape = shape,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.Gold),
                        modifier = actionModifier(0, "import-cancel").weight(1f).heightIn(min = 48.dp)) {
                        Text("Отменить", fontSize = (28 * s).sp)
                    }
                    Button(onClick = ::apply, enabled = canApply, shape = shape,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.Gold, contentColor = colors.Deep,
                            disabledContainerColor = colors.Secondary.copy(alpha = .16f), disabledContentColor = colors.Secondary.copy(alpha = .5f)),
                        modifier = actionModifier(2, "import-apply").weight(2f).heightIn(min = 48.dp)) {
                        Text("Применить расписание", fontSize = (28 * s).sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (!touchMode) Text("↑↓ — действия · ←→ — прокрутка · OK — выбрать · BACK — отмена",
                    color = colors.Secondary, fontSize = (22 * s).sp)
            }
        }
    }
}

@Composable
fun ScheduleImportResult(schedule: ValidatedSchedule, touchMode: Boolean, onBoard: () -> Unit, onDismiss: () -> Unit, iqamahNotice: String? = null) {
    var selection by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val colors = LocalBoardAppearance.current
    val testSource = isTestScheduleSource(schedule.metadata.source)
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler(onBack = onDismiss)
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.Deep).testTag("import-result")
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            val handled = key.keyCode in listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
            if (handled && key.action == KeyEvent.ACTION_DOWN) when (key.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (selection == 0) onBoard() else onDismiss()
                else -> selection = 1 - selection
            }
            handled
        }.focusRequester(focus).focusable()) {
        val s = if (touchMode) .5f else minOf(maxWidth.value / 1080f, maxHeight.value / 1080f)
        Column(Modifier.fillMaxSize().padding(if (touchMode) 16.dp else (40 * s).dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(if (testSource) "Тестовое расписание применено" else "Расписание применено", color = colors.Gold, fontSize = (40 * s).sp, fontWeight = FontWeight.Bold)
                if (testSource) Text("Не утверждено мечетью. Только для демонстрации.", color = colors.Gold, fontSize = (28 * s).sp)
                Text("Табло обновлено. Сохранено ${schedule.days.size} дней на ${schedule.metadata.year} год.", color = colors.Text, fontSize = (30 * s).sp)
                Text("Источник: ${schedule.metadata.source}", color = colors.Secondary, fontSize = (26 * s).sp, modifier = Modifier.padding(vertical = 12.dp))
                if (iqamahNotice != null) Text(iqamahNotice, color = colors.Gold, fontSize = (28 * s).sp)
                Text("Управление версиями и откат — в разделе «Расписание».", color = colors.Secondary, fontSize = (24 * s).sp)
            }
            listOf("Вернуться к табло", "К расписанию").forEachIndexed { index, label ->
                val modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 48.dp)
                    .semantics(mergeDescendants = true) { this[ImportActionHighlighted] = selection == index }.border(if (selection == index) 2.dp else 0.dp, colors.Gold, RoundedCornerShape(8.dp))
                if (index == 0) Button(onClick = onBoard, modifier = modifier,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.Gold, contentColor = colors.Deep)) { Text(label, fontSize = (28 * s).sp) }
                else OutlinedButton(onClick = onDismiss, modifier = modifier,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.Gold)) { Text(label, fontSize = (28 * s).sp) }
            }
        }
    }
}
