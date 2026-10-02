package com.familymdm.agent;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Shared helpers: preferences, device-policy handles, and the queue of command results. */
final class Agent {
    private Agent() {}

    /** Packages this agent just changed, so its own hide/show broadcasts aren't logged as user activity. */
    static final Map<String, Long> TOUCHED = new ConcurrentHashMap<>();

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("agent", Context.MODE_PRIVATE);
    }

    static ComponentName admin(Context c) {
        return new ComponentName(c, AdminReceiver.class);
    }

    static DevicePolicyManager dpm(Context c) {
        return (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    /** A custom icon downloaded from the dashboard for the home screen. */
    static File iconFile(Context c, String pkg) {
        File dir = new File(c.getFilesDir(), "icons");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, pkg + ".png");
    }

    static File logoFile(Context c) {
        return new File(c.getFilesDir(), "logo.png");
    }

    static boolean isOwner(Context c) {
        return dpm(c).isDeviceOwnerApp(c.getPackageName());
    }

    /** Offline mode: no dashboard; the settings live on this phone and are applied from here. */
    static boolean standalone(Context c) {
        return prefs(c).getBoolean("standalone", false);
    }

    static boolean enrolled(Context c) {
        return prefs(c).getString("token", null) != null;
    }

    static void startServiceIfEnrolled(Context c) {
        if (enrolled(c) || standalone(c)) {
            c.startForegroundService(new Intent(c, AgentService.class));
        }
    }

    static Set<String> getSet(Context c, String key) {
        return new HashSet<>(prefs(c).getStringSet(key, new HashSet<String>()));
    }

    static void putSet(Context c, String key, Set<String> value) {
        prefs(c).edit().putStringSet(key, new HashSet<>(value)).apply();
    }

    // ---- results waiting to be reported to the server on the next sync ----

    static synchronized void addResult(Context c, String id, String type, boolean ok, String msg) {
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("results", "[]"));
            JSONObject r = new JSONObject();
            r.put("id", id);
            r.put("type", type);
            r.put("ok", ok);
            r.put("msg", msg == null ? "" : msg);
            arr.put(r);
            prefs(c).edit().putString("results", arr.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    static synchronized JSONArray peekResults(Context c) {
        try {
            return new JSONArray(prefs(c).getString("results", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    /** Drops the first {@code count} results (the ones that were just delivered). */
    static synchronized void dropResults(Context c, int count) {
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("results", "[]"));
            JSONArray rest = new JSONArray();
            for (int i = count; i < arr.length(); i++) rest.put(arr.get(i));
            prefs(c).edit().putString("results", rest.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    // ---------- phone log: events reported to the dashboard on the next sync ----------

    static synchronized void addEvent(Context c, String kind, String msg) {
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("events", "[]"));
            JSONObject e = new JSONObject();
            e.put("k", kind);
            e.put("m", msg);
            e.put("at", System.currentTimeMillis());
            arr.put(e);
            JSONArray keep = new JSONArray();
            for (int i = Math.max(0, arr.length() - 100); i < arr.length(); i++) keep.put(arr.get(i));
            prefs(c).edit().putString("events", keep.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    static synchronized JSONArray peekEvents(Context c) {
        try {
            return new JSONArray(prefs(c).getString("events", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    static synchronized void dropEvents(Context c, int count) {
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("events", "[]"));
            JSONArray rest = new JSONArray();
            for (int i = count; i < arr.length(); i++) rest.put(arr.get(i));
            prefs(c).edit().putString("events", rest.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    // ---------- overrides made on the phone with the master code ----------

    static synchronized JSONObject getOverrides(Context c) {
        try {
            return new JSONObject(prefs(c).getString("overrides", "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    static long overridesRev(Context c) {
        return prefs(c).getLong("overridesRev", 0);
    }

    /** mode: "allow", "block", or null to remove the override. Bumps the revision so the dashboard adopts it. */
    static synchronized void setOverride(Context c, String pkg, String mode) {
        try {
            JSONObject o = getOverrides(c);
            if (mode == null) o.remove(pkg);
            else o.put(pkg, mode);
            prefs(c).edit().putString("overrides", o.toString()).putLong("overridesRev", System.currentTimeMillis()).apply();
        } catch (JSONException ignored) {
        }
    }

    /** Adopts the dashboard's list when it is newer than ours (e.g. "clear local changes"). */
    static synchronized void adoptOverrides(Context c, JSONObject map, long rev) {
        if (map == null || rev <= overridesRev(c)) return;
        JSONObject clean = new JSONObject();
        Iterator<String> it = map.keys();
        try {
            while (it.hasNext()) {
                String k = it.next();
                String v = map.optString(k);
                if (v.equals("allow") || v.equals("block")) clean.put(k, v);
            }
        } catch (JSONException ignored) {
        }
        prefs(c).edit().putString("overrides", clean.toString()).putLong("overridesRev", rev).apply();
    }
}
