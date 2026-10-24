package dev.nurislam.tv

import android.app.ActivityManager.*
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.nurislam.tv.setup.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Policy decisions on all supported generations; no actual device owner is assigned. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 29, 30, 31, 35])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class DeviceSetupControllerTest {
    private lateinit var prefs: SetupPreferences
    private lateinit var device: FakeDevice
    private lateinit var controller: DeviceSetupController
    @Before fun prepare() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val raw = context.getSharedPreferences("controller-test", Context.MODE_PRIVATE)
        raw.edit().clear().commit()
        prefs = SetupPreferences(raw)
        device = FakeDevice()
        controller = DeviceSetupController(device, prefs)
    }
    private suspend fun fails(block: suspend () -> Unit) {
        try { block(); fail("Operation must be refused") } catch (_: IllegalStateException) {} catch (_: SecurityException) {}
    }
    @Test fun ordinaryInstallNeverStartsPinningOrChangesOwnerOrHome() = runTest {
        fails { controller.startTrial() }
        assertEquals(0, device.starts)
        assertFalse(prefs.kiosk)
        assertFalse(prefs.completed)
        assertFalse(device.facts.home)
    }
    @Test fun enabledRequiresSuccessfulRemoteExitInSameSession() = runTest {
        device.facts = device.facts.copy(deviceOwner = true)
        fails { controller.enableKiosk() }
        controller.startTrial()
        assertTrue(prefs.trialPending)
        assertFalse(prefs.kiosk)
        assertEquals(LOCK_TASK_MODE_LOCKED, device.facts.lockState)
        controller.finishTrialByRemote()
        assertEquals(LOCK_TASK_MODE_NONE, device.facts.lockState)
        assertTrue(controller.exitTested)
        controller.enableKiosk()
        assertTrue(prefs.kiosk)
        assertFalse(prefs.trialPending)
        controller.disableKiosk()
        assertFalse(prefs.kiosk)
        assertFalse(device.facts.lockPermitted)
        assertFalse(controller.exitTested)
    }
    @Test fun cancellationAndBackgroundNeverSaveKiosk() = runTest {
        device.facts = device.facts.copy(deviceOwner = true)
        controller.startTrial()
        controller.cancelTrial()
        assertFalse(prefs.kiosk)
        assertFalse(prefs.trialPending)
        assertFalse(controller.exitTested)
        assertEquals(LOCK_TASK_MODE_NONE, device.facts.lockState)
        fails { controller.enableKiosk() }
    }
    @Test fun externalDpcPermissionDoesNotModifyItsPolicy() = runTest {
        device.facts = device.facts.copy(lockPermitted = true)
        controller.startTrial(); controller.finishTrialByRemote(); controller.enableKiosk(); controller.disableKiosk()
        assertEquals(0, device.allowCalls)
        assertEquals(0, device.removeCalls)
        assertTrue(device.facts.lockPermitted)
    }
    @Test fun systemRefusingLockAndPinningAreRolledBack() = runTest {
        device.facts = device.facts.copy(lockPermitted = true)
        device.startMode = LOCK_TASK_MODE_PINNED
        fails { controller.startTrial() }
        assertFalse(prefs.kiosk)
        assertEquals(LOCK_TASK_MODE_NONE, device.facts.lockState)
        assertFalse(prefs.trialPending)
    }
    @Test fun revokedPermissionIsRecheckedBeforeEveryStartAndRestart() = runTest {
        device.facts = device.facts.copy(lockPermitted = true)
        controller.startTrial(); controller.finishTrialByRemote()
        device.facts = device.facts.copy(lockPermitted = false)
        fails { controller.enableKiosk() }
        assertFalse(prefs.kiosk)
        prefs.kiosk(true)
        fails { DeviceSetupController(device, prefs).restoreKiosk() }
        assertFalse(prefs.kiosk)
        assertEquals(1, device.starts)
    }
    @Test fun interruptedTrialIsRecoveredAfterProcessDeathEvenBetweenSaveSteps() = runTest {
        device.facts = device.facts.copy(deviceOwner = true)
        controller.startTrial()
        prefs.kiosk(true) // crash after kiosk commit, before clearing the pending marker
        val restarted = DeviceSetupController(device, prefs)
        restarted.recoverInterruptedTrial()
        restarted.restoreKiosk()
        assertFalse(prefs.kiosk)
        assertFalse(prefs.trialPending)
        assertEquals(LOCK_TASK_MODE_NONE, device.facts.lockState)
        assertFalse(restarted.exitTested)
    }
    @Test fun confirmedKioskRestoresAfterRestartButUnconfirmedDoesNot() = runTest {
        device.facts = device.facts.copy(lockPermitted = true)
        controller.startTrial(); controller.finishTrialByRemote(); controller.enableKiosk()
        device.facts = device.facts.copy(lockState = LOCK_TASK_MODE_NONE)
        val restarted = DeviceSetupController(device, prefs)
        restarted.recoverInterruptedTrial(); restarted.restoreKiosk()
        assertEquals(LOCK_TASK_MODE_LOCKED, device.facts.lockState)
        assertTrue(prefs.kiosk)
    }
    @Test fun stopFailureDisablesRestartAndKeepsRecoveryMarker() = runTest {
        device.facts = device.facts.copy(lockPermitted = true)
        controller.startTrial()
        device.rejectStop = true
        fails { controller.finishTrialByRemote() }
        assertFalse(controller.exitTested)
        assertTrue(prefs.trialPending)
        prefs.kiosk(true)
        fails { controller.disableKiosk() }
        assertFalse(prefs.kiosk)
        assertTrue(prefs.trialPending)
        device.rejectStop = false
        controller.disableKiosk()
        assertFalse(prefs.trialPending)
    }
    @Test fun deniedAllowlistDoesNotFallBackToPinning() = runTest {
        device.facts = device.facts.copy(deviceOwner = true)
        device.rejectAllow = true
        fails { controller.startTrial() }
        assertEquals(0, device.starts)
        assertFalse(prefs.kiosk)
    }
    @Test fun homePolicyIsExplicitOwnedAndReleasedOnlyAfterUnlock() = runTest {
        fails { controller.setManagedHome() }
        device.facts = device.facts.copy(deviceOwner = true)
        controller.setManagedHome()
        assertTrue(device.facts.home)
        assertTrue(prefs.ownsHomePolicy)
        controller.startTrial(); controller.finishTrialByRemote(); controller.enableKiosk()
        controller.releaseHome()
        assertFalse(prefs.kiosk)
        assertFalse(prefs.ownsHomePolicy)
        assertFalse(device.facts.home)
        assertTrue(device.releasedAfterStop)
    }
    @Test fun foreignHomePolicyNeverClearedAndExistingAllowlistNeverRemoved() = runTest {
        device.facts = device.facts.copy(deviceOwner = true, lockPermitted = true, home = true)
        controller.startTrial(); controller.finishTrialByRemote(); controller.enableKiosk(); controller.releaseHome()
        assertEquals(0, device.clearManagedCalls)
        assertEquals(0, device.removeCalls)
        assertTrue(device.facts.lockPermitted)
    }
    @Test fun missingRecoveryLauncherKeepsHomeButDoesNotPreventUnlock() = runTest {
        device.facts = device.facts.copy(deviceOwner = true, homeSettingsAvailable = false)
        controller.setManagedHome()
        fails { controller.releaseHome() }
        assertTrue(prefs.ownsHomePolicy)
        assertTrue(device.facts.home)
        assertFalse(prefs.kiosk)
        assertEquals(0, device.clearManagedCalls)
    }
    @Test fun preferenceFailureStillStopsLockTaskAndDoesNotStartUnmarkedTrial() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val real = context.getSharedPreferences("failing-controller", Context.MODE_PRIVATE)
        val failing = object : android.content.SharedPreferences by real {
            override fun edit(): android.content.SharedPreferences.Editor {
                val editor = real.edit()
                return object : android.content.SharedPreferences.Editor by editor {
                    override fun putBoolean(key: String?, value: Boolean): android.content.SharedPreferences.Editor = this
                    override fun commit() = false
                }
            }
        }
        val broken = DeviceSetupController(device, SetupPreferences(failing))
        device.facts = device.facts.copy(lockPermitted = true)
        fails { broken.startTrial() }
        assertEquals(0, device.starts)
        device.facts = device.facts.copy(lockState = LOCK_TASK_MODE_LOCKED)
        fails { broken.disableKiosk() }
        assertEquals(LOCK_TASK_MODE_NONE, device.facts.lockState)
    }
    @Test fun malformedPreferencesDoNotGrantOrRestoreKiosk() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val raw = context.getSharedPreferences("controller-test", Context.MODE_PRIVATE)
        raw.edit().putString("completed", "true").putString("kiosk", "true").commit()
        assertFalse(prefs.completed)
        assertFalse(prefs.kiosk)
        controller.restoreKiosk()
        assertEquals(0, device.starts)
    }
    @Test fun completedFlagPersistsButFactsAndTrialProofAreNotPreferences() {
        prefs.completed(true)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reopened = SetupPreferences(context.getSharedPreferences("controller-test", Context.MODE_PRIVATE))
        assertTrue(reopened.completed)
        assertFalse(DeviceSetupController(device, reopened).exitTested)
        assertFalse(device.status().deviceOwner)
    }
}

internal class FakeDevice : DeviceAccess {
    var facts = DeviceStatus("test API", "test.package", homeSettingsAvailable = true)
    var starts = 0
    var allowCalls = 0
    var removeCalls = 0
    var clearManagedCalls = 0
    var startMode = LOCK_TASK_MODE_LOCKED
    var rejectStop = false
    var rejectAllow = false
    var releasedAfterStop = false
    override fun status() = facts
    override fun homeRequest() = Intent("test.home")
    override fun legacyProvisioningRequest() = Intent("test.provision")
    override fun allowSelf() { allowCalls++; if (rejectAllow) throw SecurityException("denied"); facts = facts.copy(lockPermitted = true) }
    override fun removeSelf() { removeCalls++; facts = facts.copy(lockPermitted = false) }
    override fun startLock() { starts++; facts = facts.copy(lockState = startMode) }
    override fun stopLock() { if (rejectStop) throw SecurityException("stop denied"); facts = facts.copy(lockState = LOCK_TASK_MODE_NONE) }
    override fun setManagedHome() { facts = facts.copy(home = true) }
    override fun clearManagedHome() { clearManagedCalls++; releasedAfterStop = facts.lockState == LOCK_TASK_MODE_NONE; facts = facts.copy(home = false) }
    override fun clearOrdinaryHome() { releasedAfterStop = facts.lockState == LOCK_TASK_MODE_NONE }
}
