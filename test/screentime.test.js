import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { buildAgentPolicy, normalizeConfig, normalizeScreenTime, summarizeConfigChange } from "../src/policy.js";

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

test("screen time settings are off by default and cleaned up", () => {
  assert.deepEqual(normalizeConfig({}).screenTime, { dailyLimit: 0, weekendLimit: 0, weekendDays: [0, 6], bedtime: null, schoolTime: null, schoolBreak: null, appLimits: {}, siteLimits: {}, exempt: [] });
  const st = normalizeScreenTime({
    dailyLimit: 99999,
    bedtime: { days: [0, 1, 9, 1], from: "22:00", to: "07:00" },
    appLimits: { "com.instagram.android": 30, "bad pkg": 10, "com.zero.app": 0, "com.text.app": "20" },
    exempt: ["com.android.dialer", "com.android.dialer", "no good!", 5],
  });
  assert.equal(st.dailyLimit, 1440, "capped at a day");
  assert.deepEqual(st.bedtime, { days: [0, 1], from: "22:00", to: "07:00" });
  assert.deepEqual(st.appLimits, { "com.instagram.android": 30 });
  assert.deepEqual(st.exempt, ["com.android.dialer"]);
  assert.equal(normalizeScreenTime({ bedtime: { days: [1], from: "22:00", to: "22:00" } }).bedtime, null, "an empty window is no bedtime");
  assert.equal(normalizeScreenTime({ dailyLimit: -5 }).dailyLimit, 0);
  assert.deepEqual(buildAgentPolicy({ screenTime: { dailyLimit: 120 } }, []).screenTime, { dailyLimit: 120, weekendLimit: 0, weekendDays: [0, 6], bedtime: null, schoolTime: null, schoolBreak: null, appLimits: {}, siteLimits: {}, exempt: [] });
  const lines = summarizeConfigChange(normalizeConfig({}), normalizeConfig({ screenTime: { dailyLimit: 120, bedtime: { days: [1], from: "21:30", to: "06:30" }, appLimits: { "a.b": 15 } } }));
  assert.ok(lines.includes("Daily screen time: 120 minutes"));
  assert.ok(lines.includes("Bedtime: 21:30 to 06:30"));
  assert.ok(lines.includes("App time limits changed (1 app(s))"));
});

test("the phone's usage report is kept for two weeks, and extra time can be asked for and given", async () => {
  const login = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  const cookie = login.headers.get("set-cookie").split(";")[0];
  const { code } = await (await req("/api/codes", { method: "POST", headers: { ...J, cookie }, body: JSON.stringify({ type: "enroll" }) })).json();
  const { token } = await (await req("/agent/enroll", { method: "POST", headers: J, body: JSON.stringify({ code, info: { model: "T" } }) })).json();
  const auth = { ...J, authorization: `Bearer ${token}` };
  const sync = async (body) => await (await req("/agent/sync", { method: "POST", headers: auth, body: JSON.stringify(body) })).json();
  const device = async () => (await (await req("/api/devices", { headers: { cookie } })).json())[0];

  for (let i = 1; i <= 16; i++) {
    await sync({ usage: { day: `2026-11-${String(i).padStart(2, "0")}`, total: i * 10, ok: true, apps: [{ p: "com.a.b", l: "App", m: i * 10 }, { p: "bad pkg", m: 5 }] } });
  }
  await sync({ usage: { day: "not-a-day", total: 5 } });
  await sync({ usage: { day: "2026-11-16", total: 999999, ok: false, apps: [] }, appRequests: [{ kind: "time" }, { kind: "time" }] });
  const d = await device();
  const days = Object.keys(d.usage).sort();
  assert.equal(days.length, 14, "only the last 14 days are kept");
  assert.equal(days[0], "2026-11-03");
  assert.deepEqual(d.usage["2026-11-10"].apps, [{ p: "com.a.b", l: "App", m: 100 }], "bad app entries are dropped");
  assert.equal(d.usage["2026-11-16"].total, 1440, "a day cannot be longer than a day");
  assert.equal(d.usage["2026-11-16"].ok, false, "the phone saying it cannot count is passed on");
  assert.deepEqual(d.appRequests.map((r) => r.key), ["time"], "one request for more time, not two");

  const give = (minutes) => req(`/api/devices/${d.id}/command`, { method: "POST", headers: { ...J, cookie }, body: JSON.stringify({ type: "addTime", args: { minutes } }) });
  await give(30);
  await give(99999);
  await give("lots");
  const cmds = (await sync({})).commands;
  assert.deepEqual(cmds.map((c) => [c.type, c.args.minutes]), [["addTime", 30], ["addTime", 240], ["addTime", 15]], "extra time is between 5 minutes and 4 hours");
});
