package com.familymdm.agent;

import android.content.Context;
import android.content.Intent;

import com.familymdm.agent.sitepolicy.SitePolicy;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Downloaded YouTube videos ("Downloads"): the list kept in files/downloads/downloads.json (also read by the Browser
 * inside this app), the rule for what may be downloaded, and the entry point that starts a download.
 *
 * A download is only started for a YouTube video link, when the dashboard allows downloads, and while YouTube is not
 * switched off. The page it comes from was already allowed to open, so what can be watched can be downloaded.
 */
final class Downloads {
    private Downloads() {}

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "downloads");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File index(Context c) {
        return new File(dir(c), "downloads.json");
    }

    /** The dashboard's switch (on unless switched off). */
    static boolean allowed(Context c) {
        try {
            JSONObject p = new JSONObject(Agent.prefs(c).getString("policy", "{}"));
            return p.optBoolean("downloads", true) && !"blocked".equals(p.optString("youtubeMode", "channels"));
        } catch (Exception e) {
            return true;
        }
    }

    /** null when this address may be downloaded, otherwise why not (plain words, shown to the child). */
    static String refusal(Context c, String url) {
        if (!allowed(c)) return "Downloads are turned off on this phone.";
        String host = url == null ? null : SitePolicy.hostOf(url);
        if (host == null || !(SitePolicy.isYoutubeHost(host) || "youtu.be".equals(host)) || SitePolicy.youtubeVideoId(url) == null)
            return "Only a YouTube video can be downloaded.";
        return null;
    }

    /** Starts a download; returns null on success, otherwise what to tell the child. */
    static String request(Context c, String url) {
        return request(c, url, false);
    }

    /** music = a YouTube Music song: only its sound is downloaded, and it is listed in the Music icon (not YouTube). */
    static String request(Context c, String url, boolean music) {
        String why = refusal(c, url);
        if (why != null) return why;
        String id = SitePolicy.youtubeVideoId(url);
        if (find(c, id) != null) return music ? "That song is already downloaded." : "That video is already downloaded.";
        if (DownloadService.isActive(id)) return "That is already downloading. See Downloads.";
        Intent i = new Intent(c, DownloadService.class).putExtra("url", "https://www.youtube.com/watch?v=" + id).putExtra("music", music);
        try {
            c.startForegroundService(i);
        } catch (Exception e) {
            return "Could not start the download: " + e.getMessage();
        }
        return null;
    }

    static synchronized JSONArray list(Context c) {
        try (FileInputStream in = new FileInputStream(index(c))) {
            byte[] b = new byte[(int) index(c).length()];
            int n = in.read(b);
            return new JSONArray(new String(b, 0, Math.max(n, 0), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static JSONObject find(Context c, String id) {
        JSONArray a = list(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && id != null && id.equals(o.optString("id")) && new File(dir(c), o.optString("file")).exists()) return o;
        }
        return null;
    }

    static synchronized void add(Context c, String id, String title, String file, String thumb) {
        add(c, id, title, file, thumb, false);
    }

    static synchronized void add(Context c, String id, String title, String file, String thumb, boolean music) {
        try {
            JSONArray old = list(c), next = new JSONArray();
            next.put(new JSONObject().put("id", id).put("t", title).put("file", file).put("i", thumb == null ? "" : thumb).put("k", music ? "music" : "video").put("at", System.currentTimeMillis()));
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.optJSONObject(i);
                if (o != null && !id.equals(o.optString("id"))) next.put(o);
            }
            try (FileOutputStream out = new FileOutputStream(index(c))) {
                out.write(next.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    static synchronized void remove(Context c, String id) {
        try {
            JSONArray old = list(c), next = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.optJSONObject(i);
                if (o == null) continue;
                if (id.equals(o.optString("id"))) new File(dir(c), o.optString("file")).delete();
                else next.put(o);
            }
            try (FileOutputStream out = new FileOutputStream(index(c))) {
                out.write(next.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    /** For the YouTube home page and the Browser: [{id, t, i}] of what is downloaded (videos). */
    static JSONArray forPages(Context c) {
        return forPages(c, false);
    }

    /** The Music icon lists the downloaded songs; the YouTube icon and the Browser list the downloaded videos. */
    static JSONArray forPages(Context c, boolean music) {
        JSONArray out = new JSONArray();
        JSONArray a = list(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null || !new File(dir(c), o.optString("file")).exists()) continue;
            if ("music".equals(o.optString("k")) != music) continue;
            try {
                out.put(new JSONObject().put("v", o.optString("id")).put("t", o.optString("t")).put("i", o.optString("i")));
            } catch (Exception ignored) {
            }
        }
        return out;
    }
}
