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
        if (approvedOnlyBlocks(c, pkg, host)) return true;
        if (host == null || "off".equals(WebFilter.tier(c)) || !WebFilter.active(c)) return false;
        boolean core = WebFilter.blocksCore(c, host);
        boolean social = !core && WebFilter.blocksSocial(c, host);
        if (!core && !social) return false;
        return CategoryFilterRules.blocks(approvedSite(c, host), core, social, isBrowser(c, pkg));
    }

    /**
     * A request that came through the Agent's inspection proxy. Only browsers (and web views) send their traffic there, and the proxy looks the
     * site up itself (as the Agent), so the DNS filter never sees which browser asked: everything a browser may not open is refused here too.
     */
    static boolean blocksViaProxy(Context c, String host) {
        if (host == null || Protection.paused(c)) return false;
        String h = host.toLowerCase(java.util.Locale.ROOT);
        if (approvedOnly(c)) {
            for (String p : PAGE_PARTS) if (h.equals(p) || h.endsWith("." + p)) return false;
            return !approvedSite(c, h) && !DnsAllowlist.allows(c, h);
        }
        if ("off".equals(WebFilter.tier(c)) || !WebFilter.active(c)) return false;
        boolean core = WebFilter.blocksCore(c, h);
        boolean browserOnly = !core && WebFilter.blocksSocial(c, h);
        if (!core && !browserOnly) return false;
        return !approvedSite(c, h) && !DnsAllowlist.allows(c, h);
    }

    /** True when the app's connections need identifying (only Strict's social rule depends on which app it is). */
    static boolean needsUid(Context c) {
        if (approvedOnly(c)) return true;
        return WebFilter.active(c) && (WebFilter.tier(c).startsWith("strict") || WebFilter.hasBrowserOnly(c));
    }

    /** QUIC (UDP 443) hides the site name from the filter, so it is dropped while category filtering is on. */
    static boolean blockQuic(Context c) {
        return on(c);
    }

    // ---- "Approved sites only" in every browser ----

    private static boolean approvedOnly(Context c) {
        return Agent.prefs(c).getBoolean("approvedOnlyBrowsers", false);
    }

    /** What every web page needs to show (fonts, scripts, pictures from the big content networks), and the browsers' own update and safety checks. */
    private static final String[] PAGE_PARTS = {"gstatic.com", "googleapis.com", "cdnjs.cloudflare.com", "jsdelivr.net", "unpkg.com",
            "cloudfront.net", "akamaihd.net", "akamaized.net", "fastly.net", "bootstrapcdn.com", "jquery.com"};

    /**
     * "Approved sites only": Chrome, Samsung Internet, Edge, Firefox and every other browser open only the
     * approved sites (whole sites on the allowed list, and the phone's own allow list), the same as our
     * Browser. Our Browser and this app decide by page themselves; other apps are not affected.
     */
    static boolean approvedOnlyBlocks(Context c, String pkg, String host) {
        if (host == null || pkg == null || !approvedOnly(c)) return false;
        if (pkg.equals(c.getPackageName()) || pkg.equals("com.familymdm.browser") || !isBrowser(c, pkg)) return false;
        String h = host.toLowerCase(java.util.Locale.ROOT);
        for (String p : PAGE_PARTS) if (h.equals(p) || h.endsWith("." + p)) return false;
        return !approvedSite(c, h) && !DnsAllowlist.allows(c, h);
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
