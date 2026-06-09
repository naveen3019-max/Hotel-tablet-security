import asyncio
from motor.motor_asyncio import AsyncIOMotorClient
from config import settings

async def check_rooms():
    client = AsyncIOMotorClient(settings.mongodb_url)
    db = client['hotel_security']
    
    print("Checking rooms configuration...")
    rooms = await db.rooms.find({}).to_list(length=100)
    print(f"\n✅ Found {len(rooms)} rooms\n")
    
    for room in rooms:
        room_id = room.get("_id")
        bssid = room.get("bssid", "NOT SET")
        rssi_threshold = room.get("rssi_threshold", "NOT SET")
        print(f"Room {room_id}:")
        print(f"  BSSID: {bssid}")
        print(f"  RSSI Threshold: {rssi_threshold}")
        print()
    
    # Also check devices to see their configuration
    print("\nChecking device configuration...")
    devices = await db.devices.find({}).to_list(length=100)
    print(f"✅ Found {len(devices)} devices\n")
    
    for device in devices:
        device_id = device.get("_id")
        room_id = device.get("roomId", "NOT SET")
        status = device.get("status", "NOT SET")
        rssi = device.get("rssi", "NOT SET")
        bssid = device.get("bssid", "NOT SET")
        print(f"Device {device_id}:")
        print(f"  Room: {room_id}")
        print(f"  Status: {status}")
        print(f"  Current RSSI: {rssi}")
        print(f"  Current BSSID: {bssid}")
        print()
    
    await client.close()

if __name__ == "__main__":
    asyncio.run(check_rooms())
