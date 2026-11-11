package com.familymdm.agent;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;

import com.familymdm.agent.sitepolicy.ScreenTimeRules;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Screen time: a daily limit for the whole phone, a bedtime, daily limits for single apps, and the
 * usage report the dashboard shows. The rules are in ScreenTimeRules; this class reads Android's
 * usage events twice a minute and acts on what the rules say.
 *
 *  - Bedtime or the daily limit reached: the same full-screen lock the dashboard's "Lock" uses
 *    (emergency calls and the administrator's code still work), until bedtime ends or midnight.
 *  - An app past its own limit: hidden until midnight, like a blocked app.
 *  - Extra time from the administrator ("addTime") lifts all of it for that many minutes.
 *
 * Needs Android's "usage access" for the Agent. As device owner the Agent asks for it by itself;
 * on phones that refuse, it is one switch under the Agent's Administrator > Security > App access.
 * Without it the limits cannot count anything, and the dashboard is told so.
 */
final class ScreenTime {
    private ScreenTime() {}

    private static final long TICK_MS = 30_000;
    private static final String BY = "screentime";
    private static HandlerThread thread;
    private static Handler handler;
    private static Context app;

    private static final Runnable TICK = new Runnable() {
        @Override
        public void run() {
            try {
                tick(app);
            } catch (Throwable t) {
                android.util.Log.w("MdmScreenTime", "tick failed: " + t);
            }
            if (handler != null) handler.postDelayed(this, TICK_MS);
        }
    };

    static synchronized void start(Context c) {
        if (thread != null) return;
        app = c.getApplicationContext();
        SettingsMenu.tryGrantUsageAccess(app);
        thread = new HandlerThread("mdm-screen-time");
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.post(TICK);
    }

    /** Re-check right now (a new policy arrived, extra time was given). */
    static void soon() {
        Handler h = handler;
        if (h != null) {
            h.removeCallbacks(TICK);
            h.post(TICK);
        }
    }

    private static JSONObject config(Context c) {
        try {
            return new JSONObject(Agent.prefs(c).getString("policy", "{}")).optJSONObject("screenTime");
        } catch (Exception e) {
            return null;
        }
    }

    static boolean lockIsOurs(Context c) {
        return BY.equals(Agent.prefs(c).getString("lockBy", ""));
    }

    /** The administrator gives extra time: nothing is locked for time for this many minutes. */
    static void grant(Context c, int minutes, String by) {
        int m = Math.max(1, Math.min(minutes, 24 * 60));
        Agent.prefs(c).edit().putLong("timeFreeUntil", System.currentTimeMillis() + m * 60_000L).apply();
        Agent.addEvent(c, "lock", m + " minutes of extra time given (" + by + ")");
        if (lockIsOurs(c) && Actions.timedLockActive(c)) Actions.endTimedLock(c);
        soon();
    }

    /** What is sent to the dashboard with each check-in: today's total and the most-used apps. */
    static JSONObject report(Context c) {
        try {
            String s = Agent.prefs(c).getString("usageToday", null);
            return s == null ? null : new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static int minutesOf(String hhmm) {
        try {
            String[] p = hhmm.split(":");
            int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
            return h >= 0 && h < 24 && m >= 0 && m < 60 ? h * 60 + m : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private static void tick(Context c) throws Exception {
        if (c == null || !Agent.isOwner(c)) return;
        Tamper.check(c); // remembers the phone is on (for "was off for ...") and watches the SIM
        Whereabouts.check(c); // location history, when switched on
        JSONObject cfg = config(c);
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        int dow = cal.get(Calendar.DAY_OF_WEEK) - 1;
        int mins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long dayStart = cal.getTimeInMillis();
        String day = String.format(java.util.Locale.US, "%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
        cal.add(Calendar.DAY_OF_MONTH, 1);
        long nextMidnight = cal.getTimeInMillis();

        boolean canCount = Agent.usageAccessOn(c);
        Map<String, Long> used = canCount ? usage(c, dayStart, now) : new HashMap<String, Long>();

        Set<String> excluded = new HashSet<>(PolicyApplier.neverHide(c)); // the home screen, system UI, the Agent itself
        if (cfg != null) {
            JSONArray ex = cfg.optJSONArray("exempt");
            for (int i = 0; ex != null && i < ex.length(); i++) excluded.add(ex.optString(i));
        }
        long total = ScreenTimeRules.total(used, excluded);
        saveReport(c, day, used, excluded, total, canCount);

        boolean free = Agent.prefs(c).getLong("timeFreeUntil", 0) > now;
        int dailyLimit = cfg == null ? 0 : cfg.optInt("dailyLimit", 0);
        JSONObject bed = cfg == null ? null : cfg.optJSONObject("bedtime");
        boolean bedNow = false;
        int bedTo = -1;
        if (bed != null) {
            JSONArray d = bed.optJSONArray("days");
            int[] days = new int[d == null ? 0 : d.length()];
            for (int i = 0; i < days.length; i++) days[i] = d.optInt(i, -1);
            bedTo = minutesOf(bed.optString("to"));
            bedNow = ScreenTimeRules.inWindow(days, minutesOf(bed.optString("from")), bedTo, dow, mins);
        }

        // ---- the whole phone ----
        int reason = ScreenTimeRules.reason(bedNow, dailyLimit, total, free);
        boolean ours = lockIsOurs(c);
        boolean locked = Actions.timedLockActive(c);
        if (reason != ScreenTimeRules.NONE) {
            // Never replace a lock the administrator set from the dashboard.
            if (!locked || ours) {
                long until = reason == ScreenTimeRules.BEDTIME ? now + ScreenTimeRules.minutesToEnd(bedTo, mins) * 60_000L : nextMidnight;
                String msg = reason == ScreenTimeRules.BEDTIME
                        ? "Bedtime. The phone opens again at " + bed.optString("to") + "."
                        : "Today's screen time is used up. The phone opens again tomorrow.";
                long had = Agent.prefs(c).getLong("lockUntil", 0);
                if (!locked || Math.abs(had - until) > 90_000L) {
                    Agent.prefs(c).edit().putLong("lockUntil", until).putString("lockMsg", msg).putString("lockBy", BY).apply();
                    if (!locked) Agent.addEvent(c, "lock", reason == ScreenTimeRules.BEDTIME ? "Locked for bedtime" : "Locked: daily screen time used up");
                }
                if (!LockActivity.alive) Actions.startTimedLock(c);
            }
        } else if (ours) {
            if (locked) Actions.endTimedLock(c);
            Agent.prefs(c).edit().remove("lockBy").apply();
        }

        // ---- single apps ----
        Map<String, Integer> limits = new HashMap<>();
        JSONObject al = cfg == null ? null : cfg.optJSONObject("appLimits");
        if (al != null) {
            java.util.Iterator<String> it = al.keys();
            while (it.hasNext()) {
                String pkg = it.next();
                limits.put(pkg, al.optInt(pkg, 0));
            }
        }
        Set<String> over = ScreenTimeRules.overLimit(used, limits, free);
        Set<String> was = Agent.getSet(c, "overLimit");
        if (!over.equals(was)) {
            for (String pkg : over) if (!was.contains(pkg)) Agent.addEvent(c, "restriction", labelOf(c, pkg) + " has used its time for today");
            Agent.putSet(c, "overLimit", over);
            PolicyApplier.applyStored(c); // hides the ones over their limit, shows the ones no longer over
        }
    }

    private static Map<String, Long> usage(Context c, long from, long to) {
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return new HashMap<>();
        UsageEvents events = usm.queryEvents(from, to);
        List<Long> times = new ArrayList<>();
        List<String> pkgs = new ArrayList<>();
        List<Integer> types = new ArrayList<>();
        UsageEvents.Event e = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(e);
            int t = e.getEventType();
            if (t == ScreenTimeRules.FOREGROUND || t == ScreenTimeRules.BACKGROUND || t == ScreenTimeRules.SCREEN_OFF
                    || t == ScreenTimeRules.KEYGUARD_SHOWN || t == ScreenTimeRules.SHUTDOWN) {
                times.add(e.getTimeStamp());
                pkgs.add(e.getPackageName());
                types.add(t);
            }
        }
        long[] ta = new long[times.size()];
        int[] ya = new int[times.size()];
        for (int i = 0; i < ta.length; i++) {
            ta[i] = times.get(i);
            ya[i] = types.get(i);
        }
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return ScreenTimeRules.tally(ta, pkgs.toArray(new String[0]), ya, to, pm == null || pm.isInteractive());
    }

    private static String labelOf(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES).loadLabel(pm).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private static void saveReport(Context c, String day, Map<String, Long> used, Set<String> excluded, long total, boolean canCount) throws Exception {
        List<Map.Entry<String, Long>> list = new ArrayList<>();
        for (Map.Entry<String, Long> e : used.entrySet()) {
            if (!PolicyApplier.neverHide(c).contains(e.getKey()) && e.getValue() >= 60_000L) list.add(e);
        }
        java.util.Collections.sort(list, (a, b) -> Long.compare(b.getValue(), a.getValue()));
        JSONArray apps = new JSONArray();
        for (int i = 0; i < list.size() && i < 15; i++) {
            JSONObject o = new JSONObject();
            o.put("p", list.get(i).getKey());
            o.put("l", labelOf(c, list.get(i).getKey()));
            o.put("m", (int) (list.get(i).getValue() / 60_000L));
            apps.put(o);
        }
        JSONObject r = new JSONObject();
        r.put("day", day);
        r.put("total", (int) (total / 60_000L));
        r.put("apps", apps);
        r.put("ok", canCount); // false: the Agent has no usage access, so nothing can be counted
        long free = Agent.prefs(c).getLong("timeFreeUntil", 0);
        if (free > System.currentTimeMillis()) r.put("extraUntil", free);
        Agent.prefs(c).edit().putString("usageToday", r.toString()).apply();
    }
}
