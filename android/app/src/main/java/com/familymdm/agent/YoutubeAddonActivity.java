package com.familymdm.agent;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.familymdm.agent.sitepolicy.SitePolicy;
import com.familymdm.agent.sitepolicy.YoutubeRestricted;
import com.familymdm.agent.sitepolicy.YoutubeScripts;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The YouTube add-on: its own "YouTube" icon (the YoutubeAddon alias, turned on from the dashboard's
 * YouTube section), built into the MDM app so it works even when browsers are blocked. Only YouTube
 * opens here, with the same rules as the family Browser: the YouTube choice (Open / Restricted /
 * Only what I allow / Off), the allowed channels and videos (listed on YouTube's home page, and shown
 * unfiltered), "Ask for a channel or video", ad blocking, picture and video covering. When YouTube is
 * Off it shows the parent's own message.
 */
public class YoutubeAddonActivity extends Activity {
    private static final String HOME_YT = "https://m.youtube.com/";

    /** Where this screen starts and where "home" goes (YouTube Music overrides it). */
    protected String home() {
        return HOME_YT;
    }
    private static final String SCHEME = "mdmguard";

    private FrameLayout root;
    private WebView web;
    private View fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenDone;
    private volatile boolean trusted;

    // ---------- the dashboard's settings for this phone ----------

    private JSONObject policy() {
        try {
            return new JSONObject(Agent.prefs(this).getString("policy", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private String mode() {
        String m = policy().optString("youtubeMode", "channels");
        return "open".equals(m) || "restricted".equals(m) || "blocked".equals(m) ? m : "channels";
    }

    private List<SitePolicy.Site> sites() {
        return SitePolicy.parse(policy().optJSONArray("sites"));
    }

    private Set<String> allowedVideoIds() {
        Set<String> out = new HashSet<>();
        JSONArray a = policy().optJSONArray("sites");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject s = a.optJSONObject(i);
            if (s == null || SitePolicy.isExpired(s) || "youtube".equals(s.optString("type"))) continue;
            String u = s.optString("url", "");
            String h = SitePolicy.hostOf(u);
            if (h != null && (SitePolicy.isYoutubeHost(h) || "youtu.be".equals(h))) {
                String id = SitePolicy.youtubeVideoId(u);
                if (id != null) out.add(id);
            }
        }
        return out;
    }

    // ---------- screen ----------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        // Without "wv" YouTube treats this as a normal phone browser instead of asking for its app.
        String ua = web.getSettings().getUserAgentString();
        web.getSettings().setUserAgentString(ua.replace("; wv", "").replace(" Version/4.0 ", " "));
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                fullscreen = view;
                fullscreenDone = callback;
                root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                web.setVisibility(View.GONE);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }

            @Override
            public void onHideCustomView() {
                if (fullscreen != null) root.removeView(fullscreen);
                fullscreen = null;
                web.setVisibility(View.VISIBLE);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                if (fullscreenDone != null) fullscreenDone.onCustomViewHidden();
                fullscreenDone = null;
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        lastAllowed = home();
        open(home());
    }

    // Where "back" goes from a page that did not open: the last page that did (not the web view's history,
    // which fills with copies of the blocked page).
    private String lastAllowed = HOME_YT;
    private boolean showingBlock;
    private boolean clearHistoryNext;

    @Override
    public void onBackPressed() {
        if (fullscreen != null) {
            web.getWebChromeClient().onHideCustomView();
            return;
        }
        if (showingBlock) {
            if (!backToAllowed()) super.onBackPressed();
            return;
        }
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    /** Back from a page that did not open: the last page that did. False when there is none that still opens. */
    private boolean backToAllowed() {
        String target = lastAllowed == null ? home() : lastAllowed;
        if (decide(target) != null) target = home();
        if (decide(target) != null) return false; // even the home page does not open (YouTube is off): leave
        open(target);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Turned Off on the dashboard while it was in the background: show the message at once.
        if ("blocked".equals(mode())) show(offPage());
    }

    private void open(String url) {
        String page = decide(url);
        if (page != null) {
            show(page);
            return;
        }
        // Leaving a page that did not open: forget its copies in the history, so back never lands on one.
        if (showingBlock) clearHistoryNext = true;
        showingBlock = false;
        lastAllowed = url;
        web.stopLoading();
        web.loadUrl(url);
    }

    private void show(String html) {
        trusted = false;
        showingBlock = true;
        web.loadDataWithBaseURL("about:blank", html, "text/html", "utf-8", null);
    }

    // ---------- the rules ----------

    /** Null when `url` may open, otherwise the page to show instead. Also sets `trusted`. */
    private String decide(String url) {
        trusted = false;
        String host = SitePolicy.hostOf(url);
        if (host == null || url.startsWith("about:") || url.startsWith("data:") || url.startsWith("blob:")) return null;
        String h = host.toLowerCase(java.util.Locale.ROOT);
        boolean yt = SitePolicy.isYoutubeHost(h) || "youtu.be".equals(h);
        // Signing in to YouTube and its consent page are fine; nothing else opens here.
        // Signing in opens only when the parent allowed it; YouTube's consent page and Google's page parts are fine.
        if (SitePolicy.isYoutubeSignIn(url) && !policy().optBoolean("youtubeSignIn", false))
            return notAllowed(url, "Signing in to YouTube is turned off on this phone.", homeButton());
        if (!yt) {
            if (SitePolicy.isYoutubeSignIn(url) || h.equals("consent.youtube.com") || h.endsWith(".gstatic.com")) return null;
            return notAllowed(url, "Only YouTube opens in this app.", homeButton());
        }
        String m = mode();
        if ("blocked".equals(m)) return offPage();
        String video = SitePolicy.youtubeVideoId(url);
        if (video != null && allowedVideoIds().contains(video)) { trusted = true; return null; }
        List<SitePolicy.Site> list = sites();
        String handle = SitePolicy.youtubeChannelHandle(url);
        if (handle != null && SitePolicy.youtubeChannelAllowed(handle, list)) { trusted = true; return null; }
        if (video != null) {
            String known = YoutubeLookup.cached(this, video);
            if (known != null && !known.isEmpty() && SitePolicy.youtubeChannelAllowed(known, list)) { trusted = true; return null; }
            if (known == null && "channels".equals(m) && SitePolicy.hasYoutubeChannels(list)) {
                YoutubeLookup.start(this, video, () -> runOnUiThread(() -> { if (!isFinishing()) open(url); }));
                return page("Checking this video…", "Making sure it is from an allowed channel.", "");
            }
        }
        if ("open".equals(m)) return null;
        int kind = SitePolicy.youtubeKind(url);
        if (kind == SitePolicy.YT_HOME) return null; // its feed is emptied and the allowed ones are shown (scripts)
        if ("restricted".equals(m)) {
            if (SitePolicy.youtubeRestrictedAllows(url)) return null;
            return notAllowed(url, "This part of YouTube is not available.", homeButton());
        }
        // Searching, to find videos to ask for: the ones not allowed show covered, with "Ask for access".
        if (kind == SitePolicy.YT_SEARCH && policy().optBoolean("youtubeSearch", true)) return null;
        return notAllowed(url, "Only the YouTube channels and videos that were allowed open here.", askButton(url, "Ask for access") + homeButton());
    }

    private String offPage() {
        String msg = policy().optString("youtubeOffMessage", "");
        if (msg.isEmpty()) msg = "Sorry, YouTube is not available right now. Ask your administrator to open it.";
        return page("YouTube is off", esc(msg), askButton(home(), "Ask to open YouTube"));
    }

    private class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (url.startsWith(SCHEME + "://")) { action(Uri.parse(url)); return true; }
            if (!request.isForMainFrame()) return false;
            if (!url.startsWith("http")) return true; // no other apps from here (intent:, market:...)
            String page = decide(url);
            if (page != null) { show(page); return true; }
            showingBlock = false;
            lastAllowed = url;
            return false;
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            super.doUpdateVisitedHistory(view, url, isReload);
            if (url == null || !url.startsWith("http")) return;
            // YouTube moves between pages without loading them: check each one.
            String page = decide(url);
            if (page != null) { view.stopLoading(); show(page); }
            else {
                showingBlock = false;
                lastAllowed = url;
                view.evaluateJavascript("window.__mdmTrusted=" + trusted + ";", null);
            }
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            scripts(view, url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (clearHistoryNext && url != null && url.startsWith("http")) {
                clearHistoryNext = false;
                view.clearHistory(); // only the page now showing stays in the back list
            }
            // Back from a stale copy of a blocked page (restored from the history) goes to the last allowed page too.
            if ("about:blank".equals(url)) showingBlock = true;
            scripts(view, url);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            try {
                String url = request.getUrl().toString();
                JSONObject p = policy();
                if (p.optBoolean("blockAds", false) && YoutubeScripts.isAdRequest(url)) return empty(204);
                String guard = p.optString("pictureGuard", "off");
                // Search results shown with skin covered: YouTube's video pictures get the skin covering.
                if ("off".equals(guard) && "skin".equals(p.optString("youtubeSearchPictures", "hide")) && "channels".equals(mode())
                        && url.matches("^https://i[0-9]?\\.ytimg\\.com/vi(_webp)?/.*")) guard = "skin";
                if (!trusted && ("nudity".equals(guard) || "skin".equals(guard)) && isPicture(request) && !allowedThumb(url))
                    return filteredPicture(request, guard, p.optString("skinWho", "all"));
            } catch (Exception ignored) {
                // never break the page because of a problem here
            }
            return null;
        }
    }

    private void scripts(WebView view, String url) {
        if (url == null || !url.startsWith("http")) return;
        JSONObject p = policy();
        StringBuilder js = new StringBuilder("window.__mdmTrusted=" + trusted + ";");
        if (p.optBoolean("blockAds", false)) { String a = YoutubeScripts.adBlock(); if (a != null) js.append(a); }
        String m = mode();
        if ("restricted".equals(m) || "channels".equals(m)) {
            js.append(YoutubeRestricted.HIDE_SCRIPT);
            String shelf = shelf("channels".equals(m));
            if (shelf != null) js.append(shelf);
            String cover = coverScript(m);
            if (cover != null) js.append(cover);
        }
        if ("skin".equals(p.optString("pictureGuard", "off"))) { String v = YoutubeScripts.load("video_skin.js"); if (v != null) js.append(v); }
        if (Downloads.allowed(this)) { String d = YoutubeScripts.load("youtube_download.js"); if (d != null) js.append(d); }
        view.evaluateJavascript(js.toString(), null);
    }

    /** Covers the videos that may not be watched (every page in "Only what I allow"; suggestions in Restricted). */
    private String coverScript(String mode) {
        try {
            JSONArray ch = new JSONArray(), vids = new JSONArray();
            for (SitePolicy.Site s : sites()) if ("youtube".equals(s.type) && s.live()) ch.put(new JSONObject().put("h", s.id));
            for (String id : allowedVideoIds()) vids.put(new JSONObject().put("v", id));
            return YoutubeScripts.cover(new JSONObject().put("cover", mode).put("channels", ch).put("videos", vids)
                    .put("askSearch", "channels".equals(mode) && policy().optBoolean("youtubeSearch", true))
                    .put("askPictures", policy().optString("youtubeSearchPictures", "hide")).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /** "Your channels", "Your videos" and "Ask for a channel or video" on YouTube's home page. */
    private String shelf(boolean ask) {
        try {
            JSONArray channels = new JSONArray(), videos = new JSONArray();
            JSONArray a = policy().optJSONArray("sites");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject s = a.optJSONObject(i);
                if (s == null || SitePolicy.isExpired(s)) continue;
                String thumb = s.optString("thumb", "");
                if (!thumb.startsWith("https://")) thumb = "";
                if ("youtube".equals(s.optString("type"))) {
                    String handle = s.optString("id", "");
                    if (handle.matches("@[a-z0-9._-]{3,30}"))
                        channels.put(new JSONObject().put("h", handle).put("t", s.optString("label", handle).replaceFirst("^YouTube channel ", "")).put("i", thumb));
                } else {
                    String u = s.optString("url", ""), h = SitePolicy.hostOf(u);
                    String id = h != null && (SitePolicy.isYoutubeHost(h) || "youtu.be".equals(h)) ? SitePolicy.youtubeVideoId(u) : null;
                    if (id != null) {
                        String t = s.optString("label", "");
                        videos.put(new JSONObject().put("v", id).put("t", "YouTube video".equals(t) ? "" : t).put("i", thumb));
                    }
                }
            }
            JSONArray downloads = Downloads.forPages(this);
            if (channels.length() == 0 && videos.length() == 0 && !ask && downloads.length() == 0) return null;
            return YoutubeScripts.shelf(new JSONObject().put("channels", channels).put("videos", videos).put("ask", ask).put("downloads", downloads).toString());
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- "Ask for a channel or video" ----------

    private void action(Uri u) {
        String what = u.getHost() == null ? "" : u.getHost();
        if ("ask".equals(what)) {
            show(askPage(u.getQueryParameter("u")));
        } else if ("request".equals(what)) {
            String target = u.getQueryParameter("u");
            if (target != null && !target.isEmpty()) {
                Agent.addSiteRequest(this, target, u.getQueryParameter("note"));
                AgentService.requestSync();
            }
            show(page("Sent", "Your parent sees it on the dashboard. Once it is allowed, it opens here.", homeButton()));
        } else if ("download".equals(what)) {
            String why = Downloads.request(this, u.getQueryParameter("u"));
            Toast.makeText(this, why == null ? "Downloading…" : why, Toast.LENGTH_LONG).show();
        } else if ("play".equals(what)) {
            startActivity(new Intent(this, DownloadPlayerActivity.class).putExtra("id", u.getQueryParameter("id")));
        } else if ("home".equals(what)) {
            open(home());
        } else if ("back".equals(what)) {
            if (!backToAllowed()) finish();
        }
    }

    private static String askPage(String url) {
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

    private static String askButton(String url, String label) {
        return "<a class=b href='" + SCHEME + "://ask?u=" + Uri.encode(url) + "'>" + esc(label) + "</a>";
    }

    private static String homeButton() {
        return "<a class='b o' href='" + SCHEME + "://home'>Go to YouTube's home page</a>";
    }

    private static String notAllowed(String url, String reason, String buttons) {
        buttons = "<a class=b href='" + SCHEME + "://back'>Go back</a>" + buttons.replace("class=b href='" + SCHEME + "://ask", "class='b o' href='" + SCHEME + "://ask");
        return com.familymdm.agent.sitepolicy.BlockedPage.raw(com.familymdm.agent.sitepolicy.BlockedPage.titleFor(url),
                "<p>" + esc(com.familymdm.agent.sitepolicy.BlockedPage.CONTACT) + "</p>" + (reason != null ? "<p>" + esc(reason) + "</p>" : "")
                        + "<p style='font-size:13px'>" + esc(url) + "</p>", buttons);
    }

    private static String page(String title, String body, String buttons) {
        return com.familymdm.agent.sitepolicy.BlockedPage.raw(title, body == null || body.isEmpty() ? "" : "<p>" + body + "</p>", buttons);
    }

    private static String esc(String s) {
        return s == null ? "" : Html.escapeHtml(s);
    }

    // ---------- pictures: the same checks as the family Browser ----------

    private static boolean isPicture(WebResourceRequest r) {
        if (!"GET".equalsIgnoreCase(r.getMethod()) || r.isForMainFrame()) return false;
        for (Map.Entry<String, String> e : r.getRequestHeaders().entrySet())
            if ("accept".equalsIgnoreCase(e.getKey()) && e.getValue() != null && e.getValue().startsWith("image/")) return true;
        String path = r.getUrl().getPath() == null ? "" : r.getUrl().getPath().toLowerCase(java.util.Locale.ROOT);
        return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".webp");
    }

    /** Thumbnails of allowed videos show as they are. */
    private boolean allowedThumb(String url) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^https://i[0-9]?\\.ytimg\\.com/vi(?:_webp)?/([A-Za-z0-9_-]{11})/").matcher(url);
        return m.find() && allowedVideoIds().contains(m.group(1));
    }

    private WebResourceResponse filteredPicture(WebResourceRequest r, String guard, String who) {
        try {
            String url = r.getUrl().toString();
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(10000);
            con.setReadTimeout(15000);
            for (Map.Entry<String, String> e : r.getRequestHeaders().entrySet()) con.setRequestProperty(e.getKey(), e.getValue());
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) con.setRequestProperty("Cookie", cookie);
            if (con.getResponseCode() != 200) return null;
            String type = con.getContentType() == null ? "image/jpeg" : con.getContentType().split(";")[0].trim();
            byte[] raw;
            try (InputStream in = con.getInputStream()) {
                ByteArrayOutputStream all = new ByteArrayOutputStream();
                byte[] b = new byte[32768];
                for (int n; (n = in.read(b)) > 0; ) {
                    all.write(b, 0, n);
                    if (all.size() > 8 * 1024 * 1024) return empty(204);
                }
                raw = all.toByteArray();
            }
            if (type.contains("svg")) return new WebResourceResponse(type, null, 200, "OK", new HashMap<>(), new ByteArrayInputStream(raw));
            byte[] out = new ProxyPictureFilter(getApplicationContext(), guard, who).filter(raw);
            if (out == null) return new WebResourceResponse(type, null, 200, "OK", new HashMap<>(), new ByteArrayInputStream(raw));
            return new WebResourceResponse("image/png", null, 200, "OK", new HashMap<>(), new ByteArrayInputStream(out));
        } catch (Exception e) {
            return null;
        }
    }

    private static WebResourceResponse empty(int code) {
        return new WebResourceResponse("text/plain", "utf-8", code, code == 204 ? "No Content" : "Blocked", new HashMap<>(), new ByteArrayInputStream(new byte[0]));
    }
}
