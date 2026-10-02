package com.familymdm.agent;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Silent install/uninstall through PackageInstaller (allowed without prompts for the device owner). */
final class Installer {
    static final String ACTION = "com.familymdm.agent.INSTALL_RESULT";
    private static final long MAX_BYTES = 300L * 1024 * 1024;

    private Installer() {}

    static String installFromUrl(Context c, String url) throws IOException {
        if (url == null || !url.startsWith("https://")) throw new IOException("APK link must start with https://");
        File f = new File(c.getCacheDir(), "download.apk");
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(f)) {
            byte[] buf = new byte[16384];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_BYTES) throw new IOException("APK too large");
                out.write(buf, 0, n);
            }
        } finally {
            conn.disconnect();
        }

        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int id = pi.createSession(params);
        PackageInstaller.Session session = pi.openSession(id);
        try {
            try (OutputStream out = session.openWrite("apk", 0, f.length()); InputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                session.fsync(out);
            }
            session.commit(resultIntent(c, id));
        } finally {
            session.close();
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        return "install started";
    }

    /** Installs an APK read from a stream (for example one picked with the system file picker). */
    static String installFromStream(Context c, InputStream in) throws IOException {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int id = pi.createSession(params);
        PackageInstaller.Session session = pi.openSession(id);
        try {
            try (OutputStream out = session.openWrite("apk", 0, -1)) {
                byte[] buf = new byte[16384];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_BYTES) throw new IOException("APK too large");
                    out.write(buf, 0, n);
                }
                session.fsync(out);
            }
            session.commit(resultIntent(c, id));
        } catch (IOException | RuntimeException e) {
            session.abandon();
            throw e;
        } finally {
            session.close();
        }
        return "install started";
    }

    static String uninstall(Context c, String packageName) {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        pi.uninstall(packageName, resultIntent(c, packageName.hashCode()));
        return "uninstall started";
    }

    private static android.content.IntentSender resultIntent(Context c, int requestCode) {
        Intent i = new Intent(c, InstallReceiver.class).setAction(ACTION);
        PendingIntent pending = PendingIntent.getBroadcast(
                c, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        return pending.getIntentSender();
    }
}
