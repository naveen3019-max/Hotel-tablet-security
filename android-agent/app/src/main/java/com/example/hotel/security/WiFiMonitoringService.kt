package com.example.hotel.security

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class WiFiMonitoringService : Service() {

    private lateinit var wifiManager: WifiManager
    private lateinit var powerManager: PowerManager
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenAndWiFiReceiver: ScreenAndWiFiReceiver? = null
    
    private val sixSignalMonitor = SixSignalMonitor(this)
    private var isServiceRunning = false

    companion object {
        private const val TAG = "WiFiMonitoringService"
        private const val CHANNEL_ID = "wifi_security_channel"
        private const val NOTIFICATION_ID = 1001
        
        // Expose a way to interact with the service instance if needed
        var instance: WiFiMonitoringService? = null
            private set

        fun setMonitoringInterval(interval: Long, reason: String) {
            Log.d(TAG, "Changing interval to ${interval}ms due to: $reason")
            instance?.sixSignalMonitor?.setInterval(interval)
        }

        fun triggerBreachAlert(reason: String) {
            Log.e(TAG, "BREACH TRIGGERED: $reason")
            instance?.sixSignalMonitor?.triggerBreach(reason)
        }

        fun onNetworkLost() {
            Log.e(TAG, "Network connection lost!")
            // This is one of the 6 signals. If network is lost, trigger breach immediately
            triggerBreachAlert("ConnectivityAction: Network Lost")
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isServiceRunning) {
            Log.d(TAG, "Service is already running. Ignoring start command.")
            return START_STICKY
        }
        
        Log.i(TAG, "Starting WiFiMonitoringService...")
        startForeground(NOTIFICATION_ID, createNotification())
        
        acquireLocks()
        registerDynamicReceivers()
        
        sixSignalMonitor.startMonitoring()
        isServiceRunning = true
        
        return START_STICKY // System will recreate service if killed
    }

    private fun acquireLocks() {
        try {
            // Acquire Partial WakeLock to keep CPU running when screen is off
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "HotelSecurity::MonitoringWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
                Log.d(TAG, "WakeLock acquired")
            }

            // Acquire High Perf WifiLock to keep WiFi active and responsive when screen is off
            if (wifiLock == null) {
                val lockType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                
                wifiLock = wifiManager.createWifiLock(lockType, "HotelSecurity::MonitoringWifiLock")
                wifiLock?.setReferenceCounted(false)
                wifiLock?.acquire()
                Log.d(TAG, "WifiLock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire locks: ${e.message}", e)
        }
    }

    private fun registerDynamicReceivers() {
        if (screenAndWiFiReceiver == null) {
            screenAndWiFiReceiver = ScreenAndWiFiReceiver()
            // We register inside the service context
            screenAndWiFiReceiver?.register(this)
            Log.d(TAG, "Dynamic receivers registered")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Security Monitoring",
                NotificationManager.IMPORTANCE_LOW // Low importance so it doesn't vibrate/ring constantly
            ).apply {
                description = "Monitors device security and location"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security System Active")
            .setContentText("Device is being monitored.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // Fallback icon
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "Service being destroyed! Releasing resources...")
        
        sixSignalMonitor.stopMonitoring()
        
        screenAndWiFiReceiver?.unregister(this)
        screenAndWiFiReceiver = null
        
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
            wakeLock = null
            wifiLock = null
            Log.d(TAG, "Locks released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing locks", e)
        }
        
        isServiceRunning = false
        instance = null
        
        // Self-heal mechanism: If destroyed by system, try to restart unless explicitly stopped by app
        val restartIntent = Intent(this, WiFiMonitoringService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent)
            } else {
                startService(restartIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart service in onDestroy: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
