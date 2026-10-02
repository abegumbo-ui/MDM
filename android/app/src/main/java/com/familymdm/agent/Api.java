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

    static JSONObject post(String url, JSONObject body, String bearer) throws IOException, JSONException {
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
