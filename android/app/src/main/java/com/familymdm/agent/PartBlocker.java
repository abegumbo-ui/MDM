package com.familymdm.agent;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * "Tap to block": block one part of an app (a button, a tab, a feed -- YouTube Shorts, Instagram
 * Reels...) instead of the whole app.
 *
 * Picking (Administrator → Apps → Tap to block): a small floating "Pick" button appears. Open the
 * app, go to the part, tap "Pick", then tap the part. It is outlined in red with what was found;
 * "Bigger" / "Smaller" choose the surrounding area or a smaller piece, then "Cover it" or "Close the
 * screen". The part is remembered by what identifies it on the screen (view id, description, text),
 * not by where it is, so it is found again when the app moves it. Saved like every other rule (goes
 * to the dashboard, which can remove it).
 *
 * Blocking: whenever the app's screen changes, its parts are checked (at most every 300 ms, up to
 * 1500 pieces). "cover": a dark "Blocked" box over it, which also takes any tap. "close": back out.
 * Covers are the accessibility service's own overlay windows (no extra permission).
 * Everything runs inside BlockAccessibilityService, which must be switched on once.
 */
final class PartBlocker {
    private PartBlocker() {}

    /* ------------------------------------------------------------------ rules ------------------------------------------------------------------ */

    private static volatile String cachedFrom;
    private static volatile List<JSONObject> cached = new ArrayList<>();

    static List<JSONObject> rules(Context c) {
        JSONArray a = PhoneRules.current(c).optJSONArray("blockedParts");
        String key = a == null ? "" : a.toString();
        if (!key.equals(cachedFrom)) {
            List<JSONObject> out = new ArrayList<>();
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) out.add(o);
            }
            cached = out;
            cachedFrom = key;
        }
        return cached;
    }

    private static boolean anyFor(Context c, String pkg) {
        for (JSONObject r : rules(c)) if (pkg.equals(r.optString("pkg"))) return true;
        return false;
    }

    static boolean matches(JSONObject r, AccessibilityNodeInfo n) {
        String viewId = r.optString("viewId", ""), desc = r.optString("desc", ""), text = r.optString("text", "");
        if (!viewId.isEmpty()) {
            if (!viewId.equals(n.getViewIdResourceName())) return false;
            // A view id shared by many items (rows of a list): the description or text narrows it down.
            if (!desc.isEmpty() && n.getContentDescription() != null && !desc.contentEquals(n.getContentDescription())) return false;
            return true;
        }
        if (!desc.isEmpty()) return n.getContentDescription() != null && desc.contentEquals(n.getContentDescription());
        return !text.isEmpty() && n.getText() != null && text.contentEquals(n.getText());
    }

    /* ------------------------------------------------------------------ blocking ------------------------------------------------------------------ */

    private static long lastScan, lastBack;
    private static String coveredPkg;
    private static final List<View> covers = new ArrayList<>();

    /** Called for every screen change of any app. */
    static void onScreen(AccessibilityService s, String pkg) {
        if (EmergencyUnlock.isActive(s)) { if (!covers.isEmpty()) clearCovers(s); return; } // every restriction is off during an emergency unlock
        if (pkg == null || pkg.equals(s.getPackageName()) || pkg.equals("com.android.systemui")) return;
        // A screen that was blocked closes even while the picker bar is open (only covers of parts wait for it to close).
        if (screenBlockedNow(s, pkg)) return;
        if (picking) return;
        if (!anyFor(s, pkg)) {
            if (coveredPkg != null && !pkg.equals(coveredPkg)) clearCovers(s);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastScan < 300) return;
        lastScan = now;
        AccessibilityNodeInfo root = s.getRootInActiveWindow();
        if (root == null || root.getPackageName() == null || !pkg.contentEquals(root.getPackageName())) return;
        List<Rect> toCover = new ArrayList<>();
        boolean close = false;
        String closedWhat = null;
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int seen = 0;
        List<JSONObject> mine = new ArrayList<>();
        for (JSONObject r : rules(s)) if (pkg.equals(r.optString("pkg"))) mine.add(r);
        while (!q.isEmpty() && seen++ < 1500) {
            AccessibilityNodeInfo n = q.poll();
            if (n.isVisibleToUser()) {
                for (JSONObject r : mine) {
                    if (!matches(r, n)) continue;
                    if ("close".equals(r.optString("mode"))) {
                        close = true;
                        closedWhat = r.optString("label");
                    } else {
                        Rect b = new Rect();
                        n.getBoundsInScreen(b);
                        if (b.width() > 0 && b.height() > 0) toCover.add(b);
                    }
                }
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo ch = n.getChild(i);
                if (ch != null) q.add(ch);
            }
        }
        if (close && now - lastBack > 800) {
            lastBack = now;
            clearCovers(s);
            s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            Agent.addEvent(s, "restriction", "Closed a blocked part of an app: " + closedWhat + " (" + pkg + ")");
            return;
        }
        showCovers(s, pkg, toCover);
    }

    private static List<Rect> shown = new ArrayList<>();

    private static void showCovers(AccessibilityService s, String pkg, List<Rect> rects) {
        // Unchanged (the usual case while a video plays or a feed updates): leave the covers as they are, no flicker.
        if (pkg.equals(coveredPkg) && rects.equals(shown) && covers.size() == Math.min(rects.size(), 12)) return;
        clearCovers(s);
        shown = new ArrayList<>(rects);
        if (rects.isEmpty()) return;
        WindowManager wm = s.getSystemService(WindowManager.class);
        for (Rect r : rects) {
            if (covers.size() >= 12) break;
            TextView box = new TextView(s);
            box.setText("Blocked");
            box.setTextColor(Color.WHITE);
            box.setGravity(Gravity.CENTER);
            box.setBackgroundColor(0xF0303038);
            box.setOnTouchListener((v, e) -> true); // takes every tap
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(r.width(), r.height(),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = r.left;
            lp.y = r.top;
            try {
                wm.addView(box, lp);
                covers.add(box);
            } catch (Exception ignored) {
            }
        }
        coveredPkg = pkg;
    }

    static void clearCovers(Context s) {
        WindowManager wm = s.getSystemService(WindowManager.class);
        for (View v : covers) {
            try {
                wm.removeView(v);
            } catch (Exception ignored) {
            }
        }
        covers.clear();
        coveredPkg = null;
        shown = new ArrayList<>();
    }

    /* ------------------------------------------------------------------ picking ------------------------------------------------------------------ */

    static volatile boolean picking;
    private static View pickButton, capture, outline, panel;
    private static List<AccessibilityNodeInfo> chain = new ArrayList<>();
    private static int chainAt;
    private static String chainPkg;

    /** Starts picking. Returns a message if it cannot. */
    static String startPicking(Context c) {
        BlockAccessibilityService s = BlockAccessibilityService.instance;
        if (s == null) return "Switch on MDM Agent in Settings → Accessibility first (Rules → Safety → Health check has a button).";
        s.getMainExecutor().execute(() -> showPickButton(s));
        return null;
    }

    private static WindowManager.LayoutParams lp(int w, int h, boolean touchable) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | (touchable ? 0 : WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        return lp;
    }

    private static void showPickButton(AccessibilityService s) {
        stopPicking(s);
        picking = true;
        clearCovers(s);
        WindowManager wm = s.getSystemService(WindowManager.class);
        // Two rows that fit the screen: the hint with a ✕ (always visible, top right), then the two buttons.
        LinearLayout bar = new LinearLayout(s);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(Ui.dp(s, 12), Ui.dp(s, 8), Ui.dp(s, 12), Ui.dp(s, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF04F46E5);
        bg.setCornerRadius(Ui.dp(s, 22));
        bar.setBackground(bg);
        LinearLayout top = new LinearLayout(s);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView hint = new TextView(s);
        hint.setText("Go to the screen, then press ✓.\nOr Pick a part to block one button.");
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(14);
        top.addView(hint, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button done = new Button(s);
        done.setText("✕");
        done.setTextSize(20);
        done.setAllCaps(false);
        done.setMinWidth(0);
        done.setMinimumWidth(0);
        done.setPadding(Ui.dp(s, 14), 0, Ui.dp(s, 14), 0);
        done.setContentDescription("Close");
        done.setOnClickListener(v -> stopPicking(s));
        top.addView(done, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(s, 46)));
        LinearLayout buttons = new LinearLayout(s);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button check = new Button(s);
        check.setText("✓ Block this screen");
        check.setAllCaps(false);
        check.setOnClickListener(v -> blockThisScreen(s));
        Button pick = new Button(s);
        pick.setText("Pick a part");
        pick.setAllCaps(false);
        pick.setOnClickListener(v -> showCapture(s));
        buttons.addView(check, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        buttons.addView(pick, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(top);
        bar.addView(buttons);
        WindowManager.LayoutParams p = lp(s.getResources().getDisplayMetrics().widthPixels - Ui.dp(s, 24), WindowManager.LayoutParams.WRAP_CONTENT, true);
        p.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        p.y = Ui.dp(s, 90);
        try {
            wm.addView(bar, p);
            pickButton = bar;
        } catch (Exception e) {
            picking = false;
        }
    }

    /** The screen the person is on right now (the app's own screen, not ours). */
    static volatile String currentScreen, currentScreenPkg, currentTitle;

    // ---------- a screen's "fingerprint": the words at the top of it ----------

    static final String SEP = "\u001f";

    private static volatile long screensAt;
    private static volatile java.util.Set<String> screensCache = new java.util.TreeSet<>();

    /**
     * Screens blocked by what is written at the top of them (works when the title cannot be read). They live in the
     * phone's settings ("blockedScreens"), so the dashboard lists and removes them too.
     */
    static java.util.Set<String> blockedScreens(Context c) {
        long now = System.currentTimeMillis();
        if (now - screensAt < 1500) return new java.util.TreeSet<>(screensCache);
        java.util.Set<String> out = new java.util.TreeSet<>();
        JSONArray a = PhoneRules.current(c).optJSONArray("blockedScreens");
        for (int i = 0; a != null && i < a.length(); i++) {
            String s = a.optString(i, "");
            if (!s.isEmpty()) out.add(s);
        }
        // Screens blocked before they were kept in the settings (local to the phone): moved over once.
        java.util.Set<String> legacy = Agent.getSet(c, "blockedScreens");
        if (!legacy.isEmpty()) {
            out.addAll(legacy);
            Agent.putSet(c, "blockedScreens", new java.util.HashSet<String>());
            setBlockedScreens(c, out, "kept the screens blocked before");
        }
        screensCache = out;
        screensAt = now;
        return new java.util.TreeSet<>(out);
    }

    static void setBlockedScreens(Context c, java.util.Set<String> screens, String what) {
        screensCache = new java.util.TreeSet<>(screens);
        screensAt = System.currentTimeMillis();
        try {
            PhoneRules.set(c, new JSONObject().put("blockedScreens", new JSONArray(new ArrayList<>(screens))), what);
        } catch (Exception ignored) {
        }
    }

    /** The first words on screen, top to bottom (the title, then the headings), at most max of them. */
    static List<String> topTexts(AccessibilityNodeInfo root, int max) {
        List<Object[]> found = new ArrayList<>();
        if (root == null) return new ArrayList<>();
        Rect screen = new Rect();
        root.getBoundsInScreen(screen);
        int limit = screen.top + Math.max(1, screen.height()) * 42 / 100;
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int seen = 0;
        while (!q.isEmpty() && seen++ < 500) {
            AccessibilityNodeInfo n = q.poll();
            CharSequence t = n.getText();
            if (t != null && !n.isPassword() && n.isVisibleToUser()) {
                String x = t.toString().trim().replaceAll("\\s+", " ");
                if (x.length() >= 2 && x.length() <= 60 && !x.matches("[\\d\\s:.,%/+\\-]+")) {
                    Rect r = new Rect();
                    n.getBoundsInScreen(r);
                    if (r.top < limit && r.height() > 0) found.add(new Object[]{r.top, r.left, x});
                }
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo ch = n.getChild(i);
                if (ch != null) q.add(ch);
            }
        }
        java.util.Collections.sort(found, (a, b) -> ((Integer) a[0]).equals(b[0]) ? ((Integer) a[1]).compareTo((Integer) b[1]) : ((Integer) a[0]).compareTo((Integer) b[0]));
        List<String> out = new ArrayList<>();
        for (Object[] f : found) {
            String x = (String) f[2];
            if (!out.contains(x)) out.add(x);
            if (out.size() >= max) break;
        }
        return out;
    }

    static String signatureOf(String pkg, List<String> texts) {
        return pkg + SEP + String.join(SEP, texts);
    }

    static String signatureLabel(String sig) {
        String[] p = sig.split(SEP);
        StringBuilder b = new StringBuilder();
        for (int i = 1; i < p.length; i++) b.append(i > 1 ? " › " : "").append(p[i]);
        return b.toString();
    }

    private static long lastSigScan;
    private static long lastSigBack;

    // ---------- built-in protection: the MDM app's own Settings pages and the certificate pages ----------

    /** The Settings pages where the certificate is listed or removed (their titles, as shown at the top of the page). */
    private static final String[] CERTIFICATE_PAGES = {"user credentials", "trusted credentials", "view security certificates",
            "user certificates", "install a certificate", "install from device storage", "ca certificate", "credential storage",
            "security certificates", "certificates"};

    private static boolean protectOn(Context c) {
        return PhoneRules.current(c).optBoolean("protectScreens", true);
    }

    private static boolean isSettingsApp(String pkg) {
        return pkg != null && pkg.contains("settings") && !pkg.equals("com.familymdm.agent");
    }

    /**
     * "Protect the MDM app": in Settings, the page of this app (its accessibility switch, App info, notifications...)
     * and the pages that list or remove the phone's certificate close the moment they open. Off from the dashboard,
     * and not applied during an emergency unlock.
     */
    private static boolean protectedPage(Context c, List<String> top) {
        String label = appLabel(c, c.getPackageName());
        for (int i = 0; i < top.size() && i < 4; i++) {
            String t = top.get(i);
            if (label != null && t.equalsIgnoreCase(label)) return true;
            if (i < 3) for (String p : CERTIFICATE_PAGES) if (t.equalsIgnoreCase(p)) return true;
        }
        return false;
    }

    /** Is the screen open now one that was blocked by its fingerprint (or a protected Settings page)? Backs out of it if so. */
    private static boolean screenBlockedNow(AccessibilityService s, String pkg) {
        java.util.Set<String> all = blockedScreens(s);
        boolean guard = isSettingsApp(pkg) && protectOn(s);
        List<String> mine = new ArrayList<>();
        for (String sig : all) if (sig.startsWith(pkg + SEP)) mine.add(sig);
        if (mine.isEmpty() && !guard) return false;
        long now = System.currentTimeMillis();
        if (now - lastSigScan < 250) return false;
        lastSigScan = now;
        AccessibilityNodeInfo root = s.getRootInActiveWindow();
        if (root == null || root.getPackageName() == null || !pkg.contentEquals(root.getPackageName())) return false;
        List<String> here = topTexts(root, 14);
        if (guard && protectedPage(s, here)) {
            if (now - lastSigBack > 700) {
                lastSigBack = now;
                s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                Agent.addEvent(s, "restriction", "A protected Settings page opened (" + String.join(" › ", here.subList(0, Math.min(3, here.size()))) + ") -- closed instantly");
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        android.widget.Toast.makeText(s.getApplicationContext(), "This setting is protected on this phone.", android.widget.Toast.LENGTH_SHORT).show());
            }
            return true;
        }
        for (String sig : mine) {
            String[] p = sig.split(SEP);
            boolean all3 = p.length > 1;
            for (int i = 1; i < p.length && all3; i++) {
                boolean found = false;
                for (String h : here) if (h.equalsIgnoreCase(p[i])) { found = true; break; }
                if (!found) all3 = false;
            }
            if (!all3) continue;
            if (now - lastSigBack > 700) {
                lastSigBack = now;
                s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                Agent.addEvent(s, "restriction", "Blocked screen opened (" + signatureLabel(sig) + ") -- backed out instantly");
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        android.widget.Toast.makeText(s.getApplicationContext(), "This screen is not allowed on this phone.", android.widget.Toast.LENGTH_SHORT).show());
            }
            return true;
        }
        return false;
    }

    /** Screens blocked by their title (Settings pages, which all have the same class name). */
    static java.util.Set<String> blockedTitles(Context c) {
        return Agent.getSet(c, "blockedTitles");
    }

    static void noteScreen(String pkg, String cls, String title) {
        noteScreen(pkg, cls);
        if (pkg != null && !pkg.equals("com.familymdm.agent") && !pkg.equals("com.android.systemui") && cls != null
                && !cls.startsWith("android.widget.") && !cls.startsWith("android.view.")) currentTitle = title;
    }

    /** One class for many screens (Settings' SubSettings and the like): those are told apart by their title. */
    static boolean sharedClass(String pkg, String cls) {
        return pkg.contains("settings") || cls.endsWith("SubSettings") || cls.endsWith("SettingsActivity") || cls.endsWith("$SubSettings");
    }

    static void noteScreen(String pkg, String cls) {
        if (pkg == null || cls == null || pkg.equals("com.familymdm.agent") || pkg.equals("com.android.systemui")) return;
        if (cls.startsWith("android.widget.") || cls.startsWith("android.view.")) return; // a popup, not a screen
        currentScreenPkg = pkg;
        currentScreen = pkg + "/" + cls;
    }

    /** "✓ Block this screen": the whole screen that is open now, from now on (Blocked apps/screens). */
    private static void blockThisScreen(AccessibilityService s) {
        String comp = currentScreen;
        if (comp == null) {
            showPanel(s, "Open the app and the screen first, then press ✓.", false);
            return;
        }
        String pkg = currentScreenPkg;
        if (isHome(s, pkg) || pkg.equals("com.android.dialer") || pkg.equals("com.google.android.dialer") || pkg.equals("com.samsung.android.dialer") || pkg.equals("com.android.emergency")) {
            showPanel(s, "The home screen and the phone app cannot be blocked (calls, emergency calls). Open another app's screen, or a Settings page.", false);
            return;
        }
        String cls = comp.substring(comp.indexOf('/') + 1);
        String nice = cls.substring(cls.lastIndexOf('.') + 1).replaceAll("(Activity|Fragment)$", "").replaceAll("([a-z])([A-Z])", "$1 $2");
        if (pickButton != null) pickButton.setVisibility(View.GONE);
        String title = currentTitle;
        if (sharedClass(pkg, cls)) {
            // Settings: every page is "SubSettings", so blocking the class would block them all. Told apart by
            // the words at the top of the page instead (its title and first headings).
            AccessibilityNodeInfo root = s.getRootInActiveWindow();
            List<String> top = root != null && root.getPackageName() != null && pkg.contentEquals(root.getPackageName()) ? topTexts(root, 3) : new ArrayList<String>();
            if (!top.isEmpty()) {
                showConfirmScreen(s, pkg, "sig:" + signatureOf(pkg, top), appLabel(s, pkg) + ": " + String.join(" › ", top));
                return;
            }
            if (title != null && !title.trim().isEmpty()) {
                showConfirmScreen(s, pkg, "title:" + pkg + "|" + title.trim(), appLabel(s, pkg) + ": " + title.trim());
                return;
            }
            showPanel(s, "This page shows no words I can read, so I cannot tell it from the other Settings pages. Use Pick a part and tap something on it instead.", false);
            return;
        }
        showConfirmScreen(s, pkg, comp, appLabel(s, pkg) + ": " + nice);
    }

    private static void showConfirmScreen(AccessibilityService s, String pkg, String comp, String label) {
        WindowManager wm = s.getSystemService(WindowManager.class);
        removeView(wm, panel);
        LinearLayout box = new LinearLayout(s);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(0xF2FFFFFF);
        box.setPadding(Ui.dp(s, 16), Ui.dp(s, 12), Ui.dp(s, 16), Ui.dp(s, 12));
        TextView t = new TextView(s);
        t.setText("Block this whole screen?\n" + label + "\n\nIf this app shows almost everything on one screen, this blocks most of the app: use \"Pick a part\" instead.");
        t.setTextColor(0xFF111111);
        box.addView(t);
        LinearLayout row = new LinearLayout(s);
        addBtn(s, row, "Block it", v -> {
            if (comp.startsWith("sig:")) {
                java.util.Set<String> sigs = blockedScreens(s);
                sigs.add(comp.substring(4));
                setBlockedScreens(s, sigs, "blocked the screen " + label);
            } else if (comp.startsWith("title:")) {
                java.util.Set<String> titles = Agent.getSet(s, "blockedTitles");
                titles.add(comp.substring(6));
                Agent.putSet(s, "blockedTitles", titles);
            } else AppBlocklist.add(s, comp);
            Agent.addEvent(s, "local", "Blocked a screen with ✓ on the phone: " + label + " (" + comp + ")");
            showPanel(s, "Blocked: " + label + ".\nIt closes whenever it opens. Undo: Administrator → Apps → Blocked.", false);
            s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        });
        addBtn(s, row, "Cancel", v -> backToPickButton(s));
        box.addView(row);
        WindowManager.LayoutParams p = lp(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, true);
        p.gravity = Gravity.BOTTOM;
        try {
            wm.addView(box, p);
            panel = box;
        } catch (Exception ignored) {
        }
    }

    /** A see-through layer over everything that takes the next tap. */
    private static void showCapture(AccessibilityService s) {
        WindowManager wm = s.getSystemService(WindowManager.class);
        removeView(wm, outline);
        removeView(wm, panel);
        if (pickButton != null) pickButton.setVisibility(View.GONE);
        FrameLayout layer = new FrameLayout(s);
        layer.setBackgroundColor(0x224F46E5);
        TextView t = new TextView(s);
        t.setText("Tap the part to block");
        t.setTextColor(Color.WHITE);
        t.setBackgroundColor(0xCC000000);
        t.setPadding(24, 12, 24, 12);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tl.topMargin = Ui.dp(s, 40);
        layer.addView(t, tl);
        Button back = new Button(s);
        back.setText("✕");
        back.setTextSize(20);
        back.setAllCaps(false);
        back.setContentDescription("Cancel");
        back.setOnClickListener(v -> {
            removeView(wm, capture);
            capture = null;
            backToPickButton(s);
        });
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Ui.dp(s, 56), Ui.dp(s, 56), Gravity.TOP | Gravity.END);
        bl.topMargin = Ui.dp(s, 36);
        bl.rightMargin = Ui.dp(s, 12);
        layer.addView(back, bl);
        layer.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                int x = (int) e.getRawX(), y = (int) e.getRawY();
                removeView(wm, capture);
                capture = null;
                // Not v.postDelayed: v was just removed, and a removed view never runs its timers.
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> picked(s, x, y), 150); // let the app's window be the active one again
            }
            return true;
        });
        try {
            wm.addView(layer, lp(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, true));
            capture = layer;
        } catch (Exception ignored) {
        }
    }

    /** Finds what was tapped: the smallest piece under the finger that can be recognised, and everything around it. */
    private static void picked(AccessibilityService s, int x, int y) {
        AccessibilityNodeInfo root = s.getRootInActiveWindow();
        chain = new ArrayList<>();
        if (root != null) {
            AccessibilityNodeInfo n = root;
            while (n != null) {
                if (recognisable(n)) chain.add(0, n);
                AccessibilityNodeInfo next = null;
                Rect b = new Rect();
                for (int i = n.getChildCount() - 1; i >= 0; i--) { // topmost child first
                    AccessibilityNodeInfo ch = n.getChild(i);
                    if (ch == null || !ch.isVisibleToUser()) continue;
                    ch.getBoundsInScreen(b);
                    if (b.contains(x, y)) { next = ch; break; }
                }
                n = next;
            }
        }
        chainPkg = root != null && root.getPackageName() != null ? root.getPackageName().toString() : "";
        chainAt = 0;
        if (chain.isEmpty() || chainPkg.equals(s.getPackageName())) {
            showPanel(s, "Nothing there can be recognised. Try a nearby spot, or block the whole app instead.", false);
            return;
        }
        showChoice(s);
    }

    private static boolean recognisable(AccessibilityNodeInfo n) {
        return n.getViewIdResourceName() != null || (n.getContentDescription() != null && n.getContentDescription().length() > 0)
                || (n.getText() != null && n.getText().length() > 0 && n.getText().length() <= 60);
    }

    private static void showChoice(AccessibilityService s) {
        WindowManager wm = s.getSystemService(WindowManager.class);
        AccessibilityNodeInfo n = chain.get(chainAt);
        Rect b = new Rect();
        n.getBoundsInScreen(b);
        removeView(wm, outline);
        View box = new View(s);
        GradientDrawable border = new GradientDrawable();
        border.setColor(0x33FF0000);
        border.setStroke(Ui.dp(s, 3), 0xFFFF0000);
        box.setBackground(border);
        WindowManager.LayoutParams p = lp(Math.max(b.width(), 4), Math.max(b.height(), 4), false);
        p.x = b.left;
        p.y = b.top;
        try {
            wm.addView(box, p);
            outline = box;
        } catch (Exception ignored) {
        }
        showPanel(s, "Block this in " + appLabel(s, chainPkg) + "?\n" + describe(n), true);
    }

    private static void showPanel(AccessibilityService s, String text, boolean choice) {
        WindowManager wm = s.getSystemService(WindowManager.class);
        removeView(wm, panel);
        LinearLayout box = new LinearLayout(s);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(0xF2FFFFFF);
        box.setPadding(Ui.dp(s, 16), Ui.dp(s, 12), Ui.dp(s, 16), Ui.dp(s, 12));
        TextView t = new TextView(s);
        t.setText(text);
        t.setTextColor(0xFF111111);
        box.addView(t);
        LinearLayout row1 = new LinearLayout(s), row2 = new LinearLayout(s);
        if (choice) {
            addBtn(s, row1, "Bigger", v -> { if (chainAt < chain.size() - 1) { chainAt++; showChoice(s); } });
            addBtn(s, row1, "Smaller", v -> { if (chainAt > 0) { chainAt--; showChoice(s); } });
            addBtn(s, row2, "Cover it", v -> save(s, "cover"));
            addBtn(s, row2, "Close the screen", v -> save(s, "close"));
        }
        addBtn(s, row2, choice ? "Cancel" : "OK", v -> backToPickButton(s));
        box.addView(row1);
        box.addView(row2);
        WindowManager.LayoutParams p = lp(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, true);
        p.gravity = Gravity.BOTTOM;
        try {
            wm.addView(box, p);
            panel = box;
        } catch (Exception ignored) {
        }
    }

    private static void addBtn(Context c, LinearLayout row, String label, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label);
        b.setOnClickListener(l);
        row.addView(b);
    }

    private static void save(AccessibilityService s, String mode) {
        try {
            AccessibilityNodeInfo n = chain.get(chainAt);
            JSONObject part = new JSONObject();
            part.put("id", Long.toHexString(System.nanoTime() & 0xffffffffL));
            part.put("pkg", chainPkg);
            if (n.getViewIdResourceName() != null) part.put("viewId", n.getViewIdResourceName());
            if (n.getContentDescription() != null) part.put("desc", n.getContentDescription().toString());
            if (n.getViewIdResourceName() == null && n.getContentDescription() == null && n.getText() != null) part.put("text", n.getText().toString());
            if (n.getClassName() != null) part.put("cls", n.getClassName().toString());
            part.put("mode", mode);
            part.put("label", appLabel(s, chainPkg) + ": " + shortName(n));
            part.put("at", System.currentTimeMillis());
            JSONArray cur = PhoneRules.current(s).optJSONArray("blockedParts");
            JSONArray next = new JSONArray();
            for (int i = 0; cur != null && i < cur.length(); i++) next.put(cur.get(i));
            next.put(part);
            JSONObject v = new JSONObject();
            v.put("blockedParts", next);
            PhoneRules.set(s, v, "blocked " + part.optString("label") + " (" + ("close".equals(mode) ? "screen closed" : "covered") + ")");
            showPanel(s, "Blocked: " + part.optString("label") + ".\nPick another, or tap Done.", false);
        } catch (Exception e) {
            showPanel(s, "Could not save: " + e.getMessage(), false);
        }
    }

    private static void backToPickButton(AccessibilityService s) {
        WindowManager wm = s.getSystemService(WindowManager.class);
        removeView(wm, outline);
        removeView(wm, panel);
        outline = panel = null;
        if (pickButton != null) pickButton.setVisibility(View.VISIBLE);
    }

    static void stopPicking(Context s) {
        WindowManager wm = s.getSystemService(WindowManager.class);
        for (View v : new View[]{pickButton, capture, outline, panel}) removeView(wm, v);
        pickButton = capture = outline = panel = null;
        picking = false;
    }

    private static void removeView(WindowManager wm, View v) {
        if (v == null) return;
        try {
            wm.removeView(v);
        } catch (Exception ignored) {
        }
    }

    private static String describe(AccessibilityNodeInfo n) {
        StringBuilder sb = new StringBuilder();
        if (n.getContentDescription() != null) sb.append("\"").append(n.getContentDescription()).append("\"  ");
        else if (n.getText() != null) sb.append("\"").append(n.getText()).append("\"  ");
        if (n.getViewIdResourceName() != null) sb.append("(").append(n.getViewIdResourceName().replaceFirst("^.*:id/", "")).append(")");
        return sb.length() == 0 ? "(a part of the screen)" : sb.toString();
    }

    private static String shortName(AccessibilityNodeInfo n) {
        if (n.getContentDescription() != null && n.getContentDescription().length() > 0) return n.getContentDescription().toString();
        if (n.getText() != null && n.getText().length() > 0) return n.getText().toString();
        if (n.getViewIdResourceName() != null) return n.getViewIdResourceName().replaceFirst("^.*:id/", "").replace('_', ' ');
        return "part";
    }

    private static boolean isHome(Context c, String pkg) {
        try {
            android.content.pm.ResolveInfo r = c.getPackageManager().resolveActivity(new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME), 0);
            return r != null && r.activityInfo != null && pkg.equals(r.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    private static String appLabel(Context c, String pkg) {
        try {
            return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }
}
