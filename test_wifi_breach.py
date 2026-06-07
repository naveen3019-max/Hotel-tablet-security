#!/usr/bin/env python3
"""
Test WiFi Breach Detection - Verify breach alerts are working properly
"""

import asyncio
import time
import aiohttp
from datetime import datetime

BACKEND_URL = "http://localhost:8000"  # Change to your deployed backend URL
DEVICE_ID = "TAB-D9413C44"  # Your test device

async def test_wifi_monitoring():
    """Test if WiFi monitoring and breach detection is working"""
    
    print("🔬 TESTING WiFi BREACH DETECTION")
    print("=" * 50)
    
    async with aiohttp.ClientSession() as session:
        
        # 1. Check device status
        print("1️⃣ Checking current device status...")
        
        # First test backend health
        try:
            async with session.get(f"{BACKEND_URL}/health") as resp:
                if resp.status == 200:
                    print(f"   ✅ Backend health check passed")
                else:
                    print(f"   ❌ Backend health check failed: {resp.status}")
                    return
        except Exception as e:
            print(f"   ❌ Cannot connect to backend: {e}")
            return
            
        try:
            async with session.get(f"{BACKEND_URL}/api/device/{DEVICE_ID}") as resp:
                if resp.status == 200:
                    data = await resp.json()
                    current_status = data.get("status", "unknown")
                    last_seen = data.get("last_seen", "never")
                    print(f"   📱 Device {DEVICE_ID}: {current_status}")
                    print(f"   🕐 Last seen: {last_seen}")
                elif resp.status == 404:
                    print(f"   📱 Device {DEVICE_ID}: not found - needs registration")
                    current_status = "not_found"
                else:
                    print(f"   ❌ Failed to get device status: {resp.status}")
                    text = await resp.text()
                    print(f"   Response: {text[:200]}")
                    return
        except Exception as e:
            print(f"   ❌ Error: {e}")
            return
            
        print()
        
        # 2. Listen for breach alerts in real-time
        print("2️⃣ Monitoring for breach alerts (for 60 seconds)...")
        print("   💡 Turn OFF WiFi on the tablet now to trigger breach detection")
        print("   ⏱️  Monitoring...")
        
        start_time = time.time()
        last_status = current_status
        breach_detected = False
        
        while time.time() - start_time < 60:  # Monitor for 60 seconds
            try:
                await asyncio.sleep(2)  # Check every 2 seconds
                
                async with session.get(f"{BACKEND_URL}/api/device/{DEVICE_ID}") as resp:
                    if resp.status == 200:
                        data = await resp.json()
                        new_status = data.get("status", "unknown")
                        
                        if new_status != last_status:
                            elapsed = int(time.time() - start_time)
                            print(f"   📊 [{elapsed:02d}s] Status change: {last_status} → {new_status}")
                            
                            if new_status == "breach":
                                print("   🚨 BREACH DETECTED! ✅")
                                breach_detected = True
                                break
                                
                            last_status = new_status
                        else:
                            # Show periodic status updates every 10 seconds
                            if int(time.time() - start_time) % 10 == 0:
                                elapsed = int(time.time() - start_time)
                                print(f"   📊 [{elapsed:02d}s] Status: {new_status}")
                            
            except Exception as e:
                print(f"   ⚠️ Error checking status: {e}")
                await asyncio.sleep(5)
                
        print()
        
        # 3. Check recent alerts
        print("3️⃣ Checking recent alerts...")
        try:
            async with session.get(f"{BACKEND_URL}/api/alerts/recent?limit=5") as resp:
                if resp.status == 200:
                    alerts = await resp.json()
                    device_alerts = [a for a in alerts if a.get("deviceId") == DEVICE_ID]
                    
                    if device_alerts:
                        print(f"   📋 Found {len(device_alerts)} recent alerts for {DEVICE_ID}:")
                        for alert in device_alerts[:3]:  # Show last 3
                            alert_type = alert.get("type", "unknown")
                            message = alert.get("message", "")
                            ts = alert.get("ts", "")
                            print(f"      • {alert_type}: {message} ({ts})")
                    else:
                        print(f"   📭 No recent alerts found for {DEVICE_ID}")
                else:
                    print(f"   ❌ Failed to get alerts: {resp.status}")
        except Exception as e:
            print(f"   ⚠️ Error getting alerts: {e}")
            
        print()
        
        # 4. Test summary
        print("4️⃣ TEST SUMMARY")
        print("-" * 30)
        if breach_detected:
            print("   ✅ WiFi breach detection is WORKING!")
            print("   ✅ Device properly detects WiFi disconnection")
            print("   ✅ Backend receives and processes breach alerts")
        else:
            print("   ❌ No breach detected during monitoring period")
            print("   💡 Possible issues:")
            print("      • WiFi wasn't actually turned off")
            print("      • Android app isn't running/monitoring")
            print("      • Network connectivity issues")
            print("      • Backend deployment issues")
            
        print()
        print("🔬 Test completed!")

if __name__ == "__main__":
    # Allow customizing backend URL
    import sys
    if len(sys.argv) > 1:
        BACKEND_URL = sys.argv[1]
        print(f"Using backend URL: {BACKEND_URL}")
    
    asyncio.run(test_wifi_monitoring())