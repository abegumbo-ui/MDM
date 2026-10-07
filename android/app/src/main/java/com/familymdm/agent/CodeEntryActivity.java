package com.familymdm.agent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The same full-screen look as AdminCodeActivity (shield, headline, subtitle, code field, big
 * filled button), generalized for every other code prompt in the app -- the App PIN unlock, the
 * Administrator bypass from the lock screen, and the one-time install/uninstall codes -- instead
 * of each one using its own plain AlertDialog. This activity only collects the typed code and
 * hands it back via onActivityResult; the caller still does its own verification (CodeRedeem,
 * Master.check, AppPin.check), exactly as before.
 */
public class CodeEntryActivity extends Activity {
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_SUBTITLE = "subtitle";
    static final String EXTRA_BUTTON = "button";
    static final String EXTRA_CODE = "code";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = Ui.dp(this, 32);
        root.setPadding(pad, pad, pad, pad);

        ImageView shield = new ImageView(this);
        shield.setImageResource(R.drawable.ic_shield);
        shield.setColorFilter(Ui.color(this, R.color.m3_primary));
        Ui.add(root, shield, 0);
        shield.getLayoutParams().width = Ui.dp(this, 72);
        shield.getLayoutParams().height = Ui.dp(this, 72);

        TextView title = Ui.headline(this, nonNull(getIntent().getStringExtra(EXTRA_TITLE), "Enter Code"));
        title.setGravity(Gravity.CENTER);
        Ui.add(root, title, 16);

        String subtitle = getIntent().getStringExtra(EXTRA_SUBTITLE);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = Ui.body(this, subtitle, true);
            sub.setGravity(Gravity.CENTER);
            Ui.add(root, sub, 4);
        }

        final EditText input = Ui.field(this, "Code");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                Ui.dp(this, 260), ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = Ui.dp(this, 28);
        root.addView(input, inputLp);

        android.widget.Button submit = Ui.button(this, nonNull(getIntent().getStringExtra(EXTRA_BUTTON), "Submit"),
                Ui.FILLED, v -> {
                    setResult(RESULT_OK, new Intent().putExtra(EXTRA_CODE, input.getText().toString()));
                    finish();
                });
        LinearLayout.LayoutParams submitLp = new LinearLayout.LayoutParams(
                Ui.dp(this, 260), ViewGroup.LayoutParams.WRAP_CONTENT);
        submitLp.topMargin = Ui.dp(this, 16);
        root.addView(submit, submitLp);

        android.widget.Button cancel = Ui.button(this, "Cancel", Ui.OUTLINED, v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                Ui.dp(this, 260), ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelLp.topMargin = Ui.dp(this, 8);
        root.addView(cancel, cancelLp);

        setContentView(root);
    }

    private static String nonNull(String s, String fallback) {
        return s == null ? fallback : s;
    }
}
