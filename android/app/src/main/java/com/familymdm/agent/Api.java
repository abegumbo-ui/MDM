package com.familymdm.agent;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Tiny JSON-over-HTTPS client for the dashboard's /agent endpoints. */
final class Api {
    private Api() {}

    static class HttpException extends IOException {
        final int code;

        HttpException(int code, String message) {
            super("HTTP " + code + ": " + message);
            this.code = code;
        }
    }

    // ---- two servers: the Raspberry Pi first, Cloudflare as the backup ----
    // The dashboard tells each phone both addresses in its check-in reply. From then on every call goes to the Pi first
    // (the address the phone was enrolled with, Cloudflare, counts as the backup), and to the backup only while the Pi
    // does not answer; the next call tries the Pi again, so the phone is back on it as soon as it is.
    private static volatile String primary, backup;
    static volatile boolean onBackup; // the last answer came from the backup
    // After the Pi failed to answer, go straight to the backup for a minute (instead of waiting 15 seconds at every call
    // for a Pi that is off), then try the Pi again.
    private static volatile long primaryDownUntil;
    private static final long PRIMARY_RETRY_MS = 60_000;

    /** In words, for the app's front screen: which server the phone is talking to ("" when only one server is set up). */
    static String where() {
        if (primary == null || backup == null) return "";
        return onBackup ? "Cloudflare (backup)" : "Raspberry Pi";
    }

    static void setServers(String p, String b) {
        p = cleanOrigin(p);
        b = cleanOrigin(b);
        if (p == null || b == null) { primary = null; backup = null; }
        else { primary = p; backup = b; }
    }

    private static String cleanOrigin(String u) {
        if (u == null) return null;
        u = u.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u.startsWith("https://") && u.length() > 9 ? u : null;
    }

    private static String originOf(String url) {
        int i = url.indexOf("://");
        if (i < 0) return url;
        int j = url.indexOf('/', i + 3);
        return j < 0 ? url : url.substring(0, j);
    }

    static JSONObject post(String url, JSONObject body, String bearer) throws IOException, JSONException {
        String p = primary, b = backup, o = originOf(url);
        if (p == null || b == null || !(o.equals(p) || o.equals(b))) return postOnce(url, body, bearer);
        String path = url.substring(o.length());
        boolean down = false; // the Pi did not answer at all (not: it answered with an error about this request)
        if (System.currentTimeMillis() < primaryDownUntil) {
            down = true; // it failed a moment ago: do not wait for it again right now
        } else {
            try {
                JSONObject r = postOnce(p + path, body, bearer);
                onBackup = false;
                return r;
            } catch (HttpException e) {
                down = e.code >= 500;
            } catch (IOException e) {
                down = true;
            }
            if (down) primaryDownUntil = System.currentTimeMillis() + PRIMARY_RETRY_MS;
        }
        JSONObject copy = new JSONObject(body.toString());
        if (down && path.endsWith("/agent/sync")) copy.put("viaBackup", true); // the backup then notes that the Pi was off
        JSONObject r = postOnce(b + path, copy, bearer);
        onBackup = true;
        return r;
    }

    private static JSONObject postOnce(String url, JSONObject body, String bearer) throws IOException, JSONException {
        if (!url.startsWith("https://")) throw new IOException("server must be an https:// address");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setDoOutput(true);
            c.setRequestProperty("content-type", "application/json");
            if (bearer != null) c.setRequestProperty("authorization", "Bearer " + bearer);
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
            String text = read(is);
            if (code >= 400) throw new HttpException(code, text);
            return new JSONObject(text);
        } finally {
            c.disconnect();
        }
    }

    /** GET a small file (for example the logo) from the dashboard. */
    static byte[] getBytes(String url, String bearer) throws IOException {
        String p = primary, b = backup, o = originOf(url);
        if (p != null && b != null && (o.equals(p) || o.equals(b))) {
            String path = url.substring(o.length());
            try {
                return getBytesOnce(p + path, bearer);
            } catch (IOException e) {
                return getBytesOnce(b + path, bearer);
            }
        }
        return getBytesOnce(url, bearer);
    }

    private static byte[] getBytesOnce(String url, String bearer) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("server must be an https:// address");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod("GET");
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            if (bearer != null) c.setRequestProperty("authorization", "Bearer " + bearer);
            int code = c.getResponseCode();
            if (code >= 400) throw new HttpException(code, "");
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 2 * 1024 * 1024) throw new IOException("file too large");
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream is) throws IOException {
        if (is == null) return "";
        try (InputStream in = is) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
