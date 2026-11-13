package com.familymdm.agent;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

/**
 * The agent sets itself up: once it is the phone's device owner it gives itself every right it can,
 * instead of asking someone to tap through Settings.
 *
 *  1. It grants itself WRITE_SECURE_SETTINGS (a device owner may grant itself permissions).
 *  2. With that it switches its own accessibility service on.
 *  3. It watches that setting: if anybody switches the service off, it is switched back on within a
 *     moment, so it cannot be turned off from Settings.
 *
 * What Android allows differs by version and maker, so every step is tried and what happened is
 * written to the activity log once (changes only): "refused" tells the administrator which one tap is
 * still needed (the Health check shows it).
 */
final class SelfSetup {
    private SelfSetup() {}

    private static final String TAG = "SelfSetup";
    private static final String PERM = "android.permission.WRITE_SECURE_SETTINGS";

    private static ContentObserver observer;

    private static ComponentName service(Context c) {
        return new ComponentName(c, BlockAccessibilityService.class);
    }

    /** Called with every policy apply (and from the service). Cheap when everything is already in place. */
    static void run(Context c, DevicePolicyManager dpm, ComponentName admin) {
        if (!dpm.isDeviceOwnerApp(c.getPackageName())) return;
        String result;
        try {
            result = grantSecureSettings(c, dpm, admin);
            if (result == null) result = enableAccessibility(c);
        } catch (Exception e) {
            result = "failed: " + e.getMessage();
        }
        note(c, result);
        try {
            extras(c, dpm, admin);
        } catch (Exception ignored) {
        }
        BatteryAllow.ask(c); // "unrestricted battery": asked, and the accessibility service presses Allow
    }

    /**
     * Other rights the agent would like (usage access for screen time, drawing over other apps). A device owner may
     * grant itself ordinary permissions; whether Android also lets it for these two differs by version, so each is
     * tried and the outcome is written to the activity log once.
     */
    private static void extras(Context c, DevicePolicyManager dpm, ComponentName admin) {
        StringBuilder refused = new StringBuilder();
        String[][] wanted = {{"android.permission.PACKAGE_USAGE_STATS", "usage access (screen time)"},
                {"android.permission.SYSTEM_ALERT_WINDOW", "drawing over other apps"}};
        for (String[] w : wanted) {
            try {
                if (dpm.getPermissionGrantState(admin, c.getPackageName(), w[0]) == DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED) continue;
                dpm.setPermissionGrantState(admin, c.getPackageName(), w[0], DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
            } catch (Exception ignored) {
            }
        }
        if (!Agent.usageAccessOn(c)) refused.append("usage access (screen time)");
        if (!android.provider.Settings.canDrawOverlays(c)) refused.append(refused.length() > 0 ? ", " : "").append("drawing over other apps");
        String now = refused.length() == 0 ? "ok" : refused.toString();
        if (now.equals(Agent.prefs(c).getString("selfSetupExtras", ""))) return;
        Agent.prefs(c).edit().putString("selfSetupExtras", now).apply();
        Agent.addEvent(c, "restriction", refused.length() == 0
                ? "The agent holds usage access and may draw over other apps"
                : "Android still needs a tap on the phone for: " + refused + " (Safety → Health check shows where)");
    }

    /** null when the agent now holds WRITE_SECURE_SETTINGS, otherwise why not. */
    private static String grantSecureSettings(Context c, DevicePolicyManager dpm, ComponentName admin) {
        if (c.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED) return null;
        try {
            boolean ok = dpm.setPermissionGrantState(admin, c.getPackageName(), PERM, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
            if (ok && c.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED) return null;
            return ok ? "Android accepted the grant but it did not take effect" : "Android refused to let the agent grant itself the right to change settings";
        } catch (SecurityException e) {
            return "Android refused to let the agent grant itself the right to change settings (" + e.getMessage() + ")";
        }
    }

    /** Switches the accessibility service on, when allowed. null when it is on afterwards. */
    private static String enableAccessibility(Context c) {
        if (Agent.accessibilityServiceOn(c)) return null;
        String me = service(c).flattenToShortString();
        String cur = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String next = cur == null || cur.isEmpty() ? me : (cur.contains(me) ? cur : cur + ":" + me);
        try {
            Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next);
            Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (SecurityException e) {
            return "the accessibility service could not be switched on by the agent (" + e.getMessage() + ")";
        }
        return Agent.accessibilityServiceOn(c) ? null : "the accessibility switch did not turn on";
    }

    /** Writes to the activity log only when the outcome changes. */
    private static void note(Context c, String problem) {
        String now = problem == null ? "ok" : problem;
        String before = Agent.prefs(c).getString("selfSetupResult", "");
        if (now.equals(before)) return;
        Agent.prefs(c).edit().putString("selfSetupResult", now).apply();
        if (problem == null) Agent.addEvent(c, "restriction", "The agent set itself up: its accessibility service is on and is kept on");
        else Agent.addEvent(c, "restriction", "Self set-up: " + problem + ". One tap on the phone is still needed (Safety → Health check).");
        Log.i(TAG, now);
    }

    /** Keeps the accessibility switch on: if it is turned off, it is turned back on. Started by the service. */
    static void watch(final Context c) {
        if (observer != null) return;
        final Context app = c.getApplicationContext();
        observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                try {
                    DevicePolicyManager dpm = Agent.dpm(app);
                    if (!dpm.isDeviceOwnerApp(app.getPackageName())) return;
                    if (EmergencyUnlock.isActive(app)) return; // an emergency unlock leaves the phone alone
                    if (app.checkSelfPermission(PERM) != PackageManager.PERMISSION_GRANTED) return;
                    if (!Agent.accessibilityServiceOn(app)) {
                        enableAccessibility(app);
                        Agent.addEvent(app, "restriction", "Someone switched the accessibility service off: it was switched back on");
                    }
                } catch (Exception ignored) {
                }
            }
        };
        try {
            app.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observer);
        } catch (Exception e) {
            observer = null;
        }
    }

    static void stopWatching(Context c) {
        if (observer == null) return;
        try {
            c.getApplicationContext().getContentResolver().unregisterContentObserver(observer);
        } catch (Exception ignored) {
        }
        observer = null;
    }
}
