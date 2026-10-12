package com.familymdm.lockdown;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * A static, reusable way to reach the same "lockdown" SharedPreferences file every Activity here
 * already opens its own private prefs() instance method for -- needed so the DNS/TLS filter
 * classes (DnsBlocklist, NetworkBlocklist, TrafficLog, and the rest, ported from the agent app's
 * own copies) can read/write it as plain static utilities, the same shape they already have
 * there. Everything lands in this one file alongside the rest of this app's own config, nothing
 * separate to lose track of.
 */
final class LockdownPrefs {
    private LockdownPrefs() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("lockdown", Context.MODE_PRIVATE);
    }

    static Set<String> getSet(Context c, String key) {
        return new HashSet<>(prefs(c).getStringSet(key, new HashSet<String>()));
    }

    static void putSet(Context c, String key, Set<String> value) {
        prefs(c).edit().putStringSet(key, new HashSet<>(value)).apply();
    }
}
