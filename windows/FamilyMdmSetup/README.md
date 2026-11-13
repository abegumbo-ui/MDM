# Family MDM phone setup (Windows program)

`FamilyMdmSetup.exe` makes a phone managed by the Family MDM app with a USB cable, the way Netspark's installer does.
It is one small file (about 35 KB). It needs nothing installed: it uses Google's own `adb` (it downloads it, about 10 MB, if
the computer does not have it) and the Family MDM app (it downloads the newest one from this project's GitHub release, and
checks it against the published checksum).

## Use
1. On the phone: Settings > About phone > tap *Build number* 7 times; then Settings > Developer options > **USB debugging** on.
2. Plug the phone into the computer. On the phone tap **Allow** (tick *Always allow*).
3. On the dashboard: Codes > Enrollment code > *Generate new code*.
4. Open `FamilyMdmSetup.exe`, type the code (and the dashboard address if the app does not already know it), press
   **Set up the phone**. First time, press *Only check the phone* to see what it would do without changing anything.

## What it does (and in what order)
1. Finds or downloads `adb`; waits for exactly one phone (it refuses to run when two are plugged in).
2. Looks at the phone: refuses when another app already manages it, or when it has more than one user (guest, work profile).
3. Installs the app.
4. Android will not hand over management while the phone has accounts. So it switches off **only the account services of
   accounts that exist** (for example Google, Samsung account), writing the list to `switched-off.txt` first.
5. Runs `dpm set-device-owner`. On Android 14 and newer it restarts the phone once and tries again (you may have to unlock it).
6. **Switches the account services straight back on** and checks that they are on. This also happens when anything fails.
7. Starts the app with your code.

## If something goes wrong
- *Put things back* (button, or `FamilyMdmSetup.exe --restore`) switches on whatever an interrupted run left off.
  The next normal run does this first by itself.
- The log is `%LOCALAPPDATA%\FamilyMDMSetup\setup.log` (button *Open the log*).

## Command line
`FamilyMdmSetup.exe --cli [--dry-run] [--code CODE] [--server https://...] [--apk file.apk] [--serial SERIAL]`,
`--restore`, `--selftest` (checks the reading of phone output against copies of real phone output).

## Build
`powershell -ExecutionPolicy Bypass -File windows\FamilyMdmSetup\build.ps1` (uses the C# compiler that comes with Windows).
GitHub builds it too (`windows-setup.yml`) and puts `FamilyMdmSetup.exe` on the *Latest agent build* release.

## Tested
- Reading of the phone's output (`--selftest`, against copies of real phone output).
- The whole flow against a pretend adb that behaves like a phone with a Google and a Samsung account (`test/run-flow-tests.ps1`, 18 checks, also run on every build): the account services of the existing accounts go off BEFORE set-device-owner and come back on right after; a failure still puts them back; a phone that is already ours, managed by another app, or has two users is handled; "only check" changes nothing; "put things back" repairs an interrupted run.
- A read-only run and a switch-off/put-back of one account service on a real phone (the Samsung A16).
- NOT tried: the real hand-over on a phone that is not managed yet.
