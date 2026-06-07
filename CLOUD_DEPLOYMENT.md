# 🚀 Cloud Deployment Guide - Hotel Tablet Security System

This guide will help you deploy your project online using free/affordable cloud platforms.

---

## 📋 Overview

Your system has 3 components:
1. **Backend API** (FastAPI + Python) → Deploy to Render/Railway/Fly.io
2. **Dashboard** (Next.js) → Deploy to Vercel
3. **Database** (MongoDB) → Already using MongoDB Atlas (cloud)
4. **Redis** (Cache) → Use Upstash Redis (free tier)
5. **Android App** → Update API URL to point to deployed backend

---

## ⚡ Quick Start - Free Tier Deployment

### Option 1: Render (Recommended - Easiest)
### Option 2: Railway (Developer-friendly)
### Option 3: Fly.io (More control)

---

## 🎯 Step-by-Step: Deploy with Render (FREE)

### Phase 1: Prepare MongoDB Atlas

1. **Go to MongoDB Atlas**: https://www.mongodb.com/cloud/atlas/register
2. **Create free cluster** (M0 - 512MB)
3. **Create database user**:
   - Username: `hotel_admin`
   - Password: (generate strong password)
4. **Whitelist IP**: Add `0.0.0.0/0` (allow from anywhere)
5. **Get connection string**:
   - Click "Connect" → "Connect your application"
   - Copy: `mongodb+srv://hotel_admin:<password>@cluster0.xxxxx.mongodb.net/`
   - Replace `<password>` with your actual password

### Phase 2: Set Up Redis (FREE)

1. **Go to Upstash**: https://upstash.com/
2. **Sign up** (free)
3. **Create Redis Database**:
   - Name: `hotel-redis`
   - Type: Regional
   - Region: Choose closest to your location
4. **Copy Redis URL**: Will look like `redis://default:xxxxx@xxxxx.upstash.io:6379`

### Phase 3: Deploy Backend API to Render

1. **Go to Render**: https://render.com/
2. **Sign up** with GitHub account
3. **New Web Service**:
   - Connect your GitHub repository
   - Name: `hotel-backend`
   - Region: Choose closest to you
   - Branch: `main` (or your default branch)
   - Root Directory: `backend-api`
   - Runtime: `Python 3`
   - Build Command: `pip install -r requirements.txt`
   - Start Command: `uvicorn main:app --host 0.0.0.0 --port $PORT`

4. **Environment Variables** (Add in Render dashboard):
   ```
   MONGODB_URL=mongodb+srv://hotel_admin:YOUR_PASSWORD@cluster0.xxxxx.mongodb.net/
   DATABASE_NAME=hotel_security
   REDIS_URL=redis://default:xxxxx@xxxxx.upstash.io:6379
   API_TOKEN=your-secure-api-token-here-change-this
   SECRET_KEY=your-super-secret-key-min-32-chars-change-this
   APP_ENV=production
   DEBUG=false
   CORS_ORIGINS=https://your-dashboard-url.vercel.app,http://localhost:3000
   SMTP_ENABLED=false
   SLACK_ENABLED=false
   ```

5. **Deploy**: Click "Create Web Service"
6. **Wait 3-5 minutes** for deployment
7. **Copy Backend URL**: Will be like `https://hotel-backend.onrender.com`

### Phase 4: Deploy Dashboard to Vercel (FREE)

1. **Go to Vercel**: https://vercel.com/
2. **Sign up** with GitHub account
3. **Import Git Repository**:
   - Select your repository
   - Framework Preset: `Next.js`
   - Root Directory: `dashboard`
   
4. **Environment Variables**:
   ```
   NEXT_PUBLIC_API_URL=https://hotel-backend.onrender.com
   ```

5. **Deploy**: Click "Deploy"
6. **Wait 2-3 minutes**
7. **Copy Dashboard URL**: Will be like `https://hotel-dashboard.vercel.app`

### Phase 5: Update CORS Settings

1. **Go back to Render dashboard**
2. **Update CORS_ORIGINS** environment variable:
   ```
   CORS_ORIGINS=https://hotel-dashboard.vercel.app
   ```
3. **Save & Redeploy**

### Phase 6: Configure Android App

Update the API URL in Android app to point to your deployed backend.

---

## 🔧 Alternative: Deploy with Railway (FREE $5 credit)

### Backend Deployment

1. **Go to Railway**: https://railway.app/
2. **Sign up** with GitHub
3. **New Project** → **Deploy from GitHub repo**
4. **Select your repository**
5. **Add Service** → **GitHub Repo** → Select `backend-api` directory
6. **Add variables** (same as Render above)
7. **Railway will auto-deploy** using Dockerfile
8. **Generate domain** in Settings

### Dashboard Deployment on Railway

1. **Same project** → **New Service**
2. **Select GitHub repo** → `dashboard` directory
3. **Add variables**:
   ```
   NEXT_PUBLIC_API_URL=https://your-backend.railway.app
   ```
4. **Generate domain**

---

## 🌐 Alternative: Deploy with Fly.io (More Control)

### Install Fly CLI

```powershell
# Windows
powershell -Command "iwr https://fly.io/install.ps1 -useb | iex"

# Or use chocolatey
choco install flyctl
```

### Deploy Backend

```powershell
cd backend-api

# Login to Fly.io
fly auth login

# Launch app
fly launch --name hotel-backend --region iad --no-deploy

# Set secrets (environment variables)
fly secrets set MONGODB_URL="mongodb+srv://..."
fly secrets set DATABASE_NAME="hotel_security"
fly secrets set REDIS_URL="redis://..."
fly secrets set API_TOKEN="your-token"
fly secrets set SECRET_KEY="your-secret"
fly secrets set APP_ENV="production"
fly secrets set DEBUG="false"

# Deploy
fly deploy

# Get URL
fly status
```

### Deploy Dashboard

```powershell
cd ../dashboard

fly launch --name hotel-dashboard --region iad --no-deploy
fly secrets set NEXT_PUBLIC_API_URL="https://hotel-backend.fly.dev"
fly deploy
fly status
```

---

## 📱 Update Android App Configuration

After deploying backend, update the Android app:

### Edit: `android-agent/app/src/main/java/com/hotel/security/config/ApiConfig.kt`

```kotlin
object ApiConfig {
    // Change this to your deployed backend URL
    const val BASE_URL = "https://hotel-backend.onrender.com"  // or your Railway/Fly.io URL
    
    // Keep other settings
    const val API_TOKEN = "your-secure-api-token-here-change-this"  // Same as backend
}
```

### Rebuild Android App

```powershell
cd android-agent
.\gradlew assembleRelease
```

The APK will be in: `app/build/outputs/apk/release/app-release.apk`

---

## 🔒 Security Best Practices

### Generate Strong Secrets

```powershell
# Generate API Token (32 characters)
-join ((48..57) + (65..90) + (97..122) | Get-Random -Count 32 | ForEach-Object {[char]$_})

# Generate Secret Key (64 characters)
-join ((48..57) + (65..90) + (97..122) | Get-Random -Count 64 | ForEach-Object {[char]$_})
```

### Environment Variables Checklist

- [ ] `MONGODB_URL` - MongoDB Atlas connection string
- [ ] `DATABASE_NAME` - Database name (hotel_security)
- [ ] `REDIS_URL` - Upstash Redis URL
- [ ] `API_TOKEN` - Strong random token (32+ chars)
- [ ] `SECRET_KEY` - Strong secret key (64+ chars)
- [ ] `APP_ENV` - Set to "production"
- [ ] `DEBUG` - Set to "false"
- [ ] `CORS_ORIGINS` - Your dashboard domain only

### Lock Down CORS

After deployment, update CORS to only allow your dashboard domain:

```
CORS_ORIGINS=https://hotel-dashboard.vercel.app
```

---

## 🧪 Test Your Deployment

### 1. Test Backend API

```powershell
# Health check
curl https://hotel-backend.onrender.com/health

# API docs
# Open in browser: https://hotel-backend.onrender.com/docs
```

### 2. Test Dashboard

Open your dashboard URL: https://hotel-dashboard.vercel.app

### 3. Test Android App

1. Install the updated APK on a test device
2. Register a device
3. Verify it appears in the dashboard

---

## 📊 Cost Breakdown (Free Tier)

| Service | Platform | Cost |
|---------|----------|------|
| Backend API | Render | **FREE** (with sleep after 15 min idle) |
| Dashboard | Vercel | **FREE** (unlimited sites) |
| Database | MongoDB Atlas | **FREE** (512MB M0 cluster) |
| Redis Cache | Upstash Redis | **FREE** (10K commands/day) |
| **TOTAL** | | **$0/month** |

### Upgrade Options (if needed)

- **Render**: $7/month (no sleep)
- **Railway**: $5/month credit (pay for usage)
- **Fly.io**: $5-10/month (2 VMs)
- **MongoDB**: $9/month (M2 cluster, 2GB)

---

## 🔄 Continuous Deployment (Auto-Deploy)

### Render & Vercel
- **Automatically deploy** when you push to GitHub
- No configuration needed!

### Railway
- Automatically deploys on push
- Can configure deploy branch in settings

### Fly.io
- Set up GitHub Actions:

```yaml
# .github/workflows/deploy.yml
name: Deploy to Fly.io
on:
  push:
    branches: [main]
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: superfly/flyctl-actions/setup-flyctl@master
      - run: flyctl deploy --remote-only
        env:
          FLY_API_TOKEN: ${{ secrets.FLY_API_TOKEN }}
```

---

## 📞 Support & Troubleshooting

### Common Issues

**1. Backend sleeping (Render free tier)**
- Free tier sleeps after 15 minutes of inactivity
- First request will be slow (wakes up)
- Upgrade to $7/month for always-on

**2. CORS errors in dashboard**
- Check `CORS_ORIGINS` includes your dashboard URL
- Must include protocol: `https://...`

**3. Android app can't connect**
- Verify `BASE_URL` in Android app
- Must include `https://` (no trailing slash)
- Verify API_TOKEN matches backend

**4. Database connection failed**
- Check MongoDB Atlas IP whitelist includes `0.0.0.0/0`
- Verify username/password in connection string
- Test connection string with MongoDB Compass

**5. Redis connection failed**
- Verify Upstash Redis URL is correct
- Check if URL includes `rediss://` (SSL) or `redis://`

---

## 🎉 Next Steps

1. ✅ Deploy backend to Render
2. ✅ Deploy dashboard to Vercel
3. ✅ Update Android app with production URL
4. ✅ Test end-to-end functionality
5. 📧 Configure email alerts (optional)
6. 💬 Configure Slack notifications (optional)
7. 📊 Set up monitoring (UptimeRobot, Better Uptime)

---

## 🔗 Quick Links

- **MongoDB Atlas**: https://cloud.mongodb.com/
- **Upstash Redis**: https://console.upstash.com/
- **Render**: https://dashboard.render.com/
- **Vercel**: https://vercel.com/dashboard
- **Railway**: https://railway.app/dashboard
- **Fly.io**: https://fly.io/dashboard

---

**Need help?** Open an issue on GitHub or contact support.

**Ready to go live?** Follow the steps above and your app will be online in ~30 minutes! 🚀
