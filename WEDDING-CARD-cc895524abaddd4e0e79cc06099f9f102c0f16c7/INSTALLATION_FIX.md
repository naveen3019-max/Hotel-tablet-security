# App Installation Fix - Complete Guide

## 🔴 Problem: App Not Installing

### Most Common Cause: **SIGNATURE MISMATCH**
The new APK has a different signature than the installed version.

---

## ✅ SOLUTION 1: Uninstall Old Version First (RECOMMENDED)

### Option A: Via ADB (if device connected)
```powershell
# Connect to device
adb connect 192.168.40.77:5555

# Uninstall old version
adb uninstall com.example.hotel

# Install new version
adb install C:\Users\narra\OneDrive\Desktop\WEDDING-CARD-cc895524abaddd4e0e79cc06099f9f102c0f16c7\RELEASE\HotelSecurityAgent_v2.0.0_2026-01-09.apk
```

### Option B: Manual Uninstall on Tablet
1. Open **Settings** on tablet
2. Go to **Apps** or **Applications**
3. Find **Hotel Agent** or **Hotel Security**
4. Tap **Uninstall**
5. Confirm uninstall
6. Now install the new APK (via WhatsApp file)

---

## ✅ SOLUTION 2: Create Signed APK

The current APK is unsigned. Let me create a properly signed version:

### Auto-Sign Script:
```powershell
cd C:\Users\narra\OneDrive\Desktop\WEDDING-CARD-cc895524abaddd4e0e79cc06099f9f102c0f16c7\android-agent

# Generate keystore (one-time only)
keytool -genkey -v -keystore hotel-release.keystore -alias hotel -keyalg RSA -keysize 2048 -validity 10000

# Sign the APK
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore hotel-release.keystore app\build\outputs\apk\release\app-release-unsigned.apk hotel

# Verify signature
jarsigner -verify -verbose -certs app\build\outputs\apk\release\app-release-unsigned.apk
```

**Note:** When running keytool, it will ask for:
- Keystore password (remember this!)
- Name, Organization, etc. (can press Enter to skip)

---

## ✅ SOLUTION 3: Force Install (Bypass Existing)

### Via ADB with -r flag (reinstall):
```powershell
adb connect 192.168.40.77:5555
adb install -r -d C:\Users\narra\OneDrive\Desktop\WEDDING-CARD-cc895524abaddd4e0e79cc06099f9f102c0f16c7\RELEASE\HotelSecurityAgent_v2.0.0_2026-01-09.apk
```

The `-r` flag means "reinstall" (replace existing)
The `-d` flag allows downgrade/debuggable apps

---

## ✅ SOLUTION 4: Install as New Package

If you want to keep both versions, change the package name:

### Edit build.gradle.kts:
```kotlin
android {
    namespace = "com.example.hotel.v2"  // Changed from com.example.hotel
    defaultConfig {
        applicationId = "com.example.hotel.v2"  // Changed
    }
}
```

Then rebuild:
```powershell
cd android-agent
.\gradlew.bat assembleRelease
```

---

## 🔍 Detailed Error Diagnosis

### Check What Error You're Getting:

#### Error: "App not installed"
**Cause:** Signature mismatch or corrupted APK
**Fix:** Uninstall old version first (Solution 1)

#### Error: "Package conflicts with existing package"
**Cause:** Different signature
**Fix:** Use Solution 1 or 3

#### Error: "Installation blocked"
**Cause:** Unknown sources not allowed
**Fix:** Settings → Security → Enable "Unknown Sources"

#### Error: "Insufficient storage"
**Cause:** Not enough space
**Fix:** Delete apps/files to free up space

#### Error: "Parse error"
**Cause:** Corrupted APK or incompatible Android version
**Fix:** Re-download APK, check Android version

---

## 🚀 Quick Fix Script (Automated)

Run this PowerShell script:

```powershell
# Quick uninstall and reinstall
$apk = "C:\Users\narra\OneDrive\Desktop\WEDDING-CARD-cc895524abaddd4e0e79cc06099f9f102c0f16c7\RELEASE\HotelSecurityAgent_v2.0.0_2026-01-09.apk"

Write-Host "Connecting to device..." -ForegroundColor Yellow
adb connect 192.168.40.77:5555

Write-Host "Uninstalling old version..." -ForegroundColor Yellow
adb uninstall com.example.hotel

Write-Host "Installing new version..." -ForegroundColor Yellow
adb install $apk

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Installation successful!" -ForegroundColor Green
} else {
    Write-Host "❌ Installation failed" -ForegroundColor Red
    Write-Host "Try manual uninstall on tablet first" -ForegroundColor Yellow
}
```

---

## 📱 Manual Installation Steps (Most Reliable)

1. **On Tablet:**
   - Settings → Apps → Hotel Agent → **Uninstall**

2. **Send New APK:**
   - Send via WhatsApp/Telegram

3. **On Tablet:**
   - Download the APK
   - Tap to open
   - Enable "Install from Unknown Sources" if asked
   - Tap **Install**

4. **Verify:**
   - Open the app
   - Check if it's the new version (should have WiFi PIN feature)

---

## 🔧 Still Not Working?

### Check Installation Logs:
```powershell
adb logcat -c
adb install -r C:\Users\narra\...\HotelSecurityAgent_v2.0.0_2026-01-09.apk
adb logcat -d | Select-String "PackageInstaller|INSTALL"
```

### Check Current Installed Package:
```powershell
adb shell pm list packages | Select-String "hotel"
adb shell dumpsys package com.example.hotel | Select-String "versionName"
```

### Force Remove All Traces:
```powershell
adb shell pm uninstall -k --user 0 com.example.hotel
adb shell pm clear com.example.hotel
```

---

## ✅ Recommended: Complete Clean Install

1. **Uninstall via ADB:**
   ```powershell
   adb connect 192.168.40.77:5555
   adb uninstall com.example.hotel
   ```

2. **Clear app data:**
   ```powershell
   adb shell pm clear com.example.hotel
   ```

3. **Restart tablet**

4. **Install new APK:**
   ```powershell
   adb install C:\Users\narra\OneDrive\Desktop\WEDDING-CARD-cc895524abaddd4e0e79cc06099f9f102c0f16c7\RELEASE\HotelSecurityAgent_v2.0.0_2026-01-09.apk
   ```

---

## Need Help?

Run this diagnostic:
```powershell
Write-Host "Device Status:" -ForegroundColor Cyan
adb devices

Write-Host "`nInstalled Packages:" -ForegroundColor Cyan
adb shell pm list packages | Select-String "hotel"

Write-Host "`nApp Info:" -ForegroundColor Cyan
adb shell dumpsys package com.example.hotel | Select-String "versionCode|versionName|signatures"
```

This will show you what's currently installed and help diagnose the issue.
