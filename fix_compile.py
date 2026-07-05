import os
import re

base_dir = r"c:\Users\navee\Downloads\Hotel-tablet-security-master\Hotel-tablet-security-master\WEDDING-CARD-cc895524abaddd4e0e79cc06099f9f102c0f16c7"
monitor_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\security\SixSignalMonitor.kt")

with open(monitor_path, "r", encoding="utf-8") as f:
    monitor_code = f.read()

# Fix wifiLostTimestamp -> firstWifiLossTime
monitor_code = monitor_code.replace("wifiLostTimestamp = 0L", "firstWifiLossTime = 0L")

# Fix deviceId and roomId resolution in fireBreach
old_vars = '''        val deviceIdVal = deviceId ?: run {
            Log.e(TAG, "fireBreach: deviceId is null — aborting")
            return
        }
        val roomIdVal = roomId ?: run {
            Log.e(TAG, "fireBreach: roomId is null — aborting")
            return
        }'''

new_vars = '''        val prefs = context.getSharedPreferences("hotel_prefs", Context.MODE_PRIVATE)
        val deviceIdVal = prefs.getString("device_id", null) ?: run {
            Log.e(TAG, "fireBreach: deviceId is null — aborting")
            return
        }
        val roomIdVal = prefs.getString("room_id", null) ?: run {
            Log.e(TAG, "fireBreach: roomId is null — aborting")
            return
        }'''

monitor_code = monitor_code.replace(old_vars, new_vars)

with open(monitor_path, "w", encoding="utf-8") as f:
    f.write(monitor_code)

print("Compile fixes applied successfully.")
