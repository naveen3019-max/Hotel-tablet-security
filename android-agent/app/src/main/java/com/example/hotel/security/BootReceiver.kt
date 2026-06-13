package com.example.hotel.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Broadcast received: ${intent.action}")
        
        val isBootAction = intent.action == Intent.ACTION_BOOT_COMPLETED ||
                           intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
                           intent.action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
                           intent.action == "com.lenovo.sleepmode.BOOT_COMPLETED" ||
                           intent.action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (isBootAction) {
            Log.i(TAG, "Boot or package update detected. Starting core services.")
            val monitorIntent = Intent(context, WiFiMonitoringService::class.java)
            val watchdogIntent = Intent(context, WatchdogService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(monitorIntent)
                context.startForegroundService(watchdogIntent)
            } else {
                context.startService(monitorIntent)
                context.startService(watchdogIntent)
            }
        }
    }
}
