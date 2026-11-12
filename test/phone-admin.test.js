import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

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

test("settings changed on the phone's Administrator screen reach the dashboard, once, and only allowed ones", async () => {
  const r = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  const cookie = r.headers.get("set-cookie").split(";")[0];
  const { code } = await (await req("/api/codes", { method: "POST", headers: { ...J, cookie }, body: JSON.stringify({ type: "enroll" }) })).json();
  const { token } = await (await req("/agent/enroll", { method: "POST", headers: J, body: JSON.stringify({ code, info: { model: "Kid" } }) })).json();
  const sync = async (body) => (await req("/agent/sync", { method: "POST", headers: { ...J, authorization: `Bearer ${token}` }, body: JSON.stringify(body) })).json();
  const dev = async () => (await (await req("/api/devices", { headers: { cookie } })).json())[0];

  const first = await sync({});
  assert.equal(first.config.safeSearch, false, "the phone gets the dashboard's settings");
  assert.equal(first.configPatchRev, 0);

  const after = await sync({ configPatch: { rev: 3, values: { safeSearch: true, browserFilter: "strict", messageAlerts: true, apps: { "com.evil": { mode: "allow" } }, phoneAdminLocked: true } } });
  assert.equal(after.configPatchRev, 3);
  assert.equal(after.config.safeSearch, true);
  assert.equal(after.config.browserFilter, "strict");
  assert.equal(after.policy.messageAlerts, true, "the phone's own policy follows at once");
  const d = await dev();
  assert.equal(d.config.apps["com.evil"], undefined, "app modes are not changed this way");
  assert.equal(d.config.phoneAdminLocked, false, "nor the switch that locks the phone's own Administrator screen");
  assert.ok(d.pushLog.some((p) => p.changes.some((c) => c.startsWith("On the phone: "))), "shown in the dashboard log");

  await sync({ configPatch: { rev: 3, values: { safeSearch: false } } });
  assert.equal((await dev()).config.safeSearch, true, "the same change number is taken only once");

  // "Only control from the dashboard": the phone's changes are ignored.
  await req(`/api/devices/${d.id}/config`, { method: "PUT", headers: { ...J, cookie }, body: JSON.stringify({ ...d.config, phoneAdminLocked: true }) });
  const locked = await sync({ configPatch: { rev: 4, values: { safeSearch: false } } });
  assert.equal(locked.config.safeSearch, true);
  assert.equal(locked.configPatchRev, 4, "acknowledged, so the phone stops resending it");
});

import { normalizeBlockedParts, buildAgentPolicy, normalizeConfig } from "../src/policy.js";
test("tap to block: parts of apps are cleaned up, need something to recognise, and reach the phone's policy", () => {
  const parts = normalizeBlockedParts([
    { pkg: "com.google.android.youtube", desc: "Shorts", mode: "cover", label: "YouTube: Shorts" },
    { pkg: "com.instagram.android", viewId: "com.instagram.android:id/clips_tab", mode: "close" },
    { pkg: "com.instagram.android", viewId: "com.instagram.android:id/clips_tab", mode: "cover" },
    { pkg: "bad pkg!", desc: "x" },
    { pkg: "com.x", mode: "cover" },
  ]);
  assert.equal(parts.length, 2, "duplicates, bad package names and unrecognisable parts are dropped");
  assert.equal(parts[1].mode, "close");
  assert.equal(parts[1].label, "com.instagram.android:id/clips_tab");
  assert.match(parts[0].id, /^[0-9a-f]{6,16}$/);
  const pol = buildAgentPolicy(normalizeConfig({ blockedParts: parts }), []);
  assert.equal(pol.blockedParts.length, 2);
});

test("weekend limit, internet off for a while, camera block and new-app alerts are kept and sent to the phone", async () => {
  const cfg = normalizeConfig({ screenTime: { dailyLimit: 60, weekendLimit: 180, weekendDays: [5, 6, 9] }, internetOffUntil: 1234567890123, restrictions: { cameraDisabled: true } });
  assert.deepEqual([cfg.screenTime.weekendLimit, cfg.screenTime.weekendDays], [180, [5, 6]]);
  const pol = buildAgentPolicy(cfg, []);
  assert.equal(pol.internetOffUntil, 1234567890123);
  assert.equal(pol.screenTime.weekendLimit, 180);
  assert.ok(JSON.stringify(pol.restrictions).includes("no_camera"), "camera block goes to the phone");
});

test("school time is kept, and the Check in button is a phone menu choice", async () => {
  const cfg = normalizeConfig({ screenTime: { schoolTime: { days: [1, 2, 3, 4, 5], from: "08:00", to: "15:00" } } });
  assert.deepEqual(cfg.screenTime.schoolTime, { days: [1, 2, 3, 4, 5], from: "08:00", to: "15:00" });
  assert.equal(normalizeConfig({ screenTime: { schoolTime: { days: [], from: "08:00", to: "15:00" } } }).screenTime.schoolTime, null);
  const { USER_MENU } = await import("../src/policy.js");
  assert.ok(USER_MENU.checkin);
});
