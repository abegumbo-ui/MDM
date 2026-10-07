package com.familymdm.agent;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Turns the Browser add-on's own launcher icon (the BrowserAddon activity-alias in
 * AndroidManifest.xml) on or off. Nothing here touches the separate standalone Browser app that
 * may already be installed -- this is purely additive, and only ever runs once an administrator
 * explicitly turns it on from AdminActivity.buildAddonsSection().
 */
final class BrowserAddon {
    private BrowserAddon() {}

    private static ComponentName alias(Context c) {
        return new ComponentName(c, "com.familymdm.agent.BrowserAddon");
    }

    static boolean isEnabled(Context c) {
        return c.getPackageManager().getComponentEnabledSetting(alias(c)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    static void setEnabled(Context c, boolean on) {
        c.getPackageManager().setComponentEnabledSetting(alias(c),
                on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP);
        Agent.addEvent(c, "local", "Master code on phone: Browser add-on turned " + (on ? "on" : "off"));
    }
}
