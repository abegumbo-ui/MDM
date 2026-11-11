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
  locationConfigDisabled: { key: "no_config_location", label: "Block changing Location settings", on: false },
  airplaneModeDisabled: { key: "no_airplane_mode", label: "Block turning on Airplane mode", on: false },
  mobileNetworksDisabled: { key: "no_config_mobile_networks", label: "Block changing mobile network settings", on: false },
  tetheringDisabled: { key: "no_config_tethering", label: "Block Wi-Fi hotspot and tethering", on: false },
  vpnConfigDisabled: { key: "no_config_vpn", label: "Block adding or changing a VPN", on: false },
  // Unlike the rest of this list, Android's own constant for this one is spelled out in full --
  // "disallow_config_private_dns", not the usual "no_..." shorthand -- so it doesn't follow the
  // pattern the other keys do.
  privateDnsDisabled: { key: "disallow_config_private_dns", label: "Block changing Private DNS", on: false },

  // Everything below is every other android.os.UserManager.DISALLOW_* restriction that exists,
  // verified directly against current AOSP source (not guessed from the naming pattern -- that's
  // exactly how the Private DNS key above ended up wrong once already). All default off: being
  // listed here doesn't mean it does anything until it's explicitly turned on. Several of these
  // (the "profile"/"user switch" ones) only matter on a device with secondary users or a work
  // profile, which this setup doesn't use -- they're included anyway so there's one place to see
  // (and turn on, if it's ever relevant) everything Android itself offers.
  wifiConfigDisabled: { key: "no_config_wifi", label: "Block changing Wi-Fi settings", on: false },
  wifiStateDisabled: { key: "no_change_wifi_state", label: "Block turning Wi-Fi on or off", on: false },
  wifiTetheringDisabled: { key: "no_wifi_tethering", label: "Block Wi-Fi tethering", on: false },
  sharingAdminWifiDisabled: { key: "no_sharing_admin_configured_wifi", label: "Block sharing admin-configured Wi-Fi (QR code, password)", on: false },
  wifiDirectDisabled: { key: "no_wifi_direct", label: "Block Wi-Fi Direct", on: false },
  addWifiConfigDisabled: { key: "no_add_wifi_config", label: "Block adding new Wi-Fi networks", on: false },
  localeDisabled: { key: "no_config_locale", label: "Block changing language and region", on: false },
  shareLocationDisabled: { key: "no_share_location", label: "Block sharing location", on: false },
  brightnessDisabled: { key: "no_config_brightness", label: "Block changing screen brightness", on: false },
  ambientDisplayDisabled: { key: "no_ambient_display", label: "Block ambient display (always-on display)", on: false },
  screenTimeoutDisabled: { key: "no_config_screen_timeout", label: "Block changing screen timeout", on: false },
  configBluetoothDisabled: { key: "no_config_bluetooth", label: "Block changing Bluetooth settings", on: false },
  bluetoothDisabled: { key: "no_bluetooth", label: "Turn off Bluetooth entirely", on: false },
  bluetoothSharingDisabled: { key: "no_bluetooth_sharing", label: "Block sharing files over Bluetooth", on: false },
  usbFileTransferDisabled: { key: "no_usb_file_transfer", label: "Block USB file transfer", on: false },
  removeUserDisabled: { key: "no_remove_user", label: "Block removing users (not relevant without secondary users)", on: false },
  removeManagedProfileDisabled: { key: "no_remove_managed_profile", label: "Block removing a work profile (not relevant without one)", on: false },
  dateTimeDisabled: { key: "no_config_date_time", label: "Block changing date and time", on: false },
  networkResetDisabled: { key: "no_network_reset", label: "Block \"Reset network settings\"", on: false },
  addManagedProfileDisabled: { key: "no_add_managed_profile", label: "Block adding a work profile", on: false },
  addCloneProfileDisabled: { key: "no_add_clone_profile", label: "Block adding a cloned app profile", on: false },
  addPrivateProfileDisabled: { key: "no_add_private_profile", label: "Block adding a private space profile", on: false },
  cellBroadcastsDisabled: { key: "no_config_cell_broadcasts", label: "Block changing emergency cell broadcast settings", on: false },
  physicalMediaDisabled: { key: "no_physical_media", label: "Block mounting SD cards or USB storage", on: false },
  unmuteMicDisabled: { key: "no_unmute_microphone", label: "Keep the microphone forced muted", on: false },
  adjustVolumeDisabled: { key: "no_adjust_volume", label: "Block changing volume", on: false },
  outgoingCallsDisabled: { key: "no_outgoing_calls", label: "Block making calls (emergency calls still work)", on: false },
  smsDisabled: { key: "no_sms", label: "Block sending and receiving SMS", on: false },
  funDisabled: { key: "no_fun", label: "Disable the \"Easter egg\" (build-number tap tricks)", on: false },
  createWindowsDisabled: { key: "no_create_windows", label: "Block apps from drawing over other apps", on: false },
  systemErrorDialogsDisabled: { key: "no_system_error_dialogs", label: "Suppress crash and \"app not responding\" dialogs", on: false },
  crossProfileCopyPasteDisabled: { key: "no_cross_profile_copy_paste", label: "Block copy/paste between profiles", on: false },
  outgoingBeamDisabled: { key: "no_outgoing_beam", label: "Block Android Beam (old NFC sharing)", on: false },
  wallpaperDisabled: { key: "no_wallpaper", label: "Block viewing or changing wallpaper at all", on: false },
  setWallpaperDisabled: { key: "no_set_wallpaper", label: "Block changing wallpaper (can still view it)", on: false },
  recordAudioDisabled: { key: "no_record_audio", label: "Block every app from recording audio", on: false },
  runInBackgroundDisabled: { key: "no_run_in_background", label: "Stop apps from running in the background", on: false },
  cameraDisabled: { key: "no_camera", label: "Disable the camera entirely", on: false },
  unmuteDeviceDisabled: { key: "disallow_unmute_device", label: "Keep the ringer forced silent", on: false },
  dataRoamingDisabled: { key: "no_data_roaming", label: "Block enabling data roaming", on: false },
  setUserIconDisabled: { key: "no_set_user_icon", label: "Block changing the profile icon", on: false },
  oemUnlockDisabled: { key: "no_oem_unlock", label: "Block unlocking the bootloader", on: false },
  unifiedPasswordDisabled: { key: "no_unified_password", label: "Force a separate work-profile password (not relevant without one)", on: false },
  autofillDisabled: { key: "no_autofill", label: "Block the autofill service", on: false },
  contentCaptureDisabled: { key: "no_content_capture", label: "Block content capture (used by some assistants)", on: false },
  contentSuggestionsDisabled: { key: "no_content_suggestions", label: "Block content suggestions", on: false },
  userSwitchDisabled: { key: "no_user_switch", label: "Block switching between users (not relevant without secondary users)", on: false },
  shareIntoProfileDisabled: { key: "no_sharing_into_profile", label: "Block sharing content into a work profile", on: false },
  printingDisabled: { key: "no_printing", label: "Block printing", on: false },
  micToggleDisabled: { key: "disallow_microphone_toggle", label: "Block the quick-settings microphone privacy toggle", on: false },
  cameraToggleDisabled: { key: "disallow_camera_toggle", label: "Block the quick-settings camera privacy toggle", on: false },
  biometricDisabled: { key: "disallow_biometric", label: "Block enrolling fingerprint or face unlock", on: false },
  defaultAppsDisabled: { key: "disallow_config_default_apps", label: "Block changing default apps (browser, etc.)", on: false },
  cellular2gDisabled: { key: "no_cellular_2g", label: "Block allowing 2G cellular connections", on: false },
  uwbDisabled: { key: "no_ultra_wideband_radio", label: "Block Ultra-wideband radio", on: false },
  nfcDisabled: { key: "no_near_field_communication_radio", label: "Turn off NFC entirely", on: false },
  changeNfcDisabled: { key: "no_change_near_field_communication_radio", label: "Block changing NFC on/off", on: false },
  threadNetworkDisabled: { key: "no_thread_network", label: "Block Thread network radio (smart-home)", on: false },
  simGloballyDisabled: { key: "no_sim_globally", label: "Disable the SIM entirely", on: false },
  assistContentDisabled: { key: "no_assist_content", label: "Block sharing screen content with the assistant", on: false },
  unknownSourcesGloballyDisabled: { key: "no_install_unknown_sources_globally", label: "Block installing from unknown sources, for every user", on: false },
};

export const DEFAULT_RESTRICTIONS = Object.fromEntries(Object.entries(RESTRICTIONS).map(([k, v]) => [k, v.on]));

// The agent only knows the raw android.os.UserManager.DISALLOW_* key (what it actually passes to
// dpm.addUserRestriction); this maps back to the friendly config key above, so a restriction
// flipped on the phone itself can be folded into the same cfg.restrictions the dashboard edits.
export const RESTRICTION_BY_KEY = Object.fromEntries(Object.entries(RESTRICTIONS).map(([k, v]) => [v.key, k]));

const HHMM = /^([01]\d|2[0-3]):[0-5]\d$/;

// ---- browser allowlist ----

function hostOf(u) {
  try {
    return new URL(u.includes("://") ? u : "https://" + u).hostname.toLowerCase().replace(/^www\./, "");
  } catch {
    return null;
  }
}

const YOUTUBE_HOSTS = new Set(["youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com", "youtu.be"]);

/** The 11-character id of the video this address plays (watch, youtu.be, shorts, embed, live), or null. Mirrors SitePolicy.youtubeVideoId. */
export function youtubeVideoId(u) {
  try {
    const url = new URL(u.includes("://") ? u : "https://" + u);
    const host = url.hostname.toLowerCase().replace(/^www\./, "");
    if (!YOUTUBE_HOSTS.has(host)) return null;
    let id = null;
    if (host === "youtu.be") id = url.pathname.split("/")[1] || null;
    else if (url.pathname === "/watch") id = url.searchParams.get("v");
    else {
      const m = /^\/(?:shorts|embed|live|v)\/([^/?#]+)/.exec(url.pathname);
      if (m) id = m[1];
    }
    return id && /^[A-Za-z0-9_-]{11}$/.test(id) ? id : null;
  } catch {
    return null;
  }
}

/** The "@handle" (lower case) of the channel page this address is, or null. Mirrors SitePolicy.youtubeChannelHandle. */
export function youtubeChannelHandle(u) {
  try {
    const url = new URL(u.includes("://") ? u : "https://" + u);
    const host = url.hostname.toLowerCase().replace(/^www\./, "");
    if (host !== "youtube.com" && host !== "m.youtube.com") return null;
    const m = /^\/(@[A-Za-z0-9._-]{3,30})(?:\/|$)/.exec(url.pathname);
    return m ? m[1].toLowerCase() : null;
  } catch {
    return null;
  }
}

function pathKeyOf(u) {
  // Every YouTube video shares the address "youtube.com/watch": only ?v= tells them apart. Without
  // this, allowing one video "page" would quietly allow every video.
  const video = youtubeVideoId(u);
  if (video) return "youtube.com/watch?v=" + video;
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
  if (raw.type === "youtube") {
    // A whole YouTube channel, given as its "@handle" page address (or the handle itself). Everything
    // else on YouTube (home, search, other channels' videos) stays blocked.
    const handle = youtubeChannelHandle(String(raw.url || "")) || (/^@[A-Za-z0-9._-]{3,30}$/.test(String(raw.id || "")) ? String(raw.id).toLowerCase() : null);
    if (!handle) return null;
    return {
      type: "youtube",
      kind: "channel",
      id: handle,
      url: "https://www.youtube.com/" + handle,
      host: "youtube.com",
      label: String(raw.label || "YouTube " + handle).slice(0, 80),
      blockImages: false,
      installable: false,
    };
  }
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
    const key = s.type + ":" + (s.type === "domain" ? s.host : s.type === "youtube" ? s.id : pathKeyOf(s.url));
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
    (s) => s.type !== "youtube" && (s.type === "domain" && (host === s.host || host.endsWith("." + s.host))) || (s.type === "exact" && path === pathKeyOf(s.url)),
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

export const BROWSER_FILTERS = ["off", "moderate", "strict"];

/**
 * Screen time: dailyLimit is minutes a day for the whole phone (0 = no limit); bedtime is a
 * {days, from, to} window when the phone is locked; appLimits is minutes a day per app; exempt is
 * apps that do not count toward the daily limit (Phone, Maps...).
 */
export function normalizeScreenTime(raw) {
  const c = raw && typeof raw === "object" ? raw : {};
  const clamp = (v) => (Number.isInteger(v) ? Math.min(Math.max(v, 0), 24 * 60) : 0);
  const appLimits = {};
  for (const [pkg, m] of Object.entries(c.appLimits && typeof c.appLimits === "object" ? c.appLimits : {})) {
    if (/^[A-Za-z0-9_.]{1,200}$/.test(pkg) && clamp(m) > 0) appLimits[pkg] = clamp(m);
  }
  const exempt = [...new Set((Array.isArray(c.exempt) ? c.exempt : []).filter((p) => typeof p === "string" && /^[A-Za-z0-9_.]{1,200}$/.test(p)))].slice(0, 100);
  return { dailyLimit: clamp(c.dailyLimit), bedtime: normalizeSchedule(c.bedtime), appLimits, exempt };
}

// Whole groups of apps that can be blocked with one switch, like other parental-control apps offer.
// An app you explicitly set to Allow is never blocked by its group, so you can block "all social
// media" and still leave one in. Messaging apps (WhatsApp, Signal, SMS) are deliberately not here.
export const APP_GROUPS = {
  social: {
    label: "Social media apps",
    packages: [
      "com.facebook.katana", "com.facebook.lite", "com.facebook.orca", "com.facebook.mlite",
      "com.instagram.android", "com.instagram.lite", "com.instagram.barcelona",
      "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go",
      "com.snapchat.android", "com.twitter.android", "com.twitter.android.lite",
      "com.reddit.frontpage", "com.pinterest", "com.tumblr", "com.discord",
      "org.telegram.messenger", "tv.twitch.android.app", "com.bereal.ft",
    ],
  },
  // categories: what the app itself tells Android it is (ApplicationInfo.category; 0 = game,
  // 2 = video). That catches games and video apps nobody listed by name. Apps that do not say,
  // are caught by the names below.
  games: {
    label: "Games",
    categories: [0],
    packages: [
      "com.roblox.client", "com.mojang.minecraftpe", "com.supercell.clashofclans", "com.supercell.clashroyale",
      "com.supercell.brawlstars", "com.epicgames.fortnite", "com.king.candycrushsaga", "com.activision.callofduty.shooter",
      "com.tencent.ig", "com.dts.freefireth", "com.innersloth.spacemafia", "com.kiloo.subwaysurf", "com.miniclip.eightballpool",
      "com.outfit7.mytalkingtomfree", "com.ea.gp.fifamobile", "com.nianticlabs.pokemongo", "com.chess", "com.playrix.homescapes",
    ],
  },
  video: {
    label: "Video and streaming apps",
    categories: [2],
    packages: [
      "com.google.android.youtube", "com.google.android.apps.youtube.kids", "com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient",
      "com.disney.disneyplus", "com.hulu.plus", "com.wbd.stream", "com.peacocktv.peacockandroid", "com.cbs.app", "com.plexapp.android",
      "com.vimeo.android.videoapp", "tv.pluto.android", "com.tubitv", "com.dailymotion.dailymotion",
    ],
  },
  dating: {
    label: "Dating apps",
    packages: [
      "com.tinder", "co.hinge.app", "com.bumble.app", "com.okcupid.okcupid", "com.match.android.matchmobile", "com.grindrapp.android",
      "com.pof.android", "com.badoo.mobile", "com.coffeemeetsbagel", "ru.mamba.client", "com.taimi.android", "com.jdate.android",
    ],
  },
  shopping: {
    label: "Shopping apps",
    packages: [
      "com.amazon.mShop.android.shopping", "com.ebay.mobile", "com.einnovation.temu", "com.zzkko", "com.alibaba.aliexpresshd",
      "com.contextlogic.wish", "com.walmart.android", "com.target.ui", "com.etsy.android", "com.shopify.arrive",
    ],
  },
};

/** The packages of these reported apps that a group covers, by name or by the category the app declares. */
export function inGroup(group, pkg, category) {
  const g = APP_GROUPS[group];
  return !!g && (g.packages.includes(pkg) || (Number.isInteger(category) && (g.categories || []).includes(category)));
}

/** Blocked words: lower-case, 1-60 characters, letters/digits (Hebrew included) and single spaces; at most 200. Mirrors WordFilter.clean. */
export function normalizeBlockedWords(list) {
  const out = [];
  for (const raw of Array.isArray(list) ? list : []) {
    if (typeof raw !== "string") continue;
    const w = raw.toLowerCase().split(/[^a-z0-9\u0590-\u05ff]+/).filter(Boolean).join(" ");
    if (w && w.length <= 60 && !out.includes(w)) out.push(w);
    if (out.length >= 200) break;
  }
  return out;
}

/** Chrome's own settings the dashboard can lock (see PolicyApplier.pushChromeConfig). */
function normalizeChromePolicy(c) {
  const o = c && typeof c === "object" ? c : {};
  return { noIncognito: o.noIncognito === true, adultFilter: o.adultFilter === true, noSignin: o.noSignin === true };
}

// The buttons on the phone's own "User" screen. Each can be hidden per phone from the dashboard.
export const USER_MENU = {
  site: "Request a site",
  app: "Request a blocked app",
  store: "Open Google Play Store",
  message: "Message the administrator",
  install: "Install an app from a file (with a code)",
  updates: "Check for app updates",
};
function normalizeUserMenu(m) {
  const out = {};
  for (const k of Object.keys(USER_MENU)) out[k] = !(m && typeof m === "object" && m[k] === false); // shown unless switched off
  return out;
}
// YouTube in the browsers: "channels" = only allowed channels and videos open; "restricted" = YouTube
// opens with search but no recommendations (home, feeds and Shorts are blocked); "auto" (the default)
// = follow the browser filter level: Moderate -> restricted, Strict or Off -> channels.
export const YOUTUBE_MODES = ["auto", "channels", "restricted"];

/** The YouTube mode actually applied: "channels" or "restricted", whatever the setting is. */
export function effectiveYoutubeMode(cfg) {
  const m = cfg && cfg.youtubeMode;
  if (m === "channels" || m === "restricted") return m;
  return cfg && cfg.browserFilter === "moderate" ? "restricted" : "channels";
}

export function normalizeConfig(input) {
  const c = input || {};
  const frpAccounts = normalizeFrpAccounts(c.frpAccounts);
  const restrictions = { ...DEFAULT_RESTRICTIONS };
  for (const k of Object.keys(RESTRICTIONS)) {
    if (c.restrictions && typeof c.restrictions[k] === "boolean") restrictions[k] = c.restrictions[k];
  }
  const homeScreen = c.homeScreen === true;
  const apps = {};
  for (const [pkg, a] of Object.entries(c.apps && typeof c.apps === "object" ? c.apps : {})) {
    // "soft" only means anything while Home screen mode is on; once it's off, a leftover "soft"
    // entry would otherwise sit there forever (never selectable in the dashboard, never hidden
    // either) instead of going back to Default like the dashboard already shows it doing.
    if (a && a.mode === "soft" && !homeScreen) continue;
    if (a && ["allow", "force", "block", "soft"].includes(a.mode)) {
      const entry = { mode: a.mode, label: a.label };
      const schedule = normalizeSchedule(a.schedule);
      if (schedule && a.mode !== "block" && a.mode !== "soft") entry.schedule = schedule;
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
    reportWifi: c.reportWifi !== false, autoUpdate: c.autoUpdate === true, homeScreen,
    // Makes the agent's own browser the phone's only handler for web links (so Chrome etc. stop opening them).
    restrictBrowsing: c.restrictBrowsing === true, frpAccounts,
    // How the Browser filters the open web: "off" keeps it allowlist-only (nothing opens unless approved);
    // "moderate" and "strict" open the web but block whole categories (see tools/build-blocklists.mjs).
    browserFilter: BROWSER_FILTERS.includes(c.browserFilter) ? c.browserFilter : "off",
    youtubeMode: YOUTUBE_MODES.includes(c.youtubeMode) ? c.youtubeMode : "auto",
    screenTime: normalizeScreenTime(c.screenTime),
    chromePolicy: normalizeChromePolicy(c.chromePolicy),
    blockedWords: normalizeBlockedWords(c.blockedWords),
    // Forces SafeSearch on Google, Bing and DuckDuckGo for every browser and app (see SafeSearchDns.java).
    safeSearch: c.safeSearch === true,
    // Forces YouTube's own Restricted Mode everywhere, the YouTube app included: "off", "moderate" or "strict".
    youtubeDns: ["moderate", "strict"].includes(c.youtubeDns) ? c.youtubeDns : "off",
    // Hides pictures on web pages: always in our own browsers; in Chrome and others only with inspection on.
    blockImages: c.blockImages === true,
    imagesAllow: normalizeDnsAllowlist(c.imagesAllow),
    blockAppGroups: [...new Set((Array.isArray(c.blockAppGroups) ? c.blockAppGroups : []).filter((g) => Object.prototype.hasOwnProperty.call(APP_GROUPS, g)))],
    userMenu: normalizeUserMenu(c.userMenu),
    // Optional: the Agent installs a certificate of its own and runs a local proxy, so browsers' secure
    // pages can be filtered by what is on them (see TlsInspection.java). Off unless chosen, per phone.
    tlsInspection: c.tlsInspection === true,
    // Sites never looked inside even when inspection is on (banks and the like), added to the Agent's built-in list.
    inspectBypass: normalizeDnsAllowlist(c.inspectBypass),
    // Android caps any single freeze at 90 days and forces a 60-day gap before the next one --
    // there's no API for a true permanent block. This is the closest legal approximation.
    freezeUpdates: c.freezeUpdates === true,
    // Closes the trick where a person grants a sideloaded app an accessibility service to get
    // around app controls. While this is on, no app's accessibility service runs at all --
    // including ones used for real accessibility needs, so only turn it on if nobody here needs one.
    blockAccessibility: c.blockAccessibility === true,
    // The phone's own master-code Administrator panel refuses to open at all while this is on --
    // every change has to come from here instead. Ignored on a standalone (no-dashboard) phone,
    // which has no other way to be managed at all.
    phoneAdminLocked: c.phoneAdminLocked === true,
    // Hides the agent's own icon from the launcher/app drawer -- device-owner status and every
    // other restriction keep working exactly the same either way. Dialing *#*#636#*#* on the
    // phone's own dialer brings it back too, so this isn't a one-way door even offline.
    hideAppIcon: c.hideAppIcon === true,
    // The "Block enrolling fingerprint or face unlock" restriction only stops a NEW biometric from
    // being set up -- it does nothing to one already enrolled. This is the actual off switch:
    // disables fingerprint/face at the keyguard itself (forces PIN/pattern/password), independent
    // of that restriction and of whatever's already enrolled.
    disableBiometricUnlock: c.disableBiometricUnlock === true,
    // Home screen mode's own launcher always shows an "Administrator" button below the allowed
    // apps so whoever set it up can get back in -- this hides that button for everyone else who
    // picks up the phone, same as hiding the app icon does outside home screen mode.
    hideKioskAdmin: c.hideKioskAdmin === true,
    // The lighter, built-in Browser add-on (its own icon, right inside this app, no separate
    // install) -- same on/off switch as the phone's own Add-ons screen, just reachable remotely too.
    browserAddonEnabled: c.browserAddonEnabled === true,
    // Whole-package hides (Android's setApplicationHidden, not the regular per-app Block mode) --
    // same list the phone's own Blocking > Block Whole Apps screen edits, just reachable remotely
    // too. {pkg: {paused: bool}}.
    wholeAppBlocklist: normalizeWholeAppBlocklist(c.wholeAppBlocklist),
    // The device's own local DNS filter -- see normalizeDnsBlocklist's own doc comment for how
    // this differs from the Browser's URL-level site allowlist above.
    dnsBlocklist: normalizeDnsBlocklist(c.dnsBlocklist),
    // Overrides dnsBlocklist for a hostname that matches both -- see normalizeDnsAllowlist's doc.
    dnsAllowlist: normalizeDnsAllowlist(c.dnsAllowlist),
    // Cuts an app's network off entirely (it still runs, just can't reach anything) -- a
    // different thing from wholeAppBlocklist above, which stops the app running at all. No
    // dashboard editor for this one yet, phone-only (Blocking > Block Whole Network), but the
    // sync plumbing is built now so the dashboard side is a pure UI addition later. {pkg:
    // {paused: bool}}, same shape as wholeAppBlocklist.
    networkBlocklist: normalizeWholeAppBlocklist(c.networkBlocklist),
    // The only exception to networkBlocklist: hostnames listed per-package here are let through
    // for that app specifically. {pkg: [host, ...]}. Deliberately NOT the same list as
    // dnsAllowlist above -- see PerAppDnsAllowlist's own doc comment on the phone for why a
    // network-blocked app doesn't get the device-wide allow list too.
    perAppDnsAllowlist: normalizePerAppDnsAllowlist(c.perAppDnsAllowlist),
    // The DNS filter notification's own title/text -- editable here or on the phone's own
    // Administrator panel (whichever changed more recently wins, same mirroring as hideAppIcon
    // etc. above). Empty/missing means "use the built-in default".
    dnsFilterNotifTitle: typeof c.dnsFilterNotifTitle === "string" ? c.dnsFilterNotifTitle.slice(0, 100) : "",
    dnsFilterNotifText: typeof c.dnsFilterNotifText === "string" ? c.dnsFilterNotifText.slice(0, 100) : "",
    sites, restrictions };
}

/** {pkg: {paused: bool}} for the whole-app blocklist, sanitized -- same shape round-tripped both
 * ways, since the phone needs the whole map to reconcile its local list against, not just a
 * derived array. */
export function normalizeWholeAppBlocklist(o) {
  const out = {};
  for (const [pkg, v] of Object.entries(o && typeof o === "object" ? o : {})) {
    if (/^[A-Za-z0-9_.]{1,200}$/.test(pkg)) out[pkg] = { paused: !!(v && v.paused) };
  }
  return out;
}

/** Hostnames for the device's own local DNS filter (DnsFilterVpnService) -- a different thing
 * from the Browser's own URL-level site allowlist: this works at the whole-device network level,
 * blocking a hostname (and every subdomain of it) no matter which app asked for it. Plain strings,
 * not objects -- nothing per-entry to track (no pause state, no label) the way the whole-app
 * blocklist has. */
function sanitizeHostnameList(list) {
  const out = new Set();
  for (const raw of Array.isArray(list) ? list : []) {
    let h = String(raw || "")
      .trim()
      .toLowerCase();
    if (!h || h.startsWith("#") || h.startsWith("!")) continue; // a blank or comment line from a pasted list
    // Pasted blocklists rarely come as bare hostnames -- accept the common export shapes
    // (AdBlock-style "||host^", a full URL, a "*." wildcard prefix) by reducing them to the
    // same bare hostname a plain line would be, rather than silently dropping every line that
    // isn't already in that exact shape.
    h = h.replace(/^\|\|/, "").replace(/\^$/, "");
    h = h.replace(/^https?:\/\//, "");
    h = h.replace(/\/.*$/, "");
    h = h.replace(/^\*\.?/, "");
    if (/^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$/.test(h)) out.add(h);
  }
  return [...out].slice(0, 500);
}
export function normalizeDnsBlocklist(list) {
  return sanitizeHostnameList(list);
}

/** Overrides normalizeDnsBlocklist for a hostname that matches both -- for when a block entry is
 * deliberately broad (a whole CDN domain) but one specific host under it is still needed. Same
 * shape and same tolerant parsing as the block list; the agent checks both the same way
 * (DnsBlocklist.blocks() && !DnsAllowlist.allows()) and this just mirrors that server-side. */
export function normalizeDnsAllowlist(list) {
  return sanitizeHostnameList(list);
}

/** {pkg: [host, ...]} -- the only exception to networkBlocklist, per package. Each package's own
 * hostname list goes through the same tolerant parsing as dnsBlocklist/dnsAllowlist. */
export function normalizePerAppDnsAllowlist(o) {
  const out = {};
  for (const [pkg, list] of Object.entries(o && typeof o === "object" ? o : {})) {
    if (!/^[A-Za-z0-9_.]{1,200}$/.test(pkg)) continue;
    const hosts = sanitizeHostnameList(list);
    if (hosts.length) out[pkg] = hosts;
  }
  return out;
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
  const show = new Set();
  const pending = [];
  const allowed = new Set();
  // Explicit blocks, soft-blocks and allows apply even to packages the device didn't report (or
  // didn't report on this particular sync) -- otherwise an app an admin explicitly allowed could
  // silently drop out of `allowed`/`show` the moment one sync's package list happens to omit it,
  // and fall through to blockUnlisted's default of hiding it instead, with no visible sign on the
  // dashboard that anything changed (the admin still sees "Allow" selected).
  for (const [pkg, a] of Object.entries(apps)) {
    if (a.mode === "block") hide.add(pkg);
    if (a.mode === "soft") show.add(pkg);
    if (a.mode === "allow" || a.mode === "force") {
      allowed.add(pkg);
      show.add(pkg);
    }
  }
  const categories = opts.categories || {};
  const groupBlocked = { has: (pkg) => cfg.blockAppGroups.some((g) => inGroup(g, pkg, categories[pkg])) };
  for (const pkg of reportedPackages) {
    const mode = apps[pkg]?.mode;
    if (mode === "block" || mode === "allow" || mode === "force" || mode === "soft") continue; // handled above
    if (groupBlocked.has(pkg)) {
      hide.add(pkg); // its whole group is blocked and nothing was said about this app in particular
      continue;
    }
    if (cfg.approveNew && known && !known.has(pkg) && !isProtected(pkg)) {
      hide.add(pkg);
      pending.push(pkg);
    } else if (cfg.blockUnlisted && !isProtected(pkg)) hide.add(pkg);
    else show.add(pkg);
  }
  const restrictions = Object.entries(RESTRICTIONS)
    .filter(([k]) => cfg.restrictions[k])
    .map(([, v]) => v.key);
  // The phone checks these against its own clock, so apps open and close on time even offline.
  const schedules = {};
  for (const [pkg, a] of Object.entries(apps)) if (a.schedule) schedules[pkg] = a.schedule;
  // With approval mode on, the phone also gets the approved baseline so it can hold a new app
  // right away, even when it has no connection to the dashboard.
  // homeScreen: the agent becomes the home screen; only `allowed` apps can be opened. A "soft"
  // app is excluded from that list but left running (not in `hide`); a "block" app is disabled
  // outright via `hide`, same as it always is outside home-screen mode too.
  const out = { hide: [...hide], show: [...show], allowed: [...allowed], restrictions, schedules, pending, approveNew: cfg.approveNew, reportWifi: cfg.reportWifi, autoUpdate: cfg.autoUpdate, homeScreen: cfg.homeScreen, restrictBrowsing: cfg.restrictBrowsing, browserFilter: cfg.browserFilter, youtubeMode: effectiveYoutubeMode(cfg), tlsInspection: cfg.tlsInspection, inspectBypass: cfg.inspectBypass, frpAccounts: cfg.frpAccounts, freezeUpdates: cfg.freezeUpdates, blockAccessibility: cfg.blockAccessibility, phoneAdminLocked: cfg.phoneAdminLocked, hideAppIcon: cfg.hideAppIcon, disableBiometricUnlock: cfg.disableBiometricUnlock, hideKioskAdmin: cfg.hideKioskAdmin, browserAddonEnabled: cfg.browserAddonEnabled, wholeAppBlocklist: cfg.wholeAppBlocklist, dnsBlocklist: cfg.dnsBlocklist, dnsAllowlist: cfg.dnsAllowlist, networkBlocklist: cfg.networkBlocklist, perAppDnsAllowlist: cfg.perAppDnsAllowlist, sites: normalizeSites(cfg.sites), dnsFilterNotifTitle: cfg.dnsFilterNotifTitle, dnsFilterNotifText: cfg.dnsFilterNotifText };
  if (cfg.approveNew && known) out.known = [...known];
  out.userMenu = cfg.userMenu;
  out.screenTime = cfg.screenTime;
  out.chromePolicy = cfg.chromePolicy;
  out.blockedWords = cfg.blockedWords;
  out.safeSearch = cfg.safeSearch;
  out.youtubeDns = cfg.youtubeDns;
  out.blockImages = cfg.blockImages;
  out.imagesAllow = cfg.imagesAllow;
  return out;
}

const SIMPLE_CONFIG_FIELDS = [
  ["blockUnlisted", "Hide unlisted apps"],
  ["approveNew", "Hold new apps for approval"],
  ["homeScreen", "Home screen mode"],
  ["restrictBrowsing", "Restrict browsing to the agent's browser"],
  ["freezeUpdates", "Freeze system updates"],
  ["blockAccessibility", "Block accessibility services"],
  ["phoneAdminLocked", "Only control from the dashboard"],
  ["hideAppIcon", "Hide the app icon"],
  ["disableBiometricUnlock", "Disable fingerprint/face at the lock screen"],
  ["hideKioskAdmin", "Hide the Administrator button in home screen mode"],
  ["browserAddonEnabled", "Browser add-on"],
  ["reportWifi", "Report Wi-Fi name"],
  ["autoUpdate", "Auto-update the agent"],
];

/**
 * A plain-English list of what a config PUT actually changed, for a dashboard-side log separate
 * from the phone's own self-reported one -- so "I turned this on" and "the phone applied it" can
 * be compared side by side instead of taking it on faith that a save reached the phone at all.
 */
export function summarizeConfigChange(before, after) {
  const lines = [];
  for (const [k, v] of Object.entries(RESTRICTIONS)) {
    if (!!before.restrictions[k] !== !!after.restrictions[k]) lines.push(v.label + ": " + (after.restrictions[k] ? "on" : "off"));
  }
  for (const [field, label] of SIMPLE_CONFIG_FIELDS) {
    if (!!before[field] !== !!after[field]) lines.push(label + ": " + (after[field] ? "on" : "off"));
  }
  const beforeApps = before.apps || {};
  const afterApps = after.apps || {};
  for (const pkg of new Set([...Object.keys(beforeApps), ...Object.keys(afterApps)])) {
    const b = beforeApps[pkg]?.mode;
    const a = afterApps[pkg]?.mode;
    if (b !== a) lines.push((afterApps[pkg]?.label || beforeApps[pkg]?.label || pkg) + ": " + (a || "default"));
  }
  if ((before.browserFilter || "off") !== (after.browserFilter || "off")) lines.push("Browser filter: " + (after.browserFilter || "off"));
  for (const [g, info] of Object.entries(APP_GROUPS)) {
    const b = (before.blockAppGroups || []).includes(g), a = (after.blockAppGroups || []).includes(g);
    if (a !== b) lines.push(info.label + ": " + (a ? "blocked" : "no longer blocked as a group"));
  }
  for (const [k, label] of Object.entries(USER_MENU)) {
    const b = !before.userMenu || before.userMenu[k] !== false, a = !after.userMenu || after.userMenu[k] !== false;
    if (a !== b) lines.push('Phone menu "' + label + '": ' + (a ? "shown" : "hidden"));
  }
  if (JSON.stringify(normalizeBlockedWords(before.blockedWords)) !== JSON.stringify(normalizeBlockedWords(after.blockedWords))) lines.push("Blocked words: " + normalizeBlockedWords(after.blockedWords).length);
  const cpB = normalizeChromePolicy(before.chromePolicy), cpA = normalizeChromePolicy(after.chromePolicy);
  for (const [k, label] of [["noIncognito", "Chrome: no incognito tabs"], ["adultFilter", "Chrome: Google's adult-site filter"], ["noSignin", "Chrome: no signing in"]]) {
    if (cpB[k] !== cpA[k]) lines.push(label + ": " + (cpA[k] ? "on" : "off"));
  }
  const stB = normalizeScreenTime(before.screenTime), stA = normalizeScreenTime(after.screenTime);
  if (stB.dailyLimit !== stA.dailyLimit) lines.push("Daily screen time: " + (stA.dailyLimit ? stA.dailyLimit + " minutes" : "no limit"));
  if (JSON.stringify(stB.bedtime) !== JSON.stringify(stA.bedtime)) lines.push("Bedtime: " + (stA.bedtime ? stA.bedtime.from + " to " + stA.bedtime.to : "off"));
  if (JSON.stringify(stB.appLimits) !== JSON.stringify(stA.appLimits)) lines.push("App time limits changed (" + Object.keys(stA.appLimits).length + " app(s))");
  if (!!before.safeSearch !== !!after.safeSearch) lines.push("Forced SafeSearch: " + (after.safeSearch ? "on" : "off"));
  if ((before.youtubeDns || "off") !== (after.youtubeDns || "off")) lines.push("YouTube Restricted Mode everywhere: " + (after.youtubeDns || "off"));
  if (!!before.blockImages !== !!after.blockImages) lines.push("Block pictures on web pages: " + (after.blockImages ? "on" : "off"));
  if (!!before.tlsInspection !== !!after.tlsInspection) lines.push("Browser inspection: " + (after.tlsInspection ? "on" : "off"));
  if ((before.youtubeMode || "auto") !== (after.youtubeMode || "auto")) lines.push("YouTube in the browsers: " + (after.youtubeMode || "auto"));
  if (JSON.stringify(before.frpAccounts) !== JSON.stringify(after.frpAccounts)) lines.push("Factory Reset Protection accounts changed");
  if (JSON.stringify(Object.keys(before.sites || {})) !== JSON.stringify(Object.keys(after.sites || {}))) lines.push("Sites list changed");
  if (JSON.stringify(Object.keys(before.wholeAppBlocklist || {}).sort()) !== JSON.stringify(Object.keys(after.wholeAppBlocklist || {}).sort()))
    lines.push("Block Whole Apps list changed");
  if (!sameStringSet(before.dnsBlocklist, after.dnsBlocklist))
    lines.push("DNS Hostname Block list changed (" + (after.dnsBlocklist || []).length + " host(s))");
  if (!sameStringSet(before.dnsAllowlist, after.dnsAllowlist))
    lines.push("DNS Hostname Allow list changed (" + (after.dnsAllowlist || []).length + " host(s))");
  if (JSON.stringify(Object.keys(before.networkBlocklist || {}).sort()) !== JSON.stringify(Object.keys(after.networkBlocklist || {}).sort()))
    lines.push("Block Whole Network list changed");
  if (JSON.stringify(before.perAppDnsAllowlist || {}) !== JSON.stringify(after.perAppDnsAllowlist || {}))
    lines.push("Per-app DNS Hostname Allow changed");
  return lines;
}

/** Order doesn't matter for either list -- only content. */
function sameStringSet(a, b) {
  const as = [...new Set(a || [])].sort();
  const bs = [...new Set(b || [])].sort();
  return JSON.stringify(as) === JSON.stringify(bs);
}
