package com.familymdm.agent;

import android.content.Context;

import org.json.JSONArray;

import java.util.Locale;
import java.util.Set;

/**
 * Overrides DnsBlocklist for a hostname that matches both -- some entries on the block list are
 * broad on purpose (a whole CDN domain, to catch every subdomain of it), but one specific
 * subdomain under it is still needed. Checked the same way DnsBlocklist itself is (exact hostname
 * or any subdomain of an entry), and wins whenever both lists match the same query or SNI
 * hostname: see DnsFilterVpnService's DNS handling and TcpRelay's SNI handling, both of which
 * check `DnsBlocklist.blocks(...) && !DnsAllowlist.allows(...)`.
 *
 * Editable from either side now, same rev-based local-edit-wins pattern as DnsBlocklist: the
 * dashboard's own DNS Hostname Allow box, or this app's own Administrator panel.
 */
final class DnsAllowlist {
    private static final String PREF_KEY = "dnsAllowlist";
    private static final String REV_KEY = "dnsAllowlistRev";

    private DnsAllowlist() {}

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

    /** An edit made directly on the phone (the Administrator panel's own DNS Hostname Allow
     * section) -- bumps the revision so the next sync hands it up to the dashboard, same pattern
     * as DnsBlocklist.setLocal(). */
    static void setLocal(Context c, Set<String> hosts) {
        set(c, hosts);
        bump(c);
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

    /** The current list, for the sync body's dnsAllowlistValue. */
    static JSONArray toJson(Context c) {
        JSONArray arr = new JSONArray();
        for (String h : list(c)) arr.put(h);
        return arr;
    }

    /** Same tolerant parsing as DnsBlocklist.sanitize() -- lines pasted in here come from the
     * same kinds of sources (an exported list, or hostnames copied out of the Traffic Log). */
    static Set<String> sanitize(JSONArray raw) {
        return DnsBlocklist.sanitize(raw);
    }

    static Set<String> sanitize(Iterable<String> rawLines) {
        return DnsBlocklist.sanitize(rawLines);
    }
}
