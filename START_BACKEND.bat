@echo off
echo Starting B2B Inventory Backend on Port 8082...
echo.

cd /d "%~dp0"

echo Step 1: Stopping old containers...
docker stop b2b-inventory-backend 2>nul
docker rm b2b-inventory-backend 2>nul

echo.
echo Step 2: Starting Database...
docker start b2b-inventory-db
timeout /t 5 /nobreak >nul

echo.
echo Step 3: Starting Backend on Port 8082...
docker run -d --name b2b-inventory-backend --network b2b-inventory-app_b2b-network -e SPRING_DATASOURCE_URL=jdbc:postgresql://b2b-inventory-db:5432/b2b_inventory -e SPRING_DATASOURCE_USERNAME=postgres -e SPRING_DATASOURCE_PASSWORD=postgres -e SPRING_JPA_HIBERNATE_DDL_AUTO=update -e SERVER_PORT=8082 -p 8082:8082 b2b-backend

echo.
echo Step 4: Waiting for backend to start...
timeout /t 15 /nobreak >nul

echo.
echo Step 5: Checking status...
docker ps | findstr "b2b"

echo.
echo Step 6: Viewing backend logs (last 20 lines)...
docker logs b2b-inventory-backend --tail 20

echo.
echo ========================================
echo Backend Status:
echo ========================================
echo Database (PostgreSQL): Running on port 5432
echo Backend (Spring Boot): Running on port 8082
echo.
echo Test from phone browser:
echo http://192.168.29.166:8082
echo.
echo Press any key to view live logs...
pause >nul
docker logs -f b2b-inventory-backend
