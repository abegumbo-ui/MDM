package com.familymdm.agent;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.net.ProxyInfo;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.familymdm.agent.inspect.CertAuthority;
import com.familymdm.agent.inspect.InspectProxy;
import com.familymdm.agent.inspect.YoutubeInspector;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Optional browser inspection (off unless the dashboard turns it on for this phone).
 *
 * What it does, in order: this phone makes its own certificate authority (its key lives in the
 * Android Keystore and cannot be copied off the phone); the Agent, as device owner, installs that
 * CA's certificate so browsers trust it; a small proxy inside the Agent (InspectProxy) is started on
 * 127.0.0.1; and the phone's system-wide proxy setting is pointed at it. Browsers such as Chrome
 * honour that setting, so their secure connections pass through the proxy, which can then apply
 * page-level rules (today: restricted YouTube).
 *
 * Safety, in the order things happen:
 *  - Turning on: CA installed, proxy confirmed listening, and only then is the phone pointed at it.
 *    If any step fails, nothing is left pointing at a proxy that is not there.
 *  - Turning off (or emergency unlock, or release): the system proxy is cleared FIRST so browsing
 *    never goes to a dead address, then the proxy stops, then the CA certificate is removed.
 *  - Hosts on the bypass list (banks and the like) are never looked inside.
 *  - Apps that do not use the system proxy, or do not trust added certificates, are simply not
 *    affected: this only ever reaches browsers (and apps that opt in).
 */
final class TlsInspection {
    private TlsInspection() {}

    private static final String ALIAS = "mdm_inspect_ca";
    private static final int PREFERRED_PORT = 38417;
    // Sites that insist on their own certificate or hold the most sensitive sessions: never decrypted.
    private static final String[] DEFAULT_BYPASS = {
            "bankofamerica.com", "chase.com", "wellsfargo.com", "citi.com", "capitalone.com", "usbank.com",
            "paypal.com", "venmo.com", "zellepay.com", "americanexpress.com", "discover.com", "irs.gov", "ssa.gov"};

    private static InspectProxy proxy;
    private static int runningPort = -1;
    private static volatile YoutubeInspector rules = new YoutubeInspector(false, new HashSet<String>());
    private static volatile Context appContext; // for the category rule (BrowserCategoryFilter.blocksViaProxy)

    /** Forwards to whichever rules are current, so a policy change never needs a restart. */
    private static final InspectProxy.Inspector LIVE = new InspectProxy.Inspector() {
        public boolean bypass(String host, int port) { return rules.bypass(host, port); }
        public boolean bypassTunnel(String host, int port, InspectProxy.Headers connect) {
            return com.familymdm.agent.sitepolicy.ConnectivityProbe.isProbeTunnel(host, connect == null ? null : connect.get("User-Agent"));
        }
        public boolean refuses(String host) {
            Context c = appContext;
            // With an approved page or site on this phone a closed host is not refused here, before anything is seen: it is inspected and every
            // request to it is decided in onRequest (an approved page opens, its own files load, everything else is still refused).
            return c != null && BrowserCategoryFilter.blocksViaProxy(c, host) && !BrowserCategoryFilter.anyApprovedSite(c);
        }
        public InspectProxy.Response onRequest(InspectProxy.Request r) {
            // Android's internet check (a 204 from www.google.com and friends) goes straight through: no category, word or YouTube rule applies to it.
            if (com.familymdm.agent.sitepolicy.ConnectivityProbe.isProbe(r.host, r.path)) return null;
            if (ASK_HOST.equals(r.host)) return askAnswer(r);
            Context c = appContext;
            String why = c == null ? null : BrowserCategoryFilter.refusalViaProxy(c, r.host);
            String dest = r.headers.get("Sec-Fetch-Dest");
            String accept = r.headers.get("Accept");
            boolean page = dest != null ? dest.equals("document") : accept != null && accept.startsWith("text/html");
            if (why != null && c != null) {
                // The page that was approved opens, and so do the files it loads (its own style and script hosts); a different page does not.
                if (BrowserCategoryFilter.approvedUrl(c, r.url())) why = null;
                else if (!page && BrowserCategoryFilter.approvedReferer(c, r.headers.get("Referer"))) why = null;
            }
            if (why != null) {
                if (page) return InspectProxy.Response.html(403, com.familymdm.agent.sitepolicy.BlockedPage.notAllowed(r.url(), why, askLinks(r.url())));
                return new InspectProxy.Response(403, "text/plain", "Closed on this phone.".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return rules.onRequest(r);
        }
        public boolean rewrites(InspectProxy.Request r, String ct) { return rules.rewrites(r, ct); }
        public boolean wantsIdentity(InspectProxy.Request r) { return rules.wantsIdentity(r); }
        public byte[] rewrite(InspectProxy.Request r, byte[] body) { return rules.rewrite(r, body); }
    };

    /** A made-up web address the blocked page's buttons point at (https): the filter answers it itself, nothing leaves the phone. */
    private static final String ASK_HOST = "ask.mdm.invalid";

    /** "Ask for this site" under a blocked page: just this page, or the whole site (never offered for YouTube, which is asked for one channel or video at a time). */
    private static String askLinks(String url) {
        try {
            String host = com.familymdm.agent.sitepolicy.SitePolicy.hostOf(url);
            if (host == null || url == null || !url.startsWith("http") || com.familymdm.agent.sitepolicy.SitePolicy.isYoutubeHost(host)) return null;
            String enc = android.net.Uri.encode(url);
            // https, not http: the browser engine inside apps refuses plain http links (ERR_CLEARTEXT_NOT_PERMITTED, seen on the A16); the filter
            // inspects https and answers this made-up address itself, so it works the same in Chrome and in our Browser.
            return "<a class='b o' href='https://" + ASK_HOST + "/ask?s=page&u=" + enc + "'>Ask for just this page</a>"
                    + "<a class='b o' href='https://" + ASK_HOST + "/ask?s=site&u=" + enc + "'>Ask for the whole site (" + com.familymdm.agent.sitepolicy.BlockedPage.esc(host) + ")</a>";
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The page after a button on a blocked page was pressed: the request goes to the dashboard like any other. */
    private static InspectProxy.Response askAnswer(InspectProxy.Request r) {
        Context c = appContext;
        if (r.path.startsWith("/nudity")) return nudityAnswer(c, r);
        try {
            android.net.Uri u = android.net.Uri.parse("http://" + ASK_HOST + r.path);
            String target = u.getQueryParameter("u");
            String host = com.familymdm.agent.sitepolicy.SitePolicy.hostOf(target);
            if (c == null || host == null || !(target.startsWith("http://") || target.startsWith("https://")))
                return InspectProxy.Response.html(400, com.familymdm.agent.sitepolicy.BlockedPage.html("Not sent", new String[]{"That did not work. Go back and try again."}, null));
            String note = "Asked from a blocked page.";
            if ("site".equals(u.getQueryParameter("s"))) {
                target = "https://" + host + "/";
                note = "Wants the whole site.";
            }
            Agent.addSiteRequest(c, target, note);
            return InspectProxy.Response.html(200, com.familymdm.agent.sitepolicy.BlockedPage.html("Sent to your parent", new String[]{"They will see " + target + " on the dashboard."}, null));
        } catch (RuntimeException e) {
            return InspectProxy.Response.html(400, com.familymdm.agent.sitepolicy.BlockedPage.html("Not sent", new String[]{"That did not work. Go back and try again."}, null));
        }
    }

    /**
     * A video frame a page sent to be checked ("Filter nudity only" on a site's videos, video_nudity.js): a small picture as plain text (base64), checked
     * here with the same on-phone checker as pictures, and answered {"nude":true|false}. The frame goes nowhere else.
     */
    private static InspectProxy.Response nudityAnswer(Context c, InspectProxy.Request r) {
        if ("OPTIONS".equals(r.method)) return InspectProxy.Response.json(200, "{}");
        try {
            if (c == null || r.body.length == 0) return InspectProxy.Response.json(400, "{\"error\":\"no frame\"}");
            String text = new String(r.body, java.nio.charset.StandardCharsets.UTF_8).trim();
            int comma = text.indexOf(',');
            if (text.startsWith("data:") && comma > 0) text = text.substring(comma + 1);
            byte[] jpg = android.util.Base64.decode(text, android.util.Base64.DEFAULT);
            android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeByteArray(jpg, 0, jpg.length);
            if (b == null) return InspectProxy.Response.json(400, "{\"error\":\"not a picture\"}");
            float score = NudityCheck.score(c, b);
            b.recycle();
            if (score < 0) return InspectProxy.Response.json(200, "{\"nude\":false,\"checked\":false}"); // no checker in this build
            return InspectProxy.Response.json(200, "{\"nude\":" + (score >= com.familymdm.agent.sitepolicy.NudityPrep.THRESHOLD) + ",\"checked\":true}");
        } catch (RuntimeException e) {
            return InspectProxy.Response.json(500, "{\"error\":\"could not check\"}");
        }
    }

    /** Brings the phone in line with the policy. Never throws: a problem here must not stop other policy applying. */
    static synchronized void apply(Context c, DevicePolicyManager dpm, ComponentName admin, JSONObject policy) {
        try {
            boolean want = policy.optBoolean("tlsInspection", false);
            boolean applied = Agent.prefs(c).getBoolean("tlsInspectionApplied", false);
            if (want) {
                updateRules(c, policy);
                enable(c, dpm, admin, applied);
            } else if (applied || runningPort != -1) {
                disable(c, dpm, admin, "turned off");
            }
        } catch (Throwable t) {
            Agent.addEvent(c, "error", "Browser inspection problem: " + t.getMessage());
            try {
                disable(c, dpm, admin, "stopped after a problem");
            } catch (Throwable ignored) {
            }
        }
    }

    /** Emergency unlock / release: leave nothing behind. */
    static synchronized void release(Context c, DevicePolicyManager dpm, ComponentName admin) {
        try {
            if (Agent.prefs(c).getBoolean("tlsInspectionApplied", false) || runningPort != -1) disable(c, dpm, admin, "released");
        } catch (Throwable ignored) {
        }
    }

    /** "Only what I allow" on YouTube, for Chrome and the other browsers: the allowed channels and videos, and the scripts for its pages. */
    private static void youtubeChannels(JSONObject policy) {
        if (!"channels".equals(policy.optString("youtubeMode", "channels"))) {
            rules.setYoutubeChannels(null, null, null);
            return;
        }
        try {
            Set<String> handles = new HashSet<>(), videos = new HashSet<>(), songs = new HashSet<>();
            JSONArray ch = new JSONArray(), vids = new JSONArray(), songList = new JSONArray();
            JSONArray raw = policy.optJSONArray("sites");
            for (int i = 0; raw != null && i < raw.length(); i++) {
                JSONObject s = raw.optJSONObject(i);
                if (s == null || com.familymdm.agent.sitepolicy.SitePolicy.isExpired(s)) continue;
                String thumb = s.optString("thumb", "");
                if (!thumb.startsWith("https://")) thumb = "";
                if ("youtube".equals(s.optString("type"))) {
                    String h = s.optString("id", "").toLowerCase(Locale.ROOT);
                    if (!h.matches("@[a-z0-9._-]{3,30}")) continue;
                    handles.add(h);
                    ch.put(new JSONObject().put("h", h).put("t", s.optString("label", h).replaceFirst("^YouTube channel ", "")).put("i", thumb));
                } else {
                    String u = s.optString("url", ""), host = com.familymdm.agent.sitepolicy.SitePolicy.hostOf(u);
                    boolean yt = host != null && (com.familymdm.agent.sitepolicy.SitePolicy.isYoutubeHost(host) || "youtu.be".equals(host));
                    String id = yt ? com.familymdm.agent.sitepolicy.SitePolicy.youtubeVideoId(u) : null;
                    if (id == null) continue;
                    String t = s.optString("label", "");
                    JSONObject item = new JSONObject().put("v", id).put("t", "YouTube video".equals(t) ? "" : t).put("i", thumb);
                    // Songs (music.youtube.com) and videos are kept apart: each page lists and opens only its own.
                    if (com.familymdm.agent.sitepolicy.SitePolicy.isMusicHost(host)) { songs.add(id); songList.put(item); }
                    else { videos.add(id); vids.put(item); }
                }
            }
            String shelf = com.familymdm.agent.sitepolicy.YoutubeScripts.shelf(new JSONObject().put("channels", ch).put("videos", vids).put("ask", false).toString());
            String cover = com.familymdm.agent.sitepolicy.YoutubeScripts.cover(new JSONObject().put("channels", ch).put("videos", vids).put("cover", "channels").put("guard", true).toString());
            // YouTube Music's own pair: only the songs, no channels.
            String mShelf = com.familymdm.agent.sitepolicy.YoutubeScripts.shelf(new JSONObject().put("channels", new JSONArray()).put("videos", songList).put("ask", false).put("music", true).toString());
            String mCover = com.familymdm.agent.sitepolicy.YoutubeScripts.cover(new JSONObject().put("channels", new JSONArray()).put("videos", songList).put("cover", "channels").put("guard", true).toString());
            rules.setYoutubeChannels(handles, videos, songs,
                    (shelf != null ? "<script>" + shelf + "</script>" : "") + (cover != null ? "<script>" + cover + "</script>" : ""),
                    (mShelf != null ? "<script>" + mShelf + "</script>" : "") + (mCover != null ? "<script>" + mCover + "</script>" : ""));
        } catch (Exception e) {
            rules.setYoutubeChannels(java.util.Collections.<String>emptySet(), java.util.Collections.<String>emptySet(), "");
        }
    }

    private static Set<String> hostSet(JSONArray a) {
        Set<String> out = new HashSet<>();
        for (int i = 0; a != null && i < a.length(); i++) {
            String h = a.optString(i, "").trim().toLowerCase(Locale.ROOT);
            if (!h.isEmpty()) out.add(h.startsWith("www.") ? h.substring(4) : h);
        }
        return out;
    }

    private static void updateRules(Context c, JSONObject policy) {
        Set<String> bypass = new HashSet<>();
        for (String d : DEFAULT_BYPASS) bypass.add(d);
        JSONArray extra = policy.optJSONArray("inspectBypass");
        if (extra != null) {
            for (int i = 0; i < extra.length(); i++) {
                String h = extra.optString(i, "").trim().toLowerCase(Locale.ROOT);
                if (!h.isEmpty()) bypass.add(h);
            }
        }
        Set<String> imagesAllow = new HashSet<>();
        JSONArray ia = policy.optJSONArray("imagesAllow");
        if (ia != null) {
            for (int i = 0; i < ia.length(); i++) {
                String h = ia.optString(i, "").trim().toLowerCase(Locale.ROOT);
                if (!h.isEmpty()) imagesAllow.add(h.startsWith("www.") ? h.substring(4) : h);
            }
        }
        java.util.List<String> words = new java.util.ArrayList<>();
        JSONArray bw = policy.optJSONArray("blockedWords");
        for (int i = 0; bw != null && i < bw.length(); i++) {
            String w = com.familymdm.agent.sitepolicy.WordFilter.clean(bw.optString(i));
            if (w != null) words.add(w);
        }
        rules = new YoutubeInspector("restricted".equals(policy.optString("youtubeMode", "channels")), bypass,
                policy.optBoolean("blockImages", false), imagesAllow, words);
        rules.setBlockImageSearch(policy.optBoolean("blockImageSearch", false));
        rules.setMusicPictures(!"hide".equals(policy.optString("musicPictures", "allowed")));
        String restrictDns = policy.optString("youtubeDns", "off"); // YouTube's Restricted Mode, forced from the dashboard: also for YouTube Music
        rules.setRestrictLevel("strict".equals(restrictDns) ? "Strict" : "moderate".equals(restrictDns) ? "Moderate" : null);
        rules.setYoutubeOff("blocked".equals(policy.optString("youtubeMode", "")));
        youtubeChannels(policy);
        rules.setStrictSearch("strict".equals(policy.optString("browserFilter", "off")));
        rules.setYoutubeAdBlock(policy.optBoolean("blockAds", false) ? com.familymdm.agent.sitepolicy.YoutubeScripts.adBlock() : null);
        Set<String> noPics = new HashSet<>();
        JSONArray np = policy.optJSONArray("noPictureSites");
        for (int i = 0; np != null && i < np.length(); i++) {
            String h = np.optString(i, "").trim().toLowerCase(Locale.ROOT);
            if (!h.isEmpty()) noPics.add(h.startsWith("www.") ? h.substring(4) : h);
        }
        rules.setNoPictureSites(noPics);
        rules.setVideoRules(policy.optBoolean("blockVideos", false), hostSet(policy.optJSONArray("videoAllow")), hostSet(policy.optJSONArray("noVideoSites")));
        String guard = policy.optString("pictureGuard", "off");
        rules.setPictureFilter("nudity".equals(guard) || "skin".equals(guard) ? new ProxyPictureFilter(c.getApplicationContext(), guard, policy.optString("skinWho", "all")) : null);
        // "Cover skin" also covers skin in videos, in Chrome and the other browsers that go through Inspection.
        rules.setPageScript("skin".equals(guard) ? com.familymdm.agent.inspect.YoutubeInspector.videoSkinScript() : null);
        // Filter chosen on one site (pictures and videos each on their own): checked there even when no filter is on everywhere.
        Set<String> filterPics = hostSet(policy.optJSONArray("pictureFilterSites")), filterVids = hostSet(policy.optJSONArray("videoFilterSites"));
        // "Filter nudity and skin" on a site always covers skin too, whatever the general setting is (nudity only has its own choice below).
        rules.setSiteFilters(filterPics, filterVids,
                filterPics.isEmpty() ? null : new ProxyPictureFilter(c.getApplicationContext(), "skin", policy.optString("skinWho", "all")),
                filterVids.isEmpty() ? null : com.familymdm.agent.inspect.YoutubeInspector.videoSkinScript());
        // "Filter nudity only": only what may show nudity is covered (pictures by the on-phone checker, videos frame by frame through the same checker).
        Set<String> nudePics = hostSet(policy.optJSONArray("pictureNudityFilterSites")), nudeVids = hostSet(policy.optJSONArray("videoNudityFilterSites"));
        rules.setSiteNudityFilters(nudePics, nudeVids,
                nudePics.isEmpty() ? null : new ProxyPictureFilter(c.getApplicationContext(), "nudity", policy.optString("skinWho", "all")),
                nudeVids.isEmpty() ? null : com.familymdm.agent.inspect.YoutubeInspector.videoNudityScript());
    }

    private static void enable(Context c, DevicePolicyManager dpm, ComponentName admin, boolean applied) throws Exception {
        CertAuthority ca = authority(c);
        byte[] der = ca.caCertificate().getEncoded();
        if (!applied) {
            if (!dpm.installCaCert(admin, der)) throw new IllegalStateException("could not install the certificate");
        }
        boolean fresh = proxy == null;
        final Context app = c.getApplicationContext();
        appContext = app;
        // Forced SafeSearch / YouTube Restricted Mode for browsers that go through this proxy (same rule as the DNS filter).
        InspectProxy.setHostMap(host -> com.familymdm.agent.sitepolicy.SafeSearchDns.target(host,
                !Protection.paused(app) && Agent.prefs(app).getBoolean("safeSearch", false),
                Protection.paused(app) ? "off" : Agent.prefs(app).getString("youtubeDns", "off")));
        if (fresh) {
            InspectProxy p = new InspectProxy(ca, LIVE, InspectProxy.verifyingUpstream());
            int port;
            try {
                port = p.start(PREFERRED_PORT);
            } catch (java.io.IOException busy) {
                port = p.start(0);
            }
            proxy = p;
            runningPort = port;
        }
        // Only now that the proxy is really listening is the phone pointed at it.
        if (fresh || !applied) dpm.setRecommendedGlobalProxy(admin, ProxyInfo.buildDirectProxy("127.0.0.1", runningPort));
        if (!applied) {
            Agent.prefs(c).edit().putBoolean("tlsInspectionApplied", true).apply();
            Agent.addEvent(c, "restriction", "Browser inspection turned on (certificate installed; browsers use the Agent's filter)");
        }
    }

    private static void disable(Context c, DevicePolicyManager dpm, ComponentName admin, String why) throws Exception {
        // Order matters: stop pointing at the proxy first, so browsing never goes to a dead address.
        try {
            dpm.setRecommendedGlobalProxy(admin, null);
        } catch (Exception ignored) {
        }
        if (proxy != null) {
            proxy.close();
            proxy = null;
            runningPort = -1;
        }
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            String stored = Agent.prefs(c).getString("inspectCaDer", null);
            if (stored != null) dpm.uninstallCaCert(admin, Base64.decode(stored, Base64.NO_WRAP));
        } catch (Exception ignored) {
        }
        if (Agent.prefs(c).getBoolean("tlsInspectionApplied", false)) {
            Agent.prefs(c).edit().putBoolean("tlsInspectionApplied", false).apply();
            Agent.addEvent(c, "restriction", "Browser inspection " + why + " (certificate removed)");
        }
    }

    /** This phone's CA: created once, its private key held by the Keystore (it can sign but cannot be exported). */
    private static CertAuthority authority(Context c) throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        boolean newKey = !ks.containsAlias(ALIAS);
        if (newKey) {
            KeyPairGenerator g = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
            g.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build());
            g.generateKeyPair();
            Agent.prefs(c).edit().remove("inspectCaDer").apply(); // a new key needs a new certificate
        }
        final PrivateKey key = (PrivateKey) ks.getKey(ALIAS, null);
        PublicKey pub = ks.getCertificate(ALIAS).getPublicKey();
        CertAuthority.Signer signer = data -> {
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initSign(key);
            s.update(data);
            return s.sign();
        };
        String stored = Agent.prefs(c).getString("inspectCaDer", null);
        byte[] der;
        if (stored == null) {
            der = CertAuthority.newCaCertificate(signer, pub, "MDM Inspection CA (" + Build.MODEL + ")", System.currentTimeMillis());
            Agent.prefs(c).edit().putString("inspectCaDer", Base64.encodeToString(der, Base64.NO_WRAP)).apply();
        } else {
            der = Base64.decode(stored, Base64.NO_WRAP);
        }
        return new CertAuthority(signer, der);
    }
}
