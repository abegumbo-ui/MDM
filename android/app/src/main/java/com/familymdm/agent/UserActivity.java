package com.familymdm.agent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;

/**
 * Everything on the phone that doesn't need the master code -- installing an app with a one-time
 * code, and checking/applying app updates -- so whoever's holding the phone doesn't need to find
 * the administrator for either.
 */
public class UserActivity extends Activity {
    private static final int PICK_APK = 1;
    private static final int REQUEST_CODE_ENTRY = 2;
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
            GridLayout grid = Ui.tileGrid(this);
            Ui.addTile(grid, Ui.tile(this, "Install\nan App", R.drawable.ic_apps_tile, 96, v -> promptInstallCode()));
            Ui.addTile(grid, Ui.tile(this, "Updates", R.drawable.ic_update_tile, 96, v -> {
                showingUpdates = true;
                build();
            }));
            Ui.add(root, grid, 16);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
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
                eligible = eligiblePackagesForUpdates();
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

    /** Same eligibility AdminActivity's own Updates section uses: installed, not hard-blocked/hidden,
     * and not soft-blocked, plus PlayUpdates.CANDIDATE_PACKAGES unconditionally since those (Play
     * services, the Play Store app, Android Auto) are usually unmanaged, not explicitly "allowed". */
    private java.util.List<String> eligiblePackagesForUpdates() throws JSONException {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>(PlayUpdates.CANDIDATE_PACKAGES);
        JSONObject overrides = Agent.getOverrides(this);
        java.util.Set<String> hardBlocked = WholeAppBlocklist.list(this);
        JSONArray packages = PolicyApplier.collectPackages(this);
        for (int i = 0; i < packages.length(); i++) {
            JSONObject a = packages.optJSONObject(i);
            if (a == null) continue;
            String pkg = a.optString("p", "");
            if (pkg.isEmpty() || hardBlocked.contains(pkg) || a.optBoolean("h")) continue;
            if ("block".equals(overrides.optString(pkg, ""))) continue;
            set.add(pkg);
        }
        return new java.util.ArrayList<>(set);
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
