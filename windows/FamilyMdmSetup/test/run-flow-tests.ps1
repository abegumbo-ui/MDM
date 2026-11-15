# Runs FamilyMdmSetup.exe against a pretend adb (FakeAdb.cs) to check its whole flow without a phone.
#   powershell -ExecutionPolicy Bypass -File windows\FamilyMdmSetup\test\run-flow-tests.ps1 [-Exe path\to\FamilyMdmSetup.exe]
# It uses the tool's own folder (%LOCALAPPDATA%\FamilyMDMSetup) for the log and the "switched off" list, and cleans up after itself.
param([string]$Exe = "")
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$csc = (Get-ChildItem 'C:\Windows\Microsoft.NET\Framework64\v4*\csc.exe' | Select-Object -First 1).FullName
$work = Join-Path ([IO.Path]::GetTempPath()) ('mdm-flow-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory $work | Out-Null
if (-not $Exe) {
    $Exe = Join-Path $work 'FamilyMdmSetup.exe'
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path (Split-Path -Parent $here) 'build.ps1') -Out $Exe | Out-Null
}
if ((Resolve-Path $Exe).Path -ne (Join-Path $work 'FamilyMdmSetup.exe')) { Copy-Item $Exe (Join-Path $work 'FamilyMdmSetup.exe') }
& $csc /nologo /target:exe "/out:$work\adb.exe" (Join-Path $here 'FakeAdb.cs')
if ($LASTEXITCODE -ne 0) { throw 'could not build the pretend adb' }
Set-Content (Join-Path $work 'fake.apk') 'not a real apk'
$logDir = Join-Path $env:LOCALAPPDATA 'FamilyMDMSetup'
$stateFile = Join-Path $logDir 'switched-off.txt'
$failures = 0
function Check($ok, $what) { if ($ok) { "PASS  $what" } else { "FAIL  $what"; $script:failures++ } }

function RunExe([string[]]$exeArgs, [hashtable]$envv) {
    Remove-Item (Join-Path $work 'fake-state.txt'), (Join-Path $work 'fake-adb.log'), (Join-Path $work 'fake-testonly.txt'), (Join-Path $work 'fake-dropped.txt') -ErrorAction SilentlyContinue
    foreach ($k in 'FAKE_SDK', 'FAKE_OWNER', 'FAKE_FAIL', 'FAKE_USERS', 'FAKE_REFUSE', 'FAKE_TESTONLY', 'FAKE_STALE') { Remove-Item "Env:$k" -ErrorAction SilentlyContinue }
    foreach ($k in $envv.Keys) { Set-Item "Env:$k" $envv[$k] }
    Remove-Item (Join-Path $logDir 'setup.log') -ErrorAction SilentlyContinue
    $p = Start-Process -FilePath (Join-Path $work 'FamilyMdmSetup.exe') -ArgumentList $exeArgs -Wait -PassThru -WorkingDirectory $work
    $log = if (Test-Path (Join-Path $logDir 'setup.log')) { Get-Content (Join-Path $logDir 'setup.log') -Raw } else { '' }
    $calls = if (Test-Path (Join-Path $work 'fake-adb.log')) { Get-Content (Join-Path $work 'fake-adb.log') } else { @() }
    $left = if (Test-Path (Join-Path $work 'fake-state.txt')) { @(Get-Content (Join-Path $work 'fake-state.txt') | Where-Object { $_.Trim() }) } else { @() }
    return [pscustomobject]@{ Exit = $p.ExitCode; Log = $log; Calls = $calls; Left = $left }
}
function Idx($calls, $pattern) { for ($i = 0; $i -lt $calls.Count; $i++) { if ($calls[$i] -match $pattern) { return $i } }; return -1 }
function IdxLast($calls, $pattern) { for ($i = $calls.Count - 1; $i -ge 0; $i--) { if ($calls[$i] -match $pattern) { return $i } }; return -1 }
function Count($calls, $pattern) { return @($calls | Where-Object { $_ -match $pattern }).Count }

Remove-Item $stateFile -ErrorAction SilentlyContinue
$common = @('--cli', '--apk', (Join-Path $work 'fake.apk'), '--serial', 'FAKE', '--code', 'ABC123')

# 1. A phone that refuses a test build too (an older Android): the older way. The direct try fails, then the account services go off, then owner, then back on, then the app starts with the code.
$r = RunExe $common @{ FAKE_SDK = '30' }
$off1 = Idx $r.Calls 'disable-user .*com\.google\.android\.gms'; $off2 = Idx $r.Calls 'disable-user .*com\.osp\.app\.signin'
$own = IdxLast $r.Calls 'set-device-owner'; $on1 = Idx $r.Calls 'pm enable .*com\.google\.android\.gms'; $start = Idx $r.Calls 'am start .*--es code ABC123'
Check ($r.Exit -eq 0) 'normal hand-over: exits 0'
Check ($off1 -ge 0 -and $off2 -ge 0 -and $own -gt $off1 -and $own -gt $off2) 'the account services of the two existing accounts are switched off BEFORE set-device-owner'
Check (-not ($r.Calls -match 'disable-user .*skydrive')) 'a service with no account on the phone (OneDrive) is left alone'
Check ($on1 -gt $own -and $start -gt $on1) 'they are switched back on right after owner is set, and the app is started after that'
Check ($r.Left.Count -eq 0) 'nothing is left switched off on the phone'
Check ($r.Log -match 'Family MDM is now the manager') 'the log says it worked'
Check (-not (Test-Path $stateFile)) 'the saved "switched off" list is gone afterwards'

# 2. set-device-owner fails: everything must still be put back, and the exit code says it failed.
$r = RunExe $common @{ FAKE_SDK = '30'; FAKE_FAIL = '1' }
Check ($r.Exit -eq 1) 'failure: exits 1'
Check ($r.Left.Count -eq 0) 'failure: the account services are switched back on anyway'
Check ($r.Log -match 'would not make Family MDM its manager') 'failure: the log explains it'
Check (-not (Test-Path $stateFile)) 'failure: the saved list is cleaned up'

# 3. Already managed by this app: no account services are touched, the app is still updated and started.
$r = RunExe $common @{ FAKE_OWNER = 'com.familymdm.agent' }
Check ($r.Exit -eq 0 -and (Idx $r.Calls 'disable-user') -lt 0 -and (Idx $r.Calls 'set-device-owner') -lt 0) 'already ours: nothing is switched off and owner is not set again'
Check ((Idx $r.Calls 'am start') -ge 0) 'already ours: the app is started with the code'

# 4. Managed by another app: refused, nothing changed (not even an install).
$r = RunExe $common @{ FAKE_OWNER = 'com.other.mdm' }
Check ($r.Exit -eq 1 -and (Idx $r.Calls '^(-s FAKE )?install') -lt 0 -and (Idx $r.Calls 'disable-user') -lt 0) 'another manager: refused, nothing installed or switched off'

# 5a. Android refuses to switch one account service off (the Samsung account on a real Samsung tablet): stop BEFORE set-device-owner and BEFORE any restart,
#     put back what was switched off, and say what to do.
$r = RunExe $common @{ FAKE_SDK = '35'; FAKE_REFUSE = 'com.osp.app.signin' }
Check ($r.Exit -eq 1) 'a service Android will not switch off: exits 1'
Check ((Count $r.Calls 'set-device-owner') -eq 1) 'a service Android will not switch off: set-device-owner is only tried the direct way, never after the refusal'
Check ((Idx $r.Calls ' reboot$') -lt 0 -and (Idx $r.Calls '^reboot') -lt 0) 'a service Android will not switch off: the phone is not restarted for nothing'
Check ($r.Left.Count -eq 0) 'a service Android will not switch off: the ones that were switched off are put back'
Check ($r.Log -match 'Samsung account' -and $r.Log -match 'would not let this computer switch off') 'a service Android will not switch off: the log says which one and what to do'

# 5b. The Android 16 way: the app goes on as a test build, so set-device-owner works at once with accounts present and nothing is switched off.
$r = RunExe $common @{ FAKE_SDK = '36'; FAKE_TESTONLY = '1' }
Check ($r.Exit -eq 0) 'test build: exits 0'
Check ((Idx $r.Calls 'install -t -r') -ge 0) 'test build: the app is installed with -t'
Check ((Count $r.Calls 'set-device-owner') -eq 1 -and (Idx $r.Calls 'disable-user') -lt 0) 'test build: owner is set the first time and no account service is touched'
Check ((Idx $r.Calls ' reboot$') -lt 0 -and (Idx $r.Calls '^reboot') -lt 0) 'test build: the phone is not restarted'
Check ($r.Log -match 'Nothing was removed or switched off' -and (Idx $r.Calls 'am start .*--es code ABC123') -ge 0) 'test build: the log says so and the app starts with the code'

# 5c. A phone an older version of this program already tried (it left the app registered as an ordinary admin): the app is asked to drop that, then owner is set.
$r = RunExe $common @{ FAKE_SDK = '36'; FAKE_TESTONLY = '1'; FAKE_STALE = '1' }
$drop = Idx $r.Calls 'am start .*dropAdmin'; $own1 = Idx $r.Calls 'set-device-owner'; $own2 = IdxLast $r.Calls 'set-device-owner'
Check ($r.Exit -eq 0) 'leftover admin: exits 0'
Check ($own1 -ge 0 -and $drop -gt $own1 -and $own2 -gt $drop -and (Count $r.Calls 'set-device-owner') -eq 2) 'leftover admin: owner is tried, the app is asked to drop its admin record, owner is tried again'
Check ((Idx $r.Calls 'disable-user') -lt 0) 'leftover admin: still no account service is touched'

# 5. More than one user on the phone: refused before anything is changed.
$r = RunExe $common @{ FAKE_USERS = '2' }
Check ($r.Exit -eq 1 -and (Idx $r.Calls 'disable-user') -lt 0 -and (Idx $r.Calls '^(-s FAKE )?install') -lt 0) 'two users: refused, nothing changed'

# 6. Only checking: no install, nothing switched off.
$r = RunExe @('--cli', '--dry-run', '--serial', 'FAKE') @{}
Check ($r.Exit -eq 0 -and (Idx $r.Calls '^(-s FAKE )?install') -lt 0 -and (Idx $r.Calls 'disable-user') -lt 0 -and (Idx $r.Calls 'set-device-owner') -lt 0) 'only checking: changes nothing'

# 7. A leftover from an interrupted run: --restore switches the listed services back on.
New-Item -ItemType Directory $logDir -Force | Out-Null
Set-Content $stateFile "FAKE`tcom.google.android.gms`r`nFAKE`tcom.osp.app.signin" -Encoding ascii
Set-Content (Join-Path $work 'fake-state.txt') "com.google.android.gms`r`ncom.osp.app.signin"
Remove-Item (Join-Path $work 'fake-adb.log') -ErrorAction SilentlyContinue
foreach ($k in 'FAKE_SDK', 'FAKE_OWNER', 'FAKE_FAIL', 'FAKE_USERS', 'FAKE_REFUSE', 'FAKE_TESTONLY') { Remove-Item "Env:$k" -ErrorAction SilentlyContinue }
$p = Start-Process -FilePath (Join-Path $work 'FamilyMdmSetup.exe') -ArgumentList '--restore' -Wait -PassThru -WorkingDirectory $work
$left = if (Test-Path (Join-Path $work 'fake-state.txt')) { @(Get-Content (Join-Path $work 'fake-state.txt') | Where-Object { $_.Trim() }) } else { @() }
Check ($p.ExitCode -eq 0 -and $left.Count -eq 0) 'restore: services left off by an interrupted run are switched back on'
Check (-not (Test-Path $stateFile)) 'restore: the saved list is cleared'

Remove-Item $stateFile -ErrorAction SilentlyContinue
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
if ($failures -eq 0) { 'FLOW TESTS OK' } else { "FLOW TESTS FAILED ($failures)"; exit 1 }
