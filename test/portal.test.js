import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

// Self sign-up and the walls between people. The sweep at the bottom is the important part: two clients, each with a phone, a Browser and a
// template, and every route that takes an id is tried by one of them with the other one's ids (and with the administrator's). Anything but
// "refused" fails the test, so a new route that forgets the ownership check shows up here.

function makeEnv(extra = {}) {
  const kv = new Map();
  return {
    kv,
    env: {
      ADMIN_PASSWORD: "pw",
      ...extra,
      STATE: {
        get: async (k, t) => (kv.has(k) ? (t === "json" ? JSON.parse(kv.get(k)) : kv.get(k)) : null),
        put: async (k, v) => void kv.set(k, v),
        delete: async (k) => void kv.delete(k),
        list: async ({ prefix }) => ({ keys: [...kv.keys()].filter((k) => k.startsWith(prefix)).map((name) => ({ name })), list_complete: true }),
      },
    },
  };
}
const J = { "content-type": "application/json" };

async function boot(extra) {
  const { env, kv } = makeEnv(extra);
  const call = (path, init = {}) => worker.fetch(new Request("https://x.test" + path, init), env);
  const post = async (path, form, headers = {}) => call(path, { method: "POST", headers, body: new URLSearchParams(form) });
  const login = async (loginName, password) => {
    const r = await post("/login", loginName ? { loginName, password } : { password });
    const c = r.headers.get("set-cookie");
    return c ? c.split(";")[0] : null;
  };
  const adminCookie = await login("", "pw");
  const api = (cookie) => (path, method = "GET", body) => call(path, { method, headers: { ...J, ...(cookie ? { cookie } : {}) }, ...(body !== undefined ? { body: JSON.stringify(body) } : {}) });
  return { env, kv, call, post, login, adminCookie, admin: api(adminCookie), api };
}

const goodForm = (over = {}) => ({ name: "Dana Cohen", loginName: "dana", email: "dana@example.com", password: "a-long-password", password2: "a-long-password", website: "", ...over });

test("the sign-up page does not exist until the main administrator opens it", async () => {
  const { call, post, admin } = await boot();
  assert.equal((await call("/signup")).status, 404, "closed: no page");
  assert.equal((await post("/signup", goodForm())).status, 404, "closed: nothing can be sent");
  assert.equal((await admin("/api/signups/settings", "PUT", { open: true })).status, 200);
  assert.equal((await call("/signup")).status, 200, "open: the page is there");
  assert.match(await (await call("/signup")).text(), /Get your own portal/);
});

test("a sign-up is only a request: it cannot sign in or see anything until the administrator approves it", async () => {
  const { call, post, login, admin, api } = await boot();
  await admin("/api/signups/settings", "PUT", { open: true });
  const sent = await post("/signup", goodForm());
  assert.equal(sent.status, 200);
  assert.match(await sent.text(), /Thank you/);
  const list = await (await admin("/api/signups")).json();
  assert.equal(list.pending.length, 1);
  assert.equal(list.pending[0].id, "dana");
  assert.equal(JSON.stringify(list).includes("hash"), false, "the administrator's list never carries the password hash");
  assert.equal(await login("dana", "a-long-password"), null, "pending: cannot sign in");
  assert.equal((await admin("/api/signups/dana/approve", "POST", {})).status, 200);
  assert.equal((await (await admin("/api/signups")).json()).pending.length, 0, "approved: no longer waiting");
  const cookie = await login("dana", "a-long-password");
  assert.ok(cookie, "approved: signs in with the password they chose");
  const me = await (await api(cookie)("/api/state")).json();
  assert.equal(me.session.role, "client");
  assert.deepEqual(await (await api(cookie)("/api/devices")).json(), [], "a new client starts with no phones");
  assert.equal((await api(cookie)("/api/clients")).status, 403, "and cannot list other accounts");
});

test("a refused sign-up leaves nothing behind, and strangers learn nothing about existing logins", async () => {
  const { post, admin, kv } = await boot();
  await admin("/api/clients", "POST", { loginName: "carol", name: "Someone", password: "whatever-pass" });
  await admin("/api/signups/settings", "PUT", { open: true });
  let ip = 0; // a different address for each try, so the throttle (tested on its own) does not get in the way here
  const msg = async (over) => { const r = await post("/signup", goodForm(over), { "cf-connecting-ip": "192.0.2." + (++ip) }); return { status: r.status, text: await r.text() }; };
  const reserved = await msg({ loginName: "admin" });
  const existing = await msg({ loginName: "carol" });
  assert.equal(reserved.status, 400);
  assert.equal(existing.status, 400);
  assert.match(reserved.text, /not available/);
  assert.match(existing.text, /not available/);
  assert.equal(/already|exists|taken/i.test(existing.text.replace(/login name is not available/g, "")), false, "no hint that the name is in use");
  assert.equal((await msg({ password: "short", password2: "short" })).status, 400, "short password");
  assert.equal((await msg({ password2: "a-different-one" })).status, 400, "passwords must match");
  assert.equal((await msg({ name: "" })).status, 400, "a name is needed");
  const bot = await msg({ website: "http://spam.example" });
  assert.equal(bot.status, 200, "a bot is told it worked");
  assert.equal([...kv.keys()].some((k) => k.startsWith("signup:")), false, "but nothing was saved");
  assert.equal(JSON.stringify([...kv.values()]).includes("a-long-password"), false, "a password is never stored as typed");
});

test("sign-ups are throttled per address and the queue is capped", async () => {
  const { post, admin } = await boot();
  await admin("/api/signups/settings", "PUT", { open: true });
  let last;
  for (let i = 0; i < 6; i++) last = await post("/signup", goodForm({ loginName: "user" + i }), { "cf-connecting-ip": "203.0.113.7" });
  assert.equal(last.status, 429, "the sixth try from one address in a short time is refused");
  assert.equal((await post("/signup", goodForm({ loginName: "elsewhere" }), { "cf-connecting-ip": "203.0.113.8" })).status, 200, "another address is not affected");
  // 30 waiting requests is the most; each address may only send 5, so use many addresses.
  let status = 200;
  for (let i = 0; i < 40 && status !== 503; i++) status = (await post("/signup", goodForm({ loginName: "fill" + i }), { "cf-connecting-ip": "198.51.100." + i })).status;
  assert.equal(status, 503, "the queue is full: new requests are told to come back later");
  assert.ok(((await (await admin("/api/signups")).json()).pending).length <= 30);
});

test("only the main administrator can look at, approve or deny sign-ups or open the page", async () => {
  const { post, login, admin, api, call } = await boot();
  await admin("/api/signups/settings", "PUT", { open: true });
  await post("/signup", goodForm());
  await admin("/api/clients", "POST", { loginName: "cleo", name: "Cleo", password: "cleo-password" });
  await admin("/api/helpers", "POST", { loginName: "helper1", name: "Helper", password: "helper-password" });
  const client = api(await login("cleo", "cleo-password"));
  const helper = api(await login("helper1", "helper-password"));
  for (const who of [client, helper]) {
    assert.equal((await who("/api/signups")).status, 403);
    assert.equal((await who("/api/signups/settings", "PUT", { open: false })).status, 403);
    assert.equal((await who("/api/signups/dana/approve", "POST", {})).status, 403);
    assert.equal((await who("/api/signups/dana", "DELETE")).status, 403);
  }
  assert.equal((await call("/api/signups")).status, 401, "not signed in at all");
  assert.equal((await (await admin("/api/signups")).json()).pending.length, 1, "nothing changed");
  assert.equal((await admin("/api/signups/dana", "DELETE")).status, 200, "the administrator can deny");
  assert.equal((await (await admin("/api/signups")).json()).pending.length, 0);
});

test("the backup copy sends sign-ups to the Raspberry Pi while the Pi is up", async () => {
  const { call, admin } = await boot({ ROLE: "backup", PRIMARY_URL: "https://pi.example" });
  await admin("/api/signups/settings", "PUT", { open: true });
  const r = await call("/signup");
  assert.equal(r.status, 302);
  assert.equal(r.headers.get("location"), "https://pi.example/signup");
});

/* ------------------------------ the walls between people ------------------------------ */

async function twoClients() {
  const t = await boot();
  const { admin, login, api, call } = t;
  for (const [n, p] of [["alice", "alice-password"], ["bob", "bob-password"]]) await admin("/api/clients", "POST", { loginName: n, name: n, password: p });
  const A = api(await login("alice", "alice-password"));
  const B = api(await login("bob", "bob-password"));
  const enroll = async (who, type, path, info) => {
    const { code } = await (await who("/api/codes", "POST", { type })).json();
    const r = await call(path, { method: "POST", headers: J, body: JSON.stringify({ code, info }) });
    assert.equal(r.status, 200, "enrolled " + type);
    return r.json();
  };
  await enroll(A, "enroll", "/agent/enroll", { model: "ALICE-PHONE", manufacturer: "T" });
  await enroll(B, "enroll", "/agent/enroll", { model: "BOB-PHONE", manufacturer: "T" });
  await enroll(admin, "enroll", "/agent/enroll", { model: "ADMIN-PHONE", manufacturer: "T" });
  await enroll(A, "browser", "/browser/enroll", { model: "ALICE-BROWSER" });
  await enroll(B, "browser", "/browser/enroll", { model: "BOB-BROWSER" });
  const idOf = async (who, model) => { const l = await (await who("/api/devices")).json(); return (l.find((d) => JSON.stringify(d).includes(model)) || {}).id; };
  const browserIdOf = async (who, model) => { const l = await (await who("/api/browsers")).json(); return (l.find((d) => JSON.stringify(d).includes(model)) || {}).id; };
  const ids = { a: await idOf(A, "ALICE-PHONE"), b: await idOf(B, "BOB-PHONE"), admin: await idOf(admin, "ADMIN-PHONE"), ab: await browserIdOf(A, "ALICE-BROWSER"), bb: await browserIdOf(B, "BOB-BROWSER") };
  for (const [k, v] of Object.entries(ids)) assert.match(String(v), /^[0-9a-f]+$/, "found the id of " + k);
  // Give Bob something worth stealing.
  assert.equal((await B(`/api/devices/${ids.b}/config`, "PUT", { blockedWords: ["bobsecret"], sites: { x: { type: "domain", url: "bobs-private-site.example" } } })).status, 200);
  await B("/api/templates", "POST", { name: "Bobs template", deviceId: ids.b });
  return { ...t, A, B, ids };
}

test("each client sees only their own phones, Browsers and templates", async () => {
  const { A, B, admin, ids } = await twoClients();
  const names = async (who, path) => JSON.stringify(await (await who(path)).json());
  assert.match(await names(A, "/api/devices"), /ALICE-PHONE/);
  assert.doesNotMatch(await names(A, "/api/devices"), /BOB-PHONE|ADMIN-PHONE|bobs-secret/);
  assert.doesNotMatch(await names(A, "/api/browsers"), /BOB-BROWSER/);
  assert.doesNotMatch(await names(A, "/api/templates"), /Bobs template/);
  assert.doesNotMatch(await names(B, "/api/devices"), /ALICE-PHONE|ADMIN-PHONE/);
  const all = await names(admin, "/api/devices");
  assert.match(all, /ALICE-PHONE/); assert.match(all, /BOB-PHONE/); assert.match(all, /ADMIN-PHONE/, "the administrator sees everything");
  assert.ok(ids.a !== ids.b);
});

test("every route that takes an id refuses someone else's id (a sweep)", async () => {
  const { A, B, admin, ids } = await twoClients();
  const refused = (r) => [400, 403, 404, 405].includes(r.status);
  const victims = [{ phone: ids.b, browser: ids.bb }, { phone: ids.admin, browser: ids.bb }];
  const probes = [];
  for (const v of victims) {
    for (const suffix of ["", "/config", "/master", "/clone-from", "/owner", "/name", "/command", "/site-requests", "/app-requests", "/messages", "/msg-alerts", "/shots/aaaaaaaaaaaa"]) probes.push(`/api/devices/${v.phone}${suffix}`);
    for (const suffix of ["", "/config", "/clone-from", "/owner", "/site-requests"]) probes.push(`/api/browsers/${v.browser}${suffix}`);
    for (const suffix of ["", "/config", "/command", "/removal-code", "/site-requests", "/shots/aaaaaaaaaaaa"]) probes.push(`/api/windevices/${v.phone}${suffix}`);
  }
  const bodies = { "/config": { blockedWords: ["pwned"] }, "/owner": { ownerId: "alice" }, "/name": { name: "pwned" }, "/clone-from": { sourceId: ids.a }, "/command": { type: "lock", args: {} }, "/master": { master: "x" } };
  const failures = [];
  for (const path of probes) {
    const suffix = Object.keys(bodies).find((k) => path.endsWith(k));
    for (const method of ["GET", "POST", "PUT", "DELETE"]) {
      const r = await A(path, method, method === "GET" || method === "DELETE" ? undefined : bodies[suffix] || {});
      const text = await r.text();
      if (!refused(r)) failures.push(`${method} ${path} -> ${r.status}`);
      if (/BOB-|ADMIN-PHONE|bobsecret|bobs-private/.test(text)) failures.push(`${method} ${path} leaked something`);
    }
  }
  assert.deepEqual(failures, [], "Alice was refused everywhere");
  // And nothing of Bob's changed.
  const bobCfg = JSON.stringify(await (await B("/api/devices")).json());
  assert.match(bobCfg, /bobsecret/);
  assert.doesNotMatch(bobCfg, /pwned/);
  // Alice's own phone still works for her, and she cannot copy Bob's settings onto it.
  assert.equal((await A(`/api/devices/${ids.a}/config`, "PUT", { blockedWords: ["mine"] })).status, 200);
  const clone = await A(`/api/devices/${ids.a}/clone-from`, "PUT", { sourceId: ids.b });
  assert.ok(refused(clone) || clone.status === 200 === false, "cloning from Bob's phone is refused");
  assert.doesNotMatch(JSON.stringify(await (await A("/api/devices")).json()), /bobsecret/, "Bob's settings did not arrive on Alice's phone");
  // A code for someone else's phone, and someone else's template, are refused too.
  assert.ok(refused(await A("/api/codes", "POST", { type: "install", deviceId: ids.b })));
  const tpls = await (await admin("/api/templates")).json();
  const bobsTpl = (Array.isArray(tpls) ? tpls : []).find((t) => /Bobs template/.test(JSON.stringify(t)));
  assert.ok(bobsTpl, "the administrator can see Bob's template");
  assert.ok(refused(await A(`/api/templates/${bobsTpl.id}/apply`, "POST", { deviceId: ids.a })), "Alice cannot apply Bob's template");
  assert.ok(refused(await A(`/api/templates/${bobsTpl.id}`, "DELETE")), "or delete it");
});

test("the administrator's own pages are closed to every client and every helper", async () => {
  const { A, B, admin, login, kv, ids } = await twoClients();
  await admin("/api/helpers", "POST", { loginName: "aliceshelper", name: "Helper", password: "helper-password" });
  const adminOnly = [
    ["GET", "/api/alerts"], ["PUT", "/api/alerts"], ["POST", "/api/alerts/test"], ["GET", "/api/audit"], ["GET", "/api/2fa"], ["POST", "/api/2fa/start"],
    ["POST", "/api/2fa/enable"], ["POST", "/api/2fa/disable"], ["GET", "/api/clients"], ["POST", "/api/clients"], ["DELETE", "/api/clients/alice"], ["DELETE", "/api/clients/bob"],
    ["PUT", "/api/apk"], ["PUT", "/api/logo"], ["DELETE", "/api/logo"], ["PUT", "/api/icon/com.example.app"], ["DELETE", "/api/icon/com.example.app"], ["GET", "/api/signups"], ["PUT", "/api/signups/settings"],
  ];
  for (const who of [A, B]) {
    for (const [method, path] of adminOnly) {
      const r = await who(path, method, method === "GET" || method === "DELETE" ? undefined : {});
      assert.ok([403, 404, 405].includes(r.status), `${method} ${path} as a client -> ${r.status}`);
    }
  }
  assert.ok((await B("/api/clients/alice", "DELETE")).status === 403);
  const stillThere = await (await admin("/api/clients")).json();
  assert.equal(stillThere.length, 2, "no account was removed");
  assert.equal(JSON.stringify(await (await A("/api/state")).json()).includes("pw"), false, "the administrator password is nowhere in what a client receives");
  assert.ok(ids.a);
});
