package com.familymdm.lockdown;

import android.content.Context;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Hostnames DnsFilterVpnService refuses to resolve OR connect to -- not a general-purpose site
 * blocklist, just a plain set of names, matched as an exact hostname or any subdomain of one,
 * checked against both a DNS query's QNAME and a TLS connection's own SNI hostname (TcpRelay). A
 * match here is only the first word, not the last: DnsAllowlist can override it for a more
 * specific hostname still needed under a deliberately broad entry here. Whether the filter is
 * even running is derived from this list being non-empty, not a separate on/off flag.
 *
 * Ported from the agent app's own DnsBlocklist, with the dashboard-sync revision tracking
 * stripped out -- this app never talks to a dashboard, there's nothing to sync.
 */
final class DnsBlocklist {
    private static final String PREF_KEY = "dnsBlocklist";

    private DnsBlocklist() {}

    static Set<String> list(Context c) {
        return LockdownPrefs.getSet(c, PREF_KEY);
    }

    static void set(Context c, Set<String> hosts) {
        LockdownPrefs.putSet(c, PREF_KEY, hosts);
    }

    /** host == one of the blocked entries, or a subdomain of one. */
    static boolean blocks(Context c, String host) {
        if (host == null || host.isEmpty()) return false;
        host = host.toLowerCase(Locale.ROOT);
        for (String blocked : list(c)) {
            if (host.equals(blocked) || host.endsWith("." + blocked)) return true;
        }
        return false;
    }

    /** Tolerates the common blocklist export shapes (AdBlock "||host^", full URLs, "*."
     * wildcards, "#"/"!" comment lines) rather than only bare hostnames, since pasted lists
     * rarely come bare. */
    static Set<String> sanitize(Iterable<String> rawLines) {
        Set<String> out = new LinkedHashSet<>();
        for (String raw : rawLines) {
            String h = (raw == null ? "" : raw).trim().toLowerCase(Locale.ROOT);
            if (h.isEmpty() || h.startsWith("#") || h.startsWith("!")) continue;
            if (h.startsWith("||")) h = h.substring(2);
            if (h.endsWith("^")) h = h.substring(0, h.length() - 1);
            if (h.startsWith("https://")) h = h.substring(8);
            else if (h.startsWith("http://")) h = h.substring(7);
            int slash = h.indexOf('/');
            if (slash >= 0) h = h.substring(0, slash);
            if (h.startsWith("*.")) h = h.substring(2);
            else if (h.startsWith("*")) h = h.substring(1);
            if (h.matches("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$")) out.add(h);
        }
        return out;
    }
}
