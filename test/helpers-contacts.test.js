import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { buildAgentPolicy, normalizeConfig } from "../src/policy.js";

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
const login = async (form) => {
  const r = await req("/login", { method: "POST", body: new URLSearchParams(form) });
  const c = r.headers.get("set-cookie");
  return c ? c.split(";")[0] : null;
};
const enroll = async (cookie, model) => {
  const { code } = await (await req("/api/codes", { method: "POST", headers: { ...J, cookie }, body: JSON.stringify({ type: "enroll" }) })).json();
  const { token } = await (await req("/agent/enroll", { method: "POST", headers: J, body: JSON.stringify({ code, info: { model } }) })).json();
  await req("/agent/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${token}` }, body: "{}" });
  return { token, dev: (await (await req("/api/devices", { headers: { cookie } })).json()).find((d) => d.name.endsWith(model)) };
};

test("other parents: full and view-only logins see the owner's phones; only the main login manages logins", async () => {
  const admin = await login({ password: env.ADMIN_PASSWORD });
  const { dev } = await enroll(admin, "Family");
  assert.equal((await req("/api/helpers", { method: "POST", headers: { cookie: admin, ...J }, body: JSON.stringify({ loginName: "sarah", password: "short" }) })).status, 400, "password too short");
  assert.equal((await req("/api/helpers", { method: "POST", headers: { cookie: admin, ...J }, body: JSON.stringify({ name: "Sarah", loginName: "sarah", password: "sarah-pass-1" }) })).status, 200);
  await req("/api/helpers", { method: "POST", headers: { cookie: admin, ...J }, body: JSON.stringify({ name: "Grandpa", loginName: "zaidy", password: "zaidy-pass-1", viewOnly: true }) });
  assert.equal((await req("/api/helpers", { method: "POST", headers: { cookie: admin, ...J }, body: JSON.stringify({ loginName: "sarah", password: "another-pass" }) })).status, 400, "name taken");
  assert.equal((await req("/api/clients", { method: "POST", headers: { cookie: admin, ...J }, body: JSON.stringify({ loginName: "sarah", password: "client-pass" }) })).status, 400, "a client cannot take a parent's login name");
  const list = await (await req("/api/helpers", { headers: { cookie: admin } })).json();
  assert.deepEqual(list.map((x) => [x.id, x.viewOnly]), [["sarah", false], ["zaidy", true]]);
  assert.ok(!JSON.stringify(list).includes("hash"), "no password data leaves the server");

  assert.equal(await login({ loginName: "sarah", password: "wrong-pass" }), null);
  const sarah = await login({ loginName: "sarah", password: "sarah-pass-1" });
  const zaidy = await login({ loginName: "zaidy", password: "zaidy-pass-1" });
  const st = await (await req("/api/state", { headers: { cookie: zaidy } })).json();
  assert.deepEqual(st.session, { role: "admin", helper: "Grandpa", viewOnly: true });

  assert.ok((await (await req("/api/devices", { headers: { cookie: zaidy } })).json()).some((d) => d.id === dev.id), "view-only sees the phones");
  assert.equal((await req(`/api/devices/${dev.id}/config`, { method: "PUT", headers: { cookie: zaidy, ...J }, body: JSON.stringify({ safeSearch: true }) })).status, 403, "view-only cannot change anything");
  assert.equal((await req(`/api/devices/${dev.id}/config`, { method: "PUT", headers: { cookie: sarah, ...J }, body: JSON.stringify({ safeSearch: true }) })).status, 200, "a full login can");
  assert.equal((await req("/api/helpers", { method: "POST", headers: { cookie: sarah, ...J }, body: JSON.stringify({ loginName: "third", password: "third-pass-1" }) })).status, 403, "a second parent cannot add logins");
  assert.equal((await req("/api/2fa/setup", { method: "POST", headers: { cookie: sarah, ...J }, body: "{}" })).status, 403, "nor change two-step login");
  const audit = await (await req("/api/audit", { headers: { cookie: admin } })).json();
  assert.ok(audit.entries.some((e) => e.who === 'Parent "Sarah"'), "changes are recorded with who made them");

  // A client's own second parent sees only that client's phones.
  await req("/api/clients", { method: "POST", headers: { cookie: admin, ...J }, body: JSON.stringify({ name: "Cohen", loginName: "cohen", password: "cohen-pass" }) });
  const cohen = await login({ loginName: "cohen", password: "cohen-pass" });
  await req("/api/helpers", { method: "POST", headers: { cookie: cohen, ...J }, body: JSON.stringify({ name: "Mrs Cohen", loginName: "mrscohen", password: "mrs-cohen-pass" }) });
  assert.deepEqual((await (await req("/api/helpers", { headers: { cookie: admin } })).json()).map((x) => x.id), ["sarah", "zaidy"], "the admin's list is the admin's own");
  const mrs = await login({ loginName: "mrscohen", password: "mrs-cohen-pass" });
  assert.ok(!(await (await req("/api/devices", { headers: { cookie: mrs } })).json()).some((d) => d.id === dev.id), "not the admin's phones");
  assert.equal((await req("/api/helpers/sarah", { method: "DELETE", headers: { cookie: cohen } })).status, 404, "a client cannot remove the admin's logins");

  assert.equal((await req("/api/helpers/zaidy", { method: "DELETE", headers: { cookie: admin } })).status, 200);
  assert.equal((await req("/api/devices", { headers: { cookie: zaidy } })).status, 401, "a removed login is signed out at once");
});

test("contacts: shown on the dashboard; allowed numbers sent to the phone's contacts only when switched on", async () => {
  const p0 = buildAgentPolicy(normalizeConfig({ callNumbers: [{ name: "Mom", number: "052-123-4567" }] }), []);
  assert.deepEqual(p0.contactsToAdd, []);
  const p1 = buildAgentPolicy(normalizeConfig({ syncContacts: true, reportContacts: true, blockWaStatus: true, callNumbers: [{ name: "Mom", number: "052-123-4567" }] }), []);
  assert.deepEqual(p1.contactsToAdd, [{ name: "Mom", number: "0521234567" }]);
  assert.equal(p1.reportContacts, true);
  assert.equal(p1.blockWaStatus, true);
  assert.equal(p1.blockWaChannels, false);

  const admin = await login({ password: env.ADMIN_PASSWORD });
  const { token, dev } = await enroll(admin, "Contacts");
  await req("/agent/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${token}` }, body: JSON.stringify({ contacts: [{ name: "Mom", number: "052-123-4567" }, "junk", { name: "Dad" }] }) });
  const d = (await (await req("/api/devices", { headers: { cookie: admin } })).json()).find((x) => x.id === dev.id);
  assert.deepEqual(d.contacts, [{ name: "Mom", number: "052-123-4567" }, { name: "Dad", number: "" }]);
  assert.ok(d.contactsAt > 0);
});
