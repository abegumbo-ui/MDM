package com.familymdm.agent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Foreground service: checks in with the dashboard, applies policy, runs commands. */
public class AgentService extends Service {
    private static final String TAG = "MdmAgent";
    private volatile boolean running;
    private Thread thread;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, notification());
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
        if (thread != null) thread.interrupt();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
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
                Thread.sleep(Math.max(15, sleepSeconds) * 1000L);
            } catch (InterruptedException e) {
                return;
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
        body.put("packages", PolicyApplier.collectPackages(this));
        body.put("results", results);

        JSONObject reply = Api.post(server + "/agent/sync", body, token);
        Agent.dropResults(this, results.length());

        JSONObject policy = reply.optJSONObject("policy");
        if (policy != null) PolicyApplier.apply(this, policy);

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
                    dpm.lockNow();
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
                    Agent.addResult(this, id, type, true, "released; the app can now be uninstalled");
                    flushResults();
                    PolicyApplier.release(this);
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
            msg = e.toString();
        }
        Agent.addResult(this, id, type, ok, msg);
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
