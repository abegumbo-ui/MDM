# Morning summary, 2026-11-13 (night work)

## What you need to do first
1. **The Raspberry Pi is offline** (since about 02:43). It rebooted every few minutes, then stopped answering. Probably power (wall charger / cable) or the SD card. Look at its light, check the cable, unplug it for 5 seconds and plug it back in. When it comes back it copies everything that happened on Cloudflare by itself and becomes the main server again. Nothing is lost meanwhile: the phones switched to Cloudflare at 02:46 on their own (checked on your A16).
2. Open the dashboard (https://mdm.abegumbo.workers.dev while the Pi is down). The new look is on by default. The **New / Legacy** switch is at the top right. Legacy is the old screen.

## What is new and live
- Dashboard, new look: a home for each phone (what is waiting for you, quick buttons, one line per part), and short pages for **Browser and web**, **Apps**, **Screen time** and **YouTube**. Everything old is still under "More settings". Safety, Calls, Internet, Security and Phone settings still use the old pages inside the new frame (next to do).
- Front page: one card per device.
- Top bar badge: **Raspberry Pi** or **Cloudflare (backup)**. Each phone shows "via Raspberry Pi / via Cloudflare".
- **Server page** (when the Pi is up): temperature, memory, power, Wi-Fi (add a network with its password, prefer one, forget one, switch), Cloudflare copy status, restart, log. On the Pi the dashboard also refreshes itself every 5 seconds, and phones check in every 3 seconds while a dashboard is open (5 minutes otherwise).
- Phone build 670 (installed on your A16): unblocking an app now brings it back (it stayed hidden before), the agent prepares PIN control by itself, the dashboard shows how strong the lock is, the app front screen says which server it is on, and after a dead Pi it goes straight to Cloudflare for a minute instead of waiting 15 seconds each time.

## Your lock-screen question, answered
Android never lets any app read a PIN, a pattern or a password, and it does not say which kind it is (only how strong). What the MDM can do: **remove** the lock, or **set a new PIN** (then it knows it, and the dashboard shows it). On a phone with no lock this is ready by itself with build 670. On a phone that already has a lock, one tap on that phone is needed once: Agent, Administrator, Activate PIN control.

## Checked on your A16 tonight
SafeSearch is forced in Chrome. The adult, gambling and piracy filter works. The YouTube icon and the YouTube Music icon open fine. Blocking and unblocking an app from the dashboard works. Locking with a message and unlocking works. Turning the internet off and on works.

## Not done / not verified
- **Downloading a video on the A16 failed** ("Did not work") once; the real reason was hidden by a long warning. Build 671 shows the actual reason in the Activity log. Cancel and progress on a running download are therefore not re-tested tonight.
- Software update screen block: not re-tested. Most of the long phone test checklist: not run. Nicer look for the phone app's own admin screens: only the front-screen wording changed.
- Pi: its log was wiped at each reboot, so the cause is unknown. Persistent logging is now on, so the next reboot leaves evidence.
