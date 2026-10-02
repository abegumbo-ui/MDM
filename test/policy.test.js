import test from "node:test";
import assert from "node:assert/strict";
import { buildPolicy, isProtected, normalizeConfig, packagesFromDevices } from "../src/policy.js";

const find = (p, pkg) => p.applications.find((a) => a.packageName === pkg);

test("blocks unlisted unprotected apps, keeps protected and allowed ones", () => {
  const cfg = normalizeConfig({ apps: { "com.google.android.apps.maps": { mode: "allow" } } });
  const p = buildPolicy(cfg, [
    "com.google.android.apps.maps",
    "com.android.chrome",
    "com.android.systemui",
    "com.motorola.launcher3",
  ]);
  assert.equal(find(p, "com.google.android.apps.maps").installType, "AVAILABLE");
  assert.equal(find(p, "com.android.chrome").installType, "BLOCKED");
  assert.equal(find(p, "com.android.systemui"), undefined);
  assert.equal(find(p, "com.motorola.launcher3"), undefined);
});

test("explicit block overrides protection; force installs", () => {
  const p = buildPolicy({
    apps: { "com.android.settings": { mode: "block" }, "org.example": { mode: "force" } },
  });
  assert.equal(find(p, "com.android.settings").installType, "BLOCKED");
  assert.equal(find(p, "org.example").installType, "FORCE_INSTALLED");
});

test("blockUnlisted=false leaves unknown apps alone; extraPolicy wins", () => {
  const p = buildPolicy({ blockUnlisted: false, extraPolicy: { factoryResetDisabled: false } }, ["x.y"]);
  assert.equal(find(p, "x.y"), undefined);
  assert.equal(p.factoryResetDisabled, false);
  assert.equal(p.playStoreMode, "WHITELIST");
});

test("helpers", () => {
  assert.ok(isProtected("com.android.providers.media"));
  assert.deepEqual(packagesFromDevices([{ applicationReports: [{ packageName: "b" }, { packageName: "a" }] }, {}]), ["a", "b"]);
});
