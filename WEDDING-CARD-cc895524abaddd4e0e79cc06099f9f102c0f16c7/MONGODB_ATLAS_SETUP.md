# MongoDB Atlas Setup Guide 🚀

Complete guide to set up MongoDB Atlas (cloud database) for the Hotel Tablet Security System.

---

## Step 1: Create MongoDB Atlas Account

1. **Go to MongoDB Atlas**: https://www.mongodb.com/cloud/atlas/register
2. **Sign up** with email or Google account
3. **Verify email** and log in

---

## Step 2: Create a Free Cluster

1. **Click "Build a Database"** or "Create"
2. **Choose deployment option:**
   - **M0 Free Tier** (512 MB storage, perfect for development)
   - Or choose paid tier for production

3. **Select cloud provider & region:**
   - Provider: AWS, Google Cloud, or Azure
   - Region: Choose closest to your location
   - Click **"Create Cluster"**

4. **Wait 3-5 minutes** for cluster creation

---

## Step 3: Configure Database Access

### 3.1 Create Database User

1. Go to **"Database Access"** (left sidebar)
2. Click **"Add New Database User"**
3. **Authentication Method:** Password
4. **Username:** `hotel_admin` (or your choice)
5. **Password:** Generate a strong password or create custom
   - ⚠️ **SAVE THIS PASSWORD** - you'll need it for connection string
6. **Database User Privileges:** 
   - Select **"Read and write to any database"**
7. Click **"Add User"**

### 3.2 Add IP Whitelist

1. Go to **"Network Access"** (left sidebar)
2. Click **"Add IP Address"**
3. **Options:**
   - **Allow Access from Anywhere:** `0.0.0.0/0` (for development/testing)
   - **Add Current IP Address:** For your local machine only
   - **Add specific IPs:** For production servers
4. Click **"Confirm"**

⚠️ **Security Note:** For production, use specific IPs instead of `0.0.0.0/0`

---

## Step 4: Get Connection String

1. Go to **"Database"** (left sidebar)
2. Click **"Connect"** button on your cluster
3. Choose **"Connect your application"**
4. **Driver:** Python, **Version:** 3.12 or later
5. **Copy the connection string:**

```
mongodb+srv://hotel_admin:<password>@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority
```

6. **Replace `<password>`** with your actual password
7. **Save this connection string** - you'll use it in `.env` file

---

## Step 5: Configure Backend

### 5.1 Create `.env` file

Create `backend-api/.env` with your MongoDB Atlas credentials:

```env
# MongoDB Atlas Connection
MONGODB_URL=mongodb+srv://hotel_admin:YOUR_PASSWORD@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority
DATABASE_NAME=hotel_security

# JWT Authentication
SECRET_KEY=your-super-secret-key-min-32-characters-long
JWT_EXPIRATION_MINUTES=43200

# Redis (local or cloud)
REDIS_URL=redis://localhost:6379/0

# Notifications
SMTP_ENABLED=true
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-email@gmail.com
SMTP_PASSWORD=your-app-password
ALERT_EMAIL_RECIPIENTS=security@hotel.com

SLACK_ENABLED=true
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/YOUR/WEBHOOK

# Application
APP_ENV=production
DEBUG=false
CORS_ORIGINS=*
```

### 5.2 Install Dependencies

```powershell
cd backend-api
pip install motor pymongo
```

### 5.3 Test Connection

```powershell
# Start backend
.\.venv\Scripts\python.exe -m uvicorn main:app --reload --port 8080

# Test health check
curl http://localhost:8080/health
```

Expected response:
```json
{
  "status": "healthy",
  "database": "connected",
  "redis": true,
  "timestamp": "2025-12-21T12:00:00"
}
```

---

## Step 6: (Optional) MongoDB Atlas API Key

For programmatic database management, create API keys:

1. Go to **"Access Manager"** → **"Organization Access"**
2. Click **"Create API Key"**
3. **Description:** "Hotel Security Backend"
4. **Permissions:** Organization Member or Project Owner
5. **Copy API Key and Secret** ⚠️ Save these immediately!

Add to `.env`:
```env
MONGODB_API_KEY=your-public-key
MONGODB_API_SECRET=your-private-secret
```

---

## Step 7: Create Database and Collections

MongoDB Atlas automatically creates collections when you insert data, but you can pre-create them:

1. Go to **"Database"** → **"Browse Collections"**
2. Click **"Create Database"**
3. **Database name:** `hotel_security`
4. **Collection name:** `devices`
5. Click **"Create"**

Repeat for other collections:
- `alerts`
- `rooms`
- `hotels`

**Or let the backend create them automatically!**

---

## Step 8: Configure Indexes (Important for Performance)

The backend auto-creates indexes on startup, but you can verify:

1. Go to **"Database"** → **"Browse Collections"**
2. Select collection → **"Indexes"** tab
3. Verify indexes exist:

**devices collection:**
- `hotel_id` (ascending)
- `room_id` (ascending)
- `last_seen` (descending)

**alerts collection:**
- `ts` (descending)
- `hotel_id` (ascending)
- `acknowledged` (ascending)

**rooms collection:**
- `hotel_id` (ascending)

---

## Step 9: Deploy with Docker

Update your `.env` file in the project root:

```env
# MongoDB Atlas (no local MongoDB needed)
MONGODB_URL=mongodb+srv://hotel_admin:YOUR_PASSWORD@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority
DATABASE_NAME=hotel_security
```

Start services:
```powershell
docker-compose up -d
```

Services running:
- ✅ Backend API (port 8080) - connects to Atlas
- ✅ Dashboard (port 3000)
- ✅ Redis (port 6379)
- ✅ Celery Worker
- ✅ Flower (port 5555)
- ✅ Prometheus (port 9090)
- ✅ Grafana (port 3001)

❌ Local MongoDB removed (using Atlas instead)

---

## Step 10: Monitor Database

### MongoDB Atlas Dashboard

1. **Metrics:** Real-time performance metrics
   - Go to **"Metrics"** tab
   - View connections, operations/sec, query performance

2. **Performance Advisor:** 
   - Suggests indexes for slow queries
   - Go to **"Performance Advisor"** tab

3. **Real-time Logs:**
   - View database logs
   - Go to **"Metrics"** → **"Logs"**

### Alerts (Optional)

1. Go to **"Alerts"** (left sidebar)
2. Set up alerts for:
   - High CPU usage
   - Connection spikes
   - Disk space warnings

---

## Pricing

### Free Tier (M0)
- ✅ 512 MB storage
- ✅ Shared RAM
- ✅ Good for: Development, testing, small deployments
- ✅ Cost: **FREE forever**

### Paid Tiers (when you need more)
- **M10:** $0.08/hour (~$60/month) - 2GB RAM, 10GB storage
- **M20:** $0.20/hour (~$150/month) - 4GB RAM, 20GB storage
- Scalable up to TB of storage

---

## Security Best Practices

### ✅ Connection Security
1. **Use VPC Peering** for production (connects Atlas to your VPC)
2. **Enable IP Whitelist** - don't use `0.0.0.0/0` in production
3. **Rotate passwords** regularly
4. **Use TLS/SSL** (enabled by default with `mongodb+srv://`)

### ✅ Authentication
1. **Strong passwords** (min 20 chars)
2. **Use SCRAM-SHA-256** (default, most secure)
3. **Create separate users** for different apps
4. **Minimum privileges** - read-only for analytics

### ✅ Backup
- **Atlas automatic backups** (enabled by default)
- **Point-in-time recovery** (M10+ clusters)
- **Download snapshots** if needed

---

## Troubleshooting

### ❌ Error: "Authentication failed"
**Fix:** 
1. Double-check password in connection string
2. Ensure user exists in Database Access
3. Verify database privileges

### ❌ Error: "Connection timeout"
**Fix:**
1. Check Network Access whitelist
2. Add your IP: `0.0.0.0/0` (temporary)
3. Verify internet connection

### ❌ Error: "Database not found"
**Fix:**
- MongoDB creates databases automatically on first write
- Or create manually in Atlas dashboard

### ❌ Backend won't connect
**Fix:**
```powershell
# Test connection string
python -c "from pymongo import MongoClient; client = MongoClient('YOUR_MONGODB_URL'); print(client.server_info())"
```

---

## Testing Your Setup

### 1. Register a device
```powershell
curl -X POST http://localhost:8080/api/devices/register `
  -H "Content-Type: application/json" `
  -d '{"deviceId": "TAB-001", "roomId": "101", "hotelId": "hotel-1"}'
```

### 2. Check data in Atlas
1. Go to **"Browse Collections"**
2. Click **"devices"** collection
3. See your newly created device

### 3. Send heartbeat
```powershell
$token = "your-jwt-token-from-register-response"
curl -X POST http://localhost:8080/api/heartbeat `
  -H "Authorization: Bearer $token" `
  -H "Content-Type: application/json" `
  -d '{"deviceId": "TAB-001", "roomId": "101", "wifiBssid": "AA:BB:CC:DD:EE:FF", "rssi": -55, "battery": 85}'
```

### 4. View metrics
```powershell
curl http://localhost:8080/metrics
```

---

## Migration from Local MongoDB

If you have existing data in local MongoDB:

### Option 1: MongoDB Compass
1. **Install MongoDB Compass:** https://www.mongodb.com/try/download/compass
2. **Connect to local:** `mongodb://localhost:27017`
3. **Export collections** → Export as JSON
4. **Connect to Atlas:** Use connection string
5. **Import collections** → Import JSON files

### Option 2: mongodump/mongorestore
```bash
# Export from local
mongodump --db=hotel_security --out=./backup

# Import to Atlas
mongorestore --uri="mongodb+srv://user:pass@cluster.mongodb.net" --db=hotel_security ./backup/hotel_security
```

---

## Production Checklist

Before going live:

- [ ] Use paid tier (M10+) for production
- [ ] Enable VPC Peering for security
- [ ] Restrict IP whitelist to specific IPs
- [ ] Enable point-in-time backups
- [ ] Set up performance alerts
- [ ] Create read-only users for analytics
- [ ] Rotate passwords regularly
- [ ] Enable audit logs (Enterprise only)
- [ ] Test disaster recovery plan
- [ ] Monitor query performance

---

## Support & Resources

- **MongoDB Atlas Docs:** https://docs.atlas.mongodb.com/
- **Motor (async driver):** https://motor.readthedocs.io/
- **Support:** https://support.mongodb.com/
- **Community Forums:** https://community.mongodb.com/

---

## Summary

✅ **MongoDB Atlas** - Cloud-hosted, no local installation needed
✅ **Free tier available** - Perfect for development
✅ **Auto-scaling** - Grows with your business
✅ **Automatic backups** - No data loss
✅ **Global deployment** - Deploy close to users
✅ **Built-in monitoring** - Real-time insights
✅ **Secure by default** - TLS, authentication, IP whitelist

**Your backend is now connected to MongoDB Atlas! 🎉**
