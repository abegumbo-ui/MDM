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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
    private static volatile Map<String, String[]> serverPatterns = new HashMap<>();
    private static long lastEventTime;
    private static final long LEARN_TIMEOUT_MS = 3 * 60 * 1000;
    private static final String LOCAL_PATTERNS_PREF = "localWatchdogPatterns";

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

    // Display labels, matching SETTINGS_CATEGORIES in src/policy.js -- used by AdminActivity's
    // own local "Learn" card, which has no dashboard to ask for labels.
    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put("google", "Google");
        LABELS.put("network", "Network and internet");
        LABELS.put("connected", "Connected devices");
        LABELS.put("apps", "Apps");
        LABELS.put("notifications", "Notifications");
        LABELS.put("sound", "Sound and vibration");
        LABELS.put("modes", "Modes");
        LABELS.put("personalize", "Personalize");
        LABELS.put("display", "Display");
        LABELS.put("homeLock", "Home and lock screen");
        LABELS.put("gesture", "Gesture");
        LABELS.put("storage", "Storage");
        LABELS.put("battery", "Battery");
        LABELS.put("system", "System");
        LABELS.put("systemUpdates", "System updates");
        LABELS.put("aboutPhone", "About phone");
        LABELS.put("passwords", "Passwords, passkeys and accounts");
        LABELS.put("security", "Security and privacy");
        LABELS.put("location", "Location");
        LABELS.put("digitalWellbeing", "Digital wellbeing and parental controls");
        LABELS.put("safety", "Safety and emergency");
        LABELS.put("accessibility", "Accessibility");
    }

    private SettingsWatchdog() {}

    static Set<String> categoryKeys() {
        return CATEGORIES.keySet();
    }

    static String label(String category) {
        String l = LABELS.get(category);
        return l != null ? l : category;
    }

    /** Called from PolicyApplier.apply() with the category keys the dashboard wants kicked out of. */
    static void setBlocked(Set<String> categories) {
        blocked = new HashSet<>(categories);
    }

    /** Called from PolicyApplier.apply() with extra per-category fragments the dashboard knows about. */
    static void setExtraPatterns(Map<String, String[]> patterns) {
        serverPatterns = new HashMap<>(patterns);
    }

    /** Fragments learned right here on the phone (via AdminActivity's own Learn card), per category. */
    static Map<String, String[]> localPatterns(Context c) {
        Map<String, String[]> out = new HashMap<>();
        try {
            String stored = Agent.prefs(c).getString(LOCAL_PATTERNS_PREF, null);
            if (stored == null) return out;
            JSONObject o = new JSONObject(stored);
            Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                JSONArray arr = o.optJSONArray(k);
                if (arr == null) continue;
                String[] frags = new String[arr.length()];
                for (int i = 0; i < arr.length(); i++) frags[i] = arr.optString(i);
                out.put(k, frags);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /**
     * Server-provided and locally-learned fragments, unioned per category -- never one replacing
     * the other. Re-read fresh every call (SharedPreferences keeps its backing XML in memory, so
     * this is cheap) rather than cached, so a pattern learned locally is never clobbered by the
     * next policy sync overwriting a stale in-memory copy.
     */
    private static Map<String, String[]> mergedPatterns(Context c) {
        Map<String, String[]> merged = new HashMap<>(serverPatterns);
        for (Map.Entry<String, String[]> e : localPatterns(c).entrySet()) {
            String[] serverFrags = merged.get(e.getKey());
            if (serverFrags == null) {
                merged.put(e.getKey(), e.getValue());
            } else {
                Set<String> union = new LinkedHashSet<>(Arrays.asList(serverFrags));
                union.addAll(Arrays.asList(e.getValue()));
                merged.put(e.getKey(), union.toArray(new String[0]));
            }
        }
        return merged;
    }

    /** Learns a fragment for `category` from a captured "pkg/cls" component, on this phone only. */
    static void addLocalPattern(Context c, String category, String fragment) {
        if (category == null || fragment == null) return;
        String frag = fragment.toLowerCase().trim();
        if (frag.isEmpty() || frag.length() > 100) return;
        try {
            JSONObject o;
            String stored = Agent.prefs(c).getString(LOCAL_PATTERNS_PREF, null);
            o = stored != null ? new JSONObject(stored) : new JSONObject();
            JSONArray existing = o.optJSONArray(category);
            Set<String> frags = new LinkedHashSet<>();
            if (existing != null) for (int i = 0; i < existing.length(); i++) frags.add(existing.optString(i));
            frags.add(frag);
            List<String> capped = new ArrayList<>(frags);
            if (capped.size() > 20) capped = capped.subList(capped.size() - 20, capped.size());
            o.put(category, new JSONArray(capped));
            Agent.prefs(c).edit().putString(LOCAL_PATTERNS_PREF, o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Clears every locally-learned fragment for one category (not the server-provided ones). */
    static void clearLocalPatterns(Context c, String category) {
        try {
            String stored = Agent.prefs(c).getString(LOCAL_PATTERNS_PREF, null);
            if (stored == null) return;
            JSONObject o = new JSONObject(stored);
            o.remove(category);
            Agent.prefs(c).edit().putString(LOCAL_PATTERNS_PREF, o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /**
     * Raw diagnostic, independent of the category-learning workflow entirely: whatever was last in
     * the foreground, even if it doesn't look settings-related, plus how many foreground events were
     * seen at all. Answers the one question "Learn" timing out can't: is detection working on this
     * phone at all, or is it the category matching that's the problem.
     */
    static String debugCurrentForeground(Context c) {
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return "This phone has no usage-stats service -- the watchdog cannot work here at all.";
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(now - 15 * 60 * 1000, now);
        String pkg = null, cls = null;
        int count = 0;
        UsageEvents.Event e = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                pkg = e.getPackageName();
                cls = e.getClassName();
                count++;
            }
        }
        if (pkg == null) {
            return "No foreground-app events seen in the last 15 minutes at all. Usage access likely isn't "
                    + "actually granted on this phone, whatever the automatic device-owner grant is supposed to do.";
        }
        return "Last foreground app seen: " + pkg + "/" + cls + " (" + count + " foreground event(s) in the last 15 minutes).";
    }

    /**
     * What was last on screen in Settings, looking back 15 minutes -- for the normal case: already
     * sitting on the target Settings screen, then pressing Learn (dashboard flow), or coming back
     * from Settings into MDM Agent itself (local, on-phone flow). That second case is why this
     * tracks the most recent settings-ish event specifically, not just the single most recent
     * foreground event of any kind: switching back into MDM Agent to finish the capture is itself
     * a foreground event, for com.familymdm.agent, and it would otherwise be the last one seen,
     * overwriting the real answer with "myself" every single time. Returns "pkg/cls", or null if
     * nothing settings-ish happened in the last 15 minutes at all.
     */
    static String captureNow(Context c) {
        try {
            UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;
            long now = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(now - 15 * 60 * 1000, now);
            String pkg = null, cls = null;
            UsageEvents.Event e = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                if (e.getEventType() != UsageEvents.Event.MOVE_TO_FOREGROUND) continue;
                String p = e.getPackageName();
                boolean settingsish = p.equals("com.android.settings") || p.toLowerCase().contains("settings")
                        || p.startsWith("com.google.android.apps.wellbeing") || p.equals("com.google.android.gms");
                if (settingsish) {
                    pkg = p;
                    cls = e.getClassName();
                }
            }
            return pkg != null ? pkg + "/" + cls : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fallback for the other order: Learn pressed first, then the person navigates. Arms a one-shot
     * capture -- the next settings-ish screen that comes to the foreground is reported back as a
     * candidate for this category, tagged with the queued command's id so the result reaches the
     * same place any other command's result would. Expires on its own if nothing opens in time, so
     * a forgotten "Learn" tap doesn't leave this armed forever.
     */
    static void startLearn(Context c, String category, String commandId) {
        Agent.prefs(c).edit()
                .putString("learnCategory", category)
                .putString("learnCommandId", commandId)
                .putLong("learnExpiresAt", System.currentTimeMillis() + LEARN_TIMEOUT_MS)
                .apply();
    }

    /**
     * A device owner is often assumed to get Usage access automatically, with no prompt -- that
     * turns out not to be reliable across OEMs (confirmed against public docs: PACKAGE_USAGE_STATS
     * is a "special" app-op permission, not a normal runtime one, and nothing guarantees a device
     * owner gets it for free). setPermissionGrantState() is the one API a device owner has for
     * trying to grant it anyway; some Android/OEM combinations honor it, some silently don't. Either
     * way this can't hurt -- if it's ignored, the phone still needs it granted by hand under
     * Settings > Apps > Special access > Usage access, same as it always did.
     */
    private static void tryGrantUsageAccess(Context c) {
        try {
            android.app.admin.DevicePolicyManager dpm = Agent.dpm(c);
            android.content.ComponentName admin = Agent.admin(c);
            if (dpm != null && admin != null && dpm.isDeviceOwnerApp(c.getPackageName())) {
                dpm.setPermissionGrantState(admin, c.getPackageName(), "android.permission.PACKAGE_USAGE_STATS",
                        android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
            }
        } catch (Exception ignored) {
        }
    }

    static synchronized void start(Context c) {
        if (thread != null) return;
        tryGrantUsageAccess(c);
        // A failure anywhere in here must never crash the whole agent service over a watchdog
        // problem -- and silently doing nothing would look identical to everything simply being
        // allowed, with nothing in the log to tell the two apart. So: catch everything, and leave
        // a one-time confirmation either way.
        try {
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
            Agent.addEvent(c, "watchdog", "Settings watchdog started");
        } catch (Exception e) {
            thread = null;
            handler = null;
            screenReceiver = null;
            Agent.addEvent(c, "error", "Settings watchdog could not start: " + e);
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
                reportError(c, "Settings watchdog check failed: " + e);
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
        String learnCategory = Agent.prefs(c).getString("learnCategory", null);
        long learnExpires = Agent.prefs(c).getLong("learnExpiresAt", 0);
        boolean learning = learnCategory != null && now < learnExpires;
        if (learnCategory != null && !learning) finishLearn(c, null); // armed, but nothing opened in time

        if (blocked.isEmpty() && !learning) {
            lastEventTime = now;
            return;
        }
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            reportError(c, "Settings watchdog: this phone has no usage-stats service, so it cannot work here.");
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
        // Loosely "settings-shaped", not an exact match to "com.android.settings" -- an OEM could
        // ship its own Settings package under a different name, and silently dropping every event
        // whose package isn't exactly right would hide that possibility completely, same as the
        // category match below already does for an unrecognized screen within a recognized package.
        boolean settingsish = pkg.equals("com.android.settings") || pkg.toLowerCase().contains("settings")
                || pkg.startsWith("com.google.android.apps.wellbeing") || pkg.equals("com.google.android.gms");
        if (!settingsish) return;

        if (learning) {
            finishLearn(c, pkg + "/" + cls);
            return;
        }

        String lower = cls.toLowerCase();
        String matched = firstMatch(lower, CATEGORIES);
        if (matched == null) matched = firstMatch(lower, mergedPatterns(c));
        if (matched == null) {
            // Unrecognized Settings screen -- logged at most once every 10 minutes per class, so the
            // map above can be refined for this phone without flooding the log over one visit.
            String key = "seenSettings:" + pkg + "/" + cls;
            long seen = Agent.prefs(c).getLong(key, 0);
            if (now - seen > 10 * 60 * 1000) {
                Agent.prefs(c).edit().putLong(key, now).apply();
                Agent.addEvent(c, "watchdog", "Settings screen opened, not in any blocked category: " + pkg + "/" + cls);
            }
            return;
        }
        if (blocked.contains(matched)) {
            Kiosk.startHome(c);
            Agent.addEvent(c, "restriction", "Blocked Settings category \"" + matched + "\" was opened -- sent back to the home screen");
        }
    }

    private static String firstMatch(String lowerClassName, Map<String, String[]> table) {
        for (Map.Entry<String, String[]> entry : table.entrySet()) {
            for (String frag : entry.getValue()) {
                if (lowerClassName.contains(frag)) return entry.getKey();
            }
        }
        return null;
    }

    /** Ends a "Learn" capture -- component is the pkg/cls seen, or null if the window expired first. */
    private static void finishLearn(Context c, String component) {
        String category = Agent.prefs(c).getString("learnCategory", null);
        String commandId = Agent.prefs(c).getString("learnCommandId", null);
        Agent.prefs(c).edit().remove("learnCategory").remove("learnCommandId").remove("learnExpiresAt").apply();
        if (category == null) return;
        String msg;
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("category", category);
            if (component != null) o.put("component", component);
            msg = o.toString();
        } catch (Exception ex) {
            msg = component;
        }
        if (commandId != null) Agent.addResult(c, commandId, "learnSettings", component != null, msg);
        Agent.addEvent(c, "watchdog", "Learn \"" + category + "\": " + (component != null ? "saw " + component : "nothing opened in time"));
    }
}
