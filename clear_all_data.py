#!/usr/bin/env python3
"""
Clear ALL data from MongoDB database via API endpoint
"""

import requests
import json

BACKEND_URL = "https://hotel-backend-zqc1.onrender.com"

print("=" * 70)
print("🗑️  DATABASE CLEANUP - DELETE ALL DATA")
print("=" * 70)
print()
print("⚠️  WARNING: This will DELETE ALL data from the backend!")
print("   - All devices")
print("   - All alerts")
print("   - All rooms")
print("   - All breach records")
print()

# Ask for confirmation
confirm = input("Type 'DELETE ALL DATA' to confirm: ")

if confirm != "DELETE ALL DATA":
    print("\n❌ Cancelled - no data was deleted")
    exit(0)

print("\n🔧 Sending delete request to backend...")

try:
    response = requests.post(
        f"{BACKEND_URL}/api/admin/clear-database",
        json={"confirm": "DELETE ALL DATA"},
        timeout=30
    )
    
    if response.status_code == 200:
        data = response.json()
        
        print("\n✅ DATABASE CLEARED SUCCESSFULLY!")
        print("=" * 70)
        print("\nDeleted:")
        for collection, count in data.get("deleted", {}).items():
            print(f"   {collection}: {count} documents")
        
        print("\n" + "=" * 70)
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
        
    else:
        print(f"\n❌ Error: {response.status_code}")
        print(f"Response: {response.text}")
        
except Exception as e:
    print(f"\n❌ Error: {e}")
    print("\nMake sure:")
    print("1. Backend is deployed and running")
    print("2. You have internet connection")
    print("3. The backend URL is correct")
