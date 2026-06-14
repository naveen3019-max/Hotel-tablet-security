from fastapi import FastAPI, Depends, HTTPException, Body, WebSocket, WebSocketDisconnect  # ← NEW: Added WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
from pydantic import BaseModel
from datetime import datetime
from typing import Optional
import pytz  # type: ignore
from db import (
    db, devices_collection, alerts_collection, rooms_collection,
    StatusEnum, init_db
)
from config import settings
from notifications import NotificationService
from auth import AuthService, get_current_device
import asyncio
import logging
import httpx
import json
from sse_starlette.sse import EventSourceResponse
import redis.asyncio as redis
from bson import ObjectId
import sys
from websocket_manager import manager as ws_manager  # ← NEW: Import shared WebSocket connection manager

# Configure logging with forced flushing for Render
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    stream=sys.stdout,
    force=True
)
logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

# Force unbuffered output for Render
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(line_buffering=True)

async def keepalive_ping():
    """Pings self every 8 minutes to prevent Render connection idle timeout."""
    await asyncio.sleep(30)  # wait for startup to complete
    while True:
        try:
            async with httpx.AsyncClient() as client:
                r = await client.get(
                    "https://hotel-tablet-security.onrender.com/health",
                    timeout=10.0
                )
                logger.info(f"🏓 Keepalive ping: {r.status_code}")
        except Exception as e:
            logger.warning(f"⚠️ Keepalive ping failed: {e}")
        await asyncio.sleep(480)  # 8 minutes — under Render's ~10 min timeout

@asynccontextmanager
async def lifespan(app: FastAPI):
    global redis_client, monitoring_task, keepalive_task
    keepalive_task = None
    
    print("="*60, flush=True)
    print("🚀 BACKEND STARTUP", flush=True)
    print(f"   Environment: {settings.app_env}", flush=True)
    print(f"   Debug: {settings.debug}", flush=True)
    print(f"   MongoDB: {settings.mongodb_url}", flush=True)
    print("="*60, flush=True)
    
    # Initialize MongoDB indexes
    await init_db()
    print("✅ MongoDB indexes created successfully", flush=True)
    
    # Initialize Redis for SSE
    try:
        redis_instance = await redis.from_url(settings.redis_url)
        await redis_instance.ping()
        redis_client = redis_instance
        logger.info("Redis connected for SSE")
        print("✅ Redis connected for SSE", flush=True)
    except Exception as e:
        redis_client = None
        logger.warning(f"Redis connection failed: {e}. SSE will work without Redis.")
        print(f"⚠️ Redis connection failed: {e}", flush=True)
    
    # Start background heartbeat monitoring
    monitoring_task = asyncio.create_task(monitor_device_heartbeats())
    keepalive_task = asyncio.create_task(keepalive_ping())
    logger.info("🚀 Background heartbeat monitoring started")
    print("✅ Heartbeat monitoring task started", flush=True)
    print("="*60, flush=True)
    print("📡 READY TO ACCEPT CONNECTIONS", flush=True)
    print("="*60, flush=True)
    
    yield
    
    if redis_client:
        await redis_client.close()
    
    # Cancel monitoring task
    if monitoring_task:
        monitoring_task.cancel()
    if keepalive_task:
        keepalive_task.cancel()
        try:
            await monitoring_task
        except asyncio.CancelledError:
            pass
        logger.info("Heartbeat monitoring stopped")

app = FastAPI(
    title="Hotel Tablet Security API",
    version="0.6.0",
    debug=settings.debug,
    docs_url="/docs" if settings.debug else None,
    redoc_url="/redoc" if settings.debug else None,
    lifespan=lifespan
)

# Configure CORS
# ← FIXED: Root Cause 4: CORS allows WebSocket origins with wildcard
cors_origins = ["*"]
app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_origins,
    allow_credentials=False, # Must be False when origins is ["*"]
    allow_methods=["*"],
    allow_headers=["*"],
    max_age=3600
)

# Helper function for Indian Standard Time
def get_ist_time():
    """Get current time in Indian Standard Time (properly converted from UTC)"""
    # Get current UTC time (timezone aware)
    utc_now = datetime.now(pytz.utc)
    # Convert to IST (UTC+5:30)
    ist = pytz.timezone('Asia/Kolkata')
    return utc_now.astimezone(ist)

def get_utc_naive() -> datetime:
    """
    Returns current UTC time as naive datetime.
    Use this for ALL MongoDB last_seen writes.
    MongoDB stores naive datetimes as UTC.
    Using this ensures consistent comparison
    with the utc_threshold_naive in heartbeat
    monitoring.
    """
    return datetime.now(pytz.utc).replace(tzinfo=None)

def to_ist_isoformat(dt):
    """Convert datetime to IST timezone and return ISO format string"""
    if dt is None:
        return None
    
    ist = pytz.timezone('Asia/Kolkata')
    
    # DEBUG: Print what we receive from MongoDB
    print(f"🔍 to_ist_isoformat input: {dt} | tzinfo: {dt.tzinfo} | type: {type(dt)}", flush=True)
    
    # If datetime is timezone-naive, it's from MongoDB which stores UTC as naive
    if dt.tzinfo is None:
        # MongoDB stores UTC timestamps as naive datetimes - convert properly!
        utc = pytz.timezone('UTC')
        dt = utc.localize(dt)  # Label as UTC first
        dt = dt.astimezone(ist)  # Then convert UTC → IST (+5:30)
        print(f"✅ Converted naive UTC → IST: {dt.isoformat()}", flush=True)
    # If datetime has a different timezone, convert to IST
    elif dt.tzinfo != ist:
        original = dt.isoformat()
        dt = dt.astimezone(ist)
        print(f"✅ Converted {original} → {dt.isoformat()}", flush=True)
    else:
        print(f"✅ Already IST: {dt.isoformat()}", flush=True)
    
    return dt.isoformat()

# Redis connection for SSE
redis_client = None

# Background monitoring task
monitoring_task = None

async def monitor_device_heartbeats():
    """Background task to detect devices that stop sending heartbeats (WiFi OFF)"""
    logger.info("🔍 Starting heartbeat monitoring task for WiFi OFF detection")
    
    # Heartbeat timeout: 35 seconds
    # (devices send heartbeats every 10 seconds. 35s provides a safe buffer for network jitter)
    OFFLINE_THRESHOLD_SECONDS = 60
    
    while True:
        try:
            # Check every 5 seconds
            await asyncio.sleep(5)
            
            now = get_ist_time()
            
            import datetime as dt
            utc_now = datetime.now(pytz.utc)
            utc_threshold = utc_now - dt.timedelta(seconds=OFFLINE_THRESHOLD_SECONDS)
            # Remove timezone info to match MongoDB naive UTC
            utc_threshold_naive = utc_threshold.replace(tzinfo=None)
            
            # Query devices with last_seen older than threshold
            cursor = devices_collection.find({
                "last_seen": {"$exists": True},
                "last_seen": {"$lt": utc_threshold_naive}
            })
            
            async for device in cursor:
                device_id = device["_id"]
                current_status = device.get("status", StatusEnum.ok)
                last_seen = device.get("last_seen")
                
                # Only trigger breach for devices that were previously OK or offline
                if current_status in [StatusEnum.ok, StatusEnum.offline]:
                    # Ensure last_seen is timezone-aware for comparison
                    if last_seen and last_seen.tzinfo is None:
                        # MongoDB stores UTC as naive datetime
                        # MUST localize as UTC first then convert to IST
                        utc = pytz.timezone('UTC')
                        last_seen = utc.localize(last_seen)
                        ist = pytz.timezone('Asia/Kolkata')
                        last_seen = last_seen.astimezone(ist)
                    
                    seconds_since_heartbeat = (now - last_seen).total_seconds()
                    
                    logger.warning(f"🚨 HEARTBEAT TIMEOUT: Device {device_id} - Last seen {int(seconds_since_heartbeat)}s ago (threshold: {OFFLINE_THRESHOLD_SECONDS}s)")
                    print(f"🚨 HEARTBEAT TIMEOUT: {device_id} - WiFi likely OFF (no heartbeat for {int(seconds_since_heartbeat)}s)", flush=True)
                    
                    # LOG LINE REQUIRED BY PROMPT:
                    logger.error(f"OFFLINE CHECKER FIRED: Device={device_id}, Stored last_seen={last_seen.isoformat()}, Current time={now.isoformat()}, Gap={int(seconds_since_heartbeat)}s")
                    
                    # Mark device as breach
                    await devices_collection.update_one(
                        {"_id": device_id},
                        {"$set": {"status": StatusEnum.breach}}
                    )
                    
                    # Prevent breach spam for same device
                    # Only create breach if device was NOT
                    # already in breach status
                    if current_status not in [StatusEnum.breach, StatusEnum.compromised]:
                        # Create dynamic breach message
                        rssi_val = device.get("rssi", -127)
                        if rssi_val <= -120 or rssi_val is None:
                            breach_msg = f"WiFi disabled on device (RSSI: {rssi_val} dBm)"
                        else:
                            breach_msg = f"No heartbeat for {int(seconds_since_heartbeat)}s (RSSI last seen: {rssi_val} dBm)"

                        # Create breach alert
                        await alerts_collection.insert_one({
                            "deviceId": device_id,
                            "roomId": device.get("roomId", device.get("room_id", "unknown")),
                            "type": "breach",
                            "severity": "high",
                            "message": breach_msg,
                            "rssi": rssi_val,
                            "bssid": device.get("bssid", "unknown"),
                            "ts": now,
                            "acknowledged": False,
                            "source": "heartbeat_timeout"
                        })
                        
                        # Broadcast alert to dashboard
                        await broadcast_event("alert", {
                            "type": "breach",
                            "deviceId": device_id,
                            "roomId": device.get("roomId", device.get("room_id", "unknown")),
                            "rssi": rssi_val,
                            "bssid": device.get("bssid", "unknown"),
                            "source": "heartbeat_timeout",
                            "message": breach_msg
                        })
                        
                        # Send mobile notification
                        asyncio.create_task(
                            NotificationService.send_breach_alert(
                                device_id,
                                device.get("roomId", device.get("room_id", "unknown")),
                                device.get("rssi", -127)
                            )
                        )
                    
        except Exception as e:
            logger.error(f"Error in heartbeat monitoring: {e}")
            await asyncio.sleep(5)  # Continue monitoring even if error occurs

# Startup and shutdown handled by lifespan context manager above

# Request Models
class Breach(BaseModel):
    deviceId: str
    roomId: str
    rssi: int
    ts: Optional[datetime] = None

class Battery(BaseModel):
    deviceId: str
    level: int
    ts: Optional[datetime] = None

class Heartbeat(BaseModel):
    deviceId: str
    roomId: str
    wifiBssid: str
    rssi: int
    ip: Optional[str] = None
    battery: Optional[int] = None
    ts: Optional[datetime] = None

class Tamper(BaseModel):
    deviceId: str
    roomId: str
    threats: list[str]
    descriptions: list[str]
    ts: Optional[datetime] = None

class DeviceRegister(BaseModel):
    deviceId: str
    roomId: str
    hotelId: Optional[str] = None

class AlertAcknowledge(BaseModel):
    alertId: str
    acknowledgedBy: str
    notes: Optional[str] = None

# Root endpoint
@app.get("/")
def root():
    return {
        "ok": True,
        "service": "Hotel Tablet Security API",
        "version": "0.5.0",
        "environment": settings.app_env,
        "database": "MongoDB",
        "features": ["JWT Auth", "SSE", "Multi-tenancy", "Message Queue"],
        "server_time_ist": get_ist_time().isoformat(),
        "server_time_readable": get_ist_time().strftime('%Y-%m-%d %I:%M:%S %p IST')
    }

# Health check
@app.get("/health")
async def health_check():
    """Health check endpoint for monitoring"""
    try:
        # Check MongoDB connection
        await db.command("ping")
        return {
            "status": "healthy",
            "database": "connected",
            "redis": redis_client is not None,
            "timestamp": get_ist_time().isoformat()
        }
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        raise HTTPException(status_code=503, detail="Service unhealthy")

# Metrics
@app.get("/metrics")
async def metrics():
    """Basic metrics endpoint"""
    total_devices = await devices_collection.count_documents({})
    total_alerts = await alerts_collection.count_documents({})
    breached = await devices_collection.count_documents({"status": "breach"})
    
    return {
        "total_devices": total_devices,
        "total_alerts": total_alerts,
        "breached_devices": breached,
        "timestamp": get_ist_time().isoformat()
    }

# Authentication endpoints
@app.post("/api/auth/device-token")
async def create_device_token(payload: DeviceRegister):
    """Issue JWT token for device"""
    token = AuthService.create_device_token(
        device_id=payload.deviceId,
        room_id=payload.roomId,
        hotel_id=payload.hotelId or "default"
    )
    return {"token": token, "type": "Bearer", "expires_in": settings.jwt_expiration_minutes * 60}

@app.post("/api/auth/user-token")
async def create_user_token(username: str = Body(...), password: str = Body(...)):
    """Issue JWT token for user (staff/admin)"""
    if username == "admin" and password == "admin":
        token = AuthService.create_user_token(user_id=username, role="admin")
        return {"token": token, "type": "Bearer", "expires_in": settings.jwt_expiration_minutes * 60}
    raise HTTPException(status_code=401, detail="Invalid credentials")

# Device registration with JWT
@app.post("/api/devices/register")
async def register_device(payload: DeviceRegister):
    """Register device and return JWT token"""
    # Use print for immediate visibility in Render logs
    print("="*60, flush=True)
    print(f"📱 NEW DEVICE REGISTRATION", flush=True)
    print(f"   Device ID: {payload.deviceId}", flush=True)
    print(f"   Room ID: {payload.roomId}", flush=True)
    print(f"   Hotel ID: {payload.hotelId or 'default'}", flush=True)
    print("="*60, flush=True)
    
    logger.info("="*60)
    logger.info(f"📱 NEW DEVICE REGISTRATION")
    logger.info(f"   Device ID: {payload.deviceId}")
    logger.info(f"   Room ID: {payload.roomId}")
    logger.info(f"   Hotel ID: {payload.hotelId or 'default'}")
    logger.info("="*60)
    
    device_data = {
        "_id": payload.deviceId,
        "device_id": payload.deviceId,
        "room_id": payload.roomId,
        "hotel_id": payload.hotelId or "default",
        "status": StatusEnum.ok,
        "last_seen": get_utc_naive()
    }
    
    await devices_collection.update_one(
        {"_id": payload.deviceId},
        {"$set": device_data},
        upsert=True
    )
    
    # Issue JWT token
    token = AuthService.create_device_token(
        device_id=payload.deviceId,
        room_id=payload.roomId,
        hotel_id=payload.hotelId or "default"
    )
    
    print(f"✅ Device {payload.deviceId} registered successfully", flush=True)
    print(f"🔑 JWT Token issued (length: {len(token)} chars)", flush=True)
    print("="*60, flush=True)
    
    logger.info(f"✅ Device {payload.deviceId} registered successfully")
    logger.info(f"🔑 JWT Token issued (length: {len(token)} chars)")
    logger.info("="*60)
    
    return {"ok": True, "token": token}

# Breach alert with JWT
@app.post("/api/alert/breach")
async def alert_breach(b: Breach, device=Depends(get_current_device)):
    """Record breach alert (JWT protected)"""
    # ALWAYS use server IST time (ignore device timestamp to prevent timezone issues)
    b.ts = get_ist_time()
    logger.warning(f"BREACH ALERT: Device {b.deviceId}, Room {b.roomId}, RSSI {b.rssi}")
    
    # Update device status
    await devices_collection.update_one(
        {"_id": b.deviceId},
        {
            "$set": {
                "status": StatusEnum.breach,
                "rssi": b.rssi,
                "last_seen": get_utc_naive(),
                "roomId": b.roomId
            }
        },
        upsert=True
    )
    
    # Create alert with proper deviceId and roomId fields
    alert_data = {
        "deviceId": b.deviceId,
        "roomId": b.roomId,
        "type": "breach",
        "severity": "critical",
        "message": f"WiFi disconnected - Device breach detected",
        "rssi": b.rssi,
        "ts": b.ts,
        "acknowledged": False
    }
    
    result = await alerts_collection.insert_one(alert_data)
    logger.info(f"✅ STORED ALERT: deviceId={b.deviceId}, roomId={b.roomId}, _id={result.inserted_id}")
    
    # Broadcast via Redis for SSE
    await broadcast_event("device_update", {
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
    })
    
    # Queue notification task
    asyncio.create_task(
        NotificationService.send_breach_alert(b.deviceId, b.roomId, b.rssi)
    )
    
    return {"ok": True}

# Tamper alert with JWT
@app.post("/api/alert/tamper")
async def alert_tamper(t: Tamper, device=Depends(get_current_device)):
    """Record tamper alert (JWT protected)"""
    # ALWAYS use server IST time (ignore device timestamp to prevent timezone issues)
    t.ts = get_ist_time()
    logger.warning(f"TAMPER ALERT: Device {t.deviceId}, Threats {t.threats}")
    
    # Update device status
    await devices_collection.update_one(
        {"_id": t.deviceId},
        {
            "$set": {
                "status": StatusEnum.compromised,
                "last_seen": get_utc_naive()
            }
        },
        upsert=True
    )
    
    # Create alert
    alert_data = {
        "type": "tamper",
        "device_id": t.deviceId,
        "payload": {
            "deviceId": t.deviceId,
            "roomId": t.roomId,
            "threats": t.threats,
            "descriptions": t.descriptions,
            "ts": t.ts.isoformat()
        },
        "ts": t.ts,
        "acknowledged": False
    }
    
    await alerts_collection.insert_one(alert_data)
    
    # Broadcast via Redis
    await broadcast_event("device_update", {
        "deviceId": t.deviceId,
        "status": "compromised"
    })
    
    await broadcast_event("alert", {
        "type": "tamper",
        "deviceId": t.deviceId,
        "roomId": t.roomId,
        "threats": t.threats
    })
    
    return {"ok": True}

# Battery alert with JWT
@app.post("/api/alert/battery")
async def alert_battery(b: Battery, device=Depends(get_current_device)):
    """Record battery alert (JWT protected)"""
    # ALWAYS use server IST time (ignore device timestamp to prevent timezone issues)
    b.ts = get_ist_time()
    logger.warning(f"BATTERY ALERT: Device {b.deviceId}, Level {b.level}%")
    
    # Update device battery
    device_doc = await devices_collection.find_one({"_id": b.deviceId})
    
    await devices_collection.update_one(
        {"_id": b.deviceId},
        {
            "$set": {
                "status": StatusEnum.ok,
                "battery": b.level,
                "last_seen": get_utc_naive()
            }
        },
        upsert=True
    )
    
    # Create alert
    alert_data = {
        "type": "battery_low",
        "device_id": b.deviceId,
        "room_id": device_doc.get("room_id") if device_doc else None,
        "payload": {
            "deviceId": b.deviceId,
            "level": b.level,
            "ts": b.ts.isoformat()
        },
        "ts": b.ts,
        "acknowledged": False
    }
    
    await alerts_collection.insert_one(alert_data)
    
    # Broadcast via Redis
    await broadcast_event("alert", {
        "type": "battery_low",
        "deviceId": b.deviceId,
        "level": b.level
    })
    
    # Queue notification
    asyncio.create_task(
        NotificationService.send_battery_alert(b.deviceId, b.level)
    )
    
    return {"ok": True}

# Heartbeat with JWT
@app.post("/api/heartbeat")
async def heartbeat(h: Heartbeat, device=Depends(get_current_device)):
    """Record device heartbeat (JWT protected)"""
    # ALWAYS use server IST time (ignore device timestamp to prevent timezone issues)
    h.ts = get_ist_time()
    
    # Print for immediate visibility
    print(f"💓 HEARTBEAT: {h.deviceId} | Room: {h.roomId} | RSSI: {h.rssi} dBm | Battery: {h.battery}%", flush=True)
    
    logger.info(f"💓 HEARTBEAT: {h.deviceId} | Room: {h.roomId} | RSSI: {h.rssi} dBm | BSSID: {h.wifiBssid[:17]}")
    
    # Android 10+ always sends BSSID 02:00:00:00:00:00 due to MAC randomization
    # BSSID is NOT a reliable WiFi indicator on Android 10+
    # Use RSSI only: -127 means WiFi radio is OFF, anything above means connected
    WIFI_OFF_RSSI = -120

    if h.rssi <= WIFI_OFF_RSSI:
        
        logger.warning(
            f"🚨 WiFi OFF heartbeat: "
            f"Device={h.deviceId} "
            f"RSSI={h.rssi} "
            f"BSSID={h.wifiBssid}"
        )
        
        # Get current device to check if we already marked breach
        current_device = await devices_collection.find_one({"_id": h.deviceId})
        existing_status = current_device.get("status", StatusEnum.ok) if current_device else StatusEnum.ok
        
        # Update device status to breach
        await devices_collection.update_one(
            {"_id": h.deviceId},
            {"$set": {
                "status": StatusEnum.breach,
                "rssi": h.rssi,
                "roomId": h.roomId
            }},
            upsert=True
        )
        
        # Only create alert if not already breached
        if existing_status != StatusEnum.breach:
            # Create breach alert
            await alerts_collection.insert_one({
                "deviceId": h.deviceId,
                "roomId": h.roomId,
                "type": "breach",
                "severity": "critical",
                "message": f"WiFi disabled on device (RSSI: {h.rssi} dBm)",
                "rssi": h.rssi,
                "ts": get_ist_time(),
                "acknowledged": False
            })
        
        # Broadcast to dashboard
        print(f"🚨 WiFi OFF breach: {h.deviceId}",
              flush=True)
        await broadcast_event("alert", {
            "type": "breach",
            "deviceId": h.deviceId,
            "roomId": h.roomId,
            "rssi": h.rssi,
            "message": "WiFi disabled on device"
        })
        
        # Do NOT update last_seen
        # Return breach status
        return {"ok": True, "status": "breach"}

    # Get current device status
    current_device = await devices_collection.find_one({"_id": h.deviceId})
    existing_status = current_device.get("status", StatusEnum.ok) if current_device else StatusEnum.ok
    
    # NOTE: WiFi OFF detection (missing heartbeats) is handled by monitor_device_heartbeats() background task
    # This endpoint focuses on proactive breach detection (BSSID mismatch, weak RSSI) during active heartbeats
    
    # Proactive breach detection: Check BSSID/RSSI against room baseline
    new_status = existing_status
    
    # ← CRITICAL FIX: Clear breach immediately
    # when device sends good heartbeat
    # This handles devices with NO room config
    # that get stuck in breach forever
    if existing_status == StatusEnum.breach and \
       h.rssi > -120 and \
       h.wifiBssid != "02:00:00:00:00:00":
        
        logger.info(
            f"✅ AUTO BREACH CLEAR: {h.deviceId} "
            f"sending good heartbeat "
            f"RSSI:{h.rssi} dBm"
        )
        
        new_status = StatusEnum.ok
        
        # Broadcast recovery to dashboard instantly
        await broadcast_event("device_recovered", {
            "deviceId": h.deviceId,
            "roomId": h.roomId,
            "rssi": h.rssi,
            "battery": h.battery,
            "message": "Device WiFi restored - "
                       "good heartbeat received"
        })
    
    # Get room configuration to check against
    room = await rooms_collection.find_one({"_id": h.roomId})
    if room:
        target_bssid = room.get("bssid")
        min_rssi = room.get("rssi_threshold", -80)
        
        # Check if WiFi signal is good (BSSID matches AND RSSI is strong)
        bssid_matches = not target_bssid or h.wifiBssid.lower() == target_bssid.lower()
        rssi_good = h.rssi >= min_rssi
        wifi_is_good = bssid_matches and rssi_good
        
        # If device was in breach but WiFi is now good, clear the breach
        if existing_status == StatusEnum.breach and wifi_is_good:
            logger.info(f"✅ WiFi RECOVERED: Device {h.deviceId} - BSSID OK, RSSI {h.rssi} >= {min_rssi}")
            new_status = StatusEnum.ok
            
            # Broadcast recovery event
            await broadcast_event("device_recovered", {
                "deviceId": h.deviceId,
                "roomId": h.roomId,
                "rssi": h.rssi,
                "message": "Device WiFi connection restored"
            })
        
        # If device was compromised but WiFi is good, clear the compromised status
        # (since tamper detection is now disabled, we reset on good connectivity)
        elif existing_status == StatusEnum.compromised and wifi_is_good:
            logger.info(f"✅ STATUS RESET: Device {h.deviceId} - Clearing compromised status on good WiFi")
            new_status = StatusEnum.ok
        
        # ONLY check for new breaches if device is OK or offline AND room has configuration
        elif existing_status in [StatusEnum.ok, StatusEnum.offline]:
            # 1. BSSID Mismatch check (case-insensitive)
            if target_bssid and h.wifiBssid.lower() != target_bssid.lower():
                logger.warning(f"🚨 Proactive Breach Detected (BSSID): Device {h.deviceId} reported {h.wifiBssid}, expected {target_bssid}")
                new_status = StatusEnum.breach
                
            # 2. RSSI Breach check (if BSSID matches or target is not set)
            elif h.rssi < min_rssi:
                logger.warning(f"🚨 Proactive Breach Detected (RSSI): Device {h.deviceId} reported {h.rssi} dBm, threshold {min_rssi} dBm")
                new_status = StatusEnum.breach

            if new_status == StatusEnum.breach:
                # Create a breach alert record
                await alerts_collection.insert_one({
                    "deviceId": h.deviceId,
                    "roomId": h.roomId,
                    "type": "breach",
                    "severity": "high",
                    "message": f"Security boundary breach detected via proactive heartbeat monitoring (BSSID: {h.wifiBssid}, RSSI: {h.rssi})",
                    "rssi": h.rssi,
                    "bssid": h.wifiBssid,
                    "ts": h.ts,
                    "acknowledged": False
                })
                
                # Broadcast alert
                await broadcast_event("alert", {
                    "type": "breach",
                    "deviceId": h.deviceId,
                    "roomId": h.roomId,
                    "rssi": h.rssi,
                    "bssid": h.wifiBssid,
                    "source": "proactive_heartbeat"
                })
                
                # Queue mobile notification
                asyncio.create_task(
                    NotificationService.send_breach_alert(h.deviceId, h.roomId, h.rssi)
                )
            else:
                # If not a breach and was offline, mark as OK
                new_status = StatusEnum.ok
    else:
        # No room configuration exists - clear breach when heartbeat received
        if existing_status == StatusEnum.breach:
            logger.info(f"✅ WiFi RECOVERED (No room config): Device {h.deviceId} - Clearing breach on heartbeat, RSSI {h.rssi}")
            new_status = StatusEnum.ok
            
            # Broadcast recovery event
            await broadcast_event("device_recovered", {
                "deviceId": h.deviceId,
                "roomId": h.roomId,
                "rssi": h.rssi,
                "message": "Device WiFi connection restored"
            })

    update_data = {
        "roomId": h.roomId,  # Use camelCase to match monitor_device_heartbeats
        "room_id": h.roomId,  # Keep snake_case for backward compatibility
        "status": new_status,
        "rssi": h.rssi,
        "bssid": h.wifiBssid,
        "ip": h.ip,
        "last_seen": get_utc_naive()
    }
    
    if h.battery is not None:
        update_data["battery"] = h.battery
    
    try:
        result = await devices_collection.update_one(
            {"_id": h.deviceId},
            {"$set": update_data},
            upsert=True
        )
        logger.info(f"Heartbeat DB write: matched={result.matched_count}, modified={result.modified_count}, upserted={result.upserted_id}")
    except Exception as e:
        logger.error(f"❌ MongoDB write failed in heartbeat for {h.deviceId}: {e}")
    
    # Broadcast device update
    await broadcast_event("device_update", {
        "deviceId": h.deviceId,
        "status": new_status,
        "rssi": h.rssi,
        "battery": h.battery
    })
    
    return {"ok": True, "status": new_status}

# Alert acknowledgment
@app.post("/api/alerts/acknowledge")
async def acknowledge_alert(payload: AlertAcknowledge):
    """Acknowledge an alert"""
    result = await alerts_collection.update_one(
        {"_id": ObjectId(payload.alertId)},
        {
            "$set": {
                "acknowledged": True,
                "acknowledged_by": payload.acknowledgedBy,
                "acknowledged_at": get_ist_time(),
                "notes": payload.notes
            }
        }
    )
    
    if result.modified_count == 0:
        raise HTTPException(status_code=404, detail="Alert not found")
    
    # Broadcast acknowledgment
    await broadcast_event("alert_acknowledged", {
        "alertId": payload.alertId,
        "acknowledgedBy": payload.acknowledgedBy
    })
    
    return {"ok": True}

# Get recent alerts
@app.get("/api/alerts/recent")
async def recent_alerts(limit: int = 100, hotel_id: Optional[str] = None):
    """Get recent alerts with optional hotel filter"""
    query = {}
    if hotel_id:
        query["hotel_id"] = hotel_id
    
    # Optimized query with projection to reduce data transfer
    cursor = alerts_collection.find(
        query,
        projection={"payload": 1, "type": 1, "deviceId": 1, "roomId": 1, "device_id": 1, "room_id": 1, "severity": 1, "message": 1, "ts": 1, "acknowledged": 1, "acknowledged_by": 1, "acknowledged_at": 1}
    ).sort("ts", -1).limit(limit)
    alerts = await cursor.to_list(length=limit)
    
    logger.info(f"📋 FETCHING {len(alerts)} ALERTS for dashboard")
    
    # Convert ObjectId to string and normalize field names
    for alert in alerts:
        alert["id"] = str(alert.pop("_id"))
        # Support both camelCase and snake_case for backward compatibility
        alert["deviceId"] = alert.get("deviceId") or alert.get("device_id", "Unknown")
        alert["roomId"] = alert.get("roomId") or alert.get("room_id", "Unknown")
        logger.info(f"  Alert {alert['id'][:8]}: deviceId={alert['deviceId']}, roomId={alert['roomId']}, type={alert.get('type')}")
        if alert.get("ts"):
            alert["ts"] = to_ist_isoformat(alert["ts"])
        if alert.get("acknowledged_at"):
            alert["acknowledged_at"] = to_ist_isoformat(alert["acknowledged_at"])
    
    return alerts

# List devices
@app.get("/api/devices")
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
    } for d in devices]

# Delete device (Owner only)
@app.delete("/api/devices/{device_id}")
async def delete_device(device_id: str):
    """Delete a device - Owner dashboard feature"""
    # Delete device
    result = await devices_collection.delete_one({"_id": device_id})
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Device not found")
    
    # Delete associated alerts asynchronously
    asyncio.create_task(alerts_collection.delete_many({"device_id": device_id}))
    
    logger.info(f"Device {device_id} deleted")
    
    # Broadcast device removal
    await broadcast_event("device_deleted", {"deviceId": device_id})
    
    return {"ok": True, "message": f"Device {device_id} deleted successfully"}

# Clear all database data (DANGER ZONE - Fresh start)
@app.post("/api/admin/clear-database")
async def clear_database(confirm: str = Body(..., embed=True)):
    """
    ⚠️ DANGER ZONE: Clear ALL data from database
    Requires confirmation: {"confirm": "DELETE ALL DATA"}
    """
    if confirm != "DELETE ALL DATA":
        raise HTTPException(
            status_code=400, 
            detail="Confirmation required. Send: {\"confirm\": \"DELETE ALL DATA\"}"
        )
    
    logger.warning("🗑️ DATABASE CLEAR REQUESTED - Deleting all data!")
    
    deleted_counts = {}
    
    try:
        # Delete all devices
        result = await devices_collection.delete_many({})
        deleted_counts["devices"] = result.deleted_count
        
        # Delete all alerts
        result = await alerts_collection.delete_many({})
        deleted_counts["alerts"] = result.deleted_count
        
        # Delete all rooms
        result = await rooms_collection.delete_many({})
        deleted_counts["rooms"] = result.deleted_count
        
        logger.warning(f"✅ DATABASE CLEARED: {deleted_counts}")
        
        # Broadcast database clear event
        await broadcast_event("database_cleared", {
            "timestamp": get_ist_time().isoformat(),
            "deleted_counts": deleted_counts
        })
        
        return {
            "ok": True,
            "message": "Database cleared successfully - fresh start!",
            "deleted": deleted_counts,
            "next_steps": [
                "1. Clear app data on tablets",
                "2. Re-register all devices",
                "3. Configure room baselines"
            ]
        }
        
    except Exception as e:
        logger.error(f"Error clearing database: {e}")
        raise HTTPException(status_code=500, detail=f"Database clear failed: {str(e)}")

# Quick add device (Owner dashboard)
@app.post("/api/devices/quick-add")
async def quick_add_device(
    deviceId: str = Body(...),
    roomId: str = Body(...),
    hotelId: str = Body(default="default")
):
    """Quick add device from owner dashboard"""
    # Check if device already exists
    existing = await devices_collection.find_one({"_id": deviceId})
    if existing:
        raise HTTPException(status_code=400, detail="Device ID already exists")
    
    # Create device
    device_data = {
        "_id": deviceId,
        "device_id": deviceId,
        "room_id": roomId,
        "hotel_id": hotelId,
        "status": StatusEnum.ok,
        "last_seen": get_utc_naive(),
        "battery": None,
        "rssi": None
    }
    
    await devices_collection.insert_one(device_data)
    
    # Generate JWT token for the device
    token = AuthService.create_device_token(
        device_id=deviceId,
        room_id=roomId,
        hotel_id=hotelId
    )
    
    logger.info(f"Device {deviceId} added by owner for room {roomId}")
    
    # Broadcast new device
    await broadcast_event("device_added", {
        "deviceId": deviceId,
        "roomId": roomId,
        "hotelId": hotelId
    })
    
    return {
        "ok": True,
        "message": f"Device {deviceId} added successfully",
        "token": token,
        "device": {
            "deviceId": deviceId,
            "roomId": roomId,
            "hotelId": hotelId,
            "status": "ok"
        }
    }

# Room configuration
@app.post("/api/rooms/upsert")
async def upsert_room(room: dict = Body(...), device=Depends(get_current_device)):
    """Upsert room configuration (JWT protected)"""
    room_data = {
        "_id": room["roomId"],
        "room_id": room["roomId"],
        "name": room.get("name"),
        "ssid": room.get("ssid"),
        "bssid": room.get("bssid"),
        "rssi_threshold": room.get("rssiThreshold", room.get("minRssi", -70))
    }
    
    await rooms_collection.update_one(
        {"_id": room["roomId"]},
        {"$set": room_data},
        upsert=True
    )
    
    return {"ok": True}

# Get device config
@app.get("/api/config/{device_id}")
async def get_config(device_id: str, device=Depends(get_current_device)):
    """Get configuration for a device (JWT protected)"""
    device_doc = await devices_collection.find_one({"_id": device_id})
    
    if device_doc and device_doc.get("room_id"):
        room_doc = await rooms_collection.find_one({"_id": device_doc["room_id"]})
        if room_doc:
            return {
                "room": {
                    "bssid": room_doc.get("bssid"),
                    "ssid": room_doc.get("ssid"),
                    "minRssi": room_doc.get("rssi_threshold", -70)
                },
                "pin": "832504",
                "thresholds": {
                    "batteryLow": 20,
                    "breachGraceSec": 10
                }
            }
    
    # Default fallback
    return {
        "room": {
            "bssid": "AA:BB:CC:DD:EE:FF",
            "minRssi": -70
        },
        "pin": "832504",
        "thresholds": {
            "batteryLow": 20,
            "breachGraceSec": 10
        }
    }

# In-memory event queue for SSE clients (fallback when Redis is not available)
sse_clients: list = []

# Server-Sent Events endpoint
@app.get("/api/events")
async def sse_endpoint():
    """Server-Sent Events endpoint for real-time updates"""
    async def event_generator():
        # Create a queue for this client
        client_queue = asyncio.Queue()
        sse_clients.append(client_queue)
        
        try:
            # Send initial connection message
            yield {
                "event": "connected",
                "data": json.dumps({"message": "Connected to SSE stream"})
            }
            
            if redis_client:
                # Subscribe to Redis pub/sub for multi-worker support
                pubsub = redis_client.pubsub()
                await pubsub.subscribe("sse_events")
                
                try:
                    async for message in pubsub.listen():
                        if message["type"] == "message":
                            data = message["data"].decode("utf-8") if isinstance(message["data"], bytes) else message["data"]
                            yield {
                                "event": "message",
                                "data": data
                            }
                except asyncio.CancelledError:
                    await pubsub.unsubscribe("sse_events")
                    await pubsub.close()
                    raise
            else:
                # Fallback: Use in-memory queue for this client
                logger.info(f"SSE client connected via in-memory queue (total clients: {len(sse_clients)})")
                while True:
                    try:
                        # Wait for events with timeout for periodic pings
                        message = await asyncio.wait_for(client_queue.get(), timeout=30.0)
                        yield {
                            "event": "message",
                            "data": message
                        }
                    except asyncio.TimeoutError:
                        # Send ping to keep connection alive
                        yield {
                            "event": "ping",
                            "data": json.dumps({"timestamp": get_ist_time().isoformat()})
                        }
        finally:
            # Clean up this client's queue when connection closes
            if client_queue in sse_clients:
                sse_clients.remove(client_queue)
                logger.info(f"SSE client disconnected (remaining clients: {len(sse_clients)})")
    
    return EventSourceResponse(event_generator())


# ← NEW: WebSocket endpoint — dashboard connects here for instant push updates
@app.websocket("/ws/dashboard")
async def websocket_dashboard(websocket: WebSocket):
    """Real-time WebSocket endpoint for the dashboard.
    Each dashboard tab that opens creates one persistent connection here.
    The server pushes breach/heartbeat/offline events to all connections instantly.
    """
    await ws_manager.connect(websocket)  # ← NEW: Register this client
    try:
        # ← NEW: Send an immediate welcome message so the frontend knows it's live
        await websocket.send_text(json.dumps({
            "type": "connected",
            "message": "WebSocket connected to Hotel Security Dashboard",
            "timestamp": get_ist_time().isoformat()
        }))

        # ← NEW: Keep the connection alive by listening for any client messages
        # (We don't expect any, but we must await to avoid closing immediately)
        while True:
            data = await websocket.receive_text()
            # ← FIXED: The /ws/dashboard endpoint must handle ping and echo back pong to keep connection alive
            if data == "ping":
                await websocket.send_text(json.dumps({"type": "pong"}))

    except WebSocketDisconnect:  # ← NEW: Client closed their browser tab cleanly
        ws_manager.disconnect(websocket)
    except Exception as e:  # ← NEW: Network drop, Render timeout, etc.
        logger.warning(f"WebSocket connection error: {e}")
        ws_manager.disconnect(websocket)

# Helper function to broadcast events via Redis, in-memory SSE queue, AND WebSocket
async def broadcast_event(event_type: str, data: dict):
    """Broadcast event to all SSE clients via Redis or in-memory queue, and to all WebSocket clients."""
    message = {
        "type": event_type,
        "data": data,
        "timestamp": get_ist_time().isoformat()
    }
    message_str = json.dumps(message)

    # ← NEW: Push to ALL connected WebSocket dashboard clients instantly
    await ws_manager.broadcast(message)

    if redis_client:
        try:
            # Use Redis for multi-worker SSE support
            await redis_client.publish("sse_events", message_str)
        except Exception as e:
            logger.warning(f"Could not broadcast via Redis (likely down): {e}")
    else:
        # Fallback: Use in-memory queue for all connected SSE clients
        if sse_clients:
            logger.info(f"📢 Broadcasting {event_type} to {len(sse_clients)} SSE clients via in-memory queue")
            for client_queue in sse_clients:
                try:
                    client_queue.put_nowait(message_str)
                except asyncio.QueueFull:
                    logger.warning("SSE client queue full, dropping message")
        else:
            logger.debug(f"No SSE clients connected, skip SSE broadcast: {event_type}")
