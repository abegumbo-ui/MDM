import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import worker from "../src/index.js";
import { normalizeConfig } from "../src/policy.js";
import { KEY_GROUPS, COMMAND_GROUPS, CODE_GROUPS, PERM_GROUPS, ALL_PERMS, DEFAULT_SIGNUP_PERMS, deniedConfigKeys, permsOfClient } from "../src/perms.js";

const J = { "content-type": "application/json" };

test("every setting the server knows belongs to a control group, and every group is real", () => {
  const keys = Object.keys(normalizeConfig({}));
  const unmapped = keys.filter((k) => !KEY_GROUPS[k]);
  assert.deepEqual(unmapped, [], "a new setting needs a group in src/perms.js (until then only the administrator can change it)");
  assert.deepEqual(Object.keys(KEY_GROUPS).filter((k) => !keys.includes(k)), [], "no group for a setting that no longer exists");
  for (const g of Object.values(KEY_GROUPS).flat()) assert.ok(ALL_PERMS.includes(g), "unknown group " + g);
  for (const g of [...Object.values(COMMAND_GROUPS), ...Object.values(CODE_GROUPS)]) assert.ok(ALL_PERMS.includes(g), "unknown group " + g);
});

test("every phone command is either given a group or deliberately left to the administrator", () => {
  const src = fs.readFileSync(new URL("../src/index.js", import.meta.url), "utf8");
  const commands = JSON.parse(/const COMMANDS = new Set\((\[[^\]]*\])\)/.exec(src)[1]);
  const adminOnly = []; // none: wipe and release have their own group ("dangerous")
  assert.deepEqual(commands.filter((c) => !COMMAND_GROUPS[c] && !adminOnly.includes(c)), [], "a new command needs a group in src/perms.js");
});

test("what a change is checked against: only the settings that really differ", () => {
  const before = normalizeConfig({ blockedWords: ["a"], apps: { "x.y": { mode: "block" } } });
  const same = normalizeConfig({ blockedWords: ["a"], apps: { "x.y": { mode: "block" } } });
  assert.deepEqual(deniedConfigKeys(before, same, new Set()), [], "nothing changed: nothing to refuse, even for an account with no controls");
  const changed = normalizeConfig({ blockedWords: ["a", "b"], apps: { "x.y": { mode: "block" } } });
  assert.deepEqual(deniedConfigKeys(before, changed, new Set(["apps"])), ["blockedWords"]);
  assert.deepEqual(deniedConfigKeys(before, changed, new Set(["browser"])), []);
  const sites = normalizeConfig({ sites: { a: { type: "domain", url: "example.org" } } });
  assert.deepEqual(deniedConfigKeys(normalizeConfig({}), sites, new Set(["youtube"])), [], "allowed sites can be changed by whoever owns the browser OR YouTube");
  assert.deepEqual(deniedConfigKeys(normalizeConfig({}), sites, new Set(["apps"])), ["sites"]);
  assert.deepEqual(permsOfClient({}), ALL_PERMS, "an old account keeps everything");
  assert.deepEqual(permsOfClient({ perms: ["apps", "nonsense"] }), ["apps"]);
  assert.ok(!DEFAULT_SIGNUP_PERMS.includes("security") && !DEFAULT_SIGNUP_PERMS.includes("dangerous"), "a sign-up never starts with locks or erasing");
});

async function boot() {
  const kv = new Map();
  const env = {
    ADMIN_PASSWORD: "pw",
    STATE: {
      get: async (k, t) => (kv.has(k) ? (t === "json" ? JSON.parse(kv.get(k)) : kv.get(k)) : null),
      put: async (k, v) => void kv.set(k, v),
      delete: async (k) => void kv.delete(k),
      list: async ({ prefix }) => ({ keys: [...kv.keys()].filter((k) => k.startsWith(prefix)).map((name) => ({ name })), list_complete: true }),
    },
  };
  const call = (path, init = {}) => worker.fetch(new Request("https://x.test" + path, init), env);
  const login = async (loginName, password) => {
    const r = await call("/login", { method: "POST", body: new URLSearchParams(loginName ? { loginName, password } : { password }) });
    const c = r.headers.get("set-cookie");
    return c ? c.split(";")[0] : null;
  };
  const api = (cookie) => (path, method = "GET", body) => call(path, { method, headers: { ...J, cookie }, ...(body !== undefined ? { body: JSON.stringify(body) } : {}) });
  const admin = api(await login("", "pw"));
  return { call, login, api, admin };
}

test("a client can only change what the administrator gave them (settings, commands, codes, templates, clones), and so can their helper", async () => {
  const { call, login, api, admin } = await boot();
  // pat: only Apps. old: made the old way (no controls recorded), so everything.
  await admin("/api/clients", "POST", { loginName: "pat", name: "Pat", password: "pat-password" });
  await admin("/api/clients", "POST", { loginName: "oldie", name: "Old", password: "oldie-password" });
  assert.equal((await admin("/api/clients/pat/perms", "PUT", { perms: ["apps"] })).status, 200);
  const list = await (await admin("/api/clients")).json();
  assert.deepEqual(list.find((c) => c.id === "pat").perms, ["apps"]);
  assert.deepEqual(list.find((c) => c.id === "oldie").perms, ALL_PERMS, "an account made before controls existed keeps all of them");
  const pat = api(await login("pat", "pat-password"));
  const oldie = api(await login("oldie", "oldie-password"));

  const enroll = async (who, model) => {
    const { code } = await (await who("/api/codes", "POST", { type: "enroll" })).json();
    assert.equal((await call("/agent/enroll", { method: "POST", headers: J, body: JSON.stringify({ code, info: { model, manufacturer: "T" } }) })).status, 200);
  };
  await enroll(pat, "PAT-ONE");
  await enroll(pat, "PAT-TWO");
  await enroll(oldie, "OLD-ONE");
  const devs = await (await pat("/api/devices")).json();
  const one = devs.find((d) => JSON.stringify(d).includes("PAT-ONE")), two = devs.find((d) => JSON.stringify(d).includes("PAT-TWO"));
  const oldDev = (await (await oldie("/api/devices")).json())[0];
  const cfgOf = async (who, id) => ((await (await who("/api/devices")).json()).find((d) => d.id === id) || {}).config;

  // Settings: the dashboard saves the WHOLE config, so a limited account sends back everything unchanged plus its own change.
  const base = await cfgOf(pat, one.id);
  const put = (who, id, patch) => who(`/api/devices/${id}/config`, "PUT", { ...base, ...patch });
  assert.equal((await put(pat, one.id, { approveNew: true })).status, 200, "Apps: allowed");
  const refusal = await put(pat, one.id, { blockedWords: ["casino"] });
  assert.equal(refusal.status, 403, "Browser and web: refused");
  assert.match((await refusal.json()).error, /Browser and web/, "and it says which control it is");
  const firstRestriction = Object.keys(base.restrictions)[0];
  assert.equal((await put(pat, one.id, { restrictions: { ...base.restrictions, [firstRestriction]: !base.restrictions[firstRestriction] } })).status, 403, "Lock and security: refused");
  assert.equal((await put(pat, one.id, { screenTime: { ...base.screenTime, dailyLimit: 60 } })).status, 403, "Screen time: refused");
  assert.equal((await put(pat, one.id, { youtubeMode: "channels" })).status, 403, "YouTube: refused");
  assert.deepEqual((await cfgOf(pat, one.id)).blockedWords, [], "nothing of the refused changes was saved");
  assert.equal((await put(oldie, oldDev.id, { blockedWords: ["casino"] })).status, 200, "the old-style account can still change everything");

  // Commands.
  const cmd = (who, id, type, args = {}) => who(`/api/devices/${id}/command`, "POST", { type, args });
  assert.equal((await cmd(pat, one.id, "findScreen", { name: "Wi-Fi" })).status, 200, "an Apps command: allowed");
  for (const type of ["lock", "locate", "addTime", "screenshot", "setPin", "wipe", "release"]) assert.equal((await cmd(pat, one.id, type, {})).status, 403, type + " needs a control pat was not given");
  assert.equal((await cmd(oldie, oldDev.id, "locate")).status, 200, "the old-style account can do them");

  // Codes.
  const code = async (who, type, extra = {}) => (await who("/api/codes", "POST", { type, ...extra })).status;
  assert.equal(await code(pat, "enroll"), 200, "a client may always add their own phones");
  assert.equal(await code(pat, "install", { deviceId: one.id }), 200, "an install code is Apps");
  assert.equal(await code(pat, "freebrowse", { deviceId: one.id }), 403, "a browse-freely code is Browser and web");
  assert.equal(await code(pat, "uninstall", { deviceId: one.id }), 403, "a removal code is Lock and security");

  // Cloning one phone's settings onto another counts as changing them.
  assert.equal((await put(pat, two.id, { blockedWords: ["x"] })).status, 403);
  assert.equal((await oldie(`/api/devices/${oldDev.id}/config`, "PUT", { ...base, blockedWords: ["from-old"] })).status, 200);
  assert.equal((await pat(`/api/devices/${two.id}/clone-from`, "POST", { sourceId: one.id })).status, 200, "a clone that changes nothing is fine");
  // A template that changes a setting pat has no control over is refused for that phone.
  const tpl = await (await oldie("/api/templates", "POST", { name: "T", deviceId: oldDev.id })).json();
  assert.equal((await pat(`/api/templates/${tpl.id}/apply`, "POST", { deviceIds: [one.id] })).status, 404, "someone else's template is not theirs");
  await pat("/api/templates", "POST", { name: "mine", deviceId: one.id });

  // Their helper (another parent on pat's account) is held to the same limits.
  assert.equal((await pat("/api/helpers", "POST", { loginName: "pathelper", name: "Helper", password: "helper-password" })).status, 200);
  const helper = api(await login("pathelper", "helper-password"));
  assert.equal((await put(helper, one.id, { blockedWords: ["h"] })).status, 403, "a helper of a limited client is limited too");
  assert.equal((await put(helper, one.id, { approveNew: false })).status, 200);

  // Nobody but the main administrator can hand out controls.
  assert.equal((await pat("/api/clients/pat/perms", "PUT", { perms: ALL_PERMS })).status, 403, "a client cannot give themselves more");
  assert.equal((await helper("/api/clients/pat/perms", "PUT", { perms: ALL_PERMS })).status, 403);
  assert.equal((await oldie("/api/clients/pat/perms", "PUT", { perms: ALL_PERMS })).status, 403, "nor another client");
  assert.deepEqual((await (await admin("/api/clients")).json()).find((c) => c.id === "pat").perms, ["apps"], "unchanged");

  // The administrator widens it, and it applies at once.
  assert.equal((await admin("/api/clients/pat/perms", "PUT", { perms: ["apps", "browser", "device"] })).status, 200);
  assert.equal((await put(pat, one.id, { blockedWords: ["casino"] })).status, 200, "now allowed");
  assert.equal((await cmd(pat, one.id, "lock")).status, 200, "Phone settings includes lock and find");
  assert.equal((await cmd(pat, one.id, "wipe")).status, 403, "erasing is its own control");
  // What the client's own dashboard is told.
  const state = await (await pat("/api/state")).json();
  assert.deepEqual([...state.session.perms].sort(), ["apps", "browser", "device"]);
  assert.equal(state.permGroups.length, PERM_GROUPS.length);
});

test("an approved sign-up starts with the ready-made controls (or exactly what the administrator ticks), never locks or erasing", async () => {
  const { call, api, admin, login } = await boot();
  await admin("/api/signups/settings", "PUT", { open: true });
  const sign = (loginName, ip) => call("/signup", { method: "POST", headers: { "cf-connecting-ip": ip }, body: new URLSearchParams({ name: loginName, loginName, email: "", password: "a-long-password", password2: "a-long-password", website: "" }) });
  assert.equal((await sign("first", "192.0.2.1")).status, 200);
  assert.equal((await sign("second", "192.0.2.2")).status, 200);
  assert.equal((await admin("/api/signups/first/approve", "POST", {})).status, 200);
  assert.equal((await admin("/api/signups/second/approve", "POST", { perms: ["time", "bogus"] })).status, 200);
  const clients = await (await admin("/api/clients")).json();
  assert.deepEqual(clients.find((c) => c.id === "first").perms, DEFAULT_SIGNUP_PERMS);
  assert.deepEqual(clients.find((c) => c.id === "second").perms, ["time"], "only known controls, exactly as ticked");
  const me = await (await api(await login("first", "a-long-password"))("/api/state")).json();
  assert.ok(!me.session.perms.includes("security") && !me.session.perms.includes("dangerous"));
});
