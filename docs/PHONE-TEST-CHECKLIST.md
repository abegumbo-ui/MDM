# Phone test checklist

Everything below was built and checked on a computer, but **not yet run on a real phone**. Go through
it once with one test phone before relying on any of it. For each step: do it, and compare with
"You should see". If something differs, note the step number and what happened.

**Before you start**

- A phone managed by the MDM Agent (the Agent is device owner and connected to your dashboard).
- The phone is on the latest build (dashboard: the phone's Overview shows "Agent build").
- Changes from the dashboard reach the phone within about 5 minutes. To skip the wait, open the
  Agent on the phone, tap **User**, then **Refresh (check in now)**.

## 1. The phone's own menu

1. On the phone open **MDM Agent → User**.
   You should see: a list with Request a site, Request a blocked app, Open Google Play Store,
   Message the administrator, Install an app from a file, App updates, Refresh.
2. Dashboard: **Device → Phone menu**, switch off "Request a blocked app". Refresh on the phone and
   reopen User.
   You should see: that button is gone, the others remain.

## 2. Requests

1. Phone: **Request a site**, type `example.com`, Send.
   You should see on the dashboard: the **Browser** group shows a badge; **Browser → Allowed sites**
   lists the request. Approve it, refresh the phone, open the site in the Agent's browser: it opens.
2. Dashboard: block any app (**Apps → Apps**, set it to Block). Phone: **Request a blocked app**,
   pick it.
   You should see: **Overview** shows "Requests from the phone". Tap "Allow this app"; after a
   refresh the app is back on the phone.
3. Phone: **Message the administrator**, send a line.
   You should see: it under **Overview → Messages**.

## 3. Play Store

1. Dashboard: **Apps → Play Store**, switch on "Keep the Play Store closed". Refresh the phone.
   You should see: the Play Store icon is gone from the phone.
2. Phone: **User → Open Google Play Store → Ask**.
   You should see: a request on the dashboard. Tap "Open for 15 minutes". After a refresh the Play
   Store is back, the User screen shows the minutes left, and the dashboard shows a "Play Store
   open until ..." chip.
3. Wait for the time to run out.
   You should see: the Play Store disappears again within a minute or two of the time shown.
4. Dashboard: **One-time code → Get a code**. Phone: **Open Google Play Store → I have a code**.
   You should see: the store opens for the time you chose. Typing the same code again is refused.
5. Dashboard: "Until I close it", then "Close now".
   You should see: it opens, then closes after the next check-in.
6. Watch for: if "Block installing apps" is on under Security → Restrictions, installing should
   still work while the store is open, and be blocked again afterwards.

## 4. App groups

1. Install Instagram (or any app in the group) on the test phone. Dashboard: **Apps → Apps**,
   switch on "Block social media apps".
   You should see: it is hidden after a refresh; WhatsApp is not affected.
2. Set Instagram itself to **Allow** in the list below.
   You should see: Instagram comes back while the group stays blocked.

## 4b. Screen time

Dashboard: the **Screen time** group.

1. Use the phone for a few minutes, then Refresh. Open **Screen time → Usage**.
   You should see: today's total and the apps you used, in minutes. If instead it says "The Agent
   cannot see app usage on this phone", open MDM Agent → Administrator → Security → App access on
   the phone, turn on usage access, and tell me which phone model it was.
2. **Limits → Daily limit**: pick 30 minutes (or less than what is already used today).
   You should see within a minute of the phone getting it: a full lock screen saying today's screen
   time is used up, with a countdown to midnight, "Emergency call" and "Ask for more time".
3. Tap **Ask for more time**.
   You should see on the dashboard: a "More screen time" request. Tap "Give 15 minutes". After a
   refresh the phone unlocks, and locks again about 15 minutes later.
4. Set the daily limit back to "No limit".
   You should see: the phone unlocks by itself within a minute of getting the change.
5. **Bedtime**: switch it on, set "From" to a couple of minutes from now, save.
   You should see: at that time the phone locks with "Bedtime. The phone opens again at ...".
   Switch bedtime off: it unlocks.
6. **Apps**: give one app a 15-minute limit and use it past that.
   You should see: the app disappears from the phone until midnight, and the Usage tab shows
   "of 15 min" next to it.
7. Mark the Phone app "Does not count toward the daily limit".
   You should see: time in that app is still listed, but no longer added to the total.
8. Watch for: the lock appearing while a normal Lock you sent from Security is active (it should
   not replace yours), and the count being clearly wrong compared with what you actually used.

## 5. Browser filter level (our own browser)

Dashboard: **Browser → At a glance** should describe each setting in plain words as you change it.

1. **Browser → Filter level → Approved sites only.** In the Agent's browser open `wikipedia.org`.
   You should see: "This page isn't allowed" with a Request button.
2. Switch to **Moderate**. Wait a few minutes (the phone downloads about 13 MB of lists), refresh.
   You should see: `wikipedia.org` opens. `bet365.com` (a gambling site) is blocked.
   `facebook.com` opens.
3. Switch to **Strict**.
   You should see: `facebook.com` is now blocked too.
4. Add `facebook.com` under **Allowed sites**.
   You should see: it opens even on Strict (allowed sites always win).

## 6. The same levels in Chrome

1. With **Moderate** on, open Chrome on the phone and go to `bet365.com`.
   You should see: it fails to load. `wikipedia.org` loads. A key/VPN icon is in the status bar.
2. With **Strict** on, `facebook.com` fails in Chrome, **but WhatsApp still sends and receives**.
3. Watch for: any ordinary app that stops working while a level is on. Note which app.

## 7. YouTube

1. Level **Approved sites only**, YouTube set to "Follow the filter level". Add a channel under
   Allowed sites by pasting its `youtube.com/@name` address.
   You should see: the channel is a button on the browser's start page; its videos play; the
   YouTube home page, search, and another channel's video are blocked.
2. Open a video that is not allowed, tap **Request access**.
   You should see on the dashboard: the request shows the video's title and channel, with "Allow
   this video" and "Allow all videos from ...". After approving, it appears on the start page by
   its title.
3. Switch the level to **Moderate**.
   You should see: `youtube.com` shows only a search box. Searching and watching work. Shorts are
   blocked. Under a video there are no suggested videos and no comments.
4. Watch for: suggested videos or comments showing anyway (YouTube changes its pages; tell me).

## 7a. Chrome's own settings, and app groups

1. Dashboard: **Browser → Browser apps → Chrome**, switch on "No incognito tabs". Refresh the
   phone, then fully close and reopen Chrome.
   You should see: Chrome's menu has no "New incognito tab". Typing `chrome://policy` in Chrome
   lists `IncognitoModeAvailability` with value 1. **Tell me exactly what that page shows** - it
   is how we confirm Chrome accepts each setting.
2. Switch on "Google's adult-site filter" and "Block pictures on web pages" (Filter level tab).
   You should see in `chrome://policy`: `SafeSitesFilterBehavior` and `DefaultImagesSetting`.
   Pages in Chrome load without pictures; a site on the "show pictures" list keeps them.
3. Dashboard: **Apps → Apps → App groups**, switch on "Block games".
   You should see: any game on the phone disappears, including games not named in our list (the
   card names the games it found on the phone). An app you set to Allow stays.

## 7b. Safe search, YouTube Restricted Mode, pictures

Dashboard: **Browser → Filter level → Safe search and pictures**.

1. Switch on **Force SafeSearch**. On the phone, in Chrome, search Google for something and open
   Google's search settings.
   You should see: SafeSearch is on and cannot be turned off ("locked by your network" or similar).
   The same in Bing. A key/VPN icon is in the status bar.
2. Set **YouTube Restricted Mode everywhere** to Strict. Open the YouTube app.
   You should see: under the profile menu, Restricted Mode is on and locked; comments are hidden.
3. Watch for: Google, Bing, or YouTube not loading at all while these are on. Note which one.
4. Switch on **Block pictures on web pages**. Open a news site in the Agent's browser.
   You should see: the page loads with no pictures.
5. Open the same site in Chrome.
   You should see: pictures still show, unless Inspection (section 8) is on, in which case they
   are gone there too. A site on the "show pictures" list keeps its pictures in Chrome.

## 7c. Blocked words, tamper alerts, templates

1. Dashboard: **Browser → Filter level → Blocked words**, add `casino`, save, refresh the phone.
   You should see: searching Google for "casino" in the Agent's browser shows "This page or search
   has a blocked word in it". In Chrome, a site with "casino" in its name does not load (a search
   for it in Chrome still works unless Inspection is on).
2. With alerts set up, restart the phone.
   You should see: an alert "Phone was restarted" and the same line in the phone log.
3. Take the SIM card out (or swap it), wait a minute.
   You should see: "SIM card removed (was ...)" or "SIM card changed" in the log and as an alert.
4. On a phone set up the way you like: **Device → Controls → Templates → Save as template**. Then
   **Templates** (top of the Devices page): tick another phone, Apply.
   You should see: the other phone gets the same settings (check its Browser and Screen time
   tabs); its own reset-protection account is unchanged.

## 7d. Protection level, pause, internet for apps, image search, SOS, location

1. **Overview → Protection level → Recommended → Use this.** Open the Browser and Apps tabs.
   You should see: filter level Strict, SafeSearch on, social media and dating apps blocked.
2. **Overview → Pause web filtering → 15 minutes**, refresh the phone. In Chrome open a site the
   filter blocks (e.g. `bet365.com`).
   You should see: it loads now. After 15 minutes (even with Wi-Fi off) it is blocked again.
   Blocked apps stay hidden the whole time.
3. **Internet → Internet for apps**: tick WhatsApp only, Save, switch it on, refresh the phone.
   You should see: WhatsApp sends and receives; Chrome and YouTube show "no internet"; phone calls,
   texts and the Agent still work. **Note any app that should have kept working but didn't**
   (especially notifications). Switch it off: everything has internet again.
4. **Browser → Filter level → Block image search** on. In the Agent's browser search Google, tap
   "Images".
   You should see: "Image search is turned off". Normal search results still work.
5. Phone: **MDM Agent → User → I need help → Send.**
   You should see: an alert starting "SOS" right away, then a second one with a map link; the
   Overview shows a red "Asked for help" card with "Open map".
6. **Device → Location**: set "Every 15 minutes". Wait about half an hour.
   You should see: entries under "Where it was" with working Map links. Add a place "Home" with
   the coordinates field empty (it uses the last location). Leave and come back.
   You should see: "Left Home" and "Arrived at Home" in the log and as alerts (up to 15 minutes late).
   If nothing arrives at all, tell me: the phone may need Location switched on.

## 7e. Calls and texts

Dashboard: the **Calls & texts** group.

1. **Who can call → Allowed numbers**: add your own number. Set **Incoming calls** to "Only the
   numbers below". Refresh the phone.
   You should see on the phone: a notification "Finish setting up call controls". Tap it, choose
   Allow. The dashboard's "Not active yet" card turns into "Call controls are active".
2. Call the phone from your number: it rings. Call it from another phone (not on the list).
   You should see: that call does not ring at all; the phone log says "Blocked a call from ...".
3. Set **Outgoing calls** to "Only the numbers below" (Allow again if the phone asks). On the phone
   dial a number that is not on the list.
   You should see: the call does not start; a message "Calls to this number are not allowed".
   Calling your own number works. **Do not test 911**, but voicemail should still work.
4. **Call log**: switch on, refresh the phone.
   You should see: the past week's calls, with type and length.
5. **Texts**: switch on, send the phone a text.
   You should see: who and when. Switch on "Include what the messages say": new texts show the words.
6. Watch for: an allowed caller being blocked (tell me how their number is saved in contacts and
   how it shows in the phone's call log).

## 7f. Faster updates, per-app filtering, sites visited, schedule, weekly report

1. Keep the dashboard open on the phone's page for a minute, then change any setting.
   You should see: the phone picks it up within about 30 seconds (the first change after opening
   the dashboard can still take up to 5 minutes).
2. **Internet → Internet for apps → Filtering per app**: set Chrome to "No internet".
   You should see: Chrome cannot load anything; other apps can. Set it back to "Filtered".
3. With the level on Strict, set a browser you trust to "Not filtered".
   You should see: `facebook.com` opens in that app but not in Chrome.
4. **Browser → Sites visited**: switch on, use the phone for a while, refresh.
   You should see: today's sites with the app that opened them, blocked ones marked. Background
   services (googleapis and the like) are not listed. Tell me any noise that still shows up a lot.
5. **Browser → Filter level → Filter schedule**: level Strict, today, from now to an hour from now.
   You should see: within about 5 minutes `facebook.com` is blocked; after the end time it opens again.
6. Weekly report: Sunday after 17:00 UTC you get one alert summing up each phone.

## 7g. Web pages inside apps

1. Dashboard: **Browser → Browser apps → Web pages inside apps → Close browser pages opened by
   apps**. Refresh the phone.
   You should see: a notification "Finish setting up: turn on MDM Agent". Tap it, choose MDM
   Agent, switch it on. The dashboard then says "Active on the phone".
2. In Gmail (or WhatsApp), tap a link to a website.
   You should see: the page closes at once with "Web pages inside apps are turned off on this
   phone". Chrome itself still opens normally.
3. Switch to **Close every web page inside apps**. Open Instagram and tap a link in a profile.
   You should see: the page closes. **Note any app that stops working** (a login, a help screen,
   or the whole app): tick it under "Apps whose web pages stay allowed" and tell me which.
4. With "Block apps' accessibility services" on (Security), check the Agent's own switch in
   Settings → Accessibility can still be turned on, and other apps' cannot.

## 7h. WhatsApp, contacts, videos, other parents

1. **Apps → Apps → WhatsApp → Close Status** (the Agent's accessibility switch must be on, see 7g).
   Open WhatsApp → Updates and tap someone's status.
   You should see: it closes at once with a message; chats and calls work. If the status still
   plays, open **Apps → Blocked** on the dashboard, find the WhatsApp screen names the phone
   reported, and tell me them (WhatsApp may have renamed the screen).
2. Same with **Close Channels**: open a channel.
3. **Calls & texts → Contacts → Show the phone's contacts here**.
   You should see: the phone's contacts within a check-in or two; "Allow calls" adds one to the
   allowed numbers.
4. Switch on **Put the allowed numbers into the phone's contacts**.
   You should see: the allowed numbers appear by name in the phone's Contacts/Phone app. Remove
   one from the allowed list: it disappears from the phone's contacts too. Contacts that were
   already on the phone are never touched.
5. Phone: **User → Request a site or video**, paste a YouTube video link.
   You should see on the dashboard: the request with the video's title, "Allow this video" and
   "Allow all videos from ...". After approving, it is on the browser's start page.
6. **Settings → Other parents**: add a "View only" login, sign in with it in another browser.
   You should see: the phones, but any change says "This login can only look". A "Full" login
   can change settings; both appear by name in **Activity**.

## 7i. Safety: health check, message alerts, screenshots, notifications

1. Open the phone on the dashboard → **Safety → Health check**.
   You should see: everything that works with a ✅, anything red with "How to fix". Do the red
   ones and press Refresh after the next check-in: they turn green.
2. **Safety → Message alerts** → switch on (accessibility switch on, see 7g).
   You should see on the phone: a notice "Message alerts are on". Then in WhatsApp, open a chat
   with a test message containing "send nudes" (send it from your own phone).
   You should see on the dashboard within a minute or two: it under **Found**, with the sentence.
3. **Safety → Screenshots → Take a screenshot now** (Android 11 or later).
   You should see: the phone shows "A screenshot was sent to your parent", and the picture appears
   on the dashboard after Refresh.
4. On your own phone: open the dashboard → **Settings → Alerts → Get notifications on this device**
   (iPhone: Add to Home Screen first and open it from that icon). Press **Send a test**.
   You should see: a notification "MDM test alert" on your phone.
5. **Overview → Ready-made setups → Teen** on a test phone.
   You should see: the confirm box lists what it does; afterwards the filter level, apps and
   message alerts match it.

## 8. Inspection (certificate) - one test phone only

1. Dashboard: **Browser → Inspection (advanced)**, switch it on, confirm.
   You should see after a refresh: the phone shows a "network may be monitored" notice, and
   **Overview → Activity log** has "Browser inspection turned on".
2. With the level on **Moderate**, open `youtube.com` in **Chrome**.
   You should see: the plain search box instead of the YouTube home page. Shorts refused.
3. Open ordinary sites in Chrome (a news site, a shop) and your bank's site.
   You should see: they load without certificate warnings. If a site breaks, add it to "Never look
   inside these sites" and try again.
4. Switch inspection off.
   You should see: the notice goes away, Chrome browses normally, the log says "certificate
   removed".
5. Watch for: Chrome saying "your connection is not private", or no internet in Chrome. If that
   happens, switch inspection off; that restores browsing.

## 9. Dashboard and sign-in

1. **Settings → Two-step login**: set it up with an authenticator app, sign out, sign in again.
   You should see: it asks for the 6-digit code.
2. **Settings → Alerts**: add an ntfy address, press "Send a test".
   You should see: a notification on your own phone. Then send a request from the test phone: you
   get an alert for it.
3. **Activity**: your sign-ins and changes are listed.
4. Type a wrong password five times.
   You should see: "Too many wrong tries" for 15 minutes (from that network only).

## If something goes wrong on the phone

- **No internet in apps or browsers:** set the Browser filter level to "Approved sites only" and
  switch Inspection off, then Refresh on the phone.
- **Locked out of everything:** the Agent's Administrator screen with your master code works with no
  internet; "Emergency unlock" there clears every restriction.

## 7j. Rules on the phone (Administrator → Rules)

1. On the phone: Administrator (master code) → **Rules** → Websites → switch **Force SafeSearch** on.
   You should see within a minute: the dashboard shows it on, and its log says "On the phone: ...".
2. Change the same switch on the dashboard. You should see: the phone's Rules screen follows after a check-in.
3. **Rules → Ready-made → Teen**: the confirm box lists what it does; the dashboard matches afterwards.
4. **Rules → Safety**: the health check shows each item; red ones have a **Fix it** button that opens the right Android screen.
5. Dashboard: Device → Controls → **Only control from the dashboard** on. Change something in Rules on the phone:
   the dashboard ignores it and the phone goes back to the dashboard's setting at the next check-in.


## 7k. Tap to block (parts of apps)

1. Administrator → Apps → Blocked → **Tap to block → Start**. Open YouTube, tap **Pick**, tap the **Shorts** tab, choose **Cover it**.
   You should see: a dark "Blocked" box over the Shorts tab that cannot be tapped; the dashboard lists it under Apps → Blocked → Parts of apps.
2. Same in Instagram with the **Reels** tab, choose **Close the screen**: opening Reels backs straight out.
3. Use **Bigger** / **Smaller** to see what gets outlined; tell me any app where nothing can be recognised.
4. Remove one on the dashboard: within a check-in it stops being blocked.


## 7l. Weekend limit, internet off, camera, new-app and search alerts

1. Screen time → Limits → **A different limit on some days**: on a chosen day the phone uses that limit.
2. Overview → **Turn the internet off → 30 minutes**: browsers and apps get no internet, calls and texts still work; it comes back by itself.
3. Security → Restrictions → **Block the camera**: the camera app does not open.
4. Install any app on the phone: you get an alert "New app installed: ..." (Settings → Alerts → new app).
5. With message alerts on, type a worrying word into Google or YouTube search: it shows under Safety → Message alerts as "Searched: ...".
6. Tap to block → **✓ Block this screen** inside an app's screen: that screen closes whenever it opens (Apps → Blocked to undo).


## 7m. School time and Check in

1. Screen time → Limits → **School time** on, with the next few minutes as its hours: every app with an icon disappears except the phone, texts, settings, clock, calculator and apps marked "does not count"; they come back when it ends.
2. Phone menu → **Check in**: you get an alert "Checked in" with a map link.


## 7n. Searches list

1. Browser → Sites visited → **Keep a list of searches** on (accessibility switch on). The phone shows "Searches are kept".
2. Search for something in Google, YouTube and Chrome. After a check-in the searches show on that tab (one line per finished search, not every letter).

