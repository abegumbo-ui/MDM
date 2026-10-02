import test from "node:test";
import assert from "node:assert/strict";
import { buildAgentPolicy, isProtected, normalizeConfig, normalizeSchedule } from "../src/policy.js";

test("hides unlisted unprotected apps, shows protected and allowed ones", () => {
  const cfg = normalizeConfig({ blockUnlisted: true, apps: { "com.google.android.apps.maps": { mode: "allow" } } });
  const p = buildAgentPolicy(cfg, [
    "com.google.android.apps.maps",
    "com.android.chrome",
    "com.android.systemui",
    "com.motorola.launcher3",
  ]);
  assert.deepEqual(p.hide, ["com.android.chrome"]);
  assert.ok(p.show.includes("com.google.android.apps.maps"));
  assert.ok(p.show.includes("com.android.systemui"));
  assert.ok(p.show.includes("com.motorola.launcher3"));
});

test("explicit block overrides protection", () => {
  const p = buildAgentPolicy({ apps: { "com.android.settings": { mode: "block" } } }, ["com.android.settings"]);
  assert.deepEqual(p.hide, ["com.android.settings"]);
});

test("blockUnlisted is off by default and leaves unknown apps visible", () => {
  const p = buildAgentPolicy({}, ["x.y"]);
  assert.deepEqual(p.show, ["x.y"]);
  assert.deepEqual(p.hide, []);
});

test("explicit blocks apply even if the device never reported the package", () => {
  const p = buildAgentPolicy({ apps: { "com.example.game": { mode: "block" } } }, []);
  assert.deepEqual(p.hide, ["com.example.game"]);
});

test("developer options and factory reset are blocked by default; toggles work", () => {
  const p = buildAgentPolicy({}, []);
  assert.ok(p.restrictions.includes("no_factory_reset"));
  assert.ok(p.restrictions.includes("no_debugging_features"));
  const q = buildAgentPolicy({ restrictions: { debuggingDisabled: false, safeBootDisabled: false } }, []);
  assert.ok(!q.restrictions.includes("no_debugging_features"));
  assert.ok(!q.restrictions.includes("no_safe_boot"));
});

test("schedules are validated and passed to the phone", () => {
  assert.deepEqual(normalizeSchedule({ days: [1, 1, 3, 9, -1], from: "08:00", to: "17:30" }), { days: [1, 3], from: "08:00", to: "17:30" });
  assert.equal(normalizeSchedule({ days: [1], from: "8:00", to: "17:30" }), null);
  assert.equal(normalizeSchedule({ days: [], from: "08:00", to: "17:30" }), null);
  assert.equal(normalizeSchedule({ days: [1], from: "09:00", to: "09:00" }), null);
  const p = buildAgentPolicy({
    apps: {
      "a.b": { mode: "allow", schedule: { days: [0, 6], from: "10:00", to: "12:00" } },
      "c.d": { mode: "block", schedule: { days: [1], from: "10:00", to: "12:00" } },
    },
  }, ["a.b"]);
  assert.deepEqual(Object.keys(p.schedules), ["a.b"]);
});

test("the file picker is protected so in-app APK install keeps working", () => {
  assert.ok(isProtected("com.android.documentsui"));
  assert.ok(isProtected("com.google.android.documentsui"));
});

test("agent package is protected; garbage config is sanitized", () => {
  assert.ok(isProtected("com.familymdm.agent"));
  assert.deepEqual(normalizeConfig({ apps: { a: { mode: "evil" }, b: { mode: "block" } } }).apps, { b: { mode: "block", label: undefined } });
});
