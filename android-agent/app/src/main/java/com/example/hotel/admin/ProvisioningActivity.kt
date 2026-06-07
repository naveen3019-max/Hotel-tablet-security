package com.example.hotel.admin

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.hotel.data.AgentRepository
import com.example.hotel.data.RegisterRequest
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
    
    private lateinit var deviceIdInput: EditText
    private lateinit var roomIdInput: EditText
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
            hint = "https://hotel-backend-zqc1.onrender.com/"
            setText("https://hotel-backend-zqc1.onrender.com/")  // Default value
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
    
    private fun registerDevice() {
        val deviceId = deviceIdInput.text.toString().trim()
        val roomId = roomIdInput.text.toString().trim()
        val backendUrl = backendUrlInput.text.toString().trim()
        
        if (deviceId.isEmpty() || roomId.isEmpty() || backendUrl.isEmpty()) {
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
                val registerResponse = repo.register(tempAuth, RegisterRequest(deviceId, roomId))
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
                    .putString("jwt_token", jwtToken)  // Save JWT token
                    .putString("bssid", bssid)
                    .putString("ssid", ssid)
                    .putInt("minRssi", minRssi)
                    .putBoolean("provisioned", true)
                    .apply()
                
                android.util.Log.d("Provisioning", "Settings saved, registration complete!")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ProvisioningActivity,
                        "✓ Device registered!\nBackend: $backendUrl\nWorks with ANY WiFi network\nThreshold: $minRssi dBm",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    statusText.text = "Registration complete!"
                    
                    // Restart to apply stealth mode
                    val intent = android.content.Intent(this@ProvisioningActivity, com.example.hotel.MainActivity::class.java)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                    finish()
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
}
