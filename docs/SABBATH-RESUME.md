# Sabbath run: resume notes (read this first after any restart)

Started 2026-11-13 about 16:00. The user observes the Sabbath, is away, and wants to see this work STILL RUNNING about 24 hours later.
They warned that Claude's usage limit may stop the session for ~5 hours. **After any pause or restart, carry on from the list below without asking.**

## Rules that always apply
- Branch -> PR -> wait for ALL checks -> merge (`scratchpad\ship.ps1 -Title "..."`, it pushes, opens the PR, waits for CI, merges, deploys the Cloudflare branch).
- Batch fixes first, then ONE compile/ship (memory: mdm-batch-fixes-then-one-build). Keep `npm test` green. Update docs.
- Never change the PC's system settings or Wi-Fi network. Never use the user's other phone (the Pixel). Do not adb-install onto anything except the test phone A16 (serial R5GL40JLNTY, adb at `C:\Users\ygutt\AppData\Local\Android\Sdk\platform-tools\adb.exe`).
- Credentials: never type passwords; Samsung Knox / Xiaomi sign-ups and the Tailscale SSH sign-in are the USER's actions.
- Phone UI helper for tests: `scratchpad\ui.ps1` (dot-source it; `A`, `Nodes`, `Tap`, `Focus`, `Summ`).
- Honest reporting: say what is verified on the A16, what only ran in stand-ins/tests, what cannot be tested (calls/texts need a SIM).

## The list (also in docs/OVERNIGHT-PLAN.md "Sabbath work list")
1. **Windows .exe installer** (user wants a real `.exe`, not a script): installs the agent over adb, TEMPORARILY switches off the account services so `set-device-owner` is allowed on Android 14+, sets device owner, switches the services back on (also on any failure; "restore" mode + saved list), starts the agent with the enrollment code. Build with `C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe` (no installs needed) or `dotnet`. Reference: Netspark's `run_script.bat` (scratchpad `netspark-exe-x\installer.exe_extracted\run_script.bat`). Bundle or download adb platform-tools; log file; clear messages.
2. **Phone-to-phone installer APK** (user: small or big, doesn't matter): an Android app that acts as the adb host over a USB OTG cable and does the same steps on another phone. Needs an adb-over-USB-host library (look for an open one, e.g. libadb-android / an adblib with UsbManager support), a screen that shows steps and results. Test is limited to what the A16 + the one cable allow: say so.
3. **Two-way parity phone <-> dashboard**: anything set in the phone app (blocked pages / apps / sites in the Agent app itself, and all other phone-side settings) must appear on the dashboard and be removable from there; anything on the dashboard that makes sense on the phone must be switchable in the phone's Administrator screens. Includes: pause web filtering for a while, all restrictions off for a while, picture checking, Restricted Mode, Music pictures, category boxes.
4. **Lock a screen from the dashboard by name**: the parent types a screen/app name; the dashboard checks it against what exists on the phone (installed apps and screens the phone has seen); a right name creates the block, a wrong one answers "There is no screen or app with that name on this phone." Build on the existing screen-block-by-words feature.
5. **Whole-phone test pass** of every feature not yet checked (docs/PHONE-TEST-CHECKLIST.md).
6. **Simplify the UI** (Legacy dashboard groups; phone screens).
7. **Browser check**: Agent-app Browser add-on and the separate Browser app: open, search, allowed sites, blocked page, YouTube inside it.
8. **On-device picture model** (open license, small, arm64): another choice in the picture filter, switchable from the dashboard AND the phone; check false alarms; mind APK size.
9. **Final report** to the user.

## Done but not shipped (branch `sabbath-fixes`, local)
- YouTube Music **Back bug** (found 2026-11-13 16:30 on the A16): search -> open a not-allowed song -> Back -> results -> Back again left the app, because `onPageFinished` cleared the whole web-view history. Fix: `backToAllowed()` in `YoutubeAddonActivity` now steps back through the web view's own history to the nearest page that opens (history kept). **Needs a build + A16 check** (search "avraham fried" -> Songs -> tap a song -> Back, Back: should reach the search/Home, only a third Back leaves).
- Branch `music-restricted-header` (commit ce68d49): Restricted Mode header for Music. Merge it into the same ship (rebase or merge) and verify on the A16 (General -> Restricted Mode, search an explicit song).

## Waiting on the user
- Samsung Knox licence / Xiaomi agency: user signs up (needs a company-style email domain).
- Pi: open the Tailscale SSH sign-in link, then deploy latest main (adds /agent request logging).
- Tell the user about anything they must do at the end of the run.
