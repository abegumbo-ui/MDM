package com.familymdm.agent;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A capped, newest-first record of what DnsFilterVpnService has actually seen: every DNS query
 * name and every TLS SNI hostname it parsed, which app (by UID) made it, whether it was blocked,
 * and when. Local-only, never sent to the dashboard -- purely a way to see on the phone itself
 * what the filter is doing, from the Administrator panel's own Traffic Log section.
 */
final class TrafficLog {
    private static final String PREF_KEY = "trafficLog";
    private static final int MAX = 500;

    static final String KIND_DNS = "dns";
    static final String KIND_TLS = "tls";

    private TrafficLog() {}

    static synchronized void add(Context c, String kind, String host, int uid, String appLabel, boolean blocked) {
        try {
            JSONArray current = new JSONArray(Agent.prefs(c).getString(PREF_KEY, "[]"));
            JSONObject entry = new JSONObject();
            entry.put("time", System.currentTimeMillis());
            entry.put("kind", kind);
            entry.put("host", host == null ? "" : host);
            entry.put("uid", uid);
            entry.put("app", appLabel == null ? "" : appLabel);
            entry.put("blocked", blocked);
            // Newest first: insert at the front, then trim from the back once over the cap.
            JSONArray next = new JSONArray();
            next.put(entry);
            for (int i = 0; i < current.length() && next.length() < MAX; i++) next.put(current.get(i));
            Agent.prefs(c).edit().putString(PREF_KEY, next.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    static List<JSONObject> list(Context c) {
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(Agent.prefs(c).getString(PREF_KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getJSONObject(i));
        } catch (JSONException ignored) {
        }
        return out;
    }

    static void clear(Context c) {
        Agent.prefs(c).edit().remove(PREF_KEY).apply();
    }
}
