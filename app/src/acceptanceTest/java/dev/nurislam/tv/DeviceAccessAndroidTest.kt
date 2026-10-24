package dev.nurislam.tv

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
import dev.nurislam.tv.setup.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Actual Android adapter/manifest exercised with Robolectric framework shadows, not a real TV. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 29, 30, 31, 35])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class DeviceAccessAndroidTest {
    private fun withDevice(block: (Activity, AndroidDeviceAccess) -> Unit) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        try { block(activity.get(), AndroidDeviceAccess(activity.get())) } finally { activity.pause().stop().destroy() }
    }
    @Test fun requestUsesHomeRoleOn29OrLaterAndSettingsFallbackElsewhere() = withDevice { activity, device ->
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = activity.getSystemService(RoleManager::class.java)
            shadowOf(roles).addAvailableRole(RoleManager.ROLE_HOME)
            assertTrue(device.status().roleAvailable)
            assertEquals("android.app.role.action.REQUEST_ROLE", device.homeRequest().action)
            shadowOf(roles).removeAvailableRole(RoleManager.ROLE_HOME)
        }
        assertEquals(Settings.ACTION_HOME_SETTINGS, device.homeRequest().action)
    }
    @Test fun actualHomeResolverNotResultCodeDeterminesStatus() = withDevice { activity, device ->
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val pm = shadowOf(activity.packageManager)
        fun resolve(pkg: String) = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = pkg; name = "$pkg.Home" } }
        pm.setResolveInfosForIntent(home, listOf(resolve("vendor.launcher")))
        assertFalse(device.status().home)
        assertTrue(device.status().otherHomes.any { it.packageName == "vendor.launcher" })
        pm.setResolveInfosForIntent(home, listOf(resolve(activity.packageName)))
        assertTrue(device.status().home)
        pm.setResolveInfosForIntent(home, listOf(resolve("vendor.launcher")))
        assertFalse(device.status().home)
    }
    @Test fun deviceAdminIsNotDeviceOwnerAndPolicyPreservesOtherPackages() = withDevice { activity, device ->
        val dpm = activity.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(activity, MosqueDeviceAdmin::class.java)
        shadowOf(dpm).setActiveAdmin(admin)
        assertFalse(device.status().deviceOwner)
        assertFalse(device.status().lockPermitted)
        shadowOf(dpm).setDeviceOwner(admin)
        assertTrue(device.status().deviceOwner)
        dpm.setLockTaskPackages(admin, arrayOf("existing.allowed"))
        device.allowSelf()
        assertEquals(setOf("existing.allowed", activity.packageName), dpm.getLockTaskPackages(admin).toSet())
        assertTrue(device.status().lockPermitted)
        device.removeSelf()
        assertArrayEquals(arrayOf("existing.allowed"), dpm.getLockTaskPackages(admin))
        assertFalse(device.status().lockPermitted)
    }
    @Test fun legacyRequestUsesCorrectAdminAndIsNeverAdvertisedOnAndroid12() = withDevice { activity, device ->
        @Suppress("DEPRECATION") val action = DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE
        val intent = device.legacyProvisioningRequest()
        assertEquals(action, intent.action)
        @Suppress("DEPRECATION") val admin = intent.getParcelableExtra<ComponentName>(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME)
        assertEquals(ComponentName(activity, MosqueDeviceAdmin::class.java), admin)
        assertTrue(intent.getBooleanExtra(DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, false))
        if (Build.VERSION.SDK_INT >= 31) assertFalse(device.status().legacyProvisioningAvailable)
        val receiver = activity.packageManager.getReceiverInfo(ComponentName(activity, MosqueDeviceAdmin::class.java), 0)
        assertEquals("android.permission.BIND_DEVICE_ADMIN", receiver.permission)
        val provisioning = activity.packageManager.getActivityInfo(ComponentName(activity, ProvisioningActivity::class.java), 0)
        assertEquals("android.permission.BIND_DEVICE_ADMIN", provisioning.permission)
    }
    @Test fun keyguardMustBeUnlockedBeforeStartingRealActivityLockTask() = withDevice { activity, device ->
        try { device.startLock(); fail("Adapter must also refuse missing DPC permission") } catch (_: IllegalStateException) {}
        val dpm = activity.getSystemService(DevicePolicyManager::class.java)
        shadowOf(dpm).setDeviceOwner(ComponentName(activity, MosqueDeviceAdmin::class.java))
        device.allowSelf()
        val keyguard = activity.getSystemService(android.app.KeyguardManager::class.java)
        shadowOf(keyguard).setKeyguardLocked(true)
        try { device.startLock(); fail("Must refuse to lock over keyguard") } catch (_: IllegalStateException) {}
        assertEquals(android.app.ActivityManager.LOCK_TASK_MODE_NONE, device.status().lockState)
        shadowOf(keyguard).setKeyguardLocked(false)
        // The authorized adapter call reaches the Activity framework shadow only after unlocking.
        device.startLock()
        assertEquals(android.app.ActivityManager.LOCK_TASK_MODE_LOCKED, device.status().lockState)
        device.stopLock()
        assertEquals(android.app.ActivityManager.LOCK_TASK_MODE_NONE, device.status().lockState)
    }
    @Test fun savedKioskSurvivesRealKeyguardAndRestoresAfterUnlock() = withDevice { activity, device ->
        val dpm = activity.getSystemService(DevicePolicyManager::class.java)
        shadowOf(dpm).setDeviceOwner(ComponentName(activity, MosqueDeviceAdmin::class.java))
        device.allowSelf()
        val prefs = SetupPreferences.from(activity)
        prefs.kiosk(true)
        val controller = DeviceSetupController(device, prefs)
        val keyguard = activity.getSystemService(android.app.KeyguardManager::class.java)
        shadowOf(keyguard).setKeyguardLocked(true)
        kotlinx.coroutines.runBlocking {
            try { controller.restoreKiosk(); fail("Keyguard must defer kiosk") } catch (_: IllegalStateException) {}
            assertTrue(SetupPreferences.from(activity).kiosk)
            assertEquals(android.app.ActivityManager.LOCK_TASK_MODE_NONE, device.status().lockState)
            shadowOf(keyguard).setKeyguardLocked(false)
            controller.restoreKiosk()
            assertTrue(prefs.kiosk)
            assertEquals(android.app.ActivityManager.LOCK_TASK_MODE_LOCKED, device.status().lockState)
            controller.disableKiosk()
        }
    }
    @Test fun revokedRealDpcPermissionDisablesSavedKioskWithoutPinning() = withDevice { activity, device ->
        val dpm = activity.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(activity, MosqueDeviceAdmin::class.java)
        shadowOf(dpm).setDeviceOwner(admin)
        device.allowSelf()
        val prefs = SetupPreferences.from(activity)
        prefs.kiosk(true)
        dpm.setLockTaskPackages(admin, emptyArray())
        kotlinx.coroutines.runBlocking {
            try { DeviceSetupController(device, prefs).restoreKiosk(); fail("Revoked permission must refuse") } catch (_: IllegalStateException) {}
        }
        assertFalse(prefs.kiosk)
        assertEquals(android.app.ActivityManager.LOCK_TASK_MODE_NONE, device.status().lockState)
    }

}
