#!/usr/bin/env python3
"""
Reset Device Status Script

This script resets device status from 'compromised' to 'ok' 
since tamper detection is now disabled.
"""

import requests
import json

BACKEND_URL = "https://hotel-backend-zqc1.onrender.com"

def reset_device_status(device_id="TAB-D9413C44"):
    """Reset device status to 'ok' if it's currently 'compromised'"""
    print(f"🔧 RESETTING DEVICE STATUS FOR: {device_id}")
    print("=" * 50)
    
    # First check current status
    try:
        response = requests.get(f"{BACKEND_URL}/api/devices", timeout=10)
        if response.status_code == 200:
            devices = response.json()
            
            target_device = None
            for device in devices:
                if device.get('deviceId') == device_id:
                    target_device = device
                    break
            
            if target_device:
                current_status = target_device.get('status', 'unknown')
                print(f"📊 Current Status: {current_status}")
                
                if current_status == "compromised":
                    print(f"🔄 Status is 'compromised' - needs reset")
                    
                    # Register device to trigger heartbeat and status reset
                    print(f"🔄 Sending registration to trigger status reset...")
                    
                    registration_data = {
                        "deviceId": device_id,
                        "roomId": target_device.get('roomId', '420')
                    }
                    
                    reg_response = requests.post(
                        f"{BACKEND_URL}/api/devices/register", 
                        json=registration_data, 
                        timeout=10
                    )
                    
                    if reg_response.status_code == 200:
                        result = reg_response.json()
                        print(f"✅ Registration successful - Device should reset to 'ok' status")
                        print(f"✅ JWT Token received: {result.get('token', 'N/A')[:50]}...")
                        
                        # Check status again
                        response2 = requests.get(f"{BACKEND_URL}/api/devices", timeout=10)
                        if response2.status_code == 200:
                            devices2 = response2.json()
                            for device2 in devices2:
                                if device2.get('deviceId') == device_id:
                                    new_status = device2.get('status', 'unknown')
                                    print(f"📊 New Status: {new_status}")
                                    
                                    if new_status == "ok":
                                        print(f"🎉 SUCCESS! Device status reset to 'ok'")
                                    break
                    else:
                        print(f"❌ Registration failed: {reg_response.status_code}")
                        print(f"Response: {reg_response.text}")
                        
                elif current_status == "ok":
                    print(f"✅ Status is already 'ok' - no reset needed")
                elif current_status == "breach":
                    print(f"🚨 Status is 'breach' - will be reset when WiFi reconnects")
                else:
                    print(f"⚠️  Unknown status: {current_status}")
            else:
                print(f"❌ Device {device_id} not found in backend")
        else:
            print(f"❌ Failed to get devices: {response.status_code}")
            
    except Exception as e:
        print(f"❌ Error: {e}")

def main():
    print("🔧 DEVICE STATUS RESET UTILITY")
    print("=" * 50)
    print("Purpose: Clear 'compromised' status since tamper detection is disabled")
    print("")
    
    reset_device_status()
    
    print("\n📋 SUMMARY:")
    print("• Tamper detection is now DISABLED in Android app")
    print("• Backend will clear 'compromised' status on good WiFi heartbeat")
    print("• Focus is now only on WiFi breach and battery alerts")
    print("• Install updated APK to apply changes")

if __name__ == "__main__":
    main()