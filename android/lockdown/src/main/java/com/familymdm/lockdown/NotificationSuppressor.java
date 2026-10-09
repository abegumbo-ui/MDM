package com.familymdm.lockdown;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Set;

/**
 * An allow-list, not a block-list: every package is silent by default, and only a package
 * explicitly checked on the Notifications picker gets to post anything that stays. This is
 * deliberately independent of whether a package is blocked outright or has a kiosk home-screen
 * tile -- a package can keep running fine, even stay reachable, and still never get to notify
 * (Google Play Services and the Google app, specifically, which Android Auto needs alive in the
 * background but which nobody should ever see a notification from). Only active while the
 * Lockdown switch is on, same as every other enforcement in this app. Needs "Notification access"
 * granted to this app once, manually, via Settings -- Android does not let a device owner grant
 * this one silently via DevicePolicyManager, unlike every other permission this app needs.
 */
public class NotificationSuppressor extends NotificationListenerService {

    private SharedPreferences prefs() {
        return getSharedPreferences("lockdown", MODE_PRIVATE);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!prefs().getBoolean("lockdownOn", false)) return;
        Set<String> allowed = prefs().getStringSet("allowedNotificationPackages", null);
        if (allowed == null || !allowed.contains(sbn.getPackageName())) {
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
