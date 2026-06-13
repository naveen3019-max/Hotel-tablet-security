package com.example.hotel.security

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log

class WatchdogService : Service() {

    companion object {
        private const val TAG = "WatchdogService"
        private const val WATCHDOG_INTERVAL_MS = 10_000L 
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastWatchdogRunTime = 0L

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            try {
                lastWatchdogRunTime = SystemClock.elapsedRealtime()
                checkAndRestartMonitoringService()
            } finally {
                handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        scheduleWatchdogAlarm()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "WATCHDOG_ALARM") { 
            scheduleWatchdogAlarm()
        }
        handler.removeCallbacks(watchdogRunnable)
        handler.post(watchdogRunnable)
        return START_STICKY
    }

    private fun checkAndRestartMonitoringService() {
        if (!WiFiMonitoringService.isRunning || WiFiMonitoringService.instance?.sixSignalMonitor?.isMonitoringAlive() != true) {
            Log.e(TAG, "God Mode: Primary monitor dead or frozen! Executing full restart sequence.")
            restartEverything()
        }
    }

    private fun restartEverything() {
        stopService(Intent(this, WiFiMonitoringService::class.java))
        Thread.sleep(500) 
        
        val restartIntent = Intent(this, WiFiMonitoringService::class.java)
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        if (manufacturer.contains("samsung")) restartIntent.setPackage(packageName)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
        
        if (manufacturer.contains("lenovo")) {
            sendBroadcast(Intent("com.example.hotel.security.RESTART_MONITORING")) 
        }
    }

    private fun scheduleWatchdogAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(this, WatchdogService::class.java).apply { action = "WATCHDOG_ALARM" }
        val pendingIntent = PendingIntent.getService(this, 2, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle( 
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 90_000L,
                pendingIntent
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(watchdogRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
