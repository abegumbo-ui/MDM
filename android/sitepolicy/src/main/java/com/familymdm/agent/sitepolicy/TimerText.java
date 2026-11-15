package com.familymdm.agent.sitepolicy;

/**
 * Words for "how long is left" on a timer the dashboard started (internet off, web filtering paused, all restrictions off), for the
 * agent's notification on the phone. Pure Java so it is checked on a computer (TimerTextCheck).
 */
public final class TimerText {
    private TimerText() {
    }

    /** "23 min", "1 h 5 min", "2 h": how long is left, rounded up to the next minute. Empty when nothing is left. */
    public static String left(long millis) {
        long m = (millis + 59_999L) / 60_000L;
        if (m <= 0) return "";
        if (m < 60) return m + " min";
        long h = m / 60, r = m % 60;
        return r == 0 ? h + " h" : h + " h " + r + " min";
    }

    /**
     * The notification line while a timer runs, or null when none does:
     * "Internet is off: 23 min left", or two timers joined with " · ". The one that ends first comes first.
     */
    public static String describe(long now, long internetOffUntil, long pauseUntil, long restrictionsOffUntil) {
        String[] names = {"Internet is off", "Web filtering is paused", "All restrictions are off"};
        long[] until = {internetOffUntil, pauseUntil, restrictionsOffUntil};
        boolean[] used = new boolean[3];
        StringBuilder sb = new StringBuilder();
        for (int round = 0; round < 3; round++) {
            int best = -1;
            for (int i = 0; i < 3; i++) if (!used[i] && until[i] > now && (best < 0 || until[i] < until[best])) best = i;
            if (best < 0) break;
            used[best] = true;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(names[best]).append(": ").append(left(until[best] - now)).append(" left");
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
