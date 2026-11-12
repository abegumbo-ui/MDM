# Overnight work plan (started 2026-11-12, user asleep, phone goes in next week)

User's words: "do what you need to do for the PC", and "make it that the Android app has every
last thing I'm able to do inside of it. Simple everything." Full permission for the work, but:
- NEVER install, update, remove or change system settings on the user's own PC (LockGuard broke
  it once; nobody is awake to click UAC anyway). Test Windows work only in CI (windows/ci-e2e.ps1).
- Normal repo workflow: branch → PR → wait for `build` → merge → fast-forward ccr-29e8dccc-iuk5jo.
- Keep `npm test`, ui-smoke and the Android compile check green. Update docs/PHONE-TEST-CHECKLIST.md.
- No message reading beyond what was approved (message alerts / screenshots, child is told).

Tick items off here as they merge, so a resumed session knows where to continue.

## A. Android app: everything the dashboard can do, also on the phone (Administrator screen)
1. [x] Inventory: list every dashboard setting/command (src/ui.js groups) vs what AdminActivity offers offline.
2. [x] Fill the gaps in the phone's Administrator screen, same plain wording and groups as the dashboard.
3. [x] Simple home of the Administrator screen: big tiles, ready-made setups, health check on the phone.
4. [x] Phone-side changes flow back to the dashboard (overrides pattern) so both agree.

5. [x] "Tap to block" (+ ✓ Block this screen, as the user described) picker (user asked ~12:45am): like the developer-assistant / layout-inspector
       apps — on the phone, an overlay where the admin taps any part of any app (a button, tab, feed,
       section); the accessibility service identifies it (view id / class / text / position path) and
       from then on blocks it (closes that screen or covers / backs out of that part). Managed from
       the dashboard too (list of blocked parts per phone, remove).
6. [x] RESEARCH for real (WebSearch): feature lists of Qustodio, Bark, Google Family Link, Kidslox,
       Net Nanny, MMGuardian, Norton Family, Aura/Circle, Screen Time (Apple), FamiSafe, Canopy,
       Covenant Eyes, MB Smart, NetSpark, Techloq. Write docs/FEATURE-GAPS.md (what they have, what we
       have, what is possible on Android) and build every gap that Android allows. Android app first.
   The user is "laser focused on the Android app" — make it the best. Keep going until 6am+.

7. [x] Next gaps from docs/FEATURE-GAPS.md: weekend limit, internet off, new-app alert, camera, search alerts (PR night-controls), then more research.
8. [ ] LAST, after everything else: bring all of this to the "regular tablet app" (the Browser app on tablets?) and the Lockdown app (user asked 2026-11-12 ~1am).

## B. PC (LockGuard) — user asked at ~12:30am 2026-11-12
1. [x] Dashboard PC page: list the computer's installed programs (reported by the service) with a
       per-program "No internet" / "Blocked" switch, like the phones' app list. No typing exe names.
2. [x] A full LockGuard window like the big commercial filters (status, filter level, requests,
       code entry), LockGuard's own look; do NOT copy Techloq's branding.
3. [x] Windows "Installed apps" → Uninstall opens LockGuard, which asks for a removal code made on
       the dashboard (one-time, per PC). Local code keeps working offline.
4. [x] Unlock file: dashboard sets a file name + secret text; while that file (with that text) is in
       a user's Downloads, every restriction is off; removed → back on. Like the tablet's emergency unlock.
5. [ ] Anything else phones have that the PC page lacks, where Windows allows it.
All Windows changes must pass windows/ci-e2e.ps1 (extend it for new behaviour).

## C. Wrap-up for the morning
- [ ] Short plain summary for the user: what was built, what is tested, what still needs the phone.

## Done overnight (merged, live)
- #207 Rules on the phone · #208 Tap to block · #209 weekend limit, internet off, camera, new-app + search alerts, ✓ Block this screen
- #210 PC: program list (Allowed / No internet / Blocked), removal code, unlock file, new LockGuard window; fixed 'only chosen programs' being on by default
- #211 School time + Check in · #212 Quick blocks (Shorts, Reels...) · #213 10-minute warnings

## Next
- PC: 'Turn the internet off for a while' (like phones)
- More research rounds (Family Link, Norton, Net Nanny, Canopy, Covenant Eyes)
- LAST: bring new features to the Lockdown app and check the tablet
