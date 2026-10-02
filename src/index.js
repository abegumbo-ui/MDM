import { buildAgentPolicy, isProtected, normalizeConfig, normalizeOverrides, RESTRICTIONS } from "./policy.js";
import { loginPage, dashboardPage } from "./ui.js";

const SESSION_SECONDS = 60 * 60 * 12;
const POLL_SECONDS = 60;
// Workers KV's free tier allows ~1000 writes/day, so a device record is only
// rewritten when something changed or the stored "last seen" is this stale.
const LAST_SEEN_WRITE_MS = 10 * 60 * 1000;
const COMMANDS = new Set(["lock", "reboot", "wipe", "release", "install", "uninstall", "sync", "setPin", "clearPin", "clearOverrides", "unlock", "addWifi"]);
const MAX_LOCK_MINUTES = 480; // 8 hours: emergency calls stay possible but this is a safety cap
const MASTER_ITERATIONS = 100000;
const MAX_EVENTS = 60;
const CODE_TYPES = new Set(["enroll", "install", "uninstall"]);
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
});

// ---- admin API (cookie auth) ----
async function adminApi(request, env, url) {
  const path = url.pathname;
  const method = request.method;
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
    await putJSON(env, `code:${body.type}:${code}`, { created: Date.now() }, { expirationTtl: 3600 });
    return json({ code, type: body.type, server: url.origin, expiresInSeconds: 3600 });
  }
  const iconMatch = /^\/api\/icon\/([A-Za-z0-9_.]+)$/.exec(path);
  if (iconMatch && method === "GET") {
    const b64 = await env.STATE.get(`icon:${iconMatch[1]}`);
    if (!b64) return new Response(null, { status: 404 });
    const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
    return new Response(bytes, { headers: { "content-type": "image/png", "cache-control": "private, max-age=86400" } });
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
  const dev = /^\/api\/devices\/([0-9a-f]+)(?:\/(command))?$/.exec(path);
  if (dev) {
    const key = `device:${dev[1]}`;
    const d = await getJSON(env, key, null);
    if (!d) return json({ error: "Unknown device" }, 404);
    if (method === "DELETE" && !dev[2]) {
      await env.STATE.delete(key);
      return json({ ok: true });
    }
    if (method === "POST" && dev[2]) {
      if (!COMMANDS.has(body.type)) return json({ error: "Unknown command" }, 400);
      const args = {};
      const given = body.args || {};
      if (body.type === "install") {
        if (typeof given.url !== "string" || !given.url.startsWith("https://")) return json({ error: "APK link must start with https://" }, 400);
        args.url = given.url;
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

  if (url.pathname === "/agent/sync" || url.pathname === "/agent/redeem") {
    const m = /^Bearer ([0-9a-f]+)\.([0-9a-f]+)$/.exec(request.headers.get("authorization") || "");
    if (!m) return json({ error: "Unauthorized" }, 401);
    const key = `device:${m[1]}`;
    const d = await getJSON(env, key, null);
    if (!d || !safeEqual(d.tokenHash, await sha256(m[2]))) return json({ error: "Unauthorized" }, 401);

    if (url.pathname === "/agent/redeem") {
      // One-time codes unlock features inside the phone app (install an APK, remove the agent).
      const type = body.type;
      const code = String(body.code || "").toUpperCase();
      if (!["install", "uninstall"].includes(type) || !/^[0-9A-F]{8}$/.test(code) || !(await env.STATE.get(`code:${type}:${code}`))) {
        return json({ error: "Invalid or expired code" }, 403);
      }
      await env.STATE.delete(`code:${type}:${code}`);
      d.results = [...(d.results || []), { id: randomHex(6), type: `code:${type}`, ok: true, msg: "one-time code used on the phone", at: Date.now() }].slice(-30);
      await putJSON(env, key, d);
      return json({ ok: true });
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
    if (Array.isArray(body.events) && body.events.length) {
      d.events = [...(d.events || []), ...body.events.slice(0, 40).map((e) => ({ k: String(e.k || "info").slice(0, 20), m: String(e.m || "").slice(0, 200), at: Number(e.at) || now }))].slice(-MAX_EVENTS);
      dirty = true;
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
    const master = await getJSON(env, "master", null);
    return json({ policy, commands, pollSeconds: POLL_SECONDS, overrides: d.overrides || {}, overridesRev: d.overridesRev || 0, master });
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
      return url.pathname.startsWith("/api/") || url.pathname.startsWith("/agent/")
        ? json({ error: msg }, 500)
        : html(`Error: ${msg}`, 500);
    }
  },
};
