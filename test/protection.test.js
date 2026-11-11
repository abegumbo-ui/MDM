import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { buildAgentPolicy, distanceMeters, normalizeConfig, normalizePlaces, placesAt, summarizeConfigChange, USER_MENU } from "../src/policy.js";

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

test("pause, internet-only apps, image search and location settings are cleaned and reach the phone", () => {
  const c = normalizeConfig({ pauseUntil: "1700000000000.7", internetOnlyApps: true, internetApps: ["com.whatsapp", "com.whatsapp", "bad name!", 5], blockImageSearch: true, locationEvery: 30 });
  assert.equal(c.pauseUntil, 1700000000000);
  assert.deepEqual(c.internetApps, ["com.whatsapp"]);
  assert.equal(normalizeConfig({ locationEvery: 7 }).locationEvery, 0, "only 15, 30 or 60 minutes");
  assert.equal(normalizeConfig({ pauseUntil: -5 }).pauseUntil, 0);
  const p = buildAgentPolicy(c, []);
  assert.equal(p.pauseUntil, 1700000000000);
  assert.equal(p.internetOnlyApps, true);
  assert.deepEqual(p.internetApps, ["com.whatsapp"]);
  assert.equal(p.blockImageSearch, true);
  assert.equal(p.locationEvery, 30);
  const lines = summarizeConfigChange(normalizeConfig({}), c);
  for (const l of ["Image search: blocked", "Web filtering paused for a while", "Internet only for chosen apps: on (1 apps)", "Location history: every 30 minutes"]) assert.ok(lines.includes(l), l);
  assert.ok(USER_MENU.sos, "the phone menu has the help button");
  assert.equal(normalizeConfig({}).userMenu.sos, true, "shown unless switched off");
});

test("places: cleaned, measured in metres, and matched", () => {
  assert.deepEqual(normalizePlaces([{ name: " Home ", lat: 40.7, lon: -74, r: 10 }, { name: "", lat: 1, lon: 1 }, { name: "Bad", lat: 99, lon: 0 }, null]), [{ name: "Home", lat: 40.7, lon: -74, r: 50 }]);
  const d = distanceMeters(40.7, -74, 40.701, -74);
  assert.ok(d > 100 && d < 120, "0.001 degrees of latitude is about 111 m, got " + d);
  const places = [{ name: "Home", lat: 40.7, lon: -74, r: 200 }, { name: "School", lat: 40.8, lon: -74, r: 200 }];
  assert.deepEqual(placesAt(places, { lat: 40.7005, lon: -74 }).map((p) => p.name), ["Home"]);
  assert.deepEqual(placesAt(places, { lat: 40.75, lon: -74 }), []);
});

test("location history: kept on the dashboard, arrive/leave noted, SOS always alerted", async () => {
  const sent = [];
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (url, init) => { sent.push(String(init && init.body)); return new Response("ok"); };
  try {
    const login = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
    const cookie = login.headers.get("set-cookie").split(";")[0];
    // Alerts on, but "places" and "tamper" off: the help request must still go out.
    await req("/api/alerts", { method: "PUT", headers: { cookie, ...J }, body: JSON.stringify({ url: "https://ntfy.sh/test-topic", places: false, tamper: false }) });
    const { code } = await (await req("/api/codes", { method: "POST", headers: { ...J, cookie }, body: JSON.stringify({ type: "enroll" }) })).json();
    const { token } = await (await req("/agent/enroll", { method: "POST", headers: J, body: JSON.stringify({ code, info: { model: "Loc" } }) })).json();
    const sync = (body) => req("/agent/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${token}` }, body: JSON.stringify(body) });
    await sync({});
    const dev = (await (await req("/api/devices", { headers: { cookie } })).json()).find((d) => d.name.endsWith("Loc"));
    await req(`/api/devices/${dev.id}/config`, { method: "PUT", headers: { cookie, ...J }, body: JSON.stringify({ locationEvery: 15, places: [{ name: "Home", lat: 40.7, lon: -74, r: 200 }] }) });

    await sync({ locations: [{ lat: 40.7001, lon: -74, accuracy: 20, at: 1000 }] }); // starting point: no "arrived"
    await sync({ locations: [{ lat: 40.75, lon: -74, accuracy: 20, at: 2000 }, { lat: "x", lon: 0 }] });
    await sync({ locations: [{ lat: 40.7002, lon: -74, accuracy: 900, at: 2500 }] }); // too rough to count
    await sync({ locations: [{ lat: 40.7002, lon: -74, accuracy: 15, at: 3000 }] });
    const d = (await (await req("/api/devices", { headers: { cookie } })).json()).find((x) => x.id === dev.id);
    assert.equal(d.locations.length, 4, "the bad entry is dropped");
    assert.deepEqual(d.events.filter((e) => e.k === "place").map((e) => e.m), ["Left Home", "Arrived at Home"]);
    assert.ok(!sent.some((b) => b.includes("Home")), "place alerts are off here");

    await sync({ events: [{ k: "sos", m: "Asked for help from the phone", at: 4000 }] });
    await new Promise((r) => setTimeout(r, 20));
    assert.ok(sent.some((b) => b.includes("SOS: Asked for help")), "the help request is sent even with other alerts off");
  } finally {
    globalThis.fetch = realFetch;
  }
});

test("a template never carries a pause", async () => {
  const login = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  const cookie = login.headers.get("set-cookie").split(";")[0];
  const dev = (await (await req("/api/devices", { headers: { cookie } })).json())[0];
  await req(`/api/devices/${dev.id}/config`, { method: "PUT", headers: { cookie, ...J }, body: JSON.stringify({ ...dev.config, pauseUntil: Date.now() + 3600000 }) });
  await req("/api/templates", { method: "POST", headers: { cookie, ...J }, body: JSON.stringify({ name: "No pause", deviceId: dev.id }) });
  const t = (await (await req("/api/templates", { headers: { cookie } })).json()).find((x) => x.name === "No pause");
  assert.ok(!t.summary.some((l) => l.includes("paused")));
});

test("calls: settings cleaned and sent to the phone; call and texts logs kept on the dashboard", async () => {
  const { normalizeCallNumbers } = await import("../src/policy.js");
  assert.deepEqual(normalizeCallNumbers([{ name: "Mom", number: "+1 (212) 555-1234" }, { number: "212-555-1234" }, { number: "12" }, { name: "Star", number: "*611" }, "x"]),
    [{ name: "Mom", number: "+12125551234" }, { name: "Star", number: "*611" }]);
  const c = normalizeConfig({ callsIn: "list", callsOut: "nonsense", callNumbers: [{ name: "Mom", number: "052-123-4567" }], callLog: true });
  assert.equal(c.callsOut, "all");
  const p = buildAgentPolicy(c, []);
  assert.equal(p.callsIn, "list");
  assert.deepEqual(p.callNumbers, ["0521234567"]);
  assert.equal(p.callLog, true);
  assert.equal(p.textLog, false);
  const lines = summarizeConfigChange(normalizeConfig({}), c);
  for (const l of ["Who can call: only the list", "Allowed numbers: 1", "Call log: on"]) assert.ok(lines.includes(l), l);

  const login = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  const cookie = login.headers.get("set-cookie").split(";")[0];
  const { code } = await (await req("/api/codes", { method: "POST", headers: { ...J, cookie }, body: JSON.stringify({ type: "enroll" }) })).json();
  const { token } = await (await req("/agent/enroll", { method: "POST", headers: J, body: JSON.stringify({ code, info: { model: "Caller" } }) })).json();
  const sync = (body) => req("/agent/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${token}` }, body: JSON.stringify(body) });
  await sync({
    calls: [{ number: "0521234567", name: "Mom", type: "in", at: 5, secs: 61 }, { number: "999", type: "weird", at: 6 }],
    texts: [{ number: "0521234567", type: "in", at: 7, text: "hi", secs: 3 }],
  });
  const d = (await (await req("/api/devices", { headers: { cookie } })).json()).find((x) => x.name.endsWith("Caller"));
  assert.deepEqual(d.calls, [{ number: "0521234567", name: "Mom", type: "in", at: 5, secs: 61 }, { number: "999", type: "other", at: 6 }]);
  assert.deepEqual(d.texts, [{ number: "0521234567", type: "in", at: 7, text: "hi" }]);
});
