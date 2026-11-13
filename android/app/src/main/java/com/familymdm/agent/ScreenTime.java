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
        if (EmergencyUnlock.isActive(c)) cfg = null; // "all restrictions off": no daily limit, bedtime, school time or app limits (usage is still counted)
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
        // A different limit on chosen days (weekends), in the phone's own time.
        int weekendLimit = cfg == null ? 0 : cfg.optInt("weekendLimit", 0);
        if (weekendLimit > 0) {
            org.json.JSONArray wd = cfg.optJSONArray("weekendDays");
            int today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1;
            for (int i = 0; wd != null && i < wd.length(); i++) if (wd.optInt(i, -1) == today) dailyLimit = weekendLimit;
        }
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

        // ---- a warning 10 minutes before ----
        int soonDow = mins + 10 >= 24 * 60 ? (dow + 1) % 7 : dow, soonMins = (mins + 10) % (24 * 60);
        if (bed != null && !bedNow) {
            JSONArray d = bed.optJSONArray("days");
            int[] days = new int[d == null ? 0 : d.length()];
            for (int i = 0; i < days.length; i++) days[i] = d.optInt(i, -1);
            if (ScreenTimeRules.inWindow(days, minutesOf(bed.optString("from")), bedTo, soonDow, soonMins)) warnOnce(c, "bed:" + day, "Bedtime in 10 minutes", "The phone locks at " + bed.optString("from") + ".");
        }
        JSONObject schoolSoon = cfg == null ? null : cfg.optJSONObject("schoolTime");
        if (schoolSoon != null) {
            JSONArray d = schoolSoon.optJSONArray("days");
            int[] days = new int[d == null ? 0 : d.length()];
            for (int i = 0; i < days.length; i++) days[i] = d.optInt(i, -1);
            int sFrom = minutesOf(schoolSoon.optString("from")), sTo = minutesOf(schoolSoon.optString("to"));
            if (!ScreenTimeRules.inWindow(days, sFrom, sTo, dow, mins) && ScreenTimeRules.inWindow(days, sFrom, sTo, soonDow, soonMins))
                warnOnce(c, "school:" + day, "School time in 10 minutes", "Only the basics open from " + schoolSoon.optString("from") + ".");
        }
        if (dailyLimit > 0 && !free && canCount && total < dailyLimit * 60_000L && total >= (dailyLimit - 10) * 60_000L)
            warnOnce(c, "limit:" + day, "10 minutes of screen time left today", "When it is used up, the phone locks until tomorrow. You can ask for more time.");

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
        Set<String> timeUp = ScreenTimeRules.overLimit(used, limits, free);
        Set<String> over = new java.util.HashSet<>(timeUp);
        // School time: during its hours every app is away except the basics and the "never counted" ones.
        JSONObject school = cfg == null ? null : cfg.optJSONObject("schoolTime");
        boolean schoolNow = false;
        if (school != null && !free) {
            JSONArray sd = school.optJSONArray("days");
            int[] sdays = new int[sd == null ? 0 : sd.length()];
            for (int i = 0; i < sdays.length; i++) sdays[i] = sd.optInt(i, -1);
            schoolNow = ScreenTimeRules.inWindow(sdays, minutesOf(school.optString("from")), minutesOf(school.optString("to")), dow, mins);
            JSONObject brk = cfg.optJSONObject("schoolBreak");
            if (schoolNow && brk != null && mins >= minutesOf(brk.optString("from")) && mins < minutesOf(brk.optString("to"))) schoolNow = false; // lunch break
        }
        if (schoolNow) over.addAll(schoolBlocked(c, excluded));
        boolean wasSchool = Agent.prefs(c).getBoolean("schoolNow", false);
        if (schoolNow != wasSchool) {
            Agent.prefs(c).edit().putBoolean("schoolNow", schoolNow).apply();
            Agent.addEvent(c, "restriction", schoolNow ? "School time started: only the basics open" : "School time is over");
        }
        Set<String> was = Agent.getSet(c, "overLimit");
        if (!over.equals(was)) {
            for (String pkg : timeUp) if (!was.contains(pkg)) Agent.addEvent(c, "restriction", labelOf(c, pkg) + " has used its time for today");
            Agent.putSet(c, "overLimit", over);
            PolicyApplier.applyStored(c); // hides the ones over their limit, shows the ones no longer over
        }
    }

    /** Apps put away during school time: every app with an icon, except the basics and the "never counted" ones. */
    private static Set<String> schoolBlocked(Context c, Set<String> neverCounted) {
        Set<String> out = new java.util.HashSet<>();
        android.content.pm.PackageManager pm = c.getPackageManager();
        Set<String> keep = new java.util.HashSet<>(neverCounted);
        keep.add(c.getPackageName());
        for (String action : new String[]{android.content.Intent.ACTION_DIAL, android.content.Intent.ACTION_SENDTO}) {
            android.content.Intent i = new android.content.Intent(action, android.net.Uri.parse(action.equals(android.content.Intent.ACTION_DIAL) ? "tel:1" : "smsto:1"));
            android.content.pm.ResolveInfo r = pm.resolveActivity(i, 0);
            if (r != null && r.activityInfo != null) keep.add(r.activityInfo.packageName);
        }
        android.content.pm.ResolveInfo home = pm.resolveActivity(new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME), 0);
        if (home != null && home.activityInfo != null) keep.add(home.activityInfo.packageName);
        for (String basic : new String[]{"com.android.settings", "com.google.android.contacts", "com.android.contacts", "com.google.android.deskclock",
                "com.android.deskclock", "com.google.android.calculator", "com.android.calculator2", "com.android.emergency", "com.android.phone"}) keep.add(basic);
        android.content.Intent launcher = new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER);
        for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(launcher, 0)) {
            String p = r.activityInfo.packageName;
            if (!keep.contains(p)) out.add(p);
        }
        return out;
    }

    /** A notification on the phone, once per key (kind and day). */
    private static void warnOnce(Context c, String key, String title, String text) {
        android.content.SharedPreferences p = Agent.prefs(c);
        if (p.getBoolean("warned:" + key, false)) return;
        p.edit().putBoolean("warned:" + key, true).apply();
        try {
            android.app.NotificationManager nm = c.getSystemService(android.app.NotificationManager.class);
            NoBadge.install(nm, "timewarn2", "Screen time warnings", android.app.NotificationManager.IMPORTANCE_HIGH, "timewarn");
            nm.notify(44, new android.app.Notification.Builder(c, "timewarn2").setContentTitle(title).setContentText(text)
                    .setStyle(new android.app.Notification.BigTextStyle().bigText(text)).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setAutoCancel(true).build());
        } catch (Exception ignored) {
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
