package com.familymdm.agent;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Shared helpers: preferences, device-policy handles, and the queue of command results. */
final class Agent {
    private Agent() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("agent", Context.MODE_PRIVATE);
    }

    static ComponentName admin(Context c) {
        return new ComponentName(c, AdminReceiver.class);
    }

    static DevicePolicyManager dpm(Context c) {
        return (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    static boolean isOwner(Context c) {
        return dpm(c).isDeviceOwnerApp(c.getPackageName());
    }

    static boolean enrolled(Context c) {
        return prefs(c).getString("token", null) != null;
    }

    static void startServiceIfEnrolled(Context c) {
        if (enrolled(c)) {
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
}
