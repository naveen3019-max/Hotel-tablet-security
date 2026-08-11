package com.example.hotel.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

class OfflineQueueFlushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("OfflineQueue", "Alarm trigger flush timestamp: ${System.currentTimeMillis()}")
        OfflineQueueManager.getInstance(context).triggerImmediateFlush()
        scheduleNext(context)
    }

    companion object {
        fun scheduleNext(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, OfflineQueueFlushReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                200,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = SystemClock.elapsedRealtime() + 30_000L // 30 seconds

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }
            } catch (e: Exception) {
                Log.e("OfflineQueue", "Failed to schedule flush alarm: ${e.message}")
            }
        }
    }
}
