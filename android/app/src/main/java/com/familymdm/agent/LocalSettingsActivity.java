package com.familymdm.agent;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline mode: everything the dashboard's Settings and Apps tabs do, on the phone itself.
 * Opened from the master-code admin panel (MainActivity checked the code; the unlock lasts 10 minutes).
 */
public class LocalSettingsActivity extends Activity {
    private static final String[] DAY_NAMES = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
    private static final String[] DAY_SHORT = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final String PEOPLE_API = "https://developers.google.com/people/api/rest/v1/people/get";

    private ScrollView scroll;
    private LinearLayout root;
    private JSONObject cfg;

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
        scroll = new ScrollView(this);
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

    /** Saves the settings and applies them now (off the UI thread), then redraws without losing the scroll position. */
    private void commit() {
        LocalConfig.save(this, cfg);
        final int y = scroll.getScrollY();
        new Thread(() -> {
            try {
                LocalConfig.refresh(this);
            } catch (Exception e) {
                runOnUiThread(() -> toast("Could not apply: " + e.getMessage()));
            }
            runOnUiThread(() -> {
                build();
                scroll.post(() -> scroll.scrollTo(0, y));
            });
        }).start();
    }

    // ---------- small UI helpers ----------
    private LinearLayout toggle(LinearLayout parent, String label, String desc, boolean on, final java.util.function.Consumer<Boolean> change) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Ui.titleText(this, label));
        TextView t = (TextView) text.getChildAt(0);
        t.setTextSize(15);
        if (desc != null) text.addView(Ui.body(this, desc, true));
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener((b, checked) -> {
            if (unlocked()) change.accept(checked);
        });
        row.addView(sw);
        Ui.add(parent, row, 0);
        return row;
    }

    private LinearLayout form(EditText... fields) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        for (EditText f : fields) Ui.add(box, f, 8);
        return box;
    }

    // ---------- the screen ----------
    private void build() {
        cfg = LocalConfig.load(this);
        root.removeAllViews();
        root.addView(Ui.headline(this, "Phone settings"));
        root.addView(Ui.body(this, "Offline mode: these settings live on this phone only. Changes apply within seconds.", true));

        // ----- master code -----
        LinearLayout master = Ui.card(this, root);
        master.addView(Ui.titleText(this, "Master code"));
        master.addView(Ui.body(this, "Opens this screen and every other code prompt. There is no dashboard to reset it, so don't lose it: with reset protection on, wiping the phone won't get you out either.", true));
        Ui.add(master, Ui.button(this, "Change master code", Ui.TONAL, v -> { if (unlocked()) changeMaster(); }), 12);

        // ----- switches -----
        LinearLayout sw = Ui.card(this, root);
        sw.addView(Ui.titleText(this, "How apps are handled"));
        toggle(sw, "Home screen mode", "The agent becomes the home screen and shows only the apps you Allow. Other apps keep running in the background but can't be opened. Allow phone, messages and maps first.",
                cfg.optBoolean("homeScreen"), on -> {
                    if (on && Agent.prefs(this).getBoolean("kioskPaused", false)) Agent.prefs(this).edit().putBoolean("kioskPaused", false).apply();
                    put("homeScreen", on);
                    commit();
                });
        toggle(sw, "Hold new apps until I approve them", "Apps already on the phone count as approved. Anything installed later stays hidden until you approve it below.",
                cfg.optBoolean("approveNew"), on -> {
                    try {
                        if (on) cfg.put("known", LocalConfig.reportedNames(this));
                    } catch (Exception e) {
                        toast("Could not read the app list: " + e.getMessage());
                    }
                    put("approveNew", on);
                    commit();
                });
        toggle(sw, "Hide apps that aren't allowed", "Every launcher app not set to Allow is switched off. Protected system parts are never hidden.",
                cfg.optBoolean("blockUnlisted"), on -> {
                    put("blockUnlisted", on);
                    commit();
                });

        // ----- restrictions -----
        LinearLayout rs = Ui.card(this, root);
        rs.addView(Ui.titleText(this, "Restrictions"));
        JSONObject current = cfg.optJSONObject("restrictions");
        for (final String[] r : LocalPolicy.RESTRICTIONS) {
            toggle(rs, r[2], null, current != null && current.optBoolean(r[0]), on -> {
                try {
                    JSONObject now = cfg.optJSONObject("restrictions");
                    if (now == null) now = new JSONObject();
                    now.put(r[0], on);
                    cfg.put("restrictions", now);
                } catch (Exception ignored) {
                }
                commit();
            });
        }

        // ----- Factory Reset Protection -----
        buildFrp();

        // ----- apps -----
        buildApps();
    }

    private void put(String key, Object value) {
        try {
            cfg.put(key, value);
        } catch (Exception ignored) {
        }
    }

    // ---------- Factory Reset Protection ----------
    private void buildFrp() {
        LinearLayout card = Ui.card(this, root);
        card.addView(Ui.titleText(this, "Factory Reset Protection"));
        card.addView(Ui.body(this, "After a factory reset from recovery mode, setup demands this Google account, so the phone is useless to anyone else. No account has to be signed in on the phone. Android 11 or newer; for it to hold, the bootloader must stay locked.", true));

        final EditText id = Ui.field(this, "Google account ID (about 21 digits)");
        JSONArray ids = cfg.optJSONArray("frpAccounts");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; ids != null && i < ids.length(); i++) sb.append(i > 0 ? ", " : "").append(ids.optString(i));
        id.setText(sb.toString());
        Ui.add(card, id, 12);

        Ui.add(card, Ui.button(this, "Save", Ui.FILLED, v -> {
            if (!unlocked()) return;
            String text = id.getText().toString().trim();
            JSONArray entered = new JSONArray();
            for (String part : text.split("[ ,\\n]+")) if (!part.isEmpty()) entered.put(part);
            List<String> clean = LocalPolicy.normalizeAccounts(entered);
            if (entered.length() > 0 && clean.size() != entered.length()) {
                toast("That is not a Google account ID. It is a number of about 21 digits, not an email address.");
                return;
            }
            put("frpAccounts", new JSONArray(clean));
            commit();
            toast(clean.isEmpty() ? "Reset protection removed." : "Saved. Applying...");
        }), 8);

        TextView how = Ui.body(this,
                "How to get your Google account ID (use another device if easier):\n"
                        + "1. Open the Google People API page and tap \"Try it\".\n"
                        + "2. resourceName: people/me   personFields: metadata\n"
                        + "3. Tap Execute and sign in with the Google account YOU control.\n"
                        + "4. Copy the long number next to \"id\" (about 21 digits) and paste it above.\n"
                        + "Keep that account safe: whoever can sign in to it can set the phone up again after a reset.", true);
        how.setTextIsSelectable(true);
        Ui.add(card, how, 12);
        Ui.add(card, Ui.button(this, "Open the Google People API page", Ui.TONAL, v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PEOPLE_API)));
            } catch (Exception e) {
                toast("No browser available. Open " + PEOPLE_API + " on another device.");
            }
        }), 8);

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                android.app.admin.FactoryResetProtectionPolicy p = Agent.dpm(this).getFactoryResetProtectionPolicy(Agent.admin(this));
                card.addView(Ui.body(this, "Active on this phone now: " + (p == null ? 0 : p.getFactoryResetProtectionAccounts().size()) + " account(s).", true));
            } catch (Exception ignored) {
            }
        } else {
            card.addView(Ui.body(this, "This phone's Android is older than 11, so reset protection isn't available.", true));
        }
    }

    // ---------- apps ----------
    private void buildApps() {
        JSONArray packages;
        try {
            packages = PolicyApplier.collectPackages(this);
        } catch (Exception e) {
            toast("Could not list apps: " + e.getMessage());
            return;
        }

        // Waiting for approval
        List<String> pending = new ArrayList<>();
        try {
            String stored = Agent.prefs(this).getString("policy", null);
            if (stored != null && cfg.optBoolean("approveNew")) {
                JSONArray pa = new JSONObject(stored).optJSONArray("pending");
                for (int i = 0; pa != null && i < pa.length(); i++) pending.add(pa.getString(i));
            }
        } catch (Exception ignored) {
        }
        if (!pending.isEmpty()) {
            LinearLayout card = Ui.card(this, root);
            card.addView(Ui.titleText(this, "Waiting for your approval (" + pending.size() + ")"));
            for (final String pkg : pending) {
                String label = pkg;
                for (int i = 0; i < packages.length(); i++) {
                    JSONObject a = packages.optJSONObject(i);
                    if (a != null && pkg.equals(a.optString("p"))) label = a.optString("l", pkg);
                }
                card.addView(Ui.body(this, label + "  (" + pkg + ")", false));
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.addView(Ui.button(this, "Approve", Ui.FILLED, v -> { if (unlocked()) setMode(pkg, "allow"); }), weight());
                row.addView(Ui.button(this, "Block", Ui.DANGER, v -> { if (unlocked()) setMode(pkg, "block"); }), weight());
                Ui.add(card, row, 4);
            }
        }

        LinearLayout header = Ui.card(this, root);
        header.addView(Ui.titleText(this, "Apps"));
        header.addView(Ui.body(this, "Default = hidden only if \"Hide apps that aren't allowed\" is on. Allow can have a schedule: the app is only available in that window. Block switches the app off (in Home screen mode it just can't be opened).", true));

        JSONObject apps = cfg.optJSONObject("apps");
        for (int i = 0; i < packages.length(); i++) {
            final JSONObject a = packages.optJSONObject(i);
            if (a == null) continue;
            final String pkg = a.optString("p");
            JSONObject entry = apps == null ? null : apps.optJSONObject(pkg);
            String mode = entry == null ? "" : entry.optString("mode", "");

            LinearLayout card = Ui.card(this, root);
            card.addView(Ui.titleText(this, a.optString("l", pkg)));
            card.addView(Ui.body(this, pkg + (LocalPolicy.isProtected(pkg) ? " · protected" : "")
                    + (a.optBoolean("s") ? " · system app" : " · can be uninstalled"), true));

            LinearLayout modes = new LinearLayout(this);
            modes.setOrientation(LinearLayout.HORIZONTAL);
            modes.addView(Ui.button(this, "Default", mode.isEmpty() ? Ui.FILLED : Ui.OUTLINED, v -> { if (unlocked()) setMode(pkg, ""); }), weight());
            modes.addView(Ui.button(this, "Allow", mode.equals("allow") || mode.equals("force") ? Ui.FILLED : Ui.OUTLINED, v -> { if (unlocked()) setMode(pkg, "allow"); }), weight());
            modes.addView(Ui.button(this, "Block", mode.equals("block") ? Ui.FILLED : Ui.OUTLINED, v -> { if (unlocked()) setMode(pkg, "block"); }), weight());
            Ui.add(card, modes, 8);

            if (!mode.equals("block")) {
                JSONObject schedule = entry == null ? null : entry.optJSONObject("schedule");
                String label = schedule == null ? "Schedule…" : "Schedule: " + describe(schedule);
                Ui.add(card, Ui.button(this, label, schedule == null ? Ui.TONAL : Ui.FILLED, v -> { if (unlocked()) editSchedule(pkg); }), 8);
            }
        }
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = Ui.dp(this, 6);
        return lp;
    }

    private void setMode(String pkg, String mode) {
        try {
            JSONObject apps = cfg.optJSONObject("apps");
            if (apps == null) apps = new JSONObject();
            if (mode.isEmpty()) {
                apps.remove(pkg);
            } else {
                JSONObject entry = apps.optJSONObject(pkg);
                if (entry == null) entry = new JSONObject();
                entry.put("mode", mode);
                if (mode.equals("block")) entry.remove("schedule");
                apps.put(pkg, entry);
            }
            cfg.put("apps", apps);
        } catch (Exception ignored) {
        }
        commit();
    }

    private static String describe(JSONObject s) {
        JSONArray days = s.optJSONArray("days");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; days != null && i < days.length(); i++) {
            int d = days.optInt(i, 0);
            if (d >= 0 && d < 7) sb.append(sb.length() > 0 ? " " : "").append(DAY_SHORT[d]);
        }
        return sb + " " + s.optString("from") + "-" + s.optString("to");
    }

    // ---------- schedules ----------
    private void editSchedule(final String pkg) {
        JSONObject apps = cfg.optJSONObject("apps");
        JSONObject entry = apps == null ? null : apps.optJSONObject(pkg);
        JSONObject existing = entry == null ? null : entry.optJSONObject("schedule");
        final boolean[] checked = new boolean[7];
        if (existing != null) {
            JSONArray days = existing.optJSONArray("days");
            for (int i = 0; days != null && i < days.length(); i++) {
                int d = days.optInt(i, -1);
                if (d >= 0 && d < 7) checked[d] = true;
            }
        } else {
            for (int d = 1; d <= 5; d++) checked[d] = true;
        }
        new AlertDialog.Builder(this)
                .setTitle("Days this app is available")
                .setMultiChoiceItems(DAY_NAMES, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("No schedule", (d, w) -> setSchedule(pkg, null))
                .setPositiveButton("Next", (d, w) -> {
                    boolean any = false;
                    for (boolean b : checked) any |= b;
                    if (!any) {
                        toast("Pick at least one day.");
                        return;
                    }
                    pickTime("Available from", 8, 0, (fh, fm) -> pickTime("Available until", 17, 0, (th, tm) -> {
                        try {
                            JSONArray days = new JSONArray();
                            for (int i = 0; i < 7; i++) if (checked[i]) days.put(i);
                            JSONObject s = new JSONObject();
                            s.put("days", days);
                            s.put("from", String.format("%02d:%02d", fh, fm));
                            s.put("to", String.format("%02d:%02d", th, tm));
                            if (LocalPolicy.normalizeSchedule(s) == null) {
                                toast("The start and end times must be different.");
                                return;
                            }
                            setSchedule(pkg, s);
                        } catch (Exception e) {
                            toast("Could not save the schedule.");
                        }
                    }));
                })
                .show();
    }

    private interface TimeChosen {
        void chosen(int hour, int minute);
    }

    private void pickTime(String title, int hour, int minute, final TimeChosen done) {
        TimePickerDialog dlg = new TimePickerDialog(this, (view, h, m) -> done.chosen(h, m), hour, minute, true);
        dlg.setTitle(title);
        dlg.show();
    }

    private void setSchedule(String pkg, JSONObject schedule) {
        try {
            JSONObject apps = cfg.optJSONObject("apps");
            if (apps == null) apps = new JSONObject();
            JSONObject entry = apps.optJSONObject(pkg);
            if (entry == null) entry = new JSONObject();
            if (!entry.has("mode") || entry.optString("mode").isEmpty() || entry.optString("mode").equals("block")) entry.put("mode", "allow");
            if (schedule == null) entry.remove("schedule");
            else entry.put("schedule", schedule);
            apps.put(pkg, entry);
            cfg.put("apps", apps);
        } catch (Exception ignored) {
        }
        commit();
    }

    // ---------- master code ----------
    private void changeMaster() {
        final EditText one = Ui.field(this, "New master code (6 or more characters)");
        final EditText two = Ui.field(this, "Repeat it");
        one.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        two.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("Change master code")
                .setMessage("Write it down somewhere safe. There is no way to reset it.")
                .setView(form(one, two))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    final String a = one.getText().toString();
                    if (a.length() < 6 || !a.equals(two.getText().toString())) {
                        toast("Use at least 6 characters, typed the same twice.");
                        return;
                    }
                    new Thread(() -> {
                        String msg;
                        try {
                            Master.setLocal(this, a);
                            msg = "Master code changed.";
                        } catch (Exception e) {
                            msg = "Could not save it: " + e.getMessage();
                        }
                        final String text = msg;
                        runOnUiThread(() -> toast(text));
                    }).start();
                })
                .show();
    }
}
