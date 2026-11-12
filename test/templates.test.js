import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { buildAgentPolicy, normalizeBlockedWords, normalizeConfig, summarizeConfigChange } from "../src/policy.js";

// Connecting a Browser needs a code made on the dashboard.
async function enrollBrowser(fetcher, cookie, info) {
  const { code } = await (await fetcher("/api/codes", { method: "POST", headers: { "content-type": "application/json", cookie }, body: JSON.stringify({ type: "browser" }) })).json();
  return fetcher("/browser/enroll", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ code, info }) });
}

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
const J = { "content-type": "application/json" };

test("blocked words are cleaned the same way the phone cleans them, and reach the phone and the Browser", async () => {
  assert.deepEqual(normalizeBlockedWords(["Casino", "  lottery   tickets ", "casino", "!!!", 5, "גוי", "x".repeat(61)]), ["casino", "lottery tickets", "גוי"]);
  assert.equal(normalizeBlockedWords(Array.from({ length: 250 }, (_, i) => "w" + i)).length, 200);
  assert.deepEqual(normalizeConfig({}).blockedWords, []);
  assert.deepEqual(buildAgentPolicy({ blockedWords: ["Casino"] }, []).blockedWords, ["casino"]);
  assert.ok(summarizeConfigChange(normalizeConfig({}), normalizeConfig({ blockedWords: ["a", "b"] })).includes("Blocked words: 2"));
  const login = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  const cookie = login.headers.get("set-cookie").split(";")[0];
  const reg = await (await enrollBrowser(req, cookie, { model: "W" })).json();
  await req(`/api/browsers/${reg.token.split(".")[0]}/config`, { method: "PUT", headers: { cookie, ...J }, body: JSON.stringify({ blockedWords: ["Casino"] }) });
  const s = await (await req("/browser/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${reg.token}` }, body: "{}" })).json();
  assert.deepEqual(s.filter.words, ["casino"]);
});

test("templates: save a phone's settings, apply them to others, keep each phone's own reset protection, and keep clients apart", async () => {
  const login = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  const admin = login.headers.get("set-cookie").split(";")[0];
  const enroll = async (cookie, model) => {
    const { code } = await (await req("/api/codes", { method: "POST", headers: { ...J, cookie }, body: JSON.stringify({ type: "enroll" }) })).json();
    const { token } = await (await req("/agent/enroll", { method: "POST", headers: J, body: JSON.stringify({ code, info: { model } }) })).json();
    await req("/agent/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${token}` }, body: "{}" });
    return (await (await req("/api/devices", { headers: { cookie } })).json()).find((d) => d.name.endsWith(model));
  };
  const a = await enroll(admin, "Alpha"), b = await enroll(admin, "Beta");
  const put = (id, body) => req(`/api/devices/${id}/config`, { method: "PUT", headers: { cookie: admin, ...J }, body: JSON.stringify(body) });
  await put(a.id, { safeSearch: true, blockedWords: ["casino"], screenTime: { dailyLimit: 120 }, frpAccounts: ["123456789012345678901"] });
  await put(b.id, { frpAccounts: ["999999999999999999999"] });

  assert.equal((await req("/api/templates", { method: "POST", headers: { cookie: admin, ...J }, body: JSON.stringify({ name: "", deviceId: a.id }) })).status, 400, "a name is needed");
  const { id } = await (await req("/api/templates", { method: "POST", headers: { cookie: admin, ...J }, body: JSON.stringify({ name: "Teen phone", deviceId: a.id }) })).json();
  const list = await (await req("/api/templates", { headers: { cookie: admin } })).json();
  assert.equal(list.length, 1);
  assert.equal(list[0].name, "Teen phone");
  assert.equal(list[0].from, a.name);
  assert.ok(list[0].summary.includes("Forced SafeSearch: on"));
  assert.ok(!JSON.stringify(list).includes("123456789012345678901"), "reset-protection accounts are not in a template");

  const r = await (await req(`/api/templates/${id}/apply`, { method: "POST", headers: { cookie: admin, ...J }, body: JSON.stringify({ deviceIds: [b.id, "ffff", "not hex!"] }) })).json();
  assert.equal(r.applied, 1, "only real phones are counted");
  const b2 = (await (await req("/api/devices", { headers: { cookie: admin } })).json()).find((d) => d.id === b.id);
  assert.equal(b2.config.safeSearch, true);
  assert.deepEqual(b2.config.blockedWords, ["casino"]);
  assert.equal(b2.config.screenTime.dailyLimit, 120);
  assert.deepEqual(b2.config.frpAccounts, ["999999999999999999999"], "the phone keeps its own reset-protection account");
  assert.ok(b2.pushLog.some((p) => p.changes[0] === 'Template "Teen phone" applied'));

  // A client sees and applies only its own templates and phones.
  await req("/api/clients", { method: "POST", headers: { cookie: admin, ...J }, body: JSON.stringify({ name: "Kid", loginName: "kidfam", password: "kid-password-1" }) });
  const client = (await req("/login", { method: "POST", body: new URLSearchParams({ loginName: "kidfam", password: "kid-password-1" }) })).headers.get("set-cookie").split(";")[0];
  assert.deepEqual(await (await req("/api/templates", { headers: { cookie: client } })).json(), [], "the admin's template is not visible to a client");
  assert.equal((await req(`/api/templates/${id}/apply`, { method: "POST", headers: { cookie: client, ...J }, body: JSON.stringify({ deviceIds: [b.id] }) })).status, 404);
  assert.equal((await req("/api/templates", { method: "POST", headers: { cookie: client, ...J }, body: JSON.stringify({ name: "Mine", deviceId: a.id }) })).status, 404, "a client cannot copy the admin's phone");

  assert.equal((await req(`/api/templates/${id}`, { method: "DELETE", headers: { cookie: admin } })).status, 200);
  assert.deepEqual(await (await req("/api/templates", { headers: { cookie: admin } })).json(), []);
  const audit = await (await req("/api/audit", { headers: { cookie: admin } })).json();
  assert.ok(audit.entries.some((e) => e.text === "Applied a template to 3 phone(s)") && audit.entries.some((e) => e.text === "Deleted a template"));
});

test("a template's description never mentions reset protection, which templates do not carry", async () => {
  const login = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  const cookie = login.headers.get("set-cookie").split(";")[0];
  const { code } = await (await req("/api/codes", { method: "POST", headers: { ...J, cookie }, body: JSON.stringify({ type: "enroll" }) })).json();
  const { token } = await (await req("/agent/enroll", { method: "POST", headers: J, body: JSON.stringify({ code, info: { model: "Gamma" } }) })).json();
  await req("/agent/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${token}` }, body: "{}" });
  const g = (await (await req("/api/devices", { headers: { cookie } })).json()).find((d) => d.name.endsWith("Gamma"));
  await req(`/api/devices/${g.id}/config`, { method: "PUT", headers: { cookie, ...J }, body: JSON.stringify({ safeSearch: true }) });
  await req("/api/templates", { method: "POST", headers: { cookie, ...J }, body: JSON.stringify({ name: "Plain", deviceId: g.id }) });
  const t = (await (await req("/api/templates", { headers: { cookie } })).json()).find((x) => x.name === "Plain");
  assert.deepEqual(t.summary, ["Forced SafeSearch: on"]);
});
