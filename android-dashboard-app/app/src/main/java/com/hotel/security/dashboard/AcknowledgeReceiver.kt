package com.hotel.security.dashboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AcknowledgeReceiver : 
    BroadcastReceiver() {
    
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (intent.action != "ACK_BREACH") 
            return
        
        val alertId = intent.getStringExtra(
            "alertId") ?: ""
        val deviceId = intent.getStringExtra(
            "deviceId") ?: ""
        val notificationId = 
            intent.getIntExtra(
                "notificationId", 0)
        
        Log.i("AckReceiver",
            "ACK received: device=$deviceId " +
            "alert=$alertId")
        
        // ← IMMEDIATELY dismiss notification
        try {
            NotificationManagerCompat
                .from(context)
                .cancel(notificationId)
            Log.i("AckReceiver",
                "✅ Notification cancelled: " +
                "$notificationId")
        } catch (e: Exception) {
            Log.e("AckReceiver",
                "Cancel notif: $e")
        }
        
        // ← IMMEDIATELY stop alarm
        try {
            context.stopService(
                Intent(context,
                    AlarmSoundService::class.java)
            )
            Log.i("AckReceiver",
                "✅ Alarm stopped")
        } catch (e: Exception) {
            Log.e("AckReceiver",
                "Stop alarm: $e")
        }
        
        // ← Save to SharedPreferences
        // BEFORE network call
        // Prevents re-show on poll
        BreachPollingService
            .acknowledgeLocally(
            alertId,
            deviceId,
            notificationId,
            context
        )
        
        // ← Send to backend async
        Thread {
            sendAckToBackend(
                context, alertId, deviceId)
        }.start()
    }
    
    private fun sendAckToBackend(
        context: Context,
        alertId: String,
        deviceId: String
    ) {
        val prefs = context
            .getSharedPreferences(
            "hotel_dashboard_prefs",
            Context.MODE_PRIVATE)
        val token = prefs.getString(
            "auth_token", "") ?: ""
        
        if (token.isEmpty()) {
            Log.w("AckReceiver", "No token")
            return
        }
        
        val baseUrl = 
            "https://hotel-tablet-security" +
            ".onrender.com"
        
        // ← Try acknowledge by alertId
        if (alertId.isNotEmpty()) {
            if (postRequest(
                "$baseUrl/api/alerts" +
                "/acknowledge/$alertId",
                token)) {
                Log.i("AckReceiver",
                    "✅ Backend acked: $alertId")
                return
            }
        }
        
        // ← Fallback: by device
        if (postRequest(
            "$baseUrl/api/alerts" +
            "/acknowledge-device/$deviceId",
            token)) {
            Log.i("AckReceiver",
                "✅ Backend acked device: " +
                "$deviceId")
        }
    }
    
    private fun postRequest(
        urlStr: String,
        token: String
    ): Boolean {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection()
                as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty(
                "Authorization", "Bearer $token")
            conn.setRequestProperty(
                "Content-Type",
                "application/json")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            OutputStreamWriter(
                conn.outputStream
            ).use {
                it.write("{}")
                it.flush()
            }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e("AckReceiver",
                "POST failed: $e")
            false
        }
    }
}
