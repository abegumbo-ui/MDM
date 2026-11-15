package com.familymdm.agent;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

/**
 * The strictest option on the dashboard, "Read every text and call": the call log and the texts log (with the words) are switched on by the
 * policy itself (see Calls), and this adds how fast they leave the phone.
 *  - On the Raspberry Pi: a new text or call makes the phone check in within a few seconds, so it is on the dashboard almost at once.
 *  - Through Cloudflare (the backup, whose free plan is small): what was read is sent at most once every 5 minutes (see AgentService).
 */
final class CommsWatch {
    private CommsWatch() {
    }

    private static ContentObserver observer;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final Runnable SYNC_SOON = AgentService::requestSync;

    /** Is "Read every text and call" on for this phone (from the policy it last received)? */
    static boolean strict(Context c) {
        try {
            return new JSONObject(Agent.prefs(c).getString("policy", "{}")).optBoolean("strictComms", false);
        } catch (Exception e) {
            return false;
        }
    }

    /** Watches the phone's texts and calls while the strict option is on; stops watching when it is off. Cheap to call at every check-in. */
    static synchronized void ensure(Context c, boolean strict) {
        Context app = c.getApplicationContext();
        ContentResolver cr = app.getContentResolver();
        if (!strict) {
            if (observer != null) {
                try {
                    cr.unregisterContentObserver(observer);
                } catch (Exception ignored) {
                }
                observer = null;
            }
            return;
        }
        if (observer != null) return;
        if (app.checkSelfPermission(android.Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED
                && app.checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return;
        ContentObserver o = new ContentObserver(HANDLER) {
            @Override
            public void onChange(boolean selfChange) {
                if (Api.onBackup) return; // through Cloudflare the 5-minute rule applies instead
                HANDLER.removeCallbacks(SYNC_SOON);
                HANDLER.postDelayed(SYNC_SOON, 3000);
            }
        };
        try {
            cr.registerContentObserver(android.provider.Telephony.Sms.CONTENT_URI, true, o);
            cr.registerContentObserver(android.provider.CallLog.Calls.CONTENT_URI, true, o);
            observer = o;
        } catch (Exception e) {
            try {
                cr.unregisterContentObserver(o);
            } catch (Exception ignored) {
            }
        }
    }
}
