package com.familymdm.agent;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * A separate PIN/password that only locks this app's own front screen (Admin, status, everything).
 * Unlike the master code, it has nothing to do with managing the phone -- it's just a way for the
 * administrator to keep this app's screen from being casually opened, independent of whether the
 * phone itself even has a screen lock set. Stored the same scrambled way as the master code.
 */
final class AppPin {
    private static final int MAX_FAILS = 5;
    private static final long LOCKOUT_MS = 15 * 60 * 1000;
    private static final int ITERATIONS = 10000;

    private AppPin() {}

    static boolean isSet(Context c) {
        return Agent.prefs(c).getString("appPinHash", null) != null;
    }

    static void set(Context c, String pin) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        StringBuilder sb = new StringBuilder();
        for (byte b : salt) sb.append(String.format("%02x", b));
        Agent.prefs(c).edit()
                .putString("appPinSalt", sb.toString())
                .putString("appPinHash", derive(pin, sb.toString()))
                .putInt("appPinFails", 0)
                .putLong("appPinLockUntil", 0)
                .apply();
    }

    static void clear(Context c) {
        Agent.prefs(c).edit().remove("appPinSalt").remove("appPinHash")
                .remove("appPinFails").remove("appPinLockUntil").apply();
    }

    /** Returns null if the PIN is right, otherwise a message to show. */
    static String check(Context c, String pin) {
        SharedPreferences p = Agent.prefs(c);
        if (!isSet(c)) return "No app PIN is set.";
        long now = System.currentTimeMillis();
        long until = p.getLong("appPinLockUntil", 0);
        if (now < until) return "Too many wrong tries. Try again in " + ((until - now) / 60000 + 1) + " min.";
        boolean ok;
        try {
            ok = MessageDigest.isEqual(derive(pin, p.getString("appPinSalt", "")).getBytes(),
                    p.getString("appPinHash", "").getBytes());
        } catch (Exception e) {
            return "Could not check the PIN: " + e.getMessage();
        }
        if (ok) {
            p.edit().putInt("appPinFails", 0).apply();
            return null;
        }
        int fails = p.getInt("appPinFails", 0) + 1;
        SharedPreferences.Editor ed = p.edit();
        if (fails >= MAX_FAILS) {
            ed.putInt("appPinFails", 0).putLong("appPinLockUntil", now + LOCKOUT_MS);
            ed.apply();
            return "Too many wrong tries. Locked for 15 minutes. Use the Administrator code instead.";
        }
        ed.putInt("appPinFails", fails).apply();
        return "Wrong PIN.";
    }

    private static String derive(String pin, String saltHex) throws Exception {
        byte[] salt = new byte[saltHex.length() / 2];
        for (int i = 0; i < salt.length; i++) salt[i] = (byte) Integer.parseInt(saltHex.substring(2 * i, 2 * i + 2), 16);
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256);
        byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        StringBuilder sb = new StringBuilder();
        for (byte b : out) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
