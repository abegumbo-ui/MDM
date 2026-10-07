package com.familymdm.lockdown;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * One-time, one-way setup. Unlike the MDM Agent, this never talks to a server and keeps nothing
 * running afterward -- it asks what to lock down, applies it once, hides itself forever, and the
 * only way back is a factory reset. See the restriction/app choices below; nothing here is
 * reversible from inside the app once "Close Forever" is confirmed.
 */
public class MainActivity extends Activity {
    private LinearLayout root;
    private ScrollView scroll;

    // In-memory choices for this setup session -- persisted to prefs on every change, so leaving
    // and coming back (or a rotation) doesn't lose them before the final, irreversible step.
    private Set<String> selectedRestrictions;
    private Set<String> selectedDeletes;
    private boolean showingAllRestrictions;

    private SharedPreferences prefs() {
        return getSharedPreferences("lockdown", MODE_PRIVATE);
    }

    private DevicePolicyManager dpm() {
        return (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
    }

    private ComponentName admin() {
        return new ComponentName(this, AdminReceiver.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Defensive only -- by the time this is true, MainActivity's own launcher component is
        // already disabled, so Android shouldn't be able to start this at all.
        if (prefs().getBoolean("lockedForever", false)) {
            finish();
            return;
        }

        selectedRestrictions = new HashSet<>(prefs().getStringSet("selectedRestrictions", new HashSet<>()));
        if (selectedRestrictions.isEmpty() && !prefs().contains("selectedRestrictions")) {
            for (String[] r : LockdownPolicy.CURATED_RESTRICTIONS) if (r[2].equals("1")) selectedRestrictions.add(r[0]);
        }
        selectedDeletes = new HashSet<>(prefs().getStringSet("selectedDeletes", new HashSet<>()));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);
        scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        build();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private void build() {
        root.removeAllViews();
        Ui.add(root, Ui.banner(this, "Lockdown Setup"), 0);

        if (!dpm().isDeviceOwnerApp(getPackageName())) {
            buildOwnerStep();
            return;
        }
        if (!prefs().contains("frpAccountId")) {
            buildFrpStep();
            return;
        }
        buildWizard();
    }

    // ---------- step 1: device owner ----------
    private void buildOwnerStep() {
        LinearLayout card = Ui.card(this, root);
        card.addView(Ui.titleText(this, "Step 1: make this app the device owner"));
        card.addView(Ui.body(this, "Connect the phone to a computer with USB debugging on and no accounts on the phone, then run this on the computer:", true));
        TextView adbCommand = Ui.body(this, "adb shell dpm set-device-owner com.familymdm.lockdown/.AdminReceiver", false);
        adbCommand.setTextIsSelectable(true);
        adbCommand.setTypeface(android.graphics.Typeface.MONOSPACE);
        adbCommand.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        Ui.add(card, adbCommand, 8);
        card.addView(Ui.body(this, "It should print \"Success\". This screen updates by itself.", true));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (root != null) build();
    }

    // ---------- step 2: Factory Reset Protection account ----------
    private void buildFrpStep() {
        LinearLayout card = Ui.card(this, root);
        card.addView(Ui.titleText(this, "Factory Reset Protection"));
        card.addView(Ui.body(this, "The Google account that can set this phone up again after a factory reset -- "
                + "once this is locked forever, that reset is the only way to undo anything, so this account "
                + "matters. No account has to be signed in on the phone (Android 11 or newer; keep the bootloader "
                + "locked).", true));
        final EditText id = Ui.field(this, "Google account ID (about 21 digits)");
        Ui.add(card, id, 12);
        TextView how = Ui.body(this,
                "To get the ID: open the Google People API page, tap \"Try it\", set resourceName to people/me "
                        + "and personFields to metadata, tap Execute, sign in with the account YOU control, and "
                        + "copy the long number next to \"id\".", true);
        Ui.add(card, how, 8);
        Ui.add(card, Ui.button(this, "Open the Google People API page", Ui.OUTLINED, v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://developers.google.com/people/api/rest/v1/people/get")));
            } catch (Exception e) {
                toast("No browser available. Open developers.google.com/people/api/rest/v1/people/get on another device.");
            }
        }), 8);
        Ui.add(card, Ui.button(this, "Continue", Ui.FILLED, v -> {
            String typed = id.getText().toString();
            if (!LockdownPolicy.validAccountId(typed)) {
                toast("That is not a Google account ID. It is a number of about 21 digits, not an email address.");
                return;
            }
            prefs().edit().putString("frpAccountId", LockdownPolicy.normalizeAccountId(typed)).apply();
            build();
        }), 16);
        if (Build.VERSION.SDK_INT < 30) {
            Ui.add(card, Ui.body(this, "This phone's Android is older than 11, so Factory Reset Protection itself "
                    + "isn't available -- everything else below still works.", true), 12);
        }
    }

    // ---------- step 3: the wizard itself ----------
    private void buildWizard() {
        LinearLayout warn = Ui.card(this, root);
        warn.addView(Ui.titleText(this, "This is permanent"));
        warn.addView(Ui.body(this, "Pick the restrictions and apps to remove below, test everything you need "
                + "(install anything you still want on the phone now -- Waze, Maps, whatever -- before locking "
                + "it), then press \"Close Forever\" at the bottom. After that, this app disappears, nothing can "
                + "be installed or removed again, and the only way to undo any of it is a factory reset.", true));

        buildRestrictionsSection();
        buildAppsSection();

        LinearLayout closeCard = Ui.card(this, root);
        closeCard.addView(Ui.titleText(this, "Close Forever"));
        closeCard.addView(Ui.body(this, "Applies everything above, hides this app for good, and blocks all future "
                + "installs and uninstalls. Cannot be undone from the phone.", true));
        Ui.add(closeCard, Ui.button(this, "Close Forever", Ui.DANGER, v -> promptCloseForever()), 12);
    }

    private void buildRestrictionsSection() {
        LinearLayout card = Ui.card(this, root);
        card.addView(Ui.titleText(this, "Restrictions"));
        card.addView(Ui.body(this, "These four are always applied at Close Forever, whatever else is picked: "
                + "no installing, no uninstalling, no unknown sources, no changing apps in Settings. Pick any "
                + "others below.", true));
        for (String[] r : LockdownPolicy.CURATED_RESTRICTIONS) {
            boolean forced = isAlwaysOn(r[0]);
            Ui.add(card, Ui.checkRow(this, r[1] + (forced ? " (always on)" : ""), null,
                    forced || selectedRestrictions.contains(r[0]), (btn, checked) -> {
                        if (forced) return; // always on regardless of the box
                        toggleRestriction(r[0], checked);
                    }), 8);
        }
        Ui.add(card, Ui.button(this, showingAllRestrictions ? "Hide the rest" : "Show every restriction Android has",
                Ui.OUTLINED, v -> {
                    showingAllRestrictions = !showingAllRestrictions;
                    build();
                }), 12);
        if (showingAllRestrictions) {
            for (String key : LockdownPolicy.OTHER_RESTRICTIONS) {
                Ui.add(card, Ui.checkRow(this, LockdownPolicy.humanize(key), null,
                        selectedRestrictions.contains(key), (btn, checked) -> toggleRestriction(key, checked)), 6);
            }
        }
    }

    private boolean isAlwaysOn(String key) {
        for (String a : LockdownPolicy.ALWAYS_ON_LOCKDOWN) if (a.equals(key)) return true;
        return false;
    }

    private void toggleRestriction(String key, boolean on) {
        if (on) selectedRestrictions.add(key); else selectedRestrictions.remove(key);
        prefs().edit().putStringSet("selectedRestrictions", selectedRestrictions).apply();
    }

    private void buildAppsSection() {
        LinearLayout card = Ui.card(this, root);
        card.addView(Ui.titleText(this, "Apps to remove"));
        card.addView(Ui.body(this, "Checked apps are deleted at Close Forever -- uninstalled if that's possible, "
                + "otherwise hidden at the Android level so they can't open or run. System parts the phone "
                + "depends on aren't offered here.", true));

        Map<String, String> byLabel = new TreeMap<>();
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String, String> seen = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || LockdownPolicy.isProtected(pkg) || seen.containsKey(pkg)) continue;
            String label = ri.loadLabel(pm).toString();
            seen.put(pkg, label);
        }
        for (Map.Entry<String, String> e : seen.entrySet()) byLabel.put(e.getValue() + "\u0000" + e.getKey(), e.getKey());

        for (Map.Entry<String, String> e : byLabel.entrySet()) {
            String pkg = e.getValue();
            String label = e.getKey().split("\u0000")[0];
            Ui.add(card, Ui.checkRow(this, label, pkg, selectedDeletes.contains(pkg), (btn, checked) -> {
                if (checked) selectedDeletes.add(pkg); else selectedDeletes.remove(pkg);
                prefs().edit().putStringSet("selectedDeletes", selectedDeletes).apply();
            }), 6);
        }
    }

    // ---------- the irreversible step ----------
    private void promptCloseForever() {
        final EditText input = Ui.field(this, "Type LOCK to confirm");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 20);
        box.setPadding(p, Ui.dp(this, 8), p, 0);
        Ui.add(box, input, 8);
        android.app.AlertDialog dialog = Ui.alertDialog(this)
                .setTitle("Close forever?")
                .setMessage("This phone will be locked exactly as set up above. Nothing can be installed or "
                        + "removed again, and this app will no longer open. Only a factory reset undoes any of "
                        + "this -- make sure everything you wanted is already set up on the phone.")
                .setView(box)
                .create();
        Ui.add(box, Ui.button(this, "Close Forever", Ui.DANGER, v -> {
            if (!input.getText().toString().trim().equals("LOCK")) {
                toast("Not locked. You must type LOCK.");
                return;
            }
            dialog.dismiss();
            closeForever();
        }), 16);
        Ui.add(box, Ui.button(this, "Cancel", Ui.OUTLINED, v -> dialog.dismiss()), 8);
        dialog.show();
    }

    private void closeForever() {
        DevicePolicyManager dpm = dpm();
        ComponentName admin = admin();

        applyFrp(dpm, admin);

        Set<String> restrictions = new HashSet<>(selectedRestrictions);
        for (String a : LockdownPolicy.ALWAYS_ON_LOCKDOWN) restrictions.add(a);
        for (String r : restrictions) {
            try {
                dpm.addUserRestriction(admin, r);
            } catch (Exception ignored) {
            }
        }

        PackageManager pm = getPackageManager();
        for (String pkg : selectedDeletes) deleteApp(pm, dpm, admin, pkg);

        // Disable only this one component -- never the whole package or the admin receiver, which
        // would risk Android treating device-admin status itself as removed. This alone hides the
        // icon from the launcher and makes the app unopenable from anywhere, while leaving device
        // owner, and every restriction just applied, fully in force.
        getPackageManager().setComponentEnabledSetting(
                new ComponentName(this, MainActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        prefs().edit().putBoolean("lockedForever", true).apply();

        finishAndRemoveTask();
    }

    private void applyFrp(DevicePolicyManager dpm, ComponentName admin) {
        if (Build.VERSION.SDK_INT < 30) return;
        String accountId = prefs().getString("frpAccountId", null);
        if (accountId == null || accountId.isEmpty()) return;
        try {
            android.app.admin.FactoryResetProtectionPolicy p = new android.app.admin.FactoryResetProtectionPolicy.Builder()
                    .setFactoryResetProtectionAccounts(java.util.Collections.singletonList(accountId))
                    .setFactoryResetProtectionEnabled(true)
                    .build();
            dpm.setFactoryResetProtectionPolicy(admin, p);
        } catch (Exception ignored) {
        }
    }

    private void deleteApp(PackageManager pm, DevicePolicyManager dpm, ComponentName admin, String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES);
            boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            if (system) {
                dpm.setApplicationHidden(admin, pkg, true);
                return;
            }
            if (dpm.isApplicationHidden(admin, pkg)) dpm.setApplicationHidden(admin, pkg, false);
            Intent result = new Intent(this, UninstallReceiver.class);
            PendingIntent pending = PendingIntent.getBroadcast(this, pkg.hashCode(), result,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            pm.getPackageInstaller().uninstall(pkg, pending.getIntentSender());
        } catch (Exception ignored) {
            // Best-effort -- a handful of apps failing to delete shouldn't block the rest of lockdown.
        }
    }
}
