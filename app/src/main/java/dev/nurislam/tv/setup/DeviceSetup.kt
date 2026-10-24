package dev.nurislam.tv.setup

import android.app.Activity
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import dev.nurislam.tv.MainActivity
import kotlinx.coroutines.delay

/** Live facts, never restored from preferences as if they were system permissions. */
data class DeviceStatus(
    val androidVersion: String,
    val packageName: String,
    val home: Boolean = false,
    val homeName: String = "Не выбран / неизвестен",
    val roleAvailable: Boolean = false,
    val roleHeld: Boolean = false,
    val homeSettingsAvailable: Boolean = false,
    val deviceOwner: Boolean = false,
    val lockPermitted: Boolean = false,
    val lockState: Int = ActivityManager.LOCK_TASK_MODE_NONE,
    val provisioningAllowed: Boolean = false,
    val legacyProvisioningAvailable: Boolean = false,
    val otherHomes: List<ComponentName> = emptyList(),
    val problem: String? = null,
)

/** Small boundary for testing safety decisions without granting real device privileges. */
interface DeviceAccess {
    fun status(): DeviceStatus
    fun homeRequest(): Intent
    fun legacyProvisioningRequest(): Intent
    fun allowSelf()
    fun removeSelf()
    fun startLock()
    fun stopLock()
    fun setManagedHome()
    fun clearManagedHome()
    fun clearOrdinaryHome()
}

class SetupPreferences(private val prefs: SharedPreferences) {
    private fun flag(key: String) = try { prefs.getBoolean(key, false) } catch (_: ClassCastException) { false }
    val completed get() = flag("completed")
    val kiosk get() = flag("kiosk")
    val trialPending get() = flag("trial_pending")
    val ownsAllowlistEntry get() = flag("own_allowlist")
    val ownsHomePolicy get() = flag("own_home")
    fun completed(value: Boolean) = save("completed", value)
    fun kiosk(value: Boolean) = save("kiosk", value)
    fun trialPending(value: Boolean) = save("trial_pending", value)
    fun ownsAllowlistEntry(value: Boolean) = save("own_allowlist", value)
    fun ownsHomePolicy(value: Boolean) = save("own_home", value)
    private fun save(key: String, value: Boolean) { check(prefs.edit().putBoolean(key, value).commit()) { "Не удалось записать настройки устройства. Повторите действие; после перезапуска может вернуться прежняя настройка." } }
    companion object { fun from(context: Context) = SetupPreferences(context.getSharedPreferences("device-setup", Context.MODE_PRIVATE)) }
}

/** Only explicit administrator actions change policy. No owner grant, reset or screen pinning. */
class DeviceSetupController(val access: DeviceAccess, val preferences: SetupPreferences) {
    var trialDeadline: Long? = null
        private set
    var exitTested: Boolean = false
        private set
    private var starting = false

    suspend fun startTrial() {
        check(trialDeadline == null && !starting) { "Проба уже запущена" }
        check(access.status().lockState == ActivityManager.LOCK_TASK_MODE_NONE) { "Сначала отключите существующий Lock Task / закрепление" }
        exitTested = false
        starting = true
        try {
            val s = access.status()
            check(s.problem == null) { "Полномочия Android не проверены: ${s.problem}" }
            if (!s.lockPermitted && s.deviceOwner) {
                // Record before the policy call, so interrupted/failed calls can be cleaned up.
                preferences.ownsAllowlistEntry(true)
                access.allowSelf()
            }
            check(access.status().lockPermitted) { "DPC не разрешил Lock Task. Обычное закрепление экрана не запускается." }
            // Set deadline BEFORE start: lifecycle interruption must be able to undo it.
            preferences.trialPending(true)
            trialDeadline = SystemClock.elapsedRealtime() + 20_000
            startVerified()
        } catch (e: Exception) {
            cancelTrial()
            throw e
        } finally { starting = false }
    }

    suspend fun finishTrialByRemote() {
        check(trialDeadline?.let { SystemClock.elapsedRealtime() < it } == true) { "Время пробы истекло" }
        val facts = access.status()
        check(facts.problem == null && facts.lockState == ActivityManager.LOCK_TASK_MODE_LOCKED) { "Настоящий Lock Task не подтверждён системой" }
        stopVerified()
        preferences.trialPending(false)
        trialDeadline = null
        exitTested = true
    }

    suspend fun enableKiosk() {
        check(exitTested) { "Сначала успешно завершите пробу выхода с пульта" }
        check(!preferences.kiosk) { "Kiosk уже сохранён" }
        // A crash before confirmation must not enable automatic locking on next launch.
        trialDeadline = SystemClock.elapsedRealtime() + 20_000
        try {
            preferences.trialPending(true)
            startVerified()
            preferences.kiosk(true)
            preferences.trialPending(false)
            trialDeadline = null
            exitTested = false
        } catch (e: Exception) {
            try { preferences.kiosk(false) } finally { cancelTrial() }
            throw e
        }
    }

    suspend fun recoverInterruptedTrial() {
        if (!preferences.trialPending) return
        try { preferences.kiosk(false) } finally { stopVerified() }
        preferences.trialPending(false)
    }

    suspend fun restoreKiosk() {
        if (!preferences.kiosk) return
        try { startVerified() }
        catch (e: Exception) {
            try { preferences.kiosk(false) } finally { access.stopLock() }
            throw e
        }
    }

    private suspend fun startVerified() {
        val facts = access.status()
        check(facts.problem == null && facts.lockPermitted) { "Разрешение Lock Task отсутствует, отозвано или не подтверждено" }
        if (facts.lockState == ActivityManager.LOCK_TASK_MODE_LOCKED) return
        check(facts.lockState == ActivityManager.LOCK_TASK_MODE_NONE) { "Закрепление экрана не является kiosk" }
        access.startLock()
        repeat(10) {
            val state = access.status()
            if (state.problem == null && state.lockState == ActivityManager.LOCK_TASK_MODE_LOCKED) return
            delay(100)
        }
        error("Android не подтвердил Lock Task; выполнен безопасный выход")
    }

    private suspend fun stopVerified() {
        access.stopLock()
        repeat(10) {
            val state = access.status()
            if (state.problem == null && state.lockState == ActivityManager.LOCK_TASK_MODE_NONE) return
            delay(100)
        }
        error("Android не подтвердил выход. Обратитесь к администратору DPC; повторите отключение.")
    }

    /** Synchronous best effort on background/destroy; trial is never persistent. */
    fun cancelTrial() {
        if (trialDeadline != null) {
            // Leave the deadline armed if stop throws: the next resume/timer retries.
            access.stopLock()
            val state = access.status()
            check(state.problem == null && state.lockState == ActivityManager.LOCK_TASK_MODE_NONE) { "Выход ещё не подтверждён. Повторите отключение kiosk." }
            preferences.trialPending(false)
            trialDeadline = null
            exitTested = false
        }
    }

    suspend fun disableKiosk() {
        // Attempt durable opt-out first, but a disk failure must NEVER prevent unlocking.
        try { preferences.kiosk(false) } finally { stopVerified() }
        preferences.trialPending(false)
        trialDeadline = null
        exitTested = false
        if (preferences.ownsAllowlistEntry && access.status().deviceOwner) {
            access.removeSelf()
            preferences.ownsAllowlistEntry(false)
        }
    }

    fun setManagedHome() {
        val facts = access.status()
        check(facts.problem == null && facts.deviceOwner) { "Требуется подтверждённый Device Owner нашего приложения" }
        preferences.ownsHomePolicy(true)
        access.setManagedHome()
        check(access.status().home) { "Android пока не подтвердил HOME. Обновите диагностику." }
    }

    suspend fun releaseHome() {
        disableKiosk()
        val facts = access.status()
        check(facts.problem == null) { "HOME не освобождён: диагностика Android недоступна. Повторите проверку." }
        check(facts.otherHomes.isNotEmpty() || facts.homeSettingsAvailable) { "Другой Launcher и настройки HOME не обнаружены. Kiosk отключён; HOME пока сохранён. Включите штатный Launcher средствами производителя." }
        if (preferences.ownsHomePolicy) {
            check(access.status().deviceOwner) { "Политику HOME должен снять управляющий DPC" }
            access.clearManagedHome()
            preferences.ownsHomePolicy(false)
        }
        access.clearOrdinaryHome()
    }
}

@Suppress("DEPRECATION") // Legacy provisioning is gated to API 26–30; constants used for diagnostics on newer APIs.
class AndroidDeviceAccess(private val activity: Activity) : DeviceAccess {
    private val context get() = activity
    private val pkg get() = context.packageName
    private val pm get() = context.packageManager
    private val dpm get() = context.getSystemService(DevicePolicyManager::class.java)
    private val admin get() = ComponentName(context, MosqueDeviceAdmin::class.java)
    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    private fun available(intent: Intent) = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
    override fun status(): DeviceStatus {
        var result = DeviceStatus("${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}", pkg)
        return try {
            val info = pm.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            val roles = if (Build.VERSION.SDK_INT >= 29) context.getSystemService(RoleManager::class.java) else null
            val roleAvailable = Build.VERSION.SDK_INT >= 29 && roles?.isRoleAvailable(RoleManager.ROLE_HOME) == true
            val homes = pm.queryIntentActivities(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
                .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
                .filter { it.packageName != pkg }.distinct()
            result = result.copy(
                home = info?.packageName == pkg,
                homeName = info?.let { "${it.packageName}/${it.name}" } ?: result.homeName,
                roleAvailable = roleAvailable,
                roleHeld = Build.VERSION.SDK_INT >= 29 && roleAvailable && roles?.isRoleHeld(RoleManager.ROLE_HOME) == true,
                homeSettingsAvailable = available(Intent(Settings.ACTION_HOME_SETTINGS)),
                otherHomes = homes,
            )
            result.copy(
                deviceOwner = dpm?.isDeviceOwnerApp(pkg) == true,
                lockPermitted = dpm?.isLockTaskPermitted(pkg) == true,
                lockState = context.getSystemService(ActivityManager::class.java)?.lockTaskModeState ?: ActivityManager.LOCK_TASK_MODE_NONE,
                provisioningAllowed = dpm?.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE) == true,
                legacyProvisioningAvailable = Build.VERSION.SDK_INT < 31 && available(legacyProvisioningRequest()),
            )
        } catch (e: RuntimeException) { result.copy(lockState = -1, problem = "Проверка Android недоступна: ${e.javaClass.simpleName}. Полномочия не подтверждены.") }
    }
    override fun homeRequest(): Intent {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles?.isRoleAvailable(RoleManager.ROLE_HOME) == true) return roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }
    override fun legacyProvisioningRequest() = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE)
        .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, admin)
        .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, true)
    override fun allowSelf() { dpm.setLockTaskPackages(admin, (dpm.getLockTaskPackages(admin).toList() + pkg).distinct().toTypedArray()) }
    override fun removeSelf() { dpm.setLockTaskPackages(admin, dpm.getLockTaskPackages(admin).filter { it != pkg }.toTypedArray()) }
    override fun startLock() {
        check(dpm?.isLockTaskPermitted(pkg) == true) { "DPC не разрешил Lock Task. Закрепление экрана не запускается." }
        check(context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == false) { "Сначала разблокируйте Android. Kiosk поверх экрана блокировки не запускается; повторите настройку после разблокировки." }
        activity.startLockTask()
    }
    override fun stopLock() = activity.stopLockTask()
    override fun setManagedHome() {
        val filter = IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME); addCategory(Intent.CATEGORY_DEFAULT) }
        dpm.addPersistentPreferredActivity(admin, filter, ComponentName(context, MainActivity::class.java))
    }
    override fun clearManagedHome() = dpm.clearPackagePersistentPreferredActivities(admin, pkg)
    override fun clearOrdinaryHome() = pm.clearPackagePreferredActivities(pkg)
}
