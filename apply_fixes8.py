import os
import re

path2 = 'android-agent/app/src/main/java/com/example/hotel/security/SixSignalMonitor.kt'
with open(path2, 'r', encoding='utf-8') as f:
    content2 = f.read()

imports2 = """import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay"""
if "import kotlinx.coroutines.sync.Mutex" not in content2:
    content2 = content2.replace("import kotlinx.coroutines.launch", "import kotlinx.coroutines.launch\n" + imports2)

if "fun getInstance(): SixSignalMonitor?" not in content2:
    content2 = content2.replace(
        "val DEDUP_WINDOW_MS = 30_000L",
        "val DEDUP_WINDOW_MS = 30_000L\n        @Volatile private var instance: SixSignalMonitor? = null\n        fun getInstance(): SixSignalMonitor? = instance\n        fun setInstance(monitor: SixSignalMonitor) { instance = monitor }"
    )

six_methods = """    private val breachLock = Mutex()

    fun triggerBreach(reason: String, rssi: Int, isImmediate: Boolean = false) {
        fireBreach(rssi, isImmediate)
    }

    fun fireBreach(rssi: Int = -127, fromReceiver: Boolean = false) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val actualRssi = if (!wifiManager.isWifiEnabled || fromReceiver) -127 else rssi
        
        CoroutineScope(Dispatchers.IO).launch {
            breachLock.withLock {
                val now = SystemClock.elapsedRealtime()
                if (now - lastBreachSentTime < DEDUP_WINDOW_MS) {
                    Log.d(TAG, "Breach dedup — skip")
                    return@withLock
                }
                
                lastBreachSentTime = now
                isBreachActive = true
                
                val breachTime = System.currentTimeMillis()
                var success = false
                
                repeat(3) { attempt ->
                    if (success) return@repeat
                    try {
                        success = postBreach(actualRssi, breachTime)
                        if (success) {
                            Log.i(TAG, "✅ Breach sent attempt ${attempt+1}")
                            clearPendingBreach()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Attempt ${attempt+1}: ${e.message}")
                    }
                    if (!success) delay(1000L)
                }
                
                if (success) return@withLock
                
                repeat(12) { attempt ->
                    if (success) return@repeat
                    try {
                        success = postBreach(actualRssi, breachTime)
                        if (success) {
                            Log.i(TAG, "✅ Breach sent attempt ${attempt+4}")
                            clearPendingBreach()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Attempt ${attempt+4}: ${e.message}")
                    }
                    if (!success) delay(2000L)
                }
                
                if (!success) {
                    Log.w(TAG, "⚠️ All attempts failed. Storing breach locally.")
                    savePendingBreach(actualRssi, breachTime)
                }
            }
        }
    }

    private fun postBreach(rssi: Int, breachTimestamp: Long): Boolean {
        val prefs = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
        val token = prefs.getString("device_token", "") ?: return false
        val deviceId = prefs.getString("device_id", "") ?: return false
        val roomId = prefs.getString("room_id", "") ?: return false
        val backendUrl = prefs.getString("backend_base_url", "https://hotel-tablet-security.onrender.com") ?: return false
        
        val url = URL("$backendUrl/api/alert/breach")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.doOutput = true
        conn.connectTimeout = 3000
        conn.readTimeout = 5000
        
        val body = JSONObject().apply {
            put("deviceId", deviceId)
            put("roomId", roomId)
            put("rssi", rssi)
            put("breachTimestamp", breachTimestamp)
        }.toString()
        
        OutputStreamWriter(conn.outputStream).use { it.write(body); it.flush() }
        
        val code = conn.responseCode
        conn.disconnect()
        return code in 200..299
    }

    private fun savePendingBreach(rssi: Int, timestamp: Long) {
        context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE).edit().apply {
            putBoolean("pending_breach", true)
            putInt("pending_breach_rssi", rssi)
            putLong("pending_breach_time", timestamp)
            apply()
        }
    }

    private fun clearPendingBreach() {
        context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE).edit().apply {
            remove("pending_breach")
            remove("pending_breach_rssi")
            remove("pending_breach_time")
            apply()
        }
    }

    fun sendPendingBreachIfExists() {
        val prefs = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
        
        if (!prefs.getBoolean("pending_breach", false)) return
        
        val rssi = prefs.getInt("pending_breach_rssi", -127)
        val timestamp = prefs.getLong("pending_breach_time", 0L)
        
        val ageMs = System.currentTimeMillis() - timestamp
        if (ageMs > 10 * 60 * 1000L) {
            Log.w(TAG, "Pending breach too old (${ageMs/1000}s) — discarded")
            clearPendingBreach()
            return
        }
        
        Log.i(TAG, "📤 Sending pending breach from ${ageMs/1000}s ago")
        
        CoroutineScope(Dispatchers.IO).launch {
            delay(3000L)
            
            repeat(5) { attempt ->
                try {
                    val success = postBreach(rssi, timestamp)
                    if (success) {
                        clearPendingBreach()
                        Log.i(TAG, "✅ Pending breach sent!")
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Pending attempt $attempt: ${e.message}")
                }
                delay(2000L)
            }
        }
    }

    private fun performSecurityCheck()"""

start_idx = content2.find("    // ← FIXED BUG 2: triggerBreach now actually sends HTTP POST to backend")
end_idx = content2.find("    private fun performSecurityCheck()")
if start_idx != -1 and end_idx != -1:
    content2 = content2[:start_idx] + six_methods + content2[end_idx + len("    private fun performSecurityCheck()"):]
else:
    print(f"Could not find replacement points in SixSignalMonitor.kt: start={start_idx}, end={end_idx}")

with open(path2, 'w', encoding='utf-8') as f:
    f.write(content2)

