# Quick Deployment Check Script - Windows PowerShell

Write-Host "============================================" -ForegroundColor Cyan
Write-Host " Hotel Tablet Security - Production Setup" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# Check if .env exists
if (-not (Test-Path ".env")) {
    Write-Host "❌ .env file not found!" -ForegroundColor Red
    Write-Host "📝 Copying .env.production template..." -ForegroundColor Yellow
    Copy-Item ".env.production" ".env"
    Write-Host "✅ .env file created" -ForegroundColor Green
    Write-Host ""
    Write-Host "⚠️  IMPORTANT: Edit .env and fill in your values:" -ForegroundColor Yellow
    Write-Host "   - MONGODB_URL (MongoDB Atlas)" -ForegroundColor Gray
    Write-Host "   - REDIS_URL (Upstash)" -ForegroundColor Gray
    Write-Host "   - API_TOKEN (generate secure token)" -ForegroundColor Gray
    Write-Host "   - SECRET_KEY (generate secure key)" -ForegroundColor Gray
    Write-Host ""
    exit 1
}

Write-Host "✅ Environment file found" -ForegroundColor Green
Write-Host ""

# Display what needs to be deployed
Write-Host "📦 Components to deploy:" -ForegroundColor Cyan
Write-Host "   1. Backend API (FastAPI) → Render/Railway/Fly.io" -ForegroundColor White
Write-Host "   2. Dashboard (Next.js) → Vercel" -ForegroundColor White
Write-Host "   3. Database → MongoDB Atlas (already cloud)" -ForegroundColor White
Write-Host "   4. Redis → Upstash (already cloud)" -ForegroundColor White
Write-Host ""

# Check if required variables are set
Write-Host "🔍 Checking environment variables..." -ForegroundColor Yellow

$envContent = Get-Content ".env" -Raw

if ($envContent -match "xxxxx" -or $envContent -match "mongodb://localhost") {
    Write-Host "❌ MONGODB_URL not configured" -ForegroundColor Red
    Write-Host "   Get from: https://cloud.mongodb.com/" -ForegroundColor Gray
    exit 1
}

if ($envContent -match "API_TOKEN=change-this") {
    Write-Host "❌ API_TOKEN not configured" -ForegroundColor Red
    Write-Host "   Generate with PowerShell:" -ForegroundColor Gray
    Write-Host "   -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 32 | ForEach-Object {[char]`$_})" -ForegroundColor DarkGray
    exit 1
}

if ($envContent -match "SECRET_KEY=change-this") {
    Write-Host "❌ SECRET_KEY not configured" -ForegroundColor Red
    Write-Host "   Generate with PowerShell:" -ForegroundColor Gray
    Write-Host "   -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 64 | ForEach-Object {[char]`$_})" -ForegroundColor DarkGray
    exit 1
}

Write-Host "✅ Environment configured" -ForegroundColor Green
Write-Host ""

Write-Host "🚀 Ready to deploy!" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "   1. Push code to GitHub" -ForegroundColor White
Write-Host "   2. Deploy Backend:" -ForegroundColor White
Write-Host "      → Render: https://render.com/" -ForegroundColor Gray
Write-Host "      → Railway: https://railway.app/" -ForegroundColor Gray
Write-Host "      → Fly.io: flyctl deploy" -ForegroundColor Gray
Write-Host ""
Write-Host "   3. Deploy Dashboard:" -ForegroundColor White
Write-Host "      → Vercel: https://vercel.com/" -ForegroundColor Gray
Write-Host ""
Write-Host "   4. Update CORS_ORIGINS in backend with dashboard URL" -ForegroundColor White
Write-Host ""
Write-Host "   5. Update Android app API_URL with backend URL" -ForegroundColor White
Write-Host ""
Write-Host "See CLOUD_DEPLOYMENT.md for detailed instructions" -ForegroundColor Yellow
Write-Host ""

# Generate example secrets
Write-Host "💡 Quick secret generation:" -ForegroundColor Cyan
Write-Host ""
Write-Host "API_TOKEN (32 chars):" -ForegroundColor Yellow
$apiToken = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 32 | ForEach-Object {[char]$_})
Write-Host $apiToken -ForegroundColor Green
Write-Host ""
Write-Host "SECRET_KEY (64 chars):" -ForegroundColor Yellow
$secretKey = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 64 | ForEach-Object {[char]$_})
Write-Host $secretKey -ForegroundColor Green
Write-Host ""
