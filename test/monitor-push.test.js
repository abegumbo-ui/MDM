import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { buildAgentPolicy, normalizeConfig, matchMessageWord, messageWordList } from "../src/policy.js";
import { validPushEndpoint } from "../src/alerts.js";

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

test("message words: whole words only, groups plus the parent's own words, only sent when switched on", () => {
  assert.equal(matchMessageWord("you should KILL   yourself!!", ["kill yourself"]), "kill yourself");
  assert.equal(matchMessageWord("I have class today", ["ass"]), null);
  assert.equal(matchMessageWord("you're ugly", ["you're ugly"]), "you're ugly");
  const cfg = normalizeConfig({ messageAlerts: true, messageGroups: ["drugs"], messageWords: "Secret Word, other" });
  const words = messageWordList(cfg);
  assert.ok(words.includes("weed") && words.includes("secret word") && !words.includes("nudes"));
  assert.deepEqual(buildAgentPolicy(normalizeConfig({ messageWords: ["x1"] }), []).messageWords, [], "off: the phone gets no words");
  assert.equal(normalizeConfig({}).messageGroups.length, 5, "every group is chosen by default");
});

test("message alerts from the phone and from texts are kept; screenshots are stored and shown only to the owner", async () => {
  const admin = await login({ password: env.ADMIN_PASSWORD });
  const { token, dev } = await enroll(admin, "Kid");
  const sync = (body) => req("/agent/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${token}` }, body: JSON.stringify(body) });
  // Off: nothing is kept.
  await sync({ msgAlerts: [{ app: "com.whatsapp", word: "nudes", text: "send nudes", at: 1 }] });
  assert.equal((((await (await req("/api/devices", { headers: { cookie: admin } })).json()).find((x) => x.id === dev.id)).msgAlerts || []).length, 0);
  await req(`/api/devices/${dev.id}/config`, { method: "PUT", headers: { cookie: admin, ...J }, body: JSON.stringify({ messageAlerts: true, textLog: true, textContent: true }) });
  const r = await (await sync({ msgAlerts: [{ app: "com.whatsapp", word: "nudes", text: "send nudes", at: 2 }], texts: [{ number: "555", type: "in", text: "lets get high later", at: 3 }, { number: "556", type: "in", text: "hi", at: 4 }] })).json();
  assert.equal(r.policy.messageAlerts, true);
  assert.ok(r.policy.messageWords.includes("nudes"));
  const d = (await (await req("/api/devices", { headers: { cookie: admin } })).json()).find((x) => x.id === dev.id);
  assert.deepEqual(d.msgAlerts.map((m) => [m.app, m.word]), [["com.whatsapp", "nudes"], ["texts", "get high"]]);
  assert.equal((await req(`/api/devices/${dev.id}/msg-alerts`, { method: "DELETE", headers: { cookie: admin } })).status, 200);

  // Screenshot: asked for as a command, the phone sends it, the dashboard shows it.
  assert.equal((await req(`/api/devices/${dev.id}/command`, { method: "POST", headers: { cookie: admin, ...J }, body: JSON.stringify({ type: "screenshot" }) })).status, 200);
  const jpeg = Buffer.from(new Uint8Array(300).fill(7)).toString("base64");
  assert.equal((await req("/agent/screenshot", { method: "POST", headers: { ...J, authorization: `Bearer ${token}` }, body: JSON.stringify({ jpeg, app: "com.whatsapp" }) })).status, 200);
  assert.equal((await req("/agent/screenshot", { method: "POST", headers: J, body: JSON.stringify({ jpeg }) })).status, 401, "only an enrolled phone");
  const d2 = (await (await req("/api/devices", { headers: { cookie: admin } })).json()).find((x) => x.id === dev.id);
  assert.equal(d2.shots.length, 1);
  const img = await req(`/api/devices/${dev.id}/shots/${d2.shots[0].id}`, { headers: { cookie: admin } });
  assert.equal(img.headers.get("content-type"), "image/jpeg");
  assert.equal((await img.arrayBuffer()).byteLength, 300);
  await req("/api/clients", { method: "POST", headers: { cookie: admin, ...J }, body: JSON.stringify({ name: "Other", loginName: "other", password: "other-pass" }) });
  const other = await login({ loginName: "other", password: "other-pass" });
  assert.equal((await req(`/api/devices/${dev.id}/shots/${d2.shots[0].id}`, { headers: { cookie: other } })).status, 404, "another family cannot see it");
});

test("notifications on the parent's phone: sign up, alerts poke the push service, the service worker gets the newest alert", async () => {
  assert.ok(validPushEndpoint("https://fcm.googleapis.com/fcm/send/abc"));
  assert.ok(validPushEndpoint("https://web.push.apple.com/QAbc"));
  assert.ok(!validPushEndpoint("https://evil.example/fcm.googleapis.com"));
  const admin = await login({ password: env.ADMIN_PASSWORD });
  const { publicKey } = await (await req("/api/push/key", { headers: { cookie: admin } })).json();
  assert.equal(Buffer.from(publicKey, "base64url").length, 65, "an uncompressed P-256 key, as browsers expect");
  const endpoint = "https://fcm.googleapis.com/fcm/send/test-1";
  assert.equal((await req("/api/push/subscribe", { method: "POST", headers: { cookie: admin, ...J }, body: JSON.stringify({ endpoint: "https://x.example/p" }) })).status, 400);
  assert.equal((await (await req("/api/push/subscribe", { method: "POST", headers: { cookie: admin, ...J }, body: JSON.stringify({ endpoint }) })).json()).pushCount, 1);
  assert.equal((await (await req("/api/alerts", { headers: { cookie: admin } })).json()).pushCount, 1);

  const calls = [];
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (u, init) => {
    calls.push({ u: String(u), auth: init && init.headers && init.headers.authorization });
    return new Response(null, { status: 201 });
  };
  try {
    const r = await req("/api/alerts/test", { method: "POST", headers: { cookie: admin } });
    assert.equal(r.status, 200, "a test alert succeeds with only a phone signed up");
  } finally {
    globalThis.fetch = realFetch;
  }
  assert.equal(calls.length, 1);
  assert.equal(calls[0].u, endpoint);
  assert.match(calls[0].auth, /^vapid t=[\w-]+\.[\w-]+\.[\w-]+, k=[\w-]+$/);
  const jwt = JSON.parse(Buffer.from(calls[0].auth.split(" t=")[1].split(".")[1], "base64url"));
  assert.equal(jwt.aud, "https://fcm.googleapis.com");
  assert.equal(jwt.sub, "https://x.test", "the contact is the dashboard's own address");

  const latest = await (await req("/push/latest", { method: "POST", headers: J, body: JSON.stringify({ endpoint }) })).json();
  assert.equal(latest.title, "MDM test alert");
  assert.deepEqual(await (await req("/push/latest", { method: "POST", headers: J, body: JSON.stringify({ endpoint: "https://fcm.googleapis.com/fcm/send/other" }) })).json(), {}, "not for an address that is not signed up");
  assert.match(await (await req("/sw.js")).text(), /showNotification/);
  await req("/api/push/unsubscribe", { method: "POST", headers: { cookie: admin, ...J }, body: JSON.stringify({ endpoint }) });
  assert.equal((await (await req("/api/alerts", { headers: { cookie: admin } })).json()).pushCount, 0);
});

test("searches typed on the phone are kept only while the switch is on", async () => {
  const admin = await login({ password: env.ADMIN_PASSWORD });
  const { token, dev } = await enroll(admin, "Searcher");
  const sync = (body) => req("/agent/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${token}` }, body: JSON.stringify(body) });
  const list = async () => (await (await req("/api/devices", { headers: { cookie: admin } })).json()).find((x) => x.id === dev.id);
  await sync({ searches: [{ app: "com.android.chrome", q: "math homework", at: 1 }] });
  assert.equal((await list()).searches.length, 0, "off: nothing kept");
  const d = await list();
  await req(`/api/devices/${dev.id}/config`, { method: "PUT", headers: { cookie: admin, ...J }, body: JSON.stringify({ ...d.config, recordSearches: true }) });
  const r = await (await sync({ searches: [{ app: "com.google.android.youtube", q: "  minecraft  ", at: 2 }, { q: "" }] })).json();
  assert.equal(r.policy.recordSearches, true);
  assert.deepEqual((await list()).searches.map((s) => s.q), ["minecraft"]);
});
