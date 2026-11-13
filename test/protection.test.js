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

test("web pages inside apps: cleaned and sent to the phone", () => {
  const c = normalizeConfig({ inAppBrowsers: "all", inAppExceptions: ["com.bank", "bad name"] });
  assert.equal(normalizeConfig({ inAppBrowsers: "everything" }).inAppBrowsers, "off");
  const p = buildAgentPolicy(c, []);
  assert.equal(p.inAppBrowsers, "all");
  assert.deepEqual(p.inAppExceptions, ["com.bank"]);
  assert.ok(summarizeConfigChange(normalizeConfig({}), c).includes("Web pages inside apps: all closed"));
});

test("'turn off all restrictions for a while': cleaned, capped at 24 hours, sent to the phone, noted in the change summary, never copied into a template", () => {
  const soon = Date.now() + 3600000;
  const c = normalizeConfig({ restrictionsOffUntil: soon, restrictionsOffSince: Date.now() });
  assert.equal(c.restrictionsOffUntil, Math.floor(soon));
  assert.equal(normalizeConfig({ restrictionsOffUntil: Date.now() + 10 * 24 * 3600000 }).restrictionsOffUntil <= Date.now() + 24 * 3600000, true, "never more than a day ahead");
  assert.equal(normalizeConfig({ restrictionsOffUntil: -3 }).restrictionsOffUntil, 0);
  assert.equal(normalizeConfig({ restrictionsOffUntil: "soon" }).restrictionsOffUntil, 0);
  assert.equal(buildAgentPolicy(c, []).restrictionsOffUntil, Math.floor(soon));
  assert.ok(summarizeConfigChange(normalizeConfig({}), c).includes("ALL restrictions turned off for a while"));
  assert.ok(summarizeConfigChange(c, normalizeConfig({})).includes("Restrictions back on"));
});

test("YouTube Music: pictures show for approved songs by default, and songs allowed together keep the artist or album they came from", () => {
  assert.equal(normalizeConfig({}).musicPictures, "allowed", "approved songs show their art by default");
  assert.equal(normalizeConfig({ musicPictures: "show" }).musicPictures, "show");
  assert.equal(normalizeConfig({ musicPictures: "hide" }).musicPictures, "hide");
  assert.equal(normalizeConfig({ musicPictures: "whatever" }).musicPictures, "allowed");
  const c = normalizeConfig({ musicPictures: "show", sites: { a: { type: "exact", url: "https://music.youtube.com/watch?v=abcdefghijk", label: "A song", group: "  Avraham Fried  " }, b: { type: "exact", url: "https://music.youtube.com/watch?v=lmnopqrstuv", label: "Another", group: 5 } } });
  const p = buildAgentPolicy(c, []);
  assert.equal(p.musicPictures, "show");
  const songs = p.sites.filter((s) => s.host === "music.youtube.com");
  assert.equal(songs.length, 2);
  assert.equal(songs.find((s) => s.label === "A song").group, "Avraham Fried");
  assert.equal(songs.find((s) => s.label === "Another").group, undefined, "a group must be a name");
});

test("downloads: the phone reports what is downloaded, the dashboard can ask it to delete some, one kind, or all", async () => {
  const login = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  const cookie = login.headers.get("set-cookie").split(";")[0];
  const dev = (await (await req("/api/devices", { headers: { cookie } })).json())[0];
  const { code } = await (await req("/api/codes", { method: "POST", headers: { cookie, ...J }, body: JSON.stringify({ type: "enroll" }) })).json();
  const { token } = await (await req("/agent/enroll", { method: "POST", headers: J, body: JSON.stringify({ code, info: { model: "Dl" } }) })).json();
  const sync = (body) => req("/agent/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${token}` }, body: JSON.stringify(body) });
  await sync({});
  await sync({ downloads: [{ id: "abcdefghijk", t: "A song", k: "music", at: 1000, bytes: 4194304 }, { id: "lmnopqrstuv", t: "A video", k: "video", at: 2000, bytes: 9000000 }, { id: "bad id", t: "broken" }, "nope"] });
  const d = (await (await req("/api/devices", { headers: { cookie } })).json()).find((x) => x.name.endsWith("Dl"));
  assert.deepEqual(d.downloads.map((x) => [x.id, x.k, x.bytes]), [["abcdefghijk", "music", 4194304], ["lmnopqrstuv", "video", 9000000]], "broken entries are dropped");
  assert.ok(d.downloadsAt > 0);
  const queue = async (args) => (await req(`/api/devices/${d.id}/command`, { method: "POST", headers: { cookie, ...J }, body: JSON.stringify({ type: "deleteDownloads", args }) })).status;
  assert.equal(await queue({ ids: ["abcdefghijk", "../../etc", 5] }), 200);
  assert.equal(await queue({ kind: "music" }), 200);
  assert.equal(await queue({ all: true }), 200);
  const reply = await (await sync({})).json();
  const cmds = reply.commands.filter((c) => c.type === "deleteDownloads").map((c) => c.args);
  assert.deepEqual(cmds, [{ ids: ["abcdefghijk"] }, { kind: "music" }, { all: true }], "only well-formed ids reach the phone");
});

test("find a screen by name: the dashboard gets the command id back, a short name is refused, and the phone's answer is found by that id", async () => {
  const login = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  const cookie = login.headers.get("set-cookie").split(";")[0];
  const { code } = await (await req("/api/codes", { method: "POST", headers: { cookie, ...J }, body: JSON.stringify({ type: "enroll" }) })).json();
  const { token } = await (await req("/agent/enroll", { method: "POST", headers: J, body: JSON.stringify({ code, info: { model: "Fs" } }) })).json();
  const sync = (body) => req("/agent/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${token}` }, body: JSON.stringify(body) });
  await sync({});
  const d = (await (await req("/api/devices", { headers: { cookie } })).json()).find((x) => x.name.endsWith("Fs"));
  const send = (args) => req(`/api/devices/${d.id}/command`, { method: "POST", headers: { cookie, ...J }, body: JSON.stringify({ type: "findScreen", args }) });
  assert.equal((await send({ name: " x " })).status, 400, "a one-letter name is refused");
  const ok = await send({ name: "  Software   update  " });
  assert.equal(ok.status, 200);
  const { id } = await ok.json();
  assert.match(id, /^[0-9a-f]{12}$/, "the id comes back so the dashboard can find the answer");
  const reply = await (await sync({})).json();
  const cmd = reply.commands.find((c) => c.id === id);
  assert.deepEqual(cmd.args, { name: "Software update" }, "spaces are tidied before the phone sees the name");
  const answer = JSON.stringify({ name: "Software update", found: [{ p: "com.android.settings", a: "Settings", t: "Software update", k: "screen" }], similar: [] });
  await sync({ results: [{ id, type: "findScreen", ok: true, msg: answer }] });
  const after = (await (await req("/api/devices", { headers: { cookie } })).json()).find((x) => x.id === d.id);
  const res = after.results.find((r) => r.id === id);
  assert.equal(JSON.parse(res.msg).found[0].t, "Software update");
  // Other commands keep answering plain { ok: true }.
  const plain = await (await req(`/api/devices/${d.id}/command`, { method: "POST", headers: { cookie, ...J }, body: JSON.stringify({ type: "sync" }) })).json();
  assert.deepEqual(plain, { ok: true });
});

test("Open level: the whole web is open, only the chosen kinds are closed (adult sites at first), and YouTube is not one of them", () => {
  const c = normalizeConfig({ browserFilter: "custom" });
  assert.deepEqual(c.filterKinds, ["adult"], "nothing chosen yet closes adult sites only");
  const p = buildAgentPolicy(c, []);
  assert.equal(p.browserFilter, "custom");
  assert.deepEqual(p.filterExtras, ["adult"], "the phone reads the kinds from the same field the extras use");
  assert.equal(p.youtubeMode, "open", "YouTube is open in this level (the time limits are what apply to it)");
  const mixed = normalizeConfig({ browserFilter: "custom", filterKinds: ["gambling", "bogus", "news", "news", 5, "social"], filterExtras: ["shopping"] });
  assert.deepEqual(mixed.filterKinds, ["gambling", "news", "social"], "only known kinds, once each");
  assert.deepEqual(buildAgentPolicy(mixed, []).filterExtras, ["gambling", "news", "social"], "the extras of Medium and Strict do not leak into Open");
  assert.deepEqual(buildAgentPolicy(normalizeConfig({ browserFilter: "custom", filterKinds: [] }), []).filterExtras, [], "an empty list is allowed: nothing is closed");
  assert.deepEqual(buildAgentPolicy(normalizeConfig({ browserFilter: "moderate", filterExtras: ["news"], filterKinds: ["adult"] }), []).filterExtras, ["news"], "Medium still uses its own extras");
  assert.equal(normalizeConfig({ browserFilter: "bogus" }).browserFilter, "off", "an unknown level still means approved sites only");
  assert.ok(summarizeConfigChange(normalizeConfig({ browserFilter: "custom" }), normalizeConfig({ browserFilter: "custom", filterKinds: ["adult", "gambling"] })).includes("Open web, closed kinds: adult, gambling"));
  assert.equal(buildAgentPolicy(normalizeConfig({ browserFilter: "custom", youtubeMode: "restricted" }), []).youtubeMode, "restricted", "a YouTube setting chosen by hand still wins");
});

test("daily time per site: tidied, sent to the phone with the screen-time rules, and the day's use comes back with the check-in", async () => {
  const c = normalizeConfig({ screenTime: { siteLimits: { "https://www.YouTube.com/watch?v=1": 120, "music.youtube.com": 60, "bad host": 30, "nodot": 10, "zero.com": 0, "huge.com": 9999 } } });
  assert.deepEqual(c.screenTime.siteLimits, { "youtube.com": 120, "music.youtube.com": 60, "huge.com": 1440 }, "addresses become site names; junk and zero are dropped; a day has 1440 minutes");
  assert.deepEqual(buildAgentPolicy(c, []).screenTime.siteLimits, { "youtube.com": 120, "music.youtube.com": 60, "huge.com": 1440 }, "the phone gets them with the rest of screenTime");
  assert.deepEqual(normalizeConfig({}).screenTime.siteLimits, {}, "none by default");
  const before = normalizeConfig({}), after = normalizeConfig({ screenTime: { siteLimits: { "youtube.com": 120 } } });
  assert.ok(summarizeConfigChange(before, after).includes("Daily time per site: youtube.com 120 min"));
  const login = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  const cookie = login.headers.get("set-cookie").split(";")[0];
  const { code } = await (await req("/api/codes", { method: "POST", headers: { cookie, ...J }, body: JSON.stringify({ type: "enroll" }) })).json();
  const { token } = await (await req("/agent/enroll", { method: "POST", headers: J, body: JSON.stringify({ code, info: { model: "Sl" } }) })).json();
  const sync = (body) => req("/agent/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${token}` }, body: JSON.stringify(body) });
  await sync({});
  await sync({ usage: { day: "2026-11-13", total: 5, ok: true, apps: [], sites: [{ h: "youtube.com", m: 30, l: 120 }, { h: "bad host", m: 1, l: 1 }, "nope", { h: "music.youtube.com", m: 99999, l: 60 }] } });
  const d = (await (await req("/api/devices", { headers: { cookie } })).json()).find((x) => x.name.endsWith("Sl"));
  assert.deepEqual(d.usage["2026-11-13"].sites, [{ h: "youtube.com", m: 30, l: 120 }, { h: "music.youtube.com", m: 1440, l: 60 }], "broken entries are dropped and minutes are capped");
});

test("screens blocked on the phone (by the name of the screen) are kept in the settings, so the dashboard lists them and can remove them", () => {
  const c = normalizeConfig({ blockedComponents: ["com.example.app/com.example.app.PlayerActivity", "bad", "com.example.app/com.example.app.PlayerActivity"] });
  assert.deepEqual(c.blockedComponents, ["com.example.app/com.example.app.PlayerActivity"]);
  assert.deepEqual(buildAgentPolicy(c, []).blockedComponents, ["com.example.app/com.example.app.PlayerActivity"]);
  assert.deepEqual(normalizeConfig({}).blockedComponents, [], "none by default");
  assert.ok(summarizeConfigChange(normalizeConfig({}), c).includes("Blocked app screens (by name of the screen): 1"));
});
