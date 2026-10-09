package com.familymdm.lockdown;

import java.util.HashMap;
import java.util.Map;

/**
 * A plain-English note for common no-icon system packages, judged specifically against a
 * Waze/Google Maps/Android Auto-only build (the default this app ships with) -- not a general
 * statement about what's safe on every phone for every use. "GMS" (com.google.android.gms) and
 * the handful of other things navigation apps actually depend on are never offered on the picker
 * at all (see LockdownPolicy.isProtected/SYSTEM_ESSENTIALS), so nothing in this catalog overrides
 * that -- it's only commentary on the apps the picker actually lets you block.
 *
 * This list is necessarily incomplete -- phones from different makers ship different system
 * packages. Anything not listed here gets an honest "not in the list" note instead of a guess.
 */
final class SystemAppSafety {
    private SystemAppSafety() {}

    private static final String SAFE = "Safe to block for a Waze/Maps-only build -- not used by either app.";
    private static final String SAFE_SETUP_ONLY = "Safe to block -- only used during initial phone setup, which is already done by this point.";

    private static final Map<String, String> NOTES = new HashMap<>();
    static {
        // Setup-only, one-shot tools -- irrelevant once the phone is already provisioned.
        NOTES.put("com.google.android.setupwizard", SAFE_SETUP_ONLY);
        NOTES.put("com.google.android.partnersetup", SAFE_SETUP_ONLY);
        NOTES.put("com.google.android.onetimeinitializer", SAFE_SETUP_ONLY);
        NOTES.put("com.google.android.apps.restore", SAFE_SETUP_ONLY);

        // Background Google features navigation doesn't touch.
        NOTES.put("com.google.android.apps.turbo", SAFE); // Device Health Services
        NOTES.put("com.google.android.as", SAFE); // Android System Intelligence
        NOTES.put("com.google.android.feedback", SAFE);
        NOTES.put("com.google.android.backuptransport", SAFE);
        NOTES.put("com.google.android.printservice.recommendation", SAFE);
        NOTES.put("com.google.android.syncadapters.calendar", SAFE + " Only matters if you use Calendar.");
        NOTES.put("com.google.android.syncadapters.contacts", SAFE + " Only matters if you use Contacts sync.");
        NOTES.put("com.google.android.markup", SAFE);
        NOTES.put("com.google.android.apps.wellbeing", SAFE); // Digital Wellbeing, if no-icon on this phone

        // Plain AOSP background utilities.
        NOTES.put("com.android.musicfx", SAFE);
        NOTES.put("com.android.statementservice", SAFE + " Only affects verified deep links, not in-app navigation.");
        NOTES.put("com.android.htmlviewer", SAFE);
        NOTES.put("com.android.wallpaperbackup", SAFE);
        NOTES.put("com.android.wallpapercropper", SAFE);
        NOTES.put("com.android.dreams.basic", SAFE); // screensaver
        NOTES.put("com.android.dreams.phototable", SAFE);
        NOTES.put("com.android.mtp", "Safe to block for a Waze/Maps-only build if you don't plug this phone into a computer for USB file transfer.");
        NOTES.put("com.google.android.tts", "Caution: Google Text-to-Speech. Most apps play pre-recorded turn directions, but if Waze or Maps "
                + "ever uses the system TTS engine for spoken directions on this phone, blocking this silences that. Test with navigation "
                + "running before relying on it being blocked.");

        // Things that matter even for a navigation-only phone -- keep these.
        NOTES.put("com.android.cellbroadcastreceiver", "Keep this -- delivers emergency weather/AMBER alerts. Not related to Waze/Maps, but worth keeping on a phone in a car.");
        NOTES.put("com.google.android.cellbroadcastreceiver", "Keep this -- delivers emergency weather/AMBER alerts. Not related to Waze/Maps, but worth keeping on a phone in a car.");
        NOTES.put("com.android.cellbroadcastservice", "Keep this -- backs the emergency alert receiver above.");
        NOTES.put("com.android.companiondevicemanager", "Keep this -- handles Bluetooth companion pairing, which Android Auto's wireless connection can depend on.");
        NOTES.put("com.google.android.ims", "Keep this -- carrier/Wi-Fi calling service. Not used by Waze/Maps, but blocking it can break phone calls on some carriers.");
    }

    /** True for a package name that starts a known family of car/Android-Auto-projection
     * services -- OEM naming varies, so this is a prefix check rather than an exact map entry. */
    private static boolean isAutomotiveRelated(String pkg) {
        return pkg.startsWith("com.google.android.apps.automotive.")
                || pkg.startsWith("com.android.car.")
                || pkg.startsWith("com.google.android.projection.");
    }

    /** A short, honest verdict for this package against a Waze/Maps-only build. Never claims
     * certainty for a package it doesn't actually recognize. */
    static String note(String pkg) {
        String known = NOTES.get(pkg);
        if (known != null) return known;
        if (isAutomotiveRelated(pkg)) {
            return "Keep this -- looks like part of Android Auto's own projection service.";
        }
        return "Not in the recognized list for this build -- look up what this package is before blocking it if you don't recognize the name.";
    }
}
