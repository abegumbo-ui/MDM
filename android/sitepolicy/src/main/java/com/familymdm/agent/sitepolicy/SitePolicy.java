package com.familymdm.agent.sitepolicy;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Browser allowlist matching, shared by the agent app and the separate Browser app. Pure Java (no
 * Android classes) so it can be checked on a computer. Mirrors src/policy.js — keep them in step.
 */
public final class SitePolicy {
    private SitePolicy() {}

    public static final class Site {
        public final String type; // "domain" or "exact"
        public final String url;
        public final String host;
        public final String label;
        public final boolean blockImages;
        public final boolean installable;

        public Site(String type, String url, String host, String label, boolean blockImages, boolean installable) {
            this.type = type;
            this.url = url;
            this.host = host;
            this.label = label;
            this.blockImages = blockImages;
            this.installable = installable;
        }
    }

    public static String hostOf(String u) {
        if (u == null) return null;
        try {
            URI uri = new URI(u.contains("://") ? u : "https://" + u);
            String h = uri.getHost();
            if (h == null) return null;
            h = h.toLowerCase(Locale.ROOT);
            return h.startsWith("www.") ? h.substring(4) : h;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** host + normalized path, ignoring query string, fragment and a trailing slash. */
    public static String pathKeyOf(String u) {
        String host = hostOf(u);
        if (host == null) return null;
        try {
            URI uri = new URI(u.contains("://") ? u : "https://" + u);
            String path = uri.getPath();
            if (path == null || path.isEmpty()) path = "/";
            while (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            return host + path;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** Parses the "sites" array from a policy JSON (the shape src/policy.js sends). */
    public static List<Site> parse(JSONArray arr) {
        List<Site> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String type = o.optString("type", "");
            String url = o.optString("url", "");
            if (!type.equals("domain") && !type.equals("exact")) continue;
            String host = hostOf(url);
            if (host == null) continue;
            out.add(new Site(type, url, host, o.optString("label", host), o.optBoolean("blockImages", false), o.optBoolean("installable", true)));
        }
        return out;
    }

    public static boolean allowed(String url, List<Site> sites) {
        return matching(url, sites) != null;
    }

    /** The first allowlist entry that covers this URL, or null. */
    public static Site matching(String url, List<Site> sites) {
        String host = hostOf(url);
        if (host == null) return null;
        String path = pathKeyOf(url);
        for (Site s : sites) {
            if (s.type.equals("domain") && (host.equals(s.host) || host.endsWith("." + s.host))) return s;
            if (s.type.equals("exact") && path != null && path.equals(pathKeyOf(s.url))) return s;
        }
        return null;
    }
}
