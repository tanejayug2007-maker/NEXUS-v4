<#
.SYNOPSIS
  Measures NEXUS app-drawer smoothness on the Nothing Phone (3a). Read-only: changes nothing.

.DESCRIPTION
  Uses only Android's own counters:
    - dumpsys gfxinfo   frame count, janky frames, frame-time percentiles, missed vsync, slow UI thread
    - dumpsys meminfo   NEXUS memory (PSS) after the run
    - logcat NexusPerf  per-gesture lines written by NEXUS v1.5.1+ when Studio > Performance >
                        Frame stats is ON: touch-to-frame latency, release velocity/decision,
                        and how long each open/close took to settle. (M6 has no such lines; its
                        gfxinfo numbers are still comparable.)

  Run it once with the old build and once with the new one, with the same theme/settings,
  doing the same swipes. Reports go to reports\perf-<label>-<time>.txt next to this script.
  It refuses to run unless exactly one Phone (3a) is connected (or an emulator with -AllowEmulator).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File nexus-perf.ps1 -Label m6
  powershell -ExecutionPolicy Bypass -File nexus-perf.ps1 -Label v151
#>
param(
    [string]$Label = "run",
    [int]$Swipes = 20,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [switch]$AllowEmulator
)

$ErrorActionPreference = "Stop"
$Pkg = "com.nexus.launcher"

function Q([string]$cmd) {
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
$isPhone3a = (-not $isEmulator) -and ($maker -like "Nothing*") -and
    (($device -like "Asteroids*") -or ($name -like "Asteroids*") -or ($model -like "A059*"))
if ($isEmulator -and -not $AllowEmulator) { throw "This is an emulator (use -AllowEmulator to test the script)." }
if (-not $isEmulator -and -not $isPhone3a) { throw "Only for the Nothing Phone (3a). Nothing was changed." }
if (-not (Q "pm list packages $Pkg")) { throw "NEXUS is not installed." }

$version = Q "dumpsys package $Pkg | grep -m1 versionName"
$refresh = Q "dumpsys display | grep -m1 -o 'renderFrameRate [0-9.]*'"
Write-Host "NEXUS $version on $model ($refresh)" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Unlock the phone and go to the NEXUS home screen (same page every run, e.g. Orbit)."
Write-Host "2. For a per-gesture breakdown (v1.5.1+): NEXUS Studio > Performance > Frame stats ON."
Read-Host "Press Enter when you are on the home screen"

Q "dumpsys gfxinfo $Pkg reset" | Out-Null
& $Adb logcat -c 2>$null
Write-Host ""
Write-Host "NOW: do $Swipes QUICK flicks up to open the drawer, each followed by a quick flick down to close it." -ForegroundColor Yellow
Write-Host "     Then 5 slow drags up and down, then scroll the app list fast a few times."
Read-Host "Press Enter when finished"

$gfx = Q "dumpsys gfxinfo $Pkg"
$mem = Q "dumpsys meminfo $Pkg | grep -E 'TOTAL PSS|TOTAL:' | head -n 1"
$log = (& $Adb logcat -d -s NexusPerf:I 2>$null) -join "`n"

function Pick([string]$pattern) {
    $m = [regex]::Match($gfx, $pattern)
    if ($m.Success) { return $m.Groups[1].Value } else { return "n/a" }
}
$summary = [ordered]@{
    "Frames rendered"     = Pick "Total frames rendered: (\d+)"
    "Janky frames"        = Pick "Janky frames: (\d+ \([\d.]+%\))"
    "50th pct frame"      = Pick "50th percentile: (\d+ms)"
    "90th pct frame"      = Pick "90th percentile: (\d+ms)"
    "95th pct frame"      = Pick "95th percentile: (\d+ms)"
    "99th pct frame"      = Pick "99th percentile: (\d+ms)"
    "Missed vsync"        = Pick "Number Missed Vsync: (\d+)"
    "Slow UI thread"      = Pick "Number Slow UI thread: (\d+)"
    "Slow draw commands"  = Pick "Number Slow issue draw commands: (\d+)"
    "Memory"              = $mem
}

function Stats([string]$key) {
    $vals = @([regex]::Matches($log, "$key=(-?\d+)") | ForEach-Object { [int]$_.Groups[1].Value } | Sort-Object)
    if ($vals.Count -eq 0) { return "n/a (enable Frame stats on v1.5.1+)" }
    $med = $vals[[int][math]::Floor(($vals.Count - 1) / 2)]
    $p90 = $vals[[int][math]::Floor(($vals.Count - 1) * 0.9)]
    return "n=$($vals.Count) median=${med}ms p90=${p90}ms max=$($vals[-1])ms"
}
$opens = @([regex]::Matches($log, "release progress=[\d.]+ vy=-?\d+ open=(true|false)"))
$fast = @($opens | Where-Object { [int]([regex]::Match($_.Value, "vy=(-?\d+)").Groups[1].Value) -lt -1000 })
$fastOpened = @($fast | Where-Object { $_.Groups[1].Value -eq "true" })
$summary["Touch-to-frame"] = Stats "touch-to-frame ms"
$summary["Open settle"] = Stats "settle open=true ms"
$summary["Close settle"] = Stats "settle open=false ms"
$summary["Fast flicks opened"] = if ($fast.Count) { "$($fastOpened.Count) / $($fast.Count) (release faster than 1000 px/s up)" } else { "n/a" }

Write-Host ""
Write-Host "=== $Label : NEXUS $version ===" -ForegroundColor Cyan
foreach ($k in $summary.Keys) { Write-Host ("{0,-20} {1}" -f $k, $summary[$k]) }

$dir = Join-Path $PSScriptRoot "reports"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$file = Join-Path $dir ("perf-{0}-{1}.txt" -f $Label, (Get-Date -Format "yyyyMMdd-HHmmss"))
$body = @("NEXUS perf run '$Label'  $version  $model  $refresh", "")
foreach ($k in $summary.Keys) { $body += ("{0,-20} {1}" -f $k, $summary[$k]) }
$body += @("", "---- NexusPerf log ----", $log, "", "---- dumpsys gfxinfo ----", $gfx)
$body | Set-Content -Encoding UTF8 $file
Write-Host ""
Write-Host "Saved $file"
