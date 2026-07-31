import asyncio
from motor.motor_asyncio import AsyncIOMotorClient
import os

async def main():
    uri = os.getenv("MONGODB_URI", "mongodb+srv://naved3019:maxmaxmax@cluster0.nawm4.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0")
    client = AsyncIOMotorClient(uri)
    db = client["hotel_security"]
    devices_collection = db["devices"]
    
    device = await devices_collection.find_one({"_id": "VASHI4FOURPOINT"})
    print("Device by _id:", device)
    
    device2 = await devices_collection.find_one({"device_id": "VASHI4FOURPOINT"})
    print("Device by device_id:", device2)
    
    all_devices = await devices_collection.find().to_list(10)
    print("Sample devices:", [{"_id": d["_id"], "device_id": d.get("device_id")} for d in all_devices])

asyncio.run(main())
