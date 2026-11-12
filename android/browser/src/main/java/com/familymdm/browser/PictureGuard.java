package com.familymdm.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import com.familymdm.agent.sitepolicy.NudityPrep;
import com.familymdm.agent.sitepolicy.SkinMask;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * "Pictures in our Browser" (set on the dashboard): every picture a page loads is fetched here
 * first and checked on the phone before the page gets it.
 * - "nudity": a picture that may show nudity (the same on-phone checker as photo alerts) is
 *   replaced by a plain grey picture of the same size.
 * - "skin": that, and every skin-coloured patch of every picture is covered with grey blocks
 *   (SkinMask: by colour, so faces and hands are covered too).
 * Pictures that cannot be read are not shown. Tiny pictures (icons) and SVG drawings pass through.
 * Videos are not touched: Android plays them where the Browser cannot paint over them.
 */
final class PictureGuard {
    private PictureGuard() {}

    private static final int MAX_BYTES = 8 * 1024 * 1024, MAX_SIDE = 800, TINY = 48;
    private static volatile boolean cacheInstalled;

    /** Pictures fetched here are kept on the phone (like the browser's own cache), so a page opened again is fast. */
    private static void ensureHttpCache(Context c) {
        if (cacheInstalled) return;
        cacheInstalled = true;
        try {
            if (android.net.http.HttpResponseCache.getInstalled() == null)
                android.net.http.HttpResponseCache.install(new java.io.File(c.getCacheDir(), "pictures"), 64L * 1024 * 1024);
        } catch (Exception ignored) {
        }
    }
    private static final LruCache<String, byte[]> cache = new LruCache<String, byte[]>(16 * 1024 * 1024) {
        @Override
        protected int sizeOf(String k, byte[] v) {
            return v.length;
        }
    };
    private static volatile ByteBuffer modelBytes;
    private static volatile boolean noModel;
    private static final ThreadLocal<Interpreter> interp = new ThreadLocal<>();

    static boolean isPicture(WebResourceRequest r) {
        if (!"GET".equalsIgnoreCase(r.getMethod()) || r.isForMainFrame()) return false;
        String scheme = r.getUrl().getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) return false;
        String accept = null;
        for (Map.Entry<String, String> e : r.getRequestHeaders().entrySet()) if ("accept".equalsIgnoreCase(e.getKey())) accept = e.getValue();
        if (accept != null && accept.startsWith("image/")) return true;
        String p = r.getUrl().getPath() == null ? "" : r.getUrl().getPath().toLowerCase(java.util.Locale.ROOT);
        return p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png") || p.endsWith(".webp") || p.endsWith(".gif") || p.endsWith(".avif") || p.endsWith(".heic");
    }

    /** The checked picture, or null to let the WebView load it itself (could not be fetched here). */
    static WebResourceResponse handle(Context c, WebResourceRequest r, String mode) {
        String url = r.getUrl().toString();
        ensureHttpCache(c);
        byte[] done = cache.get(mode + "|" + url);
        if (done != null) return image(done);
        byte[] raw;
        String type;
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(10000);
            con.setReadTimeout(15000);
            con.setInstanceFollowRedirects(true);
            for (Map.Entry<String, String> e : r.getRequestHeaders().entrySet()) con.setRequestProperty(e.getKey(), e.getValue());
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) con.setRequestProperty("Cookie", cookie);
            int code = con.getResponseCode();
            if (code < 200 || code >= 300) return null;
            type = con.getContentType() == null ? "" : con.getContentType().toLowerCase(java.util.Locale.ROOT);
            try (InputStream in = con.getInputStream()) {
                ByteArrayOutputStream all = new ByteArrayOutputStream();
                byte[] buf = new byte[32768];
                for (int n; (n = in.read(buf)) > 0; ) {
                    all.write(buf, 0, n);
                    if (all.size() > MAX_BYTES) return blank(1, 1);
                }
                raw = all.toByteArray();
            }
        } catch (Exception e) {
            return null;
        }
        if (type.contains("svg")) return passThrough(raw, type);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return blank(1, 1); // cannot be read, so cannot be checked
        if (bounds.outWidth < TINY && bounds.outHeight < TINY) return passThrough(raw, type);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (o.inSampleSize * 2) >= MAX_SIDE) o.inSampleSize *= 2;
        o.inMutable = true;
        Bitmap b = BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
        if (b == null) return blank(bounds.outWidth, bounds.outHeight);
        int w = b.getWidth(), h = b.getHeight();
        float score = score(c, b);
        if (score >= NudityPrep.THRESHOLD) {
            byte[] grey = encode(Bitmap.createBitmap(new int[]{SkinMask.GREY}, 1, 1, Bitmap.Config.ARGB_8888), w, h);
            cache.put(mode + "|" + url, grey);
            return image(grey);
        }
        if (score < 0 && noModel && "nudity".equals(mode)) return passThrough(raw, type); // no checker in this build
        if ("skin".equals(mode)) {
            int[] px = new int[w * h];
            b.getPixels(px, 0, w, 0, 0, w, h);
            if (SkinMask.cover(px, w, h) > 0) {
                b.setPixels(px, 0, w, 0, 0, w, h);
                byte[] out = encode(b, w, h);
                cache.put(mode + "|" + url, out);
                return image(out);
            }
        }
        return passThrough(raw, type);
    }

    /** JPEG is several times faster to make than PNG; pictures with see-through parts stay PNG. */
    private static byte[] encode(Bitmap b, int w, int h) {
        Bitmap s = b.getWidth() == w && b.getHeight() == h ? b : Bitmap.createScaledBitmap(b, w, h, false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        s.compress(s.hasAlpha() ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 85, out);
        return out.toByteArray();
    }

    private static WebResourceResponse image(byte[] bytes) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-store");
        boolean png = bytes.length > 4 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P';
        return new WebResourceResponse(png ? "image/png" : "image/jpeg", null, 200, "OK", headers, new ByteArrayInputStream(bytes));
    }

    private static WebResourceResponse passThrough(byte[] raw, String type) {
        String mime = type.isEmpty() ? "image/jpeg" : type.split(";")[0].trim();
        return new WebResourceResponse(mime, null, 200, "OK", new HashMap<>(), new ByteArrayInputStream(raw));
    }

    private static WebResourceResponse blank(int w, int h) {
        Bitmap one = Bitmap.createBitmap(new int[]{SkinMask.GREY}, 1, 1, Bitmap.Config.ARGB_8888);
        return image(encode(one, Math.max(1, Math.min(w, 2000)), Math.max(1, Math.min(h, 2000))));
    }

    /** The same on-phone checker as the Agent's photo alerts; -1 when this build has none. */
    private static synchronized ByteBuffer model(Context c) {
        if (modelBytes == null && !noModel) {
            try (InputStream in = c.getAssets().open("nsfw.tflite")) {
                ByteArrayOutputStream all = new ByteArrayOutputStream();
                byte[] buf = new byte[65536];
                for (int n; (n = in.read(buf)) > 0; ) all.write(buf, 0, n);
                ByteBuffer m = ByteBuffer.allocateDirect(all.size()).order(ByteOrder.nativeOrder());
                m.put(all.toByteArray()).rewind();
                modelBytes = m;
            } catch (Throwable e) {
                noModel = true;
            }
        }
        return modelBytes;
    }

    private static float score(Context c, Bitmap picture) {
        Interpreter it = interp.get();
        if (it == null) {
            ByteBuffer m = model(c);
            if (m == null) return -1;
            try {
                Interpreter.Options opt = new Interpreter.Options();
                opt.setNumThreads(1);
                it = new Interpreter(m.duplicate().order(ByteOrder.nativeOrder()), opt);
                interp.set(it);
            } catch (Throwable e) {
                return -1;
            }
        }
        try {
            Bitmap s = Bitmap.createScaledBitmap(picture, NudityPrep.SCALED, NudityPrep.SCALED, true);
            int[] px = new int[NudityPrep.SCALED * NudityPrep.SCALED];
            s.getPixels(px, 0, NudityPrep.SCALED, 0, 0, NudityPrep.SCALED, NudityPrep.SCALED);
            float[] in = NudityPrep.toInput(px);
            ByteBuffer input = ByteBuffer.allocateDirect(in.length * 4).order(ByteOrder.nativeOrder());
            input.asFloatBuffer().put(in);
            float[][] out = new float[1][2];
            it.run(input, out);
            return out[0][1];
        } catch (Throwable e) {
            return -1;
        }
    }
}
