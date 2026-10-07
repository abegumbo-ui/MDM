import test from "node:test";
import assert from "node:assert/strict";
import { buildAgentPolicy, isBlockedAdultHost, isProtected, normalizeConfig, normalizeSchedule, normalizeSite, normalizeSites, siteAllowed } from "../src/policy.js";

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

test("freezeUpdates defaults off and passes through when set", () => {
  assert.equal(buildAgentPolicy({}, []).freezeUpdates, false);
  assert.equal(buildAgentPolicy({ freezeUpdates: true }, []).freezeUpdates, true);
});

test("blockAccessibility defaults off and passes through when set", () => {
  assert.equal(buildAgentPolicy({}, []).blockAccessibility, false);
  assert.equal(buildAgentPolicy({ blockAccessibility: true }, []).blockAccessibility, true);
});

test("blockedSettings: every category is blocked by default, and an explicit list is honored", () => {
  const fresh = buildAgentPolicy({}, []);
  assert.equal(fresh.blockedSettings.length, 22);
  assert.ok(fresh.blockedSettings.includes("accessibility"));

  const narrowed = buildAgentPolicy({ blockedSettings: ["network", "location"] }, []);
  assert.deepEqual(narrowed.blockedSettings, ["network", "location"]);

  const allowedEverything = buildAgentPolicy({ blockedSettings: [] }, []);
  assert.deepEqual(allowedEverything.blockedSettings, []);

  const ignoresJunk = buildAgentPolicy({ blockedSettings: ["network", "not-a-real-category"] }, []);
  assert.deepEqual(ignoresJunk.blockedSettings, ["network"]);
});

test("new location/network/VPN restrictions default off, and turn on only when asked", () => {
  const off = buildAgentPolicy({}, []);
  for (const key of ["no_config_location", "no_airplane_mode", "no_config_mobile_networks", "no_config_tethering", "no_config_vpn", "disallow_config_private_dns"]) {
    assert.ok(!off.restrictions.includes(key), key + " should default off");
  }
  const on = buildAgentPolicy({ restrictions: { locationConfigDisabled: true, tetheringDisabled: true, vpnConfigDisabled: true, privateDnsDisabled: true } }, []);
  assert.ok(on.restrictions.includes("no_config_location"));
  assert.ok(on.restrictions.includes("no_config_tethering"));
  assert.ok(on.restrictions.includes("no_config_vpn"));
  assert.ok(on.restrictions.includes("disallow_config_private_dns"));
  assert.ok(!on.restrictions.includes("no_airplane_mode"));
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

test("soft block: stays visible (never hidden) and never appears in the home-screen allowed list", () => {
  const p = buildAgentPolicy({ homeScreen: true, apps: { "com.example.game": { mode: "soft" }, "com.example.maps": { mode: "allow" } } }, [
    "com.example.game",
    "com.example.maps",
  ]);
  assert.ok(p.show.includes("com.example.game"), "soft-blocked apps are never hidden");
  assert.ok(!p.hide.includes("com.example.game"));
  assert.ok(!p.allowed.includes("com.example.game"), "soft-blocked apps don't appear in the home-screen launcher");
  assert.ok(p.allowed.includes("com.example.maps"));
  // Also applies to a soft-blocked app the device hasn't reported yet.
  const q = buildAgentPolicy({ homeScreen: true, apps: { "com.example.other": { mode: "soft" } } }, []);
  assert.ok(q.show.includes("com.example.other"));
  assert.ok(!q.hide.includes("com.example.other"));
});

test("a harsh Block still hides the app even with blockUnlisted off, unlike soft block", () => {
  const p = buildAgentPolicy({ homeScreen: true, apps: { "com.example.a": { mode: "block" }, "com.example.b": { mode: "soft" } } }, [
    "com.example.a",
    "com.example.b",
  ]);
  assert.deepEqual(p.hide, ["com.example.a"]);
  assert.ok(p.show.includes("com.example.b"));
});

test("a leftover \"soft\" entry goes back to Default once Home screen mode is off", () => {
  const cfg = normalizeConfig({ homeScreen: false, apps: { "com.example.game": { mode: "soft" }, "com.example.b": { mode: "allow" } } });
  assert.deepEqual(cfg.apps, { "com.example.b": { mode: "allow", label: undefined } }, "the soft entry is dropped, not just unselectable");
  const kept = normalizeConfig({ homeScreen: true, apps: { "com.example.game": { mode: "soft" } } });
  assert.equal(kept.apps["com.example.game"].mode, "soft", "kept while Home screen mode is actually on");
});

test("Google Play Store is protected from automatic hiding, but an explicit Block still hides it", () => {
  assert.ok(isProtected("com.android.vending"), "never auto-hidden by blockUnlisted/approveNew");
  const p = buildAgentPolicy({ apps: { "com.android.vending": { mode: "block" } } }, ["com.android.vending"]);
  assert.deepEqual(p.hide, ["com.android.vending"], "an explicit Block always wins, even over protection");
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
  assert.ok(isProtected("com.familymdm.browser"));
  assert.deepEqual(normalizeConfig({ apps: { a: { mode: "evil" }, b: { mode: "block" } } }).apps, { b: { mode: "block", label: undefined } });
});

test("approval mode sends the approved baseline to the phone", () => {
  const p = buildAgentPolicy({ approveNew: true }, ["a.b", "c.d"], { known: ["a.b"] });
  assert.deepEqual(p.known, ["a.b"]);
  assert.deepEqual(p.pending, ["c.d"]);
  assert.equal(buildAgentPolicy({}, ["a.b"], { known: ["a.b"] }).known, undefined);
});

test("home-screen mode: the phone gets the apps that may be opened", () => {
  const p = buildAgentPolicy(
    { homeScreen: true, apps: { "a.b": { mode: "allow" }, "c.d": { mode: "block" } } },
    ["a.b", "c.d", "e.f"],
    { overrides: { "e.f": "allow" } },
  );
  assert.equal(p.homeScreen, true);
  assert.deepEqual(p.allowed.sort(), ["a.b", "e.f"]);
  assert.equal(buildAgentPolicy({}, ["a.b"]).homeScreen, false);
});

test("Factory Reset Protection account IDs are validated and sent to the phone", async () => {
  const { normalizeFrpAccounts } = await import("../src/policy.js");
  assert.deepEqual(normalizeFrpAccounts(["people/118273645564738291027", "118273645564738291027", "me@gmail.com", "123", " 118273645564738291028 "]),
    ["118273645564738291027", "118273645564738291028"]);
  assert.equal(normalizeFrpAccounts("nope").length, 0);
  assert.equal(normalizeFrpAccounts(Array.from({ length: 9 }, (_, i) => "11827364556473829102" + i)).length, 3, "at most 3");
  const p = buildAgentPolicy({ frpAccounts: ["118273645564738291027"] }, []);
  assert.deepEqual(p.frpAccounts, ["118273645564738291027"]);
  assert.deepEqual(buildAgentPolicy({}, []).frpAccounts, []);
});

test("normalizeSite: validates, normalizes host, fills defaults", () => {
  assert.equal(normalizeSite(null), null);
  assert.equal(normalizeSite({ type: "bogus", url: "x.com" }), null);
  assert.equal(normalizeSite({ type: "domain", url: "" }), null);
  const d = normalizeSite({ type: "domain", url: "WWW.NYTimes.com" });
  assert.equal(d.host, "nytimes.com");
  assert.equal(d.url, "https://WWW.NYTimes.com");
  assert.equal(d.blockImages, false);
  assert.equal(d.installable, true);
  assert.equal(d.label, "nytimes.com");
  const e = normalizeSite({ type: "exact", url: "https://example.com/a/b/", label: "Page B", blockImages: true });
  assert.equal(e.label, "Page B");
  assert.equal(e.blockImages, true);
});

test("a known adult site is never accepted as an allowlist entry, from any source", () => {
  assert.ok(isBlockedAdultHost("pornhub.com"));
  assert.ok(isBlockedAdultHost("www.pornhub.com"));
  assert.ok(isBlockedAdultHost("sub.pornhub.com"));
  assert.ok(!isBlockedAdultHost("notpornhub.com"));
  assert.equal(normalizeSite({ type: "domain", url: "pornhub.com" }), null);
  assert.equal(normalizeSite({ type: "exact", url: "https://pornhub.com/video" }), null);
  const cfg = normalizeConfig({ sites: { a: { type: "domain", url: "pornhub.com" }, b: { type: "domain", url: "khanacademy.org" } } });
  assert.deepEqual(Object.keys(cfg.sites), ["b"]);
});

test("normalizeSites: drops invalid entries, de-duplicates", () => {
  const sites = normalizeSites({
    a: { type: "domain", url: "nytimes.com" },
    b: { type: "domain", url: "www.nytimes.com" }, // same as a once normalized
    c: { type: "exact", url: "https://example.com/page/" },
    d: { type: "exact", url: "https://example.com/page" }, // same as c (trailing slash)
    e: { type: "domain", url: "" }, // invalid, dropped
  });
  assert.equal(sites.length, 2);
});

test("siteAllowed: domain covers subpages and subdomains, not other domains", () => {
  const sites = normalizeSites({ a: { type: "domain", url: "nytimes.com" } });
  assert.equal(siteAllowed("https://nytimes.com/section/world", sites), true);
  assert.equal(siteAllowed("https://www.nytimes.com/", sites), true);
  assert.equal(siteAllowed("https://m.nytimes.com/x", sites), true);
  assert.equal(siteAllowed("https://notnytimes.com/", sites), false);
  assert.equal(siteAllowed("https://evilnytimes.com/", sites), false);
});

test("siteAllowed: exact only covers that one page, ignoring query/fragment/trailing slash", () => {
  const sites = normalizeSites({ a: { type: "exact", url: "https://example.com/safe-page" } });
  assert.equal(siteAllowed("https://example.com/safe-page", sites), true);
  assert.equal(siteAllowed("https://example.com/safe-page/", sites), true);
  assert.equal(siteAllowed("https://example.com/safe-page?x=1#y", sites), true);
  assert.equal(siteAllowed("https://example.com/other-page", sites), false);
  assert.equal(siteAllowed("https://example.com/", sites), false);
});

test("sites reach the agent policy", () => {
  const p = buildAgentPolicy({ sites: { a: { type: "domain", url: "khanacademy.org" } } }, []);
  assert.equal(p.sites.length, 1);
  assert.equal(p.sites[0].host, "khanacademy.org");
});
