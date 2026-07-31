package com.example.hotel.admin

import android.content.ComponentName       // ← NEW: used to target MainActivityAlias
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager   // ← NEW: COMPONENT_ENABLED_STATE_DISABLED
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler                  // ← NEW: 3-second delay before icon hide
import android.os.Looper                   // ← NEW: main-thread looper for Handler
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.hotel.data.AgentRepository
import com.example.hotel.data.RegisterRequest
import android.net.wifi.WifiManager // ← NEW: for authorized network
import com.example.hotel.security.WiFiMonitoringService  // ← NEW: start WiFi monitoring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Device Provisioning Activity
 * First-time setup wizard for new devices
 * - Generate/assign device ID
 * - Assign to room
 * - Set admin PIN
 * - Fetch initial configuration
 */
class ProvisioningActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ProvisioningActivity"
    }

    private lateinit var deviceIdInput: EditText
    private lateinit var roomIdInput: EditText
    private lateinit var hotelUsernameInput: EditText
    private lateinit var staffNameInput: EditText
    private lateinit var backendUrlInput: EditText
    private lateinit var generateIdButton: Button
    private lateinit var registerButton: Button
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request SYSTEM_ALERT_WINDOW permission for showing breach screen over other apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                Toast.makeText(this, "Please enable 'Display over other apps' permission", Toast.LENGTH_LONG).show()
            }
        }

        // ← NEW: Ask the user to exempt this app from Doze battery optimisations.
        // Hotel tablets run 24/7 — Doze mode would throttle heartbeats after ~2 minutes
        // of screen-off time, causing false BREACH/OFFLINE alerts on the dashboard.
        requestBatteryOptimizationExemption()
        
        // Check if already provisioned
        val prefs = getSharedPreferences("agent", MODE_PRIVATE)
        if (prefs.contains("device_id") && prefs.contains("room_id")) {
            // Already provisioned, skip to main
            finish()
            return
        }
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        val title = TextView(this).apply {
            text = "Device Provisioning"
            textSize = 24f
            setPadding(0, 0, 0, 24)
        }
        layout.addView(title)
        
        val instructions = TextView(this).apply {
            text = "Setup this tablet for hotel use"
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(instructions)
        
        // Device ID
        val deviceIdLabel = TextView(this).apply {
            text = "Device ID:"
            setPadding(0, 16, 0, 8)
        }
        layout.addView(deviceIdLabel)
        
        deviceIdInput = EditText(this).apply {
            hint = "e.g., TAB-101, TAB-102..."
            setPadding(16, 16, 16, 16)
        }
        layout.addView(deviceIdInput)
        
        generateIdButton = Button(this).apply {
            text = "Generate Random ID"
            setOnClickListener { generateDeviceId() }
        }
        layout.addView(generateIdButton)
        
        // Backend URL
        val backendUrlLabel = TextView(this).apply {
            text = "Backend URL:"
            setPadding(0, 16, 0, 8)
        }
        layout.addView(backendUrlLabel)
        
        backendUrlInput = EditText(this).apply {
            hint = "https://hotel-tablet-security.onrender.com/"
            setText("https://hotel-tablet-security.onrender.com/")  // Default value
            setPadding(16, 16, 16, 16)
        }
        layout.addView(backendUrlInput)
        
        // Room ID
        val roomIdLabel = TextView(this).apply {
            text = "Assign to Room:"
            setPadding(0, 16, 0, 8)
        }
        layout.addView(roomIdLabel)
        
        roomIdInput = EditText(this).apply {
            hint = "e.g., 101, 102, 103..."
            setPadding(16, 16, 16, 16)
        }
        layout.addView(roomIdInput)
        
        // Hotel Username
        val hotelUsernameLabel = TextView(this).apply {
            text = "Hotel Username:"
            setPadding(0, 16, 0, 8)
        }
        layout.addView(hotelUsernameLabel)
        
        hotelUsernameInput = EditText(this).apply {
            hint = "e.g. hilton_admin"
            setPadding(16, 16, 16, 16)
        }
        layout.addView(hotelUsernameInput)

        // Staff Name
        val staffNameLabel = TextView(this).apply {
            text = "Staff Name:"
            setPadding(0, 16, 0, 8)
        }
        layout.addView(staffNameLabel)
        
        staffNameInput = EditText(this).apply {
            hint = "e.g. John Doe"
            setPadding(16, 16, 16, 16)
        }
        layout.addView(staffNameInput)
        
        // Status
        statusText = TextView(this).apply {
            text = "Enter device details and register"
            textSize = 12f
            setPadding(0, 16, 0, 16)
        }
        layout.addView(statusText)
        
        // Register button
        registerButton = Button(this).apply {
            text = "Register Device"
            setOnClickListener { registerDevice() }
        }
        layout.addView(registerButton)
        
        setContentView(layout)
    }
    
    private fun generateDeviceId() {
        val randomId = "TAB-${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
        deviceIdInput.setText(randomId)
        Toast.makeText(this, "Generated ID: $randomId", Toast.LENGTH_SHORT).show()
    }

    /**
     * Requests that Android exempt this app from Doze-mode battery optimisations.
     *
     * Why this is necessary:
     *   Doze Mode (API 23+) aggressively throttles background work once the screen
     *   has been off for ~60 seconds. On dedicated hotel tablets the screen is always
     *   off; without this exemption the heartbeat alarms are deferred and the
     *   dashboard emits false BREACH/OFFLINE alerts within minutes of screen-off.
     *
     * Why ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS and not a runtime permission?
     *   There is no runtime permission for this — the user must explicitly grant it
     *   via the system settings dialog. We trigger that dialog here during one-time
     *   provisioning so an admin can approve it before handing the tablet to a room.
     *
     * Why check isIgnoringBatteryOptimizations() first?
     *   On devices where the admin has already granted the exemption (or it was
     *   granted by an MDM profile) we must NOT show the dialog again. Repeated
     *   dialogs create a poor UX and are unnecessary.
     *
     * Security note:
     *   REQUEST_IGNORE_BATTERY_OPTIMIZATIONS must be declared in AndroidManifest.xml.
     *   Google Play restricts this to specific use cases; for a sideloaded enterprise
     *   hotel app this is acceptable.
     *
     * Call site: called ONCE during first-time provisioning setup only.
     */
    private fun requestBatteryOptimizationExemption() {
        // Doze Mode was introduced in API 23 — no-op on older devices.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager

        // If already exempt (e.g., MDM policy or previous grant) skip silently.
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Log.d(TAG, "✅ Battery optimisation exemption already granted — no dialog needed")
            return
        }

        Log.d(TAG, "🔋 Requesting battery optimisation exemption for $packageName")

        // The URI must reference this package name exactly.
        // Without the URI, the Intent is rejected on most OEM ROMs (Samsung, Xiaomi, etc.).
        startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
        )

        Toast.makeText(
            this,
            "Battery optimisation exemption required.\nTap 'Allow' to prevent false security alerts.",
            Toast.LENGTH_LONG
        ).show()
    }
    
    private fun registerDevice() {
        val deviceId = deviceIdInput.text.toString().trim()
        val roomId = roomIdInput.text.toString().trim()
        val hotelUsername = hotelUsernameInput.text.toString().trim()
        val staffName = staffNameInput.text.toString().trim()
        val backendUrl = backendUrlInput.text.toString().trim()
        
        if (deviceId.isEmpty() || roomId.isEmpty() || hotelUsername.isEmpty() || staffName.isEmpty() || backendUrl.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Validate backend URL
        if (!backendUrl.startsWith("http://") && !backendUrl.startsWith("https://")) {
            Toast.makeText(this, "Backend URL must start with http:// or https://", Toast.LENGTH_SHORT).show()
            return
        }
        
        statusText.text = "Registering device..."
        registerButton.isEnabled = false
        
        GlobalScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("Provisioning", "Starting registration...")
                android.util.Log.d("Provisioning", "Backend URL: $backendUrl")
                android.util.Log.d("Provisioning", "Device ID: $deviceId")
                android.util.Log.d("Provisioning", "Room ID: $roomId")
                
                // Show progress on UI
                withContext(Dispatchers.Main) {
                    statusText.text = "Connecting to backend...\n(This may take up to 60 seconds if server is sleeping)"
                }
                
                // Save backend URL and prepare SharedPreferences
                val prefs = getSharedPreferences("agent", MODE_PRIVATE)
                prefs.edit().putString("backend_url", backendUrl).apply()
                
                android.util.Log.d("Provisioning", "Creating repository...")
                val repo = AgentRepository.default(applicationContext).alerts
                val tempAuth = "Bearer changeme" // Temporary auth for initial registration
                
                // Update progress
                withContext(Dispatchers.Main) {
                    statusText.text = "Registering device with backend..."
                }
                
                // Register device with backend
                android.util.Log.d("Provisioning", "Calling register API...")
                val startTime = System.currentTimeMillis()
                // ← FIXED: Verified hotelId is set to the staff-entered hotelUsername, not hardcoded "default"
                val registerResponse = repo.register(tempAuth, RegisterRequest(deviceId, roomId, hotelUsername, staffName))
                val duration = (System.currentTimeMillis() - startTime) / 1000.0
                android.util.Log.d("Provisioning", "Register response received in ${duration}s: $registerResponse")
                
                // Extract JWT token from response
                val jwtToken = registerResponse["token"] as? String
                if (jwtToken == null) {
                    throw Exception("No token received from server")
                }
                
                android.util.Log.d("Provisioning", "Token received: ${jwtToken.take(20)}...")
                val authHeader = "Bearer $jwtToken"
                
                // Use ANY WiFi configuration - no specific BSSID/SSID required
                val bssid = "ANY_WIFI"
                val ssid = "ANY_WIFI"
                val minRssi = -70  // Default threshold: -70 dBm for any WiFi
                
                // Save all settings to SharedPreferences including the JWT token
                prefs.edit()
                    .putString("device_id", deviceId)
                    .putString("room_id", roomId)
                    .putString("hotel_username", hotelUsername)
                    .putString("staff_name", staffName)
                    .putString("jwt_token", jwtToken)  // Save JWT token
                    .putString("bssid", bssid)
                    .putString("ssid", ssid)
                    .putInt("minRssi", minRssi)
                    .putBoolean("provisioned", true)
                    .apply()
                
                android.util.Log.d("Provisioning", "Settings saved, registration complete!")
                
                // ← NEW: Save authorized WiFi network
                saveAuthorizedNetwork()
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ProvisioningActivity,
                        "✅ Device registered!\n" +
                        "Device: $deviceId\nRoom: $roomId\n\n" +
                        "Security monitoring starting...",
                        Toast.LENGTH_LONG
                    ).show()

                    statusText.text = "✅ Registration complete! Starting security monitoring..."

                    // ← Navigate to MainActivity after 2 seconds so the toast is readable.
                    //   MainActivity handles:
                    //   1. Runtime permission requests (location, notifications)
                    //   2. startForegroundService(KioskService)  — after permission check
                    //   3. hideAppIcon() safety net — hides launcher icon silently
                    //
                    //   We do NOT call startMonitoringServices() here because the
                    //   location permission has NOT been granted yet at this point.
                    //   Calling startForeground() with connectedDevice type is safe
                    //   without location, so this path works correctly.
                    Handler(Looper.getMainLooper()).postDelayed({
                        val intent = Intent(
                            this@ProvisioningActivity,
                            com.example.hotel.MainActivity::class.java
                        ).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                            )
                        }
                        startActivity(intent)
                        // finish() is implicit due to FLAG_ACTIVITY_CLEAR_TASK
                    }, 2_000L)
                }
                
            } catch (e: Exception) {
                android.util.Log.e("Provisioning", "Registration failed", e)
                
                // Detailed error classification
                val errorMsg = when {
                    e is java.net.SocketTimeoutException -> {
                        "Timeout: Backend didn't respond in 60 seconds\n\nPossible causes:\n" +
                        "• Render server is cold starting (can take 50+ seconds)\n" +
                        "• Internet connection too slow\n" +
                        "• Backend URL incorrect\n\nTry again in a moment."
                    }
                    e is java.net.UnknownHostException -> {
                        "Cannot reach server\n\nPossible causes:\n" +
                        "• No internet connection\n" +
                        "• Wrong backend URL\n" +
                        "• DNS issue\n\nCheck internet and URL:\n$backendUrl"
                    }
                    e is java.net.ConnectException -> {
                        "Connection refused\n\nPossible causes:\n" +
                        "• Backend is offline\n" +
                        "• Wrong port number\n" +
                        "• Firewall blocking\n\nBackend: $backendUrl"
                    }
                    e is javax.net.ssl.SSLException || e.message?.contains("SSL", ignoreCase = true) == true -> {
                        "SSL/HTTPS error\n\nPossible causes:\n" +
                        "• Certificate issue\n" +
                        "• Incorrect HTTPS URL\n" +
                        "• System date/time wrong\n\nCheck device date/time"
                    }
                    e.message?.contains("401", ignoreCase = true) == true || 
                    e.message?.contains("403", ignoreCase = true) == true -> {
                        "Authentication failed\n\nBackend rejected request\nContact administrator"
                    }
                    else -> {
                        "Error: ${e.javaClass.simpleName}\n${e.message ?: "Unknown error"}\n\nCheck logcat for details"
                    }
                }
                
                withContext(Dispatchers.Main) {
                    statusText.text = "Registration failed: $errorMsg"
                    Toast.makeText(
                        this@ProvisioningActivity,
                        "Failed: $errorMsg\n\nCheck logcat for details",
                        Toast.LENGTH_LONG
                    ).show()
                    registerButton.isEnabled = true
                }
            }
        }
    }
    
    override fun onBackPressed() {
        // Prevent exiting provisioning without completing
        Toast.makeText(this, "Please complete device registration", Toast.LENGTH_SHORT).show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ← NEW: Stealth / icon-hiding helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Hides the app icon from the home screen launcher by DISABLING the
     * activity-alias (.MainActivityAlias).  The alias is the only component
     * that carries the LAUNCHER intent-filter; disabling it makes every
     * launcher drop the shortcut within ~5 seconds without any UI prompt.
     *
     * WHY alias and not MainActivity directly?
     *   If we disabled MainActivity the system would kill the whole task and
     *   all bound services.  The alias is just a pointer — disabling it only
     *   affects launcher discovery; services keep running unaffected.
     *
     * DONT_KILL_APP flag ensures no process restart happens.
     */
    private fun hideAppIcon() {
        try {
            packageManager.setComponentEnabledSetting(
                ComponentName(this, "${packageName}.MainActivityAlias"), // ← alias name
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP   // ← services keep running
            )
            Log.i(TAG, "✅ App icon hidden from launcher — running silently in background")
        } catch (e: Exception) {
            // Never crash registration just because icon-hide failed
            Log.e(TAG, "⚠️ Failed to hide app icon: ${e.message}")
        }
    }

    /**
     * Starts both background services that must run forever after registration.
     *
     * • WiFiMonitoringService — sends heartbeats every 10 s, detects breach
     * • KioskService          — enforces kiosk lock, monitors battery/screen
     *
     * startForegroundService() is mandatory on API 26+ (our minSdk is 26).
     * Each service calls startForeground() in its own onCreate() within 5 s.
     */
    private fun startMonitoringServices() {
        // ← NEW: Start WiFi heartbeat + breach detection service
        val wifiIntent = Intent(this, WiFiMonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(wifiIntent)
        } else {
            startService(wifiIntent)
        }
        Log.i(TAG, "✅ WiFiMonitoringService started")

        // ← NEW: Start kiosk lock + screen management service
        val kioskIntent = Intent(this, com.example.hotel.service.KioskService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(kioskIntent)
        } else {
            startService(kioskIntent)
        }
        Log.i(TAG, "✅ KioskService started")
    }

    // ← NEW: Save authorized WiFi network
    private fun saveAuthorizedNetwork() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        
        if (!wifiManager.isWifiEnabled) return
        
        val info = wifiManager.connectionInfo ?: return
        
        val bssid = info.bssid ?: ""
        val ssid = info.ssid?.replace("\"", "") ?: ""
        
        // ← Skip privacy MAC
        val finalBssid = if (bssid == "02:00:00:00:00:00") "" else bssid
        
        if (finalBssid.isEmpty() && ssid.isEmpty()) {
            Log.w(TAG, "Cannot save authorized network — no WiFi info available")
            return
        }
        
        getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE).edit().apply {
            putString("authorized_bssid", finalBssid)
            putString("authorized_ssid", ssid)
            apply()
        }
        
        Log.i(TAG, "✅ Authorized network saved: BSSID=$finalBssid SSID=$ssid")
    }
}
