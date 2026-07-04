import os

# 1. ScreenAndWiFiReceiver.kt
path1 = 'android-agent/app/src/main/java/com/example/hotel/security/ScreenAndWiFiReceiver.kt'
with open(path1, 'r', encoding='utf-8') as f:
    content1 = f.read()

imports = """import java.net.URL
import java.net.HttpURLConnection
import org.json.JSONObject
import java.io.OutputStreamWriter"""
if "import java.net.URL" not in content1:
    content1 = content1.replace("import android.util.Log", "import android.util.Log\n" + imports)

# We want to replace the whole onReceive method and add sendRecoveryHeartbeat.
# I will just replace from `override fun onReceive` to the end of the file.
receiver_code = """    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HotelSecurity::ScreenAndWiFiReceiver"
        )
        wl.acquire(3000)
        try {
            when (intent.action) {
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                    
                    when (wifiState) {
                        WifiManager.WIFI_STATE_DISABLING -> {
                            Log.e(TAG, "🚨 WiFi DISABLING!")
                            
                            SixSignalMonitor.lastBreachSentTime = SystemClock.elapsedRealtime()
                            SixSignalMonitor.isBreachActive = true
                            
                            val serviceIntent = Intent(context, WiFiMonitoringService::class.java).apply {
                                action = "WIFI_OFF_BREACH"
                                putExtra("IMMEDIATE_BREACH", true)
                                putExtra("FORCED_RSSI", -127)
                            }
                            if (Build.VERSION.SDK_INT >= 26){
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        }
                        
                        WifiManager.WIFI_STATE_DISABLED -> {
                            Log.d(TAG, "WiFi DISABLED")
                        }
                        
                        WifiManager.WIFI_STATE_ENABLED -> {
                            Log.d(TAG, "✅ WiFi ENABLED")
                            
                            SixSignalMonitor.isBreachActive = false
                            SixSignalMonitor.lastBreachSentTime = 0L
                            
                            Handler(Looper.getMainLooper()).postDelayed({
                                SixSignalMonitor.getInstance()?.sendPendingBreachIfExists()
                            }, 3000L)
                            
                            Handler(Looper.getMainLooper()).postDelayed({
                                Thread {
                                    sendRecoveryHeartbeat(context)
                                }.start()
                            }, 5000L)
                        }
                        
                        WifiManager.WIFI_STATE_ENABLING -> {
                            Log.d(TAG, "WiFi enabling...")
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

    private fun sendRecoveryHeartbeat(context: Context) {
        try {
            val prefs = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
            val token = prefs.getString("device_token", "") ?: return
            val deviceId = prefs.getString("device_id", "") ?: return
            val roomId = prefs.getString("room_id", "") ?: return
            val backendUrl = prefs.getString("backend_base_url", "https://hotel-tablet-security.onrender.com") ?: return
            
            Thread.sleep(2000)
            
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val rssi = try {
                wm.connectionInfo?.rssi ?: -65
            } catch (e: Exception) { -65 }
            
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
"""
start_idx = content1.find("    override fun onReceive")
if start_idx != -1:
    content1 = content1[:start_idx] + receiver_code
else:
    print("Could not find onReceive in ScreenAndWiFiReceiver.kt")

with open(path1, 'w', encoding='utf-8') as f:
    f.write(content1)


# 2. SixSignalMonitor.kt
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

"""
# Replace from `fun triggerBreach` up to `fun performSecurityCheck()` or `fun checkConnection`
start_idx = content2.find("    // ← FIXED BUG 2: triggerBreach now actually sends HTTP POST to backend")
if start_idx == -1:
    start_idx = content2.find("    fun triggerBreach")

end_idx = content2.find("    private fun performSecurityCheck()")
if end_idx == -1:
    end_idx = content2.find("    fun performSecurityCheck()")

if start_idx != -1 and end_idx != -1:
    content2 = content2[:start_idx] + six_methods + content2[end_idx:]
else:
    print(f"Could not find replacement points in SixSignalMonitor.kt: start={start_idx}, end={end_idx}")

with open(path2, 'w', encoding='utf-8') as f:
    f.write(content2)

