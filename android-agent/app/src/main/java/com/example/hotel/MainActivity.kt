package com.example.hotel

import android.content.Intent
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

        // STEALTH MODE: Hide app icon from launcher after provisioning
        /* 
        val componentName = android.content.ComponentName(this, MainActivity::class.java)
        packageManager.setComponentEnabledSetting(
            componentName,
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            android.content.pm.PackageManager.DONT_KILL_APP
        )
        */
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
}
