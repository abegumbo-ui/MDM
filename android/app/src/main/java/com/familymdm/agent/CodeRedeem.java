package com.familymdm.agent;

import android.app.Activity;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * Shared one-time-code redemption, used by both MainActivity (uninstall) and UserActivity
 * (install). The master code always works in place of a real one-time code, with no internet
 * needed; otherwise the code is checked against the dashboard, or just rejected outright offline.
 * Five wrong tries locks code entry for five minutes.
 */
final class CodeRedeem {
    private CodeRedeem() {}

    interface Callback {
        /** null means success. */
        void onResult(String error);
    }

    static void redeem(Activity a, String type, String code, Callback cb) {
        final String server = Agent.prefs(a).getString("server", null);
        final String token = Agent.prefs(a).getString("token", null);
        final boolean standalone = Agent.standalone(a);
        if (code.isEmpty() || (!standalone && (server == null || token == null))) return;
        new Thread(() -> {
            SharedPreferences p = Agent.prefs(a);
            long now = System.currentTimeMillis();
            long lockedUntil = p.getLong("redeemLockUntil", 0);
            String error = null;
            final boolean masterOk = Master.matches(a, code);
            if (now < lockedUntil && !masterOk) {
                error = "Too many wrong tries. Try again in " + ((lockedUntil - now) / 60000 + 1) + " min.";
            } else if (masterOk) {
                Agent.addEvent(a, "local", "Master code used instead of a one-time " + type + " code");
                p.edit().putInt("redeemFails", 0).putLong("redeemLockUntil", 0).apply();
            } else if (standalone) {
                error = failedTry(a, p, now, "Wrong code.");
            } else {
                try {
                    JSONObject body = new JSONObject();
                    body.put("type", type);
                    body.put("code", code);
                    Api.post(server + "/agent/redeem", body, token);
                    p.edit().putInt("redeemFails", 0).apply();
                } catch (Exception e) {
                    if (e instanceof Api.HttpException && ((Api.HttpException) e).code == 403) {
                        error = failedTry(a, p, now, "That code is wrong, already used, or expired.");
                    } else {
                        error = "Could not check the code: " + e.getMessage();
                    }
                }
            }
            final String err = error;
            a.runOnUiThread(() -> cb.onResult(err));
        }).start();
    }

    private static String failedTry(Activity a, SharedPreferences p, long now, String message) {
        int fails = p.getInt("redeemFails", 0) + 1;
        if (fails >= 5) {
            p.edit().putInt("redeemFails", 0).putLong("redeemLockUntil", now + 5 * 60 * 1000).apply();
            Agent.addEvent(a, "security", "Code entry locked for 5 minutes after 5 wrong tries");
            return "Too many wrong tries. Locked for 5 minutes.";
        }
        p.edit().putInt("redeemFails", fails).apply();
        return message;
    }
}
