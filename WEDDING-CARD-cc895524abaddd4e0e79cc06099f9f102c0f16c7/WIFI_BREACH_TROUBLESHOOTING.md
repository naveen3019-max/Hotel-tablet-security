📱 WIFI BREACH DETECTION TROUBLESHOOTING GUIDE
===============================================

The issue is that WiFi breach detection is not working when you turn off WiFi. 
Here's how to systematically diagnose and fix this:

## 🔍 ROOT CAUSE ANALYSIS

Your tablet needs 3 things to detect WiFi breaches:

1. ✅ **DEVICE REGISTERED**: Your device TAB-D9413C44 is now registered
2. ❌ **APP CONFIGURED**: Android app needs the JWT token  
3. ❌ **SERVICE RUNNING**: KioskService must be monitoring WiFi

## 🛠️ STEP-BY-STEP FIX

### STEP 1: Configure Android App
Open the hotel tablet security app and:

1. **Go to Settings/Configuration screen**
2. **Enter these values:**
   - Device ID: `TAB-D9413C44`
   - Room ID: `101`
   - Backend URL: `https://hotel-backend-zqc1.onrender.com`
   - JWT Token: `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJUQUItRDk0MTNDNDQ...`
     (Use the full token from device_token.txt)

3. **Save configuration**
4. **Restart the app**

### STEP 2: Start Monitoring Service
1. **Open the app main screen**
2. **Look for "Start Service" or "Start Monitoring" button**
3. **Tap it to start KioskService**
4. **Check notification area** - should show "Hotel Security Monitoring Active"

### STEP 3: Test WiFi Detection
1. **Keep the app open** or running in background
2. **Turn OFF WiFi** on the tablet
3. **Wait 3-5 seconds** (grace period)
4. **Check for:**
   - Orange breach screen appears
   - Notification about WiFi disconnection
   - Status changes in dashboard (https://hotel-backend-zqc1.onrender.com)

## 📊 DEBUGGING CHECKLIST

Use Android Studio or `adb logcat` to check logs:

```bash
adb logcat | grep -E "WifiFence|KioskService|BREACH"
```

**Look for these logs:**
- ✅ `WifiFence STARTED - Monitoring every 2000ms`
- ✅ `ACTIVE MONITORING: WiFi Breach Detection: ENABLED`
- ✅ `📡 Scan: SSID=..., BSSID=..., RSSI=... dBm`
- 🚨 `WIFI FENCE BREACH DETECTED!` (when WiFi turns off)
- 📤 `Breach alert sent successfully` (API call works)

## ⚠️ COMMON ISSUES

### Issue 1: App Not Running
**Problem**: KioskService stopped or never started
**Solution**: Restart app, enable "Start Monitoring"

### Issue 2: Wrong Configuration  
**Problem**: Old backend URL or missing JWT token
**Solution**: Update app configuration with values above

### Issue 3: Network Permissions
**Problem**: App can't access network to send alerts
**Solution**: Grant network permissions in Android settings

### Issue 4: Background Restrictions
**Problem**: Android kills the monitoring service
**Solution**: Disable battery optimization for the app

## 🔧 INSTANT TEST

Run this command to verify the backend is receiving alerts:

```bash
python test_wifi_breach.py https://hotel-backend-zqc1.onrender.com
```

Then **turn OFF WiFi on tablet** - you should see the device status change from "ok" to "breach" within 10 seconds.

## 📱 TABLET CONFIGURATION

1. **WiFi Settings**: Note current network SSID and BSSID
2. **App Permissions**: Ensure app has Location and Network access
3. **Battery Optimization**: Disable for this app
4. **Developer Options**: Enable if needed for debugging

## ✅ SUCCESS INDICATORS

When working correctly, you'll see:

1. **Tablet**: Orange breach screen when WiFi turns off
2. **Dashboard**: Device status changes red (breach)
3. **Logs**: "WIFI FENCE BREACH DETECTED!" messages
4. **Backend**: New breach alerts in database

## 🚀 QUICK START

1. Copy JWT token from `device_token.txt`
2. Open Android app → Settings
3. Paste token and backend URL
4. Start monitoring service
5. Test by turning off WiFi

**Device Token Location:** `device_token.txt` in this folder

---
🔬 If issues persist, check Android logs and verify the KioskService is actually running and configured correctly.