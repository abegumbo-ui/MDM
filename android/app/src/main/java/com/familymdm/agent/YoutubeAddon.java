package com.familymdm.agent;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/** Turns the YouTube add-on's own launcher icon (the YoutubeAddon alias) on or off, from the dashboard. */
final class YoutubeAddon {
    private YoutubeAddon() {}

    private static ComponentName alias(Context c) {
        return new ComponentName(c, "com.familymdm.agent.YoutubeAddon");
    }

    static boolean isEnabled(Context c) {
        return c.getPackageManager().getComponentEnabledSetting(alias(c)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    static void setEnabled(Context c, boolean on) {
        c.getPackageManager().setComponentEnabledSetting(alias(c),
                on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP);
    }
}
