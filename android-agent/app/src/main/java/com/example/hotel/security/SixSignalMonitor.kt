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
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var lastCheckTime = 0L
    private var lastBreachSentTime = 0L // ← FIXED: Cooldown tracker to prevent backend spam

    companion object {
        private const val TAG = "SixSignalMonitor"
        private const val CHECK_INTERVAL = 15_000L
        private const val BREACH_COOLDOWN = 15_000L // ← FIXED: 15 second cooldown between breach POSTs
        private const val PREFS_NAME = "hotel_prefs" // ← FIXED: SharedPreferences name for config
    }

    private val monitorRunnable = object : Runnable {
        override fun run() {
            try {
                val now = SystemClock.elapsedRealtime()

                if (lastCheckTime > 0 && (now - lastCheckTime) > 20000L) {
                    Log.w(TAG, "God Mode: Monitoring was paused by Doze! Gap: ${now - lastCheckTime}ms")
                }

                lastCheckTime = now
                performSecurityCheck()
            } catch (e: Exception) {
                Log.e(TAG, "Error in God Mode monitoring loop", e)
            } finally {
                if (isRunning) {
                    handler.postDelayed(this, CHECK_INTERVAL) // ← FIXED: ALWAYS reschedule even on crash
                }
            }
        }
    }

    fun startMonitoring() {
        if (!isRunning) {
            isRunning = true
            lastCheckTime = SystemClock.elapsedRealtime()
            handler.post(monitorRunnable)
            Log.i(TAG, "God Mode: SixSignalMonitor started")
        }
    }

    fun stopMonitoring() {
        isRunning = false
        handler.removeCallbacks(monitorRunnable)
        Log.i(TAG, "God Mode: SixSignalMonitor stopped")
    }

    fun forceImmediateCheck() {
        handler.removeCallbacks(monitorRunnable)
        handler.post(monitorRunnable)
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
        lastBreachSentTime = now

        // ← FIXED: Read config from SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val backendUrl = prefs.getString("backend_base_url", "https://hotel-tablet-security.onrender.com") ?: "https://hotel-tablet-security.onrender.com"
        val deviceToken = prefs.getString("device_token", "") ?: ""
        val deviceId = prefs.getString("device_id", "UNKNOWN") ?: "UNKNOWN"
        val roomId = prefs.getString("room_id", "UNKNOWN") ?: "UNKNOWN"

        if (deviceToken.isEmpty()) {
            Log.e(TAG, "❌ Cannot send breach: device_token is empty in SharedPreferences!")
            return
        }

        Log.e(TAG, "🚨 SENDING BREACH TO BACKEND: $reason | Device: $deviceId | Room: $roomId")

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

                // ← FIXED: Build JSON body matching backend Breach model
                val body = JSONObject().apply {
                    put("deviceId", deviceId)
                    put("roomId", roomId)
                    put("rssi", -127) // ← WiFi OFF means no signal at all
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
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        var failScore = 0

        // ← FIXED: Signal 1 — WiFi enabled check (INSTANT BREACH if OFF)
        if (!wifiManager.isWifiEnabled) {
            failScore++
            Log.e(TAG, "🚨 Signal 1 FAIL: WiFi is DISABLED — triggering INSTANT breach")
            // ← FIXED: WiFi OFF = INSTANT BREACH, do NOT wait for score threshold
            WiFiMonitoringService.triggerBreachAlert("WiFi is OFF")
            triggerBreach("WiFi is OFF")
            return // ← No need to check other signals, WiFi is completely off
        }

        // ← FIXED: Signal 2 — Connection info null/disconnected check
        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo
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
        @Suppress("DEPRECATION")
        val rssi = info?.rssi ?: -127
        if (rssi < -90) {
            failScore++
            Log.w(TAG, "Signal 4 FAIL: RSSI too weak: $rssi dBm")
        }

        // ← FIXED BUG 1: Proper scoring — breach on >= 3 signals failing (not the old broken >= 3 with max 2)
        if (failScore >= 3) {
            Log.e(TAG, "🚨 Score $failScore/4 — BREACH THRESHOLD REACHED")
            WiFiMonitoringService.triggerBreachAlert("Score $failScore/4 failed")
            triggerBreach("Score $failScore/4 signals failed")
        } else if (failScore > 0) {
            Log.w(TAG, "⚠️ Warning: $failScore/4 signals degraded, not breaching yet.")
        } else {
            // ← All signals healthy — reset breach cooldown
            WiFiMonitoringService.lastBreachTime = 0L
            Log.d(TAG, "✅ All 4 signals SECURE. RSSI: $rssi dBm")
        }
    }
}
