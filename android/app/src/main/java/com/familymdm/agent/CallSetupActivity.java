package com.familymdm.agent;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.UserManager;

import java.util.HashSet;
import java.util.Set;

/**
 * Asks for the call-screening and call-redirection roles (Android's own "Allow" dialogs). Opened
 * from the "Finish setting up call controls" notification. Android gives no device-owner way to
 * take these roles silently, so this one tap on the phone is needed once.
 *
 * "Block changing default apps" (if on) would also block these dialogs, so it is lifted for the
 * moment the dialogs are open and put back straight after.
 */
public class CallSetupActivity extends Activity {
    private static final int REQ = 7;
    private final Set<String> asked = new HashSet<>();
    private boolean liftedDefaultApps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT < 29) {
            finish();
            return;
        }
        try {
            UserManager um = getSystemService(UserManager.class);
            if (um.hasUserRestriction("disallow_config_default_apps") && Agent.isOwner(this)) {
                Agent.dpm(this).clearUserRestriction(Agent.admin(this), "disallow_config_default_apps");
                liftedDefaultApps = true;
            }
        } catch (Exception ignored) {
        }
        next();
    }

    private void next() {
        RoleManager rm = getSystemService(RoleManager.class);
        for (String role : new String[]{RoleManager.ROLE_CALL_SCREENING, RoleManager.ROLE_CALL_REDIRECTION}) {
            if (rm != null && Calls.wanted(this, role) && rm.isRoleAvailable(role) && !rm.isRoleHeld(role) && asked.add(role)) {
                startActivityForResult(rm.createRequestRoleIntent(role), REQ);
                return;
            }
        }
        done();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        next();
    }

    private void done() {
        if (liftedDefaultApps) {
            try {
                Agent.dpm(this).addUserRestriction(Agent.admin(this), "disallow_config_default_apps");
            } catch (Exception ignored) {
            }
            liftedDefaultApps = false;
        }
        if (!Calls.needsSetup(this)) Calls.clearReminder(this);
        AgentService.requestSync();
        finish();
    }

    @Override
    protected void onDestroy() {
        if (liftedDefaultApps) {
            try {
                Agent.dpm(this).addUserRestriction(Agent.admin(this), "disallow_config_default_apps");
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }
}
