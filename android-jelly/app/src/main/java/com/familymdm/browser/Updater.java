package com.familymdm.browser;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * "Automatic updates" (the Browser's settings, on by default): every few hours the Browser looks at
 * the published newest version (browser-version.json next to mdm-browser.apk), downloads a newer one
 * by itself, checks its fingerprint, and brings up Android's own "Update" button. Nothing is
 * installed without that tap. On a phone run by the MDM app, the MDM app updates it instead.
 */
public final class Updater {
    private Updater() {}

    public static final String PREF_AUTO = "key_auto_update";
    private static final long EVERY_MS = 6L * 60 * 60 * 1000;
    private static final long MAX_BYTES = 150L * 1024 * 1024;
    private static volatile boolean busy;

    /** From MainActivity.onResume: checks now and then, when automatic updates are on. */
    public static void maybeCheck(Activity a) {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(a);
        if (!p.getBoolean(PREF_AUTO, true) || Guard.mode(a) == Guard.Mode.AGENT) return;
        if (System.currentTimeMillis() - p.getLong("updateCheckedAt", 0) < EVERY_MS) return;
        check(a, false);
    }

    /** "Check for updates now" (manual: says so when it is already the newest). */
    public static void check(final Activity a, final boolean manual) {
        if (busy) return;
        final String feed = BrowserApp.updateUrl(a);
        if (feed.isEmpty()) { if (manual) toast(a, "This copy has no update address built in."); return; }
        busy = true;
        if (manual) toast(a, "Checking for updates…");
        new Thread(() -> {
            String msg = null;
            try {
                PreferenceManager.getDefaultSharedPreferences(a).edit().putLong("updateCheckedAt", System.currentTimeMillis()).apply();
                JSONObject info = new JSONObject(new String(fetch(feed, 64 * 1024), "UTF-8"));
                long newest = info.getLong("versionCode");
                PackageInfo me = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
                long mine = Build.VERSION.SDK_INT >= 28 ? me.getLongVersionCode() : me.versionCode;
                if (newest <= mine) {
                    if (manual) msg = "You have the newest version.";
                } else {
                    File apk = new File(a.getCacheDir(), "update.apk");
                    download(info.getString("url"), apk, info.getString("sha256"));
                    a.runOnUiThread(() -> install(a, apk));
                }
            } catch (Exception e) {
                if (manual) msg = "Could not check for updates: " + e.getMessage();
            } finally {
                busy = false;
            }
            final String m = msg;
            if (m != null) a.runOnUiThread(() -> toast(a, m));
        }).start();
    }

    private static byte[] fetch(String url, int max) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(30000);
        h.setInstanceFollowRedirects(true);
        if (h.getResponseCode() != 200) throw new Exception("HTTP " + h.getResponseCode());
        try (InputStream in = h.getInputStream()) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) > 0; ) {
                out.write(b, 0, n);
                if (out.size() > max) throw new Exception("too large");
            }
            return out.toByteArray();
        } finally {
            h.disconnect();
        }
    }

    /** Downloads the new version and makes sure it is exactly the published one (its fingerprint). */
    private static void download(String url, File dest, String sha256) throws Exception {
        if (!url.startsWith("https://github.com/")) throw new Exception("updates only come from GitHub");
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(60000);
        h.setInstanceFollowRedirects(true);
        if (h.getResponseCode() != 200) throw new Exception("HTTP " + h.getResponseCode());
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        long total = 0;
        try (InputStream in = h.getInputStream(); OutputStream out = new FileOutputStream(dest)) {
            byte[] b = new byte[65536];
            for (int n; (n = in.read(b)) > 0; ) {
                total += n;
                if (total > MAX_BYTES) throw new Exception("too large");
                md.update(b, 0, n);
                out.write(b, 0, n);
            }
        } finally {
            h.disconnect();
        }
        StringBuilder hex = new StringBuilder();
        for (byte x : md.digest()) hex.append(String.format("%02x", x));
        if (!hex.toString().equalsIgnoreCase(sha256)) {
            dest.delete();
            throw new Exception("the download does not match the published version");
        }
    }

    /** Hands the new version to Android, which shows its own "Update" button. */
    private static void install(Activity a, File apk) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
                toast(a, "A new version is ready. Allow \"Install unknown apps\" for Browser once, then come back.");
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
                PreferenceManager.getDefaultSharedPreferences(a).edit().putLong("updateCheckedAt", 0).apply(); // try again on return
                return;
            }
            PackageInstaller pi = a.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(a.getPackageName());
            int id = pi.createSession(params);
            try (PackageInstaller.Session s = pi.openSession(id)) {
                try (InputStream in = new FileInputStream(apk); OutputStream out = s.openWrite("browser.apk", 0, apk.length())) {
                    byte[] b = new byte[65536];
                    for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
                    s.fsync(out);
                }
                int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                PendingIntent done = PendingIntent.getBroadcast(a, 0, new Intent(a, Result.class), flags);
                s.commit(done.getIntentSender());
            }
        } catch (Exception e) {
            toast(a, "Could not start the update: " + e.getMessage());
        }
    }

    /** Android's answer: show its "Update" button, or say what went wrong. */
    public static class Result extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(confirm);
                }
            } else if (status != PackageInstaller.STATUS_SUCCESS) {
                String why = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Toast.makeText(c, "The update did not install" + (why != null ? ": " + why : "."), Toast.LENGTH_LONG).show();
            }
        }
    }

    private static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_LONG).show();
    }
}
