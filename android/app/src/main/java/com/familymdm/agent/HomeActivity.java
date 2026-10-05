package com.familymdm.agent;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The home screen while home-screen mode is on: a grid of the allowed apps, with the administrator's
 * logo and custom icons. Everything else stays installed and running but cannot be opened.
 */
public class HomeActivity extends Activity {
    static volatile boolean alive;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout root;
    private LinearLayout logoHolder;
    private GridLayout grid;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (!Kiosk.active(HomeActivity.this)) {
                leave();
                return;
            }
            build();
            handler.postDelayed(this, 30000);
        }
    };

    /** Lets Kiosk.clear() exit this screen right away instead of waiting for the 30s refresh. */
    private final BroadcastReceiver kioskChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (!Kiosk.active(HomeActivity.this)) leave();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        alive = true;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        alive = true;
        if (!Kiosk.active(this)) {
            leave();
            return;
        }
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) startLockTask();
        } catch (Exception ignored) {
        }
        registerReceiver(kioskChanged, new IntentFilter(Kiosk.ACTION_CHANGED));
        handler.removeCallbacks(refresh);
        refresh.run();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        ScrollView sv = (ScrollView) root.getParent();
        if (sv != null) sv.scrollTo(0, 0);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        try {
            unregisterReceiver(kioskChanged);
        } catch (IllegalArgumentException ignored) {
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        alive = false;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // This is the home screen; there is nowhere to go back to.
    }

    /** Home-screen mode was switched off or paused: give the phone back to its normal launcher. */
    private void leave() {
        try {
            stopLockTask();
        } catch (Exception ignored) {
        }
        alive = false;
        finish();
    }

    private void build() {
        root.removeAllViews();
        ImageView logo = Ui.logoView(this);
        if (logo != null) Ui.add(root, logo, 0);
        else root.addView(Ui.headline(this, "Apps"));

        grid = new GridLayout(this);
        grid.setColumnCount(3);
        Ui.add(root, grid, 16);

        final PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        for (String pkg : Kiosk.allowedNow(this)) {
            if (pkg.equals(getPackageName())) continue;
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) continue;
            try {
                apps.add(new String[]{pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(), pkg});
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        // Honour schedules that closed since the last policy pass.
        List<String[]> shown = new ArrayList<>();
        org.json.JSONObject schedules = null;
        boolean showBrowser = false;
        try {
            String stored = Agent.prefs(this).getString("policy", null);
            if (stored != null) {
                org.json.JSONObject policy = new org.json.JSONObject(stored);
                schedules = policy.optJSONObject("schedules");
                org.json.JSONArray sites = policy.optJSONArray("sites");
                showBrowser = policy.optBoolean("restrictBrowsing", false) || (sites != null && sites.length() > 0);
            }
        } catch (Exception ignored) {
        }
        if (showBrowser) grid.addView(browserTile((getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 32)) / 3));
        for (String[] a : apps) {
            if (schedules == null || PolicyApplier.withinSchedule(schedules.optJSONObject(a[1]))) shown.add(a);
        }
        Collections.sort(shown, (x, y) -> x[0].compareToIgnoreCase(y[0]));

        if (shown.isEmpty()) {
            Ui.add(root, Ui.body(this, "No apps are available right now.", true), 16);
        }
        int width = (getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 32)) / 3;
        for (String[] a : shown) grid.addView(tile(a[0], a[1], width));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 24);
        root.addView(Ui.button(this, "Administrator", Ui.OUTLINED, v -> startActivity(new Intent(this, MainActivity.class))), lp);
    }

    private LinearLayout tile(String label, final String pkg, int width) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10));
        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.width = width;
        t.setLayoutParams(glp);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(iconFor(pkg));
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

    /** A tile for the separate Browser app, since home-screen mode only lists apps the admin explicitly allowed. */
    private LinearLayout browserTile(int width) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10));
        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.width = width;
        t.setLayoutParams(glp);

        ImageView icon = new ImageView(this);
        icon.setImageResource(android.R.drawable.ic_menu_compass);
        t.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));

        TextView name = Ui.body(this, "Browser", false);
        name.setTextSize(12);
        name.setGravity(Gravity.CENTER);
        t.addView(name);

        t.setOnClickListener(v -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.familymdm.browser");
            if (launch != null) startActivity(launch);
            else Toast.makeText(this, "The Browser app isn't installed on this phone yet.", Toast.LENGTH_SHORT).show();
        });
        return t;
    }

    /** The administrator's custom icon if there is one, otherwise the app's own. */
    private Drawable iconFor(String pkg) {
        try {
            File f = Agent.iconFile(this, pkg);
            if (f.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(f.getPath());
                if (bmp != null) return new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
            }
            return getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return getPackageManager().getDefaultActivityIcon();
        }
    }
}
