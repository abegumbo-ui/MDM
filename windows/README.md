# LockGuard (Windows) -- Phase 1

Blocks internet access for everything on a Windows computer except specific programs you allow
(e.g. an email client). Apps aren't hidden or blocked from opening -- Word, the Microsoft Store,
everything still works normally -- they just can't reach the internet unless they're on the
allowed list. Protected from being stopped, disabled, or uninstalled without the code.

**This was written without access to a Windows machine.** Everything here should be read as a
first draft: a careful attempt at a correct design, not something that has been proven to work by
actually running it. Build and test it for real before trusting it on anyone's computer -- see
"What specifically needs checking" below.

## What's here

- `LockGuard.Common/` -- shared code: the code/PIN store (PBKDF2-hashed, same approach as the
  Android side of this project), the allowed-programs config, and the pipe protocol between the
  two apps below.
- `LockGuard.Service/` -- the actual enforcement. A Windows Service (runs as SYSTEM, starts at
  boot, survives logoff) that sets the Windows Firewall to block all outbound internet by default
  and allows only the listed programs through, re-applying that every 30 seconds in case something
  resets it.
- `LockGuard.Setup/` -- the only UI. A small WinForms app that talks to the service over a local
  named pipe, authenticated by the code on every single action (not just once per session). First
  run walks through choosing a code without ever asking for or showing the bootstrap one.

## Building

Every push that touches `windows/**` is actually compiled by GitHub Actions on a real Windows
runner (`.github/workflows/windows.yml`) -- this is how "does this even build" gets checked
without needing a Windows machine locally. The easiest way to get an installable copy:

1. Open the **Actions** tab on the GitHub repo, find the latest green "Build Windows LockGuard"
   run, and download its `LockGuard-windows` artifact (a zip).
2. Extract it anywhere on the target Windows computer.
3. Run `install.ps1` from inside that extracted folder, as Administrator (see "Installing" below)
   -- no .NET SDK needed on that computer at all, since the artifact already contains the built app.

To build it yourself instead (e.g. to test a change before pushing), you need the .NET 8 SDK
(`dotnet --version` should print something starting with `8.`) and Windows, since both the
service and the firewall APIs it uses are Windows-only:

```powershell
dotnet publish windows\LockGuard.Service -c Release -r win-x64 --self-contained false
dotnet publish windows\LockGuard.Setup -c Release -r win-x64 --self-contained false
```

## Installing

```powershell
# As Administrator, from the folder containing install.ps1 (either the extracted CI artifact,
# or the windows\ folder itself if you built it locally):
powershell -ExecutionPolicy Bypass -File install.ps1
```

This registers the Windows Service, starts it, and puts a "LockGuard Setup" shortcut on the
desktop. Run that shortcut to finish setup (choose a real code, add the allowed program(s)).

## Removing it

There is deliberately no `uninstall.ps1`. The only supported way to remove LockGuard is through
its own Setup app's "Remove LockGuard entirely" button, which asks for the code first. A script
that silently tore everything down without that check would defeat the entire point.

If the code is genuinely lost, there's no recovery code built into this (unlike the Android side
of this project, which has a per-device one) -- getting it off the computer at that point means
someone comfortable with `services.msc` and PowerShell manually stopping and deleting the
`LockGuard` service and clearing its firewall rules (named `LockGuard-*` in Windows Firewall with
Advanced Security). **Write the code down somewhere safe** -- Setup says this too, but it's worth
repeating here.

## What specifically needs checking on a real machine

1. **DNS.** Windows normally resolves domain names through the "DNS Client" (`Dnscache`) service
   running inside a shared `svchost.exe`, not inside the allowed program's own process.
   `FirewallManager` allows that service by name, but whether that's actually sufficient (or
   whether it needs a broader allowance that would open a bigger hole than intended) needs
   confirming by actually testing whether an allowed program can resolve a domain name and reach
   it, and whether a *non*-allowed program still can't.
2. **Whether default-outbound-block actually blocks everything** for a program not on the allowed
   list, including anything that tries to make a lower-level/raw connection rather than going
   through normal Windows networking APIs.
3. **The self-uninstall script** in `Worker.BeginUninstall()` (a short detached PowerShell script
   that waits for the service to stop, then deletes it and the data folder). This needs an actual
   end-to-end test: click "Remove LockGuard entirely" in Setup, confirm the service is gone
   (`Get-Service LockGuard` should error "not found"), confirm normal internet access is fully
   restored, confirm the `LockGuard-*` firewall rules are gone.
4. **The `icacls` lockdown in `install.ps1`** -- confirm a standard (non-administrator) account on
   the computer genuinely cannot read or write `C:\ProgramData\LockGuard` directly.
5. Whether `HNetCfg.FwPolicy2` (the Windows Firewall COM API this uses) needs the service to run
   with any additional privilege beyond LocalSystem, and behaves the same across whatever Windows
   version(s) this actually needs to run on.

## Not built yet (Phase 2)

Per-website filtering for a browser (allow specific sites, block everything else, the way the
Android side's Browser app works) needs a local filtering layer -- a firewall rule can only allow
or block a whole program, not individual domains inside it. That's a separate, bigger piece of
work, intentionally left for after Phase 1 is proven solid.
