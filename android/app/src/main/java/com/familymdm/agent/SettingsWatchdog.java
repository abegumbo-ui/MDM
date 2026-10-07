package com.familymdm.agent;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Kicks the person back to the home screen the moment a blocked Settings screen comes to the
 * foreground. Android has no API letting one app refuse to let another app's screen open at all
 * -- this watches for it happening (checking roughly every 800ms while the screen is on, using the
 * device owner's automatic access to usage stats, no Settings prompt needed) and bounces out right
 * after, which in practice means a flash of the blocked screen for well under a second, not true
 * prevention.
 *
 * Settings does not expose which of its sections is open, only the Android component on screen,
 * and the manufacturer decides that component's name -- there is no stable public list of them.
 * CATEGORIES below is a best-effort map of the names stock Android and common OEM builds are known
 * to use. Every Settings screen that opens without matching a category is still logged once (see
 * the Phone log) instead of silently ignored, so the map can be corrected for this specific phone
 * over time rather than guessed blind.
 */
final class SettingsWatchdog {
    private static final String TAG = "MdmAgent";
    private static final long POLL_MS = 800;

    private static HandlerThread thread;
    private static Handler handler;
    private static BroadcastReceiver screenReceiver;
    private static volatile boolean screenOn = true;
    private static volatile Set<String> blocked = new HashSet<>();
    private static long lastEventTime;

    // category key -> lowercase fragments of a Settings activity's class name known to open it.
    // A screen matches if any fragment is contained in its (lowercased) class name.
    private static final Map<String, String[]> CATEGORIES = new LinkedHashMap<>();
    static {
        CATEGORIES.put("google", new String[]{"googlesettingsactivity", "googlesettings"});
        CATEGORIES.put("network", new String[]{"networkdashboard", "wifisettings", "datausage"});
        CATEGORIES.put("connected", new String[]{"connecteddevice", "bluetoothsettings", "nfcsettings"});
        CATEGORIES.put("apps", new String[]{"appdashboard", "manageapplications", "allapplications"});
        CATEGORIES.put("notifications", new String[]{"notificationstation", "configurenotification", "notificationsettings"});
        CATEGORIES.put("sound", new String[]{"soundsettings"});
        CATEGORIES.put("modes", new String[]{"zenmode", "donotdisturb", "modessettings"});
        CATEGORIES.put("personalize", new String[]{"personalize", "themes"});
        CATEGORIES.put("display", new String[]{"displaysettings"});
        CATEGORIES.put("homeLock", new String[]{"locksettings", "lockscreen", "wallpaper"});
        CATEGORIES.put("gesture", new String[]{"gesture", "motoactions", "moveactions"});
        CATEGORIES.put("storage", new String[]{"storagedashboard", "storagesettings"});
        CATEGORIES.put("battery", new String[]{"powerusagesummary", "batterysaver"});
        CATEGORIES.put("system", new String[]{"systemdashboard"});
        CATEGORIES.put("systemUpdates", new String[]{"systemupdate", "firmwareversion"});
        CATEGORIES.put("aboutPhone", new String[]{"deviceinfosettings", "deviceinfo", "mydevice"});
        CATEGORIES.put("passwords", new String[]{"userandaccount", "accountsettings", "credentialsettings", "autofillsettings"});
        CATEGORIES.put("security", new String[]{"securitydashboard", "securitysettings", "privacydashboard"});
        CATEGORIES.put("location", new String[]{"locationsettings"});
        CATEGORIES.put("digitalWellbeing", new String[]{"wellbeing"});
        CATEGORIES.put("safety", new String[]{"emergency", "safetycenter"});
        CATEGORIES.put("accessibility", new String[]{"accessibilitysettings"});
    }

    private SettingsWatchdog() {}

    static Set<String> categoryKeys() {
        return CATEGORIES.keySet();
    }

    /** Called from PolicyApplier.apply() with the category keys the dashboard wants kicked out of. */
    static void setBlocked(Set<String> categories) {
        blocked = new HashSet<>(categories);
    }

    static synchronized void start(Context c) {
        if (thread != null) return;
        Context app = c.getApplicationContext();
        thread = new HandlerThread("mdm-settings-watchdog");
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
                clearError(c);
            } catch (SecurityException e) {
                // Silently failing here would leave the whole feature looking broken with no clue why.
                // This is the one failure mode that isn't "the OEM named this screen something else" --
                // it means the phone never granted usage access, so nothing below even gets tried.
                reportError(c, "Settings watchdog can't see which screen is open: " + e.getMessage()
                        + ". A device owner should get usage access automatically; if this keeps showing, "
                        + "it may need to be granted by hand under Settings > Apps > Special access > Usage access.");
            } catch (Exception e) {
                Log.w(TAG, "settings watchdog check failed: " + e);
            }
            if (handler != null) handler.postDelayed(this, POLL_MS);
        }
    }

    /** At most once every 30 minutes, so a permission problem shows up without flooding the log. */
    private static void reportError(Context c, String msg) {
        long last = Agent.prefs(c).getLong("watchdogErrorAt", 0);
        long now = System.currentTimeMillis();
        if (now - last > 30 * 60 * 1000) {
            Agent.prefs(c).edit().putLong("watchdogErrorAt", now).apply();
            Agent.addEvent(c, "error", msg);
        }
    }

    private static void clearError(Context c) {
        if (Agent.prefs(c).getLong("watchdogErrorAt", 0) != 0) Agent.prefs(c).edit().remove("watchdogErrorAt").apply();
    }

    private static void check(Context c) {
        long now = System.currentTimeMillis();
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
        if (!pkg.equals("com.android.settings") && !pkg.startsWith("com.google.android.apps.wellbeing") && !pkg.equals("com.google.android.gms")) return;

        String lower = cls.toLowerCase();
        String matched = null;
        for (Map.Entry<String, String[]> entry : CATEGORIES.entrySet()) {
            for (String frag : entry.getValue()) {
                if (lower.contains(frag)) {
                    matched = entry.getKey();
                    break;
                }
            }
            if (matched != null) break;
        }
        if (matched == null) {
            // Unrecognized Settings screen -- logged at most once every 10 minutes per class, so the
            // map above can be refined for this phone without flooding the log over one visit.
            String key = "seenSettings:" + cls;
            long seen = Agent.prefs(c).getLong(key, 0);
            if (now - seen > 10 * 60 * 1000) {
                Agent.prefs(c).edit().putLong(key, now).apply();
                Agent.addEvent(c, "watchdog", "Settings screen opened, not in any blocked category: " + cls);
            }
            return;
        }
        if (blocked.contains(matched)) {
            Kiosk.startHome(c);
            Agent.addEvent(c, "restriction", "Blocked Settings category \"" + matched + "\" was opened -- sent back to the home screen");
        }
    }
}
