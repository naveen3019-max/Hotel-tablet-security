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
import android.util.Log

class WatchdogService : Service() {

    companion object {
        private const val TAG = "WatchdogService"
        private const val WATCHDOG_INTERVAL_MS = 20_000L 
    }

    private val handler = Handler(Looper.getMainLooper())
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            checkAndRestartMonitoringService()
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "WatchdogService Created")
        scheduleWatchdogAlarm()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "WatchdogService Started")
        handler.post(watchdogRunnable)
        return START_STICKY
    }

    private fun checkAndRestartMonitoringService() {
        if (!WiFiMonitoringService.isRunning) {
            Log.e(TAG, "Monitoring service died! Restarting immediately...")
            val intent = Intent(this, WiFiMonitoringService::class.java)
            val manufacturer = Build.MANUFACTURER.lowercase()
            
            try {
                if (manufacturer.contains("samsung")) {
                    intent.setPackage(packageName) 
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                
                if (manufacturer.contains("lenovo")) {
                    val broadcastIntent = Intent("com.example.hotel.security.RESTART_MONITORING")
                    sendBroadcast(broadcastIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart WiFiMonitoringService: ${e.message}")
            }
        }
    }

    private fun scheduleWatchdogAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(this, WatchdogService::class.java)
        val pendingIntent = PendingIntent.getService(
            this,
            1,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + WATCHDOG_INTERVAL_MS,
                pendingIntent
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(watchdogRunnable)
        val restartIntent = Intent(this, WatchdogService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
