package com.example.hotel.security

import android.content.Context
import android.content.Intent
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
import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.hotel.service.KioskService

class SixSignalMonitor(private val context: Context) {

    fun resetWifiLostState() {
        firstWifiLossTime = 0L
        isBreachActive = false
        lastBreachSentTime = 0L
        Log.d(TAG, "WiFi lost state reset — breach detection reset for next disconnect")
    }

    private var isRunning = false
    private var lastCheckTime = 0L
    private var firstWifiLossTime = 0L
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
        private const val BREACH_COOLDOWN = 15_000L
        private const val PREFS_NAME = "hotel_prefs"

        @Volatile var lastBreachSentTime = 0L
        @Volatile var isBreachActive = false
        @Volatile var pendingBreachRssi: Int? = null
        val DEDUP_WINDOW_MS = 30_000L
        @Volatile private var instance: SixSignalMonitor? = null
        fun getInstance(): SixSignalMonitor? = instance
        fun setInstance(monitor: SixSignalMonitor) { instance = monitor }

        /**
         * True while a breach POST thread is actively running.
         * Any concurrent fireBreach() call that sees this flag set will
         * return immediately — preventing duplicate concurrent POSTs that
         * arise when WIFI_STATE_DISABLING and WIFI_STATE_DISABLED both
         * fire WIFI_OFF_BREACH within milliseconds of each other.
         */
        @Volatile var breachPostInFlight = false
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Network-bound connection helper
    // When WiFi is OFF the system still has mobile/ethernet networks available.
    // Binding the socket to one of those bypasses the dead WiFi interface and
    // gives Android a working DNS resolver, fixing UnknownHostException.
    // ─────────────────────────────────────────────────────────────────────────
    private fun openConnectionOnAnyNetwork(urlString: String): HttpURLConnection {
        val url = URL(urlString)
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            try {
                val conn = network.openConnection(url) as HttpURLConnection
                Log.i(TAG, "✅ Using non-WiFi network for breach POST")
                return conn
            } catch (e: Exception) {
                Log.w(TAG, "Network $network failed: ${e.message}")
            }
        }

        Log.w(TAG, "No mobile network found, trying default connection")
        return url.openConnection() as HttpURLConnection
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

    fun triggerBreach(
        reason: String,
        rssi: Int,
        isImmediate: Boolean = false
    ) {
        val now = SystemClock.elapsedRealtime()
        Log.d(TAG, "DEBUG: triggerBreach() called with isImmediate=$isImmediate at ${System.currentTimeMillis()}")

        if (!isImmediate) {
            if (now - Companion.lastBreachSentTime < BREACH_COOLDOWN) {
                Log.w(TAG, "Breach cooldown — skip")
                return
            }
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // ← FIX: Use DeviceIdentity to ensure it's available as early as possible
        val deviceId = com.example.hotel.security.DeviceIdentity.deviceId ?: prefs.getString("device_id", "") ?: ""
        val roomId = prefs.getString("room_id", "") ?: ""

        // ← FIX: Decouple overlay from backend reporting entirely. Always show local overlay first!
        try {
            val lockIntent = Intent(context, com.example.hotel.ui.LockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_HISTORY)
                // ← FIX: Pass authorized SSID into the overlay for wrong-network breach
                if (reason.contains("Wrong", ignoreCase = true) || reason.contains("WRONG", ignoreCase = true)) {
                    val expectedSsid = prefs.getString("authorized_ssid", null)
                    putExtra("expected_ssid", expectedSsid)
                }
            }
            context.startActivity(lockIntent)
            Log.i(TAG, "✅ Orange screen triggered directly from SixSignalMonitor")
        } catch (e: Exception) {
            Log.e(TAG, "Orange screen failed: $e")
        }

        if (deviceId.isEmpty() || deviceId == "UNKNOWN" || roomId.isEmpty()) {
            // ← FIX: don't just drop it — queue and retry since overlay already fired
            Log.e(TAG, "⚠️ deviceId unavailable — queuing breach for retry and firing local overlay anyway")
            savePendingBreach(rssi, System.currentTimeMillis(), reason)
            return
        }

        Companion.lastBreachSentTime = now
        Companion.isBreachActive = true

        Log.e(TAG, "🚨 BREACH: $reason Device:$deviceId RSSI:$rssi immediate:$isImmediate")

        fireBreach(rssi = if (rssi > -10) -127 else rssi, reason = reason)
    }

    private val breachLock = Mutex()

    fun fireBreach(rssi: Int = -127, reason: String = "WiFi disabled on device") {
        Log.d(TAG, "fireBreach() called with rssi=$rssi")

        val prefs = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
        val deviceIdVal = com.example.hotel.security.DeviceIdentity.deviceId ?: prefs.getString("device_id", null) ?: run {
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
            val altToken = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
                .getString("device_token", null)
                ?: context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
                    .getString("authToken", null)

            if (altToken == null) {
                Log.e(TAG, "fireBreach: no token in any pref — cannot POST breach")
                return
            }
            Log.d(TAG, "fireBreach: found token in alternate prefs")
            executeBreachPost(deviceIdVal, roomIdVal, altToken, rssi, reason)
        } else {
            executeBreachPost(deviceIdVal, roomIdVal, freshToken, rssi, reason)
        }
    }

    private fun executeBreachPost(
        deviceId: String,
        roomId: String,
        token: String,
        rssi: Int,
        reason: String,
        breachTimestamp: Long = System.currentTimeMillis()
    ) {
        // ← DEDUP GUARD: if a POST is already in-flight, skip this call entirely.
        // This is the primary protection against concurrent POSTs caused by
        // WIFI_STATE_DISABLING + WIFI_STATE_DISABLED firing within milliseconds.
        if (breachPostInFlight) {
            Log.w(TAG, "⚠️ Breach POST already in-flight — skipping duplicate call")
            return
        }
        breachPostInFlight = true

        Thread {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val wakeLock = pm.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK,
                "HotelSecurity::BreachPost"
            )
            wakeLock.acquire(60_000L)

            try {
                val backendUrl = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
                    .getString("backend_base_url", "https://hotel-tablet-security.onrender.com")
                    ?: "https://hotel-tablet-security.onrender.com"

                var posted = false

                // ← First 10 attempts: NO gap
                // Fires as fast as Render responds
                // If Render awake: success in <3s!
                for (attempt in 1..10) {
                    if (posted) break
                    Log.d(TAG, "Breach POST attempt $attempt/15 (fast phase)")
                    posted = attemptBreachPost(
                        backendUrl, deviceId, roomId, token, rssi, breachTimestamp, reason, attempt
                    )
                    if (posted) {
                        Log.i(TAG, "✅ Breach sent attempt $attempt")
                    }
                    // ← NO delay between attempts!
                    // Each attempt is 3s timeout
                    // 10 × 3s = 30s max coverage
                }

                if (posted) {
                    clearPendingBreach()
                    return@Thread
                }

                // ← Last 5 attempts: 1s gap
                for (attempt in 11..15) {
                    if (posted) break
                    Log.d(TAG, "Breach POST attempt $attempt/15 (slow phase)")
                    posted = attemptBreachPost(
                        backendUrl, deviceId, roomId, token, rssi, breachTimestamp, reason, attempt
                    )
                    if (posted) {
                        Log.i(TAG, "✅ Breach sent attempt $attempt")
                        clearPendingBreach()
                    }
                    if (!posted) Thread.sleep(1_000L)
                }

                if (!posted) {
                    Log.w(TAG, "All 15 failed — saving pending breach")
                    savePendingBreach(rssi, breachTimestamp)
                }

            } finally {
                breachPostInFlight = false
                if (wakeLock.isHeld) wakeLock.release()
            }
        }.apply { isDaemon = false; start() }
    }

    /** Single HTTP attempt; returns true on HTTP 2xx. */
    private fun attemptBreachPost(
        backendUrl: String,
        deviceId: String,
        roomId: String,
        token: String,
        rssi: Int,
        breachTimestamp: Long,
        reason: String,
        attempt: Int
    ): Boolean {
        return try {
            val conn = openConnectionOnAnyNetwork("$backendUrl/api/alert/breach").apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                // ← FIXED: 3s timeout not 5s/12s!
                connectTimeout = 3000
                readTimeout = 4000
                doOutput = true
            }
            val body = JSONObject().apply {
                put("deviceId", deviceId)
                put("roomId", roomId)
                put("rssi", rssi)
                put("breachTimestamp", breachTimestamp)
                put("message", reason)
            }
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            val code = conn.responseCode
            conn.disconnect()
            Log.d(TAG, "Breach POST attempt $attempt → HTTP $code")
            when {
                code in 200..299 -> true
                code == 401 -> {
                    Log.e(TAG, "❌ 401 — token rejected, stopping retries")
                    // Signal caller to stop by setting posted=true (we can't send)
                    breachPostInFlight = false
                    true  // exit retry loop; no pending saved for auth failures
                }
                else -> { Log.w(TAG, "⚠️ HTTP $code — will retry"); false }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Breach POST attempt $attempt exception: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /** Atomically clears all pending-breach prefs. Safe to call multiple times. */
    fun clearPendingBreach() {
        val prefs = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
        val hadPending = prefs.getBoolean("pending_breach", false)
        prefs.edit()
            .remove("pending_breach")
            .remove("pending_breach_rssi")
            .remove("breach_detected_at")
            .remove("pending_breach_reason")
            .apply()
        pendingBreachRssi = null
        if (hadPending) {
            Log.i(TAG, "✅ Pending breach cleared")
        }
    }

    /**
     * Persists a breach that failed all POST retries so it can be re-sent
     * once connectivity returns. Only called after all 15 retries fail.
     */
    private fun savePendingBreach(rssi: Int, breachTimestamp: Long, reason: String = "WiFi disabled (pending)") {
        context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("pending_breach", true)
            .putInt("pending_breach_rssi", rssi)
            .putLong("breach_detected_at", breachTimestamp)
            .putString("pending_breach_reason", reason)
            .apply()
        pendingBreachRssi = rssi
        Log.i(TAG, "💾 Pending breach saved (queued) timestamp: ${System.currentTimeMillis()}, rssi=$rssi ts=$breachTimestamp reason=$reason")
    }

    /**
     * Called when WiFi is restored. Reads prefs, discards stale entries
     * (> 5 minutes old), and retries the POST up to 5 times.
     */
    fun sendPendingBreachIfExists() {
        val prefs = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)

        if (!prefs.getBoolean("pending_breach", false)) {
            Log.d(TAG, "No pending breach — skipping")
            return
        }

        val rssi = prefs.getInt("pending_breach_rssi", -127)
        val timestamp = prefs.getLong("breach_detected_at", 0L)
        val pendingReason = prefs.getString("pending_breach_reason", "WiFi disabled (pending)") ?: "WiFi disabled (pending)"
        val ageMs = System.currentTimeMillis() - timestamp

        if (ageMs > 5 * 60 * 1_000L) {
            Log.w(TAG, "Pending breach too old (${ageMs / 1000}s) — discarding")
            clearPendingBreach()
            return
        }

        Log.i(TAG, "📤 Sending pending breach from ${ageMs / 1000}s ago (rssi=$rssi)")

        val token = context.getSharedPreferences("kiosk_prefs", Context.MODE_PRIVATE)
            .getString("authToken", null)
            ?: context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
                .getString("device_token", null)
            ?: run {
                Log.e(TAG, "sendPendingBreachIfExists: no token — cannot send")
                return
            }
        val deviceId = com.example.hotel.security.DeviceIdentity.deviceId ?: prefs.getString("device_id", "") ?: return
        val roomId = prefs.getString("room_id", "") ?: return
        val backendUrl = prefs.getString(
            "backend_base_url", "https://hotel-tablet-security.onrender.com"
        ) ?: "https://hotel-tablet-security.onrender.com"

        Thread {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val wakeLock = pm.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK,
                "HotelSecurity::PendingBreach"
            )
            wakeLock.acquire(30_000L)
            try {
                // Give network a moment to fully stabilize before attempting
                Thread.sleep(3_000L)

                var success = false
                for (attempt in 1..5) {
                    if (success) break
                    success = attemptBreachPost(
                        backendUrl, deviceId, roomId, token,
                        rssi, timestamp, pendingReason, attempt
                    )
                    if (success) {
                        Log.i(TAG, "✅ backend POST success timestamp: ${System.currentTimeMillis()} for pending breach on attempt $attempt")
                        clearPendingBreach()
                    } else {
                        Thread.sleep(2_000L)
                    }
                }

                if (!success) {
                    Log.e(TAG, "❌ Failed to send pending breach after 5 attempts")
                }
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }.apply { isDaemon = false; start() }
    }

    private fun postBreachWithTimestamp(rssi: Int, breachTimestamp: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val backendUrl = prefs.getString(
            "backend_base_url",
            "https://hotel-tablet-security.onrender.com"
        ) ?: return false
        val deviceToken = prefs.getString("device_token", "") ?: return false
        val deviceId = prefs.getString("device_id", "") ?: return false
        val roomId = prefs.getString("room_id", "") ?: return false

        if (deviceToken.isEmpty() || deviceId.isEmpty()) return false

        return try {
            val conn = openConnectionOnAnyNetwork("$backendUrl/api/alert/breach")
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $deviceToken")
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.readTimeout = 4000

            val body = JSONObject().apply {
                put("deviceId", deviceId)
                put("roomId", roomId)
                put("rssi", rssi)
                put("breachTimestamp", breachTimestamp)
            }.toString()

            OutputStreamWriter(conn.outputStream).use {
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

    // ← Only checks WiFi ON/OFF. SSID comparison is done in KioskService heartbeat.
    private fun performSecurityCheck(skipConfirmationDelay: Boolean = false) {
        Log.d(TAG, "DEBUG: performSecurityCheck entered at ${System.currentTimeMillis()}")
        if (isStartupGracePeriod) return

        // ← FIX: If WiFi just turned on and is still stabilizing, skip this check.
        // During the stabilization window (0-10 seconds after connect) the active
        // network may not yet have TRANSPORT_WIFI, causing a false "WiFi OFF" reading.
        val wifiOnAt = KioskService.wifiTurnedOnAt
        if (wifiOnAt > 0L) {
            val timeSinceConnect = SystemClock.elapsedRealtime() - wifiOnAt
            if (timeSinceConnect < KioskService.WIFI_STABILIZE_DELAY) {
                // WiFi is still stabilizing — skip check.
                // FIX BUG A: Do NOT reset firstWifiLossTime here. If we reset it, the 8s timer is re-armed
                // on every tick while stabilizing, meaning it never expires.
                Log.d(TAG,
                    "⏳ WiFi stabilizing in SixSignalMonitor " +
                    "(${timeSinceConnect}ms / ${KioskService.WIFI_STABILIZE_DELAY}ms) " +
                    "— skipping check")
                return
            }
        }

        val wifiData = getWifiInfo()
        val now = SystemClock.elapsedRealtime()

        if (!wifiData.isConnected || wifiData.rssi <= -127) {

            if (skipConfirmationDelay) {
                Log.e(TAG, "⚡ INSTANT: WiFi OFF")
                triggerBreach("WiFi disabled", rssi = -127, isImmediate = true)
                firstWifiLossTime = 0L
                return
            }

            if (firstWifiLossTime == 0L) {
                firstWifiLossTime = now
                Log.w(TAG, "WiFi loss — 8s timer")
            } else if (now - firstWifiLossTime >= 8_000L) {
                Log.e(TAG, "8s WiFi OFF confirmed")
                triggerBreach("WiFi disabled confirmed", rssi = -127)
                firstWifiLossTime = 0L
            }

        } else {
            // WiFi ON and connected — reset timer
            firstWifiLossTime = 0L
            Log.d(TAG, "WiFi OK RSSI:${wifiData.rssi}")
        }
    }

    private fun isLocationPermissionGranted(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) Log.w(TAG, "ACCESS_FINE_LOCATION NOT granted")
        return granted
    }

    private fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val enabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
        if (!enabled) Log.w(TAG, "Location services are DISABLED")
        return enabled
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
            @Suppress("DEPRECATION")
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
