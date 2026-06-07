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
                if (wifiState == WifiManager.WIFI_STATE_DISABLED || wifiState == WifiManager.WIFI_STATE_DISABLING) {
                    Log.e(TAG, "WiFi was DISABLED! Triggering instant breach.")
                    WiFiMonitoringService.triggerBreachAlert("WiFi Disabled by User/System")
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
