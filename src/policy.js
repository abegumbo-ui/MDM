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
  // Also hides Developer options. USB debugging (adb) stops working while this is on;
  // use "Release device" in the dashboard (or recovery mode) to get back in.
  debuggingDisabled: { key: "no_debugging_features", label: "Block Developer options and USB debugging", on: true },
};

export const DEFAULT_RESTRICTIONS = Object.fromEntries(Object.entries(RESTRICTIONS).map(([k, v]) => [k, v.on]));

const HHMM = /^([01]\d|2[0-3]):[0-5]\d$/;

/** A schedule is {days:[0-6, Sunday=0], from:"HH:MM", to:"HH:MM"}, or null if invalid. */
export function normalizeSchedule(s) {
  if (!s || typeof s !== "object" || !Array.isArray(s.days)) return null;
  const days = [...new Set(s.days.filter((d) => Number.isInteger(d) && d >= 0 && d <= 6))].sort();
  if (!days.length || !HHMM.test(s.from) || !HHMM.test(s.to) || s.from === s.to) return null;
  return { days, from: s.from, to: s.to };
}

export function normalizeConfig(input) {
  const c = input || {};
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
  return { apps, // Off by default so a fresh device keeps working until you have chosen what to allow.
    blockUnlisted: c.blockUnlisted === true,
    // Newly installed apps stay hidden until you approve them.
    approveNew: c.approveNew === true,
    // Android only shows the Wi-Fi name when Location is on; this lets the agent turn it on (no location is collected).
    reportWifi: c.reportWifi !== false, autoUpdate: c.autoUpdate === true, restrictions };
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
  // Explicit blocks apply even to packages the device didn't report.
  for (const [pkg, a] of Object.entries(apps)) if (a.mode === "block") hide.add(pkg);
  for (const pkg of reportedPackages) {
    const mode = apps[pkg]?.mode;
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
  const out = { hide: [...hide], show, restrictions, schedules, pending, approveNew: cfg.approveNew, reportWifi: cfg.reportWifi, autoUpdate: cfg.autoUpdate };
  if (cfg.approveNew && known) out.known = [...known];
  return out;
}
