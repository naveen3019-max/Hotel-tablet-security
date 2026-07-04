import os
import re

path2 = 'android-agent/app/src/main/java/com/example/hotel/security/ScreenAndWiFiReceiver.kt'
with open(path2, 'r', encoding='utf-8') as f:
    content2 = f.read()

disabling_repl = """                        WifiManager.WIFI_STATE_DISABLING -> {
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

content2 = re.sub(r'                        WifiManager\.WIFI_STATE_DISABLING -> \{.*?(?=                        WifiManager\.WIFI_STATE_DISABLED -> \{)', disabling_repl + '\n\n', content2, flags=re.DOTALL)


enabled_repl = """                        WifiManager.WIFI_STATE_ENABLED -> {
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
content2 = re.sub(r'                        WifiManager\.WIFI_STATE_ENABLED -> \{.*?(?=                        else -> \{)', enabled_repl + '\n\n', content2, flags=re.DOTALL)


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
