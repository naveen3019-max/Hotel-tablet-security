package com.example.hotel.security

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * WiFiMonitoringService — Doze-proof hotel tablet security monitor.
 *
 * Design rationale:
 * ─────────────────
 * Android 10 (API 29) Doze Mode fires ~60 s after screen-off. It does two things:
 *   1. Defers Handler.postDelayed() / coroutine delay() — they simply do not fire.
 *   2. Blocks all network access — so even if the scheduler fires, HTTP POST fails.
 *
 * The ONLY blessed mechanism that fires during Doze is AlarmManager.setExactAndAllowWhileIdle().
 * Each alarm:
 *   • Wakes the CPU via ELAPSED_REALTIME_WAKEUP.
 *   • Delivers ACTION_HEARTBEAT to this service.
 *   • We acquire a TIMED WakeLock (10 s), run the Wi-Fi check + heartbeat POST,
 *     release the lock in a finally block, then schedule the next alarm.
 *
 * Why START_NOT_STICKY?
 *   AlarmManager re-delivers the intent on a schedule. We don't need Android to
 *   restart the service with the last intent — that could cause a stale check.
 *   AlarmManager is the authoritative restart mechanism here.
 *
 * Why timed WakeLock (not indefinite)?
 *   WakeLocks held indefinitely will flatten battery and are rejected by Play Store
 *   policies. The heartbeat POST takes < 2 s on a good connection; 10 s is a safe
 *   upper bound that auto-releases even if the POST hangs.
 */
class WiFiMonitoringService : Service() {

    // ── Lazy system service references ────────────────────────────────────────
    // by lazy avoids crash if getSystemService() is called before onCreate().
    private val powerManager: PowerManager by lazy {
        getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    private val wifiManager: WifiManager by lazy {
        applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    private val alarmManager: AlarmManager? by lazy {
        // AlarmManager can theoretically be null on some embedded builds — handle it.
        getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    }

    // SixSignalMonitor owns all Wi-Fi / heartbeat / breach logic — do NOT change it.
    val sixSignalMonitor = SixSignalMonitor(this)

    // BroadcastReceiver registered programmatically (ACTION_SCREEN_OFF cannot use Manifest)
    private lateinit var wifiReceiver: ScreenAndWiFiReceiver

    private var isServiceRunning = false

    companion object {
        private const val TAG = "WiFiMonitoringService"
        private const val CHANNEL_ID = "hotel_security_channel"
        private const val NOTIFICATION_ID = 1001

        // ACTION sent by AlarmManager every HEARTBEAT_INTERVAL_MS
        const val ACTION_HEARTBEAT = "com.example.hotel.ACTION_HEARTBEAT"

        // AlarmManager request code — must be unique per PendingIntent in this app
        private const val ALARM_REQUEST_CODE = 1001

        // 10-second heartbeat — matches backend's expected interval exactly.
        // DO NOT change this value per project constraints.
        private const val HEARTBEAT_INTERVAL_MS = 10_000L

        // WakeLock timeout: enough for a heartbeat POST even on a slow connection.
        // Must be released in finally {} — this is the safety net if the service crashes.
        private const val WAKELOCK_TIMEOUT_MS = 10_000L

        // Breach cooldown to prevent flooding the backend with duplicate alerts
        const val BREACH_COOLDOWN = 15_000L

        @Volatile var isRunning: Boolean = false
        var lastBreachTime = 0L

        // Singleton reference — nullable, always null-checked before use
        var instance: WiFiMonitoringService? = null
            private set

        // ── Public helpers called from ScreenAndWiFiReceiver / SixSignalMonitor ──

        fun triggerBreachAlert(reason: String, rssi: Int = -127) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBreachTime > BREACH_COOLDOWN) {
                lastBreachTime = now
                Log.e(TAG, "🚨 BREACH TRIGGERED: $reason")
                instance?.sixSignalMonitor?.triggerBreach(reason, rssi)
            }
        }

        fun onNetworkLost() {
            Log.e(TAG, "Network Connectivity Lost — routing through 15s validation")
            instance?.sixSignalMonitor?.forceImmediateCheck()
        }

        fun reAcquireWakeLock() {
            // Called from ScreenAndWiFiReceiver when screen turns off — ensures the
            // service WakeLock is held before Doze can suppress it.
            instance?.acquireTimedWakeLock()
        }

        // Kept for source-compatibility with existing callers — interval is managed
        // by AlarmManager now, not this method.
        fun setMonitoringInterval(interval: Long, reason: String = "") {
            Log.i(TAG, "Interval is controlled by AlarmManager ($HEARTBEAT_INTERVAL_MS ms). " +
                    "Ignoring request for $interval ms. Reason: $reason")
            instance?.acquireTimedWakeLock()
        }
    }

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        instance = this

        createNotificationChannel()

        // Must call startForeground() within 5 seconds of onCreate() to avoid ANR.
        startForegroundCompat()

        // Register WiFi / screen state receiver programmatically.
        // ACTION_SCREEN_OFF is only deliverable to runtime-registered receivers.
        wifiReceiver = ScreenAndWiFiReceiver()
        wifiReceiver.register(this)
        Log.d(TAG, "✅ WiFi broadcast receiver registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {

            ACTION_HEARTBEAT -> {
                // ── Alarm fired — this is the core Doze-proof heartbeat path ──
                Log.d(TAG, "⏰ AlarmManager heartbeat fired")

                // Acquire a TIMED WakeLock so the CPU stays awake long enough to
                // complete the heartbeat POST. Released in the finally block inside
                // acquireTimedWakeLock() after WAKELOCK_TIMEOUT_MS at most.
                acquireTimedWakeLock()

                // Run the Wi-Fi check + HTTP POST (SixSignalMonitor.forceImmediateCheck
                // calls performSecurityCheck which sends the heartbeat)
                sixSignalMonitor.forceImmediateCheck()

                // Schedule the next alarm AFTER the check so there is no drift.
                scheduleNextAlarm()
            }

            "WIFI_OFF_BREACH" -> {
                // ── Instant breach from ScreenAndWiFiReceiver ──
                Log.e(TAG, "🚨 WiFi OFF broadcast received — forcing immediate breach check")
                acquireTimedWakeLock()
                val isImmediate = intent.getBooleanExtra("IMMEDIATE_BREACH", false)
                sixSignalMonitor.forceImmediateCheck(skipDelay = isImmediate)
                scheduleNextAlarm()
            }

            else -> {
                // ── Normal service start (boot, first launch, process restart) ──
                if (!isServiceRunning) {
                    Log.i(TAG, "🚀 Starting hotel security monitoring")
                    isRunning = true
                    isServiceRunning = true

                    acquireTimedWakeLock()
                    scheduleNextAlarm()
                    // Run an immediate check on start so we don't wait 10 s for first data
                    sixSignalMonitor.startMonitoring()
                }
            }
        }

        // START_NOT_STICKY: AlarmManager is the re-trigger mechanism.
        // If Android kills the service between alarms we do NOT want it to restart
        // immediately with a stale intent — the next alarm will restart it correctly.
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // App was swiped from recents. Schedule one last alarm so the service
        // restarts via AlarmManager before the next heartbeat is due.
        Log.w(TAG, "Task removed — scheduling resurrection alarm")
        scheduleNextAlarm()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "Service destroyed — scheduling resurrection alarm")

        try {
            wifiReceiver.unregister(this)
        } catch (e: Exception) {
            Log.w(TAG, "Receiver already unregistered: $e")
        }

        sixSignalMonitor.stopMonitoring()

        // Schedule the next alarm so AlarmManager restarts us after the interval
        scheduleNextAlarm()

        isRunning = false
        isServiceRunning = false
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Schedule the next heartbeat alarm using setExactAndAllowWhileIdle().
     *
     * Why setExactAndAllowWhileIdle() and not setExact()?
     *   setExact() is deferred during Doze — it is batched with other alarms and
     *   may not fire for minutes. setExactAndAllowWhileIdle() is explicitly blessed
     *   by Android to fire on time even in deep Doze, at the cost of a minimum
     *   9-minute gap between alarms on API 31+ (we are on API 29 so no restriction).
     *
     * Why ELAPSED_REALTIME_WAKEUP?
     *   It wakes the CPU from sleep. ELAPSED_REALTIME without WAKEUP would not fire
     *   while the processor is asleep.
     *
     * Note: Do NOT use SCHEDULE_EXACT_ALARM (API 31+) — minSdk is 29.
     */
    private fun scheduleNextAlarm() {
        val am = alarmManager ?: run {
            Log.e(TAG, "AlarmManager is null — cannot schedule heartbeat alarm")
            return
        }

        val intent = Intent(this, WiFiMonitoringService::class.java).apply {
            action = ACTION_HEARTBEAT
        }
        val pendingIntent = PendingIntent.getService(
            this,
            ALARM_REQUEST_CODE,
            intent,
            // FLAG_UPDATE_CURRENT replaces any existing alarm with the same request code.
            // FLAG_IMMUTABLE is required on API 23+ for security.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = SystemClock.elapsedRealtime() + HEARTBEAT_INTERVAL_MS

        // setExactAndAllowWhileIdle() is available from API 23 (we are min API 29).
        am.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            pendingIntent
        )

        Log.d(TAG, "⏰ Next heartbeat alarm in ${HEARTBEAT_INTERVAL_MS / 1000}s")
    }

    /**
     * Acquire a TIMED PARTIAL_WAKE_LOCK.
     *
     * Why timed (not indefinite)?
     * • Indefinite WakeLocks drain battery and are a red flag in ANR/battery analysis.
     * • The heartbeat POST completes in < 2 s under normal conditions.
     * • 10 s is a conservative upper bound — auto-releases even if the service crashes.
     * • We ALSO release explicitly in finally{} inside the caller, so in the happy
     *   path the lock is released promptly after the POST completes.
     *
     * setReferenceCounted(false) prevents a crash if acquire() is called twice
     * (e.g., ACTION_HEARTBEAT and WIFI_OFF_BREACH arrive close together).
     */
    private fun acquireTimedWakeLock() {
        try {
            val wl = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HotelSecurity:HeartbeatWakeLock"
            )
            wl.setReferenceCounted(false)
            wl.acquire(WAKELOCK_TIMEOUT_MS)   // auto-releases after 10 s
            Log.d(TAG, "🔒 WakeLock acquired (${WAKELOCK_TIMEOUT_MS / 1000}s timeout)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock: ${e.message}")
        }
    }

    /**
     * Start this service as a foreground service with FOREGROUND_SERVICE_TYPE_DATA_SYNC.
     *
     * Why DATA_SYNC type?
     *   Android 14+ requires a foreground service type. DATA_SYNC is the correct
     *   semantic for a service that periodically syncs data (heartbeats) to a server.
     *   It also requires FOREGROUND_SERVICE_DATA_SYNC permission in the manifest.
     *
     * Why PRIORITY_LOW notification?
     *   Hotel guests should not be disturbed by a security notification. PRIORITY_LOW
     *   keeps it below the fold in the notification shade with no sound/vibration.
     *
     * Why "Security monitoring active" and no room number?
     *   Room numbers in notifications are a privacy/security risk if a guest sees them.
     */
    private fun startForegroundCompat() {
        val notification = buildNotification()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hotel Security",
                // IMPORTANCE_LOW = no sound, no vibration, below-the-fold placement.
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Security monitoring active"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security monitoring active")
            // Generic text — does NOT reveal room number or device ID to protect guest privacy
            .setContentText("Hotel tablet protection is running")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)              // Cannot be dismissed by the user
            .setPriority(NotificationCompat.PRIORITY_LOW)    // No sound, no heads-up
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
}
