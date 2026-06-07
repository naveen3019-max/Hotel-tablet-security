# Hotel Tablet Security

Enterprise-grade security system for hotel tablets with WiFi geofencing, battery monitoring, and real-time alerts.

## Features

### 🔒 Security & Kiosk Mode
- **WiFi Geofencing**: RSSI-based room boundary detection
- **True Kiosk Mode**: Foreground service with LOCK_TASK_MODE
- **Password Protection**: Admin-only access to WiFi/Power settings
- **Tamper Detection**: Root detection, debugger checks
- **Lock Screen**: Full-screen breach alert with hidden admin access (tap 5 times)

### 📱 Android Agent
- **RSSI Calibration Tool**: Real-time WiFi signal strength viewer
- **Device Provisioning**: First-time setup wizard with QR code support
- **Battery Monitoring**: Low battery alerts (configurable threshold)
- **Offline Queue**: Local SQLite queue for network outages
- **Heartbeat System**: 10-second status updates to backend

### 🖥️ Backend API (FastAPI)
- **RESTful Endpoints**: Breach, battery, heartbeat, config management
- **PostgreSQL Database**: Production-ready with Alembic migrations
- **Redis Caching**: Device status and alert caching
- **Environment Config**: 12-factor app with `.env` support
- **Health & Metrics**: `/health` and `/metrics` endpoints for monitoring

### 📊 Dashboard (Next.js)
- **Fleet Management**: Real-time device status, battery, RSSI
- **Alert Dashboard**: Recent alerts with filtering and search
- **Device CRUD**: Assign/reassign rooms, decommission devices
- **Real-time Updates**: Server-Sent Events (SSE) for live data
- **Data Visualization**: Charts and heatmaps for RSSI calibration

### 🔔 Notifications
- **Email Alerts**: SMTP integration for breach/battery notifications
- **Slack Integration**: Webhook support for instant alerts
- **Multi-channel**: Concurrent notifications via all enabled channels

### 🏢 Enterprise Features
- **Multi-tenancy**: Support for multiple hotels (configurable)
- **RBAC**: Role-based access control (admin/staff/viewer)
- **Audit Logs**: Track all actions and configuration changes
- **Docker Deployment**: Complete containerization with docker-compose
- **Monitoring Stack**: Prometheus + Grafana integration
- **Message Queue**: Celery + Redis for async processing

## Architecture

```
hotel-tablet-security/
├── android-agent/         # Kotlin Android app
│   ├── admin/            # Password protection, calibration, provisioning
│   ├── security/         # WiFi fence, battery watcher
│   ├── service/          # Foreground kiosk service
│   └── data/             # API client, repositories
├── backend-api/          # FastAPI backend
│   ├── main.py           # API endpoints
│   ├── db.py             # SQLAlchemy models
│   ├── config.py         # Environment settings
│   ├── notifications.py  # Email & Slack alerts
│   └── Dockerfile
├── dashboard/            # Next.js dashboard
│   ├── src/app/         # Pages and components
│   └── Dockerfile
└── docker-compose.yml    # Full stack deployment
```

## Prerequisites

- **Development**:
  - JDK 17+
  - Android SDK + Platform Tools
  - Node.js 20+ (npm or pnpm)
  - Python 3.11+
  - PostgreSQL 15+ (or SQLite for dev)
  - Redis 7+ (optional for caching)

- **Production**:
  - Docker & Docker Compose
  - SMTP server (Gmail, SendGrid, etc.)
  - Slack workspace (optional)

## Quick Start

### 1. Clone & Setup

```bash
git clone <repository-url>
cd hotel-tablet-security
```

### 2. Backend Setup

```bash
cd backend-api

# Create virtual environment
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Copy environment file and configure
cp .env.example .env
# Edit .env with your settings

# Run backend
uvicorn main:app --reload --port 8080
```

### 3. Dashboard Setup

```bash
cd dashboard

# Install dependencies
npm install

# Run dashboard
npm run dev -- -p 3000
```

Open http://localhost:3000

### 4. Android Agent Setup

**Option A: Android Studio**
- Open `android-agent` in Android Studio
- Connect device with USB Debugging enabled
- Run the app

**Option B: VS Code**
- Open `hotel-tablet-security.code-workspace`
- Connect device
- Run task: `android:build` then `android:install`

**First Run - Device Provisioning:**
1. App opens provisioning screen
2. Enter Device ID (e.g., TAB-101) or generate random
3. Enter Room ID (e.g., 101)
4. Tap "Register Device"
5. Device fetches configuration from backend

**RSSI Calibration (per room):**
1. On lock screen, tap hidden button 5 times quickly
2. Enter admin PIN (set on first access)
3. Select "Start Calibration Tool"
4. Enter room number
5. Tap "Refresh WiFi Scan"
6. Select strongest network
7. Tap "Save Room Configuration"
8. Repeat for each room (30 min/room × 16 rooms ≈ 8 hours)

## Docker Deployment

### Full Stack with One Command

```bash
# Copy environment file
cp backend-api/.env.example backend-api/.env
# Edit backend-api/.env with your settings

# Start all services
docker-compose up -d

# Check status
docker-compose ps

# View logs
docker-compose logs -f backend
```

**Services:**
- Backend API: http://localhost:8080
- Dashboard: http://localhost:3000
- Grafana: http://localhost:3001 (admin/admin)
- Prometheus: http://localhost:9090
- PostgreSQL: localhost:5432
- Redis: localhost:6379

### Stop & Cleanup

```bash
docker-compose down          # Stop services
docker-compose down -v       # Stop and remove volumes
```

## Configuration

### Backend (.env)

```bash
# Database
DATABASE_URL=postgresql+asyncpg://user:pass@localhost:5432/hotel_security

# Authentication
API_TOKEN=your-secure-token-here
SECRET_KEY=minimum-32-character-secret-key

# Email Notifications
SMTP_ENABLED=true
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-email@gmail.com
SMTP_PASSWORD=your-app-password
ALERT_EMAIL_RECIPIENTS=admin@hotel.com,security@hotel.com

# Slack Notifications
SLACK_ENABLED=true
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/YOUR/WEBHOOK

# Environment
APP_ENV=production
DEBUG=false
CORS_ORIGINS=https://dashboard.yourdomain.com
```

### Android Agent

**Update Base URL** (for physical devices):
Edit `android-agent/app/src/main/java/com/example/hotel/data/AgentRepository.kt`:

```kotlin
private const val BASE_URL = "http://YOUR_PC_IP:8080"  // Replace with your IP
```

## API Endpoints

### Public
- `GET /` - Service info
- `GET /health` - Health check
- `GET /metrics` - Prometheus metrics

### Authenticated (Bearer Token)
- `POST /api/alert/breach` - Report security breach
- `POST /api/alert/battery` - Report low battery
- `POST /api/heartbeat` - Device status update
- `POST /api/devices/register` - Register new device
- `POST /api/rooms/upsert` - Create/update room config
- `GET /api/config/{device_id}` - Get device configuration
- `GET /api/devices` - List all devices
- `GET /api/alerts/recent?limit=50` - Recent alerts

## Testing

### Backend Tests

```bash
cd backend-api
pytest tests/ -v --cov
```

### Dashboard Tests

```bash
cd dashboard
npm test
npm run test:e2e
```

### Android Tests

```bash
cd android-agent
./gradlew test
./gradlew connectedAndroidTest
```

## Monitoring

### Prometheus Metrics
- Device count, alert count, breach rate
- API response times, error rates
- Database connection pool status

### Grafana Dashboards
- Fleet overview with device status
- Alert trends and breach frequency
- Battery health across fleet
- RSSI heatmaps per room

## Security Best Practices

1. **Change Default Credentials**: Update `API_TOKEN` and `SECRET_KEY` in `.env`
2. **Enable HTTPS**: Use nginx reverse proxy with SSL certificates
3. **Firewall Rules**: Restrict backend access to dashboard and devices only
4. **Device Admin**: Enable Device Owner mode for unbreakable kiosk
5. **Network Isolation**: Use separate VLAN for hotel tablets
6. **Regular Updates**: Keep dependencies updated (Dependabot)
7. **Backup Database**: Automated PostgreSQL backups to S3/GCS

## Troubleshooting

### Device Not Reporting
- Check WiFi connection on device
- Verify `BASE_URL` matches backend IP
- Check backend logs: `docker-compose logs backend`
- Test heartbeat manually: `curl http://YOUR_IP:8080/health`

### Calibration Issues
- Ensure device has `ACCESS_FINE_LOCATION` permission
- WiFi must be enabled during calibration
- Stand in center of room for 10-15 seconds before saving
- RSSI threshold set 10 dBm below current reading for safety margin

### Notifications Not Sending
- SMTP: Enable "Less Secure Apps" or use App Password (Gmail)
- Slack: Verify webhook URL in `.env`
- Check backend logs for notification errors
- Test manually: POST to `/api/alert/breach`

## Development Timeline

| Phase | Hours | Days (3hr/day) | Features |
|-------|-------|----------------|----------|
| Phase 1 | 15 | 5 | Password protection, calibration, provisioning |
| Phase 2 | 12 | 4 | Kiosk mode, tamper detection, secure auth |
| Phase 3 | 18 | 6 | Notifications, enhanced dashboard, offline mode |
| Phase 4 | 20 | 7 | PostgreSQL, Docker, testing, monitoring |
| **Total** | **65** | **22** | Production-ready system |

**Additional Enterprise Features:** +50 hours (Redis, message queue, multi-tenancy, staff mobile app, BLE beacons)

## Pricing Recommendation

**Development:**
- Freelance: $3,250 - $6,500 (65 hours × $50-100/hr)
- White-label to tech company: $15,000 - $25,000

**Licensing:**
- SaaS: $5-10/device/month (recurring)
- One-time: $100-200/device (lifetime)

**Services:**
- On-site calibration: $500-1,000/hotel (8 hours)
- Custom branding: $2,000
- Hotel PMS integration: $5,000+

## 🚀 Deployment

### Cloud Deployment (Recommended)

Deploy your system online in ~30 minutes using free cloud services:

**Quick Start:**
1. 📋 **Follow the checklist**: See [DEPLOY_CHECKLIST.txt](DEPLOY_CHECKLIST.txt)
2. 📖 **Step-by-step guide**: See [DEPLOY_ONLINE.md](DEPLOY_ONLINE.md)
3. 🔧 **Detailed options**: See [CLOUD_DEPLOYMENT.md](CLOUD_DEPLOYMENT.md)

**Deployment Stack:**
- **Backend API** → Render/Railway/Fly.io (FREE tier available)
- **Dashboard** → Vercel (FREE, unlimited deployments)
- **Database** → MongoDB Atlas (FREE 512MB)
- **Cache** → Upstash Redis (FREE 10K commands/day)
- **Total Cost** → $0/month (free tier) or $20-50/month (production)

**What You Need:**
- GitHub account
- MongoDB Atlas account (free)
- Upstash account (free)
- Render/Vercel accounts (free)
- ~30 minutes

### Docker Deployment (Self-Hosted)

For self-hosting on your own server:

```bash
# Configure environment
cd backend-api
cp .env.production .env
# Edit .env with your settings

# Deploy with Docker Compose
cd ..
docker-compose up -d

# Check status
docker-compose ps
docker-compose logs -f
```

See [DEPLOYMENT.md](DEPLOYMENT.md) for detailed server setup.

### Android App Configuration

After deploying your backend, update the Android app:

```bash
# Quick setup
cd backend-api
.\deploy-check.ps1  # Windows
# OR
./deploy-check.sh   # Linux/Mac

# Build production APK
cd ../android-agent
.\gradlew assembleRelease

# APK location
# app/build/outputs/apk/release/app-release.apk
```

See [ANDROID_PRODUCTION_CONFIG.md](ANDROID_PRODUCTION_CONFIG.md) for detailed configuration.

## Support

For issues, feature requests, or contributions:
- GitHub Issues: [repository]/issues
- Email: support@hotel-security.com
- Slack: #hotel-security

## License

Proprietary - All Rights Reserved

---

**Version:** 0.3.0  
**Last Updated:** January 2026  
**Status:** ✅ Production Ready & Cloud-Deployable
