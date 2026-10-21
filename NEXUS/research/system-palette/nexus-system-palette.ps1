<#
.SYNOPSIS
  EXPERIMENT (research track, not part of the NEXUS APK): ask Android's own Material You theme
  engine to use the NEXUS cyan as the system seed colour, so the REAL Quick Settings, notification
  shade, Settings and Material You apps pick up NEXUS accents. No root, no bootloader unlock, no
  system files touched: it writes one Secure setting that Settings > Wallpaper & style normally writes.

.DESCRIPTION
  -Check    (default) Print device/build info and the current value. Changes nothing.
  -Apply    Back up the current value to a file, then write the NEXUS palette.
  -Restore  Write the backed-up value back (or delete the key if it was unset).

  Status: UNVERIFIED on Nothing OS 4.1. Nothing OS may ignore or overwrite this key (for example
  when you change wallpaper or pick a colour in Nothing's own settings). If nothing changes,
  run -Restore and treat the experiment as failed. Worst case is a colour you don't like:
  choosing any colour in Settings > Wallpaper & style also overwrites it.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File nexus-system-palette.ps1 -Check
  powershell -ExecutionPolicy Bypass -File nexus-system-palette.ps1 -Apply -Seed 00E5FF -Style VIBRANT
  powershell -ExecutionPolicy Bypass -File nexus-system-palette.ps1 -Restore
#>
param(
    [switch]$Apply,
    [switch]$Restore,
    [string]$Seed = "00E5FF",
    [ValidateSet("TONAL_SPOT", "VIBRANT", "EXPRESSIVE", "SPRITZ", "RAINBOW", "FRUIT_SALAD", "MONOCHROMATIC", "FIDELITY", "CONTENT")]
    [string]$Style = "VIBRANT",
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$Backup = "$PSScriptRoot\palette-backup.txt"
)

$ErrorActionPreference = "Stop"
$Key = "theme_customization_overlay_packages"

function Invoke-Device([string]$cmd) {
    # Commands go through stdin so PowerShell 5.1 never mangles the JSON quotes.
    $out = $cmd | & $Adb shell
    if ($LASTEXITCODE -ne 0) { throw "adb shell failed: $cmd" }
    return ($out -join "`n").Trim()
}

if (-not (Test-Path $Adb)) { throw "adb not found at $Adb (install Android SDK Platform-Tools)" }
$devices = (& $Adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" }
if (@($devices).Count -ne 1) { throw "Connect exactly one phone with USB debugging authorised (found $(@($devices).Count))." }

$device = Invoke-Device "getprop ro.product.device"
$model = Invoke-Device "getprop ro.product.model"
$sdk = Invoke-Device "getprop ro.build.version.sdk"
$fingerprint = Invoke-Device "getprop ro.build.fingerprint"
Write-Host "Device:      $model ($device), API $sdk"
Write-Host "Build:       $fingerprint"
if ($device -notlike "Asteroids*") { throw "This script is only for the Nothing Phone (3a) (codename Asteroids*); found '$device'." }
if ([int]$sdk -lt 31) { throw "Needs Android 12+ (API 31); found $sdk." }

$current = Invoke-Device "settings get secure $Key"
Write-Host "Current value: $current"

if ($Restore) {
    if (-not (Test-Path $Backup)) { throw "No backup at $Backup - nothing to restore." }
    $old = (Get-Content $Backup -Raw).Trim()
    if ($old -eq "null" -or $old -eq "") {
        Invoke-Device "settings delete secure $Key" | Out-Null
    } else {
        Invoke-Device "settings put secure $Key '$old'" | Out-Null
    }
    Write-Host "Restored. Now: $(Invoke-Device "settings get secure $Key")"
    exit 0
}

if (-not $Apply) { Write-Host "`nCheck only. Use -Apply to try the NEXUS palette, -Restore to undo."; exit 0 }

if ($Seed -notmatch "^[0-9A-Fa-f]{6}$") { throw "Seed must be 6 hex digits, e.g. 00E5FF" }
if (-not (Test-Path $Backup)) {
    Set-Content -Path $Backup -Value $current -Encoding ascii
    Write-Host "Backed up current value to $Backup"
} else {
    Write-Host "Keeping existing backup $Backup (from before the first -Apply)"
}

$json = '{"android.theme.customization.system_palette":"' + $Seed.ToUpper() + '",' +
        '"android.theme.customization.accent_color":"' + $Seed.ToUpper() + '",' +
        '"android.theme.customization.color_source":"preset",' +
        '"android.theme.customization.theme_style":"' + $Style + '",' +
        '"_applied_timestamp":' + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() + '}'
Invoke-Device "settings put secure $Key '$json'" | Out-Null
Write-Host "Applied. Now: $(Invoke-Device "settings get secure $Key")"
Write-Host "Pull down Quick Settings and open Settings to see whether Nothing OS honoured it."
Write-Host "Undo any time with -Restore."
