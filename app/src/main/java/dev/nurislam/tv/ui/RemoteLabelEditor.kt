package dev.nurislam.tv.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** In-app keyboard remains correctly rotated on TVs where the system IME cannot rotate. */
@Composable
fun RemoteLabelEditor(initial: String, maxLength: Int, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    var latin by remember { mutableStateOf(false) }
    var lower by remember { mutableStateOf(false) }
    var selection by remember { mutableIntStateOf(0) }
    val alphabet = if (latin) "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,·-()/" else "АӘБВГДЕЁЖҖЗИЙКЛМНҢОӨПРСТУҮФХҺЦЧШЩЪЫЬЭЮЯ0123456789.,·-()/"
    val letters = if (lower) alphabet.lowercase() else alphabet
    val keys = letters.map(Char::toString) + listOf("⌫", "Пробел", "Язык", "а/А", "Очистить", "Готово", "Отмена")
    val focus = remember { FocusRequester() }
    val grid = rememberLazyGridState()
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(selection) { grid.scrollToItem(selection.coerceIn(keys.indices)) }
    BackHandler(onBack = onCancel)
    fun activate(index: Int) {
        when (index - letters.length) {
            0 -> value = value.dropLast(1)
            1 -> if (value.length < maxLength) value += " "
            2 -> { latin = !latin; selection = 0 }
            3 -> lower = !lower
            4 -> value = ""
            5 -> if (value.isNotBlank()) onSave(value.trim())
            6 -> onCancel()
            else -> if (index in letters.indices && value.length < maxLength) value += letters[index]
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(LocalBoardAppearance.current.Deep).testTag("label-editor")
        .onPreviewKeyEvent { e ->
            if (e.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) false else {
                when (e.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BACK -> onCancel()
                    KeyEvent.KEYCODE_DPAD_DOWN -> selection = (selection + 10).coerceAtMost(keys.lastIndex)
                    KeyEvent.KEYCODE_DPAD_UP -> selection = (selection - 10).coerceAtLeast(0)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> selection = (selection + 1) % keys.size
                    KeyEvent.KEYCODE_DPAD_LEFT -> selection = (selection + keys.size - 1) % keys.size
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> activate(selection.coerceIn(keys.indices))
                }
                true
            }
        }.focusRequester(focus).focusable()) {
        val s = minOf(maxWidth.value / 1080f, maxHeight.value / 1080f)
        val colors = LocalBoardAppearance.current
        Column(Modifier.fillMaxSize().padding(maxWidth * .06f)) {
            Text("Название / местоположение", color = colors.Gold, fontSize = (38 * s).sp)
            Text(value.ifEmpty { "…" }, color = colors.Text, fontSize = (40 * s).sp, modifier = Modifier.fillMaxWidth().padding(vertical = (20 * s).dp).testTag("label-value"))
            Text("${value.length}/$maxLength · D-pad + OK · Готово — сохранить · BACK — отменить", color = colors.Secondary, fontSize = (22 * s).sp)
            Spacer(Modifier.height((20 * s).dp))
            LazyVerticalGrid(columns = GridCells.Fixed(10), state = grid, modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy((6 * s).dp), verticalArrangement = Arrangement.spacedBy((6 * s).dp)) {
                itemsIndexed(keys) { index, text ->
                    Box(Modifier.height((90 * s).dp).background(if (selection == index) colors.Gold else colors.Card)
                        .clickable { selection = index; activate(index) }, contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.text.BasicText(text,
                            style = androidx.compose.ui.text.TextStyle(color = if (selection == index) colors.Deep else colors.Text, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                            maxLines = 1, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = (10 * s).coerceAtLeast(6f).sp, maxFontSize = (32 * s).coerceAtLeast(6f).sp), modifier = Modifier.padding((4 * s).dp))
                    }
                }
            }
        }
    }
}
