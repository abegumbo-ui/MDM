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
        if (packageReceiver == null) registerPackageReceiver();
        if (thread == null || !thread.isAlive()) {
            running = true;
            thread = new Thread(this::loop, "mdm-agent");
            thread.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (instance == this) instance = null;
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
                sleepSeconds = syncOnce();
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
                Thread.sleep(Math.max(15, sleepSeconds) * 1000L);
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
        return o;
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
        body.put("overrides", Agent.getOverrides(this));
        body.put("overridesRev", Agent.overridesRev(this));
        List<String> iconsSent = new ArrayList<>();
        JSONObject icons = PolicyApplier.collectIcons(this, packages, 8, iconsSent);
        if (icons.length() > 0) body.put("icons", icons);

        JSONObject reply = Api.post(server + "/agent/sync", body, token);
        Agent.dropResults(this, results.length());
        Agent.dropEvents(this, events.length());
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
        }

        JSONArray commands = reply.optJSONArray("commands");
        if (commands != null) {
            for (int i = 0; i < commands.length(); i++) runCommand(commands.getJSONObject(i));
        }
        return reply.optLong("pollSeconds", 60);
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
                    msg = Installer.installFromUrl(this, args.optString("url"));
                    break;
                case "uninstall":
                    msg = Installer.uninstall(this, args.getString("packageName"));
                    break;
                case "sync":
                    break;
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
