# Builds the Android app against this PC's current LAN IP and installs it on the
# phone connected over ADB (USB debugging or wireless debugging).
#   powershell -ExecutionPolicy Bypass -File INSTALL-APP.ps1
#   powershell -ExecutionPolicy Bypass -File INSTALL-APP.ps1 -SkipBuild
param([switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$apk = Join-Path $root 'android\app\build\outputs\apk\debug\app-debug.apk'

# LAN IP = the IPv4 address of the adapter that has a default gateway.
$ip = (Get-NetIPConfiguration | Where-Object { $_.IPv4DefaultGateway -and $_.NetAdapter.Status -eq 'Up' } |
       Select-Object -First 1).IPv4Address.IPAddress
if (-not $ip) { throw 'Could not detect the LAN IP. Is Wi-Fi/Ethernet connected?' }
Write-Host "Backend URL: http://${ip}:8082/" -ForegroundColor Cyan

if (-not $SkipBuild) {
    Push-Location (Join-Path $root 'android')
    try {
        & .\gradlew.bat assembleDebug "-PapiBaseUrl=http://${ip}:8082/" --console=plain -q
        if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed' }
    } finally { Pop-Location }
}

Write-Host 'Waiting for phone (unlock it and allow USB debugging)...' -ForegroundColor Yellow
adb wait-for-device
adb devices -l

adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'adb install failed (on iQOO/vivo enable "Install via USB" in Developer options)' }

adb shell monkey -p com.b2binventory.app -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host 'Installed and launched.' -ForegroundColor Green
