package com.example.hotel.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

object BatteryOptimizationHelper {

    fun checkAndRequestExemption(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            showManufacturerSpecificDialog(context) 
        }
    }

    private fun showManufacturerSpecificDialog(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val message = when {
            manufacturer.contains("samsung") -> "CRITICAL SETUP (Samsung):
1. Settings -> Device Care -> Battery
2. Tap 'Background usage limits'
3. Tap 'Never sleeping apps'
4. Tap '+' and add Hotel Agent"
            manufacturer.contains("lenovo") -> "CRITICAL SETUP (Lenovo):
1. Settings -> Battery
2. Tap 'Background app management'
3. Find Hotel Agent
4. Select 'No restrictions'"
            else -> "CRITICAL SETUP:
1. Settings -> Apps -> Hotel Agent
2. Tap Battery
3. Select 'Unrestricted'"
        }

        AlertDialog.Builder(context)
            .setTitle("God Mode Security Setup")
            .setMessage(message)
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:" + context.packageName)
                context.startActivity(intent)
            }
            .setCancelable(false)
            .show()
    }
}
