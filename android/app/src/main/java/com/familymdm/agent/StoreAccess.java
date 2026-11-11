package com.familymdm.agent;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

/**
 * A timed "Play Store is open" window, for a phone whose Play Store is normally blocked.
 *
 * Started by the administrator (a dashboard command, or the master code on the phone) or by a
 * one-time code typed on the phone's User screen. While it is open, PolicyApplier leaves the Play
 * Store un-hidden and pauses the "no installing apps" restrictions; when the time is up everything
 * goes back exactly as the policy says. Apps installed meanwhile are treated like any newly
 * installed app (held for approval if that setting is on).
 */
final class StoreAccess {
    private StoreAccess() {}

    static final String PKG = "com.android.vending";
    private static final long UNTIL_CLOSED = Long.MAX_VALUE;

    private static long until(Context c) {
        return Agent.prefs(c).getLong("storeUntil", 0);
    }

    static boolean open(Context c) {
        return until(c) > System.currentTimeMillis();
    }

    /** What the dashboard is told: 0 = not open, -1 = open until closed, otherwise the end time. */
    static long reportUntil(Context c) {
        long u = until(c);
        if (u <= System.currentTimeMillis()) return 0;
        return u == UNTIL_CLOSED ? -1 : u;
    }

    static int minutesLeft(Context c) {
        long u = until(c);
        if (u == UNTIL_CLOSED) return -1;
        return (int) Math.max(0, (u - System.currentTimeMillis() + 59_999) / 60_000);
    }

    /** minutes <= 0 means "until it is closed again". Applies right away. */
    static void start(Context c, int minutes, String by) {
        long u = minutes <= 0 ? UNTIL_CLOSED : System.currentTimeMillis() + minutes * 60_000L;
        Agent.prefs(c).edit().putLong("storeUntil", u).apply();
        Agent.addEvent(c, "restriction", "Play Store opened " + (minutes <= 0 ? "until it is closed again" : "for " + minutes + " minutes") + " (" + by + ")");
        PolicyApplier.applyStored(c);
    }

    static void stop(Context c, String by) {
        if (until(c) == 0) return;
        Agent.prefs(c).edit().putLong("storeUntil", 0).apply();
        Agent.addEvent(c, "restriction", "Play Store closed again (" + by + ")");
        PolicyApplier.applyStored(c);
    }

    /** Called whenever the policy is applied: notices a window that has run out and says so once. */
    static void expireIfDue(Context c) {
        long u = until(c);
        if (u != 0 && u <= System.currentTimeMillis()) {
            Agent.prefs(c).edit().putLong("storeUntil", 0).apply();
            Agent.addEvent(c, "restriction", "Play Store closed again (time is up)");
        }
    }

    /** Is the Play Store hidden on this phone right now? */
    static boolean hidden(Context c) {
        try {
            return Agent.dpm(c).isApplicationHidden(Agent.admin(c), PKG);
        } catch (Exception e) {
            return false; // not device owner (preview): nothing is enforced, so it is not hidden
        }
    }

    static boolean launch(Activity a) {
        try {
            Intent i = a.getPackageManager().getLaunchIntentForPackage(PKG);
            if (i == null) return false;
            a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
