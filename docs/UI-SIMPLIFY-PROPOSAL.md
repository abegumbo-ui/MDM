# Dashboard: where it is crowded, and what I would change

Written 2026-11-13 during the Sabbath run. **Nothing here has been changed.** It is based on the structure of the device page
(`GROUPS` in `src/ui.js`) and on what was built today, not on watching anyone use it, so treat the ranking as a first guess.
Each item says what it would take. Tell me which ones to do (or "all of the cheap ones") and I will.

## What the device page looks like now
10 groups, each with tabs along the top:

| Group | Tabs |
|---|---|
| Overview | Status, Messages, Activity log (3) |
| Safety | Health check, Message alerts, Screenshots (3) |
| Apps | Apps, Add-ons, Home screen, Blocked, System apps, Play Store (**6**) |
| YouTube | YouTube, YouTube Music (2) |
| Screen time | Usage, Limits (2) |
| Browser | At a glance, Filter level, **YouTube**, Sites visited, Allowed sites, Browser apps, Inspection (**7**) |
| Calls & texts | Who can call, Contacts, Call log, Texts (4) |
| Internet | Wi-Fi, **Website filter**, Internet for apps, Traffic log (4) |
| Security | Screen lock, App lock, Admin code, Restrictions (4) |
| Device | Location, Phone menu, Settings menu, Updates, Controls (5) |

## Cheap and low-risk (a few lines each, no behaviour change)
1. **Browser > YouTube duplicates the YouTube group.** Two places to set what can be watched on YouTube. Replace the Browser tab with a one-line pointer to the YouTube group (or drop the tab). Saves one tab and the "which one wins?" doubt.
2. **"Filter level" (Browser) vs "Website filter" (Internet).** They sound like the same thing; they are not (the first is the web level in browsers and the phone-wide kinds of site; the second is a list of individual addresses blocked or allowed for every app). Rename the second to "Blocked and allowed addresses" and add one sentence to each saying which the other is.
3. **Fold the rarely used Browser tabs.** "Inspection (advanced)" and "Browser apps" into one "Advanced" tab: Browser goes from 7 tabs to 5 (At a glance, Filter level, Sites visited, Allowed sites, Advanced).
4. **Apps > System apps** is a scan tool, used rarely: move it under "Blocked" as a collapsed "Hidden system apps" section. Apps goes from 6 tabs to 5.
5. **Say "Open" first.** On Browser > Filter level the new "Open: close only what I choose" is the first choice; the old order (Strict, Medium, Approved only) reads as a safety ladder. Show them as one list from most open to most closed, so the choice is one decision.

## Medium (changes how things are grouped; I would want your yes first)
6. **One "Time" place.** Daily limit, bedtime, school time, app limits and (now) site allowances are all under Screen time > Limits, but "turn off all restrictions for a while", "pause web filtering" and "extra time" live on Overview. A small "Right now" card at the top of Overview (pause filter / all off / give time / internet off) with each button taking a duration, and nothing else about those four anywhere else.
7. **Overview > Status is long.** It lists every rule in force. Collapse it into five lines (Web, Apps, Time, Calls, Safety) that each expand.
8. **Phone's own screens mirror the dashboard.** The Administrator > Rules tabs (Websites, Time, Calls, Apps, Safety, More) follow a different grouping from the dashboard (10 groups). A parent who learns one then has to relearn the other. Cheapest fix: use the same names for the same things (no moves).

## Not proposing
- Removing features. Everything above only renames, moves or folds.
- Redesigning the look. The Legacy look was kept on purpose.

## What I need from you
Which of 1 to 8 to do. If you only answer "the cheap ones", I do 1 to 5 in one change, test it (the dashboard has a test that opens every page and tab), and ship it.
