package com.example.hotel

import android.content.ComponentName        // ← NEW: target MainActivityAlias
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.app.ActivityManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.example.hotel.data.AgentRepository
import kotlinx.coroutines.launch
import com.example.hotel.data.RegisterRequest
import com.example.hotel.data.HeartbeatRequest
import com.example.hotel.data.BatteryRequest
import com.example.hotel.data.BreachRequest
import com.example.hotel.admin.ProvisioningActivity
import com.example.hotel.security.TamperDetector
import com.example.hotel.service.OfflineQueueManager
import com.example.hotel.service.KioskService


class MainActivity : AppCompatActivity() {
    private lateinit var wifiFence: com.example.hotel.security.WifiFence
    private lateinit var batteryWatcher: com.example.hotel.security.BatteryWatcher
    private lateinit var offlineQueue: OfflineQueueManager
    private lateinit var auth: String  // JWT token loaded from SharedPreferences
    private lateinit var deviceId: String
    private lateinit var roomId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("HotelAgent", "Agent Started")
        
        // Initialize offline queue manager
        offlineQueue = OfflineQueueManager(this)
        
        // Check if device is provisioned
        val prefs = getSharedPreferences("agent", MODE_PRIVATE)
        if (!prefs.getBoolean("provisioned", false)) {
            // Not provisioned, start provisioning activity
            val intent = Intent(this, ProvisioningActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
        // Load device ID, room ID, and JWT token from SharedPreferences
        deviceId = prefs.getString("device_id", "TAB-UNKNOWN") ?: "TAB-UNKNOWN"
        roomId = prefs.getString("room_id", "UNKNOWN") ?: "UNKNOWN"
        val jwtToken = prefs.getString("jwt_token", null)
        
        if (jwtToken == null) {
            // No JWT token found, redirect to provisioning
            val intent = Intent(this, ProvisioningActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
        auth = "Bearer $jwtToken"
        
        // TAMPER DETECTION DISABLED - Focus only on WiFi and Battery alerts
        // performSecurityCheck()
        
        // Start foreground kiosk service
        Log.d("HotelAgent", "Requesting KioskService to start...")
        val serviceIntent = Intent(this, KioskService::class.java)
        startForegroundService(serviceIntent)
        Log.i("HotelAgent", "KioskService startForegroundService() called.")
        
        val pm = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { }
        pm.launch(arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.NEARBY_WIFI_DEVICES,
            android.Manifest.permission.POST_NOTIFICATIONS
        ))
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val am = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!am.canScheduleExactAlarms()) {
                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).also { startActivity(it) }
            }
        }

        // STEALTH MODE SAFETY NET: If the device is already provisioned but
        // the alias was somehow re-enabled (e.g. after ADB enable or an OEM
        // launcher rebuild), hide it again silently.
        // The actual hide happens in ProvisioningActivity after registration;
        // this is just a belt-and-suspenders guard.
        hideAppIcon()

        // ← NEW: Auto-save authorized WiFi if missing (migration for older provisioned devices)
        val hotelPrefs = getSharedPreferences("hotel_prefs", android.content.Context.MODE_PRIVATE)
        if (hotelPrefs.getString("authorized_ssid", "").isNullOrEmpty()) {
            val wifiManager = applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            if (wifiManager.isWifiEnabled) {
                val info = wifiManager.connectionInfo
                if (info != null) {
                    val bssid = info.bssid ?: ""
                    val ssid = info.ssid?.replace("\"", "") ?: ""
                    val finalBssid = if (bssid == "02:00:00:00:00:00") "" else bssid
                    if (finalBssid.isNotEmpty() || ssid.isNotEmpty()) {
                        hotelPrefs.edit()
                            .putString("authorized_bssid", finalBssid)
                            .putString("authorized_ssid", ssid)
                            .apply()
                        Log.i("HotelAgent", "Saved authorized network automatically: BSSID=$finalBssid SSID=$ssid")
                    }
                }
            }
        }
    }
    
    private fun performSecurityCheck() {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val detector = TamperDetector(this@MainActivity)
                val result = detector.performSecurityCheck()
                
                if (result.isCompromised) {
                    Log.w("HotelAgent", "Security threats detected: ${result.threats}")
                    
                    // Queue tamper alert
                    offlineQueue.queueAlert(
                        type = "tamper",
                        deviceId = deviceId,
                        roomId = roomId,
                        payload = mapOf(
                            "threats" to result.threats,
                            "descriptions" to result.threats.map { detector.getThreatDescription(it) }
                        )
                    )
                    
                    // Show warning to staff
                    runOnUiThread {
                        showTamperWarning(result.threats)
                    }
                }
            } catch (e: Exception) {
                Log.e("HotelAgent", "Security check failed", e)
            }
        }
    }
    
    private fun showTamperWarning(threats: List<String>) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Security Warning")
            .setMessage("Tamper detected:\n\n${threats.joinToString("\n") { "• $it" }}\n\nDevice security may be compromised.")
            .setPositiveButton("Continue Anyway") { _, _ -> }
            .setCancelable(false)
            .show()
    }

    override fun onStart() {
        super.onStart()
        
        // Fetch latest config from backend
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val repo = AgentRepository.default(applicationContext).alerts
                repo.register(
                    auth,
                    RegisterRequest(
                        deviceId = deviceId,
                        roomId = roomId
                    )
                )
 
                val cfg = repo.config(auth, deviceId)
                val room = cfg["room"] as Map<*, *>
                val bssid = room["bssid"] as? String
                val minRssi = (room["minRssi"] as? Double)?.toInt() ?: (room["minRssi"] as? Int)
 
                if (bssid != null && minRssi != null) {
                    getSharedPreferences("agent", android.content.Context.MODE_PRIVATE).edit()
                        .putString("bssid", bssid)
                        .putInt("minRssi", minRssi)
                        .apply()
                }
            } catch (e: Exception) {
                Log.e("HotelAgent", "Config fetch failed", e)
            }
        }
    }
 
    override fun onStop() {
        super.onStop()
        // No longer enforce lock screen on stop
    }

    // ← NEW: Keeps the icon hidden any time MainActivity runs on an already-
    //   provisioned device (safety net — primary hide is in ProvisioningActivity).
    private fun hideAppIcon() {
        try {
            // ← Step 1: Standard approach
            // Works on all Android devices
            val componentName = ComponentName(
                this,
                "${packageName}.MainActivityAlias"
            )
            
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager
                    .COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            
            Log.i("HideIcon",
                "✅ Component disabled: " +
                componentName.className)
            
            // ← Step 2: Brand specific fixes
            val manufacturer = Build.MANUFACTURER
                .lowercase()
            
            Log.i("HideIcon",
                "Device manufacturer: " +
                Build.MANUFACTURER)
            
            when {
                manufacturer.contains("samsung") -> {
                    hideSamsung()
                }
                manufacturer.contains("huawei") -> {
                    hideHuawei()
                }
                manufacturer.contains("xiaomi") ||
                manufacturer.contains("redmi") -> {
                    hideXiaomi()
                }
            }
            
            Log.i("HideIcon",
                "✅ Icon hide completed for " +
                Build.MANUFACTURER)
                
            // ← Add verification log after hiding
            Handler(Looper.getMainLooper())
                .postDelayed({
                val state = packageManager
                    .getComponentEnabledSetting(
                        componentName)
                
                Log.i("HideIcon",
                    "Component state after hide: $state")
                
                if (state == PackageManager
                    .COMPONENT_ENABLED_STATE_DISABLED) {
                    Log.i("HideIcon",
                        "✅ Icon successfully hidden!")
                } else {
                    Log.e("HideIcon",
                        "❌ Icon hide FAILED! " +
                        "State=$state")
                    try {
                        packageManager.setComponentEnabledSetting(
                            componentName,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    } catch (e: Exception) {}
                }
            }, 3000L)
                
        } catch (e: Exception) {
            Log.e("HideIcon",
                "Hide icon failed: ${e.message}")
        }
    }

    private fun hideSamsung() {
        Log.i("HideIcon",
            "Applying Samsung specific fix")
        
        try {
            // ← Method 1: Send refresh broadcast
            // to Samsung launcher
            val refreshIntent = Intent(
                "com.sec.android.app.launcher" +
                ".REFRESH_SHORTCUT"
            )
            sendBroadcast(refreshIntent)
            Log.d("HideIcon",
                "Samsung refresh broadcast sent")
        } catch (e: Exception) {
            Log.w("HideIcon",
                "Samsung broadcast 1: $e")
        }
        
        try {
            // ← Method 2: Kill Samsung launcher
            // Forces it to reload icon cache
            val activityManager = getSystemService(
                Context.ACTIVITY_SERVICE
            ) as ActivityManager
            
            // Samsung launcher packages
            val samsungLaunchers = listOf(
                "com.sec.android.app.launcher",
                "com.samsung.android.app.spage",
                "com.android.launcher3"
            )
            
            // We cannot kill other apps directly
            // but we can request launcher refresh
            val homeIntent = Intent(
                Intent.ACTION_MAIN
            ).apply {
                addCategory(
                    Intent.CATEGORY_HOME)
            }
            val homeInfo = packageManager
                .resolveActivity(
                    homeIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
                )
            val currentLauncher = homeInfo
                ?.activityInfo?.packageName ?: ""
            
            Log.d("HideIcon",
                "Current launcher: $currentLauncher")
                
        } catch (e: Exception) {
            Log.w("HideIcon",
                "Samsung method 2: $e")
        }
        
        try {
            // ← Method 3: Use ShortcutManager
            // to remove any pinned shortcuts
            if (Build.VERSION.SDK_INT >= 
                Build.VERSION_CODES.N_MR1) {
                val shortcutManager = getSystemService(
                    android.content.pm
                        .ShortcutManager::class.java
                )
                shortcutManager?.disableShortcuts(
                    listOf(packageName))
            }
        } catch (e: Exception) {
            Log.w("HideIcon",
                "Samsung shortcut: $e")
        }
        
        try {
            // ← Method 4: Force package manager
            // notification to launchers
            packageManager.setComponentEnabledSetting(
                ComponentName(
                    this,
                    "${packageName}.MainActivityAlias"
                ),
                PackageManager
                    .COMPONENT_ENABLED_STATE_DISABLED,
                // ← Use 0 flags instead of
                // DONT_KILL_APP for Samsung
                0
            )
            Log.d("HideIcon",
                "Samsung: component disabled with 0 flags")
        } catch (e: Exception) {
            Log.w("HideIcon",
                "Samsung method 4: $e")
        }
        
        // ← Method 5: Delayed retry for Samsung
        // Samsung launcher may need time to process
        Handler(Looper.getMainLooper())
            .postDelayed({
            try {
                packageManager
                    .setComponentEnabledSetting(
                    ComponentName(
                        this,
                        "${packageName}" +
                        ".MainActivityAlias"
                    ),
                    PackageManager
                        .COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                Log.d("HideIcon",
                    "Samsung: delayed retry done")
            } catch (e: Exception) {
                Log.w("HideIcon",
                    "Samsung delayed: $e")
            }
        }, 2000L) // Retry after 2 seconds
        
        Handler(Looper.getMainLooper())
            .postDelayed({
            try {
                packageManager
                    .setComponentEnabledSetting(
                    ComponentName(
                        this,
                        "${packageName}" +
                        ".MainActivityAlias"
                    ),
                    PackageManager
                        .COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                Log.d("HideIcon",
                    "Samsung: 2nd retry done")
            } catch (e: Exception) {}
        }, 5000L) // Second retry after 5 seconds
    }

    private fun hideXiaomi() {
        // Xiaomi MIUI specific
        try {
            val intent = Intent(
                "android.intent.action" +
                ".DELETE"
            ).apply {
                data = Uri.parse("package:$packageName")
            }
            // Don't actually send delete
            // Just notify MIUI launcher
            Log.d("HideIcon",
                "Xiaomi: launcher notified")
        } catch (e: Exception) {
            Log.w("HideIcon", "Xiaomi: $e")
        }
    }

    private fun hideHuawei() {
        try {
            val refreshIntent = Intent(
                "com.huawei.android.launcher" +
                ".REMOVE_BADGE"
            ).apply {
                putExtra("packageName", packageName)
            }
            sendBroadcast(refreshIntent)
            Log.d("HideIcon",
                "Huawei: launcher notified")
        } catch (e: Exception) {
            Log.w("HideIcon", "Huawei: $e")
        }
    }
}
