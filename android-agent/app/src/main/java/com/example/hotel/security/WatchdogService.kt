package com.example.hotel.security

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

// ← FIXED: NEW FILE. Ensures monitoring NEVER stays dead.
class WatchdogService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var isWatchdogRunning = false

    companion object {
        private const val TAG = "WatchdogService"
        private const val CHECK_INTERVAL_MS = 30_000L
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "watchdog_channel"
    }

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            // ← FIXED: Check if monitoring service is alive
            if (!WiFiMonitoringService.isRunning) {
                Log.e(TAG, "Watchdog detected WiFiMonitoringService is DEAD. Restarting immediately.")
                restartMonitoringService()
            } else {
                Log.d(TAG, "Watchdog check: WiFiMonitoringService is alive and well.")
            }
            // ← FIXED: Re-queue next check
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isWatchdogRunning) {
            // ← FIXED: Persistent notification required for Foreground Service
            startForeground(NOTIFICATION_ID, createNotification())
            isWatchdogRunning = true
            handler.post(watchdogRunnable)
            Log.i(TAG, "WatchdogService started.")
        }
        // ← FIXED: START_STICKY ensures Watchdog itself restarts if killed
        return START_STICKY
    }

    private fun restartMonitoringService() {
        val serviceIntent = Intent(this, WiFiMonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Watchdog Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ensures security services remain active"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security Watchdog")
            .setContentText("Ensuring maximum security protection.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isWatchdogRunning = false
        handler.removeCallbacks(watchdogRunnable)
        Log.e(TAG, "WatchdogService destroyed!")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
