# WiFi PIN Protection - Installation & Testing Script

Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "   WiFi PIN Protection - Quick Install & Test" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# Get tablet IP
$tabletIP = Read-Host "Enter tablet IP address (press Enter for 192.168.40.77)"
if ([string]::IsNullOrWhiteSpace($tabletIP)) {
    $tabletIP = "192.168.40.77"
}

Write-Host ""
Write-Host "Step 1: Connecting to tablet..." -ForegroundColor Yellow
adb connect "${tabletIP}:5555"

Start-Sleep -Seconds 2

# Check if connected
$devices = adb devices | Select-String "${tabletIP}"
if ($devices) {
    Write-Host "✅ Connected to $tabletIP" -ForegroundColor Green
} else {
    Write-Host "❌ Could not connect to tablet" -ForegroundColor Red
    Write-Host "Make sure:" -ForegroundColor Yellow
    Write-Host "  1. Tablet WiFi is ON" -ForegroundColor Yellow
    Write-Host "  2. Tablet is on the same network" -ForegroundColor Yellow
    Write-Host "  3. USB debugging is enabled" -ForegroundColor Yellow
    Write-Host "  4. Run: adb tcpip 5555 (if connected via USB)" -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
Write-Host "Step 2: Installing updated APK..." -ForegroundColor Yellow
$apkPath = "app\build\outputs\apk\release\app-release-unsigned.apk"
$fullPath = Join-Path (Get-Location) $apkPath

if (Test-Path $fullPath) {
    $apkSize = [math]::Round((Get-Item $fullPath).Length / 1MB, 2)
    Write-Host "   APK Size: $apkSize MB" -ForegroundColor Gray
    
    adb install -r $fullPath
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ APK installed successfully!" -ForegroundColor Green
    } else {
        Write-Host "❌ APK installation failed" -ForegroundColor Red
        Read-Host "Press Enter to exit"
        exit 1
    }
} else {
    Write-Host "❌ APK not found at: $fullPath" -ForegroundColor Red
    Write-Host "Run: .\gradlew.bat assembleRelease first" -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
Write-Host "Step 3: Starting log monitor..." -ForegroundColor Yellow
Write-Host "   Watching for WiFi PIN events..." -ForegroundColor Gray
Write-Host ""
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Green
Write-Host "   ✅ INSTALLATION COMPLETE!" -ForegroundColor Green
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Green
Write-Host ""
Write-Host "🧪 TEST NOW:" -ForegroundColor Cyan
Write-Host "   1. On the tablet, try to turn OFF WiFi" -ForegroundColor White
Write-Host "   2. WiFi should turn back ON immediately" -ForegroundColor White
Write-Host "   3. PIN dialog should appear" -ForegroundColor White
Write-Host "   4. Enter PIN: 1234" -ForegroundColor White
Write-Host ""
Write-Host "📋 Monitoring logs below (Ctrl+C to stop)..." -ForegroundColor Yellow
Write-Host "─────────────────────────────────────────────────" -ForegroundColor Gray
Write-Host ""

# Monitor logs
adb logcat -c
adb logcat -s "WifiStateReceiver:*" "WifiPinDialog:*" "KioskService:*" | ForEach-Object {
    if ($_ -match "🚨|❌|BREACH") {
        Write-Host $_ -ForegroundColor Red
    } elseif ($_ -match "✅|SUCCESS") {
        Write-Host $_ -ForegroundColor Green
    } elseif ($_ -match "📱|PIN|🔒") {
        Write-Host $_ -ForegroundColor Cyan
    } elseif ($_ -match "⚠️|WARNING") {
        Write-Host $_ -ForegroundColor Yellow
    } else {
        Write-Host $_ -ForegroundColor Gray
    }
}
