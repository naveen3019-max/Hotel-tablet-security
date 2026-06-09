# WIFI BREACH TESTING GUIDE 🧪

## Quick Reference: How to Test Each Attack Scenario

---

## Setup Requirements

### ✅ DO THIS FIRST:
1. **Use a stable Wi-Fi router** (NOT mobile hotspot)
   - Home router, office router, or buy a cheap spare (~₹700)
   
2. **Provision tablet:**
   - Device ID: `TAB-TEST-001`
   - Room ID: `ROOM-101`
   - Network: `YourWifiSSID`
   - BSSID: Note the exact MAC address shown during provisioning
   - Min RSSI: `-70 dBm` (or `-60` if close to router)

3. **Verify backend running:**
   ```bash
   # Terminal 1: Backend API
   cd backend-api
   uvicorn main:app --host 0.0.0.0 --port 8080 --reload
   
   # Terminal 2: MongoDB (if local)
   mongod
   ```

4. **Verify tablet connected:**
   - Tablet shows "ACTIVE" on dashboard
   - Heartbeat arriving every 10 seconds

---

## Test 1: WiFi OFF (IMMEDIATE LOCK) ⚡

### Scenario:
User manually turns off Wi-Fi in device settings.

### Expected Behavior:
- **T=0ms:** LockActivity appears full-screen
- **T=200ms:** Backend receives breach alert
- **T=500ms:** Dashboard shows LOCKED
- **T=1s:** Slack/Email notification sent

### Steps to Test:

```
1. Tablet is running, screen on, Wi-Fi connected
2. Observer watches: Pull down notification center
3. Toggle: Wi-Fi OFF (radio button)
4. Expected: Lock screen appears immediately
5. Verify: Backend shows breach_reason="WiFi-OFF"
6. Verify: Dashboard RED for this device
```

### Pass Criteria:
- Lock appears within **500ms**
- Backend logs show breach alert
- Dashboard status changes to LOCKED
- No errors in logcat

### Fail Indicators:
- Lock doesn't appear (check permissions, device admin)
- Breach not recorded (backend endpoint issue)
- Delayed lock > 2 seconds (network lag)

---

## Test 2: WiFi SSID Change (4 SECOND LOCK) 📡

### Scenario:
User connects to different Wi-Fi network (e.g., mobile hotspot).

### Expected Behavior:
- **T=0s:** Device connects to different SSID
- **T=2s:** Next Wi-Fi scan detects SSID mismatch
- **T=4s:** Breach threshold reached
- **T=4.1s:** LockActivity launches

### Steps to Test:

```
1. Tablet connected to "HotelWiFi" (original)
2. Observer opens Settings → WiFi
3. Toggle "HotelWiFi" OFF, connect to "PersonalHotspot"
4. Watch logcat: grep "WifiFence"
5. Expected: "SSID mismatch" after ~2 seconds
6. Lock screen after ~4 seconds total
```

### Logcat Patterns to Expect:
```
D/WifiFence: Scan result: SSID=PersonalHotspot, expected=HotelWiFi
W/WifiFence: 🚨 SSID mismatch: PersonalHotspot != HotelWiFi
W/WifiFence: ⚠ Breach counter increasing: 1 (~2/4 seconds)
W/WifiFence: ⚠ Breach counter increasing: 2 (~4/4 seconds)
E/WifiFence: 🚨🚨🚨 WIFI FENCE BREACH! Elapsed: 4 s, Grace: 4 s
```

### Pass Criteria:
- Breach detected **exactly at SSID change**
- Lock triggered after **4 seconds**
- Backend receives breach alert with reason
- Dashboard updates immediately

---

## Test 3: RSSI/Signal Drop (8 SECOND LOCK) 📉

### Scenario:
User walks away from router (signal weakens).

### Expected Behavior:
- **T=0s:** User starts walking away
- **T=2s:** RSSI drops below threshold (e.g., -70 to -80 dBm)
- **T=4s:** Next scan confirms weak signal (+1 breach counter)
- **T=6s:** +1 again
- **T=8s:** Threshold reached, lock triggered

### Steps to Test:

```
1. Tablet connected at strong signal (-50 dBm)
2. Note RSSI in logcat: grep "Scan result: RSSI"
3. Walk tablet 30 meters from router (or through walls)
4. Watch RSSI decrease in logcat
5. When RSSI < -70 dBm for 8 seconds → lock
```

### Logcat Pattern:
```
D/WifiFence: Scan result: RSSI=-50  (good signal)
D/WifiFence: Scan result: RSSI=-65  (still ok)
D/WifiFence: Scan result: RSSI=-75  (TOO WEAK)
W/WifiFence: ⚠ RSSI breach: -75 < -70 dBm
W/WifiFence: ⚠ Breach counter: 1 (~2/4 seconds)
...after 4 more seconds...
W/WifiFence: ⚠ Breach counter: 2 (~4/4 seconds)
E/WifiFence: 🚨🚨🚨 WIFI FENCE BREACH!
```

### Pass Criteria:
- RSSI monitored continuously in logcat
- Lock triggered when signal weak for grace period
- Signal recovery = breach counter reset
- No false positives (shouldn't lock if recovering)

---

## Test 4: Network Loss / Disconnection (4 SECOND LOCK) ❌

### Scenario:
Device disconnects from network (e.g., router powered off).

### Expected Behavior:
- **T=0s:** Network lost
- **T=0.1s:** ConnectivityManager.onLost() fires
- **T=2s:** Wi-Fi scan shows no connection
- **T=4s:** Grace period expired
- **T=4.1s:** Lock triggered

### Steps to Test:

```
Option A (Power off router):
1. Tablet connected to Wi-Fi
2. Unplug router power
3. Watch for immediate lock

Option B (WiFi disable on another device):
1. SSH to router: $ systemctl stop wpa_supplicant
2. Tablet loses connection
3. Should lock within 4 seconds

Option C (Network loss simulation):
1. adb shell: $ pm hide com.android.systemui (hides UI)
2. Disconnect from within tablet settings
3. Watch for lock
```

### Expected Logcat:
```
E/WifiFence: 🚨 Network lost: Network{...}
W/WifiFence: ⚠ Connection info null
W/WifiFence: ⚠ Breach counter: 1 (~2/4 seconds)
...wait 2 more seconds...
E/WifiFence: 🚨🚨🚨 WIFI FENCE BREACH!
```

### Pass Criteria:
- Network loss detected immediately
- Lock triggered within grace period
- Backend marked LOCKED, not COMPROMISED

---

## Test 5: BSSID Change (Rare, 4 SECOND LOCK) 🔄

### Scenario:
Device reconnects to **same SSID** but **different BSSID** (e.g., switched to 5GHz band or neighboring AP).

### Expected Behavior:
- **T=0s:** Disconnected and reconnected
- **T=2s:** Scan detects BSSID mismatch
- **T=4s:** Lock triggered (heavy weight +2)

### Steps to Test:

```
Advanced: Requires dual-band router
1. Tablet connected to "HotelWiFi" (2.4 GHz, BSSID=AA:BB:CC:DD:EE:FF)
2. Manually connect to "HotelWiFi-5G" (5 GHz, BSSID=AA:BB:CC:DD:EE:F0)
   - Same SSID, different BSSID
3. Lock should trigger (if 5GHz not in allowlist)
4. Check logcat for BSSID mismatch
```

### Pass Criteria:
- BSSID change detected even with same SSID
- Proper weight applied (BSSID change = +2)
- Lock faster than RSSI-only scenarios

---

## Test 6: Wi-Fi ON/OFF Toggle Recovery 🔄

### Scenario:
User briefly disables Wi-Fi then re-enables (should NOT lock if quick).

### Expected Behavior:
- **T=0s:** Wi-Fi OFF → LockActivity launches (immediate)
- **T=5s:** User taps Wi-Fi ON
- **T=6s:** Device reconnects
- **Result:** Stays locked (can't un-lock from lock screen)

### Steps:
```
1. Tablet displaying lock screen
2. Don't restart service (leave locked)
3. Turn Wi-Fi back on (optional verification)
4. Lock screen remains (expected - only admin can unlock)
```

### Pass Criteria:
- Lock persists
- Unlock requires admin PIN
- Backend shows LOCKED status

---

## Test 7: Backend Command Lock ✅

### Scenario:
Backend sends "LOCK" command via heartbeat response.

### Expected Behavior:
- **T=0s:** Heartbeat sent
- **T=0.5s:** Backend responds with `status=LOCKED`
- **T=0.6s:** KioskService checks response
- **T=0.7s:** LockActivity launched

### Steps:
```
1. Manually modify backend to return LOCKED status
   (Edit route: POST /alerts/heartbeat)
2. Return: {"status": "LOCKED", ...}
3. Next heartbeat (10s) triggers lock
```

### Expected Logcat:
```
D/KioskService: Heartbeat successful. Server status: LOCKED
W/KioskService: 🚨 Backend requested LOCK (status=LOCKED)
D/KioskService: Starting lock activity
```

### Pass Criteria:
- Lock triggered by backend command
- Works even if Wi-Fi signals OK
- Proves backend control works

---

## Test 8: Offline Queue Sync 📤

### Scenario:
Device goes offline, queues alerts, reconnects, syncs.

### Expected Behavior:
- **T=0s:** Network lost
- **T=2s:** Breach alert attempted
- **T=3s:** OfflineQueueManager queues to SQLite
- **T=10s:** Network reconnects (reconnect to Wi-Fi)
- **T=15s:** Next heartbeat succeeds
- **T=15.5s:** Offline queue synced to backend

### Steps:
```
1. Tablet on Wi-Fi, running KioskService
2. Walk tablet out of range (or disable Wi-Fi)
3. Trigger breach (e.g., move far from router)
4. Verify logcat: "queuing offline"
5. Reconnect to Wi-Fi
6. Next heartbeat should show: "Successfully synced X offline alerts"
```

### Expected Logcat:
```
W/KioskService: Breach alert failed, queuing offline
D/OfflineQueue: Queued alert to SQLite: breach
...
I/KioskService: Successfully synced 1 offline alerts
```

### Pass Criteria:
- Offline alerts queued successfully
- No data loss
- Alerts delivered after reconnection
- Backend receives correct timestamps

---

## Troubleshooting Test Failures

### Lock never triggers
```
❌ Check:
1. WifiManager.isWifiEnabled() returns true
2. WifiManager.connectionInfo not null
3. Breach counter threshold (grace seconds)
4. Is KioskService running? (adb shell ps | grep KioskService)

✅ Fix:
1. Increase log verbosity: gradle build with DEBUG logging
2. Check foreground service notification
3. Verify device admin is enabled
4. Restart service: adb shell am startservice ...
```

### Heartbeat not sent
```
❌ Check:
1. Backend endpoint returning 200 OK
2. JWT token valid (check expiry)
3. Device ID matches backend registration

✅ Fix:
1. Check backend logs: tail -f backend.log
2. Curl test: curl http://backend:8080/alerts/heartbeat
3. Re-register device if token expired
```

### Logcat spamming / too much output
```
Filter to WifiFence only:
$ adb logcat | grep "WifiFence\|KioskService"

Save to file:
$ adb logcat > device_logs.txt &
$ # Run tests
$ kill %1  (stop logging)
$ cat device_logs.txt | grep "breach\|LOCK"
```

---

## Quick Test Script (Bash/PowerShell)

### Run All Tests Automated:

**test_wifi_breach.ps1:**
```powershell
# Configure
$DEVICE_ID = "TAB-TEST-001"
$BACKEND = "http://localhost:8080"

# Test 1: Get current status
$status = curl "$BACKEND/devices/$DEVICE_ID" -H "Accept: application/json"
Write-Host "Current status: $status"

# Test 2: Trigger lock
curl "$BACKEND/devices/$DEVICE_ID/lock" -Method POST

# Test 3: Check breach
$breaches = curl "$BACKEND/devices/$DEVICE_ID/breaches"
Write-Host "Recent breaches: $breaches"

# Test 4: Get dashboard
Start-Process "http://localhost:3000/dashboard"
```

---

## Success Criteria Summary ✅

| Test | Pass Condition | Failure Action |
|------|---|---|
| WiFi OFF | Lock < 500ms | Debug WifiStateReceiver |
| SSID Change | Lock in 4s ±1s | Check SSID parsing |
| RSSI Drop | Lock in 8s ±2s | Verify min RSSI threshold |
| Network Loss | Lock in 4s ±1s | Check onLost callback |
| BSSID Change | Lock in 4s ±1s | Verify BSSID comparison |
| Recovery | Counter resets | Check reset logic |
| Backend Lock | Lock in 1s | Verify response parsing |
| Offline Sync | Queued & synced | Check SQLite + batch endpoint |

---

## Next Steps

Once all tests pass:
1. ✅ Record test videos (for client demo)
2. ✅ Document actual timings on your router
3. ✅ Deploy to production tablets
4. ✅ Monitor real-world breach events
5. ✅ Tune grace period based on network conditions
6. ✅ Brief client on detection + enforcement model

---

Generated: January 5, 2026
Ready for: Enterprise Testing & Certification 🎯
