package com.familymdm.agent.sitepolicy;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Small rules shared by the Agent, the Browser and the inspection proxy. Pure Java, checked on a
 * computer (see ProtectionRulesCheck).
 */
public final class ProtectionRules {
    private ProtectionRules() {}

    /**
     * Parts of Android that must keep their internet even when "internet only for these apps" is on,
     * or the phone itself stops working properly: notifications for the allowed apps come through
     * Google Play services, Wi-Fi sign-in pages need the captive-portal app, and so on.
     */
    public static final Set<String> ALWAYS_ONLINE = new HashSet<>(Arrays.asList(
            "com.familymdm.agent", "android", "com.android.phone", "com.android.systemui",
            "com.google.android.gms", "com.google.android.gsf", "com.android.providers.telephony",
            "com.android.networkstack", "com.google.android.networkstack", "com.android.networkstack.tethering",
            "com.android.captiveportallogin", "com.google.android.captiveportallogin",
            "com.android.cellbroadcastreceiver", "com.google.android.cellbroadcastreceiver"));

    /** With "internet only for these apps" on, may this app use the internet? An unknown app (null) is not blocked here. */
    public static boolean internetAllowed(String pkg, boolean onlyChosen, Set<String> chosen) {
        if (!onlyChosen || pkg == null) return true;
        if (ALWAYS_ONLINE.contains(pkg)) return true;
        return chosen != null && chosen.contains(pkg);
    }

    /** Is this address an image search (Google, Bing, DuckDuckGo)? */
    public static boolean isImageSearch(String url) {
        if (url == null) return false;
        try {
            URI u = new URI(url.contains("://") ? url : "https://" + url);
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            String path = u.getPath() == null ? "" : u.getPath().toLowerCase(Locale.ROOT);
            String q = u.getRawQuery() == null ? "" : "&" + u.getRawQuery().toLowerCase(Locale.ROOT) + "&";
            if (host.startsWith("images.google.")) return true;
            if (host.matches("(www\\.)?google\\.[a-z.]{2,7}") && (path.equals("/search") || path.equals("/imgres"))
                    && (q.contains("&tbm=isch&") || q.contains("&udm=2&") || path.equals("/imgres"))) return true;
            if (host.matches("(www\\.)?bing\\.com") && path.startsWith("/images")) return true;
            if (host.matches("(www\\.)?duckduckgo\\.com") && (q.contains("&ia=images&") || q.contains("&iax=images&"))) return true;
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
