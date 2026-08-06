package com.hotel.security.dashboard

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.webkit.JavascriptInterface

class TokenBridge(
    private val context: Context
) {
    companion object {
        const val PREFS_NAME = "hotel_dashboard_prefs"
        const val KEY_TOKEN = "auth_token"
        const val KEY_HOTEL_ID = "hotel_id"
        const val KEY_USERNAME = "username"
    }
    
    // Called from JavaScript via WebView bridge
    @JavascriptInterface
    fun saveToken(
        token: String,
        hotelId: String,
        username: String
    ) {
        context.getSharedPreferences(
            PREFS_NAME, Context.MODE_PRIVATE
        ).edit().apply {
            putString(KEY_TOKEN, token)
            putString(KEY_HOTEL_ID, hotelId)
            putString(KEY_USERNAME, username)
            apply()
        }
        Log.i("TokenBridge", "✅ Token saved for $username")
        
        // Start polling service now that we have a valid token
        val serviceIntent = Intent(context, BreachPollingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
    
    @JavascriptInterface
    fun clearToken() {
        context.getSharedPreferences(
            PREFS_NAME, Context.MODE_PRIVATE
        ).edit().clear().apply()
        Log.i("TokenBridge", "Token cleared")
        
        // Stop polling service on logout
        context.stopService(Intent(context, BreachPollingService::class.java))
    }
    
    @JavascriptInterface
    fun getHotelId(): String {
        return context.getSharedPreferences(
            PREFS_NAME, Context.MODE_PRIVATE
        ).getString(KEY_HOTEL_ID, "") ?: ""
    }

    @JavascriptInterface
    fun acknowledgeAlert(alertId: String, deviceId: String) {
        Log.i("TokenBridge", "✅ Acknowledge alert from WebView: $alertId, device: $deviceId")
        
        // Calculate notificationId the same way BreachPollingService does
        val notificationId = Math.abs(deviceId.hashCode()) + 2000
        
        BreachPollingService.acknowledgeLocally(alertId, deviceId, notificationId, context)
    }

    @JavascriptInterface
    fun acknowledgeAllAlerts() {
        Log.i("TokenBridge", "✅ Acknowledge all alerts from WebView")
        try {
            androidx.core.app.NotificationManagerCompat.from(context).cancelAll()
            context.stopService(Intent(context, AlarmSoundService::class.java))
        } catch (e: Exception) {
            Log.e("TokenBridge", "Error clearing all notifications/alarms: $e")
        }
    }
}
