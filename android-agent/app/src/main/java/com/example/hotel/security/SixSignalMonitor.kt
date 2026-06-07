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

    // Handler bound to main looper (reliable for exact timing when screen is off with WakeLock)
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var currentIntervalMs: Long = 2000L
    private var isMonitoring = false

    // ← FIXED: Cooldown tracking — prevents spamming backend on rapid WiFi toggles
    @Volatile
    private var lastBreachTimestampMs: Long = 0L

    companion object {
        private const val TAG = "SixSignalMonitor"
        // Minimum acceptable signal strength
        private const val RSSI_THRESHOLD = -85
        // ← FIXED: 10-second cooldown between breach alerts to prevent backend spam
        private const val BREACH_COOLDOWN_MS = 10_000L
    }

    private val monitoringRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoring) return

            val status = checkSignals()
            if (status == SecurityStatus.BREACH) {
                // ← FIXED: Use cooldown-aware trigger instead of calling triggerBreach directly
                triggerBreachIfCooldownPassed("Routine 6-Signal Check Failed")
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

    // ← FIXED: WiFi OFF is now checked FIRST as the highest-priority signal
    //          before wasting time on SSID/BSSID/RSSI checks that require WiFi to be on
    private fun checkSignals(): SecurityStatus {
        // Signal 1: WiFi State — checked FIRST, short-circuits all other checks if OFF
        if (!wifiManager.isWifiEnabled) {
            Log.e(TAG, "Signal 1 BREACH: WiFi is Disabled — immediate breach, skipping remaining signals")
            return SecurityStatus.BREACH // ← FIXED: Returns immediately without checking other signals
        }

        // Signal 2: Connection Info Null Check
        val wifiInfo = wifiManager.connectionInfo
        if (wifiInfo == null || wifiInfo.networkId == -1) {
            Log.e(TAG, "Signal 2 BREACH: Connection info is null or invalid")
            return SecurityStatus.BREACH
        }

        // Signal 3: Network Capabilities (Checks if actual WiFi transport is active)
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (activeNetwork == null || caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            Log.e(TAG, "Signal 3 BREACH: No active WiFi transport detected")
            return SecurityStatus.BREACH
        }

        // Fetch expected values from SharedPrefs set during provisioning
        val expectedSsid = sharedPrefs.getString("expected_ssid", "") ?: ""
        val expectedBssid = sharedPrefs.getString("expected_bssid", "") ?: ""

        val currentSsid = wifiInfo.ssid?.replace("\"", "") ?: ""
        val currentBssid = wifiInfo.bssid ?: ""
        val currentRssi = wifiInfo.rssi

        // Signal 4: SSID Match (Detects network switch to a different AP)
        if (expectedSsid.isNotEmpty() && currentSsid != expectedSsid) {
            Log.e(TAG, "Signal 4 BREACH: SSID mismatch. Expected: $expectedSsid, Got: $currentSsid")
            return SecurityStatus.BREACH
        }

        // Signal 5: BSSID Match (Detects rogue AP or mobile hotspot spoofing)
        if (expectedBssid.isNotEmpty() && currentBssid != expectedBssid) {
            Log.e(TAG, "Signal 5 BREACH: BSSID mismatch. Expected: $expectedBssid, Got: $currentBssid")
            return SecurityStatus.BREACH
        }

        // Signal 6: RSSI Threshold (Detects physical movement away from the access point)
        if (currentRssi < RSSI_THRESHOLD) {
            Log.w(TAG, "Signal 6 BREACH: RSSI $currentRssi is below threshold $RSSI_THRESHOLD")
            return SecurityStatus.BREACH
        }

        Log.d(TAG, "All 6 signals SECURE. SSID=$currentSsid RSSI=$currentRssi")
        return SecurityStatus.SECURE
    }

    // ← FIXED: New helper — only fires breach if cooldown window has passed
    //          Prevents backend spam when WiFi is toggled rapidly on/off
    private fun triggerBreachIfCooldownPassed(reason: String) {
        val now = System.currentTimeMillis()
        val timeSinceLastBreach = now - lastBreachTimestampMs

        if (timeSinceLastBreach >= BREACH_COOLDOWN_MS) {
            lastBreachTimestampMs = now // ← Update timestamp BEFORE sending to prevent race condition
            Log.e(TAG, "BREACH CONFIRMED — cooldown passed (${timeSinceLastBreach}ms since last). Reason: $reason")
            triggerBreach(reason)
        } else {
            // Still within cooldown window — log and skip
            val remaining = BREACH_COOLDOWN_MS - timeSinceLastBreach
            Log.d(TAG, "Breach suppressed by cooldown — ${remaining}ms remaining. Reason: $reason")
            // Resume polling so we keep checking
            handler.postDelayed(monitoringRunnable, currentIntervalMs)
        }
    }

    fun triggerBreach(reason: String) {
        Log.e(TAG, "Triggering final breach routine. Reason: $reason")
        // Stop polling loop to prevent further BREACH detections while lockdown is active
        stopMonitoring()

        // Trigger your local lock screen activity here once integrated:
        // val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
        //     flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        // }
        // context.startActivity(lockIntent)

        // Send HTTP POST to backend on a raw background thread (no coroutine dependency)
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
                urlConnection.connectTimeout = 3000 // Short timeout — breach must be fast

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
                // Production: queue this alert locally in SQLite for retry when network returns
            } finally {
                urlConnection?.disconnect()
            }
        }
    }
}
