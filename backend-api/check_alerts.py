import asyncio
from motor.motor_asyncio import AsyncIOMotorClient
from config import settings
import json
from bson import json_util

async def check_alerts():
    """Check what alerts actually exist in the database"""
    client = AsyncIOMotorClient(settings.mongodb_url)
    db = client['hotel_security']
    alerts_collection = db['alerts']
    
    print("Checking alerts in database...")
    
    # Get a few recent alerts
    alerts = await alerts_collection.find().sort("ts", -1).limit(5).to_list(length=5)
    
    print(f"\nFound {len(alerts)} recent alerts:")
    print("=" * 80)
    
    for i, alert in enumerate(alerts, 1):
        print(f"\nAlert #{i}:")
        # Convert to JSON for pretty printing
        alert_json = json.loads(json_util.dumps(alert))
        print(json.dumps(alert_json, indent=2))
        print("-" * 80)
    
    # Count total alerts
    total = await alerts_collection.count_documents({})
    print(f"\nTotal alerts in database: {total}")
    
    # Check how many have deviceId
    with_device_id = await alerts_collection.count_documents({"deviceId": {"$exists": True}})
    print(f"Alerts with deviceId: {with_device_id}")
    
    # Check how many have device_id
    with_device_id_snake = await alerts_collection.count_documents({"device_id": {"$exists": True}})
    print(f"Alerts with device_id (snake_case): {with_device_id_snake}")
    
    # Check how many have neither
    without_either = await alerts_collection.count_documents({
        "deviceId": {"$exists": False},
        "device_id": {"$exists": False}
    })
    print(f"Alerts without either field: {without_either}")

if __name__ == "__main__":
    asyncio.run(check_alerts())
