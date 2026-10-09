package com.familymdm.lockdown;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    /** Android restriction key -> plain-English label, in the exact order and wording used on the
     * dashboard's own Restrictions screen (src/policy.js's RESTRICTIONS) -- kept in sync by hand,
     * since this app deliberately has no dependency on the agent/dashboard code and never talks to
     * it. Every one of these is an ordinary, individually-toggleable row on the Device restrictions
     * picker; none are hardcoded as non-negotiable here either, same as every other picker in this
     * app -- the admin decides, and whatever's checked applies immediately, live, the same pattern
     * as the rest of setup. */
    static final LinkedHashMap<String, String> RESTRICTIONS = new LinkedHashMap<>();

    /** Which of the above start checked the first time the picker is opened -- the same defaults as
     * the dashboard's own "on: true" entries, the handful that make sense turned on for almost any
     * phone (factory reset chief among them). Everything else starts off. */
    static final Set<String> DEFAULT_ON_RESTRICTIONS = new LinkedHashSet<>();

    private static void r(String key, String label, boolean onByDefault) {
        RESTRICTIONS.put(key, label);
        if (onByDefault) DEFAULT_ON_RESTRICTIONS.add(key);
    }

    static {
        r("no_factory_reset", "Block factory reset from Settings", true);
        r("no_safe_boot", "Block Safe Mode", true);
        r("no_uninstall_apps", "Block uninstalling apps", true);
        r("no_control_apps", "Block changing apps in Settings", true);
        r("no_modify_accounts", "Block adding accounts", true);
        r("no_add_user", "Block adding users", true);
        r("no_install_unknown_sources", "Block installing from unknown sources", true);
        r("no_install_apps", "Block ALL app installs (including Play Store)", false);
        r("no_config_credentials", "Only the administrator can set the screen lock (the person can't set their own PIN or pattern)", false);
        r("no_debugging_features", "Block Developer options and USB debugging", true);
        r("no_config_location", "Block changing Location settings", false);
        r("no_airplane_mode", "Block turning on Airplane mode", false);
        r("no_config_mobile_networks", "Block changing mobile network settings", false);
        r("no_config_tethering", "Block Wi-Fi hotspot and tethering", false);
        r("no_config_vpn", "Block adding or changing a VPN", false);
        r("disallow_config_private_dns", "Block changing Private DNS", false);
        r("no_config_wifi", "Block changing Wi-Fi settings", false);
        r("no_change_wifi_state", "Block turning Wi-Fi on or off", false);
        r("no_wifi_tethering", "Block Wi-Fi tethering", false);
        r("no_sharing_admin_configured_wifi", "Block sharing admin-configured Wi-Fi (QR code, password)", false);
        r("no_wifi_direct", "Block Wi-Fi Direct", false);
        r("no_add_wifi_config", "Block adding new Wi-Fi networks", false);
        r("no_config_locale", "Block changing language and region", false);
        r("no_share_location", "Block sharing location", false);
        r("no_config_brightness", "Block changing screen brightness", false);
        r("no_ambient_display", "Block ambient display (always-on display)", false);
        r("no_config_screen_timeout", "Block changing screen timeout", false);
        r("no_config_bluetooth", "Block changing Bluetooth settings", false);
        r("no_bluetooth", "Turn off Bluetooth entirely", false);
        r("no_bluetooth_sharing", "Block sharing files over Bluetooth", false);
        r("no_usb_file_transfer", "Block USB file transfer", false);
        r("no_remove_user", "Block removing users (not relevant without secondary users)", false);
        r("no_remove_managed_profile", "Block removing a work profile (not relevant without one)", false);
        r("no_config_date_time", "Block changing date and time", false);
        r("no_network_reset", "Block \"Reset network settings\"", false);
        r("no_add_managed_profile", "Block adding a work profile", false);
        r("no_add_clone_profile", "Block adding a cloned app profile", false);
        r("no_add_private_profile", "Block adding a private space profile", false);
        r("no_config_cell_broadcasts", "Block changing emergency cell broadcast settings", false);
        r("no_physical_media", "Block mounting SD cards or USB storage", false);
        r("no_unmute_microphone", "Keep the microphone forced muted", false);
        r("no_adjust_volume", "Block changing volume", false);
        r("no_outgoing_calls", "Block making calls (emergency calls still work)", false);
        r("no_sms", "Block sending and receiving SMS", false);
        r("no_fun", "Disable the \"Easter egg\" (build-number tap tricks)", false);
        r("no_create_windows", "Block apps from drawing over other apps", false);
        r("no_system_error_dialogs", "Suppress crash and \"app not responding\" dialogs", false);
        r("no_cross_profile_copy_paste", "Block copy/paste between profiles", false);
        r("no_outgoing_beam", "Block Android Beam (old NFC sharing)", false);
        r("no_wallpaper", "Block viewing or changing wallpaper at all", false);
        r("no_set_wallpaper", "Block changing wallpaper (can still view it)", false);
        r("no_record_audio", "Block every app from recording audio", false);
        r("no_run_in_background", "Stop apps from running in the background", false);
        r("no_camera", "Disable the camera entirely", false);
        r("disallow_unmute_device", "Keep the ringer forced silent", false);
        r("no_data_roaming", "Block enabling data roaming", false);
        r("no_set_user_icon", "Block changing the profile icon", false);
        r("no_oem_unlock", "Block unlocking the bootloader", false);
        r("no_unified_password", "Force a separate work-profile password (not relevant without one)", false);
        r("no_autofill", "Block the autofill service", false);
        r("no_content_capture", "Block content capture (used by some assistants)", false);
        r("no_content_suggestions", "Block content suggestions", false);
        r("no_user_switch", "Block switching between users (not relevant without secondary users)", false);
        r("no_sharing_into_profile", "Block sharing content into a work profile", false);
        r("no_printing", "Block printing", false);
        r("disallow_microphone_toggle", "Block the quick-settings microphone privacy toggle", false);
        r("disallow_camera_toggle", "Block the quick-settings camera privacy toggle", false);
        r("disallow_biometric", "Block enrolling fingerprint or face unlock", false);
        r("disallow_config_default_apps", "Block changing default apps (browser, etc.)", false);
        r("no_cellular_2g", "Block allowing 2G cellular connections", false);
        r("no_ultra_wideband_radio", "Block Ultra-wideband radio", false);
        r("no_near_field_communication_radio", "Turn off NFC entirely", false);
        r("no_change_near_field_communication_radio", "Block changing NFC on/off", false);
        r("no_thread_network", "Block Thread network radio (smart-home)", false);
        r("no_sim_globally", "Disable the SIM entirely", false);
        r("no_assist_content", "Block sharing screen content with the assistant", false);
        r("no_install_unknown_sources_globally", "Block installing from unknown sources, for every user", false);
    }

    /** The subset of RESTRICTIONS that fights the Wi-Fi/Connected devices Settings categories or
     * calls and texts -- flagged on the picker with a warning, not left out of it. */
    private static final Set<String> CONFLICTS_WITH_BUILTINS = new HashSet<>(Arrays.asList(
            "no_config_wifi", "no_change_wifi_state", "no_wifi_tethering", "no_sharing_admin_configured_wifi",
            "no_wifi_direct", "no_add_wifi_config",
            "no_config_bluetooth", "no_bluetooth", "no_bluetooth_sharing",
            "no_outgoing_calls", "no_sms"));

    static boolean conflictsWithBuiltins(String restrictionKey) {
        return CONFLICTS_WITH_BUILTINS.contains(restrictionKey);
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
