import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

const kv = new Map();
let getCalls = 0;
const env = {
  ADMIN_PASSWORD: "correct horse",
  STATE: {
    get: async (k, t) => {
      getCalls++;
      return kv.has(k) ? (t === "json" ? JSON.parse(kv.get(k)) : kv.get(k)) : null; // arrayBuffer values are stored as-is
    },
    put: async (k, v) => void kv.set(k, v),
    delete: async (k) => void kv.delete(k),
    list: async ({ prefix }) => ({ keys: [...kv.keys()].filter((k) => k.startsWith(prefix)).map((name) => ({ name })), list_complete: true }),
  },
};
const req = (path, init) => worker.fetch(new Request("https://x.test" + path, init), env);
const post = (path, body, headers = {}) =>
  req(path, { method: "POST", headers: { "content-type": "application/json", ...headers }, body: JSON.stringify(body) });

async function login() {
  const ok = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  assert.equal(ok.status, 303);
  return ok.headers.get("set-cookie").split(";")[0];
}

test("unauthenticated requests are rejected", async () => {
  assert.equal((await req("/api/state")).status, 401);
  assert.match(await (await req("/")).text(), /Sign in/);
  assert.equal((await post("/agent/sync", {})).status, 401);
});

test("wrong password fails, forged cookie fails, right password works", async () => {
  const bad = await req("/login", { method: "POST", body: new URLSearchParams({ password: "nope" }) });
  assert.equal(bad.status, 401);
  const forged = await req("/", { headers: { cookie: "sess=9999999999.deadbeef" } });
  assert.match(await forged.text(), /Sign in/);
  const cookie = await login();
  assert.match(await (await req("/", { headers: { cookie } })).text(), /MDM Dashboard/);
});

test("enroll, sync, apply policy, queue a command", async () => {
  const cookie = await login();
  const { code } = await (await post("/api/codes", { type: "enroll" }, { cookie })).json();

  assert.equal((await post("/agent/enroll", { code: "ZZZZZZZZ" })).status, 403);
  const enrolled = await (await post("/agent/enroll", { code, info: { model: "S22 Flip", manufacturer: "CAT" } })).json();
  assert.ok(enrolled.token);
  assert.equal((await post("/agent/enroll", { code })).status, 403, "codes are single use");

  const auth = { authorization: `Bearer ${enrolled.token}` };
  assert.equal((await post("/agent/sync", {}, { authorization: "Bearer aa.bb" })).status, 401);

  const packages = [
    { p: "com.google.android.apps.maps", l: "Maps", s: true, h: false },
    { p: "com.android.chrome", l: "Chrome", s: true, h: false },
    { p: "com.android.systemui", l: "System UI", s: true, h: false },
  ];
  let sync = await (await post("/agent/sync", { packages, info: { deviceOwner: true } }, auth)).json();
  assert.deepEqual(sync.policy.hide, [], "nothing is hidden until you opt in");

  let devices = await (await req("/api/devices", { headers: { cookie } })).json();
  const devId = devices[0].id;
  await req(`/api/devices/${devId}/config`, { method: "PUT", headers: { cookie, "content-type": "application/json" }, body: JSON.stringify({ blockUnlisted: true, apps: { "com.google.android.apps.maps": { mode: "allow" } } }) });
  sync = await (await post("/agent/sync", { packages }, auth)).json();
  assert.deepEqual(sync.policy.hide, ["com.android.chrome"]);
  assert.ok(sync.policy.show.includes("com.google.android.apps.maps"));

  devices = await (await req("/api/devices", { headers: { cookie } })).json();
  assert.equal(devices.length, 1);
  assert.equal(devices[0].name, "CAT S22 Flip");
  assert.equal(devices[0].packages.length, 3);

  const cmd = await post(`/api/devices/${devices[0].id}/command`, { type: "lock" }, { cookie });
  assert.equal(cmd.status, 200);
  assert.equal((await post(`/api/devices/${devices[0].id}/command`, { type: "rm -rf" }, { cookie })).status, 400);
  sync = await (await post("/agent/sync", { results: [] }, auth)).json();
  assert.equal(sync.commands.length, 1);
  assert.equal(sync.commands[0].type, "lock");
  sync = await (await post("/agent/sync", {}, auth)).json();
  assert.equal(sync.commands.length, 0, "commands are delivered once");
});

async function enrolledDevice(cookie) {
  const { code } = await (await post("/api/codes", { type: "enroll" }, { cookie })).json();
  const { token } = await (await post("/agent/enroll", { code, info: { model: "X" } })).json();
  return { auth: { authorization: `Bearer ${token}` }, id: token.split(".")[0] };
}

test("listSystemApps: queued like any other command, its result (the phone's JSON list) round-trips to the dashboard", async () => {
  const cookie = await login();
  const { auth, id } = await enrolledDevice(cookie);

  const cmd = await post(`/api/devices/${id}/command`, { type: "listSystemApps" }, { cookie });
  assert.equal(cmd.status, 200);

  let sync = await (await post("/agent/sync", {}, auth)).json();
  assert.equal(sync.commands.length, 1);
  assert.equal(sync.commands[0].type, "listSystemApps");

  const found = JSON.stringify([{ p: "com.android.keyguard", l: "Lock screen", s: true, h: false }]);
  await post("/agent/sync", { results: [{ id: sync.commands[0].id, type: "listSystemApps", ok: true, msg: found }] }, auth);

  const devices = await (await req("/api/devices", { headers: { cookie } })).json();
  const device = devices.find((x) => x.id === id);
  const result = device.results.find((r) => r.type === "listSystemApps");
  assert.equal(result.ok, true);
  assert.deepEqual(JSON.parse(result.msg), [{ p: "com.android.keyguard", l: "Lock screen", s: true, h: false }]);
});

test("dashboard log: a config save that actually changes something is recorded, a no-op save isn't", async () => {
  const cookie = await login();
  const { id } = await enrolledDevice(cookie);
  const putConfig = (body) => req(`/api/devices/${id}/config`, { method: "PUT", headers: { cookie, "content-type": "application/json" }, body: JSON.stringify(body) });

  await putConfig({ restrictions: { cameraDisabled: true } });
  let devices = await (await req("/api/devices", { headers: { cookie } })).json();
  let device = devices.find((x) => x.id === id);
  assert.equal(device.pushLog.length, 1);
  assert.deepEqual(device.pushLog[0].changes, ["Disable the camera entirely: on"]);

  await putConfig({ restrictions: { cameraDisabled: true } }); // identical save
  devices = await (await req("/api/devices", { headers: { cookie } })).json();
  device = devices.find((x) => x.id === id);
  assert.equal(device.pushLog.length, 1, "a no-op save adds nothing to the log");
});

async function enrolledBrowser(cookie) {
  const { code } = await (await post("/api/codes", { type: "browser" }, { cookie })).json();
  const { token } = await (await post("/browser/enroll", { code, info: { model: "Y" } })).json();
  return { auth: { authorization: `Bearer ${token}` }, id: token.split(".")[0] };
}

test("standalone Browser: connects directly to the dashboard with its own code, no agent involved", async () => {
  const cookie = await login();
  await post("/browser/enroll", { code: "DEADBEEF" }).then((r) => assert.equal(r.status, 403));

  const { code } = await (await post("/api/codes", { type: "browser" }, { cookie })).json();
  assert.equal((await post("/agent/enroll", { code })).status, 403, "a browser code does not work as an agent enrollment code");
  const { token } = await (await post("/browser/enroll", { code, info: { model: "Pixel" } })).json();
  assert.ok(token);
  assert.equal((await post("/browser/enroll", { code })).status, 403, "one-time: the same code cannot be used twice");

  const auth = { authorization: `Bearer ${token}` };
  let browsers = await (await req("/api/browsers", { headers: { cookie } })).json();
  assert.equal(browsers.length, 1);
  assert.equal(browsers[0].info.model, "Pixel");
  await put(cookie, `/api/browsers/${browsers[0].id}/config`, { sites: { a: { type: "domain", url: "khanacademy.org" }, b: { type: "domain", url: "chromebooks.com" } } });
  const sync = await (await post("/browser/sync", {}, auth)).json();
  assert.equal(sync.sites.length, 2, "a standalone browser's sites are its own, set on its own page");
  browsers = await (await req("/api/browsers", { headers: { cookie } })).json();

  await post("/browser/sync", { siteRequests: [{ url: "https://bad.example/" }] }, auth);
  let list = await (await req("/api/browsers", { headers: { cookie } })).json();
  assert.deepEqual(list[0].siteRequests.map((r) => r.url), ["https://bad.example/"]);
  await req(`/api/browsers/${list[0].id}/site-requests?url=${encodeURIComponent("https://bad.example/")}`, { method: "DELETE", headers: { cookie } });
  list = await (await req("/api/browsers", { headers: { cookie } })).json();
  assert.deepEqual(list[0].siteRequests, []);

  await req(`/api/browsers/${list[0].id}`, { method: "DELETE", headers: { cookie } });
  assert.equal((await post("/browser/sync", {}, auth)).status, 401, "once removed, its old token is dead and it needs a fresh code");
});

test("standalone Browser: self-registers with no code at all, named so it's findable, and can be renamed", async () => {
  const cookie = await login();

  const { token } = await (await post("/browser/register", { info: { model: "X" } })).json();
  assert.ok(token);
  const auth = { authorization: `Bearer ${token}` };
  assert.equal((await post("/browser/sync", {}, auth)).status, 200, "the self-issued token works immediately, no admin step needed");

  const browsers = await (await req("/api/browsers", { headers: { cookie } })).json();
  assert.equal(browsers.length, 1);
  assert.match(browsers[0].name, /^New Browser \(/, "self-registered browsers are named so more than one stays tellable apart");

  await req(`/api/browsers/${browsers[0].id}`, { method: "PUT", headers: { cookie, "content-type": "application/json" }, body: JSON.stringify({ name: "Kid's phone" }) });
  const renamed = await (await req("/api/browsers", { headers: { cookie } })).json();
  assert.equal(renamed[0].name, "Kid's phone");
  assert.equal((await req(`/api/browsers/${browsers[0].id}`, { method: "PUT", headers: { cookie, "content-type": "application/json" }, body: JSON.stringify({ name: "" }) })).status, 400, "an empty name is rejected");
});

test("Windows LockGuard device: self-registers, takes dashboard-set policy, and self-uninstalls on command", async () => {
  const cookie = await login();

  const { token } = await (await post("/windows/register", { info: { hostname: "KIDS-LAPTOP" } })).json();
  assert.ok(token);
  const auth = { authorization: `Bearer ${token}` };

  let devices = await (await req("/api/windevices", { headers: { cookie } })).json();
  assert.equal(devices.length, 1);
  assert.match(devices[0].name, /^New LockGuard Device \(/);
  assert.equal(devices[0].enabled, false, "disabled until the admin turns it on");

  // Nothing allowed yet: a fresh registration starts locked out, same as a fresh Browser.
  let sync = await (await post("/windows/sync", { info: { hostname: "KIDS-LAPTOP" }, enabled: false, allowedPrograms: [] }, auth)).json();
  assert.equal(sync.enabled, false);
  assert.deepEqual(sync.allowedPrograms, []);

  await req(`/api/windevices/${devices[0].id}/config`, {
    method: "PUT",
    headers: { cookie, "content-type": "application/json" },
    body: JSON.stringify({ enabled: true, allowedPrograms: ["C:\\\\Program Files\\\\Outlook\\\\outlook.exe"] }),
  });
  sync = await (await post("/windows/sync", { info: { hostname: "KIDS-LAPTOP" }, enabled: false, allowedPrograms: [] }, auth)).json();
  assert.equal(sync.enabled, true, "the device picks up the admin's policy on its next sync");
  assert.deepEqual(sync.allowedPrograms, ["C:\\\\Program Files\\\\Outlook\\\\outlook.exe"]);

  devices = await (await req("/api/windevices", { headers: { cookie } })).json();
  assert.equal(devices[0].reportedEnabled, false, "what the device last reported, before it applied the new policy");

  await req(`/api/windevices/${devices[0].id}`, { method: "PUT", headers: { cookie, "content-type": "application/json" }, body: JSON.stringify({ name: "Kid's laptop" }) });
  devices = await (await req("/api/windevices", { headers: { cookie } })).json();
  assert.equal(devices[0].name, "Kid's laptop");

  await req(`/api/windevices/${devices[0].id}/command`, { method: "POST", headers: { cookie, "content-type": "application/json" }, body: JSON.stringify({ type: "uninstall" }) });
  sync = await (await post("/windows/sync", { info: { hostname: "KIDS-LAPTOP" }, enabled: true, allowedPrograms: [] }, auth)).json();
  assert.deepEqual(sync.commands.map((c) => c.type), ["uninstall"], "the queued uninstall command is delivered on the next sync");
  sync = await (await post("/windows/sync", { info: { hostname: "KIDS-LAPTOP" }, enabled: true, allowedPrograms: [] }, auth)).json();
  assert.deepEqual(sync.commands, [], "delivered once -- the queue is cleared, not retried");

  await req(`/api/windevices/${devices[0].id}`, { method: "DELETE", headers: { cookie } });
  assert.equal((await post("/windows/sync", {}, auth)).status, 401, "once removed, its old token is dead");
});

test("one-time install/uninstall codes: typed, single use, need a device token", async () => {
  const cookie = await login();
  const { auth } = await enrolledDevice(cookie);
  assert.equal((await post("/api/codes", { type: "bogus" }, { cookie })).status, 400);

  const install = (await (await post("/api/codes", { type: "install" }, { cookie })).json()).code;
  const uninstall = (await (await post("/api/codes", { type: "uninstall" }, { cookie })).json()).code;

  assert.equal((await post("/agent/redeem", { type: "install", code: install })).status, 401, "needs a device token");
  assert.equal((await post("/agent/redeem", { type: "uninstall", code: install }, auth)).status, 403, "codes are typed");
  assert.equal((await post("/agent/redeem", { type: "install", code: install }, auth)).status, 200);
  assert.equal((await post("/agent/redeem", { type: "install", code: install }, auth)).status, 403, "single use");
  assert.equal((await post("/agent/redeem", { type: "uninstall", code: uninstall }, auth)).status, 200);
});

test("freebrowse code: carries a chosen duration through to the redeem reply", async () => {
  const cookie = await login();
  const { auth } = await enrolledDevice(cookie);

  const def = await (await post("/api/codes", { type: "freebrowse" }, { cookie })).json();
  assert.equal(def.minutes, 60, "defaults to 60 minutes when none is given");

  const { code, minutes } = await (await post("/api/codes", { type: "freebrowse", minutes: 30 }, { cookie })).json();
  assert.equal(minutes, 30);
  const reply = await (await post("/agent/redeem", { type: "freebrowse", code }, auth)).json();
  assert.equal(reply.minutes, 30, "the redeem reply carries the same duration the code was made with");
  assert.equal((await post("/agent/redeem", { type: "freebrowse", code }, auth)).status, 403, "single use");

  const clamped = await (await post("/api/codes", { type: "freebrowse", minutes: 9999 }, { cookie })).json();
  assert.equal(clamped.minutes, 240, "an out-of-range duration is clamped, not rejected");
});

test("a code generated for one device cannot be redeemed by a different device", async () => {
  const cookie = await login();
  const { auth: authA, id: idA } = await enrolledDevice(cookie);
  const { auth: authB } = await enrolledDevice(cookie);

  const { code } = await (await post("/api/codes", { type: "install", deviceId: idA }, { cookie })).json();
  assert.equal((await post("/agent/redeem", { type: "install", code }, authB)).status, 403, "wrong device, even with a valid code");
  assert.equal((await post("/agent/redeem", { type: "install", code }, authA)).status, 200, "the right device still works");

  // Generated with no deviceId (e.g. from the global flow): any device can redeem it, as before.
  const { code: openCode } = await (await post("/api/codes", { type: "install" }, { cookie })).json();
  assert.equal((await post("/agent/redeem", { type: "install", code: openCode }, authB)).status, 200);
});

test("per-device config: a brand-new device always starts clean, never from another device's rules", async () => {
  const cookie = await login();
  // Even if an old global "config" key is still sitting in KV from before per-device config
  // existed, a brand-new device must never inherit it -- or any other device's rules, for that
  // matter. A rule set for one phone (e.g. because of something only relevant there) must not
  // show up as a starting default on an unrelated phone.
  await env.STATE.put("config", JSON.stringify({ sites: { a: { type: "domain", url: "oldsite.example" } }, approveNew: true }));

  const { auth: auth1, id: id1 } = await enrolledDevice(cookie);
  let sync = await (await post("/agent/sync", {}, auth1)).json();
  assert.equal(sync.policy.sites.length, 0, "a brand-new device starts with nothing, ignoring any leftover legacy global config");
  assert.equal(sync.policy.approveNew, false);

  await put(cookie, `/api/devices/${id1}/config`, { sites: { b: { type: "domain", url: "newsite.example" } } });
  const { auth: auth2 } = await enrolledDevice(cookie);
  const sync2 = await (await post("/agent/sync", {}, auth2)).json();
  assert.equal(sync2.policy.sites.length, 0, "device 2 starts clean too, not from device 1's edits");
  sync = await (await post("/agent/sync", {}, auth1)).json();
  assert.equal(sync.policy.sites[0].host, "newsite.example", "device 1 kept its own change");
  await env.STATE.delete("config"); // tests share one KV store; don't leak the legacy key into later tests
});

test("clone settings from one device (or browser) into another", async () => {
  const cookie = await login();
  const { id: source } = await enrolledDevice(cookie);
  await put(cookie, `/api/devices/${source}/config`, { sites: { a: { type: "domain", url: "khanacademy.org" } }, approveNew: true });

  const { auth: targetAuth, id: target } = await enrolledDevice(cookie);
  let sync = await (await post("/agent/sync", {}, targetAuth)).json();
  assert.equal(sync.policy.sites.length, 0, "a fresh device starts with nothing of its own");

  assert.equal((await post(`/api/devices/${target}/clone-from`, { sourceId: "deadbeef" }, { cookie })).status, 404);
  assert.equal((await post(`/api/devices/${target}/clone-from`, { sourceId: source }, { cookie })).status, 200);
  sync = await (await post("/agent/sync", {}, targetAuth)).json();
  assert.equal(sync.policy.sites[0].host, "khanacademy.org");
  assert.equal(sync.policy.approveNew, true);

  // A browser can only ever receive sites, never app rules or restrictions.
  const { token } = await (await post("/browser/register", { info: {} })).json();
  const browserAuth = { authorization: `Bearer ${token}` };
  const browserId = token.split(".")[0];
  assert.equal((await post(`/api/browsers/${browserId}/clone-from`, { sourceId: source }, { cookie })).status, 200);
  const bsync = await (await post("/browser/sync", {}, browserAuth)).json();
  assert.equal(bsync.sites[0].host, "khanacademy.org");

  // And a device can clone a browser's sites back, browser-to-device.
  const { auth: target2Auth, id: target2 } = await enrolledDevice(cookie);
  assert.equal((await post(`/api/devices/${target2}/clone-from`, { sourceId: browserId, browser: true }, { cookie })).status, 200);
  const sync2 = await (await post("/agent/sync", {}, target2Auth)).json();
  assert.equal(sync2.policy.sites[0].host, "khanacademy.org");
  assert.equal(sync2.policy.approveNew, false, "cloning from a browser never carries app rules, since it has none");
});

test("icons are stored once and served to the admin only", async () => {
  const cookie = await login();
  const { auth } = await enrolledDevice(cookie);
  const png = Buffer.from("fakepng").toString("base64");
  await post("/agent/sync", { icons: { "com.example.app": png, "../evil": png, "com.bad": "not base64!!" } }, auth);
  const ok = await req("/api/icon/com.example.app", { headers: { cookie } });
  assert.equal(ok.status, 200);
  assert.equal(ok.headers.get("content-type"), "image/png");
  assert.equal(Buffer.from(await ok.arrayBuffer()).toString(), "fakepng");
  assert.equal((await req("/api/icon/com.bad", { headers: { cookie } })).status, 404);
  assert.equal((await req("/api/icon/com.example.app")).status, 401);
});

test("commands are tracked as in-flight until the phone reports a result", async () => {
  const cookie = await login();
  const { auth, id } = await enrolledDevice(cookie);
  await post(`/api/devices/${id}/command`, { type: "lock" }, { cookie });
  const sync = await (await post("/agent/sync", {}, auth)).json();
  const cmdId = sync.commands[0].id;
  let dev = (await (await req("/api/devices", { headers: { cookie } })).json()).find((d) => d.id === id);
  assert.equal(dev.inflight.length, 1);
  await post("/agent/sync", { results: [{ id: cmdId, type: "lock", ok: true, msg: "locked" }] }, auth);
  dev = (await (await req("/api/devices", { headers: { cookie } })).json()).find((d) => d.id === id);
  assert.equal(dev.inflight.length, 0);
  assert.equal(dev.results.at(-1).msg, "locked");
});

test("command arguments are validated", async () => {
  const cookie = await login();
  const { id } = await enrolledDevice(cookie);
  const cmd = (type, args) => post(`/api/devices/${id}/command`, { type, args }, { cookie });
  assert.equal((await cmd("install", { url: "http://insecure/app.apk" })).status, 400);
  assert.equal((await cmd("install", { url: "https://ok.example/app.apk" })).status, 200);
  assert.equal((await cmd("uninstall", { packageName: "bad name; rm" })).status, 400);
  assert.equal((await cmd("uninstall", { packageName: "com.example.app" })).status, 200);
  assert.equal((await cmd("release", { uninstall: true })).status, 200);
});

const put = (cookie, path, body, method = "PUT") =>
  req(path, { method, headers: { cookie, "content-type": "application/json" }, body: body === undefined ? undefined : JSON.stringify(body) });

test("approval mode holds apps installed after it was switched on until approved", async () => {
  const cookie = await login();
  const { auth, id } = await enrolledDevice(cookie);
  const pk = (p) => ({ p, l: p, s: false, h: false });
  await post("/agent/sync", { packages: [pk("com.old.app")] }, auth); // baseline
  await put(cookie, `/api/devices/${id}/config`, { approveNew: true });

  let sync = await (await post("/agent/sync", { packages: [pk("com.old.app"), pk("com.new.app")] }, auth)).json();
  assert.deepEqual(sync.policy.hide, ["com.new.app"]);
  assert.deepEqual(sync.policy.pending, ["com.new.app"]);
  assert.ok(sync.policy.show.includes("com.old.app"));

  // Approve from the dashboard (config says allow)
  await put(cookie, `/api/devices/${id}/config`, { approveNew: true, apps: { "com.new.app": { mode: "allow" } } });
  sync = await (await post("/agent/sync", { packages: [pk("com.old.app"), pk("com.new.app")] }, auth)).json();
  assert.deepEqual(sync.policy.hide, []);
  assert.deepEqual(sync.policy.pending, []);
  const dev = (await (await req("/api/devices", { headers: { cookie } })).json()).find((d) => d.id === id);
  assert.equal(dev.applied.pending.length, 0);
});

test("an override made on the phone becomes the real dashboard setting, not a permanent shadow state", async () => {
  const cookie = await login();
  const { auth, id } = await enrolledDevice(cookie);
  const pk = (p) => ({ p, l: p, s: false, h: false });
  await put(cookie, `/api/devices/${id}/config`, { blockUnlisted: true });
  let sync = await (await post("/agent/sync", { packages: [pk("a.b")], overrides: { "a.b": "allow" }, overridesRev: 100 }, auth)).json();
  assert.deepEqual(sync.policy.hide, [], "phone-side allow beats hide-unlisted");
  // Folded straight into config -- not kept around as a second state to re-fight every sync.
  assert.deepEqual(sync.overrides, {});
  assert.ok(sync.overridesRev > 100, "a newer revision, so the phone actually drops its own local copy");
  let dev = (await (await req("/api/devices", { headers: { cookie } })).json()).find((d) => d.id === id);
  assert.equal(dev.config.apps["a.b"].mode, "allow", "the dashboard now mirrors what happened on the phone");

  // stale revision is ignored
  sync = await (await post("/agent/sync", { packages: [pk("a.b")], overrides: { "a.b": "block" }, overridesRev: 50 }, auth)).json();
  assert.deepEqual(sync.policy.hide, []);

  // Changing it from the dashboard now actually sticks -- no stale phone-side override left to fight it.
  await put(cookie, `/api/devices/${id}/config`, { blockUnlisted: true, apps: { "a.b": { mode: "block" } } });
  sync = await (await post("/agent/sync", { packages: [pk("a.b")] }, auth)).json();
  assert.deepEqual(sync.policy.hide, ["a.b"]);
});

test("phone log events are stored and shown", async () => {
  const cookie = await login();
  const { auth, id } = await enrolledDevice(cookie);
  await post("/agent/sync", { events: [{ k: "hide", m: "Hid Chrome", at: 1 }, { k: "error", m: "x".repeat(500) }] }, auth);
  const dev = (await (await req("/api/devices", { headers: { cookie } })).json()).find((d) => d.id === id);
  assert.equal(dev.events.length, 2);
  assert.equal(dev.events[0].m, "Hid Chrome");
  assert.equal(dev.events[1].m.length, 200);
});

test("master code: only a hash is accepted; it reaches the phone in sync; it's per device", async () => {
  const cookie = await login();
  const { auth, id } = await enrolledDevice(cookie);
  const { id: id2 } = await enrolledDevice(cookie);
  assert.equal((await put(cookie, `/api/devices/${id}/master`, { salt: "nothex", hash: "x" })).status, 400);
  const salt = "ab".repeat(16), hash = "cd".repeat(32);
  assert.equal((await put(cookie, `/api/devices/${id}/master`, { salt, hash })).status, 200);
  const devices = await (await req("/api/devices", { headers: { cookie } })).json();
  assert.equal(devices.find((d) => d.id === id).masterSet, true);
  assert.equal(devices.find((d) => d.id === id2).masterSet, false, "a master code set on one device doesn't appear on another");
  let sync = await (await post("/agent/sync", {}, auth)).json();
  assert.deepEqual(sync.master, { salt, hash, iterations: 10000 });
  assert.equal((await put(cookie, `/api/devices/${id}/master`, undefined, "DELETE")).status, 200);
  sync = await (await post("/agent/sync", {}, auth)).json();
  assert.equal(sync.master, null);
});

test("a phone's own recovery code is reported up and shown per device", async () => {
  const cookie = await login();
  const { auth, id } = await enrolledDevice(cookie);
  const { id: id2 } = await enrolledDevice(cookie);
  await post("/agent/sync", { fallbackCode: "012345" }, auth);
  let devices = await (await req("/api/devices", { headers: { cookie } })).json();
  assert.equal(devices.find((d) => d.id === id).fallbackCode, "012345");
  assert.equal(devices.find((d) => d.id === id2).fallbackCode, null, "each phone's own code, not shared");
  // Junk is ignored, not stored.
  await post("/agent/sync", { fallbackCode: "not-six-digits" }, auth);
  devices = await (await req("/api/devices", { headers: { cookie } })).json();
  assert.equal(devices.find((d) => d.id === id).fallbackCode, "012345");
});

test("a phone can report itself as paused (battery saving), and the dashboard shows it", async () => {
  const cookie = await login();
  const { auth, id } = await enrolledDevice(cookie);
  let devices = await (await req("/api/devices", { headers: { cookie } })).json();
  assert.equal(devices.find((d) => d.id === id).syncPaused, false);
  await post("/agent/sync", { syncPaused: true }, auth);
  devices = await (await req("/api/devices", { headers: { cookie } })).json();
  assert.equal(devices.find((d) => d.id === id).syncPaused, true);
  // Resuming (a later sync reporting it back off) clears it.
  await post("/agent/sync", { syncPaused: false }, auth);
  devices = await (await req("/api/devices", { headers: { cookie } })).json();
  assert.equal(devices.find((d) => d.id === id).syncPaused, false);
});

test("turning Home screen mode on from the phone's own Admin screen mirrors into the dashboard config", async () => {
  const cookie = await login();
  const { auth, id } = await enrolledDevice(cookie);
  let devices = await (await req("/api/devices", { headers: { cookie } })).json();
  assert.equal(devices.find((d) => d.id === id).config.homeScreen, false);
  await post("/agent/sync", { homeScreenRev: 100, homeScreenValue: true }, auth);
  devices = await (await req("/api/devices", { headers: { cookie } })).json();
  assert.equal(devices.find((d) => d.id === id).config.homeScreen, true);
  // A stale revision is ignored.
  await post("/agent/sync", { homeScreenRev: 50, homeScreenValue: false }, auth);
  devices = await (await req("/api/devices", { headers: { cookie } })).json();
  assert.equal(devices.find((d) => d.id === id).config.homeScreen, true);
  // A newer one wins.
  await post("/agent/sync", { homeScreenRev: 200, homeScreenValue: false }, auth);
  devices = await (await req("/api/devices", { headers: { cookie } })).json();
  assert.equal(devices.find((d) => d.id === id).config.homeScreen, false);
});

test("PIN commands validate the PIN and never echo it back in the device record", async () => {
  const cookie = await login();
  const { auth, id } = await enrolledDevice(cookie);
  const cmd = (type, args) => post(`/api/devices/${id}/command`, { type, args }, { cookie });
  assert.equal((await cmd("setPin", { pin: "12" })).status, 400);
  assert.equal((await cmd("setPin", { pin: "abcd" })).status, 400);
  assert.equal((await cmd("setPin", { pin: "4821" })).status, 200);
  const sync = await (await post("/agent/sync", {}, auth)).json();
  assert.equal(sync.commands[0].args.pin, "4821");
  const dev = (await (await req("/api/devices", { headers: { cookie } })).json()).find((d) => d.id === id);
  assert.ok(!JSON.stringify({ ...dev, adminPin: undefined }).includes("4821"), "PIN is not in the queue, log or results once delivered; only the admin-only adminPin field keeps it");
  assert.equal((await cmd("clearPin", {})).status, 200);
});

test("timed lock arguments are capped and the Wi-Fi password is remembered, not logged", async () => {
  const cookie = await login();
  const { auth, id } = await enrolledDevice(cookie);
  const cmd = (type, args) => post(`/api/devices/${id}/command`, { type, args }, { cookie });
  assert.equal((await cmd("lock", { minutes: 99999, message: "x".repeat(500) })).status, 200);
  assert.equal((await cmd("unlock", {})).status, 200);
  assert.equal((await cmd("addWifi", { ssid: "", password: "longenough" })).status, 400);
  assert.equal((await cmd("addWifi", { ssid: "Home", password: "short" })).status, 400);
  assert.equal((await cmd("addWifi", { ssid: "Home", password: "sup3rsecret" })).status, 200);
  const sync = await (await post("/agent/sync", {}, auth)).json();
  const lock = sync.commands.find((c) => c.type === "lock");
  assert.equal(lock.args.minutes, 480);
  assert.equal(lock.args.message.length, 140);
  assert.equal(sync.commands.find((c) => c.type === "addWifi").args.password, "sup3rsecret");
  const dev = (await (await req("/api/devices", { headers: { cookie } })).json()).find((d) => d.id === id);
  assert.deepEqual(dev.wifiNetworks.map((n) => [n.ssid, n.password]), [["Home", "sup3rsecret"]]);
  assert.ok(!JSON.stringify(dev.results).includes("sup3rsecret"));
  assert.equal(sync.policy.reportWifi, true);
});

test("uploaded APKs: validated, stored, fetched only with a device token, installable by id", async () => {
  const cookie = await login();
  const { auth, id } = await enrolledDevice(cookie);
  const apk = new Uint8Array(2000); apk[0] = 0x50; apk[1] = 0x4b;
  const upload = (body, name = "x.apk") => req(`/api/apk?name=${name}`, { method: "PUT", headers: { cookie }, body });
  assert.equal((await upload(new Uint8Array(2000))).status, 400, "must look like a zip/APK");
  assert.equal((await upload(new Uint8Array(10))).status, 400, "too small");
  const ok = await upload(apk, "my app!.apk");
  assert.equal(ok.status, 200);
  const { id: apkId, name } = await ok.json();
  assert.equal(name, "my app.apk");
  assert.equal((await req("/api/apk", { method: "PUT", body: apk })).status, 401, "admin only");

  assert.equal((await post(`/api/devices/${id}/command`, { type: "install", args: { apkId: "aaaaaaaaaaaa" } }, { cookie })).status, 400);
  assert.equal((await post(`/api/devices/${id}/command`, { type: "install", args: { apkId } }, { cookie })).status, 200);
  const sync = await (await post("/agent/sync", {}, auth)).json();
  assert.equal(sync.commands.find((c) => c.type === "install").args.apkId, apkId);

  assert.equal((await req(`/agent/apk/${apkId}`)).status, 401, "needs a device token");
  const dl = await req(`/agent/apk/${apkId}`, { headers: auth });
  assert.equal(dl.status, 200);
  assert.equal((await dl.arrayBuffer()).byteLength, 2000);
});

test("app code chosen on the phone is visible to the administrator and can be reset", async () => {
  const cookie = await login();
  const { auth, id } = await enrolledDevice(cookie);
  await post("/agent/sync", { appCode: "my-secret-4" }, auth);
  let dev = (await (await req("/api/devices", { headers: { cookie } })).json()).find((d) => d.id === id);
  assert.equal(dev.appCode, "my-secret-4");
  assert.equal((await post(`/api/devices/${id}/command`, { type: "resetAppCode" }, { cookie })).status, 200);
  const sync = await (await post("/agent/sync", { appCode: "" }, auth)).json();
  assert.equal(sync.commands[0].type, "resetAppCode");
  dev = (await (await req("/api/devices", { headers: { cookie } })).json()).find((d) => d.id === id);
  assert.equal(dev.appCode, null);
});

test("agent update info comes from the latest GitHub build and needs a device token", async () => {
  const cookie = await login();
  const { auth } = await enrolledDevice(cookie);
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (u) => (String(u).endsWith("/version.json") ? new Response(JSON.stringify({ versionCode: 42 })) : new Response("no", { status: 404 }));
  try {
    assert.equal((await post("/agent/update", {})).status, 401);
    const info = await (await post("/agent/update", {}, auth)).json();
    assert.equal(info.latest.versionCode, 42);
    assert.match(info.latest.apkUrl, /^https:\/\/github\.com\/.+\/releases\/download\/latest\/mdm-agent\.apk$/);
    const dash = await (await req("/api/latest-agent", { headers: { cookie } })).json();
    assert.equal(dash.latest.versionCode, 42);
    globalThis.fetch = async () => new Response("nope", { status: 404 });
    assert.equal((await (await req("/api/latest-agent", { headers: { cookie } })).json()).latest, null);
  } finally {
    globalThis.fetch = realFetch;
  }
});

test("play-token needs a device token, needs the dashboard set up, and refreshes a short-lived token from the stored master token", async () => {
  const cookie = await login();
  const { auth } = await enrolledDevice(cookie);
  assert.equal((await post("/agent/play-token", {})).status, 401, "needs a device token");

  const notSetUp = await post("/agent/play-token", {}, auth);
  assert.equal(notSetUp.status, 503);
  assert.match((await notSetUp.json()).error, /not set up/);

  env.PLAY_EMAIL = "updates@example.com";
  env.PLAY_ANDROID_ID = "abc123";
  env.PLAY_MASTER_TOKEN = "aas_et/fake-master-token";
  const realFetch = globalThis.fetch;
  let seenBody = null;
  globalThis.fetch = async (u, init) => {
    assert.equal(u, "https://android.clients.google.com/auth");
    seenBody = new URLSearchParams(init.body);
    return new Response("Auth=ya29.fake-short-token\nSID=x\n");
  };
  try {
    const res = await post("/agent/play-token", {}, auth);
    assert.equal(res.status, 200);
    const info = await res.json();
    assert.equal(info.auth, "ya29.fake-short-token");
    assert.equal(info.email, "updates@example.com");
    assert.equal(info.androidId, "abc123");
    assert.equal(seenBody.get("EncryptedPasswd"), "aas_et/fake-master-token");
    assert.equal(seenBody.get("service"), "androidmarket");
    assert.equal(seenBody.get("app"), "com.android.vending");

    globalThis.fetch = async () => new Response("Error=BadAuthentication\n", { status: 403 });
    const bad = await post("/agent/play-token", {}, auth);
    assert.equal(bad.status, 503);
    assert.match((await bad.json()).error, /BadAuthentication/);
  } finally {
    globalThis.fetch = realFetch;
    delete env.PLAY_EMAIL;
    delete env.PLAY_ANDROID_ID;
    delete env.PLAY_MASTER_TOKEN;
  }
});

test("enrollment code carries the APK URL and checksum for QR-code provisioning, when published", async () => {
  const cookie = await login();
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (u) => (String(u).endsWith("/version.json") ? new Response(JSON.stringify({ versionCode: 42, sha256: "abc123" })) : new Response("no", { status: 404 }));
  try {
    const r = await (await post("/api/codes", { type: "enroll" }, { cookie })).json();
    assert.equal(r.sha256, "abc123");
    assert.match(r.apkUrl, /mdm-agent\.apk$/);
    // Other code types never need this, since only "enroll" drives QR provisioning.
    const r2 = await (await post("/api/codes", { type: "install" }, { cookie })).json();
    assert.equal(r2.sha256, undefined);
  } finally {
    globalThis.fetch = realFetch;
  }
});

test("enrollment code omits the checksum when the build hasn't published one yet", async () => {
  const cookie = await login();
  const realFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response("no", { status: 404 });
  try {
    const r = await (await post("/api/codes", { type: "enroll" }, { cookie })).json();
    assert.equal(r.apkUrl, undefined);
    assert.equal(r.sha256, undefined);
  } finally {
    globalThis.fetch = realFetch;
  }
});

const PNG = (n = 200) => { const b = new Uint8Array(n); b.set([0x89, 0x50, 0x4e, 0x47]); return b; };

test("custom app icons override the phone's icon and can be reset", async () => {
  const cookie = await login();
  const { auth } = await enrolledDevice(cookie);
  await post("/agent/sync", { icons: { "com.x.app": Buffer.from(PNG(100)).toString("base64") } }, auth);
  const put = (body) => req("/api/icon/com.x.app", { method: "PUT", headers: { cookie }, body });
  assert.equal((await put(new Uint8Array(200))).status, 400, "must be a PNG");
  assert.equal((await put(PNG(300 * 1024))).status, 400, "too big");
  assert.equal((await req("/api/icon/com.x.app", { method: "PUT", body: PNG() })).status, 401, "admin only");
  const custom = PNG(150); custom[10] = 7;
  assert.equal((await put(custom)).status, 200);
  const got = Buffer.from(await (await req("/api/icon/com.x.app", { headers: { cookie } })).arrayBuffer());
  assert.equal(got[10], 7, "custom icon is served");
  assert.equal((await req("/api/icon/com.x.app", { method: "DELETE", headers: { cookie } })).status, 200);
  const back = Buffer.from(await (await req("/api/icon/com.x.app", { headers: { cookie } })).arrayBuffer());
  assert.equal(back.length, 100, "the phone's own icon comes back");
});

test("logo: admin uploads a PNG, phones see a new revision and can fetch it", async () => {
  const cookie = await login();
  const { auth } = await enrolledDevice(cookie);
  let sync = await (await post("/agent/sync", {}, auth)).json();
  assert.equal(sync.logoRev, 0);
  assert.equal((await req("/api/logo", { method: "PUT", headers: { cookie }, body: new Uint8Array(500) })).status, 400);
  assert.equal((await req("/api/logo", { method: "PUT", headers: { cookie }, body: PNG(500) })).status, 200);
  sync = await (await post("/agent/sync", {}, auth)).json();
  assert.ok(sync.logoRev > 0);
  assert.equal((await req("/agent/logo")).status, 401, "needs a device token");
  const dl = await req("/agent/logo", { headers: auth });
  assert.equal(dl.status, 200);
  assert.equal((await dl.arrayBuffer()).byteLength, 500);
  assert.equal((await req("/api/logo", { method: "DELETE", headers: { cookie } })).status, 200);
  sync = await (await post("/agent/sync", {}, auth)).json();
  assert.equal(sync.logoRev, 0);
});

test("a PIN the administrator sets can be shown on the dashboard once the phone confirms it", async () => {
  const cookie = await login();
  const { auth, id } = await enrolledDevice(cookie);
  const cmd = (type, args) => post(`/api/devices/${id}/command`, { type, args }, { cookie });
  const dev = async () => (await (await req("/api/devices", { headers: { cookie } })).json()).find((d) => d.id === id);

  await cmd("setPin", { pin: "4821" });
  assert.deepEqual((await dev()).adminPin, { pin: "4821", ok: false }, "pending until the phone confirms");
  const sync = await (await post("/agent/sync", {}, auth)).json();
  await post("/agent/sync", { results: [{ id: sync.commands[0].id, type: "setPin", ok: true, msg: "screen lock PIN set" }] }, auth);
  assert.deepEqual((await dev()).adminPin, { pin: "4821", ok: true });

  await cmd("clearPin", {});
  const s2 = await (await post("/agent/sync", {}, auth)).json();
  await post("/agent/sync", { results: [{ id: s2.commands[0].id, type: "clearPin", ok: true, msg: "screen lock removed" }] }, auth);
  assert.equal((await dev()).adminPin, null);

  await cmd("setPin", { pin: "9999" });
  const s3 = await (await post("/agent/sync", {}, auth)).json();
  await post("/agent/sync", { results: [{ id: s3.commands[0].id, type: "setPin", ok: false, msg: "rejected" }] }, auth);
  assert.equal((await dev()).adminPin, null, "a rejected PIN is not shown");
});

test("home-screen mode: phones learn which custom icons to download and can fetch them", async () => {
  const cookie = await login();
  const { auth, id } = await enrolledDevice(cookie);
  const custom = PNG(120); custom[9] = 5;
  await req("/api/icon/com.x.app", { method: "PUT", headers: { cookie }, body: custom });

  let sync = await (await post("/agent/sync", {}, auth)).json();
  assert.equal(sync.policy.customIcons, undefined, "only sent when home-screen mode is on");
  await put(cookie, `/api/devices/${id}/config`, { homeScreen: true });
  sync = await (await post("/agent/sync", {}, auth)).json();
  assert.equal(sync.policy.homeScreen, true);
  assert.ok(sync.policy.customIcons["com.x.app"] > 0);

  assert.equal((await req("/agent/icon/com.x.app")).status, 401);
  const dl = Buffer.from(await (await req("/agent/icon/com.x.app", { headers: auth })).arrayBuffer());
  assert.equal(dl[9], 5);
  await req("/api/icon/com.x.app", { method: "DELETE", headers: { cookie } });
  sync = await (await post("/agent/sync", {}, auth)).json();
  assert.deepEqual(sync.policy.customIcons, {});
  assert.equal((await req("/agent/icon/com.x.app", { headers: auth })).status, 404);
});

test("blocked-site requests: reported by the phone, deduplicated, shown to the admin, dismissable", async () => {
  const cookie = await login();
  const { auth, id } = await enrolledDevice(cookie);

  await post("/agent/sync", { siteRequests: [{ url: "https://bad.example/page" }, { url: "https://bad.example/page" }] }, auth);
  let dev = (await (await req("/api/devices", { headers: { cookie } })).json()).find((d) => d.id === id);
  assert.equal(dev.siteRequests.length, 1, "duplicates within one report are collapsed");

  await post("/agent/sync", { siteRequests: [{ url: "https://bad.example/page" }, { url: "https://other.example/" }] }, auth);
  dev = (await (await req("/api/devices", { headers: { cookie } })).json()).find((d) => d.id === id);
  assert.equal(dev.siteRequests.length, 2, "a repeat of an already-known URL is not added again");

  await req(`/api/devices/${id}/site-requests?url=${encodeURIComponent("https://bad.example/page")}`, { method: "DELETE", headers: { cookie } });
  dev = (await (await req("/api/devices", { headers: { cookie } })).json()).find((d) => d.id === id);
  assert.deepEqual(dev.siteRequests.map((r) => r.url), ["https://other.example/"]);
});

test("messages from the phone: free text, reach the dashboard, dismissable", async () => {
  const cookie = await login();
  const { auth, id } = await enrolledDevice(cookie);

  await post("/agent/sync", { messages: [{ msg: "The lock screen won't turn off" }, { msg: "  " }] }, auth);
  let dev = (await (await req("/api/devices", { headers: { cookie } })).json()).find((d) => d.id === id);
  assert.equal(dev.messages.length, 1, "a blank message is dropped");
  assert.equal(dev.messages[0].msg, "The lock screen won't turn off");
  assert.ok(dev.messages[0].id);

  await post("/agent/sync", { messages: [{ msg: "Second message" }] }, auth);
  dev = (await (await req("/api/devices", { headers: { cookie } })).json()).find((d) => d.id === id);
  assert.equal(dev.messages.length, 2, "messages accumulate across syncs, unlike site requests they are not deduplicated by text");

  await req(`/api/devices/${id}/messages?id=${dev.messages[0].id}`, { method: "DELETE", headers: { cookie } });
  dev = (await (await req("/api/devices", { headers: { cookie } })).json()).find((d) => d.id === id);
  assert.deepEqual(dev.messages.map((m) => m.msg), ["Second message"]);
});

test("approving a site adds it to that device's own policy, not any other device's", async () => {
  const cookie = await login();
  const { auth, id } = await enrolledDevice(cookie);
  const { auth: auth2 } = await enrolledDevice(cookie);
  await put(cookie, `/api/devices/${id}/config`, { sites: { a: { type: "domain", url: "khanacademy.org" } } });
  const sync = await (await post("/agent/sync", {}, auth)).json();
  assert.equal(sync.policy.sites.length, 1);
  assert.equal(sync.policy.sites[0].host, "khanacademy.org");
  const sync2 = await (await post("/agent/sync", {}, auth2)).json();
  assert.equal(sync2.policy.sites.length, 0, "a site added to one device's own page doesn't leak to a different device");
});

test("GET /api/devices caches briefly (saves KV reads on the dashboard's own auto-refresh) but a change on the device shows up immediately, not after the cache expires", async () => {
  const cookie = await login();
  const { id } = await enrolledDevice(cookie);
  const mine = (list) => list.find((d) => d.id === id);

  const first = await (await req("/api/devices", { headers: { cookie } })).json();
  assert.ok(mine(first));
  const callsAfterFirst = getCalls;

  const second = await (await req("/api/devices", { headers: { cookie } })).json();
  assert.ok(mine(second));
  assert.equal(getCalls, callsAfterFirst, "a second read moments later should be served from cache, not hit KV again");

  assert.equal((await post(`/api/devices/${id}/command`, { type: "lock" }, { cookie })).status, 200);
  const afterChange = await (await req("/api/devices", { headers: { cookie } })).json();
  assert.equal(mine(afterChange).pending, 1, "the queued command shows up right away -- the write busted the cache instead of leaving it stale");
  assert.ok(getCalls > callsAfterFirst, "the post-write read should have gone back to KV instead of reusing the stale cache");
});
