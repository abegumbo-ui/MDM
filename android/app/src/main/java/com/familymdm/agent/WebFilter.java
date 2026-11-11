package com.familymdm.agent;

import android.content.Context;
import android.content.SharedPreferences;

import com.familymdm.agent.sitepolicy.BlockList;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Browser's category filter (Moderate / Strict): downloads the category blocklists the weekly
 * GitHub job publishes, checks them, and answers "is this host blocked?".
 *
 * Fail-closed: until every list the chosen level needs has been downloaded AND matches the
 * published checksum, {@link #active} is false and the Browser simply stays on "approved sites
 * only", exactly as it was before this feature existed. A half-finished download, a corrupted file
 * or no connection can therefore never open the web.
 *
 * Nothing here runs at all while the level is "off".
 */
final class WebFilter {
    private WebFilter() {}

    private static final long RECHECK_MS = 24L * 60 * 60 * 1000;
    private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    private static final String GITHUB = "https://github.com/";

    private static final Object LOCK = new Object();
    private static volatile boolean refreshing;
    // Lists opened for the current tier+manifest, or null when not (yet) usable.
    private static List<BlockList> loaded;
    private static List<String> loadedCats = new ArrayList<>(); // the category of each list above, same order
    private static String loadedFor = "";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("webfilter", Context.MODE_PRIVATE);
    }

    /** Remembers what the dashboard (or the agent) says the level is, and starts getting the lists if it needs them. */
    static void setPolicy(Context c, String tier, String base) {
        String t = "moderate".equals(tier) || "strict".equals(tier) ? tier : "off";
        String b = base != null && base.startsWith(GITHUB) && base.length() < 300 ? base : "";
        SharedPreferences p = prefs(c);
        boolean changed = !t.equals(p.getString("filterTier", "off")) || !b.equals(p.getString("filterBase", ""));
        if (changed) p.edit().putString("filterTier", t).putString("filterBase", b).apply();
        if (changed) synchronized (LOCK) { loaded = null; loadedFor = ""; }
        if (!"off".equals(t) && !b.isEmpty()) refreshAsync(c, changed);
    }

    static String tier(Context c) {
        return prefs(c).getString("filterTier", "off");
    }

    private static boolean wanted(Context c) {
        return !"off".equals(tier(c)) && !prefs(c).getString("filterBase", "").isEmpty();
    }

    /** True only when the level is on AND all its lists are downloaded and verified. */
    static boolean active(Context c) {
        if (!wanted(c)) return false;
        synchronized (LOCK) {
            ensureLoaded(c);
            return loaded != null;
        }
    }

    /** Is this host in one of the active lists? False if the filter is not active. */
    static boolean blocks(Context c, String host) {
        List<BlockList> l;
        synchronized (LOCK) {
            ensureLoaded(c);
            l = loaded;
        }
        return l != null && BlockList.anyBlocks(l, host);
    }

    /** The YouTube setting from the dashboard: "restricted" (search, no recommendations) or "channels" (default). */
    static void setYoutubeMode(Context c, String mode) {
        prefs(c).edit().putString("youtubeMode", "restricted".equals(mode) ? "restricted" : "channels").apply();
    }

    static boolean youtubeRestricted(Context c) {
        return "restricted".equals(prefs(c).getString("youtubeMode", "channels"));
    }

    /** In one of the core lists (everything except social media)? False if the filter is not active. */
    static boolean blocksCore(Context c, String host) {
        return blocksIn(c, host, false);
    }

    /** In the social-media list (only part of the Strict level)? False if the filter is not active. */
    static boolean blocksSocial(Context c, String host) {
        return blocksIn(c, host, true);
    }

    private static boolean blocksIn(Context c, String host, boolean social) {
        List<BlockList> l;
        List<String> names;
        synchronized (LOCK) {
            ensureLoaded(c);
            l = loaded;
            names = loadedCats;
        }
        if (l == null) return false;
        for (int i = 0; i < l.size() && i < names.size(); i++) {
            if ("social".equals(names.get(i)) == social && l.get(i).blocks(host)) return true;
        }
        return false;
    }

    // ---------- loading what is already on disk ----------

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "blocklists");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** The category files the current level needs, from the saved manifest; null if there is no usable manifest. */
    private static Map<String, JSONObject> needed(Context c) {
        try {
            String m = prefs(c).getString("filterManifest", null);
            if (m == null) return null;
            JSONObject man = new JSONObject(m);
            JSONArray cats = man.getJSONObject("tiers").optJSONArray(tier(c));
            if (cats == null || cats.length() == 0) return null;
            Map<String, JSONObject> out = new HashMap<>();
            for (int i = 0; i < cats.length(); i++) {
                JSONObject cat = man.getJSONObject("categories").optJSONObject(cats.getString(i));
                if (cat == null) return null; // the level names a category that is not published: do not guess
                out.put(cats.getString(i), cat);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static void ensureLoaded(Context c) {
        String key = tier(c) + "|" + prefs(c).getString("filterManifest", "");
        if (loaded != null && key.equals(loadedFor)) return;
        loaded = null;
        loadedFor = key;
        Map<String, JSONObject> need = needed(c);
        if (need == null) return;
        List<BlockList> out = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try {
            for (Map.Entry<String, JSONObject> entry : need.entrySet()) {
                JSONObject cat = entry.getValue();
                String file = cat.getString("file");
                File f = new File(dir(c), file);
                // Only a file that was fully downloaded and checked against the published checksum
                // is ever trusted; the checksum is remembered when it passes.
                if (!f.exists() || !cat.getString("sha256").equals(prefs(c).getString("sha:" + file, ""))) return;
                if (f.length() != cat.getLong("bytes")) return;
                out.add(BlockList.open(f));
                names.add(entry.getKey());
            }
        } catch (Exception e) {
            return;
        }
        loaded = out;
        loadedCats = names;
    }

    // ---------- downloading ----------

    /** Gets the lists in the background if they are missing or a day has passed. Safe to call often. */
    static void refreshAsync(final Context ctx, final boolean force) {
        final Context c = ctx.getApplicationContext();
        if (!wanted(c) || refreshing) return;
        boolean ready = active(c);
        long last = prefs(c).getLong("filterChecked", 0);
        if (!force && ready && System.currentTimeMillis() - last < RECHECK_MS) return;
        // Do not hammer GitHub: after a failed try, wait a little before the next one.
        if (!force && !ready && System.currentTimeMillis() - prefs(c).getLong("filterTried", 0) < 10 * 60 * 1000) return;
        refreshing = true;
        new Thread(() -> {
            try {
                prefs(c).edit().putLong("filterTried", System.currentTimeMillis()).apply();
                refresh(c);
            } catch (Exception ignored) {
                // offline or GitHub unreachable: keep whatever is already verified, try again later
            } finally {
                refreshing = false;
            }
        }).start();
    }

    private static void refresh(Context c) throws Exception {
        String base = prefs(c).getString("filterBase", "");
        String manifestText = new String(fetch(base + "/manifest.json", 256 * 1024), "UTF-8");
        JSONObject man = new JSONObject(manifestText);
        JSONArray cats = man.getJSONObject("tiers").optJSONArray(tier(c));
        if (cats == null || cats.length() == 0) throw new Exception("unknown level");
        for (int i = 0; i < cats.length(); i++) {
            JSONObject cat = man.getJSONObject("categories").getJSONObject(cats.getString(i));
            String file = cat.getString("file");
            if (!file.matches("[a-z0-9_-]{1,40}\\.bin")) throw new Exception("bad file name");
            String sha = cat.getString("sha256");
            File dest = new File(dir(c), file);
            if (dest.exists() && sha.equals(prefs(c).getString("sha:" + file, "")) && dest.length() == cat.getLong("bytes")) continue;
            File tmp = new File(dir(c), file + ".tmp");
            String got = download(base + "/" + file, tmp, cat.getLong("bytes"));
            if (!got.equals(sha)) {
                tmp.delete();
                throw new Exception("checksum mismatch for " + file);
            }
            if (dest.exists()) dest.delete();
            if (!tmp.renameTo(dest)) throw new Exception("could not save " + file);
            prefs(c).edit().putString("sha:" + file, sha).apply();
        }
        // Only now that every file is in place does the new manifest take effect.
        prefs(c).edit().putString("filterManifest", manifestText).putLong("filterChecked", System.currentTimeMillis()).apply();
        synchronized (LOCK) {
            loaded = null;
            loadedFor = "";
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        if (!url.startsWith(GITHUB)) throw new Exception("lists only come from GitHub");
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(20000);
        h.setReadTimeout(60000);
        h.setInstanceFollowRedirects(true);
        if (h.getResponseCode() != 200) {
            int code = h.getResponseCode();
            h.disconnect();
            throw new Exception("HTTP " + code);
        }
        return h;
    }

    private static byte[] fetch(String url, int max) throws Exception {
        HttpURLConnection h = open(url);
        try (InputStream in = h.getInputStream()) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > max) throw new Exception("too large");
            }
            return out.toByteArray();
        } finally {
            h.disconnect();
        }
    }

    /** Downloads to `dest`, returning the SHA-256 (hex) of what arrived. */
    private static String download(String url, File dest, long expectedBytes) throws Exception {
        if (expectedBytes <= 0 || expectedBytes > MAX_FILE_BYTES) throw new Exception("bad size");
        HttpURLConnection h = open(url);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        long total = 0;
        try (InputStream in = h.getInputStream(); FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > expectedBytes) throw new Exception("larger than published");
                md.update(buf, 0, n);
                out.write(buf, 0, n);
            }
        } finally {
            h.disconnect();
        }
        if (total != expectedBytes) throw new Exception("incomplete download");
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
