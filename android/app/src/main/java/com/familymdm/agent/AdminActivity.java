package com.familymdm.agent;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
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
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);
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

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private boolean isPackageInstalled(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Button that refuses to act once the 10-minute admin session has run out. */
    private android.widget.Button action(LinearLayout box, String label, int style, final View.OnClickListener l) {
        android.widget.Button b = Ui.button(this, label, style, v -> {
            if (unlocked()) l.onClick(v);
        });
        Ui.add(box, b, 8);
        return b;
    }

    private void build() {
        root.removeAllViews();
        root.addView(Ui.headline(this, "Administrator"));
        LinearLayout statusCard = Ui.card(this, root);
        status = Ui.body(this, "", false);
        statusCard.addView(status);
        if (Agent.standalone(this)) {
            action(statusCard, "Phone settings (apps, restrictions, schedules, reset protection)", Ui.FILLED,
                    v -> startActivity(new Intent(this, LocalSettingsActivity.class)));
        } else {
            action(statusCard, "Sync with dashboard now", Ui.TONAL, v -> {
                AgentService.requestSync();
                toast("Checking in...");
            });
        }

        LinearLayout lock = Ui.card(this, root);
        lock.addView(Ui.titleText(this, "Lock"));
        action(lock, "Lock now", Ui.FILLED, v -> run(() -> Actions.lock(this, 0, "")));
        action(lock, "Lock with a message and time…", Ui.TONAL, v -> askLock());
        if (Agent.prefs(this).getLong("lockUntil", 0) > System.currentTimeMillis()) {
            action(lock, "Unlock now", Ui.OUTLINED, v -> run(() -> Actions.unlock(this)));
        }
        action(lock, "Set screen lock PIN", Ui.TONAL, v -> askPin());
        action(lock, "Remove screen lock", Ui.OUTLINED, v -> run(() -> Actions.clearPin(this)));
        if (Actions.hasScreenLock(this) && !Actions.pinControlActive(this)) {
            action(lock, "Activate PIN control (confirm current lock once)", Ui.OUTLINED, v -> activatePinControl());
        }

        LinearLayout apps = Ui.card(this, root);
        apps.addView(Ui.titleText(this, "Apps"));
        action(apps, "Install an APK file", Ui.FILLED, v -> pickApk());
        if (!Agent.standalone(this)) action(apps, "Show / hide apps", Ui.TONAL, v -> showApps());
        appsBox = new LinearLayout(this);
        appsBox.setOrientation(LinearLayout.VERTICAL);
        Ui.add(apps, appsBox, 0);

        LinearLayout browser = Ui.card(this, root);
        browser.addView(Ui.titleText(this, "Browser"));
        boolean browserInstalled = isPackageInstalled("com.familymdm.browser");
        browser.addView(Ui.body(this, browserInstalled
                ? "Installed. Only opens sites you've allowed; hooks into this phone's site rules on its own."
                : "A separate app with its own whitelist, so other apps don't need a browser inside them.", true));
        action(browser, browserInstalled ? "Reinstall Browser app" : "Install Browser app", Ui.TONAL, v -> {
            toast("Downloading the Browser app...");
            new Thread(() -> {
                String msg;
                try {
                    Installer.installFromUrl(this,
                            "https://github.com/abegumbo-ui/MDM/releases/download/latest/mdm-browser.apk", null);
                    PolicyApplier.applyStored(this);
                    msg = "Installing the Browser app now.";
                } catch (Exception e) {
                    msg = "Could not install the Browser app: " + (e.getMessage() == null ? e.toString() : e.getMessage());
                }
                final String text = msg;
                runOnUiThread(() -> toast(text));
            }).start();
        });
        if (browserInstalled) {
            action(browser, "Open Browser", Ui.TONAL, v -> {
                Intent launch = getPackageManager().getLaunchIntentForPackage("com.familymdm.browser");
                if (launch != null) startActivity(launch);
                else toast("The Browser app isn't installed on this phone yet.");
            });
            action(browser, "Browse freely for a while…", Ui.OUTLINED, v -> askFreebrowse());
        }

        LinearLayout home = Ui.card(this, root);
        home.addView(Ui.titleText(this, "Home screen mode"));
        home.addView(Ui.body(this, Kiosk.paused(this) ? "Paused: the phone is working normally."
                : Kiosk.active(this) ? "On: only allowed apps can be opened." : "Off.", true));
        if (Kiosk.paused(this)) {
            action(home, "Resume home screen mode", Ui.FILLED, v -> {
                Agent.prefs(this).edit().putBoolean("kioskPaused", false).apply();
                Agent.addEvent(this, "local", "Master code on phone: resumed home screen mode");
                new Thread(() -> {
                    PolicyApplier.applyStored(this);
                    AgentService.requestSync();
                    runOnUiThread(this::build);
                }).start();
            });
        } else if (Kiosk.active(this)) {
            action(home, "Pause home screen mode", Ui.OUTLINED, v -> {
                Agent.prefs(this).edit().putBoolean("kioskPaused", true).apply();
                Agent.addEvent(this, "local", "Master code on phone: paused home screen mode");
                Kiosk.clear(this);
                Kiosk.syncPreferredActivities(this, false, Agent.prefs(this).getBoolean("prefBrowser", false));
                try {
                    stopLockTask();
                } catch (Exception ignored) {
                }
                build();
                toast("Paused. The phone works normally until you resume it.");
            });
        }

        LinearLayout appLockCard = Ui.card(this, root);
        appLockCard.addView(Ui.titleText(this, "App lock"));
        boolean appLockOn = Agent.prefs(this).getBoolean("appLock", false);
        appLockCard.addView(Ui.body(this, appLockOn
                ? "On: opening MDM Agent needs your fingerprint, face, or device PIN."
                : "Off: anyone who opens this app can see it with no extra check.", true));
        if (appLockOn) {
            action(appLockCard, "Turn off app lock", Ui.OUTLINED, v -> {
                Agent.prefs(this).edit().putBoolean("appLock", false).apply();
                Agent.addEvent(this, "local", "Master code on phone: turned off app lock");
                build();
            });
        } else {
            action(appLockCard, "Turn on app lock", Ui.TONAL, v -> {
                KeyguardManager km = getSystemService(KeyguardManager.class);
                if (!km.isDeviceSecure()) {
                    toast("Set a screen lock (PIN, pattern, or password) on this phone first, in Android Settings — otherwise there'd be nothing to confirm against.");
                    return;
                }
                Agent.prefs(this).edit().putBoolean("appLock", true).apply();
                Agent.addEvent(this, "local", "Master code on phone: turned on app lock");
                toast("App lock on. It applies the next time MDM Agent is opened.");
                build();
            });
        }

        LinearLayout net = Ui.card(this, root);
        net.addView(Ui.titleText(this, "Wi-Fi"));
        action(net, "Add a Wi-Fi network…", Ui.TONAL, v -> askWifi());

        LinearLayout device = Ui.card(this, root);
        device.addView(Ui.titleText(this, "Device"));
        action(device, "Reboot", Ui.TONAL, v -> confirm("Reboot the phone?", () -> run(() -> {
            Agent.dpm(this).reboot(Agent.admin(this));
            return "rebooting";
        })));
        action(device, "Stop managing (release)", Ui.OUTLINED, v -> confirm(
                "Release this phone? All restrictions are removed.", () -> release(false)));
        action(device, "Stop managing and remove this app", Ui.OUTLINED, v -> confirm(
                "Release this phone and uninstall the agent?", () -> release(true)));
        action(device, "Erase everything (factory reset)", Ui.DANGER, v -> promptWipe());
        refreshStatus();
    }

    private void refreshStatus() {
        long until = Agent.prefs(this).getLong("lockUntil", 0);
        status.setText("Screen lock: " + (Actions.hasScreenLock(this) ? "set" : "NOT set")
                + "\nPIN control: " + (Actions.pinControlActive(this) ? "ready" : "not active")
                + (until > System.currentTimeMillis() ? "\nTimed lock active" : "")
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

    private LinearLayout form(EditText... fields) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        for (EditText f : fields) Ui.add(box, f, 8);
        return box;
    }

    private void askLock() {
        final EditText message = Ui.field(this, "Message shown on the phone (optional)");
        final EditText minutes = Ui.field(this, "Minutes (0 = just lock the screen)");
        minutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this)
                .setTitle("Lock with a message")
                .setView(form(message, minutes))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Lock", (d, w) -> {
                    int m = 0;
                    try {
                        m = Math.min(480, Integer.parseInt(minutes.getText().toString().trim()));
                    } catch (NumberFormatException ignored) {
                    }
                    final int mins = m;
                    final String msg = message.getText().toString();
                    if (unlocked()) run(() -> Actions.lock(this, mins, msg));
                })
                .show();
    }

    private void askFreebrowse() {
        final EditText minutes = Ui.field(this, "Minutes");
        minutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        minutes.setText("60");
        new AlertDialog.Builder(this)
                .setTitle("Browse freely for a while")
                .setMessage("Opens any site for a chosen time. Every new site visited is then held for approval, just like a newly installed app.")
                .setView(minutes)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Start", (d, w) -> {
                    int m = 60;
                    try {
                        m = Math.max(5, Math.min(240, Integer.parseInt(minutes.getText().toString().trim())));
                    } catch (NumberFormatException ignored) {
                    }
                    final int mins = m;
                    if (unlocked()) {
                        PolicyApplier.startBrowseWindow(this, mins);
                        Agent.addEvent(this, "local", "Master code on phone: started free browsing for " + mins + " minutes");
                        toast("Browser can open any site for " + mins + " minutes.");
                    }
                })
                .show();
    }

    private void askWifi() {
        final EditText ssid = Ui.field(this, "Network name");
        final EditText pass = Ui.field(this, "Password (empty for an open network)");
        new AlertDialog.Builder(this)
                .setTitle("Add a Wi-Fi network")
                .setView(form(ssid, pass))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (d, w) -> {
                    final String s = ssid.getText().toString().trim();
                    final String p = pass.getText().toString();
                    if (s.isEmpty()) {
                        toast("Enter the network name.");
                    } else if (unlocked()) {
                        run(() -> Actions.addWifi(this, s, p));
                    }
                })
                .show();
    }

    private void askPin() {
        final EditText input = Ui.field(this, "New PIN (4 to 16 digits)");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("Set screen lock PIN")
                .setView(form(input))
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
        final EditText input = Ui.field(this, "Type ERASE to confirm");
        new AlertDialog.Builder(this)
                .setTitle("Erase everything?")
                .setMessage("This factory-resets the phone and cannot be undone.")
                .setView(form(input))
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
                LinearLayout row = Ui.card(this, appsBox);
                String state = a.optBoolean("h") ? "hidden" : "visible";
                if (overrides.has(pkg)) state += ", set here: " + overrides.optString(pkg);
                row.addView(Ui.titleText(this, a.optString("l", pkg)));
                row.addView(Ui.body(this, pkg + " · " + state, true));
                LinearLayout buttons = new LinearLayout(this);
                buttons.setOrientation(LinearLayout.HORIZONTAL);
                android.widget.Button allow = Ui.button(this, "Allow", Ui.TONAL, v -> {
                    if (unlocked()) setApp(pkg, "allow");
                });
                android.widget.Button block = Ui.button(this, "Block", Ui.DANGER, v -> {
                    if (unlocked()) setApp(pkg, "block");
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroupWrap(), 1f);
                lp.rightMargin = Ui.dp(this, 8);
                buttons.addView(allow, lp);
                buttons.addView(block, new LinearLayout.LayoutParams(0, ViewGroupWrap(), 1f));
                Ui.add(row, buttons, 8);
            }
        } catch (Exception e) {
            toast("Could not list apps: " + e.getMessage());
        }
    }

    private static int ViewGroupWrap() {
        return android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
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
