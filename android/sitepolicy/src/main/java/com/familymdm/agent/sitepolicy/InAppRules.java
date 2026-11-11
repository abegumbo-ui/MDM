package com.familymdm.agent.sitepolicy;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * "Web pages inside apps": what the Agent's accessibility service closes. Pure Java, checked on a
 * computer (see InAppRulesCheck).
 *
 * Modes: "off"; "tabs" (the browser page an app opens on top of itself, e.g. Chrome's "custom
 * tab" from a link in Instagram or Gmail); "all" (also web pages built into an app, Android's
 * WebView). "all" can break apps whose own screens are web pages, hence the exceptions list.
 */
public final class InAppRules {
    private InAppRules() {}

    /** Real browsers, our own apps and Android itself: never treated as "an app showing a web page". */
    public static final Set<String> NEVER = new HashSet<>(Arrays.asList(
            "com.familymdm.agent", "com.familymdm.browser", "com.familymdm.lockdown",
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "org.mozilla.firefox", "org.mozilla.focus",
            "com.sec.android.app.sbrowser", "com.microsoft.emmx", "com.brave.browser", "com.opera.browser",
            "com.opera.mini.native", "com.duckduckgo.mobile.android", "com.kiwibrowser.browser", "com.vivaldi.browser",
            "android", "com.android.systemui", "com.android.settings", "com.google.android.gms",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller",
            "com.android.vending", "com.google.android.packageinstaller", "com.android.packageinstaller",
            "com.google.android.captiveportallogin", "com.android.captiveportallogin",
            "com.google.android.inputmethod.latin", "com.samsung.android.honeyboard"));

    /** Is this screen a browser page opened on top of another app (Chrome, Samsung Internet, Edge, Firefox...)? */
    public static boolean isCustomTab(String className) {
        if (className == null) return false;
        String c = className.toLowerCase(Locale.ROOT);
        return c.contains("customtab") || c.contains("custom_tab");
    }

    /** Should this screen be closed? hasWebView: the app's screen contains a web page. */
    public static boolean blocks(String mode, String pkg, String className, boolean hasWebView, Collection<String> exceptions) {
        if (mode == null || "off".equals(mode)) return false;
        if (isCustomTab(className)) return true; // "tabs" and "all"
        if (!"all".equals(mode) || !hasWebView || pkg == null) return false;
        if (NEVER.contains(pkg)) return false;
        return exceptions == null || !exceptions.contains(pkg);
    }
}
