package com.example.hotel.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log

class DozeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HotelSecurity:DozeAlarmReceiver")
        wl.acquire(30_000) 
        
        try {
            val serviceIntent = Intent(context, WiFiMonitoringService::class.java).apply { 
                action = "ALARM_CHECK" 
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            Log.d("DozeAlarm", "✅ God Mode: Woke up from Doze - checking WiFi")
        } catch (e: Exception) {
            Log.e("DozeAlarm", "Failed to boot service from Receiver", e)
        } finally {
            wl.release()
        }
    }
}
