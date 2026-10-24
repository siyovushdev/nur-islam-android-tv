package dev.nurislam.tv.ui

import android.app.ActivityManager
import android.os.SystemClock
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nurislam.tv.setup.DeviceStatus
import kotlinx.coroutines.delay

// Every system mutation is an explicit action, separate from page navigation.
enum class SetupAction { REFRESH, REQUEST_HOME, HOME_SETTINGS, MANAGED_HOME, PROVISION, START_TRIAL, FINISH_TRIAL, CANCEL_TRIAL, ENABLE_KIOSK, DISABLE_KIOSK, RELEASE_HOME, FINISH, APP_SETTINGS, TEST_HOME }
private enum class SetupPage { CHECK, HOME, KIOSK, PROVISIONING, SUMMARY, CONFIRM_HOME, CONFIRM_RESTORE, CONFIRM_PROVISION, CONFIRM_KIOSK }
private data class SetupItem(val title: String, val detail: String = "", val enabled: Boolean = true, val action: () -> Unit)

/** One scrollable D-pad list in every orientation, including software-rotated televisions. */
@Composable
fun DeviceSetupWizard(
    status: DeviceStatus,
    kioskSaved: Boolean,
    trialDeadline: Long?,
    exitTested: Boolean,
    busy: Boolean,
    message: String?,
    onAction: (SetupAction) -> Unit,
    onDismissMessage: () -> Unit,
) {
    var page by remember { mutableStateOf(SetupPage.CHECK) }
    var selection by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val scroll = rememberLazyListState()
    var seconds by remember { mutableIntStateOf(20) }
    fun go(next: SetupPage) { page = next; selection = 0 }
    fun action(action: SetupAction) { onAction(action) }
    fun back() {
        when {
            trialDeadline != null -> action(SetupAction.CANCEL_TRIAL)
            message != null -> onDismissMessage()
            page == SetupPage.CHECK -> go(SetupPage.SUMMARY)
            page == SetupPage.CONFIRM_KIOSK -> go(SetupPage.KIOSK)
            else -> go(SetupPage.CHECK)
        }
    }
    BackHandler { back() }
    LaunchedEffect(page, message, trialDeadline, exitTested, busy) { selection = 0; focus.requestFocus() }
    LaunchedEffect(selection, page) { scroll.scrollToItem(selection) }
    LaunchedEffect(trialDeadline) {
        if (trialDeadline != null) {
            while (true) {
                val remaining = trialDeadline - SystemClock.elapsedRealtime()
                seconds = ((remaining.coerceAtLeast(0) + 999) / 1000).toInt()
                if (remaining <= 0) { action(SetupAction.CANCEL_TRIAL); delay(1_000) }
                delay(200)
            }
        }
    }
    val locked = status.lockState == ActivityManager.LOCK_TASK_MODE_LOCKED
    val facts = "HOME: ${if (status.home) "Нур Ислам" else "не назначен"} · Device Owner: ${if (status.deviceOwner) "да" else "нет"}\nLock Task: ${when (status.lockState) { ActivityManager.LOCK_TASK_MODE_LOCKED -> "активен"; ActivityManager.LOCK_TASK_MODE_PINNED -> "только закрепление, не kiosk"; else -> "выключен" }} · разрешение DPC: ${if (status.lockPermitted) "есть" else "нет"}"
    val items = when {
        trialDeadline != null -> listOf(
            SetupItem("Отменить пробу и выйти", message ?: "Автовыход через $seconds с. Настройка не сохранена. BACK также отменяет пробу.") { action(SetupAction.CANCEL_TRIAL) },
            SetupItem("Завершить пробу с пульта", "Нажмите ↓ и OK: приложение должно выйти из Lock Task. Затем потребуется отдельное включение.", locked && !busy) { action(SetupAction.FINISH_TRIAL) },
        )
        message != null -> listOf(SetupItem("Понятно", message) { onDismissMessage() })
        page == SetupPage.CHECK -> listOf(
            SetupItem("Далее: основной экран", "1 / 4 · Мастер можно пропустить. Управление: ↑ ↓, OK и BACK.") { go(SetupPage.HOME) },
            SetupItem("Возможности устройства", "Android ${status.androidVersion}\n${status.packageName}\n$facts\nВыбор HOME: ${if (status.roleAvailable || status.homeSettingsAvailable) "системный запрос доступен" else "роль и настройки не обнаружены; зависит от прошивки"}", false) {},
            SetupItem("Обновить проверку", status.problem ?: "Состояние проверяется заново после возврата из Android.") { action(SetupAction.REFRESH) },
            SetupItem("Экран, тема и расписание", "Настройте поворот CW/CCW для физически вертикального ТВ, подписи и локальный JSON. Мастер доступен снова в разделе HOME.") { action(SetupAction.APP_SETTINGS) },
            SetupItem("Пропустить и открыть табло", "Обычный режим без HOME и без kiosk. Мастер: OK пять раз → настройки → HOME.") { action(SetupAction.FINISH) },
        )
        page == SetupPage.HOME -> listOf(
            SetupItem("Продолжить к kiosk", "2 / 4 · $facts") { go(SetupPage.KIOSK) },
            SetupItem("Сделать Нур Ислам главным экраном", if (status.home) "Уже выбран. Можно повторно открыть системный выбор." else if (status.roleAvailable) "Откроется системный запрос HOME. Результат проверим после возврата." else "Откроются настройки HOME. Выберите Нур Ислам → Всегда, если прошивка позволяет.") { action(SetupAction.REQUEST_HOME) },
            SetupItem("Настройки HOME Android", "Текущий обработчик: ${status.homeName}. Роль HOME: ${if (status.roleHeld) "получена" else if (status.roleAvailable) "доступна, не получена" else "недоступна"}. Если запроса нет, используйте этот запасной путь.") { action(SetupAction.HOME_SETTINGS) },
            SetupItem("Назначить HOME через Device Owner", "Только наш Device Owner. Будет создана снимаемая политика HOME.", status.deviceOwner && !status.home) { go(SetupPage.CONFIRM_HOME) },
            SetupItem("Обновить проверку") { action(SetupAction.REFRESH) },
            SetupItem("Назад") { go(SetupPage.CHECK) },
        )
        page == SetupPage.KIOSK -> listOf(
            SetupItem("Далее: итоговая проверка", "3 / 4 · $facts") { go(SetupPage.SUMMARY) },
            SetupItem("Отключить kiosk", "Сначала отключается восстановление kiosk при запуске, затем Lock Task. Выход доступен с пульта всегда.", kioskSaved || locked || status.lockState == ActivityManager.LOCK_TASK_MODE_PINNED || status.deviceOwner) { action(SetupAction.DISABLE_KIOSK) },
            SetupItem("Проверить безопасный выход", "20 секунд. Сначала пробный Lock Task, затем ↓ + OK для выхода. Таймаут/BACK/уход в фон отменяют пробу.", !kioskSaved && !locked && (status.deviceOwner || status.lockPermitted)) { action(SetupAction.START_TRIAL) },
            SetupItem("Включить kiosk при запуске", "Доступно только после успешного выхода из пробы с пульта. Администратор: OK пять раз → настройки → HOME → kiosk → отключить.", exitTested && !kioskSaved) { go(SetupPage.CONFIRM_KIOSK) },
            SetupItem("Управляемая первоначальная настройка", if (status.deviceOwner) "Device Owner уже назначен системе." else "Device Owner нельзя получить кнопкой разрешения после обычной установки.") { go(SetupPage.PROVISIONING) },
            SetupItem("Назад") { go(SetupPage.HOME) },
        )
        page == SetupPage.PROVISIONING -> listOf(
            SetupItem("Назад к kiosk") { go(SetupPage.KIOSK) },
            SetupItem("Device Owner — отдельная установка", "На уже настроенном ТВ это не обычное разрешение. Часто требуется заводской сброс с потерей данных. Приложение никогда не выполняет сброс.", false) {},
            SetupItem("Проверка системы", "Provisioning сейчас разрешён: ${if (status.provisioningAllowed) "да" else "нет"}. Это не подтверждает наличие QR/камеры или совместимость модели. Android 12+ использует только первоначальный setup wizard.", false) {},
            SetupItem("Штатный provisioning Android 8–11", "Только если Android сейчас разрешает первоначальную настройку и системный мастер присутствует. Никакого обхода ограничений.", status.provisioningAllowed && status.legacyProvisioningAvailable && !status.deviceOwner) { go(SetupPage.CONFIRM_PROVISION) },
            SetupItem("Без ADB: порядок действий", "Сначала подтвердите у производителя поддержку managed/dedicated provisioning этой модели. Сохраните данные и расписание. При необходимости сброс делает администратор вручную. В начальной настройке используйте поддерживаемый QR/EMM/способ производителя с подписанным APK и нашим admin-компонентом. Если такого пути нет — используйте обычный HOME.", false) {},
            SetupItem("Инструкция для установщика", "docs/TV_SETUP.md в репозитории содержит компонент DPC, QR-поля и проверку результата. Подтверждённых моделей пока нет; не сбрасывайте ТВ ради эксперимента.", false) {},
        )
        page == SetupPage.SUMMARY -> listOf(
            SetupItem("Сохранить и открыть табло", "4 / 4 · $facts\nМастер не появится при каждом запуске. Расписание, тема и ориентация сохраняются отдельно.") { action(SetupAction.FINISH) },
            SetupItem("Проверить возврат к табло через HOME", "Откроется настоящий HOME Android. Если открылся другой экран, назначение не завершено. При активном kiosk проверку делайте после отключения.", !locked) { action(SetupAction.TEST_HOME) },
            SetupItem("Восстановить штатный Launcher", "Отключить kiosk, снять созданную нами политику HOME и открыть системный выбор. Device Owner останется; сброса нет.") { go(SetupPage.CONFIRM_RESTORE) },
            SetupItem("Аппаратная проверка", "После установки проверьте HOME с пульта, 3 перезагрузки, отключение питания, сон/CEC и 72 часа работы. Автозапуск зависит от прошивки. Приложение не управляет питанием панели.", false) {},
            SetupItem("Начать проверку заново") { go(SetupPage.CHECK) },
        )
        else -> {
            val (title, detail, operation) = when (page) {
                SetupPage.CONFIRM_HOME -> Triple("Подтвердить управляемый HOME", "Наш DPC назначит табло основным экраном. Отмена: мастер → итоговая проверка → восстановить Launcher.", SetupAction.MANAGED_HOME)
                SetupPage.CONFIRM_RESTORE -> Triple("Отключить kiosk и освободить HOME", "Других HOME найдено: ${status.otherHomes.size}. Затем выберите штатный Launcher в Android и подтвердите «Всегда». Если им управляет другой DPC, понадобится его администратор. Не удаляйте приложение до проверки выхода.", SetupAction.RELEASE_HOME)
                SetupPage.CONFIRM_PROVISION -> Triple("Открыть системный provisioning", "Только на согласованном тестовом устройстве с резервной копией! Android может выполнить заводской сброс при ошибке provisioning. Приложение не вызывает сброс. Продолжить штатный мастер?", SetupAction.PROVISION)
                else -> Triple("Подтвердить постоянный kiosk", "Безопасный выход проверен в этой сессии. После включения: OK пять раз → настройки → HOME → kiosk → отключить. Включение сохранится после перезапуска. Это не парольная защита от посетителей.", SetupAction.ENABLE_KIOSK)
            }
            listOf(SetupItem("Отменить") { go(if (page == SetupPage.CONFIRM_KIOSK) SetupPage.KIOSK else SetupPage.CHECK) },
                SetupItem(title, detail) { action(operation); go(if (page == SetupPage.CONFIRM_KIOSK) SetupPage.KIOSK else SetupPage.SUMMARY) })
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(LocalBoardAppearance.current.Deep).testTag("device-setup")
        .onPreviewKeyEvent { e ->
            if (e.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) false else {
                when (e.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BACK -> back()
                    KeyEvent.KEYCODE_DPAD_DOWN -> selection = (selection + 1) % items.size
                    KeyEvent.KEYCODE_DPAD_UP -> selection = (selection + items.size - 1) % items.size
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> items[selection.coerceIn(items.indices)].let { if (!busy && it.enabled) it.action() }
                }
                true
            }
        }.focusRequester(focus).focusable()) {
        val s = minOf(maxWidth.value / 1080, maxHeight.value / 1080)
        val c = LocalBoardAppearance.current
        Column(Modifier.fillMaxSize().padding(horizontal = maxWidth * .055f, vertical = maxHeight * .05f)) {
            Text(if (trialDeadline != null) "Проба выхода · $seconds с" else "Первоначальная настройка", color = c.Gold, fontSize = (40 * s).sp)
            Text(if (busy) "Проверяем Android…" else "↑ ↓ — выбор · OK — действие · BACK — назад", color = c.Secondary, fontSize = (24 * s).sp, modifier = Modifier.padding(vertical = (16 * s).dp))
            LazyColumn(state = scroll, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((10 * s).dp)) {
                itemsIndexed(items) { index, item ->
                    val active = selection == index
                    val shape = RoundedCornerShape((12 * s).dp)
                    Column(Modifier.fillMaxWidth().semantics { selected = active }.testTag("setup-item-$index")
                        .background(if (active) c.Gold else c.Card, shape)
                        .border((2 * s).dp, if (active) c.Gold else c.Outline.copy(alpha = .4f), shape)
                        .clickable(enabled = item.enabled && !busy) { selection = index; item.action() }.padding((20 * s).dp)) {
                        Text(item.title, color = if (active) c.Deep else c.Text, fontSize = (32 * s).sp, fontWeight = FontWeight.Bold)
                        if (item.detail.isNotEmpty()) Text(item.detail, color = if (active) c.Deep else c.Secondary, fontSize = (24 * s).sp)
                    }
                }
            }
            if (kioskSaved || locked) Text("Выход: OK ×5 → настройки → HOME → kiosk → отключить", color = c.Gold, fontSize = (22 * s).sp)
        }
    }
}
