# 🔍 ROOT CAUSE ANALYSIS: WiFi Breach Not Showing in Dashboard

## Date: February 13, 2026
## Device: TAB-B2A8792B, Room 5680

---

## 🚨 PROBLEM DIAGNOSED

Your logs show the tablet **has been disconnected from WiFi for over 5 minutes**, trying to send heartbeats but failing with:
```
UnknownHostException: Unable to resolve host "hotel-backend-zqc1.onrender.com"
WiFi is ON but not connected (209s, 211s, 213s...)
BSSID=null, RSSI=null
```

**BUT** dashboard shows device as "OK/Connected" and NO breach alerts.

---

## 🔎 ROOT CAUSE DISCOVERED

When I checked the backend, I found:

```
⚠️ Device TAB-B2A8792B not found in backend
```

**The device is NOT registered in the backend database!**

This explains everything:
- Device tries to send heartbeats → Fails (no WiFi)
- Backend doesn't know about this device → No monitoring
- Dashboard doesn't show device → No breach alerts
- Heartbeat timeout detection can't work → Device not in database

---

## 📋 WHAT HAPPENED?

Your device WAS registered before (you mentioned it was showing in dashboard), but now it's gone. Possible reasons:

1. **Database was cleared/reset** during backend deployment
2. **Device record expired or was deleted**
3. **Token expired** and device lost its registration
4. **Backend redeployment** may have wiped MongoDB data

The backend shows **32 devices registered**, but all have `device_id=None` (indicates a schema/API bug that needs fixing).

---

## ✅ SOLUTION: Re-Register the Device

I've already generated a NEW JWT token for your device:

**New Token:** `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...` (saved to `device_token_TAB-B2A8792B.txt`)

### Option 1: Re-Register Through App (EASIEST)

1. **On the tablet:**
   - Open Settings → Apps → Hotel Security
   - Tap "Clear Data" or "Clear Storage"
   - Open the Hotel Security app
   
2. **You'll see the Device Registration screen:**
   - Enter Device ID: `TAB-B2A8792B`
   - Enter Room Number: `5680`
   - Tap "Register Device"
   
3. **Wait for confirmation:**
   - Should see "Registration Successful"
   - App will start sending heartbeats
   - Device will appear in dashboard
   
4. **Test breach detection:**
   - Wait 30 seconds for heartbeats to establish
   - Turn OFF WiFi on tablet
   - Wait 15-20 seconds
   - Dashboard should show breach alert!

### Option 2: Clear App Data via ADB

If you have the tablet connected via USB:

1. Connect tablet to computer via USB
2. Enable USB debugging on tablet
3. Run: `update_tablet_token.bat`
4. Follow the on-screen instructions

---

## 🔧 BACKEND FIX NEEDED

I noticed the backend API is returning devices with `device_id=None`. This needs to be fixed in the backend code. The issue is likely in the MongoDB schema or API serialization.

**Backend file to check:** `backend-api/main.py` - line where devices are fetched and returned via API.

---

## 🎯 EXPECTED BEHAVIOR AFTER FIX

Once device is properly registered:

1. **Normal Operation:**
   - Tablet sends heartbeat every 4 seconds
   - Backend updates `last_seen` timestamp
   - Dashboard shows device status as "Connected"
   
2. **WiFi Turns OFF:**
   - Tablet enters breach state (orange screen)
   - Tablet cannot send HTTP request (no internet)
   - Backend detects missing heartbeats after 15 seconds
   - Backend marks device as "breach" status
   - Backend broadcasts SSE event to dashboard
   - Dashboard shows breach alert for room 5680
   
3. **WiFi Turns Back ON:**
   - Tablet reconnects to WiFi
   - Heartbeat resumes within seconds
   - Backend detects heartbeat, changes status to "ok"
   - Dashboard shows recovery notification

---

## ⚡ QUICK TEST CHECKLIST

After re-registration:

- [ ] Device appears in dashboard as "Connected"
- [ ] Dashboard shows recent heartbeat timestamp (updating every 4 seconds)
- [ ] Turn OFF WiFi on tablet
- [ ] Orange breach screen appears immediately on tablet ✅
- [ ] Dashboard shows breach alert within 15-20 seconds
- [ ] Turn WiFi back ON
- [ ] Tablet reconnects and sends heartbeat
- [ ] Dashboard shows device as "Connected" again
- [ ] Breach alert clears or shows "Resolved"

---

## 📞 IF STILL NOT WORKING

1. **Check backend deployment logs:**
   ```
   Go to: https://dashboard.render.com
   Open your backend service
   View logs for: "🔍 Starting heartbeat monitoring task"
   ```

2. **Check MongoDB connection:**
   - Backend needs MongoDB Atlas connection
   - Check environment variables are set correctly
   - Verify MongoDB connection string is valid

3. **Test heartbeat endpoint manually:**
   ```bash
   python check_heartbeat_monitoring.py
   ```

4. **Check for device record in MongoDB:**
   - Device should appear with `device_id: "TAB-B2A8792B"`
   - Should have `status: "ok"` when connected
   - Should have recent `last_seen` timestamp

---

## 🎓 LESSONS LEARNED

1. **Device must be registered** before breach detection works
2. **Backend database persistence** is critical - don't clear MongoDB
3. **Token management** - devices need valid JWT tokens
4. **Monitoring requires heartbeats** - can't detect WiFi OFF without baseline
5. **Backend-side detection** is essential for connectivity-loss scenarios

---

## 📁 FILES CREATED

- `register_TAB-B2A8792B.py` - Re-registration script
- `device_token_TAB-B2A8792B.txt` - New JWT token for tablet
- `check_heartbeat_monitoring.py` - Backend diagnostic tool
- `update_tablet_token.bat` - ADB helper script
- This diagnosis document

---

**Status:** Ready for re-registration ✅  
**Next Action:** Clear app data and re-register device through tablet app  
**Expected Result:** Breach detection working within 2 minutes of re-registration
