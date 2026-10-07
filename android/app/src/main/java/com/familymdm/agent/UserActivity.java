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
 * Everything on the phone that doesn't need the master code -- right now just installing an app
 * with a one-time code, the same flow MainActivity used to host directly.
 */
public class UserActivity extends Activity {
    private static final int PICK_APK = 1;
    private static final int REQUEST_CODE_ENTRY = 2;
    private static final long PICK_WINDOW_MS = 5 * 60 * 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);
        Ui.add(root, Ui.banner(this, "User"), 0);

        GridLayout grid = Ui.tileGrid(this);
        Ui.addTile(grid, Ui.tile(this, "Install\nan App", R.drawable.ic_apps_tile, 96, v -> promptInstallCode()));
        Ui.add(root, grid, 16);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
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
