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

test("YouTube follows the filter level by default, and an explicit choice always wins", async () => {
  assert.equal(normalizeConfig({}).youtubeMode, "auto");
  for (const bad of ["Restricted", "open", "", null, 1, "restricted "]) assert.equal(normalizeConfig({ youtubeMode: bad }).youtubeMode, "auto", String(bad));
  const mode = (c) => buildAgentPolicy(normalizeConfig(c), []).youtubeMode;
  // auto: Moderate -> restricted (search works); Strict and Off -> only allowed channels/videos
  assert.equal(mode({}), "channels");
  assert.equal(mode({ browserFilter: "off" }), "channels");
  assert.equal(mode({ browserFilter: "moderate" }), "restricted");
  assert.equal(mode({ browserFilter: "strict" }), "channels");
  // an explicit choice overrides the level in both directions
  assert.equal(mode({ browserFilter: "moderate", youtubeMode: "channels" }), "channels");
  assert.equal(mode({ browserFilter: "strict", youtubeMode: "restricted" }), "restricted");
  assert.equal(mode({ browserFilter: "off", youtubeMode: "restricted" }), "restricted");
  assert.deepEqual(summarizeConfigChange(normalizeConfig({}), normalizeConfig({ youtubeMode: "restricted" })).filter((l) => l.startsWith("YouTube")), ["YouTube in the browsers: restricted"]);

  const login = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  const cookie = login.headers.get("set-cookie").split(";")[0];
  const reg = await (await req("/browser/register", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ info: { model: "Y" } }) })).json();
  const auth = { authorization: `Bearer ${reg.token}`, "content-type": "application/json" };
  const sync = async () => await (await req("/browser/sync", { method: "POST", headers: auth, body: "{}" })).json();
  const id = reg.token.split(".")[0];
  const put = (body) => req(`/api/browsers/${id}/config`, { method: "PUT", headers: { cookie, "content-type": "application/json" }, body: JSON.stringify(body) });
  assert.equal((await sync()).filter.youtube, "channels", "a new Browser: off -> channels only");
  await put({ browserFilter: "moderate" });
  assert.equal((await sync()).filter.youtube, "restricted", "Moderate lets him search YouTube");
  await put({ browserFilter: "strict" });
  assert.equal((await sync()).filter.youtube, "channels", "Strict: allowed channels only");
  await put({ browserFilter: "moderate", youtubeMode: "channels" });
  assert.equal((await sync()).filter.youtube, "channels", "an explicit choice wins over the level");
});
test("browser inspection is off unless exactly true, and its skip list is sanitized", () => {
  assert.equal(normalizeConfig({}).tlsInspection, false);
  assert.equal(normalizeConfig({ tlsInspection: true }).tlsInspection, true);
  for (const bad of ["true", 1, "yes", null]) assert.equal(normalizeConfig({ tlsInspection: bad }).tlsInspection, false, String(bad));
  // Tolerant on purpose (pasted links and wildcards become plain hostnames); junk is dropped; always lower-case.
  assert.deepEqual(normalizeConfig({ inspectBypass: ["MyBank.com", "bad host!", "https://x.com/y", "pay.example.org", "*.wild.com"] }).inspectBypass.sort(),
    ["mybank.com", "pay.example.org", "wild.com", "x.com"]);
  const p = buildAgentPolicy(normalizeConfig({ tlsInspection: true, inspectBypass: ["mybank.com"] }), []);
  assert.equal(p.tlsInspection, true);
  assert.deepEqual(p.inspectBypass, ["mybank.com"]);
  assert.equal(buildAgentPolicy(normalizeConfig({}), []).tlsInspection, false);
  assert.deepEqual(summarizeConfigChange(normalizeConfig({}), normalizeConfig({ tlsInspection: true })).filter((l) => l.startsWith("Browser inspection")), ["Browser inspection: on"]);
});
