package com.familymdm.agent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** A proper full screen for the Administrator (master) code, instead of a plain dialog box. */
public class AdminCodeActivity extends Activity {
    private EditText input;
    private android.widget.Button submit;

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

        TextView title = Ui.headline(this, "Administrator");
        title.setGravity(Gravity.CENTER);
        Ui.add(root, title, 16);

        TextView sub = Ui.body(this, "Enter the master code to continue.", true);
        sub.setGravity(Gravity.CENTER);
        Ui.add(root, sub, 4);

        input = Ui.field(this, "Code");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                Ui.dp(this, 260), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = Ui.dp(this, 28);
        root.addView(input, inputLp);

        submit = Ui.button(this, "Unlock", Ui.FILLED, v -> submit());
        LinearLayout.LayoutParams submitLp = new LinearLayout.LayoutParams(
                Ui.dp(this, 260), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        submitLp.topMargin = Ui.dp(this, 16);
        root.addView(submit, submitLp);

        setContentView(root);
    }

    private void submit() {
        final String code = input.getText().toString();
        submit.setEnabled(false);
        new Thread(() -> {
            final String err = Master.check(this, code);
            runOnUiThread(() -> {
                submit.setEnabled(true);
                if (err != null) {
                    Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                } else {
                    Agent.prefs(this).edit().putLong("adminUntil", System.currentTimeMillis() + 10 * 60 * 1000).apply();
                    startActivity(new Intent(this, AdminActivity.class));
                    finish();
                }
            });
        }).start();
    }
}
