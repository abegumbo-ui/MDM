package com.familymdm.agent;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import com.familymdm.agent.sitepolicy.SitePolicy;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.mapper.VideoInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads a YouTube video or song with yt-dlp (the open-source downloader, built into this app and updated by itself
 * from time to time, because YouTube changes). Runs as a foreground service with a progress notification that has a
 * Cancel button. Every download that is waiting, running or failed is listed in JOBS, which the Downloads screen shows
 * with its progress and a Cancel button. The file goes to the app's own private folder; finished ones are listed in
 * Downloads and played by DownloadPlayerActivity.
 */
public class DownloadService extends Service {
    private static final String CHANNEL = "downloads";
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static boolean ready;

    /** The useful line of yt-dlp's complaint: its "ERROR:" line, not the warnings that come first. */
    static String reason(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "it did not work";
        String best = null, last = null;
        for (String line : raw.split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("ERROR")) best = t;
            if (!t.startsWith("WARNING")) last = t;
        }
        String out = best != null ? best : last != null ? last : raw.trim();
        return out.length() > 400 ? out.substring(0, 400) : out;
    }

    /** One download that is not finished yet (or did not work). */
    static final class Job {
        final String id;
        final boolean music;
        volatile String title = "";
        volatile int percent = -1; // -1 = not started / unknown
        volatile String state = "waiting"; // waiting | downloading | error
        volatile String error = "";
        volatile boolean cancelled;

        Job(String id, boolean music) {
            this.id = id;
            this.music = music;
        }
    }

    private static final Map<String, Job> JOBS = new ConcurrentHashMap<>();

    /** The downloads in progress (waiting, running, failed) for the songs (music) or the videos. */
    static List<Job> jobs(boolean music) {
        List<Job> out = new ArrayList<>();
        for (Job j : JOBS.values()) if (j.music == music) out.add(j);
        return out;
    }

    static boolean isActive(String id) {
        Job j = JOBS.get(id);
        return j != null && !"error".equals(j.state);
    }

    /** Stops a download (or removes a failed one from the list) and deletes its partial file. */
    static void cancel(android.content.Context c, String id) {
        Job j = JOBS.remove(id);
        if (j == null) return;
        j.cancelled = true;
        if ("downloading".equals(j.state)) {
            try {
                YoutubeDL.getInstance().destroyProcessById("dl-" + id);
            } catch (Throwable ignored) {
                // the file is removed below either way
            }
        }
        File[] parts = Downloads.dir(c).listFiles((d, name) -> name.startsWith(id + ".") && !isFinishedName(c, id, name));
        if (parts != null) for (File f : parts) f.delete();
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (JOBS.isEmpty()) nm.cancel(2);
    }

    private static boolean isFinishedName(android.content.Context c, String id, String name) {
        org.json.JSONObject done = Downloads.find(c, id);
        return done != null && name.equals(done.optString("file"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(2, note("Getting ready…", -1, null));
        final String url = intent == null ? null : intent.getStringExtra("url");
        final boolean music = intent != null && intent.getBooleanExtra("music", false);
        final int id = startId;
        final String vid = url == null ? null : SitePolicy.youtubeVideoId(url);
        if (vid == null || isActive(vid)) { // a failed one can be tried again
            if (JOBS.isEmpty()) stopSelf(id);
            return START_NOT_STICKY;
        }
        final Job job = new Job(vid, music);
        JOBS.put(vid, job);
        POOL.execute(() -> {
            try {
                if (!job.cancelled) download(url, job);
            } finally {
                if (JOBS.isEmpty()) stopSelf(id);
            }
        });
        return START_NOT_STICKY;
    }

    private void toast(final String s) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show());
    }

    private Notification note(String text, int percent, String jobId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NoBadge.install(nm, CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setContentTitle("Downloading")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOnlyAlertOnce(true);
        if (percent >= 0) b.setProgress(100, percent, false);
        if (jobId != null) {
            Intent cancel = new Intent(this, DownloadReceiver.class).setAction("cancel").putExtra("id", jobId);
            cancel.setComponent(new ComponentName(this, DownloadReceiver.class));
            PendingIntent pi = PendingIntent.getBroadcast(this, jobId.hashCode(), cancel, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(new Notification.Action.Builder(null, "Cancel", pi).build());
        }
        return b.build();
    }

    private static synchronized void prepare(android.content.Context c) throws Exception {
        if (ready) return;
        YoutubeDL.getInstance().init(c);
        ready = true;
    }

    /** yt-dlp is updated at most once a day (YouTube changes often; an old one stops working). */
    private void refreshDownloader() {
        long last = Agent.prefs(this).getLong("ytdlpUpdatedAt", 0);
        if (System.currentTimeMillis() - last < 24L * 3600 * 1000) return;
        try {
            YoutubeDL.getInstance().updateYoutubeDL(getApplicationContext(), YoutubeDL.UpdateChannel._STABLE);
            Agent.prefs(this).edit().putLong("ytdlpUpdatedAt", System.currentTimeMillis()).apply();
        } catch (Exception ignored) {
            // an update that fails (no internet) leaves the one that works
        }
    }

    private void download(String url, Job job) {
        final String vid = job.id;
        final boolean music = job.music;
        try {
            job.state = "downloading";
            prepare(getApplicationContext());
            refreshDownloader();
            VideoInfo info = null;
            try {
                info = YoutubeDL.getInstance().getInfo(url);
            } catch (Exception ignored) {
                // still try to download without the title
            }
            if (job.cancelled) return;
            File dir = Downloads.dir(this);
            YoutubeDLRequest req = new YoutubeDLRequest(url);
            req.addOption("--no-playlist");
            req.addOption("--no-mtime");
            // A song is only its sound (m4a); a video is one file with sound and picture.
            req.addOption("-f", music ? "140/bestaudio[ext=m4a]/bestaudio/best" : "18/best[ext=mp4][height<=720]/best[height<=720]/best");
            req.addOption("-o", dir.getAbsolutePath() + "/%(id)s.%(ext)s");
            final String title = info != null && info.getTitle() != null ? info.getTitle() : (music ? "Song" : "Video");
            job.title = title;
            final NotificationManager nm = getSystemService(NotificationManager.class);
            nm.notify(2, note(title, 0, vid));
            YoutubeDL.getInstance().execute(req, "dl-" + vid, (progress, eta, line) -> {
                int p = Math.max(0, Math.min(100, Math.round(progress)));
                job.percent = p;
                if (!job.cancelled) nm.notify(2, note(title, p, vid));
                return kotlin.Unit.INSTANCE;
            });
            if (job.cancelled) return;
            File[] files = dir.listFiles((d, name) -> name.startsWith(vid + ".") && !name.endsWith(".part") && !name.endsWith(".ytdl"));
            if (files == null || files.length == 0) throw new Exception("no file was made");
            Downloads.add(this, vid, title, files[0].getName(), info != null ? info.getThumbnail() : null, music);
            JOBS.remove(vid);
            Agent.addEvent(this, "app", (music ? "Downloaded a YouTube Music song: " : "Downloaded a YouTube video: ") + title);
            toast("Downloaded: " + title);
        } catch (Exception e) {
            if (job.cancelled) return; // cancelled on purpose: nothing went wrong
            job.state = "error";
            job.error = reason(e.getMessage());
            Agent.addEvent(this, "error", "A download did not work: " + job.error);
            toast("The download did not work. Try again in a little while.");
        } finally {
            NotificationManager nm = getSystemService(NotificationManager.class);
            boolean running = false;
            for (Job j : JOBS.values()) if ("downloading".equals(j.state) || "waiting".equals(j.state)) running = true;
            if (!running) nm.cancel(2);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
