# B2B Inventory - Fix Firewall for Port 8082
# Run this as Administrator

Write-Host "=== B2B Inventory Backend - Firewall Fix ===" -ForegroundColor Cyan
Write-Host ""

# Check if running as administrator
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "ERROR: This script must be run as Administrator!" -ForegroundColor Red
    Write-Host "Right-click PowerShell and select 'Run as Administrator'" -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "Step 1: Checking existing firewall rules..." -ForegroundColor Yellow
$existingRule = Get-NetFirewallRule -DisplayName "B2B Backend Port 8082" -ErrorAction SilentlyContinue

if ($existingRule) {
    Write-Host "  -> Rule already exists. Removing old rule..." -ForegroundColor Gray
    Remove-NetFirewallRule -DisplayName "B2B Backend Port 8082" -ErrorAction SilentlyContinue
}

Write-Host "Step 2: Creating new firewall rule for port 8082..." -ForegroundColor Yellow
try {
    New-NetFirewallRule `
        -DisplayName "B2B Backend Port 8082" `
        -Direction Inbound `
        -LocalPort 8082 `
        -Protocol TCP `
        -Action Allow `
        -Profile Any `
        -ErrorAction Stop | Out-Null
    
    Write-Host "  -> SUCCESS! Firewall rule created." -ForegroundColor Green
} catch {
    Write-Host "  -> ERROR: Failed to create firewall rule." -ForegroundColor Red
    Write-Host "  -> $($_.Exception.Message)" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
Write-Host "Step 3: Verifying firewall rule..." -ForegroundColor Yellow
$rule = Get-NetFirewallRule -DisplayName "B2B Backend Port 8082" -ErrorAction SilentlyContinue

if ($rule -and $rule.Enabled -eq $true) {
    Write-Host "  -> Rule is active and enabled!" -ForegroundColor Green
} else {
    Write-Host "  -> WARNING: Rule exists but might not be enabled." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Step 4: Getting your computer's IP address..." -ForegroundColor Yellow
$ipAddresses = Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -like "192.168.*" }

if ($ipAddresses) {
    foreach ($ip in $ipAddresses) {
        Write-Host "  -> Found IP: $($ip.IPAddress)" -ForegroundColor Cyan
    }
} else {
    Write-Host "  -> No local IP found. Make sure WiFi is connected." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Step 5: Checking if backend is running on port 8082..." -ForegroundColor Yellow
$port8082 = netstat -ano | Select-String ":8082"

if ($port8082) {
    Write-Host "  -> Backend is RUNNING on port 8082!" -ForegroundColor Green
    Write-Host "  -> $port8082" -ForegroundColor Gray
} else {
    Write-Host "  -> Backend is NOT running!" -ForegroundColor Red
    Write-Host "  -> Start it with: cd backend ; .\start-backend.bat" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== FIX COMPLETE! ===" -ForegroundColor Green
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Cyan
Write-Host "1. Make sure backend is running (see above)" -ForegroundColor White
Write-Host "2. Connect your phone to the same WiFi" -ForegroundColor White
Write-Host "3. Test in phone browser: http://YOUR_IP:8082" -ForegroundColor White
Write-Host "4. Try adding inventory in the app" -ForegroundColor White
Write-Host ""

Read-Host "Press Enter to exit"
