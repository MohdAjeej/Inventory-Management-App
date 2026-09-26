# Network Diagnostic Script for B2B Inventory App

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  B2B Inventory Network Checker" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# Get PC IP
Write-Host "Checking PC Network..." -ForegroundColor Yellow
$pcIP = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -like "192.168.*" -and $_.InterfaceAlias -eq "Wi-Fi" }).IPAddress

if ($pcIP) {
    Write-Host "✓ PC WiFi IP: $pcIP" -ForegroundColor Green
} else {
    Write-Host "✗ PC not connected to WiFi!" -ForegroundColor Red
    Write-Host "  Please connect your PC to WiFi first.`n" -ForegroundColor Yellow
    exit
}

# Get Phone IP
Write-Host "`nChecking Phone Network..." -ForegroundColor Yellow
$phoneIP = (adb shell ip addr show wlan0 | Select-String "inet " | Out-String).Trim()

if ($phoneIP -match "inet (\d+\.\d+\.\d+\.\d+)") {
    $phoneIP = $matches[1]
    Write-Host "✓ Phone WiFi IP: $phoneIP" -ForegroundColor Green
} else {
    Write-Host "✗ Phone not connected or ADB not working!" -ForegroundColor Red
    exit
}

# Compare networks
$pcNetwork = $pcIP.Substring(0, $pcIP.LastIndexOf('.'))
$phoneNetwork = $phoneIP.Substring(0, $phoneIP.LastIndexOf('.'))

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  Network Analysis" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

if ($pcNetwork -eq $phoneNetwork) {
    Write-Host "✓ SAME NETWORK - Devices can communicate!" -ForegroundColor Green
    Write-Host "  PC Network: $pcNetwork.x" -ForegroundColor Green
    Write-Host "  Phone Network: $phoneNetwork.x" -ForegroundColor Green
    Write-Host "`n✓ Your current configuration should work!" -ForegroundColor Green
    Write-Host "  Backend URL: http://${pcIP}:8082/`n" -ForegroundColor Cyan
} else {
    Write-Host "✗ DIFFERENT NETWORKS - Cannot communicate!" -ForegroundColor Red
    Write-Host "  PC Network: $pcNetwork.x" -ForegroundColor Red
    Write-Host "  Phone Network: $phoneNetwork.x" -ForegroundColor Red
    
    Write-Host "`n🔧 FIX:" -ForegroundColor Yellow
    Write-Host "  1. Connect BOTH devices to the SAME WiFi network" -ForegroundColor Yellow
    Write-Host "  2. Or use your PC's WiFi hotspot" -ForegroundColor Yellow
    Write-Host "  3. Run this script again to verify`n" -ForegroundColor Yellow
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  Current App Configuration" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

$apiConfig = Get-Content "android\app\src\main\java\com\b2binventory\app\data\ApiConfig.kt" | Select-String "BASE_URL"
Write-Host $apiConfig -ForegroundColor White

Write-Host "`nPress any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
