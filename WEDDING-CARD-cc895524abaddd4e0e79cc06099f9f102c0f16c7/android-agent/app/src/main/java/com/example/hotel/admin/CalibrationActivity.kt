package com.example.hotel.admin

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.hotel.data.AgentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RSSI Calibration Tool
 * Shows real-time WiFi signal strength from all nearby access points
 * Allows staff to set baseline RSSI for each room
 */
class CalibrationActivity : AppCompatActivity() {
    
    private lateinit var wifiManager: WifiManager
    private lateinit var roomInput: EditText
    private lateinit var networkList: ListView
    private lateinit var refreshButton: Button
    private lateinit var saveButton: Button
    private lateinit var statusText: TextView
    
    private val networks = mutableListOf<WifiNetwork>()
    private var selectedNetwork: WifiNetwork? = null
    
    data class WifiNetwork(
        val ssid: String,
        val bssid: String,
        val rssi: Int,
        val frequency: Int
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create simple layout programmatically
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        // Title
        val title = TextView(this).apply {
            text = "WiFi Calibration Tool"
            textSize = 24f
            setPadding(0, 0, 0, 24)
        }
        layout.addView(title)
        
        // Instructions
        val instructions = TextView(this).apply {
            text = "1. Enter room number\n2. Refresh to scan WiFi\n3. Select network\n4. Save configuration"
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(instructions)
        
        // Room input
        val roomLabel = TextView(this).apply {
            text = "Room Number:"
            setPadding(0, 0, 0, 8)
        }
        layout.addView(roomLabel)
        
        roomInput = EditText(this).apply {
            hint = "e.g., 101, 102, 103..."
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setPadding(16, 16, 16, 16)
        }
        layout.addView(roomInput)
        
        // Refresh button
        refreshButton = Button(this).apply {
            text = "Refresh WiFi Scan"
            setOnClickListener { scanWifi() }
        }
        layout.addView(refreshButton)
        
        // Status text
        statusText = TextView(this).apply {
            text = "Tap Refresh to start scanning"
            textSize = 12f
            setPadding(0, 16, 0, 8)
        }
        layout.addView(statusText)
        
        // Network list
        networkList = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setOnItemClickListener { _, _, position, _ ->
                selectedNetwork = networks[position]
                Toast.makeText(
                    this@CalibrationActivity,
                    "Selected: ${networks[position].ssid}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        layout.addView(networkList)
        
        // Save button
        saveButton = Button(this).apply {
            text = "Save Room Configuration"
            isEnabled = false
            setOnClickListener { saveCalibration() }
        }
        layout.addView(saveButton)
        
        setContentView(layout)
        
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    
    private fun scanWifi() {
        statusText.text = "Scanning WiFi networks..."
        
        try {
            @Suppress("DEPRECATION")
            val scanResults = wifiManager.scanResults
            
            networks.clear()
            scanResults.forEach { result ->
                networks.add(
                    WifiNetwork(
                        ssid = result.SSID,
                        bssid = result.BSSID,
                        rssi = result.level,
                        frequency = result.frequency
                    )
                )
            }
            
            // Sort by signal strength (strongest first)
            networks.sortByDescending { it.rssi }
            
            // Update UI
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_2,
                android.R.id.text1,
                networks.map { network ->
                    "${network.ssid} (${network.rssi} dBm)\nBSSID: ${network.bssid} | ${network.frequency} MHz"
                }
            )
            networkList.adapter = adapter
            
            statusText.text = "Found ${networks.size} networks. Tap to select."
            saveButton.isEnabled = networks.isNotEmpty()
            
        } catch (e: Exception) {
            Log.e("Calibration", "WiFi scan failed", e)
            statusText.text = "Scan failed: ${e.message}"
            Toast.makeText(this, "WiFi scan failed", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveCalibration() {
        val roomId = roomInput.text.toString().trim()
        val network = selectedNetwork
        
        if (roomId.isEmpty()) {
            Toast.makeText(this, "Please enter room number", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (network == null) {
            Toast.makeText(this, "Please select a network", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Calculate threshold (10 dBm lower than current RSSI for safety margin)
        val minRssi = network.rssi - 10
        
        statusText.text = "Saving configuration..."
        saveButton.isEnabled = false
        
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val repo = AgentRepository.default(applicationContext).alerts
                val auth = "Bearer changeme" // TODO: Replace with proper auth
                
                // Call backend API to save room configuration
                val payload = mapOf(
                    "roomId" to roomId,
                    "name" to "Room $roomId",
                    "bssid" to network.bssid,
                    "minRssi" to minRssi
                )
                
                repo.upsertRoom(auth, payload)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CalibrationActivity,
                        "✓ Room $roomId configured\nBSSID: ${network.bssid}\nThreshold: $minRssi dBm",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    statusText.text = "Configuration saved! Setup next room."
                    roomInput.text.clear()
                    selectedNetwork = null
                    saveButton.isEnabled = false
                }
                
            } catch (e: Exception) {
                Log.e("Calibration", "Save failed", e)
                withContext(Dispatchers.Main) {
                    statusText.text = "Save failed: ${e.message}"
                    Toast.makeText(
                        this@CalibrationActivity,
                        "Failed to save: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    saveButton.isEnabled = true
                }
            }
        }
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
