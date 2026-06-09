from motor.motor_asyncio import AsyncIOMotorClient
from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
from enum import Enum
import os
from config import settings
print("MONGODB_URL USED BY FASTAPI:", settings.mongodb_url)

# MongoDB Atlas connection
MONGODB_URL = os.getenv(
    "MONGODB_URL",
    "mongodb+srv://hotel_security:65JdbW2Xazplojmg@cluster0.7q1xysy.mongodb.net/?retryWrites=true&w=majority"
)
DATABASE_NAME = os.getenv("DATABASE_NAME", "hotel_security")

# Optimized MongoDB client with connection pooling
client = AsyncIOMotorClient(
    MONGODB_URL,
    maxPoolSize=50,
    minPoolSize=10,
    maxIdleTimeMS=45000,
    serverSelectionTimeoutMS=5000,
    connectTimeoutMS=10000,
    socketTimeoutMS=45000,
)
db = client[DATABASE_NAME]

# Collections - cached references
hotels_collection = db.hotels
devices_collection = db.devices
rooms_collection = db.rooms
alerts_collection = db.alerts

class StatusEnum(str, Enum):
    ok = "ok"
    breach = "breach"
    offline = "offline"
    compromised = "compromised"

# Pydantic Models for validation
class Hotel(BaseModel):
    hotel_id: str = Field(..., alias="_id")
    name: str
    contact_email: Optional[str] = None
    slack_webhook: Optional[str] = None
    created_at: datetime = Field(default_factory=datetime.utcnow)
    
    class Config:
        populate_by_name = True
        json_encoders = {datetime: lambda v: v.isoformat()}

class Room(BaseModel):
    room_id: str = Field(..., alias="_id")
    hotel_id: Optional[str] = None
    name: Optional[str] = None
    bssid: Optional[str] = None
    ssid: Optional[str] = None
    min_rssi: Optional[int] = -70
    rssi_threshold: Optional[int] = -70
    
    class Config:
        populate_by_name = True

class Device(BaseModel):
    device_id: str = Field(..., alias="_id")
    room_id: Optional[str] = None
    hotel_id: Optional[str] = None
    status: StatusEnum = StatusEnum.ok
    battery: Optional[int] = None
    rssi: Optional[int] = None
    ip: Optional[str] = None
    last_seen: datetime = Field(default_factory=datetime.utcnow)
    
    class Config:
        populate_by_name = True
        json_encoders = {datetime: lambda v: v.isoformat()}

class Alert(BaseModel):
    id: Optional[str] = Field(default=None, alias="_id")
    type: str
    device_id: str
    room_id: Optional[str] = None
    hotel_id: Optional[str] = None
    payload: dict
    ts: datetime = Field(default_factory=datetime.utcnow)
    acknowledged: bool = False
    acknowledged_by: Optional[str] = None
    acknowledged_at: Optional[datetime] = None
    notes: Optional[str] = None
    
    class Config:
        populate_by_name = True
        json_encoders = {datetime: lambda v: v.isoformat()}

# Database initialization
async def init_db():
    """Create optimized indexes for better query performance"""
    # Device indexes - compound index for common queries
    await devices_collection.create_index([("hotel_id", 1), ("status", 1)])
    await devices_collection.create_index("last_seen")
    await devices_collection.create_index("room_id", sparse=True)
    
    # Alert indexes - compound index for recent alerts query
    await alerts_collection.create_index([("ts", -1), ("acknowledged", 1)])
    await alerts_collection.create_index("device_id")
    await alerts_collection.create_index("hotel_id", sparse=True)
    
    # Room indexes
    await rooms_collection.create_index("hotel_id", sparse=True)
    
    print("MongoDB indexes created successfully")
