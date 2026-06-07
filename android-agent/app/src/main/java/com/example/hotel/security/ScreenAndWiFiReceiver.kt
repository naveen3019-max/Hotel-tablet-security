package com.example.hotel.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.util.Log

class ScreenAndWiFiReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenAndWiFiReceiver"
    }

    // ← FIXED: Added flag to prevent duplicate breach alerts on rapid WiFi toggles
    @Volatile
    private var breachAlreadySent = false

    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        context.registerReceiver(this, filter)
    }

    fun unregister(context: Context) {
        try {
            context.unregisterReceiver(this)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver already unregistered")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.i(TAG, "Screen OFF detected. Switching to HIGH FREQUENCY monitoring.")
                WiFiMonitoringService.setMonitoringInterval(500L, "Screen Off")
            }
            Intent.ACTION_SCREEN_ON -> {
                Log.i(TAG, "Screen ON detected. Switching to NORMAL monitoring.")
                WiFiMonitoringService.setMonitoringInterval(2000L, "Screen On")
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.i(TAG, "Device unlocked. Normal operations resumed.")
                WiFiMonitoringService.setMonitoringInterval(2000L, "User Present")
            }
            WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)

                // ← FIXED: Handle each WiFi state separately with correct breach logic
                when (wifiState) {
                    WifiManager.WIFI_STATE_DISABLING -> {
                        // ← FIXED: DISABLING fires before DISABLED — fastest possible detection
                        if (!breachAlreadySent) {
                            breachAlreadySent = true // ← FIXED: Set flag before sending to prevent race condition
                            Log.e(TAG, "WiFi DISABLING detected — triggering INSTANT breach alert!")
                            WiFiMonitoringService.triggerBreachAlert("WiFi Turned OFF (DISABLING state)")
                        } else {
                            Log.d(TAG, "WiFi DISABLING — breach already sent, skipping duplicate.")
                        }
                    }
                    WifiManager.WIFI_STATE_DISABLED -> {
                        // ← FIXED: Backup trigger in case DISABLING was missed (e.g., some OEM ROMs)
                        if (!breachAlreadySent) {
                            breachAlreadySent = true
                            Log.e(TAG, "WiFi DISABLED detected — triggering backup breach alert!")
                            WiFiMonitoringService.triggerBreachAlert("WiFi Turned OFF (DISABLED state)")
                        } else {
                            Log.d(TAG, "WiFi DISABLED — breach already sent, skipping duplicate.")
                        }
                    }
                    WifiManager.WIFI_STATE_ENABLING -> {
                        // ← FIXED: Do nothing while enabling — don't alert, just log
                        Log.d(TAG, "WiFi ENABLING — waiting for connection, no action taken.")
                    }
                    WifiManager.WIFI_STATE_ENABLED -> {
                        // ← FIXED: Reset flag when WiFi comes back ON, resume normal monitoring
                        // Previously this was incorrectly triggering a breach alert
                        Log.d(TAG, "WiFi ENABLED — resetting breach flag, resuming normal monitoring.")
                        breachAlreadySent = false // ← FIXED: Reset so next WiFi-OFF triggers correctly
                        WiFiMonitoringService.setMonitoringInterval(2000L, "WiFi Re-enabled")
                    }
                    else -> {
                        Log.d(TAG, "WiFi state UNKNOWN ($wifiState) — ignoring.")
                    }
                }
            }
            ConnectivityManager.CONNECTIVITY_ACTION -> {
                val noConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)
                if (noConnectivity) {
                    Log.e(TAG, "No connectivity detected!")
                    WiFiMonitoringService.onNetworkLost()
                }
            }
        }
    }
}
