package com.familymdm.agent;

import android.content.Context;

import com.familymdm.agent.sitepolicy.ProtectionRules;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Three dashboard switches the phone-wide filter (DnsFilterVpnService, TcpRelay, UdpRelay) and
 * the Agent's browser check:
 * - "Pause web filtering until ...": site lists, categories, blocked words, SafeSearch and YouTube
 *   Restricted Mode stand down until then, then come back by themselves even with no internet.
 *   App controls (blocked apps, cut-off apps, internet-only-for-these-apps) are not paused.
 * - "Internet only for these apps": every other app gets no internet (see ProtectionRules).
 * - "Block image search" in Google, Bing and DuckDuckGo.
 */
final class Protection {
    private Protection() {}

    private static volatile Set<String> cachedApps;
    private static volatile String cachedFrom;

    static void apply(Context c, JSONObject policy) {
        JSONArray a = policy.optJSONArray("internetApps");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; a != null && i < a.length(); i++) {
            String p = a.optString(i, "").trim();
            if (!p.isEmpty()) sb.append(sb.length() == 0 ? "" : ",").append(p);
        }
        Agent.prefs(c).edit()
                .putLong("pauseUntil", policy.optLong("pauseUntil", 0))
                .putBoolean("internetOnlyApps", policy.optBoolean("internetOnlyApps", false))
                .putString("internetApps", sb.toString())
                .putBoolean("blockImageSearch", policy.optBoolean("blockImageSearch", false))
                .apply();
    }

    /** Web filtering paused right now? Uses the phone's own clock, so it ends on time offline too. */
    static boolean paused(Context c) {
        return Agent.prefs(c).getLong("pauseUntil", 0) > System.currentTimeMillis();
    }

    /** True while "internet only for these apps" is on, so the filter has to work out which app is asking. */
    static boolean needsUid(Context c) {
        return Agent.prefs(c).getBoolean("internetOnlyApps", false);
    }

    static boolean internetAllowed(Context c, String pkg) {
        boolean on = Agent.prefs(c).getBoolean("internetOnlyApps", false);
        if (!on) return true;
        return ProtectionRules.internetAllowed(pkg, true, apps(c));
    }

    private static Set<String> apps(Context c) {
        String s = Agent.prefs(c).getString("internetApps", "");
        if (s.equals(cachedFrom) && cachedApps != null) return cachedApps;
        Set<String> out = new HashSet<>();
        for (String p : s.split(",")) if (!p.isEmpty()) out.add(p);
        cachedApps = out;
        cachedFrom = s;
        return out;
    }

    static boolean blocksImageSearch(Context c, String url) {
        return Agent.prefs(c).getBoolean("blockImageSearch", false) && ProtectionRules.isImageSearch(url);
    }
}
