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

    private final boolean blockImages;
    private final Set<String> imagesAllow;
    // A 1x1 see-through picture, sent in place of a blocked one so pages keep their shape without "broken image" marks.
    private static final byte[] BLANK_GIF = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 1, 0, 1, 0, (byte) 0x80, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            0x21, (byte) 0xf9, 4, 1, 0, 0, 0, 0, 0x2c, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x44, 1, 0, 0x3b};

    public YoutubeInspector(boolean restricted, Set<String> bypass) {
        this(restricted, bypass, false, java.util.Collections.<String>emptySet());
    }

    private final java.util.List<String> blockedWords;
    private volatile boolean blockImageSearch;

    /**
     * "Pictures in our Browser" carried over to every browser that goes through Inspection: each
     * picture is handed to this before the browser gets it. Returns the picture to show instead
     * (grey, or with skin covered), or null to leave it as it is.
     */
    public interface PictureFilter {
        byte[] filter(byte[] picture);
    }

    private volatile PictureFilter pictureFilter;
    private volatile String pageScript;
    private volatile boolean youtubeOff;
    private volatile String youtubeAdScript;
    private volatile boolean strictSearch;

    /** The Strict level: explicit searches get "you're better than that" (blocked words always do). */
    public void setStrictSearch(boolean on) {
        strictSearch = on;
    }

    /** "Block ads": YouTube pages get the ad remover, and ad-only requests are answered empty. */
    public void setYoutubeAdBlock(String js) {
        youtubeAdScript = js == null || js.isEmpty() ? null : js;
    }

    /** YouTube "Off": its pages do not open. */
    public void setYoutubeOff(boolean off) {
        youtubeOff = off;
    }

    /** A script added to every web page (the video skin covering); null for none. */
    public void setPageScript(String js) {
        pageScript = js == null || js.isEmpty() ? null : js;
    }

    /** The video skin covering, packed with this code (video_skin.js, same as the Browser's). */
    public static String videoSkinScript() {
        try (java.io.InputStream in = YoutubeInspector.class.getResourceAsStream("video_skin.js")) {
            if (in == null) return null;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
            return out.toString("UTF-8");
        } catch (java.io.IOException e) {
            return null;
        }
    }
    private volatile Set<String> noPictureSites = java.util.Collections.emptySet();

    /** "No pictures on these sites": a picture on (or from) one of them is replaced by a blank one. */
    public void setNoPictureSites(Set<String> sites) {
        noPictureSites = sites == null ? java.util.Collections.<String>emptySet() : sites;
    }

    private static boolean inList(Set<String> list, String host) {
        if (host == null) return false;
        String h = host.toLowerCase(java.util.Locale.ROOT);
        for (String a : list) if (h.equals(a) || h.endsWith("." + a)) return true;
        return false;
    }

    public void setPictureFilter(PictureFilter f) {
        pictureFilter = f;
    }

    private boolean filtersPicture(InspectProxy.Request r, String contentType) {
        if (pictureFilter == null || !r.method.equals("GET")) return false;
        String ct = contentType.toLowerCase(java.util.Locale.ROOT);
        return ct.startsWith("image/") && !ct.contains("svg");
    }

    /** Pictures are told apart from pages by their first bytes (JPEG, PNG, GIF, WebP, BMP, AVIF/HEIF). */
    static boolean looksLikePicture(byte[] b) {
        if (b == null || b.length < 12) return false;
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return true;
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return true;
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F') return true;
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return true;
        if (b[0] == 'B' && b[1] == 'M') return true;
        return b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
    }

    /** The "Images" tab of Google, Bing and DuckDuckGo is refused (see ProtectionRules.isImageSearch). */
    public void setBlockImageSearch(boolean on) {
        blockImageSearch = on;
    }

    /** blockImages: replace every picture on web pages with a blank, except on (or from) the sites in imagesAllow. */
    public YoutubeInspector(boolean restricted, Set<String> bypass, boolean blockImages, Set<String> imagesAllow) {
        this(restricted, bypass, blockImages, imagesAllow, java.util.Collections.<String>emptyList());
    }

    /** blockedWords: a page (or search) whose address contains one is refused. See WordFilter. */
    public YoutubeInspector(boolean restricted, Set<String> bypass, boolean blockImages, Set<String> imagesAllow, java.util.List<String> blockedWords) {
        this.blockedWords = blockedWords;
        this.restricted = restricted;
        this.bypass = bypass;
        this.blockImages = blockImages;
        this.imagesAllow = imagesAllow;
    }

    private boolean listed(String host) {
        if (host == null) return false;
        for (String a : imagesAllow) if (host.equals(a) || host.endsWith("." + a)) return true;
        return false;
    }

    private boolean imageBlocked(InspectProxy.Request r) {
        if (!r.method.equals("GET")) return false;
        boolean perSite = !noPictureSites.isEmpty() && (inList(noPictureSites, SitePolicy.hostOf(r.headers.get("Referer"))) || inList(noPictureSites, r.host));
        if (!blockImages && !perSite) return false;
        String dest = r.headers.get("Sec-Fetch-Dest");
        boolean image = dest != null ? dest.equals("image")
                : r.path.toLowerCase(java.util.Locale.ROOT).matches("[^?]*\\.(jpe?g|png|gif|webp|avif|bmp|svg|ico)(\\?.*)?");
        if (!image) return false;
        if (perSite) return true;
        // Allowed if the page showing it, or the picture's own site, is on the "show pictures" list.
        return !listed(SitePolicy.hostOf(r.headers.get("Referer"))) && !listed(SitePolicy.hostOf(r.host));
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
        if (imageBlocked(r)) return new InspectProxy.Response(200, "image/gif", BLANK_GIF);
        if (youtubeAdScript != null && com.familymdm.agent.sitepolicy.YoutubeScripts.isAdRequest(r.url())) return new InspectProxy.Response(200, "text/plain", new byte[0]);
        if (isDocument(r)) {
            String searched = com.familymdm.agent.sitepolicy.BadSearch.query(r.url());
            if (searched != null && com.familymdm.agent.sitepolicy.BadSearch.isBad(searched, strictSearch, blockedWords))
                return InspectProxy.Response.html(200, com.familymdm.agent.sitepolicy.BadSearch.page(null));
        }
        if (youtubeOff && isDocument(r) && isYoutube(r.host)) {
            return InspectProxy.Response.html(403, com.familymdm.agent.sitepolicy.BlockedPage.notAllowed(r.url(), "YouTube is turned off on this phone.", null));
        }
        if (blockImageSearch && isDocument(r) && com.familymdm.agent.sitepolicy.ProtectionRules.isImageSearch(r.url())) {
            return InspectProxy.Response.html(403, com.familymdm.agent.sitepolicy.BlockedPage.notAllowed(r.url(), "Image search is turned off on this phone.", null));
        }
        if (!blockedWords.isEmpty() && isDocument(r) && com.familymdm.agent.sitepolicy.WordFilter.match(r.url(), blockedWords) != null) {
            return InspectProxy.Response.html(403, com.familymdm.agent.sitepolicy.BlockedPage.notAllowed(r.url(), "This page has a blocked word in it.", null));
        }
        if (!restricted || !isYoutube(r.host)) return null;
        r.headers.set(YoutubeRestricted.RESTRICT_HEADER, YoutubeRestricted.RESTRICT_VALUE);
        if (!isDocument(r)) return null;
        String url = r.url();
        int kind = SitePolicy.youtubeKind(url);
        if (kind == SitePolicy.YT_HOME) return null; // YouTube's own home page; its feed is emptied by the script added to the page
        if (!SitePolicy.youtubeRestrictedAllows(url) && r.host.toLowerCase(java.util.Locale.ROOT).matches(".*youtube(-nocookie)?\\.com|youtu\\.be")) {
            return InspectProxy.Response.html(403, com.familymdm.agent.sitepolicy.BlockedPage.notAllowed(r.url(), "This part of YouTube isn't available.", null));
        }
        return null;
    }

    @Override
    public boolean wantsIdentity(InspectProxy.Request r) {
        return isDocument(r) && (((restricted || youtubeAdScript != null) && isYoutube(r.host)) || pageScript != null);
    }

    @Override
    public boolean rewrites(InspectProxy.Request r, String contentType) {
        if (filtersPicture(r, contentType)) return true;
        if (pageScript != null && contentType.toLowerCase(java.util.Locale.ROOT).startsWith("text/html")) return true;
        return (restricted || youtubeAdScript != null) && isYoutube(r.host) && contentType.toLowerCase(java.util.Locale.ROOT).startsWith("text/html");
    }

    /** Restricted: the suggested videos beside and after a video are covered (youtube_cover.js). */
    private static String restrictedCover() {
        String js = com.familymdm.agent.sitepolicy.YoutubeScripts.cover("{\"cover\":\"restricted\"}");
        return js == null ? "" : "<script>" + js + "</script>";
    }

    @Override
    public byte[] rewrite(InspectProxy.Request r, byte[] body) {
        PictureFilter pf = pictureFilter;
        if (pf != null && looksLikePicture(body)) {
            try {
                byte[] changed = pf.filter(body);
                return changed != null ? changed : body;
            } catch (RuntimeException e) {
                return body;
            }
        }
        boolean yt = restricted && isYoutube(r.host);
        String ads = youtubeAdScript != null && isYoutube(r.host) ? youtubeAdScript : null;
        String js = pageScript;
        if (!yt && js == null && ads == null) return body;
        String html = new String(body, StandardCharsets.UTF_8);
        String tag = (ads != null ? "<script>" + ads + "</script>" : "")
                + (yt ? "<style>" + YoutubeRestricted.HIDE_CSS + "</style><script>" + YoutubeRestricted.HIDE_SCRIPT + "</script>" + restrictedCover() : "") + (js != null ? "<script>" + js + "</script>" : "");
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
