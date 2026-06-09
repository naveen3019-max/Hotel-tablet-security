# 🔧 WiFi PIN Protection - Troubleshooting & Installation

## Current Status
✅ APK Built Successfully (2.65 MB)  
❌ Tablet Not Connected - Cannot Install Remotely

## 📱 Manual Installation Steps

### Option 1: USB Cable Installation (RECOMMENDED)

1. **Connect tablet to computer via USB cable**

2. **Enable USB Debugging on tablet**:
   - Settings → About Tablet → Tap "Build Number" 7 times
   - Settings → Developer Options → Enable "USB Debugging"

3. **Install APK**:
   ```powershell
   cd C:\Users\narra\OneDrive\Desktop\WEDDING-CARD-cc895524abaddd4e0e79cc06099f9f102c0f16c7\android-agent
   adb devices
   # Should show your device
   adb install -r app\build\outputs\apk\release\app-release-unsigned.apk
   ```

4. **Monitor logs while testing**:
   ```powershell
   adb logcat -s "WifiStateReceiver:*" "WifiPinDialog:*"
   ```

### Option 2: WiFi Installation (If Connected)

1. **Turn ON tablet WiFi**

2. **Find tablet IP address**:
   - Settings → About → Status → IP address
   - Or use: `adb shell ip addr show wlan0`

3. **Enable ADB over network** (if connected via USB first):
   ```powershell
   adb tcpip 5555
   adb connect <TABLET_IP>:5555
   ```

4. **Install APK**:
   ```powershell
   adb install -r app\build\outputs\apk\release\app-release-unsigned.apk
   ```

### Option 3: Manual File Transfer

1. Copy APK file to USB drive or use file sharing
2. On tablet, use File Manager to locate the APK
3. Tap the APK and install
4. Grant "Install from Unknown Sources" if prompted

## 🧪 Testing the PIN Protection

### What Should Happen:

1. **Try to turn OFF WiFi**:
   - Open Quick Settings and tap WiFi toggle
   - OR go to Settings → WiFi → Toggle OFF

2. **Expected Behavior**:
   ```
   ⏱️ 0ms   - User taps WiFi OFF
   ⏱️ 50ms  - System starts disabling WiFi
   ⏱️ 100ms - WifiStateReceiver intercepts WIFI_STATE_DISABLING
   ⏱️ 150ms - WiFi is turned back ON automatically
   ⏱️ 200ms - PIN dialog appears
   ```

3. **In the PIN Dialog**:
   - Enter: `1234` (default PIN)
   - Click "Confirm"
   - WiFi will NOW turn OFF (authorized)
   
   - OR click "Cancel" - WiFi stays ON

### What You'll See in Logs:

```
WifiStateReceiver: 🔔🔔🔔 BROADCAST RECEIVED!
WifiStateReceiver: Action: android.net.wifi.WIFI_STATE_CHANGED
WifiStateReceiver: 📶 Intent WiFi state: 0
WifiStateReceiver: 📶 Current WiFi state: 0  
WifiStateReceiver: 📶 WiFi enabled: false
WifiStateReceiver: ⚠️⚠️ WiFi DISABLING - INTERCEPTING!
WifiStateReceiver: 🚨 UNAUTHORIZED WiFi disable attempt - turning back ON!
WifiStateReceiver: ✅ WiFi turned back ON
WifiStateReceiver: 📱 PIN dialog launched
WifiPinDialog: WiFi PIN dialog displayed
```

When correct PIN entered:
```
WifiPinDialog: ✅ Correct PIN entered - allowing WiFi disable
WifiPinDialog: WiFi disable command sent
```

## ❓ Why PIN Dialog Might Not Show

### 1. **APK Not Installed Yet**
- Solution: Follow installation steps above

### 2. **Device Not Provisioned**
- The PIN protection only works on provisioned devices
- Check: Settings in app should show "Provisioned: Yes"
- Solution: Complete device provisioning first

### 3. **Permission Not Granted**
- WRITE_SETTINGS permission might not be granted
- Solution: Settings → Apps → Hotel Agent → Permissions → Allow all

### 4. **Receiver Not Registered**
- The BroadcastReceiver might not be active
- Check logs for: "WiFi state receiver REGISTERED!"
- Solution: Restart the app or reboot device

### 5. **Android Version Issue**
- Some Android versions handle WiFi differently
- Try toggling from Settings instead of Quick Settings

### 6. **WiFi Already OFF**
- If WiFi is already disabled when app starts
- Solution: Turn WiFi ON first, then try disabling

## 🔍 Debug Steps

### 1. Check if App is Running:
```powershell
adb shell dumpsys activity services | Select-String "KioskService"
```

### 2. Check if Receiver is Registered:
```powershell
adb logcat -d | Select-String "WiFi state receiver"
```

### 3. Force WiFi State Broadcast:
```powershell
adb shell am broadcast -a android.net.wifi.WIFI_STATE_CHANGED
```

### 4. Check Current WiFi State:
```powershell
adb shell dumpsys wifi | Select-String "Wi-Fi is"
```

### 5. View All Logs:
```powershell
adb logcat | Select-String "WiFi|wifi|PIN|WifiState"
```

## 🔑 Change Default PIN

Edit in the app or use ADB:
```powershell
adb shell "su 0 sh -c 'echo \"admin_pin=YOUR_PIN\" >> /data/data/com.example.hotel/shared_prefs/agent.xml'"
```

## 📦 Files Modified

- `WifiStateReceiver.kt` - Intercepts WiFi disable attempts
- `WifiPinDialog.kt` - PIN entry dialog
- `AndroidManifest.xml` - Added WRITE_SETTINGS permission, receiver priority
- APK: `app\build\outputs\apk\release\app-release-unsigned.apk` (2.65 MB)

## ✅ Next Steps

1. **Turn ON tablet WiFi** (so it's reachable)
2. **Connect via USB or WiFi** 
3. **Install the APK**
4. **Test by trying to disable WiFi**
5. **Watch for PIN dialog**

---

**Current APK Location**:  
`C:\Users\narra\OneDrive\Desktop\WEDDING-CARD-cc895524abaddd4e0e79cc06099f9f102c0f16c7\android-agent\app\build\outputs\apk\release\app-release-unsigned.apk`

**Quick Install Command** (when connected):
```powershell
cd C:\Users\narra\OneDrive\Desktop\WEDDING-CARD-cc895524abaddd4e0e79cc06099f9f102c0f16c7\android-agent
adb install -r app\build\outputs\apk\release\app-release-unsigned.apk
```
