package com.familymdm.lockdown;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * One-time, one-way setup. This never talks to a server and keeps nothing configurable running
 * afterward: pick what's allowed, confirm, set the recovery account, and it locks itself away for
 * good. There is no undo from inside the app -- only a factory reset gets back in, and that reset
 * itself is gated by the Google account entered at the very end.
 */
public class MainActivity extends Activity {
    private LinearLayout root;

    // null = the two-button hub; "regular" or "system" = inside one of the pickers; "frp" = the
    // final recovery-account step, reached only after "This device is set up" is confirmed.
    private String section;
    private String search = "";
    private LinearLayout pickerList;

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
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (root != null) build();
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
        if ("regular".equals(section)) {
            buildPicker(true);
        } else if ("system".equals(section)) {
            buildPicker(false);
        } else if ("frp".equals(section)) {
            buildFrpStep();
        } else {
            buildHub();
        }
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

    // ---------- step 2: the hub ----------
    private void buildHub() {
        LinearLayout card = Ui.card(this, root);
        card.addView(Ui.titleText(this, "Step 2: choose what's allowed"));
        card.addView(Ui.body(this, "Google Maps, Waze, and Android Auto are always allowed -- nothing to set up "
                + "for those. Every other app starts blocked. Use the two lists below to allow more regular "
                + "apps, or to block specific system apps (ones with no icon of their own, like a search or "
                + "suggestions service).", true));
        Set<String> allowed = prefs().getStringSet("allowedApps", new LinkedHashSet<>());
        Set<String> blocked = prefs().getStringSet("blockedSystemApps", new LinkedHashSet<>());
        Ui.add(card, Ui.button(this, "Regular apps (" + allowed.size() + " extra allowed)", Ui.TONAL, v -> {
            section = "regular";
            search = "";
            build();
        }), 12);
        Ui.add(card, Ui.button(this, "System apps (" + blocked.size() + " blocked)", Ui.TONAL, v -> {
            section = "system";
            search = "";
            build();
        }), 8);

        LinearLayout doneCard = Ui.card(this, root);
        doneCard.addView(Ui.titleText(this, "This device is set up"));
        doneCard.addView(Ui.body(this, "Locks the phone to exactly what's allowed above, hides this app for "
                + "good, and the phone can't be changed again from inside it. The only way back in afterward "
                + "is a factory reset.", true));
        Ui.add(doneCard, Ui.button(this, "This device is set up", Ui.DANGER, v -> promptConfirm()), 12);
    }

    // ---------- the two pickers ----------
    private void buildPicker(boolean regular) {
        LinearLayout header = Ui.card(this, root);
        Ui.add(header, Ui.button(this, "< Back", Ui.OUTLINED, v -> {
            section = null;
            search = "";
            build();
        }), 0);

        LinearLayout card = Ui.card(this, root);
        card.addView(Ui.titleText(this, regular ? "Regular apps" : "System apps"));
        card.addView(Ui.body(this, regular
                ? "Checked apps can be opened once the phone is locked. Everything else stays installed but can't be opened."
                : "Checked apps are switched off at the Android level -- they can't run, show a notification, "
                + "or pop up an ad once the phone is locked. System parts the phone depends on aren't listed here.", true));
        EditText searchField = Ui.field(this, "Search");
        searchField.setText(search);
        searchField.setSelection(search.length());
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                search = s.toString();
                fillPickerList(regular);
            }
        });
        Ui.add(card, searchField, 8);

        pickerList = new LinearLayout(this);
        pickerList.setOrientation(LinearLayout.VERTICAL);
        Ui.add(card, pickerList, 8);
        fillPickerList(regular);
    }

    /** Only repopulates the list rows, leaving the search field (and its focus/cursor) alone --
     * rebuilding the whole screen on every keystroke would kick the keyboard focus out each time. */
    private void fillPickerList(boolean regular) {
        pickerList.removeAllViews();
        Map<String, String> entries = regular ? regularAppCandidates() : systemAppCandidates();
        String needle = search.trim().toLowerCase(java.util.Locale.ROOT);
        Set<String> selected = new LinkedHashSet<>(prefs().getStringSet(
                regular ? "allowedApps" : "blockedSystemApps", new LinkedHashSet<>()));
        int shown = 0;
        for (Map.Entry<String, String> e : entries.entrySet()) {
            String pkg = e.getKey();
            String label = e.getValue();
            if (!needle.isEmpty() && !label.toLowerCase(java.util.Locale.ROOT).contains(needle)
                    && !pkg.toLowerCase(java.util.Locale.ROOT).contains(needle)) continue;
            shown++;
            Ui.add(pickerList, Ui.checkRow(this, label, pkg, selected.contains(pkg), (box, checked) -> {
                Set<String> s = new LinkedHashSet<>(prefs().getStringSet(
                        regular ? "allowedApps" : "blockedSystemApps", new LinkedHashSet<>()));
                if (checked) s.add(pkg); else s.remove(pkg);
                prefs().edit().putStringSet(regular ? "allowedApps" : "blockedSystemApps", s).apply();
            }), 6);
        }
        if (shown == 0) Ui.add(pickerList, Ui.body(this, "No matches.", true), 8);
    }

    /** Every launcher app on the phone, except this app itself, the three always-allowed ones
     * (nothing to toggle for those), and anything LockdownPolicy protects. */
    private Map<String, String> regularAppCandidates() {
        Set<String> alwaysAllowed = new LinkedHashSet<>();
        for (String p : LockdownPolicy.ALWAYS_ALLOWED_APPS) alwaysAllowed.add(p);
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String, String> byPkg = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || alwaysAllowed.contains(pkg) || LockdownPolicy.isProtected(pkg) || byPkg.containsKey(pkg))
                continue;
            byPkg.put(pkg, ri.loadLabel(pm).toString());
        }
        return sortedByLabel(byPkg);
    }

    /** Apps with no launcher icon of their own -- the only kind worth individually blocking, since
     * regular apps are already excluded from the allow-list by default. */
    private Map<String, String> systemAppCandidates() {
        PackageManager pm = getPackageManager();
        Set<String> launchable = new LinkedHashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
            launchable.add(ri.activityInfo.packageName);
        Map<String, String> byPkg = new LinkedHashMap<>();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if (ai.packageName.equals(getPackageName()) || launchable.contains(ai.packageName) || LockdownPolicy.isProtected(ai.packageName))
                continue;
            byPkg.put(ai.packageName, pm.getApplicationLabel(ai).toString());
        }
        return sortedByLabel(byPkg);
    }

    private Map<String, String> sortedByLabel(Map<String, String> byPkg) {
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> e : byPkg.entrySet()) sorted.put(e.getValue() + "\u0000" + e.getKey(), e.getKey());
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : sorted.entrySet()) out.put(e.getValue(), e.getKey().split("\u0000")[0]);
        return out;
    }

    // ---------- confirm, then the recovery-account step ----------
    private void promptConfirm() {
        Ui.alertDialog(this)
                .setTitle("Are you sure?")
                .setMessage("This locks the phone to exactly what's allowed right now. This app disappears "
                        + "afterward, and nothing can be changed again from the phone -- only a factory reset "
                        + "undoes any of it.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Yes, set it up", (d, w) -> {
                    section = "frp";
                    build();
                })
                .show();
    }

    private void buildFrpStep() {
        LinearLayout card = Ui.card(this, root);
        card.addView(Ui.titleText(this, "Factory Reset Protection"));
        card.addView(Ui.body(this, "The Google account that can set this phone up again after a factory reset "
                + "-- the only way back in once this is locked. No account has to be signed in on the phone "
                + "itself (Android 11 or newer; keep the bootloader locked).", true));
        final EditText id = Ui.field(this, "Google account ID (21 digits)");
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
        if (Build.VERSION.SDK_INT < 30) {
            Ui.add(card, Ui.body(this, "This phone's Android is older than 11, so Factory Reset Protection itself "
                    + "isn't available -- everything else still locks down normally.", true), 12);
        }
        Ui.add(card, Ui.button(this, "Lock it", Ui.DANGER, v -> {
            String typed = id.getText().toString();
            if (!LockdownPolicy.validAccountId(typed)) {
                toast("That is not 21 digits. A Google account ID from the People API page is exactly 21 digits.");
                return;
            }
            closeForever(LockdownPolicy.normalizeAccountId(typed));
        }), 16);
        Ui.add(card, Ui.button(this, "< Back", Ui.OUTLINED, v -> {
            section = null;
            build();
        }), 8);
    }

    // ---------- the irreversible step ----------
    private void closeForever(String frpAccountId) {
        DevicePolicyManager dpm = dpm();
        ComponentName admin = admin();

        applyFrp(dpm, admin, frpAccountId);

        for (String r : LockdownPolicy.ALWAYS_ON_LOCKDOWN) {
            try {
                dpm.addUserRestriction(admin, r);
            } catch (Exception ignored) {
            }
        }

        Set<String> blockedSystem = prefs().getStringSet("blockedSystemApps", new LinkedHashSet<>());
        for (String pkg : blockedSystem) {
            try {
                dpm.setApplicationHidden(admin, pkg, true);
            } catch (Exception ignored) {
            }
        }

        Set<String> allowed = new LinkedHashSet<>(prefs().getStringSet("allowedApps", new LinkedHashSet<>()));
        Kiosk.activate(this, dpm, admin, allowed);
        startForegroundService(new Intent(this, GuardService.class));

        // Disable only this one component -- never the whole package or the admin receiver, which
        // would risk Android treating device-admin status itself as removed. This alone hides the
        // icon from the launcher and makes the app unopenable from anywhere, while leaving device
        // owner, lock task, and every restriction just applied fully in force.
        getPackageManager().setComponentEnabledSetting(
                new ComponentName(this, MainActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        prefs().edit().putBoolean("lockedForever", true).apply();

        finishAndRemoveTask();
    }

    private void applyFrp(DevicePolicyManager dpm, ComponentName admin, String accountId) {
        if (Build.VERSION.SDK_INT < 30) return;
        try {
            android.app.admin.FactoryResetProtectionPolicy p = new android.app.admin.FactoryResetProtectionPolicy.Builder()
                    .setFactoryResetProtectionAccounts(java.util.Collections.singletonList(accountId))
                    .setFactoryResetProtectionEnabled(true)
                    .build();
            dpm.setFactoryResetProtectionPolicy(admin, p);
        } catch (Exception ignored) {
        }
    }
}
