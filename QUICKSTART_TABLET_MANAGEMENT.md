# 🎯 QUICK START - Owner Dashboard Tablet Management

**Add and delete tablets with one click!**

---

## 📱 Dashboard Features

### ➕ Add Tablet
Click **"Add Tablet"** button → Fill form → Get token instantly

### ❌ Delete Tablet  
Click **"Delete"** on device card → Confirm → Done

### 📊 View All Tablets
Real-time list with status, battery, signal strength

---

## 🚀 Step-by-Step: Add Your First Tablet

### 1. Open Dashboard
```
http://localhost:3000
```
(or http://YOUR_SERVER_IP:3000)

### 2. Click "Add Tablet" Button
Look for the blue button in the top-right corner

### 3. Fill the Form
```
Device ID: TAB-101
Room ID: 101
Hotel ID: default
```

### 4. Click "Add Tablet"

### 5. Copy the JWT Token
You'll see a popup with a long token like:
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**⚠️ SAVE THIS TOKEN!** You need it to configure the tablet.

### 6. Done! 
Tablet appears in dashboard immediately ✅

---

## 🗑️ Delete Tablet

### 1. Find the Tablet
Scroll through the dashboard to find the device

### 2. Click "Delete" Button
Red button on the top-right of the device card

### 3. Confirm
Click "OK" when asked:
```
Are you sure you want to delete device TAB-101?
This will also delete all associated alerts.
```

### 4. Done!
Tablet and all its alerts are deleted permanently ✅

---

## 📋 What Information to Enter

### Device ID
**What it is:** Unique name for the tablet

**Examples:**
- `TAB-101` - Simple format
- `HOTEL1-ROOM-101` - Multi-hotel format
- `TABLET-FLOOR2-01` - By floor

**Rules:**
- ✅ Must be unique (no duplicates)
- ✅ Use letters, numbers, hyphens
- ❌ No spaces
- ❌ No special characters

### Room ID
**What it is:** Room number where tablet is installed

**Examples:**
- `101`, `201`, `301` - Standard room numbers
- `1A`, `2B` - Lettered rooms
- `SUITE-101` - Special rooms

### Hotel ID
**What it is:** Hotel identifier (for multi-property)

**Default:** `default` (single hotel)

**Multi-hotel examples:**
- `hotel-downtown`
- `hotel-beach`
- `property-1`

---

## 🔧 After Adding Tablet - Configure Physical Device

### Option 1: Via Tablet Settings (If supported)
1. Install app on tablet
2. Open app → Settings
3. Enter:
   - Server URL: `http://YOUR_SERVER_IP:8080`
   - Device ID: `TAB-101`
   - Room ID: `101`
   - JWT Token: `[paste token here]`
4. Save → Restart app

### Option 2: Update Code Before Building
Edit `AgentRepository.kt`:
```kotlin
private val BASE_URL = "http://192.168.1.100:8080"
private var jwtToken = "eyJhbGci..." // Token from dashboard
```

Then rebuild APK:
```powershell
cd android-agent
.\gradlew.bat assembleRelease
```

### Option 3: API Configuration
Use environment variables or config file (advanced)

---

## 📊 Dashboard View Explained

Each device card shows:

```
┌─────────────────────────────────────────────┐
│  TAB-101 — Room 101          [Delete]       │
│  Last seen: Dec 21, 2025 2:30 PM            │
│                                              │
│  Status: ok          Battery: 85%           │
│                      RSSI: -65 dBm          │
└─────────────────────────────────────────────┘
```

### Status Colors (Future Enhancement)
- 🟢 **Green** - Protected (ok)
- 🔴 **Red** - Breach detected
- ⚪ **Gray** - Offline

### Fields
- **Device ID** - Unique identifier
- **Room ID** - Room number
- **Last Seen** - Last heartbeat timestamp
- **Status** - Current state (ok/breach/offline)
- **Battery** - Battery percentage
- **RSSI** - WiFi signal strength (dBm)

---

## ⚡ Quick Operations

### Add 10 Tablets (Rooms 101-110)
```powershell
$API = "http://localhost:8080"
101..110 | ForEach-Object {
    Invoke-RestMethod -Method POST `
      -Uri "$API/api/devices/quick-add" `
      -ContentType "application/json" `
      -Body "{`"deviceId`":`"TAB-$_`",`"roomId`":`"$_`",`"hotelId`":`"default`"}"
}
```

### Delete All Tablets
```powershell
$API = "http://localhost:8080"
$devices = Invoke-RestMethod -Uri "$API/api/devices"
$devices | ForEach-Object {
    Invoke-RestMethod -Method DELETE -Uri "$API/api/devices/$($_.deviceId)"
}
```

### Export Device List to CSV
```powershell
$devices = Invoke-RestMethod -Uri "http://localhost:8080/api/devices"
$devices | Export-Csv -Path "devices.csv" -NoTypeInformation
```

---

## 🚨 Common Issues & Solutions

### "Device already exists"
**Problem:** Device ID is already registered

**Solution:**
1. Check if it's an old device
2. Delete the old device first
3. Try adding again

### Tablet not showing in dashboard
**Problem:** New tablet not appearing after adding

**Solution:**
1. Wait 3 seconds (auto-refresh)
2. Refresh page manually (F5)
3. Check if API server is running

### Delete button not working
**Problem:** Click delete but nothing happens

**Solution:**
1. Check backend is running: `http://localhost:8080/health`
2. Check browser console (F12) for errors
3. Try API method manually

### Token not working in tablet
**Problem:** Tablet says "Unauthorized"

**Solution:**
1. Copy the FULL token (very long string)
2. Delete device and re-add (generates new token)
3. Make sure no extra spaces in token
4. Check token expiration (default: 30 days)

---

## 📞 Testing the System

### Test 1: Add Device
1. Dashboard → Add Tablet
2. Enter: `TEST-001`, Room `999`
3. Copy token
4. Check device appears in list ✅

### Test 2: Delete Device
1. Find TEST-001 in dashboard
2. Click Delete → Confirm
3. Check device disappears ✅

### Test 3: API Verification
```powershell
# List all devices
Invoke-RestMethod -Uri "http://localhost:8080/api/devices"

# Check health
Invoke-RestMethod -Uri "http://localhost:8080/health"
```

### Test 4: Real Tablet
1. Add device: `TAB-101`, Room `101`
2. Configure physical tablet with token
3. Open tablet app
4. Check dashboard - should show online
5. Walk away from room - should trigger breach

---

## 📚 More Information

**Complete guides:**
- [TABLET_MANAGEMENT.md](TABLET_MANAGEMENT.md) - Full documentation
- [COMPLETE_SETUP_GUIDE.md](COMPLETE_SETUP_GUIDE.md) - System setup
- [EMAIL_SETUP.md](EMAIL_SETUP.md) - Email notifications
- [SLACK_SETUP.md](SLACK_SETUP.md) - Slack notifications

**API Documentation:**
```
http://localhost:8080/docs
```

**Dashboard:**
```
http://localhost:3000
```

---

## ✅ Checklist

Before adding tablets:
- [ ] Backend running (`http://localhost:8080/health`)
- [ ] Dashboard running (`http://localhost:3000`)
- [ ] MongoDB Atlas configured
- [ ] Know your server IP address

After adding tablet:
- [ ] Token copied and saved
- [ ] Tablet app installed
- [ ] Token configured in tablet
- [ ] Tablet appears online in dashboard
- [ ] Test breach alert

---

**You're ready to manage your tablet fleet! 🎉**

**Quick commands:**
- Add: Dashboard → "Add Tablet" button
- Delete: Device card → "Delete" button
- View: Dashboard homepage (auto-refreshes every 3 seconds)

**Need help?** Check [TABLET_MANAGEMENT.md](TABLET_MANAGEMENT.md) for detailed instructions and troubleshooting.
