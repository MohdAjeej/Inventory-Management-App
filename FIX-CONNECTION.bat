@echo off
echo ======================================
echo B2B Inventory - Fix Connection Issue
echo ======================================
echo.
echo This will:
echo   1. Add Windows Firewall rule for port 8082
echo   2. Check if backend is running
echo   3. Show your computer's IP address
echo.
echo IMPORTANT: This needs Administrator privileges!
echo.
pause

powershell -ExecutionPolicy Bypass -File "%~dp0fix-firewall.ps1"

pause
