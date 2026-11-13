package com.familymdm.agent;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Locale;

/**
 * Administrator → Rules: the dashboard's settings, on the phone, in the same plain words.
 *
 * Connected phone: the dashboard sends its settings with every check-in (stored as "dashConfig").
 * A change made here is kept as a numbered "configPatch", sent with the next check-in (requested at
 * once), and the dashboard takes it in and sends the new policy back -- so the phone and the
 * dashboard always agree, and the dashboard's log shows "On the phone: ...". If the dashboard has
 * "Only control from the dashboard" on, it ignores these changes.
 *
 * Offline phone (no dashboard): the same settings are kept on the phone ("localExtras") and added
 * to the policy the phone works out for itself (see LocalConfig.refresh). Settings that need the
 * dashboard (alerts, logs it keeps, app groups) are not shown then.
 */
final class PhoneRules {
    private PhoneRules() {}

    static final String[] SUBS = {"setups", "web", "time", "calls", "apps", "safety", "more"};
    static final String[] SUB_LABELS = {"Ready-made", "Websites", "Screen time", "Calls", "App groups", "Safety", "More"};

    private static final String BLOCKLISTS = "https://github.com/abegumbo-ui/MDM/releases/download/blocklists";

    /* ------------------------------------------------------------------ state ------------------------------------------------------------------ */

    private static boolean offline(Context c) {
        return Agent.standalone(c);
    }

    /** The settings as they are now, with any change made here that the dashboard has not confirmed yet. */
    static JSONObject current(Context c) {
        JSONObject out;
        try {
            out = new JSONObject(Agent.prefs(c).getString(offline(c) ? "localExtras" : "dashConfig", "{}"));
            if (!offline(c)) {
                JSONObject pend = new JSONObject(Agent.prefs(c).getString("configPatchValues", "{}"));
                for (Iterator<String> it = pend.keys(); it.hasNext(); ) {
                    String k = it.next();
                    out.put(k, pend.get(k));
                }
            }
        } catch (Exception e) {
            out = new JSONObject();
        }
        return out;
    }

    /** Saves changes (key → value) and gets them working: a check-in now, or the phone's own policy now. */
    static void set(Context c, JSONObject values, String what) {
        try {
            android.content.SharedPreferences p = Agent.prefs(c);
            if (offline(c)) {
                JSONObject extras = new JSONObject(p.getString("localExtras", "{}"));
                for (Iterator<String> it = values.keys(); it.hasNext(); ) {
                    String k = it.next();
                    extras.put(k, values.get(k));
                }
                p.edit().putString("localExtras", extras.toString()).apply();
                final Context app = c.getApplicationContext();
                new Thread(() -> {
                    try {
                        LocalConfig.refresh(app);
                    } catch (Exception ignored) {
                    }
                }, "mdm-rules").start();
            } else {
                JSONObject pend = new JSONObject(p.getString("configPatchValues", "{}"));
                for (Iterator<String> it = values.keys(); it.hasNext(); ) {
                    String k = it.next();
                    pend.put(k, values.get(k));
                }
                long rev = Math.max(p.getLong("configPatchRev", 0), p.getLong("configPatchAck", 0)) + 1;
                p.edit().putString("configPatchValues", pend.toString()).putLong("configPatchRev", rev).apply();
                AgentService.requestSync();
            }
            Agent.addEvent(c, "local", "Changed on the phone (Administrator → Rules): " + what);
        } catch (Exception ignored) {
        }
    }

    private static void set1(Context c, String key, Object value, String what) {
        try {
            JSONObject v = new JSONObject();
            v.put(key, value);
            set(c, v, what);
        } catch (Exception ignored) {
        }
    }

    /** For the check-in: the change waiting to go up, or null. */
    static JSONObject pendingPatch(Context c) {
        try {
            JSONObject pend = new JSONObject(Agent.prefs(c).getString("configPatchValues", "{}"));
            if (pend.length() == 0) return null;
            JSONObject o = new JSONObject();
            o.put("rev", Agent.prefs(c).getLong("configPatchRev", 1));
            o.put("values", pend);
            return o;
        } catch (Exception e) {
            return null;
        }
    }

    /** After a check-in: keep the dashboard's settings; forget the change once the dashboard took it. */
    static void adopt(Context c, JSONObject reply) {
        android.content.SharedPreferences p = Agent.prefs(c);
        android.content.SharedPreferences.Editor e = p.edit();
        JSONObject cfg = reply.optJSONObject("config");
        if (cfg != null) e.putString("dashConfig", cfg.toString());
        long ack = reply.optLong("configPatchRev", 0);
        long mine = p.getLong("configPatchRev", 0);
        if (ack > 0) {
            e.putLong("configPatchAck", ack);
            if (ack == mine) e.remove("configPatchValues"); // the dashboard took exactly this change
            else if (ack > mine && p.contains("configPatchValues")) {
                // The dashboard's count is ahead of ours (this app was reset or reinstalled), so it
                // ignored our change: number it after the dashboard's and send it again.
                e.putLong("configPatchRev", ack + 1);
                AgentService.requestSync();
            }
        }
        e.apply();
    }

    /** Offline mode: the Rules settings that work with no dashboard go straight into the policy. */
    static void addOfflineExtras(Context c, JSONObject policy) {
        try {
            JSONObject x = new JSONObject(Agent.prefs(c).getString("localExtras", "{}"));
            String[] pass = {"safeSearch", "youtubeDns", "blockImages", "pictureGuard", "blockImageSearch", "blockedWords", "inAppBrowsers", "blockWaStatus",
                    "blockWaChannels", "screenTime", "callsIn", "callsOut", "pauseUntil", "freezeUpdates", "disableBiometricUnlock", "blockedParts", "blockedScreens", "youtubeSignIn", "protectScreens", "internetOffUntil"};
            for (String k : pass) if (x.has(k)) policy.put(k, x.get(k));
            if (x.has("browserFilter")) {
                policy.put("browserFilter", x.getString("browserFilter"));
                policy.put("browserFilterBase", BLOCKLISTS);
                // The "Open" level closes only the kinds chosen on the phone (the policy reads them from filterExtras).
                if ("custom".equals(x.getString("browserFilter"))) policy.put("filterExtras", x.has("filterKinds") ? x.get("filterKinds") : new org.json.JSONArray().put("adult"));
            }
        } catch (Exception ignored) {
        }
    }

    /* ------------------------------------------------------------------ screens ------------------------------------------------------------------ */

    /** Builds one tab of Administrator → Rules. rebuild redraws the screen after a change. */
    static void build(Activity a, LinearLayout root, String sub, Runnable rebuild) {
        JSONObject cfg = current(a);
        boolean off = offline(a);
        if (!off && cfg.length() == 0) {
            LinearLayout card = Ui.card(a, root);
            card.addView(Ui.titleText(a, "Waiting for the dashboard"));
            card.addView(Ui.body(a, "This phone has not received its settings from the dashboard yet. Connect to the internet and tap \"Sync with dashboard now\" on Overview.", true));
            return;
        }
        if (!off && cfg.optBoolean("phoneAdminLocked", false)) {
            LinearLayout card = Ui.card(a, root);
            card.addView(Ui.body(a, "\"Only control from the dashboard\" is on, so changes here are not taken. Change them on the dashboard instead.", false));
        }
        switch (sub) {
            case "web": web(a, root, cfg, rebuild); break;
            case "time": time(a, root, cfg, rebuild); break;
            case "calls": calls(a, root, cfg, rebuild, off); break;
            case "apps": apps(a, root, cfg, rebuild, off); break;
            case "safety": safety(a, root, cfg, rebuild, off); break;
            case "more": more(a, root, cfg, rebuild, off); break;
            default: setups(a, root, rebuild, off);
        }
        if (!off) {
            LinearLayout note = Ui.card(a, root);
            note.addView(Ui.body(a, "Changes made here go to the dashboard within a minute (it shows them as \"On the phone\"), and the same switches on the dashboard change them here.", true));
        }
    }

    /** "What's on for this phone": every rule in force, one plain line each (same as the dashboard's Overview). */
    static void whatsOn(Activity a, LinearLayout root) {
        JSONObject c = current(a);
        if (c.length() == 0) return;
        java.util.List<String> rows = new java.util.ArrayList<>();
        long now = System.currentTimeMillis();
        String lvl = c.optString("browserFilter", "off");
        rows.add("Websites: " + ("strict".equals(lvl) ? "Strict filter" : "moderate".equals(lvl) ? "Moderate filter" : "custom".equals(lvl) ? "Open (only the chosen kinds of site are closed)" : "Approved sites only")
                + (c.optBoolean("safeSearch") ? ", SafeSearch" : "") + (!"off".equals(c.optString("youtubeDns", "off")) ? ", YouTube restricted" : "")
                + (c.optBoolean("blockAds") ? ", ads blocked" : ""));
        if (c.optLong("pauseUntil", 0) > now) rows.add("Web filtering paused for a while");
        if (c.optLong("internetOffUntil", 0) > now) rows.add("Internet off for a while");
        JSONObject st = c.optJSONObject("screenTime");
        if (st != null) {
            if (st.optInt("dailyLimit", 0) > 0) rows.add("Daily screen time: " + fmt(st.optInt("dailyLimit")));
            JSONObject bed = st.optJSONObject("bedtime");
            if (bed != null) rows.add("Bedtime " + bed.optString("from") + " to " + bed.optString("to"));
            JSONObject sch = st.optJSONObject("schoolTime");
            if (sch != null) rows.add("School time " + sch.optString("from") + " to " + sch.optString("to"));
        }
        JSONArray groups = c.optJSONArray("blockAppGroups");
        if (groups != null && groups.length() > 0) rows.add(groups.length() + " app group" + (groups.length() == 1 ? "" : "s") + " blocked");
        if (c.optBoolean("approveNew")) rows.add("New apps wait for your OK");
        JSONArray parts = c.optJSONArray("blockedParts");
        if (parts != null && parts.length() > 0) rows.add(parts.length() + " part" + (parts.length() == 1 ? "" : "s") + " of apps blocked");
        if (c.optBoolean("blockWaStatus") || c.optBoolean("blockWaChannels") || c.optBoolean("waTextOnly")) rows.add("WhatsApp limited");
        if (c.optBoolean("messageAlerts")) rows.add("Message alerts on");
        if (c.optBoolean("recordSearches")) rows.add("Searches kept");
        if (c.optBoolean("recordVideos")) rows.add("YouTube videos listed");
        if (c.optBoolean("nudityAlerts")) rows.add("Photo alerts on");
        if (c.optInt("locationEvery", 0) > 0) rows.add("Location every " + c.optInt("locationEvery") + " minutes");
        LinearLayout card = Ui.card(a, root);
        card.addView(Ui.titleText(a, "What's on for this phone"));
        for (String r : rows) Ui.add(card, Ui.body(a, "•  " + r, false), 4);
        card.addView(Ui.body(a, "Change them under Rules.", true));
    }

    /* ---- Ready-made setups (the same as the dashboard's) ---- */

    private static void setups(Activity a, LinearLayout root, Runnable rebuild, boolean off) {
        LinearLayout card = Ui.card(a, root);
        card.addView(Ui.titleText(a, "Ready-made setups"));
        card.addView(Ui.body(a, "Not sure what to switch on? Pick the one that fits. It sets many rules at once; you can still change any single one afterwards.", true));
        String[][] L = {
                {"email", "Email only", "Only email apps (Gmail, Outlook, Yahoo Mail, Samsung Email) can use the internet. Calls and texts work as usual. Browsers open approved sites only; new apps wait for you."},
                {"young", "Young child (under 10)", "Only sites you approve. No pictures on web pages, no image search, no web pages inside apps. Every app group blocked; new apps wait for you. WhatsApp Status and Channels closed. Message alerts on."},
                {"teen", "Teen", "Strict filter (adult, gambling, drugs, scam, piracy sites blocked). SafeSearch, YouTube Restricted Mode, no image search. Dating apps blocked, new apps wait for you. Browser pages that apps open are closed. Message alerts on. Ads blocked."},
                {"kosher", "Kosher phone", "Only sites you approve, no pictures on web pages, no image search, no web pages inside apps. Social media, video, games, dating and shopping apps blocked; new apps wait for you. WhatsApp chats and calls work; Status and Channels closed."},
                {"strictest", "Strictest", "Approved sites only, SafeSearch, strict YouTube, no pictures, no image search, every app group blocked, new apps wait for you."},
                {"recommended", "Recommended", "Strict filter, SafeSearch, YouTube Restricted Mode, no image search, social media and dating apps blocked, new apps wait for you. Ads blocked."},
                {"light", "Light", "Moderate filter, SafeSearch, YouTube Restricted Mode, dating apps blocked. Everything else open."}};
        for (String[] l : L) {
            LinearLayout row = Ui.card(a, root);
            TextView t = Ui.titleText(a, l[1]);
            row.addView(t);
            row.addView(Ui.body(a, l[2], true));
            Ui.add(row, Ui.button(a, "Use this", Ui.TONAL, v -> Ui.alertDialog(a)
                    .setTitle("Use \"" + l[1] + "\"?")
                    .setMessage(l[2])
                    .setPositiveButton("Use it", (d, w) -> {
                        set(a, setupValues(l[0], off), "ready-made setup " + l[1]);
                        Toast(a, "Set to " + l[1] + ".");
                        rebuild.run();
                    })
                    .setNegativeButton("Cancel", null)
                    .show()), 8);
        }
    }

    static JSONObject setupValues(String which, boolean off) {
        JSONObject v = new JSONObject();
        try {
            boolean young = which.equals("young"), kosher = which.equals("kosher"), strictest = which.equals("strictest");
            boolean teen = which.equals("teen"), rec = which.equals("recommended");
            boolean approvedOnly = young || kosher || strictest;
            v.put("browserFilter", approvedOnly ? "off" : (teen || rec) ? "strict" : "moderate");
            v.put("safeSearch", true);
            v.put("youtubeDns", approvedOnly ? "strict" : "moderate");
            v.put("blockImages", approvedOnly);
            v.put("blockImageSearch", !which.equals("light"));
            if (!off) {
                JSONArray groups = new JSONArray();
                if (approvedOnly) for (String g : new String[]{"social", "games", "video", "dating", "shopping"}) groups.put(g);
                else if (rec) { groups.put("social"); groups.put("dating"); }
                else groups.put("dating");
                v.put("blockAppGroups", groups);
                v.put("approveNew", !which.equals("light"));
                JSONObject chrome = new JSONObject();
                chrome.put("noIncognito", !which.equals("light"));
                v.put("chromePolicy", chrome);
                if (young || teen) v.put("messageAlerts", true);
            if (teen || rec) v.put("blockAds", true);
            }
            if (young || kosher) { v.put("blockWaStatus", true); v.put("blockWaChannels", true); v.put("inAppBrowsers", "all"); }
            boolean email = which.equals("email");
            v.put("internetOnlyApps", email);
            if (email) {
                v.put("browserFilter", "off");
                v.put("blockImages", false);
                v.put("youtubeDns", "strict");
                JSONArray mail = new JSONArray();
                for (String p : new String[]{"com.google.android.gm", "com.microsoft.office.outlook", "com.yahoo.mobile.client.android.mail", "com.samsung.android.email.provider", "com.android.email"}) mail.put(p);
                v.put("internetApps", mail);
                if (!off) v.put("blockAppGroups", new JSONArray());
            }
            else if (teen) v.put("inAppBrowsers", "tabs");
        } catch (Exception ignored) {
        }
        return v;
    }

    /* ---- Websites ---- */

    private static void web(Activity a, LinearLayout root, JSONObject c, Runnable rebuild) {
        LinearLayout card = Ui.card(a, root);
        card.addView(Ui.titleText(a, "Websites"));
        choice(a, card, c, "browserFilter", "Filter level", "What browsers and apps may open.",
                new String[]{"off", "moderate", "strict", "custom"},
                new String[]{"Approved sites only", "Moderate: blocks adult, gambling, drugs, scams, piracy", "Strict: Moderate plus social media", "Open: close only the kinds I choose"}, "off", rebuild);
        if ("custom".equals(c.optString("browserFilter", "off"))) kinds(a, card, c, rebuild);
        toggle(a, card, c, "safeSearch", "Force SafeSearch", "Google, Bing and DuckDuckGo show filtered results only, in every browser and app.", rebuild);
        choice(a, card, c, "youtubeDns", "YouTube Restricted Mode", "YouTube's own filter, everywhere, the YouTube app included.",
                new String[]{"off", "moderate", "strict"}, new String[]{"Off", "Moderate", "Strict"}, "off", rebuild);
        toggle(a, card, c, "blockAds", "Block ads", "Ad servers are blocked in every app and browser (with Moderate or Strict). A few apps may show empty boxes where ads were.", rebuild);
        toggle(a, card, c, "blockImageSearch", "Block image search", "The \"Images\" tab of Google, Bing and DuckDuckGo is refused.", rebuild);
        toggle(a, card, c, "blockImages", "Block pictures on web pages", "Pages load without pictures in our browsers (in Chrome only with Inspection on, set on the dashboard).", rebuild);
        choice(a, card, c, "inAppBrowsers", "Web pages inside apps", "Links opened inside Instagram, Gmail and other apps.",
                new String[]{"off", "tabs", "all"}, new String[]{"Allowed", "Close browser pages that apps open", "Close every web page inside apps"}, "off", rebuild);
        approvedSites(a, card, c, rebuild);
        filterSchedule(a, card, c, rebuild);
        words(a, card, c, "blockedWords", "Blocked words", "Searches and site names containing these words are blocked. One per line.", rebuild);

        LinearLayout net = Ui.card(a, root);
        long offUntil = c.optLong("internetOffUntil", 0);
        boolean netOff = offUntil > System.currentTimeMillis();
        net.addView(Ui.titleText(a, netOff ? "The internet is off" : "Turn the internet off"));
        net.addView(Ui.body(a, netOff ? "Until " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date(offUntil)) + ". Calls and texts still work."
                : "No app can use the internet for a while. Calls and texts still work.", true));
        if (netOff) Ui.add(net, Ui.button(a, "Turn the internet back on", Ui.FILLED, v -> { set1(a, "internetOffUntil", 0, "internet back on"); rebuild.run(); }), 8);
        else for (int m : new int[]{30, 60, 120}) {
            Ui.add(net, Ui.button(a, m < 60 ? m + " minutes" : (m / 60) + (m == 60 ? " hour" : " hours"), Ui.TONAL, v -> {
                set1(a, "internetOffUntil", System.currentTimeMillis() + m * 60000L, "internet off for " + m + " minutes");
                rebuild.run();
            }), 8);
        }

        LinearLayout pause = Ui.card(a, root);
        long until = c.optLong("pauseUntil", 0);
        boolean paused = until > System.currentTimeMillis();
        pause.addView(Ui.titleText(a, paused ? "Web filtering is paused" : "Pause web filtering"));
        pause.addView(Ui.body(a, paused ? "Until " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date(until)) + ". It comes back by itself."
                : "Lets every site through for a while, for example while you use the phone yourself. Blocked apps stay blocked.", true));
        if (paused) Ui.add(pause, Ui.button(a, "Turn filtering back on now", Ui.FILLED, v -> { set1(a, "pauseUntil", 0, "filtering back on"); rebuild.run(); }), 8);
        else for (int m : new int[]{15, 60, 180}) {
            Ui.add(pause, Ui.button(a, m < 60 ? m + " minutes" : (m / 60) + (m == 60 ? " hour" : " hours"), Ui.TONAL, v -> {
                set1(a, "pauseUntil", System.currentTimeMillis() + m * 60000L, "filtering paused for " + m + " minutes");
                rebuild.run();
            }), 8);
        }
    }

    /* ---- Screen time ---- */

    private static void time(Activity a, LinearLayout root, JSONObject c, Runnable rebuild) {
        JSONObject st = c.optJSONObject("screenTime");
        if (st == null) st = new JSONObject();
        final JSONObject screen = st;
        LinearLayout card = Ui.card(a, root);
        card.addView(Ui.titleText(a, "Screen time"));
        int limit = screen.optInt("dailyLimit", 0);
        int[] mins = {0, 30, 60, 90, 120, 180, 240, 300};
        String[] labels = new String[mins.length];
        for (int i = 0; i < mins.length; i++) labels[i] = mins[i] == 0 ? "No daily limit" : fmt(mins[i]) + " a day";
        int sel = 0;
        for (int i = 0; i < mins.length; i++) if (mins[i] == limit) sel = i;
        final int selected = sel;
        row(a, card, "Daily limit", "When it is used up, only always-allowed apps (phone, maps...) open until tomorrow.", labels[sel], v ->
                Ui.alertDialog(a).setTitle("Daily limit").setSingleChoiceItems(labels, selected, (d, w) -> {
                    d.dismiss();
                    putIn(screen, "dailyLimit", mins[w]);
                    set1(a, "screenTime", screen, "daily limit " + labels[w]);
                    rebuild.run();
                }).show());

        JSONObject bed = screen.optJSONObject("bedtime");
        boolean bedOn = bed != null;
        Switch sw = sw(a, card, "Bedtime", bedOn ? "Every night " + bed.optString("from") + " to " + bed.optString("to") + ": only always-allowed apps open." : "Off.", bedOn);
        sw.setOnCheckedChangeListener((b, on) -> {
            try {
                if (on) {
                    JSONObject nb = new JSONObject();
                    nb.put("days", new JSONArray(new int[]{0, 1, 2, 3, 4, 5, 6}));
                    nb.put("from", "21:00");
                    nb.put("to", "07:00");
                    screen.put("bedtime", nb);
                } else screen.put("bedtime", JSONObject.NULL);
                set1(a, "screenTime", screen, on ? "bedtime 21:00-07:00" : "bedtime off");
            } catch (Exception ignored) {
            }
            rebuild.run();
        });
        if (bedOn) {
            for (String end : new String[]{"from", "to"}) {
                String cur = bed.optString(end, end.equals("from") ? "21:00" : "07:00");
                Ui.add(card, Ui.button(a, (end.equals("from") ? "Starts " : "Ends ") + cur, Ui.OUTLINED, v -> {
                    int h = Integer.parseInt(cur.substring(0, 2)), m = Integer.parseInt(cur.substring(3, 5));
                    new TimePickerDialog(a, (tp, hh, mm) -> {
                        try {
                            bed.put(end, String.format(Locale.US, "%02d:%02d", hh, mm));
                            screen.put("bedtime", bed);
                            set1(a, "screenTime", screen, "bedtime " + bed.optString("from") + "-" + bed.optString("to"));
                        } catch (Exception ignored) {
                        }
                        rebuild.run();
                    }, h, m, true).show();
                }), 8);
            }
        }

        int wl = screen.optInt("weekendLimit", 0);
        int wsel = 0;
        for (int i = 0; i < mins.length; i++) if (mins[i] == wl) wsel = i;
        final int wselected = wsel;
        String[] wlabels = labels.clone();
        wlabels[0] = "Same as every day";
        row(a, card, "Weekends (Saturday and Sunday)", "A different limit on those days. Other days can be chosen on the dashboard.", wlabels[wsel], v ->
                Ui.alertDialog(a).setTitle("Weekend limit").setSingleChoiceItems(wlabels, wselected, (d, w) -> {
                    d.dismiss();
                    putIn(screen, "weekendLimit", mins[w]);
                    set1(a, "screenTime", screen, "weekend limit " + wlabels[w]);
                    rebuild.run();
                }).show());

        JSONObject school = screen.optJSONObject("schoolTime");
        Switch ss = sw(a, card, "School time", school != null ? school.optString("from") + " to " + school.optString("to") + " on school days: only the phone, texts and never-counted apps open." : "Off.", school != null);
        ss.setOnCheckedChangeListener((b, on) -> {
            try {
                if (on) {
                    JSONObject ns = new JSONObject();
                    ns.put("days", new JSONArray(new int[]{1, 2, 3, 4, 5}));
                    ns.put("from", "08:00");
                    ns.put("to", "15:00");
                    screen.put("schoolTime", ns);
                } else screen.put("schoolTime", JSONObject.NULL);
                set1(a, "screenTime", screen, on ? "school time 08:00-15:00 Monday to Friday" : "school time off");
            } catch (Exception ignored) {
            }
            rebuild.run();
        });

        LinearLayout extra = Ui.card(a, root);
        extra.addView(Ui.titleText(a, "Extra time today"));
        extra.addView(Ui.body(a, "More time on top of the daily limit, for today only.", true));
        for (int m : new int[]{15, 30, 60}) {
            Ui.add(extra, Ui.button(a, "Give " + m + " minutes", Ui.TONAL, v -> {
                ScreenTime.grant(a, m, "from the phone's Administrator screen");
                Toast(a, m + " minutes added.");
            }), 8);
        }
        appLimits(a, root, screen, rebuild);
    }

    /** A daily limit for single apps: each app with an icon, its limit, tap to change. */
    private static void appLimits(Activity a, LinearLayout root, JSONObject screen, Runnable rebuild) {
        LinearLayout card = Ui.card(a, root);
        card.addView(Ui.titleText(a, "Limits for single apps"));
        card.addView(Ui.body(a, "When an app has used its time today, it is put away until tomorrow.", true));
        JSONObject limits = screen.optJSONObject("appLimits");
        final JSONObject cur = limits == null ? new JSONObject() : limits;
        android.content.pm.PackageManager pm = a.getPackageManager();
        java.util.List<android.content.pm.ResolveInfo> apps = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        java.util.TreeMap<String, String> byLabel = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (android.content.pm.ResolveInfo r : apps) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(a.getPackageName())) continue;
            byLabel.put(String.valueOf(r.loadLabel(pm)), pkg);
        }
        int[] mins = {0, 15, 30, 45, 60, 90, 120, 180};
        String[] labels = new String[mins.length];
        for (int i = 0; i < mins.length; i++) labels[i] = mins[i] == 0 ? "No limit" : fmt(mins[i]) + " a day";
        for (java.util.Map.Entry<String, String> e : byLabel.entrySet()) {
            final String pkg = e.getValue(), name = e.getKey();
            int m = cur.optInt(pkg, 0);
            int sel = 0;
            for (int i = 0; i < mins.length; i++) if (mins[i] == m) sel = i;
            final int selected = sel;
            Ui.add(card, Ui.button(a, name + ":  " + (m == 0 ? "no limit" : fmt(m)), m == 0 ? Ui.OUTLINED : Ui.TONAL, v ->
                    Ui.alertDialog(a).setTitle(name).setSingleChoiceItems(labels, selected, (d, w) -> {
                        d.dismiss();
                        try {
                            if (mins[w] == 0) cur.remove(pkg); else cur.put(pkg, mins[w]);
                            screen.put("appLimits", cur);
                        } catch (Exception ignored) {
                        }
                        set1(a, "screenTime", screen, name + " " + labels[w]);
                        rebuild.run();
                    }).show()), 4);
        }
    }

    /* ---- Calls ---- */

    private static void calls(Activity a, LinearLayout root, JSONObject c, Runnable rebuild, boolean off) {
        LinearLayout card = Ui.card(a, root);
        card.addView(Ui.titleText(a, "Calls"));
        String[] modes = {"all", "contacts", "list"};
        String[] labels = {"Anyone", "Only contacts and allowed numbers", "Only allowed numbers"};
        choice(a, card, c, "callsIn", "Who can call this phone", "Calls from anyone else are rejected. Emergency numbers always work.", modes, labels, "all", rebuild);
        choice(a, card, c, "callsOut", "Whom this phone can call", "Emergency numbers always work.", modes, labels, "all", rebuild);
        JSONArray nums = c.optJSONArray("callNumbers");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; nums != null && i < nums.length(); i++) {
            JSONObject n = nums.optJSONObject(i);
            if (n == null) continue;
            sb.append(sb.length() == 0 ? "" : "\n").append(n.optString("name").isEmpty() ? "" : n.optString("name") + " ").append(n.optString("number"));
        }
        int count = nums == null ? 0 : nums.length();
        row(a, card, "Allowed numbers", "One per line, a name and the number, for example: Mom 052-123-4567", count == 0 ? "None" : count + (count == 1 ? " number" : " numbers"), v -> {
            EditText f = new EditText(a);
            f.setMinLines(5);
            f.setText(sb.toString());
            new AlertDialog.Builder(a).setTitle("Allowed numbers").setView(f).setPositiveButton("Save", (d, w) -> {
                JSONArray out = new JSONArray();
                for (String line : f.getText().toString().split("\n")) {
                    String l = line.trim();
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.*?)\\s*([+*]?[0-9][0-9 ()\\-]{2,24})$").matcher(l);
                    if (!m.find()) continue;
                    try {
                        JSONObject n = new JSONObject();
                        n.put("name", m.group(1).trim());
                        n.put("number", m.group(2).trim());
                        out.put(n);
                    } catch (Exception ignored) {
                    }
                }
                set1(a, "callNumbers", out, "allowed numbers (" + out.length() + ")");
                rebuild.run();
            }).setNegativeButton("Cancel", null).show();
        });
        if (off) return;
        LinearLayout logs = Ui.card(a, root);
        logs.addView(Ui.titleText(a, "Shown on the dashboard"));
        toggle(a, logs, c, "callLog", "Call log", "Calls made and received.", rebuild);
        toggle(a, logs, c, "textLog", "Texts log", "Who texted, and when.", rebuild);
        toggle(a, logs, c, "textContent", "Include what the texts say", "Only with the texts log on.", rebuild);
        toggle(a, logs, c, "reportContacts", "The phone's contacts", "So you can allow calls from them with one tap.", rebuild);
        toggle(a, logs, c, "syncContacts", "Put the allowed numbers into the phone's contacts", "So they show by name.", rebuild);
    }

    /* ---- App groups ---- */

    /** "Tap to block": pick a part of an app on this phone (see PartBlocker), and the list of what is blocked. */
    static void tapToBlock(Activity a, LinearLayout root, Runnable rebuild) {
        LinearLayout card = Ui.card(a, root);
        card.addView(Ui.titleText(a, "Tap to block"));
        card.addView(Ui.body(a, "Block one part of an app instead of the whole app: a button, a tab, a feed (YouTube Shorts, Instagram Reels, WhatsApp Status...). "
                + "Tap Start: a bar stays at the bottom of every screen. Open the app and go to the screen, then press ✓ to block that whole screen, "
                + "or \"Pick a part\" and tap one button or tab to block just that.", true));
        Ui.add(card, Ui.button(a, "Start", Ui.FILLED, v -> {
            String err = PartBlocker.startPicking(a);
            if (err != null) Toast(a, err);
            else {
                Toast(a, "Now open the app. A Pick button is at the bottom of the screen.");
                Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                a.startActivity(home);
            }
        }), 8);
        // Quick blocks: known tabs of popular apps, found by their button's (English) name.
        String[][] quick = {
                {"YouTube: Shorts", "com.google.android.youtube", "Shorts"},
                {"Instagram: Reels", "com.instagram.android", "Reels"},
                {"Instagram: Search and explore", "com.instagram.android", "Search and explore"},
                {"Facebook: Reels", "com.facebook.katana", "Reels"},
                {"Google Maps: Explore", "com.google.android.apps.maps", "Explore"},
                {"Snapchat: Spotlight", "com.snapchat.android", "Spotlight"}};
        JSONArray have = current(a).optJSONArray("blockedParts");
        for (String[] q : quick) {
            boolean on = false;
            for (int k = 0; have != null && k < have.length(); k++) {
                JSONObject x = have.optJSONObject(k);
                if (x != null && q[1].equals(x.optString("pkg")) && q[2].equals(x.optString("desc"))) on = true;
            }
            final boolean was = on;
            Ui.add(card, Ui.button(a, (on ? "✓ " : "Quick block: ") + q[0], on ? Ui.OUTLINED : Ui.TONAL, v -> {
                try {
                    JSONArray cur = current(a).optJSONArray("blockedParts");
                    JSONArray next = new JSONArray();
                    for (int k = 0; cur != null && k < cur.length(); k++) {
                        JSONObject x = cur.optJSONObject(k);
                        if (x != null && !(q[1].equals(x.optString("pkg")) && q[2].equals(x.optString("desc")))) next.put(x);
                    }
                    if (!was) {
                        JSONObject p = new JSONObject();
                        p.put("pkg", q[1]);
                        p.put("desc", q[2]);
                        p.put("mode", "cover");
                        p.put("label", q[0]);
                        next.put(p);
                    }
                    set1(a, "blockedParts", next, (was ? "allowed " : "covered ") + q[0]);
                } catch (Exception ignored) {
                }
                rebuild.run();
            }), 6);
        }
        for (String sg : new java.util.TreeSet<>(PartBlocker.blockedScreens(a))) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.VERTICAL);
            row.addView(Ui.body(a, "Screen: " + PartBlocker.signatureLabel(sg) + "  (" + sg.substring(0, sg.indexOf(PartBlocker.SEP)) + ")", false));
            Ui.add(row, Ui.button(a, "Remove", Ui.OUTLINED, v -> {
                java.util.Set<String> s = PartBlocker.blockedScreens(a);
                s.remove(sg);
                PartBlocker.setBlockedScreens(a, s, "unblocked the screen " + PartBlocker.signatureLabel(sg));
                rebuild.run();
            }), 4);
            Ui.add(card, row, 10);
        }
        for (String t : new java.util.TreeSet<>(PartBlocker.blockedTitles(a))) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.VERTICAL);
            row.addView(Ui.body(a, "Screen: " + t.substring(t.indexOf('|') + 1) + "  (" + t.substring(0, t.indexOf('|')) + ")", false));
            Ui.add(row, Ui.button(a, "Remove", Ui.OUTLINED, v -> {
                java.util.Set<String> s = PartBlocker.blockedTitles(a);
                s.remove(t);
                Agent.putSet(a, "blockedTitles", s);
                rebuild.run();
            }), 4);
            Ui.add(card, row, 10);
        }
        JSONArray parts = current(a).optJSONArray("blockedParts");
        for (int i = 0; parts != null && i < parts.length(); i++) {
            JSONObject p = parts.optJSONObject(i);
            if (p == null) continue;
            final String id = p.optString("id");
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.VERTICAL);
            row.addView(Ui.body(a, p.optString("label") + "  (" + ("close".equals(p.optString("mode")) ? "screen closed" : "covered") + ")", false));
            Ui.add(row, Ui.button(a, "Remove", Ui.OUTLINED, v -> {
                JSONArray cur = current(a).optJSONArray("blockedParts");
                JSONArray next = new JSONArray();
                for (int k = 0; cur != null && k < cur.length(); k++) {
                    JSONObject x = cur.optJSONObject(k);
                    if (x != null && !id.equals(x.optString("id"))) next.put(x);
                }
                set1(a, "blockedParts", next, "removed " + p.optString("label"));
                rebuild.run();
            }), 4);
            Ui.add(card, row, 10);
        }
    }

    private static void apps(Activity a, LinearLayout root, JSONObject c, Runnable rebuild, boolean off) {
        tapToBlock(a, root, rebuild);
        LinearLayout card = Ui.card(a, root);
        card.addView(Ui.titleText(a, "Apps"));
        if (!off) {
            String[][] groups = {{"social", "Social media apps"}, {"games", "Games"}, {"video", "Video and streaming apps"}, {"dating", "Dating apps"}, {"shopping", "Shopping apps"}};
            JSONArray cur = c.optJSONArray("blockAppGroups");
            for (String[] g : groups) {
                boolean on = contains(cur, g[0]);
                Switch s = sw(a, card, "Block all " + g[1].toLowerCase(Locale.ROOT), "An app you set to Allow stays allowed.", on);
                s.setOnCheckedChangeListener((b, now) -> {
                    JSONArray next = new JSONArray();
                    JSONArray base = current(a).optJSONArray("blockAppGroups");
                    for (int i = 0; base != null && i < base.length(); i++) if (!base.optString(i).equals(g[0])) next.put(base.optString(i));
                    if (now) next.put(g[0]);
                    set1(a, "blockAppGroups", next, g[1] + (now ? " blocked" : " allowed"));
                    rebuild.run();
                });
            }
            toggle(a, card, c, "approveNew", "New apps wait for you", "A newly installed app stays hidden until you allow it.", rebuild);
            toggle(a, card, c, "blockUnlisted", "Hide apps you have not allowed", "Only apps set to Allow can be opened.", rebuild);
        }
        toggle(a, card, c, "blockWaStatus", "WhatsApp: close Status", "Watching other people's statuses. Chats and calls keep working.", rebuild);
        toggle(a, card, c, "blockWaChannels", "WhatsApp: close Channels", "Following and reading channels.", rebuild);
        if (!off) toggle(a, card, c, "waTextOnly", "WhatsApp: text and calls only", "No photos, videos or voice messages (their servers are blocked).", rebuild);
        card.addView(Ui.body(a, "Single apps: Apps tab above (Allow, Block, Schedule).", true));
    }

    /* ---- Safety ---- */

    private static void safety(Activity a, LinearLayout root, JSONObject c, Runnable rebuild, boolean off) {
        health(a, root);
        if (off) return;
        LinearLayout card = Ui.card(a, root);
        card.addView(Ui.titleText(a, "Message alerts"));
        toggle(a, card, c, "messageAlerts", "Alerts about worrying words in chats",
                "WhatsApp, Instagram, Snapchat, texts and other chat apps: the sentence with the word goes to the dashboard. The child is told this is on.", rebuild);
        if (c.optBoolean("messageAlerts", false)) {
            String[][] groups = {{"bullying", "Bullying and threats"}, {"selfharm", "Self-harm and suicide"}, {"sexual", "Sexual messages"}, {"drugs", "Drugs and alcohol"}, {"strangers", "Strangers and secrets"}};
            JSONArray cur = c.optJSONArray("messageGroups");
            for (String[] g : groups) {
                boolean on = cur == null || contains(cur, g[0]);
                Switch s = sw(a, card, g[1], null, on);
                s.setOnCheckedChangeListener((b, now) -> {
                    JSONArray base = current(a).optJSONArray("messageGroups");
                    JSONArray next = new JSONArray();
                    if (base == null) for (String[] x : groups) next.put(x[0]);
                    else for (int i = 0; i < base.length(); i++) next.put(base.optString(i));
                    JSONArray out = new JSONArray();
                    for (int i = 0; i < next.length(); i++) if (!next.optString(i).equals(g[0])) out.put(next.optString(i));
                    if (now) out.put(g[0]);
                    set1(a, "messageGroups", out, g[1] + (now ? " on" : " off"));
                    rebuild.run();
                });
            }
            words(a, card, c, "messageWords", "Your own words", "Extra words or phrases to look for. One per line.", rebuild);
        }
        LinearLayout loc = Ui.card(a, root);
        loc.addView(Ui.titleText(a, "Location history"));
        choice(a, loc, c, "locationEvery", "Note where the phone is", "Shown on the dashboard, with alerts for places you saved there.",
                new String[]{"0", "15", "30", "60"}, new String[]{"Off", "Every 15 minutes", "Every 30 minutes", "Every hour"}, "0", rebuild);
        JSONArray places = c.optJSONArray("places");
        for (int i = 0; places != null && i < places.length(); i++) {
            JSONObject pl = places.optJSONObject(i);
            if (pl != null) Ui.add(loc, Ui.body(a, "Place: " + pl.optString("name") + " (" + pl.optInt("r", 200) + " m around it)", false), 4);
        }
        Ui.add(loc, Ui.button(a, "Save where the phone is now as a place", Ui.TONAL, v -> {
            EditText name = new EditText(a);
            name.setHint("Name, for example Home or School");
            new AlertDialog.Builder(a).setTitle("Save this place").setView(name).setPositiveButton("Save", (d, w) -> {
                String n = name.getText().toString().trim();
                if (n.isEmpty()) return;
                Toast(a, "Finding where the phone is...");
                final Context app = a.getApplicationContext();
                new Thread(() -> {
                    try {
                        JSONObject fix = new JSONObject(Actions.locate(app));
                        JSONArray cur = current(app).optJSONArray("places");
                        JSONArray next = new JSONArray();
                        for (int k = 0; cur != null && k < cur.length(); k++) next.put(cur.get(k));
                        JSONObject p = new JSONObject();
                        p.put("name", n);
                        p.put("lat", fix.optDouble("lat"));
                        p.put("lon", fix.optDouble("lon"));
                        p.put("r", 200);
                        next.put(p);
                        set1(app, "places", next, "saved the place " + n);
                        a.runOnUiThread(() -> { Toast(a, n + " saved. Alerts come when the phone arrives or leaves."); rebuild.run(); });
                    } catch (Exception e) {
                        a.runOnUiThread(() -> Toast(a, "Could not find where the phone is: " + e.getMessage()));
                    }
                }, "mdm-save-place").start();
            }).setNegativeButton("Cancel", null).show();
        }), 8);
    }

    /** What has to work on this phone, read straight from the phone, with a button to fix each. */
    private static void health(Activity a, LinearLayout root) {
        LinearLayout card = Ui.card(a, root);
        card.addView(Ui.titleText(a, "Health check"));
        card.addView(Ui.body(a, "Everything that has to work on this phone for the rules to work.", true));
        boolean a11y = Agent.accessibilityServiceOn(a);
        boolean battery = true;
        try {
            battery = ((android.os.PowerManager) a.getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(a.getPackageName());
        } catch (Exception ignored) {
        }
        boolean location = a.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        healthRow(a, card, Agent.isOwner(a), "MDM Agent is in full control", "Set the phone up again with the QR code.", null);
        healthRow(a, card, DnsFilterVpnService.filterRunning || !needsFilter(a), "Website filter running", "Restart the phone.", null);
        healthRow(a, card, a11y, "Accessibility switch on (message alerts, screenshots, WhatsApp, web pages in apps)",
                "Tap: Accessibility → MDM Agent → on.", new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
        healthRow(a, card, battery, "Battery saver leaves MDM Agent alone", "Tap: Battery → Unrestricted.",
                new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        healthRow(a, card, Agent.usageAccessOn(a), "Usage access on (screen time)", "Tap: MDM Agent → allow.",
                new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS));
        healthRow(a, card, location, "Location allowed (location history, \"I need help\")", "Tap: Permissions → Location → Allow all the time.",
                new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + a.getPackageName())));
        if (!offline(a)) {
            long last = Agent.prefs(a).getLong("lastSync", 0);
            healthRow(a, card, System.currentTimeMillis() - last < 15 * 60000L, "Connected to the dashboard", "Check the internet connection, then Overview → Sync now.", null);
        }
    }

    private static boolean needsFilter(Context c) {
        try {
            JSONObject p = new JSONObject(Agent.prefs(c).getString("policy", "{}"));
            return !"off".equals(p.optString("browserFilter", "off")) || p.optBoolean("safeSearch") || !"off".equals(p.optString("youtubeDns", "off"));
        } catch (Exception e) {
            return false;
        }
    }

    private static void healthRow(Activity a, LinearLayout card, boolean ok, String what, String fix, Intent open) {
        TextView t = Ui.body(a, (ok ? "✅  " : "🔴  ") + what + (ok ? "" : "\n     How to fix: " + fix), !ok ? false : true);
        Ui.add(card, t, 8);
        if (!ok && open != null) Ui.add(card, Ui.button(a, "Fix it", Ui.TONAL, v -> {
            try {
                a.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                a.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }), 4);
    }

    /* ---- More ---- */

    private static void more(Activity a, LinearLayout root, JSONObject c, Runnable rebuild, boolean off) {
        LinearLayout card = Ui.card(a, root);
        card.addView(Ui.titleText(a, "More"));
        toggle(a, card, c, "freezeUpdates", "Freeze system updates", "Android system updates wait (they can reset settings).", rebuild);
        toggle(a, card, c, "disableBiometricUnlock", "No fingerprint or face unlock", "Only the PIN unlocks the screen.", rebuild);
        if (!off) {
            toggle(a, card, c, "autoUpdate", "Keep MDM Agent up to date by itself", "Installs new versions of this app automatically.", rebuild);
            toggle(a, card, c, "reportWifi", "Show the Wi-Fi name on the dashboard", "Needs Location on.", rebuild);
            toggle(a, card, c, "recordSites", "Keep a list of sites visited", "Shown on the dashboard (Browser → Sites visited).", rebuild);
        }
    }

    /* ------------------------------------------------------------------ widgets ------------------------------------------------------------------ */

    private static void toggle(Activity a, LinearLayout card, JSONObject c, String key, String title, String help, Runnable rebuild) {
        Switch s = sw(a, card, title, help, c.optBoolean(key, false));
        s.setOnCheckedChangeListener((b, on) -> {
            set1(a, key, on, title + (on ? ": on" : ": off"));
            rebuild.run();
        });
    }

    private static Switch sw(Activity a, LinearLayout card, String title, String help, boolean on) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout text = new LinearLayout(a);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.body(a, title, false);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        text.addView(t);
        if (help != null) text.addView(Ui.body(a, help, true));
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Switch s = new Switch(a);
        s.setChecked(on);
        row.addView(s);
        Ui.add(card, row, 12);
        return s;
    }

    private static void choice(Activity a, LinearLayout card, JSONObject c, String key, String title, String help, String[] values, String[] labels, String def, Runnable rebuild) {
        String cur = c.has(key) ? String.valueOf(c.opt(key)) : def;
        int sel = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) sel = i;
        final int selected = sel;
        row(a, card, title, help, labels[sel], v -> Ui.alertDialog(a).setTitle(title).setSingleChoiceItems(labels, selected, (d, w) -> {
            d.dismiss();
            Object value = values[w];
            if (values[w].matches("\\d+")) value = Integer.parseInt(values[w]);
            set1(a, key, value, title + ": " + labels[w]);
            rebuild.run();
        }).show());
    }

    private static void row(Activity a, LinearLayout card, String title, String help, String value, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.body(a, title, false);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(t);
        if (help != null) box.addView(Ui.body(a, help, true));
        Button b = Ui.button(a, value + "  ▾", Ui.OUTLINED, click);
        Ui.add(box, b, 6);
        Ui.add(card, box, 12);
    }

    private static final String[] KIND_IDS = {"adult", "gambling", "drugs", "scams", "piracy", "social", "news", "shopping", "games", "video", "dating"};
    private static final String[] KIND_NAMES = {"Adult sites", "Gambling", "Drugs and vaping", "Scams and malware", "Piracy and torrents", "Social media", "News", "Shopping", "Games", "Video and streaming", "Dating"};

    /** The "Open" level: which kinds of site are closed (nothing saved yet means adult sites). Everything else stays open. */
    private static void kinds(Activity a, LinearLayout card, JSONObject c, Runnable rebuild) {
        JSONArray cur = c.has("filterKinds") ? c.optJSONArray("filterKinds") : null;
        final boolean[] on = new boolean[KIND_IDS.length];
        if (cur == null) on[0] = true;
        else for (int i = 0; i < cur.length(); i++) for (int k = 0; k < KIND_IDS.length; k++) if (KIND_IDS[k].equals(cur.optString(i))) on[k] = true;
        int n = 0;
        for (boolean b : on) if (b) n++;
        row(a, card, "Kinds of site that are closed", "Everything not ticked stays open (Facebook, YouTube, news...).", n == 0 ? "None" : n + " closed", v -> {
            final boolean[] chosen = on.clone();
            new AlertDialog.Builder(a).setTitle("Close these kinds of site")
                    .setMultiChoiceItems(KIND_NAMES, chosen, (d, w, ck) -> chosen[w] = ck)
                    .setPositiveButton("Save", (d, w) -> {
                        JSONArray out = new JSONArray();
                        for (int k = 0; k < KIND_IDS.length; k++) if (chosen[k]) out.put(KIND_IDS[k]);
                        set1(a, "filterKinds", out, "Closed kinds of site (" + out.length() + ")");
                        rebuild.run();
                    }).setNegativeButton("Cancel", null).show();
        });
    }

    private static void words(Activity a, LinearLayout card, JSONObject c, String key, String title, String help, Runnable rebuild) {
        JSONArray cur = c.optJSONArray(key);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; cur != null && i < cur.length(); i++) sb.append(i == 0 ? "" : "\n").append(cur.optString(i));
        int n = cur == null ? 0 : cur.length();
        row(a, card, title, help, n == 0 ? "None" : n + (n == 1 ? " word" : " words"), v -> {
            EditText f = new EditText(a);
            f.setMinLines(4);
            f.setText(sb.toString());
            new AlertDialog.Builder(a).setTitle(title).setView(f).setPositiveButton("Save", (d, w) -> {
                JSONArray out = new JSONArray();
                for (String line : f.getText().toString().split("[\\n,]")) {
                    String s = line.trim().toLowerCase(Locale.ROOT);
                    if (s.length() >= 2) out.put(s);
                }
                set1(a, key, out, title + " (" + out.length() + ")");
                rebuild.run();
            }).setNegativeButton("Cancel", null).show();
        });
    }

    /** A different filter level at set times (for example approved sites only at night), in the phone's own time. */
    private static void filterSchedule(Activity a, LinearLayout card, JSONObject c, Runnable rebuild) {
        JSONObject fs = c.optJSONObject("filterSchedule");
        final JSONObject sch;
        try {
            sch = fs != null ? new JSONObject(fs.toString()) : new JSONObject();
        } catch (Exception e) {
            return;
        }
        boolean on = sch.optBoolean("on", false);
        String[] levels = {"off", "moderate", "strict"}, names = {"Approved sites only", "Moderate", "Strict"};
        String lvl = sch.optString("level", "strict");
        String lvlName = names[2];
        for (int i = 0; i < levels.length; i++) if (levels[i].equals(lvl)) lvlName = names[i];
        Switch s = sw(a, card, "A different level at set times", on ? lvlName + " from " + sch.optString("from", "08:00") + " to " + sch.optString("to", "15:00") + ", in the phone's own time." : "Off.", on);
        s.setOnCheckedChangeListener((b, now) -> {
            try {
                sch.put("on", now);
                if (!sch.has("level")) sch.put("level", "off");
                if (!sch.has("from")) sch.put("from", "21:00");
                if (!sch.has("to")) sch.put("to", "07:00");
                if (!sch.has("days")) sch.put("days", new JSONArray(new int[]{0, 1, 2, 3, 4, 5, 6}));
            } catch (Exception ignored) {
            }
            set1(a, "filterSchedule", sch, now ? "filter schedule on" : "filter schedule off");
            rebuild.run();
        });
        if (!on) return;
        int sel = 0;
        for (int i = 0; i < levels.length; i++) if (levels[i].equals(lvl)) sel = i;
        final int selected = sel;
        Ui.add(card, Ui.button(a, "Level then: " + lvlName, Ui.OUTLINED, v -> Ui.alertDialog(a).setTitle("Level at those times").setSingleChoiceItems(names, selected, (d, w) -> {
            d.dismiss();
            try {
                sch.put("level", levels[w]);
            } catch (Exception ignored) {
            }
            set1(a, "filterSchedule", sch, "filter schedule level " + names[w]);
            rebuild.run();
        }).show()), 6);
        for (String end : new String[]{"from", "to"}) {
            String cur = sch.optString(end, end.equals("from") ? "21:00" : "07:00");
            Ui.add(card, Ui.button(a, (end.equals("from") ? "Starts " : "Ends ") + cur, Ui.OUTLINED, v -> {
                int h = Integer.parseInt(cur.substring(0, 2)), m = Integer.parseInt(cur.substring(3, 5));
                new TimePickerDialog(a, (tp, hh, mm) -> {
                    try {
                        sch.put(end, String.format(Locale.US, "%02d:%02d", hh, mm));
                    } catch (Exception ignored) {
                    }
                    set1(a, "filterSchedule", sch, "filter schedule " + sch.optString("from") + "-" + sch.optString("to"));
                    rebuild.run();
                }, h, m, true).show();
            }), 6);
        }
    }

    /** Approved sites (websites only; YouTube channels and single pages are kept as the dashboard set them). */
    private static void approvedSites(Activity a, LinearLayout card, JSONObject c, Runnable rebuild) {
        JSONObject sites = c.optJSONObject("sites");
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Iterator<String> it = sites == null ? java.util.Collections.<String>emptyIterator() : sites.keys(); it.hasNext(); ) {
            JSONObject s = sites.optJSONObject(it.next());
            if (s != null && "domain".equals(s.optString("type"))) {
                sb.append(n++ == 0 ? "" : "\n").append(s.optString("host"));
            }
        }
        row(a, card, "Approved sites", "Always open, whatever the filter level (with \"Approved sites only\" these are the only ones). One per line.",
                n == 0 ? "None" : n + (n == 1 ? " site" : " sites"), v -> {
                    EditText f = new EditText(a);
                    f.setMinLines(5);
                    f.setText(sb.toString());
                    new AlertDialog.Builder(a).setTitle("Approved sites").setView(f).setPositiveButton("Save", (d, w) -> {
                        try {
                            JSONObject next = new JSONObject();
                            JSONObject cur = current(a).optJSONObject("sites");
                            for (Iterator<String> it = cur == null ? java.util.Collections.<String>emptyIterator() : cur.keys(); it.hasNext(); ) {
                                String k = it.next();
                                JSONObject s = cur.optJSONObject(k);
                                if (s != null && !"domain".equals(s.optString("type"))) next.put(k, s); // YouTube channels, single pages
                            }
                            int i = 0;
                            for (String line : f.getText().toString().split("[\\s,]+")) {
                                String h = line.trim().toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceFirst("/.*$", "").replaceFirst("^www\\.", "");
                                if (!h.matches("[a-z0-9.-]+\\.[a-z]{2,}")) continue;
                                JSONObject s = new JSONObject();
                                s.put("type", "domain");
                                s.put("url", "https://" + h);
                                s.put("host", h);
                                s.put("label", h);
                                next.put("p" + Integer.toHexString(h.hashCode()) + (i++), s);
                            }
                            set1(a, "sites", next, "approved sites (" + i + ")");
                        } catch (Exception ignored) {
                        }
                        rebuild.run();
                    }).setNegativeButton("Cancel", null).show();
                });
    }

    private static boolean contains(JSONArray a, String v) {
        for (int i = 0; a != null && i < a.length(); i++) if (v.equals(a.optString(i))) return true;
        return false;
    }

    private static void putIn(JSONObject o, String k, Object v) {
        try {
            o.put(k, v);
        } catch (Exception ignored) {
        }
    }

    private static String fmt(int m) {
        return m < 60 ? m + " min" : (m / 60) + " h" + (m % 60 == 0 ? "" : " " + (m % 60) + " min");
    }

    private static void Toast(Activity a, String s) {
        android.widget.Toast.makeText(a, s, android.widget.Toast.LENGTH_LONG).show();
    }
}
