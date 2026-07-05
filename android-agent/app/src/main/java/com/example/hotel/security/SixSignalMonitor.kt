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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SixSignalMonitor(private val context: Context) {
    
    fun resetWifiLostState() {
        firstWifiLossTime = 0L
        isBreachActive = false
        lastBreachSentTime = 0L
        Log.d(TAG, "WiFi lost state reset — breach detection reset for next disconnect")
    }
    private var isRunning = false
    private var lastCheckTime = 0L
    // Duplicate removed
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

        // ← FIXED: Store breach locally when offline
        @Volatile var lastBreachSentTime = 0L
        @Volatile var isBreachActive = false
        @Volatile var pendingBreachRssi: Int? = null
        val DEDUP_WINDOW_MS = 30_000L
        @Volatile private var instance: SixSignalMonitor? = null
        fun getInstance(): SixSignalMonitor? = instance
        fun setInstance(monitor: SixSignalMonitor) { instance = monitor }
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
    fun triggerBreach(
        reason: String,
        rssi: Int,
        isImmediate: Boolean = false
    ) {
        val now = SystemClock.elapsedRealtime()
        
        // Dedup check
        if (now - Companion.lastBreachSentTime < 
            BREACH_COOLDOWN) {
            Log.w(TAG, "Breach cooldown — skip")
            return
        }
        
        val prefs = context.getSharedPreferences(
            PREFS_NAME, Context.MODE_PRIVATE)
        val deviceId = prefs.getString(
            "device_id", "") ?: ""
        val roomId = prefs.getString(
            "room_id", "") ?: ""
        
        if (deviceId.isEmpty() || 
            deviceId == "UNKNOWN" ||
            roomId.isEmpty()) {
            Log.e(TAG, "No deviceId — cannot breach")
            return
        }
        
        Companion.lastBreachSentTime = now
        Companion.isBreachActive = true
        
        Log.e(TAG,
            "🚨 BREACH: $reason " +
            "Device:$deviceId RSSI:$rssi")
        
        // ← Call fireBreach directly
        fireBreach(rssi = if (rssi > -10) -127 
                          else rssi)
    }

    private val breachLock = Mutex()

    fun fireBreach(rssi: Int = -127) {
        val actualRssi = rssi // trust caller
        Log.d(TAG, "fireBreach() called with rssi=$rssi")
        
        val prefs = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
        val deviceIdVal = prefs.getString("device_id", null) ?: run {
            Log.e(TAG, "fireBreach: deviceId is null — aborting")
            return
        }
        val roomIdVal = prefs.getString("room_id", null) ?: run {
            Log.e(TAG, "fireBreach: roomId is null — aborting")
            return
        }
        
        val freshToken = context.getSharedPreferences("kiosk_prefs", Context.MODE_PRIVATE)
            .getString("authToken", null)

        if (freshToken == null) {
            Log.e(TAG, "fireBreach: authToken is null — check SharedPreferences key name")
            // Try alternate key names
            val altToken = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
                .getString("device_token", null)
                ?: context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
                .getString("authToken", null)
                
            if (altToken == null) {
                Log.e(TAG, "fireBreach: no token in any pref — cannot POST breach")
                return
            }
            Log.d(TAG, "fireBreach: found token in alternate prefs")
            executeBreachPost(deviceIdVal, roomIdVal, altToken, rssi)
        } else {
            executeBreachPost(deviceIdVal, roomIdVal, freshToken, rssi)
        }
    }

    private fun executeBreachPost(deviceId: String, roomId: String, token: String, rssi: Int) {
        Thread {
            
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val wakeLock = pm.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK,
                "HotelSecurity::BreachPost"
            )
            wakeLock.acquire(60_000L)
            
            try {
                var posted = false
                repeat(5) { attempt ->
                    if (posted) return@repeat
                    Log.d(TAG, "Breach POST attempt ${attempt + 1}/5")
                    try {
                        val backendUrl = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
                            .getString("backend_base_url", "https://hotel-tablet-security.onrender.com")
                        val conn = (URL("$backendUrl/api/alert/breach")
                            .openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            setRequestProperty("Content-Type", "application/json")
                            setRequestProperty("Authorization", "Bearer $token")
                            connectTimeout = 12_000   // ← increase from 8s to 12s for Render cold start
                            readTimeout = 12_000
                            doOutput = true
                        }
                        val body = JSONObject().apply {
                            put("deviceId", deviceId)
                            put("roomId", roomId)
                            put("rssi", rssi)
                        }
                        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
                        val code = conn.responseCode
                        Log.d(TAG, "Breach POST attempt ${attempt + 1} → HTTP $code")
                        when {
                            code in 200..299 -> { posted = true; Log.d(TAG, "✅ Breach POST succeeded") }
                            code == 401 -> { Log.e(TAG, "❌ 401 — token rejected, stopping retries"); return@Thread }
                            else -> Log.w(TAG, "⚠️ HTTP $code — will retry")
                        }
                        conn.disconnect()
                    } catch (e: Exception) {
                        Log.e(TAG, "Breach POST attempt ${attempt + 1} exception: ${e.javaClass.simpleName}: ${e.message}")
                    }
                    if (!posted) Thread.sleep(3_000L)
                }
                if (!posted) Log.e(TAG, "❌ All 5 breach POST attempts failed")
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }.apply { isDaemon = true; start() }
    }

    private fun clearPendingBreach() {
        context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE).edit()
            .remove("pending_breach")
            .remove("pending_breach_rssi")
            .remove("breach_detected_at")
            .apply()
        pendingBreachRssi = null
    }

    private fun postBreachWithTimestamp(
        rssi: Int,
        breachTimestamp: Long
    ): Boolean {
        val prefs = context.getSharedPreferences(
            PREFS_NAME, Context.MODE_PRIVATE)
        val backendUrl = prefs.getString(
            "backend_base_url",
            "https://hotel-tablet-security.onrender.com"
        ) ?: return false
        val deviceToken = prefs.getString(
            "device_token", "") ?: return false
        val deviceId = prefs.getString(
            "device_id", "") ?: return false
        val roomId = prefs.getString(
            "room_id", "") ?: return false
        
        if (deviceToken.isEmpty() ||
            deviceId.isEmpty()) return false
        
        return try {
            val url = URL(
                "$backendUrl/api/alert/breach")
            val conn = url.openConnection()
                as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty(
                "Content-Type", "application/json")
            conn.setRequestProperty(
                "Authorization", "Bearer $deviceToken")
            conn.doOutput = true
            // ← SHORT timeouts so retry fast
            conn.connectTimeout = 5000  // 5s
            conn.readTimeout = 5000     // 5s
            
            val body = JSONObject().apply {
                put("deviceId", deviceId)
                put("roomId", roomId)
                put("rssi", rssi)
                put("breachTimestamp", breachTimestamp)
            }.toString()
            
            OutputStreamWriter(conn.outputStream)
                .use { 
                    it.write(body)
                    it.flush() 
                }
            
            val code = conn.responseCode
            conn.disconnect()
            
            Log.i(TAG, "POST breach: $code")
            code in 200..299
            
        } catch (e: Exception) {
            Log.w(TAG, "POST failed: ${e.message}")
            false
        }
    }
    private fun performSecurityCheck(
        skipConfirmationDelay: Boolean = false
    ) {
        if (isStartupGracePeriod) return
        
        val wifiData = getWifiInfo()
        val now = SystemClock.elapsedRealtime()
        
        if (!wifiData.isConnected || 
            wifiData.rssi <= -127) {
            
            if (skipConfirmationDelay) {
                // ← Instant breach from Receiver
                Log.e(TAG,
                    "⚡ INSTANT BREACH — " +
                    "skipDelay=true")
                triggerBreach(
                    "WiFi DISABLING instant",
                    rssi = -127,
                    isImmediate = true
                )
                firstWifiLossTime = 0L
                return
            }
            
            // Normal path with 8s timer
            if (firstWifiLossTime == 0L) {
                firstWifiLossTime = now
                Log.w(TAG, "WiFi loss — 8s timer")
            } else if (now - firstWifiLossTime 
                >= 8_000L) {
                Log.e(TAG, "8s confirmed — breach!")
                triggerBreach(
                    "WiFi OFF confirmed 8s",
                    rssi = -127
                )
                firstWifiLossTime = 0L
            }
            
        } else {
            firstWifiLossTime = 0L
            if (Companion.isBreachActive) {
                Companion.isBreachActive = false
                Log.d(TAG, "WiFi restored")
            }
            Log.d(TAG, "WiFi OK RSSI:${wifiData.rssi}")
        }
    }

    private fun getWifiInfo(): WifiData {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        
        val isEnabled = wifiManager.isWifiEnabled
        if (!isEnabled) {
            return WifiData(rssi = -127, bssid = "00:00:00:00:00:00", isConnected = false)
        }
        
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val isWifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
        
        if (!isWifiConnected) {
            return WifiData(rssi = -127, bssid = "00:00:00:00:00:00", isConnected = false)
        }
        
        val rssi = try {
            val info = wifiManager.connectionInfo
            info?.rssi ?: -65
        } catch (e: Exception) {
            -65
        }
        
        return WifiData(rssi = rssi, bssid = "AA:BB:CC:DD:EE:FF", isConnected = true)
    }

    data class WifiData(
        val rssi: Int,
        val bssid: String,
        val isConnected: Boolean
    )

    fun resetBreachState() {
        Companion.lastBreachSentTime = 0L
        firstWifiLossTime = 0L
    }
}
