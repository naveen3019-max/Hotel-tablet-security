# Tablet Management Guide - Add & Delete Tablets

Complete guide for owners to manage tablets from the dashboard with one-click operations.

---

## Overview

The owner dashboard now has **one-click** tablet management:
- ➕ **Add new tablets** - Click button, fill form, get token instantly
- ❌ **Delete tablets** - Click delete button, confirm, done
- 🔄 **Automatic sync** - Changes reflect immediately in real-time
- 🎫 **Auto-generated tokens** - JWT token created automatically for new tablets

---

## Part 1: Adding New Tablets (Owner Dashboard)

### Method 1: Quick Add from Dashboard (Recommended)

1. **Open Dashboard**: http://localhost:3000

2. **Click "Add Tablet"** button (top right corner)

3. **Fill the form:**
   ```
   Device ID: TAB-101
   Room ID: 101
   Hotel ID: default
   ```

4. **Click "Add Tablet"**

5. **Copy the JWT Token** from the popup:
   ```
   JWT Token (save this):
   eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJUQUItMTAxIiwicm9vbV9pZCI6IjEwMSIsImhvdGVsX2lkIjoiZGVmYXVsdCIsInR5cGUiOiJkZXZpY2UiLCJleHAiOjE3MzUyMDcyMDB9.abcdefghijklmnopqrstuvwxyz1234567890
   
   Configure this token in the tablet app.
   ```

6. **Done!** Tablet appears in the dashboard immediately.

### What Happens When You Add a Tablet?

✅ Device registered in MongoDB database  
✅ JWT token automatically generated  
✅ Device appears in dashboard  
✅ Ready to receive alerts  
✅ Real-time broadcast to all connected dashboards  

### Field Explanations

| Field | Required | Description | Example |
|-------|----------|-------------|---------|
| **Device ID** | ✅ Yes | Unique identifier for tablet | `TAB-101`, `TAB-201`, `TABLET-ROOM-101` |
| **Room ID** | ✅ Yes | Room number where tablet is installed | `101`, `201`, `301` |
| **Hotel ID** | ❌ Optional | Hotel identifier (for multi-hotel) | `default`, `hotel-downtown`, `hotel-beach` |

### Naming Conventions (Best Practices)

**Device IDs:**
- ✅ `TAB-101`, `TAB-201` (simple, clear)
- ✅ `HOTEL1-ROOM-101` (multi-hotel)
- ✅ `TABLET-FLOOR2-01` (by floor)
- ❌ `Device123` (not descriptive)
- ❌ `Room 101 Tablet` (spaces cause issues)

**Room IDs:**
- ✅ `101`, `201`, `301` (room numbers)
- ✅ `1A`, `2B` (lettered rooms)
- ✅ `SUITE-101` (special rooms)

---

## Part 2: Deleting Tablets (Owner Dashboard)

### One-Click Delete

1. **Find the tablet** in the dashboard

2. **Click "Delete"** button (red button, top-right of device card)

3. **Confirm deletion** in the popup:
   ```
   Are you sure you want to delete device TAB-101?
   This will also delete all associated alerts.
   ```

4. **Click OK**

5. **Done!** Tablet and all its alerts are permanently deleted.

### What Gets Deleted?

❌ **Device record** - Removed from database  
❌ **All alerts** - Breach, battery, offline alerts deleted  
❌ **Alert history** - Past alerts for this device  
✅ **JWT token invalidated** - Token no longer works  

### Important Notes

⚠️ **Deletion is permanent** - Cannot be undone  
⚠️ **Alerts are deleted** - All historical alerts for this device are removed  
⚠️ **Token stops working** - If tablet tries to send data, it will be rejected  

**When to delete a tablet:**
- Tablet permanently removed from room
- Room decommissioned or closed
- Tablet hardware broken/replaced
- Device ID needs to be changed

**When NOT to delete:**
- Tablet temporarily offline (it will come back)
- Moving tablet to different room (update room ID instead)
- Tablet in maintenance (just wait for it to reconnect)

---

## Part 3: API Method (Advanced Users)

### Add Tablet via API

```powershell
# PowerShell
$response = Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/devices/quick-add" `
  -ContentType "application/json" `
  -Body '{"deviceId":"TAB-301","roomId":"301","hotelId":"default"}'

Write-Host "Token: $($response.token)"
```

```bash
# Linux/Mac
curl -X POST http://localhost:8080/api/devices/quick-add \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"TAB-301","roomId":"301","hotelId":"default"}'
```

**Response:**
```json
{
  "ok": true,
  "message": "Device TAB-301 added successfully",
  "token": "eyJhbGci...",
  "device": {
    "deviceId": "TAB-301",
    "roomId": "301",
    "hotelId": "default",
    "status": "ok"
  }
}
```

### Delete Tablet via API

```powershell
# PowerShell
Invoke-RestMethod -Method DELETE `
  -Uri "http://localhost:8080/api/devices/TAB-301"
```

```bash
# Linux/Mac
curl -X DELETE http://localhost:8080/api/devices/TAB-301
```

**Response:**
```json
{
  "ok": true,
  "message": "Device TAB-301 deleted successfully"
}
```

---

## Part 4: Configuring Tablet with Token

After adding a tablet from dashboard, you need to configure the physical tablet:

### Step 1: Save the JWT Token

**Copy the token from the popup:**
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJUQUItMTAxIiwicm9vbV9pZCI6IjEwMSIsImhvdGVsX2lkIjoiZGVmYXVsdCIsInR5cGUiOiJkZXZpY2UiLCJleHAiOjE3MzUyMDcyMDB9.abc123
```

**Save it to a text file** or send to installation team.

### Step 2: Install App on Tablet

1. Transfer APK to tablet:
   ```powershell
   adb install android-agent/app/build/outputs/apk/release/app-release.apk
   ```

2. Open the app on tablet

### Step 3: Configure Tablet

**Method A: Manual Entry (If app has settings)**

1. Open Hotel Security app
2. Go to Settings → API Configuration
3. Enter:
   ```
   API Server: http://YOUR_SERVER_IP:8080
   Device ID: TAB-101
   Room ID: 101
   JWT Token: [paste the long token here]
   ```
4. Save and restart

**Method B: Update AgentRepository.kt (Before building)**

Edit `android-agent/app/src/main/java/.../AgentRepository.kt`:

```kotlin
class AgentRepository(context: Context) {
    private val BASE_URL = "http://192.168.1.100:8080" // Your server IP
    private var jwtToken = "eyJhbGci..." // Token from dashboard
    
    // Rest of the code...
}
```

Then rebuild and reinstall APK.

### Step 4: Test Connection

1. Open app on tablet
2. App should show: **"Room 101 - Protected"** (green)
3. Check dashboard - device should show online
4. Walk tablet away from room - should trigger breach alert

---

## Part 5: Bulk Operations

### Add Multiple Tablets (PowerShell Script)

Create `add-tablets.ps1`:

```powershell
# Add 10 tablets for rooms 101-110
$API = "http://localhost:8080"

for ($i = 101; $i -le 110; $i++) {
    $deviceId = "TAB-$i"
    $roomId = "$i"
    
    Write-Host "Adding $deviceId for Room $roomId..."
    
    $response = Invoke-RestMethod -Method POST `
      -Uri "$API/api/devices/quick-add" `
      -ContentType "application/json" `
      -Body "{`"deviceId`":`"$deviceId`",`"roomId`":`"$roomId`",`"hotelId`":`"hotel-1`"}"
    
    Write-Host "✅ $deviceId added. Token: $($response.token.Substring(0,20))..."
    Write-Host ""
}

Write-Host "All tablets added!"
```

**Run it:**
```powershell
.\add-tablets.ps1
```

### Delete Multiple Tablets

Create `delete-tablets.ps1`:

```powershell
# Delete tablets 101-110
$API = "http://localhost:8080"

for ($i = 101; $i -le 110; $i++) {
    $deviceId = "TAB-$i"
    
    Write-Host "Deleting $deviceId..."
    
    try {
        Invoke-RestMethod -Method DELETE -Uri "$API/api/devices/$deviceId"
        Write-Host "✅ $deviceId deleted"
    } catch {
        Write-Host "❌ Failed to delete $deviceId"
    }
}

Write-Host "Cleanup complete!"
```

**Run it:**
```powershell
.\delete-tablets.ps1
```

---

## Part 6: Common Scenarios

### Scenario 1: New Hotel Opening - 50 Rooms

**Task:** Add tablets for rooms 101-150

```powershell
# Quick script
$API = "http://localhost:8080"
$tokens = @()

101..150 | ForEach-Object {
    $response = Invoke-RestMethod -Method POST `
      -Uri "$API/api/devices/quick-add" `
      -ContentType "application/json" `
      -Body "{`"deviceId`":`"TAB-$_`",`"roomId`":`"$_`",`"hotelId`":`"hotel-new`"}"
    
    $tokens += [PSCustomObject]@{
        Device = "TAB-$_"
        Room = "$_"
        Token = $response.token
    }
}

# Export tokens to CSV
$tokens | Export-Csv -Path "tablet-tokens.csv" -NoTypeInformation

Write-Host "✅ 50 tablets added! Tokens saved to tablet-tokens.csv"
```

### Scenario 2: Replace Broken Tablet

**Problem:** Tablet TAB-205 is broken, need to replace with new hardware.

**Solution:**

1. **Delete old device:**
   - Dashboard → Find TAB-205 → Click Delete

2. **Add new device with same ID:**
   - Dashboard → Add Tablet
   - Device ID: `TAB-205`
   - Room ID: `205`
   - Get new token

3. **Configure new tablet:**
   - Install app on new tablet
   - Use the new token

### Scenario 3: Room Renovation - Temporarily Remove

**Problem:** Rooms 201-210 under renovation for 2 weeks.

**Options:**

**Option A: Keep devices (Recommended)**
- Leave devices in database
- They'll show as "offline" in dashboard
- No data loss
- When renovation done, tablets reconnect automatically

**Option B: Temporarily delete**
- Delete all 10 devices
- Lose alert history
- Re-add after renovation

**Recommendation:** Keep them! Just let them show offline.

### Scenario 4: Moving Tablet to Different Room

**Problem:** Tablet TAB-150 needs to move from Room 150 to Room 160.

**Solution:** Currently requires delete & re-add:

1. Delete TAB-150 from dashboard
2. Add new: TAB-160 for Room 160
3. Reconfigure tablet with new token

**Future:** Update endpoint to modify room ID without delete/add.

---

## Part 7: Monitoring & Verification

### Check All Devices via API

```powershell
# List all devices
$devices = Invoke-RestMethod -Uri "http://localhost:8080/api/devices"
$devices | Format-Table deviceId, roomId, status, lastSeen
```

**Example output:**
```
deviceId  roomId  status  lastSeen
--------  ------  ------  --------
TAB-101   101     ok      2025-12-21T14:30:00
TAB-102   102     breach  2025-12-21T14:28:00
TAB-103   103     ok      2025-12-21T14:31:00
```

### Check Device Count

```powershell
# Count devices
$devices = Invoke-RestMethod -Uri "http://localhost:8080/api/devices"
Write-Host "Total tablets: $($devices.Count)"
```

### Find Offline Devices

```powershell
# Find devices not seen in 10+ minutes
$devices = Invoke-RestMethod -Uri "http://localhost:8080/api/devices"
$now = Get-Date

$offline = $devices | Where-Object {
    $lastSeen = [DateTime]::Parse($_.lastSeen)
    ($now - $lastSeen).TotalMinutes -gt 10
}

Write-Host "Offline devices:"
$offline | Format-Table deviceId, roomId, lastSeen
```

---

## Part 8: Security Best Practices

### Token Security

🔒 **JWT tokens are sensitive!** Treat them like passwords.

**Do:**
- ✅ Store tokens securely on tablets
- ✅ Use HTTPS in production
- ✅ Rotate tokens periodically
- ✅ Delete devices when tablets are decommissioned

**Don't:**
- ❌ Share tokens publicly
- ❌ Commit tokens to Git repositories
- ❌ Log tokens in plain text
- ❌ Email tokens unencrypted

### Access Control

**Dashboard Access:**
- Should be password-protected in production
- Only hotel owners/managers should access
- Consider adding authentication to dashboard

**API Access:**
- Use firewall rules to restrict access
- Only allow tablets and dashboard to connect
- Block public internet access to API

---

## Part 9: Troubleshooting

### "Device already exists" error

**Problem:** Trying to add a device with an existing Device ID.

**Solution:**
1. Check if device is already registered
2. If it's an old device, delete it first
3. Use a different Device ID

### Tablet not appearing in dashboard after adding

**Causes:**
1. Tablet not configured with token yet
2. Tablet not connected to WiFi
3. Server URL wrong in tablet
4. Firewall blocking connection

**Solution:**
1. Check tablet has WiFi connection
2. Verify server URL in tablet settings
3. Check firewall allows port 8080
4. Check backend logs: `docker-compose logs -f backend`

### Delete button not working

**Causes:**
1. API server down
2. Network connection issue
3. Device doesn't exist

**Solution:**
1. Check API health: `curl http://localhost:8080/health`
2. Check browser console for errors (F12)
3. Try API method: `curl -X DELETE http://localhost:8080/api/devices/TAB-101`

### Token expired

**Problem:** Tablet says "Unauthorized" or "Invalid token"

**Solution:**
1. Delete device from dashboard
2. Re-add device (generates new token)
3. Reconfigure tablet with new token

---

## Part 10: Quick Reference

### Dashboard Operations

| Action | Steps | Result |
|--------|-------|--------|
| **Add tablet** | Click "Add Tablet" → Fill form → Click "Add" | Device registered + token generated |
| **Delete tablet** | Click "Delete" on device card → Confirm | Device + alerts deleted |
| **View all tablets** | Dashboard homepage | Real-time list of all devices |
| **Refresh list** | Wait 3 seconds | Auto-refresh every 3 seconds |

### API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/devices/quick-add` | POST | Add new device (owner) |
| `/api/devices/{id}` | DELETE | Delete device (owner) |
| `/api/devices` | GET | List all devices |
| `/api/devices/register` | POST | Device self-registration (deprecated) |

### Device States

| Status | Meaning | Action Needed |
|--------|---------|---------------|
| **ok** | Online and protected | None |
| **breach** | Removed from room | Investigate immediately |
| **offline** | Not responding | Check tablet connection |
| **battery_low** | <20% battery | Charge tablet |

---

## Summary

✅ **Adding tablets** - One click from dashboard, auto-generates token  
✅ **Deleting tablets** - One click delete with confirmation  
✅ **Bulk operations** - PowerShell scripts for multiple tablets  
✅ **Token management** - Automatically generated JWT tokens  
✅ **Real-time sync** - Changes appear immediately in dashboard  

**Next Steps:**
1. Open dashboard: http://localhost:3000
2. Click "Add Tablet" and try it out
3. Configure a physical tablet with the token
4. Test the connection

Your owner dashboard is now a complete tablet management system! 🎉
