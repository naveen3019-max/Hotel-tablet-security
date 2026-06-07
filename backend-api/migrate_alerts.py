import asyncio
from motor.motor_asyncio import AsyncIOMotorClient
from config import settings

async def migrate_alerts():
    """Migrate old alerts to have deviceId and roomId fields"""
    client = AsyncIOMotorClient(settings.mongodb_url)
    db = client['hotel_security']
    alerts_collection = db['alerts']
    
    print("Starting alert migration...")
    
    # Find all alerts that have device_id but not deviceId
    alerts_to_update = await alerts_collection.find({
        "device_id": {"$exists": True},
        "deviceId": {"$exists": False}
    }).to_list(length=10000)
    
    print(f"Found {len(alerts_to_update)} alerts to migrate")
    
    for alert in alerts_to_update:
        device_id = alert.get("device_id")
        room_id = alert.get("room_id")
        
        update_data = {}
        if device_id:
            update_data["deviceId"] = device_id
        if room_id:
            update_data["roomId"] = room_id
        
        if update_data:
            await alerts_collection.update_one(
                {"_id": alert["_id"]},
                {"$set": update_data}
            )
            print(f"Updated alert {alert['_id']}: deviceId={device_id}, roomId={room_id}")
    
    # Also check for alerts in payload structure
    payload_alerts = await alerts_collection.find({
        "payload.deviceId": {"$exists": True},
        "deviceId": {"$exists": False}
    }).to_list(length=10000)
    
    print(f"Found {len(payload_alerts)} alerts with payload structure to migrate")
    
    for alert in payload_alerts:
        payload = alert.get("payload", {})
        device_id = payload.get("deviceId")
        room_id = payload.get("roomId")
        
        update_data = {}
        if device_id:
            update_data["deviceId"] = device_id
        if room_id:
            update_data["roomId"] = room_id
        
        if update_data:
            await alerts_collection.update_one(
                {"_id": alert["_id"]},
                {"$set": update_data}
            )
            print(f"Updated alert {alert['_id']} from payload: deviceId={device_id}, roomId={room_id}")
    
    print("Migration complete!")
    await client.close()

if __name__ == "__main__":
    asyncio.run(migrate_alerts())
