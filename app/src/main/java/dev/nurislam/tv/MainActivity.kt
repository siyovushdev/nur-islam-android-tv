package dev.nurislam.tv

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
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
import dev.nurislam.tv.setup.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class MainActivity : ComponentActivity() {
    private lateinit var deviceSetup: DeviceSetupController
    private var setupOpen by mutableStateOf(false)
    private var deviceStatus by mutableStateOf(DeviceStatus("", ""))
    private var setupMessage by mutableStateOf<String?>(null)
    private var setupBusy by mutableStateOf(false)
    private var setupRevision by mutableIntStateOf(0)
    private var setupJob: Job? = null
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) retryKioskRestore()
        }
    }
    private val homePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshDevice() // RESULT_OK is not evidence that HOME was actually assigned.
        setupMessage = if (deviceStatus.home) "Нур Ислам назначен HOME. Проверьте перезапуск на вашем ТВ." else "HOME пока не назначен. Запрос отклонён или прошивка не разрешила изменение. Доступны настройки HOME и обычный режим."
    }
    private val provisioningPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshDevice()
        setupMessage = if (deviceStatus.deviceOwner) "Device Owner подтверждён. HOME и kiosk включаются отдельно в мастере." else "Device Owner не подтверждён. Системный provisioning отменён, недоступен или ещё не завершён."
    }
    private fun refreshDevice() {
        deviceStatus = deviceSetup.access.status()
        setupRevision++
    }
    private fun setupOperation(block: suspend () -> Unit) {
        if (setupBusy) return
        setupJob = lifecycleScope.launch {
            setupBusy = true
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { setupMessage = e.message ?: "Android не выполнил действие: ${e.javaClass.simpleName}" }
            finally { setupBusy = false; refreshDevice() }
        }
    }
    private fun openHomeSettings() {
        try { homePicker.launch(Intent(Settings.ACTION_HOME_SETTINGS)) }
        catch (_: ActivityNotFoundException) {
            // Ordinary HOME intent invokes the platform resolver if no default is set.
            try { homePicker.launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) }
            catch (_: RuntimeException) { setupMessage = "Системный выбор HOME отсутствует. Откройте настройки приложений ТВ вручную. Смена HOME зависит от прошивки." }
        }
        catch (_: SecurityException) { setupMessage = "Прошивка запрещает настройки HOME. Назначение не подтверждено." }
    }
    override fun onResume() {
        super.onResume()
        if (::deviceSetup.isInitialized) {
            refreshDevice()
            retryKioskRestore()
        }
    }
    private fun retryKioskRestore() {
        // One bounded attempt per resume/unlock event; never interfere with a live setup/trial.
        if (setupBusy || deviceSetup.trialDeadline != null) return
        setupOperation {
            deviceSetup.recoverInterruptedTrial()
            if (deviceSetup.preferences.kiosk) {
                deviceSetup.restoreKiosk()
                setupMessage = null
            }
        }
    }
    override fun onStart() {
        super.onStart()
        if (!BuildConfig.PHONE_PREVIEW) ContextCompat.registerReceiver(this, unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onStop() {
        if (!BuildConfig.PHONE_PREVIEW) unregisterReceiver(unlockReceiver)
        if (::deviceSetup.isInitialized && deviceSetup.trialDeadline != null) {
            setupJob?.cancel()
            try { deviceSetup.cancelTrial() }
            catch (e: RuntimeException) { setupMessage = "Не удалось завершить пробу: ${e.message}. Повторите отключение kiosk." }
            refreshDevice()
        }
        super.onStop()
    }
    private val homeRequests = mutableIntStateOf(0)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeRequests.intValue++
    }
    private fun fullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            if (BuildConfig.PHONE_PREVIEW) show(WindowInsetsCompat.Type.systemBars())
            else {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) fullscreen() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.PHONE_PREVIEW) {
            deviceSetup = DeviceSetupController(AndroidDeviceAccess(this), SetupPreferences.from(this))
            setupOpen = !deviceSetup.preferences.completed
            refreshDevice()
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        fullscreen()
        setContent {
            val factory = remember { viewModelFactory { initializer { BoardViewModel(this@MainActivity.application) } } }
            val vm: BoardViewModel = viewModel(factory = factory)
            val settings by vm.settings.collectAsStateWithLifecycle()
            val schedules by vm.schedules.collectAsStateWithLifecycle()
            val candidate by vm.candidate.collectAsStateWithLifecycle()
            val appliedSchedule by vm.appliedSchedule.collectAsStateWithLifecycle()
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
            LaunchedEffect(admin, orientation, setupOpen) { if (!admin && orientation == null && !setupOpen) focus.requestFocus() }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.import(uri) }
            fun launchSafely(intent: Intent, failure: String) {
                try { startActivity(intent) }
                catch (_: ActivityNotFoundException) { vm.message(failure) }
                catch (_: SecurityException) { vm.message(failure) }
            }
            BackHandler(!admin && !BuildConfig.PHONE_PREVIEW) { /* HOME board stays visible; settings provide a deliberate exit. */ }
            NurIslamTheme(settings.theme) {
                CompositionLocalProvider(LocalBoardSettings provides settings) {
                    val now = rememberBoardClock()
                    val minute by remember(now) { derivedStateOf { now.value.truncatedTo(ChronoUnit.MINUTES) } }
                    val date = minute.atZone(MoscowZone).toLocalDate()
                    val active = remember(schedules, date) { schedules.scheduleFor(date) }
                    val testSchedule = active != null && isTestScheduleSource(active.metadata.source)
                    val day = remember(active, date) { active?.day(date) }
                    val status = remember(schedules, minute) { prayerStatus(minute, schedules) }
                    val next = (status as? PrayerStatus.Upcoming)?.prayer
                    BoxWithConstraints(Modifier.fillMaxSize().background(LocalBoardAppearance.current.Deep)
                        .then(if (BuildConfig.PHONE_PREVIEW) Modifier.safeDrawingPadding() else Modifier)
                        .onPreviewKeyEvent { e ->
                            if (admin || setupOpen) false else {
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
                        val board: @Composable () -> Unit = {
                            PrayerBoardScreen(minute, now, day, next, resolved == DisplayMode.LANDSCAPE, false, status = status,
                                notice = if (setupMessage != null && !setupOpen) setupMessage else if (schedules.problem != null) stringResource(R.string.storage_notice) else if (testSchedule) "Тестовый источник · Не официальное расписание" else if (showEntryHint && !BuildConfig.PHONE_PREVIEW) stringResource(R.string.settings_hint) else null)
                        }
                        val overlays: @Composable () -> Unit = {
                            if (admin && !setupOpen) AdminPanel(settings, schedules, candidate, busy, message, BuildConfig.VERSION_NAME,
                                getExternalFilesDir(null)?.absolutePath ?: "Папка недоступна",
                                focusToken = orientation, touchMode = BuildConfig.PHONE_PREVIEW, appliedSchedule = appliedSchedule,
                                onSettings = { change -> vm.settings(change) },
                                onOrientation = { orientation = OrientationPreview(it, SystemClock.elapsedRealtime() + 15_000) },
                                onPickFile = {
                                    try { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                                    catch (_: ActivityNotFoundException) { vm.message("Файловый диалог SAF отсутствует. Используйте импорт из локальной папки; инструкция в docs/TV_OPERATIONS.md.") }
                                    catch (_: SecurityException) { vm.message("Доступ к файловому диалогу запрещён. Доступен импорт из локальной папки.") }
                                },
                                onInbox = { vm.importInbox() }, onApply = { vm.applyCandidate() }, onRollback = { vm.rollback() },
                                onDismissCandidate = vm::dismissCandidate, onDismissMessage = vm::dismissMessage,
                                onTimeSettings = { launchSafely(Intent(Settings.ACTION_DATE_SETTINGS), "Настройки даты и времени недоступны. Откройте системные настройки вручную.") },
                                onDeviceSetup = if (BuildConfig.PHONE_PREVIEW) null else ({ refreshDevice(); setupOpen = true }),
                                onHomeSettings = {
                                    try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                                    catch (_: ActivityNotFoundException) { launchSafely(Intent(Settings.ACTION_SETTINGS), "Системные настройки недоступны. Используйте инструкцию ADB для возврата штатного HOME.") }
                                    catch (_: SecurityException) { vm.message("Открытие настроек запрещено прошивкой. Используйте инструкцию восстановления через ADB.") }
                                },
                                onOtherLauncher = { launchSafely(Intent.createChooser(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), "Выберите штатный лаунчер").putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(componentName)), "Нет доступного лаунчера. Откройте системные настройки HOME или используйте ADB.") },
                                onClose = {
                                    admin = false
                                    vm.cancelPendingImport()
                                    vm.dismissCandidate()
                                    vm.dismissMessage()
                                })
                            if (setupOpen) {
                                // Revision also tracks non-persistent trial/proof state of the controller.
                                @Suppress("UNUSED_VARIABLE") val revision = setupRevision
                                DeviceSetupWizard(deviceStatus, deviceSetup.preferences.kiosk, deviceSetup.trialDeadline,
                                    deviceSetup.exitTested, setupBusy, setupMessage,
                                    onDismissMessage = { setupMessage = null },
                                    onAction = { action ->
                                        setupOperation {
                                            when (action) {
                                                SetupAction.REFRESH -> Unit
                                                SetupAction.REQUEST_HOME -> {
                                                    try { homePicker.launch(deviceSetup.access.homeRequest()) }
                                                    catch (_: RuntimeException) { openHomeSettings() }
                                                }
                                                SetupAction.HOME_SETTINGS -> openHomeSettings()
                                                SetupAction.MANAGED_HOME -> deviceSetup.setManagedHome()
                                                SetupAction.START_TRIAL -> deviceSetup.startTrial()
                                                SetupAction.FINISH_TRIAL -> deviceSetup.finishTrialByRemote()
                                                SetupAction.CANCEL_TRIAL -> deviceSetup.cancelTrial()
                                                SetupAction.ENABLE_KIOSK -> deviceSetup.enableKiosk()
                                                SetupAction.DISABLE_KIOSK -> deviceSetup.disableKiosk()
                                                SetupAction.RELEASE_HOME -> { deviceSetup.releaseHome(); openHomeSettings() }
                                                SetupAction.PROVISION -> {
                                                    val facts = deviceSetup.access.status()
                                                    check(facts.provisioningAllowed && facts.legacyProvisioningAvailable && !facts.deviceOwner) { "Первоначальный provisioning сейчас недоступен. Используйте согласованный путь производителя при начальной настройке устройства." }
                                                    provisioningPicker.launch(deviceSetup.access.legacyProvisioningRequest())
                                                }
                                                SetupAction.FINISH -> { deviceSetup.preferences.completed(true); setupOpen = false; admin = false }
                                                SetupAction.APP_SETTINGS -> { deviceSetup.preferences.completed(true); setupOpen = false; admin = true }
                                                SetupAction.TEST_HOME -> { deviceSetup.preferences.completed(true); setupOpen = false; admin = false; startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) }
                                            }
                                        }
                                    })
                            }
                            orientation?.let { preview ->
                                OrientationConfirmation(preview, touchMode = BuildConfig.PHONE_PREVIEW, onConfirm = {
                                    if (!preview.expired(SystemClock.elapsedRealtime())) vm.settings({ it.copy(displayMode = preview.candidate) }) { orientation = null }
                                }, onCancel = { orientation = null })
                            }
                        }
                        if (BuildConfig.PHONE_PREVIEW) {
                            Column(Modifier.fillMaxSize()) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text("Нур Ислам · тест", color = LocalBoardAppearance.current.Secondary,
                                        fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    TextButton(onClick = {
                                        orientation = null
                                        if (admin) {
                                            admin = false
                                            vm.cancelPendingImport()
                                            vm.dismissCandidate()
                                            vm.dismissMessage()
                                        } else {
                                            admin = true
                                        }
                                    }, modifier = Modifier.testTag("phone-settings")) {
                                        Text(if (admin) "Табло" else "Настройки", color = LocalBoardAppearance.current.Gold, fontSize = 16.sp)
                                    }
                                }
                                if (testSchedule && !admin && !setupOpen) {
                                    Text("ТЕСТОВОЕ РАСПИСАНИЕ", color = LocalBoardAppearance.current.Deep,
                                        fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        modifier = Modifier.fillMaxWidth().background(LocalBoardAppearance.current.Gold)
                                            .padding(horizontal = 12.dp, vertical = 8.dp).testTag("test-schedule-banner"))
                                }
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    PhoneBoardViewport(mode) {
                                        RotatedBoard(mode, Modifier.fillMaxSize(), board)
                                    }
                                    overlays()
                                }
                            }
                        } else {
                            RotatedBoard(mode, Modifier.fillMaxSize()) {
                                Box(Modifier.fillMaxSize()) { board(); overlays() }
                            }
                        }
                    }
                }
            }
        }
    }
}
