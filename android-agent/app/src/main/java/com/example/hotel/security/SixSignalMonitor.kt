package com.example.hotel.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SixSignalMonitor(private val context: Context) {
    private var isRunning = false
    private var lastCheckTime = 0L
    private var lastBreachSentTime = 0L // Cooldown tracker to prevent backend spam
    private var firstWifiLossTime = 0L // Tracks when Wi-Fi loss was first detected
    private var isStartupGracePeriod = true

    init {
        Handler(Looper.getMainLooper()).postDelayed({
            isStartupGracePeriod = false
        }, 5000L)
    }

    companion object {
        private const val TAG = "SixSignalMonitor"
        private const val BREACH_COOLDOWN = 15_000L // 15 second cooldown between breach POSTs
        private const val PREFS_NAME = "hotel_prefs" // SharedPreferences name for config
    }

    fun startMonitoring() {
        if (!isRunning) {
            isRunning = true
            lastCheckTime = SystemClock.elapsedRealtime()
            Log.i(TAG, "God Mode: SixSignalMonitor started")
            performSecurityCheck()
        }
    }

    fun stopMonitoring() {
        isRunning = false
        Log.i(TAG, "God Mode: SixSignalMonitor stopped")
    }

    fun forceImmediateCheck() {
        if (!isRunning) return
        try {
            val now = SystemClock.elapsedRealtime()

            if (lastCheckTime > 0 && (now - lastCheckTime) > 20000L) {
                Log.w(TAG, "God Mode: Monitoring was paused by Doze! Gap: ${now - lastCheckTime}ms")
            }

            lastCheckTime = now
            performSecurityCheck()
        } catch (e: Exception) {
            Log.e(TAG, "Error in God Mode monitoring loop", e)
        }
    }

    fun isMonitoringAlive(): Boolean {
        if (!isRunning) return false
        val timeSinceLastCheck = SystemClock.elapsedRealtime() - lastCheckTime
        return timeSinceLastCheck < 30_000L
    }

    // ← FIXED BUG 2: triggerBreach now actually sends HTTP POST to backend
    fun triggerBreach(reason: String) {
        val now = SystemClock.elapsedRealtime()

        // ← FIXED: Check cooldown to prevent spamming backend
        if (now - lastBreachSentTime < BREACH_COOLDOWN) {
            Log.w(TAG, "Breach cooldown active. Skipping POST for: $reason")
            return
        }

        // ← FIXED: Read config from SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val backendUrl = prefs.getString("backend_base_url", "https://hotel-tablet-security.onrender.com") ?: "https://hotel-tablet-security.onrender.com"
        val deviceToken = prefs.getString("device_token", "") ?: ""
        val deviceId = prefs.getString("device_id", "") ?: ""
        val roomId = prefs.getString("room_id", "") ?: ""

        // Validate deviceId and roomId are non-empty
        if (deviceId.isEmpty() || roomId.isEmpty() || deviceId == "UNKNOWN" || roomId == "UNKNOWN") {
            Log.e(TAG, "❌ Cannot send breach: deviceId or roomId is null/empty!")
            return
        }

        if (deviceToken.isEmpty()) {
            Log.e(TAG, "❌ Cannot send breach: device_token is empty in SharedPreferences!")
            return
        }

        // Read real RSSI at the moment of firing
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val actualRssi = wifiManager.connectionInfo?.rssi ?: -127

        lastBreachSentTime = now
        Log.e(TAG, "🚨 SENDING BREACH TO BACKEND: $reason | Device: $deviceId | Room: $roomId | RSSI: $actualRssi")

        // ← FIXED: Non-blocking HTTP POST using Coroutine on IO thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("$backendUrl/api/alert/breach")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $deviceToken") // ← FIXED: JWT auth header
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.doOutput = true

                // ← FIXED: Build JSON body matching backend Breach model with actual RSSI
                val body = JSONObject().apply {
                    put("deviceId", deviceId)
                    put("roomId", roomId)
                    put("rssi", actualRssi)
                }

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(body.toString())
                writer.flush()
                writer.close()

                val responseCode = conn.responseCode
                Log.i(TAG, "✅ Breach POST response: $responseCode for reason: $reason")

                conn.disconnect()
            } catch (e: Exception) {
                // ← FIXED: Never crash on network error — just log and retry next cycle
                Log.e(TAG, "❌ Breach POST failed (will retry next check): ${e.message}")
            }
        }
    }

    // ← FIXED BUG 1: Complete rewrite of security check with proper threshold
    private fun performSecurityCheck() {
        if (isStartupGracePeriod) {
            Log.d(TAG, "Startup grace period — ignoring Wi-Fi callback")
            return
        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        var failScore = 0

        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo
        val actualRssi = info?.rssi ?: -127

        // ← FIXED: Signal 1 — WiFi enabled check
        if (!wifiManager.isWifiEnabled) {
            failScore++
            Log.e(TAG, "🚨 Signal 1 FAIL: WiFi is DISABLED")
        }

        // ← FIXED: Signal 2 — Connection info null/disconnected check
        if (info == null || info.networkId == -1) {
            failScore++
            Log.w(TAG, "Signal 2 FAIL: WiFi connected but no network ID")
        }

        // ← FIXED: Signal 3 — Internet connectivity check via ConnectivityManager
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork == null) {
            failScore++
            Log.w(TAG, "Signal 3 FAIL: No active network")
        } else {
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                failScore++
                Log.w(TAG, "Signal 3 FAIL: No NET_CAPABILITY_INTERNET")
            }
        }

        // ← FIXED: Signal 4 — RSSI strength check
        if (actualRssi < -90) {
            failScore++
            Log.w(TAG, "Signal 4 FAIL: RSSI too weak: $actualRssi dBm")
        }

        val now = SystemClock.elapsedRealtime()

        if (failScore >= 3) {
            // Validate RSSI: if signal is actually fine, ignore the screen-off blip
            if (actualRssi > -100 && wifiManager.isWifiEnabled) {
                Log.w(TAG, "Ignoring false breach: RSSI is $actualRssi dBm (fine). Likely screen-off blip.")
                firstWifiLossTime = 0L // Reset delay timer
            } else {
                // Start or check the 15-second delay timer
                if (firstWifiLossTime == 0L) {
                    firstWifiLossTime = now
                    Log.w(TAG, "⏱️ Starting 15s Wi-Fi loss confirmation timer...")
                } else if (now - firstWifiLossTime >= 15_000L) {
                    Log.e(TAG, "🚨 15s confirmed! Score $failScore/4 — BREACH")
                    WiFiMonitoringService.triggerBreachAlert("Score $failScore/4 failed")
                } else {
                    Log.w(TAG, "⏱️ Waiting for 15s confirmation... (${(now - firstWifiLossTime) / 1000}s elapsed)")
                }
            }
        } else {
            // Signal is healthy or recovering
            firstWifiLossTime = 0L
            WiFiMonitoringService.lastBreachTime = 0L
            Log.d(TAG, "✅ Signal SECURE. RSSI: $actualRssi dBm")
        }
    }
}
