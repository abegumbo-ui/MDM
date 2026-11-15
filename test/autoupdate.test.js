import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

// Automatic updates: on for phones enrolled from now on, untouched for phones enrolled before. A phone that is behind is told to update.
function boot() {
  const kv = new Map();
  const env = {
    ADMIN_PASSWORD: "pw",
    STATE: {
      get: async (k, t) => (kv.has(k) ? (t === "json" ? JSON.parse(kv.get(k)) : kv.get(k)) : null),
      put: async (k, v) => void kv.set(k, v),
      delete: async (k) => void kv.delete(k),
      list: async ({ prefix }) => ({ keys: [...kv.keys()].filter((k) => k.startsWith(prefix)).map((name) => ({ name })), list_complete: true }),
    },
  };
  const call = (path, init = {}) => worker.fetch(new Request("https://x.test" + path, init), env);
  return { env, kv, call };
}
const J = { "content-type": "application/json" };

test("a phone enrolled now starts with automatic updates on; one enrolled before keeps them off", async () => {
  const { kv, call } = boot();
  const login = await call("/login", { method: "POST", body: new URLSearchParams({ password: "pw" }) });
  const cookie = login.headers.get("set-cookie").split(";")[0];
  const code = (await (await call("/api/codes", { method: "POST", headers: { ...J, cookie }, body: JSON.stringify({ type: "enroll" }) })).json()).code;
  const enrolled = await (await call("/agent/enroll", { method: "POST", headers: J, body: JSON.stringify({ code, info: { manufacturer: "x", model: "NewPhone", versionCode: 999999 } }) })).json();
  const sync = await (await call("/agent/sync", { method: "POST", headers: { ...J, authorization: "Bearer " + enrolled.token }, body: JSON.stringify({ info: { manufacturer: "x", model: "NewPhone", versionCode: 999999 }, packages: [] }) })).json();
  assert.equal(sync.policy.autoUpdate, true, "a new phone has automatic updates on");

  // A phone that was enrolled before this existed: a record without the new flag and without a saved config.
  const oldId = "0123456789abcdef";
  const secret = "ab".repeat(24);
  const hash = Buffer.from(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(secret))).toString("hex");
  kv.set("device:" + oldId, JSON.stringify({ id: oldId, name: "old phone", tokenHash: hash, lastSeen: Date.now(), info: {}, packages: [] }));
  const oldSync = await (await call("/agent/sync", { method: "POST", headers: { ...J, authorization: "Bearer " + oldId + "." + secret }, body: JSON.stringify({ info: { versionCode: 1 }, packages: [] }) })).json();
  assert.equal(oldSync.policy.autoUpdate, false, "an older phone is left exactly as it was");
  assert.equal((oldSync.commands || []).some((c) => c.type === "updateAgent"), false, "and is never told to update by itself");
});
