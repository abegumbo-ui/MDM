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
  // Off by default while testing: keeps USB debugging usable as a way back in.
  debuggingDisabled: { key: "no_debugging_features", label: "Block USB debugging", on: false },
};

export const DEFAULT_RESTRICTIONS = Object.fromEntries(Object.entries(RESTRICTIONS).map(([k, v]) => [k, v.on]));

export function normalizeConfig(input) {
  const c = input || {};
  const restrictions = { ...DEFAULT_RESTRICTIONS };
  for (const k of Object.keys(RESTRICTIONS)) {
    if (c.restrictions && typeof c.restrictions[k] === "boolean") restrictions[k] = c.restrictions[k];
  }
  const apps = {};
  for (const [pkg, a] of Object.entries(c.apps && typeof c.apps === "object" ? c.apps : {})) {
    if (a && ["allow", "force", "block"].includes(a.mode)) apps[pkg] = { mode: a.mode, label: a.label };
  }
  return { apps, // Off by default so a fresh device keeps working until you have chosen what to allow.
    blockUnlisted: c.blockUnlisted === true, restrictions };
}

/** Instructions for one device, given the packages it reported. */
export function buildAgentPolicy(config, reportedPackages = []) {
  const cfg = normalizeConfig(config);
  const hide = new Set();
  const show = [];
  // Explicit blocks apply even to packages the device didn't report.
  for (const [pkg, a] of Object.entries(cfg.apps)) if (a.mode === "block") hide.add(pkg);
  for (const pkg of reportedPackages) {
    const mode = cfg.apps[pkg]?.mode;
    if (mode === "block") hide.add(pkg);
    else if (mode === "allow" || mode === "force") show.push(pkg);
    else if (cfg.blockUnlisted && !isProtected(pkg)) hide.add(pkg);
    else show.push(pkg);
  }
  const restrictions = Object.entries(RESTRICTIONS)
    .filter(([k]) => cfg.restrictions[k])
    .map(([, v]) => v.key);
  return { hide: [...hide], show, restrictions };
}
