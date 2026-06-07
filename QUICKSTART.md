# Hotel Tablet Security System - Quick Start Guide

## Project Structure (Optimized)

```
hotel-tablet-security/
├── backend-api/          # Python FastAPI backend
│   ├── main.py          # Main API application
│   ├── db.py            # MongoDB models and connection
│   ├── auth.py          # JWT authentication
│   ├── config.py        # Configuration settings
│   ├── notifications.py # Email/Slack alerts
│   ├── tasks.py         # Celery background tasks
│   ├── requirements.txt # Python dependencies
│   ├── .env.example     # Environment template
│   └── Dockerfile       # Docker container config
├── dashboard/           # Next.js React dashboard
├── android-agent/       # Kotlin Android app
├── docker-compose.yml   # Full stack orchestration
└── MONGODB_ATLAS_SETUP.md  # Database setup guide

```

## Quick Start (3 Steps)

### 1. Set up MongoDB Atlas (Cloud Database)

**Create free account:** https://www.mongodb.com/cloud/atlas/register

**Get connection string:**
```
mongodb+srv://username:password@cluster0.xxxxx.mongodb.net/
```

### 2. Configure Environment

Create `backend-api/.env`:
```env
MONGODB_URL=mongodb+srv://user:pass@cluster.mongodb.net/
DATABASE_NAME=hotel_security
SECRET_KEY=your-secret-key-min-32-characters
REDIS_URL=redis://localhost:6379/0
```

### 3. Run Backend

**Option A: Local Development**
```powershell
cd backend-api
pip install -r requirements.txt
python -m uvicorn main:app --reload --port 8080
```

**Option B: Docker (Full Stack)**
```powershell
docker-compose up -d
```

**Access:**
- Backend API: http://localhost:8080
- Dashboard: http://localhost:3000
- API Docs: http://localhost:8080/docs

## Features

✅ **JWT Authentication** - Secure device tokens
✅ **Real-time SSE** - Live device updates
✅ **MongoDB Atlas** - Cloud database
✅ **Celery Tasks** - Async notifications
✅ **Multi-tenancy** - Multiple hotels
✅ **Email/Slack Alerts** - Breach notifications
✅ **Kiosk Mode** - Android security app
✅ **Tamper Detection** - 5 security checks
✅ **Offline Queue** - Works without internet

## API Endpoints

### Device Management
- `POST /api/devices/register` - Register device & get JWT
- `POST /api/heartbeat` - Device status update
- `GET /api/devices` - List all devices
- `GET /api/config/{device_id}` - Get device config

### Alerts
- `POST /api/alert/breach` - Security breach
- `POST /api/alert/battery` - Low battery
- `GET /api/alerts/recent` - Recent alerts
- `POST /api/alerts/acknowledge` - Acknowledge alert

### Real-time
- `GET /api/events` - SSE stream for live updates

### System
- `GET /health` - Health check
- `GET /metrics` - System metrics

## Testing

```powershell
# Health check
curl http://localhost:8080/health

# Register device
curl -X POST http://localhost:8080/api/devices/register `
  -H "Content-Type: application/json" `
  -d '{"deviceId":"TAB-001","roomId":"101"}'

# Response includes JWT token
```

## Technology Stack

**Backend:** FastAPI (Python 3.14+)
**Database:** MongoDB Atlas (Cloud)
**Cache/Queue:** Redis + Celery
**Dashboard:** Next.js 16 + React 19
**Android:** Kotlin + Room + Retrofit
**Deployment:** Docker Compose

## Documentation

- **[MONGODB_ATLAS_SETUP.md](MONGODB_ATLAS_SETUP.md)** - Complete database setup
- **[IMPLEMENTATION_COMPLETE.md](IMPLEMENTATION_COMPLETE.md)** - All features list
- **[DEPLOYMENT.md](DEPLOYMENT.md)** - Production deployment

## Support

**Issues:** Check logs in Docker: `docker-compose logs -f backend`
**API Docs:** Interactive docs at http://localhost:8080/docs
**Database:** View data in MongoDB Atlas dashboard

---

**System is production-ready! 🚀**
