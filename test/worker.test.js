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
  const { code } = await (await post("/api/enrollment-code", {}, { cookie })).json();

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
