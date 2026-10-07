package com.familymdm.agent;

import android.content.Context;

import java.util.Set;

/**
 * Whole-package hides via Android's own setApplicationHidden -- an OS-level block, not a reactive
 * bounce: the app simply can't launch or run at all while hidden, instantly, with no flash. Phone-
 * only, same as AppBlocklist -- nothing here touches a dashboard. Pause/Remove work the same way as
 * AppBlocklist's: Pause un-hides the app temporarily without forgetting it, Remove un-hides it and
 * drops it from the list for good.
 */
final class WholeAppBlocklist {
    private static final String PREF_KEY = "hiddenPackages";
    private static final String PAUSED_KEY = "hiddenPackagesPaused";

    private WholeAppBlocklist() {}

    static Set<String> list(Context c) {
        return Agent.getSet(c, PREF_KEY);
    }

    static Set<String> pausedList(Context c) {
        return Agent.getSet(c, PAUSED_KEY);
    }

    static boolean isPaused(Context c, String pkg) {
        return pausedList(c).contains(pkg);
    }

    static void add(Context c, String pkg) {
        Set<String> set = list(c);
        set.add(pkg);
        Agent.putSet(c, PREF_KEY, set);
        applyHidden(c, pkg, true);
    }

    static void remove(Context c, String pkg) {
        Set<String> set = list(c);
        set.remove(pkg);
        Agent.putSet(c, PREF_KEY, set);
        Set<String> paused = pausedList(c);
        if (paused.remove(pkg)) Agent.putSet(c, PAUSED_KEY, paused);
        applyHidden(c, pkg, false);
    }

    static void setPaused(Context c, String pkg, boolean paused) {
        Set<String> set = pausedList(c);
        if (paused) set.add(pkg); else set.remove(pkg);
        Agent.putSet(c, PAUSED_KEY, set);
        applyHidden(c, pkg, !paused);
    }

    private static void applyHidden(Context c, String pkg, boolean hidden) {
        try {
            Agent.dpm(c).setApplicationHidden(Agent.admin(c), pkg, hidden);
        } catch (Exception ignored) {
        }
    }
}
