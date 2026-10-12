package com.familymdm.lockdown;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * A capped, newest-first record of what DnsFilterVpnService has actually seen: every DNS query
 * name and every TLS SNI hostname it parsed, which app (by UID) made it, whether it was blocked,
 * and when. Shown locally from the Lockdown setup screen's own Traffic Log section -- no
 * dashboard to mirror it to, this app never talks to a server.
 *
 * add() is on the hot path -- called for every DNS query and every TLS connection, device wide,
 * which can be many times a second. Keeps the live state in memory (loaded from disk once), only
 * actually persisting it every few seconds rather than on every single entry -- the agent app's
 * own first version of this class did the write-on-every-call thing and it was a real, measured
 * performance bug (SharedPreferences.apply() queued up constantly enough to stall the UI thread).
 */
final class TrafficLog {
    private static final String PREF_KEY = "trafficLog";
    private static final int MAX = 500;
    private static final long FLUSH_INTERVAL_MS = 3000;

    static final String KIND_DNS = "dns";
    static final String KIND_TLS = "tls";
    static final String KIND_NET = "net";

    private static final Object LOCK = new Object();
    private static boolean loaded;
    private static final LinkedList<JSONObject> ring = new LinkedList<>(); // newest first
    private static boolean dirty;
    private static long lastFlush;

    private TrafficLog() {}

    private static void ensureLoaded(Context c) {
        synchronized (LOCK) {
            if (loaded) return;
            try {
                JSONArray arr = new JSONArray(LockdownPrefs.prefs(c).getString(PREF_KEY, "[]"));
                for (int i = 0; i < arr.length(); i++) ring.addLast(arr.getJSONObject(i));
            } catch (JSONException ignored) {
            }
            loaded = true;
        }
    }

    static void add(Context c, String kind, String host, int uid, String appLabel, boolean blocked) {
        ensureLoaded(c);
        try {
            JSONObject entry = new JSONObject();
            entry.put("time", System.currentTimeMillis());
            entry.put("kind", kind);
            entry.put("host", host == null ? "" : host);
            entry.put("uid", uid);
            entry.put("app", appLabel == null ? "" : appLabel);
            entry.put("blocked", blocked);
            synchronized (LOCK) {
                ring.addFirst(entry);
                while (ring.size() > MAX) ring.removeLast();
                dirty = true;
            }
        } catch (JSONException ignored) {
        }
        maybeFlush(c);
    }

    private static void maybeFlush(Context c) {
        boolean shouldFlush;
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            shouldFlush = dirty && (now - lastFlush > FLUSH_INTERVAL_MS);
            if (shouldFlush) lastFlush = now;
        }
        if (shouldFlush) flush(c);
    }

    private static void flush(Context c) {
        JSONArray ringArr = new JSONArray();
        synchronized (LOCK) {
            for (JSONObject o : ring) ringArr.put(o);
            dirty = false;
        }
        LockdownPrefs.prefs(c).edit().putString(PREF_KEY, ringArr.toString()).apply();
    }

    static List<JSONObject> list(Context c) {
        ensureLoaded(c);
        synchronized (LOCK) {
            return new ArrayList<>(ring);
        }
    }

    /** A direct user action, not the hot path -- flushes immediately. */
    static void clear(Context c) {
        ensureLoaded(c);
        synchronized (LOCK) {
            ring.clear();
            dirty = true;
        }
        flush(c);
    }
}
