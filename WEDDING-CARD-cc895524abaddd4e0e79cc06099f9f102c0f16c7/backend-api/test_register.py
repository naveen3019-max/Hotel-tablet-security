import requests

urls = [
    "http://10.247.23.77:8080/",
    "http://10.247.23.77:8080/health",
    "http://10.247.23.77:8080/api/devices/register"
]

for url in urls:
    print(f"\nTesting {url}...")
    try:
        if "register" in url:
            response = requests.post(url, json={"deviceId": "TEST", "roomId": "101"})
        else:
            response = requests.get(url)
        print(f"Status Code: {response.status_code}")
        print(f"Body: {response.text}")
    except Exception as e:
        print(f"Error: {e}")
