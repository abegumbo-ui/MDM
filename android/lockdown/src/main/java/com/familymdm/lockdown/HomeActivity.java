package com.familymdm.lockdown;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The kiosk home screen -- only reachable once "This device is set up for good" has actually run
 * (the Lockdown switch applies blocks/restrictions live but deliberately never starts this
 * takeover). A plain grid of whatever's checked on the Kiosk home screen picker -- a separate,
 * explicit choice from both the Regular/System apps block lists and the Notifications allow-list,
 * so something can run fine and even notify without ever getting a tile here. Its own "Lockdown"
 * tile always opens MainActivity's setup/admin screen, reachable right up until the final lock-in
 * happens.
 */
public class HomeActivity extends Activity {
    private GridLayout grid;
    private boolean torchOn;

    private SharedPreferences prefs() {
        return getSharedPreferences("lockdown", MODE_PRIVATE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);
        root.addView(Ui.headline(this, "Apps"));
        grid = new GridLayout(this);
        grid.setColumnCount(3);
        Ui.add(root, grid, 16);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        ensureDnsFilterRunning();
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) startLockTask();
        } catch (Exception ignored) {
        }
        ensureDnsFilterRunning();
        build();
    }

    /** Once "This device is set up for good" has run, MainActivity -- where every other bit of
     * "make sure the DNS filter is actually running" logic lives (applyDnsFilterLive(), the retry
     * loop in DnsFilterVpnService itself) -- gets permanently disabled and never runs again. This
     * screen is the only thing still running at that point, so it's the only thing left that can
     * notice the filter isn't up (killed by the OS, or never restarted after a reboot) and bring
     * it back -- the self-managed equivalent of "stays on," without registering as an OS-level
     * always-on VPN. Deliberately plain and cheap (a couple of prefs reads, no DPM/Binder calls
     * now that always-on's own calls are gone) -- safe to run directly here on every resume. */
    private void ensureDnsFilterRunning() {
        boolean wanted = prefs().getBoolean("lockdownOn", false) && !prefs().getBoolean("vpnFilterPaused", false)
                && (!DnsBlocklist.list(this).isEmpty() || !NetworkBlocklist.list(this).isEmpty());
        if (wanted == DnsFilterVpnService.isActive()) return;
        try {
            if (wanted) startForegroundService(new Intent(this, DnsFilterVpnService.class));
            else stopService(new Intent(this, DnsFilterVpnService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onBackPressed() {
        // This is the only home screen there is; there's nowhere to go back to.
    }

    private void build() {
        grid.removeAllViews();
        PackageManager pm = getPackageManager();
        // The Kiosk home screen list (MainActivity's own picker for it) is the one and only thing
        // that decides what gets a tile here -- not whether an app is blocked, not whether it's
        // allowed to notify. An app can be reachable and even noisy without ever having a tile
        // (Android Auto, for instance, launches itself and doesn't need one).
        Set<String> tiles = prefs().getStringSet("kioskTileApps", new LinkedHashSet<>());

        List<String[]> apps = new ArrayList<>();
        for (String pkg : tiles) {
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) continue;
            try {
                apps.add(new String[]{pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)).toString(), pkg});
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));

        int width = (getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 32)) / 3;
        Set<String> settingsCategories = prefs().getStringSet("settingsCategories", new LinkedHashSet<>());
        // Always first while there's still a setup screen to go back to -- this is what makes the
        // lockdown reversible instead of a one-way trap. Gone for good once "This device is set up
        // for good" has run and disabled MainActivity -- there's genuinely nothing left to open.
        if (!prefs().getBoolean("lockedForever", false)) grid.addView(adminTile(width));
        // Always next if any Settings categories are enabled, same as the agent app's own kiosk
        // home screen -- the only way to reach any part of Settings once locked, so it's not
        // subject to being left out like a regular app.
        if (!settingsCategories.isEmpty()) grid.addView(settingsTile(width));
        if (flashCameraId() != null) grid.addView(flashlightTile(width));
        for (String[] a : apps) grid.addView(tile(a[0], a[1], width));
    }

    private LinearLayout adminTile(int width) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10));
        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.width = width;
        t.setLayoutParams(glp);

        ImageView icon = new ImageView(this);
        try {
            icon.setImageDrawable(getPackageManager().getApplicationIcon(getPackageName()));
        } catch (Exception e) {
            icon.setImageDrawable(getPackageManager().getDefaultActivityIcon());
        }
        t.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));

        TextView name = Ui.body(this, "Lockdown", false);
        name.setTextSize(12);
        name.setGravity(Gravity.CENTER);
        t.addView(name);

        t.setOnClickListener(v -> {
            try {
                startActivity(new Intent(this, MainActivity.class));
            } catch (Exception e) {
                // Only happens once "This device is set up for good" has run and disabled
                // MainActivity's own launcher component -- the expected, final state, not a bug.
            }
        });
        return t;
    }

    private LinearLayout settingsTile(int width) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10));
        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.width = width;
        t.setLayoutParams(glp);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(getPackageManager().getDefaultActivityIcon());
        t.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));

        TextView name = Ui.body(this, "Settings", false);
        name.setTextSize(12);
        name.setGravity(Gravity.CENTER);
        t.addView(name);

        t.setOnClickListener(v -> startActivity(new Intent(this, SettingsMenuActivity.class)));
        return t;
    }

    /** Toggles the flash as a torch directly, since Android's lock task mode suppresses Quick
     * Settings expansion while this is the home screen (a deliberate restriction, there to stop
     * Wi-Fi/data/airplane mode being flipped from there too) -- there's no way to reach the usual
     * flashlight tile at all once locked. Same mechanism as the agent app's own kiosk home screen
     * (HomeActivity): setTorchMode() doesn't need the CAMERA permission, it's a torch-only API
     * meant for exactly this. */
    private LinearLayout flashlightTile(int width) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10));
        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.width = width;
        t.setLayoutParams(glp);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_flash);
        icon.setColorFilter(torchOn ? 0xFFFFD600 : 0xFFFFFFFF);
        t.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));

        TextView name = Ui.body(this, torchOn ? "Flashlight: On" : "Flashlight", false);
        name.setTextSize(12);
        name.setGravity(Gravity.CENTER);
        t.addView(name);

        t.setOnClickListener(v -> {
            String id = flashCameraId();
            if (id == null) {
                Toast.makeText(this, "No flash on this phone.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                torchOn = !torchOn;
                cm.setTorchMode(id, torchOn);
                build();
            } catch (Exception e) {
                torchOn = false;
                Toast.makeText(this, "Could not turn on the flashlight.", Toast.LENGTH_SHORT).show();
            }
        });
        return t;
    }

    /** The first back camera with a flash unit, or null if this phone has none. */
    private String flashCameraId() {
        try {
            CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                Boolean has = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(has)) return id;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private LinearLayout tile(String label, String pkg, int width) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10));
        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.width = width;
        t.setLayoutParams(glp);

        ImageView icon = new ImageView(this);
        Drawable d;
        try {
            d = getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            d = getPackageManager().getDefaultActivityIcon();
        }
        icon.setImageDrawable(d);
        t.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));

        TextView name = Ui.body(this, label, false);
        name.setTextSize(12);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(name);

        t.setOnClickListener(v -> {
            try {
                startActivity(getPackageManager().getLaunchIntentForPackage(pkg));
            } catch (Exception e) {
                Toast.makeText(this, "That app can't be opened right now.", Toast.LENGTH_SHORT).show();
            }
        });
        return t;
    }
}
