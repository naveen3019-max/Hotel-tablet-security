package com.example.hotel.service

import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.graphics.PixelFormat
import androidx.core.app.NotificationCompat
import com.example.hotel.security.BatteryWatcher
import com.example.hotel.security.WifiFence
import com.example.hotel.security.WifiStateReceiver
import com.example.hotel.security.ScreenStateReceiver
import com.example.hotel.data.AgentRepository
import com.example.hotel.data.HeartbeatRequest
import com.example.hotel.data.BreachRequest
import com.example.hotel.data.BatteryRequest
import com.example.hotel.service.OfflineQueueManager
import kotlinx.coroutines.*

/**
 * Foreground Service to keep WiFi and Battery monitoring alive
 */
class KioskService : Service() {

    private lateinit var wifiFence: WifiFence
    private lateinit var batteryWatcher: BatteryWatcher
    private var wifiStateReceiver: WifiStateReceiver? = null
    private var screenStateReceiver: ScreenStateReceiver? = null
    private var breachOverlayView: View? = null
    private var windowManager: WindowManager? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRunning = false

    companion object {
        const val CHANNEL_ID = "HotelKioskService"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Acquire WiFi lock to keep WiFi connected even when screen turns OFF
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "HotelSecurityWifiLock")
        wifiLock?.acquire()
        Log.i("KioskService", "🔒 WiFi Lock acquired - WiFi will stay connected during screen OFF")

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hotel Security Active")
            .setContentText("Device monitoring in progress")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        Log.d("KioskService", "Foreground service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e("KioskService", "")
        Log.e("KioskService", "═══════════════════════════════════════════════")
        Log.e("KioskService", "🚀 KIOSK SERVICE STARTING - v2.5.0")
        Log.e("KioskService", "═══════════════════════════════════════════════")
        Log.e("KioskService", "")
        
        if (!isRunning) {
            isRunning = true
            
            // WiFi PIN protection disabled
            // startWifiPinProtection()
            
            // Start monitoring (requires provisioning)
            startMonitoring()
        }
        return START_STICKY
    }
    
    /**
     * WiFi PIN Protection - DISABLED
     */
    private fun startWifiPinProtection() {
        // WiFi PIN protection has been disabled
        Log.i("KioskService", "ℹ️ WiFi PIN Protection is DISABLED")
    }

    private fun startMonitoring() {
        try {
            Log.d("KioskService", "Initializing monitoring components...")
            val prefs = getSharedPreferences("agent", Context.MODE_PRIVATE)

            val deviceId = prefs.getString("device_id", "TAB-UNKNOWN")!!
            val roomId = prefs.getString("room_id", "UNKNOWN")!!
            val bssid = prefs.getString("bssid", "AA:BB:CC:DD:EE:FF")!!
            val ssid = prefs.getString("ssid", null)
            
            // CRITICAL FIX: Detect unconfigured room (default BSSID) and use permissive threshold
            // When no room config exists, only detect complete WiFi loss, not weak signal
            val isRoomConfigured = bssid != "AA:BB:CC:DD:EE:FF"
            val defaultMinRssi = if (isRoomConfigured) -70 else -90  // Permissive threshold for unconfigured rooms
            val minRssi = prefs.getInt("minRssi", defaultMinRssi)
            
            val backendUrl = prefs.getString("backend_url", "NOT_SET")
            
            Log.i("KioskService", "📱 Device Configuration:")
            Log.i("KioskService", "   Device ID: '$deviceId'")
            Log.i("KioskService", "   Room ID: '$roomId'")
            Log.i("KioskService", "   Backend URL: '$backendUrl'")
            Log.i("KioskService", "   SSID: '$ssid'")
            Log.i("KioskService", "   BSSID: '$bssid'")
            Log.i("KioskService", "   Room Configured: $isRoomConfigured")
            Log.i("KioskService", "   Min RSSI: $minRssi dBm ${if (!isRoomConfigured) "(PERMISSIVE - no room config)" else ""}")
            
            val auth = prefs.getString("jwt_token", null)?.let { "Bearer $it" }
            Log.i("KioskService", "   JWT Token: ${if (auth != null) "Present (${auth.length} chars)" else "❌ MISSING!"}")
            
            Log.i("KioskService", "")
            Log.i("KioskService", "🔧 ACTIVE MONITORING:")
            Log.i("KioskService", "   ✅ WiFi Breach Detection: ENABLED")
            Log.i("KioskService", "      • Signal Threshold: $minRssi dBm")
            Log.i("KioskService", "      • Grace Period: 3 seconds")
            Log.i("KioskService", "      • WiFi OFF Detection: ENABLED")
            Log.i("KioskService", "   ✅ Battery Low Detection: ENABLED (20%)")
            Log.i("KioskService", "   ❌ Tamper Detection: DISABLED")
            Log.i("KioskService", "")

            if (auth == null) {
                Log.e("KioskService", "❌ CRITICAL: No JWT token found - Device needs registration!")
                Log.e("KioskService", "   Please open the app and complete device registration")
                stopSelf()
                return
            }

            Log.d(
                "KioskService",
                "Configuration: deviceId=$deviceId, roomId=$roomId, targetSSID=$ssid, " +
                "targetBssid=$bssid, minRssi=$minRssi"
            )
            
            // Check notification permissions
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val areNotificationsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                notificationManager.areNotificationsEnabled()
            } else {
                true
            }
            Log.e("KioskService", "📬 Notifications enabled: $areNotificationsEnabled")
            
            if (!areNotificationsEnabled) {
                Log.e("KioskService", "⚠️ WARNING: Notifications are DISABLED - breach alerts will not work!")
            }
            
            /* ---------------- WIFI FENCE WITH MULTI-SIGNAL DETECTION -------- */

        wifiFence = WifiFence(
            context = this,
            targetBssid = bssid,
            targetSsid = ssid,
            minRssi = minRssi,
            graceSeconds = 3,
            onBreach = { currentRssiNullable: Int? ->

            val currentRssi = currentRssiNullable ?: -127
            
            Log.e("KioskService", "")
            Log.e("KioskService", "🚨🚨🚨 WiFi FENCE BREACH DETECTED!")
            Log.e("KioskService", "   Current RSSI: $currentRssi dBm")
            Log.e("KioskService", "   Min RSSI: $minRssi dBm")
            Log.e("KioskService", "   Target BSSID: $bssid")
            Log.e("KioskService", "   Target SSID: $ssid")
            
            // Check if screen is locked OR within grace period after lock - WiFi disconnects are NORMAL
            val shouldIgnore = ScreenStateReceiver.shouldIgnoreWiFiBreach()
            val isScreenLocked = ScreenStateReceiver.getIsScreenLocked()
            Log.e("KioskService", "   Screen State: ${if (isScreenLocked) "LOCKED 🌙" else "UNLOCKED ☀️"}")
            Log.e("KioskService", "   Should Ignore: ${if (shouldIgnore) "YES (screen lock grace period)" else "NO"}")
            
            if (shouldIgnore) {
                Log.w("KioskService", "🌙 Ignoring WiFi disconnect - Screen lock or grace period active")
                Log.w("KioskService", "   This is NOT a security breach, just Android power management!")
                return@WifiFence
            }
            
            // Check if WiFi PIN dialog is currently active
            val prefs = getSharedPreferences("agent", Context.MODE_PRIVATE)
            val pinDialogActive = prefs.getBoolean("wifi_pin_dialog_active", false)
            
            if (pinDialogActive) {
                Log.w("KioskService", "🔐 WiFi PIN dialog is active - ignoring breach")
                return@WifiFence
            }

            // Send breach alert
            Log.i("KioskService", "🚨 Sending breach alert to backend...")
            Log.i("KioskService", "   Device ID: '$deviceId'")
            Log.i("KioskService", "   Room ID: '$roomId'")
            Log.i("KioskService", "   RSSI: $currentRssi dBm")
            Log.i("KioskService", "   Auth Token: ${if (auth != null) "Present" else "Missing"}")
            
            serviceScope.launch {
                try {
                    Log.d("KioskService", "🌐 Making API call to breach endpoint...")
                    val response = AgentRepository.default(applicationContext).alerts.breach(
                        auth,
                        BreachRequest(deviceId, roomId, currentRssi)
                    )
                    Log.i("KioskService", "✅ Breach alert sent successfully: $response")
                } catch (e: Exception) {
                    Log.e("KioskService", "❌ Breach alert failed: ${e.javaClass.simpleName}: ${e.message}")
                    Log.e("KioskService", "Full stack trace:", e)
                    try {
                        Log.d("KioskService", "💾 Queuing breach alert for later sync...")
                        OfflineQueueManager.getInstance(applicationContext).queueAlert(
                            type = "breach",
                            deviceId = deviceId,
                            roomId = roomId,
                            payload = mapOf("rssi" to currentRssi)
                        )
                        Log.d("KioskService", "✅ Breach alert queued offline successfully")
                    } catch (queueEx: Exception) {
                        Log.e("KioskService", "❌ Failed to queue breach alert", queueEx)
                    }
                }
            }

            // Show breach screen - launch activity directly
            Log.e("KioskService", "")
            Log.e("KioskService", "═══════════════════════════════════════════")
            Log.e("KioskService", "🚨🚨🚨 BREACH DETECTED - LAUNCHING ORANGE SCREEN")
            Log.e("KioskService", "═══════════════════════════════════════════")
            Log.e("KioskService", "")
            
            try {
                // Launch LockActivity directly with aggressive flags
                val lockIntent = Intent(this, com.example.hotel.ui.LockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                }
                
                // Launch activity immediately
                startActivity(lockIntent)
                Log.e("KioskService", "✅ ORANGE BREACH SCREEN LAUNCHED")
                
                // Also show a simple notification as backup
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    100,
                    lockIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                val breachNotification = NotificationCompat.Builder(this, "BREACH_ALERTS")
                    .setContentTitle("⚠️ WiFi Disconnected")
                    .setContentText("Please reconnect WiFi to restore security monitoring")
                    .setStyle(NotificationCompat.BigTextStyle()
                        .bigText("WiFi connection lost. Please reconnect to WiFi immediately to restore device security monitoring."))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setFullScreenIntent(pendingIntent, true)
                    .build()
                
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(999, breachNotification)
                Log.e("KioskService", "✅ Backup notification posted")
                
            } catch (e: Exception) {
                Log.e("KioskService", "❌ CRITICAL: Failed to show breach alert: ${e.message}", e)
            }
            },
            onRecovery = {
                // WiFi recovered - close the breach screen if it's open
                Log.e("KioskService", "")
                Log.e("KioskService", "═══════════════════════════════════════════")
                Log.e("KioskService", "🎉🎉🎉 WIFI RECOVERY CALLBACK TRIGGERED")
                Log.e("KioskService", "═══════════════════════════════════════════")
                
                // Send recovery heartbeat to backend to clear breach status
                Log.e("KioskService", "📤 Sending recovery heartbeat to backend...")
                serviceScope.launch {
                    try {
                        val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
                        @Suppress("DEPRECATION")
                        val connectionInfo = wifiManager.connectionInfo
                        val currentRssi = connectionInfo?.rssi ?: -127
                        val currentBssid = connectionInfo?.bssid ?: bssid
                        
                        // Get current battery level
                        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
                        val batteryLevel = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                        
                        AgentRepository.default(applicationContext).alerts.heartbeat(
                            auth,
                            HeartbeatRequest(
                                deviceId = deviceId,
                                roomId = roomId,
                                wifiBssid = currentBssid,
                                rssi = currentRssi,
                                battery = batteryLevel
                            )
                        )
                        Log.e("KioskService", "✅ Recovery heartbeat sent successfully (RSSI: $currentRssi, Battery: $batteryLevel%) - backend should clear breach status")
                    } catch (e: Exception) {
                        Log.e("KioskService", "❌ Recovery heartbeat failed: ${e.message}", e)
                    }
                }
                
                Log.e("KioskService", "📡 Sending broadcast to close LockActivity")
                
                // Sync any queued offline alerts now that connectivity is restored
                Log.e("KioskService", "📦 Syncing queued alerts now that WiFi is restored...")
                serviceScope.launch {
                    try {
                        val offlineQueue = OfflineQueueManager.getInstance(applicationContext)
                        val syncResult = offlineQueue.syncQueuedAlerts()
                        Log.e("KioskService", "✅ Offline sync completed: ${syncResult.synced} alerts synced, ${syncResult.failed} failed")
                        
                        if (syncResult.synced > 0) {
                            Log.e("KioskService", "🎉 ${syncResult.synced} breach alerts successfully sent to backend!")
                        }
                    } catch (e: Exception) {
                        Log.e("KioskService", "❌ Failed to sync offline queue: ${e.message}", e)
                    }
                }
                
                // Dismiss breach notification
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(999)
                
                Log.e("KioskService", "✅ Breach notification dismissed")
                
                // Broadcast to close LockActivity
                val intent = Intent("com.example.hotel.WIFI_RECOVERED")
                intent.setPackage(packageName)  // Explicitly target this app
                sendBroadcast(intent)
                Log.e("KioskService", "✅ WIFI_RECOVERED broadcast sent")
                
                // Show toast notification
                Handler(Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(
                        applicationContext,
                        "✅ WiFi Connection Restored - Back Online",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        )

        Log.i("KioskService", "🚀 Starting WiFi Fence monitoring...")
        Log.i("KioskService", "   Target BSSID: $bssid")
        Log.i("KioskService", "   Target SSID: $ssid") 
        Log.i("KioskService", "   Min RSSI Threshold: $minRssi dBm")
        Log.i("KioskService", "   Grace Period: 3 seconds")
        
        wifiFence.start()
        
        Log.i("KioskService", "✅ WiFi Fence started successfully")

        /* ---------------- WIFI STATE RECEIVER (BACKUP) ---------------- */
        // Keep receiver as backup
        wifiStateReceiver = WifiStateReceiver()
        val wifiFilter = IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
        registerReceiver(wifiStateReceiver, wifiFilter)
        
        /* ---------------- SCREEN STATE RECEIVER ---------------- */
        // Register screen lock/unlock detection to prevent false breach alerts
        screenStateReceiver = ScreenStateReceiver()
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenStateReceiver, screenFilter)
        
        // Initialize current screen state (important if service starts while screen is locked)
        ScreenStateReceiver.initializeScreenState(this)
        Log.i("KioskService", "✅ Screen state receiver registered - WiFi changes during screen lock will be ignored")

        /* ---------------- BATTERY WATCHER ---------------- */

        batteryWatcher = BatteryWatcher(this) { level ->
            Log.e("KioskService", "🚨🔋 LOW BATTERY ALERT: $level% - Sending to backend...")

            serviceScope.launch {
                try {
                    val response = AgentRepository.default(applicationContext).alerts.battery(
                        auth,
                        BatteryRequest(deviceId, level)
                    )
                    Log.i("KioskService", "✅ Battery alert sent successfully: $level%")
                } catch (e: Exception) {
                    Log.e("KioskService", "❌ Battery alert failed: ${e.message}", e)
                    try {
                        OfflineQueueManager.getInstance(applicationContext).queueAlert(
                            type = "battery",
                            deviceId = deviceId,
                            roomId = roomId,
                            payload = mapOf("level" to level)
                        )
                        Log.w("KioskService", "📦 Battery alert queued for retry")
                    } catch (queueEx: Exception) {
                        Log.e("KioskService", "Failed to queue battery alert", queueEx)
                    }
                }
            }
        }

        batteryWatcher.start(threshold = 20)
        Log.i("KioskService", "🔋 Battery monitoring initialized (threshold: 20%)")

        /* ---------------- HEARTBEAT ---------------- */

        serviceScope.launch {
            Log.d("KioskService", "Heartbeat coroutine started")
            val repo = AgentRepository.default(applicationContext).alerts

            while (isRunning) {
                try {
                    Log.v("KioskService", "Heartbeat loop tick starting...")
                    
                    val rssi = wifiFence.getCurrentRssi() ?: -127
                    Log.v("KioskService", "Fetched RSSI: $rssi")
                    
                    val bssidActual = wifiFence.getCurrentBssid() ?: "02:00:00:00:00:00"
                    Log.v("KioskService", "Fetched BSSID: $bssidActual")
                    
                    val battery = batteryWatcher.getCurrentLevel()
                    Log.v("KioskService", "Fetched Battery: $battery%")

                    Log.d("KioskService", "Sending heartbeat: deviceId=$deviceId, bssid=$bssidActual, rssi=$rssi, battery=$battery")
                    val response = repo.heartbeat(
                        auth,
                        HeartbeatRequest(deviceId, roomId, bssidActual, rssi, battery)
                    )

                    val status = response["status"] as? String
                    Log.i("KioskService", "Heartbeat successful. Server status: $status")
 
                    // Sync offline queue if heartbeat succeeded
                    serviceScope.launch {
                        try {
                            Log.v("KioskService", "Checking offline queue for sync...")
                            val offlineQueue = OfflineQueueManager.getInstance(applicationContext)
                            val syncResult = offlineQueue.syncQueuedAlerts()
                            if (syncResult.synced > 0) {
                                Log.i("KioskService", "Successfully synced ${syncResult.synced} offline alerts")
                            }
                        } catch (e: Exception) {
                            Log.e("KioskService", "Offline sync sub-task failed", e)
                        }
                    }
 
                    val isBadStatus = status?.equals("LOCKED", ignoreCase = true) == true ||
                                     status?.equals("COMPROMISED", ignoreCase = true) == true ||
                                     status?.equals("BREACH", ignoreCase = true) == true

                    if (isBadStatus) {
                         Log.w("KioskService", "🚨 Backend requested LOCK (status=$status)")
                         val lockIntent = Intent(applicationContext, com.example.hotel.ui.LockActivity::class.java)
                             .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                         startActivity(lockIntent)
                    }
                } catch (e: Exception) {
                    Log.e("KioskService", "Heartbeat failed in loop: ${e.message}", e)
                }

                Log.v("KioskService", "Waiting 4s for next heartbeat...")
                delay(4_000)
            }
            Log.d("KioskService", "Heartbeat loop exited (isRunning=false)")
        }
    } catch (e: Exception) {
        Log.e("KioskService", "Fatal error starting monitoring", e)
    }
}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Low priority channel for foreground service
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Hotel Kiosk Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps device monitoring active"
            }
            manager.createNotificationChannel(serviceChannel)
            
            // High priority channel for breach alerts
            val breachChannel = NotificationChannel(
                "BREACH_ALERTS",
                "Security Breach Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical security breach notifications"
                enableVibration(true)
                enableLights(true)
            }
            manager.createNotificationChannel(breachChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        wifiFence.stop()
        batteryWatcher.stop()
        
        // Release WiFi lock
        wifiLock?.let {
            if (it.isHeld) {
                it.release()
                Log.i("KioskService", "🔓 WiFi Lock released")
            }
        }
        
        // Remove breach overlay if showing
        dismissBreachOverlay()
        
        // Unregister WiFi state receiver
        wifiStateReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.i("KioskService", "WiFi state receiver unregistered")
            } catch (e: Exception) {
                Log.e("KioskService", "Failed to unregister WiFi receiver: ${e.message}")
            }
        }
        
        // Unregister screen state receiver
        screenStateReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.i("KioskService", "Screen state receiver unregistered")
            } catch (e: Exception) {
                Log.e("KioskService", "Failed to unregister screen receiver: ${e.message}")
            }
        }
        
        Log.d("KioskService", "Service stopped")
    }
    
    private fun showBreachOverlay() {
        Handler(Looper.getMainLooper()).post {
            try {
                if (breachOverlayView != null) {
                    Log.w("KioskService", "Breach overlay already showing")
                    return@post
                }
                
                windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                
                // Inflate the breach screen layout
                val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                breachOverlayView = inflater.inflate(
                    applicationContext.resources.getIdentifier("activity_lock", "layout", packageName), 
                    null
                )
                
                // Set up window params for overlay
                val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }
                
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
                )
                
                windowManager?.addView(breachOverlayView, params)
                Log.e("KioskService", "✅ Breach overlay added to WindowManager")
                
            } catch (e: Exception) {
                Log.e("KioskService", "❌ Failed to show breach overlay: ${e.message}", e)
                breachOverlayView = null
            }
        }
    }
    
    private fun dismissBreachOverlay() {
        Handler(Looper.getMainLooper()).post {
            try {
                if (breachOverlayView != null && windowManager != null) {
                    windowManager?.removeView(breachOverlayView)
                    breachOverlayView = null
                    Log.e("KioskService", "✅ Breach overlay removed")
                }
            } catch (e: Exception) {
                Log.e("KioskService", "❌ Failed to remove breach overlay: ${e.message}", e)
            }
        }
    }
    
    private fun showBreachNotification() {
        val lockIntent = Intent(this, com.example.hotel.ui.LockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, lockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, "BREACH_ALERTS")
            .setContentTitle("🚨 SECURITY BREACH")
            .setContentText("Device moved out of room - Tap to view")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(999, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
