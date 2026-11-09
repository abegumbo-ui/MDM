package com.familymdm.agent;

import android.content.Context;
import android.content.SharedPreferences;

import com.familymdm.agent.sitepolicy.SitePolicy;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Finds out which YouTube channel a video belongs to, so the Browser can allow "everything from this
 * channel" without allowing the rest of YouTube.
 *
 * It asks YouTube's public "oEmbed" address, which names a video's channel and needs no account or
 * key. A video address alone never says who posted it, which is why this lookup exists.
 *
 * Answers are remembered on the phone. A video whose channel could not be found out (no connection,
 * private, removed, or a channel without an @handle) counts as "not allowed": it fails closed.
 */
final class YoutubeLookup {
    private YoutubeLookup() {}

    private static final long FAILURE_MEMORY_MS = 2 * 60 * 1000;
    private static final int MAX_REMEMBERED = 400;

    private static final Map<String, String> memory = new HashMap<>(); // video id -> "@handle", or "" if not allowed/unknown
    private static final Map<String, Long> failedAt = new HashMap<>(); // video id -> when a lookup last failed (temporary)
    private static final Set<String> inFlight = new HashSet<>();

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("youtube", Context.MODE_PRIVATE);
    }

    /** The remembered channel handle for a video ("" if it is known to have none we can use), or null if not known yet. */
    static synchronized String cached(Context c, String videoId) {
        String m = memory.get(videoId);
        if (m != null) return m;
        Long failed = failedAt.get(videoId);
        if (failed != null) {
            if (System.currentTimeMillis() - failed < FAILURE_MEMORY_MS) return ""; // just failed: do not hammer, treat as not allowed
            failedAt.remove(videoId);
        }
        String stored = prefs(c).getString("v:" + videoId, null);
        if (stored != null) memory.put(videoId, stored);
        return stored;
    }

    /** Looks the video up in the background, then runs `done` (on that background thread). */
    static void start(final Context ctx, final String videoId, final Runnable done) {
        final Context c = ctx.getApplicationContext();
        synchronized (YoutubeLookup.class) {
            if (!inFlight.add(videoId)) {
                // Already being looked up: let the caller wait for that one to land by polling once it is done.
                new Thread(() -> {
                    for (int i = 0; i < 40; i++) {
                        try { Thread.sleep(500); } catch (InterruptedException ignored) { }
                        synchronized (YoutubeLookup.class) { if (!inFlight.contains(videoId)) break; }
                    }
                    done.run();
                }).start();
                return;
            }
        }
        new Thread(() -> {
            try {
                lookup(c, videoId);
            } finally {
                synchronized (YoutubeLookup.class) { inFlight.remove(videoId); }
                done.run();
            }
        }).start();
    }

    private static void lookup(Context c, String videoId) {
        HttpURLConnection h = null;
        try {
            String watch = "https://www.youtube.com/watch?v=" + videoId;
            h = (HttpURLConnection) new URL("https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode(watch, "UTF-8")).openConnection();
            h.setConnectTimeout(12000);
            h.setReadTimeout(15000);
            int code = h.getResponseCode();
            if (code == 401 || code == 403 || code == 404) {
                // Private, removed, or not embeddable: there is no usable channel, so it is not allowed.
                remember(c, videoId, "");
                return;
            }
            if (code != 200) throw new Exception("HTTP " + code);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = h.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 64 * 1024) throw new Exception("too large");
                }
            }
            String handle = SitePolicy.youtubeChannelHandle(new JSONObject(out.toString("UTF-8")).optString("author_url", ""));
            remember(c, videoId, handle == null ? "" : handle);
        } catch (Exception e) {
            synchronized (YoutubeLookup.class) { failedAt.put(videoId, System.currentTimeMillis()); } // try again later; not kept on disk
        } finally {
            if (h != null) h.disconnect();
        }
    }

    private static synchronized void remember(Context c, String videoId, String handle) {
        memory.put(videoId, handle);
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor ed = p.edit();
        if (p.getAll().size() >= MAX_REMEMBERED) ed.clear();
        ed.putString("v:" + videoId, handle).apply();
    }
}
