# Puts the MDM server on the Raspberry Pi and starts it. Run on the PC (PowerShell), once the Pi can be reached over SSH.
#   .\pi\deploy.ps1 -Target "fe80::1234:abcd%12" -BackupUrl "https://mdm.yourname.workers.dev"
# -Target is the Pi's address (the USB link-local address with its %interface, or its Tailscale name / LAN address).
# The settings (random secrets, the dashboard password, the Pi's public address) are made the first time and kept in
# Documents\MDM-Pi-settings.txt, so a second run updates the Pi without changing them.
param(
  [Parameter(Mandatory)][string]$Target,
  [string]$User = 'mdm',
  [string]$Key = "$env:USERPROFILE\.ssh\id_ed25519",
  [string]$BackupUrl = '',
  [string]$AdminPassword = '',
  [switch]$SkipTailscale
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$settings = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'MDM-Pi-settings.txt'
$sshOpts = @('-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=accept-new', '-o', 'ConnectTimeout=15', '-i', $Key)
$sshDest = "$User@$Target" # ssh takes an IPv6 address with its %interface as it is; scp wants it in [brackets]
$scpDest = if ($Target.Contains(':')) { "$User@[$Target]" } else { "$User@$Target" }

function Run-Ssh([string]$cmd) { & ssh @sshOpts $sshDest $cmd }
function Random-Text([int]$n) { -join ((1..$n) | ForEach-Object { 'abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'[(Get-Random -Maximum 55)] }) }

# --- settings (made once) ---
$vars = @{}
if (Test-Path $settings) {
  foreach ($l in Get-Content $settings) { if ($l -match '^([A-Z_]+)=(.*)$') { $vars[$Matches[1]] = $Matches[2] } }
}
if ($AdminPassword) { $vars.ADMIN_PASSWORD = $AdminPassword }
if (-not $vars.ADMIN_PASSWORD) { $vars.ADMIN_PASSWORD = Random-Text 20 }
if (-not $vars.REPLICA_SECRET) { $vars.REPLICA_SECRET = Random-Text 40 }
if ($BackupUrl) { $vars.BACKUP_URL = $BackupUrl.TrimEnd('/') }
if (-not $vars.ROLE) { $vars.ROLE = 'main' }
if (-not $vars.GITHUB_REPO) { $vars.GITHUB_REPO = 'abegumbo-ui/MDM' }
if (-not $vars.BACKUP_URL) { $vars.BACKUP_URL = 'https://mdm.example.workers.dev' }

function Settings-Lines {
  $out = @('# MDM server on the Raspberry Pi: these are the settings it uses. Keep this file private.')
  foreach ($k in 'ADMIN_PASSWORD', 'ROLE', 'PRIMARY_URL', 'BACKUP_URL', 'PUBLIC_URL', 'REPLICA_SECRET', 'GITHUB_REPO') { $out += "$k=$($vars[$k])" }
  $out + @('PORT=8787', 'HOST=127.0.0.1', 'DATA_DIR=/var/lib/mdm-pi')
}
Set-Content -Path $settings -Value (Settings-Lines) -Encoding ASCII
Write-Host "settings file: $settings"

# --- what goes to the Pi ---
$work = Join-Path $env:TEMP 'mdm-pi-deploy'
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
$pkg = Join-Path $work 'mdm-pi-src'
New-Item -ItemType Directory -Force "$pkg\src", "$pkg\pi" | Out-Null
Copy-Item "$root\src\*.js" "$pkg\src\"
Copy-Item "$root\pi\*" "$pkg\pi\" -Exclude 'deploy.ps1'
# Files for Linux: plain line endings.
$envText = ((Settings-Lines) -join "`n") + "`n"
[IO.File]::WriteAllText((Join-Path $work 'mdm-pi.env'), $envText)
foreach ($f in Get-ChildItem "$pkg\pi\*" -Include '*.sh', '*.service', '*.mjs', '*.example') {
  [IO.File]::WriteAllText($f.FullName, ([IO.File]::ReadAllText($f.FullName)).Replace("`r`n", "`n"))
}
$tar = Join-Path $work 'mdm-pi.tar'
Push-Location $work; & tar -cf $tar 'mdm-pi-src'; Pop-Location

Write-Host "copying the program to the Pi..."
& scp @sshOpts $tar (Join-Path $work 'mdm-pi.env') "${scpDest}:/tmp/"
if ($LASTEXITCODE -ne 0) { throw 'could not copy to the Pi' }
Run-Ssh 'cd /tmp && rm -rf mdm-pi-src && tar -xf mdm-pi.tar && sudo bash /tmp/mdm-pi-src/pi/install.sh'
if ($LASTEXITCODE -ne 0) { throw 'the install on the Pi failed' }

$code = 0
if (-not $SkipTailscale) {
  Write-Host "setting up the public address (Tailscale)..."
  $lines = @()
  Run-Ssh 'sudo bash /tmp/mdm-pi-src/pi/tailscale-setup.sh' 2>&1 | ForEach-Object { Write-Host $_; $lines += [string]$_ }
  $code = $LASTEXITCODE
  $hit = $lines | Select-String 'PUBLIC-ADDRESS=(\S+)' | Select-Object -First 1
  if ($hit) {
    # Remembered, so a later deploy does not put an empty address back.
    $vars.PUBLIC_URL = $hit.Matches[0].Groups[1].Value
    $vars.PRIMARY_URL = $vars.PUBLIC_URL
    Set-Content -Path $settings -Value (Settings-Lines) -Encoding ASCII
    Write-Host "public address: $($vars.PUBLIC_URL)"
  }
  Write-Host "(setup exit code ${code}: 0 = done, 2 = a login link was printed above, 3 = a link to enable Funnel was printed above)"
}
Write-Host "done. The dashboard password and the Cloudflare secret are in: $settings"
exit $code
