# Owner Dashboard - Tablet Management Workflow

Visual guide showing how to add/delete tablets from the owner dashboard.

---

## Complete Workflow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     OWNER DASHBOARD                              │
│                  http://localhost:3000                           │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Hotel Tablets - Owner Dashboard      [+ Add Tablet]     │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  Tablet Fleet (3)                                                │
│  ┌──────────────────────────────────┐  ┌─────────────────────┐ │
│  │ TAB-101 — Room 101    [Delete]   │  │ TAB-102 — Room 102  │ │
│  │ Last seen: 2:30 PM               │  │ [Delete]            │ │
│  │ Status: ok    Battery: 85%       │  │ Status: breach      │ │
│  │               RSSI: -65 dBm      │  │ Battery: 45%        │ │
│  └──────────────────────────────────┘  └─────────────────────┘ │
│                                                                  │
│  ┌──────────────────────────────────┐                           │
│  │ TAB-103 — Room 103    [Delete]   │                           │
│  │ Last seen: 2:32 PM               │                           │
│  │ Status: ok    Battery: 92%       │                           │
│  │               RSSI: -58 dBm      │                           │
│  └──────────────────────────────────┘                           │
└─────────────────────────────────────────────────────────────────┘
```

---

## Add Tablet Flow

```
Owner Dashboard
      │
      │ (1) Click "Add Tablet"
      ▼
┌─────────────────────────┐
│   Add Tablet Modal      │
│                         │
│  Device ID: TAB-104     │
│  Room ID:   104         │
│  Hotel ID:  default     │
│                         │
│  [Cancel]  [Add Tablet] │
└─────────────────────────┘
      │
      │ (2) Click "Add Tablet"
      ▼
┌─────────────────────────────────────────────┐
│  POST /api/devices/quick-add                │
│  {                                          │
│    "deviceId": "TAB-104",                   │
│    "roomId": "104",                         │
│    "hotelId": "default"                     │
│  }                                          │
└─────────────────────────────────────────────┘
      │
      │ (3) Backend processes
      ▼
┌──────────────────────────────────────────────┐
│  Backend Actions:                            │
│  ✅ Save device to MongoDB                   │
│  ✅ Generate JWT token                       │
│  ✅ Broadcast to SSE clients                 │
│  ✅ Return response with token               │
└──────────────────────────────────────────────┘
      │
      │ (4) Response
      ▼
┌──────────────────────────────────────────────┐
│  Success Popup                               │
│                                              │
│  Device TAB-104 added successfully!          │
│                                              │
│  JWT Token (save this):                      │
│  eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...   │
│                                              │
│  Configure this token in the tablet app.     │
│                                              │
│  [OK]                                        │
└──────────────────────────────────────────────┘
      │
      │ (5) Owner copies token
      ▼
┌──────────────────────────────────────────────┐
│  Tablet appears in dashboard immediately     │
│                                              │
│  TAB-104 — Room 104          [Delete]        │
│  Last seen: Just now                         │
│  Status: ok    Battery: —                    │
│                RSSI: —                       │
└──────────────────────────────────────────────┘
      │
      │ (6) Configure physical tablet
      ▼
┌──────────────────────────────────────────────┐
│  Physical Tablet (Android)                   │
│                                              │
│  1. Install app                              │
│  2. Open app → Settings                      │
│  3. Enter server URL + token                 │
│  4. Save and restart                         │
└──────────────────────────────────────────────┘
      │
      │ (7) Tablet sends first heartbeat
      ▼
┌──────────────────────────────────────────────┐
│  Dashboard updates automatically             │
│                                              │
│  TAB-104 — Room 104          [Delete]        │
│  Last seen: 2:35 PM                          │
│  Status: ok    Battery: 78%                  │
│                RSSI: -62 dBm                 │
└──────────────────────────────────────────────┘
```

---

## Delete Tablet Flow

```
Owner Dashboard
      │
      │ (1) Click "Delete" on device card
      ▼
┌─────────────────────────────────────────────┐
│  Confirmation Dialog                        │
│                                             │
│  Are you sure you want to delete device     │
│  TAB-104?                                   │
│                                             │
│  This will also delete all associated       │
│  alerts.                                    │
│                                             │
│  [Cancel]  [OK]                             │
└─────────────────────────────────────────────┘
      │
      │ (2) Click "OK"
      ▼
┌─────────────────────────────────────────────┐
│  DELETE /api/devices/TAB-104                │
└─────────────────────────────────────────────┘
      │
      │ (3) Backend processes
      ▼
┌──────────────────────────────────────────────┐
│  Backend Actions:                            │
│  ❌ Delete device from MongoDB               │
│  ❌ Delete all alerts for device             │
│  ✅ Broadcast deletion to SSE clients        │
│  ✅ Return success response                  │
└──────────────────────────────────────────────┘
      │
      │ (4) Response
      ▼
┌──────────────────────────────────────────────┐
│  Success Alert                               │
│                                              │
│  Device TAB-104 deleted successfully!        │
│                                              │
│  [OK]                                        │
└──────────────────────────────────────────────┘
      │
      │ (5) Dashboard refreshes
      ▼
┌──────────────────────────────────────────────┐
│  Device removed from dashboard               │
│                                              │
│  TAB-104 no longer appears in list           │
│  All TAB-104 alerts deleted                  │
└──────────────────────────────────────────────┘
      │
      │ (6) Tablet tries to connect (if still powered)
      ▼
┌──────────────────────────────────────────────┐
│  Physical Tablet                             │
│                                              │
│  Sends heartbeat with JWT token              │
│  Backend rejects: "Unauthorized"             │
│  Token no longer valid                       │
└──────────────────────────────────────────────┘
```

---

## System Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                     OWNER WORKFLOW                            │
└──────────────────────────────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│   Browser    │   │   Browser    │   │   Browser    │
│ (Dashboard)  │   │ (Dashboard)  │   │ (Dashboard)  │
│ localhost:   │   │ Manager's    │   │ Front Desk   │
│   3000       │   │ Laptop       │   │ Computer     │
└──────────────┘   └──────────────┘   └──────────────┘
        │                   │                   │
        └───────────────────┼───────────────────┘
                            │
                            ▼
                ┌─────────────────────┐
                │   Backend API       │
                │   FastAPI           │
                │   Port 8080         │
                │                     │
                │   Endpoints:        │
                │   • POST /quick-add │
                │   • DELETE /{id}    │
                │   • GET /devices    │
                └─────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│  MongoDB     │   │    Redis     │   │   Celery     │
│  Atlas       │   │  (SSE/PubSub)│   │  (Async      │
│              │   │              │   │   Tasks)     │
│  Collections:│   │  Broadcasts  │   │              │
│  • devices   │   │  real-time   │   │  Email/Slack │
│  • alerts    │   │  updates     │   │  alerts      │
│  • rooms     │   └──────────────┘   └──────────────┘
└──────────────┘
        ▲
        │
        │ (Tablets send heartbeats)
        │
┌───────┴────────────────────────────────────────┐
│                 TABLETS                        │
├────────────────┬────────────────┬──────────────┤
│  TAB-101       │  TAB-102       │  TAB-103     │
│  Room 101      │  Room 102      │  Room 103    │
│  Android       │  Android       │  Android     │
│  JWT: abc...   │  JWT: def...   │  JWT: ghi... │
└────────────────┴────────────────┴──────────────┘
```

---

## Data Flow: Add Tablet

```
1. OWNER CLICKS "ADD TABLET"
   ↓
2. MODAL OPENS WITH FORM
   Device ID: [_______]
   Room ID:   [_______]
   Hotel ID:  [_______]
   ↓
3. OWNER FILLS FORM
   Device ID: TAB-105
   Room ID:   105
   Hotel ID:  default
   ↓
4. CLICK "ADD TABLET"
   ↓
5. JAVASCRIPT SENDS REQUEST
   fetch('/api/devices/quick-add', {
     method: 'POST',
     body: JSON.stringify({
       deviceId: 'TAB-105',
       roomId: '105',
       hotelId: 'default'
     })
   })
   ↓
6. BACKEND VALIDATES
   ✓ Device ID not already in use
   ✓ Required fields present
   ↓
7. BACKEND CREATES DEVICE
   MongoDB.insert({
     _id: 'TAB-105',
     device_id: 'TAB-105',
     room_id: '105',
     hotel_id: 'default',
     status: 'ok',
     last_seen: NOW
   })
   ↓
8. BACKEND GENERATES TOKEN
   JWT.sign({
     sub: 'TAB-105',
     room_id: '105',
     hotel_id: 'default',
     type: 'device',
     exp: NOW + 30 days
   })
   ↓
9. BACKEND BROADCASTS EVENT
   Redis.publish('device_added', {
     deviceId: 'TAB-105',
     roomId: '105',
     hotelId: 'default'
   })
   ↓
10. BACKEND RETURNS RESPONSE
    {
      "ok": true,
      "message": "Device TAB-105 added",
      "token": "eyJhbGci...",
      "device": {...}
    }
    ↓
11. DASHBOARD SHOWS TOKEN POPUP
    Alert: "Device added! Token: eyJhbGci..."
    ↓
12. OWNER COPIES TOKEN
    Token saved to clipboard
    ↓
13. DASHBOARD AUTO-REFRESHES
    Device TAB-105 appears in list
    ↓
14. OWNER CONFIGURES TABLET
    Install app → Enter token → Start
    ↓
15. TABLET SENDS FIRST HEARTBEAT
    POST /api/heartbeat
    Authorization: Bearer eyJhbGci...
    ↓
16. BACKEND UPDATES DEVICE
    MongoDB.update({_id: 'TAB-105'}, {
      battery: 80,
      rssi: -65,
      last_seen: NOW
    })
    ↓
17. DASHBOARD SHOWS FULL STATUS
    TAB-105 — Room 105
    Status: ok | Battery: 80% | RSSI: -65 dBm
    ↓
18. ✅ TABLET FULLY OPERATIONAL
```

---

## Data Flow: Delete Tablet

```
1. OWNER CLICKS "DELETE" BUTTON
   (On device card: TAB-105)
   ↓
2. CONFIRMATION DIALOG
   "Are you sure you want to delete TAB-105?
    This will delete all alerts."
    [Cancel] [OK]
   ↓
3. OWNER CLICKS "OK"
   ↓
4. JAVASCRIPT SENDS REQUEST
   fetch('/api/devices/TAB-105', {
     method: 'DELETE'
   })
   ↓
5. BACKEND CHECKS IF EXISTS
   device = MongoDB.findOne({_id: 'TAB-105'})
   ✓ Device found
   ↓
6. BACKEND DELETES DEVICE
   MongoDB.deleteOne({_id: 'TAB-105'})
   ↓
7. BACKEND DELETES ALERTS
   MongoDB.deleteMany({device_id: 'TAB-105'})
   ↓
8. BACKEND BROADCASTS EVENT
   Redis.publish('device_deleted', {
     deviceId: 'TAB-105'
   })
   ↓
9. BACKEND RETURNS SUCCESS
   {
     "ok": true,
     "message": "Device TAB-105 deleted"
   }
   ↓
10. DASHBOARD SHOWS ALERT
    "Device TAB-105 deleted successfully!"
    ↓
11. DASHBOARD AUTO-REFRESHES
    TAB-105 removed from list
    All TAB-105 alerts gone
    ↓
12. IF TABLET STILL POWERED ON
    Tablet sends heartbeat
    ↓
13. BACKEND REJECTS REQUEST
    JWT verification fails
    Device not found in database
    Returns: 401 Unauthorized
    ↓
14. TABLET SHOWS ERROR
    "Connection failed"
    or "Unauthorized"
    ↓
15. ✅ TABLET DECOMMISSIONED
```

---

## Real-Time Updates

```
┌──────────────┐
│  Dashboard 1 │  (Owner's laptop)
│  Browser     │
└──────┬───────┘
       │
       │  SSE Connection: /api/events
       │  Listens for updates
       │
       ▼
┌─────────────────────────────────┐
│  Backend - Redis Pub/Sub        │
│                                 │
│  Channels:                      │
│  • device_added                 │
│  • device_deleted               │
│  • device_update                │
│  • alert                        │
└─────────────────────────────────┘
       ▲
       │
       │  Broadcasts events
       │
       ├──────────────┬──────────────┬──────────────┐
       │              │              │              │
       ▼              ▼              ▼              ▼
┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
│Dashboard │   │Dashboard │   │Dashboard │   │  Tablet  │
│Browser 1 │   │Browser 2 │   │Browser 3 │   │ (sends)  │
│(Owner)   │   │(Manager) │   │(FrontDesk│   │          │
└──────────┘   └──────────┘   └──────────┘   └──────────┘

All dashboards update simultaneously when:
• New tablet added
• Tablet deleted  
• Status changes (breach/ok)
• Battery updates
• Alerts triggered
```

---

## Security Model

```
┌─────────────────────────────────────────────┐
│              OWNER DASHBOARD                │
│                                             │
│  No authentication (to be added)            │
│  Trusted network access only                │
│  Full control:                              │
│  • Add tablets                              │
│  • Delete tablets                           │
│  • View all devices                         │
│  • Acknowledge alerts                       │
└─────────────────────────────────────────────┘
                    │
                    │ HTTP Requests
                    │ (No auth header needed)
                    ▼
┌─────────────────────────────────────────────┐
│              BACKEND API                    │
│                                             │
│  Owner endpoints (no auth required):        │
│  • POST /api/devices/quick-add              │
│  • DELETE /api/devices/{id}                 │
│  • GET /api/devices                         │
│  • GET /api/alerts/recent                   │
│                                             │
│  Device endpoints (JWT required):           │
│  • POST /api/heartbeat                      │
│  • POST /api/alert/breach                   │
│  • POST /api/alert/battery                  │
└─────────────────────────────────────────────┘
                    │
                    │ Validates JWT
                    ▼
┌─────────────────────────────────────────────┐
│              TABLETS                        │
│                                             │
│  JWT token in Authorization header          │
│  Authorization: Bearer eyJhbGci...          │
│                                             │
│  Token contains:                            │
│  • Device ID                                │
│  • Room ID                                  │
│  • Hotel ID                                 │
│  • Expiration (30 days)                     │
└─────────────────────────────────────────────┘
```

---

## Database Schema

```
MongoDB Collection: devices
┌────────────────────────────────────────────┐
│  _id: "TAB-101"                            │
│  device_id: "TAB-101"                      │
│  room_id: "101"                            │
│  hotel_id: "default"                       │
│  status: "ok"                              │
│  battery: 85                               │
│  rssi: -65                                 │
│  ip: "192.168.1.50"                        │
│  last_seen: 2025-12-21T14:30:00Z           │
└────────────────────────────────────────────┘

MongoDB Collection: alerts
┌────────────────────────────────────────────┐
│  _id: ObjectId("...")                      │
│  type: "breach"                            │
│  device_id: "TAB-101"                      │
│  room_id: "101"                            │
│  payload: {                                │
│    deviceId: "TAB-101",                    │
│    roomId: "101",                          │
│    rssi: -95                               │
│  }                                         │
│  ts: 2025-12-21T14:25:00Z                  │
│  acknowledged: false                       │
│  acknowledged_by: null                     │
│  acknowledged_at: null                     │
│  notes: null                               │
└────────────────────────────────────────────┘
```

---

**Full documentation:** [TABLET_MANAGEMENT.md](TABLET_MANAGEMENT.md)

**Quick start:** [QUICKSTART_TABLET_MANAGEMENT.md](QUICKSTART_TABLET_MANAGEMENT.md)
