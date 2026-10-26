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
fun ScheduleImportPreview(candidate: ValidatedSchedule, schedules: ScheduleState, busy: Boolean, touchMode: Boolean,
    onApply: () -> Unit, onCancel: () -> Unit) {
    val today = Instant.now().atZone(candidate.metadata.timezone).toLocalDate()
    val date = candidate.days.keys.firstOrNull { it == today } ?: candidate.days.keys.minOrNull()!!
    val details = buildString {
        append("Источник: ${candidate.metadata.source}\nВерсия данных: ${candidate.metadata.revision}\n\nВремена на $date — без корректировок\n")
        val names = listOf("Завершение сухура", "Фаджр / в мечети", "Восход", "Зенит", "Зухр", "Аср", "Магриб", "Иша")
        ScheduleEvent.entries.forEachIndexed { i, e -> append("${names[i]}    ${formatTime(candidate.day(date)!!.times.getValue(e))}\n") }
        if (candidate.fajrMeaning == dev.nurislam.tv.data.FajrMeaning.UNCONFIRMED) append("\nФайл не уточняет начало Фаджра. Отсчёт от него не используется.\n")
        candidate.excelNotice?.let { append("\n$it") }
    }
    DocumentPreview("Проверка расписания", "${candidate.mosqueName ?: candidate.locationName}\n${candidate.locationName} · ${candidate.metadata.timezone.id}\n${candidate.metadata.year} год · ${candidate.days.size} дней",
        details, isTestScheduleSource(candidate.metadata.source), busy, touchMode, onApply, onCancel,
        warning = if (candidate.metadata.year != today.year) "Файл на ${candidate.metadata.year} год. Для сегодняшнего табло нужно расписание на ${today.year}." else null,
        tag = "import")
}

/** Content scrolls independently; Apply and Cancel remain visible on short phone windows. */
@Composable
internal fun DocumentPreview(title: String, summary: String, details: String, demo: Boolean, busy: Boolean,
    touch: Boolean, onApply: () -> Unit, onCancel: () -> Unit, warning: String? = null, tag: String = "calendar") {
    var expanded by remember(title, summary) { mutableStateOf(false) }
    var selection by remember(title, summary) { mutableIntStateOf(2) }
    val focus = remember { FocusRequester() }
    val scroll = rememberScrollState(); val scope = rememberCoroutineScope()
    LaunchedEffect(title, summary) { focus.requestFocus() }
    BackHandler { if (!busy) onCancel() }
    val c = LocalBoardAppearance.current
    BoxWithConstraints(Modifier.fillMaxSize().background(c.Deep).testTag("$tag-preview")
        .onPreviewKeyEvent { e ->
            val k = e.nativeKeyEvent
            val handled = k.keyCode in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK)
            if (handled && k.action == KeyEvent.ACTION_DOWN && !busy) when (k.keyCode) {
                KeyEvent.KEYCODE_BACK -> onCancel()
                KeyEvent.KEYCODE_DPAD_DOWN -> selection = (selection + 1) % 3
                KeyEvent.KEYCODE_DPAD_UP -> selection = (selection + 2) % 3
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> scope.launch { scroll.animateScrollBy(scroll.viewportSize * if (k.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) .7f else -.7f) }
                else -> when (selection) { 0 -> expanded = !expanded; 1 -> onCancel(); else -> onApply() }
            }; handled
        }.focusRequester(focus).focusable()) {
        val s = if (touch) .5f else minOf(maxWidth.value / 1080f, maxHeight.value / 1080f)
        fun action(i: Int, name: String) = Modifier.testTag("$tag-$name").semantics { this[ImportActionHighlighted] = selection == i }
            .border(if (selection == i) 2.dp else 0.dp, c.Gold, RoundedCornerShape(8.dp))
        Column(Modifier.fillMaxSize().padding(if (touch) 16.dp else (32*s).dp)) {
            Column(Modifier.weight(1f).verticalScroll(scroll).testTag("$tag-information")) {
                Text(title, color = c.Gold, fontSize = (38*s).sp, fontWeight = FontWeight.Bold)
                Text(summary, color = c.Text, fontSize = (30*s).sp, modifier = Modifier.padding(vertical = 12.dp))
                if (demo) Text("ДЕМОНСТРАЦИОННЫЕ ДАННЫЕ · для проверки интерфейса", color = c.Gold, fontSize = (26*s).sp, modifier = Modifier.testTag("$tag-source-warning"))
                if (warning != null) Text(warning, color = c.Gold, fontSize = (26*s).sp)
                Text("Данные будут показаны как в выбранном файле.", color = c.Secondary, fontSize = (24*s).sp)
                if (expanded) Text(details, color = c.Text, fontSize = (26*s).sp, modifier = Modifier.padding(vertical = 12.dp))
            }
            OutlinedButton(onClick = { expanded = !expanded }, modifier = action(0, "details").fillMaxWidth(), enabled = !busy) { Text(if (expanded) "Скрыть подробности" else "Подробности", color = c.Gold, fontSize = (28*s).sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, enabled = !busy, modifier = action(1, "cancel").weight(1f).heightIn(min = 48.dp)) { Text("Отмена", color = c.Gold, fontSize = (28*s).sp) }
                Button(onClick = onApply, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = c.Gold, contentColor = c.Deep),
                    modifier = action(2, "apply").weight(1f).heightIn(min = 48.dp)) { Text(if (busy) "Применение…" else "Применить", fontWeight = FontWeight.Bold, fontSize = (28*s).sp) }
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
