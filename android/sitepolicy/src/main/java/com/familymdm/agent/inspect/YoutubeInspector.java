package com.familymdm.agent.inspect;

import com.familymdm.agent.sitepolicy.SitePolicy;
import com.familymdm.agent.sitepolicy.YoutubeRestricted;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * The rules for "restricted YouTube" (see YoutubeRestricted), applied to every browser on the phone
 * through the inspection proxy instead of only inside our own browsers.
 *
 *  - Every request to YouTube carries "YouTube-Restrict: Strict". That is the header Google
 *    documents for network filters, and because it goes on every request (not just the first page)
 *    it covers YouTube's search and its in-page loading too.
 *  - Opening the home page shows a plain search box instead of the recommendation feed.
 *  - Shorts, feeds, trending and other pages that are not search, a video or a channel are refused.
 *  - The suggestions and comments around a video are hidden.
 *
 * Anything not on YouTube is left alone. Hosts in `bypass` are not even looked inside.
 */
public final class YoutubeInspector implements InspectProxy.Inspector {
    private final boolean restricted;
    private final Set<String> bypass;

    public YoutubeInspector(boolean restricted, Set<String> bypass) {
        this.restricted = restricted;
        this.bypass = bypass;
    }

    static boolean isYoutube(String host) {
        String h = host.toLowerCase(java.util.Locale.ROOT);
        return h.equals("youtube.com") || h.endsWith(".youtube.com") || h.equals("youtu.be")
                || h.equals("youtube-nocookie.com") || h.endsWith(".youtube-nocookie.com") || h.equals("youtubei.googleapis.com");
    }

    @Override
    public boolean bypass(String host, int port) {
        String h = host.toLowerCase(java.util.Locale.ROOT);
        for (String b : bypass) if (h.equals(b) || h.endsWith("." + b)) return true;
        return false;
    }

    private static boolean isDocument(InspectProxy.Request r) {
        if (!r.method.equals("GET")) return false;
        String dest = r.headers.get("Sec-Fetch-Dest");
        if (dest != null) return dest.equals("document");
        String accept = r.headers.get("Accept");
        return accept != null && accept.startsWith("text/html");
    }

    @Override
    public InspectProxy.Response onRequest(InspectProxy.Request r) {
        if (!restricted || !isYoutube(r.host)) return null;
        r.headers.set(YoutubeRestricted.RESTRICT_HEADER, YoutubeRestricted.RESTRICT_VALUE);
        if (!isDocument(r)) return null;
        String url = r.url();
        int kind = SitePolicy.youtubeKind(url);
        if (kind == SitePolicy.YT_HOME) return InspectProxy.Response.html(200, YoutubeRestricted.searchPageHtml());
        if (!SitePolicy.youtubeRestrictedAllows(url) && r.host.toLowerCase(java.util.Locale.ROOT).matches(".*youtube(-nocookie)?\\.com|youtu\\.be")) {
            return InspectProxy.Response.html(403, "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                    + "<body style='font-family:sans-serif;padding:24px'><h2>Not available</h2><p>This part of YouTube isn't available.</p></body></html>");
        }
        return null;
    }

    @Override
    public boolean wantsIdentity(InspectProxy.Request r) {
        return restricted && isYoutube(r.host) && isDocument(r);
    }

    @Override
    public boolean rewrites(InspectProxy.Request r, String contentType) {
        return restricted && isYoutube(r.host) && contentType.toLowerCase(java.util.Locale.ROOT).startsWith("text/html");
    }

    @Override
    public byte[] rewrite(InspectProxy.Request r, byte[] body) {
        String html = new String(body, StandardCharsets.UTF_8);
        String tag = "<style>" + YoutubeRestricted.HIDE_CSS + "</style>";
        int head = html.toLowerCase(java.util.Locale.ROOT).indexOf("<head");
        if (head >= 0) {
            int close = html.indexOf('>', head);
            if (close >= 0) html = html.substring(0, close + 1) + tag + html.substring(close + 1);
        } else {
            html = tag + html;
        }
        return html.getBytes(StandardCharsets.UTF_8);
    }
}
