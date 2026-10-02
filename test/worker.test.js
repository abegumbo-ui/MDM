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

  await req("/api/config", { method: "PUT", headers: { cookie, "content-type": "application/json" }, body: JSON.stringify({ blockUnlisted: true, apps: { "com.google.android.apps.maps": { mode: "allow" } } }) });
  sync = await (await post("/agent/sync", { packages }, auth)).json();
  assert.deepEqual(sync.policy.hide, ["com.android.chrome"]);
  assert.ok(sync.policy.show.includes("com.google.android.apps.maps"));

  const devices = await (await req("/api/devices", { headers: { cookie } })).json();
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
