package com.example.hotel.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * BootReceiver — restarts the monitoring service after a tablet reboot.
 *
 * Why this is necessary:
 *   Services are killed when the device reboots. Without a BootReceiver, the
 *   heartbeat monitoring would not resume until a user opens the app — which
 *   never happens on locked-down hotel tablets. This receiver fires automatically
 *   when Android has finished booting and restarted all core services.
 *
 * Why startForegroundService() and not startService()?
 *   On API 26+ (our minSdk is 29) background service starts are blocked unless
 *   the service is started as a foreground service. startForegroundService() tells
 *   Android we intend to call startForeground() within 5 seconds of the service
 *   starting, which WiFiMonitoringService does in onCreate().
 *
 * Why check isProvisioned?
 *   On a fresh install before ProvisioningActivity completes there is no JWT token
 *   or room assignment. Starting the service at that point would cause repeated
 *   failed heartbeat POSTs. We guard against this by checking the provisioned flag
 *   that ProvisioningActivity sets in SharedPreferences.
 *
 * Manifest requirement:
 *   <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
 *   <receiver android:name=".security.BootReceiver" android:exported="true"
 *       android:enabled="true">
 *       <intent-filter>
 *           <action android:name="android.intent.action.BOOT_COMPLETED" />
 *           <action android:name="android.intent.action.QUICKBOOT_POWERON" />
 *       </intent-filter>
 *   </receiver>
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val PREFS_NAME = "hotel_prefs"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // Ignore any other intents that may be routed here
            return
        }

        Log.i(TAG, "Boot completed — checking provisioning state before starting services")

        // Guard: only start the service if the device has been provisioned.
        // If not provisioned, SharedPreferences won't have a jwt_token and the
        // service would send unauthenticated requests that the backend rejects.
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isProvisioned = prefs.getBoolean("provisioned", false)

        if (!isProvisioned) {
            Log.w(TAG, "Device not provisioned — skipping service start on boot")
            return
        }

        Log.i(TAG, "Device is provisioned — starting WiFiMonitoringService")

        val serviceIntent = Intent(context, WiFiMonitoringService::class.java)

        // startForegroundService() is required on API 26+ (minSdk 29).
        // WiFiMonitoringService.onCreate() calls startForeground() within 5 s.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        Log.i(TAG, "✅ WiFiMonitoringService started after boot")
    }
}
