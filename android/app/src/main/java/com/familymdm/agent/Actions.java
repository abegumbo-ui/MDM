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
                    .remove("lockBy") // the administrator's own lock, not a screen-time one
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
        Agent.prefs(c).edit().remove("lockUntil").remove("lockMsg").remove("lockBy").apply();
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

    /**
     * Gets PIN control ready in the background, so "Set PIN" and "Remove screen lock" work from the dashboard the first time
     * (before, the token was only made by the first attempt, which then failed). A phone with no screen lock is ready at once;
     * a phone that already has one still needs the one-time "Activate PIN control" confirmation on the phone itself, because
     * Android only lets the person who knows the lock approve that. Never throws.
     */
    static void prepareTokenQuietly(Context c) {
        try {
            android.content.SharedPreferences p = Agent.prefs(c);
            DevicePolicyManager dpm = Agent.dpm(c);
            boolean active = false;
            try {
                active = dpm.isResetPasswordTokenActive(Agent.admin(c));
            } catch (Exception ignored) {
                // no token yet
            }
            if (active) {
                p.edit().putBoolean("rpTokenSet", true).apply();
                return;
            }
            if (p.getBoolean("rpTokenSet", false)) return;
            // Adding a token REPLACES the one before it and makes the new one inactive again, so this must never run in a loop (it did: every
            // check-in, after a failure that was not ours, which undid the person's "Activate PIN control" within seconds). At most once in 30 minutes.
            long now = System.currentTimeMillis();
            if (now - p.getLong("rpTokenTry", 0) < 30L * 60 * 1000) return;
            p.edit().putLong("rpTokenTry", now).apply();
            if (dpm.setResetPasswordToken(Agent.admin(c), token(c))) p.edit().putBoolean("rpTokenSet", true).apply();
        } catch (Exception ignored) {
        }
    }

    /**
     * How strong the phone's current screen lock is, in words ("" when it cannot be told). Android never tells an app what the
     * lock is (nobody can read a PIN, a pattern or a password that someone chose), and it does not say pattern or PIN either:
     * only how strong it is.
     */
    static String lockStrength(Context c) {
        try {
            if (!hasScreenLock(c)) return "none";
            if (Build.VERSION.SDK_INT < 29) return "set";
            switch (Agent.dpm(c).getPasswordComplexity()) {
                case DevicePolicyManager.PASSWORD_COMPLEXITY_LOW: return "pattern or simple PIN";
                case DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM: return "PIN or password (medium)";
                case DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH: return "long PIN or password (strong)";
                default: return "set";
            }
        } catch (Exception e) {
            return hasScreenLock(c) ? "set" : "none";
        }
    }

    /**
     * From the dashboard ("Show the PIN-control prompt on the phone"): makes Android's own "confirm your screen lock" screen appear on the
     * phone. Whoever is holding it enters the CURRENT lock once; that is what switches PIN control on (Android lets nobody else approve it,
     * not even the manager of the phone). A phone with no lock needs nothing: it is ready at once.
     */
    static String askPinControl(Context c) {
        prepareTokenQuietly(c);
        if (!hasScreenLock(c)) return "this phone has no screen lock, so PIN control is ready already";
        if (pinControlActive(c)) return "PIN control is already on";
        KeyguardManager km = c.getSystemService(KeyguardManager.class);
        Intent i = km.createConfirmDeviceCredentialIntent("Turn on PIN control", "Enter the current screen lock once. This lets the dashboard set or remove the lock.");
        if (i == null) return "there is nothing to confirm on this phone";
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
        return "the prompt is showing on the phone: enter the current screen lock there once to turn PIN control on";
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
        prepareTokenQuietly(c); // adds the token once if it never was (never again while one exists)
        boolean active = false;
        try {
            active = dpm.isResetPasswordTokenActive(admin);
        } catch (Exception ignored) {
        }
        if (active) return;
        if (!Agent.prefs(c).getBoolean("rpTokenSet", false)) {
            throw new Exception("this phone has not accepted PIN control yet (it tries again by itself every half hour); the dashboard's Lock page shows when it is ON");
        }
        throw new Exception("PIN control needs the phone's current lock entered once: press Turn on PIN control on the dashboard's Lock page, or on the phone go to LockGuard, Administrator, Security, Lock, Activate PIN Control");
    }

    static String setPin(Context c, String pin) throws Exception {
        ensureToken(c);
        if (!Agent.dpm(c).resetPasswordWithToken(Agent.admin(c), pin, token(c), 0)) {
            throw new Exception("Android rejected that PIN (too short or too simple)");
        }
        // So the dashboard can say "a PIN you set from here" (Android itself cannot tell a pattern from a PIN or a password).
        Agent.prefs(c).edit().putBoolean("lockByAgent", true).putString("lockByAgentStrength", lockStrength(c)).apply();
        return "screen lock PIN set";
    }

    static String clearPin(Context c) throws Exception {
        ensureToken(c);
        if (!Agent.dpm(c).resetPasswordWithToken(Agent.admin(c), "", token(c), 0)) {
            throw new Exception("could not remove the screen lock");
        }
        Agent.prefs(c).edit().putBoolean("lockByAgent", false).apply();
        return "screen lock removed";
    }

    /** The app's own front-screen PIN (AppPin) -- separate from the phone's screen lock above.
     * Same effect as AdminActivity's own "Turn On App Lock" / "Change App PIN", just reachable
     * from the dashboard too. */
    static String setAppLockPin(Context c, String pin) throws Exception {
        if (pin == null || pin.length() < 4) throw new Exception("App PIN needs to be at least 4 characters");
        AppPin.set(c, pin);
        Agent.prefs(c).edit().putBoolean("appLock", true).apply();
        return "app lock on";
    }

    static String clearAppLockPin(Context c) {
        AppPin.clear(c);
        Agent.prefs(c).edit().putBoolean("appLock", false).apply();
        return "app lock off";
    }
}
