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

    /** Forwards to whichever rules are current, so a policy change never needs a restart. */
    private static final InspectProxy.Inspector LIVE = new InspectProxy.Inspector() {
        public boolean bypass(String host, int port) { return rules.bypass(host, port); }
        public InspectProxy.Response onRequest(InspectProxy.Request r) { return rules.onRequest(r); }
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
                updateRules(policy);
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

    private static void updateRules(JSONObject policy) {
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
    }

    private static void enable(Context c, DevicePolicyManager dpm, ComponentName admin, boolean applied) throws Exception {
        CertAuthority ca = authority(c);
        byte[] der = ca.caCertificate().getEncoded();
        if (!applied) {
            if (!dpm.installCaCert(admin, der)) throw new IllegalStateException("could not install the certificate");
        }
        boolean fresh = proxy == null;
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
