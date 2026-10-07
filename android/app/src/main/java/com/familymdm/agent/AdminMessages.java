package com.familymdm.agent;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Local-only inbox for "Message the Administrator": text plus an optional photo, stored on this
 * phone and read in Administrator. Deliberately not sent to any dashboard -- the dashboard already
 * has its own separate free-text message channel (Agent.addMessage); this is a second, simpler one
 * that stays entirely on the phone, per the user's choice to keep it local for now.
 */
final class AdminMessages {
    private static final String KEY = "adminMessages";

    private AdminMessages() {}

    static JSONArray list(Context c) {
        try {
            return new JSONArray(Agent.prefs(c).getString(KEY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static void add(Context c, String text, String photoPath) {
        try {
            JSONArray arr = list(c);
            JSONObject m = new JSONObject();
            m.put("text", text);
            m.put("photo", photoPath == null ? JSONObject.NULL : photoPath);
            m.put("at", System.currentTimeMillis());
            arr.put(m);
            Agent.prefs(c).edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static void removeAt(Context c, int index) {
        JSONArray arr = list(c);
        JSONArray next = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            if (i != index) next.put(arr.optJSONObject(i));
        }
        Agent.prefs(c).edit().putString(KEY, next.toString()).apply();
    }

    /** Copies the picked photo into this app's own files dir -- the picked content:// Uri isn't guaranteed to stay readable later. */
    static String savePhoto(Context c, InputStream in) throws Exception {
        File dir = new File(c.getFilesDir(), "messagePhotos");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File out = new File(dir, "msg_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        }
        return out.getAbsolutePath();
    }
}
