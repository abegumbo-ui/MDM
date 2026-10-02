// Pure functions: turn the dashboard's simple config into an Android Management API policy.

// Packages that are never auto-blocked, so a lockdown can't brick the phone.
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
  "com.google.android.apps.work.clouddpc",
  "com.google.android.apps.restore",
]);
const PROTECTED_PATTERNS = [
  /^com\.android\.providers\./,
  /^com\.android\.inputmethod\./,
  /inputmethod/,
  /launcher/i,
  /^com\.android\.(bluetooth|nfc|networkstack|captiveportal|certinstaller|keychain|se|shell)/,
  /^com\.google\.android\.(networkstack|ext\.|modulemetadata|captiveportal)/,
  /^com\.qualcomm\./,
  /^com\.mediatek\./,
];

export function isProtected(pkg) {
  return PROTECTED_EXACT.has(pkg) || PROTECTED_PATTERNS.some((re) => re.test(pkg));
}

export const DEFAULT_CONFIG = {
  // apps[pkg] = { mode: "allow" | "force" | "block", label?: string }
  apps: {},
  // Block every app seen on the device that you haven't allowed (except protected ones).
  blockUnlisted: true,
  restrictions: {
    uninstallAppsDisabled: true,
    factoryResetDisabled: true,
    safeBootDisabled: true,
    modifyAccountsDisabled: true,
    addUserDisabled: true,
    debuggingFeaturesAllowed: false,
    installUnknownSourcesAllowed: false,
  },
  // Raw AMAPI policy fields merged last, for anything the dashboard doesn't cover.
  extraPolicy: {},
};

export function normalizeConfig(input) {
  const c = input || {};
  return {
    apps: c.apps && typeof c.apps === "object" ? c.apps : {},
    blockUnlisted: c.blockUnlisted !== false,
    restrictions: { ...DEFAULT_CONFIG.restrictions, ...(c.restrictions || {}) },
    extraPolicy: c.extraPolicy && typeof c.extraPolicy === "object" ? c.extraPolicy : {},
  };
}

/**
 * @param config  normalized dashboard config
 * @param seenPackages  package names reported by enrolled devices (used for blockUnlisted)
 */
export function buildPolicy(config, seenPackages = []) {
  const cfg = normalizeConfig(config);
  const apps = new Map();

  for (const [pkg, a] of Object.entries(cfg.apps)) {
    const installType =
      a.mode === "force" ? "FORCE_INSTALLED" : a.mode === "block" ? "BLOCKED" : "AVAILABLE";
    const entry = { packageName: pkg, installType };
    if (installType !== "BLOCKED") entry.autoUpdateMode = "AUTO_UPDATE_HIGH_PRIORITY";
    apps.set(pkg, entry);
  }

  if (cfg.blockUnlisted) {
    for (const pkg of seenPackages) {
      if (!apps.has(pkg) && !isProtected(pkg)) {
        apps.set(pkg, { packageName: pkg, installType: "BLOCKED" });
      }
    }
  }

  return {
    // Only apps listed in this policy can be installed from Google Play.
    playStoreMode: "WHITELIST",
    applications: [...apps.values()],
    ...cfg.restrictions,
    statusReportingSettings: {
      applicationReportsEnabled: true,
      softwareInfoEnabled: true,
      deviceSettingsEnabled: true,
    },
    systemUpdate: { type: "AUTOMATIC" },
    ...cfg.extraPolicy,
  };
}

/** Extract unique package names from AMAPI device resources. */
export function packagesFromDevices(devices) {
  const set = new Set();
  for (const d of devices) for (const r of d.applicationReports || []) set.add(r.packageName);
  return [...set].sort();
}
