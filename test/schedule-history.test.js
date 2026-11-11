import test from "node:test";
import assert from "node:assert/strict";
import worker, { addSiteHistory, siteOf } from "../src/index.js";
import { buildAgentPolicy, effectiveFilter, normalizeConfig, normalizeFilterSchedule } from "../src/policy.js";
import { maybeSendWeekly, weeklyReport } from "../src/alerts.js";

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
const at = (iso) => Date.parse(iso);

test("filter schedule: the scheduled level inside its hours, in the phone's own time", () => {
  const cfg = { browserFilter: "moderate", filterSchedule: { on: true, level: "strict", days: [1, 2, 3, 4, 5], from: "08:00", to: "15:00" } };
  assert.equal(effectiveFilter(cfg, at("2026-11-09T09:00:00Z"), 0), "strict", "Monday 9:00");
  assert.equal(effectiveFilter(cfg, at("2026-11-09T15:00:00Z"), 0), "moderate", "15:00 is the end");
  assert.equal(effectiveFilter(cfg, at("2026-11-08T09:00:00Z"), 0), "moderate", "Sunday is not on the list");
  assert.equal(effectiveFilter(cfg, at("2026-11-09T14:00:00Z"), -300), "strict", "New York: 14:00 UTC is 9:00 there");
  assert.equal(effectiveFilter(cfg, at("2026-11-09T07:30:00Z"), 120), "strict", "Israel: 07:30 UTC is 9:30 there");
  assert.equal(effectiveFilter({ ...cfg, filterSchedule: { ...cfg.filterSchedule, on: false } }, at("2026-11-09T09:00:00Z")), "moderate");
  const night = { browserFilter: "moderate", filterSchedule: { on: true, level: "off", days: [0], from: "22:00", to: "06:00" } };
  assert.equal(effectiveFilter(night, at("2026-11-08T23:00:00Z")), "off", "Sunday 23:00");
  assert.equal(effectiveFilter(night, at("2026-11-09T05:00:00Z")), "off", "Monday 05:00 is still Sunday night");
  assert.equal(effectiveFilter(night, at("2026-11-09T23:00:00Z")), "moderate", "Monday night is not on the list");
  assert.deepEqual(normalizeFilterSchedule({ on: true, level: "x", days: [9, 1, 1], from: "25:00" }), { on: true, level: "strict", days: [1], from: "08:00", to: "15:00" });
});

test("sites visited: grouped by site, background services left out, two weeks kept", () => {
  assert.equal(siteOf("m.youtube.com"), "youtube.com");
  assert.equal(siteOf("www.ynet.co.il."), "ynet.co.il");
  assert.equal(siteOf("1.2.3.4"), null);
  const t = at("2026-11-09T10:00:00Z");
  let h = addSiteHistory({}, [
    { kind: "dns", host: "www.youtube.com", time: t, app: "Chrome" },
    { kind: "tls", host: "m.youtube.com", time: t, app: "Chrome" },
    { kind: "dns", host: "bet365.com", time: t, app: "Chrome", blocked: true },
    { kind: "dns", host: "play.googleapis.com", time: t },
    { kind: "net", host: "1.2.3.4:443", time: t },
  ]);
  assert.deepEqual(h, { "2026-11-09": { "youtube.com": [2, 0, "Chrome"], "bet365.com": [1, 1, "Chrome"] } });
  for (let i = 1; i <= 20; i++) h = addSiteHistory(h, [{ kind: "dns", host: "a.com", time: t + i * 86400000 }]);
  assert.equal(Object.keys(h).length, 14);
  assert.ok(!h["2026-11-09"], "the oldest days are dropped");
});

test("the weekly report says what happened, in plain words", () => {
  const now = at("2026-11-15T18:00:00Z");
  const text = weeklyReport([{
    name: "Pixel", lastSeen: now - 1000,
    usage: { "2026-11-15": { total: 120, apps: [{ p: "a", l: "YouTube", m: 60 }] }, "2026-11-14": { total: 60, apps: [{ p: "a", l: "YouTube", m: 30 }] }, "2026-10-01": { total: 999 } },
    siteHistory: { "2026-11-14": { "youtube.com": [5, 0, ""], "bet365.com": [2, 2, ""] } },
    calls: [{ at: now - 1000, type: "blocked" }, { at: now - 2000, type: "in" }],
    events: [{ k: "sos", m: "Asked for help from the phone", at: now - 5000 }, { k: "sos", m: "Help request location: https://maps.google.com/?q=1,2", at: now - 4000 }],
  }, { name: "Old", lastSeen: now - 30 * 86400000 }], now);
  assert.match(text, /Pixel\n- Screen time: about 1 h 30 min a day \(most: YouTube 1 h 30 min\)/);
  assert.match(text, /2 visits to sites, 1 blocked \(bet365\.com\)/);
  assert.match(text, /Calls: 2, 1 blocked/);
  assert.match(text, /Asked for help: 1 time\n/);
  assert.match(text, /Old\n- Did not check in this week/);
});

test("weekly report goes out once, Sunday evening, only when alerts are set up", async () => {
  const sent = [];
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (u, init) => { sent.push(String(init && init.body)); return new Response("ok"); };
  try {
    const dev = [{ name: "Pixel", lastSeen: at("2026-11-15T17:00:00Z") }];
    assert.equal((await maybeSendWeekly(env, dev, at("2026-11-15T18:00:00Z"))).sent, false, "no alert address yet");
    kv.set("alerts", JSON.stringify({ url: "https://ntfy.sh/x" }));
    assert.equal((await maybeSendWeekly(env, dev, at("2026-11-14T18:00:00Z"))).sent, false, "Saturday");
    assert.equal((await maybeSendWeekly(env, dev, at("2026-11-15T16:00:00Z"))).sent, false, "too early on Sunday");
    assert.equal((await maybeSendWeekly(env, dev, at("2026-11-15T17:05:00Z"))).sent, true);
    assert.equal((await maybeSendWeekly(env, dev, at("2026-11-15T17:20:00Z"))).sent, false, "only once");
    assert.equal(sent.length, 1);
    assert.match(sent[0], /Pixel/);
  } finally {
    globalThis.fetch = realFetch;
    kv.delete("alerts");
  }
});

test("phones check in every 30 s while the dashboard is in use; the schedule and per-app modes reach the phone", async () => {
  const { code } = await (await req("/api/codes", { method: "POST", headers: { ...J, cookie: await cookie() }, body: JSON.stringify({ type: "enroll" }) })).json();
  const { token } = await (await req("/agent/enroll", { method: "POST", headers: J, body: JSON.stringify({ code, info: { model: "Sched" } }) })).json();
  const sync = async (info) => (await req("/agent/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${token}` }, body: JSON.stringify(info ? { info } : {}) })).json();
  kv.set("adminActive", String(Date.now() - 20 * 60 * 1000));
  assert.equal((await sync()).pollSeconds, 300, "nobody on the dashboard: every 5 minutes");
  const c = await cookie();
  const dev = (await (await req("/api/devices", { headers: { cookie: c } })).json()).find((d) => d.name.endsWith("Sched"));
  assert.equal((await sync()).pollSeconds, 30, "dashboard open: every 30 seconds");

  // A schedule that is on all day, every day: the phone gets its level now.
  await req(`/api/devices/${dev.id}/config`, { method: "PUT", headers: { cookie: c, ...J }, body: JSON.stringify({ browserFilter: "moderate", filterSchedule: { on: true, level: "strict", days: [0, 1, 2, 3, 4, 5, 6], from: "00:00", to: "00:00" }, filterSkipApps: ["com.bank"], recordSites: true }) });
  const r = await sync({ tz: 120 });
  assert.equal(r.policy.browserFilter, "strict");
  assert.deepEqual(r.policy.filterSkipApps, ["com.bank"]);
  assert.equal(r.policy.recordSites, true);
  const after = (await (await req("/api/devices", { headers: { cookie: c } })).json()).find((d) => d.id === dev.id);
  assert.equal(after.config.browserFilter, "moderate", "the saved level itself is unchanged");
  assert.equal(buildAgentPolicy(normalizeConfig({}), []).recordSites, false);
});

async function cookie() {
  const login = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  return login.headers.get("set-cookie").split(";")[0];
}
