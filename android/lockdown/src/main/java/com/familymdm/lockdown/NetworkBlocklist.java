package com.familymdm.lockdown;

import android.content.Context;

import java.util.Set;

/**
 * A different thing from the regular app block list above (which stops an app launching or
 * running at all): this one lets the app run completely normally, it just gets no network access
 * at all -- enforced by DnsFilterVpnService/TcpRelay/UdpRelay checking the owning app of every
 * query/connection against this list. No OS API call to make here (there's nothing to "apply" --
 * the filter just reads this list live).
 *
 * PerAppDnsAllowlist is the only way through: a network-blocked app gets nothing except whatever
 * hostnames are listed for it there specifically -- the device-wide DnsAllowlist does NOT apply
 * to a network-blocked app, on purpose (see PerAppDnsAllowlist's own doc for why).
 */
final class NetworkBlocklist {
    private static final String PREF_KEY = "networkBlockedPackages";
    private static final String PAUSED_KEY = "networkBlockedPackagesPaused";

    private NetworkBlocklist() {}

    static Set<String> list(Context c) {
        return LockdownPrefs.getSet(c, PREF_KEY);
    }

    static Set<String> pausedList(Context c) {
        return LockdownPrefs.getSet(c, PAUSED_KEY);
    }

    static boolean isPaused(Context c, String pkg) {
        return pausedList(c).contains(pkg);
    }

    /** Whether this package's network is currently cut off -- listed, and not paused. */
    static boolean isBlocked(Context c, String pkg) {
        return pkg != null && list(c).contains(pkg) && !isPaused(c, pkg);
    }

    static void add(Context c, String pkg) {
        Set<String> set = list(c);
        set.add(pkg);
        LockdownPrefs.putSet(c, PREF_KEY, set);
    }

    static void remove(Context c, String pkg) {
        Set<String> set = list(c);
        set.remove(pkg);
        LockdownPrefs.putSet(c, PREF_KEY, set);
        Set<String> paused = pausedList(c);
        if (paused.remove(pkg)) LockdownPrefs.putSet(c, PAUSED_KEY, paused);
    }

    static void setPaused(Context c, String pkg, boolean paused) {
        Set<String> set = pausedList(c);
        if (paused) set.add(pkg); else set.remove(pkg);
        LockdownPrefs.putSet(c, PAUSED_KEY, set);
    }
}
