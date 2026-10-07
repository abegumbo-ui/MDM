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
        Set<String> blocked = list(c);
        if (blocked.isEmpty()) {
            lastEventTime = now;
            return;
        }
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return;
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
        if (blocked.contains(pkg + "/" + cls)) {
            Kiosk.startHome(c);
            Agent.addEvent(c, "restriction", "Blocked app/screen opened (" + pkg + "/" + cls + ") -- sent back to the home screen");
        }
    }
}
