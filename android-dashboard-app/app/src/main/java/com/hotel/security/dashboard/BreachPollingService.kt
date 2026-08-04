package com.hotel.security.dashboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class BreachPollingService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private val POLL_INTERVAL = 30_000L // 30s
    private val BACKEND_URL = "https://hotel-tablet-security.onrender.com"
    private val CHANNEL_ID = "breach_alerts"
    
    // ← FIXED: track which devices already have notifications
    private val notifiedDevices = mutableSetOf<String>()
    private val lastNotifiedAlertId = mutableMapOf<String, String>()

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            checkForNewBreaches()
            handler.postDelayed(this, POLL_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        BreachAlarmManager(this).createNotificationChannel() // ← ADDED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        
        // Start as foreground service with a subtle persistent notification
        val notification = NotificationCompat.Builder(this, "service_channel")
            .setContentTitle("Hotel Security")
            .setContentText("Monitoring for alerts...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
        
        startForeground(1999, notification)
        
        // Start polling
        handler.post(pollRunnable)
        Log.i("PollingService", "✅ Breach polling started")
        
        return START_STICKY
    }

    private fun checkForNewBreaches() {
        Thread {
            try {
                // Get JWT token from SharedPreferences
                val prefs = getSharedPreferences("hotel_dashboard_prefs", Context.MODE_PRIVATE)
                val token = prefs.getString("auth_token", "") ?: ""
                
                if (token.isEmpty()) {
                    Log.d("PollingService", "No token — user not logged in")
                    return@Thread
                }
                
                // Poll recent alerts
                val url = URL("$BACKEND_URL/api/alerts/recent?limit=5")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                
                val code = conn.responseCode
                if (code != 200) {
                    conn.disconnect()
                    return@Thread
                }
                
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                
                // Parse response
                processAlerts(response)
                
            } catch (e: Exception) {
                Log.e("PollingService", "Poll error: ${e.message}")
            }
        }.start()
    }

    private fun processAlerts(jsonResponse: String) {
        try {
            val jsonArray = JSONArray(jsonResponse)
            if (jsonArray.length() == 0) return
            
            // ← Process ALL alerts not just first
            // Show notification for each device that has a new unacknowledged breach
            val newBreaches = mutableListOf<Triple<String, String, String>>()
            val alertIds = mutableListOf<String>()
            
            for (i in 0 until jsonArray.length()) {
                val alert = jsonArray.getJSONObject(i)
                
                val alertId = alert.optString("_id", alert.optString("id", ""))
                val alertType = alert.optString("type", "")
                val deviceId = alert.optString("deviceId", "Unknown")
                val roomId = alert.optString("roomId", "Unknown")
                val message = alert.optString("message", "Security breach detected")
                val acknowledged = alert.optBoolean("acknowledged", false)
                
                // ← Only breach alerts
                if (alertType != "breach") continue
                // ← Only unacknowledged
                if (acknowledged) continue
                // ← Only new alerts for this device
                if (alertId == lastNotifiedAlertId[deviceId]) {
                    continue
                }
                
                newBreaches.add(Triple(deviceId, roomId, message))
                alertIds.add(alertId)
                lastNotifiedAlertId[deviceId] = alertId
            }
            
            // ← Show notification for each new breach device
            handler.post {
                for (i in newBreaches.indices) {
                    val breach = newBreaches[i]
                    val alertId = alertIds[i]
                    BreachAlarmManager(applicationContext).startBreachAlarm(
                        alertId, breach.first, breach.second, breach.third
                    )
                }
            }
            
            if (newBreaches.isNotEmpty()) {
                Log.i("PollingService", "🚨 ${newBreaches.size} new breach notifications shown")
            }
            
        } catch (e: Exception) {
            Log.e("PollingService", "Parse error: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            
            // Breach alert channel - HIGH priority
            val breachChannel = NotificationChannel(
                CHANNEL_ID,
                "Breach Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Hotel security breach alerts"
                enableVibration(true)
                enableLights(true)
                lightColor = Color.RED
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                )
            }
            
            // Service channel - silent
            val serviceChannel = NotificationChannel(
                "service_channel",
                "Security Monitor",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(breachChannel)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun stopService(name: Intent?): Boolean {
        isRunning = false
        handler.removeCallbacks(pollRunnable)
        return super.stopService(name)
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(pollRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
