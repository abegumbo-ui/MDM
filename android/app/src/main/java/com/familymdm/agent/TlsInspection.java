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
        public boolean refuses(String host) {
            Context c = appContext;
            return c != null && BrowserCategoryFilter.blocksViaProxy(c, host);
        }
        public InspectProxy.Response onRequest(InspectProxy.Request r) {
            Context c = appContext;
            if (c != null && BrowserCategoryFilter.blocksViaProxy(c, r.host)) {
                String dest = r.headers.get("Sec-Fetch-Dest");
                String accept = r.headers.get("Accept");
                boolean page = dest != null ? dest.equals("document") : accept != null && accept.startsWith("text/html");
                if (page) return InspectProxy.Response.html(403, com.familymdm.agent.sitepolicy.BlockedPage.notAllowed(r.url(), "This kind of site is closed on this phone.", null));
                return new InspectProxy.Response(403, "text/plain", "Closed on this phone.".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return rules.onRequest(r);
        }
        public boolean rewrites(InspectProxy.Request r, String ct) { return rules.rewrites(r, ct); }
        public boolean wantsIdentity(InspectProxy.Request r) { return rules.wantsIdentity(r); }
        public byte[] rewrite(InspectProxy.Request r, byte[] body) { return rules.rewrite(r, body); }
    };

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
            Set<String> handles = new HashSet<>(), videos = new HashSet<>();
            JSONArray ch = new JSONArray(), vids = new JSONArray();
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
                    videos.add(id);
                    String t = s.optString("label", "");
                    vids.put(new JSONObject().put("v", id).put("t", "YouTube video".equals(t) ? "" : t).put("i", thumb));
                }
            }
            String shelf = com.familymdm.agent.sitepolicy.YoutubeScripts.shelf(new JSONObject().put("channels", ch).put("videos", vids).put("ask", false).toString());
            String cover = com.familymdm.agent.sitepolicy.YoutubeScripts.cover(new JSONObject().put("channels", ch).put("videos", vids).put("cover", "channels").put("guard", true).toString());
            rules.setYoutubeChannels(handles, videos, (shelf != null ? "<script>" + shelf + "</script>" : "") + (cover != null ? "<script>" + cover + "</script>" : ""));
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
