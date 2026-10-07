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

    @Override
    protected void onResume() {
        super.onResume();
        checkPendingLocalLearn();
    }

    /**
     * Picks up where a "Learn" tap left off: it sent the person into Settings and recorded which
     * category they were after, so coming back here (swipe up / recents, not re-launching this
     * activity) is what finishes the capture -- no server round trip, no dashboard involved. What
     * gets captured becomes the Settings menu icon's direct link for a category with no
     * android-wide link at all (see SettingsMenu).
     */
    private void checkPendingLocalLearn() {
        String category = Agent.prefs(this).getString("pendingLocalLearnCategory", null);
        if (category == null) return;
        long expires = Agent.prefs(this).getLong("pendingLocalLearnExpiresAt", 0);
        if (System.currentTimeMillis() > expires) {
            Agent.prefs(this).edit().remove("pendingLocalLearnCategory").remove("pendingLocalLearnExpiresAt").apply();
            return;
        }
        String component = SettingsMenu.captureNow(this);
        if (component == null) return; // still waiting -- came back too soon, or never got to a settings-ish screen
        Agent.prefs(this).edit().remove("pendingLocalLearnCategory").remove("pendingLocalLearnExpiresAt").apply();
        String label = SettingsMenu.label(category);
        new AlertDialog.Builder(this)
                .setTitle("Learned \"" + label + "\"?")
                .setMessage("Right after Settings opened, this is what was on screen:\n\n" + component
                        + "\n\nUse it as the Settings menu's direct link for \"" + label + "\"?")
                .setPositiveButton("Use it", (d, w) -> {
                    SettingsMenu.setLearnedTarget(this, category, component);
                    Agent.addEvent(this, "local", "Master code on phone: learned the Settings menu link for \"" + label + "\"");
                    toast("Saved. The Settings menu icon opens straight to it now.");
                    build();
                })
                .setNegativeButton("Discard", null)
                .show();
    }

    private void startLocalLearn(String category, String label) {
        if (!unlocked()) return;
        Agent.prefs(this).edit()
                .putString("pendingLocalLearnCategory", category)
                .putLong("pendingLocalLearnExpiresAt", System.currentTimeMillis() + 10 * 60 * 1000)
                .apply();
        toast("Opening Settings -- go into \"" + label + "\", then come back here.");
        startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
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
        statusCard.addView(Ui.body(this, "This phone's own recovery code: " + Agent.fallbackCode(this)
                + ". Works here and anywhere else a code is asked for, with no internet, even with no master code set.", true));
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
        JSONArray storedAllowed = null;
        try {
            String stored = Agent.prefs(this).getString("policy", null);
            if (stored != null) storedAllowed = new JSONObject(stored).optJSONArray("allowed");
        } catch (Exception ignored) {
        }
        boolean canTurnOnHere = storedAllowed != null && storedAllowed.length() > 0;
        home.addView(Ui.body(this, Kiosk.paused(this) ? "Paused: the phone is working normally."
                : Kiosk.active(this) ? "On: only allowed apps can be opened."
                : canTurnOnHere ? "Off."
                : "Off. Allow some apps first (App rules, from the dashboard or this phone's App rules screen) — otherwise there'd be nothing to open here.", true));
        if (!Kiosk.active(this) && !Kiosk.paused(this) && canTurnOnHere) {
            action(home, "Turn on home screen mode", Ui.TONAL, v -> {
                if (!unlocked()) return;
                try {
                    String stored = Agent.prefs(this).getString("policy", "{}");
                    JSONObject policy = new JSONObject(stored);
                    policy.put("homeScreen", true);
                    Agent.prefs(this).edit().putString("policy", policy.toString()).apply();
                    long rev = System.currentTimeMillis();
                    Agent.prefs(this).edit().putLong("homeScreenRev", rev).putBoolean("homeScreenValue", true).apply();
                    Agent.addEvent(this, "local", "Master code on phone: turned on home screen mode");
                    PolicyApplier.applyStored(this);
                    AgentService.requestSync();
                    build();
                    toast("Home screen mode on. The phone switches right away.");
                } catch (Exception e) {
                    toast("Could not turn it on: " + e.getMessage());
                }
            });
        }
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
                Kiosk.announceChange(this);
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
                ? "On: opening MDM Agent needs its own PIN, separate from the phone's screen lock."
                : "Off: anyone who opens this app can see it with no extra check.", true));
        if (appLockOn) {
            action(appLockCard, "Change app PIN", Ui.OUTLINED, v -> promptNewAppPin(false));
            action(appLockCard, "Turn off app lock", Ui.OUTLINED, v -> {
                AppPin.clear(this);
                Agent.prefs(this).edit().putBoolean("appLock", false).apply();
                Agent.addEvent(this, "local", "Master code on phone: turned off app lock");
                build();
            });
        } else {
            action(appLockCard, "Turn on app lock", Ui.TONAL, v -> promptNewAppPin(true));
        }

        LinearLayout menuCard = Ui.card(this, root);
        menuCard.addView(Ui.titleText(this, "Settings menu icon setup"));
        menuCard.addView(Ui.body(this, "The \"Settings\" icon this phone shows has no Android-wide link for a few "
                + "categories -- Motorola made these up itself, with no name any app can use. Learn them here the "
                + "same way: tap Learn, go into that category for real, then come back to this screen.", true));
        for (String category : SettingsMenu.NEEDS_LEARN) {
            String label = category.equals("connected") ? "Connected devices (Connection preferences)" : SettingsMenu.label(category);
            String learned = SettingsMenu.learnedTarget(this, category);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView rowLabel = Ui.body(this, label, false);
            rowLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(rowLabel);
            row.addView(Ui.button(this, learned != null ? "Re-learn" : "Learn", Ui.OUTLINED,
                    v -> startLocalLearn(category, label)));
            row.addView(Ui.button(this, "Add", Ui.OUTLINED, v -> promptAddLearnedTarget(category, label)));
            Ui.add(menuCard, row, 4);
            if (learned != null) {
                LinearLayout learnedRow = new LinearLayout(this);
                learnedRow.setOrientation(LinearLayout.HORIZONTAL);
                learnedRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
                TextView learnedText = Ui.body(this, "Set to: " + learned, true);
                learnedText.setTextSize(12);
                learnedText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                learnedRow.addView(learnedText);
                learnedRow.addView(Ui.button(this, "Clear", Ui.OUTLINED, v -> {
                    if (!unlocked()) return;
                    SettingsMenu.clearLearnedTarget(this, category);
                    build();
                }));
                Ui.add(menuCard, learnedRow, 8);
            }
        }

        // Plain list, nothing fancy: exact components to watch for and bounce away from the
        // instant they're in the foreground. Never touches a dashboard -- stored on this phone
        // only. Whatever is typed in has to be the full "package/ClassName" component (the part
        // after the slash is a fully-qualified class name, same as UsageEvents reports it).
        LinearLayout blockCard = Ui.card(this, root);
        blockCard.addView(Ui.titleText(this, "Blocked apps/screens"));
        for (String comp : AppBlocklist.list(this)) {
            boolean paused = AppBlocklist.isPaused(this, comp);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView t = Ui.body(this, comp + (paused ? "  (paused)" : ""), false);
            t.setTextSize(12);
            t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(t);
            row.addView(Ui.button(this, paused ? "Resume" : "Pause", Ui.OUTLINED, v -> {
                if (!unlocked()) return;
                AppBlocklist.setPaused(this, comp, !paused);
                build();
            }));
            row.addView(Ui.button(this, "Remove", Ui.OUTLINED, v -> {
                if (!unlocked()) return;
                AppBlocklist.remove(this, comp);
                build();
            }));
            Ui.add(blockCard, row, 4);
        }
        action(blockCard, "Add App", Ui.TONAL, v -> promptAddBlockedComponent());

        // A whole different kind of block from the one above -- an OS-level hide via
        // setApplicationHidden(), not a reactive bounce. The app just can't launch or run at all
        // while hidden, instantly, with no flash. Phone-only, same as the component list.
        LinearLayout wholeAppCard = Ui.card(this, root);
        wholeAppCard.addView(Ui.titleText(this, "Block whole app"));
        wholeAppCard.addView(Ui.body(this, "Hides an entire app at the Android level -- it can't open "
                + "or run at all, not just one screen inside it. Can hide something the phone or other "
                + "apps depend on, so each one asks to confirm first.", true));
        android.content.pm.PackageManager wholeAppPm = getPackageManager();
        for (String pkg : WholeAppBlocklist.list(this)) {
            boolean paused = WholeAppBlocklist.isPaused(this, pkg);
            String label = appLabel(wholeAppPm, pkg);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView t = Ui.body(this, label + " (" + pkg + ")" + (paused ? "  (paused)" : ""), false);
            t.setTextSize(12);
            t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(t);
            row.addView(Ui.button(this, paused ? "Resume" : "Pause", Ui.OUTLINED, v -> {
                if (!unlocked()) return;
                WholeAppBlocklist.setPaused(this, pkg, !paused);
                Agent.addEvent(this, "local", "Master code on phone: " + (!paused ? "paused" : "resumed")
                        + " the whole-app block on \"" + pkg + "\"");
                build();
            }));
            row.addView(Ui.button(this, "Remove", Ui.OUTLINED, v -> {
                if (!unlocked()) return;
                WholeAppBlocklist.remove(this, pkg);
                Agent.addEvent(this, "local", "Master code on phone: removed the whole-app block on \"" + pkg + "\"");
                build();
            }));
            Ui.add(wholeAppCard, row, 4);
        }
        action(wholeAppCard, "Add App", Ui.TONAL, v -> promptAddWholeApp());

        LinearLayout accessCard = Ui.card(this, root);
        accessCard.addView(Ui.titleText(this, "Accessibility service"));
        boolean accessOn = Agent.accessibilityServiceOn(this);
        accessCard.addView(Ui.body(this, "Backs out of a blocked screen (above) instantly, and doesn't "
                + "leave it sitting in recents the way the plain bounce does. "
                + (accessOn ? "Currently on. To stop it being turned off from Settings without going through "
                + "here, add Settings' own Accessibility screen to the blocked list above (same \"Add App\" "
                + "flow), then use its Pause button whenever you need to get in and change something yourself."
                : "Currently off -- turn it on under Settings > Accessibility, then come back here."), true));
        if (!accessOn) {
            action(accessCard, "Open Accessibility settings", Ui.OUTLINED, v -> {
                startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            });
        }

        if (!Agent.standalone(this)) {
            LinearLayout syncCard = Ui.card(this, root);
            syncCard.addView(Ui.titleText(this, "Dashboard connection"));
            boolean syncPaused = Agent.syncPaused(this);
            syncCard.addView(Ui.body(this, syncPaused
                    ? "Paused: this phone stopped checking in with the dashboard to save battery. The settings it already had keep being enforced. The dashboard shows it as not connected, on purpose."
                    : "On: this phone checks in with the dashboard regularly.", true));
            if (syncPaused) {
                action(syncCard, "Resume dashboard connection", Ui.FILLED, v -> {
                    if (!unlocked()) return;
                    Agent.prefs(this).edit().putBoolean("syncPaused", false).apply();
                    Agent.addEvent(this, "local", "Master code on phone: resumed the dashboard connection");
                    AgentService.requestSync();
                    build();
                    toast("Resuming. Checking in with the dashboard now.");
                });
            } else {
                action(syncCard, "Pause dashboard connection", Ui.OUTLINED, v -> {
                    if (!unlocked()) return;
                    toast("Pausing...");
                    new Thread(() -> {
                        // One last call so the dashboard knows this was paused on purpose,
                        // before this phone stops checking in at all.
                        try {
                            JSONObject body = new JSONObject();
                            body.put("syncPaused", true);
                            Api.post(Agent.prefs(this).getString("server", "") + "/agent/sync", body,
                                    Agent.prefs(this).getString("token", ""));
                        } catch (Exception ignored) {
                        }
                        Agent.prefs(this).edit().putBoolean("syncPaused", true).apply();
                        Agent.addEvent(this, "local", "Master code on phone: paused the dashboard connection");
                        runOnUiThread(() -> {
                            build();
                            toast("Paused. This phone won't check in again until you resume it here.");
                        });
                    }).start();
                });
            }
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

    /** An AutoCompleteTextView that narrows `suggestions` by substring match anywhere, not just a prefix. */
    private android.widget.AutoCompleteTextView autoCompleteOver(String hint, final java.util.List<String> suggestions) {
        final android.widget.AutoCompleteTextView input = Ui.autoCompleteField(this, hint);
        input.setAdapter(new android.widget.ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, suggestions) {
            @Override
            public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        java.util.List<String> matches = new java.util.ArrayList<>();
                        String needle = constraint == null ? "" : constraint.toString().toLowerCase();
                        for (String s : suggestions) {
                            if (needle.isEmpty() || s.toLowerCase().contains(needle)) matches.add(s);
                        }
                        FilterResults results = new FilterResults();
                        results.values = matches;
                        results.count = matches.size();
                        return results;
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        clear();
                        if (results.values != null) addAll((java.util.List<String>) results.values);
                        notifyDataSetChanged();
                    }
                };
            }
        });
        return input;
    }

    private String appLabel(android.content.pm.PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private void promptAddBlockedComponent() {
        if (!unlocked()) return;
        final java.util.List<String> suggestions = new java.util.ArrayList<>(AppBlocklist.seen(this));
        java.util.Collections.sort(suggestions);
        final android.widget.AutoCompleteTextView input = autoCompleteOver("package/ClassName", suggestions);
        new AlertDialog.Builder(this)
                .setTitle("Add app/screen to block")
                .setMessage("The exact component, written as package/ClassName -- for example:\n\n"
                        + "com.google.android.gms/com.google.android.gms.googlesettings.ui.GoogleSettingsActivity")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (d, w) -> {
                    String v = input.getText().toString().trim();
                    if (!v.contains("/")) {
                        toast("Needs a package and class name, separated by /");
                        return;
                    }
                    AppBlocklist.add(this, v);
                    Agent.addEvent(this, "local", "Master code on phone: added \"" + v + "\" to the blocked list");
                    build();
                })
                .show();
    }

    private void promptAddWholeApp() {
        if (!unlocked()) return;
        final android.content.pm.PackageManager pm = getPackageManager();
        final java.util.Map<String, String> byDisplay = new java.util.LinkedHashMap<>();
        for (android.content.pm.ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if (ai.packageName.equals(getPackageName())) continue;
            byDisplay.put(appLabel(pm, ai.packageName) + " (" + ai.packageName + ")", ai.packageName);
        }
        final java.util.List<String> suggestions = new java.util.ArrayList<>(byDisplay.keySet());
        java.util.Collections.sort(suggestions);
        final android.widget.AutoCompleteTextView input = autoCompleteOver("App name or package", suggestions);
        new AlertDialog.Builder(this)
                .setTitle("Block a whole app")
                .setMessage("Pick an installed app from the list, or type its exact package name.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Next", (d, w) -> {
                    String typed = input.getText().toString().trim();
                    String pkg = byDisplay.get(typed);
                    if (pkg == null) {
                        for (String p : byDisplay.values()) {
                            if (p.equalsIgnoreCase(typed)) {
                                pkg = p;
                                break;
                            }
                        }
                    }
                    if (pkg == null) {
                        toast("Pick a real installed app from the list, or type its exact package name.");
                        return;
                    }
                    final String finalPkg = pkg;
                    final String label = appLabel(pm, finalPkg);
                    new AlertDialog.Builder(this)
                            .setTitle("Block \"" + label + "\" entirely?")
                            .setMessage("This hides the whole app at the Android level -- it won't open or run "
                                    + "at all until you Pause or Remove it here. If the phone or other apps "
                                    + "depend on it, parts of the phone could stop working until then.\n\n" + finalPkg)
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Block it", (d2, w2) -> {
                                WholeAppBlocklist.add(this, finalPkg);
                                Agent.addEvent(this, "local", "Master code on phone: blocked the whole app \"" + finalPkg + "\"");
                                toast("\"" + label + "\" is hidden now.");
                                build();
                            })
                            .show();
                })
                .show();
    }

    private void promptAddLearnedTarget(String category, String label) {
        if (!unlocked()) return;
        final java.util.List<String> suggestions = new java.util.ArrayList<>(AppBlocklist.seen(this));
        java.util.Collections.sort(suggestions);
        final android.widget.AutoCompleteTextView input = autoCompleteOver("package/ClassName", suggestions);
        new AlertDialog.Builder(this)
                .setTitle("Add the link for \"" + label + "\"")
                .setMessage("The exact component, written as package/ClassName.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Check", (d, w) -> {
                    String v = input.getText().toString().trim();
                    if (!v.contains("/")) {
                        toast("Needs a package and class name, separated by /");
                        return;
                    }
                    String pkg = v.substring(0, v.indexOf('/'));
                    String cls = v.substring(v.indexOf('/') + 1);
                    boolean exists;
                    try {
                        getPackageManager().getActivityInfo(new android.content.ComponentName(pkg, cls), 0);
                        exists = true;
                    } catch (Exception e) {
                        exists = false;
                    }
                    String msg = (exists
                            ? "Found it -- this screen exists on this phone.\n\n"
                            : "Android doesn't recognize this as an existing screen on this phone -- it could "
                            + "still work if it's just not exported, or it could be wrong.\n\n") + v;
                    new AlertDialog.Builder(this)
                            .setTitle(exists ? "Found it" : "Not found")
                            .setMessage(msg + "\n\nUse it as the link for \"" + label + "\" anyway?")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Save", (d2, w2) -> {
                                SettingsMenu.setLearnedTarget(this, category, v);
                                Agent.addEvent(this, "local", "Master code on phone: set the Settings menu link for \"" + label + "\" by hand");
                                toast("Saved. The Settings menu icon opens straight to it now.");
                                build();
                            })
                            .show();
                })
                .show();
    }

    private void promptNewAppPin(boolean turningOn) {
        final EditText one = Ui.field(this, "App PIN or password (4+ characters)");
        final EditText two = Ui.field(this, "Repeat it");
        one.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        two.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle(turningOn ? "Choose an app PIN" : "Change the app PIN")
                .setMessage("This only locks MDM Agent's own screen. It has nothing to do with the phone's own screen lock, and works even if the phone has none set.")
                .setView(form(one, two))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    final String a = one.getText().toString();
                    if (a.length() < 4 || !a.equals(two.getText().toString())) {
                        toast("Use at least 4 characters, typed the same twice.");
                        return;
                    }
                    try {
                        AppPin.set(this, a);
                        Agent.prefs(this).edit().putBoolean("appLock", true).apply();
                        Agent.addEvent(this, "local", "Master code on phone: " + (turningOn ? "turned on app lock" : "changed the app PIN"));
                        toast(turningOn ? "App lock on. It applies the next time MDM Agent is opened." : "App PIN changed.");
                        build();
                    } catch (Exception e) {
                        toast("Could not save the PIN: " + e.getMessage());
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
