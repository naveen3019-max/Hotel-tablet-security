package com.hotel.security.dashboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

class AcknowledgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BreachAlarmManager.ACTION_ACKNOWLEDGE) return

        val alertId = intent.getStringExtra(BreachAlarmManager.EXTRA_ALERT_ID) ?: return
        val deviceId = intent.getStringExtra("deviceId") ?: ""

        // Stop the alarm immediately
        BreachAlarmManager(context).stopBreachAlarm()

        // Tell backend this alert is acknowledged
        Thread {
            try {
                val token = context.getSharedPreferences("hotel_dashboard_prefs", Context.MODE_PRIVATE)
                    .getString("auth_token", null) ?: return@Thread
                val url = URL("https://hotel-tablet-security.onrender.com/api/alerts/$alertId/acknowledge")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $token")
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
                val code = conn.responseCode
                Log.d("AckReceiver", "Acknowledged alert $alertId → HTTP $code")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("AckReceiver", "Ack failed: ${e.message}")
            }
        }.apply { isDaemon = true; start() }

        // Broadcast to MainActivity to update UI if app is open
        context.sendBroadcast(Intent("com.hotel.security.dashboard.ALERT_ACKNOWLEDGED").apply {
            putExtra("alertId", alertId)
            putExtra("deviceId", deviceId)
        })
    }
}
