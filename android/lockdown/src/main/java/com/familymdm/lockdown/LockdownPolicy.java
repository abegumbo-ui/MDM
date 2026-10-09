package com.familymdm.lockdown;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** The restriction catalog and account-ID validation, ported from the agent app's own
 * LocalPolicy.java -- this app is deliberately its own module with no dependency on the agent
 * (it never talks to it, never shares a process), so the small bit of logic it needs is
 * duplicated here rather than coupling the two apps together. */
final class LockdownPolicy {
    private LockdownPolicy() {}

    /** Pre-checked (allowed) by default on the Regular Apps picker the first time it's opened --
     * the three apps this phone is actually meant to run. Not hardcoded or forced: each one is a
     * normal row on that picker like any other app, and can be unchecked (blocked) same as
     * anything else if that's ever wanted. */
    static final String[] DEFAULT_ALLOWED_APPS = {
            "com.google.android.apps.maps", "com.waze", "com.google.android.projection.gearhead",
    };

    /** Parts of the system every allowed app (and the lock screen itself) needs to keep working:
     * permission prompts, file picker, share sheets, Google sign-in. Deliberately does NOT include
     * com.android.settings -- see SettingsCategories for how specific real Settings screens stay
     * reachable without ever putting the whole Settings app in the allowlist. */
    static final String[] SYSTEM_ESSENTIALS = {
            "android", "com.android.systemui",
            "com.android.documentsui", "com.google.android.documentsui",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller",
            "com.google.android.gms",
    };

    /** Applied unconditionally at "This device is set up" -- no picker for these, since the whole
     * point of this app is a small, fixed, non-negotiable lockdown rather than a pick-your-own
     * restrictions list. Nothing can be installed or removed again, Settings can't touch app
     * controls, and neither a factory reset from Settings nor Safe Mode offers a way around any of
     * the above (an actual recovery-mode factory reset still works, which is why the Factory Reset
     * Protection account matters). */
    static final String[] ALWAYS_ON_LOCKDOWN = {
            "no_install_apps", "no_uninstall_apps", "no_install_unknown_sources", "no_control_apps",
            "no_factory_reset", "no_safe_boot",
    };

    /**
     * Every other android.os.UserManager.DISALLOW_* restriction worth offering here -- defense in
     * depth, in case something ever reaches a screen outside the in-app Settings menu's own tightly
     * limited set (an OS bug, a future Android version routing a shortcut differently, anything not
     * foreseen). "No developer options" (DISALLOW_DEBUGGING_FEATURES) is the one that also blocks
     * the About phone > Build number tap-seven-times trick -- there's no separate restriction for
     * tapping the build number itself; blocking debugging features blocks the whole unlock flow.
     *
     * None of these are applied automatically -- they're offered on their own picker, individually
     * toggleable, nothing checked by default. Some of them actively fight functionality this app
     * already builds on purpose (the Wi-Fi/Bluetooth Settings categories, calls and texts), so
     * checking those specific ones will break that functionality -- that's left to the admin's own
     * judgment rather than decided here, since whether that trade-off is wanted depends on what this
     * particular phone is actually for.
     */
    static final String[] EXTRA_RESTRICTIONS = {
            "no_debugging_features", "no_oem_unlock", "no_config_credentials",
            "no_modify_accounts", "no_add_user", "no_remove_user", "no_add_managed_profile",
            "no_remove_managed_profile", "no_add_clone_profile", "no_add_private_profile",
            "no_config_location", "no_share_location", "no_airplane_mode", "no_config_mobile_networks",
            "no_config_tethering", "no_config_vpn", "disallow_config_private_dns", "no_network_reset",
            "no_config_cell_broadcasts", "no_data_roaming", "no_usb_file_transfer", "no_physical_media",
            "no_config_locale", "no_config_brightness", "no_ambient_display", "no_config_screen_timeout",
            "no_config_date_time", "no_adjust_volume", "no_camera", "no_record_audio",
            "no_unmute_microphone", "disallow_unmute_device", "no_fun", "no_create_windows",
            "no_system_error_dialogs", "no_cross_profile_copy_paste", "no_outgoing_beam",
            "no_wallpaper", "no_set_wallpaper", "no_run_in_background", "no_set_user_icon",
            "no_config_wifi", "no_change_wifi_state", "no_wifi_tethering", "no_sharing_admin_configured_wifi",
            "no_wifi_direct", "no_add_wifi_config",
            "no_config_bluetooth", "no_bluetooth", "no_bluetooth_sharing",
            "no_outgoing_calls", "no_sms",
    };

    /** The subset of EXTRA_RESTRICTIONS that fights the Wi-Fi/Connected devices Settings categories
     * or calls and texts -- flagged on the picker with a warning, not left out of it. */
    private static final Set<String> CONFLICTS_WITH_BUILTINS = new HashSet<>(Arrays.asList(
            "no_config_wifi", "no_change_wifi_state", "no_wifi_tethering", "no_sharing_admin_configured_wifi",
            "no_wifi_direct", "no_add_wifi_config",
            "no_config_bluetooth", "no_bluetooth", "no_bluetooth_sharing",
            "no_outgoing_calls", "no_sms"));

    static boolean conflictsWithBuiltins(String restrictionKey) {
        return CONFLICTS_WITH_BUILTINS.contains(restrictionKey);
    }

    /** A plain-English label for a restriction key -- its own key, "no_"/"disallow_" dropped,
     * underscores to spaces, each word capitalized. Good enough for a read-only list; nothing here
     * needs the hand-written copy a picker UI would justify. */
    static String humanize(String restrictionKey) {
        String s = restrictionKey.startsWith("no_") ? restrictionKey.substring(3)
                : restrictionKey.startsWith("disallow_") ? restrictionKey.substring(9) : restrictionKey;
        String[] words = s.split("_");
        StringBuilder sb = new StringBuilder("Block ");
        for (int i = 0; i < words.length; i++) {
            if (words[i].isEmpty()) continue;
            if (i > 0) sb.append(' ');
            sb.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return sb.toString();
    }

    private static final Set<String> PROTECTED_EXACT = new HashSet<>(Arrays.asList(
            "android", "com.android.systemui", "com.android.settings", "com.android.vending", "com.android.phone",
            "com.android.server.telecom", "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller", "com.google.android.gms",
            "com.google.android.gsf", "com.google.android.webview", "com.android.webview",
            "com.android.documentsui", "com.google.android.documentsui"));

    private static final Pattern[] PROTECTED_PATTERNS = {
            Pattern.compile("^com\\.android\\.providers\\."),
            Pattern.compile("^com\\.android\\.inputmethod\\."),
            Pattern.compile("inputmethod"),
            Pattern.compile("launcher", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^com\\.android\\.(bluetooth|nfc|networkstack|captiveportal|certinstaller|keychain|se|shell|emergency)"),
            Pattern.compile("^com\\.google\\.android\\.(networkstack|ext\\.|modulemetadata|captiveportal)"),
            Pattern.compile("^com\\.qualcomm\\."),
            Pattern.compile("^com\\.mediatek\\."),
    };

    /** Never offered on either app picker -- blocking or hiding these can break the phone outright.
     * This app's own package is excluded separately, by the caller. */
    static boolean isProtected(String pkg) {
        if (PROTECTED_EXACT.contains(pkg)) return true;
        for (Pattern p : PROTECTED_PATTERNS) if (p.matcher(pkg).find()) return true;
        return false;
    }

    private static final Pattern ACCOUNT_ID = Pattern.compile("^\\d{21}$");

    /** True for a Google account ID: exactly 21 digits (a leading "people/" is accepted and
     * stripped first). Real Google account IDs returned by the People API are consistently 21
     * digits; anything else typed here is a mistake worth catching before it's locked in for good. */
    static boolean validAccountId(String s) {
        s = s.trim();
        if (s.startsWith("people/")) s = s.substring(7);
        return ACCOUNT_ID.matcher(s).matches();
    }

    static String normalizeAccountId(String s) {
        s = s.trim();
        if (s.startsWith("people/")) s = s.substring(7);
        return s;
    }
}
