package com.familymdm.agent;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Intent;
import android.net.Uri;
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
    private static final int CONFIRM_APP_LOCK = 2;
    private static final long PICK_WINDOW_MS = 5 * 60 * 1000;
    // Survives Activity re-creation within the same process, but not a fresh launch (process restart,
    // reboot, the app swiped away) — so the app re-locks whenever it was actually closed, without
    // nagging for every internal navigation (e.g. opening the file picker and coming back).
    private static boolean appLockPassed = false;
    private boolean locked;

    private TextView status;
    private LinearLayout logoHolder;
    private LinearLayout statusCard;
    private TextView updateText;
    private LinearLayout ownerBox;
    private LinearLayout modeBox;
    private LinearLayout switchBox;
    private TextView installHint;
    private TextView removeHint;
    private boolean showEnroll;
    private boolean reshowFrp;
    private LinearLayout enrollBox;
    private LinearLayout actionsBox;
    private EditText serverField;
    private EditText codeField;
    private android.widget.Button enrollButton;
    private android.widget.Button pickButton;
    private android.widget.Button updateButton;
    private boolean updateAvailable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Agent.prefs(this).getBoolean("appLock", false) && !appLockPassed) {
            showLockScreen();
            return;
        }
        locked = false;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);

        root.addView(Ui.headline(this, "MDM Agent"));
        logoHolder = new LinearLayout(this);
        logoHolder.setOrientation(LinearLayout.VERTICAL);
        root.addView(logoHolder);

        statusCard = Ui.card(this, root);
        status = Ui.body(this, "", false);
        statusCard.addView(status);

        // ---- step 1: device owner (shown until the adb command has been run) ----
        ownerBox = Ui.card(this, root);
        ownerBox.addView(Ui.titleText(this, "Step 1: make this app the device owner"));
        ownerBox.addView(Ui.body(this, "Connect the phone to a computer with USB debugging on and no accounts on the phone, then run this on the computer:", true));
        TextView adbCommand = Ui.body(this, "adb shell dpm set-device-owner com.familymdm.agent/.AdminReceiver", false);
        adbCommand.setTextIsSelectable(true);
        adbCommand.setTypeface(android.graphics.Typeface.MONOSPACE);
        adbCommand.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        Ui.add(ownerBox, adbCommand, 8);
        ownerBox.addView(Ui.body(this, "It should print \"Success\". This screen updates by itself.", true));
        Ui.add(ownerBox, Ui.button(this, "Skip for now (preview only)", Ui.OUTLINED, v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Skip step 1?")
                    .setMessage("Only for trying out the screens on a phone you're not setting up for real. Without device owner, nothing is actually hidden, locked, or restricted — every switch here will look like it works but won't enforce anything. Do step 1 for real use.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Skip", (d, w) -> {
                        Agent.prefs(this).edit().putBoolean("previewSkip", true).apply();
                        refresh();
                    })
                    .show();
        }), 12);

        // ---- step 2: online or offline ----
        modeBox = Ui.card(this, root);
        modeBox.addView(Ui.titleText(this, "How do you want to use this phone?"));
        modeBox.addView(Ui.body(this, "Online: control it from a dashboard website. Offline: set everything up on this phone only, with no server.", true));
        Ui.add(modeBox, Ui.button(this, "Connect to a dashboard (online)", Ui.FILLED, v -> {
            showEnroll = true;
            refresh();
        }), 12);
        Ui.add(modeBox, Ui.button(this, "Use it on its own (offline)", Ui.TONAL, v -> startStandaloneSetup()), 8);

        // ---- enrollment ----
        enrollBox = Ui.card(this, root);
        enrollBox.addView(Ui.titleText(this, "Connect to the dashboard"));
        serverField = Ui.field(this, "Dashboard address (https://...)");
        Ui.add(enrollBox, serverField, 12);
        String defaultServer = getString(R.string.default_server);
        if (!defaultServer.isEmpty()) {
            serverField.setText(defaultServer);
            serverField.setVisibility(View.GONE);
            Ui.add(enrollBox, Ui.button(this, "Use a different dashboard", Ui.OUTLINED,
                    v -> serverField.setVisibility(View.VISIBLE)), 8);
        }
        codeField = Ui.field(this, "Enrollment code");
        Ui.add(enrollBox, codeField, 8);
        enrollButton = Ui.button(this, "Enroll", Ui.FILLED, v -> enroll());
        Ui.add(enrollBox, enrollButton, 12);

        // ---- code-protected actions (shown once enrolled) ----
        actionsBox = new LinearLayout(this);
        actionsBox.setOrientation(LinearLayout.VERTICAL);
        Ui.add(root, actionsBox, 0);

        LinearLayout upd = Ui.card(this, actionsBox);
        upd.addView(Ui.titleText(this, "Update"));
        updateText = Ui.body(this, "", true);
        upd.addView(updateText);
        updateButton = Ui.button(this, "Check for an update", Ui.TONAL, v -> checkUpdate(updateAvailable));
        Ui.add(upd, updateButton, 12);

        LinearLayout install = Ui.card(this, actionsBox);
        install.addView(Ui.titleText(this, "Install an app"));
        installHint = Ui.body(this, "", true);
        install.addView(installHint);
        Ui.add(install, Ui.button(this, "Install an app (needs code)", Ui.FILLED, v -> promptCode("install")), 12);
        pickButton = Ui.button(this, "Choose APK file", Ui.TONAL, v -> pickApk());
        Ui.add(install, pickButton, 8);

        LinearLayout admin = Ui.card(this, actionsBox);
        admin.addView(Ui.titleText(this, "Administrator"));
        admin.addView(Ui.body(this, "Everything the dashboard can do, here on the phone, even without internet.", true));
        Ui.add(admin, Ui.button(this, "Administrator (code)", Ui.TONAL, v -> promptMaster()), 12);

        LinearLayout msg = Ui.card(this, actionsBox);
        msg.addView(Ui.titleText(this, "Message the administrator"));
        msg.addView(Ui.body(this, "A bug, a question, anything — no code needed.", true));
        Ui.add(msg, Ui.button(this, "Send a message", Ui.OUTLINED, v -> promptMessage()), 12);

        LinearLayout remove = Ui.card(this, actionsBox);
        remove.addView(Ui.titleText(this, "Remove this agent"));
        removeHint = Ui.body(this, "", true);
        remove.addView(removeHint);
        Ui.add(remove, Ui.button(this, "Remove agent (needs code)", Ui.DANGER, v -> promptCode("uninstall")), 12);

        switchBox = Ui.card(this, actionsBox);
        switchBox.addView(Ui.titleText(this, "Dashboard"));
        switchBox.addView(Ui.body(this, "Want to control this phone from a dashboard website instead?", true));
        Ui.add(switchBox, Ui.button(this, "Connect to a dashboard", Ui.OUTLINED, v -> {
            showEnroll = true;
            refresh();
        }), 12);

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
        if (locked) return;
        refresh();
        Agent.startServiceIfEnrolled(this);
        if (reshowFrp) {
            reshowFrp = false;
            askFrp();
        }
    }

    private void promptMessage() {
        final EditText input = Ui.field(this, "Message");
        new AlertDialog.Builder(this)
                .setTitle("Message the administrator")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send", (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) return;
                    if (Agent.standalone(this)) {
                        Agent.addEvent(this, "local", "Message: " + text);
                        toast("No dashboard is connected offline, so this was only saved to the phone log.");
                    } else {
                        Agent.addMessage(this, text);
                        AgentService.requestSync();
                        toast("Sent.");
                    }
                })
                .show();
    }

    // ---------- app lock: fingerprint, face, or the phone's own PIN/pattern/password ----------
    private void showLockScreen() {
        locked = true;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER);
        int pad = Ui.dp(this, 24);
        box.setPadding(pad, pad, pad, pad);
        box.addView(Ui.headline(this, "Locked"));
        Ui.add(box, Ui.body(this, "Unlock with your fingerprint, face, or device PIN to open MDM Agent.", true), 8);
        Ui.add(box, Ui.button(this, "Unlock", Ui.FILLED, v -> requestUnlock()), 20);
        setContentView(box);
        requestUnlock();
    }

    private void requestUnlock() {
        KeyguardManager km = getSystemService(KeyguardManager.class);
        Intent i = km.createConfirmDeviceCredentialIntent("Unlock MDM Agent", null);
        if (i == null) {
            // The phone has no lock screen set up at all (so nothing to confirm against): don't
            // permanently lock the admin out of their own app over a setting that got turned off.
            appLockPassed = true;
            recreate();
            return;
        }
        startActivityForResult(i, CONFIRM_APP_LOCK);
    }

    private void refresh() {
        boolean owner = Agent.isOwner(this);
        boolean previewSkip = !owner && Agent.prefs(this).getBoolean("previewSkip", false);
        boolean pastStep1 = owner || previewSkip;
        boolean enrolled = Agent.enrolled(this);
        boolean standalone = Agent.standalone(this);
        boolean active = enrolled || standalone;
        StringBuilder sb = new StringBuilder();
        sb.append(owner ? "Device owner: yes\n" : previewSkip ? "Device owner: NO — previewing without it; nothing is actually enforced\n" : "Device owner: NO (do step 1 below)\n");
        if (enrolled) {
            long last = Agent.prefs(this).getLong("lastSync", 0);
            sb.append("Mode: online (dashboard)\nServer: ").append(Agent.prefs(this).getString("server", ""));
            sb.append("\nLast check-in: ").append(last == 0 ? "not yet"
                    : DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(last)));
        } else if (standalone) {
            sb.append("Mode: offline (this phone only)");
        } else {
            sb.append("Mode: not set up yet");
        }
        sb.append("\nAgent build: ").append(Updater.currentBuild(this));
        status.setText(sb.toString());
        logoHolder.removeAllViews();
        android.widget.ImageView logo = Ui.logoView(this);
        if (logo != null) Ui.add(logoHolder, logo, 8);

        ownerBox.setVisibility(pastStep1 ? View.GONE : View.VISIBLE);
        modeBox.setVisibility(!active && pastStep1 && !showEnroll ? View.VISIBLE : View.GONE);
        enrollBox.setVisibility(!enrolled && showEnroll ? View.VISIBLE : View.GONE);
        actionsBox.setVisibility(active ? View.VISIBLE : View.GONE);
        switchBox.setVisibility(standalone && !showEnroll ? View.VISIBLE : View.GONE);
        installHint.setText(standalone
                ? "Enter the code, then pick the APK file from this phone."
                : "Ask the administrator for a code to install this, then pick the APK file from this phone.");
        removeHint.setText(standalone
                ? "Enter the code. This ends all management of the phone."
                : "Ask the administrator for a code to remove this. This ends all management of the phone.");
        updateText.setText("This agent is build " + Updater.currentBuild(this) + ".");
        boolean canPick = System.currentTimeMillis() < Agent.prefs(this).getLong("installUntil", 0);
        pickButton.setVisibility(canPick ? View.VISIBLE : View.GONE);
    }

    // ---------- offline mode setup ----------
    private void startStandaloneSetup() {
        if (!Agent.isOwner(this) && !Agent.prefs(this).getBoolean("previewSkip", false)) {
            toast("First make this app the device owner (step 1).");
            return;
        }
        final EditText one = Ui.field(this, "Master code (6 or more characters)");
        final EditText two = Ui.field(this, "Repeat it");
        one.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        two.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        Ui.add(box, one, 8);
        Ui.add(box, two, 8);
        new AlertDialog.Builder(this)
                .setTitle("Choose a master code")
                .setMessage("This code opens every setting and every code prompt on this phone, even with no internet. "
                        + "There is no dashboard to reset it, so write it down. If you lose it while reset protection is on, wiping the phone will not get you out either.")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    final String a = one.getText().toString();
                    if (a.length() < 6 || !a.equals(two.getText().toString())) {
                        toast("Use at least 6 characters, typed the same twice.");
                        return;
                    }
                    new Thread(() -> {
                        String error = null;
                        try {
                            Master.setLocal(this, a);
                            LocalConfig.save(this, LocalConfig.load(this));
                            Agent.prefs(this).edit().putBoolean("standalone", true).apply();
                            Agent.startServiceIfEnrolled(this);
                        } catch (Exception e) {
                            error = e.getMessage();
                        }
                        final String err = error;
                        runOnUiThread(() -> {
                            refresh();
                            if (err != null) toast("Could not set up: " + err);
                            else askFrp();
                        });
                    }).start();
                })
                .show();
    }

    /** Step 3 of offline setup: the Google account that may set the phone up again after a reset. */
    private void askFrp() {
        final EditText id = Ui.field(this, "Google account ID (about 21 digits)");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        Ui.add(box, id, 8);
        new AlertDialog.Builder(this)
                .setTitle("Factory Reset Protection")
                .setMessage("After a factory reset from recovery mode, setup will demand this Google account, so the phone is useless to anyone else. "
                        + "No account has to be signed in on the phone (Android 11 or newer; keep the bootloader locked).\n\n"
                        + "To get the ID: open the Google People API page, tap \"Try it\", set resourceName to people/me and personFields to metadata, "
                        + "tap Execute, sign in with the account YOU control, and copy the long number next to \"id\".\n\n"
                        + "You can also do this later in Phone settings.")
                .setView(box)
                .setNegativeButton("Skip for now", (d, w) -> openLocalSettings())
                .setNeutralButton("Open People API page", (d, w) -> {
                    reshowFrp = true;
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://developers.google.com/people/api/rest/v1/people/get")));
                    } catch (Exception e) {
                        reshowFrp = false;
                        toast("No browser available. Open developers.google.com/people/api/rest/v1/people/get on another device.");
                        askFrp();
                    }
                })
                .setPositiveButton("Save", (d, w) -> {
                    String text = id.getText().toString().trim();
                    org.json.JSONArray entered = new org.json.JSONArray();
                    for (String part : text.split("[ ,\\n]+")) if (!part.isEmpty()) entered.put(part);
                    java.util.List<String> clean = LocalPolicy.normalizeAccounts(entered);
                    if (entered.length() > 0 && clean.size() != entered.length()) {
                        toast("That is not a Google account ID. It is a number of about 21 digits, not an email address.");
                        askFrp();
                        return;
                    }
                    try {
                        JSONObject cfg = LocalConfig.load(this);
                        cfg.put("frpAccounts", new org.json.JSONArray(clean));
                        LocalConfig.save(this, cfg);
                    } catch (Exception ignored) {
                    }
                    new Thread(() -> {
                        try {
                            LocalConfig.refresh(this);
                        } catch (Exception ignored) {
                        }
                    }).start();
                    openLocalSettings();
                })
                .show();
    }

    private void openLocalSettings() {
        Agent.prefs(this).edit().putLong("adminUntil", System.currentTimeMillis() + 10 * 60 * 1000).apply();
        startActivity(new Intent(this, LocalSettingsActivity.class));
    }

    /** One button: first tap checks, and if a newer build exists it becomes "Update now" for the next tap. */
    private void checkUpdate(final boolean install) {
        toast(install ? "Updating..." : "Checking...");
        updateButton.setEnabled(false);
        new Thread(() -> {
            String msg;
            boolean available = false;
            try {
                if (install) {
                    msg = Updater.update(this, false);
                } else {
                    JSONObject latest = Updater.latest(this);
                    if (latest == null) {
                        msg = "Could not find the latest build.";
                    } else if (latest.getInt("versionCode") > Updater.currentBuild(this)) {
                        msg = "Build " + latest.getInt("versionCode") + " is available. This agent is build " + Updater.currentBuild(this) + ".";
                        available = true;
                    } else {
                        msg = "Up to date (build " + Updater.currentBuild(this) + ").";
                    }
                }
            } catch (Exception e) {
                msg = "Update failed: " + (e.getMessage() == null ? e.toString() : e.getMessage());
            }
            final String text = msg;
            final boolean avail = install ? false : available;
            runOnUiThread(() -> {
                toast(text);
                updateText.setText(text);
                updateAvailable = avail;
                updateButton.setText(avail ? "Update now" : "Check for an update");
                updateButton.setEnabled(true);
            });
        }).start();
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
                Agent.enrollWith(this, server, code);
                showEnroll = false;
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
        final EditText input = Ui.field(this, "Code");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("Enter code")
                .setMessage("Ask the administrator for a code to " + (type.equals("install") ? "install this." : type.equals("freebrowse") ? "browse freely for a while." : "remove this."))
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("OK", (d, w) -> redeem(type, input.getText().toString().trim()))
                .show();
    }

    private void promptMaster() {
        final EditText input = Ui.field(this, "Code");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("Enter code")
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
        final boolean standalone = Agent.standalone(this);
        if (code.isEmpty() || (!standalone && (server == null || token == null))) return;
        final int[] minutesHolder = {60}; // default when the master code is used instead of a server code
        new Thread(() -> {
            android.content.SharedPreferences p = Agent.prefs(this);
            long now = System.currentTimeMillis();
            long lockedUntil = p.getLong("redeemLockUntil", 0);
            String error = null;
            if (now < lockedUntil) {
                error = "Too many wrong tries. Try again in " + ((lockedUntil - now) / 60000 + 1) + " min.";
            } else if (Master.matches(this, code)) {
                // The master code works in place of any one-time code, with no internet needed.
                Agent.addEvent(this, "local", "Master code used instead of a one-time " + type + " code");
                p.edit().putInt("redeemFails", 0).apply();
            } else if (standalone) {
                error = failedTry(p, now, "Wrong code.");
            } else {
                try {
                    JSONObject body = new JSONObject();
                    body.put("type", type);
                    body.put("code", code);
                    JSONObject reply = Api.post(server + "/agent/redeem", body, token);
                    if (type.equals("freebrowse")) minutesHolder[0] = reply.optInt("minutes", 60);
                    p.edit().putInt("redeemFails", 0).apply();
                } catch (Exception e) {
                    if (e instanceof Api.HttpException && ((Api.HttpException) e).code == 403) {
                        error = failedTry(p, now, "That code is wrong, already used, or expired.");
                    } else {
                        error = "Could not check the code: " + e.getMessage();
                    }
                }
            }
            final String err = error;
            runOnUiThread(() -> {
                if (err != null) {
                    toast(err);
                } else if (type.equals("install")) {
                    Agent.prefs(this).edit().putLong("installUntil", System.currentTimeMillis() + PICK_WINDOW_MS).apply();
                    refresh();
                    pickApk();
                } else if (type.equals("freebrowse")) {
                    PolicyApplier.startBrowseWindow(this, minutesHolder[0]);
                    refresh();
                    toast("Browser can open any site for " + minutesHolder[0] + " minutes.");
                } else {
                    removeAgent();
                }
            });
        }).start();
    }

    /** Counts a wrong code; five wrong tries lock code entry for five minutes. */
    private String failedTry(android.content.SharedPreferences p, long now, String message) {
        int fails = p.getInt("redeemFails", 0) + 1;
        if (fails >= 5) {
            p.edit().putInt("redeemFails", 0).putLong("redeemLockUntil", now + 5 * 60 * 1000).apply();
            Agent.addEvent(this, "security", "Code entry locked for 5 minutes after 5 wrong tries");
            return "Too many wrong tries. Locked for 5 minutes.";
        }
        p.edit().putInt("redeemFails", fails).apply();
        return message;
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
        if (requestCode == CONFIRM_APP_LOCK) {
            if (resultCode == RESULT_OK) {
                appLockPassed = true;
                recreate();
            } else if (!isFinishing()) {
                finish(); // cancelled or backed out: don't leave a half-built locked screen sitting open
            }
            return;
        }
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
