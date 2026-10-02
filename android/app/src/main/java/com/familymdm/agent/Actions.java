package com.familymdm.agent;

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.util.Base64;

import java.security.SecureRandom;

/** Device actions shared by dashboard commands and the on-device admin panel. */
final class Actions {
    private Actions() {}

    static boolean hasScreenLock(Context c) {
        return c.getSystemService(KeyguardManager.class).isDeviceSecure();
    }

    static String lock(Context c) {
        boolean secure = hasScreenLock(c);
        Agent.dpm(c).lockNow();
        return secure
                ? "screen locked"
                : "screen turned off, but this phone has no screen lock (PIN) set, so anyone can wake it. Use Set PIN to make Lock real";
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
