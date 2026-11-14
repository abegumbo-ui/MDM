package com.familymdm.browser;

import android.content.Context;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Iterator;
import java.util.Locale;

/**
 * The Browser's side of the daily time allowance ("YouTube 2 hours a day"; see com.familymdm.agent.SiteTime, which this mirrors).
 *
 * Inside the MDM app (the Browser add-on) this class runs in the Agent's own process and counts into the Agent's counters
 * ("siteTime" in the "agent" settings file, same format), so the YouTube icon and the Browser share ONE allowance per site.
 * The limits and the "limits are lifted until" time come from the Agent in the Browser's settings bundle (Guard.siteLimits).
 * In the separate Browser app there are no limits, so nothing here ever blocks. Keep the two classes in step.
 */
final class SiteTime {
    private SiteTime() {}

    private static final String FILE = "agent";
    private static final String PREF = "siteTime";

    private static String today() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static android.content.SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static JSONObject used(Context c) {
        try {
            JSONObject o = new JSONObject(prefs(c).getString(PREF, "{}"));
            JSONObject ms = o.optJSONObject("ms");
            if (ms != null && today().equals(o.optString("day"))) return ms;
        } catch (Exception ignored) {
        }
        return new JSONObject();
    }

    /** The limit entry that governs this host: the most specific one that covers it, or null. */
    static String keyFor(JSONObject limits, String host) {
        if (host == null || limits == null) return null;
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("www.")) h = h.substring(4);
        String best = null;
        for (Iterator<String> it = limits.keys(); it.hasNext(); ) {
            String k = it.next();
            if (limits.optInt(k, 0) <= 0) continue;
            boolean covers = h.equals(k) || h.endsWith("." + k);
            if (covers && "music.youtube.com".equals(h) && !k.equals("music.youtube.com")) covers = false; // YouTube Music is a site of its own
            if (covers && (best == null || k.length() > best.length())) best = k;
        }
        return best;
    }

    /** Adds time spent on this host (only when a limit covers it). */
    static synchronized void add(Context c, JSONObject limits, String host, long millis) {
        String k = keyFor(limits, host);
        if (k == null) return;
        try {
            JSONObject ms = used(c);
            ms.put(k, ms.optLong(k, 0) + millis);
            prefs(c).edit().putString(PREF, new JSONObject().put("day", today()).put("ms", ms).toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** True when the day's time for this host is used up and nothing lifts the limit (freeUntil: extra time or "all restrictions off"). */
    static synchronized boolean exhausted(Context c, JSONObject limits, long freeUntil, String host) {
        String k = keyFor(limits, host);
        if (k == null || freeUntil > System.currentTimeMillis()) return false;
        return used(c).optLong(k, 0) >= limits.optInt(k, 0) * 60_000L;
    }

    static String words(int minutes) {
        if (minutes == 1) return "1 minute";
        if (minutes % 60 == 0) return minutes / 60 + (minutes == 60 ? " hour" : " hours");
        if (minutes > 60) return String.format(Locale.US, "%.1f hours", minutes / 60.0);
        return minutes + " minutes";
    }

    static String message(JSONObject limits, String host) {
        String k = keyFor(limits, host);
        if (k == null) return "";
        return "Today's time on " + k + " (" + words(limits.optInt(k, 0)) + " a day) is used up. It opens again tomorrow.";
    }
}
