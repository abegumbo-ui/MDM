package com.familymdm.agent;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Things worth telling the administrator about even though nothing was "blocked": the phone was
 * restarted (and for how long it was off), or its SIM card was taken out, put in, or swapped.
 *
 * Each becomes a "security" event, which the dashboard shows in the phone log and sends as an
 * alert when alerts are set up (Settings > Alerts > "something security related").
 */
final class Tamper {
    private Tamper() {}

    private static final long OFF_WORTH_MENTIONING_MS = 10 * 60 * 1000;

    /** Called when the phone has finished starting up. */
    static void onBoot(Context c) {
        try {
            long last = Agent.prefs(c).getLong("lastAlive", 0);
            long off = last == 0 ? 0 : System.currentTimeMillis() - last;
            String how = off > OFF_WORTH_MENTIONING_MS ? " after being off for about " + human(off) : "";
            Agent.addEvent(c, "security", "Phone was restarted" + how);
        } catch (Exception ignored) {
        }
    }

    private static String human(long ms) {
        long min = ms / 60000;
        if (min < 90) return min + " minutes";
        long h = Math.round(min / 60.0);
        return h < 48 ? h + " hours" : (h / 24) + " days";
    }

    /** Called about twice a minute (from ScreenTime): remembers the phone is on, and looks at the SIM. */
    static void check(Context c) {
        try {
            Agent.prefs(c).edit().putLong("lastAlive", System.currentTimeMillis()).apply();
            checkSim(c);
        } catch (Exception ignored) {
        }
    }

    private static void checkSim(Context c) {
        if (c.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            // As device owner the Agent may grant itself this, the same way it does usage access.
            try {
                DevicePolicyManager dpm = Agent.dpm(c);
                if (dpm.isDeviceOwnerApp(c.getPackageName())) {
                    dpm.setPermissionGrantState(Agent.admin(c), c.getPackageName(), android.Manifest.permission.READ_PHONE_STATE,
                            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
                }
            } catch (Exception ignored) {
            }
            if (c.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return;
        }
        SubscriptionManager sm = c.getSystemService(SubscriptionManager.class);
        if (sm == null) return;
        List<String> now = new ArrayList<>();
        List<SubscriptionInfo> subs = sm.getActiveSubscriptionInfoList();
        if (subs != null) {
            for (SubscriptionInfo s : subs) {
                CharSequence carrier = s.getCarrierName();
                now.add((carrier == null ? "unknown" : carrier.toString()) + " #" + s.getSubscriptionId());
            }
        }
        Collections.sort(now);
        String snapshot = String.join(", ", now);
        String had = Agent.prefs(c).getString("simSnapshot", null);
        if (had == null) {
            Agent.prefs(c).edit().putString("simSnapshot", snapshot).apply(); // first look: nothing to compare with
            return;
        }
        if (had.equals(snapshot)) return;
        Agent.prefs(c).edit().putString("simSnapshot", snapshot).apply();
        String msg;
        if (snapshot.isEmpty()) msg = "SIM card removed (was " + had + ")";
        else if (had.isEmpty()) msg = "SIM card put in: " + snapshot;
        else msg = "SIM card changed: was " + had + ", now " + snapshot;
        Agent.addEvent(c, "security", msg);
    }
}
