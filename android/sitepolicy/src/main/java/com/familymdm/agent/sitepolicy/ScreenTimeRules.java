package com.familymdm.agent.sitepolicy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The rules behind screen time: how long each app was really on screen today, whether it is
 * bedtime, and whether the phone or an app has used up its time. Pure Java so it can be checked on
 * a computer (see ScreenTimeRulesCheck); ScreenTime in the Agent feeds it Android's usage events
 * and acts on the answers.
 */
public final class ScreenTimeRules {
    private ScreenTimeRules() {}

    // Android's UsageEvents.Event types that matter here.
    public static final int FOREGROUND = 1, BACKGROUND = 2, SCREEN_OFF = 16, KEYGUARD_SHOWN = 17, SHUTDOWN = 26;

    public static final int NONE = 0, BEDTIME = 1, LIMIT = 2;

    /**
     * Milliseconds each app spent on screen, from a day's events in time order. An app counts from
     * the moment it comes to the front until another app does, it goes to the back, or the screen
     * goes off. Whatever is still in front at `now` counts up to `now`, but only if the screen is on.
     */
    public static Map<String, Long> tally(long[] times, String[] pkgs, int[] types, long now, boolean screenOnNow) {
        Map<String, Long> out = new HashMap<>();
        String current = null;
        long start = 0;
        for (int i = 0; i < times.length; i++) {
            int type = types[i];
            if (type == FOREGROUND) {
                if (current != null && !current.equals(pkgs[i])) {
                    add(out, current, times[i] - start);
                    current = null;
                }
                if (current == null) {
                    current = pkgs[i];
                    start = times[i];
                }
            } else if (type == BACKGROUND) {
                if (current != null && current.equals(pkgs[i])) {
                    add(out, current, times[i] - start);
                    current = null;
                }
            } else if (type == SCREEN_OFF || type == KEYGUARD_SHOWN || type == SHUTDOWN) {
                if (current != null) {
                    add(out, current, times[i] - start);
                    current = null;
                }
            }
        }
        if (current != null && screenOnNow) add(out, current, now - start);
        return out;
    }

    private static void add(Map<String, Long> m, String pkg, long ms) {
        if (pkg == null || ms <= 0) return;
        Long had = m.get(pkg);
        m.put(pkg, (had == null ? 0 : had) + ms);
    }

    /** Total time, leaving out the apps that do not count (the home screen, the Agent, apps marked "doesn't count"). */
    public static long total(Map<String, Long> used, Set<String> excluded) {
        long sum = 0;
        for (Map.Entry<String, Long> e : used.entrySet()) if (!excluded.contains(e.getKey())) sum += e.getValue();
        return sum;
    }

    /**
     * Is this minute inside a daily window? days are 0-6 with Sunday = 0, from/to are minutes after
     * midnight, `dow` and `mins` are now. A window that crosses midnight (22:00 to 07:00) belongs to
     * the day it starts on, so "Sunday" bedtime runs Sunday night into Monday morning.
     */
    public static boolean inWindow(int[] days, int from, int to, int dow, int mins) {
        if (days == null || from < 0 || to < 0 || from == to) return false;
        if (from < to) return mins >= from && mins < to && has(days, dow);
        if (mins >= from) return has(days, dow);
        if (mins < to) return has(days, (dow + 6) % 7);
        return false;
    }

    private static boolean has(int[] days, int d) {
        for (int x : days) if (x == d) return true;
        return false;
    }

    /** Minutes from now until the window's end time (assuming it is inside the window now). */
    public static int minutesToEnd(int to, int mins) {
        int left = to - mins;
        return left > 0 ? left : left + 24 * 60;
    }

    /** Why the whole phone should be locked right now, if at all. Extra time from the administrator lifts both. */
    public static int reason(boolean bedtimeNow, int dailyLimitMinutes, long usedMs, boolean extraTimeNow) {
        if (extraTimeNow) return NONE;
        if (bedtimeNow) return BEDTIME;
        if (dailyLimitMinutes > 0 && usedMs >= dailyLimitMinutes * 60_000L) return LIMIT;
        return NONE;
    }

    /** The apps that have used up their own daily limit. */
    public static Set<String> overLimit(Map<String, Long> used, Map<String, Integer> limitMinutes, boolean extraTimeNow) {
        Set<String> out = new HashSet<>();
        if (extraTimeNow) return out;
        for (Map.Entry<String, Integer> e : limitMinutes.entrySet()) {
            Long u = used.get(e.getKey());
            if (e.getValue() > 0 && u != null && u >= e.getValue() * 60_000L) out.add(e.getKey());
        }
        return out;
    }
}
