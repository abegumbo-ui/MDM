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
                .putString("filterSkipApps", join(policy.optJSONArray("filterSkipApps")))
                .putString("inAppMode", policy.optString("inAppBrowsers", "off"))
                .putString("inAppExceptions", join(policy.optJSONArray("inAppExceptions")))
                .putBoolean("waStatus", policy.optBoolean("blockWaStatus", false))
                .putBoolean("waChannels", policy.optBoolean("blockWaChannels", false))
                .putLong("internetOffUntil", policy.optLong("internetOffUntil", 0))
                .apply();
        if ((!"off".equals(inAppMode(c)) || waStatus(c) || waChannels(c)) && !Agent.accessibilityServiceOn(c)) remindAccessibility(c);
    }

    private static String join(JSONArray a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; a != null && i < a.length(); i++) {
            String p = a.optString(i, "").trim();
            if (!p.isEmpty()) sb.append(sb.length() == 0 ? "" : ",").append(p);
        }
        return sb.toString();
    }

    private static volatile Set<String> cachedSkip;
    private static volatile String cachedSkipFrom;

    /** "Not filtered" apps (dashboard: Internet for apps): the site filter leaves them alone. */
    static boolean skips(Context c, String pkg) {
        if (pkg == null) return false;
        String s = Agent.prefs(c).getString("filterSkipApps", "");
        if (s.isEmpty()) return false;
        if (!s.equals(cachedSkipFrom) || cachedSkip == null) {
            Set<String> out = new HashSet<>();
            for (String p : s.split(",")) if (!p.isEmpty()) out.add(p);
            cachedSkip = out;
            cachedSkipFrom = s;
        }
        return cachedSkip.contains(pkg);
    }

    static boolean waStatus(Context c) {
        return Agent.prefs(c).getBoolean("waStatus", false);
    }

    static boolean waChannels(Context c) {
        return Agent.prefs(c).getBoolean("waChannels", false);
    }

    /** "Web pages inside apps": off, tabs or all (see InAppRules and BlockAccessibilityService). */
    static String inAppMode(Context c) {
        return Agent.prefs(c).getString("inAppMode", "off");
    }

    private static volatile Set<String> cachedInApp;
    private static volatile String cachedInAppFrom;

    static Set<String> inAppExceptions(Context c) {
        String s = Agent.prefs(c).getString("inAppExceptions", "");
        if (!s.equals(cachedInAppFrom) || cachedInApp == null) {
            Set<String> out = new HashSet<>();
            for (String p : s.split(",")) if (!p.isEmpty()) out.add(p);
            cachedInApp = out;
            cachedInAppFrom = s;
        }
        return cachedInApp;
    }

    /** A notification that opens Settings → Accessibility, where the Agent is switched on once. At most every 6 hours. */
    static void remindAccessibility(Context c) {
        android.content.SharedPreferences p = Agent.prefs(c);
        long now = System.currentTimeMillis();
        if (now - p.getLong("a11yReminded", 0) < 6 * 3600 * 1000L) return;
        p.edit().putLong("a11yReminded", now).apply();
        try {
            android.app.NotificationManager nm = c.getSystemService(android.app.NotificationManager.class);
            nm.createNotificationChannel(new android.app.NotificationChannel("setup", "Setup steps", android.app.NotificationManager.IMPORTANCE_DEFAULT));
            android.content.Intent i = new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(c, 42, i, android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
            nm.notify(42, new android.app.Notification.Builder(c, "setup")
                    .setContentTitle("Finish setting up: turn on MDM Agent")
                    .setContentText("Tap, choose MDM Agent, switch it on. Needed once.")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build());
        } catch (Exception ignored) {
        }
    }

    /** Web filtering paused right now? Uses the phone's own clock, so it ends on time offline too. */
    static boolean paused(Context c) {
        return Agent.prefs(c).getLong("pauseUntil", 0) > System.currentTimeMillis();
    }

    /** True while "internet only for these apps" is on, so the filter has to work out which app is asking. */
    /** "Turn the internet off for a while": every app but Android's essentials and the "internet only" apps. */
    static boolean internetOff(Context c) {
        return Agent.prefs(c).getLong("internetOffUntil", 0) > System.currentTimeMillis();
    }

    static boolean needsUid(Context c) {
        return internetOff(c) || Agent.prefs(c).getBoolean("internetOnlyApps", false) || !Agent.prefs(c).getString("filterSkipApps", "").isEmpty();
    }

    static boolean internetAllowed(Context c, String pkg) {
        if (internetOff(c)) return ProtectionRules.internetAllowed(pkg, true, Agent.prefs(c).getBoolean("internetOnlyApps", false) ? apps(c) : new HashSet<>());
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
