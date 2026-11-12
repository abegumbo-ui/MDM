# End-to-end test of installing, updating, a failed update, the guard, and removal, on a throwaway
# Windows machine (GitHub Actions). Run as administrator with the test installers in .\e2e:
#   LockGuard-Setup-1.exe, LockGuard-Setup-2.exe  - working builds (no dashboard, so nothing
#                                                    shows up on the real dashboard)
#   LockGuard-Setup-bad.exe                        - its LockGuard does not start (a broken update)
# Never run this on a real computer.
param([string]$Dir = (Join-Path $PSScriptRoot '..\e2e'))
$ErrorActionPreference = 'Stop'
$Dir = (Resolve-Path $Dir).Path
$guardDir = Join-Path $env:ProgramFiles 'LockGuard Guard'
$failures = 0

# Every line also goes straight into a file, so it survives even if this machine loses touch with GitHub.
$progress = Join-Path $env:TEMP 'lockguard-e2e-progress.txt'
function Out($m) { Write-Host $m; Add-Content -Path $progress -Value $m }
function Step($m) { Out ''; Out ("=== " + (Get-Date -Format HH:mm:ss) + "  $m") }
function Check($ok, $m) {
    if ($ok) { Out "PASS  $m" } else { Out "FAIL  $m"; $script:failures++ }
}
function Lookup($server) {
    try {
        if ($server) { Resolve-DnsName example.com -Server $server -DnsOnly -QuickTimeout -ErrorAction Stop | Out-Null }
        else { Clear-DnsClientCache; Resolve-DnsName example.com -DnsOnly -ErrorAction Stop | Out-Null }
        return $true
    } catch { return $false }
}
function WaitFor($what, $seconds, [scriptblock]$cond) {
    for ($i = 0; $i -lt $seconds; $i += 2) { if (& $cond) { return $true }; Start-Sleep 2 }
    Out "  (gave up waiting for: $what)"
    return $false
}
function AsSystem($name, $command) {
    # Runs a command as the system (what LockGuard's own updater and uninstaller do), and waits.
    $cmdFile = Join-Path $env:TEMP "$name.cmd"
    Set-Content -Path $cmdFile -Value $command -Encoding ascii
    schtasks.exe /create /tn $name /tr "cmd.exe /c `"$cmdFile`"" /sc once /st 00:00 /ru SYSTEM /rl HIGHEST /f | Out-Null
    schtasks.exe /run /tn $name | Out-Null
    Start-Sleep 3
    WaitFor "$name to finish" 300 { (schtasks.exe /query /tn $name /fo csv /nh) -notmatch 'Running' } | Out-Null
    schtasks.exe /delete /tn $name /f | Out-Null
}
function TaskExists($n) { cmd.exe /c "schtasks /query /tn `"$n`" >nul 2>&1"; return ($LASTEXITCODE -eq 0) }
function Svc() { Get-Service LockGuard -ErrorAction SilentlyContinue }
function LoopbackDns() { [bool](Get-DnsClientServerAddress -AddressFamily IPv4 | Where-Object { $_.ServerAddresses -contains '127.0.0.1' }) }
function ShowLogs() {
    foreach ($f in 'install.log', 'guard.log') { $p = Join-Path $guardDir $f; if (Test-Path $p) { Write-Host "--- $f"; Get-Content $p | Select-Object -Last 25 } }
}

# A second copy of curl.exe somewhere else: Windows sees it as a different program.
$curl2 = Join-Path $env:TEMP 'curl-copy\curl.exe'; New-Item -ItemType Directory -Force (Split-Path $curl2) | Out-Null; Copy-Item "$env:SystemRoot\System32\curl.exe" $curl2 -Force
function CurlTry($exe) { $ErrorActionPreference = 'Continue'; $out = & $exe -sS -o NUL -m 15 https://example.com 2>&1 | Out-String; $code = $LASTEXITCODE; if ($code -ne 0) { Out ("  ($exe exit ${code}: $out)") }; return ($code -eq 0) }
function OtherWorks() { return (CurlTry $curl2) }
Step 'Before: normal lookups work'
Check (Lookup $null) 'lookups work'
Check (Lookup '8.8.8.8') 'lookups straight to 8.8.8.8 work'
Check (OtherWorks) 'before LockGuard: the copy of curl.exe can go online'

Step 'Install build 1 with the web filter on'
# The filter settings the dashboard would send, paused (everything allowed) so this machine keeps
# reaching GitHub: LockGuard still takes over the lookups and blocks going around it, which is what this tests.
$data = Join-Path $env:ProgramData 'LockGuard'
New-Item -ItemType Directory -Force $data | Out-Null
'{"on":true,"level":"moderate","pauseUntil":4102444800000,"base":"https://github.com/abegumbo-ui/MDM/releases/download/blocklists","essential":["github.com","githubusercontent.com","actions.githubusercontent.com","blob.core.windows.net","visualstudio.com","microsoft.com","windows.net","azure.com","azureedge.net"],"noInternetPrograms":["C:\\Windows\\System32\\curl.exe"]}' | Set-Content (Join-Path $data 'filter.json') -Encoding ascii
Start-Process (Join-Path $Dir 'LockGuard-Setup-1.exe') -ArgumentList '/quiet' -Wait
Check ((Svc).Status -eq 'Running') 'LockGuard is running'
Check (WaitFor 'lookups pointed at the filter' 90 { LoopbackDns }) 'lookups go through the filter (127.0.0.1)'
Check (WaitFor 'lookups through the filter' 90 { Lookup $null }) 'lookups work through the filter'
Check (-not (Lookup '8.8.8.8')) 'going around the filter (8.8.8.8) is blocked'
Check ((TaskExists 'LockGuard Guard')) 'the guard task exists'
function CurlWorks() { & "$env:SystemRoot\System32\curl.exe" -s -o NUL -m 15 https://example.com; return ($LASTEXITCODE -eq 0) }
Check (WaitFor 'curl.exe to lose its internet' 60 { -not (CurlWorks) }) 'a program set to "No internet" (curl.exe) cannot go online'
Check (OtherWorks) 'other programs still can (a copy of curl.exe elsewhere)'
# Diagnostics: LockGuard's per-program blocks as Windows has them.
$ErrorActionPreference = 'Continue'
netsh wfp show filters file="$env:TEMP\wfp.xml" | Out-Null
if (Test-Path "$env:TEMP\wfp.xml") { [xml]$x = Get-Content "$env:TEMP\wfp.xml"; foreach ($f in $x.SelectNodes('//item[displayData/name="LockGuard: no internet for a program"]')) { Out ('  WFP filter: layer ' + $f.layerKey + ' action ' + $f.action.type + ' conditions ' + $f.filterCondition.numItems + ': ' + ($f.filterCondition.item | ForEach-Object { $_.fieldKey + ' ' + $_.matchType + ' ' + $_.conditionValue.type + ' ' + ($_.conditionValue.byteBlob.asString) }) -join ' | ') } }
$ErrorActionPreference = 'Stop'

Step 'The unlock file: in Downloads, every restriction is off; deleted, back on'
# What the dashboard would send (written as the system, since the folder is locked to it).
AsSystem 'E2EUnlockCfg' "echo {""name"":""lockguard-off.txt"",""text"":""secret-123""} > ""%ProgramData%\LockGuard\unlock-file.json"""
$unlock = Join-Path $env:USERPROFILE 'Downloads\lockguard-off.txt'
New-Item -ItemType Directory -Force (Split-Path $unlock) | Out-Null
Set-Content -Path $unlock -Value 'secret-123'
Check (WaitFor 'the unlock file to take effect' 60 { CurlWorks }) 'with the unlock file, the blocked program can go online'
Remove-Item $unlock -Force
Check (WaitFor 'restrictions to come back' 60 { -not (CurlWorks) }) 'without it, the program is blocked again'
Set-Content -Path $unlock -Value 'wrong text'
Start-Sleep 12
Check (-not (CurlWorks)) 'a file with the wrong text does nothing'
Remove-Item $unlock -Force
Step 'Update to build 2, the way the updater does it (a system task)'
AsSystem 'E2EUpdate' "`"$Dir\LockGuard-Setup-2.exe`" /quiet"
Check ((Svc).Status -eq 'Running') 'LockGuard is running after the update'
Check ((Get-Content (Join-Path $guardDir 'install.log') -Raw) -match 'New version running') 'the install log says the new version runs'
Check (WaitFor 'lookups after update' 60 { Lookup $null }) 'lookups work after the update'

Step 'A broken update: its LockGuard does not start, so the previous one must come back'
AsSystem 'E2EBadUpdate' "`"$Dir\LockGuard-Setup-bad.exe`" /quiet"
Check ((Svc).Status -eq 'Running') 'LockGuard is running (the previous version was put back)'
Check ((Get-Content (Join-Path $guardDir 'install.log') -Raw) -match 'Previous version running again') 'the install log says the previous version was put back'
Check (WaitFor 'lookups after the broken update' 60 { Lookup $null }) 'lookups still work'

Step 'The worst case from 2026-11-11: LockGuard deleted halfway, settings still pointing at it'
AsSystem 'E2EBreak' 'sc.exe stop LockGuard & timeout /t 10 >nul & sc.exe delete LockGuard'
Check (WaitFor 'LockGuard to be gone' 60 { -not (Svc) }) 'LockGuard is gone'
Check (-not (Lookup $null)) 'lookups fail now (this is what took the computer offline)'
schtasks.exe /run /tn 'LockGuard Guard' | Out-Null
Check (WaitFor 'the guard to give the internet back' 120 { (-not (LoopbackDns)) -and (Lookup $null) }) 'the guard gave the internet back'
Check (Lookup '8.8.8.8') 'the low-level lookup block is gone too'

Step 'Install again, then remove it completely'
Start-Process (Join-Path $Dir 'LockGuard-Setup-2.exe') -ArgumentList '/quiet' -Wait
Check ((Svc).Status -eq 'Running') 'LockGuard is running again'
Check (WaitFor 'lookups pointed at the filter' 90 { LoopbackDns }) 'lookups go through the filter again'
$rm = Start-Process powershell.exe -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $PSScriptRoot 'Remove-LockGuard.ps1')`"" -PassThru -NoNewWindow
if (-not $rm.WaitForExit(300000)) { Out '  (the removal script did not finish in 5 minutes)'; $rm.Kill() }
Check ($rm.HasExited) 'the removal script finished'
Check (WaitFor 'LockGuard to be removed' 60 { -not (Svc) }) 'LockGuard is removed'
Check (WaitFor 'normal lookups' 60 { (-not (LoopbackDns)) -and (Lookup $null) }) 'normal lookups work'
Check (Lookup '8.8.8.8') 'lookups straight to 8.8.8.8 work again'
Check (CurlWorks) 'the program blocked from the internet can go online again'
Check (-not (TaskExists 'LockGuard Guard')) 'the guard is gone too'
Check (-not (Test-Path (Join-Path $env:ProgramFiles 'LockGuard\Service'))) 'no program files left'

ShowLogs
Write-Host ''
if ($failures) { Write-Host "$failures check(s) FAILED"; exit 1 }
Write-Host 'Every check passed.'
