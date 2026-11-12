package com.familymdm.browser;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.familymdm.agent.sitepolicy.SitePolicy;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "Allow list" (the button at the top right of the Browser): everything that is allowed, how long is
 * left on what was allowed for a while, and what ran out or was ended in the last 24 hours.
 *  - Something allowed for a while can be ended early ("Block it now"): it is blocked right away and
 *    stays blocked until it is allowed again (the dashboard is told too).
 *  - Something whose time is up (or that was ended) can be asked for again.
 */
public final class AllowList {
    private AllowList() {}

    static final long KEEP_MS = 24L * 60 * 60 * 1000;

    public interface Callback {
        /** Open this address in the Browser. */
        void open(String url);

        /** Something was ended early: the page shown is checked again. */
        void changed();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("allowlist", Context.MODE_PRIVATE);
    }

    static String keyOf(JSONObject s) {
        return "youtube".equals(s.optString("type")) ? "youtube:" + s.optString("id") : s.optString("type") + ":" + s.optString("url");
    }

    private static JSONObject read(Context c, String name) {
        try {
            return new JSONObject(prefs(c).getString(name, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    // ---------- ended early ----------

    static JSONObject endedMap(Context c) {
        return read(c, "ended");
    }

    /** Ended early by the child: blocked until it is allowed again (a new end time from the dashboard). */
    static boolean endedEarly(JSONObject ended, JSONObject s) {
        long u = s.optLong("until", 0);
        if (u <= 0) return false;
        JSONObject e = ended.optJSONObject(keyOf(s));
        return e != null && e.optLong("until") == u;
    }

    static boolean endedEarly(Context c, JSONObject s) {
        return endedEarly(endedMap(c), s);
    }

    static synchronized void endEarly(Context c, JSONObject s) {
        try {
            JSONObject all = endedMap(c);
            JSONObject e = new JSONObject(s.toString());
            e.put("at", System.currentTimeMillis());
            all.put(keyOf(s), e);
            prune(all, "at");
            prefs(c).edit().putString("ended", all.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Ended early and still running on the dashboard: sent with each check-in so it ends there too. */
    static JSONArray endedForServer(Context c) {
        JSONArray out = new JSONArray();
        JSONObject all = endedMap(c);
        long now = System.currentTimeMillis();
        for (Iterator<String> it = all.keys(); it.hasNext(); ) {
            JSONObject e = all.optJSONObject(it.next());
            if (e == null || e.optLong("until", 0) <= now) continue;
            try {
                out.put(new JSONObject().put("type", e.optString("type")).put("url", e.optString("url"))
                        .put("id", e.optString("id")).put("until", e.optLong("until")));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    // ---------- what was allowed for a while (kept a day after it ends) ----------

    private static volatile String lastSeen;

    /** Remembers entries allowed for a while, so "Time's up" stays in the list for a day after. */
    static synchronized void remember(Context c, JSONArray raw) {
        String now = raw == null ? "" : raw.toString();
        if (now.equals(lastSeen) && prefs(c).contains("seen")) return;
        lastSeen = now;
        try {
            JSONObject seen = read(c, "seen");
            boolean changed = false;
            for (int i = 0; raw != null && i < raw.length(); i++) {
                JSONObject s = raw.optJSONObject(i);
                if (s == null || s.optLong("until", 0) <= 0) continue;
                String k = keyOf(s);
                JSONObject old = seen.optJSONObject(k);
                if (old == null || old.optLong("until") != s.optLong("until")) {
                    seen.put(k, s);
                    changed = true;
                }
            }
            if (prune(seen, "until")) changed = true;
            if (changed) prefs(c).edit().putString("seen", seen.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static boolean prune(JSONObject o, String field) {
        long cut = System.currentTimeMillis() - KEEP_MS;
        List<String> old = new ArrayList<>();
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONObject e = o.optJSONObject(k);
            if (e == null || e.optLong(field, 0) < cut) old.add(k);
        }
        for (String k : old) o.remove(k);
        return !old.isEmpty();
    }

    // ---------- the list ----------

    static final class Row {
        final JSONObject site;
        final String state; // "live" (allowed for a while), "always", "over" (time is up), "ended" (ended early)
        final long time;    // the end time (live, over), or when it was ended (ended)

        Row(JSONObject site, String state, long time) {
            this.site = site;
            this.state = state;
            this.time = time;
        }
    }

    static final class Rows {
        final List<Row> live = new ArrayList<>(), always = new ArrayList<>(), done = new ArrayList<>();

        boolean isEmpty() {
            return live.isEmpty() && always.isEmpty() && done.isEmpty();
        }
    }

    static Rows rows(Context c, JSONArray raw) {
        long now = System.currentTimeMillis();
        Rows r = new Rows();
        Set<String> shown = new HashSet<>();
        JSONObject ended = endedMap(c);
        for (int i = 0; raw != null && i < raw.length(); i++) {
            JSONObject s = raw.optJSONObject(i);
            if (s == null) continue;
            long u = s.optLong("until", 0);
            if ((u > 0 && u <= now) || endedEarly(ended, s)) continue; // listed below, under "ended"
            shown.add(keyOf(s));
            if (u > 0) r.live.add(new Row(s, "live", u));
            else r.always.add(new Row(s, "always", 0));
        }
        for (Iterator<String> it = ended.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONObject e = ended.optJSONObject(k);
            if (e == null || shown.contains(k) || e.optLong("at") < now - KEEP_MS) continue;
            shown.add(k);
            r.done.add(new Row(e, "ended", e.optLong("at")));
        }
        JSONObject seen = read(c, "seen");
        for (Iterator<String> it = seen.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONObject s = seen.optJSONObject(k);
            if (s == null || shown.contains(k)) continue;
            long u = s.optLong("until", 0);
            if (u <= now && u >= now - KEEP_MS) r.done.add(new Row(s, "over", u));
        }
        Collections.sort(r.live, (a, b) -> Long.compare(a.time, b.time));
        Collections.sort(r.always, (a, b) -> label(a.site).compareToIgnoreCase(label(b.site)));
        Collections.sort(r.done, (a, b) -> Long.compare(b.time, a.time));
        return r;
    }

    static String label(JSONObject s) {
        String l = s.optString("label", "").replaceFirst("^YouTube channel ", "");
        if (l.isEmpty() || "YouTube video".equals(l)) l = "youtube".equals(s.optString("type")) ? s.optString("id") : s.optString("url");
        return l;
    }

    private static String kind(JSONObject s) {
        String type = s.optString("type");
        String host = SitePolicy.hostOf(s.optString("url"));
        if ("youtube".equals(type)) return "YouTube channel " + s.optString("id");
        boolean yt = host != null && (SitePolicy.isYoutubeHost(host) || "youtu.be".equals(host));
        if (yt && SitePolicy.youtubeVideoId(s.optString("url")) != null) return "YouTube video";
        return ("domain".equals(type) ? "Whole site: " : "Page: ") + (host == null ? s.optString("url") : host);
    }

    static String left(long ms) {
        long min = (ms + 59_999) / 60_000;
        if (ms < 60_000) return "Less than a minute left";
        long d = min / 1440, h = (min % 1440) / 60, m = min % 60;
        StringBuilder b = new StringBuilder();
        if (d > 0) b.append(d).append(d == 1 ? " day " : " days ");
        if (h > 0) b.append(h).append(" h ");
        if (d == 0 && m > 0) b.append(m).append(" min ");
        return b.toString().trim() + " left";
    }

    private static String when(Context c, long t) {
        String time = DateFormat.getTimeFormat(c).format(new Date(t));
        if (DateUtils.isToday(t)) return "at " + time;
        if (DateUtils.isToday(t + 24L * 60 * 60 * 1000)) return "yesterday at " + time;
        if (DateUtils.isToday(t - 24L * 60 * 60 * 1000)) return "tomorrow at " + time;
        return new java.text.SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(new Date(t)) + " at " + time;
    }

    static String status(Context c, Row r) {
        long now = System.currentTimeMillis();
        switch (r.state) {
            case "live":
                return r.time > now ? left(r.time - now) + " · until " + when(c, r.time).replaceFirst("^at ", "") : "Time's up · Tap to ask again";
            case "always":
                return "Always allowed";
            case "over":
                return "Time's up " + when(c, r.time) + " · Tap to ask again";
            default:
                return "You ended it " + when(c, r.time) + " · Tap to ask again";
        }
    }

    // ---------- the window ----------

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    private static TextView text(Activity a, String s, int sp, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** Opens the Allow list. raw: the allowed list as the dashboard sent it (Guard.allSites). */
    public static void show(final Activity a, JSONArray raw, final Callback cb) {
        final Context c = a.getApplicationContext();
        Rows rows = rows(c, raw);
        ScrollView scroll = new ScrollView(a);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(a, 4), 0, dp(a, 8));
        scroll.addView(box);
        final AlertDialog[] dlg = new AlertDialog[1];
        final List<Object[]> clocks = new ArrayList<>();
        if (rows.isEmpty()) {
            TextView t = text(a, "Nothing is allowed here yet. When a page is blocked, you can ask for it right on that page.", 15, false);
            t.setPadding(dp(a, 24), dp(a, 8), dp(a, 24), dp(a, 8));
            box.addView(t);
        }
        section(a, box, "Allowed for a while", rows.live, cb, dlg, clocks);
        section(a, box, "Always allowed", rows.always, cb, dlg, clocks);
        section(a, box, "Ended in the last 24 hours", rows.done, cb, dlg, clocks);
        dlg[0] = new MaterialAlertDialogBuilder(a).setTitle("Allow list").setView(scroll).setPositiveButton("Close", null).create();
        // The time left counts down while the list is open.
        final Handler h = new Handler(Looper.getMainLooper());
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                for (Object[] x : clocks) ((TextView) x[0]).setText(status(a, (Row) x[1]));
                h.postDelayed(this, 20_000);
            }
        };
        dlg[0].setOnDismissListener(d -> h.removeCallbacks(tick));
        dlg[0].show();
        h.postDelayed(tick, 20_000);
    }

    private static void section(final Activity a, LinearLayout box, String title, List<Row> rows, final Callback cb, final AlertDialog[] dlg, List<Object[]> clocks) {
        if (rows.isEmpty()) return;
        TextView head = text(a, title, 13, true);
        head.setPadding(dp(a, 24), dp(a, 14), dp(a, 24), dp(a, 4));
        head.setTextColor(MaterialColors.getColor(a, androidx.appcompat.R.attr.colorPrimary, 0xFF4F46E5));
        box.addView(head);
        TypedValue ripple = new TypedValue();
        a.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        for (final Row r : rows) {
            LinearLayout item = new LinearLayout(a);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(a, 24), dp(a, 10), dp(a, 24), dp(a, 10));
            item.setBackgroundResource(ripple.resourceId);
            item.addView(text(a, label(r.site), 16, true));
            TextView k = text(a, kind(r.site), 13, false);
            k.setAlpha(0.7f);
            item.addView(k);
            TextView st = text(a, status(a, r), 14, false);
            boolean done = "over".equals(r.state) || "ended".equals(r.state);
            st.setTextColor(done ? MaterialColors.getColor(a, com.google.android.material.R.attr.colorError, 0xFFB3261E)
                    : MaterialColors.getColor(a, androidx.appcompat.R.attr.colorPrimary, 0xFF4F46E5));
            item.addView(st);
            if ("live".equals(r.state)) clocks.add(new Object[]{st, r});
            item.setOnClickListener(v -> act(a, r, cb, dlg[0]));
            box.addView(item);
        }
    }

    private static void act(final Activity a, final Row r, final Callback cb, final AlertDialog list) {
        final String url = r.site.optString("url");
        final String name = label(r.site);
        if ("live".equals(r.state) && r.time <= System.currentTimeMillis()) {
            askAgain(a, new Row(r.site, "over", r.time), list);
            return;
        }
        if ("live".equals(r.state) || "always".equals(r.state)) {
            MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(a).setTitle(name)
                    .setMessage("live".equals(r.state) ? status(a, r) + "." : "Always allowed.")
                    .setPositiveButton("Open", (d, w) -> {
                        if (list != null) list.dismiss();
                        cb.open(url);
                    })
                    .setNeutralButton("Cancel", null);
            if ("live".equals(r.state)) b.setNegativeButton("Block it now", (d, w) -> confirmEnd(a, r, cb, list));
            b.show();
        } else {
            askAgain(a, r, list);
        }
    }

    private static void confirmEnd(final Activity a, final Row r, final Callback cb, final AlertDialog list) {
        new MaterialAlertDialogBuilder(a).setTitle("Block it now?")
                .setMessage(label(r.site) + " is closed and blocked right away. You lose access until it is allowed again (you can ask for it again from this list).")
                .setPositiveButton("Block it now", (d, w) -> {
                    Context c = a.getApplicationContext();
                    endEarly(c, r.site);
                    Guard.forgetCache();
                    Guard.sync(c, true);
                    if (list != null) list.dismiss();
                    Toast.makeText(a, label(r.site) + " is blocked now.", Toast.LENGTH_LONG).show();
                    cb.changed();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void askAgain(final Activity a, final Row r, final AlertDialog list) {
        final EditText note = new EditText(a);
        note.setHint("Why (optional)");
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        FrameLayout wrap = new FrameLayout(a);
        wrap.setPadding(dp(a, 24), dp(a, 4), dp(a, 24), 0);
        wrap.addView(note);
        new MaterialAlertDialogBuilder(a).setTitle("Ask for " + label(r.site) + " again?")
                .setMessage(("ended".equals(r.state) ? "You ended it " : "Its time ran out ") + when(a, r.time) + ". Your parent gets the request.")
                .setView(wrap)
                .setPositiveButton("Ask", (d, w) -> {
                    String why = note.getText().toString().trim();
                    Guard.sendRequest(a, r.site.optString("url"), why.isEmpty() ? ("ended".equals(r.state) ? "Asking again" : "Asking for more time") : why);
                    if (list != null) list.dismiss();
                    Toast.makeText(a, "Asked. It opens once it is allowed again.", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
