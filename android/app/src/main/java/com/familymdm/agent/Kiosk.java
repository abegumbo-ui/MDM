package com.familymdm.agent;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.provider.Telephony;
import android.telecom.TelecomManager;
import android.util.Log;

import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Home-screen mode. The agent becomes the phone's home screen and Android's lock-task mode is limited to
 * the apps the administrator allowed. Apps that are not allowed are NOT switched off: they keep running in
 * the background (Maps keeps using Google Play services), but they cannot be opened.
 */
final class Kiosk {
    private static final String TAG = "MdmAgent";

    // Parts of the system that allowed apps need in order to work: permission prompts, file picker,
    // share sheets, Google sign-in and consent dialogs.
    private static final String[] ESSENTIALS = {
            "android", "com.android.systemui",
            "com.android.documentsui", "com.google.android.documentsui",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller",
            "com.google.android.gms", "com.familymdm.browser"
    };

    private Kiosk() {}

    static boolean paused(Context c) {
        return Agent.prefs(c).getBoolean("kioskPaused", false);
    }

    static boolean active(Context c) {
        return Agent.prefs(c).getBoolean("kioskOn", false) && !paused(c);
    }

    /** Apps shown on the home screen (already filtered by schedules and the master-code overrides). */
    static Set<String> allowedNow(Context c) {
        return Agent.getSet(c, "kioskAllowed");
    }

    /** Everything that may open while home-screen mode is on. */
    static String[] lockTaskPackages(Context c) {
        Set<String> s = new LinkedHashSet<>();
        s.add(c.getPackageName());
        for (String e : ESSENTIALS) s.add(e);
        try {
            String dialer = ((TelecomManager) c.getSystemService(Context.TELECOM_SERVICE)).getDefaultDialerPackage();
            if (dialer != null) s.add(dialer); // incoming and outgoing call screens
        } catch (Exception ignored) {
        }
        try {
            String sms = Telephony.Sms.getDefaultSmsPackage(c);
            if (sms != null) s.add(sms);
        } catch (Exception ignored) {
        }
        if (active(c)) s.addAll(allowedNow(c));
        return s.toArray(new String[0]);
    }

    static void apply(Context c, JSONObject policy, Set<String> allowed) {
        if (!policy.optBoolean("homeScreen", false) || paused(c)) {
            clear(c);
            return;
        }
        DevicePolicyManager dpm = Agent.dpm(c);
        ComponentName admin = Agent.admin(c);
        try {
            boolean first = !Agent.prefs(c).getBoolean("kioskOn", false);
            Agent.putSet(c, "kioskAllowed", allowed);
            Agent.prefs(c).edit().putBoolean("kioskOn", true).apply();
            dpm.setLockTaskPackages(admin, lockTaskPackages(c));
            if (Build.VERSION.SDK_INT >= 28) {
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_HOME
                        | DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS
                        | DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                        | DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
                        | DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS);
            }
            if (first) {
                Agent.addEvent(c, "restriction", "Home screen mode is on: only allowed apps can be opened");
                startHome(c);
            }
            Agent.prefs(c).edit().putBoolean("kioskError", false).apply();
        } catch (Exception e) {
            Log.w(TAG, "home screen mode failed: " + e);
            if (!Agent.prefs(c).getBoolean("kioskError", false)) {
                Agent.prefs(c).edit().putBoolean("kioskError", true).apply();
                Agent.addEvent(c, "error", "Home screen mode could not be switched on: " + e.getMessage());
            }
        }
    }

    /** Back to the normal home screen and normal app access (the browser default, if any, is untouched). */
    static void clear(Context c) {
        boolean was = Agent.prefs(c).getBoolean("kioskOn", false);
        if (!was) return;
        DevicePolicyManager dpm = Agent.dpm(c);
        ComponentName admin = Agent.admin(c);
        try {
            if (!Actions.timedLockActive(c)) dpm.setLockTaskPackages(admin, new String[0]);
            if (Build.VERSION.SDK_INT >= 28) dpm.setLockTaskFeatures(admin, 0);
        } catch (Exception e) {
            Log.w(TAG, "clear lock task failed: " + e);
        }
        Agent.prefs(c).edit().putBoolean("kioskOn", false).remove("kioskAllowed").apply();
        Agent.addEvent(c, "restriction", "Home screen mode is off");
    }

    /**
     * HomeActivity only re-checks whether it should still be active every 30s or on resume; this
     * tells it right away instead of leaving the phone stuck on the kiosk screen until then. Call
     * this AFTER syncPreferredActivities() has already cleared HomeActivity as the preferred home
     * app -- telling it to leave before that was cleared raced finish() against Android still
     * considering HomeActivity the home app, which could show a stuck black screen in between.
     */
    static void announceChange(Context c) {
        c.sendBroadcast(new Intent(ACTION_CHANGED).setPackage(c.getPackageName()));
    }

    static final String ACTION_CHANGED = "com.familymdm.agent.action.KIOSK_CHANGED";

    /**
     * The only place that touches Android's persistent-preferred-activity list, since setting one
     * requires clearing ALL of them for the package first — so home and browser defaults must be
     * kept in sync together, not registered/cleared independently.
     */
    static void syncPreferredActivities(Context c, boolean wantHome, boolean wantBrowser) {
        boolean hadHome = Agent.prefs(c).getBoolean("prefHome", false);
        boolean hadBrowser = Agent.prefs(c).getBoolean("prefBrowser", false);
        if (hadHome == wantHome && hadBrowser == wantBrowser) return;
        DevicePolicyManager dpm = Agent.dpm(c);
        ComponentName admin = Agent.admin(c);
        try {
            if ((hadHome && !wantHome) || (hadBrowser && !wantBrowser)) {
                dpm.clearPackagePersistentPreferredActivities(admin, c.getPackageName());
                hadHome = false;
                hadBrowser = false;
            }
            if (wantHome && !hadHome) {
                IntentFilter home = new IntentFilter(Intent.ACTION_MAIN);
                home.addCategory(Intent.CATEGORY_HOME);
                home.addCategory(Intent.CATEGORY_DEFAULT);
                dpm.addPersistentPreferredActivity(admin, home, new ComponentName(c, HomeActivity.class));
            }
            if (wantBrowser && !hadBrowser) {
                IntentFilter browse = new IntentFilter(Intent.ACTION_VIEW);
                browse.addCategory(Intent.CATEGORY_DEFAULT);
                browse.addCategory(Intent.CATEGORY_BROWSABLE);
                browse.addDataScheme("http");
                browse.addDataScheme("https");
                dpm.addPersistentPreferredActivity(admin, browse,
                        new ComponentName("com.familymdm.browser", "com.familymdm.browser.BrowserActivity"));
                Agent.addEvent(c, "restriction", "The Browser app is now the only browser for web links");
            }
            if (hadBrowser && !wantBrowser) Agent.addEvent(c, "restriction", "Other browsers can be used again");
            Agent.prefs(c).edit().putBoolean("prefHome", wantHome).putBoolean("prefBrowser", wantBrowser).apply();
        } catch (Exception e) {
            Log.w(TAG, "preferred activity sync failed: " + e);
        }
    }

    static void startHome(Context c) {
        try {
            c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Log.w(TAG, "could not open the home screen: " + e);
        }
    }

    /** After a reboot or restart of the agent: bring the home screen back if the mode is on. */
    static void ensureHome(Context c) {
        if (active(c) && !HomeActivity.alive) startHome(c);
    }
}
