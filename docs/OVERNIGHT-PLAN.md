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
8. [x] LAST: the "regular tablet app" is the MDM Agent (already has everything); Lockdown set aside (user: "forget Lockdown, focus on the dashboard").

## B. PC (LockGuard) — user asked at ~12:30am 2026-11-12
1. [x] Dashboard PC page: list the computer's installed programs (reported by the service) with a
       per-program "No internet" / "Blocked" switch, like the phones' app list. No typing exe names.
2. [x] A full LockGuard window like the big commercial filters (status, filter level, requests,
       code entry), LockGuard's own look; do NOT copy Techloq's branding.
3. [x] Windows "Installed apps" → Uninstall opens LockGuard, which asks for a removal code made on
       the dashboard (one-time, per PC). Local code keeps working offline.
4. [x] Unlock file: dashboard sets a file name + secret text; while that file (with that text) is in
       a user's Downloads, every restriction is off; removed → back on. Like the tablet's emergency unlock.
5. [x] Anything else phones have that the PC page lacks, where Windows allows it: Block ads and sites allowed for a while (#274). YouTube "Only what I allow", covered ads and "better than that" need HTTPS inspection, which LockGuard does not do.
All Windows changes must pass windows/ci-e2e.ps1 (extend it for new behaviour).

## C. Wrap-up for the morning
- [x] Short plain summary for the user: what was built, what is tested, what still needs the phone.

## Done overnight (merged, live)
- #207 Rules on the phone · #208 Tap to block · #209 weekend limit, internet off, camera, new-app + search alerts, ✓ Block this screen
- #210 PC: program list (Allowed / No internet / Blocked), removal code, unlock file, new LockGuard window; fixed 'only chosen programs' being on by default
- #211 School time + Check in · #212 Quick blocks (Shorts, Reels...) · #213 10-minute warnings

## Next
- PC: 'Turn the internet off for a while' (like phones)
- More research rounds (Family Link, Norton, Net Nanny, Canopy, Covenant Eyes)
- LAST: bring new features to the Lockdown app and check the tablet

## Later overnight (merged, live)
- #214 PC internet off · #215 searches list · #216 What's on for this phone · #217 Lockdown Tap to block · #218 Block ads (lists published: 229,823 ad sites) · #219/#220 review fixes (Pick a part timer, dashboard loading crash, lost phone change after reset, cover flicker)
- Emulator test skipped: 0.8 GB free memory on the PC; would have frozen it.

## After 2am (merged, live)
- #222 school-time lunch break + dashboard tests now run automatically on GitHub (found 2 stale tests)
- #223 push contact fix · #224 app names in alerts · #225 daily summary at 8pm (opt-in) · #226 lock screen 'Call Mom' buttons
- #227 Tap to block respects Lockdown's switch and emergency unlock · #228 WhatsApp text and calls only (test first!)
- #229 quick blocks: Maps Explore, Snapchat Spotlight · #230 Teen/Recommended block ads · #231 PC 'What's on for this computer'
- #232 phone menu 'What your parent can see' · #233 PC 'Ask for a website' · #234 LockGuard window 'What your parent can see'

## Notes for the morning
- 'Regular tablet app' = the MDM Agent on the tablet: it already had the unlock file (emergency unlock), dashboard one-time codes and per-app internet, so the PC features already have their phone/tablet versions.
- Not built: YouTube 'videos watched' report (no reliable way to read the title), nude-photo detection (needs an image model), PC screen-time limits and PC screenshots (LockGuard runs outside the user's session).
- Nothing was installed or changed on the user's PC. Downloads\\Fix-Internet-After-LockGuard.cmd still needs one 'Yes' click to clear the old lookup block.

## Night of 2026-11-11 → 12 (after "work all night")

Done and merged (PRs #244–#248), nothing tested on a real phone yet:
- **Switches flipping back on** (Restrictions and per-app choices): three causes fixed — a change made
  during a check-in was thrown away by the answer; the dashboard's older list was applied over unconfirmed
  phone changes; the dashboard compared the phone's change number with its own clock. Test added.
- **Restrictions in both apps**: one shared list (RestrictionInfo) with the dashboard's names and one line on
  why; grouped Recommended (18) / More (28) / Advanced (31, behind "Show all").
- **Settings pages** (all called SubSettings): ✓ now blocks that page by its title, not every Settings page.
  A blocked screen backs out at once and shows "This screen is not allowed on this phone."
- **No internet for one app** in the phone's app sheet.
- **Email only** ready-made setup (dashboard + phone).

Not done: a visual clean-up of both apps (needs eyes on a real screen — do it with the phone in hand).

## 2026-11-12 (day, after "Yes, do it" and "Nude photo detection")
Merged: YouTube videos watched (#251), PC screen time (#252), PC screenshots (#253), photo alerts (#254).
Photo checker: built from opennsfw2 by the "Photo checker model" workflow into the `models` release; the
Android build downloads it (re-run the Android build if the model was published after it started).
Nothing of this is tested on a real phone/PC yet: checklist 7s, 7t and 8.

## 2026-11-12 evening (merged, live; nothing tested on a real phone yet)
- #262-#270 Jelly Browser official; YouTube section, search and allow, My channels/My videos on YouTube's home, YouTube add-on in the MDM app, Ask for a channel/video, ad block, branded blocked page.
- #271 ads blocked in every browser on MDM phones; "Oh come on, you're better than that"; Browser automatic updates.
- #272 YouTube videos that are not allowed are covered with the family logo (every page; Restricted: suggestions); allow for a while; blocked page Go back + real address; ads covered on sites that refuse ad blocking.
- #273 Browser "Allow list" (shield, top right: time left, Block it now, ask again); Custom allow length (days/hours/minutes); "Only what I allow" YouTube in Chrome and other browsers through Inspection.
- #274 PC: Block ads, sites allowed for a while.
- Test phone: Samsung Galaxy A16 (SM-S166V, Android 16, serial R5GL40JLNTY). MDM app 0.3.617 installed and made device owner by adb (user asked), Browser 1000044 installed. NOT connected to the dashboard yet (needs a code from the user). New phones get "Block factory reset" and "Block USB debugging" by default: switch both off while testing (see the PDF).
- Guide for the user: Downloads\MDM phone test - what to check.pdf (made from docs/PHONE-TEST-CHECKLIST.md + today's features).

## Night of 2026-11-13 → 14 (user's orders at ~2 AM: "go a whole night")
The Pi is the main server now (public https://mdmpi.tail67992b.ts.net, Tailscale Funnel; Cloudflare = backup, 4 variables set, all data copied: 350 entries).
Rules unchanged: branch → PR → build check → merge; never change the PC's Wi-Fi; no PC system changes (Tailscale + the RNDIS driver were done with the user's yes).
1. [ ] Dashboard header badge: Raspberry Pi / Cloudflare (backup) + each phone's chip "via Pi / via Cloudflare" (Worker: /api/server-status, device.lastVia).
2. [ ] Server page (Pi only): health, memory, temperature, power, Wi-Fi now + remembered networks (add with password, prefer, forget), restart, reboot, log, copy now. Backend done (pi/admin.mjs, system.mjs) but NOT committed/tested yet; UI not written.
3. [ ] 3-second check-ins on the Pi (code done: env FAST/BURST_POLL_SECONDS; Cloudflare keeps 30/15): verify live with the A16.
4. [ ] Dashboard redo, Family Link style: per-device Home with big switches + "More settings" for the old pages; simpler top level; keep search.
5. [ ] Phone app UI polish (Administrator screen, tiles, spacing, consistent look).
6. [ ] Re-test EVERY feature on the A16 (old + new) and record in docs/PHONE-TEST-CHECKLIST.md; fix what fails.
7. [ ] Leftovers: lock-screen PIN/type (setPin/clearPin exist; add lock type hint), SafeSearch in Chrome (build 667 proxy host map), Software update block, Downloads cancel test (interrupted), Music icon.
8. [ ] Pi hardening: stop using USB+wall together (rebooted twice); Server page "update" (self-update from GitHub) later.
9. [ ] Morning summary for the user (plain words: what works, what to check, what is still open).
