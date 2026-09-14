package com.example.hotel.admin

import android.content.ComponentName       // ← NEW: used to target MainActivityAlias
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.ActivityManager
import android.location.LocationManager    // ← FIX (CAUSE 1): check location services
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler                  // â†  NEW: 3-second delay before icon hide
import android.os.Looper                   // â†  NEW: main-thread looper for Handler
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat // â† FIX (CAUSE 1): runtime permission check
import com.example.hotel.data.AgentRepository
import com.example.hotel.data.RegisterRequest
import android.Manifest                    // â† FIX (CAUSE 1): ACCESS_FINE_LOCATION
import android.net.wifi.WifiManager // â† NEW: for authorized network
import com.example.hotel.security.WiFiMonitoringService  // â† NEW: start WiFi monitoring
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
        private const val PERMISSIONS_REQUEST = 100
        
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
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
        
        if (!hasAllPermissions()) {
            requestAllPermissions()
        } else {
            setupRegistrationForm()
        }
    }

    private fun setupRegistrationForm() {
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
     *   There is no runtime permission for this â€” the user must explicitly grant it
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

    private fun hasAllPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestAllPermissions() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(
                "Hotel Security requires:\n\n" +
                "• Location: To read WiFi network " +
                "information for security monitoring\n\n" +
                "• WiFi: To monitor connection status\n\n" +
                "Please grant all permissions to continue."
            )
            .setPositiveButton("Grant") { _, _ ->
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    REQUIRED_PERMISSIONS,
                    PERMISSIONS_REQUEST
                )
            }
            .setCancelable(false)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSIONS_REQUEST) {
            val allGranted = grantResults.all {
                it == PackageManager.PERMISSION_GRANTED
            }
            
            if (allGranted) {
                Log.i("Provision", "✅ All permissions granted")
                setupRegistrationForm()
            } else {
                val denied = permissions.filterIndexed { index, _ ->
                    grantResults[index] != PackageManager.PERMISSION_GRANTED
                }
                Log.e("Provision", "❌ Denied: $denied")
                
                val permanentlyDenied = denied.any {
                    !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(this, it)
                }
                
                if (permanentlyDenied) {
                    showGoToSettingsDialog()
                } else {
                    requestAllPermissions()
                }
            }
        }
    }

    private fun showGoToSettingsDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Permissions Denied")
            .setMessage(
                "Location permission was permanently " +
                "denied. Please go to Settings → " +
                "Apps → Hotel Security → Permissions " +
                "and enable Location permission."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
            .setNegativeButton("Exit") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }


    private fun requestBatteryOptimizationExemption() {
        // Doze Mode was introduced in API 23 â€” no-op on older devices.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager

        // If already exempt (e.g., MDM policy or previous grant) skip silently.
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Log.d(TAG, "âœ… Battery optimisation exemption already granted â€” no dialog needed")
            return
        }

        Log.d(TAG, "ðŸ”‹ Requesting battery optimisation exemption for $packageName")

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
                
                // ← FIX: Block registration if Doze battery optimization exemption is not granted
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                val isIgnoringBatteryOpt = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || pm.isIgnoringBatteryOptimizations(packageName)
                android.util.Log.d("Provisioning", "DEBUG: isIgnoringBatteryOptimizations = $isIgnoringBatteryOpt")
                
                if (!isIgnoringBatteryOpt) {
                    android.util.Log.e("Provisioning", "🚨 Provisioning Blocked: Battery optimization exemption not granted.")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@ProvisioningActivity,
                            "Allow unrestricted battery usage to complete registration.",
                            Toast.LENGTH_LONG
                        ).show()
                        statusText.text = "Registration blocked: Battery exemption required."
                        registerButton.isEnabled = true
                        requestBatteryOptimizationExemption()
                    }
                    return@launch
                }
                
                val startTime = System.currentTimeMillis()
                // â† FIXED: Verified hotelId is set to the staff-entered hotelUsername, not hardcoded "default"
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
                
                // â† FIX (CAUSE 1): Verify ACCESS_FINE_LOCATION + location services are ON
                //   BEFORE calling saveAuthorizedNetwork(). Without location permission,
                //   wifiManager.connectionInfo returns "<unknown ssid>" / "02:00:00:00:00:00"
                //   which would silently save junk as the "authorized" network, making
                //   wrong-network detection blind on all subsequent network switches.
                val locationGranted = ContextCompat.checkSelfPermission(
                    this@ProvisioningActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    (getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled
                } else {
                    val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    @Suppress("DEPRECATION")
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                }

                if (!locationGranted || !locationEnabled) {
                    val reason = when {
                        !locationGranted && !locationEnabled -> "Location permission AND location services are required."
                        !locationGranted -> "Location permission (ACCESS_FINE_LOCATION) is required."
                        else -> "Location services must be ON."
                    }
                    Log.e(TAG, "🚨 Provisioning Blocked: $reason")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@ProvisioningActivity,
                            "Enable Location to complete registration — this is required to verify the hotel WiFi network.",
                            Toast.LENGTH_LONG
                        ).show()
                        statusText.text = "Registration blocked: $reason"
                        registerButton.isEnabled = true
                    }
                    // <- FIX: Return early so registration cannot be completed until Location is on
                    return@launch
                } else {
                    // Location permission + services are confirmed good â€” safe to save
                    saveAuthorizedNetwork()
                    getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE).edit()
                        .putBoolean("authorized_network_unverified", false)
                        .apply()
                    Log.i(TAG, "âœ… Authorized network saved with verified location data")
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ProvisioningActivity,
                        "âœ… Device registered!\n" +
                        "Device: $deviceId\nRoom: $roomId\n\n" +
                        "Security monitoring starting...",
                        Toast.LENGTH_LONG
                    ).show()

                    statusText.text = "âœ… Registration complete! Starting security monitoring..."
                    statusText.text = "✅ Registration complete! Starting security monitoring..."

                    // ← Navigate to MainActivity after 2 seconds so the toast is readable.
                    //   MainActivity handles:
                    //   1. startForegroundService(KioskService) — after permission check
                    //   2. hideAppIcon() safety net — hides launcher icon silently
                    
                    // ← FIX 3: Start critical monitoring services first before cosmetic steps
                    startMonitoringServices()
                    
                    try {
                        // ← FIX 2: Wrap cosmetic icon hiding in try/catch
                        hideAppShortcut()
                    } catch (e: Exception) {
                        Log.w("Provisioning", "Non-critical: launcher icon hide failed", e)
                    }
                    
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
                        "â€¢ Render server is cold starting (can take 50+ seconds)\n" +
                        "â€¢ Internet connection too slow\n" +
                        "â€¢ Backend URL incorrect\n\nTry again in a moment."
                    }
                    e is java.net.UnknownHostException -> {
                        "Cannot reach server\n\nPossible causes:\n" +
                        "â€¢ No internet connection\n" +
                        "â€¢ Wrong backend URL\n" +
                        "â€¢ DNS issue\n\nCheck internet and URL:\n$backendUrl"
                    }
                    e is java.net.ConnectException -> {
                        "Connection refused\n\nPossible causes:\n" +
                        "â€¢ Backend is offline\n" +
                        "â€¢ Wrong port number\n" +
                        "â€¢ Firewall blocking\n\nBackend: $backendUrl"
                    }
                    e is javax.net.ssl.SSLException || e.message?.contains("SSL", ignoreCase = true) == true -> {
                        "SSL/HTTPS error\n\nPossible causes:\n" +
                        "â€¢ Certificate issue\n" +
                        "â€¢ Incorrect HTTPS URL\n" +
                        "â€¢ System date/time wrong\n\nCheck device date/time"
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
    // Stealth / icon-hiding helpers  (Samsung One UI aware)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Master icon-hide entry point called after successful registration.
     *
     * Step 1 – Standard disable: works on Pixel, MIUI, Motorola, OnePlus.
     * Step 2 – Samsung-specific fix: One UI caches launcher shortcuts
     *           independently of component state; we flush that cache with
     *           6 complementary methods (see applySamsungIconHideFix).
     * Step 3 – Verification after 3 s to confirm state and retry if needed.
     *
     * Also enters lock-task mode if this app is the Device Owner.
     */
    private fun hideAppShortcut() {
        try {
            val componentName = ComponentName(
                this,
                "${packageName}.MainActivityAlias"
            )

            // ← Step 1: Standard disable — works on all non-Samsung devices
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.i("HideIcon", "✅ Component disabled")

            // ← Step 2: Samsung-specific fixes
            if (isSamsung()) {
                Log.i("HideIcon", "Samsung detected — applying Samsung fix")
                applySamsungIconHideFix(componentName)
            }

            // ← Step 3: Verify after 3 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                verifyIconHidden(componentName)
            }, 3000L)

        } catch (e: Exception) {
            Log.e("HideIcon", "Hide failed: ${e.message}")
        }

        // Lock-task / kiosk mode (Device Owner only)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE)
            as android.app.admin.DevicePolicyManager
        val adminComponent = ComponentName(this, HotelDeviceAdminReceiver::class.java)
        if (dpm.isDeviceOwnerApp(packageName)) {
            dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))
            startLockTask()
        }

        Log.d("Kiosk", "Icon hidden. KioskService running: "
            + "${isServiceRunning(com.example.hotel.service.KioskService::class.java)}")
    }

    /** Returns true when running on a Samsung device (any One UI version). */
    private fun isSamsung(): Boolean =
        Build.MANUFACTURER.lowercase().contains("samsung")

    /**
     * Six complementary methods to force Samsung One UI Home to drop its
     * cached launcher shortcut for this package.
     *
     * Samsung's launcher caches shortcuts separately from Android's
     * PackageManager component state, so the standard
     * setComponentEnabledSetting() call alone is insufficient on One UI.
     */
    private fun applySamsungIconHideFix(componentName: ComponentName) {

        // ── Method 1 ──────────────────────────────────────────────────────────
        // Disable with flags=0 (KILL_APP) to force Samsung launcher to
        // invalidate its cache. We immediately follow up with DONT_KILL_APP
        // retries (M4 & M5) to ensure services survive.
        try {
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                0  // 0 = KILL_APP → forces launcher refresh
            )
            Log.d("HideIcon", "Samsung M1: disabled with 0 flags")
        } catch (e: Exception) {
            Log.w("HideIcon", "Samsung M1: $e")
        }

        // ── Method 2 ──────────────────────────────────────────────────────────
        // Samsung-proprietary broadcast: asks One UI Home to refresh shortcuts.
        try {
            val launcherIntent = Intent(
                "com.sec.android.app.launcher.REFRESH_SHORTCUT"
            ).apply {
                setPackage("com.sec.android.app.launcher")
            }
            sendBroadcast(launcherIntent)
            Log.d("HideIcon", "Samsung M2: refresh broadcast sent")
        } catch (e: Exception) {
            Log.w("HideIcon", "Samsung M2: $e")
        }

        // ── Method 3 ──────────────────────────────────────────────────────────
        // Synthesise PACKAGE_CHANGED so the launcher re-queries component state.
        // NOTE: ACTION_PACKAGE_CHANGED is a protected broadcast on API 26+;
        // the send may be silently dropped on hardened ROMs, which is fine —
        // the other methods will still take effect.
        try {
            val intent = Intent(
                "android.intent.action.PACKAGE_CHANGED"
            ).apply {
                data = Uri.parse("package:$packageName")
                putExtra("android.intent.extra.PACKAGE_NAME", packageName)
                putExtra(
                    "android.intent.extra.CHANGED_COMPONENT_NAME_LIST",
                    arrayOf("${packageName}.MainActivityAlias")
                )
                putExtra("android.intent.extra.DONT_KILL_APP", true)
            }
            sendBroadcast(intent)
            Log.d("HideIcon", "Samsung M3: package changed broadcast")
        } catch (e: Exception) {
            Log.w("HideIcon", "Samsung M3: $e")
        }

        // ── Method 4 ──────────────────────────────────────────────────────────
        // First retry disable after 500 ms — Samsung launcher needs a moment
        // to process the above broadcasts before re-reading component state.
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                packageManager.setComponentEnabledSetting(
                    ComponentName(this, "${packageName}.MainActivityAlias"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                Log.d("HideIcon", "Samsung M4: retry disable")
            } catch (e: Exception) {
                Log.w("HideIcon", "Samsung M4: $e")
            }
        }, 500L)

        // ── Method 5 ──────────────────────────────────────────────────────────
        // Second retry after 2 s + disable dynamic shortcuts so One UI Home
        // removes any pinned shortcut badges for this app.
        Handler(Looper.getMainLooper()).postDelayed({
            // 2nd disable retry
            try {
                packageManager.setComponentEnabledSetting(
                    ComponentName(this, "${packageName}.MainActivityAlias"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                Log.d("HideIcon", "Samsung M5: 2nd retry")
            } catch (e: Exception) {
                Log.w("HideIcon", "Samsung M5: $e")
            }

            // Disable dynamic shortcuts → Samsung launcher removes icon badges
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    val shortcutManager = getSystemService(
                        android.content.pm.ShortcutManager::class.java
                    )
                    shortcutManager?.disableShortcuts(
                        listOf("main_shortcut"),
                        "App is running in background mode"
                    )
                }
            } catch (e: Exception) {
                Log.w("HideIcon", "Samsung shortcut: $e")
            }
        }, 2000L)

        // ── Method 6 ──────────────────────────────────────────────────────────
        // After 3 s, notify all known Samsung launcher packages with a generic
        // ACTION_PACKAGE_CHANGED so they re-read component state one final time.
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val launcherPackages = listOf(
                    "com.sec.android.app.launcher",
                    "com.samsung.android.app.spage"
                )
                for (pkg in launcherPackages) {
                    try {
                        val notifyIntent = Intent(
                            Intent.ACTION_PACKAGE_CHANGED
                        ).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        sendBroadcast(notifyIntent)
                    } catch (ignored: Exception) { /* best-effort */ }
                }
                Log.d("HideIcon", "Samsung M6: launcher notified")
            } catch (e: Exception) {
                Log.w("HideIcon", "Samsung M6: $e")
            }
        }, 3000L)
    }

    /**
     * Called 3 seconds after the initial disable to confirm the component
     * state is DISABLED.  If not, performs one final retry.
     */
    private fun verifyIconHidden(componentName: ComponentName) {
        val state = packageManager.getComponentEnabledSetting(componentName)
        Log.i("HideIcon",
            "Component state: $state (2=DISABLED ✅, 1=ENABLED ❌)")

        if (state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            Log.e("HideIcon",
                "❌ Icon still showing! State=$state — retrying...")
            try {
                packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                Log.e("HideIcon", "Final retry: $e")
            }
        } else {
            Log.i("HideIcon", "✅ Icon successfully hidden!")
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == serviceClass.name }
    }

    /**
     * Starts both background services that must run forever after registration.
     *
     * â€¢ WiFiMonitoringService â€” sends heartbeats every 10 s, detects breach
     * â€¢ KioskService          â€” enforces kiosk lock, monitors battery/screen
     *
     * startForegroundService() is mandatory on API 26+ (our minSdk is 26).
     * Each service calls startForeground() in its own onCreate() within 5 s.
     */
    private fun startMonitoringServices() {
        // â†  NEW: Start WiFi heartbeat + breach detection service
        val wifiIntent = Intent(this, WiFiMonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(wifiIntent)
        } else {
            startService(wifiIntent)
        }
        Log.i(TAG, "âœ… WiFiMonitoringService started")

        // â†  NEW: Start kiosk lock + screen management service
        val kioskIntent = Intent(this, com.example.hotel.service.KioskService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(kioskIntent)
        } else {
            startService(kioskIntent)
        }
        Log.i(TAG, "âœ… KioskService started")
    }

    // <- NEW: Save authorized WiFi network on registration
    private fun saveAuthorizedNetwork() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) return

        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo ?: return

        @Suppress("DEPRECATION")
        val ssid = info.ssid?.replace("\"", "")?.trim() ?: ""

        @Suppress("DEPRECATION")
        val bssid = info.bssid ?: ""

        if (ssid.isEmpty() || ssid == "<unknown ssid>") {
            Log.w(TAG, "Cannot save authorized network - SSID empty at registration")
            return
        }

        val finalBssid = if (bssid == "02:00:00:00:00:00") "" else bssid

        getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE).edit().apply {
            putString("authorized_ssid", ssid)
            putString("authorized_bssid", finalBssid)
            apply()
        }

        Log.i(TAG, "Authorized network saved at registration: SSID='$ssid' BSSID='$finalBssid'")
    }
}

