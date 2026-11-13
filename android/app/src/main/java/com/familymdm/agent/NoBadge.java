package com.familymdm.agent;

import android.app.NotificationChannel;
import android.app.NotificationManager;

/**
 * Notification channels for this app, without the dot or number on the app's icons. The MDM app, the YouTube, YouTube
 * Music and Browser icons are one app, so Android would otherwise show the MDM app's notification dot on all of them.
 * A channel's dot setting cannot be changed once it exists, so each one gets a new id and the old one is removed
 * (a channel in use by a running foreground service cannot be removed; it is simply left unused).
 */
final class NoBadge {
    private NoBadge() {}

    static void install(NotificationManager nm, String id, String name, int importance, String... oldIds) {
        for (String old : oldIds) {
            try {
                nm.deleteNotificationChannel(old);
            } catch (Exception ignored) {
            }
        }
        NotificationChannel ch = new NotificationChannel(id, name, importance);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }
}
