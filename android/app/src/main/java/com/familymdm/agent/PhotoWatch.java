package com.familymdm.agent;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Size;

import com.familymdm.agent.sitepolicy.NudityPrep;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;

/**
 * "Alert me about photos that may show nudity": every new photo saved on the phone (camera,
 * WhatsApp and other chat apps, downloads, screenshots) is checked on the phone by NudityCheck. When
 * one looks like nudity, only a line of text goes to the parent: when, which folder, how sure. The
 * photo itself never leaves the phone -- sending it, even to a parent, would mean sending a nude
 * picture of a child. Only photos saved after the switch was turned on are checked; videos are not.
 */
final class PhotoWatch {
    private PhotoWatch() {}

    private static final String PENDING = "nudityPending";
    private static final int PER_RUN = 15;

    static void apply(Context c, boolean on) {
        boolean was = Agent.prefs(c).getBoolean("nudityAlerts", false);
        Agent.prefs(c).edit().putBoolean("nudityAlerts", on).apply();
        if (on && !was) {
            grant(c);
            // Start from the newest photo now: the old gallery is not gone through.
            Agent.prefs(c).edit().putLong("nudityLastId", newestId(c)).apply();
        }
    }

    private static void grant(Context c) {
        String perm = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        try {
            Agent.dpm(c).setPermissionGrantState(Agent.admin(c), c.getPackageName(), perm, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
        } catch (Exception ignored) {
        }
    }

    private static long newestId(Context c) {
        try (Cursor cur = c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID}, null, null, MediaStore.Images.Media._ID + " DESC")) {
            if (cur != null && cur.moveToFirst()) return cur.getLong(0);
        } catch (Exception ignored) {
        }
        return 0;
    }

    /** Checks up to PER_RUN new photos. Called from the background check-in. */
    static void scan(Context c) {
        if (!Agent.prefs(c).getBoolean("nudityAlerts", false) || !NudityCheck.available(c)) return;
        grant(c);
        long last = Agent.prefs(c).getLong("nudityLastId", -1);
        if (last < 0) {
            Agent.prefs(c).edit().putLong("nudityLastId", newestId(c)).apply();
            return;
        }
        String[] cols = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.BUCKET_DISPLAY_NAME};
        try (Cursor cur = c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cols,
                MediaStore.Images.Media._ID + " > ?", new String[]{String.valueOf(last)}, MediaStore.Images.Media._ID + " ASC")) {
            if (cur == null) return;
            int done = 0;
            while (cur.moveToNext() && done++ < PER_RUN) {
                long id = cur.getLong(0);
                Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                Bitmap b = load(c, uri);
                float score = b != null ? NudityCheck.score(c, b) : -1;
                if (score >= NudityPrep.THRESHOLD) keep(c, cur.getLong(1) * 1000L, cur.getString(2), score);
                Agent.prefs(c).edit().putLong("nudityLastId", id).apply();
            }
        } catch (SecurityException e) {
            // No permission to read photos (not device owner, or refused): nothing can be checked.
        } catch (Exception ignored) {
        }
    }

    private static Bitmap load(Context c, Uri uri) {
        try {
            if (Build.VERSION.SDK_INT >= 29) return c.getContentResolver().loadThumbnail(uri, new Size(512, 512), null);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= 512) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) { return BitmapFactory.decodeStream(in, null, d); }
        } catch (Exception e) {
            return null;
        }
    }

    private static synchronized void keep(Context c, long at, String folder, float score) {
        try {
            JSONArray list = new JSONArray(Agent.prefs(c).getString(PENDING, "[]"));
            JSONObject o = new JSONObject();
            o.put("at", at > 0 ? at : System.currentTimeMillis());
            o.put("folder", folder == null ? "" : folder);
            o.put("score", Math.round(score * 100));
            list.put(o);
            JSONArray out = new JSONArray();
            for (int i = Math.max(0, list.length() - 50); i < list.length(); i++) out.put(list.get(i));
            Agent.prefs(c).edit().putString(PENDING, out.toString()).apply();
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
}
