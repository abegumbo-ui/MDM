package com.familymdm.agent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import java.io.InputStream;

/**
 * Everything on the phone that doesn't need the master code -- installing an app with a one-time
 * code, and checking/applying app updates -- so whoever's holding the phone doesn't need to find
 * the administrator for either.
 */
public class UserActivity extends Activity {
    private static final int PICK_APK = 1;
    private static final int REQUEST_CODE_ENTRY = 2;
    private static final int REQUEST_STORE_CODE = 3;
    private static final long PICK_WINDOW_MS = 5 * 60 * 1000;

    private boolean showingUpdates;
    private boolean updatesChecking;
    private String updatesError;
    private java.util.List<PlayUpdates.UpdateInfo> updatesList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        build();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);
        Ui.add(root, Ui.banner(this, "User"), 0);

        if (showingUpdates) {
            buildUpdatesSection(root);
        } else {
            buildMenu(root);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    /** Whether the administrator left this button on (see the dashboard's "Phone menu" switches). Shown unless switched off. */
    private boolean menuOn(String key) {
        try {
            org.json.JSONObject menu = new org.json.JSONObject(Agent.prefs(this).getString("policy", "{}")).optJSONObject("userMenu");
            return menu == null || menu.optBoolean(key, true);
        } catch (Exception e) {
            return true;
        }
    }

    /** The list of things the person holding the phone can do or ask for, without any code. */
    private void buildMenu(LinearLayout root) {
        boolean online = !Agent.standalone(this) && Agent.prefs(this).getString("token", null) != null;
        int n = 0;
        if (online && menuOn("site")) {
            Ui.add(root, Ui.rowTile(this, "Request a site", R.drawable.ic_browser_tile, v -> promptSiteRequest()), n++ == 0 ? 16 : 8);
        }
        if (online && menuOn("app")) {
            Ui.add(root, Ui.rowTile(this, "Request a blocked app", R.drawable.ic_apps_tile, v -> promptAppRequest()), n++ == 0 ? 16 : 8);
        }
        if (menuOn("store")) {
            String label = "Open Google Play Store";
            if (StoreAccess.open(this)) {
                int left = StoreAccess.minutesLeft(this);
                label += left < 0 ? "\nopen for now" : "\nopen for " + left + " more min";
            }
            Ui.add(root, Ui.rowTile(this, label, R.drawable.ic_flash, v -> openStore(online)), n++ == 0 ? 16 : 8);
        }
        if (menuOn("message")) {
            Ui.add(root, Ui.rowTile(this, "Message the administrator", R.drawable.ic_message_tile, v -> promptMessage(online)), n++ == 0 ? 16 : 8);
        }
        if (menuOn("install")) {
            Ui.add(root, Ui.rowTile(this, "Install an app from a file", R.drawable.ic_device_tile, v -> promptInstallCode()), n++ == 0 ? 16 : 8);
        }
        if (menuOn("updates")) {
            Ui.add(root, Ui.rowTile(this, "App updates", R.drawable.ic_update_tile, v -> {
                showingUpdates = true;
                build();
            }), n++ == 0 ? 16 : 8);
        }
        if (online) {
            Ui.add(root, Ui.rowTile(this, "Refresh (check in now)", R.drawable.ic_wifi_tile, v -> {
                AgentService.requestSync();
                toast("Checking in...");
            }), n++ == 0 ? 16 : 8);
        }
        if (n == 0) {
            LinearLayout card = Ui.card(this, root);
            card.addView(Ui.body(this, "Nothing to do here right now.", true));
        }
    }

    private void promptSiteRequest() {
        final android.widget.EditText input = Ui.field(this, "Site, e.g. example.com");
        Ui.alertDialog(this)
                .setTitle("Request a site")
                .setMessage("Type the site you would like. The administrator sees your request and decides.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send", (d, w) -> {
                    String site = input.getText().toString().trim();
                    if (site.isEmpty() || site.contains(" ") || !site.contains(".")) {
                        toast("That does not look like a site address.");
                        return;
                    }
                    Agent.addSiteRequest(this, site.contains("://") ? site : "https://" + site);
                    AgentService.requestSync();
                    toast("Sent. You will be able to open it once it is approved.");
                })
                .show();
    }

    /** The apps on this phone that are blocked right now, with their names. */
    private void promptAppRequest() {
        final java.util.List<String> pkgs = new java.util.ArrayList<>();
        final java.util.List<String> labels = new java.util.ArrayList<>();
        android.content.pm.PackageManager pm = getPackageManager();
        for (String pkg : new java.util.TreeSet<>(Agent.getSet(this, "hidden"))) {
            String label = pkg;
            try {
                label = pm.getApplicationInfo(pkg, android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES).loadLabel(pm).toString();
            } catch (Exception ignored) {
            }
            if (label.equals(pkg) && !pkg.equals(StoreAccess.PKG)) continue; // a nameless system part: not something to ask for
            pkgs.add(pkg);
            labels.add(label);
        }
        if (pkgs.isEmpty()) {
            toast("No apps are blocked on this phone.");
            return;
        }
        Ui.alertDialog(this)
                .setTitle("Which app would you like?")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    Agent.addAppRequest(this, "app", pkgs.get(which), labels.get(which));
                    AgentService.requestSync();
                    toast("Sent. " + labels.get(which) + " will appear once it is approved.");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openStore(boolean online) {
        if (StoreAccess.open(this) || !StoreAccess.hidden(this)) {
            if (!StoreAccess.launch(this)) toast("The Play Store is not available on this phone.");
            return;
        }
        android.app.AlertDialog.Builder b = Ui.alertDialog(this)
                .setTitle("The Play Store is closed")
                .setMessage(online ? "Ask the administrator to open it for a while, or enter a code if you were given one." : "Enter the code to open it for a while.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("I have a code", (d, w) -> startActivityForResult(new Intent(this, CodeEntryActivity.class)
                        .putExtra(CodeEntryActivity.EXTRA_TITLE, "Open the Play Store")
                        .putExtra(CodeEntryActivity.EXTRA_SUBTITLE, "Enter the code you were given.")
                        .putExtra(CodeEntryActivity.EXTRA_BUTTON, "Open"), REQUEST_STORE_CODE));
        if (online) {
            b.setNeutralButton("Ask", (d, w) -> {
                Agent.addAppRequest(this, "store", null, null);
                AgentService.requestSync();
                toast("Sent. It opens once the administrator approves.");
            });
        }
        b.show();
    }

    private void promptMessage(final boolean online) {
        final android.widget.EditText input = Ui.field(this, "Message");
        Ui.alertDialog(this)
                .setTitle("Message the administrator")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send", (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) return;
                    AdminMessages.add(this, text, null); // kept on the phone for the Administrator screen
                    if (online) {
                        Agent.addMessage(this, text); // and sent to the dashboard
                        AgentService.requestSync();
                    }
                    toast(online ? "Sent." : "Saved for the administrator.");
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!showingUpdates) build(); // the Play Store row shows the time left
    }

    private void buildUpdatesSection(LinearLayout root) {
        Ui.add(root, Ui.rowTile(this, "Back", R.drawable.ic_apps_tile, v -> {
            showingUpdates = false;
            build();
        }), 0);

        LinearLayout card = Ui.card(this, root);
        card.addView(Ui.titleText(this, "Updates"));
        card.addView(Ui.body(this, "Checks only the apps already on this phone that aren't blocked, "
                + "plus whatever Waze, Google Maps, and Android Auto need to keep working -- never "
                + "the whole Play Store. Nothing else becomes installable.", true));
        if (updatesError != null) card.addView(Ui.body(this, updatesError, true));

        Ui.add(card, Ui.button(this, updatesChecking ? "Checking..." : "Check for updates", Ui.TONAL, v -> {
            if (updatesChecking) return;
            updatesChecking = true;
            updatesError = null;
            updatesList = null;
            build();
            java.util.List<String> eligible;
            try {
                eligible = PolicyApplier.eligiblePackagesForUpdates(this);
            } catch (Exception e) {
                eligible = PlayUpdates.CANDIDATE_PACKAGES;
            }
            PlayUpdates.checkForUpdates(this, eligible, (result, error) -> runOnUiThread(() -> {
                updatesChecking = false;
                updatesError = error;
                updatesList = result;
                build();
            }));
        }), 8);

        if (updatesList != null) {
            if (updatesList.isEmpty()) card.addView(Ui.body(this, "Everything checked is already up to date.", true));
            for (PlayUpdates.UpdateInfo u : updatesList) {
                final String pkg = u.getPkg();
                final String label = u.getLabel();
                Ui.add(root, Ui.rowTile(this, label + "\nbuild " + u.getInstalledVersion() + " -> " + u.getAvailableVersion(),
                        R.drawable.ic_update_tile, v -> {
                            toast("Updating " + label + "...");
                            PlayUpdates.updateOne(this, pkg, (msg, error) -> runOnUiThread(() -> {
                                if (error != null) {
                                    toast("Could not update " + label + ": " + error);
                                    return;
                                }
                                Agent.addEvent(this, "local", msg);
                                toast(msg);
                                updatesList = null;
                                build();
                            }));
                        }), 8);
            }
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private void promptInstallCode() {
        startActivityForResult(new Intent(this, CodeEntryActivity.class)
                .putExtra(CodeEntryActivity.EXTRA_TITLE, "Install an App")
                .putExtra(CodeEntryActivity.EXTRA_SUBTITLE, Agent.standalone(this)
                        ? "Enter the code, then pick the APK file from this phone."
                        : "Ask the administrator for a code to install this, then pick the APK file from this phone.")
                .putExtra(CodeEntryActivity.EXTRA_BUTTON, "Submit"), REQUEST_CODE_ENTRY);
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
        if (requestCode == REQUEST_STORE_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                String code = data.getStringExtra(CodeEntryActivity.EXTRA_CODE);
                CodeRedeem.redeemTimed(this, "playstore", code == null ? "" : code.trim(), (err, minutes) -> {
                    if (err != null) {
                        toast(err);
                        return;
                    }
                    final int mins = minutes;
                    new Thread(() -> {
                        StoreAccess.start(this, mins, "a code on the phone");
                        runOnUiThread(() -> {
                            toast("The Play Store is open for " + mins + " minutes.");
                            build();
                            StoreAccess.launch(this);
                        });
                    }).start();
                });
            }
            return;
        }
        if (requestCode == REQUEST_CODE_ENTRY) {
            if (resultCode == RESULT_OK && data != null) {
                String code = data.getStringExtra(CodeEntryActivity.EXTRA_CODE);
                CodeRedeem.redeem(this, "install", code == null ? "" : code.trim(), err -> {
                    if (err != null) {
                        toast(err);
                    } else {
                        Agent.prefs(this).edit().putLong("installUntil", System.currentTimeMillis() + PICK_WINDOW_MS).apply();
                        pickApk();
                    }
                });
            }
            return;
        }
        if (requestCode != PICK_APK || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (System.currentTimeMillis() > Agent.prefs(this).getLong("installUntil", 0)) {
            toast("The install code window has expired. Ask for a new code.");
            return;
        }
        final android.net.Uri uri = data.getData();
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
                if (err != null) toast("Install failed: " + err);
            });
        }).start();
    }
}
