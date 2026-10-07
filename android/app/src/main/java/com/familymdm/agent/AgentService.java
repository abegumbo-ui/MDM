package com.familymdm.agent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Foreground service: checks in with the dashboard, applies policy, runs commands. */
public class AgentService extends Service {
    private static final String TAG = "MdmAgent";
    private static volatile AgentService instance;
    private volatile boolean running;
    private volatile boolean ranCommands;
    private Thread thread;
    private BroadcastReceiver packageReceiver;

    /** Ask the running service to check in right now (used by the on-phone admin panel). */
    static void requestSync() {
        AgentService s = instance;
        if (s != null && s.thread != null) s.thread.interrupt();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, notification());
        instance = this;
        int build = Updater.currentBuild(this);
        int before = Agent.prefs(this).getInt("knownBuild", 0);
        if (before != 0 && before != build) Agent.addEvent(this, "update", "Agent updated: build " + before + " to build " + build);
        Agent.prefs(this).edit().putInt("knownBuild", build).apply();
        if (packageReceiver == null) registerPackageReceiver();
        SettingsWatchdog.start(this);
        if (thread == null || !thread.isAlive()) {
            running = true;
            thread = new Thread(this::loop, "mdm-agent");
            thread.start();
            Kiosk.ensureHome(this); // after a reboot or restart, bring the home screen back
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (instance == this) instance = null;
        SettingsWatchdog.stop(this);
        if (packageReceiver != null) {
            try {
                unregisterReceiver(packageReceiver);
            } catch (Exception ignored) {
            }
            packageReceiver = null;
        }
        if (thread != null) thread.interrupt();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Reacts within seconds when an app is installed or removed, so new apps can be held for approval quickly. */
    private void registerPackageReceiver() {
        packageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getData() == null || intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
                String pkg = intent.getData().getSchemeSpecificPart();
                Long touched = Agent.TOUCHED.get(pkg);
                if (touched != null && System.currentTimeMillis() - touched < 20000) return; // our own hide/show
                boolean added = Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction());
                Agent.addEvent(context, "app", (added ? "App installed: " : "App removed: ") + pkg);
                if (added) PolicyApplier.holdIfNew(context, pkg);
                if (thread != null) thread.interrupt();
            }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addDataScheme("package");
        registerReceiver(packageReceiver, f);
    }

    private Notification notification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("agent", "MDM agent", NotificationManager.IMPORTANCE_LOW));
        return new Notification.Builder(this, "agent")
                .setContentTitle("MDM agent")
                .setContentText("Connected to your dashboard")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build();
    }

    private void loop() {
        while (running) {
            long sleepSeconds = 60;
            try {
                sleepSeconds = Agent.syncPaused(this) ? 1800 : Agent.standalone(this) ? standaloneOnce() : syncOnce();
                if (ranCommands) sleepSeconds = 3; // report what a command did right away
            } catch (Api.HttpException e) {
                Log.w(TAG, "sync failed: " + e.getMessage());
                if (e.code == 401) {
                    // Dashboard no longer knows this device (record removed). Stop checking in.
                    Agent.prefs(this).edit().remove("token").apply();
                    stopSelf();
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "sync failed: " + e);
            }
            try {
                PolicyApplier.applyStored(this);
            } catch (Exception e) {
                Log.w(TAG, "stored policy failed: " + e);
            }
            try {
                Actions.ensureTimedLock(this);
            } catch (Exception e) {
                Log.w(TAG, "timed lock check failed: " + e);
            }
            try {
                Thread.sleep(Math.max(3, sleepSeconds) * 1000L);
            } catch (InterruptedException e) {
                if (!running) return; // otherwise this was a wake-up call: check in now
            }
        }
    }

    private JSONObject info() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("manufacturer", Build.MANUFACTURER);
        o.put("model", Build.MODEL);
        o.put("android", Build.VERSION.RELEASE);
        o.put("sdk", Build.VERSION.SDK_INT);
        o.put("agent", "0.1.0");
        o.put("deviceOwner", Agent.isOwner(this));
        o.put("screenLock", Actions.hasScreenLock(this));
        o.put("pinControl", Actions.pinControlActive(this));
        o.put("masterSet", Master.isSet(this));
        o.put("restrictions", new JSONArray(Agent.getSet(this, "restrictions")));
        o.put("hiddenCount", Agent.getSet(this, "hidden").size());
        o.put("versionCode", Updater.currentBuild(this));
        o.put("securityPatch", Build.VERSION.SECURITY_PATCH);
        o.put("bootloader", Telemetry.bootloader(this));
        o.put("frpSupported", Build.VERSION.SDK_INT >= 30);
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                android.app.admin.FactoryResetProtectionPolicy frp =
                        Agent.dpm(this).getFactoryResetProtectionPolicy(Agent.admin(this));
                o.put("frpAccounts", frp == null ? 0 : frp.getFactoryResetProtectionAccounts().size());
                o.put("frpEnabled", frp != null && frp.isFactoryResetProtectionEnabled());
            } catch (Exception e) {
                o.put("frpAccounts", 0);
            }
        }
        o.put("kiosk", Kiosk.active(this));
        o.put("kioskPaused", Kiosk.paused(this));
        JSONObject battery = Telemetry.battery(this);
        if (battery != null) o.put("battery", battery);
        o.put("wifi", Telemetry.wifi(this));
        long lockUntil = Agent.prefs(this).getLong("lockUntil", 0);
        if (lockUntil > System.currentTimeMillis()) {
            JSONObject lock = new JSONObject();
            lock.put("until", lockUntil);
            lock.put("msg", Agent.prefs(this).getString("lockMsg", ""));
            o.put("lock", lock);
        }
        long browseUntil = Agent.prefs(this).getLong("browseUntil", 0);
        if (browseUntil > System.currentTimeMillis()) o.put("browseUntil", browseUntil);
        return o;
    }

    /** Offline mode: no server. Work out the policy from the phone's own settings and apply it. */
    private long standaloneOnce() throws Exception {
        LocalConfig.refresh(this);
        Agent.prefs(this).edit().putLong("lastSync", System.currentTimeMillis()).apply();
        return 60;
    }

    /** One check-in. Returns how many seconds to wait before the next one. */
    private long syncOnce() throws Exception {
        String server = Agent.prefs(this).getString("server", null);
        String token = Agent.prefs(this).getString("token", null);
        if (server == null || token == null) {
            stopSelf();
            return 60;
        }
        JSONArray results = Agent.peekResults(this);
        JSONObject body = new JSONObject();
        body.put("info", info());
        JSONArray packages = PolicyApplier.collectPackages(this);
        body.put("packages", packages);
        body.put("results", results);
        JSONArray events = Agent.peekEvents(this);
        body.put("events", events);
        JSONArray siteRequests = Agent.peekSiteRequests(this);
        body.put("siteRequests", siteRequests);
        JSONArray messages = Agent.peekMessages(this);
        body.put("messages", messages);
        body.put("overrides", Agent.getOverrides(this));
        body.put("overridesRev", Agent.overridesRev(this));
        body.put("fallbackCode", Agent.fallbackCode(this));
        body.put("syncPaused", Agent.syncPaused(this));
        long hsRev = Agent.prefs(this).getLong("homeScreenRev", 0);
        if (hsRev > 0) {
            body.put("homeScreenRev", hsRev);
            body.put("homeScreenValue", Agent.prefs(this).getBoolean("homeScreenValue", false));
        }
        List<String> iconsSent = new ArrayList<>();
        JSONObject icons = PolicyApplier.collectIcons(this, packages, 8, iconsSent);
        if (icons.length() > 0) body.put("icons", icons);

        JSONObject reply = Api.post(server + "/agent/sync", body, token);
        Agent.dropResults(this, results.length());
        Agent.dropEvents(this, events.length());
        Agent.dropSiteRequests(this, siteRequests.length());
        Agent.dropMessages(this, messages.length());
        Agent.adoptOverrides(this, reply.optJSONObject("overrides"), reply.optLong("overridesRev", 0));
        Master.store(this, reply.optJSONObject("master"));
        if (!iconsSent.isEmpty()) {
            Set<String> sent = Agent.getSet(this, "icons_sent");
            sent.addAll(iconsSent);
            Agent.putSet(this, "icons_sent", sent);
        }
        Agent.prefs(this).edit().putLong("lastSync", System.currentTimeMillis()).apply();

        JSONObject policy = reply.optJSONObject("policy");
        if (policy != null) {
            Agent.prefs(this).edit().putString("policy", policy.toString()).apply();
            PolicyApplier.apply(this, policy);
            syncCustomIcons(server, token, policy.optJSONObject("customIcons"));
            Updater.maybeAutoUpdate(this, policy);
        }

        syncLogo(server, token, reply.optLong("logoRev", 0));

        JSONArray commands = reply.optJSONArray("commands");
        ranCommands = commands != null && commands.length() > 0;
        if (commands != null) {
            for (int i = 0; i < commands.length(); i++) runCommand(commands.getJSONObject(i));
        }
        return reply.optLong("pollSeconds", 60);
    }

    /** Custom app icons for the home screen: download new or changed ones, drop removed ones. */
    private void syncCustomIcons(String server, String token, JSONObject icons) {
        if (icons == null) return;
        try {
            int downloaded = 0;
            java.util.Iterator<String> keys = icons.keys();
            java.util.Set<String> wanted = new java.util.HashSet<>();
            while (keys.hasNext()) {
                String pkg = keys.next();
                wanted.add(pkg);
                long rev = icons.optLong(pkg, 0);
                if (rev == Agent.prefs(this).getLong("iconRev:" + pkg, 0) || downloaded >= 20) continue;
                byte[] png = Api.getBytes(server + "/agent/icon/" + pkg, token);
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(Agent.iconFile(this, pkg))) {
                    out.write(png);
                }
                Agent.prefs(this).edit().putLong("iconRev:" + pkg, rev).apply();
                downloaded++;
            }
            java.io.File[] files = new java.io.File(getFilesDir(), "icons").listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    String name = f.getName();
                    String pkg = name.endsWith(".png") ? name.substring(0, name.length() - 4) : name;
                    if (!wanted.contains(pkg)) {
                        //noinspection ResultOfMethodCallIgnored
                        f.delete();
                        Agent.prefs(this).edit().remove("iconRev:" + pkg).apply();
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "icon sync failed: " + e);
        }
    }

    /** Downloads (or removes) the administrator's logo when its version changed. */
    private void syncLogo(String server, String token, long rev) {
        if (rev == Agent.prefs(this).getLong("logoRev", 0)) return;
        try {
            if (rev == 0) {
                //noinspection ResultOfMethodCallIgnored
                Agent.logoFile(this).delete();
            } else {
                byte[] png = Api.getBytes(server + "/agent/logo", token);
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(Agent.logoFile(this))) {
                    out.write(png);
                }
            }
            Agent.prefs(this).edit().putLong("logoRev", rev).apply();
        } catch (Exception e) {
            Log.w(TAG, "logo sync failed: " + e);
        }
    }

    private void runCommand(JSONObject cmd) {
        String id = cmd.optString("id");
        String type = cmd.optString("type");
        JSONObject args = cmd.optJSONObject("args");
        if (args == null) args = new JSONObject();
        DevicePolicyManager dpm = Agent.dpm(this);
        ComponentName admin = Agent.admin(this);
        String msg = "done";
        boolean ok = true;
        try {
            switch (type) {
                case "lock":
                    msg = Actions.lock(this, args.optInt("minutes", 0), args.optString("message", ""));
                    break;
                case "unlock":
                    msg = Actions.unlock(this);
                    break;
                case "addWifi":
                    msg = Actions.addWifi(this, args.optString("ssid"), args.optString("password"));
                    break;
                case "setPin":
                    msg = Actions.setPin(this, args.optString("pin"));
                    break;
                case "clearPin":
                    msg = Actions.clearPin(this);
                    break;
                case "reboot":
                    Agent.addResult(this, id, type, true, "rebooting");
                    flushResults();
                    dpm.reboot(admin);
                    return;
                case "wipe":
                    Agent.addResult(this, id, type, true, "wiping");
                    flushResults();
                    dpm.wipeData(0);
                    return;
                case "release":
                    boolean remove = args.optBoolean("uninstall", false);
                    Agent.addResult(this, id, type, true, remove
                            ? "released; tap the notification on the phone to finish removing the agent"
                            : "released; the agent is no longer device owner and can be uninstalled");
                    flushResults();
                    PolicyApplier.release(this);
                    if (remove) notifyRemove();
                    running = false;
                    stopSelf();
                    return;
                case "install":
                    if (args.has("apkId")) {
                        String server = Agent.prefs(this).getString("server", "");
                        String token = Agent.prefs(this).getString("token", "");
                        msg = Installer.installFromUrl(this, server + "/agent/apk/" + args.getString("apkId"), token);
                    } else {
                        msg = Installer.installFromUrl(this, args.optString("url"));
                    }
                    break;
                case "resetAppCode":
                    msg = "no longer used";
                    break;
                case "updateAgent":
                    msg = Updater.update(this, false);
                    break;
                case "uninstall":
                    msg = Installer.uninstall(this, args.getString("packageName"));
                    break;
                case "sync":
                    break;
                case "listSystemApps":
                    msg = PolicyApplier.collectSystemPackages(this).toString();
                    break;
                case "learnSettings":
                    // No result here -- SettingsWatchdog reports back on its own once the next
                    // Settings screen actually opens (or the capture window times out).
                    SettingsWatchdog.startLearn(this, args.optString("category"), id);
                    return;
                default:
                    ok = false;
                    msg = "unknown command";
            }
        } catch (Exception e) {
            ok = false;
            msg = e.getMessage() == null ? e.toString() : e.getMessage();
        }
        Agent.addResult(this, id, type, ok, msg);
        Agent.addEvent(this, ok ? "command" : "error", type + (ok ? ": " : " failed: ") + msg);
    }

    /** After release the phone must confirm the uninstall itself; offer it as a tap-to-finish notification. */
    private void notifyRemove() {
        Intent del = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 2, del, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "agent")
                .setContentTitle("Finish removing MDM Agent")
                .setContentText("Tap to uninstall it")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        getSystemService(NotificationManager.class).notify(2, n);
        try {
            startActivity(del);
        } catch (Exception ignored) {
        }
    }

    /** Best-effort immediate report, used before actions that interrupt the app (reboot, wipe, release). */
    private void flushResults() {
        try {
            String server = Agent.prefs(this).getString("server", null);
            String token = Agent.prefs(this).getString("token", null);
            JSONArray results = Agent.peekResults(this);
            JSONObject body = new JSONObject();
            body.put("results", results);
            Api.post(server + "/agent/sync", body, token);
            Agent.dropResults(this, results.length());
        } catch (Exception e) {
            Log.w(TAG, "flush failed: " + e);
        }
    }
}
