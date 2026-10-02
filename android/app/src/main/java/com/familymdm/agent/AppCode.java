package com.familymdm.agent;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * The code the person using the phone chooses to open this app. The phone verifies it locally
 * (salted hash). The plain code is also reported to the dashboard, so the administrator can see it.
 */
final class AppCode {
    private static final int MAX_FAILS = 5;
    private static final long LOCKOUT_MS = 5 * 60 * 1000;

    private AppCode() {}

    static boolean isSet(Context c) {
        return Agent.prefs(c).getString("appCodeHash", null) != null;
    }

    static void set(Context c, String code) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String saltHex = hex(salt);
        Agent.prefs(c).edit()
                .putString("appCodeSalt", saltHex)
                .putString("appCodeHash", hash(saltHex, code))
                .putString("appCodeUnsent", code) // reported on the next check-in, then forgotten
                .apply();
        Agent.addEvent(c, "security", "The person using the phone chose a new app code");
    }

    /** Forgets the code (administrator reset). The person must choose a new one; the dashboard is told it is cleared. */
    static void clear(Context c) {
        Agent.prefs(c).edit()
                .remove("appCodeSalt").remove("appCodeHash")
                .putString("appCodeUnsent", "")
                .apply();
    }

    /** What still has to be reported to the dashboard: the new code, "" for cleared, or null for nothing. */
    static String pendingReport(Context c) {
        return Agent.prefs(c).getString("appCodeUnsent", null);
    }

    static void reported(Context c) {
        Agent.prefs(c).edit().remove("appCodeUnsent").apply();
    }

    /** Returns null if the code is right, otherwise a message to show. */
    static String check(Context c, String code) {
        SharedPreferences p = Agent.prefs(c);
        long now = System.currentTimeMillis();
        long until = p.getLong("appCodeLockUntil", 0);
        if (now < until) return "Too many wrong tries. Try again in " + ((until - now) / 60000 + 1) + " min.";
        try {
            if (MessageDigest.isEqual(hash(p.getString("appCodeSalt", ""), code).getBytes(),
                    p.getString("appCodeHash", "").getBytes())) {
                p.edit().putInt("appCodeFails", 0).apply();
                return null;
            }
        } catch (Exception e) {
            return "Could not check the code.";
        }
        int fails = p.getInt("appCodeFails", 0) + 1;
        if (fails >= MAX_FAILS) {
            p.edit().putInt("appCodeFails", 0).putLong("appCodeLockUntil", now + LOCKOUT_MS).apply();
            Agent.addEvent(c, "security", "App code locked for 5 minutes after " + MAX_FAILS + " wrong tries");
            return "Too many wrong tries. Locked for 5 minutes.";
        }
        p.edit().putInt("appCodeFails", fails).apply();
        return "Wrong code.";
    }

    private static String hash(String saltHex, String code) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest((saltHex + ":" + code).getBytes("UTF-8")));
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
