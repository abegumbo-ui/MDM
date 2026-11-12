# LockGuard Guard. Runs as the system at startup and every 5 minutes, separately from LockGuard
# itself, so a broken LockGuard can never leave the computer without internet:
#  - LockGuard installed but stopped: start it again (it then reconnects to the dashboard).
#  - It will not start, or it is gone: undo what makes the internet depend on it (the lookup
#    settings that point at it, the low-level lookup block, its firewall rules), until it runs again.
#  - LockGuard fully removed: also remove this guard.
# Leaves everything alone while an install or update is running. Log: guard.log next to this file.
$ErrorActionPreference = 'SilentlyContinue'
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$log = Join-Path $dir 'guard.log'
function Log($m) {
    if ((Test-Path $log) -and (Get-Item $log).Length -gt 200KB) { Remove-Item $log -Force }
    Add-Content -Path $log -Value ((Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + '  ' + $m)
}

# The installer is published as LockGuard-Setup.exe (any copy of it, e.g. "LockGuard-Setup (1).exe").
if (Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^LockGuard-Setup' -or $_.Name -eq 'LockGuard.Installer' }) { exit 0 }

$svc = Get-Service -Name LockGuard -ErrorAction SilentlyContinue
if ($svc) {
    if ($svc.Status -eq 'Running') { exit 0 }
    Log "LockGuard was $($svc.Status). Starting it."
    Start-Service -Name LockGuard -ErrorAction SilentlyContinue
    for ($i = 0; $i -lt 12; $i++) {
        Start-Sleep -Seconds 5
        $svc.Refresh()
        if ($svc.Status -eq 'Running') { Log 'Started.'; exit 0 }
    }
    Log 'It would not start. Giving the computer its normal internet back until it does.'
} else {
    Log 'LockGuard is not installed. Giving the computer its normal internet back.'
}

# 1. The low-level block on outside lookups (Windows Filtering Platform; see WfpBlocker.cs).
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class LockGuardGuardWfp {
    [DllImport("fwpuclnt.dll", CharSet = CharSet.Unicode)] static extern uint FwpmEngineOpen0(string s, uint a, IntPtr i, IntPtr ses, out IntPtr e);
    [DllImport("fwpuclnt.dll")] static extern uint FwpmEngineClose0(IntPtr e);
    [DllImport("fwpuclnt.dll")] static extern uint FwpmFilterDeleteByKey0(IntPtr e, ref Guid k);
    [DllImport("fwpuclnt.dll")] static extern uint FwpmSubLayerDeleteByKey0(IntPtr e, ref Guid k);
    public static string Remove() {
        IntPtr e; uint r = FwpmEngineOpen0(null, 10, IntPtr.Zero, IntPtr.Zero, out e);
        if (r != 0) return "could not open the filtering engine 0x" + r.ToString("x8");
        int removed = 0;
        for (int i = 0; i < 4; i++) {
            Guid k = new Guid("6a1f0c5e-7b2d-4c11-9a8e-4c6f636b67" + (0x10 + i).ToString("x2"));
            if (FwpmFilterDeleteByKey0(e, ref k) == 0) removed++;
        }
        Guid s = new Guid("6a1f0c5e-7b2d-4c11-9a8e-4c6f636b6701");
        FwpmSubLayerDeleteByKey0(e, ref s);
        FwpmEngineClose0(e);
        return removed + " block(s) removed";
    }
}
'@ -ErrorAction SilentlyContinue
Log ('Lookup block: ' + [LockGuardGuardWfp]::Remove())

# 2. Adapters whose lookups point at LockGuard (127.0.0.1 / ::1): back to how they were before
#    LockGuard (saved in dns-original.json), or automatic.
$orig = @{}
$origFile = Join-Path $env:ProgramData 'LockGuard\dns-original.json'
if (Test-Path $origFile) {
    $j = Get-Content $origFile -Raw | ConvertFrom-Json
    foreach ($p in $j.PSObject.Properties) { $orig[$p.Value.Name] = $p.Value }
}
$fixed = @()
foreach ($a in Get-DnsClientServerAddress) {
    if (-not ($a.ServerAddresses -contains '127.0.0.1' -or $a.ServerAddresses -contains '::1')) { continue }
    $name = $a.InterfaceAlias
    $v6 = $a.AddressFamily -eq 23
    $fam = if ($v6) { 'ipv6' } else { 'ipv4' }
    $list = @()
    if ($orig.ContainsKey($name)) { $list = @(if ($v6) { $orig[$name].V6 } else { $orig[$name].V4 }) | Where-Object { $_ } }
    if ($list.Count -eq 0) {
        netsh.exe interface $fam set dnsservers name="$name" source=dhcp | Out-Null
    } else {
        netsh.exe interface $fam set dnsservers name="$name" source=static address=$($list[0]) register=primary validate=no | Out-Null
        for ($i = 1; $i -lt $list.Count; $i++) { netsh.exe interface $fam add dnsservers name="$name" address=$($list[$i]) index=$($i + 1) validate=no | Out-Null }
    }
    $fixed += "$name ($fam)"
}
if ($fixed.Count) { Log ('Lookup settings put back: ' + ($fixed -join ', ')) }

# 3. LockGuard's own lookup-blocking firewall rules.
Get-NetFirewallRule -ErrorAction SilentlyContinue | Where-Object { $_.DisplayName -like 'LockGuard-Filter-*' } | Remove-NetFirewallRule -ErrorAction SilentlyContinue
ipconfig.exe /flushdns | Out-Null

# 4. Fully removed (no service, no program folder): the "only these programs" firewall mode goes
#    too, and so does this guard.
$programDir = Join-Path $env:ProgramFiles 'LockGuard'
if (-not $svc -and -not (Test-Path (Join-Path $programDir 'Service\LockGuard.Service.exe'))) {
    $allow = @(Get-NetFirewallRule -ErrorAction SilentlyContinue | Where-Object { $_.DisplayName -like 'LockGuard-*' })
    if ($allow.Count) {
        # Its "only these programs" mode was on, which had switched outgoing traffic to blocked.
        $allow | Remove-NetFirewallRule -ErrorAction SilentlyContinue
        Set-NetFirewallProfile -All -DefaultOutboundAction Allow -ErrorAction SilentlyContinue
    }
    Log 'LockGuard is fully removed, so the guard removes itself too.'
    schtasks.exe /delete /tn 'LockGuard Guard' /f | Out-Null
    schtasks.exe /delete /tn 'LockGuard Guard at startup' /f | Out-Null
    Start-Process -FilePath cmd.exe -ArgumentList "/c timeout /t 3 >nul & rmdir /s /q `"$dir`"" -WindowStyle Hidden
}
exit 0
