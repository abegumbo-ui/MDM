package com.familymdm.agent;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.security.MessageDigest;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * The master code works with no internet. The dashboard only ever sends a salted PBKDF2 hash of it,
 * and the phone checks the typed code against that hash locally, with a lockout after wrong guesses.
 */
final class Master {
    private static final int MAX_FAILS = 5;
    private static final long LOCKOUT_MS = 15 * 60 * 1000;

    private Master() {}

    static boolean isSet(Context c) {
        return Agent.prefs(c).getString("masterHash", null) != null;
    }

    /** Stores (or clears, when null) the hash received from the dashboard. */
    static void store(Context c, JSONObject master) {
        SharedPreferences.Editor e = Agent.prefs(c).edit();
        if (master == null) {
            e.remove("masterSalt").remove("masterHash").remove("masterIter");
        } else {
            e.putString("masterSalt", master.optString("salt"))
                    .putString("masterHash", master.optString("hash"))
                    .putInt("masterIter", master.optInt("iterations", 100000));
        }
        e.apply();
    }

    /** True if the code is the master code. Doesn't count as a failed try (the caller does its own limiting). */
    static boolean matches(Context c, String code) {
        if (!isSet(c)) return false;
        SharedPreferences p = Agent.prefs(c);
        try {
            return MessageDigest.isEqual(derive(code, p.getString("masterSalt", ""), p.getInt("masterIter", 100000)).getBytes(),
                    p.getString("masterHash", "").getBytes());
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns null if the code is right, otherwise a message to show. */
    static String check(Context c, String code) {
        SharedPreferences p = Agent.prefs(c);
        if (!isSet(c)) return "No master code is set. Set one in the dashboard (Settings).";
        long now = System.currentTimeMillis();
        long until = p.getLong("masterLockUntil", 0);
        if (now < until) return "Too many wrong tries. Try again in " + ((until - now) / 60000 + 1) + " min.";
        boolean ok;
        try {
            ok = MessageDigest.isEqual(derive(code, p.getString("masterSalt", ""), p.getInt("masterIter", 100000)).getBytes(),
                    p.getString("masterHash", "").getBytes());
        } catch (Exception e) {
            return "Could not check the code: " + e.getMessage();
        }
        if (ok) {
            p.edit().putInt("masterFails", 0).apply();
            return null;
        }
        int fails = p.getInt("masterFails", 0) + 1;
        SharedPreferences.Editor ed = p.edit();
        if (fails >= MAX_FAILS) {
            ed.putInt("masterFails", 0).putLong("masterLockUntil", now + LOCKOUT_MS);
            ed.apply();
            Agent.addEvent(c, "security", "Master code locked for 15 minutes after " + MAX_FAILS + " wrong tries");
            return "Too many wrong tries. Locked for 15 minutes.";
        }
        ed.putInt("masterFails", fails).apply();
        return "Wrong code.";
    }

    private static String derive(String code, String saltHex, int iterations) throws Exception {
        byte[] salt = new byte[saltHex.length() / 2];
        for (int i = 0; i < salt.length; i++) salt[i] = (byte) Integer.parseInt(saltHex.substring(2 * i, 2 * i + 2), 16);
        PBEKeySpec spec = new PBEKeySpec(code.toCharArray(), salt, iterations, 256);
        byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        StringBuilder sb = new StringBuilder();
        for (byte b : out) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
