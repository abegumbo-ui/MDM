package com.familymdm.agent;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.InputStream;
import java.text.DateFormat;
import java.util.Date;

/**
 * Status and enrollment screen. Two actions are locked behind one-time codes that the admin
 * generates in the dashboard: installing an APK without "unknown sources", and removing this agent.
 */
public class MainActivity extends Activity {
    private static final int PICK_APK = 1;
    private static final long PICK_WINDOW_MS = 5 * 60 * 1000;

    private TextView status;
    private LinearLayout enrollBox;
    private LinearLayout actionsBox;
    private EditText serverField;
    private EditText codeField;
    private Button enrollButton;
    private Button pickButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = new TextView(this);
        title.setText("MDM Agent");
        title.setTextSize(24);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(15);
        status.setPadding(0, dp(8), 0, dp(12));
        root.addView(status);

        // ---- enrollment (shown until enrolled) ----
        enrollBox = card(root);
        serverField = field(enrollBox, "Dashboard address (https://...)");
        codeField = field(enrollBox, "Enrollment code");
        enrollButton = button(enrollBox, "Enroll", v -> enroll());

        // ---- code-protected actions (shown once enrolled) ----
        actionsBox = card(root);
        label(actionsBox, "Install an app");
        hint(actionsBox, "Ask the administrator for a one-time install code. Then pick the APK file from this phone.");
        button(actionsBox, "Install an app (needs code)", v -> promptCode("install"));
        pickButton = button(actionsBox, "Choose APK file", v -> pickApk());
        label(actionsBox, "Remove this agent");
        hint(actionsBox, "Ask the administrator for a one-time removal code. This stops all management of the phone.");
        button(actionsBox, "Remove agent (needs code)", v -> promptCode("uninstall"));
        label(actionsBox, "Administrator");
        hint(actionsBox, "Master code: all dashboard actions on this phone, even without internet.");
        button(actionsBox, "Administrator (master code)", v -> promptMaster());
        button(actionsBox, "Allow background activity", v -> startActivity(new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))));

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

    // ---------- small UI helpers ----------
    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.parseColor("#CAC4D0"));
        box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        parent.addView(box, lp);
        return box;
    }

    private EditText field(LinearLayout parent, String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        parent.addView(e);
        return e;
    }

    private Button button(LinearLayout parent, String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        parent.addView(b);
        return b;
    }

    private void label(LinearLayout parent, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(17);
        t.setPadding(0, dp(10), 0, 0);
        parent.addView(t);
    }

    private void hint(LinearLayout parent, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(Color.GRAY);
        parent.addView(t);
    }

    private void refresh() {
        boolean owner = Agent.isOwner(this);
        boolean enrolled = Agent.enrolled(this);
        StringBuilder sb = new StringBuilder();
        sb.append(owner ? "Device owner: yes\n" : "Device owner: NO. Run the adb set-device-owner command first.\n");
        if (enrolled) {
            long last = Agent.prefs(this).getLong("lastSync", 0);
            sb.append("Enrolled: yes\nServer: ").append(Agent.prefs(this).getString("server", ""));
            sb.append("\nLast check-in: ").append(last == 0 ? "not yet"
                    : DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(last)));
        } else {
            sb.append("Enrolled: no");
        }
        status.setText(sb.toString());
        enrollBox.setVisibility(enrolled ? View.GONE : View.VISIBLE);
        actionsBox.setVisibility(enrolled ? View.VISIBLE : View.GONE);
        boolean canPick = System.currentTimeMillis() < Agent.prefs(this).getLong("installUntil", 0);
        pickButton.setVisibility(canPick ? View.VISIBLE : View.GONE);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    // ---------- enrollment ----------
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

    // ---------- one-time-code actions ----------
    private void promptCode(final String type) {
        final EditText input = new EditText(this);
        input.setHint("8-character code");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        new AlertDialog.Builder(this)
                .setTitle(type.equals("install") ? "Install code" : "Removal code")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("OK", (d, w) -> redeem(type, input.getText().toString().trim()))
                .show();
    }

    private void redeem(final String type, final String code) {
        final String server = Agent.prefs(this).getString("server", null);
        final String token = Agent.prefs(this).getString("token", null);
        if (server == null || token == null || code.isEmpty()) return;
        new Thread(() -> {
            String error = null;
            try {
                JSONObject body = new JSONObject();
                body.put("type", type);
                body.put("code", code);
                Api.post(server + "/agent/redeem", body, token);
            } catch (Exception e) {
                error = e instanceof Api.HttpException && ((Api.HttpException) e).code == 403
                        ? "That code is wrong, already used, or expired." : "Could not check the code: " + e.getMessage();
            }
            final String err = error;
            runOnUiThread(() -> {
                if (err != null) {
                    toast(err);
                } else if (type.equals("install")) {
                    Agent.prefs(this).edit().putLong("installUntil", System.currentTimeMillis() + PICK_WINDOW_MS).apply();
                    refresh();
                    pickApk();
                } else {
                    removeAgent();
                }
            });
        }).start();
    }

    private void promptMaster() {
        final EditText input = new EditText(this);
        input.setHint("Master code");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("Master code")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("OK", (d, w) -> {
                    final String code = input.getText().toString();
                    new Thread(() -> {
                        final String err = Master.check(this, code);
                        runOnUiThread(() -> {
                            if (err != null) {
                                toast(err);
                            } else {
                                Agent.prefs(this).edit().putLong("adminUntil", System.currentTimeMillis() + 10 * 60 * 1000).apply();
                                startActivity(new Intent(this, AdminActivity.class));
                            }
                        });
                    }).start();
                })
                .show();
    }

    private void pickApk() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/vnd.android.package-archive", "application/octet-stream"});
        try {
            startActivityForResult(i, PICK_APK);
        } catch (Exception e) {
            toast("No file picker available on this phone.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_APK || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (System.currentTimeMillis() > Agent.prefs(this).getLong("installUntil", 0)) {
            toast("The install code window has expired. Ask for a new code.");
            return;
        }
        final Uri uri = data.getData();
        toast("Installing...");
        new Thread(() -> {
            String error = null;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new Exception("could not open the file");
                Installer.installFromStream(this, in);
                Agent.prefs(this).edit().putLong("installUntil", 0).apply();
            } catch (Exception e) {
                error = e.toString();
            }
            final String err = error;
            runOnUiThread(() -> {
                refresh();
                if (err != null) toast("Install failed: " + err);
            });
        }).start();
    }

    private void removeAgent() {
        PolicyApplier.release(this);
        stopService(new Intent(this, AgentService.class));
        refresh();
        toast("Management removed. Confirm the uninstall on the next screen.");
        try {
            startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            toast("You can now uninstall MDM Agent from Settings.");
        }
    }
}
