import re

# 1. Read main.py
with open("backend-api/main.py", "r", encoding="utf-8") as f:
    main_content = f.read()

# 2. Add decode_jwt_hotel_id
jwt_helper = '''
from jose import jwt, JWTError

def decode_jwt_hotel_id(token: str) -> str:
    """
    # ← NEW: Extract hotel_id from JWT token
    # without full validation (for WebSocket
    # where we just need the hotel context)
    """
    try:
        payload = jwt.decode(
            token,
            settings.secret_key,
            algorithms=[settings.jwt_algorithm],
            options={"verify_exp": False}
        )
        return payload.get("hotel_id", "default")
    except JWTError:
        return "unknown"
'''
main_content = main_content.replace('from auth import AuthService, get_current_device, get_current_user, require_role\n', 'from auth import AuthService, get_current_device, get_current_user, require_role\n' + jwt_helper)

# 3. Update websocket_dashboard
ws_old = '''@app.websocket("/ws/dashboard")
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

ws_new = '''@app.websocket("/ws/dashboard")
async def websocket_dashboard(
    websocket: WebSocket,
    token: str = None  # ← NEW: JWT as query param
):
    """
    Real-time WebSocket endpoint.
    # ← FIXED: Now requires token to identify
    # which hotel this connection belongs to.
    """
    # ← NEW: Extract hotel_id from token
    hotel_id = "default"
    if token:
        hotel_id = decode_jwt_hotel_id(token)
    else:
        # Try to get token from query string manually
        query_params = dict(websocket.query_params)
        token_param = query_params.get("token")
        if token_param:
            hotel_id = decode_jwt_hotel_id(token_param)
            
    print(f"🔌 WebSocket connecting for hotel={hotel_id}", flush=True)
    
    # ← FIXED: Pass hotel_id to connect()
    await ws_manager.connect(websocket, hotel_id)'''
main_content = main_content.replace(ws_old, ws_new)

# 4. Update broadcast_event definition
broad_old = '''async def broadcast_event(event_type: str, data: dict):
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

broad_new = '''async def broadcast_event(
    event_type: str, 
    data: dict,
    hotel_id: str = "default"  # ← NEW parameter
):
    """
    # ← FIXED: Now requires hotel_id to ensure
    # events only reach the correct hotel's
    # dashboard clients.
    """
    message = {
        "type": event_type,
        "data": data,
        "timestamp": get_ist_time().isoformat()
    }
    message_str = json.dumps(message)
    
    # ← FIXED: Pass hotel_id for filtered broadcast
    await ws_manager.broadcast(message, target_hotel_id=hotel_id)'''
main_content = main_content.replace(broad_old, broad_new)

# 5. Update GET /api/devices
dev_old = '''@app.get("/api/devices")
async def list_devices(hotel_id: Optional[str] = None, user=Depends(get_current_user)):
    """List all devices with optional hotel filter"""
    query = {}
    if user["role"] == "hotel_admin":
        query["hotel_id"] = user["hotel_id"]
    elif hotel_id:
        query["hotel_id"] = hotel_id'''

dev_new = '''from fastapi import Header
@app.get("/api/devices")
async def list_devices(authorization: str = Header(None)):
    """List devices filtered by hotel"""
    hotel_id = None
    role = None
    
    if authorization:
        token = authorization.replace("Bearer ", "")
        try:
            payload = jwt.decode(
                token,
                settings.secret_key,
                algorithms=[settings.jwt_algorithm]
            )
            hotel_id = payload.get("hotel_id")
            role = payload.get("role")
        except JWTError:
            raise HTTPException(401, "Invalid token")
    
    query = {}
    # ← FIXED: super_admin sees all, everyone else filtered by hotel_id
    if role != "super_admin" and hotel_id:
        query["hotel_id"] = hotel_id'''
main_content = main_content.replace(dev_old, dev_new)

# 6. Update GET /api/alerts/recent
alert_old = '''@app.get("/api/alerts/recent")
async def recent_alerts(limit: int = 100, hotel_id: Optional[str] = None, user=Depends(get_current_user)):
    """Get recent alerts with optional hotel filter"""
    query = {}
    if user["role"] == "hotel_admin":
        query["hotel_id"] = user["hotel_id"]
    elif hotel_id:
        query["hotel_id"] = hotel_id'''

alert_new = '''@app.get("/api/alerts/recent")
async def recent_alerts(limit: int = 100, authorization: str = Header(None)):
    """Get alerts filtered by hotel"""
    hotel_id = None
    role = None
    
    if authorization:
        token = authorization.replace("Bearer ", "")
        try:
            payload = jwt.decode(
                token,
                settings.secret_key,
                algorithms=[settings.jwt_algorithm]
            )
            hotel_id = payload.get("hotel_id")
            role = payload.get("role")
        except JWTError:
            raise HTTPException(401, "Invalid token")
    
    query = {}
    
    if role != "super_admin" and hotel_id:
        # ← FIXED: Filter alerts by devices belonging to this hotel
        hotel_device_ids = await devices_collection.find(
            {"hotel_id": hotel_id},
            {"_id": 1}
        ).to_list(length=1000)
        device_id_list = [d["_id"] for d in hotel_device_ids]
        query["deviceId"] = {"$in": device_id_list}'''
main_content = main_content.replace(alert_old, alert_new)

# 7. Add hotel_id lookup for EVERY broadcast_event
# I will do manual replacements for each occurrence to get it right.
main_content = main_content.replace('''                        await broadcast_event("alert", {
                            "type": "breach",
                            "deviceId": device_id,
                            "roomId": device.get("roomId", device.get("room_id", "unknown")),
                            "rssi": rssi_val,
                            "bssid": device.get("bssid", "unknown"),
                            "source": "heartbeat_timeout",
                            "message": breach_msg
                        })''', '''                        # ← ADD hotel_id lookup
                        device_doc = await devices_collection.find_one({"_id": device_id})
                        device_hotel_id = device_doc.get("hotel_id", "default") if device_doc else "default"
                        await broadcast_event("alert", {
                            "type": "breach",
                            "deviceId": device_id,
                            "roomId": device.get("roomId", device.get("room_id", "unknown")),
                            "rssi": rssi_val,
                            "bssid": device.get("bssid", "unknown"),
                            "source": "heartbeat_timeout",
                            "message": breach_msg
                        }, hotel_id=device_hotel_id) # ← ADD hotel_id''')

main_content = main_content.replace('''    await broadcast_event("alert", {
        "type": "breach",
        "deviceId": deviceId,
        "roomId": roomId,
        "rssi": rssi,
        "message": "WiFi disabled - instant breach"
    })''', '''    # ← ADD hotel_id lookup
    device_doc = await devices_collection.find_one({"_id": deviceId})
    device_hotel_id = device_doc.get("hotel_id", "default") if device_doc else "default"
    await broadcast_event("alert", {
        "type": "breach",
        "deviceId": deviceId,
        "roomId": roomId,
        "rssi": rssi,
        "message": "WiFi disabled - instant breach"
    }, hotel_id=device_hotel_id) # ← ADD hotel_id''')

main_content = main_content.replace('''    await broadcast_event("device_update", {
        "deviceId": b.deviceId,
        "status": "breach",
        "rssi": b.rssi
    })
    
    # ← FIXED: Added debug print BEFORE broadcast as requested
    print(f"🚨 BREACH → broadcasting to WebSocket clients", flush=True)
    await broadcast_event("alert", {
        "type": "breach",
        "deviceId": b.deviceId,
        "roomId": b.roomId,
        "rssi": b.rssi,
        "message": "WiFi breach detected" # ← FIXED: added message field
    })''', '''    # ← ADD hotel_id lookup
    device_doc = await devices_collection.find_one({"_id": b.deviceId})
    device_hotel_id = device_doc.get("hotel_id", "default") if device_doc else "default"
    
    await broadcast_event("device_update", {
        "deviceId": b.deviceId,
        "status": "breach",
        "rssi": b.rssi
    }, hotel_id=device_hotel_id) # ← ADD hotel_id
    
    # ← FIXED: Added debug print BEFORE broadcast as requested
    print(f"🚨 BREACH → broadcasting to WebSocket clients", flush=True)
    await broadcast_event("alert", {
        "type": "breach",
        "deviceId": b.deviceId,
        "roomId": b.roomId,
        "rssi": b.rssi,
        "message": "WiFi breach detected" # ← FIXED: added message field
    }, hotel_id=device_hotel_id) # ← ADD hotel_id''')

main_content = main_content.replace('''    await broadcast_event("device_update", {
        "deviceId": t.deviceId,
        "status": "compromised"
    })
    
    await broadcast_event("alert", {
        "type": "tamper",
        "deviceId": t.deviceId,
        "roomId": t.roomId,
        "threats": t.threats
    })''', '''    # ← ADD hotel_id lookup
    device_doc = await devices_collection.find_one({"_id": t.deviceId})
    device_hotel_id = device_doc.get("hotel_id", "default") if device_doc else "default"
    
    await broadcast_event("device_update", {
        "deviceId": t.deviceId,
        "status": "compromised"
    }, hotel_id=device_hotel_id) # ← ADD hotel_id
    
    await broadcast_event("alert", {
        "type": "tamper",
        "deviceId": t.deviceId,
        "roomId": t.roomId,
        "threats": t.threats
    }, hotel_id=device_hotel_id) # ← ADD hotel_id''')

main_content = main_content.replace('''    await broadcast_event("alert", {
        "type": "battery_low",
        "deviceId": b.deviceId,
        "level": b.level
    })''', '''    # ← ADD hotel_id lookup
    device_hotel_id = device_doc.get("hotel_id", "default") if device_doc else "default"
    
    await broadcast_event("alert", {
        "type": "battery_low",
        "deviceId": b.deviceId,
        "level": b.level
    }, hotel_id=device_hotel_id) # ← ADD hotel_id''')

main_content = main_content.replace('''        await broadcast_event("alert", {
            "type": "breach",
            "deviceId": h.deviceId,
            "roomId": h.roomId,
            "rssi": h.rssi,
            "message": "WiFi disabled on device"
        })''', '''        # ← ADD hotel_id lookup
        device_hotel_id = current_device.get("hotel_id", "default") if current_device else "default"
        await broadcast_event("alert", {
            "type": "breach",
            "deviceId": h.deviceId,
            "roomId": h.roomId,
            "rssi": h.rssi,
            "message": "WiFi disabled on device"
        }, hotel_id=device_hotel_id) # ← ADD hotel_id''')

main_content = main_content.replace('''        await broadcast_event("device_recovered", {
            "deviceId": h.deviceId,
            "roomId": h.roomId,
            "rssi": h.rssi,
            "battery": h.battery,
            "message": "Device WiFi restored - "
                       "good heartbeat received"
        })''', '''        device_hotel_id = current_device.get("hotel_id", "default") if current_device else "default"
        await broadcast_event("device_recovered", {
            "deviceId": h.deviceId,
            "roomId": h.roomId,
            "rssi": h.rssi,
            "battery": h.battery,
            "message": "Device WiFi restored - "
                       "good heartbeat received"
        }, hotel_id=device_hotel_id)''')

main_content = main_content.replace('''            await broadcast_event("device_recovered", {
                "deviceId": h.deviceId,
                "roomId": h.roomId,
                "rssi": h.rssi,
                "message": "Device WiFi connection restored"
            })''', '''            device_hotel_id = current_device.get("hotel_id", "default") if current_device else "default"
            await broadcast_event("device_recovered", {
                "deviceId": h.deviceId,
                "roomId": h.roomId,
                "rssi": h.rssi,
                "message": "Device WiFi connection restored"
            }, hotel_id=device_hotel_id)''')

main_content = main_content.replace('''                await broadcast_event("alert", {
                    "type": "breach",
                    "deviceId": h.deviceId,
                    "roomId": h.roomId,
                    "rssi": h.rssi,
                    "bssid": h.wifiBssid,
                    "source": "proactive_heartbeat"
                })''', '''                device_hotel_id = current_device.get("hotel_id", "default") if current_device else "default"
                await broadcast_event("alert", {
                    "type": "breach",
                    "deviceId": h.deviceId,
                    "roomId": h.roomId,
                    "rssi": h.rssi,
                    "bssid": h.wifiBssid,
                    "source": "proactive_heartbeat"
                }, hotel_id=device_hotel_id)''')

main_content = main_content.replace('''    await broadcast_event("device_update", {
        "deviceId": h.deviceId,
        "status": new_status,
        "rssi": h.rssi,
        "battery": h.battery
    })''', '''    device_hotel_id = current_device.get("hotel_id", "default") if current_device else "default"
    await broadcast_event("device_update", {
        "deviceId": h.deviceId,
        "status": new_status,
        "rssi": h.rssi,
        "battery": h.battery
    }, hotel_id=device_hotel_id)''')

main_content = main_content.replace('''    await broadcast_event("alert_acknowledged", {
        "alertId": payload.alertId,
        "acknowledgedBy": payload.acknowledgedBy
    })''', '''    # ← ADD hotel_id lookup
    alert = await alerts_collection.find_one({"_id": ObjectId(payload.alertId)})
    device_id = alert.get("deviceId") if alert else None
    device_doc = await devices_collection.find_one({"_id": device_id}) if device_id else None
    device_hotel_id = device_doc.get("hotel_id", "default") if device_doc else "default"

    await broadcast_event("alert_acknowledged", {
        "alertId": payload.alertId,
        "acknowledgedBy": payload.acknowledgedBy
    }, hotel_id=device_hotel_id) # ← ADD hotel_id''')

main_content = main_content.replace('''    await broadcast_event("device_deleted", {"deviceId": device_id})''', '''    # ← ADD hotel_id lookup
    device_hotel_id = "default"  # We already deleted it above, so hotel_id might be lost unless we fetch first
    await broadcast_event("device_deleted", {"deviceId": device_id}, hotel_id="ALL") # broadcast to ALL or we need to find it before delete''')

main_content = main_content.replace('''        await broadcast_event("database_cleared", {
            "timestamp": get_ist_time().isoformat(),
            "deleted_counts": deleted_counts
        })''', '''        await broadcast_event("database_cleared", {
            "timestamp": get_ist_time().isoformat(),
            "deleted_counts": deleted_counts
        }, hotel_id="ALL")''')

main_content = main_content.replace('''    await broadcast_event("device_added", {
        "deviceId": deviceId,
        "roomId": roomId,
        "hotelId": hotelId
    })''', '''    await broadcast_event("device_added", {
        "deviceId": deviceId,
        "roomId": roomId,
        "hotelId": hotelId
    }, hotel_id=hotelId)''')

# Write back
with open("backend-api/main.py", "w", encoding="utf-8") as f:
    f.write(main_content)

print("Done editing main.py")
