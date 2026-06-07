#!/usr/bin/env python3
"""
Test what timestamp backend is returning RIGHT NOW
"""

import requests
from datetime import datetime
import pytz

BACKEND_URL = "https://hotel-backend-zqc1.onrender.com"

def test_backend_timestamp():
    """Test backend root endpoint to see server time"""
    print("=" * 70)
    print("🕐 TESTING BACKEND TIMESTAMP")
    print("=" * 70)
    
    # Get current local time
    ist = pytz.timezone('Asia/Kolkata')
    local_now = datetime.now(ist)
    
    print(f"\n📍 Your Local Time (IST): {local_now.strftime('%I:%M:%S %p IST')}")
    print(f"   ISO Format: {local_now.isoformat()}")
    
    # Test backend
    try:
        print(f"\n📡 Testing backend: {BACKEND_URL}")
        response = requests.get(f"{BACKEND_URL}/", timeout=10)
        
        if response.status_code == 200:
            data = response.json()
            backend_time = data.get("timestamp")
            
            print(f"\n✅ Backend Response:")
            print(f"   Status: {data.get('status')}")
            print(f"   Timestamp: {backend_time}")
            
            # Parse backend timestamp
            if backend_time:
                backend_dt = datetime.fromisoformat(backend_time.replace('Z', '+00:00'))
                
                # Convert to IST for comparison
                backend_ist = backend_dt.astimezone(ist)
                
                print(f"\n📊 COMPARISON:")
                print(f"   Backend time (IST): {backend_ist.strftime('%I:%M:%S %p IST')}")
                print(f"   Your local time:    {local_now.strftime('%I:%M:%S %p IST')}")
                
                # Calculate difference
                diff = abs((backend_ist - local_now).total_seconds())
                
                if diff < 60:  # Within 1 minute
                    print(f"   ✅ TIME IS CORRECT! (difference: {int(diff)} seconds)")
                else:
                    hours_diff = diff / 3600
                    print(f"   ❌ TIME IS WRONG! (difference: {hours_diff:.1f} hours)")
                    
                    if abs(hours_diff - 5.5) < 0.1:
                        print(f"   🐛 This is the UTC/IST offset - backend still using UTC!")
                
            else:
                print("   ⚠️ No timestamp in response")
                
        else:
            print(f"❌ Backend returned {response.status_code}")
            
    except Exception as e:
        print(f"❌ Error testing backend: {e}")
    
    print("\n" + "=" * 70)

if __name__ == "__main__":
    test_backend_timestamp()
