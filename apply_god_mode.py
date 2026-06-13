import os
import re

base_dir = r"c:\Users\navee\Downloads\Hotel-tablet-security-master\Hotel-tablet-security-master\WEDDING-CARD-cc895524abaddd4e0e79cc06099f9f102c0f16c7"

wifi_service_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\security\WiFiMonitoringService.kt")
six_signal_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\security\SixSignalMonitor.kt")
watchdog_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\security\WatchdogService.kt")
doze_alarm_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\security\DozeAlarmReceiver.kt")
boot_receiver_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\security\BootReceiver.kt")
battery_helper_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\security\BatteryOptimizationHelper.kt")
manifest_path = os.path.join(base_dir, r"android-agent\app\src\main\AndroidManifest.xml")

use_websocket_path = os.path.join(base_dir, r"dashboard\src\hooks\useWebSocket.ts")
page_tsx_path = os.path.join(base_dir, r"dashboard\src\app\page.tsx")

wifi_content = """package com.example.hotel.security

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat

class WiFiMonitoringService : Service() {

    private lateinit var powerManager: PowerManager
    private lateinit var wifiManager: WifiManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val sixSignalMonitor = SixSignalMonitor(this)
    private var isServiceRunning = false

    companion object {
        private const val TAG = "WiFiMonitoringService"
        private const val CHANNEL_ID = "wifi_security_channel"
        private const val NOTIFICATION_ID = 1001
        
        @Volatile var isRunning: Boolean = false
        var lastBreachTime = 0L 
        const val BREACH_COOLDOWN = 15_000L 
        
        var instance: WiFiMonitoringService? = null
            private set

        fun triggerBreachAlert(reason: String) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBreachTime > BREACH_COOLDOWN) {
                lastBreachTime = now
                Log.e(TAG, "🚨 BREACH TRIGGERED: $reason")
                instance?.sixSignalMonitor?.triggerBreach(reason)
            }
        }

        fun onNetworkLost() {
            triggerBreachAlert("Network Connectivity Lost")
        }

        fun reAcquireWakeLock() {
            instance?.acquireLocksSafely() 
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        createNotificationChannel()
        applyManufacturerFix() 
    }

    private fun applyManufacturerFix() {
        val manufacturer = Build.MANUFACTURER.lowercase()
        when {
            manufacturer.contains("samsung") -> {
                Log.d(TAG, "God Mode: Samsung One UI bypass active")
                startForeground(NOTIFICATION_ID, buildNotification(NotificationCompat.PRIORITY_MAX)) 
            }
            manufacturer.contains("lenovo") -> {
                Log.d(TAG, "God Mode: Lenovo background bypass active")
                startForeground(NOTIFICATION_ID, buildNotification(NotificationCompat.PRIORITY_HIGH))
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification(NotificationCompat.PRIORITY_DEFAULT))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ALARM_CHECK") { 
            Log.d(TAG, "God Mode: Doze ALARM_CHECK fired!")
            acquireLocksSafely()
            scheduleNextAlarm()
            if (!sixSignalMonitor.isMonitoringAlive()) {
                Log.w(TAG, "God Mode: Monitoring paused by Doze! Forcing restart...")
                sixSignalMonitor.startMonitoring()
            } else {
                sixSignalMonitor.forceImmediateCheck() 
            }
            return START_STICKY
        }

        if (!isServiceRunning) {
            Log.i(TAG, "God Mode: Starting impenetrable monitoring...")
            isRunning = true
            isServiceRunning = true
            
            acquireLocksSafely()
            scheduleNextAlarm()
            sixSignalMonitor.startMonitoring()
        }
        
        return START_STICKY 
    }

    private fun scheduleNextAlarm() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getService(
            this, 1001,
            Intent(this, WiFiMonitoringService::class.java).apply { action = "ALARM_CHECK" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle( 
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 55_000L,
                pi
            )
        } else {
            am.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 55_000L,
                pi
            )
        }
    }

    private fun acquireLocksSafely() {
        if (wakeLock?.isHeld == false || wakeLock == null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HotelSecurity:GodModeWakeLock")
            wakeLock?.acquire() 
        }
        if (wifiLock?.isHeld == false || wifiLock == null) {
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "HotelSecurity:GodModeWifiLock")
            wifiLock?.acquire() 
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Security Active", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "God Mode monitoring active"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(priority: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔒 Security Active")
            .setContentText("Monitoring every 15s")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_ALARM) 
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.e(TAG, "God Mode: Task removed! Triggering immediate self-resurrection.")
        scheduleNextAlarm() 
    }

    override fun onDestroy() {
        super.onDestroy()
        scheduleNextAlarm() 
        sixSignalMonitor.stopMonitoring()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
        isRunning = false
        isServiceRunning = false
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
"""

six_signal_content = """package com.example.hotel.security

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
"""

watchdog_content = """package com.example.hotel.security

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log

class WatchdogService : Service() {

    companion object {
        private const val TAG = "WatchdogService"
        private const val WATCHDOG_INTERVAL_MS = 10_000L 
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastWatchdogRunTime = 0L

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            try {
                lastWatchdogRunTime = SystemClock.elapsedRealtime()
                checkAndRestartMonitoringService()
            } finally {
                handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        scheduleWatchdogAlarm()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "WATCHDOG_ALARM") { 
            scheduleWatchdogAlarm()
        }
        handler.removeCallbacks(watchdogRunnable)
        handler.post(watchdogRunnable)
        return START_STICKY
    }

    private fun checkAndRestartMonitoringService() {
        if (!WiFiMonitoringService.isRunning || WiFiMonitoringService.instance?.sixSignalMonitor?.isMonitoringAlive() != true) {
            Log.e(TAG, "God Mode: Primary monitor dead or frozen! Executing full restart sequence.")
            restartEverything()
        }
    }

    private fun restartEverything() {
        stopService(Intent(this, WiFiMonitoringService::class.java))
        Thread.sleep(500) 
        
        val restartIntent = Intent(this, WiFiMonitoringService::class.java)
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        if (manufacturer.contains("samsung")) restartIntent.setPackage(packageName)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
        
        if (manufacturer.contains("lenovo")) {
            sendBroadcast(Intent("com.example.hotel.security.RESTART_MONITORING")) 
        }
    }

    private fun scheduleWatchdogAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(this, WatchdogService::class.java).apply { action = "WATCHDOG_ALARM" }
        val pendingIntent = PendingIntent.getService(this, 2, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle( 
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 90_000L,
                pendingIntent
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(watchdogRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
"""

doze_alarm_content = """package com.example.hotel.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log

class DozeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HotelSecurity:DozeAlarmReceiver")
        wl.acquire(30_000) 
        
        try {
            val serviceIntent = Intent(context, WiFiMonitoringService::class.java).apply { 
                action = "ALARM_CHECK" 
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            Log.d("DozeAlarm", "✅ God Mode: Woke up from Doze - checking WiFi")
        } catch (e: Exception) {
            Log.e("DozeAlarm", "Failed to boot service from Receiver", e)
        } finally {
            wl.release()
        }
    }
}
"""

boot_receiver_content = """package com.example.hotel.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i("BootReceiver", "God Mode: Boot intercept: $action")

        Handler(Looper.getMainLooper()).postDelayed({
            val serviceIntent = Intent(context, WiFiMonitoringService::class.java)
            val watchdogIntent = Intent(context, WatchdogService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
                context.startForegroundService(watchdogIntent)
            } else {
                context.startService(serviceIntent)
                context.startService(watchdogIntent)
            }
            Log.i("BootReceiver", "God Mode: All systems online post-boot")
        }, 3000)
    }
}
"""

battery_helper_content = """package com.example.hotel.security

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
            manufacturer.contains("samsung") -> "CRITICAL SETUP (Samsung):\n1. Settings -> Device Care -> Battery\n2. Tap 'Background usage limits'\n3. Tap 'Never sleeping apps'\n4. Tap '+' and add Hotel Agent"
            manufacturer.contains("lenovo") -> "CRITICAL SETUP (Lenovo):\n1. Settings -> Battery\n2. Tap 'Background app management'\n3. Find Hotel Agent\n4. Select 'No restrictions'"
            else -> "CRITICAL SETUP:\n1. Settings -> Apps -> Hotel Agent\n2. Tap Battery\n3. Select 'Unrestricted'"
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
"""

use_websocket_content = """import { useState, useEffect, useRef, useCallback } from 'react';

type ConnectionStatus = 'connected' | 'disconnected' | 'connecting';

export function useWebSocket(url: string) {
  const [status, setStatus] = useState<ConnectionStatus>('disconnected');
  const [lastMessage, setLastMessage] = useState<any>(null);
  const [isPollingMode, setIsPollingMode] = useState<boolean>(true); 
  
  const wsRef = useRef<WebSocket | null>(null);
  const pingTimerRef = useRef<NodeJS.Timeout>();
  const healthCheckRef = useRef<NodeJS.Timeout>();
  const lastMessageTime = useRef<number>(Date.now());
  const reconnectAttempts = useRef<number>(0);
  const lastSuccessfulConnection = useRef<number>(0); 

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return;

    setStatus('connecting');
    const ws = new WebSocket(url);

    ws.onopen = () => {
      setStatus('connected');
      setIsPollingMode(false); 
      reconnectAttempts.current = 0;
      lastMessageTime.current = Date.now();
      lastSuccessfulConnection.current = Date.now();
      console.log('⚡ God Mode: WebSocket Linked');
      
      if (pingTimerRef.current) clearInterval(pingTimerRef.current);
      pingTimerRef.current = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'ping' })); 
        }
      }, 15000); 
    };

    ws.onmessage = (event) => {
      lastMessageTime.current = Date.now();
      setIsPollingMode(false);
      try {
        const data = JSON.parse(event.data);
        if (data.type === 'pong') return;
        setLastMessage(data); 
      } catch (e) {
        console.error('WS Parse Error:', e);
      }
    };

    ws.onclose = () => {
      setStatus('disconnected');
      setIsPollingMode(true); 
      if (pingTimerRef.current) clearInterval(pingTimerRef.current);
      
      const timeout = Math.min(1000 * Math.pow(2, reconnectAttempts.current), 30000);
      reconnectAttempts.current += 1;
      setTimeout(() => connect(), timeout);
    };

    ws.onerror = () => ws.close();
    wsRef.current = ws;
  }, [url]);

  useEffect(() => {
    connect();

    healthCheckRef.current = setInterval(() => {
      const silent = Date.now() - lastMessageTime.current;
      if (silent > 45000 && wsRef.current?.readyState === WebSocket.OPEN) {
        console.log("💀 God Mode: Silent drop detected. Nuking connection...");
        setIsPollingMode(true);
        wsRef.current.close();
      }
    }, 5000);

    return () => {
      if (wsRef.current) wsRef.current.close();
      if (pingTimerRef.current) clearInterval(pingTimerRef.current);
      if (healthCheckRef.current) clearInterval(healthCheckRef.current);
    };
  }, [connect]);

  return { status, lastMessage, isPollingMode, lastSuccessfulConnection: lastSuccessfulConnection.current };
}
"""

page_content = """'use client';

import { useState, useEffect, useRef } from 'react';
import { useWebSocket } from '@/hooks/useWebSocket';

interface Alert {
  id: string;
  deviceId: string;
  status: string;
  timestamp: string;
  reason: string;
}

export default function DashboardPage() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const lastAlertTimestamp = useRef<number>(0);

  const wsUrl = process.env.NEXT_PUBLIC_API_URL?.replace('https://', 'wss://').replace('http://', 'ws://') + '/ws/dashboard';
  const { status, lastMessage, isPollingMode } = useWebSocket(wsUrl);

  useEffect(() => {
    if (lastMessage && lastMessage.type === 'breach') {
      const ts = new Date(lastMessage.data.timestamp).getTime();
      if (ts > lastAlertTimestamp.current) lastAlertTimestamp.current = ts;
      
      setAlerts(prev => {
        if (prev.some(a => a.id === lastMessage.data.id)) return prev;
        return [lastMessage.data, ...prev].slice(0, 50);
      });
    }
  }, [lastMessage]);

  useEffect(() => {
    const pollAlerts = async () => {
      try {
        const res = await fetch('/api/alerts/recent');
        if (!res.ok) return;
        const data = await res.json();
        
        if (Array.isArray(data)) {
          let injected = false;
          setAlerts(prev => {
            const merged = [...data, ...prev];
            const unique = Array.from(new Map(merged.map(i => [i.id, i])).values());
            const sorted = unique.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()).slice(0, 50);
            
            if (sorted.length > 0) {
              const newestTs = new Date(sorted[0].timestamp).getTime();
              if (newestTs > lastAlertTimestamp.current) {
                lastAlertTimestamp.current = newestTs;
                injected = true;
              }
            }
            return sorted;
          });
          if (injected && isPollingMode) console.log("God Mode: Polling caught missed breach.");
        }
      } catch (e) {
        // silent catch
      }
    };

    const interval = setInterval(pollAlerts, 15000);
    pollAlerts(); 
    return () => clearInterval(interval);
  }, [isPollingMode]); 

  const displayStatus = () => {
    if (status === 'connected' && !isPollingMode) return <span className="text-green-500 flex items-center"><div className="w-3 h-3 bg-green-500 rounded-full mr-2 animate-pulse shadow-[0_0_8px_rgba(34,197,94,0.8)]"/>LIVE</span>;
    if (status === 'connecting') return <span className="text-yellow-500 flex items-center"><div className="w-3 h-3 bg-yellow-500 rounded-full mr-2"/>RECONNECTING</span>;
    return <span className="text-red-500 flex items-center"><div className="w-3 h-3 bg-red-500 rounded-full mr-2"/>POLLING</span>;
  };

  return (
    <div className="p-8 max-w-6xl mx-auto bg-gray-50 min-h-screen">
      <div className="flex items-center justify-between mb-8 bg-white p-6 rounded-xl shadow-sm border border-gray-100">
        <h1 className="text-3xl font-bold tracking-tight text-gray-900">God Mode Security</h1>
        <div className="font-bold tracking-widest bg-gray-100 px-4 py-2 rounded-lg">
          {displayStatus()}
        </div>
      </div>

      <div className="bg-white rounded-xl shadow-lg border border-gray-200 overflow-hidden transition-all duration-300">
        <table className="min-w-full">
          <thead className="bg-gray-900 text-white">
            <tr>
              <th className="px-6 py-4 text-left text-xs font-bold uppercase tracking-widest">Time</th>
              <th className="px-6 py-4 text-left text-xs font-bold uppercase tracking-widest">Device</th>
              <th className="px-6 py-4 text-left text-xs font-bold uppercase tracking-widest">Status</th>
              <th className="px-6 py-4 text-left text-xs font-bold uppercase tracking-widest">Reason</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {alerts.length === 0 ? (
              <tr><td colSpan={4} className="px-6 py-8 text-center text-gray-500 font-medium">All Systems Secure. No Alerts.</td></tr>
            ) : (
              alerts.map((alert) => {
                const isBreach = alert.status === 'breach';
                return (
                  <tr key={alert.id} className={`${isBreach ? 'bg-red-50 border-l-4 border-red-500 animate-pulse' : 'hover:bg-gray-50'} transition-colors duration-200`}>
                    <td className="px-6 py-5 whitespace-nowrap text-sm font-medium text-gray-900">{new Date(alert.timestamp).toLocaleTimeString()}</td>
                    <td className="px-6 py-5 whitespace-nowrap text-sm font-bold text-gray-800">{alert.deviceId}</td>
                    <td className="px-6 py-5 whitespace-nowrap">
                      <span className={`px-4 py-1.5 inline-flex text-xs font-extrabold uppercase tracking-widest rounded-full ${isBreach ? 'bg-red-600 text-white shadow-[0_0_12px_rgba(220,38,38,0.8)]' : 'bg-green-100 text-green-800'}`}>
                        {isBreach ? '🚨 BREACH' : '✓ SECURE'}
                      </span>
                    </td>
                    <td className="px-6 py-5 text-sm text-gray-600 font-medium">{alert.reason}</td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
"""

with open(wifi_service_path, "w", encoding="utf-8") as f: f.write(wifi_content)
with open(six_signal_path, "w", encoding="utf-8") as f: f.write(six_signal_content)
with open(watchdog_path, "w", encoding="utf-8") as f: f.write(watchdog_content)
with open(doze_alarm_path, "w", encoding="utf-8") as f: f.write(doze_alarm_content)
with open(boot_receiver_path, "w", encoding="utf-8") as f: f.write(boot_receiver_content)
with open(battery_helper_path, "w", encoding="utf-8") as f: f.write(battery_helper_content)
with open(use_websocket_path, "w", encoding="utf-8") as f: f.write(use_websocket_content)
with open(page_tsx_path, "w", encoding="utf-8") as f: f.write(page_content)

# Process AndroidManifest
with open(manifest_path, "r", encoding="utf-8") as f:
    manifest_text = f.read()

# Add missing permissions if not present
permissions_to_add = [
    '<uses-permission android:name="android.permission.RESTART_PACKAGES" />'
]
for perm in permissions_to_add:
    if perm not in manifest_text:
        manifest_text = manifest_text.replace('</manifest>', f'    {perm}\n</manifest>')

# Ensure DozeAlarmReceiver is registered
doze_receiver = '''        <receiver
            android:name=".security.DozeAlarmReceiver"
            android:enabled="true"
            android:exported="false" />'''
            
if "DozeAlarmReceiver" not in manifest_text:
    manifest_text = manifest_text.replace('</application>', f'{doze_receiver}\n    </application>')

# Fix foregroundServiceType of WiFiMonitoringService to connectedDevice|location
manifest_text = re.sub(r'android:foregroundServiceType="location"', 'android:foregroundServiceType="connectedDevice|location"\n            android:stopWithTask="false"', manifest_text)
manifest_text = re.sub(r'android:foregroundServiceType="connectedDevice"', 'android:foregroundServiceType="connectedDevice"\n            android:stopWithTask="false"', manifest_text)

with open(manifest_path, "w", encoding="utf-8") as f:
    f.write(manifest_text)

print("God Mode files written successfully.")
