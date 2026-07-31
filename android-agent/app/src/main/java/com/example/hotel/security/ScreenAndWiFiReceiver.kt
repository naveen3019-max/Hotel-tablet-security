package com.example.hotel.security

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.net.NetworkInfo
import androidx.core.content.ContextCompat
import java.net.URL
import java.net.HttpURLConnection
import org.json.JSONObject
import java.io.OutputStreamWriter

class ScreenAndWiFiReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenAndWiFiReceiver"
        private const val DBG = "WIFI_BREACH_DEBUG"   // ← DEBUG: unified tag for logcat filtering
        private const val UNKNOWN_SSID = "<unknown ssid>"
        private const val PRIVACY_MAC  = "02:00:00:00:00:00"
    }

    @Volatile private var breachAlreadySent = false

    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        context.registerReceiver(this, filter)
        Log.d(TAG, "✅ WiFi broadcast receiver registered")
    }

    fun unregister(context: Context) {
        try { context.unregisterReceiver(this) }
        catch (e: IllegalArgumentException) { Log.w(TAG, "Receiver already unregistered") }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HotelSecurity::ScreenAndWiFiReceiver")
        wl.acquire(3000)
        try {
            when (action) {
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                    when (wifiState) {
                        WifiManager.WIFI_STATE_DISABLING -> {
                            Log.e(TAG, "🚨 WiFi DISABLING!")
                            sendServiceIntent(context, "WIFI_OFF_BREACH", true)
                        }
                        WifiManager.WIFI_STATE_DISABLED -> {
                            Log.d(TAG, "WiFi DISABLED")
                            sendServiceIntent(context, "WIFI_OFF_BREACH", false)
                        }
                        WifiManager.WIFI_STATE_ENABLED -> {
                            Log.d(TAG, "✅ WiFi ENABLED — sending WIFI_RESTORED")
                            val si = Intent(context, WiFiMonitoringService::class.java).apply {
                                this.action = "WIFI_RESTORED"
                            }
                            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(si)
                            else context.startService(si)
                            Handler(Looper.getMainLooper()).postDelayed({
                                Thread { sendRecoveryHeartbeat(context) }.start()
                            }, 5000L)
                        }
                    }
                }

                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    // ← Kept as secondary/backup (deprecated but still works as fallback)
                    @Suppress("DEPRECATION")
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                    @Suppress("DEPRECATION")
                    if (networkInfo?.state == NetworkInfo.State.CONNECTED) {
                        Log.d(DBG, "NETWORK_STATE_CHANGED_ACTION: WiFi connected (backup trigger)")
                        checkNetworkAuthorization(context, source = "broadcast-backup")
                    }
                }

                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    val noConn = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)
                    if (noConn) {
                        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                        val wifiState = wm.wifiState
                        if (wifiState == WifiManager.WIFI_STATE_DISABLED || wifiState == WifiManager.WIFI_STATE_DISABLING) {
                            Log.e(TAG, "CONNECTIVITY_ACTION: WiFi is OFF, sending breach")
                            sendServiceIntent(context, "WIFI_OFF_BREACH", false)
                        }
                    }
                }

                Intent.ACTION_SCREEN_OFF -> Log.d(TAG, "Screen OFF")
                Intent.ACTION_SCREEN_ON  -> Log.d(TAG, "Screen ON")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onReceive: ${e.message}", e)
        } finally {
            if (wl.isHeld) wl.release()
        }
    }

    private fun sendServiceIntent(context: Context, action: String, immediate: Boolean) {
        val si = Intent(context, WiFiMonitoringService::class.java).apply {
            this.action = action
            putExtra("IMMEDIATE_BREACH", immediate)
            putExtra("FORCED_RSSI", -127)
        }
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(si)
        else context.startService(si)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // checkNetworkAuthorization — called from NetworkCallback (primary) and
    // broadcast receiver (secondary). source tag identifies the call site in logs.
    // ─────────────────────────────────────────────────────────────────────────
    fun checkNetworkAuthorization(
        context: Context,
        liveBssid: String? = null,
        liveSsid:  String? = null,
        source:    String  = "unknown"  // ← DEBUG: identifies call origin in logcat
    ) {
        Log.d(DBG, "──────────────────────────────────────────────")
        Log.d(DBG, "checkNetworkAuthorization() called from='$source'")

        // ← DEBUG: log SDK and targetSdk so we know which permission path applies
        Log.d(DBG, "  SDK=${Build.VERSION.SDK_INT} targetSdk=34 (compileSdk=34)")

        // ← DEBUG: log raw permission states
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        Log.d(DBG, "  ACCESS_FINE_LOCATION granted=$fineGranted")

        val nearbyGranted: Boolean? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val g = ContextCompat.checkSelfPermission(
                context, Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
            Log.d(DBG, "  NEARBY_WIFI_DEVICES granted=$g (API>=33 path)")
            g
        } else {
            Log.d(DBG, "  NEARBY_WIFI_DEVICES N/A (SDK ${Build.VERSION.SDK_INT} < 33)")
            null
        }

        // ← DEBUG: log location services state
        val locEnabled = isLocationEnabled(context)
        Log.d(DBG, "  LocationManager.isLocationEnabled=$locEnabled")

        val prefs = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
        val authorizedBssid = prefs.getString("authorized_bssid", "") ?: ""
        val authorizedSsid  = prefs.getString("authorized_ssid",  "") ?: ""
        Log.d(DBG, "  authorizedBssid='$authorizedBssid'  authorizedSsid='$authorizedSsid'")

        if (authorizedBssid.isEmpty() && authorizedSsid.isEmpty()) {
            Log.d(DBG, "  → BRANCH: no authorized network saved — SKIPPED")
            return
        }

        // ── Permission guard ─────────────────────────────────────────────────
        // On API >= 33 (targetSdk 33+): NEARBY_WIFI_DEVICES is the primary permission.
        // On API <  33: ACCESS_FINE_LOCATION + location services is required.
        // ← FIX: check both, emit clear degraded-breach if neither is satisfied.
        val canReadWifiIdentity: Boolean = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // API 33+ path: NEARBY_WIFI_DEVICES sufficient (neverForLocation means
                // location services ON/OFF doesn't matter for this permission)
                val ok = nearbyGranted == true || fineGranted
                Log.d(DBG, "  API>=33 permission check: NEARBY_WIFI_DEVICES=$nearbyGranted " +
                        "OR ACCESS_FINE_LOCATION=$fineGranted → canReadWifiIdentity=$ok")
                ok
            }
            else -> {
                // API < 33 path: ACCESS_FINE_LOCATION AND location services both required
                val ok = fineGranted && locEnabled
                Log.d(DBG, "  API<33 permission check: ACCESS_FINE_LOCATION=$fineGranted " +
                        "AND locationEnabled=$locEnabled → canReadWifiIdentity=$ok")
                ok
            }
        }

        if (!canReadWifiIdentity) {
            Log.e(DBG,
                "  ⚠️ WIFI IDENTITY UNAVAILABLE — Cannot read WiFi identity. " +
                "NEARBY_WIFI_DEVICES=$nearbyGranted ACCESS_FINE_LOCATION=$fineGranted " +
                "locationEnabled=$locEnabled → triggering degraded-state breach")
            triggerNetworkBreach(context, "Cannot verify network identity — permission/location unavailable")
            return
        }

        // ── Resolve BSSID / SSID ─────────────────────────────────────────────
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) {
            Log.d(DBG, "  → BRANCH: WiFi is disabled — skipped")
            return
        }

        val currentBssid: String
        val currentSsid: String

        if (liveBssid != null && liveSsid != null) {
            currentBssid = liveBssid
            currentSsid  = liveSsid
            Log.d(DBG, "  Using live values from NetworkCallback: BSSID='$currentBssid' SSID='$currentSsid'")
        } else {
            @Suppress("DEPRECATION")
            val info = wifiManager.connectionInfo
            @Suppress("DEPRECATION")
            currentBssid = info?.bssid ?: ""
            @Suppress("DEPRECATION")
            currentSsid  = info?.ssid?.replace("\"", "") ?: ""
            Log.d(DBG, "  Using WifiManager.connectionInfo: BSSID='$currentBssid' SSID='$currentSsid'")
        }

        // ← DEBUG: Log the raw values BEFORE privacy-MAC filtering
        Log.d(DBG, "  raw currentBssid='$currentBssid'  raw currentSsid='$currentSsid'")

        val isPrivacyMac = currentBssid == PRIVACY_MAC
        Log.d(DBG, "  isPrivacyMac=$isPrivacyMac")

        // ── Comparison branch ─────────────────────────────────────────────────
        if (authorizedBssid.isNotEmpty() && !isPrivacyMac && currentBssid.isNotEmpty()) {
            if (currentBssid != authorizedBssid) {
                Log.e(DBG, "  → BRANCH: BSSID MISMATCH — expected='$authorizedBssid' got='$currentBssid' → BREACH")
                triggerNetworkBreach(context, "Wrong WiFi network: $currentBssid")
            } else {
                Log.d(DBG, "  → BRANCH: BSSID MATCH — authorized ✅")
            }
            return
        }

        if (authorizedSsid.isNotEmpty() && currentSsid.isNotEmpty() && currentSsid != UNKNOWN_SSID) {
            if (currentSsid != authorizedSsid) {
                Log.e(DBG, "  → BRANCH: SSID MISMATCH — expected='$authorizedSsid' got='$currentSsid' → BREACH")
                triggerNetworkBreach(context, "Wrong WiFi network: $currentSsid")
            } else {
                Log.d(DBG, "  → BRANCH: SSID MATCH — authorized ✅")
            }
            return
        }

        // ← DEBUG: catch-all so no branch is silent
        Log.w(DBG, "  → BRANCH: no comparison performed — " +
                "authorizedBssid='$authorizedBssid' authorizedSsid='$authorizedSsid' " +
                "currentBssid='$currentBssid' currentSsid='$currentSsid' " +
                "isPrivacyMac=$isPrivacyMac")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Permission helpers — SDK-version-aware, exposed so SixSignalMonitor
    // can call the same logic without duplicating version checks
    // ─────────────────────────────────────────────────────────────────────────
    fun isLocationPermissionGranted(context: Context): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) Log.w(DBG, "ACCESS_FINE_LOCATION NOT granted")
        return granted
    }

    fun isNearbyWifiGranted(context: Context): Boolean {
        // ← FIX: NEARBY_WIFI_DEVICES is only a runtime permission on API 33+
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) Log.w(DBG, "NEARBY_WIFI_DEVICES NOT granted (API 33+)")
            granted
        } else true // irrelevant below API 33
    }

    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val enabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
        if (!enabled) Log.w(DBG, "Location services are DISABLED")
        return enabled
    }

    // ← canReadWifiIdentity: single entry point for all code that needs to know
    //   whether SSID/BSSID values from WifiManager/NetworkCapabilities are real.
    fun canReadWifiIdentity(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isNearbyWifiGranted(context) || isLocationPermissionGranted(context)
        } else {
            isLocationPermissionGranted(context) && isLocationEnabled(context)
        }

    private fun triggerNetworkBreach(context: Context, reason: String) {
        Log.e(DBG, "triggerNetworkBreach() reason='$reason'")
        val si = Intent(context, WiFiMonitoringService::class.java).apply {
            action = "WRONG_NETWORK_BREACH"
            putExtra("BREACH_REASON", reason)
            putExtra("IMMEDIATE_BREACH", true)
            putExtra("FORCED_RSSI", -127)
        }
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(si)
        else context.startService(si)
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
            @Suppress("DEPRECATION")
            val rssi = try { wm.connectionInfo?.rssi ?: -65 } catch (e: Exception) { -65 }
            val url = URL("$backendUrl/api/heartbeat")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val body = JSONObject().apply {
                put("deviceId", deviceId); put("roomId", roomId)
                put("rssi", rssi); put("wifiBssid", "AA:BB:CC:DD:EE:FF"); put("battery", 50)
            }.toString()
            OutputStreamWriter(conn.outputStream).use { it.write(body); it.flush() }
            Log.i(TAG, "✅ Recovery heartbeat: ${conn.responseCode} RSSI:$rssi")
            conn.disconnect()
        } catch (e: Exception) { Log.e(TAG, "Recovery failed: ${e.message}") }
    }
}
