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
