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
