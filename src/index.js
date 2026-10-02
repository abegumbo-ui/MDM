import { buildAgentPolicy, isProtected, normalizeConfig, RESTRICTIONS } from "./policy.js";
import { loginPage, dashboardPage } from "./ui.js";

const SESSION_SECONDS = 60 * 60 * 12;
const POLL_SECONDS = 60;
// Workers KV's free tier allows ~1000 writes/day, so a device record is only
// rewritten when something changed or the stored "last seen" is this stale.
const LAST_SEEN_WRITE_MS = 10 * 60 * 1000;
const COMMANDS = new Set(["lock", "reboot", "wipe", "release", "install", "uninstall", "sync"]);

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
  results: (d.results || []).slice(-10),
});

// ---- admin API (cookie auth) ----
async function adminApi(request, env, url) {
  const path = url.pathname;
  const method = request.method;
  const body = method === "GET" || method === "DELETE" ? null : await request.json().catch(() => ({}));

  if (path === "/api/state" && method === "GET") {
    return json({ config: await loadConfig(env), restrictions: RESTRICTIONS, origin: url.origin });
  }
  if (path === "/api/config" && method === "PUT") {
    await putJSON(env, "config", normalizeConfig(body));
    return json({ ok: true });
  }
  if (path === "/api/enrollment-code" && method === "POST") {
    const code = randomHex(4).toUpperCase();
    await putJSON(env, `code:${code}`, { created: Date.now() }, { expirationTtl: 3600 });
    return json({ code, server: url.origin });
  }
  if (path === "/api/devices" && method === "GET") {
    const devices = await listDevices(env);
    const config = await loadConfig(env);
    return json(
      devices.map((d) => ({
        ...publicDevice(d),
        packages: (d.packages || []).map((p) => ({ ...p, protected: isProtected(p.p) })),
        applied: buildAgentPolicy(config, (d.packages || []).map((p) => p.p)),
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
      d.queue = [...(d.queue || []), { id: randomHex(6), type: body.type, args: body.args || {} }];
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
    if (!/^[0-9A-F]{8}$/.test(code) || !(await env.STATE.get(`code:${code}`))) {
      return json({ error: "Invalid or expired enrollment code" }, 403);
    }
    await env.STATE.delete(`code:${code}`);
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
      results: [],
    });
    return json({ token: `${id}.${secret}`, pollSeconds: POLL_SECONDS });
  }

  if (url.pathname === "/agent/sync") {
    const m = /^Bearer ([0-9a-f]+)\.([0-9a-f]+)$/.exec(request.headers.get("authorization") || "");
    if (!m) return json({ error: "Unauthorized" }, 401);
    const key = `device:${m[1]}`;
    const d = await getJSON(env, key, null);
    if (!d || !safeEqual(d.tokenHash, await sha256(m[2]))) return json({ error: "Unauthorized" }, 401);

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
      if (JSON.stringify(body.info) !== JSON.stringify(d.info)) {
        d.info = body.info;
        dirty = true;
      }
    }
    if (Array.isArray(body.results) && body.results.length) {
      d.results = [...(d.results || []), ...body.results.slice(0, 20).map((r) => ({ ...r, at: now }))].slice(-30);
      dirty = true;
    }
    const commands = d.queue || [];
    if (commands.length) {
      d.queue = [];
      dirty = true;
    }
    if (dirty) await putJSON(env, key, d);

    const policy = buildAgentPolicy(await loadConfig(env), (d.packages || []).map((p) => p.p));
    return json({ policy, commands, pollSeconds: POLL_SECONDS });
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
