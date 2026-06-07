package com.example.hotel.ui

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.text.InputType
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.example.hotel.admin.HotelDeviceAdminReceiver
import com.example.hotel.data.AgentRepository
import com.example.hotel.data.BreachRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * PIN Dialog for power off/restart authorization
 */
class PowerOffPinDialog : Activity() {

    private val ADMIN_PIN = "1234"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wrongPinAttempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        Log.e("PowerOffPinDialog", "🚨 POWER-OFF ATTEMPT DETECTED at $timestamp")
        
        // Log the attempt to backend
        logShutdownAttempt("initiated")
        
        // Make dialog appear on lock screen and as high priority
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        
        // Create UI
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(80, 80, 80, 80)
            setBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))
        }

        // Title
        val title = TextView(this).apply {
            text = "⚠️ Power Off Protection"
            textSize = 26f
            setTextColor(android.graphics.Color.parseColor("#C62828"))
            setPadding(0, 0, 0, 30)
            gravity = android.view.Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        layout.addView(title)

        // Message
        val message = TextView(this).apply {
            text = "Enter PIN to power off device"
            textSize = 18f
            setTextColor(android.graphics.Color.DKGRAY)
            setPadding(0, 0, 0, 30)
            gravity = android.view.Gravity.CENTER
        }
        layout.addView(message)

        // Warning
        val warning = TextView(this).apply {
            text = "⚠️ Unauthorized shutdown attempts will be logged"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#D32F2F"))
            setPadding(20, 0, 20, 30)
            gravity = android.view.Gravity.CENTER
        }
        layout.addView(warning)

        // PIN Input
        val pinInput = EditText(this).apply {
            hint = "Enter Admin PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            textSize = 22f
            setPadding(30, 30, 30, 30)
            setBackgroundColor(android.graphics.Color.WHITE)
        }
        layout.addView(pinInput, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 30)
        })

        // Button container
        val buttonLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        // Cancel Button
        val cancelButton = Button(this).apply {
            text = "Cancel"
            textSize = 18f
            setBackgroundColor(android.graphics.Color.GRAY)
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                Log.i("PowerOffPinDialog", "✅ Power-off cancelled by user at $timestamp")
                logShutdownAttempt("cancelled")
                Toast.makeText(this@PowerOffPinDialog, "Power off cancelled", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        buttonLayout.addView(cancelButton, android.widget.LinearLayout.LayoutParams(
            0,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            setMargins(0, 0, 15, 0)
        })

        // Confirm Button
        val confirmButton = Button(this).apply {
            text = "Power Off"
            textSize = 18f
            setBackgroundColor(android.graphics.Color.parseColor("#D32F2F"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener {
                val enteredPin = pinInput.text.toString()
                
                // Get configured PIN
                val prefs = getSharedPreferences("agent", Context.MODE_PRIVATE)
                val configuredPin = prefs.getString("admin_pin", ADMIN_PIN) ?: ADMIN_PIN
                
                if (enteredPin == configuredPin) {
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    Log.i("PowerOffPinDialog", "✅ CORRECT PIN - Authorizing shutdown at $timestamp")
                    
                    // Set authorization flag
                    prefs.edit().putBoolean("shutdown_authorized", true).apply()
                    
                    // Log authorized shutdown
                    logShutdownAttempt("authorized")
                    
                    Toast.makeText(this@PowerOffPinDialog, "✅ Authorized - Device will shutdown", Toast.LENGTH_SHORT).show()
                    
                    // Note: Device will continue with system shutdown
                    // We cannot programmatically shutdown without system permissions
                    finish()
                    
                } else {
                    wrongPinAttempts++
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    Log.e("PowerOffPinDialog", "❌ INCORRECT PIN! Attempt #$wrongPinAttempts at $timestamp")
                    
                    // Send breach alert to backend
                    sendBreachAlert(wrongPinAttempts)
                    
                    // Lock device after 2 wrong attempts
                    if (wrongPinAttempts >= 2) {
                        Log.e("PowerOffPinDialog", "🔒 LOCKING DEVICE - Multiple failed attempts!")
                        lockDevice()
                    }
                    
                    Toast.makeText(this@PowerOffPinDialog, "❌ Incorrect PIN - Attempt $wrongPinAttempts/2", Toast.LENGTH_LONG).show()
                    pinInput.text.clear()
                    pinInput.requestFocus()
                }
            }
        }
        buttonLayout.addView(confirmButton, android.widget.LinearLayout.LayoutParams(
            0,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            setMargins(15, 0, 0, 0)
        })

        layout.addView(buttonLayout)

        setContentView(layout)
        
        // Auto-focus on PIN input
        pinInput.requestFocus()
    }

    override fun onBackPressed() {
        // Prevent back button from closing dialog
        Log.i("PowerOffPinDialog", "Back button blocked")
        Toast.makeText(this, "Enter PIN or cancel", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Lock the device immediately using Device Admin
     */
    private fun lockDevice() {
        try {
            val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(this, HotelDeviceAdminReceiver::class.java)
            
            if (devicePolicyManager.isAdminActive(adminComponent)) {
                Log.e("PowerOffPinDialog", "🔒 Locking device NOW!")
                Toast.makeText(this, "🚨 DEVICE LOCKED - Unauthorized attempt!", Toast.LENGTH_LONG).show()
                
                // Lock the device
                devicePolicyManager.lockNow()
                
                // Close dialog
                finish()
            } else {
                Log.w("PowerOffPinDialog", "Device Admin not active - cannot lock")
                Toast.makeText(this, "⚠️ Security breach logged!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("PowerOffPinDialog", "Failed to lock device: ${e.message}", e)
        }
    }
    
    /**
     * Send breach alert to backend
     */
    private fun sendBreachAlert(attemptNumber: Int) {
        scope.launch {
            try {
                val prefs = getSharedPreferences("agent", Context.MODE_PRIVATE)
                val deviceId = prefs.getString("device_id", null)
                val roomId = prefs.getString("room_id", null)
                val token = prefs.getString("auth_token", null)
                
                if (deviceId != null && roomId != null && token != null) {
                    Log.e("PowerOffPinDialog", "📡 Sending breach alert: Power-off attempt #$attemptNumber")
                    
                    val repo = AgentRepository.default(applicationContext)
                    repo.alerts.breach(
                        "Bearer $token",
                        BreachRequest(
                            deviceId = deviceId,
                            roomId = roomId,
                            rssi = -999
                        )
                    )
                    
                    Log.i("PowerOffPinDialog", "✅ Breach alert sent successfully")
                } else {
                    Log.w("PowerOffPinDialog", "⚠️ Cannot send breach - device not provisioned")
                }
            } catch (e: Exception) {
                Log.e("PowerOffPinDialog", "❌ Failed to send breach alert: ${e.message}", e)
            }
        }
    }
    
    /**
     * Log shutdown attempt with status
     */
    private fun logShutdownAttempt(status: String) {
        scope.launch {
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                Log.e("PowerOffPinDialog", "📝 Shutdown attempt logged: STATUS=$status, TIME=$timestamp")
            } catch (e: Exception) {
                Log.e("PowerOffPinDialog", "Failed to log attempt: ${e.message}")
            }
        }
    }
}
