package com.familymdm.browser;

import android.content.Context;

/** Small helpers for the browser's own build settings. */
public final class BrowserApp {
    private BrowserApp() {}

    /** Where the newest version is announced (browser-version.json), built in at build time; "" if none. */
    public static String updateUrl(Context c) {
        int id = c.getResources().getIdentifier("update_url", "string", c.getPackageName());
        return id == 0 ? "" : c.getString(id);
    }

    /** The dashboard's address, built in when the app was built (DASHBOARD_URL); "" if none. */
    public static String defaultServer(Context c) {
        int id = c.getResources().getIdentifier("default_server", "string", c.getPackageName());
        return id == 0 ? "" : c.getString(id);
    }
}
