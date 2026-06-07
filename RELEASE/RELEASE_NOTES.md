# Hotel Security Agent - Release v2.0.0

**Release Date:** January 9, 2026  
**Version:** 2.0.0  
**APK Name:** HotelSecurityAgent_v2.0.0_2026-01-09.apk  
**File Size:** 2.65 MB  

---

## 🎉 What's New in v2.0.0

### 🔐 WiFi PIN Protection
- **NEW!** WiFi cannot be turned OFF without PIN authorization
- When user tries to disable WiFi:
  - WiFi automatically turns back ON
  - PIN dialog appears
  - User must enter PIN (default: `1234`) to disable WiFi
- Prevents unauthorized WiFi disable attempts
- Back button disabled on PIN dialog

### ⚡ Instant Breach Detection
- Backend monitors device heartbeats in real-time
- **12-second detection** - If device goes silent for 12+ seconds, breach triggered immediately
- Dashboard shows breaches instantly without waiting for device to reconnect
- Background monitoring runs continuously

### 📊 Enhanced Logging
- Detailed registration logs with device info
- Heartbeat logs show: Device ID, Room, RSSI, BSSID
- Clear visual indicators with emojis for easy monitoring
- Color-coded log levels

### 🔄 Improved WiFi State Handling
- Multiple WiFi state action support
- High-priority broadcast receiver (priority: 999)
- Better detection of WiFi state changes
- Support for both Quick Settings and Settings toggles

---

## 📋 Installation Instructions

### Method 1: WhatsApp/Telegram Transfer
1. Send the APK file via WhatsApp/Telegram to the tablet
2. On tablet, tap the file to download
3. Tap to open and install
4. Enable "Install from Unknown Sources" if prompted
5. Tap "Install"

### Method 2: ADB Installation
```bash
adb connect <TABLET_IP>:5555
adb install -r HotelSecurityAgent_v2.0.0_2026-01-09.apk
```

### Method 3: USB/SD Card Transfer
1. Copy APK to USB drive or SD card
2. Insert into tablet
3. Use File Manager to locate APK
4. Tap to install

---

## 🧪 Testing Checklist

### Test 1: WiFi PIN Protection
- [ ] Try to turn OFF WiFi
- [ ] Verify WiFi turns back ON automatically
- [ ] Verify PIN dialog appears
- [ ] Enter wrong PIN → Should show error
- [ ] Enter correct PIN (1234) → WiFi should turn OFF
- [ ] Click Cancel → WiFi should stay ON

### Test 2: Breach Detection (WiFi OFF)
- [ ] Turn ON WiFi on tablet
- [ ] Wait for heartbeat to connect
- [ ] Open dashboard on laptop
- [ ] Turn OFF WiFi with correct PIN
- [ ] Within 12 seconds, breach should appear on dashboard

### Test 3: Device Registration
- [ ] Check backend terminal for registration logs
- [ ] Should see: "📱 NEW DEVICE REGISTRATION"
- [ ] Verify Device ID, Room ID displayed

### Test 4: Heartbeat Monitoring
- [ ] Check backend terminal for heartbeat logs
- [ ] Should see: "💓 HEARTBEAT: [DeviceID] | Room: [RoomID] | RSSI: [value]"
- [ ] Heartbeats should appear every 4 seconds

---

## 🔑 Configuration

### Default Settings
- **Admin PIN:** `1234`
- **Grace Period:** 4 seconds
- **Heartbeat Interval:** 4 seconds
- **Breach Detection Timeout:** 12 seconds
- **Min RSSI:** -70 dBm (configurable per room)

### Change Admin PIN
Option 1 - Via ADB:
```bash
adb shell "su 0 sh -c 'echo \"admin_pin=YOUR_NEW_PIN\" >> /data/data/com.example.hotel/shared_prefs/agent.xml'"
```

Option 2 - In App:
- Open Admin Settings
- Navigate to Security
- Update Admin PIN field

---

## 📱 System Requirements

- **Android Version:** 8.0 (Oreo) or higher
- **Permissions Required:**
  - Internet
  - WiFi State Access
  - Change WiFi State
  - Write Settings
  - Location (for WiFi scanning)
  - Foreground Service
  - Notifications

---

## 🛠️ Backend Setup

### Prerequisites
1. **Backend Server Running:**
   ```bash
   cd backend-api
   python -m uvicorn main:app --host 0.0.0.0 --port 8080 --reload
   ```

2. **Dashboard Running:**
   ```bash
   cd dashboard
   npm run dev
   ```

3. **MongoDB Running:**
   - Local: `mongodb://localhost:27017`
   - Or MongoDB Atlas connection configured

### Monitor Backend Logs
Backend will show:
- `📱 NEW DEVICE REGISTRATION` when devices register
- `💓 HEARTBEAT: [device]` every 4 seconds
- `🚨 INSTANT BREACH:` when devices go silent
- `✅ Breach alert broadcasted` when dashboard notified

---

## 🐛 Troubleshooting

### PIN Dialog Not Appearing

**Issue:** WiFi turns OFF without showing PIN dialog

**Solutions:**
1. Verify app is installed (check app list)
2. Restart the app
3. Check if device is provisioned in app settings
4. Grant all permissions in Settings → Apps → Hotel Security Agent
5. Try toggling WiFi from Settings (not Quick Settings)

**Check Logs:**
```bash
adb logcat -s "WifiStateReceiver:*" "WifiPinDialog:*"
```

### Breach Not Showing in Dashboard

**Issue:** WiFi OFF but no breach on dashboard

**Solutions:**
1. Check backend server is running
2. Verify dashboard is connected (should show "Live (SSE)")
3. Wait 12 seconds after WiFi OFF
4. Check backend terminal for "🚨 INSTANT BREACH" message
5. Refresh dashboard page

### Device Not Connecting

**Issue:** Device shows as offline

**Solutions:**
1. Verify WiFi is ON on tablet
2. Check tablet is on same network as backend
3. Verify backend URL is correct in app config
4. Check backend terminal for heartbeat logs
5. Restart KioskService in app

---

## 📊 Architecture

### Components
1. **Android Agent (Tablet):**
   - WifiFence - WiFi boundary monitoring
   - WifiStateReceiver - WiFi disable interception
   - WifiPinDialog - PIN authorization
   - KioskService - Background monitoring
   - Heartbeat - 4-second intervals

2. **Backend API (FastAPI):**
   - Device registration & JWT authentication
   - Heartbeat monitoring
   - Breach detection (12-second timeout)
   - Real-time SSE broadcasting
   - MongoDB storage

3. **Dashboard (Next.js):**
   - Real-time device status
   - Breach alerts
   - SSE event streaming
   - Device management

---

## 📝 Release Files

**Location:** `C:\Users\narra\OneDrive\Desktop\WEDDING-CARD-cc895524abaddd4e0e79cc06099f9f102c0f16c7\RELEASE\`

**Files:**
- `HotelSecurityAgent_v2.0.0_2026-01-09.apk` (2.65 MB)
- `RELEASE_NOTES.md` (this file)

---

## 🎯 Next Steps

1. **Install the APK** on all tablets
2. **Test WiFi PIN protection** on each device
3. **Monitor backend logs** during testing
4. **Verify dashboard** shows real-time updates
5. **Configure PINs** if needed (default: 1234)
6. **Train staff** on PIN usage

---

## 📞 Support

For issues or questions:
- Check troubleshooting section above
- Review logs using `adb logcat`
- Check backend terminal for error messages
- Verify all services are running

---

## 🔄 Upgrade from Previous Version

If upgrading from v1.x:
1. Uninstall old version (optional - can install over)
2. Install new APK
3. Re-provision device if needed
4. Test all features
5. Update admin PIN if desired

**Note:** Previous configurations will be preserved during upgrade.

---

**Build Date:** January 9, 2026  
**Package Name:** com.example.hotel  
**Minimum SDK:** 26 (Android 8.0)  
**Target SDK:** 34 (Android 14)
