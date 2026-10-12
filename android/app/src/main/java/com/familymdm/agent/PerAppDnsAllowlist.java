package com.familymdm.agent;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The only exception to NetworkBlocklist: hostnames listed here for a specific package are let
 * through even though the app has no network access otherwise. Deliberately separate from
 * DnsAllowlist (the device-wide override of DnsBlocklist) -- blocking one app's network entirely
 * and then letting the device-wide allow list quietly open it back up again for that same app
 * would defeat the whole point of blocking it per-app in the first place. A network-blocked
 * package only ever sees what's listed for it here, nothing else, regardless of what's on
 * DnsAllowlist. See DnsFilterVpnService's DNS handling and TcpRelay's SNI handling for exactly
 * how the two layers (device-wide, then per-app) combine.
 *
 * Stored as one JSON object ({pkg: [host, ...]}), one revision for the whole thing -- same
 * granularity WholeAppBlocklist uses for its own per-package map.
 */
final class PerAppDnsAllowlist {
    private static final String PREF_KEY = "perAppDnsAllowlist";
    private static final String REV_KEY = "perAppDnsAllowlistRev";

    private PerAppDnsAllowlist() {}

    private static JSONObject raw(Context c) {
        try {
            return new JSONObject(Agent.prefs(c).getString(PREF_KEY, "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    static boolean isEmpty(Context c) {
        return raw(c).length() == 0;
    }

    static Set<String> get(Context c, String pkg) {
        Set<String> out = new LinkedHashSet<>();
        JSONArray arr = raw(c).optJSONArray(pkg);
        if (arr != null) for (int i = 0; i < arr.length(); i++) out.add(arr.optString(i, ""));
        return out;
    }

    static boolean allows(Context c, String pkg, String host) {
        if (pkg == null || host == null || host.isEmpty()) return false;
        host = host.toLowerCase(Locale.ROOT);
        for (String allowed : get(c, pkg)) {
            if (host.equals(allowed) || host.endsWith("." + allowed)) return true;
        }
        return false;
    }

    static long rev(Context c) {
        return Agent.prefs(c).getLong(REV_KEY, 0);
    }

    private static void bump(Context c) {
        Agent.prefs(c).edit().putLong(REV_KEY, System.currentTimeMillis()).apply();
    }

    static void seed(Context c) {
        bump(c);
    }

    /** An edit made directly on the phone -- bumps the revision so the next sync hands the whole
     * map up to the dashboard, same pattern as DnsAllowlist.setLocal(). */
    static void setLocalFor(Context c, String pkg, Set<String> hosts) {
        try {
            JSONObject obj = raw(c);
            JSONArray arr = new JSONArray();
            for (String h : hosts) arr.put(h);
            if (hosts.isEmpty()) obj.remove(pkg); else obj.put(pkg, arr);
            Agent.prefs(c).edit().putString(PREF_KEY, obj.toString()).apply();
            bump(c);
        } catch (JSONException ignored) {
        }
    }

    /** {pkg: [host, ...]}, for the sync body. */
    static JSONObject toJson(Context c) {
        return raw(c);
    }

    /** Reconciles the local map to match the dashboard's current one -- same "whole map, every
     * sync" shape as WholeAppBlocklist.adopt(). */
    static void adopt(Context c, JSONObject map) {
        if (map == null) return;
        JSONObject clean = new JSONObject();
        java.util.Iterator<String> it = map.keys();
        while (it.hasNext()) {
            String pkg = it.next();
            if (!pkg.matches("^[A-Za-z0-9_.]{1,200}$")) continue;
            JSONArray in = map.optJSONArray(pkg);
            if (in == null) continue;
            Set<String> sanitized = DnsBlocklist.sanitize(in);
            if (sanitized.isEmpty()) continue;
            try {
                JSONArray out = new JSONArray();
                for (String h : sanitized) out.put(h);
                clean.put(pkg, out);
            } catch (JSONException ignored) {
            }
        }
        Agent.prefs(c).edit().putString(PREF_KEY, clean.toString()).apply();
    }
}
