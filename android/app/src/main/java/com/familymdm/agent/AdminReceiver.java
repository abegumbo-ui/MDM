package com.familymdm.agent;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.util.Log;

public class AdminReceiver extends DeviceAdminReceiver {
    private static final String TAG = "MdmAgent";

    @Override
    public void onEnabled(Context context, Intent intent) {
        Agent.startServiceIfEnrolled(context);
    }

    /**
     * Fired once after QR-code (or NFC) provisioning finishes making this app the device owner.
     * The QR can carry a server address and a one-time enrollment code in its admin extras bundle,
     * so scanning it is the only step needed -- nothing to type on the phone itself.
     */
    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        if (!Agent.dpm(context).isDeviceOwnerApp(context.getPackageName())) return;
        PersistableBundle extras = intent.getParcelableExtra(
                android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);
        String server = extras == null ? null : extras.getString("server");
        String code = extras == null ? null : extras.getString("code");
        if (server != null && !server.isEmpty() && code != null && !code.isEmpty()) {
            new Thread(() -> {
                try {
                    Agent.enrollWith(context, server, code);
                    Agent.startServiceIfEnrolled(context);
                } catch (Exception e) {
                    Log.w(TAG, "QR-code enrollment failed: " + e);
                }
                startMain(context);
            }).start();
        } else {
            startMain(context);
        }
    }

    /** Fired when another device-owner app hands this app device-owner status via transferOwnership(). */
    @Override
    public void onTransferOwnershipComplete(Context context, PersistableBundle bundle) {
        startMain(context);
    }

    private void startMain(Context context) {
        context.startActivity(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
