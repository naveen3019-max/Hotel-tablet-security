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
import com.example.hotel.service.KioskService
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
        Log.d(TAG, "âœ… WiFi broadcast receiver registered")
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
                        WifiManager.WIFI_STATE_ENABLING -> {
                            // WiFi turning on — reset stabilization so timer re-arms on connect
                            KioskService.wifiTurnedOnAt = 0L
                            KioskService.wifiStabilized = false
                            Log.d(TAG, "WiFi enabling...")
                        }
                        WifiManager.WIFI_STATE_DISABLING -> {
                            Log.e(TAG, "🚨 WiFi DISABLING!")
                            // Reset stabilization — WiFi is going away
                            KioskService.wifiTurnedOnAt = 0L
                            KioskService.wifiStabilized = false
                            sendServiceIntent(context, "WIFI_OFF_BREACH", true)
                        }
                        WifiManager.WIFI_STATE_DISABLED -> {
                            // WiFi fully off — DISABLING already fired the breach intent.
                            // Do NOT fire a second WIFI_OFF_BREACH here; that is the
                            // primary cause of duplicate breach POSTs on the dashboard.
                            // Only reset stabilization counters for the next connect.
                            KioskService.wifiTurnedOnAt = 0L
                            KioskService.wifiStabilized = false
                            Log.d(TAG, "WiFi DISABLED (breach already fired on DISABLING)")
                        }
                        WifiManager.WIFI_STATE_ENABLED -> {
                            Log.d(TAG, "✅ WiFi ENABLED — resetting stabilization, waiting 5s before pending check")
                            // Reset stabilization so KioskService timer starts fresh
                            KioskService.wifiTurnedOnAt = 0L
                            KioskService.wifiStabilized = false
                            // ← FIX: skip the first network check after WiFi reconnects
                            // so a transient SSID read of "" or UNKNOWN doesn't trigger
                            // a false wrong-network breach.
                            KioskService.skipNextNetworkCheck = true

                            // Reset breach active flag so SixSignalMonitor loss timer
                            // doesn't carry over a stale state from when WiFi was off
                            SixSignalMonitor.isBreachActive = false

                            // Notify WiFiMonitoringService that WiFi is restored
                            val si = Intent(context, WiFiMonitoringService::class.java).apply {
                                this.action = "WIFI_RESTORED"
                            }
                            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(si)
                            else context.startService(si)

                            // Wait 5 seconds for network to stabilize, then send a recovery
                            // heartbeat. We no longer send "pending breaches" here, as the
                            // backend's Heartbeat Timeout will have already caught the offline
                            // state, and sending one now creates a false duplicate breach.
                            Handler(Looper.getMainLooper()).postDelayed({
                                Thread { sendRecoveryHeartbeat(context) }.start()
                            }, 5_000L)  // 5-second stabilization delay
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
                            // Guard: skip if a breach POST is already running from the
                            // WIFI_STATE_DISABLING broadcast — avoids a third duplicate.
                            if (SixSignalMonitor.breachPostInFlight) {
                                Log.d(TAG, "CONNECTIVITY_ACTION: breach already in-flight — skipping")
                            } else {
                                Log.e(TAG, "CONNECTIVITY_ACTION: WiFi is OFF, sending breach")
                                sendServiceIntent(context, "WIFI_OFF_BREACH", false)
                            }
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

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // checkNetworkAuthorization â€” called from NetworkCallback (primary) and
    // broadcast receiver (secondary). source tag identifies the call site in logs.
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    fun checkNetworkAuthorization(
        context: Context,
        liveBssid: String? = null,
        liveSsid:  String? = null,
        source:    String  = "unknown"
    ) {
        val prefs = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
        val authorizedBssid = prefs.getString("authorized_bssid", "") ?: ""
        val authorizedSsid = prefs.getString("authorized_ssid", "") ?: ""
        
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) return
        
        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo
        @Suppress("DEPRECATION")
        val currentBssid = info?.bssid ?: ""
        @Suppress("DEPRECATION")
        val currentSsid = info?.ssid?.replace("\"", "")?.trim() ?: ""
        
        Log.d(DBG, "Network auth check: current SSID='$currentSsid' BSSID='$currentBssid' | authorized SSID='$authorizedSsid' BSSID='$authorizedBssid'")
        
        // <- If no authorized network saved, save current network as authorized (first time setup)
        if (authorizedSsid.isEmpty() && authorizedBssid.isEmpty()) {
            if (currentSsid.isNotEmpty() && currentSsid != "<unknown ssid>") {
                Log.i(DBG, "First connection — saving '$currentSsid' as authorized")
                val finalBssid = if (currentBssid == "02:00:00:00:00:00" || currentBssid == "00:00:00:00:00:00") "" else currentBssid
                prefs.edit().apply {
                    putString("authorized_ssid", currentSsid)
                    putString("authorized_bssid", finalBssid)
                    putLong("authorized_network_saved_at", System.currentTimeMillis())
                    apply()
                }
            }
            return
        }
        
        // <- Skip if current SSID unknown
        if (currentSsid.isEmpty() || currentSsid == "<unknown ssid>") {
            Log.w(DBG, "Current SSID unknown — skip check")
            return
        }
        
        val isPrivacyMac = currentBssid == "02:00:00:00:00:00" || currentBssid == "00:00:00:00:00:00"
        
        // <- PRIMARY CHECK: Compare SSID
        if (authorizedSsid.isNotEmpty()) {
            if (currentSsid != authorizedSsid) {
                Log.e(DBG, "🚨 WRONG NETWORK! Expected SSID: '$authorizedSsid' Got: '$currentSsid'")
                triggerNetworkBreach(context, "Wrong WiFi: connected to '$currentSsid' instead of '$authorizedSsid'")
                return
            } else {
                Log.d(DBG, "✅ SSID matches authorized network '$currentSsid'")
            }
        }
        
        // <- SECONDARY CHECK: Compare BSSID
        if (!isPrivacyMac && authorizedBssid.isNotEmpty() && currentBssid.isNotEmpty() && currentBssid != authorizedBssid) {
            if (authorizedSsid.isEmpty()) {
                Log.e(DBG, "🚨 WRONG NETWORK BSSID! Expected: '$authorizedBssid' Got: '$currentBssid'")
                triggerNetworkBreach(context, "Wrong WiFi network detected")
            } else {
                Log.d(DBG, "BSSID different but SSID matches — likely same network different access point")
            }
            return
        }
        
        Log.d(DBG, "✅ Authorized network OK")
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Permission helpers â€” SDK-version-aware, exposed so SixSignalMonitor
    // can call the same logic without duplicating version checks
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    fun isLocationPermissionGranted(context: Context): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) Log.w(DBG, "ACCESS_FINE_LOCATION NOT granted")
        return granted
    }

    fun isNearbyWifiGranted(context: Context): Boolean {
        // â† FIX: NEARBY_WIFI_DEVICES is only a runtime permission on API 33+
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

    // â† canReadWifiIdentity: single entry point for all code that needs to know
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
            Log.i(TAG, "âœ… Recovery heartbeat: ${conn.responseCode} RSSI:$rssi")
            conn.disconnect()
        } catch (e: Exception) { Log.e(TAG, "Recovery failed: ${e.message}") }
    }
}

