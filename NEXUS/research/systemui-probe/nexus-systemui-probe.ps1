<#
.SYNOPSIS
  READ-ONLY probe of the Nothing Phone (3a)'s system UI theming surface. Changes nothing.

.DESCRIPTION
  Runs only "get"/"list"/"query" commands over ADB and writes a text report next to this script
  (reports\<build>.txt, git-ignored). The report answers, for YOUR exact firmware:
    - which SystemUI / Settings / Nothing packages and versions are installed
    - which resource overlays exist and which are enabled/immutable (cmd overlay list)
    - whether the Material You seed key (theme_customization_overlay_packages) is in use
    - which Quick Settings tiles are configured (sysui_qs_tiles)
    - which lock-screen clock key is set (lock_screen_custom_clock_face)
    - Nothing-specific settings keys mentioning glyph / nt_ / essential (names and values)
    - display refresh modes (for animation frame pacing)
  It deliberately does NOT read notifications, messages, accounts, contacts or app data.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File nexus-systemui-probe.ps1
#>
param([string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe")

$ErrorActionPreference = "Stop"
if (-not (Test-Path $Adb)) { throw "adb not found at $Adb (install Android SDK Platform-Tools)" }
$devices = (& $Adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" }
if (@($devices).Count -ne 1) { throw "Connect exactly one phone with USB debugging authorised (found $(@($devices).Count))." }

function Q([string]$cmd) {
    # stdin keeps quoting intact on Windows PowerShell 5.1
    $out = $cmd | & $Adb shell 2>&1
    return ($out -join "`n").TrimEnd()
}

$device = Q "getprop ro.product.device"
if ($device -notlike "Asteroids*") { throw "Probe is scoped to the Nothing Phone (3a) (Asteroids*); found '$device'." }

$sections = [ordered]@{
    "Build"                     = "for p in ro.product.model ro.product.device ro.build.fingerprint ro.build.display.id ro.build.version.release ro.build.version.sdk ro.build.version.security_patch ro.boot.verifiedbootstate ro.boot.flash.locked ro.boot.vbmeta.device_state; do echo `"`$p=`$(getprop `$p)`"; done"
    "Key packages"              = "pm list packages -f | grep -iE 'systemui|android.settings|com.nothing|launcher|camera|wallpaper|themepicker|glyph|essential'"
    "SystemUI version"          = "dumpsys package com.android.systemui | grep -E 'versionName|versionCode|codePath' | head -n 6"
    "Settings version"          = "dumpsys package com.android.settings | grep -E 'versionName|versionCode|codePath' | head -n 6"
    "Overlays (state, mutability)" = "cmd overlay list"
    "Material You seed key"     = "settings get secure theme_customization_overlay_packages"
    "QS tiles configured"       = "settings get secure sysui_qs_tiles"
    "Lock-screen clock key"     = "settings get secure lock_screen_custom_clock_face"
    "Night mode"                = "cmd uimode night"
    "Nothing keys (global)"     = "settings list global | grep -iE 'glyph|^nt_|nothing|essential'"
    "Nothing keys (secure)"     = "settings list secure | grep -iE 'glyph|^nt_|nothing|essential'"
    "Nothing keys (system)"     = "settings list system | grep -iE 'glyph|^nt_|nothing|essential'"
    "Display modes"             = "dumpsys display | grep -iE 'supportedModes|mActiveModeId|refreshRate' | head -n 12"
    "Exported Settings actions NEXUS uses" = "for a in android.settings.TETHER_SETTINGS android.settings.ZEN_MODE_SETTINGS android.settings.LOCK_SCREEN_SETTINGS android.settings.ALL_APPS_NOTIFICATION_SETTINGS android.settings.DARK_THEME_SETTINGS; do echo `"== `$a`"; cmd package resolve-activity --brief -a `$a | tail -n 1; done"
}

$fp = (Q "getprop ro.build.display.id") -replace '[^A-Za-z0-9._-]', '_'
$dir = Join-Path $PSScriptRoot "reports"
New-Item -ItemType Directory -Force $dir | Out-Null
$file = Join-Path $dir "$fp.txt"
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("NEXUS SystemUI probe - read-only - $(Get-Date -Format s)")
foreach ($k in $sections.Keys) {
    Write-Host "== $k"
    [void]$sb.AppendLine("`n===== $k =====")
    [void]$sb.AppendLine((Q $sections[$k]))
}
Set-Content -Path $file -Value $sb.ToString() -Encoding utf8
Write-Host "`nReport: $file"
Write-Host "Nothing on the phone was changed. The report holds system package names and settings keys only (no messages, notifications or app data). Skim it, then share it to plan the next step."
