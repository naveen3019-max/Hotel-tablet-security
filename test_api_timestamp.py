import requests
from datetime import datetime

# Get devices from API
response = requests.get("https://hotel-backend-zqc1.onrender.com/api/devices")
devices = response.json()

# Find the new device
new_device = next((d for d in devices if d.get("deviceId") == "TAB-F3CE674D"), None)

if new_device:
    print("=" * 60)
    print(f"Device: {new_device['deviceId']} (Room {new_device.get('roomId')})")
    print(f"Status: {new_device.get('status')}")
    print(f"Last Seen: {new_device.get('lastSeen')}")
    print("=" * 60)
    print(f"\nYour PC time now: {datetime.now().strftime('%d/%m/%Y %I:%M:%S %p')}")
    print()
    
    # Parse the timestamp
    last_seen = new_device.get('lastSeen')
    if last_seen:
        # Extract time from ISO format
        if 'T' in last_seen:
            time_part = last_seen.split('T')[1].split('+')[0]
            date_part = last_seen.split('T')[0]
            print(f"Timestamp breakdown:")
            print(f"  Date: {date_part}")
            print(f"  Time: {time_part}")
            
            # Check if it's IST
            if '+05:30' in last_seen:
                print(f"  Timezone: IST (correct!)")
            else:
                print(f"  Timezone: Unknown or wrong")
else:
    print("Device TAB-F3CE674D not found!")
    print(f"\nFound {len(devices)} devices:")
    for d in devices[:5]:
        print(f"  - {d.get('deviceId')} (Room {d.get('roomId')}): {d.get('lastSeen')}")
