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
3. In the dashboard click **Generate enrollment code**. It prints the exact commands. They look like:
   ```
   adb install mdm-agent.apk
   adb shell dpm set-device-owner com.familymdm.agent/.AdminReceiver
   adb shell am start -n com.familymdm.agent/.MainActivity --es server https://YOUR-WORKER.workers.dev --es code CODE
   ```
4. The phone appears under **Devices**. Pick what to allow under **Apps**, then **Save**. The phone picks changes up within about a minute (longer if the phone is idle in Doze).

## Safety while testing
- Keep the bootloader unlocked and **USB debugging on** (the *Block USB debugging* toggle is off by default). Recovery/fastboot can always wipe the phone.
- **Hide every launcher app that isn't allowed** is off by default. Allow the apps you need (phone, messages, maps, …) *before* turning it on. Protected system components are never auto-hidden.
- **Release device** in the dashboard removes every restriction, un-hides every app, and drops device-owner status; after that the app can be uninstalled normally.

## Limits
- There is no managed Google Play whitelist. Hide apps instead, or install APKs by link (**Install APK**).
- Changes are polled, not pushed, so there can be a short delay.
- Workers KV's free tier allows ~1000 writes/day; the dashboard only writes when something changed.
