#!/usr/bin/env python3
"""
Breach Detection Debug Script

This script helps diagnose why breach alerts from the Android app
are not reaching the dashboard and backend logs.

The issue: Android app shows orange screen (breach detected locally)
but alerts don't appear in dashboard or backend logs.
"""

import requests
import time
from datetime import datetime

BASE_URL = "https://hotel-backend-zqc1.onrender.com"

def check_backend_health():
    """Check if backend is responding"""
    try:
        response = requests.get(f"{BASE_URL}/health", timeout=10)
        print(f"✅ Backend Health: {response.status_code}")
        return True
    except Exception as e:
        print(f"❌ Backend Down: {e}")
        return False

def test_device_registration():
    """Test device registration (simulates Android registration)"""
    print("\n🔧 Testing device registration...")
    
    data = {
        "deviceId": "TEST_TABLET_001",
        "roomId": "Room-101"
    }
    
    try:
        response = requests.post(f"{BASE_URL}/api/devices/register", json=data, timeout=10)
        print(f"Registration Status: {response.status_code}")
        
        if response.status_code == 200:
            result = response.json()
            token = result.get("token")
            print(f"✅ JWT Token: {token[:50]}...")
            return token
        else:
            print(f"❌ Registration Failed: {response.text}")
            return None
            
    except Exception as e:
        print(f"❌ Registration Error: {e}")
        return None

def test_breach_alert(token):
    """Test breach alert (simulates Android breach detection)"""
    print("\n🚨 Testing breach alert...")
    
    headers = {"Authorization": f"Bearer {token}"}
    data = {
        "deviceId": "TEST_TABLET_001",
        "roomId": "Room-101",
        "rssi": -90
    }
    
    try:
        response = requests.post(f"{BASE_URL}/api/alert/breach", json=data, headers=headers, timeout=10)
        print(f"Breach Alert Status: {response.status_code}")
        
        if response.status_code == 200:
            print(f"✅ Breach Alert Sent: {response.json()}")
            return True
        else:
            print(f"❌ Breach Alert Failed: {response.text}")
            return False
            
    except Exception as e:
        print(f"❌ Breach Alert Error: {e}")
        return False

def check_dashboard_alerts():
    """Check if alerts appear in dashboard"""
    print("\n📊 Checking recent alerts...")
    
    try:
        response = requests.get(f"{BASE_URL}/api/alerts/recent", timeout=10)
        print(f"Alerts API Status: {response.status_code}")
        
        if response.status_code == 200:
            alerts = response.json()
            print(f"Recent Alerts Count: {len(alerts)}")
            
            for alert in alerts[-3:]:  # Show last 3 alerts
                device_id = alert.get('deviceId', 'Unknown')
                room_id = alert.get('roomId', 'Unknown')
                message = alert.get('message', 'No message')
                ts = alert.get('ts', 'No timestamp')
                print(f"  - {device_id} • Room {room_id}: {message} ({ts})")
                
            return len(alerts)
        else:
            print(f"❌ Alerts API Failed: {response.text}")
            return 0
            
    except Exception as e:
        print(f"❌ Alerts API Error: {e}")
        return 0

def main():
    print("🔍 BREACH DETECTION DIAGNOSTIC")
    print("=" * 50)
    print(f"Time: {datetime.now()}")
    print(f"Backend: {BASE_URL}")
    
    # Step 1: Check backend health
    if not check_backend_health():
        print("\n❌ DIAGNOSIS: Backend is down or unreachable")
        return
    
    # Step 2: Test device registration 
    token = test_device_registration()
    if not token:
        print("\n❌ DIAGNOSIS: Device registration is failing")
        print("   - Check if backend allows new device registrations")
        print("   - Verify Android app registration logic")
        return
    
    # Step 3: Test breach alert
    if not test_breach_alert(token):
        print("\n❌ DIAGNOSIS: Breach alerts are failing")
        print("   - Check if JWT token is valid")
        print("   - Verify breach endpoint authentication")
        return
    
    # Step 4: Check if alerts appear in dashboard
    alert_count = check_dashboard_alerts()
    
    print("\n" + "=" * 50)
    print("📋 DIAGNOSTIC SUMMARY")
    print("=" * 50)
    
    if alert_count > 0:
        print("✅ BACKEND WORKING: Breach alerts can be sent and retrieved")
        print("\n🔍 ANDROID APP ISSUES TO CHECK:")
        print("   1. JWT token missing or expired in Android SharedPreferences")
        print("   2. Wrong backend URL configured in Android app") 
        print("   3. Network connectivity when WiFi disconnects")
        print("   4. Android app crash during breach detection")
        print("   5. Exception handling hiding the real error")
        print("\n💡 SOLUTIONS:")
        print("   - Check Android logs for authentication errors")
        print("   - Verify backend_url in Android SharedPreferences")
        print("   - Test with mobile data backup connection")
        print("   - Add more detailed logging to Android breach handler")
    else:
        print("❌ BACKEND ISSUES: No alerts found in database")
        print("   - Check MongoDB connection")
        print("   - Verify alert storage logic")

if __name__ == "__main__":
    main()