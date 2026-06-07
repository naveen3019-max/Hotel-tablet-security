#!/usr/bin/env python3
"""
Register device TAB-B2A8792B with backend  
"""

import requests
import json
from datetime import datetime

BACKEND_URL = "https://hotel-backend-zqc1.onrender.com"
DEVICE_ID = "TAB-B2A8792B"
ROOM_ID = "5680"

def register_device():
    """Register device TAB-B2A8792B room 5680"""
    
    print("🔧 REGISTERING DEVICE TAB-B2A8792B")
    print("=" * 60)
    print(f"Backend: {BACKEND_URL}")
    print(f"Device ID: {DEVICE_ID}")
    print(f"Room ID: {ROOM_ID}")
    print("=" * 60)
    
    registration_data = {
        "deviceId": DEVICE_ID,
        "roomId": ROOM_ID,
        "location": f"Room {ROOM_ID}",
        "ssid": "HotelWiFi",
        "bssid": "22:a7:c2:ef:e4:91",  # From your logs
        "minRssi": -70
    }
    
    print("\n1️⃣ Registering device...")
    print(f"   Data: {json.dumps(registration_data, indent=4)}")
    
    try:
        response = requests.post(
            f"{BACKEND_URL}/api/devices/register",
            json=registration_data,
            timeout=15
        )
        
        if response.status_code == 200:
            data = response.json()
            token = data.get("token")
            device_id = data.get("deviceId")
            
            print(f"\n✅ SUCCESS! Device registered")
            print(f"   📱 Device ID: {device_id}")
            print(f"   🏠 Room: {ROOM_ID}")
            print(f"   🔑 JWT Token: {token[:60]}...")
            
            # Save token
            token_file = f"device_token_{DEVICE_ID}.txt"
            with open(token_file, "w") as f:
                f.write(token)
            print(f"   💾 Token saved to {token_file}")
            
            return token
            
        else:
            print(f"\n❌ Registration failed: {response.status_code}")
            print(f"   Response: {response.text}")
            return None
            
    except Exception as e:
        print(f"\n❌ Error during registration: {e}")
        return None

def verify_registration():
    """Check if device appears in backend"""
    print("\n2️⃣ Verifying registration...")
    
    try:
        response = requests.get(f"{BACKEND_URL}/api/devices", timeout=10)
        
        if response.status_code == 200:
            devices = response.json()
            print(f"   Found {len(devices)} devices in backend")
            
            for device in devices:
                if device.get("device_id") == DEVICE_ID:
                    print(f"\n   ✅ Device {DEVICE_ID} found!")
                    print(f"      Room: {device.get('room_id')}")
                    print(f"      Status: {device.get('status')}")
                    print(f"      Last Seen: {device.get('last_seen')}")
                    return True
            
            print(f"\n   ⚠️ Device {DEVICE_ID} not found in device list")
            print(f"   Registered devices: {[d.get('device_id') for d in devices]}")
            return False
        else:
            print(f"   ❌ Failed to get devices: {response.status_code}")
            return False
            
    except Exception as e:
        print(f"   ❌ Error verifying: {e}")
        return False

def main():
    # Register device
    token = register_device()
    
    if token:
        # Verify registration
        verify_registration()
        
        print("\n" + "=" * 60)
        print("📋 NEXT STEPS:")
        print("=" * 60)
        print("1. The device token has been saved to file")
        print("2. You need to UPDATE THE ANDROID APP with this new token")
        print("3. The token is stored in SharedPreferences on the tablet")
        print("4. Options to update token:")
        print("   a) Uninstall and reinstall the app (easiest)")
        print("   b) Clear app data in Android settings")
        print("   c) Manually update via adb shell")
        print("\n💡 Simply turning WiFi OFF won't trigger breach until device")
        print("   is properly registered and sending heartbeats!")
        print("=" * 60)
    else:
        print("\n❌ Registration failed - check backend logs")

if __name__ == "__main__":
    main()
