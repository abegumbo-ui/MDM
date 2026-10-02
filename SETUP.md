# Setup

## 1. Dashboard (Cloudflare, free)
Already deployed from this repo with Cloudflare's Git integration. You need:
- A KV namespace bound as `STATE` (see `wrangler.toml`).
- Runtime **secret** `ADMIN_PASSWORD` (Worker → Settings → Variables and Secrets, the runtime section at the top, not the Build section). `keep_vars = true` keeps it across deploys.
- `GOOGLE_SERVICE_ACCOUNT` is no longer used; delete it, and delete the Google Cloud key too.

## 2. Get the agent app
Every push that touches `android/` is built by GitHub Actions. Download `mdm-agent.apk` from the
repo's **Releases → Latest agent build** (or from the Actions run's artifacts).

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

Codes work once and expire after an hour. The phone must be online to check them.

## Master code (works with no internet)
Set it in the dashboard under **Settings → Master code**. On the phone, open **MDM Agent → Administrator (master code)**. The panel can: lock, set or remove the screen PIN, reboot, install an APK, allow or block apps, release the phone, remove the agent, or erase it. The phone stores only a salted PBKDF2 hash, checks the code itself, and locks out for 15 minutes after 5 wrong tries. App changes made there sync to the dashboard (shown as "N app change(s) made on the phone"; clear them with **Clear phone-side changes**).
If you forget it, set a new one in the dashboard; the phone picks it up at its next check-in.

## Real lock
**Lock** only turns the screen off if the phone has no screen lock. Use **Set PIN** on the Devices tab (or in the admin panel) first. If the phone already has a lock, open the admin panel once and tap **Activate PIN control**.

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
