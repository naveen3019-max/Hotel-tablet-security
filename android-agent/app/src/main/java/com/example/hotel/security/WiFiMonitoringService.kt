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
    private var wakeLock: PowerManager.WakeLock? = null
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

    override fun onCreate() {
        super.onCreate()
        instance = this
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        createNotificationChannel()
        setManufacturerOptimizations()
    }

    private fun setManufacturerOptimizations() {
        val manufacturer = Build.MANUFACTURER.lowercase()
        when {
            manufacturer.contains("samsung") -> {
                Log.d(TAG, "Samsung optimization bypass active")
                startForeground(NOTIFICATION_ID, buildNotification(NotificationCompat.PRIORITY_MAX))
            }
            manufacturer.contains("lenovo") -> {
                Log.d(TAG, "Lenovo optimization bypass active")
                startForeground(NOTIFICATION_ID, buildNotification(NotificationCompat.PRIORITY_HIGH))
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification(NotificationCompat.PRIORITY_DEFAULT))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "DOZE_ALARM") {
            Log.d(TAG, "DOZE_ALARM fired! Re-acquiring WakeLock and verifying monitoring.")
            acquireWakeLockSafely()
            scheduleDozeAlarm()
            if (!sixSignalMonitor.isMonitoringAlive()) {
                Log.w(TAG, "Monitoring was paused by Doze! Restarting...")
                sixSignalMonitor.startMonitoring()
            }
            return START_STICKY
        }

        if (!isServiceRunning) {
            Log.i(TAG, "Starting WiFiMonitoringService...")
            isRunning = true
            isServiceRunning = true
            
            acquireWakeLockSafely()
            scheduleDozeAlarm()
            sixSignalMonitor.startMonitoring()
        }
        
        return START_STICKY
    }

    private fun scheduleDozeAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, WiFiMonitoringService::class.java).apply {
            action = "DOZE_ALARM"
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 55_000L,
                pendingIntent
            )
        }
    }

    private fun acquireWakeLockSafely() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HotelSecurity:WakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Security Monitoring",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(priority: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security System Active")
            .setContentText("Device is aggressively monitored.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .setPriority(priority)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        sixSignalMonitor.stopMonitoring()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        isRunning = false
        isServiceRunning = false
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
