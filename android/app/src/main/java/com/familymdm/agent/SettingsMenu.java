package com.familymdm.agent;

import android.content.Context;
import android.content.Intent;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The fake Settings menu's own category list -- a deliberate subset of SettingsWatchdog's 22
 * (Google, System updates, Passwords, Security and privacy, Safety and emergency, Accessibility,
 * and Digital wellbeing are left out entirely, by design, not by omission), each pointed at a real
 * screen rather than this app's own guesswork:
 *
 * - Most point at a stable android.settings.* action string. Some of those are published, public
 *   API; a few (noted below) only exist as @hide constants in AOSP source, unusable as a Java
 *   symbol (that's what broke the build once already -- see MainActivity's QUICK_SETTINGS), but
 *   the underlying string is a real, working Intent action regardless -- @hide only blocks compile-
 *   time access to the symbol, not the runtime action. Used as a plain string literal here instead.
 * - A few have no android-wide action at all, public or hidden -- Motorola's own invention, with no
 *   backing Activity action documented anywhere. Those are learned on this specific phone instead,
 *   the same capture mechanism SettingsWatchdog's Learn already uses, just saved as the one exact
 *   screen to jump to rather than a blocking-match fragment. They won't carry over to a different
 *   phone model without learning them again there.
 */
final class SettingsMenu {
    private SettingsMenu() {}

    /** Display order, left to right as the real Settings app shows them. */
    static final List<String> ORDER = Arrays.asList(
            "network", "connected", "apps", "notifications", "sound", "modes", "personalize",
            "display", "homeLock", "gesture", "storage", "battery", "system", "aboutPhone", "location");

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
}
