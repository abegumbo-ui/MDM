// Sign-in protection: a lockout after repeated wrong passwords, and optional two-step login with
// an authenticator app (the standard 6-digit code that changes every 30 seconds -- Google
// Authenticator, Authy, 1Password and so on all work).
//
// There is deliberately no back door. If the authenticator is lost, the way back in is to delete
// the "admin:totp" key from the STATE namespace in the Cloudflare dashboard, which only the
// Cloudflare account owner can do.

const enc = new TextEncoder();
const hex = (buf) => [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
const sha256 = async (s) => hex(await crypto.subtle.digest("SHA-256", enc.encode(s)));

function safeEq(a, b) {
  if (a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}

/* ------------------------------ lockout ------------------------------ */

export const LOGIN_MAX_FAILS = 5;
export const LOGIN_LOCK_SECONDS = 15 * 60;
const FAIL_KEY_TTL = 60 * 60; // forget old wrong tries after an hour of quiet

export const clientIp = (request) => request.headers.get("cf-connecting-ip") || "unknown";

// Counted per address AND login name, so one person mistyping never locks anyone else out.
async function failKey(request, name) {
  return `loginfail:${await sha256(`${clientIp(request)}|${name || "admin"}`)}`;
}

/** Is this address currently locked out for this login name? */
export async function lockStatus(env, request, name, now = Date.now()) {
  const rec = await env.STATE.get(await failKey(request, name), "json");
  if (rec && rec.until && rec.until > now) return { locked: true, minutes: Math.max(1, Math.ceil((rec.until - now) / 60000)) };
  return { locked: false, minutes: 0 };
}

/** Counts one wrong attempt. `justLocked` is true only on the attempt that triggers the lockout. */
export async function recordFailure(env, request, name, now = Date.now()) {
  const key = await failKey(request, name);
  let rec = (await env.STATE.get(key, "json")) || { n: 0, until: 0 };
  if (rec.until && rec.until <= now) rec = { n: 0, until: 0 }; // an old lockout ran out: start fresh
  let justLocked = false;
  if (!(rec.until > now)) {
    rec.n += 1;
    if (rec.n >= LOGIN_MAX_FAILS) {
      rec.until = now + LOGIN_LOCK_SECONDS * 1000;
      justLocked = true;
    }
  }
  await env.STATE.put(key, JSON.stringify(rec), { expirationTtl: FAIL_KEY_TTL });
  const locked = !!(rec.until && rec.until > now);
  return { locked, justLocked, minutes: locked ? Math.max(1, Math.ceil((rec.until - now) / 60000)) : 0, left: Math.max(0, LOGIN_MAX_FAILS - rec.n) };
}

export async function clearFailures(env, request, name) {
  await env.STATE.delete(await failKey(request, name));
}

/* ------------------------------ authenticator codes (TOTP, RFC 6238) ------------------------------ */

const B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
export function b32encode(bytes) {
  let bits = 0, value = 0, out = "";
  for (const b of bytes) {
    value = (value << 8) | b;
    bits += 8;
    while (bits >= 5) {
      out += B32[(value >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }
  if (bits > 0) out += B32[(value << (5 - bits)) & 31];
  return out;
}
export function b32decode(str) {
  let bits = 0, value = 0;
  const out = [];
  for (const ch of str.replace(/=+$/, "").toUpperCase()) {
    const i = B32.indexOf(ch);
    if (i < 0) throw new Error("bad base32");
    value = (value << 5) | i;
    bits += 5;
    if (bits >= 8) {
      out.push((value >>> (bits - 8)) & 255);
      bits -= 8;
    }
  }
  return new Uint8Array(out);
}

export const newTotpSecret = () => b32encode(crypto.getRandomValues(new Uint8Array(20)));

export const totpUri = (secret, label, issuer) =>
  `otpauth://totp/${encodeURIComponent(issuer)}:${encodeURIComponent(label)}?secret=${secret}&issuer=${encodeURIComponent(issuer)}&algorithm=SHA1&digits=6&period=30`;

async function hotp(keyBytes, counter) {
  const buf = new ArrayBuffer(8);
  new DataView(buf).setBigUint64(0, BigInt(counter));
  const key = await crypto.subtle.importKey("raw", keyBytes, { name: "HMAC", hash: "SHA-1" }, false, ["sign"]);
  const mac = new Uint8Array(await crypto.subtle.sign("HMAC", key, buf));
  const o = mac[mac.length - 1] & 15;
  const n = ((mac[o] & 0x7f) << 24) | (mac[o + 1] << 16) | (mac[o + 2] << 8) | mac[o + 3];
  return String(n % 1000000).padStart(6, "0");
}

/** The code the authenticator app shows right now (used by tests). */
export async function totpNow(secret, now = Date.now()) {
  return hotp(b32decode(secret), Math.floor(now / 30000));
}

/**
 * Checks a code, allowing one 30-second step either side for clock drift. Returns the matching
 * time step (so the caller can refuse to accept the same code twice), or 0 if it doesn't match
 * or is no newer than `lastStep`.
 */
export async function totpVerify(secret, code, lastStep = 0, now = Date.now()) {
  const clean = String(code || "").replace(/\s+/g, "");
  if (!/^\d{6}$/.test(clean)) return 0;
  const key = b32decode(secret);
  const current = Math.floor(now / 30000);
  for (const delta of [0, -1, 1]) {
    const step = current + delta;
    if (step > lastStep && safeEq(await hotp(key, step), clean)) return step;
  }
  return 0;
}
