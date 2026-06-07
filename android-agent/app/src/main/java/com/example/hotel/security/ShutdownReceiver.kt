package com.example.hotel.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.hotel.ui.PowerOffPinDialog

/**
 * Intercepts device shutdown/restart attempts
 * Shows PIN dialog before allowing power off
 */
class ShutdownReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.e("ShutdownReceiver", "════════════════════════════════════════")
        Log.e("ShutdownReceiver", "🔴 SHUTDOWN/RESTART DETECTED!")
        Log.e("ShutdownReceiver", "Action: ${intent?.action}")
        Log.e("ShutdownReceiver", "════════════════════════════════════════")

        val action = intent?.action
        
        when (action) {
            Intent.ACTION_SHUTDOWN,
            "android.intent.action.ACTION_SHUTDOWN",
            Intent.ACTION_REBOOT,
            "android.intent.action.REBOOT",
            "android.intent.action.QUICKBOOT_POWEROFF",
            "com.android.internal.intent.action.REQUEST_SHUTDOWN" -> {
                
                Log.e("ShutdownReceiver", "🚨 Power off/restart attempt intercepted!")
                
                // Check if device is provisioned
                val prefs = context.getSharedPreferences("agent", Context.MODE_PRIVATE)
                val isProvisioned = prefs.getBoolean("provisioned", false)
                
                if (!isProvisioned) {
                    Log.e("ShutdownReceiver", "Device not provisioned - allowing shutdown")
                    return
                }
                
                // Check if authorized
                val authorized = prefs.getBoolean("shutdown_authorized", false)
                
                if (authorized) {
                    Log.e("ShutdownReceiver", "✅ Shutdown AUTHORIZED by PIN")
                    prefs.edit().putBoolean("shutdown_authorized", false).apply()
                    return
                }
                
                Log.e("ShutdownReceiver", "❌ UNAUTHORIZED shutdown attempt!")
                
                // Try to abort the broadcast (may not work on all Android versions)
                try {
                    abortBroadcast()
                    Log.e("ShutdownReceiver", "✅ Shutdown broadcast aborted")
                } catch (e: Exception) {
                    Log.e("ShutdownReceiver", "Could not abort broadcast: ${e.message}")
                }
                
                // Show PIN dialog immediately
                try {
                    val pinIntent = Intent(context, PowerOffPinDialog::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                        addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    }
                    context.startActivity(pinIntent)
                    Log.e("ShutdownReceiver", "📱 Power-off PIN dialog launched")
                } catch (e: Exception) {
                    Log.e("ShutdownReceiver", "Failed to launch PIN dialog: ${e.message}")
                }
            }
            
            else -> {
                Log.e("ShutdownReceiver", "Unknown action: $action")
            }
        }
    }
}
