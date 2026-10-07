package com.familymdm.agent;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The fake Settings menu's own category list -- a deliberate subset of the 22 real Settings
 * categories (Google, System updates, Passwords, Security and privacy, Safety and emergency,
 * Accessibility, and Digital wellbeing are left out entirely, by design, not by omission), each
 * pointed at a real screen rather than this app's own guesswork:
 *
 * - Most point at a stable android.settings.* action string. Some of those are published, public
 *   API; a few (noted below) only exist as @hide constants in AOSP source, unusable as a Java
 *   symbol (that's what broke the build once already -- see MainActivity's QUICK_SETTINGS), but
 *   the underlying string is a real, working Intent action regardless -- @hide only blocks compile-
 *   time access to the symbol, not the runtime action. Used as a plain string literal here instead.
 * - A few have no android-wide action at all, public or hidden -- Motorola's own invention, with no
 *   backing Activity action documented anywhere. Those are learned on this specific phone instead:
 *   go into Settings for real, come back, and whatever screen was last open gets captured and saved
 *   as the one exact screen to jump to. They won't carry over to a different phone model without
 *   learning them again there.
 */
final class SettingsMenu {
    private SettingsMenu() {}

    /** Display order, left to right as the real Settings app shows them. */
    static final List<String> ORDER = Arrays.asList(
            "network", "connected", "apps", "notifications", "sound", "modes", "personalize",
            "display", "homeLock", "gesture", "storage", "battery", "system", "aboutPhone", "location");

    // All 22 real Settings categories' display labels -- wider than ORDER, since MainActivity's
    // own Quick Settings card (a different, older feature) covers a couple this fake menu doesn't.
    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put("google", "Google");
        LABELS.put("network", "Network and internet");
        LABELS.put("connected", "Connected devices");
        LABELS.put("apps", "Apps");
        LABELS.put("notifications", "Notifications");
        LABELS.put("sound", "Sound and vibration");
        LABELS.put("modes", "Modes");
        LABELS.put("personalize", "Personalize");
        LABELS.put("display", "Display");
        LABELS.put("homeLock", "Home and lock screen");
        LABELS.put("gesture", "Gesture");
        LABELS.put("storage", "Storage");
        LABELS.put("battery", "Battery");
        LABELS.put("system", "System");
        LABELS.put("systemUpdates", "System updates");
        LABELS.put("aboutPhone", "About phone");
        LABELS.put("passwords", "Passwords, passkeys and accounts");
        LABELS.put("security", "Security and privacy");
        LABELS.put("location", "Location");
        LABELS.put("digitalWellbeing", "Digital wellbeing and parental controls");
        LABELS.put("safety", "Safety and emergency");
        LABELS.put("accessibility", "Accessibility");
    }

    static String label(String category) {
        String l = LABELS.get(category);
        return l != null ? l : category;
    }

    /** Category -> real android.settings.* action string. Some are @hide; see class doc. */
    private static final Map<String, String> LINKS = new LinkedHashMap<>();
    static {
        LINKS.put("network", android.provider.Settings.ACTION_WIRELESS_SETTINGS);
        LINKS.put("apps", android.provider.Settings.ACTION_APPLICATION_SETTINGS);
        LINKS.put("notifications", android.provider.Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS);
        LINKS.put("sound", android.provider.Settings.ACTION_SOUND_SETTINGS);
        LINKS.put("display", android.provider.Settings.ACTION_DISPLAY_SETTINGS);
        LINKS.put("storage", android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
        LINKS.put("location", android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        LINKS.put("aboutPhone", android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS);
        // @hide in AOSP source, but a real action -- the general Do Not Disturb screen, not just
        // its "priority" sub-page.
        LINKS.put("modes", "android.settings.ZEN_MODE_SETTINGS");
        // Only the Battery Saver toggle -- there's no android-wide action for the full battery/
        // usage screen.
        LINKS.put("battery", android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS);
        // @hide, and only the lock-screen part -- no action covers wallpaper/home-app choice too.
        LINKS.put("homeLock", "android.settings.LOCK_SCREEN_SETTINGS");
    }

    /**
     * Used for "connected" only, until that category is learned: at least gets to Bluetooth, which
     * is better than nothing, even though Connection preferences (Bluetooth + NFC + Printing +
     * Quick Share + Android Auto together) is the real target once it's been learned.
     */
    private static final Map<String, String> FALLBACK_LINKS = new LinkedHashMap<>();
    static {
        FALLBACK_LINKS.put("connected", android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
    }

    /** No android-wide action exists at all -- only reachable once learned on this specific phone. */
    static final Set<String> NEEDS_LEARN = new LinkedHashSet<>(Arrays.asList(
            "connected", "personalize", "gesture", "system"));

    private static final String PREF_PREFIX = "fakeMenuTarget:";

    /** The exact "pkg/cls" learned on this phone for a NEEDS_LEARN category, or null if not yet. */
    static String learnedTarget(Context c, String category) {
        return Agent.prefs(c).getString(PREF_PREFIX + category, null);
    }

    static void setLearnedTarget(Context c, String category, String component) {
        Agent.prefs(c).edit().putString(PREF_PREFIX + category, component).apply();
    }

    static void clearLearnedTarget(Context c, String category) {
        Agent.prefs(c).edit().remove(PREF_PREFIX + category).apply();
    }

    /** What tapping this category's row should do -- an Intent to launch, or null if nothing is set up for it yet. */
    static Intent intentFor(Context c, String category) {
        String action = LINKS.get(category);
        if (action != null) return new Intent(action);
        String learned = learnedTarget(c, category);
        if (learned != null && learned.contains("/")) {
            String pkg = learned.substring(0, learned.indexOf('/'));
            String cls = learned.substring(learned.indexOf('/') + 1);
            Intent i = new Intent();
            i.setClassName(pkg, cls);
            return i;
        }
        String fallback = FALLBACK_LINKS.get(category);
        return fallback != null ? new Intent(fallback) : null;
    }

    /**
     * What was last on screen in Settings, looking back 15 minutes -- for Learn: already sitting on
     * the target screen, then pressing Learn, or coming back from Settings into MDM Agent itself.
     * Tracks the most recent settings-ish event specifically, not just the single most recent
     * foreground event of any kind: switching back into MDM Agent to finish the capture is itself a
     * foreground event, for com.familymdm.agent, and it would otherwise be the last one seen,
     * overwriting the real answer with "myself" every single time. Returns "pkg/cls", or null if
     * nothing settings-ish happened in the last 15 minutes at all.
     */
    static String captureNow(Context c) {
        try {
            tryGrantUsageAccess(c);
            UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;
            long now = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(now - 15 * 60 * 1000, now);
            String pkg = null, cls = null;
            UsageEvents.Event e = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                if (e.getEventType() != UsageEvents.Event.MOVE_TO_FOREGROUND) continue;
                String p = e.getPackageName();
                boolean settingsish = p.equals("com.android.settings") || p.toLowerCase().contains("settings")
                        || p.startsWith("com.google.android.apps.wellbeing") || p.equals("com.google.android.gms");
                if (settingsish) {
                    pkg = p;
                    cls = e.getClassName();
                }
            }
            return pkg != null ? pkg + "/" + cls : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * A device owner is often assumed to get Usage access automatically, with no prompt -- that
     * isn't reliable across OEMs (PACKAGE_USAGE_STATS is a "special" app-op permission, not a
     * normal runtime one, and nothing guarantees a device owner gets it for free).
     * setPermissionGrantState() is the one API a device owner has for trying to grant it anyway;
     * some Android/OEM combinations honor it, some silently don't. Either way this can't hurt -- if
     * it's ignored, the phone still needs it granted by hand under Settings > Apps > Special access
     * > Usage access.
     */
    static void tryGrantUsageAccess(Context c) {
        try {
            android.app.admin.DevicePolicyManager dpm = Agent.dpm(c);
            android.content.ComponentName admin = Agent.admin(c);
            if (dpm != null && admin != null && dpm.isDeviceOwnerApp(c.getPackageName())) {
                dpm.setPermissionGrantState(admin, c.getPackageName(), "android.permission.PACKAGE_USAGE_STATS",
                        android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
            }
        } catch (Exception ignored) {
        }
    }
}
