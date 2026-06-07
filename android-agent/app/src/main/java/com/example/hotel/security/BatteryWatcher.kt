package com.example.hotel.security

import android.content.*
import android.os.BatteryManager
import android.util.Log

class BatteryWatcher(
    private val ctx: Context,
    private val onLow: (Int) -> Unit
) {

    private var receiver: BroadcastReceiver? = null
    private var lastKnownLevel: Int = 100
    private var lowBatteryAlertSent: Boolean = false

    fun start(threshold: Int = 20) {
        if (receiver != null) return

        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                i ?: return

                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                status == BatteryManager.BATTERY_STATUS_FULL

                if (level < 0 || scale <= 0) return

                val pct = (level * 100) / scale
                val previousLevel = lastKnownLevel
                lastKnownLevel = pct

                // Log all battery changes
                val chargingStatus = if (isCharging) "🔌 CHARGING" else "🔋 DISCHARGING"
                Log.i("BatteryWatcher", "🔋 Battery Level: $pct% | $chargingStatus | Threshold: $threshold%")
 
                if (pct <= threshold && pct != previousLevel) {
                    if (!lowBatteryAlertSent || previousLevel > threshold) {
                        Log.e("BatteryWatcher", "⚠️⚠️⚠️ LOW BATTERY ALERT: $pct% (Threshold: $threshold%) ⚠️⚠️⚠️")
                        lowBatteryAlertSent = true
                    }
                    onLow(pct)
                } else if (pct > threshold && lowBatteryAlertSent) {
                    Log.i("BatteryWatcher", "✅ Battery recovered: $pct% (Above threshold)")
                    lowBatteryAlertSent = false
                }
            }
        }

        ctx.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        
        Log.i("BatteryWatcher", "🔋 Battery monitoring started with threshold: $threshold%")
    }

    fun stop() {
        receiver?.let {
            ctx.unregisterReceiver(it)
            Log.i("BatteryWatcher", "🔋 Battery monitoring stopped")
        }
        receiver = null
    }

    /** Latest cached battery percentage (for heartbeat) */
    fun getCurrentLevel(): Int {
        return lastKnownLevel
    }
}
