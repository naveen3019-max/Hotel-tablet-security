import os
import re

base_dir = r"c:\Users\navee\Downloads\Hotel-tablet-security-master\Hotel-tablet-security-master\WEDDING-CARD-cc895524abaddd4e0e79cc06099f9f102c0f16c7"

six_monitor_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\security\SixSignalMonitor.kt")
wifi_service_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\security\WiFiMonitoringService.kt")
kiosk_service_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\service\KioskService.kt")
main_py_path = os.path.join(base_dir, r"backend-api\main.py")

# WiFiMonitoringService.kt
with open(wifi_service_path, "r", encoding="utf-8") as f:
    wifi = f.read()

wifi_old = r'''    override fun onStartCommand\(intent: Intent\?, flags: Int, startId: Int\): Int \{
        Log\.d\(TAG, "onStartCommand action: \$\{intent\?\.action\}"\)
        
        if \(intent\?\.action == "WIFI_OFF_BREACH"\) \{
            val isImmediate = intent\.getBooleanExtra\("IMMEDIATE_BREACH", false\)
            Log\.w\(TAG, "🚨 Received WIFI_OFF_BREACH intent, forcing check, isImmediate=\$isImmediate"\)
            sixSignalMonitor\.forceImmediateCheck\(skipDelay = isImmediate\)
            return START_STICKY
        \}'''

wifi_new = '''    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action: ${intent?.action}")
        
        if (intent?.action == "WIFI_OFF_BREACH") {
            val isImmediate = intent.getBooleanExtra("IMMEDIATE_BREACH", false)
            Log.w(TAG, "🚨 Received WIFI_OFF_BREACH intent, forcing check, isImmediate=$isImmediate")
            
            // ← Acquire WakeLock for breach POST
            val pm = getSystemService(
                Context.POWER_SERVICE
            ) as android.os.PowerManager
            val wl = pm.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK,
                "HotelSecurity::BreachService"
            )
            wl.acquire(60_000L)
            
            try {
                sixSignalMonitor.forceImmediateCheck(
                    skipDelay = isImmediate)
            } finally {
                if (wl.isHeld) wl.release()
            }
            
            return START_STICKY
        }'''
wifi = re.sub(wifi_old, wifi_new, wifi)
with open(wifi_service_path, "w", encoding="utf-8") as f:
    f.write(wifi)

print("Done")
