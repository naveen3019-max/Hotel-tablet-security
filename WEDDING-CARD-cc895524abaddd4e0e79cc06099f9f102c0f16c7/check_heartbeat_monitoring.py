#!/usr/bin/env python3
"""
Check if backend heartbeat monitoring is working correctly
Tests the deployed backend to verify timeout detection is active
"""

import requests
import time
from datetime import datetime

BACKEND_URL = "https://hotel-backend-zqc1.onrender.com"
DEVICE_ID = "TAB-B2A8792B"
ROOM_ID = "5680"

def check_backend_health():
    """Check if backend is responding"""
    try:
        response = requests.get(f"{BACKEND_URL}/", timeout=10)
        print(f"✅ Backend is UP: {response.status_code}")
        return True
    except Exception as e:
        print(f"❌ Backend is DOWN: {e}")
        return False

def get_device_status():
    """Get current device status from backend"""
    try:
        response = requests.get(f"{BACKEND_URL}/api/devices", timeout=10)
        if response.status_code == 200:
            devices = response.json()
            for device in devices:
                if device.get("device_id") == DEVICE_ID:
                    print(f"\n📱 Device {DEVICE_ID} Status:")
                    print(f"   Room: {device.get('room_id')}")
                    print(f"   Status: {device.get('status')}")
                    print(f"   Last Seen: {device.get('last_seen')}")
                    print(f"   WiFi BSSID: {device.get('wifi_bssid')}")
                    print(f"   Battery: {device.get('battery_level')}%")
                    return device
            print(f"\n⚠️ Device {DEVICE_ID} not found in backend")
            return None
        else:
            print(f"❌ Failed to get devices: {response.status_code}")
            return None
    except Exception as e:
        print(f"❌ Error getting device status: {e}")
        return None

def get_recent_alerts():
    """Get recent alerts from backend"""
    try:
        response = requests.get(f"{BACKEND_URL}/api/alerts", timeout=10)
        if response.status_code == 200:
            alerts = response.json()
            print(f"\n🚨 Recent Alerts ({len(alerts)} total):")
            
            # Filter alerts for our device
            device_alerts = [a for a in alerts if a.get('device_id') == DEVICE_ID]
            
            if device_alerts:
                print(f"   Found {len(device_alerts)} alerts for {DEVICE_ID}:")
                for alert in device_alerts[:5]:  # Show last 5
                    alert_type = alert.get('alert_type', 'unknown')
                    source = alert.get('source', 'unknown')
                    created = alert.get('created_at', 'unknown')
                    print(f"   - Type: {alert_type}, Source: {source}, Time: {created}")
            else:
                print(f"   ⚠️ No alerts found for {DEVICE_ID}")
            
            return device_alerts
        else:
            print(f"❌ Failed to get alerts: {response.status_code}")
            return []
    except Exception as e:
        print(f"❌ Error getting alerts: {e}")
        return []

def check_heartbeat_timeout():
    """Calculate if device should trigger heartbeat timeout"""
    device = get_device_status()
    if not device:
        return
    
    last_seen = device.get('last_seen')
    if not last_seen:
        print("\n⚠️ No last_seen timestamp - device may never have connected")
        return
    
    # Parse last_seen timestamp (ISO format)
    try:
        from datetime import datetime, timezone
        last_seen_dt = datetime.fromisoformat(last_seen.replace('Z', '+00:00'))
        now = datetime.now(timezone.utc)
        seconds_ago = (now - last_seen_dt).total_seconds()
        
        print(f"\n⏱️ Heartbeat Timing:")
        print(f"   Last heartbeat: {seconds_ago:.1f} seconds ago")
        print(f"   Timeout threshold: 15 seconds")
        
        if seconds_ago > 15:
            print(f"   🔴 TIMEOUT! Device should be marked as breach")
            print(f"   ⚠️ If status is still 'ok', heartbeat monitoring may not be working")
        else:
            print(f"   ✅ Within timeout window, no breach expected")
            
    except Exception as e:
        print(f"❌ Error parsing timestamp: {e}")

def main():
    print("=" * 70)
    print("🔍 Checking Backend Heartbeat Monitoring")
    print("=" * 70)
    print(f"Backend: {BACKEND_URL}")
    print(f"Device: {DEVICE_ID} (Room {ROOM_ID})")
    print(f"Current Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)
    
    # Check backend health
    if not check_backend_health():
        print("\n❌ Cannot continue - backend is not responding")
        return
    
    # Get device status
    device = get_device_status()
    
    # Get recent alerts
    alerts = get_recent_alerts()
    
    # Check if heartbeat timeout should trigger
    check_heartbeat_timeout()
    
    print("\n" + "=" * 70)
    print("📋 DIAGNOSTIC SUMMARY:")
    print("=" * 70)
    
    if device:
        status = device.get('status', 'unknown')
        print(f"Device Status: {status}")
        
        if status == 'ok':
            print("✅ Device is connected and sending heartbeats")
            print("💡 To test timeout detection: Turn OFF WiFi and wait 20 seconds")
        elif status == 'breach':
            print("🔴 Device is in BREACH state")
            if alerts:
                print("✅ Breach detection is WORKING")
            else:
                print("⚠️ No recent alerts found - may be old breach")
        else:
            print(f"⚠️ Unknown device status: {status}")
    else:
        print("❌ Device not found in backend")
        print("💡 Device may need to be registered first")
    
    print("=" * 70)

if __name__ == "__main__":
    main()
