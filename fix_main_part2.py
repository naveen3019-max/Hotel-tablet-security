import sys

with open("backend-api/main.py", "r", encoding="utf-8") as f:
    content = f.read()

# 1. Update /api/alerts/recent
target_alerts = '''@app.get("/api/alerts/recent")
async def recent_alerts(limit: int = 100, hotel_id: Optional[str] = None):
    """Get recent alerts with optional hotel filter"""
    query = {}
    if hotel_id:
        query["hotel_id"] = hotel_id'''
        
replace_alerts = '''@app.get("/api/alerts/recent")
async def recent_alerts(limit: int = 100, hotel_id: Optional[str] = None, user=Depends(get_current_user)):
    """Get recent alerts with optional hotel filter"""
    query = {}
    if user["role"] == "hotel_admin":
        query["hotel_id"] = user["hotel_id"]
    elif hotel_id:
        query["hotel_id"] = hotel_id'''
content = content.replace(target_alerts, replace_alerts)


# 2. Update /api/devices
target_devices = '''@app.get("/api/devices")
async def list_devices(hotel_id: Optional[str] = None):
    """List all devices with optional hotel filter"""
    query = {}
    if hotel_id:
        query["hotel_id"] = hotel_id
    
    # Optimized with projection - include both camelCase and snake_case fields
    cursor = devices_collection.find(
        query,
        projection={"room_id": 1, "roomId": 1, "hotel_id": 1, "status": 1, "battery": 1, "rssi": 1, "ip": 1, "last_seen": 1}
    )
    devices = await cursor.to_list(length=1000)
    
    # List comprehension for better performance
    return [{
        "id": d["_id"],
        "deviceId": d["_id"],
        "roomId": d.get("roomId") or d.get("room_id"),  # Check both field names
        "hotelId": d.get("hotel_id"),
        "status": d.get("status"),
        "battery": d.get("battery"),
        "batteryLevel": d.get("battery"),
        "rssi": d.get("rssi"),
        "ip": d.get("ip"),
        "lastSeen": to_ist_isoformat(d.get("last_seen"))
    } for d in devices]'''

replace_devices = '''@app.get("/api/devices")
async def list_devices(hotel_id: Optional[str] = None, user=Depends(get_current_user)):
    """List all devices with optional hotel filter"""
    query = {}
    if user["role"] == "hotel_admin":
        query["hotel_id"] = user["hotel_id"]
    elif hotel_id:
        query["hotel_id"] = hotel_id
    
    # Optimized with projection - include both camelCase and snake_case fields
    cursor = devices_collection.find(
        query,
        projection={"room_id": 1, "roomId": 1, "hotel_id": 1, "status": 1, "battery": 1, "rssi": 1, "ip": 1, "last_seen": 1, "staff_name": 1, "registered_by": 1}
    )
    devices = await cursor.to_list(length=1000)
    
    # List comprehension for better performance
    return [{
        "id": d["_id"],
        "deviceId": d["_id"],
        "roomId": d.get("roomId") or d.get("room_id"),  # Check both field names
        "hotelId": d.get("hotel_id"),
        "status": d.get("status"),
        "battery": d.get("battery"),
        "batteryLevel": d.get("battery"),
        "rssi": d.get("rssi"),
        "ip": d.get("ip"),
        "lastSeen": to_ist_isoformat(d.get("last_seen")),
        "staffName": d.get("staff_name"),
        "registeredBy": d.get("registered_by")
    } for d in devices]'''
content = content.replace(target_devices, replace_devices)

# 3. Update websocket_dashboard
target_ws = '''@app.websocket("/ws/dashboard")
async def websocket_dashboard(websocket: WebSocket):
    """Real-time WebSocket endpoint for the dashboard.
    Each dashboard tab that opens creates one persistent connection here.
    The server pushes breach/heartbeat/offline events to all connections instantly.
    """
    await ws_manager.connect(websocket)  # ← NEW: Register this client'''

replace_ws = '''@app.websocket("/ws/dashboard")
async def websocket_dashboard(websocket: WebSocket, token: Optional[str] = None):
    """Real-time WebSocket endpoint for the dashboard.
    Each dashboard tab that opens creates one persistent connection here.
    The server pushes breach/heartbeat/offline events to all connections instantly.
    """
    hotel_id = "ALL"
    if token:
        try:
            payload = AuthService.verify_token(token)
            hotel_id = payload.get("hotel_id", "ALL")
        except:
            pass
    await ws_manager.connect(websocket, hotel_id)  # ← NEW: Register this client with hotel_id'''
content = content.replace(target_ws, replace_ws)


# 4. Update broadcast_event
target_broadcast = '''async def broadcast_event(event_type: str, data: dict):
    """Broadcast event to all SSE clients via Redis or in-memory queue, and to all WebSocket clients."""
    message = {
        "type": event_type,
        "data": data,
        "timestamp": get_ist_time().isoformat()
    }
    message_str = json.dumps(message)

    # ← NEW: Push to ALL connected WebSocket dashboard clients instantly
    await ws_manager.broadcast(message)'''

replace_broadcast = '''async def broadcast_event(event_type: str, data: dict):
    """Broadcast event to all SSE clients via Redis or in-memory queue, and to all WebSocket clients."""
    message = {
        "type": event_type,
        "data": data,
        "timestamp": get_ist_time().isoformat()
    }
    message_str = json.dumps(message)
    
    hotel_id = data.get("hotel_id")
    if not hotel_id and data.get("deviceId"):
        device = await devices_collection.find_one({"_id": data["deviceId"]})
        if device:
            hotel_id = device.get("hotel_id")
            data["hotel_id"] = hotel_id

    # ← NEW: Push to connected WebSocket dashboard clients
    await ws_manager.broadcast(message, hotel_id)'''
content = content.replace(target_broadcast, replace_broadcast)

with open("backend-api/main.py", "w", encoding="utf-8") as f:
    f.write(content)

print("Updates applied successfully.")
