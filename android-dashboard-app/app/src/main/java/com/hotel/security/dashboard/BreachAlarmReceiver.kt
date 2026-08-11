package com.hotel.security.dashboard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

class BreachAlarmReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("AlarmReceiver", "⏰ Breach check alarm fired")
        
        // ← Start polling service
        val serviceIntent = Intent(context, BreachPollingService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Start service: $e")
        }
        
        // ← Schedule NEXT alarm
        scheduleNextAlarm(context)
    }
    
    companion object {
        fun scheduleNextAlarm(context: Context) {
            val intent = Intent(context, BreachAlarmReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                100,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            // ← Every 2 minutes
            val triggerAt = SystemClock.elapsedRealtime() + 2 * 60 * 1000L
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt, pi
                    )
                } else {
                    am.setRepeating(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        2 * 60 * 1000L,
                        pi
                    )
                }
                Log.d("AlarmReceiver", "Next alarm in 2 min")
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Schedule alarm: $e")
            }
        }
    }
}
