<#
.SYNOPSIS
  Zips the NEXUS project source (everything git tracks or would track; no build outputs,
  local.properties or IDE caches) into a ZIP whose root folder is "NEXUS".
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\make-source-zip.ps1 -Out ..\dist\NEXUS-source.zip
#>
param([Parameter(Mandatory = $true)][string]$Out)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
$root = Split-Path -Parent $PSScriptRoot
$Out = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Out))

Push-Location $root
try { $files = git ls-files --cached --others --exclude-standard } finally { Pop-Location }
if (-not $files) { throw "git returned no files - run from inside the NEXUS repository" }

New-Item -ItemType Directory -Force (Split-Path -Parent $Out) | Out-Null
if (Test-Path $Out) { Remove-Item $Out -Confirm:$false }
$zip = [System.IO.Compression.ZipFile]::Open($Out, "Create")
try {
    foreach ($f in $files) {
        $src = Join-Path $root $f
        if (Test-Path $src -PathType Leaf) {
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $src, "NEXUS/" + $f) | Out-Null
        }
    }
} finally { $zip.Dispose() }
Write-Host "Wrote $Out ($(@($files).Count) files)"
