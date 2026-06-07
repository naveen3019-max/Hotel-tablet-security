package com.example.hotel.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.hotel.data.AgentRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Detects package removal events.
 * Note: We cannot detect our OWN removal efficiently this way (OS kills us first).
 * But we can detect if critical dependencies or other admin apps are removed.
 */
class UninstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_PACKAGE_REMOVED) {
            val packageName = intent.data?.schemeSpecificPart
            Log.w("UninstallReceiver", "Package removed: $packageName")
            
            // If we want to be paranoid, lock on ANY uninstall
            // Or just log it to backend
            val prefs = context.getSharedPreferences("agent", Context.MODE_PRIVATE)
            val auth = prefs.getString("jwt_token", null)?.let { "Bearer $it" }
            val deviceId = prefs.getString("device_id", "UNKNOWN")
            val roomId = prefs.getString("room_id", "UNKNOWN")

            if (auth != null) {
                GlobalScope.launch {
                    try {
                        // We reuse breach alert for now or implement a new one
                        // Let's force a lock locally first
                        val lockIntent = Intent(context, com.example.hotel.ui.LockActivity::class.java)
                        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(lockIntent)
                    } catch (e: Exception) {
                        Log.e("UninstallReceiver", "Failed to handle uninstall", e)
                    }
                }
            }
        }
    }
}
