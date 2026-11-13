package com.familymdm.agent.sitepolicy;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        /** Only for type "youtube": what kind of entry ("channel") and its id (the "@handle", lower case). */
        public final String kind;
        public final String id;
        /** Allowed only until this time (ms since 1970), 0 = always. */
        public final long until;

        public Site(String type, String url, String host, String label, boolean blockImages, boolean installable) {
            this(type, url, host, label, blockImages, installable, null, null);
        }

        public Site(String type, String url, String host, String label, boolean blockImages, boolean installable, String kind, String id) {
            this(type, url, host, label, blockImages, installable, kind, id, 0);
        }

        public Site(String type, String url, String host, String label, boolean blockImages, boolean installable, String kind, String id, long until) {
            this.until = until;
            this.type = type;
            this.url = url;
            this.host = host;
            this.label = label;
            this.blockImages = blockImages;
            this.installable = installable;
            this.kind = kind;
            this.id = id;
        }

        /** Still allowed (its time, if it has one, is not up). */
        public boolean live() {
            return until <= 0 || System.currentTimeMillis() < until;
        }
    }

    /** An entry allowed "for 1 hour" (or until some time) whose time is up. */
    public static boolean isExpired(JSONObject o) {
        long u = o == null ? 0 : o.optLong("until", 0);
        return u > 0 && System.currentTimeMillis() >= u;
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
        // Every YouTube video shares the address "youtube.com/watch" -- only the ?v= part tells them apart.
        // Without this, allowing one video "page" would quietly allow every video.
        String video = youtubeVideoId(u);
        if (video != null) return "youtube.com/watch?v=" + video;
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
            if (o == null || isExpired(o)) continue;
            long until = Math.max(0, o.optLong("until", 0));
            String type = o.optString("type", "");
            String url = o.optString("url", "");
            if (type.equals("youtube")) {
                String id = o.optString("id", "").toLowerCase(Locale.ROOT);
                if (!o.optString("kind", "").equals("channel") || !id.matches("@[a-z0-9._-]{3,30}")) continue;
                out.add(new Site("youtube", url, "youtube.com", o.optString("label", "YouTube " + id), false, false, "channel", id, until));
                continue;
            }
            if (!type.equals("domain") && !type.equals("exact")) continue;
            String host = hostOf(url);
            if (host == null) continue;
            out.add(new Site(type, url, host, o.optString("label", host), o.optBoolean("blockImages", false), o.optBoolean("installable", true), null, null, until));
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
            if (!s.live()) continue; // allowed for a while, and the time is up
            if (s.type.equals("youtube")) continue; // channel entries are decided by the Browser (see youtubeChannelAllowed)
            if (s.type.equals("domain") && (host.equals(s.host) || host.endsWith("." + s.host))) return s;
            if (s.type.equals("exact") && path != null && path.equals(pathKeyOf(s.url))) return s;
        }
        return null;
    }

    // ---------- YouTube ----------

    private static final Set<String> YOUTUBE_HOSTS = new HashSet<>(Arrays.asList(
            "youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com", "youtu.be"));

    /** Google's and YouTube's sign-in pages (opened only when the parent allowed signing in). */
    public static boolean isYoutubeSignIn(String u) {
        String host = hostOf(u);
        if (host == null) return false;
        if (host.equals("accounts.google.com") || host.equals("accounts.youtube.com") || host.equals("myaccount.google.com")) return true;
        if (!isYoutubeHost(host)) return false;
        try {
            String p = new URI(u.contains("://") ? u : "https://" + u).getPath();
            if (p == null) return false;
            String l = p.toLowerCase(Locale.ROOT);
            return l.startsWith("/signin") || l.startsWith("/servicelogin") || l.startsWith("/accounts");
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static boolean isYoutubeHost(String host) {
        return host != null && YOUTUBE_HOSTS.contains(host.toLowerCase(Locale.ROOT));
    }

    private static String queryParam(URI uri, String name) {
        String q = uri.getRawQuery();
        if (q == null) return null;
        for (String pair : q.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) return pair.substring(eq + 1);
        }
        return null;
    }

    /** The 11-character id of the video this address plays (watch, youtu.be, shorts, embed, live), or null. */
    public static String youtubeVideoId(String u) {
        String host = hostOf(u);
        if (!isYoutubeHost(host)) return null;
        try {
            URI uri = new URI(u.contains("://") ? u : "https://" + u);
            String path = uri.getPath() == null ? "" : uri.getPath();
            String id = null;
            if (host.equals("youtu.be")) {
                String[] seg = path.split("/");
                id = seg.length > 1 ? seg[1] : null;
            } else if (path.equals("/watch")) {
                id = queryParam(uri, "v");
            } else {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^/(?:shorts|embed|live|v)/([^/?#]+)").matcher(path);
                if (m.find()) id = m.group(1);
            }
            return id != null && id.matches("[A-Za-z0-9_-]{11}") ? id : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** The "@handle" (lower case) of the channel page this address is, or null. */
    public static String youtubeChannelHandle(String u) {
        String host = hostOf(u);
        if (host == null || !(host.equals("youtube.com") || host.equals("m.youtube.com"))) return null;
        try {
            URI uri = new URI(u.contains("://") ? u : "https://" + u);
            String path = uri.getPath() == null ? "" : uri.getPath();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^/(@[A-Za-z0-9._-]{3,30})(?:/|$)").matcher(path);
            return m.find() ? m.group(1).toLowerCase(Locale.ROOT) : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    // What kind of YouTube page an address is, for the "restricted YouTube" setting.
    public static final int YT_OTHER = 0;   // not allowed: Shorts, feeds, trending, hashtags, anything unrecognised
    public static final int YT_HOME = 1;    // the home page: replaced by a plain search page, never the recommendation feed
    public static final int YT_SEARCH = 2;  // search results
    public static final int YT_VIDEO = 3;   // one video (watch, youtu.be, embed, live)
    public static final int YT_CHANNEL = 4; // a channel or playlist page
    public static final int YT_SYSTEM = 5;  // YouTube's own consent / sign-in screens

    /** Classifies an address on a YouTube host. Anything not clearly one of the allowed kinds is YT_OTHER. */
    public static int youtubeKind(String u) {
        String host = hostOf(u);
        if (host == null) return YT_OTHER;
        if (host.equals("consent.youtube.com") || host.equals("accounts.youtube.com")) return YT_SYSTEM;
        if (!isYoutubeHost(host)) return YT_OTHER;
        if (youtubeVideoId(u) != null) {
            // Shorts have a video id too, but are not offered in restricted mode.
            try {
                String p = new URI(u.contains("://") ? u : "https://" + u).getPath();
                if (p != null && p.startsWith("/shorts")) return YT_OTHER;
            } catch (URISyntaxException e) {
                return YT_OTHER;
            }
            return YT_VIDEO;
        }
        try {
            String path = new URI(u.contains("://") ? u : "https://" + u).getPath();
            if (path == null || path.isEmpty() || path.equals("/")) return YT_HOME;
            if (path.equals("/results") || (host.equals("music.youtube.com") && path.equals("/search"))) return YT_SEARCH;
            if (path.equals("/playlist")) return YT_CHANNEL;
            if (path.matches("^/(@[^/]+|channel/[^/]+|c/[^/]+|user/[^/]+)(/.*)?$")) return YT_CHANNEL;
        } catch (URISyntaxException e) {
            return YT_OTHER;
        }
        return YT_OTHER;
    }

    /** True for the kinds of YouTube page restricted mode lets through (not the home page, which is replaced). */
    public static boolean youtubeRestrictedAllows(String u) {
        int k = youtubeKind(u);
        return k == YT_SEARCH || k == YT_VIDEO || k == YT_CHANNEL || k == YT_SYSTEM;
    }

    public static boolean hasYoutubeChannels(List<Site> sites) {
        for (Site s : sites) if (s.type.equals("youtube") && s.live()) return true;
        return false;
    }

    /** True if this channel handle is one of the allowed YouTube channels. */
    public static boolean youtubeChannelAllowed(String handle, List<Site> sites) {
        if (handle == null) return false;
        String h = handle.toLowerCase(Locale.ROOT);
        for (Site s : sites) if (s.type.equals("youtube") && s.live() && h.equals(s.id)) return true;
        return false;
    }

    /**
     * A short, hardcoded list of some of the best-known adult sites, blocked no matter what the
     * allowlist says and with no override (not even the master code). This is a safety net for a
     * mistake, not the actual filter: the real protection is that the allowlist opens nothing unless
     * it was explicitly approved. This list is intentionally small (a few dozen of the most-visited
     * names) and easy to get around — any adult site not on it loads fine if it were ever allowlisted.
     */
    private static final Set<String> BLOCKED_ADULT_DOMAINS = new HashSet<>(Arrays.asList(
            "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com", "youporn.com",
            "tube8.com", "spankbang.com", "beeg.com", "txxx.com", "porn.com", "pornone.com",
            "chaturbate.com", "livejasmin.com", "onlyfans.com", "brazzers.com", "motherless.com",
            "rule34.xxx", "e-hentai.org", "hentaihaven.xxx", "nhentai.net", "fapello.com",
            "thumbzilla.com", "eporner.com", "sex.com", "porntrex.com", "porndig.com", "4chan.org"));

    public static boolean isBlockedAdult(String url) {
        String host = hostOf(url);
        if (host == null) return false;
        for (String d : BLOCKED_ADULT_DOMAINS) {
            if (host.equals(d) || host.endsWith("." + d)) return true;
        }
        return false;
    }
}
