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
        const val NOTIFICATION_ID = 9001
        const val ACTION_ACKNOWLEDGE = "com.hotel.security.dashboard.ACKNOWLEDGE"
        const val REPEAT_INTERVAL_MS = 30_000L
        const val EXTRA_ALERT_ID = "alertId"
        
        @Volatile
        private var ringtone: Ringtone? = null
        private var alarmHandler: Handler? = null
        var isActive = false
    }

    fun startBreachAlarm(alertId: String, deviceId: String, roomId: String, message: String) {
        if (isActive) stopBreachAlarm()  // cancel any previous alarm first
        isActive = true

        // Show persistent non-dismissible notification
        showBreachNotification(alertId, deviceId, roomId, message)

        // Start repeating alarm every 30 seconds
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

    fun stopBreachAlarm() {
        isActive = false
        alarmHandler?.removeCallbacksAndMessages(null)
        alarmHandler = null
        ringtone?.stop()
        ringtone = null
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIFICATION_ID)
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
        // Acknowledge PendingIntent — fires AcknowledgeReceiver
        val ackIntent = Intent(context, AcknowledgeReceiver::class.java).apply {
            action = ACTION_ACKNOWLEDGE
            putExtra(EXTRA_ALERT_ID, alertId)
            putExtra("deviceId", deviceId)
        }
        val ackPendingIntent = PendingIntent.getBroadcast(
            context, 0, ackIntent,
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
            context, 1, openAppIntent,
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
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
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
