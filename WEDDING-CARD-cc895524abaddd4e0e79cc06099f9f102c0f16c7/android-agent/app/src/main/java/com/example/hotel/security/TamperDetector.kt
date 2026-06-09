package com.example.hotel.security

import android.content.Context
import android.os.Debug
import java.io.File

/**
 * Tamper Detection Module
 * Detects root access, debugger attachment, and security threats
 */
class TamperDetector(private val context: Context) {
    
    data class TamperResult(
        val isCompromised: Boolean,
        val threats: List<String>
    )
    
    fun performSecurityCheck(): TamperResult {
        val threats = mutableListOf<String>()
        
        // Check for root access
        if (isDeviceRooted()) {
            threats.add("ROOT_DETECTED")
        }
        
        // Check for debugger
        if (isDebuggerAttached()) {
            threats.add("DEBUGGER_ATTACHED")
        }
        
        // Check for USB debugging
        if (isUsbDebuggingEnabled()) {
            threats.add("USB_DEBUGGING_ENABLED")
        }
        
        // Check for Developer Options
        if (isDeveloperOptionsEnabled()) {
            threats.add("DEVELOPER_OPTIONS_ENABLED")
        }
        
        // Check for emulator
        if (isRunningOnEmulator()) {
            threats.add("EMULATOR_DETECTED")
        }
        
        // Check for installer package (should be from Play Store or manual install)
        if (isSuspiciousInstaller()) {
            threats.add("SUSPICIOUS_INSTALLER")
        }
        
        return TamperResult(
            isCompromised = threats.isNotEmpty(),
            threats = threats
        )
    }
    
    /**
     * Check if device is rooted
     * Multiple detection methods for reliability
     */
    private fun isDeviceRooted(): Boolean {
        return checkRootMethod1() || checkRootMethod2() || checkRootMethod3()
    }
    
    private fun checkRootMethod1(): Boolean {
        // Check for common root binaries
        val rootPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        
        return rootPaths.any { File(it).exists() }
    }
    
    private fun checkRootMethod2(): Boolean {
        // Check for root management apps
        val rootApps = arrayOf(
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.topjohnwu.magisk"
        )
        
        val packageManager = context.packageManager
        return rootApps.any { packageName ->
            try {
                packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
    
    private fun checkRootMethod3(): Boolean {
        // Try to execute su command
        return try {
            Runtime.getRuntime().exec("su")
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if debugger is attached
     */
    private fun isDebuggerAttached(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }
    
    /**
     * Check if USB debugging is enabled
     */
    private fun isUsbDebuggingEnabled(): Boolean {
        return try {
            val adbEnabled = android.provider.Settings.Secure.getInt(
                context.contentResolver,
                android.provider.Settings.Global.ADB_ENABLED,
                0
            )
            adbEnabled == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if Developer Options are enabled
     */
    fun isDeveloperOptionsEnabled(): Boolean {
        return try {
            val devOptions = android.provider.Settings.Secure.getInt(
                context.contentResolver,
                android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) 
            devOptions == 1
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if running on emulator
     */
    private fun isRunningOnEmulator(): Boolean {
        return (android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic")
                || "google_sdk" == android.os.Build.PRODUCT)
    }
    
    /**
     * Check for suspicious installer
     */
    private fun isSuspiciousInstaller(): Boolean {
        val installer = context.packageManager.getInstallerPackageName(context.packageName)
        
        // Allow these installers
        val trustedInstallers = listOf(
            "com.android.vending",        // Google Play Store
            "com.google.android.feedback", // Play Store
            null                           // Manual install (adb/file manager)
        )
        
        return installer != null && !trustedInstallers.contains(installer)
    }
    
    /**
     * Get human-readable threat description
     */
    fun getThreatDescription(threat: String): String {
        return when (threat) {
            "ROOT_DETECTED" -> "Device has root access - security compromised"
            "DEBUGGER_ATTACHED" -> "Debugger is attached to the app"
            "USB_DEBUGGING_ENABLED" -> "USB debugging is enabled in developer options"
            "EMULATOR_DETECTED" -> "App is running on an emulator"
            "SUSPICIOUS_INSTALLER" -> "App was not installed from trusted source"
            else -> "Unknown security threat"
        }
    }
}
