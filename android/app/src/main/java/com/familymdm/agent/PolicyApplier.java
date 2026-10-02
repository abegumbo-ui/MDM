package com.familymdm.agent;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
            "no_install_apps", "no_debugging_features"));

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

        Set<String> never = neverHide(c);
        Set<String> hiddenByUs = Agent.getSet(c, "hidden");

        for (String pkg : strings(policy.optJSONArray("hide"))) {
            if (never.contains(pkg)) continue;
            try {
                if (!dpm.isApplicationHidden(admin, pkg) && dpm.setApplicationHidden(admin, pkg, true)) {
                    hiddenByUs.add(pkg);
                } else if (dpm.isApplicationHidden(admin, pkg)) {
                    hiddenByUs.add(pkg);
                }
            } catch (Exception e) {
                Log.w(TAG, "hide failed for " + pkg + ": " + e);
            }
        }
        for (String pkg : strings(policy.optJSONArray("show"))) {
            try {
                if (dpm.isApplicationHidden(admin, pkg)) dpm.setApplicationHidden(admin, pkg, false);
                hiddenByUs.remove(pkg);
            } catch (Exception e) {
                Log.w(TAG, "show failed for " + pkg + ": " + e);
            }
        }
        Agent.putSet(c, "hidden", hiddenByUs);

        Set<String> wanted = new HashSet<>();
        for (String r : strings(policy.optJSONArray("restrictions"))) {
            if (ALLOWED_RESTRICTIONS.contains(r)) wanted.add(r);
        }
        Set<String> applied = Agent.getSet(c, "restrictions");
        for (String r : wanted) {
            try {
                dpm.addUserRestriction(admin, r);
            } catch (Exception e) {
                Log.w(TAG, "restriction failed " + r + ": " + e);
            }
        }
        for (String r : applied) {
            if (!wanted.contains(r)) {
                try {
                    dpm.clearUserRestriction(admin, r);
                } catch (Exception e) {
                    Log.w(TAG, "clear restriction failed " + r + ": " + e);
                }
            }
        }
        Agent.putSet(c, "restrictions", wanted);
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
