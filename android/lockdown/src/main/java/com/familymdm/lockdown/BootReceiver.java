package com.familymdm.lockdown;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Brings GuardService back after a reboot -- but only once Close Forever has actually run; during
 * setup there's nothing to guard yet, and MainActivity is still the thing the admin is using. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean locked = context.getSharedPreferences("lockdown", Context.MODE_PRIVATE)
                .getBoolean("lockedForever", false);
        if (locked) context.startForegroundService(new Intent(context, GuardService.class));
    }
}
