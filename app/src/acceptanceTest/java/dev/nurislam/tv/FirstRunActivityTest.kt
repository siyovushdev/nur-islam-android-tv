package dev.nurislam.tv

import android.app.Activity
import android.content.Intent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nurislam.tv.setup.SetupPreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Production wizard, also run against Release. No real device provisioning is performed. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FirstRunActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun key(key: Key, count: Int = 1) = compose.onRoot().performKeyInput { repeat(count) { pressKey(key) } }
    private fun openWizard() {
        key(Key.DirectionCenter)
        compose.onNodeWithTag("admin-list").performScrollToNode(hasText("Запуск при включении телевизора"))
        compose.onNodeWithText("Запуск при включении телевизора").performClick()
    }
    @org.junit.Before fun openOptionalWizard() {
        compose.onNodeWithTag("device-setup").assertDoesNotExist()
        openWizard()
    }
    @Test fun skipWithRemotePersistsAndWizardCanBeReopenedFromSettings() {
        compose.onNodeWithTag("device-setup").assertIsDisplayed()
        key(Key.DirectionUp); key(Key.Enter)
        compose.onNodeWithTag("device-setup").assertDoesNotExist()
        assertTrue(SetupPreferences.from(compose.activity).completed)
        assertFalse(SetupPreferences.from(compose.activity).kiosk)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("device-setup").assertDoesNotExist()
        openWizard()
        compose.onNodeWithTag("device-setup").assertIsDisplayed()
    }
    @Test fun homeDenialAndEvenOkResultCannotForgeHomeOrDeviceOwner() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val vendor = android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply { packageName = "vendor.launcher"; name = "vendor.launcher.Home" }
        }
        shadowOf(compose.activity.packageManager).setResolveInfosForIntent(home, listOf(vendor))
        key(Key.Enter) // HOME
        key(Key.DirectionDown); key(Key.Enter)
        val shadow = shadowOf(compose.activity)
        val request = shadow.nextStartedActivityForResult
        assertNotNull(request)
        assertTrue(request.intent.action in listOf("android.app.role.action.REQUEST_ROLE", "android.settings.HOME_SETTINGS"))
        compose.runOnIdle { shadow.receiveResult(request.intent, Activity.RESULT_OK, Intent()) }
        compose.onNodeWithText("HOME пока не назначен.", substring = true).assertIsDisplayed()
        key(Key.Enter)
        compose.onNodeWithText("Продолжить к kiosk").assertIsDisplayed()
        key(Key.Enter)
        // No DO or allowlisting: trial and persistent kiosk are disabled, normal board is usable.
        compose.onNodeWithText("Проверить безопасный выход").performClick()
        assertFalse(SetupPreferences.from(compose.activity).kiosk)
        key(Key.Enter); key(Key.Enter)
        compose.onNodeWithTag("device-setup").assertDoesNotExist()
    }
    @Test fun provisioningExplainsRestrictionsAndDoesNotLaunchOnConfiguredDevice() {
        key(Key.Enter); key(Key.Enter) // HOME -> KIOSK
        key(Key.DirectionDown, 4); key(Key.Enter)
        compose.onNodeWithText("Device Owner — отдельная установка").assertIsDisplayed()
        key(Key.DirectionDown, 3); key(Key.Enter) // unavailable provisioning row
        assertNull(shadowOf(compose.activity).nextStartedActivityForResult)
        assertFalse(SetupPreferences.from(compose.activity).kiosk)
        key(Key.Back)
        compose.onNodeWithTag("device-setup").assertIsDisplayed()
    }
    private fun makeTestOwner() {
        // Robolectric-only policy grant; never run against connected devices.
        val dpm = compose.activity.getSystemService(android.app.admin.DevicePolicyManager::class.java)
        shadowOf(dpm).setDeviceOwner(android.content.ComponentName(compose.activity, dev.nurislam.tv.setup.MosqueDeviceAdmin::class.java))
        key(Key.DirectionDown, 2); key(Key.Enter) // refresh real adapter
        key(Key.DirectionUp, 2) // refresh retains its highlighted row
        key(Key.Enter); key(Key.Enter) // HOME -> kiosk
    }
    private fun lockState() = compose.activity.getSystemService(android.app.ActivityManager::class.java).lockTaskModeState
    @Test fun actualActivityTrialConfirmationRestartAndRemoteExit() {
        makeTestOwner()
        key(Key.DirectionDown, 2); key(Key.Enter)
        compose.onNodeWithText("Завершить пробу с пульта").assertIsDisplayed()
        assertEquals(android.app.ActivityManager.LOCK_TASK_MODE_LOCKED, lockState())
        key(Key.DirectionDown); key(Key.Enter)
        assertEquals(android.app.ActivityManager.LOCK_TASK_MODE_NONE, lockState())
        key(Key.DirectionDown, 3); key(Key.Enter)
        key(Key.DirectionDown); key(Key.Enter)
        assertTrue(SetupPreferences.from(compose.activity).kiosk)
        // Explicit kiosk consent also survives a restart before the final wizard page.
        compose.activityRule.scenario.recreate()
        assertEquals(android.app.ActivityManager.LOCK_TASK_MODE_LOCKED, lockState())
        compose.onNodeWithTag("device-setup").assertDoesNotExist()
        openWizard()
        key(Key.Back); key(Key.Enter) // summary -> completed board
        compose.onNodeWithTag("device-setup").assertDoesNotExist()
        // Simulate system losing the task on process/activity restart, then restore from saved consent.
        shadowOf(compose.activity.getSystemService(android.app.ActivityManager::class.java)).setLockTaskModeState(android.app.ActivityManager.LOCK_TASK_MODE_NONE)
        compose.activityRule.scenario.recreate()
        assertEquals(android.app.ActivityManager.LOCK_TASK_MODE_LOCKED, lockState())
        openWizard()
        key(Key.Enter); key(Key.Enter) // wizard -> HOME -> kiosk
        key(Key.DirectionDown); key(Key.Enter)
        assertFalse(SetupPreferences.from(compose.activity).kiosk)
        assertEquals(android.app.ActivityManager.LOCK_TASK_MODE_NONE, lockState())
    }
    @Test fun backgroundDuringActualTrialExitsAndNeverRestoresIt() {
        makeTestOwner()
        key(Key.DirectionDown, 2); key(Key.Enter)
        assertTrue(SetupPreferences.from(compose.activity).trialPending)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        assertFalse(SetupPreferences.from(compose.activity).trialPending)
        assertEquals(android.app.ActivityManager.LOCK_TASK_MODE_NONE, lockState())
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        assertFalse(SetupPreferences.from(compose.activity).kiosk)
        compose.onNodeWithText("Далее: итоговая проверка").assertIsDisplayed()
    }
    private fun bootWithSavedKioskAndKeyguard() {
        compose.runOnIdle {
            val dpm = compose.activity.getSystemService(android.app.admin.DevicePolicyManager::class.java)
            val admin = android.content.ComponentName(compose.activity, dev.nurislam.tv.setup.MosqueDeviceAdmin::class.java)
            shadowOf(dpm).setDeviceOwner(admin)
            dpm.setLockTaskPackages(admin, arrayOf(compose.activity.packageName))
            SetupPreferences.from(compose.activity).apply { completed(true); kiosk(true) }
            shadowOf(compose.activity.getSystemService(android.app.KeyguardManager::class.java)).setKeyguardLocked(true)
        }
        compose.activityRule.scenario.recreate()
        assertTrue(SetupPreferences.from(compose.activity).kiosk)
        assertEquals(android.app.ActivityManager.LOCK_TASK_MODE_NONE, lockState())
    }
    @Test fun savedKioskRetriesOnUserPresentWithoutActivityRecreation() {
        bootWithSavedKioskAndKeyguard()
        compose.runOnIdle {
            shadowOf(compose.activity.getSystemService(android.app.KeyguardManager::class.java)).setKeyguardLocked(false)
            compose.activity.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        }
        compose.waitForIdle()
        assertEquals(android.app.ActivityManager.LOCK_TASK_MODE_LOCKED, lockState())
        assertTrue(SetupPreferences.from(compose.activity).kiosk)
        compose.onNodeWithText("Android пока не готов", substring = true).assertDoesNotExist()
    }
    @Test fun savedKioskRetriesOnResumeAndNotWhileStopped() {
        bootWithSavedKioskAndKeyguard()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.runOnUiThread {
            shadowOf(compose.activity.getSystemService(android.app.KeyguardManager::class.java)).setKeyguardLocked(false)
            compose.activity.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        }
        assertEquals(android.app.ActivityManager.LOCK_TASK_MODE_NONE, lockState())
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertEquals(android.app.ActivityManager.LOCK_TASK_MODE_LOCKED, lockState())
        assertTrue(SetupPreferences.from(compose.activity).kiosk)
    }
    @Test fun revocationWhileWaitingForUnlockDoesNotStartPinningOnLaterEvents() {
        bootWithSavedKioskAndKeyguard()
        compose.runOnIdle {
            val dpm = compose.activity.getSystemService(android.app.admin.DevicePolicyManager::class.java)
            dpm.setLockTaskPackages(android.content.ComponentName(compose.activity, dev.nurislam.tv.setup.MosqueDeviceAdmin::class.java), emptyArray())
            shadowOf(compose.activity.getSystemService(android.app.KeyguardManager::class.java)).setKeyguardLocked(false)
            compose.activity.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        }
        compose.waitForIdle()
        assertFalse(SetupPreferences.from(compose.activity).kiosk)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        assertEquals(android.app.ActivityManager.LOCK_TASK_MODE_NONE, lockState())
    }
    @Test fun homeTestCompletesSetupAndLaunchesTheRealHomeIntent() {
        key(Key.Back) // summary
        key(Key.DirectionDown); key(Key.Enter)
        assertTrue(SetupPreferences.from(compose.activity).completed)
        compose.onNodeWithTag("device-setup").assertDoesNotExist()
        val intent = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_HOME))
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("device-setup").assertDoesNotExist()
    }
    @Test fun homeReturnDoesNotBypassUnfinishedWizardAndBackIsSafe() {
        compose.runOnIdle { androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(compose.activity, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) }
        compose.onNodeWithTag("device-setup").assertIsDisplayed()
        key(Key.Back)
        compose.onNodeWithText("Сохранить и открыть табло").assertIsDisplayed()
        key(Key.Enter)
        assertTrue(SetupPreferences.from(compose.activity).completed)
    }
}
