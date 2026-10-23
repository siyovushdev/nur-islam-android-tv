package dev.nurislam.tv

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.nurislam.tv.data.*
import dev.nurislam.tv.domain.*
import dev.nurislam.tv.model.MoscowZone
import dev.nurislam.tv.settings.OrientationPreview
import dev.nurislam.tv.ui.*
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {
    private val homeRequests = mutableIntStateOf(0)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeRequests.intValue++
    }
    private fun fullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) fullscreen() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        fullscreen()
        setContent {
            val factory = remember { viewModelFactory { initializer { BoardViewModel(this@MainActivity.application) } } }
            val vm: BoardViewModel = viewModel(factory = factory)
            val settings by vm.settings.collectAsStateWithLifecycle()
            val schedules by vm.schedules.collectAsStateWithLifecycle()
            val candidate by vm.candidate.collectAsStateWithLifecycle()
            val busy by vm.busy.collectAsStateWithLifecycle()
            val message by vm.message.collectAsStateWithLifecycle()
            var admin by remember { mutableStateOf(false) }
            var showEntryHint by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) { delay(15_000); showEntryHint = false }
            var orientation by remember { mutableStateOf<OrientationPreview?>(null) }
            val mode = orientation?.candidate ?: settings.displayMode
            val focus = remember { FocusRequester() }
            val entry = remember { RemoteEntryGesture() }
            LaunchedEffect(homeRequests.intValue) { admin = false; orientation = null; entry.reset() }
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) { orientation = null; entry.reset() }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            LaunchedEffect(admin, orientation) { if (!admin && orientation == null) focus.requestFocus() }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.import(uri) }
            fun launchSafely(intent: Intent, failure: String) {
                try { startActivity(intent) }
                catch (_: ActivityNotFoundException) { vm.message(failure) }
                catch (_: SecurityException) { vm.message(failure) }
            }
            BackHandler(!admin) { /* HOME board stays visible; settings provide a deliberate exit. */ }
            NurIslamTheme(settings.theme) {
                CompositionLocalProvider(LocalBoardSettings provides settings) {
                    val now = rememberBoardClock()
                    val minute by remember(now) { derivedStateOf { now.value.truncatedTo(ChronoUnit.MINUTES) } }
                    val date = minute.atZone(MoscowZone).toLocalDate()
                    val active = schedules.current
                    val day = remember(active, date) { active?.day(date) }
                    val status = remember(active, minute) { prayerStatus(minute, active) }
                    val next = (status as? PrayerStatus.Upcoming)?.prayer
                    BoxWithConstraints(Modifier.fillMaxSize().background(LocalBoardAppearance.current.Deep)
                        .onPreviewKeyEvent { e ->
                            if (admin) false else {
                                val key = e.nativeKeyEvent
                                when {
                                    key.keyCode == KeyEvent.KEYCODE_MENU && key.action == KeyEvent.ACTION_DOWN -> { admin = true; true }
                                    key.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER) -> {
                                        if (key.action == KeyEvent.ACTION_UP && entry.click(SystemClock.elapsedRealtime())) admin = true
                                        true
                                    }
                                    else -> false
                                }
                            }
                        }.focusRequester(focus).focusable()) {
                        val resolved = resolveDisplayMode(mode, constraints.maxWidth, constraints.maxHeight)
                        RotatedBoard(mode, Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize()) {
                                PrayerBoardScreen(minute, now, day, next, resolved == DisplayMode.LANDSCAPE, false, status = status,
                                    notice = if (schedules.problem != null) stringResource(R.string.storage_notice) else if (showEntryHint) stringResource(R.string.settings_hint) else null)
                                if (admin) AdminPanel(settings, schedules, candidate, busy, message, BuildConfig.VERSION_NAME,
                                    getExternalFilesDir(null)?.absolutePath ?: "Папка недоступна",
                                    focusToken = orientation,
                                    onSettings = { change -> vm.settings(change) },
                                    onOrientation = { orientation = OrientationPreview(it, SystemClock.elapsedRealtime() + 15_000) },
                                    onPickFile = {
                                        try { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                                        catch (_: ActivityNotFoundException) { vm.message("Файловый диалог SAF отсутствует. Используйте импорт из локальной папки; инструкция в docs/TV_OPERATIONS.md.") }
                                        catch (_: SecurityException) { vm.message("Доступ к файловому диалогу запрещён. Доступен импорт из локальной папки.") }
                                    },
                                    onInbox = { vm.importInbox() }, onApply = { vm.applyCandidate() }, onRollback = { vm.rollback() },
                                    onDismissCandidate = vm::dismissCandidate, onDismissMessage = vm::dismissMessage,
                                    onHomeSettings = {
                                        try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                                        catch (_: ActivityNotFoundException) { launchSafely(Intent(Settings.ACTION_SETTINGS), "Системные настройки недоступны. Используйте инструкцию ADB для возврата штатного HOME.") }
                                        catch (_: SecurityException) { vm.message("Открытие настроек запрещено прошивкой. Используйте инструкцию восстановления через ADB.") }
                                    },
                                    onOtherLauncher = { launchSafely(Intent.createChooser(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), "Выберите штатный лаунчер").putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(componentName)), "Нет доступного лаунчера. Откройте системные настройки HOME или используйте ADB.") },
                                    onClose = { admin = false; vm.cancelPendingImport(); vm.dismissCandidate(); vm.dismissMessage() })
                                orientation?.let { preview ->
                                    OrientationConfirmation(preview, onConfirm = {
                                        if (!preview.expired(SystemClock.elapsedRealtime())) vm.settings({ it.copy(displayMode = preview.candidate) }) { orientation = null }
                                    }, onCancel = { orientation = null })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
