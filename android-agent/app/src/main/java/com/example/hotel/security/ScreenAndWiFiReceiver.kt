package com.example.hotel.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log

class ScreenAndWiFiReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenAndWiFiReceiver"
    }

    // ← FIXED: Flag to prevent duplicate breach alerts on rapid WiFi toggles
    @Volatile
    private var breachAlreadySent = false

    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        context.registerReceiver(this, filter)
        Log.d(TAG, "✅ WiFi broadcast receiver registered") // ← FIXED: Confirm registration
    }

    fun unregister(context: Context) {
        try {
            context.unregisterReceiver(this)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver already unregistered")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // ← FIXED: Acquire wake lock IMMEDIATELY inside onReceive before any work
        // BroadcastReceiver has a very short execution window (~10 seconds).
        // Without a wake lock, Android can kill the process mid-execution during Doze.
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HotelSecurity:ReceiverWakeLock")

        try {
            wl.acquire(10_000L) // ← FIXED: 10 second timeout for safety — auto-releases

            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // ← FIXED: Re-acquire service locks when screen turns off
                    Log.i(TAG, "📱 Screen OFF — max security mode")
                    WiFiMonitoringService.reAcquireWakeLock()
                }

                Intent.ACTION_SCREEN_ON -> {
                    // ← FIXED: Log only, no action needed
                    Log.i(TAG, "📱 Screen ON — normal mode")
                }

                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)

                    when (wifiState) {
                        WifiManager.WIFI_STATE_DISABLING -> {
                            // ← FIXED: DISABLING fires before DISABLED — fastest possible detection
                            if (!breachAlreadySent) {
                                breachAlreadySent = true
                                Log.e(TAG, "🚨 WiFi DISABLING — instant breach!")

                                val serviceIntent = Intent(context, WiFiMonitoringService::class.java).apply {
                                    action = "WIFI_OFF_BREACH"
                                    putExtra("IMMEDIATE_BREACH", true) // ← ADD THIS
                                    putExtra("FORCED_RSSI", -127) // ← ADD THIS
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(serviceIntent)
                                } else {
                                    context.startService(serviceIntent)
                                }

                                // ← FIXED: Also trigger breach alert directly via companion object
                                WiFiMonitoringService.triggerBreachAlert("WiFi Turned OFF (DISABLING state)", -127)
                            } else {
                                Log.d(TAG, "WiFi DISABLING — breach already sent, skipping duplicate.")
                            }
                        }

                        WifiManager.WIFI_STATE_DISABLED -> {
                            // ← FIXED: Backup trigger in case DISABLING was missed (some OEM ROMs)
                            if (!breachAlreadySent) {
                                breachAlreadySent = true
                                Log.e(TAG, "🚨 WiFi DISABLED — backup breach trigger!")

                                val serviceIntent = Intent(context, WiFiMonitoringService::class.java).apply {
                                    action = "WIFI_OFF_BREACH"
                                    putExtra("IMMEDIATE_BREACH", false)
                                    putExtra("FORCED_RSSI", -127) // ← ADD THIS
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(serviceIntent)
                                } else {
                                    context.startService(serviceIntent)
                                }

                                WiFiMonitoringService.triggerBreachAlert("WiFi Turned OFF (DISABLED state)", -127)
                            } else {
                                Log.d(TAG, "WiFi DISABLED — breach already sent, skipping duplicate.")
                            }
                        }

                        WifiManager.WIFI_STATE_ENABLING -> {
                            Log.d(TAG, "WiFi ENABLING — waiting for connection, no action taken.")
                        }

                        WifiManager.WIFI_STATE_ENABLED -> {
                            // ← FIXED: Reset breach flag and breach timer when WiFi comes back ON
                            Log.i(TAG, "✅ WiFi ENABLED — clearing breach")
                            breachAlreadySent = false
                            WiFiMonitoringService.lastBreachTime = 0L
                            WiFiMonitoringService.instance?.sixSignalMonitor?.resetBreachState()
                            
                            // ← Send recovery notification to backend
                            Thread {
                                sendWifiRecovery(context)
                            }.start()
                        }

                        else -> {
                            Log.d(TAG, "WiFi state UNKNOWN ($wifiState) — ignoring.")
                        }
                    }
                }

                @Suppress("DEPRECATION")
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    val noConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)
                    if (noConnectivity) {
                        Log.e(TAG, "No connectivity detected!")
                        WiFiMonitoringService.onNetworkLost()
                    }
                }
            }
        } catch (e: Exception) {
            // ← FIXED: Never crash on any exception inside onReceive
            Log.e(TAG, "Error in onReceive: ${e.message}", e)
        } finally {
            // ← FIXED: Always release wake lock in finally block
            if (wl.isHeld) {
                wl.release()
            }
        }
    }

    private fun sendWifiRecovery(context: Context) {
        try {
            val prefs = context.getSharedPreferences(
                "hotel_prefs", 
                Context.MODE_PRIVATE
            )
            val token = prefs.getString("device_token", "") ?: ""
            val deviceId = prefs.getString("device_id", "") ?: ""
            val roomId = prefs.getString("room_id", "") ?: ""
            
            if (token.isEmpty()) return
            
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            
            // Wait 2 seconds for WiFi to stabilize
            Thread.sleep(2000)
            
            val rssi = try {
                wifiManager.connectionInfo?.rssi ?: -65
            } catch (e: Exception) {
                -65
            }
            
            val backendUrl = prefs.getString(
                "backend_base_url",
                "https://hotel-tablet-security.onrender.com"
            ) ?: "https://hotel-tablet-security.onrender.com"

            val url = java.net.URL("$backendUrl/api/heartbeat")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            
            val body = org.json.JSONObject().apply {
                put("deviceId", deviceId)
                put("roomId", roomId)
                put("rssi", rssi)
                put("wifiBssid", "AA:BB:CC:DD:EE:FF")
                put("battery", 50)
            }.toString()
            
            java.io.OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(body)
                writer.flush()
            }
            
            val code = conn.responseCode
            Log.i(TAG, "✅ WiFi recovery heartbeat sent: $code")
            conn.disconnect()
            
        } catch (e: Exception) {
            Log.e(TAG, "Recovery heartbeat failed: ${e.message}")
        }
    }
}
