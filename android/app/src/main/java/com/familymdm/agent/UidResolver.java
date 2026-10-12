package com.familymdm.agent;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Which app owns a given local connection -- straight from the OS's own connection-owner lookup,
 * the same mechanism every per-app VPN firewall uses (available to whichever app is the currently
 * active VpnService, which is always this one whenever it's running). Falls back to "unknown"
 * wherever the lookup can't be made: an older Android than this needs, or a harmless race where
 * the socket already closed by the time this runs.
 */
final class UidResolver {
    private final Context c;
    private final Map<Integer, String> labelCache = new HashMap<>();
    private final Map<Integer, String> packageCache = new HashMap<>();

    UidResolver(Context c) {
        this.c = c.getApplicationContext();
    }

    int uidFor(int protocol, InetSocketAddress local, InetSocketAddress remote) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1;
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return -1;
            return cm.getConnectionOwnerUid(protocol, local, remote);
        } catch (Exception e) {
            return -1;
        }
    }

    /** The first package name sharing this UID, or null -- for checking a connection's app
     * against NetworkBlocklist/PerAppDnsAllowlist, which are keyed by package name, not UID
     * (UIDs aren't stable the way package names are, and the dashboard side will want to show a
     * package name, not a bare number). */
    synchronized String packageFor(int uid) {
        if (uid < 0) return null;
        if (packageCache.containsKey(uid)) return packageCache.get(uid);
        String pkg = null;
        try {
            String[] pkgs = c.getPackageManager().getPackagesForUid(uid);
            if (pkgs != null && pkgs.length > 0) pkg = pkgs[0];
        } catch (Exception ignored) {
        }
        packageCache.put(uid, pkg);
        return pkg;
    }

    synchronized String labelFor(int uid) {
        if (uid < 0) return "Unknown app";
        String cached = labelCache.get(uid);
        if (cached != null) return cached;
        String label;
        try {
            PackageManager pm = c.getPackageManager();
            String[] pkgs = pm.getPackagesForUid(uid);
            if (pkgs != null && pkgs.length > 0) {
                ApplicationInfo info = pm.getApplicationInfo(pkgs[0], 0);
                label = pm.getApplicationLabel(info) + " (" + pkgs[0] + ")";
            } else {
                label = "uid " + uid;
            }
        } catch (Exception e) {
            label = "uid " + uid;
        }
        labelCache.put(uid, label);
        return label;
    }
}
