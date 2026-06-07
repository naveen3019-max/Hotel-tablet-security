package com.example.hotel.security

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

enum class SecurityStatus {
    SECURE, WARNING, BREACH
}

class SixSignalMonitor(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("HotelPrefs", Context.MODE_PRIVATE)
    
    // Handler bound to main looper (or background looper if preferred, but Main is reliable for exact timing)
    private val handler = Handler(Looper.getMainLooper())
    
    @Volatile
    private var currentIntervalMs: Long = 2000L
    private var isMonitoring = false

    companion object {
        private const val TAG = "SixSignalMonitor"
        // Minimum acceptable signal strength
        private const val RSSI_THRESHOLD = -85
    }

    private val monitoringRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoring) return

            val status = checkSignals()
            if (status == SecurityStatus.BREACH) {
                // If it's a breach, stop normal polling to prevent spam, 
                // but the system will handle lockdown.
                triggerBreach("Routine Check Failed")
            } else {
                // Queue next check
                handler.postDelayed(this, currentIntervalMs)
            }
        }
    }

    fun startMonitoring() {
        if (!isMonitoring) {
            isMonitoring = true
            handler.post(monitoringRunnable)
            Log.d(TAG, "Started monitoring loop at ${currentIntervalMs}ms interval")
        }
    }

    fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacks(monitoringRunnable)
        Log.d(TAG, "Stopped monitoring loop")
    }

    fun setInterval(interval: Long) {
        if (currentIntervalMs != interval) {
            currentIntervalMs = interval
            // Restart the loop immediately with new interval
            handler.removeCallbacks(monitoringRunnable)
            if (isMonitoring) {
                handler.post(monitoringRunnable)
            }
        }
    }

    private fun checkSignals(): SecurityStatus {
        // Signal 1: WiFi State
        if (!wifiManager.isWifiEnabled) {
            Log.e(TAG, "Signal 1 Failed: WiFi is Disabled")
            return SecurityStatus.BREACH
        }

        // Signal 2: Connection Info Null Check
        val wifiInfo = wifiManager.connectionInfo
        if (wifiInfo == null || wifiInfo.networkId == -1) {
            Log.e(TAG, "Signal 2 Failed: Connection info is null or invalid")
            return SecurityStatus.BREACH
        }

        // Signal 3: Network Capabilities (Checks if actual internet/network is reachable)
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (activeNetwork == null || caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            Log.e(TAG, "Signal 3 Failed: No active WiFi transport detected")
            return SecurityStatus.BREACH
        }

        // We fetch expected values from prefs set during provisioning
        val expectedSsid = sharedPrefs.getString("expected_ssid", "") ?: ""
        val expectedBssid = sharedPrefs.getString("expected_bssid", "") ?: ""

        val currentSsid = wifiInfo.ssid?.replace("\"", "") ?: ""
        val currentBssid = wifiInfo.bssid ?: ""
        val currentRssi = wifiInfo.rssi

        // Signal 4: SSID Match (Detects network switch)
        if (expectedSsid.isNotEmpty() && currentSsid != expectedSsid) {
            Log.e(TAG, "Signal 4 Failed: SSID mismatch. Expected: $expectedSsid, Got: $currentSsid")
            return SecurityStatus.BREACH
        }

        // Signal 5: BSSID Match (Detects rogue AP or mobile hotspot spoofing)
        // If expectedBssid is configured, we must match it
        if (expectedBssid.isNotEmpty() && currentBssid != expectedBssid) {
            Log.e(TAG, "Signal 5 Failed: BSSID mismatch. Expected: $expectedBssid, Got: $currentBssid")
            return SecurityStatus.BREACH
        }

        // Signal 6: RSSI Threshold (Detects physical movement away from AP)
        if (currentRssi < RSSI_THRESHOLD) {
            Log.w(TAG, "Signal 6 Failed: RSSI $currentRssi is below threshold $RSSI_THRESHOLD")
            return SecurityStatus.BREACH
        }

        return SecurityStatus.SECURE
    }

    fun triggerBreach(reason: String) {
        Log.e(TAG, "Triggering final breach routine. Reason: $reason")
        // Prevent recursive spamming
        stopMonitoring() 
        
        // 1. You would trigger your local lock screen activity here
        // val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
        //     flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        // }
        // context.startActivity(lockIntent)
        
        // 2. Send network request in a dedicated background thread (not coroutine)
        sendBreachAlertToBackend(reason)
    }

    private fun sendBreachAlertToBackend(reason: String) {
        val baseUrl = sharedPrefs.getString("backend_base_url", "https://hotel-backend-zqc1.onrender.com")
        val deviceId = sharedPrefs.getString("device_id", "UNKNOWN_DEVICE")
        
        thread {
            var urlConnection: HttpURLConnection? = null
            try {
                val url = URL("$baseUrl/api/v1/alerts/breach")
                urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.requestMethod = "POST"
                urlConnection.setRequestProperty("Content-Type", "application/json")
                urlConnection.setRequestProperty("Accept", "application/json")
                urlConnection.doOutput = true
                urlConnection.connectTimeout = 3000 // Very short timeout for quick failover

                val jsonPayload = """
                    {
                        "device_id": "$deviceId",
                        "reason": "$reason",
                        "timestamp": ${System.currentTimeMillis()}
                    }
                """.trimIndent()

                val writer = OutputStreamWriter(urlConnection.outputStream)
                writer.write(jsonPayload)
                writer.flush()
                writer.close()

                val responseCode = urlConnection.responseCode
                Log.i(TAG, "Breach alert sent. Backend responded with: $responseCode")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send breach alert: ${e.message}")
                // In a full implementation, you would queue this alert locally in SQLite here
            } finally {
                urlConnection?.disconnect()
            }
        }
    }
}
