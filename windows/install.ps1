#Requires -RunAsAdministrator
<#
.SYNOPSIS
  Installs LockGuard: copies the already-published app to Program Files, registers the Windows
  Service, and starts it. Run this once, as Administrator, after publishing both projects (see
  windows/README.md for the exact `dotnet publish` commands -- this script does not build them).

.DESCRIPTION
  There is deliberately no matching uninstall.ps1. Removing LockGuard is only ever done through
  its own Setup app (the "Remove LockGuard entirely" button), which requires the code -- a script
  that silently tore it all down without that check would defeat the entire point of this project.
#>

$ErrorActionPreference = "Stop"

$installDir = "$Env:ProgramFiles\LockGuard"
$serviceDir = Join-Path $installDir "Service"
$setupDir = Join-Path $installDir "Setup"
$dataDir = "$Env:ProgramData\LockGuard"

$publishedService = Join-Path $PSScriptRoot "LockGuard.Service\bin\Release\net8.0-windows\publish"
$publishedSetup = Join-Path $PSScriptRoot "LockGuard.Setup\bin\Release\net8.0-windows\publish"

if (-not (Test-Path $publishedService)) {
    throw "Publish LockGuard.Service first -- see windows/README.md. Expected files at: $publishedService"
}
if (-not (Test-Path $publishedSetup)) {
    throw "Publish LockGuard.Setup first -- see windows/README.md. Expected files at: $publishedSetup"
}

Write-Host "Stopping any existing LockGuard service..."
Stop-Service -Name LockGuard -Force -ErrorAction SilentlyContinue
sc.exe delete LockGuard 2>$null | Out-Null

Write-Host "Copying files to $installDir ..."
New-Item -ItemType Directory -Force -Path $serviceDir, $setupDir | Out-Null
Copy-Item "$publishedService\*" $serviceDir -Recurse -Force
Copy-Item "$publishedSetup\*" $setupDir -Recurse -Force

Write-Host "Registering the service..."
$exePath = Join-Path $serviceDir "LockGuard.Service.exe"
sc.exe create LockGuard binPath= "`"$exePath`"" start= auto DisplayName= "LockGuard" | Out-Null
sc.exe description LockGuard "Blocks internet access for everything except the programs you allow. Managed through LockGuard Setup." | Out-Null
sc.exe failure LockGuard reset= 0 actions= restart/5000/restart/5000/restart/5000 | Out-Null

Write-Host "Starting the service..."
Start-Service -Name LockGuard

# The data folder (code + allowed-programs list) is created by the service on first start. Lock
# it down once it exists, so a standard account can't edit it directly to bypass the lock --
# changes are only meant to happen through the code-gated pipe.
for ($i = 0; $i -lt 10 -and -not (Test-Path $dataDir); $i++) { Start-Sleep -Seconds 1 }
if (Test-Path $dataDir) {
    Write-Host "Locking down $dataDir ..."
    icacls $dataDir /inheritance:r | Out-Null
    icacls $dataDir /grant "SYSTEM:(OI)(CI)F" | Out-Null
    icacls $dataDir /grant "*S-1-5-32-544:(OI)(CI)F" | Out-Null # Administrators
} else {
    Write-Warning "The service didn't create $dataDir yet -- check its status with: Get-Service LockGuard"
}

Write-Host ""
Write-Host "Done. Run LockGuard Setup to finish setup:"
Write-Host "  $setupDir\LockGuard.Setup.exe"
Write-Host "A shortcut on the desktop makes that easier for whoever is setting this up for someone else:"
$shortcut = (New-Object -ComObject WScript.Shell).CreateShortcut("$Env:Public\Desktop\LockGuard Setup.lnk")
$shortcut.TargetPath = Join-Path $setupDir "LockGuard.Setup.exe"
$shortcut.Save()
