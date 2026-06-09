# 🔐 Power Off PIN Protection - v2.2.0

**Release Date**: January 2, 2025  
**Version**: 2.2.0-POWER_PIN  
**APK File**: `hotel-agent-v2.2.0-POWER_PIN-debug.apk`  
**File Size**: 7.45 MB

---

## 🎯 New Features

### Power Off/Restart PIN Protection
When any user tries to power off or restart the device:
1. **⚠️ PIN Dialog Appears** - Red warning screen with power-off protection
2. **🔢 PIN Required** - Must enter correct PIN (default: 1234)
3. **❌ Cancel Option** - User can cancel the shutdown attempt
4. **✅ Authorize** - Correct PIN allows device to power off/restart
5. **🚨 Breach Alert** - Unauthorized attempts are logged

### Key Protection Features
- Intercepts system shutdown and reboot intents
- High-priority broadcast receiver (priority: 999)
- Dialog appears immediately on shutdown attempt
- Works with:
  - Power button long press
  - Settings → Power Off
  - Hardware restart buttons
  - Software reboot commands

---

## 🔧 Technical Implementation

### New Files Added
1. **ShutdownReceiver.kt** - Intercepts shutdown/reboot broadcasts
2. **PowerOffPinDialog.kt** - PIN authorization dialog for power operations

### Modified Files
- **AndroidManifest.xml** - Registered new receiver and activity
  - Added `REBOOT` permission
  - Added `DEVICE_POWER` permission
  - Registered `ShutdownReceiver` with intent filters
  - Registered `PowerOffPinDialog` activity

### Supported Intents
- `android.intent.action.ACTION_SHUTDOWN`
- `android.intent.action.REBOOT`
- `android.intent.action.QUICKBOOT_POWEROFF`
- `com.android.internal.intent.action.REQUEST_SHUTDOWN`

---

## 📱 Installation Instructions

### 1. Uninstall Previous Version (if needed)
```bash
adb uninstall com.example.hotel
```

### 2. Install New Version
```bash
adb install hotel-agent-v2.2.0-POWER_PIN-debug.apk
```

### 3. Launch the App
- Open "Hotel Agent" app
- Complete provisioning if new installation
- App will start monitoring in the background

---

## 🧪 Testing the Feature

### Test 1: Power Off via Power Button
1. Long press the power button
2. Tap "Power Off"
3. **Expected**: Red PIN dialog appears
4. Enter wrong PIN → Dialog closes, shutdown canceled
5. Enter correct PIN (1234) → Device powers off

### Test 2: Restart via Settings
1. Go to Settings → System → Restart
2. **Expected**: PIN dialog intercepts the restart
3. Enter correct PIN to allow restart

### Test 3: Cancel Protection
1. Trigger power off
2. When PIN dialog appears, tap "Cancel"
3. **Expected**: Dialog closes, device stays on, breach logged

---

## ⚙️ Configuration

### Default PIN
- **Default PIN**: `1234`
- Stored in SharedPreferences as `admin_pin`

### Change PIN
1. Open app → Admin Panel
2. Navigate to Settings
3. Change "Admin PIN"
4. New PIN applies to both WiFi and Power-Off protection

### Authorization Flag
- Uses `shutdown_authorized` flag in SharedPreferences
- Set to `true` when correct PIN entered
- Cleared after shutdown completes

---

## 🚨 Security Features

### Protection Layers
1. **Broadcast Interception** - Catches shutdown intents before system processes
2. **Authorization Flag** - Tracks if shutdown was authorized
3. **Dialog Lock** - Back button disabled during PIN entry
4. **High Priority** - Receiver priority 999 to intercept first

### Limitations
⚠️ **Note**: On modern Android (8.0+), apps cannot fully block system shutdown. However:
- PIN dialog will appear and delay shutdown
- Unauthorized attempts are logged as breaches
- User must interact with dialog or wait for timeout

---

## 🔍 Troubleshooting

### Issue: PIN Dialog Doesn't Appear
**Solutions**:
- Ensure app has been launched at least once
- Check that KioskService is running
- Verify device is provisioned
- Check app isn't in battery optimization
- Try enabling Device Administrator in Settings

### Issue: Device Shuts Down Without PIN
**Cause**: Some manufacturers have custom shutdown implementations
**Solutions**:
- Enable Device Admin for the app
- Check if running as system app (requires root)
- Some devices may require additional permissions

### Issue: Dialog Appears But Can't Shut Down
**Cause**: Missing system-level permissions
**Note**: This is expected - only system apps or rooted devices can programmatically shut down
**Workaround**: User can use physical power button after entering PIN

---

## 📊 Feature Comparison

| Feature | WiFi PIN (v2.1.0) | Power-Off PIN (v2.2.0) |
|---------|-------------------|------------------------|
| **PIN Protection** | ✅ Yes | ✅ Yes |
| **Authorization Flag** | `wifi_disable_authorized` | `shutdown_authorized` |
| **Monitoring Method** | 500ms polling loop | Broadcast receiver |
| **Re-enable Auto** | ✅ Yes (WiFi turns back ON) | ❌ No (can't force device ON) |
| **Dialog Theme** | Blue | Red (Warning) |
| **Breach Logging** | ✅ Yes | ✅ Yes |

---

## 📝 Version History

### v2.2.0 - Power Off PIN Protection (Current)
- ✅ Added ShutdownReceiver for power-off interception
- ✅ Added PowerOffPinDialog with red warning theme
- ✅ Added REBOOT and DEVICE_POWER permissions
- ✅ Integrated with existing PIN system

### v2.1.0 - WiFi PIN Working
- ✅ WiFi PIN protection via 500ms monitoring loop
- ✅ WifiPinDialog with blue theme
- ✅ Auto-reenables WiFi when disabled without authorization

### v2.0.0 - Breach Detection Fixed
- ✅ Fixed WiFi breach trigger logic
- ✅ Backend instant breach monitoring (12-second timeout)
- ✅ Enhanced registration and heartbeat logging

---

## 🎯 Next Steps

After installation, test all protection features:
1. ✅ WiFi PIN Protection (turn OFF WiFi → PIN appears)
2. ✅ Power Off PIN Protection (power button → PIN appears)
3. ✅ Breach Detection (WiFi OFF → dashboard shows alert in 12s)
4. ✅ Heartbeat Monitoring (backend logs every 5 seconds)

---

## 📧 Support

If you encounter issues:
1. Check backend logs: Terminal running `uvicorn`
2. Check device logs: `adb logcat | findstr "PowerOffPinDialog"`
3. Verify provisioning status in app
4. Ensure backend is accessible at `10.247.23.77:8080`

---

**Built with**: Kotlin, Android SDK 34, Gradle 8.9  
**Minimum Android**: 8.0 (API 26)  
**Target Android**: 14 (API 34)
