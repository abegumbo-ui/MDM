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
            FilterStore.setNoPictureSites(c, b.getStringArray("noPictureSites"));
            FilterStore.setSkinWho(c, b.getString("skinWho", "all"));
            FilterStore.setBlockAds(c, b.getBoolean("blockAds", false));
            FilterStore.setCoveredFromDashboard(c, b.getStringArray("adsCoveredSites"));
            FilterStore.setAdsAutoCover(c, b.getBoolean("adsAutoCover", true));
            FilterStore.setYoutubeSearch(c, b.getBoolean("youtubeSearch", true));
            FilterStore.setYoutubeSearchPictures(c, b.getString("youtubeSearchPictures", "hide"));
            FilterStore.setYoutubeSignIn(c, b.getBoolean("youtubeSignIn", false));
            FilterStore.setDownloads(c, b.getBoolean("downloads", true));
            FilterStore.setYoutubeOffMessage(c, b.getString("youtubeOffMessage", ""));
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
        startSiteTick(c);
    }

    public static void pause() {
        if (tick != null) timer.removeCallbacks(tick);
        tick = null;
        if (siteTick != null) timer.removeCallbacks(siteTick);
        siteTick = null;
    }

    // ---------- a daily time allowance per site ("YouTube 2 hours a day"; the Agent passes screenTime.siteLimits in the bundle) ----------

    private static volatile WebView pageView;
    private static Runnable siteTick;

    /** The page that is on screen (set while the browser is in front), so its time can be counted and a "Time is up" page shown in it. */
    public static void setPageView(WebView v) {
        pageView = v;
    }

    /** host -> minutes a day, or null when this Browser has no limits (always, outside the MDM app). */
    private static JSONObject siteLimits(Context c) {
        Bundle b = agentBundle(c);
        if (b == null) return null;
        try {
            String s = b.getString("siteLimits", "");
            if (s == null || s.isEmpty()) return null;
            JSONObject o = new JSONObject(s);
            return o.length() == 0 ? null : o;
        } catch (Exception e) {
            return null;
        }
    }

    /** Until when the limits are lifted (extra time from the administrator, or every restriction off). */
    private static long siteFreeUntil(Context c) {
        Bundle b = agentBundle(c);
        return b == null ? 0 : b.getLong("siteFreeUntil", 0);
    }

    /**
     * Tells the Agent something (a page to ask for, more time). Inside the MDM app (the Browser add-on) the Agent is this very app: an explicit
     * broadcast to its own receiver, which needs no permission (the permission-protected one below was not being delivered there). From the
     * separate Browser app it is the broadcast that only something signed with the family key can send.
     */
    private static void sendToAgent(Context c, Intent i) {
        try {
            if (AGENT_PACKAGE.equals(c.getPackageName())) c.sendBroadcast(i.setClassName(AGENT_PACKAGE, "com.familymdm.agent.SiteRequestReceiver"));
            else c.sendBroadcast(i.setAction(SITE_REQUEST_ACTION).setPackage(AGENT_PACKAGE), "com.familymdm.agent.permission.BROWSER");
        } catch (Exception ignored) {
        }
    }

    static String timeUpPage(JSONObject limits, String host) {
        return page("Time is up for today", esc(SiteTime.message(limits, host)), "<a class=b href='" + SCHEME + "://moretime'>Ask for more time</a>" + homeButton());
    }

    /** The "Time is up" answer when this address belongs to a site whose allowance for today is used up; null otherwise. */
    private static Decision timeCheck(Context c, String url) {
        JSONObject limits = siteLimits(c);
        if (limits == null) return null;
        String host = SitePolicy.hostOf(url);
        if (host == null || !SiteTime.exhausted(c, limits, siteFreeUntil(c), host)) return null;
        return Decision.no(timeUpPage(limits, host));
    }

    /** Every 5 seconds the browser is in front: that time counts for the site on screen; when it is used up the page is replaced. */
    private static void startSiteTick(final Context c) {
        siteTick = new Runnable() {
            @Override
            public void run() {
                try {
                    WebView v = pageView;
                    JSONObject limits = siteLimits(c);
                    if (v != null && limits != null) {
                        String u = v.getUrl();
                        String host = u != null && u.startsWith("http") ? SitePolicy.hostOf(u) : null;
                        if (host != null) {
                            SiteTime.add(c, limits, host, 5000);
                            if (SiteTime.exhausted(c, limits, siteFreeUntil(c), host)) showPage(v, null, timeUpPage(limits, host));
                        }
                    }
                } catch (RuntimeException ignored) {
                    // a counting problem must never break the page
                }
                if (siteTick == this) timer.postDelayed(this, 5000);
            }
        };
        timer.postDelayed(siteTick, 1500);
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
                info.put("adsCovered", new JSONArray(FilterStore.coveredHere(c))); // set on this phone (menu, or noticed)
                body.put("info", info);
                JSONArray visited = takeVisited();
                body.put("visited", visited);
                JSONArray requests = BrowserState.peekSiteRequests(c);
                body.put("siteRequests", requests);
                body.put("endedEarly", AllowList.endedForServer(c)); // the dashboard ends them too
                JSONObject reply = Api.post(server + "/browser/sync", body, token);
                BrowserState.dropSiteRequests(c, requests.length());
                visitedSent = true;
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
                JSONArray np = f == null ? null : f.optJSONArray("noPictureSites");
                String[] npSites = new String[np == null ? 0 : np.length()];
                for (int i = 0; i < npSites.length; i++) npSites[i] = np.optString(i);
                FilterStore.setNoPictureSites(c, npSites);
                FilterStore.setSkinWho(c, f == null ? "all" : f.optString("skinWho", "all"));
                FilterStore.setBlockAds(c, f != null && f.optBoolean("blockAds", false));
                JSONArray ac = f == null ? null : f.optJSONArray("adsCoveredSites");
                String[] acSites = new String[ac == null ? 0 : ac.length()];
                for (int i = 0; i < acSites.length; i++) acSites[i] = ac.optString(i);
                FilterStore.setCoveredFromDashboard(c, acSites);
                FilterStore.setAdsAutoCover(c, f == null || f.optBoolean("adsAutoCover", true));
                FilterStore.setYoutubeSearch(c, f == null || f.optBoolean("youtubeSearch", true));
                FilterStore.setYoutubeSearchPictures(c, f == null ? "hide" : f.optString("youtubeSearchPictures", "hide"));
                FilterStore.setYoutubeSignIn(c, f != null && f.optBoolean("youtubeSignIn", false));
                FilterStore.setYoutubeOffMessage(c, f == null ? "" : f.optString("youtubeOffMessage", ""));
                FilterStore.refreshAsync(c, false);
            } catch (Exception ignored) {
                // offline or the dashboard unreachable: keep the last settings
            } finally {
                if (!visitedSent) putBackVisited();
                visitedSent = false;
                syncing = false;
            }
        }).start();
    }

    // ---------- sites opened, for the dashboard's "Sites opened" list ----------
    private static final List<JSONObject> visited = new ArrayList<>();
    private static List<JSONObject> inFlight = new ArrayList<>();
    private static volatile boolean visitedSent;
    private static String lastVisitedHost;

    static synchronized void noteVisit(String url, boolean blocked) {
        String host = SitePolicy.hostOf(url);
        if (host == null || host.equals(lastVisitedHost)) return;
        lastVisitedHost = host;
        try {
            visited.add(new JSONObject().put("host", host).put("at", System.currentTimeMillis()).put("blocked", blocked));
            if (visited.size() > 300) visited.remove(0);
        } catch (Exception ignored) {
        }
    }

    private static synchronized JSONArray takeVisited() {
        inFlight = new ArrayList<>(visited);
        visited.clear();
        return new JSONArray(inFlight);
    }

    private static synchronized void putBackVisited() {
        visited.addAll(0, inFlight);
        inFlight = new ArrayList<>();
    }

    /** For the tests: read the site list again right away. */
    public static void forgetCache() {
        sitesAt = 0;
    }

    private static List<SitePolicy.Site> sites(Context c) {
        long now = System.currentTimeMillis();
        if (now - sitesAt < 3000) return sitesCache;
        List<SitePolicy.Site> s = new ArrayList<>();
        try {
            Mode m = mode(c);
            if (m == Mode.AGENT || m == Mode.ONLINE) s = SitePolicy.parse(rawSites(c));
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
        /** An allowed YouTube channel or video: shown as it is, no picture or video covering. */
        public final boolean trusted;

        Decision(boolean allow, String html, boolean blockImages) {
            this(allow, html, blockImages, false);
        }

        Decision(boolean allow, String html, boolean blockImages, boolean trusted) {
            this.allow = allow;
            this.html = html;
            this.blockImages = blockImages;
            this.trusted = trusted;
        }

        static Decision trustedYes() { return new Decision(true, null, false, true); }

        static Decision yes(boolean blockImages) { return new Decision(true, null, blockImages); }

        static Decision no(String html) { return new Decision(false, html, false); }
    }

    /** May `url` open? `recheck` is run later when the answer was not known yet (a YouTube video's channel). */
    public static Decision check(Context ctx, String url, Runnable recheck) {
        Decision d = decide(ctx, url, recheck);
        if (url != null && url.startsWith("http")) noteVisit(url, !d.allow);
        return d;
    }

    private static Decision decide(Context ctx, String url, Runnable recheck) {
        Context c = ctx.getApplicationContext();
        boolean noPics = FilterStore.blockImages(c) || FilterStore.noPicturesFor(c, SitePolicy.hostOf(url));
        if (url == null || url.startsWith("about:") || url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("javascript:"))
            return Decision.yes(noPics);
        if (FilterStore.adultBlock(c) && SitePolicy.isBlockedAdult(url))
            return Decision.no(notAllowed(url, null, homeButton()));
        Mode mode = mode(c);
        if (mode == Mode.UNCONFIGURED) return Decision.no(connectPage(null));
        if (mode == Mode.AGENT) {
            Decision timeUp = timeCheck(c, url); // the day's allowance for this site is used up
            if (timeUp != null) return timeUp;
        }
        // A bad search: "Oh come on, you're better than that." (Strict: explicit words; any level: blocked words)
        String searched = com.familymdm.agent.sitepolicy.BadSearch.query(url);
        if (searched != null && com.familymdm.agent.sitepolicy.BadSearch.isBad(searched, "strict".equals(FilterStore.tier(c)), FilterStore.blockedWords(c)))
            return Decision.no(com.familymdm.agent.sitepolicy.BadSearch.page(backButton() + homeButton()));
        if (WordFilter.match(url, FilterStore.blockedWords(c)) != null)
            return Decision.no(notAllowed(url, "It has a blocked word in it.", homeButton()));
        if (FilterStore.blockImageSearch(c) && ProtectionRules.isImageSearch(url))
            return Decision.no(notAllowed(url, "Image search is turned off.", homeButton()));
        // Signing in (YouTube's Sign in button, Google's sign-in page): only when the parent allowed it.
        if (SitePolicy.isYoutubeSignIn(url)) {
            if (FilterStore.youtubeSignIn(c)) return Decision.yes(noPics);
            return Decision.no(notAllowed(url, "Signing in to YouTube is turned off.", homeButton()));
        }
        String ytMode = FilterStore.youtubeMode(c);
        if ("blocked".equals(ytMode) && SitePolicy.isYoutubeHost(SitePolicy.hostOf(url)))
            return Decision.no(page("YouTube is off", esc(FilterStore.youtubeOffMessage(c)), askButton("https://www.youtube.com/", "Ask to open YouTube") + homeButton()));
        // A single allowed video opens in every mode, whatever extras its link carries (start time, share code).
        String videoId = SitePolicy.isYoutubeHost(SitePolicy.hostOf(url)) || "youtu.be".equals(SitePolicy.hostOf(url)) ? SitePolicy.youtubeVideoId(url) : null;
        if (videoId != null && allowedVideoIds(c).contains(videoId)) return Decision.trustedYes();
        // A video from an allowed channel (already known): shown as it is too.
        if (videoId != null) {
            String known = YoutubeLookup.cached(c, videoId);
            if (known != null && !known.isEmpty() && SitePolicy.youtubeChannelAllowed(known, sites(c))) return Decision.trustedYes();
        }
        if (FilterStore.youtubeRestricted(c) && isYoutubeAddress(url)) {
            int kind = SitePolicy.youtubeKind(url);
            if (kind == SitePolicy.YT_HOME) return Decision.yes(noPics); // YouTube's own home page, its feed emptied (afterLoadScript)
            if (SitePolicy.youtubeRestrictedAllows(url)) return Decision.yes(noPics);
            return Decision.no(notAllowed(url, "This part of YouTube is not available.", homeButton()));
        }
        List<SitePolicy.Site> list = sites(c);
        SitePolicy.Site match = SitePolicy.matching(url, list);
        if (match != null) return Decision.yes(match.blockImages || noPics);
        // Allowed YouTube channels: their pages, and videos YouTube says belong to them.
        String host = SitePolicy.hostOf(url);
        if (SitePolicy.isYoutubeHost(host) && SitePolicy.hasYoutubeChannels(list)) {
            String handle = SitePolicy.youtubeChannelHandle(url);
            if (handle != null && SitePolicy.youtubeChannelAllowed(handle, list)) return Decision.trustedYes();
            String video = SitePolicy.youtubeVideoId(url);
            if (video != null) {
                String known = YoutubeLookup.cached(c, video);
                if (known == null) {
                    YoutubeLookup.start(c, video, recheck != null ? recheck : () -> { });
                    return Decision.no(page("Checking this video…", "Making sure it is from an allowed channel.", ""));
                }
                if (SitePolicy.youtubeChannelAllowed(known, list)) return Decision.trustedYes();
            }
        }
        // "Only what I allow": YouTube's home page opens and shows the allowed channels and videos
        // (afterLoadScript); the rest of YouTube is blocked whatever the web level is.
        if ("channels".equals(ytMode) && SitePolicy.isYoutubeHost(host) && SitePolicy.youtubeKind(url) == SitePolicy.YT_HOME)
            return Decision.yes(noPics);
        // Searching YouTube to find videos to ask for (they show covered, with "Ask for access").
        if ("channels".equals(ytMode) && SitePolicy.isYoutubeHost(host) && SitePolicy.youtubeKind(url) == SitePolicy.YT_SEARCH && FilterStore.youtubeSearch(c))
            return Decision.yes(noPics);
        if ("channels".equals(ytMode) && SitePolicy.isYoutubeHost(host))
            return Decision.no(notAllowed(url, "Only the YouTube channels and videos that were allowed open here.",
                    askButton(url, "Ask for access") + homeButton()));
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
        return Decision.no(notAllowed(url, null, note + askButton(url, "Ask for access") + homeButton()));
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
            sendToAgent(c, new Intent().putExtra("url", "https://" + host + "/"));
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

    /** For an allowed YouTube channel or video: only the blocked-category check, no picture covering. */
    public static WebResourceResponse interceptFilterOnly(Context ctx, WebResourceRequest request) {
        Context c = ctx.getApplicationContext();
        try {
            String url = request.getUrl().toString();
            String host = SitePolicy.hostOf(url);
            if (FilterStore.active(c) && host != null && FilterStore.blocks(c, host) && SitePolicy.matching(url, sites(c)) == null)
                return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked", new HashMap<>(), new ByteArrayInputStream(new byte[0]));
            if (FilterStore.blockAds(c) && com.familymdm.agent.sitepolicy.YoutubeScripts.isAdRequest(url)) return noContent();
        } catch (Exception ignored) {
        }
        return null;
    }

    private static WebResourceResponse noContent() {
        return new WebResourceResponse("text/plain", "utf-8", 204, "No Content", new HashMap<>(), new ByteArrayInputStream(new byte[0]));
    }

    /** "Block ads" on YouTube: the script that takes the ads out (run before YouTube's own), or null. */
    public static String adBlockScript(Context ctx) {
        Context c = ctx.getApplicationContext();
        return FilterStore.blockAds(c) && !FilterStore.adsCovered(c, "youtube.com") ? com.familymdm.agent.sitepolicy.YoutubeScripts.adBlock() : null;
    }

    /** For a page: ads covered (a site that refuses ad blocking), or the watcher that notices such a site; or null. */
    public static String adsPageScript(Context ctx, String pageHost) {
        Context c = ctx.getApplicationContext();
        if (!FilterStore.blockAds(c) || pageHost == null) return null;
        if (FilterStore.adsCovered(c, pageHost)) return com.familymdm.agent.sitepolicy.YoutubeScripts.load("ads_cover.js");
        return FilterStore.adsAutoCover(c) ? com.familymdm.agent.sitepolicy.YoutubeScripts.load("adblock_detect.js") : null;
    }

    /** The page said it will not open with ads blocked: its ads load from now on, covered. True to reload. */
    public static boolean noticedAdBlockWall(Context ctx, String reportedHost, String pageHost) {
        Context c = ctx.getApplicationContext();
        if (pageHost == null || reportedHost == null || !FilterStore.blockAds(c) || !FilterStore.adsAutoCover(c)) return false;
        if (!pageHost.equalsIgnoreCase(reportedHost) || FilterStore.adsCovered(c, pageHost)) return false;
        FilterStore.setCoveredHere(c, pageHost, true);
        sync(c, true);
        return true;
    }

    /** The ⋮ menu: "Show ads on this page (covered)" / "Block ads on this page", or null when ads are not blocked. */
    public static String adsMenuLabel(Context ctx, String pageHost) {
        Context c = ctx.getApplicationContext();
        if (!FilterStore.blockAds(c) || pageHost == null) return null;
        return FilterStore.adsCovered(c, pageHost) ? "Block ads on this page" : "Show ads on this page (covered)";
    }

    public static void toggleAdsHere(Context ctx, String pageHost) {
        Context c = ctx.getApplicationContext();
        if (pageHost == null) return;
        FilterStore.setCoveredHere(c, pageHost, !FilterStore.adsCovered(c, pageHost));
        sync(c, true);
    }

    public static WebResourceResponse intercept(Context ctx, WebResourceRequest request) {
        return intercept(ctx, request, null);
    }

    /** pageHost: the site the page is on (its ads may be allowed, covered). */
    public static WebResourceResponse intercept(Context ctx, WebResourceRequest request, String pageHost) {
        Context c = ctx.getApplicationContext();
        try {
            String url = request.getUrl().toString();
            String host = SitePolicy.hostOf(url);
            boolean covered = FilterStore.adsCovered(c, pageHost);
            boolean filterBlocks = FilterStore.active(c) && host != null && (covered ? FilterStore.blocksExceptAds(c, host) : FilterStore.blocks(c, host))
                    && SitePolicy.matching(url, sites(c)) == null && !(mode(c) == Mode.AGENT && browseWindow(c));
            if (filterBlocks) return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked", new HashMap<>(), new ByteArrayInputStream(new byte[0]));
            if (!covered && FilterStore.blockAds(c) && com.familymdm.agent.sitepolicy.YoutubeScripts.isAdRequest(url)) return noContent();
            String guard = FilterStore.pictureGuard(c);
            // Search results shown with skin covered: YouTube's video pictures get the skin covering.
            if ("off".equals(guard) && "skin".equals(FilterStore.youtubeSearchPictures(c)) && "channels".equals(FilterStore.youtubeMode(c))
                    && url.matches("^https://i[0-9]?\\.ytimg\\.com/vi(_webp)?/.*")) guard = "skin";
            if (!"off".equals(guard) && !FilterStore.blockImages(c) && PictureGuard.isPicture(request) && !isAllowedVideoThumb(c, url))
                return PictureGuard.handle(c, request, guard);
        } catch (Exception ignored) {
            // never break a page because of a problem here
        }
        return null;
    }

    /**
     * Script for a YouTube page (restricted or "only what I allow"): its feeds hidden, and on its home
     * page the allowed channels and videos shown in their place. Null for other pages.
     */
    public static String afterLoadScript(Context ctx, String url) {
        Context c = ctx.getApplicationContext();
        String m = FilterStore.youtubeMode(c);
        if (url == null || !isYoutubeAddress(url) || !("restricted".equals(m) || "channels".equals(m))) return null;
        String shelf = shelfScript(c);
        String cover = coverScript(c, m);
        return YoutubeRestricted.HIDE_SCRIPT + (shelf != null ? shelf : "") + (cover != null ? cover : "");
    }

    /** Covers the videos that may not be watched (every page in "Only what I allow"; suggestions in Restricted). */
    static String coverScript(Context c, String mode) {
        try {
            JSONArray ch = new JSONArray(), vids = new JSONArray();
            for (SitePolicy.Site s : sites(c)) if ("youtube".equals(s.type) && s.live()) ch.put(new JSONObject().put("h", s.id));
            for (String id : allowedVideoIds(c)) vids.put(new JSONObject().put("v", id));
            return com.familymdm.agent.sitepolicy.YoutubeScripts.cover(new JSONObject().put("cover", mode).put("channels", ch).put("videos", vids)
                    .put("askSearch", "channels".equals(mode) && FilterStore.youtubeSearch(c) && mode(c) != Mode.UNCONFIGURED)
                    .put("askPictures", FilterStore.youtubeSearchPictures(c)).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /** The allowed list exactly as the dashboard sent it (with pictures, titles and end times). */
    public static JSONArray allSites(Context ctx) {
        Context c = ctx.getApplicationContext();
        JSONArray a;
        try {
            if (mode(c) == Mode.AGENT) {
                String json = agentBundle(c).getString("sites");
                a = json == null ? new JSONArray() : new JSONArray(json);
            } else {
                a = BrowserState.cachedSites(c);
            }
        } catch (Exception e) {
            a = new JSONArray();
        }
        if (a == null) a = new JSONArray();
        AllowList.remember(c, a);
        return a;
    }

    /** What is allowed right now: without what was allowed for a while and is over, or was ended early. */
    private static JSONArray rawSites(Context c) {
        JSONArray all = allSites(c), out = new JSONArray();
        JSONObject ended = AllowList.endedMap(c);
        for (int i = 0; i < all.length(); i++) {
            JSONObject s = all.optJSONObject(i);
            if (s == null || SitePolicy.isExpired(s) || AllowList.endedEarly(ended, s)) continue;
            out.put(s);
        }
        return out;
    }

    /** A YouTube thumbnail (i.ytimg.com/vi/ID/...) of an allowed video: shown as it is. */
    private static boolean isAllowedVideoThumb(Context c, String url) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^https://i[0-9]?\\.ytimg\\.com/vi(?:_webp)?/([A-Za-z0-9_-]{11})/").matcher(url);
        return m.find() && allowedVideoIds(c).contains(m.group(1));
    }

    /** Ids of the single YouTube videos on the allowed list. */
    static Set<String> allowedVideoIds(Context c) {
        Set<String> out = new HashSet<>();
        JSONArray a = rawSites(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject s = a.optJSONObject(i);
            if (s == null || SitePolicy.isExpired(s) || "youtube".equals(s.optString("type"))) continue;
            String u = s.optString("url", "");
            String h = SitePolicy.hostOf(u);
            if (h == null || !(SitePolicy.isYoutubeHost(h) || "youtu.be".equals(h))) continue;
            String id = SitePolicy.youtubeVideoId(u);
            if (id != null) out.add(id);
        }
        return out;
    }

    /** What the MDM app has downloaded (its files/downloads/downloads.json), for YouTube's home page. */
    private static JSONArray downloadedForPages(Context c) {
        JSONArray out = new JSONArray();
        if (mode(c) != Mode.AGENT) return out;
        try {
            java.io.File dir = new java.io.File(c.getFilesDir(), "downloads");
            java.io.File index = new java.io.File(dir, "downloads.json");
            if (!index.exists()) return out;
            JSONArray a = new JSONArray(new String(java.nio.file.Files.readAllBytes(index.toPath()), java.nio.charset.StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || !new java.io.File(dir, o.optString("file")).exists()) continue;
                out.put(new JSONObject().put("v", o.optString("id")).put("t", o.optString("t")).put("i", o.optString("i")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** The "Download" button script for YouTube video pages, or null (downloads off, or not inside the MDM app). */
    public static String downloadScript(Context ctx) {
        Context c = ctx.getApplicationContext();
        if (mode(c) != Mode.AGENT || !FilterStore.downloads(c)) return null;
        return com.familymdm.agent.sitepolicy.YoutubeScripts.load("youtube_download.js");
    }

    private static volatile String shelfJs;

    /** "Your channels" and "Your videos" on YouTube's home page (assets/youtube_shelf.js), or null if none. */
    static String shelfScript(Context c) {
        try {
            JSONArray channels = new JSONArray(), videos = new JSONArray();
            JSONArray a = rawSites(c);
            for (int i = 0; i < a.length(); i++) {
                JSONObject s = a.optJSONObject(i);
                if (s == null || SitePolicy.isExpired(s)) continue;
                String thumb = s.optString("thumb", "");
                if (!thumb.startsWith("https://")) thumb = "";
                if ("youtube".equals(s.optString("type"))) {
                    String handle = s.optString("id", "");
                    if (!handle.matches("@[a-z0-9._-]{3,30}")) continue;
                    channels.put(new JSONObject().put("h", handle).put("t", s.optString("label", handle).replaceFirst("^YouTube channel ", "")).put("i", thumb));
                } else {
                    String h = SitePolicy.hostOf(s.optString("url", ""));
                    if (h == null || !(SitePolicy.isYoutubeHost(h) || "youtu.be".equals(h))) continue;
                    String id = SitePolicy.youtubeVideoId(s.optString("url", ""));
                    if (id == null) continue;
                    String t = s.optString("label", "");
                    videos.put(new JSONObject().put("v", id).put("t", "YouTube video".equals(t) || t.equals(h) ? "" : t).put("i", thumb));
                }
            }
            boolean ask = "channels".equals(FilterStore.youtubeMode(c)) && mode(c) != Mode.UNCONFIGURED;
            JSONArray downloads = downloadedForPages(c);
            if (channels.length() == 0 && videos.length() == 0 && !ask && downloads.length() == 0) return null;
            return com.familymdm.agent.sitepolicy.YoutubeScripts.shelf(new JSONObject().put("channels", channels).put("videos", videos).put("ask", ask).put("downloads", downloads).toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static volatile String videoSkinJs;

    /** "Cover skin" also covers skin in videos (assets/video_skin.js), or null when that setting is off. */
    public static String videoScript(Context ctx) {
        Context c = ctx.getApplicationContext();
        if (!"skin".equals(FilterStore.pictureGuard(c))) return null;
        if (videoSkinJs == null) {
            try (java.io.InputStream in = c.getAssets().open("video_skin.js")) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] b = new byte[8192];
                for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
                videoSkinJs = out.toString("UTF-8");
            } catch (Exception e) {
                return null;
            }
        }
        return videoSkinJs;
    }

    /** No installing apps from downloads. */
    public static boolean downloadAllowed(String url, String mimeType) {
        String u = url == null ? "" : url.toLowerCase(java.util.Locale.ROOT);
        return !"application/vnd.android.package-archive".equals(mimeType) && !u.matches("[^?#]*\\.(apk|apks|xapk)([?#].*)?");
    }

    // ---------- our own pages and their buttons ----------

    /** Asks the parent for an address (with an optional note): through the MDM app, or straight to the dashboard. */
    public static void sendRequest(Context ctx, String target, String note) {
        Context c = ctx.getApplicationContext();
        if (mode(c) == Mode.AGENT) {
            try {
                sendToAgent(c, new Intent().putExtra("url", target).putExtra("note", note == null ? "" : note));
            } catch (Exception ignored) {
            }
        } else {
            BrowserState.addSiteRequest(c, target, note);
            sync(c, true);
        }
    }

    public static void showPage(WebView v, String url, String html) {
        // The address bar shows the address that was tried, not "about:blank".
        if (v instanceof PageHost) ((PageHost) v).showingPageFor(url != null && url.startsWith("http") ? url : null);
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
            case "download": {
                // Inside the MDM app: its downloader does the work (yt-dlp). The page stays as it is.
                String target = u.getQueryParameter("u");
                if (!TextUtils.isEmpty(target)) {
                    try {
                        c.sendBroadcast(new Intent().setClassName(c.getPackageName(), "com.familymdm.agent.DownloadReceiver").putExtra("url", target));
                    } catch (Exception ignored) {
                    }
                }
                return true;
            }
            case "play":
                try {
                    c.startActivity(new Intent().setClassName(c.getPackageName(), "com.familymdm.agent.DownloadPlayerActivity")
                            .putExtra("id", u.getQueryParameter("id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {
                }
                return true;
            case "ask":
                showPage(v, null, askPage(u.getQueryParameter("u")));
                return true;
            case "moretime":
                // "Ask for more time" on the Time is up page: the Agent turns it into a "More screen time" request on the dashboard.
                sendToAgent(c, new Intent().putExtra("kind", "time"));
                showPage(v, null, page("Sent", "Your parent was asked for more time.", homeButton()));
                return true;
            case "request": {
                String target = u.getQueryParameter("u");
                String note = u.getQueryParameter("note");
                if (!TextUtils.isEmpty(target)) sendRequest(c, target, note);
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
            case "back":
                // Back to the last page that did open (the history fills with copies of the blocked page).
                if (v instanceof PageHost && ((PageHost) v).lastAllowedPage() != null) v.loadUrl(((PageHost) v).lastAllowedPage());
                else if (v.canGoBack()) v.goBack();
                else showPage(v, null, startPage(c));
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

    private static String askButton(String url, String label) {
        return "<a class=b href='" + SCHEME + "://ask?u=" + Uri.encode(url) + "'>" + esc(label) + "</a>";
    }

    /**
     * "Ask your parent": which page (already known, or typed: a link or just a name, which becomes a
     * YouTube search the parent can run from the dashboard) and a message.
     */
    static String askPage(String url) {
        boolean known = url != null && !url.isEmpty();
        String field = known
                ? "<p>" + esc(url) + "</p><input type=hidden name=w value='x'>"
                : "<input name=w autocomplete=off placeholder='A link, or the name of a channel or video' style='font-size:17px;padding:12px;width:100%;box-sizing:border-box;border:1px solid #999;border-radius:12px'>";
        return page(known ? "Ask your parent" : "Ask for a channel or video", known ? "Your parent gets this on the dashboard." : "Paste a link, or write what you are looking for.",
                "<form onsubmit=\"var v=this.w.value.trim();if(!v)return false;var u=this.u.value||(/youtu\\.?be|youtube\\.com|^https?:/i.test(v)?(v.indexOf('://')<0?'https://'+v:v):'https://www.youtube.com/results?search_query='+encodeURIComponent(v));"
                        + "location.href='" + SCHEME + "://request?u='+encodeURIComponent(u)+'&note='+encodeURIComponent(this.n.value.trim());return false\">"
                        + "<input type=hidden name=u value='" + (known ? esc(url) : "") + "'>" + field
                        + "<textarea name=n rows=3 placeholder='Message to your parent (optional)' style='margin-top:12px;font-size:16px;padding:12px;width:100%;box-sizing:border-box;border:1px solid #999;border-radius:12px'></textarea>"
                        + "<button class=b style='border:0;width:100%'>Send</button></form>" + homeButton());
    }

    private static String homeButton() {
        return "<a class='b o' href='" + SCHEME + "://start'>Go to the start page</a>";
    }

    private static String backButton() {
        return "<a class=b href='" + SCHEME + "://back'>Go back</a>";
    }

    /** The family's "This site is not allowed" page (logo behind it), with the reason and the address. */
    static String notAllowed(String url, String reason, String extraHtml) {
        extraHtml = backButton() + (extraHtml == null ? "" : extraHtml.replace("class=b href='" + SCHEME + "://ask", "class='b o' href='" + SCHEME + "://ask"));
        String body = "<p>" + esc(com.familymdm.agent.sitepolicy.BlockedPage.CONTACT) + "</p>"
                + (reason != null ? "<p>" + esc(reason) + "</p>" : "")
                + (url != null && url.startsWith("http") ? "<p style='font-size:13px'>" + esc(url) + "</p>" : "");
        return com.familymdm.agent.sitepolicy.BlockedPage.raw(com.familymdm.agent.sitepolicy.BlockedPage.titleFor(url), body, extraHtml);
    }

    static String page(String title, String body, String buttons) {
        return com.familymdm.agent.sitepolicy.BlockedPage.raw(title, body == null || body.isEmpty() ? "" : "<p>" + body + "</p>", buttons);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;").replace("\"", "&quot;");
    }
}
