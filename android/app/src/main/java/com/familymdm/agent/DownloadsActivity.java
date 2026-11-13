package com.familymdm.agent;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

/**
 * "Downloads": what is being downloaded (with its progress and a Cancel button), and what is downloaded (Play, Delete).
 * The YouTube icon lists videos, the YouTube Music icon lists songs ("music" extra).
 */
public class DownloadsActivity extends Activity {
    private static final int BG = 0xFF0F0F0F, CARD = 0xFF1E1E1E, TEXT = 0xFFF1F1F1, MUTED = 0xFFAAAAAA, ACCENT = 0xFF4F46E5, BAD = 0xFFE5484D;

    private boolean music;
    private LinearLayout list;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String shown = "";
    private final java.util.Map<String, ProgressBar> bars = new java.util.HashMap<>();
    private final java.util.Map<String, TextView> labels = new java.util.HashMap<>();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 600);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        music = getIntent().getBooleanExtra("music", false);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        list.setPadding(pad, dp(24), pad, dp(32));
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        setTitle(music ? "Downloaded songs" : "Downloads");
    }

    @Override
    protected void onResume() {
        super.onResume();
        shown = "";
        tick.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String label, int color, View.OnClickListener l) {
        Button x = new Button(this);
        x.setText(label);
        x.setAllCaps(false);
        x.setTextColor(Color.WHITE);
        x.setTextSize(14);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(18));
        x.setBackground(g);
        x.setPadding(dp(18), 0, dp(18), 0);
        x.setMinHeight(dp(40));
        x.setMinimumHeight(dp(40));
        x.setOnClickListener(l);
        return x;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(14));
        c.setBackground(g);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        c.setLayoutParams(lp);
        return c;
    }

    private static String sizeOf(long bytes) {
        if (bytes <= 0) return "";
        double mb = bytes / 1048576.0;
        return mb >= 1 ? String.format(java.util.Locale.US, "%.1f MB", mb) : (bytes / 1024) + " KB";
    }

    /** Rebuilds the list when something started, finished or was removed; otherwise only moves the progress bars. */
    private void refresh() {
        List<DownloadService.Job> jobs = DownloadService.jobs(music);
        JSONArray done = Downloads.forPages(this, music);
        StringBuilder sig = new StringBuilder();
        for (DownloadService.Job j : jobs) sig.append(j.id).append(':').append(j.state).append(';');
        sig.append('|');
        for (int i = 0; i < done.length(); i++) sig.append(done.optJSONObject(i).optString("v")).append(';');
        if (!sig.toString().equals(shown)) {
            shown = sig.toString();
            rebuild(jobs, done);
        }
        for (DownloadService.Job j : jobs) {
            ProgressBar bar = bars.get(j.id);
            TextView label = labels.get(j.id);
            if (bar != null) {
                if (j.percent < 0) bar.setIndeterminate(true);
                else {
                    bar.setIndeterminate(false);
                    bar.setProgress(j.percent);
                }
            }
            if (label != null) label.setText(statusOf(j));
        }
    }

    private static String statusOf(DownloadService.Job j) {
        if ("error".equals(j.state)) return "Did not work. Try again in a little while.";
        if ("waiting".equals(j.state)) return "Waiting…";
        return j.percent < 0 ? "Getting ready…" : j.percent + "%";
    }

    private void rebuild(List<DownloadService.Job> jobs, JSONArray done) {
        list.removeAllViews();
        bars.clear();
        labels.clear();
        list.addView(text(music ? "Downloaded songs" : "Downloads", 24, TEXT, true));
        TextView sub = text(music ? "Songs you downloaded play here, even without internet." : "Videos you downloaded play here, even without internet.", 14, MUTED, false);
        sub.setPadding(0, dp(2), 0, dp(16));
        list.addView(sub);

        if (!jobs.isEmpty()) {
            list.addView(text("Downloading", 17, TEXT, true));
            for (DownloadService.Job j : jobs) {
                final String id = j.id;
                LinearLayout c = card();
                String name = j.title.isEmpty() ? (music ? "Song" : "Video") : j.title;
                c.addView(text(name, 15, TEXT, true));
                boolean failed = "error".equals(j.state);
                if (!failed) {
                    ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                    bar.setMax(100);
                    bar.setIndeterminate(j.percent < 0);
                    bar.setProgress(Math.max(0, j.percent));
                    bar.getProgressDrawable().setColorFilter(ACCENT, android.graphics.PorterDuff.Mode.SRC_IN);
                    LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
                    bl.topMargin = dp(10);
                    c.addView(bar, bl);
                    bars.put(id, bar);
                }
                TextView status = text(statusOf(j), 13, failed ? BAD : MUTED, false);
                status.setPadding(0, dp(6), 0, dp(8));
                c.addView(status);
                labels.put(id, status);
                Button cancel = button(failed ? "Remove" : "Cancel download", failed ? 0xFF555555 : BAD, v -> {
                    DownloadService.cancel(this, id);
                    refresh();
                });
                c.addView(cancel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
                list.addView(c);
            }
        }

        TextView head = text("Downloaded", 17, TEXT, true);
        head.setPadding(0, dp(jobs.isEmpty() ? 0 : 12), 0, dp(8));
        list.addView(head);
        if (done.length() == 0) {
            list.addView(text(music ? "No songs yet. Open a song you are allowed to listen to and press Download song."
                    : "No videos yet. Open a video you are allowed to watch and press Download.", 14, MUTED, false));
        }
        for (int i = 0; i < done.length(); i++) {
            JSONObject o = done.optJSONObject(i);
            if (o == null) continue;
            final String id = o.optString("v");
            JSONObject rec = Downloads.find(this, id);
            LinearLayout c = card();
            c.addView(text(o.optString("t").isEmpty() ? (music ? "Song" : "Video") : o.optString("t"), 15, TEXT, true));
            if (rec != null) {
                String size = sizeOf(new File(Downloads.dir(this), rec.optString("file")).length());
                if (!size.isEmpty()) {
                    TextView s = text(size, 13, MUTED, false);
                    s.setPadding(0, dp(2), 0, dp(8));
                    c.addView(s);
                }
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.START);
            Button play = button(music ? "Play song" : "Play", ACCENT, v -> startActivity(new Intent(this, DownloadPlayerActivity.class).putExtra("id", id)));
            Button del = button("Delete", 0xFF555555, v -> new AlertDialog.Builder(this)
                    .setMessage("Delete this download from the phone?")
                    .setPositiveButton("Delete", (d, w) -> {
                        Downloads.remove(this, id);
                        refresh();
                    })
                    .setNegativeButton("Keep it", null)
                    .show());
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
            pl.rightMargin = dp(10);
            row.addView(play, pl);
            row.addView(del, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
            c.addView(row);
            list.addView(c);
        }
    }
}
