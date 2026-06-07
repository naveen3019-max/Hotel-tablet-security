#!/usr/bin/env python3
"""
Check recent backend activity and device heartbeats
"""

import asyncio
import aiohttp
from datetime import datetime

BACKEND_URL = "https://hotel-backend-zqc1.onrender.com"

async def check_backend_activity():
    """Check what devices are active and sending heartbeats"""
    
    print("🔍 BACKEND ACTIVITY CHECK")
    print("=" * 50)
    
    async with aiohttp.ClientSession() as session:
        
        # 1. Check all devices
        print("1️⃣ Checking registered devices...")
        try:
            async with session.get(f"{BACKEND_URL}/api/devices") as resp:
                if resp.status == 200:
                    devices = await resp.json()
                    print(f"   📱 Found {len(devices)} total devices:")
                    
                    for device in devices:
                        device_id = device.get('_id') or device.get('deviceId')
                        status = device.get('status', 'unknown')
                        last_seen = device.get('last_seen', 'never')
                        room_id = device.get('roomId', 'unknown')
                        print(f"      • {device_id}: {status} (Room {room_id}, Last: {last_seen})")
                        
                    # Check specifically for our test device
                    test_device = next((d for d in devices if d.get('_id') == 'TAB-D9413C44' or d.get('deviceId') == 'TAB-D9413C44'), None)
                    if test_device:
                        print(f"   ✅ Test device TAB-D9413C44 found: {test_device.get('status')}")
                    else:
                        print(f"   ❌ Test device TAB-D9413C44 NOT FOUND in devices list")
                else:
                    print(f"   ❌ Failed to get devices: {resp.status}")
        except Exception as e:
            print(f"   ❌ Error: {e}")
            
        print()
        
        # 2. Check recent alerts
        print("2️⃣ Checking recent alerts...")
        try:
            async with session.get(f"{BACKEND_URL}/api/alerts/recent?limit=10") as resp:
                if resp.status == 200:
                    alerts = await resp.json()
                    print(f"   📋 Found {len(alerts)} recent alerts:")
                    
                    for alert in alerts:
                        device_id = alert.get('deviceId')
                        alert_type = alert.get('type')
                        message = alert.get('message', '')[:50]
                        ts = alert.get('ts', '')
                        print(f"      • {device_id}: {alert_type} - {message} ({ts})")
                        
                    # Check for our test device alerts
                    test_alerts = [a for a in alerts if a.get('deviceId') == 'TAB-D9413C44']
                    print(f"   🎯 TAB-D9413C44 alerts: {len(test_alerts)}")
                    
                else:
                    print(f"   ❌ Failed to get alerts: {resp.status}")
        except Exception as e:
            print(f"   ❌ Error: {e}")
            
        print()
        
        # 3. Test authentication
        print("3️⃣ Testing device authentication...")
        try:
            # Try to read the JWT token
            with open("device_token.txt", "r") as f:
                token = f.read().strip()
                
            headers = {"Authorization": f"Bearer {token}"}
            heartbeat_data = {
                "deviceId": "TAB-D9413C44",
                "roomId": "101",
                "rssi": -65,
                "batteryLevel": 85,
                "isCharging": False,
                "wifiBssid": "AA:BB:CC:DD:EE:FF"
            }
            
            async with session.post(f"{BACKEND_URL}/api/heartbeat", 
                                  json=heartbeat_data, 
                                  headers=headers) as resp:
                if resp.status == 200:
                    print(f"   ✅ Test heartbeat successful - device should now be visible")
                else:
                    text = await resp.text()
                    print(f"   ❌ Test heartbeat failed: {resp.status}")
                    print(f"   Response: {text}")
        except FileNotFoundError:
            print(f"   ❌ device_token.txt not found - device needs registration")
        except Exception as e:
            print(f"   ❌ Authentication test error: {e}")
            
        print()
        
        # 4. Final status check
        print("4️⃣ Final device status check...")
        try:
            async with session.get(f"{BACKEND_URL}/api/devices") as resp:
                if resp.status == 200:
                    devices = await resp.json()
                    test_device = next((d for d in devices if d.get('_id') == 'TAB-D9413C44' or d.get('deviceId') == 'TAB-D9413C44'), None)
                    if test_device:
                        status = test_device.get('status')
                        last_seen = test_device.get('last_seen')
                        print(f"   ✅ TAB-D9413C44 now shows: {status} (Last: {last_seen})")
                    else:
                        print(f"   ❌ TAB-D9413C44 still not found after test heartbeat")
        except Exception as e:
            print(f"   ❌ Final check error: {e}")
            
        print()
        print("🔬 Analysis complete!")

if __name__ == "__main__":
    asyncio.run(check_backend_activity())