# Test backend endpoints
$baseUrl = "https://hotel-backend-zqc1.onrender.com"

Write-Host "Testing backend connectivity..." -ForegroundColor Cyan

# Test health endpoint
Write-Host ""
Write-Host "1. Testing /health endpoint..." -ForegroundColor Yellow
try {
    $health = Invoke-WebRequest -Uri "$baseUrl/health" -UseBasicParsing
    Write-Host "✓ Health check successful" -ForegroundColor Green
    Write-Host $health.Content
} catch {
    Write-Host "✗ Health check failed: $_" -ForegroundColor Red
}

# Test register endpoint
Write-Host ""
Write-Host "2. Testing /api/devices/register endpoint..." -ForegroundColor Yellow
try {
    $registerBody = @{
        deviceId = "TEST-DEVICE-001"
        roomId = "101"
        hotelId = "test-hotel"
    } | ConvertTo-Json

    $register = Invoke-WebRequest -Uri "$baseUrl/api/devices/register" `
        -Method POST `
        -Body $registerBody `
        -ContentType "application/json" `
        -UseBasicParsing

    Write-Host "✓ Register successful" -ForegroundColor Green
    $response = $register.Content | ConvertFrom-Json
    $token = $response.token
    Write-Host "Token received: $($token.Substring(0, 20))..."
    
    # Test heartbeat with the token
    Write-Host ""
    Write-Host "3. Testing /api/heartbeat endpoint..." -ForegroundColor Yellow
    $heartbeatBody = @{
        deviceId = "TEST-DEVICE-001"
        roomId = "101"
        wifiBssid = "AA:BB:CC:DD:EE:FF"
        rssi = -45
        battery = 85
    } | ConvertTo-Json

    $heartbeat = Invoke-WebRequest -Uri "$baseUrl/api/heartbeat" `
        -Method POST `
        -Headers @{ "Authorization" = "Bearer $token" } `
        -Body $heartbeatBody `
        -ContentType "application/json" `
        -UseBasicParsing

    Write-Host "✓ Heartbeat successful" -ForegroundColor Green
    Write-Host $heartbeat.Content

} catch {
    Write-Host "✗ Test failed: $_" -ForegroundColor Red
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $reader.BaseStream.Position = 0
        $reader.DiscardBufferedData()
        $responseBody = $reader.ReadToEnd()
        Write-Host "Response body: $responseBody" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "Test completed." -ForegroundColor Cyan
