import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { KvStore } from "../pi/kv.mjs";
import { createApp, readEnv } from "../pi/server.mjs";
import { noteBackupUse } from "../src/replicate.js";

const tmp = () => fs.mkdtempSync(path.join(os.tmpdir(), "mdm-pi-"));
const listen = (app) => new Promise((r) => app.server.listen(0, "127.0.0.1", () => r(app.server.address().port)));

test("storage: values, lists, expiry, deletes and a restart", async () => {
  const dir = tmp();
  let kv = new KvStore(dir);
  await kv.init();
  await kv.put("a:1", "one");
  await kv.put("a:2", JSON.stringify({ n: 2 }));
  await kv.put("b:1", new Uint8Array([1, 2, 3]).buffer);
  await kv.put("gone", "soon", { expirationTtl: 1 });
  assert.equal(await kv.get("a:1"), "one");
  assert.deepEqual(await kv.get("a:2", "json"), { n: 2 });
  assert.deepEqual([...new Uint8Array(await kv.get("b:1", "arrayBuffer"))], [1, 2, 3]);
  assert.deepEqual((await kv.list({ prefix: "a:" })).keys.map((k) => k.name), ["a:1", "a:2"]);
  assert.equal((await kv.list({ prefix: "gone" })).keys.length, 1, "not expired yet");
  await kv.delete("a:1");
  assert.equal(await kv.get("a:1"), null);
  kv.map.get("gone").exp = Date.now() - 1; // time passes
  assert.equal(await kv.get("gone"), null, "expired entries are gone");
  kv.close();
  // a restart finds everything again (snapshot and log)
  kv = new KvStore(dir);
  await kv.init();
  assert.equal(await kv.get("a:1"), null);
  assert.deepEqual(await kv.get("a:2", "json"), { n: 2 });
  assert.equal(await kv.get("b:1", "arrayBuffer").then((x) => x.byteLength), 3);
  kv.close();
});

test("storage: big values stay on disk, not in memory, and survive a restart", async () => {
  const dir = tmp();
  let kv = new KvStore(dir);
  await kv.init();
  const big = "x".repeat(200 * 1024);
  await kv.put("shot:1:abc", big);
  const bin = new Uint8Array(150 * 1024).map((_, i) => i % 251);
  await kv.put("apk:9", bin.buffer);
  assert.ok(kv.map.get("shot:1:abc").f && kv.map.get("shot:1:abc").v === undefined, "no big text in memory");
  assert.equal(fs.readdirSync(path.join(dir, "blobs")).length, 2);
  assert.equal(await kv.get("shot:1:abc"), big);
  assert.deepEqual(new Uint8Array(await kv.get("apk:9", "arrayBuffer")), bin);
  await kv.put("shot:1:abc", "small now"); // replaced by a small one: the file goes
  assert.equal(fs.readdirSync(path.join(dir, "blobs")).length, 1);
  kv.close();
  kv = new KvStore(dir);
  await kv.init();
  assert.deepEqual(new Uint8Array(await kv.get("apk:9", "arrayBuffer")), bin, "after a restart");
  await kv.delete("apk:9");
  assert.equal(fs.readdirSync(path.join(dir, "blobs")).length, 0, "deleting removes the file");
  kv.close();
});

test("the dashboard code runs on the Pi: sign-in, pages, phone API", async () => {
  const app = await createApp({ dataDir: tmp(), vars: readEnv({ ADMIN_PASSWORD: "correct horse" }), replication: false, scheduleMs: 0 });
  const port = await listen(app);
  const base = `http://127.0.0.1:${port}`;
  const login = await fetch(base + "/");
  assert.equal(login.status, 200);
  assert.match(await login.text(), /password/i, "the sign-in page");
  const bad = await fetch(base + "/login", { method: "POST", body: new URLSearchParams({ password: "wrong" }) });
  assert.equal(bad.status, 401);
  const ok = await fetch(base + "/login", { method: "POST", body: new URLSearchParams({ password: "correct horse" }), redirect: "manual" });
  assert.equal(ok.status, 303);
  const cookie = ok.headers.getSetCookie().map((c) => c.split(";")[0]).join("; ");
  assert.ok(cookie.includes("sess="), "a session cookie is given");
  const home = await fetch(base + "/", { headers: { cookie } });
  assert.equal(home.status, 200);
  assert.match(await home.text(), /<script/i, "the dashboard page");
  const devices = await fetch(base + "/api/devices", { headers: { cookie } });
  assert.equal(devices.status, 200);
  assert.ok(Array.isArray(await devices.json()));
  const unknown = await fetch(base + "/agent/sync", { method: "POST", headers: { "content-type": "application/json" }, body: "{}" });
  assert.equal(unknown.status, 401, "a phone without a token is refused");
  const health = await (await fetch(base + "/healthz")).json();
  assert.equal(health.ok, true);
  assert.equal(health.role, "main");
  await app.stop();
});

test("the Pi copies changes to the backup, and takes them back after an outage", async () => {
  const secret = "s3cret-for-the-test";
  const backup = await createApp({ dataDir: tmp(), vars: { ADMIN_PASSWORD: "x", ROLE: "backup", REPLICA_SECRET: secret }, replication: false, scheduleMs: 0 });
  const backupPort = await listen(backup);
  const backupUrl = `http://127.0.0.1:${backupPort}`;
  // The backup already has something (for example the data from before the Pi existed).
  await backup.kv.put("device:phone1", JSON.stringify({ id: "phone1", name: "Old phone" }));
  await backup.kv.put("config:x", "from cloudflare");

  const vars = { ADMIN_PASSWORD: "y", ROLE: "main", REPLICA_SECRET: secret, BACKUP_URL: backupUrl, PRIMARY_URL: "https://pi.example" };
  // A brand-new Pi starts with everything from the backup.
  const pi = await createApp({ dataDir: tmp(), vars, scheduleMs: 0 });
  await pi.repl.first;
  assert.equal(await pi.kv.get("config:x"), "from cloudflare", "first copy");
  assert.deepEqual(await pi.kv.get("device:phone1", "json"), { id: "phone1", name: "Old phone" });

  // A change on the Pi goes to the backup (and so does a delete); local-only things do not.
  await pi.kv.put("config:x", "changed on the pi");
  await pi.kv.put("device:phone2", JSON.stringify({ id: "phone2" }));
  await pi.kv.delete("device:phone1");
  await pi.kv.put("shot:phone2:1", "y".repeat(100 * 1024));
  let r = await pi.repl.flush();
  assert.equal(r.error, undefined, "copy worked: " + JSON.stringify(r));
  assert.equal(await backup.kv.get("config:x"), "changed on the pi");
  assert.deepEqual(await backup.kv.get("device:phone2", "json"), { id: "phone2" });
  assert.equal(await backup.kv.get("device:phone1"), null, "deleted there too");
  assert.equal(await backup.kv.get("shot:phone2:1"), null, "screenshots stay on the Pi");

  // The Pi goes off: a phone uses the backup and says so; a change is made there.
  await noteBackupUse({ ROLE: "backup", STATE: backup.kv }, { viaBackup: true });
  assert.ok(await backup.kv.get("repl:outage"), "the backup notes the outage");
  await backup.kv.put("config:x", "changed while the pi was off");
  await backup.kv.put("device:phone3", JSON.stringify({ id: "phone3" }));
  // The Pi comes back: before copying anything it takes the backup's changes, and the backup is told.
  await pi.kv.put("config:y", "pi change made before it noticed");
  r = await pi.repl.flush();
  assert.equal(r.tookBack, true, "outage handled: " + JSON.stringify(r));
  assert.equal(await pi.kv.get("config:x"), "changed while the pi was off", "the backup's change is back on the Pi");
  assert.deepEqual(await pi.kv.get("device:phone3", "json"), { id: "phone3" });
  assert.equal(await backup.kv.get("repl:outage"), null, "outage cleared");
  assert.ok(await pi.kv.get("shot:phone2:1"), "the Pi keeps its own local-only entries");

  // A Pi that cannot reach the backup keeps its changes for the next time.
  const lonely = await createApp({ dataDir: tmp(), vars: { ...vars, BACKUP_URL: "http://127.0.0.1:1" }, scheduleMs: 0 });
  await lonely.repl.first;
  await lonely.kv.put("k", "v");
  const lr = await lonely.repl.flush();
  assert.ok(lr.offline || lr.error, "offline is not a crash");
  assert.equal(lonely.repl.dirty.size, 1, "the change waits");
  await lonely.stop();
  await pi.stop();
  await backup.stop();
});

test("the backup answers /internal only with the shared secret, and tells phones which servers to use", async () => {
  const app = await createApp({ dataDir: tmp(), vars: { ADMIN_PASSWORD: "x", ROLE: "backup", REPLICA_SECRET: "abc", PRIMARY_URL: "https://pi.example/", BACKUP_URL: "https://mdm.example" }, replication: false, scheduleMs: 0 });
  const port = await listen(app);
  const base = `http://127.0.0.1:${port}`;
  assert.equal((await fetch(base + "/internal/status")).status, 403, "no secret");
  assert.equal((await fetch(base + "/internal/status", { headers: { "x-replica-secret": "nope" } })).status, 403, "wrong secret");
  const ok = await fetch(base + "/internal/status", { headers: { "x-replica-secret": "abc" } });
  assert.equal(ok.status, 200);
  assert.equal((await ok.json()).role, "backup");
  const plain = await createApp({ dataDir: tmp(), vars: { ADMIN_PASSWORD: "x" }, replication: false, scheduleMs: 0 });
  const p2 = await listen(plain);
  assert.equal((await fetch(`http://127.0.0.1:${p2}/internal/status`, { headers: { "x-replica-secret": "abc" } })).status, 404, "off unless configured");
  await plain.stop();
  await app.stop();
});

test("Pi system helpers: nmcli output is read correctly, Wi-Fi input is checked, a dangerous removal is refused", async () => {
  const { splitTerse, parseSavedWifi, parseVisibleWifi, wifiInputProblem, createSystem } = await import("../pi/system.mjs");
  assert.deepEqual(splitTerse("a\\:b:c:d"), ["a:b", "c", "d"], "an escaped colon stays in the value");
  const saved = parseSavedWifi("Home 5G:802-11-wireless:yes:20:wlan0\nguest:802-11-wireless:yes:10:\nlo:loopback:no:0:lo\n");
  assert.deepEqual(saved.map((s) => [s.name, s.priority, s.active]), [["Home 5G", 20, true], ["guest", 10, false]], "only Wi-Fi, best first");
  const vis = parseVisibleWifi("*:Home 5G:78:WPA2\n :Cafe:40:WPA2 WPA3\n :Cafe:62:WPA2 WPA3\n :open one:30:\n");
  assert.equal(vis[0].ssid, "Home 5G"); assert.equal(vis[0].active, true);
  assert.equal(vis.find((v) => v.ssid === "Cafe").signal, 62, "the strongest copy of a name");
  assert.equal(wifiInputProblem("Home", "longenough"), null);
  assert.match(wifiInputProblem("", "longenough"), /name/);
  assert.match(wifiInputProblem("Home", "short"), /8 to 63/);
  assert.match(wifiInputProblem("Ho\nme", "longenough"), /name/);
  assert.equal(wifiInputProblem("Open", ""), null, "an open network has no password");

  const calls = [];
  const run = async (cmd, args) => {
    calls.push([cmd, ...args]);
    if (cmd === "nmcli" && args.includes("con") && args.includes("show")) return { ok: true, code: 0, out: "guest:802-11-wireless:yes:10:wlan0\n", err: "" };
    if (cmd === "nmcli" && args.includes("wifi") && args.includes("list")) return { ok: true, code: 0, out: "*:guest:80:WPA2\n", err: "" };
    return { ok: true, code: 0, out: "", err: "" };
  };
  const sys = createSystem({ run });
  const added = await sys.wifiRemember({ ssid: "New Net", password: "s3cret-pass", priority: 30 });
  assert.equal(added.ok, true);
  const add = calls.find((c) => c.includes("add"));
  assert.ok(add.includes("New Net") && add.includes("s3cret-pass") && add.includes("30"), "the network is saved with its password and priority");
  assert.ok(calls.some((c) => c.includes("delete") && c.includes("New Net")), "a name saved before is replaced (new password)");
  assert.ok(calls.every((c) => c[0] === "sudo" || c[0] === "nmcli"), "no shell, only these programs");
  const refuse = await sys.wifiForget("guest");
  assert.equal(refuse.ok, false, "removing the only network the Pi is on is refused");
  assert.match(refuse.error, /cut the Pi off/);
});

test("the Server page backend: administrator only, no cross-site presses, Wi-Fi and status", async () => {
  const calls = [];
  const fakeSys = {
    health: async () => ({ hostname: "mdmpi", uptimeSeconds: 99, cpuTempC: 41.2, memoryTotalMB: 416, memoryFreeMB: 200, power: { ok: true } }),
    wifiNow: async () => ({ state: "connected", ssid: "Home", signal: 70, ip: "192.168.0.50/24" }),
    wifiLists: async () => ({ saved: [{ name: "Home", priority: 10, autoconnect: true, active: true }], visible: [{ ssid: "Home", signal: 70, security: "WPA2", active: true }] }),
    wifiRemember: async (a) => { calls.push(["remember", a.ssid, a.password]); return { ok: true }; },
    wifiConnect: async (n) => { calls.push(["connect", n]); return { ok: true }; },
    wifiForget: async (n) => { calls.push(["forget", n]); return { ok: true }; },
    wifiPriority: async (n, p) => { calls.push(["priority", n, p]); return { ok: true }; },
    tailscale: async () => ({ installed: true, state: "Running", name: "mdmpi.tail.ts.net" }),
    logLines: async () => "line one\nline two",
    restartService: async () => { calls.push(["restart"]); return { ok: true }; },
    rebootPi: async () => ({ ok: true }),
  };
  const app = await createApp({ dataDir: tmp(), vars: readEnv({ ADMIN_PASSWORD: "pw pw pw" }), replication: false, scheduleMs: 0, piAdminOptions: { sys: fakeSys } });
  const port = await listen(app);
  const base = `http://127.0.0.1:${port}`;
  const J = { "content-type": "application/json" };
  assert.equal((await fetch(base + "/api/pi/status")).status, 403, "not signed in");
  const login = await fetch(base + "/login", { method: "POST", body: new URLSearchParams({ password: "pw pw pw" }), redirect: "manual" });
  const cookie = login.headers.getSetCookie().map((c) => c.split(";")[0]).join("; ");
  const st = await (await fetch(base + "/api/pi/status", { headers: { cookie } })).json();
  assert.equal(st.role, "main"); assert.equal(st.health.cpuTempC, 41.2); assert.equal(st.wifi.ssid, "Home"); assert.equal(st.tailscale.state, "Running");
  assert.deepEqual(st.polling, { idleSeconds: 300, dashboardOpenSeconds: 3, afterChangeSeconds: 3 }, "3 seconds while the dashboard is open, 5 minutes otherwise");
  const w = await (await fetch(base + "/api/pi/wifi", { headers: { cookie } })).json();
  assert.equal(w.saved[0].name, "Home");
  assert.equal((await fetch(base + "/api/pi/wifi", { method: "POST", headers: { cookie }, body: JSON.stringify({ ssid: "X", password: "12345678" }) })).status, 415, "must be JSON");
  assert.equal((await fetch(base + "/api/pi/wifi", { method: "POST", headers: { cookie, ...J, origin: "https://evil.example" }, body: JSON.stringify({ ssid: "X", password: "12345678" }) })).status, 403, "a page on another site cannot press it");
  const ok = await fetch(base + "/api/pi/wifi", { method: "POST", headers: { cookie, ...J }, body: JSON.stringify({ ssid: "Neighbour", password: "12345678" }) });
  assert.equal(ok.status, 200);
  assert.deepEqual(calls.find((c) => c[0] === "remember"), ["remember", "Neighbour", "12345678"]);
  await fetch(base + "/api/pi/wifi/priority", { method: "POST", headers: { cookie, ...J }, body: JSON.stringify({ name: "Home", priority: 50 }) });
  assert.deepEqual(calls.find((c) => c[0] === "priority"), ["priority", "Home", 50]);
  assert.equal((await fetch(base + "/api/pi/wifi?name=Old", { method: "DELETE", headers: { cookie } })).status, 415, "a removal without the JSON header is refused too");
  await fetch(base + "/api/pi/wifi?name=Old", { method: "DELETE", headers: { cookie, ...J } });
  assert.deepEqual(calls.find((c) => c[0] === "forget"), ["forget", "Old"]);
  assert.match((await (await fetch(base + "/api/pi/log", { headers: { cookie } })).json()).text, /line two/);
  assert.equal((await fetch(base + "/api/pi/copy-now", { method: "POST", headers: { cookie, ...J }, body: "{}" })).status, 400, "no Cloudflare copy set up yet: a plain message");
  await app.stop();
  // On Cloudflare (no Pi) the same address is simply not there.
  const { default: worker } = await import("../src/index.js");
  const cfKv = new KvStore(tmp()); await cfKv.init();
  const r = await worker.fetch(new Request("https://mdm.example/api/pi/status"), { ADMIN_PASSWORD: "x", STATE: cfKv }, { waitUntil() {} });
  assert.ok(r.status === 401 || r.status === 404, "Cloudflare has no Server page");
  cfKv.close();
});

test("phones check in every 3 seconds while the dashboard is open, every 5 minutes otherwise (Pi); Cloudflare keeps its slow numbers", async () => {
  const mk = async (vars) => {
    const app = await createApp({ dataDir: tmp(), vars, replication: false, scheduleMs: 0 });
    const port = await listen(app);
    const base = `http://127.0.0.1:${port}`;
    const login = await fetch(base + "/login", { method: "POST", body: new URLSearchParams({ password: vars.ADMIN_PASSWORD }), redirect: "manual" });
    const cookie = login.headers.getSetCookie().map((c) => c.split(";")[0]).join("; ");
    const { code } = await (await fetch(base + "/api/codes", { method: "POST", headers: { cookie, "content-type": "application/json" }, body: JSON.stringify({ type: "enroll" }) })).json();
    const { token } = await (await fetch(base + "/agent/enroll", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ code, info: { model: "T" } }) })).json();
    const sync = async () => (await (await fetch(base + "/agent/sync", { method: "POST", headers: { "content-type": "application/json", authorization: `Bearer ${token}` }, body: "{}" })).json()).pollSeconds;
    return { app, sync };
  };
  const pi = await mk(readEnv({ ADMIN_PASSWORD: "pw pw pw" }));
  await pi.app.kv.put("adminActive", String(Date.now() - 20 * 60 * 1000));
  assert.equal(await pi.sync(), 300, "nobody on the dashboard: every 5 minutes");
  await pi.app.kv.put("adminActive", String(Date.now()));
  await pi.app.kv.put("adminAction", String(Date.now() - 20 * 60 * 1000));
  assert.equal(await pi.sync(), 3, "dashboard open: every 3 seconds");
  await pi.app.kv.put("adminAction", String(Date.now()));
  assert.equal(await pi.sync(), 3, "right after a change: every 3 seconds");
  await pi.app.stop();
  const cf = await mk({ ADMIN_PASSWORD: "pw pw pw", ROLE: "backup" });
  await cf.app.kv.put("adminActive", String(Date.now()));
  await cf.app.kv.put("adminAction", String(Date.now() - 20 * 60 * 1000));
  assert.equal(await cf.sync(), 30, "Cloudflare: 30 seconds while the dashboard is open");
  await cf.app.stop();
});

test("the dashboard says which server it is, and each phone says which one it used", async () => {
  // On the Pi.
  const app = await createApp({ dataDir: tmp(), vars: readEnv({ ADMIN_PASSWORD: "pw pw pw", PRIMARY_URL: "https://pi.example", BACKUP_URL: "https://cf.example", REPLICA_SECRET: "s" }), replication: false, scheduleMs: 0 });
  const port = await listen(app);
  const base = `http://127.0.0.1:${port}`;
  const J = { "content-type": "application/json" };
  const login = await fetch(base + "/login", { method: "POST", body: new URLSearchParams({ password: "pw pw pw" }), redirect: "manual" });
  const cookie = login.headers.getSetCookie().map((c) => c.split(";")[0]).join("; ");
  const state = await (await fetch(base + "/api/state", { headers: { cookie } })).json();
  assert.equal(state.server.runtime, "pi");
  assert.equal(state.server.primary, "https://pi.example");
  const { code } = await (await fetch(base + "/api/codes", { method: "POST", headers: { cookie, ...J }, body: JSON.stringify({ type: "enroll" }) })).json();
  const { token } = await (await fetch(base + "/agent/enroll", { method: "POST", headers: J, body: JSON.stringify({ code, info: { model: "T" } }) })).json();
  const sync = (body) => fetch(base + "/agent/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${token}` }, body: JSON.stringify(body) });
  await sync({});
  assert.equal((await (await fetch(base + "/api/devices", { headers: { cookie } })).json())[0].via, "pi", "a phone that reached the Pi says so");
  await sync({ viaBackup: true });
  assert.equal((await (await fetch(base + "/api/devices", { headers: { cookie } })).json())[0].via, "cloudflare", "a phone that fell back says so");
  // The dashboard shows the real last check-in, not the (at most every 10 minutes) saved one.
  const before = Date.now();
  await sync({});
  const seenAt = (await (await fetch(base + "/api/devices", { headers: { cookie } })).json())[0].lastSeen;
  assert.ok(seenAt >= before - 50, "the last check-in shown is the newest one, not a saved one up to 10 minutes old");
  await app.stop();

  // On Cloudflare (the backup).
  const { default: worker } = await import("../src/index.js");
  const kv = new KvStore(tmp()); await kv.init();
  const env = { ADMIN_PASSWORD: "x", STATE: kv, ROLE: "backup", PRIMARY_URL: "https://pi.example", BACKUP_URL: "https://cf.example", REPLICA_SECRET: "s" };
  const cf = (p, init) => worker.fetch(new Request("https://cf.example" + p, init), env, { waitUntil() {} });
  const lg = await cf("/login", { method: "POST", body: new URLSearchParams({ password: "x" }), redirect: "manual" });
  const ck = lg.headers.get("set-cookie").split(";")[0];
  let st = await (await cf("/api/state", { headers: { cookie: ck } })).json();
  assert.deepEqual([st.server.runtime, st.server.role, st.server.outage], ["cloudflare", "backup", false]);
  const c2 = await (await cf("/api/codes", { method: "POST", headers: { cookie: ck, ...J }, body: JSON.stringify({ type: "enroll" }) })).json();
  const t2 = (await (await cf("/agent/enroll", { method: "POST", headers: J, body: JSON.stringify({ code: c2.code, info: { model: "T" } }) })).json()).token;
  await cf("/agent/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${t2}` }, body: JSON.stringify({ viaBackup: true }) });
  st = await (await cf("/api/state", { headers: { cookie: ck } })).json();
  assert.equal(st.server.outage, true, "Cloudflare says the phones had to use it");
  assert.equal((await (await cf("/api/devices", { headers: { cookie: ck } })).json())[0].via, "cloudflare");
  kv.close();
});
