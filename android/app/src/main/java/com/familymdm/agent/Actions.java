package com.familymdm.agent;

import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telecom.TelecomManager;
import android.util.Base64;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/** Device actions shared by dashboard commands and the on-device admin panel. */
final class Actions {
    private Actions() {}

    static boolean hasScreenLock(Context c) {
        return c.getSystemService(KeyguardManager.class).isDeviceSecure();
    }

    /**
     * One on-demand fix, not continuous tracking -- there is no background location loop anywhere
     * in this app, only this single request, made the moment "Find now" is queued from the
     * dashboard. Permission is already silently granted as a side effect of "Report Wi-Fi name"
     * (PolicyApplier.enableWifiName), on by default, so this almost never needs anything new
     * granted; if it somehow isn't, this fails with a clear message instead of guessing.
     */
    static String locate(Context c) throws Exception {
        if (c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            throw new Exception("location permission is not granted on this phone");
        }
        android.location.LocationManager lm = (android.location.LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null || !lm.isLocationEnabled()) throw new Exception("Location is turned off on this phone");

        String provider = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                ? android.location.LocationManager.GPS_PROVIDER
                : android.location.LocationManager.NETWORK_PROVIDER;
        final java.util.concurrent.ArrayBlockingQueue<android.location.Location> queue = new java.util.concurrent.ArrayBlockingQueue<>(1);
        android.location.LocationListener listener = new android.location.LocationListener() {
            @Override
            public void onLocationChanged(android.location.Location location) {
                queue.offer(location);
            }
        };
        android.location.Location fix;
        try {
            lm.requestSingleUpdate(provider, listener, android.os.Looper.getMainLooper());
            fix = queue.poll(20, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            lm.removeUpdates(listener);
        }
        if (fix == null) fix = lm.getLastKnownLocation(provider); // a fresh fix timed out -- settle for the last one
        if (fix == null) throw new Exception("could not get a location fix (no signal, or none ever recorded)");

        org.json.JSONObject out = new org.json.JSONObject();
        out.put("lat", fix.getLatitude());
        out.put("lon", fix.getLongitude());
        out.put("accuracy", fix.getAccuracy());
        out.put("at", fix.getTime());
        return out.toString();
    }

    /**
     * Locks the screen. With minutes > 0 it is a timed lock: a full-screen message and countdown that
     * nothing can be opened over (emergency calls stay possible) until time is up or it is unlocked.
     * The message is also shown on the lock screen itself.
     */
    static String lock(Context c, int minutes, String message) {
        DevicePolicyManager dpm = Agent.dpm(c);
        ComponentName admin = Agent.admin(c);
        boolean secure = hasScreenLock(c);
        boolean hasMessage = message != null && !message.trim().isEmpty();
        dpm.setDeviceOwnerLockScreenInfo(admin, hasMessage ? message.trim() : null);
        if (minutes > 0) {
            Agent.prefs(c).edit()
                    .putLong("lockUntil", System.currentTimeMillis() + minutes * 60000L)
                    .putString("lockMsg", hasMessage ? message.trim() : "")
                    .apply();
            startTimedLock(c);
            dpm.lockNow();
            return "locked for " + minutes + " min" + (hasMessage ? " with your message" : "");
        }
        dpm.lockNow();
        if (secure) return hasMessage ? "screen locked, message shown on the lock screen" : "screen locked";
        return "screen turned off, but this phone has no screen lock (PIN) set, so anyone can wake it. Use Set PIN to make Lock real";
    }

    static void startTimedLock(Context c) {
        DevicePolicyManager dpm = Agent.dpm(c);
        ComponentName admin = Agent.admin(c);
        // Home-screen mode already allows the agent and the phone app; otherwise allow just those two.
        String[] allowed = Kiosk.lockTaskPackages(c);
        dpm.setLockTaskPackages(admin, allowed);
        if (Build.VERSION.SDK_INT >= 28) dpm.setLockTaskFeatures(admin, 0);
        Intent i = new Intent(c, LockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        ActivityOptions opts = ActivityOptions.makeBasic();
        opts.setLockTaskEnabled(true);
        c.startActivity(i, opts.toBundle());
    }

    /** Called on a timer and after reboot: keeps a running timed lock in place, or ends an expired one. */
    static void ensureTimedLock(Context c) {
        long until = Agent.prefs(c).getLong("lockUntil", 0);
        if (until == 0 || !Agent.isOwner(c)) return;
        if (until <= System.currentTimeMillis()) {
            endTimedLock(c);
        } else if (!LockActivity.alive) {
            startTimedLock(c);
        }
    }

    static boolean timedLockActive(Context c) {
        return Agent.prefs(c).getLong("lockUntil", 0) > System.currentTimeMillis();
    }

    static void endTimedLock(Context c) {
        boolean wasLocked = Agent.prefs(c).getLong("lockUntil", 0) != 0;
        Agent.prefs(c).edit().remove("lockUntil").remove("lockMsg").apply();
        try {
            Agent.dpm(c).setDeviceOwnerLockScreenInfo(Agent.admin(c), null);
            Agent.dpm(c).setLockTaskPackages(Agent.admin(c), Kiosk.active(c) ? Kiosk.lockTaskPackages(c) : new String[0]);
        } catch (Exception ignored) {
        }
        if (wasLocked) Agent.addEvent(c, "lock", "Timed lock ended");
    }

    static String unlock(Context c) {
        endTimedLock(c);
        return "unlocked";
    }

    /** Adds and joins a Wi-Fi network (WPA/WPA2 password or open). Allowed for device owners. */
    static String addWifi(Context c, String ssid, String password) throws Exception {
        WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiConfiguration conf = new WifiConfiguration();
        conf.SSID = "\"" + ssid + "\"";
        if (password == null || password.isEmpty()) {
            conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        } else {
            conf.preSharedKey = "\"" + password + "\"";
        }
        int id = wm.addNetwork(conf);
        if (id == -1) throw new Exception("the phone refused to add " + ssid);
        wm.enableNetwork(id, true);
        wm.reconnect();
        return "added Wi-Fi network " + ssid;
    }

    /** True when the agent is allowed to set or clear the screen lock PIN. */
    static boolean pinControlActive(Context c) {
        try {
            return Agent.dpm(c).isResetPasswordTokenActive(Agent.admin(c));
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] token(Context c) {
        String stored = Agent.prefs(c).getString("rpToken", null);
        if (stored != null) return Base64.decode(stored, Base64.NO_WRAP);
        byte[] t = new byte[32];
        new SecureRandom().nextBytes(t);
        Agent.prefs(c).edit().putString("rpToken", Base64.encodeToString(t, Base64.NO_WRAP)).apply();
        return t;
    }

    private static void ensureToken(Context c) throws Exception {
        DevicePolicyManager dpm = Agent.dpm(c);
        ComponentName admin = Agent.admin(c);
        byte[] token = token(c);
        if (!Agent.prefs(c).getBoolean("rpTokenSet", false)) {
            if (!dpm.setResetPasswordToken(admin, token)) throw new Exception("this phone does not allow PIN control");
            Agent.prefs(c).edit().putBoolean("rpTokenSet", true).apply();
        }
        if (!dpm.isResetPasswordTokenActive(admin)) {
            throw new Exception("this phone already has a screen lock; open the agent, choose Administrator, and tap Activate PIN control once");
        }
    }

    static String setPin(Context c, String pin) throws Exception {
        ensureToken(c);
        if (!Agent.dpm(c).resetPasswordWithToken(Agent.admin(c), pin, token(c), 0)) {
            throw new Exception("Android rejected that PIN (too short or too simple)");
        }
        return "screen lock PIN set";
    }

    static String clearPin(Context c) throws Exception {
        ensureToken(c);
        if (!Agent.dpm(c).resetPasswordWithToken(Agent.admin(c), "", token(c), 0)) {
            throw new Exception("could not remove the screen lock");
        }
        return "screen lock removed";
    }
}
