package com.familymdm.lockdown;

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
 * Everything runs inside TapBlockService, which must be switched on once.
 */
final class PartBlocker {
    private PartBlocker() {}

    /* ------------------------------------------------------------------ rules ------------------------------------------------------------------ */

    /** The parts picked on this phone (Lockdown has no dashboard: they live in its own settings). */
    static JSONArray stored(Context c) {
        try {
            return new JSONArray(LockdownPrefs.prefs(c).getString("blockedParts", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static void save(Context c, JSONArray parts) {
        LockdownPrefs.prefs(c).edit().putString("blockedParts", parts.toString()).apply();
    }

    private static volatile String cachedFrom;
    private static volatile List<JSONObject> cached = new ArrayList<>();

    static List<JSONObject> rules(Context c) {
        JSONArray a = stored(c);
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
        if (picking) return;
        if (pkg == null || pkg.equals(s.getPackageName()) || pkg.equals("com.android.systemui")) return;
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

            return;
        }
        showCovers(s, pkg, toCover);
    }

    private static void showCovers(AccessibilityService s, String pkg, List<Rect> rects) {
        clearCovers(s);
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
    }

    /* ------------------------------------------------------------------ picking ------------------------------------------------------------------ */

    static volatile boolean picking;
    private static View pickButton, capture, outline, panel;
    private static List<AccessibilityNodeInfo> chain = new ArrayList<>();
    private static int chainAt;
    private static String chainPkg;

    /** Starts picking. Returns a message if it cannot. */
    static String startPicking(Context c) {
        TapBlockService s = TapBlockService.instance;
        if (s == null) return "Switch on Lockdown in Settings → Accessibility first (the button on the Tap to block card opens it).";
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
        LinearLayout bar = new LinearLayout(s);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(Ui.dp(s, 8), Ui.dp(s, 6), Ui.dp(s, 8), Ui.dp(s, 6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEE4F46E5);
        bg.setCornerRadius(Ui.dp(s, 24));
        bar.setBackground(bg);
        Button check = new Button(s);
        check.setText("✓ Block this screen");
        check.setOnClickListener(v -> blockThisScreen(s));
        Button pick = new Button(s);
        pick.setText("Pick a part");
        pick.setOnClickListener(v -> showCapture(s));
        Button done = new Button(s);
        done.setText("Done");
        done.setOnClickListener(v -> stopPicking(s));
        TextView hint = new TextView(s);
        hint.setText("  Go to the screen, then press ✓.\n  Or Pick a part to block just one button.  ");
        hint.setTextColor(Color.WHITE);
        bar.addView(hint);
        bar.addView(check);
        bar.addView(pick);
        bar.addView(done);
        WindowManager.LayoutParams p = lp(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, true);
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
    static volatile String currentScreen, currentScreenPkg;

    static void noteScreen(String pkg, String cls) {
        if (pkg == null || cls == null || pkg.equals("com.familymdm.lockdown") || pkg.equals("com.android.systemui")) return;
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
        if (isHome(s, pkg) || pkg.equals("com.android.settings") || pkg.equals("com.android.dialer") || pkg.equals("com.google.android.dialer")) {
            showPanel(s, "The home screen, Settings and the phone app cannot be blocked this way. Open the app you want first.", false);
            return;
        }
        String cls = comp.substring(comp.indexOf('/') + 1);
        String nice = cls.substring(cls.lastIndexOf('.') + 1).replaceAll("(Activity|Fragment)$", "").replaceAll("([a-z])([A-Z])", "$1 $2");
        if (pickButton != null) pickButton.setVisibility(View.GONE);
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
            java.util.Set<String> screens = LockdownPrefs.getSet(s, "tapBlockedScreens");
            screens.add(comp);
            LockdownPrefs.putSet(s, "tapBlockedScreens", screens);
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
        layer.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                int x = (int) e.getRawX(), y = (int) e.getRawY();
                removeView(wm, capture);
                capture = null;
                v.postDelayed(() -> picked(s, x, y), 150); // let the app's window be the active one again
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
            JSONArray cur = stored(s);
            JSONArray next = new JSONArray();
            for (int i = 0; cur != null && i < cur.length(); i++) next.put(cur.get(i));
            next.put(part);
            save(s, next);
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

