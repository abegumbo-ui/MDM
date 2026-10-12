package com.familymdm.lockdown;

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
 * package only ever sees what's listed for it here, nothing else.
 *
 * Stored as one JSON object ({pkg: [host, ...]}) in this app's own "lockdown" prefs file.
 */
final class PerAppDnsAllowlist {
    private static final String PREF_KEY = "perAppDnsAllowlist";

    private PerAppDnsAllowlist() {}

    private static JSONObject raw(Context c) {
        try {
            return new JSONObject(LockdownPrefs.prefs(c).getString(PREF_KEY, "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
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

    static void setFor(Context c, String pkg, Set<String> hosts) {
        try {
            JSONObject obj = raw(c);
            JSONArray arr = new JSONArray();
            for (String h : hosts) arr.put(h);
            if (hosts.isEmpty()) obj.remove(pkg); else obj.put(pkg, arr);
            LockdownPrefs.prefs(c).edit().putString(PREF_KEY, obj.toString()).apply();
        } catch (JSONException ignored) {
        }
    }
}
