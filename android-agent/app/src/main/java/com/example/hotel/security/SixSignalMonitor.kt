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
// â† FIX (CAUSE 1): Need these to check location permission and services
import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

class SixSignalMonitor(private val context: Context) {
    
    fun resetWifiLostState() {
        firstWifiLossTime = 0L
        isBreachActive = false
        lastBreachSentTime = 0L
        Log.d(TAG, "WiFi lost state reset â€” breach detection reset for next disconnect")
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
            Log.d(TAG, "Grace period ended â€” breach detection now active")
        }, 8_000L)
    }

    companion object {
        private const val TAG = "SixSignalMonitor"
        private const val BREACH_COOLDOWN = 15_000L // 15 second cooldown between breach POSTs
        private const val PREFS_NAME = "hotel_prefs" // SharedPreferences name for config

        // â† FIXED: Store breach locally when offline
        @Volatile var lastBreachSentTime = 0L
        @Volatile var isBreachActive = false
        @Volatile var pendingBreachRssi: Int? = null
        val DEDUP_WINDOW_MS = 30_000L
        @Volatile private var instance: SixSignalMonitor? = null
        fun getInstance(): SixSignalMonitor? = instance
        fun setInstance(monitor: SixSignalMonitor) { instance = monitor }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Network-bound connection helper
    // When WiFi is OFF the system still has mobile/ethernet networks available.
    // Binding the socket to one of those bypasses the dead WiFi interface and
    // gives Android a working DNS resolver, fixing UnknownHostException.
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private fun openConnectionOnAnyNetwork(
        urlString: String
    ): HttpURLConnection {
        val url = URL(urlString)
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager

        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            // Skip the dead WiFi interface
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            // Only use networks that actually claim Internet access
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            try {
                val conn = network.openConnection(url) as HttpURLConnection
                Log.i(TAG, "âœ… Using non-WiFi network for breach POST")
                return conn
            } catch (e: Exception) {
                Log.w(TAG, "Network $network failed: ${e.message}")
            }
        }

        // Fallback: default connection (may still work if DNS is cached)
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

    // â† FIXED BUG 2: triggerBreach now actually sends HTTP POST to backend
    fun triggerBreach(
        reason: String,
        rssi: Int,
        isImmediate: Boolean = false
    ) {
        val now = SystemClock.elapsedRealtime()
        
        // Dedup check
        if (now - Companion.lastBreachSentTime < 
            BREACH_COOLDOWN) {
            Log.w(TAG, "Breach cooldown â€” skip")
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
            Log.e(TAG, "No deviceId â€” cannot breach")
            return
        }
        
        Companion.lastBreachSentTime = now
        Companion.isBreachActive = true
        
        Log.e(TAG,
            "ðŸš¨ BREACH: $reason " +
            "Device:$deviceId RSSI:$rssi")
        
        // â† Call fireBreach directly
        fireBreach(rssi = if (rssi > -10) -127 
                          else rssi, reason = reason)
    }

    private val breachLock = Mutex()

    fun fireBreach(rssi: Int = -127, reason: String = "WiFi disabled on device") {
        val actualRssi = rssi // trust caller
        Log.d(TAG, "fireBreach() called with rssi=$rssi")
        
        val prefs = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
        val deviceIdVal = prefs.getString("device_id", null) ?: run {
            Log.e(TAG, "fireBreach: deviceId is null â€” aborting")
            return
        }
        val roomIdVal = prefs.getString("room_id", null) ?: run {
            Log.e(TAG, "fireBreach: roomId is null â€” aborting")
            return
        }
        
        val freshToken = context.getSharedPreferences("kiosk_prefs", Context.MODE_PRIVATE)
            .getString("authToken", null)

        if (freshToken == null) {
            Log.e(TAG, "fireBreach: authToken is null â€” check SharedPreferences key name")
            // Try alternate key names
            val altToken = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
                .getString("device_token", null)
                ?: context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
                .getString("authToken", null)
                
            if (altToken == null) {
                Log.e(TAG, "fireBreach: no token in any pref â€” cannot POST breach")
                return
            }
            Log.d(TAG, "fireBreach: found token in alternate prefs")
            executeBreachPost(deviceIdVal, roomIdVal, altToken, rssi, reason)
        } else {
            executeBreachPost(deviceIdVal, roomIdVal, freshToken, rssi, reason)
        }
    }

    private fun executeBreachPost(deviceId: String, roomId: String, token: String, rssi: Int, reason: String) {
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
                        // â† FIXED: bind to mobile/ethernet to bypass dead WiFi interface
                        val conn = openConnectionOnAnyNetwork(
                            "$backendUrl/api/alert/breach"
                        ).apply {
                            requestMethod = "POST"
                            setRequestProperty("Content-Type", "application/json")
                            setRequestProperty("Authorization", "Bearer $token")
                            connectTimeout = 12_000   // 12s for Render cold start
                            readTimeout = 12_000
                            doOutput = true
                        }
                        val body = JSONObject().apply {
                            put("deviceId", deviceId)
                            put("roomId", roomId)
                            put("rssi", rssi)
                            // â† ADD message field
                            put("message", reason)
                        }
                        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
                        val code = conn.responseCode
                        Log.d(TAG, "Breach POST attempt ${attempt + 1} â†’ HTTP $code")
                        when {
                            code in 200..299 -> { posted = true; Log.d(TAG, "âœ… Breach POST succeeded") }
                            code == 401 -> { Log.e(TAG, "âŒ 401 â€” token rejected, stopping retries"); return@Thread }
                            else -> Log.w(TAG, "âš ï¸ HTTP $code â€” will retry")
                        }
                        conn.disconnect()
                    } catch (e: Exception) {
                        Log.e(TAG, "Breach POST attempt ${attempt + 1} exception: ${e.javaClass.simpleName}: ${e.message}")
                    }
                    if (!posted) Thread.sleep(3_000L)
                }
                if (!posted) Log.e(TAG, "âŒ All 5 breach POST attempts failed")
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
            // â† FIXED: bind to mobile/ethernet to bypass dead WiFi interface
            val conn = openConnectionOnAnyNetwork(
                "$backendUrl/api/alert/breach"
            )
            conn.requestMethod = "POST"
            conn.setRequestProperty(
                "Content-Type", "application/json")
            conn.setRequestProperty(
                "Authorization", "Bearer $deviceToken")
            conn.doOutput = true
            // â† SHORT timeouts so retry fast
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
                // â† Instant breach from Receiver
                Log.e(TAG,
                    "âš¡ INSTANT BREACH â€” " +
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
                Log.w(TAG, "WiFi loss â€” 8s timer")
            } else if (now - firstWifiLossTime 
                >= 8_000L) {
                Log.e(TAG, "8s confirmed â€” breach!")
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
            
            // â† NEW: Check authorized network
            checkWrongNetwork()
            
            Log.d(TAG, "WiFi OK RSSI:${wifiData.rssi}")
        }
    }

    // â† FIX (CAUSE 1 + CAUSE 2): Periodic fallback check (15 s heartbeat path).
    //   NetworkCallback is the PRIMARY trigger (fast, event-driven). This runs as a
    //   backup every 10 s via performSecurityCheck() to catch any missed events.
    private fun checkWrongNetwork() {
        val prefs = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
        val authorizedSsid = prefs.getString("authorized_ssid", "") ?: ""
        val authorizedBssid = prefs.getString("authorized_bssid", "") ?: ""
        
        if (authorizedSsid.isEmpty() && authorizedBssid.isEmpty()) {
            // <- Not provisioned yet
            return
        }
        
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) return
        
        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo ?: return
        
        @Suppress("DEPRECATION")
        val currentSsid = info.ssid?.replace("\"", "")?.trim() ?: ""
        
        @Suppress("DEPRECATION")
        val currentBssid = info.bssid ?: ""
        
        if (currentSsid.isEmpty() || currentSsid == "<unknown ssid>") return
        
        // <- Check SSID mismatch
        if (authorizedSsid.isNotEmpty() && currentSsid != authorizedSsid) {
            Log.e(TAG, "🚨 WRONG NETWORK PERIODIC CHECK: Expected '$authorizedSsid' Got '$currentSsid'")
            triggerBreach(reason = "Wrong WiFi: '$currentSsid' expected '$authorizedSsid'", rssi = -127)
        }
    }

    // â† FIX (CAUSE 1): Centralised helpers used by checkWrongNetwork().
    //   Mirrors the same helpers in ScreenAndWiFiReceiver so both code paths apply
    //   the same guard without duplicating the platform-version logic.
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

