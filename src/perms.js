// What a client account may change on its own phones. The administrator always may do everything.
//
// Deny by default: a setting, a command or a code type that is not listed here can only be used by the administrator, so
// a new feature can never become available to clients by accident (test/perms.test.js fails if a setting has no group).
// A client record without `perms` (accounts made before this existed) keeps every control, so nothing changes for them.

export const PERM_GROUPS = [
  { key: "apps", label: "Apps", help: "Allow, block and time-limit apps, the Play Store, home screen mode, blocked screens." },
  { key: "time", label: "Screen time", help: "Daily limit, bedtime, school time, extra time, turning all restrictions off for a while." },
  { key: "browser", label: "Browser and web", help: "Website filter level, SafeSearch, allowed sites, blocked words, pictures, ads, in-app browsers." },
  { key: "youtube", label: "YouTube and YouTube Music", help: "Which YouTube they use, channels, videos and songs, downloads, sign-in." },
  { key: "calls", label: "Calls and texts", help: "Who can call, allowed numbers, contacts, call and text logs." },
  { key: "internet", label: "Internet", help: "Turning the internet off, per-app internet, address lists, pausing the web filter." },
  { key: "safety", label: "Safety and alerts", help: "Message alerts, picture alerts, history of searches, sites and videos, screenshots." },
  { key: "security", label: "Lock and security", help: "Screen lock and PINs, restrictions, hiding the app, protecting settings." },
  { key: "device", label: "Phone settings", help: "Location and places, updates, the child's phone menu, lock and find the phone, restart." },
  { key: "dangerous", label: "Erase or release a phone", help: "Wipe the phone or take it out of management. Not part of any ready-made choice: give it deliberately." },
];
export const ALL_PERMS = PERM_GROUPS.map((g) => g.key);
/** What a person who signed up gets when approved, until the administrator changes it: everything except locks and erasing. */
export const DEFAULT_SIGNUP_PERMS = ["apps", "time", "browser", "youtube", "calls", "internet", "safety", "device"];

const G = (group, keys) => Object.fromEntries(keys.split(/\s+/).filter(Boolean).map((k) => [k, group]));
// A setting that two groups both own (allowed channels and songs live in "sites"; Restricted Mode is YouTube's and the web's) may be changed by either.
export const KEY_GROUPS = {
  ...G("apps", "apps approveNew blockUnlisted blockAppGroups homeScreen hideKioskAdmin wholeAppBlocklist blockedParts blockedScreens blockedComponents blockWaStatus blockWaChannels"),
  ...G("time", "screenTime restrictionsOffUntil restrictionsOffSince"),
  ...G("browser", "adsAutoCoverOff adsCoveredSites adultListOff blockAds blockImageSearch blockImages blockVideos blockedWords browserAddonEnabled browserFilter browserLook chromePolicy filterExtras filterKinds filterSchedule imagesAllow inAppBrowsers inAppExceptions inspectBypass inspectSkipGoogle noPictureSites openWeb pictureGuard restrictBrowsing safeSearch safeSearchOff searchOff skinWho tlsInspection tlsInspectionOff"),
  ...G("youtube", "youtubeMode musicMode youtubeAddon youtubeMusicAddon youtubeOffMessage youtubeSearchOff youtubeSearchPictures youtubeSignIn downloadsOff musicPictures"),
  ...G("calls", "callLog callNumbers callsIn callsOut reportContacts syncContacts textContent textLog"),
  ...G("internet", "dnsAllowTimed dnsAllowlist dnsBlocklist filterSkipApps internetApps internetOffUntil internetOnlyApps networkBlocklist pauseUntil perAppDnsAllowlist"),
  ...G("safety", "messageAlerts messageGroups messageWords nudityAlerts recordSearches recordSites recordVideos waTextOnly"),
  ...G("security", "blockAccessibility disableBiometricUnlock frpAccounts hideAppIcon phoneAdminLocked protectScreens restrictions"),
  ...G("device", "autoUpdate freezeUpdates locationEvery places reportWifi userMenu dnsFilterNotifText dnsFilterNotifTitle"),
  sites: ["browser", "youtube"],
  youtubeDns: ["youtube", "browser"],
};

/** What each phone command needs. Anything not listed needs the administrator. */
export const COMMAND_GROUPS = {
  addTime: "time",
  openStore: "apps", closeStore: "apps", install: "apps", uninstall: "apps", updateApp: "apps", listSystemApps: "apps", findScreen: "apps",
  lock: "device", unlock: "device", locate: "device", reboot: "device", sync: "device", checkUpdates: "device", updateAgent: "device",
  screenshot: "safety",
  deleteDownloads: "youtube",
  addWifi: "internet",
  setPin: "security", clearPin: "security", setAppLockPin: "security", clearAppLockPin: "security", resetAppCode: "security", clearOverrides: "security",
  wipe: "dangerous", release: "dangerous",
};
/** Codes made for one phone. (Enrollment codes for a client's own new phones are always allowed.) */
export const CODE_GROUPS = { install: "apps", playstore: "apps", freebrowse: "browser", uninstall: "security" };

export function normalizePerms(list) {
  return [...new Set((Array.isArray(list) ? list : []).filter((p) => ALL_PERMS.includes(p)))];
}
/** A client account's permissions: an old account (no `perms`) keeps everything. */
export function permsOfClient(rec) {
  return rec && Array.isArray(rec.perms) ? normalizePerms(rec.perms) : [...ALL_PERMS];
}

const stable = (v) => {
  if (Array.isArray(v)) return "[" + v.map(stable).join(",") + "]";
  if (v && typeof v === "object") return "{" + Object.keys(v).sort().map((k) => JSON.stringify(k) + ":" + stable(v[k])).join(",") + "}";
  return JSON.stringify(v === undefined ? null : v);
};
const allowedFor = (allowed, group) => (Array.isArray(group) ? group.some((x) => allowed.has(x)) : allowed.has(group));

/** The settings that differ between two (normalised) configs and that this account may not change. A setting nobody mapped is denied. */
export function deniedConfigKeys(before, after, allowed) {
  const keys = new Set([...Object.keys(before || {}), ...Object.keys(after || {})]);
  const out = [];
  for (const k of keys) {
    if (stable((before || {})[k]) === stable((after || {})[k])) continue;
    const g = KEY_GROUPS[k];
    if (!g || !allowedFor(allowed, g)) out.push(k);
  }
  return out.sort();
}
export function commandAllowed(type, allowed) {
  const g = COMMAND_GROUPS[type];
  return !!g && allowed.has(g);
}
export function codeAllowed(type, allowed) {
  const g = CODE_GROUPS[type];
  return !g || allowed.has(g); // enrollment ("enroll", "browser") has no group: a client may always add their own phones
}
/** Words for an error message: the control groups the denied settings belong to. */
export function describeDenied(keys) {
  const names = new Set();
  for (const k of keys) {
    const g = KEY_GROUPS[k];
    const first = Array.isArray(g) ? g[0] : g;
    const grp = PERM_GROUPS.find((x) => x.key === first);
    names.add(grp ? grp.label : "settings only the administrator can change");
  }
  return [...names].join(", ");
}
