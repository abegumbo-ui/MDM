package com.familymdm.agent;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

/**
 * A daily time allowance for a site: "YouTube, 2 hours a day". The budget starts again every morning (it is not "2 hours from when
 * it was approved"), and it is counted on the phone, so it keeps working with no internet.
 *
 * Time is counted where this app itself shows the site: the YouTube and YouTube Music icons and the Browser, while the page is on
 * screen. When the budget is used up the site shows "Time is up for today" until midnight. Extra time from the administrator
 * ("addTime") and "all restrictions off" lift the limit while they last (the time is still counted).
 *
 * The limits come from the dashboard (screenTime.siteLimits: host -> minutes a day).
 */
final class SiteTime {
    private SiteTime() {}

    private static final String PREF = "siteTime";
    private static String day = "";
    private static final HashMap<String, Long> MS = new HashMap<>(); // today's milliseconds per limit entry
    private static boolean loaded;

    private static String today() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** Loads what was counted so far today, and starts a new day with nothing counted. */
    private static synchronized void sync(Context c) {
        if (!loaded) {
            loaded = true;
            try {
                JSONObject o = new JSONObject(Agent.prefs(c).getString(PREF, "{}"));
                day = o.optString("day", "");
                JSONObject m = o.optJSONObject("ms");
                if (m != null && today().equals(day)) {
                    for (Iterator<String> it = m.keys(); it.hasNext(); ) {
                        String k = it.next();
                        MS.put(k, m.optLong(k, 0));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        String t = today();
        if (!t.equals(day)) {
            day = t;
            MS.clear();
        }
    }

    private static void save(Context c) {
        try {
            JSONObject m = new JSONObject();
            for (java.util.Map.Entry<String, Long> e : MS.entrySet()) m.put(e.getKey(), e.getValue());
            Agent.prefs(c).edit().putString(PREF, new JSONObject().put("day", day).put("ms", m).toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** host -> minutes a day, from the dashboard. */
    static JSONObject limits(Context c) {
        try {
            JSONObject st = new JSONObject(Agent.prefs(c).getString("policy", "{}")).optJSONObject("screenTime");
            JSONObject l = st == null ? null : st.optJSONObject("siteLimits");
            return l == null ? new JSONObject() : l;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** The limit entry that governs this host: the most specific one that covers it (youtube.com covers m.youtube.com), or null. */
    static String keyFor(Context c, String host) {
        if (host == null) return null;
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("www.")) h = h.substring(4);
        JSONObject l = limits(c);
        String best = null;
        for (Iterator<String> it = l.keys(); it.hasNext(); ) {
            String k = it.next();
            if (l.optInt(k, 0) <= 0) continue;
            boolean covers = h.equals(k) || h.endsWith("." + k);
            // YouTube Music is a site of its own here: a limit on youtube.com does not count (or stop) it.
            if (covers && "music.youtube.com".equals(h) && !k.equals("music.youtube.com")) covers = false;
            if (covers && (best == null || k.length() > best.length())) best = k;
        }
        return best;
    }

    /** True while the limits are lifted: extra time was given, or every restriction is off. */
    private static boolean lifted(Context c) {
        return EmergencyUnlock.isActive(c) || Agent.prefs(c).getLong("timeFreeUntil", 0) > System.currentTimeMillis();
    }

    /** Adds time spent on this host (only when a limit covers it). */
    static synchronized void add(Context c, String host, long millis) {
        String k = keyFor(c, host);
        if (k == null) return;
        sync(c);
        Long had = MS.get(k);
        MS.put(k, (had == null ? 0 : had) + millis);
        save(c);
    }

    /** True when the day's time for this host is used up (and nothing lifts the limit). */
    static synchronized boolean exhausted(Context c, String host) {
        String k = keyFor(c, host);
        if (k == null || lifted(c)) return false;
        sync(c);
        Long used = MS.get(k);
        return used != null && used >= limits(c).optInt(k, 0) * 60_000L;
    }

    static String words(int minutes) {
        if (minutes == 1) return "1 minute";
        if (minutes % 60 == 0) return minutes / 60 + (minutes == 60 ? " hour" : " hours");
        if (minutes > 60) return String.format(Locale.US, "%.1f hours", minutes / 60.0);
        return minutes + " minutes";
    }

    /** What the "Time is up" page says. */
    static String message(Context c, String host) {
        String k = keyFor(c, host);
        if (k == null) return "";
        return "Today's time on " + k + " (" + words(limits(c).optInt(k, 0)) + " a day) is used up. It opens again tomorrow.";
    }

    /** Today's use for the dashboard: [{h: site, m: minutes used, l: minutes allowed}]. */
    static synchronized JSONArray report(Context c) {
        JSONArray out = new JSONArray();
        try {
            sync(c);
            JSONObject l = limits(c);
            for (Iterator<String> it = l.keys(); it.hasNext(); ) {
                String k = it.next();
                if (l.optInt(k, 0) <= 0) continue;
                Long used = MS.get(k);
                out.put(new JSONObject().put("h", k).put("m", (int) ((used == null ? 0 : used) / 60_000L)).put("l", l.optInt(k, 0)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
