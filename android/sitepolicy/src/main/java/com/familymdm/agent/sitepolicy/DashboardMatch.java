package com.familymdm.agent.sitepolicy;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * The dashboard itself: whatever rule is on -- "Internet off", a blocked site or word, "no internet for this app", a filter level, every
 * restriction turned off together, anything -- the phone (and any browser on it, not only the agent app) must still always be able to
 * reach its own dashboard. Getting locked out of the one thing that could turn a rule back off, from the very phone the rule is on,
 * defeats the purpose of having a dashboard at all, so this check comes before every other blocking decision, never after it.
 * <p>
 * Pure matching logic only (no Android framework), so it can be checked offline -- see DashboardMatchCheck. DashboardHost, in the agent
 * module, is where the phone's current server address(es) actually come from and where this gets called from (DnsFilterVpnService's DNS
 * and QUIC handling, TcpRelay's TLS SNI check).
 */
public final class DashboardMatch {
    private DashboardMatch() {
    }

    /** The hostname out of a server address ("https://mdmpi.tail67992b.ts.net/" -&gt; "mdmpi.tail67992b.ts.net"), or null. */
    public static String hostOf(String serverUrl) {
        if (serverUrl == null || serverUrl.isEmpty()) return null;
        try {
            String h = new URI(serverUrl).getHost();
            return h == null || h.isEmpty() ? null : h.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * True for a question (a DNS lookup, or a TLS SNI name) naming the dashboard itself. An exact match only, never "ends with": that
     * would also match a look-alike subdomain some other site controls (evil.mdmpi.tail67992b.ts.net.attacker.example would end with
     * the real name too if this used endsWith the other way around; an exact match after stripping one trailing dot has no such hole).
     */
    public static boolean matches(Set<String> dashboardHosts, String name) {
        if (name == null || dashboardHosts == null || dashboardHosts.isEmpty()) return false;
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".")) n = n.substring(0, n.length() - 1);
        return dashboardHosts.contains(n);
    }
}
