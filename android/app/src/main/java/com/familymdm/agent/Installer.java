package com.familymdm.agent;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.app.admin.DevicePolicyManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;

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

    /**
     * The agent is the device owner, so it must be able to install, update and uninstall even when the
     * "block installs / unknown sources / uninstalls" restrictions are on for everyone else. They are paused
     * for two minutes (or until the result arrives), then put back by the next policy pass.
     */
    static void openWindow(Context c) {
        Agent.prefs(c).edit().putLong("installWindowUntil", System.currentTimeMillis() + 2 * 60 * 1000).apply();
        PolicyApplier.applyStored(c);
    }

    static void closeWindow(Context c) {
        Agent.prefs(c).edit().putLong("installWindowUntil", 0).apply();
        PolicyApplier.applyStored(c);
    }

    static String installFromUrl(Context c, String url) throws IOException {
        return installFromUrl(c, url, null);
    }

    /** bearer: the device token, when the file is served by the dashboard itself (uploaded APKs). */
    static String installFromUrl(Context c, String url, String bearer) throws IOException {
        if (url == null || !url.startsWith("https://")) throw new IOException("APK link must start with https://");
        File f = new File(c.getCacheDir(), "download.apk");
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        if (bearer != null) conn.setRequestProperty("authorization", "Bearer " + bearer);
        if (conn.getResponseCode() >= 400) throw new IOException("download failed (HTTP " + conn.getResponseCode() + ")");
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

        openWindow(c);
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        try {
            int id = pi.createSession(params);
            PackageInstaller.Session session = pi.openSession(id);
            try {
                try (OutputStream out = session.openWrite("apk", 0, f.length()); InputStream in = new FileInputStream(f)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    session.fsync(out);
                }
                session.commit(resultIntent(c, id, "install"));
            } finally {
                session.close();
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        } catch (IOException | RuntimeException e) {
            closeWindow(c);
            throw e;
        }
        return "install started";
    }

    /** Installs an APK read from a stream (for example one picked with the system file picker). */
    static String installFromStream(Context c, InputStream in) throws IOException {
        openWindow(c);
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
            session.commit(resultIntent(c, id, "install"));
        } catch (IOException | RuntimeException e) {
            session.abandon();
            closeWindow(c);
            throw e;
        } finally {
            session.close();
        }
        return "install started";
    }

    static String uninstall(Context c, String packageName) throws Exception {
        PackageManager pm = c.getPackageManager();
        DevicePolicyManager dpm = Agent.dpm(c);
        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES);
            boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            if (system) throw new Exception("that is a system app and cannot be uninstalled; use Block to hide it instead");
        } catch (PackageManager.NameNotFoundException e) {
            throw new Exception("that app is not on the phone");
        }
        // An app the agent had hidden has to be switched back on before Android will uninstall it.
        if (dpm.isApplicationHidden(Agent.admin(c), packageName)) {
            dpm.setApplicationHidden(Agent.admin(c), packageName, false);
            java.util.Set<String> hidden = Agent.getSet(c, "hidden");
            hidden.remove(packageName);
            Agent.putSet(c, "hidden", hidden);
        }
        openWindow(c);
        try {
            pm.getPackageInstaller().uninstall(packageName, resultIntent(c, packageName.hashCode(), "uninstall"));
        } catch (RuntimeException e) {
            closeWindow(c);
            throw e;
        }
        return "uninstall started";
    }

    private static android.content.IntentSender resultIntent(Context c, int requestCode, String kind) {
        Intent i = new Intent(c, InstallReceiver.class).setAction(ACTION).putExtra("kind", kind);
        PendingIntent pending = PendingIntent.getBroadcast(
                c, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        return pending.getIntentSender();
    }
}
