import { buildAgentPolicy, isProtected, normalizeConfig, normalizeOverrides, normalizeSites, RESTRICTIONS } from "./policy.js";
import { loginPage, dashboardPage } from "./ui.js";

const SESSION_SECONDS = 60 * 60 * 12;
const POLL_SECONDS = 60;
// Workers KV's free tier allows ~1000 writes/day, so a device record is only
// rewritten when something changed or the stored "last seen" is this stale.
const LAST_SEEN_WRITE_MS = 10 * 60 * 1000;
const COMMANDS = new Set(["lock", "reboot", "wipe", "release", "install", "uninstall", "sync", "setPin", "clearPin", "clearOverrides", "unlock", "addWifi", "resetAppCode", "updateAgent"]);
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
const MASTER_ITERATIONS = 100000;
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

// ---- admin auth: one password (Worker secret) -> HMAC-signed cookie ----
async function isAuthed(request, env) {
  const m = /(?:^|;\s*)sess=(\d+)\.([0-9a-f]+)/.exec(request.headers.get("cookie") || "");
  if (!m || Number(m[1]) < Date.now() / 1000) return false;
  return safeEqual(m[2], await hmac(env.ADMIN_PASSWORD, `sess:${m[1]}`));
}
async function sessionCookie(env) {
  const exp = Math.floor(Date.now() / 1000) + SESSION_SECONDS;
  return `sess=${exp}.${await hmac(env.ADMIN_PASSWORD, `sess:${exp}`)}; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=${SESSION_SECONDS}`;
}

// ---- KV state ----
const getJSON = async (env, key, dflt) => (await env.STATE.get(key, "json")) ?? dflt;
const putJSON = (env, key, val, opts) => env.STATE.put(key, JSON.stringify(val), opts);
const loadConfig = async (env) => normalizeConfig(await getJSON(env, "config", null));

async function listDevices(env) {
  const out = [];
  let cursor;
  do {
    const page = await env.STATE.list({ prefix: "device:", ...(cursor && { cursor }) });
    for (const k of page.keys) {
      const d = await getJSON(env, k.name, null);
      if (d) out.push(d);
    }
    cursor = page.list_complete ? undefined : page.cursor;
  } while (cursor);
  return out;
}

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
  adminPin: d.adminPin ? { pin: d.adminPin.pin, ok: !!d.adminPin.ok } : null,
  siteRequests: d.siteRequests || [],
  messages: d.messages || [],
});

// Browser devices: a standalone Browser app connected straight to this dashboard, with no agent/MDM
// at all. They share the same global "sites" allowlist as agent-managed devices, but have no app
// policy, restrictions, or device-owner features of their own.
async function listBrowserDevices(env) {
  const out = [];
  let cursor;
  do {
    const page = await env.STATE.list({ prefix: "browserDevice:", ...(cursor && { cursor }) });
    for (const k of page.keys) {
      const d = await getJSON(env, k.name, null);
      if (d) out.push(d);
    }
    cursor = page.list_complete ? undefined : page.cursor;
  } while (cursor);
  return out;
}

const publicBrowserDevice = (d) => ({
  id: d.id,
  name: d.name,
  lastSeen: d.lastSeen,
  info: d.info || {},
  siteRequests: d.siteRequests || [],
});

/** namePrefix: null names it from device info (code-based /browser/enroll); a string (e.g. "New
 * Browser") names it that plus a short id suffix, so several self-registrations stay tellable apart. */
async function createBrowserDevice(env, info, namePrefix) {
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

// Latest agent build, published by GitHub Actions next to the APK. Cached at the edge for 5 minutes.
async function latestAgent(env) {
  const repo = env.GITHUB_REPO || DEFAULT_REPO;
  const base = `https://github.com/${repo}/releases/download/latest`;
  try {
    const r = await fetch(`${base}/version.json`, { cf: { cacheTtl: 300, cacheEverything: true } });
    if (!r.ok) return null;
    const v = await r.json();
    if (!Number.isInteger(v.versionCode)) return null;
    return { versionCode: v.versionCode, apkUrl: `${base}/mdm-agent.apk` };
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
async function adminApi(request, env, url) {
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
      await env.STATE.delete("logo");
      await env.STATE.delete("logoRev");
      return json({ ok: true });
    }
    if (method === "PUT") {
      const buf = await request.arrayBuffer();
      if (buf.byteLength < 50 || buf.byteLength > MAX_IMAGE_BYTES || !isPng(buf)) return json({ error: "Use a PNG image under 200 KB" }, 400);
      await env.STATE.put("logo", toBase64(buf));
      await env.STATE.put("logoRev", String(Date.now()));
      return json({ ok: true });
    }
  }
  const body = method === "GET" || method === "DELETE" ? null : await request.json().catch(() => ({}));

  if (path === "/api/state" && method === "GET") {
    return json({ config: await loadConfig(env), restrictions: RESTRICTIONS, origin: url.origin, masterSet: !!(await env.STATE.get("master")), masterIterations: MASTER_ITERATIONS });
  }
  if (path === "/api/config" && method === "PUT") {
    const before = await loadConfig(env);
    const next = normalizeConfig(body);
    await putJSON(env, "config", next);
    if (next.approveNew && !before.approveNew) {
      // Turning approval mode on: everything installed right now counts as already approved.
      for (const d of await listDevices(env)) {
        d.known = (d.packages || []).map((p) => p.p);
        await putJSON(env, `device:${d.id}`, d);
      }
    }
    return json({ ok: true });
  }
  if (path === "/api/master" && method === "PUT") {
    // The browser derives the hash (PBKDF2), so the code itself never reaches the server.
    if (!/^[0-9a-f]{32}$/.test(body.salt || "") || !/^[0-9a-f]{64}$/.test(body.hash || "")) return json({ error: "Invalid master code data" }, 400);
    await putJSON(env, "master", { salt: body.salt, hash: body.hash, iterations: MASTER_ITERATIONS });
    return json({ ok: true });
  }
  if (path === "/api/master" && method === "DELETE") {
    await env.STATE.delete("master");
    return json({ ok: true });
  }
  if (path === "/api/codes" && method === "POST") {
    if (!CODE_TYPES.has(body.type)) return json({ error: "Unknown code type" }, 400);
    const code = randomHex(4).toUpperCase();
    const value = { created: Date.now() };
    if (body.type === "freebrowse") {
      const minutes = Number.isInteger(body.minutes) ? Math.min(Math.max(body.minutes, 5), MAX_FREEBROWSE_MINUTES) : 60;
      value.minutes = minutes;
    }
    await putJSON(env, `code:${body.type}:${code}`, value, { expirationTtl: 3600 });
    return json({ code, type: body.type, server: url.origin, expiresInSeconds: 3600, minutes: value.minutes });
  }
  const iconMatch = /^\/api\/icon\/([A-Za-z0-9_.]+)$/.exec(path);
  if (iconMatch && method === "GET") {
    const b64 = (await env.STATE.get(`iconc:${iconMatch[1]}`)) || (await env.STATE.get(`icon:${iconMatch[1]}`));
    return b64 ? imageResponse(b64) : new Response(null, { status: 404 });
  }
  if (path === "/api/devices" && method === "GET") {
    const devices = await listDevices(env);
    const config = await loadConfig(env);
    return json(
      devices.map((d) => ({
        ...publicDevice(d),
        packages: (d.packages || []).map((p) => ({ ...p, protected: isProtected(p.p) })),
        applied: buildAgentPolicy(config, (d.packages || []).map((p) => p.p), { known: d.known, overrides: d.overrides }),
      })),
    );
  }
  if (path === "/api/browsers" && method === "GET") {
    return json((await listBrowserDevices(env)).map(publicBrowserDevice));
  }
  const bdev = /^\/api\/browsers\/([0-9a-f]+)(?:\/(site-requests))?$/.exec(path);
  if (bdev) {
    const key = `browserDevice:${bdev[1]}`;
    const d = await getJSON(env, key, null);
    if (!d) return json({ error: "Unknown browser" }, 404);
    if (method === "DELETE" && !bdev[2]) {
      // The app keeps no local fallback of its own, so this is the only way to disconnect it
      // short of uninstalling: without a token it goes right back to "nothing is allowed" and
      // needs a fresh code.
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
    if (!d) return json({ error: "Unknown device" }, 404);
    if (method === "DELETE" && !dev[2]) {
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
    if (!/^[0-9A-F]{8}$/.test(code) || !(await env.STATE.get(`code:enroll:${code}`))) {
      return json({ error: "Invalid or expired enrollment code" }, 403);
    }
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
    });
    return json({ token: `${id}.${secret}`, pollSeconds: POLL_SECONDS });
  }

  if (url.pathname === "/agent/sync" || url.pathname === "/agent/redeem" || url.pathname === "/agent/update") {
    const auth = await authDevice(request, env);
    if (!auth) return json({ error: "Unauthorized" }, 401);
    const { key, d } = auth;

    if (url.pathname === "/agent/update") return json({ latest: await latestAgent(env) });

    if (url.pathname === "/agent/redeem") {
      // One-time codes unlock features inside the phone app (install an APK, remove the agent,
      // browse freely for a while).
      const type = body.type;
      const code = String(body.code || "").toUpperCase();
      const stored = ["install", "uninstall", "freebrowse"].includes(type) && /^[0-9A-F]{8}$/.test(code)
        ? await getJSON(env, `code:${type}:${code}`, null)
        : null;
      if (!stored) return json({ error: "Invalid or expired code" }, 403);
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
    const config = await loadConfig(env);
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
      // Changes made on the phone with the master code (newest write wins).
      d.overrides = normalizeOverrides(body.overrides);
      d.overridesRev = Number(body.overridesRev);
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
    const master = await getJSON(env, "master", null);
    const logoRev = Number(await env.STATE.get("logoRev")) || 0;
    return json({ policy, commands, pollSeconds: POLL_SECONDS, overrides: d.overrides || {}, overridesRev: d.overridesRev || 0, master, logoRev });
  }
  return json({ error: "Not found" }, 404);
}

// ---- standalone Browser API (per-device bearer token, no agent/MDM involved) ----
async function browserApi(request, env, url) {
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const body = await request.json().catch(() => ({}));

  if (url.pathname === "/browser/enroll") {
    const code = String(body.code || "").toUpperCase();
    if (!/^[0-9A-F]{8}$/.test(code) || !(await env.STATE.get(`code:browser:${code}`))) {
      return json({ error: "Invalid or expired code" }, 403);
    }
    await env.STATE.delete(`code:browser:${code}`);
    const { id, secret } = await createBrowserDevice(env, body.info, null);
    return json({ token: `${id}.${secret}`, pollSeconds: POLL_SECONDS });
  }

  if (url.pathname === "/browser/register") {
    // No code: Browser connects itself the moment someone taps "yes" to whitelist mode, with
    // nothing typed on either end. It shows up on the dashboard immediately as "New Browser"
    // (plus a short id so more than one is tellable apart) for the admin to rename and manage.
    // There is deliberately no gate here beyond knowing the dashboard's own address: a rogue
    // registration can only ever reach what's already on the global Sites allowlist, and it's
    // always visible (and removable) under Sites -> Standalone browsers.
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
    if (dirty) await putJSON(env, key, d);
    const config = await loadConfig(env);
    return json({ sites: normalizeSites(config.sites), restrictBrowsing: config.restrictBrowsing, pollSeconds: POLL_SECONDS });
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

      if (url.pathname === "/login" && request.method === "POST") {
        const form = await request.formData();
        const ok = safeEqual(String(form.get("password") || ""), env.ADMIN_PASSWORD);
        if (!ok) return html(loginPage("Wrong password."), 401);
        return new Response(null, { status: 303, headers: { location: "/", "set-cookie": await sessionCookie(env) } });
      }
      if (url.pathname === "/logout") {
        return new Response(null, { status: 303, headers: { location: "/", "set-cookie": "sess=; Max-Age=0; Path=/" } });
      }
      if (!(await isAuthed(request, env))) {
        return url.pathname.startsWith("/api/") ? json({ error: "Unauthorized" }, 401) : html(loginPage());
      }
      if (url.pathname.startsWith("/api/")) return await adminApi(request, env, url);
      if (url.pathname === "/") return html(dashboardPage());
      return html("Not found", 404);
    } catch (e) {
      const msg = e.message || String(e);
      return url.pathname.startsWith("/api/") || url.pathname.startsWith("/agent/") || url.pathname.startsWith("/browser/")
        ? json({ error: msg }, 500)
        : html(`Error: ${msg}`, 500);
    }
  },
};
