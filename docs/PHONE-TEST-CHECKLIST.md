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
