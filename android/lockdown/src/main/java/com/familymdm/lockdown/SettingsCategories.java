package com.familymdm.lockdown;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The real Settings screens this app's own in-app Settings menu can offer -- chosen by the admin
 * during setup (see MainActivity's Settings step), shown by SettingsMenuActivity. Each one is a
 * real android.settings.* action, the same stable public actions the agent app's own SettingsMenu
 * uses. Deliberately limited to categories with a real, documented action for every Android
 * version and phone brand -- the agent app's equivalent list also has a handful of manufacturer-
 * only screens reachable only by a "Learn" step done by hand on that specific phone; this app has
 * no interactive setup like that (it's meant to be quick, and done once), so those aren't offered
 * here at all rather than being offered broken.
 *
 * "Connected devices" has no single Android-wide action (same gap the agent app documents on its
 * own equivalent) -- Bluetooth settings is the closest public action and still gets most of the
 * way there (Bluetooth pairing, and often a link onward to the full Connected devices page).
 */
final class SettingsCategories {
    private SettingsCategories() {}

    /** Display order. */
    static final String[] ORDER = {
            "network", "connected", "sound", "display", "battery", "storage", "location", "aboutPhone",
    };

    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    private static final Map<String, String> ACTIONS = new LinkedHashMap<>();
    static {
        LABELS.put("network", "Wi-Fi / Network");
        ACTIONS.put("network", android.provider.Settings.ACTION_WIRELESS_SETTINGS);
        LABELS.put("connected", "Connected devices (Bluetooth)");
        ACTIONS.put("connected", android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
        LABELS.put("sound", "Sound and vibration");
        ACTIONS.put("sound", android.provider.Settings.ACTION_SOUND_SETTINGS);
        LABELS.put("display", "Display");
        ACTIONS.put("display", android.provider.Settings.ACTION_DISPLAY_SETTINGS);
        LABELS.put("battery", "Battery");
        ACTIONS.put("battery", android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS);
        LABELS.put("storage", "Storage");
        ACTIONS.put("storage", android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
        LABELS.put("location", "Location");
        ACTIONS.put("location", android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        LABELS.put("aboutPhone", "About phone");
        ACTIONS.put("aboutPhone", android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS);
    }

    static String label(String category) {
        String l = LABELS.get(category);
        return l != null ? l : category;
    }

    static String action(String category) {
        return ACTIONS.get(category);
    }
}
