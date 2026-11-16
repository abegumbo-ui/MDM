package com.familymdm.agent.sitepolicy;

import java.util.Locale;

/**
 * Android decides whether a Wi-Fi network "has internet" by fetching a tiny page that answers "204 No Content" (and a few fallbacks). When the
 * browser-inspection proxy is the phone's global proxy, those fetches go through it, and a filter that refuses every site that is not approved
 * (Strict, "Approved sites only") refused them too: Android then marked the network "partial connectivity" and every Wi-Fi said
 * "Sign in to the network" although nothing needed signing in to (seen on a Galaxy Tab S9 FE, 2026-11-15).
 *
 * These addresses carry nothing but the connectivity answer, so they are never refused.
 * The list is Android's own: AOSP config_captive_portal_http_url / https_url / fallback urls.
 */
public final class ConnectivityProbe {
    private ConnectivityProbe() {
    }

    /** Hosts that exist only to answer Android's internet check (the whole host is allowed). */
    private static final String[] PROBE_ONLY_HOSTS = {
            "connectivitycheck.gstatic.com", "connectivitycheck.android.com", "clients3.google.com", "clients1.google.com",
    };

    /** Hosts that also serve real pages: only the check's own paths are allowed. */
    private static final String[] PROBE_PATH_HOSTS = {"www.google.com", "play.googleapis.com"};

    private static String clean(String host) {
        if (host == null) return null;
        String h = host.trim().toLowerCase(Locale.ROOT);
        return h.endsWith(".") ? h.substring(0, h.length() - 1) : h;
    }

    /** True for a host that only answers Android's internet check. */
    public static boolean isProbeHost(String host) {
        String h = clean(host);
        if (h == null) return false;
        for (String p : PROBE_ONLY_HOSTS) if (h.equals(p)) return true;
        return false;
    }

    /**
     * True for the HTTPS tunnel of Android's own internet check. Its fetch (https://www.google.com/generate_204) cannot accept the certificate
     * browser inspection hands out (Android's network checker trusts only the system's own certificates), so with inspection on every Wi-Fi was
     * marked "partial connectivity" and showed "!" (seen on the A16, 2026-11-15). The check is recognised by the User-Agent it always sends:
     * Android's fixed one (Chrome 60 on desktop Linux), which no browser on a phone sends. Such a tunnel is passed through untouched, and only
     * for the hosts that answer the check.
     */
    public static boolean isProbeTunnel(String host, String userAgent) {
        String h = clean(host);
        if (h == null) return false;
        if (isProbeHost(h)) return true; // these hosts exist only for the check: nothing there to inspect
        boolean known = false;
        for (String p : PROBE_PATH_HOSTS) if (h.equals(p)) known = true;
        if (!known) return false;
        // The check's own User-Agent, if it is on the tunnel request...
        if (userAgent != null && userAgent.contains("X11; Linux x86_64") && userAgent.contains("Chrome/60.0.3112.32")) return true;
        // ...but Android's HTTP stack puts its own fixed identity on a tunnel request instead (or none): "okhttp/x", "Dalvik/x", "Java/x". A browser
        // (Chrome, our Browser, Samsung Internet) always sends its own "Mozilla/5.0 ..." there, so a browser is never passed through untouched.
        if (userAgent == null || userAgent.trim().isEmpty()) return true;
        String ua = userAgent.trim();
        return ua.startsWith("okhttp/") || ua.startsWith("Dalvik/") || ua.startsWith("Java/");
    }

    /** True for a request that is Android's internet check (path and query as sent, e.g. "/generate_204"). */
    public static boolean isProbe(String host, String path) {
        if (isProbeHost(host)) return true;
        String h = clean(host);
        if (h == null || path == null) return false;
        boolean known = false;
        for (String p : PROBE_PATH_HOSTS) if (h.equals(p)) known = true;
        if (!known) return false;
        int q = path.indexOf('?');
        String only = q >= 0 ? path.substring(0, q) : path;
        return only.equals("/generate_204") || only.equals("/gen_204");
    }
}
