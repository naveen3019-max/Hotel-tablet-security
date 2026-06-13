import os

base_dir = r"c:\Users\navee\Downloads\Hotel-tablet-security-master\Hotel-tablet-security-master\WEDDING-CARD-cc895524abaddd4e0e79cc06099f9f102c0f16c7"

wifi_service_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\security\WiFiMonitoringService.kt")
six_signal_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\security\SixSignalMonitor.kt")
watchdog_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\security\WatchdogService.kt")
use_websocket_path = os.path.join(base_dir, r"dashboard\src\hooks\useWebSocket.ts")
page_tsx_path = os.path.join(base_dir, r"dashboard\src\app\page.tsx")
live_indicator_path = os.path.join(base_dir, r"dashboard\src\components\LiveIndicator.tsx")

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
    private var wakeLock: PowerManager.WakeLock? = null
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

    override fun onCreate() {
        super.onCreate()
        instance = this
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        createNotificationChannel()
        setManufacturerOptimizations()
    }

    private fun setManufacturerOptimizations() {
        val manufacturer = Build.MANUFACTURER.lowercase()
        when {
            manufacturer.contains("samsung") -> {
                Log.d(TAG, "Samsung optimization bypass active")
                startForeground(NOTIFICATION_ID, buildNotification(NotificationCompat.PRIORITY_MAX))
            }
            manufacturer.contains("lenovo") -> {
                Log.d(TAG, "Lenovo optimization bypass active")
                startForeground(NOTIFICATION_ID, buildNotification(NotificationCompat.PRIORITY_HIGH))
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification(NotificationCompat.PRIORITY_DEFAULT))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "DOZE_ALARM") {
            Log.d(TAG, "DOZE_ALARM fired! Re-acquiring WakeLock and verifying monitoring.")
            acquireWakeLockSafely()
            scheduleDozeAlarm()
            if (!sixSignalMonitor.isMonitoringAlive()) {
                Log.w(TAG, "Monitoring was paused by Doze! Restarting...")
                sixSignalMonitor.startMonitoring()
            }
            return START_STICKY
        }

        if (!isServiceRunning) {
            Log.i(TAG, "Starting WiFiMonitoringService...")
            isRunning = true
            isServiceRunning = true
            
            acquireWakeLockSafely()
            scheduleDozeAlarm()
            sixSignalMonitor.startMonitoring()
        }
        
        return START_STICKY
    }

    private fun scheduleDozeAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, WiFiMonitoringService::class.java).apply {
            action = "DOZE_ALARM"
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 55_000L,
                pendingIntent
            )
        }
    }

    private fun acquireWakeLockSafely() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HotelSecurity:WakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Security Monitoring",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(priority: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security System Active")
            .setContentText("Device is aggressively monitored.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .setPriority(priority)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        sixSignalMonitor.stopMonitoring()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        isRunning = false
        isServiceRunning = false
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
"""

six_signal_content = """package com.example.hotel.security

import android.content.Context
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
        private const val CHECK_INTERVAL = 3000L
    }

    private val monitorRunnable = object : Runnable {
        override fun run() {
            try {
                val now = SystemClock.elapsedRealtime()
                
                if (lastCheckTime > 0 && (now - lastCheckTime) > 30000L) {
                    Log.w(TAG, "Monitoring was paused by Doze! Gap: ${now - lastCheckTime}ms")
                }
                
                lastCheckTime = now
                performSecurityCheck()
            } catch (e: Exception) {
                Log.e(TAG, "Error in monitoring loop", e)
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
            Log.i(TAG, "SixSignalMonitor started")
        }
    }

    fun stopMonitoring() {
        isRunning = false
        handler.removeCallbacks(monitorRunnable)
        Log.i(TAG, "SixSignalMonitor stopped")
    }

    fun isMonitoringAlive(): Boolean {
        if (!isRunning) return false
        val timeSinceLastCheck = SystemClock.elapsedRealtime() - lastCheckTime
        return timeSinceLastCheck < 10000L
    }

    fun setInterval(interval: Long) {
        // Handle interval change if needed
    }

    fun triggerBreach(reason: String) {
        // Implementation for trigger breach
    }

    private fun performSecurityCheck() {
        // Implementation for security check
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
        private const val WATCHDOG_INTERVAL_MS = 15_000L 
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
        if (!WiFiMonitoringService.isRunning) {
            Log.e(TAG, "Monitoring service completely dead! Restarting immediately...")
            val restartIntent = Intent(this, WiFiMonitoringService::class.java)
            val manufacturer = Build.MANUFACTURER.lowercase()
            
            if (manufacturer.contains("samsung")) {
                restartIntent.setPackage(packageName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent)
            } else {
                startService(restartIntent)
            }
            if (manufacturer.contains("lenovo")) {
                sendBroadcast(Intent("com.example.hotel.security.RESTART_MONITORING"))
            }
        }
    }

    private fun scheduleWatchdogAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(this, WatchdogService::class.java).apply {
            action = "WATCHDOG_ALARM"
        }
        val pendingIntent = PendingIntent.getService(
            this,
            1,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 120_000L,
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

use_websocket_content = """import { useState, useEffect, useRef, useCallback } from 'react';

type ConnectionStatus = 'connected' | 'disconnected' | 'connecting';

export function useWebSocket(url: string) {
  const [status, setStatus] = useState<ConnectionStatus>('disconnected');
  const [lastMessage, setLastMessage] = useState<any>(null);
  const [isLikelySilentDisconnect, setIsLikelySilentDisconnect] = useState<boolean>(false);
  
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimerRef = useRef<NodeJS.Timeout>();
  const lastMessageTime = useRef<number>(Date.now());
  const healthCheckIntervalRef = useRef<NodeJS.Timeout>();
  const reconnectAttempts = useRef<number>(0);

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return;

    setStatus('connecting');
    const ws = new WebSocket(url);

    ws.onopen = () => {
      setStatus('connected');
      setIsLikelySilentDisconnect(false);
      reconnectAttempts.current = 0;
      lastMessageTime.current = Date.now();
      console.log('[WS] Connected to dashboard stream');
      
      if (reconnectTimerRef.current) clearInterval(reconnectTimerRef.current);
      reconnectTimerRef.current = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'ping' }));
        }
      }, 30000);
    };

    ws.onmessage = (event) => {
      lastMessageTime.current = Date.now();
      setIsLikelySilentDisconnect(false);
      try {
        const data = JSON.parse(event.data);
        if (data.type === 'pong') return;
        setLastMessage(data);
      } catch (e) {
        console.error('WebSocket parse error:', e);
      }
    };

    ws.onclose = () => {
      setStatus('disconnected');
      if (reconnectTimerRef.current) clearInterval(reconnectTimerRef.current);
      
      const timeout = Math.min(1000 * Math.pow(2, reconnectAttempts.current), 30000);
      reconnectAttempts.current += 1;
      setTimeout(() => connect(), timeout);
    };

    ws.onerror = (error) => {
      console.error('WebSocket error:', error);
      ws.close();
    };

    wsRef.current = ws;
  }, [url]);

  useEffect(() => {
    connect();

    healthCheckIntervalRef.current = setInterval(() => {
      const silent = Date.now() - lastMessageTime.current;
      if (silent > 45000 && wsRef.current?.readyState === WebSocket.OPEN) {
        console.log("🔄 Silent disconnect! Force reconnecting...");
        setIsLikelySilentDisconnect(true);
        wsRef.current.close();
      }
    }, 5000);

    return () => {
      if (wsRef.current) wsRef.current.close();
      if (reconnectTimerRef.current) clearInterval(reconnectTimerRef.current);
      if (healthCheckIntervalRef.current) clearInterval(healthCheckIntervalRef.current);
    };
  }, [connect]);

  return { status, lastMessage, isLikelySilentDisconnect };
}
"""

page_content = """'use client';

import { useState, useEffect, useRef } from 'react';
import { useWebSocket } from '@/hooks/useWebSocket';
import LiveIndicator from '@/components/LiveIndicator';

interface Alert {
  id: string;
  deviceId: string;
  status: string;
  timestamp: string;
  reason: string;
}

export default function DashboardPage() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const lastFetchTimeRef = useRef<number>(Date.now());

  const { status, lastMessage, isLikelySilentDisconnect } = useWebSocket(
    process.env.NEXT_PUBLIC_API_URL?.replace('https://', 'wss://').replace('http://', 'ws://') + '/ws/dashboard'
  );

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
        
        if (status === 'connected' && !isLikelySilentDisconnect && timeSinceLastWsMessage < 20000) {
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
  }, [status, isLikelySilentDisconnect]);

  const getConnectionState = () => {
    if (status === 'connected' && !isLikelySilentDisconnect) return 'connected';
    if (status === 'connecting') return 'connecting';
    return 'disconnected';
  };

  return (
    <div className="p-8 max-w-6xl mx-auto">
      <div className="flex items-center justify-between mb-8">
        <h1 className="text-3xl font-bold">Security Dashboard</h1>
        <div className="flex items-center">
          <span className="text-gray-500 text-sm mr-2">Status:</span>
          <LiveIndicator status={getConnectionState()} />
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
                <tr key={alert.id} className={alert.status === 'breach' ? 'bg-red-50 animate-pulse' : ''}>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {new Date(alert.timestamp).toLocaleTimeString()}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                    {alert.deviceId}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm">
                    <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
                      alert.status === 'breach' ? 'bg-red-500 text-white shadow-[0_0_10px_rgba(239,68,68,0.7)]' : 'bg-green-100 text-green-800'
                    }`}>
                      {alert.status === 'breach' ? '🔴 BREACH' : '🟢 SECURE'}
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

live_indicator_content = """import React from 'react';

interface LiveIndicatorProps {
  status: 'connected' | 'connecting' | 'disconnected';
}

export default function LiveIndicator({ status }: LiveIndicatorProps) {
  if (status === 'connected') {
    return (
      <div className="flex items-center text-green-500 font-bold ml-4">
        <span className="w-3 h-3 bg-green-500 rounded-full mr-2 animate-pulse shadow-[0_0_8px_rgba(34,197,94,0.8)]"></span>
        LIVE
      </div>
    );
  }
  
  if (status === 'connecting') {
    return (
      <div className="flex items-center text-yellow-500 font-bold ml-4">
        <span className="w-3 h-3 bg-yellow-500 rounded-full mr-2"></span>
        RECONNECTING
      </div>
    );
  }

  return (
    <div className="flex items-center text-red-500 font-bold ml-4">
      <span className="w-3 h-3 bg-red-500 rounded-full mr-2"></span>
      POLLING MODE
    </div>
  );
}
"""

with open(wifi_service_path, "w", encoding="utf-8") as f: f.write(wifi_content)
with open(six_signal_path, "w", encoding="utf-8") as f: f.write(six_signal_content)
with open(watchdog_path, "w", encoding="utf-8") as f: f.write(watchdog_content)

if os.path.exists(os.path.dirname(use_websocket_path)):
    with open(use_websocket_path, "w", encoding="utf-8") as f: f.write(use_websocket_content)
if os.path.exists(os.path.dirname(page_tsx_path)):
    with open(page_tsx_path, "w", encoding="utf-8") as f: f.write(page_content)
if os.path.exists(os.path.dirname(live_indicator_path)):
    with open(live_indicator_path, "w", encoding="utf-8") as f: f.write(live_indicator_content)
else:
    os.makedirs(os.path.dirname(live_indicator_path), exist_ok=True)
    with open(live_indicator_path, "w", encoding="utf-8") as f: f.write(live_indicator_content)

print("Files written successfully")
