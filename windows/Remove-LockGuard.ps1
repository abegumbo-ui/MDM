# Removes LockGuard completely and puts this computer back exactly as it was, without needing
# LockGuard's code or the dashboard -- for when LockGuard itself is broken or the code is lost.
# Run as Administrator (Remove-LockGuard.cmd does that for you). Removes ONLY what LockGuard
# added; nothing else on the computer is touched or deleted.
#   1. stops and removes the LockGuard service
#   2. points the network back at the lookup servers it used before (saved by LockGuard)
#   3. removes LockGuard's firewall rules and turns outgoing traffic back to "allowed"
#   4. removes the browser policies LockGuard set, restoring any that were there before
#   5. deletes LockGuard's own folders (Program Files\LockGuard, ProgramData\LockGuard)

$ErrorActionPreference = 'Continue'
# LockGuard protects itself from administrators, so this hands itself to the system (a one-off
# scheduled task) and shows what it did. Only an administrator can do that.
if ([Security.Principal.WindowsIdentity]::GetCurrent().User.Value -ne 'S-1-5-18') {
    $log = Join-Path $env:TEMP 'remove-lockguard.log'
    Remove-Item $log -ErrorAction SilentlyContinue
    # A tiny launcher file, so the task's command line has no tricky quoting.
    $launcher = Join-Path $env:TEMP 'lg-remove.cmd'
    Set-Content -Path $launcher -Encoding ASCII -Value "@powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`" > `"$log`" 2>&1"
    schtasks.exe /create /tn LockGuardRemove /tr $launcher /sc once /st 00:00 /ru SYSTEM /rl HIGHEST /f | Out-Null
    schtasks.exe /run /tn LockGuardRemove | Out-Null
    Write-Host 'Removing LockGuard (as the system)...'
    for ($i = 0; $i -lt 90; $i++) { Start-Sleep 2; if ((Test-Path $log) -and (Select-String -Path $log -Pattern '^Done\.' -Quiet)) { break } }
    schtasks.exe /delete /tn LockGuardRemove /f | Out-Null
    Remove-Item $launcher -ErrorAction SilentlyContinue
    if (Test-Path $log) { Get-Content $log }
    exit
}
$data = Join-Path $env:ProgramData 'LockGuard'
Write-Host 'Removing LockGuard...'

# 1. the service (normal rights back first)
sc.exe sdset LockGuard "D:(A;;CCLCSWRPWPDTLOCRRC;;;SY)(A;;CCDCLCSWRPWPDTLOCRSDRCWDWO;;;BA)(A;;CCLCSWLOCRRC;;;IU)(A;;CCLCSWLOCRRC;;;SU)" | Out-Null
Stop-Service -Name LockGuard -Force -ErrorAction SilentlyContinue
sc.exe delete LockGuard | Out-Null
Get-Process -Name 'LockGuard.Service','LockGuard.Setup' -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

# 2. network lookups
$origFile = Join-Path $data 'dns-original.json'
$restored = @{}
if (Test-Path $origFile) {
    $orig = Get-Content $origFile -Raw | ConvertFrom-Json
    foreach ($p in $orig.PSObject.Properties) {
        $o = $p.Value
        $ad = Get-NetAdapter | Where-Object { $_.InterfaceGuid -eq $o.Id -or $_.Name -eq $o.Name } | Select-Object -First 1
        if (-not $ad) { continue }
        if ($o.V4 -and $o.V4.Count -gt 0) { Set-DnsClientServerAddress -InterfaceIndex $ad.ifIndex -ServerAddresses @($o.V4 + @($o.V6 | Where-Object { $_ })) }
        else { Set-DnsClientServerAddress -InterfaceIndex $ad.ifIndex -ResetServerAddresses }
        $restored[$ad.ifIndex] = $true
        Write-Host "  network '$($ad.Name)' put back"
    }
}
# Anything still pointing at LockGuard's filter (127.0.0.1 / ::1) goes back to automatic.
foreach ($ad in Get-NetAdapter) {
    if ($restored[$ad.ifIndex]) { continue }
    $dns = (Get-DnsClientServerAddress -InterfaceIndex $ad.ifIndex -ErrorAction SilentlyContinue).ServerAddresses
    if ($dns -contains '127.0.0.1' -or $dns -contains '::1') {
        Set-DnsClientServerAddress -InterfaceIndex $ad.ifIndex -ResetServerAddresses
        Write-Host "  network '$($ad.Name)' set back to automatic"
    }
}

# 3. firewall
Get-NetFirewallRule -ErrorAction SilentlyContinue | Where-Object { $_.DisplayName -like 'LockGuard-*' -or $_.Name -like 'LockGuard-*' } | Remove-NetFirewallRule -ErrorAction SilentlyContinue
Set-NetFirewallProfile -Profile Domain,Private,Public -DefaultOutboundAction Allow
Write-Host '  firewall rules removed'

# 4. browser policies
$owned = Join-Path $data 'policies-owned.json'
$backup = Join-Path $data 'policies-backup.json'
if (Test-Path $owned) {
    foreach ($item in (Get-Content $owned -Raw | ConvertFrom-Json)) {
        $key = 'HKLM:\' + $item[0]
        if ($item[1] -eq '') { Remove-Item -Path $key -Recurse -Force -ErrorAction SilentlyContinue }
        else { Remove-ItemProperty -Path $key -Name $item[1] -ErrorAction SilentlyContinue }
    }
}
if (Test-Path $backup) {
    $b = Get-Content $backup -Raw | ConvertFrom-Json
    foreach ($p in $b.PSObject.Properties) {
        $parts = $p.Name.Split('|')
        $key = 'HKLM:\' + $parts[0]
        $was = $p.Value
        if (-not $was.Existed) { continue }
        New-Item -Path $key -Force | Out-Null
        if ($parts[1] -eq '') {
            for ($i = 0; $i -lt $was.List.Count; $i++) { New-ItemProperty -Path $key -Name ($i + 1).ToString() -Value $was.List[$i] -PropertyType String -Force | Out-Null }
        } elseif ($was.Dword) { New-ItemProperty -Path $key -Name $parts[1] -Value ([int]$was.Value) -PropertyType DWord -Force | Out-Null }
        else { New-ItemProperty -Path $key -Name $parts[1] -Value $was.Value -PropertyType String -Force | Out-Null }
    }
}
Write-Host '  browser settings put back'

# 5. LockGuard's own files
ipconfig /flushdns | Out-Null
Remove-Item -Path 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\LockGuard' -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force (Join-Path $env:ProgramFiles 'LockGuard') -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force $data -ErrorAction SilentlyContinue
Get-ChildItem ([Environment]::GetFolderPath('CommonDesktopDirectory')), ([Environment]::GetFolderPath('Desktop')) -Filter 'LockGuard Setup*.lnk' -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
Write-Host 'Done. LockGuard is removed and the computer is back to how it was.'
