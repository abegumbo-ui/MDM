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
- **Downloading a video on the A16 failed ("Did not work"). Cause found (build 673 now says it): YouTube itself refused the video, "This video is restricted", because Restricted Mode (Strict) is forced on the whole phone. Some videos you allow are blocked by Strict. The YouTube page now shows a warning with a one-tap "Use Moderate instead". Cancel and progress on a running download are still not re-tested (needs a video Restricted Mode lets through).
- Software update screen block: not re-tested. Most of the long phone test checklist: not run. Nicer look for the phone app's own admin screens: only the front-screen wording changed.
- Pi: its log was wiped at each reboot, so the cause is unknown. Persistent logging is now on, so the next reboot leaves evidence.

## Added later this morning (phone build 676, installed on your A16)
- **Rule changes now reach the phone within about a minute.** Android remembers each website lookup for as long as the answer says, so after "Pause web filtering" ended, a site that had been let through stayed reachable (other sites were blocked again at once). The filter now shortens the lifetime of the answers it passes on to 60 seconds. Checked: a gambling site was blocked again 59 seconds after the pause ended (it stayed open for 3+ minutes before). The same cache was behind the confusing YouTube Restricted Mode result earlier (Off now takes effect in about 15 seconds).
- Also checked on the A16 this morning: blocking a whole screen by its words (the Samsung "Software update" page, which Android calls "One UI 8.5", is backed out instantly), bedtime lock, Find phone (about 50 seconds), screenshot (about 60 seconds), Pause web filtering.
- Everything I changed on the A16 for testing was put back (compared with a saved copy of its settings: no differences).
## Web filter rebuilt (builds 682 to 688, 2026-11-13 morning)
- **Why Strict let sites through in Chrome:** with Browser inspection on, the phone points every browser at the Agent's own proxy. The proxy looked sites up itself, as the Agent app, which the filter does not count as a browser, so the "browsers only" kinds (social media) and "Approved sites only" never reached Chrome. Fixed: the proxy now applies the same rules (build 685).
- **Dashboard (Browser and web):** Strict, Medium and Approved sites only, each listing exactly what it closes. Under them you can tick News, Shopping, Games, Video and streaming, Dating (and Social media on Medium). Same in every browser. The lists rebuild every week by themselves (UT1 blacklists + a list of the biggest sites kept in the repo).
- **Tested on the A16 in Chrome:** Strict + News + Shopping closed WSJ, Fox News, BBC, Amazon, eBay, Walmart, Temu, Facebook ("This site is not allowed"); Roblox, Wikipedia and example.com stayed open. Approved sites only closed everything not on the list. Facebook opened on Medium, as it should.
- **Build 688:** the app says only "Connected" (no address). The phone's own check-ins no longer go through its proxy and only a real "Pi is off" answer sends it to Cloudflare, which stops the app showing the backup while the Pi is up. One plain line in the phone's activity log whenever its website level changes.
- **Dashboard:** the Cloudflare page now says "Raspberry Pi is running" (it asks the Pi) or "Raspberry Pi is off, using Cloudflare". It also sends you to the Pi address when the Pi answers (add ?stay to stay). "Last seen" is now the real time.
- **Raspberry Pi:** it still resets itself every 30 to 45 minutes with nothing touching it (logs show clean power readings right up to the reset): a weak charger or cable. Plug its USB cable into the PC as a second power source, or use a 5 V / 2.5 A adapter and a short thick cable.
- **PC cleanup:** moved LockGuard-Setup.exe to the Recycle Bin. Double-click Downloads\Clean-Up-LockGuard-Leftovers.cmd and click Yes to remove the rest of LockGuard (checks each item first; leaves Techloq alone).
- **Not done:** instant 3-second check-ins the moment a dashboard opens (a phone asleep on its 5-minute timer only learns at its next check-in); the Legacy dashboard does not have the new category boxes.
