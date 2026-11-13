package com.familymdm.agent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.familymdm.agent.sitepolicy.MessageRules;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Two things a parent switches on from the dashboard, both open to the child, never hidden:
 * 1. Message alerts: while a chat app (WhatsApp, Instagram, texts...) is on the screen, the text
 *    shown is checked against the dashboard's word list; a match is sent to the parent with the
 *    sentence it was in. Nothing else is kept or sent: no other messages, no other apps. When it is
 *    switched on, the phone shows a notice saying so.
 * 2. Screenshot on request: the parent taps "Take a screenshot" on the dashboard; the phone takes
 *    one picture of the screen, sends it, and shows "A screenshot was sent to your parent".
 * Both use the Agent's accessibility service (Settings → Accessibility → MDM Agent, switched on once).
 */
final class Monitor {
    private Monitor() {}

    private static final String PENDING = "msgAlertPending";
    private static final int MAX_PENDING = 40;
    private static final LinkedHashSet<Integer> recent = new LinkedHashSet<>();
    private static long lastScanAt;
    private static volatile List<String> cachedWords;
    private static volatile String cachedFrom;

    static void apply(Context c, JSONObject policy) {
        boolean on = policy.optBoolean("messageAlerts", false);
        JSONArray w = policy.optJSONArray("messageWords");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; w != null && i < w.length(); i++) {
            String s = w.optString(i, "").trim();
            if (!s.isEmpty()) sb.append(sb.length() == 0 ? "" : "\n").append(s);
        }
        boolean searches = policy.optBoolean("recordSearches", false);
        boolean hadSearches = Agent.prefs(c).getBoolean("recordSearches", false);
        Agent.prefs(c).edit().putBoolean("recordSearches", searches).apply();
        if (searches && !hadSearches) tellChild(c, "Searches are kept", "Your parent can see what is searched for on this phone (Google, YouTube, browsers).");
        boolean videos = policy.optBoolean("recordVideos", false);
        boolean hadVideos = Agent.prefs(c).getBoolean("recordVideos", false);
        Agent.prefs(c).edit().putBoolean("recordVideos", videos).apply();
        if (videos && !hadVideos) tellChild(c, "YouTube videos are listed", "Your parent can see the names of the YouTube videos watched on this phone.");
        boolean nudity = policy.optBoolean("nudityAlerts", false);
        boolean hadNudity = Agent.prefs(c).getBoolean("nudityAlerts", false);
        PhotoWatch.apply(c, nudity);
        if (nudity && !hadNudity) tellChild(c, "Photo alerts are on",
                "New photos on this phone are checked on the phone itself. If one may show nudity, your parent is told when it was saved. The photo is never sent to anyone.");
        boolean was = Agent.prefs(c).getBoolean("msgAlerts", false);
        Agent.prefs(c).edit().putBoolean("msgAlerts", on).putString("msgWords", sb.toString()).apply();
        if (on && !was) tellChild(c, "Message alerts are on",
                "Your parent gets an alert if worrying words (bullying, sexual, self-harm, drugs) appear in chats on this phone.");
        if (on && !Agent.accessibilityServiceOn(c)) Protection.remindAccessibility(c);
    }

    static boolean on(Context c) {
        return Agent.prefs(c).getBoolean("msgAlerts", false);
    }

    private static List<String> words(Context c) {
        String s = Agent.prefs(c).getString("msgWords", "");
        if (!s.equals(cachedFrom) || cachedWords == null) {
            cachedWords = MessageRules.parse(s);
            cachedFrom = s;
        }
        return cachedWords;
    }

    /** Search boxes of browsers, YouTube and the Google app: what is typed there is checked too. */
    static final java.util.Set<String> SEARCH_APPS = java.util.Set.of(
            "com.android.chrome", "org.mozilla.firefox", "com.sec.android.app.sbrowser", "com.brave.browser", "com.opera.browser",
            "com.microsoft.emmx", "com.duckduckgo.mobile.android", "com.google.android.youtube", "com.google.android.googlequicksearchbox",
            "com.familymdm.browser", "com.zhiliaoapp.musically", "com.instagram.android", "com.pinterest");
    private static long lastSearchScanAt;

    /** Worrying words typed into a search box (only with message alerts on). At most every 2 s. */
    static void scanSearch(Context c, String pkg, AccessibilityNodeInfo root) {
        if (root == null || !on(c) || pkg == null || !SEARCH_APPS.contains(pkg)) return;
        long now = System.currentTimeMillis();
        if (now - lastSearchScanAt < 2000) return;
        lastSearchScanAt = now;
        List<String> words = words(c);
        if (words.isEmpty()) return;
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int seen = 0;
        while (!q.isEmpty() && seen++ < 400) {
            AccessibilityNodeInfo n = q.poll();
            if (n.isEditable() && n.getText() != null && n.getText().length() >= 3 && !n.isPassword()) {
                String text = n.getText().toString();
                String word = MessageRules.match(text, words);
                if (word != null) found(c, pkg, word, "Searched: " + text, now);
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo ch = n.getChild(i);
                if (ch != null) q.add(ch);
            }
        }
    }

    /* ---- "Oh come on, you're better than that" in any browser ---- */
    private static long lastBadSearchAt;

    /**
     * A search for explicit things (Strict) or with a blocked word, once it has been SENT (the box is no
     * longer being typed in, or the address bar shows the search address): the browser goes back and
     * the child sees "Oh come on, you're better than that." Never while still typing.
     */
    static void watchBadSearch(Context c, String pkg, AccessibilityNodeInfo root) {
        if (root == null || pkg == null || !SEARCH_APPS.contains(pkg)) return;
        boolean strict = WebFilter.tier(c).startsWith("strict");
        List<String> words = BlockedWords.list(c);
        if (!strict && (words == null || words.isEmpty())) return;
        long now = System.currentTimeMillis();
        if (now - lastBadSearchAt < 2500) return;
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int seen = 0;
        while (!q.isEmpty() && seen++ < 300) {
            AccessibilityNodeInfo n = q.poll();
            if (n.isEditable() && !n.isPassword() && n.getText() != null && !(android.os.Build.VERSION.SDK_INT >= 26 && n.isShowingHintText())) {
                String t = n.getText().toString().trim();
                String searched = null;
                if (t.startsWith("http") || t.contains("/search?") || t.contains("/results?")) searched = com.familymdm.agent.sitepolicy.BadSearch.query(t.startsWith("http") ? t : "https://" + t);
                else if (!n.isFocused()) searched = t; // the search was sent; the box shows what was searched
                if (searched != null && com.familymdm.agent.sitepolicy.BadSearch.isBad(searched, strict, words)) {
                    lastBadSearchAt = now;
                    Agent.addEvent(c, "restriction", "Search stopped (" + pkg + "): " + (searched.length() > 60 ? searched.substring(0, 60) + "…" : searched));
                    // The message covers the screen at once; the browser then goes back underneath it.
                    try {
                        c.startActivity(new android.content.Intent(c, BetterThanThatActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception ignored) {
                    }
                    if (c instanceof android.accessibilityservice.AccessibilityService) {
                        final android.accessibilityservice.AccessibilityService svc = (android.accessibilityservice.AccessibilityService) c;
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                                () -> svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK), 350);
                    }
                    return;
                }
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo ch = n.getChild(i);
                if (ch != null) q.add(ch);
            }
        }
    }

    /* ---- searches list ("Keep a list of searches") ---- */
    private static final String SEARCHES = "searchPending";
    private static String typingPkg, typing, lastKept;

    /** Watches search boxes; a search is kept once the screen moves on from it (results opened). */
    static void trackSearch(Context c, String pkg, AccessibilityNodeInfo root, boolean newScreen) {
        if (!Agent.prefs(c).getBoolean("recordSearches", false) || pkg == null) return;
        if (newScreen || (typingPkg != null && !typingPkg.equals(pkg))) keepTyped(c);
        if (root == null || !SEARCH_APPS.contains(pkg)) return;
        String now = null;
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int seen = 0;
        while (!q.isEmpty() && seen++ < 300) {
            AccessibilityNodeInfo n = q.poll();
            if (n.isEditable() && !n.isPassword() && n.getText() != null && !(android.os.Build.VERSION.SDK_INT >= 26 && n.isShowingHintText())) {
                String t = n.getText().toString().trim();
                if (t.length() >= 2 && t.length() <= 200 && !t.startsWith("http")) { now = t; break; }
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo ch = n.getChild(i);
                if (ch != null) q.add(ch);
            }
        }
        if (now == null) { keepTyped(c); return; }
        typingPkg = pkg;
        typing = now;
    }

    private static synchronized void keepTyped(Context c) {
        String q = typing, pkg = typingPkg;
        typing = null;
        typingPkg = null;
        if (q == null || q.equals(lastKept)) return;
        lastKept = q;
        try {
            JSONArray list = new JSONArray(Agent.prefs(c).getString(SEARCHES, "[]"));
            JSONObject o = new JSONObject();
            o.put("app", pkg);
            o.put("q", q);
            o.put("at", System.currentTimeMillis());
            list.put(o);
            JSONArray out = new JSONArray();
            for (int i = Math.max(0, list.length() - 100); i < list.length(); i++) out.put(list.get(i));
            Agent.prefs(c).edit().putString(SEARCHES, out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static synchronized JSONArray peekSearches(Context c) {
        try {
            return new JSONArray(Agent.prefs(c).getString(SEARCHES, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static synchronized void dropSearches(Context c, int count) {
        try {
            JSONArray list = peekSearches(c), out = new JSONArray();
            for (int i = count; i < list.length(); i++) out.put(list.get(i));
            Agent.prefs(c).edit().putString(SEARCHES, out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /* ---- YouTube videos watched ("Keep a list of YouTube videos") ---- */
    private static final String VIDEOS = "videoPending";
    private static String seenTitle, keptTitle;
    private static long seenSince, lastVideoLook;

    /**
     * Reads the title of the video playing in the YouTube app from the screen: only while a player is
     * on the screen (the watch page or the mini player), and only once the same title has stayed for
     * 10 seconds, so scrolling past videos in the feed is not counted. YouTube's screen layout is not
     * documented, so this looks for views whose id mentions "player" and "title".
     */
    static void trackVideo(Context c, String pkg, AccessibilityNodeInfo root) {
        if (root == null || !"com.google.android.youtube".equals(pkg) || !Agent.prefs(c).getBoolean("recordVideos", false)) return;
        long now = System.currentTimeMillis();
        if (now - lastVideoLook < 2000) return;
        lastVideoLook = now;
        boolean player = false;
        String title = null;
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int seen = 0;
        while (!q.isEmpty() && seen++ < 600) {
            AccessibilityNodeInfo n = q.poll();
            String id = n.getViewIdResourceName();
            if (id != null) {
                String low = id.toLowerCase(java.util.Locale.ROOT);
                if (low.contains("player")) player = true;
                if (title == null && low.contains("title") && !low.contains("channel") && n.getText() != null) {
                    String t = n.getText().toString().trim();
                    if (t.length() >= 3 && t.length() <= 200) title = t;
                }
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo ch = n.getChild(i);
                if (ch != null) q.add(ch);
            }
        }
        if (!player || title == null) { seenTitle = null; return; }
        if (!title.equals(seenTitle)) { seenTitle = title; seenSince = now; return; }
        if (now - seenSince < 10000 || title.equals(keptTitle)) return;
        keptTitle = title;
        keepVideo(c, title, seenSince);
    }

    private static synchronized void keepVideo(Context c, String title, long at) {
        try {
            JSONArray list = new JSONArray(Agent.prefs(c).getString(VIDEOS, "[]"));
            JSONObject o = new JSONObject();
            o.put("title", title);
            o.put("at", at);
            list.put(o);
            JSONArray out = new JSONArray();
            for (int i = Math.max(0, list.length() - 100); i < list.length(); i++) out.put(list.get(i));
            Agent.prefs(c).edit().putString(VIDEOS, out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static synchronized JSONArray peekVideos(Context c) {
        try {
            return new JSONArray(Agent.prefs(c).getString(VIDEOS, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static synchronized void dropVideos(Context c, int count) {
        try {
            JSONArray list = peekVideos(c), out = new JSONArray();
            for (int i = count; i < list.length(); i++) out.put(list.get(i));
            Agent.prefs(c).edit().putString(VIDEOS, out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Called by the accessibility service when a chat app's screen changes. At most every 1.5 s. */
    static void scan(Context c, String pkg, AccessibilityNodeInfo root) {
        if (root == null || !on(c) || !MessageRules.watched(pkg)) return;
        long now = System.currentTimeMillis();
        if (now - lastScanAt < 1500) return;
        lastScanAt = now;
        List<String> words = words(c);
        if (words.isEmpty()) return;
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int seen = 0;
        while (!q.isEmpty() && seen++ < 800) {
            AccessibilityNodeInfo n = q.poll();
            CharSequence t = n.getText();
            if (t != null && t.length() >= 3 && t.length() <= 4000) {
                String text = t.toString();
                String word = MessageRules.match(text, words);
                if (word != null) found(c, pkg, word, text, now);
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo ch = n.getChild(i);
                if (ch != null) q.add(ch);
            }
        }
    }

    private static synchronized void found(Context c, String pkg, String word, String text, long now) {
        int key = (pkg + "\u0000" + text).hashCode();
        if (recent.contains(key)) return; // the same message, still on the screen
        recent.add(key);
        if (recent.size() > 300) recent.remove(recent.iterator().next());
        try {
            JSONArray list = new JSONArray(Agent.prefs(c).getString(PENDING, "[]"));
            JSONObject o = new JSONObject();
            o.put("app", pkg);
            o.put("word", word);
            o.put("text", MessageRules.snippet(text, word, 300));
            o.put("at", now);
            list.put(o);
            JSONArray out = new JSONArray();
            for (int i = Math.max(0, list.length() - MAX_PENDING); i < list.length(); i++) out.put(list.get(i));
            Agent.prefs(c).edit().putString(PENDING, out.toString()).apply();
            AgentService.requestSync();
        } catch (Exception ignored) {
        }
    }

    static synchronized JSONArray peek(Context c) {
        try {
            return new JSONArray(Agent.prefs(c).getString(PENDING, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static synchronized void drop(Context c, int count) {
        try {
            JSONArray list = peek(c), out = new JSONArray();
            for (int i = count; i < list.length(); i++) out.put(list.get(i));
            Agent.prefs(c).edit().putString(PENDING, out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /**
     * "Take a screenshot" from the dashboard. Takes one picture of the screen (Android 11 and later,
     * accessibility service on), sends it as a small JPEG, and tells the child. Returns a result line.
     */
    static String screenshot(Context c) throws Exception {
        if (Build.VERSION.SDK_INT < 30) return "This phone's Android is too old for screenshots (needs Android 11 or later).";
        BlockAccessibilityService svc = BlockAccessibilityService.instance;
        if (svc == null) return "Turn on MDM Agent in the phone's Settings → Accessibility first (needed once).";
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Bitmap> shot = new AtomicReference<>();
        final AtomicReference<String> err = new AtomicReference<>();
        svc.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, c.getMainExecutor(),
                new android.accessibilityservice.AccessibilityService.TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(android.accessibilityservice.AccessibilityService.ScreenshotResult r) {
                        try {
                            Bitmap hw = Bitmap.wrapHardwareBuffer(r.getHardwareBuffer(), r.getColorSpace());
                            if (hw != null) shot.set(hw.copy(Bitmap.Config.ARGB_8888, false));
                            r.getHardwareBuffer().close();
                        } catch (Exception e) {
                            err.set(e.toString());
                        }
                        done.countDown();
                    }

                    @Override
                    public void onFailure(int code) {
                        err.set("Android refused the screenshot (code " + code + ")");
                        done.countDown();
                    }
                });
        if (!done.await(15, TimeUnit.SECONDS)) return "The screenshot took too long.";
        Bitmap b = shot.get();
        if (b == null) return err.get() != null ? err.get() : "No picture came back.";
        int w = b.getWidth(), h = b.getHeight();
        float scale = Math.min(1f, 720f / Math.max(1, Math.min(w, h)));
        Bitmap small = scale < 1f ? Bitmap.createScaledBitmap(b, Math.round(w * scale), Math.round(h * scale), true) : b;
        // Never send a picture of the screen that may show nudity (it could be of a child).
        float nude = NudityCheck.score(c, small);
        if (nude >= com.familymdm.agent.sitepolicy.NudityPrep.THRESHOLD)
            return "Not sent: the screen may show nudity, so the picture stayed on the phone.";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        small.compress(Bitmap.CompressFormat.JPEG, 70, out);
        String server = Agent.prefs(c).getString("server", "");
        String token = Agent.prefs(c).getString("token", "");
        JSONObject body = new JSONObject();
        body.put("jpeg", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP));
        body.put("app", topApp(svc));
        Api.post(server + "/agent/screenshot", body, token);
        tellChildBriefly(c, "A screenshot of this screen was sent to your parent.");
        return "Screenshot sent (" + (out.size() / 1024) + " KB)";
    }

    private static String topApp(BlockAccessibilityService svc) {
        try {
            AccessibilityNodeInfo root = svc.getRootInActiveWindow();
            return root != null && root.getPackageName() != null ? root.getPackageName().toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static void tellChildBriefly(Context c, String text) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(c.getApplicationContext(), text, Toast.LENGTH_LONG).show());
    }

    /** A plain notification on the child's phone, so monitoring is never secret. */
    private static void tellChild(Context c, String title, String text) {
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel("monitor", "What your parent can see", NotificationManager.IMPORTANCE_DEFAULT));
            nm.notify(43, new Notification.Builder(c, "monitor")
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build());
        } catch (Exception ignored) {
        }
    }
}
