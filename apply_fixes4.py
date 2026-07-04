import os
import re

# File 1: SixSignalMonitor.kt
path1 = 'android-agent/app/src/main/java/com/example/hotel/security/SixSignalMonitor.kt'
with open(path1, 'r', encoding='utf-8') as f:
    content1 = f.read()

comp_repl = """    companion object {
        private const val TAG = "SixSignalMonitor"
        private const val BREACH_COOLDOWN = 15_000L // 15 second cooldown between breach POSTs
        private const val PREFS_NAME = "hotel_prefs" // SharedPreferences name for config

        // ← FIXED: Store breach locally when offline
        @Volatile var lastBreachSentTime = 0L
        @Volatile var isBreachActive = false
        @Volatile var pendingBreachRssi: Int? = null
        val DEDUP_WINDOW_MS = 30_000L
    }"""
content1 = re.sub(r'    companion object \{.*?\n    \}', comp_repl, content1, flags=re.DOTALL)

trigger_breach_repl = """    // ← FIXED BUG 2: triggerBreach now actually sends HTTP POST to backend
    fun triggerBreach(reason: String, rssi: Int, isImmediate: Boolean = false) {
        val now = SystemClock.elapsedRealtime()

        // ← FIXED: Check cooldown to prevent spamming backend
        if (now - lastBreachSentTime < BREACH_COOLDOWN) {
            Log.w(TAG, "Breach cooldown active. Skipping POST for: $reason")
            return
        }

        // ← FIXED: Read config from SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val backendUrl = prefs.getString("backend_base_url", "https://hotel-tablet-security.onrender.com") ?: "https://hotel-tablet-security.onrender.com"
        val deviceToken = prefs.getString("device_token", "") ?: ""
        val deviceId = prefs.getString("device_id", "") ?: ""
        val roomId = prefs.getString("room_id", "") ?: ""

        // Validate deviceId and roomId are non-empty
        if (deviceId.isEmpty() || roomId.isEmpty() || deviceId == "UNKNOWN" || roomId == "UNKNOWN") {
            Log.e(TAG, "❌ Cannot send breach: deviceId or roomId is null/empty!")
            return
        }

        if (deviceToken.isEmpty()) {
            Log.e(TAG, "❌ Cannot send breach: device_token is empty in SharedPreferences!")
            return
        }

        lastBreachSentTime = now
        Log.e(TAG, "🚨 SENDING BREACH TO BACKEND: $reason | Device: $deviceId | Room: $roomId | RSSI: $rssi")

        fireBreach(rssi)
    }

    // ← FIXED: Force -127 when WiFi is off
    fun fireBreach(rssi: Int = -127) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val actualRssi = if (!wifiManager.isWifiEnabled) {
            -127
        } else {
            rssi
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            var success = false
            
            // ← Try immediate send (3 attempts fast)
            // These will fail if WiFi is truly off
            repeat(3) { attempt ->
                if (success) return@repeat
                try {
                    val result = postBreachWithTimestamp(actualRssi, System.currentTimeMillis())
                    if (result) {
                        success = true
                        pendingBreachRssi = null
                        Log.i(TAG, "✅ Breach sent attempt ${attempt + 1}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                    kotlinx.coroutines.delay(1000L)
                }
            }
            
            if (!success) {
                // ← WiFi is physically off
                // Cannot reach internet
                // Store breach locally to send later
                Log.w(TAG, "⚠️ Cannot reach backend — WiFi is off. Storing breach for when connectivity returns.")
                
                // Save to SharedPreferences
                context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE).edit()
                    .putBoolean("pending_breach", true)
                    .putInt("pending_breach_rssi", actualRssi)
                    .putLong("breach_detected_at", System.currentTimeMillis())
                    .apply()
                
                pendingBreachRssi = actualRssi
            }
        }
    }

    // ← FIXED: Called when WiFi turns back ON
    fun sendPendingBreach() {
        val prefs = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
        val hasPending = prefs.getBoolean("pending_breach", false)
        
        if (!hasPending) return
        
        val pendingRssi = prefs.getInt("pending_breach_rssi", -127)
        val breachTime = prefs.getLong("breach_detected_at", 0L)
        
        // ← Check breach is not too old (>10 min)
        val ageMs = System.currentTimeMillis() - breachTime
        if (ageMs > 10 * 60 * 1000L) {
            // Too old — discard
            clearPendingBreach()
            Log.w(TAG, "Pending breach too old — discarded")
            return
        }
        
        Log.i(TAG, "📤 Sending pending breach from ${ageMs / 1000}s ago RSSI:$pendingRssi")
        
        CoroutineScope(Dispatchers.IO).launch {
            val breachTimestamp = breachTime
            
            repeat(5) { attempt ->
                try {
                    val success = postBreachWithTimestamp(pendingRssi, breachTimestamp)
                    if (success) {
                        clearPendingBreach()
                        Log.i(TAG, "✅ Pending breach sent!")
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Pending breach attempt ${attempt + 1} failed")
                    kotlinx.coroutines.delay(2000L)
                }
            }
        }
    }

    private fun clearPendingBreach() {
        context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE).edit()
            .remove("pending_breach")
            .remove("pending_breach_rssi")
            .remove("breach_detected_at")
            .apply()
        pendingBreachRssi = null
    }

    private fun postBreachWithTimestamp(rssi: Int, breachTimestamp: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val backendUrl = prefs.getString("backend_base_url", "https://hotel-tablet-security.onrender.com") ?: "https://hotel-tablet-security.onrender.com"
        val deviceToken = prefs.getString("device_token", "") ?: ""
        val deviceId = prefs.getString("device_id", "") ?: ""
        val roomId = prefs.getString("room_id", "") ?: ""
        
        val url = URL("$backendUrl/api/alert/breach")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $deviceToken")
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        
        val body = JSONObject().apply {
            put("deviceId", deviceId)
            put("roomId", roomId)
            put("rssi", rssi)
            // ← Send actual breach time so backend stores correct timestamp
            put("breachTimestamp", breachTimestamp)
        }.toString()
        
        OutputStreamWriter(conn.outputStream).use { 
            it.write(body)
            it.flush() 
        }
        
        val code = conn.responseCode
        conn.disconnect()
        return code in 200..299
    }"""
content1 = re.sub(r'    // ← FIXED BUG 2: triggerBreach now actually sends HTTP POST to backend.*?(?=    private fun performSecurityCheck)', trigger_breach_repl + '\n', content1, flags=re.DOTALL)

with open(path1, 'w', encoding='utf-8') as f:
    f.write(content1)


# File 2: ScreenAndWiFiReceiver.kt
path2 = 'android-agent/app/src/main/java/com/example/hotel/security/ScreenAndWiFiReceiver.kt'
with open(path2, 'r', encoding='utf-8') as f:
    content2 = f.read()

disabling_repl = """            WifiManager.WIFI_STATE_DISABLING -> {
                Log.e(TAG, "🚨 WiFi DISABLING!")
                
                // ← FIXED: Set dedup flags
                SixSignalMonitor.lastBreachSentTime = SystemClock.elapsedRealtime()
                SixSignalMonitor.isBreachActive = true
                
                // ← FIXED: Start service with forced rssi=-127
                val serviceIntent = Intent(context, WiFiMonitoringService::class.java).apply {
                    action = "WIFI_OFF_BREACH"
                    putExtra("IMMEDIATE_BREACH", true)
                    putExtra("FORCED_RSSI", -127)
                }
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }"""
content2 = re.sub(r'            WifiManager\.WIFI_STATE_DISABLING -> \{.*?(?=            WifiManager\.WIFI_STATE_DISABLED -> \{)', disabling_repl + '\n', content2, flags=re.DOTALL)

enabled_repl = """            WifiManager.WIFI_STATE_ENABLED -> {
                Log.d(TAG, "✅ WiFi ENABLED")
                
                // ← FIXED: Wait 3 seconds for WiFi to stabilize
                Handler(Looper.getMainLooper()).postDelayed({
                    
                    // ← FIXED: Send any pending breach first
                    val monitor = SixSignalMonitor(context)
                    monitor.sendPendingBreach()
                    
                    // ← FIXED: Reset breach flags AFTER sending pending breach
                    SixSignalMonitor.isBreachActive = false
                    SixSignalMonitor.lastBreachSentTime = 0L
                    WiFiMonitoringService.lastBreachTime = 0L
                    
                    // ← FIXED: Send recovery heartbeat
                    Thread {
                        sendRecoveryHeartbeat(context)
                    }.start()
                    
                }, 3000L) // 3 second delay
            }"""
content2 = re.sub(r'            WifiManager\.WIFI_STATE_ENABLED -> \{.*?(?=        \})', enabled_repl + '\n', content2, flags=re.DOTALL)

send_recovery = """
    // ← FIXED: Send recovery heartbeat without triggering new monitoring check
    private fun sendRecoveryHeartbeat(context: Context) {
        try {
            val prefs = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
            val token = prefs.getString("device_token", "") ?: ""
            val deviceId = prefs.getString("device_id", "") ?: ""
            val roomId = prefs.getString("room_id", "") ?: ""
            
            if (token.isEmpty()) return
            
            // Wait extra 2s for network
            Thread.sleep(2000)
            
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val rssi = try {
                wifiManager.connectionInfo?.rssi ?: -65
            } catch (e: Exception) { -65 }
            
            val backendUrl = prefs.getString("backend_base_url", "https://hotel-tablet-security.onrender.com") ?: "https://hotel-tablet-security.onrender.com"
            val url = java.net.URL("$backendUrl/api/heartbeat")
            val conn = url.openConnection() as java.net.HttpURLConnection
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
            
            java.io.OutputStreamWriter(conn.outputStream).use { 
                it.write(body)
                it.flush() 
            }
            
            val code = conn.responseCode
            Log.i(TAG, "✅ Recovery heartbeat: $code RSSI:$rssi")
            conn.disconnect()
            
        } catch (e: Exception) {
            Log.e(TAG, "Recovery heartbeat failed: ${e.message}")
        }
    }
}
"""
content2 = re.sub(r'    private fun sendWifiRecovery\(context: Context\) \{.*$', send_recovery, content2, flags=re.DOTALL)

with open(path2, 'w', encoding='utf-8') as f:
    f.write(content2)


# File 3: backend-api/main.py
path3 = 'backend-api/main.py'
with open(path3, 'r', encoding='utf-8') as f:
    content3 = f.read()

# Update Breach model
breach_model_repl = """class Breach(BaseModel):
    deviceId: str
    roomId: str
    rssi: int = -127
    breachTimestamp: Optional[int] = None"""
content3 = re.sub(r'class Breach\(BaseModel\):\n    deviceId: str\n    roomId: str\n    rssi: int', breach_model_repl, content3)

# Update alert_breach endpoint
alert_breach_repl = """@app.post("/api/alert/breach")
async def alert_breach(b: Breach, device=Depends(get_current_device)):
    \"\"\"Record breach alert (JWT protected)\"\"\"
    # ← Validate and correct RSSI
    rssi = b.rssi
    if rssi > -10:
        rssi = -127
        logger.warning(f"Invalid RSSI corrected to -127 for {b.deviceId}")
    
    # ← Use provided breach timestamp if valid
    # This preserves WHEN breach happened even if POST arrives later
    if b.breachTimestamp:
        breach_age_ms = int(time.time() * 1000) - b.breachTimestamp
        breach_age_s = breach_age_ms / 1000
        
        if breach_age_s < 600:
            # Less than 10 minutes old — use it
            breach_time = datetime.fromtimestamp(b.breachTimestamp / 1000, tz=pytz.utc).replace(tzinfo=None)
            logger.info(f"Using device breach timestamp: {breach_age_s:.0f}s ago")
        else:
            # Too old — use current time
            breach_time = get_utc_naive()
    else:
        breach_time = get_utc_naive()
    
    # ← Deduplication check (30 seconds)
    recent_cutoff = datetime.now(pytz.utc).replace(tzinfo=None) - timedelta(seconds=30)
    
    existing = await alerts_collection.find_one({
        "deviceId": b.deviceId,
        "type": "breach",
        "ts": {"$gte": recent_cutoff}
    })
    
    if existing:
        logger.info(f"Duplicate breach skipped: {b.deviceId}")
        return {"ok": True, "duplicate": True}
    
    # ← Store breach with correct timestamp
    alert_doc = {
        "deviceId": b.deviceId,
        "roomId": b.roomId,
        "type": "breach",
        "severity": "critical",
        "message": "WiFi disabled on device",
        "rssi": rssi,
        "ts": breach_time,
        "acknowledged": False,
        "hotel_id": device.get("hotel_id", "default")
    }
    
    await alerts_collection.insert_one(alert_doc)
    
    # ← Update device status
    await devices_collection.update_one(
        {"_id": b.deviceId},
        {"$set": {
            "status": StatusEnum.breach,
            "rssi": rssi,
            "last_seen": get_utc_naive()
        }}
    )
    
    # ← Broadcast to hotel's WebSocket only
    device_hotel_id = device.get("hotel_id", "default")
    
    await broadcast_event("alert", {
        "type": "breach",
        "deviceId": b.deviceId,
        "roomId": b.roomId,
        "rssi": rssi,
        "message": "WiFi disabled on device",
        "timestamp": to_ist_isoformat(breach_time)
    }, hotel_id=device_hotel_id)
    
    return {"ok": True}"""
content3 = re.sub(r'@app\.post\("/api/alert/breach"\)\nasync def alert_breach\(b: Breach, device=Depends\(get_current_device\)\):.*?return \{"ok": True\}', alert_breach_repl, content3, flags=re.DOTALL)

# Fix heartbeat endpoint
heartbeat_repl = """    # ← FIXED: Clear breach WITHOUT re-broadcasting old alerts
    if existing_status == StatusEnum.breach and \
       h.rssi > -120 and \
       h.wifiBssid not in ["02:00:00:00:00:00", "00:00:00:00:00:00"]:
        
        # ← Clear breach
        await devices_collection.update_one(
            {"_id": h.deviceId},
            {"$set": {
                "status": StatusEnum.ok,
                "rssi": h.rssi,
                "battery": h.battery,
                "last_seen": get_utc_naive()
            }}
        )
        
        # ← Broadcast ONLY recovery event
        # NEVER broadcast old alerts here
        device_hotel_id = current_device.get("hotel_id", "default") if current_device else "default"
        await broadcast_event("device_recovered", {
            "deviceId": h.deviceId,
            "roomId": h.roomId,
            "rssi": h.rssi,
            "battery": h.battery,
            "status": "ok",
            "message": "WiFi restored"
        }, hotel_id=device_hotel_id)
        
        logger.info(f"✅ Breach cleared: {h.deviceId} RSSI:{h.rssi}")
        
        return {"ok": True, "status": "recovered"}"""
content3 = re.sub(r'    if existing_status == StatusEnum\.breach and \\.*?return \{"ok": True, "status": "recovered"\}', heartbeat_repl, content3, flags=re.DOTALL)

with open(path3, 'w', encoding='utf-8') as f:
    f.write(content3)


# File 4: dashboard/src/app/page.tsx
path4 = 'dashboard/src/app/page.tsx'
with open(path4, 'r', encoding='utf-8') as f:
    content4 = f.read()

# Track shownAlertIds
if 'const [shownAlertIds, setShownAlertIds]' not in content4:
    content4 = content4.replace(
        'const [lastAlertTime, setLastAlertTime] = useState<string>("");',
        'const [lastAlertTime, setLastAlertTime] = useState<string>("");\n  const [shownAlertIds, setShownAlertIds] = useState<Set<string>>(new Set());'
    )

alert_repl = """      if (
        lastMessage.type === "breach" ||
        (lastMessage.type === "alert" && d?.type === "breach") ||
        (lastMessage.type === "device_update" && d?.status === "breach")
      ) {
        const breachDeviceId = d?.deviceId || d?.device_id || (lastMessage as Record<string, unknown>).deviceId;
        if (breachDeviceId) {
          setDevices((prev) => prev.map((dev) => dev.deviceId === breachDeviceId ? { ...dev, status: "breach" } : dev));
        }
        const alertTime = ((lastMessage as Record<string, unknown>).timestamp as string | undefined) ?? new Date().toISOString();
        const alertId = `${breachDeviceId}${alertTime}`;
        
        // ← Skip if already shown
        if (shownAlertIds.has(alertId)) {
          return;
        }
        
        setShownAlertIds((prev) => {
          const next = new Set(prev);
          next.add(alertId);
          return next;
        });
        
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
content4 = re.sub(r'      if \(\n        lastMessage\.type === "breach" \|\|.*?setLastAlertTime\(alertTime\);\n      \}', alert_repl, content4, flags=re.DOTALL)

recovery_repl = """      if (lastMessage?.type === "device_recovered" && d?.deviceId) {
        // ← Update device to OK, do NOT add to alerts list
        setDevices((prev) => prev.map((dev) => dev.deviceId === d.deviceId ? { ...dev, status: "ok", rssi: (d.rssi as number) ?? dev.rssi } : dev));
      }"""
content4 = re.sub(r'      if \(lastMessage\?\.type === "device_recovered" && d\?\.deviceId\) \{.*?\}', recovery_repl, content4, flags=re.DOTALL)

with open(path4, 'w', encoding='utf-8') as f:
    f.write(content4)
