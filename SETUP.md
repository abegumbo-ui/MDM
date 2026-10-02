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

## 3. Put the agent on a phone (needs a computer with `adb`)
1. Factory-reset the phone. Skip Google sign-in so it has **no accounts** (device owner can only be set on a phone without accounts).
2. Settings → About phone → tap *Build number* 7 times → Developer options → turn on **USB debugging**.
3. In the dashboard open **Codes → Enrollment code → Generate**. It prints the exact commands. They look like:
   ```
   adb install mdm-agent.apk
   adb shell dpm set-device-owner com.familymdm.agent/.AdminReceiver
   adb shell am start -n com.familymdm.agent/.MainActivity --es server https://YOUR-WORKER.workers.dev --es code CODE
   ```
4. The phone appears under **Devices**. Pick what to allow under **Apps**, then **Save**. The phone picks changes up within about a minute (longer if the phone is idle in Doze).

## One-time codes (Codes tab)
- **Enrollment code**: connects a new phone.
- **Install-app code**: the person on the phone opens *MDM Agent → Install an app*, types the code, and picks an APK file. The agent installs it silently; "unknown sources" never has to be enabled. New apps still follow your Apps rules (hidden if "Hide apps that aren't allowed" is on and the app isn't set to Allow).
- **Removal code**: *MDM Agent → Remove agent*. Releases all restrictions and starts the uninstall. You can do the same remotely with **Release device** or **Release & remove app** on the Devices tab.
- **Browser connect code**: see "Browser allowlist" below.
- **Browse code**: see "Browse freely for a while" below.

Codes work once and expire after an hour. The phone must be online to check them.

## Browse freely for a while (opt-in, needs the agent)
The same idea as a timed Play Store window for app installs, applied to Browser. Codes tab → **Browse code**, pick how long (15 minutes to 4 hours), and give the code to the person. On the phone, *MDM Agent → Browser → "Browse freely for a while"*, type the code (or the master code, which defaults to 60 minutes with no server needed). For that long, Browser opens **any** site, including the address bar's search, bypassing the Sites allowlist entirely — except the hardcoded adult-site block, which no code or master code ever overrides.

Every new site it lands on during that window is silently queued in **Sites → Site requests**, exactly like a newly installed app waits in Apps for your approval — so you still see and decide on everything that was visited, just after the fact instead of before. When the time is up, Browser goes straight back to only opening sites already on the allowlist (plus whatever you approved from that session). This only works with the agent on the phone; Browser's own direct-to-dashboard and offline setups don't have it.

## Master code (works with no internet)
Set it in the dashboard under **Settings → Master code**. On the phone, open **MDM Agent → Administrator (master code)**. The panel can: lock, set or remove the screen PIN, reboot, install an APK, allow or block apps, release the phone, remove the agent, or erase it. The phone stores only a salted PBKDF2 hash, checks the code itself, and locks out for 15 minutes after 5 wrong tries. App changes made there sync to the dashboard (shown as "N app change(s) made on the phone"; clear them with **Clear phone-side changes**).
If you forget it, set a new one in the dashboard; the phone picks it up at its next check-in.

## Seeing or removing the screen lock
- A PIN you set from the dashboard (or the phone's admin panel) is shown on the phone's **Overview → PIN you set → Show**.
- **Nobody can read a PIN or pattern the person chose themselves.** Android stores only a scrambled form. What you can do is **Remove screen lock** (works for any PIN, pattern or password) or replace it with **Set PIN**. For that, **PIN control** (Overview) must say *Ready*. If it doesn't: open the agent app → Administrator → **Activate PIN control**, and confirm the current lock once on the phone.
- To make sure every lock is one you can see, turn on **Settings → Only the administrator can set the screen lock**. The person then can't set their own PIN or pattern.

## Real lock
**Lock** only turns the screen off if the phone has no screen lock. Use **Set PIN** on the Devices tab (or in the admin panel) first. If the phone already has a lock, open the admin panel once and tap **Activate PIN control**.

## Home screen mode (opt-in)
**Settings → Home screen mode.** The agent becomes the phone's home screen. It shows only the apps you set to **Allow**, with your logo and custom icons. Apps you didn't allow are **not switched off**: they keep working in the background (Maps keeps using Google Play services) but cannot be opened, because Android's lock-task mode is limited to the allowed apps. Calls and texts keep working; the phone, messages, file picker, permission prompts and Google dialogs are always permitted.
- **Allow the apps people need first** (phone, messages, maps…). Only allowed apps appear.
- **Settings is not available** unless you Allow it. Add Wi-Fi from the dashboard.
- **Escape hatches:** switch it off in the dashboard; or on the phone tap **Administrator → Administrator (master code) → Pause home screen mode**; **Release device** also removes it.
- **Custom icons** (Apps tab → Icon…) show on this home screen.
- It is off by default. Test it on a spare phone first.

## Browser allowlist (opt-in)
**Browser** is a separate app from the agent (package `com.familymdm.browser`) — a real tabbed browser (address bar, back/forward/reload, multiple tabs, "Add to Home Screen"). Install it the same way you installed the agent (sideload `mdm-browser.apk` from the same release the agent's APK comes from). It has three independent ways to be set up, checked in this order every time a page loads:

1. **With the agent (MDM) on the same phone.** If the agent has pushed a site list — Home screen mode or not, enrolled or offline — Browser uses it automatically, silently, with no setup screen at all. This is the strongest option (device-owner backed) and always wins if present; Browser never even asks about connecting if it finds the agent.
2. **Connected to this dashboard with no agent, and no code to type anywhere.** The first time Browser is opened with no agent found, it asks: *"Use this in whitelist mode? Every new site you visit will be sent to the administrator for approval."* Tap yes, and it connects itself — no code, no typing a dashboard address (if `DASHBOARD_URL` was set when the APK was built; see below). It shows up immediately under **Sites → Standalone browsers**, named "New Browser (xxxx)" so more than one is tellable apart — rename it there. Disconnecting it there is the only way back out; it then needs to connect again (still with no code).
3. **Advanced, for everything else.** The same first-run screen has an "Advanced setup options" link: **connect to a specific dashboard with a one-time code** (Codes tab → **Browser connect code**; useful if you run more than one dashboard, or didn't bake one in at build time), or **set up entirely offline** with a local master code — no admin, no server, just this one phone, sites added from the menu (**Manage sites**).

**Until one of these is done, Browser allows nothing at all** — no page, nothing. That's deliberate: a fresh install has no owner yet, so the safe default is to open nothing rather than everything.

**To make the automatic connect (option 2) skip even the dashboard address**, set a repository variable once: GitHub → this repo → **Settings → Secrets and variables → Actions → Variables → New repository variable**, name `DASHBOARD_URL`, value your Worker's `https://...` address. The next build bakes it in. Without it, tapping "yes" asks for the address once (still no code) and remembers it.

**A self-registered Browser has no password of its own** — anyone who both knows your dashboard's address and reaches it can create one, which then shows up under Standalone browsers for you to notice and remove. It can never see anything beyond whatever's already on the Sites allowlist, so the worst a stranger's registration can do is add noise to your Site requests queue, not gain access to anything. If that's a real concern for your dashboard's address, use option 3's code-based connect instead and don't set `DASHBOARD_URL`.

**The address bar only takes a full web address — there is no search box.** Typing something that isn't a URL shows an error instead of running it as a search, on purpose: a search engine's results page can show things from sites that were never explicitly allowed, which defeats the point of an allowlist.

**A short list of well-known adult sites is always blocked, in every mode, with no override** — not by a master code, not by a dashboard admin typing one into the Sites tab, nowhere. This is a safety net for a mistake, not the real filter: the actual protection is that the allowlist opens nothing unless you explicitly added it. The built-in list is short (a few dozen of the most-visited names) and easy to get around by nature of being a denylist, so don't rely on it alone — it exists only to stop an obviously wrong entry from ever taking effect.

**Sites tab** (shared by agent-managed phones and standalone-connected browsers alike) → add a site as either:
- **Whole site**: the domain and its subpages and subdomains (e.g. `nytimes.com` covers `www.nytimes.com/anything` and `m.nytimes.com`).
- **Exact page**: only that one link, ignoring its query string and a trailing slash.

Anything not on the list shows **This page isn't allowed** with a **Request access** button (agent-managed and dashboard-connected Browser alike); the request appears at the top of the Sites tab for you to approve as a whole site or an exact page, or dismiss. Per site, you can also set:
- **Block images** — nothing loads any pictures on that site.
- **Allow home-screen shortcut** — lets the person tap **Add to Home Screen** while on that page, so it behaves like an installed app.

**Settings → Make Browser the only browser** (agent mode only) replaces Chrome (and any other browser) as the handler for web links, so tapping a link anywhere opens the Browser app instead. You still need to **Block Chrome** itself on the Apps tab so it can't be opened directly; the switch only redirects links, it doesn't hide other browsers. Leave it off while you're still building the allowlist, so you can keep using a normal browser to test. Browser is always reachable (even in Home screen mode) whatever the Apps tab says about it, the same way the agent app always is.

Browser is reachable from the agent's main screen (**Open Browser**, which just launches the separate app if it's installed) and, in Home screen mode, from a **Browser** tile. In **offline mode**, a blocked page offers **Allow with the master code** instead of a request, adding the page directly on the phone — the same master code that works everywhere else in the agent.

## Offline mode: no dashboard at all
You can run a phone entirely on its own. After the adb `set-device-owner` command, open the agent app: it shows the command (step 1) and then asks **how you want to use the phone**.
- **Use it on its own (offline)** walks you through: choose a **master code** (6+ characters; write it down, there is no way to reset it), then the **Factory Reset Protection** ID (optional here, you can add it later), then opens **Phone settings**.
- **Phone settings** (also under *Administrator → Phone settings*, behind the master code) has everything the dashboard's Settings and Apps tabs have: Home screen mode, hold new apps for approval, hide unlisted apps, every restriction switch, Factory Reset Protection, a waiting-for-approval list, and for each app Default / Allow / Block plus a **schedule** (days and a from/to time).
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
2. Dashboard **Settings → Factory Reset Protection**: paste it and **Save**. Phones apply it within about a minute (the phone's log says "Factory Reset Protection set").
3. Open the phone's page: the **Reset protection** card is your checklist. It says **Strong** only when all of these hold: bootloader locked, protection set, reset from Settings blocked, Safe Mode blocked, Developer options and USB debugging blocked, and a recent security patch.

For it to hold, use a phone whose **bootloader stays locked** (for example a Moto G; do not unlock it). With an unlocked bootloader the protection can be erased from a computer. With it locked there is no fastboot erase or flash for you either, so your way out is **Release device** in the dashboard (or the master-code panel). Keep the phone's system updates current: old patches have known bypasses. No protection is unbreakable. This closes the known doors, so **test it on a spare phone before relying on it**: reset it from recovery mode and confirm setup demands your Google account.

Keep that Google account safe: whoever can sign in to it can set the phone up again after a reset.

## The agent is exempt from the install restrictions
Because the agent is the device owner, its own installs, updates and uninstalls work even when **Block ALL app installs**, **Block installing from unknown sources** or **Block uninstalling apps** are on. For those moments (two minutes at most, or until the result arrives) the agent pauses those restrictions, then puts them back. This covers **Update agent**, **Upload APK**, **Install from link**, the in-app install, and the **Uninstall** button.

## What "Block" really does
Block switches an app off completely, as if it were uninstalled for the person. It is not "unreachable but still running": apps that depend on a blocked app stop working too (for example Maps needs Google Play services, which is why Play services and other core parts are protected, and the dashboard warns before you block one). Blocking the Play Store is fine for Maps; it only stops installs and updates from the store.

## Uninstall
On the **Apps** tab, an app the person installed has a red **Uninstall** button and a green "can be uninstalled" tag. System apps say "cannot be uninstalled, use Block to switch it off". Uninstall also works on an app you had blocked (the agent switches it back on first, then removes it).

## Master code works everywhere on the phone
Wherever the agent app asks for a code, the **master code** is accepted too: the install code, the removal code, the timed-lock screen's **Administrator unlock** button, and the admin panel. It works with no internet. (Android's own screen-lock PIN is separate; Android doesn't let an app make its lock screen accept another code. Set the PIN from the dashboard, and you may choose the same digits as your master code if you like.)

## Logo and custom icons
- **Settings → Logo on the phones:** upload an image; phones show it on the timed-lock screen and at the top of the agent app.
- **Apps tab → Icon…** changes how an app looks *in the dashboard* (Reset icon undoes it). Android doesn't allow changing another app's icon on the phone's home screen.

## Updating the agent
- **From the dashboard:** the phone's page shows "Update available" when GitHub has a newer build. **Controls → Update agent** installs it silently over the old one (same signing key, so the phone stays managed).
- **From the phone:** open the agent app. **Update** is on the main screen: **Check for an update** / **Update now**. No code needed.
- **Automatically:** **Settings → Agent updates**. Phones check every 6 hours.
Updates come from the "Latest agent build" release in this repo (the repo is public, so no token is needed).

## Installing apps from the dashboard
**Controls → Upload APK from this computer** sends a file (up to 24 MB, kept for a week) to the phone and installs it silently. For bigger apps use **Install from link** (any direct https:// link, e.g. a GitHub release), or turn on approval mode and let the person install from the Play Store.

## Phones list
The **Devices** tab shows one compact card per phone. **Unlock now** is always on a phone's Controls page. Tap it for a page you can swipe through: Overview, Controls, Apps (with Uninstall for apps the person installed), Log and Network. Uninstall is also on the **Apps** tab for non-system apps.

## Timed lock with a message
**Lock…** on the Devices tab (or *Lock with a message and time* in the phone's admin panel) can lock for 5 minutes up to 8 hours. The phone shows your message and a countdown full-screen, and nothing else can be opened until time is up, you tap **Unlock now**, or someone enters the master code (long-press the title on the lock screen). The message also appears on the normal lock screen. An **Emergency call** button stays on the screen. The lock survives a reboot.

## Wi-Fi and battery
Each device card shows the battery level, whether it is charging, and the network it is on with signal strength. Android only shows the network name when Location is on, so the agent turns that on (switch in **Settings → Phone info**; no location is read or sent). Android never lets apps read saved Wi-Fi passwords, so use **Add Wi-Fi** on the dashboard (or in the admin panel) to push a network to the phone. The dashboard remembers the passwords you add. Battery and signal refresh every ~10 minutes in the dashboard to stay inside the free storage limits.

## Approving new apps
**Settings → Hold newly installed apps until I approve them** lets you leave the Play Store available. Whatever is on the phone when you switch it on counts as approved; anything installed afterward is hidden within seconds and listed under **Apps → Waiting for your approval** (Approve or Block).

## Phone log
Each device card has a **Phone log**: apps hidden/shown, installs and removals, failed restrictions, commands and their results, and anything done with the master code.

## Schedules
On the Apps tab, an app set to Allow can have a **Schedule** (days and a time window, in the phone's local time). Outside the window the app is hidden. The phone enforces it itself, so it works even when offline.

## Safety while testing
- **Block Developer options and USB debugging** is on by default. While it is on, adb stops working. To get back in, send **Release device** from the dashboard, or use recovery mode. Turn the switch off in **Settings** if you want adb access.
- Keep the bootloader unlocked while testing. Recovery/fastboot can always wipe the phone.
- **Hide every launcher app that isn't allowed** is off by default. Allow the apps you need (phone, messages, maps, …) *before* turning it on. Protected system components are never auto-hidden.
- **Release device** in the dashboard removes every restriction, un-hides every app, and drops device-owner status; after that the app can be uninstalled normally.

## Limits
- There is no managed Google Play whitelist. Hide apps instead, or install APKs by link (**Install APK**).
- Changes are polled, not pushed, so there can be a short delay.
- Workers KV's free tier allows ~1000 writes/day; the dashboard only writes when something changed.
