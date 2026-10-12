package com.familymdm.agent;

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
 * and when. Shown locally from the Administrator panel's own Traffic Log section, and mirrored up
 * to the dashboard on each sync (AgentService.syncOnce()) the same way WholeAppBlocklist's own
 * edits are -- except one-directional: the dashboard has nothing to push back down for this, it's
 * a record of what already happened, not something to configure.
 *
 * add() is on the hot path now -- called for every DNS query and every TLS connection, device
 * wide, which can be many times a second. The first version of this class re-parsed and rewrote
 * the whole JSON array to SharedPreferences, twice, on every single call -- cheap-looking code
 * that was in practice hammering SharedPreferences' own apply() queue constantly, which Android
 * can make the whole app pay for at the worst times (a multi-second stall on the next Activity
 * pause/resume, which looked exactly like "the app froze when I pressed a button"). The fix: keep
 * the live state in memory (loaded from disk once), and only actually write it back out
 * periodically, not on every entry.
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

    // How rarely the in-memory state actually gets written to SharedPreferences -- the whole
    // point of keeping it in memory at all. A crash between flushes can lose a few seconds of
    // log entries; nothing here is safety-critical enough to be worth paying disk-write cost on
    // every single packet to avoid that.
    private static final long FLUSH_INTERVAL_MS = 3000;

    static final String KIND_DNS = "dns";
    static final String KIND_TLS = "tls";
    // A network-blocked app's connection refused outright, with no SNI possible to check against
    // its own allow list (anything not TCP :443) -- see NetworkBlocklist/PerAppDnsAllowlist.
    static final String KIND_NET = "net";

    private static final Object LOCK = new Object();
    private static boolean loaded;
    private static final LinkedList<JSONObject> ring = new LinkedList<>(); // newest first
    private static final LinkedList<JSONObject> pending = new LinkedList<>(); // oldest first
    private static boolean dirty;
    private static long lastFlush;

    private TrafficLog() {}

    private static void ensureLoaded(Context c) {
        synchronized (LOCK) {
            if (loaded) return;
            try {
                JSONArray arr = new JSONArray(Agent.prefs(c).getString(PREF_KEY, "[]"));
                for (int i = 0; i < arr.length(); i++) ring.addLast(arr.getJSONObject(i));
            } catch (JSONException ignored) {
            }
            try {
                JSONArray arr = new JSONArray(Agent.prefs(c).getString(SYNC_KEY, "[]"));
                for (int i = 0; i < arr.length(); i++) pending.addLast(arr.getJSONObject(i));
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
                pending.addLast(entry);
                while (pending.size() > SYNC_MAX) pending.removeFirst();
                dirty = true;
            }
        } catch (JSONException ignored) {
        }
        maybeFlush(c);
    }

    /** Writes the in-memory state out, but only if it's actually changed and enough time has
     * passed since the last write -- the one thing that makes keeping this in memory worthwhile. */
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
        JSONArray pendingArr = new JSONArray();
        synchronized (LOCK) {
            for (JSONObject o : ring) ringArr.put(o);
            for (JSONObject o : pending) pendingArr.put(o);
            dirty = false;
        }
        Agent.prefs(c).edit().putString(PREF_KEY, ringArr.toString()).putString(SYNC_KEY, pendingArr.toString()).apply();
    }

    static List<JSONObject> list(Context c) {
        ensureLoaded(c);
        synchronized (LOCK) {
            return new ArrayList<>(ring);
        }
    }

    /** A direct user action (Clear in the Administrator panel), not the hot path -- flushes
     * immediately rather than waiting for the next throttled write. */
    static void clear(Context c) {
        ensureLoaded(c);
        synchronized (LOCK) {
            ring.clear();
            dirty = true;
        }
        flush(c);
    }

    static JSONArray peekPending(Context c) {
        ensureLoaded(c);
        JSONArray arr = new JSONArray();
        synchronized (LOCK) {
            for (JSONObject o : pending) arr.put(o);
        }
        return arr;
    }

    static void dropPending(Context c, int count) {
        ensureLoaded(c);
        synchronized (LOCK) {
            for (int i = 0; i < count && !pending.isEmpty(); i++) pending.removeFirst();
            dirty = true;
        }
        // AgentService calls this right after a successful sync -- already an infrequent,
        // background-thread operation (not the hot path add() is on), so flushing immediately
        // here is cheap and avoids re-uploading the same entries again if the process dies before
        // the next throttled flush would have caught it.
        flush(c);
    }
}
