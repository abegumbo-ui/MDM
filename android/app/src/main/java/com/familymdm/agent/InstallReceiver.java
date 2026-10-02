package com.familymdm.agent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Receives the final status of an install/uninstall and queues it for the next report. */
public class InstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        String pkg = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirm);
                } catch (Exception ignored) {
                }
            }
            return;
        }
        boolean ok = status == PackageInstaller.STATUS_SUCCESS;
        String text = (pkg == null ? "" : pkg + ": ") + (ok ? "done" : (message == null ? "failed (status " + status + ")" : message));
        Agent.addResult(context, "pkg-" + System.currentTimeMillis(), "install-result", ok, text);
        Toast.makeText(context, ok ? "App installed" : "Install failed: " + text, Toast.LENGTH_LONG).show();
    }
}
