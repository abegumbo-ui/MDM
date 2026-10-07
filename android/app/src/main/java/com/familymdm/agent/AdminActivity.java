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
    private TextView status;
    // null = the tile-grid Administrator home; otherwise which section's cards build() renders.
    private String currentSection;
    // Apps section: whether the "Show / Hide Apps" picker is expanded below its row.
    private boolean showingAppsPicker;
    // Home screen mode section: whether the "Allowed Apps" picker is expanded below its row.
    private boolean showingKioskAllowedPicker;
    // Settings menu section: null = pick "Icon setup" or "Categories"; otherwise which one is open.
    private String settingsMenuSub;
    // Shared by both apps pickers above (never shown at once, since only one section renders at a
    // time) -- the apps list and search text backing whichever picker is currently expanded.
    private JSONArray appsPickerPackages;
    private String appsPickerQuery = "";

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
    public void onBackPressed() {
        if (currentSection != null) {
            currentSection = null;
            resetSubState();
            build();
        } else {
            super.onBackPressed();
        }
    }

    /** Clears every section's own nested-navigation state, so leaving a section and coming back to
     * a different one never shows it still mid-way through something from last time. */
    private void resetSubState() {
        showingAppsPicker = false;
        showingKioskAllowedPicker = false;
        settingsMenuSub = null;
    }

    private boolean inSection(String name) {
        return name.equals(currentSection);
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

    private static final java.util.Map<String, String> SECTION_TITLES = new java.util.LinkedHashMap<>();
    static {
        SECTION_TITLES.put("lock", "Lock");
        SECTION_TITLES.put("apps", "Apps");
        SECTION_TITLES.put("browser", "Browser");
        SECTION_TITLES.put("kiosk", "Home screen mode");
        SECTION_TITLES.put("applock", "App lock");
        SECTION_TITLES.put("settingsmenu", "Settings menu");
        SECTION_TITLES.put("blocking", "Blocking");
        SECTION_TITLES.put("network", "Network");
        SECTION_TITLES.put("device", "Device");
        SECTION_TITLES.put("messages", "Messages");
    }

    private void openSection(String name) {
        currentSection = name;
        resetSubState();
        build();
    }

    private void build() {
        root.removeAllViews();
        Ui.add(root, Ui.banner(this, currentSection == null ? "Administrator" : SECTION_TITLES.get(currentSection)), 0);

        if (currentSection == null) {
            LinearLayout statusCard = Ui.card(this, root);
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

            android.widget.GridLayout grid = Ui.tileGrid(this);
            Ui.addTile(grid, Ui.tile(this, "Lock", R.drawable.ic_lock_tile, 96, v -> openSection("lock")));
            Ui.addTile(grid, Ui.tile(this, "Apps", R.drawable.ic_apps_tile, 96, v -> openSection("apps")));
            Ui.addTile(grid, Ui.tile(this, "Browser", R.drawable.ic_browser_tile, 96, v -> openSection("browser")));
            Ui.addTile(grid, Ui.tile(this, "Home screen\nmode", R.drawable.ic_home_tile, 96, v -> openSection("kiosk")));
            Ui.addTile(grid, Ui.tile(this, "App lock", R.drawable.ic_lock_tile, 96, v -> openSection("applock")));
            Ui.addTile(grid, Ui.tile(this, "Settings\nmenu", R.drawable.ic_apps_tile, 96, v -> openSection("settingsmenu")));
            Ui.addTile(grid, Ui.tile(this, "Blocking", R.drawable.ic_shield, 96, v -> openSection("blocking")));
            Ui.addTile(grid, Ui.tile(this, "Network", R.drawable.ic_wifi_tile, 96, v -> openSection("network")));
            Ui.addTile(grid, Ui.tile(this, "Device", R.drawable.ic_device_tile, 96, v -> openSection("device")));
            Ui.addTile(grid, Ui.tile(this, "Messages", R.drawable.ic_message_tile, 96, v -> openSection("messages")));
            Ui.add(root, grid, 16);
            return;
        }

        Ui.add(root, Ui.button(this, "< Back", Ui.OUTLINED, v -> onBackPressed()), 0);

        if (inSection("messages")) buildMessagesSection(root);
        if (inSection("lock")) buildLockSection(root);
        if (inSection("apps")) buildAppsSection(root);
        if (inSection("browser")) buildBrowserSection(root);
        if (inSection("kiosk")) buildKioskSection(root);
        if (inSection("applock")) buildAppLockSection(root);
        if (inSection("settingsmenu")) buildSettingsMenuSection(root);
        if (inSection("blocking")) buildBlockingSection(root);
        if (inSection("network")) buildNetworkSection(root);
        if (inSection("device")) buildDeviceSection(root);
        if (inSection("lock")) refreshStatus();
    }

    private void buildMessagesSection(LinearLayout root) {
        LinearLayout messagesCard = Ui.card(this, root);
        messagesCard.addView(Ui.titleText(this, "Messages"));
        JSONArray adminMessages = AdminMessages.list(this);
        if (adminMessages.length() == 0) {
            messagesCard.addView(Ui.body(this, "Nothing sent from \"Message the Administrator\" yet.", true));
        }
        for (int i = adminMessages.length() - 1; i >= 0; i--) {
            JSONObject m = adminMessages.optJSONObject(i);
            if (m == null) continue;
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            long at = m.optLong("at", 0);
            TextView when = Ui.body(this, at == 0 ? "" : java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(new java.util.Date(at)), true);
            when.setTextSize(11);
            row.addView(when);
            String text = m.optString("text", "");
            row.addView(Ui.body(this, text.isEmpty() ? "(no text)" : text, false));
            String photo = m.isNull("photo") ? null : m.optString("photo", null);
            if (photo != null) {
                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(photo);
                if (bmp != null) {
                    android.widget.ImageView iv = new android.widget.ImageView(this);
                    iv.setImageBitmap(bmp);
                    iv.setAdjustViewBounds(true);
                    iv.setMaxHeight(Ui.dp(this, 160));
                    iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                    Ui.add(row, iv, 8);
                }
            }
            row.addView(Ui.button(this, "Delete", Ui.OUTLINED, v -> {
                if (!unlocked()) return;
                AdminMessages.removeAt(this, index);
                build();
            }));
            Ui.add(messagesCard, row, 12);
        }
    }

    private void buildLockSection(LinearLayout root) {
        status = Ui.body(this, "", false);
        LinearLayout statusBox = Ui.card(this, root);
        statusBox.addView(status);

        Ui.add(root, Ui.rowTile(this, "Lock Now", R.drawable.ic_lock_tile,
                v -> { if (unlocked()) run(() -> Actions.lock(this, 0, "")); }), 12);
        Ui.add(root, Ui.rowTile(this, "Lock With a Message and Time", R.drawable.ic_lock_tile,
                v -> { if (unlocked()) askLock(); }), 8);
        if (Agent.prefs(this).getLong("lockUntil", 0) > System.currentTimeMillis()) {
            Ui.add(root, Ui.rowTile(this, "Unlock Now", R.drawable.ic_lock_tile,
                    v -> { if (unlocked()) run(() -> Actions.unlock(this)); }), 8);
        }
        Ui.add(root, Ui.rowTile(this, "Set Lock PIN", R.drawable.ic_lock_tile,
                v -> { if (unlocked()) askPin(); }), 8);
        Ui.add(root, Ui.rowTile(this, "Remove Lock Screen", R.drawable.ic_remove_tile,
                v -> { if (unlocked()) run(() -> Actions.clearPin(this)); }), 8);
        if (Actions.hasScreenLock(this) && !Actions.pinControlActive(this)) {
            Ui.add(root, Ui.rowTile(this, "Activate PIN Control", R.drawable.ic_lock_tile,
                    v -> { if (unlocked()) activatePinControl(); }), 8);
        }
    }

    private void buildAppsSection(LinearLayout root) {
        boolean kioskOn = Kiosk.active(this);
        if (kioskOn) {
            LinearLayout note = Ui.card(this, root);
            note.addView(Ui.body(this, "Home screen mode is on. Apps can only be allowed or blocked from the "
                    + "\"Allowed Apps\" row in the Home screen mode section while it's on.", true));
            Ui.add(root, Ui.rowTile(this, "Install an APK File", R.drawable.ic_apps_tile,
                    v -> { if (unlocked()) pickApk(); }), 16);
            return;
        }
        Ui.add(root, Ui.rowTile(this, "Install an APK File", R.drawable.ic_apps_tile,
                v -> { if (unlocked()) pickApk(); }), 12);
        if (!Agent.standalone(this)) {
            Ui.add(root, Ui.rowTile(this, "Show / Hide Apps", R.drawable.ic_apps_tile, v -> {
                if (!unlocked()) return;
                showingAppsPicker = !showingAppsPicker;
                build();
            }), 8);
        }
        LinearLayout appsPickerHolder = new LinearLayout(this);
        appsPickerHolder.setOrientation(LinearLayout.VERTICAL);
        Ui.add(root, appsPickerHolder, 8);
        if (showingAppsPicker) showAppsPicker(appsPickerHolder);
    }

    private void buildBrowserSection(LinearLayout root) {
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
    }

    private void buildKioskSection(LinearLayout root) {
        LinearLayout home = Ui.card(this, root);
        home.addView(Ui.titleText(this, "Home screen mode"));
        JSONArray storedAllowed = null;
        try {
            String stored = Agent.prefs(this).getString("policy", null);
            if (stored != null) storedAllowed = new JSONObject(stored).optJSONArray("allowed");
        } catch (Exception ignored) {
        }
        boolean canTurnOnHere = storedAllowed != null && storedAllowed.length() > 0;
        boolean active = Kiosk.active(this);
        home.addView(Ui.body(this, active ? "On: only allowed apps can be opened."
                : canTurnOnHere ? "Off."
                : "Off. Allow some apps below first (Allowed Apps) — otherwise there'd be nothing to open here.", true));
        if (active) {
            action(home, "Turn off home screen mode", Ui.OUTLINED, v -> {
                if (!unlocked()) return;
                Agent.prefs(this).edit().putBoolean("kioskPaused", true).apply();
                Agent.addEvent(this, "local", "Master code on phone: turned off home screen mode");
                Kiosk.clear(this);
                Kiosk.syncPreferredActivities(this, false, Agent.prefs(this).getBoolean("prefBrowser", false));
                Kiosk.announceChange(this);
                try {
                    stopLockTask();
                } catch (Exception ignored) {
                }
                build();
                toast("Off. The phone works normally now.");
            });
        } else if (canTurnOnHere) {
            action(home, "Turn on home screen mode", Ui.TONAL, v -> {
                if (!unlocked()) return;
                try {
                    if (Kiosk.paused(this)) {
                        Agent.prefs(this).edit().putBoolean("kioskPaused", false).apply();
                        Agent.addEvent(this, "local", "Master code on phone: turned on home screen mode");
                        new Thread(() -> {
                            PolicyApplier.applyStored(this);
                            AgentService.requestSync();
                            runOnUiThread(() -> {
                                build();
                                toast("Home screen mode on. The phone switches right away.");
                            });
                        }).start();
                    } else {
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
                    }
                } catch (Exception e) {
                    toast("Could not turn it on: " + e.getMessage());
                }
            });
        }

        // Always here, whether on or off -- this is the same allow/block list the regular Apps
        // section uses (PolicyApplier feeds it straight into Kiosk's own allowed-apps set), just
        // reachable without having to leave this section to get at it.
        Ui.add(root, Ui.rowTile(this, "Allowed Apps", R.drawable.ic_apps_tile, v -> {
            if (!unlocked()) return;
            showingKioskAllowedPicker = !showingKioskAllowedPicker;
            build();
        }), 16);
        LinearLayout appsPickerHolder = new LinearLayout(this);
        appsPickerHolder.setOrientation(LinearLayout.VERTICAL);
        Ui.add(root, appsPickerHolder, 8);
        if (showingKioskAllowedPicker) showAppsPicker(appsPickerHolder);
    }

    private void buildAppLockSection(LinearLayout root) {
        boolean appLockOn = Agent.prefs(this).getBoolean("appLock", false);
        LinearLayout statusBox = Ui.card(this, root);
        statusBox.addView(Ui.body(this, appLockOn
                ? "On: opening MDM Agent needs its own PIN, separate from the phone's screen lock."
                : "Off: anyone who opens this app can see it with no extra check.", true));
        if (appLockOn) {
            Ui.add(root, Ui.rowTile(this, "Change App PIN", R.drawable.ic_lock_tile,
                    v -> { if (unlocked()) promptNewAppPin(false); }), 12);
            Ui.add(root, Ui.rowTile(this, "Turn Off App Lock", R.drawable.ic_remove_tile, v -> {
                if (!unlocked()) return;
                AppPin.clear(this);
                Agent.prefs(this).edit().putBoolean("appLock", false).apply();
                Agent.addEvent(this, "local", "Master code on phone: turned off app lock");
                build();
            }), 8);
        } else {
            Ui.add(root, Ui.rowTile(this, "Turn On App Lock", R.drawable.ic_lock_tile,
                    v -> { if (unlocked()) promptNewAppPin(true); }), 12);
        }
    }

    private void buildSettingsMenuSection(LinearLayout root) {
        if (settingsMenuSub == null) {
            Ui.add(root, Ui.rowTile(this, "Settings Menu Icon Setup", R.drawable.ic_apps_tile,
                    v -> { settingsMenuSub = "iconsetup"; build(); }), 12);
            Ui.add(root, Ui.rowTile(this, "Settings Menu Categories", R.drawable.ic_apps_tile,
                    v -> { settingsMenuSub = "categories"; build(); }), 8);
            return;
        }
        Ui.add(root, Ui.button(this, "< Back", Ui.OUTLINED, v -> { settingsMenuSub = null; build(); }), 0);
        if ("iconsetup".equals(settingsMenuSub)) buildSettingsIconSetup(root);
        else buildSettingsCategories(root);
    }

    private void buildSettingsIconSetup(LinearLayout root) {
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
    }

    private void buildSettingsCategories(LinearLayout root) {
        LinearLayout categoriesCard = Ui.card(this, root);
        categoriesCard.addView(Ui.titleText(this, "Settings menu categories"));
        categoriesCard.addView(Ui.body(this, "Which rows show up on the fake Settings icon. Remove one if it's not "
                + "needed; Replace brings it back.", true));
        java.util.Set<String> hiddenCategories = SettingsMenu.hiddenCategories(this);
        for (String category : SettingsMenu.ORDER) {
            boolean hidden = hiddenCategories.contains(category);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView t = Ui.body(this, SettingsMenu.label(category) + (hidden ? "  (removed)" : ""), hidden);
            t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(t);
            row.addView(Ui.button(this, hidden ? "Replace" : "Remove", Ui.OUTLINED, v -> {
                if (!unlocked()) return;
                SettingsMenu.setHidden(this, category, !hidden);
                Agent.addEvent(this, "local", "Master code on phone: " + (!hidden ? "removed" : "replaced")
                        + " \"" + SettingsMenu.label(category) + "\" on the Settings menu");
                build();
            }));
            Ui.add(categoriesCard, row, 4);
        }
    }

    private void buildBlockingSection(LinearLayout root) {
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
    }

    private void buildNetworkSection(LinearLayout root) {
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
    }

    private void buildDeviceSection(LinearLayout root) {
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
                    // Matching is deliberately lenient here -- requiring the exact "Label (pkg)"
                    // suggestion string, with exact case and spacing, was rejecting real apps the
                    // person had clearly picked or typed correctly in every way that mattered.
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
                        for (java.util.Map.Entry<String, String> e : byDisplay.entrySet()) {
                            if (e.getKey().equalsIgnoreCase(typed)) {
                                pkg = e.getValue();
                                break;
                            }
                        }
                    }
                    if (pkg == null && !typed.isEmpty()) {
                        String needle = typed.toLowerCase();
                        String onlyMatch = null;
                        int matchCount = 0;
                        for (java.util.Map.Entry<String, String> e : byDisplay.entrySet()) {
                            if (e.getKey().toLowerCase().contains(needle)) {
                                matchCount++;
                                onlyMatch = e.getValue();
                            }
                        }
                        if (matchCount == 1) pkg = onlyMatch;
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
    // Shared by both the Apps section's "Show / Hide Apps" and the Home screen mode section's
    // "Allowed Apps" -- same underlying override list (PolicyApplier feeds it into Kiosk's own
    // allowed set too), just two different doors into it.
    private void showAppsPicker(LinearLayout box) {
        try {
            appsPickerPackages = PolicyApplier.collectPackages(this);
        } catch (Exception e) {
            toast("Could not list apps: " + e.getMessage());
            return;
        }
        box.removeAllViews();
        final EditText search = Ui.field(this, "Search apps");
        search.setText(appsPickerQuery);
        Ui.add(box, search, 8);
        LinearLayout gridHolder = new LinearLayout(this);
        gridHolder.setOrientation(LinearLayout.VERTICAL);
        Ui.add(box, gridHolder, 8);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                appsPickerQuery = s.toString();
                renderAppsGrid(gridHolder);
            }
        });
        renderAppsGrid(gridHolder);
    }

    private void renderAppsGrid(LinearLayout gridHolder) {
        gridHolder.removeAllViews();
        android.widget.GridLayout grid = Ui.tileGrid(this);
        android.content.pm.PackageManager pm = getPackageManager();
        JSONObject overrides = Agent.getOverrides(this);
        String needle = appsPickerQuery.trim().toLowerCase();
        for (int i = 0; i < appsPickerPackages.length(); i++) {
            JSONObject a = appsPickerPackages.optJSONObject(i);
            if (a == null) continue;
            final String pkg = a.optString("p", "");
            final String label = a.optString("l", pkg);
            if (!needle.isEmpty() && !label.toLowerCase().contains(needle) && !pkg.toLowerCase().contains(needle)) continue;
            String state = a.optBoolean("h") ? "hidden" : "allowed";
            if (overrides.has(pkg)) state = overrides.optString(pkg).equals("block") ? "blocked" : "allowed";
            android.graphics.drawable.Drawable icon;
            try {
                icon = pm.getApplicationIcon(pkg);
            } catch (Exception e) {
                icon = null;
            }
            Ui.addTile(grid, Ui.appTile(this, label + "\n(" + state + ")", icon, 72,
                    v -> promptAppMode(gridHolder, pkg, label)));
        }
        Ui.add(gridHolder, grid, 0);
    }

    private void promptAppMode(LinearLayout gridHolder, String pkg, String label) {
        if (!unlocked()) return;
        new AlertDialog.Builder(this)
                .setTitle(label)
                .setMessage(pkg)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Allow", (d, w) -> setApp(gridHolder, pkg, "allow"))
                .setPositiveButton("Block", (d, w) -> setApp(gridHolder, pkg, "block"))
                .show();
    }

    private void setApp(LinearLayout gridHolder, String pkg, String mode) {
        String err = PolicyApplier.applyOverride(this, pkg, mode);
        if (err != null) toast(err);
        else AgentService.requestSync();
        try {
            appsPickerPackages = PolicyApplier.collectPackages(this);
        } catch (Exception ignored) {
        }
        renderAppsGrid(gridHolder);
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
