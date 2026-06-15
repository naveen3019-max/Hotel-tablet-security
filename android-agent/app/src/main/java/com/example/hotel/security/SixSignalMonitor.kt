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
        isStartupGracePeriod = true
        Handler(Looper.getMainLooper()).postDelayed({
            isStartupGracePeriod = false
            Log.d(TAG, "Grace period ended — breach detection now active")
        }, 8_000L)
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

    fun forceImmediateCheck(skipDelay: Boolean = false) {
        if (!isRunning) return
        try {
            val now = SystemClock.elapsedRealtime()

            if (lastCheckTime > 0 && (now - lastCheckTime) > 20000L) {
                Log.w(TAG, "God Mode: Monitoring was paused by Doze! Gap: ${now - lastCheckTime}ms")
            }

            lastCheckTime = now
            performSecurityCheck(skipConfirmationDelay = skipDelay)
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
    fun triggerBreach(reason: String, rssi: Int, isImmediate: Boolean = false) {
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

        lastBreachSentTime = now
        Log.e(TAG, "🚨 SENDING BREACH TO BACKEND: $reason | Device: $deviceId | Room: $roomId | RSSI: $rssi")

        // Launch in a background thread — HTTPURLConnection is blocking
        // We use a Thread instead of Coroutine because the dispatcher might be throttled when WiFi is off
        Thread {
            var success = false
            
            // ← FIXED: Try no-auth instant endpoint first for immediate breach detection
            if (isImmediate) {
                try {
                    success = postBreach(backendUrl, "breach-instant", deviceId, roomId, rssi, deviceToken, requireAuth = false)
                    if (success) {
                        Log.i(TAG, "✅ Instant breach sent!")
                        return@Thread
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Instant endpoint failed, trying JWT endpoint")
                }
            }
            
            val maxAttempts = 15
            
            // Fall back to JWT endpoint with retries
            repeat(maxAttempts) { attempt ->
                if (success) return@repeat
                
                try {
                    success = postBreach(backendUrl, "breach", deviceId, roomId, rssi, deviceToken, requireAuth = true)
                    
                    if (success) {
                        Log.i(TAG, "✅ JWT breach success attempt ${attempt + 1}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Attempt ${attempt + 1} failed: ${e.message}")
                }
                
                if (!success) {
                    // ← FIXED: Fast retry first 3 attempts then normal pace for Render wake
                    val delayMs = if (attempt < 3) {
                        1000L  // 1s for first 3 attempts
                    } else {
                        2000L  // 2s for remaining attempts
                    }
                    Thread.sleep(delayMs)
                }
            }
            
            if (!success) {
                Log.e(TAG, "❌ All $maxAttempts breach attempts failed. Backend timeout will catch it.")
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun postBreach(
        backendUrl: String,
        endpoint: String,
        deviceId: String,
        roomId: String,
        rssi: Int,
        deviceToken: String,
        requireAuth: Boolean
    ): Boolean {
        val url = URL("$backendUrl/api/alert/$endpoint")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        if (requireAuth) {
            conn.setRequestProperty("Authorization", "Bearer $deviceToken")
        }
        conn.doOutput = true
        // ← FIXED: 5s timeout not 10s
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        
        val body = JSONObject().apply {
            put("deviceId", deviceId)
            put("roomId", roomId)
            put("rssi", rssi)
        }
        
        OutputStreamWriter(conn.outputStream).use {
            it.write(body.toString())
            it.flush()
        }
        
        val code = conn.responseCode
        conn.disconnect()
        return code in 200..299
    }

    // ← FIXED BUG 1: Complete rewrite of security check with proper threshold
    private fun performSecurityCheck(skipConfirmationDelay: Boolean = false) {
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
            if (skipConfirmationDelay) {
                // WIFI_STATE_DISABLING = hardware confirmation, no delay needed
                // Radio still active for ~1-2s — fire POST immediately
                Log.d(TAG, "⚡ IMMEDIATE breach — DISABLING state, skipping 8s delay")
                triggerBreach("⚡ IMMEDIATE breach — DISABLING state", actualRssi, isImmediate = true)
                return
            }

            // Validate RSSI: if signal is actually fine, ignore the screen-off blip
            if (actualRssi > -100 && wifiManager.isWifiEnabled) {
                Log.w(TAG, "Ignoring false breach: RSSI is $actualRssi dBm (fine). Likely screen-off blip.")
                firstWifiLossTime = 0L // Reset delay timer
            } else {
                // Start or check the 8-second delay timer
                if (firstWifiLossTime == 0L) {
                    firstWifiLossTime = now
                    Log.w(TAG, "⏱️ Starting 8s Wi-Fi loss confirmation timer...")
                } else if (now - firstWifiLossTime >= 8_000L) {
                    Log.e(TAG, "🚨 8s confirmed! Score $failScore/4 — BREACH")
                    WiFiMonitoringService.triggerBreachAlert("Score $failScore/4 failed", actualRssi)
                } else {
                    Log.w(TAG, "⏱️ Waiting for 8s confirmation... (${(now - firstWifiLossTime) / 1000}s elapsed)")
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
