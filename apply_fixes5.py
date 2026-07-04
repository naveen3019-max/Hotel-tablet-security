import os
import re

# File 1: KioskService.kt
path1 = 'android-agent/app/src/main/java/com/example/hotel/service/KioskService.kt'
with open(path1, 'r', encoding='utf-8') as f:
    content1 = f.read()

keepalive_imports = """import java.net.HttpURLConnection
import java.net.URL"""
if "import java.net.HttpURLConnection" not in content1:
    content1 = content1.replace("import kotlinx.coroutines.*", "import kotlinx.coroutines.*\n" + keepalive_imports)

# Add startKeepalive call to onCreate
content1 = re.sub(r'(        Log\.d\("KioskService", "Foreground service started"\)\n    \})', r'        startKeepalive()\n\1', content1)

# Add the methods
keepalive_methods = """
    private fun startKeepalive() {
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
"""
if "startKeepalive" not in content1:
    content1 = content1.replace("class KioskService : Service() {", "class KioskService : Service() {\n" + keepalive_methods)

with open(path1, 'w', encoding='utf-8') as f:
    f.write(content1)

# File 2: SixSignalMonitor.kt
path2 = 'android-agent/app/src/main/java/com/example/hotel/security/SixSignalMonitor.kt'
with open(path2, 'r', encoding='utf-8') as f:
    content2 = f.read()

if "import kotlinx.coroutines.sync.Mutex" not in content2:
    content2 = content2.replace("import kotlinx.coroutines.launch", "import kotlinx.coroutines.launch\nimport kotlinx.coroutines.sync.Mutex\nimport kotlinx.coroutines.sync.withLock\nimport kotlinx.coroutines.delay")

trigger_breach_repl = """
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
"""

content2 = re.sub(r'    fun triggerBreach.*?fun resetBreachState\(\) \{', trigger_breach_repl + '\n\n    fun resetBreachState() {', content2, flags=re.DOTALL)
with open(path2, 'w', encoding='utf-8') as f:
    f.write(content2)

# File 3: ScreenAndWiFiReceiver.kt
path3 = 'android-agent/app/src/main/java/com/example/hotel/security/ScreenAndWiFiReceiver.kt'
with open(path3, 'r', encoding='utf-8') as f:
    content3 = f.read()

receiver_repl = """    override fun onReceive(context: Context, intent: Intent) {
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
            
            val body = org.json.JSONObject().apply {
                put("deviceId", deviceId)
                put("roomId", roomId)
                put("rssi", rssi)
                put("wifiBssid", "AA:BB:CC:DD:EE:FF")
                put("battery", 50)
            }.toString()
            
            java.io.OutputStreamWriter(conn.outputStream).use { it.write(body); it.flush() }
            
            val code = conn.responseCode
            Log.i(TAG, "✅ Recovery heartbeat: $code RSSI:$rssi")
            conn.disconnect()
            
        } catch (e: Exception) {
            Log.e(TAG, "Recovery failed: ${e.message}")
        }
    }
}
"""

content3 = re.sub(r'    override fun onReceive\(context: Context, intent: Intent\) \{.*$', receiver_repl, content3, flags=re.DOTALL)
with open(path3, 'w', encoding='utf-8') as f:
    f.write(content3)


# File 4: main.py
path4 = 'backend-api/main.py'
with open(path4, 'r', encoding='utf-8') as f:
    content4 = f.read()

# Add keepalive task
keepalive_task = """
@app.on_event("startup")
async def startup_event():
    # Keepalive to prevent render sleeping
    async def keepalive_task():
        while True:
            try:
                await asyncio.sleep(60)
                await devices_collection.find_one({})
                print("💓 Keepalive", flush=True)
            except Exception as e:
                logger.error(f"Keepalive: {e}")
    asyncio.create_task(keepalive_task())
"""
if "Keepalive to prevent render sleeping" not in content4:
    content4 = content4.replace("app = FastAPI(", "app = FastAPI(\n" + keepalive_task)

# Health endpoint just in case
if "/health" not in content4:
    content4 += "\n@app.get('/health')\nasync def health():\n    return {'status':'ok'}\n"

with open(path4, 'w', encoding='utf-8') as f:
    f.write(content4)

# File 5: dashboard/src/app/page.tsx
path5 = 'dashboard/src/app/page.tsx'
with open(path5, 'r', encoding='utf-8') as f:
    content5 = f.read()

if "const shownAlertIds = useRef<Set<string>>(new Set())" not in content5:
    content5 = content5.replace("const [shownAlertIds, setShownAlertIds] = useState<Set<string>>(new Set());", "const shownAlertIds = useRef<Set<string>>(new Set());")

alert_repl2 = """      if (
        lastMessage.type === "breach" ||
        (lastMessage.type === "alert" && d?.type === "breach") ||
        (lastMessage.type === "device_update" && d?.status === "breach")
      ) {
        const breachDeviceId = d?.deviceId || d?.device_id || (lastMessage as Record<string, unknown>).deviceId;
        if (breachDeviceId) {
          setDevices((prev) => prev.map((dev) => dev.deviceId === breachDeviceId ? { ...dev, status: "breach" } : dev));
        }
        const alertTime = ((lastMessage as Record<string, unknown>).timestamp as string | undefined) ?? new Date().toISOString();
        const alertId = `${breachDeviceId}_${alertTime}`;
        
        if (shownAlertIds.current.has(alertId)) {
          return;
        }
        shownAlertIds.current.add(alertId);
        
        const newAlert: Alert = {
          id: alertId,
          type: "breach",
          deviceId: (breachDeviceId as string) || "Unknown",
          roomId: d?.roomId as string | undefined,
          ts: alertTime,
          acknowledged: false,
          message: d?.message as string | undefined,
        };
        
        setAlerts((prev) => [newAlert, ...prev].sort((a, b) => new Date(b.ts).getTime() - new Date(a.ts).getTime()).slice(0, 100));
        setLastAlertTime(alertTime);
        
        if (breachDeviceId) {
          addToast(breachDeviceId as string, d?.roomId as string | undefined, d?.message as string | undefined);
          if (typeof window !== "undefined" && "Notification" in window && Notification.permission === "granted") {
            const notif = new Notification("🚨 SECURITY BREACH DETECTED", {
              body: `Device ${breachDeviceId} ${d?.roomId ? `(Room ${d.roomId})` : ""} - ${d?.message || "Immediate attention required"}`,
            });
            notif.onclick = () => { window.focus(); notif.close(); };
          }
        }
      }"""
content5 = re.sub(r'      if \(\n        lastMessage\.type === "breach" \|\|.*?setLastAlertTime\(alertTime\);\n        \n        if \(breachDeviceId\) \{.*?\n        \}\n      \}', alert_repl2, content5, flags=re.DOTALL)

with open(path5, 'w', encoding='utf-8') as f:
    f.write(content5)
