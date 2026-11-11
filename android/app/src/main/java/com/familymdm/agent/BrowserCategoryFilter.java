package com.familymdm.agent;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import com.familymdm.agent.sitepolicy.CategoryFilterRules;
import com.familymdm.agent.sitepolicy.SitePolicy;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies the browser filter level (Moderate / Strict) to the whole phone through the DNS/TLS filter
 * (DnsFilterVpnService and TcpRelay), so it covers Chrome, Firefox, Samsung Internet and the rest,
 * not only our own browsers, and needs no certificate.
 *
 * It uses the same compact category lists the Browser app uses (WebFilter downloads and verifies
 * them). The rule itself lives in CategoryFilterRules; this class supplies its inputs.
 *
 * What it can and cannot do, honestly:
 *  - It sees the name of a site (a DNS lookup, or the name in the secure-connection hello), never
 *    the page. So it blocks whole sites by category, not parts of a page.
 *  - If a browser looks sites up through its own encrypted DNS, the lookup is not seen; the
 *    secure-connection name still is (unless the site hides it), and QUIC is dropped while this is
 *    on so browsers fall back to the kind of connection the filter can see.
 *  - Until the lists are downloaded and verified it blocks nothing (it cannot fail closed without
 *    cutting off the whole phone's internet).
 */
final class BrowserCategoryFilter {
    private BrowserCategoryFilter() {}

    private static final long BROWSER_LIST_TTL_MS = 10 * 60 * 1000;
    private static Set<String> browsers;
    private static long browsersAt;
    private static String sitesPolicyRef;
    private static List<SitePolicy.Site> sites = new ArrayList<>();

    private static boolean on(Context c) {
        return !"off".equals(WebFilter.tier(c)) && WebFilter.active(c);
    }

    /** Should this host be refused for this app? `pkg` may be null when the app is not known. */
    static boolean blocks(Context c, String pkg, String host) {
        if (host == null || "off".equals(WebFilter.tier(c)) || !WebFilter.active(c)) return false;
        boolean core = WebFilter.blocksCore(c, host);
        boolean social = !core && WebFilter.blocksSocial(c, host);
        if (!core && !social) return false;
        return CategoryFilterRules.blocks(approvedSite(c, host), core, social, isBrowser(c, pkg));
    }

    /** True when the app's connections need identifying (only Strict's social rule depends on which app it is). */
    static boolean needsUid(Context c) {
        return "strict".equals(WebFilter.tier(c)) && WebFilter.active(c);
    }

    /** QUIC (UDP 443) hides the site name from the filter, so it is dropped while category filtering is on. */
    static boolean blockQuic(Context c) {
        return on(c);
    }

    /** A site on this phone's approved list always wins over a category. */
    private static synchronized boolean approvedSite(Context c, String host) {
        String policy = Agent.prefs(c).getString("policy", null);
        if (policy != sitesPolicyRef) { // same String object until the policy changes, so this is cheap
            sitesPolicyRef = policy;
            try {
                sites = policy == null ? new ArrayList<SitePolicy.Site>() : SitePolicy.parse(new JSONObject(policy).optJSONArray("sites"));
            } catch (Exception e) {
                sites = new ArrayList<>();
            }
        }
        return !sites.isEmpty() && SitePolicy.allowed("https://" + host + "/", sites);
    }

    /** Browsers are the apps that handle ordinary web links for every address. Re-checked every few minutes. */
    private static synchronized boolean isBrowser(Context c, String pkg) {
        if (pkg == null) return false;
        long now = System.currentTimeMillis();
        if (browsers == null || now - browsersAt > BROWSER_LIST_TTL_MS) {
            Set<String> s = new HashSet<>();
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/")).addCategory(Intent.CATEGORY_BROWSABLE);
                for (ResolveInfo ri : c.getPackageManager().queryIntentActivities(i, PackageManager.MATCH_ALL)) {
                    if (ri.activityInfo != null) s.add(ri.activityInfo.packageName);
                }
            } catch (Exception ignored) {
            }
            browsers = s;
            browsersAt = now;
        }
        return browsers.contains(pkg);
    }
}
