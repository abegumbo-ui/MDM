package com.familymdm.lockdown;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * This app's own in-app Settings menu -- shows only the categories the admin checked during setup
 * (see MainActivity's Settings step), each one a plain row that jumps straight to the real Settings
 * screen for it. Lives in this app's own package, so it's hard-locked into the kiosk exactly like
 * HomeActivity: Android's lock task mode never had to allow the real Settings app at all, since
 * android.settings.* action intents are let through even for an app outside the lock task
 * allowlist -- see Kiosk.java's own note on this.
 */
public class SettingsMenuActivity extends Activity {
    private SharedPreferences prefs() {
        return getSharedPreferences("lockdown", MODE_PRIVATE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        build();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);
        root.addView(Ui.headline(this, "Settings"));

        Set<String> enabled = prefs().getStringSet("settingsCategories", new LinkedHashSet<>());
        LinearLayout card = Ui.card(this, root);
        boolean any = false;
        for (String category : SettingsCategories.ORDER) {
            if (!enabled.contains(category)) continue;
            any = true;
            Ui.add(card, row(category), 4);
        }
        if (!any) card.addView(Ui.body(this, "Nothing here yet.", true));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private LinearLayout row(String category) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 14);
        r.setPadding(pad, pad, pad, pad);
        r.addView(Ui.body(this, SettingsCategories.label(category), false));
        r.setClickable(true);
        r.setFocusable(true);
        r.setOnClickListener(v -> open(category));
        return r;
    }

    private void open(String category) {
        String action = SettingsCategories.action(category);
        if (action == null) return;
        try {
            startActivity(new Intent(action));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open " + SettingsCategories.label(category) + ".", Toast.LENGTH_SHORT).show();
        }
    }
}
