package com.familymdm.lockdown;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The PackageInstaller.uninstall() target this app points at -- as device owner, the uninstall
 * itself is silent either way; this only exists because the API requires a valid IntentSender to
 * report back to, and there's nothing useful to do with that result during one-time setup. */
public class UninstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
    }
}
