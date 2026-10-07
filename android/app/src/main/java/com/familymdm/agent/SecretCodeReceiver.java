package com.familymdm.agent;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * Dial *#*#636#*#* ("MDM" on a phone keypad) on the phone's own dialer app and Android broadcasts
 * this -- the standard "secret code" mechanism most stock dialers support (a few OEM dialers
 * don't). The only job here is bringing the app's own icon back after "hideAppIcon" hid it
 * (PolicyApplier.applyHideAppIcon): nothing else in the app is ever touched by this, and nothing
 * about device-owner status or any other restriction changes either way.
 */
public class SecretCodeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            PackageManager pm = context.getPackageManager();
            ComponentName launcher = new ComponentName(context, MainActivity.class);
            pm.setComponentEnabledSetting(launcher, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
            // The dashboard's own "hideAppIcon" setting would just re-hide it again at the next
            // sync otherwise -- this is meant as a real way back in, not a one-sync reprieve.
            try {
                org.json.JSONObject policy = new org.json.JSONObject(Agent.prefs(context).getString("policy", "{}"));
                policy.put("hideAppIcon", false);
                Agent.prefs(context).edit().putString("policy", policy.toString()).apply();
            } catch (Exception ignored) {
            }
            Agent.addEvent(context, "local", "Secret code dialed: the app icon is visible again");
        } catch (Exception ignored) {
        }
    }
}
