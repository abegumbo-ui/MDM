import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { b32decode, b32encode, newTotpSecret, totpNow, totpVerify } from "../src/security.js";
import { checkDevices, describeChange, mergeAlerts, validWebhook, DEFAULT_ALERTS } from "../src/alerts.js";

const kv = new Map();
const env = {
  ADMIN_PASSWORD: "correct horse",
  STATE: {
    get: async (k, t) => (kv.has(k) ? (t === "json" ? JSON.parse(kv.get(k)) : kv.get(k)) : null),
    put: async (k, v) => void kv.set(k, v),
    delete: async (k) => void kv.delete(k),
    list: async ({ prefix }) => ({ keys: [...kv.keys()].filter((k) => k.startsWith(prefix)).map((name) => ({ name })), list_complete: true }),
  },
};
const req = (path, init) => worker.fetch(new Request("https://x.test" + path, init), env);
const loginAs = (fields, ip = "1.1.1.1") =>
  req("/login", { method: "POST", headers: { "cf-connecting-ip": ip }, body: new URLSearchParams(fields) });
const jpost = (path, body, cookie) => req(path, { method: "POST", headers: { "content-type": "application/json", cookie }, body: JSON.stringify(body) });
const jput = (path, body, cookie) => req(path, { method: "PUT", headers: { "content-type": "application/json", cookie }, body: JSON.stringify(body) });

test("base32 round-trips and a known TOTP vector matches RFC 6238", async () => {
  const bytes = new Uint8Array([1, 2, 3, 250, 251, 252, 0, 9, 99]);
  assert.deepEqual([...b32decode(b32encode(bytes))], [...bytes]);
  // RFC 6238 appendix B: secret "12345678901234567890", T=59s -> 94287082 (8 digits); last 6 digits are 287082.
  const secret = b32encode(new TextEncoder().encode("12345678901234567890"));
  assert.equal(await totpNow(secret, 59000), "287082");
  assert.ok(await totpVerify(secret, "287082", 0, 59000));
  assert.equal(await totpVerify(secret, "000000", 0, 59000), 0);
});

test("a TOTP code cannot be used twice", async () => {
  const secret = newTotpSecret();
  const now = Date.now();
  const code = await totpNow(secret, now);
  const step = await totpVerify(secret, code, 0, now);
  assert.ok(step);
  assert.equal(await totpVerify(secret, code, step, now), 0);
});

test("five wrong passwords lock that address out, even for the right password", async () => {
  for (let i = 0; i < 4; i++) assert.equal((await loginAs({ password: "nope" }, "9.9.9.9")).status, 401);
  const fifth = await loginAs({ password: "nope" }, "9.9.9.9");
  assert.equal(fifth.status, 429);
  assert.match(await fifth.text(), /Too many wrong tries/);
  assert.equal((await loginAs({ password: env.ADMIN_PASSWORD }, "9.9.9.9")).status, 429, "locked even with the right password");
  assert.equal((await loginAs({ password: env.ADMIN_PASSWORD }, "8.8.8.8")).status, 303, "someone else is not affected");
});

test("a right password resets the wrong-try count", async () => {
  for (let i = 0; i < 3; i++) await loginAs({ password: "nope" }, "7.7.7.7");
  assert.equal((await loginAs({ password: env.ADMIN_PASSWORD }, "7.7.7.7")).status, 303);
  for (let i = 0; i < 3; i++) assert.equal((await loginAs({ password: "nope" }, "7.7.7.7")).status, 401);
});

test("two-step login: set up, required at sign-in, replay refused, turn off", async () => {
  const ok = await loginAs({ password: env.ADMIN_PASSWORD }, "2.2.2.2");
  const cookie = ok.headers.get("set-cookie").split(";")[0];
  assert.deepEqual(await (await req("/api/2fa", { headers: { cookie } })).json(), { enabled: false });

  const { secret } = await (await jpost("/api/2fa/start", {}, cookie)).json();
  assert.ok(secret);
  assert.equal((await jpost("/api/2fa/enable", { code: "000000" }, cookie)).status, 400, "wrong code does not turn it on");
  assert.equal((await jpost("/api/2fa/enable", { code: await totpNow(secret) }, cookie)).status, 200);
  assert.deepEqual(await (await req("/api/2fa", { headers: { cookie } })).json(), { enabled: true });

  // Sign-in now needs the code.
  const noCode = await loginAs({ password: env.ADMIN_PASSWORD }, "3.3.3.3");
  assert.equal(noCode.status, 401);
  assert.match(await noCode.text(), /6-digit code/);
  const wrong = await loginAs({ password: env.ADMIN_PASSWORD, code: "123456" }, "3.3.3.3");
  assert.equal(wrong.status, 401);
  // The code used to enable it is spent, so wait-free reuse is refused; a code from the next window works.
  const next = await totpNow(secret, Date.now() + 30000);
  const good = await loginAs({ password: env.ADMIN_PASSWORD, code: next }, "3.3.3.3");
  assert.equal(good.status, 303);
  const replay = await loginAs({ password: env.ADMIN_PASSWORD, code: next }, "4.4.4.4");
  assert.equal(replay.status, 401, "the same code cannot sign in twice");

  // Turning it off needs a current code too.
  assert.equal((await jpost("/api/2fa/disable", { code: "000000" }, cookie)).status, 400);
  // Move the clock forward two minutes so there is a fresh, unspent code to use.
  const realNow = Date.now;
  Date.now = () => realNow() + 120000;
  try {
    const later = await totpNow(secret, Date.now());
    assert.equal((await jpost("/api/2fa/disable", { code: later }, cookie)).status, 200);
  } finally {
    Date.now = realNow;
  }
  assert.equal((await loginAs({ password: env.ADMIN_PASSWORD }, "5.5.5.5")).status, 303);
});

test("clients cannot reach the admin-only security pages", async () => {
  const admin = (await loginAs({ password: env.ADMIN_PASSWORD }, "6.6.6.6")).headers.get("set-cookie").split(";")[0];
  await jpost("/api/clients", { name: "Kid", loginName: "kid", password: "kid-password-1" }, admin);
  const kid = (await loginAs({ loginName: "kid", password: "kid-password-1" }, "6.6.6.7")).headers.get("set-cookie").split(";")[0];
  for (const p of ["/api/audit", "/api/2fa", "/api/alerts"]) assert.equal((await req(p, { headers: { cookie: kid } })).status, 403, p);
});

test("the change history records actions, never their contents", async () => {
  const cookie = (await loginAs({ password: env.ADMIN_PASSWORD }, "10.0.0.1")).headers.get("set-cookie").split(";")[0];
  await jpost("/api/codes", { type: "enroll" }, cookie);
  const { entries } = await (await req("/api/audit", { headers: { cookie } })).json();
  assert.ok(entries.some((e) => e.text.includes("Signed in")));
  assert.ok(entries.some((e) => e.text.includes('"enroll" code')));
  assert.ok(!JSON.stringify(entries).includes("correct horse"));
  assert.equal(describeChange("POST", "/api/devices/abc123/command", { type: "setPin", args: { pin: "1234" } }).text, 'Sent the "setPin" command');
  assert.ok(!JSON.stringify(describeChange("POST", "/api/devices/abc123/command", { type: "setPin", args: { pin: "1234" } })).includes("1234"));
});

test("alerts settings validate the address and hide it from the page", async () => {
  assert.ok(validWebhook("https://ntfy.sh/my-topic"));
  assert.ok(!validWebhook("http://ntfy.sh/my-topic"));
  assert.ok(!validWebhook("https://user:pw@example.com/x"));
  assert.ok(!validWebhook("javascript:alert(1)"));
  assert.throws(() => mergeAlerts(DEFAULT_ALERTS, { url: "ftp://x.y/z" }));
  assert.equal(mergeAlerts(DEFAULT_ALERTS, { offlineHours: 9999 }).offlineHours, 72);

  const cookie = (await loginAs({ password: env.ADMIN_PASSWORD }, "10.0.0.2")).headers.get("set-cookie").split(";")[0];
  assert.equal((await jput("/api/alerts", { url: "http://insecure.example/x" }, cookie)).status, 400);
  const saved = await (await jput("/api/alerts", { url: "https://ntfy.sh/secret-topic-abc", offlineHours: 5 }, cookie)).json();
  assert.equal(saved.urlSet, true);
  assert.equal(saved.url, undefined, "the address itself is never sent back");
  assert.ok(!JSON.stringify(saved).includes("secret-topic"));
  const kept = await (await jput("/api/alerts", { offlineHours: 6 }, cookie)).json();
  assert.equal(kept.urlSet, true, "leaving the address out keeps it");
  const cleared = await (await jput("/api/alerts", { url: "" }, cookie)).json();
  assert.equal(cleared.urlSet, false);
});

test("offline and low-battery alerts fire once, and again only after recovering", async () => {
  const sent = [];
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (url, init) => {
    sent.push({ url: String(url), body: String(init && init.body), headers: init && init.headers });
    return new Response("ok");
  };
  try {
    await env.STATE.put("alerts", JSON.stringify({ ...DEFAULT_ALERTS, url: "https://ntfy.sh/topic-x" }));
    const now = 1_000_000_000_000;
    const phone = { id: "p1", name: "Kid's phone", lastSeen: now - 5 * 3600 * 1000, info: { battery: { pct: 8, charging: false } } };
    let r = await checkDevices(env, [phone], now);
    assert.equal(r.sent, 2, "offline + low battery");
    assert.ok(sent.every((s) => s.url === "https://ntfy.sh/topic-x"));
    r = await checkDevices(env, [phone], now + 900000);
    assert.equal(r.sent, 0, "no repeat while it stays that way");
    const back = { ...phone, lastSeen: now + 1_000_000, info: { battery: { pct: 50, charging: true } } };
    await checkDevices(env, [back], now + 1_100_000);
    r = await checkDevices(env, [{ ...phone, lastSeen: now + 1_000_000 - 20 * 3600 * 1000 }], now + 1_200_000 + 3600 * 1000 * 6);
    assert.ok(r.sent >= 1, "alerts again after it recovered and failed again");
  } finally {
    globalThis.fetch = realFetch;
  }
});

test("the install-to-home-screen files are public and valid", async () => {
  const m = await req("/manifest.webmanifest");
  assert.equal(m.status, 200);
  assert.equal((await m.json()).name, "MDM Dashboard");
  const icon = await req("/icon-192.png");
  assert.equal(icon.headers.get("content-type"), "image/png");
  assert.deepEqual([...new Uint8Array(await icon.arrayBuffer()).slice(0, 4)], [0x89, 0x50, 0x4e, 0x47]);
});
