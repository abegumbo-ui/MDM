import { buildAgentPolicy, isProtected, normalizeConfig, normalizeOverrides, normalizeSites, RESTRICTIONS, RESTRICTION_BY_KEY, summarizeConfigChange } from "./policy.js";
import { loginPage, dashboardPage } from "./ui.js";

const SESSION_SECONDS = 60 * 60 * 12;
// How often an online phone checks in on its own. A command queued from the dashboard only
// reaches the phone at its NEXT check-in -- there's no push channel to wake it sooner -- so this
// is also the real upper bound on "how long until a dashboard action takes effect" (the UI says so
// honestly rather than a shorter number that isn't true). 5 minutes, not something much shorter,
// to stay well under the KV free tier's write/read caps across many devices.
const POLL_SECONDS = 300;
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
const COMMANDS = new Set(["lock", "reboot", "wipe", "release", "install", "uninstall", "sync", "setPin", "clearPin", "setAppLockPin", "clearAppLockPin", "clearOverrides", "unlock", "addWifi", "resetAppCode", "updateAgent", "listSystemApps", "locate", "checkUpdates", "updateApp"]);
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
const CODE_TYPES = new Set(["enroll", "install", "uninstall", "browser", "freebrowse"]);
const MAX_FREEBROWSE_MINUTES = 240;
const PKG_RE = /^[A-Za-z0-9_.]{1,200}$/;
const B64_RE = /^[A-Za-z0-9+/=]+$/;
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
  overrides: d.overrides || {},
  wifiNetworks: d.wifiNetworks || [],
  appCode: d.appCode || null,
  fallbackCode: d.fallbackCode || null,
  syncPaused: !!d.syncPaused,
  adminPin: d.adminPin ? { pin: d.adminPin.pin, ok: !!d.adminPin.ok } : null,
  siteRequests: d.siteRequests || [],
  messages: d.messages || [],
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

const publicWinDevice = (d) => ({
  id: d.id,
  name: d.name,
  lastSeen: d.lastSeen,
  info: d.info || {},
  enabled: !!d.enabled,
  allowedPrograms: d.allowedPrograms || [],
  reportedEnabled: !!d.reportedEnabled,
  reportedAllowedPrograms: d.reportedAllowedPrograms || [],
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
async function adminApi(request, env, url, session) {
  const path = url.pathname;
  const method = request.method;

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
    if (await getJSON(env, `client:${loginName}`, null)) return json({ error: "That login name is already taken" }, 400);
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
      origin: url.origin,
      masterIterations: MASTER_ITERATIONS,
      session: session.role === "admin" ? { role: "admin" } : { role: "client", id: session.id, name: session.name },
    });
  }
  const devConfig = /^\/api\/devices\/([0-9a-f]+)\/(config|master|clone-from)$/.exec(path);
  if (devConfig) {
    const d = await getJSON(env, `device:${devConfig[1]}`, null);
    if (!d || !canSee(session, d.ownerId)) return json({ error: "Unknown device" }, 404);
    const key = `device:${devConfig[1]}`;
    if (devConfig[2] === "config" && method === "PUT") {
      const { cfg: before } = await configOf(env, d);
      const next = normalizeConfig(body);
      if (next.approveNew && !before.approveNew) d.known = (d.packages || []).map((p) => p.p); // everything installed now counts as already approved
      d.config = next;
      const changes = summarizeConfigChange(before, next);
      if (changes.length) d.pushLog = [...(d.pushLog || []), { at: Date.now(), changes }].slice(-30);
      await putJSON(env, key, d);
      return json({ ok: true });
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
  const brConfig = /^\/api\/browsers\/([0-9a-f]+)\/(config|clone-from)$/.exec(path);
  if (brConfig) {
    const key = `browserDevice:${brConfig[1]}`;
    const d = await getJSON(env, key, null);
    if (!d || !canSee(session, d.ownerId)) return json({ error: "Unknown browser" }, 404);
    if (brConfig[2] === "config" && method === "PUT") {
      d.config = normalizeConfig({ sites: body.sites, restrictBrowsing: body.restrictBrowsing });
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
    if (body.type === "freebrowse") {
      const minutes = Number.isInteger(body.minutes) ? Math.min(Math.max(body.minutes, 5), MAX_FREEBROWSE_MINUTES) : 60;
      value.minutes = minutes;
    }
    // Generated from inside one device's own page: only that device can redeem it, so handing the
    // code to a different device (or a sibling's phone) does nothing. Enroll codes can't work this
    // way -- a device has no identity yet before it enrolls -- so those stay usable by whoever is first.
    if (["install", "uninstall", "freebrowse"].includes(body.type) && /^[0-9a-f]{1,32}$/.test(body.deviceId || "")) {
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
    const devices = (await listDevices(env)).filter((d) => canSee(session, d.ownerId));
    return json(
      await Promise.all(devices.map(async (d) => {
        const { cfg } = await configOf(env, d);
        return {
          ...publicDevice(d),
          config: cfg,
          masterSet: !!d.master,
          packages: (d.packages || []).map((p) => ({ ...p, protected: isProtected(p.p) })),
          applied: buildAgentPolicy(cfg, (d.packages || []).map((p) => p.p), { known: d.known, overrides: d.overrides }),
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
  const dev = /^\/api\/devices\/([0-9a-f]+)(?:\/(command|site-requests|messages))?$/.exec(path);
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
  const wdev = /^\/api\/windevices\/([0-9a-f]+)(?:\/(config|command))?$/.exec(path);
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
      d.enabled = !!body.enabled;
      d.allowedPrograms = Array.isArray(body.allowedPrograms)
        ? body.allowedPrograms.map((p) => String(p).slice(0, 300)).filter(Boolean).slice(0, 200)
        : [];
      await putJSON(env, key, d);
      return json({ ok: true });
    }
    if (method === "POST" && wdev[2] === "command") {
      if (body.type !== "uninstall") return json({ error: "Unknown command" }, 400);
      d.queue = [...(d.queue || []), { id: randomHex(6), type: "uninstall" }];
      await putJSON(env, key, d);
      return json({ ok: true });
    }
  }
  return json({ error: "Not found" }, 404);
}

// ---- agent API (per-device bearer token) ----
async function agentApi(request, env, url) {
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
    url.pathname === "/agent/play-token"
  ) {
    const auth = await authDevice(request, env);
    if (!auth) return json({ error: "Unauthorized" }, 401);
    const { key, d } = auth;

    if (url.pathname === "/agent/update") return json({ latest: await latestAgent(env) });
    if (url.pathname === "/agent/play-token") {
      const result = await playToken(env);
      return json(result, result.error ? 503 : 200);
    }

    if (url.pathname === "/agent/redeem") {
      // One-time codes unlock features inside the phone app (install an APK, remove the agent,
      // browse freely for a while).
      const type = body.type;
      const code = String(body.code || "").toUpperCase();
      const stored = ["install", "uninstall", "freebrowse"].includes(type) && /^[0-9A-F]{8}$/.test(code)
        ? await getJSON(env, `code:${type}:${code}`, null)
        : null;
      if (!stored || (stored.deviceId && stored.deviceId !== d.id)) return json({ error: "Invalid or expired code" }, 403);
      await env.STATE.delete(`code:${type}:${code}`);
      d.results = [...(d.results || []), { id: randomHex(6), type: `code:${type}`, ok: true, msg: "one-time code used on the phone", at: Date.now() }].slice(-30);
      await putJSON(env, key, d);
      return json(type === "freebrowse" ? { ok: true, minutes: stored.minutes || 60 } : { ok: true });
    }

    let dirty = false;
    const now = Date.now();
    if (now - (d.lastSeen || 0) > LAST_SEEN_WRITE_MS) dirty = true;
    d.lastSeen = now;

    if (Array.isArray(body.packages)) {
      const pk = body.packages
        .filter((p) => p && typeof p.p === "string")
        .map((p) => ({ p: p.p, l: String(p.l || p.p).slice(0, 80), s: !!p.s, h: !!p.h }))
        .sort((a, b) => a.p.localeCompare(b.p));
      if (JSON.stringify(pk) !== JSON.stringify(d.packages)) {
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
    const { cfg: config, seeded } = await configOf(env, d);
    if (seeded) dirty = true;
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
    if (body.overrides && typeof body.overrides === "object" && Number(body.overridesRev) > (d.overridesRev || 0)) {
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
    if (body.restrictionOverrides && typeof body.restrictionOverrides === "object" && Number(body.restrictionOverridesRev) > (d.restrictionOverridesRev || 0)) {
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
    if (Number(body.hideKioskAdminRev) > (d.hideKioskAdminRev || 0)) {
      // Same mirroring again, for home screen mode's own Administrator-button hide/show toggle.
      config.hideKioskAdmin = !!body.hideKioskAdminValue;
      d.config = config;
      d.hideKioskAdminRev = Number(body.hideKioskAdminRev);
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
      d.events = [...(d.events || []), ...body.events.slice(0, 40).map((e) => ({ k: String(e.k || "info").slice(0, 20), m: String(e.m || "").slice(0, 200), at: Number(e.at) || now }))].slice(-MAX_EVENTS);
      dirty = true;
    }
    if (Array.isArray(body.siteRequests) && body.siteRequests.length) {
      // Pages the person tried to open that weren't on the allowlist; shown on the dashboard to approve or deny.
      const existing = d.siteRequests || [];
      const urls = new Set(existing.map((r) => r.url));
      const added = body.siteRequests
        .slice(0, 20)
        .map((r) => String(r.url || "").slice(0, 500))
        .filter((u) => u && !urls.has(u) && (urls.add(u), true))
        .map((url) => ({ url, at: now }));
      if (added.length) {
        d.siteRequests = [...existing, ...added].slice(-30);
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

    const policy = buildAgentPolicy(config, reported, { known: d.known, overrides: d.overrides });
    if (config.homeScreen) policy.customIcons = (await env.STATE.get("iconIndex", "json")) || {};
    const master = d.master || null;
    const logoRev = await cachedLogoRev(env);
    return json({ policy, commands, pollSeconds: POLL_SECONDS, overrides: d.overrides || {}, overridesRev: d.overridesRev || 0, restrictionOverrides: d.restrictionOverrides || {}, restrictionOverridesRev: d.restrictionOverridesRev || 0, master, logoRev });
  }
  return json({ error: "Not found" }, 404);
}

// ---- standalone Browser API (per-device bearer token, no agent/MDM involved) ----
async function browserApi(request, env, url) {
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const body = await request.json().catch(() => ({}));

  if (url.pathname === "/browser/enroll") {
    const code = String(body.code || "").toUpperCase();
    const codeRec = /^[0-9A-F]{8}$/.test(code) ? await getJSON(env, `code:browser:${code}`, null) : null;
    if (!codeRec) return json({ error: "Invalid or expired code" }, 403);
    await env.STATE.delete(`code:browser:${code}`);
    const { id, secret } = await createBrowserDevice(env, body.info, null, codeRec.ownerId);
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
    const { id, secret } = await createBrowserDevice(env, body.info, "New Browser");
    return json({ token: `${id}.${secret}`, pollSeconds: POLL_SECONDS });
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
      const added = body.siteRequests
        .slice(0, 20)
        .map((r) => String(r.url || "").slice(0, 500))
        .filter((u) => u && !urls.has(u) && (urls.add(u), true))
        .map((url) => ({ url, at: now }));
      if (added.length) {
        d.siteRequests = [...existing, ...added].slice(-30);
        dirty = true;
      }
    }
    const { cfg: config, seeded } = await configOf(env, d);
    if (seeded) dirty = true;
    if (dirty) await putJSON(env, key, d);
    return json({ sites: normalizeSites(config.sites), restrictBrowsing: config.restrictBrowsing, pollSeconds: POLL_SECONDS });
  }
  return json({ error: "Not found" }, 404);
}

// ---- Windows LockGuard API (per-device bearer token, no agent/MDM involved) ----
async function windowsApi(request, env, url) {
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const body = await request.json().catch(() => ({}));

  if (url.pathname === "/windows/register") {
    // No code, same reasoning as /browser/register: it shows up immediately on the dashboard,
    // disabled and with nothing allowed, for the admin to configure and name.
    const { id, secret } = await createWinDevice(env, body.info);
    return json({ token: `${id}.${secret}`, pollSeconds: POLL_SECONDS });
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
      pollSeconds: POLL_SECONDS,
    });
  }
  return json({ error: "Not found" }, 404);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (!env.ADMIN_PASSWORD) {
      // Names only, never values: helps diagnose a missing binding.
      return html(`ADMIN_PASSWORD secret is not set. See SETUP.md.<br>Settings this app can see: ${Object.keys(env).join(", ") || "(none)"}`, 500);
    }

    try {
      if (url.pathname.startsWith("/agent/")) return await agentApi(request, env, url);
      if (url.pathname.startsWith("/browser/")) return await browserApi(request, env, url);
      if (url.pathname.startsWith("/windows/")) return await windowsApi(request, env, url);

      if (url.pathname === "/login" && request.method === "POST") {
        const form = await request.formData();
        const password = String(form.get("password") || "");
        const loginName = String(form.get("loginName") || "").trim().toLowerCase();
        if (!loginName) {
          if (!safeEqual(password, env.ADMIN_PASSWORD)) return html(loginPage("Wrong password."), 401);
          return new Response(null, { status: 303, headers: { location: "/", "set-cookie": await sessionCookieFor(env, "admin") } });
        }
        // A client login name is public-ish (shared with them to log in at all), so this path
        // can't use safeEqual's constant-time comparison to hide "no such account" -- the KV
        // lookup itself already takes a different amount of time depending on whether the key
        // exists. That's fine: it's no worse than any login form ever is about that.
        const c = await getJSON(env, `client:${loginName}`, null);
        if (!c || !(await verifyClientPassword(password, c))) return html(loginPage("Wrong login name or password."), 401);
        return new Response(null, { status: 303, headers: { location: "/", "set-cookie": await sessionCookieFor(env, `client:${loginName}`) } });
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
};
