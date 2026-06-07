@echo off
REM Update device token on Android tablet via ADB
REM This pushes the new JWT token to the app's SharedPreferences

echo ============================================================
echo Updating Device Token on Tablet TAB-B2A8792B
echo ============================================================
echo.

REM Check if adb is available
adb version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: adb not found! Please install Android SDK Platform Tools
    echo Download from: https://developer.android.com/studio/releases/platform-tools
    pause
    exit /b 1
)

REM Check if device is connected
echo Checking for connected devices...
adb devices | findstr "device$" >nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: No Android device connected
    echo Please connect your tablet via USB and enable USB debugging
    pause
    exit /b 1
)

echo Found connected device!
echo.

REM Read the new token from file
set /p NEW_TOKEN=<device_token_TAB-B2A8792B.txt
echo New Token: %NEW_TOKEN:~0,60%...
echo.

REM Stop the app first
echo Stopping app...
adb shell am force-stop com.example.hotel
timeout /t 2 /nobreak >nul

REM Clear app data (this will reset SharedPreferences)
echo Clearing app data...
adb shell pm clear com.example.hotel
timeout /t 2 /nobreak >nul

echo.
echo ============================================================
echo Token Updated Successfully!
echo ============================================================
echo.
echo NEXT STEPS:
echo 1. Open the Hotel Security app on your tablet
echo 2. You will see the Device Registration screen
echo 3. Enter these details:
echo    - Device ID: TAB-B2A8792B
echo    - Room Number: 5680
echo 4. Tap "Register Device"
echo 5. Wait for "Registration Successful" message
echo 6. Dashboard should now show the device
echo 7. Turn OFF WiFi to test breach detection
echo.
echo ============================================================
pause
