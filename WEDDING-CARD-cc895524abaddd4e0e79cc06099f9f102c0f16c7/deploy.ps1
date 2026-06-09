# Deployment Script for Production

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Hotel Tablet Security - Deployment" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if .env file exists
if (-not (Test-Path "backend-api\.env")) {
    Write-Host "[ERROR] .env file not found!" -ForegroundColor Red
    Write-Host "Please create backend-api\.env with your MongoDB Atlas credentials" -ForegroundColor Yellow
    Write-Host "Example:" -ForegroundColor Yellow
    Write-Host "  MONGODB_URL=mongodb+srv://user:pass@cluster.mongodb.net/" -ForegroundColor Gray
    Write-Host "  DATABASE_NAME=hotel_security" -ForegroundColor Gray
    Write-Host "  SECRET_KEY=your-secret-key-here" -ForegroundColor Gray
    exit 1
}

# Check Docker is running
try {
    docker ps | Out-Null
    Write-Host "[OK] Docker is running" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Docker is not running" -ForegroundColor Red
    Write-Host "Please start Docker Desktop" -ForegroundColor Yellow
    exit 1
}

# Pull latest images
Write-Host "`n[1/4] Pulling Docker images..." -ForegroundColor Yellow
docker-compose pull

# Build backend
Write-Host "`n[2/4] Building backend..." -ForegroundColor Yellow
docker-compose build backend

# Start services
Write-Host "`n[3/4] Starting services..." -ForegroundColor Yellow
docker-compose up -d

# Wait for services to be healthy
Write-Host "`n[4/4] Waiting for services to be ready..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# Check service status
Write-Host "`nService Status:" -ForegroundColor Cyan
docker-compose ps

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host " Deployment Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Access your services:" -ForegroundColor Cyan
Write-Host "  Backend API:    http://localhost:8080" -ForegroundColor White
Write-Host "  API Docs:       http://localhost:8080/docs" -ForegroundColor White
Write-Host "  Dashboard:      http://localhost:3000" -ForegroundColor White
Write-Host "  Flower:         http://localhost:5555" -ForegroundColor White
Write-Host "  Prometheus:     http://localhost:9090" -ForegroundColor White
Write-Host "  Grafana:        http://localhost:3001" -ForegroundColor White
Write-Host ""
Write-Host "View logs:" -ForegroundColor Cyan
Write-Host "  docker-compose logs -f backend" -ForegroundColor Gray
Write-Host ""
