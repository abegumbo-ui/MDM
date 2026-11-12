# What other parental-control apps offer, and what we have (researched 2026-11-12)

Sources: Qustodio, Bark, MMGuardian, Kidslox, FamiSafe review and vendor pages; MB Smart's Android
portal docs (sites.google.com/view/mb-smart/android/android-portal); open-source Shorts/Reels blockers
(github.com/atick-faisal/Shorts-Blocker, F-Droid AwayDoomscrollin'). Vendor pages are marketing.

| Feature (who has it) | Us |
|---|---|
| App blocking, by app and by category (all) | Yes: per app, app groups (social, games, video, dating, shopping) |
| New apps blocked until approved (MB Smart "Whitelist Apps", Qustodio) | Yes: "New apps wait for you" |
| Screen time: daily limit, schedules, per-app limits, extra time (all) | Yes, plus weekend limit and School time (#209, #211) |
| Instant lock (Kidslox) | Yes, plus "internet off for a while" on phones and PCs (#209, #214) |
| Web filter categories, SafeSearch, YouTube restricted (all) | Yes |
| Web history (Qustodio; Bark limited) | Yes: Sites visited |
| In-app browser blocking (MB Smart "Teen-proof IAB mode") | Yes: Web pages inside apps |
| In-app blocks: parts of WhatsApp, Maps, Spotify, Lens... (MB Smart) | Yes: WhatsApp Status/Channels + **Tap to block** (any part of any app) |
| Shorts / Reels blocking (Focsy, Decafly, Shorts Blocker) | Yes via Tap to block |
| Message monitoring alerts: bullying, sexual, self-harm, predators (Bark, MMGuardian) | Yes: Message alerts (child is told) |
| **Search alerts** (Bark monitors web searches) | Yes: search alerts (#209) and a full searches list (#215) |
| Location, geofences, SOS (Bark, Qustodio, MMGuardian) | Yes |
| Calls: allow/block lists (MMGuardian) | Yes; SMS per-number blocking not possible on Android |
| **Alert when a new app is installed** (Qustodio, FamiSafe) | Yes |
| Hotspot / unknown sources / installs blocks (MB Smart) | Yes: Restrictions |
| **Camera block** | Yes (DevicePolicyManager.setCameraDisabled) |
| Ad blocking (MB Smart) | Yes: Block ads (#218) |
| Play Store / Spotify without pictures (MB Smart) | Not possible without inspection inside those apps |
| AI image blurring in browser (MB Smart Pro) | Not planned |
| Uninstall protection (all) | Yes: device owner |
| Requests from the child (MB Smart, Qustodio) | Yes: sites, apps, time, Play Store |
| Rewards / extra time (FamiSafe) | Yes |
| Screenshot on request | Yes (child is told) |

## Round 2 (Family Link, Norton, Canopy, Covenant Eyes)
- School time with lunch breaks (Family Link): we have school time, no breaks yet.
- Nude-photo detection (Canopy): needs an on-phone image model; not built.
- Search report (Norton, Covenant Eyes): built (#215).
- YouTube videos watched report (Norton, Covenant Eyes): not built.
