import test from "node:test";
import assert from "node:assert/strict";
import { endSitesEarly, normalizeTimedHosts, normalizeBlockedScreens, buildAgentPolicy, isBlockedAdultHost, isProtected, normalizeConfig, normalizeSchedule, normalizeSite, normalizeSites, RESTRICTIONS, siteAllowed, summarizeConfigChange, FILTER_EXTRAS, normalizeFilterExtras } from "../src/policy.js";

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

test("phoneAdminLocked defaults off and passes through when set", () => {
  assert.equal(buildAgentPolicy({}, []).phoneAdminLocked, false);
  assert.equal(buildAgentPolicy({ phoneAdminLocked: true }, []).phoneAdminLocked, true);
});

test("hideAppIcon defaults off and passes through when set", () => {
  assert.equal(buildAgentPolicy({}, []).hideAppIcon, false);
  assert.equal(buildAgentPolicy({ hideAppIcon: true }, []).hideAppIcon, true);
});

test("browserAddonEnabled defaults off and passes through when set", () => {
  assert.equal(buildAgentPolicy({}, []).browserAddonEnabled, false);
  assert.equal(buildAgentPolicy({ browserAddonEnabled: true }, []).browserAddonEnabled, true);
});

test("wholeAppBlocklist defaults empty, passes through, and sanitizes junk entries", () => {
  assert.deepEqual(buildAgentPolicy({}, []).wholeAppBlocklist, {});
  const p = buildAgentPolicy({ wholeAppBlocklist: { "com.example.a": { paused: true }, "bad pkg!": { paused: true }, "com.example.b": {} } }, []);
  assert.deepEqual(p.wholeAppBlocklist, { "com.example.a": { paused: true }, "com.example.b": { paused: false } });
});

test("restrictions: every android.os.UserManager.DISALLOW_* key is listed, and every new one defaults off", () => {
  const keys = Object.values(RESTRICTIONS).map((v) => v.key);
  assert.equal(keys.length, 77, "should cover every known DISALLOW_* restriction");
  assert.equal(new Set(keys).size, keys.length, "no duplicate keys");
  const defaultOnCount = Object.values(RESTRICTIONS).filter((v) => v.on).length;
  assert.equal(defaultOnCount, 8, "only the original core-lockdown restrictions default on; everything added since defaults off");
  for (const key of ["no_camera", "no_sms", "no_bluetooth", "disallow_biometric", "no_cellular_2g", "no_sim_globally"]) {
    assert.ok(keys.includes(key), key + " should be listed");
    assert.ok(!buildAgentPolicy({}, []).restrictions.includes(key), key + " should default off");
  }
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

test("summarizeConfigChange: reports what actually changed, in plain English, and nothing when nothing did", () => {
  const before = normalizeConfig({});
  const afterRestriction = normalizeConfig({ restrictions: { cameraDisabled: true } });
  assert.deepEqual(summarizeConfigChange(before, afterRestriction), ["Disable the camera entirely: on"]);

  const afterApp = normalizeConfig({ apps: { "com.example.app": { mode: "block", label: "Example" } } });
  assert.deepEqual(summarizeConfigChange(before, afterApp), ["Example: block"]);

  assert.deepEqual(summarizeConfigChange(before, normalizeConfig({})), [], "an identical save reports no changes");
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

test("explicit allow applies even if this sync's package report omits it -- it never silently falls back to blockUnlisted's hide", () => {
  // Android Auto has no stable moment it's guaranteed to appear in a given sync's package report;
  // an admin who already set it to Allow must not have it drop out of `allowed`/`show` (and get
  // hidden by blockUnlisted) just because one sync's reported-packages list happens to omit it.
  const p = buildAgentPolicy(
    { homeScreen: true, blockUnlisted: true, apps: { "com.google.android.projection.gearhead": { mode: "allow" } } },
    [], // the device's last report doesn't include it this time
  );
  assert.ok(p.allowed.includes("com.google.android.projection.gearhead"));
  assert.ok(p.show.includes("com.google.android.projection.gearhead"));
  assert.ok(!p.hide.includes("com.google.android.projection.gearhead"));
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

test("sites allowed for a while: sent with their end time, dropped once it is up", () => {
  const soon = Date.now() + 3600e3;
  const sites = normalizeSites({
    a: { type: "domain", url: "games.example", until: soon },
    b: { type: "domain", url: "over.example", until: Date.now() - 1000 },
    c: { type: "domain", url: "always.example" },
  });
  assert.deepEqual(sites.map((s) => s.host).sort(), ["always.example", "games.example"]);
  assert.equal(sites.find((s) => s.host === "games.example").until, soon);
  assert.equal(sites.find((s) => s.host === "always.example").until, undefined, "no end time: always");
});

test("ended early in the Browser's Allow list: ends on the dashboard too, only the exact entry", () => {
  const now = Date.now(), until = now + 3600e3;
  const cfg = { sites: {
    a: { type: "domain", url: "https://games.example", until },
    b: { type: "domain", url: "https://other.example", until },
    c: { type: "youtube", id: "@chan", url: "https://www.youtube.com/@chan", until },
  } };
  assert.equal(endSitesEarly(cfg, [{ type: "domain", url: "https://games.example", until }, { type: "youtube", id: "@Chan", until }], now), true);
  assert.equal(cfg.sites.a.until, now);
  assert.equal(cfg.sites.c.until, now);
  assert.equal(cfg.sites.b.until, until, "the others are untouched");
  assert.equal(endSitesEarly(cfg, [{ type: "domain", url: "https://other.example", until: until + 5 }], now), false, "a different end time (allowed again) is not ended");
  const sent = normalizeSites(cfg.sites).map((s) => s.host);
  assert.deepEqual(sent, ["other.example"]);
});

test("computers: sites allowed for a while keep their end time, the latest per site, and drop once over", () => {
  const now = Date.now();
  const out = normalizeTimedHosts([
    { host: "https://Games.example/x", until: now + 3600e3 },
    { host: "games.example", until: now + 7200e3 },
    { host: "over.example", until: now - 1 },
    { host: "", until: now + 5 },
    "junk",
  ], now);
  assert.deepEqual(out, [{ host: "games.example", until: now + 7200e3 }]);
  const cfg = normalizeConfig({ dnsAllowTimed: [{ host: "khanacademy.org", until: now + 60e3 }] });
  assert.equal(cfg.dnsAllowTimed[0].host, "khanacademy.org");
});

test("signing in to YouTube is off unless allowed; whole screens blocked on the phone are kept clean and reach it", () => {
  assert.equal(normalizeConfig({}).youtubeSignIn, false);
  assert.equal(normalizeConfig({ youtubeSignIn: "yes" }).youtubeSignIn, false, "only exactly true");
  assert.equal(buildAgentPolicy(normalizeConfig({ youtubeSignIn: true }), []).youtubeSignIn, true);
  const sep = "\u001f";
  const good = "com.android.settings" + sep + "Accessibility" + sep + "Installed apps";
  assert.deepEqual(normalizeBlockedScreens([good, good, "no-separator", "bad pkg!" + sep + "x", "com.x.y" + sep + "", 5, null, "com.x.y" + sep + "a".repeat(81)]), [good]);
  const p = buildAgentPolicy(normalizeConfig({ blockedScreens: [good] }), []);
  assert.deepEqual(p.blockedScreens, [good], "the phone is told");
});

test("per-site pictures and videos: the boxes on each approved site become the phone's lists", () => {
  const cfg = {
    sites: {
      a: { type: "domain", url: "https://maps.example.com", pictures: "allow", videos: "block" },
      b: { type: "domain", url: "https://www.news.example.org", pictures: "block", videos: "allow" },
      c: { type: "domain", url: "https://plain.example.net" },
      d: { type: "domain", url: "https://old.example.io", blockImages: true },
      e: { type: "domain", url: "https://junk.example.io", pictures: "maybe", videos: 5 },
      y: { type: "youtube", id: "@somebody", pictures: "block", videos: "block" },
    },
    blockVideos: true,
  };
  const p = buildAgentPolicy(cfg, []);
  assert.deepEqual(p.imagesAllow, ["maps.example.com"], "Allow pictures");
  assert.deepEqual([...p.noPictureSites].sort(), ["news.example.org", "old.example.io"], "Block pictures (and the older single tick)");
  assert.deepEqual(p.videoAllow, ["news.example.org"], "Allow videos");
  assert.deepEqual(p.noVideoSites, ["maps.example.com"], "Block videos");
  assert.equal(p.blockVideos, true, "the everywhere switch");
  const s = normalizeSites(cfg.sites);
  assert.equal(s.find((x) => x.host === "junk.example.io").pictures, undefined, "an unknown value is dropped");
  assert.equal(s.find((x) => x.host === "junk.example.io").videos, undefined);
  assert.equal(normalizeSite({ type: "domain", url: "https://x.test", pictures: "block" }).blockImages, true, "Block pictures also sets the older flag");
  assert.equal(buildAgentPolicy({}, []).blockVideos, false, "videos are shown unless switched off");
  assert.deepEqual(buildAgentPolicy({}, []).noVideoSites, []);
});

test("extra categories (news, shopping...) reach the phone only with a level on, and never twice", () => {
  assert.deepEqual(FILTER_EXTRAS, ["social", "news", "shopping", "games", "video", "dating"]);
  assert.deepEqual(normalizeFilterExtras(["news", "bogus", "news", 5, "shopping"]), ["news", "shopping"], "unknown and repeated ids are dropped");
  assert.deepEqual(normalizeFilterExtras("news"), []);
  const pol = (cfg) => buildAgentPolicy(cfg, []).filterExtras;
  assert.deepEqual(pol({ browserFilter: "strict", filterExtras: ["news", "shopping", "social"] }), ["news", "shopping"], "Strict already closes social media");
  assert.deepEqual(pol({ browserFilter: "moderate", filterExtras: ["social", "news"] }), ["social", "news"], "Medium plus social media and news");
  assert.deepEqual(pol({ browserFilter: "off", filterExtras: ["news"] }), [], "Approved sites only: nothing else to close");
  assert.deepEqual(pol({ filterExtras: ["games"] }), [], "no level, no extras");
  const lines = summarizeConfigChange(normalizeConfig({}), normalizeConfig({ filterExtras: ["news", "games"] }));
  assert.ok(lines.some((l) => /Also closed in every browser: news, games/.test(l)), "the change is written to the dashboard log");
});