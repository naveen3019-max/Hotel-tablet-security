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
import android.net.NetworkInfo // ← kept for broadcast compat (deprecated but still works on our minSdk)
import androidx.core.content.ContextCompat
import java.net.URL
import java.net.HttpURLConnection
import org.json.JSONObject
import java.io.OutputStreamWriter

class ScreenAndWiFiReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenAndWiFiReceiver"

        // ← FIX (CAUSE 1): sentinel value that means Android returned junk because
        //   location permission / services are unavailable. We must NOT treat this as
        //   "no network found" — we must treat it as DEGRADED state.
        private const val UNKNOWN_SSID = "<unknown ssid>"
        private const val PRIVACY_MAC  = "02:00:00:00:00:00"
    }

    // ← Flag to prevent duplicate breach alerts on rapid WiFi toggles
    @Volatile
    private var breachAlreadySent = false

    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION) // secondary/backup trigger (kept)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        context.registerReceiver(this, filter)
        Log.d(TAG, "✅ WiFi broadcast receiver registered")
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

                // ← FIX (CAUSE 2): Keep NETWORK_STATE_CHANGED_ACTION as secondary/backup trigger.
                //   Primary trigger is NetworkCallback in WiFiMonitoringService (more reliable on
                //   Samsung/Redmi/Lenovo where this broadcast is flaky during fast hotspot swaps).
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                    @Suppress("DEPRECATION")
                    if (networkInfo?.state == NetworkInfo.State.CONNECTED) {
                        Log.d(TAG, "NETWORK_STATE_CHANGED_ACTION: WiFi connected — running backup check")
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

    // ─────────────────────────────────────────────────────────────────────────
    // ← FIX (CAUSE 1 + CAUSE 2): checkNetworkAuthorization now accepts optional
    //   bssid/ssid from NetworkCallback so we use the LIVE WifiInfo delivered by
    //   ConnectivityManager rather than the deprecated WifiManager.connectionInfo.
    //   When called from the backup broadcast path, bssid/ssid are null and we fall
    //   back to WifiManager — same as before.
    // ─────────────────────────────────────────────────────────────────────────
    fun checkNetworkAuthorization(
        context: Context,
        liveBssid: String? = null,  // ← FIX (CAUSE 2): live value from NetworkCallback
        liveSsid: String?  = null   // ← FIX (CAUSE 2): live value from NetworkCallback
    ) {
        val prefs = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
        
        val authorizedBssid = prefs.getString("authorized_bssid", "") ?: ""
        val authorizedSsid  = prefs.getString("authorized_ssid",  "") ?: ""
        
        // If no authorized network saved, skip (not yet provisioned)
        if (authorizedBssid.isEmpty() && authorizedSsid.isEmpty()) {
            Log.d(TAG, "No authorized network saved — skipping check")
            return
        }

        // ← FIX (CAUSE 1): Check location permission and location services before
        //   trusting any BSSID/SSID value. Without these, Android always returns
        //   UNKNOWN_SSID / PRIVACY_MAC regardless of which network is connected.
        if (!isLocationPermissionGranted(context)) {
            Log.e(TAG,
                "⚠️ WIFI IDENTITY UNAVAILABLE — ACCESS_FINE_LOCATION not granted. " +
                "Cannot verify authorized network. Triggering degraded-state breach.")
            triggerNetworkBreach(
                context,
                "Cannot verify network identity — location permission denied"
            )
            return
        }
        if (!isLocationEnabled(context)) {
            Log.e(TAG,
                "⚠️ WIFI IDENTITY UNAVAILABLE — Location services are OFF. " +
                "Cannot verify authorized network. Triggering degraded-state breach.")
            triggerNetworkBreach(
                context,
                "Cannot verify network identity — location services disabled"
            )
            return
        }

        // ← FIX (CAUSE 2): Prefer the live values from NetworkCallback; fall back to
        //   WifiManager.connectionInfo only when called from the backup broadcast path.
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) return

        val currentBssid: String
        val currentSsid: String

        if (liveBssid != null && liveSsid != null) {
            // Called from NetworkCallback — use the fresh WifiInfo directly
            currentBssid = liveBssid
            currentSsid  = liveSsid
            Log.d(TAG, "NetworkCallback path — BSSID=$currentBssid SSID=$currentSsid")
        } else {
            // Called from broadcast backup path — use WifiManager
            @Suppress("DEPRECATION")
            val info = wifiManager.connectionInfo ?: return
            @Suppress("DEPRECATION")
            currentBssid = info.bssid ?: ""
            @Suppress("DEPRECATION")
            currentSsid  = info.ssid?.replace("\"", "") ?: ""
            Log.d(TAG, "Broadcast path — BSSID=$currentBssid SSID=$currentSsid")
        }

        Log.d(TAG, "Network check: current=$currentBssid/$currentSsid  authorized=$authorizedBssid/$authorizedSsid")

        val isPrivacyMac = currentBssid == PRIVACY_MAC

        // 1. Compare BSSID if available and not randomized
        if (authorizedBssid.isNotEmpty() && !isPrivacyMac && currentBssid.isNotEmpty()) {
            if (currentBssid != authorizedBssid) {
                Log.e(TAG, "🚨 UNAUTHORIZED NETWORK! Expected BSSID: $authorizedBssid Got: $currentBssid")
                triggerNetworkBreach(context, "Wrong WiFi network: $currentBssid")
            } else {
                Log.d(TAG, "✅ Authorized network confirmed (BSSID match)")
            }
            return
        }

        // 2. Fall back to SSID comparison
        if (authorizedSsid.isNotEmpty() && currentSsid.isNotEmpty() && currentSsid != UNKNOWN_SSID) {
            if (currentSsid != authorizedSsid) {
                Log.e(TAG, "🚨 UNAUTHORIZED NETWORK! Expected SSID: $authorizedSsid Got: $currentSsid")
                triggerNetworkBreach(context, "Wrong WiFi network: $currentSsid")
            } else {
                Log.d(TAG, "✅ Authorized network confirmed (SSID match)")
            }
        } else if (currentSsid == UNKNOWN_SSID) {
            // ← FIX (CAUSE 1): SSID is still masked — Android returned junk despite permission check.
            //   This can happen during a brief handoff window. Log it but do NOT silently pass.
            Log.w(TAG,
                "⚠️ SSID still '$UNKNOWN_SSID' after permission check — " +
                "possible brief handoff window. Will re-check on next NetworkCallback.")
        }
    }

    // ← FIX (CAUSE 1): Dedicated helpers for permission and location-services checks.
    //   Centralised here so both checkNetworkAuthorization() and SixSignalMonitor can
    //   call the same logic without duplication.
    fun isLocationPermissionGranted(context: Context): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) Log.w(TAG, "ACCESS_FINE_LOCATION NOT granted")
        return granted
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
        if (!enabled) Log.w(TAG, "Location services are DISABLED")
        return enabled
    }

    // ← NEW: trigger breach for wrong network — delegates to WiFiMonitoringService
    private fun triggerNetworkBreach(context: Context, reason: String) {
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
