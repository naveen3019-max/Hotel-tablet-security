#!/usr/bin/env python3
"""
Clear all device registrations from the database
"""
import asyncio
from motor.motor_asyncio import AsyncIOMotorClient
import os

# MongoDB connection
MONGODB_URL = os.getenv(
    "MONGODB_URL",
    "mongodb+srv://hotel_security:65JdbW2Xazplojmg@cluster0.7q1xysy.mongodb.net/?retryWrites=true&w=majority"
)
DATABASE_NAME = os.getenv("DATABASE_NAME", "hotel_security")

async def clear_all_data():
    """Clear all device registrations and related data from database"""
    
    client = AsyncIOMotorClient(MONGODB_URL)
    db = client[DATABASE_NAME]
    
    print("🗑️  Clearing all data from database...")
    print(f"📦 Database: {DATABASE_NAME}")
    print()
    
    # Clear devices collection
    result = await db.devices.delete_many({})
    print(f"✅ Deleted {result.deleted_count} devices")
    
    # Clear rooms collection
    result = await db.rooms.delete_many({})
    print(f"✅ Deleted {result.deleted_count} rooms")
    
    # Clear alerts collection
    result = await db.alerts.delete_many({})
    print(f"✅ Deleted {result.deleted_count} alerts")
    
    # Clear hotels collection
    result = await db.hotels.delete_many({})
    print(f"✅ Deleted {result.deleted_count} hotels")
    
    print()
    print("✨ Database cleared successfully!")
    
    client.close()

if __name__ == "__main__":
    asyncio.run(clear_all_data())
