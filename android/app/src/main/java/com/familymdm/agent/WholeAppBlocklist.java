package com.familymdm.agent;

import android.content.Context;

import org.json.JSONObject;

import java.util.Set;

/**
 * Whole-package hides via Android's own setApplicationHidden -- an OS-level block, not a reactive
 * bounce: the app simply can't launch or run at all while hidden, instantly, with no flash.
 * Pause/Remove work the same way as AppBlocklist's: Pause un-hides the app temporarily without
 * forgetting it, Remove un-hides it and drops it from the list for good. Synced to the dashboard
 * the same "most recent change wins" way as the simple on/off settings: a rev that bumps on every
 * local change, round-tripped through config.wholeAppBlocklist.
 */
final class WholeAppBlocklist {
    private static final String PREF_KEY = "hiddenPackages";
    private static final String PAUSED_KEY = "hiddenPackagesPaused";
    private static final String REV_KEY = "wholeAppBlocklistRev";

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

    static long rev(Context c) {
        return Agent.prefs(c).getLong(REV_KEY, 0);
    }

    private static void bump(Context c) {
        Agent.prefs(c).edit().putLong(REV_KEY, System.currentTimeMillis()).apply();
    }

    /** Bumps the rev with no other change -- the phone's existing list is the thing being pushed
     * up as-is, not a new edit. See the migration-safety note in PolicyApplier.apply(). */
    static void seed(Context c) {
        bump(c);
    }

    static void add(Context c, String pkg) {
        Set<String> set = list(c);
        set.add(pkg);
        Agent.putSet(c, PREF_KEY, set);
        applyHidden(c, pkg, true);
        bump(c);
    }

    static void remove(Context c, String pkg) {
        Set<String> set = list(c);
        set.remove(pkg);
        Agent.putSet(c, PREF_KEY, set);
        Set<String> paused = pausedList(c);
        if (paused.remove(pkg)) Agent.putSet(c, PAUSED_KEY, paused);
        applyHidden(c, pkg, false);
        bump(c);
    }

    static void setPaused(Context c, String pkg, boolean paused) {
        Set<String> set = pausedList(c);
        if (paused) set.add(pkg); else set.remove(pkg);
        Agent.putSet(c, PAUSED_KEY, set);
        applyHidden(c, pkg, !paused);
        bump(c);
    }

    private static void applyHidden(Context c, String pkg, boolean hidden) {
        try {
            Agent.dpm(c).setApplicationHidden(Agent.admin(c), pkg, hidden);
        } catch (Exception ignored) {
        }
    }

    /** {pkg: {paused: bool}}, for the sync body. */
    static JSONObject toJson(Context c) {
        JSONObject out = new JSONObject();
        Set<String> paused = pausedList(c);
        try {
            for (String pkg : list(c)) {
                JSONObject entry = new JSONObject();
                entry.put("paused", paused.contains(pkg));
                out.put(pkg, entry);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Reconciles the local list to match the dashboard's current map: adds, removes, and
     * pause-state changes are all applied, same as any other policy field. Called on every sync,
     * same as a boolean field -- the whole point is the dashboard becomes a second place that can
     * genuinely drive this, not just a bystander. */
    static void adopt(Context c, JSONObject map) {
        if (map == null) return;
        Set<String> wantPkgs = new java.util.LinkedHashSet<>();
        Set<String> wantPaused = new java.util.LinkedHashSet<>();
        java.util.Iterator<String> it = map.keys();
        while (it.hasNext()) {
            String pkg = it.next();
            if (!pkg.matches("^[A-Za-z0-9_.]{1,200}$")) continue;
            wantPkgs.add(pkg);
            if (map.optJSONObject(pkg) != null && map.optJSONObject(pkg).optBoolean("paused", false)) wantPaused.add(pkg);
        }
        Set<String> have = list(c);
        for (String pkg : wantPkgs) if (!have.contains(pkg)) applyHidden(c, pkg, true);
        for (String pkg : have) if (!wantPkgs.contains(pkg)) applyHidden(c, pkg, false);
        Set<String> havePaused = pausedList(c);
        for (String pkg : wantPkgs) applyHidden(c, pkg, !wantPaused.contains(pkg));
        if (!have.equals(wantPkgs)) Agent.putSet(c, PREF_KEY, wantPkgs);
        if (!havePaused.equals(wantPaused)) Agent.putSet(c, PAUSED_KEY, wantPaused);
    }
}
