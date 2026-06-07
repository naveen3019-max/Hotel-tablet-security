# 🚀 Push to GitHub - Helper Script

Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  📤 PUSH TO GITHUB" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

Write-Host "✅ Your code is committed and ready to push!" -ForegroundColor Green
Write-Host ""

Write-Host "📋 Next Steps:" -ForegroundColor Yellow
Write-Host ""
Write-Host "1️⃣  CREATE GITHUB REPOSITORY" -ForegroundColor Cyan
Write-Host "   • Go to: https://github.com/new" -ForegroundColor White
Write-Host "   • Repository name: hotel-tablet-security (or your choice)" -ForegroundColor White
Write-Host "   • Description: Enterprise Hotel Tablet Security System" -ForegroundColor White
Write-Host "   • Visibility: Public or Private" -ForegroundColor White
Write-Host "   • ⚠️  DO NOT initialize with README, .gitignore, or license" -ForegroundColor Yellow
Write-Host "   • Click 'Create repository'" -ForegroundColor White
Write-Host ""

Write-Host "2️⃣  COPY THE REPOSITORY URL" -ForegroundColor Cyan
Write-Host "   After creating, GitHub will show commands." -ForegroundColor White
Write-Host "   Copy the HTTPS URL that looks like:" -ForegroundColor White
Write-Host "   https://github.com/YOUR_USERNAME/hotel-tablet-security.git" -ForegroundColor Gray
Write-Host ""

$repoUrl = Read-Host "📎 Paste your GitHub repository URL here"

if ($repoUrl -eq "") {
    Write-Host ""
    Write-Host "❌ No URL provided. Exiting..." -ForegroundColor Red
    Write-Host ""
    Write-Host "Run this script again after creating your GitHub repository." -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

Write-Host ""
Write-Host "3️⃣  CONFIGURING GIT REMOTE..." -ForegroundColor Cyan

try {
    git remote add origin $repoUrl
    Write-Host "   ✅ Remote 'origin' added successfully" -ForegroundColor Green
} catch {
    Write-Host "   ⚠️  Remote already exists, updating..." -ForegroundColor Yellow
    git remote set-url origin $repoUrl
    Write-Host "   ✅ Remote 'origin' updated" -ForegroundColor Green
}

Write-Host ""
Write-Host "4️⃣  PUSHING TO GITHUB..." -ForegroundColor Cyan
Write-Host "   This may take a minute for the first push..." -ForegroundColor Gray
Write-Host ""

try {
    git push -u origin master
    Write-Host ""
    Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host "  🎉 SUCCESS! YOUR PROJECT IS ON GITHUB!" -ForegroundColor Green
    Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host ""
    
    # Extract username and repo from URL
    $repoUrl -match "github\.com[:/]([^/]+)/([^/\.]+)" | Out-Null
    $username = $matches[1]
    $reponame = $matches[2]
    
    Write-Host "📂 Repository: https://github.com/$username/$reponame" -ForegroundColor White
    Write-Host ""
    Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "🎯 What's Next?" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Now you can deploy your project online!" -ForegroundColor White
    Write-Host ""
    Write-Host "  1. Deploy Backend → Render.com" -ForegroundColor White
    Write-Host "     • Go to: https://render.com/" -ForegroundColor Gray
    Write-Host "     • Connect your GitHub repo" -ForegroundColor Gray
    Write-Host "     • See: DEPLOY_ONLINE.md for instructions" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  2. Deploy Dashboard → Vercel.com" -ForegroundColor White
    Write-Host "     • Go to: https://vercel.com/" -ForegroundColor Gray
    Write-Host "     • Import your GitHub repo" -ForegroundColor Gray
    Write-Host "     • See: DEPLOY_ONLINE.md for instructions" -ForegroundColor Gray
    Write-Host ""
    Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "📚 Quick Commands:" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  # Start deployment process" -ForegroundColor White
    Write-Host "  .\start-deployment.ps1" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "  # View deployment checklist" -ForegroundColor White
    Write-Host "  notepad DEPLOY_CHECKLIST.txt" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "  # Make changes and push again" -ForegroundColor White
    Write-Host "  git add ." -ForegroundColor Cyan
    Write-Host "  git commit -m 'Your message'" -ForegroundColor Cyan
    Write-Host "  git push" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Ready to deploy online? Run:" -ForegroundColor Green
    Write-Host "  .\start-deployment.ps1" -ForegroundColor Cyan
    Write-Host ""
    
} catch {
    Write-Host ""
    Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Red
    Write-Host "  ❌ PUSH FAILED" -ForegroundColor Red
    Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Red
    Write-Host ""
    Write-Host "Error: $_" -ForegroundColor Red
    Write-Host ""
    Write-Host "Common Issues:" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "1. Authentication Required:" -ForegroundColor White
    Write-Host "   • You may need to authenticate with GitHub" -ForegroundColor Gray
    Write-Host "   • Use GitHub CLI: gh auth login" -ForegroundColor Gray
    Write-Host "   • Or use Personal Access Token" -ForegroundColor Gray
    Write-Host ""
    Write-Host "2. Repository Doesn't Exist:" -ForegroundColor White
    Write-Host "   • Make sure you created the repository on GitHub" -ForegroundColor Gray
    Write-Host "   • Check the URL is correct" -ForegroundColor Gray
    Write-Host ""
    Write-Host "3. Permission Denied:" -ForegroundColor White
    Write-Host "   • Check you have write access to the repository" -ForegroundColor Gray
    Write-Host "   • Verify your GitHub authentication" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Try again:" -ForegroundColor Yellow
    Write-Host "  git push -u origin master" -ForegroundColor Cyan
    Write-Host ""
}

Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
