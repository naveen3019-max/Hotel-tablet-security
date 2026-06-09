package com.example.hotel.security

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

object BatteryOptimizationHelper {

    fun isAppExcludedFromOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true 
    }

    fun requestExclusion(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isAppExcludedFromOptimizations(context)) {
            AlertDialog.Builder(context)
                .setTitle("Security Requirement")
                .setMessage("To ensure the security alarm works when the screen is off, this app must be excluded from Android's Battery Optimization.")
                .setPositiveButton("Configure") { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
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

    // ← FIXED: Added manufacturer instructions to handle OEM aggressive App Standby
    fun showManufacturerInstructions(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val title = "Crucial Step for $manufacturer"
        val message = when {
            manufacturer.contains("samsung") -> 
                "On Samsung tablets:\n1. Go to Settings > Device Care > Battery\n2. Background Usage Limits > 'Never sleeping apps'\n3. Add 'Hotel Agent' to this list."
            manufacturer.contains("lenovo") -> 
                "On Lenovo tablets:\n1. Go to Settings > Battery\n2. Background app management\n3. Allow 'Hotel Agent' to run unrestricted."
            else -> 
                "Please ensure there are no third-party battery savers or OEM optimization settings restricting 'Hotel Agent'."
        }

        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Understood") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
}
