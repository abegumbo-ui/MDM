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
1. [x] Dashboard header badge: Raspberry Pi / Cloudflare (backup) + each phone chip "via Raspberry Pi / via Cloudflare" (shipped, PR #292).
2. [x] Server page (Pi only): health, power, temperature, Wi-Fi (add / prefer / forget / connect), Cloudflare copy status, restart, log (shipped PR #292; read from the real Pi and correct).
3. [x] 3-second check-ins on the Pi: the Pi serves phones every 3 s while a dashboard is open (measured: the A16 checked in every ~3 s). Instant wake-up (build 691 + Pi server): a phone on its 5-minute timer keeps one request waiting on the Pi, and the Pi answers it the moment a dashboard changes something or is opened after a quiet time. MEASURED on the A16: it checked in 1.5 s after a dashboard change while it had been idle for 3 minutes. The Pi health line shows "waiting" (phones holding a request).
4. [x] Dashboard redo, Family Link style, with a New / Legacy switch: phone home, Browser, Apps, Screen time, YouTube pages, device cards (shipped PR #292 and #293). Still plain legacy under "More settings": Safety, Calls, Internet, Security, Device.
5. [~] Phone app UI polish: rounder cards and buttons, softer title, list rows with hints and arrows, new icons, front screen says only "Connected". Administrator screens use the shared pieces but could not be viewed (master code), so they are not restructured.
6. [~] Re-test every feature on the A16: verified = SafeSearch, website filter (Strict / News / Shopping ticks, blocked page wording), YouTube + Music icons (approved songs listed), app block/unblock, lock/unlock, internet off/on, build update, turn off all restrictions (off, back on by itself, VPN restarts). Waiting for the A16 to be plugged in again (it dropped off the PC at about 13:35, and the dashboard "update" command did not take build 712 without a tap in the app): Music album art for approved songs, downloads list + delete from the phone, Software update block, Cancel download, calls/texts, location history, screen-time limits, Play Store window, most of sections 5-7 of docs/PHONE-TEST-CHECKLIST.md.
7. [x] Leftovers: SafeSearch in Chrome, Music icon, lock / unlock, app block, "turn off all restrictions" (incl. coming back by itself) are VERIFIED on the A16; lock strength + PIN control and the unblock fix shipped (build 688 and later). Software update block and Cancel download are listed under item 6.
8. [~] Pi hardening: the resets were power. Moved by the user to its own wall adapter on 2026-11-13 around 13:00; up 21+ minutes without a reset at the last look (before that it reset every 5 to 45 minutes). Persistent journal + a once-a-minute health note stay on. Keep watching uptime; never leave a background watcher logging into it.
9. [x] Morning summary: docs/MORNING-SUMMARY-2026-11-13.md.

### Night log 2026-11-13 (A16, build 667, over Cloudflare because the Pi was down)
- Pi went offline about 02:43 (reboot loop, then silent): likely power or the SD card. Phones failed over to Cloudflare by themselves at 02:46 (verified). Persistent journal turned on after that, so the next reboot leaves a log.
- Verified: SafeSearch forced (google.com resolves to the forced-safe address; Google SafeSearch page says "Filtering on"); YouTube Music icon screen opens with Ask and Downloads buttons; Downloads screen lists downloaded videos; block an app from the dashboard hides it in about 22 s; timed lock shows the message and countdown, unlock clears it in about 27 s.
- Bug found and fixed in the phone code (next build): after a block was removed the app stayed hidden (the phone only un-hid apps the server listed as installed; Android answers "false" when an app is already hidden, so it was not remembered as hidden by us). Phone now un-hides anything it hid that the dashboard no longer hides.
- Test-script note: never name a PowerShell function parameter `$args`.
- 2026-11-13 morning, A16 (build 673) also verified: screen block by words ("Software update" page, shown by Android as "One UI 8.5", is backed out instantly), bedtime lock, Find phone (about 50 s), screenshot (about 60 s), Pause web filtering. Found: after a pause ends a site that was let through can stay reachable for a long time (Android caches DNS answers for their full lifetime). Fix in the next phone build: the filter caps the lifetime of every answer it relays (and of its forced SafeSearch / Restricted Mode answers) to 60 s.
- 2026-11-13 later: web filter rebuilt (Strict / Medium / Approved only spelled out; News, Shopping, Games, Video, Dating tick-boxes; lists rebuild weekly); the real cause of "Strict lets sites through in Chrome" was the Agent's inspection proxy (fixed, build 685); app says only "Connected"; the Cloudflare page says the Raspberry Pi is running; the dashboard shows the real last check-in; the Cloudflare page goes to the Pi when it answers.
