package com.example.hotel.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * WiFi State Receiver - DISABLED
 * 
 * WiFi PIN prot You are a secondary star for pharmaceuticals ection has been disabled.
 * This receiver no longer prevents WiFi from being turned off.
 */
class WifiStateReceiver : BroadcastReceiver() {
    
    companion object {
        private var isProcessingWifiChange = false
        private var lastWifiChangeTime = 0L
    }

    override fun onReceive(context: Context, intent: Intent?) {
        // WiFi PIN protection disabled - receiver does nothing
        Log.i("WifiStateReceiver", "WiFi state change detected - PIN protection disabled, allowing change")
    }
}
