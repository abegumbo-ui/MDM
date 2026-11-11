import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { APP_GROUPS, USER_MENU, buildAgentPolicy, normalizeConfig, summarizeConfigChange } from "../src/policy.js";

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

test("every phone-menu button is shown unless switched off, and the phone is told", () => {
  const all = normalizeConfig({}).userMenu;
  assert.deepEqual(Object.keys(all).sort(), Object.keys(USER_MENU).sort());
  assert.ok(Object.values(all).every((v) => v === true));
  const some = normalizeConfig({ userMenu: { store: false, junk: false, site: "no" } }).userMenu;
  assert.equal(some.store, false);
  assert.equal(some.site, true, "only an exact false hides a button");
  assert.equal(some.junk, undefined, "unknown buttons are dropped");
  assert.equal(buildAgentPolicy({ userMenu: { store: false } }, []).userMenu.store, false);
  assert.deepEqual(summarizeConfigChange(normalizeConfig({}), normalizeConfig({ userMenu: { store: false } })).filter((l) => l.startsWith("Phone menu")),
    ['Phone menu "Open Google Play Store": hidden']);
});

test("blocking the social media group hides those apps, but an app you allowed stays", () => {
  const installed = ["com.instagram.android", "com.snapchat.android", "com.whatsapp", "com.google.android.apps.maps"];
  const off = buildAgentPolicy({}, installed);
  assert.ok(!off.hide.includes("com.instagram.android"), "nothing is blocked until the group is switched on");
  const on = buildAgentPolicy({ blockAppGroups: ["social"] }, installed);
  assert.ok(on.hide.includes("com.instagram.android") && on.hide.includes("com.snapchat.android"));
  assert.ok(!on.hide.includes("com.whatsapp") && on.show.includes("com.whatsapp"), "messaging apps are not in the social group");
  assert.ok(on.show.includes("com.google.android.apps.maps"));
  assert.ok(!on.hide.includes("com.facebook.katana"), "a group app that is not installed is not sent to the phone");
  const keepOne = buildAgentPolicy({ blockAppGroups: ["social"], apps: { "com.instagram.android": { mode: "allow" } } }, installed);
  assert.ok(!keepOne.hide.includes("com.instagram.android") && keepOne.show.includes("com.instagram.android"), "an explicit Allow wins over the group");
  assert.ok(keepOne.hide.includes("com.snapchat.android"));
  assert.deepEqual(normalizeConfig({ blockAppGroups: ["social", "social", "nonsense", 5] }).blockAppGroups, ["social"]);
  assert.ok(!APP_GROUPS.social.packages.includes("com.whatsapp"));
});

test("requests from the phone's menu arrive once, can be dismissed, and Play Store commands and codes work", async () => {
  const login = await req("/login", { method: "POST", body: new URLSearchParams({ password: env.ADMIN_PASSWORD }) });
  const cookie = login.headers.get("set-cookie").split(";")[0];
  const { code } = await (await req("/api/codes", { method: "POST", headers: { ...J, cookie }, body: JSON.stringify({ type: "enroll" }) })).json();
  const { token } = await (await req("/agent/enroll", { method: "POST", headers: J, body: JSON.stringify({ code, info: { model: "T" } }) })).json();
  const auth = { ...J, authorization: `Bearer ${token}` };
  const sync = async (body) => await (await req("/agent/sync", { method: "POST", headers: auth, body: JSON.stringify(body) })).json();
  const device = async () => (await (await req("/api/devices", { headers: { cookie } })).json())[0];

  await sync({ appRequests: [{ kind: "store" }, { kind: "app", pkg: "com.spotify.music", label: "Spotify" }, { kind: "app", pkg: "bad pkg!" }, { kind: "weird" }] });
  await sync({ appRequests: [{ kind: "store" }, { kind: "app", pkg: "com.spotify.music", label: "Spotify" }] });
  let d = await device();
  assert.deepEqual(d.appRequests.map((r) => r.key), ["store", "app:com.spotify.music"], "bad ones dropped, repeats not doubled");
  assert.equal(d.appRequests[1].label, "Spotify");

  const del = await req(`/api/devices/${d.id}/app-requests?key=${encodeURIComponent("app:com.spotify.music")}`, { method: "DELETE", headers: { cookie } });
  assert.equal(del.status, 200);
  assert.deepEqual((await device()).appRequests.map((r) => r.key), ["store"]);

  // Opening the Play Store from the dashboard: a queued command with a sane number of minutes.
  const open = await req(`/api/devices/${d.id}/command`, { method: "POST", headers: { ...J, cookie }, body: JSON.stringify({ type: "openStore", args: { minutes: 999999 } }) });
  assert.equal(open.status, 200);
  await req(`/api/devices/${d.id}/command`, { method: "POST", headers: { ...J, cookie }, body: JSON.stringify({ type: "closeStore" }) });
  const cmds = (await sync({})).commands;
  assert.deepEqual(cmds.map((c) => c.type), ["openStore", "closeStore"]);
  assert.equal(cmds[0].args.minutes, 24 * 60, "capped at a day");

  // A one-time Play Store code: tied to this phone, works once, and says for how long.
  const ps = await (await req("/api/codes", { method: "POST", headers: { ...J, cookie }, body: JSON.stringify({ type: "playstore", deviceId: d.id, minutes: 30 }) })).json();
  assert.equal(ps.minutes, 30);
  const redeem = (c) => req("/agent/redeem", { method: "POST", headers: auth, body: JSON.stringify({ type: "playstore", code: c }) });
  assert.equal((await redeem("ZZZZZZZZ")).status, 403);
  const ok = await redeem(ps.code);
  assert.deepEqual(await ok.json(), { ok: true, minutes: 30 });
  assert.equal((await redeem(ps.code)).status, 403, "a code works once");
});
