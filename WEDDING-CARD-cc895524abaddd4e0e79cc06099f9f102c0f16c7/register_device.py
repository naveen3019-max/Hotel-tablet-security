#!/usr/bin/env python3
"""
Register device with backend for WiFi breach testing
"""

import asyncio
import aiohttp
import json
from datetime import datetime

BACKEND_URL = "https://hotel-backend-zqc1.onrender.com"
DEVICE_ID = "TAB-D9413C44"
ROOM_ID = "101"  # Test room

async def register_device():
    """Register the test device with the backend"""
    
    print("🔧 DEVICE REGISTRATION")
    print("=" * 50)
    
    registration_data = {
        "deviceId": DEVICE_ID,
        "roomId": ROOM_ID,
        "location": "Test Room 101",
        "ssid": "TestWiFi", 
        "bssid": "AA:BB:CC:DD:EE:FF",
        "minRssi": -70
    }
    
    async with aiohttp.ClientSession() as session:
        
        # 1. Register device
        print("1️⃣ Registering device...")
        try:
            async with session.post(f"{BACKEND_URL}/api/devices/register", json=registration_data) as resp:
                if resp.status == 200:
                    data = await resp.json()
                    token = data.get("token")
                    device_id = data.get("deviceId")
                    print(f"   ✅ Device registered successfully!")
                    print(f"   📱 Device ID: {device_id}")
                    print(f"   🔑 JWT Token: {token[:50]}...")
                    
                    # Save token for future use
                    with open("device_token.txt", "w") as f:
                        f.write(token)
                    print(f"   💾 Token saved to device_token.txt")
                    
                else:
                    text = await resp.text()
                    print(f"   ❌ Registration failed: {resp.status}")
                    print(f"   Response: {text}")
                    return
        except Exception as e:
            print(f"   ❌ Registration error: {e}")
            return
            
        print()
        
        # 2. Test device status
        print("2️⃣ Checking device status...")
        try:
            async with session.get(f"{BACKEND_URL}/api/device/{DEVICE_ID}") as resp:
                if resp.status == 200:
                    data = await resp.json()
                    status = data.get("status", "unknown")
                    print(f"   📱 Device status: {status}")
                else:
                    print(f"   ❌ Status check failed: {resp.status}")
        except Exception as e:
            print(f"   ❌ Status error: {e}")
            
        print()
        
        # 3. Test heartbeat with token
        print("3️⃣ Testing authenticated heartbeat...")
        try:
            headers = {"Authorization": f"Bearer {token}"}
            heartbeat_data = {
                "deviceId": DEVICE_ID,
                "roomId": ROOM_ID,
                "rssi": -65,
                "batteryLevel": 85,
                "isCharging": False
            }
            
            async with session.post(f"{BACKEND_URL}/api/heartbeat", 
                                  json=heartbeat_data, 
                                  headers=headers) as resp:
                if resp.status == 200:
                    print(f"   ✅ Heartbeat successful")
                else:
                    text = await resp.text()
                    print(f"   ❌ Heartbeat failed: {resp.status}")
                    print(f"   Response: {text}")
        except Exception as e:
            print(f"   ❌ Heartbeat error: {e}")
            
        print()
        print("🎉 Device registration completed!")
        print()
        print("📝 ANDROID APP CONFIGURATION:")
        print("   Add this to your Android app's SharedPreferences:")
        print(f"   - Device ID: {DEVICE_ID}")
        print(f"   - Room ID: {ROOM_ID}")
        print(f"   - JWT Token: {token}")
        print(f"   - Backend URL: {BACKEND_URL}")
        print()
        print("📱 Now test WiFi breach detection by turning OFF WiFi on the tablet!")

if __name__ == "__main__":
    asyncio.run(register_device())