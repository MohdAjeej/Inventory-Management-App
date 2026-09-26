@echo off
echo ========================================
echo   B2B Inventory - Add Firewall Rule
echo ========================================
echo.
echo This will allow port 8082 through Windows Firewall
echo.
echo Right-click this file and select "Run as administrator"
echo.
pause

netsh advfirewall firewall add rule name="B2B Inventory Backend" dir=in action=allow protocol=TCP localport=8082

if %errorlevel%==0 (
    echo.
    echo ========================================
    echo   SUCCESS! Firewall rule added!
    echo ========================================
    echo.
    echo Port 8082 is now accessible from your phone.
    echo You can now install and test the app.
    echo.
) else (
    echo.
    echo ========================================
    echo   ERROR! Please run as Administrator
    echo ========================================
    echo.
    echo Right-click this file and select "Run as administrator"
    echo.
)

pause
