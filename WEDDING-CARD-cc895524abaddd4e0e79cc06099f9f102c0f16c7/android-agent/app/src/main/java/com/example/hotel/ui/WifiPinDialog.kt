package com.example.hotel.ui

import android.app.Activity
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.example.hotel.R

/**
 * PIN Dialog to authorize WiFi disable
 * Shows when user tries to turn OFF WiFi
 */
class WifiPinDialog : Activity() {

    private val ADMIN_PIN = "1234" // Default PIN, can be configured

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make dialog style window
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )
        
        // Create UI programmatically
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
            setBackgroundColor(android.graphics.Color.WHITE)
        }

        // Title
        val title = TextView(this).apply {
            text = "🔒 WiFi Protection"
            textSize = 24f
            setTextColor(android.graphics.Color.BLACK)
            setPadding(0, 0, 0, 30)
            gravity = android.view.Gravity.CENTER
        }
        layout.addView(title)

        // Message
        val message = TextView(this).apply {
            text = "Enter PIN to disable WiFi"
            textSize = 16f
            setTextColor(android.graphics.Color.DKGRAY)
            setPadding(0, 0, 0, 20)
            gravity = android.view.Gravity.CENTER
        }
        layout.addView(message)

        // PIN Input
        val pinInput = EditText(this).apply {
            hint = "Enter PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            textSize = 20f
            setPadding(20, 20, 20, 20)
        }
        layout.addView(pinInput, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 20)
        })

        // Button container
        val buttonLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        // Cancel Button
        val cancelButton = Button(this).apply {
            text = "Cancel"
            setOnClickListener {
                Log.i("WifiPinDialog", "❌ WiFi disable cancelled by user")
                
                // Disable button to prevent double-click
                isEnabled = false
                text = "Turning WiFi ON..."
                
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                
                // Start aggressive WiFi enforcement
                var attempts = 0
                val maxAttempts = 20  // Try for up to 10 seconds
                
                val enableWifiHandler = android.os.Handler(android.os.Looper.getMainLooper())
                val enableWifiRunnable = object : Runnable {
                    override fun run() {
                        attempts++
                        
                        // Force WiFi ON
                        if (!wifiManager.isWifiEnabled) {
                            Log.i("WifiPinDialog", "Attempt $attempts: WiFi OFF, forcing ON")
                            @Suppress("DEPRECATION")
                            wifiManager.isWifiEnabled = true
                        }
                        
                        // Check if WiFi is now ON
                        if (wifiManager.isWifiEnabled) {
                            Log.i("WifiPinDialog", "✅ WiFi confirmed ON after $attempts attempts")
                            // Clear flags
                            val prefs = getSharedPreferences("agent", Context.MODE_PRIVATE)
                            prefs.edit()
                                .putBoolean("wifi_pin_dialog_active", false)
                                .putBoolean("wifi_disable_authorized", false)
                                .apply()
                            Toast.makeText(this@WifiPinDialog, "WiFi is ON", Toast.LENGTH_SHORT).show()
                            finish()
                        } else if (attempts >= maxAttempts) {
                            Log.e("WifiPinDialog", "⚠️ WiFi still OFF after $attempts attempts, giving up")
                            // Clear flags anyway
                            val prefs = getSharedPreferences("agent", Context.MODE_PRIVATE)
                            prefs.edit()
                                .putBoolean("wifi_pin_dialog_active", false)
                                .putBoolean("wifi_disable_authorized", false)
                                .apply()
                            Toast.makeText(this@WifiPinDialog, "Please check WiFi manually", Toast.LENGTH_LONG).show()
                            finish()
                        } else {
                            // Try again in 500ms
                            enableWifiHandler.postDelayed(this, 500)
                        }
                    }
                }
                
                // Start the loop
                enableWifiHandler.post(enableWifiRunnable)
            }
        }
        buttonLayout.addView(cancelButton, android.widget.LinearLayout.LayoutParams(
            0,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            setMargins(0, 0, 10, 0)
        })

        // Confirm Button
        val confirmButton = Button(this).apply {
            text = "Confirm"
            setOnClickListener {
                val enteredPin = pinInput.text.toString()
                
                // Get configured PIN from preferences
                val prefs = getSharedPreferences("agent", Context.MODE_PRIVATE)
                val configuredPin = prefs.getString("admin_pin", ADMIN_PIN) ?: ADMIN_PIN
                
                if (enteredPin == configuredPin) {
                    Log.i("WifiPinDialog", "✅ Correct PIN entered - allowing WiFi disable")
                    
                    // Set flag to allow WiFi disable and clear dialog active flag
                    prefs.edit()
                        .putBoolean("wifi_disable_authorized", true)
                        .putBoolean("wifi_pin_dialog_active", false)
                        .apply()
                    
                    // Disable WiFi immediately - authorized
                    try {
                        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                        @Suppress("DEPRECATION")
                        wifiManager.isWifiEnabled = false
                        
                        Toast.makeText(this@WifiPinDialog, "WiFi will be disabled", Toast.LENGTH_SHORT).show()
                        Log.i("WifiPinDialog", "✅ WiFi disable command sent (authorized)")
                    } catch (e: Exception) {
                        Log.e("WifiPinDialog", "Failed to disable WiFi: ${e.message}")
                    }
                    finish()
                } else {
                    Log.w("WifiPinDialog", "❌ Incorrect PIN entered")
                    Toast.makeText(this@WifiPinDialog, "Incorrect PIN", Toast.LENGTH_SHORT).show()
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
            setMargins(10, 0, 0, 0)
        })

        layout.addView(buttonLayout)

        setContentView(layout)
        
        // Focus on PIN input
        pinInput.requestFocus()
        
        Log.i("WifiPinDialog", "WiFi PIN dialog displayed")
    }

    override fun onBackPressed() {
        // Treat back button same as cancel
        Log.i("WifiPinDialog", "❌ Back button pressed - treating as cancel")
        
        // Ensure WiFi stays ON
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (!wifiManager.isWifiEnabled) {
                Log.i("WifiPinDialog", "WiFi is OFF - re-enabling...")
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = true
                
                // Wait for WiFi to actually turn ON
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (wifiManager.isWifiEnabled) {
                        Log.i("WifiPinDialog", "✅ WiFi confirmed ON after back button")
                        // Clear flags
                        val prefs = getSharedPreferences("agent", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putBoolean("wifi_pin_dialog_active", false)
                            .putBoolean("wifi_disable_authorized", false)
                            .apply()
                        Toast.makeText(this, "WiFi remains ON", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Log.e("WifiPinDialog", "⚠️ WiFi still OFF after re-enable attempt")
                        // Try one more time
                        @Suppress("DEPRECATION")
                        wifiManager.isWifiEnabled = true
                        // Clear flags
                        val prefs = getSharedPreferences("agent", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putBoolean("wifi_pin_dialog_active", false)
                            .putBoolean("wifi_disable_authorized", false)
                            .apply()
                        Toast.makeText(this, "WiFi remains ON", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }, 1000) // Wait 1 second for WiFi to stabilize
            } else {
                Log.i("WifiPinDialog", "✅ WiFi already ON")
                // Clear flags
                val prefs = getSharedPreferences("agent", Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean("wifi_pin_dialog_active", false)
                    .putBoolean("wifi_disable_authorized", false)
                    .apply()
                Toast.makeText(this, "WiFi remains ON", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Log.e("WifiPinDialog", "Failed to ensure WiFi is ON: ${e.message}")
            // Clear flags
            val prefs = getSharedPreferences("agent", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("wifi_pin_dialog_active", false)
                .putBoolean("wifi_disable_authorized", false)
                .apply()
            Toast.makeText(this, "WiFi remains ON", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
