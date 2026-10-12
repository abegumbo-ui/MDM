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
 * and when. Shown locally from the Administrator panel's own Traffic Log section, and mirrored up
 * to the dashboard on each sync (AgentService.syncOnce()) the same way WholeAppBlocklist's own
 * edits are -- except one-directional: the dashboard has nothing to push back down for this, it's
 * a record of what already happened, not something to configure.
 */
final class TrafficLog {
    private static final String PREF_KEY = "trafficLog";
    private static final int MAX = 500;

    // A second, independent, oldest-first copy of the same entries, drained on successful upload
    // (peekPending/dropPending) -- same queue-and-drain shape as Agent's own event log. Kept
    // separate from the display ring above so a slow or offline phone doesn't lose anything to
    // upload just because the (much larger) display ring trimmed it first.
    private static final String SYNC_KEY = "trafficLogSync";
    private static final int SYNC_MAX = 60;

    static final String KIND_DNS = "dns";
    static final String KIND_TLS = "tls";
    // A network-blocked app's connection refused outright, with no SNI possible to check against
    // its own allow list (anything not TCP :443) -- see NetworkBlocklist/PerAppDnsAllowlist.
    static final String KIND_NET = "net";

    private TrafficLog() {}

    static synchronized void add(Context c, String kind, String host, int uid, String appLabel, boolean blocked) {
        try {
            JSONObject entry = new JSONObject();
            entry.put("time", System.currentTimeMillis());
            entry.put("kind", kind);
            entry.put("host", host == null ? "" : host);
            entry.put("uid", uid);
            entry.put("app", appLabel == null ? "" : appLabel);
            entry.put("blocked", blocked);

            JSONArray current = new JSONArray(Agent.prefs(c).getString(PREF_KEY, "[]"));
            // Newest first: insert at the front, then trim from the back once over the cap.
            JSONArray next = new JSONArray();
            next.put(entry);
            for (int i = 0; i < current.length() && next.length() < MAX; i++) next.put(current.get(i));
            Agent.prefs(c).edit().putString(PREF_KEY, next.toString()).apply();

            JSONArray pending = new JSONArray(Agent.prefs(c).getString(SYNC_KEY, "[]"));
            pending.put(entry);
            JSONArray keepPending = new JSONArray();
            for (int i = Math.max(0, pending.length() - SYNC_MAX); i < pending.length(); i++) keepPending.put(pending.get(i));
            Agent.prefs(c).edit().putString(SYNC_KEY, keepPending.toString()).apply();
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
        // Deliberately NOT clearing SYNC_KEY -- entries already seen locally should still make it
        // to the dashboard even if the on-device log is cleared right after.
    }

    static synchronized JSONArray peekPending(Context c) {
        try {
            return new JSONArray(Agent.prefs(c).getString(SYNC_KEY, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    static synchronized void dropPending(Context c, int count) {
        try {
            JSONArray arr = new JSONArray(Agent.prefs(c).getString(SYNC_KEY, "[]"));
            JSONArray rest = new JSONArray();
            for (int i = count; i < arr.length(); i++) rest.put(arr.get(i));
            Agent.prefs(c).edit().putString(SYNC_KEY, rest.toString()).apply();
        } catch (JSONException ignored) {
        }
    }
}
