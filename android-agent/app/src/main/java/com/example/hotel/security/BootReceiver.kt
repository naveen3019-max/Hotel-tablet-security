package com.example.hotel.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i("BootReceiver", "God Mode: Boot intercept: $action")

        Handler(Looper.getMainLooper()).postDelayed({
            val serviceIntent = Intent(context, WiFiMonitoringService::class.java)
            val watchdogIntent = Intent(context, WatchdogService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
                context.startForegroundService(watchdogIntent)
            } else {
                context.startService(serviceIntent)
                context.startService(watchdogIntent)
            }
            Log.i("BootReceiver", "God Mode: All systems online post-boot")
        }, 3000)
    }
}
