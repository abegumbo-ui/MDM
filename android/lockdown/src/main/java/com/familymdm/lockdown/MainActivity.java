package com.familymdm.lockdown;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This never talks to a server. Everything here -- which apps are allowed, which Settings screens
 * are reachable, which Android restrictions are on -- is just configuration until the Lockdown
 * switch at the top is turned on; nothing is enforced before that, and this app is never hidden or
 * disabled, so it's always reachable to come back, change something, update it, or turn the
 * switch back off. A factory reset is only ever needed if the phone is literally lost or stolen --
 * not as the normal way to change anything, which is the whole point of the switch.
 */
public class MainActivity extends Activity {
    private LinearLayout root;

    // null = the hub; "regular"/"system"/"settings"/"restrictions"/"bulk" = inside one of the
    // pickers; "frp" = the recovery-account step, reached either from its own button or
    // automatically the first time the Lockdown switch is turned on with no account saved yet.
    private String section;
    private String search = "";
    private LinearLayout pickerList;
    private EditText bulkInput;
    private List<String> bulkWent = new ArrayList<>();
    private List<String> bulkDidnt = new ArrayList<>();
    private static final String ACTION_UNINSTALL_RESULT = "com.familymdm.lockdown.UNINSTALL_RESULT";
    private static final Pattern PACKAGE_NAME = Pattern.compile("\\b[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)+\\b");
    private BroadcastReceiver uninstallReceiver;

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
        // already disabled, so Android shouldn't be able to start this at all. This is the one and
        // only irreversible step in this app: everything else (the Lockdown switch) is reversible
        // any number of times, right up until "This device is set up for good" is pressed.
        if (prefs().getBoolean("lockedForever", false)) {
            finish();
            return;
        }
        if (!prefs().contains("allowedApps")) {
            Set<String> defaults = new LinkedHashSet<>();
            for (String p : LockdownPolicy.DEFAULT_ALLOWED_APPS) defaults.add(p);
            prefs().edit().putStringSet("allowedApps", defaults).apply();
        }
        if (!prefs().contains("extraRestrictions")) {
            prefs().edit().putStringSet("extraRestrictions", new LinkedHashSet<>(LockdownPolicy.DEFAULT_ON_RESTRICTIONS)).apply();
        }
        if (!prefs().contains("settingsCategories")) {
            Set<String> defaults = new LinkedHashSet<>();
            defaults.add("network");
            defaults.add("connected");
            prefs().edit().putStringSet("settingsCategories", defaults).apply();
        }
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        uninstallReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                String pkg = i.getStringExtra("pkg");
                int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                if (status == PackageInstaller.STATUS_SUCCESS) toast("Uninstalled " + pkg);
            }
        };
        registerReceiver(uninstallReceiver, new IntentFilter(ACTION_UNINSTALL_RESULT));
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (root != null) build();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(uninstallReceiver);
        } catch (Exception ignored) {
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private boolean isLockdownOn() {
        return prefs().getBoolean("lockdownOn", false);
    }

    private boolean showProtectedApps() {
        return prefs().getBoolean("showProtectedApps", false);
    }

    private void build() {
        root.removeAllViews();
        Ui.add(root, Ui.banner(this, "Lockdown Setup"), 0);

        if (!dpm().isDeviceOwnerApp(getPackageName())) {
            buildOwnerStep();
            return;
        }
        buildLockdownSwitch();
        applyRestrictionsLive();
        applyRegularAppLiveState();
        applySystemAppLiveState();
        if ("regular".equals(section)) {
            buildPicker(true);
        } else if ("system".equals(section)) {
            buildPicker(false);
        } else if ("settings".equals(section)) {
            buildSettingsPicker();
        } else if ("restrictions".equals(section)) {
            buildRestrictionsPicker();
        } else if ("bulk".equals(section)) {
            buildBulkPicker();
        } else if ("notifications".equals(section)) {
            buildNotificationsPicker();
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

    /** The big on/off switch, rendered right under the banner on every screen -- not buried in the
     * hub, so it's always the first thing visible, and always reachable regardless of what else is
     * being configured. Off means nothing below is enforced: every restriction clears, every app
     * comes back, this app stays exactly as reachable as it always is. On means whatever's
     * currently saved everywhere else gets pushed live -- blocked apps actually can't be opened,
     * restrictions actually apply -- but the phone stays on its regular launcher the whole time.
     * The kiosk home-screen takeover (replacing the launcher, pinning into lock task mode) is
     * deliberately NOT part of this switch -- that only starts at the real final step, "This device
     * is set up for good" at the bottom of the hub. Flipping this switch is always reversible, any
     * number of times, immediately, with no prerequisite -- it has nothing to do with Factory Reset
     * Protection either, which also only matters for that final step. */
    private void buildLockdownSwitch() {
        boolean on = isLockdownOn();
        LinearLayout card = Ui.card(this, root);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView label = Ui.titleText(this, on ? "Lockdown: ON" : "Lockdown: OFF");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(label, lp);
        Switch sw = Ui.tintedSwitch(this);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener((box, checked) -> {
            if (checked != on) toggleLockdown(checked);
        });
        row.addView(sw);
        card.addView(row);
        card.addView(Ui.body(this, on
                ? "Everything configured below is actively enforced on this phone right now -- blocked apps "
                + "can't be opened, restrictions actually apply -- but the phone stays on its regular launcher; "
                + "no kiosk home-screen takeover yet. Turn this off any time -- no factory reset needed -- to "
                + "open the phone back up, install an update to this app, or change anything."
                : "Nothing below is enforced yet. Configure whatever's wanted first, then turn this on to test "
                + "it live -- this app is never hidden or disabled, so it's always reachable to come back and "
                + "change anything, including turning this off again.", true));
    }

    private void toggleLockdown(boolean on) {
        if (!on) {
            Ui.alertDialog(this)
                    .setTitle("Turn lockdown off?")
                    .setMessage("Opens the phone back up completely -- every restriction clears, every blocked "
                            + "app comes back, and the home-screen takeover stops. Nothing configured here is "
                            + "lost; turning it back on reapplies exactly what's set up below.")
                    .setNegativeButton("Cancel", (d, w) -> build())
                    .setPositiveButton("Turn it off", (d, w) -> disableLockdown())
                    .setOnCancelListener(d -> build())
                    .show();
            return;
        }
        enableLockdown();
    }

    /** Deliberately does NOT call Kiosk.activate() -- that's the kiosk home-screen takeover, which
     * only starts at the real final step (finishForever()). This just applies the blocks and
     * restrictions directly via setApplicationHidden()/addUserRestriction(), neither of which is
     * tied to lock task mode at all, so testing what's blocked works the same on the phone's
     * regular launcher as it would once kiosk mode is actually running. */
    private void enableLockdown() {
        prefs().edit().putBoolean("lockdownOn", true).apply();
        applyRestrictionsLive();
        applyRegularAppLiveState();
        applySystemAppLiveState();
        section = null;
        build();
    }

    private void disableLockdown() {
        prefs().edit().putBoolean("lockdownOn", false).apply();
        applyRestrictionsLive();
        applyRegularAppLiveState();
        applySystemAppLiveState();
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                dpm().setFactoryResetProtectionPolicy(admin(),
                        new android.app.admin.FactoryResetProtectionPolicy.Builder().setFactoryResetProtectionEnabled(false).build());
            } catch (Exception ignored) {
            }
        }
        Kiosk.deactivate(this, dpm(), admin());
        section = null;
        build();
    }

    private void applyFrpFromSaved() {
        String accountId = prefs().getString("frpAccountId", null);
        if (accountId == null || Build.VERSION.SDK_INT < 30) return;
        try {
            android.app.admin.FactoryResetProtectionPolicy p = new android.app.admin.FactoryResetProtectionPolicy.Builder()
                    .setFactoryResetProtectionAccounts(java.util.Collections.singletonList(accountId))
                    .setFactoryResetProtectionEnabled(true)
                    .build();
            dpm().setFactoryResetProtectionPolicy(admin(), p);
        } catch (Exception ignored) {
        }
    }

    /** Every Device restrictions toggle's real, right-now state is kept in sync with the Lockdown
     * switch and what's saved, on every build() -- off clears every restriction regardless of what
     * boxes are checked below; on applies exactly those boxes. Nothing drifts out of sync with what
     * the picker shows checked, and nothing is ever enforced while the switch is off. */
    private void applyRestrictionsLive() {
        boolean on = isLockdownOn();
        Set<String> enabled = on ? prefs().getStringSet("extraRestrictions", LockdownPolicy.DEFAULT_ON_RESTRICTIONS)
                : java.util.Collections.emptySet();
        for (String key : LockdownPolicy.RESTRICTIONS.keySet()) {
            try {
                if (enabled.contains(key)) dpm().addUserRestriction(admin(), key);
                else dpm().clearUserRestriction(admin(), key);
            } catch (Exception ignored) {
            }
        }
    }

    /** Same idea as applyRestrictionsLive() for the Regular apps list: while the switch is off,
     * every launchable app stays open-able (hide = false) regardless of allowedApps; while it's on,
     * only what's allowed stays reachable. Runs on every build(), so nothing drifts and nothing is
     * ever actually blocked while the switch is off. */
    private void applyRegularAppLiveState() {
        boolean on = isLockdownOn();
        boolean showProtected = showProtectedApps();
        PackageManager pm = getPackageManager();
        Set<String> allowed = prefs().getStringSet("allowedApps", new LinkedHashSet<>());
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(main, PackageManager.MATCH_UNINSTALLED_PACKAGES)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            // Protected packages are left alone here unless "also show protected system
            // components" is checked on the picker -- the same opt-in gate as what's offered to
            // block in the first place, so this never silently hides one nobody chose to.
            if (!showProtected && LockdownPolicy.isProtected(pkg)) continue;
            try {
                dpm().setApplicationHidden(admin(), pkg, on && !allowed.contains(pkg));
            } catch (Exception ignored) {
            }
        }
    }

    /** Same idea again for the System apps list -- only unhides/hides the packages actually saved
     * in blockedSystemApps, rather than re-scanning every installed system package every build(). */
    private void applySystemAppLiveState() {
        boolean on = isLockdownOn();
        Set<String> blocked = prefs().getStringSet("blockedSystemApps", new LinkedHashSet<>());
        for (String pkg : blocked) {
            try {
                dpm().setApplicationHidden(admin(), pkg, on);
            } catch (Exception ignored) {
            }
        }
    }

    // ---------- step 2: the hub ----------
    private void buildHub() {
        LinearLayout card = Ui.card(this, root);
        card.addView(Ui.titleText(this, "Step 2: choose what's allowed"));
        card.addView(Ui.body(this, "Google Maps, Waze, and Android Auto are allowed by default -- nothing to "
                + "set up for those unless you want to change it. Everything below is just configuration until "
                + "the Lockdown switch above is on: use these lists to allow or block regular apps (including "
                + "those three, if you ever want to), to block specific system apps (ones with no icon of their "
                + "own, like a search or suggestions service), to pick which Settings screens (Wi-Fi, Connected "
                + "devices, etc.) show up on this app's own Settings tile, or to flip the same restriction "
                + "switches the dashboard offers for the agent app -- a few start checked by default (Factory "
                + "Reset, Developer Options), the rest start off. Flip the switch above once it's all set up "
                + "the way you want.", true));
        Set<String> allowed = prefs().getStringSet("allowedApps", new LinkedHashSet<>());
        Set<String> blocked = prefs().getStringSet("blockedSystemApps", new LinkedHashSet<>());
        Ui.add(card, Ui.button(this, "Regular apps (" + allowed.size() + " allowed)", Ui.TONAL, v -> {
            section = "regular";
            search = "";
            build();
        }), 12);
        Ui.add(card, Ui.button(this, "System apps (" + blocked.size() + " blocked)", Ui.TONAL, v -> {
            section = "system";
            search = "";
            build();
        }), 8);
        Set<String> settingsOn = prefs().getStringSet("settingsCategories", new LinkedHashSet<>());
        Ui.add(card, Ui.button(this, "Settings (" + settingsOn.size() + " enabled)", Ui.TONAL, v -> {
            section = "settings";
            build();
        }), 8);
        Set<String> extraOn = prefs().getStringSet("extraRestrictions", new LinkedHashSet<>());
        Ui.add(card, Ui.button(this, "Device restrictions (" + extraOn.size() + " on)", Ui.TONAL, v -> {
            section = "restrictions";
            build();
        }), 8);
        Ui.add(card, Ui.button(this, "Bulk list from an AI app audit", Ui.TONAL, v -> {
            section = "bulk";
            bulkWent = new ArrayList<>();
            bulkDidnt = new ArrayList<>();
            build();
        }), 8);
        Set<String> mutedOn = prefs().getStringSet("mutedNotificationPackages", new LinkedHashSet<>());
        Ui.add(card, Ui.button(this, "Notifications (" + mutedOn.size() + " muted)", Ui.TONAL, v -> {
            section = "notifications";
            search = "";
            build();
        }), 8);

        LinearLayout finishCard = Ui.card(this, root);
        finishCard.addView(Ui.titleText(this, "This device is set up for good"));
        finishCard.addView(Ui.body(this, "A different, one-way step from the Lockdown switch above -- this one "
                + "actually can't be undone from the phone. It asks for a recovery Google account first, then "
                + "confirms twice before doing anything: once pressed through, this app disables itself for "
                + "good, applying everything currently configured. A factory reset, using that recovery "
                + "account, is the only way back in after this.", true));
        Ui.add(finishCard, Ui.button(this, "This device is set up for good", Ui.DANGER, v -> {
            section = "frp";
            build();
        }), 12);
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
        card.addView(Ui.body(this, (regular
                ? "Checked apps can be opened once Lockdown is switched on; everything else gets blocked then. "
                : "Checked apps get switched off at the Android level once Lockdown is switched on -- they can't "
                + "run, show a notification, or pop up an ad. System parts the phone depends on aren't listed "
                + "here. Each one's note below judges it specifically against a Waze/Maps/Android Auto-only "
                + "build -- anything not recognized says so honestly instead of guessing. ")
                + "Nothing here is actually applied to the phone while the switch at the top is off.", true));
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

        Ui.add(card, Ui.checkRow(this, "Also show protected system components",
                "Play Store and the handful of other parts normally left off both lists because blocking them "
                        + "can break the phone. Checking this does not block anything by itself -- it just makes "
                        + "them reachable here too, same as any other app, if that's genuinely wanted.",
                showProtectedApps(), (box, checked) -> {
                    prefs().edit().putBoolean("showProtectedApps", checked).apply();
                    fillPickerList(regular);
                }), 8);

        pickerList = new LinearLayout(this);
        pickerList.setOrientation(LinearLayout.VERTICAL);
        Ui.add(card, pickerList, 8);
        fillPickerList(regular);
    }

    // ---------- the Settings picker ----------
    private void buildSettingsPicker() {
        LinearLayout header = Ui.card(this, root);
        Ui.add(header, Ui.button(this, "< Back", Ui.OUTLINED, v -> {
            section = null;
            build();
        }), 0);

        LinearLayout card = Ui.card(this, root);
        card.addView(Ui.titleText(this, "Settings"));
        card.addView(Ui.body(this, "Checked categories show up on this app's own \"Settings\" tile once the "
                + "phone is locked, each one jumping straight to the real screen for it. Nothing checked means no "
                + "Settings tile at all. The real Settings app itself is never reachable any other way -- this "
                + "list is the only door into it.", true));
        Set<String> enabled = new LinkedHashSet<>(prefs().getStringSet("settingsCategories", new LinkedHashSet<>()));
        for (String category : SettingsCategories.ORDER) {
            Ui.add(card, Ui.checkRow(this, SettingsCategories.label(category), null, enabled.contains(category), (box, checked) -> {
                Set<String> s = new LinkedHashSet<>(prefs().getStringSet("settingsCategories", new LinkedHashSet<>()));
                if (checked) s.add(category); else s.remove(category);
                prefs().edit().putStringSet("settingsCategories", s).apply();
            }), 6);
        }
    }

    // ---------- the device restrictions picker ----------
    private void buildRestrictionsPicker() {
        LinearLayout header = Ui.card(this, root);
        Ui.add(header, Ui.button(this, "< Back", Ui.OUTLINED, v -> {
            section = null;
            build();
        }), 0);

        LinearLayout card = Ui.card(this, root);
        card.addView(Ui.titleText(this, "Device restrictions"));
        card.addView(Ui.body(this, "The same restrictions the dashboard offers for the agent app, in the same "
                + "order. Just configuration until the Lockdown switch at the top is on -- nothing here is "
                + "actually applied to the phone while it's off, so check or uncheck freely. A few (Factory "
                + "Reset, Developer Options, and so on) start checked by default; the rest start off. The ones "
                + "marked below fight features this app already builds on purpose (the Wi-Fi/Connected devices "
                + "Settings categories, calls and texts) -- turning those on will break that specific feature, "
                + "so only do it if that trade-off is actually wanted here.", true));
        Set<String> enabled = new LinkedHashSet<>(prefs().getStringSet("extraRestrictions", LockdownPolicy.DEFAULT_ON_RESTRICTIONS));
        for (String key : LockdownPolicy.RESTRICTIONS.keySet()) {
            String label = LockdownPolicy.RESTRICTIONS.get(key);
            String desc = LockdownPolicy.conflictsWithBuiltins(key)
                    ? "Will break Wi-Fi/Connected devices Settings or calls/texts if this app uses them." : null;
            Ui.add(card, Ui.checkRow(this, label, desc, enabled.contains(key), (box, checked) -> {
                Set<String> s = new LinkedHashSet<>(prefs().getStringSet("extraRestrictions", LockdownPolicy.DEFAULT_ON_RESTRICTIONS));
                if (checked) s.add(key); else s.remove(key);
                prefs().edit().putStringSet("extraRestrictions", s).apply();
                // Only actually applied to the phone while the Lockdown switch is on -- while it's
                // off, this just saves the choice for whenever it's turned on later.
                if (isLockdownOn()) {
                    try {
                        if (checked) dpm().addUserRestriction(admin(), key);
                        else dpm().clearUserRestriction(admin(), key);
                    } catch (Exception ex) {
                        toast("Could not " + (checked ? "turn on" : "turn off") + " \"" + label + "\": " + ex.getMessage());
                    }
                }
            }), 6);
        }
    }

    // ---------- the bulk-list picker ----------
    /** For pasting a list an outside AI tool produced after going through a full dump of every app
     * on this phone (adb shell pm list packages -f, or similar) -- this never sends anything
     * anywhere itself, it just reads back whatever text was already produced elsewhere and pasted
     * in here. Any package name found anywhere in the pasted text gets blocked immediately, the
     * same way the System/Regular apps pickers already do it live; a genuinely removable app (not
     * part of Android itself) also gets a real uninstall requested. That's the "uninstall it, or
     * block it if it can't really be uninstalled" behavior asked for -- the block is what actually
     * guarantees the result either way, since most of what shows up in a list like this is
     * preinstalled OEM software Android won't let any app actually remove. */
    private void buildBulkPicker() {
        LinearLayout header = Ui.card(this, root);
        Ui.add(header, Ui.button(this, "< Back", Ui.OUTLINED, v -> {
            section = null;
            build();
        }), 0);

        // The log from the last run goes at the top, above the paste box -- so it's the first
        // thing visible after pressing "Apply list" instead of something to scroll past.
        if (!bulkWent.isEmpty() || !bulkDidnt.isEmpty()) {
            LinearLayout logCard = Ui.card(this, root);
            logCard.addView(Ui.titleText(this, "Log: " + bulkWent.size() + " went through, " + bulkDidnt.size() + " didn't"));
            for (String line : bulkWent) Ui.add(logCard, Ui.body(this, "Went: " + line, false), 4);
            for (String line : bulkDidnt) Ui.add(logCard, Ui.body(this, "Didn't: " + line, true), 4);
        }

        LinearLayout card = Ui.card(this, root);
        card.addView(Ui.titleText(this, "Bulk list from an AI app audit"));
        card.addView(Ui.body(this, "Paste whatever list an AI tool gave you after going through a dump of every "
                + "app on this phone -- any format is fine, this only looks for package names (like "
                + "com.something.app) anywhere in the text and ignores everything else around them. Every "
                + "package name found gets set to block: a regular app gets unchecked on the Regular apps list, "
                + "a background one gets added to System apps. While Lockdown is on, that also applies "
                + "immediately, with a real uninstall also requested (silently, since this app is the device "
                + "owner) for anything that isn't part of Android itself -- but the block is what actually "
                + "guarantees the result either way, since most things on a list like this can't really be "
                + "removed, only blocked. While Lockdown is off, this just saves the choices for later.", true));
        bulkInput = Ui.multilineField(this, "Paste the list here");
        Ui.add(card, bulkInput, 8);
        Ui.add(card, Ui.button(this, "Apply list", Ui.DANGER, v -> applyBulkList()), 12);
    }

    // ---------- the Notifications picker (soft block) ----------
    /** A soft block, separate from the Regular/System apps lists: a checked app here still runs
     * normally -- needed for things like Android Auto, which needs the Google app and Google
     * Play Services alive in the background -- but any notification it tries to show gets
     * dismissed automatically, so there's nothing on screen to tap into. Unlike every other
     * permission this app needs, Android does not let a device owner grant "Notification access"
     * silently via DevicePolicyManager -- it has to be granted here, once, by hand. */
    private void buildNotificationsPicker() {
        LinearLayout header = Ui.card(this, root);
        Ui.add(header, Ui.button(this, "< Back", Ui.OUTLINED, v -> {
            section = null;
            search = "";
            build();
        }), 0);

        LinearLayout card = Ui.card(this, root);
        card.addView(Ui.titleText(this, "Notifications"));
        card.addView(Ui.body(this, "A soft block, separate from the Regular/System apps lists above: a checked "
                + "app here still runs in the background -- this doesn't hide or block it the way the Regular/"
                + "System apps lists do -- but it gets no icon on the kiosk home screen to tap into, and any "
                + "notification it tries to show gets dismissed the instant it posts. Meant for something "
                + "Android Auto needs running (the Google app, Google Play Services) without being something "
                + "to tap into, by icon or by notification. Only takes effect while the Lockdown switch at the "
                + "top is on; an app checked here should also be checked \"allowed\" on the Regular apps list, "
                + "or it gets hidden outright and can't run at all.", true));

        if (!NotificationSuppressor.isEnabled(this)) {
            Ui.add(card, Ui.body(this, "Notification access isn't granted to this app yet -- checking apps below "
                    + "won't actually mute anything until it is. This has to be granted by hand; a device owner "
                    + "can't grant it silently.", true), 8);
            Ui.add(card, Ui.button(this, "Grant notification access", Ui.OUTLINED, v -> {
                try {
                    startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                } catch (Exception ex) {
                    toast("Could not open notification access settings.");
                }
            }), 8);
        }

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
                fillNotificationsList();
            }
        });
        Ui.add(card, searchField, 8);

        pickerList = new LinearLayout(this);
        pickerList.setOrientation(LinearLayout.VERTICAL);
        Ui.add(card, pickerList, 8);
        fillNotificationsList();
    }

    private void fillNotificationsList() {
        pickerList.removeAllViews();
        Map<String, String> entries = notificationCandidates();
        String needle = search.trim().toLowerCase(java.util.Locale.ROOT);
        Set<String> selected = new LinkedHashSet<>(prefs().getStringSet("mutedNotificationPackages", new LinkedHashSet<>()));
        PackageManager pm = getPackageManager();
        int shown = 0;
        for (Map.Entry<String, String> e : entries.entrySet()) {
            String pkg = e.getKey();
            String label = e.getValue();
            if (!needle.isEmpty() && !label.toLowerCase(java.util.Locale.ROOT).contains(needle)
                    && !pkg.toLowerCase(java.util.Locale.ROOT).contains(needle)) continue;
            shown++;
            android.graphics.drawable.Drawable icon = appIcon(pm, pkg);
            Ui.add(pickerList, Ui.checkRow(this, icon, label, pkg, selected.contains(pkg), (box, checked) -> {
                Set<String> s = new LinkedHashSet<>(prefs().getStringSet("mutedNotificationPackages", new LinkedHashSet<>()));
                if (checked) s.add(pkg); else s.remove(pkg);
                prefs().edit().putStringSet("mutedNotificationPackages", s).apply();
            }), 6);
        }
        if (shown == 0) Ui.add(pickerList, Ui.body(this, "No matches.", true), 8);
    }

    /** Every launcher app, plus the system essentials (the Google app and Google Play Services
     * among them) that don't have their own launcher icon -- notification muting is a soft block,
     * not a destructive one, so nothing is excluded here the way isProtected() excludes things on
     * the Regular/System apps pickers. */
    private Map<String, String> notificationCandidates() {
        PackageManager pm = getPackageManager();
        Map<String, String> byPkg = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_UNINSTALLED_PACKAGES)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || byPkg.containsKey(pkg)) continue;
            byPkg.put(pkg, ri.loadLabel(pm).toString());
        }
        for (String essential : LockdownPolicy.SYSTEM_ESSENTIALS) {
            if (byPkg.containsKey(essential)) continue;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(essential, PackageManager.MATCH_UNINSTALLED_PACKAGES);
                byPkg.put(essential, pm.getApplicationLabel(ai).toString());
            } catch (Exception ignored) {
            }
        }
        return sortedBySelectionThenLabel(byPkg, "mutedNotificationPackages");
    }

    private void applyBulkList() {
        Matcher m = PACKAGE_NAME.matcher(bulkInput.getText().toString());
        Set<String> found = new LinkedHashSet<>();
        while (m.find()) found.add(m.group());

        boolean on = isLockdownOn();
        PackageManager pm = getPackageManager();
        Set<String> allowed = new LinkedHashSet<>(prefs().getStringSet("allowedApps", new LinkedHashSet<>()));
        Set<String> blockedSystem = new LinkedHashSet<>(prefs().getStringSet("blockedSystemApps", new LinkedHashSet<>()));
        List<String> went = new ArrayList<>();
        List<String> didnt = new ArrayList<>();
        int changed = 0;

        for (String pkg : found) {
            if (pkg.equals(getPackageName())) {
                didnt.add(pkg + " (this app itself)");
                continue;
            }
            if (!showProtectedApps() && LockdownPolicy.isProtected(pkg)) {
                didnt.add(pkg + " (protected -- the phone needs this to work. Check \"also show protected "
                        + "system components\" on the Regular/System apps picker to block it anyway.)");
                continue;
            }
            ApplicationInfo ai;
            try {
                ai = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES);
            } catch (PackageManager.NameNotFoundException ex) {
                didnt.add(pkg + " (not installed on this phone)");
                continue;
            }
            boolean launchable = pm.getLaunchIntentForPackage(pkg) != null;
            if (launchable) {
                if (allowed.remove(pkg)) changed++;
                went.add(pkg + " -- set to block" + (on ? ", blocked immediately" : " once Lockdown is on") + " (was an allowed regular app)");
            } else {
                if (blockedSystem.add(pkg)) changed++;
                if (on) {
                    try {
                        dpm().setApplicationHidden(admin(), pkg, true);
                        went.add(pkg + " -- blocked immediately (system app)");
                    } catch (Exception ex) {
                        didnt.add(pkg + " (added to the block list, but couldn't apply it live: " + ex.getMessage() + ")");
                    }
                } else {
                    went.add(pkg + " -- set to block once Lockdown is on (system app)");
                }
            }
            boolean isSystemApp = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (!isSystemApp && on) {
                attemptSilentUninstall(pkg);
                went.add(pkg + " -- also requested a real uninstall (not part of Android itself)");
            }
        }
        prefs().edit().putStringSet("allowedApps", allowed).putStringSet("blockedSystemApps", blockedSystem).apply();
        if (found.isEmpty()) didnt.add("nothing -- no package names found in that text");
        bulkWent = went;
        bulkDidnt = didnt;
        toast(changed + " app" + (changed == 1 ? "" : "s") + " changed.");
        build();
    }

    /** Best-effort only -- a device owner can silently uninstall without the usual confirmation
     * dialog, but most of what ends up in a list like this is preinstalled OEM software Android
     * won't actually let anything remove, so this is a bonus on top of the block above, never
     * something the block depends on. */
    private void attemptSilentUninstall(String pkg) {
        try {
            PackageInstaller installer = getPackageManager().getPackageInstaller();
            Intent intent = new Intent(ACTION_UNINSTALL_RESULT).setPackage(getPackageName()).putExtra("pkg", pkg);
            PendingIntent pending = PendingIntent.getBroadcast(this, pkg.hashCode(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
            installer.uninstall(pkg, pending.getIntentSender());
        } catch (Exception ignored) {
        }
    }

    /** Only repopulates the list rows, leaving the search field (and its focus/cursor) alone --
     * rebuilding the whole screen on every keystroke would kick the keyboard focus out each time. */
    private void fillPickerList(boolean regular) {
        pickerList.removeAllViews();
        Map<String, String> entries = regular ? regularAppCandidates() : systemAppCandidates();
        String needle = search.trim().toLowerCase(java.util.Locale.ROOT);
        Set<String> selected = new LinkedHashSet<>(prefs().getStringSet(
                regular ? "allowedApps" : "blockedSystemApps", new LinkedHashSet<>()));
        PackageManager pm = getPackageManager();
        int shown = 0;
        for (Map.Entry<String, String> e : entries.entrySet()) {
            String pkg = e.getKey();
            String label = e.getValue();
            if (!needle.isEmpty() && !label.toLowerCase(java.util.Locale.ROOT).contains(needle)
                    && !pkg.toLowerCase(java.util.Locale.ROOT).contains(needle)) continue;
            shown++;
            android.graphics.drawable.Drawable icon = appIcon(pm, pkg);
            String desc = regular ? pkg : pkg + "\n" + SystemAppSafety.note(pkg);
            Ui.add(pickerList, Ui.checkRow(this, icon, label, desc, selected.contains(pkg), (box, checked) -> {
                Set<String> s = new LinkedHashSet<>(prefs().getStringSet(
                        regular ? "allowedApps" : "blockedSystemApps", new LinkedHashSet<>()));
                if (checked) s.add(pkg); else s.remove(pkg);
                prefs().edit().putStringSet(regular ? "allowedApps" : "blockedSystemApps", s).apply();
                // Only actually applied to the phone while the Lockdown switch is on -- while it's
                // off, this just saves the choice. Checked means "allowed" for a regular app but
                // "blocked" for a system app, so which way hidden goes is flipped between the two.
                if (isLockdownOn()) {
                    try {
                        dpm().setApplicationHidden(admin(), pkg, regular ? !checked : checked);
                    } catch (Exception ex) {
                        toast("Could not " + (checked ? (regular ? "allow" : "block") : (regular ? "block" : "unblock"))
                                + " " + label + ": " + ex.getMessage());
                    }
                }
            }), 6);
        }
        if (shown == 0) Ui.add(pickerList, Ui.body(this, "No matches.", true), 8);
    }

    /** pm.getApplicationIcon(String) alone throws for an app this app has hidden
     * (setApplicationHidden) -- it's still really installed, just excluded from PackageManager's
     * default, visible-apps-only queries, so the icon has to be looked up with
     * MATCH_UNINSTALLED_PACKAGES first. Being hidden is no reason for its icon to disappear from
     * the very picker that un-hides it. Ported from the agent app's own AdminActivity.appIcon(). */
    private android.graphics.drawable.Drawable appIcon(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationIcon(pkg);
        } catch (Exception e) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES);
                return pm.getApplicationIcon(ai);
            } catch (Exception e2) {
                return pm.getDefaultActivityIcon();
            }
        }
    }

    /** Every launcher app on the phone, except this app itself and (unless "also show protected
     * system components" is checked) anything LockdownPolicy protects. Maps/Waze/Android Auto are
     * ordinary rows here too, just pre-checked by default (seeded once in onCreate) -- nothing
     * stops unchecking them like any other app. MATCH_UNINSTALLED_PACKAGES is needed so an app
     * already blocked (hidden) by this app doesn't vanish from its own picker -- hiding makes
     * PackageManager treat it like it's uninstalled unless asked otherwise. */
    private Map<String, String> regularAppCandidates() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        boolean showProtected = showProtectedApps();
        Map<String, String> byPkg = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, PackageManager.MATCH_UNINSTALLED_PACKAGES)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || byPkg.containsKey(pkg)) continue;
            if (!showProtected && LockdownPolicy.isProtected(pkg)) continue;
            byPkg.put(pkg, ri.loadLabel(pm).toString());
        }
        return sortedBySelectionThenLabel(byPkg, "allowedApps");
    }

    /** Apps with no launcher icon of their own -- the only kind worth individually blocking, since
     * regular apps are already excluded from the allow-list by default. Same
     * MATCH_UNINSTALLED_PACKAGES reasoning as above, on both the launcher-icon scan and the
     * installed-apps scan -- without it, a blocked system app drops out of this list the moment
     * it's actually blocked, making it impossible to find again to unblock. */
    private Map<String, String> systemAppCandidates() {
        PackageManager pm = getPackageManager();
        boolean showProtected = showProtectedApps();
        Set<String> launchable = new LinkedHashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_UNINSTALLED_PACKAGES))
            launchable.add(ri.activityInfo.packageName);
        Map<String, String> byPkg = new LinkedHashMap<>();
        for (ApplicationInfo ai : pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)) {
            if (ai.packageName.equals(getPackageName()) || launchable.contains(ai.packageName)) continue;
            if (!showProtected && LockdownPolicy.isProtected(ai.packageName)) continue;
            byPkg.put(ai.packageName, pm.getApplicationLabel(ai).toString());
        }
        return sortedBySelectionThenLabel(byPkg, "blockedSystemApps");
    }

    /** Checked (allowed, for Regular apps; blocked, for System apps) entries first, each group
     * then alphabetical -- so whatever's already been decided on this picker stays easy to find
     * and review instead of getting lost among hundreds of untouched apps. */
    private Map<String, String> sortedBySelectionThenLabel(Map<String, String> byPkg, String prefsKey) {
        Set<String> selected = prefs().getStringSet(prefsKey, new LinkedHashSet<>());
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> e : byPkg.entrySet()) {
            String pkg = e.getKey();
            String rank = selected.contains(pkg) ? "0" : "1";
            sorted.put(rank + "\u0000" + e.getValue() + "\u0000" + pkg, pkg);
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String pkg : sorted.values()) out.put(pkg, byPkg.get(pkg));
        return out;
    }

    // ---------- the final, one-way "set up for good" step ----------
    /** Reached only from the button at the bottom of the hub. Enter the recovery account, get
     * validated, then two separate "are you sure" confirmations before anything actually happens --
     * deliberately more friction than the reversible switch above, since this step genuinely can't
     * be undone from the phone afterward. */
    private void buildFrpStep() {
        LinearLayout card = Ui.card(this, root);
        card.addView(Ui.titleText(this, "Factory Reset Protection"));
        card.addView(Ui.body(this, "The Google account that can set this phone up again after a factory reset "
                + "-- the only way back in once this device is set up for good. No account has to be signed in "
                + "on the phone itself (Android 11 or newer; keep the bootloader locked).", true));
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
        Ui.add(card, Ui.button(this, "Continue", Ui.DANGER, v -> {
            String typed = id.getText().toString();
            if (!LockdownPolicy.validAccountId(typed)) {
                toast("That is not 21 digits. A Google account ID from the People API page is exactly 21 digits.");
                return;
            }
            promptLockItFirst(LockdownPolicy.normalizeAccountId(typed));
        }), 16);
        Ui.add(card, Ui.button(this, "< Back", Ui.OUTLINED, v -> {
            section = null;
            build();
        }), 8);
    }

    private void promptLockItFirst(String accountId) {
        Ui.alertDialog(this)
                .setTitle("Are you sure you want to lock it?")
                .setMessage("This device is set up for good: everything currently configured gets applied, and "
                        + "this app disables itself. There's no more coming back to change anything afterward.")
                .setNegativeButton("Cancel", (d, w) -> build())
                .setOnCancelListener(d -> build())
                .setPositiveButton("Yes", (d, w) -> promptLockItSecond(accountId))
                .show();
    }

    private void promptLockItSecond(String accountId) {
        Ui.alertDialog(this)
                .setTitle("Are you sure you want to lock it?")
                .setMessage("Last chance to back out. Once you press yes, this app disables itself for good -- "
                        + "only a factory reset, using the recovery account just entered, undoes any of it.")
                .setNegativeButton("Cancel", (d, w) -> build())
                .setOnCancelListener(d -> build())
                .setPositiveButton("Yes", (d, w) -> finishForever(accountId))
                .show();
    }

    /** The one genuinely irreversible step in this app -- everything else (the Lockdown switch) can
     * be flipped back and forth any number of times. Applies Factory Reset Protection and whatever's
     * currently configured, activates the kiosk takeover, then disables MainActivity's own launcher
     * component for good, the same way the original one-way design always worked. */
    private void finishForever(String accountId) {
        prefs().edit().putString("frpAccountId", accountId).putBoolean("lockdownOn", true).apply();
        applyFrpFromSaved();
        applyRestrictionsLive();
        applyRegularAppLiveState();
        applySystemAppLiveState();
        Set<String> allowed = new LinkedHashSet<>(prefs().getStringSet("allowedApps", new LinkedHashSet<>()));
        Kiosk.activate(this, dpm(), admin(), allowed);
        getPackageManager().setComponentEnabledSetting(
                new ComponentName(this, MainActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        prefs().edit().putBoolean("lockedForever", true).apply();
        finishAndRemoveTask();
    }
}
