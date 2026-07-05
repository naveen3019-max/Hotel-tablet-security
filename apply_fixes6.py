import os
import re

base_dir = r"c:\Users\navee\Downloads\Hotel-tablet-security-master\Hotel-tablet-security-master\WEDDING-CARD-cc895524abaddd4e0e79cc06099f9f102c0f16c7"
receiver_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\security\ScreenAndWiFiReceiver.kt")
wifi_service_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\security\WiFiMonitoringService.kt")
monitor_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\security\SixSignalMonitor.kt")

# 1. ScreenAndWiFiReceiver.kt
with open(receiver_path, "r", encoding="utf-8") as f:
    receiver_code = f.read()

# I will replace the entire onReceive method body to be clean and exact.
new_onreceive = '''    override fun onReceive(context: Context, intent: Intent) {
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
    }'''

receiver_code = re.sub(r'    override fun onReceive\(context: Context, intent: Intent\) \{.*?(?=    private fun sendRecoveryHeartbeat)', new_onreceive + '\n\n', receiver_code, flags=re.DOTALL)

with open(receiver_path, "w", encoding="utf-8") as f:
    f.write(receiver_code)


# 2. WiFiMonitoringService.kt
with open(wifi_service_path, "r", encoding="utf-8") as f:
    wifi_service = f.read()

if '"WIFI_RESTORED" ->' not in wifi_service:
    restore_handler = '''        if (intent?.action == "WIFI_RESTORED") {
            Log.d(TAG, "WiFi restored — breach state reset, no alert fired")
            sixSignalMonitor.resetWifiLostState()
            return START_STICKY
        }
        
        if (intent?.action == "WIFI_OFF_BREACH")'''
    wifi_service = wifi_service.replace('        if (intent?.action == "WIFI_OFF_BREACH")', restore_handler)

with open(wifi_service_path, "w", encoding="utf-8") as f:
    f.write(wifi_service)


# 3. SixSignalMonitor.kt
with open(monitor_path, "r", encoding="utf-8") as f:
    monitor_code = f.read()

# Add JSON and HTTP imports if missing
if "import org.json.JSONObject" not in monitor_code:
    monitor_code = monitor_code.replace("import android.util.Log\n", "import android.util.Log\nimport org.json.JSONObject\nimport java.io.OutputStreamWriter\nimport java.net.HttpURLConnection\nimport java.net.URL\n")

if 'fun resetWifiLostState()' not in monitor_code:
    monitor_code = monitor_code.replace('class SixSignalMonitor(private val context: Context) {', '''class SixSignalMonitor(private val context: Context) {
    
    fun resetWifiLostState() {
        wifiLostTimestamp = 0L
        isBreachActive = false
        lastBreachSentTime = 0L
        Log.d(TAG, "WiFi lost state reset — breach detection reset for next disconnect")
    }''')
else:
    # Ensure it's correct
    monitor_code = re.sub(r'fun resetWifiLostState\(\) \{.*?\}', '''fun resetWifiLostState() {
        wifiLostTimestamp = 0L
        isBreachActive = false
        lastBreachSentTime = 0L
        Log.d(TAG, "WiFi lost state reset — breach detection reset for next disconnect")
    }''', monitor_code, flags=re.DOTALL)


# Replace fireBreach completely
new_firebreach = '''    fun fireBreach(rssi: Int = -127) {
        val actualRssi = rssi // trust caller
        Log.d(TAG, "fireBreach() called with rssi=$rssi")
        
        val deviceIdVal = deviceId ?: run {
            Log.e(TAG, "fireBreach: deviceId is null — aborting")
            return
        }
        val roomIdVal = roomId ?: run {
            Log.e(TAG, "fireBreach: roomId is null — aborting")
            return
        }
        
        val freshToken = context.getSharedPreferences("kiosk_prefs", Context.MODE_PRIVATE)
            .getString("authToken", null)

        if (freshToken == null) {
            Log.e(TAG, "fireBreach: authToken is null — check SharedPreferences key name")
            // Try alternate key names
            val altToken = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
                .getString("device_token", null)
                ?: context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
                .getString("authToken", null)
                
            if (altToken == null) {
                Log.e(TAG, "fireBreach: no token in any pref — cannot POST breach")
                return
            }
            Log.d(TAG, "fireBreach: found token in alternate prefs")
            executeBreachPost(deviceIdVal, roomIdVal, altToken, rssi)
        } else {
            executeBreachPost(deviceIdVal, roomIdVal, freshToken, rssi)
        }
    }

    private fun executeBreachPost(deviceId: String, roomId: String, token: String, rssi: Int) {
        Thread {
            import android.os.PowerManager
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val wakeLock = pm.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK,
                "HotelSecurity::BreachPost"
            )
            wakeLock.acquire(60_000L)
            
            try {
                var posted = false
                repeat(5) { attempt ->
                    if (posted) return@repeat
                    Log.d(TAG, "Breach POST attempt ${attempt + 1}/5")
                    try {
                        val backendUrl = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
                            .getString("backend_base_url", "https://hotel-tablet-security.onrender.com")
                        val conn = (URL("$backendUrl/api/alert/breach")
                            .openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            setRequestProperty("Content-Type", "application/json")
                            setRequestProperty("Authorization", "Bearer $token")
                            connectTimeout = 12_000   // ← increase from 8s to 12s for Render cold start
                            readTimeout = 12_000
                            doOutput = true
                        }
                        val body = JSONObject().apply {
                            put("deviceId", deviceId)
                            put("roomId", roomId)
                            put("rssi", rssi)
                        }
                        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
                        val code = conn.responseCode
                        Log.d(TAG, "Breach POST attempt ${attempt + 1} → HTTP $code")
                        when {
                            code in 200..299 -> { posted = true; Log.d(TAG, "✅ Breach POST succeeded") }
                            code == 401 -> { Log.e(TAG, "❌ 401 — token rejected, stopping retries"); return@Thread }
                            else -> Log.w(TAG, "⚠️ HTTP $code — will retry")
                        }
                        conn.disconnect()
                    } catch (e: Exception) {
                        Log.e(TAG, "Breach POST attempt ${attempt + 1} exception: ${e.javaClass.simpleName}: ${e.message}")
                    }
                    if (!posted) Thread.sleep(3_000L)
                }
                if (!posted) Log.e(TAG, "❌ All 5 breach POST attempts failed")
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }.apply { isDaemon = true; start() }
    }'''

monitor_code = re.sub(r'    fun fireBreach\(.*?\}\n        \}\n    \}', new_firebreach, monitor_code, flags=re.DOTALL)
# Remove the invalid import android.os.PowerManager from the string I just wrote in python... Wait! 
monitor_code = monitor_code.replace("import android.os.PowerManager", "")

with open(monitor_path, "w", encoding="utf-8") as f:
    f.write(monitor_code)

print("Fixes applied successfully.")
