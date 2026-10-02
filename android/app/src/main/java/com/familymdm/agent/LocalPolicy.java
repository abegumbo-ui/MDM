package com.familymdm.agent;

import com.familymdm.agent.sitepolicy.SitePolicy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Offline mode: the same rules the dashboard's server applies, run on the phone itself.
 * Pure Java (no Android classes) so it can be tested on a computer. Keep in step with src/policy.js.
 */
final class LocalPolicy {
    private LocalPolicy() {}

    /** {settings key, Android restriction key, label, "1" if on by default}. */
    static final String[][] RESTRICTIONS = {
            {"factoryResetDisabled", "no_factory_reset", "Block factory reset from Settings", "1"},
            {"safeBootDisabled", "no_safe_boot", "Block Safe Mode", "1"},
            {"uninstallAppsDisabled", "no_uninstall_apps", "Block uninstalling apps", "1"},
            {"appsControlDisabled", "no_control_apps", "Block changing apps in Settings", "1"},
            {"modifyAccountsDisabled", "no_modify_accounts", "Block adding accounts", "1"},
            {"addUserDisabled", "no_add_user", "Block adding users", "1"},
            {"installUnknownSourcesDisabled", "no_install_unknown_sources", "Block installing from unknown sources", "1"},
            {"installAppsDisabled", "no_install_apps", "Block ALL app installs (including Play Store)", "0"},
            {"configCredentialsDisabled", "no_config_credentials", "Only the administrator can set the screen lock", "0"},
            // Off until you have finished setting up: while on, adb stops working.
            {"debuggingDisabled", "no_debugging_features", "Block Developer options and USB debugging (turn on last)", "0"},
    };

    private static final Set<String> PROTECTED_EXACT = new HashSet<>(Arrays.asList(
            "android", "com.android.systemui", "com.android.settings", "com.android.vending", "com.android.phone",
            "com.android.server.telecom", "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller", "com.google.android.gms",
            "com.google.android.gsf", "com.google.android.webview", "com.android.webview",
            "com.android.documentsui", "com.google.android.documentsui", "com.familymdm.agent", "com.familymdm.browser"));

    private static final Pattern[] PROTECTED_PATTERNS = {
            Pattern.compile("^com\\.android\\.providers\\."),
            Pattern.compile("^com\\.android\\.inputmethod\\."),
            Pattern.compile("inputmethod"),
            Pattern.compile("launcher", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^com\\.android\\.(bluetooth|nfc|networkstack|captiveportal|certinstaller|keychain|se|shell|emergency)"),
            Pattern.compile("^com\\.google\\.android\\.(networkstack|ext\\.|modulemetadata|captiveportal)"),
            Pattern.compile("^com\\.qualcomm\\."),
            Pattern.compile("^com\\.mediatek\\."),
    };

    private static final Pattern HHMM = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final Pattern PKG = Pattern.compile("^[A-Za-z0-9_.]{1,200}$");
    private static final Pattern ACCOUNT_ID = Pattern.compile("^\\d{15,25}$");

    static boolean isProtected(String pkg) {
        if (PROTECTED_EXACT.contains(pkg)) return true;
        for (Pattern p : PROTECTED_PATTERNS) if (p.matcher(pkg).find()) return true;
        return false;
    }

    /** Google account IDs: 15 to 25 digits (a leading "people/" is accepted), at most 3, no repeats. */
    static List<String> normalizeAccounts(JSONArray in) {
        Set<String> out = new LinkedHashSet<>();
        if (in != null) {
            for (int i = 0; i < in.length() && out.size() < 3; i++) {
                String s = in.optString(i, "").trim();
                if (s.startsWith("people/")) s = s.substring(7);
                if (ACCOUNT_ID.matcher(s).matches()) out.add(s);
            }
        }
        return new ArrayList<>(out);
    }

    /** {days:[0-6, Sunday=0], from:"HH:MM", to:"HH:MM"} or null if it isn't valid. */
    static JSONObject normalizeSchedule(JSONObject s) throws JSONException {
        if (s == null) return null;
        JSONArray days = s.optJSONArray("days");
        String from = s.optString("from", "");
        String to = s.optString("to", "");
        if (days == null || !HHMM.matcher(from).matches() || !HHMM.matcher(to).matches() || from.equals(to)) return null;
        Set<Integer> set = new java.util.TreeSet<>();
        for (int i = 0; i < days.length(); i++) {
            int d = days.optInt(i, -1);
            if (d >= 0 && d <= 6) set.add(d);
        }
        if (set.isEmpty()) return null;
        JSONObject out = new JSONObject();
        out.put("days", new JSONArray(set));
        out.put("from", from);
        out.put("to", to);
        return out;
    }

    /** A complete, sanitized settings object (missing or bad parts get their defaults). */
    static JSONObject normalize(JSONObject in) throws JSONException {
        JSONObject c = in == null ? new JSONObject() : in;
        JSONObject out = new JSONObject();

        JSONObject apps = new JSONObject();
        JSONObject given = c.optJSONObject("apps");
        if (given != null) {
            Iterator<String> it = given.keys();
            while (it.hasNext()) {
                String pkg = it.next();
                JSONObject a = given.optJSONObject(pkg);
                if (a == null || !PKG.matcher(pkg).matches()) continue;
                String mode = a.optString("mode", "");
                if (!mode.equals("allow") && !mode.equals("force") && !mode.equals("block")) continue;
                JSONObject entry = new JSONObject();
                entry.put("mode", mode);
                JSONObject schedule = normalizeSchedule(a.optJSONObject("schedule"));
                if (schedule != null && !mode.equals("block")) entry.put("schedule", schedule);
                apps.put(pkg, entry);
            }
        }
        out.put("apps", apps);
        out.put("blockUnlisted", c.optBoolean("blockUnlisted", false));
        out.put("approveNew", c.optBoolean("approveNew", false));
        out.put("homeScreen", c.optBoolean("homeScreen", false));
        out.put("restrictBrowsing", c.optBoolean("restrictBrowsing", false));
        // Kept keyed by entry (like apps), the same shape the dashboard uses, so a phone connected to
        // a dashboard later reads/writes the same structure. build() flattens this into a plain list.
        JSONObject sitesIn = c.optJSONObject("sites");
        JSONObject sitesOut = new JSONObject();
        if (sitesIn != null) {
            Iterator<String> sit = sitesIn.keys();
            while (sit.hasNext()) {
                String key = sit.next();
                JSONObject raw = sitesIn.optJSONObject(key);
                if (raw == null) continue;
                String type = raw.optString("type", "");
                String url = raw.optString("url", "").trim();
                if (!type.equals("domain") && !type.equals("exact")) continue;
                if (url.isEmpty()) continue;
                String host = SitePolicy.hostOf(url);
                if (host == null || SitePolicy.isBlockedAdult(url)) continue;
                JSONObject entry = new JSONObject();
                entry.put("type", type);
                entry.put("url", url.contains("://") ? url : "https://" + url);
                entry.put("host", host);
                entry.put("label", raw.optString("label", host));
                entry.put("blockImages", raw.optBoolean("blockImages", false));
                entry.put("installable", raw.optBoolean("installable", true));
                sitesOut.put(key, entry);
            }
        }
        out.put("sites", sitesOut);
        out.put("frpAccounts", new JSONArray(normalizeAccounts(c.optJSONArray("frpAccounts"))));

        JSONObject restrictions = new JSONObject();
        JSONObject givenR = c.optJSONObject("restrictions");
        for (String[] r : RESTRICTIONS) {
            boolean dflt = r[3].equals("1");
            restrictions.put(r[0], givenR != null && givenR.has(r[0]) ? givenR.optBoolean(r[0], dflt) : dflt);
        }
        out.put("restrictions", restrictions);

        JSONArray known = c.optJSONArray("known");
        if (known != null) out.put("known", known);
        return out;
    }

    /**
     * The instructions the agent applies, from the settings and the apps the phone reported.
     * known: apps that existed before "hold new apps" was switched on (null = not known yet, nothing is held).
     */
    static JSONObject build(JSONObject cfgIn, JSONArray reported, Set<String> known) throws JSONException {
        JSONObject cfg = normalize(cfgIn);
        JSONObject apps = cfg.getJSONObject("apps");
        boolean blockUnlisted = cfg.getBoolean("blockUnlisted");
        boolean approveNew = cfg.getBoolean("approveNew");

        Set<String> hide = new LinkedHashSet<>();
        List<String> show = new ArrayList<>();
        List<String> allowed = new ArrayList<>();
        List<String> pending = new ArrayList<>();

        // Explicit blocks apply even to apps the phone didn't report.
        Iterator<String> it = apps.keys();
        while (it.hasNext()) {
            String pkg = it.next();
            if (apps.getJSONObject(pkg).getString("mode").equals("block")) hide.add(pkg);
        }
        for (int i = 0; i < reported.length(); i++) {
            String pkg = reported.optString(i, null);
            if (pkg == null) continue;
            JSONObject a = apps.optJSONObject(pkg);
            String mode = a == null ? "" : a.getString("mode");
            if (mode.equals("allow") || mode.equals("force")) allowed.add(pkg);
            if (mode.equals("block")) {
                hide.add(pkg);
            } else if (mode.equals("allow") || mode.equals("force")) {
                show.add(pkg);
            } else if (approveNew && known != null && !known.contains(pkg) && !isProtected(pkg)) {
                hide.add(pkg);
                pending.add(pkg);
            } else if (blockUnlisted && !isProtected(pkg)) {
                hide.add(pkg);
            } else {
                show.add(pkg);
            }
        }

        JSONArray restrictions = new JSONArray();
        JSONObject r = cfg.getJSONObject("restrictions");
        for (String[] def : RESTRICTIONS) if (r.getBoolean(def[0])) restrictions.put(def[1]);

        JSONObject schedules = new JSONObject();
        it = apps.keys();
        while (it.hasNext()) {
            String pkg = it.next();
            JSONObject a = apps.getJSONObject(pkg);
            if (a.has("schedule")) schedules.put(pkg, a.getJSONObject("schedule"));
        }

        JSONObject out = new JSONObject();
        out.put("hide", new JSONArray(hide));
        out.put("show", new JSONArray(show));
        out.put("allowed", new JSONArray(allowed));
        out.put("restrictions", restrictions);
        out.put("schedules", schedules);
        out.put("pending", new JSONArray(pending));
        out.put("approveNew", approveNew);
        out.put("reportWifi", false); // needs Location turned on; leave that switch to the dashboard mode
        out.put("autoUpdate", false);
        out.put("homeScreen", cfg.getBoolean("homeScreen"));
        out.put("restrictBrowsing", cfg.getBoolean("restrictBrowsing"));
        JSONObject sitesObj = cfg.optJSONObject("sites");
        JSONArray sitesArr = new JSONArray();
        if (sitesObj != null) {
            Iterator<String> sk = sitesObj.keys();
            while (sk.hasNext()) sitesArr.put(sitesObj.getJSONObject(sk.next()));
        }
        out.put("sites", sitesArr);
        out.put("frpAccounts", cfg.getJSONArray("frpAccounts"));
        if (approveNew && known != null) out.put("known", new JSONArray(known));
        return out;
    }
}
