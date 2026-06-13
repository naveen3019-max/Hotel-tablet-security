package com.example.hotel.security

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat

class WiFiMonitoringService : Service() {

    private lateinit var powerManager: PowerManager
    private lateinit var wifiManager: WifiManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    val sixSignalMonitor = SixSignalMonitor(this)
    private var isServiceRunning = false

    companion object {
        private const val TAG = "WiFiMonitoringService"
        private const val CHANNEL_ID = "wifi_security_channel"
        private const val NOTIFICATION_ID = 1001
        
        @Volatile var isRunning: Boolean = false
        var lastBreachTime = 0L 
        const val BREACH_COOLDOWN = 15_000L 
        
        var instance: WiFiMonitoringService? = null
            private set

        fun triggerBreachAlert(reason: String) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBreachTime > BREACH_COOLDOWN) {
                lastBreachTime = now
                Log.e(TAG, "🚨 BREACH TRIGGERED: $reason")
                instance?.sixSignalMonitor?.triggerBreach(reason)
            }
        }

        fun onNetworkLost() {
            triggerBreachAlert("Network Connectivity Lost")
        }

        fun reAcquireWakeLock() {
            instance?.acquireLocksSafely() 
        }

        fun setMonitoringInterval(interval: Long, reason: String = "") {
            Log.i(TAG, "God Mode: Interval strictly fixed at 15s. Ignoring request for $interval ms. Reason: $reason")
            reAcquireWakeLock()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        createNotificationChannel()
        applyManufacturerFix() 
    }

    private fun applyManufacturerFix() {
        val manufacturer = Build.MANUFACTURER.lowercase()
        when {
            manufacturer.contains("samsung") -> {
                Log.d(TAG, "God Mode: Samsung One UI bypass active")
                startForeground(NOTIFICATION_ID, buildNotification(NotificationCompat.PRIORITY_MAX)) 
            }
            manufacturer.contains("lenovo") -> {
                Log.d(TAG, "God Mode: Lenovo background bypass active")
                startForeground(NOTIFICATION_ID, buildNotification(NotificationCompat.PRIORITY_HIGH))
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification(NotificationCompat.PRIORITY_DEFAULT))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ALARM_CHECK") { 
            Log.d(TAG, "God Mode: Doze ALARM_CHECK fired!")
            acquireLocksSafely()
            scheduleNextAlarm()
            if (!sixSignalMonitor.isMonitoringAlive()) {
                Log.w(TAG, "God Mode: Monitoring paused by Doze! Forcing restart...")
                sixSignalMonitor.startMonitoring()
            } else {
                sixSignalMonitor.forceImmediateCheck() 
            }
            return START_STICKY
        }

        if (!isServiceRunning) {
            Log.i(TAG, "God Mode: Starting impenetrable monitoring...")
            isRunning = true
            isServiceRunning = true
            
            acquireLocksSafely()
            scheduleNextAlarm()
            sixSignalMonitor.startMonitoring()
        }
        
        return START_STICKY 
    }

    private fun scheduleNextAlarm() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getService(
            this, 1001,
            Intent(this, WiFiMonitoringService::class.java).apply { action = "ALARM_CHECK" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle( 
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 55_000L,
                pi
            )
        } else {
            am.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 55_000L,
                pi
            )
        }
    }

    private fun acquireLocksSafely() {
        if (wakeLock?.isHeld == false || wakeLock == null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HotelSecurity:GodModeWakeLock")
            wakeLock?.acquire() 
        }
        if (wifiLock?.isHeld == false || wifiLock == null) {
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "HotelSecurity:GodModeWifiLock")
            wifiLock?.acquire() 
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Security Active", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "God Mode monitoring active"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(priority: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔒 Security Active")
            .setContentText("Monitoring every 15s")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_ALARM) 
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.e(TAG, "God Mode: Task removed! Triggering immediate self-resurrection.")
        scheduleNextAlarm() 
    }

    override fun onDestroy() {
        super.onDestroy()
        scheduleNextAlarm() 
        sixSignalMonitor.stopMonitoring()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
        isRunning = false
        isServiceRunning = false
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
