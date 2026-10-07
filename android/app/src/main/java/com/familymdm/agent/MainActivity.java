package com.familymdm.agent;

import android.app.Activity;
import android.app.AlertDialog;
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

import java.text.DateFormat;
import java.util.Date;

/**
 * Status and enrollment screen. Actions are locked behind codes the administrator controls: one-time
 * codes made in the dashboard (install an APK, remove the agent) or the offline master code.
 */
public class MainActivity extends Activity {
    private static final int PICK_MESSAGE_PHOTO = 1;

    // Cleared the instant this screen loses focus (onPause), so leaving this app for any reason at
    // all -- the home button, switching apps, even a system picker launched from here -- demands
    // the PIN again on return. Deliberately strict: no grace period, no exemption for internal
    // navigation.
    private static boolean appLockPassed = false;
    // recreate() (used right after a successful unlock) triggers this same Activity's own onPause()
    // as part of tearing down the old instance -- without this, that onPause would immediately
    // reset appLockPassed back to false before the new instance ever got to check it, undoing the
    // unlock that was just entered.
    private static boolean suppressNextLockReset = false;
    private boolean locked;

    private TextView status;
    private LinearLayout logoHolder;
    private LinearLayout statusCard;
    private TextView updateText;
    private LinearLayout ownerBox;
    private LinearLayout modeBox;
    private LinearLayout switchBox;
    private boolean showEnroll;
    private boolean reshowFrp;
    private LinearLayout enrollBox;
    private LinearLayout actionsBox;
    private EditText serverField;
    private EditText codeField;
    private android.widget.Button enrollButton;
    private android.widget.Button updateButton;
    private android.widget.Button refreshButton;
    private final android.os.Handler refreshHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkAppLock();
        if (locked) return;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);

        Ui.add(root, Ui.banner(this, "MDM"), 0);
        logoHolder = new LinearLayout(this);
        logoHolder.setOrientation(LinearLayout.VERTICAL);
        Ui.add(root, logoHolder, 12);

        statusCard = Ui.card(this, root);
        status = Ui.body(this, "", false);
        statusCard.addView(status);
        updateText = Ui.body(this, "", true);
        Ui.add(statusCard, updateText, 6);
        refreshButton = Ui.button(this, "Refresh", Ui.OUTLINED, v -> {
            AgentService.requestSync();
            toast("Checking in with the dashboard…");
            refreshHandler.postDelayed(this::refresh, 2500);
        });
        Ui.add(statusCard, refreshButton, 8);
        updateButton = Ui.button(this, "Check for an update", Ui.OUTLINED, v -> checkUpdate(true));
        Ui.add(statusCard, updateButton, 8);

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

        android.widget.GridLayout grid = Ui.tileGrid(this);
        Ui.addTile(grid, Ui.tile(this, "User", R.drawable.ic_person, 96,
                v -> startActivity(new Intent(this, UserActivity.class))));
        Ui.addTile(grid, Ui.tile(this, "Administrator", R.drawable.ic_shield, 96, v -> promptMaster()));
        Ui.addTile(grid, Ui.tile(this, "Message the\nAdministrator", R.drawable.ic_message_tile, 96, v -> promptMessage()));
        Ui.addTile(grid, Ui.tile(this, "Remove This\nAgent", R.drawable.ic_remove_tile, 96, v -> promptCode("uninstall")));
        Ui.add(actionsBox, grid, 16);

        switchBox = Ui.card(this, actionsBox);
        switchBox.addView(Ui.titleText(this, "Dashboard"));
        switchBox.addView(Ui.body(this, "Want to control this phone from a dashboard website instead?", true));
        Ui.add(switchBox, Ui.button(this, "Connect to a dashboard", Ui.OUTLINED, v -> {
            showEnroll = true;
            refresh();
        }), 12);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        String server = getIntent().getStringExtra("server");
        String code = getIntent().getStringExtra("code");
        if (server != null) serverField.setText(server);
        if (code != null) codeField.setText(code);
        if (server != null && code != null && !Agent.enrolled(this)) enroll();
        askBatteryExemptionOnce();
    }

    /**
     * Without this, Android's own battery management can throttle the background sync loop to
     * minutes instead of ~15 seconds once the screen's been off a while -- the "Allow background
     * activity" button lower on this screen does the same thing, but only if someone finds it and
     * taps it themselves. This asks once, automatically, the first time the app is opened (the
     * system still shows its own one-tap confirmation; that part can't be skipped).
     */
    private void askBatteryExemptionOnce() {
        if (Agent.prefs(this).getBoolean("askedBattery", false)) return;
        Agent.prefs(this).edit().putBoolean("askedBattery", true).apply();
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAppLock();
        if (locked) return;
        refresh();
        Agent.startServiceIfEnrolled(this);
        if (Agent.enrolled(this) || Agent.standalone(this)) checkUpdate(false);
        if (reshowFrp) {
            reshowFrp = false;
            askFrp();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (suppressNextLockReset) {
            suppressNextLockReset = false;
        } else {
            appLockPassed = false;
        }
    }

    /** Shows the lock screen if app lock is on and hasn't been passed since the last time this screen lost focus. */
    private void checkAppLock() {
        if (!locked && Agent.prefs(this).getBoolean("appLock", false) && !appLockPassed) {
            showLockScreen();
        }
    }

    // Held across the activity-result round trip to the photo picker, so the typed message isn't
    // lost while that's open.
    private String pendingMessageText;

    private void promptMessage() {
        final EditText input = Ui.field(this, "Message");
        new AlertDialog.Builder(this)
                .setTitle("Message the Administrator")
                .setMessage("Saved on this phone for the administrator to read -- not sent anywhere online.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Attach a photo", (d, w) -> {
                    pendingMessageText = input.getText().toString().trim();
                    pickMessagePhoto();
                })
                .setPositiveButton("Send", (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) return;
                    AdminMessages.add(this, text, null);
                    toast("Saved for the administrator.");
                })
                .show();
    }

    private void pickMessagePhoto() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        try {
            startActivityForResult(i, PICK_MESSAGE_PHOTO);
        } catch (Exception e) {
            toast("No photo picker available on this phone.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_MESSAGE_PHOTO || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final String text = pendingMessageText == null ? "" : pendingMessageText;
        pendingMessageText = null;
        try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
            if (in == null) throw new Exception("could not open the photo");
            final String path = AdminMessages.savePhoto(this, in);
            new AlertDialog.Builder(this)
                    .setTitle("Send with photo?")
                    .setMessage((text.isEmpty() ? "(no text)" : text) + "\n\nA photo is attached. Saved on this phone for the administrator to read -- not sent anywhere online.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Send", (d, w) -> {
                        AdminMessages.add(this, text, path);
                        toast("Saved for the administrator.");
                    })
                    .show();
        } catch (Exception e) {
            toast("Could not attach the photo: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
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
        Ui.add(box, Ui.body(this, "Enter the app PIN to open MDM Agent.", true), 8);
        Ui.add(box, Ui.button(this, "Unlock", Ui.FILLED, v -> promptAppPin()), 20);
        Ui.add(box, Ui.button(this, "Forgot it? Use the Administrator code", Ui.OUTLINED, v -> promptMasterBypass()), 8);
        setContentView(box);
        promptAppPin();
    }

    private void promptAppPin() {
        final EditText input = Ui.field(this, "App PIN");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("Unlock MDM Agent")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Unlock", (d, w) -> {
                    String err = AppPin.check(this, input.getText().toString());
                    if (err != null) {
                        toast(err);
                    } else {
                        appLockPassed = true;
                        suppressNextLockReset = true;
                        recreate();
                    }
                })
                .show();
    }

    private void promptMasterBypass() {
        final EditText input = Ui.field(this, "Administrator (master) code");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("Administrator")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Unlock", (d, w) -> {
                    final String code = input.getText().toString();
                    new Thread(() -> {
                        final String err = Master.check(this, code);
                        runOnUiThread(() -> {
                            if (err != null) {
                                toast(err);
                            } else {
                                Agent.addEvent(this, "local", "Administrator code used to bypass the app PIN");
                                appLockPassed = true;
                                suppressNextLockReset = true;
                                recreate();
                            }
                        });
                    }).start();
                })
                .show();
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
        refreshButton.setVisibility(enrolled ? View.VISIBLE : View.GONE);
        logoHolder.removeAllViews();
        android.widget.ImageView logo = Ui.logoView(this);
        if (logo != null) Ui.add(logoHolder, logo, 8);

        ownerBox.setVisibility(pastStep1 ? View.GONE : View.VISIBLE);
        modeBox.setVisibility(!active && pastStep1 && !showEnroll ? View.VISIBLE : View.GONE);
        enrollBox.setVisibility(!enrolled && showEnroll ? View.VISIBLE : View.GONE);
        actionsBox.setVisibility(active ? View.VISIBLE : View.GONE);
        switchBox.setVisibility(standalone && !showEnroll ? View.VISIBLE : View.GONE);
        updateText.setText("This agent is build " + Updater.currentBuild(this) + ".");
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

    // A fresh install/update check on literally every onResume would hit GitHub or the dashboard
    // far more than needed -- once every 10 minutes in the foreground is still effectively
    // "automatic" from the person's point of view, without hammering the update endpoint.
    private static final long AUTO_UPDATE_CHECK_MS = 10 * 60 * 1000;

    /** Checks for a newer build and installs it immediately if one exists -- a single step, not two. */
    private void checkUpdate(final boolean manual) {
        if (!manual && System.currentTimeMillis() - Agent.prefs(this).getLong("lastForegroundUpdateCheck", 0) < AUTO_UPDATE_CHECK_MS) {
            return;
        }
        Agent.prefs(this).edit().putLong("lastForegroundUpdateCheck", System.currentTimeMillis()).apply();
        if (manual) {
            toast("Checking for an update...");
            updateButton.setEnabled(false);
        }
        new Thread(() -> {
            String msg;
            try {
                msg = Updater.update(this, false);
            } catch (Exception e) {
                msg = "Update check failed: " + (e.getMessage() == null ? e.toString() : e.getMessage());
            }
            final String text = msg;
            runOnUiThread(() -> {
                updateText.setText(text);
                if (manual) {
                    toast(text);
                    updateButton.setEnabled(true);
                }
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
                .setMessage("Ask the administrator for a code to remove this.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("OK", (d, w) -> CodeRedeem.redeem(this, type, input.getText().toString().trim(), err -> {
                    if (err != null) toast(err); else removeAgent();
                }))
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
