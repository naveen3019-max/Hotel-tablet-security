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
    private var lastAlertTimestamp = ""
    private var isRunning = false
    private val POLL_INTERVAL = 30_000L // 30s
    private val BACKEND_URL = "https://hotel-tablet-security.onrender.com"
    private val CHANNEL_ID = "breach_alerts"
    private val NOTIFICATION_ID_BASE = 2000

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
            
            // Get the most recent alert
            val latestAlert = jsonArray.getJSONObject(0)
            val alertId = latestAlert.optString("_id", latestAlert.optString("id", ""))
            val alertType = latestAlert.optString("type", "")
            val deviceId = latestAlert.optString("deviceId", "Unknown")
            val roomId = latestAlert.optString("roomId", "Unknown")
            val message = latestAlert.optString("message", "Security alert detected")
            val timestamp = latestAlert.optString("ts", "")
            val acknowledged = latestAlert.optBoolean("acknowledged", false)
            
            // Only notify for breach type alerts that are not acknowledged
            if (alertType != "breach") return
            if (acknowledged) return
            
            // Check if this is a NEW alert (newer than what we last saw)
            if (alertId == lastAlertTimestamp) {
                return // Already notified this alert
            }
            
            // New breach alert found!
            lastAlertTimestamp = alertId
            
            Log.i("PollingService", "🚨 New breach: Device=$deviceId Room=$roomId")
            
            // Show notification on main thread
            handler.post {
                showBreachNotification(deviceId, roomId, message)
            }
            
        } catch (e: Exception) {
            Log.e("PollingService", "Parse error: ${e.message}")
        }
    }

    private fun showBreachNotification(deviceId: String, roomId: String, message: String) {
        val title = "🚨 BREACH - Room $roomId"
        val body = "Device $deviceId: $message"
        
        // Intent to open app when tapped
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("deviceId", deviceId)
            putExtra("roomId", roomId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setColor(Color.RED)
            .setColorized(true)
            .setFullScreenIntent(pendingIntent, true)
            .setLights(Color.RED, 1000, 500)
            .build()
        
        NotificationManagerCompat.from(this).notify(
            NOTIFICATION_ID_BASE + System.currentTimeMillis().toInt() % 1000,
            notification
        )
        
        Log.i("PollingService", "✅ Notification shown: $title")
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
