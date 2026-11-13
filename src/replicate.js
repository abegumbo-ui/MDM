// Running the dashboard on a Raspberry Pi, with Cloudflare as the backup (see pi/README.md).
// Nothing here does anything unless these are set (so the Cloudflare dashboard works exactly as before):
//   PRIMARY_URL, BACKUP_URL  - the two addresses, given to the phones in their check-in reply
//   REPLICA_SECRET           - shared by the two servers, for /internal/*
//   ROLE                     - "backup" on Cloudflare, "main" on the Pi
//
// How the two stay in step:
//  - The Pi keeps the copy on Cloudflare up to date (/internal/replicate: batches every 30 minutes, and soon after
//    an administrator change). Cloudflare's free plan allows about 1,000 saves a day, so changes are grouped.
//  - Phones use the Pi; when they cannot reach it they use Cloudflare and say so (viaBackup). Cloudflare then notes
//    "outage" (one save) and is the one that saves changes until the Pi is back.
//  - The Pi, when it is back, asks /internal/status; if there was an outage it first brings everything from Cloudflare
//    (/internal/export), then tells Cloudflare (/internal/ack) and is the main server again.
const json = (obj, status = 200) => new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json" } });

async function digest(s) {
  return new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(String(s))));
}
async function same(a, b) {
  const [x, y] = await Promise.all([digest(a), digest(b)]);
  let diff = 0;
  for (let i = 0; i < x.length; i++) diff |= x[i] ^ y[i];
  return diff === 0;
}

/** Never copied between the servers (large, short-lived, or each server's own). */
export const skipKey = (k) => k.startsWith("repl:") || k.startsWith("shot:") || k.startsWith("winshot:") || k.startsWith("apk:") || k === "adminActive" || k === "adminAction";

/** The two addresses for the phones, or undefined when this is a single-server setup. */
export const serversFor = (env) =>
  env.PRIMARY_URL && env.BACKUP_URL ? { primary: String(env.PRIMARY_URL).replace(/\/+$/, ""), backup: String(env.BACKUP_URL).replace(/\/+$/, "") } : undefined;

/** Which server this is, for the dashboard badge: "pi" on the Raspberry Pi, otherwise "cloudflare". */
export async function serverInfo(env) {
  const s = serversFor(env);
  return {
    runtime: env.RUNTIME === "pi" ? "pi" : "cloudflare",
    role: env.ROLE || "",
    primary: s ? s.primary : "",
    backup: s ? s.backup : "",
    // Cloudflare only: phones had to use it because the Pi was off (it is the one saving changes until the Pi is back).
    outage: env.ROLE === "backup" ? !!(await env.STATE.get("repl:outage")) : false,
  };
}

/** Which server a phone's check-in went to: the one it says it fell back to, else this one. */
export const viaOf = (env, body) => (body && body.viaBackup === true ? "cloudflare" : env.RUNTIME === "pi" ? "pi" : "cloudflare");

/** A phone that could not reach the Pi checks in here: from now on this server is the one saving changes. */
export async function noteBackupUse(env, body, now = Date.now()) {
  if (env.ROLE !== "backup" || !body || body.viaBackup !== true) return;
  if (!(await env.STATE.get("repl:outage"))) await env.STATE.put("repl:outage", String(now));
}

const b64ToBytes = (s) => Uint8Array.from(atob(s), (c) => c.charCodeAt(0));

export async function handleInternal(request, env, url) {
  if (!env.REPLICA_SECRET) return json({ error: "not configured" }, 404);
  if (!(await same(request.headers.get("x-replica-secret") || "", env.REPLICA_SECRET))) return json({ error: "forbidden" }, 403);
  const p = url.pathname;
  const post = request.method === "POST";

  if (p === "/internal/status") return json({ outage: Number(await env.STATE.get("repl:outage")) || 0, role: env.ROLE || "" });

  if (p === "/internal/ack" && post) {
    await env.STATE.delete("repl:outage");
    return json({ ok: true });
  }

  if (p === "/internal/replicate" && post) {
    // While this server is the one saving changes (an outage), the Pi must not overwrite them.
    if (env.ROLE === "backup" && (await env.STATE.get("repl:outage"))) return json({ error: "outage", outage: true }, 409);
    const body = await request.json().catch(() => null);
    if (!body) return json({ error: "bad body" }, 400);
    const now = Date.now();
    let puts = 0, deletes = 0;
    for (const e of (Array.isArray(body.puts) ? body.puts : []).slice(0, 200)) {
      if (!e || typeof e.k !== "string" || !e.r || skipKey(e.k)) continue;
      const opts = {};
      if (e.r.exp) {
        if (e.r.exp - now < 61000) continue; // about to expire anyway (KV needs 60 s)
        opts.expiration = Math.floor(e.r.exp / 1000);
      }
      if (e.r.meta) opts.metadata = e.r.meta;
      await env.STATE.put(e.k, e.r.t === "b" ? b64ToBytes(e.r.v) : String(e.r.v), opts);
      puts++;
    }
    for (const k of (Array.isArray(body.deletes) ? body.deletes : []).slice(0, 200)) {
      if (typeof k !== "string" || skipKey(k)) continue;
      await env.STATE.delete(k);
      deletes++;
    }
    return json({ ok: true, puts, deletes });
  }

  if (p === "/internal/export") {
    const cursor = url.searchParams.get("cursor") || undefined;
    const page = await env.STATE.list({ ...(cursor && { cursor }), limit: 100 });
    const entries = [];
    for (const k of page.keys) {
      if (skipKey(k.name)) continue;
      const v = await env.STATE.get(k.name);
      if (v === null) continue;
      entries.push({ k: k.name, r: { t: "s", v, ...(k.expiration ? { exp: k.expiration * 1000 } : {}), ...(k.metadata ? { meta: k.metadata } : {}) } });
    }
    return json({ entries, cursor: page.list_complete ? null : page.cursor });
  }

  return json({ error: "not found" }, 404);
}
