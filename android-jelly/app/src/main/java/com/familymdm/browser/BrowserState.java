package com.familymdm.browser;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Everything Browser remembers about how it's set up, when it isn't managed by the separate agent
 * app. Exactly one of "connected to a dashboard directly" or "set up on its own" applies at a time;
 * see BrowserActivity.Mode for how that's decided.
 */
final class BrowserState {
    private BrowserState() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("browser", Context.MODE_PRIVATE);
    }

    // ---------- connected directly to a dashboard ----------

    static boolean isOnline(Context c) {
        return prefs(c).getString("token", null) != null;
    }

    static void setOnline(Context c, String server, String token) {
        prefs(c).edit().putString("server", server).putString("token", token).putBoolean("standalone", false).apply();
    }

    static String server(Context c) {
        return prefs(c).getString("server", null);
    }

    static String token(Context c) {
        return prefs(c).getString("token", null);
    }

    /** The last site list the dashboard sent (cached so pages can be checked without a network round trip). */
    static JSONArray cachedSites(Context c) {
        try {
            return new JSONArray(prefs(c).getString("cachedSites", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    static void setCachedSites(Context c, JSONArray sites) {
        prefs(c).edit().putString("cachedSites", sites.toString()).putLong("lastOnlineSync", System.currentTimeMillis()).apply();
    }

    static long lastOnlineSync(Context c) {
        return prefs(c).getLong("lastOnlineSync", 0);
    }

    // ---------- pages requested that weren't on the list, reported to the dashboard on the next sync ----------

    static synchronized void addSiteRequest(Context c, String url) {
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("siteRequests", "[]"));
            for (int i = 0; i < arr.length(); i++) if (url.equals(arr.getJSONObject(i).optString("url"))) return;
            JSONObject r = new JSONObject();
            r.put("url", url);
            arr.put(r);
            prefs(c).edit().putString("siteRequests", arr.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    static synchronized JSONArray peekSiteRequests(Context c) {
        try {
            return new JSONArray(prefs(c).getString("siteRequests", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    static synchronized void dropSiteRequests(Context c, int count) {
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("siteRequests", "[]"));
            JSONArray rest = new JSONArray();
            for (int i = count; i < arr.length(); i++) rest.put(arr.get(i));
            prefs(c).edit().putString("siteRequests", rest.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    // ---------- set up on its own: a local master code and a local allowlist, no server at all ----------

    static boolean isStandalone(Context c) {
        return prefs(c).getBoolean("standalone", false);
    }

    static void setStandalone(Context c) {
        prefs(c).edit().putBoolean("standalone", true).remove("token").apply();
    }

    static JSONArray localSites(Context c) {
        try {
            return new JSONArray(prefs(c).getString("localSites", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    static void addLocalSite(Context c, String type, String url, String host) throws JSONException {
        JSONArray sites = localSites(c);
        JSONObject entry = new JSONObject();
        entry.put("type", type);
        entry.put("url", url);
        entry.put("host", host);
        entry.put("label", host);
        entry.put("blockImages", false);
        entry.put("installable", true);
        sites.put(entry);
        prefs(c).edit().putString("localSites", sites.toString()).apply();
    }

    static void removeLocalSite(Context c, int index) {
        try {
            JSONArray sites = localSites(c);
            JSONArray rest = new JSONArray();
            for (int i = 0; i < sites.length(); i++) if (i != index) rest.put(sites.get(i));
            prefs(c).edit().putString("localSites", rest.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    /** True once either path has been set up; before this, Browser allows nothing at all. */
    static boolean isConfigured(Context c) {
        return isOnline(c) || isStandalone(c);
    }
}
