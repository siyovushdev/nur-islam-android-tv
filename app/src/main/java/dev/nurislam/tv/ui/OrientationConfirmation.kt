package dev.nurislam.tv.ui

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nurislam.tv.settings.OrientationPreview
import kotlinx.coroutines.delay

@Composable
fun OrientationConfirmation(preview: OrientationPreview, onConfirm: () -> Unit, onCancel: () -> Unit) {
    var selection by remember(preview) { mutableIntStateOf(0) }
    var remaining by remember(preview) { mutableLongStateOf(preview.remainingSeconds(SystemClock.elapsedRealtime())) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(preview) {
        focus.requestFocus()
        while (!preview.expired(SystemClock.elapsedRealtime())) {
            remaining = preview.remainingSeconds(SystemClock.elapsedRealtime())
            delay(200)
        }
        onCancel()
    }
    BackHandler(onBack = onCancel)
    BoxWithConstraints(Modifier.fillMaxSize().background(LocalBoardAppearance.current.Deep).testTag("orientation-confirm")
        .onPreviewKeyEvent { e ->
            if (e.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) false else {
                when (e.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BACK -> onCancel()
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> selection = 1 - selection
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (selection == 0) onCancel() else onConfirm()
                }
                true
            }
        }.focusRequester(focus).focusable(), contentAlignment = Alignment.Center) {
        val s = minOf(maxWidth.value / 1080f, maxHeight.value / 1080f)
        val colors = LocalBoardAppearance.current
        Column(Modifier.fillMaxWidth(.85f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Сохранить ориентацию?", color = colors.Gold, fontSize = (42 * s).sp)
            Text("${preview.candidate}\nАвтоматический возврат через $remaining с", color = colors.Text, fontSize = (30 * s).sp, modifier = Modifier.padding((24 * s).dp))
            listOf("Вернуть прежний режим", "Подтвердить и сохранить").forEachIndexed { index, title ->
                Text(title, color = if (selection == index) colors.Deep else colors.Text, fontSize = (32 * s).sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = (8 * s).dp).background(if (selection == index) colors.Gold else colors.Card)
                        .clickable { if (index == 0) onCancel() else onConfirm() }.padding((24 * s).dp))
            }
        }
    }
}
