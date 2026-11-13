import { endSitesEarly, MESSAGE_WORD_GROUPS, effectiveFilter, placesAt, messageWordList, matchMessageWord, buildAgentPolicy, isProtected, normalizeConfig, normalizeDnsAllowlist, normalizeDnsBlocklist, normalizeOverrides, normalizePerAppDnsAllowlist, normalizeSites, normalizeWholeAppBlocklist, effectiveYoutubeMode, youtubeChannelHandle, youtubeVideoId, APP_GROUPS, USER_MENU, RESTRICTIONS, RESTRICTION_BY_KEY, summarizeConfigChange } from "./policy.js";
import { loginPage, dashboardPage } from "./ui.js";
import { clearFailures, clientIp, lockStatus, newTotpSecret, recordFailure, totpUri, totpVerify } from "./security.js";
import { maybeSendDaily, maybeSendWeekly, auditAdd, auditList, checkDevices, describeChange, getAlerts, mergeAlerts, publicAlerts, sendAlert, ALERTS_KEY, alertsOn, validPushEndpoint, vapidKeys, latestForPush, SERVICE_WORKER } from "./alerts.js";
import { ICON_192, ICON_512 } from "./icons.js";
import { youtubeSearch } from "./youtube.js";

const SESSION_SECONDS = 60 * 60 * 12;
// How often an online phone checks in on its own. A command queued from the dashboard only
// reaches the phone at its NEXT check-in -- there's no push channel to wake it sooner -- so this
// is also the real upper bound on "how long until a dashboard action takes effect" (the UI says so
// honestly rather than a shorter number that isn't true). 5 minutes, not something much shorter,
// to stay well under the KV free tier's write/read caps across many devices.
const POLL_SECONDS = 300; // nobody on the dashboard: every 5 minutes (keeps the free plan's daily limits safe)
// Workers KV's free tier allows ~1000 writes/day, so a device record is only
// rewritten when something changed or the stored "last seen" is this stale.
const LAST_SEEN_WRITE_MS = 10 * 60 * 1000;
// Same idea for reads: the free tier's ~100,000/day cap is mostly spent on this one value, read
// on every single agent sync (every POLL_SECONDS, per device) even though the logo itself changes rarely.
// Cached per Worker isolate instead of re-read from KV every time; an isolate can live for many
// requests in a row, so this cuts real KV reads by roughly this cache's lifetime in practice, even
// though it isn't a guaranteed shared cache across every edge location.
const LOGO_REV_CACHE_MS = 2 * 60 * 1000;
let logoRevCache = null;
let logoRevCacheAt = 0;
async function cachedLogoRev(env) {
  const now = Date.now();
  if (logoRevCache !== null && now - logoRevCacheAt < LOGO_REV_CACHE_MS) return logoRevCache;
  logoRevCache = Number(await env.STATE.get("logoRev")) || 0;
  logoRevCacheAt = now;
  return logoRevCache;
}
const COMMANDS = new Set(["addTime", "openStore", "closeStore", "lock", "reboot", "wipe", "release", "install", "uninstall", "sync", "setPin", "clearPin", "setAppLockPin", "clearAppLockPin", "clearOverrides", "unlock", "addWifi", "resetAppCode", "updateAgent", "listSystemApps", "locate", "checkUpdates", "updateApp", "screenshot"]);
const MAX_APK_BYTES = 24 * 1024 * 1024; // Workers KV allows 25 MiB per value
const DEFAULT_REPO = "abegumbo-ui/MDM";
const MAX_IMAGE_BYTES = 200 * 1024;
const PNG_MAGIC = [0x89, 0x50, 0x4e, 0x47];

const isPng = (buf) => {
  const b = new Uint8Array(buf.slice(0, 4));
  return PNG_MAGIC.every((x, i) => b[i] === x);
};
const toBase64 = (buf) => {
  let bin = "";
  const bytes = new Uint8Array(buf);
  for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(bin);
};
const fromBase64 = (b64) => Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
const imageResponse = (b64) =>
  new Response(fromBase64(b64), { headers: { "content-type": "image/png", "cache-control": "private, max-age=86400" } });
const MAX_LOCK_MINUTES = 480; // 8 hours: emergency calls stay possible but this is a safety cap
const MASTER_ITERATIONS = 10000; // 10k, not 100k: cheap flip-phone CPUs took ~5s to check a code at 100k
const MAX_EVENTS = 60;
const MAX_TRAFFIC_LOG = 150;
const MAX_LOCATIONS = 200;
const MAX_CALLS = 300;
// Faster check-ins while someone is using the dashboard (see adminActive): every 30 s for 10 minutes.
const FAST_POLL_SECONDS = 30;
const FAST_WINDOW_MS = 10 * 60 * 1000;
// Right after the dashboard changes something: every 15 s for 3 minutes, so a change shows up on the phone fast.
const BURST_POLL_SECONDS = 15;
const BURST_WINDOW_MS = 3 * 60 * 1000;
let adminActionMarked = 0;
async function markAdminAction(env, now = Date.now()) {
  if (now - adminActionMarked < 20 * 1000) return; // at most one write per 20 s
  adminActionMarked = now;
  await env.STATE.put("adminAction", String(now));
}
let adminActiveMarked = 0; // this worker already noted it in the last minute: no storage read at all
async function markAdminActive(env, now = Date.now()) {
  if (now - adminActiveMarked < 60 * 1000) return;
  adminActiveMarked = now;
  const last = Number(await env.STATE.get("adminActive")) || 0;
  if (now - last > 60 * 1000) await env.STATE.put("adminActive", String(now)); // at most one write a minute
}
async function pollFor(env, now = Date.now()) {
  const last = Number(await env.STATE.get("adminActive")) || 0;
  if (now - last >= FAST_WINDOW_MS) return POLL_SECONDS; // one read only when nobody is on the dashboard
  const act = Number(await env.STATE.get("adminAction")) || 0;
  return now - act < BURST_WINDOW_MS ? BURST_POLL_SECONDS : FAST_POLL_SECONDS;
}

// Sites visited: the site a hostname belongs to ("m.youtube.com" -> "youtube.com", "www.ynet.co.il" -> "ynet.co.il").
const TWO_PART_TLDS = /\.(co|com|org|net|ac|gov|edu|k12|muni)\.[a-z]{2}$/;
export function siteOf(host) {
  const h = String(host || "").toLowerCase().replace(/\.$/, "");
  if (!/^[a-z0-9.-]+\.[a-z]{2,}$/.test(h)) return null; // not a name (an address, or junk)
  const parts = h.split(".");
  return parts.slice(TWO_PART_TLDS.test(h) ? -3 : -2).join(".");
}
// Background services every phone talks to all day; hidden so the list shows what was actually opened.
const SITE_NOISE = new Set(["googleapis.com", "gstatic.com", "googleusercontent.com", "gvt1.com", "gvt2.com", "gvt3.com", "doubleclick.net",
  "google-analytics.com", "googlesyndication.com", "googletagmanager.com", "googleadservices.com", "app-measurement.com", "crashlytics.com",
  "firebaseio.com", "firebaseinstallations.com", "ggpht.com", "ytimg.com", "googlevideo.com", "android.com", "akamaihd.net", "akamaized.net",
  "akamaiedge.net", "cloudfront.net", "fastly.net", "amazonaws.com", "cloudflare.com", "cloudflare-dns.com", "workers.dev", "github.com",
  "githubusercontent.com", "samsungcloud.com", "samsungapps.com", "samsungosp.com", "msftconnecttest.com", "gvt0.com", "fbcdn.net",
  "whatsapp.net", "cdninstagram.com", "tiktokcdn.com", "scorecardresearch.com", "adnxs.com", "criteo.com", "appsflyer.com", "adjust.com",
  "branch.io", "onesignal.com", "sentry.io", "newrelic.com", "nr-data.net", "mozilla.com", "mozilla.net", "xiaomi.com", "miui.com",
  "qualcomm.com", "e2ro.com", "edgekey.net", "edgesuite.net", "azureedge.net", "trafficmanager.net", "windows.net", "jsdelivr.net",
  "unity3d.com", "applovin.com", "inmobi.com", "ironsrc.mobi", "mopub.com", "facebook.net", "pki.goog", "digicert.com", "letsencrypt.org"]);
const MAX_SITES_PER_DAY = 400;
export function addSiteHistory(hist, entries, now = Date.now()) {
  const out = hist && typeof hist === "object" ? hist : {};
  for (const e of entries) {
    if (!e || e.kind === "net") continue;
    const site = siteOf(e.host);
    if (!site || SITE_NOISE.has(site)) continue;
    const day = new Date(Number(e.time) || now).toISOString().slice(0, 10);
    const dd = out[day] || (out[day] = {});
    const r = dd[site] || (dd[site] = [0, 0, ""]);
    r[0]++;
    if (e.blocked) r[1]++;
    if (!r[2] && e.app) r[2] = String(e.app).slice(0, 40);
  }
  for (const day of Object.keys(out).sort().slice(0, -14)) delete out[day]; // two weeks
  for (const day of Object.keys(out)) {
    const sites = Object.entries(out[day]);
    if (sites.length > MAX_SITES_PER_DAY) out[day] = Object.fromEntries(sites.sort((a, b) => b[1][0] - a[1][0]).slice(0, MAX_SITES_PER_DAY));
  }
  return out;
}
function siteHistoryView(hist) {
  const out = {};
  for (const day of Object.keys(hist || {}).sort().slice(-7)) {
    out[day] = Object.fromEntries(Object.entries(hist[day]).sort((a, b) => b[1][0] - a[1][0]).slice(0, 200));
  }
  return out;
}
const MAX_TEXTS = 300;
const CODE_TYPES = new Set(["enroll", "install", "uninstall", "browser", "freebrowse", "playstore"]);
const MAX_STORE_MINUTES = 24 * 60;
const MAX_FREEBROWSE_MINUTES = 240;
const PKG_RE = /^[A-Za-z0-9_.]{1,200}$/;
const B64_RE = /^[A-Za-z0-9+/=]+$/;
// Settings the phone's own Administrator screen may change (sent up as configPatch).
const PHONE_EDITABLE = ["browserFilter", "safeSearch", "youtubeDns", "blockImages", "blockImageSearch", "blockedWords", "pauseUntil", "recordSites", "inAppBrowsers", "internetOnlyApps", "screenTime", "callsIn", "callsOut", "callLog", "textLog", "textContent", "reportContacts", "syncContacts", "locationEvery", "blockAppGroups", "approveNew", "blockUnlisted", "blockWaStatus", "blockWaChannels", "messageAlerts", "messageGroups", "messageWords", "freezeUpdates", "disableBiometricUnlock", "autoUpdate", "reportWifi", "chromePolicy", "youtubeMode", "blockedParts", "internetOffUntil", "recordSearches", "blockAds", "waTextOnly", "sites", "callNumbers", "places", "filterSchedule", "internetApps", "recordVideos", "nudityAlerts", "pictureGuard", "browserLook", "noPictureSites", "skinWho", "youtubeAddon", "youtubeOffMessage", "adsCoveredSites", "adsAutoCoverOff", "youtubeSearchOff", "youtubeSearchPictures"];
// Friendly names for the chat apps message alerts look at.
const CHAT_APPS = { 'com.android.chrome': 'Chrome', 'org.mozilla.firefox': 'Firefox', 'com.sec.android.app.sbrowser': 'Samsung Internet', 'com.brave.browser': 'Brave', 'com.microsoft.emmx': 'Edge', 'com.google.android.youtube': 'YouTube', 'com.google.android.googlequicksearchbox': 'the Google app', 'com.familymdm.browser': 'our Browser', 'com.pinterest': 'Pinterest', 'com.whatsapp': 'WhatsApp', 'com.whatsapp.w4b': 'WhatsApp Business', 'org.telegram.messenger': 'Telegram', 'com.instagram.android': 'Instagram', 'com.facebook.orca': 'Messenger', 'com.facebook.mlite': 'Messenger Lite', 'com.snapchat.android': 'Snapchat', 'org.thoughtcrime.securesms': 'Signal', 'com.discord': 'Discord', 'com.zhiliaoapp.musically': 'TikTok', 'com.google.android.apps.messaging': 'Messages', 'com.samsung.android.messaging': 'Samsung Messages', 'com.kik.android': 'Kik', 'com.viber.voip': 'Viber', 'com.google.android.apps.dynamite': 'Google Chat', texts: 'a text message' };
export const appName = (pkg) => CHAT_APPS[pkg] || (pkg ? 'a chat app (' + pkg + ')' : 'a chat app');
const MAX_ICON_B64 = 30000;
const MAX_ICONS_PER_SYNC = 12;

const json = (data, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });
const html = (body, status = 200) =>
  new Response(body, { status, headers: { "content-type": "text/html;charset=utf-8" } });

// ---- crypto helpers ----
const enc = new TextEncoder();
const hex = (buf) => [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
const randomHex = (bytes) => hex(crypto.getRandomValues(new Uint8Array(bytes)));
const sha256 = async (s) => hex(await crypto.subtle.digest("SHA-256", enc.encode(s)));
async function hmac(secret, msg) {
  const key = await crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return hex(await crypto.subtle.sign("HMAC", key, enc.encode(msg)));
}
function safeEqual(a, b) {
  if (a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}
async function pbkdf2(password, saltHex, iterations) {
  const salt = Uint8Array.from(saltHex.match(/.{2}/g).map((b) => parseInt(b, 16)));
  const key = await crypto.subtle.importKey("raw", enc.encode(password), "PBKDF2", false, ["deriveBits"]);
  return hex(await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt, iterations }, key, 256));
}
// A Worker checking this, not a cheap phone CPU, so full-strength iterations (unlike the phone's own
// master-code check, which has to stay cheap enough for a flip-phone-class chip -- see Master.java).
const CLIENT_PW_ITERATIONS = 100000;
async function hashClientPassword(password) {
  const salt = randomHex(16);
  return { salt, hash: await pbkdf2(password, salt, CLIENT_PW_ITERATIONS), iterations: CLIENT_PW_ITERATIONS };
}
async function verifyClientPassword(password, rec) {
  if (!rec || !rec.salt || !rec.hash) return false;
  return safeEqual(await pbkdf2(password, rec.salt, rec.iterations || CLIENT_PW_ITERATIONS), rec.hash);
}
const LOGIN_NAME_RE = /^[a-z0-9][a-z0-9_-]{2,31}$/;

// ---- auth: the admin password (Worker secret) signs every session cookie, admin or client, so
// a per-client password never has to double as a signing key -- only who's logged in (and as
// what) changes between sessions. isAdmin() and isClient() below are the two shapes of "logged
// in" everything else branches on; getSession() itself never needs to be called twice.
async function sessionCookieFor(env, tag) {
  const exp = Math.floor(Date.now() / 1000) + SESSION_SECONDS;
  return `sess=${exp}.${encodeURIComponent(tag)}.${await hmac(env.ADMIN_PASSWORD, `sess:${tag}:${exp}`)}; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=${SESSION_SECONDS}`;
}
async function getSession(request, env) {
  const m = /(?:^|;\s*)sess=(\d+)\.([^.;]+)\.([0-9a-f]+)/.exec(request.headers.get("cookie") || "");
  if (!m) return null;
  const exp = Number(m[1]);
  if (!exp || exp < Date.now() / 1000) return null;
  const tag = decodeURIComponent(m[2]);
  if (!safeEqual(m[3], await hmac(env.ADMIN_PASSWORD, `sess:${tag}:${exp}`))) return null;
  if (tag === "admin") return { role: "admin" };
  const hm = /^helper:(.+)$/.exec(tag);
  if (hm) {
    // Another parent's login: sees exactly what its owner (the administrator, or one client) sees.
    const hp = await getJSON(env, `helper:${hm[1]}`, null);
    if (!hp) return null; // removed since this cookie was issued
    const extra = { helper: hp.id, helperName: hp.name, viewOnly: !!hp.viewOnly };
    if (hp.owner === "admin") return { role: "admin", ...extra };
    const oc = await getJSON(env, `client:${hp.owner}`, null);
    if (!oc) return null;
    return { role: "client", id: hp.owner, name: oc.name, ...extra };
  }
  const cm = /^client:(.+)$/.exec(tag);
  if (!cm) return null;
  const c = await getJSON(env, `client:${cm[1]}`, null);
  if (!c) return null; // the client account was deleted since this cookie was issued
  return { role: "client", id: cm[1], name: c.name };
}
const isAdmin = (session) => !!session && session.role === "admin";
const isClient = (session) => !!session && session.role === "client";

// ---- KV state ----
const getJSON = async (env, key, dflt) => (await env.STATE.get(key, "json")) ?? dflt;
const putJSON = (env, key, val, opts) => {
  invalidateListCache(key);
  return env.STATE.put(key, JSON.stringify(val), opts);
};
// Every device (and every standalone Browser) carries its own independent config -- apps, sites,
// and every Settings toggle. A brand-new device starts from a clean, empty config, never from
// another device's rules -- app rules set for one phone (e.g. because of something only relevant
// there, like a sideloaded tool) must never show up as a starting default on an unrelated phone.
async function configOf(env, d) {
  if (d.config) return { cfg: normalizeConfig(d.config), seeded: false };
  const cfg = normalizeConfig({});
  d.config = cfg;
  return { cfg, seeded: true };
}

// Listing any of the three device kinds below reads every single record from KV, one get() per
// device -- that's the normal, correct way to do it, but it means the dashboard's own 60-second
// auto-refresh (ui.js) burns through Workers KV's free-tier ~100,000 reads/day cap far faster than
// a handful of phones checking in every few minutes ever would, especially with more than one
// admin tab/session open at once. A short, per-isolate cache doesn't make that correct (a change
// can take up to CACHE_MS to show up through the auto-refresh alone), but every action in ui.js
// already reloads right after making it, so that's the only path that actually needs to be instant
// -- and it is, since it's a fresh, uncached call either way.
const CACHE_MS = 20 * 1000;
const listCaches = { device: { at: 0, value: null }, browserDevice: { at: 0, value: null }, winDevice: { at: 0, value: null }, client: { at: 0, value: null } };
// Called on every write/delete to a device:/browserDevice:/winDevice: key (see putJSON below, and
// the three explicit deletes) so a just-made change is never masked by a stale cached list -- the
// cache only ever saves a read when nothing relevant changed, never when something did.
function invalidateListCache(key) {
  const prefix = key.split(":")[0];
  if (listCaches[prefix]) listCaches[prefix].at = 0;
}
async function listByPrefix(env, prefix) {
  const cache = listCaches[prefix];
  const now = Date.now();
  if (cache.value && now - cache.at < CACHE_MS) return cache.value;
  const out = [];
  let cursor;
  do {
    const page = await env.STATE.list({ prefix: `${prefix}:`, ...(cursor && { cursor }) });
    for (const k of page.keys) {
      const d = await getJSON(env, k.name, null);
      if (d) out.push(d);
    }
    cursor = page.list_complete ? undefined : page.cursor;
  } while (cursor);
  cache.value = out;
  cache.at = now;
  return out;
}

async function listDevices(env) {
  return listByPrefix(env, "device");
}

// ---- client accounts: each owns a slice of devices/browsers, created by the admin only (see
// adminApi's /api/clients routes) -- never self-signup. A device's ownerId is null until a
// client's own enrollment code stamps it (see /agent/enroll and /browser/enroll below); null
// means admin-only, exactly today's behavior, so nothing already enrolled changes hands.
async function listClients(env) {
  return listByPrefix(env, "client");
}
const publicClient = (c) => ({ id: c.id, name: c.name, createdAt: c.createdAt });
// True if a client session may see this record at all; always true for the admin.
const canSee = (session, ownerId) => session.role === "admin" || ownerId === session.id;

const publicDevice = (d) => ({
  id: d.id,
  name: d.name,
  lastSeen: d.lastSeen,
  info: d.info || {},
  packages: d.packages || [],
  pending: (d.queue || []).length,
  inflight: d.inflight || [],
  results: (d.results || []).slice(-15),
  events: (d.events || []).slice(-40),
  trafficLog: (d.trafficLog || []).slice(-150),
  locations: (d.locations || []).slice(-100),
  calls: (d.calls || []).slice(-200),
  contacts: d.contacts || [],
  contactsAt: d.contactsAt || 0,
  siteHistory: siteHistoryView(d.siteHistory),
  texts: (d.texts || []).slice(-200),
  overrides: d.overrides || {},
  wifiNetworks: d.wifiNetworks || [],
  appCode: d.appCode || null,
  fallbackCode: d.fallbackCode || null,
  syncPaused: !!d.syncPaused,
  adminPin: d.adminPin ? { pin: d.adminPin.pin, ok: !!d.adminPin.ok } : null,
  siteRequests: d.siteRequests || [],
  appRequests: d.appRequests || [],
  usage: d.usage || {},
  messages: d.messages || [],
  msgAlerts: (d.msgAlerts || []).slice(-100),
  searches: (d.searches || []).slice(-300),
  videos: (d.videos || []).slice(-300),
  nudityAlerts: (d.nudityAlerts || []).slice(-100),
  shots: d.shots || [],
  pushLog: (d.pushLog || []).slice(-30),
  ownerId: d.ownerId || null,
});

// Browser devices: a standalone Browser app connected straight to this dashboard, with no agent/MDM
// at all. Each has its own "sites" allowlist, same as any agent-managed device, but no app policy,
// restrictions, or device-owner features of their own.
async function listBrowserDevices(env) {
  return listByPrefix(env, "browserDevice");
}

const publicBrowserDevice = (d) => ({
  id: d.id,
  name: d.name,
  lastSeen: d.lastSeen,
  info: d.info || {},
  siteRequests: d.siteRequests || [],
  siteHistory: siteHistoryView(d.siteHistory),
  ownerId: d.ownerId || null,
});

/** namePrefix: null names it from device info (code-based /browser/enroll); a string (e.g. "New
 * Browser") names it that plus a short id suffix, so several self-registrations stay tellable apart. */
async function createBrowserDevice(env, info, namePrefix, ownerId) {
  const id = randomHex(8);
  const secret = randomHex(24);
  info = info || {};
  await putJSON(env, `browserDevice:${id}`, {
    id,
    name: namePrefix ? `${namePrefix} (${id.slice(0, 4)})` : [info.manufacturer, info.model].filter(Boolean).join(" ") || id,
    tokenHash: await sha256(secret),
    lastSeen: Date.now(),
    info,
    siteRequests: [],
    ownerId: ownerId || null,
  });
  return { id, secret };
}

/** Finds the browser device a bearer token belongs to, or null. */
async function authBrowserDevice(request, env) {
  const m = /^Bearer ([0-9a-f]+)\.([0-9a-f]+)$/.exec(request.headers.get("authorization") || "");
  if (!m) return null;
  const key = `browserDevice:${m[1]}`;
  const d = await getJSON(env, key, null);
  if (!d || !safeEqual(d.tokenHash, await sha256(m[2]))) return null;
  return { key, d };
}

// Windows LockGuard devices: self-registers the moment it installs (no code to type, the same way
// a standalone Browser can with /browser/register), and shows up immediately as "New LockGuard
// Device" for the admin to rename and manage -- enable/disable lockdown, the allowed-programs
// list, and remote removal, all from here instead of only locally on the machine's own Setup app.
async function listWinDevices(env) {
  return listByPrefix(env, "winDevice");
}

// The PC filter's own switches (the rest of a PC's settings use the phones' config shape).
const normalizeWin = (w) => {
  const o = w && typeof w === "object" ? w : {};
  return {
    blockOtherBrowsers: o.blockOtherBrowsers !== false,
    blockVpnApps: o.blockVpnApps !== false,
    blockExtensions: o.blockExtensions === true,
    // LockGuard refuses to be stopped or removed by an administrator (code or dashboard only).
    protectFromAdmins: o.protectFromAdmins !== false,
    blockedPrograms: [...new Set((Array.isArray(o.blockedPrograms) ? o.blockedPrograms : []).map((p) => String(p).trim().toLowerCase()).filter((p) => /^[a-z0-9 ._()+-]{1,80}$/.test(p)))].slice(0, 200),
    // Per installed program (its full path, lower case), picked from the list the computer reports:
    // "noInternet" (opens, but cannot go online) or "blocked" (closed the moment it starts).
    programRules: Object.fromEntries(Object.entries(o.programRules && typeof o.programRules === "object" ? o.programRules : {})
      .filter(([p, m]) => /^[a-z]:\\[^<>"|?*\n]{1,300}\.exe$/i.test(p) && (m === "noInternet" || m === "blocked"))
      .map(([p, m]) => [p.toLowerCase(), m]).slice(0, 300)),
    // The unlock file: while a file with this name, holding this text, is in a user's Downloads
    // folder, every restriction on the computer is off. Both empty = no unlock file.
    unlockName: typeof o.unlockName === "string" && /^[A-Za-z0-9 ._-]{3,60}$/.test(o.unlockName.trim()) ? o.unlockName.trim() : "",
    unlockText: typeof o.unlockText === "string" ? o.unlockText.trim().slice(0, 100) : "",
    // Screen time for the child's (standard) Windows accounts: minutes a day (0 = none), bedtime, extra minutes today.
    screenLimit: Math.max(0, Math.min(1440, Math.round(Number(o.screenLimit) || 0))),
    bedFrom: /^([01]?\d|2[0-3]):[0-5]\d$/.test(String(o.bedFrom || "")) ? String(o.bedFrom) : "",
    bedTo: /^([01]?\d|2[0-3]):[0-5]\d$/.test(String(o.bedTo || "")) ? String(o.bedTo) : "",
    extraMinutes: Math.max(0, Math.min(600, Math.round(Number(o.extraMinutes) || 0))),
    extraAt: Math.max(0, Number(o.extraAt) || 0),
  };
};
/** What the computer gets for screen time, or null when none is set. */
const winScreenTime = (w) => (w.screenLimit > 0 || (w.bedFrom && w.bedTo && w.bedFrom !== w.bedTo))
  ? { limit: w.screenLimit, bedFrom: w.bedFrom, bedTo: w.bedTo, extraMinutes: w.extraMinutes, extraAt: w.extraAt }
  : null;
/** The filter's program lists, from the per-program choices: file names to close, full paths with no internet. */
const winProgramLists = (w) => {
  const rules = Object.entries(w.programRules || {});
  return {
    blockedPrograms: [...new Set([...w.blockedPrograms, ...rules.filter(([, m]) => m === "blocked").map(([p]) => p.split("\\").pop())])],
    noInternetPrograms: rules.filter(([, m]) => m === "noInternet").map(([p]) => p),
  };
};
// "Email only" (approved sites only, with these): what each email service needs to load and sign in.
export const EMAIL_SERVICES = {
  gmail: { label: "Gmail", open: "mail.google.com", hosts: ["mail.google.com", "accounts.google.com", "apis.google.com", "ogs.google.com", "contacts.google.com", "gstatic.com", "googleusercontent.com", "googleapis.com"] },
  outlook: { label: "Outlook / Hotmail", open: "outlook.live.com", hosts: ["outlook.live.com", "outlook.office.com", "outlook.office365.com", "login.live.com", "account.live.com", "login.microsoftonline.com", "office.net", "msauth.net", "msftauth.net", "live.net"] },
  yahoo: { label: "Yahoo Mail", open: "mail.yahoo.com", hosts: ["mail.yahoo.com", "login.yahoo.com", "yimg.com"] },
};
const EMAIL_HELPERS = new Set(Object.values(EMAIL_SERVICES).flatMap((e) => e.hosts.filter((h) => h !== e.open)));
const EMAIL_NAMES = Object.fromEntries(Object.values(EMAIL_SERVICES).map((e) => [e.open, e.label]));

/** The start page's buttons: the allowed sites someone would open (not the sign-in and picture servers). */
function startSites(allow) {
  return allow.filter((h) => !EMAIL_HELPERS.has(h)).slice(0, 24);
}

/** The newest published LockGuard (GitHub release "lockguard-vN" with LockGuard-Setup.exe), cached 10 minutes. */
let lockguardCache = { at: 0, value: null };
async function latestLockguard(env) {
  if (Date.now() - lockguardCache.at < 10 * 60 * 1000) return lockguardCache.value;
  lockguardCache.at = Date.now();
  // First the fixed pointer file CI publishes (no rate limit); GitHub's API only as a fallback.
  try {
    const repo = env.GITHUB_REPO || DEFAULT_REPO;
    const r = await fetch(`https://github.com/${repo}/releases/download/lockguard-latest/lockguard.json`, { cf: { cacheTtlByStatus: { "200-299": 300, "300-599": 0 } } });
    if (r.ok) {
      const v = await r.json();
      if (Number.isInteger(v.build) && typeof v.url === "string" && v.url.startsWith(`https://github.com/${repo}/releases/download/`)) {
        lockguardCache.value = { build: v.build, url: v.url, sha256: /^[0-9a-f]{64}$/i.test(v.sha256 || "") ? v.sha256 : "" };
        return lockguardCache.value;
      }
    }
  } catch {
    // fall through to the API
  }
  try {
    const repo = env.GITHUB_REPO || DEFAULT_REPO;
    const r = await fetch(`https://api.github.com/repos/${repo}/releases?per_page=30`, { headers: { "user-agent": "family-mdm-dashboard", accept: "application/vnd.github+json" } });
    if (!r.ok) return lockguardCache.value;
    for (const rel of await r.json()) {
      const m = /^lockguard-v(\d+)$/.exec(rel.tag_name || "");
      const asset = (rel.assets || []).find((a) => a.name === "LockGuard-Setup.exe");
      if (!m || !asset || rel.draft || rel.prerelease) continue;
      const digest = /^sha256:([0-9a-f]{64})$/i.exec(asset.digest || "");
      lockguardCache.value = { build: Number(m[1]), url: asset.browser_download_url, sha256: digest ? digest[1] : "" };
      break;
    }
  } catch {
    // keep whatever was known before
  }
  return lockguardCache.value;
}

/** The browsers' start page on a PC with "approved sites only": big buttons to the allowed sites. */
function startPageHtml(sites) {
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
  const buttons = sites.map((h) => `<a class="b" href="https://${esc(h)}/">${esc(EMAIL_NAMES[h] || h)}<span>${esc(h)}</span></a>`).join("");
  return `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Start</title>
<style>body{margin:0;font-family:system-ui,Segoe UI,sans-serif;background:#0b0d12;color:#f2f4f7;display:flex;min-height:100vh;align-items:center;justify-content:center}
main{width:min(720px,92vw);text-align:center}h1{font-weight:600;font-size:28px;margin:0 0 6px}p{color:#98a2b3;margin:0 0 28px}
.g{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:14px}.b{display:block;padding:26px 16px;border-radius:16px;background:#12151c;border:1px solid #232833;color:#f2f4f7;text-decoration:none;font-size:20px;font-weight:600}
.b:hover{border-color:#6366f1;background:#161a24}.b span{display:block;font-size:13px;font-weight:400;color:#98a2b3;margin-top:6px}</style></head>
<body><main><h1>What would you like to open?</h1><p>Only these sites are available on this computer.</p><div class="g">${buttons || "<p>No sites are allowed yet.</p>"}</div></main></body></html>`;
}

const WIN_DEFAULT_CONFIG = { browserFilter: "moderate", safeSearch: true, youtubeDns: "moderate", chromePolicy: { noIncognito: true } };

/** What the PC's filter applies right now (see windows/LockGuard.Common/FilterPolicy.cs). null = no web filter. */
function winFilter(env, d, now, dashboardHost) {
  if (!d.filterOn) return null;
  const c = normalizeConfig(d.config || WIN_DEFAULT_CONFIG);
  const level = effectiveFilter(c, now, d.info && d.info.tz);
  const w = normalizeWin(d.win);
  return {
    on: true,
    level: level === "off" ? "approved" : level, // on a PC, "off" (as on the phones' browser) means approved sites only
    base: filterFor(env, c).base,
    allow: [...new Set([...c.dnsAllowlist, ...c.dnsAllowTimed.map((t) => t.host)])], // with the ones allowed for a while
    block: c.dnsBlocklist,
    ads: !!c.blockAds, // "Block ads": the ad list on top of the level's lists (LockGuard 2026-11-13 and later)
    essential: [dashboardHost, "github.com", "githubusercontent.com", "msftconnecttest.com", "msftncsi.com"].filter(Boolean),
    words: c.blockedWords,
    safeSearch: c.safeSearch,
    youtube: c.youtubeDns,
    pauseUntil: c.pauseUntil,
    internetOffUntil: c.internetOffUntil,
    recordSites: c.recordSites,
    blockImages: c.blockImages,
    imagesAllow: c.imagesAllow,
    noIncognito: !!c.chromePolicy.noIncognito,
    startPage: level === "off" && dashboardHost ? `https://${dashboardHost}/start?sites=${encodeURIComponent(startSites([...c.dnsAllowlist, ...c.dnsAllowTimed.map((t) => t.host)]).join(","))}` : "",
    ...w,
    ...winProgramLists(w),
  };
}

const publicWinDevice = (d) => ({
  id: d.id,
  name: d.name,
  lastSeen: d.lastSeen,
  info: d.info || {},
  filterOn: !!d.filterOn,
  config: normalizeConfig(d.config || WIN_DEFAULT_CONFIG),
  win: normalizeWin(d.win),
  siteHistory: siteHistoryView(d.siteHistory),
  events: (d.events || []).slice(-40),
  enabled: !!d.enabled,
  allowedPrograms: d.allowedPrograms || [],
  reportedEnabled: !!d.reportedEnabled,
  reportedAllowedPrograms: d.reportedAllowedPrograms || [],
  programs: d.programs || [],
  programsAt: d.programsAt || 0,
  siteRequests: d.siteRequests || [],
  shots: d.shots || [],
  pending: (d.queue || []).length,
});

async function createWinDevice(env, info) {
  const id = randomHex(8);
  const secret = randomHex(24);
  info = info || {};
  await putJSON(env, `winDevice:${id}`, {
    id,
    name: `New LockGuard Device (${id.slice(0, 4)})`,
    tokenHash: await sha256(secret),
    lastSeen: Date.now(),
    info,
    enabled: false,
    // New computers start with the web filter on (Moderate, SafeSearch, YouTube Restricted Mode).
    filterOn: true,
    config: normalizeConfig(WIN_DEFAULT_CONFIG),
    win: normalizeWin({}),
    allowedPrograms: [],
    reportedEnabled: false,
    reportedAllowedPrograms: [],
    queue: [],
  });
  return { id, secret };
}

/** Finds the LockGuard device a bearer token belongs to, or null. */
async function authWinDevice(request, env) {
  const m = /^Bearer ([0-9a-f]+)\.([0-9a-f]+)$/.exec(request.headers.get("authorization") || "");
  if (!m) return null;
  const key = `winDevice:${m[1]}`;
  const d = await getJSON(env, key, null);
  if (!d || !safeEqual(d.tokenHash, await sha256(m[2]))) return null;
  return { key, d };
}

// Latest agent build, published by GitHub Actions next to the APK. Cached at the edge for 5 minutes.
async function latestAgent(env) {
  const repo = env.GITHUB_REPO || DEFAULT_REPO;
  const base = `https://github.com/${repo}/releases/download/latest`;
  try {
    // Cache a real answer for a while, but never a failure -- a brief 404 mid-release (or any other
    // hiccup) used to get cached for the full 5 minutes right along with a real one, turning a
    // few-second gap into "could not find the latest build" for anyone who asked during it.
    const r = await fetch(`${base}/version.json`, { cf: { cacheTtlByStatus: { "200-299": 300, "300-599": 0 } } });
    if (!r.ok) return null;
    const v = await r.json();
    if (!Number.isInteger(v.versionCode)) return null;
    return {
      versionCode: v.versionCode,
      apkUrl: `${base}/mdm-agent.apk`,
      sha256: v.sha256 || null,
      // The "enroll" build (fewer permissions, see android/app/build.gradle): only for a brand-new
      // phone's first install. It self-updates to the full build above right after enrolling.
      enrollApkUrl: `${base}/mdm-agent-enroll.apk`,
      enrollSha256: v.enrollSha256 || null,
    };
  } catch {
    return null;
  }
}

// {package: revision} of the custom icons, so phones in home-screen mode know which to download.
async function setIconRev(env, pkg, rev) {
  const index = (await env.STATE.get("iconIndex", "json")) || {};
  if (rev === null) delete index[pkg];
  else index[pkg] = rev;
  await env.STATE.put("iconIndex", JSON.stringify(index));
}

/**
 * Mints a short-lived (~50 minute) Google Play Store auth token from the long-lived master token
 * for one dedicated Google account, set up once via tools/get-play-token.py and stored only as
 * Cloudflare secrets (PLAY_EMAIL, PLAY_ANDROID_ID, PLAY_MASTER_TOKEN) -- never on a phone. Lets the
 * agent keep a small allow-listed set of apps (Waze, Google Maps, Android Auto, Play services --
 * whatever it actually takes to run them) self-updating through the real Play Store protocol,
 * while every other install/update stays blocked. Same token-refresh exchange gpsoauth/gplayapi/
 * Aurora Store's own "dispenser" all use against Google's own endpoint -- this just runs it from
 * the Worker instead of trusting someone else's dispenser server.
 */
async function playToken(env) {
  if (!env.PLAY_EMAIL || !env.PLAY_ANDROID_ID || !env.PLAY_MASTER_TOKEN) {
    return { error: "Play Store updates are not set up on this dashboard -- see tools/get-play-token.py" };
  }
  const body = new URLSearchParams({
    accountType: "HOSTED_OR_GOOGLE",
    Email: env.PLAY_EMAIL,
    has_permission: "1",
    EncryptedPasswd: env.PLAY_MASTER_TOKEN,
    service: "androidmarket",
    source: "android",
    androidId: env.PLAY_ANDROID_ID,
    app: "com.android.vending",
    client_sig: "61ed377e85d386a8dfee6b864bd85b0bcfcfb67e",
    device_country: "us",
    operatorCountry: "us",
    lang: "en",
    sdk_version: "30",
  });
  const res = await fetch("https://android.clients.google.com/auth", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded", "user-agent": "GoogleAuth/1.4" },
    body: body.toString(),
  });
  const text = await res.text();
  const fields = Object.fromEntries(
    text.split("\n").filter((l) => l.includes("=")).map((l) => { const i = l.indexOf("="); return [l.slice(0, i), l.slice(i + 1)]; })
  );
  if (!fields.Auth) return { error: "Google did not return a token (" + (fields.Error || res.status) + ")" };
  return { auth: fields.Auth, email: env.PLAY_EMAIL, androidId: env.PLAY_ANDROID_ID };
}

/** Finds the device a bearer token belongs to, or null. */
async function authDevice(request, env) {
  const m = /^Bearer ([0-9a-f]+)\.([0-9a-f]+)$/.exec(request.headers.get("authorization") || "");
  if (!m) return null;
  const key = `device:${m[1]}`;
  const d = await getJSON(env, key, null);
  if (!d || !safeEqual(d.tokenHash, await sha256(m[2]))) return null;
  return { key, d };
}

// ---- admin API (cookie auth) ----
// Wraps every dashboard action so that anything that CHANGES something is written to the change
// history (who, what, which device) -- never the contents, so no PIN or code ever lands there.
async function adminApi(request, env, url, session) {
  const method = request.method;
  let body = null;
  if (method !== "GET" && method !== "HEAD" && (request.headers.get("content-type") || "").includes("json")) {
    body = await request.clone().json().catch(() => null);
  }
  const res = await adminApiInner(request, env, url, session);
  if (method !== "GET" && method !== "HEAD" && res.ok) {
    await markAdminAction(env); // phones check in every 15 s for 3 minutes after a change
    const change = describeChange(method, url.pathname, body);
    if (change) {
      await auditAdd(env, {
        who: session.helper ? `Parent "${session.helperName}"` : session.role === "admin" ? "Administrator" : `Client "${session.name}"`,
        text: change.text,
        ...(change.target && { target: change.target }),
      });
    }
  }
  return res;
}

async function adminApiInner(request, env, url, session) {
  const path = url.pathname;
  const method = request.method;

  // ---- other parents' logins ----
  if (session.viewOnly && method !== "GET" && method !== "HEAD") return json({ error: "This login can only look, not change anything." }, 403);
  if (session.helper && (path.startsWith("/api/2fa") || path.startsWith("/api/helpers") || (path.startsWith("/api/clients") && method !== "GET"))) {
    return json({ error: "Only the main login can do this." }, 403);
  }
  const hpm = /^\/api\/helpers(?:\/([a-z0-9_-]{3,32}))?$/.exec(path);
  if (hpm) {
    const owner = session.role === "admin" ? "admin" : session.id;
    if (!hpm[1] && method === "GET") {
      const out = [];
      for (const k of (await env.STATE.list({ prefix: "helper:" })).keys) {
        const hp = await getJSON(env, k.name, null);
        if (hp && hp.owner === owner) out.push({ id: hp.id, name: hp.name, viewOnly: !!hp.viewOnly, createdAt: hp.createdAt });
      }
      return json(out.sort((a, b) => a.createdAt - b.createdAt));
    }
    if (!hpm[1] && method === "POST") {
      const hb = await request.json().catch(() => ({}));
      const loginName = String(hb.loginName || "").trim().toLowerCase();
      const password = String(hb.password || "");
      if (!LOGIN_NAME_RE.test(loginName)) return json({ error: "Login name must be 3-32 lowercase letters, numbers, - or _, starting with a letter or number" }, 400);
      if (password.length < 8) return json({ error: "Password must be at least 8 characters" }, 400);
      if ((await getJSON(env, `helper:${loginName}`, null)) || (await getJSON(env, `client:${loginName}`, null))) return json({ error: "That login name is already taken" }, 400);
      const rec = { id: loginName, name: String(hb.name || loginName).trim().slice(0, 60) || loginName, owner, viewOnly: hb.viewOnly === true, createdAt: Date.now(), ...(await hashClientPassword(password)) };
      await putJSON(env, `helper:${loginName}`, rec);
      return json({ id: rec.id, name: rec.name, viewOnly: rec.viewOnly, createdAt: rec.createdAt });
    }
    if (hpm[1] && method === "DELETE") {
      const hp = await getJSON(env, `helper:${hpm[1]}`, null);
      if (!hp || hp.owner !== owner) return json({ error: "Not found" }, 404);
      await env.STATE.delete(`helper:${hpm[1]}`);
      return json({ ok: true });
    }
    return json({ error: "Not found" }, 404);
  }

  // ---- templates: a phone's settings saved under a name, to apply to other phones ----
  const tpl = /^\/api\/templates(?:\/([0-9a-f]{12})(\/apply)?)?$/.exec(path);
  if (tpl) {
    // Read here: this block runs before the request body is parsed for the rest of the API.
    const tb = method === "POST" ? await request.json().catch(() => ({})) : {};
    const mine = (t) => t && canSee(session, t.ownerId);
    if (!tpl[1] && method === "GET") {
      const out = [];
      let cursor;
      do {
        const page = await env.STATE.list({ prefix: "template:", ...(cursor && { cursor }) });
        for (const k of page.keys) {
          const t = await getJSON(env, k.name, null);
          if (mine(t)) out.push({ id: t.id, name: t.name, from: t.from, createdAt: t.createdAt, summary: summarizeConfigChange(normalizeConfig({}), normalizeConfig(t.config)) });
        }
        cursor = page.list_complete ? null : page.cursor;
      } while (cursor);
      return json(out.sort((a, b) => b.createdAt - a.createdAt));
    }
    if (!tpl[1] && method === "POST") {
      const name = String(tb.name || "").trim().slice(0, 60);
      if (!name) return json({ error: "Give the template a name" }, 400);
      const src = /^[0-9a-f]{1,32}$/.test(tb.deviceId || "") ? await getJSON(env, `device:${tb.deviceId}`, null) : null;
      if (!src || !canSee(session, src.ownerId)) return json({ error: "Unknown device" }, 404);
      const { cfg } = await configOf(env, src);
      const { frpAccounts, pauseUntil, ...config } = cfg; // reset-protection accounts belong to one phone, never copied; nor does a pause
      const id = randomHex(6);
      await putJSON(env, `template:${id}`, { id, name, from: src.name, config, createdAt: Date.now(), ownerId: isClient(session) ? session.id : null });
      return json({ id, name });
    }
    const key = `template:${tpl[1]}`;
    const t = tpl[1] ? await getJSON(env, key, null) : null;
    if (!mine(t)) return json({ error: "Unknown template" }, 404);
    if (!tpl[2] && method === "DELETE") {
      await env.STATE.delete(key);
      return json({ ok: true });
    }
    if (tpl[2] && method === "POST") {
      const ids = (Array.isArray(tb.deviceIds) ? tb.deviceIds : []).filter((x) => /^[0-9a-f]{1,32}$/.test(x)).slice(0, 200);
      let applied = 0;
      for (const id of ids) {
        const dk = `device:${id}`;
        const d = await getJSON(env, dk, null);
        if (!d || !canSee(session, d.ownerId)) continue;
        const { cfg: before } = await configOf(env, d);
        const next = normalizeConfig({ ...t.config, frpAccounts: before.frpAccounts }); // keep this phone's own reset-protection accounts
        if (next.approveNew && !before.approveNew) d.known = (d.packages || []).map((p) => p.p);
        d.config = next;
        const changes = summarizeConfigChange(before, next);
        d.pushLog = [...(d.pushLog || []), { at: Date.now(), changes: [`Template "${t.name}" applied`, ...changes] }].slice(-30);
        await putJSON(env, dk, d);
        applied++;
      }
      return json({ applied });
    }
    return json({ error: "Not found" }, 404);
  }

  // A YouTube video's title and channel, so a request can be approved by name instead of as a bare address.
  if (path === "/api/youtube-info" && method === "GET") {
    const id = youtubeVideoId(url.searchParams.get("url") || "");
    if (!id) return json({ error: "That is not a YouTube video link" }, 400);
    try {
      const r = await fetch("https://www.youtube.com/oembed?format=json&url=" + encodeURIComponent("https://www.youtube.com/watch?v=" + id), { signal: AbortSignal.timeout(6000) });
      if (!r.ok) return json({ videoId: id, error: "YouTube gave no details for it (private, removed or not shareable)" });
      const j = await r.json();
      return json({ videoId: id, title: String(j.title || "").slice(0, 120), author: String(j.author_name || "").slice(0, 80), handle: youtubeChannelHandle(String(j.author_url || "")) });
    } catch {
      return json({ videoId: id, error: "Could not reach YouTube just now" });
    }
  }

  // ---- notifications on a parent's own phone (any parent login) ----
  if (path === "/api/push/key" && method === "GET") return json({ publicKey: (await vapidKeys(env)).publicKey });
  if ((path === "/api/push/subscribe" || path === "/api/push/unsubscribe") && method === "POST") {
    // Alerts cover every phone on this dashboard, so only the administrator (and their other parents) can sign up.
    if (session.role !== "admin") return json({ error: "Administrator only" }, 403);
    const b = await request.json().catch(() => ({}));
    const endpoint = b && b.endpoint;
    if (!validPushEndpoint(endpoint)) return json({ error: "This browser's notification address was not recognised." }, 400);
    const a = await getAlerts(env);
    const others = (a.push || []).filter((x) => x.endpoint !== endpoint);
    a.pushOrigin = url.origin; // the push services want a real contact: this dashboard's own address
    a.push = path.endsWith("/subscribe") ? [...others, { endpoint, at: Date.now(), who: session.helper ? String(session.helperName || "Parent") : "Administrator" }].slice(-20) : others;
    await putJSON(env, ALERTS_KEY, a);
    return json({ ok: true, pushCount: a.push.length });
  }

  // ---- admin-only security pages: change history, two-step login, alerts ----
  if (path === "/api/audit" || path.startsWith("/api/2fa") || path.startsWith("/api/alerts")) {
    if (!isAdmin(session)) return json({ error: "Administrator only" }, 403);
    if (path === "/api/audit" && method === "GET") return json({ entries: await auditList(env) });
    if (path === "/api/2fa" && method === "GET") return json({ enabled: !!(await getJSON(env, "admin:totp", null)) });
    if (path === "/api/2fa/start" && method === "POST") {
      if (await getJSON(env, "admin:totp", null)) return json({ error: "Two-step login is already on. Turn it off first to set it up again." }, 409);
      const secret = newTotpSecret();
      await putJSON(env, "admin:totp:pending", { secret, at: Date.now() }, { expirationTtl: 15 * 60 });
      return json({ secret, uri: totpUri(secret, "Administrator", "MDM Dashboard") });
    }
    if (path === "/api/2fa/enable" && method === "POST") {
      const b = await request.json().catch(() => ({}));
      const pending = await getJSON(env, "admin:totp:pending", null);
      if (!pending) return json({ error: "That set-up expired. Start again." }, 400);
      const step = await totpVerify(pending.secret, b.code, 0);
      if (!step) return json({ error: "That code is not right. Check the code in your authenticator app and try again." }, 400);
      await putJSON(env, "admin:totp", { secret: pending.secret, step, at: Date.now() });
      await env.STATE.delete("admin:totp:pending");
      return json({ ok: true });
    }
    if (path === "/api/2fa/disable" && method === "POST") {
      const b = await request.json().catch(() => ({}));
      const cur = await getJSON(env, "admin:totp", null);
      if (!cur) return json({ ok: true });
      const step = await totpVerify(cur.secret, b.code, cur.step || 0);
      if (!step) return json({ error: "That code is not right." }, 400);
      await env.STATE.delete("admin:totp");
      return json({ ok: true });
    }
    if (path === "/api/alerts" && method === "GET") return json(publicAlerts(await getAlerts(env)));
    if (path === "/api/alerts" && method === "PUT") {
      const b = await request.json().catch(() => ({}));
      let next;
      try {
        next = mergeAlerts(await getAlerts(env), b || {});
      } catch (e) {
        return json({ error: e.message }, 400);
      }
      await putJSON(env, ALERTS_KEY, next);
      return json(publicAlerts(next));
    }
    if (path === "/api/alerts/test" && method === "POST") {
      const r = await sendAlert(env, "MDM test alert", "If you can read this, alerts are working.");
      return r.ok ? json({ ok: true }) : json({ error: r.error }, 400);
    }
    return json({ error: "Not found" }, 404);
  }

  if (path === "/api/apk" && method === "PUT") {
    // Raw APK bytes from the dashboard's file picker. Stored for a week, fetched once by the phone.
    const buf = await request.arrayBuffer();
    const head = new Uint8Array(buf.slice(0, 2));
    if (buf.byteLength < 1000 || buf.byteLength > MAX_APK_BYTES) {
      return json({ error: `APK must be between 1 KB and ${MAX_APK_BYTES / 1048576} MB` }, 400);
    }
    if (head[0] !== 0x50 || head[1] !== 0x4b) return json({ error: "That does not look like an APK file" }, 400);
    const id = randomHex(6);
    const name = String(url.searchParams.get("name") || "app.apk").replace(/[^\w. -]/g, "").slice(0, 60);
    await env.STATE.put(`apk:${id}`, buf, { expirationTtl: 7 * 86400, metadata: { name, size: buf.byteLength } });
    return json({ id, name, size: buf.byteLength });
  }
  if (path === "/api/latest-agent" && method === "GET") return json({ latest: await latestAgent(env) });

  // Custom app icons (shown on the dashboard) and the logo (shown on the phones).
  const iconPut = /^\/api\/icon\/([A-Za-z0-9_.]{1,200})$/.exec(path);
  if (iconPut && (method === "PUT" || method === "DELETE")) {
    if (method === "DELETE") {
      await env.STATE.delete(`iconc:${iconPut[1]}`);
      await setIconRev(env, iconPut[1], null);
      return json({ ok: true });
    }
    const buf = await request.arrayBuffer();
    if (buf.byteLength < 50 || buf.byteLength > MAX_IMAGE_BYTES || !isPng(buf)) return json({ error: "Use a PNG image under 200 KB" }, 400);
    await env.STATE.put(`iconc:${iconPut[1]}`, toBase64(buf));
    await setIconRev(env, iconPut[1], Date.now());
    return json({ ok: true });
  }
  if (path === "/api/logo") {
    if (method === "GET") {
      const b64 = await env.STATE.get("logo");
      return b64 ? imageResponse(b64) : new Response(null, { status: 404 });
    }
    if (method === "DELETE") {
      if (!isAdmin(session)) return json({ error: "Admin only" }, 403);
      await env.STATE.delete("logo");
      await env.STATE.delete("logoRev");
      logoRevCache = 0;
      logoRevCacheAt = Date.now();
      return json({ ok: true });
    }
    if (method === "PUT") {
      if (!isAdmin(session)) return json({ error: "Admin only" }, 403);
      const buf = await request.arrayBuffer();
      if (buf.byteLength < 50 || buf.byteLength > MAX_IMAGE_BYTES || !isPng(buf)) return json({ error: "Use a PNG image under 200 KB" }, 400);
      await env.STATE.put("logo", toBase64(buf));
      const rev = Date.now();
      await env.STATE.put("logoRev", String(rev));
      logoRevCache = rev;
      logoRevCacheAt = Date.now();
      return json({ ok: true });
    }
  }
  const body = method === "GET" || method === "DELETE" ? null : await request.json().catch(() => ({}));

  if (path === "/api/clients" && method === "GET") {
    if (!isAdmin(session)) return json({ error: "Admin only" }, 403);
    return json((await listClients(env)).map(publicClient));
  }
  if (path === "/api/clients" && method === "POST") {
    if (!isAdmin(session)) return json({ error: "Admin only" }, 403);
    const loginName = String(body.loginName || "").trim().toLowerCase();
    const name = String(body.name || loginName).slice(0, 80);
    const password = String(body.password || "");
    if (!LOGIN_NAME_RE.test(loginName)) return json({ error: "Login name must be 3-32 lowercase letters, numbers, - or _, starting with a letter or number" }, 400);
    if (password.length < 6) return json({ error: "Password must be at least 6 characters" }, 400);
    if ((await getJSON(env, `client:${loginName}`, null)) || (await getJSON(env, `helper:${loginName}`, null))) return json({ error: "That login name is already taken" }, 400);
    const rec = { id: loginName, name, createdAt: Date.now(), ...(await hashClientPassword(password)) };
    await putJSON(env, `client:${loginName}`, rec);
    return json(publicClient(rec));
  }
  const clientMatch = /^\/api\/clients\/([a-z0-9_-]{3,32})$/.exec(path);
  if (clientMatch && method === "DELETE") {
    if (!isAdmin(session)) return json({ error: "Admin only" }, 403);
    // Devices this client owned aren't deleted or reassigned -- they just become invisible to
    // every client (their ownerId now points at an account that can't log in any more) until the
    // admin, who can always see everything, hands them to a new or recreated client account.
    await env.STATE.delete(`client:${clientMatch[1]}`);
    invalidateListCache(`client:${clientMatch[1]}`);
    return json({ ok: true });
  }
  if (path === "/api/state" && method === "GET") {
    return json({
      restrictions: RESTRICTIONS,
      appGroups: APP_GROUPS,
      messageWordGroups: Object.fromEntries(Object.entries(MESSAGE_WORD_GROUPS).map(([k, v]) => [k, { label: v.label, words: v.words }])),
      userMenu: USER_MENU,
      origin: url.origin,
      masterIterations: MASTER_ITERATIONS,
      session: {
        ...(session.role === "admin" ? { role: "admin" } : { role: "client", id: session.id, name: session.name }),
        ...(session.helper ? { helper: session.helperName, viewOnly: !!session.viewOnly } : {}),
      },
    });
  }
  const devConfig = /^\/api\/devices\/([0-9a-f]+)\/(config|master|clone-from|owner)$/.exec(path);
  if (devConfig) {
    const d = await getJSON(env, `device:${devConfig[1]}`, null);
    if (!d || !canSee(session, d.ownerId)) return json({ error: "Unknown device" }, 404);
    const key = `device:${devConfig[1]}`;
    if (devConfig[2] === "owner" && method === "PUT") {
      // Admin-only reassignment: hand an existing (or ownerless) device to a client account, or
      // take it back to admin-only. Separate from enrollment-time ownership (see /agent/enroll) --
      // this is for a device that's already here and needs to move, same as a deleted client's
      // devices sitting there until someone gives them to a new or recreated account.
      if (!isAdmin(session)) return json({ error: "Admin only" }, 403);
      const ownerId = body.ownerId == null ? null : String(body.ownerId);
      if (ownerId !== null) {
        const client = await getJSON(env, `client:${ownerId}`, null);
        if (!client) return json({ error: "Unknown client" }, 404);
      }
      d.ownerId = ownerId;
      await putJSON(env, key, d);
      return json({ ok: true });
    }
    if (devConfig[2] === "config" && method === "PUT") {
      await markAdminActive(env); // phones check in every 30 s for a while
      const { cfg: before } = await configOf(env, d);
      const next = normalizeConfig(body);
      if (next.approveNew && !before.approveNew) d.known = (d.packages || []).map((p) => p.p); // everything installed now counts as already approved
      d.config = next;
      const changes = summarizeConfigChange(before, next);
      if (changes.length) d.pushLog = [...(d.pushLog || []), { at: Date.now(), changes }].slice(-30);
      await putJSON(env, key, d);
      // Echoed back so the dashboard can tell whether every pasted line actually survived
      // normalization -- a hostname-looking regex silently drops anything that doesn't match
      // (a stray "*", a "https://" prefix, AdBlock-style "||...^" syntax), with no error, which
      // looked exactly like a save doing nothing at all when an entire pasted list was rejected.
      return json({ ok: true, dnsBlocklist: next.dnsBlocklist, dnsAllowlist: next.dnsAllowlist });
    }
    if (devConfig[2] === "master" && method === "PUT") {
      // The browser derives the hash (PBKDF2), so the code itself never reaches the server.
      if (!/^[0-9a-f]{32}$/.test(body.salt || "") || !/^[0-9a-f]{64}$/.test(body.hash || "")) return json({ error: "Invalid master code data" }, 400);
      d.master = { salt: body.salt, hash: body.hash, iterations: MASTER_ITERATIONS };
      // Newer than whatever the phone itself might have set, same reasoning as every other
      // phone-settable value below: the most recent change, from either side, wins.
      d.masterRev = Date.now();
      await putJSON(env, key, d);
      return json({ ok: true });
    }
    if (devConfig[2] === "master" && method === "DELETE") {
      d.master = null;
      d.masterRev = Date.now();
      await putJSON(env, key, d);
      return json({ ok: true });
    }
    if (devConfig[2] === "clone-from" && method === "POST") {
      const src = body.browser
        ? await getJSON(env, `browserDevice:${body.sourceId}`, null)
        : await getJSON(env, `device:${body.sourceId}`, null);
      if (!src || !canSee(session, src.ownerId)) return json({ error: "Unknown source" }, 404);
      const { cfg: srcCfg } = await configOf(env, src);
      d.config = normalizeConfig(body.browser ? { sites: srcCfg.sites } : srcCfg);
      await putJSON(env, key, d);
      return json({ ok: true });
    }
  }
  const brConfig = /^\/api\/browsers\/([0-9a-f]+)\/(config|clone-from|owner)$/.exec(path);
  if (brConfig) {
    const key = `browserDevice:${brConfig[1]}`;
    const d = await getJSON(env, key, null);
    if (!d || !canSee(session, d.ownerId)) return json({ error: "Unknown browser" }, 404);
    if (brConfig[2] === "owner" && method === "PUT") {
      if (!isAdmin(session)) return json({ error: "Admin only" }, 403);
      const ownerId = body.ownerId == null ? null : String(body.ownerId);
      if (ownerId !== null) {
        const client = await getJSON(env, `client:${ownerId}`, null);
        if (!client) return json({ error: "Unknown client" }, 404);
      }
      d.ownerId = ownerId;
      await putJSON(env, key, d);
      return json({ ok: true });
    }
    if (brConfig[2] === "config" && method === "PUT") {
      // An omitted browserFilter becomes "off" (allowlist-only), the stricter setting, so a stale
      // dashboard tab can never accidentally open the web.
      d.config = normalizeConfig({ sites: body.sites, restrictBrowsing: body.restrictBrowsing, browserFilter: body.browserFilter, youtubeMode: body.youtubeMode, blockImages: body.blockImages, blockedWords: body.blockedWords, blockImageSearch: body.blockImageSearch,
        pictureGuard: body.pictureGuard, openWeb: body.openWeb, safeSearchOff: body.safeSearchOff, adultListOff: body.adultListOff, searchOff: body.searchOff, browserLook: body.browserLook, noPictureSites: body.noPictureSites, skinWho: body.skinWho, blockAds: body.blockAds, youtubeOffMessage: body.youtubeOffMessage, adsCoveredSites: body.adsCoveredSites, adsAutoCoverOff: body.adsAutoCoverOff, youtubeSearchOff: body.youtubeSearchOff, youtubeSearchPictures: body.youtubeSearchPictures });
      await putJSON(env, key, d);
      return json({ ok: true });
    }
    if (brConfig[2] === "clone-from" && method === "POST") {
      const src = body.browser
        ? await getJSON(env, `browserDevice:${body.sourceId}`, null)
        : await getJSON(env, `device:${body.sourceId}`, null);
      if (!src || !canSee(session, src.ownerId)) return json({ error: "Unknown source" }, 404);
      const { cfg: srcCfg } = await configOf(env, src);
      d.config = normalizeConfig({ sites: srcCfg.sites });
      await putJSON(env, key, d);
      return json({ ok: true });
    }
  }
  if (path === "/api/codes" && method === "POST") {
    if (!CODE_TYPES.has(body.type)) return json({ error: "Unknown code type" }, 400);
    const code = randomHex(4).toUpperCase();
    const value = { created: Date.now() };
    if (body.type === "freebrowse" || body.type === "playstore") {
      const minutes = Number.isInteger(body.minutes) ? Math.min(Math.max(body.minutes, 5), MAX_FREEBROWSE_MINUTES) : 60;
      value.minutes = minutes;
    }
    // Generated from inside one device's own page: only that device can redeem it, so handing the
    // code to a different device (or a sibling's phone) does nothing. Enroll codes can't work this
    // way -- a device has no identity yet before it enrolls -- so those stay usable by whoever is first.
    if (["install", "uninstall", "freebrowse", "playstore"].includes(body.type) && /^[0-9a-f]{1,32}$/.test(body.deviceId || "")) {
      const owner = await getJSON(env, `device:${body.deviceId}`, null);
      if (!owner || !canSee(session, owner.ownerId)) return json({ error: "Unknown device" }, 404);
      value.deviceId = body.deviceId;
    }
    // Stamped onto an enroll/browser code so the device it creates is this client's from the
    // moment it exists -- see /agent/enroll and /browser/enroll. Admin-generated codes stay
    // ownerless, same as every device enrolled before client accounts existed.
    if (isClient(session)) value.ownerId = session.id;
    await putJSON(env, `code:${body.type}:${code}`, value, { expirationTtl: 3600 });
    const reply = { code, type: body.type, server: url.origin, expiresInSeconds: 3600, minutes: value.minutes };
    if (body.type === "enroll") {
      const latest = await latestAgent(env);
      // The "enroll" build (fewer permissions -- see android/app/build.gradle) is what a brand-new
      // phone should install; it self-updates to the full build right after becoming device owner.
      // Fall back to the full build if an enroll checksum isn't published yet (e.g. right after this
      // feature first ships, before a build has run), so enrollment never just breaks.
      if (latest && latest.enrollSha256) {
        reply.apkUrl = latest.enrollApkUrl;
        reply.sha256 = latest.enrollSha256;
      } else if (latest && latest.sha256) {
        reply.apkUrl = latest.apkUrl;
        reply.sha256 = latest.sha256;
      }
    }
    return json(reply);
  }
  const iconMatch = /^\/api\/icon\/([A-Za-z0-9_.]+)$/.exec(path);
  if (iconMatch && method === "GET") {
    const b64 = (await env.STATE.get(`iconc:${iconMatch[1]}`)) || (await env.STATE.get(`icon:${iconMatch[1]}`));
    return b64 ? imageResponse(b64) : new Response(null, { status: 404 });
  }
  if (path === "/api/devices" && method === "GET") {
    await markAdminActive(env); // phones check in every 30 s for a while (see pollFor)
    const devices = (await listDevices(env)).filter((d) => canSee(session, d.ownerId));
    return json(
      await Promise.all(devices.map(async (d) => {
        const { cfg } = await configOf(env, d);
        return {
          ...publicDevice(d),
          config: cfg,
          masterSet: !!d.master,
          packages: (d.packages || []).map((p) => ({ ...p, protected: isProtected(p.p) })),
          applied: buildAgentPolicy(cfg, (d.packages || []).map((p) => p.p), { known: d.known, overrides: d.overrides, categories: categoriesOf(d) }),
        };
      })),
    );
  }
  if (path === "/api/browsers" && method === "GET") {
    const browsers = (await listBrowserDevices(env)).filter((d) => canSee(session, d.ownerId));
    return json(
      await Promise.all(browsers.map(async (d) => {
        const { cfg } = await configOf(env, d);
        return { ...publicBrowserDevice(d), config: cfg };
      })),
    );
  }
  const bdev = /^\/api\/browsers\/([0-9a-f]+)(?:\/(site-requests))?$/.exec(path);
  if (bdev) {
    const key = `browserDevice:${bdev[1]}`;
    const d = await getJSON(env, key, null);
    if (!d || !canSee(session, d.ownerId)) return json({ error: "Unknown browser" }, 404);
    if (method === "DELETE" && !bdev[2]) {
      // The app keeps no local fallback of its own, so this is the only way to disconnect it
      // short of uninstalling: without a token it goes right back to "nothing is allowed" and
      // needs a fresh code.
      invalidateListCache(key);
      await env.STATE.delete(key);
      return json({ ok: true });
    }
    if (method === "DELETE" && bdev[2] === "site-requests") {
      const requestUrl = url.searchParams.get("url");
      d.siteRequests = (d.siteRequests || []).filter((r) => r.url !== requestUrl);
      await putJSON(env, key, d);
      return json({ ok: true });
    }
    if (method === "PUT" && !bdev[2]) {
      const name = String(body.name || "").trim().slice(0, 60);
      if (!name) return json({ error: "Name can't be empty" }, 400);
      d.name = name;
      await putJSON(env, key, d);
      return json({ ok: true });
    }
  }
  const shotM = /^\/api\/devices\/([0-9a-f]+)\/shots\/([0-9a-f]{12})$/.exec(path);
  if (shotM && method === "GET") {
    const sd = await getJSON(env, `device:${shotM[1]}`, null);
    if (!sd || !canSee(session, sd.ownerId)) return json({ error: "Unknown device" }, 404);
    const b64 = await env.STATE.get(`shot:${shotM[1]}:${shotM[2]}`);
    if (!b64) return json({ error: "That screenshot has expired" }, 404);
    return new Response(Uint8Array.from(atob(b64), (c) => c.charCodeAt(0)), { headers: { "content-type": "image/jpeg", "cache-control": "private, max-age=86400" } });
  }
  const dev = /^\/api\/devices\/([0-9a-f]+)(?:\/(command|site-requests|app-requests|messages|msg-alerts))?$/.exec(path);
  if (dev) {
    const key = `device:${dev[1]}`;
    const d = await getJSON(env, key, null);
    if (!d || !canSee(session, d.ownerId)) return json({ error: "Unknown device" }, 404);
    if (method === "DELETE" && !dev[2]) {
      invalidateListCache(key);
      await env.STATE.delete(key);
      return json({ ok: true });
    }
    if (method === "DELETE" && dev[2] === "site-requests") {
      const requestUrl = url.searchParams.get("url");
      d.siteRequests = (d.siteRequests || []).filter((r) => r.url !== requestUrl);
      await putJSON(env, key, d);
      return json({ ok: true });
    }
    if (method === "DELETE" && dev[2] === "app-requests") {
      const reqKey = url.searchParams.get("key");
      d.appRequests = (d.appRequests || []).filter((r) => r.key !== reqKey);
      await putJSON(env, key, d);
      return json({ ok: true });
    }
    if (method === "DELETE" && dev[2] === "msg-alerts") {
      d.msgAlerts = [];
      await putJSON(env, key, d);
      return json({ ok: true });
    }
    if (method === "DELETE" && dev[2] === "messages") {
      const msgId = url.searchParams.get("id");
      d.messages = (d.messages || []).filter((m) => m.id !== msgId);
      await putJSON(env, key, d);
      return json({ ok: true });
    }
    if (method === "POST" && dev[2]) {
      if (!COMMANDS.has(body.type)) return json({ error: "Unknown command" }, 400);
      const args = {};
      const given = body.args || {};
      if (body.type === "install") {
        if (given.apkId !== undefined) {
          if (!/^[0-9a-f]{12}$/.test(given.apkId) || !(await env.STATE.get(`apk:${given.apkId}`, "arrayBuffer"))) {
            return json({ error: "That uploaded APK has expired. Upload it again." }, 400);
          }
          args.apkId = given.apkId;
        } else {
          if (typeof given.url !== "string" || !given.url.startsWith("https://")) return json({ error: "APK link must start with https://" }, 400);
          args.url = given.url;
        }
      }
      if (body.type === "uninstall") {
        if (!PKG_RE.test(given.packageName || "")) return json({ error: "Invalid package name" }, 400);
        args.packageName = given.packageName;
      }
      if (body.type === "release") args.uninstall = given.uninstall === true;
      // Extra screen time: lifts bedtime and the limits for this many minutes from when the phone gets it.
      if (body.type === "addTime") args.minutes = Number.isInteger(given.minutes) ? Math.min(Math.max(given.minutes, 5), 240) : 15;
      // Opens the Play Store on the phone for a while (0 = until it is closed again from here).
      if (body.type === "openStore") args.minutes = Number.isInteger(given.minutes) ? Math.min(Math.max(given.minutes, 0), MAX_STORE_MINUTES) : 60;
      if (body.type === "lock") {
        const minutes = Number.isInteger(given.minutes) ? Math.min(Math.max(given.minutes, 0), MAX_LOCK_MINUTES) : 0;
        args.minutes = minutes;
        args.message = String(given.message || "").slice(0, 140);
      }
      if (body.type === "addWifi") {
        const ssid = String(given.ssid || "");
        const pass = String(given.password || "");
        if (ssid.length < 1 || ssid.length > 32) return json({ error: "Network name must be 1 to 32 characters" }, 400);
        if (pass && (pass.length < 8 || pass.length > 63)) return json({ error: "Wi-Fi password must be 8 to 63 characters (or empty for an open network)" }, 400);
        args.ssid = ssid;
        args.password = pass;
        // The dashboard remembers networks you add, so you can look the password up later.
        d.wifiNetworks = [...(d.wifiNetworks || []).filter((n) => n.ssid !== ssid), { ssid, password: pass, at: Date.now() }].slice(-20);
      }
      if (body.type === "setPin") {
        if (!/^\d{4,16}$/.test(given.pin || "")) return json({ error: "PIN must be 4 to 16 digits" }, 400);
        args.pin = given.pin; // delivered once, then dropped from the queue; never written to the activity log
        // Kept (admin-only) so the dashboard can show a PIN the administrator set. Confirmed when the phone reports success.
        d.adminPin = { pin: given.pin, ok: false, at: Date.now() };
      }
      if (body.type === "clearOverrides") {
        // Handled here: the next sync hands the phone the cleared list.
        d.overrides = {};
        d.overridesRev = Date.now();
        await putJSON(env, key, d);
        return json({ ok: true });
      }
      d.queue = [...(d.queue || []), { id: randomHex(6), type: body.type, args }];
      await putJSON(env, key, d);
      return json({ ok: true });
    }
  }
  // LockGuard (Windows) devices self-register with no enrollment code at all -- there's no client-
  // scoped flow for them, so for now they stay admin-only, same as before client accounts existed.
  if (path === "/api/windevices" && method === "GET") {
    if (!isAdmin(session)) return json([]);
    const wins = await listWinDevices(env);
    return json(wins.map(publicWinDevice));
  }
  if (path === "/api/youtube/search" && method === "GET") {
    // Find a YouTube channel or video to allow (any signed-in parent).
    const results = await youtubeSearch(url.searchParams.get("q"), url.searchParams.get("type")).catch(() => []);
    return json({ results });
  }
  const wshot = /^\/api\/windevices\/([0-9a-f]+)\/shots\/([0-9a-f]{12})$/.exec(path);
  if (wshot && method === "GET") {
    if (!isAdmin(session)) return json({ error: "Unknown device" }, 404);
    const b64 = await env.STATE.get(`winshot:${wshot[1]}:${wshot[2]}`);
    if (!b64) return json({ error: "That screenshot has expired" }, 404);
    return new Response(Uint8Array.from(atob(b64), (c) => c.charCodeAt(0)), { headers: { "content-type": "image/jpeg", "cache-control": "private, max-age=86400" } });
  }
  const wdev = /^\/api\/windevices\/([0-9a-f]+)(?:\/(config|command|removal-code|site-requests))?$/.exec(path);
  if (wdev) {
    if (!isAdmin(session)) return json({ error: "Unknown device" }, 404);
    const key = `winDevice:${wdev[1]}`;
    const d = await getJSON(env, key, null);
    if (!d) return json({ error: "Unknown device" }, 404);
    if (method === "DELETE" && !wdev[2]) {
      // Same as disconnecting a Browser: without a token it goes right back to doing nothing
      // locally until re-registered. If the device is still alive, use the "uninstall" command
      // below first so it actually removes itself, rather than just losing contact with it.
      invalidateListCache(key);
      await env.STATE.delete(key);
      return json({ ok: true });
    }
    if (method === "PUT" && !wdev[2]) {
      const name = String(body.name || "").trim().slice(0, 60);
      if (!name) return json({ error: "Name can't be empty" }, 400);
      d.name = name;
      await putJSON(env, key, d);
      return json({ ok: true });
    }
    if (method === "PUT" && wdev[2] === "config") {
      // Each part is optional, so the dashboard can save just the filter or just the programs.
      if (typeof body.enabled === "boolean") d.enabled = body.enabled;
      if (Array.isArray(body.allowedPrograms)) d.allowedPrograms = body.allowedPrograms.map((p) => String(p).slice(0, 300)).filter(Boolean).slice(0, 200);
      if (typeof body.filterOn === "boolean") d.filterOn = body.filterOn;
      if (body.config && typeof body.config === "object") d.config = normalizeConfig(body.config);
      if (body.win && typeof body.win === "object") d.win = normalizeWin(body.win);
      await putJSON(env, key, d);
      await markAdminActive(env);
      return json({ ok: true });
    }
    if (method === "DELETE" && wdev[2] === "site-requests") {
      const u = url.searchParams.get("url");
      d.siteRequests = (d.siteRequests || []).filter((r) => r.url !== u);
      await putJSON(env, key, d);
      return json({ ok: true });
    }
    if (method === "POST" && wdev[2] === "removal-code") {
      // A code for removing LockGuard on the computer itself (Settings → Apps → LockGuard → Uninstall,
      // which opens LockGuard and asks for it). Works for 48 hours; only its fingerprint is kept.
      const code = String(crypto.getRandomValues(new Uint32Array(1))[0] % 100000000).padStart(8, "0");
      d.removal = { hash: await sha256(code), until: Date.now() + 48 * 3600 * 1000 };
      await putJSON(env, key, d);
      await auditAdd(env, { who: "Administrator", text: `Made a removal code for the computer "${d.name}"` });
      return json({ code, until: d.removal.until });
    }
    if (method === "POST" && wdev[2] === "command") {
      if (body.type !== "uninstall" && body.type !== "screenshot") return json({ error: "Unknown command" }, 400);
      d.queue = [...(d.queue || []), { id: randomHex(6), type: body.type }];
      await putJSON(env, key, d);
      return json({ ok: true });
    }
  }
  return json({ error: "Not found" }, 404);
}

/** {package: category} for the apps a phone reported, for the app groups that go by category. */
const categoriesOf = (d) => Object.fromEntries((d.packages || []).filter((p) => Number.isInteger(p.c)).map((p) => [p.p, p.c]));

// ---- agent API (per-device bearer token) ----
// Where the Browser downloads its category blocklists from (the fixed "blocklists" release the weekly job publishes).
const filterFor = (env, config) => ({ tier: config.openWeb ? "open" : config.browserFilter || "off", safeSearch: !config.safeSearchOff, adultBlock: !config.adultListOff, allowSearch: !config.searchOff, look: config.browserLook || "classic", noPictureSites: config.noPictureSites || [], skinWho: config.skinWho || "all", blockAds: !!config.blockAds, youtubeOffMessage: config.youtubeOffMessage || "", adsCoveredSites: config.adsCoveredSites || [], adsAutoCover: !config.adsAutoCoverOff, youtubeSearch: !config.youtubeSearchOff, youtubeSearchPictures: config.youtubeSearchPictures || "hide", youtube: effectiveYoutubeMode(config), blockImages: !!config.blockImages, pictureGuard: config.pictureGuard || "off", imageSearch: !!config.blockImageSearch, words: config.blockedWords || [], base: `https://github.com/${env.GITHUB_REPO || DEFAULT_REPO}/releases/download/blocklists` });

async function agentApi(request, env, url, ctx) {
  const apk = /^\/agent\/apk\/([0-9a-f]{12})$/.exec(url.pathname);
  if (apk && request.method === "GET") {
    if (!(await authDevice(request, env))) return json({ error: "Unauthorized" }, 401);
    const buf = await env.STATE.get(`apk:${apk[1]}`, "arrayBuffer");
    if (!buf) return json({ error: "Not found" }, 404);
    return new Response(buf, { headers: { "content-type": "application/vnd.android.package-archive" } });
  }
  const agentIcon = /^\/agent\/icon\/([A-Za-z0-9_.]{1,200})$/.exec(url.pathname);
  if (agentIcon && request.method === "GET") {
    if (!(await authDevice(request, env))) return json({ error: "Unauthorized" }, 401);
    const b64 = await env.STATE.get(`iconc:${agentIcon[1]}`);
    return b64 ? imageResponse(b64) : new Response(null, { status: 404 });
  }
  if (url.pathname === "/agent/logo" && request.method === "GET") {
    if (!(await authDevice(request, env))) return json({ error: "Unauthorized" }, 401);
    const b64 = await env.STATE.get("logo");
    return b64 ? imageResponse(b64) : new Response(null, { status: 404 });
  }
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const body = await request.json().catch(() => ({}));

  if (url.pathname === "/agent/enroll") {
    const code = String(body.code || "").toUpperCase();
    const codeRec = /^[0-9A-F]{8}$/.test(code) ? await getJSON(env, `code:enroll:${code}`, null) : null;
    if (!codeRec) return json({ error: "Invalid or expired enrollment code" }, 403);
    await env.STATE.delete(`code:enroll:${code}`);
    const id = randomHex(8);
    const secret = randomHex(24);
    const info = body.info || {};
    await putJSON(env, `device:${id}`, {
      id,
      name: [info.manufacturer, info.model].filter(Boolean).join(" ") || id,
      tokenHash: await sha256(secret),
      lastSeen: Date.now(),
      info,
      packages: [],
      queue: [],
      inflight: [],
      results: [],
      ownerId: codeRec.ownerId || null,
    });
    return json({ token: `${id}.${secret}`, pollSeconds: POLL_SECONDS });
  }

  if (
    url.pathname === "/agent/sync" ||
    url.pathname === "/agent/redeem" ||
    url.pathname === "/agent/update" ||
    url.pathname === "/agent/play-token" ||
    url.pathname === "/agent/screenshot"
  ) {
    const auth = await authDevice(request, env);
    if (!auth) return json({ error: "Unauthorized" }, 401);
    const { key, d } = auth;

    if (url.pathname === "/agent/update") return json({ latest: await latestAgent(env) });
    if (url.pathname === "/agent/screenshot") {
      // One picture of the phone's screen, asked for from the dashboard. Kept for 7 days, last 10 per phone.
      const b = body;
      const jpeg = typeof b.jpeg === "string" ? b.jpeg : "";
      if (jpeg.length < 100 || jpeg.length > 3_000_000 || !B64_RE.test(jpeg)) return json({ error: "No picture" }, 400);
      const id = randomHex(6);
      await env.STATE.put(`shot:${d.id}:${id}`, jpeg, { expirationTtl: 7 * 86400 });
      const keep = [...(d.shots || []), { id, at: Date.now(), app: String(b.app || "").slice(0, 100) }];
      for (const old of keep.slice(0, -10)) await env.STATE.delete(`shot:${d.id}:${old.id}`);
      d.shots = keep.slice(-10).filter((s) => Date.now() - s.at < 7 * 86400 * 1000);
      await putJSON(env, key, d);
      return json({ ok: true });
    }
    if (url.pathname === "/agent/play-token") {
      const result = await playToken(env);
      return json(result, result.error ? 503 : 200);
    }

    if (url.pathname === "/agent/redeem") {
      // One-time codes unlock features inside the phone app (install an APK, remove the agent,
      // browse freely for a while).
      const type = body.type;
      const code = String(body.code || "").toUpperCase();
      const stored = ["install", "uninstall", "freebrowse", "playstore"].includes(type) && /^[0-9A-F]{8}$/.test(code)
        ? await getJSON(env, `code:${type}:${code}`, null)
        : null;
      if (!stored || (stored.deviceId && stored.deviceId !== d.id)) return json({ error: "Invalid or expired code" }, 403);
      await env.STATE.delete(`code:${type}:${code}`);
      d.results = [...(d.results || []), { id: randomHex(6), type: `code:${type}`, ok: true, msg: "one-time code used on the phone", at: Date.now() }].slice(-30);
      await putJSON(env, key, d);
      return json(type === "freebrowse" || type === "playstore" ? { ok: true, minutes: stored.minutes || 60 } : { ok: true });
    }

    let dirty = false;
    const toAlert = []; // things worth telling the administrator about, sent once the record is saved
    const now = Date.now();
    if (now - (d.lastSeen || 0) > LAST_SEEN_WRITE_MS) dirty = true;
    d.lastSeen = now;

    if (Array.isArray(body.packages)) {
      const pk = body.packages
        .filter((p) => p && typeof p.p === "string")
        .map((p) => ({ p: p.p, l: String(p.l || p.p).slice(0, 80), s: !!p.s, h: !!p.h, ...(Number.isInteger(p.c) && p.c >= 0 && p.c <= 20 && { c: p.c }) }))
        .sort((a, b) => a.p.localeCompare(b.p));
      if (JSON.stringify(pk) !== JSON.stringify(d.packages)) {
        // A new app on a phone that already reported its apps: tell the parent.
        if (Array.isArray(d.packages) && d.packages.length) {
          const had = new Set(d.packages.map((p) => p.p));
          for (const p of pk.filter((x) => !had.has(x.p) && !x.s).slice(0, 5)) toAlert.push({ kind: "apps", text: `New app installed: ${p.l}` });
        }
        d.packages = pk;
        dirty = true;
      }
    }
    if (body.info && typeof body.info === "object") {
      // Battery % and signal strength change constantly; they ride along with the next real write
      // instead of using up Workers KV's free write allowance.
      const stable = (i) => JSON.stringify({ ...i, battery: undefined, wifi: i.wifi ? { ssid: i.wifi.ssid, transport: i.wifi.transport } : undefined });
      if (stable(body.info) !== stable(d.info || {})) dirty = true;
      d.info = body.info;
    }
    if (Array.isArray(body.results) && body.results.length) {
      const done = new Set(body.results.map((r) => r.id));
      d.inflight = (d.inflight || []).filter((c) => !done.has(c.id));
      for (const r of body.results) {
        if (r.type === "setPin") d.adminPin = r.ok && d.adminPin ? { ...d.adminPin, ok: true } : null;
        if (r.type === "clearPin" && r.ok) d.adminPin = null;
      }
      d.results = [...(d.results || []), ...body.results.slice(0, 20).map((r) => ({ ...r, at: now }))].slice(-30);
      dirty = true;
    }
    let { cfg: config, seeded } = await configOf(env, d);
    if (seeded) dirty = true;
    // Settings changed on the phone's own Administrator screen (same settings as the dashboard).
    // Each change has a number; a change is taken once, and only while the dashboard allows the
    // phone to be managed from itself ("Only control from the dashboard" off).
    const cp = body.configPatch;
    if (cp && typeof cp === "object" && cp.values && typeof cp.values === "object" && Number(cp.rev) > (d.configPatchRev || 0)) {
      d.configPatchRev = Number(cp.rev);
      dirty = true;
      if (!config.phoneAdminLocked) {
        const patch = {};
        for (const k of PHONE_EDITABLE) if (Object.prototype.hasOwnProperty.call(cp.values, k)) patch[k] = cp.values[k];
    // SafeSearch is on unless switched off: the phone's switch maps onto that.
    if (Object.prototype.hasOwnProperty.call(patch, "safeSearch")) patch.safeSearchOff = patch.safeSearch !== true;
        const next = normalizeConfig({ ...config, ...patch });
        if (next.approveNew && !config.approveNew) d.known = (d.packages || []).map((p) => p.p);
        const changes = summarizeConfigChange(config, next);
        d.config = next;
        config = next;
        if (changes.length) {
          d.pushLog = [...(d.pushLog || []), { at: now, changes: changes.map((c) => "On the phone: " + c) }].slice(-30);
          await auditAdd(env, { who: `Phone "${d.name}"`, text: "Changed on the phone: " + changes.join("; ") });
        }
      }
    }
    const reported = (d.packages || []).map((p) => p.p);
    if (!Array.isArray(d.known)) {
      if (reported.length) {
        d.known = reported; // first report: whatever is already installed is the approved baseline
        dirty = true;
      }
    } else if (!config.approveNew && reported.some((p) => !d.known.includes(p))) {
      d.known = [...new Set([...d.known, ...reported])];
      dirty = true;
    }
    if (body.overrides && typeof body.overrides === "object" && Object.keys(body.overrides).length && Number(body.overridesRev) > 0 && Number(body.overridesRev) > (d.overridesPhoneRev || 0)) {
      d.overridesPhoneRev = Number(body.overridesRev); // compared with this phone's own last change, never with our clock (a phone's clock may be behind)
      // A change made on the phone itself becomes the real setting here, instead of a second,
      // parallel state that would otherwise keep silently winning over the dashboard forever --
      // the two are meant to mirror each other, not permanently disagree until someone remembers
      // "Clear phone-side changes" in Controls. Folding it into config now means a later dashboard
      // change isn't fighting a stale phone-side override it has no way to see.
      const changes = normalizeOverrides(body.overrides);
      for (const [pkg, mode] of Object.entries(changes)) {
        config.apps[pkg] = { mode, label: config.apps[pkg]?.label };
      }
      d.config = config;
      // A strictly newer revision than what the phone just sent is what makes its own
      // adoptOverrides() actually replace its local copy with this empty one below, instead of
      // ignoring it as an "older" echo of what it already has.
      d.overrides = {};
      d.overridesRev = Date.now();
      dirty = true;
    }
    if (body.restrictionOverrides && typeof body.restrictionOverrides === "object" && Object.keys(body.restrictionOverrides).length && Number(body.restrictionOverridesRev) > 0 && Number(body.restrictionOverridesRev) > (d.restrictionOverridesPhoneRev || 0)) {
      d.restrictionOverridesPhoneRev = Number(body.restrictionOverridesRev); // not compared with our clock
      // Same mirroring as app overrides above: a restriction flipped on the phone's own
      // Administrator screen becomes the real dashboard setting, not a second state that would
      // otherwise silently disagree with the dashboard forever.
      const restrictions = { ...config.restrictions };
      for (const [key, on] of Object.entries(body.restrictionOverrides)) {
        const cfgKey = RESTRICTION_BY_KEY[key];
        if (cfgKey) restrictions[cfgKey] = !!on;
      }
      config.restrictions = restrictions;
      d.config = config;
      // Strictly newer than what the phone just sent, so its own adoptRestrictionOverrides()
      // replaces its local copy with this empty one instead of ignoring it as an older echo.
      d.restrictionOverrides = {};
      d.restrictionOverridesRev = Date.now();
      dirty = true;
    }
    if (Number(body.homeScreenRev) > (d.homeScreenRev || 0)) {
      // Turned on (or off) from the phone's own Admin screen, same mirroring as app overrides
      // above: it becomes the real dashboard setting, not a value that gets silently stomped by
      // whatever this device's next ordinary sync would otherwise have sent back down.
      config.homeScreen = !!body.homeScreenValue;
      d.config = config;
      d.homeScreenRev = Number(body.homeScreenRev);
      dirty = true;
    }
    if (Number(body.hideAppIconRev) > (d.hideAppIconRev || 0)) {
      // Same mirroring again, for the app icon's own hide/show toggle.
      config.hideAppIcon = !!body.hideAppIconValue;
      d.config = config;
      d.hideAppIconRev = Number(body.hideAppIconRev);
      dirty = true;
    }
    if (Number(body.dnsFilterNotifTitleRev) > (d.dnsFilterNotifTitleRev || 0)) {
      // Same mirroring again, for the DNS filter notification's title.
      config.dnsFilterNotifTitle = String(body.dnsFilterNotifTitleValue || "").slice(0, 100);
      d.config = config;
      d.dnsFilterNotifTitleRev = Number(body.dnsFilterNotifTitleRev);
      dirty = true;
    }
    if (Number(body.dnsFilterNotifTextRev) > (d.dnsFilterNotifTextRev || 0)) {
      // Same mirroring again, for the DNS filter notification's own text.
      config.dnsFilterNotifText = String(body.dnsFilterNotifTextValue || "").slice(0, 100);
      d.config = config;
      d.dnsFilterNotifTextRev = Number(body.dnsFilterNotifTextRev);
      dirty = true;
    }
    if (Number(body.hideKioskAdminRev) > (d.hideKioskAdminRev || 0)) {
      // Same mirroring again, for home screen mode's own Administrator-button hide/show toggle.
      config.hideKioskAdmin = !!body.hideKioskAdminValue;
      d.config = config;
      d.hideKioskAdminRev = Number(body.hideKioskAdminRev);
      dirty = true;
    }
    if (Number(body.browserAddonRev) > (d.browserAddonRev || 0)) {
      // Same mirroring again, for the Browser add-on's own on/off toggle.
      config.browserAddonEnabled = !!body.browserAddonValue;
      d.config = config;
      d.browserAddonRev = Number(body.browserAddonRev);
      dirty = true;
    }
    if (Number(body.wholeAppBlocklistRev) > (d.wholeAppBlocklistRev || 0)) {
      // Same mirroring again, for the whole-app blocklist (Blocking > Block Whole Apps).
      config.wholeAppBlocklist = normalizeWholeAppBlocklist(body.wholeAppBlocklistValue);
      d.config = config;
      d.wholeAppBlocklistRev = Number(body.wholeAppBlocklistRev);
      dirty = true;
    }
    if (Number(body.dnsBlocklistRev) > (d.dnsBlocklistRev || 0)) {
      // Same mirroring again, for the DNS Hostname Block list -- edited either on the dashboard
      // or directly on the phone's own Administrator panel.
      config.dnsBlocklist = normalizeDnsBlocklist(body.dnsBlocklistValue);
      d.config = config;
      d.dnsBlocklistRev = Number(body.dnsBlocklistRev);
      dirty = true;
    }
    if (Number(body.dnsAllowlistRev) > (d.dnsAllowlistRev || 0)) {
      // Same mirroring again, for the DNS Hostname Allow list (overrides DNS Hostname Block).
      config.dnsAllowlist = normalizeDnsAllowlist(body.dnsAllowlistValue);
      d.config = config;
      d.dnsAllowlistRev = Number(body.dnsAllowlistRev);
      dirty = true;
    }
    if (Number(body.networkBlocklistRev) > (d.networkBlocklistRev || 0)) {
      // Same mirroring again, for Block Whole Network (cuts an app's network off entirely,
      // unlike wholeAppBlocklist which stops it running at all). No dashboard editor for this
      // yet -- phone-only -- but the sync plumbing is here now so adding one later is pure UI.
      config.networkBlocklist = normalizeWholeAppBlocklist(body.networkBlocklistValue);
      d.config = config;
      d.networkBlocklistRev = Number(body.networkBlocklistRev);
      dirty = true;
    }
    if (Number(body.perAppDnsAllowlistRev) > (d.perAppDnsAllowlistRev || 0)) {
      // The only exception to networkBlocklist, per package. Same no-dashboard-editor-yet note
      // as above.
      config.perAppDnsAllowlist = normalizePerAppDnsAllowlist(body.perAppDnsAllowlistValue);
      d.config = config;
      d.perAppDnsAllowlistRev = Number(body.perAppDnsAllowlistRev);
      dirty = true;
    }
    if (body.masterValue && Number(body.masterRev) > (d.masterRev || 0)
        && /^[0-9a-f]{32}$/.test(body.masterValue.salt || "") && /^[0-9a-f]{64}$/.test(body.masterValue.hash || "")) {
      // Changed on the phone itself (Administrator -> Device): becomes the real master code here
      // too, same mirroring as everything else above -- not a value the dashboard's own Settings
      // card would otherwise silently overwrite back to whatever it last had.
      d.master = { salt: body.masterValue.salt, hash: body.masterValue.hash, iterations: Number(body.masterValue.iterations) || MASTER_ITERATIONS };
      d.masterRev = Number(body.masterRev);
      dirty = true;
    }
    if (typeof body.appCode === "string") {
      // The code the person chose to open the phone app; the administrator can see it on the dashboard.
      const code = body.appCode.slice(0, 32) || null;
      if (code !== (d.appCode || null)) {
        d.appCode = code;
        dirty = true;
      }
    }
    if (/^[0-9]{6}$/.test(body.fallbackCode || "") && body.fallbackCode !== d.fallbackCode) {
      // This phone's own always-on recovery code, generated once on the phone itself.
      d.fallbackCode = body.fallbackCode;
      dirty = true;
    }
    if (typeof body.syncPaused === "boolean" && body.syncPaused !== !!d.syncPaused) {
      // The phone itself decided to stop checking in (to save battery) or start again -- this is
      // its one chance to tell the dashboard that before it goes quiet.
      d.syncPaused = body.syncPaused;
      dirty = true;
    }
    if (Array.isArray(body.events) && body.events.length) {
      for (const e of body.events.slice(0, 40)) {
        if (e && (e.k === "security" || e.k === "watchdog")) toAlert.push({ kind: "tamper", text: String(e.m || "").slice(0, 200) });
        if (e && e.k === "sos") toAlert.push({ kind: "sos", text: "SOS: " + String(e.m || "").slice(0, 200) });
      if (e && e.k === "checkin") toAlert.push({ kind: "places", text: String(e.m || "").slice(0, 200) });
      }
      d.events = [...(d.events || []), ...body.events.slice(0, 40).map((e) => ({ k: String(e.k || "info").slice(0, 20), m: String(e.m || "").slice(0, 200), at: Number(e.at) || now }))].slice(-MAX_EVENTS);
      dirty = true;
    }
    // The phone's call log and texts log (each switched on per phone).
    const logRow = (x, types) => ({
      number: String((x && x.number) || "").slice(0, 40),
      ...(x && x.name ? { name: String(x.name).slice(0, 80) } : {}),
      type: types.includes(x && x.type) ? x.type : "other",
      at: Number(x && x.at) || now,
      ...(x && Number.isFinite(Number(x.secs)) ? { secs: Math.max(0, Math.round(Number(x.secs))) } : {}),
      ...(x && typeof x.text === "string" ? { text: x.text.slice(0, 310) } : {}),
    });
    if (Array.isArray(body.contacts)) {
      d.contacts = body.contacts.slice(0, 1000).filter((x) => x && typeof x === "object")
        .map((x) => ({ name: String(x.name || "").slice(0, 80), number: String(x.number || "").slice(0, 40) }));
      d.contactsAt = now;
      dirty = true;
    }
    if (Array.isArray(body.calls) && body.calls.length) {
      d.calls = [...(d.calls || []), ...body.calls.slice(0, 150).map((x) => logRow(x, ["in", "out", "missed", "voicemail", "rejected", "blocked"]))].slice(-MAX_CALLS);
      dirty = true;
    }
    if (Array.isArray(body.texts) && body.texts.length) {
      d.texts = [...(d.texts || []), ...body.texts.slice(0, 150).map((x) => { const r = logRow(x, ["in", "out"]); delete r.secs; return r; })].slice(-MAX_TEXTS);
      dirty = true;
    }
    // Searches typed on the phone (with "Keep a list of searches" on).
    if (config.recordSearches && Array.isArray(body.searches) && body.searches.length) {
      const add = body.searches.slice(0, 50).filter((x) => x && typeof x.q === "string" && x.q.trim())
        .map((x) => ({ app: String(x.app || "").slice(0, 100), q: x.q.trim().slice(0, 200), at: Number(x.at) || now }));
      d.searches = [...(d.searches || []), ...add].slice(-300);
      dirty = true;
    }
    // Photo alerts: a new photo on the phone may show nudity (checked on the phone; the photo never comes here).
    if (config.nudityAlerts && Array.isArray(body.nudity) && body.nudity.length) {
      const add = body.nudity.slice(0, 50).filter((x) => x && typeof x === "object")
        .map((x) => ({ folder: String(x.folder || "").slice(0, 80), score: Math.max(0, Math.min(100, Math.round(Number(x.score) || 0))), at: Number(x.at) || now }));
      if (add.length) {
        d.nudityAlerts = [...(d.nudityAlerts || []), ...add].slice(-100);
        toAlert.push({ kind: "messages", text: (add.length === 1 ? "A new photo" : add.length + " new photos") + " on the phone may show nudity" + (add[0].folder ? " (" + add[0].folder + ")" : "") + ". The photo stays on the phone." });
        dirty = true;
      }
    }
    // YouTube videos watched (with "Keep a list of YouTube videos" on).
    if (config.recordVideos && Array.isArray(body.videos) && body.videos.length) {
      const add = body.videos.slice(0, 50).filter((x) => x && typeof x.title === "string" && x.title.trim())
        .map((x) => ({ title: x.title.trim().slice(0, 200), at: Number(x.at) || now }));
      d.videos = [...(d.videos || []), ...add].slice(-300);
      dirty = true;
    }
    // Message alerts: worrying words found on the phone (in a chat on the screen), and in texts sent here.
    const newMsgAlerts = [];
    if (config.messageAlerts && Array.isArray(body.msgAlerts)) {
      for (const x of body.msgAlerts.slice(0, 40)) {
        if (!x || typeof x !== "object") continue;
        newMsgAlerts.push({ app: String(x.app || "").slice(0, 100), word: String(x.word || "").slice(0, 60), text: String(x.text || "").slice(0, 320), at: Number(x.at) || now });
      }
    }
    if (config.messageAlerts && Array.isArray(body.texts)) {
      const words = messageWordList(config);
      for (const x of body.texts.slice(0, 150)) {
        const word = x && typeof x.text === "string" ? matchMessageWord(x.text, words) : null;
        if (word) newMsgAlerts.push({ app: "texts", word, text: x.text.slice(0, 320), who: String(x.name || x.number || "").slice(0, 80), at: Number(x.at) || now });
      }
    }
    if (newMsgAlerts.length) {
      d.msgAlerts = [...(d.msgAlerts || []), ...newMsgAlerts].slice(-100);
      for (const m of newMsgAlerts) toAlert.push({ kind: "messages", text: `Worrying words ("${m.word}") in ${appName(m.app)}: ${m.text.slice(0, 200)}` });
      dirty = true;
    }
    if (Array.isArray(body.locations) && body.locations.length) {
      // Location history (switched on per phone), and "arrived at / left" a saved place.
      const fixes = body.locations.slice(-30)
        .map((f) => ({ lat: Number(f && f.lat), lon: Number(f && f.lon), acc: Number(f && f.accuracy) || 0, at: Number(f && f.at) || now, ...(f && f.why === "sos" ? { why: "sos" } : {}) }))
        .filter((f) => Number.isFinite(f.lat) && Number.isFinite(f.lon) && Math.abs(f.lat) <= 90 && Math.abs(f.lon) <= 180);
      if (fixes.length) {
        d.locations = [...(d.locations || []), ...fixes].slice(-MAX_LOCATIONS);
        const latest = fixes[fixes.length - 1];
        if (latest.acc <= 500) { // a rough fix would make up arrivals and departures
          const inside = placesAt((d.config || {}).places, latest).map((p) => p.name);
          const before = Array.isArray(d.inPlaces) ? d.inPlaces : inside; // the first fix only sets the starting point
          const moves = [...inside.filter((n) => !before.includes(n)).map((n) => "Arrived at " + n), ...before.filter((n) => !inside.includes(n)).map((n) => "Left " + n)];
          for (const m of moves) toAlert.push({ kind: "places", text: m });
          if (moves.length) d.events = [...(d.events || []), ...moves.map((m) => ({ k: "place", m, at: latest.at }))].slice(-MAX_EVENTS);
          d.inPlaces = inside;
        }
        dirty = true;
      }
    }
    if (Array.isArray(body.trafficLog) && body.trafficLog.length) {
      // Every DNS query and TLS SNI hostname DnsFilterVpnService saw, mirrored up from the
      // phone's own Traffic Log (Blocking > Traffic Log) so it's visible from the dashboard too,
      // not just on the phone itself.
      d.trafficLog = [
        ...(d.trafficLog || []),
        ...body.trafficLog.slice(0, 60).map((e) => ({
          time: Number(e.time) || now,
          kind: String(e.kind || "").slice(0, 10),
          host: String(e.host || "").slice(0, 255),
          uid: Number.isFinite(Number(e.uid)) ? Number(e.uid) : -1,
          app: String(e.app || "").slice(0, 200),
          blocked: !!e.blocked,
        })),
      ].slice(-MAX_TRAFFIC_LOG);
      d.siteHistory = addSiteHistory(d.siteHistory, body.trafficLog.slice(0, 60), now);
      dirty = true;
    }
    if (Array.isArray(body.siteRequests) && body.siteRequests.length) {
      // Pages the person tried to open that weren't on the allowlist (with the child's own message,
      // if any); shown on the dashboard to approve or deny.
      const existing = d.siteRequests || [];
      const urls = new Set(existing.map((r) => r.url));
      const added = siteRequestsFrom(body.siteRequests, urls, now);
      if (added.length) {
        d.siteRequests = [...existing, ...added].slice(-30);
        for (const r of added) toAlert.push({ kind: "requests", text: `Asked to open ${r.url}${r.note ? " — \"" + r.note + "\"" : ""}` });
        dirty = true;
      }
    }
    if (body.usage && typeof body.usage === "object" && /^\d{4}-\d{2}-\d{2}$/.test(body.usage.day || "")) {
      // Today's screen time so far. It changes at every check-in, so it does not force a write by
      // itself (that would use up the free storage allowance): it rides along with the next real
      // write, which happens at least every ten minutes. A new day is always written.
      const u = body.usage;
      const entry = {
        total: Number.isInteger(u.total) ? Math.min(Math.max(u.total, 0), 1440) : 0,
        ok: u.ok !== false,
        apps: (Array.isArray(u.apps) ? u.apps : []).slice(0, 15)
          .filter((a) => a && PKG_RE.test(a.p || ""))
          .map((a) => ({ p: a.p, l: String(a.l || a.p).slice(0, 60), m: Number.isInteger(a.m) ? Math.min(Math.max(a.m, 0), 1440) : 0 })),
        ...(Number(u.extraUntil) > now && { extraUntil: Number(u.extraUntil) }),
      };
      const all = { ...(d.usage || {}) };
      if (!all[u.day]) dirty = true;
      all[u.day] = entry;
      d.usage = Object.fromEntries(Object.entries(all).sort((a, b) => (a[0] < b[0] ? 1 : -1)).slice(0, 14));
    }
    if (Array.isArray(body.appRequests) && body.appRequests.length) {
      // Asked for from the phone's own User screen: "open the Play Store", or one particular blocked app.
      const existing = d.appRequests || [];
      const keys = new Set(existing.map((r) => r.key));
      const added = [];
      for (const r of body.appRequests.slice(0, 20)) {
        const kind = r && (r.kind === "store" || r.kind === "time") ? r.kind : r && r.kind === "app" && PKG_RE.test(r.pkg || "") ? "app" : null;
        if (!kind) continue;
        const reqKey = kind === "app" ? "app:" + r.pkg : kind;
        if (keys.has(reqKey)) continue;
        keys.add(reqKey);
        added.push(kind !== "app" ? { key: reqKey, kind, at: now } : { key: reqKey, kind, pkg: r.pkg, label: String(r.label || r.pkg).slice(0, 80), at: now });
      }
      if (added.length) {
        d.appRequests = [...existing, ...added].slice(-30);
        for (const r of added) toAlert.push({ kind: "requests", text: r.kind === "store" ? "Asked to open the Play Store" : r.kind === "time" ? "Asked for more screen time" : `Asked for the app ${r.label}` });
        dirty = true;
      }
    }
    if (Array.isArray(body.messages) && body.messages.length) {
      // Free-text messages from the phone (bug reports, questions, anything) for the administrator to read.
      const added = body.messages
        .slice(0, 10)
        .map((m) => String((m && m.msg) || "").trim().slice(0, 1000))
        .filter(Boolean)
        .map((msg) => ({ id: randomHex(6), msg, at: now }));
      if (added.length) {
        d.messages = [...(d.messages || []), ...added].slice(-20);
        for (const m of added) toAlert.push({ kind: "requests", text: `Message: ${m.msg.slice(0, 300)}` });
        dirty = true;
      }
    }
    const commands = d.queue || [];
    if (commands.length) {
      d.queue = [];
      d.inflight = [...(d.inflight || []), ...commands.map((c) => ({ id: c.id, type: c.type, at: now }))].slice(-10);
      dirty = true;
    }
    if (body.icons && typeof body.icons === "object") {
      // App icons are shared by every device; store each package's icon once.
      for (const [pkg, b64] of Object.entries(body.icons).slice(0, MAX_ICONS_PER_SYNC)) {
        if (PKG_RE.test(pkg) && typeof b64 === "string" && b64.length <= MAX_ICON_B64 && B64_RE.test(b64) && !(await env.STATE.get(`icon:${pkg}`))) {
          await env.STATE.put(`icon:${pkg}`, b64);
        }
      }
    }
    if (dirty) await putJSON(env, key, d);
    if (toAlert.length) {
      const a = await getAlerts(env);
      const lines = toAlert.filter((x) => x.kind === "sos" || a[x.kind]).map((x) => x.text); // a call for help always goes out
      if (alertsOn(a) && lines.length) {
        const p = sendAlert(env, d.name, lines.join("\n"), a);
        if (ctx && typeof ctx.waitUntil === "function") ctx.waitUntil(p);
        else await p;
      }
    }

    // A filter schedule swaps the level during its hours, in the phone's own time.
    const level = effectiveFilter(config, now, d.info && d.info.tz);
    const effective = level === config.browserFilter ? config : { ...config, browserFilter: level };
    const policy = buildAgentPolicy(effective, reported, { known: d.known, overrides: d.overrides, categories: categoriesOf(d) });
    policy.browserFilterBase = filterFor(env, effective).base;
    if (config.homeScreen) policy.customIcons = (await env.STATE.get("iconIndex", "json")) || {};
    const master = d.master || null;
    const logoRev = await cachedLogoRev(env);
    return json({ policy, config, configPatchRev: d.configPatchRev || 0, commands, pollSeconds: await pollFor(env, now), overrides: d.overrides || {}, overridesRev: d.overridesRev || 0, restrictionOverrides: d.restrictionOverrides || {}, restrictionOverridesRev: d.restrictionOverridesRev || 0, master, logoRev });
  }
  return json({ error: "Not found" }, 404);
}

// ---- standalone Browser API (per-device bearer token, no agent/MDM involved) ----
/** Requests sent from a phone or a Browser: the address and the child's own message (if any). */
function siteRequestsFrom(list, seen, now) {
  return list
    .slice(0, 20)
    .map((r) => ({ url: String((r && r.url) || "").slice(0, 500), note: String((r && r.note) || "").trim().slice(0, 300) }))
    .filter((r) => r.url && !seen.has(r.url) && (seen.add(r.url), true))
    .map((r) => ({ url: r.url, at: now, ...(r.note ? { note: r.note } : {}) }));
}

async function browserApi(request, env, url, ctx) {
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const body = await request.json().catch(() => ({}));

  if (url.pathname === "/browser/enroll") {
    const code = String(body.code || "").toUpperCase();
    const codeRec = /^[0-9A-F]{8}$/.test(code) ? await getJSON(env, `code:browser:${code}`, null) : null;
    if (!codeRec) return json({ error: "Invalid or expired code" }, 403);
    await env.STATE.delete(`code:browser:${code}`);
    const { id, secret } = await createBrowserDevice(env, body.info, "New Browser", codeRec.ownerId);
    return json({ token: `${id}.${secret}`, pollSeconds: POLL_SECONDS });
  }

  if (url.pathname === "/browser/register") {
    // No code: Browser connects itself the moment someone taps "yes" to whitelist mode, with
    // nothing typed on either end. It shows up on the dashboard immediately as "New Browser"
    // (plus a short id so more than one is tellable apart) for the admin to rename and manage.
    // There is deliberately no gate here beyond knowing the dashboard's own address: a rogue
    // registration starts with its own, separate sites list (empty, or whatever the admin clones
    // into it), reaches nothing until something is added, and is always visible and removable
    // from the Devices list.
    // No longer allowed: connecting needs a code made on the dashboard, so only the parent can connect a Browser.
    return json({ error: "Ask for a code on the dashboard (Add device → Browser)" }, 403);
  }

  if (url.pathname === "/browser/sync") {
    const auth = await authBrowserDevice(request, env);
    if (!auth) return json({ error: "Unauthorized" }, 401);
    const { key, d } = auth;
    const now = Date.now();
    let dirty = false;
    if (now - (d.lastSeen || 0) > LAST_SEEN_WRITE_MS) dirty = true;
    d.lastSeen = now;
    if (body.info && typeof body.info === "object") {
      if (JSON.stringify(body.info) !== JSON.stringify(d.info || {})) dirty = true;
      d.info = body.info;
    }
    if (Array.isArray(body.siteRequests) && body.siteRequests.length) {
      const existing = d.siteRequests || [];
      const urls = new Set(existing.map((r) => r.url));
      const added = siteRequestsFrom(body.siteRequests, urls, now);
      if (added.length) {
        d.siteRequests = [...existing, ...added].slice(-30);
        dirty = true;
        const a = await getAlerts(env);
        if (alertsOn(a) && a.requests) {
          const p = sendAlert(env, d.name, added.map((r) => "Asked to open " + r.url + (r.note ? " — \"" + r.note + "\"" : "")).join("\n"), a);
          if (ctx && typeof ctx.waitUntil === "function") ctx.waitUntil(p);
          else await p;
        }
      }
    }
    if (Array.isArray(body.visited) && body.visited.length) {
      // Sites opened in the Browser, for its "Sites opened" list on the dashboard.
      const entries = body.visited.slice(0, 300).map((v) => ({ kind: "dns", host: String((v && v.host) || "").slice(0, 200), time: Number(v && v.at) || now, blocked: !!(v && v.blocked) }));
      d.siteHistory = addSiteHistory(d.siteHistory, entries, now);
      dirty = true;
    }
    const { cfg: config, seeded } = await configOf(env, d);
    if (seeded) dirty = true;
    // Ended early in the Browser's "Allow list": ended here too.
    if (Array.isArray(body.endedEarly) && body.endedEarly.length && endSitesEarly(config, body.endedEarly, now)) {
      d.config = config;
      dirty = true;
    }
    if (dirty) await putJSON(env, key, d);
    return json({ sites: normalizeSites(config.sites), restrictBrowsing: config.restrictBrowsing, filter: filterFor(env, config), pollSeconds: POLL_SECONDS });
  }
  return json({ error: "Not found" }, 404);
}

// ---- Windows LockGuard API (per-device bearer token, no agent/MDM involved) ----
async function windowsApi(request, env, url, ctx) {
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const body = await request.json().catch(() => ({}));

  if (url.pathname === "/windows/register") {
    // No code, same reasoning as /browser/register: it shows up immediately on the dashboard,
    // disabled and with nothing allowed, for the admin to configure and name.
    const { id, secret } = await createWinDevice(env, body.info);
    return json({ token: `${id}.${secret}`, pollSeconds: POLL_SECONDS });
  }

  if (url.pathname === "/windows/screenshot") {
    // One picture of the computer's screen, asked for from the dashboard. Kept 7 days, last 10.
    const auth = await authWinDevice(request, env);
    if (!auth) return json({ error: "Unauthorized" }, 401);
    const { key, d } = auth;
    const wid = key.slice("winDevice:".length);
    const jpeg = typeof body.jpeg === "string" ? body.jpeg : "";
    if (jpeg.length < 100 || jpeg.length > 3_000_000 || !B64_RE.test(jpeg)) return json({ error: "No picture" }, 400);
    const id = randomHex(6);
    await env.STATE.put(`winshot:${wid}:${id}`, jpeg, { expirationTtl: 7 * 86400 });
    const keep = [...(d.shots || []), { id, at: Date.now() }];
    for (const old of keep.slice(0, -10)) await env.STATE.delete(`winshot:${wid}:${old.id}`);
    d.shots = keep.slice(-10).filter((s) => Date.now() - s.at < 7 * 86400 * 1000);
    await putJSON(env, key, d);
    return json({ ok: true });
  }
  if (url.pathname === "/windows/sync") {
    const auth = await authWinDevice(request, env);
    if (!auth) return json({ error: "Unauthorized" }, 401);
    const { key, d } = auth;
    const now = Date.now();
    let dirty = false;
    if (now - (d.lastSeen || 0) > LAST_SEEN_WRITE_MS) dirty = true;
    d.lastSeen = now;
    if (body.info && typeof body.info === "object" && JSON.stringify(body.info) !== JSON.stringify(d.info || {})) {
      d.info = body.info;
      dirty = true;
    }
    if (typeof body.enabled === "boolean" && body.enabled !== d.reportedEnabled) {
      d.reportedEnabled = body.enabled;
      dirty = true;
    }
    const reportedPrograms = Array.isArray(body.allowedPrograms)
      ? body.allowedPrograms.map((p) => String(p).slice(0, 300)).slice(0, 200)
      : [];
    if (JSON.stringify(reportedPrograms) !== JSON.stringify(d.reportedAllowedPrograms || [])) {
      d.reportedAllowedPrograms = reportedPrograms;
      dirty = true;
    }
    if (Array.isArray(body.trafficLog) && body.trafficLog.length) {
      d.siteHistory = addSiteHistory(d.siteHistory, body.trafficLog.slice(0, 300), now);
      dirty = true;
    }
    if (Array.isArray(body.siteRequests) && body.siteRequests.length) {
      // Websites asked for in the LockGuard window on the computer.
      const have = new Set((d.siteRequests || []).map((r) => r.url));
      const add = [...new Set(body.siteRequests.slice(0, 20).map((r) => String((r && r.url) || "").trim().slice(0, 300)))].filter((u) => u && !have.has(u));
      if (add.length) {
        d.siteRequests = [...(d.siteRequests || []), ...add.map((url) => ({ url, at: now }))].slice(-30);
        dirty = true;
        const a = await getAlerts(env);
        if (alertsOn(a) && a.requests) {
          const p = sendAlert(env, d.name, add.map((u) => "Asked for the site " + u).join("\n"), a);
          if (ctx && typeof ctx.waitUntil === "function") ctx.waitUntil(p);
          else await p;
        }
      }
    }
    if (Array.isArray(body.programs)) {
      // The computer's installed programs (from its Start menu), for the dashboard's program list.
      d.programs = body.programs.slice(0, 500).filter((p) => p && typeof p.path === "string" && /\.exe$/i.test(p.path))
        .map((p) => ({ name: String(p.name || "").slice(0, 100), path: String(p.path).slice(0, 300) }));
      d.programsAt = now;
      dirty = true;
    }
    if (Array.isArray(body.events) && body.events.length) {
      const evs = body.events.slice(0, 40).map((e) => ({ k: String((e && e.k) || "info").slice(0, 20), m: String((e && e.m) || "").slice(0, 200), at: Number(e && e.at) || now }));
      d.events = [...(d.events || []), ...evs].slice(-MAX_EVENTS);
      dirty = true;
      const a = await getAlerts(env);
      if (alertsOn(a) && a.tamper) {
        const p = sendAlert(env, d.name, evs.map((e) => e.m).join("\n"), a);
        if (ctx && typeof ctx.waitUntil === "function") ctx.waitUntil(p);
        else await p;
      }
    }
    // Delivered at most once: the service applies a queued "uninstall" and tears itself down, so
    // there's no later sync to retry a lost command on anyway.
    const commands = d.queue || [];
    if (commands.length) {
      d.queue = [];
      dirty = true;
    }
    if (dirty) await putJSON(env, key, d);
    return json({
      enabled: !!d.enabled,
      allowedPrograms: d.allowedPrograms || [],
      commands,
      filter: winFilter(env, d, now, url.hostname),
      protect: normalizeWin(d.win).protectFromAdmins,
      screenTime: winScreenTime(normalizeWin(d.win)),
      removalCode: d.removal && d.removal.until > now ? d.removal : null,
      unlock: (() => { const w = normalizeWin(d.win); return w.unlockName && w.unlockText ? { name: w.unlockName, text: w.unlockText } : null; })(),
      // Only computers that can update themselves report a build number.
      ...(Number(body.info && body.info.build) > 0 ? await (async () => { const u = await latestLockguard(env); return u && u.build > Number(body.info.build) ? { update: u } : {}; })() : {}),
      pollSeconds: POLL_SECONDS,
    });
  }
  return json({ error: "Not found" }, 404);
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (!env.ADMIN_PASSWORD) {
      // Names only, never values: helps diagnose a missing binding.
      return html(`ADMIN_PASSWORD secret is not set. See SETUP.md.<br>Settings this app can see: ${Object.keys(env).join(", ") || "(none)"}`, 500);
    }

    try {
      if (url.pathname.startsWith("/agent/")) return await agentApi(request, env, url, ctx);
      if (url.pathname.startsWith("/browser/")) return await browserApi(request, env, url, ctx);
      if (url.pathname.startsWith("/windows/")) return await windowsApi(request, env, url, ctx);

      // Public, harmless files that let the dashboard be installed to a phone's home screen.
      if (url.pathname === "/manifest.webmanifest") {
        return new Response(
          JSON.stringify({
            name: "MDM Dashboard",
            short_name: "MDM",
            start_url: "/",
            scope: "/",
            display: "standalone",
            background_color: "#F6F7F9",
            theme_color: "#4F46E5",
            icons: [
              { src: "/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any maskable" },
              { src: "/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any maskable" },
            ],
          }),
          { headers: { "content-type": "application/manifest+json", "cache-control": "public, max-age=3600" } }
        );
      }
      if (url.pathname === "/sw.js") return new Response(SERVICE_WORKER, { headers: { "content-type": "text/javascript", "cache-control": "no-cache" } });
      if (url.pathname === "/push/latest" && request.method === "POST") {
        // Asked by the service worker after a poke; answers only an address that is signed up.
        const b = await request.json().catch(() => ({}));
        const item = validPushEndpoint(b && b.endpoint) ? await latestForPush(env, b.endpoint) : null;
        return json(item || {});
      }
      const iconM = /^\/icon-(192|512)\.png$/.exec(url.pathname);
      if (iconM) {
        const bin = Uint8Array.from(atob(iconM[1] === "192" ? ICON_192 : ICON_512), (c) => c.charCodeAt(0));
        return new Response(bin, { headers: { "content-type": "image/png", "cache-control": "public, max-age=86400" } });
      }

      if (url.pathname === "/start" && request.method === "GET") {
        const sites = String(url.searchParams.get("sites") || "").split(",").map((x) => x.trim().toLowerCase()).filter((x) => /^[a-z0-9.-]{3,100}$/.test(x)).slice(0, 24);
        return html(startPageHtml(sites));
      }
      if (url.pathname === "/login" && request.method === "POST") {
        const form = await request.formData();
        const password = String(form.get("password") || "");
        const loginName = String(form.get("loginName") || "").trim().toLowerCase();
        const code = String(form.get("code") || "");
        const who = loginName || "administrator";
        const plural = (n) => `${n} minute${n === 1 ? "" : "s"}`;
        // Five wrong tries from the same address locks that address out of this login for 15 minutes.
        const lock = await lockStatus(env, request, loginName);
        if (lock.locked) return html(loginPage(`Too many wrong tries. Try again in ${plural(lock.minutes)}.`), 429);
        const failed = async (message) => {
          const r = await recordFailure(env, request, loginName);
          if (r.justLocked) await auditAdd(env, { who: "Sign-in", text: `Locked sign-in for ${who} after repeated wrong tries (from ${clientIp(request)})` });
          return r.locked ? html(loginPage(`Too many wrong tries. Try again in ${plural(r.minutes)}.`), 429) : html(loginPage(message), 401);
        };
        const succeeded = async (tag, label) => {
          await clearFailures(env, request, loginName);
          await auditAdd(env, { who: label, text: "Signed in" });
          return new Response(null, { status: 303, headers: { location: "/", "set-cookie": await sessionCookieFor(env, tag) } });
        };
        if (!loginName) {
          if (!safeEqual(password, env.ADMIN_PASSWORD)) return failed("Wrong password.");
          const totp = await getJSON(env, "admin:totp", null);
          if (totp) {
            // Password was right but two-step is on: a missing code is just a prompt, a wrong one counts as a try.
            if (!code.trim()) return html(loginPage("Enter the 6-digit code from your authenticator app."), 401);
            const step = await totpVerify(totp.secret, code, totp.step || 0);
            if (!step) return failed("Wrong code.");
            await putJSON(env, "admin:totp", { ...totp, step }); // that code can never be used again
          }
          return succeeded("admin", "Administrator");
        }
        // A client login name is public-ish (shared with them to log in at all), so this path
        // can't use safeEqual's constant-time comparison to hide "no such account" -- the KV
        // lookup itself already takes a different amount of time depending on whether the key
        // exists. That's fine: it's no worse than any login form ever is about that.
        const c = await getJSON(env, `client:${loginName}`, null);
        if (c) {
          if (!(await verifyClientPassword(password, c))) return failed("Wrong login name or password.");
          return succeeded(`client:${loginName}`, `Client "${c.name}"`);
        }
        // Another parent's login (Settings → Other parents).
        const hp = await getJSON(env, `helper:${loginName}`, null);
        if (!hp || !(await verifyClientPassword(password, hp))) return failed("Wrong login name or password.");
        return succeeded(`helper:${loginName}`, `Parent "${hp.name}"`);
      }
      if (url.pathname === "/logout") {
        return new Response(null, { status: 303, headers: { location: "/", "set-cookie": "sess=; Max-Age=0; Path=/" } });
      }
      const session = await getSession(request, env);
      if (!session) {
        return url.pathname.startsWith("/api/") ? json({ error: "Unauthorized" }, 401) : html(loginPage());
      }
      if (url.pathname.startsWith("/api/")) return await adminApi(request, env, url, session);
      if (url.pathname === "/") return html(dashboardPage());
      return html("Not found", 404);
    } catch (e) {
      const msg = e.message || String(e);
      return url.pathname.startsWith("/api/") || url.pathname.startsWith("/agent/") || url.pathname.startsWith("/browser/") || url.pathname.startsWith("/windows/")
        ? json({ error: msg }, 500)
        : html(`Error: ${msg}`, 500);
    }
  },

  // Runs every 15 minutes (see wrangler.toml). Looks for phones that have gone quiet or whose
  // battery is nearly flat -- things nothing arrives to announce.
  async scheduled(event, env, ctx) {
    const work = (async () => {
      try {
        const devices = await listDevices(env);
        await checkDevices(env, devices);
        await maybeSendWeekly(env, devices);
        await maybeSendDaily(env, devices);
      } catch {
        // A failed check just means no alert this round; the next one tries again.
      }
    })();
    ctx.waitUntil(work);
  },
};
