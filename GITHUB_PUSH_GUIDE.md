# 📤 How to Push Your Project to GitHub

## Quick Steps

### 1. Create GitHub Repository

1. **Go to GitHub**: https://github.com/new
2. **Repository name**: `hotel-tablet-security` (or your choice)
3. **Description**: `Enterprise Hotel Tablet Security System with WiFi Geofencing`
4. **Visibility**: Choose Public or Private
5. **⚠️ IMPORTANT**: Do NOT check any of these:
   - ❌ Add a README file
   - ❌ Add .gitignore
   - ❌ Choose a license
6. **Click**: "Create repository"

### 2. Run the Push Script

```powershell
.\push-to-github.ps1
```

The script will:
- Ask for your repository URL
- Configure the remote
- Push all your code to GitHub

### 3. Done!

Your code will be on GitHub and ready for deployment!

---

## Manual Method (Alternative)

If you prefer to do it manually:

### Step 1: Create Repository on GitHub
Follow the steps above.

### Step 2: Copy the Repository URL
After creating, GitHub shows commands. Copy the HTTPS URL:
```
https://github.com/YOUR_USERNAME/hotel-tablet-security.git
```

### Step 3: Add Remote and Push

```powershell
# Add remote
git remote add origin https://github.com/YOUR_USERNAME/hotel-tablet-security.git

# Push code
git push -u origin master
```

---

## Authentication

If prompted for credentials:

### Option 1: GitHub CLI (Recommended)
```powershell
# Install GitHub CLI
winget install GitHub.cli

# Authenticate
gh auth login
```

### Option 2: Personal Access Token
1. Go to: https://github.com/settings/tokens
2. Generate new token (classic)
3. Select scopes: `repo` (all)
4. Copy token
5. Use token as password when pushing

### Option 3: Git Credential Manager
Windows should prompt you to sign in via browser.

---

## Troubleshooting

### "fatal: remote origin already exists"
```powershell
git remote remove origin
git remote add origin YOUR_REPO_URL
```

### "Authentication failed"
- Use GitHub CLI: `gh auth login`
- Or create Personal Access Token

### "Permission denied"
- Check you're logged into correct GitHub account
- Verify repository exists and you have access

---

## What's Next?

After pushing to GitHub:

1. **Deploy Backend** → Render.com
   - See: [DEPLOY_ONLINE.md](DEPLOY_ONLINE.md)

2. **Deploy Dashboard** → Vercel.com
   - See: [DEPLOY_ONLINE.md](DEPLOY_ONLINE.md)

3. **Enable Auto-Deploy**
   - Both platforms auto-deploy on git push!

---

## Quick Reference

```powershell
# Check status
git status

# Add changes
git add .

# Commit
git commit -m "Your message"

# Push
git push

# Check remote
git remote -v
```

---

**Need help?** Run: `.\push-to-github.ps1`
