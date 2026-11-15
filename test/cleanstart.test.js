import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { cleanStartConfig, normalizeConfig, buildAgentPolicy, RESTRICTIONS } from "../src/policy.js";

// A phone enrolled now starts with NOTHING on (the administrator asked for this after USB debugging, the web and YouTube were all closed on a
// brand-new tablet). Phones enrolled before keep what they have.
test("the clean start has no restriction, no filter and nothing inspecting secure pages", () => {
  const cfg = normalizeConfig(cleanStartConfig());
  assert.ok(Object.keys(RESTRICTIONS).length > 5);
  for (const k of Object.keys(RESTRICTIONS)) assert.equal(cfg.restrictions[k], false, "restriction off: " + k);
  assert.equal(cfg.restrictions.debuggingDisabled, false, "USB debugging stays usable");
  assert.equal(cfg.browserFilter, "custom");
  assert.deepEqual(cfg.filterKinds, [], "no kind of site is closed");
  assert.equal(cfg.youtubeMode, "open");
  assert.equal(cfg.tlsInspection, false);
  assert.equal(cfg.autoUpdate, true, "automatic updates are still on for a new phone");
  assert.equal(cfg.blockImages, false);
  assert.equal(cfg.safeSearch, false);
  const policy = buildAgentPolicy(cfg, [], {});
  assert.deepEqual(policy.restrictions, [], "no restriction is sent to the phone");
  assert.equal(policy.youtubeMode, "open");
  assert.ok(!policy.filterBase || policy.filterBase.length === 0, "no blocklist is sent");
});

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
  return { kv, call: (path, init = {}) => worker.fetch(new Request("https://x.test" + path, init), env) };
}
const J = { "content-type": "application/json" };

test("a phone that enrolls now gets the clean start; a phone enrolled before keeps the old defaults", async () => {
  const { kv, call } = boot();
  const login = await call("/login", { method: "POST", body: new URLSearchParams({ password: "pw" }) });
  const cookie = login.headers.get("set-cookie").split(";")[0];
  const code = (await (await call("/api/codes", { method: "POST", headers: { ...J, cookie }, body: JSON.stringify({ type: "enroll" }) })).json()).code;
  const enrolled = await (await call("/agent/enroll", { method: "POST", headers: J, body: JSON.stringify({ code, info: { manufacturer: "x", model: "NewPhone", versionCode: 999999 } }) })).json();
  const sync = await (await call("/agent/sync", { method: "POST", headers: { ...J, authorization: "Bearer " + enrolled.token }, body: JSON.stringify({ info: { versionCode: 999999 }, packages: [] }) })).json();
  assert.deepEqual(sync.policy.restrictions, [], "a new phone starts with no restriction");
  assert.equal(sync.policy.youtubeMode, "open");
  assert.equal(sync.policy.autoUpdate, true);

  const oldId = "0123456789abcdef";
  const secret = "ab".repeat(24);
  const hash = Buffer.from(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(secret))).toString("hex");
  kv.set("device:" + oldId, JSON.stringify({ id: oldId, name: "old phone", tokenHash: hash, lastSeen: Date.now(), info: {}, packages: [] }));
  const oldSync = await (await call("/agent/sync", { method: "POST", headers: { ...J, authorization: "Bearer " + oldId + "." + secret }, body: JSON.stringify({ info: { versionCode: 1 }, packages: [] }) })).json();
  assert.ok(oldSync.policy.restrictions.length > 0, "an older phone keeps its protective defaults");
  assert.equal(oldSync.policy.autoUpdate, false);
});
