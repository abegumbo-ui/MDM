package com.familymdm.agent;

import android.app.admin.DevicePolicyManager;
import android.app.admin.FactoryResetProtectionPolicy;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.LocationManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies the server's instructions with Android's device-owner APIs, and reports installed apps. */
final class PolicyApplier {
    private static final String TAG = "MdmAgent";

    // Only these restriction keys are ever applied, whatever the server sends.
    private static final Set<String> ALLOWED_RESTRICTIONS = new HashSet<>(Arrays.asList(
            "no_factory_reset", "no_safe_boot", "no_uninstall_apps", "no_control_apps",
            "no_modify_accounts", "no_add_user", "no_install_unknown_sources",
            "no_install_apps", "no_debugging_features", "no_config_credentials",
            "no_config_location", "no_airplane_mode", "no_config_mobile_networks", "no_config_tethering",
            "no_config_vpn", "no_config_private_dns"));

    // Restrictions that would also stop the agent's own installs, updates and uninstalls.
    private static final Set<String> INSTALL_RELATED = new HashSet<>(Arrays.asList(
            "no_install_apps", "no_install_unknown_sources", "no_uninstall_apps", "no_control_apps"));

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    private PolicyApplier() {}

    /** Packages that must never be hidden, whatever the server says. */
    static Set<String> neverHide(Context c) {
        Set<String> s = new HashSet<>();
        s.add(c.getPackageName());
        s.add("android");
        s.add("com.android.systemui");
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : c.getPackageManager().queryIntentActivities(home, 0)) {
            s.add(ri.activityInfo.packageName);
        }
        return s;
    }

    /** Launchable apps (what shows in the app drawer) plus apps this agent has hidden. */
    static JSONArray collectPackages(Context c) throws JSONException {
        PackageManager pm = c.getPackageManager();
        DevicePolicyManager dpm = Agent.dpm(c);
        ComponentName admin = Agent.admin(c);
        Map<String, JSONObject> out = new LinkedHashMap<>();

        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (out.containsKey(pkg)) continue;
            boolean system = (ri.activityInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
            out.put(pkg, entry(pkg, label(pm, ri), system, false));
        }
        for (String pkg : Agent.getSet(c, "hidden")) {
            if (out.containsKey(pkg)) continue;
            boolean system = false;
            String label = pkg;
            try {
                android.content.pm.ApplicationInfo ai =
                        pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES);
                system = (ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                label = ai.loadLabel(pm).toString();
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            boolean hidden = dpm.isApplicationHidden(admin, pkg);
            out.put(pkg, entry(pkg, LABELS.containsKey(pkg) ? LABELS.get(pkg) : label, system, hidden));
        }
        JSONArray arr = new JSONArray();
        for (JSONObject o : out.values()) arr.put(o);
        return arr;
    }

    /**
     * System apps with no launcher icon (e.g. a lock-screen component bundled with the phone) --
     * these never show up in {@link #collectPackages}, which only reports what's launchable (plus
     * whatever this agent has already hidden). Fetched on demand, not on every sync: a full system
     * app list can run to a few hundred entries, and nobody needs it refreshed every 15 seconds.
     */
    static JSONArray collectSystemPackages(Context c) throws JSONException {
        PackageManager pm = c.getPackageManager();
        DevicePolicyManager dpm = Agent.dpm(c);
        ComponentName admin = Agent.admin(c);
        Set<String> launcher = new HashSet<>();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) launcher.add(ri.activityInfo.packageName);
        JSONArray arr = new JSONArray();
        for (android.content.pm.ApplicationInfo ai : pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)) {
            if (launcher.contains(ai.packageName)) continue;
            if ((ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) continue;
            boolean hidden = dpm.isApplicationHidden(admin, ai.packageName);
            arr.put(entry(ai.packageName, ai.loadLabel(pm).toString(), true, hidden));
        }
        return arr;
    }

    private static String label(PackageManager pm, ResolveInfo ri) {
        String pkg = ri.activityInfo.packageName;
        String cached = LABELS.get(pkg);
        if (cached != null) return cached;
        String l = ri.loadLabel(pm).toString();
        LABELS.put(pkg, l);
        return l;
    }

    private static JSONObject entry(String pkg, String label, boolean system, boolean hidden) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("p", pkg);
        o.put("l", label);
        o.put("s", system);
        o.put("h", hidden);
        return o;
    }

    static void apply(Context c, JSONObject policy) {
        DevicePolicyManager dpm = Agent.dpm(c);
        ComponentName admin = Agent.admin(c);
        if (!dpm.isDeviceOwnerApp(c.getPackageName())) return;

        protectSelf(c, dpm, admin);
        applyFrp(c, dpm, admin, policy.optJSONArray("frpAccounts"));
        if (policy.optBoolean("reportWifi", true)) enableWifiName(c, dpm, admin);
        applyUpdateFreeze(c, dpm, admin, policy.optBoolean("freezeUpdates", false));
        applyAccessibilityLock(c, dpm, admin, policy.optBoolean("blockAccessibility", false));
        SettingsWatchdog.setBlocked(new HashSet<>(strings(policy.optJSONArray("blockedSettings"))));

        Set<String> never = neverHide(c);
        Set<String> hiddenByUs = Agent.getSet(c, "hidden");

        Set<String> hideSet = new LinkedHashSet<>(strings(policy.optJSONArray("hide")));
        Set<String> showSet = new LinkedHashSet<>(strings(policy.optJSONArray("show")));
        // Home-screen mode: only these apps may be opened. Harsh-blocked apps are still disabled
        // outright (below); "soft" apps are left running, just excluded from this launcher's list.
        Set<String> allowedSet = new LinkedHashSet<>(strings(policy.optJSONArray("allowed")));

        // Changes made on the phone with the master code win over the dashboard.
        JSONObject overrides = Agent.getOverrides(c);
        Iterator<String> oit = overrides.keys();
        while (oit.hasNext()) {
            String pkg = oit.next();
            if ("block".equals(overrides.optString(pkg))) {
                hideSet.add(pkg);
                showSet.remove(pkg);
                allowedSet.remove(pkg);
            } else if ("allow".equals(overrides.optString(pkg))) {
                hideSet.remove(pkg);
                showSet.add(pkg);
                allowedSet.add(pkg);
            }
        }

        // Apps with a schedule are hidden outside their allowed window (checked against the phone's own clock).
        JSONObject schedules = policy.optJSONObject("schedules");
        if (schedules != null) {
            Iterator<String> keys = schedules.keys();
            while (keys.hasNext()) {
                String pkg = keys.next();
                if (!"block".equals(overrides.optString(pkg)) && !withinSchedule(schedules.optJSONObject(pkg))) {
                    hideSet.add(pkg);
                    showSet.remove(pkg);
                    allowedSet.remove(pkg);
                }
            }
        }

        for (String pkg : hideSet) {
            if (never.contains(pkg)) continue;
            try {
                // Re-asserted every sync, even if Android already reported it hidden last time --
                // trusting that cached answer meant a package Android considers "hidden" for some
                // unrelated reason (e.g. shipped as disabled-until-used) could sit here forever
                // with nothing ever attempted and nothing ever logged, looking exactly like nothing
                // was happening at all. The call itself is a no-op on Android's end once it's really hidden.
                boolean wasHidden = dpm.isApplicationHidden(admin, pkg);
                Agent.TOUCHED.put(pkg, System.currentTimeMillis());
                if (dpm.setApplicationHidden(admin, pkg, true)) {
                    hiddenByUs.add(pkg);
                    if (!wasHidden) Agent.addEvent(c, "hide", "Hidden: " + nameOf(pkg));
                    errorCleared(c, "hide:" + pkg);
                } else {
                    errorOnce(c, "hide:" + pkg, "Could not hide " + nameOf(pkg) + " (the phone refused)");
                }
            } catch (Exception e) {
                errorOnce(c, "hide:" + pkg, "Could not hide " + nameOf(pkg) + ": " + e.getMessage());
            }
        }
        for (String pkg : showSet) {
            try {
                if (dpm.isApplicationHidden(admin, pkg)) {
                    Agent.TOUCHED.put(pkg, System.currentTimeMillis());
                    dpm.setApplicationHidden(admin, pkg, false);
                    Agent.addEvent(c, "show", "Shown again: " + nameOf(pkg));
                }
                hiddenByUs.remove(pkg);
            } catch (Exception e) {
                errorOnce(c, "show:" + pkg, "Could not show " + nameOf(pkg) + ": " + e.getMessage());
            }
        }
        Agent.putSet(c, "hidden", hiddenByUs);
        Kiosk.apply(c, policy, allowedSet);
        Kiosk.syncPreferredActivities(c, Kiosk.active(c), policy.optBoolean("restrictBrowsing", false));
        Kiosk.announceChange(c);
        pushBrowserConfig(c, dpm, admin, policy);

        Set<String> wanted = new HashSet<>();
        for (String r : strings(policy.optJSONArray("restrictions"))) {
            if (ALLOWED_RESTRICTIONS.contains(r)) wanted.add(r);
        }
        // While the agent itself is installing or uninstalling something (an update, an uploaded APK, the
        // Uninstall button), those restrictions are paused for a couple of minutes, then restored.
        boolean window = System.currentTimeMillis() < Agent.prefs(c).getLong("installWindowUntil", 0);
        Set<String> tempCleared = Agent.getSet(c, "tempCleared");
        if (window) {
            for (String r : INSTALL_RELATED) if (wanted.remove(r)) tempCleared.add(r);
        }
        Set<String> applied = Agent.getSet(c, "restrictions");
        Set<String> nowOn = new HashSet<>();
        for (String r : wanted) {
            try {
                dpm.addUserRestriction(admin, r);
                nowOn.add(r);
                if (!applied.contains(r) && !tempCleared.remove(r)) Agent.addEvent(c, "restriction", "Restriction on: " + r);
                errorCleared(c, "r:" + r);
            } catch (Exception e) {
                errorOnce(c, "r:" + r, "Restriction " + r + " failed: " + e.getMessage());
            }
        }
        Agent.putSet(c, "tempCleared", tempCleared);
        for (String r : applied) {
            if (!wanted.contains(r)) {
                try {
                    dpm.clearUserRestriction(admin, r);
                    if (!window || !INSTALL_RELATED.contains(r)) Agent.addEvent(c, "restriction", "Restriction off: " + r);
                } catch (Exception e) {
                    errorOnce(c, "rc:" + r, "Could not clear restriction " + r + ": " + e.getMessage());
                    nowOn.add(r);
                }
            }
        }
        wanted = nowOn;
        Agent.putSet(c, "restrictions", wanted);
    }

    private static String nameOf(String pkg) {
        String l = LABELS.get(pkg);
        return l == null ? pkg : l;
    }

    /** Logs a failure once, so a problem that repeats every minute doesn't flood the phone log. */
    private static void errorOnce(Context c, String key, String msg) {
        Set<String> logged = Agent.getSet(c, "errors");
        if (logged.add(key)) {
            Agent.putSet(c, "errors", logged);
            Agent.addEvent(c, "error", msg);
        }
    }

    private static void errorCleared(Context c, String key) {
        Set<String> logged = Agent.getSet(c, "errors");
        if (logged.remove(key)) Agent.putSet(c, "errors", logged);
    }

    /** Immediate local change from the admin panel; the override list is what syncs to the dashboard. */
    static String applyOverride(Context c, String pkg, String mode) {
        DevicePolicyManager dpm = Agent.dpm(c);
        ComponentName admin = Agent.admin(c);
        if ("block".equals(mode) && neverHide(c).contains(pkg)) {
            return "That is a protected system part and cannot be hidden.";
        }
        try {
            Set<String> hidden = Agent.getSet(c, "hidden");
            Agent.TOUCHED.put(pkg, System.currentTimeMillis());
            if ("block".equals(mode)) {
                dpm.setApplicationHidden(admin, pkg, true);
                hidden.add(pkg);
            } else {
                dpm.setApplicationHidden(admin, pkg, false);
                hidden.remove(pkg);
            }
            Agent.putSet(c, "hidden", hidden);
            Agent.setOverride(c, pkg, mode);
            Agent.addEvent(c, "local", "Master code on phone: " + ("block".equals(mode) ? "blocked " : "allowed ") + nameOf(pkg));
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /**
     * Approval mode: a launchable app that appeared after the approved baseline is hidden immediately,
     * using the last policy the dashboard sent (so it works offline too). Returns true if it was held.
     */
    static boolean holdIfNew(Context c, String pkg) {
        try {
            String stored = Agent.prefs(c).getString("policy", null);
            if (stored == null) return false;
            JSONObject policy = new JSONObject(stored);
            if (!policy.optBoolean("approveNew")) return false;
            if (policy.optBoolean("homeScreen", false) && !Kiosk.paused(c)) return false; // new apps are simply not allowed on the home screen
            if (strings(policy.optJSONArray("known")).contains(pkg)) return false;
            if (strings(policy.optJSONArray("show")).contains(pkg)) return false;
            if ("allow".equals(Agent.getOverrides(c).optString(pkg))) return false;
            if (neverHide(c).contains(pkg)) return false;
            if (c.getPackageManager().getLaunchIntentForPackage(pkg) == null) return false;
            DevicePolicyManager dpm = Agent.dpm(c);
            ComponentName admin = Agent.admin(c);
            if (!dpm.isDeviceOwnerApp(c.getPackageName())) return false;
            Agent.TOUCHED.put(pkg, System.currentTimeMillis());
            if (!dpm.setApplicationHidden(admin, pkg, true)) return false;
            Set<String> hidden = Agent.getSet(c, "hidden");
            hidden.add(pkg);
            Agent.putSet(c, "hidden", hidden);
            Agent.addEvent(c, "hide", "Held for your approval: " + nameOf(pkg));
            return true;
        } catch (Exception e) {
            Log.w(TAG, "holdIfNew failed: " + e);
            return false;
        }
    }

    /** Re-applies the last policy the server sent (keeps schedules working while offline). */
    static void applyStored(Context c) {
        String stored = Agent.prefs(c).getString("policy", null);
        if (stored == null) return;
        try {
            apply(c, new JSONObject(stored));
        } catch (JSONException e) {
            Log.w(TAG, "stored policy unreadable: " + e);
        }
    }

    /** True if "now" is inside {days:[0-6, Sunday=0], from:"HH:MM", to:"HH:MM"}. Bad data never blocks an app. */
    static boolean withinSchedule(JSONObject s) {
        if (s == null) return true;
        JSONArray days = s.optJSONArray("days");
        int from = minutes(s.optString("from"));
        int to = minutes(s.optString("to"));
        if (days == null || from < 0 || to < 0 || from == to) return true;
        Calendar now = Calendar.getInstance();
        int dow = now.get(Calendar.DAY_OF_WEEK) - 1;
        int mins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (from < to) return mins >= from && mins < to && hasDay(days, dow);
        // Window crosses midnight: the early part belongs to the day it started on.
        if (mins >= from) return hasDay(days, dow);
        if (mins < to) return hasDay(days, (dow + 6) % 7);
        return false;
    }

    private static boolean hasDay(JSONArray days, int dow) {
        for (int i = 0; i < days.length(); i++) if (days.optInt(i, -1) == dow) return true;
        return false;
    }

    private static int minutes(String hhmm) {
        try {
            String[] parts = hhmm.split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * App icons (64px PNG, base64) for apps the dashboard hasn't been sent yet, at most {@code max} per call.
     * {@code sentOut} receives the packages to remember as handled once the report succeeds.
     */
    static JSONObject collectIcons(Context c, JSONArray packages, int max, List<String> sentOut) {
        JSONObject out = new JSONObject();
        Set<String> sent = Agent.getSet(c, "icons_sent");
        PackageManager pm = c.getPackageManager();
        for (int i = 0; i < packages.length() && sentOut.size() < max; i++) {
            String pkg = packages.optJSONObject(i) == null ? null : packages.optJSONObject(i).optString("p", null);
            if (pkg == null || sent.contains(pkg)) continue;
            sentOut.add(pkg);
            try {
                Drawable d = pm.getApplicationIcon(pkg);
                Bitmap bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
                d.setBounds(0, 0, 64, 64);
                d.draw(new Canvas(bmp));
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.PNG, 100, bos);
                out.put(pkg, Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP));
            } catch (Exception e) {
                Log.w(TAG, "icon failed for " + pkg + ": " + e);
            }
        }
        return out;
    }

    /**
     * Android shows the Wi-Fi network name only to apps holding the location permission while Location is on.
     * This grants the permission to the agent and turns the setting on. No location is read or reported.
     */
    private static void enableWifiName(Context c, DevicePolicyManager dpm, ComponentName admin) {
        try {
            String pkg = c.getPackageName();
            for (String perm : new String[]{"android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION"}) {
                if (dpm.getPermissionGrantState(admin, pkg, perm) != DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED) {
                    dpm.setPermissionGrantState(admin, pkg, perm, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
                    Agent.addEvent(c, "restriction", "Allowed the agent to read the Wi-Fi name");
                }
            }
            if (Build.VERSION.SDK_INT >= 30) {
                LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
                if (lm != null && !lm.isLocationEnabled()) {
                    dpm.setLocationEnabled(admin, true);
                    Agent.addEvent(c, "restriction", "Turned on Android's Location setting so the Wi-Fi name can be shown");
                }
            }
            errorCleared(c, "wifiname");
        } catch (Exception e) {
            errorOnce(c, "wifiname", "Could not enable Wi-Fi name reporting: " + e.getMessage());
        }
    }

    /**
     * There is no Android API for a true permanent freeze of system updates: setSystemUpdatePolicy
     * caps any single freeze at 90 days and forces a 60-day gap before the next one (and Android
     * tracks freezes already applied, so repeatedly resetting the start date to "today" to dodge
     * that limit gets rejected). This instead lays down the densest schedule the OS allows, anchored
     * to whenever this was first turned on, and lets Android's own month/day recurrence repeat it
     * every year from then on -- set once, not reapplied on every sync.
     */
    private static void applyUpdateFreeze(Context c, DevicePolicyManager dpm, ComponentName admin, boolean wanted) {
        boolean applied = Agent.prefs(c).getBoolean("updateFreezeSet", false);
        if (wanted == applied) return;
        if (Build.VERSION.SDK_INT < 28) {
            errorOnce(c, "updatefreeze", "Freezing system updates needs Android 9 or newer; this phone cannot do it.");
            return;
        }
        try {
            if (wanted) {
                java.time.MonthDay cursor = java.time.MonthDay.now();
                List<android.app.admin.FreezePeriod> periods = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    java.time.MonthDay start = cursor;
                    java.time.MonthDay end = addDays(start, 89);
                    periods.add(new android.app.admin.FreezePeriod(start, end));
                    cursor = addDays(end, 60);
                }
                android.app.admin.SystemUpdatePolicy sup = android.app.admin.SystemUpdatePolicy.createPostponeInstallPolicy();
                sup = sup.setFreezePeriods(periods);
                dpm.setSystemUpdatePolicy(admin, sup);
                Agent.addEvent(c, "restriction", "Scheduled the densest update freeze Android allows");
            } else {
                dpm.setSystemUpdatePolicy(admin, null);
                Agent.addEvent(c, "restriction", "Removed the update freeze; the phone can update normally again");
            }
            Agent.prefs(c).edit().putBoolean("updateFreezeSet", wanted).apply();
            errorCleared(c, "updatefreeze");
        } catch (Exception e) {
            errorOnce(c, "updatefreeze", "Could not change the update freeze: " + e.getMessage());
        }
    }

    /**
     * A sideloaded app can ask the person to grant it an accessibility service, then use that
     * service's reach (reading the screen, performing clicks) to get around normal app controls --
     * a known MDM bypass. Setting an empty permitted list turns off every accessibility service on
     * the phone, this agent's controls included; it is only meant for phones where nobody needs one.
     */
    private static void applyAccessibilityLock(Context c, DevicePolicyManager dpm, ComponentName admin, boolean wanted) {
        try {
            dpm.setPermittedAccessibilityServices(admin, wanted ? new ArrayList<String>() : null);
            Agent.prefs(c).edit().putBoolean("accessibilityLockSet", wanted).apply();
            errorCleared(c, "a11y");
        } catch (Exception e) {
            errorOnce(c, "a11y", "Could not change the accessibility-service lock: " + e.getMessage());
        }
    }

    /** Adds days to a MonthDay via a leap year (so Feb 29 is always a valid intermediate date). */
    private static java.time.MonthDay addDays(java.time.MonthDay md, int days) {
        return java.time.MonthDay.from(java.time.LocalDate.of(2024, md.getMonthValue(), md.getDayOfMonth()).plusDays(days));
    }

    /**
     * Factory Reset Protection for the organisation: after a reset from recovery mode, only these Google
     * accounts can set the phone up again. No account has to be signed in on the phone itself (Android 11+).
     */
    private static void applyFrp(Context c, DevicePolicyManager dpm, ComponentName admin, JSONArray accounts) {
        if (Build.VERSION.SDK_INT < 30) return;
        List<String> ids = strings(accounts);
        String wanted = String.join(",", ids);
        if (wanted.equals(Agent.prefs(c).getString("frpApplied", ""))) return;
        try {
            if (ids.isEmpty()) {
                dpm.setFactoryResetProtectionPolicy(admin, null);
                Agent.addEvent(c, "restriction", "Factory Reset Protection removed");
            } else {
                FactoryResetProtectionPolicy p = new FactoryResetProtectionPolicy.Builder()
                        .setFactoryResetProtectionAccounts(ids)
                        .setFactoryResetProtectionEnabled(true)
                        .build();
                dpm.setFactoryResetProtectionPolicy(admin, p);
                Agent.addEvent(c, "restriction", "Factory Reset Protection set for " + ids.size() + " Google account(s)");
            }
            Agent.prefs(c).edit().putString("frpApplied", wanted).apply();
            errorCleared(c, "frp");
        } catch (Exception e) {
            errorOnce(c, "frp", "Could not set Factory Reset Protection: " + e.getMessage());
        }
    }

    /**
     * Pushes the site allowlist to the separate Browser app through Android's managed-configuration
     * channel. Works whether or not that app is installed yet; it reads these when it starts.
     */
    private static void pushBrowserConfig(Context c, DevicePolicyManager dpm, ComponentName admin, JSONObject policy) {
        try {
            JSONArray sites = policy.optJSONArray("sites");
            String json = sites == null ? "[]" : sites.toString();
            // "Browse freely for a while" (a redeemed one-time code): Browser bypasses the allowlist
            // until this time, reporting every new site it lands on for approval afterward.
            long browseUntil = Agent.prefs(c).getLong("browseUntil", 0);
            String cacheKey = json + "|" + browseUntil;
            if (cacheKey.equals(Agent.prefs(c).getString("browserConfigApplied", null))) return;
            Bundle b = new Bundle();
            b.putString("sites", json);
            b.putLong("browseUntil", browseUntil);
            dpm.setApplicationRestrictions(admin, "com.familymdm.browser", b);
            Agent.prefs(c).edit().putString("browserConfigApplied", cacheKey).apply();
            errorCleared(c, "browserConfig");
        } catch (Exception e) {
            // Silent before: a persistent failure here looked exactly like nothing happening at
            // all, from the phone opening the Browser app straight to its own "connect me"
            // screen as if this agent didn't exist -- same shape of bug as the stuck Block above.
            Log.w(TAG, "browser config push failed: " + e);
            errorOnce(c, "browserConfig", "Could not hand the site list to the Browser app: " + e.getMessage());
        }
    }

    /** "Browse freely for a while": redeemed on the agent's main screen, applies right away. */
    static void startBrowseWindow(Context c, int minutes) {
        long until = System.currentTimeMillis() + minutes * 60_000L;
        Agent.prefs(c).edit().putLong("browseUntil", until).apply();
        Agent.addEvent(c, "restriction", "Browser can open any site for " + minutes + " minutes; new sites visited will need your approval");
        applyStored(c);
    }

    /** Stops the agent itself from being uninstalled or force-stopped from Settings. */
    private static void protectSelf(Context c, DevicePolicyManager dpm, ComponentName admin) {
        try {
            dpm.setUninstallBlocked(admin, c.getPackageName(), true);
            if (Build.VERSION.SDK_INT >= 30) {
                List<String> pkgs = new ArrayList<>();
                pkgs.add(c.getPackageName());
                dpm.setUserControlDisabledPackages(admin, pkgs);
            }
        } catch (Exception e) {
            Log.w(TAG, "protectSelf failed: " + e);
        }
    }

    /** Undo everything this agent changed, then give up device-owner status. */
    static void release(Context c) {
        DevicePolicyManager dpm = Agent.dpm(c);
        ComponentName admin = Agent.admin(c);
        if (dpm.isDeviceOwnerApp(c.getPackageName())) {
            Kiosk.clear(c);
            Kiosk.syncPreferredActivities(c, false, false);
            for (String pkg : Agent.getSet(c, "hidden")) {
                try {
                    dpm.setApplicationHidden(admin, pkg, false);
                } catch (Exception ignored) {
                }
            }
            for (String r : Agent.getSet(c, "restrictions")) {
                try {
                    dpm.clearUserRestriction(admin, r);
                } catch (Exception ignored) {
                }
            }
            try {
                dpm.setUninstallBlocked(admin, c.getPackageName(), false);
                if (Build.VERSION.SDK_INT >= 30) {
                    dpm.setUserControlDisabledPackages(admin, new ArrayList<String>());
                }
            } catch (Exception ignored) {
            }
        }
        Agent.prefs(c).edit().clear().apply();
        if (dpm.isDeviceOwnerApp(c.getPackageName())) {
            dpm.clearDeviceOwnerApp(c.getPackageName());
        }
    }

    private static List<String> strings(JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, null);
            if (s != null && !s.isEmpty()) out.add(s);
        }
        return out;
    }
}
