import requests
import json

# Test production backend URL
BASE_URL = "https://hotel-backend-zqc1.onrender.com"

print("Testing breach alert endpoint...")

# First register a device to get a JWT token
print(f"\n1. Registering device...")
register_data = {
    "deviceId": "TEST_DEVICE_001", 
    "roomId": "101"
}

try:
    response = requests.post(f"{BASE_URL}/api/devices/register", json=register_data)
    print(f"Register Status: {response.status_code}")
    print(f"Register Response: {response.text}")
    
    if response.status_code == 200:
        result = response.json()
        jwt_token = result.get("token")
        
        if jwt_token:
            print(f"\n2. Testing breach alert with JWT token...")
            headers = {"Authorization": f"Bearer {jwt_token}"}
            breach_data = {
                "deviceId": "TEST_DEVICE_001",
                "roomId": "101", 
                "rssi": -80
            }
            
            breach_response = requests.post(f"{BASE_URL}/api/alert/breach", json=breach_data, headers=headers)
            print(f"Breach Status: {breach_response.status_code}")
            print(f"Breach Response: {breach_response.text}")
            
        else:
            print("No JWT token received from registration")
    else:
        print("Registration failed")
        
except Exception as e:
    print(f"Error: {e}")