#!/bin/bash

# Quick Deployment Script - Run this to deploy to Render

echo "============================================"
echo " Hotel Tablet Security - Production Setup"
echo "============================================"
echo ""

# Check if .env exists
if [ ! -f ".env" ]; then
    echo "❌ .env file not found!"
    echo "📝 Copying .env.production template..."
    cp .env.production .env
    echo "✅ .env file created"
    echo ""
    echo "⚠️  IMPORTANT: Edit .env and fill in your values:"
    echo "   - MONGODB_URL (MongoDB Atlas)"
    echo "   - REDIS_URL (Upstash)"
    echo "   - API_TOKEN (generate secure token)"
    echo "   - SECRET_KEY (generate secure key)"
    echo ""
    exit 1
fi

echo "✅ Environment file found"
echo ""

# Display what needs to be deployed
echo "📦 Components to deploy:"
echo "   1. Backend API (FastAPI) → Render/Railway/Fly.io"
echo "   2. Dashboard (Next.js) → Vercel"
echo "   3. Database → MongoDB Atlas (already cloud)"
echo "   4. Redis → Upstash (already cloud)"
echo ""

# Check if required variables are set
echo "🔍 Checking environment variables..."
source .env

if [[ $MONGODB_URL == *"xxxxx"* ]] || [[ $MONGODB_URL == "mongodb://localhost"* ]]; then
    echo "❌ MONGODB_URL not configured"
    echo "   Get from: https://cloud.mongodb.com/"
    exit 1
fi

if [[ $API_TOKEN == "change-this"* ]]; then
    echo "❌ API_TOKEN not configured"
    echo "   Generate with: openssl rand -hex 32"
    exit 1
fi

if [[ $SECRET_KEY == "change-this"* ]]; then
    echo "❌ SECRET_KEY not configured"
    echo "   Generate with: openssl rand -hex 64"
    exit 1
fi

echo "✅ Environment configured"
echo ""

echo "🚀 Ready to deploy!"
echo ""
echo "Next steps:"
echo "   1. Push code to GitHub"
echo "   2. Deploy Backend:"
echo "      → Render: https://render.com/"
echo "      → Railway: https://railway.app/"
echo "      → Fly.io: flyctl deploy"
echo ""
echo "   3. Deploy Dashboard:"
echo "      → Vercel: https://vercel.com/"
echo ""
echo "   4. Update CORS_ORIGINS in backend with dashboard URL"
echo ""
echo "   5. Update Android app API_URL with backend URL"
echo ""
echo "See CLOUD_DEPLOYMENT.md for detailed instructions"
echo ""
