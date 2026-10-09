# Android agent + Browser + Lockdown Setup

Three separate apps live here:

- **agent** (`android/app`) -- the ongoing MDM agent, talks to the dashboard, managed remotely.
- **browser** (`android/browser`) -- the agent's companion allowlisted browser.
- **lockdown** (`android/lockdown`) -- a self-contained setup and kiosk tool, entirely separate
  from the other two. It never talks to a server at all. It's its own device-owner app (Android
  only allows one per device, so it can't coexist with the agent on the same phone). Everything is
  configured on seven screens (Regular apps, System apps, Settings, Device restrictions, a bulk
  screen for pasting the output of an outside AI app audit, Notifications, and Kiosk home screen),
  but none of it is actually enforced on the phone until the **Lockdown switch** at the top --
  visible on every screen, not buried in a menu -- is turned on. Four of those screens are
  deliberately independent controls, not layers of the same thing:
  - **Regular apps / System apps** actually block an app outright (`setApplicationHidden`) --
    opt-in only, nothing checked by default. Blocking the wrong thing is what causes crashes, so
    nothing is ever blocked unless it's individually, deliberately checked. Both also offer an
    opt-in "also show protected system components" checkbox, off by default, that reaches a
    handful of packages (Play Store among them) normally left out of both lists because blocking
    them can break the phone.
  - **Notifications** is an allow-list, the opposite shape from the two above: everything is
    silent by default, and only a package checked here gets to post a notification that stays.
    Google Maps, Waze and Android Auto start checked (an ordinary, uncheckable-if-you-want-to row,
    not hardcoded); everything else -- including Google Play Services and the Google app itself,
    which Android Auto needs alive in the background -- is silent without ever being blocked.
    Needs "Notification access" granted to the app once, by hand, since a device owner can't grant
    that one silently like every other permission here.
  - **Kiosk home screen** decides which apps get an actual tappable icon on the locked-down home
    screen -- nothing checked by default. An app can be left off this list and still run fine,
    even still notify (Android Auto, for instance, launches itself when the car connects and needs
    no icon of its own).

  Flipping the Lockdown switch on pushes the block lists, Notifications, Device restrictions, and
  the real Settings app's reachability (whatever categories were checked on the Settings picker,
  each one opened from this app's own in-app Settings menu via a direct android.settings.* action,
  same as the agent app's own disguised Settings menu) live immediately. The Kiosk home screen list
  only actually matters once "This device is set up for good" runs; until then the phone stays on
  its regular launcher regardless of what's checked there. Flipping the switch back off reverses
  every bit of what it does control: restrictions clear, blocked apps come back, every
  notification posts normally again. The app itself is never hidden or disabled while this switch
  is being used, so there's a way back in to test, change something, or push an update, as many
  times as needed. Flipping this switch is always undoable, immediately, with no other
  prerequisite; it's for setup and testing, not the final step, and has nothing to do with Factory
  Reset Protection.

  **"This device is set up for good"**, at the bottom of the setup screen, is the real, genuinely
  one-way step, and the only one that actually starts the kiosk takeover: it asks for a recovery
  Google account, validates it's exactly 21 digits, then confirms twice before doing anything. Once
  confirmed, it applies everything currently configured, sets that account as the Factory Reset
  Protection recovery account, turns on the kiosk home-screen takeover for the first time, and
  disables this app's own launcher component for good -- no more coming back to change anything or
  push an update from the phone. A factory reset, using that recovery account, is the only way
  back in after that.

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
