package com.example.hotel.security

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

data class WifiFingerprint(
    val ssid: String?,
    val bssid: String?,
    val rssi: Int,
    val ip: String?
)

class WifiFence(
    context: Context,
    private val targetBssid: String,
    private val targetSsid: String?,
    private val minRssi: Int,
    private val graceSeconds: Int,
    private val onBreach: (Int?) -> Unit,
    private val onRecovery: (() -> Unit)? = null
) {

    private val context = context.applicationContext
    private val wifiManager =
        context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val handler = Handler(Looper.getMainLooper())
    private val scanIntervalMs = 2000L // 2 seconds
    private val networkLossTimeoutMs = 15000L // 15 seconds until network loss = breach

    private var breachCounter = 0
    private var isRunning = false
    private var isInBreachState = false  // Flag to prevent repeated breach triggers
    private var lastKnownRssi: Int? = null
    private var lastKnownBssid: String? = null
    private var lastKnownSsid: String? = null
    private var wifiDisconnectTime: Long = 0
    private var lastNetworkCheckTime: Long = 0
    private var wifiReconnectingTime: Long = 0  // Time when WiFi started reconnecting
    private val reconnectGracePeriodMs = 10000L  // 10 seconds grace period for WiFi to reconnect
    private var lastRecoveryTime: Long = 0  // Time when last recovery occurred
    private val recoveryCooldownMs = 10000L  // 10 seconds cooldown after recovery before allowing new breaches

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d("WifiFence", "Network available: $network")
            wifiDisconnectTime = 0
        }

        override fun onLost(network: Network) {
            Log.e("WifiFence", "🚨 Network lost: $network")
            if (isRunning) {
                wifiDisconnectTime = System.currentTimeMillis()
                // Will be checked in scan loop
            }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val hasWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            Log.v("WifiFence", "Network caps changed: wifi=$hasWifi, internet=$hasInternet")
            
            if (isRunning && !hasWifi) {
                Log.w("WifiFence", "🚨 WiFi transport lost")
                wifiDisconnectTime = System.currentTimeMillis()
            }
        }
    }

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            try {
                lastNetworkCheckTime = System.currentTimeMillis()
                val info = wifiManager.connectionInfo
                val currentBssid = info?.bssid
                val currentSsid = info?.ssid?.trim('"')
                val currentRssi = if (info?.rssi != -127) info?.rssi else null

                // Track current state
                lastKnownRssi = currentRssi
                lastKnownBssid = currentBssid
                lastKnownSsid = currentSsid
                
                // Check if WiFi is currently enabled
                val isWifiEnabled = wifiManager.isWifiEnabled

                Log.i(
                    "WifiFence",
                    "📡 Scan: SSID=$currentSsid, BSSID=$currentBssid, RSSI=$currentRssi dBm " +
                    "(Min: $minRssi dBm, Grace: $graceSeconds s, WiFi Enabled: $isWifiEnabled)"
                )
                
                Log.v("WifiFence", "🔍 State: WiFi Enabled=$isWifiEnabled, BSSID=$currentBssid, RSSI=$currentRssi, InBreach=$isInBreachState, ReconnectTimer=$wifiReconnectingTime")
                
                // Detect WiFi reconnection (WiFi enabled but not connected yet)
                // Start grace period whenever WiFi is ON but not connected (regardless of breach state)
                if (isWifiEnabled && currentBssid == null && wifiReconnectingTime == 0L) {
                    wifiReconnectingTime = System.currentTimeMillis()
                    Log.e("WifiFence", "🔄 WiFi is ON but not connected - STARTING RECONNECTION GRACE PERIOD")
                }
                
                // Clear grace period if WiFi is now connected
                if (isWifiEnabled && currentBssid != null && wifiReconnectingTime > 0) {
                    val reconnectDuration = (System.currentTimeMillis() - wifiReconnectingTime) / 1000
                    Log.e("WifiFence", "✅ WiFi connected after ${reconnectDuration}s! CLEARING reconnection grace period timer")
                    wifiReconnectingTime = 0
                }
                
                // Check if we're in reconnection grace period
                val isInReconnectGracePeriod = wifiReconnectingTime > 0 && 
                    (System.currentTimeMillis() - wifiReconnectingTime) < reconnectGracePeriodMs
                
                if (isInReconnectGracePeriod) {
                    val elapsed = (System.currentTimeMillis() - wifiReconnectingTime) / 1000
                    Log.i("WifiFence", "⏳ WiFi reconnecting... grace period ${elapsed}s / ${reconnectGracePeriodMs/1000}s")
                }

                // ===== SIMPLE BREACH DETECTION - ANY WIFI =====
                // Only check if WiFi is ON and connected to ANY network
                when {
                    // 1️⃣ CRITICAL: WiFi completely OFF - IMMEDIATE BREACH
                    !isWifiEnabled -> {
                        Log.e("WifiFence", "🚨 WiFi is DISABLED (turned OFF) - IMMEDIATE BREACH!")
                        // Set breach counter high enough to exceed grace period immediately
                        breachCounter = (graceSeconds * 1000 / scanIntervalMs).toInt() + 1
                        wifiReconnectingTime = 0  // Reset reconnection timer
                    }

                    // 2️⃣ CRITICAL: Network loss > 15 seconds
                    wifiDisconnectTime > 0 && 
                    (System.currentTimeMillis() - wifiDisconnectTime) > networkLossTimeoutMs -> {
                        Log.e("WifiFence", "🚨 Network disconnected for > 15 seconds")
                        breachCounter += 3  // Immediate breach
                        wifiDisconnectTime = 0
                    }

                    // 🔄 GRACE PERIOD: WiFi is reconnecting, give it time
                    // CRITICAL FIX: If WiFi is ON and we're in breach state, keep giving it time to reconnect
                    // Don't let grace period expire if WiFi is still enabled and trying to connect
                    isWifiEnabled && currentBssid == null && isInBreachState -> {
                        val elapsed = if (wifiReconnectingTime > 0) {
                            (System.currentTimeMillis() - wifiReconnectingTime) / 1000
                        } else {
                            0
                        }
                        Log.e("WifiFence", "⏳ WiFi is ON but not connected (${elapsed}s) - WAITING for reconnection (in breach state, not accumulating)")
                        breachCounter = 0  // Don't accumulate breaches while WiFi is ON and trying to connect
                    }
                    
                    // Standard grace period for non-breach situations
                    isInReconnectGracePeriod -> {
                        val elapsed = (System.currentTimeMillis() - wifiReconnectingTime) / 1000
                        Log.i("WifiFence", "⏳ In reconnection grace period ${elapsed}s / ${reconnectGracePeriodMs/1000}s - not counting as breach")
                        breachCounter = 0  // Don't accumulate breaches during grace period
                    }

                    // 3️⃣ Connection info is null (disconnected or location disabled) - only if WiFi is OFF or not in breach state
                    currentBssid == null -> {
                        Log.w("WifiFence", "⚠ Not connected to any WiFi network")
                        breachCounter += 2
                    }

                    // ✅ Connected to ANY WiFi network with good signal
                    currentRssi != null && currentRssi >= minRssi -> {
                        // Clear grace period timer since we're now connected
                        if (wifiReconnectingTime > 0) {
                            Log.e("WifiFence", "✅ Clearing reconnection timer - WiFi fully connected with good signal")
                            wifiReconnectingTime = 0
                        }
                        
                        if (breachCounter > 0) {
                            Log.e(
                                "WifiFence",
                                "✅ Connected to WiFi (SSID=$currentSsid, RSSI=$currentRssi >= $minRssi dBm). RESETTING breach counter (was $breachCounter)"
                            )
                        }
                        
                        // CRITICAL: If recovering from breach state, notify
                        if (isInBreachState) {
                            Log.e("WifiFence", "")
                            Log.e("WifiFence", "═══════════════════════════════════════════")
                            Log.e("WifiFence", "🎉🎉🎉 WiFi RECOVERED!")
                            Log.e("WifiFence", "🎉🎉🎉 Triggering recovery callback...")
                            Log.e("WifiFence", "🎉🎉🎉 Setting 30-second recovery cooldown")
                            Log.e("WifiFence", "═══════════════════════════════════════════")
                            Log.e("WifiFence", "")
                            isInBreachState = false
                            breachCounter = 0
                            lastRecoveryTime = System.currentTimeMillis()  // Start cooldown period
                            onRecovery?.invoke()
                        } else {
                            // Even if not in breach state, clear any residual breach counter
                            if (breachCounter > 0) {
                                Log.i("WifiFence", "✅ Clearing breach counter (not in breach state)")
                            }
                            breachCounter = 0
                        }
                    }

                    // ⚠️ Connected but signal is weak
                    currentRssi != null && currentRssi < minRssi -> {
                        Log.w("WifiFence", "⚠️ WiFi signal weak: $currentRssi < $minRssi dBm (SSID=$currentSsid)")
                        breachCounter += 1  // Gradual breach - allow some time to reconnect
                    }

                    // Unknown state
                    else -> {
                        Log.w("WifiFence", "⚠ Unknown WiFi state - treating as potential breach")
                        breachCounter++
                    }
                }

                val elapsedSeconds = breachCounter * (scanIntervalMs / 1000)

                // Check if we're in recovery cooldown period
                val isInRecoveryCooldown = lastRecoveryTime > 0 && 
                    (System.currentTimeMillis() - lastRecoveryTime) < recoveryCooldownMs
                
                if (isInRecoveryCooldown) {
                    val cooldownRemaining = (recoveryCooldownMs - (System.currentTimeMillis() - lastRecoveryTime)) / 1000
                    Log.i("WifiFence", "🛡️ Recovery cooldown active (${cooldownRemaining}s remaining) - preventing false breaches")
                    breachCounter = 0  // Don't accumulate breaches during cooldown
                }

                if (elapsedSeconds >= graceSeconds && !isInRecoveryCooldown) {
                    if (!isInBreachState) {
                        // CRITICAL: Don't trigger breach if screen is locked OR within grace period after lock
                        // WiFi disconnect is normal power-saving behavior on Android
                        if (ScreenStateReceiver.shouldIgnoreWiFiBreach()) {
                            Log.w("WifiFence", "🌙 Screen lock grace period active - WiFi disconnect is normal, resetting breach counter")
                            breachCounter = 0
                            return
                        }
                        
                        Log.e(
                            "WifiFence",
                            "🚨🚨🚨 WIFI FENCE BREACH! Elapsed: $elapsedSeconds s, Grace: $graceSeconds s, Counter: $breachCounter"
                        )
                        Log.e("WifiFence", "🚨 Breach trigger: WiFi Enabled=$isWifiEnabled, BSSID=$currentBssid, RSSI=$currentRssi")
                        isInBreachState = true  // Set flag to prevent repeated breach triggers
                        onBreach(lastKnownRssi)
                        breachCounter = 0
                    } else {
                        Log.w("WifiFence", "⚠ Already in breach state, not triggering again")
                    }
                } else if (breachCounter > 0) {
                    Log.w(
                        "WifiFence",
                        "⚠ Breach counter: $breachCounter (~$elapsedSeconds/$graceSeconds s) - WiFi Enabled: $isWifiEnabled"
                    )
                }

            } catch (e: Exception) {
                Log.e("WifiFence", "Wi-Fi check failed", e)
            }

            handler.postDelayed(this, scanIntervalMs)
        }
    }

    /** Start monitoring Wi-Fi boundary */
    fun start() {
        if (isRunning) {
            Log.w("WifiFence", "⚠️ Already running, ignoring start() call")
            return
        }
        isRunning = true
        breachCounter = 0
        Log.e("WifiFence", "🚀 WifiFence STARTED - Monitoring every ${scanIntervalMs}ms, Min RSSI: $minRssi dBm, Grace: $graceSeconds s")
        isInBreachState = false  // Reset breach state on start
        wifiDisconnectTime = 0
        wifiReconnectingTime = 0  // Reset reconnection timer
        
        // Register for network updates
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        
        handler.post(scanRunnable)
        Log.i("WifiFence", "✅ WiFi Fence started: SSID=$targetSsid, BSSID=$targetBssid, minRSSI=$minRssi")
    }

    /** Stop monitoring */
    fun stop() {
        isRunning = false
        handler.removeCallbacks(scanRunnable)
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e("WifiFence", "Failed to unregister callback", e)
        }
        Log.d("WifiFence", "Fence stopped")
    }

    /** Latest known RSSI (safe for heartbeat use) */
    fun getCurrentRssi(): Int? {
        return lastKnownRssi
    }

    /** Latest known BSSID (safe for heartbeat use) */
    fun getCurrentBssid(): String? {
        return lastKnownBssid
    }

    /** Latest known SSID (safe for heartbeat use) */
    fun getCurrentSsid(): String? {
        return lastKnownSsid
    }

    /** Get current Wi-Fi fingerprint for diagnostics */
    fun getCurrentFingerprint(): WifiFingerprint {
        val info = wifiManager.connectionInfo
        return WifiFingerprint(
            ssid = info?.ssid?.trim('"'),
            bssid = info?.bssid,
            rssi = info?.rssi ?: -100,
            ip = info?.ipAddress?.let { formatIpAddress(it) }
        )
    }

    /** Utility to format IP address from int */
    private fun formatIpAddress(ip: Int): String {
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
    }
}
