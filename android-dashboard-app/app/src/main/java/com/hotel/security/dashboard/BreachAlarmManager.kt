package com.hotel.security.dashboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class BreachAlarmManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "breach_alarm"
        const val ACTION_ACKNOWLEDGE = "com.hotel.security.dashboard.ACKNOWLEDGE"
        const val REPEAT_INTERVAL_MS = 30_000L
        const val EXTRA_ALERT_ID = "alertId"
        
        // ← FIXED: Generate unique notification ID per device
        fun getNotificationId(deviceId: String): Int {
            return Math.abs(deviceId.hashCode()) + 2000
        }

        // ← Track active breach count for the group summary
        private var activeBreachCount = 0
        fun getActiveBreachCount(): Int {
            return ++activeBreachCount
        }
        
        @Volatile
        private var ringtone: Ringtone? = null
        private var alarmHandler: Handler? = null
        var isActive = false
    }

    fun startBreachAlarm(alertId: String, deviceId: String, roomId: String, message: String) {
        // Do not stop the sound loop if it's already active, just let it keep playing
        if (!isActive) {
            isActive = true
            alarmHandler = Handler(Looper.getMainLooper())
            val repeatRunnable = object : Runnable {
                override fun run() {
                    if (!isActive) return
                    playAlarmSound()
                    alarmHandler?.postDelayed(this, REPEAT_INTERVAL_MS)
                }
            }
            playAlarmSound()  // play immediately
            alarmHandler?.postDelayed(repeatRunnable, REPEAT_INTERVAL_MS)
        }

        // Show persistent non-dismissible notification
        showBreachNotification(alertId, deviceId, roomId, message)
    }

    fun stopBreachAlarm(deviceId: String? = null) {
        // Stop the sound loop
        isActive = false
        alarmHandler?.removeCallbacksAndMessages(null)
        alarmHandler = null
        ringtone?.stop()
        ringtone = null
        
        val nm = context.getSystemService(NotificationManager::class.java)
        if (deviceId != null) {
            nm.cancel(getNotificationId(deviceId))
        } else {
            // Fallback if we don't know the deviceId (e.g. legacy cancel)
            // The dashboard app's acknowledge usually handles specific devices.
            nm.cancelAll() // Dangerous but effective if stopping ALL alarms
        }
    }

    private fun playAlarmSound() {
        try {
            ringtone?.stop()
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.play()
        } catch (e: Exception) {
            Log.e("BreachAlarm", "Sound failed: ${e.message}")
        }
    }

    private fun showBreachNotification(
        alertId: String, deviceId: String, roomId: String, message: String
    ) {
        val notificationId = getNotificationId(deviceId)

        // Acknowledge PendingIntent — fires AcknowledgeReceiver
        val ackIntent = Intent(context, AcknowledgeReceiver::class.java).apply {
            action = ACTION_ACKNOWLEDGE
            putExtra(EXTRA_ALERT_ID, alertId)
            putExtra("deviceId", deviceId)
        }
        val ackPendingIntent = PendingIntent.getBroadcast(
            context, notificationId, ackIntent, // ← unique request code
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Tap notification → open app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ALERT_ID, alertId)
            putExtra("deviceId", deviceId)
            putExtra("roomId", roomId)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, notificationId + 10000, openAppIntent, // ← unique request code
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 SECURITY BREACH")
            .setContentText("Room $roomId — $deviceId: $message")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Room $roomId — $deviceId\n$message\nTap Acknowledge to stop alarm"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)           // ← stays until acknowledged
            .setOngoing(true)               // ← cannot be swiped away
            .setSound(null)                 // sound handled by BreachAlarmManager
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .setLights(Color.RED, 500, 500)
            .setContentIntent(openAppPendingIntent)
            .addAction(                     // ← Acknowledge button ON notification
                android.R.drawable.ic_menu_close_clear_cancel,
                "✓ Acknowledge",
                ackPendingIntent
            )
            .setNumber(getActiveBreachCount()) // ← Show count of active breaches
            .setGroup("hotel_breaches")       // ← Group notifications together
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(notificationId, notification)

        // ← Also show group summary notification
        showGroupSummary()
    }

    // ← Add group summary so notifications are grouped in notification shade
    private fun showGroupSummary() {
        val summaryId = 1999
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Hotel Security")
            .setContentText("Multiple breach alerts")
            .setGroup("hotel_breaches")
            .setGroupSummary(true)  // ← Summary!
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
        
        context.getSystemService(NotificationManager::class.java).notify(summaryId, summary)
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Security Breach Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Persistent alarm for hotel security breaches"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                setSound(null, null)        // sound managed manually
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
