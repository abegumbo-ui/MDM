package com.familymdm.lockdown;

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
 * One-way lock task setup, ported and trimmed from the agent app's own Kiosk.java -- there's no
 * "off" here, since nothing about this app is meant to be reversible from the phone once Close
 * Forever has run. activate() is called exactly once, at that moment.
 */
final class Kiosk {
    private static final String TAG = "Lockdown";

    private Kiosk() {}

    /** Everything that may come to the foreground once locked: the three always-allowed apps, the
     * system essentials, whatever the admin additionally allowed on the Regular Apps picker, and
     * the phone's own default dialer/SMS handler (so calls and texts still work). */
    static String[] lockTaskPackages(Context c, Set<String> adminAllowed) {
        Set<String> s = new LinkedHashSet<>();
        for (String e : LockdownPolicy.SYSTEM_ESSENTIALS) s.add(e);
        for (String e : LockdownPolicy.ALWAYS_ALLOWED_APPS) s.add(e);
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

    static void startHome(Context c) {
        try {
            c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Log.w(TAG, "could not open the home screen: " + e);
        }
    }
}
