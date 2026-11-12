package com.familymdm.lockdown;

/**
 * Every Android restriction both apps offer, with the dashboard's plain name, one sentence on why
 * it matters, and a group: 1 = Recommended (shown first), 2 = More, 3 = Advanced (hidden behind
 * "Show all"; mostly about work profiles, radios and system details that do not matter here).
 * Generated from src/policy.js's RESTRICTIONS (tools: see the commit); the same file is in the
 * Agent (sitepolicy) and the Lockdown app.
 */
public final class RestrictionInfo {
    private RestrictionInfo() {}

    public static final String[][] ALL = {
            {"no_factory_reset", "Block factory reset from Settings", "Nobody can wipe the phone from Settings to get rid of the controls.", "1"},
            {"no_safe_boot", "Block Safe Mode", "Safe Mode would start the phone without these controls.", "1"},
            {"no_uninstall_apps", "Block uninstalling apps", "Apps cannot be removed (including this one).", "1"},
            {"no_control_apps", "Block changing apps in Settings", "Apps cannot be stopped, cleared or switched off in Settings.", "1"},
            {"no_modify_accounts", "Block adding accounts", "No new Google or other accounts can be added.", "1"},
            {"no_add_user", "Block adding users", "A second user on the phone would have no controls.", "1"},
            {"no_install_unknown_sources", "Block installing from unknown sources", "Apps from files or websites (not the Play Store) cannot be installed.", "1"},
            {"no_install_apps", "Block ALL app installs (including Play Store)", "No app at all can be installed, not even from the Play Store.", "2"},
            {"no_config_credentials", "Only the administrator can set the screen lock (the person can't set their own PIN or pattern)", "Only you can set or change the screen lock.", "1"},
            {"no_debugging_features", "Block Developer options and USB debugging", "Developer options and USB debugging can remove the controls with a computer. Turn on after setup.", "1"},
            {"no_config_location", "Block changing Location settings", "Location stays on (needed for location history).", "2"},
            {"no_airplane_mode", "Block turning on Airplane mode", "Airplane mode cannot be turned on.", "2"},
            {"no_config_mobile_networks", "Block changing mobile network settings", "Mobile data settings cannot be changed.", "2"},
            {"no_camera", "Disable the camera entirely", "The camera does not open.", "2"},
            {"no_config_tethering", "Block Wi-Fi hotspot and tethering", "No Wi-Fi hotspot or USB/Bluetooth tethering.", "2"},
            {"no_config_vpn", "Block adding or changing a VPN", "A VPN app could go around the filter.", "1"},
            {"disallow_config_private_dns", "Block changing Private DNS", "Private DNS could go around the filter.", "1"},
            {"no_config_wifi", "Block changing Wi-Fi settings", "Wi-Fi networks cannot be added or changed.", "2"},
            {"no_change_wifi_state", "Block turning Wi-Fi on or off", "Wi-Fi cannot be turned on or off.", "2"},
            {"no_wifi_tethering", "Block Wi-Fi tethering", "No Wi-Fi hotspot.", "2"},
            {"no_sharing_admin_configured_wifi", "Block sharing admin-configured Wi-Fi (QR code, password)", "Wi-Fi you set cannot be shared with others.", "2"},
            {"no_wifi_direct", "Block Wi-Fi Direct", "No Wi-Fi Direct sharing.", "2"},
            {"no_add_wifi_config", "Block adding new Wi-Fi networks", "No new Wi-Fi networks.", "2"},
            {"no_config_locale", "Block changing language and region", "The language cannot be changed.", "2"},
            {"no_share_location", "Block sharing location", "Apps cannot share where the phone is.", "2"},
            {"no_config_brightness", "Block changing screen brightness", "Brightness cannot be changed.", "2"},
            {"no_ambient_display", "Block ambient display (always-on display)", "", "3"},
            {"no_config_screen_timeout", "Block changing screen timeout", "How fast the screen turns off cannot be changed.", "2"},
            {"no_config_bluetooth", "Block changing Bluetooth settings", "Bluetooth settings cannot be changed.", "2"},
            {"no_bluetooth", "Turn off Bluetooth entirely", "Bluetooth is off completely.", "2"},
            {"no_bluetooth_sharing", "Block sharing files over Bluetooth", "No sending files over Bluetooth.", "2"},
            {"no_usb_file_transfer", "Block USB file transfer", "No copying files to or from a computer.", "2"},
            {"no_remove_user", "Block removing users (not relevant without secondary users)", "", "3"},
            {"no_remove_managed_profile", "Block removing a work profile (not relevant without one)", "", "3"},
            {"no_config_date_time", "Block changing date and time", "Changing the clock could get around bedtime and time limits.", "1"},
            {"no_network_reset", "Block \"Reset network settings\"", "\"Reset network settings\" could remove the filter's connection settings.", "1"},
            {"no_add_managed_profile", "Block adding a work profile", "A work profile would have no controls.", "1"},
            {"no_add_clone_profile", "Block adding a cloned app profile", "A cloned copy of an app would have no controls.", "1"},
            {"no_add_private_profile", "Block adding a private space profile", "A private space would hide apps from the controls.", "1"},
            {"no_config_cell_broadcasts", "Block changing emergency cell broadcast settings", "", "3"},
            {"no_physical_media", "Block mounting SD cards or USB storage", "No SD cards or USB sticks.", "2"},
            {"no_unmute_microphone", "Keep the microphone forced muted", "", "3"},
            {"no_adjust_volume", "Block changing volume", "The volume cannot be changed.", "2"},
            {"no_outgoing_calls", "Block making calls (emergency calls still work)", "Only emergency calls.", "2"},
            {"no_sms", "Block sending and receiving SMS", "No text messages at all.", "2"},
            {"no_fun", "Disable the \"Easter egg\" (build-number tap tricks)", "", "3"},
            {"no_create_windows", "Block apps from drawing over other apps", "Apps cannot draw over other apps (some get around blocks that way).", "2"},
            {"no_system_error_dialogs", "Suppress crash and \"app not responding\" dialogs", "", "3"},
            {"no_cross_profile_copy_paste", "Block copy/paste between profiles", "", "3"},
            {"no_outgoing_beam", "Block Android Beam (old NFC sharing)", "", "3"},
            {"no_wallpaper", "Block viewing or changing wallpaper at all", "", "3"},
            {"no_set_wallpaper", "Block changing wallpaper (can still view it)", "", "3"},
            {"no_record_audio", "Block every app from recording audio", "", "3"},
            {"no_run_in_background", "Stop apps from running in the background", "", "3"},
            {"disallow_unmute_device", "Keep the ringer forced silent", "", "3"},
            {"no_data_roaming", "Block enabling data roaming", "No data roaming abroad.", "2"},
            {"no_set_user_icon", "Block changing the profile icon", "", "3"},
            {"no_oem_unlock", "Block unlocking the bootloader", "Unlocking the bootloader is the first step to wiping the phone from a computer.", "1"},
            {"no_unified_password", "Force a separate work-profile password (not relevant without one)", "", "3"},
            {"no_autofill", "Block the autofill service", "", "3"},
            {"no_content_capture", "Block content capture (used by some assistants)", "", "3"},
            {"no_content_suggestions", "Block content suggestions", "", "3"},
            {"no_user_switch", "Block switching between users (not relevant without secondary users)", "", "3"},
            {"no_sharing_into_profile", "Block sharing content into a work profile", "", "3"},
            {"no_printing", "Block printing", "", "3"},
            {"disallow_microphone_toggle", "Block the quick-settings microphone privacy toggle", "", "3"},
            {"disallow_camera_toggle", "Block the quick-settings camera privacy toggle", "", "3"},
            {"disallow_biometric", "Block enrolling fingerprint or face unlock", "No new fingerprints or faces can be added.", "2"},
            {"disallow_config_default_apps", "Block changing default apps (browser, etc.)", "For example the default browser.", "2"},
            {"no_cellular_2g", "Block allowing 2G cellular connections", "", "3"},
            {"no_ultra_wideband_radio", "Block Ultra-wideband radio", "", "3"},
            {"no_near_field_communication_radio", "Turn off NFC entirely", "", "3"},
            {"no_change_near_field_communication_radio", "Block changing NFC on/off", "", "3"},
            {"no_thread_network", "Block Thread network radio (smart-home)", "", "3"},
            {"no_sim_globally", "Disable the SIM entirely", "", "3"},
            {"no_assist_content", "Block sharing screen content with the assistant", "", "3"},
            {"no_install_unknown_sources_globally", "Block installing from unknown sources, for every user", "The same, for every user on the phone.", "1"},
    };

    public static String[] find(String key) {
        for (String[] r : ALL) if (r[0].equals(key)) return r;
        return null;
    }

    public static String label(String key) {
        String[] r = find(key);
        return r != null ? r[1] : key;
    }

    public static String why(String key) {
        String[] r = find(key);
        return r != null ? r[2] : "";
    }

    /** 1 = Recommended, 2 = More, 3 = Advanced. */
    public static int group(String key) {
        String[] r = find(key);
        return r != null ? Integer.parseInt(r[3]) : 3;
    }
}
