// Self sign-up for people who want their own portal (their own dashboard for their own phones).
//
// Safety rules, all enforced here on the server:
//  * Off until the main administrator switches it on (Settings > Sign-ups). While it is off /signup does not exist.
//  * A person who signs up is only a PENDING request: it is not an account, cannot log in, and sees nothing, until the main
//    administrator approves it. Only the main administrator login (not another parent's login, not a client) can list, approve or deny.
//  * Every submission counts against the sender's address (5 an hour, then 15 minutes locked out), the queue holds at most 30
//    requests, requests expire after 14 days, and a hidden field catches simple bots. (Cloudflare's free plan only allows
//    about 1,000 saves a day, so spam must never be able to reach it: on the backup copy /signup sends people to the Pi.)
//  * Passwords are stored exactly like client accounts' (salted, slow hash) and must be 10 characters or more.
//  * Nothing tells a stranger whether a login name already exists.

import { DEFAULT_SIGNUP_PERMS, normalizePerms } from "./perms.js";

export const SIGNUP_SETTINGS_KEY = "signupsettings";
const PENDING_PREFIX = "signup:";
const MAX_PENDING = 30;
const PENDING_TTL_SECONDS = 14 * 24 * 3600;
const RESERVED = new Set(["admin", "administrator", "root", "system", "support", "helper", "client", "signup", "login", "logout", "api", "agent", "browser", "windows", "null", "undefined"]);

const esc = (s) => String(s == null ? "" : s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

export async function getSignupSettings(env) {
  const s = (await env.STATE.get(SIGNUP_SETTINGS_KEY, "json")) || {};
  return { open: s.open === true };
}

export function signupPage(message, values = {}, done = false) {
  const v = (k) => esc(values[k] || "");
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Sign up</title>
<style>:root{--bg:#F6F7F9;--card:#fff;--ink:#1B1F27;--mute:#5A6270;--line:#D8DCE3;--primary:#4F46E5;--err:#B3261E}
@media(prefers-color-scheme:dark){:root{--bg:#0F1115;--card:#171A21;--ink:#E7E9EE;--mute:#A0A7B4;--line:#2B303B;--primary:#8B85FF;--err:#FF8A80}}
body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.45 Inter,system-ui,sans-serif;display:flex;justify-content:center;padding:24px 16px}
main{width:100%;max-width:420px;background:var(--card);border:1px solid var(--line);border-radius:16px;padding:24px}
h1{font-size:22px;margin:0 0 6px}p{color:var(--mute);margin:0 0 16px}label{display:block;font-size:13px;font-weight:600;margin:14px 0 4px}
input{width:100%;box-sizing:border-box;padding:11px 12px;border:1px solid var(--line);border-radius:10px;background:transparent;color:inherit;font:inherit}
button{margin-top:20px;width:100%;padding:12px;border:0;border-radius:10px;background:var(--primary);color:#fff;font:600 16px Inter,system-ui,sans-serif;cursor:pointer}
.err{color:var(--err);font-weight:600;margin:0 0 12px}.hp{position:absolute;left:-9999px;height:0;overflow:hidden}small{color:var(--mute)}</style></head>
<body><main>${done
    ? `<h1>Thank you</h1><p>Your request was sent. You can sign in once the administrator approves it. There is nothing more to do now.</p>`
    : `<h1>Get your own portal</h1><p>Fill this in. The administrator looks at it and approves it, and then you can sign in and manage your own phones.</p>
${message ? `<p class="err">${esc(message)}</p>` : ""}
<form method="post" action="/signup" autocomplete="off">
<label for="name">Your name</label><input id="name" name="name" maxlength="60" required value="${v("name")}">
<label for="loginName">A login name</label><input id="loginName" name="loginName" maxlength="32" required pattern="[a-z0-9][a-z0-9_\\-]{2,31}" value="${v("loginName")}"><small>3 to 32 lowercase letters, numbers, - or _.</small>
<label for="email">Email or phone (so the administrator can reach you)</label><input id="email" name="email" maxlength="120" value="${v("email")}">
<label for="password">A password (10 characters or more)</label><input id="password" name="password" type="password" minlength="10" maxlength="200" required autocomplete="new-password">
<label for="password2">The same password again</label><input id="password2" name="password2" type="password" minlength="10" maxlength="200" required autocomplete="new-password">
<div class="hp" aria-hidden="true"><label for="website">Leave this empty</label><input id="website" name="website" tabindex="-1" autocomplete="off"></div>
<button type="submit">Send my request</button></form>`}</main></body></html>`;
}

/**
 * The public part: GET /signup (the form) and POST /signup (a request). Returns a Response, or null when the path is not ours.
 * deps: { html, getJSON, putJSON, hashClientPassword, LOGIN_NAME_RE, lockStatus, recordFailure, auditAdd, notify }
 */
export async function handleSignupPublic(request, env, url, deps) {
  if (url.pathname !== "/signup") return null;
  const { html } = deps;
  const settings = await getSignupSettings(env);
  if (!settings.open) return html("Not found", 404);
  // The backup copy never takes sign-ups while the Raspberry Pi is up: its free plan has a small daily allowance of saves.
  if (env.ROLE === "backup" && env.PRIMARY_URL && !(await env.STATE.get("repl:outage"))) {
    return new Response(null, { status: 302, headers: { location: String(env.PRIMARY_URL).replace(/\/+$/, "") + "/signup" } });
  }
  if (request.method === "GET") return html(signupPage());
  if (request.method !== "POST") return html("Method not allowed", 405);

  const lock = await deps.lockStatus(env, request, "signup");
  if (lock.locked) return html(signupPage(`Too many tries from this address. Please try again in ${lock.minutes} minute${lock.minutes === 1 ? "" : "s"}.`), 429);
  await deps.recordFailure(env, request, "signup"); // every submission counts, good or bad

  let form;
  try {
    form = await request.formData();
  } catch {
    return html(signupPage("Please fill in the form."), 400);
  }
  if (String(form.get("website") || "").trim()) return html(signupPage("", {}, true)); // a bot: act as if it worked, save nothing
  const values = { name: String(form.get("name") || "").trim().slice(0, 60), loginName: String(form.get("loginName") || "").trim().toLowerCase(), email: String(form.get("email") || "").trim().slice(0, 120) };
  const password = String(form.get("password") || "");
  const again = String(form.get("password2") || "");
  const bad = (m) => html(signupPage(m, values), 400);
  if (values.name.length < 2) return bad("Please write your name.");
  if (!deps.LOGIN_NAME_RE.test(values.loginName) || RESERVED.has(values.loginName)) return bad("That login name is not available. Use 3 to 32 lowercase letters, numbers, - or _.");
  if (password.length < 10) return bad("The password needs at least 10 characters.");
  if (password.length > 200) return bad("That password is too long.");
  if (password !== again) return bad("The two passwords are not the same.");
  const pending = (await env.STATE.list({ prefix: PENDING_PREFIX })).keys.length;
  if (pending >= MAX_PENDING) return html(signupPage("Sign-ups are full right now. Please try again later."), 503);
  const taken = (await deps.getJSON(env, `client:${values.loginName}`, null)) || (await deps.getJSON(env, `helper:${values.loginName}`, null)) || (await deps.getJSON(env, PENDING_PREFIX + values.loginName, null));
  if (taken) return bad("That login name is not available. Try another.");
  const rec = { id: values.loginName, name: values.name, email: values.email, createdAt: Date.now(), ...(await deps.hashClientPassword(password)) };
  await deps.putJSON(env, PENDING_PREFIX + values.loginName, rec, { expirationTtl: PENDING_TTL_SECONDS });
  await deps.auditAdd(env, { who: "Sign-up", text: `New sign-up request from ${values.name} (login name ${values.loginName}), waiting for approval` });
  if (deps.notify) await deps.notify("New sign-up request", `${values.name} asked for their own portal. Approve or deny it in Settings, Sign-ups.`);
  return html(signupPage("", {}, true));
}

/** What the administrator's screen sees of a request (never the password hash). */
const publicPending = (r) => ({ id: r.id, name: r.name, email: r.email || "", createdAt: r.createdAt });

/**
 * The administrator's part: /api/signups... Returns a Response, or null when the path is not ours.
 * Only the MAIN administrator login: not another parent's login (helper), not a client.
 * deps: { json, getJSON, putJSON, auditAdd, invalidateClients, isMainAdmin }
 */
export async function handleSignupAdmin(path, method, body, env, session, deps) {
  if (!path.startsWith("/api/signups")) return null;
  const { json } = deps;
  if (!deps.isMainAdmin(session)) return json({ error: "Only the main administrator can do this." }, 403);
  if (path === "/api/signups" && method === "GET") {
    const out = [];
    for (const k of (await env.STATE.list({ prefix: PENDING_PREFIX })).keys) {
      const r = await deps.getJSON(env, k.name, null);
      if (r) out.push(publicPending(r));
    }
    out.sort((a, b) => a.createdAt - b.createdAt);
    return json({ open: (await getSignupSettings(env)).open, pending: out });
  }
  if (path === "/api/signups/settings" && method === "PUT") {
    const open = !!(body && body.open === true);
    await deps.putJSON(env, SIGNUP_SETTINGS_KEY, { open });
    await deps.auditAdd(env, { who: "Administrator", text: open ? "Opened the sign-up page" : "Closed the sign-up page" });
    return json({ open });
  }
  const m = /^\/api\/signups\/([a-z0-9][a-z0-9_-]{2,31})(\/approve)?$/.exec(path);
  if (!m) return json({ error: "Not found" }, 404);
  const key = PENDING_PREFIX + m[1];
  const rec = await deps.getJSON(env, key, null);
  if (!rec) return json({ error: "Not found" }, 404);
  if (m[2] && method === "POST") {
    // Becomes an ordinary client account (their own dashboard for their own phones), with the password they chose.
    if ((await deps.getJSON(env, `client:${rec.id}`, null)) || (await deps.getJSON(env, `helper:${rec.id}`, null))) return json({ error: "That login name has been taken since." }, 409);
    // What they may control: what the administrator ticks when approving, otherwise the ready-made choice (everything except locks and erasing).
    const perms = body && Array.isArray(body.perms) ? normalizePerms(body.perms) : [...DEFAULT_SIGNUP_PERMS];
    const client = { id: rec.id, name: rec.name, email: rec.email || "", createdAt: Date.now(), approvedAt: Date.now(), via: "signup", perms, salt: rec.salt, hash: rec.hash, iterations: rec.iterations };
    await deps.putJSON(env, `client:${rec.id}`, client);
    await env.STATE.delete(key);
    if (deps.invalidateClients) deps.invalidateClients(`client:${rec.id}`);
    await deps.auditAdd(env, { who: "Administrator", text: `Approved the sign-up of ${rec.name} (login name ${rec.id})` });
    return json({ ok: true, id: rec.id });
  }
  if (!m[2] && method === "DELETE") {
    await env.STATE.delete(key);
    await deps.auditAdd(env, { who: "Administrator", text: `Denied the sign-up of ${rec.name} (login name ${rec.id})` });
    return json({ ok: true });
  }
  return json({ error: "Not found" }, 404);
}
