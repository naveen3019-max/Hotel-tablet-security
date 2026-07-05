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
        val breachTime = System.currentTimeMillis()
        
        // ← Use Thread not coroutine
        // Thread survives Doze with WakeLock
        Thread {
            // ← Acquire WakeLock to prevent
            // Android from killing this thread
            val pm = context.getSystemService(
                Context.POWER_SERVICE
            ) as android.os.PowerManager
            
            val wakeLock = pm.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK,
                "HotelSecurity::BreachPost"
            )
            
            // ← Acquire for max 60 seconds
            // Enough for all retry attempts
            wakeLock.acquire(60_000L)
            
            try {
                var success = false
                
                // First 3 attempts: 1s apart
                for (attempt in 0..2) {
                    if (success) break
                    try {
                        success = postBreachWithTimestamp(
                            actualRssi, breachTime)
                        if (success) {
                            Log.i(TAG,
                                "✅ Breach sent " +
                                "attempt ${attempt+1}")
                            clearPendingBreach()
                            break
                        }
                    } catch (e: Exception) {
                        Log.w(TAG,
                            "Attempt ${attempt+1}: " +
                            "${e.message}")
                    }
                    if (!success) Thread.sleep(1000L)
                }
                
                if (success) return@Thread
                
                // Next 12 attempts: 2s apart
                for (attempt in 0..11) {
                    if (success) break
                    try {
                        success = postBreachWithTimestamp(
                            actualRssi, breachTime)
                        if (success) {
                            Log.i(TAG,
                                "✅ Breach sent " +
                                "attempt ${attempt+4}")
                            clearPendingBreach()
                            break
                        }
                    } catch (e: Exception) {
                        Log.w(TAG,
                            "Attempt ${attempt+4}: " +
                            "${e.message}")
                    }
                    if (!success) Thread.sleep(2000L)
                }
                
                if (!success) {
                    Log.w(TAG,
                        "⚠️ All 15 failed. " +
                        "Storing breach locally.")
                    context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE).edit()
                        .putBoolean("pending_breach", true)
                        .putInt("pending_breach_rssi", actualRssi)
                        .putLong("breach_detected_at", breachTime)
                        .apply()
                    pendingBreachRssi = actualRssi
                }
                
            } finally {
                // ← ALWAYS release WakeLock
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            }
            
        }.apply { 
            isDaemon = false // ← Keep thread alive
            start() 
        }
    }

    // ← FIXED: Called when WiFi turns back ON
    fun sendPendingBreachIfExists() {
        val prefs = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
        val hasPending = prefs.getBoolean("pending_breach", false)
        
        if (!hasPending) return
        
        val pendingRssi = prefs.getInt("pending_breach_rssi", -127)
        val breachTime = prefs.getLong("breach_detected_at", 0L)
        
        // ← Check breach is not too old (>10 min)
        val ageMs = System.currentTimeMillis() - breachTime
        if (ageMs > 10 * 60 * 1000L) {
            // Too old — discard
            clearPendingBreach()
            Log.w(TAG, "Pending breach too old — discarded")
            return
        }
        
        Log.i(TAG, "📤 Sending pending breach from ${ageMs / 1000}s ago RSSI:$pendingRssi")
        
        CoroutineScope(Dispatchers.IO).launch {
            val breachTimestamp = breachTime
            
            repeat(5) { attempt ->
                try {
                    val success = postBreachWithTimestamp(pendingRssi, breachTimestamp)
                    if (success) {
                        clearPendingBreach()
                        Log.i(TAG, "✅ Pending breach sent!")
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Pending breach attempt ${attempt + 1} failed")
                    kotlinx.coroutines.delay(2000L)
                }
            }
        }
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
