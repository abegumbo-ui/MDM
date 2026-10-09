# Android agent + Browser + Lockdown Setup

Three separate apps live here:

- **agent** (`android/app`) -- the ongoing MDM agent, talks to the dashboard, managed remotely.
- **browser** (`android/browser`) -- the agent's companion allowlisted browser.
- **lockdown** (`android/lockdown`) -- a one-time, one-way setup tool, entirely separate from the
  other two. It never talks to a server at all. It's its own device-owner app (Android only allows
  one per device, so it can't coexist with the agent on the same phone). Setup is four pickers
  (Regular apps, System apps, Settings, Device restrictions), a fifth screen for pasting a bulk
  list (whatever package names an outside AI tool found going through a dump of every app on the
  phone -- each one gets blocked immediately, with a real silent uninstall also attempted for
  anything that isn't part of Android itself), plus one button: Google Maps, Waze and
  Android Auto are allowed by default (an ordinary, uncheckable-if-you-want-to row, not hardcoded)
  and every other app starts blocked -- for real, immediately, from the moment this app becomes
  the device owner, not only once the phone is finally locked; the real Settings app is never reachable at all -- only the
  specific Settings categories checked on the Settings picker, each one opened from this app's own
  in-app Settings menu via a direct android.settings.* action, the same way the agent app's own
  disguised Settings menu reaches real screens without ever putting all of Settings in the kiosk
  allowlist. Device restrictions is a separate picker of ~50 individually-toggleable Android
  lockdown switches (Developer Options, OEM unlock, USB file transfer, and so on), all off by
  default -- a few of them are flagged as conflicting with the Wi-Fi/Connected devices Settings
  categories or with calls and texts, and turning those on is a deliberate, admin-made trade-off
  rather than something this app decides for you.
  Once "This device is set up" is confirmed and a 21-digit Factory Reset Protection account ID is
  entered, it locks the phone into a permanent kiosk mode (only what's allowed can open) and hides
  its own launcher icon so it can never be opened again. A factory reset -- gated by that FRP
  account -- is the only way to undo any of it.

## Release signing

All three apps are built as the `release` variant (not `debug`) and share one signing key, set as
`applicationId`/`versionCode` says on the tin -- not a Play Store build, just a proper, unique
signing key instead of the SDK's generic, well-known `debug.keystore` (every Android Studio
install generates the exact same one: alias `androiddebugkey`, password `android`). A sideloaded
device-owner app signed with that shared default key is itself something Google Play Protect's
heuristics weigh when deciding whether to flag an app as suspicious; a dedicated key doesn't make
that warning disappear entirely (any app outside the Play Store still shows as "unknown
publisher"), but it's one real, concrete improvement.

The keystore itself is **not committed to the repo** -- it's a real secret, unlike the old
`debug.keystore`'s universally-known password. CI reads it from two GitHub Actions secrets instead:

- `RELEASE_KEYSTORE_B64` -- the keystore file, base64-encoded.
- `RELEASE_KEYSTORE_PASSWORD` -- the keystore's store/key password (this project uses the same
  password for both).

### Generating your own key

If you don't already have a keystore (e.g. you were handed one directly, skip this):

```bash
keytool -genkeypair \
  -keystore release.keystore \
  -alias mdmagentrelease \
  -keyalg RSA -keysize 2048 -validity 10950 \
  -dname "CN=Family MDM, OU=Self-hosted, O=Family MDM Project, L=, ST=, C=US"
```

It'll prompt you to set a password -- use the same one for both prompts (store password and key
password), since the apps' build.gradle files assume they match.

### Adding it to GitHub

1. Base64-encode the keystore file:
   - macOS/Linux: `base64 -i release.keystore | pbcopy` (or redirect to a file and open it)
   - Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Set-Clipboard`
2. Repo → Settings → Secrets and variables → Actions → **New repository secret**:
   - Name `RELEASE_KEYSTORE_B64`, value: the base64 text from step 1.
   - Name `RELEASE_KEYSTORE_PASSWORD`, value: the password you set when generating the key.
3. Keep the original `release.keystore` file somewhere safe outside the repo (a password manager,
   encrypted drive, etc.) -- if you ever lose it, every phone that got a build signed with it needs
   to be wiped and re-enrolled from scratch, since Android won't update an app over a differently-signed
   one.

### If you're switching from an already-enrolled debug-signed build

Any phone that already has the agent/Browser installed under the *old* debug-signed key can't be
silently updated to a release-signed build -- Android blocks installing a differently-signed APK
over an existing app. Those phones need to be wiped and re-enrolled (same adb or QR process as a
first install) to pick up the new key. New phones enrolling for the first time are unaffected.
