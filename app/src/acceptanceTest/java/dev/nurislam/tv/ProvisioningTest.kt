package dev.nurislam.tv

import android.app.Activity
import android.app.admin.DevicePolicyManager as Dpm
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import dev.nurislam.tv.setup.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 30, 31, 35])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class ProvisioningTest {
    private fun result(intent: Intent, owner: Boolean = false): Pair<Int, Intent?> {
        val activity = Robolectric.buildActivity(ProvisioningActivity::class.java, intent)
        if (owner) shadowOf(activity.get().getSystemService(Dpm::class.java)).setDeviceOwner(ComponentName(activity.get(), MosqueDeviceAdmin::class.java))
        activity.create()
        val shadow = shadowOf(activity.get())
        assertTrue(activity.get().isFinishing)
        return shadow.resultCode to shadow.resultIntent
    }
    @Test fun onlyFullyManagedModeFromSystemAllowedListIsAccepted() {
        val intent = Intent(Dpm.ACTION_GET_PROVISIONING_MODE).putIntegerArrayListExtra(Dpm.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES, arrayListOf(Dpm.PROVISIONING_MODE_FULLY_MANAGED_DEVICE))
        val (code, data) = result(intent)
        if (Build.VERSION.SDK_INT < 31) {
            assertEquals(Activity.RESULT_CANCELED, code) // older devices use the legacy system flow
            return
        }
        assertEquals(Activity.RESULT_OK, code)
        assertEquals(Dpm.PROVISIONING_MODE_FULLY_MANAGED_DEVICE, data!!.getIntExtra(Dpm.EXTRA_PROVISIONING_MODE, -1))
    }
    @Test fun missingAndProfileOnlyModesAreRejected() {
        assertEquals(Activity.RESULT_CANCELED, result(Intent(Dpm.ACTION_GET_PROVISIONING_MODE)).first)
        val intent = Intent(Dpm.ACTION_GET_PROVISIONING_MODE).putIntegerArrayListExtra(Dpm.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES, arrayListOf(Dpm.PROVISIONING_MODE_MANAGED_PROFILE))
        assertEquals(Activity.RESULT_CANCELED, result(intent).first)
    }
    @Test fun complianceRequiresActualOwnerAndUnknownActionIsRejected() {
        assertEquals(Activity.RESULT_CANCELED, result(Intent(Dpm.ACTION_ADMIN_POLICY_COMPLIANCE)).first)
        assertEquals(if (Build.VERSION.SDK_INT >= 31) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            result(Intent(Dpm.ACTION_ADMIN_POLICY_COMPLIANCE), owner = true).first)
        assertEquals(Activity.RESULT_CANCELED, result(Intent("invalid")).first)
    }
}
