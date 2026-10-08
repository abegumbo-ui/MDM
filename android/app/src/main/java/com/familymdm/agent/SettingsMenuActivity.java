package com.familymdm.agent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Its own launcher icon, named and shaped like the real Settings app, showing only the categories
 * that are safe -- each row jumps straight to the real screen (see SettingsMenu for how). Never
 * shows the real Settings app's own list, so a category left out of SettingsMenu.ORDER is simply
 * not a row here at all, not a disabled one.
 */
public class SettingsMenuActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);
        root.addView(Ui.headline(this, "Settings"));

        java.util.Set<String> hidden = SettingsMenu.hiddenCategories(this);
        for (String category : SettingsMenu.ORDER) {
            if (hidden.contains(category)) continue;
            root.addView(row(category));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private LinearLayout row(String category) {
        String label = SettingsMenu.label(category);
        boolean readyYet = !SettingsMenu.NEEDS_LEARN.contains(category) || SettingsMenu.learnedTarget(this, category) != null;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int vPad = Ui.dp(this, 16);
        row.setPadding(0, vPad, 0, vPad);

        TextView name = Ui.body(this, label, !readyYet);
        name.setTextSize(18);
        row.addView(name);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(lp);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> open(category, label));
        return row;
    }

    private void open(String category, String label) {
        Intent i = SettingsMenu.intentFor(this, category);
        if (i == null) {
            String msg = category.equals("androidAuto")
                    ? "Android Auto is not installed on this phone."
                    : "Not set up yet -- an administrator needs to learn this screen first.";
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open " + label + ".", Toast.LENGTH_SHORT).show();
        }
    }
}
