import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

const kv = new Map();
const env = {
  ADMIN_PASSWORD: "correct horse",
  STATE: { get: async (k, t) => (kv.has(k) ? (t === "json" ? JSON.parse(kv.get(k)) : kv.get(k)) : null), put: async (k, v) => kv.set(k, v) },
};
const req = (path, init) => worker.fetch(new Request("https://x.test" + path, init), env);

test("unauthenticated requests are rejected", async () => {
  assert.equal((await req("/api/state")).status, 401);
  assert.match(await (await req("/")).text(), /Sign in/);
});

test("wrong password fails, right password grants a working session", async () => {
  const bad = await req("/login", { method: "POST", body: new URLSearchParams({ password: "nope" }) });
  assert.equal(bad.status, 401);
  const ok = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  assert.equal(ok.status, 303);
  const cookie = ok.headers.get("set-cookie").split(";")[0];
  const page = await req("/", { headers: { cookie } });
  assert.match(await page.text(), /MDM Dashboard/);
  const forged = await req("/", { headers: { cookie: "sess=9999999999.deadbeef" } });
  assert.match(await forged.text(), /Sign in/);
});
