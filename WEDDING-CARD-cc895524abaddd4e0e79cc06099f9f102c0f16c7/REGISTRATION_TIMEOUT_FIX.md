# Registration Timeout Troubleshooting

## Changes Made

### 1. Increased Timeout to 60 Seconds
- **Why**: Render free tier can have cold starts that take 50+ seconds
- **Location**: `AgentRepository.kt`
- Timeout changed from 30s to 60s for connect/read/write operations
- Added retry on connection failure

### 2. Improved Error Messages
- Now shows specific causes for each error type
- Helps identify if it's timeout, network, SSL, or other issue

### 3. Added Progress Updates
- Shows "Connecting to backend..." with warning about 60 second wait
- Shows timing information in logs

## Quick Fixes to Try

### Option 1: Wake Up the Backend First
Before registering the Android device, open this URL in a browser on any device:
```
https://hotel-backend-zqc1.onrender.com/health
```

Wait until you see a response (may take 50+ seconds on first access). Then try registering the Android device.

### Option 2: Check Internet Connection
On the tablet:
1. Open Chrome browser
2. Navigate to: `https://hotel-backend-zqc1.onrender.com/health`
3. Verify you can see the health check response
4. If you can't, check:
   - WiFi is connected
   - Internet is working
   - No firewall blocking

### Option 3: Verify Backend URL
In the registration screen, make sure the backend URL is exactly:
```
https://hotel-backend-zqc1.onrender.com/
```
(with trailing slash)

### Option 4: Check Android Logs
Connect tablet via USB and run:
```powershell
adb logcat -s "Provisioning:*" "HTTP:*" "AgentRepository:*"
```

Look for:
- Connection timeout → Backend is cold, try again
- Unknown host → Internet/DNS issue
- SSL error → Check device date/time
- Connection refused → Backend might be down

## Testing Backend
Run this PowerShell script to test the backend:
```powershell
.\test-backend.ps1
```

Expected output:
- Health check: ✓ Success
- Register: ✓ Success with token
- Heartbeat: ✓ Success

## Common Issues

### Issue: "Connection timeout - 60 seconds"
**Cause**: Render free tier cold start
**Fix**: 
1. Visit backend URL in browser first to wake it up
2. Wait 1 minute
3. Try registration again

### Issue: "Cannot reach server"
**Cause**: No internet or wrong URL
**Fix**:
1. Check tablet WiFi is connected
2. Open browser and test: `https://hotel-backend-zqc1.onrender.com/health`
3. Verify URL in registration screen

### Issue: "SSL/HTTPS error"
**Cause**: Certificate or date/time issue
**Fix**:
1. Check tablet date/time is correct
2. Update Android System WebView if old Android version

### Issue: "Connection refused"
**Cause**: Backend is down or URL wrong
**Fix**:
1. Test backend with PowerShell script
2. Check Render dashboard
3. Verify URL has `https://` not `http://`

## APK Location
Updated APK with 60s timeout:
```
android-agent/app/build/outputs/apk/debug/app-debug.apk
```

## Next Steps
1. Install the updated APK
2. Wake up backend by visiting health URL
3. Try registration (wait full 60 seconds)
4. If still fails, check adb logcat for specific error
