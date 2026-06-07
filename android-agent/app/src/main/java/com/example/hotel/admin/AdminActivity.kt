package com.example.hotel.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.hotel.R
import java.security.MessageDigest

/**
 * Admin Activity for password-protected settings
 * Allows staff to:
 * - Change device settings (WiFi, Power)
 * - Update admin PIN
 * - Access calibration tool
 */
class AdminActivity : AppCompatActivity() {
    
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var prefs: android.content.SharedPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        prefs = getSharedPreferences("admin", MODE_PRIVATE)
        
        // Check if PIN is set, otherwise force setup
        if (!prefs.contains("pin_hash")) {
            showSetupPinDialog()
        } else {
            showVerifyPinDialog()
        }
    }
    
    private fun showSetupPinDialog() {
        val builder = AlertDialog.Builder(this)
        val input = EditText(this).apply {
            hint = "Enter new 4-6 digit PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or 
                       android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        
        builder.setTitle("Setup Admin PIN")
            .setMessage("Create a PIN to protect device settings")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Set PIN") { _, _ ->
                val pin = input.text.toString()
                if (pin.length in 4..6) {
                    val hash = hashPin(pin)
                    prefs.edit().putString("pin_hash", hash).apply()
                    Toast.makeText(this, "PIN set successfully", Toast.LENGTH_SHORT).show()
                    showAdminMenu()
                } else {
                    Toast.makeText(this, "PIN must be 4-6 digits", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .show()
    }
    
    private fun showVerifyPinDialog() {
        val builder = AlertDialog.Builder(this)
        val input = EditText(this).apply {
            hint = "Enter Admin PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or 
                       android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        
        builder.setTitle("Admin Access Required")
            .setMessage("Enter PIN to access settings")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Verify") { _, _ ->
                val pin = input.text.toString()
                val storedHash = prefs.getString("pin_hash", "")
                
                if (hashPin(pin) == storedHash) {
                    showAdminMenu()
                } else {
                    Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .show()
    }
    
    private fun showAdminMenu() {
        val builder = AlertDialog.Builder(this)
        val options = arrayOf(
            "Open WiFi Settings",
            "Open Device Settings",
            "Start Calibration Tool",
            "Change Admin PIN",
            "Exit Kiosk Mode (5 min)"
        )
        
        builder.setTitle("Admin Menu")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openWifiSettings()
                    1 -> openDeviceSettings()
                    2 -> startCalibration()
                    3 -> changePin()
                    4 -> exitKioskMode()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .show()
    }
    
    private fun openWifiSettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
            Toast.makeText(this, "Please return when done", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open WiFi settings", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
    
    private fun openDeviceSettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            Toast.makeText(this, "Please return when done", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open settings", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
    
    private fun startCalibration() {
        val intent = Intent(this, CalibrationActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun changePin() {
        val builder = AlertDialog.Builder(this)
        val input = EditText(this).apply {
            hint = "Enter new 4-6 digit PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or 
                       android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        
        builder.setTitle("Change Admin PIN")
            .setView(input)
            .setPositiveButton("Update") { _, _ ->
                val pin = input.text.toString()
                if (pin.length in 4..6) {
                    val hash = hashPin(pin)
                    prefs.edit().putString("pin_hash", hash).apply()
                    Toast.makeText(this, "PIN updated successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "PIN must be 4-6 digits", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .show()
    }
    
    private fun exitKioskMode() {
        // Temporarily disable kiosk for 5 minutes
        prefs.edit().putLong("kiosk_disabled_until", System.currentTimeMillis() + 300000).apply()
        Toast.makeText(this, "Kiosk disabled for 5 minutes", Toast.LENGTH_LONG).show()
        finish()
    }
    
    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
