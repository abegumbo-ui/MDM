package com.familymdm.agent;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Location history: when the dashboard asks for it ("every 15/30/60 minutes"), the phone notes
 * where it is that often and sends the places with its next check-in. The dashboard keeps the
 * history and sends "arrived at / left" alerts for places you saved. Off unless switched on.
 *
 * Also the SOS button: notes "asked for help" right away, then adds where the phone is.
 */
final class Whereabouts {
    private Whereabouts() {}

    private static final String PENDING = "locPending";
    private static final int MAX_PENDING = 30;
    private static volatile boolean busy;

    static void apply(Context c, JSONObject policy) {
        Agent.prefs(c).edit().putInt("locationEvery", Math.max(0, policy.optInt("locationEvery", 0))).apply();
    }

    /** Called about once a minute (from ScreenTime's tick). */
    static void check(Context c) {
        int every = Agent.prefs(c).getInt("locationEvery", 0);
        if (every <= 0 || busy) return;
        long now = System.currentTimeMillis();
        long last = Agent.prefs(c).getLong("locLast", 0);
        if (now - last < every * 60000L && last <= now) return;
        Agent.prefs(c).edit().putLong("locLast", now).apply();
        fixAsync(c.getApplicationContext(), null);
    }

    /** SOS: tell the dashboard now, then where the phone is once it has a fix. */
    static void sos(Context c) {
        final Context app = c.getApplicationContext();
        Agent.addEvent(app, "sos", "Asked for help from the phone");
        AgentService.requestSync();
        fixAsync(app, "sos");
    }

    /** "Check in": tells the parent where the phone is (not an emergency). */
    static void checkIn(Context c) {
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                JSONObject fix = new JSONObject(Actions.locate(app));
                fix.put("why", "checkin");
                add(app, fix);
                Agent.addEvent(app, "checkin", "Checked in: https://maps.google.com/?q=" + fix.optDouble("lat") + "," + fix.optDouble("lon"));
            } catch (Exception e) {
                Agent.addEvent(app, "checkin", "Checked in (the phone could not find its location)");
            }
            AgentService.requestSync();
        }, "mdm-checkin").start();
    }

    private static void fixAsync(final Context c, final String why) {
        busy = true;
        new Thread(() -> {
            try {
                JSONObject fix = new JSONObject(Actions.locate(c));
                if (why != null) fix.put("why", why);
                add(c, fix);
                if ("sos".equals(why)) {
                    Agent.addEvent(c, "sos", "Help request location: https://maps.google.com/?q="
                            + fix.optDouble("lat") + "," + fix.optDouble("lon"));
                }
                AgentService.requestSync();
            } catch (Exception e) {
                if ("sos".equals(why)) Agent.addEvent(c, "sos", "Help request: the phone could not find its location (" + e.getMessage() + ")");
            } finally {
                busy = false;
            }
        }, "mdm-whereabouts").start();
    }

    private static synchronized void add(Context c, JSONObject fix) throws Exception {
        JSONArray list = new JSONArray(Agent.prefs(c).getString(PENDING, "[]"));
        list.put(fix);
        JSONArray out = new JSONArray();
        for (int i = Math.max(0, list.length() - MAX_PENDING); i < list.length(); i++) out.put(list.get(i));
        Agent.prefs(c).edit().putString(PENDING, out.toString()).apply();
    }

    static synchronized JSONArray peek(Context c) {
        try {
            return new JSONArray(Agent.prefs(c).getString(PENDING, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static synchronized void drop(Context c, int count) {
        try {
            JSONArray list = peek(c), out = new JSONArray();
            for (int i = count; i < list.length(); i++) out.put(list.get(i));
            Agent.prefs(c).edit().putString(PENDING, out.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}
