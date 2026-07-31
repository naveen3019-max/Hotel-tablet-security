package com.example.hotel.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.net.NetworkInfo // ← NEW: for network state checking
import java.net.URL
import java.net.HttpURLConnection
import org.json.JSONObject
import java.io.OutputStreamWriter

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
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION) // ← NEW: listen for network change
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
        val action = intent.action ?: return
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HotelSecurity::ScreenAndWiFiReceiver"
        )
        wl.acquire(3000)
        try {
            when (action) {
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                    when (wifiState) {
                        WifiManager.WIFI_STATE_DISABLING -> {
                            Log.e(TAG, "🚨 WiFi DISABLING!")
                            val serviceIntent = Intent(context, WiFiMonitoringService::class.java).apply {
                                this.action = "WIFI_OFF_BREACH"
                                putExtra("IMMEDIATE_BREACH", true)
                                putExtra("FORCED_RSSI", -127)
                            }
                            if (Build.VERSION.SDK_INT >= 26) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        }
                        WifiManager.WIFI_STATE_DISABLED -> {
                            Log.d(TAG, "WiFi DISABLED")
                            val serviceIntent = Intent(context, WiFiMonitoringService::class.java).apply {
                                this.action = "WIFI_OFF_BREACH"
                                putExtra("IMMEDIATE_BREACH", false)
                                putExtra("FORCED_RSSI", -127)
                            }
                            if (Build.VERSION.SDK_INT >= 26) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        }
                        WifiManager.WIFI_STATE_ENABLED -> {
                            Log.d(TAG, "✅ WiFi ENABLED — sending WIFI_RESTORED, not breach")
                            val serviceIntent = Intent(context, WiFiMonitoringService::class.java).apply {
                                this.action = "WIFI_RESTORED"
                            }
                            if (Build.VERSION.SDK_INT >= 26) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                            
                            Handler(Looper.getMainLooper()).postDelayed({
                                Thread {
                                    sendRecoveryHeartbeat(context)
                                }.start()
                            }, 5000L)
                        }
                    }
                }
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    // ← NEW: handle when device connects to any network
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.state == NetworkInfo.State.CONNECTED) {
                        // WiFi connected to a network
                        // Check if it is the authorized one
                        checkNetworkAuthorization(context)
                    }
                }
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    val noConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)
                    if (noConnectivity) {
                        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                        val wifiState = wifiManager.wifiState
                        if (wifiState == WifiManager.WIFI_STATE_DISABLED || wifiState == WifiManager.WIFI_STATE_DISABLING) {
                            Log.e(TAG, "CONNECTIVITY_ACTION: WiFi is OFF, sending breach")
                            val serviceIntent = Intent(context, WiFiMonitoringService::class.java).apply {
                                this.action = "WIFI_OFF_BREACH"
                                putExtra("IMMEDIATE_BREACH", false)
                                putExtra("FORCED_RSSI", -127)
                            }
                            if (Build.VERSION.SDK_INT >= 26) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        } else {
                            Log.d(TAG, "CONNECTIVITY_ACTION no-connectivity but WiFi state=$wifiState — ignoring restore blip")
                        }
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen OFF")
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "Screen ON")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onReceive: ${e.message}", e)
        } finally {
            if (wl.isHeld) {
                wl.release()
            }
        }
    }

    // ← NEW: check if connected network is the authorized one
    private fun checkNetworkAuthorization(context: Context) {
        val prefs = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
        
        // ← Get authorized BSSID saved during tablet registration
        val authorizedBssid = prefs.getString("authorized_bssid", "") ?: ""
        val authorizedSsid = prefs.getString("authorized_ssid", "") ?: ""
        
        // ← If no authorized network saved, do not breach (not yet provisioned)
        if (authorizedBssid.isEmpty() && authorizedSsid.isEmpty()) {
            Log.d(TAG, "No authorized network saved — skipping check")
            return
        }
        
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        
        if (!wifiManager.isWifiEnabled) return
        
        // ← Get current network info
        val currentBssid = try {
            wifiManager.connectionInfo?.bssid ?: ""
        } catch (e: Exception) { "" }
        
        val currentSsid = try {
            wifiManager.connectionInfo?.ssid?.replace("\"", "") ?: ""
        } catch (e: Exception) { "" }
        
        Log.d(TAG, "Network check: current=$currentBssid/$currentSsid authorized=$authorizedBssid/$authorizedSsid")
        
        // ← Skip Android privacy MAC: 02:00:00:00:00:00 = MAC randomized
        val isPrivacyMac = currentBssid == "02:00:00:00:00:00"
        
        if (isPrivacyMac) {
            // ← Fall back to SSID comparison
            if (authorizedSsid.isNotEmpty() && currentSsid.isNotEmpty() && currentSsid != authorizedSsid && currentSsid != "<unknown ssid>") {
                Log.e(TAG, "🚨 UNAUTHORIZED NETWORK! Expected SSID: $authorizedSsid Got: $currentSsid")
                triggerNetworkBreach(context, "Wrong WiFi network: $currentSsid")
            }
            return
        }
        
        // ← Compare BSSID if available
        if (authorizedBssid.isNotEmpty() && currentBssid.isNotEmpty() && currentBssid != authorizedBssid) {
            Log.e(TAG, "🚨 UNAUTHORIZED NETWORK! Expected: $authorizedBssid Got: $currentBssid")
            triggerNetworkBreach(context, "Wrong WiFi network: $currentBssid")
            return
        }
        
        Log.d(TAG, "✅ Authorized network confirmed")
    }

    // ← NEW: trigger breach for wrong network
    private fun triggerNetworkBreach(context: Context, reason: String) {
        // ← Start service with wrong network breach
        val serviceIntent = Intent(context, WiFiMonitoringService::class.java).apply {
            action = "WRONG_NETWORK_BREACH"
            putExtra("BREACH_REASON", reason)
            putExtra("IMMEDIATE_BREACH", true)
            putExtra("FORCED_RSSI", -127)
        }
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun sendRecoveryHeartbeat(context: Context) {
        try {
            val prefs = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
            val token = prefs.getString("device_token", "") ?: return
            val deviceId = prefs.getString("device_id", "") ?: return
            val roomId = prefs.getString("room_id", "") ?: return
            val backendUrl = prefs.getString("backend_base_url", "https://hotel-tablet-security.onrender.com") ?: return
            
            Thread.sleep(2000)
            
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val rssi = try {
                wm.connectionInfo?.rssi ?: -65
            } catch (e: Exception) { -65 }
            
            val url = URL("$backendUrl/api/heartbeat")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            
            val body = JSONObject().apply {
                put("deviceId", deviceId)
                put("roomId", roomId)
                put("rssi", rssi)
                put("wifiBssid", "AA:BB:CC:DD:EE:FF")
                put("battery", 50)
            }.toString()
            
            OutputStreamWriter(conn.outputStream).use { it.write(body); it.flush() }
            
            val code = conn.responseCode
            Log.i(TAG, "✅ Recovery heartbeat: $code RSSI:$rssi")
            conn.disconnect()
            
        } catch (e: Exception) {
            Log.e(TAG, "Recovery failed: ${e.message}")
        }
    }
}
