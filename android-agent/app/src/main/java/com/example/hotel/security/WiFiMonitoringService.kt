package com.example.hotel.security

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
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
        
        @Volatile
        var isRunning: Boolean = false
        
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
            triggerBreachAlert("ConnectivityAction: Network Lost")
        }
    }

    fun getManufacturer(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("samsung") -> "samsung"
            manufacturer.contains("lenovo") -> "lenovo"
            else -> "generic"
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        createNotificationChannel()

        val manufacturer = getManufacturer()
        if (manufacturer == "samsung") {
            startForeground(NOTIFICATION_ID, createHighPriorityNotification())
            Log.d(TAG, "Samsung optimization bypass active")
        }
        if (manufacturer == "lenovo") {
            Log.d(TAG, "Lenovo specific power management bypass active")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isServiceRunning) {
            Log.d(TAG, "Service is already running. Ignoring start command.")
            return START_STICKY
        }
        
        Log.i(TAG, "Starting WiFiMonitoringService...")
        startForeground(NOTIFICATION_ID, createNotification())
        
        isRunning = true
        isServiceRunning = true

        acquireLocks()
        registerDynamicReceivers()
        requestDozeExemption() 
        scheduleDozeAlarm()    
        
        sixSignalMonitor.startMonitoring()
        
        return START_STICKY
    }

    private fun requestDozeExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.w(TAG, "Doze exemption not granted! Prompting user...")
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
        }
    }

    private fun scheduleDozeAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(this, WiFiMonitoringService::class.java)
        val pendingIntent = PendingIntent.getService(
            this,
            0,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 60_000L,
                pendingIntent
            )
            Log.d(TAG, "Scheduled next Doze wakeup alarm in 60s")
        }
    }

    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "HotelSecurity:WakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
                Log.d(TAG, "WakeLock acquired permanently")
            }

            if (wifiLock == null) {
                wifiLock = wifiManager.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "HotelSecurity:WifiLock"
                )
                wifiLock?.setReferenceCounted(false)
                wifiLock?.acquire()
                Log.d(TAG, "WifiLock (HIGH_PERF) acquired permanently")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire locks: ${e.message}", e)
        }
    }

    private fun registerDynamicReceivers() {
        if (screenAndWiFiReceiver == null) {
            screenAndWiFiReceiver = ScreenAndWiFiReceiver()
            screenAndWiFiReceiver?.register(this)
            Log.d(TAG, "Dynamic receivers registered")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Security Monitoring",
                NotificationManager.IMPORTANCE_HIGH
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
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }
    
    private fun createHighPriorityNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security System Active (Samsung)")
            .setContentText("Device is being aggressively monitored.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
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
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing locks", e)
        }
        
        isServiceRunning = false
        isRunning = false
        instance = null
        
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
