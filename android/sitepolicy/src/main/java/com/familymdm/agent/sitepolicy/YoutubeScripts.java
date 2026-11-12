package com.familymdm.agent.sitepolicy;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;

/**
 * The scripts our YouTube pages get (one copy, used by the family Browser and by the YouTube add-on
 * in the MDM app), kept as files next to this code (com/familymdm/agent/inspect/*.js):
 *  - youtube_adblock.js: "Block ads" on YouTube;
 *  - youtube_shelf.js: "Your channels", "Your videos" and "Ask for a channel or video" on YouTube's home page;
 *  - video_skin.js: "Cover skin" on videos.
 */
public final class YoutubeScripts {
    private YoutubeScripts() {}

    public static String load(String name) {
        try (InputStream in = YoutubeScripts.class.getResourceAsStream("/com/familymdm/agent/inspect/" + name)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private static volatile String adblock;

    public static String adBlock() {
        if (adblock == null) adblock = load("youtube_adblock.js");
        return adblock;
    }

    /** The home-page list with its data filled in ({"channels":[...],"videos":[...],"ask":true}). */
    public static String shelf(String dataJson) {
        String js = load("youtube_shelf.js");
        return js == null ? null : js.replace("__MDM_DATA__", dataJson.replace("</", "<\\/"));
    }

    /** Requests that only serve ads (Google's ad servers, YouTube's ad and ad-tracking addresses). */
    public static boolean isAdRequest(String url) {
        if (url == null) return false;
        String host = SitePolicy.hostOf(url);
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("doubleclick.net") || h.endsWith(".doubleclick.net") || h.endsWith("googlesyndication.com")
                || h.endsWith("googleadservices.com") || h.equals("googleads.g.doubleclick.net") || h.endsWith("adservice.google.com")) return true;
        if (!SitePolicy.isYoutubeHost(h)) return false;
        String u = url.toLowerCase(Locale.ROOT);
        int p = u.indexOf(h) + h.length();
        String path = p < u.length() ? u.substring(p) : "";
        return path.startsWith("/pagead/") || path.startsWith("/api/stats/ads") || path.startsWith("/ptracking")
                || path.startsWith("/get_midroll_info");
    }
}
