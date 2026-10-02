package com.familymdm.agent;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

/** Minimal status/enrollment screen. Everything else is controlled from the dashboard. */
public class MainActivity extends Activity {
    private TextView status;
    private EditText serverField;
    private EditText codeField;
    private Button enrollButton;
    private Button batteryButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        status = new TextView(this);
        status.setTextSize(16);
        root.addView(status);

        serverField = new EditText(this);
        serverField.setHint("Dashboard address (https://...)");
        serverField.setSingleLine(true);
        root.addView(serverField);

        codeField = new EditText(this);
        codeField.setHint("Enrollment code");
        codeField.setSingleLine(true);
        root.addView(codeField);

        enrollButton = new Button(this);
        enrollButton.setText("Enroll");
        enrollButton.setOnClickListener(v -> enroll());
        root.addView(enrollButton);

        batteryButton = new Button(this);
        batteryButton.setText("Allow background activity");
        batteryButton.setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))));
        root.addView(batteryButton);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        String server = getIntent().getStringExtra("server");
        String code = getIntent().getStringExtra("code");
        if (server != null) serverField.setText(server);
        if (code != null) codeField.setText(code);
        if (server != null && code != null && !Agent.enrolled(this)) enroll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        Agent.startServiceIfEnrolled(this);
    }

    private void refresh() {
        boolean owner = Agent.isOwner(this);
        boolean enrolled = Agent.enrolled(this);
        StringBuilder sb = new StringBuilder();
        sb.append(owner ? "Device owner: yes\n" : "Device owner: NO. Run the adb set-device-owner command first.\n");
        sb.append(enrolled ? "Enrolled: yes\nServer: " + Agent.prefs(this).getString("server", "") : "Enrolled: no");
        status.setText(sb.toString());
        int form = enrolled ? View.GONE : View.VISIBLE;
        serverField.setVisibility(form);
        codeField.setVisibility(form);
        enrollButton.setVisibility(form);
        batteryButton.setVisibility(enrolled ? View.VISIBLE : View.GONE);
    }

    private void enroll() {
        final String server = serverField.getText().toString().trim().replaceAll("/+$", "");
        final String code = codeField.getText().toString().trim();
        if (server.isEmpty() || code.isEmpty()) {
            status.setText("Enter the dashboard address and the enrollment code.");
            return;
        }
        enrollButton.setEnabled(false);
        status.setText("Enrolling...");
        new Thread(() -> {
            String error = null;
            try {
                JSONObject info = new JSONObject();
                info.put("manufacturer", Build.MANUFACTURER);
                info.put("model", Build.MODEL);
                JSONObject body = new JSONObject();
                body.put("code", code);
                body.put("info", info);
                JSONObject reply = Api.post(server + "/agent/enroll", body, null);
                Agent.prefs(this).edit()
                        .putString("server", server)
                        .putString("token", reply.getString("token"))
                        .apply();
                Agent.startServiceIfEnrolled(this);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final String err = error;
            runOnUiThread(() -> {
                enrollButton.setEnabled(true);
                refresh();
                if (err != null) status.setText("Enrollment failed: " + err);
            });
        }).start();
    }
}
