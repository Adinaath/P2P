param (
    [string]$ApkPath = "$PSScriptRoot\AirborneMesh.apk"
)

$adb = "D:\Android\platform-tools\adb.exe"

if (-not (Test-Path $adb)) {
    Write-Error "ADB executable not found at $adb"
    exit 1
}

if (-not (Test-Path $ApkPath)) {
    Write-Error "APK file not found at $ApkPath"
    exit 1
}

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " Airborne Mesh: Android APK Deployment   " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "APK Location: $ApkPath ($( [math]::Round((Get-Item $ApkPath).Length / 1MB, 2) ) MB)" -ForegroundColor Yellow
Write-Host ""

Write-Host "Checking for connected Android devices via ADB..." -ForegroundColor White
$deviceLines = & $adb devices | Where-Object { $_ -match "\tdevice$" }

if (-not $deviceLines) {
    Write-Host "⚠️  No authorized Android devices currently connected via ADB." -ForegroundColor Red
    Write-Host ""
    Write-Host "To connect your physical phone:" -ForegroundColor Yellow
    Write-Host "  1. On your phone, go to Settings -> About Phone -> Tap 'Build Number' 7 times to enable Developer Options." -ForegroundColor White
    Write-Host "  2. Go to Settings -> Developer Options -> Enable 'USB Debugging'." -ForegroundColor White
    Write-Host "  3. Connect your phone via USB cable to this PC." -ForegroundColor White
    Write-Host "  4. On the phone screen prompt, select 'Always allow from this computer' and tap 'Allow'." -ForegroundColor White
    Write-Host "  5. Re-run: .\deploy.ps1" -ForegroundColor Green
    Write-Host ""
    Write-Host "Wireless ADB Option:" -ForegroundColor Yellow
    Write-Host "  If on Android 11+ on same Wi-Fi: Developer Options -> Wireless debugging -> Pair device with pairing code." -ForegroundColor White
    Write-Host "  Then run: & '$adb' connect <IP>:<PORT>" -ForegroundColor White
    exit 0
}

foreach ($line in $deviceLines) {
    $deviceId = ($line -split "\t")[0]
    Write-Host "📲 Found device: $deviceId" -ForegroundColor Green
    Write-Host "   Installing AirborneMesh.apk..." -ForegroundColor White
    $installResult = & $adb -s $deviceId install -r $ApkPath
    Write-Host "   Result: $installResult" -ForegroundColor Cyan

    Write-Host "   Launching Airborne Mesh (MainActivity)..." -ForegroundColor White
    & $adb -s $deviceId shell am start -n org.airbornemesh/.MainActivity
    Write-Host "   ✓ Airborne Mesh is now running on $deviceId!" -ForegroundColor Green
    Write-Host ""
}
