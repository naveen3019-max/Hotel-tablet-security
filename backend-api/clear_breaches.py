"""
Clear all breach alerts and reset device statuses
"""
import asyncio
import os
from motor.motor_asyncio import AsyncIOMotorClient

async def clear_breaches():
    """Clear all breaches and reset device statuses"""
    MONGODB_URL = os.getenv(
        "MONGODB_URL",
        "mongodb+srv://hotel_security:65JdbW2Xazplojmg@cluster0.7q1xysy.mongodb.net/?retryWrites=true&w=majority"
    )
    DATABASE_NAME = os.getenv("DATABASE_NAME", "hotel_security")
    
    client = AsyncIOMotorClient(MONGODB_URL)
    db = client[DATABASE_NAME]
    
    # Clear all breach alerts
    alerts_result = await db.alerts.delete_many({"type": "breach"})
    print(f"✅ Deleted {alerts_result.deleted_count} breach alerts")
    
    # Reset all device statuses to 'ok'
    devices_result = await db.devices.update_many(
        {"status": "breach"},
        {"$set": {"status": "ok"}}
    )
    print(f"✅ Reset {devices_result.modified_count} devices from breach to ok status")
    
    # Show current device statuses
    print("\n📱 Current device statuses:")
    async for device in db.devices.find({}, {"_id": 1, "roomId": 1, "status": 1}):
        print(f"  - {device['_id']} (Room: {device.get('roomId', 'N/A')}): {device.get('status', 'unknown')}")
    
    client.close()
    print("\n✅ Done! All breaches cleared.")

if __name__ == "__main__":
    asyncio.run(clear_breaches())
