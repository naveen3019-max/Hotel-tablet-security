package com.example.hotel.security

import android.content.Context
import android.util.Log

object DeviceIdentity {
    @Volatile
    var deviceId: String? = null
        private set

    fun load(context: Context) {
        deviceId = context
            .getSharedPreferences(
                "hotel_prefs",
                Context.MODE_PRIVATE
            )
            .getString("device_id", null)
            
        // If not found, try agent prefs
        if (deviceId == null || deviceId == "TAB-UNKNOWN") {
            deviceId = context
                .getSharedPreferences(
                    "agent",
                    Context.MODE_PRIVATE
                )
                .getString("device_id", null)
        }
        
        Log.d("DeviceIdentity", "DeviceIdentity available timestamp: ${System.currentTimeMillis()}, ID: $deviceId")
        
        if (deviceId != null && deviceId != "TAB-UNKNOWN") {
            // Trigger an immediate flush of the queue now that deviceId is available
            com.example.hotel.service.OfflineQueueManager.getInstance(context).triggerImmediateFlush()
        }
    }
}
