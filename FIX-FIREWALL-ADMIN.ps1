# Run this script as Administrator to fix the firewall
# Right-click -> Run with PowerShell as Administrator

Write-Host "Adding firewall rule for B2B Inventory Backend..." -ForegroundColor Yellow

# Add firewall rule for port 8082
netsh advfirewall firewall add rule name="B2B Inventory Backend" dir=in action=allow protocol=TCP localport=8082

Write-Host ""
Write-Host "Firewall rule added successfully!" -ForegroundColor Green
Write-Host "Port 8082 is now accessible from your phone." -ForegroundColor Green
Write-Host ""
Write-Host "You can now try logging into the app again." -ForegroundColor Cyan
Write-Host ""
Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
