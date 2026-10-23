package dev.nurislam.tv

import android.os.Bundle
import android.view.KeyEvent as AndroidKeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.nurislam.tv.data.*
import dev.nurislam.tv.domain.*
import dev.nurislam.tv.model.MoscowZone
import dev.nurislam.tv.ui.*
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val preferences = getSharedPreferences("display", MODE_PRIVATE)
        val initialMode = runCatching { DisplayMode.valueOf(preferences.getString("mode", "AUTO")!!) }.getOrDefault(DisplayMode.AUTO)
        val repository = buildScheduleRepository()
        setContent {
            NurIslamTheme {
                var mode by remember { mutableStateOf(initialMode) }
                var menu by remember { mutableStateOf(false) }
                var selection by remember { mutableIntStateOf(DisplayMode.entries.indexOf(mode)) }
                var frozen by remember { mutableStateOf(false) }
                val now = rememberBoardClock(frozen && BuildConfig.DEBUG)
                val minute by remember(now) { derivedStateOf { now.value.truncatedTo(ChronoUnit.MINUTES) } }
                val date = minute.atZone(MoscowZone).toLocalDate()
                val day = remember(date) { repository.visibleDay(date, BuildConfig.DEBUG) }
                val next = remember(minute) { if (day == null) null else nextPrayer(minute, listOfNotNull(day, repository.visibleDay(date.plusDays(1), BuildConfig.DEBUG))) }
                val focus = remember { FocusRequester() }
                fun select(index: Int) {
                    when {
                        index < DisplayMode.entries.size -> {
                            mode = DisplayMode.entries[index]
                            preferences.edit().putString("mode", mode.name).apply()
                        }
                        index == DisplayMode.entries.size -> { frozen = !frozen; menu = false }
                        else -> menu = false
                    }
                }
                LaunchedEffect(menu) { focus.requestFocus() }
                BackHandler(menu) { menu = false }
                BoxWithConstraints(Modifier.fillMaxSize().background(BoardColors.Deep).onPreviewKeyEvent { event ->
                    if (!BuildConfig.DEBUG || event.nativeKeyEvent.action != AndroidKeyEvent.ACTION_DOWN) false
                    else when (event.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_M -> { menu = !menu; true }
                        else -> if (menu) {
                            val rotation = when (mode) { DisplayMode.PORTRAIT_ROTATE_CW -> 90f; DisplayMode.PORTRAIT_ROTATE_CCW -> -90f; else -> 0f }
                            val down = when (rotation) { 90f -> AndroidKeyEvent.KEYCODE_DPAD_LEFT; -90f -> AndroidKeyEvent.KEYCODE_DPAD_RIGHT; else -> AndroidKeyEvent.KEYCODE_DPAD_DOWN }
                            val up = when (rotation) { 90f -> AndroidKeyEvent.KEYCODE_DPAD_RIGHT; -90f -> AndroidKeyEvent.KEYCODE_DPAD_LEFT; else -> AndroidKeyEvent.KEYCODE_DPAD_UP }
                            when (event.nativeKeyEvent.keyCode) {
                                AndroidKeyEvent.KEYCODE_BACK -> menu = false
                                AndroidKeyEvent.KEYCODE_DPAD_DOWN, down -> selection = (selection + 1) % 7
                                AndroidKeyEvent.KEYCODE_DPAD_UP, up -> selection = (selection + 6) % 7
                                AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> select(selection)
                            }
                            true
                        } else false
                    }
                }.focusRequester(focus).focusable()) {
                    val resolved = resolveDisplayMode(mode, constraints.maxWidth, constraints.maxHeight)
                    RotatedBoard(mode, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            PrayerBoardScreen(minute, now, day, next, resolved == DisplayMode.LANDSCAPE,
                                repository.metadata?.approved == false && BuildConfig.DEBUG, frozen, BuildConfig.DEBUG)
                            if (menu && BuildConfig.DEBUG) DebugMenu(mode, selection, frozen, ::select)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugMenu(mode: DisplayMode, selection: Int, frozen: Boolean, onSelect: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(BoardColors.Deep.copy(alpha = .95f)), contentAlignment = Alignment.Center) {
        val scale = minOf(maxWidth.value / 1080f, maxHeight.value / 1080f)
        Column(Modifier.fillMaxWidth(.88f).border(2.dp, BoardColors.Gold, RoundedCornerShape(16.dp)).padding((32 * scale).dp)) {
            Text(stringResource(R.string.debug_menu), color = BoardColors.Gold, fontSize = (30 * scale).sp, modifier = Modifier.padding(bottom = (24 * scale).dp))
            val labels = listOf(R.string.mode_auto, R.string.mode_native, R.string.mode_cw, R.string.mode_ccw, R.string.mode_landscape,
                if (frozen) R.string.debug_live else R.string.debug_freeze, R.string.close)
            labels.forEachIndexed { index, label ->
                Text((if (index < 5 && DisplayMode.entries[index] == mode) "✓ " else "") + stringResource(label),
                    color = if (selection == index) BoardColors.Deep else BoardColors.Text,
                    fontSize = (28 * scale).sp,
                    modifier = Modifier.fillMaxWidth().background(if (selection == index) BoardColors.Gold else BoardColors.Card)
                        .clickable { onSelect(index) }.padding((20 * scale).dp))
            }
        }
    }
}
