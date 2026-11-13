package com.familymdm.agent;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "Is there a screen or app called ...?" -- the dashboard's "Block a screen by its name".
 *
 * The phone looks the typed name up in (1) the titles of screens it has shown (remembered as the person uses the
 * phone) and (2) the names of every app and of every screen those apps declare. A name that is nowhere gets the answer
 * "no such screen or app", so a wrong name can never become a block. The answer goes back as the command's result.
 */
final class ScreenFinder {
    private ScreenFinder() {}

    private static final String BOOK = "seenScreens"; // "package<SEP>title", at most BOOK_CAP of them
    private static final int BOOK_CAP = 800;
    private static final int MAX_FOUND = 20;
    private static final int MAX_SIMILAR = 6;
    private static Set<String> book; // read once, kept in memory (every screen change calls note)

    private static synchronized Set<String> book(Context c) {
        if (book == null) book = new HashSet<>(Agent.getSet(c, BOOK));
        return book;
    }

    /** Remembers that this screen exists (called for every screen change; cheap, stores only new titles). */
    static void note(Context c, String pkg, String title) {
        if (pkg == null || title == null) return;
        if (pkg.equals(c.getPackageName()) || pkg.equals("com.android.systemui")) return;
        String t = title.trim().replaceAll("\\s+", " ");
        if (t.length() < 3 || t.length() > 60 || PartBlocker.isGenericText(c, pkg, t)) return;
        String entry = pkg + PartBlocker.SEP + t;
        synchronized (ScreenFinder.class) {
            Set<String> b = book(c);
            if (b.size() >= BOOK_CAP || !b.add(entry)) return;
            Agent.putSet(c, BOOK, new HashSet<>(b));
        }
    }

    static String norm(CharSequence s) {
        return s == null ? "" : s.toString().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static String appLabel(PackageManager pm, ApplicationInfo ai) {
        try {
            CharSequence l = pm.getApplicationLabel(ai);
            return l == null ? ai.packageName : l.toString();
        } catch (Exception e) {
            return ai.packageName;
        }
    }

    private static void add(JSONArray out, Set<String> keys, String pkg, String app, String title, String kind) throws Exception {
        if (!keys.add(pkg + "\u001f" + title + "\u001f" + kind)) return;
        out.put(new JSONObject().put("p", pkg).put("a", app).put("t", title).put("k", kind));
    }

    /** The answer, as JSON: {"name":..., "found":[{p,a,t,k}], "similar":[{p,a,t,k}]}. k is "app" or "screen". */
    static String find(Context c, String name) throws Exception {
        String q = norm(name);
        if (q.length() < 2) throw new Exception("Type the name of the screen first");
        PackageManager pm = c.getPackageManager();
        JSONArray found = new JSONArray(), similar = new JSONArray();
        Set<String> keys = new HashSet<>(), keysSim = new HashSet<>();
        // 1. screens this phone has shown.
        for (String e : new HashSet<>(book(c))) {
            int i = e.indexOf(PartBlocker.SEP);
            if (i <= 0) continue;
            String pkg = e.substring(0, i), title = e.substring(i + 1), n = norm(title);
            if (n.equals(q)) {
                if (found.length() < MAX_FOUND) add(found, keys, pkg, labelOf(pm, pkg), title, "screen");
            } else if (n.contains(q) && similar.length() < MAX_SIMILAR) {
                add(similar, keysSim, pkg, labelOf(pm, pkg), title, "screen");
            }
        }
        // 2. every app and every screen the apps declare (one package at a time: all at once is too big for the system to hand over).
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo ai : apps) {
            if (ai.packageName.equals(c.getPackageName())) continue;
            String app = appLabel(pm, ai), appN = norm(app);
            if (appN.equals(q)) {
                if (found.length() < MAX_FOUND) add(found, keys, ai.packageName, app, app, "app");
            } else if (appN.contains(q) && similar.length() < MAX_SIMILAR) {
                add(similar, keysSim, ai.packageName, app, app, "app");
            }
            PackageInfo pi;
            try {
                pi = pm.getPackageInfo(ai.packageName, PackageManager.GET_ACTIVITIES);
            } catch (Exception e) {
                continue;
            }
            if (pi.activities == null) continue;
            for (ActivityInfo act : pi.activities) {
                CharSequence l;
                try {
                    l = act.loadLabel(pm);
                } catch (Exception e) {
                    continue;
                }
                String n = norm(l);
                if (n.isEmpty() || n.equals(appN)) continue; // a screen that only carries the app's name is the app
                if (n.equals(q)) {
                    if (found.length() < MAX_FOUND) add(found, keys, ai.packageName, app, l.toString().trim(), "screen");
                } else if (n.contains(q) && similar.length() < MAX_SIMILAR) {
                    add(similar, keysSim, ai.packageName, app, l.toString().trim(), "screen");
                }
            }
            if (found.length() >= MAX_FOUND) break;
        }
        return new JSONObject().put("name", name.trim()).put("found", found).put("similar", similar).toString();
    }

    private static String labelOf(PackageManager pm, String pkg) {
        try {
            return appLabel(pm, pm.getApplicationInfo(pkg, 0));
        } catch (Exception e) {
            return pkg;
        }
    }
}
