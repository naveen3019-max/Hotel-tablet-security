import os
import re

def safe_replace(content, old, new, filename):
    if old in content:
        return content.replace(old, new)
    elif re.search(old, content, flags=re.DOTALL):
        return re.sub(old, new, content, flags=re.DOTALL)
    else:
        print(f"Warning: Could not find target in {filename}")
        return content

# 1. KioskService.kt
path1 = 'android-agent/app/src/main/java/com/example/hotel/service/KioskService.kt'
with open(path1, 'r', encoding='utf-8') as f:
    content1 = f.read()

imports = """import java.net.HttpURLConnection
import java.net.URL"""
if "import java.net.HttpURLConnection" not in content1:
    content1 = content1.replace("import kotlinx.coroutines.*", "import kotlinx.coroutines.*\n" + imports)

# add startKeepalive in onCreate
content1 = content1.replace(
    '        Log.d("KioskService", "Foreground service started")',
    '        startKeepalive()\n        Log.d("KioskService", "Foreground service started")'
)

# add startKeepalive method before companion object
methods1 = """    private fun startKeepalive() {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    delay(60_000L) // every 60s
                    pingBackend()
                    Log.d("KioskService", "💓 Keepalive ping sent")
                } catch (e: Exception) {
                    Log.w("KioskService", "Keepalive failed: ${e.message}")
                }
            }
        }
    }

    private suspend fun pingBackend() {
        try {
            val backendUrl = getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
                .getString("backend_base_url", "https://hotel-tablet-security.onrender.com") ?: return
            
            val url = URL("$backendUrl/health")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val code = conn.responseCode
            conn.disconnect()
            Log.d("KioskService", "✅ Render ping: $code")
        } catch (e: Exception) {
            Log.w("KioskService", "Ping failed: ${e.message}")
        }
    }

    companion object {"""
content1 = content1.replace("    companion object {", methods1)

with open(path1, 'w', encoding='utf-8') as f:
    f.write(content1)

# 2. SixSignalMonitor.kt
path2 = 'android-agent/app/src/main/java/com/example/hotel/security/SixSignalMonitor.kt'
with open(path2, 'r', encoding='utf-8') as f:
    content2 = f.read()

if "import kotlinx.coroutines.sync.Mutex" not in content2:
    content2 = content2.replace("import kotlinx.coroutines.launch", "import kotlinx.coroutines.launch\nimport kotlinx.coroutines.sync.Mutex\nimport kotlinx.coroutines.sync.withLock\nimport kotlinx.coroutines.delay")

trigger_breach_repl = """    // ← FIXED BUG 2: triggerBreach now actually sends HTTP POST to backend
    private val breachLock = Mutex()

    fun triggerBreach(reason: String, rssi: Int, isImmediate: Boolean = false) {
        fireBreach(rssi, isImmediate)
    }

    fun fireBreach(rssi: Int = -127, fromReceiver: Boolean = false) {
        // ← FIXED: Always -127 when WiFi off
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val actualRssi = if (!wifiManager.isWifiEnabled || fromReceiver) -127 else rssi
        
        CoroutineScope(Dispatchers.IO).launch {
            breachLock.withLock {
                // ← Dedup check
                val now = SystemClock.elapsedRealtime()
                if (now - lastBreachSentTime < DEDUP_WINDOW_MS) {
                    Log.d(TAG, "Breach dedup — skip")
                    return@withLock
                }
                
                lastBreachSentTime = now
                isBreachActive = true
                
                val breachTime = System.currentTimeMillis()
                var success = false
                
                // First 3 attempts: 1s apart
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
                
                // Next 12 attempts: 2s apart
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
                    // ← All failed store locally
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
        conn.connectTimeout = 3000  // ← 3s fast
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
        
        // ← Discard if older than 10 minutes
        val ageMs = System.currentTimeMillis() - timestamp
        if (ageMs > 10 * 60 * 1000L) {
            Log.w(TAG, "Pending breach too old (${ageMs/1000}s) — discarded")
            clearPendingBreach()
            return
        }
        
        Log.i(TAG, "📤 Sending pending breach from ${ageMs/1000}s ago")
        
        CoroutineScope(Dispatchers.IO).launch {
            // ← Wait 3s for network to stabilize
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

    fun performSecurityCheck()"""

content2 = re.sub(r'    // ← FIXED BUG 2: triggerBreach now actually sends HTTP POST to backend.*?fun performSecurityCheck\(\)', trigger_breach_repl, content2, flags=re.DOTALL)
content2 = content2.replace("fun getInstance(): SixSignalMonitor? = instance", "fun getInstance(): SixSignalMonitor? = instance\n        fun setInstance(monitor: SixSignalMonitor) { instance = monitor }")
if "private var instance:" not in content2:
    content2 = content2.replace("val DEDUP_WINDOW_MS = 30_000L", "val DEDUP_WINDOW_MS = 30_000L\n        @Volatile private var instance: SixSignalMonitor? = null")

with open(path2, 'w', encoding='utf-8') as f:
    f.write(content2)


# 3. ScreenAndWiFiReceiver.kt
path3 = 'android-agent/app/src/main/java/com/example/hotel/security/ScreenAndWiFiReceiver.kt'
with open(path3, 'r', encoding='utf-8') as f:
    content3 = f.read()

imports3 = """import java.net.URL
import java.net.HttpURLConnection
import org.json.JSONObject
import java.io.OutputStreamWriter"""
if "import java.net.URL" not in content3:
    content3 = content3.replace("import android.util.Log", "import android.util.Log\n" + imports3)

receiver_repl = """    override fun onReceive(context: Context, intent: Intent) {
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
}"""
content3 = re.sub(r'    override fun onReceive\(context: Context, intent: Intent\) \{.*$', receiver_repl, content3, flags=re.DOTALL)
with open(path3, 'w', encoding='utf-8') as f:
    f.write(content3)
