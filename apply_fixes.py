import os

base_dir = r"c:\Users\navee\Downloads\Hotel-tablet-security-master\Hotel-tablet-security-master\WEDDING-CARD-cc895524abaddd4e0e79cc06099f9f102c0f16c7"

wifi_service_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\security\WiFiMonitoringService.kt")
watchdog_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\security\WatchdogService.kt")
battery_helper_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\security\BatteryOptimizationHelper.kt")
boot_receiver_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\security\BootReceiver.kt")
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
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat

class WiFiMonitoringService : Service() {

    private lateinit var wifiManager: WifiManager
    private lateinit var powerManager: PowerManager
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenAndWiFiReceiver: ScreenAndWiFiReceiver? = null
    
    private val sixSignalMonitor = SixSignalMonitor(this)
    private var isServiceRunning = false

    companion object {
        private const val TAG = "WiFiMonitoringService"
        private const val CHANNEL_ID = "wifi_security_channel"
        private const val NOTIFICATION_ID = 1001
        
        @Volatile
        var isRunning: Boolean = false
        
        var instance: WiFiMonitoringService? = null
            private set

        fun setMonitoringInterval(interval: Long, reason: String) {
            Log.d(TAG, "Changing interval to ${interval}ms due to: $reason")
            instance?.sixSignalMonitor?.setInterval(interval)
        }

        fun triggerBreachAlert(reason: String) {
            Log.e(TAG, "BREACH TRIGGERED: $reason")
            instance?.sixSignalMonitor?.triggerBreach(reason)
        }

        fun onNetworkLost() {
            Log.e(TAG, "Network connection lost!")
            triggerBreachAlert("ConnectivityAction: Network Lost")
        }
    }

    fun getManufacturer(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("samsung") -> "samsung"
            manufacturer.contains("lenovo") -> "lenovo"
            else -> "generic"
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        createNotificationChannel()

        val manufacturer = getManufacturer()
        if (manufacturer == "samsung") {
            startForeground(NOTIFICATION_ID, createHighPriorityNotification())
            Log.d(TAG, "Samsung optimization bypass active")
        }
        if (manufacturer == "lenovo") {
            Log.d(TAG, "Lenovo specific power management bypass active")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isServiceRunning) {
            Log.d(TAG, "Service is already running. Ignoring start command.")
            return START_STICKY
        }
        
        Log.i(TAG, "Starting WiFiMonitoringService...")
        startForeground(NOTIFICATION_ID, createNotification())
        
        isRunning = true
        isServiceRunning = true

        acquireLocks()
        registerDynamicReceivers()
        requestDozeExemption() 
        scheduleDozeAlarm()    
        
        sixSignalMonitor.startMonitoring()
        
        return START_STICKY
    }

    private fun requestDozeExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.w(TAG, "Doze exemption not granted! Prompting user...")
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
        }
    }

    private fun scheduleDozeAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(this, WiFiMonitoringService::class.java)
        val pendingIntent = PendingIntent.getService(
            this,
            0,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 60_000L,
                pendingIntent
            )
            Log.d(TAG, "Scheduled next Doze wakeup alarm in 60s")
        }
    }

    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "HotelSecurity:WakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
                Log.d(TAG, "WakeLock acquired permanently")
            }

            if (wifiLock == null) {
                wifiLock = wifiManager.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "HotelSecurity:WifiLock"
                )
                wifiLock?.setReferenceCounted(false)
                wifiLock?.acquire()
                Log.d(TAG, "WifiLock (HIGH_PERF) acquired permanently")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire locks: ${e.message}", e)
        }
    }

    private fun registerDynamicReceivers() {
        if (screenAndWiFiReceiver == null) {
            screenAndWiFiReceiver = ScreenAndWiFiReceiver()
            screenAndWiFiReceiver?.register(this)
            Log.d(TAG, "Dynamic receivers registered")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Security Monitoring",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Monitors device security and location"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security System Active")
            .setContentText("Device is being monitored.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }
    
    private fun createHighPriorityNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security System Active (Samsung)")
            .setContentText("Device is being aggressively monitored.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "Service being destroyed! Releasing resources...")
        
        sixSignalMonitor.stopMonitoring()
        screenAndWiFiReceiver?.unregister(this)
        screenAndWiFiReceiver = null
        
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
            wakeLock = null
            wifiLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing locks", e)
        }
        
        isServiceRunning = false
        isRunning = false
        instance = null
        
        val restartIntent = Intent(this, WiFiMonitoringService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent)
            } else {
                startService(restartIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart service in onDestroy: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
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
import android.util.Log

class WatchdogService : Service() {

    companion object {
        private const val TAG = "WatchdogService"
        private const val WATCHDOG_INTERVAL_MS = 20_000L 
    }

    private val handler = Handler(Looper.getMainLooper())
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            checkAndRestartMonitoringService()
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "WatchdogService Created")
        scheduleWatchdogAlarm()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "WatchdogService Started")
        handler.post(watchdogRunnable)
        return START_STICKY
    }

    private fun checkAndRestartMonitoringService() {
        if (!WiFiMonitoringService.isRunning) {
            Log.e(TAG, "Monitoring service died! Restarting immediately...")
            val intent = Intent(this, WiFiMonitoringService::class.java)
            val manufacturer = Build.MANUFACTURER.lowercase()
            
            try {
                if (manufacturer.contains("samsung")) {
                    intent.setPackage(packageName) 
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                
                if (manufacturer.contains("lenovo")) {
                    val broadcastIntent = Intent("com.example.hotel.security.RESTART_MONITORING")
                    sendBroadcast(broadcastIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart WiFiMonitoringService: ${e.message}")
            }
        }
    }

    private fun scheduleWatchdogAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(this, WatchdogService::class.java)
        val pendingIntent = PendingIntent.getService(
            this,
            1,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + WATCHDOG_INTERVAL_MS,
                pendingIntent
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(watchdogRunnable)
        val restartIntent = Intent(this, WatchdogService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
"""

battery_content = """package com.example.hotel.security

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
                message = "SAMSUNG TABLET DETECTED:\\n\\n1. Tap 'Settings' below.\\n2. Open 'Device Care' -> 'Battery'.\\n3. Tap 'App Power Management'.\\n4. Add this app to 'Apps that won't be put to sleep' exceptions."
                intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            }
            manufacturer.contains("lenovo") -> {
                message = "LENOVO TABLET DETECTED:\\n\\n1. Tap 'Settings' below.\\n2. Open 'Battery'.\\n3. Tap 'Background app management'.\\n4. Find this app and set it to 'No Restrictions'."
                intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            }
            else -> {
                message = "ANDROID TABLET DETECTED:\\n\\n1. Tap 'Settings' below.\\n2. Tap 'All Apps'.\\n3. Find this app and set it to 'Don't optimize'."
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
"""

boot_content = """package com.example.hotel.security

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
"""

manifest_content = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.hotel">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="com.samsung.android.providers.context.permission.READ_SETTINGS" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.HotelSecurity">
        
        <activity
            android:name=".admin.ProvisioningActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".security.WiFiMonitoringService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="connectedDevice" />
            
        <service
            android:name=".security.WatchdogService"
            android:enabled="true"
            android:exported="false" />

        <receiver
            android:name=".security.BootReceiver"
            android:enabled="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.QUICKBOOT_POWERON" />
                <action android:name="com.htc.intent.action.QUICKBOOT_POWERON" />
                <action android:name="com.lenovo.sleepmode.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
        
        <receiver android:name=".security.ScreenAndWiFiReceiver"
                  android:enabled="true"
                  android:exported="false" />
    </application>
</manifest>
"""

use_websocket_content = """import { useState, useEffect, useRef, useCallback } from 'react';

type ConnectionStatus = 'connected' | 'disconnected' | 'connecting';

export function useWebSocket(url: string) {
  const [status, setStatus] = useState<ConnectionStatus>('disconnected');
  const [lastMessage, setLastMessage] = useState<any>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimerRef = useRef<NodeJS.Timeout>();
  const lastMessageTime = useRef<number>(Date.now());
  const healthCheckIntervalRef = useRef<NodeJS.Timeout>();

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return;

    setStatus('connecting');
    const ws = new WebSocket(url);

    ws.onopen = () => {
      setStatus('connected');
      lastMessageTime.current = Date.now();
      console.log('WebSocket connected');
      
      if (reconnectTimerRef.current) clearInterval(reconnectTimerRef.current);
      reconnectTimerRef.current = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'ping' }));
        }
      }, 30000);
    };

    ws.onmessage = (event) => {
      lastMessageTime.current = Date.now();
      try {
        const data = JSON.parse(event.data);
        if (data.type === 'pong') {
          return;
        }
        setLastMessage(data);
      } catch (e) {
        console.error('WebSocket parse error:', e);
      }
    };

    ws.onclose = () => {
      setStatus('disconnected');
      if (reconnectTimerRef.current) clearInterval(reconnectTimerRef.current);
    };

    ws.onerror = (error) => {
      console.error('WebSocket error:', error);
      ws.close();
    };

    wsRef.current = ws;
  }, [url]);

  const forceReconnect = useCallback(() => {
    console.log('🔄 Force reconnecting...');
    setStatus('disconnected');
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }
    setTimeout(() => {
      connect();
    }, 1000);
  }, [connect]);

  useEffect(() => {
    connect();

    healthCheckIntervalRef.current = setInterval(() => {
      const timeSinceLastMessage = Date.now() - lastMessageTime.current;
      if (timeSinceLastMessage > 60000) {
        console.warn('Silent disconnect detected! No message in 60s.');
        forceReconnect();
      }
    }, 5000);

    return () => {
      if (wsRef.current) wsRef.current.close();
      if (reconnectTimerRef.current) clearInterval(reconnectTimerRef.current);
      if (healthCheckIntervalRef.current) clearInterval(healthCheckIntervalRef.current);
    };
  }, [connect, forceReconnect]);

  return { status, lastMessage };
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
  const { status, lastMessage } = useWebSocket('wss://hotel-tablet-security.onrender.com/ws/dashboard');
  const lastFetchTimeRef = useRef<number>(Date.now());

  useEffect(() => {
    if (lastMessage && lastMessage.type === 'breach') {
      lastFetchTimeRef.current = Date.now();
      setAlerts(prev => {
        const exists = prev.some(a => a.id === lastMessage.data.id);
        if (exists) return prev;
        return [lastMessage.data, ...prev].slice(0, 50);
      });
    }
  }, [lastMessage]);

  useEffect(() => {
    const pollAlerts = async () => {
      try {
        const timeSinceLastWsMessage = Date.now() - lastFetchTimeRef.current;
        if (status === 'connected' && timeSinceLastWsMessage < 20000) {
          return;
        }

        const res = await fetch('/api/alerts/recent');
        if (!res.ok) throw new Error('Poll failed');
        const data = await res.json();
        
        if (data && Array.isArray(data)) {
          lastFetchTimeRef.current = Date.now();
          setAlerts(prev => {
            const merged = [...data, ...prev];
            const unique = Array.from(new Map(merged.map(item => [item.id, item])).values());
            return unique.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()).slice(0, 50);
          });
        }
      } catch (err) {
        console.error('Backup polling error:', err);
      }
    };

    const interval = setInterval(pollAlerts, 15000);
    pollAlerts();

    return () => clearInterval(interval);
  }, [status]);

  const getDataSourceIndicator = () => {
    if (status === 'connected') return <span className="text-green-500 font-bold ml-4">● LIVE</span>;
    if (status === 'connecting') return <span className="text-yellow-500 font-bold ml-4">● CONNECTING...</span>;
    return <span className="text-orange-500 font-bold ml-4">● POLLING (FALLBACK)</span>;
  };

  return (
    <div className="p-8 max-w-6xl mx-auto">
      <div className="flex items-center justify-between mb-8">
        <h1 className="text-3xl font-bold">Security Dashboard</h1>
        <div className="flex items-center">
          <span className="text-gray-500 text-sm mr-2">Status:</span>
          {getDataSourceIndicator()}
        </div>
      </div>

      <div className="bg-white rounded-lg shadow overflow-hidden">
        <table className="min-w-full">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Time</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Device</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Reason</th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {alerts.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-6 py-4 text-center text-gray-500">No recent alerts</td>
              </tr>
            ) : (
              alerts.map((alert) => (
                <tr key={alert.id} className={alert.status === 'breach' ? 'bg-red-50' : ''}>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {new Date(alert.timestamp).toLocaleTimeString()}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                    {alert.deviceId}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm">
                    <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
                      alert.status === 'breach' ? 'bg-red-100 text-red-800' : 'bg-green-100 text-green-800'
                    }`}>
                      {alert.status.toUpperCase()}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {alert.reason}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
"""

with open(wifi_service_path, "w", encoding="utf-8") as f: f.write(wifi_content)
with open(watchdog_path, "w", encoding="utf-8") as f: f.write(watchdog_content)
with open(battery_helper_path, "w", encoding="utf-8") as f: f.write(battery_content)
with open(boot_receiver_path, "w", encoding="utf-8") as f: f.write(boot_content)
with open(manifest_path, "w", encoding="utf-8") as f: f.write(manifest_content)
if os.path.exists(os.path.dirname(use_websocket_path)):
    with open(use_websocket_path, "w", encoding="utf-8") as f: f.write(use_websocket_content)
if os.path.exists(os.path.dirname(page_tsx_path)):
    with open(page_tsx_path, "w", encoding="utf-8") as f: f.write(page_content)

print("Files written successfully")
