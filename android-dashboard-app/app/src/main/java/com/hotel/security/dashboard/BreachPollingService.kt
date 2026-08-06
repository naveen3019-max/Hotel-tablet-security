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

    private val handler = Handler(
        Looper.getMainLooper())
    private val POLL_INTERVAL = 30_000L
    private val CHANNEL_ID = "breach_alerts"
    private val SERVICE_CHANNEL = 
        "service_channel"
    private var isRunning = false
    private val BACKEND_URL =
        "https://hotel-tablet-security" +
        ".onrender.com"
    
    // ← Persistent acknowledged IDs
    // Saved to SharedPrefs to survive restart
    private val acknowledgedPrefsKey = 
        "acknowledged_alert_ids"
    
    private fun getAcknowledgedIds(): 
        MutableSet<String> {
        return getSharedPreferences(
            "hotel_dashboard_prefs",
            Context.MODE_PRIVATE
        ).getStringSet(
            acknowledgedPrefsKey,
            mutableSetOf()
        )?.toMutableSet() ?: mutableSetOf()
    }
    
    private fun saveAcknowledgedId(
        alertId: String
    ) {
        if (alertId.isEmpty()) return
        val prefs = getSharedPreferences(
            "hotel_dashboard_prefs",
            Context.MODE_PRIVATE)
        val current = prefs.getStringSet(
            acknowledgedPrefsKey,
            mutableSetOf()
        )?.toMutableSet() ?: mutableSetOf()
        current.add(alertId)
        // ← Keep only last 100 IDs
        // to prevent unlimited growth
        val trimmed = if (current.size > 100) {
            current.toList().takeLast(100).toMutableSet()
        } else current
        prefs.edit()
            .putStringSet(
                acknowledgedPrefsKey, trimmed)
            .apply()
        Log.d(TAG,
            "Saved ACK ID: $alertId " +
            "(total: ${trimmed.size})")
    }
    
    // ← Last shown alert per device
    private val lastShownAlertId = 
        mutableMapOf<String, String>()
    
    companion object {
        private const val TAG = 
            "BreachPollingService"
        
        // ← Static reference for ACK receiver
        var instance: BreachPollingService? = null
        
        fun acknowledgeLocally(
            alertId: String,
            deviceId: String,
            notificationId: Int,
            context: Context
        ) {
            // ← Dismiss notification
            try {
                NotificationManagerCompat
                    .from(context)
                    .cancel(notificationId)
                Log.i(TAG,
                    "Notification dismissed: " +
                    "$notificationId")
            } catch (e: Exception) {
                Log.e(TAG, "Dismiss error: $e")
            }
            
            // ← Stop alarm
            try {
                context.stopService(
                    Intent(context,
                        AlarmSoundService
                            ::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Stop alarm: $e")
            }
            
            // ← Save to SharedPrefs
            // so ACK survives service restart
            if (alertId.isNotEmpty()) {
                val prefs = context
                    .getSharedPreferences(
                    "hotel_dashboard_prefs",
                    Context.MODE_PRIVATE)
                val current = prefs.getStringSet(
                    "acknowledged_alert_ids",
                    mutableSetOf()
                )?.toMutableSet() 
                    ?: mutableSetOf()
                current.add(alertId)
                prefs.edit()
                    .putStringSet(
                        "acknowledged_alert_ids",
                        current)
                    .apply()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        // ← MUST call startForeground
        // within 5 seconds on Android 8+
        // Do it IMMEDIATELY in onStartCommand
        startForeground(
            1998,
            buildServiceNotification()
        )
        
        if (!isRunning) {
            isRunning = true
            handler.post(pollRunnable)
            Log.i(TAG, "✅ Polling started")
        }
        
        return START_STICKY
    }

    private fun buildServiceNotification():
        android.app.Notification {
        return NotificationCompat
            .Builder(this, SERVICE_CHANNEL)
            .setContentTitle("Hotel Security")
            .setContentText(
                "Monitoring security alerts...")
            .setSmallIcon(
                android.R.drawable
                    .ic_lock_idle_lock)
            .setPriority(
                NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()
    }

    private val pollRunnable = 
        object : Runnable {
        override fun run() {
            if (!isRunning) return
            Thread {
                checkForNewBreaches()
            }.start()
            handler.postDelayed(
                this, POLL_INTERVAL)
        }
    }

    private fun checkForNewBreaches() {
        try {
            val prefs = getSharedPreferences(
                "hotel_dashboard_prefs",
                Context.MODE_PRIVATE)
            val token = prefs.getString(
                "auth_token", "") ?: ""
            
            if (token.isEmpty()) return
            
            val url = URL(
                "$BACKEND_URL/api/alerts" +
                "/recent?limit=10")
            val conn = url.openConnection()
                as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty(
                "Authorization",
                "Bearer $token")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            
            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return
            }
            
            val response = conn.inputStream
                .bufferedReader()
                .readText()
            conn.disconnect()
            
            processAlerts(response)
            
        } catch (e: Exception) {
            Log.e(TAG,
                "Poll error: ${e.message}")
        }
    }

    private fun processAlerts(
        jsonResponse: String
    ) {
        try {
            val jsonArray = 
                JSONArray(jsonResponse)
            if (jsonArray.length() == 0) return
            
            // ← Get persistent acknowledged IDs
            val ackedIds = getAcknowledgedIds()
            
            val newBreaches = 
                mutableListOf<BreachAlert>()
            
            for (i in 0 until 
                jsonArray.length()) {
                val alert = jsonArray
                    .getJSONObject(i)
                
                val alertId = alert
                    .optString("id", "")
                val type = alert
                    .optString("type", "")
                val deviceId = alert
                    .optString("deviceId", "")
                val roomId = alert
                    .optString("roomId", "")
                val message = alert.optString(
                    "message", "Breach detected")
                val acknowledged = alert
                    .optBoolean(
                        "acknowledged", false)
                
                if (type != "breach") continue
                
                // ← Skip backend acknowledged
                if (acknowledged) {
                    // ← Dismiss any notification
                    // for this device
                    val nId = Math.abs(
                        deviceId.hashCode()
                    ) + 2000
                    handler.post {
                        NotificationManagerCompat
                            .from(this)
                            .cancel(nId)
                    }
                    continue
                }
                
                // ← Skip locally acknowledged
                if (alertId.isNotEmpty() &&
                    ackedIds.contains(alertId)) {
                    Log.d(TAG,
                        "Skip locally acked: " +
                        alertId)
                    continue
                }
                
                // ← Skip already shown
                if (alertId.isNotEmpty() &&
                    lastShownAlertId[deviceId]
                    == alertId) {
                    continue
                }
                
                newBreaches.add(BreachAlert(
                    alertId, deviceId,
                    roomId, message))
                
                if (alertId.isNotEmpty()) {
                    lastShownAlertId[deviceId] =
                        alertId
                }
            }
            
            if (newBreaches.isEmpty()) return
            
            // ← Build and show notifications
            // on main thread
            handler.post {
                for (breach in newBreaches) {
                    showBreachNotification(breach)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG,
                "processAlerts: ${e.message}")
        }
    }

    private fun showBreachNotification(
        breach: BreachAlert
    ) {
        val notificationId = Math.abs(
            breach.deviceId.hashCode()) + 2000
        val title =
            "🚨 BREACH - Room ${breach.roomId}"
        val body =
            "${breach.deviceId}: ${breach.message}"
        
        // ← Build ACK intent
        // Using explicit class reference
        val ackIntent = Intent(
            this,
            AcknowledgeReceiver::class.java
        ).apply {
            action = "ACK_BREACH"
            putExtra("alertId", breach.alertId)
            putExtra("deviceId", breach.deviceId)
            putExtra("notificationId",
                notificationId)
        }
        val ackPI = PendingIntent.getBroadcast(
            this,
            notificationId + 3000,
            ackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // ← Build open intent
        val openIntent = Intent(
            this,
            MainActivity::class.java
        ).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("deviceId", breach.deviceId)
        }
        val openPI = PendingIntent.getActivity(
            this,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // ← Build notification
        val notification = NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(
                android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body)
            )
            .setPriority(
                NotificationCompat.PRIORITY_MAX)
            .setCategory(
                NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSound(null)
            .setVibrate(null)
            .setColor(Color.RED)
            .setColorized(true)
            .setVisibility(
                NotificationCompat
                    .VISIBILITY_PUBLIC)
            .setFullScreenIntent(openPI, true)
            .setContentIntent(openPI)
            .addAction(
                android.R.drawable.ic_menu_send,
                "✅ ACKNOWLEDGE",
                ackPI
            )
            .addAction(
                android.R.drawable.ic_menu_view,
                "📱 DASHBOARD",
                openPI
            )
            .setGroup("hotel_breaches")
            .setWhen(System.currentTimeMillis())
            .build()
        
        // ← SHOW NOTIFICATION FIRST
        try {
            NotificationManagerCompat
                .from(this)
                .notify(notificationId,
                    notification)
            Log.i(TAG,
                "✅ Notification shown: $title")
        } catch (e: Exception) {
            Log.e(TAG, "Notify error: $e")
        }
        
        // ← Wake screen AFTER notification
        wakeScreen()
        
        // ← Start alarm AFTER notification
        // Small delay ensures notification
        // appears first
        handler.postDelayed({
            startAlarmSound()
        }, 100L)
    }

    private fun wakeScreen() {
        try {
            val pm = getSystemService(
                Context.POWER_SERVICE
            ) as PowerManager
            val wl = pm.newWakeLock(
                PowerManager
                    .SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager
                    .ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "HotelSecurity::ScreenWake"
            )
            wl.acquire(10_000L)
            Handler(Looper.getMainLooper())
                .postDelayed({
                if (wl.isHeld) wl.release()
            }, 10_000L)
        } catch (e: Exception) {
            Log.w(TAG, "Screen wake: $e")
        }
    }

    private fun startAlarmSound() {
        try {
            val intent = Intent(
                this,
                AlarmSoundService::class.java
            )
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Start alarm: $e")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < 
            Build.VERSION_CODES.O) return
        
        val breachChannel = NotificationChannel(
            CHANNEL_ID,
            "Security Breach Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Breach alerts"
            enableVibration(true)
            vibrationPattern = longArrayOf(
                0, 500, 200, 500)
            enableLights(true)
            lightColor = Color.RED
            setShowBadge(true)
            setBypassDnd(true)
            lockscreenVisibility =
                Notification.VISIBILITY_PUBLIC
            setSound(null, null)
        }
        
        val serviceChannel = NotificationChannel(
            SERVICE_CHANNEL,
            "Security Monitor",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        
        val nm = getSystemService(
            NotificationManager::class.java)
        nm.createNotificationChannel(
            breachChannel)
        nm.createNotificationChannel(
            serviceChannel)
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        instance = null
        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}

data class BreachAlert(
    val alertId: String,
    val deviceId: String,
    val roomId: String,
    val message: String
)
