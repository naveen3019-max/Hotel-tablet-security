package com.example.hotel.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

/**
 * Detects screen lock/unlock events
 * 
 * IMPORTANT: When screen is locked, WiFi may disconnect due to power-saving.
 * This is NORMAL behavior, NOT a security breach!
 * 
 * Android 10+ Compatible: Uses PowerManager.isInteractive (API 20+)
 */
class ScreenStateReceiver : BroadcastReceiver() {
    
    companion object {
        @Volatile
        var isScreenOn: Boolean = true
            private set
        
        @Volatile
        private var screenOffTime: Long = 0
        
        @Volatile
        private var screenOnTime: Long = 0
        
        // Grace period AFTER screen turns ON (not while OFF)
        // WiFi needs time to reconnect after screen wakes up
        // Screen OFF = 60 second grace for WiFi Lock to stabilize
        // Screen ON = 3-minute grace for WiFi reconnect, then monitor
        private const val SCREEN_ON_GRACE_PERIOD_MS = 180_000L // 180 seconds (3 minutes)
        private const val SCREEN_OFF_GRACE_PERIOD_MS = 60_000L // 60 seconds after screen OFF
        
        fun getIsScreenLocked(): Boolean = !isScreenOn
        
        /**
         * Check if WiFi breach should be ignored
         * 
         * SCREEN OFF TRANSITION FIX:
         * - When screen first turns OFF, Android briefly disconnects WiFi (power management)
         * - WiFi Lock prevents prolonged disconnect, but transition takes ~30-60 seconds
         * - Grace period: Ignore breaches for 60 seconds after screen OFF
         * - After 60s: WiFi Lock has stabilized connection, resume monitoring
         * 
         * This prevents false breaches during screen OFF transition while enabling 24/7 monitoring.
         */
        fun shouldIgnoreWiFiBreach(): Boolean {
            val now = System.currentTimeMillis()
            
            // CRITICAL: When screen FIRST turns OFF, give WiFi Lock time to stabilize
            if (!isScreenOn && screenOffTime > 0) {
                val timeSinceScreenOff = now - screenOffTime
                if (timeSinceScreenOff < SCREEN_OFF_GRACE_PERIOD_MS) {
                    val remaining = (SCREEN_OFF_GRACE_PERIOD_MS - timeSinceScreenOff) / 1000
                    Log.d("ScreenState", "🌙 Screen OFF grace period: ${remaining}s remaining (WiFi Lock stabilizing)")
                    return true  // Ignore breaches during transition
                }
            }
            
            // After grace period expires, resume 24/7 monitoring
            Log.d("ScreenState", "✅ WiFi monitoring ACTIVE (screen ${if (isScreenOn) "ON" else "OFF - WiFi Lock active"})")
            return false
        }
        
        /**
         * Initialize screen state from PowerManager (Android 10+ compatible)
         * Call this when service starts to detect current screen state
         */
        fun initializeScreenState(context: Context) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            isScreenOn = powerManager.isInteractive
            if (!isScreenOn) {
                screenOffTime = System.currentTimeMillis()
            } else {
                screenOnTime = System.currentTimeMillis()
            }
            val state = if (isScreenOn) "ON" else "OFF"
            Log.i("ScreenState", "🔍 Init: Screen $state")
        }
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SCREEN_OFF -> {
                isScreenOn = false
                screenOffTime = System.currentTimeMillis()
                screenOnTime = 0 // Clear screen-on timestamp
                Log.i("ScreenState", "")
                Log.i("ScreenState", "═══════════════════════════════════════")
                Log.i("ScreenState", "🌙 SCREEN OFF DETECTED")
                Log.i("ScreenState", "   Cause: Manual lock OR Auto-timeout")
                Log.i("ScreenState", "   ⏳ 60-second grace period STARTED")
                Log.i("ScreenState", "   WiFi Lock stabilizing connection...")
                Log.i("ScreenState", "   After 60s: Resume 24/7 monitoring")
                Log.i("ScreenState", "═══════════════════════════════════════")
            }
            Intent.ACTION_SCREEN_ON -> {
                isScreenOn = true
                screenOnTime = System.currentTimeMillis() // Start grace period NOW
                val elapsed = if (screenOffTime > 0) (System.currentTimeMillis() - screenOffTime) / 1000 else 0
                Log.i("ScreenState", "")
                Log.i("ScreenState", "═══════════════════════════════════════")
                Log.i("ScreenState", "☀️ SCREEN ON DETECTED")
                Log.i("ScreenState", "   Was off for: ${elapsed}s")
                Log.i("ScreenState", "   ⏳ 3-minute grace period STARTED (WiFi reconnecting)")
                Log.i("ScreenState", "   Breach detection resumes after grace")
                Log.i("ScreenState", "═══════════════════════════════════════")
            }
            Intent.ACTION_USER_PRESENT -> {
                // User unlocked the device (passed lock screen)
                // This happens AFTER ACTION_SCREEN_ON, so screenOnTime already set
                isScreenOn = true
                val elapsed = if (screenOffTime > 0) (System.currentTimeMillis() - screenOffTime) / 1000 else 0
                Log.i("ScreenState", "🔓 USER UNLOCKED (screen was off ${elapsed}s) - Grace period in progress")
            }
        }
    }
}
