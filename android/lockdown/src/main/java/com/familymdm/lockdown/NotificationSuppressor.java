package com.familymdm.lockdown;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Set;

/**
 * A soft block, separate from hiding an app outright: a muted package keeps running normally --
 * needed for things like Android Auto, which needs the Google app and Google Play Services alive
 * in the background even though nobody should be able to tap into either one -- but any
 * notification it tries to show gets dismissed the instant it posts, so there's nothing on screen
 * to tap into. Only active while the Lockdown switch is on, same as every other enforcement in
 * this app. Needs "Notification access" granted to this app once, manually, via Settings --
 * Android does not let a device owner grant this one silently via DevicePolicyManager, unlike
 * every other permission this app needs.
 */
public class NotificationSuppressor extends NotificationListenerService {

    private SharedPreferences prefs() {
        return getSharedPreferences("lockdown", MODE_PRIVATE);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!prefs().getBoolean("lockdownOn", false)) return;
        Set<String> muted = prefs().getStringSet("mutedNotificationPackages", null);
        if (muted != null && muted.contains(sbn.getPackageName())) {
            cancelNotification(sbn.getKey());
        }
    }

    /** No AndroidX dependency in this app, so this reads the same system setting
     * NotificationManagerCompat.getEnabledListenerPackages() would, directly. */
    static boolean isEnabled(Context c) {
        String enabled = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(c.getPackageName());
    }
}
