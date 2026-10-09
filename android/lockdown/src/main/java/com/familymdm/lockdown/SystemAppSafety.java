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
 * Covers stock Android/Pixel, Samsung (One UI), and Motorola, since this app is meant to run on
 * whatever phone it's set up on -- cheap or flagship, any of these three makers -- not just
 * whichever one it happened to be tested on. This list is still necessarily incomplete: every
 * maker ships a different bloat set, and a new OS version renames things. Anything not listed
 * here gets an honest "not in the list" note instead of a guess, with a maker-specific hint when
 * the package name itself gives one away.
 */
final class SystemAppSafety {
    private SystemAppSafety() {}

    private static final String SAFE = "Safe to block for a Waze/Maps-only build -- not used by either app.";
    private static final String SAFE_SETUP_ONLY = "Safe to block -- only used during initial phone setup, which is already done by this point.";
    private static final String BLOCK_LOOPHOLE = "Block this -- it's a way to install or run apps outside what this app allows, which defeats the point of the lockdown.";

    private static final Map<String, String> NOTES = new HashMap<>();
    static {
        // ---------- stock Android / Pixel ----------
        NOTES.put("com.google.android.setupwizard", SAFE_SETUP_ONLY);
        NOTES.put("com.google.android.partnersetup", SAFE_SETUP_ONLY);
        NOTES.put("com.google.android.onetimeinitializer", SAFE_SETUP_ONLY);
        NOTES.put("com.google.android.apps.restore", SAFE_SETUP_ONLY);

        NOTES.put("com.google.android.apps.turbo", SAFE); // Device Health Services
        NOTES.put("com.google.android.as", SAFE); // Android System Intelligence
        NOTES.put("com.google.android.feedback", SAFE);
        NOTES.put("com.google.android.backuptransport", SAFE);
        NOTES.put("com.google.android.printservice.recommendation", SAFE);
        NOTES.put("com.google.android.syncadapters.calendar", SAFE + " Only matters if you use Calendar.");
        NOTES.put("com.google.android.syncadapters.contacts", SAFE + " Only matters if you use Contacts sync.");
        NOTES.put("com.google.android.markup", SAFE);
        NOTES.put("com.google.android.apps.wellbeing", SAFE); // Digital Wellbeing, if no-icon on this phone
        NOTES.put("com.google.android.apps.tips", SAFE); // Pixel Tips
        NOTES.put("com.google.android.apps.pixelmigrate", SAFE_SETUP_ONLY); // Pixel data-transfer-in tool
        NOTES.put("com.google.android.apps.safetyhub", "Caution: Personal Safety -- includes car crash detection. Not used by "
                + "Waze/Maps, but worth keeping if this phone rides in a car and no other crash-detection feature covers that.");

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

        NOTES.put("com.android.cellbroadcastreceiver", "Keep this -- delivers emergency weather/AMBER alerts. Not related to Waze/Maps, but worth keeping on a phone in a car.");
        NOTES.put("com.google.android.cellbroadcastreceiver", "Keep this -- delivers emergency weather/AMBER alerts. Not related to Waze/Maps, but worth keeping on a phone in a car.");
        NOTES.put("com.android.cellbroadcastservice", "Keep this -- backs the emergency alert receiver above.");
        NOTES.put("com.android.companiondevicemanager", "Keep this -- handles Bluetooth companion pairing, which Android Auto's wireless connection can depend on.");
        NOTES.put("com.google.android.ims", "Keep this -- carrier/Wi-Fi calling service. Not used by Waze/Maps, but blocking it can break phone calls on some carriers.");

        // ---------- Samsung (One UI) ----------
        // Galaxy Store is a second app store -- the single biggest loophole on a Samsung phone,
        // since it can install apps this lockdown never offered a choice about.
        NOTES.put("com.sec.android.app.samsungapps", BLOCK_LOOPHOLE + " (Galaxy Store -- a second app store.)");
        NOTES.put("com.samsung.android.app.appsedge", BLOCK_LOOPHOLE + " (Samsung's app-install launcher shortcut.)");
        NOTES.put("com.samsung.android.game.gamehome", SAFE); // Game Launcher
        NOTES.put("com.samsung.android.game.gametools", SAFE);
        NOTES.put("com.samsung.android.bixby.agent", SAFE); // Bixby voice
        NOTES.put("com.samsung.android.visionintelligence", SAFE); // Bixby Vision
        NOTES.put("com.samsung.android.app.spage", SAFE); // Samsung Free / Bixby Home panel
        NOTES.put("com.samsung.android.spay", SAFE); // Samsung Pay/Wallet
        NOTES.put("com.samsung.android.spayfw", SAFE);
        NOTES.put("com.samsung.android.service.aircommand", SAFE); // Air Command (S Pen)
        NOTES.put("com.samsung.android.app.watchmanager", SAFE); // Galaxy Wearable
        NOTES.put("com.samsung.android.mdx", "Caution: Samsung's cross-device continuity service (Link to Windows / Multi Control). "
                + "Not used by Waze/Maps, but has its own remote-pairing surface -- safe to block for a nav-only build.");
        NOTES.put("com.samsung.android.smartswitchassistant", "Caution: Smart Switch, Samsung's phone-to-phone data transfer tool. Not used "
                + "by Waze/Maps -- safe to block, but it's also the normal way to move data off this phone later, so expect that to stop working.");
        NOTES.put("com.samsung.android.easySetup", SAFE_SETUP_ONLY);
        NOTES.put("com.samsung.android.app.updatecenter", "Caution: Samsung's own app/firmware update checker, separate from the Play "
                + "Store and Settings system update. Blocking it stops it nagging, but isn't itself a loophole the way Galaxy Store is.");
        NOTES.put("com.samsung.android.findmymobile", "Caution: Find My Mobile can remotely unlock, locate, or factory-reset this phone "
                + "from a Samsung account. That's a real way around this lockdown if someone else controls that account -- block it unless "
                + "you specifically want that remote-control ability and control the account yourself.");
        NOTES.put("com.samsung.android.fmm", "Caution: part of Find My Mobile -- see com.samsung.android.findmymobile. Same remote-control "
                + "risk if you don't control the Samsung account on this phone.");
        NOTES.put("com.samsung.android.knox.containercore", "Keep this if present and you don't know why -- part of Samsung Knox security, "
                + "not something to block blindly. Not used by Waze/Maps either way.");
        NOTES.put("com.samsung.android.forest", SAFE); // Digital Wellbeing (Samsung's version)
        NOTES.put("com.samsung.android.rubin.app", SAFE); // Bixby routines/"Edge Lighting"-adjacent
        NOTES.put("com.samsung.android.lool", SAFE); // Device Care
        NOTES.put("com.samsung.android.providers.context", SAFE);
        NOTES.put("com.samsung.android.messaging", "Caution: Samsung's own SMS/Messages app. Only matters if you rely on texting -- "
                + "not used by Waze/Maps.");
        NOTES.put("com.samsung.android.app.galaxyfinder", SAFE); // on-device search
        NOTES.put("com.samsung.android.allshare.service.mediashare", SAFE);

        // ---------- Motorola ----------
        NOTES.put("com.motorola.actions", SAFE); // Moto Gestures (chop for flashlight, etc.)
        NOTES.put("com.motorola.help", SAFE); // Moto Help / Device Help
        NOTES.put("com.motorola.frontdooragent", SAFE);
        NOTES.put("com.motorola.timeweatherwidget", SAFE);
        NOTES.put("com.motorola.demo", SAFE); // retail demo mode
        NOTES.put("com.motorola.contacts.extractContactPhoto", SAFE);
        NOTES.put("com.motorola.audio.jbl", SAFE); // JBL/audio tuning partnerships
        NOTES.put("com.motorola.sym.feedback", SAFE);
        NOTES.put("com.motorola.android.providers.settings", "Keep this -- Motorola's settings data provider. Blocking providers can break "
                + "the phone outright; don't treat this like the others above.");
    }

    /** True for a package name that starts a known family of car/Android-Auto-projection
     * services -- OEM naming varies, so this is a prefix check rather than an exact map entry. */
    private static boolean isAutomotiveRelated(String pkg) {
        return pkg.startsWith("com.google.android.apps.automotive.")
                || pkg.startsWith("com.android.car.")
                || pkg.startsWith("com.google.android.projection.");
    }

    /** For a package this catalog doesn't name specifically, a maker-specific hint beats a flat
     * "unknown" -- enough to point someone in the right direction without claiming certainty. */
    private static String makerHint(String pkg) {
        if (pkg.startsWith("com.samsung.") || pkg.startsWith("com.sec.android."))
            return "This looks like Samsung's own One UI software, not stock Android or Google -- not in this catalog by name, "
                    + "but most One UI extras unrelated to Wi-Fi/calls/the Settings app are safe to block for a Waze/Maps-only build. "
                    + "Watch for anything that sounds like a second app store, remote device management, or data transfer -- those are "
                    + "worth blocking specifically because they're ways around a lockdown, not just unused extras.";
        if (pkg.startsWith("com.motorola."))
            return "This looks like Motorola's own software, not stock Android or Google -- not in this catalog by name, but most "
                    + "Motorola extras unrelated to Wi-Fi/calls/the Settings app are safe to block for a Waze/Maps-only build.";
        return null;
    }

    /** A short, honest verdict for this package against a Waze/Maps-only build. Never claims
     * certainty for a package it doesn't actually recognize. */
    static String note(String pkg) {
        String known = NOTES.get(pkg);
        if (known != null) return known;
        if (isAutomotiveRelated(pkg)) {
            return "Keep this -- looks like part of Android Auto's own projection service.";
        }
        String hint = makerHint(pkg);
        if (hint != null) return hint;
        return "Not in the recognized list for this build -- look up what this package is before blocking it if you don't recognize the name.";
    }
}
