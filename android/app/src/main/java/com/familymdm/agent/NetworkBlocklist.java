package com.familymdm.agent;

import android.content.Context;

import org.json.JSONObject;

import java.util.Set;

/**
 * A different thing from WholeAppBlocklist: that one makes an app unable to launch or run at all
 * (setApplicationHidden); this one lets the app run completely normally, it just gets no network
 * access at all -- enforced by DnsFilterVpnService/TcpRelay/UdpRelay checking this list against
 * the UID a query or connection came from, the same way DnsBlocklist is checked against a
 * hostname. No OS API call to make here (there's nothing to "apply" -- the filter just reads this
 * list live on every query/connection), unlike WholeAppBlocklist's applyHidden().
 *
 * PerAppDnsAllowlist is the only way through: a network-blocked app gets nothing except whatever
 * hostnames are listed for it there specifically -- the device-wide DnsAllowlist does NOT apply
 * to a network-blocked app, on purpose (see PerAppDnsAllowlist's own doc for why).
 *
 * Pause/Remove/rev-sync all work exactly the same way WholeAppBlocklist's do.
 */
final class NetworkBlocklist {
    private static final String PREF_KEY = "networkBlockedPackages";
    private static final String PAUSED_KEY = "networkBlockedPackagesPaused";
    private static final String REV_KEY = "networkBlocklistRev";

    private NetworkBlocklist() {}

    static Set<String> list(Context c) {
        return Agent.getSet(c, PREF_KEY);
    }

    static Set<String> pausedList(Context c) {
        return Agent.getSet(c, PAUSED_KEY);
    }

    static boolean isPaused(Context c, String pkg) {
        return pausedList(c).contains(pkg);
    }

    /** Whether this package's network is currently cut off -- listed, and not paused. */
    static boolean isBlocked(Context c, String pkg) {
        return pkg != null && list(c).contains(pkg) && !isPaused(c, pkg);
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

    static void add(Context c, String pkg) {
        Set<String> set = list(c);
        set.add(pkg);
        Agent.putSet(c, PREF_KEY, set);
        bump(c);
    }

    static void remove(Context c, String pkg) {
        Set<String> set = list(c);
        set.remove(pkg);
        Agent.putSet(c, PREF_KEY, set);
        Set<String> paused = pausedList(c);
        if (paused.remove(pkg)) Agent.putSet(c, PAUSED_KEY, paused);
        bump(c);
    }

    static void setPaused(Context c, String pkg, boolean paused) {
        Set<String> set = pausedList(c);
        if (paused) set.add(pkg); else set.remove(pkg);
        Agent.putSet(c, PAUSED_KEY, set);
        bump(c);
    }

    /** {pkg: {paused: bool}}, for the sync body -- same shape as WholeAppBlocklist.toJson(). */
    static JSONObject toJson(Context c) {
        JSONObject out = new JSONObject();
        Set<String> paused = pausedList(c);
        try {
            for (String pkg : list(c)) {
                JSONObject entry = new JSONObject();
                entry.put("paused", paused.contains(pkg));
                out.put(pkg, entry);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Reconciles the local list to match the dashboard's current map -- same shape as
     * WholeAppBlocklist.adopt(), minus the OS call (nothing to hide/un-hide here). */
    static void adopt(Context c, JSONObject map) {
        if (map == null) return;
        Set<String> wantPkgs = new java.util.LinkedHashSet<>();
        Set<String> wantPaused = new java.util.LinkedHashSet<>();
        java.util.Iterator<String> it = map.keys();
        while (it.hasNext()) {
            String pkg = it.next();
            if (!pkg.matches("^[A-Za-z0-9_.]{1,200}$")) continue;
            wantPkgs.add(pkg);
            if (map.optJSONObject(pkg) != null && map.optJSONObject(pkg).optBoolean("paused", false)) wantPaused.add(pkg);
        }
        Set<String> have = list(c);
        if (!have.equals(wantPkgs)) Agent.putSet(c, PREF_KEY, wantPkgs);
        Set<String> havePaused = pausedList(c);
        if (!havePaused.equals(wantPaused)) Agent.putSet(c, PAUSED_KEY, wantPaused);
    }
}
