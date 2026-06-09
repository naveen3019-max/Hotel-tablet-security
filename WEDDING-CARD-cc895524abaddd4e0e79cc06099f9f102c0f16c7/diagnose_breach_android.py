#!/usr/bin/env python3
"""
Android Breach Detection Diagnostic

This script helps diagnose why breach alerts are not being detected
by the Android app.
"""

import requests
import json

def check_android_device_registration():
    """Check if the Android device is properly registered with the backend"""
    
    print("🔧 ANDROID DEVICE REGISTRATION DIAGNOSTIC")
    print("=" * 50)
    
    backend_url = "https://hotel-backend-zqc1.onrender.com"
    
    # Test with typical Android device IDs
    test_devices = ["TAB-UNKNOWN", "TAB-1B2022B0", "TAB-9C5E8555", "TAB-AA4CF9A3"]
    
    for device_id in test_devices:
        print(f"\n🔍 Testing registration for device: {device_id}")
        
        try:
            # Test device registration
            data = {
                "deviceId": device_id,
                "roomId": "Test-Room"
            }
            
            response = requests.post(f"{backend_url}/api/devices/register", json=data, timeout=10)
            
            if response.status_code == 200:
                result = response.json()
                print(f"✅ Registration successful")
                print(f"   Device ID: {device_id}")
                print(f"   JWT Token: {result.get('token', 'N/A')[:50]}...")
                return device_id, result.get('token')
            else:
                print(f"❌ Registration failed: {response.status_code}")
                print(f"   Response: {response.text}")
                
        except Exception as e:
            print(f"❌ Registration error: {e}")
    
    return None, None

def check_breach_detection_settings():
    """Check common breach detection issues"""
    
    print("\n\n📱 ANDROID BREACH DETECTION CHECKLIST")
    print("=" * 50)
    
    issues = []
    
    print("Common issues that prevent breach detection:")
    print()
    
    print("1. ❓ JWT Token Missing")
    print("   - Open Android app → Settings → Check if device is registered")
    print("   - If not registered, complete device registration")
    print("   - Check backend URL is: https://hotel-backend-zqc1.onrender.com")
    print()
    
    print("2. ❓ RSSI Threshold Too Strict")
    print("   - Default threshold: -70 dBm")
    print("   - If WiFi signal is stronger than -70 dBm, no breach detected")
    print("   - Try setting threshold to -50 dBm for testing")
    print()
    
    print("3. ❓ WiFi PIN Protection Active")
    print("   - PIN protection can block breach detection")
    print("   - Check if WiFi PIN dialog is currently active")
    print("   - Disable temporarily for testing")
    print()
    
    print("4. ❓ Wrong Target BSSID/SSID")
    print("   - App only monitors specific WiFi network")
    print("   - Check if device is connected to correct WiFi network")
    print("   - Verify BSSID and SSID match configuration")
    print()
    
    print("5. ❓ Service Not Running")
    print("   - KioskService must be running for breach detection")
    print("   - Check Android logs for service startup messages")
    print("   - Look for 'WiFi Fence started successfully' in logs")

def test_current_breach_alerts():
    """Check recent breach alerts in the system"""
    
    print("\n\n🚨 RECENT BREACH ALERTS")
    print("=" * 50)
    
    try:
        response = requests.get("https://hotel-backend-zqc1.onrender.com/api/alerts/recent?limit=10")
        
        if response.status_code == 200:
            alerts = response.json()
            recent_breaches = [a for a in alerts if a.get('type') == 'breach']
            
            print(f"Recent breach alerts in last 24 hours: {len(recent_breaches)}")
            print()
            
            for alert in recent_breaches[:5]:
                device_id = alert.get('deviceId', 'Unknown')
                room_id = alert.get('roomId', 'Unknown')
                timestamp = alert.get('ts', 'Unknown')
                message = alert.get('message', 'No message')
                
                print(f"🚨 {device_id} • Room {room_id}")
                print(f"   Time: {timestamp}")
                print(f"   Message: {message}")
                print()
                
        else:
            print(f"❌ Failed to fetch alerts: {response.status_code}")
            
    except Exception as e:
        print(f"❌ Error fetching alerts: {e}")

def main():
    print("🔍 ANDROID BREACH DETECTION DIAGNOSTIC")
    print("=" * 60)
    print("This tool helps diagnose why Android breach detection is not working")
    print()
    
    # Check device registration
    device_id, token = check_android_device_registration()
    
    # Show common issues checklist
    check_breach_detection_settings()
    
    # Check recent alerts
    test_current_breach_alerts()
    
    print("\n\n💡 QUICK FIX SUGGESTIONS")
    print("=" * 50)
    
    if device_id and token:
        print("✅ Backend registration is working")
        print()
        print("Most likely issues:")
        print("1. Android device not properly registered (no JWT token)")
        print("2. RSSI threshold too strict (-70 dBm default)")
        print("3. WiFi PIN protection interfering")
        print("4. Wrong WiFi network (BSSID/SSID mismatch)")
        print()
        print("📱 ANDROID DEBUG STEPS:")
        print("1. Enable USB debugging and connect device")
        print("2. Run: adb logcat | grep KioskService")
        print("3. Look for 'Device Configuration' and 'WiFi Fence' logs")
        print("4. Check for 'JWT Token: Present' message")
        print("5. Test breach by turning WiFi OFF")
        
    else:
        print("❌ Backend registration failing")
        print("   Check if backend is running and accessible")

if __name__ == "__main__":
    main()