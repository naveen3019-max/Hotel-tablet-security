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
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
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
    
    private val notifiedDevices = mutableSetOf<String>()
    private val lastNotifiedAlertId = mutableMapOf<String, String>()

    private val wakeLock by lazy {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HotelSecurity::PollWakeLock"
        )
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            
            // ← Acquire WakeLock during poll
            // Prevents CPU sleep mid-request
            try {
                if (!wakeLock.isHeld) {
                    wakeLock.acquire(15_000L)
                }
            } catch (e: Exception) {
                Log.w("PollingService", "WakeLock acquire failed: $e")
            }
            
            checkForNewBreaches()
            
            handler.postDelayed(this, POLL_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        
        // ← FIXED: Foreground service
        // Android cannot kill foreground services
        // (only suspends them briefly)
        startForegroundWithNotification()
        
        // ← Start polling
        handler.post(pollRunnable)
        
        Log.i("PollingService", "✅ Service started")
        
        // ← START_STICKY: Android restarts
        // service automatically if killed
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        createNotificationChannels()
        
        // ← Silent persistent notification
        // Required for foreground service
        val notification = NotificationCompat.Builder(this, "service_channel")
            .setContentTitle("Hotel Security")
            .setContentText("Monitoring for security alerts...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()
        
        startForeground(1998, notification)
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
            } finally {
                try {
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                    }
                } catch (e: Exception) {}
            }
        }.start()
    }

    private fun processAlerts(jsonResponse: String) {
        try {
            val jsonArray = JSONArray(jsonResponse)
            if (jsonArray.length() == 0) return
            
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
                
                if (alertType != "breach") continue
                if (acknowledged) continue
                if (alertId == lastNotifiedAlertId[deviceId]) {
                    continue
                }
                
                newBreaches.add(Triple(deviceId, roomId, message))
                alertIds.add(alertId)
                lastNotifiedAlertId[deviceId] = alertId
            }
            
            handler.post {
                for (i in newBreaches.indices) {
                    val breach = newBreaches[i]
                    val alertId = alertIds[i]
                    showBreachNotification(breach.first, breach.second, breach.third)
                }
            }
            
            if (newBreaches.isNotEmpty()) {
                Log.i("PollingService", "🚨 ${newBreaches.size} new breach notifications shown")
            }
            
        } catch (e: Exception) {
            Log.e("PollingService", "Parse error: ${e.message}")
        }
    }

    private fun wakeScreen() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            
            // ← Screen wake lock
            // Wakes screen for 10 seconds
            val screenWakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                or PowerManager.ACQUIRE_CAUSES_WAKEUP
                or PowerManager.ON_AFTER_RELEASE,
                "HotelSecurity::ScreenWake"
            )
            screenWakeLock.acquire(10_000L)
            
            // ← Release after 10 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    if (screenWakeLock.isHeld) {
                        screenWakeLock.release()
                    }
                } catch (e: Exception) {}
            }, 10_000L)
            
            Log.i("PollingService", "📱 Screen woken for breach alert")
                
        } catch (e: Exception) {
            Log.w("PollingService", "Screen wake failed: ${e.message}")
        }
    }

    private fun getAlertSound(): Uri {
        // ← Try notification sound
        val notifSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (notifSound != null) return notifSound
        
        // ← Fall back to ringtone
        val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        if (ringtone != null) return ringtone
        
        // ← Last resort
        return Settings.System.DEFAULT_RINGTONE_URI
    }

    private fun isXiaomi(): Boolean {
        return Build.MANUFACTURER.lowercase().contains("xiaomi") ||
            Build.MANUFACTURER.lowercase().contains("redmi")
    }

    private fun showBreachNotification(deviceId: String, roomId: String, message: String) {
        // ← Wake screen first!
        wakeScreen()
        
        val title = "🚨 BREACH - Room $roomId"
        val body = "$deviceId: $message"
        
        // ← Step 2: Unique ID per device
        val notificationId = Math.abs(deviceId.hashCode()) + 2000
        
        // ← Step 3: Intent to open app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("deviceId", deviceId)
            putExtra("roomId", roomId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // ← Step 4: Build notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$body\n\nTap to view dashboard")
                    .setBigContentTitle(title)
            )
            // ← MAX priority
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(
                // ← Use ALARM category for maximum interruption
                NotificationCompat.CATEGORY_ALARM
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            // ← Strong vibration pattern
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500, 200, 500))
            // ← Notification sound (not alarm)
            .setSound(getAlertSound())
            .setColor(Color.RED)
            .setColorized(true)
            // ← Full screen = shows over lockscreen
            .setFullScreenIntent(pendingIntent, true)
            // ← Show on lockscreen
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLights(Color.RED, 300, 300)
            // ← Keep showing until dismissed
            .setOngoing(false)
            // ← Group all breach notifications
            .setGroup("hotel_breaches")
            // ← Show time of breach
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .build()
        
        // ← Step 5: Show notification
        try {
            // ← Xiaomi specific fix
            if (isXiaomi()) {
                // Xiaomi needs explicit channel ID and notification manager restart
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(notificationId)
                Thread.sleep(100)
                nm.notify(notificationId, notification)
            } else {
                NotificationManagerCompat.from(this).notify(notificationId, notification)
            }
            Log.i("PollingService", "✅ Breach notification shown: $title (ID=$notificationId)")
        } catch (e: Exception) {
            Log.e("PollingService", "Notification failed: $e")
        }
        
        // ← Step 6: Show group summary
        showGroupSummary()
    }

    private fun showGroupSummary() {
        val summaryNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security Breaches")
            .setContentText("Multiple security breaches detected")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setStyle(NotificationCompat.InboxStyle().setSummaryText("Multiple breaches"))
            .setGroup("hotel_breaches")
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
            
        try {
            NotificationManagerCompat.from(this).notify(1997, summaryNotification)
        } catch (e: Exception) {
            Log.e("PollingService", "Summary notification failed: $e")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            
            // ← Breach alert channel
            val breachChannel = NotificationChannel(
                CHANNEL_ID,
                "🚨 Security Breach Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical hotel security alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500, 200, 500)
                enableLights(true)
                lightColor = Color.RED
                setShowBadge(true)
                setBypassDnd(true) // ← Bypass DND!
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                
                // ← Use notification sound NOT alarm sound
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(
                        // ← NOTIFICATION not ALARM
                        AudioAttributes.USAGE_NOTIFICATION_EVENT
                    )
                    .build()
                
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    audioAttributes
                )
            }
            
            // ← Silent service channel
            val serviceChannel = NotificationChannel(
                "service_channel",
                "Security Monitor",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                description = "Background monitoring service"
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(breachChannel)
            manager.createNotificationChannel(serviceChannel)
            
            Log.i("PollingService", "✅ Notification channels created")
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
