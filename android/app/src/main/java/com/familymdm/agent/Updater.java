package com.familymdm.agent;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

/** Self-update from the latest GitHub build. Android only accepts it if it is signed with the same key. */
final class Updater {
    private static final String TAG = "MdmAgent";
    private static final String REPO = "abegumbo-ui/MDM";
    private static final long AUTO_CHECK_MS = 6L * 60 * 60 * 1000;

    private Updater() {}

    static int currentBuild(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * {versionCode, apkUrl} of the newest build, or null if it can't be found. Always tries GitHub
     * directly first -- the builds are public, so there's never a real need to go through the
     * dashboard for this, and the main screen now checks automatically on every open. Only falls
     * back to the dashboard's own /agent/update route if GitHub itself can't be reached and this
     * phone actually has a dashboard to fall back to.
     */
    static JSONObject latest(Context c) throws Exception {
        try {
            String base = "https://github.com/" + REPO + "/releases/download/latest";
            byte[] raw = Api.getBytes(base + "/version.json", null);
            JSONObject v = new JSONObject(new String(raw, "UTF-8"));
            return new JSONObject().put("versionCode", v.getInt("versionCode")).put("apkUrl", base + "/mdm-agent.apk");
        } catch (Exception direct) {
            String server = Agent.prefs(c).getString("server", null);
            String token = Agent.prefs(c).getString("token", null);
            if (server == null || token == null) throw direct;
            return Api.post(server + "/agent/update", new JSONObject(), token).optJSONObject("latest");
        }
    }

    static String update(Context c, boolean force) throws Exception {
        JSONObject latest = latest(c);
        if (latest == null) throw new Exception("could not find the latest build");
        int mine = currentBuild(c);
        int theirs = latest.getInt("versionCode");
        if (theirs <= mine && !force) return "already up to date (build " + mine + ")";
        Agent.addEvent(c, "update", "Updating the agent from build " + mine + " to build " + theirs);
        Installer.installFromUrl(c, latest.getString("apkUrl"), null);
        return "downloading build " + theirs + " (was " + mine + "); the agent restarts when it is installed";
    }

    /** With auto-update on, look for a newer build every few hours. */
    static void maybeAutoUpdate(Context c, JSONObject policy) {
        if (!policy.optBoolean("autoUpdate", false)) return;
        long last = Agent.prefs(c).getLong("lastUpdateCheck", 0);
        if (System.currentTimeMillis() - last < AUTO_CHECK_MS) return;
        Agent.prefs(c).edit().putLong("lastUpdateCheck", System.currentTimeMillis()).apply();
        try {
            update(c, false);
        } catch (Exception e) {
            Log.w(TAG, "auto-update failed: " + e);
        }
    }
}
