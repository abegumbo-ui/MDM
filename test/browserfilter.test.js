import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { buildAgentPolicy, normalizeConfig, summarizeConfigChange } from "../src/policy.js";

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

test("the filter level is off unless it is exactly moderate or strict", () => {
  assert.equal(normalizeConfig({}).browserFilter, "off");
  assert.equal(normalizeConfig({ browserFilter: "strict" }).browserFilter, "strict");
  assert.equal(normalizeConfig({ browserFilter: "moderate" }).browserFilter, "moderate");
  for (const bad of ["STRICT", "none", "", null, 1, true, "strict "]) assert.equal(normalizeConfig({ browserFilter: bad }).browserFilter, "off", String(bad));
});

test("the agent's policy carries the filter level and changes are described", () => {
  const strict = normalizeConfig({ browserFilter: "strict" });
  assert.equal(buildAgentPolicy(strict, []).browserFilter, "strict");
  assert.equal(buildAgentPolicy(normalizeConfig({}), []).browserFilter, "off");
  assert.deepEqual(summarizeConfigChange(normalizeConfig({}), strict).filter((l) => l.startsWith("Browser filter")), ["Browser filter: strict"]);
  assert.deepEqual(summarizeConfigChange(strict, strict).filter((l) => l.startsWith("Browser filter")), []);
});

test("a dashboard-connected Browser is told its filter level and where to download the lists", async () => {
  const login = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  const cookie = login.headers.get("set-cookie").split(";")[0];
  const reg = await (await req("/browser/register", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ info: { model: "T" } }) })).json();
  assert.ok(reg.token);
  const auth = { authorization: `Bearer ${reg.token}`, "content-type": "application/json" };
  const sync = async () => (await (await req("/browser/sync", { method: "POST", headers: auth, body: "{}" })).json());

  assert.equal((await sync()).filter.tier, "off", "a new Browser starts allowlist-only");

  const browsers = await (await req("/api/browsers", { headers: { cookie } })).json();
  const id = browsers[0].id;
  const put = await req(`/api/browsers/${id}/config`, { method: "PUT", headers: { cookie, "content-type": "application/json" }, body: JSON.stringify({ browserFilter: "moderate" }) });
  assert.equal(put.status, 200);
  const s = await sync();
  assert.equal(s.filter.tier, "moderate");
  assert.match(s.filter.base, /^https:\/\/github\.com\/[^/]+\/[^/]+\/releases\/download\/blocklists$/);
  assert.deepEqual(s.sites, [], "the allowlist is untouched");
});
