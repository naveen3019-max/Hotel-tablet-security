# 🚀 START DEPLOYMENT - Quick Helper Script
# Run this to begin your deployment journey!

Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  🚀 HOTEL TABLET SECURITY - ONLINE DEPLOYMENT" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

Write-Host "Welcome! Let's get your project online! 🌐" -ForegroundColor Green
Write-Host ""

Write-Host "📋 Available Guides:" -ForegroundColor Yellow
Write-Host ""
Write-Host "  1. DEPLOY_CHECKLIST.txt      - Quick checklist (START HERE!)" -ForegroundColor White
Write-Host "  2. DEPLOY_ONLINE.md           - Step-by-step guide" -ForegroundColor White
Write-Host "  3. CLOUD_DEPLOYMENT.md        - Full deployment options" -ForegroundColor White
Write-Host "  4. ANDROID_PRODUCTION_CONFIG  - Android app configuration" -ForegroundColor White
Write-Host "  5. DEPLOYMENT_SUMMARY.md      - What you got" -ForegroundColor White
Write-Host ""

Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# Ask what they want to do
Write-Host "What would you like to do?" -ForegroundColor Yellow
Write-Host ""
Write-Host "  [1] Open quick checklist (recommended)" -ForegroundColor Green
Write-Host "  [2] Open step-by-step guide" -ForegroundColor White
Write-Host "  [3] Check environment setup" -ForegroundColor White
Write-Host "  [4] Generate secrets (API token & Secret key)" -ForegroundColor White
Write-Host "  [5] View all guides" -ForegroundColor White
Write-Host "  [Q] Quit" -ForegroundColor Gray
Write-Host ""

$choice = Read-Host "Enter your choice"

switch ($choice) {
    "1" {
        Write-Host ""
        Write-Host "Opening checklist..." -ForegroundColor Green
        Start-Process notepad "DEPLOY_CHECKLIST.txt"
    }
    "2" {
        Write-Host ""
        Write-Host "Opening step-by-step guide..." -ForegroundColor Green
        Start-Process notepad "DEPLOY_ONLINE.md"
    }
    "3" {
        Write-Host ""
        Write-Host "Checking environment setup..." -ForegroundColor Yellow
        Write-Host ""
        
        if (Test-Path "backend-api\deploy-check.ps1") {
            Set-Location backend-api
            .\deploy-check.ps1
            Set-Location ..
        } else {
            Write-Host "❌ Deploy check script not found!" -ForegroundColor Red
        }
    }
    "4" {
        Write-Host ""
        Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
        Write-Host "  🔒 GENERATING SECURE SECRETS" -ForegroundColor Cyan
        Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
        Write-Host ""
        
        Write-Host "Copy these values to your .env file:" -ForegroundColor Yellow
        Write-Host ""
        
        Write-Host "API_TOKEN=" -ForegroundColor White -NoNewline
        $apiToken = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 32 | ForEach-Object {[char]$_})
        Write-Host $apiToken -ForegroundColor Green
        
        Write-Host ""
        
        Write-Host "SECRET_KEY=" -ForegroundColor White -NoNewline
        $secretKey = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 64 | ForEach-Object {[char]$_})
        Write-Host $secretKey -ForegroundColor Green
        
        Write-Host ""
        Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "✅ Secrets generated!" -ForegroundColor Green
        Write-Host ""
        Write-Host "💡 Tip: Copy these to your backend-api\.env file" -ForegroundColor Yellow
        Write-Host ""
        
        $openEnv = Read-Host "Open .env file to edit? (y/n)"
        if ($openEnv -eq "y") {
            if (-not (Test-Path "backend-api\.env")) {
                Copy-Item "backend-api\.env.production" "backend-api\.env"
                Write-Host "✅ Created .env from template" -ForegroundColor Green
            }
            Start-Process notepad "backend-api\.env"
        }
    }
    "5" {
        Write-Host ""
        Write-Host "Available documentation files:" -ForegroundColor Yellow
        Write-Host ""
        Get-ChildItem -Filter "*DEPLOY*.md" | ForEach-Object {
            Write-Host "  - $($_.Name)" -ForegroundColor White
        }
        Get-ChildItem -Filter "*DEPLOY*.txt" | ForEach-Object {
            Write-Host "  - $($_.Name)" -ForegroundColor White
        }
        Get-ChildItem -Filter "*CLOUD*.md" | ForEach-Object {
            Write-Host "  - $($_.Name)" -ForegroundColor White
        }
        Get-ChildItem -Filter "*ANDROID*PRODUCTION*.md" | ForEach-Object {
            Write-Host "  - $($_.Name)" -ForegroundColor White
        }
        Write-Host ""
    }
    "q" {
        Write-Host ""
        Write-Host "👋 Goodbye! Come back when ready to deploy!" -ForegroundColor Cyan
        Write-Host ""
        exit
    }
    default {
        Write-Host ""
        Write-Host "Invalid choice. Please run again." -ForegroundColor Red
        Write-Host ""
    }
}

Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "📚 Quick Reference:" -ForegroundColor Yellow
Write-Host ""
Write-Host "  Time Required:  ~30 minutes" -ForegroundColor White
Write-Host "  Monthly Cost:   $0 (free tier)" -ForegroundColor White
Write-Host "  Difficulty:     ⭐⭐☆☆☆ (Easy)" -ForegroundColor White
Write-Host ""
Write-Host "  What You Need:" -ForegroundColor Yellow
Write-Host "    • GitHub account (free)" -ForegroundColor White
Write-Host "    • MongoDB Atlas account (free)" -ForegroundColor White
Write-Host "    • Upstash account (free)" -ForegroundColor White
Write-Host "    • Render account (free)" -ForegroundColor White
Write-Host "    • Vercel account (free)" -ForegroundColor White
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "🎯 Next Steps:" -ForegroundColor Green
Write-Host ""
Write-Host "  1. Open DEPLOY_CHECKLIST.txt and follow along" -ForegroundColor White
Write-Host "  2. Set up MongoDB Atlas (5 min)" -ForegroundColor White
Write-Host "  3. Set up Upstash Redis (3 min)" -ForegroundColor White
Write-Host "  4. Deploy to Render (10 min)" -ForegroundColor White
Write-Host "  5. Deploy to Vercel (5 min)" -ForegroundColor White
Write-Host "  6. Update Android app (5 min)" -ForegroundColor White
Write-Host "  7. Test everything! (5 min)" -ForegroundColor White
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "Ready to begin? Run this script again or open:" -ForegroundColor Yellow
Write-Host "  notepad DEPLOY_CHECKLIST.txt" -ForegroundColor Cyan
Write-Host ""
Write-Host "Good luck! 🚀" -ForegroundColor Green
Write-Host ""
