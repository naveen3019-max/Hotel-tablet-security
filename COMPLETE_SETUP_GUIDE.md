# Complete Setup Guide - Tablet, Dashboard & Notifications

This guide walks you through setting up the complete Hotel Tablet Security System.

---

## Part 1: Backend Setup (MongoDB Atlas)

### 1.1 Create MongoDB Atlas Account

1. Go to https://www.mongodb.com/cloud/atlas/register
2. Sign up (free tier available)
3. Create a **M0 Free Cluster**
4. **Database Access** → Add user: `hotel_admin` with password
5. **Network Access** → Add IP: `0.0.0.0/0` (allow from anywhere)
6. Get connection string from **Connect** button

### 1.2 Configure Backend

Create `backend-api/.env`:
```env
# MongoDB Atlas
MONGODB_URL=mongodb+srv://hotel_admin:YOUR_PASSWORD@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority
DATABASE_NAME=hotel_security

# JWT Secret (generate random 32+ character string)
SECRET_KEY=your-super-secret-jwt-key-change-this-to-random-string-min-32-chars

# Email Notifications (Gmail)
SMTP_ENABLED=true
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-hotel-email@gmail.com
SMTP_PASSWORD=your-gmail-app-password
SMTP_FROM_EMAIL=security@yourhotel.com
ALERT_EMAIL_RECIPIENTS=owner@yourhotel.com,manager@yourhotel.com

# Slack Notifications (optional)
SLACK_ENABLED=true
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/YOUR/WEBHOOK/URL

# Redis (for real-time updates)
REDIS_URL=redis://localhost:6379/0

# CORS (add your domain)
CORS_ORIGINS=http://localhost:3000,https://yourdomain.com
```

### 1.3 Start Backend

**Option A: Local Development**
```powershell
cd backend-api
pip install -r requirements.txt
python -m uvicorn main:app --reload --port 8080
```

**Option B: Docker (Recommended)**
```powershell
docker-compose up -d
```

### 1.4 Verify Backend is Running
```powershell
curl http://localhost:8080/health
```

Expected response:
```json
{
  "status": "healthy",
  "database": "connected",
  "timestamp": "2025-12-21T12:00:00"
}
```

---

## Part 2: Email Notifications Setup

### 2.1 Gmail App Password (Most Common)

1. **Enable 2-Factor Authentication** in your Google Account
   - Go to: https://myaccount.google.com/security
   - Enable 2-Step Verification

2. **Create App Password**
   - Go to: https://myaccount.google.com/apppasswords
   - Select app: "Mail"
   - Select device: "Other" → Type: "Hotel Security"
   - Click **Generate**
   - Copy the 16-character password (e.g., `abcd efgh ijkl mnop`)

3. **Update `.env` file:**
```env
SMTP_ENABLED=true
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=yourhotel@gmail.com
SMTP_PASSWORD=abcdefghijklmnop  # No spaces
SMTP_FROM_EMAIL=security@yourhotel.com
ALERT_EMAIL_RECIPIENTS=owner@email.com,manager@email.com
```

### 2.2 Alternative Email Providers

**Outlook/Hotmail:**
```env
SMTP_HOST=smtp-mail.outlook.com
SMTP_PORT=587
SMTP_USERNAME=yourhotel@outlook.com
SMTP_PASSWORD=your-outlook-password
```

**Custom SMTP Server:**
```env
SMTP_HOST=mail.yourhotel.com
SMTP_PORT=587
SMTP_USERNAME=security@yourhotel.com
SMTP_PASSWORD=your-smtp-password
```

### 2.3 Test Email Notifications

```powershell
# Restart backend to load new .env
docker-compose restart backend

# Or if running locally:
# Ctrl+C and restart uvicorn
```

Send a test alert:
```powershell
# Register a test device first
$response = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/devices/register" `
  -ContentType "application/json" `
  -Body '{"deviceId":"TEST-001","roomId":"101"}'

$token = $response.token

# Trigger breach alert (sends email)
Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/alert/breach" `
  -Headers @{Authorization="Bearer $token"} `
  -ContentType "application/json" `
  -Body '{"deviceId":"TEST-001","roomId":"101","rssi":-95}'
```

Check your email - you should receive a breach alert!

---

## Part 3: Slack Notifications Setup (Optional)

### 3.1 Create Slack Webhook

1. **Go to Slack API:** https://api.slack.com/apps
2. Click **"Create New App"** → **"From scratch"**
3. **App Name:** "Hotel Security Bot"
4. **Workspace:** Select your workspace
5. Click **"Create App"**

### 3.2 Enable Incoming Webhooks

1. In your app settings, click **"Incoming Webhooks"**
2. Toggle **"Activate Incoming Webhooks"** to ON
3. Click **"Add New Webhook to Workspace"**
4. Select channel (e.g., `#security-alerts`)
5. Click **"Allow"**
6. **Copy the Webhook URL** (looks like: `https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXX`)

### 3.3 Configure Backend

Update `.env`:
```env
SLACK_ENABLED=true
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXX
```

Restart backend:
```powershell
docker-compose restart backend
```

### 3.4 Test Slack Notifications

Send another test breach alert (same command as email test above). You should see:
- ✅ Email notification sent
- ✅ Slack message in your channel

---

## Part 4: Dashboard Setup (Admin Panel)

### 4.1 Install Dependencies

```powershell
cd dashboard
npm install
```

### 4.2 Configure Dashboard

Create `dashboard/.env.local`:
```env
NEXT_PUBLIC_API=http://localhost:8080
```

For production (your server IP):
```env
NEXT_PUBLIC_API=http://192.168.1.100:8080
```

### 4.3 Start Dashboard

**Development:**
```powershell
cd dashboard
npm run dev
```

**Production:**
```powershell
npm run build
npm start
```

**Access dashboard:**
- Local: http://localhost:3000
- Network: http://YOUR_SERVER_IP:3000

### 4.4 Dashboard Features

**Main Page (`/`):**
- Real-time device status grid
- Live alerts feed
- Breach/battery/offline status
- Alert acknowledgment buttons

**Pages available:**
1. **Device List** - All registered tablets
2. **Alert History** - Past security events
3. **Room Configuration** - WiFi settings per room
4. **System Metrics** - Performance stats

---

## Part 5: Android Tablet Setup (Kiosk Mode)

### 5.1 Build Android App

**Prerequisites:**
- Android Studio installed
- Java 17+ installed

**Build APK:**
```powershell
cd android-agent

# Windows
.\gradlew.bat assembleRelease

# Linux/Mac
./gradlew assembleRelease
```

APK location:
```
android-agent/app/build/outputs/apk/release/app-release.apk
```

### 5.2 Install on Tablet

**Method 1: USB Transfer**
1. Enable **Developer Options** on tablet:
   - Settings → About → Tap "Build Number" 7 times
2. Enable **USB Debugging**:
   - Settings → Developer Options → USB Debugging
3. Connect tablet to PC via USB
4. Copy APK to tablet:
```powershell
adb install android-agent/app/build/outputs/apk/release/app-release.apk
```

**Method 2: Download Link**
1. Upload APK to cloud (Google Drive, Dropbox)
2. Share download link
3. Open link on tablet → Download → Install
4. Enable "Install from Unknown Sources" if prompted

### 5.3 Configure Tablet

1. **Open the Hotel Security app** on tablet
2. **First-time setup wizard:**

**Step 1: Server Configuration**
```
API Server URL: http://YOUR_SERVER_IP:8080
Device ID: TAB-101
Room ID: 101
Hotel ID: hotel-1
```

**Step 2: Admin PIN**
```
Set admin PIN: 832504
Confirm PIN: 832504
```

**Step 3: WiFi Calibration**
- App will scan for WiFi networks
- Select your hotel WiFi network
- App shows current RSSI (signal strength)
- Set minimum RSSI threshold: `-70 dBm` (recommended)
- Walk to room door to test signal strength
- Adjust threshold if needed

**Step 4: Permissions**
- Grant all requested permissions:
  - ✅ Location (for WiFi scanning)
  - ✅ Network (for API calls)
  - ✅ Foreground service (for kiosk mode)
  - ✅ System alert window (for lock screen)

3. **Tap "Start Protection"**

### 5.4 Enable Kiosk Mode (Lock Tablet)

**Method 1: App's Built-in Kiosk**
- App automatically locks screen
- Prevents home button, back button, notifications
- Shows full-screen security UI
- Exit only with admin PIN (832504)

**Method 2: Android Kiosk Mode (Stronger)**

1. **Enable Device Owner Mode:**
```powershell
# Connect tablet via USB
adb shell dpm set-device-owner com.hotel.security/.DeviceAdminReceiver
```

2. **Or use Manual Kiosk:**
   - Settings → Security → Screen Pinning
   - Enable "Ask for PIN before unpinning"
   - Open Hotel Security app
   - Tap Recent Apps → Pin icon

**Method 3: MDM Solution (Enterprise)**
- Use MDM like Intune, AirWatch, or Hexnode
- Configure app as single-app kiosk
- Remotely manage all tablets

### 5.5 Test Tablet

1. **Normal Mode:**
   - Tablet shows "Room 101 - Protected" screen
   - Green checkmark icon
   - Battery level displayed
   - Signal strength displayed

2. **Trigger Breach (Test):**
   - Walk tablet away from room (take to hallway)
   - Wait 10 seconds
   - Screen turns RED
   - Alert sent to owner's email/Slack
   - Dashboard shows breach alert

3. **Exit Admin Mode:**
   - Long-press the lock icon
   - Enter PIN: 832504
   - Access settings/calibration

---

## Part 6: Complete System Test

### 6.1 Register Multiple Devices

```powershell
# Register Room 101 tablet
Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/devices/register" `
  -ContentType "application/json" `
  -Body '{"deviceId":"TAB-101","roomId":"101","hotelId":"hotel-1"}'

# Register Room 102 tablet
Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/devices/register" `
  -ContentType "application/json" `
  -Body '{"deviceId":"TAB-102","roomId":"102","hotelId":"hotel-1"}'

# Register Room 201 tablet
Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/devices/register" `
  -ContentType "application/json" `
  -Body '{"deviceId":"TAB-201","roomId":"201","hotelId":"hotel-1"}'
```

### 6.2 View All Devices in Dashboard

1. Open dashboard: http://localhost:3000
2. See all 3 devices in grid view
3. Green = Protected, Red = Breach, Gray = Offline

### 6.3 Test Notifications

**Scenario 1: Tablet Theft**
1. Take tablet out of room
2. Wait 10 seconds
3. **Expected results:**
   - ✅ Email sent to owner
   - ✅ Slack message posted
   - ✅ Dashboard shows red alert
   - ✅ Tablet screen turns red

**Scenario 2: Low Battery**
1. Let tablet battery drain to 15%
2. **Expected results:**
   - ✅ Email sent to maintenance
   - ✅ Dashboard shows battery warning

**Scenario 3: Offline Detection**
1. Disable WiFi on tablet
2. Wait 5 minutes
3. **Expected results:**
   - ✅ Dashboard shows "Offline" status
   - ✅ Email sent after 5 minutes

### 6.4 Test Alert Acknowledgment

1. Dashboard shows breach alert
2. Click **"Acknowledge"** button
3. Add notes: "False alarm - housekeeping"
4. Alert marked as resolved
5. Checkmark appears on alert

---

## Part 7: Production Deployment

### 7.1 Server Setup

**Recommended specs:**
- Ubuntu 22.04 LTS
- 2 CPU cores, 4GB RAM
- 20GB storage
- Static IP address

**Install Docker:**
```bash
sudo apt update
sudo apt install docker.io docker-compose -y
sudo systemctl enable docker
```

### 7.2 Deploy to Server

1. **Copy project to server:**
```powershell
scp -r hotel-tablet-security/ user@server_ip:/home/user/
```

2. **Configure production `.env`:**
```bash
cd /home/user/hotel-tablet-security/backend-api
nano .env
```

Update:
```env
APP_ENV=production
DEBUG=false
CORS_ORIGINS=http://YOUR_SERVER_IP:3000
```

3. **Start services:**
```bash
docker-compose up -d
```

4. **Enable firewall:**
```bash
sudo ufw allow 8080/tcp
sudo ufw allow 3000/tcp
sudo ufw enable
```

### 7.3 Access from Tablets

Update tablet configuration:
```
API Server URL: http://SERVER_IP:8080
```

Or use domain name:
```
API Server URL: https://security.yourhotel.com
```

### 7.4 SSL/HTTPS Setup (Optional but Recommended)

1. **Get domain name:** `security.yourhotel.com`
2. **Install Certbot:**
```bash
sudo apt install certbot python3-certbot-nginx -y
```

3. **Get SSL certificate:**
```bash
sudo certbot --nginx -d security.yourhotel.com
```

4. **Update tablet config:**
```
API Server URL: https://security.yourhotel.com
```

---

## Part 8: Troubleshooting

### Backend not starting
```powershell
# Check logs
docker-compose logs backend

# Common issues:
# - Wrong MongoDB URL → Check connection string
# - Redis not running → Start Redis: docker-compose up -d redis
```

### Email not sending
```powershell
# Test SMTP connection
python -c "import smtplib; smtplib.SMTP('smtp.gmail.com', 587).starttls(); print('OK')"

# Common issues:
# - App password not created → Create in Google account
# - 2FA not enabled → Enable 2-Step Verification
# - Firewall blocking port 587 → Check firewall
```

### Tablet not connecting
```
# Common issues:
# - Wrong server IP → Check with: ipconfig (Windows) or ifconfig (Linux)
# - Firewall blocking → Allow port 8080
# - Tablet not on same network → Connect to hotel WiFi
```

### Dashboard not showing devices
```
# Check API connection
curl http://localhost:8080/api/devices

# Check CORS settings in .env
CORS_ORIGINS=http://localhost:3000
```

### Breach alerts too sensitive
```
# Adjust RSSI threshold in tablet:
# 1. Enter admin PIN (832504)
# 2. Go to Calibration
# 3. Set higher threshold (e.g., -75 instead of -70)
# 4. Test by walking to door
```

---

## Part 9: Maintenance

### Daily Tasks
- Check dashboard for any offline devices
- Review alert history
- Acknowledge false alarms

### Weekly Tasks
- Check tablet battery levels (should be >20%)
- Verify email notifications working
- Review system metrics in Grafana (http://localhost:3001)

### Monthly Tasks
- Update tablet app if new version available
- Backup MongoDB Atlas (automatic in Atlas)
- Review and archive old alerts (90+ days)
- Test disaster recovery plan

---

## Part 10: Quick Reference

### Admin PIN
```
Default PIN: 832504
Change in tablet settings
```

### API Endpoints
```
Health: GET http://localhost:8080/health
Devices: GET http://localhost:8080/api/devices
Alerts: GET http://localhost:8080/api/alerts/recent
Docs: http://localhost:8080/docs
```

### Service URLs
```
Backend:    http://localhost:8080
Dashboard:  http://localhost:3000
Flower:     http://localhost:5555 (Celery monitoring)
Prometheus: http://localhost:9090 (Metrics)
Grafana:    http://localhost:3001 (Visualizations)
```

### Common Commands
```powershell
# Start services
docker-compose up -d

# Stop services
docker-compose down

# View logs
docker-compose logs -f backend

# Restart backend
docker-compose restart backend

# Check service status
docker-compose ps
```

---

## Support & Next Steps

✅ **Setup Complete!** You now have:
- MongoDB Atlas cloud database
- Email & Slack notifications
- Admin dashboard with real-time updates
- Android tablets in kiosk mode
- Complete security monitoring system

**Need Help?**
- Check logs: `docker-compose logs -f`
- API documentation: http://localhost:8080/docs
- MongoDB Atlas dashboard: https://cloud.mongodb.com/

**Scaling Up?**
- Add more tablets: Just install app and register
- Multiple hotels: Use different `hotelId` in registration
- Advanced analytics: Use Grafana dashboards

---

**System is ready for production! 🚀**
