package com.familymdm.lockdown;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

/**
 * Keeps Settings down to just Wi-Fi/Network and Connected devices once the phone is locked.
 * Lock task mode (Kiosk.java) has to include com.android.settings in the allowlist for those two
 * areas to be reachable at all -- but lock task only gates which PACKAGE can be in the foreground,
 * not which screen inside one. This watches for Settings bringing any other screen to the
 * foreground and bounces back to the kiosk home the moment it does.
 *
 * This is the same best-effort, "a flash on screen before it reacts" approach the agent app's own
 * AppBlocklist uses, for the same reason: there's no stronger API for reacting to a specific
 * screen opening. The allow-list of keywords below is deliberately broad (matches by substring, not
 * exact class name) since Settings' own internal class names vary by Android version and phone
 * brand -- same reasoning as the agent app's SettingsMenu docs. Not perfect, best available.
 */
public class GuardService extends Service {
    private static final String CHANNEL = "lockdown-guard";
    private static final long POLL_MS = 800;
    private static final String[] SETTINGS_PACKAGES = {
            "com.android.settings", "com.android.settings.intelligence",
    };
    // Substrings of a Settings screen's class name that keep it reachable. Deliberately broad:
    // Wi-Fi, the wider Network/Internet dashboard it lives inside, Bluetooth and the wider
    // Connected-devices dashboard, plus the handful of sub-flows those two naturally lead into
    // (tethering, NFC, Nearby/Quick Share, Android Auto's own Settings entry).
    private static final String[] ALLOWED_KEYWORDS = {
            "wifi", "wireless", "network", "bluetooth", "connecteddevice", "connected_device",
            "tether", "hotspot", "nearby", "quickshare", "nfc", "androidauto", "projection",
    };

    private HandlerThread thread;
    private Handler handler;
    private BroadcastReceiver screenReceiver;
    private volatile boolean screenOn = true;
    private long lastEventTime;

    @Override
    public void onCreate() {
        super.onCreate();
        tryGrantUsageAccess();
        startForeground(2, notification());
        thread = new HandlerThread("lockdown-guard");
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
        registerReceiver(screenReceiver, f);
        handler.post(poller);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Exception ignored) {
            }
        }
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            try {
                if (screenOn) check();
            } catch (Exception ignored) {
            }
            if (handler != null) handler.postDelayed(this, POLL_MS);
        }
    };

    private void check() {
        long now = System.currentTimeMillis();
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
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
        if (pkg == null || cls == null || !isSettingsPackage(pkg)) return;
        if (isRootSettingsScreen(cls) || matchesAllowedKeyword(cls)) return;
        Kiosk.startHome(this);
    }

    private boolean isSettingsPackage(String pkg) {
        for (String p : SETTINGS_PACKAGES) if (p.equals(pkg)) return true;
        return false;
    }

    /** The plain "Settings" list screen you land on when Settings first opens -- always allowed,
     * since bouncing away from it would make Settings impossible to even open. Tapping into
     * anything other than Network/Wi-Fi or Connected devices from there is what then gets bounced.
     * Deliberately does NOT allow Settings' generic "SubSettings" container class: that one class
     * hosts dozens of unrelated fragments (display, sound, anything), not just Wi-Fi/Connected-
     * devices ones, and UsageEvents only reports the Activity class, never which fragment it's
     * showing -- so there's no reliable way to tell those apart here. Blanket-allowing it would
     * have opened a real hole; erring toward occasionally bouncing a legitimate deep Wi-Fi sub-page
     * is the safer mistake to make. */
    private boolean isRootSettingsScreen(String cls) {
        return cls.equals("com.android.settings.Settings") || cls.endsWith(".Settings");
    }

    private boolean matchesAllowedKeyword(String cls) {
        String lower = cls.toLowerCase(java.util.Locale.ROOT);
        for (String k : ALLOWED_KEYWORDS) if (lower.contains(k)) return true;
        return false;
    }

    /** Same device-owner-only best-effort grant the agent app's SettingsMenu uses -- not guaranteed
     * on every OEM, but harmless to try, and without it this service can't see what's in the
     * foreground at all. */
    private void tryGrantUsageAccess() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, AdminReceiver.class);
            if (dpm != null && dpm.isDeviceOwnerApp(getPackageName())) {
                dpm.setPermissionGrantState(admin, getPackageName(), "android.permission.PACKAGE_USAGE_STATS",
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
            }
        } catch (Exception ignored) {
        }
    }

    private Notification notification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Lockdown", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setContentTitle("Locked down")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setPriority(Notification.PRIORITY_MIN)
                .setOngoing(true)
                .build();
    }
}
