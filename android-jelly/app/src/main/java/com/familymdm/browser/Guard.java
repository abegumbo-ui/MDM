package com.familymdm.browser;

import android.content.Context;
import android.content.Intent;
import android.content.RestrictionsManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import com.familymdm.agent.sitepolicy.ProtectionRules;
import com.familymdm.agent.sitepolicy.SitePolicy;
import com.familymdm.agent.sitepolicy.WordFilter;
import com.familymdm.agent.sitepolicy.YoutubeRestricted;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The family dashboard's rules for this browser, in one place. The browser (Jelly) sends every
 * address it opens, every link tapped and every file a page loads through here:
 *  - {@link #check}: may this page open? If not, the page to show instead.
 *  - {@link #intercept}: blocked-category files and the picture setting (PictureGuard).
 *  - {@link #searchUrl}: typed words become a Google search (SafeSearch unless switched off), when allowed.
 * Who decides: the MDM Agent on a managed phone (AGENT), or the dashboard this Browser was connected
 * to with a code (ONLINE). Not connected yet (UNCONFIGURED): nothing opens except the connect page.
 * Everything here is switchable on the dashboard.
 */
public final class Guard {
    private Guard() {}

    public enum Mode { AGENT, ONLINE, UNCONFIGURED }

    public static final String SCHEME = "mdmguard";
    private static final String AGENT_PACKAGE = "com.familymdm.agent";
    private static final String SITE_REQUEST_ACTION = "com.familymdm.agent.action.SITE_REQUEST";
    private static final long ONLINE_SYNC_MIN_MS = 30_000;

    private static volatile boolean syncing;
    private static volatile List<SitePolicy.Site> sitesCache = new ArrayList<>();
    private static volatile long sitesAt;
    private static final Set<String> reportedHosts = new HashSet<>();

    // ---------- who is in charge ----------

    public static Mode mode(Context c) {
        if (agentBundle(c) != null) return Mode.AGENT;
        if (BrowserState.isOnline(c)) return Mode.ONLINE;
        return Mode.UNCONFIGURED;
    }

    /** True whenever the dashboard's rules apply (always, except before connecting, when nothing opens at all). */
    public static boolean locked(Context c) {
        return true;
    }

    private static Bundle agentBundle(Context c) {
        try {
            RestrictionsManager rm = (RestrictionsManager) c.getSystemService(Context.RESTRICTIONS_SERVICE);
            Bundle b = rm == null ? null : rm.getApplicationRestrictions();
            return b != null && b.containsKey("sites") ? b : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- settings from the Agent or the dashboard ----------

    /** Call when the browser comes to the front. */
    public static void applyPolicy(Context ctx) {
        Context c = ctx.getApplicationContext();
        Mode m = mode(c);
        if (m == Mode.AGENT) {
            Bundle b = agentBundle(c);
            FilterStore.setPolicy(c, b.getString("filterTier", "off"), b.getString("filterBase", ""));
            FilterStore.setYoutubeMode(c, b.getString("youtubeMode", "channels"));
            FilterStore.setBlockImages(c, b.getBoolean("blockImages", false));
            FilterStore.setBlockedWords(c, b.getStringArray("blockedWords"));
            FilterStore.setBlockImageSearch(c, b.getBoolean("blockImageSearch", false));
            FilterStore.setPictureGuard(c, b.getString("pictureGuard", "off"));
            FilterStore.setSwitches(c, b.getBoolean("safeSearch", true), b.getBoolean("adultBlock", true), b.getBoolean("allowSearch", true));
        } else if (m == Mode.ONLINE) {
            sync(c, false);
        } else {
            FilterStore.setPolicy(c, "off", "");
            FilterStore.setPictureGuard(c, "off");
            FilterStore.setSwitches(c, true, true, false);
        }
        FilterStore.refreshAsync(c, false);
    }

    private static final Handler timer = new Handler(Looper.getMainLooper());
    private static Runnable tick;

    /** While the browser is open it checks with the dashboard every 30 seconds. */
    public static void resume(Context ctx) {
        final Context c = ctx.getApplicationContext();
        applyPolicy(c);
        pause();
        tick = new Runnable() {
            @Override
            public void run() {
                if (mode(c) == Mode.ONLINE) sync(c, true);
                timer.postDelayed(this, ONLINE_SYNC_MIN_MS);
            }
        };
        timer.postDelayed(tick, ONLINE_SYNC_MIN_MS);
    }

    public static void pause() {
        if (tick != null) timer.removeCallbacks(tick);
        tick = null;
    }

    /** Talks to the dashboard: reports what is happening, sends access requests, gets the settings. */
    public static void sync(final Context ctx, boolean force) {
        final Context c = ctx.getApplicationContext();
        if (syncing) return;
        if (!force && System.currentTimeMillis() - BrowserState.lastOnlineSync(c) < ONLINE_SYNC_MIN_MS) return;
        final String server = BrowserState.server(c);
        final String token = BrowserState.token(c);
        if (server == null || token == null) return;
        syncing = true;
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                JSONObject info = new JSONObject();
                info.put("manufacturer", Build.MANUFACTURER);
                info.put("model", Build.MODEL);
                info.put("filter", FilterStore.status(c));
                info.put("picture", FilterStore.pictureGuard(c));
                info.put("app", "jelly");
                body.put("info", info);
                JSONArray requests = BrowserState.peekSiteRequests(c);
                body.put("siteRequests", requests);
                JSONObject reply = Api.post(server + "/browser/sync", body, token);
                BrowserState.dropSiteRequests(c, requests.length());
                JSONArray sites = reply.optJSONArray("sites");
                BrowserState.setCachedSites(c, sites != null ? sites : new JSONArray());
                sitesAt = 0;
                JSONObject f = reply.optJSONObject("filter");
                FilterStore.setPolicy(c, f == null ? "off" : f.optString("tier", "off"), f == null ? "" : f.optString("base", ""));
                FilterStore.setYoutubeMode(c, f == null ? "channels" : f.optString("youtube", "channels"));
                FilterStore.setBlockImages(c, f != null && f.optBoolean("blockImages", false));
                JSONArray fw = f == null ? null : f.optJSONArray("words");
                String[] words = new String[fw == null ? 0 : fw.length()];
                for (int i = 0; i < words.length; i++) words[i] = fw.optString(i);
                FilterStore.setBlockedWords(c, words);
                FilterStore.setBlockImageSearch(c, f != null && f.optBoolean("imageSearch", false));
                FilterStore.setPictureGuard(c, f == null ? "off" : f.optString("pictureGuard", "off"));
                FilterStore.setSwitches(c, f == null || f.optBoolean("safeSearch", true), f == null || f.optBoolean("adultBlock", true), f == null || f.optBoolean("allowSearch", true));
                FilterStore.refreshAsync(c, false);
            } catch (Exception ignored) {
                // offline or the dashboard unreachable: keep the last settings
            } finally {
                syncing = false;
            }
        }).start();
    }

    /** For the tests: read the site list again right away. */
    static void forgetCache() {
        sitesAt = 0;
    }

    private static List<SitePolicy.Site> sites(Context c) {
        long now = System.currentTimeMillis();
        if (now - sitesAt < 3000) return sitesCache;
        List<SitePolicy.Site> s = new ArrayList<>();
        try {
            Mode m = mode(c);
            if (m == Mode.AGENT) {
                String json = agentBundle(c).getString("sites");
                if (json != null) s = SitePolicy.parse(new JSONArray(json));
            } else if (m == Mode.ONLINE) {
                s = SitePolicy.parse(BrowserState.cachedSites(c));
            }
        } catch (Exception ignored) {
        }
        sitesCache = s;
        sitesAt = now;
        return s;
    }

    private static boolean browseWindow(Context c) {
        Bundle b = agentBundle(c);
        return b != null && b.getLong("browseUntil", 0) > System.currentTimeMillis();
    }

    // ---------- addresses ----------

    public static final class Decision {
        public final boolean allow;
        public final String html;
        public final boolean blockImages;

        Decision(boolean allow, String html, boolean blockImages) {
            this.allow = allow;
            this.html = html;
            this.blockImages = blockImages;
        }

        static Decision yes(boolean blockImages) { return new Decision(true, null, blockImages); }

        static Decision no(String html) { return new Decision(false, html, false); }
    }

    /** May `url` open? `recheck` is run later when the answer was not known yet (a YouTube video's channel). */
    public static Decision check(Context ctx, String url, Runnable recheck) {
        Context c = ctx.getApplicationContext();
        boolean noPics = FilterStore.blockImages(c);
        if (url == null || url.startsWith("about:") || url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("javascript:"))
            return Decision.yes(noPics);
        if (FilterStore.adultBlock(c) && SitePolicy.isBlockedAdult(url))
            return Decision.no(page("Not available", "This kind of site isn't available in this browser.", homeButton()));
        Mode mode = mode(c);
        if (mode == Mode.UNCONFIGURED) return Decision.no(connectPage(null));
        if (WordFilter.match(url, FilterStore.blockedWords(c)) != null)
            return Decision.no(page("Not available", "This page or search has a blocked word in it.", homeButton()));
        if (FilterStore.blockImageSearch(c) && ProtectionRules.isImageSearch(url))
            return Decision.no(page("Not available", "Image search is turned off.", homeButton()));
        if (FilterStore.youtubeRestricted(c) && isYoutubeAddress(url)) {
            int kind = SitePolicy.youtubeKind(url);
            if (kind == SitePolicy.YT_HOME) return new Decision(false, YoutubeRestricted.searchPageHtml(), noPics);
            if (SitePolicy.youtubeRestrictedAllows(url)) return Decision.yes(noPics);
            return Decision.no(page("Not available", "This part of YouTube isn't available.", homeButton()));
        }
        List<SitePolicy.Site> list = sites(c);
        SitePolicy.Site match = SitePolicy.matching(url, list);
        if (match != null) return Decision.yes(match.blockImages || noPics);
        // Allowed YouTube channels: their pages, and videos YouTube says belong to them.
        String host = SitePolicy.hostOf(url);
        if (SitePolicy.isYoutubeHost(host) && SitePolicy.hasYoutubeChannels(list)) {
            String handle = SitePolicy.youtubeChannelHandle(url);
            if (handle != null && SitePolicy.youtubeChannelAllowed(handle, list)) return Decision.yes(noPics);
            String video = SitePolicy.youtubeVideoId(url);
            if (video != null) {
                String known = YoutubeLookup.cached(c, video);
                if (known == null) {
                    YoutubeLookup.start(c, video, recheck != null ? recheck : () -> { });
                    return Decision.no(page("Checking this video…", "Making sure it is from an allowed channel.", ""));
                }
                if (SitePolicy.youtubeChannelAllowed(known, list)) return Decision.yes(noPics);
            }
        }
        if (mode == Mode.AGENT && browseWindow(c)) {
            reportOnce(c, url);
            return Decision.yes(noPics);
        }
        if (FilterStore.open(c)) return Decision.yes(noPics);
        if (FilterStore.active(c) && host != null && !FilterStore.blocks(c, host)) return Decision.yes(noPics);
        String note = "";
        if (FilterStore.waitingForLists(c)) {
            FilterStore.refreshAsync(c, true);
            note = "<p class=n>The filter lists are still downloading. Until they are ready, only approved sites open. Try again in a minute.</p>";
        }
        return Decision.no(page("This page isn't allowed", esc(url) + note,
                "<a class=b href='" + SCHEME + "://request?u=" + Uri.encode(url) + "'>Ask for access</a>" + homeButton()));
    }

    private static boolean isYoutubeAddress(String url) {
        String host = SitePolicy.hostOf(url);
        return SitePolicy.isYoutubeHost(host) || "consent.youtube.com".equals(host) || "accounts.youtube.com".equals(host);
    }

    private static void reportOnce(Context c, String url) {
        String host = SitePolicy.hostOf(url);
        synchronized (reportedHosts) {
            if (host == null || !reportedHosts.add(host)) return;
        }
        try {
            c.sendBroadcast(new Intent(SITE_REQUEST_ACTION).setPackage(AGENT_PACKAGE).putExtra("url", "https://" + host + "/"),
                    "com.familymdm.agent.permission.BROWSER");
        } catch (Exception ignored) {
        }
    }

    /** Words typed instead of an address: a Google search, or null when searching is not allowed here. */
    public static String searchUrl(Context ctx, String text) {
        Context c = ctx.getApplicationContext();
        Mode m = mode(c);
        boolean ok = (m == Mode.AGENT && browseWindow(c))
                || (m != Mode.UNCONFIGURED && FilterStore.allowSearch(c) && !"off".equals(FilterStore.tier(c)));
        if (!ok) return null;
        return safeUrl(c, "https://www.google.com/search?q=" + Uri.encode(text.trim()));
    }

    public static String noSearchPage() {
        return page("Searching is turned off", "Type a full web address, like example.com.", homeButton());
    }

    private static final java.util.regex.Pattern GOOGLE_HOST = java.util.regex.Pattern.compile("^(www\\.)?google\\.[a-z.]+$");

    /** SafeSearch on every Google page (unless switched off on the dashboard). */
    public static String safeUrl(Context ctx, String url) {
        try {
            if (!FilterStore.safeSearch(ctx.getApplicationContext())) return url;
            Uri u = Uri.parse(url);
            String host = u.getHost();
            if (host == null || !GOOGLE_HOST.matcher(host.toLowerCase(java.util.Locale.ROOT)).matches()) return url;
            if ("active".equals(u.getQueryParameter("safe"))) return url;
            Uri.Builder b = u.buildUpon().clearQuery();
            for (String name : u.getQueryParameterNames()) {
                if (name.equals("safe")) continue;
                for (String v : u.getQueryParameters(name)) b.appendQueryParameter(name, v);
            }
            b.appendQueryParameter("safe", "active");
            return b.build().toString();
        } catch (Exception e) {
            return url;
        }
    }

    // ---------- everything a page loads ----------

    public static WebResourceResponse intercept(Context ctx, WebResourceRequest request) {
        Context c = ctx.getApplicationContext();
        try {
            String url = request.getUrl().toString();
            String host = SitePolicy.hostOf(url);
            boolean filterBlocks = FilterStore.active(c) && host != null && FilterStore.blocks(c, host)
                    && SitePolicy.matching(url, sites(c)) == null && !(mode(c) == Mode.AGENT && browseWindow(c));
            if (filterBlocks) return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked", new HashMap<>(), new ByteArrayInputStream(new byte[0]));
            String guard = FilterStore.pictureGuard(c);
            if (!"off".equals(guard) && !FilterStore.blockImages(c) && PictureGuard.isPicture(request))
                return PictureGuard.handle(c, request, guard);
        } catch (Exception ignored) {
            // never break a page because of a problem here
        }
        return null;
    }

    /** Script to run when a page has loaded (restricted YouTube hides its feeds), or null. */
    public static String afterLoadScript(Context ctx, String url) {
        Context c = ctx.getApplicationContext();
        return url != null && FilterStore.youtubeRestricted(c) && isYoutubeAddress(url) ? YoutubeRestricted.HIDE_SCRIPT : null;
    }

    /** No installing apps from downloads. */
    public static boolean downloadAllowed(String url, String mimeType) {
        String u = url == null ? "" : url.toLowerCase(java.util.Locale.ROOT);
        return !"application/vnd.android.package-archive".equals(mimeType) && !u.matches("[^?#]*\\.(apk|apks|xapk)([?#].*)?");
    }

    // ---------- our own pages and their buttons ----------

    public static void showPage(WebView v, String url, String html) {
        // Shown as "about:blank", never as the blocked address, so the browser does not take it for that page.
        v.loadDataWithBaseURL("about:blank", html, "text/html", "utf-8", null);
    }

    /** Buttons on our pages (mdmguard://...): ask for access, connect with a code, start page. True if handled. */
    public static boolean handleAction(final Context ctx, final WebView v, String url) {
        if (url == null || !url.startsWith(SCHEME + "://")) return false;
        final Context c = ctx.getApplicationContext();
        Uri u = Uri.parse(url);
        String what = u.getHost() == null ? "" : u.getHost();
        switch (what) {
            case "request": {
                String target = u.getQueryParameter("u");
                if (!TextUtils.isEmpty(target)) {
                    if (mode(c) == Mode.AGENT) {
                        try {
                            c.sendBroadcast(new Intent(SITE_REQUEST_ACTION).setPackage(AGENT_PACKAGE).putExtra("url", target), "com.familymdm.agent.permission.BROWSER");
                        } catch (Exception ignored) {
                        }
                    } else {
                        BrowserState.addSiteRequest(c, target);
                        sync(c, true);
                    }
                }
                showPage(v, null, page("Sent", "Your parent sees the request on the dashboard. Once it is allowed, the page opens.", homeButton()));
                return true;
            }
            case "connect": {
                final String code = u.getQueryParameter("code") == null ? "" : u.getQueryParameter("code").trim();
                final String server = BrowserApp.defaultServer(c);
                if (code.isEmpty() || server.isEmpty()) {
                    showPage(v, null, connectPage(server.isEmpty() ? "This copy has no dashboard address built in." : "Type the code first."));
                    return true;
                }
                showPage(v, null, page("Connecting…", "", ""));
                new Thread(() -> {
                    String err = null;
                    try {
                        JSONObject info = new JSONObject();
                        info.put("manufacturer", Build.MANUFACTURER);
                        info.put("model", Build.MODEL);
                        JSONObject body = new JSONObject();
                        body.put("code", code);
                        body.put("info", info);
                        JSONObject reply = Api.post(server.replaceAll("/+$", "") + "/browser/enroll", body, null);
                        BrowserState.setOnline(c, server.replaceAll("/+$", ""), reply.getString("token"));
                        sync(c, true);
                    } catch (Exception e) {
                        err = e.getMessage();
                    }
                    final String error = err;
                    v.post(() -> showPage(v, null, error == null
                            ? page("Connected", "This Browser now follows the family dashboard.", homeButton())
                            : connectPage("Could not connect: " + error)));
                }).start();
                return true;
            }
            case "start":
                showPage(v, null, startPage(c));
                return true;
            default:
                return true;
        }
    }

    /** The start page: the allowed sites, as buttons. */
    public static String startPage(Context ctx) {
        Context c = ctx.getApplicationContext();
        if (mode(c) == Mode.UNCONFIGURED) return connectPage(null);
        StringBuilder b = new StringBuilder();
        for (SitePolicy.Site s : sites(c)) b.append("<a class=b href='").append(esc(s.url)).append("'>").append(esc(s.label)).append("</a>");
        String intro = "off".equals(FilterStore.tier(c)) ? (b.length() == 0 ? "No sites are allowed yet." : "Allowed sites:") : "Search or type an address above.";
        return page("Browser", intro, b.toString());
    }

    /** True when `url` is the browser's home page and it would not open: show the start page instead. */
    public static boolean useStartPage(Context ctx, String url, String homePage) {
        return url != null && url.equals(homePage) && !check(ctx, url, null).allow;
    }

    public static String connectPage(String error) {
        return page("Connect this Browser", (error != null ? "<p class=n>" + esc(error) + "</p>" : "")
                        + "On the family dashboard: Add device → Browser. Type the code it shows.",
                "<form onsubmit=\"location.href='" + SCHEME + "://connect?code='+encodeURIComponent(this.c.value);return false\">"
                        + "<input name=c autocapitalize=characters autocomplete=off placeholder='Code' style='font-size:20px;padding:12px;width:100%;box-sizing:border-box;border:1px solid #999;border-radius:12px'>"
                        + "<button class=b style='border:0;width:100%'>Connect</button></form>");
    }

    private static String homeButton() {
        return "<a class=b href='" + SCHEME + "://start'>Go to the start page</a>";
    }

    static String page(String title, String body, String buttons) {
        return "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:sans-serif;padding:32px 20px;max-width:520px;margin:auto;color:#202124;background:#fff}"
                + "@media(prefers-color-scheme:dark){body{background:#121212;color:#e8eaed}}h2{margin:0 0 8px}p{color:#5f6368;word-break:break-all}"
                + ".n{color:#b06000}.b{display:block;text-align:center;margin-top:12px;padding:14px;border-radius:24px;background:#1a73e8;color:#fff;text-decoration:none;font-size:16px}"
                + "</style></head><body><h2>" + esc(title) + "</h2><p>" + body + "</p>" + buttons + "</body></html>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;").replace("\"", "&quot;");
    }
}
