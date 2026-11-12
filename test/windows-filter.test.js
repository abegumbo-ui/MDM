import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

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
const req = (path, init) => worker.fetch(new Request("https://mdm.example.dev" + path, init), env);
const J = { "content-type": "application/json" };

test("a new PC gets the web filter: Moderate, SafeSearch, YouTube, no incognito, other browsers and VPNs closed", async () => {
  const { token } = await (await req("/windows/register", { method: "POST", headers: J, body: JSON.stringify({ info: { hostname: "PC1" } }) })).json();
  const sync = async (body = {}) => (await req("/windows/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${token}` }, body: JSON.stringify(body) })).json();
  const r = await sync({ info: { hostname: "PC1", tz: 0 } });
  assert.equal(r.filter.on, true);
  assert.equal(r.filter.level, "moderate");
  assert.equal(r.filter.safeSearch, true);
  assert.equal(r.filter.youtube, "moderate");
  assert.equal(r.filter.noIncognito, true);
  assert.equal(r.filter.blockOtherBrowsers, true);
  assert.equal(r.filter.blockVpnApps, true);
  assert.equal(r.filter.blockExtensions, false);
  assert.equal(r.protect, true, "new computers are protected from administrators");
  assert.ok(r.filter.essential.includes("mdm.example.dev"), "the dashboard itself is never blocked");
  assert.match(r.filter.base, /releases\/download\/blocklists/);
  assert.equal(r.enabled, false, "the old program lockdown stays off");

  const login = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  const cookie = login.headers.get("set-cookie").split(";")[0];
  const pc = (await (await req("/api/windevices", { headers: { cookie } })).json())[0];
  const put = (body) => req(`/api/windevices/${pc.id}/config`, { method: "PUT", headers: { cookie, ...J }, body: JSON.stringify(body) });

  // "Approved sites only" with allowed sites, words, blocked programs; the lockdown is untouched by a filter save.
  await put({ enabled: true, allowedPrograms: ["C:\\a.exe"] });
  await put({ config: { ...pc.config, browserFilter: "off", dnsAllowlist: ["chabad.org"], blockedWords: ["Casino"] }, win: { blockedPrograms: ["Steam.exe", "bad/name"], blockExtensions: true } });
  const r2 = await sync();
  assert.equal(r2.filter.level, "approved");
  assert.deepEqual(r2.filter.allow, ["chabad.org"]);
  assert.deepEqual(r2.filter.words, ["casino"]);
  assert.deepEqual(r2.filter.blockedPrograms, ["steam.exe"]);
  assert.equal(r2.filter.blockExtensions, true);
  assert.equal(r2.filter.startPage, "https://mdm.example.dev/start?sites=chabad.org", "approved-only opens on a page of the allowed sites");
  const page = await (await req("/start?sites=mail.google.com,chabad.org,<bad>")).text();
  assert.match(page, />Gmail<span>mail\.google\.com/);
  assert.match(page, /chabad\.org/);
  assert.ok(!page.includes("<bad>"));
  assert.equal(r2.enabled, true);
  assert.deepEqual(r2.allowedPrograms, ["C:\\a.exe"]);

  // A schedule in the computer's own time zone.
  await put({ config: { ...pc.config, browserFilter: "moderate", filterSchedule: { on: true, level: "strict", days: [0, 1, 2, 3, 4, 5, 6], from: "00:00", to: "00:00" } } });
  assert.equal((await sync({ info: { tz: -300 } })).filter.level, "strict");

  // Sites visited and the computer's log arrive on the dashboard.
  await sync({ trafficLog: [{ kind: "dns", host: "www.wikipedia.org", time: Date.now(), blocked: false }, { kind: "dns", host: "bet365.com", time: Date.now(), blocked: true }], events: [{ k: "security", m: "Closed a blocked program: tor.exe", at: 1 }] });
  const after = (await (await req("/api/windevices", { headers: { cookie } })).json())[0];
  const day = Object.keys(after.siteHistory)[0];
  assert.deepEqual(after.siteHistory[day]["bet365.com"], [1, 1, ""]);
  assert.equal(after.events[0].m, "Closed a blocked program: tor.exe");

  // A computer that can update itself is told about a newer LockGuard; an up-to-date one is not.
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (u) => String(u).startsWith("https://api.github.com/")
    ? new Response(JSON.stringify([{ tag_name: "latest", assets: [] }, { tag_name: "lockguard-v40", assets: [{ name: "LockGuard-Setup.exe", browser_download_url: "https://github.com/x/releases/download/lockguard-v40/LockGuard-Setup.exe", digest: "sha256:" + "ab".repeat(32) }] }]))
    : new Response("not found", { status: 404 }); // no lockguard.json pointer here: the API is the fallback, and nothing real is fetched
  try {
    const u1 = await sync({ info: { build: 33 } });
    assert.deepEqual(u1.update, { build: 40, url: "https://github.com/x/releases/download/lockguard-v40/LockGuard-Setup.exe", sha256: "ab".repeat(32) });
    assert.equal((await sync({ info: { build: 40 } })).update, undefined);
    assert.equal((await sync({ info: {} })).update, undefined, "a computer built locally never updates");
  } finally {
    globalThis.fetch = realFetch;
  }

  // Filter off: the computer is told to undo it.
  await put({ filterOn: false });
  assert.equal((await sync()).filter, null);
});

test("a PC that was connected before the web filter existed keeps working as it did until the filter is switched on", async () => {
  kv.set("winDevice:abcdef0123456789", JSON.stringify({ id: "abcdef0123456789", name: "Old PC", tokenHash: "x", enabled: true, allowedPrograms: [], queue: [] }));
  const login = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  const cookie = login.headers.get("set-cookie").split(";")[0];
  const old = (await (await req("/api/windevices", { headers: { cookie } })).json()).find((w) => w.id === "abcdef0123456789");
  assert.equal(old.filterOn, false);
  assert.equal(old.enabled, true);
});

test("the PC reports its programs; each can be set to No internet or Blocked from the dashboard", async () => {
  const { token } = await (await req("/windows/register", { method: "POST", headers: J, body: JSON.stringify({ info: { hostname: "PC9" } }) })).json();
  const sync = async (body = {}) => (await req("/windows/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${token}` }, body: JSON.stringify(body) })).json();
  await sync({ info: { hostname: "PC9" }, programs: [{ name: "Steam", path: "C:\\Program Files\\Steam\\steam.exe" }, { name: "Not a program", path: "C:\\x.txt" }] });
  const login = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  const cookie = login.headers.get("set-cookie").split(";")[0];
  const pc = (await (await req("/api/windevices", { headers: { cookie } })).json()).find((x) => x.info.hostname === "PC9");
  assert.deepEqual(pc.programs.map((p) => p.name), ["Steam"], "only real programs are listed");
  await req(`/api/windevices/${pc.id}/config`, { method: "PUT", headers: { cookie, ...J }, body: JSON.stringify({ win: { programRules: { "C:\\Program Files\\Steam\\steam.exe": "blocked", "C:\\Games\\game.exe": "noInternet", "C:\\bad.txt": "blocked" } } }) });
  const r = await sync();
  assert.ok(r.filter.blockedPrograms.includes("steam.exe"), "blocked programs are closed by file name");
  assert.deepEqual(r.filter.noInternetPrograms, ["c:\\games\\game.exe"], "no-internet programs by full path");
});

test("removal code made on the dashboard, and the unlock file, reach the computer", async () => {
  const { token } = await (await req("/windows/register", { method: "POST", headers: J, body: JSON.stringify({ info: { hostname: "PC7" } }) })).json();
  const sync = async (body = {}) => (await req("/windows/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${token}` }, body: JSON.stringify(body) })).json();
  await sync({ info: { hostname: "PC7" } });
  const login = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  const cookie = login.headers.get("set-cookie").split(";")[0];
  const pc = (await (await req("/api/windevices", { headers: { cookie } })).json()).find((x) => x.info.hostname === "PC7");
  assert.equal((await sync()).removalCode, null);
  const { code, until } = await (await req(`/api/windevices/${pc.id}/removal-code`, { method: "POST", headers: { cookie, ...J }, body: "{}" })).json();
  assert.match(code, /^\d{8}$/);
  const r = await sync();
  const { createHash } = await import("node:crypto");
  assert.equal(r.removalCode.hash, createHash("sha256").update(code).digest("hex"), "only the fingerprint goes to the computer");
  assert.equal(r.removalCode.until, until);
  await req(`/api/windevices/${pc.id}/config`, { method: "PUT", headers: { cookie, ...J }, body: JSON.stringify({ win: { unlockName: "off.txt", unlockText: "s3cret" } }) });
  assert.deepEqual((await sync()).unlock, { name: "off.txt", text: "s3cret" });
  await req(`/api/windevices/${pc.id}/config`, { method: "PUT", headers: { cookie, ...J }, body: JSON.stringify({ win: { unlockName: "../evil", unlockText: "x" } }) });
  assert.equal((await sync()).unlock, null, "a file name with a path in it is refused");
});

test("turning the internet off for a while reaches the computer", async () => {
  const { token } = await (await req("/windows/register", { method: "POST", headers: J, body: JSON.stringify({ info: { hostname: "PC5" } }) })).json();
  const sync = async (body = {}) => (await req("/windows/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${token}` }, body: JSON.stringify(body) })).json();
  await sync({ info: { hostname: "PC5" } });
  const login = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  const cookie = login.headers.get("set-cookie").split(";")[0];
  const pc = (await (await req("/api/windevices", { headers: { cookie } })).json()).find((x) => x.info.hostname === "PC5");
  const until = Date.now() + 3600000;
  await req(`/api/windevices/${pc.id}/config`, { method: "PUT", headers: { cookie, ...J }, body: JSON.stringify({ config: { ...pc.config, internetOffUntil: until } }) });
  assert.equal((await sync()).filter.internetOffUntil, until);
});

test("websites asked for on the computer reach the dashboard once, and can be dismissed", async () => {
  const { token } = await (await req("/windows/register", { method: "POST", headers: J, body: JSON.stringify({ info: { hostname: "PC3" } }) })).json();
  const sync = async (body = {}) => (await req("/windows/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${token}` }, body: JSON.stringify(body) })).json();
  await sync({ info: { hostname: "PC3" }, siteRequests: [{ url: "school.org" }, { url: "school.org" }, { url: "" }] });
  await sync({ siteRequests: [{ url: "school.org" }] });
  const login = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  const cookie = login.headers.get("set-cookie").split(";")[0];
  let pc = (await (await req("/api/windevices", { headers: { cookie } })).json()).find((x) => x.info.hostname === "PC3");
  assert.deepEqual(pc.siteRequests.map((r) => r.url), ["school.org"]);
  await req(`/api/windevices/${pc.id}/site-requests?url=school.org`, { method: "DELETE", headers: { cookie } });
  pc = (await (await req("/api/windevices", { headers: { cookie } })).json()).find((x) => x.info.hostname === "PC3");
  assert.equal(pc.siteRequests.length, 0);
});
