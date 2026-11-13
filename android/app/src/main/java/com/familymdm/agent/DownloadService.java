package com.familymdm.agent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads a YouTube video with yt-dlp (the open-source downloader, built into this app and updated by itself
 * from time to time, because YouTube changes). Runs as a foreground service with a progress notification.
 * The file goes to the app's own private folder; it is listed in Downloads and played by DownloadPlayerActivity.
 */
public class DownloadService extends Service {
    private static final String CHANNEL = "downloads";
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static boolean ready;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(2, note("Getting ready…", -1));
        final String url = intent == null ? null : intent.getStringExtra("url");
        final boolean music = intent != null && intent.getBooleanExtra("music", false);
        final int id = startId;
        POOL.execute(() -> {
            try {
                if (url != null) download(url, music);
            } finally {
                stopSelf(id);
            }
        });
        return START_NOT_STICKY;
    }

    private void toast(final String s) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show());
    }

    private Notification note(String text, int percent) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NoBadge.install(nm, CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setContentTitle("Downloading")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOnlyAlertOnce(true);
        if (percent >= 0) b.setProgress(100, percent, false);
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

    private void download(String url, boolean music) {
        String vid = SitePolicy.youtubeVideoId(url);
        try {
            prepare(getApplicationContext());
            refreshDownloader();
            VideoInfo info = null;
            try {
                info = YoutubeDL.getInstance().getInfo(url);
            } catch (Exception ignored) {
                // still try to download without the title
            }
            File dir = Downloads.dir(this);
            YoutubeDLRequest req = new YoutubeDLRequest(url);
            req.addOption("--no-playlist");
            req.addOption("--no-mtime");
            // One file with sound and picture (no merging needed): 360p, or the best single-file mp4/webm up to 720p.
            // A song is only its sound (m4a); a video is one file with sound and picture.
            req.addOption("-f", music ? "140/bestaudio[ext=m4a]/bestaudio/best" : "18/best[ext=mp4][height<=720]/best[height<=720]/best");
            req.addOption("-o", dir.getAbsolutePath() + "/%(id)s.%(ext)s");
            final String title = info != null && info.getTitle() != null ? info.getTitle() : (music ? "Song" : "Video");
            final NotificationManager nm = getSystemService(NotificationManager.class);
            YoutubeDL.getInstance().execute(req, "dl-" + vid, (progress, eta, line) -> {
                nm.notify(2, note(title, Math.max(0, Math.min(100, Math.round(progress)))));
                return kotlin.Unit.INSTANCE;
            });
            File[] files = dir.listFiles((d, name) -> name.startsWith(vid + ".") && !name.endsWith(".part") && !name.endsWith(".ytdl"));
            if (files == null || files.length == 0) throw new Exception("no file was made");
            Downloads.add(this, vid, title, files[0].getName(), info != null ? info.getThumbnail() : null, music);
            Agent.addEvent(this, "app", (music ? "Downloaded a YouTube Music song: " : "Downloaded a YouTube video: ") + title);
            toast("Downloaded: " + title);
        } catch (Exception e) {
            Agent.addEvent(this, "error", "A download did not work: " + e.getMessage());
            toast("The download did not work. Try again in a little while.");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
