<#
.SYNOPSIS
  Safe NEXUS test install on the Nothing Phone (3a). Default mode is READ-ONLY.

.DESCRIPTION
  -Check      (default) Read-only baseline: device/build, default Home app, every Nothing package and
              whether it is enabled, the camera app, Glyph packages, free storage, NEXUS install state.
  -Install    Baseline, then a plain "adb install -r" of the APK (no permissions are auto-granted),
              then the same checks again and a comparison. Your data, apps and default launcher are
              not touched: NEXUS is installed as an ordinary app and is NOT made the Home app.
  -Uninstall  Removes NEXUS ("adb uninstall com.nexus.launcher") and re-checks the baseline.

  Refuses to run unless exactly one device is connected and it is a Phone (3a) (codename Asteroids*).
  Reports are written to reports\ next to this script (git-ignored). They contain package names and
  build info only - no personal data.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File nexus-phone-test.ps1
  powershell -ExecutionPolicy Bypass -File nexus-phone-test.ps1 -Install -Apk ..\..\..\dist\milestone4\NEXUS-m4-debug.apk
  powershell -ExecutionPolicy Bypass -File nexus-phone-test.ps1 -Uninstall
#>
param(
    [switch]$Install,
    [switch]$Uninstall,
    [string]$Apk = "",
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    # Uninstall even while NEXUS is the default Home app (Android then picks/asks for a launcher).
    [switch]$Force,
    # Test mode: run the full flow against an Android EMULATOR (ro.kernel.qemu=1) to check the script itself.
    # Never relaxes the checks for a real phone.
    [switch]$AllowEmulator
)

$ErrorActionPreference = "Stop"
$Pkg = "com.nexus.launcher"

function Q([string]$cmd) {
    # stdin keeps quoting intact on Windows PowerShell 5.1; stderr must not abort the run
    $ErrorActionPreference = "Continue"
    $out = $cmd | & $Adb shell 2>$null
    return ($out -join "`n").Trim()
}

if (-not (Test-Path $Adb)) { throw "adb not found at $Adb (install Android SDK Platform-Tools)" }
$devices = @((& $Adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" })
if ($devices.Count -ne 1) { throw "Connect exactly one phone with USB debugging authorised (found $($devices.Count))." }
$maker = Q "getprop ro.product.manufacturer"
$device = Q "getprop ro.product.device"
$name = Q "getprop ro.product.name"
$model = Q "getprop ro.product.model"
$isEmulator = (Q "getprop ro.kernel.qemu") -eq "1"
Write-Host "Connected: manufacturer='$maker' device='$device' name='$name' model='$model' emulator=$isEmulator"
$isPhone3a = (-not $isEmulator) -and ($maker -like "Nothing*") -and
    (($device -like "Asteroids*") -or ($name -like "Asteroids*") -or ($model -like "A059*"))
if ($isEmulator -and -not $AllowEmulator) { throw "This is an emulator, not the phone (use -AllowEmulator only to test the script)." }
if (-not $isEmulator -and -not $isPhone3a) { throw "This script is only for the Nothing Phone (3a) (Nothing / Asteroids / A059). Nothing was changed." }
if ($isEmulator) { Write-Host "TEST MODE: emulator, not your phone." -ForegroundColor Yellow }

function Snapshot {
    $s = [ordered]@{}
    $s.Build = Q "getprop ro.build.display.id"
    $s.Android = Q "getprop ro.build.version.release"
    $s.HomeApp = Q "cmd role get-role-holders android.app.role.HOME"
    $s.DialerApp = Q "cmd role get-role-holders android.app.role.DIALER"
    $s.CameraApp = Q "cmd package resolve-activity --brief -a android.media.action.STILL_IMAGE_CAMERA | tail -n 1"
    $s.NothingPackages = Q "pm list packages com.nothing | sort"
    $s.NothingDisabled = Q "pm list packages -d com.nothing | sort"
    $s.GlyphPackages = Q "pm list packages | grep -i glyph | sort"
    $s.FreeData = Q "df -h /data | tail -n 1"
    $s.NexusInstalled = Q "pm list packages $Pkg"
    $s.NexusVersion = Q "dumpsys package $Pkg | grep -m1 versionName"
    $s.NexusNotifAccess = Q "settings get secure enabled_notification_listeners | grep -c $Pkg"
    $s.NexusAccessibility = Q "settings get secure enabled_accessibility_services | grep -c $Pkg"
    $s.NexusOverlay = Q "appops get $Pkg SYSTEM_ALERT_WINDOW"
    return $s
}

function Show($title, $s) {
    Write-Host "`n=== $title ===" -ForegroundColor Cyan
    foreach ($k in $s.Keys) { Write-Host ("{0,-20} {1}" -f $k, ($s[$k] -replace "`n", "`n                     ")) }
}

$dir = Join-Path $PSScriptRoot "reports"
New-Item -ItemType Directory -Force $dir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"

$before = Snapshot
Show "Before" $before
($before.GetEnumerator() | ForEach-Object { "$($_.Key): $($_.Value)" }) | Set-Content (Join-Path $dir "$stamp-before.txt") -Encoding utf8

if (-not $Install -and -not $Uninstall) {
    Write-Host "`nRead-only check done. Nothing was changed. Use -Install -Apk <path> to install NEXUS." -ForegroundColor Green
    exit 0
}

if ($Install) {
    if (-not $Apk -or -not (Test-Path $Apk)) { throw "Pass -Apk <path to NEXUS APK>." }
    # Refuse anything that isn't the NEXUS app (wrong file, renamed APK, other app).
    $aapt = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools\*\aapt2.exe" -ErrorAction SilentlyContinue | Sort-Object FullName | Select-Object -Last 1
    if (-not $aapt) { throw "aapt2 not found in the Android SDK build-tools; can't verify the APK, so nothing was installed." }
    $badging = (& $aapt.FullName dump badging "$Apk" 2>$null | Select-Object -First 1)
    if ($badging -notmatch "name='$Pkg'") { throw "That APK is not NEXUS ($badging). Nothing was installed." }
    Write-Host "APK verified: $badging"
    Write-Host "`nInstalling $Apk as an ordinary app (no permissions granted, launcher NOT changed)..."
    $ErrorActionPreference = "Continue"
    $res = & $Adb install -r "$Apk" 2>&1
    $ErrorActionPreference = "Stop"
    Write-Host ($res -join "`n")
    if (($res -join "`n") -notmatch "Success") { throw "Install failed - nothing else was changed." }
}
if ($Uninstall) {
    if ($before.HomeApp -match $Pkg -and -not $Force) {
        throw "NEXUS is your default Home app. Switch back first (Settings > Apps > Default apps > Home app > Nothing Launcher), then run -Uninstall again. Nothing was changed."
    }
    Write-Host "`nUninstalling NEXUS..."
    & $Adb uninstall $Pkg
}

$after = Snapshot
Show "After" $after
($after.GetEnumerator() | ForEach-Object { "$($_.Key): $($_.Value)" }) | Set-Content (Join-Path $dir "$stamp-after.txt") -Encoding utf8

# What must never change because of NEXUS
$problems = @()
foreach ($k in @("HomeApp", "DialerApp", "CameraApp", "NothingPackages", "NothingDisabled", "GlyphPackages")) {
    if ($before[$k] -ne $after[$k]) { $problems += "$k changed: '$($before[$k])' -> '$($after[$k])'" }
}
if ($Install) {
    if ($after.NexusNotifAccess -ne "0") { $problems += "NEXUS has notification access (should be off for initial testing)" }
    if ($after.NexusAccessibility -ne "0") { $problems += "NEXUS accessibility helper is on (should be off)" }
    if ($after.NexusOverlay -match "allow") { $problems += "NEXUS can draw over apps (should be off)" }
}

if ($problems.Count -eq 0) {
    Write-Host "`nPASS: default Home/Phone/Camera apps, Nothing packages and Glyph packages are unchanged;" -ForegroundColor Green
    if ($Install) { Write-Host "      NEXUS is installed with no optional permissions. Open 'NEXUS Studio' from your app list to try it." -ForegroundColor Green }
} else {
    Write-Host "`nCHECK THESE:" -ForegroundColor Yellow
    $problems | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow }
}
Write-Host "Undo at any time: powershell -ExecutionPolicy Bypass -File nexus-phone-test.ps1 -Uninstall"
if ($problems.Count -gt 0) { exit 1 }
