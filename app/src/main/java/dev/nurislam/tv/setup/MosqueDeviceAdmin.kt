package dev.nurislam.tv.setup

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

/** Provisioning never enables kiosk or takes over HOME. Consent happens in the TV wizard. */
class MosqueDeviceAdmin : DeviceAdminReceiver() {
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        // Older provisioning flows deliver this callback. Launch only the normal first-run UI.
        if (context.getSystemService(DevicePolicyManager::class.java)?.isDeviceOwnerApp(context.packageName) == true) {
            context.startActivity(Intent(context, dev.nurislam.tv.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** Android 12+ admin-integrated provisioning hooks. Work profiles are deliberately unsupported. */
class ProvisioningActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        if (Build.VERSION.SDK_INT >= 31) {
            when (intent.action) {
                DevicePolicyManager.ACTION_GET_PROVISIONING_MODE -> {
                    val modes = intent.getIntegerArrayListExtra(DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES)
                    if (modes?.contains(DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE) == true) {
                        setResult(RESULT_OK, Intent()
                            .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE)
                            .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, true))
                    }
                }
                DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE -> {
                    // Do not manufacture success if this is an ordinary APK launch/profile owner.
                    if (getSystemService(DevicePolicyManager::class.java)?.isDeviceOwnerApp(packageName) == true) setResult(RESULT_OK)
                }
            }
        }
        finish()
    }
}
