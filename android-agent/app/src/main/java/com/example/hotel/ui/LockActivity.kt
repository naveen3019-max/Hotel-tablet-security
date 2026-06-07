package com.example.hotel.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.hotel.admin.AdminActivity

class LockActivity : AppCompatActivity() {

    private var clickCount = 0
    private var lastClickTime = 0L
    private var isReceiverRegistered = false
    private val handler = Handler(Looper.getMainLooper())
    private var wifiCheckRunnable: Runnable? = null
    
    private val recoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.hotel.WIFI_RECOVERED") {
                android.util.Log.e("LockActivity", "🎉🎉🎉 WiFi recovered broadcast received - CLOSING BREACH SCREEN")
                finish()
            }
        }
    }
    
    private fun startWifiMonitoring() {
        // Check WiFi status every 2 seconds and auto-close if reconnected
        wifiCheckRunnable = object : Runnable {
            override fun run() {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val prefs = getSharedPreferences("agent", Context.MODE_PRIVATE)
                val minRssi = prefs.getInt("minRssi", -70)
                
                if (wifiManager.isWifiEnabled) {
                    @Suppress("DEPRECATION")
                    val connectionInfo = wifiManager.connectionInfo
                    val currentRssi = connectionInfo?.rssi ?: -127
                    val isConnected = connectionInfo != null && connectionInfo.networkId != -1
                    
                    if (isConnected && currentRssi >= minRssi) {
                        android.util.Log.e("LockActivity", "✅ WiFi reconnected (RSSI: $currentRssi >= $minRssi) - AUTO-CLOSING")
                        finish()
                        return
                    }
                }
                
                // Check again in 2 seconds
                handler.postDelayed(this, 2000)
            }
        }
        handler.postDelayed(wifiCheckRunnable!!, 2000)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        android.util.Log.e("LockActivity", "🔒 LockActivity starting...")
        
        // Ensure activity appears on top of everything and wakes screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        
        // CRITICAL: Check WiFi status immediately when starting
        // If WiFi is already connected, close immediately (recovery happened before we launched)
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val prefs = getSharedPreferences("agent", Context.MODE_PRIVATE)
        val minRssi = prefs.getInt("minRssi", -70)
        
        if (wifiManager.isWifiEnabled) {
            @Suppress("DEPRECATION")
            val connectionInfo = wifiManager.connectionInfo
            val currentRssi = connectionInfo?.rssi ?: -127
            val isConnected = connectionInfo != null && connectionInfo.networkId != -1
            
            if (isConnected && currentRssi >= minRssi) {
                android.util.Log.e("LockActivity", "✅ WiFi is already connected (RSSI: $currentRssi >= $minRssi) - CLOSING IMMEDIATELY")
                finish()
                return
            } else {
                android.util.Log.e("LockActivity", "📊 WiFi status on start: Connected=$isConnected, RSSI=$currentRssi, MinRSSI=$minRssi")
            }
        }
        
        // Register broadcast receiver BEFORE setting content view
        // This ensures we catch WiFi recovery broadcasts even if they come quickly
        val filter = IntentFilter("com.example.hotel.WIFI_RECOVERED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(recoveryReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(recoveryReceiver, filter)
        }
        isReceiverRegistered = true
        android.util.Log.e("LockActivity", "📡 Broadcast receiver registered in onCreate")
        
        // Start periodic WiFi checks to auto-close if WiFi reconnects
        startWifiMonitoring()

        // Show on lock screen and turn screen on
        if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Full-screen, immersive lock
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(android.graphics.Color.parseColor("#FF5722"))
        }

        val warningText = TextView(this).apply {
            text = "⚠️ DEVICE MOVED OUT OF ROOM\n\nStaff has been notified.\n\nPlease return device to room."
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setPadding(32, 32, 32, 32)
        }
        layout.addView(warningText)

        // Hidden admin access button (tap 5 times quickly)
        val hiddenButton = Button(this).apply {
            text = "."
            alpha = 0.01f // Nearly invisible
            setOnClickListener {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime < 500) {
                    clickCount++
                } else {
                    clickCount = 1
                }
                lastClickTime = currentTime

                if (clickCount >= 5) {
                    // Open admin activity
                    val intent = Intent(this@LockActivity, AdminActivity::class.java)
                    startActivity(intent)
                    clickCount = 0
                }
            }
        }
        layout.addView(hiddenButton)

        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.e("LockActivity", "📺 onResume called - breach screen is now visible")
    }

    override fun onPause() {
        super.onPause()
        android.util.Log.e("LockActivity", "📺 onPause called")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Stop WiFi monitoring
        wifiCheckRunnable?.let { handler.removeCallbacks(it) }
        
        // Unregister receiver when activity is destroyed
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(recoveryReceiver)
                isReceiverRegistered = false
                android.util.Log.e("LockActivity", "📡 Broadcast receiver unregistered")
            } catch (e: Exception) {
                android.util.Log.w("LockActivity", "Failed to unregister receiver", e)
            }
        }
    }

    override fun onBackPressed() {
        // Allow back button to work
        super.onBackPressed()
    }
}
