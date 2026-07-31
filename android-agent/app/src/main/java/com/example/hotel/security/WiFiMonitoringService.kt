package com.example.hotel.security

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * WiFiMonitoringService â€” Doze-proof hotel tablet security monitor.
 * targetSdkVersion = 34, compileSdkVersion = 34
 *
 * On API 33+ (targetSdk >= 33) NEARBY_WIFI_DEVICES (with neverForLocation) is the
 * permission that unmasks WifiInfo. ACCESS_FINE_LOCATION is still required on API < 33
 * and on some OEM ROMs even on newer versions.
 */
class WiFiMonitoringService : Service() {

    private val powerManager: PowerManager by lazy {
        getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    private val wifiManager: WifiManager by lazy {
        applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    private val alarmManager: AlarmManager? by lazy {
        getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    }

    // â† FIX (CAUSE 2): Primary network-change trigger
    private val connectivityManager: ConnectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    val sixSignalMonitor = SixSignalMonitor(this)
    private lateinit var wifiReceiver: ScreenAndWiFiReceiver

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var isNetworkCallbackRegistered = false

    // â† DEBUG: counter so logcat shows every onCapabilitiesChanged fire
    @Volatile private var capChangedCount = 0

    private var isServiceRunning = false
    private val serviceStartTime = SystemClock.elapsedRealtime() // â† DEBUG: uptime tracking

    companion object {
        private const val TAG = "WiFiMonitoringService"
        private const val DBG = "WIFI_BREACH_DEBUG"          // â† DEBUG: unified tag
        private const val CHANNEL_ID = "hotel_security_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_HEARTBEAT = "com.example.hotel.ACTION_HEARTBEAT"
        private const val ALARM_REQUEST_CODE = 1001
        private const val HEARTBEAT_INTERVAL_MS = 10_000L
        private const val WAKELOCK_TIMEOUT_MS = 10_000L
        const val BREACH_COOLDOWN = 15_000L

        @Volatile var isRunning: Boolean = false
        var lastBreachTime = 0L
        var instance: WiFiMonitoringService? = null
            private set

        fun triggerBreachAlert(reason: String, rssi: Int = -127) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBreachTime > BREACH_COOLDOWN) {
                lastBreachTime = now
                Log.e(TAG, "ðŸš¨ BREACH TRIGGERED: $reason")
                instance?.sixSignalMonitor?.triggerBreach(reason, rssi)
            }
        }

        fun onNetworkLost() {
            Log.e(TAG, "Network Connectivity Lost â€” routing through 15s validation")
            instance?.sixSignalMonitor?.forceImmediateCheck()
        }

        fun reAcquireWakeLock() {
            instance?.acquireTimedWakeLock()
        }

        fun setMonitoringInterval(interval: Long, reason: String = "") {
            Log.i(TAG, "Interval is controlled by AlarmManager ($HEARTBEAT_INTERVAL_MS ms). " +
                    "Ignoring request for $interval ms. Reason: $reason")
            instance?.acquireTimedWakeLock()
        }
    }

    // â”€â”€ Service lifecycle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun ensureAuthorizedNetworkSaved() {
        val prefs = getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
        val savedSsid = prefs.getString("authorized_ssid", "") ?: ""
        val savedBssid = prefs.getString("authorized_bssid", "") ?: ""
        
        if (savedSsid.isNotEmpty()) {
            Log.d(TAG, "Authorized network already saved: SSID=$savedSsid BSSID=$savedBssid")
            return
        }
        
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) return
        
        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo ?: return
        
        @Suppress("DEPRECATION")
        val ssid = info.ssid?.replace("\"", "")?.trim() ?: ""
        
        @Suppress("DEPRECATION")
        val bssid = info.bssid ?: ""
        val finalBssid = if (bssid == "02:00:00:00:00:00" || bssid == "00:00:00:00:00:00") "" else bssid
        
        if (ssid.isEmpty() || ssid == "<unknown ssid>") return
        
        prefs.edit().apply {
            putString("authorized_ssid", ssid)
            putString("authorized_bssid", finalBssid)
            putLong("authorized_network_saved_at", System.currentTimeMillis())
            apply()
        }
        
        Log.i(TAG, "✅ Authorized network saved on service start: SSID=$ssid")
    }

    override fun onCreate() {
        super.onCreate()
        ensureAuthorizedNetworkSaved()
        instance = this

        // â† DEBUG: Service-alive heartbeat â€” gaps in logcat reveal OEM kills
        Log.i(DBG, "â•â•â• WiFiMonitoringService.onCreate() â•â•â• " +
                "SDK=${Build.VERSION.SDK_INT} targetSdk=34")
        logPermissionState("onCreate")

        createNotificationChannel()
        startForegroundCompat()

        wifiReceiver = ScreenAndWiFiReceiver()
        wifiReceiver.register(this)
        Log.d(TAG, "âœ… WiFi broadcast receiver registered")

        // â† FIX + DEBUG: Register NetworkCallback â€” primary event-driven trigger
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uptimeSec = (SystemClock.elapsedRealtime() - serviceStartTime) / 1000
        // â† DEBUG: Log every command so we see the service is alive
        Log.d(DBG, "onStartCommand action=${intent?.action} uptimeSec=$uptimeSec " +
                "callbackRegistered=$isNetworkCallbackRegistered capChangedCount=$capChangedCount")

        when (intent?.action) {
            ACTION_HEARTBEAT -> {
                Log.d(TAG, "â° AlarmManager heartbeat fired")
                acquireTimedWakeLock()
                sixSignalMonitor.forceImmediateCheck()
                scheduleNextAlarm()
            }

            "WIFI_OFF_BREACH" -> {
                Log.e(TAG, "ðŸš¨ WiFi OFF broadcast received â€” forcing immediate breach check")
                acquireTimedWakeLock()
                val isImmediate = intent.getBooleanExtra("IMMEDIATE_BREACH", false)
                val forcedRssi = intent.getIntExtra("FORCED_RSSI", -127)
                sixSignalMonitor.triggerBreach("WiFi DISABLING detected", forcedRssi, isImmediate)
                scheduleNextAlarm()
            }

            "WRONG_NETWORK_BREACH" -> {
                val reason = intent.getStringExtra("BREACH_REASON") ?: "Wrong WiFi network detected"
                val rssi = intent.getIntExtra("FORCED_RSSI", -127)
                Log.e(TAG, "ðŸš¨ WRONG NETWORK BREACH: $reason")
                acquireTimedWakeLock()
                scheduleNextAlarm()
                sixSignalMonitor.triggerBreach(reason = reason, rssi = rssi, isImmediate = true)
            }

            else -> {
                if (!isServiceRunning) {
                    Log.i(TAG, "ðŸš€ Starting hotel security monitoring")
                    isRunning = true
                    isServiceRunning = true
                    acquireTimedWakeLock()
                    scheduleNextAlarm()
                    sixSignalMonitor.startMonitoring()
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "Task removed â€” scheduling resurrection alarm")
        scheduleNextAlarm()
    }

    override fun onDestroy() {
        super.onDestroy()
        // â† DEBUG: Log stack-like breadcrumb when service is destroyed
        Log.w(DBG, "â•â•â• WiFiMonitoringService.onDestroy() â•â•â• scheduling resurrection alarm")

        unregisterNetworkCallback()

        try { wifiReceiver.unregister(this) } catch (e: Exception) {
            Log.w(TAG, "Receiver already unregistered: $e")
        }

        sixSignalMonitor.stopMonitoring()
        scheduleNextAlarm()

        isRunning = false
        isServiceRunning = false
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // NetworkCallback â€” PRIMARY fast trigger for any WiFi network change
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private fun registerNetworkCallback() {
        if (isNetworkCallbackRegistered) {
            Log.d(DBG, "NetworkCallback already registered â€” skipping duplicate")
            return
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {

            // â† DEBUG: onAvailable fires when WiFi associates to any network
            override fun onAvailable(network: Network) {
                Log.d(DBG, "ðŸ”” NetworkCallback.onAvailable network=$network " +
                        "callbackRegistered=$isNetworkCallbackRegistered")
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                capChangedCount++

                // â† DEBUG: Log raw transportInfo class/value BEFORE any filtering
                val transportInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    caps.transportInfo
                } else null
                Log.d(DBG, "ðŸ”” NetworkCallback.onCapabilitiesChanged #$capChangedCount " +
                        "network=$network transportInfoClass=${transportInfo?.javaClass?.simpleName ?: "null"}")

                // â† FIX + DEBUG: Extract WifiInfo from capabilities (API 29+)
                val wifiInfo: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    caps.transportInfo as? WifiInfo
                } else null

                if (wifiInfo != null) {
                    // â† DEBUG: Log raw WifiInfo string before masking-logic touches it
                    val rawBssid = wifiInfo.bssid ?: "null"
                    val rawSsid  = wifiInfo.ssid  ?: "null"
                    Log.d(DBG, "  WifiInfo.toString()=${wifiInfo}")
                    Log.d(DBG, "  raw BSSID='$rawBssid'  raw SSID='$rawSsid'")

                    val bssid = rawBssid
                    val ssid  = rawSsid.replace("\"", "")

                    // â† DEBUG: Log what we're passing into checkNetworkAuthorization
                    Log.d(DBG, "  â†’ calling checkNetworkAuthorization(liveBssid=$bssid, liveSsid=$ssid) from NetworkCallback")
                    wifiReceiver.checkNetworkAuthorization(
                        this@WiFiMonitoringService,
                        liveBssid = bssid,
                        liveSsid  = ssid,
                        source    = "NetworkCallback"       // â† DEBUG source tag
                    )
                } else {
                    // â† DEBUG: explain WHY wifiInfo was null
                    val sdkReason = when {
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ->
                            "SDK < 29 (transportInfo API not available)"
                        transportInfo == null ->
                            "transportInfo is null â€” callback may have fired before WiFi fully attached"
                        else ->
                            "transportInfo is ${transportInfo.javaClass.simpleName} (not WifiInfo)"
                    }
                    Log.w(DBG, "  WifiInfo is null: $sdkReason â€” falling back to broadcast-path check")
                    wifiReceiver.checkNetworkAuthorization(
                        this@WiFiMonitoringService,
                        source = "NetworkCallback-fallback"
                    )
                }
            }

            override fun onLost(network: Network) {
                // â† DEBUG: Helps distinguish WiFi-lost from network-switch
                Log.d(DBG, "ðŸ”” NetworkCallback.onLost network=$network " +
                        "(WiFi-off breach handled by broadcast receiver)")
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
            isNetworkCallbackRegistered = true
            Log.d(DBG, "âœ… NetworkCallback registered for TRANSPORT_WIFI at uptimeSec=" +
                    "${(SystemClock.elapsedRealtime() - serviceStartTime) / 1000}")
        } catch (e: Exception) {
            Log.e(DBG, "âŒ Failed to register NetworkCallback: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        val cb = networkCallback
        if (cb != null && isNetworkCallbackRegistered) {
            try {
                connectivityManager.unregisterNetworkCallback(cb)
                isNetworkCallbackRegistered = false
                networkCallback = null
                Log.d(DBG, "âœ… NetworkCallback unregistered")
            } catch (e: Exception) {
                Log.w(DBG, "NetworkCallback unregister failed: ${e.message}")
            }
        }
    }

    // â† DEBUG: Centralised permission state logger â€” call at key lifecycle points
    private fun logPermissionState(calledFrom: String) {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val nearbyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else null   // irrelevant below API 33

        Log.i(DBG, "[$calledFrom] SDK=${Build.VERSION.SDK_INT} targetSdk=34 " +
                "ACCESS_FINE_LOCATION=$fineGranted " +
                "NEARBY_WIFI_DEVICES=${nearbyGranted ?: "N/A(<API33)"}")
    }

    // â”€â”€ Private helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun scheduleNextAlarm() {
        val am = alarmManager ?: run {
            Log.e(TAG, "AlarmManager is null â€” cannot schedule heartbeat alarm")
            return
        }
        val intent = Intent(this, WiFiMonitoringService::class.java).apply {
            action = ACTION_HEARTBEAT
        }
        val pendingIntent = PendingIntent.getService(
            this, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = SystemClock.elapsedRealtime() + HEARTBEAT_INTERVAL_MS
        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
        Log.d(TAG, "â° Next heartbeat alarm in ${HEARTBEAT_INTERVAL_MS / 1000}s")
    }

    internal fun acquireTimedWakeLock() {
        try {
            val wl = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "HotelSecurity:HeartbeatWakeLock"
            )
            wl.setReferenceCounted(false)
            wl.acquire(WAKELOCK_TIMEOUT_MS)
            Log.d(TAG, "ðŸ”’ WakeLock acquired (${WAKELOCK_TIMEOUT_MS / 1000}s timeout)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock: ${e.message}")
        }
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Hotel Security", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Security monitoring active"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security monitoring active")
            .setContentText("Hotel tablet protection is running")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
}

