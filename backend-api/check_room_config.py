import asyncio
from motor.motor_asyncio import AsyncIOMotorClient
from config import settings
import json
from bson import json_util

async def check_room_config():
    """Check if there are room configurations that might cause false breaches"""
    client = AsyncIOMotorClient(settings.mongodb_url)
    db = client['hotel_security']
    rooms_collection = db['rooms']
    devices_collection = db['devices']
    
    print("Checking room configurations...")
    print("=" * 80)
    
    # Get all rooms
    rooms = await rooms_collection.find().to_list(length=100)
    print(f"\nFound {len(rooms)} room configurations:")
    
    for room in rooms:
        print(f"\nRoom ID: {room.get('_id')}")
        print(f"  BSSID: {room.get('bssid', 'Not set')}")
        print(f"  RSSI Threshold: {room.get('rssi_threshold', 'Not set (default -80)')} dBm")
        print(f"  Hotel ID: {room.get('hotel_id', 'Not set')}")
    
    print("\n" + "=" * 80)
    print("Checking device configurations...")
    print("=" * 80)
    
    # Get all devices
    devices = await devices_collection.find().to_list(length=100)
    print(f"\nFound {len(devices)} devices:")
    
    for device in devices:
        print(f"\nDevice ID: {device.get('_id')}")
        print(f"  Room ID: {device.get('roomId', 'Not set')}")
        print(f"  Status: {device.get('status', 'unknown')}")
        print(f"  RSSI: {device.get('rssi', 'unknown')} dBm")
        print(f"  Last Seen: {device.get('last_seen', 'Never')}")

if __name__ == "__main__":
    asyncio.run(check_room_config())
