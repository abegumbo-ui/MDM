# Setup

## 1. Dashboard (Cloudflare, free)
Already deployed from this repo with Cloudflare's Git integration. You need:
- A KV namespace bound as `STATE` (see `wrangler.toml`).
- Runtime **secret** `ADMIN_PASSWORD` (Worker → Settings → Variables and Secrets, the runtime section at the top, not the Build section). `keep_vars = true` keeps it across deploys.
- `GOOGLE_SERVICE_ACCOUNT` is no longer used; delete it, and delete the Google Cloud key too.

## 2. Get the agent app
Every push that touches `android/` is built by GitHub Actions. Download `mdm-agent.apk` from the
repo's **Releases → Latest agent build** (or from the Actions run's artifacts). The same release also
has `mdm-browser.apk` — the separate **Browser** app (see "Browser allowlist" below) — install it the
same way with `adb install mdm-browser.apk` if you want it; it's optional and can be added any time.

## 3. Put the agent on a phone
Two ways to make the agent device owner — pick whichever is easier. Either way, the phone needs **no accounts signed in yet** (device owner can only be set on a phone that hasn't finished its own setup).

**No computer, scan a QR code (recommended):**
1. Factory-reset the phone (or use it fresh out of the box) and stop at the Welcome screen — don't sign in to anything yet.
2. Tap the Welcome screen **6 times**. A camera opens for provisioning.
3. In the dashboard open **Codes → Enrollment code**, scroll to **"Set up with a QR code"**, optionally fill in your Wi-Fi so the phone can get online to download the app, and tap **Generate enrollment QR**.
4. Scan it. The phone downloads the agent, verifies it, installs it, becomes device owner, and enrolls itself with the dashboard — nothing to type, on either end. It opens straight into the normal agent screen.

**With a computer and `adb`:**
1. Factory-reset the phone. Skip Google sign-in so it has **no accounts**.
2. Settings → About phone → tap *Build number* 7 times → Developer options → turn on **USB debugging**.
3. In the dashboard open **Codes → Enrollment code → Generate**. It prints the exact commands. They look like:
   ```
   adb install mdm-agent.apk
   adb shell dpm set-device-owner com.familymdm.agent/.AdminReceiver
   adb shell am start -n com.familymdm.agent/.MainActivity --es code CODE
   ```
   If your build has the dashboard address baked in (set via the `DASHBOARD_URL` repository variable), that's all you need — the agent already knows where to connect. If not, tap **"Use a different dashboard"** on the phone once and type the address.

Either way: the phone appears under **Devices**. Tap it, open its **App rules** tab, pick what to allow, then **Save**. The phone picks changes up within about 15 seconds (longer if the phone is idle in Doze).

QR provisioning needs the agent build to have finished publishing its checksum (automatic, from GitHub Actions) — if "Generate enrollment QR" says the checksum isn't ready yet, wait a minute for the latest build to finish and try again.

**Just want to look around the screens** — on a phone you're not actually setting up, or one that still has accounts on it — without running the adb command? Step 1's card has **Skip for now (preview only)**. It lets you into the rest of the app (online/offline setup, Browser, Administrator, all the menus), but nothing is actually enforced: without device owner, Android refuses every hide/lock/restrict call, so every switch looks like it works but silently does nothing. The status line keeps saying so for as long as you're in this state. Don't use it for a phone you actually intend to manage — do step 1 for real there.

The top of the dashboard only ever says **Devices** — tap a phone or a Browser to get everything specific to it (App rules, Sites, Controls, Settings, Log, Network for a phone; just its own requests, Sites and Clone for a Browser, since it has no apps). The global **Codes** button is only for enrolling a new phone or connecting a standalone Browser; everything else is reached from inside each device's own page.

## Every phone is its own profile
There's no shared, global policy. Each phone's App rules, Sites, and every Settings toggle (approval mode, Factory Reset Protection, Home screen mode, auto-update, Wi-Fi reporting, restrictions, master code) are its own — one kid can be locked down hard, another looser, and changing one phone never touches another. A phone (or standalone Browser) that's never had its own settings yet quietly starts from whatever this dashboard's very first device had, so upgrading doesn't reset anything already in place; from that point on every device is independent.

Setting up several phones the same way? Open the new phone's own page and use **Clone settings** (in its Sites box) to copy another phone's (or browser's) whole setup over — apps, sites, and Settings all at once. Cloning *from* a browser only ever copies its Sites, since a browser has no app rules of its own; cloning *into* a browser works the same way, sites only.

## One-time codes (Codes, from the Devices list)
- **Enrollment code**: connects a new phone. Not tied to any device, since the phone has no identity yet before it enrolls.
- **Browser connect code**: see "Browser allowlist" below. Also not tied to a device, for the same reason.

The rest are generated from inside a specific phone's own page (its **Codes for this phone** card in Controls), and only work on that one phone — handing the code to a different phone's owner does nothing:
- **Install-app code**: the person on the phone opens *MDM Agent → Install an app*, types the code, and picks an APK file. The agent installs it silently; "unknown sources" never has to be enabled. New apps still follow that phone's own Apps rules (hidden if "Hide apps that aren't allowed" is on and the app isn't set to Allow).
- **Removal code**: *MDM Agent → Remove agent*. Releases all restrictions and starts the uninstall. You can do the same remotely with **Release device** or **Release & remove app** in that phone's Controls box.
- **Browse code**: see "Browse freely for a while" below.

Codes work once and expire after an hour. The phone must be online to check them.

## Browse freely for a while (opt-in, needs the agent)
The same idea as a timed Play Store window for app installs, applied to Browser. From the dashboard: open that phone's own page → Controls → **Codes for this phone** → **Browse-freely code**, pick how long (15 minutes to 4 hours), and give the code to the person — this still goes through *MDM Agent → Install an app*-style code entry on the phone, and only that phone can redeem it. From the phone itself (admin only, no code needed since the admin panel is already unlocked): *MDM Agent → Administrator → Browser → "Browse freely for a while…"*, pick the minutes. Either way, for that long, Browser opens **any** site, including the address bar's search, bypassing the Sites allowlist entirely — except the hardcoded adult-site block, which nothing ever overrides.

Every new site it lands on during that window is silently queued in **Sites → Site requests**, exactly like a newly installed app waits in Apps for your approval — so you still see and decide on everything that was visited, just after the fact instead of before. When the time is up, Browser goes straight back to only opening sites already on the allowlist (plus whatever you approved from that session). This only works with the agent on the phone; Browser's own direct-to-dashboard and offline setups don't have it.

## Master code (works with no internet)
Each phone has its own — set it from that phone's own **Settings box → Master code** (not shared with any other phone). On the phone, open **MDM Agent → Administrator (master code)**. The panel can: lock, set or remove the screen PIN, reboot, install an APK, allow or block apps, release the phone, remove the agent, or erase it. The phone stores only a salted PBKDF2 hash, checks the code itself, and locks out for 15 minutes after 5 wrong tries. App changes made there sync to the dashboard (shown as "N app change(s) made on the phone"; clear them with **Clear phone-side changes**).
If you forget it, set a new one for that phone in the dashboard; it picks it up at its next check-in.

**Recovery code: different for every phone, generated by the phone itself.** The first time the agent runs, it picks a random 6-digit code and keeps it forever after that — it works everywhere a code is asked for (the Administrator panel, an install/removal/browse code), with no internet, even with no master code set and even past the 5-wrong-tries lockout. It's shown right in the Administrator panel on the phone itself, and reported up to that phone's own Settings box on the dashboard once it's checked in at least once — unlike a code baked into every build, it's never the same across phones and never sits in the app's own source.

## App lock (opt-in, set on the phone)
Administrator (master code) → **App lock** → **Turn on app lock**. From then on, opening MDM Agent itself needs the phone's fingerprint, face, or PIN/pattern/password (whatever the phone's own screen lock uses — there's no separate biometric setup, it reuses Android's). It re-locks whenever the app was actually closed (process killed, phone rebooted, swiped away), not on every little navigation inside it. Needs a screen lock already set on the phone, or there'd be nothing to confirm against — the toggle checks for that and won't turn on otherwise. This only gates MDM Agent's own screen; it doesn't touch Home screen mode's launcher (which has to stay reachable) and it's a per-phone setting, not something the dashboard controls.

## Message the administrator
On the phone: **MDM Agent → Message the administrator → Send a message**. No code, free text — a bug report, a question, anything. It shows up on the dashboard under that phone's own **Overview**, with a Dismiss button once you've read it. Offline (no dashboard connected), it's saved to the phone's own log instead, since there's nowhere to send it.

## Seeing or removing the screen lock
- A PIN you set from the dashboard (or the phone's admin panel) is shown on the phone's **Overview → PIN you set → Show**.
- **Nobody can read a PIN or pattern the person chose themselves.** Android stores only a scrambled form. What you can do is **Remove screen lock** (works for any PIN, pattern or password) or replace it with **Set PIN**. For that, **PIN control** (Overview) must say *Ready*. If it doesn't: open the agent app → Administrator → **Activate PIN control**, and confirm the current lock once on the phone.
- To make sure every lock is one you can see, turn on **Settings → Only the administrator can set the screen lock**. The person then can't set their own PIN or pattern.

## Real lock
**Lock** only turns the screen off if the phone has no screen lock. Use **Set PIN** on the Devices tab (or in the admin panel) first. If the phone already has a lock, open the admin panel once and tap **Activate PIN control**.

## Home screen mode (opt-in)
**That phone's own Settings box → Home screen mode.** The agent becomes the phone's home screen. It shows only the apps you set to **Allow**, with your logo and custom icons. An app set to **Block** is fully switched off, same as it always is. An app left at **Default**, or explicitly set to **Soft block**, is not switched off: it keeps working in the background (Maps keeps using Google Play services) but cannot be opened from this launcher, because Android's lock-task mode is limited to the allowed apps. Calls and texts keep working; the phone, messages, file picker, permission prompts and Google dialogs are always permitted.
- **Allow the apps people need first** (phone, messages, maps…). Only allowed apps appear.
- **Soft block vs Block:** Soft block only has any effect while Home screen mode is on — turn it off and a Soft-blocked app opens normally. Block always disables the app, whether Home screen mode is on or off; use it for anything you never want opened either way.
- **Settings is not available** unless you Allow it. Add Wi-Fi from the dashboard.
- **Escape hatches:** switch it off in the dashboard; or on the phone tap **Administrator → Administrator (master code) → Pause home screen mode**; **Release device** also removes it.
- **Custom icons** (App rules box, inside any device → Icon…) show on this home screen.
- It is off by default. Test it on a spare phone first.

## Browser allowlist (opt-in)
**Browser** is a separate app from the agent (package `com.familymdm.browser`) — a real tabbed browser (address bar, back/forward/reload, multiple tabs, "Add to Home Screen"). Install it the same way you installed the agent (sideload `mdm-browser.apk` from the same release the agent's APK comes from). It has three independent ways to be set up, checked in this order every time a page loads:

1. **With the agent (MDM) on the same phone.** If the agent has pushed a site list — Home screen mode or not, enrolled or offline — Browser uses it automatically, silently, with no setup screen at all. This is the strongest option (device-owner backed) and always wins if present; Browser never even asks about connecting if it finds the agent.
2. **Connected to this dashboard with no agent, and no code to type anywhere.** The first time Browser is opened with no agent found, it asks: *"Use this in whitelist mode? Every new site you visit will be sent to the administrator for approval."* Tap yes, and it connects itself — no code, no typing a dashboard address (if `DASHBOARD_URL` was set when the APK was built; see below). It shows up immediately on the **Devices tab** as its own "Browser" card (🌐, separate from the phone cards, since it isn't one), named "New Browser (xxxx)" so more than one is tellable apart — tap it for its own requests, rename, and disconnect, same as a phone's detail page but scoped to just sites (no apps, no restrictions — a standalone Browser doesn't have those). Also listed under **Sites → Standalone browsers**. Disconnecting is the only way back out; it then needs to connect again (still with no code).
3. **Advanced, for everything else.** The same first-run screen has an "Advanced setup options" link: **connect to a specific dashboard with a one-time code** (Codes button on the Devices list → **Browser connect code**; useful if you run more than one dashboard, or didn't bake one in at build time), or **set up entirely offline** with a local master code — no admin, no server, just this one phone, sites added from the menu (**Manage sites**).

**Until one of these is done, Browser allows nothing at all** — no page, nothing. That's deliberate: a fresh install has no owner yet, so the safe default is to open nothing rather than everything.

**To make the automatic connect (option 2) skip even the dashboard address**, set a repository variable once: GitHub → this repo → **Settings → Secrets and variables → Actions → Variables → New repository variable**, name `DASHBOARD_URL`, value your Worker's `https://...` address. The next build bakes it in. Without it, tapping "yes" asks for the address once (still no code) and remembers it.

**A self-registered Browser has no password of its own** — anyone who both knows your dashboard's address and reaches it can create one, which then shows up under Standalone browsers for you to notice and remove. It can never see anything beyond whatever's already on the Sites allowlist, so the worst a stranger's registration can do is add noise to your Site requests queue, not gain access to anything. If that's a real concern for your dashboard's address, use option 3's code-based connect instead and don't set `DASHBOARD_URL`.

**The address bar only takes a full web address — there is no search box.** Typing something that isn't a URL shows an error instead of running it as a search, on purpose: a search engine's results page can show things from sites that were never explicitly allowed, which defeats the point of an allowlist. The one place search does work — a **Browse freely for a while** window (below) — forces Google's SafeSearch on for every search and every link clicked inside Google, and overwrites a typed `&safe=off` back to on; there's no way to turn it off from inside Browser. This applies the same way if `google.com` is ever added directly as an allowed site.

**A short list of well-known adult sites is always blocked, in every mode, with no override** — not by a master code, not by a dashboard admin typing one into Sites (inside any device), nowhere. This is a safety net for a mistake, not the real filter: the actual protection is that the allowlist opens nothing unless you explicitly added it. The built-in list is short (a few dozen of the most-visited names) and easy to get around by nature of being a denylist, so don't rely on it alone — it exists only to stop an obviously wrong entry from ever taking effect.

**Sites** (inside any device's own page, or a standalone Browser's — shared by all of them alike) → add a site as either:
- **Whole site**: the domain and its subpages and subdomains (e.g. `nytimes.com` covers `www.nytimes.com/anything` and `m.nytimes.com`).
- **Exact page**: only that one link, ignoring its query string and a trailing slash.

Anything not on the list shows **This page isn't allowed** with a **Request access** button (agent-managed and dashboard-connected Browser alike); the request appears at the top of Sites (inside that device or Browser) for you to approve as a whole site or an exact page, or dismiss. Per site, you can also set:
- **Block images** — nothing loads any pictures on that site.
- **Allow home-screen shortcut** — lets the person tap **Add to Home Screen** while on that page, so it behaves like an installed app.

A phone's own **Sites box → Make this the only browser** (agent mode only) replaces Chrome (and any other browser) as that phone's handler for web links, so tapping a link anywhere opens the Browser app instead. You still need to **Block Chrome** itself on that same phone's App rules box so it can't be opened directly; the switch only redirects links, it doesn't hide other browsers. Leave it off while you're still building the allowlist, so you can keep using a normal browser to test. Browser is always reachable (even in Home screen mode) whatever App rules says about it, the same way the agent app always is.

Browser is installed from *MDM Agent → Administrator → Browser → Install Browser app* (not on the main kid-facing screen — on purpose, since not every phone should have a browser), reachable afterward from the same Administrator panel (**Open Browser**) and, in Home screen mode, from a **Browser** tile. In **offline mode**, a blocked page offers **Allow with the master code** instead of a request, adding the page directly on the phone — the same master code that works everywhere else in the agent.

## Offline mode: no dashboard at all
You can run a phone entirely on its own. After the adb `set-device-owner` command, open the agent app: it shows the command (step 1) and then asks **how you want to use the phone**.
- **Use it on its own (offline)** walks you through: choose a **master code** (6+ characters; write it down, there is no way to reset it), then the **Factory Reset Protection** ID (optional here, you can add it later), then opens **Phone settings**.
- **Phone settings** (also under *Administrator → Phone settings*, behind the master code) has everything the dashboard's Settings and App rules have: Home screen mode, hold new apps for approval, hide unlisted apps, every restriction switch, Factory Reset Protection, a waiting-for-approval list, and for each app Default / Allow / Block plus a **schedule** (days and a from/to time).
- The Administrator panel still has lock (timed, with a message), PIN, Wi-Fi, install an APK, update, release and erase. Install and removal ask for the master code (there are no one-time codes without a dashboard).
- The agent works everything out on the phone every minute and applies it, so nothing needs a connection. Update checks go straight to GitHub.
- Not available offline: the dashboard views (phone log, battery and Wi-Fi name), custom app icons and the logo, remote commands, and resetting a forgotten master code.
- You can connect to a dashboard later from the main screen (**Connect to a dashboard**). The dashboard's settings then take over.
- **Careful:** with Factory Reset Protection on and a forgotten master code, wiping the phone from recovery won't help you either. Keep the code and the Google account safe.
- Debugging protection is **off** in offline mode until you switch on *Block Developer options and USB debugging* in Phone settings. Do that last: adb stops working after.

## When the phone is offline
The phone keeps enforcing everything it last received with no internet: restrictions, allowed apps and the home screen, schedules, the timed lock, and the master-code admin panel. Only new commands from the dashboard wait for a connection.

## Making a factory reset useless (Factory Reset Protection)
Android can't block a reset from recovery mode in software, so the agent and device-owner status can be wiped. What it *can* do is make the wiped phone useless: after a reset from recovery mode, setup demands one of the Google accounts you choose, and anyone else is stuck. **No account has to be signed in on the phone.** It needs Android 11 or newer.
1. Get your Google account ID (a number of about 21 digits, not your email): open the [Google People API page](https://developers.google.com/people/api/rest/v1/people/get), click **Try it**, set `resourceName` to `people/me` and `personFields` to `metadata`, click **Execute** and sign in. Copy the number next to `id`.
2. Dashboard **Settings → Factory Reset Protection**: paste it and **Save**. Phones apply it within about 15 seconds (the phone's log says "Factory Reset Protection set").
3. Open the phone's page: the **Reset protection** card is your checklist. It says **Strong** only when all of these hold: bootloader locked, protection set, reset from Settings blocked, Safe Mode blocked, Developer options and USB debugging blocked, and a recent security patch.

For it to hold, use a phone whose **bootloader stays locked** (for example a Moto G; do not unlock it). With an unlocked bootloader the protection can be erased from a computer. With it locked there is no fastboot erase or flash for you either, so your way out is **Release device** in the dashboard (or the master-code panel). Keep the phone's system updates current: old patches have known bypasses. No protection is unbreakable. This closes the known doors, so **test it on a spare phone before relying on it**: reset it from recovery mode and confirm setup demands your Google account.

Keep that Google account safe: whoever can sign in to it can set the phone up again after a reset.

## The agent is exempt from the install restrictions
Because the agent is the device owner, its own installs, updates and uninstalls work even when **Block ALL app installs**, **Block installing from unknown sources** or **Block uninstalling apps** are on. For those moments (two minutes at most, or until the result arrives) the agent pauses those restrictions, then puts them back. This covers **Update agent**, **Upload APK**, **Install from link**, the in-app install, and the **Uninstall** button.

## What "Block" and "Soft block" really do
**Block** switches an app off completely, as if it were uninstalled for the person, whether Home screen mode is on or off. It is not "unreachable but still running": apps that depend on a blocked app stop working too (for example Maps needs Google Play services, which is why Play services and other core parts are protected, and the dashboard warns before you block one). Blocking the Play Store is fine for Maps; it only stops installs and updates from the store.

**Soft block** is gentler, and only does anything while **Home screen mode** is on for that phone: the app stays installed and running, it just has no icon in that launcher and can't be opened from it. Turn Home screen mode off (or pause it) and a Soft-blocked app opens completely normally again — nothing is actually stopping it. Use Soft block for things you'd rather just keep out of sight while the custom launcher is active; use Block for anything you never want opened, launcher or no launcher.

## Uninstall
On the **Apps** tab, an app the person installed has a red **Uninstall** button and a green "can be uninstalled" tag. System apps say "cannot be uninstalled, use Block to switch it off". Uninstall also works on an app you had blocked (the agent switches it back on first, then removes it).

## Master code works everywhere on that one phone
Wherever the agent app asks for a code, that phone's own **master code** is accepted too: the install code, the removal code, the timed-lock screen's **Administrator unlock** button, and the admin panel. It works with no internet — but it's that specific phone's code, set on that phone's own Settings box, and does nothing on any other phone. (Android's own screen-lock PIN is separate; Android doesn't let an app make its lock screen accept another code. Set the PIN from the dashboard, and you may choose the same digits as your master code if you like.)

## Logo and custom icons
- **Settings → Logo on the phones:** upload an image; phones show it on the timed-lock screen and at the top of the agent app.
- **App rules tab (inside any device) → Icon…** changes how an app looks *in the dashboard* (Reset icon undoes it). Android doesn't allow changing another app's icon on the phone's home screen.

## Updating the agent
- **From the dashboard:** the phone's page shows "Update available" when GitHub has a newer build. **Controls → Update agent** installs it silently over the old one (same signing key, so the phone stays managed).
- **From the phone:** open the agent app. **Update** is on the main screen: **Check for an update** / **Update now**. No code needed.
- **Automatically:** **Settings → Agent updates**. Phones check every 6 hours.
Updates come from the "Latest agent build" release in this repo (the repo is public, so no token is needed).

## Only control from the dashboard (optional)
**Settings → Only control from the dashboard** disables the phone's own master-code Administrator
panel entirely -- entering the code there does nothing while this is on. Every change has to come
from here instead. Turning it back off also has to happen from here, so only use this on a phone
you expect to stay able to reach the dashboard. Has no effect on a standalone (no-dashboard) phone,
since that phone has no other way to be managed at all.

## Hide the app icon (optional)
**Settings → App icon → Hide the app icon** removes the agent's own icon from the launcher and app
drawer. Nothing else changes -- device-owner status, every restriction, Home screen mode, all of it
keeps working exactly the same; the app just can't be opened (or seen) the normal way anymore.
Two ways back, so this is never a one-way door even if the phone has no internet at the time:
turn the setting back off from the dashboard, or dial **\*#\*#636#\*#\*** right on the phone's own
dialer app (a few phone brands' own dialer apps don't support this standard Android feature).

## Keeping a few apps updated, even while installs are blocked (optional)
Blocking app installs/updates (the default, and what Home screen mode and the standalone Lockdown
app both force on) normally freezes everything, including apps you still need -- e.g. Waze or
Google Maps eventually stop working once their own backend drops support for an old client version,
with no way to fix that short of unblocking installs generally.

**Settings → Updates** on the phone lists exactly the installed, not-blocked apps on that phone
that could use a newer version, and updates just those -- nothing else becomes installable. It
works by embedding the same open-source library Aurora Store (a well-known unofficial, FOSS Play
Store client) is built on, talking to Google's real Play Store protocol directly, with no Play
Store app or Google account on the phone itself.

That needs a one-time setup step here on the dashboard, since the actual Google login has to live
somewhere, and a phone with no Google account on it can't be the one holding it:
1. Make a separate, dedicated Google account used for nothing else but this.
2. Run `tools/get-play-token.py` from this repo, once, **on your own computer** -- never on the
   phone, and it never asks anyone but Google for anything. Full instructions are in the script
   itself.
3. It prints three values. Set them as Worker secrets (Settings → Variables and Secrets, same
   place as `ADMIN_PASSWORD`): `PLAY_EMAIL`, `PLAY_ANDROID_ID`, `PLAY_MASTER_TOKEN`.

Until that's set up, **Settings → Updates** just says so and does nothing else -- it's entirely
optional, and every other feature in this app works the same with or without it.

## Installing apps from the dashboard
**Controls → Upload APK from this computer** sends a file (up to 24 MB, kept for a week) to the phone and installs it silently. For bigger apps use **Install from link** (any direct https:// link, e.g. a GitHub release), or turn on approval mode and let the person install from the Play Store.

## Phones list
The **Devices** list shows one plain card per phone — just its name; status (online, battery, lock, update available…) only appears once you tap in, to keep the list itself uncluttered. Tap a phone for a page you can swipe through: Overview, Controls, On this phone (with Uninstall for apps the person installed), App rules, Sites, Settings, Log and Network. **Unlock now** is always on the Controls page. Any standalone-connected Browser (no agent on that phone) shows up on the Devices list too, as its own 🌐 "Browser" card, with a shorter page (just its controls, Clone and Sites — no apps, since a browser doesn't have any).

## Timed lock with a message
**Lock…** on the Devices tab (or *Lock with a message and time* in the phone's admin panel) can lock for 5 minutes up to 8 hours. The phone shows your message and a countdown full-screen, and nothing else can be opened until time is up, you tap **Unlock now**, or someone enters the master code (long-press the title on the lock screen). The message also appears on the normal lock screen. An **Emergency call** button stays on the screen. The lock survives a reboot.

## Wi-Fi and battery
Each device card shows the battery level, whether it is charging, and the network it is on with signal strength. Android only shows the network name when Location is on, so the agent turns that on (switch in **Settings → Phone info**; no location is read or sent). Android never lets apps read saved Wi-Fi passwords, so use **Add Wi-Fi** on the dashboard (or in the admin panel) to push a network to the phone. The dashboard remembers the passwords you add. Battery and signal refresh every ~10 minutes in the dashboard to stay inside the free storage limits.

## Approving new apps
**Settings → Hold newly installed apps until I approve them** lets you leave the Play Store available. Whatever is on the phone when you switch it on counts as approved; anything installed afterward is hidden within seconds and listed under **Apps → Waiting for your approval** (Approve or Block).

## Phone log
Each device card has a **Phone log**: apps hidden/shown, installs and removals, failed restrictions, commands and their results, and anything done with the master code.

## Schedules
On the App rules tab (inside any device), an app set to Allow can have a **Schedule** (days and a time window, in the phone's local time). Outside the window the app is hidden. The phone enforces it itself, so it works even when offline.

## Safety while testing
- **Block Developer options and USB debugging** is on by default. While it is on, adb stops working. To get back in, send **Release device** from the dashboard, or use recovery mode. Turn the switch off in **Settings** if you want adb access.
- Keep the bootloader unlocked while testing. Recovery/fastboot can always wipe the phone.
- **Hide every launcher app that isn't allowed** is off by default. Allow the apps you need (phone, messages, maps, …) *before* turning it on. Protected system components are never auto-hidden.
- **Release device** in the dashboard removes every restriction, un-hides every app, and drops device-owner status; after that the app can be uninstalled normally.

## Limits
- There is no managed Google Play whitelist. Hide apps instead, or install APKs by link (**Install APK**).
- Changes are polled, not pushed, so there can be a short delay.
- Workers KV's free tier allows ~1000 writes/day; the dashboard only writes when something changed.
