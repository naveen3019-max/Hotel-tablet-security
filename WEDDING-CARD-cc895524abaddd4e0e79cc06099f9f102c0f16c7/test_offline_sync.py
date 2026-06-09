#!/usr/bin/env python3
"""
Test Offline Queue Sync

This script helps diagnose and manually trigger sync of queued breach alerts.
"""

import requests
import json
from datetime import datetime

BACKEND_URL = "https://hotel-backend-zqc1.onrender.com"

def test_backend_connectivity():
    """Test if backend is reachable"""
    print("🔍 Testing backend connectivity...")
    try:
        response = requests.get(f"{BACKEND_URL}/health", timeout=10)
        print(f"✅ Backend Status: {response.status_code}")
        if response.status_code == 200:
            print(f"✅ Response: {response.json()}")
            return True
        return False
    except Exception as e:
        print(f"❌ Backend Error: {e}")
        return False

def check_recent_alerts():
    """Check for recent breach alerts"""
    print("\n🔍 Checking recent alerts...")
    try:
        response = requests.get(f"{BACKEND_URL}/api/alerts/recent?limit=10", timeout=10)
        if response.status_code == 200:
            alerts = response.json()
            print(f"✅ Found {len(alerts)} recent alerts")
            
            for alert in alerts[:3]:  # Show last 3 alerts
                ts = alert.get('ts', 'Unknown')
                device_id = alert.get('payload', {}).get('deviceId', 'Unknown')
                alert_type = alert.get('type', 'Unknown')
                print(f"   📅 {ts} - Device: {device_id} - Type: {alert_type}")
                
            return alerts
        else:
            print(f"❌ Failed to get alerts: {response.status_code}")
            return []
    except Exception as e:
        print(f"❌ Error getting alerts: {e}")
        return []

def check_device_status(device_id="TAB-D9413C44"):
    """Check current device status"""
    print(f"\n🔍 Checking device status for {device_id}...")
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
                status = target_device.get('status', 'unknown')
                room_id = target_device.get('roomId', 'unknown')
                last_seen = target_device.get('lastSeen', 'unknown')
                rssi = target_device.get('rssi', 'unknown')
                
                print(f"✅ Device Found:")
                print(f"   📱 Device ID: {device_id}")
                print(f"   🏠 Room: {room_id}")
                print(f"   🔴 Status: {status}")
                print(f"   📶 RSSI: {rssi}")
                print(f"   ⏰ Last Seen: {last_seen}")
                
                if status == "breach":
                    print(f"🚨 DEVICE IS IN BREACH STATE!")
                elif status == "ok":
                    print(f"✅ Device status is OK")
                    
                return target_device
            else:
                print(f"❌ Device {device_id} not found in dashboard")
                return None
        else:
            print(f"❌ Failed to get devices: {response.status_code}")
            return None
    except Exception as e:
        print(f"❌ Error getting device status: {e}")
        return None

def main():
    print("🔧 OFFLINE QUEUE SYNC DIAGNOSTIC")
    print("=" * 50)
    
    # Test backend connectivity
    if not test_backend_connectivity():
        print("❌ Backend not reachable. Check internet connection.")
        return
    
    # Check recent alerts
    recent_alerts = check_recent_alerts()
    
    # Check device status
    device_status = check_device_status()
    
    print("\n📊 SUMMARY:")
    print("=" * 30)
    
    if device_status:
        status = device_status.get('status', 'unknown')
        if status == "breach":
            print("🚨 Dashboard shows device in BREACH state")
            print("✅ This means breach alerts ARE reaching the backend!")
            print("✅ Green→Red indicator should be working")
        elif status == "ok":
            print("✅ Dashboard shows device is OK")
            print("ℹ️  This suggests either:")
            print("   - No queued breach alerts to sync")
            print("   - Alerts were already synced and cleared")
            print("   - Device recovered properly")
        else:
            print(f"⚠️  Unknown device status: {status}")
    else:
        print("❌ Device not found in backend")
    
    print(f"\nℹ️  Recent alerts count: {len(recent_alerts)}")
    if len(recent_alerts) > 0:
        print("ℹ️  Backend is receiving alerts from devices")
    
    print("\n🔧 NEXT STEPS:")
    print("1. Install updated APK: app-debug.apk")
    print("2. Test WiFi disconnection to trigger breach")
    print("3. Reconnect WiFi to test automatic sync")
    print("4. Check dashboard for status changes")

if __name__ == "__main__":
    main()