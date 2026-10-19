package com.familymdm.lockdown;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** A phone-local, admin-configured escape hatch for when the dashboard is unreachable and the
 * normal on-phone controls are themselves stuck: if a file with the chosen name and content
 * exists in this app's own external files folder, Lockdown stays device owner but stops
 * enforcing everything -- see MainActivity.isLockdownOn(), the single switch every apply*Live()
 * method already reads. The chosen content is never stored in a recoverable form, only a salted
 * SHA-256 hash of it, so decompiling the app reveals neither the file name nor what it needs to
 * say -- both are chosen fresh per phone, from MainActivity's own setup screen. */
final class EmergencyUnlock {
    private EmergencyUnlock() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("emergency_unlock", Context.MODE_PRIVATE);
    }

    static boolean isConfigured(Context c) {
        return prefs(c).contains("hash");
    }

    static String fileName(Context c) {
        return prefs(c).getString("fileName", null);
    }

    static void configure(Context c, String fileName, String content) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        prefs(c).edit()
                .putString("fileName", fileName.trim())
                .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString("hash", hash(salt, content))
                .apply();
    }

    static void clear(Context c) {
        prefs(c).edit().clear().apply();
    }

    /** Where to place the file -- always readable by this app with no storage permission, on
     * every Android version, since it's this app's own sandboxed corner of external storage. */
    static File directory(Context c) {
        return c.getExternalFilesDir(null);
    }

    /** True if the configured file exists, right where this app can always read it, and its
     * content matches what was set up. */
    static boolean isActive(Context c) {
        String fileName = prefs(c).getString("fileName", null);
        String saltB64 = prefs(c).getString("salt", null);
        String wantHash = prefs(c).getString("hash", null);
        if (fileName == null || saltB64 == null || wantHash == null) return false;
        File dir = directory(c);
        if (dir == null) return false;
        File f = new File(dir, fileName);
        if (!f.isFile()) return false;
        try {
            byte[] salt = Base64.decode(saltB64, Base64.NO_WRAP);
            return wantHash.equals(hash(salt, readSmallFile(f)));
        } catch (Exception e) {
            return false;
        }
    }

    private static String readSmallFile(File f) throws Exception {
        byte[] buf = new byte[4096];
        try (FileInputStream in = new FileInputStream(f)) {
            int n = in.read(buf);
            return new String(buf, 0, Math.max(n, 0), "UTF-8");
        }
    }

    private static String hash(byte[] salt, String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(content.trim().getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
