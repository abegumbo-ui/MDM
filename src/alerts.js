// Alerts: the dashboard can send you a message (to a phone notification service such as ntfy, or a
// Discord / Slack / Teams-style webhook) when something needs attention. It never emails or texts
// anyone itself, and nothing is sent until an admin pastes a webhook address into Settings.

export const DEFAULT_ALERTS = {
  url: "",
  offline: true, // a phone has not checked in for a while
  offlineHours: 3,
  battery: true, // a phone's battery is very low
  batteryPct: 15,
  requests: true, // the phone asked for a site or sent you a message
  tamper: true, // the Agent noticed something security related on the phone
  places: true, // a phone arrived at or left a saved place (location history)
  weekly: true, // a summary of every phone, Sunday evenings
};

export const ALERTS_KEY = "alerts";

export async function getAlerts(env) {
  const stored = (await env.STATE.get(ALERTS_KEY, "json")) || {};
  return { ...DEFAULT_ALERTS, ...stored };
}

/** Only https addresses, nothing with a login baked in, nothing absurdly long. */
export function validWebhook(url) {
  if (typeof url !== "string" || url.length > 500) return false;
  try {
    const u = new URL(url);
    return u.protocol === "https:" && !u.username && !u.password && u.hostname.includes(".");
  } catch {
    return false;
  }
}

/** What the settings page may see: the address itself can contain a secret token, so only a hint. */
export function publicAlerts(a) {
  let hint = "";
  if (a.url) {
    try {
      const u = new URL(a.url);
      hint = `${u.hostname}/…${u.pathname.slice(-4)}`;
    } catch {
      hint = "set";
    }
  }
  const { url, ...rest } = a;
  return { ...rest, urlSet: !!a.url, urlHint: hint };
}

/** Merges an admin's edit into the stored alerts. An absent `url` keeps the old one; "" clears it. */
export function mergeAlerts(current, body) {
  const out = { ...current };
  if (typeof body.url === "string") {
    const u = body.url.trim();
    if (u && !validWebhook(u)) throw new Error("That is not a valid https:// address.");
    out.url = u;
  }
  for (const k of ["offline", "battery", "requests", "tamper", "places", "weekly"]) if (typeof body[k] === "boolean") out[k] = body[k];
  const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, Math.round(Number(v))));
  if (body.offlineHours !== undefined && Number.isFinite(Number(body.offlineHours))) out.offlineHours = clamp(body.offlineHours, 1, 72);
  if (body.batteryPct !== undefined && Number.isFinite(Number(body.batteryPct))) out.batteryPct = clamp(body.batteryPct, 5, 50);
  return out;
}

/** Sends one message. Never throws: a broken webhook must not break whatever triggered it. */
export async function sendAlert(env, title, message, alerts) {
  try {
    const a = alerts || (await getAlerts(env));
    if (!a.url || !validWebhook(a.url)) return { ok: false, error: "no webhook set" };
    const host = new URL(a.url).hostname;
    const isNtfy = host === "ntfy.sh" || host.endsWith(".ntfy.sh");
    const r = await fetch(
      a.url,
      isNtfy
        ? { method: "POST", headers: { Title: title, "content-type": "text/plain; charset=utf-8" }, body: message }
        : {
            method: "POST",
            headers: { "content-type": "application/json" },
            // "content" is what Discord reads, "text" is what Slack and Teams read.
            body: JSON.stringify({ content: `**${title}**\n${message}`, text: `${title}\n${message}`, username: "MDM" }),
          }
    );
    return r.ok ? { ok: true } : { ok: false, error: `the address answered ${r.status}` };
  } catch (e) {
    return { ok: false, error: e.message || String(e) };
  }
}

/** Runs the message in the background when we have a way to, otherwise waits for it. */
export async function sendAlertLater(env, ctx, title, message) {
  const a = await getAlerts(env);
  if (!a.url) return;
  const p = sendAlert(env, title, message, a);
  if (ctx && typeof ctx.waitUntil === "function") ctx.waitUntil(p);
  else await p;
}

/**
 * The every-15-minutes check for things that are about *absence* (a phone gone quiet, a battery
 * running down) -- nothing arrives to trigger those, so they are looked for on a timer. Each
 * condition alerts once and only again after it has cleared, so a phone that stays offline for a
 * day sends one message, not ninety-six.
 */
export async function checkDevices(env, devices, now = Date.now()) {
  const a = await getAlerts(env);
  if (!a.url || (!a.offline && !a.battery)) return { sent: 0 };
  let sent = 0;
  for (const d of devices) {
    const key = `alertstate:${d.id}`;
    const prev = (await env.STATE.get(key, "json")) || {};
    const next = { ...prev };
    const quietFor = d.lastSeen ? now - d.lastSeen : 0;
    const offline = !!d.lastSeen && !d.syncPaused && quietFor > a.offlineHours * 3600 * 1000;
    if (a.offline) {
      if (offline && !prev.offline) {
        next.offline = true;
        const h = Math.floor(quietFor / 3600000);
        if ((await sendAlert(env, `${d.name} is offline`, `No check-in for about ${h} hour${h === 1 ? "" : "s"}.`, a)).ok) sent++;
      } else if (!offline && prev.offline) {
        next.offline = false;
      }
    }
    const bat = d.info && d.info.battery;
    if (a.battery && bat && typeof bat.pct === "number") {
      const low = bat.pct <= a.batteryPct && !bat.charging;
      if (low && !prev.battery) {
        next.battery = true;
        if ((await sendAlert(env, `${d.name} battery is low`, `${bat.pct}% and not charging.`, a)).ok) sent++;
      } else if (prev.battery && (bat.charging || bat.pct >= a.batteryPct + 10)) {
        next.battery = false;
      }
    }
    if (JSON.stringify(next) !== JSON.stringify(prev)) await env.STATE.put(key, JSON.stringify(next));
  }
  return { sent };
}

/* ------------------------------ weekly report ------------------------------ */

const fmtMin = (m) => (m >= 60 ? Math.floor(m / 60) + " h " + (m % 60) + " min" : m + " min");

/** The weekly summary, in plain words: screen time, top apps, blocked sites, calls, places. Pure, for tests. */
export function weeklyReport(devices, now = Date.now()) {
  const since = now - 7 * 86400000;
  const days = new Set(Array.from({ length: 7 }, (_, i) => new Date(now - i * 86400000).toISOString().slice(0, 10)));
  const blocks = [];
  for (const d of devices) {
    const lines = [];
    const usage = Object.entries(d.usage || {}).filter(([day, u]) => days.has(day) && u && u.ok !== false);
    if (usage.length) {
      const total = usage.reduce((n, [, u]) => n + (u.total || 0), 0);
      const apps = {};
      for (const [, u] of usage) for (const a of u.apps || []) apps[a.l || a.p] = (apps[a.l || a.p] || 0) + (a.m || 0);
      const top = Object.entries(apps).sort((a, b) => b[1] - a[1]).slice(0, 3).map(([l, m]) => l + " " + fmtMin(m));
      lines.push("Screen time: about " + fmtMin(Math.round(total / usage.length)) + " a day" + (top.length ? " (most: " + top.join(", ") + ")" : ""));
    }
    const blocked = {};
    let visited = 0;
    for (const [day, sites] of Object.entries(d.siteHistory || {})) {
      if (!days.has(day)) continue;
      for (const [site, r] of Object.entries(sites)) {
        visited++;
        if (r[1]) blocked[site] = (blocked[site] || 0) + r[1];
      }
    }
    const nb = Object.keys(blocked).length;
    if (visited) lines.push("Sites: " + visited + " visits to sites" + (nb ? ", " + nb + " blocked (" + Object.entries(blocked).sort((a, b) => b[1] - a[1]).slice(0, 3).map(([s]) => s).join(", ") + ")" : ", none blocked"));
    const calls = (d.calls || []).filter((c) => c.at >= since);
    if (calls.length) lines.push("Calls: " + calls.length + (calls.some((c) => c.type === "blocked") ? ", " + calls.filter((c) => c.type === "blocked").length + " blocked" : ""));
    const ev = (d.events || []).filter((e) => e.at >= since);
    const sos = ev.filter((e) => e.k === "sos" && !/maps\.google/.test(e.m)).length;
    if (sos) lines.push("Asked for help: " + sos + " time" + (sos === 1 ? "" : "s"));
    const sec = ev.filter((e) => e.k === "security").length;
    if (sec) lines.push("Security notices: " + sec + " (see the phone log)");
    const reqs = (d.siteRequests || []).length + (d.appRequests || []).length;
    if (reqs) lines.push("Requests waiting for you: " + reqs);
    if (!d.lastSeen || d.lastSeen < since) lines.push("Did not check in this week");
    blocks.push(d.name + "\n" + (lines.length ? lines.map((l) => "- " + l).join("\n") : "- Nothing to report"));
  }
  return blocks.join("\n\n");
}

/** Sunday from 17:00 UTC (the first 15-minute check after), once a week, if switched on. */
export async function maybeSendWeekly(env, devices, now = Date.now()) {
  const t = new Date(now);
  if (t.getUTCDay() !== 0 || t.getUTCHours() < 17 || !devices.length) return { sent: false };
  const a = await getAlerts(env);
  if (!a.url || !a.weekly) return { sent: false };
  const week = t.toISOString().slice(0, 10);
  if ((await env.STATE.get("weeklySent")) === week) return { sent: false };
  await env.STATE.put("weeklySent", week);
  const r = await sendAlert(env, "Weekly report", weeklyReport(devices, now), a);
  return { sent: !!r.ok };
}

/* ------------------------------ change history ------------------------------ */

const AUDIT_KEY = "auditlog";
const MAX_AUDIT = 300;

export async function auditAdd(env, entry) {
  try {
    const list = (await env.STATE.get(AUDIT_KEY, "json")) || [];
    list.push({ at: Date.now(), ...entry });
    await env.STATE.put(AUDIT_KEY, JSON.stringify(list.slice(-MAX_AUDIT)));
  } catch {
    // The history is a convenience; failing to write it must never fail the real action.
  }
}

export async function auditList(env) {
  return ((await env.STATE.get(AUDIT_KEY, "json")) || []).slice().reverse();
}

/** A plain-English line for an admin or client action, or null for ones not worth recording. */
export function describeChange(method, path, body) {
  const m = /^\/api\/(devices|browsers|windevices)\/([0-9a-f]+)(\/[a-z-]+)?$/.exec(path);
  const target = m ? { kind: m[1], id: m[2] } : null;
  const sub = m ? m[3] || "" : "";
  if (m && sub === "/command" && method === "POST") return { text: `Sent the "${String((body && body.type) || "?").slice(0, 30)}" command`, target };
  if (m && sub === "/config") return { text: "Changed settings", target };
  if (m && sub === "/owner") return { text: "Changed which client owns it", target };
  if (m && sub === "/master") return { text: "Changed the administrator code", target };
  if (m && !sub && method === "DELETE") return { text: "Removed it", target };
  if (m && !sub && method === "PUT") return { text: "Renamed it", target };
  if (path === "/api/codes" && method === "POST") return { text: `Made a "${String((body && body.type) || "?").slice(0, 20)}" code`, target: null };
  if (path === "/api/clients" && method === "POST") return { text: "Created a client account", target: null };
  if (/^\/api\/clients\//.test(path) && method === "DELETE") return { text: "Deleted a client account", target: null };
  if (path === "/api/logo") return { text: method === "DELETE" ? "Removed the logo" : "Changed the logo", target: null };
  if (path.startsWith("/api/2fa/")) return { text: "Changed two-step login", target: null };
  if (path === "/api/alerts") return { text: "Changed alert settings", target: null };
  if (path === "/api/templates" && method === "POST") return { text: "Saved a template", target: null };
  if (/^\/api\/templates\/[0-9a-f]+\/apply$/.test(path)) return { text: "Applied a template to " + ((body && Array.isArray(body.deviceIds) && body.deviceIds.length) || 0) + " phone(s)", target: null };
  if (/^\/api\/templates\/[0-9a-f]+$/.test(path) && method === "DELETE") return { text: "Deleted a template", target: null };
  if (path.startsWith("/api/apk")) return { text: "Uploaded an APK", target: null };
  return null;
}
