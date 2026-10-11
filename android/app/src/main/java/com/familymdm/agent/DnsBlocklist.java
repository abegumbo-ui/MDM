package com.familymdm.agent;

import android.content.Context;

import org.json.JSONArray;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Hostnames DnsFilterVpnService refuses to resolve -- not a general-purpose site blocklist (that's
 * the Browser app's own, URL-level), just a plain set of DNS names, matched as an exact hostname or
 * any subdomain of one (the same "domain" matching normalizeSite() already uses for the Browser
 * allowlist). Whether the filter is even running is derived from this list being non-empty, not a
 * separate on/off flag -- see PolicyApplier.applyDnsFilter().
 */
final class DnsBlocklist {
    private static final String PREF_KEY = "dnsBlocklist";

    private DnsBlocklist() {}

    static Set<String> list(Context c) {
        return Agent.getSet(c, PREF_KEY);
    }

    static void set(Context c, Set<String> hosts) {
        Agent.putSet(c, PREF_KEY, hosts);
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

    /** Sanitizes the dashboard's array into a plain hostname set -- same shape sent back out in
     * reports, nothing fancier than a hostname-looking string is ever accepted. */
    static Set<String> sanitize(JSONArray raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null) return out;
        for (int i = 0; i < raw.length(); i++) {
            String h = raw.optString(i, "").trim().toLowerCase(Locale.ROOT);
            if (h.matches("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$")) out.add(h);
        }
        return out;
    }
}
