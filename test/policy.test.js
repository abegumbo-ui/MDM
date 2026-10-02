import test from "node:test";
import assert from "node:assert/strict";
import { buildAgentPolicy, isProtected, normalizeConfig } from "../src/policy.js";

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

test("restriction defaults keep debugging usable and block factory reset", () => {
  const p = buildAgentPolicy({}, []);
  assert.ok(p.restrictions.includes("no_factory_reset"));
  assert.ok(!p.restrictions.includes("no_debugging_features"));
  const q = buildAgentPolicy({ restrictions: { debuggingDisabled: true, safeBootDisabled: false } }, []);
  assert.ok(q.restrictions.includes("no_debugging_features"));
  assert.ok(!q.restrictions.includes("no_safe_boot"));
});

test("agent package is protected; garbage config is sanitized", () => {
  assert.ok(isProtected("com.familymdm.agent"));
  assert.deepEqual(normalizeConfig({ apps: { a: { mode: "evil" }, b: { mode: "block" } } }).apps, { b: { mode: "block", label: undefined } });
});
