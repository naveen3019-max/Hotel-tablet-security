package com.example.hotel.security

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

object BatteryOptimizationHelper {

    fun showOptimizationInstructions(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val message: String
        val intent = Intent()

        when {
            manufacturer.contains("samsung") -> {
                message = "SAMSUNG TABLET DETECTED:\n\n1. Tap 'Settings' below.\n2. Open 'Device Care' -> 'Battery'.\n3. Tap 'App Power Management'.\n4. Add this app to 'Apps that won't be put to sleep' exceptions."
                intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            }
            manufacturer.contains("lenovo") -> {
                message = "LENOVO TABLET DETECTED:\n\n1. Tap 'Settings' below.\n2. Open 'Battery'.\n3. Tap 'Background app management'.\n4. Find this app and set it to 'No Restrictions'."
                intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            }
            else -> {
                message = "ANDROID TABLET DETECTED:\n\n1. Tap 'Settings' below.\n2. Tap 'All Apps'.\n3. Find this app and set it to 'Don't optimize'."
                intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            }
        }

        AlertDialog.Builder(context)
            .setTitle("CRITICAL: Power Management Bypass")
            .setMessage(message)
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    fallbackIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(fallbackIntent)
                }
            }
            .setCancelable(false)
            .show()
    }
}
