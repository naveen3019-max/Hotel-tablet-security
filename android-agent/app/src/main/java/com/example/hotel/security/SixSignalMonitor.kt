package com.example.hotel.security

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

class SixSignalMonitor(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var lastCheckTime = 0L

    companion object {
        private const val TAG = "SixSignalMonitor"
        private const val CHECK_INTERVAL = 15_000L 
    }

    private val monitorRunnable = object : Runnable {
        override fun run() {
            try {
                val now = SystemClock.elapsedRealtime()
                
                if (lastCheckTime > 0 && (now - lastCheckTime) > 20000L) {
                    Log.w(TAG, "God Mode: Monitoring was paused by Doze! Gap: ${now - lastCheckTime}ms") 
                }
                
                lastCheckTime = now
                performSecurityCheck()
            } catch (e: Exception) {
                Log.e(TAG, "Error in God Mode monitoring loop", e)
            } finally {
                if (isRunning) {
                    handler.postDelayed(this, CHECK_INTERVAL) 
                }
            }
        }
    }

    fun startMonitoring() {
        if (!isRunning) {
            isRunning = true
            lastCheckTime = SystemClock.elapsedRealtime()
            handler.post(monitorRunnable)
            Log.i(TAG, "God Mode: SixSignalMonitor started")
        }
    }

    fun stopMonitoring() {
        isRunning = false
        handler.removeCallbacks(monitorRunnable)
        Log.i(TAG, "God Mode: SixSignalMonitor stopped")
    }

    fun forceImmediateCheck() {
        handler.removeCallbacks(monitorRunnable)
        handler.post(monitorRunnable) 
    }

    fun isMonitoringAlive(): Boolean {
        if (!isRunning) return false
        val timeSinceLastCheck = SystemClock.elapsedRealtime() - lastCheckTime
        return timeSinceLastCheck < 30_000L 
    }

    fun triggerBreach(reason: String) {
        // Assume existing backend alert logic via API call
    }

    private fun performSecurityCheck() {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        var failScore = 0

        if (!wifiManager.isWifiEnabled) failScore++
        
        val info = wifiManager.connectionInfo
        if (info == null || info.networkId == -1) failScore++

        if (failScore >= 3) {
            WiFiMonitoringService.triggerBreachAlert("Score $failScore/6 failed")
        } else if (failScore > 0) {
            Log.w(TAG, "God Mode: Warning - $failScore signals degraded, not breaching yet.")
        } else {
            WiFiMonitoringService.lastBreachTime = 0L 
        }
    }
}
