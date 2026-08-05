package com.hotel.security.dashboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AcknowledgeReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val alertId = intent.getStringExtra("alertId") ?: ""
        val deviceId = intent.getStringExtra("deviceId") ?: ""
        val roomId = intent.getStringExtra("roomId") ?: ""
        val notificationId = intent.getIntExtra("notificationId", 0)
        
        Log.i("AckReceiver", "✅ ACK tapped: alertId=$alertId deviceId=$deviceId")
        
        // ← NEW/FIXED: Step 1: Add to local ack set
        // This prevents re-notification BEFORE network call succeeds
        if (alertId.isNotEmpty()) {
            BreachPollingService.acknowledgedAlertIds.add(alertId)
        }
        
        // ← NEW/FIXED: Step 2: Dismiss notification
        NotificationManagerCompat.from(context).cancel(notificationId)
        
        // ← NEW/FIXED: Step 3: Stop alarm sound
        context.startService(
            Intent(context, AlarmSoundService::class.java).apply {
                action = "STOP_ALARM"
            }
        )
        
        // ← NEW/FIXED: Step 4: Send ACK to backend
        // Do this in background thread
        Thread {
            sendAcknowledge(context, alertId, deviceId)
        }.start()
        
        Log.i("AckReceiver", "✅ Alert acknowledged: $deviceId")
    }
    
    private fun sendAcknowledge(context: Context, alertId: String, deviceId: String) {
        try {
            val prefs = context.getSharedPreferences("hotel_dashboard_prefs", Context.MODE_PRIVATE)
            val token = prefs.getString("auth_token", "") ?: ""
            
            if (token.isEmpty()) {
                Log.w("AckReceiver", "No token — cannot ACK")
                return
            }
            
            val backendUrl = "https://hotel-tablet-security.onrender.com"
            
            // ← NEW/FIXED: Try acknowledge by alertId
            if (alertId.isNotEmpty()) {
                val success = postAcknowledge(backendUrl, token, alertId)
                if (success) {
                    Log.i("AckReceiver", "✅ ACK sent to backend")
                    return
                }
            }
            
            // ← NEW/FIXED: Fallback: acknowledge by deviceId
            val success = postAcknowledgeByDevice(backendUrl, token, deviceId)
            if (success) {
                Log.i("AckReceiver", "✅ ACK sent by device")
            }
            
        } catch (e: Exception) {
            Log.e("AckReceiver", "ACK failed: ${e.message}")
        }
    }
    
    private fun postAcknowledge(backendUrl: String, token: String, alertId: String): Boolean {
        return try {
            val url = URL("$backendUrl/api/alerts/acknowledge/$alertId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            
            // Empty body
            OutputStreamWriter(conn.outputStream).use {
                it.write("{}")
                it.flush()
            }
            
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e("AckReceiver", "POST ack failed: $e")
            false
        }
    }
    
    private fun postAcknowledgeByDevice(backendUrl: String, token: String, deviceId: String): Boolean {
        return try {
            val url = URL("$backendUrl/api/alerts/acknowledge-device/$deviceId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            
            OutputStreamWriter(conn.outputStream).use {
                it.write("{}")
                it.flush()
            }
            
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e("AckReceiver", "POST device ack failed: $e")
            false
        }
    }
}
