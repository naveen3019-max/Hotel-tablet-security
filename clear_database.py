#!/usr/bin/env python3
"""
Clear ALL data from MongoDB database - Fresh Start
Deletes all devices, alerts, rooms, and other collections
"""

import asyncio
import motor.motor_asyncio
from pymongo import MongoClient
import os

# MongoDB connection (update with your connection string)
MONGODB_URI = "mongodb+srv://username:password@cluster.mongodb.net/hotel_security?retryWrites=true&w=majority"

print("=" * 70)
print("🗑️  DATABASE CLEANUP - DELETE ALL DATA")
print("=" * 70)
print()
print("⚠️  WARNING: This will DELETE ALL data from MongoDB!")
print("   - All devices")
print("   - All alerts")
print("   - All rooms")
print("   - All breach records")
print("   - All configuration")
print()

# Ask for confirmation
confirm = input("Type 'DELETE ALL' to confirm: ")

if confirm != "DELETE ALL":
    print("\n❌ Cancelled - no data was deleted")
    exit(0)

print("\n🔧 Starting database cleanup...")

# Connect to MongoDB
try:
    client = MongoClient(MONGODB_URI, serverSelectionTimeoutMS=10000)
    db = client.get_database()
    
    print("\n✅ Connected to MongoDB")
    
    # Get all collection names
    collections = db.list_collection_names()
    print(f"\n📋 Found {len(collections)} collections:")
    for col in collections:
        count = db[col].count_documents({})
        print(f"   - {col}: {count} documents")
    
    print("\n🗑️  Deleting all data...")
    
    deleted = {}
    for collection_name in collections:
        result = db[collection_name].delete_many({})
        deleted[collection_name] = result.deleted_count
        print(f"   ✅ {collection_name}: Deleted {result.deleted_count} documents")
    
    print("\n" + "=" * 70)
    print("✅ DATABASE CLEANUP COMPLETE!")
    print("=" * 70)
    print()
    print("Summary:")
    total = sum(deleted.values())
    print(f"   Total documents deleted: {total}")
    print()
    print("📋 NEXT STEPS:")
    print("=" * 70)
    print("1. On tablet: Settings → Apps → Hotel Security → Clear Data")
    print("2. Open Hotel Security app")
    print("3. Register device:")
    print("   - Device ID: TAB-B2A8792B")
    print("   - Room Number: 5680")
    print("4. Wait 30 seconds for heartbeats to establish")
    print("5. Turn OFF WiFi to test breach detection")
    print("6. Dashboard should show breach with CORRECT IST time!")
    print("=" * 70)
    
except Exception as e:
    print(f"\n❌ Error: {e}")
    print("\nPossible issues:")
    print("1. MongoDB connection string is incorrect")
    print("2. Network connection problem")
    print("3. Database credentials invalid")
    print("\nYou can also delete data from MongoDB Atlas web interface:")
    print("1. Go to https://cloud.mongodb.com")
    print("2. Navigate to your cluster")
    print("3. Click 'Collections'")
    print("4. Delete each collection or drop the database")
