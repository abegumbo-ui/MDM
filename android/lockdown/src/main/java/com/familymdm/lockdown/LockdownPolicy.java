package com.familymdm.lockdown;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** The restriction catalog and account-ID validation, ported from the agent app's own
 * LocalPolicy.java -- this app is deliberately its own module with no dependency on the agent
 * (it never talks to it, never shares a process), so the small bit of logic it needs is
 * duplicated here rather than coupling the two apps together. */
final class LockdownPolicy {
    private LockdownPolicy() {}

    /** {Android restriction key, label, "1" if checked by default}. The first ten are the same
     * curated, human-labeled set the agent app offers; the device has several dozen more (every
     * other android.os.UserManager.DISALLOW_* restriction) -- those are included too, further
     * down, with an auto-generated label instead of a hand-written one. */
    static final String[][] CURATED_RESTRICTIONS = {
            {"no_factory_reset", "Block factory reset from Settings", "1"},
            {"no_safe_boot", "Block Safe Mode", "1"},
            {"no_uninstall_apps", "Block uninstalling apps", "1"},
            {"no_control_apps", "Block changing apps in Settings", "1"},
            {"no_modify_accounts", "Block adding accounts", "1"},
            {"no_add_user", "Block adding users", "1"},
            {"no_install_unknown_sources", "Block installing from unknown sources", "1"},
            {"no_install_apps", "Block ALL app installs (including Play Store)", "1"},
            {"no_config_credentials", "Only this setup can set the screen lock", "0"},
            {"no_debugging_features", "Block Developer options and USB debugging (turn on last)", "0"},
    };

    /** Every other restriction Android exposes through DevicePolicyManager.addUserRestriction(),
     * for "all the restrictions" -- shown with an auto-humanized label (its own key, "no_" dropped,
     * underscores to spaces) since there's no hand-written copy for all ~60 of these anywhere in
     * the app. None are checked by default; the ten above already cover the ones worth defaulting on. */
    static final String[] OTHER_RESTRICTIONS = {
            "no_config_location", "no_airplane_mode", "no_config_mobile_networks", "no_config_tethering",
            "no_config_vpn", "disallow_config_private_dns",
            "no_config_wifi", "no_change_wifi_state", "no_wifi_tethering", "no_sharing_admin_configured_wifi",
            "no_wifi_direct", "no_add_wifi_config", "no_config_locale", "no_share_location",
            "no_config_brightness", "no_ambient_display", "no_config_screen_timeout", "no_config_bluetooth",
            "no_bluetooth", "no_bluetooth_sharing", "no_usb_file_transfer", "no_remove_user",
            "no_remove_managed_profile", "no_config_date_time", "no_network_reset", "no_add_managed_profile",
            "no_add_clone_profile", "no_add_private_profile", "no_config_cell_broadcasts", "no_physical_media",
            "no_unmute_microphone", "no_adjust_volume", "no_outgoing_calls", "no_sms", "no_fun",
            "no_create_windows", "no_system_error_dialogs", "no_cross_profile_copy_paste", "no_outgoing_beam",
            "no_wallpaper", "no_set_wallpaper", "no_record_audio", "no_run_in_background", "no_camera",
            "disallow_unmute_device", "no_data_roaming", "no_set_user_icon", "no_oem_unlock",
    };

    /** These are always applied at "Close Forever," regardless of what else was checked -- the
     * whole point of this app is that nothing can ever be installed or removed again. */
    static final String[] ALWAYS_ON_LOCKDOWN = {
            "no_install_apps", "no_uninstall_apps", "no_install_unknown_sources", "no_control_apps",
    };

    static String humanize(String restrictionKey) {
        String s = restrictionKey.startsWith("no_") ? restrictionKey.substring(3) : restrictionKey;
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

    /** Never offered on the "apps to delete" list -- hiding or uninstalling these can break the
     * phone outright. This app's own package is excluded separately, by the caller. */
    static boolean isProtected(String pkg) {
        if (PROTECTED_EXACT.contains(pkg)) return true;
        for (Pattern p : PROTECTED_PATTERNS) if (p.matcher(pkg).find()) return true;
        return false;
    }

    private static final Pattern ACCOUNT_ID = Pattern.compile("^\\d{15,25}$");

    /** True for a Google account ID: 15 to 25 digits (a leading "people/" is accepted). */
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
