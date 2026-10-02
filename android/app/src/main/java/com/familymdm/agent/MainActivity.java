package com.familymdm.agent;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
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
 * Status and enrollment screen. Actions are locked behind codes the administrator controls: one-time
 * codes made in the dashboard (install an APK, remove the agent) or the offline master code.
 */
public class MainActivity extends Activity {
    private static final int PICK_APK = 1;
    private static final long PICK_WINDOW_MS = 5 * 60 * 1000;

    private TextView status;
    private LinearLayout statusCard;
    private LinearLayout lockBox;
    private TextView lockTitle;
    private TextView lockHint;
    private EditText lockField;
    private EditText lockField2;
    private android.widget.Button lockButton;
    private boolean unlocked;
    private boolean leavingForPicker;
    private LinearLayout enrollBox;
    private LinearLayout actionsBox;
    private EditText serverField;
    private EditText codeField;
    private android.widget.Button enrollButton;
    private android.widget.Button pickButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);

        root.addView(Ui.headline(this, "MDM Agent"));

        statusCard = Ui.card(this, root);
        status = Ui.body(this, "", false);
        statusCard.addView(status);

        // ---- enrollment (shown until enrolled) ----
        enrollBox = Ui.card(this, root);
        enrollBox.addView(Ui.titleText(this, "Connect to the dashboard"));
        serverField = Ui.field(this, "Dashboard address (https://...)");
        Ui.add(enrollBox, serverField, 12);
        codeField = Ui.field(this, "Enrollment code");
        Ui.add(enrollBox, codeField, 8);
        enrollButton = Ui.button(this, "Enroll", Ui.FILLED, v -> enroll());
        Ui.add(enrollBox, enrollButton, 12);

        // ---- the person's own code, asked every time the app is opened ----
        lockBox = Ui.card(this, root);
        lockTitle = Ui.titleText(this, "");
        lockBox.addView(lockTitle);
        lockHint = Ui.body(this, "", true);
        lockBox.addView(lockHint);
        lockField = Ui.field(this, "Code");
        lockField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Ui.add(lockBox, lockField, 12);
        lockField2 = Ui.field(this, "Repeat the code");
        lockField2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Ui.add(lockBox, lockField2, 8);
        lockButton = Ui.button(this, "Open", Ui.FILLED, v -> submitLock());
        Ui.add(lockBox, lockButton, 12);

        // ---- code-protected actions (shown once enrolled) ----
        actionsBox = new LinearLayout(this);
        actionsBox.setOrientation(LinearLayout.VERTICAL);
        Ui.add(root, actionsBox, 0);

        LinearLayout mine = Ui.card(this, actionsBox);
        mine.addView(Ui.titleText(this, "Your code"));
        mine.addView(Ui.body(this, "The code you type to open this app. The administrator can see it.", true));
        Ui.add(mine, Ui.button(this, "Change my code", Ui.TONAL, v -> changeMyCode()), 12);

        LinearLayout install = Ui.card(this, actionsBox);
        install.addView(Ui.titleText(this, "Install an app"));
        install.addView(Ui.body(this, "Ask the administrator for a one-time install code, then pick the APK file from this phone.", true));
        Ui.add(install, Ui.button(this, "Install an app (needs code)", Ui.FILLED, v -> promptCode("install")), 12);
        pickButton = Ui.button(this, "Choose APK file", Ui.TONAL, v -> pickApk());
        Ui.add(install, pickButton, 8);

        LinearLayout admin = Ui.card(this, actionsBox);
        admin.addView(Ui.titleText(this, "Administrator"));
        admin.addView(Ui.body(this, "Master code: everything the dashboard can do, here on the phone, even without internet.", true));
        Ui.add(admin, Ui.button(this, "Administrator (master code)", Ui.TONAL, v -> promptMaster()), 12);

        LinearLayout remove = Ui.card(this, actionsBox);
        remove.addView(Ui.titleText(this, "Remove this agent"));
        remove.addView(Ui.body(this, "Ask the administrator for a one-time removal code. This ends all management of the phone.", true));
        Ui.add(remove, Ui.button(this, "Remove agent (needs code)", Ui.DANGER, v -> promptCode("uninstall")), 12);

        Ui.add(actionsBox, Ui.button(this, "Allow background activity", Ui.OUTLINED, v -> startActivity(new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())))), 12);

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
        leavingForPicker = false;
        refresh();
        Agent.startServiceIfEnrolled(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Leaving the app locks it again (except while the file picker is open).
        if (!leavingForPicker) unlocked = false;
    }

    private void refresh() {
        boolean owner = Agent.isOwner(this);
        boolean enrolled = Agent.enrolled(this);
        boolean locked = enrolled && !unlocked;
        StringBuilder sb = new StringBuilder();
        sb.append(owner ? "Device owner: yes\n" : "Device owner: NO. Run the adb set-device-owner command first.\n");
        if (enrolled) {
            long last = Agent.prefs(this).getLong("lastSync", 0);
            sb.append("Enrolled: yes\nServer: ").append(Agent.prefs(this).getString("server", ""));
            sb.append("\nLast check-in: ").append(last == 0 ? "not yet"
                    : DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(last)));
            sb.append("\nAgent build: ").append(Updater.currentBuild(this));
        } else {
            sb.append("Enrolled: no");
        }
        status.setText(sb.toString());
        statusCard.setVisibility(locked ? View.GONE : View.VISIBLE);
        enrollBox.setVisibility(enrolled ? View.GONE : View.VISIBLE);
        lockBox.setVisibility(locked ? View.VISIBLE : View.GONE);
        actionsBox.setVisibility(enrolled && !locked ? View.VISIBLE : View.GONE);
        if (locked) {
            boolean set = AppCode.isSet(this);
            lockTitle.setText(set ? "Enter your code" : "Choose a code");
            lockHint.setText(set
                    ? "Forgot it? Ask the administrator."
                    : "You will type this code every time you open this app. The administrator can see it. Use at least 4 characters.");
            lockField2.setVisibility(set ? View.GONE : View.VISIBLE);
            lockButton.setText(set ? "Open" : "Save code");
        }
        boolean canPick = System.currentTimeMillis() < Agent.prefs(this).getLong("installUntil", 0);
        pickButton.setVisibility(canPick ? View.VISIBLE : View.GONE);
    }

    private void submitLock() {
        String a = lockField.getText().toString();
        if (!AppCode.isSet(this)) {
            String b = lockField2.getText().toString();
            if (a.length() < 4) {
                toast("Use at least 4 characters.");
            } else if (!a.equals(b)) {
                toast("The two codes don't match.");
            } else {
                try {
                    AppCode.set(this, a);
                    unlocked = true;
                    lockField.setText("");
                    lockField2.setText("");
                    AgentService.requestSync();
                    refresh();
                } catch (Exception e) {
                    toast("Could not save the code: " + e.getMessage());
                }
            }
            return;
        }
        String err = AppCode.check(this, a);
        if (err != null && err.equals("Wrong code.") && Master.matches(this, a)) err = null; // the master code always works
        if (err == null) {
            unlocked = true;
            lockField.setText("");
            refresh();
        } else {
            toast(err);
        }
    }

    private void changeMyCode() {
        final EditText one = Ui.field(this, "New code (at least 4 characters)");
        final EditText two = Ui.field(this, "Repeat the new code");
        one.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        two.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        Ui.add(box, one, 8);
        Ui.add(box, two, 8);
        new AlertDialog.Builder(this)
                .setTitle("Change my code")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String a = one.getText().toString();
                    if (a.length() < 4 || !a.equals(two.getText().toString())) {
                        toast("Use at least 4 characters, typed the same twice.");
                        return;
                    }
                    try {
                        AppCode.set(this, a);
                        AgentService.requestSync();
                        toast("Code changed.");
                    } catch (Exception e) {
                        toast("Could not save the code: " + e.getMessage());
                    }
                })
                .show();
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

    // ---------- code-protected actions ----------
    private void promptCode(final String type) {
        final EditText input = Ui.field(this, "8-character code");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        new AlertDialog.Builder(this)
                .setTitle(type.equals("install") ? "Install code" : "Removal code")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("OK", (d, w) -> redeem(type, input.getText().toString().trim()))
                .show();
    }

    private void promptMaster() {
        final EditText input = Ui.field(this, "Master code");
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

    private void pickApk() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/vnd.android.package-archive", "application/octet-stream"});
        try {
            leavingForPicker = true;
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
