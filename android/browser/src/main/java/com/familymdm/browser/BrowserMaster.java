package com.familymdm.browser;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Offline mode's master code: chosen on this phone, used to add sites to the local allowlist with
 * no server at all. Only ever stored as a salted PBKDF2 hash, never the code itself. Same scheme the
 * agent app uses for its own master code, but this one is entirely local to Browser.
 */
final class BrowserMaster {
    private static final int MAX_FAILS = 5;
    private static final long LOCKOUT_MS = 15 * 60 * 1000;
    private static final int ITERATIONS = 100000;

    private BrowserMaster() {}

    static boolean isSet(Context c) {
        return BrowserState.prefs(c).getString("masterHash", null) != null;
    }

    static void set(Context c, String code) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        StringBuilder sb = new StringBuilder();
        for (byte b : salt) sb.append(String.format("%02x", b));
        BrowserState.prefs(c).edit()
                .putString("masterSalt", sb.toString())
                .putString("masterHash", derive(code, sb.toString()))
                .apply();
    }

    /** Returns null if the code is right, otherwise a message to show. */
    static String check(Context c, String code) {
        SharedPreferences p = BrowserState.prefs(c);
        if (!isSet(c)) return "No master code is set yet.";
        long now = System.currentTimeMillis();
        long until = p.getLong("masterLockUntil", 0);
        if (now < until) return "Too many wrong tries. Try again in " + ((until - now) / 60000 + 1) + " min.";
        boolean ok;
        try {
            ok = MessageDigest.isEqual(derive(code, p.getString("masterSalt", "")).getBytes(), p.getString("masterHash", "").getBytes());
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
            ed.putInt("masterFails", 0).putLong("masterLockUntil", now + LOCKOUT_MS).apply();
            return "Too many wrong tries. Locked for 15 minutes.";
        }
        ed.putInt("masterFails", fails).apply();
        return "Wrong code.";
    }

    private static String derive(String code, String saltHex) throws Exception {
        byte[] salt = new byte[saltHex.length() / 2];
        for (int i = 0; i < salt.length; i++) salt[i] = (byte) Integer.parseInt(saltHex.substring(2 * i, 2 * i + 2), 16);
        PBEKeySpec spec = new PBEKeySpec(code.toCharArray(), salt, ITERATIONS, 256);
        byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        StringBuilder sb = new StringBuilder();
        for (byte b : out) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
