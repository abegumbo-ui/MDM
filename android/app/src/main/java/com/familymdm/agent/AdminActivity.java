package com.familymdm.agent;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;

/**
 * Everything the dashboard can do, on the phone itself, unlocked by the master code. Works offline.
 * MainActivity only opens this after the master code checked out; the unlock lasts 10 minutes.
 */
public class AdminActivity extends Activity {
    private static final int PICK_APK = 1;
    private static final int CONFIRM_CREDENTIAL = 2;

    private LinearLayout root;
    private LinearLayout appsBox;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (System.currentTimeMillis() > Agent.prefs(this).getLong("adminUntil", 0)) {
            finish();
            return;
        }
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        build();
    }

    private boolean unlocked() {
        if (System.currentTimeMillis() > Agent.prefs(this).getLong("adminUntil", 0)) {
            toast("Admin session expired. Enter the master code again.");
            finish();
            return false;
        }
        return true;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private TextView text(String t, int size, boolean gray) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(size);
        if (gray) v.setTextColor(Color.GRAY);
        v.setPadding(0, dp(6), 0, 0);
        return v;
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(v -> {
            if (unlocked()) l.onClick(v);
        });
        return b;
    }

    private void build() {
        root.removeAllViews();
        root.addView(text("Administrator", 24, false));
        status = text("", 14, true);
        root.addView(status);
        root.addView(button("Sync with dashboard now", v -> {
            AgentService.requestSync();
            toast("Checking in...");
        }));

        root.addView(text("Screen lock", 18, false));
        root.addView(button("Lock now", v -> run(() -> Actions.lock(this))));
        root.addView(button("Set screen lock PIN", v -> askPin()));
        root.addView(button("Remove screen lock", v -> run(() -> Actions.clearPin(this))));
        if (Actions.hasScreenLock(this) && !Actions.pinControlActive(this)) {
            root.addView(button("Activate PIN control (confirm current lock once)", v -> activatePinControl()));
        }

        root.addView(text("Apps", 18, false));
        root.addView(button("Install an APK file", v -> pickApk()));
        appsBox = new LinearLayout(this);
        appsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(appsBox);
        root.addView(button("Show / hide apps", v -> showApps()));

        root.addView(text("Device", 18, false));
        root.addView(button("Reboot", v -> confirm("Reboot the phone?", () -> run(() -> {
            Agent.dpm(this).reboot(Agent.admin(this));
            return "rebooting";
        }))));
        root.addView(button("Stop managing (release)", v -> confirm(
                "Release this phone? All restrictions are removed.", () -> release(false))));
        root.addView(button("Stop managing and remove this app", v -> confirm(
                "Release this phone and uninstall the agent?", () -> release(true))));
        root.addView(button("Erase everything (factory reset)", v -> promptWipe()));
        refreshStatus();
    }

    private void refreshStatus() {
        status.setText("Screen lock: " + (Actions.hasScreenLock(this) ? "set" : "NOT set")
                + "\nPIN control: " + (Actions.pinControlActive(this) ? "ready" : "not active")
                + "\nChanges made here are sent to the dashboard when the phone is online.");
    }

    // ---------- helpers ----------
    private interface Job {
        String run() throws Exception;
    }

    private void run(final Job job) {
        try {
            String msg = job.run();
            Agent.addEvent(this, "local", "Master code on phone: " + msg);
            toast(msg);
        } catch (Exception e) {
            String m = e.getMessage() == null ? e.toString() : e.getMessage();
            Agent.addEvent(this, "error", "Master code on phone failed: " + m);
            toast("Failed: " + m);
        }
        refreshStatus();
    }

    private void confirm(String message, final Runnable ok) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Yes", (d, w) -> {
                    if (unlocked()) ok.run();
                })
                .show();
    }

    private void askPin() {
        final EditText input = new EditText(this);
        input.setHint("New PIN (4 to 16 digits)");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("Set screen lock PIN")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Set", (d, w) -> {
                    final String pin = input.getText().toString().trim();
                    if (unlocked()) run(() -> Actions.setPin(this, pin));
                })
                .show();
    }

    private void activatePinControl() {
        KeyguardManager km = getSystemService(KeyguardManager.class);
        Intent i = km.createConfirmDeviceCredentialIntent("Activate PIN control", "Confirm the current screen lock once");
        if (i == null) {
            toast("Nothing to confirm.");
            return;
        }
        startActivityForResult(i, CONFIRM_CREDENTIAL);
    }

    private void promptWipe() {
        final EditText input = new EditText(this);
        input.setHint("Type ERASE to confirm");
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("Erase everything?")
                .setMessage("This factory-resets the phone and cannot be undone.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Erase", (d, w) -> {
                    if (unlocked() && input.getText().toString().trim().equals("ERASE")) {
                        Agent.addEvent(this, "local", "Master code on phone: erasing the device");
                        Agent.dpm(this).wipeData(0);
                    } else {
                        toast("Not erased. You must type ERASE.");
                    }
                })
                .show();
    }

    private void release(boolean uninstall) {
        Agent.addEvent(this, "local", "Master code on phone: " + (uninstall ? "released and removing the agent" : "released"));
        PolicyApplier.release(this);
        stopService(new Intent(this, AgentService.class));
        toast("Management removed.");
        if (uninstall) {
            try {
                startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                toast("You can now uninstall MDM Agent from Settings.");
            }
        }
        finish();
    }

    // ---------- apps: allow / block, remembered as overrides and sent to the dashboard ----------
    private void showApps() {
        appsBox.removeAllViews();
        try {
            JSONArray pk = PolicyApplier.collectPackages(this);
            JSONObject overrides = Agent.getOverrides(this);
            for (int i = 0; i < pk.length(); i++) {
                final JSONObject a = pk.getJSONObject(i);
                final String pkg = a.getString("p");
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(10), dp(8), dp(10), dp(8));
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dp(12));
                bg.setStroke(dp(1), Color.parseColor("#CAC4D0"));
                row.setBackground(bg);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(6);
                appsBox.addView(row, lp);
                String state = a.optBoolean("h") ? "hidden" : "visible";
                if (overrides.has(pkg)) state += ", set here: " + overrides.optString(pkg);
                row.addView(text(a.optString("l", pkg), 16, false));
                row.addView(text(pkg + " · " + state, 12, true));
                LinearLayout buttons = new LinearLayout(this);
                buttons.setOrientation(LinearLayout.HORIZONTAL);
                buttons.addView(button("Allow", v -> setApp(pkg, "allow")));
                buttons.addView(button("Block", v -> setApp(pkg, "block")));
                row.addView(buttons);
            }
        } catch (Exception e) {
            toast("Could not list apps: " + e.getMessage());
        }
    }

    private void setApp(String pkg, String mode) {
        String err = PolicyApplier.applyOverride(this, pkg, mode);
        if (err != null) toast(err);
        else AgentService.requestSync();
        showApps();
    }

    // ---------- APK install ----------
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
        if (requestCode == CONFIRM_CREDENTIAL) {
            toast(resultCode == RESULT_OK ? "PIN control activated." : "Not confirmed.");
            refreshStatus();
            return;
        }
        if (requestCode != PICK_APK || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        toast("Installing...");
        new Thread(() -> {
            String error = null;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new Exception("could not open the file");
                Installer.installFromStream(this, in);
                Agent.addEvent(this, "local", "Master code on phone: installing an APK");
            } catch (Exception e) {
                error = e.toString();
            }
            final String err = error;
            runOnUiThread(() -> {
                if (err != null) toast("Install failed: " + err);
            });
        }).start();
    }
}
