# 🚀 Quick Start - Deploy Your Project Online

Follow these steps to get your Hotel Tablet Security System online in ~30 minutes!

---

## ✅ Checklist

- [ ] Step 1: Set up MongoDB Atlas (5 min)
- [ ] Step 2: Set up Upstash Redis (3 min)
- [ ] Step 3: Configure environment variables (2 min)
- [ ] Step 4: Deploy backend to Render (10 min)
- [ ] Step 5: Deploy dashboard to Vercel (5 min)
- [ ] Step 6: Update Android app (5 min)

---

## 🎯 Step 1: MongoDB Atlas (Database)

1. Go to https://www.mongodb.com/cloud/atlas/register
2. Sign up (free)
3. **Create Cluster**:
   - Choose **M0 FREE** tier
   - Select region closest to you
   - Click "Create"
4. **Create User**:
   - Go to "Database Access"
   - Add user: `hotel_admin`
   - Generate strong password → **SAVE IT**
5. **Whitelist IP**:
   - Go to "Network Access"
   - Add IP: `0.0.0.0/0` (allow from anywhere)
6. **Get Connection String**:
   - Click "Connect" on your cluster
   - "Connect your application"
   - Copy: `mongodb+srv://hotel_admin:<password>@cluster0.xxxxx.mongodb.net/`
   - Replace `<password>` with your actual password

✅ **Save your MongoDB URL** - you'll need it in Step 3

---

## 🎯 Step 2: Upstash Redis (Cache)

1. Go to https://upstash.com/
2. Sign up (free)
3. **Create Database**:
   - Click "Create Database"
   - Name: `hotel-redis`
   - Type: Regional
   - Region: Choose closest to you
4. **Copy Redis URL**:
   - Go to database details
   - Copy "REST URL" or "Redis URL"
   - Will look like: `redis://default:xxxxx@xxxxx.upstash.io:6379`

✅ **Save your Redis URL** - you'll need it in Step 3

---

## 🎯 Step 3: Configure Environment

1. **Open PowerShell** in your project folder:
   ```powershell
   cd backend-api
   .\deploy-check.ps1
   ```

2. **Edit `.env` file** (just created):
   ```powershell
   notepad .env
   ```

3. **Fill in these values**:
   - `MONGODB_URL` - From Step 1
   - `REDIS_URL` - From Step 2
   - `API_TOKEN` - Use generated token from script
   - `SECRET_KEY` - Use generated key from script

4. **Save and close**

✅ Environment configured!

---

## 🎯 Step 4: Deploy Backend to Render

1. **Push to GitHub** (if not already):
   ```powershell
   git add .
   git commit -m "Prepare for deployment"
   git push
   ```

2. **Go to Render**: https://render.com/
3. **Sign up** with GitHub account
4. **New Web Service**:
   - Connect your repository
   - Name: `hotel-backend`
   - Root Directory: `backend-api`
   - Environment: `Python 3`
   - Build Command: `pip install -r requirements.txt`
   - Start Command: `uvicorn main:app --host 0.0.0.0 --port $PORT`

5. **Add Environment Variables**:
   Click "Advanced" → Add from your `.env` file:
   - `MONGODB_URL` = (your MongoDB URL)
   - `DATABASE_NAME` = `hotel_security`
   - `REDIS_URL` = (your Redis URL)
   - `API_TOKEN` = (your generated token)
   - `SECRET_KEY` = (your generated key)
   - `APP_ENV` = `production`
   - `DEBUG` = `false`
   - `CORS_ORIGINS` = `http://localhost:3000` (update later)

6. **Click "Create Web Service"**
7. **Wait 3-5 minutes** for deployment
8. **Copy your backend URL**: `https://hotel-backend.onrender.com`

✅ Backend online! Test it: `https://hotel-backend.onrender.com/docs`

---

## 🎯 Step 5: Deploy Dashboard to Vercel

1. **Go to Vercel**: https://vercel.com/
2. **Sign up** with GitHub account
3. **Import Repository**:
   - Click "Add New" → "Project"
   - Select your repository
   - Root Directory: `dashboard`
   - Framework: Next.js (auto-detected)

4. **Add Environment Variable**:
   - Key: `NEXT_PUBLIC_API_URL`
   - Value: `https://hotel-backend.onrender.com` (your backend URL)

5. **Click "Deploy"**
6. **Wait 2-3 minutes**
7. **Copy your dashboard URL**: `https://hotel-dashboard.vercel.app`

✅ Dashboard online! Open it in browser

---

## 🎯 Step 6: Update Backend CORS

Now that you have your dashboard URL, update backend CORS:

1. **Go back to Render dashboard**
2. **Select your backend service**
3. **Environment**
4. **Edit `CORS_ORIGINS`**:
   - Change to: `https://hotel-dashboard.vercel.app`
5. **Save** (will auto-redeploy)

✅ CORS configured!

---

## 🎯 Step 7: Update Android App

1. **Find API configuration file**:
   ```powershell
   # Search for the file
   Get-ChildItem -Path android-agent -Recurse -Filter "*.kt" | Select-String "localhost:8080" -List
   ```

2. **Edit the file** (usually in `app/src/main/java/.../config/` or similar):
   - Change `http://localhost:8080` 
   - To: `https://hotel-backend.onrender.com`
   - Change `API_TOKEN` to match your backend

3. **Example**:
   ```kotlin
   // Before
   const val BASE_URL = "http://10.0.2.2:8080"
   const val API_TOKEN = "dev-token"
   
   // After
   const val BASE_URL = "https://hotel-backend.onrender.com"
   const val API_TOKEN = "your-actual-api-token-from-env"
   ```

4. **Rebuild APK**:
   ```powershell
   cd android-agent
   .\gradlew assembleRelease
   ```

5. **Find APK**: `app/build/outputs/apk/release/app-release.apk`

✅ Android app configured!

---

## 🎉 Done! Your Project is Online!

### Access Your Services:

- **Backend API**: https://hotel-backend.onrender.com/docs
- **Dashboard**: https://hotel-dashboard.vercel.app
- **Android APK**: `android-agent/app/build/outputs/apk/release/app-release.apk`

### Test Everything:

1. **Open Dashboard** in browser
2. **Login** (or create admin account)
3. **Install APK** on Android device
4. **Register device** in app
5. **See it appear** in dashboard!

---

## 📱 Share Your App

### APK Distribution:

- **Email**: Send `app-release.apk` to users
- **Google Drive**: Upload and share link
- **Firebase App Distribution**: For beta testing
- **Play Store**: For public release (requires Google Play Console)

### Dashboard Access:

- Share: `https://hotel-dashboard.vercel.app`
- Create accounts for your team
- Set up admin users

---

## 🔧 Troubleshooting

### Backend sleeping (Render free tier)?
- Free tier sleeps after 15 min of inactivity
- First request will be slow (~30 seconds)
- Upgrade to $7/month for always-on

### Android app can't connect?
- Check if backend URL is correct
- Must include `https://` (no trailing slash)
- Verify API_TOKEN matches backend

### Dashboard shows errors?
- Check browser console (F12)
- Verify API URL in Vercel environment variables
- Check CORS_ORIGINS in backend includes dashboard URL

### Database connection failed?
- Check MongoDB Atlas IP whitelist includes `0.0.0.0/0`
- Verify username/password in connection string
- Check if cluster is running (not paused)

---

## 💰 Costs

| Service | Free Tier | Paid Tier |
|---------|-----------|-----------|
| Render | FREE (sleeps) | $7/mo (always-on) |
| Vercel | FREE (unlimited) | $20/mo (team) |
| MongoDB Atlas | FREE (512MB) | $9/mo (2GB) |
| Upstash Redis | FREE (10K/day) | $10/mo (100K/day) |
| **TOTAL** | **$0/month** | **~$50/month** |

---

## 📚 Next Steps

1. ✅ Set up email alerts → See [EMAIL_SETUP.md](EMAIL_SETUP.md)
2. ✅ Set up Slack notifications → See [SLACK_SETUP.md](SLACK_SETUP.md)
3. ✅ Custom domain → Configure in Render/Vercel
4. ✅ SSL certificate → Auto-enabled by Render/Vercel
5. ✅ Monitoring → Set up UptimeRobot

---

## 🆘 Need Help?

- **Full Guide**: See [CLOUD_DEPLOYMENT.md](CLOUD_DEPLOYMENT.md)
- **MongoDB**: See [MONGODB_ATLAS_SETUP.md](MONGODB_ATLAS_SETUP.md)
- **Issues**: Check troubleshooting section above

---

**Congratulations! Your hotel tablet security system is now online!** 🎉
