package com.familymdm.lockdown;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
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
 * takeover). A plain grid of whatever's allowed (Maps, Waze, Android Auto, plus anything the admin
 * added on the Regular Apps picker). Its own "Lockdown" tile always opens MainActivity's
 * setup/admin screen, reachable right up until the final lock-in happens.
 */
public class HomeActivity extends Activity {
    private GridLayout grid;

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
        build();
    }

    @Override
    public void onBackPressed() {
        // This is the only home screen there is; there's nowhere to go back to.
    }

    private void build() {
        grid.removeAllViews();
        PackageManager pm = getPackageManager();
        Set<String> allowed = prefs().getStringSet("allowedApps", new LinkedHashSet<>());

        Set<String> muted = prefs().getStringSet("mutedNotificationPackages", new LinkedHashSet<>());
        List<String[]> apps = new ArrayList<>();
        for (String pkg : allowed) {
            // Muted (Notifications picker) means "runs, but stays out of sight" -- an allowed app
            // that's also muted keeps running (needed for things like Android Auto) but gets no
            // icon here to tap into, same as its notifications getting no chance to be tapped.
            if (muted.contains(pkg)) continue;
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) continue;
            try {
                apps.add(new String[]{pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(), pkg});
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
