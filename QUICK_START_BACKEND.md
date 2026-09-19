# 🚀 Quick Start Backend - Fix Connection Error

## The Problem

Your app shows:
```
Failed to connect to /192.168.29.166 (port 8082) from /192.168.29.250 (port 49596) after 10000ms
```

**Good news**: Both devices are on the same network now (192.168.29.x)!  
**Bad news**: Backend isn't running on port 8082

## The Solution

### Option 1: Run START_BACKEND.bat (Easiest)

```powershell
# Right-click and "Run as Administrator"
.\START_BACKEND.bat
```

This will:
1. Stop any old containers
2. Start PostgreSQL database
3. Start backend on port 8082
4. Show you the logs
5. Let you test the connection

### Option 2: Manual Start

```powershell
# 1. Start database
docker start b2b-inventory-db

# 2. Wait 5 seconds
Start-Sleep -Seconds 5

# 3. Remove old backend (if exists)
docker stop b2b-inventory-backend
docker rm b2b-inventory-backend

# 4. Start backend on port 8082
docker run -d --name b2b-inventory-backend `
  --network b2b-inventory-app_b2b-network `
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://b2b-inventory-db:5432/b2b_inventory `
  -e SPRING_DATASOURCE_USERNAME=postgres `
  -e SPRING_DATASOURCE_PASSWORD=postgres `
  -e SPRING_JPA_HIBERNATE_DDL_AUTO=update `
  -e SERVER_PORT=8082 `
  -p 8082:8082 `
  b2b-backend

# 5. Check logs
docker logs -f b2b-inventory-backend
```

## Verify It's Running

### Check Port 8082:
```powershell
netstat -an | Select-String "8082"
```

You should see:
```
TCP    0.0.0.0:8082    0.0.0.0:0    LISTENING
```

### Check Docker Status:
```powershell
docker ps
```

You should see both containers:
- `b2b-inventory-db` (PostgreSQL) on port 5432
- `b2b-inventory-backend` (Spring Boot) on port 8082

### Test from Computer Browser:
Open: http://192.168.29.166:8082

You should see: "Whitelabel Error Page" or JSON response (this is good!)

### Test from Phone Browser:
Open: http://192.168.29.166:8082

If this works, your app will work too!

## Check Backend Logs

```powershell
docker logs b2b-inventory-backend
```

Look for:
```
Tomcat started on port 8082
Started B2bInventoryApplication
```

## If It Still Doesn't Work

### Fix 1: Rebuild Backend
```powershell
docker build -t b2b-backend ./backend
.\START_BACKEND.bat
```

### Fix 2: Check Firewall
```powershell
# Run as Administrator
.\FIX-CONNECTION.bat
```

### Fix 3: Restart Everything
```powershell
# Stop all
docker stop b2b-inventory-db b2b-inventory-backend
docker rm b2b-inventory-db b2b-inventory-backend

# Start fresh
docker-compose up -d
```

**Wait!** docker-compose.yml was using port 8080. I fixed it to use 8082.

## What Was Fixed

1. ✅ Updated `docker-compose.yml` to use port 8082 instead of 8080
2. ✅ Added `SERVER_PORT=8082` environment variable
3. ✅ Changed port mapping from `8080:8080` to `8082:8082`
4. ✅ Built new backend Docker image with fixes
5. ✅ Created `START_BACKEND.bat` for easy startup

## Test the Full Flow

1. **Start backend** (using START_BACKEND.bat)
2. **Test in phone browser**: http://192.168.29.166:8082
3. **Open your app** on phone
4. **Click Sign In** with your credentials
5. **Success!** Should navigate to Dashboard

## Troubleshooting

### Backend Won't Start:
```powershell
# Check what's using port 8082
netstat -ano | Select-String "8082"

# Kill the process if needed
Stop-Process -Id <PID> -Force

# Try again
.\START_BACKEND.bat
```

### Database Connection Error:
```powershell
# Restart database
docker restart b2b-inventory-db

# Wait 10 seconds
Start-Sleep -Seconds 10

# Restart backend
docker restart b2b-inventory-backend
```

### Container Keeps Stopping:
```powershell
# Check logs for errors
docker logs b2b-inventory-backend

# Common issues:
# - Port already in use
# - Database not reachable
# - Missing environment variables
```

## Backend Configuration

**Database**: PostgreSQL on port 5432
- Container: `b2b-inventory-db`
- Database: `b2b_inventory`
- User: `postgres`
- Password: `postgres`

**Backend**: Spring Boot on port 8082
- Container: `b2b-inventory-backend`
- Port: 8082
- Network: `b2b-inventory-app_b2b-network`

**App API**: 
- Base URL: `http://192.168.29.166:8082/`
- Login: `/api/auth/login`
- Register: `/api/auth/register-business`
- Products: `/api/inventory/products`

## Quick Commands

```powershell
# Start everything
.\START_BACKEND.bat

# View logs
docker logs -f b2b-inventory-backend

# Restart backend
docker restart b2b-inventory-backend

# Stop everything
docker stop b2b-inventory-backend b2b-inventory-db

# Check status
docker ps

# Test API
Invoke-RestMethod -Uri "http://192.168.29.166:8082/api/inventory/products?businessId=1"
```

## Success Indicators

✅ Docker shows both containers running  
✅ Port 8082 is listening  
✅ Browser shows response from http://192.168.29.166:8082  
✅ Phone browser can access the URL  
✅ App login works without connection error  
✅ Navigate to Dashboard successfully  

---

**Run `.\START_BACKEND.bat` and you're ready to test your app!** 🚀
