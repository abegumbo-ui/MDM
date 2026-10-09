package com.familymdm.lockdown;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.telecom.TelecomManager;
import android.provider.Telephony;
import android.util.Log;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Lock task setup, ported and trimmed from the agent app's own Kiosk.java -- reversible, via the
 * Lockdown switch: activate() turns the kiosk takeover on, deactivate() turns it back off, and
 * either can run any number of times as the admin flips that switch.
 */
final class Kiosk {
    private static final String TAG = "Lockdown";

    private Kiosk() {}

    /** Everything that may come to the foreground once locked: this app itself (the kiosk launcher
     * and its own in-app Settings screen both live here), the system essentials, whatever the admin
     * allowed on the Regular Apps picker (Maps/Waze/Android Auto are pre-checked there by default,
     * but are ordinary rows -- not hardcoded here), and the phone's own default dialer/SMS handler
     * (so calls and texts still work). Deliberately never includes com.android.settings -- see
     * SettingsCategories for how specific real Settings screens stay reachable without it. */
    static String[] lockTaskPackages(Context c, Set<String> adminAllowed) {
        Set<String> s = new LinkedHashSet<>();
        s.add(c.getPackageName());
        for (String e : LockdownPolicy.SYSTEM_ESSENTIALS) s.add(e);
        s.addAll(adminAllowed);
        try {
            String dialer = ((TelecomManager) c.getSystemService(Context.TELECOM_SERVICE)).getDefaultDialerPackage();
            if (dialer != null) s.add(dialer);
        } catch (Exception ignored) {
        }
        try {
            String sms = Telephony.Sms.getDefaultSmsPackage(c);
            if (sms != null) s.add(sms);
        } catch (Exception ignored) {
        }
        return s.toArray(new String[0]);
    }

    /** Locks the phone to HomeActivity for good: sets the allowlist, turns on lock task, and makes
     * HomeActivity the permanent default home app (no picker dialog, no way to choose a different
     * launcher afterward). */
    static void activate(Context c, DevicePolicyManager dpm, ComponentName admin, Set<String> adminAllowed) {
        dpm.setLockTaskPackages(admin, lockTaskPackages(c, adminAllowed));
        if (Build.VERSION.SDK_INT >= 28) {
            dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_HOME
                    | DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS
                    | DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                    | DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
                    | DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
                    | DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW);
        }
        try {
            IntentFilter home = new IntentFilter(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addCategory(Intent.CATEGORY_DEFAULT);
            dpm.addPersistentPreferredActivity(admin, home, new ComponentName(c, HomeActivity.class));
        } catch (Exception e) {
            Log.w(TAG, "could not set the permanent home app: " + e);
        }
        startHome(c);
    }

    /** Reverses activate(): clears the lock task allowlist, clears the permanent home app, and
     * exits lock task mode if the calling activity is currently pinned in it (MainActivity's own
     * package is always in that allowlist, so this works correctly when called from there). */
    static void deactivate(Activity a, DevicePolicyManager dpm, ComponentName admin) {
        try {
            dpm.setLockTaskPackages(admin, new String[0]);
        } catch (Exception e) {
            Log.w(TAG, "could not clear the lock task allowlist: " + e);
        }
        try {
            dpm.clearPackagePersistentPreferredActivities(admin, a.getPackageName());
        } catch (Exception e) {
            Log.w(TAG, "could not clear the permanent home app: " + e);
        }
        try {
            ActivityManager am = (ActivityManager) a.getSystemService(Context.ACTIVITY_SERVICE);
            if (am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE) a.stopLockTask();
        } catch (Exception e) {
            Log.w(TAG, "could not exit lock task mode: " + e);
        }
    }

    static void startHome(Context c) {
        try {
            c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Log.w(TAG, "could not open the home screen: " + e);
        }
    }
}
