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

    /** Always allowed to open, with no setup step needed for them -- the three apps this phone is
     * actually meant to run. Not shown as toggles on the Regular Apps picker, so there's nothing to
     * accidentally uncheck; everything else on that picker starts unchecked (blocked). */
    static final String[] ALWAYS_ALLOWED_APPS = {
            "com.google.android.apps.maps", "com.waze", "com.google.android.projection.gearhead",
    };

    /** Parts of the system every allowed app (and the lock screen itself) needs to keep working:
     * permission prompts, file picker, share sheets, Google sign-in, the phone/SMS apps, and
     * Settings itself (needed so Wi-Fi and Connected devices stay reachable -- see GuardService for
     * how it's kept to just those two areas once inside). */
    static final String[] SYSTEM_ESSENTIALS = {
            "android", "com.android.systemui", "com.android.settings",
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
