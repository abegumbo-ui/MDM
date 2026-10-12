package com.familymdm.agent;

import android.content.Context;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Hostnames DnsFilterVpnService refuses to resolve OR connect to -- not a general-purpose site
 * blocklist (that's the Browser app's own, URL-level), just a plain set of names, matched as an
 * exact hostname or any subdomain of one (the same "domain" matching normalizeSite() already uses
 * for the Browser allowlist), checked against both a DNS query's QNAME and a TLS connection's own
 * SNI hostname (TcpRelay). Whether the filter is even running is derived from this list being
 * non-empty, not a separate on/off flag -- see PolicyApplier.applyDnsFilter().
 *
 * Editable from either side: the dashboard's own DNS Hostname Block box, or this app's own
 * Administrator panel. REV_KEY follows the same local-edit-wins pattern as WholeAppBlocklist --
 * setLocal() bumps it so the next sync hands the phone's own list to the dashboard instead of the
 * dashboard's (possibly stale) one clobbering it.
 */
final class DnsBlocklist {
    private static final String PREF_KEY = "dnsBlocklist";
    private static final String REV_KEY = "dnsBlocklistRev";

    private DnsBlocklist() {}

    static Set<String> list(Context c) {
        return Agent.getSet(c, PREF_KEY);
    }

    static void set(Context c, Set<String> hosts) {
        Agent.putSet(c, PREF_KEY, hosts);
    }

    static long rev(Context c) {
        return Agent.prefs(c).getLong(REV_KEY, 0);
    }

    private static void bump(Context c) {
        Agent.prefs(c).edit().putLong(REV_KEY, System.currentTimeMillis()).apply();
    }

    /** Bumps the rev with no other change -- used when a list that already existed locally
     * before the dashboard offered this field at all gets carried forward as-is. */
    static void seed(Context c) {
        bump(c);
    }

    /** An edit made directly on the phone (the Administrator panel's own DNS Hostname Block
     * section) -- bumps the revision so the next sync hands it up to the dashboard, same pattern
     * as WholeAppBlocklist.add()/remove(). */
    static void setLocal(Context c, Set<String> hosts) {
        set(c, hosts);
        bump(c);
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

    /** The current list, for the sync body's dnsBlocklistValue. */
    static JSONArray toJson(Context c) {
        JSONArray arr = new JSONArray();
        for (String h : list(c)) arr.put(h);
        return arr;
    }

    /** Sanitizes the dashboard's array into a plain hostname set. */
    static Set<String> sanitize(JSONArray raw) {
        List<String> lines = new ArrayList<>();
        if (raw != null) {
            for (int i = 0; i < raw.length(); i++) lines.add(raw.optString(i, ""));
        }
        return sanitize(lines);
    }

    /** Same sanitizing, for lines typed/pasted directly into the Administrator panel. Tolerates
     * the common blocklist export shapes (AdBlock "||host^", full URLs, "*." wildcards, "#"/"!"
     * comment lines) rather than only bare hostnames, since pasted lists rarely come bare. */
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
