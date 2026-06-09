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

    // ← FIXED: Handler bound to main looper
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var currentIntervalMs: Long = 2000L
    private var isMonitoring = false

    @Volatile
    private var lastBreachTimestampMs: Long = 0L

    companion object {
        private const val TAG = "SixSignalMonitor"
        private const val RSSI_THRESHOLD = -85
        private const val BREACH_COOLDOWN_MS = 10_000L
    }

    private val monitoringRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoring) return

            // ← FIXED: Log every check with timestamp for debugging Doze mode drops
            Log.d(TAG, "[${System.currentTimeMillis()}] Executing signal check...")

            try { // ← FIXED: Wrap entire block in try/catch so handler NEVER dies
                val status = checkSignals()
                if (status == SecurityStatus.BREACH) {
                    triggerBreachIfCooldownPassed("Routine 6-Signal Check Failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL ERROR during signal check: ${e.message}", e)
            } finally {
                // ← FIXED: Always reschedule, even if there was an exception
                if (isMonitoring) {
                    handler.postDelayed(this, currentIntervalMs)
                }
            }
        }
    }

    fun startMonitoring() {
        if (!isMonitoring) { // ← FIXED: Guard against double starts
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

    // ← FIXED: Added helper for Watchdog to check if loop is still active
    fun isMonitoringAlive(): Boolean {
        return isMonitoring
    }

    fun setInterval(interval: Long) {
        if (currentIntervalMs != interval) {
            currentIntervalMs = interval
            handler.removeCallbacks(monitoringRunnable)
            if (isMonitoring) {
                handler.post(monitoringRunnable)
            }
        }
    }

    private fun checkSignals(): SecurityStatus {
        if (!wifiManager.isWifiEnabled) {
            Log.e(TAG, "Signal 1 BREACH: WiFi is Disabled — immediate breach, skipping remaining signals")
            return SecurityStatus.BREACH
        }

        val wifiInfo = wifiManager.connectionInfo
        if (wifiInfo == null || wifiInfo.networkId == -1) {
            Log.e(TAG, "Signal 2 BREACH: Connection info is null or invalid")
            return SecurityStatus.BREACH
        }

        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (activeNetwork == null || caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            Log.e(TAG, "Signal 3 BREACH: No active WiFi transport detected")
            return SecurityStatus.BREACH
        }

        val expectedSsid = sharedPrefs.getString("expected_ssid", "") ?: ""
        val expectedBssid = sharedPrefs.getString("expected_bssid", "") ?: ""

        val currentSsid = wifiInfo.ssid?.replace("\"", "") ?: ""
        val currentBssid = wifiInfo.bssid ?: ""
        val currentRssi = wifiInfo.rssi

        if (expectedSsid.isNotEmpty() && currentSsid != expectedSsid) {
            Log.e(TAG, "Signal 4 BREACH: SSID mismatch. Expected: $expectedSsid, Got: $currentSsid")
            return SecurityStatus.BREACH
        }

        if (expectedBssid.isNotEmpty() && currentBssid != expectedBssid) {
            Log.e(TAG, "Signal 5 BREACH: BSSID mismatch. Expected: $expectedBssid, Got: $currentBssid")
            return SecurityStatus.BREACH
        }

        if (currentRssi < RSSI_THRESHOLD) {
            Log.w(TAG, "Signal 6 BREACH: RSSI $currentRssi is below threshold $RSSI_THRESHOLD")
            return SecurityStatus.BREACH
        }

        Log.d(TAG, "All 6 signals SECURE. SSID=$currentSsid RSSI=$currentRssi")
        return SecurityStatus.SECURE
    }

    private fun triggerBreachIfCooldownPassed(reason: String) {
        val now = System.currentTimeMillis()
        val timeSinceLastBreach = now - lastBreachTimestampMs

        if (timeSinceLastBreach >= BREACH_COOLDOWN_MS) {
            lastBreachTimestampMs = now
            Log.e(TAG, "BREACH CONFIRMED — cooldown passed (${timeSinceLastBreach}ms since last). Reason: $reason")
            triggerBreach(reason)
        } else {
            val remaining = BREACH_COOLDOWN_MS - timeSinceLastBreach
            Log.d(TAG, "Breach suppressed by cooldown — ${remaining}ms remaining. Reason: $reason")
        }
    }

    fun triggerBreach(reason: String) {
        Log.e(TAG, "Triggering final breach routine. Reason: $reason")
        stopMonitoring()
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
                urlConnection.connectTimeout = 3000

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
                Log.i(TAG, "Breach alert sent to backend. Response code: $responseCode")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send breach alert to backend: ${e.message}")
            } finally {
                urlConnection?.disconnect()
            }
        }
    }
}
