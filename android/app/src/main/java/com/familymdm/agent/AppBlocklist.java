package com.familymdm.agent;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;

import java.util.Set;

/**
 * A plain list of exact "pkg/ClassName" components, entered by hand on the phone -- not synced to
 * any dashboard, not touching the server at all. Watches the foreground the same way the old
 * Settings watchdog did (polling UsageStatsManager while the screen is on), but reacts to exactly
 * the components in this list, wherever they live. This is deliberately narrower than hiding a
 * whole package: a component inside a shared package like com.google.android.gms gets bounced on
 * its own, without touching anything else that package runs (Maps, Android Auto, Play services).
 * Never a true block -- the real screen is on screen for a moment before this reacts, same
 * limitation the old watchdog always had.
 */
final class AppBlocklist {
    private static final String PREF_KEY = "blockedComponents";
    // Paused entries stay in the main list (so Remove still works normally) but check() skips
    // them -- lets you temporarily walk into a blocked screen yourself (to test something) without
    // deleting and re-adding it afterward.
    private static final String PAUSED_KEY = "blockedComponentsPaused";
    // Every "pkg/ClassName" ever seen in the foreground on this phone, capped so it can't grow
    // forever -- feeds the autocomplete in "Add App" so typing doesn't need the exact string typed
    // out by hand (a real source of the typos that caused double-bounces: a near-miss component
    // string that *almost* matched would never match at all, silently).
    private static final String SEEN_KEY = "seenComponents";
    private static final int SEEN_CAP = 400;
    private static final long POLL_MS = 800;

    private static HandlerThread thread;
    private static Handler handler;
    private static BroadcastReceiver screenReceiver;
    private static volatile boolean screenOn = true;
    private static long lastEventTime;

    private AppBlocklist() {}

    static Set<String> list(Context c) {
        return Agent.getSet(c, PREF_KEY);
    }

    static void add(Context c, String component) {
        Set<String> set = list(c);
        set.add(component);
        Agent.putSet(c, PREF_KEY, set);
    }

    static void remove(Context c, String component) {
        Set<String> set = list(c);
        set.remove(component);
        Agent.putSet(c, PREF_KEY, set);
        Set<String> paused = pausedList(c);
        if (paused.remove(component)) Agent.putSet(c, PAUSED_KEY, paused);
    }

    static Set<String> pausedList(Context c) {
        return Agent.getSet(c, PAUSED_KEY);
    }

    static boolean isPaused(Context c, String component) {
        return pausedList(c).contains(component);
    }

    static void setPaused(Context c, String component, boolean paused) {
        Set<String> set = pausedList(c);
        if (paused) set.add(component); else set.remove(component);
        Agent.putSet(c, PAUSED_KEY, set);
    }

    static Set<String> seen(Context c) {
        return Agent.getSet(c, SEEN_KEY);
    }

    static void recordSeen(Context c, String component) {
        Set<String> set = seen(c);
        if (set.contains(component) || set.size() >= SEEN_CAP) return;
        set.add(component);
        Agent.putSet(c, SEEN_KEY, set);
    }

    static synchronized void start(Context c) {
        if (thread != null) return;
        SettingsMenu.tryGrantUsageAccess(c);
        try {
            Context app = c.getApplicationContext();
            thread = new HandlerThread("mdm-app-blocklist");
            thread.start();
            handler = new Handler(thread.getLooper());
            lastEventTime = System.currentTimeMillis();
            screenReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    screenOn = Intent.ACTION_SCREEN_ON.equals(intent.getAction());
                    if (screenOn) lastEventTime = System.currentTimeMillis();
                }
            };
            IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_SCREEN_OFF);
            app.registerReceiver(screenReceiver, f);
            handler.post(new Poller(app));
        } catch (Exception e) {
            thread = null;
            handler = null;
            screenReceiver = null;
        }
    }

    static synchronized void stop(Context c) {
        if (thread == null) return;
        if (screenReceiver != null) {
            try {
                c.getApplicationContext().unregisterReceiver(screenReceiver);
            } catch (Exception ignored) {
            }
            screenReceiver = null;
        }
        thread.quitSafely();
        thread = null;
        handler = null;
    }

    private static final class Poller implements Runnable {
        private final Context c;

        Poller(Context c) {
            this.c = c;
        }

        @Override
        public void run() {
            try {
                if (screenOn) check(c);
            } catch (Exception ignored) {
            }
            if (handler != null) handler.postDelayed(this, POLL_MS);
        }
    }

    private static void check(Context c) {
        long now = System.currentTimeMillis();
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            lastEventTime = now;
            return;
        }
        UsageEvents events = usm.queryEvents(lastEventTime, now);
        lastEventTime = now;
        String pkg = null, cls = null;
        UsageEvents.Event e = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                pkg = e.getPackageName();
                cls = e.getClassName();
            }
        }
        if (pkg == null || cls == null) return;
        String component = pkg + "/" + cls;
        recordSeen(c, component);
        Set<String> blocked = list(c);
        // When BlockAccessibilityService is on, it already reacts to this exact same event almost
        // instantly with a clean back-press. This poller runs on an 800ms cadence and UsageEvents
        // itself isn't perfectly real-time, so without this check it could independently "discover"
        // the very same already-handled event afterward and bounce a second time on top of it --
        // the double-bounce that was closing Settings entirely instead of just backing out of the
        // blocked screen. Kept fully intact (detection, recording, the bounce itself) as a fallback
        // for whenever Accessibility isn't turned on -- it just steps aside while that's active.
        if (blocked.contains(component) && !pausedList(c).contains(component) && !Agent.accessibilityServiceOn(c)) {
            Kiosk.startHome(c);
            Agent.addEvent(c, "restriction", "Blocked app/screen opened (" + component + ") -- sent back to the home screen");
        }
    }
}
