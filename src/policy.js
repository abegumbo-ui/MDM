// Pure functions: turn the dashboard's config + a device's reported apps into
// the instructions the on-device agent applies (which apps to hide, which restrictions to set).

// Packages that are never auto-hidden, so a lockdown can't brick the phone.
// You can still block any of them explicitly from the dashboard.
export const PROTECTED_EXACT = new Set([
  "android",
  "com.android.systemui",
  "com.android.settings",
  "com.android.vending",
  "com.android.phone",
  "com.android.server.telecom",
  "com.android.packageinstaller",
  "com.google.android.packageinstaller",
  "com.google.android.permissioncontroller",
  "com.android.permissioncontroller",
  "com.google.android.gms",
  "com.google.android.gsf",
  "com.google.android.webview",
  "com.android.webview",
  "com.android.documentsui",
  "com.google.android.documentsui",
  "com.familymdm.agent",
  "com.familymdm.browser",
]);
const PROTECTED_PATTERNS = [
  /^com\.android\.providers\./,
  /^com\.android\.inputmethod\./,
  /inputmethod/,
  /launcher/i,
  /^com\.android\.(bluetooth|nfc|networkstack|captiveportal|certinstaller|keychain|se|shell|emergency)/,
  /^com\.google\.android\.(networkstack|ext\.|modulemetadata|captiveportal)/,
  /^com\.qualcomm\./,
  /^com\.mediatek\./,
];

export function isProtected(pkg) {
  return PROTECTED_EXACT.has(pkg) || PROTECTED_PATTERNS.some((re) => re.test(pkg));
}

// Dashboard toggle -> Android UserManager restriction key.
export const RESTRICTIONS = {
  factoryResetDisabled: { key: "no_factory_reset", label: "Block factory reset from Settings", on: true },
  safeBootDisabled: { key: "no_safe_boot", label: "Block Safe Mode", on: true },
  uninstallAppsDisabled: { key: "no_uninstall_apps", label: "Block uninstalling apps", on: true },
  appsControlDisabled: { key: "no_control_apps", label: "Block changing apps in Settings", on: true },
  modifyAccountsDisabled: { key: "no_modify_accounts", label: "Block adding accounts", on: true },
  addUserDisabled: { key: "no_add_user", label: "Block adding users", on: true },
  installUnknownSourcesDisabled: { key: "no_install_unknown_sources", label: "Block installing from unknown sources", on: true },
  installAppsDisabled: { key: "no_install_apps", label: "Block ALL app installs (including Play Store)", on: false },
  configCredentialsDisabled: { key: "no_config_credentials", label: "Only the administrator can set the screen lock (the person can't set their own PIN or pattern)", on: false },
  // Also hides Developer options. USB debugging (adb) stops working while this is on;
  // use "Release device" in the dashboard (or recovery mode) to get back in.
  debuggingDisabled: { key: "no_debugging_features", label: "Block Developer options and USB debugging", on: true },
};

export const DEFAULT_RESTRICTIONS = Object.fromEntries(Object.entries(RESTRICTIONS).map(([k, v]) => [k, v.on]));

const HHMM = /^([01]\d|2[0-3]):[0-5]\d$/;

// ---- browser allowlist ----

function hostOf(u) {
  try {
    return new URL(u.includes("://") ? u : "https://" + u).hostname.toLowerCase().replace(/^www\./, "");
  } catch {
    return null;
  }
}

function pathKeyOf(u) {
  try {
    const url = new URL(u.includes("://") ? u : "https://" + u);
    const path = url.pathname.replace(/\/+$/, "") || "/";
    return url.hostname.toLowerCase().replace(/^www\./, "") + path;
  } catch {
    return null;
  }
}

// A short, hardcoded list of some of the best-known adult sites. No allowlist entry for one of
// these (or a subdomain of one) is ever accepted, from any source — the dashboard, the agent's
// offline mode, or a master-code approval. This is a safety net for a mistake, not the actual
// filter: the real protection is that the allowlist opens nothing unless it was explicitly
// approved. Keep this in step with SitePolicy.java's BLOCKED_ADULT_DOMAINS.
const BLOCKED_ADULT_DOMAINS = new Set([
  "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com", "youporn.com",
  "tube8.com", "spankbang.com", "beeg.com", "txxx.com", "porn.com", "pornone.com",
  "chaturbate.com", "livejasmin.com", "onlyfans.com", "brazzers.com", "motherless.com",
  "rule34.xxx", "e-hentai.org", "hentaihaven.xxx", "nhentai.net", "fapello.com",
  "thumbzilla.com", "eporner.com", "sex.com", "porntrex.com", "porndig.com", "4chan.org",
]);
export function isBlockedAdultHost(host) {
  return !!host && [...BLOCKED_ADULT_DOMAINS].some((d) => host === d || host.endsWith("." + d));
}

/** One allowlist entry: a whole domain (with subpages/subdomains) or one exact page. */
export function normalizeSite(raw) {
  if (!raw || typeof raw !== "object") return null;
  const type = raw.type === "exact" ? "exact" : raw.type === "domain" ? "domain" : null;
  const url = String(raw.url || "").trim();
  if (!type || !url) return null;
  const host = hostOf(url);
  if (!host || isBlockedAdultHost(host)) return null;
  return {
    type,
    url: url.includes("://") ? url : "https://" + url,
    host,
    label: String(raw.label || host).slice(0, 80),
    blockImages: raw.blockImages === true,
    installable: raw.installable !== false,
  };
}

/** {key: site} -> sorted array of valid, de-duplicated entries, for sending to the phone. */
export function normalizeSites(raw) {
  const out = [];
  const seen = new Set();
  for (const v of Object.values(raw && typeof raw === "object" ? raw : {})) {
    const s = normalizeSite(v);
    if (!s) continue;
    const key = s.type + ":" + (s.type === "domain" ? s.host : pathKeyOf(s.url));
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(s);
  }
  return out;
}

/** True if `url` is covered by one of the allowed sites (domain+subdomains, or the exact page). */
export function siteAllowed(url, sites) {
  const host = hostOf(url);
  const path = pathKeyOf(url);
  if (!host) return false;
  return sites.some(
    (s) => (s.type === "domain" && (host === s.host || host.endsWith("." + s.host))) || (s.type === "exact" && path === pathKeyOf(s.url)),
  );
}

/** A schedule is {days:[0-6, Sunday=0], from:"HH:MM", to:"HH:MM"}, or null if invalid. */
export function normalizeSchedule(s) {
  if (!s || typeof s !== "object" || !Array.isArray(s.days)) return null;
  const days = [...new Set(s.days.filter((d) => Number.isInteger(d) && d >= 0 && d <= 6))].sort();
  if (!days.length || !HHMM.test(s.from) || !HHMM.test(s.to) || s.from === s.to) return null;
  return { days, from: s.from, to: s.to };
}

/** Google account IDs (21-digit numbers) allowed to set the phone up again after a reset from recovery mode. */
export function normalizeFrpAccounts(list) {
  const ids = (Array.isArray(list) ? list : []).map((x) => String(x).replace(/^people\//, "").trim()).filter((x) => /^\d{15,25}$/.test(x));
  return [...new Set(ids)].slice(0, 3);
}

export function normalizeConfig(input) {
  const c = input || {};
  const frpAccounts = normalizeFrpAccounts(c.frpAccounts);
  const restrictions = { ...DEFAULT_RESTRICTIONS };
  for (const k of Object.keys(RESTRICTIONS)) {
    if (c.restrictions && typeof c.restrictions[k] === "boolean") restrictions[k] = c.restrictions[k];
  }
  const apps = {};
  for (const [pkg, a] of Object.entries(c.apps && typeof c.apps === "object" ? c.apps : {})) {
    if (a && ["allow", "force", "block"].includes(a.mode)) {
      const entry = { mode: a.mode, label: a.label };
      const schedule = normalizeSchedule(a.schedule);
      if (schedule && a.mode !== "block") entry.schedule = schedule;
      apps[pkg] = entry;
    }
  }
  // Kept keyed (like apps), so the dashboard can edit and label entries; normalizeSites()
  // flattens this into what's actually sent to the phone.
  const sites = {};
  for (const [key, raw] of Object.entries(c.sites && typeof c.sites === "object" ? c.sites : {})) {
    const s = normalizeSite(raw);
    if (s) sites[key] = s;
  }
  return { apps, // Off by default so a fresh device keeps working until you have chosen what to allow.
    blockUnlisted: c.blockUnlisted === true,
    // Newly installed apps stay hidden until you approve them.
    approveNew: c.approveNew === true,
    // Android only shows the Wi-Fi name when Location is on; this lets the agent turn it on (no location is collected).
    reportWifi: c.reportWifi !== false, autoUpdate: c.autoUpdate === true, homeScreen: c.homeScreen === true,
    // Makes the agent's own browser the phone's only handler for web links (so Chrome etc. stop opening them).
    restrictBrowsing: c.restrictBrowsing === true, frpAccounts,
    sites, restrictions };
}

/** Overrides made on the phone itself ({pkg: "allow"|"block"}), sanitized. */
export function normalizeOverrides(o) {
  const out = {};
  for (const [pkg, mode] of Object.entries(o && typeof o === "object" ? o : {})) {
    if (/^[A-Za-z0-9_.]{1,200}$/.test(pkg) && (mode === "allow" || mode === "block")) out[pkg] = mode;
  }
  return out;
}

/**
 * Instructions for one device, given the packages it reported.
 * opts.known: packages that existed before approval mode (null = unknown, nothing is held)
 * opts.overrides: changes made on the phone with the master code; they win over the dashboard
 */
export function buildAgentPolicy(config, reportedPackages = [], opts = {}) {
  const cfg = normalizeConfig(config);
  const apps = { ...cfg.apps };
  for (const [pkg, mode] of Object.entries(normalizeOverrides(opts.overrides))) {
    apps[pkg] = { mode, ...(mode === "allow" && cfg.apps[pkg]?.schedule ? { schedule: cfg.apps[pkg].schedule } : {}) };
  }
  const known = opts.known ? new Set(opts.known) : null;
  const hide = new Set();
  const show = [];
  const pending = [];
  const allowed = [];
  // Explicit blocks apply even to packages the device didn't report.
  for (const [pkg, a] of Object.entries(apps)) if (a.mode === "block") hide.add(pkg);
  for (const pkg of reportedPackages) {
    const mode = apps[pkg]?.mode;
    if (mode === "allow" || mode === "force") allowed.push(pkg);
    if (mode === "block") hide.add(pkg);
    else if (mode === "allow" || mode === "force") show.push(pkg);
    else if (cfg.approveNew && known && !known.has(pkg) && !isProtected(pkg)) {
      hide.add(pkg);
      pending.push(pkg);
    } else if (cfg.blockUnlisted && !isProtected(pkg)) hide.add(pkg);
    else show.push(pkg);
  }
  const restrictions = Object.entries(RESTRICTIONS)
    .filter(([k]) => cfg.restrictions[k])
    .map(([, v]) => v.key);
  // The phone checks these against its own clock, so apps open and close on time even offline.
  const schedules = {};
  for (const [pkg, a] of Object.entries(apps)) if (a.schedule) schedules[pkg] = a.schedule;
  // With approval mode on, the phone also gets the approved baseline so it can hold a new app
  // right away, even when it has no connection to the dashboard.
  // homeScreen: the agent becomes the home screen; only `allowed` apps can be opened (blocked apps keep running).
  const out = { hide: [...hide], show, allowed, restrictions, schedules, pending, approveNew: cfg.approveNew, reportWifi: cfg.reportWifi, autoUpdate: cfg.autoUpdate, homeScreen: cfg.homeScreen, restrictBrowsing: cfg.restrictBrowsing, frpAccounts: cfg.frpAccounts, sites: normalizeSites(cfg.sites) };
  if (cfg.approveNew && known) out.known = [...known];
  return out;
}
