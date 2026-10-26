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

internal data class SettingsAction(val title: String, val detail: String = "", val enabled: Boolean = true, val checked: Boolean? = null, val informational: Boolean = false, val click: () -> Unit)

@Composable
internal fun SettingsMenu(title: String, actions: List<SettingsAction>, touch: Boolean, busy: Boolean, onBack: () -> Unit, onSave: (() -> Unit)? = null, notice: String? = null, tag: String = "iqamah", focusToken: Any? = null, skipInformation: Boolean = false) {
    var index by remember(title, actions.size) { mutableIntStateOf(actions.indexOfFirst { it.enabled }.takeIf { it >= 0 } ?: actions.size) }
    val focus = remember { FocusRequester() }; val scroll = rememberLazyListState()
    LaunchedEffect(title, focusToken) { focus.requestFocus() }
    LaunchedEffect(index, title) { if (actions.isNotEmpty() && index < actions.size) scroll.scrollToItem(index) }
    BackHandler { if (!busy) onBack() }
    BoxWithConstraints(Modifier.fillMaxSize().background(LocalBoardAppearance.current.Deep).testTag("$tag-panel")
        .onPreviewKeyEvent { e ->
            val key = e.nativeKeyEvent
            val handled = key.keyCode in listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK)
            val count = actions.size + 1 + if (onSave != null) 1 else 0
            if (handled && key.action == KeyEvent.ACTION_DOWN && !busy) when (key.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP -> {
                    val direction = if (key.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) 1 else -1
                    // Information has no action or visible focus. Back always remains reachable.
                    do { index = (index + count + direction) % count }
                    while (skipInformation && index < actions.size && actions[index].informational)
                }
                KeyEvent.KEYCODE_BACK -> onBack()
                else -> when {
                    index < actions.size -> actions[index].let { if (it.enabled) it.click() }
                    index == actions.size -> onBack()
                    else -> onSave?.invoke()
                }
            }
            handled
        }.focusRequester(focus).focusable()) {
        val s = if (touch) .5f else minOf(maxWidth.value / 1080, maxHeight.value / 1080)
        val c = LocalBoardAppearance.current
        Column(Modifier.fillMaxSize().padding(if (touch) 12.dp else (32 * s).dp)) {
            Text(title, color = c.Gold, fontSize = (38 * s).sp, fontWeight = FontWeight.Bold)
            if (notice != null) Text(notice, color = c.Secondary, fontSize = (24 * s).sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (!touch) Text("↑↓ — выбор · OK — действие · BACK — назад", color = c.Secondary, fontSize = (22 * s).sp)
            LazyColumn(state = scroll, modifier = Modifier.weight(1f).fillMaxWidth().testTag("$tag-list"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(actions) { i, a ->
                    val shape = RoundedCornerShape(8.dp)
                    val active = index == i && !a.informational
                    val m = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { this[ImportActionHighlighted] = index == i }
                        .border(if (active) 2.dp else 1.dp, if (active) c.Gold else if (a.informational) androidx.compose.ui.graphics.Color.Transparent else c.Outline.copy(alpha=.25f), shape).background(if (a.informational) androidx.compose.ui.graphics.Color.Transparent else if(active) c.Gold else c.Card, shape)
                    Row((if (a.informational) m else if (a.checked != null) m.toggleable(a.checked, enabled = a.enabled && !busy, role = Role.Checkbox) { index = i; a.click() }
                        else m.clickable(enabled = a.enabled && !busy, role = Role.Button) { index = i; a.click() }).heightIn(min = 48.dp).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (a.checked != null) Checkbox(a.checked, onCheckedChange = null, enabled = a.enabled && !busy)
                        Column(Modifier.weight(1f)) {
                            Text(a.title, color = if(active) c.Deep else if (a.enabled) c.Text else c.Secondary, fontSize = (30 * s).sp, fontWeight = FontWeight.Bold)
                            if (a.detail.isNotEmpty()) Text(a.detail, color = if(active) c.Deep else c.Secondary, fontSize = (24 * s).sp)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.weight(1f).border(if (index == actions.size) 2.dp else 0.dp, c.Gold)) { Text("Назад", color = c.Gold, fontSize = (28*s).sp) }
                if (onSave != null) Button(onClick = onSave, enabled = !busy, modifier = Modifier.weight(1f).testTag("$tag-save").border(if (index > actions.size) 2.dp else 0.dp, c.Gold)) { Text("Сохранить", fontSize = (28*s).sp) }
            }
        }
    }
}

@Composable
internal fun NumberEditor(initial: String, title: String, limit: Int, onSave: (String) -> Unit, onCancel: () -> Unit, touchMode: Boolean, tag: String = "iqamah") {
    var value by remember { mutableStateOf(initial) }
    var selected by remember { mutableIntStateOf(0) }
    val labels = (0..9).map(Int::toString) + listOf("⌫", "Очистить", "Готово", "Отмена")
    fun activate(i: Int) { when (i) { 10 -> value = value.dropLast(1); 11 -> value = ""; 12 -> onSave(value); 13 -> onCancel(); else -> if (value.length < limit) value += labels[i] } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler(onBack = onCancel)
    Column(Modifier.fillMaxSize().background(LocalBoardAppearance.current.Deep).imePadding().padding(16.dp).testTag("$tag-number-editor")
        .onPreviewKeyEvent { e ->
            val k = e.nativeKeyEvent
            if (k.keyCode !in listOf(KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER)) false
            else {
                if (k.action == KeyEvent.ACTION_DOWN) when(k.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> selected = (selected - 3).coerceAtLeast(0)
                    KeyEvent.KEYCODE_DPAD_DOWN -> selected = (selected + 3).coerceAtMost(labels.lastIndex)
                    KeyEvent.KEYCODE_DPAD_LEFT -> selected = (selected + labels.size - 1) % labels.size
                    KeyEvent.KEYCODE_DPAD_RIGHT -> selected = (selected + 1) % labels.size
                    else -> activate(selected)
                }
                true
            }
        }.focusRequester(focus).focusable()) {
        val c = LocalBoardAppearance.current
        Text(title, color = c.Gold, fontSize = 18.sp)
        if (touchMode) OutlinedTextField(value, onValueChange = { value = it.filter(Char::isDigit).take(limit) }, label = { Text("Цифры без разделителей") }, modifier = Modifier.fillMaxWidth().testTag("$tag-number"), singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
        else Text(value.ifEmpty { "Введите цифры" }, color = c.Text, fontSize = 28.sp, modifier = Modifier.testTag("$tag-number"))
        val numberScroll = rememberLazyListState()
        LaunchedEffect(selected) { numberScroll.scrollToItem(selected / 3) }
        LazyColumn(state = numberScroll, modifier = Modifier.weight(1f)) {
            itemsIndexed(labels.chunked(3)) { row, items ->
                Row(Modifier.fillMaxWidth()) { items.forEachIndexed { col, text ->
                    val i = row * 3 + col
                    OutlinedButton(onClick = { activate(i) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).border(if (selected == i) 2.dp else 0.dp, c.Gold)) { Text(text, color = c.Gold) }
                } }
            }
        }
        Row { Button(onClick = { onSave(value) }, modifier = Modifier.weight(1f)) { Text("Применить ввод") }; TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Отмена", color = c.Gold) } }
    }
}
