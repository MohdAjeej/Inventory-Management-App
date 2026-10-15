@echo off
echo Adding Windows Firewall rule for B2B Backend (Port 8082)...
echo.

netsh advfirewall firewall add rule name="B2B Backend Port 8082 Inbound" dir=in action=allow protocol=TCP localport=8082

echo.
echo Firewall rule added successfully!
echo Backend port 8082 is now accessible from your phone.
echo.
pause
