import requests

# Configure Room 876 with the current WiFi network
# Replace these values with YOUR hotel WiFi details:

ROOM_CONFIG = {
    "room_id": "876",
    "ssid": "YourHotelWiFi",        # Replace with actual WiFi name
    "bssid": "22:a7:c2:ef:e4:91",  # Current BSSID from device
    "rssi_threshold": -80            # Signal must be stronger than -80 dBm
}

print("Configuring Room 876...")
print(f"  SSID: {ROOM_CONFIG['ssid']}")
print(f"  BSSID: {ROOM_CONFIG['bssid']}")
print(f"  Min RSSI: {ROOM_CONFIG['rssi_threshold']} dBm")
print()

# Create the room configuration
response = requests.post(
    "https://hotel-backend-zqc1.onrender.com/api/rooms",
    json=ROOM_CONFIG
)

if response.status_code in [200, 201]:
    print("✅ Room 876 configured successfully!")
    print("\nNow the device will:")
    print("  - Allow connections to BSSID: 22:a7:c2:ef:e4:91")
    print("  - Require signal strength > -80 dBm")
    print("  - Show breach ONLY if WiFi disconnects or signal is too weak")
else:
    print(f"❌ Error: {response.status_code}")
    print(response.text)
