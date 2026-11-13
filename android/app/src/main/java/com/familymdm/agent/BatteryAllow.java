package com.familymdm.agent;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * Keeps the agent out of battery optimization by itself ("Unrestricted" battery, so Android never stops it in the
 * background). Android only lets an app do this by showing the user a question, so the agent asks it (at most every
 * 6 hours while it is not unrestricted) and its accessibility service presses "Allow" on that one question.
 */
final class BatteryAllow {
    private BatteryAllow() {}

    private static final String ASKED = "batteryAskAt";

    static boolean unrestricted(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    /** Shows Android's "Allow app to always run in the background?" question, when it is needed and not asked lately. */
    static void ask(Context c) {
        if (unrestricted(c)) return;
        long now = System.currentTimeMillis();
        if (now - Agent.prefs(c).getLong(ASKED, 0) < 6L * 3600 * 1000) return;
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + c.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Agent.prefs(c).edit().putLong(ASKED, now).apply();
            c.startActivity(i);
        } catch (Exception ignored) {
            // not allowed from here right now (or not on this build): tried again later
        }
    }

    /** For the accessibility service, on every window change: presses "Allow" on that question (only within 2 minutes of asking it). */
    static void onWindow(AccessibilityService s, String pkg) {
        long asked = Agent.prefs(s).getLong(ASKED, 0);
        if (asked == 0 || System.currentTimeMillis() - asked > 120000 || unrestricted(s)) return;
        if (pkg == null || (!pkg.contains("settings") && !pkg.contains("permissioncontroller") && !pkg.contains("packageinstaller"))) return;
        AccessibilityNodeInfo root = s.getRootInActiveWindow();
        if (root == null) return;
        try {
            // It must be this question: it names the background, not just any "Allow".
            List<AccessibilityNodeInfo> about = root.findAccessibilityNodeInfosByText("background");
            if (about == null || about.isEmpty()) return;
            List<AccessibilityNodeInfo> buttons = root.findAccessibilityNodeInfosByText("Allow");
            if (buttons == null) return;
            for (AccessibilityNodeInfo b : buttons) {
                CharSequence t = b.getText();
                if (t == null || !t.toString().trim().equalsIgnoreCase("allow")) continue;
                AccessibilityNodeInfo target = b;
                while (target != null && !target.isClickable()) target = target.getParent();
                if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Agent.addEvent(s, "app", "The agent allowed itself to run in the background (unrestricted battery)");
                    return;
                }
            }
        } catch (Exception ignored) {
            // the window changed under us: the next event tries again
        }
    }
}
