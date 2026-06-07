# Production Deployment Guide

## 🚀 Hotel Tablet Security - Production Deployment

This guide covers deploying the hotel tablet security system to production.

---

## Prerequisites

- **Server:** Linux (Ubuntu 22.04+ recommended)
- **Resources:** 4 vCPU, 8GB RAM minimum
- **Docker:** Docker Engine 24.0+ & Docker Compose 2.20+
- **Domain:** (Optional) SSL certificate for HTTPS
- **Email:** SMTP credentials (Gmail, SendGrid, etc.)
- **Slack:** (Optional) Webhook URL for alerts

---

## 📋 Pre-Deployment Checklist

- [ ] Server provisioned with Docker installed
- [ ] Domain pointed to server IP (if using HTTPS)
- [ ] SSL certificate obtained (Let's Encrypt recommended)
- [ ] SMTP credentials ready
- [ ] Slack workspace setup (optional)
- [ ] GitHub repository cloned to server
- [ ] Firewall configured (ports 80, 443, 8080, 3000)

---

## Step 1: Server Setup

### 1.1 Install Docker (Ubuntu)

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Add user to docker group
sudo usermod -aG docker $USER
newgrp docker

# Install Docker Compose
sudo apt install docker-compose-plugin -y

# Verify
docker --version
docker compose version
```

### 1.2 Configure Firewall

```bash
# Allow SSH, HTTP, HTTPS
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# Enable firewall
sudo ufw enable

# Check status
sudo ufw status
```

---

## Step 2: Clone & Configure

### 2.1 Clone Repository

```bash
cd /opt
sudo git clone <your-repo-url> hotel-security
cd hotel-security
sudo chown -R $USER:$USER .
```

### 2.2 Configure Environment

```bash
cd backend-api

# Copy environment template
cp .env.example .env

# Edit with production values
nano .env
```

**Production `.env` configuration:**

```bash
# Database (PostgreSQL)
DATABASE_URL=postgresql+asyncpg://hotel_user:CHANGE_THIS_PASSWORD@postgres:5432/hotel_security

# Authentication
SECRET_KEY=GENERATE_STRONG_64_CHAR_SECRET_KEY_HERE
API_TOKEN=GENERATE_STRONG_API_TOKEN_HERE

# SMTP Email (Gmail example)
SMTP_ENABLED=true
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-alerts-email@gmail.com
SMTP_PASSWORD=your-app-specific-password
SMTP_FROM_EMAIL=alerts@yourhotel.com
ALERT_EMAIL_RECIPIENTS=security@yourhotel.com,manager@yourhotel.com

# Slack Integration
SLACK_ENABLED=true
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/YOUR/WEBHOOK/URL

# Redis
REDIS_URL=redis://redis:6379/0

# Application
APP_ENV=production
DEBUG=false
CORS_ORIGINS=https://dashboard.yourhotel.com,https://yourhotel.com

# Optional: Sentry for error tracking
SENTRY_DSN=https://...@sentry.io/...
```

**Generate secure keys:**

```bash
# Secret key (64 chars)
openssl rand -hex 32

# API token (32 chars)
openssl rand -hex 16
```

### 2.3 Update Docker Compose

Edit `docker-compose.yml` to use production passwords:

```bash
nano docker-compose.yml
```

Change these values:
- `POSTGRES_PASSWORD`
- `DATABASE_URL` password
- Remove development ports (keep only 80, 443)

---

## Step 3: SSL Certificate (HTTPS)

### 3.1 Install Certbot

```bash
sudo apt install certbot python3-certbot-nginx -y
```

### 3.2 Obtain Certificate

```bash
# Stop any service on port 80
sudo systemctl stop nginx

# Get certificate
sudo certbot certonly --standalone -d dashboard.yourhotel.com -d api.yourhotel.com

# Certificate saved to:
# /etc/letsencrypt/live/dashboard.yourhotel.com/fullchain.pem
# /etc/letsencrypt/live/dashboard.yourhotel.com/privkey.pem
```

### 3.3 Configure Nginx

Create `nginx/nginx.conf`:

```nginx
events {
    worker_connections 1024;
}

http {
    upstream backend {
        server backend:8080;
    }
    
    upstream dashboard {
        server dashboard:3000;
    }
    
    # Redirect HTTP to HTTPS
    server {
        listen 80;
        server_name dashboard.yourhotel.com api.yourhotel.com;
        return 301 https://$host$request_uri;
    }
    
    # Dashboard (HTTPS)
    server {
        listen 443 ssl http2;
        server_name dashboard.yourhotel.com;
        
        ssl_certificate /etc/letsencrypt/live/dashboard.yourhotel.com/fullchain.pem;
        ssl_certificate_key /etc/letsencrypt/live/dashboard.yourhotel.com/privkey.pem;
        
        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_ciphers HIGH:!aNULL:!MD5;
        
        location / {
            proxy_pass http://dashboard;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
    }
    
    # Backend API (HTTPS)
    server {
        listen 443 ssl http2;
        server_name api.yourhotel.com;
        
        ssl_certificate /etc/letsencrypt/live/dashboard.yourhotel.com/fullchain.pem;
        ssl_certificate_key /etc/letsencrypt/live/dashboard.yourhotel.com/privkey.pem;
        
        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_ciphers HIGH:!aNULL:!MD5;
        
        location / {
            proxy_pass http://backend;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
    }
}
```

Update `docker-compose.yml` nginx volumes:

```yaml
nginx:
  volumes:
    - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
    - /etc/letsencrypt:/etc/letsencrypt:ro
```

---

## Step 4: Deploy with Docker

### 4.1 Build & Start Services

```bash
cd /opt/hotel-security

# Pull latest images
docker compose pull

# Build custom images
docker compose build

# Start all services
docker compose up -d

# Check status
docker compose ps
```

Expected output:
```
NAME                    STATUS      PORTS
hotel-backend           Up          0.0.0.0:8080->8080/tcp
hotel-dashboard         Up          0.0.0.0:3000->3000/tcp
hotel-postgres          Up (healthy)
hotel-redis             Up (healthy)
hotel-nginx             Up          0.0.0.0:80->80/tcp, 0.0.0.0:443->443/tcp
hotel-prometheus        Up
hotel-grafana           Up
```

### 4.2 View Logs

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f backend
docker compose logs -f dashboard

# Last 100 lines
docker compose logs --tail=100 backend
```

### 4.3 Run Migrations

```bash
# Access backend container
docker compose exec backend bash

# Run migrations
alembic upgrade head

# Exit
exit
```

---

## Step 5: Configure Monitoring

### 5.1 Access Grafana

1. Open https://dashboard.yourhotel.com:3001
2. Login: `admin` / `admin` (change on first login)
3. Add Prometheus data source:
   - URL: `http://prometheus:9090`
   - Save & Test

### 5.2 Import Dashboard

1. Click "+" → "Import"
2. Upload `grafana/dashboards/hotel-security.json`
3. Select Prometheus data source
4. Import

### 5.3 Setup Alerts

Configure alert rules in `prometheus/alerts.yml`:

```yaml
groups:
  - name: hotel_security
    interval: 30s
    rules:
      - alert: ServiceDown
        expr: up == 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Service {{ $labels.job }} is down"
      
      - alert: HighBreachRate
        expr: rate(breach_alerts_total[5m]) > 0.1
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "High breach rate detected"
```

---

## Step 6: Android Device Configuration

### 6.1 Update Base URL

Before building APK, edit `android-agent/app/src/main/java/com/example/hotel/data/AgentRepository.kt`:

```kotlin
private const val BASE_URL = "https://api.yourhotel.com"
```

### 6.2 Build Production APK

```bash
cd android-agent

# Build release APK (signed)
./gradlew assembleRelease

# APK location:
# app/build/outputs/apk/release/app-release.apk
```

### 6.3 Install on Tablets

**Via USB:**
```bash
adb install app/build/outputs/apk/release/app-release.apk
```

**Via MDM:**
- Upload APK to your Mobile Device Management system
- Push to all hotel tablets

---

## Step 7: Post-Deployment

### 7.1 Health Checks

```bash
# Backend health
curl https://api.yourhotel.com/health
# Expected: {"status":"healthy"}

# Dashboard
curl https://dashboard.yourhotel.com
# Expected: HTML response

# Prometheus targets
curl http://localhost:9090/api/v1/targets
```

### 7.2 Test Notifications

**Email:**
```bash
curl -X POST https://api.yourhotel.com/api/alert/breach \
  -H "Authorization: Bearer YOUR_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"TEST-001","roomId":"101","rssi":-80}'

# Check email inbox for alert
```

**Slack:**
- Check Slack channel for alert message

### 7.3 Setup Backup

**Database Backup Script** (`/opt/scripts/backup.sh`):

```bash
#!/bin/bash
BACKUP_DIR="/opt/backups/postgres"
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p $BACKUP_DIR

docker compose exec -T postgres pg_dump -U hotel_user hotel_security | gzip > $BACKUP_DIR/backup_$DATE.sql.gz

# Keep last 30 days
find $BACKUP_DIR -type f -mtime +30 -delete

echo "Backup completed: backup_$DATE.sql.gz"
```

**Cron job:**
```bash
# Edit crontab
crontab -e

# Add daily backup at 2 AM
0 2 * * * /opt/scripts/backup.sh >> /var/log/hotel-backup.log 2>&1
```

---

## Step 8: Maintenance

### 8.1 Update System

```bash
cd /opt/hotel-security

# Pull latest code
git pull origin main

# Rebuild and restart
docker compose down
docker compose build
docker compose up -d

# Run migrations
docker compose exec backend alembic upgrade head
```

### 8.2 View Metrics

**Prometheus:** http://localhost:9090
**Grafana:** https://dashboard.yourhotel.com:3001

### 8.3 Scale Services

Edit `docker-compose.yml`:

```yaml
backend:
  deploy:
    replicas: 3  # Run 3 backend instances
```

```bash
docker compose up -d --scale backend=3
```

---

## 🔒 Security Hardening

### 1. Firewall Rules

```bash
# Allow only specific IPs for admin access
sudo ufw allow from YOUR_OFFICE_IP to any port 3001  # Grafana
sudo ufw allow from YOUR_OFFICE_IP to any port 9090  # Prometheus
```

### 2. Regular Updates

```bash
# System updates
sudo apt update && sudo apt upgrade -y

# Docker security scan
docker scan hotel-backend
```

### 3. Secret Rotation

Rotate passwords and keys every 90 days:
- Database password
- API tokens
- SSL certificates (auto-renewed by certbot)

---

## 📊 Monitoring Checklist

- [ ] Grafana dashboards showing device status
- [ ] Prometheus scraping all services
- [ ] Email alerts working (test breach)
- [ ] Slack alerts working (test breach)
- [ ] Daily backups running
- [ ] SSL certificate auto-renewal (certbot renew)
- [ ] Disk space monitoring (>20% free)
- [ ] Log rotation configured

---

## 🚨 Troubleshooting

### Service Won't Start

```bash
# Check logs
docker compose logs backend

# Check port conflicts
sudo netstat -tulpn | grep :8080

# Restart service
docker compose restart backend
```

### Database Connection Failed

```bash
# Check PostgreSQL
docker compose exec postgres psql -U hotel_user -d hotel_security

# Run migrations
docker compose exec backend alembic upgrade head
```

### High CPU Usage

```bash
# Check resource usage
docker stats

# Limit resources in docker-compose.yml
services:
  backend:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
```

---

## 📞 Support

- **Logs:** `/var/log/hotel-security/`
- **Backups:** `/opt/backups/postgres/`
- **Config:** `/opt/hotel-security/backend-api/.env`

For issues: Check [FINAL_STATUS.md](FINAL_STATUS.md) and [README.md](README.md)

---

**Deployment Complete! 🎉**

Access:
- **Dashboard:** https://dashboard.yourhotel.com
- **API:** https://api.yourhotel.com
- **Grafana:** https://dashboard.yourhotel.com:3001
- **Prometheus:** http://localhost:9090 (localhost only)
