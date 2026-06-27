package com.hotel.security.dashboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            
            // Check if user was logged in
            val prefs = context.getSharedPreferences(
                "hotel_dashboard_prefs",
                Context.MODE_PRIVATE
            )
            val token = prefs.getString("auth_token", "") ?: ""
            
            if (token.isNotEmpty()) {
                // User was logged in -> Restart polling service
                val serviceIntent = Intent(context, BreachPollingService::class.java)
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.i("BootReceiver", "✅ Polling service restarted")
            }
        }
    }
}
