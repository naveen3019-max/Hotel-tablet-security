package com.example.hotel.security

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {

    fun isAppExcludedFromOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true // Below Android M, there is no Doze mode
    }

    fun requestExclusion(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isAppExcludedFromOptimizations(context)) {
            AlertDialog.Builder(context)
                .setTitle("Security Requirement")
                .setMessage("To ensure the security alarm works when the screen is off, this app must be excluded from Android's Battery Optimization.")
                .setPositiveButton("Configure") { _, _ ->
                    try {
                        // Takes user to the specific app optimization setting
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback if device manufacturer removed the specific intent
                        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(fallbackIntent)
                    }
                }
                .setCancelable(false)
                .show()
        }
    }
}
