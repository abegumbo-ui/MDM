package com.familymdm.lockdown;

import android.content.Context;

import java.util.Locale;
import java.util.Set;

/**
 * Overrides DnsBlocklist for a hostname that matches both -- some entries on the block list are
 * broad on purpose (a whole CDN domain, to catch every subdomain of it), but one specific
 * subdomain under it is still needed. Checked the same way DnsBlocklist itself is, and wins
 * whenever both lists match the same query or SNI hostname: see DnsFilterVpnService's DNS
 * handling and TcpRelay's SNI handling, both of which check
 * `DnsBlocklist.blocks(...) && !DnsAllowlist.allows(...)`.
 */
final class DnsAllowlist {
    private static final String PREF_KEY = "dnsAllowlist";

    private DnsAllowlist() {}

    static Set<String> list(Context c) {
        return LockdownPrefs.getSet(c, PREF_KEY);
    }

    static void set(Context c, Set<String> hosts) {
        LockdownPrefs.putSet(c, PREF_KEY, hosts);
    }

    /** host == one of the allowed entries, or a subdomain of one -- same matching shape as
     * DnsBlocklist.blocks(). */
    static boolean allows(Context c, String host) {
        if (host == null || host.isEmpty()) return false;
        host = host.toLowerCase(Locale.ROOT);
        for (String allowed : list(c)) {
            if (host.equals(allowed) || host.endsWith("." + allowed)) return true;
        }
        return false;
    }

    static Set<String> sanitize(Iterable<String> rawLines) {
        return DnsBlocklist.sanitize(rawLines);
    }
}
