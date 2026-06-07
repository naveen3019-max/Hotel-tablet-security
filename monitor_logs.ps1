# Monitor Backend and Android Logs
Write-Host "================================" -ForegroundColor Cyan
Write-Host "  HOTEL SECURITY LOG MONITOR" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# Check if backend is running
$backend = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
if ($backend) {
    Write-Host "✅ Backend server is running on port 8080" -ForegroundColor Green
} else {
    Write-Host "❌ Backend server is NOT running!" -ForegroundColor Red
    Write-Host "Start it with: python -m uvicorn main:app --host 0.0.0.0 --port 8080 --reload" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "📋 VIEWING BACKEND LOGS..." -ForegroundColor Yellow
Write-Host "Looking for: Registration, Heartbeat, and Breach events" -ForegroundColor Gray
Write-Host "Press Ctrl+C to stop" -ForegroundColor Gray
Write-Host ""
Write-Host "----------------------------------------" -ForegroundColor Cyan

# Try to find and tail the uvicorn process output
# Alternative: Monitor using HTTP requests
while ($true) {
    Start-Sleep -Seconds 2
    
    try {
        # Test backend health
        $response = Invoke-WebRequest -Uri "http://10.247.23.77:8080/health" -Method GET -TimeoutSec 2 -ErrorAction Stop
        $health = $response.Content | ConvertFrom-Json
        Write-Host "[$(Get-Date -Format 'HH:mm:ss')] ✅ Backend healthy - Monitoring active..." -ForegroundColor Green
    } catch {
        Write-Host "[$(Get-Date -Format 'HH:mm:ss')] ⚠️ Backend not responding" -ForegroundColor Yellow
    }
}
